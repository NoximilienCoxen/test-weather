package io.github.noximiliencoxen.caelum.ui.sala.rooms.rilievo

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.motion.rememberVibrazioniMeteo
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.widget.paint.render3d.Camera
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

/**
 * La settimana in rilievo: colline dove fa caldo, valli dove fa freddo.
 *
 * Righe = giorni (il primo davanti), colonne = ore da mezzanotte a mezzanotte.
 * Si ruota col dito, si tocca un punto per leggerlo. Stesso schema della luna:
 * vertici proiettati con la `Camera` dei widget e un solo `drawVertices` - ma
 * qui la superficie **non** e' convessa, quindi i triangoli si ordinano dal
 * piu' lontano al piu' vicino prima di disegnarli.
 */
@Composable
internal fun RilievoSettimana(
    rilievo: Rilievo,
    /** Come scrivere una temperatura, nell'unita' scelta. */
    gradi: (Float) -> String,
    palette: SalaPalette,
    movimento: Boolean,
    modifier: Modifier = Modifier,
) {
    val buffer = remember(rilievo) { BufferRilievo(rilievo) }
    val yaw = remember { Animatable(VISTA_YAW) }
    val pitch = remember { Animatable(VISTA_PITCH) }
    val scope = rememberCoroutineScope()
    val vibrazioni = rememberVibrazioniMeteo()
    var scelto by remember(rilievo) { mutableIntStateOf(-1) }

    fun tornaAllaVista() {
        scope.launch {
            if (movimento) {
                launch { pitch.animateTo(VISTA_PITCH, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow)) }
                yaw.animateTo(VISTA_YAW, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow))
            } else {
                pitch.snapTo(VISTA_PITCH)
                yaw.snapTo(VISTA_YAW)
            }
        }
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .semantics {
                    contentDescription = "La settimana in rilievo, dalla minima di ${gradi(rilievo.minima)} " +
                        "alla massima di ${gradi(rilievo.massima)}. Trascina per ruotare, tocca per leggere un'ora."
                }
                .pointerInput(rilievo) {
                    detectTapGestures { punto ->
                        val i = Rilievo.piuVicino(buffer.posizioni, buffer.visibile, punto.x, punto.y, 36.dp.toPx())
                        if (i >= 0 && rilievo.temperature[i / Rilievo.ORE][i % Rilievo.ORE].isFinite()) {
                            scelto = i
                            if (movimento) vibrazioni.scatto()
                        }
                    }
                }
                .pointerInput(rilievo, movimento) {
                    detectDragGestures(
                        onDragEnd = { tornaAllaVista() },
                        onDragCancel = { tornaAllaVista() },
                    ) { cambio, spinta ->
                        cambio.consume()
                        val g = 180f / size.width
                        scope.launch {
                            yaw.snapTo(yaw.value - spinta.x * g)
                            pitch.snapTo((pitch.value + spinta.y * g).coerceIn(10f, 80f))
                        }
                    }
                },
        ) {
            disegnaRilievo(rilievo, buffer, yaw.value, pitch.value, scelto, palette.ink)
        }

        val testo = if (scelto >= 0) {
            val r = scelto / Rilievo.ORE
            val c = scelto % Rilievo.ORE
            val giorno = rilievo.giorni[r].dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).uppercase()
            "$giorno ${"%02d".format(c)}:00 · ${gradi(rilievo.temperature[r][c])}"
        } else {
            "Trascina per ruotare · tocca un punto per leggerlo"
        }
        Text(
            text = testo,
            style = if (scelto >= 0) SalaType.rowTitle else SalaType.footnote,
            color = if (scelto >= 0) palette.accent else palette.inkSoft,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Posizioni, profondita', colori e indici: allocati una volta per settimana. */
internal class BufferRilievo(rilievo: Rilievo) {
    val nodi = rilievo.righe * Rilievo.ORE
    val posizioni = FloatArray(nodi * 2)
    val profondita = FloatArray(nodi)
    val visibile = BooleanArray(nodi)
    val colori: IntArray
    val triangoli: ShortArray
    val ordinati: ShortArray
    val pennello = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }

    // Dove sta ogni nodo nello spazio, prima della camera.
    val x = FloatArray(nodi)
    val y = FloatArray(nodi)
    val z = FloatArray(nodi)

    init {
        val freddo = SalaTokens.acqua
        val tiepido = SalaTokens.verde400
        val caldo = SalaTokens.accent500
        colori = IntArray(nodi)
        for (r in 0 until rilievo.righe) for (c in 0 until Rilievo.ORE) {
            val i = r * Rilievo.ORE + c
            val q = rilievo.quota(r, c)
            x[i] = -1f + 2f * c / (Rilievo.ORE - 1)
            z[i] = -0.75f + 1.5f * r / (rilievo.righe - 1).coerceAtLeast(1)
            y[i] = -q * ALTEZZA
            val tinta = if (q < 0.5f) lerp(freddo, tiepido, q * 2f) else lerp(tiepido, caldo, (q - 0.5f) * 2f)
            // Una luce semplice dall'inclinazione verso l'ora dopo: le coste
            // che salgono verso il pomeriggio un po' piu' chiare, cosi' la
            // forma si legge anche da ferma.
            val dopo = if (c < Rilievo.ORE - 1) rilievo.quota(r, c + 1) else q
            val luce = (0.82f + (dopo - q) * 2.2f).coerceIn(0.62f, 1.1f)
            colori[i] = lerp(Color.Black, tinta, luce.coerceAtMost(1f)).toArgb()
        }
        val t = ArrayList<Short>()
        for (r in 0 until rilievo.righe - 1) for (c in 0 until Rilievo.ORE - 1) {
            val a = r * Rilievo.ORE + c
            val b = a + Rilievo.ORE
            val quattro = intArrayOf(a, a + 1, b, b + 1)
            // Un buco nella previsione e' un buco nel rilievo.
            if (quattro.any { !rilievo.temperature[it / Rilievo.ORE][it % Rilievo.ORE].isFinite() }) continue
            t += a.toShort(); t += b.toShort(); t += (a + 1).toShort()
            t += (a + 1).toShort(); t += b.toShort(); t += (b + 1).toShort()
        }
        triangoli = t.toShortArray()
        ordinati = ShortArray(triangoli.size)
    }

    companion object {
        const val ALTEZZA = 0.55f
    }
}

/** Proietta e disegna; fuori dal composable per la prova che lo disegna su PNG. */
internal fun DrawScope.disegnaRilievo(
    rilievo: Rilievo,
    buffer: BufferRilievo,
    yawDeg: Float,
    pitchDeg: Float,
    scelto: Int,
    inchiostro: Color,
) {
    // Tre decimi della larghezza, non quattro: ruotato, il rettangolo dei
    // sette giorni si allunga in diagonale e a quattro usciva dalla tela.
    val scala = minOf(size.width * 0.30f, size.height * 0.62f)
    val centro = Offset(size.width / 2f, size.height * 0.54f)
    val camera = Camera(yawDeg = yawDeg, pitchDeg = pitchDeg, distance = scala * 4f, origin = centro)

    for (i in 0 until buffer.nodi) {
        camera.place(buffer.x[i] * scala, (buffer.y[i] + BufferRilievo.ALTEZZA / 2f) * scala, buffer.z[i] * scala)
        buffer.posizioni[i * 2] = camera.sx
        buffer.posizioni[i * 2 + 1] = camera.sy
        buffer.profondita[i] = camera.vz
        buffer.visibile[i] = rilievo.temperature[i / Rilievo.ORE][i % Rilievo.ORE].isFinite()
    }

    // Dal piu' lontano al piu' vicino: cosi' le colline davanti coprono quelle
    // dietro. Trecento triangoli, ordinarli a ogni fotogramma non costa.
    val t = buffer.triangoli
    val n = t.size / 3
    if (n == 0) return
    val ordine = (0 until n).sortedByDescending { k ->
        buffer.profondita[t[k * 3].toInt()] + buffer.profondita[t[k * 3 + 1].toInt()] + buffer.profondita[t[k * 3 + 2].toInt()]
    }
    var j = 0
    for (k in ordine) {
        buffer.ordinati[j++] = t[k * 3]; buffer.ordinati[j++] = t[k * 3 + 1]; buffer.ordinati[j++] = t[k * 3 + 2]
    }
    drawContext.canvas.nativeCanvas.drawVertices(
        AndroidCanvas.VertexMode.TRIANGLES,
        buffer.posizioni.size,
        buffer.posizioni,
        0,
        null,
        0,
        buffer.colori,
        0,
        buffer.ordinati,
        0,
        j,
        buffer.pennello,
    )

    // Una riga sottile all'inizio di ogni giorno: senza, la superficie e' una
    // collina sola e non si capisce dove finisce lunedi' e comincia martedi'.
    for (r in 0 until rilievo.righe) {
        var prima: Offset? = null
        for (c in 0 until Rilievo.ORE) {
            val i = r * Rilievo.ORE + c
            if (!buffer.visibile[i]) { prima = null; continue }
            val p = Offset(buffer.posizioni[i * 2], buffer.posizioni[i * 2 + 1])
            prima?.let { drawLine(inchiostro.copy(alpha = 0.22f), it, p, strokeWidth = 1.2f) }
            prima = p
        }
    }

    if (scelto in 0 until buffer.nodi) {
        val p = Offset(buffer.posizioni[scelto * 2], buffer.posizioni[scelto * 2 + 1])
        drawCircle(Color.White, radius = 7.dp.toPx(), center = p)
        drawCircle(inchiostro, radius = 4.dp.toPx(), center = p)
    }
}

private const val VISTA_YAW = -28f
private const val VISTA_PITCH = 38f
