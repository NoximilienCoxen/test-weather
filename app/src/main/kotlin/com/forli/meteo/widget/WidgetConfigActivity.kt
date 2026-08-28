package com.forli.meteo.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.lifecycleScope
import com.forli.meteo.data.Place
import com.forli.meteo.data.SkyState
import com.forli.meteo.ui.theme.MeteoTheme
import com.forli.meteo.ui.theme.skyColors
import com.forli.meteo.ui.widgetconfig.WidgetConfigScreen
import kotlinx.coroutines.launch

/**
 * Aperta dal sistema subito dopo che un widget e' stato trascinato sulla Home
 * (attributo `configure` dei provider): sceglie la localita' e i colori di
 * quella singola istanza, prima che compaia.
 *
 * Una sola per tutti e tre i widget: quale sia lo si chiede al sistema al
 * momento di ridisegnarlo.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Contratto standard dei widget: se l'utente esce senza salvare, il
        // sistema deve considerare il posizionamento annullato.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // La luna e' la stessa da qualunque parte la si guardi: chiederle una
        // citta' sarebbe una domanda senza conseguenze.
        val provider = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)?.provider?.className
        val needsPlace = provider != MoonWidgetReceiver::class.java.name

        setContent {
            // Palette neutra, non quella dell'ora del giorno: e' una schermata
            // di sistema, non una schermata dell'app.
            MeteoTheme(colors = skyColors(SkyState.Giorno)) {
                WidgetConfigScreen(
                    showLocation = needsPlace,
                    onSave = { place, useLocation, background, accent ->
                        lifecycleScope.launch {
                            saveAndFinish(place, useLocation, background, accent)
                        }
                    },
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
        WidgetPrefs(this).save(
            appWidgetId = appWidgetId,
            config = WidgetConfig(
                useLocation = useLocation,
                place = place,
                background = background.toArgb(),
                accent = accent.toArgb(),
            ),
        )

        // Un ridisegno subito, cosi' il widget nasce gia' con la tinta scelta.
        // Se non riesce non e' grave e non deve impedire il salvataggio: il
        // primo disegno legge comunque le stesse preferenze, che ora ci sono.
        runCatching { refreshWidget(this, appWidgetId) }

        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        finish()
    }
}
