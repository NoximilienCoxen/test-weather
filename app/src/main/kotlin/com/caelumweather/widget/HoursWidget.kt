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
import com.caelumweather.widget.paint.hoursArt
import com.caelumweather.widget.paint.prossimeOre
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
        vista: VistaWidget,
    ): Drawn {
        val where = requireNotNull(place)
        val hours = prossimeOre(WidgetForecast.load(context, where))
        val bitmap = withContext(Dispatchers.Default) {
            WidgetCanvas.paint(frame, ink.background) {
                hoursArt(frame, where.name.uppercase(), hours, type, ink)
            }
        }
        val spoken = buildString {
            append(tr("Prossime ore a ${where.name}", "Next hours in ${where.name}", "Nächste Stunden in ${where.name}", "Prochaines heures à ${where.name}", "Próximas horas en ${where.name}"))
            hours.take(6).forEach { h ->
                append(tr(". Alle ${h.time.hour}, ", ". At ${h.time.hour}, ", ". Um ${h.time.hour} Uhr, ", ". À ${h.time.hour} h, ", ". A las ${h.time.hour}, ") + Wmo.condition(h.weatherCode).inMezzoAllaFrase())
                h.temperature?.roundToInt()?.let { append(tr(", $it gradi", ", $it degrees", ", $it Grad", ", $it degrés", ", $it grados")) }
                h.precipProbability?.takeIf { it >= 10 }?.let { append(tr(", pioggia al $it per cento", ", $it per cent chance of rain", ", Regenwahrscheinlichkeit $it Prozent", ", $it pour cent de risque de pluie", ", $it por ciento de probabilidad de lluvia")) }
            }
        }
        return Drawn(bitmap, spoken)
    }
}

class HoursWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HoursWidget()
}
