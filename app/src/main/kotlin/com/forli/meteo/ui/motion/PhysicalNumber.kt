package com.forli.meteo.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.forli.meteo.ui.render.ExtrudedText
import com.forli.meteo.ui.render.NumberMotion
import com.forli.meteo.ui.render3d.SceneContact
import com.forli.meteo.ui.render3d.Skyline

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
    /** Quello che si posa sopra la cifra: oggi la neve, e per ora solo lei. */
    overlay: (DrawScope.(Skyline) -> Unit)? = null,
) {
    ExtrudedText(
        text = text,
        fontSize = fontSize,
        depth = depth,
        modifier = modifier,
        verticalBias = verticalBias,
        contact = contact,
        overlay = overlay,
        motion = {
            // L'inclinazione del telefono non e' un secondo comando, ma deve
            // sentirsi: e' quello che distingue un oggetto tenuto in mano da un
            // disegno stampato sul vetro. I gradi li decide DeviceTilt, che li
            // detta anche alla scultura: sono lo stesso oggetto.
            NumberMotion(
                yawDeg = rotation.yawDeg + tilt.value.x * TILT_YAW_DEGREES,
                pitchDeg = tilt.value.y * TILT_PITCH_DEGREES,
            )
        },
    )
}

