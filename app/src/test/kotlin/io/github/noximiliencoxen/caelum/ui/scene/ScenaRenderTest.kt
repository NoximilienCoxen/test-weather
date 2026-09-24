package io.github.noximiliencoxen.caelum.ui.scene

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
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
 * Ogni scena, di giorno e di notte, nel riquadro del benvenuto e a tutto
 * schermo: le immagini vanno in `build/widget-renders`, e la CI le pubblica.
 * Si guardano; la prova verifica solo che ci sia un disegno e non una tela
 * di un colore solo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScenaRenderTest {

    @Test
    fun `tutte le scene`() {
        Scena.entries.forEach { scena ->
            listOf(false, true).forEach { notte ->
                val quando = if (notte) "notte" else "giorno"
                disegna("scena-${scena.name.lowercase()}-$quando", scena, notte, 540, 700, 1.35f)
            }
            // A tutto schermo, e un altro momento dell'animazione.
            disegna("scena-${scena.name.lowercase()}-intera", scena, false, 540, 1200, 3.1f)
        }
    }

    private fun disegna(nome: String, scena: Scena, notte: Boolean, w: Int, h: Int, t: Float) {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            disegnaScena(scena, t, notte)
        }
        File("build/widget-renders").apply { mkdirs() }.resolve("$nome.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colori = HashSet<Int>()
        for (x in 0 until w step 9) for (y in 0 until h step 9) colori += bitmap.getPixel(x, y)
        assertTrue("$nome: quasi un colore solo", colori.size > 40)
    }
}
