package com.forli.meteo.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import com.forli.meteo.R
import com.forli.meteo.data.AirQualityRepository

/** La qualita' dell'aria, secondo l'indice europeo. */
class AirQualityWidget : GlanceAppWidget() {

    override val sizeMode = WidgetSizes

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = WidgetPrefs(context).load(appWidgetIdOf(context, id))
        val place = config.resolvePlace(context)
        val air = AirQualityRepository(place).load().getOrNull()
        val palette = config.palette()

        provideContent {
            WidgetFrame(
                value = air?.europeanAqi?.toString() ?: "--",
                label = place.name.uppercase(),
                // Il numero da solo non dice niente a nessuno: e' la parola
                // accanto a dirti se puoi uscire a correre.
                caption = air?.band?.label ?: "QUALITÀ DELL'ARIA",
                icon = R.drawable.ic_widget_air,
                palette = palette,
                onClick = actionRunCallback<RefreshAirAction>(),
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
