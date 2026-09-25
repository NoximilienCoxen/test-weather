package io.github.noximiliencoxen.caelum.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Disegna la scena al tempo [t] (in secondi). Fuori dal composable per le prove su PNG. */
fun DrawScope.disegnaScena(scena: Scena, t: Float, notte: Boolean) {
    clipRect {
        when (scena) {
            Scena.MARE -> scenaMare(t, notte)
            Scena.CITTA_NATALE -> scenaCittaNatale(t, notte)
            Scena.TEMPESTA -> scenaTempesta(t, notte)
            Scena.FERMATA -> scenaFermata(t, notte)
            Scena.GRANO -> scenaGrano(t, notte)
            Scena.ARCOBALENO -> scenaArcobaleno(t, notte)
            Scena.MONTAGNA -> scenaMontagna(t, notte)
        }
    }
}

/**
 * La scena che si muove. Il tempo scorre solo con [movimento]: con le
 * animazioni ridotte resta un fotogramma scelto, non il primo - al tempo zero
 * la pioggia non e' ancora cominciata e il mulino e' dritto.
 */
@Composable
fun ScenaAnimata(scena: Scena, notte: Boolean, movimento: Boolean, modifier: Modifier = Modifier) {
    var t by remember { mutableFloatStateOf(FOTOGRAMMA_FERMO) }
    if (movimento) {
        LaunchedEffect(scena) {
            val inizio = withFrameMillis { it }
            while (true) {
                withFrameMillis { t = FOTOGRAMMA_FERMO + (it - inizio) / 1000f }
            }
        }
    }
    Canvas(modifier = modifier.semantics { contentDescription = "${scena.nome}, ${if (notte) "di notte" else "di giorno"}" }) {
        disegnaScena(scena, t, notte)
    }
}

private const val FOTOGRAMMA_FERMO = 1.35f
