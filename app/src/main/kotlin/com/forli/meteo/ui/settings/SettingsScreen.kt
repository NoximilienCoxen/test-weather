package com.forli.meteo.ui.settings

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
import androidx.compose.runtime.remember
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
import com.forli.meteo.data.WeatherRepository
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
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current

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
                .padding(horizontal = 24.dp),
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
                Text(
                    text = state.place.detail.uppercase(),
                    style = MeteoType.caption,
                    color = colors.label,
                )
            }
            item {
                // Con la virgola decimale dell'italiano "44,2226, 12,0407" si
                // legge come quattro numeri invece che due. I gradi e i punti
                // cardinali tolgono ogni dubbio.
                Text(
                    text = coordinates(state.place.latitude, state.place.longitude),
                    style = MeteoType.caption,
                    color = colors.label,
                    modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
                )
            }

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
                    state.query.isBlank() -> "OPPURE SCEGLI DALL'ELENCO"
                    else -> null
                }
                Text(
                    text = message.orEmpty(),
                    style = MeteoType.caption,
                    color = colors.label,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
            }

            // Con la ricerca vuota si vedono le scorciatoie. Le prime della
            // lista sono fra i posti piu' piovosi che esistano, ed e' voluto:
            // con una citta' sola non c'era modo di vedere la pioggia se non
            // aspettando che piovesse.
            val places = if (state.results.isNotEmpty()) state.results else Place.SUGGESTIONS
            items(places, key = { "${it.name}${it.latitude}${it.longitude}" }) { place ->
                PlaceRow(
                    place = place,
                    selected = place.latitude == state.place.latitude &&
                        place.longitude == state.place.longitude,
                    onClick = { onChoosePlace(place) },
                )
            }

            item { Spacer(Modifier.height(20.dp)) }
            item { SectionTitle("UNITÀ") }
            item {
                UnitChoice(
                    current = state.unit,
                    onChoose = onChooseUnit,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                )
            }
            item {
                Text(
                    text = "LA CONVERSIONE È IMMEDIATA: I DATI RESTANO QUELLI, " +
                        "CAMBIA SOLO COME SONO SCRITTI.",
                    style = MeteoType.caption,
                    color = colors.label,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
            item { SectionTitle("DA DOVE ARRIVANO I DATI") }
            item { Spacer(Modifier.height(8.dp)) }

            item {
                SourceRow("SERVIZIO", "OPEN-METEO.COM")
            }
            item {
                SourceRow("MODELLO", "MISCELA AUTOMATICA DEI MODELLI NAZIONALI")
            }
            item {
                SourceRow("PREVISIONE", WeatherRepository.FORECAST_ENDPOINT)
            }
            item {
                SourceRow("RICERCA LUOGHI", WeatherRepository.GEOCODING_ENDPOINT)
            }
            item {
                SourceRow(
                    "GRANDEZZE ORARIE",
                    WeatherRepository.HOURLY_VARS.replace(",", ", ").uppercase(),
                )
            }
            item {
                SourceRow(
                    "GRANDEZZE GIORNALIERE",
                    WeatherRepository.DAILY_VARS.replace(",", ", ").uppercase(),
                )
            }
            item {
                SourceRow("FUSO ORARIO", "QUELLO DELLA LOCALITÀ, DEDOTTO DALLE COORDINATE")
            }
            item {
                SourceRow(
                    "ULTIMO AGGIORNAMENTO",
                    state.forecast?.fetchedAt?.format(CLOCK)?.let { "$it, ORA DEL TELEFONO" }
                        ?: "MAI",
                )
            }
            item {
                SourceRow("CHIAVE D'ACCESSO", "NESSUNA: L'USO NON COMMERCIALE È LIBERO")
            }
            item {
                SourceRow("LICENZA DEI DATI", "CC BY 4.0")
            }
            item {
                SourceRow("ALBA E TRAMONTO", "CALCOLATI DA OPEN-METEO PER QUESTE COORDINATE")
            }
            item {
                SourceRow("FASE LUNARE", "CALCOLATA NELL'APP: L'API NON LA FORNISCE")
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val colors = LocalMeteoColors.current
    Column {
        Spacer(Modifier.height(10.dp))
        Text(text = text, style = MeteoType.caption, color = colors.label)
        Box(
            modifier = Modifier
                .padding(top = 6.dp, bottom = 10.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.line),
        )
    }
}

@Composable
private fun SourceRow(label: String, value: String) {
    val colors = LocalMeteoColors.current
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
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
private fun PlaceRow(place: Place, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMeteoColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(if (selected) colors.line.copy(alpha = 0.55f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.name.uppercase(),
                style = MeteoType.value,
                color = colors.text,
            )
            if (place.detail.isNotBlank()) {
                Text(
                    text = place.detail.uppercase(),
                    style = MeteoType.caption,
                    color = colors.label,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    .padding(horizontal = 18.dp, vertical = 8.dp),
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
            .padding(horizontal = 12.dp, vertical = 12.dp),
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
