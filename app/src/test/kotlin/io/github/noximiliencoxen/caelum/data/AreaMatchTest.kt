package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Se un avviso riguardi o no la localita' mostrata.
 *
 * Questa e' la trappola che sarebbe costata di piu': il feed chiama la regione
 * di Forli' *"Emilia e Romagna"*, Open-Meteo la chiama *"Emilia-Romagna"*, e
 * **nessuna delle due contiene l'altra**. Con un confronto per sottostringa
 * Forli' sarebbe rimasta senza allerte per sempre, senza un errore da nessuna
 * parte e senza niente da guardare in uno scatto.
 *
 * Da qui in poi quel caso ha un guardiano.
 */
class AreaMatchTest {

    private fun entry(area: String?) = FeedEntry(
        id = "x",
        event = "Yellow Thunderstorm Warning",
        severity = "Moderate",
        areaDesc = area,
        onset = null,
        expires = null,
        capUrl = null,
    )

    private fun place(name: String, admin: String?) =
        Place(name = name, admin = admin, country = "IT", latitude = 0.0, longitude = 0.0)

    @Test
    fun `la congiunzione non deve far mancare l'allerta`() {
        val avviso = entry("Emilia e Romagna")
        assertTrue(avviso.matches(place("Forli'", "Emilia-Romagna")))
    }

    @Test
    fun `vale anche al contrario`() {
        val avviso = entry("Emilia-Romagna")
        assertTrue(avviso.matches(place("Forli'", "Emilia e Romagna")))
    }

    @Test
    fun `due regioni diverse non combaciano`() {
        val avviso = entry("Puglia")
        assertFalse(avviso.matches(place("Forli'", "Emilia-Romagna")))
    }

    @Test
    fun `l'area che nomina la citta' vale`() {
        val avviso = entry("Bacini Romagnoli - Forli")
        assertTrue(avviso.matches(place("Forli", null)))
    }

    @Test
    fun `un avviso senza area si tiene`() {
        // Scartare cio' che non si sa collocare significherebbe, per
        // un'allerta, non darla. Il nome dell'area viaggia fino alla schermata
        // e chi guarda decide da se'.
        assertTrue(entry(null).matches(place("Forli'", "Emilia-Romagna")))
    }
}
