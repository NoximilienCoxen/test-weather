package com.caelumweather.ui.sala.rooms

import com.caelumweather.ui.sala.cedevole
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr
import com.caelumweather.data.TendenzaPressione
import com.caelumweather.data.pressioneAlle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.caelumweather.data.Wmo
import com.caelumweather.ui.UiState
import com.caelumweather.ui.sala.Didascalia
import com.caelumweather.ui.sala.NumeroCheScorre
import com.caelumweather.ui.sala.angoloCheGira
import com.caelumweather.ui.sala.misuraCheArriva
import com.caelumweather.ui.sala.PannelloSala
import com.caelumweather.ui.sala.RigaSenzaOre
import com.caelumweather.ui.sala.SalaPalette
import com.caelumweather.ui.sala.SalaTokens
import com.caelumweather.ui.sala.SalaType
import com.caelumweather.ui.sala.oraDueCifre
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.caelumweather.ui.motion.bussolaDisponibile
import com.caelumweather.ui.motion.rememberBussola
import com.caelumweather.ui.motion.rememberVibrazioniMeteo
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Sala VI — Il vento: da dove viene, quanto forte, e cosa fa nelle prossime sei ore.
 *
 * La rosa non e' un grafico ma un **verso**: la lancetta punta da dove il vento
 * arriva, che e' il modo in cui lo si dice a voce ("viene da nord-est") e non
 * quello in cui lo scrive il dato (i gradi di provenienza).
 */
@Composable
fun SalaVentoScreen(
    state: UiState,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
    /** Falso con le animazioni ridotte: niente colpetti ai punti cardinali. */
    movimento: Boolean = true,
) {
    // Le ore del giorno mostrato: vedi la nota in Sala III.
    val ore = state.shownHours
    val scelta = state.selectedHour
    val ora = state.detailHour
    val unita = state.windUnit
    val velocita = ora?.windSpeed
    val raffiche = ora?.windGusts
    val direzione = ora?.windDirection

    val context = LocalContext.current
    val conBussola = remember(context) { bussolaDisponibile(context) }
    var bussolaAccesa by rememberSaveable { mutableStateOf(false) }
    var sguardoManuale by rememberSaveable { mutableFloatStateOf(0f) }
    val bussola by rememberBussola(accesa = bussolaAccesa && conBussola)
    val sguardo = if (bussolaAccesa) bussola ?: 0f else sguardoManuale

    // Un colpetto a ogni punto cardinale attraversato: e' cosi' che la mano
    // sente il nord senza guardare.
    val vibrazioni = rememberVibrazioniMeteo()
    val quadrante = ((sguardo + 45f) / 90f).toInt() % 4
    var quadrantePrima by remember { mutableIntStateOf(quadrante) }
    LaunchedEffect(quadrante) {
        if (quadrante != quadrantePrima && movimento) vibrazioni.scatto()
        quadrantePrima = quadrante
    }

    PannelloSala(palette = palette, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(ROSA)
                    .pointerInput(bussolaAccesa) {
                        if (bussolaAccesa) return@pointerInput
                        detectDragGestures { cambio, _ ->
                            cambio.consume()
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val prima = cambio.previousPosition - c
                            val dopo = cambio.position - c
                            val delta = Math.toDegrees(
                                (atan2(dopo.y, dopo.x) - atan2(prima.y, prima.x)).toDouble(),
                            ).toFloat()
                            // Il gesto sta prima della rotazione, quindi i
                            // punti sono quelli dello schermo e non girano con
                            // la rosa. Il dito che gira in senso orario
                            // gira la rosa in senso orario, cioe' fa guardare
                            // di tanto in senso antiorario.
                            val scarto = ((delta + 540f) % 360f) - 180f
                            sguardoManuale = ((sguardoManuale - scarto) % 360f + 360f) % 360f
                        }
                    }
                    // **La rosa gira, e con lei le lettere e la lancetta.**
                    // Con la bussola accesa la gira il telefono: il nord della
                    // rosa resta sul nord vero, e la lancetta indica da dove
                    // il vento arriva rispetto a dove si sta guardando. Spenta,
                    // la si gira col dito, come una bussola di carta.
                    .rotate(-sguardo)
                    .clip(CircleShape)
                    .background(palette.chip)
                    .semantics {
                        contentDescription = tr("Rosa dei venti. ", "Wind rose. ", "Windrose. ", "Rose des vents. ", "Rosa de los vientos. ") + (
                            if (bussolaAccesa) tr("Segue la bussola del telefono.", "It follows the phone's compass.", "Sie folgt dem Kompass des Telefons.", "Elle suit la boussole du téléphone.", "Sigue la brújula del teléfono.") else tr("Trascina per girarla.", "Drag to turn it.", "Ziehen, um sie zu drehen.", "Faites glisser pour la tourner.", "Arrastra para girarla.")
                            )
                    },
                contentAlignment = Alignment.Center,
            ) {
                listOf("N" to Alignment.TopCenter, "S" to Alignment.BottomCenter,
                    tr("O", "W", "W", "O", "O") to Alignment.CenterStart,
                    // In tedesco l'est e' la O di Ost: vedi `Wmo.windDirection`.
                    tr("E", "E", "O", "E", "E") to Alignment.CenterEnd).forEach { (lettera, dove) ->
                    Text(
                        text = lettera,
                        style = SalaType.microLabel,
                        color = if (lettera == "N") palette.accent else palette.inkFaint,
                        modifier = Modifier.align(dove).padding(8.dp),
                    )
                }
                Canvas(
                    modifier = Modifier
                        .size(ROSA)
                        // Cambiando ora la lancetta gira fin li', per la via
                        // piu' corta, invece di saltarci.
                        .rotate(angoloCheGira((direzione?.toFloat() ?: 0f) + 180f)),
                ) {
                    // La lancetta parte dal centro e va verso l'alto; la
                    // rotazione la porta dove serve. Mezzo giro in piu' perche'
                    // il dato dice **da dove viene**, non dove va.
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawRoundRect(
                        color = palette.accent,
                        topLeft = Offset(cx - 2.5.dp.toPx(), cy - size.height * 0.42f),
                        size = Size(5.dp.toPx(), size.height * 0.42f),
                        cornerRadius = CornerRadius(2.5.dp.toPx()),
                    )
                    drawCircle(color = palette.accent, radius = 7.dp.toPx())
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tr("Il vento", "Wind", "Der Wind", "Le vent", "El viento"), style = SalaType.cardTitle, color = palette.ink)
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    val nodi = velocita?.let { unita.from(it).roundToInt() }
                    NumeroCheScorre(
                        testo = nodi?.toString() ?: "--",
                        valore = nodi,
                        style = SalaType.numeroSala,
                        color = palette.ink,
                    )
                    Text(
                        text = "${unita.label} ${Wmo.windDirection(direzione)}",
                        style = SalaType.hourLabel,
                        color = palette.accent,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
                Didascalia(
                    // Le soglie vogliono i km/h; il display vuole l'unita'
                    // scelta. Passarli mescolati faceva dire "burrasca" a
                    // sessanta nodi e "brezza" a sessanta km/h.
                    nota(
                        kmh = velocita?.let { it * 3.6 },
                        raffiche = raffiche?.let { unita.from(it).roundToInt() },
                        unita = unita.label,
                    ),
                    palette,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        RigaBussola(
            sguardo = sguardo,
            direzione = direzione,
            conBussola = conBussola,
            bussolaAccesa = bussolaAccesa,
            onBussola = { bussolaAccesa = it },
            palette = palette,
            modifier = Modifier.padding(top = 14.dp),
        )

        Text(
            text = tr("LE PROSSIME SEI ORE", "THE NEXT SIX HOURS", "DIE NÄCHSTEN SECHS STUNDEN", "LES SIX PROCHAINES HEURES", "LAS PRÓXIMAS SEIS HORAS"),
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
        )
        val finestra = (scelta + 1..scelta + 6).filter { it in ore.indices }
        if (ore.isEmpty()) RigaSenzaOre(palette)
        val massimo = finestra.mapNotNull { ore[it].windSpeed }.maxOrNull()?.coerceAtLeast(0.5) ?: 1.0
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            finestra.forEach { indice ->
                val ms = ore[indice].windSpeed ?: 0.0
                val quota = (ms / massimo).coerceIn(0.0, 1.0).toFloat()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.chip)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = oraDueCifre(ore[indice].time.hour),
                        style = SalaType.microLabel,
                        color = palette.inkSoft,
                    )
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(misuraCheArriva((10f + quota * 36f).dp))
                            .clip(CircleShape)
                            .background(
                                if (unita.from(ms) > 24) SalaTokens.accent500 else SalaTokens.verde400,
                            ),
                    )
                    Text(
                        text = unita.from(ms).roundToInt().toString(),
                        style = SalaType.giornoMax,
                        color = palette.ink,
                    )
                }
            }
        }

        // ── La pressione ─────────────────────────────────────────────────────
        //
        // Sta col vento perche' il vento e' la pressione che si muove: e' la
        // differenza fra due barometri a far correre l'aria. Il numero da solo
        // dice poco, quindi la riga lo accompagna sempre con dove sta andando.
        val pressione = remember(state.forecast, ora?.time) {
            ora?.time?.let { pressioneAlle(state.forecast?.allHours.orEmpty(), it) }
        }
        if (pressione != null) {
            Text(
                text = tr("LA PRESSIONE", "PRESSURE", "LUFTDRUCK", "LA PRESSION", "LA PRESIÓN"),
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
            )
            Text(
                text = "${pressione.hPa.roundToInt()} hPa" +
                    (pressione.tendenza?.let { " · " + nomeTendenza(it) } ?: ""),
                style = SalaType.rowTitle,
                color = palette.ink,
            )
            pressione.scarto?.let { scarto ->
                Didascalia(
                    notaPressione(pressione.tendenza, scarto, pressione.inAvanti),
                    palette,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // ── Il mare ──────────────────────────────────────────────────────────
        //
        // Solo dove c'e': vedi `MareRepository`. Segue l'ora scelta come tutto
        // il resto della sala.
        state.mareMostrato?.let { oraDiMare ->
            SezioneMare(ora = oraDiMare, unita = state.unit, palette = palette, movimento = movimento)
        }
    }
}

private fun nomeTendenza(t: TendenzaPressione): String = when (t) {
    TendenzaPressione.SALE_IN_FRETTA -> tr("sale in fretta", "rising fast", "steigt schnell", "monte vite", "sube deprisa")
    TendenzaPressione.SALE -> tr("in salita", "rising", "steigend", "en hausse", "subiendo")
    TendenzaPressione.STABILE -> tr("stabile", "steady", "stabil", "stable", "estable")
    TendenzaPressione.SCENDE -> tr("in calo", "falling", "fallend", "en baisse", "bajando")
    TendenzaPressione.SCENDE_IN_FRETTA -> tr("cala in fretta", "falling fast", "fällt schnell", "baisse vite", "baja deprisa")
}

/**
 * Cosa vuol dire, detto con prudenza: "di solito", perche' il barometro e' un
 * indizio e non una previsione - la previsione vera sta nelle altre sale.
 */
private fun notaPressione(t: TendenzaPressione?, scarto: Double, inAvanti: Boolean): String {
    val quanto = String.format(Lingue.locale, "%+.1f", scarto)
    val senso = when (t) {
        TendenzaPressione.SCENDE_IN_FRETTA ->
            tr("Di solito annuncia vento in rinforzo o un peggioramento.", "Usually a sign of rising wind or worsening weather.", "Meist ein Zeichen für auffrischenden Wind oder eine Verschlechterung.", "Annonce en général un renforcement du vent ou une dégradation.", "Suele anunciar viento en aumento o un empeoramiento.")
        TendenzaPressione.SCENDE -> tr("Il tempo tende a farsi più incerto.", "The weather tends to turn less settled.", "Das Wetter wird tendenziell unbeständiger.", "Le temps tend à devenir plus incertain.", "El tiempo tiende a volverse más inestable.")
        TendenzaPressione.SALE -> tr("Il tempo tende a stabilizzarsi.", "The weather tends to settle.", "Das Wetter beruhigt sich tendenziell.", "Le temps tend à se stabiliser.", "El tiempo tiende a estabilizarse.")
        TendenzaPressione.SALE_IN_FRETTA ->
            tr("Si rasserena, ma spesso con vento teso per qualche ora.", "Clearing, often with a brisk wind for a few hours.", "Es klart auf, oft aber mit frischem Wind für einige Stunden.", "Le ciel se dégage, mais souvent avec un vent soutenu pendant quelques heures.", "Despeja, pero a menudo con viento vivo durante unas horas.")
        TendenzaPressione.STABILE, null -> tr("Nessun cambio in vista dal barometro.", "No change in sight from the barometer.", "Vom Barometer her keine Änderung in Sicht.", "Aucun changement en vue d'après le baromètre.", "Ningún cambio a la vista según el barómetro.")
    }
    val quando = if (inAvanti) {
        tr("$quanto hPa nelle prossime tre ore. ", "$quanto hPa over the next three hours. ", "$quanto hPa in den nächsten drei Stunden. ", "$quanto hPa dans les trois prochaines heures. ", "$quanto hPa en las próximas tres horas. ")
    } else {
        tr("$quanto hPa nelle ultime tre ore. ", "$quanto hPa over the last three hours. ", "$quanto hPa in den letzten drei Stunden. ", "$quanto hPa au cours des trois dernières heures. ", "$quanto hPa en las últimas tres horas. ")
    }
    return quando + senso
}

/**
 * Dove si sta guardando, da dove arriva il vento rispetto a quello, e
 * l'interruttore della bussola - che c'e' solo se il telefono ne ha una.
 */
@Composable
private fun RigaBussola(
    sguardo: Float,
    direzione: Double?,
    conBussola: Boolean,
    bussolaAccesa: Boolean,
    onBussola: (Boolean) -> Unit,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (bussolaAccesa) tr("GUARDI VERSO ${Wmo.windDirection(sguardo.toDouble())}", "FACING ${Wmo.windDirection(sguardo.toDouble())}", "BLICK NACH ${Wmo.windDirection(sguardo.toDouble())}", "VOUS REGARDEZ VERS ${Wmo.windDirection(sguardo.toDouble())}", "MIRAS HACIA ${Wmo.windDirection(sguardo.toDouble())}") else tr("GIRA LA ROSA COL DITO", "TURN THE ROSE WITH YOUR FINGER", "DREHE DIE ROSE MIT DEM FINGER", "TOURNEZ LA ROSE AVEC LE DOIGT", "GIRA LA ROSA CON EL DEDO"),
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
            )
            if (direzione != null) {
                val lato = latoDelVento(ventoRispettoASguardo(direzione.toFloat(), sguardo))
                Text(
                    text = if (bussolaAccesa) {
                        lato.replaceFirstChar { it.uppercase() }
                    } else {
                        tr("Guardando verso ${Wmo.windDirection(sguardo.toDouble())}, $lato", "Facing ${Wmo.windDirection(sguardo.toDouble())}, $lato", "Mit Blick nach ${Wmo.windDirection(sguardo.toDouble())}: $lato", "En regardant vers ${Wmo.windDirection(sguardo.toDouble())}, $lato", "Mirando hacia ${Wmo.windDirection(sguardo.toDouble())}, $lato")
                    },
                    style = SalaType.rowTitle,
                    color = palette.ink,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (conBussola) {
            Text(
                text = if (bussolaAccesa) tr("BUSSOLA ACCESA", "COMPASS ON", "KOMPASS AN", "BOUSSOLE ACTIVE", "BRÚJULA ACTIVA") else tr("USA LA BUSSOLA", "USE THE COMPASS", "KOMPASS NUTZEN", "UTILISER LA BOUSSOLE", "USAR LA BRÚJULA"),
                style = SalaType.sectionLabel,
                color = if (bussolaAccesa) palette.accentInk else palette.accent,
                modifier = Modifier
                    .cedevole()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (bussolaAccesa) palette.accent else palette.chip)
                    .clickable { onBussola(!bussolaAccesa) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private val ROSA = 120.dp

/**
 * Cosa vuol dire questa velocita', in cose che si vedono fuori.
 *
 * Le soglie sono quelle della scala Beaufort ridotte a tre gradini, e sono in
 * chilometri orari **qualunque** unita' si stia mostrando: il fenomeno non
 * cambia col modo di misurarlo.
 */
private fun nota(kmh: Double?, raffiche: Int?, unita: String): String {
    val coda = raffiche?.let { tr(" Raffiche fino a $it $unita.", " Gusts up to $it $unita.", " Böen bis $it $unita.", " Rafales jusqu'à $it $unita.", " Rachas de hasta $it $unita.") } ?: ""
    val corpo = when {
        kmh == null -> tr("Vento non disponibile per quest'ora.", "Wind unavailable for this hour.", "Wind für diese Stunde nicht verfügbar.", "Vent indisponible pour cette heure.", "Viento no disponible para esta hora.")
        kmh < 6 -> tr("Aria quasi ferma: il fumo sale dritto.", "Almost still air: smoke rises straight up.", "Fast unbewegte Luft: Rauch steigt gerade auf.", "Air presque immobile : la fumée monte tout droit.", "Aire casi quieto: el humo sube recto.")
        kmh < 20 -> tr("Brezza leggera: si muovono le foglie, non i rami.", "Light breeze: leaves move, branches do not.", "Leichte Brise: Blätter bewegen sich, Äste nicht.", "Brise légère : les feuilles bougent, pas les branches.", "Brisa ligera: se mueven las hojas, no las ramas.")
        kmh < 39 -> tr("Vento teso: i rami si piegano e l'ombrello diventa scomodo.", "Fresh wind: branches bend and an umbrella gets awkward.", "Frischer Wind: Äste biegen sich, und der Regenschirm wird unhandlich.", "Vent soutenu : les branches plient et le parapluie devient gênant.", "Viento fresco: las ramas se doblan y el paraguas se vuelve incómodo.")
        kmh < 62 -> tr("Vento forte: attenzione agli oggetti esposti sui balconi.", "Strong wind: watch out for loose things on balconies.", "Starker Wind: Vorsicht mit losen Gegenständen auf Balkonen.", "Vent fort : attention aux objets exposés sur les balcons.", "Viento fuerte: cuidado con los objetos sueltos en los balcones.")
        else -> tr("Burrasca: meglio non stare sotto gli alberi.", "Gale: better not to stand under trees.", "Sturm: besser nicht unter Bäumen stehen.", "Coup de vent : mieux vaut ne pas rester sous les arbres.", "Temporal: mejor no quedarse bajo los árboles.")
    }
    return corpo + coda
}
