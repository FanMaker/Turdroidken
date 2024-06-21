package com.fanmaker.sdk
import java.util.HashMap
import android.util.Log
import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

class FanMakerSDK(
    var version: String = "2.0.0",
    var apiKey: String = "",
    var userID: String = "",
    var memberID: String = "",
    var studentID: String = "",
    var ticketmasterID: String = "",
    var yinzid: String = "",
    var pushNotificationToken: String = "",
    var arbitraryIdentifiers: HashMap<String, String> = HashMap<String, String>(),
    var locationEnabled: Boolean = false
) : Parcelable {

    // ------------------------------------------------------------------------------------------------------
    // THESE METHODS ARE USED TO MANUALLY PARCEL AND UNPARCEL THE INSTANCE OF THIS CLASS
    // BECAUSE OTHERWISE ANYTHING THAT IS CHANGED AFTER THE INSTANCE HAS BEEN CREATED IS LOST
    // ------------------------------------------------------------------------------------------------------
    constructor(parcel: Parcel) : this(
        version = parcel.readString() ?: "",
        apiKey = parcel.readString() ?: "",
        userID = parcel.readString() ?: "",
        memberID = parcel.readString() ?: "",
        studentID = parcel.readString() ?: "",
        ticketmasterID = parcel.readString() ?: "",
        yinzid = parcel.readString() ?: "",
        pushNotificationToken = parcel.readString() ?: "",
        arbitraryIdentifiers = parcel.readHashMap(HashMap::class.java.classLoader) as HashMap<String, String>,
        locationEnabled = parcel.readBoolean() ?: false
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(version)
        parcel.writeString(apiKey)
        parcel.writeString(userID)
        parcel.writeString(memberID)
        parcel.writeString(studentID)
        parcel.writeString(ticketmasterID)
        parcel.writeString(yinzid)
        parcel.writeString(pushNotificationToken)
        parcel.writeMap(arbitraryIdentifiers)
        parcel.writeBoolean(locationEnabled)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<FanMakerSDK> {
        override fun createFromParcel(parcel: Parcel): FanMakerSDK = FanMakerSDK(parcel)
        override fun newArray(size: Int): Array<FanMakerSDK?> = arrayOfNulls(size)
    }
    // ------------------------------------------------------------------------------------------------------

    fun initialize(apiKey: String) {
        this.apiKey = apiKey
    }

    fun isInitialized(): Boolean {
        return apiKey != ""
    }

    fun isLocationTrackingEnabled(): Boolean {
        return this.locationEnabled
    }

    fun enableLocationTracking() {
        this.locationEnabled = true
    }

    fun disableLocationTracking() {
        this.locationEnabled = false
    }
}
