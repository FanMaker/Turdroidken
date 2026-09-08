package com.example.turducken

import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.fanmaker.sdk.FanMakerSDK
import com.fanmaker.sdk.FanMakerSDKBeaconManager
import com.fanmaker.sdk.FanMakerSDKWebView
import com.fanmaker.sdk.FanMakerSDKs

/**
 * Source-compatibility check for a client that updates the SDK without
 * touching their own code.
 *
 * Every call below is a shape the 4.0.3 README documents, written the way the
 * README writes it. If this file stops compiling, a drop-in SDK update is a
 * breaking change for existing integrations and the release needs a major
 * version and migration notes.
 *
 * This is a compile-time assertion first and a smoke test second - it is
 * deliberately never called from a real code path.
 */
@Suppress("UNUSED_VARIABLE", "unused", "DEPRECATION")
object LegacyApiCompatCheck {
    private const val TAG = "CompatCheck"

    fun legacyIntegration(activity: AppCompatActivity) {
        // README: initialization via the registry, never the constructor.
        FanMakerSDKs.setInstance(activity, "<DEV_DEFINED_KEY>", "<SDK_KEY>")
        val fanMakerSDK: FanMakerSDK? = FanMakerSDKs.getInstance("<DEV_DEFINED_KEY>")

        // README: launching the webview.
        val fanmakerIntent = Intent(activity, FanMakerSDKWebView::class.java)
            .apply { putExtra("fanMakerKey", "<DEV_DEFINED_KEY>") }

        // README: auto checkin, by direct property assignment.
        fanMakerSDK!!.locationEnabled = true
        activity.lifecycle.addObserver(fanMakerSDK)

        // The function form, which the README does not use but the API exposes.
        fanMakerSDK.enableLocationTracking()
        val tracking: Boolean = fanMakerSDK.isLocationTrackingEnabled()
        fanMakerSDK.disableLocationTracking()

        // README: identifiers, written and read.
        fanMakerSDK.userID = "userID"
        fanMakerSDK.memberID = "memberID"
        fanMakerSDK.studentID = "studentID"
        fanMakerSDK.ticketmasterID = "ticketmasterID"
        fanMakerSDK.yinzid = "yinzid"
        fanMakerSDK.pushNotificationToken = "pushToken"
        fanMakerSDK.arbitraryIdentifiers["some_key"] = "some_value"
        val readBack: String = fanMakerSDK.memberID

        // README: deep linking. handleUrl is used as a statement, and its
        // result is discarded - so it must stay Unit-returning, or anything
        // compiled against 4.0.3 breaks at runtime with NoSuchMethodError.
        val handled: Unit = fanMakerSDK.handleUrl("schema://fanmaker/store")
        if (fanMakerSDK.canOpenUrl("schema://fanmaker/store")) {
            fanMakerSDK.handleUrl("schema://fanmaker/store")
        }

        // README: url formatting, parameters, close handling.
        fanMakerSDK.formatUrl { url -> Log.d(TAG, url) }
        fanMakerSDK.fanMakerParameters["key"] = "value"
        fanMakerSDK.onClose = { params -> Log.d(TAG, "closed $params") }
        val headers: HashMap<String, String> = fanMakerSDK.webViewHeaders()
        val ready: Boolean = fanMakerSDK.isInitialized()

        // README: beacons.
        val beaconManager = FanMakerSDKBeaconManager(fanMakerSDK, activity.application)

        Log.i(TAG, "legacy 4.0.3 call shapes still compile")
    }

    /** Additions from this branch, for contrast - not part of the check. */
    fun newApi(activity: AppCompatActivity) {
        val sdk = FanMakerSDKs.getInstance("<DEV_DEFINED_KEY>") ?: return
        val claimed: Boolean = sdk.openUrl("https://example.com/store")
        val queued: Boolean = sdk.openPath("/store")
        val external: Boolean = sdk.isExternalWebUrl("https://example.com")
        sdk.updateAllowedDomains(listOf("example.com"))
        val domains: List<String> = sdk.allowedDomains
        sdk.clearIdentifiers()
    }
}
