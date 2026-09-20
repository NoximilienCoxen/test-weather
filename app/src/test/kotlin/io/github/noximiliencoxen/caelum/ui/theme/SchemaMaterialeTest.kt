package io.github.noximiliencoxen.caelum.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.noximiliencoxen.caelum.data.SkyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Lo schema Material: venti tinte ferme e una coppia che segue l'ora.
 *
 * **Il commento dentro `toColorScheme` diceva da sempre che una sola coppia si
 * muove, e intanto le altre venti si rifacevano con lei.** Ognuna e' una
 * ricerca di contrasto - luminanza a passi, ogni passo un elevamento a potenza
 * per canale - su colori che sono costanti scritte nel file; e la funzione gira
 * a ogni fotogramma in cui il cielo si muove, perche' `MeteoTheme` la ricorda
 * sulla tavolozza intera.
 *
 * Portarle fuori vale **solo se non cambia un numero**, ed e' quello che
 * questa classe tiene fermo, in due modi che si controllano a vicenda:
 *
 * 1. cambiando l'ora, di tutto lo schema si muovono `background` e
 *    `onBackground` e **nient'altro**;
 * 2. ogni tinta ferma e' ancora il risultato della sua formula, riscritta qui
 *    leggendo i token **dallo schema stesso** - cosi' la prova non copia le
 *    costanti, le ricava, e un token cambiato a monte la trascina dietro.
 */
class SchemaMaterialeTest {

    private val mezzanotte: ColorScheme = skyColors(SkyState.of(altitude = 0f)).toColorScheme()
    private val mezzogiorno: ColorScheme = skyColors(SkyState.of(altitude = 1f)).toColorScheme()
    private val coperto: ColorScheme =
        skyColors(SkyState.of(altitude = 0.6f), cloudiness = 1f).toColorScheme()

    private val tutte: List<ColorScheme> = listOf(mezzanotte, mezzogiorno, coperto)

    /** Nome e lettore di ogni voce che **non** deve muoversi. */
    private val ferme: List<Pair<String, (ColorScheme) -> Color>> = listOf(
        "primary" to { it.primary },
        "onPrimary" to { it.onPrimary },
        "primaryContainer" to { it.primaryContainer },
        "onPrimaryContainer" to { it.onPrimaryContainer },
        "secondary" to { it.secondary },
        "onSecondary" to { it.onSecondary },
        "secondaryContainer" to { it.secondaryContainer },
        "onSecondaryContainer" to { it.onSecondaryContainer },
        "tertiary" to { it.tertiary },
        "onTertiary" to { it.onTertiary },
        "tertiaryContainer" to { it.tertiaryContainer },
        "onTertiaryContainer" to { it.onTertiaryContainer },
        "error" to { it.error },
        "onError" to { it.onError },
        "errorContainer" to { it.errorContainer },
        "onErrorContainer" to { it.onErrorContainer },
        "surface" to { it.surface },
        "onSurface" to { it.onSurface },
        "surfaceVariant" to { it.surfaceVariant },
        "onSurfaceVariant" to { it.onSurfaceVariant },
        "surfaceDim" to { it.surfaceDim },
        "surfaceBright" to { it.surfaceBright },
        "surfaceContainerLowest" to { it.surfaceContainerLowest },
        "surfaceContainerLow" to { it.surfaceContainerLow },
        "surfaceContainer" to { it.surfaceContainer },
        "surfaceContainerHigh" to { it.surfaceContainerHigh },
        "surfaceContainerHighest" to { it.surfaceContainerHighest },
        "inverseSurface" to { it.inverseSurface },
        "inverseOnSurface" to { it.inverseOnSurface },
        "inversePrimary" to { it.inversePrimary },
        "outline" to { it.outline },
        "outlineVariant" to { it.outlineVariant },
        "scrim" to { it.scrim },
    )

    @Test
    fun `dall'ora dipendono il fondo e il suo testo, e nient'altro`() {
        ferme.forEach { (nome, leggi) ->
            assertEquals(nome, leggi(mezzanotte), leggi(mezzogiorno))
            assertEquals(nome, leggi(mezzanotte), leggi(coperto))
        }
    }

    /**
     * E il fondo **si muove davvero**.
     *
     * Senza questa riga la prova qui sopra passerebbe anche se lo schema
     * diventasse tutto costante, cioe' proprio se il rifacimento avesse
     * congelato l'unica cosa che doveva restare viva.
     */
    @Test
    fun `il fondo di mezzanotte non e' quello di mezzogiorno`() {
        assertNotEquals(mezzanotte.background, mezzogiorno.background)
        assertNotEquals(mezzanotte.onBackground, mezzogiorno.onBackground)
    }

    @Test
    fun `il testo dei pannelli e' ancora bianco reso leggibile sul container`() {
        tutte.forEach { s ->
            assertEquals(Color.White.readableOn(s.surfaceContainer), s.onSurface)
            assertEquals(s.onSurface.mutedOn(s.surfaceContainerHighest), s.onSurfaceVariant)
        }
    }

    @Test
    fun `le quattro tinte sono ancora quelle calcolate sul container`() {
        tutte.forEach { s ->
            assertEquals(SunTint.readableOn(s.surfaceContainer, CONTRAST_AA_LARGE), s.primary)
            assertEquals(RainTint.readableOn(s.surfaceContainer, CONTRAST_AA_LARGE), s.secondary)
            assertEquals(AirTint.readableOn(s.surfaceContainer, CONTRAST_AA_LARGE), s.tertiary)
            assertEquals(AlertTint.readableOn(s.surfaceContainer, CONTRAST_AA_LARGE), s.error)

            // La mescola che fa i contenitori: un filo di tinta sul grigio alto.
            // L'allerta ne prende un pelo di piu', come da sempre.
            assertEquals(lerp(s.surfaceContainerHigh, SunTint, 0.14f), s.primaryContainer)
            assertEquals(lerp(s.surfaceContainerHigh, RainTint, 0.14f), s.secondaryContainer)
            assertEquals(lerp(s.surfaceContainerHigh, AirTint, 0.14f), s.tertiaryContainer)
            assertEquals(lerp(s.surfaceContainerHigh, AlertTint, 0.16f), s.errorContainer)

            // E i testi sopra le tinte piene, che non passano da una ricerca.
            assertEquals(SunTint.onColor(), s.onPrimary)
            assertEquals(RainTint.onColor(), s.onSecondary)
            assertEquals(AirTint.onColor(), s.onTertiary)
            assertEquals(AlertTint.onColor(), s.onError)
        }
    }

    @Test
    fun `i testi dentro i quattro contenitori sono ancora calcolati sul contenitore`() {
        tutte.forEach { s ->
            assertEquals(SunTint.readableOn(s.primaryContainer), s.onPrimaryContainer)
            assertEquals(RainTint.readableOn(s.secondaryContainer), s.onSecondaryContainer)
            assertEquals(AirTint.readableOn(s.tertiaryContainer), s.onTertiaryContainer)
            assertEquals(AlertTint.readableOn(s.errorContainer), s.onErrorContainer)
        }
    }

    @Test
    fun `le pillole invertite reggono ancora il loro fondo chiaro`() {
        tutte.forEach { s ->
            assertEquals(Color.Black.readableOn(s.inverseSurface), s.inverseOnSurface)
            assertEquals(SunTint.readableOn(s.inverseSurface), s.inversePrimary)
        }
    }
}
