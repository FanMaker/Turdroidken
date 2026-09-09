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
import android.content.Intent
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
    private var _deepLinkUrl: String = "",
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

    // A pending deep link destination, persisted so a push tap that starts a
    // cold process does not lose it before the webview is ready to consume it.
    var deepLinkUrl: String
        get() = _deepLinkUrl
        set(value) {
            _deepLinkUrl = value
            persistIdentifier(KEY_DEEP_LINK, value)
            if (::fanMakerSharedPreferences.isInitialized) {
                fanMakerSharedPreferences.putLong(
                    KEY_DEEP_LINK_AT,
                    if (value.isEmpty()) 0L else System.currentTimeMillis()
                )
            }
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

    /**
     * The dev-defined key this instance was registered under, or "" when it was
     * built directly rather than through [FanMakerSDKs.setInstance].
     *
     * Needed because launching the webview requires this key as an intent
     * extra, so an instance that does not know its own key cannot start its
     * own screen.
     */
    var instanceKey: String = ""
        internal set

    /**
     * Puts the FanMaker UI on screen, without the host having to build an
     * intent or know which activity to start.
     *
     * A host previously had to construct
     * `Intent(context, FanMakerSDKWebView::class.java)` and remember to attach
     * the dev-defined key as a `"fanMakerKey"` extra - a magic string that
     * fails silently when it is wrong or missing. This is the counterpart of
     * `present()` on the iOS SDK.
     *
     * @param path an optional destination inside the FanMaker UI, such as
     *   "/store". Equivalent to calling [openPath] beforehand.
     * @return whether the activity was started.
     */
    @JvmOverloads
    fun present(context: Context, path: String? = null): Boolean {
        if (!isInitialized()) {
            Log.e("FanMakerSDK", "cannot present: initialize() has not been called yet")
            return false
        }

        if (instanceKey.isEmpty()) {
            Log.e(
                "FanMakerSDK",
                "cannot present: this instance was not registered with FanMakerSDKs.setInstance, " +
                    "so it has no key to launch with. Register it, or build the intent yourself."
            )
            return false
        }

        // Validate before starting anything, so a bad path never reaches the
        // activity and the fan is not sent to a screen that then loads the
        // wrong place.
        if (path != null && !openPath(path)) return false

        val intent = Intent(context, FanMakerSDKWebView::class.java)
            .apply { putExtra("fanMakerKey", instanceKey) }

        if (FanMakerSDKWebView.isRunning(instanceKey)) {
            // A screen is already open for this key, so reuse it rather than
            // building a second one. SINGLE_TOP makes Android deliver this to
            // the running activity's onNewIntent instead of creating another,
            // and REORDER_TO_FRONT brings it forward when the task is
            // backgrounded - a push tap while the app is not in front.
            //
            // The destination rides along on the intent, so onNewIntent applies
            // it and navigates the open screen. Nothing is dropped, and the fan
            // never sees a second copy appear and vanish.
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            if (path != null) intent.putExtra("fanMakerDeepLink", path)
            Log.i("FanMakerSDK", "a webview is already open for '$instanceKey'; reusing it")
        }

        context.startActivity(intent)
        return true
    }

    /** Whether this instance currently has a FanMaker screen on display. */
    val isPresenting: Boolean
        get() = instanceKey.isNotEmpty() && FanMakerSDKWebView.isRunning(instanceKey)

    /**
     * Closes a screen this instance put on display.
     *
     * Only needed by a host closing the UI from its own code; web content
     * triggering the close action, and the system back gesture, both already
     * unwind on their own.
     */
    fun dismiss() {
        if (instanceKey.isEmpty()) return
        FanMakerSDKWebView.finishRunning(instanceKey)
    }

    fun initialize(context: Context, apiKey: String) {
        this.apiKey = apiKey
        this.context = context
        fanMakerSharedPreferences = FanMakerSharedPreferences(context, this.apiKey)
        restoreIdentifiers()
        restoreAllowedDomains()
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

    /**
     * Forget the fan completely: every identifier, the FanMaker session token,
     * and the auto-login user token. This is what a host should call when a
     * fan signs out.
     *
     * Clearing identifiers alone is not a sign-out. The session token is what
     * actually authenticates the fan - webViewHeaders sends it as
     * X-FanMaker-SessionToken and Authorization - so leaving it behind means
     * the next person to open the webview arrives already logged in as the
     * previous fan, whatever the identifiers say. Nothing else in the SDK has
     * ever cleared that token, so before this there was no way for a host to
     * end a session at all.
     */
    fun logout() {
        clearIdentifiers()
        clearSessionToken()
        Log.i("FanMakerSDK", "logged out: identifiers and session token cleared")
    }

    /**
     * Forget the FanMaker session token and the auto-login user token, leaving
     * identifiers in place.
     *
     * Identifiers are the input to auto-login - loginUserFromParamsSync posts
     * them to site/auth/auto_login - so keeping them lets the next open
     * re-authenticate the same fan. Use [logout] to forget the fan entirely.
     */
    fun clearSessionToken() {
        if (::fanMakerSharedPreferences.isInitialized) {
            fanMakerSharedPreferences.putString("token", "")
        }
        fanMakerUserToken.clear()
    }

    /**
     * Forget every identifier, in memory and on disk.
     *
     * This does NOT end the session - see [logout], which is almost certainly
     * what a sign-out path wants.
     *
     * It exists because identifiers now persist. Before that they were
     * memory-only, so process death cleared them and an integration could
     * "log out" simply by not setting them again on the next launch.
     */
    fun clearIdentifiers() {
        _userID = ""
        _memberID = ""
        _studentID = ""
        _ticketmasterID = ""
        _yinzid = ""
        _pushNotificationToken = ""
        _arbitraryIdentifiers.clear()

        if (::fanMakerSharedPreferences.isInitialized) {
            listOf(
                KEY_USER_ID, KEY_MEMBER_ID, KEY_STUDENT_ID, KEY_TICKETMASTER_ID,
                KEY_YINZID, KEY_PUSH_TOKEN, KEY_ARBITRARY_IDENTIFIERS
            ).forEach { fanMakerSharedPreferences.putString(it, "") }
        }
        Log.i("FanMakerSDK", "identifiers cleared")
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
        // A queued destination is only worth replaying for as long as it
        // plausibly belongs to the tap that queued it. deepLinkUrl used to be
        // memory-only, so it could never outlive the process; persisting it
        // without a bound would let a destination that was never opened fire on
        // some unrelated launch days later.
        if (_deepLinkUrl.isEmpty()) {
            val stored = fanMakerSharedPreferences.getString(KEY_DEEP_LINK, "") ?: ""
            if (stored.isNotEmpty()) {
                val queuedAt = fanMakerSharedPreferences.getLong(KEY_DEEP_LINK_AT, 0L)
                val age = System.currentTimeMillis() - queuedAt
                if (queuedAt > 0L && age in 0..DEEP_LINK_TTL_MS) {
                    _deepLinkUrl = stored
                } else {
                    Log.i("FanMakerSDK", "discarding a stale queued deep link (${age}ms old): $stored")
                    fanMakerSharedPreferences.putString(KEY_DEEP_LINK, "")
                }
            }
        }

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

    // First-party hosts (lowercased) the app may open inside the SDK webview.
    // Populated from the `site_details/sdk` response and persisted so a
    // cold-start push tap can classify external links before the next fetch.
    // See FanMaker/app#1885.
    var allowedDomains: List<String> = emptyList()
        private set

    private val ALLOWED_DOMAINS_KEY = "allowed_domains"

    /** Store the first-party host allowlist (from site_details/sdk) + persist it. */
    fun updateAllowedDomains(domains: List<String>) {
        allowedDomains = domains.mapNotNull { normalizeHost(it) }
        if (::fanMakerSharedPreferences.isInitialized) {
            fanMakerSharedPreferences.putString(ALLOWED_DOMAINS_KEY, allowedDomains.joinToString(","))
        }
    }

    /** Restore the persisted allowlist into memory. Call after initialize(). */
    fun restoreAllowedDomains() {
        if (!::fanMakerSharedPreferences.isInitialized) return
        val stored = fanMakerSharedPreferences.getString(ALLOWED_DOMAINS_KEY, "") ?: ""
        allowedDomains = stored.split(",").mapNotNull { normalizeHost(it) }
    }

    private fun normalizeHost(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val host = if (trimmed.contains("://")) Uri.parse(trimmed).host else trimmed.substringBefore("/")
        return host?.lowercase()?.ifEmpty { null }
    }

    /**
     * True when [url] is an http(s) link whose host is NOT first-party (not in
     * the allowlist nor the currently loaded base host), and so should be opened
     * in the system browser rather than routed into the in-app webview.
     * Custom-scheme URLs and relative paths return false. See FanMaker/app#1885.
     */
    fun isExternalWebUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false

        val allowed = allowedDomains.toMutableList()
        Uri.parse(baseUrl).host?.lowercase()?.let { allowed.add(it) }
        if (allowed.isEmpty()) return false
        return !allowed.contains(host)
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

    /**
     * True when [host] is somewhere we are willing to load inside our own
     * webview: the site's own NUX host, or one the site's allowed_domains
     * names.
     *
     * Deliberately fails CLOSED - an unknown host is not first-party. That is
     * the opposite of [isExternalWebUrl], which fails open, and the asymmetry
     * is intentional: isExternalWebUrl decides whether to eject a link that a
     * page we already trust asked us to follow, so not knowing means "carry
     * on as before". This decides whether to admit a destination we were
     * handed from outside, where not knowing has to mean "no".
     */
    private fun isFirstPartyHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val lower = host.lowercase()
        if (allowedDomains.contains(lower)) return true
        return Uri.parse(baseUrl).host?.lowercase() == lower
    }

    /**
     * True when the SDK is willing to route [url] into the FanMaker webview.
     *
     * Historically this was only true for a literal `fanmaker` hostname, so a
     * link reached NUX only if the integrator happened to shape it as
     * `scheme://fanmaker/<path>`. A push notification's click_action normally
     * carries a real https URL instead, which was silently rejected.
     */
    fun canOpenUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val urlHost = uri.host?.lowercase()

        // Legacy form: any scheme whose host is literally fanmaker. Kept
        // working so existing integrations do not have to change.
        if (urlHost == "fanmaker" || urlHost == "fanmaker.com") return true

        // A first-party web link - our own NUX host or one of the site's
        // allowed domains. This is the shape a push click_action arrives in.
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") return isFirstPartyHost(urlHost)

        return false
    }

    /**
     * Queue [path] as the next destination inside the FanMaker webview, with
     * no hostname convention at all - `openPath("/store")` is enough.
     *
     * This is the escape hatch for a host that knows a link is ours and should
     * not have to encode that knowledge as a magic hostname, and for push
     * payloads that carry a bare path.
     */
    fun openPath(path: String): Boolean {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) {
            Log.e("FanMakerSDK", "openPath called with an empty path; ignoring")
            return false
        }

        // A path must not carry a destination of its own. "//host/x" is
        // protocol-relative rather than a path - Uri resolves everything after
        // the // as an authority - so accepting it would let a caller point the
        // webview at a host of their choosing. Same for anything with an
        // explicit scheme; that is handleUrl's job, and it checks the host.
        val uri = Uri.parse(trimmed)
        if (trimmed.startsWith("//") || uri.scheme != null || uri.authority != null) {
            Log.e(
                "FanMakerSDK",
                "openPath expects a path such as /store, got something with a " +
                    "host or scheme: $trimmed (use handleUrl for a full URL)"
            )
            return false
        }

        this.deepLinkUrl = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return true
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
                // Uri.path always carries a leading slash and appendEncodedPath
                // adds its own separator, so passing it straight through
                // produced a doubled slash - https://host//store - on every
                // deep link the SDK has ever composed, legacy shape included.
                path?.trim('/')?.takeIf { it.isNotEmpty() }?.let { newUrl.appendEncodedPath(it) }
                query?.let { newUrl.encodedQuery(it) }

                // Consumed - clear it, and the persisted copy with it, so a
                // later cold start does not replay a stale destination.
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

    /**
     * Hand a URL to the SDK. Returns whether the SDK claimed it, so a host can
     * fall through to its own handling when it did not.
     *
     * [handleUrl] cannot report this itself without changing its signature from
     * void to boolean, which would break any caller already compiled against
     * it, so the answer lives here and handleUrl delegates.
     */
    fun openUrl(url: String): Boolean {
        if (!canOpenUrl(url)) {
            Log.i(
                "FanMakerSDK",
                "deep link not claimed: $url (host is not fanmaker and not a " +
                    "known first-party host; use openPath() if this is ours)"
            )
            return false
        }
        this.deepLinkUrl = url
        return true
    }

    /**
     * Hand a URL to the SDK, ignoring whether it was claimed.
     *
     * Kept returning Unit for backwards compatibility: changing the return type
     * would change the JVM descriptor from (Ljava/lang/String;)V to
     * (Ljava/lang/String;)Z, and anything already compiled against 4.0.3 - a
     * vendored copy inside a third-party plugin, for instance - would fail with
     * NoSuchMethodError on a drop-in SDK update. Use [openUrl] for the result.
     */
    fun handleUrl(url: String) {
        openUrl(url)
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
        private const val KEY_DEEP_LINK = "pending_deep_link"
        private const val KEY_DEEP_LINK_AT = "pending_deep_link_queued_at"
        // Long enough to survive a cold start from a push tap, short enough
        // that a destination nobody opened does not resurface later.
        private const val DEEP_LINK_TTL_MS = 10 * 60 * 1000L
    }
}

// Creates a singleton-like registry to hold our FanMakerSDK instances
class FanMakerSDKs() {
    companion object {
        private val instances: MutableMap<String, FanMakerSDK> = mutableMapOf()

        // Which api key each dev-defined key was registered with, persisted so
        // an instance can be rebuilt in a process that has not run the host's
        // setInstance calls yet - a cold start from a push tap, for instance.
        //
        // This cannot live in FanMakerSDK's own preferences, because those are
        // namespaced by api key and the api key is exactly what we are trying
        // to look up. Hence a separate, key-agnostic store.
        private const val REGISTRY_STORE = "com.fanmaker.sdk.instances"
        private const val REGISTRY_PREFIX = "api_key_for_"

        private fun registry(context: Context) =
            FanMakerSharedPreferences(context, REGISTRY_STORE)

        fun setInstance(context: Context, key: String, apiKey: String) {
            val instance = FanMakerSDK()
            instance.initialize(context, apiKey)
            instance.instanceKey = key
            instances[key] = instance
            registry(context).putString(REGISTRY_PREFIX + key, apiKey)
        }

        /**
         * The instance registered under [key], or null.
         *
         * This cannot rebuild a missing instance, because it has no Context to
         * rebuild one with. Prefer the [getInstance] overload that takes a
         * Context wherever one is available - notably anything reached from a
         * push notification, which may run before the host has registered
         * anything. Kept as-is for backwards compatibility.
         */
        @Suppress("UNCHECKED_CAST")
        fun getInstance(key: String): FanMakerSDK? {
            return instances[key]
        }

        /**
         * The instance registered under [key], rebuilt from persisted state if
         * this process has not registered it yet.
         *
         * A push notification can start the SDK in a cold process, before the
         * host's own setInstance calls have run - so requiring a pre-registered
         * instance meant a push tap could only work if the host happened to
         * have initialized already. The api key is remembered per key, and the
         * session token and identifiers persist on their own, so a rebuilt
         * instance is usable straight away.
         *
         * A rebuilt instance carries no host-supplied callbacks or parameters:
         * onClose, onActionTriggered and fanMakerParameters are whatever a
         * fresh instance has. Closing therefore falls back to the SDK closing
         * its own activity, which is the documented default when onClose is
         * null.
         */
        fun getInstance(context: Context, key: String): FanMakerSDK? {
            instances[key]?.let { return it }

            val apiKey = registry(context).getString(REGISTRY_PREFIX + key, "") ?: ""
            if (apiKey.isEmpty()) {
                Log.e(
                    "FanMakerSDKs",
                    "no instance for key '$key' and nothing persisted to rebuild one from; " +
                        "the host must call setInstance at least once before this key can be used"
                )
                return null
            }

            Log.i("FanMakerSDKs", "rebuilding instance for key '$key' from persisted state")
            val instance = FanMakerSDK()
            instance.initialize(context, apiKey)
            instance.instanceKey = key
            instances[key] = instance
            return instance
        }

        /** Keys this process can resolve, whether live or rebuildable. */
        fun knownKeys(context: Context): Set<String> {
            val persisted = registry(context).getSharedPreferences().all.keys
                .filter { it.startsWith(REGISTRY_PREFIX) }
                .map { it.removePrefix(REGISTRY_PREFIX) }
            return instances.keys + persisted
        }
    }
}
