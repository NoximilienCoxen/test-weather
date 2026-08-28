package com.forli.meteo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.unit.ColorProvider
import com.forli.meteo.R
import com.forli.meteo.data.DeviceLocation
import com.forli.meteo.data.Place
import com.forli.meteo.prefs.SettingsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Glance 1.1.1 non offre un ColorProvider giorno/notte: quando l'utente non
// sceglie una tinta, la decide il sistema di risorse fra values/ e values-night/.
private val DefaultBackground = ColorProvider(R.color.widget_background)
private val DefaultPrimary = ColorProvider(R.color.widget_primary)
private val DefaultSecondary = ColorProvider(R.color.widget_secondary)

/** I tre colori con cui si disegna un widget. */
internal class WidgetPalette(
    val background: ColorProvider,
    val accent: ColorProvider,
    val secondary: ColorProvider,
)

internal fun WidgetConfig.palette(): WidgetPalette {
    val chosenAccent = accent?.let { Color(it) }
    return WidgetPalette(
        background = background?.let { ColorProvider(Color(it)) } ?: DefaultBackground,
        accent = chosenAccent?.let { ColorProvider(it) } ?: DefaultPrimary,
        // Il secondario segue l'accento invece di restare il grigio di sistema:
        // su uno sfondo scelto a mano quel grigio poteva sparirci dentro.
        secondary = chosenAccent?.let { ColorProvider(it.copy(alpha = 0.65f)) }
            ?: DefaultSecondary,
    )
}

/**
 * La localita' da mostrare.
 *
 * Un widget mai configurato, o che segue il telefono senza riuscire a sapere
 * dove sia, ripiega sulla localita' scelta nell'app: mostrare niente sarebbe
 * peggio che mostrare il posto sbagliato.
 */
internal suspend fun WidgetConfig.resolvePlace(context: Context): Place {
    suspend fun fromApp(): Place = SettingsPrefs(context).settings.first().place
    return when {
        useLocation -> DeviceLocation.current(context) ?: place ?: fromApp()
        place != null -> place
        else -> fromApp()
    }
}

internal suspend fun appWidgetIdOf(context: Context, glanceId: GlanceId): Int =
    GlanceAppWidgetManager(context).getAppWidgetId(glanceId)

/**
 * Ridisegna il widget appena configurato, qualunque dei tre sia.
 *
 * Il tipo si chiede al sistema invece di farselo passare: la configurazione
 * riceve solo un identificativo, e tre Activity quasi identiche per distinguere
 * tre widget sarebbero tre volte lo stesso codice.
 */
internal suspend fun refreshWidget(context: Context, appWidgetId: Int) {
    val provider = AppWidgetManager.getInstance(context)
        .getAppWidgetInfo(appWidgetId)?.provider?.className
    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    when (provider) {
        MoonWidgetReceiver::class.java.name -> MoonWidget().update(context, glanceId)
        AirQualityWidgetReceiver::class.java.name -> AirQualityWidget().update(context, glanceId)
        else -> WeatherWidget().update(context, glanceId)
    }
}

/**
 * Il ricevitore di un widget configurabile.
 *
 * Esiste solo per buttare via le scelte di un widget che non c'e' piu': senza,
 * l'archivio crescerebbe a ogni widget aggiunto e tolto, e l'identificativo
 * riassegnato a un widget nuovo si porterebbe dietro i colori del precedente.
 */
abstract class ConfigurableWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // La cancellazione arriva come broadcast, che non aspetta le coroutine:
        // goAsync() tiene in vita il processo il tempo di scrivere.
        val pending = goAsync()
        val prefs = WidgetPrefs(context.applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { prefs.forget(it) }
            } finally {
                pending.finish()
            }
        }
    }
}
