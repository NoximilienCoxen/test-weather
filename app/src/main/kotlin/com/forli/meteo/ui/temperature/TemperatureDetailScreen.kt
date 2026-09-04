package com.forli.meteo.ui.temperature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.alerts.AlertBanner
import com.forli.meteo.ui.WeatherViewModel
import com.forli.meteo.ui.common.MeteoEmptyState
import com.forli.meteo.ui.common.MeteoIconButton
import com.forli.meteo.ui.common.MeteoPillRow
import com.forli.meteo.ui.common.MeteoTopBar
import com.forli.meteo.ui.common.rememberMeteoLayout
import com.forli.meteo.ui.home.MoonPhase
import com.forli.meteo.ui.home.MoonSegment
import com.forli.meteo.ui.motion.PhysicalNumber
import com.forli.meteo.ui.motion.rememberSceneRotation
import com.forli.meteo.ui.motion.rotatesScene
import com.forli.meteo.ui.render3d.Camera
import com.forli.meteo.ui.render3d.MOON_SEAS
import com.forli.meteo.ui.render3d.glow
import com.forli.meteo.ui.render3d.moon
import com.forli.meteo.ui.temperature.pages.AirPage
import com.forli.meteo.ui.temperature.pages.MoonPage
import com.forli.meteo.ui.temperature.pages.RainPage
import com.forli.meteo.ui.temperature.pages.SunPage
import com.forli.meteo.ui.temperature.pages.TemperaturePage
import com.forli.meteo.ui.temperature.pages.WindPage
import com.forli.meteo.ui.temperature.pages.heroMissingReason
import com.forli.meteo.ui.temperature.pages.heroSmallTail
import com.forli.meteo.ui.temperature.pages.heroValue
import com.forli.meteo.ui.temperature.pages.pageDay
import com.forli.meteo.ui.theme.LocalMeteoAccents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Il dettaglio: sale trascinando in alto la principale, oppure toccando la
 * cifra della temperatura.
 *
 * Sei pagine, una per grandezza. La cifra sta **fuori** dal carosello e
 * rimane fissa: il gesto orizzontale sulla cifra gira la scena 3D come nella
 * schermata principale, quello sul contenuto sotto cambia pagina. I due non si
 * contendono niente perche' non si sovrappongono - e' la stessa ragione per cui
 * il carosello era stato tolto una volta, salvo che qui il confine e'
 * dichiarato invece che sottinteso.
 *
 * **La settimana e' uscita dal carosello.** Stava dentro la sola pagina della
 * temperatura, il che la rendeva lunga il doppio delle altre e la nascondeva a
 * chi guardava il vento. E' la stessa informazione per tutte, quindi sta sotto
 * tutte.
 *
 * **La sesta pagina e' la luna**, e con lei arriva il pulsante che apre
 * l'elenco: sei pillole su un telefono stretto non stanno in una schermata, e
 * una fila che scorre non ha modo di dire cosa c'e' oltre il bordo.
 */
@Composable
fun TemperatureDetailScreen(
    state: UiState,
    viewModel: WeatherViewModel,
    tilt: State<Offset>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = DetailMode.entries
    val layout = rememberMeteoLayout()
    val rotation = rememberSceneRotation()
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = modes.indexOf(state.detailMode).coerceAtLeast(0),
        pageCount = { modes.size },
    )

    // Il carosello e' la sorgente di verita'. `state.detailMode` sopravvive
    // come **modalita' d'ingresso** - da quale grandezza si e' aperto il
    // foglio - e come memoria per la riapertura, non come seconda copia di
    // "su quale pagina sono".
    //
    // Prima erano due copie riconciliate da due effetti che si rincorrevano, e
    // il secondo era chiavato su cio' che il primo cambiava: mentre
    // `animateScrollToPage` girava, la pagina intermedia faceva cambiare la
    // modalita', l'effetto veniva rilanciato e **si cancellava l'animazione da
    // solo**. Toccare "Aria" partendo da "Temp" lasciava il carosello fermo a
    // meta' strada.
    //
    // Si scrive nello stato **solo a scorrimento finito**: un trascinamento
    // lasciato a meta' e tornato indietro non e' una scelta, e non deve
    // lasciare traccia nello stato globale dell'app.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val next = modes.getOrNull(page) ?: return@collect
            if (next != state.detailMode) viewModel.setDetailMode(next)
        }
    }

    // La pagina che si sta guardando: quella piu' vicina, non quella posata.
    // Comanda titolo, pillola accesa, cifra e tinta, cioe' tutto cio' che deve
    // corrispondere a **cio' che si vede**. E' un intero e cambia solo allo
    // scavalco, quindi ricomporre qui costa una volta a pagina.
    val shownMode = modes.getOrNull(pagerState.currentPage) ?: modes.first()
    val accent = shownMode.accent()
    val accents = modes.map { it.accent() }

    // Cio' che si muove col dito passa invece per **lambda**, non per valore.
    // `currentPageOffsetFraction` cambia a ogni fotogramma del trascinamento:
    // leggerlo qui nel corpo ricomporrebbe l'intera schermata sessanta volte al
    // secondo per spostare una trasparenza e allungare un pallino. Letto dentro
    // `graphicsLayer` o dentro una tela, l'aggiornamento si ferma alla fase di
    // disegno.
    val drift = { pagerState.currentPageOffsetFraction }
    val position = { pagerState.currentPage + pagerState.currentPageOffsetFraction }

    // L'elenco dei pannelli e' interfaccia effimera: si apre, si sceglie, si
    // chiude. Non appartiene a `UiState` - sopravvivergli alla chiusura del
    // foglio vorrebbe dire riaprire il dettaglio e ritrovarsi davanti un elenco
    // che nessuno ha chiesto.
    var pickerOpen by remember { mutableStateOf(false) }

    fun goTo(picked: DetailMode) {
        val page = modes.indexOf(picked)
        if (page >= 0) scope.launch { pagerState.animateScrollToPage(page) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MeteoTopBar(
                title = shownMode.title,
                subtitle = subtitle(state, shownMode),
                onBack = onBack,
                backLabel = "Chiudi il dettaglio",
                transition = drift,
            )

            // Sotto la barra e **fuori** dal carosello: l'allerta vale per la
            // localita', non per la grandezza che si sta guardando, e deve restare
            // in scena su tutte le pagine. Non disegna niente quando non c'e'
            // niente da dire.
            AlertBanner(
                alerts = state.shownAlerts,
                onOpen = viewModel::openAlerts,
                modifier = Modifier.padding(horizontal = layout.gutter, vertical = 4.dp),
            )

            // Il pulsante sta **fuori** dalla fila, non dentro: la fila scorre, e
            // una voce dentro di lei scorrerebbe via insieme alle pillole - cioe'
            // sparirebbe proprio quando serve, che e' quando si e' scorsi lontano.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeteoPillRow(
                    items = modes,
                    selected = shownMode,
                    label = { it.chipLabel },
                    // La pillola muove il carosello **direttamente**, senza passare
                    // dallo stato: l'animazione non vive piu' dentro un effetto
                    // chiavato su cio' che essa stessa cambia, quindi nessuno la
                    // interrompe a meta'.
                    onSelect = ::goTo,
                    contentPadding = PaddingValues(start = layout.gutter, end = 4.dp),
                    position = position,
                    modifier = Modifier.weight(1f),
                )
                MeteoIconButton(
                    onClick = { pickerOpen = true },
                    contentDescription = "Mostra tutti i pannelli",
                    icon = { PanelListIcon(MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.padding(end = layout.gutter - 12.dp),
                )
            }

            PageDots(
                position = position,
                accents = accents,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 2.dp),
            )

            Hero(
                state = state,
                mode = shownMode,
                accent = accent,
                rotation = rotation,
                tilt = tilt,
                drift = drift,
                heightFraction = layout.heroFraction,
                modifier = Modifier.fillMaxWidth(),
            )

            // La settimana chiude ogni pagina: appartiene alla schermata, non a una
            // grandezza sola. Sta dentro lo scorrimento delle pagine e non appesa
            // sotto il carosello, dove sarebbe un'altezza fissa che in orizzontale
            // non lascia piu' spazio al carosello stesso.
            val week: @Composable () -> Unit = {
                DailyForecastCard(
                    days = state.forecast?.days.orEmpty(),
                    unit = state.unit,
                    selected = state.selectedDay,
                    onSelectDay = viewModel::openDayDetail,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (modes.getOrNull(page)) {
                    DetailMode.TEMPERATURA ->
                        TemperaturePage(state, layout, viewModel::setWeekMode, week = week)
                    DetailMode.SOLE ->
                        SunPage(state, layout, viewModel::setWeekMode, week = week)
                    DetailMode.PRECIPITAZIONI ->
                        RainPage(state, layout, viewModel::setWeekMode, week = week)
                    DetailMode.VENTO ->
                        WindPage(state, layout, viewModel::setWeekMode, week = week)
                    DetailMode.ARIA ->
                        AirPage(state, layout, week = week)
                    DetailMode.LUNA ->
                        MoonPage(state, layout, week = week)
                    null -> Unit
                }
            }
        }

        // Sopra tutto, e con il suo tasto indietro. E' composto **dopo** il
        // foglio che lo contiene, quindi il suo `BackHandler` viene prima:
        // indietro chiude l'elenco e lascia il dettaglio dov'e'.
        if (pickerOpen) {
            BackHandler(enabled = true) { pickerOpen = false }
            PanelPicker(
                modes = modes,
                selected = shownMode,
                onPick = { picked ->
                    goTo(picked)
                    pickerOpen = false
                },
                onClose = { pickerOpen = false },
            )
        }
    }
}

/**
 * Di chi e di quando sono questi numeri.
 *
 * Aperto il foglio non c'era piu' modo di saperlo: la localita' resta scritta
 * sulla schermata principale, che il foglio copre. E i numeri di Forli' e
 * quelli di Bergen si somigliano abbastanza da non poterli distinguere a
 * occhio.
 */
private fun subtitle(state: UiState, mode: DetailMode): String {
    val place = state.place.name.uppercase()
    val dayLabel = when (state.selectedDay) {
        0 -> "OGGI"
        1 -> "DOMANI"
        else -> state.forecast?.days?.getOrNull(state.selectedDay)?.label ?: "--"
    }
    // Un'ora precisa sopra un totale del giorno e' una bugia, e si vedeva:
    // sulla pagina del sole l'intestazione diceva "OGGI  ·  15:00" mentre la
    // cifra sotto annunciava tredici **ore di sole**, che non sono le ore di
    // sole delle quindici - sono quelle di tutta la giornata. Stessa cosa sui
    // millimetri di pioggia. `isDailyTotal` sapeva gia' distinguere i due casi
    // e nessuno glielo chiedeva.
    val moment = if (mode.isDailyTotal) {
        "TUTTO IL GIORNO"
    } else {
        state.detailHour?.time?.let { runCatching { it.format(HOUR_FORMAT) }.getOrNull() }
    }
    return listOfNotNull(place, dayLabel, moment).joinToString("  ·  ")
}

private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * La cifra, girabile col dito, con la sua unita' scritta sotto.
 *
 * L'unita' e' la correzione piu' semplice e la piu' necessaria: senza, la
 * pagina del sole diceva "8", quella della pioggia "0" e quella del vento "1",
 * e non c'era modo di sapere di cosa fossero. Solo la temperatura se la cavava,
 * perche' il suo grado il prisma lo estrude insieme alle cifre.
 *
 * Se un numero non c'e', **non si disegna niente**: si dice cosa manca e
 * perche'. Un "--" alto mezzo schermo non comunica attesa, comunica guasto.
 *
 * **La luna non ha una cifra**: al suo posto sta il corpo, e gira con lo stesso
 * gesto. Vedi [MoonHero].
 */
@Composable
private fun Hero(
    state: UiState,
    mode: DetailMode,
    accent: Color,
    rotation: com.forli.meteo.ui.motion.SceneRotation,
    tilt: State<Offset>,
    drift: () -> Float,
    heightFraction: Float,
    modifier: Modifier = Modifier,
) {
    // La cifra vive **fuori** dal carosello, quindi non scorre via con la
    // pagina: si sostituiva sul posto a meta' trascinamento, e per mezzo gesto
    // si leggeva il numero di una grandezza sopra il contenuto di un'altra.
    // Sfumando, il cambio avviene nell'istante in cui non c'e' niente da
    // leggere - lo stesso trattamento del titolo, per la stessa ragione.
    BoxWithConstraints(
        modifier = modifier.graphicsLayer {
            alpha = 1f - (abs(drift()).coerceAtMost(0.5f) * 2f)
        },
    ) {
        val heroHeight = maxHeight * heightFraction

        // La luna va **prima** del controllo sulla cifra, e per due ragioni.
        // La prima e' che non ha una cifra: il suo eroe e' il corpo. La seconda
        // e' che non ha bisogno della previsione - la fase si calcola in locale
        // - quindi e' l'unica pagina che ha ancora qualcosa da mostrare quando
        // la rete tace, e passare di li' la spegnerebbe insieme alle altre.
        if (mode == DetailMode.LUNA) {
            MoonHero(
                date = state.pageDay?.date ?: LocalDate.now(),
                rotation = rotation,
                tilt = tilt,
                height = heroHeight,
                modifier = Modifier.fillMaxWidth(),
            )
            return@BoxWithConstraints
        }

        val value = heroValue(mode, state)
        if (value == null) {
            // Un guasto globale - previsione che non arriva, rete muta - lo
            // dice l'eroe, che e' il posto piu' visibile. Il buco di una
            // singola grandezza invece lo spiega la sua pagina, che ne sa il
            // motivo: qui uscivano **tutti e due**, e la pagina dell'aria
            // ripeteva parola per parola quello che l'eroe aveva gia' detto
            // due centimetri sopra.
            val (title, message) = heroMissingReason(mode, state)
            if (state.forecast == null) MeteoEmptyState(title = title, message = message)
            return@BoxWithConstraints
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .rotatesScene(rotation),
                contentAlignment = Alignment.Center,
            ) {
                ProjectedShadow(
                    yawDeg = rotation.yawDeg,
                    pitchDeg = tilt.value.y * 5f,
                    color = accent.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxSize(),
                )
                PhysicalNumber(
                    text = value,
                    smallTail = heroSmallTail(mode),
                    fontSize = heroHeight * 0.74f,
                    rotation = rotation,
                    tilt = tilt,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (mode.unitLabel.isNotBlank()) {
                Text(
                    text = mode.unitLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * La luna, girabile col dito.
 *
 * E' lo stesso corpo della scultura della schermata principale e del widget -
 * stessa sfera, stessa luce, stessi mari - e sta nello stesso posto in cui le
 * altre pagine mettono la cifra: sopra il carosello, dentro il `Box` che porta
 * [rotatesScene]. Il gesto quindi e' gia' quello di sempre, e non c'e' un
 * secondo riconoscitore da mettere d'accordo con nessuno.
 *
 * **I mari girano, la mediana no**, e non e' un difetto da correggere: i mari
 * stanno sulla sfera e passano dalla camera, quindi ruotando scivolano verso il
 * bordo e spariscono dietro; la mediana invece la disegna [moon] in coordinate
 * di schermo, perche' da che parte cada lo decide il Sole e non chi guarda. Una
 * falce che si raddrizza girando il telefono sarebbe una luna che cambia fase
 * perche' ci si e' spostati di venti centimetri.
 */
@Composable
private fun MoonHero(
    date: LocalDate,
    rotation: com.forli.meteo.ui.motion.SceneRotation,
    tilt: State<Offset>,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val light = LocalMeteoAccents.current.moon
    // Il tondo spento e i mari vogliono un grigio medio, non un grigio da
    // testo: `outline` e' il tono che il tema tiene per i contorni, ed e' quello
    // che al 24 per cento legge come "disco che c'e' ma non e' illuminato".
    val dark = MaterialTheme.colorScheme.outline
    val phase = remember(date) { MoonPhase.at(date) }
    val spoken = remember(phase) {
        val percent = (MoonPhase.illumination(phase) * 100f).roundToInt()
        "${MoonSegment.of(phase).label.lowercase()}, illuminata al $percent per cento"
    }
    Box(
        modifier = modifier
            .height(height)
            .rotatesScene(rotation),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = spoken },
        ) {
            // Giro e inclinazione si leggono **qui dentro**, non in
            // composizione: girare deve ridipingere, non ricomporre.
            val unit = minOf(size.width, size.height)
            val camera = Camera(
                yawDeg = rotation.yawDeg,
                pitchDeg = tilt.value.y * 5f,
                distance = unit * 2.7f,
                origin = Offset(size.width / 2f, size.height / 2f),
            )
            val radius = unit * 0.40f
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

/**
 * I pallini che dicono quante pagine ci sono e a quale si e'.
 *
 * Le pillole sopra scorrono, quindi non bastano: con cinque grandezze su uno
 * schermo stretto le ultime restano fuori, e chi guarda non ha modo di sapere
 * che esistono. Era una delle mancanze annotate in CONTESTO, «non c'e' segno di
 * quante pagine ci siano».
 *
 * **Continui, non a scatti.** Prima prendevano un indice intero e il pallino
 * lungo saltava da una posizione all'altra a meta' trascinamento, cioe' nello
 * stesso istante sbagliato in cui saltava il titolo. Qui prendono la posizione
 * frazionaria del carosello: l'allungamento si travasa da un pallino al
 * successivo mentre il dito si muove, e a meta' strada sono lunghi meta'
 * ciascuno - che e' esattamente dov'e' la pagina.
 *
 * Un pallino non e' testo, quindi qui interpolare un colore e' lecito: la
 * regola sul contrasto calcolato riguarda cio' che si legge, e i due estremi
 * dell'interpolazione vengono comunque dal tema.
 */
@Composable
private fun PageDots(
    position: () -> Float,
    accents: List<Color>,
    modifier: Modifier = Modifier,
) {
    val idle = MaterialTheme.colorScheme.outlineVariant
    val count = accents.size
    // **Una tela sola, non un pallino per composable.** La posizione si legge
    // dentro il blocco di disegno: il travaso da un pallino al successivo
    // avviene in fase di disegno, senza ricomporre niente e senza rimisurare un
    // layout a ogni fotogramma. Con un `Row` di cinque `Canvas` larghi in `dp`
    // ogni frame del trascinamento avrebbe rifatto misura e posizionamento di
    // tutti e cinque.
    Canvas(modifier = modifier.height(6.dp)) {
        if (count == 0) return@Canvas
        val at = position()
        val gap = 6.dp.toPx()
        val small = 6.dp.toPx()
        val grown = 16.dp.toPx()
        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)

        // Larghezze prima, cosi' la fila si puo' centrare sapendo quanto misura
        // davvero: allungandosi un pallino, il totale cambia a ogni fotogramma.
        val shares = FloatArray(count) { i -> (1f - abs(i - at)).coerceIn(0f, 1f) }
        val widths = FloatArray(count) { i -> small + (grown - small) * shares[i] }
        val total = widths.sum() + gap * (count - 1)

        var x = (size.width - total) / 2f
        for (i in 0 until count) {
            drawRoundRect(
                color = lerp(idle, accents[i], shares[i]),
                topLeft = Offset(x, 0f),
                size = Size(widths[i], size.height),
                cornerRadius = radius,
            )
            x += widths[i] + gap
        }
    }
}

/**
 * L'ombra ellittica sotto la cifra.
 *
 * Non e' l'ombra proiettata della schermata principale - quella e' geometria
 * vera dentro il renderer - ma segue lo stesso giro e la stessa inclinazione,
 * cosi' la cifra qui non sembra appoggiata sul nulla.
 */
@Composable
private fun ProjectedShadow(
    yawDeg: Float,
    pitchDeg: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val yawRad = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val offsetX = kotlin.math.sin(yawRad) * size.width * 0.12f
        val offsetY = kotlin.math.sin(pitchRad) * size.height * 0.06f
        val halfPi = (Math.PI / 2f).toFloat()
        val scaleX = (1f - abs(yawRad) / halfPi * 0.5f).coerceAtLeast(0.2f)
        val scaleY = (1f - abs(pitchRad) / halfPi * 0.5f).coerceAtLeast(0.2f)
        val rX = size.width * 0.24f * scaleX
        val rY = size.height * 0.055f * scaleY
        val shadowCy = cy + size.height * 0.34f + offsetY
        drawOval(
            color = color,
            topLeft = Offset(cx + offsetX - rX, shadowCy - rY),
            size = Size(rX * 2f, rY * 2f),
        )
    }
}
