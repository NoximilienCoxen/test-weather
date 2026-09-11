package io.github.noximiliencoxen.caelum.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.Tema
import io.github.noximiliencoxen.caelum.data.WeatherModel
import io.github.noximiliencoxen.caelum.data.key
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.common.MeteoIconButton
import io.github.noximiliencoxen.caelum.ui.common.MeteoPill
import io.github.noximiliencoxen.caelum.ui.common.CloseIcon
import io.github.noximiliencoxen.caelum.ui.theme.MeteoType
import java.time.format.DateTimeFormatter

/**
 * I colori vengono dal tema, non da una tavolozza privata.
 *
 * Questo file ne dichiarava otto per conto suo, `WidgetConfigScreen` altrettanti,
 * `DetailChrome` un terzo gruppo: tre copie parallele destinate a divergere, e
 * divergevano. Ora sono i token Material, che a loro volta si calcolano per
 * contrasto sulla superficie che li ospita (`ui/theme/Contrast.kt`).
 */
private val SettingsPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
private val SettingsSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val SettingsLine: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val SettingsFieldBg: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
private val SettingsBlockBg: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
private val SettingsSelectedBg: Color
    @Composable get() = MaterialTheme.colorScheme.secondaryContainer

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
    onChooseTema: (Tema) -> Unit,
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

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 24.dp, top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeteoIconButton(onClick = onClose, contentDescription = "Chiudi le impostazioni") {
                CloseIcon(SettingsPrimary)
            }
            Text(
                text = "IMPOSTAZIONI",
                style = MaterialTheme.typography.titleMedium,
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
            // ── DOVE ────────────────────────────────────────────────────
            //
            // **L'ordine e' rovesciato, e non e' un vezzo.** Prima veniva il
            // nome della localita' a corpo venticinque, poi il dettaglio, poi le
            // coordinate, poi un divisore, poi la posizione, e solo allora la
            // ricerca: quasi trecento punti prima di arrivare all'unica cosa
            // che chi apre questa schermata sta quasi sempre cercando. Su un
            // telefono la ricerca finiva sotto la piega, e da li' nasceva il
            // vicolo cieco vero - scelta una localita' proposta, non si tornava
            // piu' indietro perche' i due modi per farlo erano entrambi fuori
            // schermo.
            //
            // Adesso vengono per primi i due comandi, e il posto in cui si e'
            // viene dopo: si legge come "dove vuoi andare", non come "dove sei".
            item { SectionTitle("DOVE") }
            item {
                SearchField(
                    value = state.query,
                    onValueChange = onQuery,
                    placeholder = "CERCA UNA CITTÀ",
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Block {
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

            // I risultati della ricerca, quando ce ne sono. In colonna e non a
            // pillole: un chip mostra solo il nome, e fra due omonimi e' la riga
            // di dettaglio a dire quale sia quello giusto.
            //
            // **Le localita' proposte non ci sono piu'.** Erano suggerimenti
            // dell'app - i posti piu' piovosi del mondo, messi li' per poter
            // vedere la pioggia senza aspettarla - e facevano due danni: si
            // prendevano lo spazio della ricerca, e chi ne toccava una per
            // curiosita' si ritrovava altrove senza una via di ritorno
            // evidente. Per vedere la pioggia c'e' `--ei meteo`, che e' il posto
            // giusto per una cosa che serve a chi sviluppa.
            item {
                val message = when {
                    state.searching -> "RICERCA IN CORSO…"
                    state.searchError != null -> state.searchError.uppercase()
                    state.query.trim().length >= 2 && state.results.isEmpty() -> "NESSUN RISULTATO"
                    else -> null
                }
                if (message != null) {
                    Text(
                        text = message,
                        style = MeteoType.caption,
                        color = SettingsSecondary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                } else {
                    Spacer(Modifier.height(10.dp))
                }
            }
            items(state.results, key = { "${it.name}${it.latitude}${it.longitude}" }) { place ->
                PlaceRow(
                    place = place,
                    selected = place.latitude == state.place.latitude &&
                        place.longitude == state.place.longitude,
                    onClick = { onChoosePlace(place) },
                )
            }

            // Dove si sta adesso: sotto i comandi, perche' e' una conferma e non
            // una scelta. La stella resta qui - e' l'unico posto in cui si puo'
            // salvare la localita' corrente.
            item { Spacer(Modifier.height(10.dp)) }
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
                        text = state.place.detail.uppercase(),
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
                        modifier = Modifier.padding(top = 2.dp),
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
                        text = "Nessuna città salvata. Tocca la stella accanto a un nome " +
                            "per tenerla a portata di mano.",
                        style = MaterialTheme.typography.bodyLarge,
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
                        text = "La conversione è immediata: i dati restano quelli, " +
                            "cambia solo come sono scritti.",
                        style = MaterialTheme.typography.bodyLarge,
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
                        text = "ICON-2I è più preciso in Italia. Fuori dall'Italia " +
                            "conviene comunque AUTO.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SettingsSecondary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            // ── ASPETTO ─────────────────────────────────────────────────
            //
            // **Il tema qui non e' una tavolozza, e' un orologio.** In
            // quest'app non esistono due insiemi di colori fra cui scegliere:
            // fondo, testi, tinte delle grandezze e luce della scena dipinta
            // discendono tutti da quanto e' alto il sole all'ora mostrata.
            // Quindi chiaro e scuro **bloccano l'ora** - mezzogiorno e
            // mezzanotte - e tutto il resto si ricalcola da solo. Vedi
            // `data/Tema.kt` per il ragionamento per esteso.
            item { Spacer(Modifier.height(26.dp)) }
            item { SectionTitle("ASPETTO") }
            item {
                Block {
                    TemaChoice(
                        current = state.tema,
                        onChoose = onChooseTema,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    Text(
                        text = "Automatico segue il sole della località: l'app si " +
                            "schiarisce all'alba e si spegne al tramonto. Chiaro e " +
                            "scuro fermano quell'ora.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SettingsSecondary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            // ── LA PROVENIENZA, IN UNA RIGA ─────────────────────────────────
            //
            // Erano undici righe dietro un interruttore: servizio, quattro
            // endpoint, chiave d'accesso, fuso orario, alba e tramonto, fase
            // lunare. Sono uscite, e resta questa.
            //
            // **Non resta per gusto: la CC BY 4.0 la pretende.** I dati di
            // Open-Meteo si possono usare liberamente, anche qui, a condizione
            // di dire da dove vengono. Togliere anche questa riga non sarebbe
            // una schermata piu' pulita, sarebbe distribuire dati altrui senza
            // credito - e il giorno in cui l'app finisce su uno store diventa un
            // problema vero, non una questione di stile.
            //
            // Il resto era documentazione tecnica: chi la vuole la trova nel
            // codice, che e' dove vive la documentazione tecnica.
            item { Spacer(Modifier.height(26.dp)) }
            item {
                Text(
                    text = "Dati meteo di Open-Meteo.com, licenza CC BY 4.0. " +
                        "La fase lunare è calcolata nell'app.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SettingsSecondary,
                )
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
private fun SectionTitle(text: String) {
    // **Niente piu' ramo apribile.** I due parametri `open` e `onToggle`
    // servivano a un blocco solo - il papiro della provenienza - e con quello
    // se ne vanno: un titolo che *puo'* essere un interruttore e' un titolo che
    // ogni chiamante deve chiedersi se lo sia.
    Column {
        Spacer(Modifier.height(10.dp))
        Text(
            text = text,
            style = MeteoType.caption,
            color = SettingsSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
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
    MeteoPill(
        label = place.name.uppercase(),
        selected = selected,
        onClick = onClick,
        role = androidx.compose.ui.semantics.Role.RadioButton,
    )
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
            MeteoPill(
                label = unit.symbol,
                selected = unit == current,
                onClick = { onChoose(unit) },
                role = androidx.compose.ui.semantics.Role.RadioButton,
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WeatherModel.entries.forEach { model ->
            MeteoPill(
                label = model.label,
                selected = model == current,
                onClick = { onChoose(model) },
                role = androidx.compose.ui.semantics.Role.RadioButton,
            )
        }
    }
}

/**
 * La stella che salva o toglie il posto corrente dai preferiti.
 *
 * Un pulsante vero da 48dp e non un glifo di testo da 34: sotto quella misura
 * un dito manca il bersaglio, ed era il caso di tutti e tre i pulsanti tondi
 * dell'app. Il nome cambia con lo stato, cosi' chi ascolta sente cosa fara' il
 * tocco invece di sentire "stella".
 */
/**
 * Chiaro, scuro, automatico.
 *
 * Tre pillole e non un interruttore a due stati: "automatico" non e' il mezzo
 * fra chiaro e scuro, e' una terza cosa - lascia decidere al sole - e un
 * cursore lo farebbe sembrare una via di mezzo.
 */
@Composable
private fun TemaChoice(
    current: Tema,
    onChoose: (Tema) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tema.entries.forEach { tema ->
            MeteoPill(
                label = tema.label,
                selected = tema == current,
                onClick = { onChoose(tema) },
                role = androidx.compose.ui.semantics.Role.RadioButton,
            )
        }
    }
}

@Composable
private fun FavoriteStar(filled: Boolean, onClick: () -> Unit) {
    val color = SettingsPrimary
    MeteoIconButton(
        onClick = onClick,
        contentDescription = if (filled) "Togli dai preferiti" else "Salva nei preferiti",
    ) {
        Text(
            text = if (filled) "★" else "☆",
            style = MaterialTheme.typography.headlineMedium,
            color = color,
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
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = SettingsSecondary,
            )
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
