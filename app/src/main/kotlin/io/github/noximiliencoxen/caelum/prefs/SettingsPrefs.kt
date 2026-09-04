package io.github.noximiliencoxen.caelum.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.WeatherModel
import io.github.noximiliencoxen.caelum.data.key
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Unita' della temperatura.
 *
 * La conversione sta qui e non nella rete: cambiare unita' non deve costare una
 * richiesta e un'attesa davanti a una schermata vuota.
 */
enum class TempUnit(val symbol: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F"),
    ;

    fun from(celsius: Double): Double =
        if (this == CELSIUS) celsius else celsius * 9.0 / 5.0 + 32.0
}

/** Tutto cio' che l'utente ha scelto e che deve sopravvivere alla chiusura. */
data class Settings(
    val place: Place = Place.FORLI,
    val unit: TempUnit = TempUnit.CELSIUS,
    /**
     * Vero quando il posto lo decide il telefono.
     *
     * Il posto resta comunque salvato per intero: cosi' all'avvio successivo
     * la schermata ha subito qualcosa da mostrare mentre la posizione viene
     * richiesta, invece di ripartire da una citta' che non c'entra.
     */
    val followsLocation: Boolean = false,
    /**
     * Vero da quando il benvenuto ha finito il suo lavoro.
     *
     * Serve una chiave sua e non basta guardare se una localita' e' salvata:
     * senza scelta il posto e' Forli' per impostazione predefinita, e "non ho
     * mai scelto" e "ho scelto Forli'" sono la stessa cosa vista da fuori.
     */
    val welcomed: Boolean = false,
    /** Motore numerico scelto per la previsione. */
    val model: WeatherModel = WeatherModel.AUTO,
    /** Localita' salvate a parte dalla scelta corrente. */
    val favorites: List<Place> = emptyList(),
    /**
     * Gli identificativi delle allerte per cui la fascia e' stata ridotta.
     *
     * Non basta ricordare **che** e' stata chiusa: va ricordato **cosa** e'
     * stato chiuso. Chiudere l'avviso di oggi non puo' nascondere quello che
     * arriva domani, se no la fascia smetterebbe di avvisare esattamente
     * quando serve.
     */
    val dismissedAlertIds: Set<String> = emptySet(),
    /**
     * Il peso del livello peggiore fra quelle chiuse (1 gialla, 2 arancione,
     * 3 rossa).
     *
     * Sta accanto agli identificativi perche' un'allerta puo' **peggiorare**
     * restando la stessa: la gialla di stamattina che diventa arancione ha lo
     * stesso id e non e' piu' la stessa notizia.
     */
    val dismissedAlertWeight: Int = 0,
)

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "impostazioni")

private val favoritesJson = Json { ignoreUnknownKeys = true }

class SettingsPrefs(private val context: Context) {

    val settings: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        val latitude = prefs[KEY_LAT]
        val longitude = prefs[KEY_LON]
        val name = prefs[KEY_NAME]
        // Nome e coordinate si salvano insieme e si rileggono insieme: una meta'
        // sola descriverebbe un posto che non e' ne' quello scelto ne' il
        // predefinito.
        val place = if (latitude != null && longitude != null && name != null) {
            Place(
                name = name,
                admin = prefs[KEY_ADMIN],
                country = prefs[KEY_COUNTRY],
                latitude = latitude,
                longitude = longitude,
            )
        } else {
            Place.FORLI
        }
        Settings(
            place = place,
            unit = prefs[KEY_UNIT]
                ?.let { saved -> TempUnit.entries.firstOrNull { it.name == saved } }
                ?: TempUnit.CELSIUS,
            followsLocation = prefs[KEY_FOLLOWS] ?: false,
            welcomed = prefs[KEY_WELCOMED] ?: false,
            model = prefs[KEY_MODEL]
                ?.let { saved -> WeatherModel.entries.firstOrNull { it.name == saved } }
                ?: WeatherModel.AUTO,
            favorites = decodeFavorites(prefs[KEY_FAVORITES]),
            dismissedAlertIds = prefs[KEY_ALERTS_DISMISSED].orEmpty(),
            dismissedAlertWeight = prefs[KEY_ALERTS_WEIGHT] ?: 0,
        )
    }

    /**
     * @param following vero solo quando il posto arriva dal telefono. Sceglierlo
     *   a mano spegne il seguire, e non e' un dettaglio: una scelta esplicita
     *   deve vincere su un rilevamento, altrimenti al riavvio successivo si
     *   verrebbe riportati dove si e' invece che dove si e' chiesto.
     */
    suspend fun setPlace(place: Place, following: Boolean = false) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_FOLLOWS] = following
            prefs[KEY_NAME] = place.name
            prefs[KEY_LAT] = place.latitude
            prefs[KEY_LON] = place.longitude
            place.admin?.let { prefs[KEY_ADMIN] = it } ?: prefs.remove(KEY_ADMIN)
            place.country?.let { prefs[KEY_COUNTRY] = it } ?: prefs.remove(KEY_COUNTRY)
        }
    }

    suspend fun setWelcomed() {
        context.settingsDataStore.edit { it[KEY_WELCOMED] = true }
    }

    suspend fun setUnit(unit: TempUnit) {
        context.settingsDataStore.edit { it[KEY_UNIT] = unit.name }
    }

    suspend fun setModel(model: WeatherModel) {
        context.settingsDataStore.edit { it[KEY_MODEL] = model.name }
    }

    /** Aggiunge o toglie una localita' dai preferiti, a seconda che ci sia gia'. */
    suspend fun toggleFavorite(place: Place) {
        context.settingsDataStore.edit { prefs ->
            val current = decodeFavorites(prefs[KEY_FAVORITES])
            val updated = if (current.any { it.key == place.key }) {
                current.filterNot { it.key == place.key }
            } else {
                current + place
            }
            prefs[KEY_FAVORITES] = favoritesJson.encodeToString(updated)
        }
    }

    /**
     * Riduce la fascia dell'allerta a un pallino, ricordando per cosa.
     *
     * @param ids gli identificativi delle allerte in scena in questo momento.
     * @param weight il peso del livello peggiore fra quelle.
     */
    suspend fun dismissAlerts(ids: Set<String>, weight: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_ALERTS_DISMISSED] = ids
            prefs[KEY_ALERTS_WEIGHT] = weight
        }
    }

    /**
     * Rimette la fascia intera.
     *
     * Si svuota tutto invece di togliere un identificativo per volta: chi
     * riapre la fascia sta dicendo che la vuole vedere, e ricordarsi di
     * un'allerta chiusa la settimana scorsa servirebbe solo a nasconderne una
     * di nuovo.
     */
    suspend fun restoreAlertBar() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(KEY_ALERTS_DISMISSED)
            prefs.remove(KEY_ALERTS_WEIGHT)
        }
    }

    private fun decodeFavorites(raw: String?): List<Place> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { favoritesJson.decodeFromString<List<Place>>(raw) }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY_NAME = stringPreferencesKey("localita_nome")
        val KEY_ADMIN = stringPreferencesKey("localita_regione")
        val KEY_COUNTRY = stringPreferencesKey("localita_paese")
        val KEY_LAT = doublePreferencesKey("localita_lat")
        val KEY_LON = doublePreferencesKey("localita_lon")
        val KEY_UNIT = stringPreferencesKey("unita")
        val KEY_FOLLOWS = booleanPreferencesKey("segue_posizione")
        val KEY_WELCOMED = booleanPreferencesKey("benvenuto_fatto")
        val KEY_MODEL = stringPreferencesKey("modello")
        val KEY_FAVORITES = stringPreferencesKey("preferiti")
        val KEY_ALERTS_DISMISSED = stringSetPreferencesKey("allerte_chiuse")
        val KEY_ALERTS_WEIGHT = intPreferencesKey("allerte_chiuse_peso")
    }
}
