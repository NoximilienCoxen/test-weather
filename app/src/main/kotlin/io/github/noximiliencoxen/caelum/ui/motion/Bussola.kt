package io.github.noximiliencoxen.caelum.ui.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Se il telefono ha di che fare da bussola. */
fun bussolaDisponibile(context: Context): Boolean =
    (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
        ?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

/**
 * Verso dove e' girata la cima del telefono, in gradi da nord (0-360), o
 * nullo se la bussola e' spenta o non c'e'.
 *
 * Il vettore di rotazione e non accelerometro piu' magnetometro a mano: il
 * sistema li fonde gia' col giroscopio, e il risultato trema molto meno.
 * Resta comunque un ago che vibra, quindi si smussa - sul cerchio, con seno e
 * coseno, perche' la media fra 359 e 1 gradi e' 0 e non 180 - e si scrive solo
 * quando cambia di almeno un grado, come fa [rememberDeviceTilt] per non far
 * ridisegnare lo schermo per movimenti che nessuno vede.
 *
 * Come l'inclinazione, si spegne quando l'app va in pausa.
 */
@Composable
fun rememberBussola(accesa: Boolean): State<Float?> {
    val context = LocalContext.current
    val direzione = remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context, accesa) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensore = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!accesa || manager == null || sensore == null) {
            direzione.value = null
            return@DisposableEffect onDispose { }
        }

        val matrice = FloatArray(9)
        val orientamento = FloatArray(3)
        val ascoltatore = object : SensorEventListener {
            private var sx = Float.NaN
            private var sy = 0f

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrice, event.values)
                SensorManager.getOrientation(matrice, orientamento)
                val a = orientamento[0]
                if (sx.isNaN()) {
                    sx = cos(a); sy = sin(a)
                } else {
                    sx += (cos(a) - sx) * LISCIATURA
                    sy += (sin(a) - sy) * LISCIATURA
                }
                val gradi = ((Math.toDegrees(atan2(sy, sx).toDouble()).toFloat() % 360f) + 360f) % 360f
                val prima = direzione.value
                if (prima == null || abs(((gradi - prima + 540f) % 360f) - 180f) >= 1f) {
                    direzione.value = gradi
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun avvia() = manager.registerListener(ascoltatore, sensore, SensorManager.SENSOR_DELAY_UI)
        fun ferma() = manager.unregisterListener(ascoltatore)

        val proprietario = context.findLifecycleOwner()
        val osservatore = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> avvia()
                Lifecycle.Event.ON_PAUSE -> ferma()
                else -> Unit
            }
        }
        if (proprietario != null) proprietario.lifecycle.addObserver(osservatore) else avvia()

        onDispose {
            proprietario?.lifecycle?.removeObserver(osservatore)
            ferma()
            direzione.value = null
        }
    }
    return direzione
}

private const val LISCIATURA = 0.18f
