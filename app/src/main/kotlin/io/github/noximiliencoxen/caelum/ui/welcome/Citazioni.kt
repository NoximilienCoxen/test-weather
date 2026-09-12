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
/** Quello che si sa della provenienza, quando non si e' potuto aprire un originale. */
internal const val FORNITA = "Raccolta fornita per l'app; originale non verificato"

data class Citazione(
    val testo: String,
    val autore: String,
    val originale: String,
    val fonte: String,
) {
    /** Vero quando si e' potuto aprire l'originale. */
    val verificata: Boolean get() = fonte != FORNITA

    /**
     * Come firmarla sotto la frase.
     *
     * **Le tre verificate portano il nome e basta; le altre un "attribuito a".**
     * Il campo `fonte` diceva gia' la verita' - ma la diceva **al codice**, e
     * chi apre l'app legge una frase con sotto un nome, non un campo di una
     * `data class`. Fra le due, quella che conta e' la seconda: mettere in bocca
     * a una persona vera parole che non si e' riusciti a rintracciare e' la
     * stessa cosa di un'allerta finta, e anzi piu' difficile da smentire.
     *
     * «Attribuito a» non e' una scusa ne' una diminuzione: e' la formula con cui
     * qualunque museo scrive un cartellino quando l'attribuzione e' probabile e
     * non provata. Le frasi restano tutte - sono state scelte da chi
     * commissiona l'app - e nessuna viene spacciata per cio' che non e'.
     */
    val firma: String get() = if (verificata) autore else "attribuito a $autore"
}

internal val Citazioni = listOf(
    Citazione(
        testo = "Sarà difficile nominare un genere di paesaggio in cui il cielo " +
            "non sia la nota dominante, la misura di ogni scala, l'organo " +
            "principale del sentimento.",
        autore = "John Constable",
        originale = "It will be difficult to name a class of landscape in which " +
            "the sky is not the key note, the standard of scale, and the chief " +
            "organ of sentiment.",
        fonte = "Lettera a John Fisher, 23 ottobre 1821",
    ),
    Citazione(
        testo = "Mi capita spesso di pensare che la notte sia più viva e più " +
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
    // ── Le frasi arrivate da chi ha commissionato l'app ──────────────────────
    //
    // **Portano `fonte = FORNITA` e non una citazione inventata.** Vengono da un
    // elenco raccolto altrove, non da un originale che si sia potuto aprire:
    // scriverci accanto "Lettera del tal giorno" sarebbe stato inventare la
    // parte che conta. Sono quasi certamente vere - sono fra le piu' note di
    // ognuno - ma "quasi certamente" e' esattamente il grado di certezza che
    // questo campo serve a dichiarare, non a nascondere.
    //
    // Chi vuole promuoverne una: ne trovi l'originale, lo metta in `originale`
    // e la fonte precisa in `fonte`. Fino ad allora restano cosi'.
    Citazione(
        testo = "Il colore è un mezzo per esercitare un influsso diretto " +
            "sull'anima. Il colore è il tasto, l'occhio è il martelletto, " +
            "l'anima è un pianoforte con molte corde.",
        autore = "Wassily Kandinsky",
        originale = "Lo spirituale nell'arte, 1912",
        fonte = FORNITA,
    ),
    Citazione(
        testo = "Non aver paura della perfezione: non la raggiungerai mai.",
        autore = "Salvador Dalí",
        originale = "",
        fonte = FORNITA,
    ),
    Citazione(
        testo = "Le idee non si rifiutano: germinano nella società, poi " +
            "pensatori e artisti le esprimono.",
        autore = "Lucio Fontana",
        originale = "",
        fonte = FORNITA,
    ),
    Citazione(
        testo = "Più la critica è ostile, più l'artista dovrebbe essere incoraggiato.",
        autore = "Marcel Duchamp",
        originale = "",
        fonte = FORNITA,
    ),
    Citazione(
        testo = "La creatività richiede coraggio.",
        autore = "Henri Matisse",
        originale = "",
        fonte = FORNITA,
    ),
    Citazione(
        testo = "L'artista moderno, mi pare, lavora per esprimere un mondo " +
            "interiore: il movimento, l'energia e altre forze interiori.",
        autore = "Jackson Pollock",
        originale = "",
        fonte = FORNITA,
    ),
    Citazione(
        testo = "Fai alle tue figure dei capelli che un vento invisibile sembri " +
            "far danzare intorno ai loro volti giovanili.",
        autore = "Leonardo da Vinci",
        originale = "",
        fonte = FORNITA,
    ),
    Citazione(
        testo = "Il mondo della realtà ha i suoi limiti; il mondo " +
            "dell'immaginazione è senza limiti.",
        autore = "Jean-Jacques Rousseau",
        originale = "",
        fonte = FORNITA,
    ),
    Citazione(
        testo = "L'oggetto dell'arte non è riprodurre la realtà, ma creare una " +
            "realtà della stessa intensità.",
        autore = "Alberto Giacometti",
        originale = "",
        fonte = FORNITA,
    ),
    Citazione(
        testo = "La natura non è solo tutto ciò che è visibile agli occhi: " +
            "include anche le immagini interiori dell'anima.",
        autore = "Edvard Munch",
        originale = "",
        fonte = FORNITA,
    ),
    Citazione(
        testo = "La pittura è il nipote della natura.",
        autore = "Rembrandt",
        originale = "",
        fonte = FORNITA,
    ),
    Citazione(
        testo = "Tutti discutono la mia arte e affermano di comprenderla, come " +
            "se fosse necessario comprendere, quando invece basterebbe amare.",
        autore = "Claude Monet",
        originale = "",
        fonte = FORNITA,
    ),
    Citazione(
        testo = "Non dipingo mai sogni o incubi. Dipingo la mia realtà.",
        autore = "Frida Kahlo",
        originale = "",
        fonte = FORNITA,
    ),
)

/** Una frase a caso. Chi la chiama lo fa **una volta sola**, e poi se la tiene. */
internal fun citazioneACaso(): Citazione = Citazioni.random()
