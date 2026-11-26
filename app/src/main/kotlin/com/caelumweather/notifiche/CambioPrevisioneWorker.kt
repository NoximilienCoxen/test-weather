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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.caelumweather.MainActivity
import com.caelumweather.R
import com.caelumweather.data.DayForecast
import com.caelumweather.data.ScortaPrevisioni
import com.caelumweather.data.WeatherRepository
import com.caelumweather.data.key
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr
import com.caelumweather.prefs.SettingsPrefs
import com.caelumweather.ui.sala.SalaRoom
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **"La previsione e' cambiata"**: ogni tre ore si rilegge domani e
 * dopodomani, e se rispetto a quanto si sapeva e' cambiato qualcosa che conta
 * lo si dice.
 *
 * Conta poco: la pioggia che compare o sparisce, o la massima che si sposta di
 * quattro gradi. Il resto - un grado, due decimi di millimetro - e' il respiro
 * normale di un modello fra una corsa e l'altra, e una notifica per quello
 * insegnerebbe a spegnerle.
 *
 * Il confronto e' con **l'ultima previsione di cui si e' parlato** (o la
 * prima vista), non con quella di tre ore prima: cosi' due piccoli scivolamenti
 * di due gradi fanno quattro, che e' la verita' per chi aveva letto la prima.
 */
class CambioPrevisioneWorker(contesto: Context, parametri: WorkerParameters) : CoroutineWorker(contesto, parametri) {

    override suspend fun doWork(): Result {
        val contesto = applicationContext
        val impostazioni = SettingsPrefs(contesto).settings.first()
        if (!impostazioni.avvisoCambioPrevisione) return Result.success()
        if (!PioggiaInArrivoWorker.puoNotificare(contesto)) return Result.success()

        val posto = impostazioni.place
        val modello = impostazioni.model
        val (previsione, testo) = WeatherRepository(posto, modello).loadWithBody().getOrElse { return Result.retry() }
        runCatching { ScortaPrevisioni.conserva(ScortaPrevisioni.file(contesto.noBackupFilesDir, posto, modello), testo) }

        val oggi = previsione.nowThere().toLocalDate()
        val memoria = contesto.getSharedPreferences(MEMORIA, Context.MODE_PRIVATE)
        val modifica = memoria.edit()
        val notizie = mutableListOf<CambioDelGiorno>()
        for (giorno in previsione.days.filter { it.date == oggi.plusDays(1) || it.date == oggi.plusDays(2) }) {
            val adesso = Istantanea.di(giorno) ?: continue
            val chiave = "${posto.key}.${giorno.date}"
            val prima = memoria.getString(chiave, null)?.let(Istantanea::leggi)
            val cambio = prima?.let { confronta(giorno.date, it, adesso) }
            if (prima == null || cambio != null) modifica.putString(chiave, adesso.scrivi())
            if (cambio != null) notizie += cambio
        }
        // Le istantanee di giorni passati non servono piu'.
        memoria.all.keys.filter { k -> k.substringAfterLast('.').let { d -> runCatching { LocalDate.parse(d).isBefore(oggi) }.getOrDefault(false) } }
            .forEach(modifica::remove)
        modifica.apply()

        testiCambio(posto.name, notizie, oggi, impostazioni.unit)?.let { (titolo, corpo) -> notifica(contesto, titolo, corpo) }
        return Result.success()
    }

    companion object {
        private const val LAVORO = "caelum-cambio-previsione"
        private const val MEMORIA = "cambio_previsione"
        const val CANALE = "cambio_previsione"
        private const val ID_NOTIFICA = 4501

        fun pianifica(context: Context) {
            val richiesta = PeriodicWorkRequestBuilder<CambioPrevisioneWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(LAVORO, ExistingPeriodicWorkPolicy.KEEP, richiesta)
        }

        fun annulla(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(LAVORO)
        }

        fun creaCanale(context: Context) {
            val canale = NotificationChannel(
                CANALE,
                tr("Previsione cambiata", "Forecast changed", "Vorhersage geändert", "Prévision modifiée", "Previsión cambiada"),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = tr(
                    "Quando per domani o dopodomani compare o sparisce la pioggia, o la massima cambia di 4 gradi",
                    "When rain appears or disappears for tomorrow or the day after, or the high changes by 4 degrees",
                    "Wenn für morgen oder übermorgen Regen auftaucht oder verschwindet oder sich die Höchstwerte um 4 Grad ändern",
                    "Quand la pluie apparaît ou disparaît pour demain ou après-demain, ou que la max change de 4 degrés",
                    "Cuando para mañana o pasado aparece o desaparece la lluvia, o la máxima cambia 4 grados",
                )
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canale)
        }

        @SuppressLint("MissingPermission")
        private fun notifica(context: Context, titolo: String, testo: String) {
            creaCanale(context)
            val apri = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SALA_WIDGET, SalaRoom.OGGI.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val tocco = PendingIntent.getActivity(context, ID_NOTIFICA, apri, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notifica = NotificationCompat.Builder(context, CANALE)
                .setSmallIcon(R.drawable.ic_notifica_riepilogo)
                .setContentTitle(titolo)
                .setContentText(testo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setContentIntent(tocco)
                .setAutoCancel(true)
                // Un cambio di previsione e' vecchio alla corsa successiva.
                .setTimeoutAfter(Duration.ofHours(6).toMillis())
                .build()
            if (!PioggiaInArrivoWorker.puoNotificare(context)) return
            runCatching { NotificationManagerCompat.from(context).notify(ID_NOTIFICA, notifica) }
        }
    }
}

/** Quel che conta di un giorno, per confrontarlo dopo. */
internal data class Istantanea(val massima: Double, val bagnato: Boolean, val mm: Double) {
    fun scrivi() = "$massima;$bagnato;$mm"

    companion object {
        /** Bagnato: almeno un millimetro, con almeno il 50% (o senza probabilita'). */
        fun di(g: DayForecast): Istantanea? {
            val massima = g.tempMax ?: return null
            val mm = g.precipitationSum ?: 0.0
            return Istantanea(massima, mm >= 1.0 && (g.precipProbability ?: 100) >= 50, mm)
        }

        fun leggi(s: String): Istantanea? = runCatching {
            val (a, b, c) = s.split(';')
            Istantanea(a.toDouble(), b.toBooleanStrict(), c.toDouble())
        }.getOrNull()
    }
}

internal data class CambioDelGiorno(
    val data: LocalDate,
    val pioggiaArriva: Boolean = false,
    val pioggiaSparisce: Boolean = false,
    /** Gradi in piu' (o in meno) sulla massima, se lo spostamento conta. */
    val gradi: Int? = null,
    val massimaOra: Double,
    val mmOra: Double,
)

internal fun confronta(data: LocalDate, prima: Istantanea, adesso: Istantanea): CambioDelGiorno? {
    val arriva = !prima.bagnato && adesso.bagnato
    val sparisce = prima.bagnato && !adesso.bagnato
    val delta = adesso.massima - prima.massima
    val gradi = delta.roundToInt().takeIf { abs(delta) >= 4.0 }
    if (!arriva && !sparisce && gradi == null) return null
    return CambioDelGiorno(data, arriva, sparisce, gradi, adesso.massima, adesso.mm)
}

/** Titolo e testo, o nullo se non c'e' niente da dire. */
internal fun testiCambio(
    citta: String,
    cambi: List<CambioDelGiorno>,
    oggi: LocalDate,
    unita: com.caelumweather.prefs.TempUnit = com.caelumweather.prefs.TempUnit.CELSIUS,
): Pair<String, String>? {
    if (cambi.isEmpty()) return null
    fun nome(d: LocalDate) = if (d == oggi.plusDays(1)) {
        tr("domani", "tomorrow", "morgen", "demain", "mañana")
    } else {
        d.dayOfWeek.getDisplayName(TextStyle.FULL, Lingue.locale)
    }
    val frasi = cambi.map { c ->
        val chi = nome(c.data).replaceFirstChar { it.uppercase() }
        val parti = buildList {
            if (c.pioggiaArriva) add(tr("ora è prevista pioggia", "rain is now expected", "jetzt wird Regen erwartet", "de la pluie est maintenant prévue", "ahora se espera lluvia") + " (" + String.format(Lingue.locale, "%.0f mm", c.mmOra.coerceAtLeast(1.0)) + ")")
            if (c.pioggiaSparisce) add(tr("la pioggia non è più prevista", "rain is no longer expected", "Regen wird nicht mehr erwartet", "la pluie n'est plus prévue", "ya no se espera lluvia"))
            c.gradi?.let { g ->
                // Lo scarto nella scala di chi legge: in Fahrenheit 4 gradi
                // Celsius sono 7.
                val scarto = (unita.from(g.toDouble()) - unita.from(0.0)).roundToInt()
                val segno = if (scarto > 0) "+$scarto" else "$scarto"
                val ora = unita.from(c.massimaOra).roundToInt()
                add(tr("massima $segno°, ora $ora°", "high $segno°, now $ora°", "Höchstwert $segno°, jetzt $ora°", "max $segno°, maintenant $ora°", "máxima $segno°, ahora $ora°"))
            }
        }
        "$chi: " + parti.joinToString(", ") + "."
    }
    val titolo = tr("La previsione è cambiata a $citta", "Forecast changed in $citta", "Vorhersage für $citta geändert", "La prévision a changé à $citta", "La previsión ha cambiado en $citta")
    return titolo to frasi.joinToString(" ")
}
