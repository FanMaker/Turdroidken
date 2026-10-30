package com.fanmaker.sdk

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device-registration headers carry host-app names, which can be anything.
 * NUX's server.mjs JSON-parses every X- header, so the encoding has to survive
 * a real JSON parser and stay ASCII on the wire.
 */
class EnrichmentHeaderTest {
    @Test
    fun anyNameRoundTripsThroughJsonAndStaysAscii() {
        for (name in listOf("Rewards", "Montréal Canadiens", "Fans 🎟", "Say \"hi\" \\ bye", "line\nbreak")) {
            val encoded = asciiJsonString(name)
            assertTrue("not ASCII: $encoded", encoded.all { it.code in 0x20..0x7E })
            assertEquals(name, Json.decodeFromString(String.serializer(), encoded))
        }
    }
}
