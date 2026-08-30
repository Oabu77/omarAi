package com.darcloud.omarai.data.api

import com.darcloud.omarai.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

interface SessionTokenProvider {
    fun currentToken(): String?
}

/** Tokens intentionally remain in memory until a real authentication flow is connected. */
class InMemorySessionTokenProvider : SessionTokenProvider {
    @Volatile private var token: String? = null
    override fun currentToken(): String? = token
    fun update(value: String?) { token = value }
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T, val requestId: String) : ApiResult<T>
    data class Failure(
        val userMessage: String,
        val httpCode: Int? = null,
        val requestId: String? = null,
    ) : ApiResult<Nothing>
}

class OmarApiClient(private val tokenProvider: SessionTokenProvider) {
    val isConfigured: Boolean = BuildConfig.OMAR_API_CONFIGURED
    val hasAuthenticatedSession: Boolean
        get() = !tokenProvider.currentToken().isNullOrBlank()

    private val authInterceptor = Interceptor { chain ->
        val builder = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("X-Omar-Client", "android/${BuildConfig.VERSION_NAME}")
        tokenProvider.currentToken()?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }
        chain.proceed(builder.build())
    }

    private val logging = HttpLoggingInterceptor().apply {
        // Never log prompt, file, auth, purchase-token, or response bodies.
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
        else HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    val api: OmarApi = Retrofit.Builder()
        .baseUrl(BuildConfig.OMAR_API_BASE_URL)
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OmarApi::class.java)

    suspend fun <T> call(block: suspend OmarApi.() -> ApiEnvelope<T>): ApiResult<T> {
        if (!isConfigured) return ApiResult.Failure("Omar AI service is not connected.")
        if (!hasAuthenticatedSession) {
            return ApiResult.Failure("A verified sign-in session is required. This v1 has no sign-in flow.")
        }
        return try {
            val envelope = api.block()
            val data = envelope.data
            if (envelope.ok && data != null) {
                ApiResult.Success(data, envelope.requestId)
            } else {
                ApiResult.Failure(
                    envelope.error?.message ?: "The service returned no usable result.",
                    requestId = envelope.requestId,
                )
            }
        } catch (error: retrofit2.HttpException) {
            val failure = runCatching {
                error.response()?.errorBody()?.string()?.let {
                    moshi.adapter(ApiFailureEnvelope::class.java).fromJson(it)
                }
            }.getOrNull()
            ApiResult.Failure(
                userMessage = failure?.error?.message ?: when (error.code()) {
                    401 -> "Sign in is required."
                    403 -> "This account is not authorized for that action."
                    409 -> "The task changed. Refresh and try again."
                    429 -> "The service is busy. Please wait and retry."
                    else -> "The service rejected the request (${error.code()})."
                },
                httpCode = error.code(),
                requestId = failure?.requestId,
            )
        } catch (_: java.net.SocketTimeoutException) {
            ApiResult.Failure("The request timed out. Nothing was reported as completed.")
        } catch (_: java.io.IOException) {
            ApiResult.Failure("Could not reach the Omar AI service.")
        } catch (_: Exception) {
            ApiResult.Failure("The request failed. Nothing was reported as completed.")
        }
    }

}
