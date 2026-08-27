package com.forli.meteo.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.forli.meteo.data.UpdateCheck
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType

/**
 * L'avviso che c'e' una build piu' recente.
 *
 * Una riga in cima, non una finestra al centro. Chi apre l'app la apre per
 * sapere che tempo fa: una finestra modale mette fra lui e quella risposta una
 * domanda che non ha fatto, e la mette **ogni volta**. Una riga si legge di
 * sfuggita, si tocca se interessa, e si chiude con un tocco se non interessa.
 *
 * Il tocco apre la pagina del rilascio nel browser. Non scarica e non installa:
 * un'app che si aggiorna da sola fuori da uno store e' esattamente la forma che
 * Play Protect ha ragione di guardare storto, e questa e' la parte del lavoro
 * in cui si sta cercando di *non* somigliare a quella cosa.
 */
@Composable
fun UpdateNotice(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMeteoColors.current
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(lerp(colors.background, colors.text, 0.12f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    // Se non c'e' un browser - capita sulle immagini spoglie -
                    // non succede niente invece di chiudersi.
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, UpdateCheck.RELEASE_PAGE.toUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "NUOVA VERSIONE DISPONIBILE",
                style = MeteoType.value,
                color = colors.text,
            )
            Text(
                text = "TOCCA PER APRIRE LA PAGINA DEL RILASCIO",
                style = MeteoType.caption,
                color = colors.label,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        DismissButton(onDismiss)
    }
}

/** Una croce disegnata, per non tirarsi dietro una libreria di icone. */
@Composable
private fun DismissButton(onClick: () -> Unit) {
    val colors = LocalMeteoColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val stroke = size.minDimension * 0.13f
            drawLine(
                color = colors.label,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colors.label,
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
