package com.forli.meteo.data

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
 * MeteoAlarm ne dichiara quattro (`awareness_level` da 1 a 4) ma il primo e'
 * verde, cioe' "nessun avviso": un'app che mostrasse una fascia per dire che
 * non succede niente insegnerebbe a ignorare la fascia. Il verde si scarta
 * alla fonte e qui restano i tre che valgono la pena di essere letti.
 */
enum class AlertLevel(val label: String, val weight: Int) {
    GIALLA("ALLERTA GIALLA", 1),
    ARANCIONE("ALLERTA ARANCIONE", 2),
    ROSSA("ALLERTA ROSSA", 3),
    ;

    companion object {
        /** Da `awareness_level` di MeteoAlarm. Verde e ignoto tornano nulli. */
        fun ofAwareness(value: Int?): AlertLevel? = when (value) {
            2 -> GIALLA
            3 -> ARANCIONE
            4 -> ROSSA
            else -> null
        }
    }
}

/**
 * Di cosa avvisa.
 *
 * I nomi sono quelli dei fenomeni, non i codici della fonte: `awareness_type`
 * di MeteoAlarm e' un numero, e un numero in cima allo schermo non avvisa
 * nessuno.
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
    ;

    companion object {
        /**
         * Da `awareness_type` di MeteoAlarm.
         *
         * La numerazione e' quella pubblicata da EUMETNET e non e' contigua:
         * i valori mancanti non sono buchi da riempire a intuito, sono codici
         * che non esistono. Tutto cio' che non si riconosce diventa [ALTRO],
         * che si mostra comunque - un avviso di cui non si sa il tipo resta un
         * avviso, e buttarlo sarebbe il modo peggiore di gestire l'ignoto.
         */
        fun ofAwareness(value: Int?): AlertKind = when (value) {
            1 -> VENTO
            2 -> NEVE_GHIACCIO
            3 -> TEMPORALI
            4 -> NEBBIA
            5 -> CALDO
            6 -> FREDDO
            7 -> COSTIERO
            8 -> INCENDI
            9 -> NEVE_GHIACCIO
            10 -> PIOGGIA
            11 -> VALANGHE
            12 -> PIOGGIA
            13 -> VALANGHE
            else -> ALTRO
        }
    }
}
