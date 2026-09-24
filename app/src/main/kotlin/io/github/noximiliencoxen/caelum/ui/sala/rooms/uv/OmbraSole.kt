package io.github.noximiliencoxen.caelum.ui.sala.rooms.uv

import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Quanto e' alto il sole, in gradi veri, e quanto e' lunga l'ombra che fa.
 *
 * `SunClock.altitude` non basta: e' un numero normalizzato fra alba e tramonto,
 * buono per scegliere i colori del cielo e sbagliato per un'ombra, che dipende
 * dall'angolo vero. Qui c'e' la formula solare classica - declinazione,
 * equazione del tempo, angolo orario - che sbaglia di qualche decimo di grado,
 * cioe' molto meno di quanto un'ombra disegnata possa mostrare.
 *
 * Niente Android: si prova sulla JVM (`OmbraSoleTest`).
 */
internal object OmbraSole {

    /**
     * L'altezza del sole sull'orizzonte, in gradi, negativa di notte.
     *
     * @param orologio l'ora sull'orologio del posto, come la da' la previsione.
     * @param scartoUtcSecondi lo scarto dell'orologio del posto dall'ora
     *   universale, che la previsione porta con se' (`Forecast.utcOffsetSeconds`).
     */
    fun altezza(orologio: LocalDateTime, latitudine: Double, longitudine: Double, scartoUtcSecondi: Int): Double {
        val giorno = orologio.dayOfYear
        val declinazione = 23.44 * sin(RAD * 360.0 / 365.0 * (284 + giorno))
        val b = RAD * 360.0 / 365.0 * (giorno - 81)
        val equazioneMinuti = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
        val meridiano = scartoUtcSecondi / 3600.0 * 15.0
        val minutiOrologio = orologio.hour * 60 + orologio.minute + orologio.second / 60.0
        val minutiSolari = minutiOrologio + 4.0 * (longitudine - meridiano) + equazioneMinuti
        val angoloOrario = (minutiSolari / 60.0 - 12.0) * 15.0
        val phi = latitudine * RAD
        val delta = declinazione * RAD
        val sinAlt = sin(phi) * sin(delta) + cos(phi) * cos(delta) * cos(angoloOrario * RAD)
        return asin(sinAlt.coerceIn(-1.0, 1.0)) / RAD
    }

    /**
     * Quanto e' lunga l'ombra rispetto all'altezza di chi la fa: 1 vuol dire
     * lunga quanto la persona. Nulla se il sole non c'e'. Col sole appena
     * sopra l'orizzonte l'ombra tende all'infinito: si ferma a [MASSIMA], oltre
     * la quale non starebbe comunque nel disegno.
     */
    fun lunghezzaRelativa(altezzaGradi: Double): Double? {
        if (altezzaGradi <= 0.0) return null
        return (1.0 / tan(altezzaGradi * RAD)).coerceAtMost(MASSIMA)
    }

    const val MASSIMA = 8.0

    private const val RAD = PI / 180.0
}
