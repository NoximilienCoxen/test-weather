package io.github.noximiliencoxen.caelum.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import io.github.noximiliencoxen.caelum.data.WeatherRepository
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.widget.paint.WidgetCanvas
import io.github.noximiliencoxen.caelum.widget.paint.WidgetInk
import io.github.noximiliencoxen.caelum.widget.paint.WidgetType
import io.github.noximiliencoxen.caelum.widget.paint.currentArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Il tempo che fa adesso: localita', temperatura, condizione. */
class WeatherWidget : GlanceAppWidget() {

    // Esatto e non a scaglioni: con i tagli predefiniti Glance disegnerebbe
    // un'immagine per ciascuno e il sistema ne sommerebbe il peso.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = appWidgetIdOf(context, id)

        // Legge la configurazione di questa istanza del widget.
        val config = WidgetPrefs(context).load(appWidgetId)

        val place = config.resolvePlace(context)

        val forecast = WeatherRepository(place).load().getOrNull()

        val frame = WidgetCanvas.plan(context, appWidgetId)
        val ink = WidgetInk.of(context)
        val type = WidgetType(context)
        val bitmap = withContext(Dispatchers.Default) {
            WidgetCanvas.paint(frame, ink.background) {
                currentArt(frame, place, forecast, type, ink)
            }
        }

        val current = forecast?.current
        val spoken = buildString {
            append(place.name)
            current?.temperature?.roundToInt()?.let { append(", $it gradi") }
            append(", ${Wmo.condition(current?.weatherCode).lowercase()}")
        }

        provideContent {
            WidgetImage(bitmap, spoken, actionRunCallback<RefreshWidgetAction>())
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
