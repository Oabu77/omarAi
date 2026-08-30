package com.darcloud.omarai.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "omar_preferences")

class UserPreferences(private val context: Context) {
    private val onboardingVersionKey = intPreferencesKey("onboarding_version")
    private val conversationIdKey = stringPreferencesKey("active_conversation_id")

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map {
        (it[onboardingVersionKey] ?: 0) >= REQUIRED_ONBOARDING_VERSION
    }

    suspend fun setOnboardingComplete(value: Boolean) {
        context.dataStore.edit {
            if (value) it[onboardingVersionKey] = REQUIRED_ONBOARDING_VERSION else it.remove(onboardingVersionKey)
        }
    }

    suspend fun activeConversationId(): String {
        context.dataStore.data.first()[conversationIdKey]?.let { return it }
        val created = UUID.randomUUID().toString()
        context.dataStore.edit { preferences ->
            if (preferences[conversationIdKey] == null) preferences[conversationIdKey] = created
        }
        return context.dataStore.data.first()[conversationIdKey] ?: created
    }

    suspend fun startNewConversation(): String {
        val created = UUID.randomUUID().toString()
        context.dataStore.edit { it[conversationIdKey] = created }
        return created
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        const val REQUIRED_ONBOARDING_VERSION = 2
    }
}
