package com.forli.meteo.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.forli.meteo.data.Place
import com.forli.meteo.data.SkyState
import com.forli.meteo.ui.theme.MeteoTheme
import com.forli.meteo.ui.theme.skyColors
import com.forli.meteo.ui.widgetconfig.WidgetConfigScreen
import kotlinx.coroutines.launch

/**
 * Aperta dal sistema subito dopo che il widget e' stato trascinato sulla
 * Home ([android:configure] in `weather_widget_info.xml`): sceglie la
 * localita' e i colori di quella singola istanza, prima che compaia.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Contratto standard di AppWidgetProvider: se l'utente esce senza
        // salvare, il sistema deve considerare il posizionamento annullato.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            // Palette neutra, non quella dell'ora del giorno: e' un dialogo
            // di sistema, non una schermata dell'app.
            MeteoTheme(colors = skyColors(SkyState.Giorno)) {
                WidgetConfigScreen(
                    onSave = { place, useLocation, background, accent ->
                        lifecycleScope.launch { saveAndFinish(place, useLocation, background, accent) }
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    private suspend fun saveAndFinish(
        place: Place?,
        useLocation: Boolean,
        background: Color,
        accent: Color,
    ) {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)

        updateAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetPrefKeys.USE_LOCATION] = useLocation
                if (!useLocation && place != null) {
                    this[WidgetPrefKeys.PLACE_JSON] = placeToJson(place)
                } else {
                    remove(WidgetPrefKeys.PLACE_JSON)
                }
                this[WidgetPrefKeys.BACKGROUND_ARGB] = background.toArgb()
                this[WidgetPrefKeys.ACCENT_ARGB] = accent.toArgb()
            }
        }
        WeatherWidget().update(this, glanceId)

        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, result)
        finish()
    }
}
