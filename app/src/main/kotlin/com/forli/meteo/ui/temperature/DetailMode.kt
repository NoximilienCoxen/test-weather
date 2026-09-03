package com.forli.meteo.ui.temperature

/**
 * Le grandezze che la schermata di dettaglio racconta, una pagina ciascuna.
 *
 * [title] e' il titolo lungo mostrato nella barra in cima alla schermata.
 * [chipLabel] e' l'etichetta breve della pillola: deve stare in poco spazio e
 * leggersi in una riga sola anche su schermi piccoli.
 * [unitLabel] e' l'unita' della cifra gigante, scritta **una volta sotto di
 * essa** invece che ripetuta in ogni riga della tabella. Senza, la cifra diceva
 * "8", "0", "1" e non c'era modo di sapere se fossero ore, millimetri o metri
 * al secondo: solo la temperatura portava il suo grado, perche' e' l'unico
 * simbolo che il prisma estrude insieme alle cifre.
 *
 * ARIA e' una pagina nuova. La qualita' dell'aria l'app la sapeva gia' - c'e'
 * un repository e un widget che la mostra - ma dentro l'app non compariva da
 * nessuna parte, e la vecchia pagina chiamata ARIA in realta' parlava di vento.
 */
enum class DetailMode(
    val title: String,
    val chipLabel: String,
    val unitLabel: String,
) {
    TEMPERATURA(title = "TEMPERATURA", chipLabel = "Temp", unitLabel = ""),
    SOLE(title = "SOLE", chipLabel = "Sole", unitLabel = "ORE DI SOLE"),
    PRECIPITAZIONI(title = "PRECIPITAZIONI", chipLabel = "Pioggia", unitLabel = "MM NEL GIORNO"),
    VENTO(title = "VENTO", chipLabel = "Vento", unitLabel = "M/S"),
    ARIA(title = "QUALITA' DELL'ARIA", chipLabel = "Aria", unitLabel = "INDICE EUROPEO");

    /** Vero per le grandezze la cui cifra e' un totale del giorno, non un'istantanea. */
    val isDailyTotal: Boolean
        get() = this == SOLE || this == PRECIPITAZIONI
}
