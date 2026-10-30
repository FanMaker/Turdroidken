package com.fanmaker.sdk

import android.Manifest
import android.content.pm.ServiceInfo
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every gap is a crash when AltBeacon's foreground service starts. */
class ForegroundServiceSetupTest {
    private val both = listOf(Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
    private val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

    @Test
    fun aCompleteManifestNeedsNothing() {
        assertNull(missingForegroundServiceSetup(both, location))
        assertNull(missingForegroundServiceSetup(both, null))
    }

    @Test
    fun eachMissingPieceIsNamed() {
        val noFgs = missingForegroundServiceSetup(both - Manifest.permission.FOREGROUND_SERVICE, location)
        assertTrue("$noFgs", noFgs!!.contains("FOREGROUND_SERVICE\""))
        val noType = missingForegroundServiceSetup(both - Manifest.permission.FOREGROUND_SERVICE_LOCATION, location)
        assertTrue("$noType", noType!!.contains("FOREGROUND_SERVICE_LOCATION"))
        val wrongType = missingForegroundServiceSetup(both, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        assertTrue("$wrongType", wrongType!!.contains("foregroundServiceType=\"location\""))
    }
}
