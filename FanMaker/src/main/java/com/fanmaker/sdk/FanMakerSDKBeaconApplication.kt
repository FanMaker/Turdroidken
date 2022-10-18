package com.fanmaker.sdk

import android.app.Application
import android.util.Log

import androidx.lifecycle.Observer

import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.MonitorNotifier
import org.altbeacon.beacon.Region

class FanMakerSDKBeaconApplication: Application() {
    lateinit var region: Region

    override fun onCreate() {
        super.onCreate()

        val beaconManager = BeaconManager.getInstanceForApplication(this)
        beaconManager.beaconParsers.clear()
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
        )

        region = Region("all-beacons", Identifier.parse("f7826da6-4fa2-4e98-8024-bc5b71e0893e"), null, null)
        beaconManager.startMonitoring(region)

        val regionViewModel = BeaconManager.getInstanceForApplication(this).getRegionViewModel(region)
        regionViewModel.regionState.observeForever(centralMonitoringObserver)
        Log.d(TAG, "Monitoring for beacons")
    }

    val centralMonitoringObserver = Observer<Int> { state ->
        if (state == MonitorNotifier.OUTSIDE) {
            Log.d(TAG, "outside beacon region: " + region)
        } else {
            Log.d(TAG, "inside beacon region" + region)
        }
    }

    companion object {
        val TAG = "FanMaker SDK Beacon Application"
    }
}