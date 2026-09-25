package io.github.noximiliencoxen.caelum.widget.paint.render3d
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import io.github.noximiliencoxen.caelum.data.MoonPhase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * La luna del widget Luna, e il bagliore che le sta dietro.
 *
 * **Qui c'erano anche il sole a sfera, la sua corona di raggi e le masse della
 * nuvola**, con la luce di scena che li illuminava: erano la scultura del
 * widget del tempo, sostituita dalle figurette a colori dell'app
 * (`disegnaGlifo`). La luna resta un corpo: e' la fase, non un'icona.
 */

/**
 * Un bagliore proprio: un alone che sfuma a trasparente, dietro al corpo.
 *
 * Non e' la sfumatura della sfera - quella racconta come la luce esterna
 * colpisce una superficie opaca. Questo e' l'opposto: il corpo che emette
 * luce sua, indipendente da dove sta la lampada della scena. Va disegnato
 * *prima* del disco, cosi' il disco gli sta sopra e l'alone resta un contorno
 * intorno, non una macchia che lo attraversa.
 */
fun DrawScope.glow(
    camera: Camera,
    x: Float,
    y: Float,
    z: Float,
    radius: Float,
    color: Color,
    alpha: Float,
    spread: Float = 2.4f,
) {
    if (alpha <= 0.003f) return
    camera.place(x, y, z)
    val r = radius * camera.scale
    if (r <= 0.5f) return
    val centre = Offset(camera.sx, camera.sy)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            center = centre,
            radius = r * spread,
        ),
        radius = r * spread,
        center = centre,
    )
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
/**
 * I mari lunari: due componenti sulla sfera unitaria e il raggio della macchia.
 *
 * Non sono una mappa fedele, sono l'appiglio che permette di vedere che la luna
 * sta girando invece di stare ferma a farsi guardare.
 *
 * **Sta qui e non presso chi disegna** perche' i chiamanti sono tre - la
 * scultura della schermata principale, il widget e la pagina del dettaglio - e
 * finche' erano due se ne sono tenuti una copia privata a testa. Due copie degli
 * stessi quattro numeri sono due copie destinate a divergere alla prima volta
 * che qualcuno ne sposta una.
 */
val MOON_SEAS: List<Triple<Float, Float, Float>> = listOf(
    Triple(-0.30f, -0.24f, 0.20f),
    Triple(0.16f, 0.05f, 0.26f),
    Triple(-0.08f, 0.42f, 0.15f),
    Triple(0.42f, -0.34f, 0.12f),
)

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

    // **Le tre righe che stavano qui erano `MoonPhase` ricopiata.** Carattere
    // per carattere: stessa mediana, stessa crescenza, stessa frazione
    // illuminata confrontata con mezzo. Due copie della stessa formula sono una
    // formula che un giorno diverge, e infatti `MoonPhase.terminator` e
    // `MoonPhase.waxing` risultavano "non chiamate da nessuno" a ogni giro di
    // pulizia - erano chiamate, solo che erano ricopiate qui.
    val waxing = MoonPhase.waxing(phase)
    val terminator = MoonPhase.terminator(phase)
    val gibbous = MoonPhase.illumination(phase) > 0.5f

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
 * Quanto si vede la parte in ombra della luna.
 *
 * Il compito e' chiudere il cerchio, non farsi guardare: alzandolo, la fase
 * smette di leggersi perche' acceso e spento si somigliano; togliendolo del
 * tutto si torna alla scheggia.
 *
 * **Qui accanto ce n'erano altri due, e se ne sono andati col mappamondo**:
 * `RIM_STEP`, il passo con cui si campionava l'arco del bordo, e `LIMB_SHADE`,
 * quanto scuriva il bordo della sfera. Li leggeva solo `globe`. I loro commenti
 * erano gia' scivolati via dalla costante che descrivevano - si leggevano tre
 * KDoc di fila e poi tre valori - che e' il segno che una cancellazione
 * precedente era passata di qui senza rileggere.
 */
private const val UNLIT_DISC = 0.24f

/**
 * Dove si ferma il gradiente della parte accesa, andando verso la mediana.
 *
 * A uno arriverebbe fino al grigio dell'ombra, i due lati finirebbero uguali
 * lungo il taglio e il taglio - che e' l'informazione - sparirebbe.
 */
private const val TERMINATOR_CONTRAST = 0.45f

