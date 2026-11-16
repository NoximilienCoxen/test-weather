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
                text = "←",
                style = SalaType.rowTitle,
                color = palette.accent,
                modifier = Modifier.offset(x = onda.dp),
            )
            Text(
                text = tr("  Scorri di lato · $prossima", "  Swipe sideways · $prossima", "  Zur Seite wischen · $prossima", "  Balayez sur le côté · $prossima", "  Desliza hacia el lado · $prossima"),
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
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sale.forEachIndexed { i, sala ->
            val attiva = i == corrente
            val larghezza by animateDpAsState(if (attiva) 18.dp else 6.dp, label = "puntino")
            val colore by animateColorAsState(if (attiva) palette.accentSuCielo else palette.inkSuCielo.copy(alpha = 0.45f), label = "puntino-colore")
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
                Box(
                    modifier = Modifier
                        .size(width = larghezza, height = 6.dp)
                        .clip(CircleShape)
                        .background(colore),
                )
            }
        }
    }
}
