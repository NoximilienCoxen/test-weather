package com.caelumweather.ui.sala

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.caelumweather.prefs.SogliePersonali
import com.caelumweather.ui.UiState
import com.caelumweather.ui.theme.MeteoTheme
import com.caelumweather.ui.theme.skyColors
import com.caelumweather.data.SkyState
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * La schermata Impostazioni intera, chiara e scura, in `build/widget-renders`
 * (`impostazioni-chiaro.png`, `impostazioni-scuro.png`), con il permesso delle
 * notifiche concesso e qualche voce accesa perche' si vedano anche i selettori
 * a scomparsa (ombrello, pioggia forte). La finestra e' alta apposta: la
 * schermata scorre, e cosi' entra tutta in uno scatto. In `impostazioni-misure.txt`
 * l'altezza del contenuto, per confrontare quanto e' lunga.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "it-w411dp-h4200dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImpostazioniRenderTest {

    private val stato = UiState(
        avvisoOmbrello = true,
        soglie = SogliePersonali(pioggia = true),
    )

    private var ultima: ComponentActivity? = null

    private fun disegna(nome: String, palette: SalaPalette): Int {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val attivita = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        attivita.setContent {
            MeteoTheme(colors = skyColors(SkyState.Giorno)) {
                SalaImpostazioniScreen(
                    state = stato,
                    palette = palette,
                    onToggleAnimazioni = {},
                    onToggleWidgetColori = {},
                    onToggleSchedeLarghe = {},
                    onApriGuida = {},
                    onToggleNotifichePioggia = {},
                    onToggleNotificheAllerte = {},
                    onToggleRiepilogo = {},
                    onChooseOraRiepilogo = {},
                    onToggleSoglia = { _, _ -> },
                    onChooseSoglia = { _, _ -> },
                    onToggleSoglieAltre = {},
                    onChooseTheme = {},
                    onChooseUnit = {},
                    onChooseWindUnit = {},
                    onChooseCaptionStyle = {},
                    onToggleAlert = { _, _ -> },
                    onApriLocalita = {},
                    onAggiorna = {},
                    onApriLegali = {},
                    onClose = {},
                )
            }
        }
        repeat(40) { ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS) }
        ultima = attivita
        val vista: View = attivita.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(vista.width.coerceAtLeast(1), vista.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(bitmap))
        // Fin dove arriva il contenuto: l'ultima riga con un pixel diverso dal fondo.
        val fondo = bitmap.getPixel(2, bitmap.height - 2)
        var fine = bitmap.height - 1
        trovata@ while (fine > 0) {
            for (x in 0 until bitmap.width step 3) if (bitmap.getPixel(x, fine) != fondo) break@trovata
            fine--
        }
        val ritagliata = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, (fine + 40).coerceAtMost(bitmap.height))
        val cartella = File("build/widget-renders").apply { mkdirs() }
        cartella.resolve("impostazioni-$nome.png").outputStream().use { ritagliata.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val dp = (fine / 2f).toInt()
        cartella.resolve("impostazioni-misure.txt").appendText("$nome: ${dp} dp\n")
        return dp
    }

    @Test
    fun `le impostazioni chiare e scure, intere`() {
        File("build/widget-renders/impostazioni-misure.txt").delete()
        val chiaro = salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0f)
        val scuro = salaPalette(dk = 1f, crepuscolo = 0f, chiusura = 0f)
        val altezze = listOf(disegna("chiaro", chiaro), disegna("scuro", scuro))
        assertTrue(altezze.all { it > 500 })
    }

    /**
     * **La campanella si dice "Notifica" a TalkBack, una volta sola.** Si legge
     * l'albero di accessibilita' di Compose, con i nodi uniti come li unisce la
     * riga (che e' l'interruttore): la voce "Pioggia in arrivo" deve portare il
     * titolo, la nota e la parola "Notifica" esattamente una volta.
     */
    @Test
    fun `talkback legge la campanella come Notifica, una volta`() {
        disegna("talkback", salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0f))
        val radice = ultima!!.window.decorView
        fun cerca(v: View): androidx.compose.ui.platform.ViewRootForTest? =
            v as? androidx.compose.ui.platform.ViewRootForTest
                ?: (v as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { cerca(g.getChildAt(it)) } }
        // L'albero unito come lo vede TalkBack: si parte dalla radice e si scende.
        fun tutti(n: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> =
            listOf(n) + n.children.flatMap { tutti(it) }
        val nodi = tutti(cerca(radice)!!.semanticsOwner.rootSemanticsNode)
        val voce = nodi.first { n: androidx.compose.ui.semantics.SemanticsNode ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                ?.any { it.text.contains("Pioggia in arrivo") } == true
        }
        val testi = voce.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().map { it.text }
        val descrizioni = voce.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription).orEmpty()
        val tutto = (descrizioni + testi).joinToString(" | ")
        assertTrue(tutto, tutto.contains("nella prossima ora"))
        assertEquals(tutto, 1, Regex("Notifica").findAll(tutto).count())
    }
}
