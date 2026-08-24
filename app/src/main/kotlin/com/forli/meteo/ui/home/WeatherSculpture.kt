package com.forli.meteo.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.forli.meteo.data.Wmo
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoColors
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

/**
 * Nuvole, sole e luna disegnati con la stessa luce della cifra: una sola
 * sorgente in alto a sinistra e la stessa rampa di grigi. E' questo che li fa
 * appartenere allo stesso mondo invece di sembrare icone incollate sopra.
 *
 * Niente compare o sparisce di colpo: la nuvola c'e' sempre e cambia
 * caratteristica con continuita' - si addensa, si scurisce, le gocce
 * aumentano. Un elemento che spunta a un'ora precisa si legge come un difetto,
 * non come informazione.
 */
@Composable
fun WeatherSculpture(
    weatherCode: Int?,
    precipitationMm: Double?,
    probability: Int?,
    isDay: Boolean,
    date: LocalDate,
    tilt: Offset,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current

    // Ogni carattere e' un valore continuo, cosi' lo scorrimento fra le ore
    // trasforma la scultura invece di sostituirla.
    val cloudiness by animateFloatAsState(
        targetValue = Wmo.cloudiness(weatherCode),
        animationSpec = spring(stiffness = 120f),
        label = "nuvolosita",
    )
    val wetness by animateFloatAsState(
        targetValue = (precipitationMm ?: 0.0).toFloat().coerceIn(0f, 6f) / 6f,
        animationSpec = spring(stiffness = 120f),
        label = "pioggia",
    )
    val confidence by animateFloatAsState(
        targetValue = ((probability ?: 0) / 100f).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 120f),
        label = "probabilita",
    )
    val nightness by animateFloatAsState(
        targetValue = if (isDay) 0f else 1f,
        animationSpec = spring(stiffness = 90f),
        label = "notte",
    )

    val fall by rememberInfiniteTransition(label = "caduta").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "gocce",
    )

    val phase = remember(date) { MoonPhase.at(date) }
    val drops = remember { List(28) { Random(it * 977).nextFloat() to Random(it * 613).nextFloat() } }

    Canvas(modifier) {
        val unit = size.minDimension
        // La scultura sta piu' vicina della cifra, quindi si sposta di piu'.
        val shift = Offset(tilt.x, tilt.y) * (unit * 0.045f)
        val centre = Offset(size.width / 2f, size.height * 0.46f) + shift

        drawCelestial(centre, unit, colors, nightness, cloudiness, phase)
        if (cloudiness > 0.02f) drawCloud(centre, unit, colors, cloudiness)
        if (wetness > 0.01f) drawRain(centre, unit, wetness, confidence, fall, drops)
    }
}

/** Sole o luna, sfumati l'uno nell'altro secondo l'ora. */
private fun DrawScope.drawCelestial(
    centre: Offset,
    unit: Float,
    colors: MeteoColors,
    nightness: Float,
    cloudiness: Float,
    phase: Float,
) {
    // Dietro le nuvole, e spostato in alto a destra come nel riferimento.
    val at = centre + Offset(unit * 0.20f, -unit * 0.20f)
    val radius = unit * 0.15f
    // Piu' e' coperto, meno si vede: non sparisce, si vela.
    val visibility = (1f - cloudiness * 0.55f).coerceIn(0f, 1f)
    if (visibility <= 0.02f) return

    val body = lerp(colors.numberFace, colors.numberSideNear, nightness * 0.35f)
    val shade = lerp(colors.numberSideNear, colors.numberSideFar, 0.35f + nightness * 0.2f)

    if (nightness < 0.5f) {
        sphere(at, radius, body, shade, visibility * (1f - nightness * 2f).coerceIn(0f, 1f))
    } else {
        moon(at, radius, phase, body, shade, visibility * ((nightness - 0.5f) * 2f).coerceIn(0f, 1f))
    }
}

/**
 * Una sfera illuminata da una direzionale e' un gradiente radiale con il
 * centro spostato verso la luce. Stessa direzione della cifra: alto a sinistra.
 */
private fun DrawScope.sphere(
    centre: Offset,
    radius: Float,
    light: Color,
    dark: Color,
    alpha: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(light, dark),
            center = centre + Offset(-radius * 0.42f, -radius * 0.42f),
            radius = radius * 1.7f,
        ),
        radius = radius,
        center = centre,
        alpha = alpha,
    )
}

/** La luna con la sua mediana: arco esterno piu' semiellisse del terminatore. */
private fun DrawScope.moon(
    centre: Offset,
    radius: Float,
    phase: Float,
    light: Color,
    dark: Color,
    alpha: Float,
) {
    val waxing = MoonPhase.waxing(phase)
    val terminator = MoonPhase.terminator(phase)
    val gibbous = MoonPhase.illumination(phase) > 0.5f

    val disc = Rect(centre.x - radius, centre.y - radius, centre.x + radius, centre.y + radius)
    val inner = Rect(
        centre.x - radius * terminator, centre.y - radius,
        centre.x + radius * terminator, centre.y + radius,
    )

    val lit = Path().apply {
        // Semicerchio dal lato illuminato.
        arcTo(disc, if (waxing) -90f else 90f, 180f, true)
        // Mediana: rientra o sporge secondo che la luna sia calante o gibbosa.
        arcTo(inner, if (waxing) 90f else -90f, if (gibbous) 180f else -180f, false)
        close()
    }

    drawPath(
        path = lit,
        brush = Brush.radialGradient(
            colors = listOf(light, dark),
            center = centre + Offset(-radius * 0.35f, -radius * 0.35f),
            radius = radius * 1.7f,
        ),
        alpha = alpha,
    )
}

/** Grappolo di sfere: piu' e' coperto, piu' e' grande e piu' scende di tono. */
private fun DrawScope.drawCloud(
    centre: Offset,
    unit: Float,
    colors: MeteoColors,
    cloudiness: Float,
) {
    val scale = 0.55f + cloudiness * 0.45f
    val light = lerp(colors.numberSideNear, colors.numberSideFar, cloudiness * 0.55f)
    val dark = lerp(colors.numberSideFar, Color.Black, cloudiness * 0.30f)

    val lumps = listOf(
        Offset(-0.26f, 0.02f) to 0.19f,
        Offset(0.00f, -0.08f) to 0.25f,
        Offset(0.26f, 0.03f) to 0.20f,
        Offset(-0.10f, 0.10f) to 0.18f,
        Offset(0.14f, 0.11f) to 0.17f,
    )
    lumps.forEach { (relative, size) ->
        sphere(
            centre = centre + Offset(relative.x * unit * scale, relative.y * unit * scale),
            radius = size * unit * scale,
            light = light,
            dark = dark,
            alpha = cloudiness.coerceIn(0f, 1f),
        )
    }
}

/**
 * Le gocce seguono i millimetri, non la probabilita': la probabilita' dice
 * quanto e' probabile, non quanto forte, e a colpo d'occhio si legge
 * l'intensita'. La probabilita' governa invece quanto sono marcate.
 */
private fun DrawScope.drawRain(
    centre: Offset,
    unit: Float,
    wetness: Float,
    confidence: Float,
    progress: Float,
    drops: List<Pair<Float, Float>>,
) {
    val count = (drops.size * wetness).toInt().coerceAtLeast(1)
    val width = unit * 0.014f
    val top = centre.y + unit * 0.20f
    val span = unit * 0.42f

    for (i in 0 until count) {
        val (x, phase) = drops[i]
        val travel = (phase + progress * (0.75f + x * 0.5f)) % 1f
        val length = unit * (0.05f + 0.05f * abs(x - 0.5f) * 2f)
        val y = top + travel * span
        drawRoundRect(
            color = RainBlue.copy(alpha = (0.35f + 0.65f * confidence) * (1f - travel * 0.5f)),
            topLeft = Offset(centre.x + (x - 0.5f) * unit * 0.7f, y),
            size = Size(width, length),
            cornerRadius = CornerRadius(width / 2f, width / 2f),
        )
    }
}

private val RainBlue = Color(0xFF2C7BF2)
