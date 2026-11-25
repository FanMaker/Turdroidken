package com.fanmaker.sdk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4.1.0 review: restoring stored identifiers on every launch signed the
 * previous fan back in on hosts built against 4.0.3, which have no logout().
 */
class IdentifierRestoreTest {
    @Test
    fun restoresOnlyAlongsideASessionToken() {
        assertTrue(shouldRestoreFanIdentifiers("token-a", listOf("", "", "")))
        assertFalse("no session: the last fan's IDs must not go to auto_login",
            shouldRestoreFanIdentifiers("", listOf("", "", "")))
    }

    @Test
    fun aHostSuppliedIdentifierWinsOverTheStoredSet() {
        assertFalse(shouldRestoreFanIdentifiers("token-a", listOf("", "fan-b-member", "")))
    }
}
