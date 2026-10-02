package io.github.noximiliencoxen.caelum.lingua

import io.github.noximiliencoxen.caelum.ui.scene.Scena
import io.github.noximiliencoxen.caelum.prefs.SalaWindUnit
import io.github.noximiliencoxen.caelum.data.AlertKind
import io.github.noximiliencoxen.caelum.data.AlertLevel
import io.github.noximiliencoxen.caelum.data.PrecipitazioneInArrivo
import io.github.noximiliencoxen.caelum.data.TipoPrecipitazione
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.data.shortBadge
import io.github.noximiliencoxen.caelum.notifiche.testiAllerta
import io.github.noximiliencoxen.caelum.notifiche.testiNotifica
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
