package com.caelumweather.ui.sala

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * **Sotto lo zero i comandi della scheda rispondono.** La brina era un livello
 * sopra la scheda con un suo ascoltatore di tocchi, e fra fratelli
 * sovrapposti il tocco va solo a quello in cima: a Sapporo, a meno due, non
 * si sceglieva piu' nessun giorno. Qui un tocco vero, consegnato alla vista,
 * deve arrivare al bottone dentro una scheda con la brina piena.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrinaTocchiTest {

    @Test
    fun `con la brina il tocco arriva ai comandi della scheda`() {
        var tocchi = 0
        val attivita = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        attivita.setContent {
            CompositionLocalProvider(LocalVetro provides Vetro(gelo = 1f)) {
                PannelloSala(palette = salaPalette(dk = 0f, crepuscolo = 0f, chiusura = 0f), modifier = Modifier.width(400.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp).clickable { tocchi++ })
                }
            }
        }
        repeat(10) { ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS) }
        val vista = attivita.window.decorView
        val densita = attivita.resources.displayMetrics.density
        val x = 200f * densita
        val y = 120f * densita
        val giu = SystemClock.uptimeMillis()
        vista.dispatchTouchEvent(MotionEvent.obtain(giu, giu, MotionEvent.ACTION_DOWN, x, y, 0))
        ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.MILLISECONDS)
        vista.dispatchTouchEvent(MotionEvent.obtain(giu, giu + 60, MotionEvent.ACTION_UP, x, y, 0))
        repeat(10) { ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS) }
        assertEquals("il tocco deve arrivare al bottone sotto la brina", 1, tocchi)
    }
}
