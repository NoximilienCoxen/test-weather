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
import com.forli.meteo.data.AirQuality
import com.forli.meteo.data.AirQualityRepository
import com.forli.meteo.data.Place

/** La qualita' dell'aria, secondo l'indice europeo. */
class AirQualityWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = WidgetPrefs(context).load(appWidgetIdOf(context, id))
        val place = config.resolvePlace(context)
        val air = AirQualityRepository(place).load().getOrNull()
        val palette = config.palette()
        provideContent { AirBody(place, air, palette) }
    }
}

@Composable
private fun AirBody(place: Place, air: AirQuality?, palette: WidgetPalette) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .padding(14.dp)
            .clickable(actionRunCallback<RefreshAirAction>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_air),
            contentDescription = "QUALITÀ DELL'ARIA",
            colorFilter = ColorFilter.tint(palette.accent),
            modifier = GlanceModifier.size(30.dp),
        )
        Column(
            modifier = GlanceModifier.padding(start = 10.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.Start,
        ) {
            Text(
                text = place.name.uppercase(),
                style = TextStyle(
                    color = palette.secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = air?.europeanAqi?.toString() ?: "--",
                style = TextStyle(
                    color = palette.accent,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                // Il numero da solo non dice niente a nessuno: e' la parola
                // accanto a dirti se puoi uscire a correre.
                text = air?.band?.label ?: "ARIA",
                style = TextStyle(
                    color = palette.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

class RefreshAirAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        AirQualityWidget().update(context, glanceId)
    }
}

class AirQualityWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AirQualityWidget()
}
