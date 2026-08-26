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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.render3d.Camera
import com.forli.meteo.ui.render3d.globe
import com.forli.meteo.ui.render3d.sphere
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * Il primo avvio: l'app chiede dove sei, invece di dare per scontato un posto
 * che nessuno ha scelto.
 *
 * Prima non c'era **nessun momento** in cui lo chiedesse. La localita' si
 * cambiava solo entrando nelle impostazioni, quindi chi non ci entrava restava
 * per sempre sull'ultima impostata senza sapere che ce ne fosse un'altra.
 *
 * **Un mappamondo e non un personaggio.** Una figura umana fatta di sfere non
 * viene: era stata provata, e ogni correzione ne scopriva un'altra - le braccia
 * a collana, il cappello che spariva, la mano che salutava invece di riparare lo
 * sguardo. Il mappamondo invece e' la luna con un'altra pelle, cioe' l'unica
 * cosa di questo motore che si sa gia' che funziona bene: sfera, luce di
 * sempre, macchie sulla superficie che scivolano via girando. E dice la stessa
 * identica cosa - **dove sei sulla Terra** - senza dover somigliare a nessuno.
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

    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onFindMe() else onChooseByHand() }

    val found = state.followsLocation

    // Trovato il posto, un attimo per farlo vedere e la schermata cede il passo.
    LaunchedEffect(found) {
        if (found) {
            delay(HANDOVER_MS)
            onDone()
        }
    }

    // Il giro del mappamondo, battuto a mano sui fotogrammi. Esplicito e non
    // `rememberInfiniteTransition`, che qui non anima (trappola #17).
    //
    // La velocita' e' un valore solo che sale mentre cerca e scende a zero
    // quando ha trovato: l'angolo lo integra, quindi rallenta e si ferma senza
    // scatti invece di spegnersi di colpo.
    val spin = remember { mutableFloatStateOf(0f) }
    val pulse = remember { mutableFloatStateOf(0f) }
    val hunting = state.locating
    LaunchedEffect(hunting, found) {
        var last = 0L
        val speed = when {
            found -> 0f
            hunting -> HUNT_SPEED
            else -> IDLE_SPEED
        }
        var current = spin.floatValue
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                // Insegue la velocita' voluta invece di prenderla: e' la
                // differenza fra un mappamondo che accelera e uno che scatta.
                current += (speed - current) * (dt * 2.2f).coerceAtMost(1f)
                spin.floatValue += current * dt
                pulse.floatValue = (pulse.floatValue + dt) % 1f
            }
        }
    }

    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "affondo",
    )
    val pinned by animateFloatAsState(
        targetValue = if (found) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 240f),
        label = "spillo",
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
                found -> state.place.name.uppercase()
                else -> "DOVE TI TROVI ADESSO?"
            },
            style = MeteoType.title,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(28.dp))

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(260.dp),
        ) {
            val unit = size.minDimension
            val camera = Camera(
                yawDeg = tilt.value.x * TILT_YAW,
                pitchDeg = tilt.value.y * TILT_PITCH,
                distance = unit * 2.6f,
                origin = Offset(size.width / 2f, size.height / 2f),
            )
            val radius = unit * 0.34f

            globe(
                camera = camera,
                x = 0f,
                y = 0f,
                z = 0f,
                radius = radius,
                spinDeg = spin.floatValue,
                light = colors.cloudCore,
                dark = colors.cloudShade,
                land = lerp(colors.numberSideFar, colors.background, 0.34f),
                lands = CONTINENTS,
            )

            // Lo spillo si pianta quando il posto e' arrivato. Prima di allora
            // non c'e', perche' non c'e' niente da segnare.
            if (pinned > 0.01f) {
                // Arriva da fuori e si posa: la corsa la fa la molla, qui si
                // legge solo dove e' arrivata.
                val drop = (1f - pinned) * radius * 1.6f
                sphere(
                    camera = camera,
                    x = 0f,
                    y = -radius * 0.18f - drop,
                    z = -radius * 0.98f,
                    radius = radius * 0.10f,
                    light = colors.text,
                    dark = colors.text,
                    alpha = pinned,
                )
                // L'onda che parte da li'. Una sola, e in dissolvenza: e' un
                // segno di conferma, non un radar.
                val wave = pulse.floatValue
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.text.copy(alpha = 0f),
                            colors.text.copy(alpha = 0.22f * pinned * (1f - wave)),
                            colors.text.copy(alpha = 0f),
                        ),
                        center = Offset(size.width / 2f, size.height / 2f - radius * 0.18f),
                        radius = radius * (0.3f + wave * 1.1f),
                    ),
                    radius = radius * (0.3f + wave * 1.1f),
                    center = Offset(size.width / 2f, size.height / 2f - radius * 0.18f),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .padding(top = (sink * 3).dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.pillBackground)
                .clickable(
                    interactionSource = press,
                    indication = null,
                    enabled = !state.locating && !found,
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
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

/**
 * I continenti: longitudine e latitudine in gradi, piu' quanto e' grande la
 * macchia.
 *
 * Non e' una mappa e non prova a esserlo. Servono a far **vedere** che la sfera
 * gira: una sfera liscia che ruota e' una sfera ferma. Sono sparsi su tutte le
 * longitudini apposta, cosi' in ogni istante qualcosa sta entrando da un lato
 * mentre qualcos'altro esce dall'altro.
 */
private val CONTINENTS: List<Triple<Float, Float, Float>> = listOf(
    Triple(-58f, 8f, 0.20f),
    Triple(-72f, -26f, 0.15f),
    Triple(12f, 22f, 0.17f),
    Triple(24f, -10f, 0.19f),
    Triple(46f, 46f, 0.13f),
    Triple(104f, 34f, 0.21f),
    Triple(136f, -26f, 0.14f),
    Triple(-160f, 40f, 0.12f),
)

private val BUTTON_WIDTH = 172.dp
private val BUTTON_HEIGHT = 56.dp

/** Gradi al secondo: da fermo si guarda, mentre cerca si affanna. */
private const val IDLE_SPEED = 16f
private const val HUNT_SPEED = 90f

/** Quanto aspetta, trovato il posto, prima di cedere il passo. */
private const val HANDOVER_MS = 1400L

private const val TILT_YAW = 8f
private const val TILT_PITCH = 6f
