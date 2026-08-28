package com.forli.meteo.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.forli.meteo.data.Place
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Le scelte fatte nella configurazione, una riga per ogni widget piazzato.
 *
 * Un archivio nostro e non lo stato di Glance: la configurazione gira **prima**
 * che il widget esista: quando la si scriveva attraverso Glance, il widget
 * appena creato ripartiva da uno stato vuoto e ricadeva sui colori di sistema,
 * ignorando la tinta appena scelta. Qui la chiave e' l'`appWidgetId`, che il
 * sistema assegna prima di aprire la configurazione ed e' lo stesso che il
 * widget si ritrova al primo disegno.
 */
private val Context.widgetDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "widget_config")

private val widgetJson = Json { ignoreUnknownKeys = true }

/**
 * Dove guarda un widget.
 *
 * Solo la localita': i colori li decide il tema del telefono, e sceglierli a
 * mano era una promessa che il disegno non riusciva a mantenere.
 */
data class WidgetConfig(
    val useLocation: Boolean = false,
    val place: Place? = null,
)

class WidgetPrefs(private val context: Context) {

    suspend fun load(appWidgetId: Int): WidgetConfig {
        val prefs = context.widgetDataStore.data.first()
        return WidgetConfig(
            useLocation = prefs[useLocationKey(appWidgetId)] ?: false,
            place = prefs[placeKey(appWidgetId)]?.let { raw ->
                runCatching { widgetJson.decodeFromString<Place>(raw) }.getOrNull()
            },
        )
    }

    suspend fun save(appWidgetId: Int, config: WidgetConfig) {
        context.widgetDataStore.edit { prefs ->
            prefs[useLocationKey(appWidgetId)] = config.useLocation
            // La place viene sempre salvata se presente, anche con useLocation=true:
            // serve come fallback nel caso il GPS non sia disponibile o i permessi
            // vengano revocati. resolvePlace() decide l'ordine di priorità.
            if (config.place != null) {
                prefs[placeKey(appWidgetId)] = widgetJson.encodeToString(config.place)
            } else {
                prefs.remove(placeKey(appWidgetId))
            }
        }
    }

    /** Toglie le righe di un widget che non sta piu' sulla Home. */
    suspend fun forget(appWidgetId: Int) {
        context.widgetDataStore.edit { prefs ->
            prefs.remove(useLocationKey(appWidgetId))
            prefs.remove(placeKey(appWidgetId))
            // Le due tinte non si salvano piu', ma vanno ancora tolte: chi
            // aggiorna dalla versione di prima se le ritroverebbe nell'archivio
            // per sempre.
            prefs.remove(backgroundKey(appWidgetId))
            prefs.remove(accentKey(appWidgetId))
        }
    }

    private companion object {
        fun useLocationKey(id: Int) = booleanPreferencesKey("posizione_$id")
        fun placeKey(id: Int) = stringPreferencesKey("localita_$id")
        fun backgroundKey(id: Int) = intPreferencesKey("sfondo_$id")
        fun accentKey(id: Int) = intPreferencesKey("accento_$id")
    }
}
