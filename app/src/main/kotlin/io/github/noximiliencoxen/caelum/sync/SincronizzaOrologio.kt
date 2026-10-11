package io.github.noximiliencoxen.caelum.sync

import android.app.Application
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import io.github.noximiliencoxen.caelum.data.StatoSincronizzato
import io.github.noximiliencoxen.caelum.lingua.Lingue
import io.github.noximiliencoxen.caelum.prefs.SettingsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Dice all'orologio dove siamo e come vogliamo i numeri (CONTESTO §50).
 *
 * Ogni volta che cambiano la localita', le unita' o la lingua, scrive un solo
 * dato nel Data Layer (`/caelum/stato`, vedi [StatoSincronizzato]). **Nessun
 * punto di chiamata da toccare**: si ascolta il flusso delle impostazioni, che
 * `setPlace`, `setUnit` e `setWindUnit` gia' alimentano. La lingua sta in una
 * SharedPreferences e non nel flusso, quindi `Lingue.scegli` avvisa a mano con
 * [linguaCambiata].
 *
 * **Un orologio assente non e' un guasto.** Senza orologio abbinato, o senza
 * Google Play Services (un'immagine AOSP), il Data Layer risponde con un errore:
 * qui si ignora. Un errore non intercettato in questo scope porterebbe giu' il
 * processo del telefono per una funzione che non serve a chi non ha l'orologio.
 */
object SincronizzaOrologio {

    private val rilancio = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** La lingua e' cambiata: si rimanda lo stato com'e' adesso. */
    fun linguaCambiata() {
        rilancio.tryEmit(Unit)
    }

    fun avvia(app: Application) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            combine(SettingsPrefs(app).settings, rilancio.onStart { emit(Unit) }) { impostazioni, _ -> impostazioni }
                .map {
                    StatoSincronizzato.di(it.place, it.unit.name, it.windUnit.name, Lingue.scelta)
                }
                .distinctUntilChanged()
                .catch { /* il flusso delle impostazioni non deve fermare l'app */ }
                .collect { stato -> invia(app, stato) }
        }
    }

    private suspend fun invia(app: Application, stato: StatoSincronizzato) {
        runCatching {
            val richiesta = PutDataRequest.create(StatoSincronizzato.PERCORSO)
                .setData(stato.toJson().toByteArray(Charsets.UTF_8))
                .setUrgent()
            Wearable.getDataClient(app).putDataItem(richiesta).aspetta()
        }
    }

    /** Aspetta il risultato di un `Task` senza dipendere da `kotlinx-coroutines-play-services`. */
    private suspend fun <T> Task<T>.aspetta(): T? = suspendCancellableCoroutine { continuazione ->
        addOnCompleteListener { if (continuazione.isActive) continuazione.resume(if (it.isSuccessful) it.result else null) }
    }
}
