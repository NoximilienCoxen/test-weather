package com.forli.meteo.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.SkyState
import com.forli.meteo.data.Wind
import com.forli.meteo.data.Wmo
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.asBigTemperature
import com.forli.meteo.ui.motion.PhysicalNumber
import com.forli.meteo.ui.motion.SceneRotation
import com.forli.meteo.ui.motion.rememberSceneRotation
import com.forli.meteo.ui.motion.rememberSpinFeedback
import com.forli.meteo.ui.motion.rotatesScene
import com.forli.meteo.ui.render3d.SceneContact
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType
import com.forli.meteo.ui.theme.overScene
import kotlinx.coroutines.delay
import kotlin.random.Random
import java.time.LocalDate

/**
 * Quello che serve sapere aprendo l'app: che tempo fa adesso, quanti gradi, e
 * come sara' nelle prossime ore. Tutto il resto sta un trascinamento piu' su.
 */
@Composable
fun HomeScreen(
    state: UiState,
    sky: SkyState,
    fog: Float,
    tilt: State<Offset>,
    onSelectHour: (Int) -> Unit,
    onBackToNow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val hours = state.hours
    val hour = state.hour

    // Un solo orientamento per la scultura e la cifra: sono un oggetto solo
    // visto da un punto solo, e il gesto che li gira e' lo stesso.
    val spin = rememberSpinFeedback()
    val rotation: SceneRotation = rememberSceneRotation(
        // Dietro le impostazioni la cifra non si vede: un colpo che arriva da
        // una schermata coperta non si capisce da dove venga.
        onFullTurn = { if (!state.settingsOpen) spin.landed() },
    )

    // E un solo mondo: la pioggia esce dalla nuvola e finisce sulla cifra, che
    // sta in un'altra tela. Qui passano la sagoma e le due origini.
    val contact = remember { SceneContact() }
    val snowfall = remember { Snowfall() }
    val fogBrushes = remember { FogBrushes() }
    // Il velo davanti alla scena e' un pennello anche lui, e cambia solo con la
    // densita' e la tavolozza: ricostruirlo nel disegno lo rifaceva sessanta
    // volte al secondo identico a se stesso.
    val fogVeil = remember(fog, colors.fogNear, colors.fogFar) {
        Brush.verticalGradient(
            0f to colors.fogFar.copy(alpha = fog * 0.10f),
            0.66f to colors.fogNear.copy(alpha = fog * 0.30f),
            0.80f to Color.Transparent,
            1f to Color.Transparent,
        )
    }

    val code = state.activeWeatherCode
    val family = Wmo.family(code)
    val wind = state.wind
    val snowing = family == Wmo.Family.NEVE

    // Il cielo sereno: da qui discendono gli uccelli di giorno e le stelle di
    // notte. Sotto una coltre non c'e' ne' l'uno ne' l'altro.
    val clarity = (1f - Wmo.cloudiness(code) * 1.35f - fog).coerceIn(0f, 1f)
    val starlight = clarity * (1f - sky.dayness)
    val daylight = clarity * sky.dayness

    // La meteora e' un evento, non un ciclo: fra l'una e l'altra non c'e'
    // niente in corsa, e l'orologio puo' spegnersi. Averne di piu' significa
    // aspettare meno fra un evento e il successivo, non tenerne una accesa.
    var streak by remember { mutableStateOf(ShootingStar.NONE) }
    val streakProgress = remember { mutableFloatStateOf(1f) }
    // Due stati e non uno, e la differenza e' tutta prestazionale. Il progresso
    // cambia a ogni fotogramma e viene letto **dentro il disegno**, quindi fa
    // ridipingere e basta. Questa bandiera cambia due volte per meteora e viene
    // letta in composizione, per decidere se il battito serve. Leggendo il
    // progresso qui, ogni fotogramma della scia avrebbe ricomposto l'intera
    // schermata: una quarantina di ricomposizioni per stella cadente, per un
    // numero che alla composizione non serve affatto.
    var streaking by remember { mutableStateOf(false) }
    LaunchedEffect(starlight > 0.25f) {
        if (starlight <= 0.25f) {
            streakProgress.floatValue = 1f
            streaking = false
            return@LaunchedEffect
        }
        var seed = 31
        while (true) {
            val random = Random(seed)
            seed = (seed * 1103515245 + 12345) and 0x3FFFFFFF
            delay(random.nextLong(1400, 4200))
            streak = ShootingStar.of(random)
            // Ogni tanto due di fila, a poca distanza: e' quello che fa sembrare
            // un cielo vivo invece di un metronomo.
            val repeats = if (random.nextFloat() < 0.28f) 2 else 1
            repeat(repeats) { pass ->
                if (pass > 0) {
                    streak = ShootingStar.of(random)
                    delay(220)
                }
                var origin = 0L
                var done = false
                streaking = true
                while (!done) {
                    withFrameNanos { now ->
                        if (origin == 0L) origin = now
                        val elapsed = (now - origin) / 1_000_000f
                        val t = elapsed / STREAK_MS
                        streakProgress.floatValue = t
                        if (t >= 1f) done = true
                    }
                }
                streaking = false
            }
        }
    }

    // Un battito solo per tutta la scena, acceso solo se qualcosa si muove
    // davvero. Con cielo coperto, aria ferma e niente che cada, l'app torna a
    // disegnare zero fotogrammi.
    val alive = family.isWet() ||
        fog > 0.02f ||
        daylight > 0.15f ||
        streaking ||
        (wind.strength > 0.12f && Wmo.cloudiness(code) > 0.05f)
    val clock = rememberSceneClock(alive)

    Box(modifier = modifier.fillMaxSize()) {

        // Il cielo sta dietro tutto e non ruota: girando la cifra, le stelle non si
        // devono muovere. E' proprio quello a dire che la cifra e' un oggetto e il
        // cielo e' il posto in cui sta.
        Canvas(Modifier.fillMaxSize()) {
            drawStars(presence = starlight, clarity = 1f)
            drawShootingStar(streak, streakProgress.floatValue, starlight)
            drawBirds(clock, daylight, wind, colors.bird)
            drawFog(clock, fog, wind, colors.fogNear, colors.fogFar, fogBrushes)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 24.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsButton(onClick = onOpenSettings)
                // In cima allo schermo il cielo e' la parte viva del gradiente, non
                // quella profonda: l'inchiostro del corpo del testo, tarato sul
                // fondo, li' non e' garantito. Questo lo e'.
                Text(
                    text = state.place.name.uppercase(),
                    style = MeteoType.caption.overScene(colors),
                    color = colors.textOnSky,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                // Occupa quanto il pulsante a sinistra, cosi' il nome resta al
                // centro dello schermo e non al centro di quel che avanza.
                Spacer(Modifier.width(34.dp))
            }

            // Scultura e cifra dentro lo stesso riquadro sensibile: il dito li
            // gira insieme dovunque lo si appoggi, invece di dover indovinare
            // quale dei due accetta il gesto.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .rotatesScene(rotation),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WeatherSculpture(
                    weatherCode = code,
                    // L'aggancio di verifica deve restare fedele: imporre pioggia a
                    // qualunque codice faceva piovere anche su "coperto", che e'
                    // asciutto. Solo i codici bagnati portano gocce.
                    precipitationMm = state.forcedWeatherCode
                        ?.let { if (Wmo.family(it).isWet()) 2.5 else 0.0 }
                        ?: hour?.precipitation,
                    probability = state.forcedWeatherCode
                        ?.let { if (Wmo.family(it).isWet()) 80 else 0 }
                        ?: hour?.precipProbability,
                    sky = sky,
                    wind = wind,
                    fog = fog,
                    date = hour?.time?.toLocalDate() ?: LocalDate.now(),
                    rotation = rotation,
                    tilt = tilt,
                    // Dietro le impostazioni la schermata resta viva: senza questo
                    // il telefono continuerebbe a vibrare di pioggia mentre si
                    // sceglie una citta'.
                    feelsIt = !state.settingsOpen,
                    contact = contact,
                    clock = clock,
                    snowfall = snowfall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.62f),
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                ) {
                    // Finche' non c'e' un numero non si disegna niente. Un "--"
                    // alto mezzo schermo, con tanto di spessore e di ombra, non
                    // dice "sto aspettando": dice che l'app e' rotta.
                    val degrees = hour?.temperature
                    if (degrees != null) PhysicalNumber(
                        text = degrees.asBigTemperature(state.unit),
                        fontSize = maxHeight * 0.86f,
                        rotation = rotation,
                        tilt = tilt,
                        // Un filo verso l'alto: la cifra e la scultura devono
                        // leggersi come un oggetto solo, e fra loro non ci deve
                        // stare il vuoto che ci starebbe centrandole entrambe.
                        verticalBias = -0.04f,
                        contact = contact,
                        // La coltre si disegna qui e non nella tela della scultura,
                        // perche' deve stare **sopra** la cifra: la scultura viene
                        // prima nella disposizione, e quello che disegna finisce
                        // sotto. Il tempo lo fa passare qui dentro per lo stesso
                        // motivo per cui la sagoma vive qui: e' un fotogramma solo,
                        // e c'e' un posto solo in cui e' fresco.
                        overlay = { skyline ->
                            snowfall.cap.advance(
                                clock = clock,
                                skyline = skyline,
                                yawDeg = rotation.yawDeg,
                                snowing = snowing,
                                width = size.width,
                            )
                            snowfall.cap.draw(this, skyline, size.minDimension, colors.snow)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Crossfade e non sostituzione secca: scorrendo le ore la condizione
            // cambia spesso, e uno scatto di testo si nota piu' del testo stesso.
            // Al posto della condizione, finche' non c'e', si dice cosa sta
            // succedendo. Uno schermo fermo sui trattini lascia credere che
            // l'attesa sia il risultato.
            Crossfade(
                targetState = when {
                    state.error != null -> state.error.uppercase()
                    hour == null -> "IN ATTESA DEI DATI"
                    else -> conditionLabel(hour, state.forcedWeatherCode)
                },
                label = "condizione",
                modifier = Modifier.fillMaxWidth(),
            ) { label ->
                Text(
                    text = label,
                    style = MeteoType.label.overScene(colors),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))

            HourBar(
                hours = hours,
                selected = state.selectedHour,
                nowIndex = state.nowIndex,
                onSelect = onSelectHour,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            // Tornare all'ora vera deve costare un tocco. Scorrendo la barra si
            // finisce facilmente lontani, e ritrovare la posizione a mano annulla
            // il senso di aver aperto l'app per sapere che tempo fa adesso.
            val onNow = state.selectedHour == state.nowIndex
            val interaction = remember { MutableInteractionSource() }
            Text(
                text = if (onNow) hourLabel(hour) else "${hourLabel(hour)}  ·  ADESSO",
                style = MeteoType.caption.overScene(colors),
                color = if (onNow) colors.label else colors.text,
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 8.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = !onNow,
                        onClick = onBackToNow,
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // Il velo davanti alla scena.
        //
        // La nebbia sta anche **fra** chi guarda e l'oggetto, non solo dietro: e'
        // questo a distinguerla da un cielo coperto, che invece sta tutto dietro.
        // Ma si spegne prima della fascia dei testi: li' sotto non c'e' piu' scena,
        // c'e' l'interfaccia, e sbiancare il fondo dietro un testo bianco vuol dire
        // rimettere in piedi esattamente il difetto di leggibilita' appena tolto.
        if (fog > 0.02f) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to colors.fogFar.copy(alpha = fog * 0.10f),
                        0.66f to colors.fogNear.copy(alpha = fog * 0.30f),
                        0.80f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                )
            }
        }
    }
}

/** Quanto dura la corsa di una meteora, in millisecondi. */
private const val STREAK_MS = 620f

/** Tre righe: e' il segno universale, e non serve una libreria di icone. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val colors = LocalMeteoColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val gap = size.height / 3f
            for (i in 0 until 3) {
                val y = gap * (i + 0.5f)
                drawLine(
                    color = colors.labelOnSky,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = size.height * 0.10f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * La probabilita' compare solo quando c'e' davvero qualcosa da prevedere:
 * "sereno 0%" sarebbe rumore.
 */
private fun conditionLabel(
    hour: com.forli.meteo.data.HourForecast?,
    forcedCode: Int?,
): String {
    if (hour == null && forcedCode == null) return "--"
    val code = forcedCode ?: hour?.weatherCode
    val condition = Wmo.condition(code)
    val wet = Wmo.family(code).isWet()
    val probability = if (forcedCode != null) 80 else hour?.precipProbability ?: 0
    return if (wet && probability > 0) "$condition $probability%" else condition
}
