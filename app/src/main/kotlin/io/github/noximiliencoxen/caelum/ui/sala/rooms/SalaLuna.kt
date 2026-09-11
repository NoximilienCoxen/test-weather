package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.home.MoonPhase
import io.github.noximiliencoxen.caelum.ui.home.MoonSegment
import io.github.noximiliencoxen.caelum.ui.render3d.Camera
import io.github.noximiliencoxen.caelum.ui.render3d.MOON_SEAS
import io.github.noximiliencoxen.caelum.ui.render3d.moon
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoomScaffold
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/**
 * Sala IV — La luna: la sfera vera dei widget e della scultura, non una
 * sagoma piatta. Trascinala in orizzontale per attraversare il mese; la fase
 * e' calcolata sulla data reale, non su un disegno fisso.
 */
@Composable
fun SalaLunaScreen(
    state: UiState,
    palette: SalaPalette,
    position: () -> Float,
    onPlaceClick: () -> Unit,
) {
    var offsetDays by remember { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val shownDate = today.plusDays(offsetDays.toLong())
    val phase = MoonPhase.at(shownDate)
    val illum = MoonPhase.illumination(phase)
    val segment = MoonSegment.of(phase)
    val age = MoonPhase.ageDays(phase)
    val nextFull = MoonPhase.nextDate(today, 0.5f)

    SalaRoomScaffold(
        palette = palette,
        room = SalaRoom.LUNA,
        placeName = state.place.name,
        position = position,
        onPlaceClick = onPlaceClick,
    ) { modifier ->
        Column(modifier = modifier) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .size(280.dp)
                    .pointerInput(Unit) {
                        var accum = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { accum = 0f },
                        ) { _, dragAmount ->
                            accum += dragAmount
                            val step = (accum / 14f).toInt()
                            if (step != 0) {
                                offsetDays = (offsetDays + step).coerceIn(-15, 15)
                                accum -= step * 14f
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(240.dp)) {
                    val minDim = minOf(size.width, size.height)
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)
                    val camera = Camera(yawDeg = 0f, pitchDeg = 0f, distance = minDim * 1.35f, origin = centerOffset)
                    val dark = lerp(palette.ink, palette.ground, 0.65f)
                    moon(
                        camera = camera,
                        x = 0f, y = 0f, z = 0f,
                        radius = minDim * 0.42f,
                        phase = phase,
                        light = palette.ink,
                        dark = dark,
                        alpha = 1f,
                        marks = MOON_SEAS,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Trascina in orizzontale per attraversare il mese",
                    style = SalaType.hourLabel,
                    color = palette.inkSoft,
                )
                if (offsetDays != 0) {
                    TextButton(onClick = { offsetDays = 0 }) {
                        Text(text = "Stasera", style = SalaType.sectionLabel, color = palette.inkAccent)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                Text(text = segment.label.lowercase().replaceFirstChar { it.uppercase() }, style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = "${if (offsetDays == 0) "Stasera" else shownDate.format(DayMonth)} · ${(illum * 100).roundToInt()} % illuminata · giorno ${age.toInt() + 1} del ciclo",
                    style = SalaType.sectionLabel,
                    color = palette.inkAccent,
                )
                Text(
                    text = if (offsetDays == 0) {
                        "Il disco qui sopra e' la luna di questa notte, calcolata sulla data di oggi. Trascina in orizzontale per attraversare il mese lunare."
                    } else {
                        "Stai guardando la luna del ${shownDate.format(DayMonth)}, a ${kotlin.math.abs(offsetDays)} giorni da oggi. Torna a stasera per rimetterla in pari con il cielo."
                    },
                    style = SalaType.body,
                    color = palette.ink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Stat("Fase", segment.label.lowercase().replaceFirstChar { it.uppercase() }, palette)
                    Stat("Illuminazione", "${(illum * 100).roundToInt()} %", palette)
                    Stat("Prossima piena", nextFull.format(DayMonth), palette)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, palette: SalaPalette) {
    Column {
        Text(text = label, style = SalaType.hourLabel, color = palette.inkSoft)
        Text(text = value, style = SalaType.value, color = palette.ink)
    }
}
