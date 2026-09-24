package io.github.noximiliencoxen.caelum.ui.sala.rooms.luna3d

import io.github.noximiliencoxen.caelum.data.MoonPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La luce della sfera e la fase dell'app devono dire la stessa cosa.
 *
 * La fase viene da `MoonPhase`, che il resto dell'app usa gia'; la sfera la
 * illumina con un sole messo in una direzione. Se la direzione fosse sbagliata
 * - un segno, un quarto di giro - la sfera mostrerebbe una fase diversa da
 * quella scritta sotto, e nessuno se ne accorgerebbe finche' non guarda il
 * cielo.
 */
class GloboTest {

    @Test
    fun `dalla Terra si vede illuminata la frazione della fase`() {
        listOf(0f, 0.1f, 0.25f, 0.4f, 0.5f, 0.6f, 0.75f, 0.9f).forEach { fase ->
            val attesa = MoonPhase.illumination(fase)
            val vista = Globo.frazioneIlluminataVista(fase, 0f, 0f)
            assertEquals("fase $fase", attesa, vista, 0.02f)
        }
    }

    @Test
    fun `al primo quarto e' accesa la destra, all'ultimo la sinistra`() {
        val (primoX, _, _) = Globo.direzioneSole(0.25f)
        val (ultimoX, _, _) = Globo.direzioneSole(0.75f)
        assertTrue(primoX > 0.99f)
        assertTrue(ultimoX < -0.99f)
    }

    @Test
    fun `girandole intorno al novilunio si trova la faccia piena`() {
        assertEquals(0f, Globo.frazioneIlluminataVista(0f, 0f, 0f), 0.01f)
        assertEquals(1f, Globo.frazioneIlluminataVista(0f, 180f, 0f), 0.01f)
    }

    @Test
    fun `di lato al plenilunio se ne vede meta'`() {
        assertEquals(0.5f, Globo.frazioneIlluminataVista(0.5f, 90f, 0f), 0.02f)
    }

    @Test
    fun `la parte in ombra non e' nera ma nemmeno accesa`() {
        val globo = Globo()
        val luce = FloatArray(globo.vertici)
        globo.luce(0f, luce)
        // Al novilunio la faccia verso di noi e' tutta in ombra: solo luce cinerea.
        val versoDiNoi = (0 until globo.vertici).filter { globo.z[it] < -0.5f }
        assertTrue(versoDiNoi.all { luce[it] > 0f && luce[it] < Globo.LUCE_CINEREA * 1.2f })
    }

    @Test
    fun `i mari sono piu' scuri degli altipiani`() {
        val globo = Globo()
        val chiari = (0 until globo.vertici).count { globo.albedo[it] > 0.9f }
        val scuri = (0 until globo.vertici).count { globo.albedo[it] < 0.7f }
        assertTrue("mancano i mari", scuri > globo.vertici / 50)
        assertTrue("manca il resto", chiari > scuri)
    }
}
