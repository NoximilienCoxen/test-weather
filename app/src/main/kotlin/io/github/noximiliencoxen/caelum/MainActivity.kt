package io.github.noximiliencoxen.caelum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.github.noximiliencoxen.caelum.ui.MeteoApp
import io.github.noximiliencoxen.caelum.ui.WeatherViewModel
import io.github.noximiliencoxen.caelum.widget.repaintWidgets
import kotlinx.coroutines.launch

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
     * Ridisegna i widget quando l'app torna in primo piano.
     *
     * I widget adesso sono immagini gia' dipinte, e un'immagine non cambia
     * colore da sola: cambiando il tema del telefono resterebbero chiari su una
     * schermata scura fino al risveglio successivo, mezz'ora piu' tardi. Chi
     * cambia tema passa quasi sempre di qui subito dopo.
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { repaintWidgets(applicationContext) }
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
        // Il giro accetta anche lo zero, che e' un angolo come un altro: il
        // valore che vuol dire "non imposto" e' il minimo dell'intero, non un
        // numero che qualcuno potrebbe voler chiedere davvero.
        intent.getIntExtra(EXTRA_YAW, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }
            ?.let { viewModel.forceYaw(it.toFloat()) }
        intent.getIntExtra(EXTRA_DAY, -1).takeIf { it >= 0 }?.let(viewModel::requestDayDetail)
        if (intent.getBooleanExtra(EXTRA_WELCOME, false)) viewModel.showWelcome()
        intent.getIntExtra(EXTRA_ALERT, -1).takeIf { it >= 0 }?.let(viewModel::forceAlert)
    }

    private companion object {
        const val EXTRA_HOUR = "ora"
        const val EXTRA_WEATHER = "meteo"
        const val EXTRA_YAW = "giro"

        /**
         * Apre il dettaglio di un giorno. Serve perche' col dito si arriva
         * solo scorrendo fino in fondo a una pagina, e la trascinata lunga che
         * ci vuole fa cadere l'emulatore della CI.
         */
        const val EXTRA_DAY = "giorno"
        const val EXTRA_WELCOME = "benvenuto"

        /**
         * Mette in scena un'allerta finta, per gradino: 1 gialla, 2 arancione,
         * 3 rossa.
         *
         * Senza, la fascia si potrebbe fotografare solo nei giorni in cui la
         * Protezione Civile ha davvero diramato qualcosa su Forli', cioe' quasi
         * mai e mai su richiesta. Un riquadro che compare solo col maltempo e'
         * esattamente quello che nessuno riesce a verificare prima di
         * pubblicarlo.
         */
        const val EXTRA_ALERT = "allerta"
    }
}
