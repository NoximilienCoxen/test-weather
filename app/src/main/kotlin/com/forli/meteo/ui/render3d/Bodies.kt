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
import androidx.compose.ui.graphics.lerp
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

/**
 * Una sfera opaca: un gradiente radiale col centro spostato verso la luce.
 *
 * @param wide e [tall] schiacciano il disco attorno al proprio centro, sulla
 *   tela. Servono a poche cose molto piatte - la tesa di un cappello - e non
 *   pretendono di essere un ellissoide: schiacciano l'immagine, non il corpo.
 *   A uno e uno non costano niente e non cambiano nulla.
 */
fun DrawScope.sphere(
    camera: Camera,
    x: Float,
    y: Float,
    z: Float,
    radius: Float,
    light: Color,
    dark: Color,
    alpha: Float = 1f,
    wide: Float = 1f,
    tall: Float = 1f,
) {
    if (alpha <= 0.003f) return
    camera.place(x, y, z)
    val r = radius * camera.scale
    if (r <= 0.5f) return
    val centre = Offset(camera.sx, camera.sy)

    fun disc() = drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(light, dark),
            center = centre + LightOnScreen * (r * 0.44f),
            radius = r * 1.75f,
        ),
        radius = r,
        center = centre,
        alpha = alpha,
    )

    if (wide == 1f && tall == 1f) {
        disc()
    } else {
        withTransform({ scale(wide, tall, centre) }) { disc() }
    }
}

/**
 * La luna: il disco intero, con dentro la parte illuminata e il taglio della
 * fase.
 *
 * **Il disco spento c'e', e prima non c'era.** Si disegnava solo la falce, col
 * ragionamento che una luna si riconosce proprio perche' il resto non c'e'. In
 * cielo e' vero. Su due centimetri di schermo, a chi non passa le sere a
 * guardare in su, no: una falce sola non si legge come luna, si legge come una
 * palla deformata - ed e' esattamente il commento arrivato da chi la usa.
 *
 * La fase si capisce quando si vede **il cerchio**, e dentro il cerchio il
 * taglio. E' cosi' che la disegnano i calendari e i lunari, ed e' la ragione
 * per cui funzionano: senza il bordo spento non c'e' un tondo da cui la falce
 * sia stata tolta, c'e' solo una scheggia. Qui la parte in ombra sta a un
 * quarto scarso di opacita': abbastanza da chiudere il cerchio, troppo poco da
 * competere con la parte accesa.
 *
 * Per la stessa ragione il gradiente della parte accesa non arriva piu' fino al
 * grigio dell'ombra: si ferma a mezza strada. Se le due parti finissero dello
 * stesso colore lungo la mediana, la mediana sparirebbe - e la mediana e'
 * l'informazione.
 *
 * **La sua luce non e' quella della scultura.** Sole, nuvole e cifra li
 * illumina la stessa lampada da sinistra in alto, ed e' giusto: sono oggetti
 * nella stessa stanza. La Luna no - la Luna la illumina il Sole, e da che parte
 * stia lo dice la fase, non la stanza. Prendendo la lampada della scena il
 * lembo acceso della falce veniva il punto piu' scuro del disco e la mediana ci
 * si perdeva dentro: si vedeva una palla grigia storta, non un quarto di luna.
 * Col gradiente dal lembo verso la mediana il bordo torna il piu' chiaro, la
 * luce cala andando verso il taglio, e il taglio si legge.
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

    // Il tondo spento, sotto a tutto: e' lui a dire che quella e' una luna e non
    // una scheggia. Va disegnato prima, se no coprirebbe la falce.
    drawCircle(
        color = dark,
        radius = r,
        center = centre,
        alpha = alpha * UNLIT_DISC,
    )

    // Il lembo acceso: a destra se cresce, a sinistra se cala.
    val limb = if (waxing) 1f else -1f
    drawPath(
        path = lit,
        brush = Brush.radialGradient(
            // Non fino al grigio dell'ombra: a mezza strada. Arrivandoci, lungo
            // la mediana i due lati finirebbero uguali e il taglio sparirebbe.
            colors = listOf(light, lerp(light, dark, TERMINATOR_CONTRAST)),
            center = centre + Offset(limb * r * 0.62f, -r * 0.20f),
            radius = r * 1.55f,
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
        blot(camera, centre, radius, ux, uy, -kotlin.math.sqrt(squared), size, color)
    }
}

/**
 * Una macchia appoggiata sulla superficie di una sfera, nella direzione data.
 *
 * Vista di sbieco una macchia tonda e' un'ellisse schiacciata lungo la direzione
 * che va dal centro al bordo, e oltre il bordo non c'e': se ne va dietro, e
 * sparisce. Vale per i mari della luna come per i continenti del mappamondo, e
 * la differenza fra i due sta solo in **come si sceglie la direzione** - fissa
 * per i primi, girata dal proprio asse per i secondi.
 */
private fun DrawScope.blot(
    camera: Camera,
    centre: Offset,
    radius: Float,
    ux: Float,
    uy: Float,
    uz: Float,
    size: Float,
    color: Color,
) {
    camera.normal(ux, uy, uz)
    if (camera.nvz > -0.12f) return

    val at = centre + Offset(camera.nvx * radius, camera.nvy * radius)
    val flatten = abs(camera.nvz)
    val angle = atan2(camera.nvy, camera.nvx) * 180f / PI.toFloat()

    withTransform({
        rotate(angle, at)
        scale(flatten, 1f, at)
    }) {
        drawCircle(
            color = color,
            radius = size * radius,
            center = at,
            // La dissolvenza serve solo a non far comparire una macchia di
            // colpo sul bordo, e deve finire li'. Legata direttamente
            // all'inclinazione sbiadiva tutto quello che non stava esattamente
            // al centro, e una sfera con due smagliature al centro non si legge
            // come un corpo con dei segni sopra: si legge come una sfera
            // sporca.
            alpha = ((flatten - 0.06f) / 0.22f).coerceIn(0f, 1f),
        )
    }
}

/**
 * Un mappamondo che gira sul proprio asse.
 *
 * E' la luna con due differenze: nessuna fase che la sezioni, e le macchie
 * girano per conto loro invece di stare ferme rispetto al corpo. Tutto il resto
 * - la sfera, la luce, l'ellisse che si schiaccia verso il bordo, il continente
 * che se ne va dietro - e' il codice che nell'app funziona da sempre.
 *
 * I continenti non sono una mappa e non provano a esserlo: sono l'appiglio che
 * permette di **vedere** che sta girando. Una sfera liscia che ruota e' una
 * sfera ferma.
 *
 * @param lands terne di longitudine e latitudine in gradi, piu' il raggio della
 *   macchia in frazione del raggio della sfera.
 * @param spinDeg quanto e' girato adesso, in gradi.
 */
fun DrawScope.globe(
    camera: Camera,
    x: Float,
    y: Float,
    z: Float,
    radius: Float,
    spinDeg: Float,
    light: Color,
    dark: Color,
    land: Color,
    lands: List<Triple<Float, Float, Float>>,
    alpha: Float = 1f,
) {
    if (alpha <= 0.003f) return
    sphere(camera, x, y, z, radius, light, dark, alpha)

    camera.place(x, y, z)
    val r = radius * camera.scale
    if (r <= 1f) return
    val centre = Offset(camera.sx, camera.sy)

    lands.forEach { (lonDeg, latDeg, size) ->
        val lon = (lonDeg + spinDeg) * DEG
        val lat = latDeg * DEG
        val cosLat = cos(lat)
        blot(
            camera = camera,
            centre = centre,
            radius = r,
            ux = cosLat * sin(lon),
            uy = -sin(lat),
            // Negativo alla longitudine zero: e' li' che il continente guarda
            // l'osservatore, e da li' comincia a scivolare via girando.
            uz = -cosLat * cos(lon),
            size = size,
            color = land.copy(alpha = land.alpha * alpha),
        )
    }
}

/**
 * Quanto si vede la parte in ombra della luna.
 *
 * Il compito e' chiudere il cerchio, non farsi guardare: alzandolo, la fase
 * smette di leggersi perche' acceso e spento si somigliano; togliendolo del
 * tutto si torna alla scheggia.
 */
private const val UNLIT_DISC = 0.24f

/**
 * Dove si ferma il gradiente della parte accesa, andando verso la mediana.
 *
 * A uno arriverebbe fino al grigio dell'ombra, i due lati finirebbero uguali
 * lungo il taglio e il taglio - che e' l'informazione - sparirebbe.
 */
private const val TERMINATOR_CONTRAST = 0.45f

private const val DEG = (PI / 180.0).toFloat()

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
    /** Di quanto e' girata la corona attorno al proprio centro, in gradi. */
    turnDeg: Float = 0f,
    /** Da 0 a 1: quanto i raggi sono allungati rispetto al minimo. */
    reach: Float = 1f,
) {
    if (alpha <= 0.003f) return
    val turn = turnDeg * DEG
    val tip = 1.52f + 0.20f * reach
    for (i in 0 until count) {
        val angle = turn + i * (PI.toFloat() * 2f / count)
        val dx = cos(angle)
        val dy = sin(angle)

        camera.place(x + dx * radius * 1.30f, y + dy * radius * 1.30f, z)
        if ((camera.vz > 0f) != far) continue
        val from = Offset(camera.sx, camera.sy)
        val nearScale = camera.scale
        camera.place(x + dx * radius * tip, y + dy * radius * tip, z)
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
