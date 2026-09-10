package io.github.noximiliencoxen.caelum.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * La finestra da galleria: l'elemento visivo dentro una cornice sottile.
 *
 * E' il perno dello stile editoriale. Prima ogni scheda del feed appoggiava il
 * proprio corpo - la scultura, la finestra della pioggia, la sfera della luna -
 * **direttamente sul cielo**, senza un bordo: il cielo era insieme il fondo
 * della scheda e il fondo dell'oggetto, e l'oggetto galleggiava. Con una cornice
 * il cielo dentro diventa la tela e il cielo fuori resta la parete: e' la stessa
 * immagine, ma dichiarata.
 *
 * **Nessun fondo dipinto.** La cornice e' una finestra sul cielo, non un
 * pannello: se ci si mettesse dentro una superficie si perderebbe il cielo
 * dell'ora, che e' la meta' del disegno, e si tornerebbe alle schede antracite
 * che la sezione 8-bis di CONTESTO ha tolto di mezzo.
 *
 * **Nessun modificatore di gesto, e non e' un'omissione.** La rotazione della
 * camera 3D vive su `rotatesScene`, che sta sul contenuto: la parallasse dello
 * strombo della finestra, il giro della luna e l'inclinazione del telefono
 * passano di qui senza accorgersene. Una cornice che intercettasse il
 * trascinamento orizzontale spegnerebbe in un colpo solo tutto cio' che questa
 * app ha di tridimensionale.
 *
 * **I due fili si disegnano in una tela sopra il contenuto, non con
 * `Modifier.border`.** Un bordo del modificatore si dipinge **prima** dei figli,
 * quindi la scultura che tocca il margine gli passerebbe sopra e la cornice si
 * interromperebbe proprio dove l'oggetto e' piu' grande. Sopra il contenuto,
 * invece, il filo resta continuo - ed e' anche cio' che permette di tracciare il
 * passe-partout, il secondo filo piu' interno, senza inventare una forma nuova.
 *
 * Gli angoli sono **appena** smussati, cinque punti. La scheda in vetro che le
 * si appoggia sopra ne usa venti: due raggi uguali farebbero leggere cornice e
 * scheda come lo stesso oggetto, e la sovrapposizione perderebbe il suo senso.
 */
@Composable
internal fun ArtFrame(
    line: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(FRAME_CORNER)),
        contentAlignment = Alignment.Center,
    ) {
        content()
        // Ultima figlia: si disegna sopra tutto. Non prende tocchi, perche' una
        // tela senza `pointerInput` non e' un bersaglio.
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = FRAME_STROKE.toPx()
            val corner = FRAME_CORNER.toPx()
            drawRoundRect(
                color = line.copy(alpha = FRAME_ALPHA),
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(corner),
                style = Stroke(width = stroke),
            )
            val inset = PASSEPARTOUT.toPx()
            if (size.width <= inset * 2f || size.height <= inset * 2f) return@Canvas
            drawRoundRect(
                color = line.copy(alpha = MAT_ALPHA),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                cornerRadius = CornerRadius((corner - inset).coerceAtLeast(0f)),
                style = Stroke(width = stroke),
            )
        }
    }
}

/**
 * La cornice e la scheda che le si appoggia sopra il bordo di sotto.
 *
 * **Perche' un `Layout` a mano e non un `Box` con un offset.** La scheda e' alta
 * quanto il suo contenuto, e il contenuto cambia da sezione a sezione: la
 * pioggia ci mette dentro la fascia delle ventiquattro ore, la luna solo tre
 * numeri. La cornice deve prendersi tutta l'altezza che la scheda **non** usa,
 * piu' i punti di sovrapposizione, e quell'altezza non si sa finche' la scheda
 * non e' misurata. Le due strade da libreria costano entrambe piu' di venti
 * righe di misura:
 *
 * - un `Box` con la scheda in fondo e un padding scritto a mano sulla cornice
 *   vuol dire indovinare l'altezza della scheda, e sbagliarla ogni volta che
 *   qualcuno tocca il contenuto o che cresce il carattere di sistema;
 * - un `onSizeChanged` che riporta l'altezza in uno stato costa una seconda
 *   passata di layout **a ogni ricomposizione**, e la prima e' con la cornice
 *   della misura sbagliata: si vedrebbe saltare all'apertura di ogni scheda.
 *
 * Qui si misura la scheda per prima e poi la cornice con quel che resta: una
 * passata sola, deterministica, e nessuno stato di mezzo.
 *
 * [overlap] e' quanto la scheda entra dentro la cornice. Va tenuto in accordo
 * con il padding di sopra della scheda (`GlassPanel`): sotto il bordo della
 * cornice deve passare **solo il bordo del vetro**, mai una riga di testo, se no
 * quella riga si troverebbe sopra la finestra della pioggia illuminata e nessun
 * calcolo di contrasto potrebbe salvarla - il fondo li' non e' il cielo, e' il
 * disegno.
 */
@Composable
internal fun ArtGalleryStage(
    frame: @Composable () -> Unit,
    card: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlap: Dp = STAGE_OVERLAP,
) {
    Layout(contents = listOf(frame, card), modifier = modifier) { measurables, constraints ->
        val (frameOnes, cardOnes) = measurables
        val width = constraints.maxWidth
        val loose = Constraints(minWidth = width, maxWidth = width)
        val cardPlaceable = cardOnes.first().measure(loose)

        val overlapPx = overlap.roundToPx()
        // Con un'altezza sciolta - non capita nel feed, dove la scena sta in una
        // colonna con `weight`, ma un `Layout` che si pianta fuori dal suo caso
        // e' una mina - la cornice si prende la propria altezza naturale e il
        // totale viene dalla somma.
        val framePlaceable = if (constraints.hasBoundedHeight) {
            val tall = (constraints.maxHeight - cardPlaceable.height + overlapPx).coerceAtLeast(0)
            frameOnes.first().measure(Constraints.fixed(width, tall))
        } else {
            frameOnes.first().measure(loose)
        }

        val height = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            framePlaceable.height + cardPlaceable.height - overlapPx
        }

        layout(width, height) {
            framePlaceable.place(0, 0)
            cardPlaceable.place(0, height - cardPlaceable.height)
        }
    }
}

/** Quanto la scheda in vetro entra dentro la cornice. */
internal val STAGE_OVERLAP = 22.dp

private val FRAME_CORNER = 5.dp
private val FRAME_STROKE = 1.dp

/** L'inserto del secondo filo: il passe-partout di un quadro incorniciato. */
private val PASSEPARTOUT = 10.dp

private const val FRAME_ALPHA = 0.70f
private const val MAT_ALPHA = 0.30f
