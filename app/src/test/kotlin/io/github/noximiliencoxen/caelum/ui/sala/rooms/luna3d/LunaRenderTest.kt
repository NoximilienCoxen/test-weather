package io.github.noximiliencoxen.caelum.ui.sala.rooms.luna3d

import android.graphics.Bitmap
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
 * La sfera disegnata davvero, a varie fasi e da vari punti di vista.
 *
 * Le immagini vanno in `build/widget-renders`, che la CI pubblica: la luna si
 * controlla guardandola. La prova in se' verifica solo che al plenilunio si
 * veda molta luce e al novilunio quasi niente - il resto e' per gli occhi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LunaRenderTest {

    private val globo = Globo()
    private val buffer = BufferGlobo(globo)
    private val carta by lazy { TessituraGlobo(globo) }

    /**
     * La luce per vertice, moltiplicata sulla carta in piena luce, deve ridare
     * sul vertice il colore vero: e' la promessa di [ColoriGlobo.modulatori].
     * Si tollera un passo o due di arrotondamento per canale.
     */
    @Test
    fun `luce per carta ridà il colore vero sul vertice`() {
        listOf(0.1f, 0.25f, 0.5f, 0.8f).forEach { fase ->
            val colori = coloriPer(globo, fase)
            for (v in 0 until globo.vertici step 37) {
                val pieno = TintaLuna.pieno(globo.albedo[v])
                val m = colori.modulatori[v]
                val vero = colori.vertici[v]
                for (s in intArrayOf(16, 8, 0)) {
                    val atteso = vero shr s and 0xFF
                    val ottenuto = (pieno shr s and 0xFF) * (m shr s and 0xFF) / 255
                    assertTrue("fase $fase vertice $v: $ottenuto invece di $atteso", kotlin.math.abs(ottenuto - atteso) <= 3)
                }
            }
        }
    }

    @Test
    fun `le fasi dalla Terra`() {
        val chiarezza = mutableMapOf<Float, Float>()
        listOf(0f, 0.07f, 0.25f, 0.4f, 0.5f, 0.6f, 0.75f, 0.93f).forEach { fase ->
            chiarezza[fase] = render("luna3d-fase-${(fase * 100).toInt()}", fase, 0f, 0f)
        }
        // Quota di pixel davvero accesi: al plenilunio il disco e' circa meta'
        // della tela, al novilunio non ce n'e' nessuno.
        assertTrue("plenilunio spento: ${chiarezza[0.5f]}", chiarezza[0.5f]!! > 0.3f)
        assertTrue("novilunio acceso: ${chiarezza[0f]}", chiarezza[0f]!! < 0.01f)
        assertTrue("primo quarto sbagliato: ${chiarezza[0.25f]}", chiarezza[0.25f]!! in 0.1f..0.35f)
    }

    /**
     * I mari si devono vedere: al plenilunio il centro dell'Imbrium e' almeno
     * 0,15 piu' scuro di un altipiano. Sul telefono i mari erano spariti mentre
     * qui si vedevano: questa soglia e' la parte che si puo' misurare.
     */
    @Test
    fun `al plenilunio i mari si vedono`() {
        mariVisibili(conCarta = false)
        mariVisibili(conCarta = true)
    }

    /**
     * Con la carta, com'e' sul telefono: stessa sfera, stessa fase, stessa
     * soglia sui mari - e la luna deve restare accesa quanto senza.
     */
    @Test
    fun `con la carta`() {
        listOf(0.07f, 0.25f, 0.5f, 0.75f).forEach { fase ->
            val senza = render("luna3d-fase-${(fase * 100).toInt()}", fase, 0f, 0f)
            val con = render("luna3d-carta-${(fase * 100).toInt()}", fase, 0f, 0f, conCarta = true)
            assertTrue("fase $fase: $con con la carta, $senza senza", kotlin.math.abs(con - senza) < 0.05f)
        }
        render("luna3d-carta-giro-quarto-120-0", 0.25f, 120f, 0f, conCarta = true)
    }

    private fun mariVisibili(conCarta: Boolean) {
        val lato = 440
        val bitmap = disegna(lato, 0.5f, 0f, 0f, conCarta)
        val r = lato * 0.40f
        fun luminosita(dx: Float, dy: Float): Double {
            val dz = -kotlin.math.sqrt(1f - dx * dx - dy * dy)
            val d = r * 5f
            val s = d / (d + dz * r)
            val p = bitmap.getPixel((lato / 2f + dx * r * s).toInt(), (lato / 2f + dy * r * s).toInt())
            return ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / (3.0 * 255.0)
        }
        val imbrium = Globo.IMBRIUM
        val mare = luminosita(imbrium[0], imbrium[1])
        val altipiano = luminosita(0.15f, 0.75f)
        assertTrue("carta=$conCarta: mare $mare, altipiano $altipiano", altipiano - mare >= 0.15)
    }

    @Test
    fun `girandole intorno`() {
        listOf(0f to 0f, 60f to 0f, 120f to 0f, 180f to 0f, 0f to 60f, -90f to -30f).forEach { (yaw, pitch) ->
            render("luna3d-giro-quarto-${yaw.toInt()}-${pitch.toInt()}", 0.25f, yaw, pitch)
        }
        render("luna3d-giro-nuova-180", 0f, 180f, 0f)
    }

    /** Disegna e restituisce la quota di pixel accesi (luminosita' oltre meta'). */
    private fun render(nome: String, fase: Float, yaw: Float, pitch: Float, conCarta: Boolean = false): Float {
        val lato = 440
        val bitmap = disegna(lato, fase, yaw, pitch, conCarta)
        File("build/widget-renders").apply { mkdirs() }
            .resolve("$nome.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return accesi(bitmap, lato)
    }

    private fun disegna(lato: Int, fase: Float, yaw: Float, pitch: Float, conCarta: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(lato, lato, Bitmap.Config.ARGB_8888)
        val colori = coloriPer(globo, fase)
        val tessitura = if (conCarta) carta else null
        CanvasDrawScope().draw(
            Density(2f),
            LayoutDirection.Ltr,
            Canvas(bitmap.asImageBitmap()),
            Size(lato.toFloat(), lato.toFloat()),
        ) {
            drawRect(Color(0xFF0B0E16))
            disegnaGlobo(
                globo, colori, buffer, yaw, pitch,
                alone = Color(colori.alone).copy(alpha = 0.22f * Globo.frazioneIlluminataVista(fase, yaw, pitch, 40)),
                tessitura = tessitura,
            )
        }
        return bitmap
    }

    private fun accesi(bitmap: Bitmap, lato: Int): Float {
        var accesi = 0
        var conti = 0
        for (x in 0 until lato step 4) for (y in 0 until lato step 4) {
            val p = bitmap.getPixel(x, y)
            val l = ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / (3.0 * 255.0)
            if (l > 0.5) accesi++
            conti++
        }
        return accesi.toFloat() / conti
    }
}
