package com.forli.meteo.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.forli.meteo.data.SkyState
import com.forli.meteo.data.Wind
import com.forli.meteo.data.Wmo
import com.forli.meteo.ui.motion.SceneRotation
import com.forli.meteo.ui.motion.rememberWeatherHaptics
import com.forli.meteo.ui.render3d.Camera
import com.forli.meteo.ui.render3d.SceneContact
import com.forli.meteo.ui.render3d.moon
import com.forli.meteo.ui.render3d.sphere
import com.forli.meteo.ui.render3d.sunRays
import com.forli.meteo.ui.theme.LocalMeteoColors
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Sole, luna, nuvole, pioggia e fulmini nello stesso spazio della cifra,
 * guardati dalla stessa camera e illuminati dalla stessa luce. E' questo che li
 * fa appartenere allo stesso mondo invece di sembrare icone incollate sopra.
 *
 * Ruotano insieme alla cifra perche' sono un oggetto solo con lei. Le masse
 * della nuvola stanno a profondita' diverse: girando, quelle davanti scorrono
 * piu' di quelle dietro e si scavalcano. E' la parallasse a dire che c'e' spazio
 * fra loro, e nessuna quantita' di ombreggiatura potrebbe sostituirla.
 *
 * Niente compare o sparisce di colpo: la nuvola cambia carattere con
 * continuita' - si addensa, si scurisce, le gocce aumentano. Un elemento che
 * spunta a un'ora precisa si legge come un difetto, non come informazione.
 */
@Composable
fun WeatherSculpture(
    weatherCode: Int?,
    precipitationMm: Double?,
    probability: Int?,
    sky: SkyState,
    date: LocalDate,
    rotation: SceneRotation,
    /** Falso quando la schermata non e' in primo piano: allora niente vibrazione. */
    feelsIt: Boolean,
    /** Dove la cifra offre superficie alla pioggia. */
    contact: SceneContact,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val haptics = rememberWeatherHaptics()

    val family = Wmo.family(weatherCode)
    val raining = family.isWet()
    val storming = family == Wmo.Family.TEMPORALE

    /**
     * Quanta pioggia si vede.
     *
     * I millimetri da soli non bastano: un temporale previsto al sessanta per
     * cento puo' avere zero millimetri in quell'ora esatta, e allora sotto la
     * scritta TEMPORALE non cadeva niente. Se il codice dice che piove, deve
     * piovere; i millimetri decidono quanto forte, non se.
     */
    val target = when {
        raining -> maxOf(
            (precipitationMm ?: 0.0).toFloat() / 6f,
            0.28f + 0.34f * ((probability ?: 0) / 100f),
        ).coerceAtMost(1f)
        else -> ((precipitationMm ?: 0.0).toFloat() / 6f).coerceIn(0f, 1f)
    }

    val cloudiness by animateFloatAsState(
        targetValue = Wmo.cloudiness(weatherCode),
        animationSpec = spring(stiffness = 120f),
        label = "nuvolosita",
    )
    val wetness by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(stiffness = 120f),
        label = "pioggia",
    )
    val confidence by animateFloatAsState(
        targetValue = ((probability ?: 0) / 100f).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 120f),
        label = "probabilita",
    )
    // La nuvola diventa grigia perche' porta pioggia, non perche' ne stia gia'
    // cadendo tanta: una pioviggine viene da una nuvola carica quanto un
    // rovescio, e a colpo d'occhio e' il grigio a dire che ci si bagna.
    val laden by animateFloatAsState(
        targetValue = if (raining) 1f else 0f,
        animationSpec = spring(stiffness = 110f),
        label = "carica",
    )

    // La caduta ha un orologio suo, battuto a mano sui fotogrammi.
    //
    // Con `rememberInfiniteTransition` le gocce restavano ferme: misurato, con
    // la pioggia accesa e nessun dito sullo schermo l'app disegnava **zero**
    // fotogrammi. Qualunque ne sia la ragione dentro la libreria, un'animazione
    // che si vede solo nel disegno e mai in composizione non e' terreno su cui
    // fidarsi di una comodita'. Qui il ciclo e' esplicito: gira solo quando
    // piove, e ogni battito scrive un valore che il disegno legge.
    val fall = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(raining) {
        if (!raining) return@LaunchedEffect
        var origin = 0L
        while (true) {
            withFrameNanos { now ->
                if (origin == 0L) origin = now
                val elapsed = (now - origin) / 1_000_000L
                fall.floatValue = (elapsed % FALL_CYCLE_MS) / FALL_CYCLE_MS.toFloat()
            }
        }
    }

    val flash = remember { Animatable(0f) }
    var bolt by remember { mutableStateOf(Bolt.EMPTY) }

    // Il tuono. Il colpo in mano parte insieme al lampo, non dopo: e' il lampo
    // che si vede, ed e' quello che si deve sentire.
    LaunchedEffect(storming, feelsIt) {
        if (!storming) {
            flash.snapTo(0f)
            return@LaunchedEffect
        }
        var seed = 7
        while (true) {
            delay(Random(seed).nextLong(2600, 5200))
            bolt = Bolt.of(Random(seed))
            seed = (seed * 31 + 17) and 0xFFFF
            if (feelsIt) haptics.thunder()
            flash.snapTo(1f)
            delay(70)
            flash.snapTo(0.3f)
            delay(55)
            flash.snapTo(0.95f)
            delay(60)
            flash.animateTo(0f, tween(260, easing = LinearEasing))
        }
    }

    // La pioggia si sente appena, un tocco per ogni giro di gocce. Continuo
    // sarebbe un ronzio, e un ronzio non e' pioggia.
    LaunchedEffect(raining, feelsIt) {
        if (!raining || !feelsIt) return@LaunchedEffect
        while (true) {
            delay(FALL_CYCLE_MS)
            haptics.drizzle()
        }
    }

    val phase = remember(date) { MoonPhase.at(date) }

    // Presenza uccelli: calcolata qui fuori per poterla usare sia nel clock
    // sia nel Canvas senza ricalcolare due volte.
    val birdsPresenceFraction = ((sky.sunPresence - 0.1f) / 0.5f).coerceIn(0f, 1f) *
        (1f - cloudiness * 0.80f).coerceIn(0f, 1f)

    // Orologio per uccelli: gira solo quando sono visibili, cosi' a notte
    // fonda l'app non chiede fotogrammi per un cielo vuoto.
    val skyLifeClock = rememberSceneClock(running = birdsPresenceFraction > 0.15f)

    Canvas(
        modifier.onGloballyPositioned { coordinates ->
            contact.rainOrigin = coordinates.positionInRoot()
        },
    ) {
        val unit = size.minDimension
        val camera = Camera(
            yawDeg = rotation.yawDeg + rotation.breathingOffset,
            pitchDeg = 0f,
            // Piu' vicina di quella della cifra rispetto alla propria
            // dimensione: la scultura e' un oggetto piccolo tenuto vicino
            // all'occhio, e girandola la prospettiva deve sentirsi.
            distance = unit * 2.1f,
            // In basso nel proprio riquadro: la scultura deve appoggiarsi alla
            // cifra, non galleggiare sopra un vuoto.
            origin = Offset(size.width / 2f, size.height * 0.74f),
        )
        val glare = flash.value

        // Stelle: visibili di notte con cielo sereno.
        val starsPresence = sky.moonPresence * (1f - cloudiness * 0.95f).coerceIn(0f, 1f)
        drawStars(presence = starsPresence, clarity = 1f)

        // Uccelli: presenti di giorno con cielo almeno parzialmente visibile.
        if (birdsPresenceFraction > 0.15f) {
            drawBirds(
                clock = skyLifeClock,
                presence = birdsPresenceFraction,
                wind = Wind.CALMA,
                colour = colors.label,
            )
        }

        // Sotto una nuvola spessa l'astro sparisce del tutto. Con una velatura
        // parziale i raggi del sole sbucavano da dietro un temporale, che e'
        // esattamente il tipo di dettaglio che rovina l'illusione.
        val clear = (1f - cloudiness * 1.25f).coerceIn(0f, 1f)

        val bodyX = unit * 0.15f
        val bodyY = -unit * 0.10f
        val bodyZ = unit * 0.34f
        val bodyRadius = unit * 0.23f

        val sunAlpha = clear * sky.sunPresence
        if (sunAlpha > 0.01f) {
            sunRays(camera, bodyX, bodyY, bodyZ, bodyRadius, colors.sunCore, sunAlpha * 0.75f, far = true)
            sphere(camera, bodyX, bodyY, bodyZ, bodyRadius, colors.sunCore, colors.sunShade, sunAlpha)
            sunRays(camera, bodyX, bodyY, bodyZ, bodyRadius, colors.sunCore, sunAlpha * 0.75f, far = false)
        }

        // La luna e' un corpo solido: con la nebbia si attenua ma non sparisce
        // mai del tutto. Minimo 0.25 quando e' presente, per mantenerla leggibile
        // come oggetto materico anche con cielo coperto o nebbioso.
        val moonRaw = clear * sky.moonPresence
        val moonAlpha = if (sky.moonPresence > 0.05f) moonRaw.coerceAtLeast(0.25f * sky.moonPresence) else moonRaw
        if (moonAlpha > 0.01f) {
            moon(
                camera = camera,
                x = bodyX,
                y = bodyY,
                z = bodyZ,
                radius = bodyRadius * 0.94f,
                phase = phase,
                light = colors.moonCore,
                dark = colors.moonShade,
                alpha = moonAlpha,
                marks = MOON_SEAS,
            )
        }

        val scale = 0.52f + cloudiness * 0.48f

        if (cloudiness > 0.02f) {
            val presence = ((cloudiness - 0.06f) / 0.16f).coerceIn(0f, 1f)
            val masses = (2 + (cloudiness * 3f).roundToInt()).coerceIn(2, 5)
            // Il lampo illumina la nuvola da dentro: se restasse dello stesso
            // grigio, la saetta sembrerebbe disegnata davanti a un fondale.
            val core = lerp(lerp(colors.cloudCore, colors.rainCloudCore, laden), Lightning, glare * 0.55f)
            val shade = lerp(lerp(colors.cloudShade, colors.rainCloudShade, laden), Lightning, glare * 0.40f)

            // Dal fondo verso l'osservatore, e in coordinate di vista: senza un
            // buffer di profondita' e' l'ordine di disegno a decidere chi sta
            // davanti, e ordinandole per la posizione nel modello bastava
            // girare la scena di mezzo giro perche' si scavalcassero al
            // contrario.
            CLOUD_MASSES.take(masses)
                .sortedByDescending { lump ->
                    camera.place(lump.x * unit * scale, lump.y * unit * scale, lump.z * unit * scale)
                    camera.vz
                }
                .forEach { lump ->
                    sphere(
                        camera = camera,
                        x = lump.x * unit * scale,
                        y = lump.y * unit * scale,
                        z = lump.z * unit * scale,
                        radius = lump.radius * unit * scale,
                        light = core,
                        dark = shade,
                        alpha = presence,
                    )
                }
        }

        if (glare > 0.01f && bolt.isNotEmpty()) {
            drawBolt(camera, unit, scale, bolt, glare)
        }

        if (wetness > 0.01f) {
            drawRain(
                camera = camera,
                unit = unit,
                scale = scale,
                wetness = wetness,
                confidence = confidence,
                progress = fall.floatValue,
                colour = colors.rain,
                contact = contact,
            )
        }
    }
}

/** Una massa della nuvola: posizione nello spazio e raggio, in frazioni di unita'. */
private class Lump(val x: Float, val y: Float, val z: Float, val radius: Float)

/**
 * Le masse sono sparse anche in profondita', non solo sul piano. Tutte alla
 * stessa distanza la nuvola sarebbe un ritaglio di cartone, e ruotandola si
 * vedrebbe che lo e'.
 */
private val CLOUD_MASSES = listOf(
    Lump(-0.26f, 0.02f, 0.16f, 0.19f),
    Lump(0.00f, -0.09f, -0.06f, 0.25f),
    Lump(0.26f, 0.03f, 0.12f, 0.20f),
    Lump(-0.11f, 0.10f, -0.19f, 0.18f),
    Lump(0.15f, 0.11f, -0.14f, 0.17f),
)

/**
 * I mari lunari: due componenti sulla sfera unitaria e il raggio della macchia.
 * Non sono una mappa fedele, sono l'appiglio che permette di vedere che la luna
 * sta girando invece di stare ferma a farsi guardare.
 */
private val MOON_SEAS = listOf(
    Triple(-0.30f, -0.24f, 0.20f),
    Triple(0.16f, 0.05f, 0.26f),
    Triple(-0.08f, 0.42f, 0.15f),
    Triple(0.42f, -0.34f, 0.12f),
)

/**
 * Una goccia, con un posto suo sotto la nuvola.
 *
 * Le gocce vivono nello spazio del modello, non sullo schermo. Prima cadevano
 * lungo una fascia fissa attorno al centro: non seguivano la nuvola quando la
 * si girava, non ne rispettavano la larghezza, e da qualunque angolo la si
 * guardasse restavano li'. Cosi' invece ruotano con lei, quelle davanti scorrono
 * piu' di quelle dietro, e sono grandi quanto la loro distanza impone.
 */
private class Drop(
    /** Posizione sotto la nuvola, da -1 a 1 sui due assi orizzontali. */
    val x: Float,
    val z: Float,
    val phase: Float,
    val speed: Float,
    val length: Float,
)

private val DROPS: List<Drop> = List(34) { i ->
    val r = Random(i * 7919 + 13)
    Drop(
        x = r.nextFloat() * 2f - 1f,
        z = r.nextFloat() * 2f - 1f,
        phase = r.nextFloat(),
        speed = 0.85f + r.nextFloat() * 0.5f,
        length = 0.05f + r.nextFloat() * 0.05f,
    )
}

private fun DrawScope.drawRain(
    camera: Camera,
    unit: Float,
    scale: Float,
    wetness: Float,
    confidence: Float,
    progress: Float,
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


    for (i in 0 until count) {
        val drop = DROPS[i]
        val travel = (drop.phase + progress * drop.speed) % 1f
        val y = top + travel * span
        val x = drop.x * spreadX
        val z = drop.z * spreadZ

        camera.place(x, y, z)
        val head = Offset(camera.sx, camera.sy)
        val near = camera.scale
        camera.place(x, y - drop.length * unit, z)
        val tail = Offset(camera.sx, camera.sy)

        // Entra sfumando: una goccia che appare dal nulla a mezz'aria si legge
        // come uno sfarfallio, non come pioggia.
        val alpha = (0.40f + 0.60f * confidence) * (travel / 0.08f).coerceAtMost(1f)
        val stroke = (width * near).coerceAtLeast(1.5f)

        // Dove comincia la cifra, sotto questa goccia. La sagoma arriva in
        // coordinate della propria tela: si sposta nelle nostre.
        val surface = skyline?.topAt(head.x - shift.x)?.let { it + shift.y } ?: Float.NaN

        if (surface.isNaN() || head.y < surface) {
            // Aria libera: cade e basta, smorzandosi con la distanza.
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
        // La coda si ferma un filo sopra il punto d'impatto. Attaccata allo
        // schizzo formava una figura sola - una riga verticale con due
        // diagonali in punta - e quella figura si legge come una freccia, non
        // come acqua che rimbalza.
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
        splash(Offset(head.x, surface), stroke, sunk, colour, alpha)
    }
}

/**
 * Lo schizzo: due schegge che partono ai lati del punto colpito e si aprono.
 *
 * Non una corona tonda vista di taglio, che a questa dimensione sarebbe una
 * riga. E non due segmenti attaccati al punto d'impatto: staccate dal centro si
 * leggono come acqua che rimbalza, unite come una punta di freccia.
 *
 * Piu' invecchiano piu' si allontanano e si abbassano, come se ricadessero.
 */
private fun DrawScope.splash(
    at: Offset,
    stroke: Float,
    age: Float,
    colour: Color,
    alpha: Float,
) {
    val fade = alpha * (1f - age) * 0.9f
    if (fade <= 0.01f) return

    // Nasce gia' aperto e radente. Partendo stretto e ripido, i due segmenti
    // restavano appesi sotto la goccia e insieme a lei formavano una punta di
    // freccia: l'acqua che rimbalza si allarga subito, non parte a coda.
    val gap = stroke * (0.9f + age * 1.3f)
    val reach = stroke * (1.5f + age * 3.2f)
    val lift = stroke * (1.15f - age * 0.8f)
    val tint = lerp(colour, Lightning, 0.30f).copy(alpha = fade)

    drawLine(
        color = tint,
        start = Offset(at.x - gap, at.y),
        end = Offset(at.x - gap - reach, at.y - lift),
        strokeWidth = stroke * 0.60f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = tint,
        start = Offset(at.x + gap, at.y),
        end = Offset(at.x + gap + reach * 0.88f, at.y - lift * 0.85f),
        strokeWidth = stroke * 0.60f,
        cap = StrokeCap.Round,
    )
}

/**
 * La saetta: una spezzata che scende dalla nuvola, piu' una diramazione.
 *
 * Rigenerata a ogni lampo. Sempre la stessa si riconoscerebbe al secondo colpo,
 * e un fulmine che si ripete identico non e' un fulmine.
 */
private class Bolt(val points: FloatArray, val fork: FloatArray) {

    fun isNotEmpty(): Boolean = points.size >= 4

    companion object {
        val EMPTY = Bolt(FloatArray(0), FloatArray(0))

        fun of(random: Random): Bolt {
            val steps = 5
            val startX = (random.nextFloat() - 0.5f) * 0.34f
            val points = FloatArray((steps + 1) * 2)
            var x = startX
            for (k in 0..steps) {
                val y = 0.16f + 0.62f * (k / steps.toFloat())
                points[k * 2] = x
                points[k * 2 + 1] = y
                x += (random.nextFloat() - 0.5f) * 0.20f
            }

            // La diramazione parte da meta' corsa e muore a meta' strada: e' cio'
            // che distingue una saetta da una riga storta.
            val from = steps / 2
            val fork = FloatArray(6)
            var fx = points[from * 2]
            var fy = points[from * 2 + 1]
            val side = if (random.nextBoolean()) 1f else -1f
            for (k in 0 until 3) {
                fork[k * 2] = fx
                fork[k * 2 + 1] = fy
                fx += side * (0.05f + random.nextFloat() * 0.07f)
                fy += 0.09f
            }
            return Bolt(points, fork)
        }
    }
}

private fun DrawScope.drawBolt(
    camera: Camera,
    unit: Float,
    scale: Float,
    bolt: Bolt,
    glare: Float,
) {
    // Davanti al centro della nuvola, cosi' girando la scena la saetta ruota
    // con lei invece di restare appiccicata al vetro.
    val z = -unit * 0.06f * scale

    fun stroke(data: FloatArray, width: Float, colour: Color) {
        if (data.size < 4) return
        var previous: Offset? = null
        for (k in 0 until data.size / 2) {
            camera.place(data[k * 2] * unit, data[k * 2 + 1] * unit, z)
            val here = Offset(camera.sx, camera.sy)
            previous?.let {
                drawLine(
                    color = colour,
                    start = it,
                    end = here,
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }
            previous = here
        }
    }

    // Il cielo che si accende attorno al punto colpito. Senza, la saetta sembra
    // disegnata sopra la scena invece che dentro: e' il bagliore intorno a dire
    // che quella luce viene da li'.
    camera.place(bolt.points[0] * unit, bolt.points[1] * unit, z)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Halo.copy(alpha = 0.30f * glare), Color.Transparent),
            center = Offset(camera.sx, camera.sy),
            radius = unit * 0.62f,
        ),
        radius = unit * 0.62f,
        center = Offset(camera.sx, camera.sy),
    )

    // Tre passate: due aloni sempre piu' stretti e sopra il filo incandescente.
    // Con una riga sola il fulmine sembra un tratto di penna, e su un cielo
    // grigio chiaro un bianco tenue sparisce del tutto: e' il contrasto fra
    // l'azzurro dell'alone e il bianco pieno del nucleo a farlo leggere.
    stroke(bolt.points, unit * 0.075f, Halo.copy(alpha = 0.28f * glare))
    stroke(bolt.fork, unit * 0.050f, Halo.copy(alpha = 0.20f * glare))
    stroke(bolt.points, unit * 0.034f, Glow.copy(alpha = 0.70f * glare))
    stroke(bolt.fork, unit * 0.022f, Glow.copy(alpha = 0.55f * glare))
    stroke(bolt.points, unit * 0.013f, Lightning.copy(alpha = glare))
    stroke(bolt.fork, unit * 0.008f, Lightning.copy(alpha = 0.9f * glare))
}

/** Bianco pieno: e' il nucleo, e un nucleo non ha colore. */
private val Lightning = Color(0xFFFFFFFF)

/** L'azzurro attorno al filo. Un lampo caldo non si e' mai visto. */
private val Glow = Color(0xFFBBD6FF)

private val Halo = Color(0xFF6E9BF0)

/** Famiglie che portano precipitazione, e quindi una nuvola carica. */
internal fun Wmo.Family.isWet(): Boolean =
    this == Wmo.Family.PIOGGIA || this == Wmo.Family.NEVE || this == Wmo.Family.TEMPORALE

private const val FALL_CYCLE_MS = 1400L
