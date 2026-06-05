package com.jussicodes.easytimer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "easytimer_prefs")

class PreferencesRepository(private val context: Context) {

    companion object {
        private val FAVORITES_KEY = stringSetPreferencesKey("favorite_packages")
        private val RECENT_KEY = stringSetPreferencesKey("recent_packages")
        private val LAST_DURATION_KEY = intPreferencesKey("last_duration_minutes")
        private val SELF_DESTRUCT_KEY = booleanPreferencesKey("selfdestruct_enabled")
    }

    val favoritePackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[FAVORITES_KEY] ?: emptySet()
    }

    val recentPackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[RECENT_KEY] ?: emptySet()
    }

    val lastDuration: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LAST_DURATION_KEY] ?: 15
    }

    val selfDestructEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SELF_DESTRUCT_KEY] == true
    }

    suspend fun addFavorite(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY] ?: emptySet()
            prefs[FAVORITES_KEY] = current + packageName
        }
    }

    suspend fun removeFavorite(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY] ?: emptySet()
            prefs[FAVORITES_KEY] = current - packageName
        }
    }

    suspend fun addRecent(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[RECENT_KEY] ?: emptySet()
            prefs[RECENT_KEY] = (linkedSetOf(packageName) + current).take(10).toSet()
        }
    }

    suspend fun setLastDuration(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[LAST_DURATION_KEY] = minutes
        }
    }

    suspend fun setSelfDestructEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SELF_DESTRUCT_KEY] = enabled
        }
    }
}
