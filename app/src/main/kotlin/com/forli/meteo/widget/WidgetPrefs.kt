package com.forli.meteo.widget

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.forli.meteo.data.Place
import kotlinx.coroutines.flow.first

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
     * Legge la configurazione dell'istanza dal DataStore.
     *
     * La ricostruzione del Place dai campi primitivi e' diretta e non puo'
     * fallire: se lat e nome sono presenti, il Place viene costruito senza
     * eccezioni. Nessun parsing JSON, nessun runCatching, nessun fallback
     * silenzioso.
     */
    suspend fun load(appWidgetId: Int): WidgetConfig {
        val prefs = context.widgetDataStore.data.first()
        val useLocation = prefs[useLocationKey(appWidgetId)] ?: false
        val lat = prefs[latKey(appWidgetId)]
        val lon = prefs[lonKey(appWidgetId)]
        val nome = prefs[nomeKey(appWidgetId)]

        val place = if (nome != null && lat != null && lon != null) {
            Place(
                name = nome,
                admin = prefs[adminKey(appWidgetId)],
                country = prefs[paeseKey(appWidgetId)],
                latitude = lat,
                longitude = lon,
            )
        } else {
            null
        }

        Log.d(
            "WidgetPrefs",
            "load(id=$appWidgetId): useLocation=$useLocation " +
                "place=${place?.name} lat=$lat lon=$lon",
        )
        return WidgetConfig(useLocation = useLocation, place = place)
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
                prefs[latKey(appWidgetId)] = place.latitude
                prefs[lonKey(appWidgetId)] = place.longitude
                prefs[nomeKey(appWidgetId)] = place.name
                // I campi nullable vengono rimossi se assenti per non lasciare
                // valori vecchi da una configurazione precedente.
                if (place.admin != null) {
                    prefs[adminKey(appWidgetId)] = place.admin
                } else {
                    prefs.remove(adminKey(appWidgetId))
                }
                if (place.country != null) {
                    prefs[paeseKey(appWidgetId)] = place.country
                } else {
                    prefs.remove(paeseKey(appWidgetId))
                }
            } else {
                prefs.remove(latKey(appWidgetId))
                prefs.remove(lonKey(appWidgetId))
                prefs.remove(nomeKey(appWidgetId))
                prefs.remove(adminKey(appWidgetId))
                prefs.remove(paeseKey(appWidgetId))
            }
        }

        Log.d(
            "WidgetPrefs",
            "save(id=$appWidgetId): useLocation=${config.useLocation} " +
                "place=${config.place?.name} " +
                "lat=${config.place?.latitude} lon=${config.place?.longitude}",
        )
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
            prefs.remove(latKey(appWidgetId))
            prefs.remove(lonKey(appWidgetId))
            prefs.remove(nomeKey(appWidgetId))
            prefs.remove(adminKey(appWidgetId))
            prefs.remove(paeseKey(appWidgetId))
            // Chiavi legacy: non vengono piu' scritte ma vanno rimosse per
            // chi aggiorna dalla versione precedente.
            prefs.remove(stringPreferencesKey("localita_$appWidgetId"))
            prefs.remove(intPreferencesKey("sfondo_$appWidgetId"))
            prefs.remove(intPreferencesKey("accento_$appWidgetId"))
        }
    }

    private companion object {
        fun useLocationKey(id: Int) = booleanPreferencesKey("posizione_$id")
        fun latKey(id: Int) = doublePreferencesKey("lat_$id")
        fun lonKey(id: Int) = doublePreferencesKey("lon_$id")
        fun nomeKey(id: Int) = stringPreferencesKey("nome_$id")
        fun adminKey(id: Int) = stringPreferencesKey("admin_$id")
        fun paeseKey(id: Int) = stringPreferencesKey("paese_$id")
    }
}
