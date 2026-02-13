package com.fanmaker.sdk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.EditText
import androidx.core.content.ContextCompat
import org.json.JSONObject
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

class FanMakerSDKWebInterface(
    private val mContext: Context,
    private val fanMakerSDK: FanMakerSDK,
    private val onRequestLocationAuthorization: (authorized: Boolean) -> Unit,
    private val onUpdateLocation: (location: Location) -> Unit,
    private val onTriggerAction: (action: String, params: HashMap<String, Any>?) -> Unit = { action, params ->
        // Use the callback from SDK instance if available
        fanMakerSDK.onActionTriggered?.invoke(action, params)
    }
) {
    private var locationManager: LocationManager? = null
    private var fanMakerSharedPreferences: FanMakerSharedPreferences = FanMakerSharedPreferences(mContext, fanMakerSDK.apiKey)

    @JavascriptInterface
    fun sdkOpenUrl(url: String) {
        Log.w("FANMAKER", "Opening External Url: " + url)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        mContext.startActivity(intent)
    }

    @JavascriptInterface
    fun triggerAction(action: String, paramsJson: String) {
        val parsedParams: HashMap<String, Any>? = if (paramsJson.isNotEmpty() && paramsJson != "null" && paramsJson != "undefined") {
            try {
                // Check if it's the string representation of an object
                if (paramsJson == "[object Object]") {
                    Log.e("FANMAKER", "Received '[object Object]' string - object was not stringified")
                    null
                } else {
                    val jsonObject = JSONObject(paramsJson)
                    val result = jsonObjectToAnyHashMap(jsonObject)
                    Log.w("FANMAKER", "Successfully parsed JSON string to HashMap: $result")
                    result
                }
            } catch (e: Exception) {
                null
            }
        } else {
            Log.w("FANMAKER", "ParamsJson is empty or null/undefined string")
            null
        }
        onTriggerAction(action, parsedParams)
    }

    @JavascriptInterface
    fun storeSessionToken(token: String) {
        // val settings = mContext.getSharedPreferences("com.fanmaker.sdk", Context.MODE_PRIVATE)
        // val editor = settings.edit()
        // editor.putString("token", token)
        // editor.commit()
        fanMakerSharedPreferences.putString("token", token)
        fanMakerSharedPreferences.commit()
        Log.w("FANMAKER", "SESSION SAVED")
    }

//    This is necessary to convert the arbitrary identifers from a JSON object
//    to a HashMap which is what we are expecting the value to be.
    fun jsonObjectToHashMap(jsonObject: JSONObject): HashMap<String, String> {
        val hashMap = HashMap<String, String>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next() as String
            val value = jsonObject.getString(key)
            hashMap[key] = value
        }
        return hashMap
    }

    fun jsonObjectToAnyHashMap(jsonObject: JSONObject): HashMap<String, Any> {
        val hashMap = HashMap<String, Any>()
        val keys = jsonObject.keys()

        while (keys.hasNext()) {
            val key = keys.next() as String
            val value = jsonObject.get(key) // Use get() to handle multiple types

            // Depending on the value type, you can either directly add it to the map
            // or further process it (e.g., if it's another JSONObject or JSONArray)
            when (value) {
                is JSONObject -> hashMap[key] = jsonObjectToAnyHashMap(value) // Recursively handle JSONObject
                is org.json.JSONArray -> hashMap[key] = jsonArrayToList(value) // Convert JSONArray to List
                else -> hashMap[key] = value // Add other types (String, Int, Boolean, etc.) directly
            }
        }

        return hashMap
    }

    // Helper function to convert JSONArray to List<Any>
    fun jsonArrayToList(jsonArray: org.json.JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.get(i)
            when (value) {
                is JSONObject -> list.add(jsonObjectToAnyHashMap(value)) // Handle nested JSONObject
                is org.json.JSONArray -> list.add(jsonArrayToList(value)) // Handle nested JSONArray
                else -> list.add(value) // Add primitive types (String, Int, Boolean, etc.)
            }
        }
        return list
    }

    @JavascriptInterface
    fun setIdentifiers(json: String) {
        Log.w("FANMAKER", json)

        val data = JSONObject(json)
        fanMakerSDK.userID = data.getString("user_id")
        fanMakerSDK.memberID = data.getString("member_id")
        fanMakerSDK.studentID = data.getString("student_id")
        fanMakerSDK.ticketmasterID = data.getString("ticketmaster_id")
        fanMakerSDK.yinzid = data.getString("yinzid")
        fanMakerSDK.pushNotificationToken = data.getString("push_token")

        if (data.has("arbitrary_identifiers")) {
            val arbitraryIdentifiersJson = data.getJSONObject("arbitrary_identifiers")
            val arbitraryIdentifiers: HashMap<String, String> = jsonObjectToHashMap(arbitraryIdentifiersJson)
            fanMakerSDK.arbitraryIdentifiers.clear()
            fanMakerSDK.arbitraryIdentifiers.putAll(arbitraryIdentifiers)
        }

        if (data.has("fanmaker_parameters")) {
            val fanMakerParametersJson = data.getJSONObject("fanmaker_parameters")
            val fanMakerParameters: HashMap<String, Any> = jsonObjectToAnyHashMap(fanMakerParametersJson)
            fanMakerSDK.fanMakerParameters = fanMakerParameters
        }
    }

    @JavascriptInterface
    fun requestLocationAuthorization() {
        val permission: String = Manifest.permission.ACCESS_FINE_LOCATION
        val status: Int = ContextCompat.checkSelfPermission(this.mContext, permission)

        this.onRequestLocationAuthorization(status == PackageManager.PERMISSION_GRANTED)
    }

    @JavascriptInterface
    fun updateLocation() {
        Log.w("FANMAKER", "LOCATION REQUESTED")
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        val status = ContextCompat.checkSelfPermission(mContext, permission)
        if (status == PackageManager.PERMISSION_GRANTED) {
            val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 10000L
            ).build()
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                android.os.Looper.getMainLooper()
            )
        } else {
            Log.e("FANMAKER", "Location permission not granted")
            this.onRequestLocationAuthorization(false)
        }
    }

    @JavascriptInterface
    fun jsonValueForKey(key: String): String {
        return when (key) {
            "fanmakerIdentifierLexicon" -> {
                val identifiers = mapOf(
                    "user_id" to fanMakerSDK.userID,
                    "member_id" to fanMakerSDK.memberID,
                    "student_id" to fanMakerSDK.studentID,
                    "ticketmaster_id" to fanMakerSDK.ticketmasterID,
                    "yinzid" to fanMakerSDK.yinzid,
                    "push_token" to fanMakerSDK.pushNotificationToken,
                    "arbitrary_identifiers" to fanMakerSDK.arbitraryIdentifiers
                )
                val jsonString = JSONObject(identifiers).toString()
                val escapedJson = jsonString.replace("\"", "\\\"")
                "FanmakerSDKCallback(\"$escapedJson\")"
            }
            "fanmakerParametersLexicon" -> {
                val jsonString = JSONObject(fanMakerSDK.fanMakerParameters as Map<Any?, Any?>).toString()
                val escapedJson = jsonString.replace("\"", "\\\"")
                "FanmakerSDKCallback(\"$escapedJson\")"
            }
            "fanmakerUserToken" -> {
                val jsonString = JSONObject(fanMakerSDK.fanMakerUserToken as Map<Any?, Any?>).toString()
                val escapedJson = jsonString.replace("\"", "\\\"")
                "FanmakerSDKCallback(\"$escapedJson\")"
            }
            "locationEnabled" -> {
                val isEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
                val escapedValue = isEnabled.toString().replace("\"", "\\\"")
                "FanmakerSDKCallback(\"{ \\\"value\\\": \\\"$escapedValue\\\" }\")"
            }
            else -> {
                // For unknown keys, return empty JSON object
                "{}"
            }
        }
    }

    @JavascriptInterface
    fun fetchJSONValue(value: String) {
        Log.w("FANMAKER", "FETCHING JSON VALUE: $value")
        when (value) {
            "sessionToken" -> {
                val token = fanMakerSharedPreferences.getString("token", "") ?: ""
                val escapedToken = token.replace("\"", "\\\"")
                val jsString = "FanmakerSDKCallback(\"$escapedToken\")"
                (mContext as? android.app.Activity)?.runOnUiThread {
                    (mContext as? android.app.Activity)?.findViewById<android.webkit.WebView>(R.id.fanmaker_sdk_webview)?.let { webView ->
                        webView.evaluateJavascript("""
                            if (typeof FanmakerSDKCallback === 'undefined') {
                                window.FanmakerSDKCallback = function(data) {
                                    window.dispatchEvent(new CustomEvent('fanmakerSDKCallback', {
                                        detail: data
                                    }));
                                };
                            }
                            $jsString
                        """.trimIndent(), null)
                    }
                }
            }
            "locationServicesEnabled" -> {
                val locationManager = mContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val authorizationStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    @Suppress("DEPRECATION")
                    ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                }

                val status = when (authorizationStatus) {
                    PackageManager.PERMISSION_GRANTED -> {
                        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            "Always"
                        } else {
                            "When In Use"
                        }
                    }
                    PackageManager.PERMISSION_DENIED -> "Denied"
                    else -> "Not Determined"
                }

                val escapedValue = status.replace("\"", "\\\"")
                val jsString = "FanmakerSDKCallback(\"{ \\\"value\\\": \\\"$escapedValue\\\" }\")"
                (mContext as? android.app.Activity)?.runOnUiThread {
                    (mContext as? android.app.Activity)?.findViewById<android.webkit.WebView>(R.id.fanmaker_sdk_webview)?.let { webView ->
                        webView.evaluateJavascript("""
                            if (typeof FanmakerSDKCallback === 'undefined') {
                                window.FanmakerSDKCallback = function(data) {
                                    window.dispatchEvent(new CustomEvent('fanmakerSDKCallback', {
                                        detail: data
                                    }));
                                };
                            }
                            $jsString
                        """.trimIndent(), null)
                    }
                }
            }
            "locationEnabled" -> {
                val result = jsonValueForKey("locationEnabled")
                (mContext as? android.app.Activity)?.runOnUiThread {
                    (mContext as? android.app.Activity)?.findViewById<android.webkit.WebView>(R.id.fanmaker_sdk_webview)?.let { webView ->
                        webView.evaluateJavascript("""
                            if (typeof FanmakerSDKCallback === 'undefined') {
                                window.FanmakerSDKCallback = function(data) {
                                    window.dispatchEvent(new CustomEvent('fanmakerSDKCallback', {
                                        detail: data
                                    }));
                                };
                            }
                            $result
                        """.trimIndent(), null)
                    }
                }
            }
            "identifiers" -> {
                val result = jsonValueForKey("fanmakerIdentifierLexicon")
                (mContext as? android.app.Activity)?.runOnUiThread {
                    (mContext as? android.app.Activity)?.findViewById<android.webkit.WebView>(R.id.fanmaker_sdk_webview)?.let { webView ->
                        webView.evaluateJavascript("""
                            if (typeof FanmakerSDKCallback === 'undefined') {
                                window.FanmakerSDKCallback = function(data) {
                                    window.dispatchEvent(new CustomEvent('fanmakerSDKCallback', {
                                        detail: data
                                    }));
                                };
                            }
                            $result
                        """.trimIndent(), null)
                    }
                }
            }
            "params" -> {
                val result = jsonValueForKey("fanmakerParametersLexicon")
                (mContext as? android.app.Activity)?.runOnUiThread {
                    (mContext as? android.app.Activity)?.findViewById<android.webkit.WebView>(R.id.fanmaker_sdk_webview)?.let { webView ->
                        webView.evaluateJavascript("""
                            if (typeof FanmakerSDKCallback === 'undefined') {
                                window.FanmakerSDKCallback = function(data) {
                                    window.dispatchEvent(new CustomEvent('fanmakerSDKCallback', {
                                        detail: data
                                    }));
                                };
                            }
                            $result
                        """.trimIndent(), null)
                    }
                }
            }
            "userToken" -> {
                val result = jsonValueForKey("fanmakerUserToken")
                (mContext as? android.app.Activity)?.runOnUiThread {
                    (mContext as? android.app.Activity)?.findViewById<android.webkit.WebView>(R.id.fanmaker_sdk_webview)?.let { webView ->
                        webView.evaluateJavascript("""
                            if (typeof FanmakerSDKCallback === 'undefined') {
                                window.FanmakerSDKCallback = function(data) {
                                    window.dispatchEvent(new CustomEvent('fanmakerSDKCallback', {
                                        detail: data
                                    }));
                                };
                            }
                            $result
                        """.trimIndent(), null)
                    }
                }
            }
            else -> {
                val result = jsonValueForKey(value)
                (mContext as? android.app.Activity)?.runOnUiThread {
                    (mContext as? android.app.Activity)?.findViewById<android.webkit.WebView>(R.id.fanmaker_sdk_webview)?.let { webView ->
                        webView.evaluateJavascript("""
                            if (typeof FanmakerSDKCallback === 'undefined') {
                                window.FanmakerSDKCallback = function(data) {
                                    window.dispatchEvent(new CustomEvent('fanmakerSDKCallback', {
                                        detail: data
                                    }));
                                };
                            }
                            $result
                        """.trimIndent(), null)
                    }
                }
            }
        }
    }

    // Modern location updates using FusedLocationProviderClient
    private val fusedLocationClient by lazy {
        com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(mContext)
    }

    private val locationCallback = object : com.google.android.gms.location.LocationCallback() {
        override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
            for (location in locationResult.locations) {
                onUpdateLocation(location)
            }
        }
        override fun onLocationAvailability(locationAvailability: com.google.android.gms.location.LocationAvailability) {
            // Optionally handle provider enabled/disabled
        }
    }
}
