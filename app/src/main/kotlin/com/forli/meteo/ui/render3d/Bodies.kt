package com.forli.meteo.ui.render3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * I corpi tondi della scultura - sole, luna, masse della nuvola - visti dalla
 * stessa camera della cifra e illuminati dalla stessa luce.
 *
 * Una sfera resta identica a se stessa da qualunque angolo la si guardi: se
 * ruotando cambiasse solo lei, non si vedrebbe muovere nulla. Quello che si
 * muove sono la sua posizione nello spazio, i raggi che le stanno attorno e i
 * segni sulla superficie. Sono questi a raccontare la rotazione, non il disco.
 */

/** Dove sta la luce sullo schermo, per spostare il centro del gradiente. */
private val LightOnScreen: Offset = run {
    val l = Light.Standard
    val len = hypot(l.x, l.y).takeIf { it > 1e-4f } ?: 1f
    Offset(l.x / len, l.y / len)
}

/** Una sfera opaca: un gradiente radiale col centro spostato verso la luce. */
fun DrawScope.sphere(
    camera: Camera,
    x: Float,
    y: Float,
    z: Float,
    radius: Float,
    light: Color,
    dark: Color,
    alpha: Float = 1f,
) {
    if (alpha <= 0.003f) return
    camera.place(x, y, z)
    val r = radius * camera.scale
    if (r <= 0.5f) return
    val centre = Offset(camera.sx, camera.sy)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(light, dark),
            center = centre + LightOnScreen * (r * 0.44f),
            radius = r * 1.75f,
        ),
        radius = r,
        center = centre,
        alpha = alpha,
    )
}

/**
 * La luna: solo la parte illuminata, sezionata dalla mediana della fase.
 *
 * La parte in ombra non si disegna. Riempirla di grigio darebbe un disco pieno
 * con una riga in mezzo, che non e' una luna: la falce si riconosce proprio
 * perche' il resto non c'e'.
 */
fun DrawScope.moon(
    camera: Camera,
    x: Float,
    y: Float,
    z: Float,
    radius: Float,
    /** 0 novilunio, 0.5 plenilunio. */
    phase: Float,
    light: Color,
    dark: Color,
    alpha: Float,
    marks: List<Triple<Float, Float, Float>>,
) {
    if (alpha <= 0.003f) return
    camera.place(x, y, z)
    val r = radius * camera.scale
    if (r <= 1f) return
    val centre = Offset(camera.sx, camera.sy)

    val waxing = phase < 0.5f
    val terminator = abs(cos(2.0 * PI * phase).toFloat())
    val gibbous = ((1f - cos(2.0 * PI * phase).toFloat()) / 2f) > 0.5f

    val disc = Rect(centre.x - r, centre.y - r, centre.x + r, centre.y + r)
    val inner = Rect(centre.x - r * terminator, centre.y - r, centre.x + r * terminator, centre.y + r)

    val lit = Path().apply {
        // Semicerchio dal lato illuminato.
        arcTo(disc, if (waxing) -90f else 90f, 180f, true)
        // Mediana: rientra o sporge secondo che la luna sia falce o gibbosa.
        arcTo(inner, if (waxing) 90f else -90f, if (gibbous) 180f else -180f, false)
        close()
    }

    drawPath(
        path = lit,
        brush = Brush.radialGradient(
            colors = listOf(light, dark),
            center = centre + LightOnScreen * (r * 0.40f),
            radius = r * 1.75f,
        ),
        alpha = alpha,
    )

    // I mari lunari sono l'unico appiglio per vedere che la luna sta girando.
    // Ritagliati sulla parte illuminata: sull'ombra non ci sarebbe niente da
    // vedere e sborderebbero oltre la falce.
    clipPath(lit) {
        surfaceMarks(camera, centre, r, marks, dark.copy(alpha = 0.55f * alpha))
    }
}

/**
 * Macchie sulla superficie di una sfera.
 *
 * Ogni macchia sta in una direzione fissa rispetto al corpo: ruotando il corpo
 * la direzione ruota con lui, scorre verso il bordo, si schiaccia e sparisce
 * dietro. E' il modo in cui si legge la rotazione di qualcosa di tondo.
 *
 * @param marks direzioni sulla sfera unitaria, piu' il raggio della macchia in
 *   frazione del raggio della sfera.
 */
fun DrawScope.surfaceMarks(
    camera: Camera,
    centre: Offset,
    radius: Float,
    marks: List<Triple<Float, Float, Float>>,
    color: Color,
) {
    marks.forEach { (ux, uy, size) ->
        // La terza componente si ricava dalle prime due: le macchie stanno
        // sulla sfera, non attorno.
        val squared = 1f - ux * ux - uy * uy
        if (squared <= 0f) return@forEach
        val uz = -kotlin.math.sqrt(squared)

        camera.normal(ux, uy, uz)
        if (camera.nvz > -0.12f) return@forEach

        val at = centre + Offset(camera.nvx * radius, camera.nvy * radius)
        val flatten = abs(camera.nvz)
        val markRadius = size * radius
        val angle = atan2(camera.nvy, camera.nvx) * 180f / PI.toFloat()

        // Vista di sbieco una macchia tonda e' un'ellisse schiacciata lungo la
        // direzione che va dal centro al bordo.
        withTransform({
            rotate(angle, at)
            scale(flatten, 1f, at)
        }) {
            drawCircle(
                color = color,
                radius = markRadius,
                center = at,
                alpha = (flatten - 0.12f).coerceIn(0f, 1f),
            )
        }
    }
}

/**
 * La corona di raggi del sole, in un piano solidale col corpo.
 *
 * E' il pezzo che rende visibile la rotazione: ferma la corona e' un cerchio,
 * girata diventa un'ellisse sempre piu' stretta, e i raggi laterali si
 * accorciano fino a sparire. Senza, il sole sarebbe una palla immobile.
 *
 * Va disegnata in due passate, [far] prima e dopo la sfera: girata di parecchio
 * la corona rientra nella sagoma del disco, e i raggi che stanno dietro devono
 * sparirci sotto invece di attraversarlo.
 */
fun DrawScope.sunRays(
    camera: Camera,
    x: Float,
    y: Float,
    z: Float,
    radius: Float,
    color: Color,
    alpha: Float,
    far: Boolean,
    count: Int = 12,
) {
    if (alpha <= 0.003f) return
    for (i in 0 until count) {
        val angle = i * (PI.toFloat() * 2f / count)
        val dx = cos(angle)
        val dy = sin(angle)

        camera.place(x + dx * radius * 1.30f, y + dy * radius * 1.30f, z)
        if ((camera.vz > 0f) != far) continue
        val from = Offset(camera.sx, camera.sy)
        val nearScale = camera.scale
        camera.place(x + dx * radius * 1.66f, y + dy * radius * 1.66f, z)
        val to = Offset(camera.sx, camera.sy)

        drawLine(
            color = color,
            start = from,
            end = to,
            strokeWidth = (radius * 0.085f * nearScale).coerceAtLeast(1f),
            cap = StrokeCap.Round,
            alpha = alpha,
        )
    }
}
