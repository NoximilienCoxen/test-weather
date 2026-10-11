package io.github.noximiliencoxen.caelum.data

/** Quale motore numerico usare per la previsione.
 *
 * AUTO lascia decidere a Open-Meteo il modello migliore per il posto scelto.
 * ICON-2I e' il modello ad alta risoluzione di ARPAE per l'Italia: piu'
 * preciso qui, ma non copre il resto del mondo.
 */
enum class WeatherModel(val apiValue: String, val label: String) {
    AUTO("best_match", "AUTO"),
    ICON_2I("italia_meteo_arpae_icon_2i", "ICON-2I"),
}
