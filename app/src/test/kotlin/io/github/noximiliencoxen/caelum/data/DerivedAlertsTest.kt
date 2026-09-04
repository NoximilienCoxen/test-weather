package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Le allerte calcolate dalle soglie, che coprono dove MeteoAlarm non arriva.
 *
 * Il vincolo che conta e' l'ultimo di questa classe: **il rosso non si emette
 * per soglia**. Il rosso e' una dichiarazione di un ente, con dietro una
 * valutazione del rischio sul territorio - quanto regge un argine, dove sta la
 * gente - che un confronto fra un numero e una costante non puo' fare. Se un
 * giorno qualcuno alzasse una soglia fino a emetterlo, il test lo ferma.
 */
class DerivedAlertsTest {

    private fun forecastWith(vararg days: DayForecast) = Forecast(
        current = CurrentWeather(),
        days = days.toList(),
        place = Place(name = "Prova", latitude = 0.0, longitude = 0.0),
    )

    private fun day(offset: Int = 0, gust: Double? = null, rain: Double? = null) = DayForecast(
        date = LocalDate.of(2026, 9, 4).plusDays(offset.toLong()),
        label = "PROVA",
        gustMax = gust,
        precipitationSum = rain,
    )

    @Test
    fun `una giornata tranquilla non produce avvisi`() {
        assertEquals(emptyList<WeatherAlert>(), derivedAlerts(forecastWith(day(gust = 5.0))))
    }

    @Test
    fun `venti metri al secondo sono gia' una gialla`() {
        // 20 m/s sono 72 km/h.
        val alerts = derivedAlerts(forecastWith(day(gust = 21.0)))
        assertEquals(1, alerts.size)
        assertEquals(AlertKind.VENTO, alerts[0].kind)
        assertEquals(AlertLevel.GIALLA, alerts[0].level)
    }

    @Test
    fun `sopra i ventotto si passa all'arancione`() {
        val alerts = derivedAlerts(forecastWith(day(gust = 30.0)))
        assertEquals(AlertLevel.ARANCIONE, alerts.single().level)
    }

    @Test
    fun `guarda oggi e domani, non tutta la settimana`() {
        // Oltre il secondo giorno la previsione e' troppo incerta perche' valga
        // la pena di allarmare qualcuno.
        val alerts = derivedAlerts(
            forecastWith(day(0), day(1), day(2, gust = 40.0), day(3, gust = 40.0)),
        )
        assertTrue("non deve guardare oltre domani", alerts.isEmpty())
    }

    @Test
    fun `non si emette mai una rossa per soglia`() {
        // Numeri assurdi apposta: nemmeno cosi' deve uscirne una rossa.
        val estremo = derivedAlerts(
            forecastWith(day(0, gust = 200.0, rain = 900.0), day(1, gust = 200.0, rain = 900.0)),
        )
        assertTrue("qualcosa dovrebbe pur uscire", estremo.isNotEmpty())
        assertTrue(
            "il rosso e' una valutazione di un ente, non un confronto fra numeri",
            estremo.none { it.level == AlertLevel.ROSSA },
        )
    }

    @Test
    fun `le derivate si dichiarano non ufficiali`() {
        // E' la sola cosa che distingue un bollettino di un ente da una soglia
        // superata, e quella differenza appartiene a chi legge.
        val alerts = derivedAlerts(forecastWith(day(gust = 30.0)))
        assertTrue(alerts.none { it.official })
    }

    @Test
    fun `l'identificativo e' stabile fra due letture`() {
        // Se cambiasse a ogni caricamento, chiudere la fascia non funzionerebbe
        // mai: alertsAreDismissed confronta gli identificativi.
        val f = forecastWith(day(gust = 30.0))
        assertEquals(derivedAlerts(f).map { it.id }, derivedAlerts(f).map { it.id })
    }
}
