package io.github.noximiliencoxen.caelum.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.noximiliencoxen.caelum.ui.temperature.DetailMode
import io.github.noximiliencoxen.caelum.ui.temperature.accentOf
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.MeteoType
import io.github.noximiliencoxen.caelum.ui.theme.skyAccents

/**
 * Il bordo del foglio del dettaglio, che sporge in fondo alla principale.
 *
 * **Il dettaglio c'era gia' e non si trovava.** Ci si arrivava toccando la
 * cifra della temperatura - un bersaglio senza alone, senza freccia e senza
 * nemmeno una semantica - oppure trascinando in alto, che e' un gesto che
 * nessuno annunciava. Chi non lo scopriva per caso aveva davanti un'app di una
 * schermata sola.
 *
 * Questa striscia non e' un'aggiunta decorativa: e' il **bordo superiore del
 * foglio**, disegnato dove il foglio sta quando e' chiuso. La maniglia dice che
 * si tira, i sei pallini dicono cosa c'e' oltre. Un foglio che sporge e' la cosa
 * che tutti hanno gia' imparato a tirare, e non ha bisogno di essere spiegato.
 *
 * **Non c'e' nessun riconoscitore di gesti qui dentro.** Il trascinamento
 * verticale e' gia' su tutta la schermata (`MeteoApp`, il `draggable` che
 * alimenta `SheetGesture`), quindi tirare la striglia alza il foglio senza una
 * riga in piu': la striscia ha solo bisogno di **esserci**. Quello che aggiunge
 * e' il tocco - e il tocco su un pallino non apre solo il foglio, lo apre
 * **gia' su quella grandezza**, che e' cio' che la cifra da sola non poteva
 * fare.
 *
 * **Le sei parole si vedono solo dove ci stanno.** Sotto i 340 punti di
 * larghezza restano i soli pallini: sei etichette troncate a meta' non dicono
 * sei cose, ne dicono zero, e il colore da solo continua a distinguerle.
 *
 * Del colore, che qui e' la trappola: le tinte delle grandezze che vivono in
 * `LocalMeteoAccents` sono tarate sull'antracite dei pannelli, e sopra il cielo
 * di meta' mattina - grigio chiaro - il giallo del sole sparirebbe. Si prendono
 * da `skyAccents()`, che le tara sui due capi della sfumatura. E' la regola
 * della sezione 8-bis di CONTESTO: se una tinta e' interpolata, cio' che ci va
 * sopra non si sceglie, si calcola.
 */
@Composable
fun SheetEdge(
    current: DetailMode,
    onOpenPanel: (DetailMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    // `skyAccents` fa una decina di elevamenti a potenza per tinta: dietro una
    // chiave che cambia solo quando cambia il cielo, cioe' un pugno di volte al
    // giorno, e non a ogni ora scorsa sulla barra qui sopra.
    val accents = remember(colors) { colors.skyAccents() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(EDGE_HEIGHT)) {
        val conParole = maxWidth >= LABELS_FROM

        Row(modifier = Modifier.fillMaxWidth()) {
            DetailMode.entries.forEach { mode ->
                val tinta = mode.accentOf(accents, colors.text)
                val scelta = mode == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // **Alto quanto la striscia**, non quanto il pallino:
                        // sono i 50 punti che rendono il bersaglio superiore al
                        // minimo di Material, mentre il disegno resta di sei.
                        // E' la stessa distinzione di `MeteoIconButton`.
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            // Come dappertutto su questa schermata: sopra un
                            // cielo che cambia colore un alone grigio e' l'unica
                            // cosa che sembri un pulsante di sistema. Il
                            // riscontro qui e' il foglio che sale.
                            indication = null,
                            onClick = { onOpenPanel(mode) },
                        )
                        // Una voce sola per chi ascolta, e col nome per esteso:
                        // "Temp" e' un'abbreviazione che sta in una striscia,
                        // non una parola da farsi leggere.
                        .clearAndSetSemantics {
                            contentDescription = mode.title
                            role = Role.Button
                            onClick(label = APRI) { onOpenPanel(mode); true }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(DOT)
                            .clip(CircleShape)
                            // Lo spento non e' un colore diverso, e' lo stesso
                            // meno acceso: due tinte separate si sarebbero
                            // scollate alla prima passata sui colori.
                            .background(if (scelta) tinta else tinta.copy(alpha = SPENTO)),
                    )
                    if (conParole) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = mode.chipLabel,
                            style = EDGE_LABEL,
                            color = if (scelta) colors.text else colors.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // La maniglia sta **sopra** le sei voci e non in mezzo a loro: e' il
        // segno del foglio intero, non di una delle sue pagine. Disegnata dopo,
        // cioe' sopra, e senza bersaglio proprio - a riceverla c'e' gia' la
        // voce che le sta sotto, e il trascinamento la prende comunque.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 5.dp)
                .width(HANDLE_WIDTH)
                .height(HANDLE_HEIGHT)
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.line),
        )
    }
}

/**
 * Cinquanta punti: due sopra il minimo toccabile.
 *
 * Li paga quasi tutti il margine che il tasto "torna ad adesso" teneva sotto di
 * se' - ventisei punti di vuoto messi li' per staccare la colonna dal bordo, un
 * mestiere che adesso fa la striscia. Il resto lo cede la scultura, e sono
 * ventidue punti per la sola cosa che diceva a chi apre l'app che c'e' un
 * seguito.
 */
private val EDGE_HEIGHT = 50.dp

private val DOT = 6.dp

private val HANDLE_WIDTH = 34.dp

private val HANDLE_HEIGHT = 4.dp

/** Quanto e' spenta la tinta di una grandezza che non e' quella d'ingresso. */
private const val SPENTO = 0.45f

/** Sotto questa larghezza restano i soli pallini. */
private val LABELS_FROM = 340.dp

/**
 * Piu' piccola della didascalia dell'app, e meno spaziata.
 *
 * Sei parole in una riga sola: a dodici punti con la spaziatura di
 * `MeteoType.caption` "Pioggia" da sola ne occupa settanta, e sei non ci
 * stanno su nessun telefono. La famiglia resta quella, cosi' la striscia e' la
 * stessa scrittura del resto della schermata scritta piu' in piccolo, non un
 * carattere nuovo.
 */
private val EDGE_LABEL = MeteoType.caption.copy(fontSize = 10.sp, letterSpacing = 0.02.em)

private const val APRI = "aprire il dettaglio"
