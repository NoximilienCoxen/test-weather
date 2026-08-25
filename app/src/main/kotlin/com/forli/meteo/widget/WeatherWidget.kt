package com.forli.meteo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.forli.meteo.R
import com.forli.meteo.data.Forecast
import com.forli.meteo.data.Place
import com.forli.meteo.data.WeatherRepository
import com.forli.meteo.data.Wmo
import com.forli.meteo.prefs.SettingsPrefs
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

// Glance 1.1.1 non offre un ColorProvider giorno/notte: la scelta del tema
// la fa il sistema di risorse fra values/ e values-night/.
private val WidgetBackground = ColorProvider(R.color.widget_background)
private val WidgetPrimary = ColorProvider(R.color.widget_primary)
private val WidgetSecondary = ColorProvider(R.color.widget_secondary)

class WeatherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // La localita' e' quella scelta nell'app: un widget che mostra sempre
        // Forli' mentre l'app mostra Singapore sarebbe semplicemente rotto.
        val place = SettingsPrefs(context).settings.first().place
        val forecast = WeatherRepository(place).load().getOrNull()
        provideContent { WidgetBody(place, forecast) }
    }
}

@Composable
private fun WidgetBody(place: Place, forecast: Forecast?) {
    val temperature = forecast?.current?.temperature
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .padding(14.dp)
            .clickable(actionRunCallback<RefreshWidgetAction>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        Text(
            text = place.name.uppercase(),
            style = TextStyle(
                color = WidgetSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            text = temperature?.roundToInt()?.let { "$it°" } ?: "--",
            style = TextStyle(
                color = WidgetPrimary,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = Wmo.condition(forecast?.current?.weatherCode),
            style = TextStyle(
                color = WidgetSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** Un tocco sul widget forza un nuovo scaricamento. */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WeatherWidget().update(context, glanceId)
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}
