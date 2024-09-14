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
import com.fanmaker.sdk.FanMakerSDKs
import com.fanmaker.sdk.FanMakerSDKBeaconManager
import com.fanmaker.sdk.FanMakerSDKBeaconRegion
import com.fanmaker.sdk.FanMakerSDKWebView
import org.altbeacon.beacon.BeaconManager

// Used for Deep / Universal Linking
import android.net.Uri

class MainActivity : AppCompatActivity() {
    // Declare FanMakerSDK instances you want to use
    var fanMakerSDK1: FanMakerSDK? = null
    var fanMakerSDK2: FanMakerSDK? = null

    // Declare intents for the FanMakerSDKWebView and FanMakerActivity
    lateinit var fanmakerIntent1: Intent
    lateinit var fanmakerIntent2: Intent

    // Declare BeaconManagers for the FanMakerSDK instances
    lateinit var beaconManager1: FanMakerSDKBeaconManager
    lateinit var beaconManager2: FanMakerSDKBeaconManager

    var neverAskAgainPermissions = ArrayList<String>()

    fun fanMakerStart(intent: Intent?) {
        // FanMakerSDKs is a singleton-like registry that can hold any number of FanMakerSDK instances
        // that you can access by their unique key to insure availability across your app.

        // The first parameter is the context, the second is the key you will use to access the instance, and the third is the API key for the instance.
         FanMakerSDKs.setInstance(this, "devDefinedKey1", "<SDK_KEY_1>")
         FanMakerSDKs.setInstance(this, "devDefinedKey2", "<SDK_KEY_2>")

        // Get the FanMakerSDK instances and assign them to a variable if you so desire for ease of use
        fanMakerSDK1 = FanMakerSDKs.getInstance("devDefinedKey1")
        fanMakerSDK2 = FanMakerSDKs.getInstance("devDefinedKey2")

        // Create intents for the FanMakerSDKWebView and FanMakerActivity with the appropriate key.
        // This can be done in the onClick method of a button or wherever you want to start the SDK as well.
        // It is very important that you pass the key to the intent using putExtra so the SDK knows which instance to use.
        fanmakerIntent1 = Intent(this, FanMakerSDKWebView::class.java).apply { putExtra("fanMakerKey", "devDefinedKey1") }
        fanmakerIntent2 = Intent(this, FanMakerActivity::class.java).apply { putExtra("fanMakerKey", "devDefinedKey2") }

        checkPermissions()
        if (fanMakerSDK1 != null) {
            // Enable location services for the SDK
            fanMakerSDK1!!.locationEnabled = true
            // Set Dark Loading Screen
            fanMakerSDK1!!.useDarkLoadingScreen = true
            // Lifecycle is needed for the SDK to handle Auto Checkin and to Reward Usage of Host App
            lifecycle.addObserver(fanMakerSDK1!!)
            // Initialize beacon monitoring
            beaconManager1 = FanMakerSDKBeaconManager(fanMakerSDK1!!, application)
            beaconManager1.fetchBeaconRegions()
        }
        if (fanMakerSDK2 != null) {
            // Enable location services for the SDK
            fanMakerSDK2!!.locationEnabled = true
            // Lifecycle is needed for the SDK to handle Auto Checkin and to Reward Usage of Host App
            lifecycle.addObserver(fanMakerSDK2!!)
            // Initialize beacon monitoring
            beaconManager2 = FanMakerSDKBeaconManager(fanMakerSDK2!!, application)
            beaconManager2.fetchBeaconRegions()
        }

        // Check if this intent is started via a deep link
        if (intent?.action == Intent.ACTION_VIEW) { handleDeepLink(intent) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initializes the FanMakerSDKs, but you can initialize them wherever you want to start the SDK.
        fanMakerStart(intent)
    }

    // Assuming your application uses the android:launchMode="singleTask" attribute in the AndroidManifest.xml file
    // this method will be called when the app is already running and a new intent is received. Which is useful for deep linking.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Check if this intent is started via a deep link and handle it
        handleDeepLink(intent)
    }

    fun setupIdentifiers() {
        val memberID: String = findViewById<EditText>(R.id.memberID).text.toString()
        if (memberID != "") fanMakerSDK1?.memberID = memberID

        val studentID: String = findViewById<EditText>(R.id.studentID).text.toString()
        if (studentID != "") fanMakerSDK1?.studentID = studentID

        val ticketmasterID: String = findViewById<EditText>(R.id.ticketmasterID).text.toString()
        if (ticketmasterID != "") fanMakerSDK1?.ticketmasterID = ticketmasterID

        val yinzID: String = findViewById<EditText>(R.id.yinzID).text.toString()
        if (yinzID != "") fanMakerSDK1?.yinzid = yinzID

        val pushToken: String = findViewById<EditText>(R.id.pushToken).text.toString()
        if (pushToken != "") fanMakerSDK1?.pushNotificationToken = pushToken

        fanMakerSDK1!!.arbitraryIdentifiers["nfl_oidc"] = "FanMaker_NFL_OIDC_Example"
        // fanMakerSDK1!!.fanMakerParameters["hide_menu"] = true
    }

    fun openFanMakerSDKWebView(view: View) {
        setupIdentifiers()
        // An example to go to a specific page in the FanMaker SDK
        fanMakerSDK1?.handleUrl("schema://FanMaker/store")
        startActivity(fanmakerIntent1)
    }

    fun openFanMakerSDKWebViewFragment(view: View) {
        setupIdentifiers()

        startActivity(fanmakerIntent2)
    }

    // This method is used to handle deep link requests
    private fun handleDeepLink(intent: Intent?) {
        val action: String? = intent?.action
        if(Intent.ACTION_VIEW == action) {
            val data: Uri? = intent?.data

            // We verify that we have a valid deep link
            if (data != null) {
                // We check the scheme of the deep link to determine which FanMakerSDK instance to use
                // or if you want to handle the deep link within your own application instead.
                // Establish a scheme for each FanMakerSDK instance you want to handle deep links for
                // in your AndroidManifest.xml file.
                val scheme = data.scheme
                if (scheme == "turducken") {
                    fanMakerSDK1?.let {
                        fanMakerSDK1!!.handleUrl(data.toString())
                        startActivity(fanmakerIntent1)
                    }
                } else if(scheme == "turducken2") {
                    fanMakerSDK2?.let {
                        fanMakerSDK2!!.handleUrl(data.toString())
                        startActivity(fanmakerIntent2)
                    }
                }
            }
        }
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
