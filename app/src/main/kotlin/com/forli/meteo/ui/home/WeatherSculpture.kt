package com.forli.meteo.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.forli.meteo.data.SkyState
import com.forli.meteo.data.SunClock
import com.forli.meteo.data.Wind
import com.forli.meteo.data.Wmo
import com.forli.meteo.ui.motion.SceneRotation
import com.forli.meteo.ui.motion.TILT_PITCH_DEGREES
import com.forli.meteo.ui.motion.TILT_YAW_DEGREES
import com.forli.meteo.ui.motion.rememberWeatherHaptics
import com.forli.meteo.ui.render3d.Camera
import com.forli.meteo.ui.render3d.SceneContact
import com.forli.meteo.ui.render3d.SphereBrushes
import com.forli.meteo.ui.render3d.hazeMass
import com.forli.meteo.ui.render3d.moon
import com.forli.meteo.ui.render3d.sphere
import com.forli.meteo.ui.render3d.sunRays
import com.forli.meteo.ui.theme.LocalMeteoColors
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sole, luna, nuvole, pioggia, neve e fulmini nello stesso spazio della cifra,
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
    wind: Wind,
    /** Quanto e' fitta la nebbia: nasconde gli astri prima ancora del cielo. */
    fog: Float,
    date: LocalDate,
    rotation: SceneRotation,
    tilt: State<Offset>,
    /** Falso quando la schermata non e' in primo piano: allora niente vibrazione. */
    feelsIt: Boolean,
    /** Dove la cifra offre superficie a quello che cade. */
    contact: SceneContact,
    clock: SceneClock,
    snowfall: Snowfall,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val haptics = rememberWeatherHaptics()

    val family = Wmo.family(weatherCode)
    val falling = family.isWet()
    val snowing = family == Wmo.Family.NEVE
    val storming = family == Wmo.Family.TEMPORALE

    /**
     * Quanto si vede di cio' che cade.
     *
     * I millimetri da soli non bastano: un temporale previsto al sessanta per
     * cento puo' avere zero millimetri in quell'ora esatta, e allora sotto la
     * scritta TEMPORALE non cadeva niente. Se il codice dice che precipita, deve
     * precipitare; i millimetri decidono quanto forte, non se.
     */
    val target = when {
        falling -> maxOf(
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
        targetValue = if (snowing) 0f else target,
        animationSpec = spring(stiffness = 120f),
        label = "pioggia",
    )
    val snowiness by animateFloatAsState(
        targetValue = if (snowing) target else 0f,
        animationSpec = spring(stiffness = 120f),
        label = "neve",
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
        targetValue = if (falling) 1f else 0f,
        animationSpec = spring(stiffness = 110f),
        label = "carica",
    )

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
    // sarebbe un ronzio, e un ronzio non e' pioggia. La neve non si sente
    // affatto: e' esattamente la cosa che di lei colpisce.
    LaunchedEffect(falling, snowing, feelsIt) {
        if (!falling || snowing || !feelsIt) return@LaunchedEffect
        while (true) {
            delay(DRIZZLE_TOUCH_MS)
            haptics.drizzle()
        }
    }

    val phase = remember(date) { MoonPhase.at(date) }

    // Una camera sola e un ordinatore solo, vivi quanto la schermata. Dentro il
    // disegno non si alloca: vedi [Camera.aim] e [DepthOrder].
    val camera = remember { Camera() }
    val order = remember { DepthOrder(maxOf(CLOUD_MASSES.size, DISTANT_MASSES.size)) }
    // Un posto per ogni corpo: il sole, la luna e le masse della nuvola. Il
    // posto e' fisso, quindi leggere il pennello e' un accesso a vettore.
    val brushes = remember { SphereBrushes(CLOUD_MASSES.size + DISTANT_MASSES.size + 2) }

    Canvas(
        modifier.onGloballyPositioned { coordinates ->
            contact.rainOrigin = coordinates.positionInRoot()
        },
    ) {
        val unit = size.minDimension
        camera.aim(
            yawDeg = rotation.yawDeg + tilt.value.x * TILT_YAW_DEGREES,
            pitchDeg = tilt.value.y * TILT_PITCH_DEGREES,
            // Piu' vicina di quella della cifra rispetto alla propria
            // dimensione: la scultura e' un oggetto piccolo tenuto vicino
            // all'occhio, e girandola la prospettiva deve sentirsi.
            distance = unit * EYE,
            // In basso nel proprio riquadro: la scultura deve appoggiarsi alla
            // cifra, non galleggiare sopra un vuoto.
            origin = Offset(size.width / 2f, size.height * 0.74f),
        )
        val glare = flash.value

        // Sotto una nuvola spessa l'astro sparisce del tutto. Con una velatura
        // parziale i raggi del sole sbucavano da dietro un temporale, che e'
        // esattamente il tipo di dettaglio che rovina l'illusione.
        //
        // La nebbia conta quanto le nuvole, e prima non contava affatto: dentro
        // un banco fitto il sole restava un disco arancione a contorno netto,
        // con tanto di raggi, che e' l'esatto contrario di cio' che si vede in
        // pianura a gennaio. Dalla nebbia il sole o non si vede, o si intuisce.
        val clear = (1f - cloudiness * 1.25f - fog * 0.85f).coerceIn(0f, 1f)

        val bodyX = unit * 0.15f
        val bodyY = -unit * 0.10f
        val bodyZ = unit * 0.34f
        val bodyRadius = unit * 0.23f

        val sunAlpha = clear * sky.sunPresence
        if (sunAlpha > 0.01f) {
            sunRays(camera, bodyX, bodyY, bodyZ, bodyRadius, colors.sunCore, sunAlpha * 0.75f, far = true)
            sphere(
                camera, bodyX, bodyY, bodyZ, bodyRadius,
                colors.sunCore, colors.sunShade, sunAlpha,
                brushes = brushes, slot = SLOT_SUN,
            )
            sunRays(camera, bodyX, bodyY, bodyZ, bodyRadius, colors.sunCore, sunAlpha * 0.75f, far = false)
        }

        // La luna e' un corpo, non un velo.
        //
        // Prima la sua opacita' era `clear * moonPresence`, e con qualunque
        // copertura - nuvole o nebbia - diventava un disco semitrasparente
        // attraverso cui si vedeva il cielo: non una luna dietro le nuvole, una
        // luna di garza. La luna non e' mai trasparente. O c'e' abbastanza
        // squarcio per vederla, e allora e' piena e materica, o non c'e' e non
        // si disegna. La rampa fra i due casi e' stretta apposta: serve a non
        // farla lampeggiare quando la copertura oscilla attorno alla soglia,
        // non a tenerla in mezzo.
        val moonAlpha = sky.moonPresence * SunClock.smoothstep(0.06f, 0.22f, clear)
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

        // Quanto e' grande la nuvola, e quanto puo' esserlo senza uscire.
        //
        // La scala che si vorrebbe cresce con la copertura; quella che si puo'
        // avere la detta la larghezza del riquadro. Il calcolo sta in [cloudFit]
        // e vale per **qualunque** rotazione: una scala ricavata dall'ingombro
        // dell'istante cambierebbe mentre si gira, e la nuvola si vedrebbe
        // respirare.
        val wanted = 0.52f + cloudiness * 0.48f
        val scale = minOf(wanted, cloudFit(unit, size.width))

        if (cloudiness > 0.02f) {
            drawClouds(
                camera = camera,
                unit = unit,
                scale = scale,
                cloudiness = cloudiness,
                laden = laden,
                glare = glare,
                clock = clock,
                wind = wind,
                core = colors.cloudCore,
                shade = colors.cloudShade,
                rainCore = colors.rainCloudCore,
                rainShade = colors.rainCloudShade,
                distant = colors.cloudDistant,
                order = order,
                brushes = brushes,
            )
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
                clock = clock,
                wind = wind,
                colour = colors.rain,
                contact = contact,
            )
        }

        if (snowiness > 0.01f) {
            snowfall.draw(
                scope = this,
                camera = camera,
                unit = unit,
                scale = scale,
                amount = snowiness,
                clock = clock,
                wind = wind,
                colour = colors.snow,
                contact = contact,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Nuvole
// ---------------------------------------------------------------------------

/** Una massa della nuvola: posizione nello spazio e raggio, in frazioni di unita'. */
internal class Lump(val x: Float, val y: Float, val z: Float, val radius: Float)

/**
 * Le masse sono sparse anche in profondita', non solo sul piano. Tutte alla
 * stessa distanza la nuvola sarebbe un ritaglio di cartone, e ruotandola si
 * vedrebbe che lo e'.
 */
internal val CLOUD_MASSES = listOf(
    Lump(-0.26f, 0.02f, 0.16f, 0.19f),
    Lump(0.00f, -0.09f, -0.06f, 0.25f),
    Lump(0.26f, 0.03f, 0.12f, 0.20f),
    Lump(-0.11f, 0.10f, -0.19f, 0.18f),
    Lump(0.15f, 0.11f, -0.14f, 0.17f),
)

/**
 * Lo strato dietro: nuvole piu' lontane, piu' basse e piu' scolorite.
 *
 * Una nuvola sola in mezzo al cielo non racconta il tempo, racconta un simbolo
 * del tempo. Quello che manca non e' piu' materia sulla stessa nuvola - sarebbe
 * solo piu' grande - ma **un'altra distanza**: masse piu' indietro, sbiadite
 * dall'aria che ci sta in mezzo e mosse piu' piano dal vento. Girando la scena
 * scorrono meno di quelle davanti, e da quel solo fatto si legge che il cielo ha
 * uno spessore.
 */
internal val DISTANT_MASSES = listOf(
    Lump(-0.33f, -0.34f, 0.28f, 0.22f),
    Lump(-0.06f, -0.40f, 0.38f, 0.20f),
    Lump(0.26f, -0.36f, 0.32f, 0.23f),
    Lump(0.42f, -0.30f, 0.20f, 0.18f),
)

/**
 * Perche' lo strato lontano non e' anche **largo**.
 *
 * L'istinto direbbe di spargerlo, per far sentire che il cielo continua oltre
 * la nuvola principale. Non si puo', e il motivo e' geometrico: la scena gira
 * attorno all'asse verticale, quindi dopo un quarto di giro **la profondita'
 * diventa larghezza**. Una massa messa lontana dietro, a un certo punto della
 * rotazione, si trova lontana di fianco - e li' esce dallo schermo. Misurato:
 * con lo strato sparso fino a 0,74 di scostamento e 0,95 di profondita',
 * l'ingombro peggiore passava da 0,50 a 1,40 unita', e per rientrare avrebbe
 * costretto a rimpicciolire **anche la nuvola vicina** di un terzo.
 *
 * La distanza qui la raccontano la profondita' moderata, il tono smorzato verso
 * il colore del cielo e la parallasse minore. Sono le tre cose che si vedono
 * davvero: la larghezza sarebbe costata la nuvola principale.
 */

/**
 * La scala massima a cui la nuvola resta dentro il riquadro, comunque la si
 * giri.
 *
 * Le nuvole del temporale uscivano dai fianchi dello schermo, e non per un
 * errore di posizione: la scala saliva con la copertura fino a uno, e a uno
 * l'ingombro dipende da quanto e' alto il riquadro (`unit` e' la dimensione
 * minore) mentre lo spazio disponibile dipende da quanto e' largo. Su un
 * riquadro quasi quadrato i due numeri si equivalgono e va bene; su uno piu'
 * alto che largo la nuvola sborda, e sborda **di piu'** proprio col temporale,
 * perche' e' li' che la scala arriva al massimo.
 *
 * Il limite qui e' conservativo di proposito. Per ogni massa si prende il caso
 * peggiore su tutte le rotazioni possibili: distanza dall'asse `hypot(x, z)`,
 * che e' quanto puo' allontanarsi di lato, sommata al proprio raggio, e
 * ingrandita dal guadagno prospettico che avrebbe se quella stessa distanza
 * fosse tutta verso l'osservatore. I due estremi non capitano mai insieme,
 * quindi il limite lascia qualcosa sul tavolo - ma lascia anche la certezza che
 * nessuna rotazione possa farla uscire, che e' cio' che serve.
 */
private fun cloudFit(unit: Float, width: Float): Float {
    val room = width * CLOUD_MARGIN
    val eye = unit * EYE
    var limit = Float.MAX_VALUE
    var i = 0
    while (i < CLOUD_BOUNDS.size) {
        val axis = CLOUD_BOUNDS[i] * unit
        val reach = CLOUD_BOUNDS[i + 1] * unit
        // Da  reach * s * eye / (eye - axis * s) <= room  si ricava:
        val allowed = room * eye / (reach * eye + room * axis)
        if (allowed < limit) limit = allowed
        i += 2
    }
    return limit
}

/**
 * Distanza dall'asse e ingombro di ogni massa, in frazioni di unita', appaiate
 * in un vettore piatto.
 *
 * Calcolate una volta sola. La versione precedente concatenava le due liste
 * dentro `cloudFit`, e `cloudFit` sta nel disegno: una lista nuova e cinque
 * radici quadrate a ogni fotogramma, per un risultato che dipende solo da due
 * numeri che nel frattempo non erano cambiati.
 */
private val CLOUD_BOUNDS: FloatArray = (CLOUD_MASSES + DISTANT_MASSES)
    .flatMap { lump ->
        val axis = hypot(lump.x, lump.z)
        listOf(axis, axis + lump.radius)
    }
    .toFloatArray()

private fun DrawScope.drawClouds(
    camera: Camera,
    unit: Float,
    scale: Float,
    cloudiness: Float,
    laden: Float,
    glare: Float,
    clock: SceneClock,
    wind: Wind,
    core: Color,
    shade: Color,
    rainCore: Color,
    rainShade: Color,
    distant: Color,
    order: DepthOrder,
    brushes: SphereBrushes,
) {
    val presence = ((cloudiness - 0.06f) / 0.16f).coerceIn(0f, 1f)
    val masses = (2 + (cloudiness * 3f).roundToInt()).coerceIn(2, CLOUD_MASSES.size)

    // Il lampo illumina la nuvola da dentro: se restasse dello stesso grigio, la
    // saetta sembrerebbe disegnata davanti a un fondale.
    val lit = lerp(lerp(core, rainCore, laden), Lightning, glare * 0.55f)
    val dark = lerp(lerp(shade, rainShade, laden), Lightning, glare * 0.40f)

    // Il vento porta la nuvola, e il respiro la tiene viva anche con aria ferma.
    // Due ritmi incommensurabili fra loro: con uno solo si riconoscerebbe il
    // periodo, e una nuvola che pulsa a tempo non e' una nuvola.
    val t = clock.seconds.toFloat()
    val breath = sin(t * 0.14f) * 0.010f + sin(t * 0.23f + 1.7f) * 0.006f
    val push = wind.push * 0.055f
    val drift = (breath + push) * unit

    // Lo strato lontano per primo: sta dietro, e senza buffer di profondita' e'
    // l'ordine di disegno a dirlo. Compare solo quando il cielo si carica
    // davvero: con una velatura leggera ci sarebbe un secondo strato di nuvole
    // sopra una giornata quasi serena.
    val depth = ((cloudiness - 0.35f) / 0.45f).coerceIn(0f, 1f)
    if (depth > 0.01f) {
        val far = (1 + (depth * 4f).roundToInt()).coerceIn(1, DISTANT_MASSES.size)
        order.sortFarToNear(DISTANT_MASSES, far, camera, unit * scale)
        for (i in 0 until far) {
            val lump = DISTANT_MASSES[order[i]]
            hazeMass(
                camera = camera,
                // Piu' lontano, meno lo sposta il vento: e' il modo in cui la
                // parallasse racconta la distanza senza dichiararla.
                x = lump.x * unit * scale + drift * 0.35f,
                y = lump.y * unit * scale,
                z = lump.z * unit * scale,
                radius = lump.radius * unit * scale,
                colour = distant,
                alpha = presence * depth * 0.50f,
                brushes = brushes,
                slot = SLOT_DISTANT + order[i],
            )
        }
    }

    // Dal fondo verso l'osservatore, e in coordinate di vista: senza un buffer
    // di profondita' e' l'ordine di disegno a decidere chi sta davanti, e
    // ordinandole per la posizione nel modello bastava girare la scena di mezzo
    // giro perche' si scavalcassero al contrario.
    order.sortFarToNear(CLOUD_MASSES, masses, camera, unit * scale)
    for (i in 0 until masses) {
        val lump = CLOUD_MASSES[order[i]]
        sphere(
            camera = camera,
            x = lump.x * unit * scale + drift,
            y = lump.y * unit * scale,
            z = lump.z * unit * scale,
            radius = lump.radius * unit * scale,
            light = lit,
            dark = dark,
            alpha = presence,
            brushes = brushes,
            // Il posto segue la **massa**, non l'ordine di disegno: quello
            // cambia ruotando, e un pennello che cambia posto a ogni mezzo giro
            // verrebbe ricostruito ogni volta.
            slot = SLOT_CLOUDS + order[i],
        )
    }
}

/**
 * L'ordine dal fondo verso l'osservatore, senza allocare niente.
 *
 * Serviva `take(n).sortedByDescending { ... }`, e in un disegno quelle due
 * chiamate costano piu' di quanto sembri: la prima costruisce una lista, la
 * seconda ne costruisce un'altra **e incapsula ogni chiave in un oggetto**,
 * perche' il confronto passa da `Comparable`. Cinque masse per due strati fanno
 * quattro liste e dieci float incapsulati a ogni fotogramma, cioe' quasi mille
 * oggetti al secondo per riordinare cinque cose.
 *
 * Qui gli indici stanno in un vettore di interi riusato e la profondita' in uno
 * di float, e l'ordinamento e' per inserzione: su cinque elementi e' piu'
 * veloce di qualunque cosa piu' furba, e soprattutto e' **stabile**, il che
 * evita che due masse alla stessa profondita' si scambino di posto da un
 * fotogramma all'altro facendo sfarfallare la nuvola.
 */
internal class DepthOrder(capacity: Int) {

    private val index = IntArray(capacity)
    private val depth = FloatArray(capacity)

    operator fun get(position: Int): Int = index[position]

    fun sortFarToNear(masses: List<Lump>, count: Int, camera: Camera, scale: Float) {
        for (i in 0 until count) {
            val lump = masses[i]
            camera.place(lump.x * scale, lump.y * scale, lump.z * scale)
            index[i] = i
            // In coordinate di **vista**, non del modello: ordinare per la
            // posizione nel modello bastava a far scavalcare le masse al
            // contrario dopo mezzo giro.
            depth[i] = camera.vz
        }
        for (i in 1 until count) {
            val keyIndex = index[i]
            val keyDepth = depth[i]
            var j = i - 1
            while (j >= 0 && depth[j] < keyDepth) {
                index[j + 1] = index[j]
                depth[j + 1] = depth[j]
                j--
            }
            index[j + 1] = keyIndex
            depth[j + 1] = keyDepth
        }
    }
}

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

// ---------------------------------------------------------------------------
// Fulmini
// ---------------------------------------------------------------------------

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
            // Per la stessa scala della nuvola. Senza, la saetta restava della
            // misura di prima mentre la nuvola si stringeva per rientrare nel
            // riquadro: un fulmine piu' largo della nuvola da cui esce.
            camera.place(data[k * 2] * unit * scale, data[k * 2 + 1] * unit * scale, z)
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
    // che quella luce viene da li'. Il raggio segue la scala della nuvola,
    // altrimenti col temporale - dove la nuvola e' gia' al massimo - l'alone
    // sarebbe l'ultima cosa a sbordare dopo che le nuvole hanno smesso.
    val halo = unit * 0.50f * scale
    camera.place(bolt.points[0] * unit * scale, bolt.points[1] * unit * scale, z)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Halo.copy(alpha = 0.30f * glare), Color.Transparent),
            center = Offset(camera.sx, camera.sy),
            radius = halo,
        ),
        radius = halo,
        center = Offset(camera.sx, camera.sy),
    )

    // Tre passate: due aloni sempre piu' stretti e sopra il filo incandescente.
    // Con una riga sola il fulmine sembra un tratto di penna, e su un cielo
    // grigio chiaro un bianco tenue sparisce del tutto: e' il contrasto fra
    // l'azzurro dell'alone e il bianco pieno del nucleo a farlo leggere.
    stroke(bolt.points, unit * 0.075f * scale, Halo.copy(alpha = 0.28f * glare))
    stroke(bolt.fork, unit * 0.050f * scale, Halo.copy(alpha = 0.20f * glare))
    stroke(bolt.points, unit * 0.034f * scale, Glow.copy(alpha = 0.70f * glare))
    stroke(bolt.fork, unit * 0.022f * scale, Glow.copy(alpha = 0.55f * glare))
    stroke(bolt.points, unit * 0.013f * scale, Lightning.copy(alpha = glare))
    stroke(bolt.fork, unit * 0.008f * scale, Lightning.copy(alpha = 0.9f * glare))
}

/** Bianco pieno: e' il nucleo, e un nucleo non ha colore. */
private val Lightning = Color(0xFFFFFFFF)

/** L'azzurro attorno al filo. Un lampo caldo non si e' mai visto. */
private val Glow = Color(0xFFBBD6FF)

private val Halo = Color(0xFF6E9BF0)

/** Famiglie che portano precipitazione, e quindi una nuvola carica. */
internal fun Wmo.Family.isWet(): Boolean =
    this == Wmo.Family.PIOGGIA || this == Wmo.Family.NEVE || this == Wmo.Family.TEMPORALE

/** I posti fissi dei pennelli. Il sole, e poi una massa di nuvola per posto. */
private const val SLOT_SUN = 0
private const val SLOT_CLOUDS = 1
private val SLOT_DISTANT = SLOT_CLOUDS + CLOUD_MASSES.size

/** Distanza dell'occhio, in multipli della dimensione del riquadro. */
private const val EYE = 2.1f

/** Quanta parte della larghezza puo' occupare la nuvola, per meta'. */
private const val CLOUD_MARGIN = 0.46f

private const val DRIZZLE_TOUCH_MS = 1400L
