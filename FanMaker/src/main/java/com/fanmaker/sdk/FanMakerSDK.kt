package com.fanmaker.sdk

class FanMakerSDK {
    companion object {
        var apiKey: String = ""
        var userID: String = ""
        var memberID: String = ""
        var studentID: String = ""
        var ticketmasterID: String = ""
        var yinzid: String = ""
        var pushNotificationToken: String = ""
        private var locationEnabled: Boolean = false

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
}