package com.caelumweather.ui.sala

import com.caelumweather.lingua.inInglese
import com.caelumweather.lingua.tr
import androidx.compose.runtime.Immutable
import com.caelumweather.data.DayForecast
import com.caelumweather.data.Forecast
import com.caelumweather.data.Wmo
import com.caelumweather.ui.UiState
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Un giorno della settimana, gia' pronto da mettere in scena.
 *
 * Sta in un posto solo perche' **lo leggono due sale**: la striscia in fondo a
 * Sala I e la settimana intera di Sala II mostrano gli stessi sette giorni, e
 * due letture separate degli stessi campi divergono al primo che ne aggiusta
 * una - che e' precisamente il difetto per cui le due sale si somigliavano
 * troppo senza dire le stesse cose.
 */
@Immutable
data class GiornoSettimana(
    val indice: Int,
    /** "oggi", "lun": la colonna stretta della striscia. */
    val breve: String,
    /** "oggi", "martedì": la scheda del giorno scelto, dove c'e' spazio. */
    val esteso: String,
    /** "15 set". */
    val data: String,
    /** Solo il numero: 15. Serve all'etichetta stretta sopra la barra delle
     *  ore, dove "15 set" non ci starebbe accanto all'ora. */
    val giornoDelMese: Int,
    val max: Double?,
    val min: Double?,
    val mm: Double?,
    val vento: Double?,
    val uv: Double?,
    val alba: LocalDateTime?,
    val tramonto: LocalDateTime?,
    /** Secondi di sole effettivo: non le ore di luce, vedi `DayForecast`. */
    val sole: Double?,
    val glifo: GlifoMeteo,
    /** Come si chiama questo tempo, per esteso. */
    val tipo: String,
)

/**
 * I sette giorni della previsione.
 *
 * La nuvolosita' di un giorno non e' fra i dati giornalieri di Open-Meteo,
 * quindi il glifo la ricava dal **codice** e basta: e' l'unico posto in cui
 * questa applicazione sceglie una figuretta senza il dato vero sotto, ed e'
 * dichiarato qui invece che nascosto in un ramo.
 */
fun settimanaDi(forecast: Forecast?): List<GiornoSettimana> {
    val giorni = forecast?.days.orEmpty().take(7)
    return giorni.mapIndexed { i, giorno ->
        GiornoSettimana(
            indice = i,
            // In inglese i giorni hanno la maiuscola: "Sun", non "sun".
            breve = if (i == 0) {
                tr("oggi", "Today", "Heute", "Aujourd'hui", "Hoy")
            } else if (inInglese()) {
                giorno.label.lowercase().replaceFirstChar { it.uppercase() }
            } else {
                giorno.label.lowercase()
            },
            esteso = if (i == 0) tr("oggi", "Today", "Heute", "Aujourd'hui", "Hoy") else giorno.date.dayOfWeek.italiano(),
            data = "${giorno.date.dayOfMonth} ${giorno.date.monthValue.meseBreve()}",
            giornoDelMese = giorno.date.dayOfMonth,
            max = giorno.tempMax,
            min = giorno.tempMin,
            mm = giorno.precipitationSum,
            vento = giorno.windMax,
            uv = giorno.uvMax,
            alba = giorno.sunrise,
            tramonto = giorno.sunset,
            sole = giorno.sunshineSeconds,
            glifo = glifoDi(giorno.weatherCode, giorno.nuvolositaStimata()),
            tipo = Wmo.condition(giorno.weatherCode),
        )
    }
}

/**
 * Quanto e' coperto un giorno, **stimato dal codice**.
 *
 * `Wmo.cloudiness` esiste gia' e fa esattamente questo per il cielo: qui si
 * riusa invece di inventare una seconda scala, cosi' la figuretta di un giorno
 * e il fondo di quell'ora non possono raccontare due nuvolosita' diverse.
 */
internal fun DayForecast.nuvolositaStimata(): Int =
    (Wmo.cloudiness(weatherCode) * 100f).toInt()

/**
 * "di oggi", "di domani", "di venerdì": il giorno scritto accanto a un'ora,
 * contato dall'oggi **del posto** e non del telefono.
 *
 * Serve alle sale che, lontano da adesso, devono dire di quale momento stanno
 * parlando invece di "fra poco" o "le prossime ore".
 */
internal fun UiState.diGiorno(data: java.time.LocalDate): String {
    val oggi = forecast?.nowThere()?.toLocalDate() ?: java.time.LocalDate.now()
    return when (data) {
        // In inglese senza la preposizione: "06:00 today", "06:00 on Friday".
        oggi -> tr("di oggi", "today", "heute", "aujourd'hui", "de hoy")
        oggi.plusDays(1) -> tr("di domani", "tomorrow", "morgen", "demain", "de mañana")
        else -> tr("di ${data.dayOfWeek.italiano()}", "on ${data.dayOfWeek.italiano()}", "am ${data.dayOfWeek.italiano()}", "${data.dayOfWeek.italiano()}", "del ${data.dayOfWeek.italiano()}")
    }
}

internal fun DayOfWeek.italiano(): String = when (this) {
    DayOfWeek.MONDAY -> tr("lunedì", "Monday", "Montag", "lundi", "lunes")
    DayOfWeek.TUESDAY -> tr("martedì", "Tuesday", "Dienstag", "mardi", "martes")
    DayOfWeek.WEDNESDAY -> tr("mercoledì", "Wednesday", "Mittwoch", "mercredi", "miércoles")
    DayOfWeek.THURSDAY -> tr("giovedì", "Thursday", "Donnerstag", "jeudi", "jueves")
    DayOfWeek.FRIDAY -> tr("venerdì", "Friday", "Freitag", "vendredi", "viernes")
    DayOfWeek.SATURDAY -> tr("sabato", "Saturday", "Samstag", "samedi", "sábado")
    DayOfWeek.SUNDAY -> tr("domenica", "Sunday", "Sonntag", "dimanche", "domingo")
}

private fun Int.meseBreve(): String = when (this) {
    1 -> tr("gen", "Jan", "Jan.", "janv.", "ene")
    2 -> tr("feb", "Feb", "Feb.", "févr.", "feb")
    3 -> tr("mar", "Mar", "März", "mars", "mar")
    4 -> tr("apr", "Apr", "Apr.", "avr.", "abr")
    5 -> tr("mag", "May", "Mai", "mai", "may")
    6 -> tr("giu", "Jun", "Juni", "juin", "jun")
    7 -> tr("lug", "Jul", "Juli", "juil.", "jul")
    8 -> tr("ago", "Aug", "Aug.", "août", "ago")
    9 -> tr("set", "Sep", "Sep.", "sept.", "sep")
    10 -> tr("ott", "Oct", "Okt.", "oct.", "oct")
    11 -> tr("nov", "Nov", "Nov.", "nov.", "nov")
    else -> tr("dic", "Dec", "Dez.", "déc.", "dic")
}
