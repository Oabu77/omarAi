package com.darcloud.omarai.data.auth

import android.content.Context
import com.darcloud.omarai.BuildConfig
import com.darcloud.omarai.data.api.SessionTokenProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

enum class SessionState { DISCONNECTED, CONNECTING, AUTHENTICATED, FAILED }

/**
 * Creates a Firebase anonymous account for each installation and refreshes its signed ID token
 * before protected API calls. Firebase persists and rotates the underlying session; raw tokens
 * are never written by Omar AI or included in logs.
 */
class FirebaseSessionTokenProvider(context: Context) : SessionTokenProvider {
    private val mutableState = MutableStateFlow(SessionState.DISCONNECTED)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    override val isConfigured: Boolean = listOf(
        BuildConfig.FIREBASE_API_KEY,
        BuildConfig.FIREBASE_PROJECT_ID,
        BuildConfig.FIREBASE_APP_ID,
    ).all(String::isNotBlank)

    @Volatile private var token: String? = null

    private val auth: FirebaseAuth? = if (isConfigured) {
        val app = runCatching { FirebaseApp.getInstance(APP_NAME) }.getOrElse {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .build(),
                APP_NAME,
            )
        }
        app?.let(FirebaseAuth::getInstance)
    } else {
        null
    }

    override fun currentToken(): String? = token

    override suspend fun ensureValidToken(): String? {
        val firebaseAuth = auth ?: run {
            mutableState.value = SessionState.DISCONNECTED
            return null
        }
        mutableState.value = SessionState.CONNECTING
        return try {
            val user = firebaseAuth.currentUser ?: firebaseAuth.signInAnonymously().await().user
            token = user?.getIdToken(false)?.await()?.token
            mutableState.value = if (token.isNullOrBlank()) SessionState.FAILED else SessionState.AUTHENTICATED
            token
        } catch (_: Exception) {
            token = null
            mutableState.value = SessionState.FAILED
            null
        }
    }

    fun signOut() {
        auth?.signOut()
        token = null
        mutableState.value = SessionState.DISCONNECTED
    }

    private companion object {
        const val APP_NAME = "omar-ai"
    }
}
