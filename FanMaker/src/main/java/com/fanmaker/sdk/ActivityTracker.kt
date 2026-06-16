package com.fanmaker.sdk

import android.app.Activity
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper class to track activities and allow finishing them from anywhere
 * Similar to NotificationCenter pattern in Swift
 */
@Deprecated(
    message = "The FanMaker SDK now closes its own FanMakerSDKWebView automatically when web " +
        "content triggers the \"close\" action, so manually finishing the activity via " +
        "ActivityTracker is no longer required. To run custom close logic, or to dismiss a " +
        "FanMakerSDKWebViewFragment host yourself, set FanMakerSDK.onClose instead. Retained " +
        "for backward compatibility; will be removed in a future release."
)
object ActivityTracker {
    private val activities = ConcurrentHashMap<Class<out Activity>, Activity>()
    
    /**
     * Register an activity so it can be finished later
     */
    fun register(activity: Activity) {
        activities[activity.javaClass] = activity
    }
    
    /**
     * Unregister an activity when it's destroyed
     */
    fun unregister(activity: Activity) {
        activities.remove(activity.javaClass)
    }
    
    /**
     * Finish an activity by its class type
     */
    fun finishActivity(activityClass: Class<out Activity>) {
        activities[activityClass]?.let { activity ->
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.finish()
                }
            }
        }
    }
}

