package com.fanmaker.sdk

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log

import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.MonitorNotifier
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONStringer
import org.json.JSONTokener

class FanMakerSDKBeaconManager(private val application: Application) {
    private val beaconManager = BeaconManager.getInstanceForApplication(application)
    var eventHandler: FanMakerSDKBeaconEventHandler? = null

    init {
        beaconManager.beaconParsers.clear()
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout(BEACON_LAYOUT)
        )

        Log.d(TAG, "Cleaning queues")
        updateQueue(RANGE_ACTIONS_HISTORY, emptyArray())
        updateQueue(RANGE_ACTIONS_SEND_LIST, emptyArray())
    }

    fun fetchBeaconRegions() {
        if (eventHandler != null) {
            fetchBeaconRegions { regions ->
                eventHandler!!.onBeaconRegionsReceived(this, regions)
            }
        } else {
            fetchBeaconRegions { regions ->
                Log.d(TAG, "Beacon Regions received: ${regions.joinToString()}")
            }
        }
    }

    fun fetchBeaconRegions(onSuccess: (Array<FanMakerSDKBeaconRegion>) -> Unit) {
        val http = FanMakerSDKHttp(application.applicationContext)
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
            beaconManager.startMonitoring(region.region)

            val regionViewModel = beaconManager.getRegionViewModel(region.region)
            regionViewModel.regionState.observeForever { state ->
                val action = if (state == MonitorNotifier.OUTSIDE) "exit" else "enter"
                postBeaconRegionAction(region, action) {
                    when (action) {
                        "enter" -> onBeaconRegionEnter(region)
                        "exit" -> onBeaconRegionExit(region)
                    }
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
                updateQueue(RANGE_ACTIONS_HISTORY, queue)
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
        val http = FanMakerSDKHttp(application.applicationContext)
        http.post("beacon_region_actions", body, onSuccess) { errorCode, errorMessage ->
            Log.e(TAG, "$errorCode: $errorMessage")
        }
    }

    private fun postBeaconRangeActions(actions: Array<FanMakerSDKBeaconRangeAction>) {
        val queue = rangeActionsSendList() + actions
        if (queue.isEmpty()) return

        val body = mapOf("beacons" to queue.map { it.toParams() })
        val http = FanMakerSDKHttp(application.applicationContext)
        http.post("beacon_range_actions", body, {
            updateQueue(RANGE_ACTIONS_SEND_LIST, emptyArray())
            Log.d(TAG, "${queue.size} beacon range actions successfully posted")
        }, { errorCode, errorMessage ->
            Log.e(TAG, "$errorCode: $errorMessage")
            updateQueue(RANGE_ACTIONS_SEND_LIST, queue)
            Log.d(TAG, "${queue.size} beacon range actions added to the send list")
        })
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
        val json = value.takeLast(HISTORY_LIMIT).reversed().map { it.toJSON() }
        val editor = readSettings().edit()
        editor.putString(key, "[${json.joinToString()}]")
        editor.commit()
    }

    private fun readSettings(): SharedPreferences {
        return application
            .applicationContext
            .getSharedPreferences("com.fanmaker.sdk", Context.MODE_PRIVATE)
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
            return beacon.seenAt - sameBeaconActions.last().seenAt >= 60000
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