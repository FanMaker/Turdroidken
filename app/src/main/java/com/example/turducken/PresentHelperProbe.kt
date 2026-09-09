package com.example.turducken

import android.content.Context
import android.util.Log
import com.fanmaker.sdk.FanMakerSDK
import com.fanmaker.sdk.FanMakerSDKWebView
import com.fanmaker.sdk.FanMakerSDKs

/**
 * Probe for present(), the counterpart of the iOS SDK's own present().
 *
 * A host used to have to build Intent(context, FanMakerSDKWebView::class.java)
 * and attach the dev-defined key as a "fanMakerKey" extra - a magic string that
 * fails silently when it is wrong. present() removes both, which means the SDK
 * has to know its own key.
 *
 * grep logcat for [PresentProbe].
 */
object PresentHelperProbe {
    private const val TAG = "PresentProbe"
    private const val KEY = "presentProbeKey"
    private const val API_KEY = "present-probe-local-only"

    fun run(context: Context) {
        Log.i(TAG, "---- present() helper ----")
        var pass = 0
        var fail = 0
        fun check(ok: Boolean, note: String) {
            if (ok) pass++ else fail++
            Log.i(TAG, "${if (ok) "ok  " else "FAIL"}  $note")
        }

        FanMakerSDKs.setInstance(context, KEY, API_KEY)
        val sdk = FanMakerSDKs.getInstance(context, KEY) ?: run {
            Log.e(TAG, "no probe instance"); return
        }

        // The key is what makes present() possible at all.
        check(sdk.instanceKey == KEY, "a registered instance knows its own key ('${sdk.instanceKey}')")
        check(FanMakerSDKs.getInstance(context, KEY)?.instanceKey == KEY,
            "and still knows it when resolved again")

        // An instance built directly has no key, so it must refuse rather than
        // launching an activity that would immediately fail to resolve it.
        val orphan = FanMakerSDK()
        orphan.initialize(context, API_KEY)
        check(orphan.instanceKey.isEmpty(), "an instance built directly has no key")
        check(!orphan.present(context), "and present() refuses rather than launching a doomed activity")

        // Uninitialized must also refuse.
        check(!FanMakerSDK().present(context), "an uninitialized instance refuses to present")

        // A bad path is rejected before anything is started, so the fan is not
        // sent to a screen that then loads the wrong place.
        check(!sdk.present(context, "//evil.example.com/x"),
            "present() with a host-smuggling path refuses before starting anything")
        check(sdk.deepLinkUrl.isEmpty(), "and queued nothing")

        // Nothing on screen yet.
        check(!sdk.isPresenting, "isPresenting is false before presenting")
        check(!FanMakerSDKWebView.isRunning(KEY), "and the activity registry agrees")

        Log.i(TAG, "RESULT: ${if (fail == 0) "PASS" else "FAIL"}  ($pass passed, $fail failed)")
        Log.i(TAG, "---- end probe ----")
    }

    /** Actually launches, from a real Activity context. Watch the screen. */
    fun launch(activity: android.app.Activity) {
        FanMakerSDKs.setInstance(activity, KEY, API_KEY)
        val sdk = FanMakerSDKs.getInstance(activity, KEY) ?: return
        val started = sdk.present(activity, "/store")
        Log.i(TAG, "present(activity, \"/store\") -> $started, queued='${sdk.deepLinkUrl}'")
    }
}
