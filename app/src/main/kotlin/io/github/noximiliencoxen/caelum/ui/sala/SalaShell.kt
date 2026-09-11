package io.github.noximiliencoxen.caelum.ui.sala

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.WeatherViewModel
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaAriaScreen
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaLunaScreen
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaOggiScreen
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaPioggiaScreen
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaSettimanaScreen
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaUvScreen
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaVentoScreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Sala: la galleria sostituisce il feed. Sette stanze in un carosello
 * verticale, un indicatore di percorso al posto della colonna di icone, e
 * due schermate di servizio che entrano da un lato — le localita' e le
 * impostazioni — esattamente come impostazioni e allerte facevano prima.
 *
 * Ogni stanza calcola la propria tavolozza da [SkyState] (fase reale del
 * sole nella localita' mostrata) e dal tempo dell'ora scelta: non c'e' piu'
 * un cielo unico condiviso, perche' la carta di Sala non e' un cielo, e'
 * una pagina che scurisce a scatti (vedi [paperDarkness]).
 */
@Composable
fun SalaShell(
    state: UiState,
    sky: SkyState,
    viewModel: WeatherViewModel,
    widthPx: Float,
    modifier: Modifier = Modifier,
) {
    val rooms = SalaRoom.entries
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = rooms.indexOf(state.room).coerceAtLeast(0),
        pageCount = { rooms.size },
    )

    // Come per il vecchio carosello del feed: si scrive nello stato solo sulla
    // pagina posata, mai su quella sfiorata durante un trascinamento.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            rooms.getOrNull(page)?.let(viewModel::showRoom)
        }
    }

    // L'aggancio di verifica automatica: `--ei sezione`, riletto per le sale.
    //
    // Legge la sala **dalla richiesta** e non da `state.room`, che il carosello
    // qui sopra riscrive da se' a ogni pagina posata: due scrittori sullo
    // stesso campo sono una gara, e la richiesta la perderebbe ogni volta che
    // la prima emissione - pagina zero - arriva prima che l'effetto la legga.
    // Senza animazione, perche' chi scatta vuole la sala subito.
    LaunchedEffect(state.roomRequest) {
        val wanted = state.roomRequest ?: return@LaunchedEffect
        val page = rooms.indexOf(wanted)
        if (page >= 0 && page != pagerState.currentPage) pagerState.scrollToPage(page)
        viewModel.roomRequestHonoured()
    }

    val position = { pagerState.currentPage + pagerState.currentPageOffsetFraction }

    // Come nel feed: il tasto indietro torna alla prima sala prima di chiudere
    // l'app, perche' da sei stanze sotto uscire non e' quasi mai la risposta
    // cercata.
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    val phase = salaPhaseOf(sky)
    val condition = salaConditionOf(state.forcedWeatherCode ?: state.hour?.weatherCode)
    val palette = remember(sky, phase, condition, state.cardTheme) {
        salaPalette(sky, phase, condition, state.cardTheme)
    }

    Box(modifier = modifier.fillMaxSize()) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (rooms.getOrNull(page)) {
                SalaRoom.OGGI -> SalaOggiScreen(
                    state = state,
                    palette = palette,
                    position = position,
                    viewModel = viewModel,
                    onPlaceClick = viewModel::openLocations,
                    onMenuClick = viewModel::openSettings,
                    // `currentPage` e non `settledPage`: cambia una volta per
                    // pagina, quindi non ricompone a ogni fotogramma del dito,
                    // e spegne l'orologio appena si comincia ad andare altrove.
                    inVista = pagerState.currentPage == page,
                )
                SalaRoom.SETTIMANA -> SalaSettimanaScreen(
                    state = state,
                    palette = palette,
                    position = position,
                    viewModel = viewModel,
                    onPlaceClick = viewModel::openLocations,
                    onMenuClick = viewModel::openSettings,
                )
                SalaRoom.PIOGGIA -> SalaPioggiaScreen(
                    state = state,
                    palette = palette,
                    position = position,
                    onPlaceClick = viewModel::openLocations,
                    onMenuClick = viewModel::openSettings,
                    onSelectHour = viewModel::selectHour,
                )
                SalaRoom.LUNA -> SalaLunaScreen(
                    state = state,
                    palette = palette,
                    position = position,
                    onPlaceClick = viewModel::openLocations,
                    onMenuClick = viewModel::openSettings,
                )
                SalaRoom.ARIA -> SalaAriaScreen(
                    state = state,
                    palette = palette,
                    position = position,
                    onPlaceClick = viewModel::openLocations,
                    onMenuClick = viewModel::openSettings,
                )
                SalaRoom.VENTO -> SalaVentoScreen(
                    state = state,
                    palette = palette,
                    position = position,
                    onPlaceClick = viewModel::openLocations,
                    onMenuClick = viewModel::openSettings,
                )
                SalaRoom.UV -> SalaUvScreen(
                    state = state,
                    palette = palette,
                    position = position,
                    onPlaceClick = viewModel::openLocations,
                    onMenuClick = viewModel::openSettings,
                    onSelectHour = viewModel::selectHour,
                )
                null -> Unit
            }
        }

        val locationsShift by animateFloatAsState(
            targetValue = if (state.locationsOpen) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
            label = "localita",
        )
        if (locationsShift > 0.001f) {
            BackHandler(enabled = state.locationsOpen, onBack = viewModel::closeLocations)
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(((1f - locationsShift) * widthPx).roundToInt(), 0) },
                color = MaterialTheme.colorScheme.surface,
            ) {
                SalaLocalitaScreen(
                    state = state,
                    palette = palette,
                    onPick = viewModel::choosePlace,
                    onAdd = viewModel::toggleFavorite,
                    onRemove = viewModel::toggleFavorite,
                    onSearch = viewModel::search,
                    onClose = viewModel::closeLocations,
                )
            }
        }

        val settingsShift by animateFloatAsState(
            targetValue = if (state.settingsOpen) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
            label = "impostazioni",
        )
        if (settingsShift > 0.001f) {
            BackHandler(enabled = state.settingsOpen, onBack = viewModel::closeSettings)
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset((-(1f - settingsShift) * widthPx).roundToInt(), 0) },
                color = MaterialTheme.colorScheme.surface,
            ) {
                SalaImpostazioniScreen(
                    state = state,
                    palette = palette,
                    onChooseTheme = viewModel::setCardTheme,
                    onChooseUnit = viewModel::setUnit,
                    onChooseWindUnit = viewModel::setWindUnit,
                    onChooseCaptionStyle = viewModel::setCaptionStyle,
                    onToggleAlert = viewModel::setAlertToggle,
                    onSearch = viewModel::search,
                    onPickPlace = viewModel::choosePlace,
                    onUseLocation = viewModel::useDeviceLocation,
                    onClose = viewModel::closeSettings,
                )
            }
        }
    }
}
