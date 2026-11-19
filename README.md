# Fanmaker SDK for Android App Development

## About

The Fanmaker SDK lets Android developers show the Fanmaker UI inside their own app. The SDK opens it as its own Activity, or you can embed it as a Fragment.

## What's new in 4.1.0 (upgrading from 4.0.x)

Every call this README documented for 4.0.3 still works, including code already compiled against 4.0.3. A few changes need your attention.

### Do this when you upgrade

- **Compile against API 36.** Set `compileSdk 36` or higher. The SDK now depends on `androidx.browser` 1.9.0, which requires it. See [Step 3](#step-3--update-buildgradle-inside-the-application-module).
- **Call `logout()` when a fan signs out of your app.** Identifiers are now saved on the device, as the Fanmaker session already was, so restarting the app clears neither. Without `logout()`, the next person to open the Fanmaker UI on that device is signed in as the previous fan. `logout()` also clears the SDK webview's cookies and stored website data, and leaves your own WebViews alone. See [Signing out](#signing-out).
- **If your manifest re-declares `FanMakerSDKWebView`, update it.** The SDK now declares it as not exported, with its own theme. Move any intent filter to your own activity, because links from outside your app can no longer reach `FanMakerSDKWebView` (see [Deep Linking](#deep-linking--universal-links)). An `android:exported` or `android:theme` value of your own on it now conflicts with the SDK's when the manifests merge.

### Recommended

- **Open the UI with `present()`.** `fanMakerSDK.present(this)` or `present(this, "/store")` replaces building an `Intent` with a `"fanMakerKey"` extra. See [Displaying Fanmaker UI](#displaying-fanmaker-ui).
- **Hand links to the SDK with `openUrl()` or `openPath()`.** `openUrl` tells you whether the SDK took the link; `openPath` takes a bare path such as `/store`. See [Deep Linking](#deep-linking--universal-links).
- **Use `FanMakerSDKs.getInstance(context, key)` in code a push notification can reach.** It rebuilds the instance when a notification tap starts your app before your app has called `setInstance`. See [Opening the SDK from a push notification](#opening-the-sdk-from-a-push-notification).
- **If you use beacons, keep them scanning in the background with `enableForegroundServiceScanning()`.** It needs three manifest entries. See [Scanning in the background](#scanning-in-the-background).

### Changes you may notice

- The Fanmaker UI now keeps its cookies and site data apart from your app's own WebViews, so a fan may need to sign in to the Fanmaker UI once after the update.

### Works better with no change from you

- Sign-ins that can't run in a webview, such as Ticketmaster passkeys, run in a private Chrome Auth Tab and come back to the Fanmaker UI. See [Sign-ins that can't run in a webview](#sign-ins-that-cant-run-in-a-webview).
- A link the fan taps on a Fanmaker page that leads to another site opens in the system browser. See [Links out of the webview](#links-out-of-the-webview).
- Opening the UI while it is already on screen reuses that screen instead of stacking a second copy.
- A notification tap that starts your app cold can open the Fanmaker UI, as long as your app has called `setInstance` for that key at least once since updating to this version.
- `canOpenUrl` and `handleUrl` also accept `https` links to your site's host, not only the `fanmaker` host form. If your app checks `canOpenUrl` before routing a link, those links now go to the SDK.
- Deep links keep their percent-encoding and no longer get a doubled slash. A queued destination survives an app restart, and is discarded on restart once it is more than ten minutes old.
- Identifiers you set survive app restarts, so you only need to set them when they change.
- Auto Checkin no longer misses its first location ping when you set `locationEnabled = true` after adding the lifecycle observer.
- Apps that minify with R8 or ProGuard no longer crash when beacon ranging starts. The SDK ships its own keep rules; a keep rule you added for AltBeacon's `RssiFilter` can stay or go.
- The Fanmaker UI no longer shows your app's name in an action bar, even if your manifest gives `FanMakerSDKWebView` a theme of your own.
- The webview sends your app's display name, version and package name, and the device's OS version and model, so Fanmaker can record which app and SDK version a device uses.

## Successful Implementation Checklist

Please follow this checklist to ensure the Fanmaker SDK is implemented correctly.
The items below are **required for certification**.

- [ ] **Ensure Fanmaker opens in a [FanMakerSDKWebView](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#displaying-fanmaker-ui) or [FanMakerSDKWebViewFragment](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#displaying-fanmaker-ui-as-a-fragment)**
- [ ] **No primary app UI visible**
  - No wrapper, header, or logos from the primary app present
    (Client branding is displayed prominently *inside* the SDK)
  - No primary app navigation visible when opening the Fanmaker SDK to avoid confusion or menu stacking
- [ ] **Exit behavior implemented**
  - Back button press **or**
  - [Close action handled](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#closing-the-sdk-from-web-content)
- [ ] **Background GPS permissions**
  - [Implemented](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#location-tracking-permissions)
  - Functioning as expected
- [ ] **Background Bluetooth permissions** *(required only if using bluetooth beacons)*
  - [Implemented](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#beacons-tracking-permissions) (`BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` on Android 12+)
  - Functioning as expected
- [ ] **Push notification token**
  - Token for each user is passed to Fanmaker
  - [Implemented](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#passing-a-push-notification-token)
- [ ] **Deep link handling**
  - [Implemented](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#deep-linking--universal-links)
  - Functioning as expected
- [ ] **Sign-out handled** *(required only if your app has its own sign-in)*
  - [`logout()`](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#signing-out) called whenever a fan signs out of your app
  - The next fan's identifiers set only after they sign in

Additional features, such as
[logged-in user handling](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#passing-custom-identifiers) and
[privacy permissions](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#privacy-permissions-optional), are optional.

The checklist above represents the **minimum required for a certified integration**.

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
    implementation 'com.fanmaker.sdk:fanmaker:4.1.0'
	...
}
```
> **Note**: Volley is bundled as a transitive dependency of the SDK — you do not need to add it separately.

> **Note**: The SDK ships R8/ProGuard keep rules for the beacon library it uses, applied automatically when your app minifies.

- inside the build.gradle of your app module, make sure you set the `compileSdk`, `targetSdk`, and `minSdk` values. `compileSdk` must be 36 or higher: the SDK depends on `androidx.browser` 1.9.0, which requires it. `minSdk` must be 24 or higher.
```markdown
  android {
    compileSdk 36
    ...

    defaultConfig {
      ...
      minSdk 24
      targetSdk 35
      ...
    }
  }
```

### Step 4 : Add the following permissions to the AndroidManifest.xml
- Add the following code to the **AndroidManifest.xml** directly after the `<manifest ...>` tag.
- These allow access to the camera and image gallery for users to upload profile pictures and engage with CrowdCameo.

```markdown
<uses-permission android:name="android.permission.CAMERA" />

<!-- Legacy storage access for Android 9 and below -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />

<!-- Modern photo picker for Android 13+ (API 33+) -->
<uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />

<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.camera.front" android:required="false" />
```

> **Note on storage permissions**: Android 10 (API 29) introduced scoped storage. The `maxSdkVersion` attributes above ensure legacy storage permissions are only applied on older OS versions. On Android 13+ (API 33+), `READ_MEDIA_VISUAL_USER_SELECTED` enables the modern photo picker instead. The SDK handles both paths automatically.

> **Note on FileProvider**: The SDK includes its own `FileProvider` for securely sharing captured photos. You do **not** need to add a FileProvider to your own manifest. If your app already declares a FileProvider, ensure the authorities do not conflict — the SDK uses `${applicationId}.fanmaker.sdk.fileprovider`.

NOTE: if you are targeting SDK Version 31 or Higher, you need to make sure any activity that includes an `intent-filter` has `android:exported="true|false"` like so:
``` markdown
  <activity android:name=".MainActivity" android:exported="true">
     <intent-filter>
     ...
```

### Initialization

To initialize the SDK you need to pass a `DEV_DEFINED_KEY` and your `SDK_KEY` into the Fanmaker SDK initializer. You need to call this code in your `MainActivity` class as part of your `onCreate` callback function. You can set up multiple instances of the `FanMakerSDK` by calling `setInstance` on the `FanMakerSDKs` class with a different `<DEV_DEFINED_KEY>` and a secondary `<SDK_KEY>`.

```kotlin
import com.fanmaker.sdk.FanMakerSDK
import com.fanmaker.sdk.FanMakerSDKs

class MainActivity : AppCompatActivity() {
    var fanMakerSDK: FanMakerSDK? = null
    var fanMakerSDK2: FanMakerSDK? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Fanmaker SDK Initialization
        FanMakerSDKs.setInstance(this, "<DEV_DEFINED_KEY>", "<SDK_KEY>")
        fanMakerSDK = FanMakerSDKs.getInstance("<DEV_DEFINED_KEY>")

        // Initialize a secondary FanMakerSDK Instance
        FanMakerSDKs.setInstance(this, "<DEV_DEFINED_KEY_2>", "<SDK_KEY_2>")
        fanMakerSDK2 = FanMakerSDKs.getInstance("<DEV_DEFINED_KEY_2>")

        . . .
    }

    . . .
}
```

`FanMakerSDKs.getInstance(key)` returns the instance your app registered in this process, or `null`. `FanMakerSDKs.getInstance(context, key)` also rebuilds an instance your app registered on an earlier run; use it in code a push notification can reach (see [Opening the SDK from a push notification](#opening-the-sdk-from-a-push-notification)).

### Displaying Fanmaker UI

```kotlin
fanMakerSDK?.present(this)
```

`present()` starts the SDK's own activity, `FanMakerSDKWebView`. Call it from an Activity and pass that Activity. The fan closes the screen with the system back gesture, or the Fanmaker page closes it (see [Closing the SDK from Web Content](#closing-the-sdk-from-web-content)).

To open a specific page, pass a path:

```kotlin
fanMakerSDK?.present(this, "/store")
```

`present()` returns `true` when it started the screen. It returns `false` when:

- the instance isn't initialized;
- the instance was built with the `FanMakerSDK` constructor instead of registered with `FanMakerSDKs.setInstance`, so it has no key to launch with;
- the path is empty or carries a scheme or host. Use [`openUrl`](#deep-linking--universal-links) for a full URL.

`isPresenting` tells you whether this instance has a screen open, and `dismiss()` closes it from your own code.

Only one screen is open per key. Calling `present()` again while the screen is open brings it to the front and navigates it to the new path, instead of opening a second copy.

`present()`, `isPresenting` and `dismiss()` work with `FanMakerSDKWebView` only. To embed the UI in your own screen, see [Displaying Fanmaker UI as a Fragment](#displaying-fanmaker-ui-as-a-fragment).

#### Building the intent yourself (legacy)

> **Still supported, but no longer recommended.** New integrations should use `present()`. Building the intent yourself means attaching the dev-defined key as a `"fanMakerKey"` extra. This path will not be removed without notice and a migration path.

```kotlin
import com.fanmaker.sdk.FanMakerSDKWebView

class MyActivity : AppCompatActivity() {
    . . .

    fun openFanMakerSDKWebView(view: View) {
        val intent = Intent(this, FanMakerSDKWebView::class.java).apply { putExtra("fanMakerKey", "<DEV_DEFINED_KEY>") }
        startActivity(intent)
    }
}
```

The intent can also carry its destination in a `"fanMakerDeepLink"` extra: a path such as `/store`, or a link [`openUrl`](#deep-linking--universal-links) accepts. The SDK ignores anything else.

A second launch for a key that already has a screen open hands its destination to that screen and closes itself. `FanMakerSDKWebView.isRunning("<DEV_DEFINED_KEY>")` and `FanMakerSDKWebView.runningKeys` tell you which keys have a screen open.

`FanMakerSDKWebView` is not exported, so only your app can start it. Route incoming links through your own activity (see [Deep Linking](#deep-linking--universal-links)) rather than giving the SDK's activity an intent filter.

#### Displaying Fanmaker UI as a Fragment

The Fanmaker SDK also provides a `FanMakerSDKWebViewFragment` class that you can embed in your own activity by dragging a Common -> Fragment component
from the Palette in the Layout Design view or by adding the following code in your Layout xml file:

```
<androidx.fragment.app.FragmentContainerView
        android:id="@+id/fragmentContainerView"
        android:name="com.fanmaker.sdk.FanMakerSDKWebViewFragment" />
```

The fragment reads the "\<DEV_DEFINED_KEY\>" from a `"fanMakerKey"` extra on its host activity's intent, so pass it when you start that activity (`FanMakerActivity` here is your own activity that holds the fragment):
```kotlin
fun openFanMakerSDKWebViewFragment(view: View) {
    setupIdentifiers()

    val intent = Intent(this, FanMakerActivity::class.java).apply { putExtra("fanMakerKey", "<DEV_DEFINED_KEY>") }
    startActivity(intent)
}
```

To open a specific page in the fragment, call `openPath` or `openUrl` (see [Deep Linking](#deep-linking--universal-links)) before you start the host activity.

### Closing the SDK from Web Content

Fanmaker web content can request that the SDK UI be dismissed by triggering a `close` action via `triggerAction()`. **The SDK handles this itself** — you do not need to write any close-handling code:

- In **Activity** mode, `FanMakerSDKWebView` finishes itself.
- In **Fragment** mode, `FanMakerSDKWebViewFragment` pops its back stack, or finishes its host activity if there is nothing to pop.

#### Handling the close yourself

If you want to run your own logic on close — a custom presentation, or deciding how a fragment host should close — set `onClose`. When `onClose` is set, the SDK will **not** auto-dismiss and hands control to you:

```kotlin
val sdk = FanMakerSDKs.getInstance("<DEV_DEFINED_KEY>") ?: return
sdk.onClose = { params ->
    // Your own dismissal logic (pop a nav controller, hide a sheet, etc.)
    // If present() opened the screen, close it with:
    sdk.dismiss()
}
```

The `params` argument is the `HashMap<String, Any>?` parsed from the JSON the web content passed with the action.

#### Migrating from `ActivityTracker`

Older integrations finished the activity themselves on the `close` action, by calling `ActivityTracker.finishActivity(FanMakerSDKWebView::class.java)` from `onActionTriggered`. That is no longer needed: remove those calls. `ActivityTracker` is deprecated and will be removed in a future release.

### Custom Loading Animation

By default the Fanmaker SDK displays a standard loading spinner while the WebView is loading. You can customize the loading animation. To replace the default spinner with your own animation, create an `animation-list` drawable in your app's `res/drawable/` directory:

```xml
<!-- res/drawable/my_custom_loading.xml -->
<animation-list xmlns:android="http://schemas.android.com/apk/res/android"
    android:oneshot="false">
    <item android:drawable="@drawable/my_loading_frame_00" android:duration="33" />
    <item android:drawable="@drawable/my_loading_frame_01" android:duration="33" />
    <item android:drawable="@drawable/my_loading_frame_02" android:duration="33" />
    <!-- ... -->
</animation-list>
```

Then pass the drawable resource to the SDK before opening the WebView:

```
fanMakerSDK1!!.setLoadingAnimationDrawable(R.drawable.my_custom_loading)
```

The custom animation will be centered on the loading screen.

### Deep Linking / Universal Links
To open a link inside the Fanmaker UI, your app receives the link through an intent filter on one of your own activities, hands it to the SDK, and presents the UI. Don't put the intent filter on `FanMakerSDKWebView`: it is not exported, so links from outside your app can't reach it.

An example of using a URL scheme to open app links:

In your `AndroidManifest.xml` you will need to establish conditions by which your app will know to open. These will be in the `<intent-filter>` of your activity. It is important that you have the action of `android.intent.action.VIEW` and the categories of `android.intent.category.DEFAULT` and `android.intent.category.BROWSABLE`. Furthermore, it is highly recommended that you use the `launchMode` of `singleTask` to prevent multiple instances of the application from being opened when handling deep / universal links and to mitigate any issues related to the navigation stack. It also matters for sign-ins: on a phone without Chrome's Auth Tab, a sign-in finishes in a Custom Tab and returns through your app's link, and `singleTask` brings it back to the app that is already running instead of opening a second copy on top.

```
...
<activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTask">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <!-- The android.intent.action.VIEW action is used for Deep Linking -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <!--
            Establish a scheme that you want to listen for to open the Fanmaker SDK.
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

In the example above, the app listens for the URLs `turdroidken://fanmaker/*` and `turdroidken2://fanmaker/*`, one scheme per `FanMakerSDK` instance. Use `fanmaker` as the host for these links. You can also listen for `https` links, as long as you can tell which ones belong to the Fanmaker SDK.

Back in your `MainActivity`, respond to these links when the activity is created:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    ...
    if (intent?.action == Intent.ACTION_VIEW) { handleDeepLink(intent) } // handleDeepLink is shown below
}
```

and, because of `android:launchMode="singleTask"`, when a link arrives while the app is already running:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleDeepLink(intent)
}
```

`handleDeepLink` picks the `FanMakerSDK` instance for the link's scheme, hands it the link with `openUrl`, and presents the UI:

```kotlin
import android.net.Uri

class MainActivity : AppCompatActivity() {
    var fanMakerSDK1: FanMakerSDK? = null
    var fanMakerSDK2: FanMakerSDK? = null
    ...

    private fun handleDeepLink(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return
        val data: Uri = intent.data ?: return

        // One scheme per FanMakerSDK instance, as declared in AndroidManifest.xml
        val sdk = when (data.scheme) {
            "turdroidken" -> fanMakerSDK1
            "turdroidken2" -> fanMakerSDK2
            else -> null
        } ?: return // not a Fanmaker link: handle it yourself

        if (sdk.openUrl(data.toString())) {
            sdk.present(this)
        }
    }
}
```

`openUrl` returns `true` when the SDK takes the link and queues it as the next destination, and `false` when it isn't a link the SDK handles, so you can fall through to your own handling. `present()` then opens the UI, or navigates the screen that is already open. If you use the [Fragment](#displaying-fanmaker-ui-as-a-fragment), start its host activity instead of calling `present()`.

`openUrl` accepts:

- any scheme whose host is `fanmaker` or `fanmaker.com`, such as `turdroidken://fanmaker/store`;
- an `http` or `https` link to your site's host, or to another domain Fanmaker has configured for your site. The SDK learns these hosts from Fanmaker when its screen loads and keeps them across restarts, so only on a fresh install, before the screen has loaded once, it may not recognize them yet and returns `false`.

When you already know a link belongs to the Fanmaker SDK, pass a path instead and skip the hostname: `present(this, "/store")`, or `openPath("/store")` followed by `present(this)` or starting your fragment's host activity. `openPath` returns `false` for an empty path or one that carries a scheme or host.

For example, if links on your own website such as `https://yourwebsite.com/fanmaker/store/items/1234` should open the Fanmaker UI, add this to `handleDeepLink` before the scheme check:

```kotlin
val path = data.path
if (data.host == "yourwebsite.com" && path != null && path.startsWith("/fanmaker/")) {
    fanMakerSDK1?.present(this, path.removePrefix("/fanmaker"))
    return
}
```

A queued destination is opened the next time the UI opens. If several are queued first, only the latest is shown. It is saved on the device, so a notification tap that starts your app doesn't lose it. When the app restarts, a saved destination more than ten minutes old is discarded, so one that was never opened doesn't appear on a later launch.

`canOpenUrl(url)` and `handleUrl(url)` still work. `handleUrl` is `openUrl` without the return value.

#### Opening the SDK from a push notification

A notification tap can start your app in a new process, before your `MainActivity` has called `setInstance`. In code a notification can reach, look the instance up with a Context:

```kotlin
// In the activity your notification opens, with the destination from your payload
fun openFanmakerFromPush(destination: String) {
    val sdk = FanMakerSDKs.getInstance(this, "<DEV_DEFINED_KEY>") ?: return
    if (destination.startsWith("/")) {
        sdk.present(this, destination)
    } else if (sdk.openUrl(destination)) {
        sdk.present(this)
    }
}
```

`FanMakerSDKs.getInstance(context, key)` returns the instance this process registered or, if there isn't one, rebuilds it from what the SDK saved the last time your app called `setInstance` for that key. It returns `null` if your app hasn't called `setInstance` for the key since updating to this version. The fan's session and identifiers carry over to a rebuilt instance. Callbacks and parameters you set (`onClose`, `onActionTriggered`, `fanMakerParameters`) do not, so on the close action a rebuilt instance simply closes its own screen. When your app later calls `setInstance`, its instance replaces the rebuilt one.

`FanMakerSDKs.getInstance(key)`, without a Context, is unchanged: it returns only what this process registered.

Prefer a path in your push payload. An `https` link is only accepted once the SDK knows your site's hosts (see above).

#### Links out of the webview

You don't need to wire anything up. When the fan taps a link on a Fanmaker page that leads to a host that isn't Fanmaker's (any `*.fanmaker.com` host, your site's host, or another domain Fanmaker has configured for your site), it opens in the system browser, so partner and ticketing pages get the browser's controls and the fan's existing session there.

Only a tap that replaces the whole page, on a Fanmaker page, does this. Redirects, script-driven navigations, taps inside embedded frames and taps on a provider's own pages stay in the webview, because sign-in and payment flows pass through third-party pages and have to come back.

#### Sign-ins that can't run in a webview

Some sign-ins don't work inside an embedded webview: Ticketmaster's passkeys only work on ticketmaster.com, and some identity providers refuse embedded webviews. For those, the Fanmaker page asks the SDK to run the sign-in in Chrome's **Auth Tab**, as a private session, so it neither uses nor changes the fan's own browser sign-ins. Passkeys work there. When the sign-in finishes, the Auth Tab hands the result back to the SDK, which loads it into the webview. You don't need to wire anything up.

If the fan's browser doesn't support Auth Tab, the SDK opens a Custom Tab instead, and the sign-in comes back to your app as a `<scheme>://fanmaker/...` deep link, where `<scheme>` is the scheme Fanmaker has configured for your site. The `handleDeepLink` example above (`openUrl`, then `present()`) handles it.

This is why the SDK requires `compileSdk 36`: `androidx.browser`, which provides the Auth Tab, requires it.

### Passing Custom Identifiers

The Fanmaker UI usually asks users for their Fanmaker credentials. However, you can make use of up to four different custom identifiers to allow a given user to automatically log in when they first open the Fanmaker UI.

```kotlin
class MyActivity : AppCompatActivity() {
    var fanMakerSDK: FanMakerSDK? = null
    . . .

    fun openFanMakerSDKWebView(view: View) {
        val sdk = fanMakerSDK ?: return
        sdk.memberID = "<memberID>"
        sdk.studentID = "<studentID>"
        sdk.ticketmasterID = "<ticketmasterID>"
        sdk.yinzid = "<yinzid>"

        sdk.present(this)
    }
}
```

Fanmaker additionally allows you to pass arbitrary identifiers in the `arbitraryIdentifiers` map, with a unique key and a `String` value:
```kotlin
class MyActivity : AppCompatActivity() {
    var fanMakerSDK: FanMakerSDK? = null
    . . .

    fun openFanMakerSDKWebView(view: View) {
        val sdk = fanMakerSDK ?: return
        // below is a static member id example like above
        sdk.memberID = "<memberID>"

        // This would pass an arbitrary identifier of "FanMaker_NFL_OIDC_Example" with the key "nfl_oidc"
        sdk.arbitraryIdentifiers["nfl_oidc"] = "FanMaker_NFL_OIDC_Example"

        sdk.present(this)
    }
}
```
Note: you may only have 1 identifier per key. If you require more, please contact Fanmaker.

These identifiers must be set **before** displaying the Fanmaker UI. The SDK saves them on the device and restores them when your app restarts, so you only need to set them again when they change. When a fan signs out, clear them with `logout()` (see [Signing out](#signing-out)).

### Passing Custom Parameters

Similar to passing custom identifiers, you can also pass custom parameters to the SDK. Here is how to do so and some of the options.
```kotlin
class MyActivity : AppCompatActivity() {
    var fanMakerSDK: FanMakerSDK? = null
    . . .

    fun openFanMakerSDKWebView(view: View) {
        val sdk = fanMakerSDK ?: return
        // below is a static member id example like above
        sdk.memberID = "<memberID>"

        // This would tell the SDK that we want to hide the Fanmaker Menu
        sdk.fanMakerParameters["hide_menu"] = true

        // These tell the SDK what the viewport dimensions are so Fanmaker can respond accordingly
        sdk.fanMakerParameters["viewport_width"] = 512
        sdk.fanMakerParameters["viewport_height"] = 1024

        sdk.present(this)
    }
}
```
Note: you may only have 1 parameter per key.

These parameters must be set **before** displaying the Fanmaker UI. Unlike identifiers, parameters are not saved on the device: set them again each time your app starts.

**Note**: All of these identifiers, along with the Fanmaker User ID, are automatically defined when a user successfully logs in and become accessible via the following public variables:
```kotlin
var fanMakerSDK: FanMakerSDK? = null
...

fanMakerSDK?.userID
fanMakerSDK?.memberID
fanMakerSDK?.studentID
fanMakerSDK?.ticketmasterID
fanMakerSDK?.yinzid
fanMakerSDK?.arbitraryIdentifiers
```

### Passing a Push Notification Token

In addition to user identifiers, the Fanmaker SDK allows you to provide a Push Notification Token. This token enables Fanmaker to send push notifications directly to users of your application.

Once you've obtained a valid push notification token from Firebase Cloud Messaging (FCM), pass it to the Fanmaker SDK before presenting the Fanmaker UI. The SDK will associate the token with the current user session.

```kotlin
class MyActivity : AppCompatActivity() {
    var fanMakerSDK: FanMakerSDK? = null
    . . .

    fun openFanMakerSDKWebView(view: View) {
        val sdk = fanMakerSDK ?: return
        sdk.pushNotificationToken = "<pushToken>"

        sdk.present(this)
    }
}
```

**Note**: The push notification token is automatically defined when set and becomes accessible via the following public variable:
```kotlin
fanMakerSDK?.pushNotificationToken
```

The token is saved on the device like the identifiers, and `logout()` clears it. Set it again after the next fan signs in.

### Privacy Permissions (Optional)
It is possible to pass optional privacy permission details to the Fanmaker SDK where we will record the settings for the user in our system. To pass this information to Fanmaker, please use the following protocols. Note: it is the same way you would pass Custom Identifiers above, but with specific keys.

The specific privacy opt in/out keys are as follows:
1. `privacy_advertising`
2. `privacy_analytics`
3. `privacy_functional`
4. `privacy_all`

*NOTE: all privacy permissions are optional. Do not pass privacy settings that you do not have user data for*


```kotlin
class MyActivity : AppCompatActivity() {
    var fanMakerSDK: FanMakerSDK? = null
    ...

    fun openFanMakerSDKWebView(view: View) {
        val sdk = fanMakerSDK ?: return
        ...
        // This would pass an arbitrary identifier of "FanMaker_NFL_OIDC_Example" with the key "nfl_oidc"
        sdk.arbitraryIdentifiers["nfl_oidc"] = "FanMaker_NFL_OIDC_Example"

        // Privacy Settings
        sdk.arbitraryIdentifiers["privacy_advertising"] = "false"
        sdk.arbitraryIdentifiers["privacy_analytics"] = "true"
        sdk.arbitraryIdentifiers["privacy_functional"] = "true"
        sdk.arbitraryIdentifiers["privacy_all"] = "false"

        ...
        sdk.present(this)
    }
}
```

*`Note`: a value of `"true"` indicates that the user has opted in to a privacy permission, `"false"` indicates that a user has opted out. `arbitraryIdentifiers` holds `String` values, so pass them as strings.*

### Signing out

When a fan signs out of your app, call `logout()`:

```kotlin
fanMakerSDK?.logout()
```

**When to call it:** every time a fan signs out of your app, including when one fan signs out so another can sign in on the same device.

**When you don't need to:** when a fan logs out *inside* the Fanmaker UI, the SDK ends the Fanmaker session itself.

**Switching fans:** call `logout()` when the first fan signs out, and set the next fan's identifiers only once they have signed in to your app. Don't rely on new identifiers replacing the old fan on their own: if the new fan has no Fanmaker account yet, the previous fan's session would still be there.

`logout()` clears every identifier, including the push notification token and the arbitrary identifiers, and ends the Fanmaker session, both in memory and on the device. Identifiers and the session survive app restarts, so without it the next person to open the Fanmaker UI on that device is signed in as the previous fan.

`logout()` also clears the cookies and stored website data of the SDK's webview, so neither Fanmaker's pages nor a sign-in provider such as Ticketmaster can sign the next person in as the previous fan. The SDK's webview keeps that data in a WebView profile of its own, so `logout()` leaves your app's own WebViews alone. On a device whose Android System WebView is too old for profiles, every WebView in an app shares one store, and `logout()` clears all of it. To act once everything is cleared, pass a completion, which runs on the main thread:

```kotlin
fanMakerSDK?.logout {
    // the Fanmaker session and all web data are gone
}
```

Two narrower calls exist:

- `clearSessionToken()` ends the Fanmaker session but keeps the identifiers, so the next time the UI opens, the SDK can sign the same fan back in with them.
- `clearIdentifiers()` clears the identifiers but leaves the session in place. It is not a sign-out.

### Location Tracking

The Fanmaker UI uses location tracking services when they are available. Location tracking for Auto Checkin is off until you enable it, either with `locationEnabled = true` (see [Auto Checkin](#auto-checkin)) or with these functions:

```kotlin
var fanMakerSDK: FanMakerSDK? = null
...

// To manually disable location tracking
fanMakerSDK?.disableLocationTracking()

// To manually enable location tracking back
fanMakerSDK?.enableLocationTracking()
```

### Location Tracking Permissions
In order for Location Tracking to work, you need to add the following permissions to your Manifest, as well as requesting them at runtime:
```
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

These foreground location permissions are sufficient for Auto Checkin — the GPS ping fires when the app resumes to the foreground (`onResume`), so background location access is not required for this feature. `ACCESS_BACKGROUND_LOCATION` is only needed if you are using [Beacons Tracking](https://github.com/FanMaker/Turdroidken?tab=readme-ov-file#beacons-tracking-permissions).

### Auto Checkin
The Fanmaker SDK can check users in to events automatically, without them opening the Fanmaker UI. Once the user has logged in through the Fanmaker UI and granted location permissions, each time your app opens or resumes the SDK attempts to check the user in to events within range. Enable location tracking on every `FanMakerSDK` instance you want to use this with, and add each of those instances as a lifecycle observer with `lifecycle.addObserver`, which also enables App open/resume rewards:
```kotlin
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

The order doesn't matter: if you enable location tracking after the app has resumed, the SDK sends the location ping it missed.

### Beacons Tracking

The Fanmaker SDK allows beacon tracking by using the `FanMakerSDKBeaconManager` class. This class can be instantiated and used inside any `Activity` class. The simplest way to use it is the following:

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

```kotlin
import com.fanmaker.sdk.FanMakerSDKBeaconEventHandler
import com.fanmaker.sdk.FanMakerSDKBeaconManager
import com.fanmaker.sdk.FanMakerSDKBeaconRegion

class MyCustomBeaconEventHandler : FanMakerSDKBeaconEventHandler {
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

> **Important**: Beacon tracking is an **optional feature**. Only add these permissions if your integration uses `FanMakerSDKBeaconManager`. Declaring permissions your app does not use is a violation of [Google Play's permissions policy](https://support.google.com/googleplay/android-developer/answer/9214102) and may result in rejection.

In order for Beacons Tracking to work, you need to add the following permissions to your Manifest, as well as requesting them at runtime:

```
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Bluetooth permissions required for beacon scanning on Android 12+ (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

> **Google Play — Background Location**: Apps that declare `ACCESS_BACKGROUND_LOCATION` are subject to an additional review in the Google Play Console. You must complete the **Background location access** declaration form in your app's Play Console and provide a justification for why background location is needed. Approval is not automatic. See [Google's guidance on background location](https://support.google.com/googleplay/android-developer/answer/9799150).

> **Android 12+ Bluetooth**: `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` are runtime permissions on Android 12 (API 31) and above. You must request both from the user at runtime in addition to declaring them in the manifest. On Android 11 and below, the legacy `BLUETOOTH` and `BLUETOOTH_ADMIN` permissions required for beacon scanning are automatically included via the altbeacon library's own manifest — no additional declarations needed for those older API levels.

> **Do not use `neverForLocation`**: Do not add `android:usesPermissionFlags="neverForLocation"` to `BLUETOOTH_SCAN`. Although this flag appears to reduce permission scope, it causes Android to block iBeacon advertisement packets entirely, which breaks beacon detection.

> **Background scanning on Android 8+ (API 26+)**: Android stops background beacon scanning within minutes of your app leaving the screen. To keep it going, see [Scanning in the background](#scanning-in-the-background).

### Scanning in the background

The SDK can run the scan in AltBeacon's foreground service, which keeps it going while your app is in the background. Call this once, before the first `fetchBeaconRegions()`. One call covers every SDK instance in your app:

```
beaconManager.enableForegroundServiceScanning()
beaconManager.fetchBeaconRegions()
```

Android shows a notification for as long as the service runs, and the SDK builds it: your app's name and "Scanning Bluetooth Beacons for proximity based rewards and offers", on a low-importance "Beacon scanning" channel. Its icon is the push icon you declare for Firebase (`com.google.firebase.messaging.default_notification_icon`), or the system Bluetooth icon if you don't declare one. To change the wording or the icon, pass your own notification:

```
// Create the "beacon_scan" notification channel first on Android 8+.
val notification = NotificationCompat.Builder(this, "beacon_scan")
    .setSmallIcon(R.drawable.ic_notification)
    .setContentTitle("Scanning for beacons")
    .setOngoing(true)
    .build()

beaconManager.enableForegroundServiceScanning(notification)
```

Add these to your `AndroidManifest.xml`:

```
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

<application>
    <service
        android:name="org.altbeacon.beacon.service.BeaconService"
        android:foregroundServiceType="location" />
</application>
```

If one is missing, `enableForegroundServiceScanning` returns `false`, logs the line to add, and beacons scan the default way. It also returns `false` if beacons are already scanning, which is why it has to come before the first `fetchBeaconRegions()`. Once it is on:

- The SDK scans for 1.5 seconds about twice a minute, in the foreground and the background.
- Scanning waits until the fan has granted location and, on Android 12+, Nearby devices. Starting a location foreground service without them crashes the app on Android 14+. Call `fetchBeaconRegions()` again once they are granted.

Google Play asks apps that run a location foreground service to declare it in Play Console.

### [Reward Usage of Host App](https://blog.fanmaker.com/sdk-2-0-background-check-ins-app-rewards-and-support-for-multiple-programs/)

By setting up a lifecycle observer on each `FanMakerSDK` instance, you can capture this highly useful data or award points for each day the fan uses your host application.

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

## Upgrading to 2.0 from 1.x

### Step 1:
Previously the Fanmaker SDK was initialized like this:
```
FanMakerSDK.initialize("<SDK_KEY>")
```

Now, you need to leverage the `FanMakerSDKs` singleton class to initialize and store your `FanMakerSDK` instances:
```
// Import the new FanMakerSDKs singleton class into your MainActivity so that it can be leveraged throughout the application
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
3. the `SDK_KEY` provided to you by the Fanmaker team as you were using previously


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

### Step 3:
If you are passing any values to the `FanMakerSDK` using one of our methods like setMemberID, setTicketmasterID, or setFanMakerIdentifiers, then be sure to specify which SDK instance you are passing the values to:

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
        fanMakerSDK2?.ticketmasterID = "ticketmasterID"
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
If you are using bluetooth beacons through the Fanmaker SDK, you will need to update your implementation.

Where you are initializing the `FanMakerSDKBeaconManager`, you will now need to pass the instance of the SDK you are using:

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
```

In the example above, `beaconManager1` and `beaconManager2` are established as `lateinit var` and then are defined in the `onCreate` method of your `MainActivity` using the `FanMakerSDKBeaconManager` class, which now requires a `FanMakerSDK` as the first argument.
