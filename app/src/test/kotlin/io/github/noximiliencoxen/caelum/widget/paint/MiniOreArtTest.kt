package io.github.noximiliencoxen.caelum.widget.paint

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.noximiliencoxen.caelum.data.CurrentWeather
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime

/** I widget da una cella e delle prossime ore: la scelta delle ore e il disegno. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiniOreArtTest {

    private val base = LocalDateTime.of(2026, 9, 24, 0, 0)
    private val ore = (0 until 48).map { h ->
        HourForecast(
            time = base.plusHours(h.toLong()),
            temperature = 14.0 + 8 * kotlin.math.sin((h - 9) / 24.0 * 2 * Math.PI),
            weatherCode = when (h % 24) { in 13..15 -> 61; 16 -> 95; in 10..12 -> 2; else -> 0 },
            precipProbability = when (h % 24) { in 13..16 -> 70; 12 -> 20; else -> 0 },
            isDay = h % 24 in 7..19,
        )
    }
    private val previsione = Forecast(
        current = CurrentWeather(temperature = 21.4, weatherCode = 2, isDay = true),
        days = emptyList(),
        hours = ore.take(24),
        allHours = ore,
    )

    @Test
    fun `le prossime ore partono dall'ora in corso`() {
        val scelte = prossimeOre(previsione, LocalDateTime.of(2026, 9, 24, 10, 40))
        assertEquals(10, scelte.first().time.hour)
        assertEquals(38, scelte.size)
    }

    @Test
    fun `senza previsione nessuna ora`() {
        assertTrue(prossimeOre(null).isEmpty())
    }

    @Test
    fun `una cella, chiaro`() = render("mini-1x1-chiaro", 70f, 70f, Cut.PICCOLO, light()) { miniArt(previsione, it, light()) }

    @Test
    fun `una cella, scuro e senza dati`() = render("mini-1x1-vuoto", 70f, 70f, Cut.PICCOLO, dark()) { miniArt(null, it, dark()) }

    @Test
    fun `prossime ore, chiaro`() = render("ore-4x2-chiaro", 300f, 150f, Cut.MEDIO, light()) {
        hoursArt(Frame(600, 300, 2f, Cut.MEDIO, 20f), "FORLÌ", prossimeOre(previsione, base.plusHours(11)), it, light())
    }

    @Test
    fun `prossime ore, scuro di sera`() = render("ore-4x2-scuro", 300f, 150f, Cut.MEDIO, dark()) {
        hoursArt(Frame(600, 300, 2f, Cut.MEDIO, 20f), "SAN GIOVANNI IN PERSICETO", prossimeOre(previsione, base.plusHours(17)), it, dark())
    }

    private fun render(
        name: String,
        wDp: Float,
        hDp: Float,
        cut: Cut,
        ink: WidgetInk,
        body: androidx.compose.ui.graphics.drawscope.DrawScope.(WidgetType) -> Unit,
    ) {
        val scale = 2f
        val frame = Frame((wDp * scale).toInt(), (hDp * scale).toInt(), scale, cut, 20f)
        val type = WidgetType(RuntimeEnvironment.getApplication())
        val bitmap = WidgetCanvas.paint(frame, ink.background) { body(type) }
        var inked = 0
        for (x in 0 until bitmap.width step 3) {
            for (y in 0 until bitmap.height step 3) {
                if (bitmap.getPixel(x, y) != ink.background) inked++
            }
        }
        assertTrue("$name: niente disegnato", inked > 30)
        val out = File("build/widget-renders").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

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
}
