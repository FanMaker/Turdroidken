package com.fanmaker.sdk

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley

class FanMakerSDKWebView : AppCompatActivity() {
    private val permission = arrayOf(Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
    )
    private val FILECHOOSER_RESULTCODE = 1
    private val REQUEST_SELECT_FILE = 100

    private var mUploadMessage: ValueCallback<Uri>? = null
    private var uploadMessage: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fanmaker_sdk_webview)

        val webView = findViewById<WebView>(R.id.fanmaker_sdk_webview)
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

        if(!isPermissionGranted()) { askPermissions() }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request?.resources)
            }

            private fun openFileChooser(uploadMsg: ValueCallback<Uri>) {
                mUploadMessage = uploadMsg
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                startActivityForResult(Intent.createChooser(intent, "File Chooser"), FILECHOOSER_RESULTCODE)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                uploadMessage?.onReceiveValue(null)
                uploadMessage = null

                uploadMessage = filePathCallback

                val intent = fileChooserParams!!.createIntent()
                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE)
                } catch (e: ActivityNotFoundException) {
                    uploadMessage = null
                    Toast.makeText(applicationContext, "Cannot Open File Chooser", Toast.LENGTH_LONG).show()
                    return false
                }

                return true

//                return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
            }
        }

        val jsInterface = FanMakerSDKWebInterface(this,
            { authorized ->
                var jsString: String = "FanMakerReceiveLocationAuthorization("
                if (authorized) jsString = "${jsString}true)"
                else jsString = "${jsString}false)"
                Log.w("FANMAKER", jsString)

                this@FanMakerSDKWebView.runOnUiThread {
                    webView.evaluateJavascript(jsString, null)
                }
            },
            { location ->
                val jsString: String =
                    "FanMakerReceiveLocation({ lat: ${location.latitude}, lng: ${location.longitude} })"
                Log.w("FANMAKER", jsString)

                this@FanMakerSDKWebView.runOnUiThread {
                    webView.evaluateJavascript(jsString, null)
                }
            }
        )

        webView.addJavascriptInterface(jsInterface, "fanmaker")

        val headers: HashMap<String, String> = HashMap<String, String>()
        headers.put("X-FanMaker-SDK-Version", "1.1")
        headers.put("X-FanMaker-SDK-Platform", "Turdroidken")

        if (FanMakerSDK.memberID != "") headers.put("X-Member-ID", FanMakerSDK.memberID)
        if (FanMakerSDK.studentID != "") headers.put("X-Student-ID", FanMakerSDK.studentID)
        if (FanMakerSDK.ticketmasterID != "") headers.put("X-Ticketmaster-ID", FanMakerSDK.ticketmasterID)
        if (FanMakerSDK.yinzid != "") headers.put("X-Yinzid", FanMakerSDK.yinzid)
        if (FanMakerSDK.pushNotificationToken != "") headers.put("X-PushNotification-Token", FanMakerSDK.pushNotificationToken)

        val queue = Volley.newRequestQueue(this)
        val url = "https://api.fanmaker.com/api/v2/site_details/info"
        val settings = this.getSharedPreferences("com.fanmaker.sdk", Context.MODE_PRIVATE)
        val token = settings.getString("token", "")
        token?.let {
            headers.put("X-FanMaker-SessionToken", it)
        }

        val request = object: JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                val status = response.getInt("status")
                if (status == 200) {
                    val data = response.getJSONObject("data")
                    val sdk_url = data.getString("sdk_url")
                    Log.w("FANMAKER", sdk_url)
                    webView.loadUrl(sdk_url, headers)
                } else {
                    webView.loadUrl("https://admin.fanmaker.com/500")
                }
            },
            { error ->
                webView.loadUrl("https://admin.fanmaker.com/500")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["X-FanMaker-Token"] = FanMakerSDK.apiKey
                return headers
            }
        }
        queue.add(request)
    }

    private fun askPermissions() {
        ActivityCompat.requestPermissions(this, permission, FILECHOOSER_RESULTCODE)
    }

    private fun isPermissionGranted(): Boolean {
        permission.forEach {
            if(ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED)
                return false
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode === REQUEST_SELECT_FILE) {
                if (uploadMessage == null)
                    return
                print("result code = " + resultCode)
                var results: Array<Uri>? = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                uploadMessage?.onReceiveValue(results)
                uploadMessage = null
            }
        } else if (requestCode === FILECHOOSER_RESULTCODE) {
            if (null == mUploadMessage)
                return
            // Use MainActivity.RESULT_OK if you're implementing WebView inside Fragment
            // Use RESULT_OK only if you're implementing WebView inside an Activity
            val result = if (intent == null || resultCode !== RESULT_OK) null else intent.data
            mUploadMessage?.onReceiveValue(result)
            mUploadMessage = null
        } else
            Toast.makeText(applicationContext, "Failed to Upload Image", Toast.LENGTH_LONG).show()

        super.onActivityResult(requestCode, resultCode, data)
    }
}