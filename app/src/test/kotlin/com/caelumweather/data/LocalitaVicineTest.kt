package com.caelumweather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Il controllo chiesto dall'utente: in testa ci va il nome del posto, non
 * "POSIZIONE", anche dove il geocoder del telefono non risponde. Si legge
 * l'elenco vero, quello che va nell'APK.
 */
class LocalitaVicineTest {

    private val elenco by lazy {
        val f = listOf("src/main/assets/localita.tsv.gz", "app/src/main/assets/localita.tsv.gz").map(::File).first { it.exists() }
        f.inputStream().use(LocalitaVicine::leggi)
    }

    @Test
    fun `l'elenco c'e' ed e' quello intero`() {
        assertTrue("localita': ${elenco.size}", elenco.size > 150_000)
    }

    @Test
    fun `davanti a casa si legge il nome del paese, con gli accenti`() {
        assertEquals("Forlì", elenco.piuVicina(44.2226, 12.0407)!!.name)
        assertEquals("Noceto", elenco.piuVicina(44.8100, 10.1780)!!.name)
        val p = elenco.piuVicina(44.8100, 10.1780)!!
        assertEquals("Emilia-Romagna", p.admin)
        // Le coordinate restano quelle del telefono, non del centro del paese.
        assertEquals(44.8100, p.latitude, 0.0)
    }

    @Test
    fun `in tutto il mondo, e oltre l'antimeridiano`() {
        assertNotNull(elenco.piuVicina(43.0618, 141.3545)) // Sapporo
        assertNotNull(elenco.piuVicina(-36.8485, 174.7633)) // Auckland
        assertNotNull(elenco.piuVicina(64.8378, -147.7164)) // Fairbanks
    }

    @Test
    fun `in mezzo al mare non si inventa un paese`() {
        assertNull(elenco.piuVicina(40.0, -40.0)) // Atlantico
        assertNull(elenco.piuVicina(Double.NaN, 10.0))
    }
}
