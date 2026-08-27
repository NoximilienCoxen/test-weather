package com.forli.meteo.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.forli.meteo.data.Wind
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Quello che abita il cielo dietro la scultura: stelle, meteore, uccelli e
 * banchi di nebbia.
 *
 * Non fa parte della scultura e non ruota con lei. E' la differenza fra un
 * oggetto e lo sfondo davanti a cui l'oggetto sta: girando la cifra il cielo
 * non deve girare, altrimenti si capisce che era una decalcomania anche lui.
 */

// ---------------------------------------------------------------------------
// Stelle
// ---------------------------------------------------------------------------

/** Una stella fissa: posizione in frazione del riquadro, e quanto e' luminosa. */
private class Star(val x: Float, val y: Float, val magnitude: Float)

private val STARS: List<Star> = List(66) { i ->
    val r = Random(i * 6151 + 29)
    Star(
        x = r.nextFloat(),
        // Concentrate in alto: sotto c'e' la scultura, e piu' giu' ancora c'e'
        // la cifra. Una stella dietro il numero non si vedrebbe comunque, e
        // sarebbe solo lavoro speso per coprirla subito dopo.
        y = r.nextFloat() * r.nextFloat(),
        magnitude = r.nextFloat(),
    )
}

/**
 * Il cielo stellato.
 *
 * Fisse, senza scintillio. Lo scintillio sarebbe la prima cosa da aggiungere e
 * la prima da togliere: costringerebbe l'app a chiedere fotogrammi per tutta la
 * notte, a schermo praticamente immobile, per un effetto che a questa scala si
 * confonde con il rumore del pannello.
 */
fun DrawScope.drawStars(presence: Float, clarity: Float) {
    val visible = presence * clarity
    if (visible <= 0.02f) return
    for (star in STARS) {
        val radius = size.minDimension * (0.0012f + star.magnitude * 0.0022f)
        drawCircle(
            color = Color(0xFFEFF3FF).copy(alpha = visible * (0.20f + star.magnitude * 0.75f)),
            radius = radius,
            center = Offset(star.x * size.width, star.y * size.height * 0.92f),
        )
    }
}

/**
 * Una stella cadente in corsa.
 *
 * Non e' un ciclo: e' un evento. La scia dura poco piu' di mezzo secondo, e fra
 * l'una e l'altra non c'e' niente da disegnare - il che e' anche il modo di
 * averne parecchie senza tenere lo schermo acceso a ridipingere tutta la notte.
 */
class ShootingStar(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val length: Float,
) {
    companion object {
        val NONE = ShootingStar(0f, 0f, 0f, 0f, 0f)

        fun of(random: Random): ShootingStar {
            // Sempre in discesa e sempre di sbieco: una meteora che scende
            // dritta o che sale non si legge come meteora.
            val fromX = random.nextFloat() * 1.2f - 0.1f
            val fromY = random.nextFloat() * 0.34f
            val side = if (random.nextBoolean()) 1f else -1f
            val run = 0.30f + random.nextFloat() * 0.34f
            return ShootingStar(
                fromX = fromX,
                fromY = fromY,
                toX = fromX + side * run,
                toY = fromY + run * (0.45f + random.nextFloat() * 0.4f),
                length = 0.10f + random.nextFloat() * 0.09f,
            )
        }
    }
}

/**
 * Disegna la scia al punto [progress] della sua corsa, da 0 a 1.
 *
 * Tre passate come per il fulmine, e per lo stesso motivo: una riga sola si
 * legge come un tratto di penna. Qui pero' la scia si accorcia mentre avanza,
 * perche' la coda si spegne prima che la testa arrivi.
 */
fun DrawScope.drawShootingStar(star: ShootingStar, progress: Float, presence: Float) {
    if (progress <= 0f || progress >= 1f || presence <= 0.02f) return

    val dx = star.toX - star.fromX
    val dy = star.toY - star.fromY
    val headT = progress
    val tailT = (progress - star.length).coerceAtLeast(0f)

    val head = Offset(
        (star.fromX + dx * headT) * size.width,
        (star.fromY + dy * headT) * size.height,
    )
    val tail = Offset(
        (star.fromX + dx * tailT) * size.width,
        (star.fromY + dy * tailT) * size.height,
    )

    // Nasce e muore sfumando: una meteora che compare gia' accesa e sparisce
    // accesa si legge come un difetto di disegno.
    val fade = (progress / 0.18f).coerceAtMost(1f) *
        ((1f - progress) / 0.30f).coerceAtMost(1f)
    val alpha = fade * presence
    val unit = size.minDimension

    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color(0xFFBBD6FF).copy(alpha = alpha * 0.55f)),
            start = tail,
            end = head,
        ),
        start = tail,
        end = head,
        strokeWidth = unit * 0.010f,
        cap = StrokeCap.Round,
    )
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = alpha)),
            start = tail,
            end = head,
        ),
        start = tail,
        end = head,
        strokeWidth = unit * 0.0035f,
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = Color.White.copy(alpha = alpha),
        radius = unit * 0.005f,
        center = head,
    )
}

// ---------------------------------------------------------------------------
// Uccelli
// ---------------------------------------------------------------------------

/** Uno stormo: ogni uccello ha la sua quota, la sua andatura e il suo ritmo d'ala. */
private class Bird(
    val y: Float,
    val phase: Float,
    val speed: Float,
    val scale: Float,
    val flap: Float,
)

private val BIRDS: List<Bird> = List(9) { i ->
    val r = Random(i * 2311 + 7)
    Bird(
        // Solo nella fascia alta. Scendendo fino a meta' schermo finivano
        // addosso alla scultura e alla cifra, dove un uccello non e' piu'
        // cielo: e' un segno nero sopra l'oggetto.
        y = 0.05f + r.nextFloat() * 0.26f,
        phase = r.nextFloat(),
        speed = 0.6f + r.nextFloat() * 0.7f,
        // Quelli piu' piccoli sono anche piu' lenti e piu' in alto: e' la
        // prospettiva a dirlo, non serve una seconda camera.
        scale = 0.5f + r.nextFloat() * 0.8f,
        flap = 1.6f + r.nextFloat() * 1.6f,
    )
}

/**
 * Gli uccelli in volo.
 *
 * Due archi e basta. La sagoma di un uccello a questa dimensione e' esattamente
 * quella: aggiungere un corpo, una coda o una testa non la rende piu'
 * riconoscibile, la rende solo piu' sporca.
 *
 * Il vento li porta: con vento in poppa attraversano lo schermo in meta' tempo,
 * controvento arrancano. E' gratis - il numero c'e' gia' - e collega il cielo
 * a quello che dice la previsione.
 */
fun DrawScope.drawBirds(clock: SceneClock, presence: Float, wind: Wind, colour: Color) {
    // La stessa soglia con cui si accende il battito. Sotto, l'orologio e'
    // fermo: disegnarli comunque vorrebbe dire uno stormo **immobile** a mezz'
    // aria, che si nota molto piu' di uno stormo assente.
    if (presence <= BIRDS_MIN) return
    val unit = size.minDimension
    val path = Path()

    for (bird in BIRDS) {
        // La direzione di marcia la decide il vento; con aria ferma vanno tutti
        // verso destra, che e' il verso in cui si legge. E siccome vanno **dove
        // va il vento**, il vento non li rallenta mai: li porta, e piu' tira
        // piu' in fretta attraversano.
        val heading = if (wind.push < -0.05f) -1f else 1f
        val pace = bird.speed * (1f + 0.9f * abs(wind.push))
        val travel = (bird.phase + clock.cycle(0.035f * pace)) % 1f

        // Entra da un bordo ed esce dall'altro passando per fuori: la fascia va
        // da -0,15 a 1,15 cosi' nessuno compare o sparisce in mezzo al cielo.
        val across = -0.15f + travel * 1.30f
        val x = (if (heading > 0f) across else 1f - across) * size.width
        val y = bird.y * size.height +
            sin((clock.seconds * 0.5 + bird.phase * 6.28).toFloat()) * unit * 0.012f

        val span = unit * 0.013f * bird.scale
        // L'apertura d'ala oscilla fra quasi chiusa e distesa. A riposo la
        // sagoma deve restare una V riconoscibile: con l'alzata a un terzo
        // dell'apertura, un uccello largo ottanta pixel ne era alto dieci e a
        // meta' battuta diventava un trattino. Ora l'alzata vale quasi quanto
        // l'apertura, e la V si legge in ogni istante del battito.
        val beat = sin((clock.seconds * bird.flap + bird.phase * 6.28).toFloat())
        val lift = span * (0.85f + 0.55f * beat)

        path.reset()
        path.moveTo(x - span * 2f, y - lift * 0.4f)
        path.quadraticTo(x - span, y - lift, x, y)
        path.quadraticTo(x + span, y - lift, x + span * 2f, y - lift * 0.4f)

        drawPath(
            path = path,
            color = colour.copy(alpha = presence * (0.40f + 0.50f * bird.scale)),
            style = Stroke(
                width = (unit * 0.0030f * bird.scale).coerceAtLeast(1.2f),
                cap = StrokeCap.Round,
            ),
        )
    }
}

/** Sotto questa presenza il battito e' spento, quindi non si disegnano. */
private const val BIRDS_MIN = 0.15f

/**
 * Dove finisce la scena e comincia l'interfaccia, in frazione dell'altezza.
 *
 * Sotto stanno la condizione, la barra delle ore e l'ora: sono testo, non
 * mondo, e il mondo non deve andarci sopra.
 */
private const val SCENE_BOTTOM = 0.80f

// ---------------------------------------------------------------------------
// Nebbia
// ---------------------------------------------------------------------------

/**
 * I banchi di nebbia.
 *
 * ## Perche' non e' una velatura sopra la scena
 *
 * Una lastra uniforme al quaranta per cento di bianco e' esattamente cio' che
 * la nebbia **non** sembra: sbianca tutto in modo uguale, cioe' non toglie
 * profondita', la toglie a tutti allo stesso modo - che a occhio si legge come
 * uno schermo sporco. La nebbia vera e' un volume: e' densa in basso e rada in
 * alto, si muove, e mangia gli oggetti in ordine di distanza.
 *
 * Qui sono fasce orizzontali sovrapposte, ciascuna con la propria quota, la
 * propria opacita' e la propria deriva. Il vento le porta, piu' in fretta
 * quelle vicine di quelle lontane, e sono proprio le velocita' diverse a far
 * leggere lo spessore.
 */
fun DrawScope.drawFog(
    clock: SceneClock,
    density: Float,
    wind: Wind,
    near: Color,
    far: Color,
) {
    if (density <= 0.02f) return

    // **Il fondo della nebbia non e' il fondo dello schermo.**
    //
    // Sotto questa quota non c'e' piu' scena: ci sono la condizione, la barra
    // delle ore e l'ora mostrata. Portando i banchi fin laggiu' - come facevano
    // prima - il fondo dietro quei testi si schiariva di parecchio, e il
    // contrasto garantito dalla tavolozza, che e' calcolato contro `skyBottom`,
    // smetteva di valere: l'ora scritta in grigio chiaro su nebbia grigio
    // chiara era di nuovo illeggibile, cioe' il difetto appena tolto rimesso in
    // piedi da un'altra porta.
    val height = size.height * SCENE_BOTTOM

    // Il velo di fondo: sale da terra e si esaurisce a meta' altezza. E' cio'
    // che da' il "non si vede niente" senza cancellare il cielo in cima.
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.30f to far.copy(alpha = density * 0.16f),
            0.68f to near.copy(alpha = density * 0.62f),
            SCENE_BOTTOM to near.copy(alpha = density * 0.30f),
            1f to Color.Transparent,
        ),
        topLeft = Offset.Zero,
        size = size,
    )

    for (band in FOG_BANDS) {
        // La deriva e' fatta con seno e coseno di due ritmi diversi invece che
        // con un ciclo che si riavvolge: cosi' non c'e' nessun istante in cui il
        // banco torna al punto di partenza, che e' l'unico modo perche' una
        // nebbia non riveli di essere una tendina che scorre.
        val t = clock.seconds.toFloat()
        val drift = sin(t * band.rate + band.phase) * 0.5f +
            cos(t * band.rate * 0.61f + band.phase * 1.7f) * 0.5f
        val blown = wind.push * band.depth * 0.22f
        val cx = (0.5f + drift * band.travel + blown) * size.width
        val cy = (band.y + sin(t * band.rate * 0.43f + band.phase) * 0.012f) * height

        val bandWidth = size.width * band.width
        val bandHeight = height * band.thickness
        val tint = androidx.compose.ui.graphics.lerp(far, near, band.depth)

        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    tint.copy(alpha = density * band.opacity),
                    tint.copy(alpha = 0f),
                ),
                center = Offset(cx, cy),
                radius = bandWidth / 2f,
            ),
            topLeft = Offset(cx - bandWidth / 2f, cy - bandHeight / 2f),
            size = Size(bandWidth, bandHeight),
        )
    }
}

/**
 * Un banco: quota, spessore, quanto e' vicino, quanto deriva.
 *
 * Ordinati dal piu' lontano al piu' vicino, che e' anche l'ordine in cui vanno
 * disegnati: qui non c'e' buffer di profondita', e la nebbia vicina deve
 * coprire quella lontana e non il contrario.
 */
private class FogBand(
    val y: Float,
    val width: Float,
    val thickness: Float,
    val opacity: Float,
    val depth: Float,
    val travel: Float,
    val rate: Float,
    val phase: Float,
)

private val FOG_BANDS = listOf(
    FogBand(0.52f, 1.9f, 0.30f, 0.30f, 0.10f, 0.09f, 0.043f, 0.0f),
    FogBand(0.64f, 2.2f, 0.34f, 0.36f, 0.35f, 0.13f, 0.031f, 2.1f),
    FogBand(0.76f, 2.0f, 0.30f, 0.44f, 0.62f, 0.17f, 0.052f, 4.3f),
    FogBand(0.88f, 2.6f, 0.38f, 0.54f, 0.85f, 0.22f, 0.038f, 1.2f),
    FogBand(1.00f, 2.8f, 0.44f, 0.62f, 1.00f, 0.27f, 0.026f, 5.6f),
)
