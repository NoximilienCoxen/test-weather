package com.forli.meteo.ui.widgetconfig

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.DeviceLocation
import com.forli.meteo.data.Place
import com.forli.meteo.data.WeatherRepository
import com.forli.meteo.data.key
import com.forli.meteo.prefs.SettingsPrefs
import com.forli.meteo.ui.theme.MeteoType
import com.forli.meteo.widget.WidgetKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

private enum class LocationSource { GPS, FAVORITES, SEARCH }

private const val SEARCH_DEBOUNCE_MS = 320L

/**
 * Anche qui i colori vengono dal tema.
 *
 * Era la terza copia della stessa tavolozza - dopo quella delle impostazioni e
 * quella del dettaglio - con gli stessi nomi e valori appena diversi. Adesso
 * sono i token Material, gia' calcolati per contrasto sulla superficie che li
 * ospita.
 */
private val ScreenBackground: Color
    @Composable get() = MaterialTheme.colorScheme.surface
private val Primary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
private val Secondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val FieldBackground: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
private val SelectedBackground: Color
    @Composable get() = MaterialTheme.colorScheme.secondaryContainer

/**
 * Schermata di configurazione aperta al posizionamento del widget: sceglie
 * la localita' (GPS, preferiti, ricerca) e i colori, poi salva.
 */
@Composable
fun WidgetConfigScreen(
    onSave: (place: Place?, useLocation: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Quale dei tre widget si sta configurando, per l'anteprima e la localita'. */
    kind: WidgetKind? = null,
) {
    // La luna e' la stessa da qualunque parte la si guardi: chiederle una
    // citta' sarebbe una domanda senza conseguenze.
    val showLocation = kind != WidgetKind.LUNA
    val context = LocalContext.current
    val settingsPrefs = remember { SettingsPrefs(context) }

    var source by remember { mutableStateOf(LocationSource.SEARCH) }
    var useLocation by remember { mutableStateOf(false) }
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var locationGranted by remember { mutableStateOf(DeviceLocation.granted(context)) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    val favorites by remember(settingsPrefs) { settingsPrefs.settings.map { it.favorites } }
        .collectAsState(initial = emptyList())

    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        locationGranted = granted
        useLocation = granted
    }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(SEARCH_DEBOUNCE_MS)
        results = WeatherRepository.search(query).getOrDefault(emptyList())
        searching = false
    }

    val canSave = !showLocation || useLocation || selectedPlace != null

    Column(modifier = modifier.fillMaxSize().background(ScreenBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(10.dp))
            Text(text = "CONFIGURA WIDGET", style = MeteoType.caption, color = Primary)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (showLocation) {
                item { SectionTitle("LOCALITÀ") }
                item {
                    SourceTabs(
                        current = source,
                        onChoose = { chosen ->
                            source = chosen
                            useLocation = chosen == LocationSource.GPS && locationGranted
                        },
                    )
                }
                item { Spacer(Modifier.height(10.dp)) }

                when (source) {
                    LocationSource.GPS -> item {
                        GpsSection(
                            granted = locationGranted,
                            onRequest = {
                                askPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            },
                        )
                    }

                    LocationSource.FAVORITES -> {
                        if (favorites.isEmpty()) {
                            item {
                                Text(
                                    text = "NESSUNA CITTÀ NEI PREFERITI. SALVANE UNA DALLE " +
                                        "IMPOSTAZIONI DELL'APP TOCCANDO LA STELLA.",
                                    style = MeteoType.caption,
                                    color = Secondary,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        } else {
                            items(items = favorites, key = { it.key }) { place ->
                                PlaceOption(
                                    place = place,
                                    selected = selectedPlace?.key == place.key,
                                    onClick = {
                                        // Scegliere una città disattiva esplicitamente il GPS:
                                        // i due sono mutuamente esclusivi.
                                        selectedPlace = place
                                        useLocation = false
                                    },
                                )
                            }
                        }
                    }

                    LocationSource.SEARCH -> {
                        item {
                            SearchField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = "CERCA UNA CITTÀ",
                            )
                        }
                        item {
                            val message = when {
                                searching -> "RICERCA IN CORSO…"
                                query.trim().length >= 2 && results.isEmpty() -> "NESSUN RISULTATO"
                                query.isBlank() -> "OPPURE SCEGLI FRA QUESTE"
                                else -> null
                            }
                            if (message != null) {
                                Text(
                                    text = message,
                                    style = MeteoType.caption,
                                    color = Secondary,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                                )
                            }
                        }
                        val options = results.ifEmpty {
                            if (query.isBlank()) Place.SUGGESTIONS else emptyList()
                        }
                        items(items = options, key = { it.key }) { place ->
                            PlaceOption(
                                place = place,
                                selected = selectedPlace?.key == place.key,
                                onClick = {
                                    // Scegliere una città disattiva esplicitamente il GPS:
                                    // i due sono mutuamente esclusivi.
                                    selectedPlace = place
                                    useLocation = false
                                },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(26.dp)) }
            }

            item { Spacer(Modifier.height(28.dp)) }
            item {
                SaveButton(
                    enabled = canSave,
                    onClick = { onSave(selectedPlace, useLocation) },
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column {
        Spacer(Modifier.height(10.dp))
        Text(text = text, style = MeteoType.caption, color = Secondary)
        Box(
            modifier = Modifier
                .padding(top = 6.dp, bottom = 10.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Composable
private fun SourceTabs(current: LocationSource, onChoose: (LocationSource) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceTab("POSIZIONE", current == LocationSource.GPS) { onChoose(LocationSource.GPS) }
        SourceTab("PREFERITI", current == LocationSource.FAVORITES) { onChoose(LocationSource.FAVORITES) }
        SourceTab("CERCA", current == LocationSource.SEARCH) { onChoose(LocationSource.SEARCH) }
    }
}

@Composable
private fun SourceTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) Primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = label, style = MeteoType.value, color = if (selected) MaterialTheme.colorScheme.surface else Secondary)
    }
}

@Composable
private fun GpsSection(granted: Boolean, onRequest: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = if (granted) {
                "IL WIDGET USERÀ LA POSIZIONE DEL TELEFONO AD OGNI AGGIORNAMENTO."
            } else {
                "SERVE IL PERMESSO DI POSIZIONE APPROSSIMATA."
            },
            style = MeteoType.value,
            color = Primary,
        )
        if (!granted) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Primary)
                    .pointerInput(Unit) { detectTapGestures { onRequest() } }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(text = "CONCEDI IL PERMESSO", style = MeteoType.value, color = Color.Black)
            }
        }
    }
}

@Composable
private fun PlaceOption(place: Place, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) SelectedBackground else Color.Transparent)
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = place.name.uppercase(), style = MeteoType.value, color = Primary)
            if (place.detail.isNotBlank()) {
                Text(
                    text = place.detail.uppercase(),
                    style = MeteoType.caption,
                    color = Secondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape).background(Primary),
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(FieldBackground)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = MeteoType.value, color = Secondary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MeteoType.value.copy(color = Primary),
            cursorBrush = SolidColor(Primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Primary else Primary.copy(alpha = 0.3f))
            .pointerInput(enabled) { if (enabled) detectTapGestures { onClick() } }
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = "SALVA",
            style = MeteoType.title,
            color = Color.Black,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
