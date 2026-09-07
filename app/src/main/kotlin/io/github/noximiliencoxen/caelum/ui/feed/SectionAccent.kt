package io.github.noximiliencoxen.caelum.ui.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.MeteoAccents
import io.github.noximiliencoxen.caelum.ui.theme.skyAccents

/**
 * Il colore che identifica una sezione del feed.
 *
 * Per la temperatura non c'e' una tinta sola e non e' una mancanza: la
 * temperatura **ha gia'** una scala di colore che dice quanto caldo fa
 * (`temperatureTint`), e sovrapporle un accento unico la contraddirebbe. Li' si
 * usa il colore del testo, e a colorare pensano la cifra e la barra delle ore.
 */
fun FeedSection.accentOf(accents: MeteoAccents, onSky: Color): Color = when (this) {
    FeedSection.TEMPERATURA -> onSky
    FeedSection.SOLE -> accents.sun
    FeedSection.PRECIPITAZIONI -> accents.rain
    FeedSection.VENTO -> accents.wind
    FeedSection.ARIA -> accents.air
    FeedSection.LUNA -> accents.moon
}

/**
 * Le tinte delle sezioni, tarate sul **cielo** e non sull'antracite dei
 * pannelli.
 *
 * E' la trappola della sezione 8-bis di CONTESTO, vista sul feed: le tinte di
 * `LocalMeteoAccents` sono calcolate contro la superficie scura delle schede,
 * e il feed non ha superfici - le sezioni vivono direttamente sul cielo, che a
 * meta' mattina e' grigio chiaro. Il giallo del sole sparirebbe.
 * [skyAccents] le ricalcola contro i due capi della sfumatura.
 *
 * Dietro un `remember` sul cielo perche' fa una decina di elevamenti a potenza
 * per tinta: si rifa' quando cambia l'ora del cielo, cioe' un pugno di volte al
 * giorno, non a ogni ora scorsa sulla barra.
 */
@Composable
fun rememberSkyAccents(): MeteoAccents {
    val colors = LocalMeteoColors.current
    return remember(colors) { colors.skyAccents() }
}
