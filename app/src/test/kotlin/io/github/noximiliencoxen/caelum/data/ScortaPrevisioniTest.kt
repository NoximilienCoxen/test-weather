package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * La scorta su disco e la previsione conservata riportata a oggi.
 *
 * E' quella che l'app mostra quando apre senza rete, e l'unico modo in cui puo'
 * sbagliare in silenzio e' mostrare ieri come oggi - o far partire le ore da
 * un punto diverso dalla mezzanotte, su cui l'app conta l'ora scelta.
 */
class ScortaPrevisioniTest {

    @get:Rule
    val cartella = TemporaryFolder()

    private val start = LocalDate.of(2026, 9, 20)

    private val saved = Forecast(
        current = CurrentWeather(temperature = 10.0, weatherCode = 0),
        days = (0 until 8).map { DayForecast(start.plusDays(it.toLong()), "G$it", tempMax = 20.0 + it) },
        allHours = (0 until 8 * 24).map { h ->
            HourForecast(start.atStartOfDay().plusHours(h.toLong()), temperature = h.toDouble(), weatherCode = h % 4)
        },
    )

    @Test
    fun `i giorni passati escono e oggi diventa il primo`() {
        val oggi = saved.riportataAOggi(LocalDateTime.of(2026, 9, 23, 8, 30))
        assertNotNull(oggi)
        assertEquals(LocalDate.of(2026, 9, 23), oggi!!.days.first().date)
        assertEquals(5, oggi.days.size)
    }

    @Test
    fun `le ore ripartono dalla mezzanotte di oggi, non da adesso`() {
        // E' la differenza con `agedTo` del widget: l'app conta l'ora scelta
        // come posizione in `hours`, e la posizione zero e' la mezzanotte.
        val oggi = saved.riportataAOggi(LocalDateTime.of(2026, 9, 22, 15, 40))!!
        assertEquals(LocalDateTime.of(2026, 9, 22, 0, 0), oggi.hours.first().time)
        assertEquals(24, oggi.hours.size)
        assertEquals(LocalDateTime.of(2026, 9, 22, 0, 0), oggi.allHours.first().time)
    }

    @Test
    fun `adesso e' l'ora della previsione che cade adesso`() {
        val oggi = saved.riportataAOggi(LocalDateTime.of(2026, 9, 22, 15, 40))!!
        assertEquals((2 * 24 + 15).toDouble(), oggi.current.temperature)
    }

    @Test
    fun `una scorta tutta passata non si mostra`() {
        assertNull(saved.riportataAOggi(LocalDateTime.of(2026, 10, 5, 12, 0)))
    }

    @Test
    fun `quello che si conserva si rilegge`() {
        val file = ScortaPrevisioni.file(cartella.root, Place.FORLI)
        ScortaPrevisioni.conserva(file, "{\"prova\":1}")
        val (testo, _) = ScortaPrevisioni.leggi(file, maxMinuti = 60)!!
        assertEquals("{\"prova\":1}", testo)
    }

    @Test
    fun `oltre l'eta' chiesta la scorta non c'e'`() {
        val file = ScortaPrevisioni.file(cartella.root, Place.FORLI)
        ScortaPrevisioni.conserva(file, "{}")
        file.setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000)
        assertNull(ScortaPrevisioni.leggi(file, maxMinuti = 60))
        assertNotNull(ScortaPrevisioni.leggi(file, maxMinuti = 3 * 60))
    }

    @Test
    fun `il modello automatico tiene il nome che il widget usava gia'`() {
        // Cambiarlo lascerebbe orfane le scorte di chi ha gia' l'app.
        assertEquals("44.2226_12.0407.json", ScortaPrevisioni.file(cartella.root, Place.FORLI).name)
        assertEquals(
            "44.2226_12.0407_icon_2i.json",
            ScortaPrevisioni.file(cartella.root, Place.FORLI, WeatherModel.ICON_2I).name,
        )
    }
}
