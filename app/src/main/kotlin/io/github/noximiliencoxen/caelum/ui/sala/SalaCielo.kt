package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.noximiliencoxen.caelum.data.SkyState
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
) {
    Canvas(modifier = modifier) {
        val t = tempo()
        val sx = size.width / RIF_L
        val sy = size.height / RIF_H
        val dy = insetAlto.toPx()

        drawRect(brush = cieloBrush(stops))

        // Le stelle non si spengono con le nuvole: ci passano sotto. Il velo
        // **cala** con la copertura invece di azzerarsi, perche' da sotto una
        // notte coperta qualcuna si vede lo stesso.
        cieloStellato(
            tempo = t,
            inchiostro = SalaTokens.neutral100,
            velo = scena.notte * (1f - scena.copertura * 0.72f),
        )

        arcoDelCielo(sx, sy, dy, palette, scena)
        soleEluna(sx, sy, dy, sky, scena, faseLunare, t)
        nuvole(sx, sy, dy, palette, scena, t)
        colline(sx, sy, palette)
        cioCheCade(scena, t)

        // Il riverbero del lampo si prende il cielo intero: un temporale non
        // illumina solo la nuvola che lo fa.
        riverberoDelLampo(t, scena.tempesta)
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
) {
    // Il cielo chiuso li nasconde tutti e due: dietro un fronte non si vede
    // ne' l'uno ne' l'altra.
    val velo = (1f - scena.copertura * 0.92f).coerceIn(0f, 1f) * (1f - scena.neve * 0.8f)
    if (velo <= 0.01f) return

    val posizione = arco(sky.journey, sx, sy, dy)

    // Il respiro del prototipo (`animation:respiro`), ridotto a un soffio: una
    // pulsazione del sei per cento su otto secondi si legge come luce, non come
    // un oggetto che cambia taglia.
    val respiro = 1f + 0.03f * sin(tempo * 0.78f)

    if (scena.sole > 0.01f) {
        val elev = sin(PI.toFloat() * sky.journey.coerceIn(0f, 1f))
        val cuore = lerp(Color(0xFFFFECCD), Color(0xFFFFFBE0), elev)
        val mezzo = lerp(Color(0xFFEE8A45), Color(0xFFFFD42E), elev)
        val bordo = lerp(Color(0xFFC96634), Color(0xFFF2A81B), elev)
        val r = 38f * sx * respiro
        val alone = lerp(SalaTokens.accent300, Color(0xFFFFE89A), elev)
        val rAlone = 90f * sx * (1f + 0.075f * sin(tempo * 0.63f))
        drawCircle(
            brush = Brush.radialGradient(
                0f to alone.copy(alpha = 0.55f * scena.sole * velo),
                0.70f to alone.copy(alpha = 0f),
                center = posizione,
                radius = rAlone,
            ),
            radius = rAlone,
            center = posizione,
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to cuore,
                0.58f to mezzo,
                1f to bordo,
                // Il fuoco spostato in alto a sinistra, come nel prototipo: e'
                // quello che fa sembrare il disco una sfera e non un bollo.
                center = Offset(posizione.x - r * 0.24f, posizione.y - r * 0.32f),
                radius = r * 1.35f,
            ),
            radius = r,
            center = posizione,
            alpha = (scena.sole * velo).coerceIn(0f, 1f),
        )
    }

    if (scena.notte > 0.01f) {
        val r = 35f * sx
        val alpha = (scena.notte * velo).coerceIn(0f, 1f)
        val rAlone = 85f * sx
        drawCircle(
            brush = Brush.radialGradient(
                0f to SalaTokens.neutral100.copy(alpha = 0.26f * alpha),
                0.70f to SalaTokens.neutral100.copy(alpha = 0f),
                center = posizione,
                radius = rAlone,
            ),
            radius = rAlone,
            center = posizione,
        )
        luna(posizione, r, faseLunare, alpha)
    }
}

/**
 * La luna: corpo pieno coi suoi mari, e sopra la sola parte illuminata.
 *
 * **La fase e' quella vera di stanotte**, non un disegno fisso: il prototipo
 * aveva una gibbosa al settantaquattro per cento scritta a mano, e una luna
 * che non corrisponde a quella in cielo e' un'affermazione falsa detta con
 * disinvoltura. La costruzione della sagoma e' la stessa di Sala IV e di
 * `Bodies.kt`: semicerchio dal lato illuminato piu' la mediana, che rientra
 * quando e' falce e sporge quando e' gibbosa.
 */
private fun DrawScope.luna(centro: Offset, r: Float, fase: Float, alpha: Float) {
    // Il corpo in ombra: c'e' sempre, anche al novilunio, perche' una luna
    // nuova non e' un buco nel cielo.
    drawCircle(color = SalaTokens.lunaOmbra.copy(alpha = 0.55f * alpha), radius = r, center = centro)

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
                0f to SalaTokens.lunaLuce,
                0.52f to SalaTokens.lunaMezzo,
                1f to SalaTokens.lunaBordo,
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
                color = Color(0xFF463830).copy(alpha = 0.22f * alpha),
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
        style = Stroke(width = r * 0.03f),
    )
}

/** Quante masse di nuvola ci sono, e dove. Le taglie sono quelle del prototipo. */
private data class Nuvola(val x: Float, val y: Float, val w: Float, val deriva: Float)

private val Nuvole: List<Nuvola> = List(5) { i ->
    val w = 150f + rnd(i + 11) * 110f
    Nuvola(
        x = -50f + rnd(i + 5) * 340f,
        y = 74f + rnd(i + 33) * 210f,
        w = w,
        deriva = 22f + rnd(i + 90) * 16f,
    )
}

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
private fun DrawScope.nuvole(sx: Float, sy: Float, dy: Float, palette: SalaPalette, scena: Scena, tempo: Float) {
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

        val scala = lerp(0.55f, 1f, presenza)
        val alfa = presenza * presenza * lerp(0.92f, 0.96f, scena.copertura)
        val w = n.w * sx * scala
        val h = w * 0.46f
        // La deriva del prototipo: trenta punti avanti e indietro, lenta.
        val spostamento = sin(tempo * (2f * PI.toFloat() / n.deriva)) * 30f * sx
        val x = n.x * sx + spostamento
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
 */
private fun DrawScope.cioCheCade(scena: Scena, tempo: Float) {
    if (scena.bagnato <= 0.01f) return
    val pioggia = scena.bagnato * (1f - scena.neve) * (1f - scena.ghiaccio)
    val neve = scena.bagnato * scena.neve
    val grandine = scena.bagnato * scena.ghiaccio

    val cima = -0.07f * size.height
    val fondo = 1.03f * size.height
    val corsa = fondo - cima

    for (i in 0 until Corsie.QUANTE) {
        val quota = Corsie.accesa(i, scena.bagnato)
        if (quota <= 0.01f) continue
        val prof = Corsie.profondita(i)
        // Le corsie del modello vanno da -0,65 a 0,65: riportate su 0..1
        // coprono la larghezza intera invece di stringersi attorno a un oggetto
        // che non c'e' piu'.
        val x = ((Corsie.x(i) / 1.30f) + 0.5f) * size.width

        fun quotaDi(tipo: Caduta): Float {
            val u = Corsie.corsa(tipo, i, tempo)
            return u - floor(u)
        }

        if (pioggia > 0.01f) {
            val avanzamento = quotaDi(Caduta.PIOGGIA)
            val y = cima + avanzamento * corsa
            val lung = size.width * (0.055f + 0.045f * prof)
            val a = (quota * pioggia * (0.42f + 0.58f * prof) * (avanzamento / 0.12f).coerceAtMost(1f))
                .coerceIn(0f, 1f)
            val tinta = SalaTokens.acqua
            // L'inclinazione del prototipo: dodici gradi, la stessa per tutte.
            val dx = lung * 0.21f
            drawLine(
                brush = Brush.linearGradient(
                    0f to tinta.copy(alpha = 0f),
                    1f to tinta.copy(alpha = a),
                    start = Offset(x - dx, y - lung),
                    end = Offset(x, y),
                ),
                start = Offset(x - dx, y - lung),
                end = Offset(x, y),
                strokeWidth = size.width * 0.0055f,
                cap = StrokeCap.Round,
            )
        }

        if (grandine > 0.01f) {
            val avanzamento = quotaDi(Caduta.GRANDINE)
            val y = cima + avanzamento * corsa
            val r = size.width * (0.008f + 0.005f * prof)
            drawCircle(
                color = SalaTokens.ghiaccioChiaro.copy(
                    alpha = (quota * grandine * (0.6f + 0.4f * prof)).coerceIn(0f, 1f),
                ),
                radius = r,
                center = Offset(x, y),
            )
        }

        if (neve > 0.01f) {
            val avanzamento = quotaDi(Caduta.NEVE)
            val y = cima + avanzamento * corsa
            // La deriva: un seno lungo, diverso per fiocco. E' l'unica cosa che
            // distingue la neve dalla pioggia bianca.
            val deriva = sin(tempo * 0.7f + i * 1.9f) * size.width * 0.05f
            val r = size.width * (0.006f + 0.005f * prof)
            drawCircle(
                color = SalaTokens.neutral100.copy(
                    alpha = (quota * neve * (0.6f + 0.4f * prof)).coerceIn(0f, 1f),
                ),
                radius = r,
                center = Offset(x + deriva, y),
            )
        }
    }
}

/**
 * Il lampo: un alone largo in alto a destra, e brevissimo.
 *
 * L'istante lo decide [forzaLampo], la stessa che il tuono usa per sapere
 * quando far vibrare il telefono.
 */
private fun DrawScope.riverberoDelLampo(tempo: Float, forza: Float) {
    val acceso = forzaLampo(tempo) * forza
    if (acceso <= 0.01f) return
    val centro = Offset(size.width * 0.62f, size.height * 0.18f)
    val raggio = size.width * 1.15f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFFFFF2EB).copy(alpha = 0.95f * acceso),
            0.34f to Color(0xFFFFF2EB).copy(alpha = 0.35f * acceso),
            0.62f to Color(0xFFFFF2EB).copy(alpha = 0f),
            center = centro,
            radius = raggio,
        ),
        radius = raggio,
        center = centro,
    )
}
