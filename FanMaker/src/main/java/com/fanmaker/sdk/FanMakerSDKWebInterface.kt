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
    private val onRequestLocationAuthorization: (authorized: Boolean) -> Unit,
    private val onUpdateLocation: (location: Location) -> Unit
) {
    private var locationManager: LocationManager? = null

    @JavascriptInterface
    fun sdkOpenUrl(url: String) {
        Log.w("FANMAKER", "Opening External Url: " + url)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        mContext.startActivity(intent)
    }

    @JavascriptInterface
    fun storeSessionToken(token: String) {
        val settings = mContext.getSharedPreferences("com.fanmaker.sdk", Context.MODE_PRIVATE)
        val editor = settings.edit()
        editor.putString("token", token)
        editor.commit()
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

    @JavascriptInterface
    fun setIdentifiers(json: String) {
        Log.w("FANMAKER", json)

        val data = JSONObject(json)
        FanMakerSDK.userID = data.getString("user_id")
        FanMakerSDK.memberID = data.getString("member_id")
        FanMakerSDK.studentID = data.getString("student_id")
        FanMakerSDK.ticketmasterID = data.getString("ticketmaster_id")
        FanMakerSDK.yinzid = data.getString("yinzid")
        FanMakerSDK.pushNotificationToken = data.getString("push_token")
        val arbitraryIdentifiersJson = data.getJSONObject("arbitrary_dentifiers")
        val arbitraryIdentifiers: HashMap<String, String> = jsonObjectToHashMap(arbitraryIdentifiersJson)
        FanMakerSDK.arbitraryIdentifiers = arbitraryIdentifiers
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
