package com.caelumweather.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import com.caelumweather.data.Place
import com.caelumweather.data.Wmo
import com.caelumweather.lingua.tr
import com.caelumweather.prefs.SettingsPrefs
import com.caelumweather.widget.paint.Frame
import com.caelumweather.widget.paint.WidgetCanvas
import com.caelumweather.widget.paint.WidgetInk
import com.caelumweather.widget.paint.WidgetType
import com.caelumweather.widget.paint.prossimeOre
import com.caelumweather.widget.paint.ventoArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Il vento adesso: velocita', direzione e, nei tagli larghi, le prossime ore. */
internal class VentoWidget : CaelumWidget(WidgetKind.VENTO) {

    override suspend fun paint(
        context: Context,
        frame: Frame,
        place: Place?,
        type: WidgetType,
        ink: WidgetInk,
        vista: VistaWidget,
    ): Drawn {
        val where = requireNotNull(place)
        val previsione = WidgetForecast.load(context, where)
        val ore = prossimeOre(previsione)
        // L'unita' e' quella scelta nell'app: un widget che dice "40 km/h" a chi
        // ha chiesto i nodi e' un widget che non si fida.
        val unita = SettingsPrefs(context).settings.first().windUnit

        val bitmap = withContext(Dispatchers.Default) {
            WidgetCanvas.paint(frame, ink.background) { ventoArt(frame, where.name.uppercase(), ore, unita, type, ink) }
        }
        val ora = ore.firstOrNull()
        val spoken = buildString {
            append(tr("Vento a ${where.name}", "Wind in ${where.name}", "Wind in ${where.name}", "Vent à ${where.name}", "Viento en ${where.name}"))
            ora?.windSpeed?.let { append(", ${unita.from(it).roundToInt()} ${unita.label}") }
            ora?.windDirection?.let { append(tr(", da ${Wmo.windDirection(it)}", ", from ${Wmo.windDirection(it)}", ", aus ${Wmo.windDirection(it)}", ", de ${Wmo.windDirection(it)}", ", del ${Wmo.windDirection(it)}")) }
        }
        return Drawn(bitmap, spoken)
    }
}

class VentoWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VentoWidget()
}
