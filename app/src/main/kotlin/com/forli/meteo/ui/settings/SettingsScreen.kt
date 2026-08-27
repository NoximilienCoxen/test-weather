package com.forli.meteo.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.Place
import com.forli.meteo.data.WeatherModel
import com.forli.meteo.data.WeatherRepository
import com.forli.meteo.data.key
import com.forli.meteo.prefs.TempUnit
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType
import java.time.format.DateTimeFormatter

/**
 * Le impostazioni: dove si guarda, in che unita', e da dove arrivano i numeri.
 *
 * La terza sezione non e' un obbligo di licenza travestito da schermata. Una
 * previsione senza fonte e' un'opinione: chi guarda ha il diritto di sapere chi
 * l'ha fatta, quando, e per quale punto esatto della mappa.
 */
@Composable
fun SettingsScreen(
    state: UiState,
    onQuery: (String) -> Unit,
    onChoosePlace: (Place) -> Unit,
    onChooseUnit: (TempUnit) -> Unit,
    onToggleFavorite: (Place) -> Unit,
    onChooseModel: (WeatherModel) -> Unit,
    onLocate: (canAsk: Boolean, onNeedsPermission: () -> Unit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    var advancedOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 24.dp, top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CloseButton(onClose)
            Text(
                text = "IMPOSTAZIONI",
                style = MeteoType.caption,
                color = colors.label,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { SectionTitle("LOCALITÀ") }

            item {
                Text(
                    text = state.place.name.uppercase(),
                    style = MeteoType.label,
                    color = colors.text,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            item {
                // Con la virgola decimale dell'italiano "44,2226, 12,0407" si
                // legge come quattro numeri invece che due. I gradi e i punti
                // cardinali tolgono ogni dubbio.
                val detail = state.place.detail
                val text = if (detail.isBlank()) {
                    coordinates(state.place.latitude, state.place.longitude)
                } else {
                    "$detail · ${coordinates(state.place.latitude, state.place.longitude)}"
                }
                Text(
                    text = text.uppercase(),
                    style = MeteoType.caption,
                    color = colors.label,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            item {
                LocateButton(
                    locating = state.locating,
                    error = state.locationError,
                    onLocate = onLocate,
                )
            }

            item {
                SearchField(
                    value = state.query,
                    onValueChange = onQuery,
                    placeholder = "> CERCA CITTÀ",
                )
            }

            val message = when {
                state.searching -> "RICERCA IN CORSO…"
                state.searchError != null -> state.searchError.uppercase()
                state.query.trim().length >= 2 && state.results.isEmpty() -> "NESSUN RISULTATO"
                else -> null
            }
            if (message != null) {
                item {
                    Text(
                        text = message,
                        style = MeteoType.caption,
                        color = colors.label,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
            }

            // Con la ricerca vuota si vedono le scorciatoie, con i preferiti
            // in cima. Le prime scorciatoie sono fra i posti piu' piovosi che
            // esistano, ed e' voluto: con una citta' sola non c'era modo di
            // vedere la pioggia se non aspettando che piovesse.
            val favoriteKeys = state.favorites.map { it.key }.toSet()
            fun isSelected(place: Place) = place.latitude == state.place.latitude &&
                place.longitude == state.place.longitude

            if (state.results.isNotEmpty()) {
                items(state.results, key = { it.key }) { place ->
                    PlaceRow(
                        place = place,
                        selected = isSelected(place),
                        favorite = place.key in favoriteKeys,
                        onClick = { onChoosePlace(place) },
                        onToggleFavorite = { onToggleFavorite(place) },
                    )
                }
            } else {
                if (state.favorites.isNotEmpty()) {
                    item { SectionTitle("PREFERITI") }
                    items(state.favorites, key = { it.key }) { place ->
                        PlaceRow(
                            place = place,
                            selected = isSelected(place),
                            favorite = true,
                            onClick = { onChoosePlace(place) },
                            onToggleFavorite = { onToggleFavorite(place) },
                        )
                    }
                }
                val plainSuggestions = Place.SUGGESTIONS.filterNot { it.key in favoriteKeys }
                items(plainSuggestions, key = { it.key }) { place ->
                    PlaceRow(
                        place = place,
                        selected = isSelected(place),
                        favorite = false,
                        onClick = { onChoosePlace(place) },
                        onToggleFavorite = { onToggleFavorite(place) },
                    )
                }
            }

            item { Spacer(Modifier.height(6.dp)) }
            item { SectionTitle("IMPOSTAZIONI") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "UNITÀ DI MISURA", style = MeteoType.caption, color = colors.label)
                    UnitChoice(current = state.unit, onChoose = onChooseUnit)
                }
            }

            item { Spacer(Modifier.height(6.dp)) }
            item { SectionTitle("DATI") }

            item { KeyValueRow("SERVIZIO", "OPEN-METEO.COM") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "MODELLO", style = MeteoType.caption, color = colors.label)
                    ModelChoice(current = state.model, onChoose = onChooseModel)
                }
            }
            item { KeyValueRow("FUSO ORARIO", "Auto (coordinate)") }
            item {
                KeyValueRow(
                    "AGGIORNATO",
                    state.forecast?.fetchedAt?.format(CLOCK) ?: "MAI",
                )
            }
            item { KeyValueRow("CHIAVE D'ACCESSO", "Nessuna (Uso libero)") }
            item { KeyValueRow("LICENZA", "CC BY 4.0") }
            item { KeyValueRow("FASE LUNARE", "Locale (in-app)") }

            item {
                AdvancedToggle(
                    open = advancedOpen,
                    onToggle = { advancedOpen = !advancedOpen },
                )
            }
            if (advancedOpen) {
                item { AdvancedRow("PREVISIONE", WeatherRepository.FORECAST_ENDPOINT) }
                item { AdvancedRow("RICERCA LUOGHI", WeatherRepository.GEOCODING_ENDPOINT) }
                item {
                    AdvancedRow(
                        "GRANDEZZE ORARIE",
                        WeatherRepository.HOURLY_VARS.replace(",", ", "),
                    )
                }
                item {
                    AdvancedRow(
                        "GRANDEZZE GIORNALIERE",
                        WeatherRepository.DAILY_VARS.replace(",", ", "),
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val colors = LocalMeteoColors.current
    Text(
        text = "──── $text ────",
        style = MeteoType.caption,
        color = colors.label.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    val colors = LocalMeteoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MeteoType.caption, color = colors.label)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(1.dp)
                .background(colors.line),
        )
        Text(text = value, style = MeteoType.value, color = colors.text)
    }
}

@Composable
private fun AdvancedToggle(open: Boolean, onToggle: () -> Unit) {
    val colors = LocalMeteoColors.current
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = if (open) "[-] PARAMETRI AVANZATI" else "[+] PARAMETRI AVANZATI",
        style = MeteoType.caption,
        color = colors.label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun AdvancedRow(label: String, value: String) {
    val colors = LocalMeteoColors.current
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(text = label, style = MeteoType.caption, color = colors.label)
        Text(
            text = value,
            style = MeteoType.value,
            color = colors.text,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun PlaceRow(
    place: Place,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = LocalMeteoColors.current
    val interaction = remember { MutableInteractionSource() }
    val starInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(if (selected) colors.line.copy(alpha = 0.55f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Modifier proprio e distinto da quello della riga: il tocco sulla
        // stella deve aggiungere o togliere il preferito, non selezionare
        // anche la citta'.
        Text(
            text = if (favorite) "★" else "☆",
            style = MeteoType.value,
            color = if (favorite) colors.text else colors.label,
            modifier = Modifier
                .clickable(
                    interactionSource = starInteraction,
                    indication = null,
                    onClick = onToggleFavorite,
                )
                .padding(end = 10.dp),
        )
        val label = if (place.country.isNullOrBlank()) {
            "· ${place.name}"
        } else {
            "· ${place.name} · ${place.country}"
        }
        Text(
            text = label,
            style = MeteoType.value,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.text),
            )
        }
    }
}

@Composable
private fun UnitChoice(
    current: TempUnit,
    onChoose: (TempUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TempUnit.entries.forEach { unit ->
            val active = unit == current
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (active) colors.pillBackground else Color.Transparent)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onChoose(unit) },
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = unit.symbol,
                    style = MeteoType.value,
                    color = if (active) colors.pillText else colors.label,
                )
            }
        }
    }
}

@Composable
private fun ModelChoice(
    current: WeatherModel,
    onChoose: (WeatherModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WeatherModel.entries.forEach { option ->
            val active = option == current
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (active) colors.pillBackground else Color.Transparent)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onChoose(option) },
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = option.label,
                    style = MeteoType.value,
                    color = if (active) colors.pillText else colors.label,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val colors = LocalMeteoColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.line.copy(alpha = 0.40f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = MeteoType.value, color = colors.label)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MeteoType.value.copy(color = colors.text),
            cursorBrush = SolidColor(colors.text),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Il pulsante "TROVAMI": chiede la posizione al dispositivo e, se manca il
 * permesso, apre il dialogo di sistema una sola volta. Rifiutato, resta
 * comunque possibile ritentare (l'utente puo' averlo concesso nel frattempo
 * dalle impostazioni di sistema) ma non si riapre da solo il dialogo.
 */
@Composable
private fun LocateButton(
    locating: Boolean,
    error: String?,
    onLocate: (canAsk: Boolean, onNeedsPermission: () -> Unit) -> Unit,
) {
    val colors = LocalMeteoColors.current
    var refused by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            refused = false
            onLocate(false) {}
        } else {
            refused = true
        }
    }
    val interaction = remember { MutableInteractionSource() }

    Column {
        Text(
            text = when {
                locating -> "[ ⌖ RICERCA POSIZIONE… ]"
                else -> "[ ⌖ TROVAMI ]"
            },
            style = MeteoType.caption,
            color = colors.label,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = !locating,
                    onClick = {
                        onLocate(!refused) {
                            permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                )
                .padding(vertical = 8.dp),
        )
        if (error != null) {
            Text(
                text = error,
                style = MeteoType.caption,
                color = colors.label,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

/** Una croce disegnata, per non tirarsi dietro una libreria di icone. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    val colors = LocalMeteoColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = size.minDimension * 0.11f
            drawLine(
                color = colors.label,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colors.label,
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun coordinates(latitude: Double, longitude: Double): String {
    val ns = if (latitude >= 0) "N" else "S"
    val ew = if (longitude >= 0) "E" else "O"
    return String.format(
        java.util.Locale.ROOT,
        "%.4f° %s   %.4f° %s",
        kotlin.math.abs(latitude), ns, kotlin.math.abs(longitude), ew,
    )
}
