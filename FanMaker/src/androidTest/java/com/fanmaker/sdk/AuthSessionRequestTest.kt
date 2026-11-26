package com.fanmaker.sdk

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Which pages may start a sign-in in an Auth Tab, and for where. The result is
 * a sign-in link the webview follows, so a request accepted from the wrong
 * page could sign a fan in as someone else. Same cases as the iOS SDK's
 * AuthSessionRequestTests; on a device because the rule is built on
 * android.net.Uri and org.json, which the JVM only has stubs of.
 */
@RunWith(AndroidJUnit4::class)
class AuthSessionRequestTest {
    private val tmStart = "https://tmauth.fanmaker.com/tmauth/start?state=oilersfanmakermobile.android"

    private fun request(json: String?, from: String?): FanMakerSDK.AuthSessionRequest? {
        val sdk = FanMakerSDK(baseUrl = "https://www.loiltyrewards.com")
        sdk.updateAllowedDomains(listOf("www.loiltyrewards.com"))
        return sdk.authSessionRequest(json, from)
    }

    private fun body(url: String, scheme: String) = """{"url":"$url","callbackScheme":"$scheme"}"""

    @Test
    fun ourPageCanStartATicketmasterSignIn() {
        assertEquals(
            FanMakerSDK.AuthSessionRequest(Uri.parse(tmStart), "oilers"),
            request(body(tmStart, "oilers"), "www.loiltyrewards.com")
        )
        // NUX on a fanmaker.com host counts too.
        assertNotNull(request(body(tmStart, "oilers"), "oilers.nux.fanmaker.com"))
    }

    @Test
    fun aPageWeDontOwnCannotStartOne() {
        assertNull(request(body(tmStart, "oilers"), "auth.ticketmaster.com"))
        assertNull(request(body(tmStart, "oilers"), null))
    }

    @Test
    fun theSignInHasToStartOnOneOfOurHostsOverHttps() {
        assertNull(request(body("https://evil.example.com/start", "oilers"), "www.loiltyrewards.com"))
        assertNull(request(body("http://tmauth.fanmaker.com/tmauth/start", "oilers"), "www.loiltyrewards.com"))
    }

    @Test
    fun theCallbackHasToBeAnAppScheme() {
        for (scheme in listOf("https", "javascript", "intent", "not a scheme", "", "1oilers")) {
            assertNull(scheme, request(body(tmStart, scheme), "www.loiltyrewards.com"))
        }
        assertEquals("oilers", request(body(tmStart, "Oilers"), "www.loiltyrewards.com")?.callbackScheme)
    }

    @Test
    fun aMalformedMessageIsRefused() {
        assertNull(request("not json", "www.loiltyrewards.com"))
        assertNull(request("""{"url":"$tmStart"}""", "www.loiltyrewards.com"))
        assertNull(request(null, "www.loiltyrewards.com"))
    }
}
