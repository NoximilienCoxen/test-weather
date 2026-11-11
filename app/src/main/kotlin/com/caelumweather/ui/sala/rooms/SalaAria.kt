package com.caelumweather.ui.sala.rooms

import com.caelumweather.lingua.inMezzoAllaFrase
import com.caelumweather.lingua.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.caelumweather.data.AirBand
import com.caelumweather.data.AirScale
import com.caelumweather.data.OraAria
import com.caelumweather.ui.UiState
import com.caelumweather.ui.sala.Didascalia
import com.caelumweather.ui.sala.PannelloSala
import com.caelumweather.ui.sala.SalaPalette
import com.caelumweather.ui.sala.SalaTokens
import com.caelumweather.ui.sala.SalaType
import com.caelumweather.ui.sala.diGiorno
import com.caelumweather.ui.sala.oraDueCifre
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Sala V — L'aria: l'indice, e i quattro inquinanti che lo compongono.
 *
 * Il polline, che il prototipo aveva e qui mancava, e' arrivato quando si e'
 * scoperto che lo stesso endpoint dell'aria lo serve, per l'Europa: prima tre
 * pastiglie con dentro un numero inventato sarebbero state la parte piu'
 * convincente della schermata e l'unica falsa. Adesso c'e', col dato vero, e
 * dove il dato non c'e' la sezione non compare.
 */
@Composable
fun SalaAriaScreen(
    state: UiState,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    val aria = state.air
    // Le ore del giorno mostrato: le legge il grafico, e da qui anche l'anello.
    val ore = remember(aria, state.selectedDay, state.detailDay?.date) {
        val giorno = state.detailDay?.date
        aria?.oreOggi.orEmpty().filter { giorno == null || it.ora.toLocalDate() == giorno }
    }
    val oraScelta = state.detailHour?.time?.hour
    // **L'anello segue il momento mostrato, come ogni altra sala.** Diceva
    // sempre l'aria di adesso: scelto venerdi', il grafico sotto parlava di
    // venerdi' e il numero grande di oggi. Adesso e' il valore misurato solo
    // quando si guarda adesso; altrimenti e' la previsione oraria di quell'ora,
    // e sotto la parola si dice di quale ora si tratta.
    val adesso = state.selectedDay == 0 && state.selectedHour == state.nowIndex
    val indice: Int? = if (adesso) {
        aria?.index
    } else {
        ore.firstOrNull { it.ora.hour == oraScelta }?.indice
    }
    val banda = if (adesso) aria?.band else indice?.let { aria?.scale?.band(it) }
    // Oltre i tre giorni che l'API copre, per quel giorno l'aria non c'e'.
    val fuoriModello = !adesso && aria != null && indice == null
    val tinta = coloreBanda(banda)

    PannelloSala(palette = palette, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // **Settantadue punti e non novantasei**: la sala e' cresciuta del
            // polline, e l'anello era la cosa piu' grande della scheda per il
            // dato piu' breve.
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                // L'anello dice **quanto** prima di dire quale: la porzione
                // colorata cresce con l'indice, e il resto resta il grigio
                // dell'interfaccia.
                val quota = ((indice ?: 0) / 100f).coerceIn(0f, 1f)
                Canvas(modifier = Modifier.size(72.dp)) {
                    val spessore = 8.dp.toPx()
                    drawCircle(
                        color = palette.maniglia,
                        radius = size.minDimension / 2f - spessore / 2f,
                        style = Stroke(width = spessore),
                    )
                    if (quota > 0f) {
                        drawArc(
                            color = tinta,
                            startAngle = -90f,
                            sweepAngle = 360f * quota,
                            useCenter = false,
                            topLeft = Offset(spessore / 2f, spessore / 2f),
                            size = Size(size.width - spessore, size.height - spessore),
                            style = Stroke(width = spessore),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = indice?.toString() ?: "--",
                        style = SalaType.cardTitle,
                        color = palette.ink,
                    )
                    Text(text = "AQI", style = SalaType.microLabel, color = palette.inkFaint)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tr("L'aria", "Air", "Die Luft", "L'air", "El aire"), style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = banda?.label?.inMezzoAllaFrase()?.replaceFirstChar { it.uppercase() }
                        ?: when {
                            fuoriModello -> tr("Non prevista", "Not forecast", "Keine Vorhersage", "Non prévue", "Sin previsión")
                            state.airUnavailable -> tr("Non disponibile", "Unavailable", "Nicht verfügbar", "Indisponible", "No disponible")
                            else -> tr("In arrivo", "On its way", "Unterwegs", "En cours d'arrivée", "En camino")
                        },
                    style = SalaType.rowTitle,
                    color = palette.accent,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (!adesso && oraScelta != null) {
                    Text(
                        text = tr(
                            "previsione ${state.diGiorno(state.detailDay?.date ?: LocalDate.now())} " +
                                "alle ${oraDueCifre(oraScelta)}:00",
                            "forecast ${state.diGiorno(state.detailDay?.date ?: LocalDate.now())} " +
                                "at ${oraDueCifre(oraScelta)}:00",
                            "Vorhersage ${state.diGiorno(state.detailDay?.date ?: LocalDate.now())} um ${oraDueCifre(oraScelta)}:00",
                            "prévision ${state.diGiorno(state.detailDay?.date ?: LocalDate.now())} à ${oraDueCifre(oraScelta)}:00",
                            "previsión ${state.diGiorno(state.detailDay?.date ?: LocalDate.now())} a las ${oraDueCifre(oraScelta)}:00",
                        ),
                        style = SalaType.rowNote,
                        color = palette.inkSoft,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        // La frase sotto il numero solo quando c'e' qualcosa da fare: con
        // l'aria buona o discreta ripeteva la parola accanto all'anello in tre
        // righe, e tre righe erano un quinto della scheda.
        if (fuoriModello) {
            Didascalia(
                tr("Il modello dell'aria arriva a oggi e ai due giorni dopo: per questo giorno non c'è.", "The air model covers today and the two days after: there is nothing for this day.", "Das Luftmodell reicht bis heute und die zwei Tage danach: Für diesen Tag gibt es nichts.", "Le modèle de l'air couvre aujourd'hui et les deux jours suivants : pour ce jour, il n'y a rien.", "El modelo del aire llega a hoy y a los dos días siguientes: para este día no hay nada."),
                palette,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (banda == null || banda.ordinal > AirBand.DISCRETA.ordinal || state.airUnavailable) {
            Didascalia(
                descrizione(banda, state.airUnavailable),
                palette,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ── L'andamento della giornata ───────────────────────────────────────
        //
        // **Il dato c'era e non si chiedeva.** Lo stesso endpoint che da' il
        // valore di adesso da' anche le ventiquattro ore, e questa sala
        // mostrava un numero solo: "27, discreta" - vero, e muto. Un indice che
        // alle otto vale venti e alle quattordici sessanta racconta una
        // giornata; detto una volta sola non dice **quando uscire**, che e'
        // l'unica domanda che ci si fa guardando l'aria.
        if (ore.size >= 6 && aria != null) {
            Text(
                text = tr("NELLA GIORNATA", "THROUGH THE DAY", "IM TAGESVERLAUF", "DANS LA JOURNÉE", "A LO LARGO DEL DÍA"),
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
            )
            AndamentoAria(
                ore = ore,
                // **La scala dev'essere quella del numero in cima.** L'indice
                // europeo va da zero a cento, quello americano da zero a
                // cinquecento: colorare le colonne col metro sbagliato
                // dipingerebbe di verde una giornata pessima, o viceversa.
                scala = aria.scale,
                oraScelta = state.detailHour?.time?.hour,
                palette = palette,
            )
            Didascalia(
                consiglioOrario(ore, state.detailHour?.time?.hour),
                palette,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ── Gli inquinanti, col loro limite ──────────────────────────────────
        //
        // **In una griglia due per due, e senza la frase sotto.** Erano quattro
        // righe a tutta larghezza piu' "oggi l'indice lo decide l'ozono, al
        // novantacinque per cento": la stessa informazione ora la dice la
        // cella in grassetto, con la sua percentuale.
        //
        // **I limiti sono quelli dell'OMS del 2021, non quelli di legge.**
        // Erano le soglie europee - venticinque per il PM 2,5, cinquanta per il
        // PM 10 - che sono il confine di cio' che e' punibile, non di cio' che
        // fa male: l'OMS mette quindici e quarantacinque. A chi sta decidendo
        // se andare a correre serve il secondo metro.
        //
        // Il confronto col dominante e' sul **valore**, non sul nome: due
        // stringhe uguali scritte in due file diversi si scollano al primo che
        // le ritocca.
        val dominante = aria?.dominante
        // Gli inquinanti arrivano solo come misura di adesso: lontano da adesso
        // lo si dice, invece di lasciarli passare per quelli dell'ora mostrata.
        Text(
            text = if (adesso) tr("RISPETTO AL LIMITE OMS", "AGAINST THE WHO LIMIT", "GEMESSEN AM WHO-GRENZWERT", "PAR RAPPORT À LA LIMITE OMS", "FRENTE AL LÍMITE DE LA OMS") else tr("RISPETTO AL LIMITE OMS · ADESSO", "AGAINST THE WHO LIMIT · NOW", "GEMESSEN AM WHO-GRENZWERT · JETZT", "PAR RAPPORT À LA LIMITE OMS · MAINTENANT", "FRENTE AL LÍMITE DE LA OMS · AHORA"),
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CellaInquinante("PM 2.5", aria?.pm25, 15.0, palette, dominante?.valore == aria?.pm25, Modifier.weight(1f))
                CellaInquinante("PM 10", aria?.pm10, 45.0, palette, dominante?.valore == aria?.pm10, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CellaInquinante("O₃", aria?.ozone, 100.0, palette, dominante?.valore == aria?.ozone, Modifier.weight(1f))
                CellaInquinante(
                    "NO₂",
                    aria?.nitrogenDioxide,
                    25.0,
                    palette,
                    dominante?.valore == aria?.nitrogenDioxide,
                    Modifier.weight(1f),
                )
            }
        }

        // ── Il polline ───────────────────────────────────────────────────────
        // Dallo stesso endpoint e dalla stessa richiesta: vedi `Polline.kt`.
        SezionePolline(giorni = state.pollineMostrato, palette = palette)
    }
}

/**
 * L'indice ora per ora, in colonne.
 *
 * Stessa forma del grafico dei raggi UV, e non per pigrizia: sono la stessa
 * domanda - **a che ora conviene uscire** - e due disegni diversi per la stessa
 * domanda costringono a impararli tutti e due.
 *
 * Le colonne partono tutte dalla stessa linea di base, che sembra ovvio e non
 * lo era: nella sala UV le colonne con etichetta si alzavano di quanto misurava
 * l'etichetta, e il grafico diceva una cosa falsa per un errore di
 * impaginazione (CONTESTO 16.2).
 */
@Composable
private fun AndamentoAria(
    ore: List<OraAria>,
    scala: AirScale,
    oraScelta: Int?,
    palette: SalaPalette,
) {
    val massimo = (ore.maxOfOrNull { it.indice } ?: 1).coerceAtLeast(20)
    val misura = rememberTextMeasurer()
    val densita = LocalDensity.current
    val largaEtichetta = remember(misura, densita) {
        with(densita) { misura.measure("00", EtichettaGrafico).size.width.toDp() }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // **Un'ora ogni sei non era una scala, erano quattro numeri.** Come nel
        // grafico dei raggi UV (CONTESTO 38) si misura quante colonne servono a
        // "00" e si scrive un'ora ogni tante - una ogni due su un telefono largo -
        // piu' sempre l'ora scelta, che sposta le tacche che le starebbero addosso.
        val colonna = if (ore.isEmpty()) maxWidth else (maxWidth - 3.dp * (ore.size - 1)) / ore.size
        val ogni = listOf(1, 2, 3, 4, 6)
            .firstOrNull { n -> colonna * n + 3.dp * (n - 1) >= largaEtichetta + 3.dp } ?: 6
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ore.forEach { o ->
                    val quota = (o.indice.toFloat() / massimo).coerceIn(0.08f, 1f)
                    val mia = oraScelta != null && o.ora.hour == oraScelta
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(quota)
                            .clip(CircleShape)
                            .background(
                                coloreBanda(scala.band(o.indice))
                                    .copy(alpha = if (mia) 1f else 0.55f),
                            ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ore.forEach { o ->
                    val ora = o.ora.hour
                    val scelta = oraScelta != null && ora == oraScelta
                    val libera = oraScelta == null || kotlin.math.abs(ora - oraScelta) >= ogni
                    Text(
                        text = if (scelta || (ora % ogni == 0 && libera)) oraDueCifre(ora) else "",
                        style = EtichettaGrafico,
                        color = if (oraScelta != null && o.ora.hour == oraScelta) palette.accent
                        else palette.inkFaint,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        // **La casella di un'ora e' piu' stretta della sua
                        // etichetta.** Ventiquattro colonne su seicento pixel fanno
                        // venticinque pixel l'una, e "06" ne vuole di piu': il
                        // primo scatto mostrava uno zero sopra e un sei sotto.
                        // `unbounded` lascia il testo sforare nelle caselle
                        // accanto, che sono vuote per costruzione - le etichette
                        // hanno sempre almeno una colonna vuota accanto.
                        //
                        // E' lo stesso difetto del grafico UV di CONTESTO 16.2, in
                        // un'altra forma: li' l'etichetta alzava la colonna, qui la
                        // colonna stringe l'etichetta. Tutte e due nascono dal dare
                        // a un'etichetta la larghezza del dato che descrive.
                        modifier = Modifier.weight(1f).wrapContentWidth(unbounded = true),
                    )
                }
            }
        }
    }
}

/**
 * Una riga che dice **quando**, non com'e'.
 *
 * Il valore e la banda li dicono gia' il numero e la parola sopra. Qui serve
 * l'unica cosa che i due non dicono: fra le ore che restano, quale e' la
 * migliore. Guarda avanti dall'ora scelta e non su tutta la giornata, perche'
 * un consiglio che indica le sei del mattino alle sette di sera e' un consiglio
 * per ieri.
 */
private fun consiglioOrario(ore: List<OraAria>, oraScelta: Int?): String {
    val da = oraScelta ?: ore.firstOrNull()?.ora?.hour ?: return ""
    val avanti = ore.filter { it.ora.hour >= da }
    if (avanti.size < 3) return tr("La giornata è quasi finita: l'indice non cambia più di molto.", "The day is nearly over: the index will not change much more.", "Der Tag ist fast vorbei: Der Index ändert sich nicht mehr viel.", "La journée est presque finie : l'indice ne changera plus beaucoup.", "El día casi ha terminado: el índice ya no cambia mucho.")
    val migliore = avanti.minByOrNull { it.indice } ?: return ""
    val peggiore = avanti.maxByOrNull { it.indice } ?: return ""
    if (peggiore.indice - migliore.indice < 8) {
        return tr("L'aria resta com'è per tutte le ore che restano: nessun momento è migliore di un altro.", "The air stays as it is for the rest of the day: no hour is better than another.", "Die Luft bleibt für den Rest des Tages, wie sie ist: Keine Stunde ist besser als eine andere.", "L'air reste tel quel pour le reste de la journée : aucun moment n'est meilleur qu'un autre.", "El aire sigue como está el resto del día: ningún momento es mejor que otro.")
    }
    return tr("Fra le ore che restano la migliore è verso le %02d:00, la peggiore verso le %02d:00.", "Of the hours left, the best is around %02d:00, the worst around %02d:00.", "Von den verbleibenden Stunden ist die beste gegen %02d:00, die schlechteste gegen %02d:00.", "Parmi les heures restantes, la meilleure est vers %02d:00, la pire vers %02d:00.", "De las horas que quedan, la mejor es hacia las %02d:00, la peor hacia las %02d:00.")
        .format(migliore.ora.hour, peggiore.ora.hour)
}

/**
 * Un inquinante in una cella: il nome, il valore, e una barra sottile che dice
 * quanto del limite occupa.
 *
 * Quello che comanda l'indice ha il nome in grassetto e la **percentuale** al
 * posto dei microgrammi: e' l'unico per cui la domanda "quanto manca al
 * limite" ha una risposta che cambia qualcosa.
 */
@Composable
private fun CellaInquinante(
    nome: String,
    valore: Double?,
    soglia: Double,
    palette: SalaPalette,
    dominante: Boolean,
    modifier: Modifier = Modifier,
) {
    val quota = valore?.let { (it / soglia).coerceIn(0.0, 1.0).toFloat() } ?: 0f
    val tinta = if (quota > 0.7f) SalaTokens.accent400 else SalaTokens.verde400
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.chip)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = nome,
                style = if (dominante) SalaType.rowTitle else SalaType.hourLabel,
                color = palette.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    valore == null -> "--"
                    dominante -> "${(valore / soglia * 100).roundToInt()}%"
                    else -> "${valore.roundToInt()} µg/m³"
                },
                style = SalaType.rowNote,
                color = if (dominante) palette.ink else palette.inkSoft,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(palette.maniglia),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(quota)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(tinta),
            )
        }
    }
}

private fun coloreBanda(banda: AirBand?): Color = when (banda) {
    null -> SalaTokens.neutral400
    AirBand.BUONA, AirBand.DISCRETA -> SalaTokens.verde400
    AirBand.MEDIA -> SalaTokens.accent400
    AirBand.SCARSA -> SalaTokens.accent500
    else -> SalaTokens.accent700
}

private fun descrizione(banda: AirBand?, nonDisponibile: Boolean): String = when {
    nonDisponibile -> tr("La misura dell'aria non è arrivata: la stazione più vicina non ha risposto.", "The air reading has not arrived: the nearest station did not answer.", "Die Luftmessung ist nicht angekommen: Die nächste Station hat nicht geantwortet.", "La mesure de l'air n'est pas arrivée : la station la plus proche n'a pas répondu.", "La medición del aire no ha llegado: la estación más cercana no ha respondido.")
    banda == null -> tr("La misura dell'aria sta arrivando.", "The air reading is on its way.", "Die Luftmessung ist unterwegs.", "La mesure de l'air arrive.", "La medición del aire está llegando.")
    banda == AirBand.BUONA -> tr("Particolato basso: nessuna precauzione necessaria, nemmeno per chi è sensibile.", "Low particulates: no precautions needed, even for sensitive people.", "Wenig Feinstaub: keine Vorsicht nötig, auch nicht für empfindliche Menschen.", "Peu de particules : aucune précaution nécessaire, même pour les personnes sensibles.", "Pocas partículas: no hace falta ninguna precaución, ni siquiera para las personas sensibles.")
    banda == AirBand.DISCRETA -> tr("Aria accettabile: chi ha problemi respiratori eviti lo sforzo prolungato all'aperto.", "Acceptable air: people with breathing problems should avoid long exertion outdoors.", "Annehmbare Luft: Wer Atemprobleme hat, sollte lange Anstrengung im Freien meiden.", "Air acceptable : les personnes ayant des problèmes respiratoires devraient éviter les efforts prolongés en plein air.", "Aire aceptable: quien tenga problemas respiratorios debería evitar el esfuerzo prolongado al aire libre.")
    banda == AirBand.MEDIA -> tr("Chi è sensibile faccia attenzione: meglio rimandare l'attività intensa all'aperto.", "Sensitive people should take care: better to put off intense activity outdoors.", "Empfindliche Menschen sollten aufpassen: intensive Aktivität im Freien besser verschieben.", "Les personnes sensibles doivent faire attention : mieux vaut reporter l'activité intense en plein air.", "Las personas sensibles deben tener cuidado: mejor aplazar la actividad intensa al aire libre.")
    banda == AirBand.SCARSA -> tr("Aria scarsa: limitare lo sforzo all'aperto, soprattutto nelle ore centrali.", "Poor air: limit exertion outdoors, especially in the middle of the day.", "Schlechte Luft: Anstrengung im Freien begrenzen, vor allem in den Mittagsstunden.", "Air mauvais : limiter les efforts en plein air, surtout en milieu de journée.", "Aire malo: limitar el esfuerzo al aire libre, sobre todo en las horas centrales.")
    else -> tr("Aria pessima: restare al chiuso quando possibile e tenere le finestre chiuse.", "Very bad air: stay indoors when possible and keep the windows shut.", "Sehr schlechte Luft: wenn möglich drinnen bleiben und die Fenster geschlossen halten.", "Air très mauvais : rester à l'intérieur si possible et garder les fenêtres fermées.", "Aire muy malo: quedarse en interiores cuando sea posible y mantener las ventanas cerradas.")
}
