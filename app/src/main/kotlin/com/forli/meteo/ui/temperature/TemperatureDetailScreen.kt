package com.forli.meteo.ui.temperature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.WeatherViewModel
import com.forli.meteo.ui.common.MeteoEmptyState
import com.forli.meteo.ui.common.MeteoPillRow
import com.forli.meteo.ui.common.MeteoTopBar
import com.forli.meteo.ui.common.rememberMeteoLayout
import com.forli.meteo.ui.motion.PhysicalNumber
import com.forli.meteo.ui.motion.rememberSceneRotation
import com.forli.meteo.ui.motion.rotatesScene
import com.forli.meteo.ui.temperature.pages.AirPage
import com.forli.meteo.ui.temperature.pages.RainPage
import com.forli.meteo.ui.temperature.pages.SunPage
import com.forli.meteo.ui.temperature.pages.TemperaturePage
import com.forli.meteo.ui.temperature.pages.WindPage
import com.forli.meteo.ui.temperature.pages.heroMissingReason
import com.forli.meteo.ui.temperature.pages.heroSmallTail
import com.forli.meteo.ui.temperature.pages.heroValue
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Il dettaglio: sale trascinando in alto la principale, oppure toccando la
 * cifra della temperatura.
 *
 * Cinque pagine, una per grandezza. La cifra sta **fuori** dal carosello e
 * rimane fissa: il gesto orizzontale sulla cifra gira la scena 3D come nella
 * schermata principale, quello sul contenuto sotto cambia pagina. I due non si
 * contendono niente perche' non si sovrappongono - e' la stessa ragione per cui
 * il carosello era stato tolto una volta, salvo che qui il confine e'
 * dichiarato invece che sottinteso.
 *
 * **La settimana e' uscita dal carosello.** Stava dentro la sola pagina della
 * temperatura, il che la rendeva lunga il doppio delle altre e la nascondeva a
 * chi guardava il vento. E' la stessa informazione per tutte e cinque, quindi
 * sta sotto tutte e cinque.
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
    val mode = state.detailMode
    val layout = rememberMeteoLayout()
    val rotation = rememberSceneRotation()
    val accent = mode.accent()

    val pagerState = rememberPagerState(
        initialPage = modes.indexOf(mode).coerceAtLeast(0),
        pageCount = { modes.size },
    )

    // Carosello -> stato: quando si scorre, la modalita' segue.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val next = modes.getOrNull(page) ?: return@collect
            if (next != state.detailMode) viewModel.setDetailMode(next)
        }
    }
    // Stato -> carosello: quando si tocca una pillola, il carosello ci arriva.
    LaunchedEffect(mode) {
        val page = modes.indexOf(mode)
        if (page >= 0 && page != pagerState.currentPage) pagerState.animateScrollToPage(page)
    }

    Column(modifier = modifier.fillMaxSize()) {
        MeteoTopBar(
            title = mode.title,
            subtitle = subtitle(state),
            onBack = onBack,
            backLabel = "Chiudi il dettaglio",
        )

        MeteoPillRow(
            items = modes,
            selected = mode,
            label = { it.chipLabel },
            onSelect = viewModel::setDetailMode,
            modifier = Modifier.padding(horizontal = layout.gutter),
        )

        PageDots(
            count = modes.size,
            current = modes.indexOf(mode).coerceAtLeast(0),
            accent = accent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 2.dp),
        )

        Hero(
            state = state,
            mode = mode,
            accent = accent,
            rotation = rotation,
            tilt = tilt,
            heightFraction = layout.heroFraction,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            when (modes.getOrNull(page)) {
                DetailMode.TEMPERATURA -> TemperaturePage(state, layout, viewModel::setWeekMode)
                DetailMode.SOLE -> SunPage(state, layout, viewModel::setWeekMode)
                DetailMode.PRECIPITAZIONI -> RainPage(state, layout, viewModel::setWeekMode)
                DetailMode.VENTO -> WindPage(state, layout, viewModel::setWeekMode)
                DetailMode.ARIA -> AirPage(state, layout)
                null -> Unit
            }
        }

        // La settimana, sotto tutte le pagine: appartiene alla schermata, non a
        // una grandezza sola.
        DailyForecastCard(
            days = state.forecast?.days.orEmpty(),
            unit = state.unit,
            selected = state.selectedDay,
            onSelectDay = viewModel::openDayDetail,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.gutter, vertical = 8.dp),
        )
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
private fun subtitle(state: UiState): String {
    val place = state.place.name.uppercase()
    val dayLabel = when (state.selectedDay) {
        0 -> "OGGI"
        1 -> "DOMANI"
        else -> state.forecast?.days?.getOrNull(state.selectedDay)?.label ?: "--"
    }
    val clock = state.detailHour?.time?.let {
        runCatching { it.format(HOUR_FORMAT) }.getOrNull()
    }
    return listOfNotNull(place, dayLabel, clock).joinToString("  ·  ")
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
 */
@Composable
private fun Hero(
    state: UiState,
    mode: DetailMode,
    accent: Color,
    rotation: com.forli.meteo.ui.motion.SceneRotation,
    tilt: State<Offset>,
    heightFraction: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val heroHeight = maxHeight * heightFraction
        val value = heroValue(mode, state)
        if (value == null) {
            val (title, message) = heroMissingReason(mode, state)
            MeteoEmptyState(title = title, message = message)
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
 * I pallini che dicono quante pagine ci sono e a quale si e'.
 *
 * Le pillole sopra scorrono, quindi non bastano: con cinque grandezze su uno
 * schermo stretto le ultime restano fuori, e chi guarda non ha modo di sapere
 * che esistono. Era una delle mancanze annotate in CONTESTO, «non c'e' segno di
 * quante pagine ci siano».
 */
@Composable
private fun PageDots(
    count: Int,
    current: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val idle = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            Canvas(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .then(if (active) Modifier.width(16.dp) else Modifier.width(6.dp)),
            ) {
                drawRoundRect(
                    color = if (active) accent else idle,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                )
            }
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
