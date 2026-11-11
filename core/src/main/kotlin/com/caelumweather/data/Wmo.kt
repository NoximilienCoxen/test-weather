package com.caelumweather.data

import com.caelumweather.lingua.Lingua
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr

// **`PrecipKind` non c'e' piu'.** Era la tipologia di precipitazione mostrata
// "nella tabella della pagina Precip", e quella pagina e' stata cancellata col
// feed: l'enum e la funzione che lo produceva - `precipKind` - sono rimaste a
// tradurre codici per nessuno. Chi oggi vuole sapere cosa cade chiede
// `family`, che e' la stessa domanda fatta dove stanno i codici.

/** Traduzione dei codici WMO usati da Open-Meteo. */
object Wmo {

    fun condition(code: Int?): String = when (code) {
        null -> "--"
        0 -> tr("SERENO", "CLEAR", "KLAR", "DÉGAGÉ", "DESPEJADO")
        1 -> tr("POCO NUVOLOSO", "MOSTLY CLEAR", "MEIST KLAR", "PEU NUAGEUX", "POCO NUBOSO")
        2 -> tr("PARZ. NUVOLOSO", "PARTLY CLOUDY", "TEILS BEWÖLKT", "PARTIELLEMENT NUAGEUX", "PARCIALMENTE NUBOSO")
        3 -> tr("COPERTO", "OVERCAST", "BEDECKT", "COUVERT", "CUBIERTO")
        45, 48 -> tr("NEBBIA", "FOG", "NEBEL", "BROUILLARD", "NIEBLA")
        51, 53, 55 -> tr("PIOVIGGINE", "DRIZZLE", "NIESELREGEN", "BRUINE", "LLOVIZNA")
        56, 57 -> tr("PIOVIGGINE GELATA", "FREEZING DRIZZLE", "GEFRIERENDER NIESEL", "BRUINE VERGLAÇANTE", "LLOVIZNA HELADA")
        61, 63, 65 -> tr("PIOGGIA", "RAIN", "REGEN", "PLUIE", "LLUVIA")
        66, 67 -> tr("PIOGGIA GELATA", "FREEZING RAIN", "GEFRIERENDER REGEN", "PLUIE VERGLAÇANTE", "LLUVIA HELADA")
        71, 73, 75 -> tr("NEVE", "SNOW", "SCHNEE", "NEIGE", "NIEVE")
        77 -> tr("GRANULI DI NEVE", "SNOW GRAINS", "SCHNEEGRIESEL", "NEIGE EN GRAINS", "CINARRA")
        80, 81, 82 -> tr("ROVESCI", "SHOWERS", "SCHAUER", "AVERSES", "CHUBASCOS")
        85, 86 -> tr("ROVESCI DI NEVE", "SNOW SHOWERS", "SCHNEESCHAUER", "AVERSES DE NEIGE", "CHUBASCOS DE NIEVE")
        95 -> tr("TEMPORALE", "THUNDERSTORM", "GEWITTER", "ORAGE", "TORMENTA")
        96, 99 -> tr("TEMPORALE E GRANDINE", "THUNDERSTORM, HAIL", "GEWITTER MIT HAGEL", "ORAGE ET GRÊLE", "TORMENTA Y GRANIZO")
        else -> "--"
    }

    /** Rosa dei venti a 8 settori: ovest e' O in italiano, W in inglese. */
    fun windDirection(degrees: Double?): String {
        if (degrees == null) return "--"
        // **La O non vuol dire la stessa cosa dappertutto.** In italiano,
        // francese e spagnolo e' l'ovest; in tedesco e' l'est (Ost), e
        // l'ovest e' W come in inglese.
        val sectors = when (Lingue.corrente) {
            Lingua.INGLESE -> listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            Lingua.TEDESCO -> listOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")
            Lingua.ITALIANO, Lingua.FRANCESE, Lingua.SPAGNOLO -> listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        }
        val idx = (((degrees % 360.0) + 360.0) % 360.0 / 45.0).toInt() % 8
        return sectors[idx]
    }

    /**
     * Quanto e' coperto, da 0 a 1, come valore continuo.
     * Serve a far cambiare carattere alla nuvola invece di farla comparire.
     */
    fun cloudiness(code: Int?): Float = when (code) {
        null, 0 -> 0f
        1 -> 0.22f
        2 -> 0.55f
        3 -> 0.88f
        45, 48 -> 0.78f
        51, 53, 55, 56, 57 -> 0.82f
        61, 63, 65, 66, 67 -> 0.94f
        71, 73, 75, 77 -> 0.88f
        80, 81, 82 -> 0.94f
        85, 86 -> 0.90f
        95 -> 1f
        96, 99 -> 1f
        else -> 0.5f
    }

    /** Famiglia meteo, per colorare la barra delle ore. */
    enum class Family { ASCIUTTO, NUVOLOSO, NEBBIA, PIOGGIA, NEVE, TEMPORALE }

    fun family(code: Int?): Family = when (code) {
        null, 0, 1 -> Family.ASCIUTTO
        2, 3 -> Family.NUVOLOSO
        45, 48 -> Family.NEBBIA
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Family.PIOGGIA
        71, 73, 75, 77, 85, 86 -> Family.NEVE
        95, 96, 99 -> Family.TEMPORALE
        else -> Family.ASCIUTTO
    }
}

/**
 * Le famiglie che portano precipitazione.
 *
 * Sta qui e non nel file della scultura, dov'era: parla di codici WMO, non di
 * come si disegna una nuvola, e la scultura era solo il primo posto in cui e'
 * servita. Da qui la usano la schermata principale e la barra delle ore senza
 * dipendere da millequattrocento righe di Compose - e si puo' provare.
 *
 * Attorno a questa funzione c'e' una trappola gia' pagata: **se il codice dice
 * che piove, deve piovere**. I millimetri decidono quanto forte, non se. Un
 * temporale previsto all'ottanta per cento puo' avere zero millimetri in
 * quell'ora esatta, e sotto la scritta TEMPORALE non cadeva niente.
 */
fun Wmo.Family.isWet(): Boolean =
    this == Wmo.Family.PIOGGIA || this == Wmo.Family.NEVE || this == Wmo.Family.TEMPORALE
