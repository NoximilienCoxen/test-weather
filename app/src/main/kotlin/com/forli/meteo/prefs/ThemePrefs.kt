package com.forli.meteo.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { AUTO, CHIARO, SCURO }

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "impostazioni")

/** Persiste la sola scelta di tema. Default: segue il sistema. */
class ThemePrefs(private val context: Context) {

    val mode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_MODE]?.let { saved ->
            ThemeMode.entries.firstOrNull { it.name == saved }
        } ?: ThemeMode.AUTO
    }

    suspend fun setMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[KEY_MODE] = mode.name }
    }

    private companion object {
        val KEY_MODE = stringPreferencesKey("tema")
    }
}
