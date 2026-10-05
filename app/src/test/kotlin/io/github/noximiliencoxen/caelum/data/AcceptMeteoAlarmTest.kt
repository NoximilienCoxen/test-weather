package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'intestazione `Accept` delle richieste a MeteoAlarm.
 *
 * Con i soli tipi XML il server rispondeva `406 Not Acceptable` a ogni
 * richiesta, e l'app non ha mai mostrato un'allerta ufficiale: lo ha detto il
 * logcat della cattura del 5 ottobre 2026, con un'allerta gialla per temporali
 * sull'Emilia e Romagna nel feed. Il jolly e' cio' che lo impedisce, e questo
 * test e' cio' che impedisce di toglierlo "per pulizia".
 */
class AcceptMeteoAlarmTest {

    @Test
    fun `il feed accetta anche il jolly`() {
        assertTrue(WeatherAlertsRepository.FEED_ACCEPT.contains("*/*"))
        assertTrue(WeatherAlertsRepository.FEED_ACCEPT.startsWith("application/atom+xml"))
    }

    @Test
    fun `il documento CAP accetta anche il jolly`() {
        assertTrue(WeatherAlertsRepository.CAP_ACCEPT.contains("*/*"))
        assertTrue(WeatherAlertsRepository.CAP_ACCEPT.startsWith("application/cap+xml"))
    }
}
