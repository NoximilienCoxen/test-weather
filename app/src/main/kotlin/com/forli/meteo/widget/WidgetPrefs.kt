package com.forli.meteo.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.forli.meteo.data.Place
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Chiavi delle preferenze salvate per singola istanza di widget.
 *
 * Vivono nello store di Glance ([androidx.glance.appwidget.state.PreferencesGlanceStateDefinition]),
 * gia' automaticamente separato per `GlanceId`: non serve un DataStore o una
 * tabella Room dedicati.
 */
object WidgetPrefKeys {
    val USE_LOCATION = booleanPreferencesKey("use_location")
    val PLACE_JSON = stringPreferencesKey("place_json")
    val BACKGROUND_ARGB = intPreferencesKey("background_argb")
    val ACCENT_ARGB = intPreferencesKey("accent_argb")
}

private val widgetJson = Json { ignoreUnknownKeys = true }

/** Configurazione risolta di un widget: dove guarda e con che colori. */
data class WidgetConfig(
    val useLocation: Boolean,
    val place: Place?,
    val background: Int?,
    val accent: Int?,
)

fun Preferences.toWidgetConfig(): WidgetConfig = WidgetConfig(
    useLocation = this[WidgetPrefKeys.USE_LOCATION] ?: false,
    place = this[WidgetPrefKeys.PLACE_JSON]?.let {
        runCatching { widgetJson.decodeFromString<Place>(it) }.getOrNull()
    },
    background = this[WidgetPrefKeys.BACKGROUND_ARGB],
    accent = this[WidgetPrefKeys.ACCENT_ARGB],
)

fun placeToJson(place: Place): String = widgetJson.encodeToString(place)
