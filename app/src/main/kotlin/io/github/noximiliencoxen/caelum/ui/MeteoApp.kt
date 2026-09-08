package io.github.noximiliencoxen.caelum.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.ui.alerts.AlertsSheet
import io.github.noximiliencoxen.caelum.ui.feed.FeedScreen
import io.github.noximiliencoxen.caelum.ui.motion.findLifecycleOwner
import io.github.noximiliencoxen.caelum.ui.motion.rememberDeviceTilt
import io.github.noximiliencoxen.caelum.ui.settings.SettingsScreen
import io.github.noximiliencoxen.caelum.ui.welcome.WelcomeScreen
import io.github.noximiliencoxen.caelum.ui.theme.MeteoTheme
import io.github.noximiliencoxen.caelum.ui.theme.relativeLuminance
import io.github.noximiliencoxen.caelum.ui.theme.skyColors
import kotlin.math.roundToInt

/**
 * La pila dell'app: il cielo, il feed, e i due pannelli che gli si mettono
 * davanti.
 *
 * **Il feed ha preso il posto del foglio.** Prima qui c'erano una schermata
 * essenziale e un dettaglio che saliva dal basso seguendo il dito: le sei
 * grandezze stavano dentro il foglio, in un carosello orizzontale. Adesso sono
 * le sezioni del feed - una schermata piena ciascuna, si scorre dal basso verso
 * l'alto - e questo file non ha piu' un gesto suo: quello verticale appartiene
 * al carosello, che se lo gestisce insieme al tiro per ricaricare.
 *
 * Restano davanti al feed due pannelli veri, che dipingono sopra il cielo: le
 * allerte entrano da destra perche' si scende dentro qualcosa di piu' specifico,
 * le impostazioni da sinistra, da dove sta il loro pulsante.
 */
@Composable
fun MeteoApp(viewModel: WeatherViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Un solo numero anima tutto il cielo, e da quello si ricavano insieme il
    // fondo, il colore del sole e la comparsa della luna. Animando le tre cose
    // separatamente ci sarebbero istanti in cui non sono d'accordo fra loro:
    // sole gia' sparito e cielo ancora di giorno.
    val altitude by animateFloatAsState(
        targetValue = state.skyAltitude,
        animationSpec = spring(stiffness = 110f),
        label = "cielo",
    )
    // Anche il viaggio si anima, se no scorrendo le ore l'astro salterebbe da
    // una posizione all'altra invece di attraversare il cielo.
    val journey by animateFloatAsState(
        targetValue = state.skyJourney,
        animationSpec = spring(stiffness = 110f),
        label = "viaggio",
    )
    // Da che parte della giornata si guarda: decide se l'orizzonte e' il rosa
    // dell'alba o l'arancio del tramonto. Si muove con la stessa molla degli
    // altri due, se no attraversando il mezzogiorno il cielo cambierebbe
    // tavolozza di scatto invece che di passaggio.
    val evening by animateFloatAsState(
        targetValue = state.skyEvening,
        animationSpec = spring(stiffness = 110f),
        label = "sera",
    )
    // Quanto e' coperto. Sta fuori da SkyState perche' non e' astronomia, ma si
    // anima insieme al resto: passando da un'ora serena a una piovosa il cielo
    // deve ingrigirsi, non cambiare.
    val cloudiness by animateFloatAsState(
        targetValue = state.skyCloudiness,
        animationSpec = spring(stiffness = 110f),
        label = "nuvolosita",
    )
    val sky = remember(altitude, journey, evening) { SkyState.of(altitude, journey, evening) }
    val colors = remember(sky, cloudiness) { skyColors(sky, cloudiness) }
    // Tre fermate e non due: quella di mezzo e' il tono piatto che tutto il
    // resto dell'app chiama "il fondo", e deve comparire davvero sullo schermo -
    // se no i testi e la barra sarebbero tarati su un cielo che non si vede.
    val skyBrush = remember(colors) {
        Brush.verticalGradient(
            0f to colors.skyZenith,
            0.5f to colors.background,
            1f to colors.skyHorizon,
        )
    }

    MeteoTheme(colors = colors) {
        // Un solo ascoltatore del sensore per tutta l'app, e il valore resta
        // uno stato: letto dentro il disegno invece che in composizione, il
        // sensore fa ridipingere e non ricomporre.
        val tilt = rememberDeviceTilt()

        val density = LocalDensity.current

        // I dati si ricaricano tornando in primo piano, se hanno passato la
        // loro eta'. La composizione resta viva anche in sottofondo, quindi il
        // suo ciclo di vita non basta: serve quello dell'attivita'.
        val context = LocalContext.current
        val model by rememberUpdatedState(viewModel)
        DisposableEffect(context) {
            val owner = context.findLifecycleOwner()
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) model.refreshIfStale()
            }
            owner?.lifecycle?.addObserver(observer)
            onDispose { owner?.lifecycle?.removeObserver(observer) }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(skyBrush),
        ) {
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)

            // Le icone delle barre di sistema seguono cio' che hanno sotto. Con
            // le barre trasparenti e un fondo che va dall'azzurro di
            // mezzogiorno all'indaco della notte, lasciarle fisse vuol dire
            // che per meta' giornata
            // sono invisibili: e' il motivo per cui negli scatti la barra di
            // navigazione appariva bianca sotto un'app scura.
            //
            // **Il feed non entra in questo conto.** Le sue schede vivono tutte
            // sul cielo: sopra e sotto ci sono lo zenit e l'orizzonte a
            // qualunque scheda si sia, quindi la risposta e' la stessa per tutte
            // e sei. A cambiarla restano i due pannelli veri, che dipingono
            // sopra il cielo.
            val panelled = state.settingsOpen || state.alertsOpen
            SystemBarIcons(
                behindStatusBar = if (panelled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    colors.skyZenith
                },
                behindNavigationBar = if (panelled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    colors.skyHorizon
                },
            )

            // Al primo avvio l'app chiede dove sei, invece di dare per scontato
            // un posto che nessuno ha scelto. Il feed non entra in scena finche'
            // il benvenuto non ha finito: non c'e' ancora niente da raccontare.
            if (!state.welcomed) {
                WelcomeScreen(
                    state = state,
                    tilt = tilt,
                    onFindMe = viewModel::useDeviceLocation,
                    onChooseByHand = {
                        viewModel.dismissWelcome()
                        viewModel.openSettings()
                    },
                    onDone = viewModel::dismissWelcome,
                    modifier = Modifier.systemBarsPadding(),
                )
            } else {
                // **Il feed, e non piu' una schermata con un foglio sopra.** Le
                // sei grandezze erano pagine di un carosello dentro un foglio
                // che saliva dal basso: adesso sono le schede del feed, una
                // schermata piena ciascuna, e ci si passa scorrendo. Il gesto
                // verticale che apriva il foglio e' lo stesso che adesso cambia
                // sezione - e' passato di mano, non e' stato aggiunto.
                //
                // L'inserto delle barre di sistema sta **dentro**, scheda per
                // scheda: il cielo dipinge da bordo a bordo, e fermarlo qui
                // lascerebbe una striscia grigia sopra e sotto il feed.
                FeedScreen(
                    state = state,
                    sky = sky,
                    tilt = tilt,
                    viewModel = viewModel,
                )
            }

            // Le allerte entrano da destra: si scende dentro qualcosa di piu'
            // specifico, e il verso lo racconta. Stanno **dopo** il dettaglio
            // nella pila perche' la fascia si puo' toccare anche da li', e un
            // foglio che si apre sotto quello da cui e' stato aperto non si
            // vedrebbe.
            val alertsShift by animateFloatAsState(
                targetValue = if (state.alertsOpen) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                label = "allerte",
            )
            if (alertsShift > 0.001f) {
                BackHandler(enabled = state.alertsOpen, onBack = viewModel::closeAlerts)
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(((1f - alertsShift) * widthPx).roundToInt(), 0) },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    AlertsSheet(
                        alerts = state.shownAlerts,
                        unavailable = state.alertsUnavailable,
                        outOfCoverage = state.alertsOutOfCoverage,
                        onBack = viewModel::closeAlerts,
                        modifier = Modifier.systemBarsPadding(),
                    )
                }
            }

            val settings by animateFloatAsState(
                targetValue = if (state.settingsOpen) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                label = "impostazioni",
            )
            if (settings > 0.001f) {
                BackHandler(enabled = state.settingsOpen, onBack = viewModel::closeSettings)
                // **Opaco.** Era nero all'ottantacinque per cento, e la
                // schermata sotto traspariva: negli scatti si legge la cifra
                // della temperatura in mezzo al testo delle impostazioni. Un
                // velo non e' uno sfondo, e un testo che poggia su un velo non
                // ha un contrasto: ne ha uno diverso a ogni pixel.
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset((-(1f - settings) * widthPx).roundToInt(), 0) },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    SettingsScreen(
                        state = state,
                        onQuery = viewModel::search,
                        onChoosePlace = viewModel::choosePlace,
                        onChooseUnit = viewModel::setUnit,
                        onChooseModel = viewModel::setModel,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onUseLocation = viewModel::useDeviceLocation,
                        onClose = viewModel::closeSettings,
                        modifier = Modifier.systemBarsPadding(),
                    )
                }
            }
        }
    }
}

/**
 * Chiare o scure, le icone delle barre di sistema.
 *
 * Si decide dalla luminanza di cio' che sta sotto, con la stessa formula che
 * decide i colori del testo: sopra un fondo chiaro icone scure, sopra uno scuro
 * icone chiare. La soglia e' la meta' della scala percettiva, non lo 0,5 del
 * canale: un giallo pieno ha luminanza 0,93 e vuole icone nere, un blu pieno
 * 0,07 e le vuole bianche, e i due sono altrettanto "saturi".
 */
@Composable
private fun SystemBarIcons(behindStatusBar: Color, behindNavigationBar: Color) {
    val view = LocalView.current
    // Le due barre si decidono separatamente da quando il fondo e' una
    // sfumatura: in alto c'e' lo zenit e in fondo l'orizzonte, e al crepuscolo
    // uno dei due e' scuro mentre l'altro e' chiaro. Un boolean solo per
    // entrambe sbaglia sempre una delle due, per un'ora buona al giorno.
    val lightStatus = behindStatusBar.relativeLuminance() > 0.35f
    val lightNavigation = behindNavigationBar.relativeLuminance() > 0.35f
    DisposableEffect(view, lightStatus, lightNavigation) {
        val window = (view.context.findActivity())?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightStatus
                isAppearanceLightNavigationBars = lightNavigation
            }
        }
        onDispose { }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Oltre questa velocita' il gesto decide da solo, senza guardare la posizione. */
private const val SNAP_VELOCITY = 800f

/** Quanto dito serve per chiedere una ricarica, e quanto se ne accetta in tutto. */
private const val PULL_TRIGGER = 190f
private const val PULL_LIMIT = 300f

/**
 * Quanto vicino a un'ancora conta come esserci arrivati.
 *
 * Serve perche' `raised` e' il valore di una molla che qualcuno puo' cancellare
 * a meta': un confronto esatto con 1 lasciava il foglio "quasi aperto" per il
 * resto della sua vita.
 */
private const val ANCHOR_EPSILON = 0.999f
