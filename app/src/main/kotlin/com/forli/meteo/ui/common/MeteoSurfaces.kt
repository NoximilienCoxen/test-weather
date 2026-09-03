package com.forli.meteo.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor

/**
 * Il vocabolario di componenti condiviso da tutte le schermate.
 *
 * Prima ognuna aveva il suo: `DetailChrome` dichiarava `DetailCard`,
 * `SettingsScreen` un `Block`, `WidgetConfigScreen` un terzo contenitore, e
 * ciascuno portava con se' la propria copia di `Primary`, `Secondary`,
 * `FieldBackground`. Tre palette parallele destinate a divergere - e infatti
 * divergevano.
 *
 * Qui i colori vengono tutti da `MaterialTheme.colorScheme`, che a sua volta li
 * calcola per contrasto (vedi `ui/theme/Contrast.kt`): nessun componente sceglie
 * a mano il colore di un testo.
 *
 * Tutto cio' che si tocca ha ripple e una semantica dichiarata. Il progetto
 * passava `indication = null` a ogni singolo `clickable`, quindi in tutta l'app
 * non c'era **un solo** riscontro di pressione, e i lettori di schermo
 * incontravano riquadri senza nome.
 */

/** Il raggio delle pillole: pieno, cioe' la meta' dell'altezza. */
private val PillShape = CircleShape

// ---------------------------------------------------------------------------
// Barra in cima
// ---------------------------------------------------------------------------

/**
 * La barra di una schermata: freccia, titolo, ed eventualmente una riga sotto
 * che dice **di cosa** siano i numeri.
 *
 * Il sottotitolo non e' un vezzo: aperto il foglio del dettaglio non c'era piu'
 * modo di sapere quale localita' si stesse guardando, ne' quale giorno, e i
 * numeri di una citta' sono indistinguibili da quelli di un'altra.
 *
 * [transition] e' lo scostamento frazionario del carosello sottostante, da
 * -0,5 a 0,5, e vale zero per le schermate che non ne hanno uno.
 *
 * E' una **lambda** e non un numero apposta: letta dentro `graphicsLayer`, la
 * variazione si ferma alla fase di disegno invece di ricomporre la barra a ogni
 * fotogramma del trascinamento. Passandola come valore, ogni frame del gesto
 * avrebbe ricomposto titolo e sottotitolo - due `Text` con misura del testo -
 * per cambiare una trasparenza.
 *
 * Serve a togliere di mezzo un difetto che si vedeva a ogni singola passata di
 * pagina: il titolo **si sostituiva di colpo** a meta' trascinamento, cioe'
 * annunciava "VENTO" mentre sotto si vedeva ancora la pagina del sole. Qui il
 * titolo sfuma via mentre la pagina se ne va e rientra con quella nuova; il
 * testo si sostituisce nell'istante in cui e' del tutto trasparente, che e'
 * esattamente il punto di scavalco. Non c'e' fotogramma in cui si legga un
 * titolo che non corrisponde a cio' che sta sotto.
 */
@Composable
fun MeteoTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backLabel: String = "Torna indietro",
    transition: () -> Float = { 0f },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `onSurface` esplicito e non `LocalContentColor`: la barra vive dentro
        // un Box con lo sfondo dipinto a mano, non dentro una `Surface`, e li'
        // il colore di contenuto e' ancora quello di riposo di Material - nero.
        // Sarebbe stata una freccia nera su un pannello antracite, cioe'
        // esattamente il difetto che questa passata deve togliere di mezzo.
        MeteoIconButton(onClick = onBack, contentDescription = backLabel) {
            BackArrowIcon(MaterialTheme.colorScheme.onSurface)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    // A gesto fermo vale zero e questo blocco non fa niente:
                    // le altre schermate non pagano nulla.
                    val shift = transition()
                    alpha = 1f - (abs(shift).coerceAtMost(0.5f) * 2f)
                    translationX = -shift * size.width * 0.5f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Occupa quanto la freccia a sinistra, cosi' il titolo resta al centro
        // dello schermo e non al centro di quello che avanza.
        Spacer(Modifier.width(MinTouchTarget))
    }
}

/**
 * Un pulsante tondo con dentro un disegno.
 *
 * Da 48dp e non da 34 o 40 come i tre pulsanti che sostituisce: sotto quella
 * misura un dito manca il bersaglio, ed e' il minimo che Material dichiara.
 * Il disegno resta piccolo - e' l'area sensibile a crescere, non l'icona.
 */
@Composable
fun MeteoIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    // Il nome in una variabile a parte: dentro `semantics` il ricevente ha una
    // proprieta' che si chiama gia' `contentDescription`, e averla su tutti e
    // due i lati dell'assegnamento e' un'ambiguita' che si legge male anche
    // quando le regole di risoluzione la sciolgono nel verso giusto.
    val label = contentDescription
    Box(
        modifier = modifier
            .size(MinTouchTarget)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics { this.contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/**
 * La freccia e' disegnata, non importata.
 *
 * Il progetto non ha `material-icons-extended` e non vale mezzo megabyte di
 * dipendenza per tre segmenti.
 */
@Composable
fun BackArrowIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val y = size.height / 2f
        val tipX = size.width * 0.08f
        val wing = size.height * 0.32f
        val stroke = size.height * 0.11f
        drawLine(color, Offset(size.width, y), Offset(tipX, y), stroke, StrokeCap.Round)
        drawLine(color, Offset(tipX, y), Offset(tipX + wing, y - wing), stroke, StrokeCap.Round)
        drawLine(color, Offset(tipX, y), Offset(tipX + wing, y + wing), stroke, StrokeCap.Round)
    }
}

/** La croce delle schermate che si chiudono invece di risalire. */
@Composable
fun CloseIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(15.dp)) {
        val stroke = size.minDimension * 0.11f
        drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------
// Superfici
// ---------------------------------------------------------------------------

/**
 * La scheda: una `Surface` Material con l'elevazione tonale, non un riquadro
 * col bordo disegnato a mano.
 *
 * Il bordo sottile che c'era prima serviva a staccare la scheda da un fondo
 * quasi identico. Con i livelli di superficie del tema il gradino c'e' gia', e
 * una cornice in piu' e' rumore.
 */
@Composable
fun MeteoCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = color,
        shape = MaterialTheme.shapes.large,
        content = { Column(content = content) },
    )
}

/** L'intestazione di un gruppo: una parola e un filo. */
@Composable
fun MeteoSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        Spacer(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/** Il filo che separa due cose dentro lo stesso blocco. */
@Composable
fun MeteoDivider(modifier: Modifier = Modifier, inset: androidx.compose.ui.unit.Dp = 0.dp) {
    Spacer(
        modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

// ---------------------------------------------------------------------------
// Pillole
// ---------------------------------------------------------------------------

/**
 * Una pillola selezionabile.
 *
 * Sostituisce quattro implementazioni quasi identiche - i chip delle modalita',
 * le due meta' di EFFETTIVA/PERCEPITI, le scorciatoie di localita', la scelta
 * dell'unita' - che differivano solo per il colore scritto a mano.
 *
 * Il testo dentro non lo sceglie chi chiama: su pillola attiva viene da
 * `inverseOnSurface`, su pillola spenta da `onSurfaceVariant`. Non c'e' modo di
 * ottenere per svista un grigio su grigio, che e' il difetto che aveva la
 * versione precedente (etichetta #8A8A92 su fondo #2A2A2F, sotto la soglia).
 */
@Composable
fun MeteoPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Tab,
    leading: @Composable (() -> Unit)? = null,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.inverseSurface
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.inverseOnSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .sizeIn(minHeight = MinTouchTarget)
            .clip(PillShape)
            .background(background)
            .clickable(role = role, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leading?.invoke()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 1,
            )
        }
    }
}

/**
 * Una fila di pillole che si porta da sola la selezione **al centro**.
 *
 * Scorrevole e non a larghezze uguali: i nomi sono lunghi in modo diverso, e
 * comprimerli tutti alla misura del piu' largo li spezzerebbe a meta'.
 *
 * Due difetti che questa versione toglie di mezzo, e non erano cosmetici.
 *
 * **La fila non seguiva niente.** Era un `Row` con `horizontalScroll` e nessuna
 * logica di scorrimento: cambiando pagina la pillola attiva restava dov'era,
 * cioe' spesso **fuori dallo schermo**. Con cinque grandezze su un telefono le
 * ultime non entrano, e chi scorreva fino ad "Aria" vedeva una fila di pillole
 * tutte spente.
 *
 * **La fila non era centrata.** Con `horizontalScroll` il contenuto si appoggia
 * a sinistra anche quando ci starebbe comodo, e senza margine interno la prima
 * e l'ultima pillola toccavano il bordo. Qui `spacedBy(..., CenterHorizontally)`
 * centra quando c'e' spazio e si comporta da fila che scorre quando non ce n'e',
 * e [contentPadding] tiene le estreme staccate dal bordo.
 *
 * [position] e' la posizione **frazionaria** del carosello sottostante, quando
 * ce n'e' uno. Serve a far scivolare la fila **col dito** invece che a scatti a
 * gesto finito: e' il movimento che rende leggibile dove si sta andando mentre
 * ci si va. Senza, si passa il solo indice e la fila ci arriva con
 * un'animazione.
 */
@Composable
fun <T> MeteoPillRow(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
    position: (() -> Float)? = null,
) {
    val listState = rememberLazyListState()
    val selectedIndex = items.indexOf(selected)

    // Col carosello: la fila insegue la posizione frazionaria, senza animazione
    // propria - l'animazione e' il dito. Senza carosello: ci arriva da sola.
    if (position != null) {
        // `snapshotFlow` riemette quando cambia lo **stato di Compose letto
        // dentro il blocco**. Con un `Float` gia' calcolato dal chiamante non
        // c'e' niente da osservare: il flusso emetterebbe una volta sola e la
        // fila resterebbe ferma per sempre. La lambda legge il pager dentro il
        // blocco, che e' l'unico modo perche' il flusso se ne accorga.
        LaunchedEffect(listState, items.size) {
            snapshotFlow { position() }.collect { listState.centerOn(it) }
        }
    } else {
        LaunchedEffect(selectedIndex) {
            if (selectedIndex >= 0) runCatching { listState.centerOn(selectedIndex.toFloat(), animate = true) }
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(items) { _, item ->
            MeteoPill(
                label = label(item),
                selected = item == selected,
                onClick = { onSelect(item) },
            )
        }
    }
}

/**
 * Porta al centro del viewport il punto [position] della lista, dove la parte
 * intera e' un indice e la frazione sta fra quell'elemento e il successivo.
 *
 * `animateScrollToItem` non basta e non e' pignoleria: quella porta l'elemento
 * al **bordo** d'ingresso, non al centro, e su una fila di cinque pillole
 * significa che l'attiva finisce incollata a sinistra invece che davanti agli
 * occhi. Il residuo si calcola da `layoutInfo`, che e' l'unico posto dove le
 * misure vere degli elementi esistono.
 *
 * Se il punto non e' in scena si fa prima un salto per portarcelo, se no non
 * c'e' niente da misurare.
 */
internal suspend fun LazyListState.centerOn(position: Float, animate: Boolean = false) {
    val low = floor(position).toInt()
    val high = low + 1
    val fraction = position - low

    fun centerOf(index: Int): Float? = layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == index }
        ?.let { it.offset + it.size / 2f }

    if (centerOf(low) == null && centerOf(high) == null) {
        val landing = low.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        if (animate) animateScrollToItem(landing) else scrollToItem(landing)
    }

    val a = centerOf(low)
    val b = centerOf(high)
    val target = when {
        a != null && b != null -> a + (b - a) * fraction
        a != null -> a
        b != null -> b
        else -> return
    }
    val info = layoutInfo
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val delta = target - viewportCenter
    if (animate) animateScrollBy(delta) else scrollBy(delta)
}

/**
 * Due o piu' pillole affiancate a larghezza uguale.
 *
 * A larghezza uguale perche' sono alternative dello stesso valore e devono
 * pesare uguale: una piu' larga dell'altra suggerirebbe che sia quella giusta.
 */
@Composable
fun MeteoSplitPills(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            MeteoPill(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                role = Role.RadioButton,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Righe di valori
// ---------------------------------------------------------------------------

/**
 * Una riga etichetta/valore.
 *
 * `clearAndSetSemantics` con la frase intera: un lettore di schermo che legge
 * "UMIDITA'" e poi, dopo una pausa, "58%" costringe chi ascolta a ricucire i due
 * pezzi. Letti insieme sono una frase.
 */
@Composable
fun MeteoMetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    accent: Color? = null,
) {
    val labelColor = if (emphasis) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val valueColor = accent ?: MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = "$label: $value" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            maxLines = 1,
        )
    }
}

/** Una scheda di righe etichetta/valore, con i fili in mezzo. */
@Composable
fun MeteoMetricCard(
    rows: List<MeteoMetric>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    MeteoCard(modifier = modifier) {
        Spacer(Modifier.height(4.dp))
        rows.forEachIndexed { index, row ->
            MeteoMetricRow(
                label = row.label,
                value = row.value,
                emphasis = row.emphasis,
                accent = row.accent,
            )
            if (index < rows.lastIndex) MeteoDivider(inset = 18.dp)
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Una riga di tabella. [accent] tinge il solo valore, mai l'etichetta. */
data class MeteoMetric(
    val label: String,
    val value: String,
    val emphasis: Boolean = false,
    val accent: Color? = null,
)

/**
 * Un valore grande con la sua didascalia sopra e una nota sotto.
 *
 * L'etichetta sta **sopra** il numero: si legge dall'alto, e sapere cosa si sta
 * per leggere prima di leggerlo e' meta' della comprensione.
 */
@Composable
fun MeteoStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    accent: Color? = null,
) {
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = listOfNotNull(label, value, caption).joinToString(": ")
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Una griglia di statistiche, a colonne decise dalla larghezza dello schermo.
 *
 * Righe costruite a mano e non `LazyVerticalGrid`: la griglia pigra non puo'
 * stare dentro una colonna che scorre gia' - si contendono l'altezza infinita -
 * e qui gli elementi sono sei, non seicento.
 */
@Composable
fun MeteoStatGrid(
    stats: List<MeteoStatData>,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    if (stats.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        stats.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { stat ->
                    MeteoStat(
                        label = stat.label,
                        value = stat.value,
                        caption = stat.caption,
                        accent = stat.accent,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Le celle mancanti dell'ultima riga occupano spazio senza
                // disegnare: senza, tre statistiche su quattro colonne si
                // allargherebbero a riempire, e le righe non sarebbero piu'
                // incolonnate fra loro.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

data class MeteoStatData(
    val label: String,
    val value: String,
    val caption: String? = null,
    val accent: Color? = null,
)

// ---------------------------------------------------------------------------
// Quando non c'e' niente da mostrare
// ---------------------------------------------------------------------------

/**
 * Il posto dove finisce cio' che non c'e'.
 *
 * Dice **perche'** manca, non solo che manca: "in attesa dei dati" e "questo
 * modello non fornisce il vento oltre le settantadue ore" sono due situazioni
 * diverse, e finora entrambe uscivano come una colonna di trattini o, peggio,
 * come un grafico vuoto senza una parola.
 */
@Composable
fun MeteoEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
