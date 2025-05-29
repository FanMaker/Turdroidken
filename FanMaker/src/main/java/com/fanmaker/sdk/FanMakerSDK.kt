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

// Deep Linking
import android.net.Uri
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.MapSerializer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

class ObservableHashMap<K, V>(
    private val onModified: () -> Unit
) : HashMap<K, V>() {
    override fun put(key: K, value: V): V? {
        val result = super.put(key, value)
        onModified()
        return result
    }

    override fun remove(key: K): V? {
        val result = super.remove(key)
        onModified()
        return result
    }

    override fun putAll(from: Map<out K, V>) {
        super.putAll(from)
        onModified()
    }

    override fun clear() {
        super.clear()
        onModified()
    }
}

class FanMakerSDK(
    var version: String = "2.0.5",
    var apiKey: String = "",
    private var _userID: String = "",
    private var _memberID: String = "",
    private var _studentID: String = "",
    private var _ticketmasterID: String = "",
    private var _yinzid: String = "",
    private var _pushNotificationToken: String = "",
    private var _arbitraryIdentifiers: ObservableHashMap<String, String> = ObservableHashMap { },
    var fanMakerParameters: HashMap<String, Any> = HashMap<String, Any>(),
    var fanMakerUserToken: HashMap<String, Any> = HashMap<String, Any>(),
    var useDarkLoadingScreen: Boolean = false,
    var locationEnabled: Boolean = false,
    var firstLaunch: Boolean = true,
    var baseUrl: String = "",
    var deepLinkUrl: String = "",
) : LifecycleObserver {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Property observers for identifiers
    var userID: String
        get() = _userID
        set(value) {
            _userID = value
        }

    var memberID: String
        get() = _memberID
        set(value) {
            _memberID = value
        }

    var studentID: String
        get() = _studentID
        set(value) {
            _studentID = value
        }

    var ticketmasterID: String
        get() = _ticketmasterID
        set(value) {
            _ticketmasterID = value
        }

    var yinzid: String
        get() = _yinzid
        set(value) {
            _yinzid = value
        }

    var pushNotificationToken: String
        get() = _pushNotificationToken
        set(value) {
            _pushNotificationToken = value
        }

    var arbitraryIdentifiers: ObservableHashMap<String, String>
        get() = _arbitraryIdentifiers
        set(value) {
            _arbitraryIdentifiers.clear()
            _arbitraryIdentifiers.putAll(value)
        }

    init {
        _arbitraryIdentifiers = ObservableHashMap { }
    }

    lateinit var fanMakerSharedPreferences: FanMakerSharedPreferences
    lateinit var context: android.content.Context
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ------------------------------------------------------------------------------------------------------

    fun initialize(context: Context, apiKey: String) {
        this.apiKey = apiKey
        this.context = context
        fanMakerSharedPreferences = FanMakerSharedPreferences(context, this.apiKey)
    }

    fun updateBaseUrl(url: String) {
        this.baseUrl = url
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

    fun webViewHeaders(): HashMap<String, String> {
        val headers: HashMap<String, String> = HashMap<String, String>()
        headers.put("X-FanMaker-SDK-Version", this.version)
        headers.put("X-FanMaker-SDK-Platform", "Turdroidken")
        headers.put("X-FanMaker-Mode", "sdk")

        if (this.memberID != "") headers.put("X-Member-ID", this.memberID)
        if (this.studentID != "") headers.put("X-Student-ID", this.studentID)
        if (this.ticketmasterID != "") headers.put("X-Ticketmaster-ID", this.ticketmasterID)
        if (this.yinzid != "") headers.put("X-Yinzid", this.yinzid)
        if (this.pushNotificationToken != "") headers.put("X-PushNotification-Token", this.pushNotificationToken)
        if (this.arbitraryIdentifiers.isNotEmpty()) {
            val jsonIdentifiers = Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), this.arbitraryIdentifiers)
            headers.put("X-Fanmaker-Identifiers", jsonIdentifiers)
        }

        if (this.fanMakerParameters.isNotEmpty()) {
            val jsonParameters = Json.encodeToString(MapSerializer(String.serializer(), AnySerializer), this.fanMakerParameters)
            headers.put("X-Fanmaker-Parameters", jsonParameters)
        }

        if (this.fanMakerUserToken.isNotEmpty()) {
            val jsonUserToken = Json.encodeToString(MapSerializer(String.serializer(), AnySerializer), this.fanMakerUserToken)
            headers.put("X-Fanmaker-UserToken", jsonUserToken)
        }

        val userToken = fanMakerSharedPreferences.getString("token", "")
        if (userToken != null && userToken != "") {
            headers.put("X-FanMaker-SessionToken", userToken)
        }

        headers.put("X-FanMaker-Token", this.apiKey)

        return headers
    }

    fun canOpenUrl(url: String): Boolean {
        val urlHost = Uri.parse(url).host?.toLowerCase()
        return urlHost == "fanmaker" || urlHost == "fanmaker.com"
    }

    fun loginUserFromParamsSync(onComplete: (Boolean) -> Unit) {
        // Create a map with all user identifiers
        val identifiers = HashMap<String, Any>()

        // Add all the individual identifiers if they exist and are not empty
        if (userID.isNotEmpty()) identifiers["user_id"] = userID
        if (memberID.isNotEmpty()) identifiers["member_id"] = memberID
        if (studentID.isNotEmpty()) identifiers["student_id"] = studentID
        if (ticketmasterID.isNotEmpty()) identifiers["ticketmaster_id"] = ticketmasterID
        if (yinzid.isNotEmpty()) identifiers["yinzid"] = yinzid

        // Add the arbitrary identifiers if they exist and are not empty
        if (arbitraryIdentifiers.isNotEmpty()) {
            identifiers["fanmaker_identifiers"] = arbitraryIdentifiers
        }

        // If no identifiers are present or all values are empty, return false
        if (identifiers.isEmpty() || identifiers.all { (_, value) -> 
            when (value) {
                is String -> value.isEmpty()
                is Map<*, *> -> value.isEmpty()
                else -> false
            }
        }) {
            onComplete(false)
            return
        }

        // Make the API request
        val http = FanMakerSDKHttp(this, context, "")
        http.post("site/auth/auto_login", identifiers,
            { response ->
                val status = response.getInt("status")
                if (status == 200) {
                    // If the response data is a JSONObject, set it as the user token
                    if (response.has("data")) {
                        val tokenData = response.getJSONObject("data")
                        val tokenMap = HashMap<String, Any>()
                        tokenData.keys().forEach { key ->
                            tokenMap[key] = tokenData.get(key)
                        }
                        fanMakerUserToken = tokenMap
                        onComplete(true)
                    } else {
                        onComplete(false)
                    }
                } else {
                    onComplete(false)
                }
            },
            { errorCode, errorMessage ->
                onComplete(false)
            }
        )
    }

    // This function will be used by the FanMakerSDKWebView and FanMakerSDKWebViewFragment
    // to determine which URL to open.
    fun formatUrl(url: String? = "", onUrlReady: (String) -> Unit) {
        val deepUrl = if(url != null && url.isNotEmpty()) url else this.deepLinkUrl
        if(deepUrl.isNotEmpty()) {
            // Try to login before formatting the URL
            loginUserFromParamsSync { success ->
                val urlComponents = Uri.parse(this.baseUrl)
                val deepLinkComponents = Uri.parse(deepUrl)
                val path = deepLinkComponents.path
                val query = deepLinkComponents.query

                val newUrl = Uri.parse(this.baseUrl).buildUpon()
                path?.let { newUrl.appendEncodedPath(it) }
                query?.let { newUrl.encodedQuery(it) }

                this.deepLinkUrl = ""

                onUrlReady(newUrl.toString())
            }
        } else {
            // Try to login before returning the base URL
            loginUserFromParamsSync { success ->
                onUrlReady(this.baseUrl)
            }
        }
    }

    fun handleUrl(url: String) {
        if (canOpenUrl(url)) {
            val urlComponents = Uri.parse(url)
            val path = urlComponents.path
            val query = urlComponents.query

            this.deepLinkUrl = url
        }
    }

    // This function is called when the app is resumed assuming we have a lifecycle observer
    // established in the main activity.
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

// Creates a singleton-like registry to hold our FanMakerSDK instances
class FanMakerSDKs() {
    companion object {
        private val instances: MutableMap<String, FanMakerSDK> = mutableMapOf()

        fun setInstance(context: Context, key: String, apiKey: String) {
            val instance = FanMakerSDK()
            instance.initialize(context, apiKey)
            instances[key] = instance
        }

        @Suppress("UNCHECKED_CAST")
        fun getInstance(key: String): FanMakerSDK? {
            return instances[key]
        }
    }
}
