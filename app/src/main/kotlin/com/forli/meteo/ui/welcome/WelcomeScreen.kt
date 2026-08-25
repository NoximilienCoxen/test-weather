package com.forli.meteo.ui.welcome

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.render3d.Camera
import com.forli.meteo.ui.render3d.Explorer
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Il primo avvio: l'app chiede dove sei, invece di dare per scontato un posto
 * che nessuno ha scelto.
 *
 * Prima non c'era **nessun momento** in cui lo chiedesse. La localita' si
 * cambiava solo entrando nelle impostazioni, quindi chi non ci entrava restava
 * per sempre sull'ultima impostata senza sapere che ce ne fosse un'altra.
 *
 * L'esploratore non e' una decorazione: e' l'unica cosa che spiega il tasto
 * senza scriverlo. Una mano appoggiata sopra dice "questo si preme", l'altra
 * sopra gli occhi dice cosa succede premendolo. Con la testa che si guarda
 * intorno la schermata si racconta da sola, e la riga di testo puo' restare una
 * domanda invece di diventare un'istruzione.
 *
 * **Qui l'app disegna in continuazione, ed e' l'unico posto in cui lo fa.**
 * Altrove la regola e' zero fotogrammi a schermo immobile (trappola #8), e vale
 * ancora; questa schermata si vede una volta, il movimento e' il contenuto, e
 * si spegne da sola appena si passa oltre.
 */
@Composable
fun WelcomeScreen(
    state: UiState,
    tilt: State<Offset>,
    onFindMe: () -> Unit,
    onChooseByHand: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val density = LocalDensity.current

    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onFindMe() else onChooseByHand() }

    // Trovato il posto, il pupazzo ha finito il suo lavoro: un attimo per farlo
    // vedere e la schermata cede il passo a quella vera.
    LaunchedEffect(state.followsLocation) {
        if (state.followsLocation) {
            delay(HANDOVER_MS)
            onDone()
        }
    }

    // Il ciclo del "si guarda intorno". Esplicito e non `rememberInfiniteTransition`,
    // che qui non anima (trappola #17): un battito per fotogramma che scrive un
    // valore, e il disegno lo legge.
    val look = remember { mutableFloatStateOf(0f) }
    val breath = remember { mutableFloatStateOf(0f) }
    val hunting = state.locating
    LaunchedEffect(hunting) {
        var origin = 0L
        while (true) {
            withFrameNanos { now ->
                if (origin == 0L) origin = now
                val t = (now - origin) / 1_000_000_000f
                // Mentre cerca guarda piu' in fretta e piu' largo: e' l'unico
                // modo che ha di dire che sta facendo qualcosa.
                val speed = if (hunting) 2.6f else 1.05f
                look.floatValue = sin(t * speed)
                breath.floatValue = sin(t * 2.1f)
            }
        }
    }

    val shrug by animateFloatAsState(
        targetValue = if (state.locationUnavailable) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 220f),
        label = "spallucce",
    )

    val explorer = remember { Explorer() }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "affondo",
    )

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "CIAO",
            style = MeteoType.label,
            color = colors.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                state.locating -> "TI STO CERCANDO…"
                state.locationUnavailable -> "NON RIESCO A TROVARTI"
                state.followsLocation -> state.place.name.uppercase()
                else -> "DOVE TI TROVI ADESSO?"
            },
            style = MeteoType.title,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
        ) {
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            // Il pupazzo occupa poco piu' di meta' del riquadro. A grandezza
            // piena il tasto gli finiva **addosso**: il braccio che avrebbe
            // dovuto allungarsi per raggiungerlo era lungo sette centesimi di
            // unita', cioe' spariva dentro il torace.
            val unit = min(boxW, boxH) * FIGURE_SCALE

            // Il pupazzo e il tasto si accordano su un punto solo, calcolato da
            // entrambi con le stesse frazioni: cosi' la mano ci finisce sopra a
            // qualunque dimensione di schermo, senza numeri scritti a mano.
            val originX = boxW * FIGURE_X
            val originY = boxH * FIGURE_Y
            val buttonW = with(density) { BUTTON_WIDTH.toPx() }
            val buttonH = with(density) { BUTTON_HEIGHT.toPx() }
            val buttonCx = boxW * BUTTON_X
            val buttonCy = boxH * BUTTON_Y + sink * buttonH * 0.06f

            Canvas(Modifier.fillMaxSize()) {
                val camera = Camera(
                    yawDeg = tilt.value.x * TILT_YAW,
                    pitchDeg = tilt.value.y * TILT_PITCH,
                    distance = unit * 2.6f,
                    origin = Offset(originX, originY),
                )
                // La mano si appoggia sul bordo del tasto, non al suo centro:
                // una mano che spunta da meta' del tasto sembra dentro il tasto.
                explorer.pose(
                    headYaw = look.floatValue * LOOK_DEGREES,
                    reachX = (buttonCx - buttonW * 0.30f - originX) / unit,
                    reachY = (buttonCy - buttonH * 0.22f - originY) / unit,
                    shrug = shrug,
                    breath = breath.floatValue,
                )
                explorer.draw(
                    scope = this,
                    camera = camera,
                    unit = unit,
                    skin = colors.cloudCore,
                    shade = colors.cloudShade,
                    hatSkin = colors.sunShade,
                    hatShade = lerp(colors.sunShade, colors.numberSideFar, 0.55f),
                )
            }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (buttonCx - buttonW / 2f).roundToInt(),
                            (buttonCy - buttonH / 2f).roundToInt(),
                        )
                    }
                    .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.pillBackground)
                    .clickable(
                        interactionSource = press,
                        indication = null,
                        enabled = !state.locating && !state.followsLocation,
                        onClick = {
                            askPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "TROVAMI",
                    style = MeteoType.label,
                    color = colors.pillText,
                )
            }
        }

        // La via d'uscita c'e' sempre: un permesso negato non e' un vicolo
        // cieco, e chi non lo vuole dare deve poter comunque usare l'app.
        val escape = remember { MutableInteractionSource() }
        Text(
            text = "SCELGO IO",
            style = MeteoType.caption,
            color = colors.label,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable(
                    interactionSource = escape,
                    indication = null,
                    onClick = onChooseByHand,
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

/** Dove sta il pupazzo nel suo riquadro, e quanto e' grande, in frazioni. */
private const val FIGURE_X = 0.30f
private const val FIGURE_Y = 0.55f
private const val FIGURE_SCALE = 0.78f

/**
 * Dove sta il tasto, in frazioni dello stesso riquadro.
 *
 * All'altezza della spalla, non del petto: un braccio che si alza troppo si
 * legge come un saluto, e uno che scende come un braccio caduto. Appoggiato
 * vuol dire quasi in piano.
 */
private const val BUTTON_X = 0.74f
private const val BUTTON_Y = 0.50f

private val BUTTON_WIDTH = 156.dp
private val BUTTON_HEIGHT = 54.dp

/** Di quanti gradi gira la testa agli estremi della sbirciata. */
private const val LOOK_DEGREES = 26f

/** Quanto aspetta, trovato il posto, prima di cedere il passo. */
private const val HANDOVER_MS = 900L

private const val TILT_YAW = 9f
private const val TILT_PITCH = 6f
