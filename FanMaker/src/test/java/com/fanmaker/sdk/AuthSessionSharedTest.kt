package com.fanmaker.sdk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FanMaker/NUX#1023: a sign-in session is private unless the page asks for a
 * shared one with a JSON `true`. SeatGeek asks; Ticketmaster does not.
 */
class AuthSessionSharedTest {
    @Test
    fun onlyAJsonTrueAsksForASharedSession() {
        assertTrue(wantsSharedSession(true))
    }

    @Test
    fun everythingElseStaysPrivate() {
        for (value in listOf<Any?>(null, false, "true", 1, "shared")) {
            assertFalse("$value", wantsSharedSession(value))
        }
    }
}
