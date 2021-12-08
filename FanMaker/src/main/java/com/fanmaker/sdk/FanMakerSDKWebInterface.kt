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

class FanMakerSDKWebInterface(
    private val mContext: Context,
    private val onRequestLocationAuthorization: (authorized: Boolean) -> Unit,
    private val onUpdateLocation: (location: Location) -> Unit
) {
    private var locationManager: LocationManager? = null

    @JavascriptInterface
    fun storeSessionToken(token: String) {
        val settings = mContext.getSharedPreferences("com.fanmaker.sdk", Context.MODE_PRIVATE)
        val editor = settings.edit()
        editor.putString("token", token)
        editor.commit()
        Log.w("FANMAKER", "SESSION SAVED")
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