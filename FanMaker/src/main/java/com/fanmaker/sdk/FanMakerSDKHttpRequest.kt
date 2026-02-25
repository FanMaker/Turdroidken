package com.fanmaker.sdk

import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.JsonObjectRequest
import org.json.JSONObject

open class FanMakerSDKHttpRequest(
    val fanMakerSDK: FanMakerSDK,
    method: Int,
    path: String?,
    jsonRequest: JSONObject?,
    val token: String? = "",
    val tokenType: FanMakerSDKTokenType? = null,
    listener: Response.Listener<JSONObject>?,
    errorListener: Response.ErrorListener?,
) : JsonObjectRequest(method, "$URL/$path", jsonRequest, listener, errorListener) {
    init {
        retryPolicy = DefaultRetryPolicy(
            fanMakerSDK.requestTimeoutMs,
            fanMakerSDK.requestMaxRetries,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
    }
    open fun getFanMakerToken(): String {
        if (token != null && token != "") { return token }
        return fanMakerSDK.apiKey
    }

    override fun getHeaders(): MutableMap<String, String> {
        val headers = HashMap<String, String>()
        val rawToken = getFanMakerToken()

        if (tokenType != null) {
            when (tokenType) {
                is FanMakerSDKTokenType.OAuthToken -> {
                    // OAuth: only send Bearer access token via Authorization header.
                    // Do NOT send X-FanMaker-Token or X-FanMaker-SessionToken to avoid
                    // confusing the API's token lookup.
                    headers["Authorization"] = FanMakerSDKTokenResolver.authorizationHeaderValue(tokenType)
                }
                is FanMakerSDKTokenType.ApiToken -> {
                    // Plain API token: same as legacy behavior
                    headers["X-FanMaker-Token"] = rawToken
                    headers["Authorization"] = rawToken
                }
            }
        } else {
            // No token type resolved: legacy behavior
            headers["X-FanMaker-Token"] = rawToken
            headers["Authorization"] = rawToken
        }

        headers["X-FanMaker-Mode"] = "sdk"
        headers["X-FanMaker-SDK-Version"] = fanMakerSDK.version

        return headers
    }

    // By default, empty responses are considered errors but successful POST responses can
    // be empty and still be successfull (status 202, 204, etc)
    override fun parseNetworkResponse(response: NetworkResponse): Response<JSONObject> {
        return if (
            method == Method.POST &&
            response.statusCode >= 200 &&
            response.statusCode <= 300 &&
            response.data.size <= 1
        ) {
            Response.success(JSONObject("{\"status\":200}"), HttpHeaderParser.parseCacheHeaders(response))
        } else {
            super.parseNetworkResponse(response)
        }
    }

    companion object {
        const val API_BASE = "https://api3.fanmaker.com"
        const val URL = "$API_BASE/api/v3"
    }
}
