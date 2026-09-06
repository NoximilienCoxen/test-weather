package io.github.noximiliencoxen.caelum.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.ui.home.MoonPhase
import io.github.noximiliencoxen.caelum.ui.home.MoonSegment
import io.github.noximiliencoxen.caelum.widget.paint.Frame
import io.github.noximiliencoxen.caelum.widget.paint.WidgetCanvas
import io.github.noximiliencoxen.caelum.widget.paint.WidgetInk
import io.github.noximiliencoxen.caelum.widget.paint.WidgetType
import io.github.noximiliencoxen.caelum.widget.paint.moonArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * La fase lunare.
 *
 * L'unico dei tre widget che non tocca la rete: la fase si calcola dalla data
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

        val spoken = "Luna, ${label.lowercase()}, " +
            "${(illuminated * 100).roundToInt()} per cento illuminata"

        return Drawn(bitmap, spoken)
    }
}

class MoonWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoonWidget()
}
