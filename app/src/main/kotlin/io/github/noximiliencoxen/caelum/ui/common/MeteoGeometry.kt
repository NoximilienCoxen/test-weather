package io.github.noximiliencoxen.caelum.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.widget.paint.WidgetInk
import io.github.noximiliencoxen.caelum.widget.paint.weatherBody

/**
 * Il disegno in comune: l'illustrazione del tempo, la scala di colore dei gradi,
 * e la spline che passa per i punti.
 *
 * **Stava in `ui/temperature`, che era il pacchetto del foglio di dettaglio.**
 * Il foglio non c'e' piu' - le grandezze sono le sezioni del feed - e con lui se
 * ne sono andati i grafici che consumavano meta' di questo file: il sole in
 * miniatura sopra il grafico orario, l'area sotto la curva, il nastro fra
 * massime e minime. Quello che resta lo leggono la barra delle ventiquattro
 * ore, la striscia della settimana e le schede, e non ha piu' niente a che
 * vedere con la temperatura in particolare: e' geometria e sono tinte.
 *
 * `MaterialTheme.colorScheme` compare ancora, ed e' corretto: [WeatherGlyph] si
 * disegna dentro le superfici dei pannelli. Chi disegna sul cielo prende le
 * proprie tinte da `LocalMeteoColors`, dove sono gia' calcolate per contrasto.
 */
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
private fun Path.catmullRomTo(points: List<Offset>, move: Boolean) {
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

private fun catmullRomPath(points: List<Offset>): Path =
    Path().apply { catmullRomTo(points, move = true) }
