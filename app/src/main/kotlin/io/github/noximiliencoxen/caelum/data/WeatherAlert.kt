package io.github.noximiliencoxen.caelum.data

import io.github.noximiliencoxen.caelum.lingua.tr
import java.time.LocalDateTime

/**
 * Un'allerta meteo, da qualunque parte venga.
 *
 * Il modello e' uno solo per due fonti diverse apposta. Le allerte ufficiali
 * arrivano da MeteoAlarm - il canale di EUMETNET su cui i servizi nazionali
 * pubblicano i propri bollettini, che per l'Italia sono quelli del Dipartimento
 * della Protezione Civile e dei centri funzionali regionali - e coprono
 * l'Europa. Fuori da li' non c'e' nessuno che le pubblichi in un formato
 * leggibile da un'app senza accordi, e una schermata che non dice niente su
 * mezzo mondo non e' una funzione: quelle calcolate dalle soglie riempiono il
 * buco.
 *
 * [official] tiene distinte le due cose, e non e' un dettaglio da nascondere.
 * "La Protezione Civile ha diramato un'allerta arancione" e "domani sono
 * previsti novanta chilometri orari di raffica" sono due affermazioni con un
 * peso diverso, e chi legge ha diritto di sapere quale delle due sta leggendo.
 */
data class WeatherAlert(
    /** Identificativo stabile: serve a non mostrare due volte la stessa cosa. */
    val id: String,
    val level: AlertLevel,
    val kind: AlertKind,
    /** La riga che si legge nella fascia. Gia' pronta, gia' in italiano. */
    val headline: String,
    val description: String? = null,
    val instruction: String? = null,
    val onset: LocalDateTime? = null,
    val expires: LocalDateTime? = null,
    /** Il nome dell'area cui l'avviso si riferisce, come lo scrive la fonte. */
    val areaDesc: String? = null,
    /** Chi lo dice, scritto per esteso in fondo al bollettino. */
    val source: String,
    /** Vero per i bollettini di un ente, falso per quelli calcolati dai dati. */
    val official: Boolean,
)

/**
 * Come l'avviso si annuncia, per esteso.
 *
 * **Un avviso calcolato non si chiama "allerta gialla".** Giallo, arancione e
 * rosso non sono tre aggettivi: sono i gradini del sistema di allertamento
 * nazionale, e chi li legge capisce - giustamente - che a dirli e' stata la
 * Protezione Civile. Un confronto fra una raffica e una costante scritta in
 * `DerivedAlerts.kt` non ha quel peso e non deve prendersi quelle parole: la
 * fascia direbbe la stessa cosa di un bollettino vero, e il silenzio dell'app
 * si leggerebbe come il silenzio dell'ente.
 *
 * Non e' una cautela estetica. Presentare un'informazione come se venisse da
 * un'altra fonte e' esattamente cio' che gli artt. 21-22 del Codice del
 * consumo chiamano ingannevole, e il caso pericoloso e' quello **al
 * contrario**: chi non vede nessuna "gialla" da' per buono che nessuno l'abbia
 * diramata.
 *
 * Sta nei dati e non nelle schermate: e' una regola sul dominio - come questo
 * avviso ha diritto di presentarsi - e da qui si prova senza far partire niente
 * di Android. Tutte le superfici che lo mostrano leggono questo, quindi non
 * possono divergere.
 */
val WeatherAlert.badgeLabel: String
    get() = if (official) level.label else SOGLIA_LABEL

/**
 * La stessa cosa, accorciata per la riga della fascia.
 *
 * Li' lo spazio e' una riga sola e il "ALLERTA " davanti se n'era gia' andato
 * per quello (vedi `ui/alerts/AlertBanner.kt`): il segno accanto dice gia' che
 * e' un avviso, la parola dice solo di che tipo.
 */
val WeatherAlert.shortBadge: String
    get() = if (official) level.breve else SOGLIA_SHORT

/** Come si chiama un avviso nato da una soglia, per esteso e in breve. */
val SOGLIA_LABEL: String get() = tr("SOGLIA SUPERATA", "THRESHOLD EXCEEDED")

val SOGLIA_SHORT: String get() = tr("SOGLIA", "THRESHOLD")

/**
 * La gravita', nei tre gradini che l'Italia usa a voce.
 *
 * MeteoAlarm ne usa quattro - verde, giallo, arancione, rosso - ma il verde
 * vuol dire "nessun avviso": un'app che mostrasse una fascia per dire che non
 * succede niente insegnerebbe a ignorare la fascia. Il verde si scarta alla
 * fonte e qui restano i tre che vale la pena leggere.
 */
enum class AlertLevel(private val colore: String, private val colour: String, val weight: Int) {
    GIALLA("GIALLA", "YELLOW", 1),
    ARANCIONE("ARANCIONE", "ORANGE", 2),
    ROSSA("ROSSA", "RED", 3),
    ;

    /** "ALLERTA GIALLA" / "YELLOW WARNING". */
    val label: String get() = tr("ALLERTA $colore", "$colour WARNING")

    /** Solo il colore, per la riga stretta della fascia. */
    val breve: String get() = tr(colore, colour)
}

/**
 * Di cosa avvisa.
 *
 * I nomi sono quelli dei fenomeni, non i codici della fonte. MeteoAlarm scrive
 * il tipo dentro una frase inglese - "Yellow High-temperature Warning" - e a
 * riconoscerlo pensa `FeedEntry.kind`; qui restano solo le parole che vanno a
 * schermo.
 */
enum class AlertKind(private val italiano: String, private val inglese: String) {
    VENTO("VENTO", "WIND"),
    PIOGGIA("PIOGGIA", "RAIN"),
    TEMPORALI("TEMPORALI", "THUNDERSTORMS"),
    NEVE_GHIACCIO("NEVE E GHIACCIO", "SNOW AND ICE"),
    CALDO("CALDO", "HEAT"),
    FREDDO("FREDDO", "COLD"),
    /** Nessun ente la emette: e' solo calcolata. Esiste perche' nelle
     *  impostazioni c'era da sempre un interruttore "Raggi UV sopra 6" che non
     *  accendeva e non spegneva niente - dietro non c'era nessun avviso. */
    UV("RAGGI UV", "UV RAYS"),
    NEBBIA("NEBBIA", "FOG"),
    COSTIERO("MAREGGIATE", "COASTAL"),
    INCENDI("INCENDI", "WILDFIRES"),
    VALANGHE("VALANGHE", "AVALANCHES"),
    ALTRO("AVVISO", "ALERT"),
    ;

    val label: String get() = tr(italiano, inglese)
}

// **`alertsAreDismissed` non c'e' piu', e la regola che portava merita di
// restare scritta.** Diceva quando un avviso gia' visto e archiviato torna a
// essere una notizia: la fascia resta ridotta se e solo se ogni allerta in
// scena era gia' fra quelle chiuse **e** la peggiore di adesso non e' piu'
// grave della peggiore di allora. Un'allerta nuova la riapre; un peggioramento
// la riapre pur senza allerte nuove - la gialla che diventa arancione ha lo
// stesso identificativo e non e' la stessa notizia; una che scade no.
//
// Se n'e' andata perche' la fascia che si riduceva a pallino e' uscita col
// feed: la regola non la chiedeva piu' nessuno, e l'unica cosa che la teneva in
// vita era la cattura della CI, che fotografava uno stato irraggiungibile col
// dito. Quando la fascia tornera', questa e' la regola da rimettere - non un
// booleano "gia' vista".

