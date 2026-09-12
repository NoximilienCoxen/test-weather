package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.prefs.AlertToggleKind
import io.github.noximiliencoxen.caelum.prefs.CaptionStyle
import io.github.noximiliencoxen.caelum.prefs.CardTheme
import io.github.noximiliencoxen.caelum.prefs.SalaWindUnit
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.common.MinTouchTarget

/**
 * Impostazioni: la carta e' collegata al tema che governa tutta la galleria.
 * Auto la fa seguire l'ora; Chiaro e Scuro la bloccano, con la precedenza
 * sull'automatico che vale ovunque in Sala.
 */
@Composable
fun SalaImpostazioniScreen(
    state: UiState,
    palette: SalaPalette,
    onChooseTheme: (CardTheme) -> Unit,
    onChooseUnit: (TempUnit) -> Unit,
    onChooseWindUnit: (SalaWindUnit) -> Unit,
    onChooseCaptionStyle: (CaptionStyle) -> Unit,
    onToggleAlert: (AlertToggleKind, Boolean) -> Unit,
    onSearch: (String) -> Unit,
    onPickPlace: (io.github.noximiliencoxen.caelum.data.Place) -> Unit,
    onUseLocation: () -> Unit,
    onPickSaved: (io.github.noximiliencoxen.caelum.data.Place) -> Unit,
    onSaveCurrent: () -> Unit,
    onRemoveSaved: (io.github.noximiliencoxen.caelum.data.Place) -> Unit,
    onToggleAnimazioni: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    SalaServiceScaffold(
        palette = palette,
        title = "Impostazioni",
        blobs = SalaBlobs.impostazioni,
        onClose = onClose,
    ) { modifier ->
        LazyColumn(modifier = modifier) {
            item {
                Column(modifier = Modifier.padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Impostazioni", style = SalaType.pageTitle, color = palette.ink)
                    val unitLabel = if (state.unit == TempUnit.CELSIUS) "gradi Celsius" else "gradi Fahrenheit"
                    Text(
                        text = "Carta ${state.cardTheme.name.lowercase()} · $unitLabel · didascalie ${state.captionStyle.name.lowercase()}",
                        style = SalaType.sectionLabel,
                        color = palette.inkAccent,
                    )
                }
            }
            // **La localita' sta in cima**, prima di carta e gradi: e' l'unica
            // impostazione che cambia i numeri invece di come si vedono.
            item {
                Column(modifier = Modifier.padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Località", style = SalaType.sectionLabel, color = palette.inkSoft)
                    Text(
                        text = state.place.name,
                        style = SalaType.cardTitle,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Usa la mia posizione",
                        style = SalaType.sectionLabel,
                        color = palette.inkAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MinTouchTarget)
                            .clickable(onClick = onUseLocation)
                            .padding(top = 12.dp),
                    )
                    CampoDiRicerca(valore = state.query, palette = palette, onValore = onSearch)
                    when {
                        state.searchError != null ->
                            Text(state.searchError!!, style = SalaType.footnote, color = palette.inkSoft)
                        state.searching ->
                            Text("Sto cercando…", style = SalaType.footnote, color = palette.inkSoft)
                        state.query.trim().length >= 2 && state.results.isEmpty() ->
                            Text("Nessuna località con questo nome", style = SalaType.footnote, color = palette.inkSoft)
                    }
                    state.results.take(6).forEach { trovata ->
                        Text(
                            text = listOfNotNull(trovata.name, trovata.admin).joinToString(" · "),
                            style = SalaType.value,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(MinTouchTarget)
                                .clickable { onPickPlace(trovata); onSearch("") }
                                .padding(top = 12.dp),
                        )
                    }

                    // ── Le salvate ───────────────────────────────────────────
                    //
                    // **Col loro tempo, non con un elenco di nomi.** Una lista
                    // di citta' senza niente accanto non dice perche' uno le
                    // avrebbe salvate: si tengono da parte i posti a cui si
                    // tiene, e cio' che se ne vuole sapere e' che tempo ci fa
                    // adesso. La riga e' la stessa di "Le localita'"
                    // (`PlaceRow`), riusata e non ricopiata: due copie della
                    // stessa riga divergono al primo che ne cambia una.
                    val gia = state.favorites.any { it.key == state.place.key }
                    if (!gia) {
                        Text(
                            text = "Salva questa località",
                            style = SalaType.sectionLabel,
                            color = palette.inkAccent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(MinTouchTarget)
                                .clickable(onClick = onSaveCurrent)
                                .padding(top = 12.dp),
                        )
                    }
                    if (state.favorites.isNotEmpty()) {
                        Text(
                            text = "Salvate",
                            style = SalaType.sectionLabel,
                            color = palette.inkSoft,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        state.favorites.forEach { salvata ->
                            PlaceRow(
                                place = salvata,
                                isCurrent = salvata.key == state.place.key,
                                current = state.favoritesWeather[salvata.key],
                                unit = state.unit,
                                palette = palette,
                                onPick = { onPickSaved(salvata) },
                                onRemove = { onRemoveSaved(salvata) },
                                modifier = Modifier.padding(top = 18.dp),
                            )
                        }
                    }
                }
            }
            item {
                SettingsSection(title = "Carta", palette = palette, modifier = Modifier.padding(top = 30.dp)) {
                    Segmented(listOf("Auto" to CardTheme.AUTO, "Chiaro" to CardTheme.CHIARO, "Scuro" to CardTheme.SCURO), state.cardTheme, palette, onChooseTheme)
                }
            }
            item {
                SettingsSection(title = "Temperatura", palette = palette, modifier = Modifier.padding(top = 24.dp)) {
                    Segmented(listOf("°C" to TempUnit.CELSIUS, "°F" to TempUnit.FAHRENHEIT), state.unit, palette, onChooseUnit)
                }
            }
            item {
                SettingsSection(title = "Vento", palette = palette, modifier = Modifier.padding(top = 24.dp)) {
                    Segmented(
                        listOf("km/h" to SalaWindUnit.KMH, "m/s" to SalaWindUnit.MS, "nodi" to SalaWindUnit.KN),
                        state.windUnit,
                        palette,
                        onChooseWindUnit,
                    )
                }
            }
            item {
                Column(modifier = Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(text = "Avvisi", style = SalaType.sectionLabel, color = palette.inkSoft)
                    AlertToggleRow("Pioggia intensa", state.alertToggles.pioggiaIntensa, palette) { onToggleAlert(AlertToggleKind.PIOGGIA, it) }
                    AlertToggleRow("Temporali", state.alertToggles.temporali, palette) { onToggleAlert(AlertToggleKind.TEMPORALE, it) }
                    AlertToggleRow("Raggi UV sopra 6", state.alertToggles.uvAlto, palette) { onToggleAlert(AlertToggleKind.UV, it) }
                    AlertToggleRow("Vento forte", state.alertToggles.ventoForte, palette) { onToggleAlert(AlertToggleKind.VENTO, it) }
                }
            }
            item {
                SettingsSection(title = "Didascalie", palette = palette, modifier = Modifier.padding(top = 24.dp)) {
                    Segmented(listOf("Brevi" to CaptionStyle.BREVI, "Complete" to CaptionStyle.COMPLETE), state.captionStyle, palette, onChooseCaptionStyle)
                }
            }
            item {
                SettingsSection(title = "Movimento", palette = palette, modifier = Modifier.padding(top = 24.dp)) {
                    AlertToggleRow("Riduci le animazioni", state.animazioniRidotte, palette, onToggleAnimazioni)
                    Text(
                        text = "Ferma cio' che in Sala I si muove da solo — le stelle, gli uccelli, " +
                            "cio' che cade — e le vibrazioni che ne seguono.",
                        style = SalaType.footnote,
                        color = palette.inkSoft,
                    )
                }
            }
            item {
                Text(
                    text = "Le previsioni sono di ${state.place.name} e dintorni, aggiornate ogni ora.",
                    style = SalaType.footnote,
                    color = palette.inkSoft,
                    modifier = Modifier.padding(top = 32.dp, bottom = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, palette: SalaPalette, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = title, style = SalaType.sectionLabel, color = palette.inkSoft)
        content()
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, current: T, palette: SalaPalette, onPick: (T) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (label, value) ->
            val on = value == current
            Text(
                text = label,
                style = SalaType.value,
                color = if (on) palette.ground else palette.inkSoft,
                modifier = Modifier
                    .clickable { onPick(value) }
                    .background(if (on) palette.inkAccent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(4.dp))
                    .padding(horizontal = 13.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun AlertToggleRow(label: String, on: Boolean, palette: SalaPalette, onToggle: (Boolean) -> Unit) {
    // **Il pomello scorre, non salta.** Era l'unico comando dell'app che
    // cambiava stato senza che si vedesse il passaggio: si toccava e il pallino
    // era gia' dall'altra parte, il che lascia il dubbio di aver toccato la cosa
    // sbagliata. Venti punti di corsa in un quinto di secondo bastano a dire
    // "sono io che mi sono mosso".
    val scorrimento by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f, visibilityThreshold = 0.001f),
        label = "interruttore",
    )
    val fondo by animateColorAsState(
        targetValue = if (on) palette.inkAccent else palette.inkFaint,
        animationSpec = spring(stiffness = 700f),
        label = "fondoInterruttore",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = SalaType.toggleLabel, color = palette.ink)
        Box(
            modifier = Modifier
                .size(46.dp, 26.dp)
                .clickable { onToggle(!on) }
                .background(fondo, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .offset(x = (20.dp * scorrimento))
                    .size(20.dp)
                    .background(palette.ground, CircleShape),
            )
        }
    }
}
