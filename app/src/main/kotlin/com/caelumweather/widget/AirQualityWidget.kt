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
        vista: VistaWidget,
    ): Drawn {
        val where = requireNotNull(place)
        val air = ScortaAria.carica(where)

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

/**
 * L'ultima aria buona di ogni localita', in memoria: come la scorta del mare
 * (`MareRepository.loadConScorta`), perche' ridimensionare il widget lo
 * ridisegna e ogni ridisegno chiedeva l'aria alla rete. Fresca un'ora vale
 * come una risposta nuova; piu' vecchia si richiede, e se la rete non
 * risponde si usa fino a un giorno.
 */
private object ScortaAria {
    private val scorte = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, com.caelumweather.data.AirQuality>>()

    suspend fun carica(posto: Place): com.caelumweather.data.AirQuality? {
        val chiave = "${posto.latitude}_${posto.longitude}"
        val adesso = System.currentTimeMillis()
        val nota = scorte[chiave]
        if (nota != null && adesso - nota.first < 60 * 60_000L) return nota.second
        val fresca = AirQualityRepository(posto).load().getOrNull()
        if (fresca != null) {
            scorte[chiave] = adesso to fresca
            return fresca
        }
        return nota?.takeIf { adesso - it.first < 24 * 60 * 60_000L }?.second
    }
}
