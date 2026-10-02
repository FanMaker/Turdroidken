package com.example.turducken

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.fanmaker.sdk.FanMakerSDK
import com.fanmaker.sdk.FanMakerSDKs

/**
 * Probe for the integrator-call-order and identifier-persistence behaviours.
 *
 * The sample app registers its lifecycle observer from onCreate, while the
 * lifecycle is still CREATED, so onResume arrives later - after location
 * tracking has been enabled. Third-party hosts that stand the SDK up from a
 * plugin register it against an already-RESUMED lifecycle instead, which
 * dispatches onResume synchronously, before the host enables tracking. This
 * probe reproduces that order deliberately.
 *
 * Run twice - once to write identifiers, then force-stop and run again to see
 * whether they survived process death:
 *
 *   adb shell am start -n com.example.turducken/.MainActivity
 *   adb shell am force-stop com.example.turducken
 *   adb shell am start -n com.example.turducken/.MainActivity
 *   adb logcat | grep SdkProbe
 */
object SdkOrderingProbe {
    private const val TAG = "SdkProbe"
    private const val PROBE_KEY = "orderingProbe"
    private const val PROBE_API_KEY = "ordering-probe-local-only"

    fun run(activity: AppCompatActivity) {
        FanMakerSDKs.setInstance(activity, PROBE_KEY, PROBE_API_KEY)
        val sdk = FanMakerSDKs.getInstance(PROBE_KEY) ?: run {
            Log.e(TAG, "could not get probe instance"); return
        }

        identifierPersistence(sdk)
        callOrdering(activity, sdk)
    }

    /** A04 - do identifiers set in a previous process survive a cold start? */
    private fun identifierPersistence(sdk: FanMakerSDK) {
        Log.i(TAG, "---- identifier persistence ----")
        val restored = sdk.userID
        if (restored.isEmpty()) {
            Log.i(TAG, "run 1: no persisted userID; writing probe identifiers")
            sdk.userID = "probe-user-42"
            sdk.memberID = "probe-member-99"
            sdk.yinzid = "probe-yinz-7"
            sdk.arbitraryIdentifiers["probe_key"] = "probe_value"
            Log.i(TAG, "run 1: wrote userID=${sdk.userID} memberID=${sdk.memberID}")
            Log.i(TAG, "run 1: RESULT INCONCLUSIVE - force-stop and launch again to check")
        } else {
            val ok = restored == "probe-user-42" &&
                sdk.memberID == "probe-member-99" &&
                sdk.yinzid == "probe-yinz-7" &&
                sdk.arbitraryIdentifiers["probe_key"] == "probe_value"
            Log.i(TAG, "run 2: userID=$restored memberID=${sdk.memberID} yinzid=${sdk.yinzid} arbitrary=${sdk.arbitraryIdentifiers}")
            Log.i(TAG, if (ok) "run 2: RESULT PASS - identifiers survived process death"
                       else    "run 2: RESULT FAIL - identifiers did not round-trip")

            // Persistence removed the implicit reset that process death used to
            // provide, so clearIdentifiers() has to actually clear - in memory
            // and on disk. If it only cleared memory, the next cold start would
            // resurrect the previous fan's identifiers.
            sdk.clearIdentifiers()
            val clearedInMemory = sdk.userID.isEmpty() && sdk.memberID.isEmpty() &&
                sdk.yinzid.isEmpty() && sdk.arbitraryIdentifiers.isEmpty()
            Log.i(TAG, if (clearedInMemory) "run 2: clearIdentifiers cleared memory - ok"
                       else    "run 2: clearIdentifiers FAILED to clear memory")
            Log.i(TAG, "run 2: force-stop and launch once more; run 3 must report NO persisted userID")
        }
    }

    /** A02 + A03 - the dropped cold-start ping, and what gets logged about it. */
    private fun callOrdering(activity: AppCompatActivity, sdk: FanMakerSDK) {
        Log.i(TAG, "---- call ordering (host registers against a RESUMED lifecycle) ----")
        Log.i(TAG, "locationEnabled before addObserver = ${sdk.isLocationTrackingEnabled()}")

        // Dispatches ON_CREATE/ON_START/ON_RESUME synchronously, right here.
        Log.i(TAG, "addObserver() -> expect the SDK's onResume to run inline, with tracking still off")
        activity.lifecycle.addObserver(sdk)

        // WMT's plugin enables tracking ~2.7s after init. Before the fix that
        // ping was gone for good; now the setter should notice and catch up.
        Handler(Looper.getMainLooper()).postDelayed({
            Log.i(TAG, "+2700ms: host now sets locationEnabled = true (the turducken order)")
            sdk.locationEnabled = true
            Log.i(TAG, "locationEnabled after = ${sdk.isLocationTrackingEnabled()}")
            Log.i(TAG, "---- end probe ----")
        }, 2700)
    }
}
