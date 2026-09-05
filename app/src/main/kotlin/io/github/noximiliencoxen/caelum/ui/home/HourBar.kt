package io.github.noximiliencoxen.caelum.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.temperature.buildLinePath
import io.github.noximiliencoxen.caelum.ui.temperature.temperatureRamp
import io.github.noximiliencoxen.caelum.ui.temperature.temperatureTint
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.MeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.MeteoType
import kotlin.math.floor

/**
 * Le ore del giorno come una striscia continua, colorata dal meteo di ciascuna.
 *
 * Una fascia azzurra dice a colpo d'occhio quando piove, senza bisogno di
 * scorrere fin li' per scoprirlo: e' la barra stessa a raccontare la giornata.
 *
 * Le tacche non sono decorazione. Senza, la striscia sembra continua e non si
 * capisce quante posizioni abbia: se un'ora non si riesce a centrare, non c'e'
 * modo di accorgersi che il problema e' la mira e non la barra.
 *
 * **L'ora sta in una bolla sopra il cursore**, e prima stava scritta sotto la
 * barra. Sotto la barra sta anche il pollice che la sta scorrendo: mentre si
 * cerca un'ora, l'unica cosa che direbbe quale ora si e' trovata era coperta
 * dalla mano, e per leggerla bisognava lasciare andare - cioe' aver gia' scelto.
 * Sopra il cursore invece la si legge **mentre** si sceglie, che e' il momento
 * in cui serve.
 *
 * Con l'ora se n'e' andato di li' anche il tasto per tornare all'ora vera, che
 * al testo era solo appiccicato: adesso e' un bersaglio suo
 * (vedi `HomeScreen`), e le due funzioni non se lo contendono piu'.
 *
 * Tutto sta in **una sola** tela. La bolla deve puntare esattamente dove punta
 * il cursore, e due composable che si accordano sulla geometria vanno d'accordo
 * finche' nessuno tocca l'uno senza l'altro. Qui c'e' una geometria sola.
 */
@Composable
fun HourBar(
    hours: List<HourForecast>,
    selected: Int,
    nowIndex: Int,
    /** Alba e tramonto del giorno mostrato, se l'API li ha dati. */
    sunrise: java.time.LocalDateTime? = null,
    sunset: java.time.LocalDateTime? = null,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val haptics = LocalHapticFeedback.current
    if (hours.isEmpty()) return

    val position by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 700f),
        label = "ora",
    )

    // Il riconoscitore di gesti vive dentro un pointerInput che viene ricreato
    // solo quando cambia il numero di ore. Tutto quello che la sua lambda
    // cattura resta fermo al valore che aveva la prima volta, e leggere di li'
    // l'ora selezionata significava confrontarsi per sempre con quella
    // dell'apertura: l'ora corrente diventava l'unica irraggiungibile della
    // giornata, perche' il confronto la dichiarava gia' scelta. Era questo a far
    // sembrare che la barra "saltasse" un'ora.
    val liveSelected by rememberUpdatedState(selected)
    val liveOnSelect by rememberUpdatedState(onSelect)

    // Il testo della bolla si misura **in composizione**, non nel disegno:
    // cambia una volta per ora, mentre il disegno gira a ogni fotogramma del
    // dito. La cache a zero non e' una precauzione generica: quella del
    // misuratore di Compose ignora colore e pennello, e con il cielo che tinge
    // `pillText` lungo la giornata restituirebbe la stessa riga col colore di
    // stamattina.
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val label = hourLabel(hours.getOrNull(selected))
    val bubbleStyle = MeteoType.metric.copy(color = colors.pillText)
    val bubbleText = remember(label, bubbleStyle) {
        measurer.measure(text = label, style = bubbleStyle)
    }

    // L'altezza della bolla la decide **il testo misurato**, non una costante.
    // Con il carattere di sistema ingrandito quindici punti ne diventano
    // ventidue, e una bolla alta ventiquattro fissi taglierebbe l'ora a meta' -
    // che e' lo stesso difetto per cui esiste `MeteoLayout`. Da qui viene anche
    // l'altezza della barra, cosi' il conto torna per forza invece che a occhio.
    val bubbleHeight = with(LocalDensity.current) { bubbleText.size.height.toDp() } + 8.dp
    val barHeight = CHART_HEIGHT + CHART_GAP + bubbleHeight + TAIL_HEIGHT + THUMB_OVERHANG * 2 +
        TRACK_HEIGHT + NOTE_GAP + NOW_DOT_RADIUS * 2 + 1.dp

    fun indexAt(x: Float, width: Float): Int =
        floor(x / width * hours.size).toInt().coerceIn(0, hours.lastIndex)

    fun choose(index: Int) {
        if (index != liveSelected) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            liveOnSelect(index)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            // Una tela e' muta: chi ascolta la schermata trovava una striscia
            // senza nome dove chi guarda ha ventiquattro ore da scegliere.
            .semantics { contentDescription = "Ora mostrata: $label" }
            // Un solo riconoscitore per tocco e trascinamento, e nessuna soglia
            // da superare. Con due riconoscitori separati il primo consuma
            // l'evento di discesa e il secondo annulla il proprio scorrimento:
            // il risultato e' una barra che ogni tanto ignora il dito, e ore
            // che sembrano non esistere.
            .pointerInput(hours.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    choose(indexAt(down.position.x, size.width.toFloat()))
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        choose(indexAt(change.position.x, size.width.toFloat()))
                        change.consume()
                    }
                }
            },
    ) {
        // Le fasce si misurano in punti dal bordo di sopra, non in frazioni
        // dell'altezza: una bolla che si allarga o si stringe col telefono
        // conterrebbe un testo che invece resta della sua misura.
        val chartBand = CHART_HEIGHT.toPx()
        val bubbleTop = chartBand + CHART_GAP.toPx()
        val bubbleHeightPx = bubbleHeight.toPx()
        val bubbleBottom = bubbleTop + bubbleHeightPx
        val thumbTop = bubbleBottom + TAIL_HEIGHT.toPx()
        val trackHeight = TRACK_HEIGHT.toPx()
        val top = thumbTop + THUMB_OVERHANG.toPx()
        val thumbBottom = top + trackHeight + THUMB_OVERHANG.toPx()
        val radius = trackHeight / 2f
        val slot = size.width / hours.size

        // ── Il diagramma della giornata ────────────────────────────────────────
        //
        // Una fascia bassa sopra la barra: la temperatura come linea, la pioggia
        // come colonnine sotto di essa. E' lo stesso disegno di "ANDAMENTO DELLA
        // GIORNATA" nel dettaglio - stessa spline, stessa scala di colore - ma
        // senza assi, numeri, griglia e tocco: qui non e' una cosa da leggere
        // punto per punto, e' la forma della giornata vista di sfuggita mentre
        // si sceglie un'ora. Chi vuole i numeri apre il dettaglio.
        //
        // Sta **dentro questa tela** e non in un composable sopra, per la
        // ragione scritta in cima al file: la scala orizzontale dev'essere la
        // stessa della pista, e due tele che si accordano sulla geometria vanno
        // d'accordo finche' qualcuno non tocca una sola delle due. Qui `slot` e'
        // uno.
        val gradi = hours.map { it.temperature?.toFloat() }
        val noti = gradi.filterNotNull()
        if (noti.size >= 2) {
            val minimo = noti.min()
            val massimo = noti.max()
            // Una giornata piatta non deve diventare una linea che ondeggia:
            // senza questo pavimento, mezzo grado di scarto verrebbe stirato su
            // tutta l'altezza della fascia e sembrerebbe uno sbalzo.
            val escursione = (massimo - minimo).coerceAtLeast(GRADI_MINIMI)
            val margine = CHART_INSET.toPx()

            // La pioggia per prima, cosi' la linea le passa sopra e resta
            // leggibile anche dentro un temporale.
            val mmMassimi = hours.mapNotNull { it.precipitation?.toFloat() }.maxOrNull() ?: 0f
            if (mmMassimi > 0f) {
                val piedi = chartBand
                val cima = chartBand * RAIN_SHARE
                hours.forEachIndexed { index, hour ->
                    val mm = hour.precipitation?.toFloat() ?: 0f
                    if (mm <= 0f) return@forEachIndexed
                    val altezza = (mm / mmMassimi).coerceIn(0f, 1f) * (piedi - cima)
                    drawRect(
                        color = RAIN_TINT.copy(alpha = 0.50f),
                        topLeft = Offset(index * slot + slot * 0.30f, piedi - altezza),
                        size = Size(slot * 0.40f, altezza),
                    )
                }
            }

            val punti = gradi.mapIndexed { index, valore ->
                valore?.let {
                    Offset(
                        x = (index + 0.5f) * slot,
                        y = chartBand - margine -
                            ((it - minimo) / escursione) * (chartBand - margine * 2f),
                    )
                }
            }
            drawPath(
                path = buildLinePath(punti),
                // Il gradiente e' verticale e copre la fascia: cosi' il tratto
                // caldo sta in alto e quello fresco in basso, che e' dove la
                // curva li porta. Un colore solo direbbe la forma e non i gradi.
                brush = Brush.verticalGradient(
                    colors = temperatureRamp(minimo, massimo),
                    startY = margine,
                    endY = chartBand - margine,
                ),
                style = Stroke(
                    width = CHART_STROKE.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        // Ritaglio sulla pista arrotondata e poi dipingo le ore dentro: cosi'
        // gli estremi sono tondi senza dover coprire nulla.
        val track = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, top, size.width, top + trackHeight),
                    cornerRadius = CornerRadius(radius, radius),
                ),
            )
        }
        clipPath(track) {
            hours.forEachIndexed { index, hour ->
                drawRect(
                    color = tintOf(hour, colors),
                    topLeft = Offset(index * slot, top),
                    // Mezzo pixel in piu' evita la riga di fondo fra un'ora e
                    // l'altra dovuta all'antialiasing.
                    size = Size(slot + 0.5f, trackHeight),
                )
            }

            // Una tacca ogni sei ore: abbastanza da far vedere la scansione,
            // poche da non trasformare la barra in un righello.
            for (index in hours.indices) {
                if (index == 0 || index % 6 != 0) continue
                drawRect(
                    color = colors.background.copy(alpha = 0.45f),
                    topLeft = Offset(index * slot - 0.5f, top),
                    size = Size(1f, trackHeight),
                )
            }
        }

        // Sotto la pista c'e' una fascia di annotazioni: l'ora vera, e i due
        // momenti in cui la giornata cambia luce.
        val noteY = thumbBottom + NOTE_GAP.toPx()

        // Dove sta l'ora vera. Scorrendo la barra si guarda un'altra ora, e
        // senza questo segno non ci sarebbe piu' modo di tornare a casa.
        if (nowIndex in hours.indices) {
            drawCircle(
                color = colors.text,
                radius = NOW_DOT_RADIUS.toPx(),
                center = Offset((nowIndex + 0.5f) * slot, noteY),
            )
        }

        // Alba e tramonto al minuto giusto. Il tono della pista gia' dice
        // giorno e notte, ma lo dice a scatti d'ora: la tacca dice **dove**
        // cade il confine, che finora si poteva solo intuire fra una casella e
        // la successiva. Due trattini e non due pallini, cosi' non si
        // confondono con l'ora corrente che vive sulla stessa riga.
        val span = hours.size * 60f
        val origin = hours.first().time
        listOfNotNull(sunrise, sunset).forEach { moment ->
            val minutes = java.time.Duration.between(origin, moment).toMinutes().toFloat()
            if (minutes < 0f || minutes > span) return@forEach
            val x = minutes / span * size.width
            val halfWidth = 1.dp.toPx()
            val halfHeight = 3.dp.toPx()
            drawRect(
                color = colors.label,
                topLeft = Offset(x - halfWidth, noteY - halfHeight),
                size = Size(halfWidth * 2f, halfHeight * 2f),
            )
        }

        // `position` si legge **qui**, dentro il disegno. E' il valore che si
        // muove col dito: letto in composizione rifarebbe misura e
        // posizionamento di tutta la colonna sessanta volte al secondo per
        // spostare una pillola di due punti.
        val thumbX = (position + 0.5f) * slot
        val thumbWidth = THUMB_WIDTH.toPx()
        val ring = THUMB_RING.toPx()

        // L'anello del colore del fondo prima del corpo: il cursore e' chiaro
        // come `pillBackground` e la pista sotto di lui, di giorno e con l'ora
        // asciutta, e' altrettanto chiara. Senza lo stacco il cursore spariva
        // proprio nelle ore in cui si guarda di piu'.
        drawRoundRect(
            color = colors.background,
            topLeft = Offset(thumbX - thumbWidth / 2f - ring, thumbTop - ring),
            size = Size(thumbWidth + ring * 2f, thumbBottom - thumbTop + ring * 2f),
            cornerRadius = CornerRadius(thumbWidth, thumbWidth),
        )
        drawRoundRect(
            color = colors.pillBackground,
            topLeft = Offset(thumbX - thumbWidth / 2f, thumbTop),
            size = Size(thumbWidth, thumbBottom - thumbTop),
            cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f),
        )

        // La bolla, per ultima: sta sopra tutto perche' e' cio' che si legge.
        val bubbleWidth = bubbleText.size.width + BUBBLE_PADDING.toPx() * 2f
        val bubbleLeft = if (size.width <= bubbleWidth) {
            (size.width - bubbleWidth) / 2f
        } else {
            (thumbX - bubbleWidth / 2f).coerceIn(0f, size.width - bubbleWidth)
        }
        drawRoundRect(
            color = colors.pillBackground,
            topLeft = Offset(bubbleLeft, bubbleTop),
            size = Size(bubbleWidth, bubbleHeightPx),
            cornerRadius = CornerRadius(bubbleHeightPx / 2f, bubbleHeightPx / 2f),
        )

        // La codina ha la punta sul cursore e la base dentro la bolla. Alle due
        // estremita' della barra la bolla si ferma al bordo mentre il cursore
        // va avanti: se la codina restasse al centro della bolla, la bolla
        // finirebbe per indicare un'ora che non e' quella scelta. Cosi' invece
        // si inclina e continua a puntare l'ora giusta.
        val tailHalf = TAIL_HALF_WIDTH.toPx()
        val inset = tailHalf + 4.dp.toPx()
        val baseX = thumbX.coerceIn(
            bubbleLeft + inset,
            (bubbleLeft + bubbleWidth - inset).coerceAtLeast(bubbleLeft + inset),
        )
        drawPath(
            path = Path().apply {
                moveTo(baseX - tailHalf, bubbleBottom - 1f)
                lineTo(baseX + tailHalf, bubbleBottom - 1f)
                lineTo(thumbX, thumbTop)
                close()
            },
            color = colors.pillBackground,
        )

        drawText(
            textLayoutResult = bubbleText,
            topLeft = Offset(
                x = bubbleLeft + BUBBLE_PADDING.toPx(),
                y = bubbleTop + (bubbleHeightPx - bubbleText.size.height) / 2f,
            ),
        )
    }
}

// Le misure della barra, in un posto solo. L'altezza totale e' la loro somma
// piu' la bolla (`barHeight`, che si calcola sopra): cosi' non c'e' un numero
// da tenere allineato a mano con le fasce che deve contenere - era il modo in
// cui una fascia in piu' finiva per uscire dal fondo senza che si vedesse.
private val BUBBLE_PADDING = 10.dp
private val TAIL_HEIGHT = 6.dp
private val TAIL_HALF_WIDTH = 5.dp
private val THUMB_OVERHANG = 5.dp
private val THUMB_WIDTH = 10.dp
private val THUMB_RING = 2.dp
private val TRACK_HEIGHT = 18.dp
private val NOTE_GAP = 6.dp
private val NOW_DOT_RADIUS = 2.5.dp

// ── Il diagramma sopra la barra ───────────────────────────────────────────────

/**
 * Quanto e' alta la fascia del grafico.
 *
 * Ventidue punti, ed e' un tetto piu' che una misura: quello che c'e' sotto -
 * bolla, pista, annotazioni - non si puo' stringere, e la scultura in mezzo allo
 * schermo vive di cio' che avanza. Sopra i trenta si vedeva la cifra gigante
 * farsi piu' piccola, che e' un prezzo troppo alto per un disegno che si guarda
 * di sfuggita.
 */
private val CHART_HEIGHT = 22.dp
private val CHART_GAP = 3.dp

/** Aria sopra e sotto la curva, perche' i colmi non tocchino i bordi. */
private val CHART_INSET = 2.5.dp
private val CHART_STROKE = 1.6.dp

/** Quanta parte della fascia possono prendersi le colonnine di pioggia. */
private const val RAIN_SHARE = 0.55f

/**
 * Sotto questa escursione la giornata si considera piatta.
 *
 * Tre gradi: la curva si normalizza sempre fra minimo e massimo del giorno, e
 * senza un pavimento una giornata da 24,2 a 24,6 riempirebbe tutta la fascia -
 * una collina inventata da quattro decimi di grado.
 */
private const val GRADI_MINIMI = 3f

/** Quanto l'asciutto e il nuvoloso virano verso il colore dei gradi. */
private const val GRADI_ASCIUTTO = 0.62f
private const val GRADI_NUVOLOSO = 0.32f

private val RAIN_TINT = Color(0xFF2C7BF2)

/** Colore di un'ora: asciutto resta neutro, il resto si dichiara. */
/**
 * Il colore di un'ora sulla pista.
 *
 * **Il bagnato batte il caldo.** Pioggia, neve e temporale tengono il loro
 * colore intero: sono la cosa che si cerca guardando la barra - "quando piove" -
 * e annacquarli coi gradi renderebbe una mattina di pioggia calda meno azzurra
 * di una fredda, cioe' meno riconoscibile proprio dove conta.
 *
 * **L'asciutto invece prende i gradi.** Prima era `colors.line` e basta, e una
 * giornata di sole - che qui e' la maggioranza dei giorni - usciva ventiquattro
 * caselle grigie tutte uguali: una barra "colorata dal meteo" che sul meteo piu'
 * comune non diceva niente. Adesso vira verso [temperatureTint], la stessa scala
 * dei grafici del dettaglio, quindi le ore fresche del mattino e il colmo del
 * pomeriggio si distinguono. Il nuvoloso la prende a meta': e' comunque una
 * giornata in cui il cielo conta piu' del termometro.
 */
private fun tintOf(hour: HourForecast, colors: MeteoColors): Color {
    val base = when (Wmo.family(hour.weatherCode)) {
        Wmo.Family.ASCIUTTO -> hour.temperature.warming(colors.line, GRADI_ASCIUTTO)
        Wmo.Family.NUVOLOSO ->
            hour.temperature.warming(colors.label.copy(alpha = 0.55f), GRADI_NUVOLOSO)
        Wmo.Family.NEBBIA -> colors.label.copy(alpha = 0.40f)
        Wmo.Family.PIOGGIA -> RAIN_TINT
        Wmo.Family.NEVE -> Color(0xFF8FC7F5)
        Wmo.Family.TEMPORALE -> Color(0xFF5B4BC4)
    }
    // La notte smorza, cosi' la striscia racconta anche il passare del giorno.
    return if (hour.isDay) base else base.copy(alpha = base.alpha * 0.55f)
}

/**
 * Il colore di partenza spostato verso quello dei gradi, di [quanto].
 *
 * L'opacita' resta quella di partenza: `lerp` interpolerebbe anche quella, e il
 * nuvoloso - che vive al 55% - si sarebbe schiarito verso l'opaco proprio nelle
 * ore piu' calde, cambiando due cose mentre se ne intendeva una.
 */
private fun Double?.warming(base: Color, quanto: Float): Color {
    val celsius = this?.toFloat() ?: return base
    return lerp(base, temperatureTint(celsius).copy(alpha = base.alpha), quanto)
}

internal fun nearestHourIndex(hours: List<HourForecast>, target: java.time.LocalDateTime): Int {
    if (hours.isEmpty()) return 0
    var best = 0
    var bestDistance = Long.MAX_VALUE
    hours.forEachIndexed { index, hour ->
        val distance = kotlin.math.abs(
            java.time.Duration.between(target, hour.time).toMinutes(),
        )
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}

internal fun hourLabel(hour: HourForecast?): String =
    hour?.time?.hour?.let { "%02d:00".format(it) } ?: "--"
