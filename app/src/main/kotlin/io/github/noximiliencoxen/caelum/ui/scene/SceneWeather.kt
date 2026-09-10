package io.github.noximiliencoxen.caelum.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.sin

/**
 * Il tempo che fa, disegnato sopra il dipinto invece che dentro.
 *
 * **E' meta' dell'idea, e la meta' che rende la scena viva.** Se la pioggia
 * fosse dipinta, un acquazzone e una pioggerella sarebbero la stessa immagine
 * con due etichette diverse; qui la densita' delle gocce viene dai millimetri
 * veri e la loro inclinazione dal vento vero, quindi due giornate di pioggia si
 * vedono diverse perche' **sono** diverse. E' anche il motivo per cui ai dipinti
 * si chiede di non avere pioggia addosso.
 *
 * Nessuna particella e' un oggetto in memoria: la posizione di ciascuna e' una
 * funzione del suo indice e del tempo. Cosi' non c'e' niente da far nascere e
 * morire, niente lista da far crescere, e cambiare intensita' vuol dire disegnare
 * meno indici invece che buttare via degli oggetti. A sessanta fotogrammi al
 * secondo la differenza fra allocare e non allocare e' l'unica che conta.
 */
internal fun DrawScope.drawSceneWeather(
    kind: SceneKind,
    time: Float,
    /** Da 0 a 1, ricavata dai millimetri all'ora. */
    intensity: Float,
    /** Da -1 a 1: da che parte e quanto tira. */
    wind: Float,
    ink: Color,
    /** Il lampo, da 0 a 1. Solo il temporale e la grandine lo usano. */
    flash: Float,
) {
    when (kind) {
        SceneKind.PIOGGIA -> rain(time, intensity, wind, ink, fast = false)
        SceneKind.TEMPORALE -> {
            rain(time, intensity.coerceAtLeast(0.55f), wind, ink, fast = true)
            bolt(flash, ink)
        }
        SceneKind.GRANDINE -> {
            hail(time, intensity.coerceAtLeast(0.45f), wind, ink)
            bolt(flash, ink)
        }
        SceneKind.NEVE -> snow(time, intensity, wind, ink)
        SceneKind.NEBBIA -> haze(time, ink)
        else -> Unit
    }
}

private fun DrawScope.rain(time: Float, intensity: Float, wind: Float, ink: Color, fast: Boolean) {
    val count = (RAIN_MIN + (RAIN_MAX - RAIN_MIN) * intensity).toInt()
    val fall = if (fast) 1.5f else 1.1f
    val stroke = size.minDimension * 0.0016f
    for (i in 0 until count) {
        val seed = hash(i)
        val depth = 0.35f + 0.65f * hash(i + 977)
        val speed = fall * (0.55f + 0.75f * depth)
        val y = frac(time * speed + seed)
        // Le gocce vicine sono piu' lunghe e piu' opache: e' la profondita'
        // vista da una goccia, e senza di essa la pioggia e' una grata.
        val length = size.height * (0.02f + 0.05f * depth)
        val x = frac(seed * 7.13f + wind * y * 0.22f) * size.width
        val top = y * (size.height + length) - length
        drawLine(
            color = ink.copy(alpha = 0.18f + 0.42f * depth),
            start = Offset(x, top),
            end = Offset(x + wind * length * 0.45f, top + length),
            strokeWidth = stroke * (0.6f + depth),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.hail(time: Float, intensity: Float, wind: Float, ink: Color) {
    val count = (HAIL_MIN + (HAIL_MAX - HAIL_MIN) * intensity).toInt()
    for (i in 0 until count) {
        val seed = hash(i + 313)
        val depth = 0.4f + 0.6f * hash(i + 1451)
        val y = frac(time * (1.7f + depth) + seed)
        val x = frac(seed * 5.7f + wind * y * 0.16f) * size.width
        // Chicchi, non righe: la grandine cade in grani e si vede da quello.
        drawCircle(
            color = ink.copy(alpha = 0.35f + 0.5f * depth),
            radius = size.minDimension * (0.0035f + 0.0045f * depth),
            center = Offset(x, y * size.height),
        )
    }
}

private fun DrawScope.snow(time: Float, intensity: Float, wind: Float, ink: Color) {
    val count = (SNOW_MIN + (SNOW_MAX - SNOW_MIN) * intensity).toInt()
    for (i in 0 until count) {
        val seed = hash(i + 61)
        val depth = 0.3f + 0.7f * hash(i + 733)
        val y = frac(time * (0.10f + 0.16f * depth) + seed)
        // Il fiocco non cade: ondeggia. Senza l'oscillazione la neve e' pioggia
        // bianca, ed e' esattamente cosi' che si vede quando manca.
        val sway = sin((time * 0.7f + seed * 12f) * 2.4f) * 0.035f
        val x = frac(seed * 3.31f + sway + wind * y * 0.10f) * size.width
        drawCircle(
            color = ink.copy(alpha = 0.30f + 0.55f * depth),
            radius = size.minDimension * (0.003f + 0.006f * depth),
            center = Offset(x, y * size.height),
        )
    }
}

/**
 * La nebbia: fasce morbide che scorrono lente a mezza altezza.
 *
 * Non e' un velo uniforme sopra tutto, che sbiadirebbe la scena e basta. Sono
 * bande, perche' la nebbia vera si stratifica, e passano davanti alle colline
 * lasciando il cielo piu' pulito.
 */
private fun DrawScope.haze(time: Float, ink: Color) {
    for (i in 0 until FOG_BANDS) {
        val seed = hash(i + 4099)
        val y = (0.42f + 0.45f * seed) * size.height
        val thickness = size.height * (0.05f + 0.09f * hash(i + 5077))
        val phase = sin(time * 0.09f + seed * 9f)
        drawRect(
            color = ink.copy(alpha = 0.05f + 0.07f * hash(i + 6151)),
            topLeft = Offset(phase * size.width * 0.08f - size.width * 0.05f, y),
            size = androidx.compose.ui.geometry.Size(size.width * 1.1f, thickness),
        )
    }
}

/**
 * Il lampo: un bagliore su tutta la scena, non una saetta disegnata.
 *
 * Una saetta va dove va, e sopra un dipinto che non la prevede finirebbe dietro
 * una collina o dentro una casa. Quello che si vede davvero di un fulmine
 * lontano e' **la luce che cambia per un istante**, ed e' quello che si fa qui:
 * la scena si schiarisce di colpo e torna giu'. Costa un rettangolo.
 */
private fun DrawScope.bolt(flash: Float, ink: Color) {
    if (flash <= 0.01f) return
    drawRect(color = Color.White.copy(alpha = 0.55f * flash))
    drawRect(color = ink.copy(alpha = 0.10f * flash))
}

/** Parte frazionaria, sempre positiva: `x % 1` in Kotlin non lo garantisce. */
private fun frac(x: Float): Float {
    val f = x - x.toInt()
    return if (f < 0f) f + 1f else f
}

/**
 * Un numero fermo per ogni indice.
 *
 * Non `Random`: una particella deve stare dov'e' fra un fotogramma e l'altro, e
 * un generatore con uno stato darebbe posizioni diverse a ogni passata. Qui la
 * posizione **e'** l'indice, quindi non c'e' stato da conservare.
 */
private fun hash(index: Int): Float {
    var x = index * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return abs(x % 100003) / 100003f
}

private const val RAIN_MIN = 45f
private const val RAIN_MAX = 260f
private const val HAIL_MIN = 30f
private const val HAIL_MAX = 140f
private const val SNOW_MIN = 40f
private const val SNOW_MAX = 180f
private const val FOG_BANDS = 7
