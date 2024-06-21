package com.example.turducken

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import com.fanmaker.sdk.FanMakerSDK
import com.fanmaker.sdk.FanMakerSDKBeaconManager
import com.fanmaker.sdk.FanMakerSDKBeaconRegion
import com.fanmaker.sdk.FanMakerSDKWebView
import org.altbeacon.beacon.BeaconManager

class MainActivity : AppCompatActivity() {
    private val fanMakerSDK1 = FanMakerSDK()
    private val fanMakerSDK2 = FanMakerSDK()
    lateinit var beaconManager1: FanMakerSDKBeaconManager
    lateinit var beaconManager2: FanMakerSDKBeaconManager

    var neverAskAgainPermissions = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fanMakerSDK1.initialize("")
        fanMakerSDK2.initialize("")
        checkPermissions()

        beaconManager1 = FanMakerSDKBeaconManager(fanMakerSDK1, application)
        beaconManager1.fetchBeaconRegions()

        beaconManager2 = FanMakerSDKBeaconManager(fanMakerSDK2, application)
        beaconManager2.fetchBeaconRegions()
    }

    fun setupIdentifiers() {
        val memberID: String = findViewById<EditText>(R.id.memberID).text.toString()
        if (memberID != "") fanMakerSDK1.memberID = memberID

        val studentID: String = findViewById<EditText>(R.id.studentID).text.toString()
        if (studentID != "") fanMakerSDK1.studentID = studentID

        val ticketmasterID: String = findViewById<EditText>(R.id.ticketmasterID).text.toString()
        if (ticketmasterID != "") fanMakerSDK1.ticketmasterID = ticketmasterID

        val yinzID: String = findViewById<EditText>(R.id.yinzID).text.toString()
        if (yinzID != "") fanMakerSDK1.yinzid = yinzID

        val pushToken: String = findViewById<EditText>(R.id.pushToken).text.toString()
        if (pushToken != "") fanMakerSDK1.pushNotificationToken = pushToken
    }

    fun openFanMakerSDKWebView(view: View) {
        val intent1 = Intent(this, FanMakerSDKWebView::class.java).apply {
            putExtra("fanMakerSDK", fanMakerSDK1)
        }
        startActivity(intent1)
    }

    fun openFanMakerSDKWebViewFragment(view: View) {
        setupIdentifiers()

        val intent2 = Intent(this, FanMakerActivity::class.java)
        intent2.putExtra("fanMakerSDK", fanMakerSDK2)
        startActivity(intent2)
    }

    fun checkPermissions() {
        // basepermissions are for M and higher
        var permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION)
        var permissionRationale ="This app needs fine location permission to detect beacons.  Please grant this now."
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN)
            permissionRationale ="This app needs fine location permission, and bluetooth scan permission to detect beacons.  Please grant all of these now."
        }
        else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if ((checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
                permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION)
                permissionRationale ="This app needs fine location permission to detect beacons.  Please grant this now."
            }
            else {
                permissions = arrayOf( Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                permissionRationale ="This app needs background location permission to detect beacons in the background.  Please grant this now."
            }
        }
        else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            permissionRationale ="This app needs both fine location permission and background location permission to detect beacons in the background.  Please grant both now."
        }
        var allGranted = true
        for (permission in permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) allGranted = false;
        }
        if (!allGranted) {
            if (neverAskAgainPermissions.count() == 0) {
                val builder =
                    AlertDialog.Builder(this)
                builder.setTitle("This app needs permissions to detect beacons")
                builder.setMessage(permissionRationale)
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener {
                    requestPermissions(
                        permissions,
                        PERMISSION_REQUEST_FINE_LOCATION
                    )
                }
                builder.show()
            }
            else {
                val builder =
                    AlertDialog.Builder(this)
                builder.setTitle("Functionality limited")
                builder.setMessage("Since location and device permissions have not been granted, this app will not be able to discover beacons.  Please go to Settings -> Applications -> Permissions and grant location and device discovery permissions to this app.")
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener { }
                builder.show()
            }
        }
        else {
            if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                        val builder =
                            AlertDialog.Builder(this)
                        builder.setTitle("This app needs background location access")
                        builder.setMessage("Please grant location access so this app can detect beacons in the background.")
                        builder.setPositiveButton(android.R.string.ok, null)
                        builder.setOnDismissListener {
                            requestPermissions(
                                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                PERMISSION_REQUEST_BACKGROUND_LOCATION
                            )
                        }
                        builder.show()
                    } else {
                        val builder =
                            AlertDialog.Builder(this)
                        builder.setTitle("Functionality limited")
                        builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons in the background.  Please go to Settings -> Applications -> Permissions and grant background location access to this app.")
                        builder.setPositiveButton(android.R.string.ok, null)
                        builder.setOnDismissListener { }
                        builder.show()
                    }
                }
            }
            else if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.S &&
                (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED)) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN)) {
                    val builder =
                        AlertDialog.Builder(this)
                    builder.setTitle("This app needs bluetooth scan permission")
                    builder.setMessage("Please grant scan permission so this app can detect beacons.")
                    builder.setPositiveButton(android.R.string.ok, null)
                    builder.setOnDismissListener {
                        requestPermissions(
                            arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                            PERMISSION_REQUEST_BLUETOOTH_SCAN
                        )
                    }
                    builder.show()
                } else {
                    val builder =
                        AlertDialog.Builder(this)
                    builder.setTitle("Functionality limited")
                    builder.setMessage("Since bluetooth scan permission has not been granted, this app will not be able to discover beacons  Please go to Settings -> Applications -> Permissions and grant bluetooth scan permission to this app.")
                    builder.setPositiveButton(android.R.string.ok, null)
                    builder.setOnDismissListener { }
                    builder.show()
                }
            }
            else {
                if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                            val builder =
                                AlertDialog.Builder(this)
                            builder.setTitle("This app needs background location access")
                            builder.setMessage("Please grant location access so this app can detect beacons in the background.")
                            builder.setPositiveButton(android.R.string.ok, null)
                            builder.setOnDismissListener {
                                requestPermissions(
                                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                    PERMISSION_REQUEST_BACKGROUND_LOCATION
                                )
                            }
                            builder.show()
                        } else {
                            val builder =
                                AlertDialog.Builder(this)
                            builder.setTitle("Functionality limited")
                            builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons in the background.  Please go to Settings -> Applications -> Permissions and grant background location access to this app.")
                            builder.setPositiveButton(android.R.string.ok, null)
                            builder.setOnDismissListener { }
                            builder.show()
                        }
                    }
                }
            }
        }

    }

    companion object {
        val TAG = "MainActivity"
        val PERMISSION_REQUEST_BACKGROUND_LOCATION = 0
        val PERMISSION_REQUEST_BLUETOOTH_SCAN = 1
        val PERMISSION_REQUEST_BLUETOOTH_CONNECT = 2
        val PERMISSION_REQUEST_FINE_LOCATION = 3
    }
}
