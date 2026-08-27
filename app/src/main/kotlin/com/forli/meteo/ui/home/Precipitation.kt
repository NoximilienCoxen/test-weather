package com.forli.meteo.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.forli.meteo.data.Wind
import com.forli.meteo.ui.render3d.Camera
import com.forli.meteo.ui.render3d.SceneContact
import com.forli.meteo.ui.render3d.Skyline
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Quello che cade dalla nuvola, e cosa succede quando tocca.
 *
 * Gocce e fiocchi vivono nello spazio del modello, non su quello dello schermo:
 * ruotano con la nuvola, quelli davanti scorrono piu' di quelli dietro, e sono
 * grandi quanto la distanza impone. Il vento li inclina tutti nella stessa
 * direzione, che e' la sola cosa che distingue una nevicata da uno schermo
 * salvaschermo.
 */

/** Una particella con un posto suo sotto la nuvola. */
private class Particle(
    /** Posizione sotto la nuvola, da -1 a 1 sui due assi orizzontali. */
    val x: Float,
    val z: Float,
    val phase: Float,
    val speed: Float,
    /** Lunghezza della scia per la pioggia, diametro per la neve. */
    val size: Float,
    /** Ampiezza dell'ondeggiamento laterale. Le gocce non ne hanno. */
    val sway: Float,
    val swayRate: Float,
)

private fun particles(count: Int, seed: Int, swaying: Boolean): List<Particle> =
    List(count) { i ->
        val r = Random(i * 7919 + seed)
        Particle(
            x = r.nextFloat() * 2f - 1f,
            z = r.nextFloat() * 2f - 1f,
            phase = r.nextFloat(),
            speed = 0.85f + r.nextFloat() * 0.5f,
            size = if (swaying) 0.5f + r.nextFloat() else 0.05f + r.nextFloat() * 0.05f,
            sway = if (swaying) 0.25f + r.nextFloat() * 0.75f else 0f,
            swayRate = 0.28f + r.nextFloat() * 0.5f,
        )
    }

private val DROPS = particles(count = 34, seed = 13, swaying = false)

/**
 * I fiocchi sono molti di piu' delle gocce, e non e' un capriccio: una goccia si
 * legge da sola perche' e' una riga lunga, un fiocco e' un punto e da solo non
 * dice niente. La neve la si riconosce dalla **quantita'**.
 * 200 fiocchi: abbastanza densi da leggere come nevicata vera anche con intensita' bassa.
 */
private val FLAKES = particles(count = 200, seed = 4391, swaying = true)

/** Giri al secondo della caduta. La neve scende molto piu' piano dell'acqua. */
private const val RAIN_RATE = 0.72f
private const val SNOW_RATE = 0.15f

/**
 * Quanto il vento sposta di lato una particella lungo tutta la sua caduta, in
 * frazione della caduta stessa.
 *
 * La neve deriva molto piu' della pioggia, ed e' il punto: una goccia pesa e
 * scende comunque, un fiocco fa quello che gli dice l'aria. Ma non oltre - vedi
 * la nota sul centraggio in [drawRain]: piu' si deriva, piu' la fascia si
 * allarga, e piu' particelle stanno fuori dallo schermo invece che dentro.
 */
private const val RAIN_DRIFT = 0.50f
private const val SNOW_DRIFT = 0.75f

// ---------------------------------------------------------------------------
// Pioggia
// ---------------------------------------------------------------------------

fun DrawScope.drawRain(
    camera: Camera,
    unit: Float,
    scale: Float,
    wetness: Float,
    confidence: Float,
    clock: SceneClock,
    wind: Wind,
    colour: Color,
    contact: SceneContact?,
) {
    val count = (DROPS.size * wetness).roundToInt().coerceIn(3, DROPS.size)
    val spreadX = unit * 0.40f * scale
    val spreadZ = unit * 0.17f * scale
    val top = unit * 0.16f * scale
    val span = unit * 0.90f
    val width = unit * 0.013f
    val shift = contact?.numberToRain ?: Offset.Zero
    val skyline = contact?.skyline

    // Quanto si sposta di lato una goccia lungo tutta la caduta. Da qui si
    // ricava anche l'inclinazione della scia: una goccia si allunga lungo la
    // propria velocita', non lungo la verticale, ed e' proprio quello a
    // raccontare che tira vento.
    val drift = wind.push * RAIN_DRIFT
    val slantLength = hypot(drift, 1f)
    val slantX = drift / slantLength
    val slantY = 1f / slantLength

    for (i in 0 until count) {
        val drop = DROPS[i]
        val travel = (drop.phase + clock.cycle(RAIN_RATE * drop.speed)) % 1f
        val y = top + travel * span
        // `travel - 0.5` e non `travel`, ed e' la differenza fra pioggia
        // inclinata e pioggia **spostata**. Sommando la deriva a partire da
        // zero, ogni particella nasce sopra la sua colonna e finisce sottovento:
        // col vento forte la meta' sopravvento dello schermo resta vuota e
        // tutto si ammassa da una parte, che non e' quello che fa la pioggia -
        // da sopravvento continua ad arrivarne dell'altra. Togliendo mezza
        // corsa, la fascia percorsa resta centrata sulla scena e cambia solo
        // l'inclinazione, che e' esattamente cio' che si voleva mostrare.
        val x = drop.x * spreadX + drift * (travel - 0.5f) * span
        val z = drop.z * spreadZ

        camera.place(x, y, z)
        val head = Offset(camera.sx, camera.sy)
        val near = camera.scale
        camera.place(x - slantX * drop.size * unit, y - slantY * drop.size * unit, z)
        val tail = Offset(camera.sx, camera.sy)

        // Entra sfumando: una goccia che appare dal nulla a mezz'aria si legge
        // come uno sfarfallio, non come pioggia.
        val alpha = (0.40f + 0.60f * confidence) * (travel / 0.08f).coerceAtMost(1f)
        val stroke = (width * near).coerceAtLeast(1.5f)

        // Dove comincia la cifra, sotto questa goccia. La sagoma arriva in
        // coordinate della propria tela: si sposta nelle nostre.
        val surface = skyline?.topAt(head.x - shift.x)?.let { it + shift.y } ?: Float.NaN

        if (surface.isNaN() || head.y < surface) {
            drawLine(
                color = colour.copy(alpha = alpha * (1f - travel * 0.35f)),
                start = tail,
                end = head,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            continue
        }

        // Ha toccato. Quanto della goccia e' gia' entrato nella superficie dice
        // da quanto: appena arrivata lo schizzo e' stretto e pieno, poi si apre
        // e svanisce. Non serve ricordarsi niente da un fotogramma all'altro,
        // il tempo e' gia' scritto nella posizione.
        val sunk = ((head.y - surface) / (head.y - tail.y).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val stop = surface - stroke * 1.7f
        if (tail.y < stop) {
            drawLine(
                color = colour.copy(alpha = alpha),
                start = tail,
                end = Offset(head.x, stop),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        splash(Offset(head.x, surface), stroke, sunk, colour, alpha, wind.push)
    }
}

/**
 * Lo schizzo: due schegge che partono ai lati del punto colpito e si aprono.
 *
 * Non una corona tonda vista di taglio, che a questa dimensione sarebbe una
 * riga. E non due segmenti attaccati al punto d'impatto: staccate dal centro si
 * leggono come acqua che rimbalza, unite come una punta di freccia.
 */
private fun DrawScope.splash(
    at: Offset,
    stroke: Float,
    age: Float,
    colour: Color,
    alpha: Float,
    push: Float,
) {
    val fade = alpha * (1f - age) * 0.9f
    if (fade <= 0.01f) return

    val gap = stroke * (0.9f + age * 1.3f)
    val reach = stroke * (1.5f + age * 3.2f)
    val lift = stroke * (1.15f - age * 0.8f)
    val tint = lerp(colour, Color.White, 0.30f).copy(alpha = fade)

    // Col vento lo schizzo non e' simmetrico: sottovento va piu' lontano. E'
    // un dettaglio da due righe che pero' toglie l'aria di simbolo stampato.
    val lee = 1f + push * 0.45f
    val luff = 1f - push * 0.45f

    drawLine(
        color = tint,
        start = Offset(at.x - gap, at.y),
        end = Offset(at.x - gap - reach * luff, at.y - lift),
        strokeWidth = stroke * 0.60f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = tint,
        start = Offset(at.x + gap, at.y),
        end = Offset(at.x + gap + reach * 0.88f * lee, at.y - lift * 0.85f),
        strokeWidth = stroke * 0.60f,
        cap = StrokeCap.Round,
    )
}

// ---------------------------------------------------------------------------
// Neve
// ---------------------------------------------------------------------------

/**
 * La nevicata, piu' quello che si deposita sopra la cifra.
 *
 * E' una classe e non una funzione perche' ha due cose da ricordare fra un
 * fotogramma e l'altro: i vettori dei punti, che si riempiono e si riusano
 * invece di essere riallocati centotrenta volte al secondo, e la coltre.
 */
@Stable
class Snowfall {

    val cap = SnowCap()

    /**
     * I fiocchi finiscono in tre secchi per distanza, e ogni secchio esce con
     * **una** chiamata.
     *
     * Centotrenta `drawCircle` per fotogramma sarebbero centotrenta sagome da
     * riempire, che e' esattamente l'errore gia' pagato con le pareti della
     * cifra: riempire costa in proporzione alla superficie, e la superficie qui
     * e' minuscola ma il numero delle chiamate no. `drawPoints` prende un
     * vettore piatto di coordinate e un pennello a punta tonda: tre chiamate in
     * tutto, e nessuna allocazione per fotogramma.
     */
    private val buckets = Array(BUCKETS) { FloatArray(FLAKES.size * 2) }
    private val filled = IntArray(BUCKETS)
    private val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        strokeCap = android.graphics.Paint.Cap.ROUND
        style = android.graphics.Paint.Style.STROKE
    }

    fun draw(
        scope: DrawScope,
        camera: Camera,
        unit: Float,
        scale: Float,
        amount: Float,
        clock: SceneClock,
        wind: Wind,
        colour: Color,
        contact: SceneContact?,
    ) = with(scope) {
        val count = (FLAKES.size * amount).roundToInt().coerceIn(12, FLAKES.size)
        val spreadX = unit * 0.62f * scale
        val spreadZ = unit * 0.30f * scale
        val top = unit * 0.14f * scale
        val span = unit * 1.05f
        val shift = contact?.numberToRain ?: Offset.Zero
        val skyline = contact?.skyline
        val drift = wind.push * SNOW_DRIFT

        java.util.Arrays.fill(filled, 0)
        var landed = 0f

        for (i in 0 until count) {
            val flake = FLAKES[i]
            val travel = (flake.phase + clock.cycle(SNOW_RATE * flake.speed)) % 1f
            val y = top + travel * span

            // L'ondeggiamento e' quello che rende la neve neve: un fiocco non
            // cade, galleggia. Ogni fiocco ha il proprio ritmo, altrimenti
            // oscillerebbero tutti insieme come un'unica tendina.
            val wobble = sin((clock.seconds * flake.swayRate + flake.phase * 6.28).toFloat())
            // Meno mezza corsa, per lo stesso motivo della pioggia: il vento
            // deve inclinare la traiettoria, non svuotare il campo sopravvento.
            val x = flake.x * spreadX +
                drift * (travel - 0.5f) * span +
                wobble * flake.sway * unit * 0.05f
            val z = flake.z * spreadZ

            camera.place(x, y, z)
            val sx = camera.sx
            val sy = camera.sy
            val near = camera.scale

            // Sotto la superficie della cifra il fiocco non c'e' piu': si e'
            // posato. Non sparisce di colpo, l'ultimo tratto lo consuma la
            // coltre, che infatti cresce proprio di quello.
            val surface = skyline?.topAt(sx - shift.x)?.let { it + shift.y } ?: Float.NaN
            if (!surface.isNaN() && sy >= surface) {
                landed += 1f
                continue
            }

            // Il secchio lo decide la distanza, non la posizione sullo schermo:
            // un fiocco vicino e' grande e pieno, uno lontano e' un puntino
            // smorzato. E' la sola profondita' che una nevicata possa mostrare.
            val bucket = when {
                near > 1.06f -> 0
                near > 0.94f -> 1
                else -> 2
            }
            val slot = filled[bucket]
            buckets[bucket][slot] = sx
            buckets[bucket][slot + 1] = sy
            filled[bucket] = slot + 2
        }

        val fade = (amount * 2.2f).coerceAtMost(1f)
        drawIntoCanvas { canvas ->
            for (bucket in 0 until BUCKETS) {
                if (filled[bucket] == 0) continue
                paint.strokeWidth = (unit * BUCKET_SIZE[bucket]).coerceAtLeast(1.6f)
                paint.color = colour.copy(alpha = BUCKET_ALPHA[bucket] * fade).toArgb()
                canvas.nativeCanvas.drawPoints(buckets[bucket], 0, filled[bucket], paint)
            }
        }

        // Quanti fiocchi si sono posati in questo fotogramma dice quanto in
        // fretta cresce la coltre: cade piu' fitto, si accumula prima.
        cap.feed(landed / count.toFloat())
    }

    private companion object {
        const val BUCKETS = 3
        val BUCKET_SIZE = floatArrayOf(0.026f, 0.019f, 0.013f)
        val BUCKET_ALPHA = floatArrayOf(0.98f, 0.80f, 0.55f)
    }
}

// ---------------------------------------------------------------------------
// La coltre
// ---------------------------------------------------------------------------

/**
 * La neve che si posa sopra la cifra, e che ne scivola via quando la si gira.
 *
 * Vive sulla stessa griglia di colonne della [Skyline], che e' gia' il modo in
 * cui l'oggetto dichiara dove offre superficie: dove la sagoma ha un punto piu'
 * alto, li' la neve puo' fermarsi; dove non c'e' niente - fra una cifra e
 * l'altra, dentro il buco dello zero - non si accumula nulla, e non serve
 * nessun caso particolare per ottenerlo.
 *
 * ## Perche' scivola invece di sparire
 *
 * Girando, la coltre non puo' restare: sta appoggiata su una superficie che si
 * sta voltando, e una neve che rimane incollata mentre l'oggetto ruota si legge
 * come una decalcomania. Ma non puo' nemmeno svanire sul posto, perche' la neve
 * non evapora. Scende, si sposta nel verso della rotazione e si smorza, tutto
 * nello stesso mezzo secondo: e' quello che fa una lastra che si stacca.
 */
@Stable
class SnowCap {

    private var depth = FloatArray(0)
    private var columns = 0

    /** Quanto della coltre e' in caduta, da 0 (posata) a 1 (staccata del tutto). */
    private var slide = 0f
    private var slideDirection = 1f

    private var lastSeconds = 0.0
    private var lastYaw = Float.NaN

    /** Riusata a ogni fotogramma: allocarne una per disegno e' lavoro per il raccoglitore. */
    private val outline = androidx.compose.ui.graphics.Path()

    /** Frazione di fiocchi che si sono posati nell'ultimo fotogramma. */
    private var landing = 0f

    internal fun feed(fraction: Float) {
        landing = fraction
    }

    /**
     * Fa passare il tempo sulla coltre.
     *
     * @param snowing falso quando ha smesso: allora la neve non cresce piu' e
     *   quella che c'e' si consuma piano, come si consuma davvero.
     */
    fun advance(
        clock: SceneClock,
        skyline: Skyline,
        yawDeg: Float,
        snowing: Boolean,
        width: Float,
    ) {
        val needed = ((width / Skyline.COLUMN_PX).toInt() + 2).coerceAtLeast(2)
        if (columns != needed) {
            depth = FloatArray(needed)
            columns = needed
        }

        val delta = (clock.seconds - lastSeconds).coerceIn(0.0, 0.1).toFloat()
        lastSeconds = clock.seconds
        if (delta <= 0f) return

        // Quanto ha girato da un fotogramma all'altro, riportato dentro il mezzo
        // giro. Il primo passaggio non ha un prima: senza quella guardia la
        // coltre si staccherebbe da sola all'apertura, per una rotazione che non
        // c'e' mai stata. E senza la riduzione ciclica se ne staccherebbe una
        // seconda volta a fine molla: finito un giro intero l'angolo torna a
        // zero di scatto, e trecentosessanta gradi di differenza in un
        // fotogramma non sono una rotazione, sono lo stesso identico posto.
        val turn = if (lastYaw.isNaN()) {
            0f
        } else {
            val raw = (yawDeg - lastYaw) % 360f
            when {
                raw > 180f -> raw - 360f
                raw < -180f -> raw + 360f
                else -> raw
            }
        }
        lastYaw = yawDeg

        if (abs(turn) > TURN_TO_SHED * delta) {
            if (turn != 0f) slideDirection = if (turn > 0f) 1f else -1f
            slide = (slide + abs(turn) * SHED_PER_DEGREE).coerceAtMost(1f)
        } else {
            slide = (slide - delta * SLIDE_RECOVERY).coerceAtLeast(0f)
        }

        val growth = if (snowing) landing * delta * GROWTH else 0f
        val loss = delta * (MELT + slide * SHED_LOSS)
        for (c in 0 until columns) {
            // Cresce **solo** dove la sagoma offre superficie. Farla crescere
            // ovunque e disegnarla poi solo dove serve sembra equivalente e non
            // lo e': cambiando ora la cifra cambia forma, e le colonne che
            // prima erano vuote si troverebbero addosso di colpo la coltre
            // intera, accumulata mentre li' non c'era niente.
            val standing = !skyline.topAt(c * Skyline.COLUMN_PX).isNaN()
            val added = if (standing) growth else 0f
            depth[c] = (depth[c] + added - loss).coerceIn(0f, MAX_DEPTH)
        }
    }

    /**
     * Disegna la coltre lungo la sagoma appena tracciata.
     *
     * Un tracciato solo, non una barra per colonna: sono sessanta colonne, e
     * sessanta rettangoli sono sessanta sagome da riempire per una striscia
     * alta pochi pixel. Le interruzioni della sagoma diventano sottotracciati
     * separati, che e' anche il modo in cui la neve smette da sola fra una
     * cifra e l'altra.
     */
    fun draw(scope: DrawScope, skyline: Skyline, unit: Float, colour: Color) = with(scope) {
        if (columns == 0 || slide >= 0.999f) return@with
        val thickness = unit * 0.055f
        val fall = slide * unit * 0.30f
        val sideways = slideDirection * slide * unit * 0.10f
        val alpha = (1f - slide) * 0.95f
        if (alpha <= 0.01f) return@with

        val path = outline
        path.reset()
        var run = 0
        var start = 0

        fun flush(from: Int, until: Int) {
            if (until - from < 1) return
            // Il bordo superiore da sinistra a destra...
            for (c in from until until) {
                val x = c * Skyline.COLUMN_PX + Skyline.COLUMN_PX / 2f + sideways
                val y = skyline.topAt(c * Skyline.COLUMN_PX) - depth[c] * thickness + fall
                if (c == from) path.moveTo(x, y) else path.lineTo(x, y)
            }
            // ...e quello inferiore tornando indietro, appena dentro la sagoma
            // cosi' la coltre appoggia invece di galleggiare.
            for (c in until - 1 downTo from) {
                val x = c * Skyline.COLUMN_PX + Skyline.COLUMN_PX / 2f + sideways
                val y = skyline.topAt(c * Skyline.COLUMN_PX) + thickness * 0.35f + fall
                path.lineTo(x, y)
            }
            path.close()
        }

        for (c in 0 until columns) {
            val here = skyline.topAt(c * Skyline.COLUMN_PX)
            val solid = !here.isNaN() && depth[c] > 0.02f
            if (solid) {
                if (run == 0) start = c
                run++
            } else if (run > 0) {
                flush(start, start + run)
                run = 0
            }
        }
        if (run > 0) flush(start, start + run)

        drawPath(path = path, color = colour.copy(alpha = alpha))
    }

    private companion object {
        /** Spessore massimo, in multipli di [SnowCap.draw]'s `thickness`. */
        const val MAX_DEPTH = 1f

        /** Quanto cresce al secondo con tutti i fiocchi che si posano. */
        const val GROWTH = 0.55f

        /** Quanto si consuma al secondo per conto suo. */
        const val MELT = 0.035f

        /** Consumo aggiuntivo mentre sta scivolando. */
        const val SHED_LOSS = 1.6f

        /** Gradi al secondo oltre i quali la rotazione la stacca. */
        const val TURN_TO_SHED = 22f

        /** Quanto si stacca per grado girato. */
        const val SHED_PER_DEGREE = 0.020f

        /** Quanto in fretta si riposa dopo che la rotazione e' finita. */
        const val SLIDE_RECOVERY = 1.5f
    }
}
