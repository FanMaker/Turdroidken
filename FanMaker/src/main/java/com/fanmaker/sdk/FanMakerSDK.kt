package com.fanmaker.sdk
import java.util.HashMap
import android.util.Log

// Multi-Client
import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// On Resume/Open
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

// Shared Preferences
import android.content.Context
import android.content.SharedPreferences

// Location
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices

class FanMakerSDK(
    var version: String = "2.0.0",
    var apiKey: String = "",
    var userID: String = "",
    var memberID: String = "",
    var studentID: String = "",
    var ticketmasterID: String = "",
    var yinzid: String = "",
    var pushNotificationToken: String = "",
    var arbitraryIdentifiers: HashMap<String, String> = HashMap<String, String>(),
    var locationEnabled: Boolean = false,
    var firstLaunch: Boolean = true
) : Parcelable, LifecycleObserver {
    lateinit var fanMakerSharedPreferences: FanMakerSharedPreferences
    lateinit var context: android.content.Context
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ------------------------------------------------------------------------------------------------------
    // THESE METHODS ARE USED TO MANUALLY PARCEL AND UNPARCEL THE INSTANCE OF THIS CLASS
    // BECAUSE OTHERWISE ANYTHING THAT IS CHANGED AFTER THE INSTANCE HAS BEEN CREATED IS LOST
    // ------------------------------------------------------------------------------------------------------
    constructor(parcel: Parcel) : this(
        version = parcel.readString() ?: "",
        apiKey = parcel.readString() ?: "",
        userID = parcel.readString() ?: "",
        memberID = parcel.readString() ?: "",
        studentID = parcel.readString() ?: "",
        ticketmasterID = parcel.readString() ?: "",
        yinzid = parcel.readString() ?: "",
        pushNotificationToken = parcel.readString() ?: "",
        arbitraryIdentifiers = parcel.readHashMap(HashMap::class.java.classLoader) as HashMap<String, String>,
        locationEnabled = parcel.readBoolean() ?: false
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(version)
        parcel.writeString(apiKey)
        parcel.writeString(userID)
        parcel.writeString(memberID)
        parcel.writeString(studentID)
        parcel.writeString(ticketmasterID)
        parcel.writeString(yinzid)
        parcel.writeString(pushNotificationToken)
        parcel.writeMap(arbitraryIdentifiers)
        parcel.writeBoolean(locationEnabled)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<FanMakerSDK> {
        override fun createFromParcel(parcel: Parcel): FanMakerSDK = FanMakerSDK(parcel)
        override fun newArray(size: Int): Array<FanMakerSDK?> = arrayOfNulls(size)
    }
    // ------------------------------------------------------------------------------------------------------

    fun initialize(context: Context, apiKey: String) {
        this.apiKey = apiKey
        this.context = context
        fanMakerSharedPreferences = FanMakerSharedPreferences(this.context, this.apiKey)
    }

    fun isInitialized(): Boolean {
        return apiKey != ""
    }

    fun isLocationTrackingEnabled(): Boolean {
        return this.locationEnabled
    }

    fun enableLocationTracking() {
        this.locationEnabled = true
    }

    fun disableLocationTracking() {
        this.locationEnabled = false
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        // Send app event
        val appAction = if (this.firstLaunch) "app_launch" else "app_resume"
        sendAppEvent(appAction)

        // Set first launch to false, it will reset when the app does
        if (this.firstLaunch) { this.firstLaunch = false }

        // Send location ping for auto checkin
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        sendLocationPing()
    }

    private fun sendAppEvent(appAction: String) {
        val userToken = fanMakerSharedPreferences.getString("token", "")
        if (userToken!!.isNotEmpty()) {
            val http = FanMakerSDKHttp(this, context, userToken)
            val params = HashMap<String, String>()
            params["context"] = appAction

            http.post("users/log_impression", params, { response -> }, { errorCode, errorMessage -> })
        }
    }

    private fun sendLocationPing() {
        // Check if permission is granted
        if (isLocationTrackingEnabled() && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // we only wnt to continue if we have a user token already
            val userToken = fanMakerSharedPreferences.getString("token", "")
            if (userToken!!.isNotEmpty()) {
                // Request location updates
                val locationRequest = LocationRequest.create()?.apply {
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                    maxWaitTime = 10000
                }

                locationRequest?.let {
                    fusedLocationClient.getCurrentLocation(it.priority, null)
                    .addOnSuccessListener { location: Location? ->
                        location?.let {
                            // Use the location object here
                            if (it.latitude != 0.0 && it.longitude != 0.0) {
                                val http = FanMakerSDKHttp(this, context, userToken)
                                val params = HashMap<String, String>()
                                params["latitude"] = it.latitude.toString()
                                params["longitude"] = it.longitude.toString()

                                http.post("events/auto_checkin", params, { response -> }, { errorCode, errorMessage -> })
                            }
                        }
                    }
                }

            }
        } else {
            Log.e("FanMakerSDK", "Location permission not granted")
        }
    }
}
