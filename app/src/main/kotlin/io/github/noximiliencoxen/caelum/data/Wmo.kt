package io.github.noximiliencoxen.caelum.data

/** Tipologia di precipitazione mostrata nella tabella della pagina Precip. */
enum class PrecipKind(val label: String) {
    NONE("--"),
    RAIN("PIOGGIA"),
    SNOW("NEVE"),
    MIXED("MISTA"),
    HAIL("GRANDINE"),
}

/** Traduzione dei codici WMO usati da Open-Meteo. */
object Wmo {

    fun condition(code: Int?): String = when (code) {
        null -> "--"
        0 -> "SERENO"
        1 -> "POCO NUVOLOSO"
        2 -> "PARZ. NUVOLOSO"
        3 -> "COPERTO"
        45, 48 -> "NEBBIA"
        51, 53, 55 -> "PIOVIGGINE"
        56, 57 -> "PIOVIGGINE GELATA"
        61, 63, 65 -> "PIOGGIA"
        66, 67 -> "PIOGGIA GELATA"
        71, 73, 75 -> "NEVE"
        77 -> "GRANULI DI NEVE"
        80, 81, 82 -> "ROVESCI"
        85, 86 -> "ROVESCI DI NEVE"
        95 -> "TEMPORALE"
        96, 99 -> "TEMPORALE E GRANDINE"
        else -> "--"
    }

    fun precipKind(code: Int?): PrecipKind = when (code) {
        null -> PrecipKind.NONE
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> PrecipKind.RAIN
        71, 73, 75, 77, 85, 86 -> PrecipKind.SNOW
        95 -> PrecipKind.RAIN
        96, 99 -> PrecipKind.HAIL
        else -> PrecipKind.NONE
    }

    /** Rosa dei venti in italiano a 8 settori. */
    fun windDirection(degrees: Double?): String {
        if (degrees == null) return "--"
        val sectors = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
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
