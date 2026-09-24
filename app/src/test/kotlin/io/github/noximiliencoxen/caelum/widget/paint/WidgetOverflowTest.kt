package io.github.noximiliencoxen.caelum.widget.paint

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import io.github.noximiliencoxen.caelum.data.AirQuality
import io.github.noximiliencoxen.caelum.data.AirScale
import io.github.noximiliencoxen.caelum.data.CurrentWeather
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.MoonSegment
import io.github.noximiliencoxen.caelum.data.Place
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Nessuna scritta esce dal widget, con nessun nome.
 *
 * Il difetto visto sul telefono: con un nome lungo la scritta passava il
 * bordo, e la luna faceva lo stesso con "GIBBOSA CRESCENTE". Qui ogni disegno
 * si fa con i nomi peggiori e nelle forme in cui i lanciatori danno i widget -
 * anche piu' alti che larghi, che e' dove le scritte in proporzione
 * all'altezza non ci stanno piu' in larghezza - e poi si guarda la fascia
 * esterna del margine: li' non deve esserci inchiostro.
 *
 * Le immagini finiscono in `build/widget-renders` come quelle di
 * [WeekArtTest], e la CI le pubblica.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetOverflowTest {

    private val names = listOf(
        "SAN GIOVANNI IN PERSICETO DI SOTTO",
        "LLANFAIRPWLLGWYNGYLLGOGERYCHWYRNDROBWLLLLANTYSILIOGOGOGOCH",
        "FONTEVIVO",
    )

    /** Le forme: 2x2 quadrato e alto, 4x2, 4x4. In dp. */
    private val small = listOf(150f to 150f, 140f to 190f)
    private val wide = 340f to 170f
    private val tall = 340f to 360f

    @Test
    fun `meteo, tutti i tagli`() {
        names.forEach { name ->
            small.forEach { (w, h) -> check("meteo-piccolo-${w.toInt()}x${h.toInt()}-${name.take(8)}", w, h, Cut.PICCOLO) { currentArt(it, place(name), forecast(), type, ink) } }
            check("meteo-largo-${name.take(8)}", wide.first, wide.second, Cut.MEDIO) { currentArt(it, place(name), forecast(), type, ink) }
            check("meteo-alto-${name.take(8)}", tall.first, tall.second, Cut.GRANDE) { currentArt(it, place(name), forecast(), type, ink) }
        }
    }

    @Test
    fun `aria`() {
        names.forEach { name ->
            listOf(10, 30, 50, 70, 90, 120).forEach { index ->
                small.forEach { (w, h) ->
                    check("aria-$index-${w.toInt()}x${h.toInt()}-${name.take(8)}", w, h, Cut.PICCOLO) {
                        airArt(name, AirQuality(index, AirScale.EUROPEA, null, null), type, ink)
                    }
                }
            }
        }
    }

    @Test
    fun `luna, tutte le fasi`() {
        MoonSegment.entries.forEachIndexed { i, segment ->
            small.forEach { (w, h) ->
                check("luna-${segment.name}-${w.toInt()}x${h.toInt()}", w, h, Cut.PICCOLO) {
                    moonArt(i / 8f, 0.5f, segment.label, type, ink)
                }
            }
        }
    }

    @Test
    fun `settimana`() {
        names.forEach { name ->
            check("settimana-${name.take(8)}", wide.first, wide.second, Cut.MEDIO) { weekArt(it, name, forecast().days, type, ink) }
            check("settimana-stretta-${name.take(8)}", 200f, 150f, Cut.PICCOLO) { weekArt(it, name, forecast().days, type, ink) }
        }
    }

    @Test
    fun `da configurare`() {
        listOf("METEO", "ARIA", "SETTIMANA").forEach { title ->
            small.forEach { (w, h) ->
                check("setup-$title-${w.toInt()}x${h.toInt()}", w, h, Cut.PICCOLO) { setupArt(title, type, ink) }
            }
        }
    }

    // ---------------------------------------------------------------------

    private val type by lazy { WidgetType(RuntimeEnvironment.getApplication()) }

    // I valori di res/values-night/colors.xml: il fondo nero rende evidente
    // qualunque cosa ci finisca sopra.
    private val ink = WidgetInk(
        background = Color(0xFF000000).toArgb(),
        primary = Color(0xFFFFFFFF),
        secondary = Color(0xFF8A8A8E),
        night = true,
    )

    private fun check(name: String, wDp: Float, hDp: Float, cut: Cut, body: DrawScope.(Frame) -> Unit) {
        val scale = 2f
        val frame = Frame((wDp * scale).toInt(), (hDp * scale).toInt(), scale, cut, 20f)

        // Il margine del disegno e' di 16dp: una scritta puo' avvicinarsi al
        // bordo, non attraversarlo. Mezzo pixel di tolleranza per gli
        // arrotondamenti della misura.
        val edge = 16f * scale
        val offenders = mutableListOf<String>()
        writtenText = { value, left, right ->
            if (left < edge - 0.5f || right > frame.widthPx - edge + 0.5f) {
                offenders += "\"$value\" da ${left.toInt()} a ${right.toInt()} (larghezza ${frame.widthPx})"
            }
        }
        val bitmap = try {
            WidgetCanvas.paint(frame, ink.background) { body(frame) }
        } finally {
            writtenText = null
        }

        val out = File("build/widget-renders").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        failures += offenders.map { "$name: $it" }
    }

    /**
     * Si raccolgono tutte e si fallisce alla fine: fermarsi alla prima
     * lascerebbe senza immagine - e senza diagnosi - tutte le altre.
     */
    private val failures = mutableListOf<String>()

    @After
    fun nessunaScrittaFuori() {
        // Anche su file, accanto alle immagini: il log della CI mostra solo la
        // riga dell'asserzione, non il messaggio.
        if (failures.isNotEmpty()) {
            File("build/widget-renders").apply { mkdirs() }
                .resolve("overflow-${System.nanoTime()}.txt").writeText(failures.joinToString("\n"))
        }
        assertTrue(failures.joinToString("\n", prefix = "Scritte oltre il bordo:\n"), failures.isEmpty())
    }

    private fun place(name: String) = Place(name = name, latitude = 44.22, longitude = 12.04)

    private fun forecast(): Forecast {
        val today = LocalDate.of(2026, 9, 24)
        val labels = listOf("OGGI", "VEN", "SAB", "DOM", "LUN", "MAR", "MER")
        val codes = listOf(96, 3, 61, 95, 45, 2, 0)
        return Forecast(
            current = CurrentWeather(temperature = -12.4, weatherCode = 99, isDay = true),
            days = labels.mapIndexed { i, label ->
                DayForecast(today.plusDays(i.toLong()), label, codes[i], tempMax = -3.0 + i, tempMin = -14.0 + i)
            },
            hours = (0 until 24).map { h ->
                HourForecast(LocalDateTime.of(2026, 9, 24, h, 0), temperature = -12.0 + h / 3.0, weatherCode = codes[h % 7], isDay = h in 7..19)
            },
        )
    }
}
