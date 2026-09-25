package io.github.noximiliencoxen.caelum.ui.sala

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Le figurette del tempo, tutte, di giorno e di notte, su un fondo chiaro e uno
 * scuro: vanno in `build/widget-renders`, e la CI le pubblica. Si guardano; la
 * prova verifica solo che ci sia un disegno, e che il sole sia giallo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlifoRenderTest {

    @Test
    fun `tutte le figurette`() {
        val lato = 96
        val fondi = listOf(Color(0xFFF3EFE6), Color(0xFF2B2A33))
        val glifi = GlifoMeteo.entries
        val bitmap = Bitmap.createBitmap(lato * glifi.size, lato * 4, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(bitmap.asImageBitmap()),
            Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
        ) {
            fondi.forEachIndexed { f, fondo ->
                listOf(false, true).forEachIndexed { n, notte ->
                    val riga = f * 2 + n
                    drawRect(fondo, topLeft = Offset(0f, riga * lato.toFloat()), size = Size(size.width, lato.toFloat()))
                    glifi.forEachIndexed { i, glifo ->
                        // `inset` stringe la tela al riquadro: la figuretta si
                        // misura sulla sua tela, e cosi' ne ha una sua.
                        inset(
                            left = i * lato.toFloat(),
                            top = riga * lato.toFloat(),
                            right = size.width - (i + 1) * lato,
                            bottom = size.height - (riga + 1) * lato,
                        ) { disegnaGlifo(glifo, notte) }
                    }
                }
            }
        }
        File("build/widget-renders").apply { mkdirs() }.resolve("glifi-meteo.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // Il sole di giorno, sul fondo chiaro: il centro del primo riquadro e' giallo.
        val centro = bitmap.getPixel(lato / 2, lato / 2)
        val r = centro shr 16 and 0xFF
        val g = centro shr 8 and 0xFF
        val b = centro and 0xFF
        assertTrue("il sole non e' giallo: $r $g $b", r > 200 && g > 150 && b < 120)
        // Ogni riquadro ha qualcosa disegnato sopra il fondo.
        for (riga in 0 until 4) for (i in glifi.indices) {
            val colori = HashSet<Int>()
            for (x in 0 until lato step 4) for (y in 0 until lato step 4) {
                colori += bitmap.getPixel(i * lato + x, riga * lato + y)
            }
            assertTrue("${glifi[i]} riga $riga vuota", colori.size > 3)
        }
    }
}
