package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.prefs.AlertToggleKind
import io.github.noximiliencoxen.caelum.prefs.AlertToggles
import io.github.noximiliencoxen.caelum.prefs.CaptionStyle
import io.github.noximiliencoxen.caelum.prefs.CardTheme
import io.github.noximiliencoxen.caelum.prefs.SalaWindUnit
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.UiState

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
                .background(if (on) palette.inkAccent else palette.inkFaint, RoundedCornerShape(13.dp)),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(20.dp)
                    .background(palette.ground, CircleShape),
            )
        }
    }
}
