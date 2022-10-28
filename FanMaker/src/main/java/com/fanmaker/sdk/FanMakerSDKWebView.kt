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
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.fanmaker.sdk.databinding.FanmakerSdkWebviewBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class FanMakerSDKWebView : AppCompatActivity() {
    private val permission = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
    )
    private val MEDIA_RESULTCODE = 1
    private val PERMISSION_RESULTCODE = 2
    private val REQUEST_SELECT_FILE = 100
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fanmaker_sdk_webview)

        viewBinding = FanmakerSdkWebviewBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.closeCameraButton.setOnClickListener { closeCamera() }
        viewBinding.switchCameraButton.setOnClickListener { flipCamera() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        val webView = findViewById<WebView>(R.id.fanmaker_sdk_webview)
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

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
        headers.put("X-FanMaker-SDK-Version", "1.4")
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

    fun genericAlert(message: String) {
        val dialogBuilder = AlertDialog.Builder(this@FanMakerSDKWebView)

//        dialogBuilder.setTitle("Unable to complete your request")
        dialogBuilder.setMessage(message)
        dialogBuilder.setCancelable(false)
        dialogBuilder.setNegativeButton("Close", DialogInterface.OnClickListener { dialog, id -> dialog.cancel() })
        var alert = dialogBuilder.create()
        alert.show()
    }

    fun openPicker(fileChooserParams: WebChromeClient.FileChooserParams?) {
        val hasFilePermissions = (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)

        if(hasFilePermissions) {
            val intent = fileChooserParams!!.createIntent()
            try {
                startActivityForResult(intent, REQUEST_SELECT_FILE)
            } catch (e: ActivityNotFoundException) {
                uploadMessage = null
                Toast.makeText(applicationContext, "Cannot Open File Picker", Toast.LENGTH_LONG).show()
            }
        } else {
            camPermissionMethod = "openPicker"
            askPermissions()
        }
    }

    private fun startCamera() {
        val hasCamPermissions = (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)

        if(hasCamPermissions) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
            cameraProviderFuture.addListener({
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                fanMakerCameraProvider = cameraProvider

                var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                if(cameraId != 1) { cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA }

                val preview = Preview.Builder()
                    .build()
                    .also() {
                        viewBinding.viewFinder.setVisibility(View.VISIBLE)
                        viewBinding.closeCameraButton.setVisibility(View.VISIBLE)
                        viewBinding.imageCaptureButton.setVisibility(View.VISIBLE)
                        viewBinding.switchCameraButton.setVisibility(View.VISIBLE)
                        it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                    }

                imageCapture = ImageCapture.Builder().build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                } catch(exc: Exception) {
                    Log.e("START CAMERA EXCEPTION", "BINDING FAILED", exc)
                }
            }, ContextCompat.getMainExecutor(this))
        } else {
            camPermissionMethod = "startCamera"
            askPermissions()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_SELECT_FILE) {
            if (uploadMessage == null)
                return
            var results: Array<Uri>? = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            uploadMessage?.onReceiveValue(results)
            uploadMessage = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}