package io.github.noximiliencoxen.caelum.data

import java.time.LocalDate
import java.time.LocalDateTime

data class CurrentWeather(
    val temperature: Double? = null,
    val apparent: Double? = null,
    val humidity: Double? = null,
    val dewPoint: Double? = null,
    val precipitation: Double? = null,
    val weatherCode: Int? = null,
    val windSpeed: Double? = null,
    val windDirection: Double? = null,
    val windGusts: Double? = null,
    /** Vero di giorno: sceglie fra sole e luna nell'icona del widget. */
    val isDay: Boolean = true,
)

data class DayForecast(
    val date: LocalDate,
    /** Sigla mostrata nella striscia in fondo: OGGI, DOM, LUN... */
    val label: String,
    val weatherCode: Int? = null,
    val tempMax: Double? = null,
    val tempMin: Double? = null,
    val apparentMax: Double? = null,
    val apparentMin: Double? = null,
    val humidityMean: Double? = null,
    val dewPointMean: Double? = null,
    val precipitationSum: Double? = null,
    val precipProbability: Int? = null,
    val precipHours: Double? = null,
    val windMax: Double? = null,
    val gustMax: Double? = null,
    val windDirection: Double? = null,
    val uvMax: Double? = null,
    /** Alba e tramonto: governano il colore del sole e quello del cielo. */
    val sunrise: LocalDateTime? = null,
    val sunset: LocalDateTime? = null,
    /**
     * Media storica della temperatura per questo mese del calendario.
     *
     * Ricavata dall'API archivio storico (ultimi 10 anni, stesso mese):
     * e' la "Norma" che si vede come linea tratteggiata nel grafico del
     * dettaglio del giorno. Nulla se la chiamata non e' ancora tornata o
     * e' fallita: il grafico la disegna solo quando e' disponibile.
     */
    val normTemp: Double? = null,
    /** Millimetri di sola pioggia, distinti dalla neve dentro il totale. */
    val rainSum: Double? = null,
    /** Centimetri di neve. */
    val snowfallSum: Double? = null,
    /**
     * Secondi di sole effettivo, che non sono le ore di luce.
     *
     * Sotto un cielo coperto la luce fra alba e tramonto e' la stessa e il sole
     * e' zero: mostrare la prima chiamandola "luce solare", come si faceva,
     * dice a chi guarda esattamente il contrario di quello che vedra' uscendo.
     */
    val sunshineSeconds: Double? = null,
)

/** Un'ora della previsione: e' l'unita' su cui scorre la schermata principale. */
data class HourForecast(
    val time: LocalDateTime,
    val temperature: Double? = null,
    val apparent: Double? = null,
    val weatherCode: Int? = null,
    /** Millimetri: dice quanto forte, non quanto probabile. */
    val precipitation: Double? = null,
    /** Percentuale: dice quanto probabile, non quanto forte. */
    val precipProbability: Int? = null,
    val isDay: Boolean = true,
    /**
     * Le grandezze orarie che prima non si chiedevano.
     *
     * Senza di queste il dettaglio non aveva scelta: o mostrava il valore di
     * `current` - cioe' di adesso - sotto un'intestazione che dichiarava
     * un'altra ora, o non mostrava niente. Il grafico del vento faceva la
     * seconda, e usciva vuoto senza dirlo.
     */
    val humidity: Double? = null,
    val dewPoint: Double? = null,
    val windSpeed: Double? = null,
    val windGusts: Double? = null,
    val windDirection: Double? = null,
    val uvIndex: Double? = null,
    /** Copertura nuvolosa in percentuale. */
    val cloudCover: Int? = null,
    /** Pressione al suolo in hPa. */
    val pressure: Double? = null,
    /** Visibilita' in metri. */
    val visibility: Double? = null,
    /** Millimetri di sola pioggia. */
    val rain: Double? = null,
    /** Centimetri di neve. */
    val snowfall: Double? = null,
)

data class Forecast(
    val current: CurrentWeather,
    val days: List<DayForecast>,
    /**
     * Le ore della giornata mostrata dalla barra della schermata principale.
     *
     * Sono le prime ventiquattro, e restano tali: la barra racconta un giorno,
     * l'indice dell'ora selezionata conta su questa lista, e i widget pescano
     * di qui. Chi vuole la settimana intera guarda [allHours].
     */
    val hours: List<HourForecast> = emptyList(),
    /**
     * Tutte le ore della previsione, sette giorni compresi.
     *
     * Vive accanto a [hours] invece di sostituirla perche' i grafici del
     * dettaglio hanno bisogno delle ore dei giorni successivi, ma qualunque
     * cosa conti indici sulle ore - l'ora scelta, `nowIndex`, la striscia del
     * widget - da' per scontato che la lista sia lunga un giorno.
     */
    val allHours: List<HourForecast> = emptyList(),
    /** La localita' a cui si riferisce: dati e posto viaggiano insieme. */
    val place: Place = Place.FORLI,
    /** Scarto dall'ora universale della localita', in secondi. */
    val utcOffsetSeconds: Int = 0,
    /** Quando e' stata ricevuta: la schermata delle impostazioni lo dichiara. */
    val fetchedAt: LocalDateTime = LocalDateTime.now(),
) {
    /** Il giorno in cui cade un certo istante, per alba e tramonto. */
    fun dayOf(moment: LocalDateTime): DayForecast? =
        days.firstOrNull { it.date == moment.toLocalDate() } ?: days.firstOrNull()

    /**
     * Le ore di una data precisa, per il grafico del dettaglio del giorno.
     *
     * Torna vuota se quel giorno non c'e': l'API ne da' sette, e il grafico
     * deve saper dire "non lo so" invece di mostrare le ore di un altro.
     */
    fun hoursOf(date: LocalDate): List<HourForecast> =
        allHours.filter { it.time.toLocalDate() == date }

    /**
     * L'ora corrispondente su un altro giorno.
     *
     * `hours` copre solo le prime ventiquattro ore, cioe' oggi, e l'indice
     * dell'ora scelta conta su quella lista. Il foglio del dettaglio pero'
     * puo' avere selezionato mercoledi': prendendo [hours] alla stessa
     * posizione si otteneva l'ora di **oggi** sotto un'intestazione che
     * annunciava mercoledi', e i due numeri non avevano niente a che vedere.
     *
     * Qui si cerca la stessa ora del giorno richiesto. Se quel giorno non c'e'
     * si torna nulli, che e' l'unica risposta onesta: meglio un trattino di un
     * valore preso da un altro giorno.
     */
    fun hourOn(date: LocalDate, hourOfDay: Int): HourForecast? =
        allHours.firstOrNull { it.time.toLocalDate() == date && it.time.hour == hourOfDay }

    /**
     * Che ore sono nella localita' mostrata.
     *
     * Non l'orologio del telefono: gli orari della previsione sono espressi nel
     * fuso del posto, e da quando il posto lo sceglie l'utente i due possono
     * essere mezza giornata distanti. Confrontarli darebbe un'ora corrente
     * sbagliata di preciso quel tanto.
     */
    fun nowThere(): LocalDateTime =
        java.time.Instant.now()
            .atOffset(java.time.ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
            .toLocalDateTime()
}
