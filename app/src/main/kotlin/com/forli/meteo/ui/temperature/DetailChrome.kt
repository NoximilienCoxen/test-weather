package com.forli.meteo.ui.temperature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.Wmo
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType
import com.forli.meteo.widget.paint.WidgetInk
import com.forli.meteo.widget.paint.weatherBody
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Palette del dettaglio
//
// Fissa e scura, indipendente dall'ora: le schede raccontano numeri, e un
// numero deve leggersi uguale a mezzogiorno e a mezzanotte. Il fondo della
// schermata invece resta quello del tema, cosi' il foglio che sale appartiene
// ancora al cielo da cui viene.
// ---------------------------------------------------------------------------
internal val CardBackground = Color(0xFF17171A)
internal val CardBorder     = Color(0xFF303036)
internal val PillWhite      = Color(0xFFFFFFFF)
internal val PillBlack      = Color(0xFF0B0B0D)
internal val PillTrack      = Color(0xFF2A2A2F)
internal val MetricLabel    = Color(0xFF8A8A92)
internal val MetricValue    = Color(0xFFFFFFFF)
internal val SunAccent      = Color(0xFFFFDE59)
internal val AirAccent      = Color(0xFF7EB8F7)
internal val RainAccent     = Color(0xFF3C8DF5)
internal val LineStrong     = Color(0xFFFFFFFF)

/** La curva di riferimento dietro quella colorata: l'effettiva sotto la percepita. */
internal val GhostLine = Color(0xFF9A9AA2)

/**
 * Il colore della linea "Norma" (media storica mensile).
 *
 * Bianco smorzato e non il grigio della griglia: deve distinguersi dalle
 * tacche orizzontali senza confondersi con la curva principale, che e'
 * colorata. Un bianco pieno attirerebbe troppo l'occhio su un riferimento
 * che e' secondario rispetto all'andamento reale.
 */
internal val NormLine = Color(0xFFB0B0BA)

internal fun DetailMode.accent(): Color = when (this) {
    DetailMode.TEMPERATURA    -> LineStrong
    DetailMode.SOLE           -> SunAccent
    DetailMode.PRECIPITAZIONI -> RainAccent
    DetailMode.ARIA           -> AirAccent
}

// ---------------------------------------------------------------------------
// Barra in alto: freccia indietro e titolo
// ---------------------------------------------------------------------------

/**
 * La freccia e' disegnata, non importata.
 *
 * Il progetto non ha `material-icons-extended` e non vale mezzo megabyte di
 * dipendenza per tre segmenti: e' la stessa scelta gia' fatta per il pulsante
 * delle impostazioni, che e' tre righe su una tela.
 */
@Composable
internal fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        BackArrow(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = title,
            style = MeteoType.label,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                // Lo stesso spazio a destra che occupa la freccia a sinistra:
                // senza, un titolo lungo scivolerebbe sotto la freccia e il
                // centro del testo non sarebbe il centro dello schermo.
                .padding(horizontal = 44.dp),
        )
    }
}

@Composable
private fun BackArrow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMeteoColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val y = size.height / 2f
            val tipX = size.width * 0.08f
            val wing = size.height * 0.32f
            val stroke = size.height * 0.11f
            drawLine(colors.text, Offset(size.width, y), Offset(tipX, y), stroke, StrokeCap.Round)
            drawLine(
                colors.text,
                Offset(tipX, y),
                Offset(tipX + wing, y - wing),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                colors.text,
                Offset(tipX, y),
                Offset(tipX + wing, y + wing),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Selettori
// ---------------------------------------------------------------------------

/**
 * Le linguette delle modalita'.
 *
 * Scorrevole in orizzontale e non a larghezze uguali: i nomi sono lunghi in
 * modo diverso, e comprimerli tutti alla misura del piu' largo li spezzerebbe
 * a meta'. Lo scorrimento non si vede finche' ci stanno.
 *
 * **Sostituisce il carosello a pagine**, e non e' una preferenza di stile: il
 * gesto orizzontale del carosello era lo stesso che deve far girare la cifra,
 * e uno dei due doveva cedere.
 */
@Composable
internal fun ModeChips(
    modes: List<DetailMode>,
    selected: DetailMode,
    onSelect: (DetailMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier.horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEach { mode ->
            val active = mode == selected
            val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (active) PillWhite else PillTrack)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(mode) },
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        // chipLabel e' il nome breve (Temp/Sole/Pioggia/Vento)
                        text = mode.chipLabel,
                        style = MeteoType.caption,
                        color = if (active) PillBlack else MetricLabel,
                    )
                }
        }
    }
}

/**
 * Due pillole affiancate a larghezza uguale: EFFETTIVA / PERCEPITI.
 *
 * A larghezza uguale e non a misura del testo, perche' qui le due voci sono
 * alternative dello stesso valore e devono pesare uguale: una piu' larga
 * dell'altra suggerirebbe che sia quella giusta.
 */
@Composable
internal fun SplitPills(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (active) PillWhite else PillTrack)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(index) },
                    )
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                // La spunta affianca il testo solo sull'opzione attiva;
                // sull'inattiva non occupa spazio per non spostare il testo.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (active) {
                        // Spunta disegnata come Canvas per evitare
                        // la dipendenza da material-icons-extended.
                        Canvas(modifier = androidx.compose.ui.Modifier.size(11.dp)) {
                            val w = size.width
                            val h = size.height
                            drawLine(
                                color = PillBlack,
                                start = androidx.compose.ui.geometry.Offset(0f, h * 0.55f),
                                end = androidx.compose.ui.geometry.Offset(w * 0.35f, h),
                                strokeWidth = h * 0.18f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            )
                            drawLine(
                                color = PillBlack,
                                start = androidx.compose.ui.geometry.Offset(w * 0.35f, h),
                                end = androidx.compose.ui.geometry.Offset(w, h * 0.1f),
                                strokeWidth = h * 0.18f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            )
                        }
                    }
                    Text(
                        text = label,
                        style = MeteoType.caption,
                        color = if (active) PillBlack else MetricLabel,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Scheda scura, con bordo appena accennato
// ---------------------------------------------------------------------------
@Composable
internal fun DetailCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(0.5.dp, CardBorder, RoundedCornerShape(18.dp))
            .background(CardBackground),
        content = content,
    )
}

// ---------------------------------------------------------------------------
// Illustrazioni del tempo
// ---------------------------------------------------------------------------

/**
 * L'inchiostro con cui si disegnano i corpi celesti dentro le schede.
 *
 * E' quello dei widget, e apposta: sono gli stessi corpi illuminati dalla
 * stessa luce, e riscriverli qui vorrebbe dire avere due soli che invecchiano
 * separatamente. `night = true` perche' le schede sono scure a qualunque ora.
 */
internal fun detailInk(): WidgetInk = WidgetInk(
    background = 0,
    primary = MetricValue,
    secondary = MetricLabel,
    night = true,
)

/** L'illustrazione del tempo di un giorno, grande quanto il riquadro dato. */
@Composable
internal fun WeatherGlyph(
    weatherCode: Int?,
    isDay: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = remember { detailInk() }
    val family = Wmo.family(weatherCode)
    Canvas(modifier) {
        weatherBody(
            box = Rect(0f, 0f, size.width, size.height),
            family = family,
            isDay = isDay,
            ink = ink,
        )
    }
}

/**
 * Sole o luna in miniatura, piatti.
 *
 * Sopra il grafico orario ce ne stanno una dozzina: passarli tutti dal
 * renderer dei corpi - sfere con alone e gradienti - vorrebbe dire pagare una
 * scultura per dire soltanto "qui e' giorno". A quattordici punti di lato la
 * differenza non si vede, il costo si'.
 */
internal fun DrawScope.skyMark(
    center: Offset,
    radius: Float,
    isDay: Boolean,
    behind: Color = CardBackground,
) {
    if (isDay) {
        drawCircle(SunAccent, radius * 0.60f, center)
        val inner = radius * 0.76f
        val outer = radius * 1.02f
        repeat(8) { i ->
            val angle = (Math.PI.toFloat() / 4f) * i
            val dx = cos(angle)
            val dy = sin(angle)
            drawLine(
                color = SunAccent,
                start = Offset(center.x + dx * inner, center.y + dy * inner),
                end = Offset(center.x + dx * outer, center.y + dy * outer),
                strokeWidth = radius * 0.20f,
                cap = StrokeCap.Round,
            )
        }
    } else {
        // La falce e' un disco meno un disco: la seconda circonferenza e' del
        // colore che sta dietro, quindi ritaglia invece di sovrapporsi.
        drawCircle(MoonPale, radius * 0.82f, center)
        drawCircle(
            color = behind,
            radius = radius * 0.72f,
            center = Offset(center.x + radius * 0.40f, center.y - radius * 0.26f),
        )
    }
}

private val MoonPale = Color(0xFFA9C8F0)

// ---------------------------------------------------------------------------
// Colore della temperatura
// ---------------------------------------------------------------------------

/**
 * La scala di colore dei gradi, in Celsius.
 *
 * In Celsius e non nell'unita' scelta: il colore deve dire **che caldo fa**, e
 * passando ai Fahrenheit gli stessi trentatre gradi diventerebbero novantuno,
 * cioe' un altro punto della scala. La conversione riguarda cio' che si legge,
 * non cio' che si vede.
 */
private val TempStops: List<Pair<Float, Color>> = listOf(
    -25f to Color(0xFF5B3E9B),
    -12f to Color(0xFF3F63C8),
    0f to Color(0xFF3C8DF5),
    8f to Color(0xFF3FA8A0),
    14f to Color(0xFF7FAE43),
    19f to Color(0xFFB6B22F),
    24f to Color(0xFFE2A428),
    29f to Color(0xFFEE7E2A),
    34f to Color(0xFFDE5228),
    42f to Color(0xFFAF2130),
)

internal fun temperatureTint(celsius: Float): Color {
    if (celsius <= TempStops.first().first) return TempStops.first().second
    if (celsius >= TempStops.last().first) return TempStops.last().second
    for (i in 0 until TempStops.lastIndex) {
        val (lowT, lowC) = TempStops[i]
        val (highT, highC) = TempStops[i + 1]
        if (celsius in lowT..highT) {
            val t = (celsius - lowT) / (highT - lowT)
            return lerp(lowC, highC, t)
        }
    }
    return TempStops.last().second
}

/**
 * Le fermate di un gradiente verticale che copre l'intervallo dato.
 *
 * Dal caldo in alto al freddo in basso, campionate a passo fisso: il gradiente
 * di Compose interpola linearmente fra le fermate, quindi con le sole due
 * estreme una giornata da quindici a trentacinque gradi passerebbe dal rosso
 * all'oliva **saltando** tutta la scala di mezzo.
 */
internal fun temperatureRamp(loCelsius: Float, hiCelsius: Float, alpha: Float = 1f): List<Color> {
    val steps = 8
    return (0..steps).map { i ->
        val value = hiCelsius + (loCelsius - hiCelsius) * i / steps
        temperatureTint(value).copy(alpha = alpha)
    }
}

// ---------------------------------------------------------------------------
// Spline — Catmull-Rom
// ---------------------------------------------------------------------------

internal fun buildLinePath(points: List<Offset?>): Path {
    val result = Path()
    segmentRuns(points).forEach { run -> result.addPath(catmullRomPath(run)) }
    return result
}

internal fun buildAreaPath(points: List<Offset?>, baseline: Float): Path {
    val result = Path()
    segmentRuns(points).forEach { run ->
        result.addPath(
            Path().apply {
                addPath(catmullRomPath(run))
                lineTo(run.last().x, baseline)
                lineTo(run.first().x, baseline)
                close()
            },
        )
    }
    return result
}

/** Divide la serie nullable in sotto-sequenze continue di punti validi. */
private fun segmentRuns(points: List<Offset?>): List<List<Offset>> {
    val runs = mutableListOf<List<Offset>>()
    var run = mutableListOf<Offset>()
    points.forEach { pt ->
        if (pt == null) {
            if (run.size > 1) runs += run.toList()
            run = mutableListOf()
        } else {
            run += pt
        }
    }
    if (run.size > 1) runs += run.toList()
    return runs
}

/**
 * Catmull-Rom, convertita in cubiche di Bezier.
 *
 * Le tangenti in ogni punto derivano dai due adiacenti: la curva passa per
 * tutti i punti senza oscillare fra l'uno e l'altro, che e' esattamente il
 * difetto di una spline che non guarda i vicini.
 *
 * `move` decide se cominciare un tratto nuovo o proseguire quello aperto. Serve
 * al nastro fra massime e minime: e' **un** contorno chiuso, e con due `moveTo`
 * diventerebbero due tratti separati che il riempimento non sa collegare.
 */
internal fun Path.catmullRomTo(points: List<Offset>, move: Boolean) {
    if (points.isEmpty()) return
    if (move) moveTo(points[0].x, points[0].y) else lineTo(points[0].x, points[0].y)
    if (points.size == 1) return
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
}

internal fun catmullRomPath(points: List<Offset>): Path =
    Path().apply { catmullRomTo(points, move = true) }

/** Il contorno chiuso fra due curve: la prima in avanti, la seconda all'indietro. */
internal fun ribbonPath(upper: List<Offset>, lower: List<Offset>): Path {
    val path = Path()
    if (upper.size < 2 || lower.size < 2) return path
    path.catmullRomTo(upper, move = true)
    path.catmullRomTo(lower.reversed(), move = false)
    path.close()
    return path
}

/**
 * Nastro fra due curve con gestione corretta dei null.
 *
 * [ribbonPath] riceve liste gia' filtrate con filterNotNull(): questo comprime
 * gli indici e sposta le X — il punto che era in posizione 4 viene disegnato
 * alla X della posizione 3, e il nastro si chiude nel posto sbagliato.
 *
 * Questa funzione preserva la posizione X originale di ogni punto: per ogni
 * sequenza contigua in cui sia upper[i] che lower[i] sono non-null, disegna
 * un segmento di area separato. I buchi (dove uno dei due e' null) vengono
 * saltati senza alterare le posizioni dei punti successivi.
 */
internal fun nullSafeRibbonPath(upper: List<Offset?>, lower: List<Offset?>): Path {
    val result = Path()
    val n = minOf(upper.size, lower.size)
    var runU = mutableListOf<Offset>()
    var runL = mutableListOf<Offset>()
    for (i in 0 until n) {
        val u = upper[i]
        val l = lower[i]
        if (u != null && l != null) {
            runU += u
            runL += l
        } else {
            if (runU.size >= 2) result.addPath(ribbonPath(runU, runL))
            runU = mutableListOf()
            runL = mutableListOf()
        }
    }
    if (runU.size >= 2) result.addPath(ribbonPath(runU, runL))
    return result
}
