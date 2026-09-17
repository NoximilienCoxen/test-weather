package io.github.noximiliencoxen.caelum.ui.sala.rooms

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.COSTE_ITALIA
import io.github.noximiliencoxen.caelum.data.COSTE_VICINE
import io.github.noximiliencoxen.caelum.data.MappaPrevista
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.RadarRiquadro
import io.github.noximiliencoxen.caelum.data.RadarTessere
import io.github.noximiliencoxen.caelum.data.StatoRadar
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.cos

/**
 * Il radar dentro "La pioggia".
 *
 * Non e' una sala nuova ed e' una scelta: il radar risponde alla **stessa**
 * domanda delle colonne qui sopra - piove, e quanto - solo per un'altra via.
 * Le colonne dicono *quando*, la mappa dice *dove*, e leggerle una accanto
 * all'altra vale piu' che scorrere fra due schermate.
 *
 * ## Cosa si vede anche quando il radar non c'e'
 *
 * La carta si disegna **sempre**: costa, confine, e il puntino del posto
 * scelto. Se il fotogramma non arriva - servizio giu', fuori copertura,
 * risposta che non si riconosce - resta una carta muta con una riga che dice
 * perche'. Una schermata che sparisce quando il dato manca lascia chi guarda
 * senza sapere se e' l'app a essere rotta o il cielo a essere sereno.
 *
 * ## La proiezione
 *
 * Mercatore, come le tessere che ci si posano sopra e come ogni mappa a
 * tessere del web. Fino a ieri era equirettangolare, con un commento proprio
 * qui che diceva che sarebbe stato questo il punto da cambiare se fosse
 * arrivata una fonte a tessere. E' arrivata, ed era quello il punto.
 *
 * Costa e pioggia usano **la stessa** proiezione, il che vuol dire che se una
 * macchia non segue il profilo della penisola non e' colpa della proiezione:
 * e' colpa dei dati, ed e' una cosa che si puo' riportare.
 */
@Composable
fun MappaRadar(
    stato: StatoRadar,
    place: Place,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    val prodotto = (stato as? StatoRadar.Pronto)?.prodotto
    // Due rettangoli, e non vanno confusi. Il **dominio** e' quello che il
    // Ogni tessera porta i propri estremi, quindi non c'e' piu' un "dominio"
    // solo: la **finestra** e' quello che si guarda, si calcola nel disegno
    // perche' dipende da quanto e' larga la carta sullo schermo, e ogni
    // tessera si posa dove dicono i suoi.
    // La figura del campo previsto si compone una volta per stato e non a ogni
    // fotogramma: sono centodiciassette pixel, ma il puntino batte e il
    // `Canvas` si ridisegna sessanta volte al secondo.
    val campo: Pair<ImageBitmap, MappaPrevista>? = remember(stato) {
        (stato as? StatoRadar.Previsto)?.let { p ->
            campoPrevisto(p.mappa, p.valori)?.let { it to p.mappa }
        }
    }

    val tessere: List<Pair<ImageBitmap, RadarRiquadro>> = remember(prodotto) {
        prodotto?.tessere.orEmpty().mapNotNull { t ->
            runCatching {
                BitmapFactory.decodeByteArray(t.png, 0, t.png.size).asImageBitmap() to t.riquadro
            }.getOrNull()
        }
    }

    // Il puntino respira. E' l'unico movimento della carta, ed e' l'unico
    // onesto: un radar composito non ha una spazzata che gira, e disegnarla
    // sarebbe un'animazione che racconta una cosa che non succede.
    val battito = rememberInfiniteTransition(label = "battito")
    val fase by battito.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "fase",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // **L'intestazione cambia col contenuto.** Diceva "IL RADAR" anche
            // quando sotto c'era una previsione del modello, e un titolo che
            // smentisce cio' che sta sotto e' l'ultima cosa che dovrebbe fare
            // un titolo. Chi legge "radar" crede a una misura.
            Text(
                text = if (stato is StatoRadar.Previsto) "LA PIOGGIA PREVISTA" else "IL RADAR",
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
            )
            Text(
                text = when (stato) {
                    is StatoRadar.Pronto -> ORARIO.format(stato.prodotto.istante.atZone(ZoneId.systemDefault()))
                    StatoRadar.InCorso -> "in arrivo"
                    // L'ora, non la parola "previsione": quella la dice gia'
                    // il titolo a sinistra, e qui serve sapere **quale** ora si
                    // sta guardando.
                    is StatoRadar.Previsto ->
                        ORARIO.format(stato.quando.atZone(ZoneId.systemDefault()))
                    StatoRadar.FuoriCopertura -> "nessun radar"
                    is StatoRadar.FuoriOrario -> "niente per quest'ora"
                    is StatoRadar.NonDisponibile -> "non disponibile"
                },
                style = SalaType.rowNote,
                // L'accento solo per il misurato. La previsione resta in
                // inchiostro tenue: il colore forte e' un'affermazione, e qui
                // c'e' meno da affermare.
                color = if (prodotto != null) palette.accent else palette.inkFaint,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ALTEZZA_CARTA)
                .clip(RoundedCornerShape(18.dp)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val finestra = finestraAttorno(place, size.width / size.height)
                drawRect(mare(palette))
                // Le tessere prima delle coste: la pioggia sta sopra il mare e
                // sotto il profilo della terra, cosi' il bordo della costa
                // resta leggibile anche dove la macchia e' fitta.
                tessere.forEach { (immagine, suo) -> posaRadar(immagine, suo, finestra) }
                campo?.let { (figura, mappa) -> posaPrevisione(figura, mappa, finestra) }
                disegnaCoste(finestra, palette)
                disegnaAnelli(place, finestra, palette)
                segnaPosto(place, finestra, palette, fase)
            }
        }

        // L'attribuzione non e' una nota di cortesia: i dati del radar sono in
        // CC BY-SA 4.0, e la licenza chiede che la fonte sia scritta dove il
        // dato si vede. Sta sotto la mappa e non nelle impostazioni per questo.
        Text(
            text = when (stato) {
                is StatoRadar.NonDisponibile ->
                    stato.indizio?.let { "Il radar non ha risposto come atteso: $it" }
                        ?: "Il radar non ha risposto."
                StatoRadar.InCorso -> "Si sta chiedendo il fotogramma di quest'ora."
                // **Nessun radar, che non e' "non piove".** Si accende solo
                // quando la maschera di copertura dice che li' non guarda
                // nessuno: prima questa frase stava sempre sotto la carta, e
                // un avviso che compare sempre non e' un avviso, e' una
                // cornice.
                StatoRadar.FuoriCopertura ->
                    "Su ${place.name} non guarda nessun radar: la carta resta vuota, " +
                        "e non vuol dire che non piove."
                // **La frase piu' importante di questa schermata.** Un radar
                // misura, e quello che non ha misurato non lo sa: due ore di
                // storico contro una barra che ne offre ventiquattro. Prima
                // qui compariva il fotogramma piu' recente sotto qualunque
                // ora, e sembrava una fotografia appesa.
                is StatoRadar.FuoriOrario -> stato.ultimo?.let {
                    "Il radar misura, non prevede: l'ultima fotografia e' delle " +
                        "${ORARIO.format(it.atZone(ZoneId.systemDefault()))}."
                } ?: "Il radar misura, non prevede: per quest'ora non c'e' una fotografia."
                // **La riga che tiene separate le due carte.** Il radar dice
                // cosa sta cadendo; questa dice cosa un modello si aspetta.
                // Chi guarda una carta radar le crede, e crederebbe a una
                // previsione a sedici ore come se qualcuno l'avesse vista.
                is StatoRadar.Previsto ->
                    "Previsione del modello, non radar: la pioggia misurata c'è " +
                        "solo per le ultime due ore."
                // Il nome della fonte lo porta il fotogramma, non lo sa
                // questa schermata: scritto qui, resterebbe quello di prima il
                // giorno in cui la fonte cambia.
                is StatoRadar.Pronto -> stato.prodotto.attribuzione
            },
            style = SalaType.rowNote,
            color = palette.inkFaint,
            textAlign = TextAlign.Start,
            // Tre righe e non una di piu'. L'indizio di un guasto e' utile a
            // chi lo riporta, ma non vale mezza schermata: nello scatto della
            // CI la pagina d'errore del DPC ne occupava dieci e spingeva le
            // dodici colonne fuori dalla vista.
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

private val ORARIO: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Quanto alta e' la carta. Il resto della finestra si ricava da qui. */
private val ALTEZZA_CARTA = 176.dp

/**
 * Mezza altezza della finestra, in gradi di latitudine.
 *
 * Un grado e mezzo sono centosessanta chilometri e mezzo da qui al bordo: la
 * distanza a cui un temporale che si vede sulla carta e' ancora un temporale
 * che ti riguarda. Sull'Italia intera - milleduecento chilometri da Bolzano a
 * Ragusa - la stessa macchia sarebbe larga tre pixel, e la carta risponderebbe
 * a una domanda che nessuno fa.
 */
private const val RAGGIO_GRADI = 1.45

/** Un grado di latitudine, in chilometri. Non cambia con la latitudine. */
private const val KM_PER_GRADO = 111.2

/**
 * La finestra: centrata su di te, larga quanto serve.
 *
 * Si parte dal posto scelto e si apre di [RAGGIO_GRADI] sopra e sotto; la
 * larghezza esce dalle proporzioni della carta sullo schermo, **divisa** per il
 * coseno della latitudine - un grado di longitudine a Forli' e' lungo tre
 * quarti di uno di latitudine, e senza quella divisione l'Italia verrebbe
 * grassa.
 *
 * **Non c'e' piu' un dominio da cui non uscire.** Col radar italiano la carta
 * doveva stare dentro un rettangolo, e quando il posto era vicino al bordo la
 * finestra traslava per non mostrare mezza mappa vuota. Un radar a tessere non
 * ha bordi: le tessere si chiedono dove serve, e se in una zona nessuno guarda
 * si vedra' una carta senza pioggia - che e' esattamente il limite dichiarato
 * nella riga sotto.
 */
private fun finestraAttorno(place: Place, proporzione: Float): RadarRiquadro {
    val mezzaLat = RAGGIO_GRADI
    val mezzaLon = mezzaLat * proporzione / cos(Math.toRadians(place.latitude)).coerceAtLeast(0.2)
    return RadarRiquadro(
        latMin = place.latitude - mezzaLat,
        lonMin = place.longitude - mezzaLon,
        latMax = place.latitude + mezzaLat,
        lonMax = place.longitude + mezzaLon,
    )
}

/**
 * Dove cade una coordinata dentro il rettangolo disegnato.
 *
 * **In Mercatore, e non era cosi' fino a ieri.** La longitudine e' lineare in
 * tutte e due le proiezioni; la latitudine no, e il commento in cima a questo
 * file avvisava che il giorno in cui fosse arrivata una fonte a tessere sarebbe
 * stato **questo** il punto da cambiare. Le tessere di RainViewer - come quelle
 * di chiunque serva tessere - sono disegnate in Mercatore: posarle su una carta
 * lineare in latitudine le stirerebbe, e di piu' ai bordi.
 *
 * Su tre gradi di finestra la differenza e' di pochi pixel, e nessuno se ne
 * accorgerebbe. Si fa lo stesso, perche' "pochi pixel" e' una proprieta' di
 * **questa** finestra: il giorno in cui qualcuno la allarga o la porta a una
 * latitudine alta, un errore che nessuno ha scritto da nessuna parte diventa
 * visibile e non si sa piu' da dove venga.
 */
private fun DrawScope.punto(lat: Double, lon: Double, f: RadarRiquadro): Offset {
    val giu = RadarTessere.mercatore(f.latMin)
    val su = RadarTessere.mercatore(f.latMax)
    val q = (RadarTessere.mercatore(lat) - giu) / (su - giu)
    return Offset(
        x = ((lon - f.lonMin) / (f.lonMax - f.lonMin)).toFloat() * size.width,
        y = (1f - q.toFloat()) * size.height,
    )
}

private fun mare(palette: SalaPalette): Color =
    if (palette.dark) SalaTokens.ghiaccioScuro.copy(alpha = 0.32f)
    else SalaTokens.ghiaccioChiaro.copy(alpha = 0.75f)

/**
 * Una tessera posata dove dicono i **suoi** estremi.
 *
 * I due riquadri adesso sono davvero due: la tessera copre un pezzo di mondo
 * fisso, deciso dalla griglia del livello di zoom, e la finestra e' quello che
 * si guarda. Quasi sempre la tessera sborda, e il `clipRect` la ferma al bordo
 * della carta.
 *
 * Il punto in piu' sulla taglia non e' una svista: le tessere si toccano, e
 * arrotondando ognuna per conto suo fra l'una e l'altra resterebbe una riga di
 * mare larga un pixel. E' la stessa mezza sormonta che tiene insieme le
 * ventiquattro tessere della barra delle ore.
 */
private fun DrawScope.posaRadar(
    immagine: ImageBitmap,
    suo: RadarRiquadro,
    finestra: RadarRiquadro,
) {
    val alto = punto(suo.latMax, suo.lonMin, finestra)
    val basso = punto(suo.latMin, suo.lonMax, finestra)
    val larghezza = (basso.x - alto.x) + 1f
    val altezza = (basso.y - alto.y) + 1f
    if (larghezza <= 1f || altezza <= 1f) return
    // Fuori dalla carta non si disegna: il ritaglio la butterebbe via
    // comunque, ma non prima di aver chiesto alla GPU di scalarla.
    if (basso.x < 0f || alto.x > size.width || basso.y < 0f || alto.y > size.height) return
    clipRect {
        drawImage(
            image = immagine,
            dstOffset = IntOffset(alto.x.toInt(), alto.y.toInt()),
            dstSize = IntSize(larghezza.toInt(), altezza.toInt()),
            alpha = 0.92f,
        )
    }
}

/**
 * Costa e confine.
 *
 * L'Italia e' un anello chiuso e si riempie: e' la macchia di terra a rendere
 * leggibile la macchia di pioggia, perche' il colore del radar si legge
 * **contro** qualcosa. I vicini sono tratti aperti, tagliati al bordo della
 * finestra, e si disegnano solo di linea: riempirli vorrebbe dire chiudere a
 * caso un poligono che non si chiude.
 */
private fun DrawScope.disegnaCoste(finestra: RadarRiquadro, palette: SalaPalette) {
    val terra = if (palette.dark) SalaTokens.verde900.copy(alpha = 0.55f)
    else SalaTokens.verde200.copy(alpha = 0.60f)
    val bordo = if (palette.dark) SalaTokens.neutral500.copy(alpha = 0.75f)
    else SalaTokens.neutral600.copy(alpha = 0.65f)
    val tenue = bordo.copy(alpha = 0.30f)

    fun percorso(dati: FloatArray, chiudi: Boolean): Path? {
        if (dati.size < 6) return null
        val p = Path()
        var i = 0
        while (i + 1 < dati.size) {
            val o = punto(dati[i + 1].toDouble(), dati[i].toDouble(), finestra)
            if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
            i += 2
        }
        if (chiudi) p.close()
        return p
    }

    clipRect {
        COSTE_VICINE.forEach { t ->
            percorso(t, chiudi = false)?.let { drawPath(it, tenue, style = Stroke(width = 1.1f)) }
        }
        COSTE_ITALIA.forEach { t ->
            percorso(t, chiudi = true)?.let {
                drawPath(it, terra)
                drawPath(it, bordo, style = Stroke(width = 1.4f))
            }
        }
    }
}

/**
 * Dove sei tu.
 *
 * Un anello che si allarga e svanisce, e un punto fermo al centro. L'anello
 * non misura niente - non e' un raggio in chilometri - e per questo non porta
 * scala: e' solo il modo piu' breve per far trovare all'occhio un punto in
 * mezzo a una macchia.
 */
private fun DrawScope.segnaPosto(
    place: Place,
    finestra: RadarRiquadro,
    palette: SalaPalette,
    fase: Float,
) {
    if (!finestra.contiene(place.latitude, place.longitude)) return
    val centro = punto(place.latitude, place.longitude, finestra)
    val raggio = 4f + fase * 16f
    drawCircle(
        color = palette.accent.copy(alpha = (1f - fase) * 0.55f),
        radius = raggio,
        center = centro,
        style = Stroke(width = 1.6f),
    )
    drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 4.5f, center = centro)
    drawCircle(color = palette.accent, radius = 3f, center = centro)
}

/**
 * Gli anelli della distanza: cinquanta e cento chilometri.
 *
 * Senza una scala, "la pioggia e' li' vicino" e' un'impressione, non una
 * lettura: la stessa macchia alla stessa distanza dal puntino puo' essere
 * sopra il paese accanto o sopra un'altra regione, e da sola la carta non lo
 * dice. Due anelli bastano, e sono **misurati**, non decorativi - a differenza
 * del battito del puntino, che infatti non porta numeri.
 *
 * Sono ellissi e non cerchi perche' lo sono davvero: cinquanta chilometri in
 * longitudine coprono piu' gradi che in latitudine, e un cerchio perfetto qui
 * sarebbe il disegno sbagliato di una cosa giusta.
 */
private fun DrawScope.disegnaAnelli(place: Place, finestra: RadarRiquadro, palette: SalaPalette) {
    val centro = punto(place.latitude, place.longitude, finestra)
    val cosLat = cos(Math.toRadians(place.latitude)).coerceAtLeast(0.2)
    listOf(50.0, 100.0).forEach { km ->
        val dLat = km / KM_PER_GRADO
        val dLon = dLat / cosLat
        val bordo = punto(place.latitude + dLat, place.longitude + dLon, finestra)
        val rx = abs(bordo.x - centro.x)
        val ry = abs(bordo.y - centro.y)
        if (rx < 2f || ry < 2f) return@forEach
        drawOval(
            color = palette.inkFaint.copy(alpha = 0.22f),
            topLeft = Offset(centro.x - rx, centro.y - ry),
            size = Size(rx * 2, ry * 2),
            style = Stroke(width = 1f),
        )
    }
}

/**
 * La pioggia prevista, come **campo** e non come mucchio di macchie.
 *
 * ## Il difetto che questa funzione ripara
 *
 * La prima versione disegnava un cerchio sfumato per ciascuno dei
 * centodiciassette punti, largo un terzo piu' del passo della griglia perche'
 * si toccassero. Il risultato l'ha visto chi usa l'app: una scheda che diceva
 * **0,0 mm** e **probabilita' 0%** sopra una carta piena di verde e arancio.
 *
 * Due errori, tutti e due miei.
 *
 * **Le trasparenze si sommavano.** Ogni macchia copriva i nove o dodici punti
 * vicini, e dodici veli al ventisei per cento non fanno un velo: fanno una
 * vernice. Un decimo di millimetro sparso dappertutto - la pioggia che il
 * modello semina e che nessuno sente - diventava una tinta piena.
 *
 * **E la pioggia colava da quaranta chilometri.** Il centro della carta e' il
 * paese di chi guarda, e il valore li' deve essere **il suo**; invece ci
 * arrivava sopra la coda sfumata dei vicini. La scheda leggeva lo zero del
 * punto giusto, la carta dipingeva la media di mezza provincia, e le due cose
 * non potevano che contraddirsi.
 *
 * ## Come si disegna adesso
 *
 * Si compone una figura piccola quanto la griglia - tredici per nove pixel,
 * uno per punto - e la si **stira** sul riquadro. L'ingrandimento la sfuma da
 * solo, con l'interpolazione bilineare che fa la GPU: ogni pixel dello schermo
 * riceve **un** colore, quello del valore interpolato, invece di una pila di
 * veli. Morbido come prima, ma corrispondente al dato.
 *
 * E il pixel centrale della figura e' esattamente il punto di casa - la
 * griglia ha tredici colonne e nove righe, dispari tutte e due, quindi il
 * centro e' un punto vero e non un interstizio. Se la scheda dice zero, li' la
 * carta e' pulita.
 */
private fun campoPrevisto(mappa: MappaPrevista, valori: List<Float>): ImageBitmap? {
    if (mappa.colonne < 2 || mappa.righe < 2) return null
    if (valori.size < mappa.colonne * mappa.righe) return null
    val pixel = IntArray(mappa.colonne * mappa.righe)
    for (r in 0 until mappa.righe) {
        for (c in 0 until mappa.colonne) {
            // La griglia va da sud a nord, la figura dall'alto in basso:
            // la riga zero dei dati e' l'ultima riga dell'immagine. Senza
            // questo capovolgimento la pioggia starebbe specchiata
            // sull'orizzontale, e in una carta sfumata non se ne accorgerebbe
            // nessuno finche' non piove davvero.
            val riga = mappa.righe - 1 - r
            pixel[riga * mappa.colonne + c] = colorePioggia(valori[r * mappa.colonne + c])
        }
    }
    return android.graphics.Bitmap
        .createBitmap(pixel, mappa.colonne, mappa.righe, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

/**
 * Il campo posato sul riquadro che copre.
 *
 * Gli estremi vengono dai punti **tornati** da Open-Meteo, allargati di mezza
 * cella per lato: i punti sono centri di cella, non angoli, e senza quella
 * mezza cella il campo risulterebbe rimpicciolito di una cella intera.
 */
private fun DrawScope.posaPrevisione(
    campo: ImageBitmap,
    mappa: MappaPrevista,
    finestra: RadarRiquadro,
) {
    val lat = mappa.punti.map { it.lat }
    val lon = mappa.punti.map { it.lon }
    val latMin = lat.min()
    val latMax = lat.max()
    val lonMin = lon.min()
    val lonMax = lon.max()
    if (latMax <= latMin || lonMax <= lonMin) return
    val mezzaLat = (latMax - latMin) / (mappa.righe - 1) / 2
    val mezzaLon = (lonMax - lonMin) / (mappa.colonne - 1) / 2

    val alto = punto(latMax + mezzaLat, lonMin - mezzaLon, finestra)
    val basso = punto(latMin - mezzaLat, lonMax + mezzaLon, finestra)
    val larghezza = (basso.x - alto.x).toInt()
    val altezza = (basso.y - alto.y).toInt()
    if (larghezza <= 0 || altezza <= 0) return
    clipRect {
        drawImage(
            image = campo,
            dstOffset = IntOffset(alto.x.toInt(), alto.y.toInt()),
            dstSize = IntSize(larghezza, altezza),
            // Bilineare: e' l'interpolazione che trasforma tredici per nove
            // pixel in un campo continuo. Con `None` si vedrebbero i quadretti,
            // e i quadretti darebbero al modello una precisione che non ha.
            filterQuality = FilterQuality.Low,
        )
    }
}

/**
 * Il colore di un millimetro d'acqua in un'ora.
 *
 * **La soglia e' salita da un decimo a due decimi**, e l'opacita' parte molto
 * piu' bassa. Sotto i due decimi in un'ora c'e' la pioggia che il modello
 * semina dappertutto e che nessuno sente cadere: disegnarla faceva sembrare
 * bagnato un giorno che la scheda dichiarava asciutto.
 *
 * I gradini sono quelli con cui si parla di pioggia - pioviggine, pioggia,
 * pioggia forte, rovescio, nubifragio - e non una scala continua: una scala
 * continua su un dato che ha un valore ogni quaranta chilometri promette
 * sfumature che il dato non contiene.
 *
 * **Non e' la tavolozza di RainViewer**, ed e' voluto: due carte che raccontano
 * cose diverse - una misurata e una prevista - non devono somigliarsi tanto da
 * confondersi.
 */
private fun colorePioggia(mm: Float): Int = when {
    mm < SOGLIA_MM -> 0
    mm < 1f -> velo(56, 0x7F, 0xB4, 0xD4)
    mm < 2.5f -> velo(76, 0x4E, 0x92, 0xC4)
    mm < 6f -> velo(89, 0x4F, 0x8F, 0xA8)
    mm < 12f -> velo(102, 0xD9, 0xA4, 0x41)
    mm < 25f -> velo(115, 0xD1, 0x71, 0x3C)
    else -> velo(128, 0xB8, 0x45, 0x3C)
}

/** Un colore con la sua opacita', scritto in modo che si legga. */
private fun velo(alfa: Int, r: Int, g: Int, b: Int): Int =
    android.graphics.Color.argb(alfa, r, g, b)

/**
 * Sotto questo, niente.
 *
 * Due decimi di millimetro in un'ora sono la pioggia che un modello mette
 * quasi ovunque e che nessuno sente cadere. Con un decimo - la soglia della
 * prima versione - la carta si tingeva tutta, e sopra c'era scritto
 * "nessuna precipitazione attesa".
 */
private const val SOGLIA_MM = 0.2f
