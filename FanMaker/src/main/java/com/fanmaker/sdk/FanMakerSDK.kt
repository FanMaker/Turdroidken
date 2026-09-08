package com.fanmaker.sdk
import java.util.HashMap
import android.util.Log

// Multi-Client
import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// On Resume/Open
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

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
import com.google.android.gms.location.Priority

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
    var version: String = "4.0.3",
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
    var useDarkLoadingScreen: Boolean = true,
    var loadingBackgroundColor: Int? = null,
    var loadingAnimationDrawable: Int? = null,
    var requestTimeoutMs: Int = 10000,
    var requestMaxRetries: Int = com.android.volley.DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
    private var _locationEnabled: Boolean = false,
    var firstLaunch: Boolean = true,
    var baseUrl: String = "",
    var deepLinkUrl: String = "",
) : DefaultLifecycleObserver {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Whether the observed lifecycle is currently resumed, and whether an auto
    // checkin ping has already been attempted this session. Together these let
    // enableLocationTracking recover a ping that onResume dropped because
    // locationEnabled was still false.
    private var isForegrounded = false
    private var locationPingAttempted = false

    // Property observers for identifiers
    var userID: String
        get() = _userID
        set(value) {
            _userID = value
            persistIdentifier(KEY_USER_ID, value)
        }

    var memberID: String
        get() = _memberID
        set(value) {
            _memberID = value
            persistIdentifier(KEY_MEMBER_ID, value)
        }

    var studentID: String
        get() = _studentID
        set(value) {
            _studentID = value
            persistIdentifier(KEY_STUDENT_ID, value)
        }

    var ticketmasterID: String
        get() = _ticketmasterID
        set(value) {
            _ticketmasterID = value
            persistIdentifier(KEY_TICKETMASTER_ID, value)
        }

    var yinzid: String
        get() = _yinzid
        set(value) {
            _yinzid = value
            persistIdentifier(KEY_YINZID, value)
        }

    var pushNotificationToken: String
        get() = _pushNotificationToken
        set(value) {
            _pushNotificationToken = value
            persistIdentifier(KEY_PUSH_TOKEN, value)
        }

    var arbitraryIdentifiers: ObservableHashMap<String, String>
        get() = _arbitraryIdentifiers
        set(value) {
            _arbitraryIdentifiers.clear()
            _arbitraryIdentifiers.putAll(value)
        }

    // Assigning this is the documented way to turn Auto Checkin on, so the
    // catch-up ping lives here rather than only in enableLocationTracking().
    var locationEnabled: Boolean
        get() = _locationEnabled
        set(value) {
            val wasDisabled = !_locationEnabled
            _locationEnabled = value

            // The SDK is a DefaultLifecycleObserver. A host that registers it
            // against an already-RESUMED lifecycle gets onResume synchronously,
            // which is often before it has enabled location tracking - and
            // because this defaults to false, that first ping is dropped and
            // nothing ever re-fires it, so cold start never auto-checks-in.
            // Recover it here instead of depending on integrator call order.
            if (value && wasDisabled && isForegrounded && !locationPingAttempted) {
                Log.i("FanMakerSDK", "location tracking enabled after onResume; sending the ping it missed")
                sendLocationPing()
            }
        }

    init {
        _arbitraryIdentifiers = ObservableHashMap { persistArbitraryIdentifiers() }
    }

    lateinit var fanMakerSharedPreferences: FanMakerSharedPreferences
    lateinit var context: android.content.Context
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Action callback for triggerAction - similar to NotificationCenter in Swift
    var onActionTriggered: ((action: String, params: HashMap<String, Any>?) -> Unit)? = null

    // Close callback, invoked when web content triggers the "close" action.
    // When set, the SDK hands close handling to the integrator and will NOT
    // auto-dismiss — use this to dismiss a FanMakerSDKWebViewFragment host or a
    // custom presentation. When left null, the SDK closes its own
    // FanMakerSDKWebView activity automatically. Mirrors `onClose` on iOS.
    var onClose: ((params: HashMap<String, Any>?) -> Unit)? = null

    // ------------------------------------------------------------------------------------------------------

    fun initialize(context: Context, apiKey: String) {
        this.apiKey = apiKey
        this.context = context
        fanMakerSharedPreferences = FanMakerSharedPreferences(context, this.apiKey)
        restoreIdentifiers()
    }

    // Identifiers are written by the host (usually once, at login) and by the
    // web bridge via setIdentifiers. They used to live only in memory, so every
    // one of them was silently dropped on process death and the host had to
    // re-supply them on each cold start. Persist on write, rehydrate on
    // initialize.
    private fun persistIdentifier(key: String, value: String) {
        if (::fanMakerSharedPreferences.isInitialized) {
            fanMakerSharedPreferences.putString(key, value)
        }
    }

    private fun persistArbitraryIdentifiers() {
        if (!::fanMakerSharedPreferences.isInitialized) return
        val encoded = try {
            Json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                HashMap(_arbitraryIdentifiers)
            )
        } catch (e: Exception) {
            Log.e("FanMakerSDK", "could not persist arbitrary identifiers: ${e.message}")
            return
        }
        fanMakerSharedPreferences.putString(KEY_ARBITRARY_IDENTIFIERS, encoded)
    }

    // A value supplied to the constructor is more current than anything left in
    // prefs by an earlier session, so only empty fields are rehydrated.
    private fun restoredValue(key: String, current: String): String =
        if (current.isNotEmpty()) current
        else fanMakerSharedPreferences.getString(key, current) ?: current

    private fun restoreIdentifiers() {
        _userID = restoredValue(KEY_USER_ID, _userID)
        _memberID = restoredValue(KEY_MEMBER_ID, _memberID)
        _studentID = restoredValue(KEY_STUDENT_ID, _studentID)
        _ticketmasterID = restoredValue(KEY_TICKETMASTER_ID, _ticketmasterID)
        _yinzid = restoredValue(KEY_YINZID, _yinzid)
        _pushNotificationToken = restoredValue(KEY_PUSH_TOKEN, _pushNotificationToken)

        val encoded = fanMakerSharedPreferences.getString(KEY_ARBITRARY_IDENTIFIERS, "")
        if (_arbitraryIdentifiers.isEmpty() && !encoded.isNullOrEmpty()) {
            try {
                val decoded = Json.decodeFromString(
                    MapSerializer(String.serializer(), String.serializer()),
                    encoded
                )
                // super.putAll would skip the observer; going through the map is
                // fine, it just rewrites what was read.
                _arbitraryIdentifiers.putAll(decoded)
            } catch (e: Exception) {
                Log.e("FanMakerSDK", "could not restore arbitrary identifiers: ${e.message}")
            }
        }
    }

    fun updateBaseUrl(url: String) {
        this.baseUrl = url
    }

    fun isInitialized(): Boolean {
        return apiKey != ""
    }

    /**
     * Updates the session token stored in SharedPreferences.
     * Called by the token resolver after a successful OAuth token refresh.
     */
    fun updateSessionToken(tokenString: String) {
        if (::fanMakerSharedPreferences.isInitialized) {
            fanMakerSharedPreferences.putString("token", tokenString)
        }
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

    fun setLoadingBackgroundColor(color: Int) {
        this.loadingBackgroundColor = color
    }

    fun setLoadingAnimationDrawable(drawableResId: Int) {
        this.loadingAnimationDrawable = drawableResId
    }

    fun webViewHeaders(): HashMap<String, String> {
        val headers: HashMap<String, String> = HashMap<String, String>()
        headers.put("X-FanMaker-SDK-Version", this.version)
        headers.put("X-FanMaker-SDK-Platform", "Turdroidken")
        headers.put("X-FanMaker-Mode", "sdk")
        headers.put("X-Fanmaker-Theme", if (this.useDarkLoadingScreen) "dark" else "light")

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
            headers.put("X-Fanmaker-User-Token", jsonUserToken)
        }

        val userToken = fanMakerSharedPreferences.getString("token", "")
        if (userToken != null && userToken != "") {
            // Resolve token type to set headers appropriately
            val tokenType = FanMakerSDKTokenResolver.resolve(userToken)
            val sessionHeaderValue = FanMakerSDKTokenResolver.sessionTokenHeaderValue(tokenType, userToken)
            val authHeaderValue = FanMakerSDKTokenResolver.authorizationHeaderValue(tokenType)

            headers.put("X-FanMaker-SessionToken", sessionHeaderValue)
            headers.put("Authorization", authHeaderValue)
        }

        headers.put("X-FanMaker-Token", this.apiKey)

        return headers
    }

    fun canOpenUrl(url: String): Boolean {
        val urlHost = Uri.parse(url).host?.lowercase()
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
    // @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    override fun onResume(owner: LifecycleOwner) {
        isForegrounded = true

        // Send app event
        val appAction = if (this.firstLaunch) "app_launch" else "app_resume"
        sendAppEvent(appAction)

        // Set first launch to false, it will reset when the app does
        if (this.firstLaunch) { this.firstLaunch = false }

        // Send location ping for auto checkin
        sendLocationPing()
    }

    override fun onPause(owner: LifecycleOwner) {
        isForegrounded = false
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
        if (!::context.isInitialized) {
            Log.e("FanMakerSDK", "auto checkin skipped: SDK not initialized yet (call initialize first)")
            return
        }

        // These three used to share one "Location permission not granted"
        // message, which reported a permission problem for a tracking-disabled
        // SDK whose permissions were perfectly fine.
        if (!isLocationTrackingEnabled()) {
            Log.e("FanMakerSDK", "auto checkin skipped: location tracking disabled (call enableLocationTracking)")
            return
        }
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted || !coarseGranted) {
            val missing = listOfNotNull(
                if (!fineGranted) "ACCESS_FINE_LOCATION" else null,
                if (!coarseGranted) "ACCESS_COARSE_LOCATION" else null
            ).joinToString(", ")
            Log.e("FanMakerSDK", "auto checkin skipped: location permission not granted ($missing)")
            return
        }

        // Initialized here rather than in onResume so a catch-up ping from
        // enableLocationTracking cannot hit an uninitialized lateinit.
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        locationPingAttempted = true

        // Request location updates
        @Suppress("DEPRECATION")
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMaxUpdateDelayMillis(10000)
            .build()

        locationRequest?.let {
            fusedLocationClient.getCurrentLocation(it.priority, null)
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    // Use the location object here
                    if (it.latitude != 0.0 && it.longitude != 0.0) {
                        // Read token INSIDE the callback so we get the latest value
                        // (a prior refresh from sendAppEvent may have updated it)
                        val userToken = fanMakerSharedPreferences.getString("token", "")
                        if (userToken!!.isNotEmpty()) {
                            val http = FanMakerSDKHttp(this, context, userToken)
                            val params = HashMap<String, String>()
                            params["latitude"] = it.latitude.toString()
                            params["longitude"] = it.longitude.toString()

                            http.post("events/auto_checkin", params,
                                { response -> Log.w("FanMakerSDK", "######## AUTO CHECKIN: Success") },
                                { errorCode, errorMessage -> Log.e("FanMakerSDK", "######## AUTO CHECKIN: Failed ($errorCode) $errorMessage") }
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        // SharedPreferences keys for persisted identifiers. The prefs file is
        // already namespaced per instance by api key, so these need no prefix
        // beyond staying clear of "token".
        private const val KEY_USER_ID = "identifier_user_id"
        private const val KEY_MEMBER_ID = "identifier_member_id"
        private const val KEY_STUDENT_ID = "identifier_student_id"
        private const val KEY_TICKETMASTER_ID = "identifier_ticketmaster_id"
        private const val KEY_YINZID = "identifier_yinzid"
        private const val KEY_PUSH_TOKEN = "identifier_push_notification_token"
        private const val KEY_ARBITRARY_IDENTIFIERS = "identifier_arbitrary"
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
