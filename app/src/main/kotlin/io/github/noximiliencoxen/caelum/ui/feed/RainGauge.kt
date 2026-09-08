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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.PrecipKind
import io.github.noximiliencoxen.caelum.ui.asCentimetres
import io.github.noximiliencoxen.caelum.ui.asMillimetres
import io.github.noximiliencoxen.caelum.ui.motion.SceneRotation
import io.github.noximiliencoxen.caelum.ui.motion.rotatesScene
import io.github.noximiliencoxen.caelum.ui.render3d.Camera
import io.github.noximiliencoxen.caelum.ui.render3d.DROPS
import io.github.noximiliencoxen.caelum.ui.render3d.FALL_CYCLE_MS
import io.github.noximiliencoxen.caelum.ui.render3d.Light
import io.github.noximiliencoxen.caelum.ui.render3d.SPLASH_LIFE
import io.github.noximiliencoxen.caelum.ui.render3d.microSplashRing
import io.github.noximiliencoxen.caelum.ui.render3d.splash
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.MeteoType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * La vasca graduata: quanta acqua ha portato la giornata, con la sua scala.
 *
 * **Prende il posto della cifra**, e non e' un vezzo: uno zero alto mezzo
 * schermo era l'oggetto piu' grande della schermata nella maggior parte dei
 * giorni, e un numero senza scala non dice niente - quattro e quaranta sono due
 * cifre, non due quantita'. Un livello dentro un recipiente graduato le
 * distingue a colpo d'occhio, e a zero e' un fondo asciutto, che e' una risposta
 * onesta invece di un simbolo.
 *
 * **Un prisma a sei facce, non un cilindro**, per tre ragioni che vanno insieme.
 * La prima e' che la "geometria a tubo" sta sull'elenco delle cose da evitare di
 * questo progetto, che punta alla plastica bianca opaca fresata con smussi
 * netti. La seconda conta di piu': **la sagoma di un cilindro non cambia
 * girando**, quindi il giro del dito sembrerebbe non fare niente. La terza e'
 * che sei facce piatte danno sei toni di mezzo-Lambert che si scambiano mentre
 * si gira, e cosi' la rotazione si legge prima ancora che la sagoma cambi - che
 * e' l'argomento gia' scritto sulla luce di `Camera`.
 *
 * E' fatta della **stessa plastica della cifra** che sostituisce
 * (`numberFace`, `numberSideNear`, `numberSideFar`, `numberChamfer`): e' per
 * questo che appartiene a questa scheda invece di sembrarci appoggiata sopra.
 *
 * **Il livello e' il giorno, le gocce sono l'ora.** Il numero della scheda e' un
 * totale giornaliero, ma dentro la vasca piove quando l'**ora mostrata** e'
 * bagnata: cosi' la stessa scena dice due cose vere invece di mediarle. Non e'
 * un'incoerenza, ed e' scritto qui perche' letto in fretta lo sembra.
 */
@Composable
internal fun RainGauge(
    day: DayForecast,
    /** Se all'ora mostrata piove davvero. Lo decide il codice WMO, non i millimetri. */
    wet: Boolean,
    /** Quanti millimetri in quell'ora: decidono **quante** gocce, non se. */
    hourMm: Double?,
    rotation: SceneRotation,
    tilt: State<Offset>,
    accent: Color,
    /** Falso quando la scheda e' composta ma fuori vista: allora l'orologio non batte. */
    alive: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val kind = remember(day) { precipKindOf(day) }
    val snowy = kind.isSnowy()
    val top = if (snowy) GAUGE_TOP_CM else GAUGE_TOP_MM
    val amount = remember(day, snowy) {
        (if (snowy) day.snowfallSum else day.precipitationSum) ?: 0.0
    }
    val fill = gaugeFill(amount, top)
    val overflowing = gaugeOverflowing(amount, top)

    val measurer = rememberTextMeasurer(cacheSize = 0)
    // `asMillimetres`/`asCentimetres` e non un `String.format` scritto qui: le
    // unita' si scrivono in un posto solo, e quel file porta gia' la lezione su
    // `format` che, chiamato come membro, restituiva il numero meno uno.
    val reading = remember(amount, snowy) {
        if (snowy) amount.asCentimetres() else amount.asMillimetres()
    }
    val spoken = remember(amount, snowy, kind, top) {
        val unit = if (snowy) "centimetri" else "millimetri"
        "Vasca graduata: ${amount.roundToInt()} $unit su una scala che arriva a " +
            "${top.roundToInt()} $unit, ${kind.label.lowercase()}"
    }

    // ── L'orologio delle gocce ──────────────────────────────────────────────
    //
    // Esplicito, mai `rememberInfiniteTransition`: misurato su questa app, con
    // la pioggia accesa e nessun dito sullo schermo non animava affatto -
    // l'app disegnava **zero** fotogrammi e le gocce sembravano cadere ferme.
    //
    // Gira **solo mentre piove davvero e solo mentre la scheda si guarda**. La
    // seconda meta' non e' un vezzo: il carosello tiene composta anche la
    // scheda accanto, e `withFrameNanos` dentro una finestra visibile continua
    // a battere anche per una pagina fuori vista. Senza, stando sull'aria
    // pioverebbe in un recipiente che non si vede.
    //
    // A giornata asciutta e ora asciutta l'effetto esce subito, `fall` resta a
    // zero, e da fermo l'app disegna zero fotogrammi. E' il caso piu' comune, e
    // dev'essere il piu' leggero.
    val fall = remember { mutableFloatStateOf(0f) }
    val running = wet && alive
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
                fall.floatValue = (elapsed % FALL_CYCLE_MS) / FALL_CYCLE_MS.toFloat()
            }
        }
    }

    // Quanto forte piove: **i millimetri decidono quante gocce, non se**. Un
    // temporale all'ottanta per cento puo' avere zero millimetri in quell'ora, e
    // sotto la scritta TEMPORALE non deve smettere di piovere. Stessa forma
    // della scultura, di proposito: un'ora piovosa dev'essere piovosa allo
    // stesso modo sulle due schede.
    val wetness = if (!wet) 0f else ((hourMm ?: 0.0).toFloat() / 6f).coerceIn(0.30f, 1f)

    val liquid = remember(accent, kind) {
        when (kind) {
            PrecipKind.SNOW -> Color.White
            // Misto: fra l'acqua e il bianco, secondo quanto la neve pesa sul
            // totale. E' letteralmente cio' che quel giorno ha fatto.
            PrecipKind.MIXED -> lerp(accent, Color.White, 0.5f)
            else -> accent
        }
    }

    Box(modifier = modifier.rotatesScene(rotation), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = spoken },
        ) {
            // Giro, inclinazione e caduta si leggono **qui dentro**, non in
            // composizione: girare deve ridipingere, non ricomporre.
            val unit = minOf(size.width, size.height)
            val camera = Camera(
                yawDeg = rotation.yawDeg,
                // Un'inclinazione a riposo che la luna non ha bisogno di avere:
                // una sfera non ha un dentro, questa si'. Guardata di taglio,
                // l'apertura e' una riga e l'acqua non si vede. Il telefono
                // aggiunge la sua, non la sostituisce.
                pitchDeg = GAUGE_PITCH_REST + tilt.value.y * GAUGE_TILT,
                distance = unit * 2.7f,
                origin = Offset(size.width / 2f, size.height / 2f),
            )
            drawGauge(
                camera = camera,
                unit = unit,
                fill = fill,
                overflowing = overflowing,
                snowy = snowy,
                liquid = liquid,
                face = colors.numberFace,
                sideNear = colors.numberSideNear,
                sideFar = colors.numberSideFar,
                chamfer = colors.numberChamfer,
                ink = colors.label,
                progress = fall.floatValue,
                wetness = wetness,
                measurer = measurer,
                tickStyle = TextStyle(color = colors.label, fontSize = TICK_SP),
                readingStyle = MeteoType.metric.copy(color = colors.text),
                reading = reading,
                top = top,
            )
        }
    }
}

/**
 * Disegna la vasca, in un ordine che non e' negoziabile.
 *
 * Dietro prima e davanti dopo, con le facce ordinate per profondita' **in
 * coordinate di vista** e non per la loro posizione nel modello: ordinarle nel
 * modello basta a farle scavalcare al contrario dopo mezzo giro, ed e' una
 * trappola che questo progetto ha gia' pagato sulle masse della nuvola.
 */
private fun DrawScope.drawGauge(
    camera: Camera,
    unit: Float,
    fill: Float,
    overflowing: Boolean,
    snowy: Boolean,
    liquid: Color,
    face: Color,
    sideNear: Color,
    sideFar: Color,
    chamfer: Color,
    ink: Color,
    progress: Float,
    wetness: Float,
    measurer: TextMeasurer,
    tickStyle: TextStyle,
    readingStyle: TextStyle,
    reading: String,
    top: Double,
) {
    val radius = unit * GAUGE_RADIUS
    val half = radius * GAUGE_TALL / 2f
    val floorY = half
    val rimY = -half
    val waterY = floorY - (floorY - rimY) * fill

    fun corner(k: Int, y: Float): Offset {
        val a = (k * 60f - 30f) * DEG
        camera.place(radius * sin(a), y, -radius * cos(a))
        return Offset(camera.sx, camera.sy)
    }

    /** La normale della faccia k, gia' in coordinate di vista. */
    fun facing(k: Int): Float {
        val a = k * 60f * DEG
        camera.normal(sin(a), 0f, -cos(a))
        return -camera.nvz
    }

    // ── L'ombra, per prima: sta dietro tutto ────────────────────────────────
    run {
        camera.place(0f, floorY, 0f)
        val base = Offset(camera.sx, camera.sy)
        val squash = 0.34f + 0.20f * (1f - camera.scale)
        drawOval(
            color = sideFar.copy(alpha = 0.16f),
            topLeft = Offset(base.x - radius * 1.15f, base.y - radius * squash * 0.5f),
            size = Size(radius * 2.3f, radius * squash),
        )
    }

    // ── Il fondo, visto da sopra attraverso l'apertura ──────────────────────
    drawPath(ring(::corner, floorY), color = sideFar.copy(alpha = 0.85f))

    // ── Le facce, dalla piu' lontana alla piu' vicina ───────────────────────
    val order = (0 until GAUGE_FACES).sortedByDescending { k ->
        val a = (k + 0.5f) * 60f * DEG - 30f * DEG
        camera.place(radius * sin(a), 0f, -radius * cos(a))
        camera.vz
    }

    for (k in order) {
        val front = facing(k) > 0f
        val lit = camera.lambert(Light.Standard)
        val wall = lerp(sideFar, sideNear, lit)
        val path = Path().apply {
            val a = corner(k, rimY)
            moveTo(a.x, a.y)
            val b = corner(k + 1, rimY); lineTo(b.x, b.y)
            val c = corner(k + 1, floorY); lineTo(c.x, c.y)
            val d = corner(k, floorY); lineTo(d.x, d.y)
            close()
        }

        if (!front) {
            // La parete dietro e' opaca: e' il fondale su cui si leggono la
            // graduazione e il pelo dell'acqua.
            drawPath(path, color = wall)
            drawTicks(::corner, k, rimY, floorY, radius, ink, snowy)
        } else {
            // ── Le facce davanti sono traslucide ────────────────────────────
            //
            // **Non e' la trappola dell'opacita' che codifica una quantita'**:
            // qui e' una costante e non codifica niente. E' l'unico modo perche'
            // il vetro si legga come vetro - attraverso di lui si vedono
            // l'acqua e la parete lontana - e leggere il livello dalla sola
            // apertura smetterebbe di funzionare appena l'inclinazione cala.
            drawPath(path, color = wall.copy(alpha = WALL_SEE_THROUGH))
        }
    }

    // ── L'acqua ─────────────────────────────────────────────────────────────
    if (fill > 0f) {
        // Il corpo dell'acqua, dal pelo al fondo, e poi la superficie sopra: la
        // superficie va per ultima, se no il corpo gliela copre.
        drawPath(prism(::corner, waterY, floorY), color = liquid.copy(alpha = 0.42f))
        drawPath(ring(::corner, waterY), color = liquid.copy(alpha = 0.80f))
        drawPath(
            ring(::corner, waterY),
            color = Color.White.copy(alpha = 0.22f),
            style = Stroke(width = unit * 0.004f),
        )
    }

    // ── Lo smusso del bordo: e' quello che la fa sembrare fresata ───────────
    drawPath(ring(::corner, rimY), color = chamfer.copy(alpha = 0.30f))
    drawPath(
        ring(::corner, rimY),
        color = face.copy(alpha = 0.85f),
        style = Stroke(width = unit * GAUGE_RIM * 0.5f),
    )

    // ── Quando trabocca ─────────────────────────────────────────────────────
    //
    // Sopra il colmo **non si taglia in silenzio e non si ritara la scala**:
    // l'acqua si ferma all'orlo e cola fuori. Il numero scritto accanto dice
    // sempre il valore vero. Una vasca che si riscala da sola non e' graduata.
    if (overflowing) {
        for (k in 0 until GAUGE_FACES) {
            if (facing(k) <= 0.35f) continue
            val a = corner(k, rimY)
            val b = corner(k + 1, rimY)
            val x = (a.x + b.x) / 2f
            val y = (a.y + b.y) / 2f
            drawLine(
                color = liquid.copy(alpha = 0.55f),
                start = Offset(x, y),
                end = Offset(x, y + radius * 0.55f),
                strokeWidth = unit * 0.010f,
                cap = StrokeCap.Round,
            )
        }
    }

    // ── La pioggia che ci cade dentro ───────────────────────────────────────
    if (wetness > 0f) {
        drawIntoGauge(
            camera = camera,
            unit = unit,
            radius = radius,
            rimY = rimY,
            waterY = waterY,
            snowy = snowy,
            colour = liquid,
            progress = progress,
            wetness = wetness,
        )
    }

    // ── Il numero, che **non** gira ─────────────────────────────────────────
    //
    // Sta in coordinate di schermo, dritto, all'altezza del pelo dell'acqua.
    // Stesso argomento della mediana della luna: quanti millimetri siano caduti
    // e' un fatto della giornata, non di dove sta chi guarda - e un numero che
    // va di taglio a novanta gradi e' un numero che non si legge. Chi passera'
    // di qui a "correggerlo" facendolo ruotare stia leggendo questa riga.
    run {
        // Il punto si prende **sull'asse**, non su un bordo del modello: un
        // bordo gira con l'oggetto e a mezzo giro la scritta passerebbe
        // dall'altra parte, finendo sopra la vasca. L'asse sta fermo, e lo
        // scostamento e' in punti di schermo.
        camera.place(0f, waterY, 0f)
        val laid = measurer.measure(reading, readingStyle)
        drawText(
            laid,
            topLeft = Offset(
                x = (camera.sx + radius * 1.25f)
                    .coerceAtMost(size.width - laid.size.width),
                y = camera.sy - laid.size.height / 2f,
            ),
        )
    }

    // La scaletta scritta, a sinistra, allineata alle tacche: e' il numero che
    // da' un senso all'altezza.
    drawScaleLabels(camera, unit, radius, rimY, floorY, top, snowy, measurer, tickStyle)
}

/** Il poligono orizzontale a una certa quota: fondo, pelo dell'acqua, orlo. */
private fun ring(corner: (Int, Float) -> Offset, y: Float): Path = Path().apply {
    val first = corner(0, y)
    moveTo(first.x, first.y)
    for (k in 1 until GAUGE_FACES) {
        val p = corner(k, y)
        lineTo(p.x, p.y)
    }
    close()
}

/** Il corpo fra due quote: la colonna d'acqua. */
private fun prism(corner: (Int, Float) -> Offset, topY: Float, bottomY: Float): Path =
    Path().apply {
        for (k in 0 until GAUGE_FACES) {
            val a = corner(k, topY)
            val b = corner(k + 1, topY)
            val c = corner(k + 1, bottomY)
            val d = corner(k, bottomY)
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            lineTo(c.x, c.y)
            lineTo(d.x, d.y)
            close()
        }
    }

/**
 * Le tacche incise sulla parete interna.
 *
 * Stanno **dentro**, sulle facce che danno le spalle a chi guarda, e si vedono
 * attraverso l'apertura e attraverso il vetro davanti. Girando la vasca vengono
 * avanti quelle di un'altra parete: la graduazione fa il giro, ed e' cio' che il
 * gesto del dito serve a vedere.
 */
private fun DrawScope.drawTicks(
    corner: (Int, Float) -> Offset,
    k: Int,
    rimY: Float,
    floorY: Float,
    radius: Float,
    ink: Color,
    snowy: Boolean,
) {
    val steps = if (snowy) SNOW_TICKS else RAIN_TICKS
    val span = floorY - rimY
    for ((index, share) in steps.withIndex()) {
        val y = floorY - span * share
        val a = corner(k, y)
        val b = corner(k + 1, y)
        // Le tacche tonde sono lunghe tutta la faccia, quelle fini un terzo:
        // e' la differenza fra una misura da leggere e una da contare.
        val major = index % 2 == 1
        val t = if (major) 0f else 0.34f
        drawLine(
            color = ink.copy(alpha = if (major) 0.55f else 0.30f),
            start = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t),
            end = Offset(b.x - (b.x - a.x) * t, b.y - (b.y - a.y) * t),
            strokeWidth = radius * if (major) 0.030f else 0.020f,
            cap = StrokeCap.Round,
        )
    }
}

/** La scaletta scritta accanto alla vasca, allineata alle tacche grosse. */
private fun DrawScope.drawScaleLabels(
    camera: Camera,
    unit: Float,
    radius: Float,
    rimY: Float,
    floorY: Float,
    top: Double,
    snowy: Boolean,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val span = floorY - rimY
    val majors = if (snowy) SNOW_MAJORS else RAIN_MAJORS
    for (share in majors) {
        val y = floorY - span * share.toFloat()
        // Come per la lettura: il punto sta sull'asse e lo scostamento e' in
        // punti di schermo, cosi' la scaletta resta a sinistra a ogni angolo.
        camera.place(0f, y, 0f)
        val text = (top * share).roundToInt().toString()
        val laid = measurer.measure(text, style)
        drawText(
            laid,
            topLeft = Offset(
                x = (camera.sx - radius * 1.25f - laid.size.width).coerceAtLeast(0f),
                y = camera.sy - laid.size.height / 2f,
            ),
        )
    }
}

/**
 * Le gocce che cadono dentro la vasca, e cosa succede quando toccano l'acqua.
 *
 * **Non passa da `drawRain`**, e non e' pigrizia: quella e' saldata a
 * `SceneContact` e alla sagoma della cifra, cioe' chiede a un'altra tela dove
 * sta la superficie. Qui la superficie e' un piano che la vasca conosce per via
 * analitica, e costruire una sagoma finta per farsi rispondere quello che si sa
 * gia' sarebbe una bugia con dei costi.
 *
 * Le posizioni vengono da [DROPS], che e' il campo gia' seminato una volta sola:
 * due elenchi di gocce sarebbero due elenchi destinati a divergere.
 */
private fun DrawScope.drawIntoGauge(
    camera: Camera,
    unit: Float,
    radius: Float,
    rimY: Float,
    waterY: Float,
    snowy: Boolean,
    colour: Color,
    progress: Float,
    wetness: Float,
) {
    // Almeno tre: se il codice dice che piove, deve piovere. Un temporale a zero
    // millimetri non puo' avere zero gocce.
    val count = (DROPS.size * wetness).roundToInt().coerceIn(3, DROPS.size)
    val inner = radius * 0.80f
    val start = rimY - unit * 0.22f
    val stroke = (unit * 0.010f).coerceAtLeast(1.5f)

    for (i in 0 until count) {
        val drop = DROPS[i]
        val travel = (drop.phase + progress * drop.speed) % 1f
        val x = drop.x * inner
        val z = drop.z * inner
        val y = start + travel * (waterY - start)

        camera.place(x, y, z)
        val head = Offset(camera.sx, camera.sy)
        camera.place(x, waterY, z)
        val surface = Offset(camera.sx, camera.sy)

        if (head.y < surface.y) {
            if (snowy) {
                // La neve si posa, non schizza: e' la stessa distinzione che il
                // disegno della scultura fa gia'.
                drawCircle(
                    color = colour.copy(alpha = 0.75f),
                    radius = stroke * 0.9f,
                    center = head,
                )
            } else {
                camera.place(x, y - drop.length * unit, z)
                drawLine(
                    color = colour.copy(alpha = 0.75f),
                    start = Offset(camera.sx, camera.sy),
                    end = head,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            continue
        }

        if (snowy) continue

        // Arrivata: schizzo e anello sul pelo dell'acqua. L'eta' sta gia' nella
        // posizione - quanto la goccia e' andata oltre la superficie - quindi
        // fra un fotogramma e l'altro non c'e' niente da ricordare.
        val spent = ((head.y - surface.y) / (unit * SPLASH_LIFE)).coerceIn(0f, 1f)
        if (spent >= 1f) continue
        splash(surface, stroke, spent, colour, 0.9f)
        microSplashRing(surface, stroke, spent, colour, 0.9f)
    }
}

private const val DEG = (PI / 180.0).toFloat()

/** Il raggio della vasca, in frazione del lato corto del riquadro dell'eroe. */
private const val GAUGE_RADIUS = 0.30f

/** Quanto e' alta rispetto al raggio: piu' alta che larga, se no e' una ciotola. */
private const val GAUGE_TALL = 1.55f

/** Sei facce. Quattro sono una scatola, otto tornano un tubo. */
private const val GAUGE_FACES = 6

/** Lo spessore dell'orlo, in frazione del lato corto: e' lo smusso fresato. */
private const val GAUGE_RIM = 0.020f

/**
 * L'inclinazione a riposo, in gradi.
 *
 * La luna non ne ha bisogno: una sfera non ha un dentro. Questa si' - guardata
 * di taglio l'apertura e' una riga e l'acqua e' un tratto. Quattordici gradi
 * bastano a far vedere il fondo senza che l'oggetto si legga come visto
 * dall'alto.
 */
private const val GAUGE_PITCH_REST = 14f

/** Quanto l'inclinazione del telefono aggiunge alla propria, in gradi. */
private const val GAUGE_TILT = 5f

/**
 * Quanto e' trasparente la parete davanti.
 *
 * Una costante che **non codifica niente**: e' il vetro, non una quantita'.
 * Serve perche' attraverso la parete vicina si vedano l'acqua e la graduazione
 * dietro; leggere il livello dalla sola apertura funziona solo finche'
 * l'inclinazione e' generosa, e a telefono dritto smetterebbe.
 */
private const val WALL_SEE_THROUGH = 0.34f

/** Le tacche, in frazione dell'altezza: fini ogni cinque, grosse ogni dieci. */
private val RAIN_TICKS = floatArrayOf(
    0.125f, 0.25f, 0.375f, 0.50f, 0.625f, 0.75f, 0.875f, 1.0f,
)

/** Le stesse otto per la neve: cinque, dieci, quindici, venti centimetri. */
private val SNOW_TICKS = RAIN_TICKS

/** Dove vanno i numeri scritti: 10, 20, 30, 40 millimetri. */
private val RAIN_MAJORS = doubleArrayOf(0.25, 0.5, 0.75, 1.0)

/** 5, 10, 15, 20 centimetri. */
private val SNOW_MAJORS = RAIN_MAJORS

private val TICK_SP = 9.sp
