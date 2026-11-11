package com.caelumweather.lingua

import com.caelumweather.ui.scene.Scena
import com.caelumweather.prefs.SalaWindUnit
import com.caelumweather.data.AlertKind
import com.caelumweather.data.AlertLevel
import com.caelumweather.data.PrecipitazioneInArrivo
import com.caelumweather.data.TipoPrecipitazione
import com.caelumweather.data.WeatherAlert
import com.caelumweather.data.Wmo
import com.caelumweather.data.shortBadge
import com.caelumweather.notifiche.testiAllerta
import com.caelumweather.notifiche.testiNotifica
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Le due lingue (CONTESTO §49): in italiano tutto come prima, in inglese le
 * stesse cose dette in inglese - comprese quelle che cambiano forma, non solo
 * parole (la virgola dei decimali, l'ovest che diventa W).
 */
class LinguaTest {

    @After
    fun ripristina() {
        Lingue.forzata = null
    }

    @Test
    fun `le prove parlano italiano di serie`() {
        // Lo impone il build.gradle: senza, i runner in inglese romperebbero
        // ogni prova scritta sui testi italiani.
        assertEquals(Lingua.ITALIANO, Lingue.corrente)
        assertEquals("SERENO", Wmo.condition(0))
    }

    @Test
    fun `in inglese i dati cambiano parole e forma`() {
        Lingue.forzata = Lingua.INGLESE
        assertEquals("CLEAR", Wmo.condition(0))
        assertEquals("W", Wmo.windDirection(270.0))
        assertEquals("ORANGE WARNING", AlertLevel.ARANCIONE.label)
        assertEquals("THUNDERSTORMS", AlertKind.TEMPORALI.label)
    }

    @Test
    fun `tedesco, francese e spagnolo hanno le loro parole`() {
        Lingue.forzata = Lingua.TEDESCO
        assertEquals("KLAR", Wmo.condition(0))
        assertEquals("WARNSTUFE ORANGE", AlertLevel.ARANCIONE.label)
        assertEquals("GEWITTER", AlertKind.TEMPORALI.label)
        Lingue.forzata = Lingua.FRANCESE
        assertEquals("DÉGAGÉ", Wmo.condition(0))
        assertEquals("ALERTE ORANGE", AlertLevel.ARANCIONE.label)
        assertEquals("ORAGES", AlertKind.TEMPORALI.label)
        Lingue.forzata = Lingua.SPAGNOLO
        assertEquals("DESPEJADO", Wmo.condition(0))
        assertEquals("ALERTA NARANJA", AlertLevel.ARANCIONE.label)
        assertEquals("TORMENTAS", AlertKind.TEMPORALI.label)
    }

    @Test
    fun `la O e' l'ovest in tre lingue e l'est in tedesco`() {
        Lingue.forzata = Lingua.TEDESCO
        assertEquals("O", Wmo.windDirection(90.0))
        assertEquals("W", Wmo.windDirection(270.0))
        assertEquals("SW", Wmo.windDirection(225.0))
        Lingue.forzata = Lingua.FRANCESE
        assertEquals("E", Wmo.windDirection(90.0))
        assertEquals("O", Wmo.windDirection(270.0))
        Lingue.forzata = Lingua.SPAGNOLO
        assertEquals("O", Wmo.windDirection(270.0))
        assertEquals("SO", Wmo.windDirection(225.0))
    }

    @Test
    fun `in mezzo a una frase il tedesco tiene le maiuscole dei nomi`() {
        Lingue.forzata = Lingua.TEDESCO
        assertEquals("Gewitter", "GEWITTER".inMezzoAllaFrase())
        assertEquals("Schnee und Eis", "SCHNEE UND EIS".inMezzoAllaFrase())
        assertEquals("UV-Strahlung", "UV-STRAHLUNG".inMezzoAllaFrase())
        assertEquals("meist klar", "MEIST KLAR".inMezzoAllaFrase())
        assertEquals("gefrierender Regen", "GEFRIERENDER REGEN".inMezzoAllaFrase())
        assertEquals("Gelb", "GELB".inMezzoAllaFrase())
        // Cio' che non e' tutto maiuscolo e' gia' scritto come va scritto.
        assertEquals("Gräser", "Gräser".inMezzoAllaFrase())
        Lingue.forzata = Lingua.FRANCESE
        assertEquals("orages", "ORAGES".inMezzoAllaFrase())
        Lingue.forzata = Lingua.ITALIANO
        assertEquals("temporali", "TEMPORALI".inMezzoAllaFrase())
    }

    @Test
    fun `una lingua senza la sua frase legge l'inglese`() {
        Lingue.forzata = Lingua.TEDESCO
        assertEquals("solo inglese", tr("solo italiano", "solo inglese"))
        assertEquals("deutsch", tr("it", "en", "deutsch", "français", "español"))
        Lingue.forzata = Lingua.SPAGNOLO
        assertEquals("español", tr("it", "en", "deutsch", "français", "español"))
    }

    @Test
    fun `il codice dice la lingua, anche con la regione`() {
        assertEquals(Lingua.TEDESCO, Lingua.diCodice("de-AT"))
        assertEquals(Lingua.SPAGNOLO, Lingua.diCodice("ES"))
        assertEquals(null, Lingua.diCodice("nl"))
        assertEquals(null, Lingua.diCodice(null))
        assertEquals(SceltaLingua.FRANCESE, SceltaLingua.di(Lingua.FRANCESE))
        assertEquals(SceltaLingua.AUTOMATICA, SceltaLingua.di(null))
    }

    @Test
    fun `le notifiche nelle tre lingue nuove tengono numeri e nomi`() {
        val adesso = LocalDateTime.of(2026, 1, 14, 15, 20)
        val evento = com.caelumweather.notifiche.SogliaSuperata(
            com.caelumweather.prefs.TipoSoglia.GELO, adesso.plusDays(1).withHour(5).withMinute(0), -2.3, 0,
        )
        fun testi() = com.caelumweather.notifiche.testiSoglia(
            "Forlì", evento, adesso, com.caelumweather.prefs.TempUnit.CELSIUS, SalaWindUnit.KMH,
        )
        Lingue.forzata = Lingua.TEDESCO
        assertEquals("Frost in Forlì: Tiefstwert -2°", testi().first)
        assertEquals("Erwartet morgen gegen 5 Uhr. Deine Schwelle: 0°. Das ist eine Vorhersage, keine amtliche Warnung.", testi().second)
        Lingue.forzata = Lingua.FRANCESE
        assertEquals("Gel à Forlì : minimale de -2°", testi().first)
        assertEquals("Prévue demain vers 5 h. Votre seuil : 0°. C'est une prévision, pas une alerte officielle.", testi().second)
        Lingue.forzata = Lingua.SPAGNOLO
        assertEquals("Helada en Forlì: mínima de -2°", testi().first)
        assertEquals("Prevista mañana hacia las 5. Tu umbral: 0°. Es una previsión, no una alerta oficial.", testi().second)
    }

    @Test
    fun `la pastiglia di un'allerta in inglese dice il colore, senza prefisso`() {
        Lingue.forzata = Lingua.INGLESE
        val allerta = WeatherAlert(
            id = "x", level = AlertLevel.ROSSA, kind = AlertKind.VENTO,
            headline = "", source = "MeteoAlarm", official = true,
        )
        assertEquals("RED", allerta.shortBadge)
    }

    @Test
    fun `le notifiche in inglese, col punto nei decimali`() {
        Lingue.forzata = Lingua.INGLESE
        val adesso = LocalDateTime.of(2026, 10, 2, 14, 5)
        val (titolo, testo) = testiNotifica(
            "Noceto",
            PrecipitazioneInArrivo(TipoPrecipitazione.PIOGGIA, LocalDateTime.of(2026, 10, 2, 14, 30), 1.5),
            adesso,
        )
        assertEquals("Rain on the way in Noceto", titolo)
        assertTrue(testo, testo.contains("in about 25 minutes"))
        assertTrue(testo, testo.contains("1.5 mm"))

        val allerta = WeatherAlert(
            id = "y", level = AlertLevel.ARANCIONE, kind = AlertKind.TEMPORALI,
            headline = "", source = "MeteoAlarm", official = true,
            onset = adesso.minusHours(1), expires = adesso.plusHours(8),
        )
        val (titoloA, testoA) = testiAllerta("Forlì", allerta, adesso)
        assertEquals("Orange warning for Forlì: thunderstorms", titoloA)
        assertTrue(testoA, testoA.startsWith("In force until 22:05 today."))
    }

    @Test
    fun `l'unita' del vento e le scene cambiano nome, l'italiano resta com'era`() {
        assertEquals("nodi", SalaWindUnit.KN.label)
        assertEquals("Sole e mare", Scena.MARE.nome)
        Lingue.forzata = Lingua.INGLESE
        assertEquals("kn", SalaWindUnit.KN.label)
        assertEquals("km/h", SalaWindUnit.KMH.label)
        assertEquals("Sun and sea", Scena.MARE.nome)
    }
}
