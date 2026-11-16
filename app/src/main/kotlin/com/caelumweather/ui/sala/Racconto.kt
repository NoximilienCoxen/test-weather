package com.caelumweather.ui.sala

import com.caelumweather.data.HourForecast
import com.caelumweather.data.Wmo
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr
import com.caelumweather.prefs.SalaWindUnit
import com.caelumweather.prefs.TempUnit
import java.time.LocalDateTime
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** Titolo e paragrafo di Sala I. */
data class Racconto(val titolo: String, val corpo: String)

/**
 * Il titolo e il paragrafo di Sala I, **costruiti dalle ore vere**.
 *
 * Prima erano frasi fisse, una per ogni coppia condizione x momento del
 * giorno: "Rovescio di meta' pomeriggio" usciva alle 13 come alle 10, perche'
 * era il testo di *pioggia + giorno*; "Nuvole di passaggio" sopra un cielo
 * coperto da un orizzonte all'altro; "il fumo sale dritto" a ogni vento debole.
 * Suonavano precise e non lo erano: alla seconda lettura si capiva che erano
 * modelli, ed e' il modo piu' rapido di perdere la fiducia di chi legge.
 *
 * Qui ogni parola viene da un numero della previsione: se piove, fino a che
 * ora e quanti millimetri; se no, se e quando comincia; com'e' il cielo e se
 * cambia; dove va la temperatura nelle prossime ore; le raffiche, solo se
 * contano. Meno poesia, ma vera.
 *
 * Guarda avanti dall'ora mostrata: diciotto ore per la pioggia in arrivo,
 * dodici per cielo, temperatura e raffiche, ventiquattro per sapere quando
 * smette una pioggia lunga, su tutte le ore del modello: una pioggia
 * che comincia a mezzanotte e mezza e' fra novanta minuti, non "domani".
 *
 * Restituisce null quando non c'e' niente da raccontare (nessuna ora da li' in
 * avanti): chi la chiama ripiega sui testi di prima.
 */
fun raccontoDellOra(
    ore: List<HourForecast>,
    da: LocalDateTime,
    unita: TempUnit,
    vento: SalaWindUnit,
): Racconto? {
    val avanti = ore.filter { !it.time.isBefore(da.withMinute(0).withSecond(0).withNano(0)) }.take(24)
    val ora = avanti.firstOrNull() ?: return null
    val dodici = avanti.take(12)
    // La pioggia in arrivo si cerca un po' piu' in la': alle 13 una pioggia
    // delle 2 di notte e' la cosa da sapere prima di uscire stasera.
    val diciotto = avanti.take(18)

    val titolo: String
    val prima: String

    if (bagnata(ora)) {
        // Piove adesso: quello che serve sapere e' quando smette.
        val bagnate = avanti.takeWhile(::bagnata)
        val quanta = bagnate.sumOf { it.precipitation ?: 0.0 }
        val nome = nomePrecipitazione(ora)
        val forza = if (tipo(ora) == Tipo.TEMPORALE) "" else " " + intensita(bagnate.maxOf { it.precipitation ?: 0.0 })
        if (bagnate.size >= avanti.size) {
            titolo = tr("$nome$forza, ancora a lungo", "$nome$forza, for a long while yet", "$nome$forza, noch lange", "$nome$forza, encore longtemps", "$nome$forza, todavía para rato")
            prima = tr("${mm(quanta)} mm nelle prossime ${bagnate.size} ore.", "${mm(quanta)} mm over the next ${bagnate.size} hours.", "${mm(quanta)} mm in den nächsten ${bagnate.size} Stunden.", "${mm(quanta)} mm dans les ${bagnate.size} prochaines heures.", "${mm(quanta)} mm en las próximas ${bagnate.size} horas.")
        } else {
            val fine = bagnate.last().time.plusHours(1)
            val quando = orario(fine, da)
            titolo = tr("$nome$forza fino alle $quando", "$nome$forza until $quando", "$nome$forza bis $quando", "$nome$forza jusqu'à $quando", "$nome$forza hasta las $quando")
            val dopo = avanti.firstOrNull { !it.time.isBefore(fine) }?.let { cielo(it).lowercase(Lingue.locale) }
            prima = if (dopo != null) {
                tr("${mm(quanta)} mm fino ad allora, poi $dopo.", "${mm(quanta)} mm until then, then $dopo.", "${mm(quanta)} mm bis dahin, danach $dopo.", "${mm(quanta)} mm d'ici là, puis $dopo.", "${mm(quanta)} mm hasta entonces, luego $dopo.")
            } else {
                tr("${mm(quanta)} mm fino ad allora.", "${mm(quanta)} mm until then.", "${mm(quanta)} mm bis dahin.", "${mm(quanta)} mm d'ici là.", "${mm(quanta)} mm hasta entonces.")
            }
        }
    } else {
        val adesso = cielo(ora)
        val inizio = diciotto.firstOrNull(::bagnata)
        if (inizio != null) {
            // Asciutto adesso, ma arriva: da quando, quanto dura, quanta.
            val finestra = avanti.dropWhile { it.time.isBefore(inizio.time) }.takeWhile(::bagnata)
            val nome = nomePrecipitazione(inizio).lowercase(Lingue.locale)
            val quando = orario(inizio.time, da)
            titolo = tr("$adesso, $nome dalle $quando", "$adesso, $nome from $quando", "$adesso, $nome ab $quando", "$adesso, $nome dès $quando", "$adesso, $nome desde las $quando")
            val quanta = finestra.sumOf { it.precipitation ?: 0.0 }
            val durata = if (finestra.size <= 1) {
                tr("Un'ora scarsa", "Under an hour", "Knapp eine Stunde", "Une petite heure", "Una hora escasa")
            } else {
                tr("Circa ${finestra.size} ore", "About ${finestra.size} hours", "Etwa ${finestra.size} Stunden", "Environ ${finestra.size} heures", "Unas ${finestra.size} horas")
            }
            prima = tr("$durata, ${mm(quanta)} mm in tutto.", "$durata, ${mm(quanta)} mm in all.", "$durata, insgesamt ${mm(quanta)} mm.", "$durata, ${mm(quanta)} mm au total.", "$durata, ${mm(quanta)} mm en total.")
        } else {
            // Asciutto per dodici ore: il cielo, e se cambia.
            val cambio = cambioDelCielo(dodici)
            titolo = when {
                cambio != null && cambio.second -> tr(
                    "$adesso, si apre ${versoLe(cambio.first, da)}",
                    "$adesso, clearing ${versoLe(cambio.first, da)}",
                    "$adesso, klart ${versoLe(cambio.first, da)} auf",
                    "$adesso, éclaircies ${versoLe(cambio.first, da)}",
                    "$adesso, abre ${versoLe(cambio.first, da)}",
                )
                cambio != null -> tr(
                    "$adesso, si copre ${versoLe(cambio.first, da)}",
                    "$adesso, clouding over ${versoLe(cambio.first, da)}",
                    "$adesso, zieht ${versoLe(cambio.first, da)} zu",
                    "$adesso, se couvre ${versoLe(cambio.first, da)}",
                    "$adesso, se cubre ${versoLe(cambio.first, da)}",
                )
                else -> tr("$adesso e asciutto", "$adesso and dry", "$adesso und trocken", "$adesso et sec", "$adesso y seco")
            }
            prima = tr("Nessuna pioggia nelle prossime 18 ore.", "No rain in the next 18 hours.", "Kein Regen in den nächsten 18 Stunden.", "Pas de pluie dans les 18 prochaines heures.", "Sin lluvia en las próximas 18 horas.")
        }
    }

    val corpo = listOfNotNull(prima, temperatura(ora, dodici, da, unita), raffiche(dodici, da, vento))
        .joinToString(" ")
    return Racconto(titolo, corpo)
}

private enum class Tipo { PIOGGIA, NEVE, TEMPORALE }

private const val SOGLIA_MM = 0.1

/** Un decimo di millimetro, come la sala della pioggia: sotto non si sente. */
private fun bagnata(h: HourForecast): Boolean = (h.precipitation ?: 0.0) >= SOGLIA_MM

private fun tipo(h: HourForecast): Tipo = when {
    Wmo.family(h.weatherCode) == Wmo.Family.TEMPORALE -> Tipo.TEMPORALE
    Wmo.family(h.weatherCode) == Wmo.Family.NEVE || (h.snowfall ?: 0.0) > 0.0 -> Tipo.NEVE
    else -> Tipo.PIOGGIA
}

private fun nomePrecipitazione(h: HourForecast): String = when (tipo(h)) {
    Tipo.TEMPORALE -> tr("Temporali", "Thunderstorms", "Gewitter", "Orages", "Tormentas")
    Tipo.NEVE -> tr("Neve", "Snow", "Schnee", "Neige", "Nieve")
    Tipo.PIOGGIA -> tr("Pioggia", "Rain", "Regen", "Pluie", "Lluvia")
}

/** Le stesse soglie della sala della pioggia, al femminile e al maschile. */
private fun intensita(mmOra: Double): String = when {
    mmOra < 0.5 -> tr("debole", "light", "schwach", "faible", "débil")
    mmOra < 4.0 -> tr("moderata", "moderate", "mäßig", "modérée", "moderada")
    else -> tr("forte", "heavy", "stark", "forte", "fuerte")
}

/**
 * Il cielo di un'ora in una parola: dalla nuvolosita' visibile, che e' cio'
 * che la scena disegna, e non dal codice, che dice il peggio dell'ora.
 */
internal fun cielo(h: HourForecast): String {
    val codice = h.weatherCode
    if (codice == 45 || codice == 48) return tr("Nebbia", "Fog", "Nebel", "Brouillard", "Niebla")
    val nuvole = h.cloudCover ?: when (Wmo.family(codice)) {
        Wmo.Family.ASCIUTTO -> if (codice == 1) 30 else 10
        Wmo.Family.NUVOLOSO -> if (codice == 2) 60 else 90
        else -> 90
    }
    return when {
        nuvole < 20 -> tr("Sereno", "Clear", "Klar", "Dégagé", "Despejado")
        nuvole < 50 -> tr("Poco nuvoloso", "Partly cloudy", "Leicht bewölkt", "Peu nuageux", "Poco nuboso")
        nuvole < 85 -> tr("Nuvoloso", "Cloudy", "Bewölkt", "Nuageux", "Nuboso")
        else -> tr("Coperto", "Overcast", "Bedeckt", "Couvert", "Cubierto")
    }
}

/**
 * Se il cielo cambia davvero nelle prossime ore: da coperto ad aperto o il
 * contrario, tenuto per almeno due ore (un'ora sola e' rumore del modello).
 * La coppia e' l'ora del cambio e `true` se si apre.
 */
private fun cambioDelCielo(ore: List<HourForecast>): Pair<LocalDateTime, Boolean>? {
    val inizio = ore.firstOrNull()?.cloudCover ?: return null
    val chiuso = inizio >= 85
    val aperto = inizio < 40
    if (!chiuso && !aperto) return null
    for (i in 1 until ore.size - 1) {
        val a = ore[i].cloudCover ?: continue
        val b = ore[i + 1].cloudCover ?: continue
        if (chiuso && a < 50 && b < 50) return ore[i].time to true
        if (aperto && a >= 85 && b >= 85) return ore[i].time to false
    }
    return null
}

/**
 * Dove va la temperatura: la massima che deve ancora arrivare oggi, o la
 * minima delle prossime ore, se si scostano di almeno due gradi; se no, ferma.
 */
private fun temperatura(ora: HourForecast, ore: List<HourForecast>, da: LocalDateTime, unita: TempUnit): String? {
    val t0 = ora.temperature ?: return null
    val stessoGiorno = ore.filter { it.time.toLocalDate() == da.toLocalDate() }
    val massima = stessoGiorno.filter { it.temperature != null }.maxByOrNull { it.temperature!! }
    val minima = ore.filter { it.temperature != null }.minByOrNull { it.temperature!! }
    fun g(c: Double) = "${unita.from(c).roundToInt()}°"
    return when {
        massima != null && massima.temperature!! - t0 >= 2.0 && massima.time.isAfter(ora.time) -> tr(
            "Massima di ${g(massima.temperature!!)} ${versoLe(massima.time, da)}.",
            "High of ${g(massima.temperature!!)} ${versoLe(massima.time, da)}.",
            "Höchstwert ${g(massima.temperature!!)} ${versoLe(massima.time, da)}.",
            "Maximale de ${g(massima.temperature!!)} ${versoLe(massima.time, da)}.",
            "Máxima de ${g(massima.temperature!!)} ${versoLe(massima.time, da)}.",
        )
        minima != null && t0 - minima.temperature!! >= 2.0 -> tr(
            "Scende a ${g(minima.temperature!!)} ${versoLe(minima.time, da)}.",
            "Down to ${g(minima.temperature!!)} ${versoLe(minima.time, da)}.",
            "Fällt auf ${g(minima.temperature!!)} ${versoLe(minima.time, da)}.",
            "Descend à ${g(minima.temperature!!)} ${versoLe(minima.time, da)}.",
            "Baja a ${g(minima.temperature!!)} ${versoLe(minima.time, da)}.",
        )
        else -> tr(
            "Temperatura ferma intorno a ${g(t0)}.",
            "Temperature steady around ${g(t0)}.",
            "Temperatur stabil um ${g(t0)}.",
            "Température stable autour de ${g(t0)}.",
            "Temperatura estable en torno a ${g(t0)}.",
        )
    }
}

/** Le raffiche, solo quando contano: da cinquanta chilometri orari in su. */
private fun raffiche(ore: List<HourForecast>, da: LocalDateTime, vento: SalaWindUnit): String? {
    val picco = ore.filter { it.windGusts != null }.maxByOrNull { it.windGusts!! } ?: return null
    // Le grandezze del vento arrivano in metri al secondo: 50 km/h = 13,9 m/s.
    if (picco.windGusts!! < 13.9) return null
    val n = vento.from(picco.windGusts!!).roundToInt()
    val quando = versoLe(picco.time, da)
    return tr(
        "Raffiche fino a $n ${vento.label} $quando.",
        "Gusts up to $n ${vento.label} $quando.",
        "Böen bis $n ${vento.label} $quando.",
        "Rafales jusqu'à $n ${vento.label} $quando.",
        "Rachas de hasta $n ${vento.label} $quando.",
    )
}

/**
 * Un'ora come si dice: "16:00", "02:00 di domani", "09:00 di giovedì". Il
 * giorno solo quando non e' quello dell'ora mostrata.
 */
private fun orario(t: LocalDateTime, da: LocalDateTime): String {
    val giorni = java.time.temporal.ChronoUnit.DAYS.between(da.toLocalDate(), t.toLocalDate())
    val ore = "%02d:00".format(t.hour)
    return when (giorni) {
        0L -> ore
        1L -> tr("$ore di domani", "$ore tomorrow", "$ore morgen", "$ore demain", "$ore de mañana")
        else -> {
            val giorno = t.dayOfWeek.getDisplayName(TextStyle.FULL, Lingue.locale)
            tr("$ore di $giorno", "$ore on $giorno", "$ore am $giorno", "$ore $giorno", "$ore del $giorno")
        }
    }
}

/**
 * "verso le 15:00", e "verso mezzanotte" invece di "verso le 00:00 di domani":
 * quello e' un orario dei treni, non come si dice. La preposizione sta qui
 * perche' con mezzanotte cambia ("verso le" diventa "verso").
 */
private fun versoLe(t: LocalDateTime, da: LocalDateTime): String {
    val mezzanotte = t.hour == 0 && java.time.temporal.ChronoUnit.DAYS.between(da.toLocalDate(), t.toLocalDate()) == 1L
    if (mezzanotte) return tr("verso mezzanotte", "around midnight", "gegen Mitternacht", "vers minuit", "hacia medianoche")
    val o = orario(t, da)
    return tr("verso le $o", "around $o", "gegen $o", "vers $o", "hacia las $o")
}

private fun mm(v: Double): String = String.format(Lingue.locale, "%.1f", v)
