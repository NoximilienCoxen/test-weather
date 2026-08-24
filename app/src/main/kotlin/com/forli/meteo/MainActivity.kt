package com.forli.meteo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.forli.meteo.prefs.ThemeMode
import com.forli.meteo.ui.WeatherApp
import com.forli.meteo.ui.WeatherViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyThemeExtra(intent)
        setContent {
            WeatherApp(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyThemeExtra(intent)
    }

    /**
     * Permette di fissare il tema all'avvio:
     *   adb shell am start -n .../.MainActivity --es tema SCURO
     *
     * Serve alla cattura automatica degli screenshot: pilotare la modalita'
     * notte di sistema con "cmd uimode" fa ripartire SystemUI e sugli
     * emulatori headless fa cadere la connessione adb.
     */
    private fun applyThemeExtra(intent: Intent?) {
        val requested = intent?.getStringExtra(EXTRA_THEME)?.uppercase() ?: return
        ThemeMode.entries.firstOrNull { it.name == requested }?.let(viewModel::setThemeMode)
    }

    private companion object {
        const val EXTRA_THEME = "tema"
    }
}
