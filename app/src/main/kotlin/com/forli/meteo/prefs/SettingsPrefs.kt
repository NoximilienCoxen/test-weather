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
    /**
     * Le localita' messe da parte, nell'ordine in cui sono state aggiunte.
     *
     * Una lista e non un insieme, e non e' pignoleria: `stringSetPreferencesKey`
     * sarebbe stata la strada breve, ma un insieme non ha ordine, e l'ordine qui
     * e' l'unica cosa che distingue "il primo che ho salvato" da "l'ultimo".
     * Riordinati a ogni lettura, i preferiti ballerebbero sotto il dito.
     */
    val favourites: List<Place> = emptyList(),
)

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "impostazioni")

class SettingsPrefs(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

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
            // Se il testo salvato non si legge piu' - una versione vecchia, un
            // troncamento - si riparte da un elenco vuoto invece di far morire
            // l'intero flusso delle preferenze, che porterebbe giu' anche
            // localita' e unita'.
            favourites = prefs[KEY_FAVOURITES]
                ?.let { saved -> runCatching { json.decodeFromString<List<Place>>(saved) }.getOrNull() }
                .orEmpty(),
        )
    }

    /**
     * Aggiunge una localita' ai preferiti, se non c'e' gia'.
     *
     * Il confronto e' sulle **coordinate**, non sul nome: la stessa citta'
     * arriva dalla ricerca con un nome e dalla geolocalizzazione con un altro,
     * e confrontando i nomi si finirebbe con due voci per lo stesso posto.
     */
    suspend fun addFavourite(place: Place) {
        context.settingsDataStore.edit { prefs ->
            val current = read(prefs)
            if (current.any { it.samePlaceAs(place) }) return@edit
            prefs[KEY_FAVOURITES] = json.encodeToString(current + place)
        }
    }

    suspend fun removeFavourite(place: Place) {
        context.settingsDataStore.edit { prefs ->
            val kept = read(prefs).filterNot { it.samePlaceAs(place) }
            prefs[KEY_FAVOURITES] = json.encodeToString(kept)
        }
    }

    private fun read(prefs: Preferences): List<Place> =
        prefs[KEY_FAVOURITES]
            ?.let { saved -> runCatching { json.decodeFromString<List<Place>>(saved) }.getOrNull() }
            .orEmpty()

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
        val KEY_FAVOURITES = stringPreferencesKey("preferiti")
    }
}
