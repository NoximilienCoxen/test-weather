package com.forli.meteo.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import com.forli.meteo.ui.render.ExtrudedText
import com.forli.meteo.ui.render.NumberMotion
import com.forli.meteo.ui.render3d.SceneContact

/**
 * La cifra come solido che si puo' far girare.
 *
 * Il dito non la sposta e non le cambia solo la luce: ne cambia l'inquadratura.
 * Con una camera prospettica la faccia frontale si accorcia e il fianco si
 * scopre, che e' la differenza fra un oggetto e un'immagine di un oggetto.
 *
 * Il gesto non e' qui dentro: sta un livello sopra, perche' lo stesso
 * orientamento comanda anche la scultura. Girare solo la cifra farebbe due
 * animazioni scollegate invece di una scena.
 */
@Composable
fun PhysicalNumber(
    text: String,
    fontSize: Dp,
    rotation: SceneRotation,
    tilt: State<Offset>,
    modifier: Modifier = Modifier,
    depth: Dp = fontSize * 0.17f,
    verticalBias: Float = 0f,
    contact: SceneContact? = null,
) {
    ExtrudedText(
        text = text,
        fontSize = fontSize,
        depth = depth,
        modifier = modifier,
        verticalBias = verticalBias,
        contact = contact,
        motion = {
            // L'inclinazione del telefono aggiunge poco, ed e' giusto cosi': e'
            // il respiro dell'oggetto in mano, non un secondo comando.
            NumberMotion(
                yawDeg = rotation.yawDeg + tilt.value.x * TILT_YAW,
                pitchDeg = tilt.value.y * TILT_PITCH,
            )
        },
    )
}

private const val TILT_YAW = 7f
private const val TILT_PITCH = 5f
