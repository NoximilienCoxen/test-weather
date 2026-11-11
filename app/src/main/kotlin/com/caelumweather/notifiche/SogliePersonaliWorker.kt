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
import com.caelumweather.data.HourForecast
import com.caelumweather.data.ScortaPrevisioni
import com.caelumweather.data.WeatherRepository
import com.caelumweather.data.key
import com.caelumweather.lingua.tr
import com.caelumweather.prefs.SalaWindUnit
import com.caelumweather.prefs.SettingsPrefs
import com.caelumweather.prefs.SogliePersonali
import com.caelumweather.prefs.TempUnit
import com.caelumweather.prefs.TipoSoglia
import com.caelumweather.ui.sala.SalaRoom
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Gli avvisi personali: ogni due ore guarda le prossime sedici sulla localita'
 * dell'app, e se la previsione passa una soglia **scelta da chi usa l'app** -
 * gelo, caldo, raffiche - lo dice con una notifica.
 *
 * **Non e' un'allerta, e ogni notifica lo scrive.** `AllerteUfficialiWorker`
 * spiega perche' gli avvisi calcolati dall'app non passano dalle notifiche: una
 * notifica e' una voce autorevole, e "soglia superata" detto dal telefono si
 * leggerebbe come un ente. Qui la soglia non l'ha decisa l'app: l'ha scritta la
 * persona, e la notifica gliela ripete ("la tua soglia: 0°") accanto alla parola
 * "previsione". E' un promemoria su un numero suo, non un giudizio sul rischio.
 *
 * **Sedici ore e non ventiquattro.** Con un giorno intero, il mattino dopo una
 * gelata la finestra vedrebbe gia' quella della notte seguente, e l'avviso
 * arriverebbe ventitre' ore prima. Con sedici il gelo delle cinque si viene a
 * sapere dal primo pomeriggio, il caldo delle quindici dal mattino.
 *
 * **Di notte tace.** Fra le 22 e le 7 del telefono il giro guarda e non dice
 * niente: una raffica prevista alle quattro non vale una sveglia alle due. Cio'
 * che e' ancora davanti si dice al primo giro del mattino.
 */
class SogliePersonaliWorker(contesto: Context, parametri: WorkerParameters) : CoroutineWorker(contesto, parametri) {

    override suspend fun doWork(): Result {
        val contesto = applicationContext
        val impostazioni = SettingsPrefs(contesto).settings.first()
        val soglie = impostazioni.soglie
        if (!soglie.qualcunaAccesa) return Result.success()
        if (!PioggiaInArrivoWorker.puoNotificare(contesto)) return Result.success()
        if (!oraDiParlare(LocalTime.now())) return Result.success()

        val posto = impostazioni.place
        val modello = impostazioni.model
        val (previsione, testo) = WeatherRepository(posto, modello).loadWithBody().getOrElse { return Result.retry() }
        runCatching { ScortaPrevisioni.conserva(ScortaPrevisioni.file(contesto.noBackupFilesDir, posto, modello), testo) }

        val adesso = previsione.nowThere()
        val superate = soglieSuperate(previsione.allHours, adesso, soglie)
        if (superate.isEmpty()) return Result.success()

        val memoria = contesto.getSharedPreferences(MEMORIA, Context.MODE_PRIVATE)
        val scritture = memoria.edit()
        superate.forEach { evento ->
            // **Un episodio, una notifica**: la stessa gelata si rivede a ogni
            // giro finche' non e' passata. La chiave porta il giorno in cui
            // cade, quindi quella della notte dopo e' un episodio nuovo.
            val chiave = "${posto.key}.${evento.tipo.name}"
            val giorno = evento.quando.toLocalDate().toString()
            if (memoria.getString(chiave, null) == giorno) return@forEach
            notifica(contesto, posto.name, evento, adesso, impostazioni.unit, impostazioni.windUnit)
            scritture.putString(chiave, giorno)
        }
        scritture.apply()
        return Result.success()
    }

    companion object {
        private const val LAVORO = "caelum-soglie-personali"
        private const val MEMORIA = "notifiche_soglie"
        const val CANALE = "soglie_personali"
        private const val ID_BASE = 4400

        fun pianifica(context: Context) {
            val richiesta = PeriodicWorkRequestBuilder<SogliePersonaliWorker>(2, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(LAVORO, ExistingPeriodicWorkPolicy.KEEP, richiesta)
        }

        fun annulla(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(LAVORO)
        }

        fun creaCanale(context: Context) {
            // Importanza normale, come il riepilogo: e' una previsione che ti
            // riguarda, non una cosa che sta succedendo adesso.
            val canale = NotificationChannel(
                CANALE,
                tr("Avvisi personali", "Personal notices", "Persönliche Hinweise", "Avis personnels", "Avisos personales"),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = tr(
                    "Gelo, caldo e vento forte, quando la previsione passa le soglie che hai scelto tu. Non sono allerte ufficiali",
                    "Frost, heat and strong wind, when the forecast crosses the thresholds you chose. They are not official warnings",
                    "Frost, Hitze und starker Wind, wenn die Vorhersage die von dir gewählten Schwellen überschreitet. Es sind keine amtlichen Warnungen",
                    "Gel, chaleur et vent fort, quand la prévision franchit les seuils que vous avez choisis. Ce ne sont pas des alertes officielles",
                    "Helada, calor y viento fuerte, cuando la previsión supera los umbrales que has elegido. No son alertas oficiales",
                )
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canale)
        }

        // Il permesso si controlla con `puoNotificare`, che lint non sa seguire.
        @SuppressLint("MissingPermission")
        private fun notifica(
            context: Context,
            citta: String,
            evento: SogliaSuperata,
            adesso: LocalDateTime,
            unita: TempUnit,
            vento: SalaWindUnit,
        ) {
            creaCanale(context)
            val (titolo, testo) = testiSoglia(citta, evento, adesso, unita, vento)
            val idNotifica = ID_BASE + evento.tipo.ordinal
            val sala = if (evento.tipo == TipoSoglia.VENTO) SalaRoom.VENTO else SalaRoom.OGGI
            val apri = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SALA_WIDGET, sala.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val tocco = PendingIntent.getActivity(
                context,
                idNotifica,
                apri,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val costruttore = NotificationCompat.Builder(context, CANALE)
                .setSmallIcon(R.drawable.ic_notifica_soglia)
                .setContentTitle(titolo)
                .setContentText(testo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(tocco)
                .setAutoCancel(true)
            // Passata l'ora di cui parla, la notifica racconta una cosa vecchia.
            val restano = Duration.between(adesso, evento.quando.plusHours(2)).toMillis()
            if (restano > 0) costruttore.setTimeoutAfter(restano)
            if (!PioggiaInArrivoWorker.puoNotificare(context)) return
            runCatching { NotificationManagerCompat.from(context).notify(idNotifica, costruttore.build()) }
        }
    }
}

/**
 * Una soglia che la previsione passa.
 *
 * @param valore il numero previsto nell'unita' del dato: gradi Celsius per
 *   gelo e caldo, metri al secondo per le raffiche.
 * @param soglia il numero scelto: gradi Celsius, o km/h.
 */
internal data class SogliaSuperata(
    val tipo: TipoSoglia,
    val quando: LocalDateTime,
    val valore: Double,
    val soglia: Int,
)

/** Quante ore avanti si guarda: vedi la nota in testa a [SogliePersonaliWorker]. */
internal const val ORIZZONTE_SOGLIE_ORE = 16L

/** Vero fuori dalla notte: fra le 22 e le 7 non si manda niente. */
internal fun oraDiParlare(ora: LocalTime): Boolean = ora.hour in 7..21

/**
 * Le soglie accese che la previsione passa nelle prossime [orizzonteOre] ore:
 * per ciascuna, l'ora piu' estrema. Pura, per poterla provare.
 *
 * **Si confronta il numero che si leggera'**, cioe' arrotondato: una minima di
 * 0,4° si scrive "0°", e chi ha chiesto l'avviso "da 0° in giu'" e apre l'app
 * su "0°" senza averlo ricevuto penserebbe a un guasto. Le raffiche si
 * confrontano in km/h, che e' l'unita' in cui la soglia e' scritta.
 */
internal fun soglieSuperate(
    ore: List<HourForecast>,
    adesso: LocalDateTime,
    soglie: SogliePersonali,
    orizzonteOre: Long = ORIZZONTE_SOGLIE_ORE,
): List<SogliaSuperata> {
    val da = adesso.truncatedTo(ChronoUnit.HOURS)
    val fino = da.plusHours(orizzonteOre)
    val finestra = ore.filter { !it.time.isBefore(da) && it.time.isBefore(fino) }
    if (finestra.isEmpty()) return emptyList()

    val superate = mutableListOf<SogliaSuperata>()
    if (soglie.gelo) {
        finestra.filter { it.temperature != null }.minByOrNull { it.temperature!! }?.let { h ->
            val gradi = h.temperature!!
            if (gradi.roundToInt() <= soglie.geloGradi) {
                superate += SogliaSuperata(TipoSoglia.GELO, h.time, gradi, soglie.geloGradi)
            }
        }
    }
    if (soglie.caldo) {
        finestra.filter { it.temperature != null }.maxByOrNull { it.temperature!! }?.let { h ->
            val gradi = h.temperature!!
            if (gradi.roundToInt() >= soglie.caldoGradi) {
                superate += SogliaSuperata(TipoSoglia.CALDO, h.time, gradi, soglie.caldoGradi)
            }
        }
    }
    if (soglie.vento) {
        finestra.filter { it.windGusts != null }.maxByOrNull { it.windGusts!! }?.let { h ->
            val raffica = h.windGusts!!
            if ((raffica * 3.6).roundToInt() >= soglie.ventoKmh) {
                superate += SogliaSuperata(TipoSoglia.VENTO, h.time, raffica, soglie.ventoKmh)
            }
        }
    }
    return superate
}

/** Una soglia di temperatura, scritta nell'unita' scelta: "0°", "32°". */
internal fun gradiDiSoglia(celsius: Int, unita: TempUnit): String =
    "${unita.from(celsius.toDouble()).roundToInt()}°"

/** Una soglia di vento, scritta nell'unita' scelta: "60 km/h", "32 kn". */
internal fun ventoDiSoglia(kmh: Int, vento: SalaWindUnit): String =
    "${vento.from(kmh / 3.6).roundToInt()} ${vento.label}"

/**
 * Titolo e testo dell'avviso: puri, per poterli provare.
 *
 * Il titolo dice cosa e quanto; il testo quando, qual era la soglia, e che e'
 * una previsione. **L'ultima frase non e' di troppo**: e' cio' che distingue
 * questo promemoria da un'allerta, scritto dove lo si legge e non solo nelle
 * note legali.
 */
internal fun testiSoglia(
    citta: String,
    evento: SogliaSuperata,
    adesso: LocalDateTime,
    unita: TempUnit,
    vento: SalaWindUnit,
): Pair<String, String> {
    val ora = evento.quando.hour
    val quando = when (evento.quando.toLocalDate()) {
        adesso.toLocalDate() -> tr("oggi verso le $ora", "today around $ora:00", "heute gegen $ora Uhr", "aujourd'hui vers $ora h", "hoy hacia las $ora")
        adesso.toLocalDate().plusDays(1) -> tr("domani verso le $ora", "tomorrow around $ora:00", "morgen gegen $ora Uhr", "demain vers $ora h", "mañana hacia las $ora")
        else -> tr("verso le $ora", "around $ora:00", "gegen $ora Uhr", "vers $ora h", "hacia las $ora")
    }
    val coda = tr("È una previsione, non un'allerta ufficiale.", "This is a forecast, not an official warning.", "Das ist eine Vorhersage, keine amtliche Warnung.", "C'est une prévision, pas une alerte officielle.", "Es una previsión, no una alerta oficial.")
    return when (evento.tipo) {
        TipoSoglia.GELO -> {
            val gradi = "${unita.from(evento.valore).roundToInt()}°"
            val soglia = gradiDiSoglia(evento.soglia, unita)
            tr("Gelo a $citta: minima di $gradi", "Frost in $citta: low of $gradi", "Frost in $citta: Tiefstwert $gradi", "Gel à $citta : minimale de $gradi", "Helada en $citta: mínima de $gradi") to
                tr("Prevista $quando. La tua soglia: $soglia. $coda", "Expected $quando. Your threshold: $soglia. $coda", "Erwartet $quando. Deine Schwelle: $soglia. $coda", "Prévue $quando. Votre seuil : $soglia. $coda", "Prevista $quando. Tu umbral: $soglia. $coda")
        }
        TipoSoglia.CALDO -> {
            val gradi = "${unita.from(evento.valore).roundToInt()}°"
            val soglia = gradiDiSoglia(evento.soglia, unita)
            tr("Caldo a $citta: massima di $gradi", "Heat in $citta: high of $gradi", "Hitze in $citta: Höchstwert $gradi", "Chaleur à $citta : maximale de $gradi", "Calor en $citta: máxima de $gradi") to
                tr("Prevista $quando. La tua soglia: $soglia. $coda", "Expected $quando. Your threshold: $soglia. $coda", "Erwartet $quando. Deine Schwelle: $soglia. $coda", "Prévue $quando. Votre seuil : $soglia. $coda", "Prevista $quando. Tu umbral: $soglia. $coda")
        }
        TipoSoglia.VENTO -> {
            val raffica = "${vento.from(evento.valore).roundToInt()} ${vento.label}"
            val soglia = ventoDiSoglia(evento.soglia, vento)
            tr("Vento forte a $citta: raffiche a $raffica", "Strong wind in $citta: gusts of $raffica", "Starker Wind in $citta: Böen bis $raffica", "Vent fort à $citta : rafales à $raffica", "Viento fuerte en $citta: rachas de $raffica") to
                tr("Previste $quando. La tua soglia: $soglia. $coda", "Expected $quando. Your threshold: $soglia. $coda", "Erwartet $quando. Deine Schwelle: $soglia. $coda", "Prévues $quando. Votre seuil : $soglia. $coda", "Previstas $quando. Tu umbral: $soglia. $coda")
        }
    }
}
