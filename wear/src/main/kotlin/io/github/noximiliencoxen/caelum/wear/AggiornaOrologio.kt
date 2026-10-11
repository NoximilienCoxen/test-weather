package io.github.noximiliencoxen.caelum.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Il lavoro che tiene fresche Tile e complicazione anche quando l'app non si
 * apre: scarica la previsione e chiede al sistema di ridisegnare.
 *
 * **Ogni ora, e non piu' spesso.** Sul polso la batteria e' poca, e Open-Meteo
 * aggiorna i suoi modelli a ore: un giro ogni quindici minuti scaricherebbe
 * quattro volte la stessa risposta. Tile e complicazione intanto leggono la
 * scorta su disco, quindi fra un giro e l'altro non chiedono la rete.
 */
class AggiornaOrologio(contesto: Context, parametri: WorkerParameters) : CoroutineWorker(contesto, parametri) {

    override suspend fun doWork(): Result {
        val risultato = PrevisioneOrologio.carica(applicationContext, preferisciRete = true)
            ?: return Result.retry()
        if (risultato.dallaRete) avvisaQuadrante(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODICO = "caelum-orologio-periodico"
        private const val SUBITO = "caelum-orologio-subito"

        private val conRete = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Il giro orario. Chiamarlo piu' volte non ne crea un secondo. */
        fun pianifica(contesto: Context) {
            val richiesta = PeriodicWorkRequestBuilder<AggiornaOrologio>(1, TimeUnit.HOURS)
                .setConstraints(conRete)
                .build()
            WorkManager.getInstance(contesto)
                .enqueueUniquePeriodicWork(PERIODICO, ExistingPeriodicWorkPolicy.KEEP, richiesta)
        }

        /**
         * Un giro adesso: quando il telefono manda una localita' nuova, Tile e
         * complicazione non devono aspettare l'ora per cambiare citta'.
         */
        fun subito(contesto: Context) {
            val richiesta = OneTimeWorkRequestBuilder<AggiornaOrologio>().setConstraints(conRete).build()
            WorkManager.getInstance(contesto).enqueueUniqueWork(SUBITO, ExistingWorkPolicy.REPLACE, richiesta)
        }
    }
}

/** Chiede al sistema di ridisegnare la Tile e ogni complicazione di Caelum sul quadrante. */
fun avvisaQuadrante(contesto: Context) {
    runCatching { TileService.getUpdater(contesto).requestUpdate(TileCaelum::class.java) }
    runCatching {
        ComplicationDataSourceUpdateRequester
            .create(contesto, ComponentName(contesto, ComplicazioneCaelum::class.java))
            .requestUpdateAll()
    }
}
