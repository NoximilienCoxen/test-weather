package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.data.key
import io.github.noximiliencoxen.caelum.ui.UiState
import kotlin.math.roundToInt

/**
 * "Le località": dove si e' adesso, cosa si e' salvato, e come aggiungere.
 *
 * **Le citta' consigliate non ci sono.** Il prototipo ne elencava cinque decise
 * a tavolino - Torino, Bardonecchia, Genova - che non hanno niente a che fare
 * con chi guarda: offrivano una scorciatoia verso posti che nessuno aveva
 * chiesto, mentre chi voleva la propria citta' doveva comunque cercarla. Si
 * aggiunge in un modo solo, cercando, che era gia' l'unico buono per tutti.
 */
@Composable
fun SalaLocalitaScreen(
    state: UiState,
    palette: SalaPalette,
    onPick: (Place) -> Unit,
    onAdd: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onSearch: (String) -> Unit,
    onUseLocation: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.schermoPieno)
            .systemBarsPadding()
            .padding(start = 26.dp, end = 26.dp, top = 12.dp, bottom = 30.dp),
    ) {
        IntestazioneServizio(titolo = "Le località", palette = palette, onIndietro = onClose)

        CampoDiRicerca(
            valore = state.query,
            palette = palette,
            onValore = onSearch,
            modifier = Modifier.padding(top = 18.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            // I risultati stanno **sopra** le salvate mentre si cerca: chi ha
            // appena scritto un nome guarda li', non in fondo.
            val esito = state.searchError
            when {
                esito != null -> item { Nota(esito, palette) }
                state.searching -> item { Nota("Sto cercando…", palette) }
                state.query.trim().length >= 2 && state.results.isEmpty() ->
                    item { Nota("Nessuna località con questo nome", palette) }
            }
            items(state.results, key = { "trovata-" + it.key }) { trovata ->
                RigaLocalita(
                    titolo = trovata.name,
                    nota = listOfNotNull(trovata.admin, trovata.country).joinToString(" · "),
                    temperatura = null,
                    corrente = false,
                    palette = palette,
                    onClick = {
                        onAdd(trovata)
                        onPick(trovata)
                        onSearch("")
                    },
                )
            }
            items(state.favorites, key = { it.key }) { posto ->
                val meteo = state.favoritesWeather[posto.key]
                RigaLocalita(
                    titolo = posto.name,
                    nota = listOfNotNull(
                        posto.admin,
                        meteo?.weatherCode?.let { Wmo.condition(it).lowercase() },
                    ).joinToString(" · "),
                    temperatura = meteo?.temperature?.let { state.unit.from(it).roundToInt() },
                    corrente = posto.key == state.place.key,
                    palette = palette,
                    glifo = meteo?.let { glifoDi(it.weatherCode, null) },
                    onClick = { onPick(posto) },
                    onTogli = { onRemove(posto) },
                )
            }
        }

        Text(
            text = "Aggiungi la posizione attuale",
            style = SalaType.rowTitle,
            color = palette.accentInk,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(palette.accent)
                .clickable {
                    onUseLocation()
                    onClose()
                }
                .padding(horizontal = 20.dp, vertical = 15.dp),
        )
    }
}

@Composable
private fun Nota(testo: String, palette: SalaPalette) {
    Text(text = testo, style = SalaType.footnote, color = palette.inkFaint)
}

/**
 * Una riga della lista: pastiglia larga, segno del tempo, nome, temperatura.
 *
 * La citta' che si sta guardando ha un fondo suo e il nome in accento - **e non
 * un pallino che pulsa**. Sarebbe la scelta ovvia e sarebbe la trappola #8
 * riaperta per dire "sei qui": un'animazione perenne che costa batteria tutto
 * il giorno per un'informazione che non cambia mai.
 */
@Composable
private fun RigaLocalita(
    titolo: String,
    nota: String,
    temperatura: Int?,
    corrente: Boolean,
    palette: SalaPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glifo: GlifoMeteo? = null,
    onTogli: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(if (corrente) palette.accent.copy(alpha = 0.18f) else palette.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(palette.maniglia),
            contentAlignment = Alignment.Center,
        ) {
            if (glifo != null) IconaMeteo(glifo, palette)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titolo,
                style = SalaType.value,
                color = if (corrente) palette.accent else palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (nota.isNotEmpty()) {
                Text(
                    text = nota,
                    style = SalaType.rowNote,
                    color = palette.inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (temperatura != null) {
            Row(verticalAlignment = Alignment.Top) {
                Text(text = temperatura.toString(), style = SalaType.giant(28), color = palette.ink)
                Text(
                    text = "°",
                    style = SalaType.hourLabel,
                    color = palette.accent,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        if (onTogli != null && !corrente) {
            Text(
                text = "×",
                style = SalaType.cardTitle,
                color = palette.inkFaint,
                modifier = Modifier.clickable(onClick = onTogli).padding(start = 4.dp),
            )
        }
    }
}

/** Il campo di ricerca: una pastiglia larga, come ogni altra cosa qui dentro. */
@Composable
private fun CampoDiRicerca(
    valore: String,
    palette: SalaPalette,
    onValore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(palette.chip)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(13.dp).clip(CircleShape).background(palette.inkFaint))
        Box(modifier = Modifier.weight(1f)) {
            if (valore.isEmpty()) {
                Text(
                    text = "Cerca una città o un comune",
                    style = SalaType.rowTitle,
                    color = palette.inkFaint,
                )
            }
            BasicTextField(
                value = valore,
                onValueChange = onValore,
                singleLine = true,
                textStyle = SalaType.rowTitle.copy(color = palette.ink),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

