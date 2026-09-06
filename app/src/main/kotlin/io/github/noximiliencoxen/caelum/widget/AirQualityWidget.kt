package io.github.noximiliencoxen.caelum.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import io.github.noximiliencoxen.caelum.data.AirQualityRepository
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.widget.paint.Frame
import io.github.noximiliencoxen.caelum.widget.paint.WidgetCanvas
import io.github.noximiliencoxen.caelum.widget.paint.WidgetInk
import io.github.noximiliencoxen.caelum.widget.paint.WidgetType
import io.github.noximiliencoxen.caelum.widget.paint.airArt
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
            append("Qualità dell'aria a ${where.name}")
            air?.europeanAqi?.let { append(", indice $it") }
            air?.band?.let { append(", ${it.label.lowercase()}") }
        }

        return Drawn(bitmap, spoken)
    }
}

class AirQualityWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AirQualityWidget()
}
