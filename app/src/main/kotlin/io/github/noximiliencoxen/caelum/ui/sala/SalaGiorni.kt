package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.lingua.tr
import androidx.compose.runtime.Immutable
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.Wmo
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
    /** tr("oggi", "today"), "lun": la colonna stretta della striscia. */
    val breve: String,
    /** tr("oggi", "today"), tr("martedì", "Tuesday"): la scheda del giorno scelto, dove c'e' spazio. */
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
            breve = if (i == 0) tr("oggi", "today") else giorno.label.lowercase(),
            esteso = if (i == 0) tr("oggi", "today") else giorno.date.dayOfWeek.italiano(),
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
private fun DayForecast.nuvolositaStimata(): Int =
    (Wmo.cloudiness(weatherCode) * 100f).toInt()

private fun DayOfWeek.italiano(): String = when (this) {
    DayOfWeek.MONDAY -> tr("lunedì", "Monday")
    DayOfWeek.TUESDAY -> tr("martedì", "Tuesday")
    DayOfWeek.WEDNESDAY -> tr("mercoledì", "Wednesday")
    DayOfWeek.THURSDAY -> tr("giovedì", "Thursday")
    DayOfWeek.FRIDAY -> tr("venerdì", "Friday")
    DayOfWeek.SATURDAY -> tr("sabato", "Saturday")
    DayOfWeek.SUNDAY -> tr("domenica", "Sunday")
}

private fun Int.meseBreve(): String = when (this) {
    1 -> tr("gen", "Jan")
    2 -> tr("feb", "Feb")
    3 -> tr("mar", "Mar")
    4 -> tr("apr", "Apr")
    5 -> tr("mag", "May")
    6 -> tr("giu", "Jun")
    7 -> tr("lug", "Jul")
    8 -> tr("ago", "Aug")
    9 -> tr("set", "Sep")
    10 -> tr("ott", "Oct")
    11 -> tr("nov", "Nov")
    else -> tr("dic", "Dec")
}
