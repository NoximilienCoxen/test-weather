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
    maxDegrees: Float = 9f,
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
            // Piu' tesa e appena meno smorzata di prima: il movimento deve
            // arrivare mentre si muove il polso, non due terzi di secondo dopo.
            // Sotto 0,7 di smorzamento comincia a rimbalzare, e un oggetto di
            // plastica opaca non rimbalza.
            smoothed.animateTo(value, spring(dampingRatio = 0.72f, stiffness = 420f))
        }
    }

    return smoothed.asState()
}

/**
 * Quanti gradi gira la scena quando l'inclinazione e' a fondo corsa.
 *
 * Stanno qui, e non nei due disegni che li usano, perche' la scultura e la
 * cifra sono **lo stesso oggetto visto dallo stesso punto**. Due copie di questi
 * numeri in due file diversi reggono finche' qualcuno ne ritocca una sola, e da
 * quel momento la scena si spacca in due pezzi che si inclinano di quantita'
 * diverse: un difetto che non somiglia affatto alla sua causa.
 *
 * Sedici gradi e non sette. A sette, e con la corsa di prima, un'inclinazione
 * del polso valeva mezzo grado di imbardata per grado di telefono: c'era, ma
 * bisognava sapere di doverla cercare. Ora sono quasi due gradi per grado, e la
 * faccia della cifra si accorcia abbastanza da vedersi.
 */
const val TILT_YAW_DEGREES = 16f

/**
 * Il beccheggio resta piu' contenuto dell'imbardata, e non per timidezza:
 * l'ordine di sovrapposizione dei caratteri e' garantito dalla sola rotazione
 * attorno all'asse verticale (vedi la nota sul tetto in CONTESTO.md). Il
 * beccheggio non lo rompe - i caratteri stanno tutti sulla stessa linea di
 * base, quindi il termine in y e' identico per tutti e non ne cambia l'ordine -
 * ma spinto oltre comincia a mostrare la base da sotto, e li' si vedrebbe che
 * il solido non ha un fondo.
 */
const val TILT_PITCH_DEGREES = 11f

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

private const val GRAVITY = 9.81f

/** Insegue il movimento reale. */
private const val FAST = 0.24f

/**
 * Insegue la posa media, cosi' l'oggetto si ricentra da solo.
 *
 * Piu' lento di prima. La linea di base serve a non lasciare la cifra
 * stabilmente storta quando si tiene il telefono inclinato, ma se rincorre
 * troppo in fretta si mangia il movimento che dovrebbe raccontare: si inclinava
 * il telefono, la scena rispondeva, e nel giro di un secondo tornava dritta da
 * sola mentre il telefono era ancora storto. Ora l'inseguimento e' lungo una
 * decina di secondi, che e' la scala del "come tengo in mano il telefono", non
 * quella del "lo sto inclinando".
 */
private const val SLOW = 0.0018f

/**
 * Sotto questo scostamento la lettura si considera ferma.
 *
 * Non e' un numero a caso e non si puo' lasciare quello di prima. La soglia sta
 * su un valore **normalizzato**, e normalizzare divide per la corsa: accorciando
 * la corsa da quattordici a nove gradi lo stesso identico tremolio
 * dell'accelerometro produce un valore un terzo piu' grande. In piu' il filtro
 * veloce, salendo da 0,14 a 0,24, ne lascia passare un altro quarto. Tenere lo
 * 0,01 di prima significherebbe farsi ridisegnare la scena cinquanta volte al
 * secondo col telefono appoggiato sul tavolo, che e' esattamente il difetto per
 * cui la soglia esiste.
 *
 * 1,3 volte 1,3 fa 1,7: da 0,01 si sale a 0,02, arrotondato per eccesso.
 * A schermo, moltiplicato per i sedici gradi di imbardata, vale un terzo di
 * grado - cioe' sempre niente da vedere, e sempre zero fotogrammi da fermo.
 */
private const val DEADBAND = 0.02f
