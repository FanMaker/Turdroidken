package com.fanmaker.sdk

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// MARK: - OAuth Token Data Model

/**
 * Represents the parsed JSON payload of an OAuth access token from Doorkeeper.
 * `expires_at` is optional because standard Doorkeeper responses don't always include it;
 * when missing it is computed from `created_at + expires_in`.
 */
data class FanMakerSDKOAuthToken(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Int,
    val created_at: Int,
    private val _expires_at: Int? = null
) {
    /** Unix timestamp when the access token expires. Computed if not provided. */
    val expires_at: Int
        get() = _expires_at ?: (created_at + expires_in)

    /**
     * Returns a JSON string representation suitable for storing back in SharedPreferences
     * or sending as a header value. Always includes `expires_at` (computed if needed).
     */
    fun toJSONString(): String? {
        return try {
            val json = JSONObject()
            json.put("access_token", access_token)
            json.put("refresh_token", refresh_token)
            json.put("expires_in", expires_in)
            json.put("expires_at", expires_at)
            json.put("created_at", created_at)
            json.toString()
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /**
         * Attempts to parse a JSON string into an [FanMakerSDKOAuthToken].
         * Returns null if the string is not valid OAuth token JSON.
         */
        fun fromJSON(jsonString: String): FanMakerSDKOAuthToken? {
            return try {
                val json = JSONObject(jsonString)
                // All required fields must be present
                if (!json.has("access_token") || !json.has("refresh_token") ||
                    !json.has("expires_in") || !json.has("created_at")) {
                    return null
                }
                FanMakerSDKOAuthToken(
                    access_token = json.getString("access_token"),
                    refresh_token = json.getString("refresh_token"),
                    expires_in = json.getInt("expires_in"),
                    created_at = json.getInt("created_at"),
                    _expires_at = if (json.has("expires_at") && !json.isNull("expires_at"))
                        json.getInt("expires_at") else null
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

// MARK: - Token Type

/**
 * Represents the two possible token types the SDK may encounter.
 */
sealed class FanMakerSDKTokenType {
    /** A plain API token string (e.g., "a8df7897dfaiod7faehrl") */
    data class ApiToken(val token: String) : FanMakerSDKTokenType()
    /** An OAuth token parsed from a JSON string */
    data class OAuthToken(val oauthToken: FanMakerSDKOAuthToken) : FanMakerSDKTokenType()
}

// MARK: - Token Resolver

/**
 * Utility for detecting token types, checking expiration, building header values,
 * and refreshing expired OAuth tokens via Doorkeeper.
 *
 * Refresh requests are coalesced: if multiple callers request a refresh at the same time,
 * only one network request is made and the result is shared with all waiting callers.
 */
object FanMakerSDKTokenResolver {
    private const val TAG = "FanMakerSDKToken"

    /**
     * Buffer in seconds before `expires_at` to consider the token expired,
     * avoiding edge-case races where the token expires mid-flight.
     */
    private const val EXPIRATION_BUFFER_SECONDS = 30

    // MARK: - Refresh Coalescing State

    /** Lock protecting the refresh queue state. */
    private val refreshLock = ReentrantLock()
    /** Whether a refresh network request is currently in-flight. */
    private var isRefreshing = false
    /** Queued completions waiting for the in-flight refresh to finish. */
    private val pendingRefreshCompletions = mutableListOf<(Result<FanMakerSDKOAuthToken>) -> Unit>()

    // MARK: - Token Type Detection

    /**
     * Attempts to parse the token string as an OAuth JSON payload.
     * Falls back to [FanMakerSDKTokenType.ApiToken] if JSON decoding fails.
     */
    fun resolve(tokenString: String): FanMakerSDKTokenType {
        val oauthToken = FanMakerSDKOAuthToken.fromJSON(tokenString)
        return if (oauthToken != null) {
            FanMakerSDKTokenType.OAuthToken(oauthToken)
        } else {
            FanMakerSDKTokenType.ApiToken(tokenString)
        }
    }

    // MARK: - Expiration Check

    /**
     * Returns `true` if the OAuth token has expired (or will expire within the buffer window).
     */
    fun isExpired(token: FanMakerSDKOAuthToken): Boolean {
        val nowSeconds = System.currentTimeMillis() / 1000
        return nowSeconds >= (token.expires_at - EXPIRATION_BUFFER_SECONDS)
    }

    // MARK: - Header Value Builders

    /**
     * Returns the value for the `Authorization` header.
     * - For OAuth tokens: `"Bearer <access_token>"`
     * - For API tokens: the raw token string as-is
     */
    fun authorizationHeaderValue(tokenType: FanMakerSDKTokenType): String {
        return when (tokenType) {
            is FanMakerSDKTokenType.ApiToken -> tokenType.token
            is FanMakerSDKTokenType.OAuthToken -> {
                val accessToken = tokenType.oauthToken.access_token
                if (accessToken.lowercase().startsWith("bearer ")) {
                    accessToken
                } else {
                    "Bearer $accessToken"
                }
            }
        }
    }

    /**
     * Returns the value for the `X-FanMaker-SessionToken` header.
     * - For OAuth tokens: the full JSON string (re-encoded to include refreshed data)
     * - For API tokens: the raw token string as-is
     */
    fun sessionTokenHeaderValue(tokenType: FanMakerSDKTokenType, rawTokenString: String): String {
        return when (tokenType) {
            is FanMakerSDKTokenType.ApiToken -> tokenType.token
            is FanMakerSDKTokenType.OAuthToken -> {
                // Prefer re-encoding the token to ensure consistency after a refresh,
                // but fall back to the raw string if encoding fails.
                tokenType.oauthToken.toJSONString() ?: rawTokenString
            }
        }
    }

    // MARK: - High-Level Token Resolution

    /**
     * Resolves the token type, checks expiration for OAuth tokens, refreshes if needed,
     * and returns a valid token type ready to use for headers.
     *
     * @param tokenString The raw token string from SharedPreferences
     * @param apiBase The base URL for the API (e.g., "https://api3.fanmaker.com")
     * @param onRefreshed Called with the new JSON string when a token is refreshed,
     *                    so the caller can persist it back to storage
     * @param onSuccess Called with the valid token type
     * @param onError Called with error code and message if refresh fails
     */
    fun getValidToken(
        tokenString: String,
        apiBase: String,
        onRefreshed: (String) -> Unit,
        onSuccess: (FanMakerSDKTokenType) -> Unit,
        onError: (Int, String) -> Unit
    ) {
        val tokenType = resolve(tokenString)

        when (tokenType) {
            is FanMakerSDKTokenType.ApiToken -> onSuccess(tokenType)

            is FanMakerSDKTokenType.OAuthToken -> {
                if (!isExpired(tokenType.oauthToken)) {
                    onSuccess(tokenType)
                } else {
                    Log.w(TAG, "OAuth token expired, initiating refresh...")
                    // Token is expired -- enqueue refresh (coalesced with other callers)
                    enqueueRefresh(tokenType.oauthToken, apiBase) { result ->
                        result.fold(
                            onSuccess = { newToken ->
                                // Notify caller so they can persist the new token
                                newToken.toJSONString()?.let { onRefreshed(it) }
                                onSuccess(FanMakerSDKTokenType.OAuthToken(newToken))
                            },
                            onFailure = { error ->
                                onError(0, error.message ?: "Token refresh failed")
                            }
                        )
                    }
                }
            }
        }
    }

    // MARK: - Token Refresh (Coalesced)

    /**
     * Enqueues a refresh request. If a refresh is already in-flight, the completion
     * is queued and will be called with the result of the in-flight request.
     * Only one network request is made regardless of how many callers need a refresh.
     */
    private fun enqueueRefresh(
        token: FanMakerSDKOAuthToken,
        apiBase: String,
        completion: (Result<FanMakerSDKOAuthToken>) -> Unit
    ) {
        refreshLock.withLock {
            if (isRefreshing) {
                // A refresh is already in-flight -- just queue up and wait
                val queueSize = pendingRefreshCompletions.size + 1
                Log.d(TAG, "Refresh already in-flight, queuing caller ($queueSize waiting)")
                pendingRefreshCompletions.add(completion)
                return
            }
            // We're the first -- mark as refreshing
            isRefreshing = true
        }

        executeRefresh(token, apiBase) { result ->
            // Grab all pending completions and reset state
            val waitingCompletions: List<(Result<FanMakerSDKOAuthToken>) -> Unit>
            refreshLock.withLock {
                waitingCompletions = pendingRefreshCompletions.toList()
                pendingRefreshCompletions.clear()
                isRefreshing = false
            }

            val totalCallers = waitingCompletions.size + 1
            Log.d(TAG, "Refresh complete, notifying $totalCallers caller(s)")

            // Notify the original caller
            completion(result)
            // Notify all queued callers with the same result
            for (waiting in waitingCompletions) {
                waiting(result)
            }
        }
    }

    /**
     * Executes the actual network request to refresh the OAuth token via Doorkeeper.
     * Only called by [enqueueRefresh] -- never directly.
     *
     * POST /oauth/token
     * Body: { "grant_type": "refresh_token", "refresh_token": "<refresh_token>" }
     */
    private fun executeRefresh(
        token: FanMakerSDKOAuthToken,
        apiBase: String,
        completion: (Result<FanMakerSDKOAuthToken>) -> Unit
    ) {
        Thread {
            try {
                val urlString = "$apiBase/oauth/token"
                Log.d(TAG, "Refreshing OAuth token...")

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val body = JSONObject()
                body.put("grant_type", "refresh_token")
                body.put("refresh_token", token.refresh_token)

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Refresh response HTTP $responseCode")

                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val responseBody = reader.readText()
                    reader.close()

                    val newToken = FanMakerSDKOAuthToken.fromJSON(responseBody)
                    if (newToken != null) {
                        Log.w(TAG, "OAuth token refreshed successfully")
                        completion(Result.success(newToken))
                    } else {
                        Log.e(TAG, "OAuth token refresh failed: could not parse response")
                        completion(Result.failure(Exception("Failed to decode refreshed token")))
                    }
                } else {
                    val errorStream = connection.errorStream ?: connection.inputStream
                    val errorReader = BufferedReader(InputStreamReader(errorStream))
                    val errorBody = errorReader.readText()
                    errorReader.close()

                    var errorMessage = "Token refresh failed with HTTP $responseCode"
                    try {
                        val errorJson = JSONObject(errorBody)
                        if (errorJson.has("error")) {
                            errorMessage += ": ${errorJson.getString("error")}"
                            if (errorJson.has("error_description")) {
                                errorMessage += " - ${errorJson.getString("error_description")}"
                            }
                        }
                    } catch (_: Exception) { }

                    Log.e(TAG, "OAuth token refresh failed: $errorMessage")
                    completion(Result.failure(Exception(errorMessage)))
                }

                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "OAuth token refresh failed (network): ${e.localizedMessage}")
                completion(Result.failure(Exception("Token refresh network error: ${e.localizedMessage}")))
            }
        }.start()
    }
}
