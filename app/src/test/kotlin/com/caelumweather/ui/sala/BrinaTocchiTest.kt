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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

    /** Un tocco veloce alla posizione data, in punti. */
    private fun tocca(attivita: ComponentActivity, xDp: Float, yDp: Float) {
        val vista = attivita.window.decorView
        val d = attivita.resources.displayMetrics.density
        val giu = SystemClock.uptimeMillis()
        vista.dispatchTouchEvent(MotionEvent.obtain(giu, giu, MotionEvent.ACTION_DOWN, xDp * d, yDp * d, 0))
        ShadowLooper.idleMainLooper(40, java.util.concurrent.TimeUnit.MILLISECONDS)
        vista.dispatchTouchEvent(MotionEvent.obtain(giu, giu + 60, MotionEvent.ACTION_UP, xDp * d, yDp * d, 0))
    }

    @Test
    fun `due giorni toccati di fila con la brina vengono scelti tutti e due`() {
        val scelti = mutableListOf<Int>()
        val attivita = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val previsione = com.caelumweather.data.Forecast(
            current = com.caelumweather.data.CurrentWeather(temperature = -2.0),
            days = (0 until 7).map { d ->
                com.caelumweather.data.DayForecast(java.time.LocalDate.of(2026, 11, 26).plusDays(d.toLong()), "G$d", weatherCode = 73, tempMax = 3.0, tempMin = -2.0)
            },
        )
        attivita.setContent {
            var scelto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
            CompositionLocalProvider(LocalVetro provides Vetro(gelo = 1f)) {
                PannelloSala(palette = salaPalette(dk = 1f, crepuscolo = 0f, chiusura = 0f), modifier = Modifier.width(400.dp)) {
                    com.caelumweather.ui.sala.rooms.StrisciaGiorni(
                        settimana = settimanaDi(previsione),
                        scelto = scelto,
                        state = com.caelumweather.ui.UiState(forecast = previsione),
                        palette = salaPalette(dk = 1f, crepuscolo = 0f, chiusura = 0f),
                        onScegli = { scelto = it; scelti += it },
                    )
                }
            }
        }
        repeat(10) { ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS) }
        // La striscia: sette giorni su 356 punti (400 meno i margini della scheda).
        fun x(giorno: Int) = 22f + 356f / 7f * (giorno + 0.5f)
        tocca(attivita, x(3), 60f)
        repeat(6) { ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS) }
        tocca(attivita, x(6), 60f)
        repeat(10) { ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS) }
        assertEquals(listOf(3, 6), scelti)
    }

    @Test
    fun `le celle che portano altrove rispondono con la brina, una dopo l'altra`() {
        val toccate = mutableListOf<String>()
        val attivita = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val palette = salaPalette(dk = 1f, crepuscolo = 0f, chiusura = 0f)
        attivita.setContent {
            CompositionLocalProvider(LocalVetro provides Vetro(gelo = 1f)) {
                PannelloSala(palette = palette, modifier = Modifier.width(400.dp)) {
                    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
                        CellaValore("VENTO", "13 km/h", palette, onVai = { toccate += "vento" })
                        CellaValore("UMIDITÀ", "81 %", palette, onVai = { toccate += "umidita" })
                        CellaValore("LUNA", "95 %", palette, onVai = { toccate += "luna" })
                    }
                }
            }
        }
        repeat(10) { ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS) }
        val terzo = 356f / 3f
        listOf(0, 1, 2).forEach { i ->
            tocca(attivita, 22f + terzo * (i + 0.5f), 45f)
            repeat(6) { ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS) }
        }
        assertEquals(listOf("vento", "umidita", "luna"), toccate)
    }
}
