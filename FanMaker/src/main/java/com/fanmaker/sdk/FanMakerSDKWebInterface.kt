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

class FanMakerSDKWebInterface(
    private val mContext: Context,
    private val fanMakerSDK: FanMakerSDK,
    private val onRequestLocationAuthorization: (authorized: Boolean) -> Unit,
    private val onUpdateLocation: (location: Location) -> Unit
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
        val arbitraryIdentifiersJson = data.getJSONObject("arbitrary_identifiers")
        val arbitraryIdentifiers: HashMap<String, String> = jsonObjectToHashMap(arbitraryIdentifiersJson)
        fanMakerSDK.arbitraryIdentifiers = arbitraryIdentifiers

        val fanMakerParametersJson = data.getJSONObject("fanmaker_parameters")
        val fanMakerParameters: HashMap<String, Any> = jsonObjectToAnyHashMap(fanMakerParametersJson)
        fanMakerSDK.fanMakerParameters = fanMakerParameters
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
        locationManager = this.mContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager?

        try {
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, locationListener)
        } catch(ex: SecurityException) {
            Log.e("FANMAKER", "Location services not enabled")
            this.onRequestLocationAuthorization(false)
        }
    }

    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onUpdateLocation(location)
        }
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
}
