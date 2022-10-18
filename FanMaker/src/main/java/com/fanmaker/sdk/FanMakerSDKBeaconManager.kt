package com.fanmaker.sdk

import android.app.Application
import android.util.Log

import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.MonitorNotifier
import org.json.JSONObject

class FanMakerSDKBeaconManager(private val application: Application) {
    private val beaconManager = BeaconManager.getInstanceForApplication(application)
    var eventHandler: FanMakerSDKBeaconEventHandler? = null

    init {
        beaconManager.beaconParsers.clear()
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout(BEACON_LAYOUT)
        )
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
                    if (eventHandler != null) {
                        when (action) {
                            "enter" -> eventHandler!!.onBeaconRegionEnter(this, region)
                            "exit" -> eventHandler!!.onBeaconRegionExit(this, region)
                        }
                    } else {
                        Log.d(TAG, "${action.uppercase()}: $region")
                    }
                }
            }
        }
    }

    private fun postBeaconRegionAction(
        region: FanMakerSDKBeaconRegion,
        action: String,
        onSuccess: (JSONObject) -> Unit
    ) {
        val body = mapOf<String, String>(
            "beacon_region_id" to region.id.toString(),
            "action_type" to action
        )
        val http = FanMakerSDKHttp(application.applicationContext)
        http.post("beacon_region_actions", body, onSuccess) { errorCode, errorMessage ->
            Log.e(TAG, "$errorCode: $errorMessage")
        }
    }

    companion object {
        const val BEACON_LAYOUT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"
        const val TAG = "FanMakerSDKBeaconManager"
    }
}