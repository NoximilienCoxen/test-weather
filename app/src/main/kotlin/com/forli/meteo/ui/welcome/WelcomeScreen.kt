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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.render3d.Camera
import com.forli.meteo.ui.render3d.WORLD_COASTS
import com.forli.meteo.ui.render3d.globe
import com.forli.meteo.ui.render3d.globeSpot
import com.forli.meteo.ui.render3d.spinToFace
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
    // **Si apre sull'Africa, non sul Pacifico.** A giro zero davanti c'e' la
    // longitudine zero, che di terre ne ha poche: il primo scatto mostrava un
    // disco blu con una striscia artica in cima e l'Australia in basso, cioe'
    // niente di riconoscibile. Partendo da qui la prima cosa che si vede sono
    // Africa ed Europa - le due che chiunque riconosce a colpo d'occhio - e
    // solo dopo il giro porta avanti il resto.
    val spin = remember { mutableFloatStateOf(spinToFace(OPENING_LON)) }
    val pulse = remember { mutableFloatStateOf(0f) }
    val hunting = state.locating
    val homeLon = state.place.longitude.toFloat()
    LaunchedEffect(hunting, found, homeLon) {
        var last = 0L
        var speed = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                if (found) {
                    // **Trovato il posto, il mappamondo ci si gira.** Prima
                    // rallentava e basta, fermandosi dove capitava: il segno
                    // atterrava al centro del disco su un pezzo di oceano a
                    // caso. Adesso porta davanti la longitudine di casa, e lo
                    // spillo si pianta dov'e' casa davvero - che e' l'unica
                    // cosa che quella schermata deve dire.
                    //
                    // Per la via piu' corta: da centosettanta gradi est a
                    // centosettanta ovest sono venti gradi, non trecentoquaranta.
                    var delta = (spinToFace(homeLon) - spin.floatValue) % 360f
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    spin.floatValue += delta * (dt * HOMING_EASE).coerceAtMost(1f)
                    speed = 0f
                } else {
                    val wanted = if (hunting) HUNT_SPEED else IDLE_SPEED
                    // Insegue la velocita' voluta invece di prenderla: e' la
                    // differenza fra un mappamondo che accelera e uno che scatta.
                    speed += (wanted - speed) * (dt * 2.2f).coerceAtMost(1f)
                    spin.floatValue += speed * dt
                }
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
            val radius = unit * 0.38f

            globe(
                camera = camera,
                x = 0f,
                y = 0f,
                z = 0f,
                radius = radius,
                spinDeg = spin.floatValue,
                light = colors.globeSea,
                dark = colors.globeSeaShade,
                land = colors.globeLand,
                coasts = WORLD_COASTS,
            )

            // **Lo spillo si pianta sul posto, non al centro del disco.**
            // Prima era una pallina appoggiata davanti alla sfera in un punto
            // fisso: qualunque cosa avesse trovato la localizzazione, il segno
            // finiva li'. Adesso la posizione la danno le coordinate vere, e
            // siccome il mappamondo si e' girato apposta per portarle davanti,
            // lo spillo atterra dov'e' casa.
            //
            // Nullo vuol dire che quel punto sta ancora dietro: durante la
            // rotazione capita, e in quei fotogrammi non si disegna niente
            // invece di appiccicare il segno sul bordo.
            val spot = if (pinned > 0.01f) {
                globeSpot(
                    camera = camera,
                    x = 0f, y = 0f, z = 0f,
                    radius = radius,
                    spinDeg = spin.floatValue,
                    lonDeg = state.place.longitude.toFloat(),
                    latDeg = state.place.latitude.toFloat(),
                )
            } else {
                null
            }

            spot?.let { at ->
                // Arriva da fuori e si posa: la corsa la fa la molla, qui si
                // legge solo dove e' arrivata.
                val drop = (1f - pinned) * radius * 1.4f
                val head = Offset(at.x, at.y - drop)

                // L'onda che parte da li'. Una sola, e in dissolvenza: e' un
                // segno di conferma, non un radar.
                val wave = pulse.floatValue
                val ring = radius * (0.10f + wave * 0.55f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.text.copy(alpha = 0f),
                            colors.text.copy(alpha = 0.26f * pinned * (1f - wave)),
                            colors.text.copy(alpha = 0f),
                        ),
                        center = at,
                        radius = ring,
                    ),
                    radius = ring,
                    center = at,
                )

                // Il segno: un disco pieno con attorno un alone chiaro, cosi'
                // si stacca sia dal mare sia dalla terra sotto.
                drawCircle(
                    color = colors.text,
                    radius = radius * 0.075f,
                    center = head,
                    alpha = 0.28f * pinned,
                )
                drawCircle(
                    color = colors.text,
                    radius = radius * 0.038f,
                    center = head,
                    alpha = pinned,
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


private val BUTTON_WIDTH = 172.dp
private val BUTTON_HEIGHT = 56.dp

/**
 * Quanto in fretta il mappamondo si porta davanti il posto trovato.
 *
 * E' un inseguimento, non una durata: ogni fotogramma copre una frazione di
 * quello che manca, quindi arriva piano senza sbattere contro il bersaglio.
 */
private const val HOMING_EASE = 2.6f

/** Gradi al secondo: da fermo si guarda, mentre cerca si affanna. */
/**
 * La longitudine che guarda l'osservatore all'apertura: l'Africa centrale, con
 * l'Europa sopra e le Americhe che stanno per entrare da destra.
 */
private const val OPENING_LON = 18f

private const val IDLE_SPEED = 7f
private const val HUNT_SPEED = 90f

/** Quanto aspetta, trovato il posto, prima di cedere il passo. */
private const val HANDOVER_MS = 1400L

private const val TILT_YAW = 8f
private const val TILT_PITCH = 6f
