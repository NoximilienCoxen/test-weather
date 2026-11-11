package com.caelumweather.widget

import com.caelumweather.lingua.inMezzoAllaFrase
import com.caelumweather.lingua.tr
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import com.caelumweather.data.Place
import com.caelumweather.data.Wmo
import com.caelumweather.widget.paint.Frame
import com.caelumweather.widget.paint.WidgetCanvas
import com.caelumweather.widget.paint.WidgetInk
import com.caelumweather.widget.paint.WidgetType
import com.caelumweather.widget.paint.miniArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Una cella: il tempo disegnato e la temperatura. */
internal class MiniWidget : CaelumWidget(WidgetKind.MINI) {

    override suspend fun paint(
        context: Context,
        frame: Frame,
        place: Place?,
        type: WidgetType,
        ink: WidgetInk,
    ): Drawn {
        val where = requireNotNull(place)
        val forecast = WidgetForecast.load(context, where)
        val bitmap = withContext(Dispatchers.Default) {
            WidgetCanvas.paint(frame, ink.background) { miniArt(forecast, type, ink) }
        }
        val current = forecast?.current
        val spoken = buildString {
            append(where.name)
            current?.temperature?.roundToInt()?.let { append(tr(", $it gradi", ", $it degrees", ", $it Grad", ", $it degrés", ", $it grados")) }
            append(", ${Wmo.condition(current?.weatherCode).inMezzoAllaFrase()}")
        }
        return Drawn(bitmap, spoken)
    }
}

class MiniWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MiniWidget()
}
