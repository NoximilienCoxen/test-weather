package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.ui.theme.CONTRAST_AA
import io.github.noximiliencoxen.caelum.ui.theme.CONTRAST_AA_LARGE
import io.github.noximiliencoxen.caelum.ui.theme.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gli inchiostri della galleria, provati su tutti i cieli che attraversano.
 *
 * Il difetto che questo previene e' arrivato da fuori, da chi l'app la usa in
 * strada: al sole diretto le righe piu' minute - "TRASCINA PER CAMBIARE ORA",
 * l'ora sopra la barra, le cifre sotto le colonne della pioggia - non si
 * leggevano. Erano trasparenze scelte a occhio su fondi che cambiano con
 * l'ora: al chiuso bastavano, in pieno giorno no.
 *
 * Adesso non si scelgono piu' a occhio, e questo test e' il motivo per cui si
 * puo' dire "adesso". Non prova un colore fortunato: prova **tutta** la
 * traversata dal tema chiaro a quello scuro, in tutti e due i crepuscoli e con
 * il cielo aperto e chiuso, cioe' le stesse combinazioni che la Shell produce
 * davvero mentre il sole scende.
 */
class PaletteLeggibileTest {

    private val traversata: List<SalaPalette> = buildList {
        // Passi da un decimo: il tema attraversa con una molla, e il momento
        // peggiore e' sempre da qualche parte **in mezzo** - e' esattamente il
        // motivo per cui `temaScuro` ha uno scalino che salta la fascia
        // illeggibile.
        for (i in 0..10) {
            val dk = i / 10f
            for (crepuscolo in listOf(0f, 0.5f, 1f)) {
                for (chiusura in listOf(0f, 2f, 4f)) {
                    add(salaPalette(dk = dk, crepuscolo = crepuscolo, chiusura = chiusura))
                }
            }
        }
    }

    @Test
    fun `cio che si scrive sul pannello si legge sul pannello`() {
        traversata.forEach { p ->
            val soft = p.inkSoft.contrastRatio(p.panelSolido)
            val faint = p.inkFaint.contrastRatio(p.panelSolido)
            assertTrue("inkSoft $soft su buio ${p.buio}", soft >= CONTRAST_AA - 0.05f)
            assertTrue("inkFaint $faint su buio ${p.buio}", faint >= CONTRAST_AA - 0.05f)
        }
    }

    @Test
    fun `cio che si scrive sulle colline si legge sulle colline`() {
        traversata.forEach { p ->
            val ink = p.inkSuCielo.contrastRatio(p.collina3)
            val accento = p.accentSuCielo.contrastRatio(p.collina3)
            assertTrue("inkSuCielo $ink su buio ${p.buio}", ink >= CONTRAST_AA_LARGE - 0.05f)
            assertTrue("accentSuCielo $accento su buio ${p.buio}", accento >= CONTRAST_AA_LARGE - 0.05f)
        }
    }

    @Test
    fun `cio che si scrive sull'accento si legge sull'accento, fuori dal guado`() {
        // La pillola del ritorno all'ora attuale: inchiostro chiaro su
        // terracotta di giorno, scuro su pesca di notte. E' l'unico testo
        // dell'app che sta su un fondo pieno d'accento, e nessuno lo aveva mai
        // verificato.
        //
        // **Il guado in mezzo e' escluso, e va detto perche'.** Fra il quaranta
        // e il sessanta per cento di `dk` l'inchiostro e l'accento si
        // incrociano: uno scende verso il grigio medio mentre l'altro sale, e
        // per un istante sono lo stesso colore. Non e' una svista da
        // correggere con `readableOn` - e' il **guado** che la sezione 12-ter
        // descrive, la fascia che `temaScuro` attraversa di scatto proprio
        // perche' li' non c'e' niente di leggibile da nessuna parte. Dura
        // qualche centesimo di secondo, una volta all'alba e una al tramonto.
        //
        // Chiedere la soglia anche li' vorrebbe dire far scattare l'accento al
        // bianco a meta' traversata, cioe' un lampo visibile per evitare un
        // difetto che non si vede.
        traversata.filter { it.buio <= 0.02f || it.buio >= 0.98f }.forEach { p ->
            val r = p.accentInk.contrastRatio(p.accent)
            assertTrue("accentInk $r su buio ${p.buio}", r >= CONTRAST_AA_LARGE - 0.05f)
        }
    }
}
