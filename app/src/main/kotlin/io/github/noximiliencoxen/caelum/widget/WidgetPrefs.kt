package io.github.noximiliencoxen.caelum.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.noximiliencoxen.caelum.data.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Le scelte fatte nella configurazione, una riga per ogni widget piazzato.
 *
 * La localita' e' salvata come quattro campi primitivi separati invece che
 * come blob JSON: eliminare la serializzazione elimina l'intera categoria di
 * errori silenziosi in cui il parsing fallisce, il runCatching restituisce
 * null, e il widget ricade sulla citta' globale dell'app senza avvisare.
 *
 * Le chiavi sono:
 *   posizione_$id  (Boolean)  — true se il widget segue il GPS
 *   lat_$id        (Double)   — latitudine della citta' scelta
 *   lon_$id        (Double)   — longitudine della citta' scelta
 *   nome_$id       (String)   — nome visualizzato della citta'
 *   admin_$id      (String?)  — regione/provincia (opzionale, per disambiguare omonimi)
 *   paese_$id      (String?)  — paese (opzionale)
 *   gps_lat_$id, gps_lon_$id, gps_nome_$id, gps_admin_$id, gps_paese_$id
 *                            — l'ultima posizione rilevata, per i widget
 *                              che seguono il GPS (vedi [WidgetPrefs.lastFix])
 *
 * Chiavi legacy rimosse alla prima forget() (non vengono piu' scritte):
 *   localita_$id, sfondo_$id, accento_$id
 */
private val Context.widgetDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "widget_config")

/**
 * Dove guarda un widget.
 *
 * Solo la localita': i colori li decide il tema del telefono.
 */
data class WidgetConfig(
    val useLocation: Boolean = false,
    val place: Place? = null,
)

class WidgetPrefs(private val context: Context) {

    /**
     * Il file, con il ripiego su vuoto se non si legge.
     *
     * **Il file, pero', puo' non leggersi affatto**, e quello e' un caso
     * diverso dal contenuto sbagliato. Un `IOException` qui - disco pieno,
     * file troncato da uno spegnimento brusco - risalirebbe dentro
     * `provideGlance`, cioe' dentro il disegno del widget sulla Home di
     * qualcun altro. Col ripiego su `emptyPreferences()` l'istanza si
     * comporta come una mai configurata: disegna "TOCCA PER CONFIGURARE", che
     * e' una faccia che il progetto ha gia' e che porta dove si rimedia.
     *
     * Solo `IOException`: un difetto di programmazione deve continuare a farsi
     * sentire, non a travestirsi da widget da configurare.
     */
    private val stored: Flow<Preferences>
        get() = context.widgetDataStore.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }

    /**
     * La configurazione dell'istanza, **e ogni sua modifica successiva**.
     *
     * E' quel che il widget osserva mentre e' vivo: una scelta salvata dalla
     * configurazione arriva qui da sola, senza dipendere da chi si ricorda di
     * chiedere un ridisegno. Vedi `CaelumWidget.provideGlance`.
     *
     * `distinctUntilChanged` perche' il file e' uno solo per tutte le istanze:
     * senza, il salvataggio di un widget farebbe ridisegnare - e riscaricare -
     * tutti gli altri. Lo stesso vale per [rememberFix], che scrive chiavi
     * che qui non compaiono.
     */
    fun watch(appWidgetId: Int): Flow<WidgetConfig> = stored
        .map { it.configOf(appWidgetId) }
        .distinctUntilChanged()

    /**
     * Legge la configurazione dell'istanza dal DataStore.
     *
     * La ricostruzione del Place dai campi primitivi e' diretta e non puo'
     * fallire: se lat e nome sono presenti, il Place viene costruito senza
     * eccezioni. Nessun parsing JSON, nessun runCatching, nessun fallback
     * silenzioso.
     */
    suspend fun load(appWidgetId: Int): WidgetConfig = watch(appWidgetId).first()

    /**
     * L'ultima posizione del telefono che questa istanza e' riuscita a sapere.
     *
     * Serve ai widget che seguono il GPS. **Da dietro le quinte il GPS non
     * risponde quasi mai**: da Android 10 un'app che non e' in primo piano non
     * riceve la posizione senza il permesso "sempre", che qui non si chiede.
     * Prima, in quel caso, il widget ripiegava sulla citta' aperta nell'app -
     * e chi aveva chiesto "la mia posizione" vedeva un'altra citta'. Adesso
     * ripiega sull'ultimo punto in cui il telefono e' davvero stato.
     */
    suspend fun lastFix(appWidgetId: Int): Place? =
        stored.first().placeOf(FIX_PREFIX, appWidgetId)

    /** Annota [place] come l'ultima posizione nota di questa istanza. */
    suspend fun rememberFix(appWidgetId: Int, place: Place) {
        context.widgetDataStore.edit { it.putPlace(FIX_PREFIX, appWidgetId, place) }
    }

    /**
     * Scrive la configurazione dell'istanza nel DataStore.
     *
     * La place viene sempre salvata se presente, anche quando useLocation=true:
     * serve come fallback nel caso il GPS non sia disponibile o i permessi
     * vengano revocati dopo la configurazione. E' resolvePlace() a decidere
     * l'ordine di priorita'.
     *
     * Ogni campo e' scritto come primitivo atomico: non esiste uno stato
     * parziale in cui solo alcuni campi sono aggiornati, perche' DataStore
     * garantisce l'atomicita' dell'intera lambda edit{}.
     */
    suspend fun save(appWidgetId: Int, config: WidgetConfig) {
        context.widgetDataStore.edit { prefs ->
            prefs[useLocationKey(appWidgetId)] = config.useLocation

            val place = config.place
            if (place != null) {
                prefs.putPlace(PLACE_PREFIX, appWidgetId, place)
            } else {
                prefs.removePlace(PLACE_PREFIX, appWidgetId)
            }
            // Una citta' scelta a mano non ha niente da ricordare del GPS; una
            // posizione seguita riparte da quella appena rilevata, se c'e'.
            prefs.removePlace(FIX_PREFIX, appWidgetId)
            if (config.useLocation && place != null) prefs.putPlace(FIX_PREFIX, appWidgetId, place)
        }
    }

    /**
     * Rimuove tutte le chiavi di un widget rimosso dalla Home.
     *
     * Include le chiavi legacy (localita_, sfondo_, accento_) che non vengono
     * piu' scritte ma potrebbero esistere in installazioni precedenti.
     */
    suspend fun forget(appWidgetId: Int) {
        context.widgetDataStore.edit { prefs ->
            prefs.remove(useLocationKey(appWidgetId))
            prefs.removePlace(PLACE_PREFIX, appWidgetId)
            prefs.removePlace(FIX_PREFIX, appWidgetId)
            // Chiavi legacy: non vengono piu' scritte ma vanno rimosse per
            // chi aggiorna dalla versione precedente.
            prefs.remove(stringPreferencesKey("localita_$appWidgetId"))
            prefs.remove(intPreferencesKey("sfondo_$appWidgetId"))
            prefs.remove(intPreferencesKey("accento_$appWidgetId"))
        }
    }

    private companion object {
        /** La citta' scelta: nessun prefisso, per restare compatibili con le chiavi gia' scritte. */
        const val PLACE_PREFIX = ""

        /** L'ultima posizione rilevata dal GPS. */
        const val FIX_PREFIX = "gps_"

        fun useLocationKey(id: Int) = booleanPreferencesKey("posizione_$id")
        fun latKey(prefix: String, id: Int) = doublePreferencesKey("${prefix}lat_$id")
        fun lonKey(prefix: String, id: Int) = doublePreferencesKey("${prefix}lon_$id")
        fun nomeKey(prefix: String, id: Int) = stringPreferencesKey("${prefix}nome_$id")
        fun adminKey(prefix: String, id: Int) = stringPreferencesKey("${prefix}admin_$id")
        fun paeseKey(prefix: String, id: Int) = stringPreferencesKey("${prefix}paese_$id")

        fun Preferences.configOf(id: Int) = WidgetConfig(
            useLocation = this[useLocationKey(id)] ?: false,
            place = placeOf(PLACE_PREFIX, id),
        )

        fun Preferences.placeOf(prefix: String, id: Int): Place? {
            val nome = this[nomeKey(prefix, id)] ?: return null
            val lat = this[latKey(prefix, id)] ?: return null
            val lon = this[lonKey(prefix, id)] ?: return null
            return Place(
                name = nome,
                admin = this[adminKey(prefix, id)],
                country = this[paeseKey(prefix, id)],
                latitude = lat,
                longitude = lon,
            )
        }

        fun MutablePreferences.putPlace(prefix: String, id: Int, place: Place) {
            this[latKey(prefix, id)] = place.latitude
            this[lonKey(prefix, id)] = place.longitude
            this[nomeKey(prefix, id)] = place.name
            // I campi nullable vengono rimossi se assenti per non lasciare
            // valori vecchi da una configurazione precedente.
            if (place.admin != null) this[adminKey(prefix, id)] = place.admin else remove(adminKey(prefix, id))
            if (place.country != null) this[paeseKey(prefix, id)] = place.country else remove(paeseKey(prefix, id))
        }

        fun MutablePreferences.removePlace(prefix: String, id: Int) {
            remove(latKey(prefix, id))
            remove(lonKey(prefix, id))
            remove(nomeKey(prefix, id))
            remove(adminKey(prefix, id))
            remove(paeseKey(prefix, id))
        }
    }
}
