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
    // Qui stava `normTemp`, la Norma storica. Il suo commento descriveva "la
    // linea tratteggiata nel grafico del dettaglio del giorno" - un grafico che
    // dalla schermata era gia' sparito, mentre il campo e il suo caricamento
    // erano rimasti. Vedi la nota in `WeatherRepository`.
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
    /**
     * Quando e' stata ricevuta: la schermata delle impostazioni lo dichiara.
     *
     * **E adesso e' vero.** Questa riga lo affermava da mesi mentre nessuna
     * schermata leggeva il campo: era rimasta indietro rispetto al vecchio
     * `ui/settings/SettingsScreen.kt`, cancellato col redisegno. Un commento
     * che descrive una cosa che non succede e' peggio di nessun commento,
     * perche' chi lo legge smette di andare a controllare.
     */
    val fetchedAt: LocalDateTime = LocalDateTime.now(),
) {
    /**
     * Le ore raccolte per giornata, una volta sola.
     *
     * [hoursOf] e [hourOn] setacciavano tutte e centosessantotto le ore a ogni
     * chiamata, e le chiama la schermata: **dodici volte per fotogramma**
     * mentre si trascina la barra delle ore. Peggio del conto era il risultato,
     * una lista **nuova ogni volta**, che rendeva inutile il `remember` della
     * barra - la chiave cambiava per riferimento anche quando il contenuto era
     * identico, quindi il confronto la scorreva elemento per elemento solo per
     * concludere di non dover ricalcolare niente.
     *
     * `groupBy` tiene l'ordine di incontro dentro ogni gruppo, quindi le liste
     * sono le stesse di prima nello stesso ordine; e adesso sono **sempre la
     * stessa lista**, che e' cio' che serve a chi la usa come chiave.
     *
     * Sta fuori dal costruttore, quindi fuori da `equals`: due previsioni
     * uguali restano uguali, e una copia non si porta dietro una mappa da
     * confrontare.
     *
     * `PUBLICATION` e non `NONE`: oggi la previsione la leggono tutti dal filo
     * della composizione, ma questo e' il pacchetto dei dati, dove nessuno ha
     * promesso un filo solo - la richiesta si monta fuori, e chi domani
     * spostasse `derivedAlerts` su un altro dispatcher non avrebbe modo di
     * accorgersi di aver rotto niente. Al massimo la mappa si costruisce due
     * volte; quel che non puo' succedere e' che qualcuno ne veda una a meta'.
     */
    private val perGiorno: Map<LocalDate, List<HourForecast>> by
        lazy(LazyThreadSafetyMode.PUBLICATION) { allHours.groupBy { it.time.toLocalDate() } }

    /** Il giorno in cui cade un certo istante, per alba e tramonto. */
    fun dayOf(moment: LocalDateTime): DayForecast? =
        days.firstOrNull { it.date == moment.toLocalDate() } ?: days.firstOrNull()

    /**
     * Le ore di una data precisa, per il grafico del dettaglio del giorno.
     *
     * Torna vuota se quel giorno non c'e': l'API ne da' sette, e il grafico
     * deve saper dire "non lo so" invece di mostrare le ore di un altro.
     */
    fun hoursOf(date: LocalDate): List<HourForecast> = perGiorno[date].orEmpty()

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
        perGiorno[date]?.firstOrNull { it.time.hour == hourOfDay }

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
