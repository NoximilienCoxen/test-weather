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
            Text(text = "IL RADAR", style = SalaType.sectionLabel, color = palette.inkFaint)
            Text(
                text = when (stato) {
                    is StatoRadar.Pronto -> ORARIO.format(stato.prodotto.istante.atZone(ZoneId.systemDefault()))
                    StatoRadar.InCorso -> "in arrivo"
                    StatoRadar.FuoriCopertura -> "nessun radar"
                    is StatoRadar.FuoriOrario -> "niente per quest'ora"
                    is StatoRadar.NonDisponibile -> "non disponibile"
                },
                style = SalaType.rowNote,
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
