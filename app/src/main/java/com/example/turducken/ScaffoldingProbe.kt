package com.example.turducken

import android.content.Context
import android.util.Log
import com.fanmaker.sdk.FanMakerSDKWebView
import com.fanmaker.sdk.FanMakerSDKs

/**
 * Probe for launch de-duplication and self-scaffolding.
 *
 * The scaffolding case is that a push notification can start the SDK in a cold
 * process, before the host's own setInstance calls have run. To test that
 * in-process, this registers a key on the first launch and then, on every
 * later launch, resolves it *without* registering it again - so the instance
 * map is empty for that key and the SDK has to rebuild it from persisted
 * state. MainActivity only ever registers devDefinedKey1 and devDefinedKey2,
 * so the probe's own key stays absent.
 *
 * grep logcat for [ScaffoldProbe].
 *
 * The end-to-end version of the same thing cannot be driven from inside the
 * app, because it needs a process where MainActivity never ran at all:
 *
 *   adb shell am start -n com.example.turducken/.MainActivity   # register once
 *   adb shell am force-stop com.example.turducken               # kill it
 *   adb shell am start -n com.example.turducken/com.fanmaker.sdk.FanMakerSDKWebView \
 *     --es fanMakerKey devDefinedKey1 --es fanMakerDeepLink /store
 *   # expect: "rebuilding instance for key 'devDefinedKey1' from persisted state"
 *   # before this change: "Failed to get instance of FanMakerSDK." and the activity died
 */
object ScaffoldingProbe {
    private const val TAG = "ScaffoldProbe"
    private const val SCAFFOLD_KEY = "scaffoldProbeKey"
    private const val SCAFFOLD_API_KEY = "scaffold-probe-local-only"

    fun run(context: Context) {
        Log.i(TAG, "---- de-dup registry and self-scaffolding ----")
        var pass = 0
        var fail = 0
        fun check(ok: Boolean, note: String) {
            if (ok) pass++ else fail++
            Log.i(TAG, "${if (ok) "ok  " else "FAIL"}  $note")
        }

        // --- de-dup registry, with nothing open ---
        check(FanMakerSDKWebView.runningKeys.isEmpty(),
            "runningKeys is empty with no webview open (was ${FanMakerSDKWebView.runningKeys})")
        check(!FanMakerSDKWebView.isRunning("devDefinedKey1"),
            "isRunning(devDefinedKey1) is false with nothing open")

        // --- self-scaffolding ---
        val known = FanMakerSDKs.knownKeys(context)
        if (!known.contains(SCAFFOLD_KEY)) {
            Log.i(TAG, "first run: registering $SCAFFOLD_KEY so a later process can rebuild it")
            FanMakerSDKs.setInstance(context, SCAFFOLD_KEY, SCAFFOLD_API_KEY)
            check(FanMakerSDKs.knownKeys(context).contains(SCAFFOLD_KEY),
                "the key is remembered after setInstance")
            Log.i(TAG, "first run: RESULT INCONCLUSIVE - force-stop and launch again")
        } else {
            // This process never called setInstance for this key, so a plain
            // getInstance must miss and the Context-aware one must rebuild.
            val withoutContext = FanMakerSDKs.getInstance(SCAFFOLD_KEY)
            check(withoutContext == null,
                "getInstance(key) alone cannot resolve an unregistered key - it has no Context to rebuild with")

            val rebuilt = FanMakerSDKs.getInstance(context, SCAFFOLD_KEY)
            check(rebuilt != null, "getInstance(context, key) rebuilt the instance from persisted state")
            check(rebuilt?.apiKey == SCAFFOLD_API_KEY,
                "the rebuilt instance carries the right api key (${rebuilt?.apiKey})")
            check(rebuilt?.isInitialized() == true, "the rebuilt instance reports itself initialized")

            // Second call must hand back the same object, not build another.
            check(FanMakerSDKs.getInstance(SCAFFOLD_KEY) === rebuilt,
                "once rebuilt it is cached, so a plain getInstance now finds it")
        }

        // An unknown key must stay unresolvable rather than inventing an instance.
        check(FanMakerSDKs.getInstance(context, "neverRegisteredKey") == null,
            "an unknown key resolves to null rather than a bogus instance")

        Log.i(TAG, "RESULT: ${if (fail == 0) "PASS" else "FAIL"}  ($pass passed, $fail failed)")
        Log.i(TAG, "---- end probe ----")
    }
}
