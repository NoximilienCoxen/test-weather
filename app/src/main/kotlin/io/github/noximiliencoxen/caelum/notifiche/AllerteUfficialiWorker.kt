package io.github.noximiliencoxen.caelum.notifiche

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.noximiliencoxen.caelum.MainActivity
import io.github.noximiliencoxen.caelum.R
import io.github.noximiliencoxen.caelum.data.AlertLevel
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.data.WeatherAlertsRepository
import io.github.noximiliencoxen.caelum.prefs.SettingsPrefs
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Ogni ora guarda i bollettini di MeteoAlarm sulla citta' dell'app, e se c'e'
 * un'allerta **ufficiale arancione o rossa** che non ha ancora detto, la dice.
 *
 * **Solo arancione e rossa, e non e' prudenza a meta'.** In Italia una gialla
 * da qualche parte c'e' quasi ogni giorno, e MeteoAlarm la attribuisce a una
 * regione intera: una notifica al giorno per una cosa che di solito non chiede
 * niente insegna a ignorarle tutte, anche quella rossa. La gialla resta dove
 * era, nella pastiglia e nel bollettino.
 *
 * **Solo le ufficiali.** Gli avvisi calcolati dalle soglie non passano di qui:
 * una notifica e' gia' una voce autorevole, e "soglia superata" detto dal
 * telefono in tasca si leggerebbe come la Protezione Civile (vedi
 * `WeatherAlert.badgeLabel`). Per l'acqua che arriva c'e' gia'
 * [PioggiaInArrivoWorker].
 *
 * L'ora basta: le allerte si emettono il giorno prima, e il feed non si
 * aggiorna al minuto.
 */
class AllerteUfficialiWorker(contesto: Context, parametri: WorkerParameters) : CoroutineWorker(contesto, parametri) {

    override suspend fun doWork(): Result {
        val contesto = applicationContext
        val impostazioni = SettingsPrefs(contesto).settings.first()
        if (!impostazioni.notificheAllerte) return Result.success()
        if (!PioggiaInArrivoWorker.puoNotificare(contesto)) return Result.success()

        val posto = impostazioni.place
        val allerte = WeatherAlertsRepository(posto).load().getOrElse { errore ->
            // Fuori da cio' che MeteoAlarm copre non c'e' niente da chiedere,
            // e riprovare non cambierebbe la risposta.
            return if (errore is WeatherAlertsRepository.OutOfCoverage) Result.success() else Result.retry()
        }

        val memoria = contesto.getSharedPreferences(MEMORIA, Context.MODE_PRIVATE)
        val gia = memoria.getStringSet(CHIAVE_DETTE, emptySet()).orEmpty()
        val adesso = LocalDateTime.now()
        val daDire = allerteDaNotificare(allerte, gia, adesso)
        daDire.forEach { notifica(contesto, posto.name, it, adesso) }
        memoria.edit().putStringSet(CHIAVE_DETTE, memoriaAggiornata(allerte, gia, adesso)).apply()
        return Result.success()
    }

    companion object {
        private const val LAVORO = "caelum-allerte-ufficiali"
        private const val MEMORIA = "notifiche_allerte"
        private const val CHIAVE_DETTE = "dette"
        const val CANALE = "allerte_ufficiali"

        fun pianifica(context: Context) {
            val richiesta = PeriodicWorkRequestBuilder<AllerteUfficialiWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(LAVORO, ExistingPeriodicWorkPolicy.KEEP, richiesta)
        }

        fun annulla(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(LAVORO)
        }

        fun creaCanale(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val canale = NotificationChannel(
                CANALE,
                "Allerte ufficiali",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Allerte arancioni e rosse diramate dagli enti, via MeteoAlarm" }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canale)
        }

        // Il permesso si controlla con `puoNotificare`, che lint non sa seguire.
        @SuppressLint("MissingPermission")
        private fun notifica(context: Context, citta: String, allerta: WeatherAlert, adesso: LocalDateTime) {
            creaCanale(context)
            val (titolo, testo) = testiAllerta(citta, allerta, adesso)
            val idNotifica = ID_BASE + (allerta.id.hashCode() and 0x3FF)
            val apri = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_BOLLETTINO, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val tocco = PendingIntent.getActivity(
                context,
                idNotifica,
                apri,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val costruttore = NotificationCompat.Builder(context, CANALE)
                .setSmallIcon(R.drawable.ic_notifica_allerta)
                .setContentTitle(titolo)
                .setContentText(testo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(tocco)
                .setAutoCancel(true)
            // Finita l'allerta, la notifica parlerebbe di una cosa passata.
            allerta.expires?.let { fine ->
                val ms = java.time.Duration.between(adesso, fine).toMillis()
                if (ms > 0) costruttore.setTimeoutAfter(ms)
            }
            if (!PioggiaInArrivoWorker.puoNotificare(context)) return
            runCatching { NotificationManagerCompat.from(context).notify(idNotifica, costruttore.build()) }
        }

        private const val ID_BASE = 4300
    }
}

/**
 * Le allerte che meritano una notifica e non l'hanno ancora avuta.
 *
 * **Si ricorda il livello, non solo l'allerta**: la gialla che diventa
 * arancione ha lo stesso identificativo e non e' la stessa notizia - la regola
 * scritta in fondo a `WeatherAlert.kt`. Una gia' detta allo stesso livello, o
 * a uno piu' alto, tace. Una gia' finita tace.
 */
internal fun allerteDaNotificare(
    allerte: List<WeatherAlert>,
    gia: Set<String>,
    adesso: LocalDateTime,
): List<WeatherAlert> = allerte
    .filter { it.official && it.level.weight >= AlertLevel.ARANCIONE.weight }
    .filter { a -> a.expires?.isAfter(adesso) ?: true }
    .filter { a -> gia.none { it.idDetto() == a.id && (it.pesoDetto() ?: 0) >= a.level.weight } }
    .sortedByDescending { it.level.weight }

/**
 * La memoria dopo il giro: cio' che c'era, piu' cio' che si e' appena detto,
 * meno cio' che e' finito da piu' di un giorno.
 *
 * Non si tiene solo cio' che il feed dice adesso: una risposta vuota per un
 * guasto a monte cancellerebbe la memoria, e l'allerta ricomparsa un'ora dopo
 * verrebbe notificata di nuovo.
 */
internal fun memoriaAggiornata(
    allerte: List<WeatherAlert>,
    gia: Set<String>,
    adesso: LocalDateTime,
): Set<String> {
    val scadenza = adesso.toLocalDate().minusDays(1)
    val tenute = gia.filter { voce -> voce.fineDetta()?.isBefore(scadenza) != true }
    val nuove = allerteDaNotificare(allerte, gia, adesso).map { it.voceDetta() }
    // Una voce per allerta: quella nuova, a livello piu' alto, prende il posto.
    val idNuove = nuove.map { it.idDetto() }.toSet()
    return (tenute.filter { it.idDetto() !in idNuove } + nuove).toSet()
}

/** Titolo e testo della notifica: puri, per poterli provare. */
internal fun testiAllerta(citta: String, allerta: WeatherAlert, adesso: LocalDateTime): Pair<String, String> {
    val colore = allerta.level.label.removePrefix("ALLERTA ").lowercase(Locale.ITALIAN)
    val titolo = "Allerta $colore a $citta: ${allerta.kind.label.lowercase(Locale.ITALIAN)}"
    val quando = finestraBreve(allerta, adesso)
    val zona = allerta.areaDesc?.takeIf { it.isNotBlank() }?.let { " Zona: $it." } ?: ""
    val testo = "${quando}Diramata dagli enti, via MeteoAlarm.$zona Tocca per leggere il bollettino."
    return titolo to testo
}

private fun finestraBreve(allerta: WeatherAlert, adesso: LocalDateTime): String {
    val inizio = allerta.onset
    val fine = allerta.expires
    fun giornoEOra(t: LocalDateTime): String {
        val ora = String.format(Locale.ITALIAN, "%02d:%02d", t.hour, t.minute)
        val oggi = adesso.toLocalDate()
        return when (t.toLocalDate()) {
            oggi -> "alle $ora di oggi"
            oggi.plusDays(1) -> "alle $ora di domani"
            else -> "alle $ora del ${t.dayOfMonth}/${t.monthValue}"
        }
    }
    return when {
        inizio != null && inizio.isAfter(adesso) && fine != null ->
            "Dalle ${giornoEOra(inizio).removePrefix("alle ")} ${giornoEOra(fine)}. "
        fine != null -> "In corso fino ${giornoEOra(fine)}. "
        else -> ""
    }
}

// Una voce della memoria: "id|peso|data di fine". La data serve solo a
// buttarla via, quindi basta il giorno.
private fun WeatherAlert.voceDetta(): String =
    "$id|${level.weight}|${expires?.toLocalDate() ?: ""}"

private fun String.idDetto(): String = substringBeforeLast('|').substringBeforeLast('|')

private fun String.pesoDetto(): Int? = substringBeforeLast('|').substringAfterLast('|').toIntOrNull()

private fun String.fineDetta(): LocalDate? =
    substringAfterLast('|').takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
