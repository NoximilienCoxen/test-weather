package io.github.noximiliencoxen.caelum.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * La colonna di icone sul bordo destro: dove si e', quante sezioni ci sono, e
 * come arrivarci in un tocco.
 *
 * **Sta sopra il carosello e non dentro**, quindi resta ferma mentre le schede
 * scorrono: e' l'unica cosa in scena che non si muove, ed e' cio' che la rende
 * un punto di riferimento invece di un sesto contenuto. E' anche il motivo per
 * cui il feed puo' essere profondo sei schede senza che ci si perda dentro: uno
 * scorrimento infinito senza una mappa e' un pozzo.
 *
 * **L'accensione si legge dentro il disegno.** La posizione del carosello arriva
 * per lambda e cambia a ogni fotogramma del dito: letta in composizione
 * ricomporrebbe sei icone sessanta volte al secondo per travasare una tinta.
 * Letta dentro la tela, il travaso si ferma alla fase di disegno - e' la stessa
 * regola dei pallini del vecchio carosello, e la ragione per cui le caselle
 * hanno **misura fissa**: nessun fotogramma rifa' il layout.
 *
 * Le tinte vengono da [rememberSkyAccents] e non da `LocalMeteoAccents`: la
 * colonna vive sul cielo, e le tinte dei pannelli sopra un cielo di meta'
 * mattina sparirebbero. E' la regola della sezione 8-bis di CONTESTO.
 *
 * **Le icone sono disegnate**, non caricate: `material-icons-extended` e' un
 * catalogo intero per sei glifi, ed e' una decisione di progetto ripetuta - la
 * freccia, la croce, l'orologio e il pulsante delle impostazioni sono tele come
 * queste.
 */
@Composable
fun FeedRail(
    sections: List<FeedSection>,
    position: () -> Float,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val accents = rememberSkyAccents()
    // Le sei tinte si ricavano una volta sola: dentro il disegno ci arriva un
    // elenco gia' pronto, non una funzione da richiamare a ogni fotogramma.
    val tints = remember(accents, colors) {
        sections.map { it.accentOf(accents, colors.text) }
    }
    val idle = colors.label

    Column(
        modifier = modifier.width(TARGET),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        sections.forEachIndexed { index, section ->
            Box(
                modifier = Modifier
                    .width(TARGET)
                    .height(TARGET)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        // Come dappertutto sul cielo: un alone grigio sopra un
                        // fondo che cambia colore tutto il giorno e' l'unica
                        // cosa che sembri un pulsante di sistema. Il riscontro
                        // qui e' la scheda che arriva.
                        indication = null,
                        onClick = { onPick(index) },
                    )
                    .clearAndSetSemantics {
                        contentDescription = section.title
                        role = Role.Button
                        onClick(label = VAI) { onPick(index); true }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(GLYPH)) {
                    // La distanza dalla pagina corrente decide la tinta, e si
                    // legge **qui**: mentre il dito trascina, il colore si
                    // travasa da un'icona alla successiva senza che nessuno
                    // ricomponga niente.
                    val share = (1f - abs(index - position())).coerceIn(0f, 1f)
                    val ink = lerp(idle.copy(alpha = SPENTO), tints[index], share)
                    drawSectionGlyph(section, ink)
                }
            }
        }
    }
}

/**
 * I sei glifi, ciascuno dentro il quadrato che gli si da'.
 *
 * Minimali per scelta e non per fretta: sono alti diciotto punti sul bordo dello
 * schermo, e un disegno con dentro dei dettagli a quella misura si legge come
 * una macchia. Quello che li distingue e' la **sagoma** - un'asta con una palla
 * in fondo, una goccia, un cerchio con dei raggi - non cio' che hanno dentro.
 *
 * Aria e vento sono i due che si somigliano di piu' e sono stati separati
 * apposta: il vento e' fatto di **linee che scorrono**, l'aria di **granelli
 * dentro un contorno**. Sono due immagini diverse, non la stessa con un tratto
 * in piu' o in meno.
 */
private fun DrawScope.drawSectionGlyph(section: FeedSection, ink: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val line = Stroke(width = w * 0.11f, cap = StrokeCap.Round)

    when (section) {
        // Termometro: asta e bulbo. Il bulbo e' pieno perche' e' il liquido, ed
        // e' anche cio' che rende la sagoma riconoscibile da lontano.
        FeedSection.TEMPERATURA -> {
            val bulb = w * 0.17f
            drawLine(
                color = ink,
                start = Offset(cx, h * 0.16f),
                end = Offset(cx, h * 0.60f),
                strokeWidth = line.width,
                cap = StrokeCap.Round,
            )
            drawCircle(color = ink, radius = bulb, center = Offset(cx, h - bulb - h * 0.06f))
        }

        // Goccia: due archi che si chiudono in punta. Piena, perche' un
        // contorno di goccia a diciotto punti si legge come un cerchio storto.
        FeedSection.PRECIPITAZIONI -> {
            val top = h * 0.10f
            val radius = w * 0.30f
            val center = Offset(cx, h * 0.66f)
            val drop = Path().apply {
                moveTo(cx, top)
                cubicTo(
                    cx + radius * 0.95f, h * 0.42f,
                    cx + radius, center.y - radius * 0.30f,
                    cx + radius * 0.72f, center.y + radius * 0.52f,
                )
                cubicTo(
                    cx + radius * 0.30f, center.y + radius * 1.05f,
                    cx - radius * 0.30f, center.y + radius * 1.05f,
                    cx - radius * 0.72f, center.y + radius * 0.52f,
                )
                cubicTo(
                    cx - radius, center.y - radius * 0.30f,
                    cx - radius * 0.95f, h * 0.42f,
                    cx, top,
                )
                close()
            }
            drawPath(drop, ink)
        }

        // Aria: granelli dentro un contorno. E' il pulviscolo, che e' cio' che
        // l'indice europeo misura davvero - non una brezza.
        FeedSection.ARIA -> {
            drawCircle(color = ink, radius = w * 0.40f, center = Offset(cx, cy), style = line)
            val grain = w * 0.075f
            drawCircle(color = ink, radius = grain, center = Offset(cx - w * 0.15f, cy - h * 0.10f))
            drawCircle(color = ink, radius = grain, center = Offset(cx + w * 0.13f, cy - h * 0.02f))
            drawCircle(color = ink, radius = grain, center = Offset(cx - w * 0.03f, cy + h * 0.16f))
        }

        // Vento: linee che scorrono, quella di mezzo piu' lunga, con il ricciolo
        // in fondo che dice il verso.
        FeedSection.VENTO -> {
            val curl = w * 0.15f
            drawLine(
                color = ink,
                start = Offset(w * 0.12f, h * 0.30f),
                end = Offset(w * 0.66f, h * 0.30f),
                strokeWidth = line.width,
                cap = StrokeCap.Round,
            )
            drawArc(
                color = ink,
                startAngle = 110f,
                sweepAngle = 250f,
                useCenter = false,
                topLeft = Offset(w * 0.66f - curl, h * 0.30f - curl),
                size = Size(curl * 2f, curl * 2f),
                style = line,
            )
            drawLine(
                color = ink,
                start = Offset(w * 0.12f, h * 0.62f),
                end = Offset(w * 0.58f, h * 0.62f),
                strokeWidth = line.width,
                cap = StrokeCap.Round,
            )
            drawArc(
                color = ink,
                startAngle = 110f,
                sweepAngle = 250f,
                useCenter = false,
                topLeft = Offset(w * 0.58f - curl, h * 0.62f - curl),
                size = Size(curl * 2f, curl * 2f),
                style = line,
            )
        }

        // Sole: disco e raggi. Otto e non dodici: a diciotto punti i dodici si
        // toccano fra loro e il glifo diventa una ruota piena.
        FeedSection.SOLE -> {
            drawCircle(color = ink, radius = w * 0.22f, center = Offset(cx, cy))
            val inner = w * 0.32f
            val outer = w * 0.46f
            repeat(RAYS) { i ->
                val a = (2.0 * Math.PI * i / RAYS).toFloat()
                val dx = cos(a)
                val dy = sin(a)
                drawLine(
                    color = ink,
                    start = Offset(cx + dx * inner, cy + dy * inner),
                    end = Offset(cx + dx * outer, cy + dy * outer),
                    strokeWidth = line.width,
                    cap = StrokeCap.Round,
                )
            }
        }

        // Falce: un disco meno un disco. Il secondo non si dipinge del colore
        // del fondo - qui il fondo e' un cielo che cambia tutto il giorno, e una
        // toppa di tinta fissa si vedrebbe come una macchia. Si toglie con la
        // regola di riempimento, che non ha bisogno di sapere cosa c'e' sotto.
        FeedSection.LUNA -> {
            val r = w * 0.42f
            val bite = r * 0.86f
            val crescent = Path().apply {
                fillType = PathFillType.EvenOdd
                addOval(Rect(Offset(cx - r, cy - r), Size(r * 2f, r * 2f)))
                addOval(
                    Rect(
                        Offset(cx - bite + w * 0.20f, cy - bite),
                        Size(bite * 2f, bite * 2f),
                    ),
                )
            }
            drawPath(crescent, ink)
        }
    }
}

/**
 * Il posto che la colonna si prende, e che le schede lasciano libero a destra.
 *
 * Quarantaquattro e non quarantotto: e' lo spazio **riservato** nel margine, e
 * il bersaglio e' piu' largo di cosi' - la colonna e' larga [TARGET] e sborda di
 * quattro punti dentro il margine della scheda, che li' e' vuoto. Il disegno
 * resta di diciotto: quello che si tocca non e' quello che si vede, ed e' la
 * stessa distinzione di `MeteoIconButton`.
 */
internal val RAIL_WIDTH = 44.dp

private val TARGET = 48.dp

private val GLYPH = 18.dp

private val GAP = 2.dp

/**
 * Quanto e' **alta** la colonna: sei caselle piu' i cinque stacchi.
 *
 * Serve alle schede, che devono sapere quali delle loro righe le passano
 * davanti. La colonna sta a meta' altezza (`align(CenterEnd)`) e scheda e
 * colonna portano entrambe `systemBarsPadding()`, quindi condividono il
 * centro: titolo e numeri la scavalcano **se la scheda e' alta abbastanza**, e
 * quanto sia abbastanza si ricava da qui invece che da un numero scritto a mano
 * dall'altra parte, che divergerebbe alla prima casella aggiunta.
 */
internal val RAIL_SPAN = TARGET * 6 + GAP * 5

/** Quanto e' spenta l'icona di una sezione che non e' quella in scena. */
private const val SPENTO = 0.42f

private const val RAYS = 8

private const val VAI = "andare a questa sezione"
