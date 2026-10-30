package com.fanmaker.sdk

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

import android.content.SharedPreferences
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.MonitorNotifier
import org.altbeacon.beacon.service.BeaconService
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class FanMakerSDKBeaconManager(
    private val fanMakerSDK: FanMakerSDK,
    private val application: Application
){
    private val beaconManager = BeaconManager.getInstanceForApplication(application)
    private var fanMakerSharedPreferences = FanMakerSharedPreferences(application.applicationContext, fanMakerSDK.apiKey)

    var eventHandler: FanMakerSDKBeaconEventHandler? = null
    var beaconUniquenessThrottle = 60000

    init {
        beaconManager.beaconParsers.clear()
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout(BEACON_LAYOUT)
        )

        beaconManager.monitoredRegions.forEach { beaconManager.stopMonitoring(it) }

        beaconManager.setEnableScheduledScanJobs(false);
        // The AltBeacon manager is shared by every instance, so a second one
        // must not undo the cadence enableForegroundServiceScanning set.
        if (!foregroundServiceScanning) {
            beaconManager.backgroundBetweenScanPeriod = 20000L;
            beaconManager.backgroundScanPeriod = 20000L;
        }
    }

    /**
     * Keep scanning while the app is in the background. Android 8+ otherwise
     * stops background scanning within minutes of the app leaving the screen;
     * this runs the scan in AltBeacon's foreground service instead, which
     * shows [notification] for as long as it runs. Leave [notification] out
     * and the SDK builds one, see [defaultScanNotification]; pass your own to
     * change its wording or icon.
     *
     * Call it once, before the first [fetchBeaconRegions]. It covers every SDK
     * instance in the app. The manifest needs the entries listed in the
     * README's "Scanning in the background" section; when one is missing this
     * logs which, returns false, and beacons scan the default way.
     *
     * Scanning then waits for the location grant (and Nearby devices on
     * Android 12+): starting a location foreground service without it crashes
     * the app on Android 14+. Call [fetchBeaconRegions] again once granted.
     */
    @JvmOverloads
    fun enableForegroundServiceScanning(
        notification: Notification? = null,
        notificationId: Int = FOREGROUND_SERVICE_NOTIFICATION_ID
    ): Boolean {
        val packageManager = application.packageManager
        val requested = packageManager
            .getPackageInfo(application.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.toList().orEmpty()
        val serviceTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            packageManager.getServiceInfo(ComponentName(application, BeaconService::class.java), 0)
                .foregroundServiceType
        } else null
        missingForegroundServiceSetup(requested, serviceTypes)?.let {
            Log.e(TAG, "foreground service scanning not enabled: AndroidManifest.xml needs $it")
            return false
        }

        try {
            beaconManager.enableForegroundServiceScanning(notification ?: defaultScanNotification(), notificationId)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "foreground service scanning not enabled: call it before fetchBeaconRegions, beacons are already scanning")
            return false
        }
        beaconManager.foregroundScanPeriod = SCAN_PERIOD_MS
        beaconManager.foregroundBetweenScanPeriod = BETWEEN_SCAN_PERIOD_MS
        beaconManager.backgroundScanPeriod = SCAN_PERIOD_MS
        beaconManager.backgroundBetweenScanPeriod = BETWEEN_SCAN_PERIOD_MS
        foregroundServiceScanning = true
        Log.i(TAG, "beacons scan in a foreground service: $SCAN_PERIOD_MS ms every ${SCAN_PERIOD_MS + BETWEEN_SCAN_PERIOD_MS} ms")
        return true
    }

    fun fetchBeaconRegions() {
        if (eventHandler != null) {
            fetchBeaconRegions { regions ->
                eventHandler!!.onBeaconRegionsReceived(this, regions)
            }
        } else {
            fetchBeaconRegions { regions ->
                Log.d(TAG, "Beacon Regions received: ${regions.joinToString()}")
                startScanning(regions)
            }
        }
    }

    fun fetchBeaconRegions(onSuccess: (Array<FanMakerSDKBeaconRegion>) -> Unit) {
        val http = FanMakerSDKHttp(fanMakerSDK, application.applicationContext)

        http.get("site_details/sdk", { response ->
            try {
                val data = response.getJSONObject("data")
                val beacons = data.getJSONObject("beacons")
                beaconUniquenessThrottle =
                    beacons.getString("uniqueness_throttle").toInt() * 1000
                Log.d(TAG, "beaconUniquenessThrottle set to $beaconUniquenessThrottle milliseconds")

                // First-party host allowlist for push deep-link routing.
                // See FanMaker/app#1885.
                val allowedDomainsJson = data.optJSONArray("allowed_domains")
                if (allowedDomainsJson != null) {
                    val domains = (0 until allowedDomainsJson.length()).map { allowedDomainsJson.getString(it) }
                    fanMakerSDK.updateAllowedDomains(domains)
                }
            } catch (err: Exception) {
                Log.e(TAG, err.toString())
            }
        }, { errorCode, errorMessage ->
            Log.e(TAG, "$errorCode: $errorMessage")
        })

        http.get("beacon_regions", { response ->
            val regionsData = response.getJSONArray("data")
            val regions = Array(regionsData.length()) { index ->
                val regionData = regionsData.getJSONObject(index)
                FanMakerSDKBeaconRegion(regionData)
            }
            onSuccess(regions)
        }, { errorCode, errorMessage ->
            Log.e(TAG, "$errorCode: $errorMessage")
        })
    }

    fun startScanning(regions: Array<FanMakerSDKBeaconRegion>) {
        // With the foreground service on, scanning is what starts it, and on
        // Android 14+ starting it without the location grant crashes - it did
        // in appStream on fresh installs. Without the service, scanning early
        // is harmless and picks up once the fan grants access.
        if (foregroundServiceScanning && !hasScanPermissions()) {
            Log.w(TAG, "not scanning yet: location and Nearby devices are not granted; call fetchBeaconRegions again once they are")
            return
        }
        regions.map { region ->
            Log.d(TAG, "Starting monitoring for ${region.region}")
            beaconManager.startMonitoring(region.region)

            val regionViewModel = beaconManager.getRegionViewModel(region.region)
            regionViewModel.regionState.observeForever { state ->
                val action = if (state == MonitorNotifier.OUTSIDE) "exit" else "enter"
                postBeaconRegionAction(region, action) { }
                when (action) {
                    "enter" -> onBeaconRegionEnter(region)
                    "exit" -> onBeaconRegionExit(region)
                }
            }
        }
    }

    /**
     * The app's name and appStream's wording, on a low-importance channel.
     * Android draws a notification icon as a one-colour silhouette, so a
     * full-colour app icon would come out as a plain shape: this uses the push
     * icon the host declares for Firebase when there is one, and the system
     * Bluetooth icon otherwise.
     */
    private fun defaultScanNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            application.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(SCAN_CHANNEL_ID, "Beacon scanning", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
        val packageManager = application.packageManager
        val info = packageManager.getApplicationInfo(application.packageName, PackageManager.GET_META_DATA)
        val pushIcon = info.metaData?.getInt("com.google.firebase.messaging.default_notification_icon") ?: 0
        return NotificationCompat.Builder(application, SCAN_CHANNEL_ID)
            .setSmallIcon(if (pushIcon != 0) pushIcon else android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(packageManager.getApplicationLabel(info))
            .setContentText("Scanning Bluetooth Beacons for proximity based rewards and offers")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    private fun hasScanPermissions(): Boolean {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(application, permission) == PackageManager.PERMISSION_GRANTED
        val location = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val nearbyDevices = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            granted(Manifest.permission.BLUETOOTH_SCAN)
        return location && nearbyDevices
    }

    private fun onBeaconRegionEnter(region: FanMakerSDKBeaconRegion) {
        if (eventHandler != null) {
            eventHandler!!.onBeaconRegionEnter(this, region)
        } else {
            Log.d(TAG, "ENTER: $region")
        }

        beaconManager.startRangingBeacons(region.region)
        val regionViewModel = beaconManager.getRegionViewModel(region.region)
        regionViewModel.rangedBeacons.observeForever { beacons ->
            var queue = rangeActionsHistory()
            var newActions = emptyArray<FanMakerSDKBeaconRangeAction>()

            beacons.forEach { beacon ->
                val beaconRangeAction = FanMakerSDKBeaconRangeAction(beacon)
                if (shouldAppend(beaconRangeAction, queue)) {
                    queue += beaconRangeAction
                    newActions += beaconRangeAction
                    Log.d(TAG, "queue is now ${queue.size}")
                    Log.d(TAG, "newActions is now ${newActions.size}")
                }
            }

            if (newActions.isNotEmpty()) {
                updateQueue(RANGE_ACTIONS_HISTORY, queue.takeLast(HISTORY_LIMIT).reversed().toTypedArray())
                Log.d(TAG, "Range Actions to post: $newActions")
                postBeaconRangeActions(newActions)
            }
        }
    }

    private fun onBeaconRegionExit(region: FanMakerSDKBeaconRegion) {
        if (eventHandler != null) {
            eventHandler!!.onBeaconRegionExit(this, region)
        } else {
            Log.d(TAG, "EXIT: $region")
        }

        beaconManager.stopRangingBeacons(region.region)
    }

    private fun postBeaconRegionAction(
        region: FanMakerSDKBeaconRegion,
        action: String,
        onSuccess: (JSONObject) -> Unit
    ) {
        val body = mapOf(
            "beacon_region_id" to region.id.toString(),
            "action_type" to action
        )
        val userToken = fanMakerSharedPreferences.getString("token", "")
        val http = FanMakerSDKHttp(fanMakerSDK, application.applicationContext, userToken)
        http.post("beacon_region_actions", body, onSuccess) { errorCode, errorMessage ->
            Log.e(TAG, "$errorCode: $errorMessage")
        }
    }

    private fun postBeaconRangeActions(actions: Array<FanMakerSDKBeaconRangeAction>) {
        val queue = rangeActionsSendList() + actions
        if (queue.isEmpty()) return

        val body = mapOf("beacons" to queue.map { it.toParams() })
        val userToken = fanMakerSharedPreferences.getString("token", "")

        val http = FanMakerSDKHttp(fanMakerSDK, application.applicationContext, userToken)

        http.post("beacon_range_actions", body, {
            updateQueue(RANGE_ACTIONS_SEND_LIST, emptyArray())
            Log.d(TAG, "${queue.size} beacon range actions successfully posted")
            Log.d(TAG, body.toString())
        }, { errorCode, errorMessage ->
            Log.e(TAG, "$errorCode: $errorMessage")
            updateQueue(RANGE_ACTIONS_SEND_LIST, queue)
            Log.d(TAG, "${queue.size} beacon range actions in the send list")
            Timer().schedule(object: TimerTask() {
                override fun run() {
                    sendList()
                }
            }, 1000 * 60)
        })
    }

    private fun sendList() {
        postBeaconRangeActions(emptyArray())
    }

    private fun rangeActionsHistory(): Array<FanMakerSDKBeaconRangeAction> {
        return getQueue(RANGE_ACTIONS_HISTORY)
    }

    private fun rangeActionsSendList(): Array<FanMakerSDKBeaconRangeAction> {
        return getQueue(RANGE_ACTIONS_SEND_LIST)
    }

    private fun getQueue(key: String): Array<FanMakerSDKBeaconRangeAction> {
        val settings = readSettings()
        val json = settings.getString(key, "[]")
        val data = JSONArray(json)

        return Array(data.length()) { index ->
            val regionData = data.getJSONObject(index)
            FanMakerSDKBeaconRangeAction(regionData)
        }
    }

    private fun updateQueue(key: String, value: Array<FanMakerSDKBeaconRangeAction>) {
        val json = value.map { it.toJSON() }
        // val editor = readSettings().edit()
        // editor.putString(key, "[${json.joinToString()}]")
        // editor.commit()

        fanMakerSharedPreferences.putString(key, "[${json.joinToString()}]")
        fanMakerSharedPreferences.commit()
    }

    private fun readSettings(): SharedPreferences {
        // return application
        //     .applicationContext
        //     .getSharedPreferences("com.fanmaker.sdk", Context.MODE_PRIVATE)
        return fanMakerSharedPreferences.getSharedPreferences()
    }

    private fun shouldAppend(
        beacon: FanMakerSDKBeaconRangeAction,
        queue: Array<FanMakerSDKBeaconRangeAction>
    ): Boolean {
        val sameBeaconActions = queue.filter { queueAction ->
            queueAction.uuid == beacon.uuid &&
                    queueAction.major == beacon.major &&
                    queueAction.minor == beacon.minor
        }

        if (sameBeaconActions.isEmpty()) {
            return true
        } else {
            return beacon.seenAt - sameBeaconActions.last().seenAt >= beaconUniquenessThrottle
        }
    }

    companion object {
        const val BEACON_LAYOUT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"
        const val TAG = "FanMakerSDKBeaconManager"
        const val RANGE_ACTIONS_HISTORY = "RANGE_ACTIONS_HISTORY"
        const val RANGE_ACTIONS_SEND_LIST = "RANGE_ACTIONS_SEND_LIST"
        const val HISTORY_LIMIT = 1000

        // Foreground service cadence: a 1.5 s scan twice a minute, about two
        // chances to see a beacon inside the API's default 60 s uniqueness
        // window. appStream shipped these values; they do not follow a site's
        // own uniqueness_throttle.
        private const val SCAN_PERIOD_MS = 1_500L
        private const val BETWEEN_SCAN_PERIOD_MS = 27_000L
        private const val FOREGROUND_SERVICE_NOTIFICATION_ID = 2001
        private const val SCAN_CHANNEL_ID = "fanmaker_beacon_scanning"

        // AltBeacon's BeaconManager is one per process, so this is too.
        @Volatile private var foregroundServiceScanning = false
    }
}

/**
 * The manifest entry a host still needs for AltBeacon's foreground service, or
 * null. Each one is a crash when the service starts, not a quiet failure. The
 * location type and its permission only bite on Android 14+, but are checked
 * everywhere so a build tested on an older phone cannot ship without them.
 * [serviceTypes] is null below Android 10, where it cannot be read.
 */
internal fun missingForegroundServiceSetup(requested: Collection<String>, serviceTypes: Int?): String? = when {
    Manifest.permission.FOREGROUND_SERVICE !in requested ->
        "<uses-permission android:name=\"android.permission.FOREGROUND_SERVICE\" />"
    Manifest.permission.FOREGROUND_SERVICE_LOCATION !in requested ->
        "<uses-permission android:name=\"android.permission.FOREGROUND_SERVICE_LOCATION\" />"
    serviceTypes != null && (serviceTypes and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) == 0 ->
        "android:foregroundServiceType=\"location\" on <service android:name=\"org.altbeacon.beacon.service.BeaconService\">"
    else -> null
}
