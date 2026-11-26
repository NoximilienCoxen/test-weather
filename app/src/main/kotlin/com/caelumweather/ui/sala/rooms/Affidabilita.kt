package com.caelumweather.ui.sala.rooms

import com.caelumweather.lingua.tr

/**
 * **Quanto fidarsi** di un giorno della settimana, detto a parole.
 *
 * Non c'e' un insieme di corse (ensemble) da cui leggere la dispersione: l'app
 * chiede una corsa sola. Resta la cosa che vale per ogni modello globale, e che
 * le verifiche dei centri meteo ripetono da anni: l'errore cresce con
 * l'anticipo. Fino a dopodomani una previsione e' buona; al terzo e quarto
 * giorno l'andamento regge ma ore e millimetri si spostano; dal quinto dice la
 * tendenza, non il dettaglio.
 *
 * Per oggi, domani e dopodomani torna nullo: un'etichetta "affidabile" su ogni
 * giorno vicino sarebbe rumore, e insegnerebbe a non leggerla piu'.
 */
internal fun affidabilitaDelGiorno(indice: Int): String? = when {
    indice <= 2 -> null
    indice <= 4 -> tr("affidabilità discreta", "fair confidence", "mittlere Zuverlässigkeit", "fiabilité moyenne", "fiabilidad media")
    else -> tr("solo la tendenza", "trend only", "nur der Trend", "tendance seulement", "solo la tendencia")
}
