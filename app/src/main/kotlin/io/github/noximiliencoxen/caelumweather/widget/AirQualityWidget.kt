package io.github.noximiliencoxen.caelumweather.widget

import io.github.noximiliencoxen.caelumweather.lingua.tr
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import io.github.noximiliencoxen.caelumweather.data.AirQualityRepository
import io.github.noximiliencoxen.caelumweather.data.Place
import io.github.noximiliencoxen.caelumweather.widget.paint.Frame
import io.github.noximiliencoxen.caelumweather.widget.paint.WidgetCanvas
import io.github.noximiliencoxen.caelumweather.widget.paint.WidgetInk
import io.github.noximiliencoxen.caelumweather.widget.paint.WidgetType
import io.github.noximiliencoxen.caelumweather.widget.paint.airArt
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
            append(tr("Qualità dell'aria a ${where.name}", "Air quality in ${where.name}"))
            air?.index?.let { append(tr(", indice $it", ", index $it")) }
            air?.band?.let { append(", ${it.label.lowercase()}") }
        }

        return Drawn(bitmap, spoken)
    }
}

class AirQualityWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AirQualityWidget()
}
