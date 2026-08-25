package com.forli.meteo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType

/** GIORNO / SETTIMANA: l'etichetta attiva e' quella piena. */
@Composable
fun DayWeekToggle(
    weekMode: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val knob by animateFloatAsState(if (weekMode) 1f else 0f, label = "knob")
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onChange(!weekMode) },
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "GIORNO",
            style = MeteoType.caption,
            color = if (weekMode) colors.line else colors.text,
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .width(52.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.line.copy(alpha = 0.55f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            // corsa utile = larghezza - diametro - i due margini
            val travel = 26.dp
            Box(
                modifier = Modifier
                    .padding(start = 3.dp)
                    .offset(x = travel * knob)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.pillBackground),
            )
        }
        Text(
            text = "SETTIMANA",
            style = MeteoType.caption,
            color = if (weekMode) colors.text else colors.line,
        )
    }
}

