package com.forli.meteo.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Dove si trova il telefono, chiesto alla sola piattaforma.
 *
 * `LocationManager` e non `play-services-location`: sarebbe una dipendenza
 * nuova, servirebbe che i servizi Google ci fossero, e per scegliere quale
 * citta' guardare la posizione approssimata della rete e' piu' che sufficiente.
 *
 * ## Tutto quello che qui puo' andare storto
 *
 * Questa e' la ragione per cui il pezzo esiste in un file suo. Chiedere la
 * posizione su Android fallisce in **sette** modi diversi, e ognuno di essi,
 * lasciato scoperto, chiude l'app invece di mostrare un messaggio:
 *
 * 1. il permesso non e' stato concesso, e ogni chiamata lancia `SecurityException`;
 * 2. il permesso e' stato concesso e poi revocato mentre l'app era viva;
 * 3. il servizio di sistema non c'e' - succede sulle immagini AOSP spoglie - e
 *    `getSystemService` restituisce nullo;
 * 4. il fornitore richiesto non esiste su questo dispositivo, e passarne il
 *    nome lancia `IllegalArgumentException`;
 * 5. i fornitori esistono ma sono tutti spenti, e non arriva mai niente;
 * 6. sono accesi ma non hanno mai avuto una posizione, quindi l'ultima nota e'
 *    nulla e la nuova non arriva prima che l'utente si stanchi;
 * 7. la geocodifica inversa non e' implementata, e lancia o restituisce vuoto.
 *
 * Nessuno di questi e' un caso limite: il terzo e il settimo capitano su ogni
 * emulatore, il quinto capita a chiunque tenga il risparmio energetico acceso.
 * Il risultato e' sempre un [Outcome], mai un'eccezione che esce di qui.
 */
object DeviceLocation {

    sealed interface Outcome {
        /** Trovata. Il nome puo' essere quello vero o le sole coordinate. */
        data class Found(val place: Place) : Outcome

        /** Manca il permesso: chi chiama deve chiederlo, non riprovare. */
        data object NeedsPermission : Outcome

        /** I servizi di localizzazione sono spenti o assenti. */
        data object Unavailable : Outcome

        /** Accesi, ma non hanno detto niente in tempo utile. */
        data object Timeout : Outcome
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Chiede dove siamo.
     *
     * Prima l'ultima posizione nota, che di solito arriva subito; solo se non
     * c'e' si aspetta una lettura nuova. Chiedere sempre quella nuova
     * costringerebbe a fissare uno spinner per qualche secondo anche quando la
     * risposta era gia' in tasca.
     */
    suspend fun current(context: Context): Outcome = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext Outcome.NeedsPermission

        val manager = runCatching {
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        }.getOrNull() ?: return@withContext Outcome.Unavailable

        val providers = runCatching { manager.getProviders(true) }
            .getOrDefault(emptyList())
            .filter { it != LocationManager.PASSIVE_PROVIDER }
        if (providers.isEmpty()) return@withContext Outcome.Unavailable

        lastKnown(manager, providers)?.let {
            return@withContext Outcome.Found(placeOf(context, it))
        }

        // `try` mirato e non `runCatching`: quest'ultimo cattura anche la
        // cancellazione del chiamante, e una schermata chiusa mentre si cerca
        // la posizione si tradurrebbe in un "non trovata" scritto su
        // un'interfaccia che non c'e' piu', invece che in un lavoro che smette.
        val fresh = try {
            withTimeout(FIX_TIMEOUT_MS) { awaitFix(manager, providers) }
        } catch (scaduto: TimeoutCancellationException) {
            null
        }

        when (fresh) {
            null -> Outcome.Timeout
            else -> Outcome.Found(placeOf(context, fresh))
        }
    }

    /**
     * La piu' recente fra le ultime note dei fornitori attivi.
     *
     * Non la prima che si trova: la rete e il GPS ne tengono una ciascuno, e
     * quella del GPS puo' essere di tre giorni fa, di un'altra citta'.
     */
    private fun lastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { provider ->
            // Il permesso c'e' stato verificato un istante fa, ma puo' essere
            // stato revocato nel frattempo: fra il controllo e la chiamata
            // l'utente ha avuto tutto il tempo di aprire le impostazioni.
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
            .filter { it.time > System.currentTimeMillis() - STALE_MS }
            .maxByOrNull { it.time }

    /**
     * Aspetta una lettura nuova.
     *
     * `getCurrentLocation` dal dodici in su fa esattamente questo ed e' la via
     * giusta; sotto, si registra un ascoltatore e lo si toglie al primo
     * risultato. In entrambi i casi il richiamo puo' arrivare **due volte** - una
     * dal fornitore di rete, una dal GPS - e riprendere due volte la stessa
     * continuazione fa terminare il processo con `IllegalStateException`: da qui
     * la bandiera.
     */
    private suspend fun awaitFix(
        manager: LocationManager,
        providers: List<String>,
    ): Location? = suspendCancellableCoroutine { continuation ->
        val done = AtomicBoolean(false)

        fun deliver(location: Location?) {
            if (done.compareAndSet(false, true) && continuation.isActive) {
                continuation.resume(location)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val signal = android.os.CancellationSignal()
            val provider = providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
                ?: providers.first()
            val started = runCatching {
                manager.getCurrentLocation(
                    provider,
                    signal,
                    // Esecutore che chiama sul posto invece di un pool: il
                    // richiamo non fa altro che riprendere una continuazione, e
                    // un pool creato qui non lo chiuderebbe mai nessuno - un
                    // thread abbandonato per ogni tocco di "TROVAMI".
                    { command -> command.run() },
                ) { location -> deliver(location) }
            }.isSuccess
            if (!started) deliver(null)
            continuation.invokeOnCancellation { runCatching { signal.cancel() } }
            return@suspendCancellableCoroutine
        }

        @Suppress("DEPRECATION")
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) = deliver(location)

            // Su Android 8 e 9 questi tre sono astratti: non implementarli
            // compila e poi lancia `AbstractMethodError` alla prima chiamata
            // del sistema, cioe' proprio quando servono. Deprecato dal Q in
            // poi, ma qui si e' gia' sotto al Q per costruzione.
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = deliver(null)
        }

        val looper = android.os.Looper.getMainLooper()
        var registered = false
        for (provider in providers) {
            // Un fornitore per volta e ognuno protetto: passare il nome di uno
            // che su questo dispositivo non esiste lancia, e un solo nome
            // sbagliato butterebbe via anche quelli buoni.
            registered = runCatching {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, looper)
            }.isSuccess || registered
        }
        if (!registered) {
            deliver(null)
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation {
            runCatching { manager.removeUpdates(listener) }
        }
    }

    /**
     * Da coordinate a localita'.
     *
     * La geocodifica inversa della piattaforma non risolve nulla su un'immagine
     * AOSP e puo' lanciare: se non risponde, il nome diventano le coordinate
     * stesse. Meglio "44,22° N 12,04° E" che un fallimento - il meteo di quel
     * punto e' comunque quello giusto, ed e' quello che si era chiesto.
     */
    private fun placeOf(context: Context, location: Location): Place {
        val fallback = Place(
            name = formatCoordinates(location.latitude, location.longitude),
            admin = null,
            country = null,
            latitude = location.latitude,
            longitude = location.longitude,
        )
        if (!android.location.Geocoder.isPresent()) return fallback

        return runCatching {
            @Suppress("DEPRECATION")
            val found = android.location.Geocoder(context, java.util.Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?: return fallback
            val name = found.locality
                ?: found.subAdminArea
                ?: found.adminArea
                ?: return fallback
            Place(
                name = name,
                admin = found.adminArea?.takeIf { it != name },
                country = found.countryName,
                latitude = location.latitude,
                longitude = location.longitude,
            )
        }.getOrDefault(fallback)
    }

    private fun formatCoordinates(latitude: Double, longitude: Double): String = String.format(
        java.util.Locale.ROOT,
        "%.2f° %s  %.2f° %s",
        kotlin.math.abs(latitude), if (latitude >= 0) "N" else "S",
        kotlin.math.abs(longitude), if (longitude >= 0) "E" else "O",
    )

    /** Oltre questo, l'ultima posizione nota descrive dov'eri, non dove sei. */
    private const val STALE_MS = 30 * 60 * 1000L

    /**
     * Quanto si aspetta una lettura nuova. Dieci secondi: il fornitore di rete
     * risponde in due, e oltre i dieci chi guarda ha gia' deciso che e' rotta.
     */
    private const val FIX_TIMEOUT_MS = 10_000L
}
