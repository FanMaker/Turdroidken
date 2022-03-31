# FanMaker SDK for Android App Development 

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
	
> Alternatively you can also add the **GPR_USER** and **GPR_API_KEY** values to your environment variables on you local machine or build server to avoid creating a github properties file

### Step 3 : Update build.gradle inside the application module 
- Add the following code to **build.gradle** inside the application module that will be using the library published on GitHub Packages Repository
```markdown
def githubProperties = new Properties()
githubProperties.load(new FileInputStream(rootProject.file("github.properties")))  
```
```markdown
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
```

- inside dependencies of the build.gradle of app module, use the following code
```markdown
dependencies {
    implementation 'com.android.volley:volley:1.2.0'
    implementation 'com.fanmaker.sdk:fanmaker:1.2'
	...
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

### Initialization

To initialize the SDK you need to pass your `<SDK_KEY>` into the FanMaker SDK initializer. You need to call this code in your `MainActivity` class as part of your `onCreate` callback function.

```
import com.fanmaker.sdk.FanMakerSDK

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // FanMaker SDK Initialization
        FanMakerSDK.initialize("<SDK_KEY>")
        
        . . .
    }
    
    . . .
}
```

### Displaying FanMaker UI

In order to show FanMaker UI in your app, use the provided `FanMakerSDKWebView` class as part of your usual `Intent` call.

```
import com.fanmaker.sdk.FanMakerSDKWebView

class MyActivity : AppCompatActivity() {
    . . .

    fun openFanMakerSDKWebView(view: View) {
        val intent = Intent(this, FanMakerSDKWebView::class.java)
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

### Passing Custom Identifiers

FanMaker UI usually requires users to input their FanMaker's Credentials. However, you can make use of up to four different custom identifiers to allow a given user to automatically login when they first open FanMaker UI.

```
import com.fanmaker.sdk.FanMakerSDKWebView

class MyActivity : AppCompatActivity() {
    . . .

    fun openFanMakerSDKWebView(view: View) {
        FanMakerSDK.memberID = <memberID>
        FanMakerSDK.studentID = <studentID>
        FanMakerSDK.ticketmasterID = <ticketmasterID>
        FanMakerSDK.yinzid = <yinzid>
    
        val intent = Intent(this, FanMakerSDKWebView::class.java)
        startActivity(intent)
    }
}
```

These identifiers must be set **before** displaying `FanMakerSDKWebView`.

**Note**: All of these identifiers, along with the FanMaker's User ID, are automatically defined when a user successfully logins and become accessible via the following public variables:

```
FanMakerSDK.userID
FanMakerSDK.memberID
FanMakerSDK.studentID
FanMakerSDK.ticketmasterID
FanMakerSDK.yinzid
```

### Location Tracking

FanMaker UI uses location tracking services when they are available. However, location tracking can be enabled/disabled by calling the following static functions:

```
// To manually disable location tracking
FanMakerSDK.disableLocationTracking()

// To manually enable location tracking back
FanMakerSDK.enableLocationTracking()
```
