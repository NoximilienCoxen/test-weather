package io.github.noximiliencoxen.caelum.widget.paint

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.sala.GlifoMeteo
import io.github.noximiliencoxen.caelum.ui.sala.disegnaGlifo
import io.github.noximiliencoxen.caelum.widget.paint.render3d.Camera
import io.github.noximiliencoxen.caelum.widget.paint.render3d.MOON_SEAS
import io.github.noximiliencoxen.caelum.widget.paint.render3d.glow
import io.github.noximiliencoxen.caelum.widget.paint.render3d.moon

/**
 * L'illustrazione del tempo, dentro il riquadro dato.
 *
 * **La stessa figuretta dell'app**, e non piu' una scultura a sfere: il
 * widget e l'app devono dire il tempo con lo stesso segno, e le figurette a
 * colori (`disegnaGlifo`) si leggono anche da lontano, sulla schermata home,
 * dove una sfera grigia illuminata di lato era solo una macchia.
 */
internal fun DrawScope.weatherBody(box: Rect, family: Wmo.Family, isDay: Boolean) {
    glifoNelRiquadro(box, family.glifo(), notte = !isDay)
}

/** La figuretta della famiglia: "nuvoloso" senza altri dati e' sole e nuvola. */
internal fun Wmo.Family.glifo(): GlifoMeteo = when (this) {
    Wmo.Family.ASCIUTTO -> GlifoMeteo.SOLE
    Wmo.Family.NUVOLOSO -> GlifoMeteo.POCO
    Wmo.Family.NEBBIA -> GlifoMeteo.NEBBIA
    Wmo.Family.PIOGGIA -> GlifoMeteo.PIOGGIA
    Wmo.Family.NEVE -> GlifoMeteo.NEVE
    Wmo.Family.TEMPORALE -> GlifoMeteo.TEMPORALE
}

/** Disegna la figuretta stretta nel riquadro [box]: si misura sulla sua tela. */
internal fun DrawScope.glifoNelRiquadro(box: Rect, glifo: GlifoMeteo, notte: Boolean = false) {
    inset(box.left, box.top, size.width - box.right, size.height - box.bottom) {
        disegnaGlifo(glifo, notte)
    }
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

/** Il pallino della qualita' dell'aria: pieno, con un alone che lo stacca. */
internal fun DrawScope.airDot(centre: Offset, radius: Float, colour: Color) {
    drawCircle(colour, radius * 1.7f, centre, alpha = 0.22f)
    drawCircle(colour, radius, centre)
}

/** Un cerchio vuoto, per quando il dato non e' arrivato. */
internal fun DrawScope.airDotEmpty(centre: Offset, radius: Float, colour: Color) {
    drawCircle(colour, radius, centre, alpha = 0.45f, style = Stroke(width = radius * 0.34f))
}
