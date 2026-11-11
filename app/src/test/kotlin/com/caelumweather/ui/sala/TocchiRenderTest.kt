package com.caelumweather.ui.sala

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
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
 * Cio' che lascia un tocco su pioggia, neve e grandine: si guarda in
 * `build/widget-renders/tocco-*.png`. La prova verifica due cose: che il tocco
 * disegni **qualcosa in piu'** della scena senza tocco, e che dopo la sua durata
 * non resti niente (un segno che non svanisce e' una macchia sullo schermo).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TocchiRenderTest {

    private fun scena(neve: Float, ghiaccio: Float) = Scena(
        sole = 0f, copertura = 0.9f, tempesta = 0f, bagnato = 0.9f, ghiaccio = ghiaccio, neve = neve, notte = 0f,
    )

    private fun disegna(nome: String, scena: Scena, tocchi: List<Tocco>, scroscio: Float, t: Float): Bitmap {
        val w = 540
        val h = 900
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color(0xFF5A6F86))
            cioCheCade(scena, t, tocchi, scroscio)
        }
        File("build/widget-renders").apply { mkdirs() }.resolve("$nome.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun differenze(a: Bitmap, b: Bitmap): Int {
        var n = 0
        for (x in 0 until a.width step 3) for (y in 0 until a.height step 3) if (a.getPixel(x, y) != b.getPixel(x, y)) n++
        return n
    }

    @Test
    fun `il tocco si vede, e passa`() {
        val punto = Offset(270f, 420f)
        listOf(
            Triple("pioggia", scena(0f, 0f), Caduta.PIOGGIA),
            Triple("neve", scena(1f, 0f), Caduta.NEVE),
            Triple("grandine", scena(0f, 1f), Caduta.GRANDINE),
        ).forEach { (nome, sc, tipo) ->
            val nato = 10f
            val tocco = Tocco(punto, nato, tipo, 4242)
            val senza = disegna("tocco-$nome-senza", sc, emptyList(), 0f, nato + 0.45f)
            val con = disegna("tocco-$nome-0", sc, listOf(tocco), if (tipo == Caduta.PIOGGIA) 1f else 0f, nato + 0.45f)
            disegna("tocco-$nome-1", sc, listOf(tocco), if (tipo == Caduta.PIOGGIA) 0.4f else 0f, nato + 1.0f)
            assertTrue("$nome: il tocco non disegna niente", differenze(senza, con) > 80)
            // A tocco finito: uguale alla scena senza tocco (lo scroscio e' a zero).
            val finito = disegna("tocco-$nome-finito", sc, listOf(tocco), 0f, nato + DURATA_TOCCO + 0.5f)
            val dopo = disegna("tocco-$nome-senza-dopo", sc, emptyList(), 0f, nato + DURATA_TOCCO + 0.5f)
            assertTrue("$nome: resta un segno dopo la durata", differenze(finito, dopo) == 0)
        }
    }
}
