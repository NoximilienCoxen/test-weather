package io.github.noximiliencoxen.caelum.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.WeatherModel
import io.github.noximiliencoxen.caelum.data.hasFiniteCoordinates
import io.github.noximiliencoxen.caelum.data.key
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

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

/**
 * La carta di Sala: segue l'ora (AUTO), oppure l'interruttore manuale
 * (CHIARO/SCURO) che ha sempre la precedenza.
 */
enum class CardTheme { AUTO, CHIARO, SCURO }

/** L'unita' di velocita' del vento mostrata in Sala VI. */
enum class SalaWindUnit(val label: String) {
    KMH("km/h"),
    MS("m/s"),
    KN("nodi"),
    ;

    /** Da metri al secondo, che e' l'unita' in cui viaggia il dato. */
    fun from(metresPerSecond: Double): Double = when (this) {
        KMH -> metresPerSecond * 3.6
        MS -> metresPerSecond
        KN -> metresPerSecond * 1.9438444924
    }
}

/** Quanto raccontano le didascalie sotto il titolo di ogni sala. */
enum class CaptionStyle { BREVI, COMPLETE }

/** Quali avvisi calcolati sono richiesti, sala per sala. */
data class AlertToggles(
    val pioggiaIntensa: Boolean = true,
    val temporali: Boolean = true,
    val uvAlto: Boolean = true,
    val ventoForte: Boolean = false,
)

enum class AlertToggleKind { PIOGGIA, TEMPORALE, UV, VENTO }

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
    /** La carta: automatica seguendo l'ora, o forzata chiara/scura. */
    val cardTheme: CardTheme = CardTheme.AUTO,
    val windUnit: SalaWindUnit = SalaWindUnit.KMH,
    val captionStyle: CaptionStyle = CaptionStyle.COMPLETE,
    val alertToggles: AlertToggles = AlertToggles(),
    /**
     * Ferma cio' che in Sala si muove da solo: le stelle, gli uccelli, cio' che
     * cade, e le vibrazioni che ne seguono.
     *
     * Spenta di norma. Esiste perche' quel movimento e' **un'eccezione
     * dichiarata** alla regola per cui a schermo fermo l'app disegna zero
     * fotogrammi: finche' la prima sala e' in vista, un orologio gira. Una
     * regola con un'eccezione e nessuna via d'uscita e' una regola dichiarata a
     * meta'.
     */
    val animazioniRidotte: Boolean = false,
)

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "impostazioni")

private val favoritesJson = Json { ignoreUnknownKeys = true }

class SettingsPrefs(private val context: Context) {

    /**
     * Le impostazioni, e cosa succede se il file non si legge.
     *
     * **Il `catch` non e' cintura e bretelle: senza, un file illeggibile e' un
     * crash all'avvio.** Questo Flow lo raccoglie `WeatherViewModel` dentro un
     * `launch` che non ha try/catch, quindi un `IOException` qui - disco pieno
     * a meta' scrittura, permessi cambiati, file troncato da uno spegnimento
     * brusco - non ha nessuno che lo prenda e porta giu' il processo. La prima
     * apertura dopo il guasto e' anche l'ultima.
     *
     * Con il ripiego su `emptyPreferences()` l'app riparte invece dai valori
     * predefiniti: si perdono le scelte, il che si vede e si rimedia
     * riscegliendole, invece di un'app che non si apre piu' e non dice perche'.
     *
     * **Solo `IOException`.** Il resto - un tipo sbagliato, un errore di
     * programmazione - viene rilanciato: quello non e' un file rovinato, e'
     * un difetto, e coprirlo con i valori predefiniti lo renderebbe invisibile.
     */
    val settings: Flow<Settings> = context.settingsDataStore.data.catch { cause ->
        if (cause is IOException) emit(emptyPreferences()) else throw cause
    }.map { prefs ->
        val latitude = prefs[KEY_LAT]
        val longitude = prefs[KEY_LON]
        val name = prefs[KEY_NAME]
        // Nome e coordinate si salvano insieme e si rileggono insieme: una meta'
        // sola descriverebbe un posto che non e' ne' quello scelto ne' il
        // predefinito.
        //
        // E si rilegge solo se le coordinate sono numeri veri: una localita'
        // con `NaN` dentro e' stata scritta qui davvero, da un rilevamento
        // dell'emulatore, e da sola non se ne sarebbe mai andata - a ogni
        // avvio l'app la rileggeva, la mandava alla rete e falliva. Un
        // predefinito e' meglio di un posto che non esiste.
        val place = if (latitude != null && longitude != null && name != null) {
            Place(
                name = name,
                admin = prefs[KEY_ADMIN],
                country = prefs[KEY_COUNTRY],
                latitude = latitude,
                longitude = longitude,
            ).takeIf { it.hasFiniteCoordinates } ?: Place.FORLI
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
            cardTheme = prefs[KEY_CARD_THEME]
                ?.let { saved -> CardTheme.entries.firstOrNull { it.name == saved } }
                ?: CardTheme.AUTO,
            windUnit = prefs[KEY_WIND_UNIT]
                ?.let { saved -> SalaWindUnit.entries.firstOrNull { it.name == saved } }
                ?: SalaWindUnit.KMH,
            captionStyle = prefs[KEY_CAPTION_STYLE]
                ?.let { saved -> CaptionStyle.entries.firstOrNull { it.name == saved } }
                ?: CaptionStyle.COMPLETE,
            animazioniRidotte = prefs[KEY_ANIMAZIONI_RIDOTTE] ?: false,
            alertToggles = AlertToggles(
                pioggiaIntensa = prefs[KEY_ALERT_PIOGGIA] ?: true,
                temporali = prefs[KEY_ALERT_TEMPORALE] ?: true,
                uvAlto = prefs[KEY_ALERT_UV] ?: true,
                ventoForte = prefs[KEY_ALERT_VENTO] ?: false,
            ),
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

    suspend fun setCardTheme(theme: CardTheme) {
        context.settingsDataStore.edit { it[KEY_CARD_THEME] = theme.name }
    }

    suspend fun setWindUnit(unit: SalaWindUnit) {
        context.settingsDataStore.edit { it[KEY_WIND_UNIT] = unit.name }
    }

    suspend fun setCaptionStyle(style: CaptionStyle) {
        context.settingsDataStore.edit { it[KEY_CAPTION_STYLE] = style.name }
    }

    suspend fun setAnimazioniRidotte(ridotte: Boolean) {
        context.settingsDataStore.edit { it[KEY_ANIMAZIONI_RIDOTTE] = ridotte }
    }

    suspend fun setAlertToggle(kind: AlertToggleKind, value: Boolean) {
        val key = when (kind) {
            AlertToggleKind.PIOGGIA -> KEY_ALERT_PIOGGIA
            AlertToggleKind.TEMPORALE -> KEY_ALERT_TEMPORALE
            AlertToggleKind.UV -> KEY_ALERT_UV
            AlertToggleKind.VENTO -> KEY_ALERT_VENTO
        }
        context.settingsDataStore.edit { it[key] = value }
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
        val KEY_CARD_THEME = stringPreferencesKey("sala_carta")
        val KEY_WIND_UNIT = stringPreferencesKey("sala_unita_vento")
        val KEY_CAPTION_STYLE = stringPreferencesKey("sala_didascalie")
        val KEY_ANIMAZIONI_RIDOTTE = booleanPreferencesKey("sala_animazioni_ridotte")
        val KEY_ALERT_PIOGGIA = booleanPreferencesKey("sala_avviso_pioggia")
        val KEY_ALERT_TEMPORALE = booleanPreferencesKey("sala_avviso_temporale")
        val KEY_ALERT_UV = booleanPreferencesKey("sala_avviso_uv")
        val KEY_ALERT_VENTO = booleanPreferencesKey("sala_avviso_vento")
    }
}
