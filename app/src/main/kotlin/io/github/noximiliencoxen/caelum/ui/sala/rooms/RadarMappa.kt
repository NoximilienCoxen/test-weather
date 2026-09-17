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
 * ## La proiezione, che e' il punto debole dichiarato
 *
 * Latitudine e longitudine si posano sul rettangolo **in modo lineare**
 * (equirettangolare), con la sola correzione del coseno della latitudine media
 * perche' l'Italia non venga schiacciata. E' la proiezione con cui si disegna
 * la costa, ed e' quella con cui si stira l'immagine del radar.
 *
 * **Se il prodotto del DPC fosse invece in Mercatore** - e non si e' potuto
 * verificare, vedi `RadarDpcRepository` - la pioggia risulterebbe spostata in
 * verticale rispetto alla costa, di piu' verso i bordi del riquadro. Sarebbe
 * un errore **visibile**: la macchia non seguirebbe il profilo della penisola.
 * E' voluto che lo sia. L'alternativa - scegliere una proiezione a caso e
 * sperare - produrrebbe lo stesso errore senza che nessuno se ne accorga, e
 * una carta che sbaglia in silenzio e' peggio di una carta che sbaglia in
 * faccia.
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
    // prodotto dichiara: dice dove va posata l'immagine, e senza prodotto e'
    // l'Italia intera solo perche' la carta muta vuole un inquadramento. La
    // **finestra** e' quello che si guarda, e si calcola nel disegno perche'
    // dipende da quanto e' larga la carta sullo schermo.
    val dominio = prodotto?.riquadro ?: DOMINIO_MUTO

    val immagine: ImageBitmap? = remember(prodotto) {
        prodotto?.png?.let { byte ->
            runCatching { BitmapFactory.decodeByteArray(byte, 0, byte.size).asImageBitmap() }.getOrNull()
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
                    StatoRadar.FuoriCopertura -> "fuori copertura"
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
                val finestra = finestraAttorno(place, dominio, size.width / size.height)
                drawRect(mare(palette))
                immagine?.let { posaRadar(it, dominio, finestra) }
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
                StatoRadar.FuoriCopertura ->
                    "Il radar del Dipartimento della Protezione Civile copre l'Italia: " +
                        "per ${place.name} non c'è, che non è come dire che non piove."
                StatoRadar.InCorso -> "Si sta chiedendo l'ultimo fotogramma."
                is StatoRadar.Pronto ->
                    "Dati radar: Dipartimento della Protezione Civile, CC BY-SA 4.0."
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

/** Il dominio di ripiego della carta muta: l'Italia, con un po' di mare attorno. */
private val DOMINIO_MUTO = RadarRiquadro(latMin = 35.2, lonMin = 5.4, latMax = 47.4, lonMax = 19.6)

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
 * La finestra: centrata su di te, larga quanto serve, dentro il dominio.
 *
 * Si parte dal posto scelto e si apre di [RAGGIO_GRADI] sopra e sotto; la
 * larghezza esce dalle proporzioni della carta sullo schermo, **divisa** per il
 * coseno della latitudine - un grado di longitudine a Forli' e' lungo tre
 * quarti di uno di latitudine, e senza quella divisione l'Italia verrebbe
 * grassa.
 *
 * Poi si **trasla** perche' non esca dal dominio del prodotto: meglio un posto
 * decentrato dentro la mappa che il posto al centro con mezza mappa vuota. Si
 * trasla e non si restringe, cosi' la scala resta quella dichiarata dagli
 * anelli.
 */
private fun finestraAttorno(place: Place, dominio: RadarRiquadro, proporzione: Float): RadarRiquadro {
    val mezzaLat = RAGGIO_GRADI
    val mezzaLon = mezzaLat * proporzione / cos(Math.toRadians(place.latitude)).coerceAtLeast(0.2)
    var latMin = place.latitude - mezzaLat
    var latMax = place.latitude + mezzaLat
    var lonMin = place.longitude - mezzaLon
    var lonMax = place.longitude + mezzaLon
    if (latMin < dominio.latMin) { latMax += dominio.latMin - latMin; latMin = dominio.latMin }
    if (latMax > dominio.latMax) { latMin -= latMax - dominio.latMax; latMax = dominio.latMax }
    if (lonMin < dominio.lonMin) { lonMax += dominio.lonMin - lonMin; lonMin = dominio.lonMin }
    if (lonMax > dominio.lonMax) { lonMin -= lonMax - dominio.lonMax; lonMax = dominio.lonMax }
    return RadarRiquadro(latMin = latMin, lonMin = lonMin, latMax = latMax, lonMax = lonMax)
}

/** Dove cade una coordinata dentro il rettangolo disegnato. */
private fun DrawScope.punto(lat: Double, lon: Double, f: RadarRiquadro): Offset = Offset(
    x = ((lon - f.lonMin) / (f.lonMax - f.lonMin)).toFloat() * size.width,
    y = (1f - ((lat - f.latMin) / (f.latMax - f.latMin)).toFloat()) * size.height,
)

private fun mare(palette: SalaPalette): Color =
    if (palette.dark) SalaTokens.ghiaccioScuro.copy(alpha = 0.32f)
    else SalaTokens.ghiaccioChiaro.copy(alpha = 0.75f)

/**
 * L'immagine del radar posata sul proprio riquadro.
 *
 * Il riquadro del prodotto e quello della carta oggi coincidono - la finestra
 * *e'* il riquadro - ma restano due parametri distinti perche' il giorno in cui
 * si vorra' ingrandire su una provincia la differenza sara' tutto: l'immagine
 * va posata dove dice il **suo** riquadro, non dove capita.
 */
private fun DrawScope.posaRadar(
    immagine: ImageBitmap,
    suo: RadarRiquadro,
    finestra: RadarRiquadro,
) {
    val alto = punto(suo.latMax, suo.lonMin, finestra)
    val basso = punto(suo.latMin, suo.lonMax, finestra)
    val larghezza = (basso.x - alto.x)
    val altezza = (basso.y - alto.y)
    if (larghezza <= 0f || altezza <= 0f) return
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
