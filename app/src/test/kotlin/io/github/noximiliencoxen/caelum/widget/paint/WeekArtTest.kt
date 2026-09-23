package io.github.noximiliencoxen.caelum.widget.paint

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.noximiliencoxen.caelum.data.DayForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Il widget della settimana: la scala delle barrette, e il disegno vero.
 *
 * Il disegno si prova con la grafica nativa di Robolectric - la stessa Skia del
 * telefono - e le immagini finiscono in `build/widget-renders`, da dove la CI
 * le pubblica: un widget e' un'immagine, e un'immagine si guarda.
 *
 * `sdk = 34` per la stessa ragione scritta in `ParseFeedTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekArtTest {

    private val week: List<DayForecast> = listOf(
        day(0, "OGGI", 1, 14.2, 23.8),
        day(1, "GIO", 3, 13.0, 21.4),
        day(2, "VEN", 61, 11.6, 17.1),
        day(3, "SAB", 95, 10.2, 15.0),
        day(4, "DOM", 45, 9.4, 16.3),
        day(5, "LUN", 2, 11.0, 20.2),
        day(6, "MAR", 0, 12.8, 24.6),
    )

    // --- La scala --------------------------------------------------------

    @Test
    fun `la scala va dalla minima alla massima della settimana`() {
        val scale = WeekScale.of(week)
        assertNotNull(scale)
        scale!!
        assertEquals(9.4, scale.start, 1e-9)
        assertEquals(24.6, scale.endInclusive, 1e-9)
    }

    @Test
    fun `senza temperature non c'e' scala`() {
        assertNull(WeekScale.of(listOf(DayForecast(LocalDate.now(), "OGGI"))))
        assertNull(WeekScale.of(emptyList()))
    }

    @Test
    fun `il giorno piu' freddo tocca il fondo e il piu' caldo la cima`() {
        val scale = 9.4..24.6
        val coldest = WeekScale.span(9.4, 16.3, scale)!!
        val warmest = WeekScale.span(12.8, 24.6, scale)!!
        assertEquals(0f, coldest.first, 1e-6f)
        assertEquals(1f, warmest.second, 1e-6f)
        assertTrue(coldest.second < warmest.second)
    }

    @Test
    fun `una settimana piatta non divide per zero`() {
        assertEquals(0.5f to 0.5f, WeekScale.span(18.0, 18.0, 18.0..18.0))
    }

    @Test
    fun `minima e massima invertite si rimettono in ordine`() {
        val span = WeekScale.span(20.0, 10.0, 0.0..20.0)!!
        assertEquals(0.5f, span.first, 1e-6f)
        assertEquals(1f, span.second, 1e-6f)
    }

    @Test
    fun `un giorno senza una delle due non ha barretta`() {
        assertNull(WeekScale.span(null, 20.0, 0.0..30.0))
        assertNull(WeekScale.span(10.0, Double.NaN, 0.0..30.0))
    }

    // --- Il disegno ------------------------------------------------------

    @Test
    fun `quattro per due, tema chiaro`() = render("settimana-4x2-chiaro", 300f, 150f, Cut.MEDIO, light(), week)

    @Test
    fun `quattro per due, tema scuro`() = render("settimana-4x2-scuro", 300f, 150f, Cut.MEDIO, dark(), week)

    @Test
    fun `taglio stretto, quattro giorni`() = render("settimana-stretto", 180f, 150f, Cut.PICCOLO, light(), week)

    @Test
    fun `nome lunghissimo e dati mancanti`() = render(
        "settimana-nome-lungo",
        300f,
        150f,
        Cut.MEDIO,
        dark(),
        week.mapIndexed { i, d -> if (i == 2) d.copy(tempMin = null, weatherCode = null) else d },
        place = "SAN GIOVANNI IN PERSICETO DI SOTTO",
    )

    @Test
    fun `senza previsione`() = render("settimana-vuota", 300f, 150f, Cut.MEDIO, light(), emptyList())

    private fun render(
        name: String,
        wDp: Float,
        hDp: Float,
        cut: Cut,
        ink: WidgetInk,
        days: List<DayForecast>,
        place: String = "FORLÌ",
    ) {
        val scale = 2f
        val frame = Frame(
            widthPx = (wDp * scale).toInt(),
            heightPx = (hDp * scale).toInt(),
            scale = scale,
            cut = cut,
            cornerDp = 20f,
        )
        val type = WidgetType(RuntimeEnvironment.getApplication())
        val bitmap = WidgetCanvas.paint(frame, ink.background) {
            weekArt(frame, place, days, type, ink)
        }

        // Qualcosa e' stato disegnato oltre al fondo: una tela tutta di un
        // colore vorrebbe dire che il disegno e' saltato senza dirlo.
        val background = ink.background
        val centre = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        var inked = 0
        for (x in 0 until bitmap.width step 3) {
            for (y in 0 until bitmap.height step 3) {
                if (bitmap.getPixel(x, y) != background) inked++
            }
        }
        assertTrue("$name: niente disegnato (centro ${Integer.toHexString(centre)})", inked > 50)

        val out = File("build/widget-renders").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // Gli stessi valori di res/values/colors.xml e res/values-night/colors.xml.
    private fun light() = WidgetInk(
        background = Color(0xFFFFFFFF).toArgb(),
        primary = Color(0xFF000000),
        secondary = Color(0xFF6C6C70),
        night = false,
    )

    private fun dark() = WidgetInk(
        background = Color(0xFF000000).toArgb(),
        primary = Color(0xFFFFFFFF),
        secondary = Color(0xFF8A8A8E),
        night = true,
    )

    private fun day(offset: Long, label: String, code: Int, min: Double, max: Double) = DayForecast(
        date = LocalDate.of(2026, 9, 24).plusDays(offset),
        label = label,
        weatherCode = code,
        tempMin = min,
        tempMax = max,
    )
}
