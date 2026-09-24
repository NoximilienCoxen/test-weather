package io.github.noximiliencoxen.caelum.data

import io.github.noximiliencoxen.caelum.lingua.tr
import java.time.LocalDate
import java.time.LocalTime

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
fun derivedAlerts(forecast: Forecast): List<WeatherAlert> {
    val alerts = mutableListOf<WeatherAlert>()
    // Solo oggi e domani: piu' in la' la previsione e' troppo incerta perche'
    // valga la pena di allarmare qualcuno.
    forecast.days.take(2).forEachIndexed { index, day ->
        val quando = if (index == 0) tr("oggi", "today") else tr("domani", "tomorrow")
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
            // arrivano cosi'. 20 m/s sono 72 km/h, 28 m/s poco piu' di 100.
            val level = when {
                gust >= 28.0 -> AlertLevel.ARANCIONE
                gust >= 20.0 -> AlertLevel.GIALLA
                else -> null
            }
            if (level != null) {
                add(
                    AlertKind.VENTO, level,
                    tr("Vento forte $quando", "Strong wind $quando"),
                    tr("Raffiche fino a ${kmh(gust)} km/h.", "Gusts up to ${kmh(gust)} km/h."),
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
                    tr("Pioggia abbondante $quando", "Heavy rain $quando"),
                    tr("Attesi circa ${mm.toInt()} mm", "About ${mm.toInt()} mm expected") +
                        (day.precipHours?.let { tr(" in ${it.toInt()} ore", " over ${it.toInt()} hours") } ?: "") + ".",
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
                    tr("Neve $quando", "Snow $quando"),
                    tr("Attesi circa ${cm.toInt()} cm.", "About ${cm.toInt()} cm expected."),
                )
            }
        }

        // Il caldo si misura sulla percepita e non sulla temperatura: sono i
        // gradi piu' l'umidita' a mandare la gente al pronto soccorso, e con
        // l'ottanta per cento di umidita' trentaquattro gradi ne pesano quaranta.
        (day.apparentMax ?: day.tempMax)?.let { heat ->
            val level = when {
                heat >= 40.0 -> AlertLevel.ARANCIONE
                heat >= 35.0 -> AlertLevel.GIALLA
                else -> null
            }
            if (level != null) {
                add(
                    AlertKind.CALDO, level,
                    tr("Caldo intenso $quando", "Intense heat $quando"),
                    tr("Temperatura percepita fino a ${heat.toInt()}\u00B0C.", "Feels like up to ${heat.toInt()}\u00B0C."),
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
                    tr("Freddo intenso $quando", "Intense cold $quando"),
                    tr("Temperatura percepita fino a ${cold.toInt()}\u00B0C.", "Feels like down to ${cold.toInt()}\u00B0C."),
                )
            }
        }

        // I raggi UV, che sono l'unico fenomeno per cui esisteva gia' un
        // interruttore nelle impostazioni **senza niente dietro**. La soglia e'
        // quella scritta sull'interruttore, sei, e non l'ho scelta io: e' il
        // punto in cui la scala mondiale passa da "moderato" ad "alto".
        day.uvMax?.let { uv ->
            val level = when {
                uv >= 8.0 -> AlertLevel.ARANCIONE
                uv >= 6.0 -> AlertLevel.GIALLA
                else -> null
            }
            if (level != null) {
                add(
                    AlertKind.UV, level,
                    tr("Raggi UV alti $quando", "High UV $quando"),
                    tr("Indice UV fino a ${uv.toInt()} nelle ore centrali.", "UV index up to ${uv.toInt()} around midday."),
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
                tr("Temporali $quando", "Thunderstorms $quando"),
                if (stormHours > 0) tr("Previsti temporali per circa $stormHours ore.", "Thunderstorms expected for about $stormHours hours.") else tr("Previsti temporali.", "Thunderstorms expected."),
            )
        }
    }
    return alerts.sortedByDescending { it.level.weight }
}

/** Chi lo dice, quando non lo dice un ente. */
private val SOURCE: String get() = tr("Calcolata dai dati Open-Meteo", "Calculated from Open-Meteo data")

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
