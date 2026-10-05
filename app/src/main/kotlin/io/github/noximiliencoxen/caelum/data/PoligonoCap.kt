package io.github.noximiliencoxen.caelum.data

/**
 * Se un punto sta dentro un poligono CAP.
 *
 * Il CAP scrive i poligoni come coppie "lat,lon" separate da spazi, chiuse
 * (l'ultima coppia ripete la prima): cosi' li porta l'IFRC Alert Hub, per
 * esempio "57.153,88.562 57.634,89.392 ..." per la regione di Tomsk il 5
 * ottobre 2026. Prima la latitudine, al contrario del GeoJSON.
 *
 * Il test e' quello del raggio: si contano gli attraversamenti dei lati da
 * parte di una semiretta che parte dal punto. Sulle aree di una regione la
 * curvatura della Terra non sposta il risultato in modo che conti. Un
 * poligono illeggibile torna `null`: non dice ne' si' ne' no.
 */
internal fun dentroPoligonoCap(poligono: String, lat: Double, lon: Double): Boolean? {
    val punti = poligono.trim().split(Regex("\\s+")).mapNotNull { coppia ->
        val parti = coppia.split(',')
        if (parti.size < 2) return@mapNotNull null
        val la = parti[0].toDoubleOrNull() ?: return@mapNotNull null
        val lo = parti[1].toDoubleOrNull() ?: return@mapNotNull null
        la to lo
    }
    if (punti.size < 3) return null
    var dentro = false
    var j = punti.size - 1
    for (i in punti.indices) {
        val (yi, xi) = punti[i]
        val (yj, xj) = punti[j]
        if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
            dentro = !dentro
        }
        j = i
    }
    return dentro
}
