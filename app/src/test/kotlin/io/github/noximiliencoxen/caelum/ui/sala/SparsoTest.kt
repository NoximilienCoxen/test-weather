package io.github.noximiliencoxen.caelum.ui.sala

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tabelle del caso sono **gli stessi numeri** che si calcolavano a mano.
 *
 * `sparso(i, sale)` e' un seno, un moltiplicatore grande e la parte
 * frazionaria: dipende solo dall'indice, quindi a ogni fotogramma ricalcolava
 * millesettecento volte dei valori che non cambiano mai - cinque per ognuna
 * delle centosessanta stelle, sei per ognuna delle centocinquantaquattro
 * gocce. Adesso si calcolano una volta e si leggono.
 *
 * **La prova deve essere a livello di bit, non "a occhio".** Il vincolo di
 * questo giro di pulizia e' che il cielo resti *identico*, e un `Float`
 * ricalcolato in modo appena diverso - una parentesi spostata, un
 * `Double` di troppo nell'inizializzazione - sposta una stella di una frazione
 * di pixel senza che nessun confronto approssimato se ne accorga. Per questo si
 * confrontano i [Float.toRawBits], che sono uguali o non lo sono.
 *
 * E' anche l'unico controllo che copre `t > 0`: gli scatti della cattura
 * congelano l'orologio a zero, quindi non vedrebbero mai una tabella letta
 * fuori indice dopo qualche secondo.
 */
class SparsoTest {

    private fun stessoBit(dove: String, atteso: Float, letto: Float) {
        assertEquals(dove, atteso.toRawBits(), letto.toRawBits())
    }

    @Test
    fun `le stelle stanno dove sparso le metteva`() {
        TavolaStelle.tutte.forEach { (tabella, sale) ->
            tabella.forEachIndexed { i, valore ->
                stessoBit("stella $i sale $sale", sparso(i, sale), valore)
            }
        }
    }

    @Test
    fun `il pulviscolo sta dove sparso lo metteva`() {
        TavolaPulviscolo.tutte.forEach { (tabella, sale) ->
            tabella.forEachIndexed { i, valore ->
                stessoBit("granello $i sale $sale", sparso(i, sale), valore)
            }
        }
    }

    @Test
    fun `le corsie stanno dove sparso le metteva`() {
        Corsie.perCorsia.forEach { (tabella, sale) ->
            assertEquals("quante corsie, sale $sale", Corsie.QUANTE, tabella.size)
            tabella.forEachIndexed { i, valore ->
                stessoBit("corsia $i sale $sale", sparso(i, sale), valore)
            }
        }
    }

    @Test
    fun `le gocce di ogni fila stanno dove sparso le metteva`() {
        Corsie.perGoccia.forEach { (tabella, sale) ->
            assertEquals("quante corsie, sale $sale", Corsie.QUANTE, tabella.size)
            tabella.forEachIndexed { i, fila ->
                fila.forEachIndexed { k, valore ->
                    stessoBit(
                        "corsia $i goccia $k sale $sale",
                        sparso(Corsie.semeDi(i, k), sale),
                        valore,
                    )
                }
            }
        }
    }

    /**
     * La fila piu' lunga ci sta dentro.
     *
     * Le tabelle per goccia sono larghe quanto la fila piu' folta fra pioggia,
     * neve e grandine. Se un domani si alzasse uno di quei tre numeri senza
     * allargare le tabelle, il disegno leggerebbe fuori indice - e lo farebbe
     * **solo** col tempo che nevica, cioe' non nella prova che si guarda per
     * prima. Questa riga lo dice subito.
     */
    @Test
    fun `le tabelle sono larghe quanto la fila piu' folta`() {
        val piuFolta = Caduta.entries.maxOf { Corsie.ripetizioni(it) }
        Corsie.perGoccia.forEach { (tabella, sale) ->
            tabella.forEachIndexed { i, fila ->
                assertTrue("corsia $i sale $sale", fila.size >= piuFolta)
            }
        }
    }

    /**
     * Il posto di una corsia e' quello che `indexOf` rispondeva.
     *
     * [Corsie.accesa] cercava il numero di corsia dentro l'elenco dell'ordine,
     * quarantadue volte per fotogramma. Adesso legge l'inversa, calcolata una
     * volta sola: vale se e solo se l'elenco e' davvero una permutazione di
     * tutte le corsie, senza buchi e senza ripetizioni. Se qualcuno lo
     * modifica e ne dimentica una, `indexOf` rispondeva -1 e il `coerceAtLeast`
     * lo nascondeva; qui si vede.
     */
    @Test
    fun `ogni corsia ha un posto solo nell'ordine di accensione`() {
        val posti = (0 until Corsie.QUANTE).map { i ->
            // Il posto si ricava dal numero stesso che [Corsie.accesa] legge:
            // una corsia resta spenta finche' l'intensita' non arriva al suo
            // turno, quindi **quante volte e' spenta** e' il suo posto in fila.
            // Le intensita' si prendono a mezzo passo dai gradini, se no un
            // arrotondamento deciderebbe il confine al posto della formula.
            (0 until Corsie.QUANTE).count { p ->
                Corsie.accesa(i, (p + 0.5f) / Corsie.QUANTE) == 0f
            }
        }
        assertEquals((0 until Corsie.QUANTE).toList(), posti.sorted())
    }
}
