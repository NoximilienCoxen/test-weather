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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.forli.meteo.ui.theme.MeteoType
import java.time.format.DateTimeFormatter

/**
 * Tavolozza fissa, non quella del tema.
 *
 * Il pannello ha uno sfondo scuro fisso (vedi `MeteoApp.kt`), indipendente
 * dall'ora del giorno: riusare `LocalMeteoColors` qui - pensato per un fondo
 * che va dal grigio chiaro al blu scuro - tornava a far sparire titolo e
 * pulsanti a mezzogiorno. Solo due toni per il testo, mai un grigio scuro o
 * spento: bianco pieno per tutto cio' che e' primario, grigio chiaro
 * brillante per le didascalie.
 */
private val SettingsPrimary = Color.White
private val SettingsSecondary = Color(0xFFEEEEEE)
private val SettingsLine = Color.White.copy(alpha = 0.18f)
private val SettingsFieldBg = Color.White.copy(alpha = 0.10f)
private val SettingsBlockBg = Color.White.copy(alpha = 0.06f)
private val SettingsSelectedBg = Color.White.copy(alpha = 0.14f)
private val SettingsPillBg = Color.White
private val SettingsPillText = Color.Black

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
    onChooseModel: (WeatherModel) -> Unit,
    onToggleFavorite: (Place) -> Unit,
    onUseLocation: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Il permesso lo chiede la schermata, non il ViewModel: e' un dialogo di
    // sistema legato a un'attivita'. Se e' gia' concesso il lanciatore torna
    // subito con un si', quindi non serve un ramo a parte per quel caso.
    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onUseLocation() }

    var sourcesOpen by remember { mutableStateOf(false) }

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
                color = SettingsPrimary,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // DOVE. Il posto corrente e i modi per cambiarlo, tutti nello
            // stesso blocco: prima era un elenco verticale unico in cui il nome
            // della citta', le coordinate, il rilevamento e la ricerca avevano
            // tutti lo stesso peso, e quindi nessuno ne aveva.
            item { SectionTitle("DOVE") }
            item {
                Block {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.place.name.uppercase(),
                            style = MeteoType.title,
                            color = SettingsPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        FavoriteStar(
                            filled = state.favorites.any { it.key == state.place.key },
                            onClick = { onToggleFavorite(state.place) },
                        )
                    }
                    Text(
                        text = listOf(state.place.detail.uppercase())
                            .filter { it.isNotBlank() }
                            .joinToString(),
                        style = MeteoType.caption,
                        color = SettingsSecondary,
                    )
                    // Con la virgola decimale dell'italiano "44,2226, 12,0407"
                    // si legge come quattro numeri invece che due. I gradi e i
                    // punti cardinali tolgono ogni dubbio.
                    Text(
                        text = coordinates(state.place.latitude, state.place.longitude),
                        style = MeteoType.caption,
                        color = SettingsSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                    )
                    Divider()
                    LocationRow(
                        following = state.followsLocation,
                        locating = state.locating,
                        unavailable = state.locationUnavailable,
                        onClick = {
                            askPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
            item {
                SearchField(
                    value = state.query,
                    onValueChange = onQuery,
                    placeholder = "CERCA UNA CITTÀ",
                )
            }

            item {
                val message = when {
                    state.searching -> "RICERCA IN CORSO…"
                    state.searchError != null -> state.searchError.uppercase()
                    state.query.trim().length >= 2 && state.results.isEmpty() -> "NESSUN RISULTATO"
                    state.query.isBlank() -> "OPPURE SCEGLI FRA QUESTE"
                    else -> null
                }
                Text(
                    text = message.orEmpty(),
                    style = MeteoType.caption,
                    color = SettingsSecondary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
            }

            // Le scorciatoie scorrono di lato, non in colonna: sono una manciata
            // di nomi brevi, e in verticale si mangiavano mezza schermata per
            // dire quello che una fila dice in una riga. Le prime della lista
            // sono fra i posti piu' piovosi che esistano, ed e' voluto: con una
            // citta' sola non c'era modo di vedere la pioggia se non aspettando
            // che piovesse.
            if (state.results.isEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(
                            items = Place.SUGGESTIONS,
                            key = { "${it.name}${it.latitude}" },
                        ) { place ->
                            PlaceChip(
                                place = place,
                                selected = place.latitude == state.place.latitude &&
                                    place.longitude == state.place.longitude,
                                onClick = { onChoosePlace(place) },
                            )
                        }
                    }
                }
            } else {
                // I risultati della ricerca restano in colonna, e non e' una
                // dimenticanza: un chip mostra solo il nome, e fra due omonimi
                // e' la riga di dettaglio a dire quale sia quello giusto.
                items(state.results, key = { "${it.name}${it.latitude}${it.longitude}" }) { place ->
                    PlaceRow(
                        place = place,
                        selected = place.latitude == state.place.latitude &&
                            place.longitude == state.place.longitude,
                        onClick = { onChoosePlace(place) },
                    )
                }
            }

            // PREFERITI. Le localita' salvate a mano con la stella, distinte
            // dalle scorciatoie fisse qui sopra: quelle sono suggerimenti
            // dell'app, queste sono scelte di chi guarda.
            item { Spacer(Modifier.height(26.dp)) }
            item { SectionTitle("PREFERITI") }
            item {
                if (state.favorites.isEmpty()) {
                    Text(
                        text = "NESSUNA CITTÀ SALVATA. TOCCA LA STELLA ACCANTO A UN NOME " +
                            "PER TENERLA A PORTATA DI MANO.",
                        style = MeteoType.caption,
                        color = SettingsSecondary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(items = state.favorites, key = { it.key }) { place ->
                            PlaceChip(
                                place = place,
                                selected = place.key == state.place.key,
                                onClick = { onChoosePlace(place) },
                            )
                        }
                    }
                }
            }

            // COME.
            item { Spacer(Modifier.height(26.dp)) }
            item { SectionTitle("COME") }
            item {
                Block {
                    UnitChoice(
                        current = state.unit,
                        onChoose = onChooseUnit,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    Text(
                        text = "LA CONVERSIONE È IMMEDIATA: I DATI RESTANO QUELLI, " +
                            "CAMBIA SOLO COME SONO SCRITTI.",
                        style = MeteoType.caption,
                        color = SettingsSecondary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            // MODELLO. Quale motore numerico calcola la previsione: l'AUTO di
            // Open-Meteo va bene ovunque, l'ICON-2I di ARPAE e' piu' fine ma
            // vede solo l'Italia.
            item { Spacer(Modifier.height(26.dp)) }
            item { SectionTitle("MODELLO") }
            item {
                Block {
                    ModelChoice(
                        current = state.model,
                        onChoose = onChooseModel,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    Text(
                        text = "ICON-2I È PIÙ PRECISO IN ITALIA. FUORI DALL'ITALIA " +
                            "USA COMUNQUE AUTO.",
                        style = MeteoType.caption,
                        color = SettingsSecondary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            // DA DOVE. Undici righe di documentazione che aperte occupavano piu'
            // schermo di tutto il resto messo insieme: non sono impostazioni,
            // sono una dichiarazione di provenienza, e stanno chiuse finche' non
            // le si cerca.
            item { Spacer(Modifier.height(26.dp)) }
            item {
                SectionTitle(
                    text = "DA DOVE ARRIVANO I DATI",
                    open = sourcesOpen,
                    onToggle = { sourcesOpen = !sourcesOpen },
                )
            }
            if (sourcesOpen) {
                item {
                    Block {
                        SourceRow("SERVIZIO", "OPEN-METEO.COM")
                        SourceRow(
                            "MODELLO",
                            if (state.model == WeatherModel.AUTO) {
                                "MISCELA AUTOMATICA DEI MODELLI NAZIONALI"
                            } else {
                                "${state.model.label} (ARPAE, ALTA RISOLUZIONE ITALIA)"
                            },
                        )
                        SourceRow("PREVISIONE", WeatherRepository.FORECAST_ENDPOINT)
                        SourceRow("RICERCA LUOGHI", WeatherRepository.GEOCODING_ENDPOINT)
                        SourceRow(
                            "GRANDEZZE ORARIE",
                            WeatherRepository.HOURLY_VARS.replace(",", ", ").uppercase(),
                        )
                        SourceRow(
                            "GRANDEZZE GIORNALIERE",
                            WeatherRepository.DAILY_VARS.replace(",", ", ").uppercase(),
                        )
                        SourceRow("FUSO ORARIO", "QUELLO DELLA LOCALITÀ, DEDOTTO DALLE COORDINATE")
                        SourceRow(
                            "ULTIMO AGGIORNAMENTO",
                            state.forecast?.fetchedAt?.format(CLOCK)
                                ?.let { "$it, ORA DEL TELEFONO" } ?: "MAI",
                        )
                        SourceRow("CHIAVE D'ACCESSO", "NESSUNA: L'USO NON COMMERCIALE È LIBERO")
                        SourceRow("LICENZA DEI DATI", "CC BY 4.0")
                        SourceRow(
                            "ALBA E TRAMONTO",
                            "CALCOLATI DA OPEN-METEO PER QUESTE COORDINATE",
                        )
                        SourceRow("FASE LUNARE", "CALCOLATA NELL'APP: L'API NON LA FORNISCE")
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

/**
 * La riga che chiede al telefono dove siamo.
 *
 * Dice sempre in che stato e', anche quando non ha funzionato: un permesso
 * negato non e' un errore dell'app, e' una risposta, e la riga la riporta senza
 * riprovare da sola.
 */
@Composable
private fun LocationRow(
    following: Boolean,
    locating: Boolean,
    unavailable: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "USA LA MIA POSIZIONE",
            style = MeteoType.label,
            color = if (following) SettingsPrimary else SettingsSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = when {
                locating -> "CERCO…"
                unavailable -> "NON DISPONIBILE"
                following -> "ATTIVA"
                else -> ""
            },
            style = MeteoType.caption,
            color = if (unavailable) SettingsPrimary else SettingsSecondary,
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    open: Boolean? = null,
    onToggle: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = if (onToggle == null) {
            Modifier
        } else {
            Modifier.clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onToggle,
            )
        },
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MeteoType.caption,
                color = SettingsSecondary,
                modifier = Modifier.weight(1f),
            )
            if (open != null) {
                Text(
                    text = if (open) "CHIUDI" else "MOSTRA",
                    style = MeteoType.caption,
                    color = SettingsPrimary,
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp, bottom = 10.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(SettingsLine),
        )
    }
}

/**
 * Il contenitore di un gruppo: un fondo appena staccato e nient'altro.
 *
 * Appena, e non un riquadro con bordo e ombra: quello che deve separare i
 * gruppi e' la distanza fra loro, non una cornice attorno a ciascuno. Su un
 * fondo che cambia con l'ora del giorno la tinta si ricava dal fondo stesso,
 * cosi' il blocco resta sempre allo stesso passo di distanza sia di giorno che
 * di notte.
 */
@Composable
private fun Block(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SettingsBlockBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

/** Il filo che separa due cose dentro lo stesso blocco. */
@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SettingsLine),
    )
}

/**
 * Una scorciatoia di localita', larga quanto il suo nome.
 *
 * In fila e non in colonna: sono nomi brevi, e uno sotto l'altro si mangiavano
 * mezza schermata per dire quello che una fila dice in una riga.
 */
@Composable
private fun PlaceChip(place: Place, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) SettingsPillBg else SettingsBlockBg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = place.name.uppercase(),
            style = MeteoType.label,
            color = if (selected) SettingsPillText else SettingsPrimary,
        )
    }
}

@Composable
private fun SourceRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(text = label, style = MeteoType.caption, color = SettingsSecondary)
        Text(
            text = value,
            style = MeteoType.value,
            color = SettingsPrimary,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun PlaceRow(place: Place, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(if (selected) SettingsSelectedBg else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.name.uppercase(),
                style = MeteoType.value,
                color = SettingsPrimary,
            )
            if (place.detail.isNotBlank()) {
                Text(
                    text = place.detail.uppercase(),
                    style = MeteoType.caption,
                    color = SettingsSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SettingsPrimary),
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TempUnit.entries.forEach { unit ->
            val active = unit == current
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (active) SettingsPillBg else Color.Transparent)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onChoose(unit) },
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    text = unit.symbol,
                    style = MeteoType.value,
                    color = if (active) SettingsPillText else SettingsSecondary,
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WeatherModel.entries.forEach { model ->
            val active = model == current
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (active) SettingsPillBg else Color.Transparent)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onChoose(model) },
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    text = model.label,
                    style = MeteoType.value,
                    color = if (active) SettingsPillText else SettingsSecondary,
                )
            }
        }
    }
}

/** La stella che salva o toglie il posto corrente dai preferiti. */
@Composable
private fun FavoriteStar(filled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (filled) "★" else "☆",
            style = MeteoType.title,
            color = SettingsPrimary,
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
            .background(SettingsFieldBg)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = MeteoType.value, color = SettingsSecondary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MeteoType.value.copy(color = SettingsPrimary),
            cursorBrush = SolidColor(SettingsPrimary),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
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
                color = SettingsPrimary,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = SettingsPrimary,
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
