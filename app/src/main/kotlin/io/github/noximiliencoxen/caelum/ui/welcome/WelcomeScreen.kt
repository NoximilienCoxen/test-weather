package io.github.noximiliencoxen.caelum.ui.welcome

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.common.MinTouchTarget
import io.github.noximiliencoxen.caelum.ui.sala.LocalAcquerello
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.granaDiCarta
import kotlinx.coroutines.delay

private const val HANDOVER_MS = 900L

/**
 * L'Ingresso: la prima sala, quella che si attraversa una volta sola.
 *
 * **Il mappamondo non c'e' piu'.** Girava, si fermava sulla longitudine di
 * casa e ci piantava uno spillo: era il pezzo piu' ingegnoso della vecchia
 * schermata, ed e' uscito perche' raccontava la cosa sbagliata. Caelum adesso
 * e' una galleria, e una galleria non si apre con un globo che cerca: si apre
 * con una parete, un cartellino e una frase. Il codice resta nella cronologia
 * (`ui/render3d/Bodies.kt::globe` e' ancora li', lo usano i widget).
 *
 * La frase e' **di un artista vero e verificata** (vedi `Citazioni.kt`), e
 * cambia a ogni primo avvio. Si sceglie una volta sola, in `remember`: pescarla
 * a ogni ricomposizione la farebbe cambiare sotto gli occhi di chi legge.
 */
@Composable
fun WelcomeScreen(
    state: UiState,
    onFindMe: () -> Unit,
    onChooseByHand: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val acquerello = LocalAcquerello.current
    val citazione = remember { citazioneACaso() }

    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onFindMe() else onChooseByHand() }

    // Trovato il posto, un attimo per farlo vedere e la schermata cede il passo.
    LaunchedEffect(state.followsLocation) {
        if (state.followsLocation) {
            delay(HANDOVER_MS)
            onDone()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SalaTokens.bg)
            .drawBehind {
                granaDiCarta(acquerello, forza = 0.09f)
                lavaggiDIngresso()
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(start = 30.dp, end = 30.dp, top = 40.dp, bottom = 30.dp),
        ) {
            Text(
                text = "CAELUM",
                style = SalaType.roomLabel,
                color = SalaTokens.text,
            )

            Box(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    // **Il corpo si adatta alla lunghezza della frase**, come
                    // farebbe chi impagina a mano un cartellino da parete. Col
                    // corpo fisso la citazione di Constable - centocinquanta
                    // battute - riempiva lo schermo da cima a fondo e scacciava
                    // tutto il resto, mentre quella di una riga ci nuotava
                    // dentro. Sono frasi scritte da altri: la lunghezza non la
                    // sceglie chi impagina.
                    val corpo = when {
                        citazione.testo.length > 140 -> 25
                        citazione.testo.length > 85 -> 32
                        else -> 42
                    }
                    Text(
                        text = citazione.testo,
                        style = SalaType.pageTitle.copy(
                            fontSize = corpo.sp,
                            lineHeight = (corpo * 1.12f).sp,
                        ),
                        color = SalaTokens.text,
                    )
                    Text(
                        text = citazione.autore,
                        style = SalaType.body.copy(fontStyle = FontStyle.Italic),
                        color = SalaTokens.accent700,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = quandoDice(state),
                        style = SalaType.body,
                        color = SalaTokens.text.copy(alpha = 0.72f),
                        modifier = Modifier.padding(top = 28.dp),
                    )
                }
            }

            // Mentre cerca, il posto della riga sotto il titolo lo prende la
            // scritta che respira: e' l'unica animazione permanente concessa,
            // e dura solo finche' dura l'attesa.
            if (state.locating) {
                ScrittaCheRespira(
                    testo = "Guardando il cielo…",
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }

            PulsanteDIngresso(
                testo = "Trovami",
                onClick = { askPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            )

            Text(
                text = "Scelgo io la città",
                style = SalaType.body,
                color = SalaTokens.accent700,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(MinTouchTarget)
                    .clickable(onClick = onChooseByHand)
                    .padding(top = 12.dp),
            )
        }
    }
}

/** Cosa dice la riga sotto la frase, secondo cosa sta succedendo. */
private fun quandoDice(state: UiState): String = when {
    state.followsLocation -> "Trovato. Apro il percorso."
    state.locationUnavailable -> "Non riesco a trovarti. Puoi scegliere la città a mano."
    else -> "Per aprire il percorso mi serve sapere da dove guardi il cielo."
}

/**
 * I tre lavaggi dell'Ingresso: azzurro in alto, giallo a meta', rosa in fondo.
 *
 * Sono fissi e non dipendono dal tempo, che qui non si conosce ancora: e' una
 * parete d'ingresso, non una previsione.
 */
private fun DrawScope.lavaggiDIngresso() {
    val w = size.width
    val h = size.height
    fun macchia(cx: Float, cy: Float, r: Float, colore: Color) {
        val centro = Offset(cx, cy)
        drawCircle(
            brush = Brush.radialGradient(
                0f to colore,
                0.7f to colore.copy(alpha = 0f),
                center = centro,
                radius = r,
            ),
            radius = r,
            center = centro,
        )
    }
    macchia(w * 0.42f, h * 0.20f, w * 0.78f, SalaTokens.accent.copy(alpha = 0.22f))
    macchia(w * 0.86f, h * 0.44f, w * 0.62f, SalaTokens.processYellow.copy(alpha = 0.26f))
    macchia(w * 0.44f, h * 0.72f, w * 0.72f, SalaTokens.accent2_400.copy(alpha = 0.20f))
}

/**
 * La scritta che respira mentre i dati arrivano.
 *
 * **Due trappole del progetto la riguardano, e nessuna delle due la vieta.**
 *
 * La #8 dice che da fermo l'app deve disegnare zero fotogrammi. Qui pero' fermi
 * non si e': un'attesa in cui niente si muove e' indistinguibile da un'app
 * bloccata, ed e' esattamente la domanda a cui questa scritta risponde. Esiste
 * solo mentre `locating` e' vero, e con l'attesa finisce anche lei.
 *
 * La #17 dice che `rememberInfiniteTransition` "qui non anima", e va letta per
 * intero: non animava dove il valore si leggeva **solo dentro il disegno** e mai
 * in composizione - erano le gocce di pioggia. Qui il valore finisce nel colore
 * di un `Text`, cioe' in composizione, che e' il caso in cui la comodita'
 * funziona. Ricomporre una riga di testo per un secondo di attesa e' un prezzo
 * diverso dal ricomporre la scena intera per sempre.
 */
@Composable
private fun ScrittaCheRespira(testo: String, modifier: Modifier = Modifier) {
    val respiro = rememberInfiniteTransition(label = "respiro")
    val alfa by respiro.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alfa",
    )
    Text(
        text = testo,
        style = SalaType.sectionLabel,
        color = SalaTokens.accent700.copy(alpha = alfa),
        modifier = modifier,
    )
}

/** Il comando pieno dell'Ingresso: uno solo, e si vede che e' quello. */
@Composable
private fun PulsanteDIngresso(testo: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(SalaTokens.accent700, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = testo,
            style = SalaType.value,
            color = SalaTokens.neutral100,
        )
    }
}
