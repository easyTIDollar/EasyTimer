package com.jussicodes.easytimer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "easytimer_prefs")

data class PersistedTimer(
    val packageName: String,
    val appName: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val isPaused: Boolean,
    val endAtMillis: Long
)

class PreferencesRepository(private val context: Context) {

    companion object {
        private val FAVORITES_KEY = stringSetPreferencesKey("favorite_packages")
        private val RECENT_KEY = stringSetPreferencesKey("recent_packages")
        private val LAST_DURATION_KEY = intPreferencesKey("last_duration_minutes")
        private val SELF_DESTRUCT_KEY = booleanPreferencesKey("selfdestruct_enabled")
        private val TIMER_PACKAGE_KEY = stringPreferencesKey("active_timer_package")
        private val TIMER_APP_NAME_KEY = stringPreferencesKey("active_timer_app_name")
        private val TIMER_TOTAL_SECONDS_KEY = intPreferencesKey("active_timer_total_seconds")
        private val TIMER_REMAINING_SECONDS_KEY = intPreferencesKey("active_timer_remaining_seconds")
        private val TIMER_PAUSED_KEY = booleanPreferencesKey("active_timer_paused")
        private val TIMER_END_AT_KEY = longPreferencesKey("active_timer_end_at")
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

    val activeTimer: Flow<PersistedTimer?> = context.dataStore.data.map { prefs ->
        val packageName = prefs[TIMER_PACKAGE_KEY].orEmpty()
        val appName = prefs[TIMER_APP_NAME_KEY].orEmpty()
        val totalSeconds = prefs[TIMER_TOTAL_SECONDS_KEY] ?: 0
        val remainingSeconds = prefs[TIMER_REMAINING_SECONDS_KEY] ?: 0
        val endAtMillis = prefs[TIMER_END_AT_KEY] ?: 0L
        if (packageName.isBlank() || appName.isBlank() || totalSeconds <= 0) {
            null
        } else {
            PersistedTimer(
                packageName = packageName,
                appName = appName,
                totalSeconds = totalSeconds,
                remainingSeconds = remainingSeconds,
                isPaused = prefs[TIMER_PAUSED_KEY] == true,
                endAtMillis = endAtMillis
            )
        }
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

    suspend fun saveActiveTimer(
        packageName: String,
        appName: String,
        totalSeconds: Int,
        remainingSeconds: Int,
        isPaused: Boolean,
        endAtMillis: Long
    ) {
        context.dataStore.edit { prefs ->
            prefs[TIMER_PACKAGE_KEY] = packageName
            prefs[TIMER_APP_NAME_KEY] = appName
            prefs[TIMER_TOTAL_SECONDS_KEY] = totalSeconds
            prefs[TIMER_REMAINING_SECONDS_KEY] = remainingSeconds
            prefs[TIMER_PAUSED_KEY] = isPaused
            prefs[TIMER_END_AT_KEY] = endAtMillis
        }
    }

    suspend fun clearActiveTimer() {
        context.dataStore.edit { prefs ->
            prefs.remove(TIMER_PACKAGE_KEY)
            prefs.remove(TIMER_APP_NAME_KEY)
            prefs.remove(TIMER_TOTAL_SECONDS_KEY)
            prefs.remove(TIMER_REMAINING_SECONDS_KEY)
            prefs.remove(TIMER_PAUSED_KEY)
            prefs.remove(TIMER_END_AT_KEY)
        }
    }
}
