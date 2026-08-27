package com.forli.meteo.data

import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Il vento dell'ora mostrata, gia' tradotto in quello che serve al disegno.
 *
 * La conversione sta qui e non nella scena per un motivo preciso: la
 * convenzione meteorologica dice **da dove** il vento proviene, il disegno ha
 * bisogno di sapere **dove** spinge, e i due numeri sono a centottanta gradi
 * l'uno dall'altro. Sparpagliare quel segno meno fra pioggia, neve e nuvole
 * significa sbagliarlo in due posti su tre e accorgersene solo guardando la
 * neve salire.
 */
@Immutable
data class Wind(
    /** Metri al secondo. */
    val speed: Float,
    /** Gradi da cui proviene: 0 = da nord, 90 = da est. */
    val fromDegrees: Float,
) {

    /**
     * Quanto tira, da 0 a 1.
     *
     * Non lineare: fra bonaccia e brezza la differenza si deve vedere, fra
     * burrasca e tempesta non piu', perche' oltre un certo punto la pioggia e'
     * comunque orizzontale e non c'e' altro da raccontare.
     */
    val strength: Float = (speed / STRONG).coerceIn(0f, 1f).let { it * (2f - it) }

    /**
     * Spinta orizzontale sullo schermo: -1 verso sinistra, 1 verso destra.
     *
     * Lo schermo guarda a nord, quindi l'est del vento e' la destra di chi
     * guarda. Un vento che *proviene* da ovest (270 gradi) soffia verso est,
     * cioe' verso destra: da qui il segno meno davanti al seno.
     */
    val push: Float = -sin(fromDegrees * DEG) * strength

    /**
     * Componente in profondita': un vento che soffia verso l'osservatore non
     * puo' spostare le cose di lato, ma puo' ancora far ondeggiare le nuvole.
     */
    val depth: Float = cos(fromDegrees * DEG) * strength

    companion object {
        /** Metri al secondo oltre i quali si considera "tanto". Circa 50 km/h. */
        const val STRONG = 14f

        val CALMA = Wind(0f, 0f)

        fun of(speed: Double?, fromDegrees: Double?): Wind {
            if (speed == null) return CALMA
            return Wind(
                speed = speed.toFloat().coerceAtLeast(0f),
                fromDegrees = (fromDegrees ?: 0.0).toFloat(),
            )
        }

        private const val DEG = (PI / 180.0).toFloat()
    }
}
