package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Il lettore della JMA, contro risposte vere.
 *
 * `jma-hiroshima.xml` e' il bollettino VPWW53 di Hiroshima del 5 ottobre 2026
 * (un 濃霧注意報, avvertenza per nebbia fitta, nel nord della prefettura);
 * `jma-feed-lungo-estratto.xml` sono le prime voci del feed lungo dello
 * stesso giorno piu' quelle di Hiroshima. Robolectric per `android.util.Xml`,
 * come `ParseFeedTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JmaAlertsTest {

    private fun risorsa(nome: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(nome)).bufferedReader().readText()

    private val hiroshima = Place("Hiroshima", "Hiroshima", "Giappone", 34.396, 132.459)

    @Test
    fun `il bollettino piu' recente dell'area e' il primo del feed`() {
        val feed = risorsa("jma-feed-lungo-estratto.xml")
        assertEquals(
            "https://www.data.jma.go.jp/developer/xml/data/20261005110359_0_VPWW53_340000.xml",
            JmaAlerts.ultimoBollettino(feed, "340000"),
        )
        assertNull(JmaAlerts.ultimoBollettino(feed, "999999"))
    }

    @Test
    fun `legge l'avvertenza di prefettura e non quelle dei comuni`() {
        val allerte = JmaAlerts.parse(risorsa("jma-hiroshima.xml"), JmaAlerts.areaDi(hiroshima))
        assertEquals(1, allerte.size)
        val a = allerte.single()
        assertEquals(AlertLevel.GIALLA, a.level)
        assertEquals(AlertKind.NEBBIA, a.kind)
        assertEquals(FonteAllerte.JMA, a.fonte)
        assertTrue(a.description!!.startsWith("濃霧注意報"))
        assertEquals("Hiroshima · 広島県", a.areaDesc)
        assertTrue(a.source.endsWith("広島地方気象台"))
        assertNull(a.onset)
        assertNull(a.expires)
    }

    @Test
    fun `il grado sta in fondo al nome`() {
        assertEquals(AlertLevel.ROSSA, JmaAlerts.livelloDi("大雨特別警報"))
        assertEquals(AlertLevel.ARANCIONE, JmaAlerts.livelloDi("暴風警報"))
        assertEquals(AlertLevel.GIALLA, JmaAlerts.livelloDi("雷注意報"))
    }

    @Test
    fun `il fenomeno dai caratteri`() {
        assertEquals(AlertKind.TEMPORALI, JmaAlerts.tipoDi("雷注意報"))
        assertEquals(AlertKind.NEVE_GHIACCIO, JmaAlerts.tipoDi("暴風雪警報"))
        assertEquals(AlertKind.VENTO, JmaAlerts.tipoDi("暴風警報"))
        assertEquals(AlertKind.PIOGGIA, JmaAlerts.tipoDi("大雨警報"))
        assertEquals(AlertKind.COSTIERO, JmaAlerts.tipoDi("波浪注意報"))
        assertEquals(AlertKind.INCENDI, JmaAlerts.tipoDi("乾燥注意報"))
    }

    @Test
    fun `l'area dalla regione, e dalla sede piu' vicina dove una non basta`() {
        assertEquals("130000", JmaAlerts.areaDi(Place("Tokyo", "Tokyo", "Giappone", 35.69, 139.69)).codice)
        assertEquals("270000", JmaAlerts.areaDi(Place("Osaka", "prefettura di Osaka", "Giappone", 34.69, 135.50)).codice)
        // Hokkaido ha otto aree: Sapporo cade in Ishikari, Kushiro nella sua.
        assertEquals("016000", JmaAlerts.areaDi(Place("Sapporo", "Hokkaido", "Giappone", 43.07, 141.35)).codice)
        assertEquals("014100", JmaAlerts.areaDi(Place("Kushiro", "Hokkaido", "Giappone", 42.98, 144.38)).codice)
        // Senza regione decide la distanza.
        assertEquals("340000", JmaAlerts.areaDi(Place("Hiroshima", null, "Giappone", 34.40, 132.46)).codice)
    }

    @Test
    fun `il Giappone va alla JMA`() {
        assertEquals(FonteAllerte.JMA, WeatherAlertsRepository.fonteDi("Giappone"))
        assertEquals(FonteAllerte.JMA, WeatherAlertsRepository.fonteDi("Japan"))
    }
}
