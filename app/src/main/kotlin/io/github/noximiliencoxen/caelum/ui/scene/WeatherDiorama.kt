package io.github.noximiliencoxen.caelum.ui.scene

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.MeteoColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * La scena dipinta, viva.
 *
 * E' il posto della vecchia scultura, e il cambio non e' di stile: quella era
 * geometria calcolata a ogni fotogramma da un rasterizzatore software - sfere e
 * prismi, mille e cinquecento triangoli, una luce sola - e per quanto la si
 * incorniciasse restava un oggetto su un fondo. Questa e' un **luogo**, e le
 * cose che ci si muovono dentro le decide la previsione.
 *
 * Quattro movimenti, tutti da dati veri e nessuno registrato:
 *
 * - **la parallasse**, dalla mappa di profondita' e dall'accelerometro che l'app
 *   gia' ascolta: inclinando il telefono il primo piano scorre sul fondo;
 * - **la deriva dell'aria**, che va col vento previsto e si vede in cielo molto
 *   piu' che a terra;
 * - **la luce dell'ora**, che tinge le distanze con lo stesso cielo che
 *   `skyColors` calcola per tutto il resto della schermata, cosi' un dipinto di
 *   mezzogiorno diventa lo stesso posto all'alba senza ridipingerlo;
 * - **la precipitazione**, disegnata sopra e non dentro, quindi un acquazzone e
 *   una pioggerella non sono la stessa immagine con due etichette.
 *
 * **Rompe la regola dei zero fotogrammi a riposo** (trappola #8), e di
 * proposito: e' una scena viva, e una scena viva disegna. Il freno resta
 * [alive], che la ferma appena la schermata non e' quella che si guarda.
 */
@Composable
fun WeatherDiorama(
    kind: SceneKind,
    sky: SkyState,
    tilt: State<Offset>,
    /** Metri al secondo, dalla previsione dell'ora mostrata. */
    windSpeed: Double?,
    /** Millimetri all'ora dell'ora mostrata: decide quanto e' fitta la pioggia. */
    precipitationMm: Double?,
    alive: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalMeteoColors.current
    val transition = rememberSceneTransition(kind)
    val light = rememberSceneLight(sky, colors)

    // Costruito una volta e guardando la versione: sopra la 33 c'e' lo shader,
    // sotto ci sono le tre lastre. E' la scala di ripiego per livello di API che
    // il progetto usa gia' per la vibrazione.
    val effect = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) DioramaEffect() else null
    }
    val bands = remember { if (effect == null) DioramaBands() else null }

    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        var loaded by remember { mutableStateOf<Map<SceneKind, ScenePlates>>(emptyMap()) }

        val needed = transition.needed
        LaunchedEffect(needed, widthPx) {
            if (widthPx <= 0) return@LaunchedEffect
            loaded = needed.associateWith {
                SceneLoader.load(context, it, widthPx, bandsNeeded = effect == null)
            }
        }

        // L'orologio della scena. Un valore in uno stato, letto **dentro il
        // disegno**: il fotogramma nuovo ridipinge e non ricompone, che e' la
        // regola di questa app su tutto cio' che si muove col dito o col tempo.
        val clock = remember { mutableFloatStateOf(0f) }
        LaunchedEffect(alive) {
            if (!alive) return@LaunchedEffect
            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    if (previous != 0L) {
                        clock.floatValue += ((now - previous) / 1_000_000_000.0).toFloat()
                    }
                    previous = now
                }
            }
        }

        val spoken = remember(kind) { "Scena: ${kind.name.lowercase().replace('_', ' ')}" }

        Box(Modifier.fillMaxSize().semantics { contentDescription = spoken }) {
            Canvas(Modifier.fillMaxSize()) {
                val here = loaded[transition.incoming] ?: return@Canvas
                val leaving = transition.outgoing?.let { loaded[it] }

                // Lo shader chiama `art` la scena che c'e' e `art2` quella che
                // arriva: durante un passaggio la prima e' quella che se ne va.
                val base = leaving ?: here
                val next = if (leaving != null) here else null
                val progress = if (leaving != null) transition.progress.value else 0f

                val time = clock.floatValue
                val wind = ((windSpeed ?: 0.0).toFloat() / WIND_FULL).coerceIn(-1f, 1f)
                val reach = size.minDimension * PARALLAX
                val tiltX = tilt.value.x * reach
                val tiltY = tilt.value.y * reach * 0.6f
                // **Oscilla, non trasla.** Una traslazione vera vorrebbe un
                // cielo ripetibile in orizzontale, che ai dipinti non e'
                // chiesto: senza, dopo un minuto il bordo si stirerebbe su
                // mezza scena. Un respiro lento largo il due per cento dice
                // "l'aria si muove" e non arriva mai a un bordo.
                val drift = sin(time * DRIFT_RATE * (0.35f + abs(wind))) *
                    size.width * DRIFT_SPAN * (0.3f + abs(wind))

                drawIntoCanvas { canvas ->
                    if (effect != null) {
                        effect.setPlates(base, next, size.width, size.height)
                        effect.setMotion(tiltX, tiltY, drift, progress)
                        effect.setLight(light.near, light.far, light.amount)
                        effect.draw(canvas.nativeCanvas, size.width, size.height)
                    } else {
                        bands?.draw(
                            canvas = canvas.nativeCanvas,
                            now = base,
                            next = next,
                            progress = progress,
                            tiltX = tiltX,
                            tiltY = tiltY,
                            drift = drift,
                            near = light.near,
                            far = light.far,
                            tint = light.amount,
                            width = size.width,
                            height = size.height,
                        )
                    }
                }

                // La precipitazione va **sopra** la scena arrivata, non sopra
                // quella che se ne va: durante il passaggio e' gia' il tempo
                // nuovo che si sta annunciando.
                val shown = if (progress > 0.5f) transition.incoming else base.kind
                drawSceneWeather(
                    kind = shown,
                    time = time,
                    intensity = intensityOf(precipitationMm),
                    wind = wind,
                    // **La neve non e' pioggia bianca, e non e' pioggia
                    // azzurra.** L'inchiostro della precipitazione partiva
                    // dall'azzurro della pioggia per tutti, e negli scatti i
                    // fiocchi uscivano celesti sopra una scena di neve: il
                    // colore diceva "acqua" mentre la forma diceva "ghiaccio".
                    // Quello che cade ghiacciato prende il bianco della nuvola,
                    // che il vetro ha gia' reso leggibile; la pioggia tiene il
                    // suo azzurro, che e' il colore con cui l'app la disegna
                    // dappertutto.
                    ink = when (shown) {
                        SceneKind.NEVE, SceneKind.GRANDINE -> colors.cloudCore
                        else -> lerp(colors.rain, Color.White, 0.35f)
                    },
                    flash = flashAt(time),
                )
            }
        }
    }
}

/**
 * La luce dell'ora, come due tinte di moltiplicazione.
 *
 * Le tinte del cielo si **normalizzano** prima di essere usate: divise per il
 * proprio canale piu' alto diventano una tinta pura, e a decidere quanto e'
 * buio ci pensa un fattore solo, ricavato da quanto e' giorno. Senza
 * normalizzare, moltiplicare per il grigio di mezzogiorno spegnerebbe il
 * dipinto proprio nell'ora piu' luminosa della giornata - il che e' il
 * contrario di quello che deve succedere.
 *
 * Il vicino prende il tono di mezzo fra zenit e orizzonte, il lontano prende
 * l'orizzonte: e' li' che l'aria si accumula, ed e' per questo che le montagne
 * lontane sono del colore del cielo e non del proprio.
 */
@Immutable
class SceneLight(val near: Color, val far: Color, val amount: Float)

@Composable
fun rememberSceneLight(sky: SkyState, colors: MeteoColors): SceneLight =
    remember(sky, colors) {
        val brightness = NIGHT_FLOOR + (1f - NIGHT_FLOOR) * sky.dayness
        SceneLight(
            near = pureTint(lerp(colors.skyZenith, colors.skyHorizon, 0.5f), brightness),
            far = pureTint(colors.skyHorizon, brightness * 0.92f),
            amount = TINT_AMOUNT,
        )
    }

private fun pureTint(color: Color, brightness: Float): Color {
    val peak = max(color.red, max(color.green, color.blue)).coerceAtLeast(0.001f)
    return Color(
        red = (color.red / peak * brightness).coerceIn(0f, 1f),
        green = (color.green / peak * brightness).coerceIn(0f, 1f),
        blue = (color.blue / peak * brightness).coerceIn(0f, 1f),
    )
}

/**
 * Da millimetri all'ora a "quanto e' fitta", da 0 a 1.
 *
 * Non lineare: fra zero e due millimetri c'e' tutta la differenza fra asciutto e
 * bagnato, fra venti e trenta non ne resta quasi nessuna che l'occhio possa
 * contare. La radice comprime la coda alta, che e' dove i numeri crescono e
 * l'impressione no.
 */
private fun intensityOf(mm: Double?): Float {
    val value = (mm ?: 0.0).toFloat().coerceAtLeast(0f)
    if (value <= 0f) return 0.18f
    return kotlin.math.sqrt(value / RAIN_FULL).coerceIn(0.18f, 1f)
}

/**
 * Il lampo, a intervalli irregolari.
 *
 * Due periodi che non sono multipli l'uno dell'altro: sommandoli, i bagliori
 * cadono ogni volta a distanza diversa, e un temporale che lampeggia a tempo
 * sarebbe un semaforo.
 */
private fun flashAt(time: Float): Float {
    fun pulse(period: Float, width: Float, offset: Float): Float {
        val phase = (time + offset) % period
        return if (phase < width) 1f - phase / width else 0f
    }
    return max(pulse(6.1f, 0.16f, 0f), pulse(9.7f, 0.13f, 3.3f) * 0.7f)
}


/** Quanto si sposta il punto piu' lontano, in frazione del lato corto. */
private const val PARALLAX = 0.045f

/** I metri al secondo che valgono vento pieno. */
private const val WIND_FULL = 14f

/** I millimetri all'ora che valgono pioggia piena. */
private const val RAIN_FULL = 8f

private const val DRIFT_RATE = 0.16f
private const val DRIFT_SPAN = 0.02f

/** Quanto scende la luce nella notte piena: non a zero, o non si vedrebbe nulla. */
private const val NIGHT_FLOOR = 0.30f

private const val TINT_AMOUNT = 0.62f
