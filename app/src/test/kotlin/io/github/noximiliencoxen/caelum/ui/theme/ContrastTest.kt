package io.github.noximiliencoxen.caelum.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il contrasto, che l'app calcola invece di sperarlo.
 *
 * `readableOn` **promette** un rapporto: qui si verifica che lo mantenga, e non
 * su un colore fortunato ma su una griglia di fondi che copre il cielo di
 * mezzanotte, quello di mezzogiorno e i grigi in mezzo - che sono i piu'
 * difficili, perche' da li' non si scappa ne' verso il bianco ne' verso il nero.
 *
 * Il difetto che questo previene si e' gia' visto: il titolo del dettaglio era
 * quasi nero su un pannello antracite, e a mezzogiorno spariva.
 */
class ContrastTest {

    private val fondi = listOf(
        Color(0xFF0B1020), // zenit di mezzanotte
        Color(0xFF5A9BD4), // zenit di mezzogiorno
        Color(0xFFC9E2F4), // orizzonte di giorno
        Color(0xFF7F7F7F), // il grigio medio, il caso peggiore
        Color.White,
        Color.Black,
    )

    @Test
    fun `bianco e nero stanno agli estremi della scala`() {
        assertEquals(21f, Color.White.contrastRatio(Color.Black), 0.01f)
        assertEquals(1f, Color.White.contrastRatio(Color.White), 0.01f)
    }

    @Test
    fun `la luminanza pesa i canali come li pesa l'occhio`() {
        // Un verde pieno e un blu pieno hanno la stessa "luminosita'" in HSL e
        // luminanze che differiscono di dieci volte.
        assertTrue(Color.Green.relativeLuminance() > Color.Blue.relativeLuminance() * 5)
    }

    @Test
    fun `onColor sceglie sempre il piu' leggibile dei due`() {
        fondi.forEach { fondo ->
            val scelto = fondo.onColor()
            val altro = if (scelto == Color.White) Color.Black else Color.White
            assertTrue(
                "su $fondo ha scelto il peggiore dei due",
                fondo.contrastRatio(scelto) >= fondo.contrastRatio(altro),
            )
        }
    }

    @Test
    fun `readableOn mantiene la soglia che dichiara`() {
        val tinte = listOf(
            Color(0xFFF2C230), // giallo d'allerta
            Color(0xFFF08A2B), // arancione
            Color(0xFFE0402F), // rosso
            Color(0xFF3C8DF5), // il blu della pioggia
        )
        fondi.forEach { fondo ->
            tinte.forEach { tinta ->
                val reso = tinta.readableOn(fondo, CONTRAST_AA_LARGE)
                assertTrue(
                    "$tinta su $fondo esce a ${fondo.contrastRatio(reso)}, sotto la soglia",
                    fondo.contrastRatio(reso) >= CONTRAST_AA_LARGE - 0.05f,
                )
            }
        }
    }
}
