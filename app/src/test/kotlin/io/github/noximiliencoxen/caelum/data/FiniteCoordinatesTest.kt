package io.github.noximiliencoxen.caelum.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le coordinate che non sono numeri non arrivano alla rete.
 *
 * Il difetto vero e' costato due passaggi per essere letto: l'emulatore dava
 * una posizione chiamata "MOUNTAIN VIEW" con latitudine e longitudine a `NaN`,
 * quella localita' finiva nell'URL come testo, Open-Meteo rispondeva
 * `{"latitude":NaN,...}` e il lettore JSON si fermava all'offset 15. Nessuno
 * dei tre pezzi diceva "non ho le coordinate".
 *
 * `NaN` non e' un valore che si nota: passa i confronti, si concatena, non
 * lancia niente. Un guardiano scritto e' l'unico modo perche' resti tolto.
 */
class FiniteCoordinatesTest {

    private fun place(latitude: Double, longitude: Double) =
        Place(name = "MOUNTAIN VIEW", latitude = latitude, longitude = longitude)

    @Test
    fun `una localita' vera ha coordinate finite`() {
        assertTrue(Place.FORLI.hasFiniteCoordinates)
        assertTrue(place(0.0, 0.0).hasFiniteCoordinates)
        assertTrue(place(-43.5950, 170.1418).hasFiniteCoordinates)
    }

    @Test
    fun `NaN e infinito non passano, da nessuna delle due parti`() {
        assertFalse(place(Double.NaN, 12.0407).hasFiniteCoordinates)
        assertFalse(place(44.2226, Double.NaN).hasFiniteCoordinates)
        assertFalse(place(Double.NaN, Double.NaN).hasFiniteCoordinates)
        assertFalse(place(Double.POSITIVE_INFINITY, 12.0407).hasFiniteCoordinates)
        assertFalse(place(44.2226, Double.NEGATIVE_INFINITY).hasFiniteCoordinates)
    }

    /**
     * Il guardiano del repository non e' una ripetizione di quello della
     * sorgente: serve a una localita' **gia' salvata** quando la falla c'era
     * ancora. Il fallimento avviene senza aprire una connessione, quindi il
     * test non tocca la rete.
     */
    @Test
    fun `il repository si ferma prima di chiedere`() = runBlocking {
        val outcome = WeatherRepository(place(Double.NaN, Double.NaN)).load()
        assertTrue(outcome.isFailure)
        val failure = outcome.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("MOUNTAIN VIEW"))
    }
}
