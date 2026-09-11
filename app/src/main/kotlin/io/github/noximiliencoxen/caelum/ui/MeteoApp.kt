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
import io.github.noximiliencoxen.caelum.ui.motion.findLifecycleOwner
import io.github.noximiliencoxen.caelum.ui.motion.rememberDeviceTilt
import io.github.noximiliencoxen.caelum.ui.sala.SalaShell
import io.github.noximiliencoxen.caelum.ui.welcome.WelcomeScreen
import io.github.noximiliencoxen.caelum.ui.theme.MeteoTheme
import io.github.noximiliencoxen.caelum.ui.theme.relativeLuminance
import io.github.noximiliencoxen.caelum.ui.theme.skyColors

/**
 * La pila dell'app: il benvenuto, e Sala.
 *
 * **Sala ha preso il posto del feed.** Le sette sale sono un carosello
 * verticale che gestisce da solo il proprio cielo (la carta acquerello, non
 * piu' quella a sfumatura) e i propri due pannelli di servizio — localita' e
 * impostazioni. Questo file resta responsabile solo del benvenuto e del
 * ricaricamento quando l'app torna in primo piano.
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

            // Le icone delle barre di sistema seguono cio' che hanno sotto.
            // Sala dipinge la propria carta da bordo a bordo con le sue tinte
            // (vedi `SalaPalette`), non piu' il cielo a sfumatura: qui restano
            // solo i colori del benvenuto, l'unica schermata che li usa ancora.
            SystemBarIcons(
                behindStatusBar = colors.skyZenith,
                behindNavigationBar = colors.skyHorizon,
            )

            // Al primo avvio l'app chiede dove sei, invece di dare per scontato
            // un posto che nessuno ha scelto. Sala non entra in scena finche'
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
                SalaShell(
                    state = state,
                    sky = sky,
                    viewModel = viewModel,
                    widthPx = widthPx,
                )
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

// **Qui c'erano quattro costanti del foglio, e due di loro erano doppie.**
//
// `SNAP_VELOCITY` e `ANCHOR_EPSILON` descrivevano le ancore del foglio che si
// alzava sopra la schermata: il foglio non c'e' piu' da quando c'e' il feed, e
// le due misure sono rimaste a descrivere un movimento che nessuno fa.
//
// `PULL_TRIGGER` e `PULL_LIMIT` erano il caso peggiore: il tiro per ricaricare
// e' migrato in `FeedScreen`, che se le e' **ridichiarate identiche** invece di
// importarle. Due copie con lo stesso valore e nessun legame fra loro sono due
// numeri destinati a divergere al primo che ne tara uno. Restano quelle di
// `FeedScreen`, che sono le uniche che qualcuno legge davvero.
