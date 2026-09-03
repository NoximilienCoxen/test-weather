package com.forli.meteo.data

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
        val quando = if (index == 0) "oggi" else "domani"
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
                    "Vento forte $quando",
                    "Raffiche fino a ${kmh(gust)} km/h.",
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
                    "Pioggia abbondante $quando",
                    "Attesi circa ${mm.toInt()} mm" +
                        (day.precipHours?.let { " in ${it.toInt()} ore" } ?: "") + ".",
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
                    "Neve $quando",
                    "Attesi circa ${cm.toInt()} cm.",
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
                    "Caldo intenso $quando",
                    "Temperatura percepita fino a ${heat.toInt()}\u00B0C.",
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
                    "Freddo intenso $quando",
                    "Temperatura percepita fino a ${cold.toInt()}\u00B0C.",
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
                "Temporali $quando",
                if (stormHours > 0) "Previsti temporali per circa $stormHours ore." else "Previsti temporali.",
            )
        }
    }
    return alerts.sortedByDescending { it.level.weight }
}

/** Chi lo dice, quando non lo dice un ente. */
private const val SOURCE = "Calcolata dai dati Open-Meteo"

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
 */
fun mergeAlerts(official: List<WeatherAlert>, derived: List<WeatherAlert>): List<WeatherAlert> {
    val covered = official.map { it.kind }.toSet()
    return (official + derived.filterNot { it.kind in covered })
        .sortedByDescending { it.level.weight }
}
