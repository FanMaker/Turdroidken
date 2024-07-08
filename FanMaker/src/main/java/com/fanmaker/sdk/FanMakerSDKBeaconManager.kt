package com.fanmaker.sdk

import android.app.Application
import android.content.Context
import android.util.Log

import android.content.SharedPreferences
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.MonitorNotifier
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
        beaconManager.backgroundBetweenScanPeriod = 20000L;
        beaconManager.backgroundScanPeriod = 20000L;
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
    }
}
