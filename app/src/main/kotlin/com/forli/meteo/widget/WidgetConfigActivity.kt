package com.forli.meteo.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
    private var kind: WidgetKind? = null

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

        // Il tipo si legge qui e si tiene: adesso il lanciatore ha gia'
        // agganciato il widget, mentre al momento del salvataggio la stessa
        // domanda potrebbe non avere piu' risposta.
        kind = WidgetKind.of(this, appWidgetId)

        setContent {
            // Palette neutra, non quella dell'ora del giorno: e' una schermata
            // di sistema, non una schermata dell'app.
            MeteoTheme(colors = skyColors(SkyState.Giorno)) {
                WidgetConfigScreen(
                    kind = kind,
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

        // Il ridisegno non e' una cortesia: il widget e' gia' stato disegnato
        // una volta, prima che questa schermata si aprisse, con le preferenze
        // ancora vuote. Se fallisce lo si dice, invece di lasciare all'utente
        // un widget del colore sbagliato senza spiegazione.
        runCatching { refreshWidget(this, appWidgetId, kind) }
            .onFailure { Log.w(TAG, "il widget $appWidgetId non si e' ridisegnato", it) }

        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        finish()
    }

    private companion object {
        const val TAG = "WidgetConfig"
    }
}
