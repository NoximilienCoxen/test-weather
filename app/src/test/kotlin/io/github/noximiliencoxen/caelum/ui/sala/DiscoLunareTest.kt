package io.github.noximiliencoxen.caelum.ui.sala

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.noximiliencoxen.caelum.data.MoonPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * La luna unica (`discoLunare`), che adesso disegnano il cielo, il cursore di
 * Sala IV e il widget Luna.
 *
 * Nove fasi su un fondo chiaro e uno scuro finiscono in
 * `build/widget-renders/disco-lunare.png`, che la CI pubblica. La prova misura
 * quanta parte del disco e' accesa e la confronta con [MoonPhase.illumination]:
 * se la sagoma della fase si rompe - lato sbagliato, mediana rovesciata - la
 * quota non torna.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoLunareTest {

    private val lato = 120
    private val fasi = List(9) { it / 8f }

    private fun disegna(fondo: Color): Bitmap {
        val bitmap = Bitmap.createBitmap(lato * fasi.size, lato, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(bitmap.asImageBitmap()),
            Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
        ) {
            drawRect(fondo)
            fasi.forEachIndexed { i, fase ->
                discoLunare(Offset(i * lato + lato / 2f, lato / 2f), lato * 0.42f, fase)
            }
        }
        return bitmap
    }

    /** Quota dei pixel del disco piu' chiari della luce cinerea. */
    private fun quotaAccesa(bitmap: Bitmap, i: Int): Float {
        val r = lato * 0.42f * 0.92f
        var dentro = 0
        var accesi = 0
        for (x in 0 until lato) for (y in 0 until lato) {
            val dx = x - lato / 2f
            val dy = y - lato / 2f
            if (dx * dx + dy * dy > r * r) continue
            dentro++
            val p = bitmap.getPixel(i * lato + x, y)
            val luce = ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
            if (luce > 140) accesi++
        }
        return accesi.toFloat() / dentro
    }

    @Test
    fun `la parte accesa segue la fase, su fondo chiaro e scuro`() {
        listOf(Color(0xFFF3EFE6) to "chiaro", Color(0xFF141C28) to "scuro").forEach { (fondo, nome) ->
            val bitmap = disegna(fondo)
            File("build/widget-renders").apply { mkdirs() }.resolve("disco-lunare-$nome.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            fasi.forEachIndexed { i, fase ->
                val attesa = MoonPhase.illumination(fase)
                // I mari scuriscono un poco la parte accesa: tolleranza larga,
                // ma un lato sbagliato o una mediana rovesciata la sfondano.
                assertEquals("fase $fase su fondo $nome", attesa, quotaAccesa(bitmap, i), 0.12f)
            }
        }
    }

    @Test
    fun `crescente a destra, calante a sinistra`() {
        val bitmap = disegna(Color(0xFF141C28))
        fun luce(i: Int, x: Int): Int {
            val p = bitmap.getPixel(i * lato + x, lato / 2)
            return ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
        }
        // Primo quarto (fase 0,25, indice 2): accesa la meta' destra.
        assertTrue(luce(2, lato / 2 + 25) > luce(2, lato / 2 - 25) + 60)
        // Ultimo quarto (fase 0,75, indice 6): accesa la meta' sinistra.
        assertTrue(luce(6, lato / 2 - 25) > luce(6, lato / 2 + 25) + 60)
    }
}
