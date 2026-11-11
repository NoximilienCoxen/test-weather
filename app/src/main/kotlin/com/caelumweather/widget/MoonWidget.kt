package com.caelumweather.widget

import com.caelumweather.lingua.inMezzoAllaFrase
import com.caelumweather.lingua.tr
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import com.caelumweather.data.Place
import com.caelumweather.data.MoonPhase
import com.caelumweather.data.MoonSegment
import com.caelumweather.widget.paint.Frame
import com.caelumweather.widget.paint.WidgetCanvas
import com.caelumweather.widget.paint.WidgetInk
import com.caelumweather.widget.paint.WidgetType
import com.caelumweather.widget.paint.moonArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * La fase lunare.
 *
 * L'unico widget che non tocca la rete: la fase si calcola dalla data
 * (vedi [MoonPhase]), quindi qui non c'e' niente da scaricare e niente da
 * aspettare. E l'unico che non vuole una citta': [WidgetKind.LUNA] lo dichiara
 * con `needsPlace = false`, quindi [place] arriva nullo e va bene cosi'.
 */
internal class MoonWidget : CaelumWidget(WidgetKind.LUNA) {

    override suspend fun paint(
        context: Context,
        frame: Frame,
        place: Place?,
        type: WidgetType,
        ink: WidgetInk,
    ): Drawn {
        val phase = MoonPhase.at(LocalDate.now())
        val illuminated = MoonPhase.illumination(phase)
        val label = MoonSegment.of(phase).label

        val bitmap = withContext(Dispatchers.Default) {
            WidgetCanvas.paint(frame, ink.background) {
                moonArt(phase, illuminated, label, type, ink)
            }
        }

        val spoken = tr(
            "Luna, ${label.inMezzoAllaFrase()}, ${(illuminated * 100).roundToInt()} per cento illuminata",
            "Moon, ${label.inMezzoAllaFrase()}, ${(illuminated * 100).roundToInt()} percent illuminated",
            "Mond, ${label.inMezzoAllaFrase()}, zu ${(illuminated * 100).roundToInt()} Prozent beleuchtet",
            "Lune, ${label.inMezzoAllaFrase()}, éclairée à ${(illuminated * 100).roundToInt()} pour cent",
            "Luna, ${label.inMezzoAllaFrase()}, iluminada al ${(illuminated * 100).roundToInt()} por ciento",
        )

        return Drawn(bitmap, spoken)
    }
}

class MoonWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoonWidget()
}
