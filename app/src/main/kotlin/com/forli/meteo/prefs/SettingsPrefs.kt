package com.forli.meteo.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.forli.meteo.data.Place
import com.forli.meteo.data.WeatherModel
import com.forli.meteo.data.key
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
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
    val model: WeatherModel = WeatherModel.AUTO,
    val favorites: List<Place> = emptyList(),
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
            model = prefs[KEY_MODEL]
                ?.let { saved -> WeatherModel.entries.firstOrNull { it.name == saved } }
                ?: WeatherModel.AUTO,
            favorites = decodeFavorites(prefs[KEY_FAVORITES]),
        )
    }

    suspend fun setPlace(place: Place) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_NAME] = place.name
            prefs[KEY_LAT] = place.latitude
            prefs[KEY_LON] = place.longitude
            place.admin?.let { prefs[KEY_ADMIN] = it } ?: prefs.remove(KEY_ADMIN)
            place.country?.let { prefs[KEY_COUNTRY] = it } ?: prefs.remove(KEY_COUNTRY)
        }
    }

    suspend fun setUnit(unit: TempUnit) {
        context.settingsDataStore.edit { it[KEY_UNIT] = unit.name }
    }

    suspend fun setModel(model: WeatherModel) {
        context.settingsDataStore.edit { it[KEY_MODEL] = model.name }
    }

    /**
     * Aggiunge o toglie un preferito leggendo lo stato dentro la stessa
     * transazione: due tocchi ravvicinati sulla stella non si devono perdere
     * a vicenda.
     */
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

    private fun decodeFavorites(raw: String?): List<Place> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { favoritesJson.decodeFromString<List<Place>>(raw) }
            .getOrDefault(emptyList())
    }

    private companion object {
        val KEY_NAME = stringPreferencesKey("localita_nome")
        val KEY_ADMIN = stringPreferencesKey("localita_regione")
        val KEY_COUNTRY = stringPreferencesKey("localita_paese")
        val KEY_LAT = doublePreferencesKey("localita_lat")
        val KEY_LON = doublePreferencesKey("localita_lon")
        val KEY_UNIT = stringPreferencesKey("unita")
        val KEY_MODEL = stringPreferencesKey("modello")
        val KEY_FAVORITES = stringPreferencesKey("preferiti")
    }
}
