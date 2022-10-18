package com.fanmaker.sdk

import android.util.Log

interface FanMakerSDKBeaconEventHandler {
    fun onBeaconRegionsReceived(
        manager: FanMakerSDKBeaconManager,
        regions: Array<FanMakerSDKBeaconRegion>
    ) {
        Log.d(TAG, "Beacon Regions received: ${regions.joinToString()}")
    }

    fun onBeaconRegionEnter(manager: FanMakerSDKBeaconManager, region: FanMakerSDKBeaconRegion) {
        Log.d(TAG, "Beacon Region ENTER: $region")
    }

    fun onBeaconRegionExit(manager: FanMakerSDKBeaconManager, region: FanMakerSDKBeaconRegion) {
        Log.d(TAG, "Beacon Region EXIT: $region")
    }

    companion object {
        const val TAG = "FanMakerSDKBeaconEventHandler"
    }
}