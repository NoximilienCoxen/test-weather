package io.github.noximiliencoxen.caelum.ui.feed

/**
 * Le sezioni del feed: una schermata piena ciascuna, in quest'ordine dall'alto
 * verso il basso.
 *
 * **Erano le sei pagine del foglio di dettaglio** (`DetailMode`), cioe' due
 * gesti di profondita' sotto la schermata che si apriva per prima. Adesso sono
 * la navigazione: si scorre dal basso verso l'alto e si passa dalla temperatura
 * alla pioggia, dalla pioggia all'aria. L'ordine non e' quello di prima ed e'
 * una scelta: **le prime tre sono le domande che si fanno tutti i giorni** -
 * quanti gradi, se piove, se si respira - e le altre tre stanno dopo perche' si
 * cercano piu' di rado.
 *
 * [title] e' il titolo in cima alla scheda. [chipLabel] e' l'etichetta breve:
 * la usa la colonna di icone sul bordo destro, che ha poco spazio e una riga
 * sola. [unitLabel] e' l'unita' della cifra gigante, scritta **una volta sotto
 * di essa** invece che ripetuta accanto a ogni numero - senza, la cifra diceva
 * "8", "0", "1" e non c'era modo di sapere se fossero ore, millimetri o metri
 * al secondo. Solo la temperatura porta il suo grado, perche' e' l'unico
 * simbolo che il prisma estrude insieme alle cifre.
 *
 * [stage] e' cio' che la scheda **ospitera'**, e sta scritto sul segnaposto.
 * Le schede nascono vuote apposta: cosa metterci dentro si decide una sezione
 * alla volta, e un riquadro che dichiara cosa manca e' un lavoro in corso,
 * mentre un riquadro vuoto e muto e' un difetto.
 */
enum class FeedSection(
    val title: String,
    val chipLabel: String,
    val unitLabel: String,
    val stage: String,
) {
    TEMPERATURA(
        title = "TEMPERATURA",
        chipLabel = "Temp",
        unitLabel = "",
        stage = "La scultura del meteo e la barra delle ore",
    ),
    PRECIPITAZIONI(
        title = "PRECIPITAZIONI",
        chipLabel = "Pioggia",
        unitLabel = "MM NEL GIORNO",
        stage = "Gocce che si posano sul vetro e scendono, e una pozza che cresce con i millimetri",
    ),
    ARIA(
        title = "QUALITA' DELL'ARIA",
        chipLabel = "Aria",
        unitLabel = "INDICE EUROPEO",
        stage = "Il pulviscolo in sospensione: piu' l'indice sale, piu' l'aria si fa densa da attraversare",
    ),
    VENTO(
        title = "VENTO",
        chipLabel = "Vento",
        unitLabel = "M/S",
        stage = "Erba e panni stesi che si piegano dalla parte da cui tira, e la forza si vede da quanto",
    ),
    SOLE(
        title = "SOLE",
        chipLabel = "Sole",
        unitLabel = "ORE DI SOLE",
        stage = "L'arco della giornata da percorrere col dito, dall'alba al tramonto",
    ),
    LUNA(
        title = "LUNA",
        chipLabel = "Luna",
        unitLabel = "",
        stage = "La sfera che gira col dito, con i suoi mari e la fase di stasera",
    );

    /**
     * Vero per le grandezze la cui cifra e' un totale del giorno, non
     * un'istantanea.
     *
     * La luna ci sta dentro: la fase e' del giorno, e scriverle accanto un'ora
     * precisa sarebbe la stessa bugia che l'intestazione diceva sulle ore di
     * sole - "OGGI  ·  15:00" sopra un numero che parla di tutta la giornata.
     */
    val isDailyTotal: Boolean
        get() = this == SOLE || this == PRECIPITAZIONI || this == LUNA
}
