package io.github.noximiliencoxen.caelum.ui.widgetconfig

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.noximiliencoxen.caelum.data.DeviceLocation
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.WeatherRepository
import io.github.noximiliencoxen.caelum.data.key
import io.github.noximiliencoxen.caelum.prefs.CardTheme
import io.github.noximiliencoxen.caelum.prefs.SettingsPrefs
import io.github.noximiliencoxen.caelum.ui.sala.CampoDiRicerca
import io.github.noximiliencoxen.caelum.ui.sala.FiloGruppo
import io.github.noximiliencoxen.caelum.ui.sala.GlifoMeteo
import io.github.noximiliencoxen.caelum.ui.sala.GruppoImpostazioni
import io.github.noximiliencoxen.caelum.ui.sala.IconaMeteo
import io.github.noximiliencoxen.caelum.ui.sala.IntestazioneServizio
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.SceltaSegmentata
import io.github.noximiliencoxen.caelum.ui.sala.VoceImpostazione
import io.github.noximiliencoxen.caelum.ui.sala.salaPalette
import io.github.noximiliencoxen.caelum.widget.WidgetConfig
import io.github.noximiliencoxen.caelum.widget.WidgetKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

private enum class LocationSource { GPS, FAVORITES, SEARCH }

private const val SEARCH_DEBOUNCE_MS = 320L

/**
 * Schermata di configurazione aperta al posizionamento del widget: sceglie
 * la localita' (GPS, preferiti, ricerca), poi salva.
 *
 * ## Ha la faccia dell'app, non quella di un modulo di sistema
 *
 * Era l'ultima schermata rimasta sui token Material, tutta in maiuscolo, con un
 * SALVA nero su grigio e il titolo che finiva sotto l'orologio della barra di
 * stato. Adesso e' fatta con gli stessi pezzi delle impostazioni - intestazione
 * col tondo per tornare, gruppi col titolo fuori dal riquadro, selettore a
 * segmenti, campo di ricerca delle localita' - e prende il tema che l'utente ha
 * scelto nell'app: chi ha chiesto lo scuro non deve ritrovarsi il chiaro solo
 * perche' sta mettendo un widget.
 */
@Composable
fun WidgetConfigScreen(
    onSave: (place: Place?, useLocation: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Quale widget si sta configurando, per l'anteprima e la localita'. */
    kind: WidgetKind? = null,
    /**
     * Cosa aveva gia' scelto questo widget.
     *
     * Vuota al primo piazzamento, piena quando la schermata viene riaperta per
     * cambiare la citta'. Serve a mostrare la scelta corrente invece di
     * chiederla di nuovo da zero.
     */
    initial: WidgetConfig = WidgetConfig(),
    /** Uscire senza salvare: il sistema considera annullato il posizionamento. */
    onCancel: () -> Unit = {},
) {
    // Chi vuole una citta' lo dice il widget stesso, in WidgetKind: qui non si
    // elencano piu' i tipi a mano. Un widget nuovo che non ne vuole - come la
    // luna, che e' la stessa da qualunque parte la si guardi - lo dichiara la'
    // e questa schermata si adegua da sola.
    //
    // Sconosciuto vuol dire che il lanciatore non ha ancora agganciato
    // l'istanza: si chiede la citta', perche' quasi tutti i widget la vogliono
    // e una domanda in piu' si annulla, una configurazione mancante no.
    val showLocation = kind?.needsPlace ?: true
    val context = LocalContext.current
    val settingsPrefs = remember { SettingsPrefs(context) }

    // Il tema dell'app. "Segui il cielo" qui non ha un cielo da seguire - la
    // previsione non e' ancora stata letta - e ripiega su quello del telefono.
    val tema by remember(settingsPrefs) { settingsPrefs.settings.map { it.cardTheme } }
        .collectAsStateWithLifecycle(initialValue = CardTheme.AUTO)
    val scuro = when (tema) {
        CardTheme.CHIARO -> false
        CardTheme.SCURO -> true
        CardTheme.AUTO -> isSystemInDarkTheme()
    }
    val palette = remember(scuro) { salaPalette(dk = if (scuro) 1f else 0f, crepuscolo = 0f, chiusura = 0f) }

    var locationGranted by remember { mutableStateOf(DeviceLocation.granted(context)) }
    var useLocation by remember { mutableStateOf(initial.useLocation && locationGranted) }
    var selectedPlace by remember { mutableStateOf(initial.place) }
    // La scheda che si apre e' quella da cui viene la scelta gia' fatta: chi
    // riapre per cambiare citta' trova sotto il dito quel che aveva usato.
    var source by remember {
        mutableStateOf(
            if (initial.useLocation) LocationSource.GPS else LocationSource.SEARCH,
        )
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    val favorites by remember(settingsPrefs) { settingsPrefs.settings.map { it.favorites } }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // I preferiti arrivano da un Flow, cioe' un fotogramma dopo: se la citta'
    // gia' scelta e' fra questi, la scheda si sposta su "Preferiti" appena si
    // sa. Una volta sola, e solo se nessuno ha ancora toccato le schede - se no
    // strapperebbe di mano la navigazione a chi ha gia' iniziato a scegliere.
    var tabSettled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(favorites) {
        if (tabSettled || initial.useLocation) return@LaunchedEffect
        val chosen = initial.place ?: return@LaunchedEffect
        if (favorites.any { it.key == chosen.key }) {
            source = LocationSource.FAVORITES
            tabSettled = true
        }
    }

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
    // Scegliere una città disattiva esplicitamente il GPS: i due sono
    // mutuamente esclusivi.
    val scegli: (Place) -> Unit = { place ->
        selectedPlace = place
        useLocation = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.schermoPieno)
            // Il titolo stava sotto l'orologio: la schermata e' a tutto
            // schermo (`enableEdgeToEdge`) e nessuno lasciava il posto alle
            // barre. E con la tastiera aperta il bottone deve salirle sopra.
            .systemBarsPadding()
            .imePadding()
            .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 16.dp),
    ) {
        IntestazioneServizio(titolo = nomeDelWidget(kind), palette = palette, onIndietro = onCancel)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Quale widget si sta posizionando e dove guardera'. Chi ne
            // aggancia tre uguali di aspetto deve sapere a quale stia
            // rispondendo.
            WidgetIdentity(kind = kind, place = selectedPlace, following = useLocation, palette = palette)

            if (showLocation) {
                Column {
                    Text(
                        text = "DA DOVE PRENDERE I DATI",
                        style = SalaType.sectionLabel,
                        color = palette.inkFaint,
                        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
                    )
                    SceltaSegmentata(
                        voci = listOf(
                            LocationSource.GPS to "Posizione",
                            LocationSource.FAVORITES to "Salvate",
                            LocationSource.SEARCH to "Cerca",
                        ),
                        scelta = source,
                        palette = palette,
                        onScegli = { chosen ->
                            source = chosen
                            tabSettled = true
                            useLocation = chosen == LocationSource.GPS && locationGranted
                        },
                    )
                }

                when (source) {
                    LocationSource.GPS -> GruppoImpostazioni(titolo = "POSIZIONE", palette = palette) {
                        VoceImpostazione(
                            titolo = "Dove si trova il telefono",
                            nota = if (locationGranted) {
                                "il widget segue la posizione a ogni aggiornamento"
                            } else {
                                "serve il permesso di posizione approssimata"
                            },
                            palette = palette,
                            onClick = if (locationGranted) {
                                null
                            } else {
                                { askPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                            },
                            coda = {
                                if (locationGranted) {
                                    Spunta(scelta = true, palette = palette)
                                } else {
                                    PastigliaAzione("Consenti", palette)
                                }
                            },
                        )
                    }

                    LocationSource.FAVORITES -> if (favorites.isEmpty()) {
                        Nota(
                            "Nessuna località salvata. Aggiungile dall'app, in Impostazioni › Località.",
                            palette,
                        )
                    } else {
                        GruppoImpostazioni(titolo = "LOCALITÀ SALVATE", palette = palette) {
                            ElencoPosti(favorites, selectedPlace, palette, scegli)
                        }
                    }

                    LocationSource.SEARCH -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CampoDiRicerca(
                            valore = query,
                            palette = palette,
                            onValore = { query = it },
                        )
                        val options = results.ifEmpty {
                            if (query.isBlank()) Place.SUGGESTIONS else emptyList()
                        }
                        when {
                            searching -> Nota("Sto cercando…", palette)
                            query.trim().length >= 2 && results.isEmpty() ->
                                Nota("Nessuna località con questo nome", palette)
                        }
                        if (options.isNotEmpty()) {
                            GruppoImpostazioni(
                                titolo = if (query.isBlank()) "OPPURE UNA DI QUESTE" else "RISULTATI",
                                palette = palette,
                            ) {
                                ElencoPosti(options, selectedPlace, palette, scegli)
                            }
                        }
                    }
                }
            }
        }

        BottoneSalva(
            testo = if (initial.useLocation || initial.place != null) "Salva le modifiche" else "Aggiungi il widget",
            enabled = canSave,
            palette = palette,
            onClick = { onSave(selectedPlace, useLocation) },
        )
    }
}

/** "METEO" -> "Meteo", "QUALITÀ DELL'ARIA" -> "Qualità dell'aria". */
private fun nomeDelWidget(kind: WidgetKind?): String =
    kind?.label?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Widget"

@Composable
private fun Nota(testo: String, palette: SalaPalette) {
    Text(
        text = testo,
        style = SalaType.footnote,
        color = palette.inkFaint,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

/** Un elenco di localita' dentro un gruppo: una riga ciascuna, un filo fra l'una e l'altra. */
@Composable
private fun ColumnScope.ElencoPosti(
    posti: List<Place>,
    scelto: Place?,
    palette: SalaPalette,
    onScegli: (Place) -> Unit,
) {
    posti.forEachIndexed { i, place ->
        if (i > 0) FiloGruppo(palette)
        val selezionato = scelto?.key == place.key
        VoceImpostazione(
            titolo = place.name,
            nota = place.detail.takeIf { it.isNotBlank() },
            palette = palette,
            onClick = { onScegli(place) },
            coda = { Spunta(scelta = selezionato, palette = palette) },
        )
    }
}

/**
 * Il segno della scelta: un anello vuoto, o un disco d'accento con la spunta.
 *
 * Era un puntino di otto punti in fondo alla riga, che su una lista di nove
 * citta' in maiuscolo non si trovava.
 */
@Composable
private fun Spunta(scelta: Boolean, palette: SalaPalette) {
    val fondo by animateColorAsState(
        targetValue = if (scelta) palette.accent else palette.accent.copy(alpha = 0f),
        label = "spunta",
    )
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(fondo)
            .then(if (scelta) Modifier else Modifier.border(2.dp, palette.maniglia, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (scelta) {
            Canvas(modifier = Modifier.size(12.dp)) {
                val s = 2.2.dp.toPx()
                val w = size.width
                val h = size.height
                drawLine(palette.accentInk, Offset(0f, h * 0.55f), Offset(w * 0.38f, h * 0.9f), s, StrokeCap.Round)
                drawLine(palette.accentInk, Offset(w * 0.38f, h * 0.9f), Offset(w, h * 0.12f), s, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun PastigliaAzione(testo: String, palette: SalaPalette) {
    Text(
        text = testo,
        style = SalaType.pill,
        color = palette.accentInk,
        modifier = Modifier
            .clip(CircleShape)
            .background(palette.accent)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** Il bottone in fondo: pieno d'accento quando si puo' salvare, spento quando manca la scelta. */
@Composable
private fun BottoneSalva(testo: String, enabled: Boolean, palette: SalaPalette, onClick: () -> Unit) {
    Text(
        text = testo,
        style = SalaType.rowTitle,
        color = if (enabled) palette.accentInk else palette.inkFaint,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (enabled) palette.accent else palette.maniglia)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

/**
 * Che widget e' e dove guardera'.
 *
 * **Non un'anteprima dei numeri.** I numeri non ci sono ancora - il widget non
 * e' stato salvato e la sua richiesta non e' mai partita - e disegnarne di
 * finti sarebbe peggio che non disegnare niente: chi guarda non ha modo di
 * sapere che sono finti, e i widget di questa app sono immagini dipinte, quindi
 * un'anteprima falsa sembrerebbe quella vera.
 *
 * Quel che si puo' dire con onesta' e' l'identita': quale widget e, con la
 * figuretta dell'app, quale localita' andra' a leggere.
 */
@Composable
private fun WidgetIdentity(kind: WidgetKind?, place: Place?, following: Boolean, palette: SalaPalette) {
    val dove = when {
        kind != null && !kind.needsPlace -> "Uguale da qualunque parte lo guardi"
        following -> "Seguirà la posizione del telefono"
        place != null -> "Mostrerà ${place.name}"
        else -> "Scegli qui sotto da dove prendere i dati"
    }
    val (glifo, notte) = when (kind) {
        WidgetKind.LUNA -> GlifoMeteo.SOLE to true
        WidgetKind.ARIA -> GlifoMeteo.NEBBIA to false
        WidgetKind.SETTIMANA -> GlifoMeteo.PIOGGIA to false
        else -> GlifoMeteo.POCO to false
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(palette.accent.copy(alpha = 0.14f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(palette.maniglia),
            contentAlignment = Alignment.Center,
        ) {
            IconaMeteo(glifo, modifier = Modifier.size(28.dp), notte = notte)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = kind?.let { "WIDGET ${it.label}" } ?: "NUOVO WIDGET",
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
            )
            Text(
                text = dove,
                style = SalaType.value,
                color = palette.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
