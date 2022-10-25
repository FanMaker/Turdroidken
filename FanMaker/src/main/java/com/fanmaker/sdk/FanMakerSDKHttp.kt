package com.fanmaker.sdk

import android.content.Context
import android.util.Log
import com.android.volley.toolbox.Volley
import com.android.volley.Request.Method

import org.json.JSONObject

class FanMakerSDKHttp(val context: Context) {
    val queue = Volley.newRequestQueue(context)

    fun get(path: String, onSuccess: (JSONObject) -> Unit, onError: (Int, String) -> Unit) {
        val request = FanMakerSDKHttpRequest(Method.GET, path, null,
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
        val request = object: FanMakerSDKHttpRequest(Method.POST, path, body,
            { response ->
                val status = response.getInt("status")
                if (status >= 200  && status <= 300) {
                    onSuccess(response)
                } else {
                    onError(status, response.getString("message").toString())
                }
            },
            { error ->
                try {
                    onError(error.networkResponse.statusCode, error.message.toString())
                } catch (err: java.lang.Exception) {
                    Log.e(TAG, err.localizedMessage)
                }
            }
        ) {
            override fun getFanMakerToken(): String {
                val settings = context.getSharedPreferences("com.fanmaker.sdk", Context.MODE_PRIVATE)
                return settings.getString("token", super.getFanMakerToken()).toString()
            }
        }
        queue.add(request)
    }

    companion object {
        const val TAG = "FanMakerSDKHttp"
    }
}