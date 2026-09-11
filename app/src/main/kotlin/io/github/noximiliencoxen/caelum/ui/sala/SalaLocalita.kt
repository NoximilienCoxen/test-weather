package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.CurrentWeather
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.data.key
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.asPlainDegrees

/**
 * "Le localita'": le localita' salvate, con una piccola iconcina che indica
 * il meteo del momento accanto a ognuna — tocchi un nome per renderlo
 * corrente, la croce per toglierlo, un suggerimento per aggiungerne uno.
 */
@Composable
fun SalaLocalitaScreen(
    state: UiState,
    palette: SalaPalette,
    onPick: (Place) -> Unit,
    onAdd: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onClose: () -> Unit,
) {
    val favorites = state.favorites
    val suggestions = Place.SUGGESTIONS.filterNot { s -> favorites.any { it.key == s.key } }

    SalaServiceScaffold(
        palette = palette,
        title = "Le localita'",
        blobs = SalaBlobs.localita,
        onClose = onClose,
    ) { modifier ->
        LazyColumn(modifier = modifier) {
            item {
                Column(modifier = Modifier.padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Le tue localita'", style = SalaType.pageTitle, color = palette.ink)
                    Text(text = "${favorites.size} salvate", style = SalaType.sectionLabel, color = palette.inkAccent)
                }
            }
            items(favorites, key = { it.key }) { place ->
                PlaceRow(
                    place = place,
                    isCurrent = place.key == state.place.key,
                    current = state.favoritesWeather[place.key],
                    unit = state.unit,
                    palette = palette,
                    onPick = { onPick(place) },
                    onRemove = { onRemove(place) },
                    modifier = Modifier.padding(top = 30.dp),
                )
            }
            if (suggestions.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        Text(text = "Aggiungi", style = SalaType.sectionLabel, color = palette.inkSoft)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            suggestions.forEach { s ->
                                Text(
                                    text = s.name,
                                    style = SalaType.value,
                                    color = palette.inkAccent,
                                    modifier = Modifier
                                        .clickable { onAdd(s) }
                                        .background(palette.inkFaint, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    place: Place,
    isCurrent: Boolean,
    current: CurrentWeather?,
    unit: io.github.noximiliencoxen.caelum.prefs.TempUnit,
    palette: SalaPalette,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onPick),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val condition = current?.weatherCode?.let { salaConditionOf(it) }
        Canvas(modifier = Modifier.padding(top = 4.dp).size(30.dp)) {
            if (condition != null) weatherGlyph(condition, if (isCurrent) palette.inkAccent else palette.ink.copy(alpha = 0.7f))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = place.name,
                style = SalaType.cardTitle,
                color = if (isCurrent) palette.inkAccent else palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(place.admin, current?.weatherCode?.let { Wmo.condition(it).lowercase() }).joinToString(" · "),
                style = SalaType.footnote,
                color = palette.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = current?.temperature.asPlainDegrees(unit),
                style = SalaType.giant(34),
                color = palette.ink,
            )
        }
        Text(
            text = "×",
            style = SalaType.cardTitle,
            color = palette.inkSoft,
            modifier = Modifier.clickable(onClick = onRemove).padding(start = 4.dp),
        )
    }
}
