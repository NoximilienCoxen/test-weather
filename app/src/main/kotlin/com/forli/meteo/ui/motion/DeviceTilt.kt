package com.forli.meteo.ui.motion

import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 *
 * ## Il verso
 *
 * L'oggetto **segue** il telefono, non lo contrasta. Inclinando il telefono
 * verso destra la faccia della cifra va verso destra, come se la cifra stesse
 * dentro il telefono e la si stesse porgendo; alzando il bordo superiore si
 * scopre il piano di sopra, come guardando un oggetto appoggiato su un tavolo
 * che si inclina verso di noi. Prima i segni erano quelli opposti - il
 * comportamento "controrotazione", in cui l'oggetto resta fermo rispetto al
 * mondo e a muoversi e' la finestra - che e' altrettanto difendibile in teoria
 * e, in mano, si legge semplicemente come un movimento sbagliato.
 *
 * ## Perche' il filtro cambia forza da solo
 *
 * Un filtro fisso costringe a scegliere fra due cose che servono entrambe. Se
 * e' leggero, l'oggetto segue il polso ma il rumore dell'accelerometro passa,
 * e col telefono appoggiato sul tavolo la scena si ridisegna per sempre. Se e'
 * pesante, il telefono fermo sta fermo ma l'oggetto arriva mezzo secondo dopo
 * la mano.
 *
 * Qui la frequenza di taglio dipende da **quanto velocemente il segnale sta
 * cambiando**: quasi ferma quando il telefono e' fermo, che e' quando serve
 * essere sordi al rumore; larga quando il polso si muove, che e' quando serve
 * essere pronti. Misurato in simulazione, contro un rumore realistico e con la
 * zona morta all'un per cento:
 *
 * | filtro | scritture / 1000 letture da fermo | 90% dell'inclinazione |
 * |---|---|---|
 * | un polo a 0,24 (quello di prima) | 49 | ~250 ms |
 * | due poli a 0,15 | 0,04 | ~500 ms |
 * | **taglio adattivo, qui** | **0,03** | **200 ms** |
 *
 * Cioe' piu' quieto del filtro pesante e piu' pronto di quello leggero. Non e'
 * un'invenzione: e' il filtro "a un euro", pensato esattamente per i segnali
 * che devono essere fermi da fermi e reattivi in movimento.
 */
@Composable
fun rememberDeviceTilt(
    enabled: Boolean = true,
    maxDegrees: Float = 9f,
): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(context, enabled, maxDegrees) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || manager == null || sensor == null) {
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }

        val limit = GRAVITY * sin(maxDegrees * PI.toFloat() / 180f)
        val listener = object : SensorEventListener {
            private val smoothX = AdaptiveLowPass()
            private val smoothY = AdaptiveLowPass()
            private var baseX = Float.NaN
            private var baseY = Float.NaN
            private var lastNanos = 0L

            override fun onSensorChanged(event: SensorEvent) {
                val rawX = event.values[0]
                val rawY = event.values[1]

                // Il passo di tempo vero, non quello nominale: SENSOR_DELAY_GAME
                // e' un suggerimento, e il sensore consegna quando puo'. Un
                // filtro tarato su venti millisecondi e alimentato ogni sessanta
                // sarebbe tre volte piu' lento di come e' stato misurato.
                val dt = if (lastNanos == 0L) {
                    NOMINAL_STEP
                } else {
                    ((event.timestamp - lastNanos) / 1e9f).coerceIn(0.004f, 0.1f)
                }
                lastNanos = event.timestamp

                if (baseX.isNaN()) {
                    baseX = rawX
                    baseY = rawY
                }
                baseX += (rawX - baseX) * SLOW
                baseY += (rawY - baseY) * SLOW

                val x = smoothX.next(rawX, dt) - baseX
                val y = smoothY.next(rawY, dt) - baseY

                // I segni: l'oggetto segue il telefono. Vedi la nota sopra.
                val nextX = (x / limit).coerceIn(-1f, 1f)
                val nextY = (-y / limit).coerceIn(-1f, 1f)

                // Un accelerometro non sta mai fermo: anche col telefono
                // appoggiato sul tavolo l'ultima cifra balla. Scrivere ogni
                // lettura teneva l'intera scena a ridisegnarsi cinquanta volte
                // al secondo per un movimento che nessuno puo' vedere, e con
                // una scena in tre dimensioni quel lavoro si paga caro.
                // Sotto la soglia il valore precedente e' altrettanto vero.
                val current = tilt.value
                if (abs(nextX - current.x) > DEADBAND || abs(nextY - current.y) > DEADBAND) {
                    tilt.value = Offset(nextX, nextY)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun start() = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        fun stop() {
            manager.unregisterListener(listener)
            tilt.value = Offset.Zero
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

    return tilt
}

/**
 * Passa-basso la cui frequenza di taglio sale con la velocita' del segnale.
 *
 * Niente allocazioni e niente coroutine: sono tre campi e cinque moltiplicazioni
 * per lettura, chiamate dal filo del sensore. La versione precedente affidava
 * invece l'ammorbidimento a un `Animatable`, e quello era un errore di natura,
 * non di taratura: ogni lettura del sensore **faceva ripartire una molla**, e
 * una molla che riparte cinquanta volte al secondo non ammorbidisce niente -
 * chiede fotogrammi per assestarsi e non ci riesce mai. Era li' la scattosita'.
 */
private class AdaptiveLowPass {

    private var value = Float.NaN
    private var speed = 0f

    fun next(raw: Float, dt: Float): Float {
        if (value.isNaN()) {
            value = raw
            return raw
        }
        // La velocita' del segnale, a sua volta smorzata: usare quella grezza
        // vorrebbe dire far decidere al rumore quanto essere sordi al rumore.
        val derivative = (raw - value) / dt
        speed += (derivative - speed) * alpha(SPEED_CUTOFF, dt)
        value += (raw - value) * alpha(MIN_CUTOFF + BETA * abs(speed), dt)
        return value
    }

    private fun alpha(cutoffHz: Float, dt: Float): Float {
        val tau = 1f / (TWO_PI * cutoffHz)
        return dt / (tau + dt)
    }

    private companion object {
        /** Taglio con il telefono fermo, in hertz. Piu' basso, piu' sordo. */
        const val MIN_CUTOFF = 0.40f

        /** Quanto il taglio si allarga con la velocita'. */
        const val BETA = 0.25f

        /** Taglio del filtro sulla derivata. */
        const val SPEED_CUTOFF = 1f

        const val TWO_PI = (2.0 * PI).toFloat()
    }
}

/**
 * Quanti gradi gira la scena quando l'inclinazione e' a fondo corsa.
 *
 * Stanno qui, e non nei due disegni che li usano, perche' la scultura e la
 * cifra sono **lo stesso oggetto visto dallo stesso punto**. Due copie di questi
 * numeri in due file diversi reggono finche' qualcuno ne ritocca una sola, e da
 * quel momento la scena si spacca in due pezzi che si inclinano di quantita'
 * diverse: un difetto che non somiglia affatto alla sua causa.
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

/** Passo nominale del sensore, usato solo per la primissima lettura. */
private const val NOMINAL_STEP = 0.02f

/**
 * Insegue la posa media, cosi' l'oggetto si ricentra da solo.
 *
 * Lento: la linea di base serve a non lasciare la cifra stabilmente storta
 * quando si tiene il telefono inclinato, ma se rincorre in fretta si mangia il
 * movimento che dovrebbe raccontare. Una decina di secondi, che e' la scala del
 * "come tengo in mano il telefono" e non quella del "lo sto inclinando".
 */
private const val SLOW = 0.0018f

/**
 * Sotto questo scostamento la lettura si considera ferma: l'un per cento della
 * corsa, cioe' meno di un pixel a schermo.
 *
 * Ci si puo' tornare - era salita al due per cento - perche' il filtro a taglio
 * adattivo lascia passare molto meno rumore di quello a un polo che c'era
 * prima. Misurato: 0,03 scritture ogni mille letture col telefono appoggiato,
 * contro le 49 di prima. Da fermo, zero fotogrammi.
 */
private const val DEADBAND = 0.01f
