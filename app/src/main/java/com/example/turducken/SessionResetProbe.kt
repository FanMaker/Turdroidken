package com.example.turducken

import android.content.Context
import android.util.Log
import com.fanmaker.sdk.FanMakerSDKs

/**
 * Probe for what a sign-out actually clears.
 *
 * The session token is what authenticates the fan: webViewHeaders sends it as
 * X-FanMaker-SessionToken and Authorization. Clearing identifiers without
 * clearing that token leaves the next person to open the webview logged in as
 * the previous fan, so this checks the headers rather than the fields - the
 * headers are what the server sees.
 *
 * grep logcat for [SessionProbe].
 */
object SessionResetProbe {
    private const val TAG = "SessionProbe"
    private const val KEY = "sessionResetProbe"
    private const val API_KEY = "session-reset-probe-local-only"

    fun run(context: Context) {
        FanMakerSDKs.setInstance(context, KEY, API_KEY)
        val sdk = FanMakerSDKs.getInstance(context, KEY) ?: run {
            Log.e(TAG, "no probe instance"); return
        }

        Log.i(TAG, "---- what does a sign-out clear? ----")
        var pass = 0
        var fail = 0
        fun check(ok: Boolean, note: String) {
            if (ok) pass++ else fail++
            Log.i(TAG, "${if (ok) "ok  " else "FAIL"}  $note")
        }

        // Stand up a fan: identifiers plus a session token, the way a real
        // login leaves things.
        sdk.memberID = "fan-a-member"
        sdk.yinzid = "fan-a-yinz"
        sdk.updateSessionToken("fan-a-session-token")
        sdk.fanMakerUserToken["id"] = 4242

        var headers = sdk.webViewHeaders()
        check(headers["X-Member-ID"] == "fan-a-member", "logged in: identifier header present")
        check(headers.containsKey("X-FanMaker-SessionToken"), "logged in: session token header present")
        check(headers.containsKey("Authorization"), "logged in: Authorization header present")
        check(headers.containsKey("X-Fanmaker-User-Token"), "logged in: user token header present")

        // What clearIdentifiers alone leaves behind. This is the leak: the
        // fan's name is gone but their session is not.
        sdk.clearIdentifiers()
        headers = sdk.webViewHeaders()
        check(headers["X-Member-ID"] == null, "after clearIdentifiers: identifier header gone")
        check(headers.containsKey("X-FanMaker-SessionToken"),
            "after clearIdentifiers: session token DELIBERATELY still present - clearing identifiers is not a sign-out")

        // A real sign-out.
        sdk.memberID = "fan-a-member"
        sdk.updateSessionToken("fan-a-session-token")
        sdk.fanMakerUserToken["id"] = 4242
        sdk.logout()
        headers = sdk.webViewHeaders()
        check(headers["X-Member-ID"] == null, "after logout: identifier header gone")
        check(!headers.containsKey("X-FanMaker-SessionToken"), "after logout: session token header GONE")
        check(!headers.containsKey("Authorization"), "after logout: Authorization header GONE")
        check(!headers.containsKey("X-Fanmaker-User-Token"), "after logout: user token header GONE")
        check(sdk.memberID.isEmpty() && sdk.yinzid.isEmpty(), "after logout: identifier fields empty")

        // The api key is the app's, not the fan's, and must survive.
        check(headers.containsKey("X-FanMaker-Token"), "after logout: the app's api key still present")

        Log.i(TAG, "RESULT: ${if (fail == 0) "PASS" else "FAIL"}  ($pass passed, $fail failed)")
        Log.i(TAG, "next launch will report whether logout reached disk")
        Log.i(TAG, "---- end probe ----")
    }

    /** Second run: did logout actually reach disk, or only memory? */
    fun checkPersistence(context: Context) {
        FanMakerSDKs.setInstance(context, KEY, API_KEY)
        val sdk = FanMakerSDKs.getInstance(context, KEY) ?: return
        val headers = sdk.webViewHeaders()
        val clean = !headers.containsKey("X-FanMaker-SessionToken") && sdk.memberID.isEmpty()
        Log.i(TAG, if (clean) "cold start: RESULT PASS - nothing of the signed-out fan came back"
                   else       "cold start: RESULT FAIL - signed-out fan survived, memberID='${sdk.memberID}' token=${headers.containsKey("X-FanMaker-SessionToken")}")
    }
}
