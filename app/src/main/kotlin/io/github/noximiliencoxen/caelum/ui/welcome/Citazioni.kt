package io.github.noximiliencoxen.caelum.ui.welcome

/**
 * Le frasi che l'Ingresso espone, una diversa a ogni primo avvio.
 *
 * **Regola dura: ogni frase porta la sua fonte, e senza fonte non entra.**
 * Un'app che mette in bocca a un artista vero una frase che non ha mai detto
 * sta facendo la stessa cosa che questo progetto si vieta altrove - prestare la
 * propria faccia a qualcosa che non viene da dove sembra (vedi la nota sugli
 * agganci in `MainActivity`). Una citazione inventata e' piu' difficile da
 * smentire di un'allerta finta, non piu' facile.
 *
 * Le tre di adesso sono state verificate una per una contro la fonte primaria.
 * Ne mancano altre trovate a memoria - un paio attribuite a Monet e il "The Sun
 * is God" di Turner - **escluse apposta**: sono in giro dappertutto ma non sono
 * riuscito a risalire a un originale, e circolare molto non e' una fonte.
 *
 * @param testo la frase in italiano. La traduzione e' nostra (vedi [originale]):
 *   e' il punto in cui una citazione verificata puo' comunque tradire, quindi
 *   l'originale resta scritto qui accanto e chiunque puo' controllarla.
 */
data class Citazione(
    val testo: String,
    val autore: String,
    val originale: String,
    val fonte: String,
)

internal val Citazioni = listOf(
    Citazione(
        testo = "Sara' difficile nominare un genere di paesaggio in cui il cielo " +
            "non sia la nota dominante, la misura di ogni scala, l'organo " +
            "principale del sentimento.",
        autore = "John Constable",
        originale = "It will be difficult to name a class of landscape in which " +
            "the sky is not the key note, the standard of scale, and the chief " +
            "organ of sentiment.",
        fonte = "Lettera a John Fisher, 23 ottobre 1821",
    ),
    Citazione(
        testo = "Mi capita spesso di pensare che la notte sia piu' viva e piu' " +
            "riccamente colorata del giorno.",
        autore = "Vincent van Gogh",
        originale = "I often think that the night is more alive and more richly " +
            "coloured than the day.",
        fonte = "Lettera a Theo, 8 settembre 1888",
    ),
    Citazione(
        testo = "La vista delle stelle mi fa sempre sognare.",
        autore = "Vincent van Gogh",
        originale = "The sight of the stars always makes me dream.",
        fonte = "Lettera a Theo n. 638, Arles, 9 o 10 luglio 1888",
    ),
)

/** Una frase a caso. Chi la chiama lo fa **una volta sola**, e poi se la tiene. */
internal fun citazioneACaso(): Citazione = Citazioni.random()
