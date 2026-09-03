package com.forli.meteo.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
 */
@Composable
fun MeteoTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backLabel: String = "Torna indietro",
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
                .padding(horizontal = 4.dp),
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
    Box(
        modifier = modifier
            .size(MinTouchTarget)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
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
 * Una fila di pillole che scorre se non ci sta.
 *
 * Scorrevole e non a larghezze uguali: i nomi sono lunghi in modo diverso, e
 * comprimerli tutti alla misura del piu' largo li spezzerebbe a meta'. Lo
 * scorrimento non si vede finche' ci stanno.
 */
@Composable
fun <T> MeteoPillRow(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier.horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            MeteoPill(
                label = label(item),
                selected = item == selected,
                onClick = { onSelect(item) },
            )
        }
    }
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
