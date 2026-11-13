package com.caelumweather.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicoWidgetTest {

    @Test
    fun `una cella e' la temperatura`() {
        assertEquals(VistaWidget.MINI, vistaDaForma(70f, 70f))
        assertEquals(VistaWidget.MINI, vistaDaForma(57f, 110f))
        // Una colonna tirata in verticale resta la cella, non il tempo adesso.
        assertEquals(VistaWidget.MINI, vistaDaForma(65f, 410f))
    }

    @Test
    fun `due per due e' il tempo adesso, anche piu' alto che largo`() {
        assertEquals(VistaWidget.METEO, vistaDaForma(150f, 150f))
        assertEquals(VistaWidget.METEO, vistaDaForma(140f, 190f))
        assertEquals(VistaWidget.METEO, vistaDaForma(110f, 320f))
    }

    @Test
    fun `largo e basso e' ancora il tempo adesso, largo e piu' alto sono le ore`() {
        assertEquals(VistaWidget.METEO, vistaDaForma(340f, 110f))
        assertEquals(VistaWidget.ORE, vistaDaForma(340f, 170f))
        assertEquals(VistaWidget.ORE, vistaDaForma(250f, 229f))
    }

    @Test
    fun `grande e' la settimana`() {
        assertEquals(VistaWidget.SETTIMANA, vistaDaForma(340f, 230f))
        assertEquals(VistaWidget.SETTIMANA, vistaDaForma(340f, 360f))
    }

    @Test
    fun `tutte le facce hanno un widget che le sa dipingere`() {
        // Il `when` di UnicoWidget e' esaustivo per il compilatore; qui si
        // controlla solo che nessuna vista sparisca dall'elenco senza che se ne
        // accorga la schermata di configurazione.
        assertEquals(9, VistaWidget.entries.size)
    }

    @Test
    fun `allargato a sessione aperta si ridisegna, non si stira`() {
        // Il caso visto sulla Home: disegnato 2x2, allargato a 4x2.
        assertFalse(stessaMisura(DpSize(150.dp, 180.dp), DpSize(340.dp, 180.dp)))
        assertFalse(stessaMisura(DpSize(150.dp, 180.dp), DpSize(150.dp, 90.dp)))
        // Arrotondamenti e misura ancora sconosciuta: niente ridisegno inutile.
        assertTrue(stessaMisura(DpSize(150.dp, 180.dp), DpSize(150.6.dp, 179.2.dp)))
        assertTrue(stessaMisura(DpSize(150.dp, 180.dp), DpSize(0.dp, 0.dp)))
    }
}
