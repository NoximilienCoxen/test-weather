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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.forli.meteo.data.SkyState
import com.forli.meteo.data.SunClock
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
import kotlin.math.cos
import kotlin.math.sin
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
    tilt: State<Offset>,
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
    // **La grandine e' dichiarata dal codice, non dedotta dalla quantita'.**
    // Novantasei e novantanove sono "temporale con grandine": e' l'unico modo di
    // saperlo: dai millimetri non si distingue, perche' pesano uguale.
    val hailing = weatherCode == 96 || weatherCode == 99

    /**
     * Quanta pioggia si vede.
     *
     * I millimetri da soli non bastano: un temporale previsto al sessanta per
     * cento puo' avere zero millimetri in quell'ora esatta, e allora sotto la
     * scritta TEMPORALE non cadeva niente. Se il codice dice che piove, deve
     * piovere; i millimetri decidono quanto forte, non se.
     */
    val target = when {
        // **Il temporale non contratta.** Con la formula normale un temporale
        // previsto all'ottanta per cento dava poco piu' di mezza pioggia, e
        // sotto la scritta TEMPORALE si vedeva una pioviggine. I millimetri di
        // quell'ora non sono la misura giusta: un rovescio scarica in dieci
        // minuti e la casella oraria lo diluisce.
        storming -> 1f
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

    // Chi ha toccato la cifra e quando. Lo scopre il disegno, che e' l'unico a
    // sapere dove passa la sagoma; lo consuma il ciclo qui sotto, che e'
    // l'unico posto da cui si possa chiamare il vibratore senza infilare una
    // chiamata al sistema dentro un fotogramma.
    val impacts = remember { RainImpacts() }

    // `feelsIt` cambia quando si aprono le impostazioni, e non deve far
    // ripartire la caduta da capo: la pioggia salterebbe indietro ogni volta.
    val feels = rememberUpdatedState(feelsIt)

    LaunchedEffect(raining) {
        // Chi stava toccando prima non conta piu': fra una pioggia e la
        // successiva la cifra ha cambiato numero, angolo e sagoma.
        impacts.forget()
        if (!raining) return@LaunchedEffect
        var origin = 0L
        var lastTap = 0L
        while (true) {
            var frame = 0L
            var landed = 0
            withFrameNanos { now ->
                frame = now
                if (origin == 0L) origin = now
                val elapsed = (now - origin) / 1_000_000L
                fall.floatValue = (elapsed % FALL_CYCLE_MS) / FALL_CYCLE_MS.toFloat()
                landed = impacts.take()
            }
            // Una goccia che arriva sulla cifra si sente in mano. Prima il
            // colpetto arrivava a tempo, uno per giro di gocce, e a tempo non
            // vuol dire niente: cadeva anche quando la pioggia passava a fianco
            // della cifra senza toccarla, e mancava quando ne arrivavano cinque
            // insieme. Adesso e' l'urto a chiamarlo, e la mano sente dove
            // l'occhio vede.
            //
            // Con una soglia di tempo, pero': in un rovescio arrivano decine di
            // gocce al secondo e un vibratore che non stacca mai non si legge
            // piu' come pioggia, si legge come un ronzio.
            if (landed > 0 && feels.value && frame - lastTap > TAP_GAP_NS) {
                lastTap = frame
                haptics.raindrop(landed)
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


    /**
     * Il respiro della scultura: **continuo, finche' la schermata si vede**.
     *
     * Prima durava quattro secondi e si spegneva da sola, per non rinunciare
     * alla proprieta' misurata dell'app - a schermo immobile, zero fotogrammi
     * (trappola #8). Provata in mano, quella prudenza si e' rivelata sbagliata
     * nel merito: un movimento che finisce prima che tu abbia finito di
     * guardare non si legge come una scultura viva, si legge come **niente**.
     * Chi guarda l'app la apre, guarda, e chiude: il moto deve esserci mentre
     * guarda.
     *
     * Il prezzo si paga e va detto: la scena in tre dimensioni si ridisegna a
     * ogni fotogramma finche' la schermata e' in primo piano, e in tasca si
     * sente. Non si paga in sottofondo, ed e' l'unico sconto che non e' stato
     * scelto ma regalato: `withFrameNanos` non batte quando la finestra non si
     * vede, quindi il ciclo si ferma da solo senza che nessuno glielo dica.
     *
     * Il valore e' una fase in secondi: chi disegna la usa come tempo.
     */
    val stir = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                stir.floatValue += dt
            }
        }
    }

    val phase = remember(date) { MoonPhase.at(date) }

    // Riusati a ogni fotogramma: la fila dei corpi tondi e la loro profondita'.
    val bodyDepth = remember { FloatArray(CLOUD_MASSES.size + 1) }
    val bodyOf = remember { IntArray(CLOUD_MASSES.size + 1) }

    Canvas(
        modifier.onGloballyPositioned { coordinates ->
            contact.rainOrigin = coordinates.positionInRoot()
        },
    ) {
        val unit = size.minDimension
        val camera = Camera(
            yawDeg = rotation.yawDeg + tilt.value.x * TILT_YAW,
            pitchDeg = tilt.value.y * TILT_PITCH,
            // Piu' vicina di quella della cifra rispetto alla propria
            // dimensione: la scultura e' un oggetto piccolo tenuto vicino
            // all'occhio, e girandola la prospettiva deve sentirsi.
            distance = unit * 2.1f,
            // In basso nel proprio riquadro: la scultura deve appoggiarsi alla
            // cifra, non galleggiare sopra un vuoto.
            origin = Offset(size.width / 2f, size.height * 0.74f),
        )
        val glare = flash.value

        // **Un astro non e' trasparente.** Prima l'opacita' scendeva in
        // proporzione alla copertura, e una luna al sessanta per cento su un
        // cielo notturno non si legge come luna velata: si legge come una
        // macchia. E' la stessa trappola gia' pagata sulla nuvola, che infatti
        // cambia numero di masse e dimensione invece di sbiadire. Qui l'astro
        // resta pieno finche' c'e', e a nasconderlo ci pensa la nuvola
        // passandogli davvero davanti; solo verso il cielo coperto se ne va,
        // perche' li' davvero non lo si vedrebbe piu'.
        val clear = 1f - SunClock.smoothstep(0.62f, 0.86f, cloudiness)

        val scale = 0.52f + cloudiness * 0.48f
        val presence = ((cloudiness - 0.02f) / 0.06f).coerceIn(0f, 1f)

        // **Quanto siamo nel coperto**, che e' una condizione diversa in natura
        // e non solo in quantita'.
        //
        // Fino al nuvoloso la nuvola e' una *forma*: ha dei bordi, la si guarda,
        // si sposta e la si riconosce da un momento all'altro. Il coperto no. Il
        // coperto non e' una nuvola piu' grande, e' un tetto - non ha un profilo
        // da fissare, e non si sposta perche' non c'e' un fuori verso cui
        // andare. Disegnarlo come "cinque masse invece di tre" lo faceva
        // sembrare esattamente questo: una nuvola grossa.
        val overcast = ((cloudiness - 0.70f) / 0.30f).coerceIn(0f, 1f)
        // Col temporale se ne accendono due in piu', e la fila arriva a sette.
        // Le altre condizioni si fermano a cinque: sono quelle che c'erano, e
        // il fronte deve restare un salto visibile, non un incremento.
        val masses = if (cloudiness > 0.02f) {
            val extra = if (storming) 2 else 0
            (2 + (cloudiness * 3f).roundToInt() + extra)
                .coerceIn(2, if (storming) CLOUD_MASSES.size else 5)
        } else {
            0
        }

        // **Astro e nuvola si fanno spazio a vicenda solo quando sono in due.**
        //
        // L'astro stava sempre spostato a destra, che serve a non farlo
        // inghiottire dalla nuvola - ma col sereno la nuvola non c'e', e il sole
        // restava in alto a destra da solo, scollato dalla cifra che gli sta
        // sotto. Ora lo scostamento cresce con la nuvola: senza, l'astro e' al
        // centro sopra il numero; con, i due si allargano attorno al centro.
        val pairing = if (masses > 0) presence else 0f
        val bodyX = unit * 0.21f * pairing
        val bodyY = -unit * (0.15f + 0.05f * pairing)
        val bodyZ = unit * 0.26f
        val bodyRadius = unit * 0.23f
        val cloudX = -unit * 0.13f * pairing

        val sunAlpha = clear * sky.sunPresence
        val moonAlpha = clear * sky.moonPresence
        val astroAlpha = maxOf(sunAlpha, moonAlpha)

        // Il lampo illumina la nuvola da dentro: se restasse dello stesso
        // grigio, la saetta sembrerebbe disegnata davanti a un fondale.
        val core = lerp(lerp(colors.cloudCore, colors.rainCloudCore, laden), Lightning, glare * 0.55f)
        val shade = lerp(lerp(colors.cloudShade, colors.rainCloudShade, laden), Lightning, glare * 0.40f)

        // **Un ordine solo per tutti i corpi tondi.** L'astro veniva disegnato
        // per primo e basta, cioe' era un fondale: qualunque cosa facesse la
        // rotazione, la nuvola gli restava davanti. Portandolo di fronte con
        // mezzo giro di dito lo si vedeva comunque sotto le masse bianche, ed e'
        // esattamente il difetto per cui la luna non si vedeva mai intera.
        // Adesso entra nella stessa fila delle masse, ordinato per profondita'
        // in coordinate di vista come loro: passa dietro e poi davanti, e chi
        // gira decide cosa vedere.
        val moved = stir.floatValue

        // Le stelle, e solo quando ci sono davvero: di notte e con il cielo
        // sgombro.
        //
        // **Non passano dalla camera, ed e' il punto.** Prima ci passavano, come
        // ogni altro corpo, quindi gira la scultura e girava anche il cielo. Su
        // uno scatto a giro zero e uno a centocinquantacinque gradi non c'era
        // una sola stella nello stesso posto - il campo stellato era un altro. E
        // un cielo che ruota con l'oggetto davanti non si legge come cielo: si
        // legge come una cupola dipinta attaccata alla scultura, che se la porta
        // dietro girando. Il fondo deve restare fondo. Adesso la posizione la
        // decidono lo schermo e nient'altro: si gira la cifra e la notte resta
        // dov'e'.
        //
        // Per la stessa ragione se ne va anche `camera.scale`: se non c'e'
        // profondita' non c'e' rimpicciolimento prospettico da applicare. La
        // gerarchia fra stelle grandi e piccole ce l'ha gia' l'elenco.
        //
        // Il tremolio invece era gia' sparito, ed e' un'altra cosa ancora: una
        // stella che pulsa e' un lampeggiatore, e in mezzo a un sole che gira e
        // a nuvole che derivano diventa l'unica cosa che distrae.
        //
        // **Quante, lo decide il buio.** Sul grigio del crepuscolo se ne vedono
        // due o tre, a notte piena il cielo si riempie: e' cosi' che va, e farle
        // comparire tutte insieme al calar del sole sarebbe un interruttore, non
        // una sera. Il conto e' quadratico apposta, cosi' le prime arrivano
        // tardi e poi si affollano.
        val night = (1f - sky.dayness).coerceIn(0f, 1f)
        val starAlpha = night * clear
        if (starAlpha > 0.02f) {
            val shown = (STARS.size * night * night).toInt().coerceIn(0, STARS.size)
            for (k in 0 until shown) {
                val star = STARS[k]
                drawCircle(
                    color = colors.text.copy(alpha = starAlpha * star.glow),
                    radius = (unit * star.size).coerceAtLeast(0.8f),
                    center = Offset(
                        camera.origin.x + star.x * unit,
                        camera.origin.y + star.y * unit,
                    ),
                )
            }

            // **Una stella cadente ogni tanto, e solo col buio pieno.** Non e'
            // decorazione: e' la cosa che distingue una notte da un cielo
            // semplicemente scuro. Una per volta e rade, pero' - una pioggia di
            // meteore sopra la temperatura di domani non e' atmosfera, e' un
            // salvaschermo.
            //
            // Dove parte e da che parte va lo decide il **numero del ciclo**:
            // cosi' due cadute di seguito non si somigliano, ma la stessa caduta
            // resta identica a se stessa a ogni fotogramma. Non c'e' nessuno
            // stato da tenere e da rimettere a posto - la si ricava dall'orologio
            // e basta, che e' la stessa regola delle gocce.
            if (night > SHOOTING_DARK) {
                val cycle = moved / SHOOTING_PERIOD
                val turn = cycle.toInt()
                val life = (cycle - turn) / SHOOTING_SPAN
                if (life < 1f) {
                    val seed = turn * HASH_MIX
                    val fx = ((seed shr 8) and 0xFF) / 255f
                    val fy = ((seed shr 16) and 0xFF) / 255f
                    val fa = ((seed shr 24) and 0xFF) / 255f
                    val angle = SHOOTING_TILT_MIN + fa * (SHOOTING_TILT_MAX - SHOOTING_TILT_MIN)
                    val run = unit * SHOOTING_LENGTH
                    val dx = cos(angle) * run
                    val dy = sin(angle) * run
                    val fromX = camera.origin.x + (fx * 1.8f - 1.0f) * unit
                    val fromY = camera.origin.y - (0.30f + fy * 0.66f) * unit
                    // Piena a meta' corsa: entra, attraversa, esce. Comparire e
                    // sparire di colpo si legge come uno sfarfallio del disegno.
                    val glow = sin(life * PI_F) * starAlpha
                    val head = Offset(fromX + dx * life, fromY + dy * life)
                    val tail = Offset(head.x - dx * SHOOTING_TAIL, head.y - dy * SHOOTING_TAIL)
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                colors.text.copy(alpha = 0f),
                                colors.text.copy(alpha = glow),
                            ),
                            start = tail,
                            end = head,
                        ),
                        start = tail,
                        end = head,
                        strokeWidth = unit * 0.0055f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        // Gli uccelli stanno nel cielo, quindi prima di tutto il resto: la
        // nuvola e la scultura devono poterci passare davanti. Ci sono solo col
        // sereno di giorno - con la nuvola addosso non si vedrebbero comunque,
        // e di notte non volano.
        drawBirds(
            unit = unit,
            origin = camera.origin,
            time = moved,
            presence = clear * sky.dayness,
            colour = colors.label,
        )

        // Ogni massa deriva per conto suo, con la propria fase e la propria
        // velocita': tutte insieme sarebbe una nuvola che trema, non una nuvola
        // che si muove. Quelle davanti scorrono di piu' di quelle dietro, che e'
        // la stessa parallasse che gia' racconta lo spazio quando si gira.
        // Col coperto la fila si allarga oltre i bordi: quello che si vede
        // smette di avere due estremita' e diventa una fascia che continua fuori
        // dallo schermo. E' meta' di cio' che fa un tetto - l'altra meta' e' il
        // respiro qui sotto.
        val spread = 1f + overcast * OVERCAST_SPREAD

        fun driftX(k: Int): Float {
            val lump = CLOUD_MASSES[k]
            val depth = 0.6f + 0.4f * (1f - (lump.z + 0.2f))
            return lump.x * unit * scale * spread + cloudX +
                sin(moved * 0.62f + k * 1.7f) * unit * DRIFT * depth
        }

        // **Il respiro.** Ogni massa si gonfia e si sgonfia per conto suo,
        // quindi il profilo della fila non sta mai fermo e non se ne riesce a
        // fissare uno. Solo col coperto: su una nuvola isolata questo si
        // leggerebbe come una palla che pulsa, mentre su una fascia continua
        // toglie i bordi, che e' il punto.
        fun swell(k: Int): Float =
            1f + overcast * BREATH * sin(moved * 0.5f + k * 2.1f)

        fun driftY(k: Int): Float {
            val lump = CLOUD_MASSES[k]
            return lump.y * unit * scale +
                sin(moved * 0.48f + k * 2.6f) * unit * DRIFT * 0.55f
        }

        var bodies = 0
        if (astroAlpha > 0.01f) {
            camera.place(bodyX, bodyY, bodyZ)
            bodyDepth[bodies] = camera.vz
            bodyOf[bodies] = ASTRO
            bodies++
        }
        for (k in 0 until masses) {
            val lump = CLOUD_MASSES[k]
            camera.place(driftX(k), driftY(k), lump.z * unit * scale)
            bodyDepth[bodies] = camera.vz
            bodyOf[bodies] = k
            bodies++
        }
        // Sei elementi al massimo: un inserimento diretto costa meno di
        // qualunque ordinamento generico, e soprattutto non alloca niente a ogni
        // fotogramma.
        for (i in 1 until bodies) {
            val depth = bodyDepth[i]
            val which = bodyOf[i]
            var j = i - 1
            while (j >= 0 && bodyDepth[j] < depth) {
                bodyDepth[j + 1] = bodyDepth[j]
                bodyOf[j + 1] = bodyOf[j]
                j--
            }
            bodyDepth[j + 1] = depth
            bodyOf[j + 1] = which
        }

        for (i in 0 until bodies) {
            val which = bodyOf[i]
            if (which == ASTRO) {
                if (sunAlpha >= moonAlpha) {
                    // La corona gira piano e i raggi respirano: e' quello che
                    // distingue un sole da un cerchio giallo con dei trattini.
                    val turn = moved * 9f
                    val reach = (0.5f + 0.5f * sin(moved * 1.15f))
                    sunRays(
                        camera, bodyX, bodyY, bodyZ, bodyRadius, colors.sunCore,
                        sunAlpha * 0.75f, far = true, turnDeg = turn, reach = reach,
                    )
                    sphere(camera, bodyX, bodyY, bodyZ, bodyRadius, colors.sunCore, colors.sunShade, sunAlpha)
                    sunRays(
                        camera, bodyX, bodyY, bodyZ, bodyRadius, colors.sunCore,
                        sunAlpha * 0.75f, far = false, turnDeg = turn, reach = reach,
                    )
                } else {
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
                continue
            }
            val lump = CLOUD_MASSES[which]
            sphere(
                camera = camera,
                x = driftX(which),
                y = driftY(which),
                z = lump.z * unit * scale,
                radius = lump.radius * unit * scale * swell(which),
                light = core,
                dark = shade,
                alpha = presence,
            )
        }

        if (glare > 0.01f && bolt.isNotEmpty()) {
            drawBolt(camera, unit, scale, bolt, glare)
        }

        if (wetness > 0.01f && hailing) {
            drawHail(
                camera = camera,
                unit = unit,
                scale = scale,
                wetness = wetness,
                progress = fall.floatValue,
                colour = colors.cloudCore,
                contact = contact,
                impacts = impacts,
            )
        } else if (wetness > 0.01f && family == Wmo.Family.NEVE) {
            drawSnow(
                camera = camera,
                unit = unit,
                scale = scale,
                wetness = wetness,
                progress = fall.floatValue,
                colour = colors.cloudCore,
                contact = contact,
                impacts = impacts,
            )
        } else if (wetness > 0.01f) {
            drawRain(
                camera = camera,
                unit = unit,
                scale = scale,
                wetness = wetness,
                confidence = confidence,
                progress = fall.floatValue,
                colour = colors.rain,
                contact = contact,
                impacts = impacts,
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
    // Le due del temporale. Stanno larghe e dietro: un temporale non e' una
    // nuvola piu' fitta al centro, e' un fronte che occupa piu' cielo.
    Lump(-0.42f, -0.04f, -0.24f, 0.21f),
    Lump(0.44f, -0.02f, -0.21f, 0.22f),
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
 * Quali gocce stanno toccando la cifra, e quante hanno appena cominciato.
 *
 * Lo stato sta qui e non in Compose apposta: viene scritto dentro il disegno e
 * riletto dal ciclo della caduta, sullo stesso filo e a un fotogramma di
 * distanza. Uno stato osservabile chiederebbe una ricomposizione per qualcosa
 * che sullo schermo non cambia niente - la vibrazione non si vede.
 */
private class RainImpacts {

    /** Se ognuna stava gia' toccando al fotogramma prima. */
    private val touching = BooleanArray(DROPS.size)

    private var landed = 0

    /**
     * Dal disegno: questa goccia sta toccando, o no.
     *
     * Conta solo il passaggio dall'aria alla superficie. Senza il confronto col
     * fotogramma prima, ogni goccia gia' arrivata ne segnerebbe uno per
     * fotogramma e non ci sarebbe piu' differenza fra una goccia che arriva e
     * una goccia ferma sul posto.
     */
    fun mark(index: Int, hitting: Boolean) {
        if (index !in touching.indices) return
        if (hitting && !touching[index]) landed++
        touching[index] = hitting
    }

    /** Dal ciclo: quante ne sono arrivate dall'ultima volta che si e' guardato. */
    fun take(): Int {
        val count = landed
        landed = 0
        return count
    }

    fun forget() {
        java.util.Arrays.fill(touching, false)
        landed = 0
    }
}

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

private val DROPS: List<Drop> = List(48) { i ->
    val r = Random(i * 7919 + 13)
    Drop(
        x = r.nextFloat() * 2f - 1f,
        z = r.nextFloat() * 2f - 1f,
        phase = r.nextFloat(),
        speed = 0.85f + r.nextFloat() * 0.5f,
        length = 0.05f + r.nextFloat() * 0.05f,
    )
}

/**
 * Un uccello: la corsia in cui vola, la fase, quanto e' veloce e quanto grande.
 */
private class Bird(val lane: Float, val phase: Float, val speed: Float, val size: Float)

/**
 * Tre e non uno stormo. Uno solo si legge come un difetto del disegno, dieci
 * come un'invasione: tre a distanze diverse dicono "cielo aperto" e basta.
 */
private val BIRDS = listOf(
    Bird(lane = -0.74f, phase = 0.00f, speed = 0.055f, size = 0.058f),
    Bird(lane = -0.58f, phase = 0.41f, speed = 0.044f, size = 0.044f),
    Bird(lane = -0.86f, phase = 0.72f, speed = 0.068f, size = 0.036f),
)

/**
 * Gli uccelli del sereno.
 *
 * Due archi che si toccano, cioe' la sagoma con cui **tutti** disegnano un
 * uccello lontano - e a questa dimensione qualunque tentativo di fare di piu'
 * diventa una macchia. Quello che li rende vivi non e' la forma: e' che le ali
 * battono e che ognuno attraversa con un passo suo.
 *
 * Attraversano lo schermo e basta, entrando e uscendo in dissolvenza. Farli
 * girare in tondo li avrebbe legati a un centro, e un uccello che orbita
 * attorno alla temperatura e' un carillon, non un cielo.
 */
private fun DrawScope.drawBirds(
    unit: Float,
    origin: Offset,
    time: Float,
    presence: Float,
    colour: Color,
) {
    if (presence <= 0.02f) return
    BIRDS.forEach { bird ->
        val across = (time * bird.speed + bird.phase) % 1f
        val x = origin.x + (across * 2.6f - 1.3f) * unit
        val bob = sin(time * 0.9f + bird.phase * PI_F * 2f) * unit * 0.02f
        val y = origin.y + bird.lane * unit + bob
        val edge = (across / 0.12f).coerceAtMost(1f) *
            ((1f - across) / 0.12f).coerceAtMost(1f)
        val shade = presence * edge * 0.70f
        if (shade <= 0.01f) return@forEach

        // Il battito. Le punte salgono e scendono attorno al corpo: e' l'unica
        // cosa che distingue un uccello che vola da un accento circonflesso.
        val beat = sin(time * BIRD_BEAT + bird.phase * PI_F * 2f)
        val w = unit * bird.size
        val lift = w * 0.44f * beat
        val wing = Path().apply {
            moveTo(x - w, y - lift)
            quadraticTo(x - w * 0.45f, y + w * 0.18f, x, y)
            quadraticTo(x + w * 0.45f, y + w * 0.18f, x + w, y - lift)
        }
        drawPath(
            path = wing,
            color = colour.copy(alpha = shade),
            style = Stroke(
                width = (w * 0.13f).coerceAtLeast(1.4f),
                cap = StrokeCap.Round,
            ),
        )
    }
}

/**
 * La grandine: chicchi che rimbalzano.
 *
 * Non e' pioggia piu' forte, ed e' per questo che non bastava alzare la
 * quantita'. La differenza fra acqua e ghiaccio, vista da lontano, sta tutta in
 * cosa succede **quando toccano**: l'acqua si apre e sparisce dentro la
 * superficie, il ghiaccio riparte. Un chicco che si spiaccica e' una goccia
 * grossa; un chicco che rimbalza e' grandine, anche se disegnato uguale.
 *
 * Le altre due cose che la distinguono: cade piu' dritta della neve e piu' in
 * fretta della pioggia - il vento non se la passa - ed e' tonda invece che
 * allungata, perche' a quella velocita' una goccia si stira e un chicco no.
 *
 * Il rimbalzo e' un arco solo, non tre. Farlo rimbalzare piu' volte sarebbe
 * fisica gratis: a questa dimensione il secondo rimbalzo e' due pixel e l'unico
 * effetto e' che il chicco resta in giro troppo a lungo.
 */
private fun DrawScope.drawHail(
    camera: Camera,
    unit: Float,
    scale: Float,
    wetness: Float,
    progress: Float,
    colour: Color,
    contact: SceneContact?,
    impacts: RainImpacts? = null,
) {
    val count = (DROPS.size * wetness).roundToInt().coerceIn(5, DROPS.size)
    for (i in count until DROPS.size) impacts?.mark(i, false)

    val spreadX = unit * 0.42f * scale
    val spreadZ = unit * 0.17f * scale
    val top = unit * 0.16f * scale
    val shift = contact?.numberToRain ?: Offset.Zero
    val skyline = contact?.skyline
    val ground = skyline?.floor?.takeIf { !it.isNaN() }?.let { it + shift.y } ?: Float.NaN
    val span = if (ground.isNaN()) unit * 0.90f else unit * LONG_FALL

    for (i in 0 until count) {
        val stone = DROPS[i]
        val travel = (stone.phase + progress * stone.speed * HAIL_FAST) % 1f
        camera.place(stone.x * spreadX, top + travel * span, stone.z * spreadZ)
        val at = Offset(camera.sx, camera.sy)
        val near = camera.scale
        val size = (unit * HAIL_SIZE * near).coerceAtLeast(1.8f)

        val surface = skyline?.topAt(at.x - shift.x)?.let { it + shift.y } ?: Float.NaN
        val onDigit = !surface.isNaN() && at.y >= surface
        impacts?.mark(i, onDigit)

        val rest = when {
            onDigit -> surface
            !ground.isNaN() && at.y >= ground -> ground
            else -> Float.NaN
        }

        if (rest.isNaN()) {
            hailstone(at, size, colour, 1f)
            continue
        }

        // Rimbalzato. La quota la fa un arco, lo scostamento di lato e' sempre
        // dalla stessa parte per lo stesso chicco - un rimbalzo che cambia
        // direzione a ogni fotogramma e' un tremolio, non un rimbalzo.
        val hop = ((at.y - rest) / (unit * HAIL_BOUNCE)).coerceIn(0f, 1f)
        if (hop >= 1f) continue
        val side = if ((i and 1) == 0) 1f else -1f
        val lift = sin(hop * PI_F) * unit * HAIL_HOP * near
        hailstone(
            Offset(at.x + side * lift * 0.55f, rest - lift),
            size * (1f - hop * 0.35f),
            colour,
            (1f - hop) * (1f - hop),
        )
    }
}

/** Un chicco: tondo e con un lembo piu' chiaro, se no e' un pallino grigio. */
private fun DrawScope.hailstone(at: Offset, size: Float, colour: Color, alpha: Float) {
    if (alpha <= 0.01f) return
    drawCircle(color = colour.copy(alpha = 0.92f * alpha), radius = size, center = at)
    drawCircle(
        color = Color.White.copy(alpha = 0.45f * alpha),
        radius = size * 0.42f,
        center = Offset(at.x - size * 0.28f, at.y - size * 0.30f),
    )
}

/**
 * La neve: fiocchi, non righe.
 *
 * Finora nevicare voleva dire piovere - la famiglia NEVE conta come bagnata e
 * finiva nello stesso disegno - e sotto la scritta NEVE cadevano trattini
 * verticali. Un fiocco pero' non e' una goccia corta: e' un corpo che scende
 * **piano** e non in linea retta, perche' pesa poco e l'aria se lo passa. Sono
 * quelle due cose - la lentezza e lo sbandamento - a farlo leggere come neve, e
 * nessuna quantita' di bianco puo' sostituirle.
 *
 * Riusa le stesse posizioni della pioggia: sono gia' distribuite sotto la
 * nuvola e ruotano con lei. Cambia come ci si muove sopra.
 *
 * Chi tocca la cifra si posa invece di schizzare, e resta un attimo prima di
 * sparire - la neve si ferma dove arriva, l'acqua no.
 */
private fun DrawScope.drawSnow(
    camera: Camera,
    unit: Float,
    scale: Float,
    wetness: Float,
    progress: Float,
    colour: Color,
    contact: SceneContact?,
    impacts: RainImpacts? = null,
) {
    val count = (DROPS.size * wetness).roundToInt().coerceIn(4, DROPS.size)
    for (i in count until DROPS.size) impacts?.mark(i, false)

    val spreadX = unit * 0.44f * scale
    val spreadZ = unit * 0.17f * scale
    val top = unit * 0.16f * scale
    val shift = contact?.numberToRain ?: Offset.Zero
    val skyline = contact?.skyline
    val ground = skyline?.floor?.takeIf { !it.isNaN() }?.let { it + shift.y } ?: Float.NaN
    val span = if (ground.isNaN()) unit * 0.90f else unit * LONG_FALL

    for (i in 0 until count) {
        val flake = DROPS[i]
        // Piu' lenta della pioggia, e ogni fiocco col proprio passo.
        val travel = (flake.phase + progress * flake.speed * SNOW_SLOW) % 1f
        // Lo sbandamento: un pendolo, con la fase presa dal fiocco stesso cosi'
        // non oscillano tutti insieme - che sarebbe una tendina che ondeggia,
        // non neve.
        val sway = sin(travel * SNOW_TURNS + flake.phase * PI_F * 2f) * SNOW_SWAY
        val y = top + travel * span
        camera.place((flake.x + sway) * spreadX, y, flake.z * spreadZ)
        val at = Offset(camera.sx, camera.sy)
        val near = camera.scale
        val size = (unit * SNOW_SIZE * near).coerceAtLeast(1.6f)
        val alpha = (travel / 0.08f).coerceAtMost(1f)

        val surface = skyline?.topAt(at.x - shift.x)?.let { it + shift.y } ?: Float.NaN
        val landed = !surface.isNaN() && at.y >= surface
        impacts?.mark(i, landed)

        val rest = when {
            landed -> surface
            !ground.isNaN() && at.y >= ground -> ground
            else -> Float.NaN
        }

        if (rest.isNaN()) {
            drawCircle(color = colour.copy(alpha = alpha * 0.92f), radius = size, center = at)
            continue
        }

        // Posato: si schiaccia un po' e svanisce dov'e' arrivato.
        val age = ((at.y - rest) / (unit * SNOW_REST)).coerceIn(0f, 1f)
        if (age >= 1f) continue
        val wide = size * (1f + age * 1.5f)
        drawOval(
            color = colour.copy(alpha = alpha * 0.85f * (1f - age) * (1f - age)),
            topLeft = Offset(at.x - wide, rest - size * 0.55f),
            size = Size(wide * 2f, size * 1.1f),
        )
    }
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
    impacts: RainImpacts? = null,
) {
    val count = (DROPS.size * wetness).roundToInt().coerceIn(3, DROPS.size)
    // Le gocce che la pioggia ha smesso di disegnare non stanno toccando
    // niente: senza dimenticarle, al primo rovescio dopo una pioviggine
    // risulterebbero tutte arrivate insieme.
    for (i in count until DROPS.size) impacts?.mark(i, false)
    val spreadX = unit * 0.40f * scale
    val spreadZ = unit * 0.17f * scale
    val top = unit * 0.16f * scale
    val width = unit * 0.013f
    val shift = contact?.numberToRain ?: Offset.Zero
    val skyline = contact?.skyline

    // **Dove finisce la caduta.** La base della cifra, quando si sa dov'e'.
    //
    // Compose non ritaglia ai bordi del riquadro - non e' una View - quindi la
    // pioggia poteva gia' scendere sotto la scultura: si fermava a mezz'aria
    // perche' la corsa era lunga novanta centesimi di unita' e basta. Adesso,
    // se la sagoma ha detto dove appoggia, la corsa arriva fin li'.
    val ground = skyline?.floor?.takeIf { !it.isNaN() }?.let { it + shift.y } ?: Float.NaN
    val span = if (ground.isNaN()) unit * 0.90f else unit * LONG_FALL


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
        impacts?.mark(i, !surface.isNaN() && head.y >= surface)

        if (surface.isNaN() || head.y < surface) {
            if (!ground.isNaN() && head.y >= ground) {
                // **Arrivata a terra.** Si allarga e sparisce: una pozzanghera
                // che resta e' una macchia, una che si spande e si asciuga e'
                // acqua. Quanto e' vecchia lo dice quanto la goccia e' andata
                // oltre la base - il tempo sta gia' nella posizione, come per
                // lo schizzo, e non c'e' niente da ricordare fra un fotogramma
                // e l'altro.
                val age = ((head.y - ground) / (unit * PUDDLE_LIFE)).coerceIn(0f, 1f)
                if (age < 1f) puddle(Offset(head.x, ground), stroke, age, colour, alpha)
                continue
            }
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
        // **E poi si spegne.** Con la corsa corta la goccia toccava in fondo al
        // giro e lo schizzo faceva in tempo appena a vedersi. Adesso tocca molto
        // prima, e senza questo si fermerebbe li' identico per mezzo ciclo - una
        // macchia ferma sulla cifra, non uno schizzo.
        val spent = ((head.y - surface) / (unit * SPLASH_LIFE)).coerceIn(0f, 1f)
        if (spent >= 1f) continue
        val fading = alpha * (1f - spent) * (1f - spent)
        if (tail.y < stop) {
            drawLine(
                color = colour.copy(alpha = fading),
                start = tail,
                end = Offset(head.x, stop),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        splash(Offset(head.x, surface), stroke, sunk, colour, fading)
    }
}

/**
 * Una mini-pozzanghera alla base della cifra.
 *
 * Un'ellisse molto schiacciata, non un cerchio: il piano d'appoggio si guarda
 * di sbieco, e un cerchio pieno li' si legge come una pallina appoggiata a
 * terra invece che come acqua distesa.
 *
 * Si allarga mentre si smorza, e la smorzatura va al quadrato: l'acqua non
 * evapora a velocita' costante, sparisce in fretta sul finire. Con una
 * dissolvenza lineare si vedeva un disco grigio restare li' troppo a lungo.
 */
private fun DrawScope.puddle(
    at: Offset,
    stroke: Float,
    age: Float,
    colour: Color,
    alpha: Float,
) {
    val half = stroke * (1.1f + age * 5.0f)
    val tall = half * 0.30f
    drawOval(
        color = colour.copy(alpha = alpha * 0.45f * (1f - age) * (1f - age)),
        topLeft = Offset(at.x - half, at.y - tall * 0.5f),
        size = Size(half * 2f, tall),
    )
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

/**
 * Una stella: dove sta **sullo schermo**, quanto e' grande, quanto brilla.
 *
 * Niente profondita': il cielo non passa dalla camera, quindi una terza
 * coordinata non avrebbe niente che la legga.
 */
private class Star(val x: Float, val y: Float, val size: Float, val glow: Float)

/**
 * Il cielo stellato. Sparse a mano e non a caso: attorno all'astro il cielo e'
 * piu' vuoto, se no le stelle gli finiscono addosso e si leggono come sporco.
 *
 * **L'ordine conta**: se ne accende un prefisso, quindi le prime sono le piu'
 * luminose. Sul grigio compaiono quelle che si vedrebbero davvero per prime, e
 * le fioche arrivano col buio.
 */
private val STARS: List<Star> = listOf(
    Star(-0.62f, -0.52f, 0.011f, 1.00f),
    Star(0.06f, -0.66f, 0.011f, 1.00f),
    Star(-0.66f, -0.16f, 0.010f, 0.95f),
    Star(0.58f, -0.60f, 0.010f, 0.92f),
    Star(-0.20f, -0.44f, 0.009f, 0.88f),
    Star(0.52f, 0.02f, 0.009f, 0.85f),
    Star(-0.44f, -0.70f, 0.008f, 0.80f),
    Star(0.30f, -0.78f, 0.008f, 0.78f),
    Star(-0.34f, -0.05f, 0.007f, 0.72f),
    Star(0.68f, -0.28f, 0.007f, 0.70f),
    Star(-0.78f, -0.62f, 0.007f, 0.68f),
    Star(0.14f, -0.92f, 0.006f, 0.62f),
    Star(-0.08f, -0.86f, 0.006f, 0.60f),
    Star(0.76f, -0.74f, 0.006f, 0.58f),
    Star(-0.52f, -0.90f, 0.006f, 0.55f),
    Star(0.40f, -0.36f, 0.005f, 0.52f),
    Star(0.22f, -0.20f, 0.005f, 0.50f),
    Star(-0.24f, -0.74f, 0.005f, 0.48f),
    Star(0.86f, -0.44f, 0.005f, 0.46f),
    Star(-0.88f, -0.34f, 0.005f, 0.44f),
    Star(0.62f, -0.90f, 0.004f, 0.42f),
    Star(-0.14f, -0.28f, 0.004f, 0.40f),
    Star(0.34f, -0.56f, 0.004f, 0.38f),
    Star(-0.70f, -0.78f, 0.004f, 0.36f),
    Star(0.02f, -0.40f, 0.004f, 0.34f),
    Star(-0.42f, -0.24f, 0.004f, 0.32f),

    // **La coda del buio pieno.** Da qui in giu' sono le stelle che si accendono
    // solo quando la notte e' proprio notte: il conto e' quadratico, quindi
    // arrivano tutte insieme nell'ultimo tratto invece di comparire una alla
    // volta al calare del sole. Sono anche le piu' lontane dalla scultura, cosi'
    // il cielo si riempie ai bordi mentre il centro resta libero per l'astro.
    Star(-0.94f, -0.08f, 0.004f, 0.30f),
    Star(0.92f, -0.14f, 0.004f, 0.29f),
    Star(-0.72f, -0.42f, 0.004f, 0.28f),
    Star(0.80f, -0.58f, 0.004f, 0.27f),
    Star(-0.06f, -0.98f, 0.003f, 0.26f),
    Star(0.44f, -0.96f, 0.003f, 0.25f),
    Star(-0.36f, -0.98f, 0.003f, 0.24f),
    Star(0.96f, -0.82f, 0.003f, 0.23f),
    Star(-0.96f, -0.76f, 0.003f, 0.22f),
    Star(0.34f, -0.62f, 0.003f, 0.21f),
    Star(-0.18f, -0.60f, 0.003f, 0.20f),
    Star(0.70f, -0.06f, 0.003f, 0.19f),
    Star(-0.60f, 0.06f, 0.003f, 0.18f),
    Star(0.10f, -0.12f, 0.003f, 0.17f),
    Star(-0.86f, -0.94f, 0.003f, 0.16f),
    Star(0.88f, -0.98f, 0.003f, 0.16f),
    Star(0.24f, -0.50f, 0.003f, 0.15f),
    Star(-0.50f, -0.60f, 0.003f, 0.15f),
)

/** Quanto deriva una massa della nuvola, in frazioni di unita'. */
/**
 * Le stelle cadenti.
 *
 * [SHOOTING_DARK] e' la soglia di buio sotto la quale non ne cade nessuna: sul
 * grigio del crepuscolo una scia non si vedrebbe e comunque non c'entra, e'
 * roba da notte fonda. [SHOOTING_PERIOD] sono i secondi fra un tentativo e
 * l'altro e [SHOOTING_SPAN] la frazione di quel tempo in cui la caduta si vede
 * davvero: il prodotto dei due fa quanto dura, il resto e' attesa.
 *
 * Rade apposta. Una pioggia di meteore sopra la temperatura di domani non e'
 * atmosfera, e' un salvaschermo.
 */
/**
 * Quanto e' lunga la caduta quando si sa dove appoggia, in unita'.
 *
 * Non e' un numero estetico: e' quanto serve perche' una goccia che passa
 * accanto alla cifra arrivi alla sua base invece di fermarsi a mezz'aria. Se un
 * giorno la disposizione cambiasse le proporzioni fra scultura e cifra, questo
 * va rimisurato - non indovinato.
 */
/**
 * La neve.
 *
 * [SNOW_SLOW] e' quanto va piu' piano della pioggia, [SNOW_SWAY] quanto sbanda
 * di lato in frazione della larghezza, [SNOW_TURNS] quante oscillazioni fa in
 * una discesa. Sono i tre numeri che decidono se si legge neve o coriandoli:
 * sbandamento troppo largo o troppo veloce e diventano farfalle.
 */
/** Battiti d'ala al secondo, in radianti: sotto sembrano alianti, sopra insetti. */
/**
 * Il coperto: quanto si allarga la fila oltre i bordi e quanto respira.
 *
 * Il respiro e' volutamente piccolo. Serve a togliere il profilo, non a farsi
 * notare: a occhio non si deve vedere una nuvola che pulsa, si deve solo non
 * riuscire a dire dove finisce.
 */
private const val OVERCAST_SPREAD = 0.55f
private const val BREATH = 0.07f

/**
 * La grandine. [HAIL_FAST] e' quanto va piu' svelta della pioggia, [HAIL_HOP]
 * quanto rimbalza in frazione di unita', [HAIL_BOUNCE] quanto dura il rimbalzo.
 */
private const val HAIL_FAST = 1.55f
private const val HAIL_SIZE = 0.013f
private const val HAIL_HOP = 0.055f
private const val HAIL_BOUNCE = 0.26f

private const val BIRD_BEAT = 4.2f

private const val SNOW_SLOW = 0.34f
private const val SNOW_SWAY = 0.16f
private const val SNOW_TURNS = 7.5f
private const val SNOW_SIZE = 0.011f

/** Quanto resta un fiocco posato prima di sparire, in unita' di caduta. */
private const val SNOW_REST = 0.30f

private const val LONG_FALL = 2.6f

/** Quanto vive una pozzanghera e quanto uno schizzo, in unita' di caduta. */
private const val PUDDLE_LIFE = 0.34f
private const val SPLASH_LIFE = 0.16f

private const val SHOOTING_DARK = 0.80f
private const val SHOOTING_PERIOD = 12f
private const val SHOOTING_SPAN = 0.07f

/** Quanto e' lunga la corsa e quanto la scia che si trascina dietro. */
private const val SHOOTING_LENGTH = 1.45f
private const val SHOOTING_TAIL = 0.32f

/** L'inclinazione della caduta, in radianti: sempre verso il basso, mai a piombo. */
private const val SHOOTING_TILT_MIN = 0.42f
private const val SHOOTING_TILT_MAX = 0.95f

/**
 * Il numero di Knuth per la miscelazione. Serve a far sembrare diverse due
 * cadute consecutive partendo solo dal numero del ciclo, senza tenere stato.
 */
private const val HASH_MIX = -1640531535

private const val PI_F = kotlin.math.PI.toFloat()

private const val DRIFT = 0.022f

private const val FALL_CYCLE_MS = 1400L
private const val TILT_YAW = 7f
private const val TILT_PITCH = 5f

/** Il posto dell'astro nella fila dei corpi tondi: non e' una massa di nuvola. */
private const val ASTRO = -1

/**
 * Quanto deve passare, come minimo, fra un colpetto e il successivo.
 *
 * Un decimo di secondo. Sotto, in un rovescio, il vibratore non stacca piu' e
 * quello che dovrebbe leggersi come pioggia si legge come un ronzio; sopra, si
 * perde il legame fra la goccia che si vede arrivare e quella che si sente.
 */
private const val TAP_GAP_NS = 110_000_000L
