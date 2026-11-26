package com.fanmaker.sdk

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * logout() is the sign-out. Fanmaker's pages and sign-in providers keep their
 * own state in the SDK's webview - cookies, local storage - so clearing the
 * SDK's tokens alone could still open the next person's webview as the
 * previous fan. The host app's own WebViews are not ours to clear. Same cases
 * as the iOS SDK's LogoutWebDataTests; on a device because profiles are
 * WebView's.
 */
@RunWith(AndroidJUnit4::class)
class LogoutWebDataTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val url = "https://logout-test.fanmaker.com/"

    @Before
    fun profilesSupported() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
    }

    @Test
    fun theSDKsWebViewUsesItsOwnProfile() {
        instrumentation.runOnMainSync {
            val webView = WebView(instrumentation.targetContext)
            FanMakerSDK.useOwnWebProfile(webView)
            assertEquals(FanMakerSDK.WEB_PROFILE, WebViewCompat.getProfile(webView).name)
            webView.destroy()
        }
    }

    @Test
    fun logoutClearsTheSDKsWebDataAndLeavesTheHostAppsAlone() {
        lateinit var sdkCookies: CookieManager
        instrumentation.runOnMainSync {
            sdkCookies = FanMakerSDK.ownWebProfile()!!.cookieManager
            sdkCookies.setCookie(url, "fm_previous_fan=1")
            CookieManager.getInstance().setCookie(url, "host_app_session=1")
        }
        assertTrue(sdkCookies.getCookie(url).orEmpty().contains("fm_previous_fan"))

        val loggedOut = CountDownLatch(1)
        FanMakerSDK().logout { loggedOut.countDown() }

        assertTrue("logout() never finished", loggedOut.await(10, TimeUnit.SECONDS))
        assertFalse("logout() must clear the SDK's web data",
            sdkCookies.getCookie(url).orEmpty().contains("fm_previous_fan"))
        assertTrue("logout() must leave the host app's own web data alone",
            CookieManager.getInstance().getCookie(url).orEmpty().contains("host_app_session"))
    }
}
