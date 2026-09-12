package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.ui.common.buildLinePath
import kotlin.math.roundToInt

/**
 * La barra delle ventiquattro ore: **un comando, non un grafico**.
 *
 * ### Cos'era, e perche' non bastava
 *
 * Una tela alta quarantasei punti con la curva della temperatura, cinque
 * etichette fisse - `00 06 12 18 23` - e una riga verticale sull'ora scelta.
 * Tre difetti, e il primo e' quello che conta:
 *
 * **Non si capiva che si poteva toccare.** Sembrava un grafico, e un grafico non
 * si tocca. Peggio: riconosceva **solo** il trascinamento, quindi anche chi ci
 * provava, toccandola, non otteneva niente - e da un comando che non risponde
 * si impara che non e' un comando.
 *
 * **L'ora scelta non era scritta da nessuna parte sulla barra.** Stava nella
 * didascalia, mezzo schermo piu' su: si trascinava guardando altrove.
 *
 * **Il colore non diceva niente.** Era il grigio dell'inchiostro dal primo
 * minuto all'ultimo, e intanto la giornata dentro cambiava tre volte.
 *
 * ### Cos'e' adesso
 *
 * Un binario pieno **colorato ora per ora col tempo di quell'ora**, cosi' la
 * forma della giornata - il temporale del pomeriggio, la schiarita di sera - si
 * legge senza toccare niente; una **maniglia** che dice da se' che si prende;
 * l'**ora scritta sopra la maniglia**, che viaggia con lei. Si tocca e si
 * trascina. E la curva della temperatura resta dietro, in sordina, perche' era
 * l'unica cosa buona di prima.
 *
 * @param onTick un colpetto quando si scavalca un'ora. Il dito lo sente scattare
 *   sugli scalini invece di scivolare su un continuo, che e' anche cio' che le
 *   ore sono.
 */
@Composable
fun BarraDelleOre(
    hours: List<HourForecast>,
    selected: Int,
    palette: SalaPalette,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onTick: () -> Unit = {},
) {
    // **Ogni valore letto dentro un riconoscitore passa di qui.** La lambda di
    // `pointerInput` viene ricreata solo quando cambia la sua chiave, quindi
    // senza questo confronterebbe per sempre i valori che c'erano all'apertura.
    // E' la trappola #7, e il progetto l'ha gia' pagata **su questa stessa
    // barra**: l'ora corrente era l'unica irraggiungibile della giornata,
    // perche' il confronto la dichiarava gia' scelta.
    val sceltaOra by rememberUpdatedState(selected)
    val scegli by rememberUpdatedState(onSelect)
    val colpetto by rememberUpdatedState(onTick)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val larghezza = maxWidth
        val densita = LocalDensity.current
        var larghezzaEtichetta by remember { mutableIntStateOf(0) }
        val larghezzaPx = with(densita) { larghezza.toPx() }
        val frazione = (selected.coerceIn(0, ORE - 1)).toFloat() / (ORE - 1)

        Column {
            // ── L'ora, sopra la maniglia ─────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().height(ALTEZZA_ETICHETTA)) {
                Text(
                    text = "%02d:00".format(selected.coerceIn(0, ORE - 1)),
                    style = SalaType.hourLabel,
                    color = palette.inkAccent,
                    modifier = Modifier
                        .onSizeChanged { larghezzaEtichetta = it.width }
                        .offset {
                            // Centrata sulla maniglia, ma **trattenuta dentro il
                            // binario**: agli estremi preferisce restare tutta
                            // leggibile che stare esattamente sopra il pollice.
                            val meta = larghezzaEtichetta / 2f
                            val x = (frazione * larghezzaPx - meta)
                                .coerceIn(0f, (larghezzaPx - larghezzaEtichetta).coerceAtLeast(0f))
                            IntOffset(x.roundToInt(), 0)
                        },
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ALTEZZA_BINARIO)
                    // Il tocco secco prima del trascinamento: chi tocca vuole
                    // andare li', non cominciare un gesto.
                    .pointerInput(Unit) {
                        detectTapGestures { punto ->
                            val i = indiceDa(punto.x, size.width.toFloat())
                            if (i != sceltaOra) { colpetto(); scegli(i) }
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            val i = indiceDa(change.position.x, size.width.toFloat())
                            if (i != sceltaOra) { colpetto(); scegli(i) }
                        }
                    },
            ) {
                disegnaBarra(hours, selected, palette)
            }
        }
    }
}

/** Le ore di un giorno. L'indice della colonna **e'** l'ora, e non serve una
 *  seconda verita' da tenere in fase con la prima. */
private const val ORE = 24

private val ALTEZZA_ETICHETTA = 18.dp
private val ALTEZZA_BINARIO = 44.dp

private fun indiceDa(x: Float, larghezza: Float): Int =
    (x / larghezza.coerceAtLeast(1f) * (ORE - 1)).roundToInt().coerceIn(0, ORE - 1)

/**
 * Il colore di un'ora.
 *
 * Non sono i colori delle macchie di sfondo: quelli sono lavaggi larghi e
 * tenui, e a otto punti di larghezza non si distinguerebbero l'uno dall'altro.
 * Qui servono tinte che reggano una colonna sottile, e la notte deve leggersi
 * anche su carta gia' scura - per questo il sereno notturno non e' "niente", e'
 * un blu.
 */
private fun coloreOra(hour: HourForecast?, palette: SalaPalette): Color {
    if (hour?.weatherCode == null) return palette.inkFaint
    val giorno = hour.isDay
    val tinta = when (salaConditionOf(hour.weatherCode)) {
        SalaCondition.SERENO ->
            if (giorno) SalaTokens.processYellow else SalaTokens.accent800
        SalaCondition.NUVOLOSO ->
            if (giorno) SalaTokens.neutral400 else SalaTokens.accent900
        SalaCondition.PIOGGIA -> SalaTokens.accent
        SalaCondition.GRANDINE -> SalaTokens.accent300
        SalaCondition.TEMPORALE, SalaCondition.TEMPORALE_GRANDINE -> SalaTokens.accent2_700
    }
    // Su carta scura le stesse tinte affogano: si schiariscono di un passo, non
    // di un salto - e' la stessa regola del materiale che non cambia identita'
    // girando (trappola #13), applicata al tempo invece che alla rotazione.
    return lerp(tinta, lerp(tinta, SalaTokens.neutral100, 0.34f), palette.buio)
}

private fun DrawScope.disegnaBarra(hours: List<HourForecast>, selected: Int, palette: SalaPalette) {
    val w = size.width
    val h = size.height
    val cimaBinario = h * 0.46f
    val altoBinario = h * 0.30f
    val raggio = altoBinario / 2f

    // ── La temperatura, dietro e in sordina ──────────────────────────────────
    //
    // Resta perche' e' informazione vera, ma arretra: qui il protagonista e' il
    // comando. Prima era il contrario, e il comando non si vedeva.
    if (hours.size >= 2) {
        val temps = hours.mapNotNull { it.temperature }
        if (temps.size >= 2) {
            val minimo = temps.min().toFloat()
            val massimo = temps.max().toFloat()
            val ampiezza = (massimo - minimo).coerceAtLeast(0.01f)
            val punti = hours.take(ORE).mapIndexed { i, ora ->
                val t = (ora.temperature?.toFloat() ?: minimo)
                Offset(
                    x = i.toFloat() / (ORE - 1) * w,
                    y = cimaBinario * 0.92f - (t - minimo) / ampiezza * cimaBinario * 0.78f,
                )
            }
            if (punti.size >= 2) {
                val curva = buildLinePath(punti)
                val area = Path().apply {
                    addPath(curva)
                    lineTo(w, cimaBinario)
                    lineTo(0f, cimaBinario)
                    close()
                }
                drawPath(area, color = palette.inkFaint)
                drawPath(curva, color = palette.ink.copy(alpha = 0.34f), style = Stroke(width = 2f))
            }
        }
    }

    // ── Il binario, un segmento per ora ──────────────────────────────────────
    //
    // Ritagliato dentro la pista arrotondata: cosi' le ventiquattro tessere
    // formano **una** barra con i capi tondi, e non ventiquattro mattoncini.
    val pista = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                rect = Rect(0f, cimaBinario, w, cimaBinario + altoBinario),
                radiusX = raggio,
                radiusY = raggio,
            ),
        )
    }
    clipPath(pista) {
        val passo = w / ORE
        for (i in 0 until ORE) {
            drawRect(
                color = coloreOra(hours.getOrNull(i), palette),
                topLeft = Offset(i * passo, cimaBinario),
                // Mezzo punto di sormonta: senza, fra una tessera e l'altra
                // resta una fessura chiara dovuta all'arrotondamento dei bordi.
                size = Size(passo + 0.5f, altoBinario),
            )
        }
    }

    // ── La maniglia ──────────────────────────────────────────────────────────
    //
    // E' l'unica cosa che dice "questo si prende". Un anello pieno con un centro
    // chiaro - si stacca sia dalle tessere gialle sia da quelle blu scure, che
    // un pallino di un colore solo non farebbe.
    val x = selected.coerceIn(0, ORE - 1).toFloat() / (ORE - 1) * w
    val cy = cimaBinario + altoBinario / 2f
    val rManiglia = altoBinario * 0.92f
    drawCircle(color = palette.ground, radius = rManiglia, center = Offset(x, cy))
    drawCircle(
        color = palette.inkAccent,
        radius = rManiglia,
        center = Offset(x, cy),
        style = Stroke(width = (rManiglia * 0.30f).coerceAtLeast(2f)),
    )
    drawCircle(color = palette.inkAccent, radius = rManiglia * 0.30f, center = Offset(x, cy))
}
