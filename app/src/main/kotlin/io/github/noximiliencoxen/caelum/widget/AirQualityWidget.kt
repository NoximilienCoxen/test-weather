package io.github.noximiliencoxen.caelum.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import io.github.noximiliencoxen.caelum.data.AirQualityRepository
import io.github.noximiliencoxen.caelum.widget.paint.WidgetCanvas
import io.github.noximiliencoxen.caelum.widget.paint.WidgetInk
import io.github.noximiliencoxen.caelum.widget.paint.WidgetType
import io.github.noximiliencoxen.caelum.widget.paint.airArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** La qualita' dell'aria, secondo l'indice europeo. */
class AirQualityWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = appWidgetIdOf(context, id)
        val place = WidgetPrefs(context).load(appWidgetId).resolvePlace(context)
        val air = AirQualityRepository(place).load().getOrNull()

        val frame = WidgetCanvas.plan(context, appWidgetId)
        val ink = WidgetInk.of(context)
        val type = WidgetType(context)
        val bitmap = withContext(Dispatchers.Default) {
            WidgetCanvas.paint(frame, ink.background) {
                airArt(place.name.uppercase(), air, type, ink)
            }
        }

        val spoken = buildString {
            append("Qualità dell'aria a ${place.name}")
            air?.europeanAqi?.let { append(", indice $it") }
            air?.band?.let { append(", ${it.label.lowercase()}") }
        }

        provideContent {
            WidgetImage(bitmap, spoken, actionRunCallback<RefreshAirAction>())
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
