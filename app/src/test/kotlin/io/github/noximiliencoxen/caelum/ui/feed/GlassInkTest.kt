package io.github.noximiliencoxen.caelum.ui.feed

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.ui.theme.CONTRAST_AA
import io.github.noximiliencoxen.caelum.ui.theme.CONTRAST_AA_LARGE
import io.github.noximiliencoxen.caelum.ui.theme.MeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.contrastRatio
import io.github.noximiliencoxen.caelum.ui.theme.skyColors
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/**
 * Il vetro e' una superficie nuova, e una superficie nuova e' l'occasione in cui
 * il difetto del contrasto rientra dalla porta di servizio.
 *
 * E' gia' successo, e sta scritto in CONTESTO sezione 8-bis: il fondo del cielo
 * e il colore del testo si interpolano su due scale diverse, e a meta' mattina
 * si incrociavano a **1,01 a 1** - stessa luminanza, testo invisibile. Nessuno
 * l'aveva mai visto perche' la CI fotografava le due ore estreme, che sono le
 * due in cui il contrasto e' migliore.
 *
 * Qui la stessa domanda si fa alla scheda in vetro, e si fa **senza emulatore**:
 * `onGlass` e' una funzione pura su colori, quindi si puo' spazzare tutta la
 * giornata per tutte le nuvolosita' invece di guardare due scatti. E' l'unica
 * prova di questo stile che non ha bisogno di un telefono in mano.
 */
class GlassInkTest {

    /**
     * La giornata intera, non due ore.
     *
     * L'altezza del sole va da notte piena a mezzogiorno; `evening` separa il
     * mattino dalla sera, che con la sola altezza sono indistinguibili;
     * la nuvolosita' scorre da sereno a temporale. Sono i tre assi da cui
     * `skyColors` fa il proprio cielo.
     */
    private fun cieli(): List<Pair<String, MeteoColors>> {
        val out = mutableListOf<Pair<String, MeteoColors>>()
        var altitude = -1f
        while (altitude <= 1f + 1e-4f) {
            for (evening in listOf(0f, 1f)) {
                for (step in 0..10) {
                    val cloudiness = step / 10f
                    val sky = SkyState.of(altitude = altitude, evening = evening)
                    val nome = "altezza=%.2f sera=%.0f nuvole=%.1f".format(altitude, evening, cloudiness)
                    out += nome to skyColors(sky, cloudiness)
                }
            }
            altitude += 0.05f
        }
        return out
    }

    private fun peggiore(
        colore: Color,
        ink: MeteoColors,
    ): Float = min(colore.contrastRatio(ink.skyZenith), colore.contrastRatio(ink.skyHorizon))

    @Test
    fun `il testo sul vetro regge la soglia a ogni ora e con ogni nuvolosita'`() {
        cieli().forEach { (nome, colors) ->
            val ink = colors.onGlass().colors
            val misura = peggiore(ink.text, ink)
            assertTrue(
                "testo sul vetro a %.2f:1 con %s".format(misura, nome),
                misura >= CONTRAST_AA - TOLLERANZA,
            )
        }
    }

    @Test
    fun `l'etichetta si fa da parte ma non sparisce`() {
        // `mutedOnBoth` promette di fermarsi **prima** della soglia, non dopo:
        // e' il passaggio in cui, mescolando a occhio, sono nati i grigi al tre
        // e mezzo per uno che si leggono male.
        cieli().forEach { (nome, colors) ->
            val ink = colors.onGlass().colors
            val misura = peggiore(ink.label, ink)
            assertTrue(
                "etichetta sul vetro a %.2f:1 con %s".format(misura, nome),
                misura >= CONTRAST_AA - TOLLERANZA,
            )
        }
    }

    @Test
    fun `il segno e il bianco della neve reggono la soglia del segno grande`() {
        // La linea e le colonne di neve sono figure, non scritte: gli basta la
        // soglia del testo grande. Ma la soglia gli serve davvero - le ore di
        // neve si disegnano in `cloudCore`, che sul cielo e' bianco pieno e su
        // un vetro pallido sarebbe bianco su bianco.
        cieli().forEach { (nome, colors) ->
            val ink = colors.onGlass().colors
            assertTrue(
                "linea sul vetro a %.2f:1 con %s".format(peggiore(ink.line, ink), nome),
                peggiore(ink.line, ink) >= CONTRAST_AA_LARGE - TOLLERANZA,
            )
            assertTrue(
                "neve sul vetro a %.2f:1 con %s".format(peggiore(ink.cloudCore, ink), nome),
                peggiore(ink.cloudCore, ink) >= CONTRAST_AA_LARGE - TOLLERANZA,
            )
        }
    }

    @Test
    fun `il velo cede solo quando serve, e non piu' del necessario`() {
        // La promessa scritta nel KDoc di `onGlass`: nella grande maggioranza
        // dei cieli la scheda ha l'opacita' che le e' stata disegnata. Se questo
        // numero crolla, qualcuno ha toccato il velo o le tinte del cielo, e la
        // scheda e' diventata piu' opaca di quanto chiunque abbia deciso.
        //
        // Il confronto si fa sui **capi compositati** e non sul pennello: il
        // capo composito e' esattamente cio' che il velo produce, e due
        // `Brush` uguali per costruzione non lo dimostrerebbero meglio.
        val tutti = cieli()
        val intatti = tutti.count { (_, colors) ->
            val ink = colors.onGlass().colors
            ink.skyZenith == GLASS_TOP.compositeOver(colors.skyZenith) &&
                ink.skyHorizon == GLASS_BOTTOM.compositeOver(colors.skyHorizon)
        }
        assertTrue(
            "il velo di progetto basta solo a $intatti cieli su ${tutti.size}",
            intatti * 100 >= tutti.size * 70,
        )
    }

    @Test
    fun `la bolla della barra delle ore stacca dal proprio contenuto`() {
        // `pillBackground` e `pillText` sono una coppia: se si avvicinano, la
        // bolla sopra il cursore diventa una macchia con dentro un'ombra.
        cieli().forEach { (nome, colors) ->
            val ink = colors.onGlass().colors
            val misura = ink.pillBackground.contrastRatio(ink.pillText)
            assertTrue(
                "bolla a %.2f:1 con %s".format(misura, nome),
                misura >= CONTRAST_AA - TOLLERANZA,
            )
        }
    }

    /**
     * Il margine di un centesimo.
     *
     * `readableOn` si ferma **appena** raggiunta la soglia, a passi di un
     * sedicesimo, e la misura di ritorno cade quindi su 4,50 o pochissimo sopra.
     * Un confronto secco a 4,5 fallirebbe sull'errore di virgola mobile invece
     * che su un difetto vero.
     */
    private val TOLLERANZA = 0.01f
}
