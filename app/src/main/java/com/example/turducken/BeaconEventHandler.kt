package com.example.turducken

import android.util.Log

import com.fanmaker.sdk.FanMakerSDKBeaconEventHandler
import com.fanmaker.sdk.FanMakerSDKBeaconManager
import com.fanmaker.sdk.FanMakerSDKBeaconRegion

class BeaconEventHandler : FanMakerSDKBeaconEventHandler {
    override fun onBeaconRegionsReceived(manager: FanMakerSDKBeaconManager, regions: Array<FanMakerSDKBeaconRegion>) {
        Log.d(TAG, "Regions received: ${regions.joinToString()}")
        manager.startScanning(regions)
    }

    companion object {
        const val TAG = "BeaconEventHandler"
    }
}