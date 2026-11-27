package com.caelumweather.ui.sala

import com.caelumweather.lingua.tr

/**
 * Le schermate del tempo: sei sempre, piu' neve e mare dove ci sono.
 *
 * **Erano sette.** "La settimana" ripeteva quasi tutta la prima: la stessa
 * striscia dei giorni e, nei riquadri, gli stessi numeri delle celle di Oggi.
 * Adesso la settimana sta dentro Oggi (il riassunto, la striscia, l'anteprima
 * a tenere premuto) e chi sfoglia trova solo schermate che dicono cose nuove.
 *
 * **Non portano piu' con se' il proprio fondo.** Finche' ogni schermata si
 * disegnava la propria carta, qui dentro c'erano anche le macchie d'acquerello
 * che le spettavano; adesso il cielo e' uno solo per tutte e sta nella Shell,
 * e questo elenco e' tornato a essere quello che dice di essere: l'ordine delle
 * stanze e come si chiamano.
 *
 * L'ordine in cui si sfogliano e' [inOrdine]; l'indice delle voci qui sotto e'
 * quello con cui le chiama la galleria della CI (`--ei sezione`), e per non
 * rinumerarle le schede aggiunte dopo vanno in fondo all'elenco anche quando
 * si sfogliano altrove. Il numero romano che le accompagnava non lo leggeva piu' nessuno e
 * se n'e' andato: chi guarda non chiama queste schermate "sale".
 */
enum class SalaRoom(
    private val headingIt: String,
    private val headingEn: String,
    private val headingDe: String,
    private val headingFr: String,
    private val headingEs: String,
) {
    OGGI("Oggi", "Today", "Heute", "Aujourd'hui", "Hoy"),
    PIOGGIA("La pioggia", "Rain", "Regen", "La pluie", "La lluvia"),
    LUNA("La luna", "The moon", "Der Mond", "La lune", "La luna"),
    ARIA("L'aria", "Air", "Die Luft", "L'air", "El aire"),
    VENTO("Il vento", "Wind", "Der Wind", "Le vent", "El viento"),
    UV("I raggi UV", "UV rays", "UV-Strahlung", "Les rayons UV", "Los rayos UV"),

    /**
     * Solo dove c'e' il mare (`UiState.mareMostrato`): altrove la scheda non
     * esiste. Sta in fondo apposta: i dati del mare arrivano dopo la
     * previsione, e una scheda che spuntasse in mezzo sposterebbe sotto il
     * dito quella che si sta guardando.
     */
    MARE("Il mare", "The sea", "Das Meer", "La mer", "El mar"),

    /**
     * Solo con la neve prevista nei prossimi giorni o al suolo
     * (`serveLaNeve`): altrove la scheda non esiste. **Si sfoglia subito dopo
     * la pioggia** (vedi [inOrdine]), e non in fondo come il mare: la neve
     * arriva con la previsione stessa, quindi la scheda c'e' gia' quando la
     * galleria si apre e non spunta sotto il dito. Le schede che seguono si
     * spostano di un posto, sempre nello stesso ordine.
     */
    NEVE("La neve", "Snow", "Schnee", "La neige", "La nieve"),
    ;

    /** Come si chiama, nella lingua dell'app. */
    val heading: String get() = tr(headingIt, headingEn, headingDe, headingFr, headingEs)

    companion object {
        /** Le schede nell'ordine in cui si sfogliano; neve e mare solo se ci sono. */
        fun inOrdine(conNeve: Boolean, conMare: Boolean): List<SalaRoom> = buildList {
            add(OGGI)
            add(PIOGGIA)
            if (conNeve) add(NEVE)
            addAll(listOf(LUNA, ARIA, VENTO, UV))
            if (conMare) add(MARE)
        }
    }
}
