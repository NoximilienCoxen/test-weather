package com.caelumweather.ui.sala.rooms

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.inMezzoAllaFrase
import com.caelumweather.lingua.tr
import com.caelumweather.ui.UiState
import com.caelumweather.ui.sala.GiornoSettimana
import com.caelumweather.ui.sala.IconaMeteo
import com.caelumweather.ui.sala.LocalMovimento
import com.caelumweather.ui.sala.SalaPalette
import com.caelumweather.ui.sala.SalaTokens
import com.caelumweather.ui.sala.SalaType
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * **Tieni premuto un giorno, e lo vedi senza sceglierlo.**
 *
 * Toccare un giorno della striscia lo sceglie: cambia tutta la galleria, il
 * cielo, la barra delle ore. Per dare un'occhiata a sabato e tornare dove si
 * era servivano due tocchi e un "torna a oggi". Tenendo premuto, sopra la
 * striscia compare l'anteprima di quel giorno; facendo scorrere il dito sugli
 * altri giorni l'anteprima li segue, con un colpetto a ogni giorno nuovo;
 * staccando il dito sparisce e non e' cambiato niente.
 *
 * Il gesto guarda il dito **prima** dei giorni (`PointerEventPass.Initial`):
 * finche' non e' una pressione lunga lascia fare a loro - il tocco sceglie, lo
 * scorrimento sfoglia le sale - e quando lo diventa si prende il resto del
 * gesto, cosi' staccando il dito il giorno sotto non viene scelto.
 */
internal fun Modifier.anteprimaATenerePremuto(
    quanti: Int,
    onAnteprima: (Int?) -> Unit,
): Modifier = pointerInput(quanti) {
    awaitEachGesture {
        val giu = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // Null se scade l'attesa (pressione lunga), falso se prima il dito si
        // stacca o si muove: allora era un tocco o uno scorrimento.
        val interrotto = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val evento = awaitPointerEvent(PointerEventPass.Initial)
                val dito = evento.changes.firstOrNull { it.id == giu.id } ?: return@withTimeoutOrNull false
                if (!dito.pressed || dito.isConsumed) return@withTimeoutOrNull false
                if ((dito.position - giu.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
        if (interrotto != null) return@awaitEachGesture
        onAnteprima(indiceSotto(giu.position.x, quanti))
        while (true) {
            val evento = awaitPointerEvent(PointerEventPass.Initial)
            val dito = evento.changes.firstOrNull { it.id == giu.id } ?: break
            dito.consume()
            if (!dito.pressed) break
            onAnteprima(indiceSotto(dito.position.x, quanti))
        }
        onAnteprima(null)
    }
}

private fun PointerInputScope.indiceSotto(x: Float, quanti: Int): Int =
    (x / size.width.coerceAtLeast(1) * quanti).toInt().coerceIn(0, quanti - 1)

/**
 * Il fumetto dell'anteprima, sopra la striscia e fuori dal ritaglio della
 * scheda. Entra crescendo dal giorno sulla molla Expressive, cambia giorno in
 * dissolvenza, esce in un soffio.
 *
 * @param centri il centro orizzontale di ogni giorno, in punti dentro la striscia.
 */
@Composable
internal fun FumettoAnteprima(
    indice: Int?,
    settimana: List<GiornoSettimana>,
    centri: Map<Int, Float>,
    state: UiState,
    palette: SalaPalette,
) {
    var mostrato by remember { mutableStateOf<Int?>(null) }
    val presenza = remember { Animatable(0f) }
    val movimento = LocalMovimento.current
    val molla = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(indice) {
        if (indice != null) {
            mostrato = indice
            if (movimento) presenza.animateTo(1f, molla) else presenza.snapTo(1f)
        } else if (mostrato != null) {
            if (movimento) presenza.animateTo(0f, tween(140)) else presenza.snapTo(0f)
            mostrato = null
        }
    }
    val giorno = mostrato?.let { i -> settimana.firstOrNull { it.indice == i } } ?: return
    val centro = centri[giorno.indice] ?: return
    val spazio = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.roundToPx() }
    val posizione = remember(centro, spazio) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = (anchorBounds.left + centro - popupContentSize.width / 2f).roundToInt()
                    .coerceIn(spazio, (windowSize.width - popupContentSize.width - spazio).coerceAtLeast(spazio))
                val sopra = anchorBounds.top - popupContentSize.height - spazio
                val y = if (sopra >= 0) sopra else anchorBounds.bottom + spazio
                return IntOffset(x, y)
            }
        }
    }
    Popup(popupPositionProvider = posizione, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    val p = presenza.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = 0.82f + 0.18f * p
                    scaleY = 0.82f + 0.18f * p
                    // Cresce dal basso, cioe' dal giorno sotto il dito.
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .width(236.dp)
                .shadow(14.dp, RoundedCornerShape(28.dp), clip = false)
                .clip(RoundedCornerShape(28.dp))
                .background(palette.panelSolido),
        ) {
            AnimatedContent(
                targetState = giorno,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "anteprima-giorno",
            ) { g -> ContenutoAnteprima(g, state, palette) }
        }
    }
}

private val OraMinuto: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun ContenutoAnteprima(giorno: GiornoSettimana, state: UiState, palette: SalaPalette) {
    val giornoDati = state.forecast?.days?.getOrNull(giorno.indice)
    val ore = remember(state.forecast, giornoDati?.date) {
        giornoDati?.date?.let { state.forecast?.hoursOf(it) }.orEmpty()
    }
    Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (giorno.indice == 0) tr("Oggi", "Today", "Heute", "Aujourd'hui", "Hoy") else giorno.esteso.replaceFirstChar { it.uppercase() },
                    style = SalaType.rowTitle,
                    color = palette.ink,
                    maxLines = 1,
                )
                Text(text = "${giorno.data} · ${giorno.tipo.inMezzoAllaFrase()}", style = SalaType.rowNote, color = palette.inkFaint, maxLines = 1)
            }
            IconaMeteo(giorno.glifo, modifier = Modifier.size(34.dp))
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = giorno.max?.let { "${state.unit.from(it).roundToInt()}°" } ?: "--", style = SalaType.cardTitle, color = palette.ink)
            Text(
                text = giorno.min?.let { "min ${state.unit.from(it).roundToInt()}°" } ?: "",
                style = SalaType.rowNote,
                color = palette.inkSoft,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        CurvaDelGiorno(ore.mapNotNull { it.temperature }, palette, Modifier.padding(top = 6.dp).fillMaxWidth().height(34.dp))

        val righe = listOfNotNull(
            tr("pioggia", "rain", "Regen", "pluie", "lluvia") + " " + String.format(Lingue.locale, "%.1f mm", giorno.mm ?: 0.0) +
                (giornoDati?.precipProbability?.let { " · $it %" } ?: ""),
            giorno.vento?.let { tr("vento", "wind", "Wind", "vent", "viento") + " ${state.windUnit.from(it).roundToInt()} ${state.windUnit.label}" },
            giorno.uv?.let { "UV " + String.format(Lingue.locale, "%.1f", it) },
            if (giorno.alba != null && giorno.tramonto != null) {
                tr("alba", "sunrise", "Aufgang", "lever", "amanecer") + " ${giorno.alba.format(OraMinuto)} · " +
                    tr("tramonto", "sunset", "Untergang", "coucher", "atardecer") + " ${giorno.tramonto.format(OraMinuto)}"
            } else {
                null
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        righe.forEach { riga ->
            Text(text = riga, style = SalaType.rowNote, color = palette.inkSoft, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** La temperatura della giornata in una riga sola, morbida, sfumata sotto. */
@Composable
private fun CurvaDelGiorno(temperature: List<Double>, palette: SalaPalette, modifier: Modifier = Modifier) {
    if (temperature.size < 3) return
    val minimo = temperature.min()
    val escursione = (temperature.max() - minimo).coerceAtLeast(1.0)
    val tinta = palette.accent
    Canvas(modifier = modifier) {
        val passo = size.width / (temperature.size - 1)
        fun punto(i: Int) = Offset(i * passo, size.height * (1f - ((temperature[i] - minimo) / escursione).toFloat()) * 0.85f + size.height * 0.08f)
        val linea = Path().apply {
            moveTo(punto(0).x, punto(0).y)
            for (i in 1 until temperature.size) {
                val a = punto(i - 1)
                val b = punto(i)
                cubicTo((a.x + b.x) / 2f, a.y, (a.x + b.x) / 2f, b.y, b.x, b.y)
            }
        }
        val area = Path().apply {
            addPath(linea)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(tinta.copy(alpha = 0.22f), tinta.copy(alpha = 0f))))
        drawPath(linea, tinta, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
