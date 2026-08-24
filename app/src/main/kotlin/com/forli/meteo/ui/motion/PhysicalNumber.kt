package com.forli.meteo.ui.motion

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.forli.meteo.ui.render.ExtrudedText
import com.forli.meteo.ui.render.NumberMotion

/**
 * La cifra come solido che si puo' far girare.
 *
 * Il dito non la sposta piu': ne cambia l'orientamento. Con facce che
 * conoscono la propria normale, ruotare la luce le riombreggia una per una, e
 * la differenza rispetto a una traslazione e' esattamente cio' che distingue
 * un oggetto da un'immagine.
 *
 * L'angolo resta dove lo lasci: non torna indietro da solo, perche' scegliere
 * l'inquadratura e' una decisione di chi guarda.
 */
@Composable
fun PhysicalNumber(
    text: String,
    fontSize: Dp,
    tilt: Offset,
    modifier: Modifier = Modifier,
    depth: Dp = fontSize * 0.15f,
    verticalBias: Float = -0.08f,
) {
    var dragDegrees by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier.pointerInput(Unit) {
            // Solo orizzontale. Con detectDragGestures la cifra si prendeva
            // qualunque direzione, quindi trascinare verso l'alto per aprire il
            // dettaglio la faceva ruotare e basta: il gesto piu' importante
            // dell'app veniva ingoiato da quello decorativo.
            detectHorizontalDragGestures { change, delta ->
                change.consume()
                dragDegrees += delta * DEGREES_PER_PIXEL
            }
        },
    ) {
        ExtrudedText(
            text = text,
            fontSize = fontSize,
            depth = depth,
            modifier = Modifier.fillMaxSize(),
            verticalBias = verticalBias,
            motion = NumberMotion(
                orientationDeg = NumberMotion.REST_ORIENTATION +
                    dragDegrees +
                    tilt.x * TILT_SWING,
            ),
        )
    }
}

/** Uno schermo di trascinamento vale circa un quarto di giro. */
private const val DEGREES_PER_PIXEL = 0.09f

/** Quanto la luce ruota all'inclinazione massima del telefono. */
private const val TILT_SWING = 26f
