package io.github.noximiliencoxen.caelum.ui.sala.rooms.luna3d

import io.github.noximiliencoxen.caelum.data.MoonPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

    /**
     * La carta e' la stessa luce dei vertici, piu' fitta: al primo quarto la
     * meta' destra della faccia verso di noi e' accesa e la sinistra no.
     */
    @Test
    fun `sulla carta il primo quarto e' acceso a destra`() {
        val globo = Globo()
        val larga = 240
        val alta = 120
        val luce = FloatArray(larga * alta)
        val albedo = FloatArray(larga * alta)
        globo.carta(0.25f, larga, alta, luce, albedo)
        var destra = 0
        var sinistra = 0
        for (i in 0 until alta) for (j in 0 until larga) {
            val theta = PI * (i + 0.5) / alta
            val phi = 2.0 * PI * (j + 0.5) / larga
            val px = sin(theta) * sin(phi)
            val pz = -sin(theta) * cos(phi)
            if (pz > -0.3) continue
            val l = luce[i * larga + j]
            if (px > 0.3) { destra++; assertTrue("buio a destra: $l", l > 0.3f) }
            if (px < -0.3) { sinistra++; assertTrue("acceso a sinistra: $l", l < Globo.LUCE_CINEREA * 1.2f) }
        }
        assertTrue(destra > 100 && sinistra > 100)
    }

    @Test
    fun `i crateri piccoli si vedono sulla carta`() {
        val globo = Globo()
        val larga = 1024
        val alta = 512
        val luce = FloatArray(larga * alta)
        val albedo = FloatArray(larga * alta)
        globo.carta(0.5f, larga, alta, luce, albedo)
        // Lungo una riga all'equatore, fra un punto e il successivo l'albedo
        // deve cambiare di colpo da qualche parte: un orlo, una conca. Una
        // luna tutta sfumature non lo farebbe mai.
        val riga = alta / 2
        val salti = (1 until larga).count { j -> kotlin.math.abs(albedo[riga * larga + j] - albedo[riga * larga + j - 1]) > 0.02f }
        assertTrue("nessun dettaglio fine: $salti", salti > 10)
    }

    /**
     * Il cursore che corre rinuncia alla carta a meta': la luce deve
     * fermarsi alla prima riga, e l'albedo - fatta una volta sola - non deve
     * cambiare da una fase all'altra.
     */
    @Test
    fun `la carta si ferma quando le si chiede, e l'albedo resta la stessa`() {
        val globo = Globo()
        val luce = FloatArray(64 * 32)
        var righe = 0
        val finita = globo.carta(0.3f, 64, 32, luce, continua = { righe++ < 3 })
        assertTrue("non si e' fermata", !finita)
        assertEquals(4, righe)

        val prima = FloatArray(64 * 32)
        val dopo = FloatArray(64 * 32)
        globo.carta(0.1f, 64, 32, luce, prima)
        globo.carta(0.6f, 64, 32, luce, dopo)
        assertTrue(prima.contentEquals(dopo))
        assertTrue(globo.albedoCarta(64, 32) === globo.albedoCarta(64, 32))
    }
}
