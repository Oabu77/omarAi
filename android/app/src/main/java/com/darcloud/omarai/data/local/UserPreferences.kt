package com.darcloud.omarai.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "omar_preferences")

class UserPreferences(private val context: Context) {
    private val onboardingCompleteKey = booleanPreferencesKey("onboarding_complete")

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map {
        it[onboardingCompleteKey] ?: false
    }

    suspend fun setOnboardingComplete(value: Boolean) {
        context.dataStore.edit { it[onboardingCompleteKey] = value }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
