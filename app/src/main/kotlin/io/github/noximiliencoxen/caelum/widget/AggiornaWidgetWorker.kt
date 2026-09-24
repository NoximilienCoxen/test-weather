package io.github.noximiliencoxen.caelum.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.noximiliencoxen.caelum.lingua.Lingua
import java.util.concurrent.TimeUnit

/**
 * Scarica le previsioni dei widget con la rete garantita, anche ad app chiusa.
 *
 * **Il widget restava su "--" appena l'app non era piu' aperta.** Il suo
 * ridisegno gira nel processo dell'app ma senza che l'app sia in primo piano,
 * e li' Android la rete la concede quando vuole: con l'app usata di rado, col
 * risparmio energetico, o con un firewall come NetGuard, non la concede
 * affatto. La richiesta falliva, e la scorta su disco (`WidgetForecast`) non
 * si riempiva mai, perche' si riempie solo con una richiesta riuscita.
 *
 * Un lavoro di WorkManager con il vincolo "rete connessa" e' diverso: il
 * sistema lo fa partire **quando** la rete c'e', e mentre gira la rete e' sua.
 * Qui si scarica per ogni widget, si riempie la scorta, e poi si ridisegna: i
 * widget trovano la previsione fresca su disco e non devono chiedere niente.
 */
class AggiornaWidgetWorker(contesto: Context, parametri: WorkerParameters) : CoroutineWorker(contesto, parametri) {

    override suspend fun doWork(): Result {
        val contesto = applicationContext
        Lingua.carica(contesto)
        val manager = GlanceAppWidgetManager(contesto)
        val prefs = WidgetPrefs(contesto)
        var tentati = 0
        var riusciti = 0
        WidgetKind.entries.filter { it.needsPlace }.forEach { kind ->
            val ids = runCatching { manager.getGlanceIds(kind.widget().javaClass) }.getOrDefault(emptyList())
            ids.forEach { glanceId ->
                val appWidgetId = runCatching { manager.getAppWidgetId(glanceId) }.getOrNull() ?: return@forEach
                val posto = prefs.load(appWidgetId).resolvePlace(contesto, appWidgetId) ?: return@forEach
                // L'aria non ha scorta (un indice di ore fa non e' quello di
                // adesso): il ridisegno qui sotto la riprova comunque, e
                // mentre questo lavoro gira la rete c'e'.
                if (kind == WidgetKind.ARIA) return@forEach
                tentati++
                if (WidgetForecast.scarica(contesto, posto)) riusciti++
            }
        }
        repaintWidgets(contesto)
        return if (tentati > 0 && riusciti == 0) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODICO = "caelum-widget-periodico"
        private const val SUBITO = "caelum-widget-subito"

        private val conRete = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /**
         * Ogni mezz'ora circa, finche' c'e' un widget. `KEEP`: chiamarlo di
         * nuovo non sposta il turno, quindi lo si puo' chiamare a ogni avvio.
         */
        fun pianifica(context: Context) {
            val richiesta = PeriodicWorkRequestBuilder<AggiornaWidgetWorker>(30, TimeUnit.MINUTES)
                .setConstraints(conRete)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODICO, ExistingPeriodicWorkPolicy.KEEP, richiesta)
        }

        /**
         * Appena torna la rete, una volta. Lo chiede il widget che non e'
         * riuscito a scaricare e non ha una scorta fresca; `KEEP` fa si' che
         * dieci widget falliti insieme chiedano un lavoro solo.
         */
        fun appenaPossibile(context: Context) {
            val richiesta = OneTimeWorkRequestBuilder<AggiornaWidgetWorker>()
                .setConstraints(conRete)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(SUBITO, ExistingWorkPolicy.KEEP, richiesta)
        }

        /** Nessun widget piu' sulla Home: niente da aggiornare. */
        fun annulla(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(PERIODICO)
                cancelUniqueWork(SUBITO)
            }
        }
    }
}
