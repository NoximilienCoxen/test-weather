package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Come un avviso ha diritto di presentarsi.
 *
 * Il vincolo che questa classe difende e' uno solo, e non e' estetico: **le
 * parole "gialla", "arancione" e "rossa" appartengono ai bollettini
 * ufficiali**. Sono i gradini del sistema di allertamento nazionale, e un
 * avviso nato da un confronto fra una raffica e una costante che se le
 * prendesse direbbe, a chi legge, che a diramarlo e' stata la Protezione
 * Civile.
 *
 * Il caso pericoloso e' quello al contrario: chi vede un avviso calcolato senza
 * accorgersene da' per buono che l'ente si sia gia' pronunciato, e - non
 * vedendo nessun'altra fascia - che non abbia niente da dire.
 *
 * Se un giorno qualcuno rimettesse `level.label` dentro la fascia o il pallino,
 * questi test cadono.
 */
class AlertBadgeTest {

    private val COLORI = listOf("GIALL", "ARANCION", "ROSS")

    private fun derivata(level: AlertLevel = AlertLevel.GIALLA) = WeatherAlert(
        id = "derivata-VENTO-0",
        level = level,
        kind = AlertKind.VENTO,
        headline = "Vento forte oggi",
        source = "Calcolata dai dati Open-Meteo",
        official = false,
    )

    private fun ufficiale(level: AlertLevel = AlertLevel.ARANCIONE) = WeatherAlert(
        id = "meteoalarm-1",
        level = level,
        kind = AlertKind.TEMPORALI,
        headline = "Allerta arancione: temporali",
        source = "MeteoAlarm - Protezione Civile",
        official = true,
    )

    @Test
    fun `un avviso calcolato non porta nessuno dei tre colori`() {
        AlertLevel.entries.forEach { level ->
            val alert = derivata(level)
            COLORI.forEach { colore ->
                assertFalse(
                    "badgeLabel di ${level.name} contiene $colore",
                    alert.badgeLabel.contains(colore),
                )
                assertFalse(
                    "shortBadge di ${level.name} contiene $colore",
                    alert.shortBadge.contains(colore),
                )
            }
        }
    }

    @Test
    fun `un avviso calcolato dice che e' una soglia`() {
        assertEquals(SOGLIA_LABEL, derivata().badgeLabel)
        assertEquals(SOGLIA_SHORT, derivata().shortBadge)
    }

    @Test
    fun `un bollettino ufficiale tiene il suo colore`() {
        assertEquals("ALLERTA ARANCIONE", ufficiale().badgeLabel)
        // La fascia ha una riga sola: il "ALLERTA " davanti se n'e' andato per
        // fare spazio, e il colore - che e' il pezzo per cui la fascia esiste -
        // resta.
        assertEquals("ARANCIONE", ufficiale().shortBadge)
    }

    /**
     * Le allerte che l'app calcola davvero, non quelle costruite a mano qui
     * sopra: se domani `derivedAlerts` cominciasse a marcarne una `official`,
     * il difetto passerebbe da tutti i test tranne questo.
     */
    @Test
    fun `nessuna allerta prodotta dalle soglie si dichiara ufficiale`() {
        val forecast = Forecast(
            current = CurrentWeather(),
            days = listOf(
                DayForecast(
                    date = LocalDate.of(2026, 9, 4),
                    label = "PROVA",
                    gustMax = 30.0,
                    precipitationSum = 80.0,
                    snowfallSum = 20.0,
                ),
            ),
            place = Place(name = "Prova", latitude = 0.0, longitude = 0.0),
        )
        val derivate = derivedAlerts(forecast)
        assertTrue("le soglie non hanno prodotto niente", derivate.isNotEmpty())
        derivate.forEach { alert ->
            assertFalse("${alert.id} si dichiara ufficiale", alert.official)
            COLORI.forEach { colore ->
                assertFalse(
                    "${alert.id} si annuncia come $colore",
                    alert.badgeLabel.contains(colore),
                )
            }
        }
    }
}
