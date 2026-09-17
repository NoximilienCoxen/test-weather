package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.R
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.common.MinTouchTarget

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
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RaggioPannello))
            .background(palette.panel)
            // **Il pannello puo' salire, e finora non lo faceva.** E'
            // ancorato in basso e si dimensiona sul contenuto: quando il
            // contenuto e' stretto, il cielo sopra resta inutilizzato e le sale
            // ricche - la settimana su tutte - si stringono per stare in una
            // meta' schermo che nessuno aveva chiesto. Quattro punti in piu'
            // per lato non sono decorazione: sono il pannello che si prende lo
            // spazio che c'e', invece di comprimersi sotto di esso.
            .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 22.dp),
    ) {
        // La maniglia: dice che il pannello e' una cosa che sta sopra un'altra.
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 14.dp)
                .size(52.dp, 5.dp)
                .clip(CircleShape)
                .background(palette.maniglia),
        )
        content()
    }
}

/** Una cella: etichetta in maiuscoletto, valore sotto. Tre per riga, di solito. */
@Composable
fun RowScope.CellaValore(
    etichetta: String,
    valore: String,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .weight(1f)
            .clip(RoundedCornerShape(RaggioCella))
            .background(palette.chip)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = etichetta, style = SalaType.sectionLabel, color = palette.inkFaint, maxLines = 1)
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
enum class GlifoMeteo { SOLE, POCO, NUVOLE, PIOGGIA, TEMPORALE, GRANDINE, NEVE }

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
        weatherCode == 77 || weatherCode == 85 || weatherCode == 86 -> GlifoMeteo.GRANDINE
        weatherCode == 96 || weatherCode == 99 -> GlifoMeteo.TEMPORALE
        famiglia == Wmo.Family.PIOGGIA -> GlifoMeteo.PIOGGIA
        famiglia == Wmo.Family.NEBBIA -> GlifoMeteo.NUVOLE
        famiglia == Wmo.Family.NUVOLOSO -> if ((cloudCover ?: 60) < 62) GlifoMeteo.POCO else GlifoMeteo.NUVOLE
        else -> if ((cloudCover ?: 0) < 25) GlifoMeteo.SOLE else GlifoMeteo.POCO
    }
}

/**
 * La figuretta del tempo di un giorno: sole, sole con nuvola, nuvola, gocce,
 * fulmine, chicchi, fiocchi.
 *
 * **Era un pallino colorato**, e sette pallini di sette tinte non dicono che
 * tempo fa: dicono che i giorni sono diversi fra loro. Neve e grandine si
 * distinguono **per forma** oltre che per tinta - cinque fiocchi tondi su due
 * file contro due chicchi ovali - perche' a questa taglia il colore da solo non
 * basta.
 */
@Composable
fun IconaMeteo(glifo: GlifoMeteo, palette: SalaPalette, modifier: Modifier = Modifier) {
    val buio = palette.buio
    // Le nuvole si scuriscono col tema chiaro: erano bianche, e su un pannello
    // chiaro "poco coperto" e "sereno" erano la stessa figura.
    val nuvola = when (glifo) {
        GlifoMeteo.TEMPORALE -> Color(0xFF8F8F8C)
        GlifoMeteo.POCO -> lerp(Color(0xFFC2BEB4), SalaTokens.neutral100.copy(alpha = 0.75f), buio)
        else -> lerp(Color(0xFFA7A49C), SalaTokens.neutral100.copy(alpha = 0.60f), buio)
    }
    val fiocco = lerp(Color(0xFF9FB3BD), SalaTokens.neutral100, buio)
    val chicco = lerp(SalaTokens.ghiaccioScuro, Color(0xFF7FB6D4), buio)
    Canvas(modifier = modifier.size(20.dp, 18.dp)) {
        val u = size.width / 20f
        fun x(v: Float) = v * u
        // Il prototipo misura dal basso: qui si converte una volta sola.
        fun yDalBasso(v: Float, altezza: Float) = size.height - (v + altezza) * u

        if (glifo == GlifoMeteo.SOLE || glifo == GlifoMeteo.POCO) {
            val d = if (glifo == GlifoMeteo.SOLE) 15f else 10f
            val l = if (glifo == GlifoMeteo.SOLE) 2f else 0f
            drawCircle(
                color = SalaTokens.accent400,
                radius = x(d) / 2f,
                center = Offset(x(l) + x(d) / 2f, x(d) / 2f),
            )
        }
        if (glifo != GlifoMeteo.SOLE) {
            drawRoundRect(
                color = nuvola,
                topLeft = Offset(x(1f), yDalBasso(4f, 9f)),
                size = Size(x(18f), x(9f)),
                cornerRadius = CornerRadius(x(4.5f)),
            )
            drawOval(
                color = nuvola,
                topLeft = Offset(x(7f), yDalBasso(7f, 8f)),
                size = Size(x(11f), x(8f)),
            )
        }
        when (glifo) {
            GlifoMeteo.PIOGGIA, GlifoMeteo.TEMPORALE -> {
                listOf(4f, 12f).forEach { gx ->
                    drawRoundRect(
                        color = SalaTokens.acqua,
                        topLeft = Offset(x(gx), yDalBasso(0f, 5f)),
                        size = Size(x(2.5f), x(5f)),
                        cornerRadius = CornerRadius(x(1.25f)),
                    )
                }
                if (glifo == GlifoMeteo.TEMPORALE) {
                    rotate(degrees = 18f, pivot = Offset(x(9.5f), yDalBasso(0f, 8f) + x(4f))) {
                        drawRoundRect(
                            color = SalaTokens.accent400,
                            topLeft = Offset(x(8f), yDalBasso(0f, 8f)),
                            size = Size(x(3f), x(8f)),
                            cornerRadius = CornerRadius(x(1f)),
                        )
                    }
                }
            }
            GlifoMeteo.NEVE -> {
                // Cinque fiocchi tondi su due file: la forma, prima della tinta.
                listOf(
                    3f to 0f, 9f to 0f, 15f to 0f,
                    6f to 5f, 12f to 5f,
                ).forEach { (fx, fy) ->
                    drawCircle(color = fiocco, radius = x(1.5f), center = Offset(x(fx), yDalBasso(fy, 3f)))
                }
            }
            GlifoMeteo.GRANDINE -> {
                // Due chicchi ovali: piu' pesanti e piu' pochi dei fiocchi.
                listOf(5f, 12f).forEach { cx ->
                    drawOval(
                        color = chicco,
                        topLeft = Offset(x(cx), yDalBasso(0f, 6f)),
                        size = Size(x(4f), x(6f)),
                    )
                }
            }
            else -> Unit
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
 */
@Composable
fun ColonnaScorciatoie(
    corrente: SalaRoom,
    palette: SalaPalette,
    onVai: (SalaRoom) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        // Niente spazio fra le voci: lo fa il bersaglio, che e' piu' largo del
        // disco. Con `spacedBy` **e** un bersaglio da 48 i sette non ci
        // starebbero in altezza su un telefono corto.
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SalaRoom.entries.forEach { sala ->
            val attiva = sala == corrente
            val fondo = if (attiva) {
                palette.accent
            } else {
                lerp(
                    SalaTokens.neutral100.copy(alpha = 0.72f),
                    SalaTokens.neutral100.copy(alpha = 0.16f),
                    palette.buio,
                )
            }
            val inchiostro = if (attiva) palette.accentInk else palette.ink
            // **Il bersaglio e' piu' grande del disco, e non erano la stessa
            // cosa.** Prima lo erano: trentaquattro punti di disco,
            // trentaquattro di area sensibile, sei di distanza fra uno e
            // l'altro. Quaranta punti di passo, contro i quarantotto che
            // l'accessibilita' chiede come minimo - e col pollice, tenendo il
            // telefono con una mano sola, sull'orlo destro dello schermo. Il
            // difetto e' arrivato da chi l'app la usa cosi': si sbagliava sala.
            //
            // Il disco cresce di quattro punti, il bersaglio di quattordici, e
            // **cresce verso l'interno** dello schermo oltre che in altezza:
            // il dito che arriva da destra trova l'area prima del bordo, non
            // dopo.
            Box(
                modifier = Modifier
                    .size(BERSAGLIO)
                    .clickable { onVai(sala) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(DISCO)
                        .clip(CircleShape)
                        .background(fondo),
                    contentAlignment = Alignment.Center,
                ) {
                    IconaSala(sala, inchiostro)
                }
            }
        }
    }
}

/** Il disco che si vede. */
private val DISCO = 38.dp

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
 * Le sette icone della colonna, una famiglia sola: stesso peso, stessa taglia.
 *
 * Cinque sono disegnate qui; due - l'aria e il vento - sono le immagini che
 * l'utente ha dato, usate come maschera e tinte dal tema, cosi' seguono
 * l'inchiostro invece di restare nere su fondo scuro.
 */
@Composable
private fun IconaSala(sala: SalaRoom, ink: Color) {
    when (sala) {
        SalaRoom.ARIA -> Icon(
            painter = painterResource(R.drawable.ic_aria),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(19.dp),
        )
        SalaRoom.VENTO -> Icon(
            painter = painterResource(R.drawable.ic_vento),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(20.dp),
        )
        else -> Canvas(modifier = Modifier.size(16.dp)) { disegnaIcona(sala, ink) }
    }
}

private fun DrawScope.disegnaIcona(sala: SalaRoom, ink: Color) {
    val w = size.width
    val spessore = w * 0.155f
    when (sala) {
        SalaRoom.OGGI -> {
            drawCircle(color = ink, radius = w * 0.28f, center = Offset(w / 2f, w / 2f))
            val lungo = w * 0.22f
            val corto = w * 0.155f
            // Quattro raggi ai poli: con otto, a sedici punti, diventa una macchia.
            drawRoundRect(
                color = ink,
                topLeft = Offset(w / 2f - corto / 2f, 0f),
                size = Size(corto, lungo),
                cornerRadius = CornerRadius(corto / 2f),
            )
            drawRoundRect(
                color = ink,
                topLeft = Offset(w / 2f - corto / 2f, w - lungo),
                size = Size(corto, lungo),
                cornerRadius = CornerRadius(corto / 2f),
            )
            drawRoundRect(
                color = ink,
                topLeft = Offset(0f, w / 2f - corto / 2f),
                size = Size(lungo, corto),
                cornerRadius = CornerRadius(corto / 2f),
            )
            drawRoundRect(
                color = ink,
                topLeft = Offset(w - lungo, w / 2f - corto / 2f),
                size = Size(lungo, corto),
                cornerRadius = CornerRadius(corto / 2f),
            )
        }
        SalaRoom.SETTIMANA -> {
            drawRoundRect(
                color = ink,
                topLeft = Offset(0f, w * 0.06f),
                size = Size(w, w * 0.88f),
                cornerRadius = CornerRadius(w * 0.20f),
                style = Stroke(width = spessore),
            )
            drawRect(
                color = ink,
                topLeft = Offset(0f, w * 0.06f),
                size = Size(w, w * 0.20f),
            )
        }
        SalaRoom.PIOGGIA -> {
            // La goccia: un tondo con una punta in alto, non un cerchio ruotato
            // - a sedici punti la differenza fra i due si vede.
            val goccia = Path().apply {
                moveTo(w / 2f, w * 0.08f)
                cubicTo(w * 0.86f, w * 0.44f, w * 0.84f, w * 0.92f, w / 2f, w * 0.92f)
                cubicTo(w * 0.16f, w * 0.92f, w * 0.14f, w * 0.44f, w / 2f, w * 0.08f)
                close()
            }
            drawPath(path = goccia, color = ink)
        }
        SalaRoom.LUNA -> {
            // Una falce, ottenuta togliendo un disco spostato: la stessa idea
            // del terminatore, alla taglia di un'icona.
            val falce = Path().apply {
                addOval(Rect(0f, 0f, w, w))
            }
            val morso = Path().apply {
                addOval(Rect(w * 0.32f, -w * 0.10f, w * 1.22f, w * 1.02f))
            }
            drawPath(
                path = Path().apply {
                    op(falce, morso, PathOperation.Difference)
                },
                color = ink,
            )
        }
        SalaRoom.UV -> {
            // L'indice: un anello con il fondo pieno, cioe' "quanto ne arriva".
            drawCircle(
                color = ink,
                radius = w / 2f - spessore / 2f,
                center = Offset(w / 2f, w / 2f),
                style = Stroke(width = spessore),
            )
            drawArc(
                color = ink,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(spessore, spessore),
                size = Size(w - spessore * 2f, w - spessore * 2f),
            )
        }
        else -> Unit
    }
}

/**
 * L'indicatore di percorso: sette trattini, uno per sala.
 *
 * Legge la posizione del carosello **dentro il disegno**: cambia a ogni
 * fotogramma del dito, e letta in composizione ricomporrebbe l'intera schermata
 * per travasare un colore.
 */
@Composable
fun PuntiSala(
    posizione: () -> Float,
    palette: SalaPalette,
    onVai: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quante = SalaRoom.entries.size
    Row(
        modifier = modifier.fillMaxWidth().height(MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(quante) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(MinTouchTarget)
                    .clickable { onVai(i) },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                    val vicinanza = (1f - kotlin.math.abs(posizione() - i)).coerceIn(0f, 1f)
                    drawRoundRect(
                        color = lerp(palette.maniglia, palette.accent, vicinanza),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
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

/** Una riga di servizio: nome, nota sotto, e un segno a destra. */
@Composable
fun RigaServizio(
    titolo: String,
    nota: String,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    coda: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(palette.chip)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = titolo, style = SalaType.rowTitle, color = palette.ink)
            if (nota.isNotEmpty()) {
                Text(
                    text = nota,
                    style = SalaType.rowNote,
                    color = palette.inkFaint,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        coda?.invoke()
    }
}

/** L'interruttore delle impostazioni: binario e pallino, nelle tinte del tema. */
@Composable
fun InterruttoreSala(acceso: Boolean, palette: SalaPalette, onCambia: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp, 30.dp)
            .clip(CircleShape)
            .background(if (acceso) palette.accent else palette.maniglia)
            .clickable(onClick = onCambia),
    ) {
        Box(
            modifier = Modifier
                .padding(start = if (acceso) 25.dp else 3.dp, top = 3.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (acceso) palette.accentInk else SalaTokens.neutral100),
        )
    }
}

/** Un gruppo di pastiglie fra cui se ne sceglie una: tema, unita', didascalie. */
@Composable
fun <T> SceltaPastiglie(
    voci: List<Pair<T, String>>,
    scelta: T,
    palette: SalaPalette,
    onScegli: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        voci.forEach { (valore, nome) ->
            val attiva = valore == scelta
            Text(
                text = nome,
                style = SalaType.rowNote,
                color = if (attiva) palette.accentInk else palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (attiva) palette.accent else palette.maniglia)
                    .clickable { onScegli(valore) }
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
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
