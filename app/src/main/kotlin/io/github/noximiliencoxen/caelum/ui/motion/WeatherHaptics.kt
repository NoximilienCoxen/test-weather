package io.github.noximiliencoxen.caelum.ui.motion

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Il tempo che si sente in mano.
 *
 * Non passa dal riscontro aptico di Compose ma dal vibratore della piattaforma,
 * perche' qui serve scegliere l'intensita': una goccia e un tuono non possono
 * dare lo stesso colpo. Il riscontro di sistema offre categorie di gesto, non
 * gradi di forza.
 *
 * Chiedere l'intensita' pero' non basta a ottenerla. Su questo telefono
 * `hasAmplitudeControl()` risponde di no, e un'ampiezza dichiarata viene
 * semplicemente ignorata: la pioggia usciva forte quanto il tuono, distinta
 * solo dalla durata. Da li' la scala di ripieghi qui sotto, in ordine di quanto
 * bene sanno dosare.
 *
 * **Sui `@SuppressLint("NewApi")` qui sotto.** Certi ripieghi usano API piu'
 * recenti del minimo dichiarato (26): le primitive componibili vogliono il 30,
 * gli effetti predefiniti il 29. Che non finiscano su un telefono che non le ha
 * lo garantisce [modeOf], che sceglie il modo guardando `Build.VERSION.SDK_INT`
 * prima di ogni altra cosa - ma la garanzia passa per un valore di enum, e lint
 * non la sa seguire fin qui. E' la stessa forma di `DeviceLocation`, dove il
 * permesso lo garantisce `granted()` e i metodi portano
 * `@SuppressLint("MissingPermission")`.
 *
 * Chi tocca [raindrop] e [thunder] ricordi che la soppressione vale per tutto il
 * corpo: una chiamata nuova va messa dentro il ramo del modo che la ammette, non
 * fuori dal `when`.
 */
@Immutable
class WeatherHaptics(private val vibrator: Vibrator?, private val mode: Mode) {

    enum class Mode {
        /** Primitive componibili: l'unica via che scala davvero la forza. */
        PRIMITIVE,

        /** Ampiezza per impulso. */
        AMPLITUDE,

        /** Effetti gia' pronti del sistema: pochi, ma tarati bene. */
        PREDEFINED,

        /** Solo acceso o spento: si dosa con la durata e basta. */
        BLUNT,

        NONE,
    }

    /**
     * Il colpetto di una goccia che tocca la cifra.
     *
     * Lo chiama l'urto, non un orologio: e' quello che lo rende responsivo
     * invece che decorativo. Se ne arrivano piu' d'una nello stesso istante il
     * colpo si fa un po' piu' pieno - una raffica si sente come una raffica -
     * ma con un tetto, perche' oltre un certo punto non e' piu' pioggia.
     */
    @SuppressLint("NewApi") // Il modo e' gia' passato da modeOf(), vedi in testa.
    fun raindrop(drops: Int = 1) {
        val v = vibrator ?: return
        val strength = (DROP_SCALE * (1f + (drops - 1) * 0.30f)).coerceIn(0.05f, 0.55f)
        val effect = when (mode) {
            Mode.PRIMITIVE -> VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, strength)
                .compose()
            Mode.AMPLITUDE ->
                VibrationEffect.createOneShot(11, (strength * 255f).toInt().coerceIn(1, 255))
            Mode.PREDEFINED -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            Mode.BLUNT -> VibrationEffect.createOneShot(5, VibrationEffect.DEFAULT_AMPLITUDE)
            Mode.NONE -> return
        }
        runCatching { v.vibrate(effect) }
    }

    /**
     * Il tuono: uno schianto e il rotolare che segue.
     *
     * Due tempi e non uno solo perche' un lampo non fa un rumore, ne fa due. Con
     * una vibrazione piatta si sente un avviso di notifica.
     */
    @SuppressLint("NewApi") // Il modo e' gia' passato da modeOf(), vedi in testa.
    fun thunder() {
        val v = vibrator ?: return
        val effect = when (mode) {
            Mode.PRIMITIVE -> VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.75f, 80)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.45f, 90)
                .compose()
            Mode.AMPLITUDE ->
                VibrationEffect.createWaveform(THUNDER_TIMINGS, THUNDER_AMPLITUDES, -1)
            Mode.PREDEFINED ->
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            Mode.BLUNT -> VibrationEffect.createWaveform(THUNDER_TIMINGS, -1)
            Mode.NONE -> return
        }
        runCatching { v.vibrate(effect) }
    }

    private companion object {
        /**
         * Su uno. Piu' leggero di quando il colpetto arrivava una volta per
         * giro di gocce: adesso ne arriva uno per goccia, e quello che a un
         * tocco al secondo era appena percettibile, a dieci sarebbe molesto.
         */
        const val DROP_SCALE = 0.15f

        val THUNDER_TIMINGS = longArrayOf(0, 45, 35, 130)
        val THUNDER_AMPLITUDES = intArrayOf(0, 235, 0, 110)
    }
}

@Composable
fun rememberWeatherHaptics(): WeatherHaptics {
    val context = LocalContext.current
    return remember(context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        WeatherHaptics(vibrator, modeOf(vibrator))
    }
}

private fun modeOf(vibrator: Vibrator?): WeatherHaptics.Mode = when {
    vibrator == null || !vibrator.hasVibrator() -> WeatherHaptics.Mode.NONE
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_CLICK,
    ) -> WeatherHaptics.Mode.PRIMITIVE
    vibrator.hasAmplitudeControl() -> WeatherHaptics.Mode.AMPLITUDE
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> WeatherHaptics.Mode.PREDEFINED
    else -> WeatherHaptics.Mode.BLUNT
}
