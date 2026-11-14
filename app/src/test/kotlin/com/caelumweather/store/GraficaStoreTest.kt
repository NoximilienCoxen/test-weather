package com.caelumweather.store

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import com.caelumweather.R
import com.caelumweather.ui.scene.Scena
import com.caelumweather.ui.scene.disegnaScena
import com.caelumweather.widget.paint.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * La grafica che la Play Console chiede e che il codice sa gia' disegnare:
 * l'icona a 512x512 e l'immagine in evidenza a 1024x500 (PUBBLICAZIONE.md §4).
 *
 * Si disegnano dalle stesse risorse dell'app - i livelli dell'icona adattiva,
 * una scena del benvenuto, il carattere dei widget - cosi' non c'e' un file a
 * parte da tenere allineato a mano. Finiscono in `build/widget-renders` con
 * gli altri disegni, e la CI li pubblica su `ci-artifacts`; da li' si copiano
 * in `fastlane/metadata/android/<lingua>/images/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GraficaStoreTest {

    private val contesto = RuntimeEnvironment.getApplication()

    /**
     * Quadrata e piena fino al bordo, senza maschera: gli angoli li arrotonda
     * lo store. Del livello adattivo di 108 unita' si tiene il quadrato
     * centrale di 72, quello che un lanciatore mostra.
     */
    @Test
    fun `icona 512`() {
        val icona = iconaPiena(512)
        assertEquals(512, icona.width)
        assertEquals(512, icona.height)
        salva("store-icona-512", icona)
    }

    @Test
    fun `immagine in evidenza 1024x500`() {
        mapOf(
            "it" to "Meteo, allerte ufficiali, aria e luna",
            "en" to "Weather, official warnings, air and moon",
        ).forEach { (lingua, motto) ->
            val img = inEvidenza(motto)
            assertEquals(1024, img.width)
            assertEquals(500, img.height)
            salva("store-in-evidenza-$lingua", img)
        }
    }

    private fun inEvidenza(motto: String): Bitmap {
        val w = 1024
        val h = 500
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // Il campo di grano, di giorno, fermo: e' la scena che l'icona riassume.
        CanvasDrawScope().draw(
            Density(1.6f), LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(out.asImageBitmap()), Size(w.toFloat(), h.toFloat()),
        ) { disegnaScena(Scena.GRANO, 0f, false) }

        val tela = Canvas(out)
        // Un velo chiaro a sinistra, dove sta la scritta: la scena sotto ha il
        // sole e il campo, e il testo ci deve stare sopra leggibile.
        val velo = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, w * 0.72f, 0f,
                Color.argb(215, 0xFB, 0xEE, 0xE0), Color.argb(0, 0xFB, 0xEE, 0xE0),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        tela.drawRect(0f, 0f, w.toFloat(), h.toFloat(), velo)

        // L'icona, con la maschera arrotondata di un lanciatore.
        val lato = 200
        val x0 = 72f
        val y0 = (h - lato) / 2f
        val icona = iconaPiena(lato)
        val forma = Bitmap.createBitmap(lato, lato, Bitmap.Config.ARGB_8888)
        Canvas(forma).apply {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            drawPath(Path().apply { addRoundRect(RectF(0f, 0f, lato.toFloat(), lato.toFloat()), lato * 0.22f, lato * 0.22f, Path.Direction.CW) }, p)
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            drawBitmap(icona, 0f, 0f, p)
        }
        val ombra = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 0x40, 0x30, 0x20) }
        tela.drawRoundRect(RectF(x0 + 4, y0 + 8, x0 + lato + 4, y0 + lato + 8), lato * 0.22f, lato * 0.22f, ombra)
        tela.drawBitmap(forma, x0, y0, null)

        val tipo = WidgetType(contesto)
        val inchiostro = Color.rgb(0x3A, 0x33, 0x28)
        val xt = x0 + lato + 48f
        val spazio = w - xt - 48f
        // Copie, non i pennelli in cache di WidgetType: qui si cambiano colore e corpo.
        val titolo = Paint(tipo.brush(76f, weight = 700, width = 78)).apply { color = inchiostro }
        val sotto = Paint(tipo.brush(30f, weight = 500, width = 82)).apply { color = Color.rgb(0x6A, 0x5C, 0x4A) }
        while (titolo.measureText("Caelum Weather") > spazio) titolo.textSize *= 0.95f
        while (sotto.measureText(motto) > spazio) sotto.textSize *= 0.95f
        val altezzaBlocco = (titolo.descent() - titolo.ascent()) + 14f + (sotto.descent() - sotto.ascent())
        val yTop = (h - altezzaBlocco) / 2f
        tela.drawText("Caelum Weather", xt, yTop - titolo.ascent(), titolo)
        tela.drawText(motto, xt, yTop + (titolo.descent() - titolo.ascent()) + 14f - sotto.ascent(), sotto)
        return out
    }

    private fun iconaPiena(lato: Int): Bitmap {
        val grande = lato * 108 / 72
        val livelli = Bitmap.createBitmap(grande, grande, Bitmap.Config.ARGB_8888)
        val tela = Canvas(livelli)
        listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).forEach { id ->
            val d = requireNotNull(ContextCompat.getDrawable(contesto, id))
            d.setBounds(0, 0, grande, grande)
            d.draw(tela)
        }
        val margine = (grande - lato) / 2
        return Bitmap.createBitmap(livelli, margine, margine, lato, lato)
    }

    private fun salva(nome: String, bitmap: Bitmap) {
        File("build/widget-renders").apply { mkdirs() }
            .resolve("$nome.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
