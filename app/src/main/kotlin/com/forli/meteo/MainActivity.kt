package com.forli.meteo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.forli.meteo.prefs.ThemeMode
import com.forli.meteo.ui.MeteoApp
import com.forli.meteo.ui.WeatherViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyExtras(intent)
        setContent {
            MeteoApp(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyExtras(intent)
    }

    /**
     * Permette di fissare il tema all'avvio:
     *   adb shell am start -n .../.MainActivity --es tema SCURO
     *
     * Serve alla cattura automatica degli screenshot: pilotare la modalita'
     * notte di sistema con "cmd uimode" fa ripartire SystemUI e sugli
     * emulatori headless fa cadere la connessione adb.
     */
    private fun applyExtras(intent: Intent?) {
        if (intent == null) return

        intent.getStringExtra(EXTRA_THEME)?.uppercase()?.let { requested ->
            ThemeMode.entries.firstOrNull { it.name == requested }?.let(viewModel::setThemeMode)
        }

        // Agganci per la verifica automatica. Alcuni stati non si possono
        // aspettare dal meteo vero: oggi a Forli' e' sereno tutte le
        // ventiquattro ore, quindi nuvole e pioggia non comparirebbero mai in
        // uno scatto. Imporli e' l'unico modo per vederli.
        intent.getIntExtra(EXTRA_HOUR, -1).takeIf { it >= 0 }?.let(viewModel::requestHour)
        intent.getIntExtra(EXTRA_WEATHER, -1).takeIf { it >= 0 }?.let(viewModel::forceWeatherCode)
    }

    private companion object {
        const val EXTRA_THEME = "tema"
        const val EXTRA_HOUR = "ora"
        const val EXTRA_WEATHER = "meteo"
    }
}
