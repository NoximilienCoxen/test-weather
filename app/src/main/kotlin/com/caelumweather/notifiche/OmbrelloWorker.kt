package com.caelumweather.notifiche

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.caelumweather.MainActivity
import com.caelumweather.R
import com.caelumweather.data.HourForecast
import com.caelumweather.data.ScortaPrevisioni
import com.caelumweather.data.WeatherRepository
import com.caelumweather.data.Wmo
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr
import com.caelumweather.prefs.SettingsPrefs
import com.caelumweather.ui.sala.SalaRoom
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * **"Porta l'ombrello"**: una notifica al mattino, all'ora scelta, **solo se
 * in giornata piove** sulla localita' dell'app.
 *
 * Il riepilogo del mattino (`RiepilogoMattinoWorker`) arriva ogni giorno e
 * racconta tutto; questa tace nei giorni asciutti e quando parla dice una
 * cosa sola, quella che serve uscendo di casa: da che ora a che ora, e quanta.
 * Per questo e' una voce a parte, e la si puo' tenere accesa anche a chi il
 * riepilogo quotidiano darebbe fastidio.
 *
 * Stesso meccanismo del riepilogo: non un lavoro periodico (scivolerebbe),
 * ma un giro che finito prenota il successivo all'ora esatta del giorno dopo.
 */
class OmbrelloWorker(contesto: Context, parametri: WorkerParameters) : CoroutineWorker(contesto, parametri) {

    override suspend fun doWork(): Result {
        val contesto = applicationContext
        val impostazioni = SettingsPrefs(contesto).settings.first()
        if (!impostazioni.avvisoOmbrello) return Result.success()
        val ora = orarioDi(impostazioni.oraOmbrello)
        val adesso = LocalDateTime.now()

        if (inTempo(adesso, ora) && PioggiaInArrivoWorker.puoNotificare(contesto)) {
            val posto = impostazioni.place
            val modello = impostazioni.model
            val risposta = WeatherRepository(posto, modello).loadWithBody().getOrNull()
            if (risposta == null) {
                if (runAttemptCount < TENTATIVI) return Result.retry()
            } else {
                val (previsione, testo) = risposta
                runCatching { ScortaPrevisioni.conserva(ScortaPrevisioni.file(contesto.noBackupFilesDir, posto, modello), testo) }
                val qui = previsione.nowThere()
                testiOmbrello(posto.name, previsione.hoursOf(qui.toLocalDate()), qui)?.let { (titolo, corpo) ->
                    notifica(contesto, titolo, corpo)
                }
            }
        }

        prenota(contesto, ritardoDopoUnGiro(adesso, ora), ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val LAVORO = "caelum-ombrello"
        const val CANALE = "ombrello"
        private const val ID_NOTIFICA = 4401
        private const val TENTATIVI = 4

        /** Le ore fra cui si sceglie: le stesse del riepilogo. */
        val ORE_POSSIBILI = RiepilogoMattinoWorker.ORE_POSSIBILI
        const val ORA_PREDEFINITA = 7 * 60

        fun pianifica(context: Context, minuti: Int, sostituisci: Boolean) {
            prenota(
                context,
                ritardoFinoA(LocalDateTime.now(), orarioDi(minuti)),
                if (sostituisci) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            )
        }

        fun annulla(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(LAVORO)
        }

        private fun prenota(context: Context, ritardo: Duration, politica: ExistingWorkPolicy) {
            val richiesta = OneTimeWorkRequestBuilder<OmbrelloWorker>()
                .setInitialDelay(ritardo.toMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(LAVORO, politica, richiesta)
        }

        fun creaCanale(context: Context) {
            val canale = NotificationChannel(
                CANALE,
                tr("Porta l'ombrello", "Take an umbrella", "Schirm mitnehmen", "Prends un parapluie", "Lleva paraguas"),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = tr(
                    "Al mattino, solo nei giorni in cui piove: da che ora e quanta",
                    "In the morning, only on days with rain: from when and how much",
                    "Morgens, nur an Regentagen: ab wann und wie viel",
                    "Le matin, seulement les jours de pluie : à partir de quand et combien",
                    "Por la mañana, solo los días de lluvia: desde cuándo y cuánta",
                )
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canale)
        }

        @SuppressLint("MissingPermission")
        private fun notifica(context: Context, titolo: String, testo: String) {
            creaCanale(context)
            val apri = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SALA_WIDGET, SalaRoom.PIOGGIA.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val tocco = PendingIntent.getActivity(context, ID_NOTIFICA, apri, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notifica = NotificationCompat.Builder(context, CANALE)
                .setSmallIcon(R.drawable.ic_notifica_pioggia)
                .setContentTitle(titolo)
                .setContentText(testo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setContentIntent(tocco)
                .setAutoCancel(true)
                // Finita la giornata, l'ombrello non serve piu'.
                .setTimeoutAfter(Duration.ofHours(14).toMillis())
                .build()
            if (!PioggiaInArrivoWorker.puoNotificare(context)) return
            runCatching { NotificationManagerCompat.from(context).notify(ID_NOTIFICA, notifica) }
        }
    }
}

/**
 * Il titolo e il testo della notifica, o nullo se oggi l'ombrello non serve.
 *
 * Si guarda da adesso alle 22: la pioggia della notte non riguarda chi esce
 * al mattino. Un'ora conta se porta almeno tre decimi di millimetro con una
 * probabilita' di almeno il 40 per cento: sotto, e' umido che i modelli
 * spargono dappertutto, e una notifica che arrivasse per quello insegnerebbe a
 * ignorarla. La fascia va dalla prima all'ultima ora bagnata.
 */
internal fun testiOmbrello(citta: String, ore: List<HourForecast>, adesso: LocalDateTime): Pair<String, String>? {
    val da = adesso.truncatedTo(ChronoUnit.HOURS)
    val giornata = ore.filter { !it.time.isBefore(da) && it.time.hour <= 22 }
    fun bagnata(o: HourForecast) = (o.precipitation ?: 0.0) >= 0.3 && (o.precipProbability ?: 100) >= 40
    val bagnate = giornata.filter(::bagnata)
    if (bagnate.isEmpty()) return null
    val mm = giornata.sumOf { it.precipitation ?: 0.0 }
    val neve = bagnate.count { o -> o.weatherCode?.let { Wmo.family(it) == Wmo.Family.NEVE } == true } * 2 > bagnate.size
    val inizio = bagnate.first().time.hour
    val fine = bagnate.last().time.hour + 1
    val quanta = String.format(Lingue.locale, "%.0f", mm.coerceAtLeast(1.0))
    val giaOra = bagnate.first().time == da

    val titolo = if (neve) {
        tr("Neve oggi a $citta", "Snow today in $citta", "Schnee heute in $citta", "Neige aujourd'hui à $citta", "Nieve hoy en $citta")
    } else {
        tr("Porta l'ombrello", "Take an umbrella", "Nimm einen Schirm mit", "Prends un parapluie", "Lleva paraguas")
    }
    val cosa = if (neve) tr("Neve", "Snow", "Schnee", "Neige", "Nieve") else tr("Pioggia", "Rain", "Regen", "Pluie", "Lluvia")
    val corpo = when {
        giaOra -> tr(
            "$cosa già in corso, fino alle $fine circa a $citta: $quanta mm in giornata.",
            "$cosa already falling, until about $fine:00 in $citta: $quanta mm over the day.",
            "$cosa schon jetzt, bis etwa $fine Uhr in $citta: $quanta mm im Tagesverlauf.",
            "$cosa déjà en cours, jusque vers $fine h à $citta : $quanta mm dans la journée.",
            "$cosa ya en curso, hasta las $fine aprox. en $citta: $quanta mm a lo largo del día.",
        )
        else -> tr(
            "$cosa dalle $inizio alle $fine circa a $citta: $quanta mm in giornata.",
            "$cosa from about $inizio:00 to $fine:00 in $citta: $quanta mm over the day.",
            "$cosa von etwa $inizio bis $fine Uhr in $citta: $quanta mm im Tagesverlauf.",
            "$cosa d'environ $inizio h à $fine h à $citta : $quanta mm dans la journée.",
            "$cosa de las $inizio a las $fine aprox. en $citta: $quanta mm a lo largo del día.",
        )
    }
    return titolo to corpo
}
