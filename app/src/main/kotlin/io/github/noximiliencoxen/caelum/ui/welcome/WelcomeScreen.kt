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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.common.MinTouchTarget
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import kotlinx.coroutines.delay

/** Quanto resta in scena dopo aver trovato il posto, prima di cedere il passo. */
private const val HANDOVER_MS = 900L

/**
 * Il benvenuto: un cielo, una frase, e una sola cosa da fare.
 *
 * **Non si chiama piu' "Sette sale, un cielo".** Chi guarda non chiama queste
 * schermate "sale" - e' un nome buono per il codice, non per chi legge - e il
 * titolo lo diceva prima ancora che l'app avesse mostrato qualcosa. Adesso dice
 * cosa fa: il cielo, ora per ora.
 *
 * **La frase dell'artista e' uscita di scena.** Era verificata e attribuita con
 * cura, ed e' uscita lo stesso: una citazione in apertura chiede di leggere
 * prima di guardare, e questa schermata esiste per il contrario. Il file
 * `Citazioni.kt` e' uscito con lei; resta nella cronologia (`git show`) per chi
 * volesse rimetterla altrove, con le fonti gia' controllate una per una.
 */
@Composable
fun WelcomeScreen(
    state: UiState,
    onFindMe: () -> Unit,
    onChooseByHand: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chiediPermesso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concesso -> if (concesso) onFindMe() else onChooseByHand() }

    // Trovato il posto, un attimo per farlo vedere e la schermata cede il passo.
    LaunchedEffect(state.followsLocation) {
        if (state.followsLocation) {
            delay(HANDOVER_MS)
            onDone()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4ECE0))
            .systemBarsPadding()
            .padding(start = 30.dp, end = 30.dp, top = 26.dp, bottom = 34.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(210.dp)) { cieloDIngresso() }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = "Il cielo,\nora per ora",
                style = SalaType.pageTitle,
                color = SalaTokens.neutral900,
            )
            Text(
                text = "Qui il tempo si guarda, non si legge.",
                style = SalaType.body,
                color = SalaTokens.neutral900.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Nessun account", "Dati aperti", "Senza pubblicità").forEach { voce ->
                    Text(
                        text = voce,
                        style = SalaType.rowNote,
                        color = SalaTokens.neutral900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SalaTokens.neutral900.copy(alpha = 0.07f))
                            .padding(horizontal = 15.dp, vertical = 8.dp),
                    )
                }
            }
            // Mentre cerca, sotto le pastiglie compare la scritta che respira:
            // e' l'unica animazione permanente concessa qui, e dura solo finche'
            // dura l'attesa - un'attesa in cui niente si muove e' indistinguibile
            // da un'app bloccata.
            if (state.locating) {
                ScrittaCheRespira(
                    testo = "GUARDANDO IL CIELO…",
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                Text(
                    text = cosaDice(state),
                    style = SalaType.rowNote,
                    color = SalaTokens.neutral900.copy(alpha = 0.62f),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        Column(
            modifier = Modifier.padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Pulsante(
                testo = "Trovami",
                fondo = SalaTokens.accent600,
                inchiostro = SalaTokens.neutral100,
                onClick = { chiediPermesso.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            )
            Pulsante(
                testo = "Scegli prima una località",
                fondo = SalaTokens.neutral900.copy(alpha = 0.07f),
                inchiostro = SalaTokens.neutral900,
                onClick = onChooseByHand,
            )
        }
    }
}

/** Cosa dice la riga in fondo, secondo cosa sta succedendo. */
private fun cosaDice(state: UiState): String = when {
    state.followsLocation -> "Trovato. Apro il cielo."
    state.locationUnavailable -> "Non riesco a trovarti: puoi scegliere la città a mano."
    else -> "Per aprire il cielo mi serve sapere da dove lo guardi."
}

/**
 * Il cielo dell'ingresso: un sole con l'alone e due nuvole basse.
 *
 * **Fisso e non tratto dal tempo vero**, che qui non si conosce ancora: non c'e'
 * una localita', quindi non c'e' una previsione, e un cielo inventato che si
 * spaccia per quello di adesso sarebbe la prima cosa falsa che l'app dice.
 * Questo e' dichiaratamente un'insegna.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.cieloDIngresso() {
    val cx = size.width / 2f
    val rAlone = size.width * 0.24f
    val centroSole = Offset(cx, 103f / 210f * size.height)
    drawCircle(
        brush = Brush.radialGradient(
            0f to SalaTokens.accent200.copy(alpha = 0.5f),
            0.68f to SalaTokens.accent200.copy(alpha = 0f),
            center = centroSole,
            radius = rAlone * 1.6f,
        ),
        radius = rAlone * 1.6f,
        center = centroSole,
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFFFFF4E8),
            0.60f to SalaTokens.accent400,
            1f to Color(0xFFDD8A55),
            center = Offset(centroSole.x - rAlone * 0.24f, centroSole.y - rAlone * 0.32f),
            radius = rAlone * 1.4f,
        ),
        radius = rAlone,
        center = centroSole,
    )

    fun nuvola(x: Float, y: Float, larghezza: Float, altezza: Float) {
        drawOval(
            color = SalaTokens.neutral100.copy(alpha = 0.9f),
            topLeft = Offset(x, y),
            size = Size(larghezza, altezza),
        )
    }
    nuvola(size.width * 0.02f, size.height * 0.62f, size.width * 0.34f, size.height * 0.27f)
    nuvola(size.width * 0.66f, size.height * 0.54f, size.width * 0.28f, size.height * 0.23f)
}

/**
 * La scritta che respira durante l'attesa.
 *
 * Due trappole la riguardano e vanno lette per intero. La #8 vieta di disegnare
 * fotogrammi da fermi: qui fermi non si e'. La #17 dice che
 * `rememberInfiniteTransition` "qui non anima": non animava dove il valore si
 * leggeva **solo dentro il disegno** - erano le gocce di pioggia - mentre qui
 * finisce nel colore di un `Text`, cioe' in composizione, che e' il caso in cui
 * la comodita' funziona. L'animazione muore con l'attesa.
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

/** Un comando pieno: la forma e' la stessa, cambia solo quanto pesa. */
@Composable
private fun Pulsante(testo: String, fondo: Color, inchiostro: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MinTouchTarget)
            .clip(CircleShape)
            .background(fondo)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = testo, style = SalaType.rowTitle, color = inchiostro, textAlign = TextAlign.Center)
    }
}
