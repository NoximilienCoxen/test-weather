package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.runtime.Immutable
import io.github.noximiliencoxen.caelum.data.SkyState

/**
 * La scena, in numeri che scorrono invece che in caselle.
 *
 * `SalaCondition` decide bene il **testo** - "Temporale con grandine" non ha
 * mezze misure - ma decide male il **disegno**: fra sereno e coperto ci sono tutte le
 * nuvolosita' del mondo, e con l'enum comparivano tutte insieme, in un
 * fotogramma, tutte le volte che il cielo cambiava idea.
 *
 * Ogni numero qui dentro sta fra 0 e 1 ed e' gia' passato per la sua molla. Chi
 * disegna non chiede piu' "che tempo fa": chiede **quanto**.
 */
@Immutable
data class Scena(
    /** Quanto si vede il sole. */
    val sole: Float,
    /** Quanto il cielo e' chiuso. */
    val copertura: Float,
    /** Il fronte del temporale: le due masse larghe, il fulmine, il riverbero. */
    val tempesta: Float,
    /** Quanto cade. Non **se** cade: quanto. */
    val bagnato: Float,
    /** Chicco invece di tratto. */
    val ghiaccio: Float,
    /** Fiocco invece di tratto. */
    val neve: Float,
    /** Luna invece di sole, stelle invece di uccelli. */
    val notte: Float,
) {
    /** Vero finche' qualcosa si sta ancora spostando: serve a tenere acceso
     *  l'orologio per tutta la durata di un passaggio, e non un istante di
     *  piu'. */
    val inTransito: Boolean
        get() = inMezzo(copertura) || inMezzo(notte) || inMezzo(tempesta) ||
            inMezzo(sole) || bagnato > 0.01f
}

/**
 * Dove vuole arrivare la scena, prima che le molle ci arrivino.
 *
 * **Due dei sette numeri non li calcola nessuno qui**: `sunPresence` e
 * `moonPresence` esistono gia' in `SunClock`, sono gia' continui e sono gia'
 * smorzati a monte in `MeteoApp`. Sono anche **gli stessi** che usano i widget,
 * quindi la galleria e la schermata di blocco non raccontano due cieli diversi.
 * `moonPresence` tocca 1 esattamente a un'altezza del sole di -0,42, cioe' dove
 * la vecchia soglia dichiarava "notte": passare dal booleano al continuo non
 * cambia niente a notte piena, riempie solo il crepuscolo che prima veniva
 * arrotondato via.
 *
 * E la **copertura viene dal dato vero** quando c'e'. L'enum e' il ripiego, non
 * la fonte: il codice WMO da cui l'enum nasce e' esso stesso derivato dalla
 * nuvolosita' oraria, quindi leggerla direttamente non e' una scorciatoia, e'
 * togliere un passaggio che buttava via precisione.
 */
fun scenaBersaglio(
    sky: SkyState,
    condition: SalaCondition,
    /** Vero quando il codice WMO e' di famiglia neve. Lo legge la Shell dal
     *  codice, e da questo giro lo dice anche [SalaCondition.NEVE]: restano
     *  tutti e due perche' la seconda **nasce** dalla prima, e un giorno in cui
     *  divergessero e' il difetto che questo giro ha chiuso. */
    nevicaWmo: Boolean,
    coperturaOraria: Int?,
    pioggiaMm: Double?,
): Scena {
    val temporale = condition == SalaCondition.TEMPORALE ||
        condition == SalaCondition.TEMPORALE_GRANDINE
    // **La grandine e' solo quella del temporale, e prima non lo era.** I
    // rovesci di neve - 85 e 86 - erano etichettati "grandine" dall'enum e
    // "neve" dalla famiglia WMO, e siccome le due strade arrivavano tutte e due
    // qui il cielo chiedeva **chicchi e fiocchi insieme**: due sostanze che
    // cadono dalla stessa nuvola per lo stesso codice. Adesso la strada e' una.
    val grandina = condition == SalaCondition.TEMPORALE_GRANDINE
    val nevica = nevicaWmo || condition == SalaCondition.NEVE
    val cade = condition != SalaCondition.SERENO && condition != SalaCondition.NUVOLOSO
    return Scena(
        sole = sky.sunPresence,
        notte = sky.moonPresence,
        // **Il dato vero non puo' smentire il codice.** Prendere la nuvolosita'
        // oraria e basta sembrava piu' preciso, e ha prodotto uno scatto in cui
        // la sala diceva "Rovescio di meta' pomeriggio" sotto un sole pieno
        // **senza una nuvola**: l'ora forzata era piovosa per codice ma serena
        // per nuvolosita', e vinceva la seconda. E' la trappola #14 per la
        // stessa strada di sempre - se il codice WMO dice che piove **deve
        // piovere**, e una pioggia senza nuvole e' lo stesso errore delle gocce
        // che non cadevano. Il dato vero decide **quanto** dentro il possibile;
        // il codice decide il minimo.
        copertura = maxOf(
            coperturaOraria?.let { (it / 100f).coerceIn(0f, 1f) } ?: 0f,
            coperturaMinima(condition),
        ),
        tempesta = if (temporale) 1f else 0f,
        // Una pioviggine non e' un rovescio, e finora si dipingevano uguali. Il
        // minimo non e' zero: se il codice WMO dice che piove **deve piovere**,
        // e i millimetri decidono quanto forte, non se (trappola #14).
        bagnato = if (!cade) 0f else ((pioggiaMm?.toFloat() ?: 1f) / 1.8f).coerceIn(0.55f, 1f),
        ghiaccio = if (grandina) 1f else 0f,
        neve = if (nevica) 1f else 0f,
        // Nota per chi tocca queste due righe: **non possono valere uno
        // insieme**. Il disegno le sovrappone senza chiedere - e' cosi' che si
        // ottiene il nevischio - quindi due sostanze accese in pieno sono due
        // sostanze che cadono davvero, tutte e due, nello stesso cielo.
    )
}

/**
 * Il cielo **almeno** cosi' chiuso, secondo il codice del tempo.
 *
 * Non e' un ripiego per quando il dato manca: e' un pavimento. Un temporale con
 * il venti per cento di nuvolosita' non esiste, e se i due numeri litigano ha
 * ragione quello che ha dato il nome alla giornata.
 *
 * **Non e' piu' privata, e la copia che c'era in giro e' sparita.** La barra
 * delle ore tingeva le sue ventiquattro colonne con questa identica tabella
 * ricopiata a mano: due elenchi da tenere in fase, e la prima riga aggiunta qui
 * - la neve - sarebbe stata la prima a divergere.
 */
internal fun coperturaMinima(condition: SalaCondition): Float = when (condition) {
    SalaCondition.SERENO -> 0f
    SalaCondition.NUVOLOSO -> 0.45f
    SalaCondition.PIOGGIA -> 0.80f
    SalaCondition.NEVE -> 0.85f
    SalaCondition.TEMPORALE, SalaCondition.TEMPORALE_GRANDINE -> 0.95f
}
