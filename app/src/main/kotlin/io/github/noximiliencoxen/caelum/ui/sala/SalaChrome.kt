package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.theme.MinTouchTarget
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * I pezzi che tutte le sale hanno in comune: il pannello a cassetto, le celle
 * dei valori, le pastiglie, i glifi del tempo, l'intestazione e la colonna
 * delle scorciatoie.
 *
 * Stanno in un file solo perche' nel prototipo sono letteralmente lo stesso
 * blocco di stile ripetuto sette volte: raggi, spaziature e opacita' coincidono
 * riga per riga, e tenerne sette copie vorrebbe dire che alla prima rifinitura
 * sei sale su sette restano indietro.
 */

/** Il raggio del pannello, e di tutto cio' che gli assomiglia. */
private val RaggioPannello = 42.dp
private val RaggioCella = 22.dp

/**
 * Il pannello a cassetto su cui vive ogni sala.
 *
 * E' ancorato in basso e non riempie la schermata: sopra di lui resta il cielo,
 * ed e' quello il punto della direzione scelta - il tempo si guarda, e i numeri
 * gli stanno davanti senza coprirlo.
 */
@Composable
fun PannelloSala(
    palette: SalaPalette,
    modifier: Modifier = Modifier,
    /**
     * Un riflesso dorato che attraversa la scheda ogni tanto.
     *
     * Serve a **una cosa sola**: dire che il numero qui dentro e' alto senza
     * scriverlo una seconda volta. Lo usa Sala VII quando i raggi UV superano
     * il valore cinque, e se un giorno servisse altrove il posto e' questo -
     * ma la regola e' che passi **di rado**, perche' un pannello che
     * luccicasse sempre non direbbe piu' niente.
     */
    bagliore: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(RaggioPannello))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.panel)
                // **Queste misure sono state allargate e poi rimesse, e la nota
                // resta perche' la prova conta piu' della misura.** Il ragionamento
                // era: il pannello e' ancorato in basso, si dimensiona sul
                // contenuto, quindi allargandolo sale nel cielo che sopra resta
                // inutilizzato. Vero per le sale corte. Falso per "La settimana",
                // che di cielo sopra **non ne ha**: e' gia' alta quanto lo schermo,
                // e ogni punto in piu' non la fa salire, le taglia una riga in
                // fondo. Lo scatto della CI l'ha mostrato subito.
                .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 18.dp),
        ) {
            // **Qui c'era una maniglia, ed e' stata tolta.** Cinquantadue punti
            // per cinque, in cima al pannello: voleva dire "questa cosa sta
            // sopra un'altra". Quello che diceva davvero e' un'altra cosa - una
            // maniglia orizzontale al centro di una scheda e' il segno con cui
            // mezzo mondo apre un foglio a cassetto, cioe' **si trascina in
            // verticale**. Il pannello in verticale non si trascina: scorre il
            // suo contenuto quando non ci sta, e per cambiare sala si va di
            // lato. Un comando che promette un gesto che non esiste costa piu'
            // di un comando assente, e questo lo prometteva a ogni sala.
            //
            // I diciannove punti che occupava - cinque di maniglia, quattordici
            // di stacco - tornano al cielo: il pannello e' ancorato in basso,
            // quindi accorciarlo non sposta niente verso il basso, scopre in
            // alto. Il margine superiore sale da venti a ventiquattro perche'
            // il titolo non si appoggi al raggio dell'angolo, che qui e' largo.
            content()
        }
        // Sopra il contenuto e dentro il ritaglio del `Box`, cosi' il riflesso
        // si ferma sul bordo arrotondato invece di uscire in un rettangolo.
        if (bagliore) BaglioreDorato(Modifier.matchParentSize())
    }
}

/**
 * Il riflesso che passa, e la cadenza con cui passa.
 *
 * **E' un composable a parte e non un modificatore, ed e' per poterlo chiamare
 * dentro un `if`.** Un modificatore che dentro di se' chiama `remember`
 * cambierebbe il numero di ricordi a seconda di un booleano, nello stesso
 * punto dell'albero; un composable ha un gruppo suo, e chiamarlo o non
 * chiamarlo e' una cosa che Compose sa fare da sempre.
 *
 * Una banda chiara inclinata attraversa la scheda in poco piu' di un secondo,
 * poi non succede niente per cinque. La pausa e' la parte importante: un
 * riflesso continuo diventa fondo, e un fondo non avvisa di niente.
 */
@Composable
private fun BaglioreDorato(modifier: Modifier = Modifier) {
    val giro = rememberInfiniteTransition(label = "bagliore")
    val fase by giro.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6200, easing = LinearEasing), RepeatMode.Restart),
        label = "fase",
    )
    Canvas(modifier = modifier) {
        // Il riflesso vive nel primo quinto del giro; per il resto del tempo
        // la banda sta fuori dal bordo e non si disegna niente.
        val q = fase / 0.2f
        if (q > 1f) return@Canvas
        val larghezza = size.width * 0.34f
        val centro = -larghezza + q * (size.width + larghezza * 2f)
        // Piu' tenue in partenza e in chiusura, cosi' entra ed esce invece di
        // comparire al bordo.
        val forza = sin(q * PI.toFloat())
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.5f to SalaTokens.accent300.copy(alpha = 0.34f * forza),
                1f to Color.Transparent,
                startX = centro - larghezza,
                endX = centro + larghezza,
            ),
        )
    }
}

/**
 * Una cella: etichetta in maiuscoletto, valore sotto. Tre per riga, di solito.
 *
 * ## Quando e' anche una porta
 *
 * Con [onVai] la cella diventa una scorciatoia: si tocca e si va nella sala che
 * di quel numero parla per esteso. E' lo stesso ragionamento dei sei riquadri
 * di Sala II - *un numero che riassume e' utile finche' non se ne vuole sapere
 * di piu', e a quel punto la domanda e' sempre "dove lo vedo per intero?"* -
 * portato dove quella domanda nasce piu' spesso, cioe' nella prima schermata.
 *
 * La freccetta non e' una decorazione ed e' **l'unica** cosa che distingue una
 * cella che porta da una che si limita a dire: senza, l'unico modo di scoprire
 * la scorciatoia sarebbe toccare a caso tre riquadri che sembrano etichette. E'
 * lo stesso segno di Sala II, in piccolo, perche' due segni diversi per la
 * stessa promessa sono due cose da imparare invece di una.
 */
@Composable
fun RowScope.CellaValore(
    etichetta: String,
    valore: String,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
    /** Dove porta la cella, se porta da qualche parte. */
    onVai: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .weight(1f)
            .clip(RoundedCornerShape(RaggioCella))
            .background(palette.chip)
            // `then` e non due rami di `Column`: il corpo della cella e' lo
            // stesso, e duplicarlo per un modificatore vorrebbe dire che alla
            // prima rifinitura una delle due copie resta indietro.
            .then(if (onVai != null) Modifier.clickable(onClick = onVai) else Modifier)
            // **Tre punti di margine in meno per lato, e sono sei di parola in
            // piu'.** "PROBABILITÀ" non ci stava per intero nemmeno prima di
            // ritoccare i corpi: negli scatti si leggeva "PROBABILI", e
            // nessuno l'aveva notato perche' troncata sembra un'abbreviazione
            // voluta. Tre celle per riga, undici caratteri la piu' lunga: il
            // margine era la cosa da stringere, non la parola da accorciare.
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // **I puntini sono la cosa piu' importante di questa riga.** Senza,
        // `maxLines = 1` tagliava e basta: "PROBABILITÀ" diventava
        // "PROBABILI", "ESPOSIZIONE" diventava "ESPOSIZION", e nessuno se ne
        // accorgeva **perche' una parola tagliata netta sembra
        // un'abbreviazione voluta**. Sono rimaste cosi' per mesi, in scatti
        // che qualcuno ha guardato. Coi puntini un'etichetta che non ci sta e'
        // visibilmente rotta, e chi la vede la accorcia.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = etichetta,
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Il peso sta sull'etichetta e non sulla freccia: quando la
                // parola non ci sta sono i puntini a comparire, non una
                // freccetta schiacciata contro il bordo.
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onVai != null) {
                Text(
                    text = " ›",
                    style = SalaType.sectionLabel,
                    color = palette.accent,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = valore,
            style = SalaType.value,
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Quando il giorno scelto non ha le ore.
 *
 * **Si dichiara invece di disegnare una giornata piatta.** `shownHours` torna
 * vuota oltre le ~72 ore: i modelli a corto raggio danno i totali del giorno e
 * non le sue ore. Mostrare al loro posto quelle di **oggi** sotto
 * l'intestazione di giovedi' sarebbe la stessa bugia della galleria che
 * ritraeva l'Ingresso in ogni scatto - un dato vero, messo dove non e' vero.
 */
@Composable
fun RigaSenzaOre(palette: SalaPalette, modifier: Modifier = Modifier) {
    Text(
        text = "Per questo giorno la previsione dà i totali, non le ore.",
        style = SalaType.footnote,
        color = palette.inkFaint,
        modifier = modifier,
    )
}

/** Una pastiglia piena d'accento: "torna a oggi", il giorno scelto, l'ora attuale. */
@Composable
fun PastigliaAccento(
    testo: String,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    Text(
        text = testo,
        style = SalaType.pill,
        color = palette.accentInk,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(palette.accent)
            .padding(horizontal = 13.dp, vertical = 5.dp),
    )
}

// ── I glifi del tempo ───────────────────────────────────────────────────────

/**
 * Il tempo di un giorno, ridotto a **cosa si disegna**.
 *
 * Non e' [SalaCondition] con altri nomi: quella distingue cio' che cambia il
 * colore del cielo, questa cio' che cambia la figuretta. Un cielo sereno e uno
 * poco coperto tingono la stessa carta e hanno due icone diverse; una pioggia e
 * una pioviggine hanno la stessa icona e non lo stesso cielo.
 */
enum class GlifoMeteo { SOLE, POCO, NUVOLE, NEBBIA, PIOGGIA, TEMPORALE, NEVE }

/**
 * Quale figuretta per questo codice WMO e questa nuvolosita'.
 *
 * La nuvolosita' entra solo dove il codice non decide gia' tutto: fra "sereno"
 * e "poco coperto" il codice WMO non ha una riga sola, ce l'ha il dato.
 */
fun glifoDi(weatherCode: Int?, cloudCover: Int?): GlifoMeteo {
    val famiglia = Wmo.family(weatherCode)
    return when {
        famiglia == Wmo.Family.TEMPORALE -> GlifoMeteo.TEMPORALE
        famiglia == Wmo.Family.NEVE -> GlifoMeteo.NEVE
        // Qui c'era una riga per la grandine, e non la raggiungeva nessun
        // codice: 77, 85 e 86 sono di famiglia NEVE, quindi li consuma gia' la
        // riga sopra. Era irraggiungibile, non inutilizzata.
        weatherCode == 96 || weatherCode == 99 -> GlifoMeteo.TEMPORALE
        famiglia == Wmo.Family.PIOGGIA -> GlifoMeteo.PIOGGIA
        famiglia == Wmo.Family.NEBBIA -> GlifoMeteo.NEBBIA
        // Senza nuvolosita' decide il codice: il 3 e' "coperto", e un sole che
        // spunta da dietro la nuvola lo racconterebbe meglio di com'e'.
        famiglia == Wmo.Family.NUVOLOSO ->
            if ((cloudCover ?: if (weatherCode == 3) 100 else 60) < 62) GlifoMeteo.POCO else GlifoMeteo.NUVOLE
        else -> if ((cloudCover ?: 0) < 25) GlifoMeteo.SOLE else GlifoMeteo.POCO
    }
}

/**
 * La figuretta del tempo: sole, sole con nuvola, nuvole, pioggia, temporale,
 * neve, e di notte la luna al posto del sole.
 *
 * **A colori, e non piu' a macchie piatte.** Erano un disco arancione e due
 * ovali grigi, tinti col tema: si capiva che i giorni erano diversi, non che
 * tempo facevano. Adesso sono disegnate sul modello delle icone di Google Meteo
 * - il sole giallo, le nuvole bianche col bordo, le gocce blu, il fulmine
 * giallo, la luna azzurra - **ma sono nostre**: quelle di Google sono sue e non
 * hanno una licenza che ne permetta il riuso. Forme e colori sono stati scelti
 * qui, provati prima in uno schizzo su fondo chiaro e scuro.
 *
 * **Non seguono il tema**, come la luna di Sala IV: un sole e' giallo su
 * qualunque pagina. Il bordo delle nuvole c'e' per questo - senza, una nuvola
 * bianca su un pannello chiaro sparirebbe.
 *
 * Tutto e' misurato su un quadrato di ventiquattro unita', riscalato alla tela.
 */
@Composable
fun IconaMeteo(glifo: GlifoMeteo, modifier: Modifier = Modifier, notte: Boolean = false) {
    Canvas(modifier = modifier.size(22.dp)) { disegnaGlifo(glifo, notte) }
}

/** I colori delle figurette: fissi, in tutti e due i temi. */
private object ColoriGlifo {
    val sole = Color(0xFFFFC83D)
    val bordoSole = Color(0xFFF2A516)
    val nuvola = Color(0xFFF6F7FA)
    val bordoNuvola = Color(0xFF8C96A5)
    val nuvolaCarica = Color(0xFFC3CAD4)
    val bordoCarica = Color(0xFF737D8C)
    val goccia = Color(0xFF4A93E0)
    val luna = Color(0xFFA9BDF5)
    val fiocco = Color(0xFF7FBDEB)
}

internal fun DrawScope.disegnaGlifo(glifo: GlifoMeteo, notte: Boolean) {
    val u = size.minDimension / 24f
    // Centrata anche su una tela non quadrata: i widget ne danno di ogni forma.
    val ox = (size.width - 24f * u) / 2f
    val oy = (size.height - 24f * u) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)

    // Il sole a petali: otto tondi attorno a un disco col bordo appena piu' caldo.
    fun sole(cx: Float, cy: Float, r: Float) {
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            drawCircle(ColoriGlifo.sole, r * 0.36f * u, p(cx + cos(a) * r * 1.32f, cy + sin(a) * r * 1.32f))
        }
        drawCircle(ColoriGlifo.bordoSole, (r + 0.6f) * u, p(cx, cy))
        drawCircle(ColoriGlifo.sole, r * u, p(cx, cy))
    }

    // La falce: un disco meno un disco spostato, come il terminatore.
    fun luna(cx: Float, cy: Float, r: Float) {
        val disco = Path().apply { addOval(Rect(p(cx - r, cy - r), p(cx + r, cy + r))) }
        val morso = Path().apply {
            addOval(Rect(p(cx - r + r * 0.62f, cy - r - r * 0.18f), p(cx + r + r * 0.62f, cy + r - r * 0.18f)))
        }
        drawPath(Path().apply { op(disco, morso, PathOperation.Difference) }, ColoriGlifo.luna)
    }

    // La nuvola: una base arrotondata e due gobbe, unite in una sagoma sola
    // perche' il bordo giri attorno a tutto e non dentro.
    fun nuvola(ox: Float, oy: Float, scala: Float, carica: Boolean) {
        fun q(x: Float, y: Float) = p(ox + x * scala, oy + y * scala)
        val base = Path().apply {
            addRoundRect(RoundRect(Rect(q(3f, 11f), q(21f, 19f)), CornerRadius(4f * scala * u)))
        }
        val sinistra = Path().apply { addOval(Rect(q(4.5f, 7f), q(13.5f, 16f))) }
        val destra = Path().apply { addOval(Rect(q(9f, 4f), q(20f, 15f))) }
        val sagoma = Path().apply {
            op(base, sinistra, PathOperation.Union)
            op(this, destra, PathOperation.Union)
        }
        drawPath(
            sagoma,
            if (carica) ColoriGlifo.bordoCarica else ColoriGlifo.bordoNuvola,
            style = Stroke(width = 2.2f * u, join = StrokeJoin.Round),
        )
        drawPath(sagoma, if (carica) ColoriGlifo.nuvolaCarica else ColoriGlifo.nuvola)
    }

    // La goccia: un tondo con la punta in alto, che e' da dove viene.
    fun goccia(x: Float, y: Float) {
        drawCircle(ColoriGlifo.goccia, 1.3f * u, p(x, y))
        drawPath(
            Path().apply {
                p(x - 1.2f, y - 0.5f).let { moveTo(it.x, it.y) }
                p(x + 1.2f, y - 0.5f).let { lineTo(it.x, it.y) }
                p(x + 1.0f, y - 3.2f).let { lineTo(it.x, it.y) }
                close()
            },
            ColoriGlifo.goccia,
        )
    }

    // Il fiocco: tre stanghette incrociate, sei punte.
    fun fiocco(x: Float, y: Float, r: Float) {
        for (k in 0 until 3) {
            val a = k * PI.toFloat() / 3f
            drawLine(
                ColoriGlifo.fiocco,
                p(x - cos(a) * r, y - sin(a) * r),
                p(x + cos(a) * r, y + sin(a) * r),
                strokeWidth = 0.9f * u,
                cap = StrokeCap.Round,
            )
        }
    }

    when (glifo) {
        GlifoMeteo.SOLE -> if (notte) luna(12f, 12f, 8f) else sole(12f, 12f, 6f)
        GlifoMeteo.POCO -> {
            if (notte) luna(9f, 8f, 5.5f) else sole(9f, 8f, 4.6f)
            nuvola(3.5f, 4f, 0.85f, carica = false)
        }
        GlifoMeteo.NUVOLE -> {
            nuvola(-1f, -1.5f, 0.7f, carica = true)
            nuvola(2f, 3.5f, 0.9f, carica = false)
        }
        // La nebbia: una nuvola chiara che si sfalda in tre strisce.
        GlifoMeteo.NEBBIA -> {
            nuvola(0f, -4.5f, 1f, carica = false)
            // Da sinistra a destra, sfalsate: (inizio, riga, fine).
            listOf(Triple(4f, 17.5f, 16f), Triple(7f, 20f, 20f), Triple(5f, 22.5f, 14f)).forEach { (da, y, a) ->
                drawLine(ColoriGlifo.bordoNuvola, p(da, y), p(a, y), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
            }
        }
        GlifoMeteo.PIOGGIA -> {
            nuvola(0f, -3.5f, 1f, carica = true)
            goccia(7f, 20f); goccia(12f, 21.5f); goccia(17f, 20f)
        }
        GlifoMeteo.TEMPORALE -> {
            nuvola(0f, -4f, 1f, carica = true)
            val punte = listOf(11f to 12f, 8f to 18f, 11f to 18f, 9.5f to 23f, 15f to 16f, 12f to 16f, 13.5f to 12f)
            val fulmine = Path().apply {
                punte.forEachIndexed { i, (x, y) ->
                    val q = p(x, y)
                    if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
                }
                close()
            }
            drawPath(fulmine, ColoriGlifo.sole)
            drawPath(fulmine, ColoriGlifo.bordoSole, style = Stroke(width = 0.7f * u, join = StrokeJoin.Round))
        }
        GlifoMeteo.NEVE -> {
            nuvola(0f, -3.5f, 1f, carica = false)
            fiocco(7f, 20f, 1.8f); fiocco(12f, 21.8f, 1.8f); fiocco(17f, 20f, 1.8f)
        }
    }
}

// ── L'intestazione ──────────────────────────────────────────────────────────

/**
 * La riga in cima: le impostazioni a sinistra, la citta' al centro, gli avvisi
 * a destra.
 *
 * **Al posto della fase del giorno ci sono gli avvisi.** Prima li' c'era scritto
 * "GIORNO" o "NOTTE", che e' un'informazione che chiunque ha gia' guardando
 * fuori dalla finestra; adesso c'e' l'unica cosa che l'app sa e chi guarda no.
 * Quando non c'e' niente da dire lo dice: "nessun avviso" con un pallino
 * salvia, perche' un silenzio non e' una risposta rassicurante finche' non si
 * sa che qualcuno ha guardato.
 */
@Composable
fun IntestazioneCaelum(
    citta: String,
    avvisi: List<WeatherAlert>,
    palette: SalaPalette,
    onImpostazioni: () -> Unit,
    onCitta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(MinTouchTarget)
                .clickable(onClick = onImpostazioni),
            contentAlignment = Alignment.CenterStart,
        ) {
            DueCursori(palette.ink)
        }
        Text(
            text = citta,
            style = SalaType.citta,
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clickable(onClick = onCitta),
        )
        PastigliaAvviso(avvisi, palette)
    }
}

/**
 * Il comando delle impostazioni: due cursori.
 *
 * Disegnato e non importato - il progetto non ha una libreria di icone, e in
 * un'app che disegna lune e nuvole a mano aprirne una per due segmenti e due
 * pallini sarebbe sproporzionato.
 */
@Composable
private fun DueCursori(ink: Color) {
    Canvas(modifier = Modifier.size(22.dp, 16.dp)) {
        val spessore = size.height * 0.155f
        val r = size.height * 0.22f
        fun barra(y: Float, cx: Float) {
            drawRoundRect(
                color = ink,
                topLeft = Offset(0f, y - spessore / 2f),
                size = Size(size.width, spessore),
                cornerRadius = CornerRadius(spessore / 2f),
            )
            drawCircle(color = ink, radius = r, center = Offset(cx, y))
        }
        barra(size.height * 0.28f, size.width * 0.34f)
        barra(size.height * 0.76f, size.width * 0.70f)
    }
}

/** La pastiglia degli avvisi: pallino piu' una riga sola, tutta maiuscola. */
@Composable
private fun PastigliaAvviso(avvisi: List<WeatherAlert>, palette: SalaPalette) {
    val peggiore = avvisi.maxByOrNull { it.level.weight }
    // **"Allerta" e' una parola che la spetta a un ente.** Un avviso calcolato
    // sulle soglie dei dati dice "avviso": e' la stessa regola per cui
    // `badgeLabel` non scrive mai "allerta gialla" su una soglia nostra.
    val testo = when {
        peggiore == null -> "NESSUN AVVISO"
        peggiore.official -> "ALLERTA ${peggiore.kind.label}"
        else -> "AVVISO ${peggiore.kind.label}"
    }
    val acceso = peggiore != null
    val fondo = if (acceso) {
        lerp(SalaTokens.accent200, SalaTokens.accent500.copy(alpha = 0.30f), palette.buio)
    } else {
        palette.chip
    }
    val inchiostro = if (acceso) {
        lerp(SalaTokens.accent700, SalaTokens.accent300, palette.buio)
    } else {
        palette.ink
    }
    val pallino = if (acceso) {
        SalaTokens.accent500
    } else {
        lerp(SalaTokens.verde600, SalaTokens.verde400, palette.buio)
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(fondo)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(pallino))
        Text(text = testo, style = SalaType.sectionLabel, color = inchiostro, maxLines = 1)
    }
}

// ── La colonna delle scorciatoie ────────────────────────────────────────────

/**
 * Le sette pastiglie sul fianco destro: una per sala, e ci si va senza scorrere.
 *
 * **Ha sostituito il menu a cassetto**, che era un posto in cui entrare per
 * scegliere dove andare - due gesti per una cosa sola. Qui la galleria intera
 * e' sempre in vista, e quella dov'e' si riconosce perche' e' l'unica in
 * terracotta.
 *
 * Le localita' non sono in colonna: stanno nelle impostazioni, perche' cambiare
 * citta' non e' spostarsi fra le sale, e una fila di sette icone piu' due
 * intruse non e' piu' una fila.
 *
 * ## Si tocca, e si trascina
 *
 * Toccare porta a una sala. **Tenere premuto e scorrere le attraversa tutte**,
 * con la sala che sta sotto il dito, senza staccarlo: la colonna smette di
 * essere sette bottoni e diventa un cursore, come lo e' gia' la barra delle ore
 * in fondo allo schermo. E' il gesto che chi usa l'app ha chiesto per primo, e
 * la ragione e' la stessa per cui esiste la barra delle ore: dalla settima sala
 * alla seconda, a tocchi, sono due comandi e due attese; trascinando e' un
 * gesto solo e si vede tutto quello che c'e' in mezzo mentre ci si passa.
 *
 * Il trascinamento non anima le pagine una per una - [onPorta] posa il
 * carosello dov'e' il dito, subito - e a ogni sala attraversata il telefono da'
 * un colpetto, gli stessi due della barra delle ore. Il tocco invece resta
 * animato: li' non c'e' un dito che segue, c'e' un salto da raccontare.
 *
 * **Il cartellino col nome compare solo trascinando**, accanto alla sala sotto
 * il dito. Sette glifi di sedici punti sono riconoscibili quando si sa gia' cosa
 * sono; la prima volta no, e chi trascina sta appunto cercando. Tenerlo fisso
 * sarebbe sette etichette perenni addosso al cielo, cioe' la cosa che questa
 * colonna e' nata per non essere.
 *
 * ## Da qui in poi e' anche l'unico indicatore di percorso
 *
 * Sotto il carosello c'erano **sette trattini orizzontali** che facevano
 * esattamente questo mestiere: dicevano dove sei e ci si saltava sopra. Due
 * comandi identici a due bordi diversi dello schermo, e chi li ha usati l'ha
 * detto subito. Peggio: erano **orizzontali**, e un indicatore orizzontale
 * promette che le schede si sfoglino di lato - il che oggi e' pure vero, ma
 * all'epoca non lo era, e un comando che mente sul verso del gesto e' peggio di
 * un comando assente.
 *
 * Sono spariti, e questa colonna ha preso le due cose che facevano meglio:
 *
 * - **segue il dito in continuo.** L'accento non scatta da un'icona all'altra
 *   al momento dell'aggancio: scorre, perche' [posizione] e' la stessa
 *   frazione di pagina che i trattini leggevano. Si legge dove si sta andando
 *   mentre ci si va.
 * - **c'e' un filo dietro.** Sette dischi sparsi sono sette bottoni; sette
 *   dischi su una linea sono un **asse**, e un asse verticale dice da se' che
 *   lungo quella linea ci si muove.
 *
 * ## Il disco cresce dove sei
 *
 * I sette dischi erano tutti larghi uguale e cambiavano solo colore. Sette
 * pastiglie piene da trentotto punti in fila sono una barra bianca addosso al
 * cielo, e la sala in cui sei si riconosceva **solo** dalla tinta: al sole, o
 * con un cielo terracotta dietro, quella differenza si assottiglia.
 *
 * Adesso il raggio scorre con la stessa [posizione] del colore - piccolo dove
 * non sei, pieno dove sei - e la colonna a riposo e' una fila di puntini con una
 * pastiglia sola. Pesa meno sul disegno, e dice dove sei anche in bianco e nero.
 * **Il bersaglio non si muove di un punto**: quarantotto per quarantotto,
 * sempre, perche' quello lo misura il polpastrello e non l'occhio (sezione
 * 15.3).
 *
 * @param posizione la pagina corrente con la sua frazione, letta **dentro il
 *   disegno** e non in composizione: cambia a ogni fotogramma del dito, e
 *   leggerla in composizione ricomporrebbe la colonna sessanta volte al
 *   secondo per travasare un colore.
 * @param onVai dove porta un tocco: con la molla, perche' e' un salto.
 * @param onPorta dove porta il dito che trascina: senza molla, perche' la molla
 *   e' il dito.
 * @param onTick il colpetto a ogni sala attraversata trascinando.
 */
@Composable
fun ColonnaScorciatoie(
    corrente: SalaRoom,
    posizione: () -> Float,
    palette: SalaPalette,
    onVai: (SalaRoom) -> Unit,
    modifier: Modifier = Modifier,
    onPorta: (SalaRoom) -> Unit = onVai,
    onTick: () -> Unit = {},
    /** Falso quando chi usa l'app ha chiesto meno movimento: il tocco cambia
     *  sala lo stesso, senza rimbalzo, senza onda e senza colpetti. */
    movimento: Boolean = true,
) {
    val sale = SalaRoom.entries
    // **Il rimbalzo del tocco, e l'onda che si lascia dietro.**
    //
    // Uno stato solo per sette icone, non sette: le animazioni non si
    // sovrappongono mai, perche' un dito tocca un posto alla volta. Chi tocca
    // la seconda mentre la prima sta ancora rimbalzando interrompe la prima -
    // ed e' quello che ci si aspetta, perche' l'attenzione si e' spostata.
    val colpita = remember { mutableStateOf<SalaRoom?>(null) }
    // Due molle e non una: il rimbalzo e' corto ed elastico, l'onda e' lunga e
    // in frenata. Legarle allo stesso numero vorrebbe dire che l'onda rimbalza
    // insieme al disco, che e' il contrario di cosa fa un'onda d'urto.
    val rimbalzo = remember { Animatable(1f) }
    val onda = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // La sala sotto il dito mentre trascina, oppure niente quando il dito non
    // c'e'. E' l'unico stato che il trascinamento tiene: dove sia posato il
    // carosello lo sa gia' il carosello, e una seconda verita' da tenere in
    // fase con la prima e' il difetto che si scrive da se'.
    val sottoIlDito = remember { mutableStateOf<SalaRoom?>(null) }

    val spento = lerp(
        SalaTokens.neutral100.copy(alpha = 0.72f),
        SalaTokens.neutral100.copy(alpha = 0.16f),
        palette.buio,
    )
    Box(
        modifier = modifier
            // **Il trascinamento sta sul contenitore, i tocchi sui dischi.**
            // Sono due gesti diversi e Compose li separa da se': il figlio
            // riceve per primo, e finche' il dito non ha superato la soglia di
            // slittamento resta un tocco; superata la soglia questo rilevatore
            // consuma il movimento e il tocco del figlio si annulla. Scritti
            // tutti e due qui, o tutti e due li', uno dei due avrebbe dovuto
            // indovinare le intenzioni dell'altro.
            //
            // Il conto e' una divisione perche' la colonna **non ha spazi**:
            // ogni bersaglio occupa esattamente [BERSAGLIO], quindi la sala e'
            // l'altezza divisa per il passo. Se un giorno si aggiungesse una
            // spaziatura, questa riga sarebbe la prima a mentire.
            // La chiave porta anche [movimento]: il blocco di un
            // `pointerInput` si ricorda com'era alla chiave, e senza di lei chi
            // spegne le animazioni mentre l'app e' aperta continuerebbe a
            // sentire i colpetti di un blocco scritto quando erano accese.
            .pointerInput(sale.size, movimento) {
                val passo = BERSAGLIO.toPx()
                fun salaSotto(y: Float): SalaRoom =
                    sale[(y / passo).toInt().coerceIn(0, sale.lastIndex)]

                detectVerticalDragGestures(
                    onDragStart = { punto ->
                        val sala = salaSotto(punto.y)
                        sottoIlDito.value = sala
                        onPorta(sala)
                        if (movimento) onTick()
                    },
                    onDragEnd = { sottoIlDito.value = null },
                    onDragCancel = { sottoIlDito.value = null },
                ) { cambio, _ ->
                    // Consumare e' cio' che dice al tocco del disco sotto che
                    // questo non e' piu' un tocco: senza, alzando il dito
                    // partirebbe anche il salto animato della sala d'arrivo,
                    // sopra il carosello che ci sta gia'.
                    cambio.consume()
                    val sala = salaSotto(cambio.position.y)
                    if (sala != sottoIlDito.value) {
                        sottoIlDito.value = sala
                        onPorta(sala)
                        if (movimento) onTick()
                    }
                }
            },
    ) {
        // Il filo. Va da centro a centro del primo e dell'ultimo bersaglio,
        // non da bordo a bordo: un asse che spunta sopra la prima icona e
        // sotto l'ultima sembrerebbe tagliato, non finito.
        Canvas(modifier = Modifier.matchParentSize()) {
            val mezzo = BERSAGLIO.toPx() / 2f
            drawLine(
                color = palette.maniglia,
                start = Offset(size.width / 2f, mezzo),
                end = Offset(size.width / 2f, size.height - mezzo),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Column(
            // Niente spazio fra le voci: lo fa il bersaglio, che e' piu' largo
            // del disco. Con `spacedBy` **e** un bersaglio da 48 i sette non ci
            // starebbero in altezza su un telefono corto. Ed e' anche il
            // presupposto del conto qui sopra, che divide per il solo passo.
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            sale.forEach { sala ->
                val attiva = sala == corrente
                // L'inchiostro scatta, il fondo no. Il fondo lo si puo'
                // interpolare nel disegno; l'inchiostro lo deve sapere
                // `IconaSala`, che e' un composable, e farglielo sapere a ogni
                // fotogramma costerebbe la ricomposizione che questa colonna
                // evita apposta. Una molla corta copre lo scatto.
                val inchiostro by animateColorAsState(
                    targetValue = if (attiva) palette.accentInk else palette.ink,
                    animationSpec = tween(220),
                    label = "inchiostro",
                )
                // **Il bersaglio e' piu' grande del disco, e non erano la
                // stessa cosa.** Prima lo erano: trentaquattro punti di disco,
                // trentaquattro di area sensibile, sei di distanza fra uno e
                // l'altro. Quaranta punti di passo, contro i quarantotto che
                // l'accessibilita' chiede come minimo - e col pollice, tenendo
                // il telefono con una mano sola, sull'orlo destro dello
                // schermo. Il difetto e' arrivato da chi l'app la usa cosi':
                // si sbagliava sala.
                //
                // Il disco cresce di quattro punti, il bersaglio di
                // quattordici, e **cresce verso l'interno** dello schermo
                // oltre che in altezza: il dito che arriva da destra trova
                // l'area prima del bordo, non dopo.
                val mia = colpita.value == sala
                Box(
                    modifier = Modifier
                        .size(BERSAGLIO)
                        .clickable(onClickLabel = sala.heading) {
                            onVai(sala)
                            if (movimento) {
                                colpita.value = sala
                                // Il disco scatta in fuori in un decimo di
                                // secondo e torna con una molla poco smorzata,
                                // cioe' oltrepassando e rientrando: e' quello
                                // che fa una cosa elastica, e una molla lo fa
                                // da se' - scriverlo a fotogrammi vorrebbe dire
                                // decidere a mano quanto oltrepassa.
                                scope.launch {
                                    rimbalzo.snapTo(1f)
                                    rimbalzo.animateTo(1.2f, tween(110))
                                    rimbalzo.animateTo(
                                        1f,
                                        spring(dampingRatio = 0.34f, stiffness = 520f),
                                    )
                                }
                                scope.launch {
                                    onda.snapTo(0f)
                                    onda.animateTo(1f, tween(520, easing = LinearOutSlowInEasing))
                                }
                            }
                        }
                        // **Sette dischi senza nome erano sette dischi senza
                        // nome anche per chi non li vede.** Le icone hanno la
                        // descrizione nulla - giusto, sono decorazioni dentro
                        // un comando - ma il comando un nome non ce l'aveva, e
                        // TalkBack leggeva sette "pulsante" in fila. Il nome
                        // della sala lo sa gia' l'enum, e `Tab` e' il ruolo
                        // giusto: sono pagine sorelle, non azioni.
                        .semantics {
                            contentDescription = sala.heading
                            role = Role.Tab
                            selected = attiva
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(DISCO)
                            // **La forma si legge nel livello, non in
                            // composizione.** La forma lunga di `graphicsLayer`
                            // - quella col blocco - rilegge i suoi valori
                            // quando il livello si aggiorna, non quando la
                            // funzione ricompone: il rimbalzo scorre senza
                            // rifare l'albero sessanta volte al secondo, che e'
                            // la stessa ragione per cui il fondo sta in
                            // `drawBehind`.
                            .graphicsLayer {
                                val s = if (mia) rimbalzo.value else 1f
                                scaleX = s
                                scaleY = s
                            }
                            .drawBehind {
                                // L'onda esce da sotto il disco e si allarga
                                // oltre il bersaglio. Non e' ritagliata da
                                // nessuno: `drawBehind` puo' disegnare fuori
                                // dai propri confini, ed e' quello che serve -
                                // un'onda che si ferma sul bordo del bottone
                                // che l'ha lanciata non e' un'onda.
                                if (mia && onda.value < 1f) {
                                    val p = onda.value
                                    drawCircle(
                                        color = SalaTokens.accent400.copy(alpha = (1f - p) * 0.5f),
                                        radius = size.minDimension / 2f + p * 24.dp.toPx(),
                                        style = Stroke(width = 2.dp.toPx()),
                                    )
                                }
                                // La stessa formula che avevano i trattini:
                                // uno quando la pagina e' questa, zero quando
                                // e' una qualsiasi delle altre, e in mezzo
                                // mentre il dito passa. Da qui in poi muove
                                // **due** cose, la tinta e il raggio, e sono
                                // lo stesso numero: se divergessero, il disco
                                // finirebbe di crescere prima o dopo di quando
                                // finisce di colorarsi, e si vedrebbe.
                                val vicinanza =
                                    (1f - abs(posizione() - sala.ordinal)).coerceIn(0f, 1f)
                                val pieno = size.minDimension / 2f
                                val riposo = RIPOSO.toPx()
                                drawCircle(
                                    color = lerp(spento, palette.accent, vicinanza),
                                    radius = riposo + (pieno - riposo) * vicinanza,
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        IconaSala(sala, inchiostro)
                    }
                }
            }
        }

        // ── Il cartellino del trascinamento ──────────────────────────────────
        //
        // Sta **fuori** dalla colonna, verso il centro dello schermo, e per
        // questo e' l'ultimo composto: in un `Box` l'ultimo sta sopra, e un
        // nome che passasse sotto i dischi sarebbe un nome illeggibile.
        //
        // Sborda dai confini del riquadro, che non ritaglia niente. Puo' farlo
        // perche' alla sua sinistra c'e' il cielo, e il carosello si ferma
        // quaranta punti prima del bordo destro (vedi la Shell): lo spazio in
        // cui sborda e' vuoto per costruzione.
        sottoIlDito.value?.let { sala ->
            // **`matchParentSize` e non un figlio qualunque**, e senza questo
            // riquadro in mezzo il cartellino sposterebbe la colonna. Un `Box`
            // si misura sul figlio piu' largo: il nome di una sala e' largo il
            // triplo di un bersaglio, quindi comparendo allargherebbe il
            // riquadro, e la colonna - che dentro sta in alto a sinistra -
            // scivolerebbe di settanta punti verso il centro dello schermo a
            // ogni trascinamento. I figli misurati sul genitore, invece, il
            // genitore non lo misurano.
            Box(modifier = Modifier.matchParentSize()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .height(BERSAGLIO)
                        .offset(x = -(BERSAGLIO - 3.dp), y = BERSAGLIO * sala.ordinal)
                        // Il riquadro qui sopra e' largo quanto la colonna,
                        // quarantotto punti, e dentro quei quarantotto "La
                        // settimana" starebbe come ci sta un piede in una
                        // scarpa di tre taglie in meno. `unbounded` lascia al
                        // cartellino la sua larghezza vera; l'allineamento a
                        // destra e' cio' che lo fa sbordare **verso il centro
                        // dello schermo** invece che oltre il bordo.
                        .wrapContentWidth(align = Alignment.End, unbounded = true),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sala.heading,
                        style = SalaType.pill,
                        color = palette.ink,
                        maxLines = 1,
                        // Fondo **solido** e non il pannello translucido: qui
                        // sotto puo' passarci il sole, una collina o una nuvola
                        // bianca, e un cartellino che cambia leggibilita' a
                        // seconda di cosa gli scorre dietro non e' un
                        // cartellino.
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.panelSolido)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

/** Il disco della sala in cui sei: la misura piena, quella che si vede. */
private val DISCO = 38.dp

/**
 * Il raggio dei dischi delle **altre** sale.
 *
 * Tredici punti di raggio - ventisei di diametro - contro i trentotto della
 * sala corrente. **Il limite in basso lo detta il glifo, non il gusto**: le
 * icone sono larghe da sedici a venti punti e restano larghe uguale a
 * qualunque raggio, perche' sono il modo in cui una sala si riconosce. Sotto i
 * ventisei il disco smetterebbe di contenerle e diventerebbe un bollino con
 * un'icona addosso.
 *
 * Fra i due estremi si scorre con la frazione di pagina, quindi questo valore
 * non si vede mai da solo: e' la fila a riposo, quando il carosello e' posato.
 */
private val RIPOSO = 13.dp

/**
 * L'area che risponde al dito, attorno al disco.
 *
 * Quarantotto punti e' il minimo che le linee guida di Android chiedono per un
 * comando, ed e' misurato sul polpastrello e non sull'icona. Sette bersagli da
 * quarantotto fanno 336 punti in colonna: ci stanno anche su uno schermo corto,
 * che e' il motivo per cui l'arrangiamento qui sopra non aggiunge spazio.
 */
private val BERSAGLIO = 48.dp

/**
 * Le sette icone della colonna, una famiglia sola: stesso tratto, stessa taglia.
 *
 * **Tutte disegnate qui, a tratto**, sul modello delle icone di sezione di
 * Google Meteo - linee di due unita' su ventiquattro, punte arrotondate - ma
 * nostre. Prima erano cinque sagome piene e due immagini (aria e vento) date a
 * parte: tre stili diversi in sette bottoni. Seguono l'inchiostro, come prima.
 */
@Composable
private fun IconaSala(sala: SalaRoom, ink: Color) {
    Canvas(modifier = Modifier.size(20.dp)) { disegnaIcona(sala, ink) }
}

internal fun DrawScope.disegnaIcona(sala: SalaRoom, ink: Color) {
    val u = size.minDimension / 24f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    val tratto = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun linea(x0: Float, y0: Float, x1: Float, y1: Float) =
        drawLine(ink, p(x0, y0), p(x1, y1), strokeWidth = 2f * u, cap = StrokeCap.Round)

    when (sala) {
        // Il sole: un disco e otto raggi staccati.
        SalaRoom.OGGI -> {
            drawCircle(ink, 4.2f * u, p(12f, 12f))
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f
                linea(12f + cos(a) * 7f, 12f + sin(a) * 7f, 12f + cos(a) * 9.6f, 12f + sin(a) * 9.6f)
            }
        }
        // Il calendario: la cornice, la testata, gli anelli, sei giorni.
        SalaRoom.SETTIMANA -> {
            drawRoundRect(ink, p(3.5f, 5f), Size(17f * u, 15.5f * u), CornerRadius(3f * u), style = tratto)
            linea(3.5f, 10f, 20.5f, 10f)
            linea(8f, 3f, 8f, 6.5f)
            linea(16f, 3f, 16f, 6.5f)
            for (x in listOf(8f, 12f, 16f)) for (y in listOf(14f, 17.3f)) drawCircle(ink, 1.1f * u, p(x, y))
        }
        // La nuvola a tratto, e tre righe di pioggia oblique.
        SalaRoom.PIOGGIA -> {
            val nuvola = Path().apply {
                arcTo(Rect(p(4f, 8.5f), p(11f, 15.5f)), 90f, 180f, true)
                arcTo(Rect(p(7.5f, 3f), p(17.5f, 13f)), 198f, 153f, false)
                arcTo(Rect(p(13.5f, 8.5f), p(20.5f, 15.5f)), -72.8f, 162.8f, false)
                close()
            }
            drawPath(nuvola, ink, style = tratto)
            for (x in listOf(8f, 12.5f, 17f)) linea(x, 17f, x - 1.2f, 20.5f)
        }
        // La falce, e una stellina accanto.
        SalaRoom.LUNA -> {
            val disco = Path().apply { addOval(Rect(p(3f, 5f), p(19f, 21f))) }
            val morso = Path().apply { addOval(Rect(p(8.6f, 2.4f), p(23f, 16.8f))) }
            drawPath(Path().apply { op(disco, morso, PathOperation.Difference) }, ink)
            val stella = Path().apply {
                moveTo(18.5f * u, 2.4f * u)
                for (k in 1..8) {
                    val a = -PI.toFloat() / 2f + k * PI.toFloat() / 4f
                    val r = if (k % 2 == 0) 2.6f else 2.6f * 0.38f
                    lineTo((18.5f + cos(a) * r) * u, (5f + sin(a) * r) * u)
                }
                close()
            }
            drawPath(stella, ink)
        }
        // L'aria: tre correnti, due col ricciolo, e due granelli sospesi.
        SalaRoom.ARIA -> {
            val correnti = Path().apply {
                moveTo(3f * u, 8f * u); lineTo(14f * u, 8f * u)
                cubicTo(17.5f * u, 8f * u, 17.5f * u, 3.5f * u, 14.5f * u, 3.8f * u)
                moveTo(3f * u, 12.5f * u); lineTo(19f * u, 12.5f * u)
                cubicTo(22.5f * u, 12.5f * u, 22.5f * u, 17f * u, 19.5f * u, 17f * u)
                moveTo(3f * u, 17f * u); lineTo(10f * u, 17f * u)
            }
            drawPath(correnti, ink, style = tratto)
            drawCircle(ink, 1.2f * u, p(14.5f, 17.2f))
            drawCircle(ink, 1.2f * u, p(7f, 21f))
        }
        // La manica a vento: il palo, il cono, due bande.
        SalaRoom.VENTO -> {
            linea(4f, 21f, 4f, 3.5f)
            val manica = Path().apply {
                moveTo(4f * u, 5f * u); lineTo(19f * u, 7.2f * u); lineTo(19f * u, 11.8f * u); lineTo(4f * u, 14f * u); close()
            }
            drawPath(manica, ink, style = tratto)
            linea(9f, 5.8f, 9f, 13.2f)
            linea(14f, 6.5f, 14f, 12.5f)
        }
        // I raggi UV: il sole che sale sull'orizzonte.
        SalaRoom.UV -> {
            linea(3f, 18f, 21f, 18f)
            val mezzo = Path().apply {
                moveTo(7f * u, 18f * u)
                arcTo(Rect(p(7f, 13f), p(17f, 23f)), 180f, 180f, false)
                close()
            }
            drawPath(mezzo, ink)
            for (k in 0 until 5) {
                val a = PI.toFloat() + (k + 0.5f) * PI.toFloat() / 5f
                linea(12f + cos(a) * 7.5f, 18f + sin(a) * 7.5f, 12f + cos(a) * 10f, 18f + sin(a) * 10f)
            }
        }
    }
}

/** La freccia dell'indietro delle schermate di servizio, dentro il suo tondo. */
@Composable
fun TondoIndietro(palette: SalaPalette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(palette.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(11.dp)) {
            val s = 2.4.dp.toPx()
            drawLine(
                color = palette.ink,
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height / 2f),
                strokeWidth = s,
            )
            drawLine(
                color = palette.ink,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height),
                strokeWidth = s,
            )
        }
    }
}

/** L'intestazione delle schermate di servizio: indietro e titolo. */
@Composable
fun IntestazioneServizio(
    titolo: String,
    palette: SalaPalette,
    onIndietro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TondoIndietro(palette = palette, onClick = onIndietro)
        Text(
            text = titolo,
            style = SalaType.citta,
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Le schermate di servizio ────────────────────────────────────────────────
//
// **Un riquadro per argomento, non uno per comando.** Le impostazioni erano
// quindici pastiglie tutte uguali, una sotto l'altra: il tema pesava quanto
// "Vento forte", e l'unico modo di capire dove finiva un argomento e ne
// cominciava un altro era leggerle tutte. Adesso l'etichetta sta **fuori** dal
// riquadro, come il titolo di un paragrafo, e dentro le voci sono righe
// separate da un filo: l'occhio trova prima il gruppo, poi la voce.

/** Il raggio dei riquadri di servizio. Meno del pannello: sono piu' fitti. */
private val RaggioGruppo = 26.dp

/**
 * Un gruppo di voci: titolo sopra, riquadro con le voci, e una nota sotto se
 * serve a dire **cosa non fa** il gruppo (gli avvisi calcolati non spengono le
 * allerte ufficiali, per esempio).
 */
@Composable
fun GruppoImpostazioni(
    titolo: String,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
    nota: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = titolo,
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RaggioGruppo))
                .background(palette.chip),
            content = content,
        )
        if (nota != null) {
            Text(
                text = nota,
                style = SalaType.rowNote,
                color = palette.inkFaint,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )
        }
    }
}

/** Il filo fra due voci dello stesso gruppo: rientrato, cosi' non taglia il riquadro. */
@Composable
fun FiloGruppo(palette: SalaPalette) {
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.maniglia.copy(alpha = palette.maniglia.alpha * 0.55f)),
    )
}

/**
 * Una voce dentro un gruppo: nome, nota sotto, un segno a destra ([coda]) e,
 * se serve, un comando a tutta larghezza sotto il nome ([sotto]).
 *
 * Non ha un fondo suo: il fondo e' quello del gruppo. Tutta la riga e' il
 * bersaglio del tocco, non solo il segno a destra.
 */
@Composable
fun VoceImpostazione(
    titolo: String,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
    nota: String? = null,
    /** Una nota che segnala un problema: si scrive in accento. */
    notaInRisalto: Boolean = false,
    onClick: (() -> Unit)? = null,
    coda: @Composable (RowScope.() -> Unit)? = null,
    sotto: @Composable (ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = titolo, style = SalaType.rowTitle, color = palette.ink)
                if (!nota.isNullOrEmpty()) {
                    Text(
                        text = nota,
                        style = SalaType.rowNote,
                        color = if (notaInRisalto) palette.accent else palette.inkFaint,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            coda?.invoke(this)
        }
        if (sotto != null) {
            Spacer(modifier = Modifier.height(12.dp))
            sotto()
        }
    }
}

/**
 * Una voce che si accende e si spegne: **tutta la riga** e' l'interruttore.
 *
 * Prima si poteva toccare solo il binario, cinquanta punti per trenta in fondo
 * a destra, mentre la riga intera - che sembrava un bottone - non faceva
 * niente.
 */
@Composable
fun VoceInterruttore(
    titolo: String,
    nota: String?,
    acceso: Boolean,
    palette: SalaPalette,
    onCambia: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = acceso, role = Role.Switch, onValueChange = onCambia)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = titolo, style = SalaType.rowTitle, color = palette.ink)
            if (!nota.isNullOrEmpty()) {
                Text(
                    text = nota,
                    style = SalaType.rowNote,
                    color = palette.inkFaint,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        InterruttoreSala(acceso = acceso, palette = palette)
    }
}

/** La freccetta di una voce che porta altrove. */
@Composable
fun FrecciaAvanti(palette: SalaPalette, testo: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (testo != null) {
            Text(
                text = testo,
                style = SalaType.rowNote,
                color = palette.inkFaint,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Text(text = "›", style = SalaType.value, color = palette.accent)
    }
}

/**
 * L'interruttore: binario e pallino, nelle tinte del tema.
 *
 * **Solo disegno**: il tocco lo prende la riga che lo contiene
 * ([VoceInterruttore]). Il pallino scorre invece di saltare, cosi' si vede
 * che cosa e' cambiato; e' un'animazione che parte al tocco e finisce, non
 * una che resta accesa.
 */
@Composable
fun InterruttoreSala(acceso: Boolean, palette: SalaPalette, modifier: Modifier = Modifier) {
    val corsa by animateDpAsState(
        targetValue = if (acceso) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "interruttore",
    )
    val binario by animateColorAsState(
        targetValue = if (acceso) palette.accent else palette.maniglia,
        label = "binario",
    )
    Box(
        modifier = modifier
            .size(48.dp, 28.dp)
            .clip(CircleShape)
            .background(binario),
    ) {
        Box(
            modifier = Modifier
                .padding(start = 3.dp + corsa, top = 3.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (acceso) palette.accentInk else SalaTokens.neutral100),
        )
    }
}

/** Il corpo delle voci di un selettore: piu' grande della nota, piu' piccolo del nome. */
private val StileSegmento = SalaType.rowNote.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

/**
 * Un selettore a segmenti: un binario solo, e dentro un cursore d'accento che
 * scivola sulla voce scelta.
 *
 * Erano pastiglie staccate, ognuna col suo fondo grigio: tre bottoni che
 * sembravano tre comandi diversi invece di tre risposte alla stessa domanda.
 * Un binario unico dice "se ne sceglie una", e il cursore che scorre dice
 * quale e' cambiata.
 */
@Composable
fun <T> SceltaSegmentata(
    voci: List<Pair<T, String>>,
    scelta: T,
    palette: SalaPalette,
    onScegli: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val indice = voci.indexOfFirst { it.first == scelta }.coerceAtLeast(0)
    val posizione by animateFloatAsState(
        targetValue = indice.toFloat(),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f),
        label = "segmento",
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(palette.maniglia)
            .padding(3.dp),
    ) {
        val larga = maxWidth / voci.size
        Box(
            modifier = Modifier
                .offset(x = larga * posizione)
                .width(larga)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(palette.accent),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            voci.forEachIndexed { i, (valore, nome) ->
                val attiva = i == indice
                val colore by animateColorAsState(
                    targetValue = if (attiva) palette.accentInk else palette.ink,
                    label = "segmento-testo",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(selected = attiva, role = Role.RadioButton) { onScegli(valore) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = nome,
                        style = StileSegmento,
                        color = colore,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

/** Il riquadro di una sezione delle impostazioni: etichetta e contenuto. */
@Composable
fun BloccoImpostazioni(
    etichetta: String,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(palette.chip)
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Text(text = etichetta, style = SalaType.sectionLabel, color = palette.inkFaint)
        Spacer(modifier = Modifier.height(11.dp))
        content()
    }
}
