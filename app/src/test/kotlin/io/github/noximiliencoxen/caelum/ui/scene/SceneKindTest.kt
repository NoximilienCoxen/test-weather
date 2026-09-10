package io.github.noximiliencoxen.caelum.ui.scene

import io.github.noximiliencoxen.caelum.data.Wmo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La mappa dal codice meteo alla scena dipinta.
 *
 * E' una tabella, e le tabelle si sbagliano in silenzio: un codice finito nella
 * riga sbagliata non fa saltare niente, fa solo comparire il sole mentre
 * grandina. Qui si controlla contro `Wmo`, che e' la sorgente di verita' sui
 * codici, invece che contro una lista riscritta a mano - una lista a mano
 * ripeterebbe lo stesso errore due volte e lo chiamerebbe conferma.
 */
class SceneKindTest {

    /** Tutti i codici WMO che l'app dichiara di conoscere. */
    private val known = listOf(
        0, 1, 2, 3, 45, 48,
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67,
        71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99,
    )

    @Test
    fun `ogni codice conosciuto ha la sua scena`() {
        known.forEach { code ->
            assertNotNull("codice $code senza scena", sceneOf(code))
        }
    }

    @Test
    fun `la famiglia della scena non contraddice quella del codice`() {
        // Le due classificazioni hanno grane diverse - otto scene contro sei
        // famiglie - ma non devono dire cose opposte: dove `Wmo` vede pioggia
        // la scena non puo' essere il sereno.
        known.forEach { code ->
            val scene = sceneOf(code)
            val expected = when (Wmo.family(code)) {
                Wmo.Family.ASCIUTTO -> setOf(SceneKind.SERENO, SceneKind.POCO_NUVOLOSO)
                Wmo.Family.NUVOLOSO -> setOf(SceneKind.POCO_NUVOLOSO, SceneKind.COPERTO)
                Wmo.Family.NEBBIA -> setOf(SceneKind.NEBBIA)
                Wmo.Family.PIOGGIA -> setOf(SceneKind.PIOGGIA)
                Wmo.Family.NEVE -> setOf(SceneKind.NEVE)
                Wmo.Family.TEMPORALE -> setOf(SceneKind.TEMPORALE, SceneKind.GRANDINE)
            }
            assertTrue(
                "codice $code: ${Wmo.family(code)} ma scena $scene",
                scene in expected,
            )
        }
    }

    @Test
    fun `il sereno e il poco nuvoloso sono due quadri diversi`() {
        // E' la ragione per cui le scene sono otto e non sei: come colore di una
        // tacca sulla barra delle ore sono la stessa cosa, come immagine no.
        assertEquals(SceneKind.SERENO, sceneOf(0))
        assertEquals(SceneKind.POCO_NUVOLOSO, sceneOf(1))
        assertEquals(SceneKind.COPERTO, sceneOf(3))
        assertEquals(Wmo.Family.ASCIUTTO, Wmo.family(0))
        assertEquals(Wmo.Family.ASCIUTTO, Wmo.family(1))
    }

    @Test
    fun `la grandine si stacca dal temporale`() {
        // Condividono la famiglia e non l'immagine.
        assertEquals(SceneKind.TEMPORALE, sceneOf(95))
        assertEquals(SceneKind.GRANDINE, sceneOf(96))
        assertEquals(SceneKind.GRANDINE, sceneOf(99))
    }

    @Test
    fun `senza codice si sta sul sereno, e un codice ignoto non fa saltare niente`() {
        assertEquals(SceneKind.SERENO, sceneOf(null))
        assertEquals(SceneKind.SERENO, sceneOf(12345))
        assertEquals(SceneKind.SERENO, sceneOf(-1))
    }

    @Test
    fun `ogni scena dichiara due file distinti, e con la profondita' in coda`() {
        SceneKind.entries.forEach { scene ->
            assertTrue(scene.painting.startsWith("scene/"))
            assertTrue(scene.depth.startsWith("scene/"))
            assertTrue("la profondita' di $scene non e' marcata", scene.depth.contains("_z."))
            assertTrue(scene.painting != scene.depth)
        }
    }
}
