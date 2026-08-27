package com.forli.meteo.ui.settings

import android.Manifest
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forli.meteo.data.Place
import com.forli.meteo.data.WeatherRepository
import com.forli.meteo.prefs.TempUnit
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoColors
import com.forli.meteo.ui.theme.MeteoType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Le impostazioni: dove si guarda, in che unita', e da dove arrivano i numeri.
 *
 * Tre moduli e basta, ognuno alto quanto serve.
 *
 * ## Perche' i parametri sono tanti invece di uno
 *
 * Prima arrivava l'intero `UiState`. Comodo, e sbagliato: quell'oggetto cambia
 * ogni volta che cambia **qualunque** cosa nell'app - l'ora scelta, la
 * previsione appena scaricata, il fulmine - e ogni cambiamento ricomponeva
 * l'intera schermata delle impostazioni anche mentre nessuno la stava
 * guardando. Elencando i campi che servono davvero, Compose puo' saltare la
 * ricomposizione quando nessuno di essi e' cambiato, che e' quasi sempre.
 */
@Composable
fun SettingsScreen(
    place: Place,
    unit: TempUnit,
    favourites: List<Place>,
    query: String,
    searching: Boolean,
    results: List<Place>,
    searchError: String?,
    locating: Boolean,
    locationProblem: String?,
    fetchedAt: LocalDateTime?,
    onQuery: (String) -> Unit,
    onChoosePlace: (Place) -> Unit,
    onChooseUnit: (TempUnit) -> Unit,
    onLocate: (canAsk: Boolean, onNeedsPermission: () -> Unit) -> Unit,
    onAddFavourite: (Place) -> Unit,
    onRemoveFavourite: (Place) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val surfaces = rememberSurfaces(colors)

    // Dopo un rifiuto il sistema non mostra piu' la richiesta: chiederla
    // ancora non aprirebbe nulla, quindi si dice cosa fare invece.
    var refused by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onLocate(false) {} else refused = true }

    // L'elenco delle pastiglie si ricompone solo quando cambiano i preferiti,
    // non a ogni passaggio dell'ora.
    val chips = remember(favourites) { chipsOf(favourites) }
    val saved = remember(favourites, place) { favourites.any { it.samePlaceAs(place) } }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 24.dp, top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CloseButton(onClose)
            Text(text = "IMPOSTAZIONI", style = MeteoType.caption, color = colors.label)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item { ModuleTitle("POSIZIONE") }

            item {
                CurrentPlace(
                    place = place,
                    saved = saved,
                    locating = locating,
                    refused = refused,
                    surfaces = surfaces,
                    onToggleSaved = {
                        if (saved) onRemoveFavourite(place) else onAddFavourite(place)
                    },
                    onLocate = {
                        onLocate(!refused) {
                            permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                )
            }

            val problem = locationProblem ?: if (refused) {
                "SI PUÒ CONCEDERE DALLE IMPOSTAZIONI DI ANDROID."
            } else {
                null
            }
            if (problem != null) {
                item {
                    Text(
                        text = problem,
                        style = MeteoType.caption,
                        color = colors.label,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
                    )
                }
            }

            // Il carosello. Scorre di lato invece di allungare la schermata: le
            // scorciatoie sono otto e i preferiti pochi, e in verticale
            // occupavano da soli piu' spazio dei due moduli che seguono.
            item {
                ChipRail(
                    chips = chips,
                    current = place,
                    surfaces = surfaces,
                    onChoose = onChoosePlace,
                    onRemove = onRemoveFavourite,
                )
            }

            item {
                SearchField(
                    value = query,
                    onValueChange = onQuery,
                    surfaces = surfaces,
                    placeholder = "CERCA UNA CITTÀ",
                )
            }

            // I risultati restano righe e non diventano pastiglie, ed e' una
            // scelta: una pastiglia porta il solo nome, e con "Springfield" il
            // nome non basta a dire quale dei ventiquattro. Sono anche
            // transitori - compaiono mentre si cerca e spariscono dopo - quindi
            // non sono l'elenco infinito che si voleva togliere.
            val message = when {
                searching -> "RICERCA IN CORSO…"
                searchError != null -> searchError.uppercase()
                query.trim().length >= 2 && results.isEmpty() -> "NESSUN RISULTATO"
                else -> null
            }
            if (message != null) {
                item {
                    Text(
                        text = message,
                        style = MeteoType.caption,
                        color = colors.label,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
            items(results, key = { "r${it.latitude}${it.longitude}" }) { found ->
                ResultRow(
                    place = found,
                    saved = favourites.any { it.samePlaceAs(found) },
                    onChoose = { onChoosePlace(found) },
                    onSave = { onAddFavourite(found) },
                )
            }

            item { ModuleTitle("UNITÀ", top = 26.dp) }
            item {
                UnitSwitch(
                    current = unit,
                    surfaces = surfaces,
                    onChoose = onChooseUnit,
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 4.dp),
                )
            }

            item { ModuleTitle("DATI", top = 26.dp) }
            item { DataAccordion(fetchedAt = fetchedAt, surfaces = surfaces) }
        }
    }
}

// ---------------------------------------------------------------------------
// Posizione
// ---------------------------------------------------------------------------

@Composable
private fun CurrentPlace(
    place: Place,
    saved: Boolean,
    locating: Boolean,
    refused: Boolean,
    surfaces: Surfaces,
    onToggleSaved: () -> Unit,
    onLocate: () -> Unit,
) {
    val colors = LocalMeteoColors.current
    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = place.name.uppercase(),
                style = MeteoType.label,
                color = colors.text,
                modifier = Modifier.weight(1f, fill = false),
            )
            StarToggle(saved = saved, onClick = onToggleSaved)
            Spacer(Modifier.weight(1f))
            // "Trovami" sta sulla stessa riga del nome e non sotto: e' cio' che
            // sostituisce quel nome, e le due cose vanno guardate insieme.
            Pill(
                text = when {
                    locating -> "CERCO…"
                    refused -> "NEGATO"
                    else -> "TROVAMI"
                },
                filled = !locating && !refused,
                enabled = !locating && !refused,
                surfaces = surfaces,
                onClick = onLocate,
            )
        }
        if (place.detail.isNotBlank()) {
            Text(
                text = place.detail.uppercase(),
                style = MeteoType.caption,
                color = colors.label,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // Con la virgola decimale dell'italiano "44,2226, 12,0407" si legge
        // come quattro numeri invece che due. I gradi e i punti cardinali
        // tolgono ogni dubbio.
        Text(
            text = coordinates(place.latitude, place.longitude),
            style = MeteoType.caption,
            color = colors.label,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** Una localita' del carosello, con o senza la croce per toglierla. */
@Immutable
private class Chip(val place: Place, val removable: Boolean)

/**
 * Preferiti prima, scorciatoie dopo, senza ripetizioni.
 *
 * Le scorciatoie che sono gia' fra i preferiti non compaiono due volte: la
 * stessa localita' in due pastiglie diverse, una con la croce e una senza,
 * sembrerebbe un difetto.
 */
private fun chipsOf(favourites: List<Place>): List<Chip> {
    val saved = favourites.map { Chip(it, removable = true) }
    val rest = Place.SUGGESTIONS
        .filterNot { shortcut -> favourites.any { it.samePlaceAs(shortcut) } }
        .map { Chip(it, removable = false) }
    return saved + rest
}

@Composable
private fun ChipRail(
    chips: List<Chip>,
    current: Place,
    surfaces: Surfaces,
    onChoose: (Place) -> Unit,
    onRemove: (Place) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { "c${it.place.latitude}${it.place.longitude}" }) { chip ->
            PlaceChip(
                place = chip.place,
                selected = chip.place.samePlaceAs(current),
                surfaces = surfaces,
                onClick = { onChoose(chip.place) },
                onRemove = if (chip.removable) {
                    { onRemove(chip.place) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun PlaceChip(
    place: Place,
    selected: Boolean,
    surfaces: Surfaces,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val colors = LocalMeteoColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            // Opaca, non velata: una pastiglia al venti per cento di bianco su
            // un fondo scuro non legge come pastiglia, legge come sporco.
            .background(if (selected) colors.pillBackground else surfaces.raised)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(
                start = 14.dp,
                end = if (onRemove != null) 4.dp else 14.dp,
                top = 9.dp,
                bottom = 9.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = place.name.uppercase(),
            style = MeteoType.caption,
            color = if (selected) colors.pillText else colors.text,
        )
        if (onRemove != null) {
            GlyphButton(
                glyph = Glyph.CROSS,
                tint = if (selected) colors.pillText else colors.label,
                size = 26.dp,
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun ResultRow(
    place: Place,
    saved: Boolean,
    onChoose: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = LocalMeteoColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onChoose)
            .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = place.name.uppercase(), style = MeteoType.value, color = colors.text)
            if (place.detail.isNotBlank()) {
                Text(
                    text = place.detail.uppercase(),
                    style = MeteoType.caption,
                    color = colors.label,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (!saved) {
            GlyphButton(glyph = Glyph.PLUS, tint = colors.label, size = 34.dp, onClick = onSave)
        }
    }
}

// ---------------------------------------------------------------------------
// Unita'
// ---------------------------------------------------------------------------

/**
 * L'interruttore dei gradi: una pillola sola, incassata, con il pomello bianco.
 *
 * ## Perche' e' tutto dentro una tela
 *
 * Il modo naturale sarebbe due `Text` sovrapposti a un pomello che scivola. Il
 * problema e' il colore delle due scritte: sotto il pomello bianco devono
 * essere scure, fuori chiare, e quel passaggio segue il pomello. Legandolo allo
 * **stato** invece che alla posizione, la scritta cambia colore nell'istante
 * del tocco mentre il pomello e' ancora a meta' strada; legandolo alla
 * posizione, si legge un valore animato in composizione e si ricompone a ogni
 * fotogramma per tutta la durata dello scorrimento.
 *
 * Disegnando tutto qui, la posizione si legge **dentro il disegno**: scorrere
 * ridipinge e non ricompone, ed e' la stessa regola che governa la cifra in
 * tre dimensioni.
 *
 * ## L'incasso
 *
 * Non e' un'ombra sfocata, che a questa dimensione sarebbe una macchia. Sono
 * due filetti da un pixel: uno scuro appoggiato al bordo superiore e uno chiaro
 * a quello inferiore. E' il modo in cui si legge una scanalatura fresata - la
 * luce viene dall'alto, quindi il labbro alto e' in ombra e quello basso la
 * prende - e costa due righe invece di un livello di sfocatura.
 */
@Composable
private fun UnitSwitch(
    current: TempUnit,
    surfaces: Surfaces,
    onChoose: (TempUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val density = LocalDensity.current
    val entries = remember { TempUnit.entries.toList() }

    val position by animateFloatAsState(
        targetValue = entries.indexOf(current).toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "unita",
    )

    val paint = remember(density) { unitPaint(density) }
    val interaction = remember { MutableInteractionSource() }

    Canvas(
        modifier = modifier
            .size(width = SWITCH_WIDTH, height = SWITCH_HEIGHT)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null) {
                // Un interruttore, non due pulsanti: si tocca e si ribalta.
                // Con due meta' sensibili, toccare quella gia' scelta non fa
                // niente, e un comando che a volte non risponde si legge come
                // rotto.
                onChoose(entries[(entries.indexOf(current) + 1) % entries.size])
            },
    ) {
        val radius = size.height / 2f
        val inset = size.height * 0.09f

        // La scanalatura.
        drawRoundRect(
            color = surfaces.recessed,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        )
        drawArcLip(top = true, colour = surfaces.lipShadow, radius = radius)
        drawArcLip(top = false, colour = surfaces.lipLight, radius = radius)

        // Il pomello, dove lo dice la molla.
        val knobWidth = (size.width - inset * 2f) / entries.size
        val knobLeft = inset + position * knobWidth
        drawRoundRect(
            color = colors.pillBackground,
            topLeft = Offset(knobLeft, inset),
            size = androidx.compose.ui.geometry.Size(knobWidth, size.height - inset * 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        )

        // Le scritte, l'ultima cosa: quella sotto il pomello e' scura, l'altra
        // chiara, e il passaggio segue il pomello perche' entrambe le tinte si
        // ricavano dalla stessa posizione.
        drawIntoCanvas { canvas ->
            val baseline = size.height / 2f - (paint.descent() + paint.ascent()) / 2f
            for (i in entries.indices) {
                val centre = inset + knobWidth * (i + 0.5f)
                // Quanto questa meta' e' coperta dal pomello, da 0 a 1.
                val covered = (1f - kotlin.math.abs(position - i)).coerceIn(0f, 1f)
                paint.color = lerp(colors.label, colors.pillText, covered).toArgb()
                canvas.nativeCanvas.drawText(entries[i].symbol, centre, baseline, paint)
            }
        }
    }
}

/**
 * Un filetto lungo il bordo interno della scanalatura.
 *
 * Disegnato come arco e non come riga dritta: la pillola e' tonda alle
 * estremita', e una riga dritta che si ferma prima delle curve lascia due
 * monconi visibili.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArcLip(
    top: Boolean,
    colour: Color,
    radius: Float,
) {
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color = colour,
        topLeft = Offset(0f, if (top) 0f else stroke),
        size = androidx.compose.ui.geometry.Size(size.width, size.height - stroke),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
    )
}

/**
 * Il pennello delle due scritte, costruito una volta sola.
 *
 * Nessun misuratore di testo di Compose: la sua cache ignora il colore, e qui
 * il colore cambia a ogni fotogramma mentre il pomello scorre (trappola numero
 * 3 in CONTESTO.md). Con il pennello della piattaforma il colore e' un campo, e
 * cambiarlo non invalida niente.
 */
private fun unitPaint(density: Density): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = with(density) { 13.sp.toPx() }
        letterSpacing = 0.10f
    }

private val SWITCH_WIDTH = 116.dp
private val SWITCH_HEIGHT = 40.dp

// ---------------------------------------------------------------------------
// Dati
// ---------------------------------------------------------------------------

/**
 * Le informazioni tecniche, chiuse finche' non le si chiede.
 *
 * Non spariscono e non devono: una previsione senza fonte e' un'opinione, e chi
 * guarda ha il diritto di sapere chi l'ha fatta, quando, e per quale punto
 * esatto della mappa. Ma sono dodici righe di indirizzi e nomi di variabili, e
 * tenerle aperte per sempre significa che l'ultima cosa che si vede scorrendo
 * le impostazioni e' un elenco di parametri di una richiesta HTTP.
 *
 * Chiuso resta quello che serve a chiunque: da chi arrivano i numeri e da
 * quanto sono li'.
 */
@Composable
private fun DataAccordion(fetchedAt: LocalDateTime?, surfaces: Surfaces) {
    val colors = LocalMeteoColors.current
    // Lo stato vive qui dentro, e non un livello piu' su: aprirlo ricompone
    // questo blocco e nient'altro.
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    val turn by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "accordion",
    )

    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(surfaces.raised)
            .clickable(interactionSource = interaction, indication = null) { open = !open }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "OPEN-METEO", style = MeteoType.value, color = colors.text)
                Text(
                    text = fetchedAt?.format(CLOCK)?.let { "AGGIORNATO ALLE $it" }
                        ?: "MAI AGGIORNATO",
                    style = MeteoType.caption,
                    color = colors.label,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            // La rotazione si legge dentro `graphicsLayer`, che e' una lambda
            // di disegno: girare ridipinge e non ricompone.
            Canvas(
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = turn },
            ) {
                val stroke = size.minDimension * 0.12f
                val mid = size.width / 2f
                drawLine(
                    colors.label,
                    Offset(0f, size.height * 0.32f),
                    Offset(mid, size.height * 0.68f),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    colors.label,
                    Offset(size.width, size.height * 0.32f),
                    Offset(mid, size.height * 0.68f),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }

        if (open) {
            Spacer(Modifier.height(14.dp))
            SourceRow("MODELLO", "MISCELA AUTOMATICA DEI MODELLI NAZIONALI")
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
            SourceRow("ALBA E TRAMONTO", "CALCOLATI DA OPEN-METEO PER QUESTE COORDINATE")
            SourceRow("FASE LUNARE", "CALCOLATA NELL'APP: L'API NON LA FORNISCE")
            SourceRow("CHIAVE D'ACCESSO", "NESSUNA: L'USO NON COMMERCIALE È LIBERO")
            SourceRow("LICENZA DEI DATI", "CC BY 4.0", last = true)
        }
    }
}

@Composable
private fun SourceRow(label: String, value: String, last: Boolean = false) {
    val colors = LocalMeteoColors.current
    Column(modifier = Modifier.padding(bottom = if (last) 0.dp else 12.dp)) {
        Text(text = label, style = MeteoType.caption, color = colors.label)
        Text(
            text = value,
            style = MeteoType.value,
            color = colors.text,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Superfici
// ---------------------------------------------------------------------------

/**
 * I toni delle superfici, ricavati dal fondo dell'ora.
 *
 * Tutti **opachi**. La strada breve sarebbe stata il bianco a bassa opacita'
 * sopra il fondo, e da lontano e' lo stesso colore; ma un colore trasparente si
 * ricompone col fondo a ogni disegno, e soprattutto due pastiglie che si
 * sovrappongono scorrendo il carosello si sommerebbero, mostrando un gradino
 * dove non c'e' niente. Mescolati una volta sola e stesi pieni, non succede.
 */
@Immutable
private class Surfaces(
    /** Sopra il fondo: pastiglie e blocchi. */
    val raised: Color,
    /** Sotto il fondo: la scanalatura dell'interruttore. */
    val recessed: Color,
    /** Il labbro in ombra della scanalatura, in alto. */
    val lipShadow: Color,
    /** Il labbro in luce, in basso. */
    val lipLight: Color,
)

@Composable
private fun rememberSurfaces(colors: MeteoColors): Surfaces = remember(colors) {
    Surfaces(
        raised = lerp(colors.background, colors.text, 0.09f),
        recessed = lerp(colors.background, Color.Black, 0.42f),
        lipShadow = lerp(colors.background, Color.Black, 0.66f),
        lipLight = lerp(colors.background, colors.text, 0.16f),
    )
}

// ---------------------------------------------------------------------------
// Pezzi comuni
// ---------------------------------------------------------------------------

@Composable
private fun ModuleTitle(text: String, top: Dp = 6.dp) {
    val colors = LocalMeteoColors.current
    Text(
        text = text,
        style = MeteoType.caption,
        color = colors.label,
        modifier = Modifier.padding(start = 24.dp, top = top, bottom = 10.dp),
    )
}

/** Una pastiglia d'azione: piena quando e' quella da premere, vuota quando no. */
@Composable
private fun Pill(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    surfaces: Surfaces,
    onClick: () -> Unit,
) {
    val colors = LocalMeteoColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) colors.pillBackground else surfaces.raised)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MeteoType.caption,
            color = if (filled) colors.pillText else colors.label,
        )
    }
}

/**
 * La stella dei preferiti: piena se la localita' e' salvata, di solo contorno
 * se no.
 *
 * I dieci vertici si calcolano una volta e restano: sono sempre gli stessi, e
 * ricavarli a ogni disegno vorrebbe dire venti seni e coseni per una stella
 * larga quattordici pixel.
 */
@Composable
private fun StarToggle(saved: Boolean, onClick: () -> Unit) {
    val colors = LocalMeteoColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(15.dp)) {
            val path = STAR_PATH
            path.reset()
            val r = size.minDimension / 2f
            for (i in STAR_POINTS.indices step 2) {
                val x = size.width / 2f + STAR_POINTS[i] * r
                val y = size.height / 2f + STAR_POINTS[i + 1] * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            if (saved) {
                drawPath(path, color = colors.text)
            } else {
                drawPath(
                    path,
                    color = colors.label,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = size.minDimension * 0.13f,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

private val STAR_PATH = androidx.compose.ui.graphics.Path()

/** Dieci vertici alternati fra raggio pieno e raggio interno, sul cerchio unitario. */
private val STAR_POINTS: FloatArray = FloatArray(20).also { points ->
    for (i in 0 until 10) {
        val angle = (-90.0 + i * 36.0) * PI / 180.0
        val radius = if (i % 2 == 0) 1.0 else 0.42
        points[i * 2] = (cos(angle) * radius).toFloat()
        points[i * 2 + 1] = (sin(angle) * radius).toFloat()
    }
}

private enum class Glyph { PLUS, CROSS }

/** Croce e piu' disegnati, per non tirarsi dietro una libreria di icone. */
@Composable
private fun GlyphButton(glyph: Glyph, tint: Color, size: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.32f)) {
            val stroke = this.size.minDimension * 0.16f
            val half = this.size.width / 2f
            when (glyph) {
                Glyph.PLUS -> {
                    drawLine(tint, Offset(half, 0f), Offset(half, this.size.height), stroke, StrokeCap.Round)
                    drawLine(tint, Offset(0f, half), Offset(this.size.width, half), stroke, StrokeCap.Round)
                }
                Glyph.CROSS -> {
                    drawLine(tint, Offset.Zero, Offset(this.size.width, this.size.height), stroke, StrokeCap.Round)
                    drawLine(tint, Offset(this.size.width, 0f), Offset(0f, this.size.height), stroke, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    surfaces: Surfaces,
    placeholder: String,
) {
    val colors = LocalMeteoColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 14.dp)
            .clip(CircleShape)
            .background(surfaces.recessed)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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
            drawLine(colors.label, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(colors.label, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
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
