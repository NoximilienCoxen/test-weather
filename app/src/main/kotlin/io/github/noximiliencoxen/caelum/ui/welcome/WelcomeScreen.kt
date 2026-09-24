package io.github.noximiliencoxen.caelum.ui.welcome

import io.github.noximiliencoxen.caelum.lingua.tr
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.scene.Scena
import io.github.noximiliencoxen.caelum.ui.scene.ScenaAnimata
import io.github.noximiliencoxen.caelum.ui.theme.MinTouchTarget
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
    /** Il quadretto in cima: a caso, di giorno o di notte (vedi `ui/scene`). */
    scena: Scena = Scena.MARE,
    notte: Boolean = false,
    movimento: Boolean = true,
) {
    // Di notte la pagina si fa scura sotto la scena, e le scritte chiare: un
    // crema sotto un cielo stellato sarebbe una finestra accesa di colpo.
    val fondo = if (notte) Color(0xFF121826) else Color(0xFFF4ECE0)
    val inchiostro = if (notte) SalaTokens.neutral100 else SalaTokens.neutral900

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

    Box(modifier = modifier.fillMaxSize().background(fondo)) {
    // La scena occupa la meta' alta e sfuma nel fondo della pagina: le
    // scritte restano sul pieno, dove si leggono.
    ScenaAnimata(
        scena = scena,
        notte = notte,
        movimento = movimento,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.58f),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.58f)
            .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to fondo)),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(start = 30.dp, end = 30.dp, top = 26.dp, bottom = 34.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = tr("Il cielo,\nora per ora", "The sky,\nhour by hour"),
                style = SalaType.pageTitle,
                color = inchiostro,
            )
            Text(
                text = tr("Qui il tempo si guarda, non si legge.", "Here the weather is seen, not read."),
                style = SalaType.body,
                color = inchiostro.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(tr("Nessun account", "No account"), tr("Dati aperti", "Open data"), tr("Senza pubblicità", "No ads")).forEach { voce ->
                    Text(
                        text = voce,
                        style = SalaType.rowNote,
                        color = inchiostro,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(inchiostro.copy(alpha = if (notte) 0.12f else 0.07f))
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
                    testo = tr("GUARDANDO IL CIELO…", "LOOKING AT THE SKY…"),
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                Text(
                    text = cosaDice(state),
                    style = SalaType.rowNote,
                    color = inchiostro.copy(alpha = 0.62f),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        Column(
            modifier = Modifier.padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Pulsante(
                testo = tr("Trovami", "Find me"),
                fondo = SalaTokens.accent600,
                inchiostro = SalaTokens.neutral100,
                onClick = { chiediPermesso.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            )
            Pulsante(
                testo = tr("Scegli prima una località", "Choose a place first"),
                fondo = inchiostro.copy(alpha = if (notte) 0.14f else 0.07f),
                inchiostro = inchiostro,
                onClick = onChooseByHand,
            )
        }
    }
}
}

/** Cosa dice la riga in fondo, secondo cosa sta succedendo. */
private fun cosaDice(state: UiState): String = when {
    state.followsLocation -> tr("Trovato. Apro il cielo.", "Found. Opening the sky.")
    state.locationUnavailable -> tr("Non riesco a trovarti: puoi scegliere la città a mano.", "I can't find you: you can pick the city by hand.")
    else -> tr("Per aprire il cielo mi serve sapere da dove lo guardi.", "To open the sky I need to know where you're looking from.")
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
