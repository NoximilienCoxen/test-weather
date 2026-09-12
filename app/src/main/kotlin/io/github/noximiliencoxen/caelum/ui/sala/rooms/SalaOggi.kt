package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.data.badgeLabel
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.WeatherViewModel
import io.github.noximiliencoxen.caelum.ui.common.MinTouchTarget
import io.github.noximiliencoxen.caelum.ui.motion.VibrazioniDellaScena
import io.github.noximiliencoxen.caelum.ui.motion.rememberVibrazioniMeteo
import io.github.noximiliencoxen.caelum.ui.home.MoonPhase
import io.github.noximiliencoxen.caelum.ui.sala.Didascalia
import io.github.noximiliencoxen.caelum.ui.sala.BarraDelleOre
import io.github.noximiliencoxen.caelum.ui.sala.LocalAcquerello
import io.github.noximiliencoxen.caelum.ui.sala.Scena
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoomScaffold
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.giroConLancio
import io.github.noximiliencoxen.caelum.ui.sala.label
import io.github.noximiliencoxen.caelum.ui.sala.rememberGiro
import io.github.noximiliencoxen.caelum.ui.sala.rememberTempoScena
import io.github.noximiliencoxen.caelum.ui.sala.cieloStellato
import io.github.noximiliencoxen.caelum.ui.sala.mollaScena
import io.github.noximiliencoxen.caelum.ui.sala.pulviscolo
import io.github.noximiliencoxen.caelum.ui.sala.riverbero
import io.github.noximiliencoxen.caelum.ui.sala.salaBody
import io.github.noximiliencoxen.caelum.ui.sala.scenaBersaglio
import io.github.noximiliencoxen.caelum.ui.sala.salaConditionOf
import io.github.noximiliencoxen.caelum.ui.sala.salaPhaseOf
import io.github.noximiliencoxen.caelum.ui.sala.salaTitle
import io.github.noximiliencoxen.caelum.ui.sala.scultura
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Sala I — Oggi: la stanza di sempre, sotto una carta nuova.
 *
 * La scultura del meteo (sole o luna, nuvole, pioggia o grandine) resta
 * girabile in orizzontale, come tutto il resto dell'app; la barra delle
 * ventiquattro ore sceglie l'ora — e con lei tutte le altre sale, perche' il
 * giorno e' un asse che attraversa la galleria intera.
 */
@Composable
fun SalaOggiScreen(
    state: UiState,
    /** Il cielo **gia' smorzato** da `MeteoApp`, lo stesso che tinge la carta.
     *  Prima questa sala se lo ricostruiva dai valori grezzi, e leggeva quindi
     *  un cielo mezzo passo avanti a quello che aveva sotto. */
    sky: SkyState,
    palette: SalaPalette,
    position: () -> Float,
    viewModel: WeatherViewModel,
    onPlaceClick: () -> Unit,
    onMenuClick: () -> Unit,
    /** Vero solo quando questa e' la sala che si sta guardando. */
    inVista: Boolean,
) {
    val phase = salaPhaseOf(sky)
    val condition = salaConditionOf(state.forcedWeatherCode ?: state.hour?.weatherCode)
    val hours = state.hours
    val hour = state.hour
    val activeAlerts = remember(state.shownAlerts, hour?.time) { state.shownAlerts.activeAt(hour?.time) }

    // ── La scena, in numeri che scorrono ─────────────────────────────────────
    val bersaglio = remember(sky, condition, hour) {
        scenaBersaglio(
            sky = sky,
            condition = condition,
            nevica = Wmo.family(state.forcedWeatherCode ?: hour?.weatherCode) == Wmo.Family.NEVE,
            coperturaOraria = hour?.cloudCover,
            pioggiaMm = hour?.precipitation,
        )
    }
    val m = mollaScena(state.animazioniIstantanee, state.animazioniRidotte)
    val scena = Scena(
        sole = animateFloatAsState(bersaglio.sole, m, label = "sole").value,
        copertura = animateFloatAsState(bersaglio.copertura, m, label = "copertura").value,
        tempesta = animateFloatAsState(bersaglio.tempesta, m, label = "tempesta").value,
        bagnato = animateFloatAsState(bersaglio.bagnato, m, label = "bagnato").value,
        ghiaccio = animateFloatAsState(bersaglio.ghiaccio, m, label = "ghiaccio").value,
        neve = animateFloatAsState(bersaglio.neve, m, label = "neve").value,
        notte = animateFloatAsState(bersaglio.notte, m, label = "notte").value,
    )

    // ── L'orologio della scena ───────────────────────────────────────────────
    //
    // **I valori animati possono solo allungarlo, mai accorciarlo**, ed e' una
    // regola precisa, non una cautela. Se la condizione di accensione si
    // ricalcolasse dai soli valori animati, si ribalterebbe a meta' transizione:
    // gli uccelli si congelerebbero a mezz'aria prima di svanire, e la pioggia
    // ripartirebbe da capo mentre sfuma. Quindi: il **bersaglio** dice se ci
    // sara' qualcosa da muovere, e i valori animati tengono acceso finche' un
    // passaggio e' ancora in volo.
    val siMuoveBersaglio = !state.animazioniRidotte &&
        (bersaglio.bagnato > 0.01f || bersaglio.tempesta > 0.01f ||
            bersaglio.notte > 0.01f || bersaglio.copertura < 0.99f)
    val tempo = rememberTempoScena(attivo = inVista && (siMuoveBersaglio || scena.inTransito))

    // Il telefono sente cio' che cade. Tace se la sala non e' in vista, se
    // l'orologio e' fermo, o se l'interruttore delle animazioni e' giu'.
    VibrazioniDellaScena(
        scena = scena,
        tempo = tempo,
        attiva = inVista && !state.animazioniRidotte && !state.animazioniIstantanee,
    )

    SalaRoomScaffold(
        palette = palette,
        room = SalaRoom.OGGI,
        placeName = state.place.name,
        position = position,
        onPlaceClick = onPlaceClick,
        onMenuClick = onMenuClick,
    ) { modifier ->
        Column(
            modifier = modifier.drawBehind {
                // **Il cielo sta dietro tutto e occupa tutta la pagina.**
                // Prima le stelle erano dieci, misurate in unita' della
                // scultura e disegnate dentro la sua cassa: erano un ornamento
                // attorno a un oggetto. E c'erano solo a cielo sereno, cioe'
                // proprio dove contano meno - le nuvole non spengono le
                // stelle, le coprono, e da sotto una notte coperta qualcuna si
                // vede lo stesso. Qui il velo **cala** con la copertura invece
                // di azzerarsi.
                cieloStellato(
                    tempo = tempo(),
                    inchiostro = SalaTokens.neutral100,
                    velo = scena.notte * (1f - scena.copertura * 0.72f),
                )
                pulviscolo(
                    tempo = tempo(),
                    inchiostro = palette.ink,
                    velo = (1f - scena.notte) * (1f - scena.copertura) * (1f - scena.bagnato),
                )
                // Il riverbero del lampo si prende la pagina intera: un
                // temporale non illumina solo la nuvola che lo fa.
                riverbero(tempo(), SalaTokens.neutral100, forza = scena.tempesta)
            },
        ) {
            AlertsBlock(activeAlerts, palette)

            Column(
                modifier = Modifier.weight(1f, fill = true).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Sculpture(
                    scena = scena,
                    palette = palette,
                    tempo = tempo,
                    giroImposto = state.forcedYawDeg,
                )
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = hour?.temperature?.let { state.unit.from(it).roundToInt().toString() } ?: "--",
                        style = SalaType.giant(96),
                        color = palette.ink,
                    )
                    Text(text = "°", style = SalaType.giant(32), color = palette.ink)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = salaTitle(condition, phase), style = SalaType.cardTitle, color = palette.ink)
                val timeLabel = hour?.time?.hour?.let { "%02d:00".format(it) } ?: "--:--"
                val apparent = hour?.apparent?.roundToInt()
                Text(
                    text = "$timeLabel · ${condition.label()} · ${phase.label()}" +
                        (apparent?.let { " · percepiti $it°" } ?: ""),
                    style = SalaType.sectionLabel,
                    color = palette.inkAccent,
                )
                Didascalia(salaBody(condition), palette)
            }

            // Il ritorno al presente compare **solo quando serve**: se si sta
            // gia' guardando adesso, un tasto che riporta ad adesso e' un
            // comando che non fa niente, e un comando che non fa niente insegna
            // a non fidarsi degli altri. `backToNow` rimette a posto tutti e
            // due gli assi, ora e giorno.
            val lontanoDalPresente = state.selectedHour != state.nowIndex || state.selectedDay != 0
            if (lontanoDalPresente) {
                Text(
                    text = "Torna ad adesso",
                    style = SalaType.sectionLabel,
                    color = palette.inkAccent,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .height(MinTouchTarget)
                        .clickable(onClick = viewModel::backToNow)
                        .padding(top = 10.dp),
                )
            }

            val vibrazioni = rememberVibrazioniMeteo()
            BarraDelleOre(
                hours = hours,
                selected = state.selectedHour,
                palette = palette,
                onSelect = viewModel::selectHour,
                onTick = { if (!state.animazioniRidotte) vibrazioni.scatto() },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Le allerte in corso all'ora mostrata, la piu' grave per prima. */
private fun List<WeatherAlert>.activeAt(moment: java.time.LocalDateTime?): List<WeatherAlert> {
    if (moment == null) return this
    return filter { alert ->
        val afterOnset = alert.onset?.let { !moment.isBefore(it) } ?: true
        val beforeExpiry = alert.expires?.let { !moment.isAfter(it) } ?: true
        afterOnset && beforeExpiry
    }.sortedByDescending { it.level.weight }
}

@Composable
private fun AlertsBlock(alerts: List<WeatherAlert>, palette: SalaPalette) {
    Column(
        modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        if (alerts.isEmpty()) {
            Text(text = "Nessun avviso in corso", style = SalaType.sectionLabel, color = palette.inkSoft)
        } else {
            alerts.take(3).forEach { alert ->
                val tint = if (alert.level.weight >= 2) SalaTokens.accent2_700 else palette.inkAccent
                Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .size(if (alert.level.weight >= 2) 9.dp else 5.dp, 9.dp)
                            .background(tint),
                    )
                    Column {
                        Text(text = alert.badgeLabel, style = SalaType.sectionLabel, color = tint)
                        Text(
                            text = alert.headline,
                            style = SalaType.body,
                            color = palette.inkSoft,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Sculpture(
    scena: Scena,
    palette: SalaPalette,
    tempo: () -> Float,
    giroImposto: Float?,
) {
    // La fase e' quella vera di stanotte, la stessa che calcola Sala IV: le due
    // stanze non possono raccontare due lune diverse nella stessa notte.
    val faseLunare = remember { MoonPhase.at(LocalDate.now()) }
    val acquerello = LocalAcquerello.current
    val giroAnim = rememberGiro(giroImposto)

    // Il giro si legge **dentro il disegno**, non in composizione: e' un gesto
    // continuo che produce centinaia di gradi, e letto fuori ricomporrebbe
    // l'albero a ogni fotogramma del dito invece di ridipingere e basta.
    val giro = { giroAnim.gradi }

    Canvas(
        // Solo orizzontale: il verticale e' del carosello fra le sale.
        // Il gesto - verso, inerzia, ritorno - sta in `giroConLancio`, che lo
        // condivide con la luna di Sala IV.
        modifier = Modifier
            .size(280.dp, 240.dp)
            .giroConLancio(giroAnim),
    ) {
        scultura(
            acquerello = acquerello,
            scena = scena,
            palette = palette,
            giroDeg = giro(),
            fase = faseLunare,
            tempo = tempo(),
        )
    }
}
