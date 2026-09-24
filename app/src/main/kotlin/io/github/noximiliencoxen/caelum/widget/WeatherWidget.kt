package io.github.noximiliencoxen.caelum.widget

import io.github.noximiliencoxen.caelum.lingua.tr
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.widget.paint.Frame
import io.github.noximiliencoxen.caelum.widget.paint.WidgetCanvas
import io.github.noximiliencoxen.caelum.widget.paint.WidgetInk
import io.github.noximiliencoxen.caelum.widget.paint.WidgetType
import io.github.noximiliencoxen.caelum.widget.paint.currentArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Il tempo che fa adesso: localita', temperatura, condizione. */
internal class WeatherWidget : CaelumWidget(WidgetKind.METEO) {

    override suspend fun paint(
        context: Context,
        frame: Frame,
        place: Place?,
        type: WidgetType,
        ink: WidgetInk,
    ): Drawn {
        // Non-null per costruzione: METEO dichiara `needsPlace`, e il caso
        // senza citta' non arriva fin qui.
        val where = requireNotNull(place)
        val forecast = WidgetForecast.load(context, where)

        val bitmap = withContext(Dispatchers.Default) {
            WidgetCanvas.paint(frame, ink.background) {
                currentArt(frame, where, forecast, type, ink)
            }
        }

        val current = forecast?.current
        val spoken = buildString {
            append(where.name)
            current?.temperature?.roundToInt()?.let { append(tr(", $it gradi", ", $it degrees")) }
            append(", ${Wmo.condition(current?.weatherCode).lowercase()}")
        }

        return Drawn(bitmap, spoken)
    }
}

class WeatherWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}
