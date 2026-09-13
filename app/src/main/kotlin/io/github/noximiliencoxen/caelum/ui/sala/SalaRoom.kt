package io.github.noximiliencoxen.caelum.ui.sala

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
enum class SalaRoom(val roman: String, val heading: String) {
    OGGI("I", "Oggi"),
    SETTIMANA("II", "La settimana"),
    PIOGGIA("III", "La pioggia"),
    LUNA("IV", "La luna"),
    ARIA("V", "L'aria"),
    VENTO("VI", "Il vento"),
    UV("VII", "I raggi UV"),
}
