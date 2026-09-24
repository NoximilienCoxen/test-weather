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
import io.github.noximiliencoxen.caelum.widget.paint.weekArt
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
            append(tr("Settimana a ${where.name}", "Week in ${where.name}"))
            days.take(7).forEach { day ->
                append(". ${day.label.lowercase()}, ${Wmo.condition(day.weatherCode).lowercase()}")
                day.tempMax?.roundToInt()?.let { append(tr(", massima $it", ", high $it")) }
                day.tempMin?.roundToInt()?.let { append(tr(", minima $it", ", low $it")) }
            }
        }

        return Drawn(bitmap, spoken)
    }
}

class WeekWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
