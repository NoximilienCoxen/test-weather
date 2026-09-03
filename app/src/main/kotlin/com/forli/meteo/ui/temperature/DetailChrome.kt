package com.forli.meteo.ui.temperature

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.forli.meteo.data.Wmo
import com.forli.meteo.ui.theme.LocalMeteoAccents
import com.forli.meteo.ui.theme.MeteoAccents
import com.forli.meteo.widget.paint.WidgetInk
import com.forli.meteo.widget.paint.weatherBody
import kotlin.math.cos
import kotlin.math.sin

/**
 * Quello che resta di questo file dopo il passaggio a Material 3: **geometria**.
 *
 * La tavolozza che stava qui - `CardBackground`, `MetricLabel`, `PillWhite` e
 * compagnia - era una delle tre palette parallele del progetto e se n'e'
 * andata: i colori vengono da `MaterialTheme.colorScheme`, che li calcola per
 * contrasto, e le tinte delle grandezze da [LocalMeteoAccents].
 *
 * Le spline, il nastro fra due curve, la scala di colore dei gradi e il disegno
 * dei corpi celesti invece restano: sono matematica e disegno, non stile, e
 * hanno gia' tre chiamanti a testa.
 */

// ---------------------------------------------------------------------------
// La tinta di una grandezza
// ---------------------------------------------------------------------------

/**
 * Il colore che identifica una pagina del dettaglio.
 *
 * Per la temperatura non c'e' una tinta sola e non e' una mancanza: la
 * temperatura **ha gia'** una scala di colore che dice quanto caldo fa
 * ([temperatureTint]), e sovrapporle un accento unico la contraddirebbe. Li' si
 * usa il colore del testo, e a colorare pensa la curva.
 */
@Composable
fun DetailMode.accent(): Color {
    val accents = LocalMeteoAccents.current
    return when (this) {
        DetailMode.TEMPERATURA -> MaterialTheme.colorScheme.onSurface
        DetailMode.SOLE -> accents.sun
        DetailMode.PRECIPITAZIONI -> accents.rain
        DetailMode.VENTO -> accents.wind
        DetailMode.ARIA -> accents.air
    }
}

/** La stessa scelta, fuori dalla composizione: per chi disegna dentro una tela. */
fun DetailMode.accentOf(accents: MeteoAccents, onSurface: Color): Color = when (this) {
    DetailMode.TEMPERATURA -> onSurface
    DetailMode.SOLE -> accents.sun
    DetailMode.PRECIPITAZIONI -> accents.rain
    DetailMode.VENTO -> accents.wind
    DetailMode.ARIA -> accents.air
}

// ---------------------------------------------------------------------------
// Illustrazioni del tempo
// ---------------------------------------------------------------------------

/**
 * L'inchiostro con cui si disegnano i corpi celesti dentro le schede.
 *
 * E' quello dei widget, e apposta: sono gli stessi corpi illuminati dalla
 * stessa luce, e riscriverli qui vorrebbe dire avere due soli che invecchiano
 * separatamente.
 */
internal fun detailInk(primary: Color, secondary: Color): WidgetInk = WidgetInk(
    background = 0,
    primary = primary,
    secondary = secondary,
    night = true,
)

/** L'illustrazione del tempo di un giorno, grande quanto il riquadro dato. */
@Composable
fun WeatherGlyph(
    weatherCode: Int?,
    isDay: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.onSurface
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = remember(primary, secondary) { detailInk(primary, secondary) }
    val family = Wmo.family(weatherCode)
    // Una tela e' muta: senza questo, chi ascolta la schermata trova un
    // riquadro vuoto dove chi guarda vede il tempo che fara'.
    val spoken = Wmo.condition(weatherCode).lowercase()
    Canvas(
        modifier.semantics {
            contentDescription = if (isDay) spoken else "$spoken, di notte"
        },
    ) {
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
 *
 * **[behind] non ha piu' un valore di riposo**, e non e' pignoleria: la falce
 * e' un disco meno un disco, e il secondo disco va del colore di cio' che sta
 * sotto. Il valore di riposo era il fondo delle schede, mentre il grafico del
 * dettaglio di un giorno si disegna direttamente sul fondo del pannello: due
 * grigi diversi, e il ritaglio si vedeva come una macchia scura sopra la luna.
 * Chi disegna sa su cosa sta disegnando; questa funzione no.
 */
fun DrawScope.skyMark(
    center: Offset,
    radius: Float,
    isDay: Boolean,
    behind: Color,
    sun: Color,
    moon: Color,
) {
    if (isDay) {
        drawCircle(sun, radius * 0.60f, center)
        val inner = radius * 0.76f
        val outer = radius * 1.02f
        repeat(8) { i ->
            val angle = (Math.PI.toFloat() / 4f) * i
            val dx = cos(angle)
            val dy = sin(angle)
            drawLine(
                color = sun,
                start = Offset(center.x + dx * inner, center.y + dy * inner),
                end = Offset(center.x + dx * outer, center.y + dy * outer),
                strokeWidth = radius * 0.20f,
                cap = StrokeCap.Round,
            )
        }
    } else {
        drawCircle(moon, radius * 0.82f, center)
        drawCircle(
            color = behind,
            radius = radius * 0.72f,
            center = Offset(center.x + radius * 0.40f, center.y - radius * 0.26f),
        )
    }
}

/** Il pallore della luna in miniatura. */
val MoonPale = Color(0xFFA9C8F0)

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

fun temperatureTint(celsius: Float): Color {
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
fun temperatureRamp(loCelsius: Float, hiCelsius: Float, alpha: Float = 1f): List<Color> {
    val steps = 8
    return (0..steps).map { i ->
        val value = hiCelsius + (loCelsius - hiCelsius) * i / steps
        temperatureTint(value).copy(alpha = alpha)
    }
}

// ---------------------------------------------------------------------------
// Spline — Catmull-Rom
// ---------------------------------------------------------------------------

fun buildLinePath(points: List<Offset?>): Path {
    val result = Path()
    segmentRuns(points).forEach { run -> result.addPath(catmullRomPath(run)) }
    return result
}

fun buildAreaPath(points: List<Offset?>, baseline: Float): Path {
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
fun Path.catmullRomTo(points: List<Offset>, move: Boolean) {
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

fun catmullRomPath(points: List<Offset>): Path =
    Path().apply { catmullRomTo(points, move = true) }

/** Il contorno chiuso fra due curve: la prima in avanti, la seconda all'indietro. */
fun ribbonPath(upper: List<Offset>, lower: List<Offset>): Path {
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
fun nullSafeRibbonPath(upper: List<Offset?>, lower: List<Offset?>): Path {
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
