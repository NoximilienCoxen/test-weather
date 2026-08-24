package com.forli.meteo.data

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
}
