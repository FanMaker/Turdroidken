package com.example.turducken

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import com.fanmaker.sdk.ActivityTracker

class FanMakerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register this activity so it can be finished from MainActivity callback
        ActivityTracker.register(this)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_fan_maker)

        // Set up window insets handling
        setupWindowInsets()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister this activity
        ActivityTracker.unregister(this)
    }

    private fun setupWindowInsets() {
        val rootView = findViewById<View>(R.id.fan_maker_container)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply padding for system bars to the main container
            view.updatePadding(
                top = insets.top,
                left = insets.left,
                right = insets.right,
                bottom = insets.bottom
            )

            WindowInsetsCompat.CONSUMED
        }
    }
}
