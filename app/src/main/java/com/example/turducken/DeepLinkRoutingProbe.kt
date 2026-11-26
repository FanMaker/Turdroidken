package com.example.turducken

import android.content.Context
import android.util.Log
import com.fanmaker.sdk.FanMakerSDK
import com.fanmaker.sdk.FanMakerSDKs

/**
 * Probe for push deep-link routing.
 *
 * The SDK used to claim a URL only if its host was literally `fanmaker`, so a
 * push notification's click_action - normally a real https URL - was dropped in
 * silence. These cases cover what must now be claimed, what must still be
 * refused, and the bare-path escape hatch.
 *
 * grep logcat for [LinkProbe].
 */
object DeepLinkRoutingProbe {
    private const val TAG = "LinkProbe"
    private const val PROBE_KEY = "linkProbe"
    private const val PROBE_API_KEY = "deep-link-probe-local-only"

    private const val NUX_HOST = "kcchiefs.nux.fanmaker.com"

    fun run(context: Context) {
        FanMakerSDKs.setInstance(context, PROBE_KEY, PROBE_API_KEY)
        val sdk = FanMakerSDKs.getInstance(PROBE_KEY) ?: run {
            Log.e(TAG, "could not get probe instance"); return
        }

        Log.i(TAG, "---- push deep-link routing ----")

        sdk.updateBaseUrl("https://$NUX_HOST")
        sdk.updateAllowedDomains(listOf("https://$NUX_HOST/", "chiefs.example.com"))
        Log.i(TAG, "baseUrl=https://$NUX_HOST allowedDomains=${sdk.allowedDomains}")

        var pass = 0
        var fail = 0

        fun claim(url: String, expected: Boolean, note: String) {
            val actual = sdk.canOpenUrl(url)
            val ok = actual == expected
            if (ok) pass++ else fail++
            Log.i(TAG, "${if (ok) "ok  " else "FAIL"}  claim=$actual want=$expected  $url   ($note)")
        }

        // Legacy shape - must keep working.
        claim("schema://fanmaker/store", true, "legacy magic host")
        claim("https://fanmaker.com/store", true, "legacy fanmaker.com")

        // The push click_action shape - these were all rejected before.
        claim("https://$NUX_HOST/store", true, "first-party, base host")
        claim("https://$NUX_HOST/rewards?utm=push", true, "first-party with query")
        claim("https://chiefs.example.com/offers", true, "first-party via allowed_domains")
        claim("HTTPS://${NUX_HOST.uppercase()}/store", true, "scheme and host case")

        // Must stay refused.
        claim("https://evil.example.com/steal", false, "third-party web host")
        claim("https://notkcchiefs.nux.fanmaker.com.evil.com/x", false, "lookalike suffix")
        claim("chiefs://rewards/store", false, "host-app scheme, not ours to claim")
        claim("javascript:alert(1)", false, "not a web url")

        // openUrl reports rather than dropping in silence. handleUrl stays
        // Unit-returning for backwards compatibility and delegates to it.
        val claimed = sdk.openUrl("https://$NUX_HOST/store")
        val refused = sdk.openUrl("https://evil.example.com/steal")
        if (claimed && !refused) pass++ else fail++
        Log.i(TAG, "${if (claimed && !refused) "ok  " else "FAIL"}  openUrl true for ours ($claimed), false for theirs ($refused)")

        // Bare path escape hatch - no hostname convention at all.
        sdk.openPath("store")
        val normalised = sdk.deepLinkUrl
        if (normalised == "/store") pass++ else fail++
        Log.i(TAG, "${if (normalised == "/store") "ok  " else "FAIL"}  openPath(\"store\") -> deepLinkUrl=$normalised")

        // openPath must refuse anything carrying a destination of its own.
        // "//host/x" is protocol-relative: everything after the // parses as an
        // authority, so accepting it would let a caller choose the host.
        fun path(candidate: String, expected: Boolean, note: String) {
            val actual = sdk.openPath(candidate)
            val ok = actual == expected
            if (ok) pass++ else fail++
            Log.i(TAG, "${if (ok) "ok  " else "FAIL"}  openPath=$actual want=$expected  $candidate   ($note)")
        }
        path("/store", true, "genuine path")
        path("//evil.example.com/x", false, "protocol-relative, smuggles a host")
        path("///evil.example.com/x", false, "triple slash")
        path("https://evil.example.com", false, "full url, that is handleUrl's job")
        path("", false, "empty")

        // What the webview would actually load. Checked for both shapes,
        // because the doubled-slash bug affected the legacy shape as well.
        sdk.openPath("/rewards?utm=push")
        sdk.formatUrl { fromPath ->
            val wantPath = "https://$NUX_HOST/rewards?utm=push"
            val okPath = fromPath == wantPath
            if (okPath) pass++ else fail++
            Log.i(TAG, "${if (okPath) "ok  " else "FAIL"}  openPath -> $fromPath")

            sdk.openUrl("schema://fanmaker/store?ref=push")
            sdk.formatUrl { fromLegacy ->
                val wantLegacy = "https://$NUX_HOST/store?ref=push"
                val okLegacy = fromLegacy == wantLegacy
                if (okLegacy) pass++ else fail++
                Log.i(TAG, "${if (okLegacy) "ok  " else "FAIL"}  legacy -> $fromLegacy")

                // Leave one queued so the next cold start can prove it persisted.
                sdk.openPath("/survives-a-cold-start")
                Log.i(TAG, "queued /survives-a-cold-start for the next launch")
                Log.i(TAG, "RESULT: ${if (fail == 0) "PASS" else "FAIL"}  ($pass passed, $fail failed)")
                Log.i(TAG, "---- end probe ----")
            }
        }
    }

    /** Second run checks the queued destination survived process death. */
    fun checkPersistence(context: Context) {
        FanMakerSDKs.setInstance(context, PROBE_KEY, PROBE_API_KEY)
        val sdk = FanMakerSDKs.getInstance(PROBE_KEY) ?: return
        Log.i(TAG, "cold start: restored deepLinkUrl=${"'"}${sdk.deepLinkUrl}${"'"} allowedDomains=${sdk.allowedDomains}")
    }
}
