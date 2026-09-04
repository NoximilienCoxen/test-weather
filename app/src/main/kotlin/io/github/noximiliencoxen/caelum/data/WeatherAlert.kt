package io.github.noximiliencoxen.caelum.data

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
 * La gravita', nei tre gradini che l'Italia usa a voce.
 *
 * MeteoAlarm ne usa quattro - verde, giallo, arancione, rosso - ma il verde
 * vuol dire "nessun avviso": un'app che mostrasse una fascia per dire che non
 * succede niente insegnerebbe a ignorare la fascia. Il verde si scarta alla
 * fonte e qui restano i tre che vale la pena leggere.
 */
enum class AlertLevel(val label: String, val weight: Int) {
    GIALLA("ALLERTA GIALLA", 1),
    ARANCIONE("ALLERTA ARANCIONE", 2),
    ROSSA("ALLERTA ROSSA", 3),
}

/**
 * Di cosa avvisa.
 *
 * I nomi sono quelli dei fenomeni, non i codici della fonte. MeteoAlarm scrive
 * il tipo dentro una frase inglese - "Yellow High-temperature Warning" - e a
 * riconoscerlo pensa `FeedEntry.kind`; qui restano solo le parole che vanno a
 * schermo.
 */
enum class AlertKind(val label: String) {
    VENTO("VENTO"),
    PIOGGIA("PIOGGIA"),
    TEMPORALI("TEMPORALI"),
    NEVE_GHIACCIO("NEVE E GHIACCIO"),
    CALDO("CALDO"),
    FREDDO("FREDDO"),
    NEBBIA("NEBBIA"),
    COSTIERO("MAREGGIATE"),
    INCENDI("INCENDI"),
    VALANGHE("VALANGHE"),
    ALTRO("AVVISO"),
}
