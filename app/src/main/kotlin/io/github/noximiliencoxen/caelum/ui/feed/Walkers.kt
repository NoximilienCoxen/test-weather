package io.github.noximiliencoxen.caelum.ui.feed

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * La gente che passa sotto la finestra, tagliata al busto dal davanzale.
 *
 * **Non e' il personaggio che questo progetto ha gia' provato e bocciato.**
 * Quello era un esploratore *articolato*, costruito da una gerarchia di sfere -
 * braccia a collana, il cappello che spariva dietro la testa, la mano che
 * salutava invece di riparare lo sguardo - e il verdetto fu *"se il risultato e'
 * questo, lasciamo perdere"*. Qui non c'e' rig, non c'e' articolazione, non c'e'
 * faccia: sono **contorni chiusi visti di profilo**, e il taglio al busto toglie
 * proprio le gambe, cioe' la parte che rende difficile far camminare qualcuno.
 * Il rischio che resta e' un altro, ed e' estetico: quattro sagome che si
 * ripetono in una via si notano. Si mitiga specchiando, cambiando misura e
 * corsia, e variando l'ombrello - ma si giudica solo guardandolo.
 *
 * **Come sono autorate**: tabelle di coordinate normalizzate in un riquadro che
 * va da -0,35 a 0,35 in larghezza e da 0 (la linea del taglio) a 1 (la cima
 * della testa). E' lo stile con cui il progetto tiene gia' la sua unica
 * geometria disegnata a mano, i profili delle coste del globo: numeri in fila,
 * non `Path` scritti riga per riga.
 *
 * **Cosa dicono, oltre a essere belle.** Non sono un salvaschermo: **quanti
 * hanno l'ombrello aperto e' la probabilita' di pioggia**, e quanto vanno curvi
 * e' l'intensita'. Con il sereno camminano dritti e l'ombrello resta chiuso, che
 * e' anche il motivo per cui la strada non e' vuota nelle giornate belle.
 */

/**
 * Una sagoma: profilo rivolto a destra, dal taglio del busto alla cima del capo.
 *
 * Sedici punti bastano perche' a questa misura - novanta punti scarsi di altezza
 * - il naso e' due pixel. Quello che si legge e' la nuca, la spalla e il petto;
 * il resto e' fiducia.
 */
private val BUSTO_CAPPUCCIO = floatArrayOf(
    -0.30f, 0.00f, -0.29f, 0.34f, -0.27f, 0.55f, -0.24f, 0.66f,
    -0.20f, 0.78f, -0.14f, 0.90f, -0.04f, 0.98f, 0.07f, 0.97f,
    0.15f, 0.88f, 0.17f, 0.76f, 0.12f, 0.70f, 0.09f, 0.64f,
    0.19f, 0.58f, 0.25f, 0.36f, 0.27f, 0.14f, 0.28f, 0.00f,
)

/** A capo scoperto: la nuca rientra, il naso sporge, il collo si vede. */
private val BUSTO_SCOPERTO = floatArrayOf(
    -0.29f, 0.00f, -0.28f, 0.32f, -0.25f, 0.54f, -0.20f, 0.63f,
    -0.12f, 0.69f, -0.13f, 0.80f, -0.11f, 0.91f, -0.03f, 0.98f,
    0.07f, 0.96f, 0.12f, 0.88f, 0.14f, 0.82f, 0.19f, 0.79f,
    0.13f, 0.76f, 0.15f, 0.71f, 0.09f, 0.67f, 0.07f, 0.62f,
    0.17f, 0.57f, 0.24f, 0.34f, 0.26f, 0.12f, 0.27f, 0.00f,
)

/** Con la coda: la nuca porta un ciuffo che sporge indietro. */
private val BUSTO_CODA = floatArrayOf(
    -0.30f, 0.00f, -0.28f, 0.33f, -0.26f, 0.55f, -0.21f, 0.64f,
    -0.24f, 0.72f, -0.29f, 0.78f, -0.22f, 0.82f, -0.14f, 0.86f,
    -0.10f, 0.94f, -0.01f, 0.99f, 0.08f, 0.95f, 0.13f, 0.86f,
    0.11f, 0.78f, 0.07f, 0.71f, 0.06f, 0.63f, 0.16f, 0.57f,
    0.23f, 0.35f, 0.25f, 0.13f, 0.26f, 0.00f,
)

private val SAGOME = listOf(BUSTO_CAPPUCCIO, BUSTO_SCOPERTO, BUSTO_CODA)

/**
 * Chi passa: quale sagoma, in che corsia, a che velocita', da che parte.
 *
 * Sono sei, seminati a mano e non a caso: la corsia decide la misura e la
 * velocita' insieme - piu' lontano vuol dire piu' piccolo e piu' lento - e le
 * fasi sono sparse perche' non partano in fila.
 */
private class Walker(
    val shape: Int,
    /** 0 lontano, 1 vicino. Decide misura, velocita' e quanto e' sbiadito. */
    val lane: Float,
    val phase: Float,
    /** Vero se cammina verso destra. Al contrario la sagoma si specchia. */
    val rightward: Boolean,
    /** Se ha l'ombrello, quando serve. Chi non ce l'ha resta senza anche sotto l'acqua. */
    val hasUmbrella: Boolean,
)

private val WALKERS = listOf(
    Walker(0, 1.00f, 0.05f, rightward = true, hasUmbrella = true),
    Walker(1, 0.62f, 0.38f, rightward = false, hasUmbrella = true),
    Walker(2, 0.85f, 0.71f, rightward = true, hasUmbrella = true),
    Walker(1, 0.40f, 0.22f, rightward = true, hasUmbrella = false),
    Walker(0, 0.72f, 0.90f, rightward = false, hasUmbrella = true),
    Walker(2, 0.48f, 0.55f, rightward = false, hasUmbrella = true),
)

/**
 * La via sotto la finestra.
 *
 * [chance] e' la probabilita' di pioggia da 0 a 1, ed e' cio' che decide **quanti
 * ombrelli sono aperti**: e' l'unico modo che questa scena ha di dire una cosa
 * che i numeri in fondo dicono in cifre, e di dirla senza scriverla.
 */
internal fun DrawScope.drawWalkers(
    bounds: Size,
    origin: Offset,
    progress: Float,
    /** Da 0 a 1: quanto forte piove. Piega le schiene e inclina gli ombrelli. */
    wetness: Float,
    /** Da 0 a 1: quanti ombrelli sono aperti. */
    chance: Float,
    ink: Color,
    umbrella: Color,
) {
    val street = origin.y + bounds.height
    val tall = bounds.height * WALKER_TALL

    for ((index, w) in WALKERS.withIndex()) {
        // Piu' vicino, piu' veloce: e' la parallasse fatta con la velocita'
        // invece che con la geometria, ed e' quella che a questa scala si legge.
        val speed = WALK_SLOW + w.lane * (WALK_FAST - WALK_SLOW)
        val travel = (w.phase + progress * speed) % 1f
        val across = if (w.rightward) travel else 1f - travel

        val scale = tall * (0.62f + w.lane * 0.38f)
        val x = origin.x - bounds.width * 0.15f + across * bounds.width * 1.30f
        // Il saliscendi del passo: senza gambe e' l'unica cosa che dice che sta
        // camminando invece di scivolare.
        val bob = sin(travel * PI.toFloat() * 2f * STEPS_PER_CROSSING) * scale * BOB
        val feet = street + bob

        // Piu' lontano, piu' sbiadito: e' l'aria che sta in mezzo.
        val fade = 0.30f + w.lane * 0.55f
        // Sotto l'acqua si cammina curvi, e piu' forte piove piu' ci si piega.
        val lean = (if (w.rightward) 1f else -1f) * wetness * LEAN

        val open = w.hasUmbrella && chance > (index + 0.5f) / WALKERS.size
        drawWalker(
            shape = SAGOME[w.shape],
            centre = Offset(x, feet),
            scale = scale,
            mirrored = !w.rightward,
            lean = lean,
            ink = ink.copy(alpha = fade),
        )
        if (open) {
            drawUmbrella(
                centre = Offset(x, feet),
                scale = scale,
                mirrored = !w.rightward,
                // L'ombrello oscilla **in ritardo** rispetto al corpo: seguendolo
                // esatto sembrerebbe incollato alla testa.
                sway = sin(travel * PI.toFloat() * 2f * STEPS_PER_CROSSING - 0.9f) * SWAY,
                lean = lean,
                colour = umbrella.copy(alpha = fade),
            )
        }
    }
}

/** Una sagoma, scalata e messa al suo posto. */
private fun DrawScope.drawWalker(
    shape: FloatArray,
    centre: Offset,
    scale: Float,
    mirrored: Boolean,
    lean: Float,
    ink: Color,
) {
    val flip = if (mirrored) -1f else 1f
    val path = Path()
    for (i in shape.indices step 2) {
        val sx = shape[i] * flip
        val sy = shape[i + 1]
        // L'inclinazione cresce con l'altezza: i piedi restano dove sono e le
        // spalle vanno avanti, che e' come ci si piega davvero.
        val x = centre.x + (sx + lean * sy) * scale
        val y = centre.y - sy * scale
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = ink)
}

/**
 * L'ombrello: una calotta e un'asta.
 *
 * Non un cerchio tagliato a meta': una calotta vera ha le punte che scendono
 * oltre il diametro, ed e' quello che la fa leggere come un ombrello invece che
 * come un fungo.
 */
private fun DrawScope.drawUmbrella(
    centre: Offset,
    scale: Float,
    mirrored: Boolean,
    sway: Float,
    lean: Float,
    colour: Color,
) {
    val flip = if (mirrored) -1f else 1f
    val head = Offset(
        centre.x + (lean * 1f + sway * 0.4f) * scale,
        centre.y - scale * UMBRELLA_HIGH,
    )
    val half = scale * UMBRELLA_WIDE
    val drop = scale * UMBRELLA_DROP

    val canopy = Path().apply {
        moveTo(head.x - half, head.y + drop)
        // Tre archi: le due falde e la cima. Con un arco solo la calotta e'
        // troppo tonda e diventa un cappello.
        quadraticTo(head.x - half * 0.55f, head.y - drop * 0.4f, head.x, head.y - drop * 0.9f)
        quadraticTo(head.x + half * 0.55f, head.y - drop * 0.4f, head.x + half, head.y + drop)
        quadraticTo(head.x + half * 0.5f, head.y + drop * 0.35f, head.x, head.y + drop * 0.42f)
        quadraticTo(head.x - half * 0.5f, head.y + drop * 0.35f, head.x - half, head.y + drop)
        close()
    }
    drawPath(canopy, color = colour)

    // L'asta, verso la mano: corta, perche' la mano sta appena sotto il taglio.
    drawLine(
        color = colour,
        start = Offset(head.x, head.y - drop * 0.9f),
        end = Offset(head.x + flip * scale * 0.10f, centre.y - scale * 0.34f),
        strokeWidth = (scale * 0.035f).coerceAtLeast(1f),
        cap = StrokeCap.Round,
    )
}

/**
 * La linea della via, e le pozze che ci stanno sopra.
 *
 * Serve perche' senza le sagome galleggiano: una figura tagliata a meta' in
 * mezzo al cielo si legge come un errore di ritaglio, non come qualcuno visto
 * da una finestra.
 */
internal fun DrawScope.drawStreet(
    bounds: Size,
    origin: Offset,
    wetness: Float,
    ink: Color,
    sheen: Color,
) {
    val street = origin.y + bounds.height
    drawRect(
        color = ink.copy(alpha = 0.55f),
        topLeft = Offset(origin.x, street - bounds.height * STREET_BAND),
        size = Size(bounds.width, bounds.height * STREET_BAND),
    )
    if (wetness <= 0f) return
    // L'asfalto bagnato riflette, e a questa misura riflettere vuol dire due
    // strisce chiare che corrono per il lungo: una pozza disegnata come una
    // macchia diventa una toppa.
    for (i in 0 until 3) {
        val y = street - bounds.height * STREET_BAND * (0.25f + i * 0.28f)
        val w = bounds.width * (0.5f - abs(i - 1) * 0.14f)
        drawLine(
            color = sheen.copy(alpha = 0.10f + 0.10f * wetness),
            start = Offset(origin.x + bounds.width * 0.5f - w * 0.5f, y),
            end = Offset(origin.x + bounds.width * 0.5f + w * 0.5f, y),
            strokeWidth = bounds.height * 0.006f,
            cap = StrokeCap.Round,
        )
    }
}

/** Quanto e' alta una sagoma della corsia piu' vicina, sull'apertura. */
private const val WALKER_TALL = 0.30f

/** Quanti passi in una traversata: e' quello che regola il saliscendi. */
private const val STEPS_PER_CROSSING = 9f

private const val BOB = 0.022f
private const val LEAN = 0.13f
private const val SWAY = 0.06f

/** Quanto ci mette a traversare, dal fondo alla corsia vicina. */
private const val WALK_SLOW = 0.055f
private const val WALK_FAST = 0.115f

private const val UMBRELLA_HIGH = 1.16f
private const val UMBRELLA_WIDE = 0.46f
private const val UMBRELLA_DROP = 0.13f

/** Quanta parte in basso dell'apertura e' via, e non cielo. */
private const val STREET_BAND = 0.14f
