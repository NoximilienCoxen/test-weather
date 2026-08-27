package com.forli.meteo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType

data class TableRow(val label: String, val value: String)

/**
 * Prima riga come pillola piena con testo invertito, le altre con una linea
 * sottile che collega etichetta e valore.
 */
@Composable
fun DataTable(rows: List<TableRow>, modifier: Modifier = Modifier) {
    val colors = LocalMeteoColors.current
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            if (index == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.pillBackground)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.label, style = MeteoType.label, color = colors.pillText)
                    Text(row.value, style = MeteoType.value, color = colors.pillText)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.label, style = MeteoType.label, color = colors.text)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .height(1.dp)
                            .background(colors.line),
                    )
                    Text(row.value, style = MeteoType.value, color = colors.text)
                }
            }
        }
    }
}
