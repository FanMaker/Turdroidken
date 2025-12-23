package com.fanmaker.sdk

import android.app.Activity
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper class to track activities and allow finishing them from anywhere
 * Similar to NotificationCenter pattern in Swift
 */
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

