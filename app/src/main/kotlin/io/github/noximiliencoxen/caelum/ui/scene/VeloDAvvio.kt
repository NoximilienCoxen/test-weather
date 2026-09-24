package io.github.noximiliencoxen.caelum.ui.scene

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import kotlinx.coroutines.delay

/**
 * Il velo d'apertura: la scena a tutto schermo, il nome, e poi si scioglie.
 *
 * Resta finche' non ci sono i primi dati - la galleria senza dati e' una
 * schermata vuota con scritto "in attesa", che e' il modo peggiore di
 * cominciare - ma **mai meno di [MINIMO_MS]**, se no un avvio veloce la
 * farebbe balenare come un difetto, e **mai piu' di [MASSIMO_MS]**: una rete
 * lenta non deve tenere nessuno davanti a un quadretto.
 */
@Composable
fun VeloDAvvio(
    scena: Scena,
    notte: Boolean,
    movimento: Boolean,
    /** Vero quando dietro c'e' qualcosa da mostrare. */
    pronto: Boolean,
    modifier: Modifier = Modifier,
) {
    var visibile by remember { mutableStateOf(true) }
    val prontoOra by rememberUpdatedState(pronto)
    LaunchedEffect(Unit) {
        delay(MINIMO_MS)
        var atteso = MINIMO_MS
        while (!prontoOra && atteso < MASSIMO_MS) {
            delay(100)
            atteso += 100
        }
        visibile = false
    }
    AnimatedVisibility(visible = visibile, exit = fadeOut(tween(600)), modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScenaAnimata(scena = scena, notte = notte, movimento = movimento, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f))),
            )
            Column(
                modifier = Modifier.fillMaxSize().systemBarsPadding().padding(bottom = 48.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Solo il nome. Sotto c'era scritto cosa rappresentava la scena,
                // e un quadro con la didascalia si legge invece di guardarlo.
                Text(text = "Caelum", style = SalaType.pageTitle, color = SalaTokens.neutral100)
            }
        }
    }
}

// Meno di un secondo e mezzo, sul telefono, non si faceva in tempo a
// vederla: dopo lo splash di sistema restavano pochi fotogrammi e poi la
// dissolvenza, e sembrava un difetto invece di un saluto.
private const val MINIMO_MS = 1600L
private const val MASSIMO_MS = 3200L
