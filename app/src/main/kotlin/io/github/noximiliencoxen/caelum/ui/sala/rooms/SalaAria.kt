package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.AirQuality
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.asIndex
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoomScaffold
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType

private data class Pollutant(val code: String, val value: Double?, val threshold: Double)

/**
 * Sala V — L'aria: l'arco dell'indice con la cifra al centro, poi una riga
 * per ognuno dei pollutanti che l'endpoint sa davvero dare — ognuna una
 * barra rapportata alla propria soglia, non a una scala comune, cosi'
 * l'ozono vicino al limite si legge subito come il valore piu' critico anche
 * se in microgrammi e' un numero piu' piccolo del particolato.
 */
@Composable
fun SalaAriaScreen(
    state: UiState,
    palette: SalaPalette,
    position: () -> Float,
    onPlaceClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val air = state.air

    SalaRoomScaffold(
        palette = palette,
        room = SalaRoom.ARIA,
        placeName = state.place.name,
        position = position,
        onPlaceClick = onPlaceClick,
        onMenuClick = onMenuClick,
    ) { modifier ->
        Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
            AirArc(air = air, palette = palette)

            val pollutants = listOf(
                Pollutant("PM2,5", air?.pm25, 25.0),
                Pollutant("PM10", air?.pm10, 50.0),
                Pollutant("NO₂", air?.nitrogenDioxide, 40.0),
                Pollutant("O₃", air?.ozone, 120.0),
            )
            Column(modifier = Modifier.padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                pollutants.forEach { p -> PollutantRow(p, palette) }
            }

            Column(modifier = Modifier.padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val title = when {
                    air == null -> "Qualita' dell'aria non disponibile"
                    else -> "Aria ${air.band?.label?.lowercase() ?: "--"}"
                }
                Text(text = title, style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = "${air?.scale?.label ?: "--"} · indice ${air?.index ?: "--"} · ${air?.band?.label?.lowercase() ?: "--"}",
                    style = SalaType.sectionLabel,
                    color = palette.inkAccent,
                )
                Text(
                    text = if (state.airUnavailable) {
                        "La qualita' dell'aria non e' arrivata da questa richiesta."
                    } else {
                        "Ogni riga e' rapportata alla propria soglia di riferimento, non a una scala comune."
                    },
                    style = SalaType.body,
                    color = palette.ink,
                )
            }
        }
    }
}

@Composable
private fun AirArc(air: AirQuality?, palette: SalaPalette) {
    Box(modifier = Modifier.fillMaxWidth().height(176.dp), contentAlignment = Alignment.TopCenter) {
        Canvas(modifier = Modifier.fillMaxWidth().height(176.dp)) {
            val strokeWidth = 20f
            val radius = (size.width - strokeWidth) / 2f - 20f
            val arcSize = Size(radius * 2f, radius * 2f)
            val topLeft = Offset(size.width / 2f - radius, 20f)
            drawArc(
                color = palette.inkFaint,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            val fraction = ((air?.index ?: 0).coerceAtMost(200) / 200f).coerceIn(0f, 1f)
            drawArc(
                color = palette.inkAccent,
                startAngle = 180f,
                sweepAngle = 180f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        Column(
            modifier = Modifier.padding(top = 74.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = air?.index?.toString() ?: "--", style = SalaType.giant(76), color = palette.ink)
            Text(text = air?.band?.label ?: "--", style = SalaType.hourLabel, color = palette.inkSoft)
        }
    }
}

@Composable
private fun PollutantRow(pollutant: Pollutant, palette: SalaPalette) {
    val fraction = pollutant.value?.let { (it / pollutant.threshold).toFloat().coerceIn(0f, 1f) } ?: 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(text = pollutant.code, style = SalaType.value, color = palette.ink, modifier = Modifier.width(66.dp))
        Box(modifier = Modifier.weight(1f).height(3.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(3.dp)) {
                drawRoundRect(color = palette.inkFaint, cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f))
                if (fraction > 0f) {
                    val color = if (fraction > 0.75f) SalaTokens.accent2 else palette.inkAccent
                    drawRoundRect(
                        color = color,
                        size = Size(size.width * fraction, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f),
                    )
                }
            }
        }
        Text(
            text = pollutant.value?.let { "${it.asIndex()} µg/m³" } ?: "--",
            style = SalaType.footnote,
            color = palette.inkSoft,
            textAlign = TextAlign.End,
            modifier = Modifier.width(84.dp),
        )
    }
}
