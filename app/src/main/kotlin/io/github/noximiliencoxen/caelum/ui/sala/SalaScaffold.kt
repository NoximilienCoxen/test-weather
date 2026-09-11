package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.common.MinTouchTarget
import kotlin.math.abs

/** Il margine interno di ogni sala: 40dp sopra, 30 ai lati e sotto. */
private val SalaContentPadding = PaddingValues(start = 30.dp, end = 30.dp, top = 40.dp, bottom = 30.dp)

/**
 * L'intestazione comune a ogni schermata: un nome a sinistra, un riferimento
 * a destra, entrambi maiuscoletti — e' lo stile con cui il prototipo segna
 * dove ci si trova, sala dopo sala.
 */
@Composable
private fun SalaHeader(
    leading: String,
    trailing: String,
    ink: Color,
    inkSoft: Color,
    onLeadingClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(MinTouchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Le impostazioni hanno un comando **loro**, e non sono piu' nascoste
        // dietro il nome della citta': quel nome dice dove sei, non e' un menu,
        // e chi lo tocca si aspetta di cambiare posto - che infatti e' quello
        // che fa, aprendo le localita'.
        if (onMenuClick != null) {
            Box(
                modifier = Modifier.size(MinTouchTarget).clickable(onClick = onMenuClick),
                contentAlignment = Alignment.CenterStart,
            ) {
                TreLinee(ink)
            }
        }
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .let { if (onLeadingClick != null) it.clickable(onClick = onLeadingClick) else it },
        ) {
            Text(
                text = leading.uppercase(),
                style = SalaType.roomLabel,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = trailing.uppercase(),
            style = SalaType.roomLabel,
            color = inkSoft,
            maxLines = 1,
        )
    }
}

/**
 * Le tre linee delle impostazioni, disegnate invece che importate.
 *
 * Il progetto non ha una libreria di icone e non vale la pena aprirne una per
 * tre segmenti: sono tre righe, e in un'app che disegna lune e nuvole a mano
 * importare un pacchetto per questo sarebbe sproporzionato.
 */
@Composable
private fun TreLinee(ink: Color) {
    Canvas(modifier = Modifier.size(20.dp, 14.dp)) {
        val spessore = 1.6.dp.toPx()
        listOf(0f, 0.5f, 1f).forEach { t ->
            val y = t * (size.height - spessore) + spessore / 2f
            drawLine(
                color = ink,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = spessore,
            )
        }
    }
}

/**
 * L'indicatore di percorso: sette trattini, uno per sala, al posto della
 * colonna di icone del feed. Legge la posizione del carosello **dentro il
 * disegno**: cambia a ogni fotogramma del dito, e letta in composizione
 * ricomporrebbe l'intera schermata per travasare un colore.
 */
@Composable
fun SalaPathIndicator(
    position: () -> Float,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
    count: Int = SalaRoom.entries.size,
) {
    val activeOn = if (palette.dark) SalaTokens.accent400 else SalaTokens.accent
    val muted = if (palette.dark) {
        Color.White.copy(alpha = 0.26f)
    } else {
        SalaTokens.text.copy(alpha = 0.20f)
    }
    Canvas(modifier = modifier.fillMaxWidth().height(2.dp)) {
        val gap = 6.dp.toPx()
        val slot = (size.width - gap * (count - 1)) / count
        val pos = position()
        for (i in 0 until count) {
            val closeness = (1f - abs(pos - i)).coerceIn(0f, 1f)
            val color = lerpColor(muted, activeOn, closeness)
            val left = i * (slot + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(left, 0f),
                size = Size(slot, size.height),
                cornerRadius = CornerRadius(size.height / 2f),
            )
        }
    }
}

private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha + (to.alpha - from.alpha) * t,
)

/**
 * La cornice di una sala: fondo acquerello, intestazione "località / SALA N",
 * il contenuto della stanza, e l'indicatore di percorso in fondo.
 */
@Composable
fun SalaRoomScaffold(
    palette: SalaPalette,
    room: SalaRoom,
    placeName: String,
    position: () -> Float,
    modifier: Modifier = Modifier,
    onPlaceClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    SalaBackground(palette = palette, blobs = room.blobs, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(SalaContentPadding),
        ) {
            SalaHeader(
                leading = placeName,
                trailing = "Sala ${room.roman} / ${SalaRoom.entries.last().roman}",
                ink = palette.ink,
                inkSoft = palette.inkSoft,
                onLeadingClick = onPlaceClick,
                onMenuClick = onMenuClick,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content(Modifier.fillMaxSize())
            }
            SalaPathIndicator(position = position, palette = palette, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/**
 * La cornice delle due schermate di servizio (Le localita', Impostazioni):
 * stesso fondo acquerello, un'intestazione che dice "Servizi" invece del
 * percorso fra le sale, e niente indicatore in fondo — non fanno parte del
 * giro delle sette sale.
 */
@Composable
fun SalaServiceScaffold(
    palette: SalaPalette,
    title: String,
    blobs: List<WashBlobSpec>,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    SalaBackground(palette = palette, blobs = blobs, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(SalaContentPadding),
        ) {
            if (onClose != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(MinTouchTarget)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(text = "‹", style = SalaType.cardTitle, color = palette.ink)
                    }
                }
            }
            SalaHeader(leading = title, trailing = "Servizi", ink = palette.ink, inkSoft = palette.inkSoft)
            content(Modifier.weight(1f).fillMaxWidth())
        }
    }
}
