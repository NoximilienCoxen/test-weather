package com.caelumweather.ui.sala.rooms

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.caelumweather.data.HourForecast
import com.caelumweather.lingua.tr
import com.caelumweather.prefs.TempUnit
import com.caelumweather.ui.motion.rememberVibrazioniMeteo
import com.caelumweather.ui.sala.LocalMovimento
import com.caelumweather.ui.sala.SalaPalette
import com.caelumweather.ui.sala.SalaType
import com.caelumweather.ui.sala.oraDueCifre
import kotlin.math.roundToInt

/**
 * La curva della temperatura sulle ventiquattro ore del giorno mostrato.
 *
 * Prima di questa curva la giornata era **un numero che cambia trascinando la
 * barra delle ore**: per sapere se il pomeriggio era piu' caldo di adesso
 * bisognava andarci. Qui si vede tutta insieme, con la minima e la massima
 * segnate, la notte in grigio piu' scuro, un tacchetto sotto l'ora di adesso e
 * un cursore sull'ora scelta.
 *
 * **Il cursore e' lo stesso `selectHour` della barra delle ore**: trascinare il
 * dito sulla curva sposta anche il numero grande, il cielo e le altre sale,
 * come fa la colonna della pioggia. Un colpetto a ogni ora, perche' la mano ne
 * senta il passo (spento con le animazioni ridotte).
 *
 * **Si disegna una volta e poi sta ferma**: la curva si rivela da sinistra
 * quando compare (o cambia giorno), il cursore scivola da un'ora all'altra con
 * una molla; con le animazioni ridotte, nessuna delle due cose.
 */
@Composable
internal fun GraficoGiornata(
    ore: List<HourForecast>,
    scelta: Int,
    adesso: Int?,
    unita: TempUnit,
    palette: SalaPalette,
    onScegli: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val punti = remember(ore) { ore.mapNotNull { h -> h.temperature?.let { h.time.hour to it } } }
    if (punti.size < 3) return
    val movimento = LocalMovimento.current
    val vibrazioni = rememberVibrazioniMeteo()
    val misura = rememberTextMeasurer()

    val minimo = punti.minOf { it.second }
    val massimo = punti.maxOf { it.second }
    val corrente = ore.getOrNull(scelta)
    fun gradi(v: Double) = "${unita.from(v).roundToInt()}°"

    val rivelazione = remember(ore) { Animatable(if (movimento) 0f else 1f) }
    LaunchedEffect(ore, movimento) {
        if (movimento) rivelazione.animateTo(1f, tween(900)) else rivelazione.snapTo(1f)
    }
    val cursore by animateFloatAsState(
        targetValue = scelta.toFloat(),
        animationSpec = if (movimento) spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow) else spring(stiffness = Spring.StiffnessHigh),
        label = "cursore-giornata",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = tr("LA GIORNATA", "THE DAY", "DER TAG", "LA JOURNÉE", "EL DÍA"),
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
            )
            Text(
                text = corrente?.temperature?.let { "${oraDueCifre(corrente.time.hour)}:00 · ${gradi(it)}" } ?: "",
                style = SalaType.sectionLabel,
                color = palette.accent,
            )
        }
        val descrizione = tr(
            "Temperatura nelle 24 ore: minima ${gradi(minimo)}, massima ${gradi(massimo)}. Trascina per scegliere l'ora.",
            "Temperature over 24 hours: low ${gradi(minimo)}, high ${gradi(massimo)}. Drag to choose the hour.",
            "Temperatur über 24 Stunden: Tiefstwert ${gradi(minimo)}, Höchstwert ${gradi(massimo)}. Ziehen, um die Stunde zu wählen.",
            "Température sur 24 heures : minimale ${gradi(minimo)}, maximale ${gradi(massimo)}. Faites glisser pour choisir l'heure.",
            "Temperatura en 24 horas: mínima ${gradi(minimo)}, máxima ${gradi(massimo)}. Arrastra para elegir la hora.",
        )
        val altezzaGiorno = ore.size.coerceAtLeast(2)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ALTEZZA_GRAFICO)
                .semantics { contentDescription = descrizione }
                .pointerInput(ore) {
                    fun indiceIn(x: Float): Int = (x / size.width * (altezzaGiorno - 1)).roundToInt().coerceIn(0, altezzaGiorno - 1)
                    detectTapGestures { p -> onScegli(indiceIn(p.x)) }
                }
                .pointerInput(ore, scelta) {
                    var ultima = -1
                    fun indiceIn(x: Float): Int = (x / size.width * (altezzaGiorno - 1)).roundToInt().coerceIn(0, altezzaGiorno - 1)
                    detectHorizontalDragGestures(
                        onDragStart = { p -> ultima = indiceIn(p.x); onScegli(ultima) },
                    ) { cambio, _ ->
                        cambio.consume()
                        val nuovo = indiceIn(cambio.position.x)
                        if (nuovo != ultima) {
                            ultima = nuovo
                            onScegli(nuovo)
                            if (movimento) vibrazioni.scatto()
                        }
                    }
                },
        ) {
            disegnaGiornata(
                ore = ore,
                punti = punti,
                scelta = scelta,
                cursore = cursore,
                adesso = adesso,
                rivelazione = rivelazione.value,
                unita = unita,
                palette = palette,
                misura = misura,
            )
        }
        // Le ore in basso: ogni sei, a coprire la giornata.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 6, 12, 18, 23).forEach { h ->
                Text(text = oraDueCifre(h), style = SalaType.microLabel, color = palette.inkFaint)
            }
        }
    }
}

/** Alta quanto basta a leggere la forma: la scheda di oggi deve starci intera. */
internal val ALTEZZA_GRAFICO = 80.dp

/**
 * Il disegno della curva, fuori dalla composizione perche' si possa provare
 * (`AnimazioniRenderTest`, `SchedeCompatteRenderTest`): solo `DrawScope`, niente stato.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.disegnaGiornata(
    ore: List<HourForecast>,
    punti: List<Pair<Int, Double>>,
    scelta: Int,
    cursore: Float,
    adesso: Int?,
    rivelazione: Float,
    unita: TempUnit,
    palette: SalaPalette,
    misura: androidx.compose.ui.text.TextMeasurer,
) {
    val minimo = punti.minOf { it.second }
    val massimo = punti.maxOf { it.second }
    val indiceMin = punti.indexOfFirst { it.second == minimo }
    val indiceMax = punti.indexOfFirst { it.second == massimo }
    fun gradi(v: Double) = "${unita.from(v).roundToInt()}°"
    val inchiostro = palette.ink
    val accento = palette.accent
    val debole = palette.inkFaint
    // La notte e' la fascia piu' scura in tutti e due i temi: con la carta scura
    // l'inchiostro e' chiaro, e un velo d'inchiostro la schiarirebbe.
    val notte = if (palette.buio > 0.5f) Color.Black.copy(alpha = 0.2f) else palette.ink.copy(alpha = 0.045f)
    val stileEtichetta = SalaType.microLabel.copy(color = palette.inkSoft)
    val altezzaGiorno = ore.size.coerceAtLeast(2)
    val sinistra = 6.dp.toPx()
    val destra = size.width - 6.dp.toPx()
    // Margini stretti: la curva sta in una scheda che deve restare corta. I
    // margini bastano appena ai numeri di minima e massima.
    val alto = 18.dp.toPx()
    val basso = size.height - 18.dp.toPx()
    val intervallo = (massimo - minimo).coerceAtLeast(3.0)
    fun x(ora: Float) = sinistra + (destra - sinistra) * (ora / (altezzaGiorno - 1).toFloat())
    fun y(t: Double) = (basso - (basso - alto) * ((t - minimo) / intervallo)).toFloat()

    // La notte: una fascia piu' scura per ogni ora senza sole.
    val passo = (destra - sinistra) / (altezzaGiorno - 1)
    // Una fascia per ogni tratto di notte, con gli angoli arrotondati: le
    // fasce ora per ora facevano due rettangoli grigi spigolosi.
    var inizioNotte = -1
    for (i in 0..ore.size) {
        val buio = i < ore.size && !ore[i].isDay
        if (buio && inizioNotte < 0) inizioNotte = i
        if (!buio && inizioNotte >= 0) {
            val da = (x(inizioNotte.toFloat()) - passo / 2f).coerceAtLeast(0f)
            val a = (x((i - 1).toFloat()) + passo / 2f).coerceAtMost(size.width)
            drawRoundRect(
                notte,
                topLeft = Offset(da, 0f),
                size = androidx.compose.ui.geometry.Size(a - da, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            )
            // Una falce di luna in cima alla fascia: dice "notte" senza legenda.
            if (a - da > 22.dp.toPx()) {
                // Lontana dai numeri di minima e massima: se uno cade in alto
                // nella fascia, la luna va dall'altra parte.
                val margineLuna = 10.dp.toPx()
                val etichette = listOf(indiceMax, indiceMin).map { x(punti[it].first.toFloat()) to y(punti[it].second) }
                    .filter { (ex, ey) -> ex in da..a && ey < 34.dp.toPx() }
                val cx = when {
                    etichette.isEmpty() -> (da + a) / 2f
                    etichette.first().first > (da + a) / 2f -> da + margineLuna + 4.dp.toPx()
                    else -> a - margineLuna - 4.dp.toPx()
                }
                val c = Offset(cx, 9.dp.toPx())
                val r = 4.dp.toPx()
                val falce = Path().apply {
                    op(
                        Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) },
                        Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.55f, c.y - r * 0.35f), r * 0.85f)) },
                        androidx.compose.ui.graphics.PathOperation.Difference,
                    )
                }
                drawPath(falce, debole.copy(alpha = debole.alpha * 0.55f))
            }
            inizioNotte = -1
        }
    }

    val area = Path()
    // Un tratto per ora, ciascuno a tinta unita: vedi sotto.
    val tratti = ArrayList<Path>(punti.size)
    val xy = punti.map { (o, t) -> Offset(x(o.toFloat()), y(t)) }
    area.moveTo(xy[0].x, size.height)
    area.lineTo(xy[0].x, xy[0].y)
    for (i in 0 until xy.size - 1) {
        val p0 = xy.getOrElse(i - 1) { xy[i] }
        val p1 = xy[i]
        val p2 = xy[i + 1]
        val p3 = xy.getOrElse(i + 2) { xy[i + 1] }
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
        area.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        tratti.add(Path().apply { moveTo(p1.x, p1.y); cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y) })
    }
    area.lineTo(xy.last().x, size.height)
    area.close()

    clipRect(right = size.width * rivelazione) {
        drawPath(area, Brush.verticalGradient(listOf(accento.copy(alpha = 0.28f), accento.copy(alpha = 0.0f)), startY = alto, endY = size.height))
        // **La linea prende il colore della temperatura**, ora per ora: blu
        // dove fa freddo, terracotta dove fa caldo. La scala e' assoluta (vedi
        // [coloreDellaTemperatura]): un giorno freddo resta blu anche se la sua
        // massima e' "alta" per lui.
        //
        // **A tratti di tinta unita, non con una sfumatura.** La sfumatura sul
        // tratto - prima a 24 tappe, poi a 5 - faceva cadere l'emulatore della
        // CI ogni volta, ed era l'unica aggiunta accesa nel giro che l'ha
        // provato (CONTESTO §64.8-quinquies). Un tratto per ora col colore della
        // sua temperatura media: fra un'ora e l'altra il colore cambia di poco,
        // e all'occhio resta continuo.
        val tratto = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        tratti.forEachIndexed { i, pezzo ->
            val media = (punti[i].second + punti[i + 1].second) / 2.0
            drawPath(pezzo, coloreDellaTemperatura(media, palette), style = tratto)
        }
    }

    // Minima e massima: un punto e il numero, sopra la massima e sotto la minima.
    fun etichetta(i: Int, t: Double, sopra: Boolean) {
        val c = xy[i]
        drawCircle(coloreDellaTemperatura(t, palette), radius = 3.dp.toPx(), center = c)
        val testo = misura.measure(gradi(t), stileEtichetta)
        val tx = (c.x - testo.size.width / 2f).coerceIn(0f, size.width - testo.size.width)
        val ty = if (sopra) c.y - testo.size.height - 2.dp.toPx() else c.y + 2.dp.toPx()
        drawText(testo, topLeft = Offset(tx, ty.coerceIn(0f, size.height - testo.size.height)))
    }
    if (rivelazione >= 0.98f) {
        etichetta(indiceMax, massimo, sopra = true)
        if (indiceMin != indiceMax) etichetta(indiceMin, minimo, sopra = false)
    }

    // L'ora di adesso: un tacchetto sotto la curva, non una riga, per non
    // farla confondere col cursore.
    adesso?.let { a ->
        if (a in 0 until altezzaGiorno) {
            drawLine(debole.copy(alpha = debole.alpha * 0.6f), Offset(x(a.toFloat()), size.height - 5.dp.toPx()), Offset(x(a.toFloat()), size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }

    // Il cursore: riga sottile, e un anello sul punto della curva.
    val cx = x(cursore.coerceIn(0f, (altezzaGiorno - 1).toFloat()))
    drawLine(inchiostro.copy(alpha = 0.28f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.2.dp.toPx())
    val sulla = ore.getOrNull(scelta)?.temperature
    if (sulla != null) {
        val cy = y(sulla)
        drawCircle(palette.chip, radius = 7.dp.toPx(), center = Offset(cx, cy))
        drawCircle(accento, radius = 5.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 2.4.dp.toPx()))
    }
}

/**
 * La giornata in piccolo, accanto a "min · max": la forma della curva in una
 * parola di spazio, sul cielo, e il comando che apre quella grande.
 *
 * Il punto e' l'ora scelta; la freccetta gira quando la curva e' aperta. Non
 * dice numeri: quelli sono nella riga accanto e nella curva grande.
 */
@Composable
internal fun MiniGiornata(
    ore: List<HourForecast>,
    scelta: Int,
    aperta: Boolean,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
    /** La parola fra la curva e la freccetta, che dice cosa si apre. */
    etichetta: String? = null,
) {
    val temperature = remember(ore) { ore.map { it.temperature } }
    val validi = temperature.filterNotNull()
    if (validi.size < 3) return
    val minimo = validi.min()
    val intervallo = (validi.max() - minimo).coerceAtLeast(2.0)
    val giro = com.caelumweather.ui.sala.angoloCheGira(if (aperta) 90f else 0f)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 40.dp, height = 14.dp)) {
            val n = (temperature.size - 1).coerceAtLeast(1)
            fun x(i: Int) = size.width * i / n
            fun y(t: Double) = (size.height - 1.dp.toPx() - (size.height - 2.dp.toPx()) * ((t - minimo) / intervallo)).toFloat()
            // Tratti di tinta unita, come nella curva grande: niente sfumature.
            val punti = temperature.mapIndexedNotNull { i, t -> t?.let { i to it } }
            for (k in 0 until punti.size - 1) {
                val (i1, t1) = punti[k]
                val (i2, t2) = punti[k + 1]
                drawLine(
                    coloreDellaTemperatura((t1 + t2) / 2.0, palette),
                    Offset(x(i1), y(t1)), Offset(x(i2), y(t2)),
                    strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round,
                )
            }
            temperature.getOrNull(scelta)?.let { t ->
                drawCircle(coloreDellaTemperatura(t, palette), radius = 2.4.dp.toPx(), center = Offset(x(scelta), y(t)))
            }
        }
        etichetta?.let {
            Text(text = " $it", style = SalaType.rowNote, color = palette.accent, maxLines = 1)
        }
        Text(
            text = " ›",
            style = SalaType.rowNote,
            color = palette.accent,
            modifier = Modifier.graphicsLayer { rotationZ = giro },
        )
    }
}

/**
 * Il colore di una temperatura, in gradi Celsius, su una scala **assoluta**:
 * blu acqua fino a 4°, ocra verso i 14°, l'accento della carta dai 24° in su.
 * Assoluta e non relativa al giorno: con una scala che si stira sulla giornata
 * un giorno da 2° a 6° avrebbe le ore "calde" in terracotta, e il colore
 * direbbe una cosa che il termometro smentisce.
 */
internal fun coloreDellaTemperatura(celsius: Double, palette: SalaPalette): Color {
    val freddo = androidx.compose.ui.graphics.lerp(com.caelumweather.ui.sala.SalaTokens.acqua, com.caelumweather.ui.sala.SalaTokens.acquaChiara, palette.buio)
    val tiepido = Color(0xFFD6A13F)
    val caldo = palette.accent
    return when {
        celsius <= 4.0 -> freddo
        celsius <= 14.0 -> androidx.compose.ui.graphics.lerp(freddo, tiepido, ((celsius - 4.0) / 10.0).toFloat())
        celsius <= 24.0 -> androidx.compose.ui.graphics.lerp(tiepido, caldo, ((celsius - 14.0) / 10.0).toFloat())
        else -> caldo
    }
}
