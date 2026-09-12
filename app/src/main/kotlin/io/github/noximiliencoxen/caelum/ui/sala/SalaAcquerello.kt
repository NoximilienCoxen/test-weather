package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import io.github.noximiliencoxen.caelum.R
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.ui.render3d.Camera
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * L'acquerello di Sala: **forme vive, materia dipinta**.
 *
 * Il concept non e' fatto di cerchi pieni, e i cerchi pieni infatti non gli
 * somigliavano. Un acquerello si riconosce da tre cose che un `drawCircle` non
 * ha: il bordo **spostato** dall'acqua invece che netto, il pigmento che si
 * **accumula sul filo** di quel bordo, e la **granulazione** dentro il lavaggio.
 * Nessuna delle tre si calcola: si dipingono.
 *
 * Da qui la divisione. La **composizione** resta viva e la fa il codice - dove
 * stanno le masse, quanto sono grandi, come girano, di che colore sono all'ora
 * mostrata - mentre la **materia** arriva da timbri dipinti che il codice
 * ritinge. Cosi' la scultura continua a ruotare e a cambiare col tempo, ma ha
 * addosso un acquerello vero invece di una tinta piatta.
 *
 * **Dei timbri conta solo l'alfa.** Sono bianchi su trasparente e vengono
 * ritinti con la tavolozza della sala: il colore dipinto dentro il file viene
 * buttato via. Chi li ridipinge non deve preoccuparsi della tinta, solo della
 * forma e della densita'.
 *
 * I file di adesso sono **provvisori**, generati da `scripts/texture_acquerello.py`
 * perche' la catena - carica, tinge, timbra - fosse viva e fotografabile prima
 * che qualcuno aprisse Blender. Sostituirli e' una copia di file: nessuna riga
 * di Kotlin cambia.
 *
 * **Niente sfocatura a runtime, e non e' una scorciatoia**: `Modifier.blur` e'
 * API 31, il minimo di questo progetto e' 26. Il bordo bagnato deve *essere
 * dipinto*, non calcolato - il che poi e' anche il modo giusto di ottenerlo.
 */
@Immutable
class Acquerello(
    val macchie: List<ImageBitmap>,
    val disco: ImageBitmap,
    val pennellate: List<ImageBitmap>,
    val ombra: ImageBitmap,
    val carta: ImageBitmap,
)

/**
 * I timbri, caricati **una volta sola per tutta Sala**.
 *
 * Non e' un vezzo architetturale, e' memoria: `SalaBackground` sta dentro ogni
 * sala, e le sette sale del carosello sono composizioni separate. Chiamando li'
 * il caricamento, le stesse tredici immagini verrebbero decodificate sette
 * volte - una decina di megabyte per giro, settanta in tutto, per disegnare i
 * medesimi pixel. `SalaShell` le carica e le passa di qui.
 */
val LocalAcquerello = staticCompositionLocalOf<Acquerello> {
    error("Acquerello non fornito: le sale vanno avvolte da SalaShell")
}

@Composable
fun rememberAcquerello(): Acquerello = Acquerello(
    macchie = listOf(
        ImageBitmap.imageResource(R.drawable.sala_macchia_1),
        ImageBitmap.imageResource(R.drawable.sala_macchia_2),
        ImageBitmap.imageResource(R.drawable.sala_macchia_3),
        ImageBitmap.imageResource(R.drawable.sala_macchia_4),
    ),
    disco = ImageBitmap.imageResource(R.drawable.sala_disco),
    pennellate = listOf(
        ImageBitmap.imageResource(R.drawable.sala_pennellata_1),
        ImageBitmap.imageResource(R.drawable.sala_pennellata_2),
        ImageBitmap.imageResource(R.drawable.sala_pennellata_3),
        ImageBitmap.imageResource(R.drawable.sala_pennellata_4),
        ImageBitmap.imageResource(R.drawable.sala_pennellata_5),
        ImageBitmap.imageResource(R.drawable.sala_pennellata_6),
    ),
    ombra = ImageBitmap.imageResource(R.drawable.sala_ombra),
    carta = ImageBitmap.imageResource(R.drawable.sala_carta),
)

/** Timbra un'immagine centrata, ritinta, scalata. L'unico modo in cui si disegna qui. */
fun DrawScope.timbra(
    timbro: ImageBitmap,
    centro: Offset,
    larghezza: Float,
    altezza: Float,
    tinta: Color,
    alfa: Float = 1f,
) {
    val w = larghezza.roundToInt()
    val h = altezza.roundToInt()
    if (w <= 0 || h <= 0) return
    drawImage(
        image = timbro,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(timbro.width, timbro.height),
        dstOffset = IntOffset(
            (centro.x - larghezza / 2f).roundToInt(),
            (centro.y - altezza / 2f).roundToInt(),
        ),
        dstSize = IntSize(w, h),
        alpha = alfa.coerceIn(0f, 1f),
        colorFilter = ColorFilter.tint(tinta),
    )
}

/**
 * La grana della carta, moltiplicata sopra tutto.
 *
 * Moltiplicata e non sovrapposta: la grana **toglie** luce dove la fibra e'
 * in rilievo, non aggiunge grigio sopra. Tenuta bassissima - su un fondo chiaro
 * una grana che si nota non fa carta, fa sporco.
 */
fun DrawScope.granaDiCarta(acquerello: Acquerello, forza: Float = 0.10f) {
    val lato = maxOf(size.width, size.height)
    drawImage(
        image = acquerello.carta,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(acquerello.carta.width, acquerello.carta.height),
        dstOffset = IntOffset(((size.width - lato) / 2f).roundToInt(), ((size.height - lato) / 2f).roundToInt()),
        dstSize = IntSize(lato.roundToInt(), lato.roundToInt()),
        alpha = forza.coerceIn(0f, 1f),
        blendMode = BlendMode.Multiply,
    )
}

/**
 * Le masse della nuvola, **in tre dimensioni vere**.
 *
 * Sono le stesse sette di `WeatherSculpture.CLOUD_MASSES`, con la stessa
 * ragione dietro: sparse anche in profondita', se no ruotando la nuvola si
 * rivela un ritaglio di cartone. Le ultime due sono del temporale - larghe e
 * dietro, perche' un temporale e' un fronte, non una nuvola piu' fitta.
 */
private val MasseNuvola = listOf(
    floatArrayOf(-0.26f, 0.02f, 0.16f, 0.19f),
    floatArrayOf(0.00f, -0.09f, -0.06f, 0.25f),
    floatArrayOf(0.26f, 0.03f, 0.12f, 0.20f),
    floatArrayOf(-0.11f, 0.10f, -0.19f, 0.18f),
    floatArrayOf(0.15f, 0.11f, -0.14f, 0.17f),
    floatArrayOf(-0.42f, -0.04f, -0.24f, 0.21f),
    floatArrayOf(0.44f, -0.02f, -0.21f, 0.22f),
)
private const val MASSE_TEMPORALE = 2

/** Quanto le masse si compenetrano. Sotto 1,2 si leggono come dischi separati. */
private const val FUSIONE = 1.24f

// ── Le tinte della scultura ──────────────────────────────────────────────────
//
// Prese dai token e non dall'inchiostro del testo, e **interpolate sul buio
// della carta** invece che scelte da un booleano: erano tre `if (palette.dark)`
// che si ribaltavano tutti nello stesso fotogramma attraversando il crepuscolo.

private fun tintaNuvola(palette: SalaPalette): Color =
    lerp(SalaTokens.accent, SalaTokens.accent400, palette.buio)

private fun tintaPioggia(palette: SalaPalette): Color =
    lerp(SalaTokens.accent700, SalaTokens.accent300, palette.buio)

/**
 * La sagoma della parte illuminata, alla fase data.
 *
 * E' la stessa costruzione di `Bodies.kt::moon`: un semicerchio dal lato
 * illuminato piu' la mediana, che rientra quando la luna e' falce e sporge
 * quando e' gibbosa. Ripetuta qui e non richiamata perche' li' e' intrecciata
 * col disegno; se dovesse cambiare, vanno cambiate tutte e due - ed e' scritto
 * qui perche' chi tocca l'una trovi l'altra.
 */
private fun parteIlluminata(centro: Offset, r: Float, fase: Float): Path {
    val crescente = fase < 0.5f
    val terminatore = abs(cos(2.0 * PI * fase).toFloat())
    val gibbosa = ((1f - cos(2.0 * PI * fase).toFloat()) / 2f) > 0.5f
    val disco = Rect(centro.x - r, centro.y - r, centro.x + r, centro.y + r)
    val mediana = Rect(centro.x - r * terminatore, centro.y - r, centro.x + r * terminatore, centro.y + r)
    return Path().apply {
        arcTo(disco, if (crescente) -90f else 90f, 180f, true)
        arcTo(mediana, if (crescente) 90f else -90f, if (gibbosa) 180f else -180f, false)
        close()
    }
}

/**
 * La scena, in numeri che scorrono invece che in caselle.
 *
 * `SalaCondition` decide bene il **testo** - "Rovescio di grandine" non ha mezze
 * misure - ma decide male il **disegno**: fra sereno e coperto ci sono tutte le
 * nuvolosita' del mondo, e con l'enum comparivano tutte insieme, in un
 * fotogramma, tutte le volte che il cielo cambiava idea.
 *
 * Ogni numero qui dentro sta fra 0 e 1 ed e' gia' passato per la sua molla. Chi
 * disegna non chiede piu' "che tempo fa": chiede **quanto**.
 */
@Immutable
data class Scena(
    /** Quanto si vede il sole. */
    val sole: Float,
    /** Quanto il cielo e' chiuso. */
    val copertura: Float,
    /** Il fronte del temporale: le due masse larghe, il fulmine, il riverbero. */
    val tempesta: Float,
    /** Quanto cade. Non **se** cade: quanto. */
    val bagnato: Float,
    /** Chicco invece di tratto. */
    val ghiaccio: Float,
    /** Fiocco invece di tratto. */
    val neve: Float,
    /** Luna invece di sole, stelle invece di uccelli. */
    val notte: Float,
) {
    /** Vero finche' qualcosa si sta ancora spostando: serve a tenere acceso
     *  l'orologio per tutta la durata di un passaggio, e non un istante di
     *  piu'. */
    val inTransito: Boolean
        get() = inMezzo(copertura) || inMezzo(notte) || inMezzo(tempesta) ||
            inMezzo(sole) || bagnato > 0.01f

    companion object {
        val Ferma = Scena(0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }
}

/**
 * Dove vuole arrivare la scena, prima che le molle ci arrivino.
 *
 * **Due dei sette numeri non li calcola nessuno qui**: `sunPresence` e
 * `moonPresence` esistono gia' in `SunClock`, sono gia' continui e sono gia'
 * smorzati a monte in `MeteoApp`. Sono anche **gli stessi** che usano i widget,
 * quindi la galleria e la schermata di blocco non raccontano due cieli diversi.
 * `moonPresence` tocca 1 esattamente a un'altezza del sole di -0,42, cioe' dove
 * la vecchia soglia dichiarava "notte": passare dal booleano al continuo non
 * cambia niente a notte piena, riempie solo il crepuscolo che prima veniva
 * arrotondato via.
 *
 * E la **copertura viene dal dato vero** quando c'e'. L'enum e' il ripiego, non
 * la fonte: il codice WMO da cui l'enum nasce e' esso stesso derivato dalla
 * nuvolosita' oraria, quindi leggerla direttamente non e' una scorciatoia, e'
 * togliere un passaggio che buttava via precisione.
 */
fun scenaBersaglio(
    sky: SkyState,
    condition: SalaCondition,
    nevica: Boolean,
    coperturaOraria: Int?,
    pioggiaMm: Double?,
): Scena {
    val temporale = condition == SalaCondition.TEMPORALE ||
        condition == SalaCondition.TEMPORALE_GRANDINE
    val grandina = condition == SalaCondition.GRANDINE ||
        condition == SalaCondition.TEMPORALE_GRANDINE
    val cade = condition != SalaCondition.SERENO && condition != SalaCondition.NUVOLOSO
    return Scena(
        sole = sky.sunPresence,
        notte = sky.moonPresence,
        copertura = coperturaOraria?.let { (it / 100f).coerceIn(0f, 1f) }
            ?: if (condition == SalaCondition.SERENO) 0f else 1f,
        tempesta = if (temporale) 1f else 0f,
        // Una pioviggine non e' un rovescio, e finora si dipingevano uguali. Il
        // minimo non e' zero: se il codice WMO dice che piove **deve piovere**,
        // e i millimetri decidono quanto forte, non se (trappola #14).
        bagnato = if (!cade) 0f else ((pioggiaMm?.toFloat() ?: 1f) / 2.5f).coerceIn(0.35f, 1f),
        ghiaccio = if (grandina) 1f else 0f,
        neve = if (nevica) 1f else 0f,
    )
}

/**
 * Quanto c'e' di una massa di nuvola, data la copertura.
 *
 * **Le cinque entrano in fila, non tutte insieme.** La prima basta un velo di
 * nuvole, l'ultima vuole il cielo chiuso. Con una soglia sola comparirebbero
 * tutte nello stesso istante, che e' lo scatto di prima con una rampa davanti.
 * Le ultime due sono il fronte del temporale e non seguono la copertura: un
 * temporale e' un fronte, non una nuvola piu' fitta.
 */
private fun presenzaMassa(i: Int, copertura: Float, tempesta: Float): Float {
    if (i >= MasseNuvola.size - MASSE_TEMPORALE) return tempesta
    return ((copertura - i * 0.16f) / 0.30f).coerceIn(0f, 1f)
}

/**
 * La scultura di Sala I: disco, masse, cio' che cade, ombra.
 *
 * **Non ramifica piu' sul tempo, lo interpola.** Prima ogni cosa qui dentro era
 * un `if`: coperto o sereno, notte o giorno, bagnato o asciutto. Ne veniva che
 * il cielo cambiava in un fotogramma - il sole spariva, cinque masse comparivano
 * intere, la pioggia si accendeva tutta insieme - e una galleria che cambia
 * parete di scatto non e' una galleria, e' un proiettore di diapositive. Adesso
 * arriva [Scena], sette numeri fra 0 e 1, e ogni ramo di prima e' diventato una
 * mescola.
 *
 * @param giroDeg quanto e' girata attorno alla verticale. E' il dito, e basta
 *   quello: l'asse verticale in Sala e' del carosello fra le sale, e contenderlo
 *   riaccenderebbe la trappola #5 per un tocco decorativo.
 * @param fase la fase lunare vera, 0 novilunio e 0,5 plenilunio.
 */
fun DrawScope.scultura(
    acquerello: Acquerello,
    scena: Scena,
    palette: SalaPalette,
    giroDeg: Float,
    fase: Float? = null,
    /** I secondi da quando la sala e' in vista. Zero quando niente si muove. */
    tempo: Float = 0f,
) {
    // L'unita' si misura sulla **larghezza**, non sul lato corto: la scultura
    // deve occupare la cassa come nel concept, e prendendo il minimo restava un
    // francobollo in mezzo a una pagina vuota.
    val unita = size.width * 0.80f
    val camera = Camera(
        yawDeg = giroDeg,
        pitchDeg = 0f,
        distance = unita * 2.6f,
        origin = Offset(size.width * 0.52f, size.height * 0.46f),
    )
    val c = scena.copertura
    val n = scena.notte

    // ── L'ombra portata, per prima: sta sotto tutto ──────────────────────────
    val suolo = size.height * 0.82f
    timbra(
        timbro = acquerello.ombra,
        centro = Offset(size.width * 0.52f, suolo),
        larghezza = unita * 1.15f,
        altezza = unita * 0.26f,
        tinta = lerp(SalaTokens.neutral900, Color.Black, palette.buio),
        alfa = lerp(0.13f, 0.22f, palette.buio),
    )

    // ── Il disco: si sposta e rimpicciolisce mentre il cielo si chiude ───────
    val discoRaggio = lerp(0.66f, 0.46f, c) * unita
    camera.place(
        lerp(0f, -0.30f, c) * unita,
        lerp(-0.04f, -0.32f, c) * unita,
        0.30f * unita,
    )
    val centroDisco = Offset(camera.sx, camera.sy)
    val diametro = discoRaggio * 2f * camera.scale
    val alfaDisco = lerp(0.88f, 0.78f, c)

    // **Il sole e la luna sono lo stesso timbro, nello stesso punto, con pesi
    // complementari.** Non e' solo piu' morbido del salto di prima: e'
    // un'immagine migliore. Il disco si **raffredda** dal giallo al grigio
    // mentre il terminatore lo morde, che e' esattamente cio' che fa il
    // crepuscolo. Un `if (notte)` non puo' dirlo.
    if (n < 0.999f) {
        timbra(
            timbro = acquerello.disco,
            centro = centroDisco,
            larghezza = diametro,
            altezza = diametro,
            tinta = SalaTokens.processYellow,
            alfa = alfaDisco * (1f - n),
        )
    }
    if (n > 0.001f && fase != null) {
        // La fase e' quella vera di stanotte, la stessa che mostra Sala IV: due
        // stanze della stessa galleria non possono raccontare due lune diverse
        // nella stessa notte. Il tondo spento sotto, e sopra la sola parte
        // illuminata - resta un timbro d'acquerello, non un disco piatto: la
        // fase decide **dove** il pigmento si posa, non come.
        timbra(acquerello.disco, centroDisco, diametro, diametro, SalaTokens.lunaOmbra, alfa = 0.30f * n)
        clipPath(parteIlluminata(centroDisco, diametro / 2f, fase)) {
            timbra(acquerello.disco, centroDisco, diametro, diametro, SalaTokens.lunaLuce, alfa = 0.94f * n)
        }
    }

    // ── Le masse, dalla piu' lontana alla piu' vicina ────────────────────────
    val tintaNube = tintaNuvola(palette)
    // **L'ordine si calcola su tutte e sette, sempre.** Dipende dalla
    // profondita' e non dalla presenza: se dipendesse dalla presenza, le masse
    // si riordinerebbero mentre una entra, e una nuvola che si rimescola
    // mentre compare e' peggio di una che compare di scatto.
    val indici = MasseNuvola.indices.sortedByDescending { i ->
        camera.place(MasseNuvola[i][0] * unita, MasseNuvola[i][1] * unita, MasseNuvola[i][2] * unita)
        camera.vz
    }
    indici.forEachIndexed { posto, i ->
        val p = presenzaMassa(i, c, scena.tempesta)
        if (p <= 0.004f) return@forEachIndexed
        val m = MasseNuvola[i]
        camera.place(m[0] * unita, m[1] * unita, m[2] * unita)
        // **Cresce mentre si posa, e l'alfa va al quadrato.** Nessuna delle due
        // risposte ovvie bastava da sola: con la sola opacita' compariva un
        // fantasma **a grandezza piena** al cinque per cento, che l'occhio legge
        // come un errore di resa e non come una cosa che arriva; con la sola
        // scala compariva un punto **pienamente opaco** che si gonfiava, cioe'
        // uno schiocco con una rampa incollata davanti. Insieme, e con l'alfa
        // che resta indietro finche' la massa e' piccola, e' pigmento che si
        // allarga sulla carta bagnata - l'unico modo in cui in questa sala una
        // cosa puo' comparire.
        val d = m[3] * FUSIONE * unita * 2f * camera.scale * lerp(0.55f, 1f, p)
        val piena = lerp(0.52f, 0.62f, scena.tempesta) -
            0.06f * (1f - posto.toFloat() / MasseNuvola.size)
        timbra(
            timbro = acquerello.macchie[i % acquerello.macchie.size],
            centro = Offset(camera.sx, camera.sy),
            larghezza = d,
            altezza = d * 0.92f,
            tinta = tintaNube,
            alfa = (piena * p * p).coerceIn(0f, 1f),
        )
    }

    // ── Cio' che cade, e cade fino a terra ───────────────────────────────────
    //
    // **Tre cadute sovrapposte, non una scelta fra tre.** Sono tre segni
    // genuinamente diversi (vedi `caduta`), quindi non si mescolano fra loro: si
    // sfumano l'una nell'altra condividendo corsie e orologio. Pioggia che gira
    // in neve sono due cadute negli stessi canali, ed e' esattamente com'e'
    // fatto il nevischio - senza una riga di geometria nuova.
    val origine = Offset(size.width * 0.52f, size.height * 0.46f)
    val asciutto = (1f - scena.neve) * (1f - scena.ghiaccio)
    caduta(
        tipo = Caduta.PIOGGIA, acquerello = acquerello, unita = unita, origine = origine,
        suolo = suolo, tempo = tempo, tinta = tintaPioggia(palette),
        presenza = scena.bagnato, peso = asciutto,
    )
    caduta(
        tipo = Caduta.NEVE, acquerello = acquerello, unita = unita, origine = origine,
        suolo = suolo, tempo = tempo, tinta = SalaTokens.neutral100,
        presenza = scena.bagnato, peso = scena.neve,
    )
    caduta(
        tipo = Caduta.GRANDINE, acquerello = acquerello, unita = unita, origine = origine,
        suolo = suolo, tempo = tempo, tinta = tintaPioggia(palette),
        presenza = scena.bagnato, peso = scena.ghiaccio,
    )

    // I primi lampi di un temporale arrivano tenui, non a piena forza.
    fulmine(unita, origine, tempo, SalaTokens.accent2, forza = scena.tempesta)

    // ── Gli uccelli ──────────────────────────────────────────────────────────
    //
    // Il cielo stellato non sta qui: e' a tutta pagina, dietro tutto, e lo
    // disegna la sala. Qui restano gli uccelli, che appartengono alla scultura
    // perche' misurano la sua distanza. Sfumano col chiudersi del cielo e col
    // calare della notte, non spariscono a una soglia: al crepuscolo si vedono
    // per qualche secondo uccelli tenui **e** stelle tenui insieme, che e' vero.
    uccelli(unita, origine, tempo, palette.ink, velo = (1f - c) * (1f - n))
}
