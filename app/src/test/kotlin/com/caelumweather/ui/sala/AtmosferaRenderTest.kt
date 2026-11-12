package com.caelumweather.ui.sala

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import com.caelumweather.data.HourForecast
import com.caelumweather.data.SkyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDateTime

/**
 * L'aria del cielo (nebbia, vento, caldo, l'ora dopo la pioggia) e il vetro
 * della scheda (brina, gocce), disegnati sul cielo vero di `SalaCielo` in
 * `build/widget-renders/atmosfera-*.png`. Si guardano; la prova controlla che
 * ogni effetto cambi davvero il disegno, e i conti di [atmosferaBersaglio].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AtmosferaRenderTest {

    private fun disegna(nome: String, contenuto: @Composable () -> Unit): Bitmap {
        val attivita = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        attivita.setContent { contenuto() }
        ShadowLooper.idleMainLooper()
        val vista: View = attivita.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(bitmap))
        File("build/widget-renders").apply { mkdirs() }.resolve("atmosfera-$nome.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun cielo(nome: String, atmosfera: Atmosfera, scena: Scena = Scena(sole = 1f, copertura = 0.3f, tempesta = 0f, bagnato = 0f, ghiaccio = 0f, neve = 0f, notte = 0f), sky: SkyState = SkyState.of(0.35f, journey = 0.25f)): Bitmap {
        val palette = salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0.2f)
        val stops = cieloStops(faseContinua(sky), livelloCielo(scena.copertura, scena.tempesta), scena.neve)
        return disegna(nome) {
            CompositionLocalProvider(LocalMovimento provides false) {
                SalaCielo(
                    palette = palette,
                    stops = stops,
                    scena = scena,
                    sky = sky,
                    faseLunare = 0.3f,
                    tempo = { 12.5f },
                    modifier = Modifier.fillMaxSize(),
                    interattivo = false,
                    atmosfera = atmosfera,
                )
            }
        }
    }

    private fun diversi(a: Bitmap, b: Bitmap): Int {
        var n = 0
        for (x in 0 until a.width step 6) for (y in 0 until a.height step 6) if (a.getPixel(x, y) != b.getPixel(x, y)) n++
        return n
    }

    @Test
    fun `ogni effetto del cielo cambia il disegno`() {
        val calma = cielo("calma", Atmosfera())
        val nebbia = cielo("nebbia", Atmosfera(nebbia = 1f))
        val vento = cielo("vento-forte", Atmosfera(vento = 1f, versoVento = 1f))
        val caldo = cielo("caldo", Atmosfera(caldo = 1f), sky = SkyState.of(0.8f))
        val dopo = cielo(
            "dopo-la-pioggia",
            Atmosfera(dopoPioggia = 1f),
            scena = Scena(sole = 1f, copertura = 0.55f, tempesta = 0f, bagnato = 0f, ghiaccio = 0f, neve = 0f, notte = 0f),
            sky = SkyState.of(0.25f, journey = 0.8f, evening = 0.9f),
        )
        val dopoAsciutto = cielo(
            "senza-pioggia-prima",
            Atmosfera(),
            scena = Scena(sole = 1f, copertura = 0.55f, tempesta = 0f, bagnato = 0f, ghiaccio = 0f, neve = 0f, notte = 0f),
            sky = SkyState.of(0.25f, journey = 0.8f, evening = 0.9f),
        )
        assertTrue("la nebbia non si vede", diversi(calma, nebbia) > 2000)
        assertTrue("il vento non si vede", diversi(calma, vento) > 40)
        assertTrue("l'arcobaleno dopo la pioggia non si vede", diversi(dopoAsciutto, dopo) > 200)
        cielo("calma-mezzogiorno", Atmosfera(), sky = SkyState.of(0.8f)).let {
            assertTrue("il caldo non si vede", diversi(it, caldo) > 200)
        }
    }

    @Test
    fun `brina e gocce sul vetro della scheda`() {
        val palette = salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0.2f)
        fun pannello(nome: String, vetro: Vetro) = disegna(nome) {
            CompositionLocalProvider(LocalMovimento provides false, LocalVetro provides vetro) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    PannelloSala(palette = palette) {
                        Text("Gelo, cielo sereno", style = SalaType.cardTitle, color = palette.ink)
                        Text("La brina sui bordi si scioglie toccando la scheda, e torna a formarsi piano dopo una decina di secondi, come su un vetro vero quando fuori gela.", style = SalaType.rowNote, color = palette.inkSoft)
                        androidx.compose.foundation.layout.Spacer(Modifier.height(140.dp))
                    }
                }
            }
        }
        val nudo = pannello("vetro-nudo", Vetro())
        val brina = pannello("vetro-brina", Vetro(gelo = 1f))
        assertTrue("la brina non si vede", diversi(nudo, brina) > 100)
    }

    @Test
    fun `le gocce scivolano`() {
        val a = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        val b = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        listOf(a to 2f, b to 4.5f).forEach { (bmp, t) ->
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                androidx.compose.ui.unit.Density(2.6f), androidx.compose.ui.unit.LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(bmp.asImageBitmap()),
                androidx.compose.ui.geometry.Size(400f, 600f),
            ) {
                drawRect(androidx.compose.ui.graphics.Color(0xFFEDE6DA))
                disegnaGocce(1f, t)
            }
        }
        File("build/widget-renders").resolve("atmosfera-gocce.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("le gocce stanno ferme", diversi(a, b) > 5)
    }

    private fun ora(h: Int, mm: Double = 0.0, codice: Int = 1, t: Double = 15.0, vis: Double? = 20000.0, vento: Double = 2.0, dir: Double = 270.0) =
        HourForecast(time = LocalDateTime.of(2026, 11, 12, h, 0), precipitation = mm, weatherCode = codice, temperature = t, visibility = vis, windSpeed = vento, windDirection = dir)

    @Test
    fun `i conti dell'atmosfera`() {
        val ore = listOf(ora(14, mm = 3.0, codice = 63), ora(15), ora(16), ora(17))
        assertEquals(1f, atmosferaBersaglio(ore, 1, 1).dopoPioggia)
        assertEquals(0.5f, atmosferaBersaglio(ore, 2, 1).dopoPioggia)
        assertEquals(0f, atmosferaBersaglio(ore, 3, 1).dopoPioggia)
        // Mentre piove non e' "dopo".
        assertEquals(0f, atmosferaBersaglio(ore, 0, 63).dopoPioggia)
        // Nebbia dal codice, foschia dalla visibilita'.
        assertEquals(1f, atmosferaBersaglio(listOf(ora(8, codice = 45)), 0, 45).nebbia)
        assertEquals(0.35f, atmosferaBersaglio(listOf(ora(8, vis = 2500.0)), 0, 1).nebbia)
        // Il vento da ovest va a destra, da est a sinistra.
        assertTrue(atmosferaBersaglio(listOf(ora(8, dir = 270.0)), 0, 1).versoVento > 0.9f)
        assertTrue(atmosferaBersaglio(listOf(ora(8, dir = 90.0)), 0, 1).versoVento < -0.9f)
        assertEquals(1f, atmosferaBersaglio(listOf(ora(8, vento = 20.0)), 0, 1).vento)
        // Caldo e gelo sulle loro soglie.
        assertEquals(1f, atmosferaBersaglio(listOf(ora(14, t = 37.0)), 0, 1).caldo)
        assertEquals(0f, atmosferaBersaglio(listOf(ora(14, t = 25.0)), 0, 1).caldo)
        assertEquals(1f, atmosferaBersaglio(listOf(ora(6, t = -6.0)), 0, 1).gelo)
        assertEquals(0f, atmosferaBersaglio(listOf(ora(6, t = 3.0)), 0, 1).gelo)
    }
}
