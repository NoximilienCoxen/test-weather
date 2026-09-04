package com.forli.meteo.widget.paint

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.forli.meteo.data.Wmo
import com.forli.meteo.ui.render3d.Camera
import com.forli.meteo.ui.render3d.MOON_SEAS
import com.forli.meteo.ui.render3d.glow
import com.forli.meteo.ui.render3d.moon
import com.forli.meteo.ui.render3d.sphere
import com.forli.meteo.ui.render3d.sunRays
import kotlin.math.cos
import kotlin.math.sin

/** Una massa della nuvola: posizione nello spazio e raggio, in frazioni di unita'. */
private class Lump(val x: Float, val y: Float, val z: Float, val radius: Float)

/**
 * Le stesse masse della scultura grande dell'app, ricopiate.
 *
 * Ricopiate e non condivise: stanno dentro un composable di millesettecento
 * righe che e' il cuore visivo dell'app, e sette righe di numeri non valgono il
 * rischio di rimetterci le mani.
 */
private val CLOUD_MASSES = listOf(
    Lump(-0.26f, 0.02f, 0.16f, 0.19f),
    Lump(0.00f, -0.09f, -0.06f, 0.25f),
    Lump(0.26f, 0.03f, 0.12f, 0.20f),
    Lump(-0.11f, 0.10f, -0.19f, 0.18f),
    Lump(0.15f, 0.11f, -0.14f, 0.17f),
)

/**
 * L'illustrazione del tempo, dentro il riquadro dato.
 *
 * Usa gli stessi corpi della schermata principale - sfere illuminate, non
 * sagome piatte - ma ferme: un widget non si anima, e cio' che nell'app e' un
 * fotogramma qui e' l'unica posa che si vedra'.
 */
internal fun DrawScope.weatherBody(
    box: Rect,
    family: Wmo.Family,
    isDay: Boolean,
    ink: WidgetInk,
) {
    val unit = minOf(box.width, box.height)
    val camera = Camera(
        yawDeg = 0f,
        pitchDeg = 0f,
        distance = unit * 2.1f,
        origin = box.center,
    )

    val piovoso = family == Wmo.Family.PIOGGIA ||
        family == Wmo.Family.NEVE ||
        family == Wmo.Family.TEMPORALE
    val coreCloud = if (piovoso) ink.rainCloudCore else ink.cloudCore
    val shadeCloud = if (piovoso) ink.rainCloudShade else ink.cloudShade

    when (family) {
        Wmo.Family.ASCIUTTO -> if (isDay) sun(camera, unit, ink) else moonBody(camera, unit, ink)

        Wmo.Family.NUVOLOSO -> {
            // L'astro spunta da dietro la nuvola, spostato in alto a sinistra:
            // una nuvola sola non distingue il giorno dalla notte.
            if (isDay) {
                sun(camera, unit * 0.62f, ink, x = -0.30f, y = -0.30f, z = -0.35f)
            } else {
                moonBody(camera, unit * 0.58f, ink, x = -0.30f, y = -0.30f, z = -0.35f)
            }
            cloud(camera, unit, coreCloud, shadeCloud)
        }

        Wmo.Family.NEBBIA -> {
            cloud(camera, unit, coreCloud, shadeCloud, alpha = 0.75f)
            fogBars(box, coreCloud)
        }

        Wmo.Family.PIOGGIA -> {
            cloud(camera, unit, coreCloud, shadeCloud, lift = -0.12f)
            fall(box, unit, ink.rain) { at, size, colour -> raindrop(at, size, colour) }
        }

        Wmo.Family.NEVE -> {
            cloud(camera, unit, coreCloud, shadeCloud, lift = -0.12f)
            fall(box, unit, ink.snow) { at, size, colour -> flake(at, size, colour) }
        }

        Wmo.Family.TEMPORALE -> {
            cloud(camera, unit, coreCloud, shadeCloud, lift = -0.14f)
            bolt(box, unit, ink.bolt)
        }
    }
}

private fun DrawScope.sun(
    camera: Camera,
    unit: Float,
    ink: WidgetInk,
    x: Float = 0f,
    y: Float = 0f,
    z: Float = 0f,
) {
    val r = unit * 0.30f
    glow(camera, x * unit, y * unit, z * unit, r, ink.sunCore, 0.40f)
    sunRays(camera, x * unit, y * unit, z * unit, r, ink.sunCore, 0.70f, far = true, count = 8)
    sphere(camera, x * unit, y * unit, z * unit, r, ink.sunCore, ink.sunShade, 1f)
    sunRays(camera, x * unit, y * unit, z * unit, r, ink.sunCore, 0.70f, far = false, count = 8)
}

private fun DrawScope.moonBody(
    camera: Camera,
    unit: Float,
    ink: WidgetInk,
    x: Float = 0f,
    y: Float = 0f,
    z: Float = 0f,
    phase: Float = 0.5f,
) {
    val r = unit * 0.30f
    glow(camera, x * unit, y * unit, z * unit, r, ink.moonCore, 0.35f, spread = 2.0f)
    moon(
        camera = camera,
        x = x * unit, y = y * unit, z = z * unit,
        radius = r,
        phase = phase,
        light = ink.moonCore,
        dark = ink.moonShade,
        alpha = 1f,
        marks = MOON_SEAS,
    )
}

/** La luna con la sua fase vera, grande quanto il riquadro: per il widget Luna. */
internal fun DrawScope.moonFace(box: Rect, phase: Float, ink: WidgetInk) {
    val unit = minOf(box.width, box.height)
    val camera = Camera(yawDeg = 0f, pitchDeg = 0f, distance = unit * 2.1f, origin = box.center)
    val r = unit * 0.44f
    glow(camera, 0f, 0f, 0f, r, ink.moonCore, 0.30f, spread = 2.0f)
    moon(
        camera = camera,
        x = 0f, y = 0f, z = 0f,
        radius = r,
        phase = phase,
        light = ink.moonCore,
        dark = ink.moonShade,
        alpha = 1f,
        marks = MOON_SEAS,
    )
}

private fun DrawScope.cloud(
    camera: Camera,
    unit: Float,
    core: Color,
    shade: Color,
    alpha: Float = 1f,
    lift: Float = 0f,
) {
    CLOUD_MASSES.forEach { lump ->
        sphere(
            camera,
            lump.x * unit,
            (lump.y + lift) * unit,
            lump.z * unit,
            lump.radius * unit,
            core,
            shade,
            alpha,
        )
    }
}

/** Le tre strisce della nebbia, che sfumano scendendo. */
private fun DrawScope.fogBars(box: Rect, colour: Color) {
    val step = box.height * 0.13f
    val top = box.center.y + box.height * 0.12f
    repeat(3) { i ->
        val inset = box.width * (0.10f + 0.06f * i)
        drawLine(
            color = colour,
            start = Offset(box.left + inset, top + step * i),
            end = Offset(box.right - inset, top + step * i),
            strokeWidth = box.height * 0.045f,
            cap = StrokeCap.Round,
            alpha = 0.85f - 0.22f * i,
        )
    }
}

private inline fun DrawScope.fall(
    box: Rect,
    unit: Float,
    colour: Color,
    draw: (Offset, Float, Color) -> Unit,
) {
    val y = box.center.y + unit * 0.20f
    val size = unit * 0.075f
    listOf(-0.22f, 0f, 0.22f).forEachIndexed { i, dx ->
        draw(Offset(box.center.x + dx * unit, y + (i % 2) * unit * 0.07f), size, colour)
    }
}

private fun DrawScope.raindrop(at: Offset, size: Float, colour: Color) {
    drawLine(
        color = colour,
        start = Offset(at.x + size * 0.35f, at.y),
        end = Offset(at.x - size * 0.35f, at.y + size * 2.1f),
        strokeWidth = size * 0.62f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.flake(at: Offset, size: Float, colour: Color) {
    repeat(3) { i ->
        val a = Math.PI * i / 3.0
        val dx = (cos(a) * size).toFloat()
        val dy = (sin(a) * size).toFloat()
        drawLine(
            color = colour,
            start = Offset(at.x - dx, at.y - dy + size),
            end = Offset(at.x + dx, at.y + dy + size),
            strokeWidth = size * 0.34f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.bolt(box: Rect, unit: Float, colour: Color) {
    val cx = box.center.x
    val top = box.center.y + unit * 0.14f
    val w = unit * 0.13f
    val h = unit * 0.34f
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx + w * 0.35f, top)
        lineTo(cx - w, top + h * 0.56f)
        lineTo(cx - w * 0.05f, top + h * 0.56f)
        lineTo(cx - w * 0.45f, top + h)
        lineTo(cx + w, top + h * 0.40f)
        lineTo(cx + w * 0.05f, top + h * 0.40f)
        close()
    }
    drawPath(path, colour)
}

/** Il pallino della qualita' dell'aria: pieno, con un alone che lo stacca. */
internal fun DrawScope.airDot(centre: Offset, radius: Float, colour: Color) {
    drawCircle(colour, radius * 1.7f, centre, alpha = 0.22f)
    drawCircle(colour, radius, centre)
}

/** Un cerchio vuoto, per quando il dato non e' arrivato. */
internal fun DrawScope.airDotEmpty(centre: Offset, radius: Float, colour: Color) {
    drawCircle(colour, radius, centre, alpha = 0.45f, style = Stroke(width = radius * 0.34f))
}
