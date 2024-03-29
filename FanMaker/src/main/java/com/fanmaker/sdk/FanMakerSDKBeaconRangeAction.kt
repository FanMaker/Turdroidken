package com.fanmaker.sdk

import org.altbeacon.beacon.Beacon
import org.json.JSONObject
import java.sql.Timestamp
import java.util.TimeZone

class FanMakerSDKBeaconRangeAction(
    val uuid: String,
    val major: String,
    val minor: String,
    val rssi: Int,
    val distance: Double,
    val seenAt: Long
)
{
    val proximity: String

    init {
        proximity = if (distance <= 0) {
            "unknown"
        } else if (distance < 5) {
            "immediate"
        } else if (distance < 10) {
            "near"
        } else {
            "far"
        }
    }

    constructor(beacon: Beacon) : this(
        beacon.id1.toString(),
        beacon.id2.toString(),
        beacon.id3.toString(),
        beacon.rssi,
        beacon.distance,
        System.currentTimeMillis()
    )

    constructor(data: JSONObject) : this(
        data.getString("uuid"),
        data.getString("major"),
        data.getString("minor"),
        data.getInt("rssi"),
        data.getDouble("distance"),
        data.getLong("seenAt")
    )

    fun toParams(): Map<String, Any> {
        return mapOf(
            "uuid" to uuid,
            "major" to major,
            "minor" to minor,
            "rssi" to rssi,
            "distance" to distance,
            "proximity" to proximity,
            "accuracy" to -1,
            "seen_at" to parsedSeenAt()
        )
    }

    fun parsedSeenAt(): String {
        val zone = TimeZone.getDefault()
        var offset = zone.rawOffset
        if (zone.observesDaylightTime()) {
            offset += 1000 * 60 * 60 // 1000 milliseconds * 60 seconds * 60 minutes = 1 hour
        }
        return Timestamp(seenAt - offset).toString() + "Z"
    }

    fun toJSON(): String {
        return JSONObject(mapOf(
            "uuid" to uuid,
            "major" to major,
            "minor" to minor,
            "rssi" to rssi,
            "distance" to distance,
            "proximity" to proximity,
            "accuracy" to -1,
            "seenAt" to seenAt
        )).toString()
    }

    override fun toString(): String {
        return "FanMakerSDKBeaconRangeAction:$uuid, MAJOR: $major, MINOR: $minor"
    }
}