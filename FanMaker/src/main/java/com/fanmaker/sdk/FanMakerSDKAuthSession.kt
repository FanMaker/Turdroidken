package com.fanmaker.sdk

import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.activity.result.ActivityResultCaller
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Runs sign-ins that can't work inside a webview in an Auth Tab instead:
 * passkeys are bound to the provider's own domain, and some identity
 * providers refuse embedded webviews outright. The Auth Tab shares the
 * browser's sign-ins and passkeys and hands back the link the sign-in ends
 * on, which loads into the webview like any deep link. FanMaker/NUX#907.
 *
 * A page asks with
 * `window.fanmakerAuthSession.postMessage(JSON.stringify({ url, callbackScheme }))`.
 * That object is a WebMessageListener rather than part of the `fanmaker`
 * JavascriptInterface because Android shows a JavascriptInterface to every
 * frame, third-party iframes included, without saying which one called;
 * this channel names the sending page and whether it is the main frame.
 *
 * Construct it while the activity or fragment is being created: it registers
 * an activity-result launcher, which has to happen before it starts.
 */
internal class FanMakerSDKAuthSession(
    caller: ActivityResultCaller,
    private val context: () -> Context?,
    private val sdk: () -> FanMakerSDK?,
    private val webView: () -> WebView?,
    private val loadDestination: (FanMakerSDK) -> Unit
) {
    private val launcher = AuthTabIntent.registerActivityResultLauncher(caller) { onResult(it) }

    /** Listens for the page's requests on [view]. Call before its first load. */
    fun attach(view: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            Log.w(TAG, "this WebView cannot say which page sent a message, so sign-ins stay in the webview")
            return
        }
        WebViewCompat.addWebMessageListener(view, JS_OBJECT, setOf("*")) { _, message, sourceOrigin, isMainFrame, _ ->
            start(message.data, sourceOrigin.host, isMainFrame)
        }
    }

    private fun start(data: String?, originHost: String?, isMainFrame: Boolean) {
        val sdk = sdk()
        val context = context()
        if (sdk == null || context == null) {
            Log.e(TAG, "ignored an authSession request: this screen has no SDK instance or context")
            report("failed")
            return
        }
        val request = if (isMainFrame) sdk.authSessionRequest(data, originHost) else null
        if (request == null) {
            Log.e(TAG, "refused an authSession request from $originHost (main frame: $isMainFrame): $data")
            report("refused")
            return
        }

        val browser = CustomTabsClient.getPackageName(context, null)
        if (browser != null && CustomTabsClient.isAuthTabSupported(context, browser)) {
            Log.i(TAG, "opening ${request.url} in an Auth Tab")
            AuthTabIntent.Builder().build().launch(launcher, request.url, request.callbackScheme)
        } else {
            // No Auth Tab in this browser: a Custom Tab, and the sign-in comes
            // back through the host app's own deep link for the scheme.
            Log.i(TAG, "opening ${request.url} in a Custom Tab, browser $browser has no Auth Tab")
            CustomTabsIntent.Builder().build().launchUrl(context, request.url)
        }
    }

    private fun onResult(result: AuthTabIntent.AuthResult) {
        val sdk = sdk() ?: return
        val returned = result.resultUri
        if (result.resultCode == AuthTabIntent.RESULT_OK && returned != null && sdk.openUrl(returned.toString())) {
            Log.i(TAG, "sign-in returned ${withoutSignInKey(returned.toString())}")
            loadDestination(sdk)
            return
        }
        Log.i(TAG, "authSession ended without a sign-in: result ${result.resultCode} ${returned?.let { withoutSignInKey(it.toString()) } ?: ""}")
        report(if (result.resultCode == AuthTabIntent.RESULT_CANCELED) "cancelled" else "failed")
    }

    /** Tells the page a sign-in it asked for ended without one, so it can reset its button. */
    private fun report(status: String) {
        webView()?.evaluateJavascript(
            "window.FanMakerReceiveAuthSession && window.FanMakerReceiveAuthSession(\"$status\")", null)
    }

    companion object {
        private const val TAG = "FanMakerSDKAuthSession"
        const val JS_OBJECT = "fanmakerAuthSession"
    }
}

/**
 * [url] with an sso_login key masked, for logs. The key signs whoever holds it
 * in, and logcat ends up in bug reports.
 */
internal fun withoutSignInKey(url: String): String = url.replace(Regex("/sso_login/[^/?#]+"), "/sso_login/…")
