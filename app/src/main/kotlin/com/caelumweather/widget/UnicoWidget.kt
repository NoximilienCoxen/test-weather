package com.caelumweather.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import com.caelumweather.data.Place
import com.caelumweather.widget.paint.Frame
import com.caelumweather.widget.paint.WidgetInk
import com.caelumweather.widget.paint.WidgetType

/**
 * Il widget unico: una faccia sola che si ridimensiona, al posto di sei.
 *
 * **Non ridisegna niente di suo**: sceglie quale degli altri disegni far
 * dipingere - quello che [vistaDaForma] dice per la forma del riquadro, o quello
 * che la configurazione ha fissato - e glielo gira cosi' com'e'. Per questo
 * ogni novita' dei widget singoli arriva qui da sola, e per questo i sei vecchi
 * restano: chi li ha gia' sulla Home non li perde, e chi parte adesso ne
 * piazza uno solo.
 */
internal class UnicoWidget : CaelumWidget(WidgetKind.UNICO) {

    override suspend fun paint(
        context: Context,
        frame: Frame,
        place: Place?,
        type: WidgetType,
        ink: WidgetInk,
        vista: VistaWidget,
    ): Drawn {
        val scelta = if (vista == VistaWidget.AUTO) vistaDaForma(frame.widthPx / frame.scale, frame.heightPx / frame.scale) else vista
        val delegato: CaelumWidget = when (scelta) {
            VistaWidget.MINI -> MiniWidget()
            VistaWidget.ORE -> HoursWidget()
            VistaWidget.SETTIMANA -> WeekWidget()
            VistaWidget.ARIA -> AirQualityWidget()
            VistaWidget.LUNA -> MoonWidget()
            VistaWidget.VENTO -> VentoWidget()
            VistaWidget.MARE -> MareWidget()
            VistaWidget.METEO, VistaWidget.AUTO -> WeatherWidget()
        }
        return delegato.paint(context, frame, place, type, ink, scelta)
    }
}

/**
 * Quale faccia sta nel riquadro, dalle sue misure in dp. Pura, per provarla.
 *
 * Le soglie sono quelle su cui i disegni sono stati costruiti: sotto i 120 dp
 * di lato ci sta una cifra e una figuretta (la cella); fino a 240 di larghezza
 * il tempo adesso; piu' largo e basso il tempo adesso in orizzontale; piu'
 * largo e alto le prossime ore, e da 230 in su la settimana.
 */
internal fun vistaDaForma(larghezzaDp: Float, altezzaDp: Float): VistaWidget = when {
    larghezzaDp < 120f && altezzaDp < 120f -> VistaWidget.MINI
    larghezzaDp < 240f -> VistaWidget.METEO
    altezzaDp < 130f -> VistaWidget.METEO
    altezzaDp < 230f -> VistaWidget.ORE
    else -> VistaWidget.SETTIMANA
}

class UnicoWidgetReceiver : ConfigurableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UnicoWidget()
}
