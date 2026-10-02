package com.example.turducken

import android.util.Log
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.service.RangedBeacon

/**
 * Regression probe for the AltBeacon RSSI-filter minification crash.
 *
 * AltBeacon builds its RSSI filter reflectively, from a public no-arg
 * constructor that nothing calls directly. Without a keep rule, R8 strips that
 * constructor, `RangedBeacon.mFilter` stays null, and the first ranged sample
 * throws an uncaught NPE on a beacon executor thread — killing the host
 * process. See FanMaker/consumer-rules.pro.
 *
 * This drives the exact failing frame (RangedBeacon.addMeasurement ->
 * filter.addMeasurement) without needing a beacon in range, and reports rather
 * than crashes, so a minified and a non-minified build can be compared
 * directly. Run it and grep logcat for [BeaconProbe].
 */
object BeaconMinificationProbe {
    private const val TAG = "BeaconProbe"

    fun run() {
        Log.i(TAG, "---- AltBeacon RSSI filter minification probe ----")

        val filterClass = BeaconManager.getRssiFilterImplClass()
        Log.i(TAG, "rssiFilterImplClass = ${filterClass?.name}")

        // 1. The reflective construction AltBeacon itself performs.
        val reflectiveOk = try {
            val ctors = filterClass?.constructors ?: emptyArray()
            Log.i(TAG, "public constructors visible to reflection = ${ctors.size}")
            val instance = ctors.firstOrNull()?.newInstance()
            Log.i(TAG, "newInstance() -> $instance")
            instance != null
        } catch (t: Throwable) {
            Log.e(TAG, "newInstance() FAILED: ${t.javaClass.name}: ${t.message}")
            false
        }

        // 2. The actual crash site, end to end.
        val addMeasurementOk = try {
            val beacon = Beacon.Builder()
                .setId1("2686f39c-bada-4658-854a-a62e7e5e8b8d")
                .setId2("1")
                .setId3("0")
                .setRssi(-59)
                .setTxPower(-59)
                .build()
            RangedBeacon(beacon).addMeasurement(-59)
            Log.i(TAG, "RangedBeacon.addMeasurement(-59) -> ok")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "RangedBeacon.addMeasurement(-59) THREW ${t.javaClass.name}: ${t.message}")
            t.stackTrace.take(4).forEach { Log.e(TAG, "    at $it") }
            false
        }

        if (reflectiveOk && addMeasurementOk) {
            Log.i(TAG, "RESULT: PASS - rssi filter constructible, ranging would survive")
        } else {
            Log.e(TAG, "RESULT: FAIL - rssi filter stripped by R8; ranging will kill the process")
        }
        Log.i(TAG, "---- end probe ----")
    }
}
