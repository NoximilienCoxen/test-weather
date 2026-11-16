package com.caelumweather.ui.sala

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Il pannello dentro lo scorrimento della Shell, **con e senza tetto**
 * (`LocalAltezzaPannello`). Senza tetto - il primo fotogramma, prima che la
 * Shell misuri il carosello - lo scorrimento interno non deve esserci: due
 * scorrimenti verticali annidati con altezza infinita chiudono l'app, ed e'
 * successo alla build di prova del 16 novembre (CONTESTO §80).
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PannelloTettoTest {

    private fun altezzaDelPannello(tetto: Dp): Float {
        val palette = salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0f)
        val attivita = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        attivita.setContent {
            CompositionLocalProvider(LocalAltezzaPannello provides tetto) {
                // Come la Shell: una colonna che scorre, e il pannello dentro.
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).width(360.dp)) {
                    PannelloSala(palette = palette) {
                        Spacer(Modifier.height(900.dp))
                    }
                }
            }
        }
        ShadowLooper.idleMainLooper()
        val vista: View = attivita.window.decorView
            .findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        return vista.height / attivita.resources.displayMetrics.density
    }

    @Test
    fun `senza tetto il pannello non si chiude e cresce col contenuto`() {
        val alto = altezzaDelPannello(Dp.Unspecified)
        assertTrue("alto $alto dp", alto > 900f)
    }

    @Test
    fun `con il tetto il pannello si ferma e scorre dentro`() {
        val alto = altezzaDelPannello(300.dp)
        assertTrue("alto $alto dp", alto <= 301f)
    }
}
