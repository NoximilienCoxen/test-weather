package com.caelumweather.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import com.caelumweather.data.MareRepository
import com.caelumweather.data.Place
import com.caelumweather.data.StatoDelMare
import com.caelumweather.data.prossimiEstremi
import com.caelumweather.lingua.tr
import com.caelumweather.prefs.SettingsPrefs
import com.caelumweather.widget.paint.Frame
import com.caelumweather.widget.paint.WidgetCanvas
import com.caelumweather.widget.paint.WidgetInk
import com.caelumweather.widget.paint.WidgetType
import com.caelumweather.widget.paint.mareArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Il mare adesso: onda, acqua e prossima marea, solo per le localita' di costa.
 *
 * Dove il mare non c'e' (risposta riuscita e vuota) il widget lo dice; se la
 * rete non risponde disegna i trattini - **le due cose non si scambiano**, come
 * nella sala: "qui non c'e' mare" e "non lo so" sono risposte diverse.
 */
internal class MareWidget : CaelumWidget(WidgetKind.MARE) {

    override suspend fun paint(
        context: Context,
        frame: Frame,
        place: Place?,
        type: WidgetType,
        ink: WidgetInk,
        vista: VistaWidget,
    ): Drawn {
        val where = requireNotNull(place)
        val risposta = MareRepository(where).load()
        val mare = risposta.getOrNull()
        val senzaMare = risposta.isSuccess && mare == null
        val unita = SettingsPrefs(context).settings.first().unit

        val ora = mare?.adesso()
        val adesso = mare?.utcOffsetSeconds?.let {
            Instant.now().atOffset(ZoneOffset.ofTotalSeconds(it)).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)
        }
        val prossima = if (mare != null && adesso != null) prossimiEstremi(mare, adesso, 1).firstOrNull() else null

        val bitmap = withContext(Dispatchers.Default) {
            WidgetCanvas.paint(frame, ink.background) {
                mareArt(frame, where.name.uppercase(), ora, mare, senzaMare, prossima, adesso, unita, type, ink)
            }
        }
        val spoken = buildString {
            append(tr("Mare a ${where.name}", "Sea in ${where.name}", "Meer in ${where.name}", "Mer à ${where.name}", "Mar en ${where.name}"))
            when {
                senzaMare -> append(tr(", località non di costa", ", not a coastal place", ", kein Küstenort", ", pas un lieu côtier", ", lugar sin costa"))
                ora?.onda != null -> append(", ${StatoDelMare.di(ora.onda)?.label}")
            }
        }
        return Drawn(bitmap, spoken)
    }
}

class MareWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MareWidget()
}
