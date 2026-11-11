package com.caelumweather.widget

import com.caelumweather.lingua.inMezzoAllaFrase
import com.caelumweather.lingua.tr
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import com.caelumweather.data.AirQualityRepository
import com.caelumweather.data.Place
import com.caelumweather.widget.paint.Frame
import com.caelumweather.widget.paint.WidgetCanvas
import com.caelumweather.widget.paint.WidgetInk
import com.caelumweather.widget.paint.WidgetType
import com.caelumweather.widget.paint.airArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** La qualita' dell'aria, secondo l'indice europeo. */
internal class AirQualityWidget : CaelumWidget(WidgetKind.ARIA) {

    override suspend fun paint(
        context: Context,
        frame: Frame,
        place: Place?,
        type: WidgetType,
        ink: WidgetInk,
    ): Drawn {
        val where = requireNotNull(place)
        val air = AirQualityRepository(where).load().getOrNull()

        val bitmap = withContext(Dispatchers.Default) {
            WidgetCanvas.paint(frame, ink.background) {
                airArt(where.name.uppercase(), air, type, ink)
            }
        }

        val spoken = buildString {
            append(tr("Qualità dell'aria a ${where.name}", "Air quality in ${where.name}", "Luftqualität in ${where.name}", "Qualité de l'air à ${where.name}", "Calidad del aire en ${where.name}"))
            air?.index?.let { append(tr(", indice $it", ", index $it", ", Index $it", ", indice $it", ", índice $it")) }
            air?.band?.let { append(", ${it.label.inMezzoAllaFrase()}") }
        }

        return Drawn(bitmap, spoken)
    }
}

class AirQualityWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AirQualityWidget()
}
