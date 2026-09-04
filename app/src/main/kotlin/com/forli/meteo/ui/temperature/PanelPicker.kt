package com.forli.meteo.ui.temperature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.common.CloseIcon
import com.forli.meteo.ui.common.MeteoIconButton
import com.forli.meteo.ui.common.MeteoSectionHeader
import com.forli.meteo.ui.common.MinTouchTarget

/**
 * L'elenco di tutti i pannelli, aperto da un pulsante.
 *
 * **Le pillole restano.** Questo non le sostituisce: la fila in cima serve a
 * spostarsi di una posizione e a dire dove si e', e per quello e' piu' veloce di
 * qualunque elenco. Quello che la fila non sa fare e' mostrare tutto insieme -
 * scorre, quindi le ultime grandezze restano fuori dallo schermo - ne' portare
 * dalla prima all'ultima in un tocco solo. Con sei pagine le due cose cominciano
 * a mancare.
 *
 * Ogni voce porta il **titolo lungo**, non l'abbreviazione della pillola: qui lo
 * spazio c'e', ed e' l'unico posto dell'app dove "QUALITA' DELL'ARIA" si legge
 * per esteso invece di stare stretto in "Aria".
 *
 * **Niente icone disegnate, un pallino colorato.** Il progetto non ha
 * `material-icons-extended` per scelta dichiarata, e sei glifi nuovi sarebbero
 * un lavoro a se'; il pallino invece parla la lingua che la schermata usa gia',
 * perche' e' lo stesso colore che i pallini sotto le pillole danno alla pagina.
 */
@Composable
internal fun PanelPicker(
    modes: List<DetailMode>,
    selected: DetailMode,
    onPick: (DetailMode) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Coprire con un colore non basta a coprire: un fondo dipinto non e'
    // bersaglio di eventi, e sotto l'elenco resta il carosello. Senza questi due
    // ostacoli il dito che scorre sull'elenco cambiava pagina la' sotto, e
    // quello che tirava in giu' chiudeva il foglio dietro un pannello che
    // sembrava chiuderne un altro.
    //
    // L'avanzo di scorrimento si ferma qui: questa connessione sta **dentro**
    // quella del foglio, quindi vede il residuo per prima e se lo tiene.
    val swallowScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = available

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = available
        }
    }

    // Opaco, non un velo. Un pannello semitrasparente sopra un grafico non ha
    // un contrasto: ne ha uno diverso a ogni pixel, ed e' un difetto che questa
    // app ha gia' pagato una volta sulle impostazioni.
    Surface(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(swallowScroll),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Il tappo dei tocchi sta **sotto** i comandi e non sopra: messo sopra
            // dovrebbe lasciar passare cio' che serve, e un riconoscitore che
            // decide caso per caso cosa ingoiare e' esattamente il modo in cui si
            // perdono i tocchi. Qui i comandi vengono dopo, quindi ricevono per
            // primi, e quaggiu' arriva solo cio' che nessuno voleva.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { } },
            )

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "PANNELLI",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    MeteoIconButton(
                        onClick = onClose,
                        contentDescription = "Chiudi l'elenco dei pannelli",
                        icon = { CloseIcon(MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }

                MeteoSectionHeader(
                    text = "COSA VUOI GUARDARE",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    modes.forEach { mode ->
                        PanelRow(
                            mode = mode,
                            accent = mode.accent(),
                            selected = mode == selected,
                            onClick = { onPick(mode) },
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** Una voce dell'elenco: il pallino della grandezza, il nome lungo, e la spunta. */
@Composable
private fun PanelRow(
    mode: DetailMode,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isOn = selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            // Chi ascolta la schermata deve sapere quale voce e' quella accesa,
            // e la spunta disegnata non glielo dice. Il valore passa da una
            // variabile a parte: dentro `semantics` il ricevente ha gia' una
            // proprieta' che si chiama `selected`, e averla su tutti e due i
            // lati dell'assegnamento si legge male anche quando le regole di
            // risoluzione la sciolgono nel verso giusto.
            .semantics { this.selected = isOn }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Text(
            text = mode.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        if (selected) CheckIcon(MaterialTheme.colorScheme.onSurface)
    }
}

/** La spunta della voce accesa, disegnata come tutte le altre icone del progetto. */
@Composable
private fun CheckIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(16.dp)) {
        val stroke = size.minDimension * 0.13f
        drawLine(
            color,
            Offset(size.width * 0.10f, size.height * 0.55f),
            Offset(size.width * 0.40f, size.height * 0.84f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(size.width * 0.40f, size.height * 0.84f),
            Offset(size.width * 0.92f, size.height * 0.18f),
            stroke,
            StrokeCap.Round,
        )
    }
}

/**
 * Il pulsante che apre l'elenco: tre righe con un pallino ciascuna.
 *
 * Non e' un "hamburger": quello vuol dire menu di navigazione, e questo non
 * porta altrove - mostra le pagine che si hanno gia' sotto le dita. I pallini
 * davanti alle righe sono gli stessi pallini colorati dell'elenco, cosi' il
 * disegno del pulsante dice cosa si trovera' aprendolo.
 */
@Composable
internal fun PanelListIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val rows = 3
        val dot = size.width * 0.14f
        val gap = (size.height - dot * rows) / (rows - 1).coerceAtLeast(1)
        repeat(rows) { i ->
            val y = i * (dot + gap)
            drawCircle(color, dot / 2f, Offset(dot / 2f, y + dot / 2f))
            drawRect(
                color = color,
                topLeft = Offset(dot * 1.7f, y + dot * 0.30f),
                size = Size(size.width - dot * 1.7f, dot * 0.42f),
            )
        }
    }
}
