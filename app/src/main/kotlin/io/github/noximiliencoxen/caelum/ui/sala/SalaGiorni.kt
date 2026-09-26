package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.runtime.Immutable
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.UiState
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
            breve = if (i == 0) "oggi" else giorno.label.lowercase(),
            esteso = if (i == 0) "oggi" else giorno.date.dayOfWeek.italiano(),
            data = "${giorno.date.dayOfMonth} ${giorno.date.monthValue.meseBreve()}",
            giornoDelMese = giorno.date.dayOfMonth,
            max = giorno.tempMax,
            min = giorno.tempMin,
            mm = giorno.precipitationSum,
            vento = giorno.windMax,
            uv = giorno.uvMax,
            alba = giorno.sunrise,
            tramonto = giorno.sunset,
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
        oggi -> "di oggi"
        oggi.plusDays(1) -> "di domani"
        else -> "di ${data.dayOfWeek.italiano()}"
    }
}

internal fun DayOfWeek.italiano(): String = when (this) {
    DayOfWeek.MONDAY -> "lunedì"
    DayOfWeek.TUESDAY -> "martedì"
    DayOfWeek.WEDNESDAY -> "mercoledì"
    DayOfWeek.THURSDAY -> "giovedì"
    DayOfWeek.FRIDAY -> "venerdì"
    DayOfWeek.SATURDAY -> "sabato"
    DayOfWeek.SUNDAY -> "domenica"
}

private fun Int.meseBreve(): String = when (this) {
    1 -> "gen"
    2 -> "feb"
    3 -> "mar"
    4 -> "apr"
    5 -> "mag"
    6 -> "giu"
    7 -> "lug"
    8 -> "ago"
    9 -> "set"
    10 -> "ott"
    11 -> "nov"
    else -> "dic"
}
