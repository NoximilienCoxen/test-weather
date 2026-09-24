package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.lingua.tr
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Un passo della guida: cosa si illumina, e cosa se ne dice.
 *
 * [dove] e' nelle coordinate della radice, come le da' `boundsInRoot`; nullo
 * vuol dire che quel pezzo non c'e' (la colonna, con le schede larghe) e il
 * passo si salta.
 */
internal class PassoGuida(val titolo: String, val testo: String, val dove: Rect?)

/**
 * La guida all'uso: lo schermo si scurisce tranne un riquadro, e accanto una
 * scheda dice cosa c'e' li' e cosa ci si fa.
 *
 * **Perche' esiste.** Chi ha provato l'app non sapeva che la barra in fondo
 * si trascina per cambiare ora, ne' che le sale si sfogliano: i gesti non si
 * vedono, e le scritte che li spiegano sono piccole di proposito. Compare una
 * volta, al primo avvio dopo il benvenuto, e si rivede dalle impostazioni.
 *
 * Un tocco ovunque manda avanti, "Salta" chiude; l'indietro di sistema chiude
 * anche lui, perche' una guida che trattiene il tasto indietro e' una
 * trappola.
 */
@Composable
internal fun GuidaSala(
    passi: List<PassoGuida>,
    palette: SalaPalette,
    movimento: Boolean,
    onFine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val validi = remember(passi) { passi.filter { it.dove != null } }
    var indice by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onFine)
    if (validi.isEmpty()) return
    val passo = validi[indice.coerceIn(0, validi.lastIndex)]
    val ultimo = indice >= validi.lastIndex
    fun avanti() {
        if (ultimo) onFine() else indice++
    }

    // La radice della guida non e' per forza la radice della finestra: le
    // coordinate si riportano alle sue.
    var origine by remember { mutableStateOf(Offset.Zero) }
    val bersaglio = passo.dove!!.translate(-origine)

    // Il riquadro scivola da un passo all'altro, cosi' l'occhio lo segue;
    // con le animazioni ridotte salta.
    val molla = if (movimento) {
        spring<Float>(dampingRatio = 0.85f, stiffness = 300f)
    } else {
        spring(stiffness = 100_000f)
    }
    val l by animateFloatAsState(bersaglio.left, molla, label = "l")
    val t by animateFloatAsState(bersaglio.top, molla, label = "t")
    val r by animateFloatAsState(bersaglio.right, molla, label = "r")
    val b by animateFloatAsState(bersaglio.bottom, molla, label = "b")

    val densita = LocalDensity.current
    val margine = with(densita) { 8.dp.toPx() }
    val raggio = with(densita) { 26.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origine = it.boundsInRoot().topLeft }
            // Il tocco non passa sotto: mentre la guida parla, l'app sta ferma.
            .pointerInput(ultimo, indice) { detectTapGestures { avanti() } },
    ) {
        val altezza = constraints.maxHeight.toFloat()
        Canvas(modifier = Modifier.fillMaxSize()) {
            val buco = RoundRect(
                left = l - margine,
                top = t - margine,
                right = r + margine,
                bottom = b + margine,
                cornerRadius = CornerRadius(raggio),
            )
            val velo = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(buco)
            }
            drawPath(velo, Color(0xFF10141C).copy(alpha = 0.74f))
            drawPath(
                Path().apply { addRoundRect(buco) },
                color = SalaTokens.accent400,
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }

        // La scheda sta dalla parte dove c'e' piu' posto: sotto il riquadro
        // se il riquadro e' in alto, sopra se e' in basso.
        val sotto = (t + b) / 2f < altezza / 2f
        val spazio = with(densita) { 18.dp.toPx() }
        Column(
            modifier = Modifier
                .align(if (sotto) Alignment.TopCenter else Alignment.BottomCenter)
                .offset {
                    if (sotto) {
                        IntOffset(0, (b + margine + spazio).roundToInt())
                    } else {
                        IntOffset(0, -(altezza - t + margine + spazio).roundToInt())
                    }
                }
                .padding(horizontal = 20.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(palette.panelSolido)
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 14.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                text = tr("${indice + 1} DI ${validi.size}", "${indice + 1} OF ${validi.size}"),
                style = SalaType.sectionLabel,
                color = palette.accent,
            )
            Text(
                text = passo.titolo,
                style = SalaType.cardTitle,
                color = palette.ink,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = passo.testo,
                style = SalaType.body,
                color = palette.inkSoft,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PuntiniGuida(totale = validi.size, corrente = indice, palette = palette)
                Spacer(modifier = Modifier.weight(1f))
                if (!ultimo) {
                    Text(
                        text = tr("Salta", "Skip"),
                        style = SalaType.rowTitle,
                        color = palette.inkSoft,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onFine)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
                Text(
                    text = if (ultimo) tr("Ho capito", "Got it") else tr("Avanti", "Next"),
                    style = SalaType.rowTitle,
                    color = palette.accentInk,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(palette.accent)
                        .clickable { avanti() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun PuntiniGuida(totale: Int, corrente: Int, palette: SalaPalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(totale) { i ->
            Box(
                modifier = Modifier
                    .size(if (i == corrente) 8.dp else 6.dp)
                    .align(Alignment.CenterVertically)
                    .clip(CircleShape)
                    .background(if (i == corrente) palette.accent else palette.inkFaint),
            )
        }
    }
}

/** I passi, con i riquadri misurati nella cornice. */
internal fun passiGuida(
    cielo: Rect?,
    intestazione: Rect?,
    scheda: Rect?,
    colonna: Rect?,
    barra: Rect?,
): List<PassoGuida> = listOf(
    PassoGuida(
        titolo = tr("Le sale", "The rooms"),
        testo = tr(
            "Scorri in su o in giù sulla scheda per passare da una sala all'altra: oggi, la settimana, la pioggia, la luna, l'aria, il vento e i raggi UV. Basta un colpetto.",
            "Swipe up or down on the card to move from one room to the next: today, the week, rain, the moon, air, wind and UV rays. A light flick is enough.",
        ),
        dove = scheda,
    ),
    PassoGuida(
        titolo = tr("Dentro le schede", "Inside the cards"),
        testo = tr(
            "Le voci con › si aprono e portano alla loro sala. Tocca un giorno per vedere quel giorno in tutte le sale.",
            "Items marked › open their own room. Tap a day to see that day in every room.",
        ),
        dove = scheda,
    ),
    PassoGuida(
        titolo = tr("La colonna delle sale", "The room column"),
        testo = tr(
            "Tocca un'icona per andare dritto a quella sala, o fai scorrere il dito lungo la colonna. Dalle impostazioni puoi nasconderla e avere schede più larghe.",
            "Tap an icon to jump straight to that room, or slide your finger along the column. In the settings you can hide it and get wider cards.",
        ),
        dove = colonna,
    ),
    PassoGuida(
        titolo = tr("Cambia ora", "Change the hour"),
        testo = tr(
            "Trascina il cerchio lungo la barra: cielo, temperatura e numeri seguono l'ora scelta. Ogni tratto ha il colore del cielo di quell'ora. Per tornare ad adesso tocca «Torna all'ora attuale».",
            "Drag the circle along the bar: sky, temperature and numbers follow the chosen hour. Each stretch has the colour of that hour's sky. To come back to now, tap «Back to now».",
        ),
        dove = barra,
    ),
    PassoGuida(
        titolo = tr("Il cielo", "The sky"),
        testo = tr(
            "Il disegno è il tempo dell'ora scelta: sole, nuvole, pioggia e luna seguono la previsione. Inclina il telefono o toccalo, e si muove.",
            "The drawing is the weather of the chosen hour: sun, clouds, rain and moon follow the forecast. Tilt the phone or touch it, and it moves.",
        ),
        dove = cielo,
    ),
    PassoGuida(
        titolo = tr("Città e impostazioni", "City and settings"),
        testo = tr(
            "Tocca il nome della città per cambiarla o aggiungerne altre. L'icona a sinistra apre le impostazioni; a destra, gli avvisi del giorno.",
            "Tap the city name to change it or add others. The icon on the left opens the settings; on the right, the day's alerts.",
        ),
        dove = intestazione,
    ),
)
