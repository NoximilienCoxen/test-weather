package com.caelumweather.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * In testa all'app ci va il nome del posto, non "POSIZIONE": l'utente l'ha
 * visto in mano il 26 novembre 2026, quando il geocoder del telefono non ha
 * risposto. Il ripiego resta solo quando non c'e' davvero nessun nome.
 */
class NomeDelPostoTest {

    private val casa = Place("Forlì", "Emilia-Romagna", "Italia", 44.2226, 12.0407)

    @Test
    fun `il comune, poi la provincia, poi la regione`() {
        assertEquals("Forlì", nomeDelPosto("Forlì", "Provincia di Forlì-Cesena", "Emilia-Romagna"))
        assertEquals("Provincia di Forlì-Cesena", nomeDelPosto(null, "Provincia di Forlì-Cesena", "Emilia-Romagna"))
        assertEquals("Emilia-Romagna", nomeDelPosto("  ", null, "Emilia-Romagna"))
        assertEquals(DeviceLocation.FALLBACK_NAME, nomeDelPosto(null, "", null))
    }

    @Test
    fun `senza nome, vicino a un posto con nome, si tiene il nome`() {
        val senzaNome = Place(DeviceLocation.FALLBACK_NAME, latitude = 44.2300, longitude = 12.0500)
        val r = conNomeTenuto(senzaNome, casa)
        assertEquals("Forlì", r.name)
        assertEquals("Emilia-Romagna", r.admin)
        // Le coordinate sono quelle nuove: la previsione e' per dove si e'.
        assertEquals(44.2300, r.latitude, 0.0)
    }

    @Test
    fun `lontano, o con un nome vero, non si tocca niente`() {
        val bologna = Place(DeviceLocation.FALLBACK_NAME, latitude = 44.4949, longitude = 11.3426)
        assertEquals(DeviceLocation.FALLBACK_NAME, conNomeTenuto(bologna, casa).name)
        val cesena = Place("Cesena", latitude = 44.1391, longitude = 12.2464)
        assertEquals("Cesena", conNomeTenuto(cesena, casa).name)
        val senzaNome = Place(DeviceLocation.FALLBACK_NAME, latitude = 44.2226, longitude = 12.0407)
        assertEquals(DeviceLocation.FALLBACK_NAME, conNomeTenuto(senzaNome, null).name)
    }

    @Test
    fun `la distanza e' in chilometri veri`() {
        // In linea d'aria (la strada ne fa 76).
        assertEquals(63.0, distanzaKm(44.2226, 12.0407, 44.4949, 11.3426), 3.0)
    }
}
