package com.forli.meteo.data

/**
 * Quale motore numerico usare per la previsione.
 *
 * "best_match" lascia decidere a Open-Meteo il modello migliore per il punto
 * richiesto; ICON-2I e' il modello ad alta risoluzione di ARPAE/ItaliaMeteo,
 * piu' preciso sull'Italia ma cieco altrove.
 */
enum class WeatherModel(val apiValue: String, val label: String) {
    AUTO("best_match", "AUTO"),
    ICON_2I("italia_meteo_arpae_icon_2i", "ICON-2I"),
}
