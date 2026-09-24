package io.github.noximiliencoxen.caelum.icona

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.content.ContextCompat
import io.github.noximiliencoxen.caelum.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * L'icona come la mostra un lanciatore: fondo e disegno sovrapposti, poi la
 * maschera - tonda, e quadrata coi bordi arrotondati - e la versione a tema.
 *
 * Il livello adattivo e' di 108 unita' e se ne vedono le 72 centrali: qui si
 * disegna tutto grande e poi si ritaglia il quadrato centrale, come fa il
 * telefono. Le immagini vanno in `build/widget-renders`, che la CI pubblica.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconaRenderTest {

    private val contesto = RuntimeEnvironment.getApplication()

    @Test
    fun `icona adattiva, tonda e quadrata`() {
        val livelli = strati(listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground))
        salva("icona-tonda", maschera(livelli, tonda = true))
        salva("icona-quadrata", maschera(livelli, tonda = false))
        // Anche piccola, dove si vede se una forma si impasta.
        salva("icona-48dp", Bitmap.createScaledBitmap(maschera(livelli, tonda = true), 144, 144, true))
    }

    @Test
    fun `icona a tema`() {
        val fondo = Bitmap.createBitmap(LATO, LATO, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(0x2E, 0x3A, 0x2A)) }
        val disegno = strato(R.drawable.ic_launcher_monochrome, tinta = Color.rgb(0xD8, 0xE8, 0xC8))
        Canvas(fondo).drawBitmap(disegno, 0f, 0f, null)
        salva("icona-tema", maschera(fondo, tonda = true))
    }

    private fun strati(id: List<Int>): Bitmap {
        val out = Bitmap.createBitmap(LATO, LATO, Bitmap.Config.ARGB_8888)
        val tela = Canvas(out)
        id.forEach { tela.drawBitmap(strato(it), 0f, 0f, null) }
        return out
    }

    private fun strato(id: Int, tinta: Int? = null): Bitmap {
        val d = ContextCompat.getDrawable(contesto, id)
        assertNotNull("risorsa $id", d)
        d!!.setBounds(0, 0, LATO, LATO)
        if (tinta != null) d.colorFilter = PorterDuffColorFilter(tinta, PorterDuff.Mode.SRC_IN)
        val out = Bitmap.createBitmap(LATO, LATO, Bitmap.Config.ARGB_8888)
        d.draw(Canvas(out))
        return out
    }

    /** Il quadrato centrale di 72 su 108, con la maschera del lanciatore. */
    private fun maschera(livelli: Bitmap, tonda: Boolean): Bitmap {
        val visibile = LATO * 72 / 108
        val margine = (LATO - visibile) / 2
        val out = Bitmap.createBitmap(visibile, visibile, Bitmap.Config.ARGB_8888)
        val tela = Canvas(out)
        val pennello = Paint(Paint.ANTI_ALIAS_FLAG)
        val forma = Path().apply {
            val r = RectF(0f, 0f, visibile.toFloat(), visibile.toFloat())
            if (tonda) addOval(r, Path.Direction.CW) else addRoundRect(r, visibile * 0.22f, visibile * 0.22f, Path.Direction.CW)
        }
        tela.drawPath(forma, pennello)
        pennello.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        tela.drawBitmap(livelli, -margine.toFloat(), -margine.toFloat(), pennello)
        return out
    }

    private fun salva(nome: String, bitmap: Bitmap) {
        File("build/widget-renders").apply { mkdirs() }
            .resolve("$nome.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val LATO = 648
    }
}
