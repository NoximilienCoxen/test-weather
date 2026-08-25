package com.forli.meteo.ui.motion

import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Inclinazione del dispositivo, da -1 a 1 sui due assi.
 *
 * Usa l'accelerometro e non il vettore di rotazione: l'accelerometro misura
 * esattamente cio' che ci interessa, cioe' come il telefono e' orientato
 * rispetto alla gravita', ed e' presente su qualunque dispositivo. Il vettore
 * di rotazione aggiungerebbe anche l'imbardata, che qui non serve e porta
 * deriva.
 *
 * Il valore non e' l'inclinazione assoluta ma lo scostamento da una linea di
 * base che insegue lentamente la posa corrente. Nessuno tiene il telefono
 * perfettamente verticale: senza questo accorgimento l'oggetto resterebbe
 * stabilmente spostato di lato. Cosi' invece reagisce a come *cambi* la presa
 * e torna al centro se resti fermo.
 */
@Composable
fun rememberDeviceTilt(
    enabled: Boolean = true,
    maxDegrees: Float = 14f,
): State<Offset> {
    val context = LocalContext.current
    val target = remember { mutableStateOf(Offset.Zero) }
    val smoothed = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    DisposableEffect(context, enabled, maxDegrees) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || manager == null || sensor == null) {
            target.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }

        val limit = GRAVITY * sin(maxDegrees * PI.toFloat() / 180f)
        val listener = object : SensorEventListener {
            private var baseX = Float.NaN
            private var baseY = Float.NaN
            private var fastX = 0f
            private var fastY = 0f

            override fun onSensorChanged(event: SensorEvent) {
                val rawX = event.values[0]
                val rawY = event.values[1]
                if (baseX.isNaN()) {
                    baseX = rawX
                    baseY = rawY
                    fastX = rawX
                    fastY = rawY
                }
                fastX += (rawX - fastX) * FAST
                fastY += (rawY - fastY) * FAST
                baseX += (rawX - baseX) * SLOW
                baseY += (rawY - baseY) * SLOW
                val next = Offset(
                    x = (-(fastX - baseX) / limit).coerceIn(-1f, 1f),
                    y = ((fastY - baseY) / limit).coerceIn(-1f, 1f),
                )
                // Un accelerometro non sta mai fermo: anche col telefono
                // appoggiato sul tavolo l'ultima cifra balla. Scrivere ogni
                // lettura teneva l'intera scena a ridisegnarsi cinquanta volte
                // al secondo per un movimento che nessuno puo' vedere, e con
                // una scena in tre dimensioni quel lavoro si paga caro.
                // Sotto la soglia il valore precedente e' altrettanto vero.
                if (abs(next.x - target.value.x) > DEADBAND ||
                    abs(next.y - target.value.y) > DEADBAND
                ) {
                    target.value = next
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun start() = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        fun stop() {
            manager.unregisterListener(listener)
            target.value = Offset.Zero
        }

        // Il sensore deve spegnersi quando l'app non e' in primo piano: la
        // composizione resta viva anche in sottofondo, quindi non basta il
        // ciclo di vita del composable.
        val owner = context.findLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        if (owner != null) {
            owner.lifecycle.addObserver(observer)
        } else {
            start()
        }

        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            manager.unregisterListener(listener)
        }
    }

    LaunchedEffect(smoothed) {
        snapshotFlow { target.value }.collect { value ->
            smoothed.animateTo(value, spring(dampingRatio = 0.75f, stiffness = 200f))
        }
    }

    return smoothed.asState()
}

/**
 * Il proprietario del ciclo di vita dietro un contesto di Compose.
 *
 * Serve a chi deve accendersi e spegnersi col primo piano e non con la
 * composizione: la composizione resta viva anche in sottofondo. Lo usano il
 * sensore qui sotto e la ricarica dei dati.
 */
internal tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

private const val GRAVITY = 9.81f

/** Insegue il movimento reale. */
private const val FAST = 0.14f

/** Insegue la posa media, cosi' l'oggetto si ricentra da solo. */
private const val SLOW = 0.004f

/**
 * Sotto questo scostamento la lettura si considera ferma. E' l'un per cento
 * della corsa: a schermo vale meno di un pixel, cioe' niente.
 */
private const val DEADBAND = 0.01f
