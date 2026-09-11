package io.github.noximiliencoxen.caelum.ui.sala

/**
 * Le sette sale, nell'ordine in cui si sfogliano.
 *
 * Sostituisce `ui/feed/FeedSection`: non sono piu' schede di un feed, sono le
 * stanze di una galleria, e l'indicatore di percorso in fondo a ognuna dice
 * "SALA [roman] / VII" invece di accendere un'icona in una colonna.
 */
enum class SalaRoom(val roman: String, val heading: String, val blobs: List<WashBlobSpec>) {
    OGGI("I", "Oggi", SalaBlobs.oggi),
    SETTIMANA("II", "La settimana", SalaBlobs.settimana),
    PIOGGIA("III", "La pioggia", SalaBlobs.pioggia),
    LUNA("IV", "La luna", SalaBlobs.luna),
    ARIA("V", "L'aria", SalaBlobs.aria),
    VENTO("VI", "Il vento", SalaBlobs.vento),
    UV("VII", "I raggi UV", SalaBlobs.uv),
}
