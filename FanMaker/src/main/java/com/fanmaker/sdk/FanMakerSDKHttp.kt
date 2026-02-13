package com.fanmaker.sdk

import android.content.Context
import android.util.Log
import com.android.volley.toolbox.Volley
import com.android.volley.Request.Method

import org.json.JSONObject
import java.net.ConnectException

class FanMakerSDKHttp(
    val fanMakerSDK: FanMakerSDK,
    val context: Context,
    val token: String? = ""
) {
    val queue = Volley.newRequestQueue(context)

    fun get(path: String, onSuccess: (JSONObject) -> Unit, onError: (Int, String) -> Unit) {
        resolveTokenAndExecute(
            onReady = { tokenType ->
                val request = FanMakerSDKHttpRequest(fanMakerSDK, Method.GET, path, null, token, tokenType,
                    { response ->
                        val status = response.getInt("status")
                        if (status == 200) {
                            onSuccess(response)
                        } else {
                            val message = response.getString("message")
                            onError(status, message)
                        }
                    },
                    { error ->
                        try {
                            onError(error.networkResponse.statusCode, error.message.toString())
                        } catch (err: java.lang.Exception) {
                            Log.e(TAG, err.localizedMessage)
                        }
                    }
                )
                queue.add(request)
            },
            onError = onError
        )
    }

    fun post(
        path: String,
        params: Map<String, Any>,
        onSuccess: (JSONObject) -> Unit,
        onError: (Int, String) -> Unit
    ) {
        val body = JSONObject(params)
        resolveTokenAndExecute(
            onReady = { tokenType ->
                val request = object: FanMakerSDKHttpRequest(fanMakerSDK, Method.POST, path, body, token, tokenType,
                    { response ->
                        val status = response.getInt("status")
                        if (status >= 200  && status < 300) {
                            onSuccess(response)
                        } else {
                            onError(status, response.getString("message").toString())
                        }
                    },
                    { error ->
                        if (error.networkResponse == null) {
                            onError(0, "Server not found or no Internet connection available")
                        } else {
                            try {
                                onError(error.networkResponse.statusCode, error.message.toString())
                            } catch (err: java.lang.Exception) {
                                Log.e(TAG, err.localizedMessage)
                            }
                        }
                    }
                ) {
                    override fun getFanMakerToken(): String {
                        return super.getFanMakerToken()
                    }
                }
                queue.add(request)
            },
            onError = onError
        )
    }

    /**
     * Resolves the token type (API vs OAuth) before executing the request.
     * If the token is an expired OAuth token, this will refresh it first (coalesced
     * with other concurrent refresh requests) and persist the new token.
     *
     * @param onReady Called with the resolved [FanMakerSDKTokenType] (or null for legacy behavior)
     * @param onError Called if token refresh fails
     */
    private fun resolveTokenAndExecute(
        onReady: (FanMakerSDKTokenType?) -> Unit,
        onError: (Int, String) -> Unit
    ) {
        if (token != null && token.isNotEmpty()) {
            FanMakerSDKTokenResolver.getValidToken(
                tokenString = token,
                apiBase = FanMakerSDKHttpRequest.API_BASE,
                onRefreshed = { newTokenString ->
                    // Persist the refreshed token back to SharedPreferences
                    fanMakerSDK.updateSessionToken(newTokenString)
                },
                onSuccess = { tokenType ->
                    onReady(tokenType)
                },
                onError = onError
            )
        } else {
            // No token provided, use legacy behavior
            onReady(null)
        }
    }

    companion object {
        const val TAG = "FanMakerSDKHttp"
    }
}
