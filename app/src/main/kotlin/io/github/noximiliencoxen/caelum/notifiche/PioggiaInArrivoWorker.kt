package io.github.noximiliencoxen.caelum.notifiche

import io.github.noximiliencoxen.caelum.lingua.Lingua
import io.github.noximiliencoxen.caelum.lingua.tr
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.noximiliencoxen.caelum.MainActivity
import io.github.noximiliencoxen.caelum.R
import io.github.noximiliencoxen.caelum.data.PrecipitazioneInArrivo
import io.github.noximiliencoxen.caelum.data.PrevisioneABreve
import io.github.noximiliencoxen.caelum.data.TipoPrecipitazione
import io.github.noximiliencoxen.caelum.data.key
import io.github.noximiliencoxen.caelum.data.precipitazioneInArrivo
import io.github.noximiliencoxen.caelum.prefs.SettingsPrefs
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Ogni quarto d'ora guarda l'ora che viene sopra la citta' dell'app, e se sta
 * per cominciare a piovere - o a grandinare - lo dice con una notifica.
 *
 * Il quarto d'ora e' il passo piu' corto che WorkManager concede, ed e' anche
 * la risoluzione della previsione a breve di Open-Meteo: guardare piu' spesso
 * non darebbe risposte nuove. Col telefono fermo e lo schermo spento Android
 * puo' comunque ritardare il giro: la notifica e' un aiuto, non una sirena.
 */
class PioggiaInArrivoWorker(contesto: Context, parametri: WorkerParameters) : CoroutineWorker(contesto, parametri) {

    override suspend fun doWork(): Result {
        val contesto = applicationContext
        Lingua.carica(contesto)
        val impostazioni = SettingsPrefs(contesto).settings.first()
        if (!impostazioni.notifichePioggia) return Result.success()
        if (!puoNotificare(contesto)) return Result.success()

        val posto = impostazioni.place
        val risposta = PrevisioneABreve.carica(posto).getOrElse { return Result.retry() }
        val evento = precipitazioneInArrivo(risposta.quarti, risposta.adesso) ?: return Result.success()

        val memoria = contesto.getSharedPreferences(MEMORIA, Context.MODE_PRIVATE)
        val chiave = posto.key
        val ultimaIl = memoria.getLong("$chiave.il", 0L)
        val ultimoTipo = memoria.getString("$chiave.tipo", null)
        val oraMs = System.currentTimeMillis()
        // **Un episodio, una notifica.** La stessa pioggia che arriva si
        // rivede a ogni giro per un'ora buona; se ne riparla solo dopo un'ora
        // e mezza, o se nel frattempo e' diventata grandine.
        val recente = oraMs - ultimaIl < Duration.ofMinutes(SILENZIO_MINUTI).toMillis()
        val peggiorata = evento.tipo == TipoPrecipitazione.GRANDINE && ultimoTipo != TipoPrecipitazione.GRANDINE.name
        if (recente && !peggiorata) return Result.success()

        notifica(contesto, posto.name, evento, risposta.adesso)
        memoria.edit()
            .putLong("$chiave.il", oraMs)
            .putString("$chiave.tipo", evento.tipo.name)
            .apply()
        return Result.success()
    }

    companion object {
        private const val LAVORO = "caelum-pioggia-in-arrivo"
        private const val MEMORIA = "notifiche_pioggia"
        private const val SILENZIO_MINUTI = 90L
        const val CANALE = "pioggia_in_arrivo"
        private const val ID_NOTIFICA = 4201

        fun pianifica(context: Context) {
            val richiesta = PeriodicWorkRequestBuilder<PioggiaInArrivoWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(LAVORO, ExistingPeriodicWorkPolicy.KEEP, richiesta)
        }

        fun annulla(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(LAVORO)
        }

        fun puoNotificare(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        /** Il canale, con un nome che si capisce nelle impostazioni di sistema. */
        fun creaCanale(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val canale = NotificationChannel(
                CANALE,
                tr("Pioggia e grandine in arrivo", "Rain and hail on the way"),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = tr(
                    "Avvisa quando sta per cominciare a piovere, grandinare o nevicare",
                    "Warns when rain, hail or snow is about to start",
                ) }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canale)
        }

        // Il permesso si controlla qui sotto con `puoNotificare`, che la
        // verifica di lint non sa seguire fuori dalla funzione.
        @SuppressLint("MissingPermission")
        private fun notifica(context: Context, citta: String, evento: PrecipitazioneInArrivo, adesso: LocalDateTime) {
            creaCanale(context)
            val (titolo, testo) = testiNotifica(citta, evento, adesso)
            val apri = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SALA_WIDGET, SalaRoom.PIOGGIA.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val tocco = PendingIntent.getActivity(
                context,
                ID_NOTIFICA,
                apri,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notifica = NotificationCompat.Builder(context, CANALE)
                .setSmallIcon(R.drawable.ic_notifica_pioggia)
                .setContentTitle(titolo)
                .setContentText(testo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(tocco)
                .setAutoCancel(true)
                // Dopo un'ora la notifica parla di una pioggia gia' caduta.
                .setTimeoutAfter(Duration.ofMinutes(75).toMillis())
                .build()
            if (!puoNotificare(context)) return
            runCatching { NotificationManagerCompat.from(context).notify(ID_NOTIFICA, notifica) }
        }
    }
}

/** Titolo e testo della notifica: puri, per poterli provare. */
internal fun testiNotifica(citta: String, evento: PrecipitazioneInArrivo, adesso: LocalDateTime): Pair<String, String> {
    val minuti = Duration.between(adesso, evento.inizio).toMinutes().coerceAtLeast(0)
    val quando = when {
        minuti < 10 -> tr("a momenti", "any minute now")
        else -> tr("fra circa ${((minuti + 2) / 5) * 5} minuti", "in about ${((minuti + 2) / 5) * 5} minutes")
    }
    val ora = String.format(Locale.ROOT, "%02d:%02d", evento.inizio.hour, evento.inizio.minute)
    val mm = String.format(Lingua.locale, "%.1f", evento.millimetri)
    return when (evento.tipo) {
        TipoPrecipitazione.GRANDINE ->
            tr("Grandine in arrivo a $citta", "Hail on the way in $citta") to tr(
                "Temporale con grandine $quando (verso le $ora). Metti al riparo l'auto e ciò che sta fuori.",
                "Thunderstorm with hail $quando (around $ora). Get the car and anything outside under cover.",
            )
        TipoPrecipitazione.TEMPORALE ->
            tr("Temporale in arrivo a $citta", "Thunderstorm on the way in $citta") to tr(
                "Comincia $quando (verso le $ora), con circa $mm mm nella prima mezz'ora.",
                "Starts $quando (around $ora), with about $mm mm in the first half hour.",
            )
        TipoPrecipitazione.NEVE ->
            tr("Neve in arrivo a $citta", "Snow on the way in $citta") to
                tr("Comincia a nevicare $quando (verso le $ora).", "Snow starts $quando (around $ora).")
        TipoPrecipitazione.PIOGGIA ->
            tr("Pioggia in arrivo a $citta", "Rain on the way in $citta") to tr(
                "Comincia $quando (verso le $ora), con circa $mm mm nella prima mezz'ora.",
                "Starts $quando (around $ora), with about $mm mm in the first half hour.",
            )
    }
}
