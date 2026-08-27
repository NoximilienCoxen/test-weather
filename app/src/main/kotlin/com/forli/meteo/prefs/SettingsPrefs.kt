package com.forli.meteo.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.forli.meteo.data.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
     * Vero dopo che il benvenuto e' stato superato, in un modo o nell'altro.
     *
     * Superato, non "accettato": chi salta la geolocalizzazione ha comunque
     * risposto, e riproporgli la stessa domanda a ogni apertura sarebbe
     * chiedergli di rispondere di no per sempre.
     */
    val welcomed: Boolean = false,
    /**
     * Vero se l'ultima localita' scelta l'ha trovata il telefono, non l'utente.
     *
     * Serve alle impostazioni, che altrimenti mostrerebbero un puntino di
     * selezione su nessuna delle scorciatoie senza spiegare perche'.
     */
    val located: Boolean = false,
)

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "impostazioni")

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
            welcomed = prefs[KEY_WELCOMED] ?: false,
            located = prefs[KEY_LOCATED] ?: false,
        )
    }

    suspend fun setPlace(place: Place, located: Boolean = false) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_NAME] = place.name
            prefs[KEY_LAT] = place.latitude
            prefs[KEY_LON] = place.longitude
            prefs[KEY_LOCATED] = located
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

    private companion object {
        val KEY_NAME = stringPreferencesKey("localita_nome")
        val KEY_ADMIN = stringPreferencesKey("localita_regione")
        val KEY_COUNTRY = stringPreferencesKey("localita_paese")
        val KEY_LAT = doublePreferencesKey("localita_lat")
        val KEY_LON = doublePreferencesKey("localita_lon")
        val KEY_UNIT = stringPreferencesKey("unita")
        val KEY_WELCOMED = booleanPreferencesKey("benvenuto_fatto")
        val KEY_LOCATED = booleanPreferencesKey("localita_dal_telefono")
    }
}
