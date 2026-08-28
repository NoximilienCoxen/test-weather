package com.forli.meteo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.forli.meteo.R
import com.forli.meteo.ui.home.MoonPhase
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * La fase lunare.
 *
 * L'unico dei tre widget che non tocca la rete: la fase si calcola dalla data
 * (vedi [MoonPhase]), quindi qui non c'e' niente da scaricare e niente da
 * aspettare.
 */
class MoonWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = WidgetPrefs(context).load(appWidgetIdOf(context, id)).palette()
        val phase = MoonPhase.at(LocalDate.now())
        provideContent { MoonBody(phase, palette) }
    }
}

@Composable
private fun MoonBody(phase: Float, palette: WidgetPalette) {
    val segment = MoonSegment.of(phase)
    val illuminated = (MoonPhase.illumination(phase) * 100).roundToInt()

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .padding(14.dp)
            .clickable(actionRunCallback<RefreshMoonAction>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(segment.icon),
            contentDescription = segment.label,
            colorFilter = ColorFilter.tint(palette.accent),
            modifier = GlanceModifier.size(30.dp),
        )
        Column(
            modifier = GlanceModifier.padding(start = 10.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.Start,
        ) {
            Text(
                text = "LUNA",
                style = TextStyle(
                    color = palette.secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = "$illuminated%",
                style = TextStyle(
                    color = palette.accent,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = segment.label,
                style = TextStyle(
                    color = palette.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

/**
 * Gli otto nomi con cui si chiama la luna, e la sagoma di ciascuno.
 *
 * Otto e non un disegno continuo: Glance non sa disegnare, puo' solo mostrare
 * un'immagine gia' pronta, e otto sagome bastano a riconoscere a colpo d'occhio
 * a che punto del mese si e'.
 */
internal enum class MoonSegment(val label: String, val icon: Int) {
    NOVILUNIO("NOVILUNIO", R.drawable.ic_moon_new),
    CRESCENTE("LUNA CRESCENTE", R.drawable.ic_moon_waxing_crescent),
    PRIMO_QUARTO("PRIMO QUARTO", R.drawable.ic_moon_first_quarter),
    GIBBOSA_CRESCENTE("GIBBOSA CRESCENTE", R.drawable.ic_moon_waxing_gibbous),
    PLENILUNIO("PLENILUNIO", R.drawable.ic_moon_full),
    GIBBOSA_CALANTE("GIBBOSA CALANTE", R.drawable.ic_moon_waning_gibbous),
    ULTIMO_QUARTO("ULTIMO QUARTO", R.drawable.ic_moon_last_quarter),
    CALANTE("LUNA CALANTE", R.drawable.ic_moon_waning_crescent),
    ;

    companion object {
        /** Il ciclo diviso in otto, con i nomi centrati sul loro istante esatto. */
        fun of(phase: Float): MoonSegment {
            val eighth = ((phase * 8f) + 0.5f).toInt() % 8
            return entries[eighth]
        }
    }
}

class RefreshMoonAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        MoonWidget().update(context, glanceId)
    }
}

class MoonWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoonWidget()
}
