package io.github.noximiliencoxen.caelum.notifiche

import io.github.noximiliencoxen.caelum.lingua.Lingue
import io.github.noximiliencoxen.caelum.lingua.tr
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
import io.github.noximiliencoxen.caelum.MainActivity
import io.github.noximiliencoxen.caelum.R
import io.github.noximiliencoxen.caelum.data.AlertLevel
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.ScortaPrevisioni
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.data.WeatherAlertsRepository
import io.github.noximiliencoxen.caelum.data.WeatherRepository
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.prefs.SalaWindUnit
import io.github.noximiliencoxen.caelum.prefs.SettingsPrefs
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Una notifica al giorno, all'ora scelta: com'e' la giornata sulla localita'
 * dell'app, in tre righe.
 *
 * **Non e' un lavoro periodico, e apposta.** Un `PeriodicWorkRequest` di
 * ventiquattro ore scivola: ogni giro parte dalla fine del precedente, e dopo
 * un mese le sette sono diventate le otto. Qui ogni giro, finito, prenota il
 * successivo all'ora esatta del giorno dopo (`APPEND_OR_REPLACE`: il nuovo
 * aspetta che questo finisca, invece di cancellarlo).
 *
 * **Spenta di norma.** Pioggia e allerte avvisano di una cosa che succede;
 * questa arriva tutti i giorni comunque, e una notifica quotidiana non chiesta
 * e' il modo piu' rapido per farle spegnere tutte.
 *
 * L'ora e' quella **del telefono**, non della localita': il mattino che conta e'
 * quello di chi si sveglia. La giornata raccontata invece e' quella del posto.
 */
class RiepilogoMattinoWorker(contesto: Context, parametri: WorkerParameters) : CoroutineWorker(contesto, parametri) {

    override suspend fun doWork(): Result {
        val contesto = applicationContext
        val impostazioni = SettingsPrefs(contesto).settings.first()
        if (!impostazioni.riepilogoMattino) return Result.success()
        val ora = orarioDi(impostazioni.oraRiepilogo)
        val adesso = LocalDateTime.now()

        if (inTempo(adesso, ora) && PioggiaInArrivoWorker.puoNotificare(contesto)) {
            val posto = impostazioni.place
            // Lo stesso modello dell'app: il riepilogo non deve dire un'altra
            // massima da quella che si legge aprendola.
            val modello = impostazioni.model
            val risposta = WeatherRepository(posto, modello).loadWithBody().getOrNull()
            if (risposta == null) {
                // Qualche tentativo, poi si lascia perdere: un riepilogo delle
                // sette che arriva a mezzogiorno non e' piu' un riepilogo, e
                // `inTempo` lo scarterebbe comunque.
                if (runAttemptCount < TENTATIVI) return Result.retry()
            } else {
                val (previsione, testo) = risposta
                runCatching { ScortaPrevisioni.conserva(ScortaPrevisioni.file(contesto.noBackupFilesDir, posto, modello), testo) }
                val oggi = previsione.days.firstOrNull()
                if (oggi != null) {
                    // Le allerte sono un di piu': se la fonte non risponde, il
                    // riepilogo parte senza, invece di non partire.
                    val allerte = WeatherAlertsRepository(posto).load().getOrNull().orEmpty()
                    val (titolo, corpo) = testiRiepilogo(
                        citta = posto.name,
                        giorno = oggi,
                        ore = previsione.hoursOf(oggi.date),
                        adesso = previsione.nowThere(),
                        unita = impostazioni.unit,
                        vento = impostazioni.windUnit,
                        allerte = allerte,
                    )
                    notifica(contesto, titolo, corpo, conAllerta = allertaDiOggi(allerte, previsione.nowThere()) != null)
                }
            }
        }

        prenota(contesto, ritardoDopoUnGiro(adesso, ora), ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val LAVORO = "caelum-riepilogo-mattino"
        const val CANALE = "riepilogo_mattino"
        private const val ID_NOTIFICA = 4301
        private const val TENTATIVI = 4

        /** Le ore fra cui si sceglie, in minuti dalla mezzanotte. */
        val ORE_POSSIBILI = listOf(6 * 60, 7 * 60, 8 * 60, 9 * 60)
        const val ORA_PREDEFINITA = 7 * 60

        /**
         * Accende o sposta il riepilogo. [sostituisci] vero quando l'ora e'
         * appena cambiata; falso all'avvio, dove `KEEP` lascia stare un turno
         * gia' prenotato.
         */
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
            val richiesta = OneTimeWorkRequestBuilder<RiepilogoMattinoWorker>()
                .setInitialDelay(ritardo.toMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(LAVORO, politica, richiesta)
        }

        fun creaCanale(context: Context) {
            // Importanza normale: arriva col suono, ma non si apre sopra lo
            // schermo come la pioggia che sta per cadere.
            val canale = NotificationChannel(
                CANALE,
                tr("Riepilogo del mattino", "Morning summary"),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = tr("La giornata sulla località dell'app, una volta al giorno all'ora scelta", "The day ahead for the app's place, once a day at the time you choose") }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canale)
        }

        // Il permesso si controlla con `puoNotificare`, che lint non sa seguire.
        @SuppressLint("MissingPermission")
        private fun notifica(context: Context, titolo: String, testo: String, conAllerta: Boolean) {
            creaCanale(context)
            // Con un'allerta il tocco apre il bollettino, che e' la cosa da
            // leggere; senza, la prima sala.
            val apri = Intent(context, MainActivity::class.java)
                .apply {
                    if (conAllerta) putExtra(MainActivity.EXTRA_BOLLETTINO, true)
                    else putExtra(MainActivity.EXTRA_SALA_WIDGET, SalaRoom.OGGI.name)
                }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val tocco = PendingIntent.getActivity(
                context,
                ID_NOTIFICA,
                apri,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notifica = NotificationCompat.Builder(context, CANALE)
                .setSmallIcon(R.drawable.ic_notifica_riepilogo)
                .setContentTitle(titolo)
                .setContentText(testo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setContentIntent(tocco)
                .setAutoCancel(true)
                // A sera il riepilogo del mattino e' una cosa vecchia.
                .setTimeoutAfter(Duration.ofHours(12).toMillis())
                .build()
            if (!PioggiaInArrivoWorker.puoNotificare(context)) return
            runCatching { NotificationManagerCompat.from(context).notify(ID_NOTIFICA, notifica) }
        }
    }
}

internal fun orarioDi(minuti: Int): LocalTime =
    LocalTime.of((minuti / 60).coerceIn(0, 23), (minuti % 60).coerceIn(0, 59))

/** Quanto manca alla prossima volta che l'orologio segna [ora], dopo [adesso]. */
internal fun ritardoFinoA(adesso: LocalDateTime, ora: LocalTime): Duration {
    val oggi = adesso.toLocalDate().atTime(ora)
    val prossimo = if (oggi.isAfter(adesso)) oggi else oggi.plusDays(1)
    return Duration.between(adesso, prossimo)
}

/**
 * Il ritardo del turno successivo, prenotato alla fine di un giro.
 *
 * L'ora si cerca da mezz'ora piu' in la': un giro partito un attimo prima
 * dell'ora non deve prenotarne un secondo per oggi stesso. **Ma il ritardo si
 * conta da adesso**, non da quella mezz'ora dopo: contato da li', provato sul
 * telefono, il turno usciva trentun minuti prima dell'ora, fuori dalla finestra
 * di [inTempo], e il riepilogo saltava tutti i giorni.
 */
internal fun ritardoDopoUnGiro(adesso: LocalDateTime, ora: LocalTime): Duration {
    val da = adesso.plusMinutes(31)
    return Duration.between(adesso, da.plus(ritardoFinoA(da, ora)))
}

/**
 * Vero da mezz'ora prima a due ore dopo l'ora scelta. Fuori, il giro e' partito
 * troppo tardi - telefono spento, rete assente, Android che rimanda - e un
 * "buongiorno" a mezzogiorno si legge come un guasto.
 */
internal fun inTempo(adesso: LocalDateTime, ora: LocalTime): Boolean {
    val bersaglio = adesso.toLocalDate().atTime(ora)
    return !adesso.isBefore(bersaglio.minusMinutes(30)) && adesso.isBefore(bersaglio.plusMinutes(120))
}

/** L'allerta ufficiale piu' grave in corso oggi, se ce n'e' una. */
internal fun allertaDiOggi(allerte: List<WeatherAlert>, adesso: LocalDateTime): WeatherAlert? {
    val fineGiornata = adesso.toLocalDate().plusDays(1).atStartOfDay()
    return allerte
        .filter { it.official }
        .filter { a -> a.expires?.isAfter(adesso) ?: true }
        .filter { a -> a.onset?.isBefore(fineGiornata) ?: true }
        .maxByOrNull { it.level.weight }
}

/**
 * Titolo e testo del riepilogo: puri, per poterli provare. Li usa anche il
 * tasto "condividi" di Sala I: la stessa giornata, detta con le stesse parole.
 *
 * Il titolo dice la giornata in una riga - minima, massima, che tempo - e il
 * testo solo cio' che cambia cosa fai: se e quando piove, se il sole scotta, se
 * tira vento, se c'e' un'allerta. **Le righe senza niente da dire non ci sono**:
 * "UV basso" ogni mattina d'inverno insegna a non leggere il resto.
 */
internal fun testiRiepilogo(
    citta: String,
    giorno: DayForecast,
    ore: List<HourForecast>,
    adesso: LocalDateTime,
    unita: TempUnit,
    vento: SalaWindUnit,
    allerte: List<WeatherAlert>,
    /** "oggi", o il giorno per esteso quando lo si condivide da Sala I. */
    giornoDetto: String = tr("oggi", "today"),
): Pair<String, String> {
    fun gradi(c: Double?) = c?.let { "${unita.from(it).roundToInt()}°" } ?: "--"
    val tempo = Wmo.condition(giorno.weatherCode).lowercase(Lingue.locale)
    val titolo = "$citta, $giornoDetto: ${gradi(giorno.tempMin)} – ${gradi(giorno.tempMax)}, $tempo"

    val righe = mutableListOf<String>()

    val mm = giorno.precipitationSum ?: 0.0
    val neve = (giorno.snowfallSum ?: 0.0) > 0.0 && (giorno.rainSum ?: 0.0) < mm / 2
    if (mm >= 0.2) {
        val daOra = adesso.truncatedTo(ChronoUnit.HOURS)
        val inizio = ore.firstOrNull { !it.time.isBefore(daOra) && (it.precipitation ?: 0.0) >= 0.1 }?.time
        val quanta = String.format(Lingue.locale, "%.1f", mm)
        righe += when {
            inizio != null && neve -> tr("Neve dalle ${inizio.hour} circa.", "Snow from about ${inizio.hour}:00.")
            neve -> tr("Neve in giornata.", "Snow during the day.")
            inizio != null -> tr("Pioggia dalle ${inizio.hour} circa, $quanta mm in giornata.", "Rain from about ${inizio.hour}:00, $quanta mm over the day.")
            else -> tr("$quanta mm di pioggia in giornata.", "$quanta mm of rain over the day.")
        }
    } else {
        righe += tr("Niente pioggia.", "No rain.")
    }

    val uv = giorno.uvMax ?: 0.0
    if (uv >= 3.0) {
        val picco = ore.maxByOrNull { it.uvIndex ?: 0.0 }?.time?.hour
        val quando = picco?.let { tr(" verso le $it", " around $it:00") } ?: ""
        val valore = uv.roundToInt()
        righe += if (uv >= 6.0) {
            tr("UV alto, $valore$quando: crema e cappello.", "High UV, $valore$quando: sunscreen and a hat.")
        } else {
            tr("UV moderato, $valore$quando.", "Moderate UV, $valore$quando.")
        }
    }

    // Le raffiche, non il vento medio: e' la raffica che porta via l'ombrello.
    // La soglia e' in km/h qualunque unita' si mostri.
    val raffica = giorno.gustMax
    if (raffica != null && raffica * 3.6 >= 50.0) {
        righe += tr(
            "Raffiche fino a ${vento.from(raffica).roundToInt()} ${vento.label}.",
            "Gusts up to ${vento.from(raffica).roundToInt()} ${vento.label}.",
        )
    }

    allertaDiOggi(allerte, adesso)?.let { a ->
        val colore = a.level.breve.lowercase(Lingue.locale)
        val cosa = a.kind.label.lowercase(Lingue.locale)
        righe += if (a.level.weight >= AlertLevel.ARANCIONE.weight) {
            tr("Allerta $colore per $cosa: tocca per il bollettino.", "${colore.replaceFirstChar { it.uppercase() }} warning for $cosa: tap for the bulletin.")
        } else {
            tr("Allerta $colore per $cosa.", "${colore.replaceFirstChar { it.uppercase() }} warning for $cosa.")
        }
    }

    return titolo to righe.joinToString(" ")
}
