package com.forli.meteo.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import com.forli.meteo.data.WeatherRepository
import com.forli.meteo.data.Wmo
import kotlin.math.roundToInt

/** Il tempo che fa adesso: localita', temperatura, condizione. */
class WeatherWidget : GlanceAppWidget() {

    override val sizeMode = WidgetSizes

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = WidgetPrefs(context).load(appWidgetIdOf(context, id))
        val place = config.resolvePlace(context)
        val forecast = WeatherRepository(place).load().getOrNull()
        val palette = config.palette()

        val current = forecast?.current
        val value = current?.temperature?.roundToInt()?.let { "$it°" } ?: "--"
        val icon = iconFor(Wmo.family(current?.weatherCode), current?.isDay ?: true)

        provideContent {
            WidgetFrame(
                value = value,
                label = place.name.uppercase(),
                caption = Wmo.condition(current?.weatherCode),
                icon = icon,
                palette = palette,
                onClick = actionRunCallback<RefreshWidgetAction>(),
            )
        }
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

class WeatherWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}
