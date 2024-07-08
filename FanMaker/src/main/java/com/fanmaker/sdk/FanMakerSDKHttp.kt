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
        val request = FanMakerSDKHttpRequest(fanMakerSDK, Method.GET, path, null, token,
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
    }

    fun post(
        path: String,
        params: Map<String, Any>,
        onSuccess: (JSONObject) -> Unit,
        onError: (Int, String) -> Unit
    ) {

        val body = JSONObject(params)
        val request = object: FanMakerSDKHttpRequest(fanMakerSDK, Method.POST, path, body, token,
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
    }

    companion object {
        const val TAG = "FanMakerSDKHttp"
    }
}
