package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class PrecipitazioneInArrivoTest {

    private val adesso = LocalDateTime.of(2026, 9, 24, 14, 5)

    private fun quarti(vararg v: Pair<Double, Int>): List<QuartoDOra> =
        v.mapIndexed { i, (mm, codice) ->
            QuartoDOra(LocalDateTime.of(2026, 9, 24, 14, 0).plusMinutes(15L * i), mm, codice)
        }

    @Test
    fun `asciutto adesso e pioggia fra mezz'ora`() {
        val e = precipitazioneInArrivo(quarti(0.0 to 3, 0.0 to 3, 0.6 to 61, 0.9 to 63), adesso)!!
        assertEquals(TipoPrecipitazione.PIOGGIA, e.tipo)
        assertEquals(LocalDateTime.of(2026, 9, 24, 14, 30), e.inizio)
        assertEquals(1.5, e.millimetri, 1e-9)
    }

    @Test
    fun `se piove gia' non e' in arrivo`() {
        assertNull(precipitazioneInArrivo(quarti(0.8 to 61, 0.0 to 3, 1.0 to 63), adesso))
    }

    @Test
    fun `la grandine vince anche se comincia con due gocce`() {
        val e = precipitazioneInArrivo(quarti(0.0 to 2, 0.3 to 61, 2.0 to 96), adesso)!!
        assertEquals(TipoPrecipitazione.GRANDINE, e.tipo)
        assertEquals(LocalDateTime.of(2026, 9, 24, 14, 15), e.inizio)
    }

    @Test
    fun `la pioviggine sotto soglia non conta`() {
        assertNull(precipitazioneInArrivo(quarti(0.0 to 3, 0.1 to 51, 0.1 to 51), adesso))
    }

    @Test
    fun `oltre l'ora non si avvisa`() {
        assertNull(precipitazioneInArrivo(quarti(0.0 to 3, 0.0 to 3, 0.0 to 3, 0.0 to 3, 0.0 to 3, 0.0 to 3, 2.0 to 65), adesso))
    }

    @Test
    fun `la risposta si legge nel fuso della citta'`() {
        val body = """{"utc_offset_seconds":7200,"minutely_15":{"time":["2026-09-24T14:00","2026-09-24T14:15"],""" +
            """"precipitation":[0.0,1.2],"weather_code":[3,63]}}"""
        val r = PrevisioneABreve.leggi(body, Instant.parse("2026-09-24T12:05:00Z"))
        assertEquals(LocalDateTime.of(2026, 9, 24, 14, 5), r.adesso)
        assertEquals(2, r.quarti.size)
        assertEquals(TipoPrecipitazione.PIOGGIA, precipitazioneInArrivo(r.quarti, r.adesso)!!.tipo)
    }
}
