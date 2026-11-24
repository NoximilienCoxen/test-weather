package com.caelumweather.ui.sala

import com.caelumweather.lingua.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/**
 * "Scorri in su", sotto la prima sala, finche' non si e' sfogliato una volta.
 *
 * Il gesto che cambia sala non si vede: la colonna di destra dice che le sale
 * stanno una sotto l'altra, ma non che si scorrono. Chi apre l'app per la
 * prima volta lo impara qui, una volta, e poi l'indizio sparisce per sempre
 * (`SettingsPrefs.saleSfogliate`).
 *
 * La freccia ondeggia appena, perche' e' il movimento che la rende un invito e
 * non un'etichetta; con le animazioni ridotte sta ferma.
 */
@Composable
internal fun IndizioSfoglio(
    visibile: Boolean,
    prossima: String,
    palette: SalaPalette,
    movimento: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visibile,
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val onda = if (movimento) {
            val transizione = rememberInfiniteTransition(label = "indizio")
            val v by transizione.animateFloat(
                initialValue = 0f,
                targetValue = -4f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "freccia",
            )
            v
        } else {
            0f
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "↑",
                style = SalaType.rowTitle,
                color = palette.accent,
                modifier = Modifier.offset(y = onda.dp),
            )
            Text(
                text = tr("  Scorri in su · $prossima", "  Swipe up · $prossima", "  Nach oben wischen · $prossima", "  Balayez vers le haut · $prossima", "  Desliza hacia arriba · $prossima"),
                style = SalaType.sectionLabel,
                color = palette.inkSoft,
            )
        }
    }
}


/**
 * Dove sei fra le sette sale, e un tocco per andarci: sette puntini sopra la
 * barra delle ore, al posto della colonna di icone (CONTESTO §77).
 *
 * La colonna faceva lo stesso mestiere con sette dischi sempre accesi sul
 * bordo destro, e si mangiava quaranta punti di scheda e un pezzo di cielo.
 * Qui la sala in scena e' un trattino allungato nell'accento, le altre un
 * puntino; ogni puntino ha un bersaglio di 32 punti e il nome della sala per
 * TalkBack. Le sale si sfogliano comunque col dito, come prima.
 */
@Composable
internal fun PuntiniSale(
    sale: List<SalaRoom>,
    corrente: Int,
    palette: SalaPalette,
    onVai: (SalaRoom) -> Unit,
    modifier: Modifier = Modifier,
) {
    val movimento = LocalMovimento.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sale.forEachIndexed { i, sala ->
            val attiva = i == corrente
            val larghezza by animateDpAsState(
                if (attiva) 18.dp else 6.dp,
                animationSpec = if (movimento) spring(dampingRatio = 0.5f, stiffness = 420f) else snap(),
                label = "puntino",
            )
            val colore by animateColorAsState(if (attiva) palette.accentSuCielo else palette.inkSuCielo.copy(alpha = 0.45f), label = "puntino-colore")
            // **L'arrivo si vede, e dice dove.** Quando una sala entra in
            // scena il suo trattino si scioglie nella figuretta della sala,
            // viva - la nuvola che lascia cadere le gocce, il sole che gira,
            // la manica che sventola - per poco piu' di un secondo, e poi
            // torna trattino (`disegnaIconaViva`). Solo al cambio, non
            // all'apertura dell'app; con le animazioni ridotte niente.
            val comparsa = remember { Animatable(0f) }
            val fase = remember { Animatable(0f) }
            var eraAttiva by remember { mutableStateOf(attiva) }
            LaunchedEffect(attiva) {
                val arrivata = attiva && !eraAttiva
                eraAttiva = attiva
                if (!attiva) {
                    comparsa.animateTo(0f, tween(durationMillis = 140))
                    return@LaunchedEffect
                }
                if (!arrivata || !movimento) return@LaunchedEffect
                val giro = launch {
                    fase.snapTo(0f)
                    fase.animateTo(2f, tween(durationMillis = 2000, easing = LinearEasing))
                }
                comparsa.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
                delay(1250)
                comparsa.animateTo(0f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
                giro.cancel()
            }
            val coloreIcona = palette.accentSuCielo
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 28.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = sala.heading) { onVai(sala) }
                    .semantics {
                        contentDescription = sala.heading
                        selected = attiva
                    },
                contentAlignment = Alignment.Center,
            ) {
                val c = comparsa.value
                Box(
                    modifier = Modifier
                        .size(width = larghezza, height = 6.dp)
                        .graphicsLayer {
                            val via = c.coerceIn(0f, 1f)
                            alpha = 1f - via
                            scaleX = 1f - 0.6f * via
                            scaleY = 1f - 0.6f * via
                        }
                        .clip(CircleShape)
                        .background(colore),
                )
                if (c > 0.01f) {
                    Canvas(
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                alpha = c.coerceIn(0f, 1f)
                                scaleX = 0.4f + 0.6f * c
                                scaleY = 0.4f + 0.6f * c
                            },
                    ) {
                        disegnaIconaViva(sala, coloreIcona, fase.value, c.coerceIn(0f, 1f))
                    }
                }
            }
        }
    }
}
