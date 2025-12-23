package com.fanmaker.sdk

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
    listener: Response.Listener<JSONObject>?,
    errorListener: Response.ErrorListener?,
) : JsonObjectRequest(method, "$URL/$path", jsonRequest, listener, errorListener) {
    open fun getFanMakerToken(): String {
        if (token != null && token != "") { return token }
        return fanMakerSDK.apiKey
    }

    override fun getHeaders(): MutableMap<String, String> {
        val headers = HashMap<String, String>()
        var fanmaker_token = getFanMakerToken()
        headers["X-FanMaker-Token"] = fanmaker_token
        headers["Authorization"] = fanmaker_token
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
        const val URL = "https://api3.fanmaker.com/api/v3"
    }
}
