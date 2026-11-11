package com.caelumweather.ui.sala

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.caelumweather.data.HourForecast
import com.caelumweather.prefs.TempUnit
import com.caelumweather.ui.sala.rooms.disegnaGiornata
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import kotlin.math.cos

/**
 * La curva della giornata e l'astro di attesa, come immagini in
 * `build/widget-renders` (`giornata-*.png`, `astro-*.png`): si guardano. La prova
 * controlla solo che disegnino e che il cursore cambi il disegno.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnimazioniRenderTest {

    private fun salva(nome: String, w: Int, h: Int, scala: Float, sfondo: Color, corpo: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(scala), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            drawRect(sfondo)
            corpo()
        }
        File("build/widget-renders").apply { mkdirs() }.resolve("$nome.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun giornata(): List<HourForecast> = (0 until 24).map { h ->
        HourForecast(
            time = LocalDateTime.of(2026, 11, 11, h, 0),
            temperature = 11.0 - 5.5 * cos((h - 3) / 24.0 * 2 * Math.PI),
            isDay = h in 7..17,
        )
    }

    @Test
    fun `la curva della giornata, di giorno e di notte`() {
        val ore = giornata()
        val punti = ore.map { it.time.hour to it.temperature!! }
        val misura = TextMeasurer(createFontFamilyResolver(RuntimeEnvironment.getApplication()), Density(2f), LayoutDirection.Ltr)
        listOf(false, true).forEach { scuro ->
            val palette = salaPalette(dk = if (scuro) 1f else 0f, crepuscolo = 0f, chiusura = 0f)
            val sfondo = palette.panelSolido
            val a = salva("giornata-${if (scuro) "scuro" else "chiaro"}-14", 680, 300, 2f, sfondo) {
                disegnaGiornata(ore, punti, scelta = 14, cursore = 14f, adesso = 10, rivelazione = 1f, unita = TempUnit.CELSIUS, palette = palette, misura = misura)
            }
            val b = salva("giornata-${if (scuro) "scuro" else "chiaro"}-4", 680, 300, 2f, sfondo) {
                disegnaGiornata(ore, punti, scelta = 4, cursore = 4f, adesso = 10, rivelazione = 1f, unita = TempUnit.CELSIUS, palette = palette, misura = misura)
            }
            salva("giornata-${if (scuro) "scuro" else "chiaro"}-meta", 680, 300, 2f, sfondo) {
                disegnaGiornata(ore, punti, scelta = 14, cursore = 9f, adesso = 10, rivelazione = 0.5f, unita = TempUnit.CELSIUS, palette = palette, misura = misura)
            }
            var diversi = 0
            for (x in 0 until a.width step 4) for (y in 0 until a.height step 4) if (a.getPixel(x, y) != b.getPixel(x, y)) diversi++
            assertTrue("il cursore non cambia il disegno", diversi > 50)
        }
    }

    @Test
    fun `i tre astri di attesa`() {
        AstroDiAttesa.entries.forEach { astro ->
            listOf(0f to false, 0.35f to true, 0.8f to true).forEachIndexed { i, (giro, caricando) ->
                val b = salva("astro-${astro.name.lowercase()}-$i", 160, 160, 2f, Color(0xFF3A4A60)) {
                    disegnaAstro(astro, caricando, tiro = 0.8f, colore = Color(0xFFF3E9D6), giro = if (astro == AstroDiAttesa.LUNA) giro else giro * 360f, respiro = 1f)
                }
                var pieni = 0
                for (x in 0 until b.width step 3) for (y in 0 until b.height step 3) if (b.getPixel(x, y) != Color(0xFF3A4A60).hashCode()) pieni++
                assertTrue("$astro: disegno vuoto", pieni > 100)
            }
        }
    }
}
