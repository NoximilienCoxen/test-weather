package io.github.noximiliencoxen.caelum.ui.sala.rooms.rilievo

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.noximiliencoxen.caelum.data.HourForecast
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.sin

/** Il rilievo della settimana disegnato da tre punti di vista, per gli occhi. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RilievoRenderTest {

    // Una settimana credibile: giorno e notte, e un fronte freddo a meta'.
    private val ore = (0 until 7 * 24).map { h ->
        val giorno = h / 24
        val ciclo = sin((h % 24 - 9) / 24.0 * 2 * PI) * 5
        val fronte = if (giorno in 3..4) -6.0 else 0.0
        HourForecast(LocalDateTime.of(2026, 9, 24, 0, 0).plusHours(h.toLong()), temperature = 15 + ciclo + fronte + giorno * 0.5)
    }

    @Test
    fun `tre punti di vista`() {
        val rilievo = Rilievo.da(ore)!!
        val buffer = BufferRilievo(rilievo)
        listOf(Triple(-28f, 38f, -1), Triple(20f, 60f, 3 * 24 + 15), Triple(-70f, 20f, 15)).forEach { (yaw, pitch, scelto) ->
            val w = 640; val h = 400
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
                drawRect(Color(0xFFF5EAD8))
                disegnaRilievo(rilievo, buffer, yaw, pitch, scelto, Color(0xFF2E2B25))
            }
            File("build/widget-renders").apply { mkdirs() }
                .resolve("rilievo-${yaw.toInt()}-${pitch.toInt()}.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Qualcosa e' stato disegnato sopra il fondo.
            var diversi = 0
            for (x in 0 until w step 5) for (y in 0 until h step 5) if (bitmap.getPixel(x, y) != 0xFFF5EAD8.toInt()) diversi++
            assertTrue("rilievo vuoto a $yaw/$pitch", diversi > 200)
        }
    }
}
