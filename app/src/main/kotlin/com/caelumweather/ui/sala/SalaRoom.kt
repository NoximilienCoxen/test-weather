package com.caelumweather.ui.sala

import com.caelumweather.lingua.tr

/**
 * Le sette schermate del tempo, nell'ordine in cui si sfogliano.
 *
 * **Non portano piu' con se' il proprio fondo.** Finche' ogni schermata si
 * disegnava la propria carta, qui dentro c'erano anche le macchie d'acquerello
 * che le spettavano; adesso il cielo e' uno solo per tutte e sta nella Shell,
 * e questo elenco e' tornato a essere quello che dice di essere: l'ordine delle
 * stanze e come si chiamano.
 *
 * Il numero romano resta perche' e' l'ordine, e serve alla galleria della CI per
 * dire quale scatto e' quale. **Non si scrive piu' a schermo**: chi guarda non
 * chiama queste schermate "sale", e non deve impararlo per usarle.
 */
enum class SalaRoom(
    val roman: String,
    private val headingIt: String,
    private val headingEn: String,
    private val headingDe: String,
    private val headingFr: String,
    private val headingEs: String,
) {
    OGGI("I", "Oggi", "Today", "Heute", "Aujourd'hui", "Hoy"),
    SETTIMANA("II", "La settimana", "The week", "Die Woche", "La semaine", "La semana"),
    PIOGGIA("III", "La pioggia", "Rain", "Regen", "La pluie", "La lluvia"),
    LUNA("IV", "La luna", "The moon", "Der Mond", "La lune", "La luna"),
    ARIA("V", "L'aria", "Air", "Die Luft", "L'air", "El aire"),
    VENTO("VI", "Il vento", "Wind", "Der Wind", "Le vent", "El viento"),
    UV("VII", "I raggi UV", "UV rays", "UV-Strahlung", "Les rayons UV", "Los rayos UV"),
    ;

    /** Come si chiama, nella lingua dell'app. */
    val heading: String get() = tr(headingIt, headingEn, headingDe, headingFr, headingEs)
}
