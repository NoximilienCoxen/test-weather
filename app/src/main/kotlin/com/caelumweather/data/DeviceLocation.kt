package com.caelumweather.data

import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Dove sei, chiesto alla piattaforma e a nessun altro.
 *
 * `LocationManager` e **non** `play-services-location`: sarebbe una dipendenza
 * nuova su un progetto che finora non ne ha aggiunte, e per scegliere quale
 * previsione mostrare basta molto meno di quello che quella libreria sa fare.
 *
 * **Solo approssimato.** Una previsione vale per una cella di chilometri:
 * chiedere la posizione fine sarebbe chiedere piu' di quanto serva, e un
 * permesso che non serve e' un permesso che non si chiede.
 */
object DeviceLocation {

    /** Il nome che si mette quando non c'e' modo di saperne uno vero. */
    val FALLBACK_NAME: String get() = tr("POSIZIONE", "LOCATION", "STANDORT", "POSITION", "UBICACIÓN")

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * La localita' corrente, o nullo se non si riesce a saperla.
     *
     * Prima si guarda l'ultima posizione nota, che risponde subito e nella
     * stragrande maggioranza dei casi c'e' gia': un telefono acceso da un
     * minuto sa dov'e'. Solo se non c'e' niente si chiede un rilevamento
     * vero, e con un tempo massimo - restare in attesa di un satellite
     * mentre l'utente guarda le impostazioni non e' un comportamento, e' un
     * blocco.
     *
     * Fra i modi di non riuscire c'e' anche un rilevamento che arriva ma senza
     * coordinate utilizzabili: vedi [describe].
     */
    suspend fun current(context: Context): Place? = withContext(Dispatchers.IO) {
        if (!granted(context)) return@withContext null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null
        val fix = lastKnown(manager) ?: awaitFix(manager) ?: return@withContext null
        describe(context, fix)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? = runCatching {
        // Tutti i fornitori e non uno solo: quale sia acceso dipende dal
        // telefono e da cosa ha fatto l'utente. Fra quelli che rispondono si
        // prende il piu' recente, e solo fra quelli che hanno davvero delle
        // coordinate: un'ultima posizione nota con `NaN` dentro scarterebbe da
        // sola il rilevamento vero, che verrebbe chiesto solo se qui non ci
        // fosse niente.
        manager.allProviders
            .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
            .filter { it.latitude.isFinite() && it.longitude.isFinite() }
            .maxByOrNull { it.time }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun awaitFix(manager: LocationManager): Location? =
        withTimeoutOrNull(FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val provider = when {
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                        LocationManager.NETWORK_PROVIDER
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                        LocationManager.GPS_PROVIDER
                    else -> null
                }
                if (provider == null) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    // Su Android 8 le tre qui sotto sono ancora astratte: hanno
                    // un corpo predefinito solo dal 30 in poi, e senza di loro
                    // l'app non partirebbe proprio sui telefoni piu' vecchi che
                    // dichiara di supportare.
                    @Deprecated("Richiesta dalle versioni vecchie dell'interfaccia")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) =
                        Unit

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit
                }

                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                runCatching {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }

    /**
     * Coordinate piu' un nome, che e' quello che [Place] vuole - oppure nulla,
     * se quelle coordinate non sono numeri.
     *
     * Il nome lo prova la geocodifica inversa della piattaforma. **Su
     * un'immagine AOSP non risolve niente** - e' scritto anche in `Place.kt` -
     * e Open-Meteo non ne offre una: la sua ricerca va per nome, non per
     * coordinate. Quando non torna nulla il posto si chiama e basta, invece di
     * costare una richiesta in piu' alla rete per una sola parola.
     *
     * **Il nome non fa fede sulle coordinate.** Un `Location` con latitudine e
     * longitudine a `NaN` si geocodifica lo stesso - l'emulatore ne ha dato uno
     * che si chiamava "MOUNTAIN VIEW" - e una [Place] con un nome giusto e
     * numeri finti sembra a tutti gli effetti valida, fino a quando non arriva
     * alla rete. Un rilevamento senza coordinate e' un rilevamento mancato:
     * torna nulla come tutti gli altri modi di non riuscire, e chi ha chiamato
     * resta dov'era invece di guardare una previsione di nessun posto.
     */
    private suspend fun describe(context: Context, fix: Location): Place? {
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite()) return null
        val address = indirizzo(context, fix.latitude, fix.longitude)
        return Place(
            name = nomeDelPosto(address?.locality, address?.subAdminArea, address?.adminArea),
            admin = address?.adminArea,
            country = address?.countryName,
            latitude = fix.latitude,
            longitude = fix.longitude,
        )
    }

    /**
     * La geocodifica inversa, **fatta come la piattaforma vuole**.
     *
     * La chiamata sincrona e' deprecata da Android 13 e su alcuni telefoni
     * torna vuota o lancia `IOException` anche con la rete accesa: l'app
     * restava con "POSIZIONE" in testa invece del nome del paese (visto in mano
     * il 26 novembre 2026). Da Android 13 si usa quella col richiamo, con un
     * tempo massimo; prima si riprova una volta dopo un secondo, perche' il
     * primo tentativo a freddo e' quello che fallisce piu' spesso.
     */
    private suspend fun indirizzo(context: Context, lat: Double, lon: Double): android.location.Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Lingue.locale)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            repeat(2) { tentativo ->
                val trovato = withTimeoutOrNull(GEOCODIFICA_MS) {
                    suspendCancellableCoroutine<android.location.Address?> { c ->
                        runCatching {
                            geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                    if (c.isActive) c.resume(addresses.firstOrNull())
                                }

                                override fun onError(errorMessage: String?) {
                                    if (c.isActive) c.resume(null)
                                }
                            })
                        }.onFailure { if (c.isActive) c.resume(null) }
                    }
                }
                if (trovato != null) return trovato
                if (tentativo == 0) kotlinx.coroutines.delay(1_000)
            }
            return null
        }
        repeat(2) { tentativo ->
            val trovato = runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            }.getOrNull()
            if (trovato != null) return trovato
            if (tentativo == 0) kotlinx.coroutines.delay(1_000)
        }
        return null
    }

    /** Oltre questo, un rilevamento non sta arrivando. */
    private const val FIX_TIMEOUT_MS = 8_000L

    /** Quanto si aspetta un nome, per tentativo. */
    private const val GEOCODIFICA_MS = 4_000L
}

/** Il nome da mostrare: il comune, se no la provincia, se no la regione, se no il ripiego. */
internal fun nomeDelPosto(comune: String?, provincia: String?, regione: String?): String =
    listOf(comune, provincia, regione).firstOrNull { !it.isNullOrBlank() }?.trim() ?: DeviceLocation.FALLBACK_NAME

/**
 * **Un nome vero non si perde per una geocodifica andata male.** Se il posto
 * appena trovato non ha un nome (e' il ripiego) ma quello in cui si era sta a
 * meno di [RAGGIO_KM], si tiene il nome di prima con le coordinate nuove: chi
 * apre l'app a casa sua non deve leggere "POSIZIONE" solo perche' stavolta il
 * geocoder non ha risposto.
 */
fun conNomeTenuto(trovato: Place, prima: Place?): Place {
    if (trovato.name != DeviceLocation.FALLBACK_NAME || prima == null) return trovato
    if (prima.name == DeviceLocation.FALLBACK_NAME || prima.name.isBlank()) return trovato
    if (distanzaKm(trovato.latitude, trovato.longitude, prima.latitude, prima.longitude) > RAGGIO_KM) return trovato
    return trovato.copy(name = prima.name, admin = prima.admin, country = prima.country)
}

private const val RAGGIO_KM = 10.0

internal fun distanzaKm(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
    val r = 6371.0
    val dLa = Math.toRadians(la2 - la1)
    val dLo = Math.toRadians(lo2 - lo1)
    val a = kotlin.math.sin(dLa / 2).let { it * it } +
        kotlin.math.cos(Math.toRadians(la1)) * kotlin.math.cos(Math.toRadians(la2)) * kotlin.math.sin(dLo / 2).let { it * it }
    return 2 * r * kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0, 1.0)))
}
