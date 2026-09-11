package io.github.noximiliencoxen.caelum

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
     *
     * **Solo nelle build di debug, e non e' una pignoleria.** Questa Activity
     * e' `exported` perche' e' il lanciatore: chiunque puo' avviarla, e finche'
     * questi extra erano letti in ogni build, **qualunque app installata sul
     * telefono** poteva far comparire a Caelum un'allerta arancione che nessun
     * ente aveva diramato:
     *
     *   am start -n io.github.noximiliencoxen.caelum/.MainActivity --ei allerta 2
     *
     * Non esce nessun dato e non si scrive niente, quindi il danno pratico e'
     * modesto. Ma e' la stessa questione della regola sul rosso: un'app che
     * mostra avvisi di maltempo non deve prestare la propria faccia a un avviso
     * che non viene da dove sembra. Li' la regola vale per il codice
     * dell'app, qui per chi sta fuori.
     *
     * **`BuildConfig.DEBUG` e non un flag nuovo**, perche' `capture.sh` in CI
     * pilota con questi extra l'APK di debug, ed e' l'unico modo che il giro ha
     * di mettere in scena pioggia, allerte e ore notturne senza un dito. Un
     * interruttore inventato apposta li spegnerebbe anche li'. Attenzione a non
     * confonderlo con `isDebuggable`, che in questo progetto e' **falso** anche
     * in debug (si misura la fluidita' della build vera): `BuildConfig.DEBUG`
     * resta comunque vero, e resta quello giusto.
     */
    private fun applyExtras(intent: Intent?) {
        if (intent == null) return
        if (!BuildConfig.DEBUG) return
        // Stessa ragione dell'unico log del modello (vedi `previsione pronta`):
        // serve alla cattura in CI. Senza, che un aggancio sia arrivato si puo'
        // solo **dedurre dai pixel**, e dedurlo e' gia' costato due giri interi
        // su `--ei sezione` muto - per due volte la deduzione era sbagliata.
        Log.i(
            TAG,
            "agganci: ora=${intent.getIntExtra(EXTRA_HOUR, -1)} " +
                "sezione=${intent.getIntExtra(EXTRA_SECTION, -1)} " +
                "meteo=${intent.getIntExtra(EXTRA_WEATHER, -1)} " +
                "allerta=${intent.getIntExtra(EXTRA_ALERT, -1)}",
        )
        intent.getIntExtra(EXTRA_HOUR, -1).takeIf { it >= 0 }?.let(viewModel::requestHour)
        intent.getIntExtra(EXTRA_WEATHER, -1).takeIf { it >= 0 }?.let(viewModel::forceWeatherCode)
        // Il giro accetta anche lo zero, che e' un angolo come un altro: il
        // valore che vuol dire "non imposto" e' il minimo dell'intero, non un
        // numero che qualcuno potrebbe voler chiedere davvero.
        intent.getIntExtra(EXTRA_YAW, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }
            ?.let { viewModel.forceYaw(it.toFloat()) }
        intent.getIntExtra(EXTRA_DAY, -1).takeIf { it >= 0 }?.let(viewModel::requestDay)
        intent.getIntExtra(EXTRA_SECTION, -1).takeIf { it >= 0 }?.let(viewModel::requestRoom)
        if (intent.getBooleanExtra(EXTRA_WELCOME, false)) viewModel.showWelcome()
        intent.getIntExtra(EXTRA_ALERT, -1).takeIf { it >= 0 }?.let(viewModel::forceAlert)
        // Va letto **dopo** EXTRA_ALERT: ridurre la fascia salva gli
        // identificativi di cio' che c'e' in scena, e se l'allerta imposta non
        // ci fosse ancora non ci sarebbe niente da ridurre.
        if (intent.getBooleanExtra(EXTRA_ALERT_SMALL, false)) viewModel.collapseAlerts()
    }

    private companion object {
        /** Lo stesso di `WeatherViewModel`: la cattura in CI filtra su questo. */
        const val TAG = "meteo"

        const val EXTRA_HOUR = "ora"
        const val EXTRA_WEATHER = "meteo"
        const val EXTRA_YAW = "giro"

        /**
         * Apre il dettaglio di un giorno. Serve perche' col dito si arriva
         * solo scorrendo fino in fondo a una pagina, e la trascinata lunga che
         * ci vuole fa cadere l'emulatore della CI.
         */
        const val EXTRA_DAY = "giorno"

        /**
         * Apre Sala su una stanza: 0 oggi, 1 settimana, 2 pioggia, 3 luna,
         * 4 aria, 5 vento, 6 UV.
         *
         * Serve per la stessa ragione di [EXTRA_DAY]: col dito ci si arriva
         * solo scorrendo, e sei trascinate verticali di fila fanno morire
         * l'emulatore della CI. Con questo un solo avvio mette in scena la
         * sala da fotografare, senza un gesto.
         */
        const val EXTRA_SECTION = "sezione"
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

        /**
         * Riduce subito la fascia al pallino, per fotografare quello stato.
         *
         * Con il solo `--ei allerta` si vede sempre e solo la fascia intera: lo
         * stato ridotto si raggiunge con un tocco sulla croce, e la CI non ha
         * un dito. Senza questo aggancio il pallino sarebbe l'unica cosa
         * dell'app che nessuno scatto puo' mostrare.
         */
        const val EXTRA_ALERT_SMALL = "allertaridotta"
    }
}
