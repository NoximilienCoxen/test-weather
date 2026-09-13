package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.AirBand
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.sala.Didascalia
import io.github.noximiliencoxen.caelum.ui.sala.PannelloSala
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import kotlin.math.roundToInt

/**
 * Sala V — L'aria: l'indice, e i quattro inquinanti che lo compongono.
 *
 * Quattro e non cinque come nel prototipo: i pollini non stanno nell'endpoint
 * base di Open-Meteo, e tre pastiglie con dentro un numero inventato sarebbero
 * state la parte piu' convincente della schermata e l'unica falsa.
 */
@Composable
fun SalaAriaScreen(
    state: UiState,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    val aria = state.air
    val banda = aria?.band
    val tinta = coloreBanda(banda)

    PannelloSala(palette = palette, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                // L'anello dice **quanto** prima di dire quale: la porzione
                // colorata cresce con l'indice, e il resto resta il grigio
                // dell'interfaccia.
                val quota = ((aria?.index ?: 0) / 100f).coerceIn(0f, 1f)
                Canvas(modifier = Modifier.size(96.dp)) {
                    val spessore = 11.dp.toPx()
                    drawCircle(
                        color = palette.maniglia,
                        radius = size.minDimension / 2f - spessore / 2f,
                        style = Stroke(width = spessore),
                    )
                    if (quota > 0f) {
                        drawArc(
                            color = tinta,
                            startAngle = -90f,
                            sweepAngle = 360f * quota,
                            useCenter = false,
                            topLeft = Offset(spessore / 2f, spessore / 2f),
                            size = Size(size.width - spessore, size.height - spessore),
                            style = Stroke(width = spessore),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = aria?.index?.toString() ?: "--",
                        style = SalaType.cardTitle,
                        color = palette.ink,
                    )
                    Text(text = "AQI", style = SalaType.microLabel, color = palette.inkFaint)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "L'aria", style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = banda?.label?.lowercase()?.replaceFirstChar { it.uppercase() }
                        ?: if (state.airUnavailable) "Non disponibile" else "In arrivo",
                    style = SalaType.rowTitle,
                    color = palette.accent,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Didascalia(
                    descrizione(banda, state.airUnavailable),
                    palette,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // I quattro che l'endpoint porta davvero. La scala di ognuno e' la
            // propria soglia di legge, non un massimo comune: mescolarle
            // farebbe sembrare grave un PM10 normale accanto a un ozono alto.
            BarraInquinante("PM 2.5", aria?.pm25, 25.0, palette)
            BarraInquinante("PM 10", aria?.pm10, 50.0, palette)
            BarraInquinante("O₃", aria?.ozone, 120.0, palette)
            BarraInquinante("NO₂", aria?.nitrogenDioxide, 40.0, palette)
        }
    }
}

@Composable
private fun BarraInquinante(nome: String, valore: Double?, soglia: Double, palette: SalaPalette) {
    val quota = valore?.let { (it / soglia).coerceIn(0.0, 1.0).toFloat() } ?: 0f
    val tinta = if (quota > 0.7f) SalaTokens.accent400 else SalaTokens.verde400
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = nome,
            style = SalaType.hourLabel,
            color = palette.ink,
            modifier = Modifier.width(56.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(9.dp)
                .clip(CircleShape)
                .background(palette.maniglia),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(quota)
                    .height(9.dp)
                    .clip(CircleShape)
                    .background(tinta),
            )
        }
        Text(
            text = valore?.let { "${it.roundToInt()} µg/m³" } ?: "--",
            style = SalaType.rowNote,
            color = palette.inkSoft,
            textAlign = TextAlign.End,
            modifier = Modifier.width(66.dp),
        )
    }
}

private fun coloreBanda(banda: AirBand?): Color = when (banda) {
    null -> SalaTokens.neutral400
    AirBand.BUONA, AirBand.DISCRETA -> SalaTokens.verde400
    AirBand.MEDIA -> SalaTokens.accent400
    AirBand.SCARSA -> SalaTokens.accent500
    else -> SalaTokens.accent700
}

private fun descrizione(banda: AirBand?, nonDisponibile: Boolean): String = when {
    nonDisponibile -> "La misura dell'aria non e' arrivata: la stazione piu' vicina non ha risposto."
    banda == null -> "La misura dell'aria sta arrivando."
    banda == AirBand.BUONA -> "Particolato basso: nessuna precauzione necessaria, nemmeno per chi e' sensibile."
    banda == AirBand.DISCRETA -> "Aria accettabile: chi ha problemi respiratori eviti lo sforzo prolungato all'aperto."
    banda == AirBand.MEDIA -> "Chi e' sensibile faccia attenzione: meglio rimandare l'attivita' intensa all'aperto."
    banda == AirBand.SCARSA -> "Aria scarsa: limitare lo sforzo all'aperto, soprattutto nelle ore centrali."
    else -> "Aria pessima: restare al chiuso quando possibile e tenere le finestre chiuse."
}
