package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Il difetto che questi casi tengono fermo e' uno solo, e non si vede da uno
 * scatto: **un numero giusto su una scala sbagliata e' un numero sbagliato.**
 * L'app chiedeva il solo indice europeo e lo mostrava anche a Tokyo.
 */
class AirScaleTest {

    @Test
    fun `lo stesso numero non vuol dire la stessa cosa sulle due scale`() {
        // E' tutta qui la ragione per cui la scala va scritta accanto al
        // numero: cinquanta e' aria mediocre in Europa e aria buona negli
        // Stati Uniti - due bande di distanza - e senza etichetta chi legge non
        // ha modo di saperlo.
        assertEquals(AirBand.MEDIA, AirScale.EUROPEA.band(50))
        assertEquals(AirBand.BUONA, AirScale.STATUNITENSE.band(50))
        assertNotEquals(AirScale.EUROPEA.label, AirScale.STATUNITENSE.label)
    }

    @Test
    fun `le bande europee cadono di venti in venti`() {
        assertEquals(AirBand.BUONA, AirScale.EUROPEA.band(20))
        assertEquals(AirBand.DISCRETA, AirScale.EUROPEA.band(21))
        assertEquals(AirBand.MEDIA, AirScale.EUROPEA.band(60))
        assertEquals(AirBand.SCARSA, AirScale.EUROPEA.band(80))
        assertEquals(AirBand.MOLTO_SCARSA, AirScale.EUROPEA.band(100))
        assertEquals(AirBand.ESTREMAMENTE_SCARSA, AirScale.EUROPEA.band(101))
    }

    @Test
    fun `le bande americane cadono dove le mette l'EPA`() {
        assertEquals(AirBand.BUONA, AirScale.STATUNITENSE.band(50))
        assertEquals(AirBand.DISCRETA, AirScale.STATUNITENSE.band(100))
        assertEquals(AirBand.MEDIA, AirScale.STATUNITENSE.band(150))
        assertEquals(AirBand.SCARSA, AirScale.STATUNITENSE.band(200))
        assertEquals(AirBand.MOLTO_SCARSA, AirScale.STATUNITENSE.band(300))
        assertEquals(AirBand.ESTREMAMENTE_SCARSA, AirScale.STATUNITENSE.band(301))
    }

    @Test
    fun `senza indice non si inventa una banda`() {
        assertNull(AirScale.EUROPEA.band(null))
        assertNull(AirScale.STATUNITENSE.band(null))
        assertNull(AirQuality(null, AirScale.EUROPEA, null, null).band)
    }

    @Test
    fun `la vecchia scorciatoia resta la scala europea`() {
        // `AirBand.of` la chiama ancora chi ha in mano solo un numero: deve
        // restare quello che era, se no cambierebbe di nascosto il giudizio del
        // widget.
        assertEquals(AirScale.EUROPEA.band(45), AirBand.of(45))
    }

    @Test
    fun `il giudizio viene dalla scala che la misura porta con se'`() {
        val tokyo = AirQuality(index = 50, scale = AirScale.STATUNITENSE, pm25 = null, pm10 = null)
        val forli = AirQuality(index = 50, scale = AirScale.EUROPEA, pm25 = null, pm10 = null)
        assertEquals(AirBand.BUONA, tokyo.band)
        assertEquals(AirBand.MEDIA, forli.band)
    }
}
