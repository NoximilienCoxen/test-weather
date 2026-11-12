package com.caelumweather.ui.sala

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cio' che il tempo posa **sul vetro della scheda**: la brina quando gela, le
 * gocce quando piove forte. Lo calcola la Shell dall'[Atmosfera] e dalla scena,
 * lo disegna [PannelloSala]: un posto solo per tutte le sale.
 */
data class Vetro(
    /** Da 0 a 1: la brina sui bordi. */
    val gelo: Float = 0f,
    /** Da 0 a 1: le gocce che scivolano. Solo pioggia forte. */
    val pioggia: Float = 0f,
)

/**
 * Non statico: i due numeri passano per le molle, e una lettura statica farebbe
 * ricomporre ogni scheda a ogni fotogramma del passaggio.
 */
val LocalVetro = compositionLocalOf { Vetro() }

/**
 * La brina sui bordi della scheda, e il tocco che la scioglie.
 *
 * Un velo bianco che entra dai bordi e qualche cristallo a sei bracci lungo il
 * perimetro. Toccando la scheda si scioglie in meno di un secondo e torna a
 * formarsi piano dopo una decina di secondi, come su un vetro vero. Il tocco
 * si ascolta senza prenderlo: i comandi sotto funzionano come sempre.
 */
@Composable
internal fun BrinaSulVetro(gelo: Float, modifier: Modifier = Modifier, cartaScura: Boolean = false) {
    val movimento = LocalMovimento.current
    val presenza = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Canvas(
        modifier = modifier.pointerInput(movimento) {
            if (!movimento) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                scope.launch {
                    presenza.animateTo(0f, tween(900))
                    delay(10_000)
                    presenza.animateTo(1f, tween(6000, easing = LinearEasing))
                }
            }
        },
    ) {
        disegnaBrina(gelo * presenza.value, cartaScura)
    }
}

/** Il disegno della brina, puro: lo provano anche i test. */
internal fun DrawScope.disegnaBrina(forza: Float, cartaScura: Boolean = false) {
    if (forza <= 0.02f) return
    // Sulla carta chiara il bianco sparisce: li' la brina e' azzurro ghiaccio.
    val bianco = if (cartaScura) Color(0xFFF4FAFF) else Color(0xFF8DB8D8)
    val entra = 22.dp.toPx()
    val velo = (if (cartaScura) 0.42f else 0.30f) * forza
    // Il velo che entra dai quattro bordi.
    drawRect(Brush.verticalGradient(0f to bianco.copy(alpha = velo), 1f to Color.Transparent, endY = entra), size = size.copy(height = entra))
    drawRect(
        Brush.verticalGradient(0f to Color.Transparent, 1f to bianco.copy(alpha = velo), startY = size.height - entra, endY = size.height),
        topLeft = Offset(0f, size.height - entra),
        size = size.copy(height = entra),
    )
    drawRect(Brush.horizontalGradient(0f to bianco.copy(alpha = velo), 1f to Color.Transparent, endX = entra), size = size.copy(width = entra))
    drawRect(
        Brush.horizontalGradient(0f to Color.Transparent, 1f to bianco.copy(alpha = velo), startX = size.width - entra, endX = size.width),
        topLeft = Offset(size.width - entra, 0f),
        size = size.copy(width = entra),
    )
    // I cristalli: **solo negli angoli**, dove sul vetro vero la brina
    // comincia, e radi. Un giro intero di fiocchi lungo il bordo pareva una
    // cornice di adesivi.
    val angoli = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
    angoli.forEachIndexed { a, angolo ->
        for (k in 0 until 5) {
            val seme = ((a * 31 + k * 17) % 23) / 23f
            val distanza = (5f + k * 5f + seme * 4f).dp.toPx()
            val apertura = (0.15f + 0.7f * (((a * 7 + k * 5) % 13) / 13f))
            val dx = if (angolo.x == 0f) 1f else -1f
            val dy = if (angolo.y == 0f) 1f else -1f
            val centro = Offset(
                angolo.x + dx * distanza * apertura + dx * 7.dp.toPx(),
                angolo.y + dy * distanza * (1f - apertura) + dy * 7.dp.toPx(),
            )
            val r = (2.6f + seme * 3.4f - k * 0.3f).dp.toPx()
            cristallo(centro, r, seme * 60f, bianco.copy(alpha = (0.65f - k * 0.08f) * forza))
        }
    }
}

/** Un cristallo a sei bracci con due rametti per braccio. */
private fun DrawScope.cristallo(centro: Offset, r: Float, giro: Float, colore: Color) {
    val tratto = 1.dp.toPx()
    for (k in 0 until 6) {
        val a = ((k * 60f + giro) * PI / 180f).toFloat()
        val fine = Offset(centro.x + cos(a) * r, centro.y + sin(a) * r)
        drawLine(colore, centro, fine, strokeWidth = tratto, cap = StrokeCap.Round)
        val meta = Offset(centro.x + cos(a) * r * 0.55f, centro.y + sin(a) * r * 0.55f)
        for (lato in listOf(-1f, 1f)) {
            val b = a + lato * 0.75f
            drawLine(colore, meta, Offset(meta.x + cos(b) * r * 0.35f, meta.y + sin(b) * r * 0.35f), strokeWidth = tratto * 0.8f, cap = StrokeCap.Round)
        }
    }
}

/**
 * Qualche goccia che scivola sulla scheda quando piove forte: si ferma, riparte,
 * lascia una scia che asciuga. Poche - cinque al massimo - e solo con pioggia
 * vera e forte; e' un movimento continuo, quindi vive solo finche' piove cosi'
 * e non con le animazioni ridotte.
 */
@Composable
internal fun GocceSulVetro(pioggia: Float, modifier: Modifier = Modifier) {
    if (pioggia <= 0.02f || !LocalMovimento.current) return
    val giro = rememberInfiniteTransition(label = "gocce")
    val tempo by giro.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(600_000, easing = LinearEasing), RepeatMode.Restart),
        label = "tempo-gocce",
    )
    // **Uno strato suo**: senza, ogni fotogramma delle gocce faceva ridisegnare
    // la scheda intera che sta sotto, testo compreso.
    Canvas(modifier = modifier.graphicsLayer()) { disegnaGocce(pioggia, tempo) }
}

/** Le gocce al secondo [tempo], pure: lo provano anche i test. */
internal fun DrawScope.disegnaGocce(forza: Float, tempo: Float) {
    if (forza <= 0.02f) return
    val quante = 2 + (forza * 3).toInt()
    for (i in 0 until quante) {
        val periodo = 5.5f + (i % 3) * 1.7f
        val ciclo = ((tempo + i * 2.3f) / periodo).toInt()
        val fase = ((tempo + i * 2.3f) / periodo) % 1f
        // Ogni giro la goccia nasce in un punto nuovo, deterministico.
        val semeX = ((ciclo * 7 + i * 13) % 19) / 19f
        val semeY = ((ciclo * 5 + i * 3) % 11) / 11f
        val x = size.width * (0.08f + 0.84f * semeX)
        val partenza = size.height * (0.05f + 0.35f * semeY)
        // Ferma, poi scivola accelerando: le gocce vere si attaccano e partono.
        val scivola = ((fase - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val corsa = scivola * scivola * (size.height * 0.75f)
        val y = partenza + corsa
        val comparsa = (fase / 0.08f).coerceAtMost(1f) * ((1f - fase) / 0.12f).coerceAtMost(1f)
        val r = (3.4f + 1.8f * semeY).dp.toPx()
        if (corsa > r) {
            drawLine(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0f), 1f to Color.White.copy(alpha = 0.18f * comparsa), startY = partenza, endY = y),
                Offset(x, partenza), Offset(x, y - r * 0.6f),
                strokeWidth = r * 0.55f, cap = StrokeCap.Round,
            )
        }
        drawCircle(Color.White.copy(alpha = 0.22f * comparsa), radius = r, center = Offset(x, y))
        drawCircle(Color.Black.copy(alpha = 0.10f * comparsa), radius = r, center = Offset(x, y), style = Stroke(width = 1.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.7f * comparsa), radius = r * 0.32f, center = Offset(x - r * 0.3f, y - r * 0.35f))
    }
}
