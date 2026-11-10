package io.github.noximiliencoxen.caelumweather.widget.paint

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import io.github.noximiliencoxen.caelumweather.data.AirQuality
import io.github.noximiliencoxen.caelumweather.data.AirScale
import io.github.noximiliencoxen.caelumweather.data.CurrentWeather
import io.github.noximiliencoxen.caelumweather.data.DayForecast
import io.github.noximiliencoxen.caelumweather.data.Forecast
import io.github.noximiliencoxen.caelumweather.data.HourForecast
import io.github.noximiliencoxen.caelumweather.data.MoonSegment
import io.github.noximiliencoxen.caelumweather.data.Place
import io.github.noximiliencoxen.caelumweather.lingua.Lingua
import io.github.noximiliencoxen.caelumweather.lingua.Lingue
import io.github.noximiliencoxen.caelumweather.lingua.tr
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos

/**
 * Le anteprime dei widget per il selettore del lanciatore.
 *
 * **Sono disegnate dallo stesso codice che disegna i widget veri**, con una
 * giornata qualunque - ventun gradi, qualche nuvola - invece della bufera a
 * meno dodici che serve a [WidgetOverflowTest] per stanare le scritte lunghe.
 * Un'anteprima fatta a mano in un programma di disegno invecchia al primo
 * ritocco del widget; questa si rifa' a ogni giro di CI.
 *
 * Non entrano nell'app da sole: la CI le pubblica in `widget-renders` come
 * `anteprima-*.png`, e da li' si copiano in `res/drawable-nodpi` (italiano) e
 * `res/drawable-en-nodpi` (inglese) quando un widget cambia faccia. Le
 * dichiara `android:previewImage` nei sei `*_widget_info.xml`.
 *
 * Non asserisce niente: e' una macchina fotografica, non una prova.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnteprimeWidgetTest {

    @After
    fun comePrima() {
        Lingue.forzata = null
    }

    @Test
    fun `le sei anteprime, nelle due lingue`() {
        listOf(Lingua.ITALIANO to "it", Lingua.INGLESE to "en").forEach { (lingua, sigla) ->
            Lingue.forzata = lingua
            val posto = Place(name = "BOLOGNA", latitude = 44.49, longitude = 11.34)
            val previsione = previsione()

            scatta("anteprima-meteo-$sigla", 150f, 150f, Cut.PICCOLO) { currentArt(it, posto, previsione, type, ink) }
            scatta("anteprima-aria-$sigla", 150f, 150f, Cut.PICCOLO) {
                airArt(posto.name, AirQuality(28, AirScale.EUROPEA, null, null), type, ink)
            }
            scatta("anteprima-luna-$sigla", 150f, 150f, Cut.PICCOLO) {
                moonArt(3 / 8f, 0.78f, MoonSegment.GIBBOSA_CRESCENTE.label, type, ink)
            }
            scatta("anteprima-settimana-$sigla", 340f, 170f, Cut.MEDIO) { weekArt(it, posto.name, previsione.days, type, ink) }
            scatta("anteprima-ore-$sigla", 340f, 170f, Cut.MEDIO) { hoursArt(it, posto.name, previsione.hours, type, ink) }
            scatta("anteprima-mini-$sigla", 70f, 70f, Cut.PICCOLO) { miniArt(previsione, type, ink) }
        }
    }

    private val type by lazy { WidgetType(RuntimeEnvironment.getApplication()) }

    // Il tema scuro (res/values-night/colors.xml): nel selettore sta bene su
    // qualunque fondo, e i colori delle figurette ci risaltano di piu'.
    private val ink = WidgetInk(
        background = Color(0xFF000000).toArgb(),
        primary = Color(0xFFFFFFFF),
        secondary = Color(0xFF8A8A8E),
        night = true,
    )

    private fun scatta(nome: String, wDp: Float, hDp: Float, cut: Cut, corpo: DrawScope.(Frame) -> Unit) {
        // Tre e non due: il selettore le ingrandisce, e su uno schermo denso
        // un'anteprima a due si vede sgranata.
        val scala = 3f
        val frame = Frame((wDp * scala).toInt(), (hDp * scala).toInt(), scala, cut, 20f)
        val bitmap = WidgetCanvas.paint(frame, ink.background) { corpo(frame) }
        File("build/widget-renders").apply { mkdirs() }.resolve("$nome.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun previsione(): Forecast {
        val oggi = LocalDate.of(2026, 5, 14)
        val sigle = listOf(
            tr("OGGI", "TODAY"), tr("VEN", "FRI"), tr("SAB", "SAT"), tr("DOM", "SUN"),
            tr("LUN", "MON"), tr("MAR", "TUE"), tr("MER", "WED"),
        )
        val codici = listOf(2, 0, 1, 61, 3, 0, 2)
        val massime = listOf(22.0, 24.0, 23.0, 18.0, 19.0, 23.0, 25.0)
        val minime = listOf(13.0, 14.0, 15.0, 12.0, 11.0, 12.0, 14.0)
        return Forecast(
            current = CurrentWeather(temperature = 21.0, weatherCode = 2, isDay = true),
            days = sigle.mapIndexed { i, sigla ->
                DayForecast(oggi.plusDays(i.toLong()), sigla, codici[i], tempMax = massime[i], tempMin = minime[i])
            },
            // Una giornata vera: minima all'alba, massima alle tre.
            hours = (0 until 24).map { h ->
                HourForecast(
                    time = LocalDateTime.of(2026, 5, 14, h, 0),
                    temperature = 17.5 - 4.5 * cos((h - 3) / 24.0 * 2 * PI),
                    weatherCode = if (h in 10..16) 2 else if (h in 7..19) 1 else 0,
                    isDay = h in 6..20,
                )
            },
        )
    }
}
