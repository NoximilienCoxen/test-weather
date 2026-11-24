package com.caelumweather.ui.sala

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.caelumweather.ui.theme.MeteoTheme
import com.caelumweather.ui.theme.skyColors
import com.caelumweather.data.SkyState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * I pezzi presi da Material 3 Expressive, disegnati in `build/widget-renders`
 * (`expressive-*.png`): il gruppo di bottoni collegati delle impostazioni e il
 * guscio dell'indicatore di aggiornamento che cambia forma tirando.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpressiveRenderTest {

    private val palette = salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0f)

    private fun disegna(nome: String, contenuto: @Composable () -> Unit): Bitmap {
        val attivita = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        attivita.setContent {
            MeteoTheme(colors = skyColors(SkyState.Giorno)) {
                Column(modifier = Modifier.background(Color(0xFF8A939E)).width(411.dp).padding(16.dp)) { contenuto() }
            }
        }
        repeat(30) { ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS) }
        val vista: View = attivita.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(vista.width.coerceAtLeast(1), vista.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(bitmap))
        File("build/widget-renders").apply { mkdirs() }.resolve("expressive-$nome.png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    @Test
    fun `il gruppo di bottoni collegati mostra la voce scelta`() {
        val bitmap = disegna("bottoni") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SceltaSegmentata(listOf(1 to "Automatico", 2 to "Chiaro", 3 to "Scuro"), scelta = 2, palette = palette, onScegli = {})
                SceltaSegmentata(listOf(1 to "km/h", 2 to "m/s", 3 to "nodi", 4 to "Beaufort"), scelta = 1, palette = palette, onScegli = {})
            }
        }
        assertTrue(bitmap.height > 0)
    }

    @Test
    fun `l'interruttore cresce acceso e sotto il dito`() {
        disegna("interruttori") {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                InterruttoreSala(acceso = false, palette = palette)
                InterruttoreSala(acceso = true, palette = palette)
                InterruttoreSala(acceso = true, palette = palette, premuto = true)
            }
        }
    }

    @Test
    fun `il guscio dell'indicatore cambia forma tirando`() {
        disegna("guscio") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(0f, 0.35f, 0.7f, 1f).forEach { tiro ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(palette.panelSolido, formaDellIndicatore(caricando = false, tiro = tiro, movimento = true)),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(palette.accent, formaDellIndicatore(caricando = true, tiro = 1f, movimento = true)),
                )
            }
        }
    }
}
