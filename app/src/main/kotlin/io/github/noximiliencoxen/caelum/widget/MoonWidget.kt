package io.github.noximiliencoxen.caelum.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import io.github.noximiliencoxen.caelum.ui.home.MoonPhase
import io.github.noximiliencoxen.caelum.ui.home.MoonSegment
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
 * aspettare.
 */
class MoonWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = appWidgetIdOf(context, id)
        val phase = MoonPhase.at(LocalDate.now())
        val illuminated = MoonPhase.illumination(phase)
        val label = MoonSegment.of(phase).label

        val frame = WidgetCanvas.plan(context, appWidgetId)
        val ink = WidgetInk.of(context)
        val type = WidgetType(context)
        val bitmap = withContext(Dispatchers.Default) {
            WidgetCanvas.paint(frame, ink.background) {
                moonArt(phase, illuminated, label, type, ink)
            }
        }

        val spoken = "Luna, ${label.lowercase()}, " +
            "${(illuminated * 100).roundToInt()} per cento illuminata"

        provideContent {
            WidgetImage(bitmap, spoken, actionRunCallback<RefreshMoonAction>())
        }
    }
}

class RefreshMoonAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        MoonWidget().update(context, glanceId)
    }
}

class MoonWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoonWidget()
}
