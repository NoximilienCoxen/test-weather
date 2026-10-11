package io.github.noximiliencoxen.caelum.wear

import io.github.noximiliencoxen.caelum.data.CurrentWeather
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.ui.theme.CONTRAST_AA
import io.github.noximiliencoxen.caelum.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Gli inchiostri dell'orologio, provati su tutti i cieli di una giornata.
 *
 * Sull'orologio la pagina Adesso scrive sul cielo nudo, dalla cima
 * all'orizzonte, e il cielo cambia con l'ora e col tempo: un colore scelto a
 * occhio a mezzogiorno sparisce al tramonto. Qui si prova ogni quarto d'ora di
 * un giorno intero, con il cielo aperto, chiuso, la pioggia, la neve e il
 * temporale.
 */
class CieloOrologioTest {

    private val oggi = LocalDate.of(2026, 10, 11)

    private fun previsione(codice: Int, nuvole: Int, isDay: Boolean) = Forecast(
        current = CurrentWeather(temperature = 15.0, weatherCode = codice, isDay = isDay),
        days = listOf(
            DayForecast(
                date = oggi,
                label = "OGGI",
                sunrise = oggi.atTime(7, 15),
                sunset = oggi.atTime(18, 30),
            ),
        ),
        allHours = (0..23).map { HourForecast(time = oggi.atTime(it, 0), weatherCode = codice, cloudCover = nuvole) },
    )

    private val cieli: List<Pair<String, CieloOrologio>> = buildList {
        val tempi = listOf(0 to 5, 2 to 50, 3 to 100, 61 to 100, 73 to 100, 95 to 100)
        for ((codice, nuvole) in tempi) {
            for (quarto in 0 until 24 * 4) {
                val adesso: LocalDateTime = oggi.atStartOfDay().plusMinutes(quarto * 15L)
                val giorno = adesso.hour in 7..18
                add("codice $codice alle $adesso" to cieloOrologio(previsione(codice, nuvole, giorno), adesso))
            }
        }
        add("senza previsione" to cieloOrologio(null, oggi.atTime(12, 0)))
    }

    private fun almeno(nome: String, rapporto: Float, soglia: Float) =
        assertTrue("$nome: $rapporto", rapporto >= soglia - 0.05f)

    @Test
    fun `il testo sul cielo si legge su tutta la fascia che attraversa`() {
        cieli.forEach { (nome, c) ->
            c.sottoIlTesto.forEachIndexed { i, fondo ->
                almeno("ink al campione $i, $nome", c.ink.contrastRatio(fondo), CONTRAST_AA)
                almeno("inkSoft al campione $i, $nome", c.inkSoft.contrastRatio(fondo), CONTRAST_AA)
            }
        }
    }

    @Test
    fun `di giorno e di notte pieni il velo non c'e'`() {
        val mezzogiorno = cieloOrologio(previsione(0, 5, isDay = true), oggi.atTime(13, 0))
        val mezzanotte = cieloOrologio(previsione(0, 5, isDay = false), oggi.atTime(0, 0))
        assertEquals(0f, mezzogiorno.velo.alpha, 0f)
        assertEquals(0f, mezzanotte.velo.alpha, 0f)
    }

    @Test
    fun `il testo delle righe si legge sulla pastiglia`() {
        cieli.forEach { (nome, c) ->
            val fondo = c.palette.panelSolido
            almeno("inkPanel, $nome", c.inkPanel.contrastRatio(fondo), CONTRAST_AA)
            almeno("inkPanelSoft, $nome", c.inkPanelSoft.contrastRatio(fondo), CONTRAST_AA)
            almeno("acqua, $nome", c.acqua.contrastRatio(fondo), CONTRAST_AA)
        }
    }

    @Test
    fun `la pastiglia della pioggia si legge`() {
        cieli.forEach { (nome, c) ->
            almeno("pioggia, $nome", c.inkAccento.contrastRatio(c.palette.accent), CONTRAST_AA)
        }
    }
}
