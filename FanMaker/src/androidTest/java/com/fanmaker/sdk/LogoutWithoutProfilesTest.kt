package com.fanmaker.sdk

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * logout() on a WebView too old for profiles, where every WebView in the app
 * shares one cookie jar. A program's site often sits on the client's own domain
 * (rewards.<client>.com), so clearing the whole jar signed the fan out of the
 * host app's own WebViews (4.1.0 review). Only our hosts' cookies may go.
 * The profile path is LogoutWebDataTest; this one runs where that one skips.
 */
@RunWith(AndroidJUnit4::class)
class LogoutWithoutProfilesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ours = "https://rewards.client-test.com"
    private val hostApp = "https://www.client-test.com"

    @Before
    fun noProfiles() {
        assumeFalse(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
    }

    @Test
    fun logoutClearsOnlyOurHostsCookies() {
        val cookies = CookieManager.getInstance()
        instrumentation.runOnMainSync {
            cookies.setCookie(ours, "fm_previous_fan=1; Path=/")
            cookies.setCookie(hostApp, "host_app_session=1; Domain=client-test.com; Path=/")
            cookies.setCookie("https://news.example.org", "unrelated_site=1; Path=/")
            cookies.flush()
        }
        assertTrue(cookies.getCookie(ours).orEmpty().contains("fm_previous_fan"))

        val sdk = FanMakerSDK()
        sdk.updateBaseUrl(ours)
        val loggedOut = CountDownLatch(1)
        sdk.logout { loggedOut.countDown() }

        assertTrue("logout() never finished", loggedOut.await(10, TimeUnit.SECONDS))
        assertFalse("logout() must clear our host's cookies",
            cookies.getCookie(ours).orEmpty().contains("fm_previous_fan"))
        assertTrue("logout() must leave the host app's parent-domain cookie alone",
            cookies.getCookie(hostApp).orEmpty().contains("host_app_session"))
        assertTrue("logout() must leave other sites alone",
            cookies.getCookie("https://news.example.org").orEmpty().contains("unrelated_site"))
    }
}
