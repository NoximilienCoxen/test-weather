package com.forli.meteo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.render.ExtrudedText
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoTheme
import com.forli.meteo.ui.theme.MeteoType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeteoTheme {
                MaterialValidationScreen()
            }
        }
    }
}

/**
 * Schermata di validazione del primo ciclo: serve solo a mettere il materiale
 * della cifra davanti alla macchina fotografica della CI. Viene sostituita
 * dalla schermata vera appena il materiale e' approvato.
 */
@Composable
private fun MaterialValidationScreen() {
    val colors = LocalMeteoColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = "ORA",
                style = MeteoType.caption,
                color = colors.text,
            )

            ExtrudedText(
                text = "Temp.",
                fontSize = 54.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            )

            ExtrudedText(
                text = "25",
                fontSize = 210.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp),
            )
        }
    }
}
