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
     * **`BuildConfig.AGGANCI` e non `BuildConfig.DEBUG`**, e la differenza e'
     * costata piu' di un giro di scatti buttato.
     *
     * Qui c'era scritto che `DEBUG` resta vero anche con `isDebuggable` falso,
     * e che quindi era il flag giusto. **Non e' cosi'**: AGP genera `DEBUG` da
     * `isDebuggable`, che questo progetto tiene falso anche in debug per
     * misurare la fluidita' della build vera. Le due cose si erano legate senza
     * che nessuno lo chiedesse, e il risultato era che **ogni aggancio qui
     * dentro era muto**: la CI chiedeva l'ora notturna, la sezione, il giorno,
     * il codice meteo, e l'app li ignorava tutti in silenzio. Sei sezioni
     * fotografate erano sei copie della prima, e per due passate si e' cercata
     * la colpa nella schermata.
     *
     * `AGGANCI` sta in `app/build.gradle.kts`, dichiarato a voce sui due tipi
     * di build. La proprieta' di sicurezza e' identica - falso sulla release,
     * quindi nessuna app installata puo' far comparire a Caelum un'allerta che
     * nessun ente ha diramato - ma adesso non dipende da come AGP interpreta un
     * flag che parla d'altro.
     */
    private fun applyExtras(intent: Intent?) {
        if (intent == null) return
        if (!BuildConfig.AGGANCI) return
        intent.getIntExtra(EXTRA_HOUR, -1).takeIf { it >= 0 }?.let(viewModel::requestHour)
        intent.getIntExtra(EXTRA_WEATHER, -1).takeIf { it >= 0 }?.let(viewModel::forceWeatherCode)
        // Il giro accetta anche lo zero, che e' un angolo come un altro: il
        // valore che vuol dire "non imposto" e' il minimo dell'intero, non un
        // numero che qualcuno potrebbe voler chiedere davvero.
        intent.getIntExtra(EXTRA_YAW, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }
            ?.let { viewModel.forceYaw(it.toFloat()) }
        intent.getIntExtra(EXTRA_DAY, -1).takeIf { it >= 0 }?.let(viewModel::requestDay)
        intent.getIntExtra(EXTRA_SECTION, -1).takeIf { it >= 0 }?.let(viewModel::requestSection)
        if (intent.getBooleanExtra(EXTRA_WELCOME, false)) viewModel.showWelcome()
        intent.getIntExtra(EXTRA_ALERT, -1).takeIf { it >= 0 }?.let(viewModel::forceAlert)
        // Va letto **dopo** EXTRA_ALERT: ridurre la fascia salva gli
        // identificativi di cio' che c'e' in scena, e se l'allerta imposta non
        // ci fosse ancora non ci sarebbe niente da ridurre.
        if (intent.getBooleanExtra(EXTRA_ALERT_SMALL, false)) viewModel.collapseAlerts()
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

        /**
         * Apre il feed su una sezione: 0 temperatura, 1 pioggia, 2 aria,
         * 3 vento, 4 sole, 5 luna.
         *
         * Serve per la stessa ragione di [EXTRA_DAY]: col dito ci si arriva
         * solo scorrendo, e cinque trascinate verticali di fila fanno morire
         * l'emulatore della CI. Con questo un solo avvio mette in scena la
         * scheda da fotografare, senza un gesto.
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
