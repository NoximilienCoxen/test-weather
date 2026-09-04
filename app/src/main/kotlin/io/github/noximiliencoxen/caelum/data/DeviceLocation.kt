package io.github.noximiliencoxen.caelum.data

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
import java.util.Locale
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
    const val FALLBACK_NAME = "POSIZIONE"

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
        // prende il piu' recente.
        manager.allProviders
            .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
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
     * Coordinate piu' un nome, che e' quello che [Place] vuole.
     *
     * Il nome lo prova la geocodifica inversa della piattaforma. **Su
     * un'immagine AOSP non risolve niente** - e' scritto anche in `Place.kt` -
     * e Open-Meteo non ne offre una: la sua ricerca va per nome, non per
     * coordinate. Quando non torna nulla il posto si chiama e basta, invece di
     * costare una richiesta in piu' alla rete per una sola parola.
     */
    private fun describe(context: Context, fix: Location): Place {
        val address = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.ITALIAN)
                .getFromLocation(fix.latitude, fix.longitude, 1)
                ?.firstOrNull()
        }.getOrNull()

        return Place(
            name = address?.locality
                ?: address?.subAdminArea
                ?: address?.adminArea
                ?: FALLBACK_NAME,
            admin = address?.adminArea,
            country = address?.countryName,
            latitude = fix.latitude,
            longitude = fix.longitude,
        )
    }

    /** Oltre questo, un rilevamento non sta arrivando. */
    private const val FIX_TIMEOUT_MS = 8_000L
}
