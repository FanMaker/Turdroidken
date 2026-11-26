package com.fanmaker.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 4.0.3 published `locationEnabled` and `deepLinkUrl` as constructor
 * arguments. Kotlin callers name them, so the names are part of the API.
 */
@RunWith(AndroidJUnit4::class)
class ConstructorCompatTest {
    @Test
    fun namedConstructorArgumentsStillSetTheirProperties() {
        val sdk = FanMakerSDK(locationEnabled = true, deepLinkUrl = "/store")

        assertTrue(sdk.locationEnabled)
        assertEquals("/store", sdk.deepLinkUrl)
    }
}
