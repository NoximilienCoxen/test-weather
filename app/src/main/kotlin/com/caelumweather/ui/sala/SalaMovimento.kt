package com.caelumweather.ui.sala

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/**
 * Il movimento delle schede e dei comandi, in un posto solo.
 *
 * `SalaMolle.kt` tiene le molle del cielo; qui stanno quelle di cio' che si
 * tocca e si legge: la scheda che entra coi suoi pezzi in ritardo, il comando
 * che cede sotto il dito, la barra che cresce, la riga che compare.
 *
 * **Tutte obbediscono a [LocalMovimento].** E' falso con le animazioni ridotte
 * (dell'app o del telefono) e durante la cattura: li' ogni cosa qui dentro
 * salta al valore finale, come se il file non esistesse. Chi aggiunge
 * un'animazione la appende a quel valore, non a un parametro suo: sette sale
 * che si fermano una per una sono sette posti in cui dimenticarsene una.
 *
 * **E nessuna resta accesa.** Partono da un gesto o da un dato che cambia e
 * finiscono: da fermo non si disegna un fotogramma in piu' (trappola #8). Le
 * eccezioni dichiarate restano il cielo e l'onda del mare.
 */
val LocalMovimento = staticCompositionLocalOf { true }

/**
 * Di quanto la scheda e' lontana dal centro del carosello, in pagine e col
 * segno: zero quando e' posata, negativa mentre arriva dal basso.
 *
 * E' una funzione e non un numero perche' si legge **mentre si posa la
 * scheda**, non mentre la si compone: scorrere sposta i pezzi, non li rifa'.
 */
val LocalScartoSala = staticCompositionLocalOf<() -> Float> { { 0f } }

/** Di quanto resta indietro ogni pezzo, per pagina di distanza e per posto in fila. */
private val RITARDO_PEZZO = 26.dp

/** Dal sesto pezzo in poi il ritardo non cresce: una scheda lunga non si sfilaccia. */
private const val PEZZI_IN_RITARDO = 5

/** Quanto sbiadisce ogni pezzo, alla stessa distanza e per posto in fila. */
private const val VELO_PEZZO = 0.16f

/**
 * La colonna di una scheda, coi pezzi che **arrivano uno dopo l'altro**.
 *
 * Mentre la scheda entra dal basso, il titolo viaggia con lei e cio' che sta
 * sotto resta un poco indietro - tanto piu' indietro quanto piu' sta in basso -
 * e la raggiunge quando si posa. Uscendo, o tornando dalla sala dopo, il verso
 * si rovescia: resta indietro cio' che sta in alto. E' tutto guidato dal dito:
 * non c'e' un orologio, e una scheda ferma non ha niente da disegnare.
 *
 * Impila i figli come un `Column` senza pesi ne' allineamenti (le sale non ne
 * usano a questo livello: stanno tutti dentro le righe). Ogni figlio ha il
 * suo strato, ed e' li' che si leggono scarto e trasparenza.
 */
@Composable
internal fun ColonnaInRitardo(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scarto = LocalScartoSala.current
    Layout(content = content, modifier = modifier) { misurabili, vincoli ->
        val perFiglio = vincoli.copy(minWidth = 0, minHeight = 0)
        val posati = misurabili.map { it.measure(perFiglio) }
        val larghezza = maxOf(vincoli.minWidth, posati.maxOfOrNull { it.width } ?: 0)
        val altezza = posati.sumOf { it.height }.coerceIn(vincoli.minHeight, vincoli.maxHeight)
        val passo = RITARDO_PEZZO.toPx()
        val ultimo = posati.lastIndex
        layout(larghezza, altezza) {
            var y = 0
            posati.forEachIndexed { indice, pezzo ->
                pezzo.placeRelativeWithLayer(0, y) {
                    val s = scarto()
                    if (s != 0f) {
                        // Dal basso (s negativo) e' in ritardo chi sta sotto;
                        // dall'alto, chi sta sopra.
                        val posto = if (s < 0f) indice else ultimo - indice
                        val peso = min(posto, PEZZI_IN_RITARDO).toFloat()
                        translationY = -s * passo * peso
                        alpha = (1f - abs(s) * VELO_PEZZO * peso).coerceIn(0f, 1f)
                    }
                }
                y += pezzo.height
            }
        }
    }
}

/**
 * Un comando che **cede sotto il dito**: si stringe di poco finche' e' premuto
 * e torna con un rimbalzo appena accennato.
 *
 * **Va messo prima del fondo** (`.cedevole().clip(..).background(..)`), cosi'
 * si stringe il comando intero e non solo la scritta. Guarda il dito senza
 * consumare niente: il tocco resta a chi lo prendeva prima, e quando lo prende
 * uno scorrimento il comando torna su da se'.
 */
fun Modifier.cedevole(/** Quanto si stringe: di meno i comandi larghi, di piu' i piccoli. */ premuto: Float = 0.96f): Modifier = composed {
    if (!LocalMovimento.current) return@composed this
    var giu by remember { mutableStateOf(false) }
    val scala by animateFloatAsState(
        targetValue = if (giu) premuto else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 650f, visibilityThreshold = 0.0005f),
        label = "cede",
    )
    this
        .graphicsLayer {
            scaleX = scala
            scaleY = scala
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                giu = true
                try {
                    waitForUpOrCancellation()
                } finally {
                    giu = false
                }
            }
        }
}

/**
 * Una misura che **arriva** invece di saltare: l'altezza di una barra quando
 * cambia l'ora, e le colonne si riassestano come un'onda invece di scattare.
 */
@Composable
fun misuraCheArriva(bersaglio: Dp): Dp {
    val movimento = LocalMovimento.current
    val misura by animateDpAsState(
        targetValue = bersaglio,
        animationSpec = if (movimento) {
            spring(dampingRatio = 0.72f, stiffness = 260f, visibilityThreshold = 0.25.dp)
        } else {
            snap()
        },
        label = "misura",
    )
    return misura
}

/**
 * Da zero a uno **una volta**, quando il pezzo compare: le colonnine che
 * crescono dal fondo, una curva che si disegna. Poi resta a uno e non disegna
 * piu' niente. Con le animazioni ridotte e' subito uno.
 *
 * [ritardo] sfalsa i pezzi di una fila, perche' arrivino come un'onda.
 */
@Composable
fun crescitaAllArrivo(ritardo: Int = 0): Float {
    val movimento = LocalMovimento.current
    val valore = remember { androidx.compose.animation.core.Animatable(if (movimento) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(movimento) {
        if (movimento) {
            valore.animateTo(1f, tween(durationMillis = 520, delayMillis = ritardo, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        } else {
            valore.snapTo(1f)
        }
    }
    return valore.value
}

/**
 * Un colore che **passa** invece di scattare: il fondo del giorno scelto, la
 * colonna dell'ora scelta. Due decimi di secondo, e poi fermo.
 */
@Composable
fun coloreChePassa(bersaglio: Color): Color {
    val movimento = LocalMovimento.current
    val colore by animateColorAsState(
        targetValue = bersaglio,
        animationSpec = if (movimento) tween(200) else snap(),
        label = "colore",
    )
    return colore
}

/**
 * Un angolo che gira **per la via piu' corta**: da 350° a 10° sono venti gradi
 * in avanti, non trecentoquaranta indietro. La lancetta della rosa, quando
 * cambia l'ora.
 *
 * Il bersaglio si tiene "srotolato": a ogni cambio si aggiunge lo scarto piu'
 * corto all'angolo di prima, e la molla non vede mai il salto a zero.
 */
@Composable
fun angoloCheGira(gradi: Float): Float {
    val movimento = LocalMovimento.current
    // Un ricordo e non uno stato: serve solo a sapere da dove si viene, e
    // scriverlo mentre si compone non deve far ricomporre nessuno.
    val srotolato = remember { floatArrayOf(gradi) }
    val scarto = ((gradi - srotolato[0]) % 360f + 540f) % 360f - 180f
    srotolato[0] += scarto
    val angolo by animateFloatAsState(
        targetValue = srotolato[0],
        animationSpec = if (movimento) {
            spring(dampingRatio = 0.62f, stiffness = 120f, visibilityThreshold = 0.1f)
        } else {
            snap()
        },
        label = "angolo",
    )
    return angolo
}

/**
 * Un contenitore che **cambia altezza scivolando** invece che a scatti: la
 * scheda che si allunga quando arriva "un anno fa", quando compare il giorno
 * scelto, quando una sezione si apre. Messo una volta in [PannelloSala], vale
 * per ogni sala.
 */
fun Modifier.altezzaMorbida(): Modifier = composed {
    if (!LocalMovimento.current) return@composed this
    this.animateContentSize(spring(dampingRatio = 0.9f, stiffness = 380f))
}

/**
 * Una riga che compare **aprendo il proprio spazio**, invece di spingere di
 * colpo cio' che ha sotto: le pastiglie sotto l'intestazione, le scelte che
 * escono sotto un interruttore acceso.
 */
@Composable
fun ComparsaMorbida(
    visibile: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!LocalMovimento.current) {
        if (visibile) content()
        return
    }
    AnimatedVisibility(
        visible = visibile,
        modifier = modifier,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 420f), expandFrom = Alignment.Top) +
            fadeIn(tween(180, delayMillis = 60)),
        exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 520f), shrinkTowards = Alignment.Top) +
            fadeOut(tween(110)),
    ) { content() }
}

/**
 * Un testo che **si scambia** quando cambia: quello di prima sale e sbiadisce,
 * quello nuovo arriva da sotto. Il nome della citta', la pastiglia degli
 * avvisi. La larghezza segue il testo nuovo senza ritagliare.
 */
@Composable
fun <T> ScambioMorbido(
    stato: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable (T) -> Unit,
) {
    if (!LocalMovimento.current) {
        content(stato)
        return
    }
    AnimatedContent(
        targetState = stato,
        modifier = modifier,
        contentAlignment = contentAlignment,
        transitionSpec = {
            (fadeIn(tween(220, delayMillis = 70)) + slideInVertically(tween(260)) { it / 3 })
                .togetherWith(fadeOut(tween(130)) + slideOutVertically(tween(180)) { -it / 3 })
                .using(SizeTransform(clip = false))
        },
        label = "scambio",
    ) { mostrato -> content(mostrato) }
}

/**
 * [CifreCheScorrono] che obbedisce a [LocalMovimento]: per i numeri grandi
 * delle sale, che cambiano trascinando la barra delle ore.
 */
@Composable
fun NumeroCheScorre(
    testo: String,
    valore: Int?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    CifreCheScorrono(
        testo = testo,
        valore = valore,
        style = style,
        color = color,
        ferme = !LocalMovimento.current,
        modifier = modifier,
    )
}

/**
 * L'onda che parte da una pastiglia e si allarga sulla scheda: il giorno
 * toccato "si spande" nella scheda mentre i numeri cambiano. La disegna
 * [PannelloSala] dentro il proprio ritaglio; chi tocca dice da dove parte, in
 * coordinate della finestra.
 */
class OndaScheda internal constructor() {
    internal var centro by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    internal val corsa = androidx.compose.animation.core.Animatable(1f)

    /** Fa partire l'onda da [da] (coordinate della finestra). */
    suspend fun parti(da: androidx.compose.ui.geometry.Offset) {
        centro = da
        corsa.snapTo(0f)
        corsa.animateTo(1f, tween(durationMillis = 620, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
}

/** Un'onda per scheda, ricordata. */
@Composable
fun rememberOndaScheda(): OndaScheda = remember { OndaScheda() }

/** ESPERIMENTO temporaneo: falso spegne icone che reagiscono, battito dell'allerta e colori della curva. */
internal const val GESTI_ACCESI = true

/** ESPERIMENTO temporaneo: falso spegne l'onda dal giorno toccato e i suoi rilevamenti di posizione. */
internal const val ONDA_ACCESA = false

/** ESPERIMENTO temporaneo: falso spegne le icone dei giorni che reagiscono al tocco. */
internal const val ICONE_ACCESE = false
