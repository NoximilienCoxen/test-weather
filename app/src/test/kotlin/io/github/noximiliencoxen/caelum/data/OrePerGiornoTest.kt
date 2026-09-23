package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Le ore raccolte per giornata: stessi risultati, senza riscandire la settimana.
 *
 * [Forecast.hoursOf] e [Forecast.hourOn] setacciavano tutte e centosessantotto
 * le ore a ogni chiamata, e le chiamano le schermate **una dozzina di volte per
 * fotogramma**. Adesso la previsione raccoglie le ore per data una volta sola.
 *
 * Questa prova tiene ferme due cose diverse, e la seconda e' quella per cui il
 * cambio e' stato fatto:
 *
 * 1. **le risposte sono identiche a quelle del setaccio**, ordine compreso -
 *    un grafico disegna le ore nell'ordine in cui le riceve;
 * 2. **la stessa data risponde sempre con la stessa lista**. Era il difetto
 *    vero: una lista nuova a ogni chiamata rendeva inutile il `remember` della
 *    barra delle ore, che si ritrovava una chiave diversa per riferimento
 *    anche quando il contenuto era identico e finiva per confrontarne
 *    ventiquattro elemento per elemento solo per decidere di non fare niente.
 */
class OrePerGiornoTest {

    private val primoGiorno: LocalDate = LocalDate.of(2026, 9, 4)

    /** Sette giorni di ore intere, come li manda l'API. */
    private val settimana: List<HourForecast> = (0 until 24 * 7).map { i ->
        HourForecast(
            time = LocalDateTime.of(primoGiorno, LocalTime.MIDNIGHT).plusHours(i.toLong()),
            temperature = i.toDouble(),
        )
    }

    private val previsione = Forecast(
        current = CurrentWeather(),
        days = (0 until 7).map { DayForecast(date = primoGiorno.plusDays(it.toLong()), label = "G$it") },
        hours = settimana.take(24),
        allHours = settimana,
        place = Place(name = "Prova", latitude = 0.0, longitude = 0.0),
    )

    /** Il setaccio di prima, tenuto qui come metro di paragone. */
    private fun setacciando(date: LocalDate): List<HourForecast> =
        settimana.filter { it.time.toLocalDate() == date }

    @Test
    fun `ogni giorno risponde con le stesse ore del setaccio`() {
        (0 until 7).forEach { g ->
            val date = primoGiorno.plusDays(g.toLong())
            assertEquals("giorno $g", setacciando(date), previsione.hoursOf(date))
            assertEquals("giorno $g", 24, previsione.hoursOf(date).size)
        }
    }

    @Test
    fun `un giorno che non c'e' risponde vuoto`() {
        assertTrue(previsione.hoursOf(primoGiorno.minusDays(1)).isEmpty())
        assertTrue(previsione.hoursOf(primoGiorno.plusDays(30)).isEmpty())
        assertNull(previsione.hourOn(primoGiorno.plusDays(30), 12))
    }

    @Test
    fun `l'ora di un altro giorno e' quella di quel giorno`() {
        (0 until 7).forEach { g ->
            val date = primoGiorno.plusDays(g.toLong())
            (0 until 24).forEach { ora ->
                val letta = previsione.hourOn(date, ora)
                assertEquals("giorno $g ora $ora", date, letta?.time?.toLocalDate())
                assertEquals("giorno $g ora $ora", ora, letta?.time?.hour)
                // Le temperature crescono con l'indice: dice anche **quale**
                // delle centosessantotto e' stata pescata, non solo che l'ora
                // e la data tornano.
                val atteso: Double? = (g * 24 + ora).toDouble()
                assertEquals("giorno $g ora $ora", atteso, letta?.temperature)
            }
        }
    }

    @Test
    fun `la stessa data risponde sempre con la stessa lista`() {
        val date = primoGiorno.plusDays(3)
        assertSame(previsione.hoursOf(date), previsione.hoursOf(date))
    }

    /**
     * La raccolta sta fuori dal costruttore, quindi fuori da `equals`.
     *
     * Conta perche' la previsione e' una chiave: sta dentro `UiState`, che
     * viene confrontato a ogni emissione, e finisce nelle chiavi dei `remember`
     * delle schermate. Se la mappa entrasse nell'uguaglianza, due previsioni
     * identiche diventerebbero diverse per il solo fatto che una e' stata
     * letta e l'altra no.
     */
    @Test
    fun `leggere le ore non cambia l'uguaglianza fra previsioni`() {
        val gemella = previsione.copy()
        previsione.hoursOf(primoGiorno)
        assertEquals(previsione, gemella)
        assertEquals(previsione.hashCode(), gemella.hashCode())
    }
}
