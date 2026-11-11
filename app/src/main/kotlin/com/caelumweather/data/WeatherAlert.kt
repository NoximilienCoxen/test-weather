package com.caelumweather.data

import com.caelumweather.lingua.tr
import java.time.LocalDateTime

/**
 * Un'allerta meteo, da qualunque parte venga.
 *
 * Il modello e' uno solo per due fonti diverse apposta. Le allerte ufficiali
 * arrivano da MeteoAlarm - il canale di EUMETNET su cui i servizi
 * meteorologici nazionali pubblicano i propri avvisi, che per l'Italia sono
 * quelli dell'Aeronautica Militare - e coprono l'Europa. Sono allerte meteo,
 * non i messaggi di allertamento della Protezione Civile: lo dichiara il
 * testo stesso di ogni voce (vedi `WeatherAlertsRepository`). Fuori da li' non c'e' nessuno che le pubblichi in un formato
 * leggibile da un'app senza accordi, e una schermata che non dice niente su
 * mezzo mondo non e' una funzione: quelle calcolate dalle soglie riempiono il
 * buco.
 *
 * [official] tiene distinte le due cose, e non e' un dettaglio da nascondere.
 * "Il servizio meteorologico ha emesso un'allerta arancione" e "domani sono
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
    /** Da quale canale e' arrivata, se ufficiale: decide credito e licenza. */
    val fonte: FonteAllerte? = null,
)

/**
 * I canali da cui arrivano le allerte ufficiali, uno per area del mondo.
 *
 * Ciascuno ha la sua licenza e il suo credito, e il bollettino li scrive per
 * quelli da cui viene cio' che mostra: un'allerta del National Weather Service
 * sotto "dati dei membri di EUMETNET" sarebbe un credito sbagliato.
 */
enum class FonteAllerte(val nome: String, val sito: String, val indirizzo: String) {
    /** L'Europa: i servizi meteorologici nazionali membri di EUMETNET. */
    METEOALARM("MeteoAlarm", "meteoalarm.org", "https://meteoalarm.org/"),

    /** Gli Stati Uniti: il servizio meteorologico federale (NOAA). */
    NWS("National Weather Service", "weather.gov", "https://www.weather.gov/"),

    /** Il Canada: Environment and Climate Change Canada. */
    ECCC("Environment and Climate Change Canada", "weather.gc.ca", "https://weather.gc.ca/"),

    /** Il Giappone: l'Agenzia meteorologica (気象庁). */
    JMA("Japan Meteorological Agency", "jma.go.jp", "https://www.jma.go.jp/"),

    /** Tutti gli altri: i feed CAP dei servizi nazionali, raccolti dall'IFRC. */
    IFRC("IFRC Alert Hub", "alerthub.ifrc.org", "https://alerthub.ifrc.org/"),
    ;

    /** Dove serve, detto per chi legge. */
    val zona: String
        get() = when (this) {
            METEOALARM -> tr("Europa", "Europe", "Europa", "Europe", "Europa")
            NWS -> tr("Stati Uniti", "United States", "USA", "États-Unis", "Estados Unidos")
            ECCC -> "Canada"
            JMA -> tr("Giappone", "Japan", "Japan", "Japon", "Japón")
            IFRC -> tr("gli altri paesi", "other countries", "die übrigen Länder", "les autres pays", "los demás países")
        }

    companion object {
        /** "MeteoAlarm (Europa), National Weather Service (Stati Uniti), ...": per i testi. */
        val elenco: String get() = entries.joinToString(", ") { "${it.nome} (${it.zona})" }
    }

    /** Il credito, nella forma che chiede la licenza della fonte. */
    val credito: String
        get() = when (this) {
            METEOALARM -> tr(
                "Allerte ufficiali: MeteoAlarm, dati dei membri di EUMETNET, licenza CC BY 4.0.",
                "Official warnings: MeteoAlarm, data provided by EUMETNET members, CC BY 4.0 licence.",
                "Amtliche Warnungen: MeteoAlarm, Daten der EUMETNET-Mitglieder, Lizenz CC BY 4.0.",
                "Alertes officielles : MeteoAlarm, données des membres d'EUMETNET, licence CC BY 4.0.",
                "Alertas oficiales: MeteoAlarm, datos de los miembros de EUMETNET, licencia CC BY 4.0.",
            )
            NWS -> tr(
                "Allerte ufficiali: National Weather Service (NOAA), dati di pubblico dominio.",
                "Official warnings: National Weather Service (NOAA), public domain data.",
                "Amtliche Warnungen: National Weather Service (NOAA), gemeinfreie Daten.",
                "Alertes officielles : National Weather Service (NOAA), données du domaine public.",
                "Alertas oficiales: National Weather Service (NOAA), datos de dominio público.",
            )
            ECCC -> tr(
                "Allerte ufficiali: Environment and Climate Change Canada, Open Government Licence - Canada.",
                "Official warnings: Environment and Climate Change Canada, Open Government Licence - Canada.",
                "Amtliche Warnungen: Environment and Climate Change Canada, Open Government Licence - Canada.",
                "Alertes officielles : Environment and Climate Change Canada, Open Government Licence - Canada.",
                "Alertas oficiales: Environment and Climate Change Canada, Open Government Licence - Canada.",
            )
            JMA -> tr(
                "Allerte ufficiali: Japan Meteorological Agency (気象庁), dati riutilizzabili citando la fonte.",
                "Official warnings: Japan Meteorological Agency (気象庁), reusable with attribution.",
                "Amtliche Warnungen: Japan Meteorological Agency (気象庁), mit Quellenangabe weiterverwendbar.",
                "Alertes officielles : Japan Meteorological Agency (気象庁), données réutilisables en citant la source.",
                "Alertas oficiales: Japan Meteorological Agency (気象庁), datos reutilizables citando la fuente.",
            )
            IFRC -> tr(
                "Allerte ufficiali: i servizi meteorologici nazionali, raccolte dall'IFRC Alert Hub " +
                    "(Federazione internazionale della Croce Rossa) dai loro feed CAP.",
                "Official warnings: the national weather services, collected by the IFRC Alert Hub " +
                    "(International Federation of Red Cross) from their CAP feeds.",
                "Amtliche Warnungen: die nationalen Wetterdienste, vom IFRC Alert Hub (Internationale Föderation der Rotkreuz- und Rothalbmondgesellschaften) aus ihren CAP-Feeds gesammelt.",
                "Alertes officielles : les services météorologiques nationaux, rassemblées par l'IFRC Alert Hub (Fédération internationale de la Croix-Rouge) à partir de leurs flux CAP.",
                "Alertas oficiales: los servicios meteorológicos nacionales, recogidas por el IFRC Alert Hub (Federación Internacional de la Cruz Roja) a partir de sus canales CAP.",
            )
        }
}

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
val SOGLIA_LABEL: String get() = tr("SOGLIA SUPERATA", "THRESHOLD EXCEEDED", "SCHWELLE ÜBERSCHRITTEN", "SEUIL DÉPASSÉ", "UMBRAL SUPERADO")

val SOGLIA_SHORT: String get() = tr("SOGLIA", "THRESHOLD", "SCHWELLE", "SEUIL", "UMBRAL")

/**
 * La gravita', nei tre gradini che l'Italia usa a voce.
 *
 * MeteoAlarm ne usa quattro - verde, giallo, arancione, rosso - ma il verde
 * vuol dire "nessun avviso": un'app che mostrasse una fascia per dire che non
 * succede niente insegnerebbe a ignorare la fascia. Il verde si scarta alla
 * fonte e qui restano i tre che vale la pena leggere.
 */
enum class AlertLevel(private val nomi: List<String>, private val brevi: List<String>, val weight: Int) {
    // Nell'ordine di sempre: italiano, inglese, tedesco, francese, spagnolo.
    GIALLA(
        listOf("ALLERTA GIALLA", "YELLOW WARNING", "WARNSTUFE GELB", "ALERTE JAUNE", "ALERTA AMARILLA"),
        listOf("GIALLA", "YELLOW", "GELB", "JAUNE", "AMARILLA"),
        1,
    ),
    ARANCIONE(
        listOf("ALLERTA ARANCIONE", "ORANGE WARNING", "WARNSTUFE ORANGE", "ALERTE ORANGE", "ALERTA NARANJA"),
        listOf("ARANCIONE", "ORANGE", "ORANGE", "ORANGE", "NARANJA"),
        2,
    ),
    ROSSA(
        listOf("ALLERTA ROSSA", "RED WARNING", "WARNSTUFE ROT", "ALERTE ROUGE", "ALERTA ROJA"),
        listOf("ROSSA", "RED", "ROT", "ROUGE", "ROJA"),
        3,
    ),
    ;

    /** "ALLERTA ARANCIONE", "ORANGE WARNING": il nome per esteso. In inglese
     *  "warning" e' la parola dei servizi meteorologici (Met Office). */
    val label: String get() = tr(nomi[0], nomi[1], nomi[2], nomi[3], nomi[4])

    /** Il solo colore, per la pastiglia: "ARANCIONE", "ORANGE". */
    val breve: String get() = tr(brevi[0], brevi[1], brevi[2], brevi[3], brevi[4])
}

/**
 * Di cosa avvisa.
 *
 * I nomi sono quelli dei fenomeni, non i codici della fonte. MeteoAlarm scrive
 * il tipo dentro una frase inglese - "Yellow High-temperature Warning" - e a
 * riconoscerlo pensa `FeedEntry.kind`; qui restano solo le parole che vanno a
 * schermo.
 */
enum class AlertKind(private val ita: String, private val eng: String, private val ted: String, private val fra: String, private val spa: String) {
    VENTO("VENTO", "WIND", "WIND", "VENT", "VIENTO"),
    PIOGGIA("PIOGGIA", "RAIN", "REGEN", "PLUIE", "LLUVIA"),
    TEMPORALI("TEMPORALI", "THUNDERSTORMS", "GEWITTER", "ORAGES", "TORMENTAS"),
    NEVE_GHIACCIO("NEVE E GHIACCIO", "SNOW AND ICE", "SCHNEE UND EIS", "NEIGE ET VERGLAS", "NIEVE Y HIELO"),
    CALDO("CALDO", "HEAT", "HITZE", "CHALEUR", "CALOR"),
    FREDDO("FREDDO", "COLD", "KÄLTE", "FROID", "FRÍO"),
    /** Nessun ente la emette: e' solo calcolata. Esiste perche' nelle
     *  impostazioni c'era da sempre un interruttore "Raggi UV sopra 6" che non
     *  accendeva e non spegneva niente - dietro non c'era nessun avviso. */
    UV("RAGGI UV", "UV RAYS", "UV-STRAHLUNG", "RAYONS UV", "RAYOS UV"),
    NEBBIA("NEBBIA", "FOG", "NEBEL", "BROUILLARD", "NIEBLA"),
    COSTIERO("MAREGGIATE", "COASTAL EVENTS", "STURMFLUT", "VAGUES-SUBMERSION", "FENÓMENOS COSTEROS"),
    INCENDI("INCENDI", "FOREST FIRES", "WALDBRÄNDE", "FEUX DE FORÊT", "INCENDIOS"),
    VALANGHE("VALANGHE", "AVALANCHES", "LAWINEN", "AVALANCHES", "ALUDES"),
    ALTRO("AVVISO", "WARNING", "WARNUNG", "ALERTE", "ALERTA"),
    ;

    val label: String get() = tr(ita, eng, ted, fra, spa)
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


/**
 * Com'e' andata l'ultima richiesta delle allerte ufficiali.
 *
 * **Una lista vuota non basta a dirlo.** Per tutto il tempo in cui MeteoAlarm
 * rispondeva 406 (CONTESTO §8-ter) la lista era vuota esattamente come in una
 * giornata tranquilla, e il bollettino scriveva "non è arrivata nessuna allerta
 * ufficiale" sopra un controllo che non era mai avvenuto. Quattro stati e non
 * due, come per l'aria: fuori copertura non e' un guasto, e in attesa non e'
 * ne' l'uno ne' l'altro.
 */
enum class StatoAllerteUfficiali {
    /** Non ancora chieste per il posto mostrato, o la risposta e' in volo. */
    IN_ATTESA,

    /** Il feed ha risposto: la lista dice davvero cosa c'e'. */
    ARRIVATE,

    /** MeteoAlarm non copre questo paese: non c'e' niente da chiedere. */
    FUORI_COPERTURA,

    /** La richiesta e' fallita: nessuno ha controllato. */
    NON_ARRIVATE,
}
