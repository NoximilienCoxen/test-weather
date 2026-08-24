package com.forli.meteo.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.render.ExtrudedText
import com.forli.meteo.ui.render.NumberMotion
import kotlinx.coroutines.launch

/**
 * La cifra gigante come oggetto che si puo' spingere.
 *
 * Il dito la sposta con resistenza crescente oltre una certa distanza, e al
 * rilascio torna al centro con una molla poco smorzata: e' il rimbalzo, non lo
 * spostamento, a dare la sensazione di massa.
 */
@Composable
fun PhysicalNumber(
    text: String,
    fontSize: Dp,
    tilt: Offset,
    modifier: Modifier = Modifier,
    depth: Dp = fontSize * 0.26f,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val push = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    val freeTravel = with(density) { 44.dp.toPx() }
    val sheenTravel = with(density) { 5.dp.toPx() }

    Box(
        modifier = modifier.pointerInput(freeTravel) {
            detectDragGestures(
                onDrag = { change, delta ->
                    change.consume()
                    scope.launch { push.snapTo(resist(push.value + delta, freeTravel)) }
                },
                onDragEnd = { scope.launch { push.animateTo(Offset.Zero, RETURN) } },
                onDragCancel = { scope.launch { push.animateTo(Offset.Zero, RETURN) } },
            )
        },
    ) {
        ExtrudedText(
            text = text,
            fontSize = fontSize,
            depth = depth,
            modifier = Modifier.fillMaxSize(),
            motion = NumberMotion(
                tilt = tilt,
                push = push.value,
                sheenShift = tilt.x * sheenTravel,
            ),
        )
    }
}

/**
 * Oltre la corsa libera lo spostamento continua, ma sempre piu' controvoglia.
 * Un limite netto sembrerebbe un difetto; questa resistenza sembra un elastico.
 */
private fun resist(raw: Offset, freeTravel: Float): Offset {
    val distance = raw.getDistance()
    if (distance <= freeTravel || distance == 0f) return raw
    val compressed = freeTravel + (distance - freeTravel) * 0.32f
    return raw / distance * compressed
}

private val RETURN = spring<Offset>(dampingRatio = 0.45f, stiffness = 300f)
