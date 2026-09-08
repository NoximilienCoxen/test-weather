package io.github.noximiliencoxen.caelum.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.PrecipKind
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.data.isWet
import io.github.noximiliencoxen.caelum.ui.common.buildLinePath
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.MeteoType
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Le ventiquattro ore della giornata: **quando** piove.
 *
 * E' la domanda per cui si apre questa scheda, e la cifra da sola non la
 * rispondeva: venti millimetri distribuiti su tutto il giorno e venti caduti in
 * due ore sono due giornate diverse con lo stesso numero.
 *
 * Prende il posto del riquadro tratteggiato, che era la consegna scritta accanto
 * al buco. La consegna diceva "gocce sul vetro e una pozza che cresce": quella
 * era una figura, e questa e' la risposta - le gocce sono finite dentro la
 * vasca, dove hanno una scala, e qui sta l'unica cosa che mancava davvero.
 *
 * **Una tela sola** per colonne, curva, cursore ed etichette. La barra delle ore
 * della prima scheda porta gia' la ragione scritta: due composable che si
 * accordano sulla geometria vanno d'accordo finche' nessuno tocca l'uno senza
 * l'altro. Qui la larghezza di un'ora dev'essere la stessa per tutti e tre.
 *
 * **L'ora si sceglie da qui**, e prima era un non-obiettivo dichiarato. La
 * ragione scritta era doppia, e vale ancora meta': due scrittori sullo stesso
 * stato sarebbero un difetto, e infatti non ce ne sono due - questa fascia
 * chiama `selectHour`, che e' lo scrittore che c'era gia'. L'altra meta' era il
 * rischio del secondo riconoscitore orizzontale, e quello si e' scelto di
 * correrlo: tornare sulla prima scheda per cambiare ora e ridiscendere era
 * scomodo, e una scheda che racconta un'ora senza lasciarla scegliere fa fare
 * due gesti per una domanda sola.
 *
 * **Il riconoscitore e' uno solo**, e non consuma la discesa. Finche' non si sa
 * se il dito va in orizzontale o in verticale, il carosello ha lo stesso diritto
 * di questa fascia: si aspetta la soglia, e se vince il verticale ci si ritira.
 * Due riconoscitori separati - uno per il tocco, uno per il trascinamento -
 * sarebbero il difetto che la barra delle ore ha gia' pagato: *"il primo consuma
 * l'evento di discesa e il secondo annulla il proprio scorrimento, e il
 * risultato e' una barra che ogni tanto ignora il dito"*.
 */
@Composable
internal fun RainHours(
    hours: List<HourForecast>,
    /** L'ora scelta sulla prima scheda, se cade in questo giorno. */
    selectedHour: Int?,
    /** L'ora vera, **solo** se il giorno mostrato e' oggi. */
    nowHour: Int?,
    kind: PrecipKind,
    /** Il codice imposto dagli agganci di verifica, che vince su quello vero. */
    forcedCode: Int?,
    accent: Color,
    compact: Boolean,
    /** Sceglie un'ora del giorno mostrato. Riceve **l'ora**, non la posizione. */
    onSelectHour: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val snowy = kind.isSnowy()
    val haptics = LocalHapticFeedback.current

    // Trappola #7: un riconoscitore di gesti **congela** quello che cattura. Ogni
    // valore che legge dentro passa da `rememberUpdatedState`, se no la fascia
    // continuerebbe a scegliere sulle ore di ieri.
    val liveHours = rememberUpdatedState(hours)
    val liveSelected = rememberUpdatedState(selectedHour)
    val liveSelect = rememberUpdatedState(onSelectHour)

    // La frase guarda avanti solo su oggi: `nowHour` e' nullo sugli altri
    // giorni, e li' si racconta la giornata intera.
    val sentence = remember(hours, forcedCode, nowHour) {
        rainSentence(hours, forcedCode, from = nowHour)
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            // `body` e non `caption`: e' una frase intera, e una frase in
            // maiuscolo spaziato si compita invece di leggersi. E' la stessa
            // ragione per cui `body` esiste in questo tema.
            text = sentence ?: NO_HOURS,
            style = MeteoType.body,
            color = colors.label,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )

        // Senza ore non si disegna una fascia vuota: **e' un caso vero**, non
        // codice difensivo. Con un modello a corto raggio la previsione si
        // ferma attorno alle settantadue ore, quindi il giorno esiste e le sue
        // ore no. La riga sopra lo dichiara, e qui non si aggiunge un rettangolo
        // muto che sembrerebbe una giornata piatta.
        if (hours.isEmpty()) return@Column

        val peak = hours.mapNotNull { it.precipitation }.maxOrNull() ?: 0.0
        val ceiling = bandCeiling(peak, snowy)
        val bandHeight = if (compact) BAND_HEIGHT_COMPACT else BAND_HEIGHT

        // **Cosa sente chi non la vede.** Un trascinamento e basta e' muto: la
        // fascia si dichiara per quello che e' e dice l'ora scelta. Scegliere
        // un'ora **da qui** resta pero' un gesto continuo, che TalkBack non
        // sa fare - e chi lo usa l'ora la sceglie dalla barra della prima
        // scheda, che quel mestiere ce l'ha gia'. Va detto invece di lasciar
        // credere che questa fascia sia un comando per tutti.
        val spoken = remember(sentence, selectedHour, ceiling, snowy) {
            val unita = if (snowy) "centimetri" else "millimetri"
            val ora = selectedHour?.let { ", ora scelta le $it" }.orEmpty()
            "Le ventiquattro ore: ${sentence.orEmpty()} " +
                "La scala arriva a ${ceiling.roundToInt()} $unita all'ora$ora"
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(bandHeight + AXIS_ROW)
                .padding(top = SENTENCE_GAP)
                .semantics { contentDescription = spoken }
                .pointerInput(Unit) {
                    // ── Un riconoscitore solo: tocco e trascinamento ─────────
                    //
                    // **La discesa non si consuma.** Consumarla, come fa la
                    // barra della prima scheda, renderebbe questa fascia una
                    // zona morta alta centoquattordici punti in cui il pollice
                    // non puo' piu' cambiare scheda: li' e' accettabile perche'
                    // la barra e' alta poche decine di punti, qui no.
                    //
                    // Si aspetta invece la soglia e si guarda da che parte va il
                    // dito. Se prevale il verticale ci si ritira e il carosello
                    // se lo prende: e' la spartizione di sempre - orizzontale
                    // sceglie, verticale apre.
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var claimed = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                // Sollevato senza aver mai superato la soglia:
                                // era un tocco, e un tocco sceglie l'ora sotto
                                // il dito.
                                if (!claimed) {
                                    pick(change.position.x, size.width.toFloat(),
                                        liveHours.value, liveSelected.value,
                                        liveSelect.value, haptics)
                                }
                                break
                            }
                            if (!claimed) {
                                val dx = abs(change.position.x - down.position.x)
                                val dy = abs(change.position.y - down.position.y)
                                if (dy > slop && dy > dx) break
                                claimed = dx > slop
                            }
                            if (claimed) {
                                pick(change.position.x, size.width.toFloat(),
                                    liveHours.value, liveSelected.value,
                                    liveSelect.value, haptics)
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            val slot = size.width / hours.size
            val floor = bandHeight.toPx()
            val labels = TextStyle(color = colors.label, fontSize = AXIS_SP)

            // La riga di base: senza, le colonne corte galleggiano.
            drawLine(
                color = colors.line.copy(alpha = 0.55f),
                start = Offset(0f, floor),
                end = Offset(size.width, floor),
                strokeWidth = 1.dp.toPx(),
            )

            // ── Le colonne ──────────────────────────────────────────────────
            hours.forEachIndexed { index, hour ->
                val code = forcedCode ?: hour.weatherCode
                val mm = hour.precipitation ?: 0.0
                // **Il codice per primo**: un'ora bagnata a zero millimetri
                // resta un'ora bagnata, e sotto una frase che dice temporale la
                // fascia non puo' essere piatta.
                val wet = Wmo.family(code).isWet() || mm > 0.0
                if (!wet) return@forEachIndexed

                val x = index * slot + slot * (1f - COLUMN_SHARE) / 2f
                val w = slot * COLUMN_SHARE
                val chosen = selectedHour == hour.time.hour
                // Le ore di neve in bianco: e' la differenza che conta, e sta
                // sullo stesso asse perche' la colonna misura l'equivalente in
                // acqua. Due scale dentro una fascia sarebbero due grafici
                // sovrapposti che si somigliano.
                val tint = if ((hour.snowfall ?: 0.0) > 0.0) colors.cloudCore else accent
                val alpha = if (chosen) 1f else 0.82f

                if (mm <= 0.0) {
                    // **Piove e il modello non dice quanto.** Un contorno e non
                    // un pieno: cosi' si distingue a vista da "sono caduti due
                    // decimi", che e' un'altra cosa. E' la trappola dei
                    // millimetri che non dicono se piove, disegnata invece che
                    // solo rispettata.
                    val h = RAIN_MIN.toPx()
                    drawRect(
                        color = tint.copy(alpha = alpha * 0.7f),
                        topLeft = Offset(x, floor - h),
                        size = Size(w, h),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    return@forEachIndexed
                }

                val share = (mm / ceiling).toFloat().coerceAtMost(1f)
                val h = (floor * share).coerceAtLeast(RAIN_MIN.toPx())
                drawRect(
                    color = tint.copy(alpha = alpha),
                    topLeft = Offset(x, floor - h),
                    size = Size(w, h),
                )
                // Tagliata sopra il soffitto, **e lo dice**: una tacca in cima.
                // E' la stessa scelta della vasca che trabocca invece di
                // ritarare la scala di nascosto.
                if (mm > ceiling) {
                    drawRect(
                        color = colors.text.copy(alpha = 0.8f),
                        topLeft = Offset(x, floor - h),
                        size = Size(w, 2.dp.toPx()),
                    )
                }
            }

            // ── La curva della probabilita' ─────────────────────────────────
            //
            // Millimetri e probabilita' sono **due domande diverse** - quanto
            // forte e se - e questa e' quella scritta con una linea sopra delle
            // aree, cosi' nessuno la scambia per una colonna. La dichiarano il
            // segno di percentuale al suo capo destro e il soffitto scritto in
            // alto: due etichette agli angoli, che e' come la barra delle ore
            // gia' distingue la sua curva dalle sue colonnine.
            val chances = hours.map { it.precipProbability }
            if (chances.any { it != null }) {
                val points = chances.mapIndexed { index, value ->
                    value?.let {
                        Offset(
                            x = (index + 0.5f) * slot,
                            y = floor - floor * (it / 100f),
                        )
                    }
                }
                drawPath(
                    path = buildLinePath(points),
                    color = colors.label.copy(alpha = 0.75f),
                    style = Stroke(
                        width = 1.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                val percent = measurer.measure("%", labels)
                drawText(
                    percent,
                    topLeft = Offset(size.width - percent.size.width, 0f),
                )
            }

            // Il soffitto scelto, scritto: un asse che cambia da solo senza
            // dirlo e' un grafico che mente.
            val top = measurer.measure(
                "${ceiling.roundToInt()} ${if (snowy) "CM" else "MM"}/H",
                labels,
            )
            drawText(top, topLeft = Offset(0f, 0f))

            // ── L'ora scelta, e l'ora vera ──────────────────────────────────
            //
            // Si aggancia per `time.hour` e **non per indice di lista**: un
            // giorno di cambio d'ora non ha ventiquattro voci, e contare le
            // posizioni sposterebbe il cursore di un'ora esatta proprio nel
            // giorno in cui nessuno se lo aspetta.
            hours.forEachIndexed { index, hour ->
                if (hour.time.hour != selectedHour) return@forEachIndexed
                drawLine(
                    color = colors.text.copy(alpha = 0.65f),
                    start = Offset((index + 0.5f) * slot, 0f),
                    end = Offset((index + 0.5f) * slot, floor),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            hours.forEachIndexed { index, hour ->
                if (nowHour == null || hour.time.hour != nowHour) return@forEachIndexed
                drawCircle(
                    color = colors.text.copy(alpha = 0.75f),
                    radius = NOW_DOT.toPx(),
                    center = Offset((index + 0.5f) * slot, floor + AXIS_ROW.toPx() / 2f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            // ── Le ore scritte ──────────────────────────────────────────────
            hours.forEachIndexed { index, hour ->
                val h = hour.time.hour
                if (h % 6 != 0) return@forEachIndexed
                val laid = measurer.measure(if (h < 10) "0$h" else "$h", labels)
                drawText(
                    laid,
                    topLeft = Offset(
                        x = ((index + 0.5f) * slot - laid.size.width / 2f)
                            .coerceIn(0f, size.width - laid.size.width),
                        y = floor + AXIS_ROW.toPx() / 2f - laid.size.height / 2f,
                    ),
                )
            }
        }
    }
}

/**
 * Da una posizione sulla fascia all'**ora del giorno** sotto il dito.
 *
 * Torna l'ora e non l'indice nella lista, e la differenza conta in un giorno
 * solo all'anno: quello del cambio d'ora non ha ventiquattro voci, e passare la
 * posizione sposterebbe la scelta di un'ora esatta proprio nel giorno in cui
 * nessuno se lo aspetta. `selectHour` conta sulle prime ventiquattro ore, che
 * cominciano a mezzanotte, quindi l'indice **e'** l'ora del giorno e i due
 * combaciano - ma solo perche' qui si passa l'ora.
 *
 * Il colpetto va solo quando l'ora cambia davvero, se no scorrendo il vibratore
 * non stacca piu' - la stessa regola della pioggia che vibra.
 */
private fun pick(
    x: Float,
    width: Float,
    hours: List<HourForecast>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    haptics: HapticFeedback,
) {
    if (hours.isEmpty() || width <= 0f) return
    val index = floor(x / width * hours.size).toInt().coerceIn(0, hours.lastIndex)
    val hour = hours[index].time.hour
    if (hour == selected) return
    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    onSelect(hour)
}

private const val NO_HOURS = "Le ore di questo giorno non sono nella previsione."

/**
 * L'altezza della fascia, in punti e **non in frazione dell'altezza**.
 *
 * E' la regola che la barra delle ore ha gia': le fasce si misurano in punti dal
 * bordo di sopra. Con una frazione dello spazio che avanza, su un telefono alto
 * le ventiquattro colonne diventerebbero ventiquattro pali alti mezzo schermo -
 * e la vasca, che e' l'eroe, si prenderebbe quel che resta invece del contrario.
 */
private val BAND_HEIGHT = 96.dp
private val BAND_HEIGHT_COMPACT = 84.dp

/** La riga sotto la fascia: le ore scritte e il pallino di adesso. */
private val AXIS_ROW = 18.dp

private val SENTENCE_GAP = 8.dp

/** Quanto della sua fetta si prende una colonna: il resto e' aria fra le ore. */
private const val COLUMN_SHARE = 0.56f

/**
 * L'altezza minima di un'ora bagnata.
 *
 * Serve per l'ora che ha un codice bagnato e zero millimetri: senza, non si
 * disegnerebbe affatto e la fascia direbbe "asciutto" sotto una frase che dice
 * "temporale".
 */
private val RAIN_MIN = 3.dp

private val NOW_DOT = 2.5.dp

/** Le annotazioni della fascia: sono note a margine, non righe da leggere. */
private val AXIS_SP = 9.sp
