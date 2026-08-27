package com.forli.meteo.ui.motion

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

    /** Un tocco appena percettibile, per la goccia. */
    fun drizzle() {
        val v = vibrator ?: return
        val effect = when (mode) {
            Mode.PRIMITIVE -> VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, DRIZZLE_SCALE)
                .compose()
            Mode.AMPLITUDE -> VibrationEffect.createOneShot(14, 17)
            Mode.PREDEFINED -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            Mode.BLUNT -> VibrationEffect.createOneShot(6, VibrationEffect.DEFAULT_AMPLITUDE)
            Mode.NONE -> return
        }
        runCatching { v.vibrate(effect) }
    }

    /**
     * Il giro che si chiude: un colpo secco e solo.
     *
     * Piu' netto della goccia e molto piu' corto del tuono. Sono tre gesti
     * diversi e devono restare distinguibili a occhi chiusi, che e' l'unico
     * modo in cui un riscontro aptico porta informazione invece di rumore.
     */
    fun spin() {
        val v = vibrator ?: return
        val effect = when (mode) {
            Mode.PRIMITIVE -> VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, SPIN_SCALE)
                .compose()
            Mode.AMPLITUDE -> VibrationEffect.createOneShot(18, 120)
            Mode.PREDEFINED -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            Mode.BLUNT -> VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE)
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
        /** Su uno. Meno di un quarto: si sente col palmo, non col braccio. */
        const val DRIZZLE_SCALE = 0.22f

        /** Meta' scala: sopra la goccia, ben sotto il tuono. */
        const val SPIN_SCALE = 0.55f

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
