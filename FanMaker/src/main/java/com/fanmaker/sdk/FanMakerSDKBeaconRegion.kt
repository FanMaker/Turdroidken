package com.fanmaker.sdk

import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.Region
import org.json.JSONObject

class FanMakerSDKBeaconRegion(data: JSONObject) {
    val id = data.getInt("id")
    val name: String? = data.getString("name")
    val uuid = data.getString("uuid")
    val major: String?  = data.getString("major")
    val minor: String? = data.getString("minor")
    val active: Boolean? = data.getBoolean("active")
    val region : Region

    init {
        region = if (major.toString() == "") {
            Region("fanmaker-region-$id", Identifier.parse(uuid), null, null)
        } else {
            Region("fanmaker-region-$id", Identifier.parse(uuid), Identifier.parse(major), null)
        }
    }

    override fun toString(): String {
        return if (major.toString() == "") {
            "FanMakerSDKBeaconRegion:$uuid"
        } else {
            "FanMakerSDKBeaconRegion:$uuid, MAJOR: $major"
        }
    }
}