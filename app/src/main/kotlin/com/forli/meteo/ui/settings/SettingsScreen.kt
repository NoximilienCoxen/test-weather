package com.forli.meteo.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Shadow
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
import com.forli.meteo.ui.theme.MeteoType
import java.time.format.DateTimeFormatter

// Colori fissi, non legati a LocalMeteoColors: il pannello deve restare
// leggibile sopra qualunque cielo (alba, notte, temporale), non solo su
// quello per cui i toni dinamici sono calibrati.
private val PanelBackground = Color.Black.copy(alpha = 0.60f)
private val PanelBorder = Color.White.copy(alpha = 0.20f)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFFCCCCCC)
private val SubtleFill = Color.White.copy(alpha = 0.10f)
private val SubtleLine = Color.White.copy(alpha = 0.18f)
private val TextShadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 1f), blurRadius = 3f)

/**
 * Le impostazioni: dove si guarda, in che unita', e da dove arrivano i numeri.
 *
 * Tre comparti, ognuno un riquadro scuro proprio invece di righe sciolte sul
 * fondo dinamico: quel fondo cambia con l'ora e il meteo, ed e' calibrato per
 * il cielo, non per il testo che gli sta sopra.
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
                color = TextSecondary,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Comparto(title = "LOCALITÀ") {
                    Text(
                        text = state.place.name.uppercase(),
                        style = MeteoType.label.copy(shadow = TextShadow),
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    // Con la virgola decimale dell'italiano "44,2226, 12,0407"
                    // si legge come quattro numeri invece che due. I gradi e i
                    // punti cardinali tolgono ogni dubbio.
                    val detail = state.place.detail
                    val detailText = if (detail.isBlank()) {
                        coordinates(state.place.latitude, state.place.longitude)
                    } else {
                        "$detail · ${coordinates(state.place.latitude, state.place.longitude)}"
                    }
                    Text(
                        text = detailText.uppercase(),
                        style = MeteoType.caption,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )

                    LocateButton(
                        locating = state.locating,
                        error = state.locationError,
                        onLocate = onLocate,
                    )

                    Spacer(Modifier.height(8.dp))

                    SearchField(
                        value = state.query,
                        onValueChange = onQuery,
                        placeholder = "> CERCA CITTÀ",
                    )

                    val message = when {
                        state.searching -> "RICERCA IN CORSO…"
                        state.searchError != null -> state.searchError.uppercase()
                        state.query.trim().length >= 2 && state.results.isEmpty() ->
                            "NESSUN RISULTATO"
                        else -> null
                    }
                    if (message != null) {
                        Text(
                            text = message,
                            style = MeteoType.caption,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }

                    // Con la ricerca vuota si vedono le scorciatoie, con i
                    // preferiti in cima. Le prime scorciatoie sono fra i posti
                    // piu' piovosi che esistano, ed e' voluto: con una sola
                    // citta' non c'era modo di vedere la pioggia se non
                    // aspettando che piovesse. Liste corte: un Column semplice
                    // basta, non serve la virtualizzazione di una LazyColumn
                    // annidata dentro l'unico item che e' questo comparto.
                    val favoriteKeys = state.favorites.map { it.key }.toSet()
                    fun isSelected(place: Place) = place.latitude == state.place.latitude &&
                        place.longitude == state.place.longitude

                    Spacer(Modifier.height(4.dp))

                    if (state.results.isNotEmpty()) {
                        state.results.forEach { place ->
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
                            Text(
                                text = "──── PREFERITI ────",
                                style = MeteoType.caption,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                            )
                            state.favorites.forEach { place ->
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
                        plainSuggestions.forEach { place ->
                            PlaceRow(
                                place = place,
                                selected = isSelected(place),
                                favorite = false,
                                onClick = { onChoosePlace(place) },
                                onToggleFavorite = { onToggleFavorite(place) },
                            )
                        }
                    }
                }
            }

            item {
                Comparto(title = "PREFERENZE") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "UNITÀ DI MISURA", style = MeteoType.caption, color = TextSecondary)
                        UnitChoice(current = state.unit, onChoose = onChooseUnit)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "MODELLO", style = MeteoType.caption, color = TextSecondary)
                        ModelChoice(current = state.model, onChoose = onChooseModel)
                    }
                }
            }

            item {
                Comparto(title = "DIAGNOSTICA DATI") {
                    KeyValueRow("SERVIZIO", "OPEN-METEO.COM")
                    KeyValueRow("FUSO ORARIO", "Auto (coordinate)")
                    KeyValueRow("AGGIORNATO", state.forecast?.fetchedAt?.format(CLOCK) ?: "MAI")
                    KeyValueRow("CHIAVE D'ACCESSO", "Nessuna (Uso libero)")
                    KeyValueRow("LICENZA", "CC BY 4.0")
                    KeyValueRow("FASE LUNARE", "Locale (in-app)")

                    AdvancedToggle(
                        open = advancedOpen,
                        onToggle = { advancedOpen = !advancedOpen },
                    )
                    if (advancedOpen) {
                        AdvancedRow("PREVISIONE", WeatherRepository.FORECAST_ENDPOINT)
                        AdvancedRow("RICERCA LUOGHI", WeatherRepository.GEOCODING_ENDPOINT)
                        AdvancedRow(
                            "GRANDEZZE ORARIE",
                            WeatherRepository.HOURLY_VARS.replace(",", ", "),
                        )
                        AdvancedRow(
                            "GRANDEZZE GIORNALIERE",
                            WeatherRepository.DAILY_VARS.replace(",", ", "),
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** Riquadro scuro proprio, con bordo sottile, che isola un comparto dal cielo dietro. */
@Composable
private fun Comparto(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PanelBackground)
            .border(1.dp, PanelBorder, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Text(
            text = "──── $title ────",
            style = MeteoType.caption,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MeteoType.caption, color = TextSecondary)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(1.dp)
                .background(SubtleLine),
        )
        Text(
            text = value,
            style = MeteoType.value.copy(shadow = TextShadow),
            color = TextPrimary,
        )
    }
}

@Composable
private fun AdvancedToggle(open: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = if (open) "[ − PARAMETRI AVANZATI ]" else "[ + PARAMETRI AVANZATI ]",
        style = MeteoType.caption,
        color = TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun AdvancedRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(text = label, style = MeteoType.caption, color = TextSecondary)
        Text(
            text = value,
            style = MeteoType.value.copy(shadow = TextShadow),
            color = TextPrimary,
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
    val interaction = remember { MutableInteractionSource() }
    val starInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(if (selected) SubtleFill else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Modifier proprio e distinto da quello della riga: il tocco sulla
        // stella deve aggiungere o togliere il preferito, non selezionare
        // anche la citta'.
        Text(
            text = if (favorite) "★" else "☆",
            style = MeteoType.value,
            color = if (favorite) TextPrimary else TextSecondary,
            modifier = Modifier
                .clickable(
                    interactionSource = starInteraction,
                    indication = null,
                    onClick = onToggleFavorite,
                )
                .padding(end = 10.dp),
        )
        val code = isoCountryCode(place.country)
        val label = if (code == null) "· ${place.name}" else "· ${place.name} · $code"
        Text(
            text = label,
            style = MeteoType.value.copy(shadow = TextShadow),
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TextPrimary),
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TempUnit.entries.forEach { unit ->
            PillOption(
                label = unit.symbol,
                active = unit == current,
                onClick = { onChoose(unit) },
            )
        }
    }
}

@Composable
private fun ModelChoice(
    current: WeatherModel,
    onChoose: (WeatherModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WeatherModel.entries.forEach { option ->
            PillOption(
                label = option.label,
                active = option == current,
                onClick = { onChoose(option) },
            )
        }
    }
}

/** Pillola a video invertito quando attiva: sfondo bianco pieno, testo nero. */
@Composable
private fun PillOption(label: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (active) TextPrimary else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MeteoType.value,
            color = if (active) Color.Black else TextSecondary,
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SubtleFill)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = MeteoType.value, color = TextSecondary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MeteoType.value.copy(color = TextPrimary, shadow = TextShadow),
            cursorBrush = SolidColor(TextPrimary),
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
 * dalle impostazioni di sistema) ma non si riapre da solo il dialogo. Un
 * riquadro proprio invece di testo nudo: e' l'azione principale del
 * comparto, deve saltare all'occhio.
 */
@Composable
private fun LocateButton(
    locating: Boolean,
    error: String?,
    onLocate: (canAsk: Boolean, onNeedsPermission: () -> Unit) -> Unit,
) {
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
            text = if (locating) "[ * LOCALIZZAZIONE... ]" else "[ ⌖ TROVAMI ]",
            style = MeteoType.value.copy(shadow = TextShadow),
            color = TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SubtleFill)
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        if (error != null) {
            Text(
                text = error,
                style = MeteoType.caption,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Una croce disegnata, per non tirarsi dietro una libreria di icone. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
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
                color = TextSecondary,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = TextSecondary,
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

/**
 * Nome esteso -> ISO-3166-1 alpha-2, solo per la compattezza dell'elenco.
 * `Place.country` resta il nome esteso: qui si tocca solo la visualizzazione.
 * Un paese non mappato (raro, dai risultati di ricerca live) ripiega sulle
 * sue prime due lettere maiuscole invece di sparire dalla riga.
 */
private fun isoCountryCode(country: String?): String? {
    if (country.isNullOrBlank()) return null
    return COUNTRY_CODES[country] ?: country.take(2).uppercase(java.util.Locale.ROOT)
}

private val COUNTRY_CODES = mapOf(
    "Italia" to "IT",
    "Nuova Zelanda" to "NZ",
    "Norvegia" to "NO",
    "Regno Unito" to "GB",
    "Singapore" to "SG",
    "Islanda" to "IS",
    "Francia" to "FR",
    "Germania" to "DE",
    "Spagna" to "ES",
    "Portogallo" to "PT",
    "Svizzera" to "CH",
    "Austria" to "AT",
    "Belgio" to "BE",
    "Paesi Bassi" to "NL",
    "Svezia" to "SE",
    "Danimarca" to "DK",
    "Finlandia" to "FI",
    "Irlanda" to "IE",
    "Polonia" to "PL",
    "Grecia" to "GR",
    "Australia" to "AU",
    "Stati Uniti" to "US",
    "Canada" to "CA",
    "Giappone" to "JP",
    "Cina" to "CN",
    "India" to "IN",
    "Brasile" to "BR",
    "Messico" to "MX",
    "Russia" to "RU",
    "Turchia" to "TR",
    "Egitto" to "EG",
    "Sud Africa" to "ZA",
    "Marocco" to "MA",
    "Argentina" to "AR",
    "Cile" to "CL",
)
