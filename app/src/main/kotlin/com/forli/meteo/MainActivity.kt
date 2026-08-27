package com.forli.meteo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
     * Agganci per la verifica automatica:
     *   adb shell am start -n .../.MainActivity --ei ora 2 --ei meteo 63
     *
     * Alcuni stati non si possono aspettare dal meteo vero: se la giornata e'
     * serena da mezzanotte a mezzanotte, nuvole e pioggia non comparirebbero
     * mai in uno scatto. Imporli e' l'unico modo per vederli.
     *
     * L'aggancio sul tema non c'e' piu' perche' non c'e' piu' un tema da
     * scegliere: giorno e notte adesso li decide l'ora mostrata, e per
     * fotografare la notte basta chiedere un'ora notturna.
     */
    private fun applyExtras(intent: Intent?) {
        if (intent == null) return
        intent.getIntExtra(EXTRA_HOUR, -1).takeIf { it >= 0 }?.let(viewModel::requestHour)
        intent.getIntExtra(EXTRA_WEATHER, -1).takeIf { it >= 0 }?.let(viewModel::forceWeatherCode)
    }

    private companion object {
        const val EXTRA_HOUR = "ora"
        const val EXTRA_WEATHER = "meteo"
    }
}
