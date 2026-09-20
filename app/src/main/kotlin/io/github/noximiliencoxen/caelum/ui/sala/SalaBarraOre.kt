package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.SunClock
import io.github.noximiliencoxen.caelum.data.Wmo
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * La barra delle ventiquattro ore: **un comando, non un grafico**.
 *
 * ### Cos'e'
 *
 * Un binario pieno **colorato ora per ora col cielo di quell'ora** - la stessa
 * tabella che dipinge il fondo, letta ventiquattro volte - cosi' la forma della
 * giornata si legge senza toccare niente: si vede dove comincia l'alba, dove il
 * pomeriggio si chiude, dove torna la notte. Sopra ci scorre una **maniglia**
 * che dice da se' che si prende.
 *
 * Si tocca **e** si trascina. Riconoscere il solo trascinamento era il difetto
 * peggiore della versione precedente: chi toccava non otteneva risposta, e da un
 * comando che non risponde si impara che non e' un comando.
 *
 * ### Il ritorno all'ora attuale
 *
 * Compare **solo quando si e' lontani dal presente**, al posto della riga che
 * spiega il gesto: finche' si guarda adesso, un tasto che riporta ad adesso e'
 * un comando che non fa niente, e un comando che non fa niente insegna a non
 * fidarsi nemmeno degli altri.
 *
 * @param onTick un colpetto quando si scavalca un'ora. Il dito lo sente scattare
 *   sugli scalini invece di scivolare su un continuo, che e' anche cio' che le
 *   ore sono.
 */
@Composable
fun BarraDelleOre(
    hours: List<HourForecast>,
    selected: Int,
    oraAttuale: Int,
    /**
     * Che giorno si sta guardando: "oggi", "giovedì 18 set".
     *
     * Sta qui e non in una sala perche' **lo devono vedere tutte e sette**.
     * Finora il giorno scelto si leggeva solo in Sala I e in Sala II: da "La
     * pioggia" o da "Il vento" non c'era modo di sapere se quei numeri erano
     * di oggi o di giovedi', e l'unico modo di scoprirlo era tornare indietro
     * di due schermate. Un dato senza la sua data e' un dato che chi guarda
     * deve indovinare.
     */
    giorno: String,
    palette: SalaPalette,
    alba: LocalDateTime?,
    tramonto: LocalDateTime?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onTick: () -> Unit = {},
    onTornaOra: () -> Unit = {},
) {
    // **Ogni valore letto dentro un riconoscitore passa di qui.** La lambda di
    // `pointerInput` viene ricreata solo quando cambia la sua chiave, quindi
    // senza questo confronterebbe per sempre i valori che c'erano all'apertura.
    // E' la trappola #7, e il progetto l'ha gia' pagata **su questa stessa
    // barra**: l'ora corrente era l'unica irraggiungibile della giornata,
    // perche' il confronto la dichiarava gia' scelta.
    val sceltaOra by rememberUpdatedState(selected)
    val scegli by rememberUpdatedState(onSelect)
    val colpetto by rememberUpdatedState(onTick)

    val tinte = remember(hours, alba, tramonto) { coloriDelleOre(hours, alba, tramonto) }
    // **Senza ore non c'e' niente da scegliere.** Oltre le ~72 ore la previsione
    // da' i totali del giorno e non le sue ore: un binario che risponde al dito
    // ma non cambia niente insegna che i comandi di questa app non contano.
    val attiva = hours.isNotEmpty()
    val ora = selected.coerceIn(0, ORE - 1)
    val spostata = ora != oraAttuale.coerceIn(0, ORE - 1)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (spostata) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(palette.accent)
                        .clickable(onClick = onTornaOra)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Canvas(modifier = Modifier.size(11.dp)) {
                        drawCircle(
                            color = palette.accentInk,
                            radius = size.width / 2f - 1.dp.toPx(),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                    Text(text = "TORNA ALL'ORA ATTUALE", style = SalaType.sectionLabel, color = palette.accentInk)
                }
            } else {
                Text(
                    text = "TRASCINA PER CAMBIARE ORA",
                    style = SalaType.sectionLabel,
                    // Questa riga **non sta su un pannello**: sta sulle
                    // colline, cioe' su un fondo che cambia con l'ora e che a
                    // volte e' quasi del suo stesso colore. Al sole diretto
                    // spariva. `inkSuCielo` e' lo stesso inchiostro spinto
                    // quanto basta per staccare da li'.
                    color = palette.inkSuCielo,
                )
            }
            // **Giorno e ora sulla stessa riga, e la prima stesura li aveva
            // incolonnati.** Due righe qui costano venticinque punti di
            // altezza a tutta la galleria, perche' questa barra sta sotto ogni
            // sala - e "La settimana", che e' la piu' alta, li ha pagati
            // facendosi tagliare l'ultima riga della striscia. Lo si e' visto
            // in uno scatto della CI, non a mente.
            //
            // Affiancati non costano niente: la riga era gia' alta quanto
            // l'ora, e a sinistra c'e' spazio perche' la pillola del ritorno
            // al presente finisce ben prima.
            // **Le due scritte stanno sulla stessa linea di base, e prima
            // no.** Erano allineate in basso con due punti di margine messi a
            // occhio - e allineare in basso due **riquadri** di corpi diversi
            // non allinea le lettere: allinea i fondi delle caselle, che sotto
            // le lettere scendono di quanto vuole ciascun font. Il giorno
            // galleggiava un paio di punti sopra l'ora, e si vedeva.
            //
            // `alignByBaseline` allinea quello che l'occhio guarda davvero: la
            // riga su cui poggiano le lettere. Niente margini da tarare, e
            // continua a valere se un domani i due corpi cambiano.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = giorno.uppercase(),
                    style = SalaType.microLabel,
                    color = palette.inkSuCielo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = oraPiena(ora),
                    style = SalaType.hourLabel,
                    color = palette.accentSuCielo,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ALTEZZA)
                // Il tocco secco prima del trascinamento: chi tocca vuole
                // andare li', non cominciare un gesto.
                .pointerInput(attiva) {
                    if (!attiva) return@pointerInput
                    detectTapGestures { punto ->
                        val i = indiceDa(punto.x, size.width.toFloat())
                        if (i != sceltaOra) { colpetto(); scegli(i) }
                    }
                }
                .pointerInput(attiva) {
                    if (!attiva) return@pointerInput
                    detectHorizontalDragGestures { change, _ ->
                        val i = indiceDa(change.position.x, size.width.toFloat())
                        if (i != sceltaOra) { colpetto(); scegli(i) }
                    }
                },
        ) {
            disegnaBarra(tinte, ora, palette, attiva)
        }
    }
}

/** Le ore di un giorno. L'indice della colonna **e'** l'ora, e non serve una
 *  seconda verita' da tenere in fase con la prima. */
private const val ORE = 24

private val ALTEZZA = 34.dp

private fun indiceDa(x: Float, larghezza: Float): Int =
    (x / larghezza.coerceAtLeast(1f) * (ORE - 1)).roundToInt().coerceIn(0, ORE - 1)

/**
 * Il colore di ogni ora: la fermata di mezzo del **suo** cielo.
 *
 * Non e' una tavolozza a parte da tenere in fase con quella del fondo: e'
 * letteralmente la stessa funzione, chiamata ventiquattro volte con l'ora di
 * ogni colonna. Due tabelle divergono al primo che ne tara una; una sola non
 * puo'.
 */
private fun coloriDelleOre(
    hours: List<HourForecast>,
    alba: LocalDateTime?,
    tramonto: LocalDateTime?,
): List<Color> = List(ORE) { i ->
    val ora = hours.getOrNull(i)
    if (ora == null) {
        Color.Transparent
    } else {
        val cielo = SunClock.skyAt(ora.time, alba, tramonto, ora.isDay)
        val condizione = salaConditionOf(ora.weatherCode)
        val tempesta = when (condizione) {
            SalaCondition.TEMPORALE, SalaCondition.TEMPORALE_GRANDINE -> 1f
            else -> 0f
        }
        // **La stessa tabella della scena, chiamata e non ricopiata.** Qui
        // c'era la sua copia riga per riga: due elenchi da tenere in fase, e
        // alla prima famiglia aggiunta - la neve - uno dei due sarebbe rimasto
        // indietro tingendo le colonne di un cielo che la sala non mostra.
        val copertura = maxOf(
            (ora.cloudCover ?: 0) / 100f,
            coperturaMinima(condizione),
        )
        val neve = if (Wmo.family(ora.weatherCode) == Wmo.Family.NEVE) 1f else 0f
        cieloStops(faseContinua(cielo), livelloCielo(copertura, tempesta), neve)[1]
    }
}

private fun DrawScope.disegnaBarra(
    tinte: List<Color>,
    selected: Int,
    palette: SalaPalette,
    attiva: Boolean,
) {
    val w = size.width
    val cy = size.height / 2f
    val altoBinario = 16.dp.toPx()
    val cima = cy - altoBinario / 2f

    // Ritagliato dentro la pista arrotondata: cosi' le ventiquattro tessere
    // formano **una** barra con i capi tondi, e non ventiquattro mattoncini.
    val pista = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f, top = cima, right = w, bottom = cima + altoBinario,
                cornerRadius = CornerRadius(altoBinario / 2f),
            ),
        )
    }
    clipPath(pista) {
        val passo = w / ORE
        for (i in 0 until ORE) {
            val tinta = tinte.getOrNull(i) ?: palette.maniglia
            drawRect(
                color = if (tinta == Color.Transparent) palette.maniglia else tinta,
                topLeft = Offset(i * passo, cima),
                // Mezzo punto di sormonta: senza, fra una tessera e l'altra
                // resta una fessura chiara dovuta all'arrotondamento dei bordi.
                size = Size(passo + 0.5f, altoBinario),
            )
        }
    }

    // La maniglia: l'unica cosa che dice "questo si prende". Un disco pieno col
    // colore del pannello e un anello d'accento dentro - si stacca sia dalle
    // tessere chiare del mezzogiorno sia da quelle blu della notte, che un
    // pallino di un colore solo non farebbe.
    if (!attiva) return
    val x = (selected.toFloat() / (ORE - 1) * w).coerceIn(17.dp.toPx(), w - 17.dp.toPx())
    val rManiglia = 17.dp.toPx()
    drawCircle(color = palette.panelSolido, radius = rManiglia, center = Offset(x, cy))
    drawCircle(
        color = palette.accent,
        radius = 11.dp.toPx() - 2.5.dp.toPx(),
        center = Offset(x, cy),
        style = Stroke(width = 5.dp.toPx()),
    )
}
