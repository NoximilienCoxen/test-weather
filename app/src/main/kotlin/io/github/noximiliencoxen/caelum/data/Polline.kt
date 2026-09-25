package io.github.noximiliencoxen.caelum.data

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Il polline, nelle tre famiglie che interessano a chi ne soffre.
 *
 * I nomi sono quelli che si usano parlando, non quelli dei botanici, e [cosa]
 * esiste perche' "Alberi" ed "Erbacce" da soli non dicono quali piante: e' la
 * prima cosa che ci si chiede guardando la sezione.
 */
enum class TipoPolline(val nome: String, val cosa: String) {
    ERBA("Erba", "Graminacee: i prati e le erbe dei campi. Tarda primavera ed estate."),
    ALBERI("Alberi", "Betulla, ontano e olivo. Dalla fine dell'inverno alla primavera."),
    ERBACCE("Erbacce", "Ambrosia e artemisia, erbe selvatiche di fine estate e autunno."),
}

/**
 * Le sei specie che Open-Meteo serve (modello CAMS, solo Europa), ognuna con
 * le sue soglie.
 *
 * **Le soglie non sono le stesse per tutte**, e non per pignoleria: dieci
 * granuli di graminacee al metro cubo sono una giornata qualunque, dieci di
 * ambrosia bastano a far stare male chi e' allergico. Le prime tre sono le
 * classi della rete italiana di monitoraggio (POLLnet: assente, bassa, media,
 * alta) per la famiglia di ogni specie - Betulacee per betulla e ontano,
 * Oleacee per l'olivo, Graminacee, Composite per ambrosia e artemisia. **La
 * quarta, "molto alto", e' nostra**: quattro volte la soglia dell'alta, per
 * avere la stessa scala da 0 a 4 che si usa altrove. Sono indicative: si
 * aggiornano qui, e `PollineTest` dice cosa ci si aspetta.
 *
 * [soglie] sono i limiti inferiori, in granuli al metro cubo, dei livelli da 1
 * a 4.
 */
enum class SpeciePolline(val campo: String, val tipo: TipoPolline, internal val soglie: DoubleArray) {
    ONTANO("alder_pollen", TipoPolline.ALBERI, doubleArrayOf(0.1, 16.0, 50.0, 200.0)),
    BETULLA("birch_pollen", TipoPolline.ALBERI, doubleArrayOf(0.1, 16.0, 50.0, 200.0)),
    OLIVO("olive_pollen", TipoPolline.ALBERI, doubleArrayOf(0.1, 5.0, 25.0, 100.0)),
    GRAMINACEE("grass_pollen", TipoPolline.ERBA, doubleArrayOf(0.1, 10.0, 30.0, 120.0)),
    ARTEMISIA("mugwort_pollen", TipoPolline.ERBACCE, doubleArrayOf(0.1, 5.0, 25.0, 100.0)),
    AMBROSIA("ragweed_pollen", TipoPolline.ERBACCE, doubleArrayOf(0.1, 5.0, 25.0, 100.0)),
    ;

    /** Il livello da 0 a 4 per [granuli] al metro cubo. */
    fun livello(granuli: Double): Int = soglie.count { granuli >= it }

    companion object {
        /** I nomi dei campi, per la richiesta: gli stessi dell'API. */
        val CAMPI: String = entries.joinToString(",") { it.campo }
    }
}

/** Come si dice un livello, da 0 a 4. */
fun nomeLivelloPolline(livello: Int): String = when (livello) {
    0 -> "Assente"
    1 -> "Ridotto"
    2 -> "Moderato"
    3 -> "Alto"
    else -> "Molto alto"
}

/**
 * Un giorno di polline: per ogni famiglia, il livello peggiore dell'ora
 * peggiore.
 *
 * **Il massimo, non la media**, come per l'aria: chi e' allergico esce a
 * un'ora precisa, e una media di giornata nasconderebbe proprio il picco del
 * pomeriggio. Una famiglia manca da [livelli] quando nessuna delle sue specie
 * ha un valore quel giorno - fuori dall'Europa, o un campo che l'API non manda.
 */
data class GiornoPolline(val giorno: LocalDate, val livelli: Map<TipoPolline, Int>) {
    /** Il livello della famiglia peggiore: e' quello che decide le particelle nel cielo. */
    val massimo: Int get() = livelli.values.maxOrNull() ?: 0
}

object Polline {
    /**
     * I giorni, dalle ore dell'API: al piu' [quanti], nell'ordine in cui
     * arrivano. I giorni senza nessun valore si saltano; se non ne resta
     * nessuno la lista e' vuota, e la sezione non si mostra.
     */
    fun giorni(
        tempi: List<String>,
        valori: Map<SpeciePolline, List<Double?>>,
        quanti: Int = 3,
    ): List<GiornoPolline> {
        val date = tempi.map { t -> runCatching { LocalDateTime.parse(t).toLocalDate() }.getOrNull() }
        return date.filterNotNull().distinct().mapNotNull { giorno ->
            val livelli = TipoPolline.entries.mapNotNull { tipo ->
                val specie = SpeciePolline.entries.filter { it.tipo == tipo }
                val perSpecie = specie.mapNotNull { sp ->
                    val serie = valori[sp] ?: return@mapNotNull null
                    date.indices
                        .filter { date[it] == giorno }
                        .mapNotNull { serie.getOrNull(it) }
                        .maxOrNull()
                        ?.let(sp::livello)
                }
                perSpecie.maxOrNull()?.let { tipo to it }
            }.toMap()
            if (livelli.isEmpty()) null else GiornoPolline(giorno, livelli)
        }.take(quanti)
    }
}
