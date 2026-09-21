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
    // **Qui stava `inTransito`**, che diceva se una transizione era ancora in
    // volo e serviva a tenere acceso l'orologio della scena "per tutta la
    // durata di un passaggio, e non un istante di piu'". Non l'ha mai chiamato
    // nessuno: l'orologio e' sempre acceso. Non e' una svista da correggere
    // spegnendolo - stelle, nuvole e corona si muovono anche a scena ferma, e
    // un cielo fermo non e' un cielo - quindi se ne va la promessa, non il
    // cielo.
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
    return Scena(
        sole = sky.sunPresence,
        notte = sky.moonPresence,
        // Il dato vero e il codice, messi d'accordo: vedi [coperturaDi].
        copertura = coperturaDi(condition, coperturaOraria),
        tempesta = if (temporale) 1f else 0f,
        // Una pioviggine non e' un rovescio, e finora si dipingevano uguali. Il
        // minimo non e' zero: se il codice WMO dice che piove **deve piovere**,
        // e i millimetri decidono quanto forte, non se (trappola #14).
        bagnato = if (!cade(condition)) 0f else ((pioggiaMm?.toFloat() ?: 1f) / 1.8f).coerceIn(0.55f, 1f),
        ghiaccio = if (grandina) 1f else 0f,
        neve = if (nevica) 1f else 0f,
        // Nota per chi tocca queste due righe: **non possono valere uno
        // insieme**. Il disegno le sovrappone senza chiedere - e' cosi' che si
        // ottiene il nevischio - quindi due sostanze accese in pieno sono due
        // sostanze che cadono davvero, tutte e due, nello stesso cielo.
    )
}

/**
 * Vero quando dalla condizione **cade qualcosa**: pioggia, neve, grandine.
 *
 * E' la riga che separa le due meta' di [coperturaDi], e vale la pena che abbia
 * un nome: da una parte i cieli asciutti, dove la nuvolosita' vera comanda da
 * sola; dall'altra quelli da cui viene giu' roba, dove il codice impone un
 * minimo perche' una precipitazione senza nuvole e' una precipitazione falsa.
 */
internal fun cade(condition: SalaCondition): Boolean =
    condition != SalaCondition.SERENO && condition != SalaCondition.NUVOLOSO

/**
 * Quanto e' chiuso il cielo: il dato vero e il codice, messi d'accordo.
 *
 * **Sta qui e si chiama da due posti**, la scena e le ventiquattro colonne
 * della barra delle ore. La tabella dei minimi era gia' stata unificata per
 * questo motivo - il commento in `SalaBarraOre` lo racconta - ma la formula
 * intorno era ancora ricopiata, e sarebbe stata la prossima a divergere: la
 * barra avrebbe tinto le colonne con un cielo che la sala non mostra piu'.
 *
 * Due meta', e la differenza e' [cade]:
 *
 * - **dove cade qualcosa**, il minimo del codice e' un pavimento. E' la
 *   trappola #14: se il codice WMO dice che piove **deve piovere**, e una
 *   pioggia senza nuvole e' lo stesso errore delle gocce che non cadevano. Il
 *   dato vero decide *quanto* dentro il possibile;
 * - **dove non cade niente**, non c'e' niente da difendere, e il dato vero
 *   comanda da solo. E' li' che il pavimento faceva danni: quello del nuvoloso
 *   vale 0,45 e si applicava anche a giornate che di nuvole ne avevano il
 *   cinque per cento, imponendo tre masse in cielo e un grigio che fuori non
 *   c'era. Li' il minimo resta come **ripiego** per quando la nuvolosita'
 *   oraria manca del tutto - i modelli a corto raggio si fermano attorno alle
 *   settantadue ore.
 */
internal fun coperturaDi(condition: SalaCondition, coperturaOraria: Int?): Float {
    val vera = coperturaOraria?.let { (it / 100f).coerceIn(0f, 1f) }
    val minima = coperturaMinima(condition)
    return if (cade(condition)) maxOf(vera ?: 0f, minima) else vera ?: minima
}

/**
 * Il cielo **almeno** cosi' chiuso, secondo il codice del tempo.
 *
 * **E' un pavimento dove cade qualcosa, e un ripiego dove non cade niente.**
 * Un temporale con il venti per cento di nuvolosita' non esiste, e se i due
 * numeri litigano ha ragione quello che ha dato il nome alla giornata; ma un
 * "nuvoloso" con il cinque per cento di nuvolosita' esiste eccome, e li' il
 * numero e' l'unico dei due che sia stato misurato. Chi decide quale dei due
 * casi e' e' [coperturaDi]: questa tabella da' solo il numero.
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
