package com.forli.meteo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
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
 * Quale dei tre widget e' quello con questo identificativo.
 *
 * Si chiede al sistema invece di farselo passare: la configurazione riceve solo
 * un identificativo, e tre Activity quasi identiche per distinguere tre widget
 * sarebbero tre volte lo stesso codice.
 */
enum class WidgetKind {
    METEO, LUNA, ARIA;

    fun widget(): GlanceAppWidget = when (this) {
        METEO -> WeatherWidget()
        LUNA -> MoonWidget()
        ARIA -> AirQualityWidget()
    }

    companion object {
        fun of(context: Context, appWidgetId: Int): WidgetKind? =
            when (
                AppWidgetManager.getInstance(context)
                    .getAppWidgetInfo(appWidgetId)?.provider?.className
            ) {
                MoonWidgetReceiver::class.java.name -> LUNA
                AirQualityWidgetReceiver::class.java.name -> ARIA
                WeatherWidgetReceiver::class.java.name -> METEO
                // Nessun tipo: il widget non risulta ancora agganciato. Non si
                // tira a indovinare, perche' ridisegnare il widget sbagliato
                // significa mettere la luna al posto della temperatura.
                else -> null
            }
    }
}

/**
 * Ridisegna il widget appena configurato.
 *
 * Serve davvero, e non e' una cortesia: il lanciatore aggancia il widget
 * **prima** di aprire la configurazione, quindi a quel punto e' gia' stato
 * disegnato una volta con le preferenze ancora vuote. Senza questo ridisegno
 * la tinta appena scelta non comparirebbe fino al risveglio successivo, mezz'ora
 * piu' tardi.
 */
internal suspend fun refreshWidget(context: Context, appWidgetId: Int, kind: WidgetKind?) {
    val resolved = kind ?: WidgetKind.of(context, appWidgetId) ?: return
    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    resolved.widget().update(context, glanceId)
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
