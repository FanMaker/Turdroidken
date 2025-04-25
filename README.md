# FanMaker SDK for Android App Development

## :warning: BREAKING CHANGES IN 2.0 :warning:

Version 2.0 of the FanMakerSDK has changed from static to instanced based initializtion. This means that you will need to modify your implementation to avoid service interruptions in this version. Previous versions of the SDK are no longer available for instalation. Support for SDK versions 1.x will be depreciated on December 20th, 2024, afterwords non version 2.0 + will cease to function.

The benefits of SDK 2.0 are [described in detail on our blog](https://blog.fanmaker.com/sdk-2-0-background-check-ins-app-rewards-and-support-for-multiple-programs/).

## Upgrading to 2.0 from 1.x

### Step 1:
Previously the FanMaker SDK was initialized like this:
```
FanMakerSDK.initialize("<SDK_KEY>")
```

Now, you need to leverage the `FanMakerSDKs` singleton class to initialize and store your FanMakerSDK instances:
```
# Import the new FanMakerSDKs singleton class into your MainActivity so that it can be leveraged throughout the application
import com.fanmaker.sdk.FanMakerSDKs

class MainActivity : AppCompatActivity() {
    ...
    override fun onCreate(savedInstanceState: Bundle?) {
        ...
        FanMakerSDKs.setInstance(this, "<DEV_DEFINED_KEY>", "<SDK_KEY>")
        ...
```

The `setInstance` function of the `FanMakerSDKs` singleton class takes 3 arguments:
1. the Context of the application
2. the `DEV_DEFINED_KEY` that will be set and used to retreive this instance
3. the `SDK_KEY` provided to you by the FanMaker team as you were using previously


### Step 2:
When you are preparing your Intent for display, you will need to pass the `fanMakerKey` to it as an extra. This `fanMakerKey` is the 2nd argument that was chosen during [step 1](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#step-1)

```
class MainActivity : AppCompatActivity() {
    ...
    lateinit var fanmakerIntent1: Intent


    override fun onCreate(savedInstanceState: Bundle?) {
        ...
        fanmakerIntent1 = Intent(this, FanMakerSDKWebView::class.java).apply { putExtra("fanMakerKey", "<DEV_DEFINED_KEY>") }
        ...
    }

    fun openFanMakerSDKWebView(view: View) {
        startActivity(fanmakerIntent1)
    }
```

In the example above, `fanMakerIntent1` is established as a `lateinit var` for the ease of use across the MainActivity, which can be helpful for implementing [Deep Linking](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#deep-linking--universal-links), however, you may also simply setup the intent at the time you are planning on using it.

### Step 3.
If you are passing any values to the FanMakerSDK using one of our methods like setMemberID, setTicketmasterID, or setFanMakerIdentifiers, then be sure to specify which SDK instance you are passing the values to:

```
class MainActivity : AppCompatActivity() {
    ...
    var fanMakerSDK1: FanMakerSDK? = null
    var fanMakerSDK2: FanMakerSDK? = null
    ...

    override fun onCreate(savedInstanceState: Bundle?) {
        ...
        FanMakerSDKs.setInstance(this, "<DEV_DEFINED_KEY>", "<SDK_KEY_1>")
        FanMakerSDKs.setInstance(this, "<DEV_DEFINED_KEY_2>", "<SDK_KEY_2>")

        // Get the FanMakerSDK instances and assign them to a variable if you so desire for ease of use
        fanMakerSDK1 = FanMakerSDKs.getInstance("<DEV_DEFINED_KEY>")
        fanMakerSDK2 = FanMakerSDKs.getInstance("<DEV_DEFINED_KEY_2>")
        ...
    }

    fun setupIdentifiers() {
        fanMakerSDK1?.memberID = "memberID"
        fanMakerSDK2?.ticketmasterID = ticketmasterID
    }

    ...
    fun openFanMakerSDKWebView(view: View) {
        setupIdentifiers()
        startActivity(fanmakerIntent1)
    }

    fun openFanMakerSDKWebViewFragment(view: View) {
        setupIdentifiers()
        startActivity(fanmakerIntent2)
    }
```

In the example above, `fanMakerSDK1` and `fanMakerSDK2` are established as optional variables and then defined in the onCreate function using the `FanMakerSDKs.getInstance("<DEV_DEFINED_KEY>")` where, `DEV_DEFINED_KEY` is the second argument from [step 1](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#step-1).

### Step 4:
If you are using bluetooth beacons through the FanMaker SDK, you will need to update your implementation.

Where you are initializing the FanMakerSDKBeaconsManager, you will now need to pass the instance of the SDK you are using:

```
class MainActivity : AppCompatActivity() {
    ...
    var fanMakerSDK1: FanMakerSDK? = null
    var fanMakerSDK2: FanMakerSDK? = null

    lateinit var beaconManager1: FanMakerSDKBeaconManager
    lateinit var beaconManager2: FanMakerSDKBeaconManager
    ...

    override fun onCreate(savedInstanceState: Bundle?) {
        ...
        FanMakerSDKs.setInstance(this, "<DEV_DEFINED_KEY>", "<SDK_KEY_1>")
        FanMakerSDKs.setInstance(this, "<DEV_DEFINED_KEY_2>", "<SDK_KEY_2>")

        // Get the FanMakerSDK instances and assign them to a variable if you so desire for ease of use
        fanMakerSDK1 = FanMakerSDKs.getInstance("<DEV_DEFINED_KEY>")
        fanMakerSDK2 = FanMakerSDKs.getInstance("<DEV_DEFINED_KEY_2>")

        // -- Do your check for location permissions here --
        if (fanMakerSDK1 != null) {
            // Enable location services for the SDK
            fanMakerSDK1!!.locationEnabled = true

            // Initialize beacon monitoring
            beaconManager1 = FanMakerSDKBeaconManager(fanMakerSDK1!!, application)
            beaconManager1.fetchBeaconRegions()
        }
        if (fanMakerSDK2 != null) {
            // Enable location services for the SDK
            fanMakerSDK2!!.locationEnabled = true

            // Initialize beacon monitoring
            beaconManager2 = FanMakerSDKBeaconManager(fanMakerSDK2!!, application)
            beaconManager2.fetchBeaconRegions()
        }
    }

    beaconManager1 = FanMakerSDKBeaconManager(fanMakerSDK1!!, application)
    beaconManager1.fetchBeaconRegions()

    beaconManager2 = FanMakerSDKBeaconManager(fanMakerSDK2!!, application)
    beaconManager2.fetchBeaconRegions()
```

In the example above, `beaconManager1` and `beaconManager2` are established as `lateinit var` and then are defined in the `onCreate` method of your `MainActivity` using the `FanMakerSDKBeaconManager` class, which now requires a `FanMakerSDK` as the first argument.



## About

The FanMaker SDK provides Android developers with a way of inserting the FanMaker UI in another app. The view can be displayed as any other Android Activity.

## Usage

> Currently the GitHub Package Registry requires us to Authenticate to download an Android Library (Public or Private) hosted on the GitHub Package Registry. This might change for future releases

### Step 1 : Generate a Personal Access Token for GitHub
- Inside you GitHub account:
	- Settings -> Developer Settings -> Personal Access Tokens -> Generate new token
	- Make sure you select the following scopes ("read:packages") and Generate a token
	- After Generating make sure to copy your new personal access token. You won’t be able to see it again!

### Step 2: Store your GitHub - Personal Access Token details
- Create a **github.properties** file within your root Android project
- In case of a public repository make sure you  add this file to .gitignore for keep the token private
	- Add properties **gpr.usr**=*GITHUB_USERID* and **gpr.key**=*PERSONAL_ACCESS_TOKEN*
	- Replace GITHUB_USERID with personal / organisation Github User ID and PERSONAL_ACCESS_TOKEN with the token generated in **#Step 1**
 - NOTE: you can locate your `GITHUB_USERID` by navigating in a browser to: `https://api.github.com/users/<your github username>`. It will look like the following:
```
{
  "login": "<your username>",
  "id": "<Your GITHUB_USER_ID>", // << THIS IS WHAT YOU NEED TO USE
  "node_id": "<Node ID>",
  "avatar_url": "https://avatars.githubusercontent.com/u/<GITHUB_USERID>?v=4",
  "gravatar_id": "",
  "url": "https://api.github.com/users/<your username>",
  "html_url": "https://github.com/<your username>",
  "followers_url": "https://api.github.com/users/<your username>/followers",
  "following_url": "https://api.github.com/users/<your username>/following{/other_user}",
  "gists_url": "https://api.github.com/users/<your username>/gists{/gist_id}",
  "starred_url": "https://api.github.com/users/<your username>/starred{/owner}{/repo}",
  "subscriptions_url": "https://api.github.com/users/<your username>/subscriptions",
  "organizations_url": "https://api.github.com/users/<your username>/orgs",
  "repos_url": "https://api.github.com/users/<your username>/repos",
  "events_url": "https://api.github.com/users/<your username>/events{/privacy}",
  "received_events_url": "https://api.github.com/users/<your username>/received_events",
  "type": "User",
  "site_admin": false,
  "name": "<Your Name>",
  "company": null,
  "blog": "",
  "location": null,
  "email": null,
  "hireable": null,
  "bio": null,
  "twitter_username": null,
  "public_repos": 0,
  "public_gists": 0,
  "followers": 1,
  "following": 0,
  "created_at": "<Created at>",
  "updated_at": "<Updated at>"
}
```

> Alternatively you can also add the **GPR_USER** and **GPR_API_KEY** values to your environment variables on you local machine or build server to avoid creating a github properties file

### Step 3 : Update build.gradle inside the application module
- Add the following code to **build.gradle** inside the application module that will be using the library published on GitHub Packages Repository
```markdown
def githubProperties = new Properties()
githubProperties.load(new FileInputStream(rootProject.file("github.properties")))
```
NOTE: Make sure to add the the repository at the top level in `allprojects`
```markdown
allprojects {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/FanMaker/Turdroidken")

            credentials {
                username = githubProperties['gpr.usr'] ?: System.getenv("GPR_USER")
                password = githubProperties['gpr.key'] ?: System.getenv("GPR_API_KEY")
            }
        }
    }
}
```

- inside dependencies of the build.gradle of app module, use the following code
```markdown
dependencies {
    implementation 'com.android.volley:volley:1.2.0'
    implementation 'com.fanmaker.sdk:fanmaker:2.0.0'
	...
}
```
- inside dependencies of the build.gradle of app module, make sure that you set the `compleSdkVersion` and `targetSdkVersion` to 33
```markdown
  android {
    compileSdkVersion 33
    ...

    defaultConfig {
      ...
      targetSdkVersion 33
      ...
    }
  }
```

### Step 4 : Add the following permissions to the AndroidManifest.xml
- Add the following code to the **AndroidManifext.xml** directly after the `<manifest ...>` tag.
- These allow access to the camera and image gallery for user to upload profile pictures and engage with CrowdCameo.

```markdown
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.camera.front" android:required="false" />
```

NOTE: if you are targeting SDK Version 31 or Higher, you need to make sure any activity that includes an `intent-filter` has `android:exported="true|false"` like so:
``` markdown
  <activity android:name=".MainActivity" android:exported="true">
     <intent-filter>
     ...
```

### Initialization

To initialize the SDK you need to pass a `DEV_DEFINED_KEY` and your `SDK_KEY` into the FanMaker SDK initializer. You need to call this code in your `MainActivity` class as part of your `onCreate` callback function. You can setup multiple instances of the FanMakerSDK by passing calling `setInstance` on the `FanMakerSDKs` class with a different `<DEV_DEFINED_KEY>` and a secondary `<SDK_KEY>`

```
import com.fanmaker.sdk.FanMakerSDK
import com.fanmaker.sdk.FanMakerSDKs

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // FanMaker SDK Initialization
        fanMakerSDK = FanMakerSDKs.setInstance(this, "<DEV_DEFINED_KEY>", "<SDK_KEY>")

        // Initialize a secondary FanMakerSDK Instance
        fanMakerSDK2 = FanMakerSDKs.setInstance(this, "<DEV_DEFINED_KEY_2>", "<SDK_KEY_2>")

        . . .
    }

    . . .
}
```

### Displaying FanMaker UI

In order to show FanMaker UI in your app, use the provided `FanMakerSDKWebView` class as part of your usual `Intent` call. Note: it is important that you pass your `<DEV_DEFINED_KEY>` as an extra of the intent so that the `FanMakerWebView` is able to find the `FanMakerSDK` instance to use.

```
import com.fanmaker.sdk.FanMakerSDKWebView

class MyActivity : AppCompatActivity() {
    . . .

    fun openFanMakerSDKWebView(view: View) {
        val intent = Intent(this, FanMakerSDKWebView::class.java).apply { putExtra("fanMakerKey", "<DEV_DEFINED_KEY>") }
        startActivity(intent)
    }
}
```

Then you can call `openFanMakerSDKWebView` when user taps a button, for example.

#### Displaying FanMaker UI as a Fragment

FanMaker SDK also provides a `FanMakerSDKWebViewFragment` class that can be embed into your own activity by dragging a Common -> Fragment component
from the Palette in the Layout Design view or by adding the following code in your Layout xml file:

```
<androidx.fragment.app.FragmentContainerView
        android:id="@+id/fragmentContainerView"
        android:name="com.fanmaker.sdk.FanMakerSDKWebViewFragment" />
```

Like the `FanMakerSDKWebView` class, you will need to pass the "\<DEV_DEFINED_KEY\>" to it as well when you setup the intent:
```
fun openFanMakerSDKWebViewFragment(view: View) {
        setupIdentifiers()

        val intent = Intent(this, FanMakerActivity::class.java).apply { putExtra("fanMakerKey", "<DEV_DEFINED_KEY>") }
        startActivity(intent)
    }
```

### Loading Animation | Light vs Dark
By default the FanMaker SDK will use a Light loading animated view when initializing the FanMaker SDK. There is an optional Dark loading animated view that you can use instead:
```
fanMakerSDK1!!.useDarkLoadingScreen = true
```

### Deep Linking / Universal Links
If you wish to link to something within the FanMaker SDK, you need to setup your application to accept URL Scheme or Universal Links, or know the resource you are trying to access.

An example of using a URL scheme to open app links:

In your `AndroidManifest.xml` you will need to establish conditions by which your app will know to open. These will be in the `<intent-filter>` of your activity. It is important that you have the action of `android.intent.action.VIEW` and the categories of `android.intent.category.DEFAULT` and `android.intent.category.BROWSABLE`. Furthermore, it is highly recommended that you use the `launchMode` of `singleTask` to prevent multiple instances of the application from being opened when handling deep / universal links and to mitigate any issues related to the navigation stack.

```
...
<activity android:name=".MainActivity" android:exported="true" tools:replace="android:exported" android:launchMode="singleTask">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <!-- The android.intent.action.ViEW action is used for Deep Linking -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <!--
            Establish a scheme that you want to listen for to initialize the FanMaker SDK.
            Note: the host must be fanmaker or fanmaker.com to be recognized by the SDK.
        -->
        <data
            android:scheme="turdroidken"
            android:host="fanmaker" />
        <data
            android:scheme="turdroidken2"
            android:host="fanmaker" />
    </intent-filter>
</activity>
...
```

In the example above, we have setup the host application to listen for the urls `turdroidken://fanmaker/*` and `turdroidkens://fanmaker/*`. NOTE: it is important that you use the `fanmaker` as the host in these situations. You may also listen to standard `https` schemes as long as you have a means of identifying that it will be routed to the FanMakerSDK later on.


Back in your `MainActivity`, you will need to respond to these Deep Link / Universal Link events:

```
override fun onCreate(savedInstanceState: Bundle?) {
    ...
    if (intent?.action == Intent.ACTION_VIEW) { handleDeepLink(intent) } // handleDeepLink is identified in an example below
}
```

Additionally, you'll want to add a `onNewIntent` override function if you are using `android:launchMode="singleTask"` in your `AndroidManifest.xml`
```
// Assuming your applicaiton uses the android:launchMode="singleTask" attribute in the AndroidManifest.xml file
// this method will be called when the app is already running and a new intent is received. Which is useful for deep linking.
override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    setIntent(intent)

    // Check if this intent is started via a deep link and handle it
    handleDeepLink(intent)  // handleDeepLink is identified in an example below
}
```

In this example, we have opted to make a method called `handleDeepLink` to check and handle the deep / universal link requests.
```
// Used for Deep / Universal Linking
import android.net.Uri

class MainActivity : AppCompatActivity() {
    // Declare FanMakerSDK instances you want to use
    var fanMakerSDK1: FanMakerSDK? = null
    var fanMakerSDK2: FanMakerSDK? = null

    // Declare intents for the FanMakerSDKWebView and FanMakerActivity
    lateinit var fanmakerIntent1: Intent
    lateinit var fanmakerIntent2: Intent


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
                if (scheme == "turdroidken") {
                    fanMakerSDK1?.let {
                        fanMakerSDK1!!.handleUrl(data.toString())
                        startActivity(fanmakerIntent1)
                    }
                } else if(scheme == "turdroidken2") {
                    fanMakerSDK2?.let {
                        fanMakerSDK2!!.handleUrl(data.toString())
                        startActivity(fanmakerIntent2)
                    }
                }
            }
        }
    }
    ...

```

It should also be noted, that the FanMakerSDK intents used in this example are established as a `lateinit var` and are defined in the `onCreate` method like other examples in this document.

**Important**: The FanMakerSDK verifies that the `host` is either `fanmaker` or `fanmaker.com`. If you are not using a deep link with a custom scheme, like `turdroidken`, then you will need to format the URL yourself assuming you have defined a identifable link for it like:
```
https://yourwebsite.com/fanmaker/store/items/1234 // example link the user has clicked on
```

When passing a deeplink/universal link to the Fanmaker SDK, this simply tells the SDK that when it is opened next to navigate to the desired route. If multiple links are passed without opening the SDK, then only the latest link will be shown to the user.

In your `MainActivity`
```
private fun handleDeepLink(intent: Intent?) {
        val action: String? = intent?.action
        if(Intent.ACTION_VIEW == action) {
            val data: Uri? = intent?.data

            // We verify that we have a valid deep link
            if (data != null) {
                val scheme = data.scheme
                val host = data.host
                val path = data.path
                if (scheme == "https") {
                    if (data.host == "yourwebsite.com") {
                        if (path?.contains("/fanmaker/") == true) {
                            fanMakerSDK1?.let {
                                val pathSegments = path?.split("/fanmaker/") ?: emptyList()
                                val fanmakerPath = if (pathSegments.size > 1) pathSegments[1] else ""
                                val fanMakerUrl = "https://fanmaker.com/$fanmakerPath"
                                fanMakerSDK1!!.handleUrl(fanMakerUrl)
                                startActivity(fanmakerIntent1)
                            }
                        }
                    }
                }
            }
        }
    }


```


### Passing Custom Identifiers

FanMaker UI usually requires users to input their FanMaker's Credentials. However, you can make use of up to four different custom identifiers to allow a given user to automatically login when they first open FanMaker UI.

```
import com.fanmaker.sdk.FanMakerSDKWebView

class MyActivity : AppCompatActivity() {
    var fanMakerSDK: FanMakerSDK? = null
    . . .

    fun openFanMakerSDKWebView(view: View) {
        fanMakerSDK?.memberID = <memberID>
        fanMakerSDK?.studentID = <studentID>
        fanMakerSDK?.ticketmasterID = <ticketmasterID>
        fanMakerSDK?.yinzid = <yinzid>
        fanMakerSDK?.pushNotificationToken = <pushToken>

        val intent = Intent(this, FanMakerSDKWebView::class.java).apply { putExtra("fanMakerKey", "<DEV_DEFINED_KEY>") }
        startActivity(intent)
    }
}
```

FanMaker additionally allows you to pass arbitrary identifiers as part of a HashMap. The HashMap already exists with the key `FanMaker.arbitraryIdentifiers`. Like in the examples above you can pass an arbitrary identifier with a unique key like so:
```
import com.fanmaker.sdk.FanMakerSDKWebView

class MyActivity : AppCompatActivity() {
    var fanMakerSDK: FanMakerSDK? = null
    . . .

    fun openFanMakerSDKWebView(view: View) {
        // below is a static member id example like above
        fanMakerSDK?.memberID = <memberID>

        // This would pass an arbitrary identifier of "FanMaker_NFL_OIDC_Example" with the key "nfl_oidc"
        fanMakerSDK?.arbitraryIdentifiers["nfl_oidc"] = "FanMaker_NFL_OIDC_Example"

        val intent = Intent(this, FanMakerSDKWebView::class.java).apply { putExtra("fanMakerKey", "<DEV_DEFINED_KEY>") }
        startActivity(intent)
    }
}

```
Note: you may only have 1 identifier per key. If you require more, please contact FanMaker.

These identifiers must be set **before** displaying `FanMakerSDKWebView`.

### Passing Custom Parameters

Similar to passing custom identifiers, you can also pass custom parameters to the SDK. Here is how to do so and some of the options.
```
import com.fanmaker.sdk.FanMakerSDKWebView

class MyActivity : AppCompatActivity() {
    var fanMakerSDK: FanMakerSDK? = null
    . . .

    fun openFanMakerSDKWebView(view: View) {
        // below is a static member id example like above
        fanMakerSDK?.memberID = <memberID>

        // This would tell the SDK that we want to hide the FanMaker Menu
        fanMakerSDK?.fanMakerParameters["hide_menu"] = true
        
        // These tell the SDK what the viewport dimensions are so FanMaker can respond accordingly
        fanMakerSDK?.fanMakerParameters["viewport_width"] = 512
        fanMakerSDK?.fanMakerParameters["viewport_height"] = 1024

        val intent = Intent(this, FanMakerSDKWebView::class.java).apply { putExtra("fanMakerKey", "<DEV_DEFINED_KEY>") }
        startActivity(intent)
    }
}

```
Note: you may only have 1 parameter per key.

These parameters must be set **before** displaying `FanMakerSDKWebView`.

**Note**: All of these identifiers, along with the FanMaker's User ID, are automatically defined when a user successfully logins and become accessible via the following public variables:
```
...
var fanMakerSDK: FanMakerSDK? = null
...

fanMakerSDK?.userID
fanMakerSDK?.memberID
fanMakerSDK?.studentID
fanMakerSDK?.ticketmasterID
fanMakerSDK?.yinzid
fanMakerSDK?.pushNotificationToken
fanMakerSDK?.arbitraryIdentifiers
```

### Privacy Permissions (Optional)
It is possible to pass optional privacy permission details to the FanMaker SDK where we will record the settings for the user in our system. To pass this information to FanMaker, please use the following protocols. Note: it is the same way you would pass Custom Identifiers above, but with specific keys.

The specific privacy opt in/out keys are as follows:
1. `privacy_advertising`
2. `privacy_analytics`
3. `privacy_functional`
4. `privacy_all`

*NOTE: all privacy permissions are optional. Do not pass privacy settings that you do not have user data for*


```
class MyActivity : AppCompatActivity() {
    var fanMakerSDK: FanMakerSDK? = null
    ...

    fun openFanMakerSDKWebView(view: View) {
        ...
        // This would pass an arbitrary identifier of "FanMaker_NFL_OIDC_Example" with the key "nfl_oidc"
        fanMakerSDK?.arbitraryIdentifiers["nfl_oidc"] = "FanMaker_NFL_OIDC_Example"

        // Privacy Settings
        fanMakerSDK?.arbitraryIdentifiers["privacy_advertising"] = false
        fanMakerSDK?.arbitraryIdentifiers["privacy_analytics"] = true
        fanMakerSDK?.arbitraryIdentifiers["privacy_functional"] = true
        fanMakerSDK?.arbitraryIdentifiers["privacy_all"] = false

        ...
        val intent = Intent(this, FanMakerSDKWebView::class.java).apply { putExtra("fanMakerKey", "<DEV_DEFINED_KEY>") }
        startActivity(intent)
    }
}
```

*`Note`: a value of `true` indicates that the user has opted in to a privacy permission, `false` indicates that a user has opted out.*

### Location Tracking

FanMaker UI uses location tracking services when they are available. However, location tracking can be enabled/disabled by calling the following static functions:

```
var fanMakerSDK: FanMakerSDK? = null
...

// To manually disable location tracking
fanMakerSDK?.disableLocationTracking()

// To manually enable location tracking back
fanMakerSDK?.enableLocationTracking()
```

### Location Tracking Permissions
In order for Location Tracking to work, you need to add the following permissions to your Manifest, as well as asking users for them in running time:
```
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

### Auto Checkin
The FanMakerSDK can auto checkin users to events without them opening the FanMakerSDK itself. Once the user has successfully logged into the FanMakerSDK and granted location permissions, on subsequent opens of your application, the FanMakerSDK will automatically attempt to automatically checkin the user to events within range. Be sure to enable location tracking for the feature to be enabled on every instance of the FanMakerSDK you would like to use. You must also add a lifecycle observer to each SDK instance you'd like to have Auto Checkin and App open/resume enabled for using the `lifecycle.addObserver` method:
```
class MainActivity : AppCompatActivity() {
    ...
    var fanMakerSDK1: FanMakerSDK? = null
    var fanMakerSDK2: FanMakerSDK? = null
    ...


    override fun onCreate(savedInstanceState: Bundle?) {
        ...
        if (fanMakerSDK1 != null) {
            // Enable location services for the SDK
            fanMakerSDK1!!.locationEnabled = true

            // Lifecycle is needed for the SDK to handle Auto Checkin and to Reward Usage of Host App
            lifecycle.addObserver(fanMakerSDK1!!)
        }
        if (fanMakerSDK2 != null) {
            // Enable location services for the SDK
            fanMakerSDK2!!.locationEnabled = true

            // Lifecycle is needed for the SDK to handle Auto Checkin and to Reward Usage of Host App
            lifecycle.addObserver(fanMakerSDK2!!)
        }
        ...
    }
```

### Beacons Tracking

FanMaker SDK allows beacon tracking by using the `FanMakerSDKBeaconManager` class. This class can be instantiated and used inside any `Activity` class. The simplest way to use it is the following:

```
class MainActivity : AppCompatActivity() {
    ...
    var fanMakerSDK1: FanMakerSDK? = null
    var fanMakerSDK2: FanMakerSDK? = null

    lateinit var beaconManager1: FanMakerSDKBeaconManager
    lateinit var beaconManager2: FanMakerSDKBeaconManager
    ...

    override fun onCreate(savedInstanceState: Bundle?) {
        ...
        if (fanMakerSDK1 != null) {
            // Enable location services for the SDK
            fanMakerSDK1!!.locationEnabled = true

            beaconManager1 = FanMakerSDKBeaconManager(fanMakerSDK1!!, application)
            beaconManager1.fetchBeaconRegions()
        }
        if (fanMakerSDK2 != null) {
            // Enable location services for the SDK
            fanMakerSDK2!!.locationEnabled = true

            // Initialize beacon monitoring
            beaconManager2 = FanMakerSDKBeaconManager(fanMakerSDK2!!, application)
            beaconManager2.fetchBeaconRegions()
        }
        ...
    }
```

You can, however, attach an instance of a custom class complying with the `FanMakerSDKBeaconEventHandler` interface in order to gain more control about the stages of the process, both for debugging purposes or a most customized workflow:

```
...
beaconManager1 = FanMakerSDKBeaconManager(fanMakerSDK1!!, application)
...

// The custom event handler needs to be attached before calling fetchBeaconRegions()
beaconManager1.eventHandler = MyCustomBeaconEventHandler()
beaconManager1.fetchBeaconRegions()
```

where `MyCustomBeaconEventHandler` is defined as:

```
import com.fanmaker.sdk.FanMakerSDKBeaconEventHandler

class BeaconEventHandler : FanMakerSDKBeaconEventHandler {
    // This function gets called when the list of beacon regions is successfully retrieved from the server.
    // Maybe you want to customize which regions get scanned or add debug messages to better monitor the workflow.
    override fun onBeaconRegionsReceived(manager: FanMakerSDKBeaconManager, regions: Array<FanMakerSDKBeaconRegion>) {
        manager.startScanning(regions)
    }

    // This function gets called whenever user enters into a region (gets a beacon ping for the first time)
    override fun onBeaconRegionEnter(manager: FanMakerSDKBeaconManager, region: FanMakerSDKBeaconRegion) {
        . . .
    }

    // This function gets called whenever user exits a region (stops getting a given beacon pings for a while)
    override fun onBeaconRegionExit(manager: FanMakerSDKBeaconManager, region: FanMakerSDKBeaconRegion) {
        . . .
    }
}
```

### Beacons Tracking Permissions

In order for Beacons Tracking to work, you need to add the following permissions to your Manifest, as well as asking users for them in running time:

```
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
```

### [Reward Usage of Host App](https://blog.fanmaker.com/sdk-2-0-background-check-ins-app-rewards-and-support-for-multiple-programs/)

By setting up a lifecycle observer on each instance of the FanMakerSDK, you can capture this highly useful data or award points for each day the fan uses the your host application.

```
class MainActivity : AppCompatActivity() {
    ...
    var fanMakerSDK1: FanMakerSDK? = null
    var fanMakerSDK2: FanMakerSDK? = null
    ...


    override fun onCreate(savedInstanceState: Bundle?) {
        ...
        if (fanMakerSDK1 != null) {
            // Lifecycle is needed for the SDK to handle Auto Checkin and to Reward Usage of Host App
            lifecycle.addObserver(fanMakerSDK1!!)
        }
        if (fanMakerSDK2 != null) {
            // Lifecycle is needed for the SDK to handle Auto Checkin and to Reward Usage of Host App
            lifecycle.addObserver(fanMakerSDK2!!)
        }
        ...
    }
```
