package io.github.noximiliencoxen.caelum.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.PrecipKind
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.asBigDegrees
import io.github.noximiliencoxen.caelum.ui.asCentimetres
import io.github.noximiliencoxen.caelum.ui.asIndex
import io.github.noximiliencoxen.caelum.ui.asMetresPerSecond
import io.github.noximiliencoxen.caelum.ui.asMillimetres
import io.github.noximiliencoxen.caelum.ui.asPercent
import io.github.noximiliencoxen.caelum.ui.asPlainDegrees
import io.github.noximiliencoxen.caelum.ui.alerts.AlertBanner
import io.github.noximiliencoxen.caelum.ui.common.EditorialHeader
import io.github.noximiliencoxen.caelum.ui.common.MeteoLayout
import io.github.noximiliencoxen.caelum.ui.common.MetricsBar
import io.github.noximiliencoxen.caelum.ui.common.MinTouchTarget
import io.github.noximiliencoxen.caelum.ui.home.BarSwitch
import io.github.noximiliencoxen.caelum.ui.home.HourBar
import io.github.noximiliencoxen.caelum.ui.home.MoonPhase
import io.github.noximiliencoxen.caelum.ui.home.MoonSegment
import io.github.noximiliencoxen.caelum.ui.home.WeekBar
import io.github.noximiliencoxen.caelum.ui.motion.PhysicalNumber
import io.github.noximiliencoxen.caelum.ui.motion.SceneRotation
import io.github.noximiliencoxen.caelum.ui.motion.rememberSceneRotation
import io.github.noximiliencoxen.caelum.ui.motion.rotatesScene
import io.github.noximiliencoxen.caelum.ui.render3d.Camera
import io.github.noximiliencoxen.caelum.ui.render3d.MOON_SEAS
import io.github.noximiliencoxen.caelum.ui.render3d.glow
import io.github.noximiliencoxen.caelum.ui.render3d.moon
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.MeteoType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Una sezione del feed: un blocco della colonna, non piu' una schermata piena.
 *
 * **Il cambio di forma e' il cambio di idea.** Prima ogni grandezza aveva una
 * schermata tutta sua, con un eroe grande in mezzo e tre numeri in fondo: sei
 * pagine da sfogliare, ognuna che si prendeva l'attenzione per intero. Adesso
 * l'eroe e' uno solo ed e' la scena dipinta in cima; queste sono le voci del
 * documento che le sta sotto, e si misurano **sul proprio contenuto** invece che
 * sullo schermo.
 *
 * Da qui due conseguenze pratiche. Le altezze non sono piu' frazioni di quel che
 * avanza ma punti dichiarati: in una colonna non c'e' nessun avanzo da dividere.
 * E i corpi che erano eroi - la finestra della pioggia, la sfera della luna, la
 * cifra estrusa - restano, ma alti quanto una figura dentro un articolo. Non
 * sono stati buttati: erano la parte migliore di quel che c'era, e in una
 * colonna una figura ci sta benissimo.
 *
 * I colori vengono da `LocalMeteoColors`, che dentro [GlassPanel] e' gia' quella
 * ricavata dal vetro: qui non c'e' niente da sapere sul fondo.
 */
@Composable
internal fun SectionBlock(
    section: FeedSection,
    state: UiState,
    tilt: State<Offset>,
    layout: MeteoLayout,
    onSelectHour: (Int) -> Unit,
    onSelectDay: (Int) -> Unit,
    onBackToNow: () -> Unit,
    onSetWeek: (Boolean) -> Unit,
    onOpenAlerts: () -> Unit,
    onDismissAlerts: () -> Unit,
    onReopenAlerts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation: SceneRotation = rememberSceneRotation()
    LaunchedEffect(state.forcedYawDeg) { rotation.pin(state.forcedYawDeg) }

    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = layout.gutter, vertical = 6.dp),
    ) {
        val accent = section.accentOf(rememberSkyAccents(), LocalMeteoColors.current.text)

        EditorialHeader(
            kicker = moment(state, section),
            title = section.title,
            accent = accent,
            modifier = Modifier.padding(horizontal = PAD),
        )

        Spacer(Modifier.height(10.dp))

        when (section) {
            FeedSection.TEMPERATURA -> Temperature(
                state = state,
                rotation = rotation,
                tilt = tilt,
                onSelectHour = onSelectHour,
                onSelectDay = onSelectDay,
                onBackToNow = onBackToNow,
                onSetWeek = onSetWeek,
                onOpenAlerts = onOpenAlerts,
                onDismissAlerts = onDismissAlerts,
                onReopenAlerts = onReopenAlerts,
            )

            FeedSection.PRECIPITAZIONI -> Rain(
                state = state,
                rotation = rotation,
                tilt = tilt,
                accent = accent,
                layout = layout,
                onSelectHour = onSelectHour,
            )

            FeedSection.LUNA -> MoonBody(
                date = state.pageDay?.date ?: LocalDate.now(),
                rotation = rotation,
                tilt = tilt,
                light = accent,
                modifier = Modifier.fillMaxWidth().height(MOON_HEIGHT),
            )

            else -> Placeholder(
                section = section,
                state = state,
                rotation = rotation,
                tilt = tilt,
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionNumbers(
            section = section,
            state = state,
            accent = accent,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PAD),
        )
    }
}

/**
 * La temperatura: la cifra girabile, le ventiquattro ore, la settimana.
 *
 * **E' quel che restava di `HomeScreen`**, tolti la scultura e l'intestazione.
 * La scultura l'ha sostituita la scena dipinta - erano la stessa cosa detta due
 * volte, e la seconda la diceva peggio - e l'intestazione e' salita in cima alla
 * colonna, dove vale per tutte le sezioni invece che per questa sola.
 */
@Composable
private fun Temperature(
    state: UiState,
    rotation: SceneRotation,
    tilt: State<Offset>,
    onSelectHour: (Int) -> Unit,
    onSelectDay: (Int) -> Unit,
    onBackToNow: () -> Unit,
    onSetWeek: (Boolean) -> Unit,
    onOpenAlerts: () -> Unit,
    onDismissAlerts: () -> Unit,
    onReopenAlerts: () -> Unit,
) {
    val colors = LocalMeteoColors.current
    val today = state.selectedDay == 0
    val hours = if (today) state.hours else state.shownHours
    val hour = state.detailHour

    // L'allerta sta in cima alla prima sezione, dove stava prima: e' l'unica
    // cosa che deve leggersi uguale a mezzanotte e a mezzogiorno, quindi
    // dipinge un fondo proprio e prende i colori dai pannelli.
    AnimatedVisibility(visible = !state.alertsCollapsed && state.shownAlerts.isNotEmpty()) {
        AlertBanner(
            alerts = state.shownAlerts,
            onOpen = onOpenAlerts,
            onDismiss = onDismissAlerts,
            modifier = Modifier.padding(horizontal = PAD, vertical = 4.dp),
        )
    }
    if (state.alertsCollapsed && state.shownAlerts.isNotEmpty()) {
        // Ridotto, l'avviso lascia una riga che lo riapre. Un gesto solo per
        // due effetti: chi la tocca vuole leggere il bollettino, e ritrovarselo
        // per esteso e' la risposta che non richiede di cercare come si fa.
        Text(
            text = "AVVISO IN CORSO",
            style = MeteoType.kicker,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Riapri il bollettino", onClick = onReopenAlerts)
                .padding(horizontal = PAD, vertical = 8.dp),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(NUMBER_HEIGHT)
            .rotatesScene(rotation),
        contentAlignment = Alignment.Center,
    ) {
        val degrees = hour?.temperature
        if (degrees != null) {
            PhysicalNumber(
                text = degrees.asBigDegrees(state.unit),
                smallTail = 1,
                fontSize = NUMBER_HEIGHT * 0.82f,
                rotation = rotation,
                tilt = tilt,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SkyMessage(
                title = if (state.error != null) state.error.uppercase() else "IN ATTESA DEI DATI",
                message = null,
            )
        }
    }

    // La condizione con la dissolvenza: scorrendo le ore cambia spesso, e uno
    // scatto di testo si nota piu' del testo stesso.
    Crossfade(
        targetState = conditionLabel(hour?.weatherCode, state.forcedWeatherCode),
        label = "condizione",
        modifier = Modifier.fillMaxWidth(),
    ) { label ->
        Text(
            text = label,
            style = MeteoType.label,
            color = colors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PAD),
        )
    }

    Spacer(Modifier.height(10.dp))
    BarSwitch(settimana = state.weekMode, onChoose = onSetWeek)

    val shownDay = state.detailDay ?: hour?.time?.let { state.forecast?.dayOf(it) }
    Box(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (state.weekMode) {
            WeekBar(
                days = state.forecast?.days.orEmpty(),
                unit = state.unit,
                selected = state.selectedDay,
                onOpenDay = onSelectDay,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        } else {
            HourBar(
                hours = hours,
                selected = state.selectedHour,
                nowIndex = if (today) state.nowIndex else -1,
                sunrise = shownDay?.sunrise,
                sunset = shownDay?.sunset,
                unit = state.unit,
                onSelect = onSelectHour,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }

    // **L'altezza si riserva anche da spento.** Comparendo e sparendo a ogni ora
    // scelta farebbe sussultare tutto quello che ha sotto, e in una colonna
    // quel sussulto si propaga fino in fondo.
    val onNow = today && state.selectedHour == state.nowIndex
    Box(
        modifier = Modifier.fillMaxWidth().height(MinTouchTarget),
        contentAlignment = Alignment.Center,
    ) {
        if (!onNow) {
            Text(
                text = if (today) "TORNA AD ADESSO" else "TORNA A OGGI",
                style = MeteoType.kicker,
                color = colors.text.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = if (today) "Torna all'ora attuale" else "Torna a oggi",
                        onClick = onBackToNow,
                    )
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/** La pioggia: la finestra sull'ora scelta, e le ventiquattro ore sotto. */
@Composable
private fun Rain(
    state: UiState,
    rotation: SceneRotation,
    tilt: State<Offset>,
    accent: Color,
    layout: MeteoLayout,
    onSelectHour: (Int) -> Unit,
) {
    val day = state.pageDay
    if (day == null || (day.precipitationSum == null && day.snowfallSum == null)) {
        val (title, message) = heroMissingReason(FeedSection.PRECIPITAZIONI, state)
        SkyMessage(title = title, message = message)
        return
    }
    RainWindow(
        hour = state.pageHour,
        day = day,
        forcedCode = state.forcedWeatherCode,
        rotation = rotation,
        tilt = tilt,
        alive = true,
        modifier = Modifier.fillMaxWidth().height(WINDOW_HEIGHT),
    )
    Spacer(Modifier.height(8.dp))
    RainHours(
        hours = state.pageHours,
        selectedHour = state.detailHour?.time?.hour,
        nowHour = state.nowHourOnShownDay,
        kind = precipKindOf(day, state.forcedWeatherCode),
        forcedCode = state.forcedWeatherCode,
        accent = accent,
        compact = layout.compact,
        onSelectHour = onSelectHour,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PAD),
    )
}

/**
 * Le sezioni ancora da fare: la cifra, e la riga che dice cosa arrivera'.
 *
 * Un segnaposto dichiarato e' un lavoro in corso; uno spazio muto e' un difetto.
 * Il riquadro tratteggiato che diceva questa stessa cosa non c'e' piu' - dentro
 * una colonna sarebbe un buco disegnato - e a dirla resta la riga.
 */
@Composable
private fun Placeholder(
    section: FeedSection,
    state: UiState,
    rotation: SceneRotation,
    tilt: State<Offset>,
) {
    val colors = LocalMeteoColors.current
    val value = heroValue(section, state)
    if (value == null) {
        val (title, message) = heroMissingReason(section, state)
        SkyMessage(title = title, message = message)
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(NUMBER_HEIGHT)
            .rotatesScene(rotation),
        contentAlignment = Alignment.Center,
    ) {
        PhysicalNumber(
            text = value,
            smallTail = heroSmallTail(section),
            fontSize = NUMBER_HEIGHT * 0.78f,
            rotation = rotation,
            tilt = tilt,
            modifier = Modifier.fillMaxSize(),
        )
    }
    val unit = unitLabelFor(section, state)
    if (unit.isNotBlank()) {
        Text(
            text = unit,
            style = MeteoType.kicker,
            color = colors.label,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(
        text = section.stage,
        style = MeteoType.body,
        color = colors.label,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PAD, vertical = 8.dp),
    )
}

/**
 * La luna, girabile col dito.
 *
 * **I mari girano, la mediana no**, e non e' un difetto: i mari stanno sulla
 * sfera e passano dalla camera, quindi ruotando scivolano verso il bordo; la
 * mediana la disegna [moon] in coordinate di schermo, perche' da che parte cada
 * lo decide il Sole e non chi guarda. Una falce che si raddrizza girando il
 * telefono sarebbe una luna che cambia fase perche' ci si e' spostati di venti
 * centimetri.
 */
@Composable
private fun MoonBody(
    date: LocalDate,
    rotation: SceneRotation,
    tilt: State<Offset>,
    light: Color,
    modifier: Modifier = Modifier,
) {
    val dark = LocalMeteoColors.current.line
    val phase = remember(date) { MoonPhase.at(date) }
    val spoken = remember(phase) {
        val percent = (MoonPhase.illumination(phase) * 100f).roundToInt()
        "${MoonSegment.of(phase).label.lowercase()}, illuminata al $percent per cento"
    }
    Box(modifier = modifier.rotatesScene(rotation), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = spoken },
        ) {
            val unit = minOf(size.width, size.height)
            val camera = Camera(
                yawDeg = rotation.yawDeg,
                pitchDeg = tilt.value.y * SHADOW_PITCH,
                distance = unit * 2.7f,
                origin = Offset(size.width / 2f, size.height / 2f),
            )
            val radius = unit * MOON_RADIUS
            glow(camera, 0f, 0f, 0f, radius, light, 0.28f, spread = 2.0f)
            moon(
                camera = camera,
                x = 0f, y = 0f, z = 0f,
                radius = radius,
                phase = phase,
                light = light,
                dark = dark,
                alpha = 1f,
                marks = MOON_SEAS,
            )
        }
    }
}

/** Cosa manca, e perche'. */
@Composable
private fun SkyMessage(title: String, message: String?, modifier: Modifier = Modifier) {
    val colors = LocalMeteoColors.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MeteoType.label,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MeteoType.body,
                color = colors.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/** I due o tre numeri che la sezione sa gia' dire. */
@Composable
private fun SectionNumbers(
    section: FeedSection,
    state: UiState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val day = state.pageDay
    val hour = state.pageHour
    val entries: List<Pair<String, String>> = when (section) {
        FeedSection.TEMPERATURA -> listOf(
            "MASSIMA" to day?.tempMax.asPlainDegrees(state.unit),
            "PERCEPITA" to hour?.apparent.asPlainDegrees(state.unit),
            "MINIMA" to day?.tempMin.asPlainDegrees(state.unit),
        )

        FeedSection.PRECIPITAZIONI -> {
            val kind = day?.let { precipKindOf(it, state.forcedWeatherCode) } ?: PrecipKind.NONE
            listOf(
                "TOTALE" to if (kind.isSnowy()) {
                    day?.snowfallSum.asCentimetres()
                } else {
                    day?.precipitationSum.asMillimetres()
                },
                "PROBABILITA'" to day?.precipProbability.asPercent(),
                // Il totale dice **quanta**, il picco dice **quanto in fretta**:
                // quaranta millimetri su tutto il giorno e quaranta in due ore
                // sono due giornate diverse con lo stesso totale.
                "PICCO" to (
                    peakPrecipitation(state.pageHours, kind.isSnowy())
                        ?.let { if (kind.isSnowy()) it.asCentimetres() else it.asMillimetres() }
                        ?.plus("/H")
                        ?: MISSING
                    ),
            )
        }

        FeedSection.ARIA -> listOf(
            "GIUDIZIO" to (state.air?.band?.label ?: MISSING),
            "PM 2.5" to (state.air?.pm25?.roundToInt()?.let { "$it" } ?: MISSING),
            "PM 10" to (state.air?.pm10?.roundToInt()?.let { "$it" } ?: MISSING),
        )

        FeedSection.VENTO -> listOf(
            "DIREZIONE" to Wmo.windDirection(hour?.windDirection),
            "RAFFICHE" to hour?.windGusts.asMetresPerSecond(),
            "MASSIMO OGGI" to day?.windMax.asMetresPerSecond(),
        )

        FeedSection.SOLE -> listOf(
            "ALBA" to (day?.sunrise?.toLocalTime()?.format(CLOCK) ?: MISSING),
            "TRAMONTO" to (day?.sunset?.toLocalTime()?.format(CLOCK) ?: MISSING),
            "UV MASSIMO" to day?.uvMax.asIndex(),
        )

        FeedSection.LUNA -> {
            val date = day?.date ?: LocalDate.now()
            val phase = MoonPhase.at(date)
            listOf(
                "FASE" to MoonSegment.of(phase).label,
                "ILLUMINATA" to "${(MoonPhase.illumination(phase) * 100f).roundToInt()}%",
                "ETA'" to "${MoonPhase.ageDays(phase).roundToInt()} GIORNI",
            )
        }
    }
    MetricsBar(entries = entries, accent = accent, modifier = modifier)
}

/**
 * Di quando parla questa sezione.
 *
 * **Solo il momento, non il posto.** La localita' e il giorno stanno gia' nella
 * testata in cima alla colonna, che non scorre via: ripeterli in ogni blocco
 * sarebbe la stessa riga stampata sei volte.
 *
 * **Un'ora precisa sopra un totale del giorno e' una bugia**, e si vedeva: la
 * sezione del sole diceva "15:00" sopra tredici *ore di sole*, che sono quelle
 * di tutta la giornata.
 */
private fun moment(state: UiState, section: FeedSection): String =
    if (section.isDailyTotal) {
        "TUTTO IL GIORNO"
    } else {
        state.detailHour?.time?.let { runCatching { it.format(CLOCK) }.getOrNull() } ?: MISSING
    }

private fun conditionLabel(code: Int?, forcedCode: Int?): String {
    val actual = forcedCode ?: code
    return Wmo.condition(actual)
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private const val MISSING = "--"

/** Il margine laterale del contenuto dentro il blocco. */
private val PAD = 12.dp

/** L'altezza della cifra estrusa dentro un blocco: una figura, non un eroe. */
private val NUMBER_HEIGHT = 132.dp

/** La finestra della pioggia, che vuole essere piu' alta che larga. */
private val WINDOW_HEIGHT = 232.dp

private val MOON_HEIGHT = 196.dp

/** Quanto l'inclinazione del telefono piega la luce sulla luna, in gradi. */
private const val SHADOW_PITCH = 5f

private const val MOON_RADIUS = 0.40f
