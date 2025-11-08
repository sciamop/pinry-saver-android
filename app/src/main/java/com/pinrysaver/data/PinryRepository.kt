package com.pinrysaver.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.pinrysaver.data.model.PinryPinsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

class PinryRepository private constructor(private val context: Context) {

    private val gson = Gson()
    private val settingsManager by lazy { SettingsManager(context) }

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BASIC
        builder.addInterceptor(logging)

        builder.build()
    }

    suspend fun fetchPins(offset: Int, limit: Int): PinFetchResult = withContext(Dispatchers.IO) {
        val baseUrl = settingsManager.getPinryUrl().trim().removeSuffix("/")
        if (baseUrl.isEmpty()) {
            return@withContext PinFetchResult.error("Pinry Base URL is not configured")
        }

        val uri = Uri.parse("$baseUrl/api/v2/pins/")
            .buildUpon()
            .appendQueryParameter("limit", limit.toString())
            .appendQueryParameter("offset", offset.toString())
            .appendQueryParameter("ordering", "-id")
            .build()

        val requestBuilder = Request.Builder()
            .url(uri.toString())
            .get()
            .addHeader("Accept", "application/json")

        val token = settingsManager.getApiToken().ifBlank { null }
        token?.let {
            requestBuilder.addHeader("Authorization", "Token $it")
        }

        val request = requestBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    return@withContext PinFetchResult.error(
                        "Server error (${response.code}): ${errorBody.take(200)}"
                    )
                }

                val body = response.body?.string().orEmpty()
                if (body.isEmpty()) {
                    return@withContext PinFetchResult.error("Empty response from server")
                }

                val pinsResponse = gson.fromJson(body, PinryPinsResponse::class.java)
                val pins = pinsResponse.results.orEmpty()
                return@withContext PinFetchResult(
                    success = true,
                    pins = pins,
                    hasMore = pinsResponse.next != null,
                    totalCount = pinsResponse.count,
                    error = null
                )
            }
        } catch (ex: JsonSyntaxException) {
            return@withContext PinFetchResult.error("Failed to parse server response: ${ex.localizedMessage}")
        } catch (ex: IOException) {
            return@withContext PinFetchResult.error("Network error: ${ex.localizedMessage}")
        }
    }

    suspend fun validatePinryServer(baseUrl: String, token: String?): ValidationResult = withContext(Dispatchers.IO) {
        val normalized = baseUrl.trim().removeSuffix("/")
        if (normalized.isEmpty()) {
            return@withContext ValidationResult(
                urlValid = false,
                tokenValid = false,
                requiresToken = false,
                errorMessage = "URL cannot be empty"
            )
        }

        val uri = Uri.parse("$normalized/api/v2/pins/")
            .buildUpon()
            .appendQueryParameter("limit", "1")
            .build()

        val requestBuilder = Request.Builder()
            .url(uri.toString())
            .get()
            .addHeader("Accept", "application/json")

        token?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.addHeader("Authorization", "Token $it")
        }

        val request = requestBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                return@withContext when (response.code) {
                    200 -> ValidationResult(
                        urlValid = true,
                        tokenValid = true,
                        requiresToken = false,
                        errorMessage = null
                    )
                    401 -> {
                        val requiresToken = true
                        val message = if (token.isNullOrBlank()) {
                            "This server requires an API token to access pins."
                        } else {
                            "The provided API token was rejected by the server."
                        }
                        ValidationResult(
                            urlValid = true,
                            tokenValid = false,
                            requiresToken = requiresToken,
                            errorMessage = message
                        )
                    }
                    else -> ValidationResult(
                        urlValid = false,
                        tokenValid = false,
                        requiresToken = false,
                        errorMessage = "Server returned ${response.code}"
                    )
                }
            }
        } catch (ex: IOException) {
            return@withContext ValidationResult(
                urlValid = false,
                tokenValid = false,
                requiresToken = false,
                errorMessage = "Network error: ${ex.localizedMessage}"
            )
        }
    }

    fun getSettings(): SettingsManager = settingsManager

    companion object {
        @Volatile
        private var instance: PinryRepository? = null

        fun getInstance(context: Context): PinryRepository =
            instance ?: synchronized(this) {
                instance ?: PinryRepository(context.applicationContext).also { instance = it }
            }
    }
}

data class PinFetchResult(
    val success: Boolean,
    val pins: List<com.pinrysaver.data.model.PinryPin>,
    val hasMore: Boolean,
    val totalCount: Int,
    val error: String?
) {
    companion object {
        fun error(message: String) = PinFetchResult(
            success = false,
            pins = emptyList(),
            hasMore = false,
            totalCount = 0,
            error = message
        )
    }
}

data class ValidationResult(
    val urlValid: Boolean,
    val tokenValid: Boolean,
    val requiresToken: Boolean,
    val errorMessage: String?
)

