package io.github.noximiliencoxen.caelum.ui.scene

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import io.github.noximiliencoxen.caelum.ui.sala.FaseContinua
import io.github.noximiliencoxen.caelum.ui.sala.SalaPhase
import io.github.noximiliencoxen.caelum.ui.sala.cieloStops
import io.github.noximiliencoxen.caelum.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La tinta della scena d'apertura sposta il colore del cielo, e non la luce.
 *
 * E' la promessa che tiene leggibili i testi: il contrasto dipende solo dalla
 * luminanza, e se la luminanza di ogni fermata resta quella della tabella, i
 * conti di `temaScuro` restano veri su qualunque cielo tinto.
 */
class TintaCieloTest {

    private val cieli: List<Color> = SalaPhase.entries.flatMap { fase ->
        listOf(0f, 1f, 2f, 3f, 4f).flatMap { livello ->
            listOf(0f, 1f).flatMap { neve -> cieloStops(FaseContinua(fase, fase, 0f), livello, neve) }
        }
    }

    @Test
    fun `nessuna scena cambia il contrasto del cielo con l'inchiostro`() {
        Scena.entries.forEach { scena ->
            listOf(0f, 0.5f, 1f).forEach { notte ->
                listOf(0.4f, 1f).forEach { forza ->
                    cieli.chunked(3).forEach { stops ->
                        val tinti = tingiCielo(stops, scena, notte, forza)
                        stops.zip(tinti).forEach { (prima, dopo) ->
                            listOf(Color.White, Color.Black).forEach { inchiostro ->
                                val a = prima.contrastRatio(inchiostro)
                                val b = dopo.contrastRatio(inchiostro)
                                assertEquals("$scena notte=$notte forza=$forza su $prima", a, b, a * 0.02f)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `un cielo grigio prende l'azzurro del mare`() {
        val grigio = listOf(Color(0xFF9A9A9A), Color(0xFFB0B0B0), Color(0xFFC8C8C8))
        val tinti = tingiCielo(grigio, Scena.MARE, notte = 0f, forza = 0.4f)
        val b = tinti[0].convert(ColorSpaces.CieLab).blue
        assertTrue("la cima non si e' tinta d'azzurro: b*=$b", b < -3f)
    }

    @Test
    fun `a forza zero il cielo resta quello`() {
        val stops = cieli.take(3)
        assertEquals(stops, tingiCielo(stops, Scena.GRANO, notte = 0f, forza = 0f))
    }
}
