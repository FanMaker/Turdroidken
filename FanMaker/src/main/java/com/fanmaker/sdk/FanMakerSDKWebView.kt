package com.fanmaker.sdk

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.hardware.camera2.CameraDevice
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.fanmaker.sdk.databinding.FanmakerSdkWebviewBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import com.fanmaker.sdk.FanMakerSDKs

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import android.widget.ImageView
import android.graphics.drawable.AnimationDrawable
import android.widget.FrameLayout

class FanMakerSDKWebView : AppCompatActivity() {
    private lateinit var fanMakerSDK: FanMakerSDK
    private lateinit var fanMakerSharedPreferences: FanMakerSharedPreferences
    lateinit var animationDrawable: AnimationDrawable
    lateinit var loadingAnimationFrame: FrameLayout
    lateinit var loadingAnimationView: ImageView

    private val permission = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
    )
    private val MEDIA_RESULTCODE = 1
    private val PERMISSION_RESULTCODE = 2
    private val REQUEST_SELECT_FILE = 100
    private val REQUEST_IMAGE_CAPTURE = 1
    private val FILENAME_FORMAT = "yyyyMMdd-HHmmssSSS"
    private var cameraId = 1

    private var mUploadMessage: ValueCallback<Uri>? = null
    private var uploadMessage: ValueCallback<Array<Uri>>? = null

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private lateinit var backgroundHandlerThread: HandlerThread
    private lateinit var backgroundHandler: android.os.Handler

    private lateinit var fanMakerCameraProvider: ProcessCameraProvider
    private var camPermissionMethod: String? = null
    private var fanMakerFileChooserParams: WebChromeClient.FileChooserParams? = null

    private lateinit var viewBinding: FanmakerSdkWebviewBinding
    private fun startBackgroundThread() {
        backgroundHandlerThread = HandlerThread("CameraVideoThread")
        backgroundHandlerThread.start()
        backgroundHandler = android.os.Handler(
            backgroundHandlerThread.looper
        )
    }

    private fun stopBackgroundThread() {
        backgroundHandlerThread.quitSafely()
        backgroundHandlerThread.join()
    }

    private fun setupWindowInsets() {
        val rootView = viewBinding.root
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply padding for system bars to the main container, allowing WebView to extend behind them
            view.updatePadding(
                top = insets.top,
                left = insets.left,
                right = insets.right,
                bottom = insets.bottom
            )
            
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.fanmaker_sdk_webview)

        val fanMakerKey = intent.getStringExtra("fanMakerKey")
        if (fanMakerKey == null) {
            Log.e("FanMakerSDKWebView", "fanMakerKey is null. Cannot initialize FanMakerSDK.")
            finish() // Close the activity
            return
        }

        val fanMakerSDK = FanMakerSDKs.getInstance(fanMakerKey)
        if (fanMakerSDK == null) {
            Log.e("FanMakerSDKWebView", "Failed to get instance of FanMakerSDK.")
            finish() // Close the activity
            return
        }

        fanMakerSharedPreferences = FanMakerSharedPreferences(getApplicationContext(), fanMakerSDK!!.apiKey)

        viewBinding = FanmakerSdkWebviewBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        
        // Set up window insets handling
        setupWindowInsets()
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.closeCameraButton.setOnClickListener { closeCamera() }
        viewBinding.switchCameraButton.setOnClickListener { flipCamera() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        val webView = findViewById<WebView>(R.id.fanmaker_sdk_webview)
        
        // Enable WebView debugging for Chrome DevTools
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        
        if(fanMakerSDK.useDarkLoadingScreen) {
            loadingAnimationFrame = findViewById<FrameLayout>(R.id.fanmaker_sdk_dark_loading_frame)
            loadingAnimationView = findViewById<ImageView>(R.id.darkLoadingGif)
        } else {
            loadingAnimationFrame = findViewById<FrameLayout>(R.id.fanmaker_sdk_light_loading_frame)
            loadingAnimationView = findViewById<ImageView>(R.id.lightLoadingGif)
        }

        loadingAnimationFrame.visibility = FrameLayout.VISIBLE
        animationDrawable = loadingAnimationView.drawable as AnimationDrawable
        animationDrawable.start()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadingAnimationFrame.visibility = FrameLayout.GONE
                animationDrawable.stop()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                Log.e("FANMAKER", "WebView error: ${error?.description} for URL: ${request?.url}")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                Log.w("FANMAKER", "Loading URL: ${request?.url}")
                return super.shouldOverrideUrlLoading(view, request)
            }
        }

        webView.webChromeClient = WebChromeClient()

        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        
        // Allow mixed content for Charles Proxy debugging
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request?.resources)
            }

            private fun openFileChooser(uploadMsg: ValueCallback<Uri>) {
                mUploadMessage = uploadMsg
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                startActivityForResult(Intent.createChooser(intent, "File Chooser"), MEDIA_RESULTCODE)
            }
            private val cameraStateCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {}

                override fun onDisconnected(cameraDevice: CameraDevice) {
                    Log.w("CAMERA", "DISCONNECTED")
                }

                override fun onError(cameraDevice: CameraDevice, error: Int) {
                    val errorMsg = when(error) {
                        ERROR_CAMERA_DEVICE -> "Fatal (device)"
                        ERROR_CAMERA_DISABLED -> "Device policy"
                        ERROR_CAMERA_IN_USE -> "Camera in use"
                        ERROR_CAMERA_SERVICE -> "Fatal (service)"
                        ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                        else -> "Unknown"
                    }
                    Log.w("CAMERA ERROR", "Error when trying to connect camera $errorMsg")
                }
            }

            private fun selectImage(fileChooserParams: FileChooserParams?) {
                val dialogBuilder = AlertDialog.Builder(this@FanMakerSDKWebView)

                dialogBuilder.setTitle("Please Select:")
                dialogBuilder.setCancelable(false)
                dialogBuilder.setPositiveButton("Camera", DialogInterface.OnClickListener { dialog, id ->
                    startCamera()
                    dialog.cancel()
                })
                dialogBuilder.setNegativeButton("Gallery", DialogInterface.OnClickListener { dialog, id ->
                    openPicker(fileChooserParams)
                    dialog.cancel()
                })

                var alert = dialogBuilder.create()
                alert.show()
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                uploadMessage?.onReceiveValue(null)
                uploadMessage = null
                uploadMessage = filePathCallback

                fanMakerFileChooserParams = fileChooserParams
                selectImage(fileChooserParams)
                return true
            }
        }

        val jsInterface = FanMakerSDKWebInterface(this,
            fanMakerSDK,
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

        val queue = Volley.newRequestQueue(this)
        val url = "${FanMakerSDKHttpRequest.URL}/site_details/sdk"

        val request = object: JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                val status = response.getInt("status")
                if (status == 200) {
                    val data = response.getJSONObject("data")
                    val sdk_url = data.getString("url")
                    fanMakerSDK!!.updateBaseUrl(sdk_url)

                    fanMakerSDK!!.formatUrl { formattedUrl ->
                        webView.loadUrl(formattedUrl, fanMakerSDK!!.webViewHeaders())
                    }
                } else {
                    webView.loadUrl("https://admin.fanmaker.com/500")
                }
            },
            { error ->
                Log.w("FANMAKER", "ERROR: $error")
                webView.loadUrl("https://admin.fanmaker.com/500")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return fanMakerSDK!!.webViewHeaders()
            }
        }

        queue.add(request)
    }

    fun genericAlert(message: String) {
        val dialogBuilder = AlertDialog.Builder(this@FanMakerSDKWebView)

        // dialogBuilder.setTitle("Unable to complete your request")
        dialogBuilder.setMessage(message)
        dialogBuilder.setCancelable(false)
        dialogBuilder.setNegativeButton("Close", DialogInterface.OnClickListener { dialog, id -> dialog.cancel() })
        var alert = dialogBuilder.create()
        alert.show()
    }


    private fun openPicker(fileChooserParams: WebChromeClient.FileChooserParams?) {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_SELECT_FILE)
    }

    private fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // We are trying to open the front camera, but it may not be available
        intent.putExtra("android.intent.extras.CAMERA_FACING", android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT)  // for API < 21
        intent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1)  // for API >= 21
        intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
    }

    private fun startCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // Permission is already granted
            launchCamera()
        } else {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_IMAGE_CAPTURE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted
                launchCamera()
            } else {
                // Permission denied
                genericAlert("Please make sure the app has access to your Camera to use this feature.")
                uploadMessage?.onReceiveValue(null)
                uploadMessage = null
            }
        }
    }

    private fun closeCamera() {
        viewBinding.viewFinder.setVisibility(View.GONE)
        viewBinding.closeCameraButton.setVisibility(View.GONE)
        viewBinding.imageCaptureButton.setVisibility(View.GONE)
        viewBinding.switchCameraButton.setVisibility(View.GONE)
        fanMakerCameraProvider.unbindAll()
        uploadMessage?.onReceiveValue(null)
        uploadMessage = null
    }

    private fun flipCamera() {
        cameraId = if(cameraId == 1) 0 else 1;
        startCamera()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FanMaker")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("IMAGE CAPTURE FAILURE", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    var results: Array<Uri>? = arrayOf(output.savedUri!!)

                    uploadMessage?.onReceiveValue(results)
                    uploadMessage = null
                    closeCamera()
                }
            }
        )
    }

    private val permReqLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions.entries.all { it.value == true }
        if (granted) {
            if(camPermissionMethod == "startCamera") { startCamera() }
            else if(camPermissionMethod == "openPicker") { openPicker(fanMakerFileChooserParams) }
            camPermissionMethod = null
        } else {
            camPermissionMethod = null
            genericAlert("Please make sure the app has access to your Camera and Media Gallery to use this feature.")
            uploadMessage?.onReceiveValue(null)
            uploadMessage = null
        }
    }

    private fun askPermissions() {
//        ActivityCompat.requestPermissions(this, permission, PERMISSION_RESULTCODE)
        permReqLauncher.launch(permission)
    }

    private fun isPermissionGranted(): Boolean {
        permission.forEach {
            if(ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED)
                return false
        }
        return true
    }

    fun getImageUri(context: Context, bitmap: Bitmap): Uri {
        // Get the application's cache directory
        val filesDir = context.externalCacheDir
        val imageFile = File(filesDir, "share_image_" + System.currentTimeMillis() + ".png")

        // Write the bitmap to a file
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        val bitmapData = bos.toByteArray()

        // Save bitmap to file
        val fos = FileOutputStream(imageFile)
        fos.write(bitmapData)
        fos.flush()
        fos.close()

        // Use FileProvider to get a content URI
        return Uri.fromFile(imageFile)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_SELECT_FILE) {
            if (uploadMessage == null)
                return
            var results: Array<Uri>? = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            uploadMessage?.onReceiveValue(results)
            uploadMessage = null
        }
        else if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (data?.extras?.get("data") == null) {
                uploadMessage?.onReceiveValue(null)
                uploadMessage = null
                return
            }

            val imageBitmap = data?.extras?.get("data") as Bitmap
            val uri = getImageUri(applicationContext, imageBitmap)
            val results: Array<Uri>? = arrayOf(uri)
            uploadMessage?.onReceiveValue(results)
            uploadMessage = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
