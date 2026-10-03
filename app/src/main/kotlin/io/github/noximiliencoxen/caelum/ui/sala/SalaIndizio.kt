package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.lingua.tr
import androidx.compose.animation.AnimatedVisibility
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
                text = tr("  Scorri in su · $prossima", "  Swipe up · $prossima"),
                style = SalaType.sectionLabel,
                color = palette.inkSoft,
            )
        }
    }
}
