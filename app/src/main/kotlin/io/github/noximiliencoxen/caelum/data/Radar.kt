package io.github.noximiliencoxen.caelum.data

import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/** Gli estremi geografici di un'immagine, in gradi. */
data class RadarRiquadro(
    val latMin: Double,
    val lonMin: Double,
    val latMax: Double,
    val lonMax: Double,
) {
    fun contiene(lat: Double, lon: Double): Boolean =
        lat in latMin..latMax && lon in lonMin..lonMax
}

/** Una tessera del radar, coi propri estremi. */
class RadarTessera(val png: ByteArray, val riquadro: RadarRiquadro)

/**
 * Un fotogramma del radar: quando, cosa, e di chi e'.
 *
 * Le tessere sono piu' d'una perche' un radar a tessere si serve cosi': ognuna
 * copre un pezzo di mondo e porta i propri estremi, e chi disegna le posa una
 * per una. Un'unica immagine composta a monte costerebbe una bitmap in piu' in
 * memoria per non risparmiare niente: la carta le disegna comunque una per una.
 */
class RadarProdotto(
    val istante: Instant,
    val tessere: List<RadarTessera>,
    /**
     * La riga da scrivere **sotto la carta**.
     *
     * Viaggia col fotogramma e non sta scritta nell'interfaccia, ed e' una
     * differenza che si e' pagata per impararla: la carta e' nata per i dati
     * del Dipartimento della Protezione Civile e adesso mostra quelli di
     * RainViewer. Con l'attribuzione scritta dove si disegna, oggi ci sarebbe
     * **il nome sbagliato sopra i dati di un altro**.
     */
    val attribuzione: String,
)

/** Un fotogramma disponibile: quando e' stato misurato, e dove prenderlo. */
data class RadarFotogramma(val istante: Instant, val percorso: String)

/** L'elenco dei fotogrammi che il servizio ha in questo momento. */
data class RadarIndice(val host: String, val fotogrammi: List<RadarFotogramma>) {

    /**
     * Il fotogramma piu' vicino a un istante, se ce n'e' uno abbastanza vicino.
     *
     * **Il "se" e' tutta la funzione.** Senza, si prende sempre il piu' vicino
     * che esiste - cioe' il piu' recente - e lo si mostra sotto qualunque ora
     * scelta: e' quello che l'app faceva, e chi l'ha usata l'ha visto subito.
     * La barra diceva 22:00 e la carta era delle 15:40, ferma, uguale a se
     * stessa a ogni ora della giornata.
     *
     * Mostrare la pioggia di un'ora sotto l'etichetta di un'altra e' la stessa
     * bugia della galleria che ritraeva l'Ingresso in ogni scatto, o delle ore
     * di oggi sotto l'intestazione di giovedi': un dato vero, messo dove non e'
     * vero.
     */
    fun vicinoA(istante: Instant, tolleranza: Duration = TOLLERANZA): RadarFotogramma? =
        fotogrammi
            .minByOrNull { abs(it.istante.epochSecond - istante.epochSecond) }
            ?.takeIf { abs(it.istante.epochSecond - istante.epochSecond) <= tolleranza.seconds }

    /** Il piu' recente, che e' quello da mostrare quando si guarda "adesso". */
    val ultimo: RadarFotogramma? get() = fotogrammi.maxByOrNull { it.istante }

    companion object {
        /**
         * Quanto lontano puo' stare il fotogramma dall'ora scelta.
         *
         * I fotogrammi distano dieci minuti l'uno dall'altro, e la barra sceglie
         * ore intere: mezz'ora copre l'ora corrente e quella prima, e lascia
         * fuori tutto il resto. Allargarla vorrebbe dire far passare per "le
         * venti" una pioggia delle ventuno e mezza.
         */
        val TOLLERANZA: Duration = Duration.ofMinutes(30)
    }
}

/**
 * A che punto sta il radar, per chi deve disegnarlo.
 *
 * Quattro casi, e due sono stati aggiunti da chi l'app la usa davvero.
 *
 * **`FuoriCopertura` e' tornato.** Se n'era andato col Dipartimento della
 * Protezione Civile, perche' RainViewer non sembrava dichiarare dove arrivano
 * i suoi radar. Lo dichiara: una mappa di copertura servita a tessere, che si
 * legge **invertita** - opaco vuol dire che li' non guarda nessuno. Cinque
 * punti di prova, fra cui il Kansas e il mezzo del Pacifico (CONTESTO 18.5).
 *
 * **`FuoriOrario` e' nuovo**, ed e' il piu' importante: un radar **misura**, e
 * quello che non ha misurato non lo sa. Due ore di storico contro una barra che
 * sceglie ventiquattro ore: per quasi tutte le ore della giornata la risposta
 * onesta e' "non c'e' una fotografia di quel momento".
 */
sealed interface StatoRadar {
    /** Si sta chiedendo. */
    data object InCorso : StatoRadar

    /** C'e' un fotogramma da posare. */
    data class Pronto(val prodotto: RadarProdotto) : StatoRadar

    /** Li' non guarda nessun radar. Non e' un guasto, ed e' diverso da "non piove". */
    data object FuoriCopertura : StatoRadar

    /**
     * Un radar c'e', ma non ha una fotografia dell'ora scelta.
     *
     * [ultimo] e' l'istante del fotogramma piu' recente, perche' la frase da
     * scrivere non e' "non si sa" ma "si sa fino a quest'ora".
     *
     * **C'e' stato un tentativo di riempire queste ore con la previsione del
     * modello, ed e' stato tolto.** Funzionava e diceva il vero, ma un modello
     * da' un valore ogni quaranta chilometri: accanto a una tessera radar da
     * un chilometro per pixel sembrava - ed era - un'altra cosa. Vedi
     * CONTESTO 22: la differenza non era di disegno, era di dato, e nessun
     * disegno la colma senza fingere una precisione che non c'e'.
     */
    data class FuoriOrario(val ultimo: Instant?) : StatoRadar

    /**
     * Ha risposto male, o non ha risposto.
     *
     * [indizio] e' quello che si e' visto, e finisce **sullo schermo**. E'
     * brutto da leggere e sta li' apposta: e' la riga che ha detto, dal
     * telefono e non da una sonda, che il servizio del Dipartimento della
     * Protezione Civile risponde 403 anche a un chiamante che si presenta con
     * nome e cognome. Senza quella riga si sarebbe continuato a credere che
     * fosse un problema di datacentro.
     */
    data class NonDisponibile(val indizio: String?) : StatoRadar
}

/**
 * La matematica delle tessere, che e' quella di ogni mappa a tessere del web.
 *
 * **Mercatore, e non per gusto.** Le tessere di RainViewer - come quelle di
 * OpenStreetMap, di Google e di chiunque altro - sono disegnate in proiezione
 * di Mercatore sferica: la longitudine e' lineare, la latitudine no. Posarle su
 * una carta che tratta la latitudine come lineare le stira, e lo stira **di
 * piu' ai bordi**: la pioggia finirebbe qualche chilometro piu' a nord o piu' a
 * sud di dov'e', e piu' ci si allontana dal centro peggio e'.
 *
 * La carta di Caelum era equirettangolare, e c'era un commento che avvisava che
 * sarebbe stato questo il punto da cambiare il giorno in cui fosse arrivata una
 * fonte a tessere. E' arrivata.
 *
 * Tutto quello che sta qui dentro e' **aritmetica pura**: nessuna rete, nessun
 * Android, nessun disegno. E' l'unica parte del radar che si possa provare
 * senza un servizio che risponda, ed e' il motivo per cui sta in un oggetto suo
 * e non dentro il repository.
 */
object RadarTessere {

    /**
     * Quanto sono fini le tessere che si chiedono.
     *
     * A questo livello una tessera copre poco meno di tre gradi di longitudine,
     * e la finestra della carta - tre gradi di latitudine attorno a casa - ne
     * vuole una manciata. Un livello piu' fitto vorrebbe quattro volte le
     * tessere per un dettaglio che su una carta alta centosettantasei punti non
     * si vedrebbe; uno piu' grosso farebbe una macchia di pioggia larga come
     * una provincia.
     */
    const val ZOOM = 7

    /** Il lato di una tessera, in pixel. RainViewer le serve 256 o 512. */
    const val LATO = 256

    /** Quante tessere ha il mondo a questo livello, per lato. */
    private val QUANTE = 1 shl ZOOM

    /**
     * La latitudine oltre la quale Mercatore non ci arriva.
     *
     * A ottantacinque gradi e mezzo la proiezione manda i poli all'infinito, e
     * ogni mappa a tessere del web taglia li'. Serve a non chiedere tessere che
     * non esistono per una localita' polare.
     */
    const val LIMITE_MERCATORE = 85.05112878

    /** La y di Mercatore di una latitudine, in unita' arbitrarie ma coerenti. */
    fun mercatore(lat: Double): Double {
        val l = lat.coerceIn(-LIMITE_MERCATORE, LIMITE_MERCATORE)
        return ln(tan(PI / 4 + l * PI / 360))
    }

    /** L'indice di colonna della tessera che contiene questa longitudine. */
    fun colonna(lon: Double): Int =
        floor((lon + 180.0) / 360.0 * QUANTE).toInt().coerceIn(0, QUANTE - 1)

    /** L'indice di riga della tessera che contiene questa latitudine. */
    fun riga(lat: Double): Int {
        val l = lat.coerceIn(-LIMITE_MERCATORE, LIMITE_MERCATORE) * PI / 180
        val y = (1.0 - asinh(tan(l)) / PI) / 2.0
        return floor(y * QUANTE).toInt().coerceIn(0, QUANTE - 1)
    }

    /**
     * Dove cade un punto **dentro** la propria tessera, in pixel.
     *
     * Serve a leggere la maschera di copertura: la tessera dice se in quella
     * zona ci sono radar, ma "quella zona" e' larga duecento chilometri - la
     * risposta che interessa e' quella del pixel di casa propria.
     */
    fun pixelDentroLaTessera(lat: Double, lon: Double, lato: Int): Pair<Int, Int> {
        val fx = (lon + 180.0) / 360.0 * QUANTE
        val l = lat.coerceIn(-LIMITE_MERCATORE, LIMITE_MERCATORE) * PI / 180
        val fy = (1.0 - asinh(tan(l)) / PI) / 2.0 * QUANTE
        return Pair(
            ((fx - floor(fx)) * lato).toInt().coerceIn(0, lato - 1),
            ((fy - floor(fy)) * lato).toInt().coerceIn(0, lato - 1),
        )
    }

    /** Gli estremi geografici di una tessera. */
    fun riquadroDi(colonna: Int, riga: Int): RadarRiquadro {
        val lonMin = colonna.toDouble() / QUANTE * 360.0 - 180.0
        val lonMax = (colonna + 1).toDouble() / QUANTE * 360.0 - 180.0
        return RadarRiquadro(
            latMin = latitudineDiRiga(riga + 1),
            lonMin = lonMin,
            latMax = latitudineDiRiga(riga),
            lonMax = lonMax,
        )
    }

    private fun latitudineDiRiga(riga: Int): Double {
        val n = PI - 2.0 * PI * riga / QUANTE
        return 180.0 / PI * atan(sinh(n))
    }

    /**
     * Il pezzo di mondo da chiedere, attorno a una localita'.
     *
     * **E' piu' largo di quello che si disegna, ed e' voluto.** La finestra
     * disegnata dipende da quanto e' larga la carta sullo schermo, che dipende
     * dal telefono; questa no, e la contiene in ogni caso plausibile - anche a
     * Reykjavik, dove i meridiani si stringono tanto che la stessa carta copre
     * il doppio dei gradi di longitudine che a Forli'. Le tessere in piu' le
     * ritaglia il disegno, e sono zero byte sprecati: a questo livello un
     * grado in piu' o in meno quasi sempre cade nella stessa tessera.
     *
     * L'alternativa - far calcolare la finestra a chi disegna e passarla a chi
     * scarica - legherebbe una richiesta di rete alla larghezza di un
     * riquadro, cioe' la farebbe ripartire a ogni rotazione dello schermo.
     */
    fun finestraDaChiedere(lat: Double, lon: Double): RadarRiquadro = RadarRiquadro(
        latMin = lat - 1.6,
        lonMin = lon - 5.0,
        latMax = lat + 1.6,
        lonMax = lon + 5.0,
    )

    /**
     * Le tessere che coprono una finestra, in ordine di lettura.
     *
     * **C'e' un tetto, e non e' prudenza generica.** Il numero di tessere
     * cresce col quadrato della finestra, e una finestra assurda - per un
     * errore di conto, per una localita' vicina all'antimeridiano - chiederebbe
     * centinaia di immagini a un servizio gratuito. Il tetto le ferma a
     * ventiquattro: meglio una carta parziale che un'app che martella qualcun
     * altro.
     *
     * Ventiquattro e non sedici perche' le finestre vere ne chiedono fino a
     * sedici - a Bergen e a Reykjavik esattamente sedici - e un tetto che il
     * caso normale tocca non e' un tetto, e' un ritaglio silenzioso.
     */
    fun coprono(finestra: RadarRiquadro, massimo: Int = 24): List<Pair<Int, Int>> {
        val da = colonna(finestra.lonMin)
        val a = colonna(finestra.lonMax)
        // Le righe si contano dall'alto: la riga della latitudine massima e' la
        // piu' piccola.
        val su = riga(finestra.latMax)
        val giu = riga(finestra.latMin)
        var colonne = (da..a).toList()
        var righe = (su..giu).toList()
        // **Se si sfora, si taglia dal centro e non dal bordo.** Tagliare
        // dall'inizio lascerebbe scoperto proprio il posto che si sta
        // guardando, che sta in mezzo: una carta vuota sotto il puntino e
        // piena a nord-ovest.
        while (colonne.size * righe.size > massimo) {
            if (colonne.size >= righe.size) colonne = attornoAlCentro(colonne)
            else righe = attornoAlCentro(righe)
        }
        return righe.flatMap { r -> colonne.map { c -> c to r } }
    }

    private fun attornoAlCentro(indici: List<Int>): List<Int> =
        if (indici.size <= 1) indici else indici.drop(1).dropLast(if (indici.size > 2) 1 else 0)
}
