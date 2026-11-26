package com.caelumweather.data

import com.caelumweather.lingua.tr
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Le allerte che l'app si calcola da sola, dai numeri che ha gia' in mano.
 *
 * **Non sostituiscono i bollettini ufficiali e non ci provano.** Servono dove
 * quelli non arrivano: MeteoAlarm copre l'Europa, l'app no - c'e' una
 * scorciatoia per Aoraki in Nuova Zelanda dentro `Place.SUGGESTIONS` - e un
 * feed puo' non rispondere. In quei due casi l'alternativa a queste non e' un
 * bollettino migliore, e' il silenzio davanti a novanta chilometri orari di
 * raffica.
 *
 * Nessuna rete: ogni grandezza usata qui viaggia gia' dentro la previsione che
 * la schermata ha scaricato comunque. Aggiungere una chiamata per dire quello
 * che i dati dicono gia' sarebbe un secondo giro per la stessa risposta.
 *
 * Le soglie sono quelle di uso comune in Italia per i primi due gradini
 * (gialla e arancione). **Il rosso non si emette qui**: il rosso e' una
 * dichiarazione di un ente, con dietro una valutazione del rischio sul
 * territorio - quanto regge un argine, dove sta la gente - che un confronto fra
 * un numero e una costante non puo' fare. Il massimo che questa funzione si
 * permette e' l'arancione.
 */
fun derivedAlerts(forecast: Forecast, soglie: SoglieAvvisi = SoglieAvvisi()): List<WeatherAlert> {
    val alerts = mutableListOf<WeatherAlert>()
    // Solo oggi e domani: piu' in la' la previsione e' troppo incerta perche'
    // valga la pena di allarmare qualcuno.
    forecast.days.take(2).forEachIndexed { index, day ->
        val quando = if (index == 0) tr("oggi", "today", "heute", "aujourd'hui", "hoy") else tr("domani", "tomorrow", "morgen", "demain", "mañana")
        val giorno = day.date

        fun add(kind: AlertKind, level: AlertLevel, headline: String, detail: String) {
            alerts += WeatherAlert(
                id = "derivata-${kind.name}-$giorno",
                level = level,
                kind = kind,
                headline = headline,
                description = detail,
                onset = giorno.atStartOfDay(),
                expires = giorno.atTime(LocalTime.MAX),
                areaDesc = forecast.place.name,
                source = SOURCE,
                official = false,
            )
        }

        day.gustMax?.let { gust ->
            // In metri al secondo: l'app chiede `wind_speed_unit=ms` e i numeri
            // arrivano cosi'. La gialla parte dalla soglia scelta per il vento
            // (la stessa della notifica "Raffiche", CONTESTO §84), l'arancione
            // da 28 m/s - poco piu' di 100 km/h - o dalla soglia se e' piu' alta.
            val kmh = (gust * 3.6).roundToInt()
            val level = when {
                kmh >= maxOf(ARANCIONE_VENTO_KMH, soglie.ventoKmh) -> AlertLevel.ARANCIONE
                kmh >= soglie.ventoKmh -> AlertLevel.GIALLA
                else -> null
            }
            if (level != null) {
                add(
                    AlertKind.VENTO, level,
                    tr("Vento forte $quando", "Strong wind $quando", "Starker Wind $quando", "Vent fort $quando", "Viento fuerte $quando"),
                    tr("Raffiche fino a ${kmh(gust)} km/h.", "Gusts up to ${kmh(gust)} km/h.", "Böen bis ${kmh(gust)} km/h.", "Rafales jusqu'à ${kmh(gust)} km/h.", "Rachas de hasta ${kmh(gust)} km/h."),
                )
            }
        }

        day.precipitationSum?.let { mm ->
            val level = when {
                mm >= 70.0 -> AlertLevel.ARANCIONE
                mm >= 40.0 -> AlertLevel.GIALLA
                else -> null
            }
            if (level != null) {
                add(
                    AlertKind.PIOGGIA, level,
                    tr("Pioggia abbondante $quando", "Heavy rain $quando", "Starker Regen $quando", "Fortes pluies $quando", "Lluvia abundante $quando"),
                    tr("Attesi circa ${mm.toInt()} mm", "About ${mm.toInt()} mm expected", "Etwa ${mm.toInt()} mm erwartet", "Environ ${mm.toInt()} mm attendus", "Se esperan unos ${mm.toInt()} mm") +
                        (day.precipHours?.let { tr(" in ${it.toInt()} ore", " over ${it.toInt()} hours", " in ${it.toInt()} Stunden", " en ${it.toInt()} heures", " en ${it.toInt()} horas") } ?: "") + ".",
                )
            }
        }

        day.snowfallSum?.let { cm ->
            val level = when {
                cm >= 15.0 -> AlertLevel.ARANCIONE
                cm >= 5.0 -> AlertLevel.GIALLA
                else -> null
            }
            if (level != null) {
                add(
                    AlertKind.NEVE_GHIACCIO, level,
                    tr("Neve $quando", "Snow $quando", "Schnee $quando", "Neige $quando", "Nieve $quando"),
                    tr("Attesi circa ${cm.toInt()} cm.", "About ${cm.toInt()} cm expected.", "Etwa ${cm.toInt()} cm erwartet.", "Environ ${cm.toInt()} cm attendus.", "Se esperan unos ${cm.toInt()} cm."),
                )
            }
        }

        // Il caldo si misura sulla percepita e non sulla temperatura: sono i
        // gradi piu' l'umidita' a mandare la gente al pronto soccorso, e con
        // l'ottanta per cento di umidita' trentaquattro gradi ne pesano quaranta.
        // La gialla dalla soglia scelta (la stessa della notifica "Caldo", che
        // ora guarda anch'essa la percepita), l'arancione quattro gradi sopra.
        (day.apparentMax ?: day.tempMax)?.let { heat ->
            val gradi = heat.roundToInt()
            val level = when {
                gradi >= soglie.caldoGradi + ARANCIONE_CALDO_IN_PIU -> AlertLevel.ARANCIONE
                gradi >= soglie.caldoGradi -> AlertLevel.GIALLA
                else -> null
            }
            if (level != null) {
                add(
                    AlertKind.CALDO, level,
                    tr("Caldo intenso $quando", "Intense heat $quando", "Große Hitze $quando", "Forte chaleur $quando", "Calor intenso $quando"),
                    tr("Temperatura percepita fino a ${heat.toInt()}\u00B0C.", "Feels like up to ${heat.toInt()}\u00B0C.", "Gefühlte Temperatur bis ${heat.toInt()}°C.", "Température ressentie jusqu'à ${heat.toInt()}°C.", "Sensación térmica de hasta ${heat.toInt()}°C."),
                )
            }
        }

        (day.apparentMin ?: day.tempMin)?.let { cold ->
            val level = when {
                cold <= -10.0 -> AlertLevel.ARANCIONE
                cold <= -5.0 -> AlertLevel.GIALLA
                else -> null
            }
            if (level != null) {
                add(
                    AlertKind.FREDDO, level,
                    tr("Freddo intenso $quando", "Intense cold $quando", "Strenge Kälte $quando", "Froid intense $quando", "Frío intenso $quando"),
                    tr("Temperatura percepita fino a ${cold.toInt()}\u00B0C.", "Feels like down to ${cold.toInt()}\u00B0C.", "Gefühlte Temperatur bis ${cold.toInt()}°C.", "Température ressentie jusqu'à ${cold.toInt()}°C.", "Sensación térmica de hasta ${cold.toInt()}°C."),
                )
            }
        }

        // I raggi UV. La gialla dalla soglia scelta (la stessa della notifica
        // "UV alto"), l'arancione dal gradino successivo della scala dell'OMS:
        // 6 "alto" -> 8 "molto alto" -> 11 "estremo". **Con la soglia a 11 un
        // gradino dopo non c'e'**: l'estremo e' gia' la cima della scala, e
        // l'avviso nasce direttamente arancione, senza gialla prima.
        day.uvMax?.let { uv ->
            val indice = uv.roundToInt()
            val level = when {
                indice >= arancioneUv(soglie.uvIndice) -> AlertLevel.ARANCIONE
                indice >= soglie.uvIndice -> AlertLevel.GIALLA
                else -> null
            }
            if (level != null) {
                add(
                    AlertKind.UV, level,
                    tr("Raggi UV alti $quando", "High UV $quando", "Hohe UV-Strahlung $quando", "UV élevés $quando", "Rayos UV altos $quando"),
                    tr("Indice UV fino a ${uv.toInt()} nelle ore centrali.", "UV index up to ${uv.toInt()} around midday.", "UV-Index bis ${uv.toInt()} in den Mittagsstunden.", "Indice UV jusqu'à ${uv.toInt()} en milieu de journée.", "Índice UV de hasta ${uv.toInt()} en las horas centrales."),
                )
            }
        }

        // I temporali non hanno una soglia numerica nel blocco giornaliero: il
        // codice meteo del giorno e' l'unica cosa che li dichiara, e conta
        // quante ore ne sono toccate per non gridare al temporale per una
        // schiarita di dieci minuti.
        val stormHours = forecast.hoursOf(giorno)
            .count { Wmo.family(it.weatherCode) == Wmo.Family.TEMPORALE }
        if (stormHours >= 2 || Wmo.family(day.weatherCode) == Wmo.Family.TEMPORALE) {
            add(
                AlertKind.TEMPORALI,
                if (stormHours >= 5) AlertLevel.ARANCIONE else AlertLevel.GIALLA,
                tr("Temporali $quando", "Thunderstorms $quando", "Gewitter $quando", "Orages $quando", "Tormentas $quando"),
                if (stormHours > 0) tr("Previsti temporali per circa $stormHours ore.", "Thunderstorms expected for about $stormHours hours.", "Gewitter für etwa $stormHours Stunden erwartet.", "Orages prévus pendant environ $stormHours heures.", "Se esperan tormentas durante unas $stormHours horas.") else tr("Previsti temporali.", "Thunderstorms expected.", "Gewitter erwartet.", "Orages prévus.", "Se esperan tormentas."),
            )
        }
    }
    return alerts.sortedByDescending { it.level.weight }
}

/** Chi lo dice, quando non lo dice un ente. */
private val SOURCE: String get() = tr("Calcolata dai dati Open-Meteo", "Calculated from Open-Meteo data", "Aus Open-Meteo-Daten berechnet", "Calculé à partir des données Open-Meteo", "Calculado con datos de Open-Meteo")

/** Metri al secondo in chilometri orari, che e' come si dice il vento a voce. */
private fun kmh(ms: Double): Int = (ms * 3.6).toInt()

/**
 * Le due fonti messe insieme: **l'ufficiale vince**.
 *
 * Una derivata sopravvive solo se nessun bollettino ufficiale parla gia' di
 * quel fenomeno. Senza questa regola la schermata direbbe due volte la stessa
 * cosa con due voci diverse - "Allerta gialla per vento" della Protezione
 * Civile e "Vento forte oggi" calcolata qui - e la seconda, essendo un
 * doppione peggiore, toglierebbe autorevolezza alla prima.
 *
 * Il confronto e' per **tipo di fenomeno**, non per testo: due avvisi sul vento
 * sono lo stesso avviso anche se scritti in modo diverso.
 *
 * **E per giorno, che prima mancava.** [derivedAlerts] ne emette due per
 * fenomeno, una per oggi e una per domani, con id distinti per data. Scartando
 * per il solo tipo, un bollettino ufficiale valido **oggi** portava via anche
 * l'avviso calcolato per **domani** - e domani non lo copriva nessuno. Il buco
 * si vedeva solo il giorno in cui c'era qualcosa da dire, che e' il giorno
 * sbagliato per accorgersene.
 *
 * Un bollettino ufficiale copre un intervallo, non una data: la derivata cade
 * se quell'intervallo tocca il suo giorno. Le due estremita' possono mancare -
 * il feed non sempre le scrive - e allora quel lato non limita: un avviso senza
 * scadenza vale da qui in avanti, ed e' la lettura prudente. Un ufficiale senza
 * ne' inizio ne' fine copre quel fenomeno e basta, come faceva prima.
 */
fun mergeAlerts(official: List<WeatherAlert>, derived: List<WeatherAlert>): List<WeatherAlert> {
    fun coversDay(alert: WeatherAlert, day: LocalDate): Boolean {
        val startsBefore = alert.onset?.toLocalDate()?.let { it <= day } ?: true
        val endsAfter = alert.expires?.toLocalDate()?.let { it >= day } ?: true
        return startsBefore && endsAfter
    }

    return (
        official + derived.filterNot { d ->
            // Il giorno della derivata sta nel suo `onset`, che
            // `derivedAlerts` mette sempre: e' `giorno.atStartOfDay()`. Senza,
            // non si sa di quale giorno parli e la si tiene.
            val day = d.onset?.toLocalDate() ?: return@filterNot false
            official.any { it.kind == d.kind && coversDay(it, day) }
        }
        ).sortedByDescending { it.level.weight }
}

/**
 * **Le soglie che l'avviso nell'app condivide con la notifica**, per gli
 * argomenti in cui le due misurano la stessa cosa (CONTESTO §84): vento in km/h,
 * indice UV, percepita del caldo in gradi Celsius. Un numero solo per argomento,
 * scelto una volta nelle impostazioni; gli interruttori dicono solo dove compare.
 * La pioggia no: l'app guarda i millimetri del giorno, la notifica quelli di
 * un'ora, e sono due domande diverse.
 */
data class SoglieAvvisi(
    val ventoKmh: Int = com.caelumweather.prefs.SogliePersonali.VENTO_PREDEFINITO,
    val uvIndice: Int = com.caelumweather.prefs.SogliePersonali.UV_PREDEFINITO,
    val caldoGradi: Int = com.caelumweather.prefs.SogliePersonali.CALDO_PREDEFINITO,
) {
    companion object {
        fun da(s: com.caelumweather.prefs.SogliePersonali) = SoglieAvvisi(s.ventoKmh, s.uvIndice, s.caldoGradi)
    }
}

/** Il gradino OMS dopo la soglia, o la soglia stessa se e' gia' l'estremo. */
internal fun arancioneUv(soglia: Int): Int = when {
    soglia < 8 -> 8
    soglia < 11 -> 11
    else -> soglia
}

private const val ARANCIONE_VENTO_KMH = 100
private const val ARANCIONE_CALDO_IN_PIU = 4
