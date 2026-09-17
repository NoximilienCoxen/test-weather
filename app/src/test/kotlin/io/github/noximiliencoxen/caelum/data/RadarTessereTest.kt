package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'aritmetica delle tessere, provata contro numeri che si possono verificare
 * a mano.
 *
 * **E' l'unica parte del radar che un test possa toccare**, e adesso e' anche
 * quella dove un errore sarebbe invisibile: una tessera posata di mezzo grado
 * piu' a nord non si vede come un guasto, si vede come pioggia che cade
 * altrove. La riga sotto la carta direbbe "Radar: RainViewer", la carta
 * sarebbe bella, e la macchia sarebbe sul paese sbagliato.
 *
 * Il file che questo sostituisce - `RadarFormaTest` - provava un lettore
 * scritto per un servizio che non ha mai risposto. E' andato via col lettore.
 */
class RadarTessereTest {

    // ── La griglia, sui punti che si conoscono a memoria ─────────────────────

    @Test
    fun `l'origine della griglia e' l'angolo in alto a sinistra del mondo`() {
        // Colonna 0 e riga 0 sono l'angolo nord-ovest: longitudine -180,
        // latitudine al limite di Mercatore. E' la convenzione di ogni mappa a
        // tessere, e se un giorno cambiasse verso tutto il resto seguirebbe.
        val r = RadarTessere.riquadroDi(0, 0)
        assertEquals(-180.0, r.lonMin, 0.0001)
        assertEquals(RadarTessere.LIMITE_MERCATORE, r.latMax, 0.001)
    }

    @Test
    fun `il meridiano di Greenwich e l'equatore cadono in mezzo`() {
        val quante = 1 shl RadarTessere.ZOOM
        assertEquals(quante / 2, RadarTessere.colonna(0.0))
        assertEquals(quante / 2, RadarTessere.riga(0.0))
    }

    @Test
    fun `ogni tessera contiene il punto da cui e' stata trovata`() {
        // La prova che lega le tre funzioni fra loro: da un punto si ricava la
        // tessera, dalla tessera il riquadro, e il punto ci deve stare dentro.
        // Se una delle tre sbagliasse di un indice, questa cadrebbe.
        listOf(
            44.2226 to 12.0407, // Forli'
            35.7 to 139.6875, // Tokyo
            -1.3 to 36.82, // Nairobi, sotto l'equatore
            64.13 to -21.89, // Reykjavik, longitudine negativa
            -43.59 to 170.14, // Aoraki, l'angolo opposto del mondo
        ).forEach { (lat, lon) ->
            val r = RadarTessere.riquadroDi(RadarTessere.colonna(lon), RadarTessere.riga(lat))
            assertTrue("$lat,$lon fuori da $r", r.contiene(lat, lon))
        }
    }

    @Test
    fun `le tessere di una riga si toccano senza sovrapporsi`() {
        val c = RadarTessere.colonna(12.0)
        val a = RadarTessere.riquadroDi(c, 40)
        val b = RadarTessere.riquadroDi(c + 1, 40)
        assertEquals(a.lonMax, b.lonMin, 0.0000001)
    }

    @Test
    fun `le tessere di una colonna si toccano senza sovrapporsi`() {
        val r = RadarTessere.riga(44.0)
        val sopra = RadarTessere.riquadroDi(60, r)
        val sotto = RadarTessere.riquadroDi(60, r + 1)
        assertEquals(sopra.latMin, sotto.latMax, 0.0000001)
    }

    // ── Mercatore ───────────────────────────────────────────────────────────

    @Test
    fun `mercatore e' zero all'equatore e cresce verso nord`() {
        assertEquals(0.0, RadarTessere.mercatore(0.0), 0.0000001)
        assertTrue(RadarTessere.mercatore(45.0) > 0.0)
        assertTrue(RadarTessere.mercatore(-45.0) < 0.0)
        assertTrue(RadarTessere.mercatore(60.0) > RadarTessere.mercatore(45.0))
    }

    @Test
    fun `mercatore non esplode ai poli`() {
        // Senza il taglio al limite, la tangente a novanta gradi e' infinita e
        // il logaritmo di infinito pure: la carta diventerebbe un `NaN` che si
        // propaga a ogni coordinata disegnata.
        listOf(90.0, -90.0, 89.999, 1000.0).forEach { lat ->
            val y = RadarTessere.mercatore(lat)
            assertTrue("$lat -> $y", y.isFinite())
        }
    }

    @Test
    fun `sopra l'equatore Mercatore stira, e per questo non e' lineare`() {
        // Un grado a sessanta di latitudine occupa piu' spazio verticale di un
        // grado a zero. E' esattamente la ragione per cui la carta ha smesso
        // di trattare la latitudine come lineare quando sono arrivate le
        // tessere: posate su una carta lineare, sarebbero stirate di questo.
        val allEquatore = RadarTessere.mercatore(1.0) - RadarTessere.mercatore(0.0)
        val aSessanta = RadarTessere.mercatore(61.0) - RadarTessere.mercatore(60.0)
        assertTrue(aSessanta > allEquatore * 1.5)
    }

    // ── La copertura di una finestra ─────────────────────────────────────────

    @Test
    fun `una finestra normale chiede una manciata di tessere`() {
        val f = RadarTessere.finestraDaChiedere(44.2226, 12.0407)
        val quali = RadarTessere.coprono(f)
        assertTrue("${quali.size}", quali.size in 2..24)
    }

    @Test
    fun `le tessere chieste coprono davvero la finestra, ovunque nel mondo`() {
        listOf(
            44.2226 to 12.0407,
            35.7 to 139.6875,
            -1.3 to 36.82,
            64.13 to -21.89,
            -43.59 to 170.14,
            60.39 to 5.32,
        ).forEach { (lat, lon) -> angoliCoperti(lat, lon) }
    }

    private fun angoliCoperti(lat: Double, lon: Double) {
        val f = RadarTessere.finestraDaChiedere(lat, lon)
        val riquadri = RadarTessere.coprono(f).map { (c, r) -> RadarTessere.riquadroDi(c, r) }
        // I quattro angoli della finestra devono cadere dentro qualcuna delle
        // tessere chieste. Se ne mancasse una, sulla carta ci sarebbe un
        // quadrante senza pioggia che sembrerebbe sereno.
        listOf(
            f.latMin to f.lonMin,
            f.latMin to f.lonMax,
            f.latMax to f.lonMin,
            f.latMax to f.lonMax,
        ).forEach { (angoloLat, angoloLon) ->
            assertTrue(
                "attorno a $lat,$lon l'angolo $angoloLat,$angoloLon resta scoperto",
                riquadri.any { it.contiene(angoloLat, angoloLon) },
            )
        }
    }

    @Test
    fun `una finestra assurda non chiede il mondo intero`() {
        // Il tetto esiste perche' un errore di conto altrove non si trasformi
        // in centinaia di richieste a un servizio gratuito.
        val tutto = RadarRiquadro(latMin = -80.0, lonMin = -179.0, latMax = 80.0, lonMax = 179.0)
        assertTrue(RadarTessere.coprono(tutto).size <= 24)
    }

    @Test
    fun `la finestra chiesta contiene quella che si disegna`() {
        // Chi scarica apre piu' largo di chi disegna, apposta: la finestra
        // disegnata dipende da quanto e' larga la carta sul telefono, e quella
        // chiesta deve contenerla su qualsiasi telefono. Tre gradi e mezzo di
        // mezza larghezza sono il caso peggiore plausibile - schermo largo,
        // latitudine alta, meridiani stretti.
        listOf(44.2226 to 12.0407, 60.39 to 5.32, 64.13 to -21.89).forEach { (lat, lon) ->
            val chiesta = RadarTessere.finestraDaChiedere(lat, lon)
            assertTrue(chiesta.contiene(lat + 1.45, lon))
            assertTrue(chiesta.contiene(lat - 1.45, lon))
            assertTrue(chiesta.contiene(lat, lon + 3.5))
            assertTrue(chiesta.contiene(lat, lon - 3.5))
        }
    }
}
