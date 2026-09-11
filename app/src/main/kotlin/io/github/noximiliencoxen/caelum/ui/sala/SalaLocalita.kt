package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.CurrentWeather
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.data.key
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.asPlainDegrees
import io.github.noximiliencoxen.caelum.ui.common.MinTouchTarget

/**
 * "Le località": dove si e' adesso, cosa si e' salvato, e come aggiungere.
 *
 * **Le citta' consigliate non ci sono piu'.** Sotto "Aggiungi" c'era una fila
 * di nomi decisi da noi - Milano, Roma, e altre - che non avevano niente a che
 * fare con chi guarda: offrivano una scorciatoia verso posti che nessuno aveva
 * chiesto, e chi voleva la propria citta' doveva comunque cercarla. Adesso si
 * aggiunge in un modo solo, cercando, che era gia' l'unico modo che funzionava
 * per tutti.
 */
@Composable
fun SalaLocalitaScreen(
    state: UiState,
    palette: SalaPalette,
    onPick: (Place) -> Unit,
    onAdd: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onSearch: (String) -> Unit,
    onClose: () -> Unit,
) {
    val favorites = state.favorites
    var cercando by remember { mutableStateOf(false) }

    SalaServiceScaffold(
        palette = palette,
        title = "Le località",
        blobs = SalaBlobs.localita,
        onClose = onClose,
    ) { modifier ->
        LazyColumn(modifier = modifier) {
            item {
                Column(modifier = Modifier.padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Le tue località", style = SalaType.pageTitle, color = palette.ink)
                    Text(
                        text = "${favorites.size} salvate",
                        style = SalaType.sectionLabel,
                        color = palette.inkAccent,
                    )
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

            item {
                Column(modifier = Modifier.padding(top = 40.dp)) {
                    Text(
                        text = if (cercando) "Chiudi la ricerca" else "Aggiungi una città",
                        style = SalaType.sectionLabel,
                        color = palette.inkAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MinTouchTarget)
                            .clickable {
                                cercando = !cercando
                                if (!cercando) onSearch("")
                            }
                            .padding(top = 12.dp),
                    )
                    if (cercando) {
                        CampoDiRicerca(
                            valore = state.query,
                            palette = palette,
                            onValore = onSearch,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        val esito = state.searchError
                        when {
                            esito != null -> Riga(esito, palette.inkSoft)
                            state.searching -> Riga("Sto cercando…", palette.inkSoft)
                            state.query.trim().length >= 2 && state.results.isEmpty() ->
                                Riga("Nessuna località con questo nome", palette.inkSoft)
                        }
                        state.results.forEach { trovata ->
                            Text(
                                text = listOfNotNull(trovata.name, trovata.admin).joinToString(" · "),
                                style = SalaType.value,
                                color = palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MinTouchTarget)
                                    .clickable {
                                        onAdd(trovata)
                                        onPick(trovata)
                                        cercando = false
                                        onSearch("")
                                    }
                                    .padding(top = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Riga(testo: String, colore: Color) {
    Text(
        text = testo,
        style = SalaType.footnote,
        color = colore,
        modifier = Modifier.padding(top = 12.dp),
    )
}

/**
 * Il campo di ricerca, con lo stesso impianto che aveva nelle vecchie
 * impostazioni. Lo usano sia "Le localita'" sia il pannello delle impostazioni:
 * due campi uguali scritti due volte divergono al primo che ne tocca uno.
 */
@Composable
internal fun CampoDiRicerca(
    valore: String,
    palette: SalaPalette,
    onValore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.inkFaint, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (valore.isEmpty()) {
            Text(text = "Nome della città", style = SalaType.body, color = palette.inkSoft)
        }
        BasicTextField(
            value = valore,
            onValueChange = onValore,
            singleLine = true,
            textStyle = SalaType.body.copy(color = palette.ink),
            cursorBrush = SolidColor(palette.inkAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Il segno della citta' che si sta guardando **adesso**.
 *
 * Un cerchio pieno dentro un anello, fermo. Un pallino che pulsa sarebbe la
 * scelta ovvia e sarebbe sbagliata: e' un segno permanente, e da fermo l'app
 * deve disegnare zero fotogrammi (trappola #8). Un'animazione perenne per dire
 * "sei qui" costa batteria tutto il giorno.
 */
@Composable
private fun SegnoCorrente(palette: SalaPalette, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(12.dp)) {
        val r = size.minDimension / 2f
        drawCircle(color = palette.inkAccent, radius = r, style = Stroke(width = 1.4.dp.toPx()))
        drawCircle(color = palette.inkAccent, radius = r * 0.42f)
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
            // Il segno sta **accanto al nome**, non altrove: chi scorre la
            // lista cerca il nome, e un segno lontano da li' si guarda solo se
            // qualcuno ha gia' spiegato che esiste.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isCurrent) SegnoCorrente(palette)
                Text(
                    text = place.name,
                    style = SalaType.cardTitle,
                    color = if (isCurrent) palette.inkAccent else palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
