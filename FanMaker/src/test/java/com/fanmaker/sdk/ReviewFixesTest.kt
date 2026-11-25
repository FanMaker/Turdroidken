package com.fanmaker.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 4.1.0 review fixes that keep 4.0 hosts working. Same rules as iOS. */
class ReviewFixesTest {
    @Test
    fun onlyANewWindowTapOffFirstPartyGoesToTheBrowser() {
        assertTrue(ejectsToBrowser(opensANewWindow = true, isUserGesture = true, offFirstParty = true))
        assertFalse("a same-window store or partner link keeps the in-app session",
            ejectsToBrowser(opensANewWindow = false, isUserGesture = true, offFirstParty = true))
        assertFalse("a script popup isn't the fan's choice",
            ejectsToBrowser(opensANewWindow = true, isUserGesture = false, offFirstParty = true))
        assertFalse(ejectsToBrowser(opensANewWindow = true, isUserGesture = true, offFirstParty = false))
    }

    @Test
    fun logoutExpiresOnlyOurOwnHostsCookies() {
        assertEquals(listOf("session", "nux_lang"), cookieNames("session=abc; nux_lang=en"))
        assertEquals(emptyList<String>(), cookieNames(null))
        val expiries = expiringCookies("session", "rewards.wild.com")
        assertTrue(expiries.all { it.startsWith("session=; Max-Age=0") })
        assertFalse("never the parent domain the host app's cookies live on",
            expiries.any { it.contains("Domain=wild.com") || it.contains("Domain=.wild.com") })
    }
}
