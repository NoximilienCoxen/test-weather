package com.forli.meteo.ui.motion

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Il segnale che il giro si e' chiuso.
 *
 * Girando la cifra con un colpo deciso, la molla la riporta al giro intero piu'
 * vicino. Quando quel giro e' davvero **un giro** - e non un'oscillazione che
 * torna al punto di partenza - c'e' un momento preciso in cui l'oggetto si
 * riappoggia, e finora quel momento non si sentiva. Un tocco e uno schiocco
 * brevissimo bastano a farlo esistere.
 *
 * Discreto e' un requisito, non un'aspirazione: questo suono puo' capitare
 * decine di volte di fila mentre si gioca con la cifra, e qualunque cosa duri
 * piu' di un battito di ciglia diventa molesta al terzo.
 */
class SpinFeedback(
    private val haptics: WeatherHaptics,
    private val track: AudioTrack?,
    private val samples: ShortArray?,
) {

    fun landed() {
        haptics.spin()
        val audio = track ?: return
        val data = samples ?: return
        runCatching {
            // Riavvolgere e riscrivere invece di allocare: il campione e'
            // sempre lo stesso, e generarlo a ogni giro vorrebbe dire fare
            // qualche migliaio di seni nel momento esatto in cui la molla si
            // sta ancora animando.
            audio.stop()
            audio.reloadStaticData()
            audio.play()
        }
    }

    internal fun release() {
        runCatching {
            track?.stop()
            track?.release()
        }
    }
}

@Composable
fun rememberSpinFeedback(): SpinFeedback {
    val context = LocalContext.current
    val haptics = rememberWeatherHaptics()
    val feedback = remember(haptics) {
        val samples = buildClick()
        SpinFeedback(haptics, buildTrack(samples), samples)
    }
    DisposableEffect(feedback) {
        onDispose { feedback.release() }
    }
    return feedback
}

/**
 * Lo schiocco, calcolato invece che registrato.
 *
 * Nessun file audio: sarebbe un allegato da versionare, da decodificare e da
 * tenere in memoria per settanta millisecondi di suono che si scrivono in dodici
 * righe. Due sinusoidi vicine ma non armoniche fra loro, spente da un
 * esponenziale ripido: e' la ricetta di un corpo rigido che si appoggia. Con una
 * sola frequenza si otterrebbe un bip da orologio digitale; con due che battono
 * fra loro esce un legno.
 */
private fun buildClick(): ShortArray {
    val count = (SAMPLE_RATE * CLICK_SECONDS).toInt()
    val data = ShortArray(count)
    for (i in 0 until count) {
        val t = i / SAMPLE_RATE.toFloat()
        // L'attacco non e' istantaneo: un fronte verticale produce uno scoppio
        // che l'altoparlante di un telefono restituisce come un fruscio.
        val attack = (t / ATTACK_SECONDS).coerceAtMost(1f)
        val decay = exp(-t / DECAY_SECONDS)
        val body = sin(TWO_PI * LOW_HZ * t) * 0.6f + sin(TWO_PI * HIGH_HZ * t) * 0.4f
        data[i] = (body * attack * decay * PEAK * Short.MAX_VALUE).toInt().toShort()
    }
    return data
}

private fun buildTrack(samples: ShortArray): AudioTrack? = runCatching {
    AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                // Sonificazione e non musica: cosi' segue il volume delle
                // notifiche di sistema, tace quando il telefono e' in
                // silenzioso, e non interrompe quello che si sta ascoltando.
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setBufferSizeInBytes(samples.size * 2)
        // Statica: il campione sta nel buffer una volta sola e si rilancia da
        // capo. In modalita' a flusso servirebbe un filo che lo riscriva a ogni
        // colpo, per settanta millisecondi di suono.
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
        .also { it.write(samples, 0, samples.size) }
}.getOrNull()

private const val SAMPLE_RATE = 22_050
private const val CLICK_SECONDS = 0.075f
private const val ATTACK_SECONDS = 0.0016f
private const val DECAY_SECONDS = 0.019f

/** Le due frequenze del corpo. Volutamente non in rapporto semplice fra loro. */
private const val LOW_HZ = 523f
private const val HIGH_HZ = 787f

/** Un quinto di fondo scala. Deve sentirsi, non annunciarsi. */
private const val PEAK = 0.20f

private const val TWO_PI = (2.0 * PI).toFloat()
