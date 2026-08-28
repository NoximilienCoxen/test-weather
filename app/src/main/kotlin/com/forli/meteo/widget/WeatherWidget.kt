package com.forli.meteo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.forli.meteo.R
import com.forli.meteo.data.DeviceLocation
import com.forli.meteo.data.Forecast
import com.forli.meteo.data.Place
import com.forli.meteo.data.WeatherRepository
import com.forli.meteo.data.Wmo
import com.forli.meteo.prefs.SettingsPrefs
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

// Colori di sistema, usati finche' l'istanza non e' stata configurata (o se
// la configurazione e' stata annullata): Glance 1.1.1 non offre un
// ColorProvider giorno/notte, e la scelta del tema la fa il sistema di
// risorse fra values/ e values-night/.
private val WidgetBackground = ColorProvider(R.color.widget_background)
private val WidgetPrimary = ColorProvider(R.color.widget_primary)
private val WidgetSecondary = ColorProvider(R.color.widget_secondary)

class WeatherWidget : GlanceAppWidget() {

    // Ogni istanza tiene la propria localita' e i propri colori, salvati
    // dalla configurazione al posizionamento (vedi WidgetConfigActivity).
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)
            .toWidgetConfig()

        // Un'istanza mai configurata (o con la configurazione annullata)
        // ripiega sulla localita' scelta nell'app: un widget vuoto sarebbe
        // semplicemente rotto.
        val fallback = SettingsPrefs(context).settings.first().place
        val place = when {
            config.useLocation -> DeviceLocation.current(context) ?: config.place ?: fallback
            config.place != null -> config.place
            else -> fallback
        }

        val forecast = WeatherRepository(place).load().getOrNull()

        val background = config.background?.let { ColorProvider(Color(it)) } ?: WidgetBackground
        val accent = config.accent?.let { ColorProvider(Color(it)) } ?: WidgetPrimary

        provideContent {
            WidgetBody(
                place = place,
                forecast = forecast,
                background = background,
                accent = accent,
                secondary = WidgetSecondary,
            )
        }
    }
}

@Composable
private fun WidgetBody(
    place: Place,
    forecast: Forecast?,
    background: ColorProvider,
    accent: ColorProvider,
    secondary: ColorProvider,
) {
    val temperature = forecast?.current?.temperature
    val family = Wmo.family(forecast?.current?.weatherCode)
    val isDay = forecast?.current?.isDay ?: true

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .padding(14.dp)
            .clickable(actionRunCallback<RefreshWidgetAction>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(iconFor(family, isDay)),
            contentDescription = Wmo.condition(forecast?.current?.weatherCode),
            colorFilter = ColorFilter.tint(accent),
            modifier = GlanceModifier.size(30.dp),
        )
        Column(
            modifier = GlanceModifier.padding(start = 10.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.Start,
        ) {
            Text(
                text = place.name.uppercase(),
                style = TextStyle(
                    color = secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = temperature?.roundToInt()?.let { "$it°" } ?: "--",
                style = TextStyle(
                    color = accent,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = Wmo.condition(forecast?.current?.weatherCode),
                style = TextStyle(
                    color = secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

/** Un tocco sul widget forza un nuovo scaricamento. */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WeatherWidget().update(context, glanceId)
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}
