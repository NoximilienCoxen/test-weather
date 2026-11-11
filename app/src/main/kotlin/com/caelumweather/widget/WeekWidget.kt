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
import com.caelumweather.widget.paint.weekArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** I prossimi sette giorni: il tempo, la massima e la minima di ciascuno. */
internal class WeekWidget : CaelumWidget(WidgetKind.SETTIMANA) {

    override suspend fun paint(
        context: Context,
        frame: Frame,
        place: Place?,
        type: WidgetType,
        ink: WidgetInk,
        vista: VistaWidget,
    ): Drawn {
        // Non-null per costruzione: SETTIMANA dichiara `needsPlace`.
        val where = requireNotNull(place)
        val days = WidgetForecast.load(context, where)?.days.orEmpty()

        val bitmap = withContext(Dispatchers.Default) {
            WidgetCanvas.paint(frame, ink.background) {
                weekArt(frame, where.name.uppercase(), days, type, ink)
            }
        }

        // Chi non vede l'immagine sente la settimana giorno per giorno: la
        // barretta non si legge ad alta voce, i numeri si'.
        val spoken = buildString {
            append(tr("Settimana a ${where.name}", "Week in ${where.name}", "Woche in ${where.name}", "Semaine à ${where.name}", "Semana en ${where.name}"))
            days.take(7).forEach { day ->
                append(". ${day.label.lowercase()}, ${Wmo.condition(day.weatherCode).inMezzoAllaFrase()}")
                day.tempMax?.roundToInt()?.let { append(tr(", massima $it", ", high $it", ", Höchstwert $it", ", maximale $it", ", máxima $it")) }
                day.tempMin?.roundToInt()?.let { append(tr(", minima $it", ", low $it", ", Tiefstwert $it", ", minimale $it", ", mínima $it")) }
            }
        }

        return Drawn(bitmap, spoken)
    }
}

class WeekWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
