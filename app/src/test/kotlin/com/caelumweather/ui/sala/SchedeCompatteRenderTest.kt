package com.caelumweather.ui.sala

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caelumweather.data.HourForecast
import com.caelumweather.data.parseMare
import com.caelumweather.prefs.TempUnit
import com.caelumweather.ui.sala.rooms.GraficoGiornata
import com.caelumweather.ui.sala.rooms.SezioneMare
import com.caelumweather.ui.sala.rooms.SezioneNeve
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDateTime
import kotlin.math.cos

/**
 * Le sezioni aggiunte alle schede (la curva della giornata, il mare con le
 * maree, la neve), disegnate a larghezza di telefono in `build/widget-renders`
 * (`scheda-*.png`). **Hanno un tetto d'altezza**: una scheda che si allunga
 * oltre lo schermo taglia la fila dei giorni in fondo, ed e' successo (gli
 * screenshot della CI di novembre 2026, sala di oggi).
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SchedeCompatteRenderTest {

    private fun risorsa(nome: String) = javaClass.classLoader!!.getResource(nome)!!.readText()

    /** Disegna [contenuto] largo 323 dp (la scheda su un telefono da 411) e ne restituisce l'altezza in dp. */
    private fun disegna(nome: String, contenuto: @Composable () -> Unit): Float {
        val palette = salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0f)
        val attivita = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        attivita.setContent {
            Column(modifier = Modifier.background(palette.panelSolido).padding(24.dp).width(323.dp)) { contenuto() }
        }
        ShadowLooper.idleMainLooper()
        val radice = attivita.window.decorView
        val vista: View = (radice.findViewById<android.view.ViewGroup>(android.R.id.content)).getChildAt(0)
        val bitmap = Bitmap.createBitmap(vista.width.coerceAtLeast(1), vista.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(bitmap))
        File("build/widget-renders").apply { mkdirs() }.resolve("scheda-$nome.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return vista.height / attivita.resources.displayMetrics.density
    }

    @Test
    fun `la curva della giornata resta bassa`() {
        val ore = (0 until 24).map { h ->
            HourForecast(time = LocalDateTime.of(2026, 11, 11, h, 0), temperature = 11.0 - 5.5 * cos((h - 3) / 24.0 * 2 * Math.PI), isDay = h in 7..17)
        }
        val palette = salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0f)
        val alta = disegna("giornata") {
            GraficoGiornata(ore = ore, scelta = 13, adesso = 14, unita = TempUnit.CELSIUS, palette = palette, onScegli = {})
        }
        // 48 di margini; la curva con le ore sotto sta sotto i 130 dp.
        assertTrue("curva alta $alta dp", alta - 48f < 130f)
    }

    @Test
    fun `la luna non si appoggia alla massima quando la massima e' di sera`() {
        // Come Forli' il 12 novembre 2026: piatta la notte, massima alle 22.
        val ore = (0 until 24).map { h ->
            HourForecast(time = LocalDateTime.of(2026, 11, 12, h, 0), temperature = 10.0 + 3.0 * (h / 22.0).coerceAtMost(1.0) * (h / 22.0), isDay = h in 7..16)
        }
        val palette = salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0f)
        disegna("giornata-massima-di-sera") {
            GraficoGiornata(ore = ore, scelta = 13, adesso = 1, unita = TempUnit.CELSIUS, palette = palette, onScegli = {})
        }
    }

    @Test
    fun `il mare con le maree resta corto`() {
        val mare = parseMare(risorsa("mare-venezia-livello.json"))!!
        val ora = mare.ore[mare.ore.size / 3]
        val palette = salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0f)
        val alta = disegna("mare-maree") {
            SezioneMare(ora = ora, unita = TempUnit.CELSIUS, palette = palette, movimento = false, mare = mare)
        }
        assertTrue("mare alto $alta dp", alta - 48f < 330f)
    }

    @Test
    fun `la neve sta in una fila`() {
        // Il file vero delle Dolomiti era di un giorno asciutto: la fila si
        // vede solo con giorni di neve, quindi la previsione e' scritta qui.
        val oggi = java.time.LocalDate.of(2026, 12, 3)
        val cm = listOf(0.0, 4.0, 18.0, 7.0, 0.0, 2.0, 0.0)
        val previsione = com.caelumweather.data.Forecast(
            current = com.caelumweather.data.CurrentWeather(),
            days = cm.mapIndexed { i, c -> com.caelumweather.data.DayForecast(oggi.plusDays(i.toLong()), listOf("OGGI", "VEN", "SAB", "DOM", "LUN", "MAR", "MER")[i], snowfallSum = c) },
            allHours = (0 until 7 * 24).map { h ->
                HourForecast(time = oggi.atStartOfDay().plusHours(h.toLong()), snowfall = if (cm[h / 24] > 0 && h % 24 in 6..12) cm[h / 24] / 7 else 0.0, freezingLevel = 1300.0 + 40 * (h % 24))
            },
            elevation = 1450.0,
        )
        val palette = salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0f)
        val alta = disegna("neve") {
            SezioneNeve(previsione = previsione, zeroTermicoOra = 1450.0, unita = TempUnit.CELSIUS, palette = palette)
        }
        assertTrue("neve alta $alta dp", alta - 48f < 260f)
    }
}
