package com.caelumweather.notifiche

import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr
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
import com.caelumweather.MainActivity
import com.caelumweather.R
import com.caelumweather.data.PrecipitazioneInArrivo
import com.caelumweather.data.PrevisioneABreve
import com.caelumweather.data.TipoPrecipitazione
import com.caelumweather.data.key
import com.caelumweather.data.precipitazioneInArrivo
import com.caelumweather.prefs.SettingsPrefs
import com.caelumweather.ui.sala.SalaRoom
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
                tr("Pioggia e grandine in arrivo", "Rain and hail on the way", "Regen und Hagel im Anmarsch", "Pluie et grêle en approche", "Lluvia y granizo en camino"),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = tr("Avvisa quando sta per cominciare a piovere, grandinare o nevicare", "Tells you when rain, hail or snow is about to start", "Meldet, wenn Regen, Hagel oder Schnee gleich einsetzen", "Prévient quand la pluie, la grêle ou la neige vont commencer", "Avisa cuando está a punto de empezar a llover, granizar o nevar") }
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
        minuti < 10 -> tr("a momenti", "any moment now", "jeden Moment", "d'un instant à l'autre", "de un momento a otro")
        else -> tr("fra circa ${((minuti + 2) / 5) * 5} minuti", "in about ${((minuti + 2) / 5) * 5} minutes", "in etwa ${((minuti + 2) / 5) * 5} Minuten", "dans environ ${((minuti + 2) / 5) * 5} minutes", "en unos ${((minuti + 2) / 5) * 5} minutos")
    }
    val ora = String.format(Locale.ITALIAN, "%02d:%02d", evento.inizio.hour, evento.inizio.minute)
    // La virgola in italiano, il punto in inglese.
    val mm = String.format(Lingue.locale, "%.1f", evento.millimetri)
    return when (evento.tipo) {
        TipoPrecipitazione.GRANDINE ->
            tr("Grandine in arrivo a $citta", "Hail on the way in $citta", "Hagel im Anmarsch in $citta", "Grêle en approche à $citta", "Granizo en camino en $citta") to
                tr(
                    "Temporale con grandine $quando (verso le $ora). Metti al riparo l'auto e ciò che sta fuori.",
                    "Thunderstorm with hail $quando (around $ora). Shelter the car and anything left outside.",
                    "Gewitter mit Hagel $quando (gegen $ora). Bring das Auto und alles, was draußen steht, in Sicherheit.",
                    "Orage avec grêle $quando (vers $ora). Mettez à l'abri la voiture et ce qui reste dehors.",
                    "Tormenta con granizo $quando (hacia las $ora). Pon a cubierto el coche y lo que esté fuera.",
                )
        TipoPrecipitazione.TEMPORALE ->
            tr("Temporale in arrivo a $citta", "Thunderstorm on the way in $citta", "Gewitter im Anmarsch in $citta", "Orage en approche à $citta", "Tormenta en camino en $citta") to
                tr("Comincia $quando (verso le $ora), con circa $mm mm nella prima mezz'ora.", "Starting $quando (around $ora), with about $mm mm in the first half hour.", "Beginn $quando (gegen $ora), mit etwa $mm mm in der ersten halben Stunde.", "Début $quando (vers $ora), avec environ $mm mm dans la première demi-heure.", "Empieza $quando (hacia las $ora), con unos $mm mm en la primera media hora.")
        TipoPrecipitazione.NEVE ->
            tr("Neve in arrivo a $citta", "Snow on the way in $citta", "Schnee im Anmarsch in $citta", "Neige en approche à $citta", "Nieve en camino en $citta") to
                tr("Comincia a nevicare $quando (verso le $ora).", "Snow starting $quando (around $ora).", "Der Schneefall beginnt $quando (gegen $ora).", "Il commence à neiger $quando (vers $ora).", "Empieza a nevar $quando (hacia las $ora).")
        TipoPrecipitazione.PIOGGIA ->
            tr("Pioggia in arrivo a $citta", "Rain on the way in $citta", "Regen im Anmarsch in $citta", "Pluie en approche à $citta", "Lluvia en camino en $citta") to
                tr("Comincia $quando (verso le $ora), con circa $mm mm nella prima mezz'ora.", "Starting $quando (around $ora), with about $mm mm in the first half hour.", "Beginn $quando (gegen $ora), mit etwa $mm mm in der ersten halben Stunde.", "Début $quando (vers $ora), avec environ $mm mm dans la première demi-heure.", "Empieza $quando (hacia las $ora), con unos $mm mm en la primera media hora.")
    }
}
