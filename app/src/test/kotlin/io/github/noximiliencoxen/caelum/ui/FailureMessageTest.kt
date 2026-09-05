package io.github.noximiliencoxen.caelum.ui

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Cio' che si legge sullo schermo quando la previsione non arriva.
 *
 * La schermata principale scrive `state.error` **in maiuscolo**, al posto della
 * parola sul tempo. Finche' li' dentro c'era il messaggio dell'eccezione, il
 * fallimento sulle coordinate `NaN` si presentava come sei righe di JSON
 * maiuscolo - coordinate comprese - dove doveva esserci "SERENO".
 *
 * Questi non sono test sul testo esatto: sono test sul fatto che quel testo
 * **sia stato scritto per essere letto**, cioe' non arrivi da un'eccezione.
 */
class FailureMessageTest {

    private val technical = SerializationException(
        "Unexpected JSON token at offset 15: Failed to parse type 'double' for input 'NaN'",
    )

    @Test
    fun `nessun messaggio ripete l'eccezione che l'ha causato`() {
        listOf(
            technical,
            UnknownHostException("api.open-meteo.com"),
            IllegalStateException("HTTP 500 da Open-Meteo: {\"error\":true}"),
        ).forEach { failure ->
            val shown = failureMessage(failure)
            assertFalse(shown, shown == failure.message)
            // Una riga sola e corta: sotto la cifra c'e' spazio per una
            // condizione, non per un referto.
            assertFalse(shown, shown.contains('\n'))
            assertFalse(shown, shown.length > 40)
        }
    }

    @Test
    fun `una risposta illeggibile parla del servizio, non del parser`() {
        assertEquals("Il servizio meteo ha risposto male", failureMessage(technical))
    }

    @Test
    fun `tutti i modi di non arrivare sono la stessa cosa per chi guarda`() {
        val expected = "Rete non raggiungibile"
        assertEquals(expected, failureMessage(UnknownHostException("api.open-meteo.com")))
        assertEquals(expected, failureMessage(SocketTimeoutException("timeout")))
        assertEquals(expected, failureMessage(IOException("connection reset")))
    }

    /**
     * Il guardiano delle coordinate lancia una `IllegalArgumentException`: non
     * e' ne' rete ne' formato, e deve comunque uscire leggibile.
     */
    @Test
    fun `il resto ha una risposta sua, non il messaggio grezzo`() {
        assertEquals(
            "Previsione non disponibile",
            failureMessage(IllegalArgumentException("Coordinate non utilizzabili: NaN, NaN")),
        )
    }
}
