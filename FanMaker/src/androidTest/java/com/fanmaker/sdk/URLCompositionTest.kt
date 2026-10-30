package com.fanmaker.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On a device, not the JVM: composeUrl is built on android.net.Uri, and the
 * unit-test android.jar only has stubs of it. Same cases as the iOS SDK's
 * URLCompositionTests.
 */
@RunWith(AndroidJUnit4::class)
class URLCompositionTest {
    private val base = "https://pipboy.fanmaker.com"

    @Test
    fun aPathIsAppendedWithoutADoubledSlash() {
        assertEquals("$base/store", composeUrl(base, "/store"))
        assertEquals("$base/store", composeUrl("$base/", "store/"))
        assertEquals(base, composeUrl(base, "/"))
    }

    @Test
    fun theDeepLinksQueryIsKept() {
        assertEquals("$base/store?item=7", composeUrl(base, "/store?item=7"))
    }

    @Test
    fun anEncodedQueryValueStaysOneValue() {
        val redirect = "redirect=https%3A%2F%2Fx.com%2Fa%3Fb%3D1%26c%3D2"
        assertEquals("$base/sso?$redirect", composeUrl(base, "/sso?$redirect"))
        assertEquals("$base/search?q=hats%26gloves", composeUrl(base, "/search?q=hats%26gloves"))
    }

    @Test
    fun anEncodedPathIsNotDecoded() {
        assertEquals("$base/teams/a%2Fb", composeUrl(base, "/teams/a%2Fb"))
    }

    @Test
    fun theBaseQueryIsKeptAndTheDeepLinksGoesAfterIt() {
        assertEquals(
            "$base/store?utm_source=app&item=7",
            composeUrl("$base/?utm_source=app", "/store?item=7")
        )
        assertEquals("$base/store?utm_source=app", composeUrl("$base/?utm_source=app", "/store"))
    }

    @Test
    fun aFullUrlGivesItsPathAndQueryButNotItsHostOrFragment() {
        assertEquals(
            "$base/store?q=a%26b",
            composeUrl(base, "https://other.fanmaker.com/store?q=a%26b#top")
        )
    }
}
