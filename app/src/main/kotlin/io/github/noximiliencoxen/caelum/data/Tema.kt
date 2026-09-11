package io.github.noximiliencoxen.caelum.data

/**
 * Chiaro, scuro, o l'ora vera.
 *
 * **In quest'app il tema non e' una tavolozza, e' un orologio.** Il colore del
 * cielo, quello dei testi, le tinte delle grandezze e adesso anche la luce
 * della scena dipinta discendono tutti da un numero solo: quanto e' alto il
 * sole all'ora mostrata. Non esiste da nessuna parte una coppia "colori chiari"
 * e "colori scuri" fra cui scegliere.
 *
 * Quindi la scelta manuale non sostituisce una tavolozza: **blocca l'ora**.
 * [CHIARO] mette il sole allo zenit, [SCURO] lo mette sotto l'orizzonte, e da
 * li' in giu' tutto il resto si ricalcola come farebbe a mezzogiorno o a
 * mezzanotte. Una cosa sola comanda, ed e' la stessa che comandava prima.
 *
 * L'alternativa - due tavolozze vere accanto a quella dell'ora - vorrebbe dire
 * tre sorgenti di colore che devono restare d'accordo fra loro, e ogni tinta
 * nuova andrebbe decisa tre volte. Questa strada non aggiunge niente da tenere
 * allineato: aggiunge un valore a un conto che c'era gia'.
 */
enum class Tema(val label: String) {
    /** L'ora vera della localita': il comportamento di sempre. */
    AUTO("AUTOMATICO"),

    /** Mezzogiorno fisso. */
    CHIARO("CHIARO"),

    /** Notte fissa. */
    SCURO("SCURO");

    companion object {
        fun of(name: String?): Tema = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * L'altezza del sole imposta da un tema, o nulla se comanda l'ora.
 *
 * I due numeri non sono gli estremi assoluti (-1 e 1) ma **quelli che il cielo
 * usa davvero**: a mezzogiorno pieno `SkyState.of` vuole 0,92 perche' oltre non
 * cambia piu' niente, e a notte piena -0,75 basta a spegnere ogni residuo di
 * crepuscolo. Usare gli estremi darebbe le stesse tinte con meno margine.
 */
val Tema.forcedAltitude: Float?
    get() = when (this) {
        Tema.AUTO -> null
        Tema.CHIARO -> 0.92f
        Tema.SCURO -> -0.75f
    }
