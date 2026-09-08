package io.github.noximiliencoxen.caelum.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.PrecipKind
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.data.SunClock
import io.github.noximiliencoxen.caelum.data.isWet
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.motion.SceneRotation
import io.github.noximiliencoxen.caelum.ui.motion.rotatesScene
import io.github.noximiliencoxen.caelum.ui.render3d.Camera
import io.github.noximiliencoxen.caelum.ui.render3d.Light
import io.github.noximiliencoxen.caelum.ui.render3d.addFacet
import io.github.noximiliencoxen.caelum.ui.render3d.moon
import io.github.noximiliencoxen.caelum.ui.render3d.sphere
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.skyColors
import io.github.noximiliencoxen.caelum.ui.home.MoonPhase
import java.time.LocalDate

/**
 * Una finestra su un'altra ora.
 *
 * **Prende il posto della vasca graduata**, e la ragione sta nelle misure prima
 * che nel gusto: il riquadro dell'eroe e' 283 punti per 600, e la vasca - che si
 * dimensionava sul lato corto - ne usava centotrentadue in altezza. Due terzi
 * dello spazio erano vuoti, e si vedeva. Una feritoia verticale e' la forma di
 * una finestra molto piu' che di un barattolo.
 *
 * Dentro si guarda il **cielo dell'ora scelta**, non quello di adesso: scorrendo
 * la fascia delle ventiquattro ore qui sotto, la giornata passa - l'alba, il
 * mezzogiorno, il temporale, la notte. E' quello che rende la scheda una cosa da
 * toccare invece che da leggere.
 *
 * **Il blocco e' l'unica cosa che e' 3D per davvero.** Un parallelepipedo fresato
 * con un foro rettangolare: girandolo col dito si scopre lo strombo, cioe' la
 * parete di dentro del foro, esattamente come **si guarda di lato attraverso una
 * finestra**. E' il vocabolario che il progetto si e' scritto - plastica bianca
 * opaca fresata, smussi netti - e da' finalmente un mestiere alla rotazione, che
 * sulla vasca era solo un giro attorno a un barattolo.
 *
 * **E lo strombo e' anche la parallasse.** Girando, un montante copre una parte
 * di cio' che si vede e il cielo scivola rispetto all'apertura: non c'e' niente
 * da simulare, lo fa la geometria. Va detto perche' e' controintuitivo - qui la
 * camera **ruota l'oggetto**, non sposta la testa di chi guarda, e i due
 * movimenti non sono lo stesso.
 */
@Composable
internal fun RainWindow(
    /** L'ora mostrata: da lei vengono il cielo, il sole e il tempo che fa. */
    hour: HourForecast?,
    /** Il giorno mostrato: porta alba e tramonto, che sono il posto dentro il conto. */
    day: DayForecast?,
    /** Il codice imposto dagli agganci di verifica, che vince su quello vero. */
    forcedCode: Int?,
    rotation: SceneRotation,
    tilt: State<Offset>,
    /** Falso quando la scheda e' composta ma non la guarda nessuno. */
    alive: Boolean,
    modifier: Modifier = Modifier,
) {
    val room = LocalMeteoColors.current
    val code = forcedCode ?: hour?.weatherCode
    val wet = Wmo.family(code).isWet()
    val kind = Wmo.precipKind(code)
    val wetness = wetnessOf(wet, hour?.precipitation)

    // **Il cielo dell'ora si calcola, non si prende dal tema.**
    //
    // `LocalMeteoColors` porta il cielo di **adesso**, ed e' quello giusto per
    // la scheda che ci sta attorno. Qui dentro serve l'ora scelta, e le tinte si
    // passano **come valore**: ri-fornire il local sarebbe la strada corta e la
    // trappola lunga, perche' e' `staticCompositionLocalOf` e ogni fotogramma
    // dello scorrimento invaliderebbe tutto il sotto-albero.
    val outside = remember(hour?.time, day?.sunrise, day?.sunset, code) {
        val moment = hour?.time
        val sky = if (moment == null) {
            SkyState.Giorno
        } else {
            SunClock.skyAt(moment, day?.sunrise, day?.sunset, hour.isDay)
        }
        skyColors(sky, Wmo.cloudiness(code))
    }
    val journey = remember(hour?.time, day?.sunrise, day?.sunset) {
        val moment = hour?.time ?: return@remember 0.5f
        SunClock.journey(moment, day?.sunrise, day?.sunset)
    }
    val altitude = remember(hour?.time, day?.sunrise, day?.sunset, hour?.isDay) {
        val moment = hour?.time ?: return@remember 0.62f
        SunClock.altitude(moment, day?.sunrise, day?.sunset, hour.isDay)
    }
    val phase = remember(day?.date) { MoonPhase.at(day?.date ?: LocalDate.now()) }

    // ── L'orologio della scena ──────────────────────────────────────────────
    //
    // Esplicito, mai `rememberInfiniteTransition`: su questa app e' gia' stato
    // misurato **non animare affatto** - le gocce sembravano cadere e invece
    // l'app disegnava zero fotogrammi.
    //
    // Gira mentre la scheda si guarda **e c'e' qualcosa che si muove**. Oggi
    // quel qualcosa e' solo il tempo: con il sereno non c'e' niente da animare,
    // e un orologio che batte per non muovere niente e' batteria buttata. Quando
    // arrivera' la strada con la gente sotto gli ombrelli la condizione si
    // allarghera' a loro, ed e' li' che questa scheda diventera' l'eccezione
    // dichiarata alla regola dei zero fotogrammi da fermo.
    val fall = remember { mutableFloatStateOf(0f) }
    val running = alive && wetness > 0f
    LaunchedEffect(running) {
        if (!running) {
            fall.floatValue = 0f
            return@LaunchedEffect
        }
        var origin = 0L
        while (true) {
            withFrameNanos { now ->
                if (origin == 0L) origin = now
                val elapsed = (now - origin) / 1_000_000L
                fall.floatValue = (elapsed % SCENE_CYCLE_MS) / SCENE_CYCLE_MS.toFloat()
            }
        }
    }

    val spoken = remember(hour?.time, code) {
        val ora = hour?.time?.hour?.let { "alle $it" } ?: "adesso"
        "Finestra sul cielo $ora: ${Wmo.condition(code).lowercase()}"
    }

    Box(modifier = modifier.rotatesScene(rotation), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = spoken },
        ) {
            // Giro e inclinazione si leggono **qui dentro**: girare deve
            // ridipingere, non ricomporre.
            val camera = Camera(
                yawDeg = rotation.yawDeg,
                pitchDeg = tilt.value.y * WINDOW_TILT,
                distance = maxOf(size.width, size.height) * 2.2f,
                origin = Offset(size.width / 2f, size.height / 2f),
            )
            drawWindow(
                camera = camera,
                unit = size.width,
                // **Si dimensiona sui due lati, non sul lato corto.** Ogni altro
                // corpo di questa app usa `min(larghezza, altezza)`, e va bene
                // per una sfera o per una cifra, che sono larghe quanto alte.
                // Una finestra no: qui il riquadro e' alto piu' del doppio della
                // sua larghezza, e prendere il lato corto e' esattamente
                // l'errore che lasciava la vasca in mezzo al vuoto.
                halfWide = size.width * BLOCK_WIDE,
                halfTall = size.height * BLOCK_TALL,
                zenith = outside.skyZenith,
                horizon = outside.skyHorizon,
                sunCore = outside.sunCore,
                sunShade = outside.sunShade,
                moonCore = outside.moonCore,
                moonShade = outside.moonShade,
                face = room.numberFace,
                sideNear = room.numberSideNear,
                sideFar = room.numberSideFar,
                chamfer = room.numberChamfer,
                journey = journey,
                altitude = altitude,
                phase = phase,
                night = altitude < 0f,
                progress = fall.floatValue,
                wetness = wetness,
                kind = kind,
                rain = room.rain,
                flake = room.cloudCore,
            )
        }
    }
}

/**
 * Il blocco, il foro, e cio' che si vede attraverso.
 *
 * L'ordine non e' negoziabile: prima il cielo, ritagliato sull'apertura, poi le
 * pareti che lo coprono dove il muro e' spesso, poi la faccia davanti. Le facce
 * si scartano con [Camera.facesViewer] invece che a occhio, perche' con la
 * prospettiva il segno di `z` non basta - la direzione di vista cambia da punto
 * a punto.
 */
private fun DrawScope.drawWindow(
    camera: Camera,
    unit: Float,
    halfWide: Float,
    halfTall: Float,
    zenith: Color,
    horizon: Color,
    sunCore: Color,
    sunShade: Color,
    moonCore: Color,
    moonShade: Color,
    face: Color,
    sideNear: Color,
    sideFar: Color,
    chamfer: Color,
    journey: Float,
    altitude: Float,
    phase: Float,
    night: Boolean,
    progress: Float,
    wetness: Float,
    kind: PrecipKind,
    rain: Color,
    flake: Color,
) {
    val depth = halfWide * BLOCK_DEPTH
    val openWide = halfWide * OPENING_WIDE
    val openTall = halfTall * OPENING_TALL

    /** Un vertice del blocco: `sx`/`sy` gia' proiettati. */
    fun at(x: Float, y: Float, z: Float): Offset {
        camera.place(x, y, z)
        return Offset(camera.sx, camera.sy)
    }

    // Il foro visto dalla stanza, cioe' il limite di tutto cio' che si puo'
    // vedere. Il cielo si ritaglia qui.
    val nearHole = listOf(
        at(-openWide, -openTall, -depth),
        at(openWide, -openTall, -depth),
        at(openWide, openTall, -depth),
        at(-openWide, openTall, -depth),
    )
    val holePath = Path().apply {
        moveTo(nearHole[0].x, nearHole[0].y)
        for (i in 1 until nearHole.size) lineTo(nearHole[i].x, nearHole[i].y)
        close()
    }

    // ── Il cielo dell'ora, attraverso il foro ───────────────────────────────
    clipPath(holePath) {
        val bounds = holePath.getBounds()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(zenith, horizon),
                startY = bounds.top,
                endY = bounds.bottom,
            ),
            topLeft = Offset(bounds.left, bounds.top),
            size = Size(bounds.width, bounds.height),
        )
        drawAstro(
            camera = camera,
            unit = unit,
            openWide = openWide,
            openTall = openTall,
            depth = depth,
            journey = journey,
            altitude = altitude,
            phase = phase,
            night = night,
            sunCore = sunCore,
            sunShade = sunShade,
            moonCore = moonCore,
            moonShade = moonShade,
        )
        drawBehindGlass(
            bounds = Size(bounds.width, bounds.height),
            origin = Offset(bounds.left, bounds.top),
            progress = progress,
            wetness = wetness,
            kind = kind,
            colour = if (kind == PrecipKind.SNOW) flake else rain,
        )
    }

    // ── Lo strombo: le pareti dentro il foro ────────────────────────────────
    //
    // Sono quelle che, girando, coprono un pezzo di cielo da una parte e lo
    // scoprono dall'altra. **La parallasse e' questa**, e non c'e' niente da
    // simulare: la fa la geometria del muro spesso.
    // I quattro lati del foro: sinistro, destro, cima, davanzale.
    for (side in REVEALS) {
        val nx = side[0]
        val ny = side[1]
        // La normale guarda **dentro** il foro, ed e' quella che decide se
        // questa parete si vede: `facesViewer` e non il segno di `z`, perche'
        // con la prospettiva la direzione di vista cambia da punto a punto.
        camera.normal(-nx, -ny, 0f)
        camera.place(nx * openWide, ny * openTall, 0f)
        if (!camera.facesViewer()) continue
        val lit = camera.lambert(Light.Standard)

        // Gli spigoli del lato: se la parete e' verticale corrono in altezza, se
        // e' orizzontale corrono in larghezza.
        val x0 = if (nx != 0f) nx * openWide else -openWide
        val x1 = if (nx != 0f) nx * openWide else openWide
        val y0 = if (ny != 0f) ny * openTall else -openTall
        val y1 = if (ny != 0f) ny * openTall else openTall

        drawPath(
            Path().apply {
                addFacet(
                    at(x0, y0, -depth),
                    at(x1, y1, -depth),
                    at(x1, y1, depth),
                    at(x0, y0, depth),
                )
            },
            color = lerp(sideFar, sideNear, lit),
        )
    }

    // ── Il fianco del blocco, quando girando si scopre ──────────────────────
    for (s in intArrayOf(-1, 1)) {
        camera.normal(s.toFloat(), 0f, 0f)
        camera.place(s * halfWide, 0f, 0f)
        if (!camera.facesViewer()) continue
        val lit = camera.lambert(Light.Standard)
        drawPath(
            Path().apply {
                addFacet(
                    at(s * halfWide, -halfTall, -depth),
                    at(s * halfWide, -halfTall, depth),
                    at(s * halfWide, halfTall, depth),
                    at(s * halfWide, halfTall, -depth),
                )
            },
            color = lerp(sideFar, sideNear, lit),
        )
    }

    // ── La faccia davanti: quattro fasce attorno al foro ────────────────────
    //
    // Quattro e non un anello con un buco: un `Path` con due contorni chiederebbe
    // di nuovo di ragionare sull'avvolgimento, ed e' la cosa che qui e' gia'
    // costata un difetto spedito.
    camera.normal(0f, 0f, -1f)
    camera.place(0f, 0f, -depth)
    val frontLit = camera.lambert(Light.Standard)
    val frontTone = lerp(face, sideNear, 1f - frontLit)
    val bands = listOf(
        floatArrayOf(-halfWide, -halfTall, halfWide, -openTall),
        floatArrayOf(-halfWide, openTall, halfWide, halfTall),
        floatArrayOf(-halfWide, -openTall, -openWide, openTall),
        floatArrayOf(openWide, -openTall, halfWide, openTall),
    )
    for (b in bands) {
        drawPath(
            Path().apply {
                addFacet(
                    at(b[0], b[1], -depth),
                    at(b[2], b[1], -depth),
                    at(b[2], b[3], -depth),
                    at(b[0], b[3], -depth),
                )
            },
            color = frontTone,
        )
    }

    // ── Le gocce **sul** vetro ──────────────────────────────────────────────
    //
    // Dopo lo strombo e prima della cornice: la lastra sta a filo della faccia
    // davanti, quindi nessuna parete la copre, ma la cornice si'.
    if (kind != PrecipKind.SNOW) {
        clipPath(holePath) {
            val b = holePath.getBounds()
            drawOnGlass(
                bounds = Size(b.width, b.height),
                origin = Offset(b.left, b.top),
                progress = progress,
                wetness = wetness,
                colour = rain,
            )
        }
    }

    // Lo smusso attorno al foro: e' quello che la fa sembrare fresata invece che
    // ritagliata con le forbici.
    // Scuro e non chiaro: `numberChamfer` e' quasi bianco come la faccia, e uno
    // smusso bianco su bianco non si vede. Un solco lo si legge perche' e'
    // **in ombra**, non perche' e' piu' chiaro.
    drawPath(
        holePath,
        color = lerp(sideFar, chamfer, 0.35f).copy(alpha = 0.5f),
        style = Stroke(width = unit * CHAMFER),
    )
}

/**
 * Il sole o la luna, dietro il foro.
 *
 * Il posto lo dicono le due grandezze che il progetto usa gia' per la scultura:
 * [journey] da che parte sta andando - senza, alle otto e alle sedici starebbe
 * nello stesso punto, perche' l'altezza vale lo stesso numero - e [altitude]
 * quanto e' alto.
 */
private fun DrawScope.drawAstro(
    camera: Camera,
    unit: Float,
    openWide: Float,
    openTall: Float,
    depth: Float,
    journey: Float,
    altitude: Float,
    phase: Float,
    night: Boolean,
    sunCore: Color,
    sunShade: Color,
    moonCore: Color,
    moonShade: Color,
) {
    val x = (journey * 2f - 1f) * openWide * ASTRO_SPAN
    val y = -altitude.coerceIn(-1f, 1f) * openTall * ASTRO_RISE
    val z = depth * ASTRO_DEPTH
    val radius = unit * ASTRO_RADIUS

    if (night) {
        moon(
            camera = camera,
            x = x, y = y, z = z,
            radius = radius,
            phase = phase,
            light = moonCore,
            dark = moonShade,
            alpha = 1f,
            marks = emptyList(),
        )
    } else {
        sphere(camera, x, y, z, radius, sunCore, sunShade)
    }
}

/** Quanto del riquadro prende il blocco, per lato. */
/**
 * Guardato in uno scatto: con 0,42 e 0,34 il blocco riempiva poco piu' di meta'
 * del riquadro, e restava un oggetto piccolo in mezzo al vuoto - cioe' il
 * difetto per cui la vasca e' uscita di scena. Girando **si stringe** invece di
 * allargarsi (la faccia si accorcia piu' di quanto il fianco si scopra), quindi
 * crescere non costa margine.
 */
private const val BLOCK_WIDE = 0.46f
private const val BLOCK_TALL = 0.43f

/** Quanto e' spesso il muro, in frazione della sua mezza larghezza. */
private const val BLOCK_DEPTH = 0.17f

/** Quanto del blocco e' foro. Il resto e' la cornice. */
private const val OPENING_WIDE = 0.76f
private const val OPENING_TALL = 0.86f

/** Lo smusso attorno al foro, in frazione della larghezza del riquadro. */
private const val CHAMFER = 0.008f

/** Il giro completo delle gocce, in millisecondi. */
private const val SCENE_CYCLE_MS = 1400L

/** Quanto l'inclinazione del telefono piega la scena, in gradi. */
private const val WINDOW_TILT = 4f

/** Dove sta l'astro dentro il foro, e quanto e' grande. */
private const val ASTRO_SPAN = 0.62f
private const val ASTRO_RISE = 0.58f
private const val ASTRO_DEPTH = 6f
private const val ASTRO_RADIUS = 0.085f

/**
 * Da che parte guarda ciascuna delle quattro pareti del foro.
 *
 * Una tabella e non quattro chiamate: e' lo stesso disegno quattro volte, e
 * scriverlo quattro volte e' quattro posti in cui sbagliarlo una volta sola.
 */
private val REVEALS = listOf(
    floatArrayOf(-1f, 0f),
    floatArrayOf(1f, 0f),
    floatArrayOf(0f, -1f),
    floatArrayOf(0f, 1f),
)
