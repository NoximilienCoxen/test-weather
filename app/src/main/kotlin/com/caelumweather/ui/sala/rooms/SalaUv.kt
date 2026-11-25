package com.caelumweather.ui.sala.rooms

import com.caelumweather.ui.theme.readableOn
import com.caelumweather.ui.sala.NumeroCheScorre
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.semantics
import com.caelumweather.ui.sala.rooms.uv.CieloDellaLuce
import com.caelumweather.ui.sala.rooms.uv.Fascia
import com.caelumweather.ui.sala.rooms.uv.OmbraSole
import com.caelumweather.ui.sala.rooms.uv.cieloDellaLuce
import com.caelumweather.ui.sala.rooms.uv.luceDelGiorno
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caelumweather.ui.UiState
import com.caelumweather.ui.sala.CellaValore
import com.caelumweather.ui.sala.Didascalia
import com.caelumweather.ui.sala.PannelloSala
import com.caelumweather.ui.sala.RigaSenzaOre
import com.caelumweather.ui.sala.SalaPalette
import com.caelumweather.ui.sala.misuraCheArriva
import com.caelumweather.ui.sala.SalaTokens
import com.caelumweather.ui.sala.SalaType
import com.caelumweather.ui.sala.oraDueCifre
import com.caelumweather.ui.sala.oraPiena
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sala VII — I raggi UV: l'indice ora per ora, sulle ore in cui esiste.
 *
 * Legge le ventiquattro ore vere e **lo stesso asse di tutte le altre**: nel
 * prototipo questa schermata aveva un cursore suo, separato, sulle sole ore
 * 6-21, e due cursori che dicono l'ora sono due verita' da tenere in fase.
 */
@Composable
fun SalaUvScreen(
    state: UiState,
    palette: SalaPalette,
    onSelectHour: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Falso quando chi usa l'app ha chiesto meno movimento: il riflesso non
     *  passa, e il numero resta a dire quello che ha sempre detto. */
    movimento: Boolean = true,
) {
    // Le ore del giorno mostrato: vedi la nota in Sala III.
    val ore = state.shownHours
    val scelta = state.selectedHour
    val corrente = ore.getOrNull(scelta)?.uvIndex ?: 0.0
    // Le ore in cui il sole c'e': fuori da quelle l'indice vale zero a ogni
    // latitudine, e quindici colonne a zero non sono un grafico.
    val finestra = ore.indices.filter { ore[it].time.hour in 5..20 }
    val picco = ore.indices.maxByOrNull { ore[it].uvIndex ?: 0.0 }
    val ozono = state.air?.ozone

    // **Il riflesso avvisa dove il numero non basta.** Cinque e' la soglia in
    // cui la scala mondiale dell'OMS passa da "moderato" ad "alto", cioe' dove
    // la protezione smette di essere consigliata e diventa necessaria. Sopra
    // quel valore la scheda luccica ogni tanto: non aggiunge un dato - il
    // numero e la parola ci sono gia' - aggiunge il fatto che uno sguardo di
    // passaggio se ne accorga.
    //
    // Si accende sul valore **mostrato**, non sul picco del giorno: scorrendo
    // la barra fino alle tre di notte il riflesso deve spegnersi, perche' li'
    // l'indice e' zero e un avviso di sole alle tre di notte e' un avviso che
    // insegna a ignorare gli avvisi.
    // **Rifatta da capo.** La prima versione metteva insieme un'ombra
    // disegnata, una frase geometrica ("alle 12 sole a 24 gradi, l'ombra e' 2,3
    // volte te") e un consiglio che, con il cielo coperto e l'indice a zero,
    // diceva "sole sotto l'orizzonte" a mezzogiorno. Adesso la scheda risponde
    // a due domande sole: **quanto e' forte adesso** (il numero, la parola e la
    // scala colorata dell'OMS) e **quando proteggersi oggi** (la frase e le
    // colonne della giornata, ciascuna nel colore della sua fascia).
    val massimo = picco?.let { ore[it].uvIndex } ?: 0.0
    val sopraTre = remember(ore) { ore.filter { (it.uvIndex ?: 0.0) >= 3.0 } }
    val altezzaSole = ore.getOrNull(scelta)?.let { o ->
        remember(o.time, state.place, state.forecast?.utcOffsetSeconds) {
            OmbraSole.altezza(o.time, state.place.latitude, state.place.longitude, state.forecast?.utcOffsetSeconds ?: 0)
        }
    }
    val notte = altezzaSole != null && altezzaSole <= 0.0
    PannelloSala(palette = palette, modifier = modifier, bagliore = corrente >= 5.0 && movimento) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tr("I raggi UV", "UV rays", "UV-Strahlung", "Les rayons UV", "Los rayos UV"), style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = if (notte) tr("Sole tramontato", "Sun down", "Sonne untergegangen", "Soleil couché", "Sol puesto") else nomeUv(corrente),
                    style = SalaType.rowTitle,
                    color = if (notte) palette.inkSoft else coloreScala(corrente).readableOn(palette.panelSolido, com.caelumweather.ui.theme.CONTRAST_AA_LARGE),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            NumeroCheScorre(
                testo = String.format(Lingue.locale, "%.1f", corrente),
                valore = (corrente * 10).roundToInt(),
                style = SalaType.giant(46),
                color = palette.ink,
            )
        }

        ScalaUv(valore = corrente, palette = palette, modifier = Modifier.padding(top = 12.dp))

        if (ore.isEmpty()) RigaSenzaOre(palette, Modifier.padding(top = 16.dp))
        if (finestra.isNotEmpty()) {
            ColonneUv(
                ore = ore,
                finestra = finestra,
                scelta = scelta,
                palette = palette,
                onSelectHour = onSelectHour,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Didascalia(
            consiglioDelGiorno(
                oggi = state.selectedDay == 0,
                massimo = massimo,
                daOra = sopraTre.firstOrNull()?.time?.hour,
                aOra = sopraTre.lastOrNull()?.time?.hour?.plus(1),
            ),
            palette,
            modifier = Modifier.padding(top = 10.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            CellaValore(
                etichetta = tr("PICCO", "PEAK", "SPITZE", "PIC", "PICO"),
                valore = picco?.takeIf { massimo > 0.2 }?.let { "${oraPiena(ore[it].time.hour)} · ${String.format(Lingue.locale, "%.1f", massimo)}" } ?: "--",
                palette = palette,
            )
            CellaValore(
                etichetta = tr("AL SOLE", "IN THE SUN", "IN DER SONNE", "AU SOLEIL", "AL SOL"),
                valore = if (notte) "--" else esposizione(corrente),
                palette = palette,
            )
            if (ozono != null) {
                CellaValore(
                    etichetta = tr("OZONO", "OZONE", "OZON", "OZONE", "OZONO"),
                    valore = "${ozono.roundToInt()} µg/m³",
                    palette = palette,
                )
            }
        }

        // ── La luce per le foto ──────────────────────────────────────────────
        //
        // Sta qui perche' questa e' la sala del sole: l'ombra sopra dice quanto
        // e' alto, e queste due colonne quando e' abbastanza basso da fare la
        // luce che si fotografa. Del giorno mostrato, come tutto il resto.
        val data = ore.firstOrNull()?.time?.toLocalDate()
        if (data != null) {
            val luce = remember(data, state.place, state.forecast?.utcOffsetSeconds) {
                luceDelGiorno(data, state.place.latitude, state.place.longitude, state.forecast?.utcOffsetSeconds ?: 0)
            }
            Text(
                text = tr("LA LUCE PER LE FOTO", "LIGHT FOR PHOTOS", "LICHT FÜR FOTOS", "LA LUMIÈRE POUR LES PHOTOS", "LA LUZ PARA LAS FOTOS"),
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
                modifier = Modifier.padding(top = 10.dp, bottom = 5.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                ColonnaLuce(
                    quando = tr("MATTINA", "MORNING", "MORGEN", "MATIN", "MAÑANA"),
                    oro = luce.oroMattina,
                    blu = luce.bluMattina?.let { tr("ora blu dalle ${hhmm(it.inizio)}", "blue hour from ${hhmm(it.inizio)}", "blaue Stunde ab ${hhmm(it.inizio)}", "heure bleue dès ${hhmm(it.inizio)}", "hora azul desde las ${hhmm(it.inizio)}") },
                    cielo = cieloDellaLuce(luce.oroMattina, ore),
                    palette = palette,
                )
                ColonnaLuce(
                    quando = tr("SERA", "EVENING", "ABEND", "SOIR", "TARDE"),
                    oro = luce.oroSera,
                    blu = luce.bluSera?.let { tr("ora blu fino alle ${hhmm(it.fine)}", "blue hour until ${hhmm(it.fine)}", "blaue Stunde bis ${hhmm(it.fine)}", "heure bleue jusqu'à ${hhmm(it.fine)}", "hora azul hasta las ${hhmm(it.fine)}") },
                    cielo = cieloDellaLuce(luce.oroSera, ore),
                    palette = palette,
                )
            }
        }
    }
}

/**
 * Mezza giornata di luce: l'ora d'oro in grande, perche' e' quella che si
 * cerca; l'ora blu e il cielo sotto, piccoli.
 */
@Composable
private fun RowScope.ColonnaLuce(
    quando: String,
    oro: Fascia?,
    blu: String?,
    cielo: CieloDellaLuce?,
    palette: SalaPalette,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(22.dp))
            .background(palette.chip)
            .padding(horizontal = 13.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = quando, style = SalaType.microLabel, color = palette.inkFaint)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SalaTokens.accent400))
            Text(
                text = oro?.let { "${hhmm(it.inizio)} – ${hhmm(it.fine)}" } ?: "--",
                style = SalaType.rowTitle,
                color = palette.ink,
                maxLines = 1,
            )
        }
        if (blu != null) Text(text = blu, style = SalaType.microLabel, color = palette.inkSoft, maxLines = 1)
        if (cielo != null) {
            Text(
                text = when (cielo) {
                    CieloDellaLuce.LIBERO -> tr("cielo libero, luce calda", "clear sky, warm light", "freier Himmel, warmes Licht", "ciel dégagé, lumière chaude", "cielo despejado, luz cálida")
                    CieloDellaLuce.SPARSO -> tr("nuvole sparse, possibili colori", "scattered clouds, maybe colour", "vereinzelte Wolken, vielleicht Farben", "nuages épars, couleurs possibles", "nubes dispersas, posibles colores")
                    CieloDellaLuce.COPERTO -> tr("coperto, luce piatta", "overcast, flat light", "bedeckt, flaches Licht", "couvert, lumière plate", "cubierto, luz plana")
                },
                style = SalaType.rowNote,
                color = palette.inkFaint,
            )
        }
    }
}

// Non `oraDueCifre`, che si ferma a 23: i minuti arrivano a 59.
private fun hhmm(t: java.time.LocalDateTime): String =
    String.format(java.util.Locale.ROOT, "%02d:%02d", t.hour, t.minute)



/**
 * Le cifre sopra e sotto le colonne: il corpo delle etichette, senza la loro
 * spaziatura. Lo 0,1 em fra le lettere e' fatto per le parole in maiuscolo; su
 * due cifre allargava "00" di un punto e mezzo, quanto bastava a non farle
 * stare in sedici colonne su un telefono largo.
 */
internal val EtichettaGrafico = SalaType.microLabel.copy(letterSpacing = 0.sp)

/** L'aria fra una colonna e l'altra del grafico. */
private val SPAZIO_COLONNE = 4.dp

/** I nomi della scala mondiale: gli stessi cinque gradini di ogni bollettino. */
private fun nomeUv(valore: Double): String = when {
    valore >= 11 -> tr("Estremo", "Extreme", "Extrem", "Extrême", "Extremo")
    valore >= 8 -> tr("Molto alto", "Very high", "Sehr hoch", "Très élevé", "Muy alto")
    valore >= 6 -> tr("Alto", "High", "Hoch", "Élevé", "Alto")
    valore >= 3 -> tr("Moderato", "Moderate", "Mäßig", "Modéré", "Moderado")
    valore > 0.2 -> tr("Basso", "Low", "Niedrig", "Faible", "Bajo")
    else -> tr("Assente", "None", "Keine", "Nul", "Nulo")
}

/**
 * Quanto si sta al sole prima di scottarsi.
 *
 * E' la regola pratica del tempo di eritema per una pelle chiara non protetta:
 * circa duecento diviso l'indice, in minuti. Un ordine di grandezza, e va detto
 * come tale - non un timer.
 */
private fun esposizione(valore: Double): String = when {
    valore <= 0.2 -> tr("libera", "unlimited", "unbegrenzt", "illimitée", "sin límite")
    else -> "~${(200.0 / valore).roundToInt().coerceAtMost(240)} min"
}

/**
 * I colori della scala UV dell'OMS: verde, giallo, arancio, rosso, viola. Sono
 * quelli che si vedono sui cartelli delle spiagge e nei bollettini, e per
 * questo restano questi in ogni tema: un colore della scala che cambiasse con
 * la carta non direbbe piu' la stessa cosa.
 */
private val FasceUv = listOf(
    0.0 to Color(0xFF6BA368),
    3.0 to Color(0xFFE2B93B),
    6.0 to Color(0xFFE8873A),
    8.0 to Color(0xFFD9483B),
    11.0 to Color(0xFF8E5BB5),
)

private fun coloreScala(valore: Double): Color = FasceUv.last { valore >= it.first }.second

/**
 * La scala dell'OMS in una barra: cinque fasce colorate, larghe quanto i loro
 * valori fino a 12, e un segnaposto sul valore mostrato.
 */
@Composable
private fun ScalaUv(valore: Double, palette: SalaPalette, modifier: Modifier = Modifier) {
    val posizione = (valore / 12.0).coerceIn(0.0, 1.0).toFloat()
    val anello = palette.panelSolido
    val inchiostro = palette.ink
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            val alto = 8.dp.toPx()
            val cima = (size.height - alto) / 2f
            val spazio = 2.dp.toPx()
            val limiti = FasceUv.map { it.first } + 12.0
            for (i in FasceUv.indices) {
                val x0 = (limiti[i] / 12.0 * size.width).toFloat() + if (i > 0) spazio / 2f else 0f
                val x1 = (limiti[i + 1] / 12.0 * size.width).toFloat() - if (i < FasceUv.lastIndex) spazio / 2f else 0f
                drawRoundRect(
                    color = FasceUv[i].second,
                    topLeft = Offset(x0, cima),
                    size = androidx.compose.ui.geometry.Size(x1 - x0, alto),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(alto / 2f),
                )
            }
            val x = (posizione * size.width).coerceIn(size.height / 2f, size.width - size.height / 2f)
            drawCircle(color = anello, radius = size.height / 2f, center = Offset(x, size.height / 2f))
            drawCircle(color = inchiostro, radius = size.height / 2f - 3.dp.toPx(), center = Offset(x, size.height / 2f))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp)) {
            listOf(
                3f to tr("basso", "low", "niedrig", "faible", "bajo"),
                3f to tr("moderato", "moderate", "mäßig", "modéré", "moderado"),
                2f to tr("alto", "high", "hoch", "élevé", "alto"),
                3f to tr("molto alto", "very high", "sehr hoch", "très élevé", "muy alto"),
                1f to "11+",
            ).forEach { (peso, nome) ->
                Text(
                    text = nome,
                    style = EtichettaGrafico,
                    color = palette.inkFaint,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                    modifier = Modifier.weight(peso),
                )
            }
        }
    }
}

/**
 * Le ore di luce in colonne, ciascuna nel colore della sua fascia e alta
 * quanto l'indice (su una scala che arriva almeno a 8, cosi' un giorno basso
 * resta basso). Sopra le colonne che contano, il valore; sotto, l'ora ogni
 * tre. Si tocca o si trascina per scegliere l'ora, come la barra in basso.
 */
@Composable
private fun ColonneUv(
    ore: List<com.caelumweather.data.HourForecast>,
    finestra: List<Int>,
    scelta: Int,
    palette: SalaPalette,
    onSelectHour: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tetto = (finestra.maxOfOrNull { ore[it].uvIndex ?: 0.0 } ?: 0.0).coerceAtLeast(8.0)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(finestra) {
                detectHorizontalDragGestures { cambio, _ ->
                    cambio.consume()
                    val i = (cambio.position.x / size.width * finestra.size).toInt().coerceIn(0, finestra.lastIndex)
                    if (finestra[i] != scelta) onSelectHour(finestra[i])
                }
            },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        finestra.forEach { indice ->
            val uv = ore[indice].uvIndex ?: 0.0
            val evidenziata = indice == scelta
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = "${oraDueCifre(ore[indice].time.hour)}:00, UV ${String.format(Lingue.locale, "%.1f", uv)}"
                        selected = evidenziata
                    }
                    .clickable { onSelectHour(indice) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (uv >= 0.95) uv.roundToInt().toString() else "",
                    style = EtichettaGrafico,
                    color = if (evidenziata) palette.ink else palette.inkFaint,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .fillMaxWidth()
                        .height(misuraCheArriva((4f + (uv / tetto).toFloat().coerceIn(0f, 1f) * 40f).dp))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(if (uv > 0.2) coloreScala(uv) else palette.maniglia),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(width = 14.dp, height = 3.dp)
                        .clip(CircleShape)
                        .background(if (evidenziata) palette.accent else Color.Transparent),
                )
                Text(
                    text = if (ore[indice].time.hour % 3 == 0 || evidenziata) oraDueCifre(ore[indice].time.hour) else "",
                    style = EtichettaGrafico,
                    color = if (evidenziata) palette.accent else palette.inkSoft,
                    maxLines = 1,
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                )
            }
        }
    }
}

/**
 * Quando proteggersi, **dalla giornata intera e non dall'ora mostrata**: e'
 * la domanda che si fa uscendo di casa. Non parla mai del sole sotto
 * l'orizzonte - quella e' la parola "Sole tramontato" in testa, che viene
 * dall'altezza vera del sole e non da un indice a zero sotto le nuvole.
 */
private fun consiglioDelGiorno(oggi: Boolean, massimo: Double, daOra: Int?, aOra: Int?): String {
    val quando = if (oggi) tr("Oggi", "Today", "Heute", "Aujourd'hui", "Hoy") else tr("Quel giorno", "That day", "An dem Tag", "Ce jour-là", "Ese día")
    return when {
        daOra != null && aOra != null && massimo >= 8 -> tr(
            "$quando indice molto alto: dalle ${oraDueCifre(daOra)} alle ${oraDueCifre(aOra)} cappello, occhiali, crema ad alto fattore e ombra quando c'è.",
            "$quando a very high index: from ${oraDueCifre(daOra)} to ${oraDueCifre(aOra)} a hat, sunglasses, high-factor sunscreen and shade when there is any.",
            "$quando sehr hoher Index: von ${oraDueCifre(daOra)} bis ${oraDueCifre(aOra)} Uhr Hut, Sonnenbrille, Sonnencreme mit hohem Schutz und Schatten.",
            "$quando indice très élevé : de ${oraDueCifre(daOra)} h à ${oraDueCifre(aOra)} h chapeau, lunettes, crème à indice élevé et ombre.",
            "$quando índice muy alto: de ${oraDueCifre(daOra)} a ${oraDueCifre(aOra)} sombrero, gafas, crema de alto factor y sombra.",
        )
        daOra != null && aOra != null -> tr(
            "Proteggiti dalle ${oraDueCifre(daOra)} alle ${oraDueCifre(aOra)}: crema, occhiali e cappello se resti fuori a lungo.",
            "Protect yourself from ${oraDueCifre(daOra)} to ${oraDueCifre(aOra)}: sunscreen, sunglasses and a hat if you stay out long.",
            "Schütze dich von ${oraDueCifre(daOra)} bis ${oraDueCifre(aOra)} Uhr: Sonnencreme, Sonnenbrille und Hut, wenn du lange draußen bleibst.",
            "Protège-toi de ${oraDueCifre(daOra)} h à ${oraDueCifre(aOra)} h : crème, lunettes et chapeau si tu restes longtemps dehors.",
            "Protégete de ${oraDueCifre(daOra)} a ${oraDueCifre(aOra)}: crema, gafas y sombrero si estás mucho tiempo fuera.",
        )
        massimo > 0.2 -> tr(
            "$quando l'indice resta sotto 3: non serve protezione, nemmeno nelle ore centrali.",
            "$quando the index stays below 3: no protection needed, even around midday.",
            "$quando bleibt der Index unter 3: kein Schutz nötig, auch mittags nicht.",
            "$quando l'indice reste sous 3 : aucune protection nécessaire, même à midi.",
            "$quando el índice se queda por debajo de 3: no hace falta protección, ni a mediodía.",
        )
        else -> tr(
            "$quando i raggi UV restano a zero: niente da cui proteggersi.",
            "$quando UV stays at zero: nothing to protect yourself from.",
            "$quando bleibt die UV-Strahlung bei null: kein Schutz nötig.",
            "$quando les UV restent à zéro : rien dont se protéger.",
            "$quando los rayos UV se quedan en cero: nada de qué protegerse.",
        )
    }
}
