package com.forli.meteo.ui.temperature

/**
 * Le modalita' della schermata di dettaglio.
 *
 * [title] e' il titolo lungo mostrato nella barra in cima alla schermata.
 * [chipLabel] e' l'etichetta breve mostrata sul chip di selezione: deve
 * stare in poco spazio e si legge in una riga sola anche su schermi piccoli.
 */
enum class DetailMode(val title: String, val chipLabel: String) {
    TEMPERATURA("TEMPERATURA", "Temp"),
    SOLE("SOLE", "Sole"),
    PRECIPITAZIONI("PIOGGIA", "Pioggia"),
    ARIA("VENTO", "Vento"),
}
