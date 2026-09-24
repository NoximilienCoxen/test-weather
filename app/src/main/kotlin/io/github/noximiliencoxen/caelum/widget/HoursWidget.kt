package io.github.noximiliencoxen.caelum.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.widget.paint.Frame
import io.github.noximiliencoxen.caelum.widget.paint.WidgetCanvas
import io.github.noximiliencoxen.caelum.widget.paint.WidgetInk
import io.github.noximiliencoxen.caelum.widget.paint.WidgetType
import io.github.noximiliencoxen.caelum.widget.paint.hoursArt
import io.github.noximiliencoxen.caelum.widget.paint.prossimeOre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Le prossime ore, una colonna ciascuna. */
internal class HoursWidget : CaelumWidget(WidgetKind.ORE) {

    override suspend fun paint(
        context: Context,
        frame: Frame,
        place: Place?,
        type: WidgetType,
        ink: WidgetInk,
    ): Drawn {
        val where = requireNotNull(place)
        val hours = prossimeOre(WidgetForecast.load(context, where))
        val bitmap = withContext(Dispatchers.Default) {
            WidgetCanvas.paint(frame, ink.background) {
                hoursArt(frame, where.name.uppercase(), hours, type, ink)
            }
        }
        val spoken = buildString {
            append("Prossime ore a ${where.name}")
            hours.take(6).forEach { h ->
                append(". Alle ${h.time.hour}, ${Wmo.condition(h.weatherCode).lowercase()}")
                h.temperature?.roundToInt()?.let { append(", $it gradi") }
                h.precipProbability?.takeIf { it >= 10 }?.let { append(", pioggia al $it per cento") }
            }
        }
        return Drawn(bitmap, spoken)
    }
}

class HoursWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HoursWidget()
}
