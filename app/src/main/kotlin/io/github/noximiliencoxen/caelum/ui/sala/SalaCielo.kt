package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.data.SunClock
import io.github.noximiliencoxen.caelum.ui.motion.rememberDeviceTilt
import io.github.noximiliencoxen.caelum.ui.motion.rememberVibrazioniMeteo
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Il cielo di Caelum: sfumatura, arco del sole, nuvole, colline, cio' che cade.
 *
 * **Sta dietro tutte e sette le sale, una volta sola.** Nella versione
 * precedente ogni sala si disegnava il proprio fondo, perche' quel fondo era
 * una carta e una carta e' della pagina che la usa. Qui il fondo e' il tempo:
 * scorrendo il carosello il cielo non deve ricominciare, deve **restare**,
 * mentre i pannelli gli passano davanti. Per questo il disegno e' salito nella
 * Shell e le sale sono diventate pannelli trasparenti.
 *
 * Tutto e' misurato nel sistema del prototipo, 411 x 914 - che sono anche i
 * punti Android di un telefono di riferimento. Le **posizioni** si riscalano
 * sui due assi separatamente, cosi' la scena occupa lo schermo qualunque sia
 * la proporzione; i **diametri** seguono la sola larghezza, perche' un disco
 * riscalato su due assi diversi non e' piu' un disco.
 *
 * Niente qui dentro ramifica sul tempo che fa: arriva [Scena], sette numeri fra
 * 0 e 1 gia' passati per le loro molle, e ogni "se piove" e' una mescola. E'
 * la stessa regola della scultura che questo file sostituisce.
 */
@Composable
fun SalaCielo(
    palette: SalaPalette,
    /** Le tre fermate della sfumatura, gia' mescolate fra le fasi e animate. */
    stops: List<Color>,
    scena: Scena,
    sky: SkyState,
    /** La fase lunare vera, 0 novilunio e 0,5 plenilunio. La stessa di Sala IV:
     *  le due schermate non possono raccontare due lune diverse. */
    faseLunare: Float,
    tempo: () -> Float,
    modifier: Modifier = Modifier,
    /**
     * Quanto scendere per stare sotto la barra di stato.
     *
     * La sfumatura e le colline no - quelle vanno da bordo a bordo e si
     * appoggiano al fondo - ma **l'arco, il sole, la luna e le nuvole si'**: il
     * prototipo li misura dentro la cornice dell'app, che li' comincia a zero e
     * su un telefono vero comincia sotto la barra di stato. Senza questo la
     * luna esce dietro la pastiglia degli avvisi, che e' esattamente dove il
     * disegno **non** la mette.
     */
    insetAlto: Dp = 0.dp,
    /**
     * Se il cielo risponde al dito e al sensore.
     *
     * Falso quando le animazioni sono ridotte - li' un accelerometro acceso e'
     * un costo che chi ha chiesto meno movimento non si aspetta - e durante la
     * cattura, dove ogni scatto deve poter uscire identico al precedente.
     */
    interattivo: Boolean = true,
) {
    // ── Cio' che il cielo si tiene per se' ───────────────────────────────────
    //
    // Tocchi, fiammata e inclinazione vivono **qui** e non nella Shell: sono
    // cose del cielo, nessun'altra schermata le usa, e tenerle qui vuol dire
    // che la Shell non sa nemmeno che esistano.
    val onde = remember { mutableStateListOf<Increspatura>() }
    val fiamma = remember { Animatable(0f) }
    val vibrazioni = rememberVibrazioniMeteo()
    val scope = rememberCoroutineScope()

    // **Da -1 a 1 su ogni asse**, gia' smorzato e con la linea di base che
    // insegue la posa: restituisce al centro se si resta fermi, quindi non
    // deriva. Era in `ui/motion/` e non lo chiamava piu' nessuno da quando il
    // mappamondo del benvenuto e' uscito.
    val inclinazione by rememberDeviceTilt(enabled = interattivo)
    val ampiezza = with(LocalDensity.current) { 16.dp.toPx() }
    val parallasse = Offset(inclinazione.x * ampiezza, inclinazione.y * ampiezza)

    Canvas(
        modifier = modifier.pointerInput(interattivo) {
            if (!interattivo) return@pointerInput
            detectTapGestures { punto ->
                val ora = tempo()
                // Il disco sta dove lo mette l'arco: stesso conto del disegno.
                val centro = arco(
                    t = sky.journey,
                    sx = size.width / RIF_L,
                    sy = size.height / RIF_H,
                    dy = insetAlto.toPx(),
                )
                val raggioDisco = 38f * (size.width / RIF_L) * 1.7f
                if ((punto - centro).getDistance() <= raggioDisco) {
                    // Sul disco: divampa, e si sente.
                    vibrazioni.scatto()
                    scope.launch {
                        fiamma.snapTo(1f)
                        fiamma.animateTo(0f, tween(durationMillis = 1100))
                    }
                } else {
                    // **Si potano le spente prima di aggiungere.** Una lista che
                    // cresce a ogni tocco e non cala e' una perdita lenta: il
                    // disegno le salterebbe comunque, ma resterebbero in memoria
                    // per tutta la vita della schermata.
                    onde.removeAll { ora - it.nata > DURATA_INCRESPATURA }
                    if (onde.size < 4) onde.add(Increspatura(punto, ora))
                }
            }
        },
    ) {
        val t = tempo()
        val sx = size.width / RIF_L
        val sy = size.height / RIF_H
        val dy = insetAlto.toPx()

        drawRect(brush = cieloBrush(stops))

        // Le stelle non si spengono con le nuvole: ci passano sotto. Il velo
        // **cala** con la copertura invece di azzerarsi, perche' da sotto una
        // notte coperta qualcuna si vede lo stesso.
        //
        // Le piu' lontane si spostano pochissimo: sono il fondo del cielo.
        translate(parallasse.x * 0.12f, parallasse.y * 0.12f) {
            cieloStellato(
                tempo = t,
                inchiostro = SalaTokens.neutral100,
                velo = scena.notte * (1f - scena.copertura * 0.72f),
            )
        }

        translate(parallasse.x * 0.34f, parallasse.y * 0.34f) {
            arcoDelCielo(sx, sy, dy, palette, scena)
            soleEluna(sx, sy, dy, sky, scena, faseLunare, t, fiamma.value)
        }

        // Di giorno, a cielo aperto, qualcosa attraversa: senza, un sereno e'
        // una sfumatura ferma. Il velo cala con le nuvole invece di spegnersi.
        val giorno = (1f - scena.notte) * (1f - scena.bagnato)
        translate(parallasse.x * 0.52f, parallasse.y * 0.52f) {
            pulviscolo(
                tempo = t,
                inchiostro = SalaTokens.neutral100,
                velo = giorno * (1f - scena.copertura * 0.55f) * 0.9f,
            )
            uccelli(
                unita = size.width * 0.55f,
                origine = Offset(size.width * 0.5f, size.height * 0.40f + dy),
                tempo = t,
                inchiostro = palette.ink,
                velo = giorno * (1f - scena.copertura * 0.62f),
            )
        }

        // Le nuvole sono il piano di mezzo, e si spostano piu' di tutto.
        translate(parallasse.x * 0.78f, parallasse.y * 0.78f) {
            nuvole(sx, sy, dy, palette, scena, sky, t)
        }

        // Le colline sono terra: stanno ferme, o il mondo si stacca dai piedi.
        colline(sx, sy, palette)
        cioCheCade(scena, t)

        disegnaIncrespature(onde, t, palette)

        // Il fulmine si prende il cielo intero - un temporale non illumina
        // solo la nuvola che lo fa - ed e' l'ultimo disegnato: la sua luce
        // passa sopra le nuvole, le colline e cio' che cade, come fa la luce.
        fulmine(t, scena.tempesta)
    }
}

private const val RIF_L = 411f
private const val RIF_H = 914f

/** Il centro e il raggio dell'arco su cui viaggiano sole e luna. */
private const val ARCO_CX = 206f
private const val ARCO_CY = 288f
private const val ARCO_R = 170f

/**
 * Lo stesso disordine deterministico del prototipo (`rnd`), perche' nuvole e
 * gocce cadano dove cadevano li'. Deterministico e non casuale: un cielo che
 * si ridispone a ogni ricomposizione non e' un cielo, e' rumore.
 */
private fun rnd(i: Int): Float {
    // **In doppia precisione, e non e' un dettaglio.** `fract(sin(x) * 43758.5)`
    // e' l'inganno piu' sensibile alla precisione che ci sia: con i `Float` il
    // risultato non somiglia nemmeno da lontano a quello del prototipo, e le
    // nuvole finiscono dove nessuno le ha viste. Il disordine deve essere lo
    // **stesso** che il disegno mostrava, non un disordine qualsiasi.
    val x = sin(i * 127.1 + 311.7) * 43758.5453
    return (x - floor(x)).toFloat()
}

/** Il punto dell'arco a frazione [t], da 0 (sorge a sinistra) a 1 (cala a destra). */
private fun arco(t: Float, sx: Float, sy: Float, dy: Float): Offset {
    val a = PI.toFloat() * (1f - t.coerceIn(0f, 1f))
    return Offset((ARCO_CX + ARCO_R * cos(a)) * sx, (ARCO_CY - ARCO_R * sin(a)) * sy + dy)
}

/**
 * L'arco tratteggiato: la strada che il sole percorre in giornata.
 *
 * Si smorza col cielo chiuso (nel prototipo scende a un quarto): sotto un
 * fronte non si vede da nessuna parte dove sia il sole, e disegnarne la strada
 * a piena forza sarebbe dire una cosa che il cielo non dice.
 */
private fun DrawScope.arcoDelCielo(sx: Float, sy: Float, dy: Float, palette: SalaPalette, scena: Scena) {
    val forza = lerp(1f, 0.25f, scena.copertura) * (1f - scena.tempesta * 0.6f)
    if (forza <= 0.02f) return
    val strada = Path().apply {
        val passi = 48
        for (i in 0..passi) {
            val p = arco(i / passi.toFloat(), sx, sy, dy)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
    }
    drawPath(
        path = strada,
        color = palette.ink.copy(alpha = 0.26f * forza),
        style = Stroke(
            width = 1.6f * sx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * sx, 7f * sx)),
        ),
    )
}

/**
 * Il sole e la luna, sullo stesso arco e con pesi complementari.
 *
 * **Il colore del sole segue l'altezza, e non e' un vezzo.** All'orizzonte e'
 * un arancione bruciato, allo zenit un giallo acceso, e in mezzo ci passa ora
 * per ora: e' cio' che fa la luce vera, ed e' la richiesta con cui questa
 * passata e' cominciata. Un `if (alba)` non saprebbe dirlo.
 */
private fun DrawScope.soleEluna(
    sx: Float,
    sy: Float,
    dy: Float,
    sky: SkyState,
    scena: Scena,
    faseLunare: Float,
    tempo: Float,
    fiamma: Float,
) {
    // Il cielo chiuso li nasconde tutti e due: dietro un fronte non si vede
    // ne' l'uno ne' l'altra.
    val velo = (1f - scena.copertura * 0.92f).coerceIn(0f, 1f) * (1f - scena.neve * 0.8f)
    if (velo <= 0.01f) return

    val posizione = arco(sky.journey, sx, sy, dy)

    // **Disco e alone respirano sfasati.** All'unisono sembrerebbero un solo
    // oggetto che cambia taglia; sfalsati sembrano luce che varia.
    val respiro = 1f + 0.06f * sin(tempo * 0.78f)
    val respiroAlone = 1f + 0.11f * sin(tempo * 0.53f + 1.2f)

    if (scena.sole > 0.01f) {
        val elev = sin(PI.toFloat() * sky.journey.coerceIn(0f, 1f))
        // Piu' caldo e piu' acceso di prima: il cuore va quasi al bianco allo
        // zenit, cosi' il disco brucia invece di posarsi.
        val cuore = lerp(Color(0xFFFFF3DA), Color(0xFFFFFFF2), elev)
        val mezzo = lerp(Color(0xFFF79A46), Color(0xFFFFDC3A), elev)
        val bordo = lerp(Color(0xFFD86A2C), Color(0xFFFFB01C), elev)
        val alone = lerp(Color(0xFFFFB98C), Color(0xFFFFEE9C), elev)
        val r = 38f * sx * respiro
        val forza = (scena.sole * velo).coerceIn(0f, 1f)

        // ── Il bagliore, in tre strati ────────────────────────────────────
        //
        // Uno solo dava un alone piatto che finiva di colpo. Tre raggi diversi,
        // ognuno piu' largo e piu' tenue, danno la caduta continua che ha la
        // luce vera - ed e' quello che fa "bruciare" il disco sul cielo.
        listOf(
            5.6f to 0.20f,
            3.1f to 0.30f,
            1.9f to 0.42f,
        ).forEach { (quanto, opacita) ->
            val raggio = r * quanto * respiroAlone * (1f + 0.25f * fiamma)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to alone.copy(alpha = opacita * forza * (1f + 0.6f * fiamma)),
                    1f to alone.copy(alpha = 0f),
                    center = posizione,
                    radius = raggio,
                ),
                radius = raggio,
                center = posizione,
            )
        }

        // ── La corona, che gira ───────────────────────────────────────────
        //
        // **E' questa a dare il movimento che mancava.** Il respiro da solo e'
        // una pulsazione: si nota dopo un minuto e poi non piu'. Un giro lento
        // - novanta secondi per tornare al punto di partenza - non si guarda
        // mai partire e non finisce mai, che e' come si comporta il sole.
        val raggi = 16
        val giro = tempo * (2f * PI.toFloat() / 90f)
        for (k in 0 until raggi) {
            val a = giro + k * (2f * PI.toFloat() / raggi)
            // Due lunghezze alternate, e ognuna respira per conto suo: una
            // corona a denti uguali si legge come un ingranaggio.
            val lungo = if (k % 2 == 0) 1f else 0.62f
            val palpito = 1f + 0.18f * sin(tempo * 1.6f + k * 0.8f)
            val dentro = r * 1.12f
            val fuori = dentro + r * 0.55f * lungo * palpito * (1f + 1.4f * fiamma)
            drawLine(
                brush = Brush.linearGradient(
                    0f to alone.copy(alpha = 0.55f * forza * (1f + fiamma)),
                    1f to alone.copy(alpha = 0f),
                    start = Offset(posizione.x + cos(a) * dentro, posizione.y + sin(a) * dentro),
                    end = Offset(posizione.x + cos(a) * fuori, posizione.y + sin(a) * fuori),
                ),
                start = Offset(posizione.x + cos(a) * dentro, posizione.y + sin(a) * dentro),
                end = Offset(posizione.x + cos(a) * fuori, posizione.y + sin(a) * fuori),
                strokeWidth = r * 0.085f,
                cap = StrokeCap.Round,
            )
        }

        // ── Il disco ──────────────────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                0f to cuore,
                0.52f to mezzo,
                1f to bordo,
                // Il fuoco spostato in alto a sinistra: e' quello che fa
                // sembrare il disco una sfera e non un bollo.
                center = Offset(posizione.x - r * 0.24f, posizione.y - r * 0.32f),
                radius = r * 1.35f,
            ),
            radius = r,
            center = posizione,
            alpha = forza,
        )
    }

    if (scena.notte > 0.01f) {
        val r = 35f * sx
        val alpha = (scena.notte * velo).coerceIn(0f, 1f)
        // Anche la luna ha il suo bagliore a due strati, e cresce col tocco.
        listOf(3.4f to 0.16f, 2.0f to 0.24f).forEach { (quanto, opacita) ->
            val raggio = r * quanto * respiroAlone * (1f + 0.22f * fiamma)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to SalaTokens.lunaLuce.copy(alpha = opacita * alpha * (1f + 0.8f * fiamma)),
                    1f to SalaTokens.lunaLuce.copy(alpha = 0f),
                    center = posizione,
                    radius = raggio,
                ),
                radius = raggio,
                center = posizione,
            )
        }
        luna(posizione, r, faseLunare, alpha, tempo)
    }
}

/**
 * La luna: corpo pieno coi suoi mari, e sopra la sola parte illuminata.
 *
 * **La fase e' quella vera del giorno mostrato**, non un disegno fisso: il
 * prototipo aveva una gibbosa al settantaquattro per cento scritta a mano, e
 * una luna che non corrisponde a quella in cielo e' un'affermazione falsa detta
 * con disinvoltura. La costruzione della sagoma e' la stessa di Sala IV e di
 * `Bodies.kt`: semicerchio dal lato illuminato piu' la mediana, che rientra
 * quando e' falce e sporge quando e' gibbosa.
 */
private fun DrawScope.luna(centro: Offset, r: Float, fase: Float, alpha: Float, tempo: Float) {
    // **La luce cinerea.** La parte in ombra non e' nera: e' Terra che la
    // illumina, e a occhio nudo si vede eccome - e' quel disco fantasma dentro
    // la falce. Prima era quasi nera, e al novilunio la luna spariva dal cielo
    // come se qualcuno l'avesse spenta.
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFF2A3446).copy(alpha = 0.92f * alpha),
            1f to Color(0xFF141C28).copy(alpha = 0.80f * alpha),
            center = Offset(centro.x - r * 0.2f, centro.y - r * 0.2f),
            radius = r * 1.3f,
        ),
        radius = r,
        center = centro,
    )

    val crescente = fase < 0.5f
    val terminatore = abs(cos(2.0 * PI * fase).toFloat())
    val gibbosa = ((1f - cos(2.0 * PI * fase).toFloat()) / 2f) > 0.5f
    val disco = Rect(centro.x - r, centro.y - r, centro.x + r, centro.y + r)
    val mediana = Rect(centro.x - r * terminatore, centro.y - r, centro.x + r * terminatore, centro.y + r)
    val illuminata = Path().apply {
        arcTo(disco, if (crescente) -90f else 90f, 180f, true)
        arcTo(mediana, if (crescente) 90f else -90f, if (gibbosa) 180f else -180f, false)
        close()
    }

    clipPath(illuminata) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFFFFDF6),
                0.48f to SalaTokens.lunaLuce,
                1f to SalaTokens.lunaMezzo,
                center = Offset(centro.x - r * 0.32f, centro.y - r * 0.40f),
                radius = r * 1.5f,
            ),
            radius = r,
            center = centro,
            alpha = alpha,
        )
        // I mari, alle stesse quote del prototipo. Restano dentro la parte
        // illuminata: un mare che si vedesse sull'ombra sarebbe una macchia.
        val mari = listOf(
            Triple(-0.07f, -0.06f, 0.20f),
            Triple(0.24f, -0.23f, 0.14f),
            Triple(0.07f, 0.27f, 0.24f),
        )
        mari.forEach { (mx, my, md) ->
            drawOval(
                color = Color(0xFF6E6152).copy(alpha = 0.20f * alpha),
                topLeft = Offset(centro.x + mx * r - md * r, centro.y + my * r - md * r * 0.78f),
                size = Size(md * 2f * r, md * 1.56f * r),
            )
        }
    }

    // Un filo di contorno, perche' la sfera si stacchi anche da un cielo chiaro.
    drawCircle(
        color = SalaTokens.lunaBordo.copy(alpha = 0.30f * alpha),
        radius = r,
        center = centro,
        style = Stroke(width = r * 0.04f),
    )

    // **Tre scintille che le girano attorno**, lente e sfasate. Non e'
    // astronomia: e' il segno con cui si disegna "brilla" da sempre, e a questa
    // taglia fa piu' la luna di quanto farebbe un altro alone.
    for (k in 0 until 3) {
        val a = tempo * 0.22f + k * (2f * PI.toFloat() / 3f)
        val d = r * (1.55f + 0.12f * sin(tempo * 0.7f + k))
        val p = Offset(centro.x + cos(a) * d, centro.y + sin(a) * d * 0.82f)
        val pulsa = (0.35f + 0.65f * sin(tempo * 1.3f + k * 2.1f)).coerceIn(0f, 1f)
        val punta = r * 0.16f * pulsa
        if (punta <= 0.4f) continue
        listOf(Offset(punta, 0f), Offset(0f, punta)).forEach { v ->
            drawLine(
                color = SalaTokens.lunaLuce.copy(alpha = 0.55f * alpha * pulsa),
                start = Offset(p.x - v.x, p.y - v.y),
                end = Offset(p.x + v.x, p.y + v.y),
                strokeWidth = r * 0.035f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Un'increspatura nata da un dito sul cielo.
 *
 * Porta **quando** e' nata e non quanto e' vecchia: l'eta' si ricava
 * dall'orologio della scena al momento del disegno, cosi' non c'e' un secondo
 * contatore da tenere in fase col primo - e' la stessa ragione per cui le
 * gocce e le vibrazioni condividono `Corsie`.
 */
@Immutable
data class Increspatura(val centro: Offset, val nata: Float)

/** Quanto dura un'increspatura, dal tocco allo svanire. */
const val DURATA_INCRESPATURA = 1.15f

/**
 * Le increspature del tocco: un anello di luce che si allarga e svanisce.
 *
 * Due anelli e non uno - uno largo e tenue, uno stretto e netto - perche' un
 * cerchio solo che cresce si legge come un bersaglio, due come un'onda.
 */
private fun DrawScope.disegnaIncrespature(
    onde: List<Increspatura>,
    tempo: Float,
    palette: SalaPalette,
) {
    onde.forEach { onda ->
        val eta = ((tempo - onda.nata) / DURATA_INCRESPATURA).coerceIn(0f, 1f)
        if (eta >= 1f) return@forEach
        // Si allarga in fretta e rallenta: e' come si apre un'onda sull'acqua.
        val raggio = size.width * 0.52f * (1f - (1f - eta) * (1f - eta))
        val svanire = (1f - eta) * (1f - eta)
        val tinta = lerp(palette.accent, SalaTokens.neutral100, palette.buio)
        drawCircle(
            brush = Brush.radialGradient(
                0.55f to tinta.copy(alpha = 0f),
                0.88f to tinta.copy(alpha = 0.16f * svanire),
                1f to tinta.copy(alpha = 0f),
                center = onda.centro,
                radius = raggio,
            ),
            radius = raggio,
            center = onda.centro,
        )
        drawCircle(
            color = tinta.copy(alpha = 0.34f * svanire),
            radius = raggio,
            center = onda.centro,
            style = Stroke(width = size.width * 0.0042f * (0.4f + svanire)),
        )
    }
}

/** Quante masse di nuvola ci sono, e dove. Le taglie sono quelle del prototipo. */
private data class Nuvola(val x: Float, val y: Float, val w: Float, val passo: Float)

private val Nuvole: List<Nuvola> = List(5) { i ->
    val w = 150f + rnd(i + 11) * 110f
    Nuvola(
        x = -50f + rnd(i + 5) * 340f,
        y = 74f + rnd(i + 33) * 210f,
        w = w,
        // Ognuna va per conto suo, fra il settanta e il centotrenta per cento
        // della velocita' di base: cinque masse alla stessa identica velocita'
        // sono un fondale che scorre, non cinque nuvole.
        passo = 0.7f + rnd(i + 90) * 0.6f,
    )
}

/**
 * Quanto scorre una nuvola in un secondo, col cielo aperto, in punti del
 * disegno di riferimento.
 *
 * **Tre decimi di punto al secondo e' lentissimo, ed e' il punto.** Una nuvola
 * attraversa lo schermo in una ventina di minuti: nessuno la vede muoversi, e
 * chi torna sull'app dopo mezz'ora la trova altrove. E' il modo in cui un
 * disegno dice *non c'e' vento* senza scriverlo - e, al contrario di una
 * scritta, non puo' contraddire i dati perche' non afferma niente di preciso.
 *
 * Prima era un'**oscillazione**: trenta punti avanti e indietro con un periodo
 * di venti-quaranta secondi, cioe' fino a nove punti al secondo nel mezzo della
 * corsa. Trenta volte questa. Il cielo sereno respirava come un fondale di
 * teatro, e una nuvola che torna sempre al punto di partenza non e' una nuvola
 * che si sposta: e' una nuvola appesa.
 */
private const val DERIVA_SERENO = 0.3f

/**
 * Quanto va piu' veloce quando il cielo si chiude.
 *
 * Un fronte **si muove**, e muoverlo alla velocita' di un cumulo di bel tempo
 * lo farebbe sembrare lo stesso cielo con un colore diverso. Sei volte tanto
 * resta comunque lento - meno di due punti al secondo - ma la differenza fra i
 * due si legge senza doverla cercare.
 */
private const val DERIVA_FRONTE = 6f

/**
 * Le nuvole, rifatte da zero: corpo a pillola con fondo piatto, tre gonfiori
 * sovrapposti, un tocco di luce in alto e un'ombra sotto. Niente sfocatura -
 * bordi netti e morbidi.
 *
 * **Come compare una massa senza schioccare.** Nessuna delle due risposte ovvie
 * basta da sola: con la sola opacita' compare un fantasma **a grandezza piena**
 * al cinque per cento, che l'occhio legge come un errore di resa e non come una
 * cosa che arriva; con la sola scala compare un punto **pienamente opaco** che
 * si gonfia, cioe' uno schiocco con una rampa incollata davanti. Servono
 * entrambe, con l'alfa **al quadrato** perche' la massa resti tenue finche' e'
 * piccola.
 */
private fun DrawScope.nuvole(
    sx: Float,
    sy: Float,
    dy: Float,
    palette: SalaPalette,
    scena: Scena,
    sky: SkyState,
    tempo: Float,
) {
    // Le cinque entrano in fila e non tutte insieme: con una soglia sola
    // comparirebbero nello stesso istante, che e' lo scatto di prima con una
    // rampa davanti.
    Nuvole.forEachIndexed { i, n ->
        val presenza = (((scena.copertura - i * 0.13f) / 0.24f).coerceIn(0f, 1f))
            .coerceAtLeast(if (i < 2) scena.tempesta else 0f)
        if (presenza <= 0.01f) return@forEachIndexed

        // Il corpo si schiarisce o si incupisce col cielo: bianco panna su un
        // cielo aperto, ardesia sotto un fronte.
        val chiara = lerp(Color(0xFFFDFAF4), Color(0xFF9A9994), ((scena.copertura - 0.35f) / 0.65f).coerceIn(0f, 1f))
        val scura = lerp(Color(0xFF3F4653), Color(0xFF2A2F37), ((scena.copertura - 0.35f) / 0.65f).coerceIn(0f, 1f))
        val corpo = lerp(chiara, scura, palette.buio)
        val luce = lerp(Color.White.copy(alpha = 0.72f), SalaTokens.neutral100.copy(alpha = 0.16f), palette.buio)
        val ombra = lerp(SalaTokens.neutral900.copy(alpha = 0.07f), Color(0xFF121824).copy(alpha = 0.18f), palette.buio)

        // **L'evaporazione delle ore calde.** Quando il sole e' alto sopra un
        // cielo aperto, i cumuli di bel tempo si sfilacciano: non spariscono,
        // si fanno piu' tenui e un po' piu' larghi. Qui l'opacita' scende
        // dall'intero a quattro quinti e la massa si allarga del tre per
        // cento.
        //
        // **Non e' agganciata all'orologio ma all'altezza del sole**, e la
        // differenza conta: "le due del pomeriggio" e' il picco d'insolazione a
        // luglio in pianura padana e non lo e' a dicembre, ne' a Nairobi, ne'
        // a Bergen - e questa app apre tutte e tre. L'altezza del sole il picco
        // ce l'ha per costruzione, dove e quando che sia, e d'inverno non lo
        // raggiunge mai: il cielo non evapora, che e' esattamente giusto.
        //
        // Solo a cielo aperto: sotto un fronte non evapora niente, e vederlo
        // schiarire a mezzogiorno sarebbe il disegno che smentisce il dato.
        val evaporazione = SunClock.smoothstep(0.62f, 0.88f, sky.altitude) *
            (1f - scena.copertura).coerceIn(0f, 1f)

        val scala = lerp(0.55f, 1f, presenza) * lerp(1f, 1.03f, evaporazione)
        val alfa = presenza * presenza *
            lerp(0.92f, 0.96f, scena.copertura) *
            lerp(1f, 0.8f, evaporazione)
        val w = n.w * sx * scala
        val h = w * 0.46f
        // **La deriva scorre, e non torna indietro.** La velocita' e' quella
        // del cielo aperto finche' il cielo e' aperto, e sale col fronte; il
        // resto lo fa il tempo, che va avanti e basta.
        //
        // Il `mod` riporta dentro chi esce a destra: senza, dopo un'ora di app
        // aperta le cinque masse sarebbero tutte fuori schermo e il cielo
        // sarebbe vuoto. La corsa e' larga quanto lo schermo piu' la nuvola
        // piu' larga, cosi' rientra da sinistra **dopo** essere sparita del
        // tutto, e non a meta'.
        val velocita = lerp(DERIVA_SERENO, DERIVA_FRONTE, scena.copertura) * n.passo
        val corsa = RIF_L + 260f
        val spostamento = ((n.x + tempo * velocita) % corsa + corsa) % corsa - 130f
        val x = spostamento * sx
        // Il fondo della nuvola, da cui tutti i pezzi si misurano verso l'alto.
        val fondo = n.y * sy + h + dy

        fun tondo(d: Float, dx: Float, su: Float, tinta: Color) {
            drawCircle(
                color = tinta,
                radius = d / 2f,
                center = Offset(x + dx + d / 2f, fondo - su - d / 2f),
                alpha = alfa,
            )
        }

        // Il corpo: una pillola col fondo piatto appoggiato sulla linea.
        val corpoH = h * 0.46f
        drawRoundRect(
            color = corpo,
            topLeft = Offset(x, fondo - corpoH),
            size = Size(w, corpoH),
            cornerRadius = CornerRadius(corpoH / 2f),
            alpha = alfa,
        )
        tondo(w * 0.33f, w * 0.04f, h * 0.10f, corpo)
        tondo(w * 0.48f, w * 0.25f, h * 0.16f, corpo)
        tondo(w * 0.30f, w * 0.62f, h * 0.12f, corpo)
        tondo(w * 0.16f, w * 0.31f, h * 0.44f, luce)
        val ombraH = h * 0.10f
        drawRoundRect(
            color = ombra,
            topLeft = Offset(x + w * 0.14f, fondo - ombraH),
            size = Size(w * 0.72f, ombraH),
            cornerRadius = CornerRadius(ombraH / 2f),
            alpha = alfa,
        )
    }
}

/**
 * Le colline e i tre alberi.
 *
 * Sono la sola cosa ferma della schermata, ed e' voluto: qualcosa deve dire
 * dov'e' la terra, se no il sole e la pioggia galleggiano in un fondale.
 * I due dossi dietro escono dai lati; quello davanti e' una mezza ellisse che
 * poggia sul bordo dello schermo.
 */
private fun DrawScope.colline(sx: Float, sy: Float, palette: SalaPalette) {
    // I due dossi dietro: il fondo resta nascosto da quello davanti, quindi si
    // vedono solo come gobbe.
    drawOval(
        color = palette.collina1,
        topLeft = Offset(-90f * sx, 484f * sy),
        size = Size(340f * sx, 280f * sy),
    )
    drawOval(
        color = palette.collina2,
        topLeft = Offset(131f * sx, 484f * sy),
        size = Size(400f * sx, 300f * sy),
    )
    // Quello davanti: un'ellisse alta il doppio, tagliata dal bordo inferiore.
    // E' il modo piu' diretto di ottenere il "tondo sopra, dritto sotto" che
    // nel prototipo e' un raggio d'angolo azzerato in basso.
    clipRect {
        drawOval(
            color = palette.collina3,
            topLeft = Offset(-60f * sx, 678f * sy),
            size = Size(560f * sx, 472f * sy),
        )
    }

    fun albero(tx: Float, ty: Float, tw: Float, th: Float, fx: Float, fy: Float, fd: Float) {
        drawRoundRect(
            color = palette.tronco,
            topLeft = Offset(tx * sx, ty * sy),
            size = Size(tw * sx, th * sy),
            cornerRadius = CornerRadius(tw * sx * 0.5f),
        )
        drawOval(
            color = palette.fronda,
            topLeft = Offset(fx * sx, fy * sy),
            size = Size(fd * sx, fd * sy),
        )
    }
    albero(62f, 718f, 12f, 44f, 46f, 682f, 44f)
    albero(303f, 736f, 10f, 34f, 289f, 706f, 38f)
    albero(176f, 738f, 9f, 30f, 164f, 713f, 33f)
}

/**
 * Pioggia, grandine e neve: **non si mescolano, si sovrappongono.**
 *
 * Sono tre segni genuinamente diversi, e interpolare fra un tratto e un fiocco
 * non da' niente. Due cadute che condividono corsie e orologio e sfumano l'una
 * nell'altra sono esattamente com'e' fatto il nevischio, e non costano una riga
 * di geometria nuova.
 *
 * Le corsie sono quelle di [Corsie], **le stesse che decidono le vibrazioni**:
 * un contatore suo andrebbe in fase per un po' e poi scivolerebbe, e una
 * vibrazione fuori tempo rispetto a cio' che si vede e' peggio di nessuna
 * vibrazione.
 *
 * ## Perche' "non pioveva" pur piovendo
 *
 * Il difetto e' arrivato da chi l'app la usa: *l'animazione della pioggia non
 * succede quando dovrebbe*. Succedeva. Solo che era invisibile, per due
 * ragioni che si sommavano:
 *
 * - **erano quattordici segni in tutto**, uno per corsia, e la scheda ne copre
 *   la meta' bassa: sette tratti su uno schermo intero non sono una pioggia,
 *   sono graffi. Adesso ogni corsia porta una fila di gocce ([Corsie.ripetizioni])
 *   e i segni visibili diventano quattro volte tanti, senza toccare il battito
 *   che si sente in mano.
 * - **erano dipinti con `acqua`**, un azzurro medio tarato sul cielo di
 *   giorno. Di notte il cielo sta fra `#0D1420` e `#2B2F3D`: quell'azzurro ci
 *   finisce dentro, e il contrasto contro il fondo scendeva sotto il due a uno.
 *   Adesso la tinta **schiarisce col buio** fino al bianco ghiaccio, che e' poi
 *   cio' che si vede davvero guardando la pioggia di notte - non l'acqua, la
 *   luce che ci rimbalza sopra.
 *
 * La fioritura sulla riga di caduta, che era il terzo pezzo, non torna: il
 * pannello arriva a meta' schermo e la riga dove le gocce toccherebbero sta
 * sotto di lui. Una cosa dipinta dove nessuno la vede e' peggio di una cosa
 * che manca, perche' costa e non si nota se si rompe.
 */
private fun DrawScope.cioCheCade(scena: Scena, tempo: Float) {
    if (scena.bagnato <= 0.01f) return
    val pioggia = scena.bagnato * (1f - scena.neve) * (1f - scena.ghiaccio)
    val neve = scena.bagnato * scena.neve
    val grandine = scena.bagnato * scena.ghiaccio

    val cima = -0.07f * size.height
    val fondo = 1.03f * size.height
    val corsa = fondo - cima
    val larghezzaCorsia = size.width / Corsie.QUANTE

    /**
     * Il giro che le tre sostanze condividono: per ogni corsia accesa, la fila
     * di gocce che le tocca. Scritto una volta perche' le tre differenze vere -
     * il segno, la tinta, la velocita' - stiano dentro il blocco e non in tre
     * copie dello stesso doppio ciclo.
     */
    fun perOgniGoccia(
        tipo: Caduta,
        presenza: Float,
        disegna: (x: Float, y: Float, prof: Float, quota: Float, avanzamento: Float) -> Unit,
    ) {
        if (presenza <= 0.01f) return
        val quante = Corsie.ripetizioni(tipo)
        for (i in 0 until Corsie.QUANTE) {
            val quota = Corsie.accesa(i, scena.bagnato)
            if (quota <= 0.01f) continue
            // Le corsie del modello vanno da -0,65 a 0,65: riportate su 0..1
            // coprono la larghezza intera invece di stringersi attorno a un
            // oggetto che non c'e' piu'.
            val xCorsia = ((Corsie.x(i) / 1.30f) + 0.5f) * size.width
            for (k in 0 until quante) {
                val u = Corsie.corsa(tipo, i, tempo) + Corsie.scarto(i, k, quante)
                val avanzamento = u - floor(u)
                disegna(
                    xCorsia + Corsie.scostamento(i, k) * larghezzaCorsia * 1.7f,
                    cima + avanzamento * corsa,
                    Corsie.profonditaDi(i, k),
                    quota,
                    avanzamento,
                )
            }
        }
    }

    // **La tinta della pioggia schiarisce col buio e col fronte.** Di giorno e'
    // l'azzurro d'acqua di sempre; di notte, o sotto un cielo chiuso, tende al
    // bianco ghiaccio - che e' l'unico modo in cui una goccia si vede su un
    // fondo che e' gia' quasi nero.
    val pallore = (scena.notte * 0.88f + scena.copertura * 0.20f).coerceIn(0f, 1f)
    val tintaPioggia = lerp(SalaTokens.acqua, SalaTokens.ghiaccioChiaro, pallore)

    perOgniGoccia(Caduta.PIOGGIA, pioggia) { x, y, prof, quota, avanzamento ->
        val lung = size.width * (0.070f + 0.080f * prof)
        // L'inclinazione del prototipo: dodici gradi, la stessa per tutte.
        val dx = lung * 0.21f
        val a = (
            quota * (0.30f + 0.70f * prof) * (0.55f + 0.45f * pioggia) *
                // L'entrata in scena, accorciata: prima la goccia restava
                // trasparente per un ottavo della propria discesa, cioe' per
                // tutta la fascia di cielo che si vede sopra la scheda.
                (avanzamento / 0.05f).coerceAtMost(1f)
            ).coerceIn(0f, 1f)
        val testa = Offset(x, y)
        val coda = Offset(x - dx, y - lung)
        drawLine(
            brush = Brush.linearGradient(
                0f to tintaPioggia.copy(alpha = 0f),
                0.45f to tintaPioggia.copy(alpha = a * 0.5f),
                1f to tintaPioggia.copy(alpha = a),
                start = coda,
                end = testa,
            ),
            start = coda,
            end = testa,
            // Le vicine sono tratti larghi, le lontane fili: con uno spessore
            // solo per tutte la profondita' la diceva la sola opacita', e non
            // bastava a dare aria fra una corsia e l'altra.
            strokeWidth = size.width * (0.0028f + 0.0040f * prof),
            cap = StrokeCap.Round,
        )
    }

    perOgniGoccia(Caduta.GRANDINE, grandine) { x, y, prof, quota, _ ->
        val r = size.width * (0.0062f + 0.0078f * prof)
        val a = (quota * grandine * (0.55f + 0.45f * prof)).coerceIn(0f, 1f)
        // **La scia corta, che prima non c'era.** Un chicco cade a novanta
        // chilometri l'ora: fermo e tondo si legge come una pallina, non come
        // grandine. Basta un tratto di due diametri dietro di lui.
        drawLine(
            color = SalaTokens.ghiaccioScuro.copy(alpha = a * 0.38f),
            start = Offset(x - r * 0.5f, y - r * 4.6f),
            end = Offset(x, y),
            strokeWidth = r * 1.15f,
            cap = StrokeCap.Round,
        )
        drawCircle(color = SalaTokens.ghiaccioChiaro.copy(alpha = a), radius = r, center = Offset(x, y))
        // Il lustro: il punto in cui il chicco riflette il cielo. E' l'unica
        // cosa che distingue una biglia di ghiaccio da un pallino grigio.
        drawCircle(
            color = Color.White.copy(alpha = a * 0.85f),
            radius = r * 0.34f,
            center = Offset(x - r * 0.3f, y - r * 0.34f),
        )
    }

    perOgniGoccia(Caduta.NEVE, neve) { x, y, prof, quota, _ ->
        // La deriva: un seno lungo, diverso per fiocco - la fase la da' la
        // posizione, non l'indice della corsia, se no i fiocchi di una stessa
        // fila sbanderebbero tutti insieme come un tergicristallo.
        val deriva = sin(tempo * (0.5f + prof * 0.45f) + x * 0.013f) *
            size.width * (0.025f + 0.040f * prof)
        val r = size.width * (0.0042f + 0.0072f * prof)
        val a = (quota * neve * (0.42f + 0.58f * prof)).coerceIn(0f, 1f)
        val centro = Offset(x + deriva, y)
        // I fiocchi vicini hanno un alone: e' il modo in cui l'occhio legge
        // "fuori fuoco", e senza, cinquanta dischi tutti nitidi si leggono come
        // coriandoli.
        if (prof > 0.55f) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to SalaTokens.neutral100.copy(alpha = a * 0.40f),
                    1f to SalaTokens.neutral100.copy(alpha = 0f),
                    center = centro,
                    radius = r * 3.4f,
                ),
                radius = r * 3.4f,
                center = centro,
            )
        }
        drawCircle(color = SalaTokens.neutral100.copy(alpha = a), radius = r, center = centro)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Il fulmine
// ─────────────────────────────────────────────────────────────────────────────

/** La luce di una scarica: bianca appena fredda, come la si ricorda. */
private val LuceLampo = Color(0xFFE8F1FF)

/**
 * Il fulmine: il cielo che si accende, l'alone, e **la saetta**.
 *
 * ### Cosa c'era prima
 *
 * Un alone tondo in alto a destra, sempre nello stesso punto, acceso da due
 * rampe lineari. Faceva il suo mestiere - dire "temporale" con la coda
 * dell'occhio - ma di un fulmine non aveva niente: nessun canale, nessuna
 * biforcazione, nessuno sfarfallio, e un centro fisso che dopo il secondo giro
 * si legge come una macchia dello schermo.
 *
 * ### Cosa c'e' adesso, in tre strati
 *
 * 1. **Il cielo intero si accende.** Un velo su tutta la tela, colline
 *    comprese: un fulmine illumina il paesaggio, non solo la nuvola che lo fa.
 * 2. **L'alone** attorno al punto da cui il canale scende, largo quanto lo
 *    schermo, che tiene insieme il velo e la saetta.
 * 3. **La saetta**, disegnata in quattro passate - alone largo e tenue,
 *    poi via via piu' stretto e piu' opaco, fino al nucleo bianco. E' cosi'
 *    che si dipinge una cosa che **emette** luce invece di rifletterla: il
 *    bianco puro al centro, il colore attorno.
 *
 * Il canale cambia a ogni colpo ([indiceLampo]) e resta lo stesso dentro un
 * colpo: le riprese di un fulmine riaccendono il canale gia' aperto, e vederne
 * uno nuovo a ogni sfarfallio sarebbe la cosa sbagliata detta due volte in
 * mezzo secondo.
 */
private fun DrawScope.fulmine(tempo: Float, tempesta: Float) {
    if (tempesta <= 0.01f) return
    val cielo = forzaLampo(tempo) * tempesta
    if (cielo <= 0.006f) return
    val n = indiceLampo(tempo)
    val xAlto = size.width * (0.18f + rnd(n * 17 + 3) * 0.62f)

    // 1. Il velo su tutto.
    //
    // **Ed e' meno forte di quanto verrebbe da fare.** Il riverbero di prima
    // arrivava al novantacinque per cento di bianco: sullo scatto del temporale
    // la schermata era una macchia chiara in cui non si distingueva piu' ne'
    // una nuvola ne' un chicco di grandine. Un fulmine vero **stacca** il
    // paesaggio in controluce, non lo cancella: qui il velo schiarisce, e a
    // bucare il cielo ci pensa la saetta, che e' stretta e puo' permettersi il
    // bianco pieno.
    drawRect(color = LuceLampo.copy(alpha = (0.18f * cielo).coerceIn(0f, 1f)))

    // 2. L'alone, appeso al canale e non a un angolo fisso.
    val centro = Offset(xAlto, size.height * 0.16f)
    val raggio = size.width * 1.15f
    drawCircle(
        brush = Brush.radialGradient(
            0f to LuceLampo.copy(alpha = (0.55f * cielo).coerceIn(0f, 1f)),
            0.30f to LuceLampo.copy(alpha = (0.24f * cielo).coerceIn(0f, 1f)),
            0.64f to LuceLampo.copy(alpha = 0f),
            center = centro,
            radius = raggio,
        ),
        radius = raggio,
        center = centro,
    )

    // 3. La saetta, solo finche' il canale e' acceso.
    val canale = forzaSaetta(tempo) * tempesta
    if (canale > 0.02f) saetta(n, canale, xAlto)
}

/**
 * Il canale, e i due rami che se ne staccano.
 *
 * I nodi sono dodici e lo zigzag e' laterale: un fulmine scende **dritto** e
 * sbanda, non serpeggia. La deriva cresce col quadrato della discesa, cosi' la
 * parte alta e' quasi verticale e quella bassa si apre - che e' come si vedono.
 */
private fun DrawScope.saetta(n: Int, forza: Float, xAlto: Float) {
    val cima = size.height * 0.015f
    val fondo = size.height * (0.50f + rnd(n * 17 + 9) * 0.18f)
    val verso = if (rnd(n * 17 + 6) > 0.5f) 1f else -1f
    val deriva = size.width * (0.05f + rnd(n * 17 + 5) * 0.22f) * verso
    val nodi = 12
    val canale = List(nodi + 1) { i ->
        val t = i / nodi.toFloat()
        val zig = if (i == 0 || i == nodi) 0f else (rnd(n * 131 + i) - 0.5f) * size.width * 0.095f
        Offset(xAlto + deriva * t * t + zig, cima + (fondo - cima) * t)
    }
    tracciaCanale(canale, forza)

    // I rami: due, da due nodi diversi, corti e piu' tenui del canale. Sono la
    // differenza fra "un fulmine" e "una riga bianca storta".
    listOf(4, 7).forEachIndexed { quale, nodo ->
        val da = canale[nodo]
        val versoRamo = if (rnd(n * 53 + quale) > 0.5f) 1f else -1f
        val lungo = size.height * (0.06f + rnd(n * 53 + quale + 7) * 0.09f)
        val ramo = List(4) { i ->
            val t = i / 3f
            Offset(
                da.x + versoRamo * lungo * t * (0.55f + rnd(n * 71 + quale * 5 + i) * 0.5f),
                da.y + lungo * t,
            )
        }
        tracciaCanale(ramo, forza * 0.72f, scala = 0.5f)
    }
}

/**
 * Le quattro passate che fanno una cosa che emette luce.
 *
 * Un `Path` solo per passata e non un tratto per segmento: disegnando segmento
 * per segmento, i giunti si sovrappongono e a opacita' parziale ogni nodo
 * diventa un puntino piu' chiaro - una collana di perle al posto di un canale.
 */
private fun DrawScope.tracciaCanale(punti: List<Offset>, forza: Float, scala: Float = 1f) {
    if (punti.size < 2) return
    val strada = Path().apply {
        moveTo(punti[0].x, punti[0].y)
        for (i in 1 until punti.size) lineTo(punti[i].x, punti[i].y)
    }
    listOf(
        0.070f to 0.13f,
        0.028f to 0.26f,
        0.0105f to 0.58f,
        0.0040f to 1.00f,
    ).forEach { (larghezza, alfa) ->
        drawPath(
            path = strada,
            // Il nucleo e' bianco puro e il resto e' la luce fredda: e' la
            // sovraesposizione di una fotografia, dove il centro di cio' che
            // brucia perde sempre il colore prima dei bordi.
            color = (if (alfa >= 1f) Color.White else LuceLampo)
                .copy(alpha = (alfa * forza).coerceIn(0f, 1f)),
            style = Stroke(
                width = size.width * larghezza * scala,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
