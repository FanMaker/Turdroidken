package com.fanmaker.sdk

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.webkit.WebView
import android.webkit.WebViewClient
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

import android.hardware.camera2.CameraDevice
import android.os.Build
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.*
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import java.util.*
import com.fanmaker.sdk.databinding.FanmakerSdkWebviewFragmentBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import android.widget.ImageView
import android.graphics.drawable.AnimationDrawable
import android.widget.FrameLayout

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

import com.fanmaker.sdk.FanMakerSDKs

class FanMakerSDKWebViewFragment : Fragment() {
    // Activity Result Launchers for file chooser and image picker
    private lateinit var fileChooserLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var imagePickerLauncher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest>
    private lateinit var fanMakerSDK: FanMakerSDK
    private lateinit var fanMakerSharedPreferences: FanMakerSharedPreferences
    lateinit var animationDrawable: AnimationDrawable
    lateinit var loadingAnimationFrame: FrameLayout
    lateinit var loadingAnimationView: ImageView

    private var _viewBinding: FanmakerSdkWebviewFragmentBinding? = null
    private val viewBinding get() = _viewBinding!!

    private val permission = arrayOf(
        Manifest.permission.CAMERA
    )
    private val MEDIA_RESULTCODE = 1
    private val PERMISSION_RESULTCODE = 2
    private val REQUEST_SELECT_FILE = 100
    private val REQUEST_IMAGE_CAPTURE = 1
    private val FILENAME_FORMAT = "yyyyMMdd-HHmmssSSS"
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    private var mUploadMessage: ValueCallback<Uri>? = null
    private var uploadMessage: ValueCallback<Array<Uri>>? = null

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private lateinit var backgroundHandlerThread: HandlerThread
    private lateinit var backgroundHandler: android.os.Handler

    private var fanMakerCameraProvider: ProcessCameraProvider? = null

    private var camPermissionMethod: String? = null
    private var fanMakerFileChooserParams: WebChromeClient.FileChooserParams? = null

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Register ActivityResultLauncher for file chooser
        fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val results: Array<Uri>? = when {
                    data != null && data.clipData != null -> Array(data.clipData!!.itemCount) { i -> data.clipData!!.getItemAt(i).uri }
                    data != null && data.data != null -> arrayOf(data.data!!)
                    else -> null
                }
                uploadMessage?.onReceiveValue(results)
            } else {
                uploadMessage?.onReceiveValue(null)
            }
            uploadMessage = null
        }

        // Register ActivityResultLauncher for image picker (gallery)
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val results: Array<Uri>? = if (uri != null) arrayOf(uri) else null
            uploadMessage?.onReceiveValue(results)
            uploadMessage = null
        }
        _viewBinding = FanmakerSdkWebviewFragmentBinding.inflate(inflater, container, false)

        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.closeCameraButton.setOnClickListener { closeCamera() }
        viewBinding.switchCameraButton.setOnClickListener { flipCamera() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        val webView = viewBinding.root.findViewById<WebView>(R.id.fanmaker_sdk_webview)
        
        // Enable WebView debugging for Chrome DevTools
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        //     WebView.setWebContentsDebuggingEnabled(true)
        // }
        
        webView.webChromeClient = WebChromeClient()
        val intent = Intent(Intent.ACTION_GET_CONTENT)

        val fanMakerKey = activity?.intent?.getStringExtra("fanMakerKey")
        if (fanMakerKey == null) {
            Log.e("FanMakerSDKWebViewFragment", "fanMakerKey is null. Cannot initialize FanMakerSDK.")
            return viewBinding.root
        }

        val fanMakerSDK = FanMakerSDKs.getInstance(fanMakerKey)
        if (fanMakerSDK == null) {
            Log.e("FanMakerSDKWebViewFragment", "Failed to get instance of FanMakerSDK.")
            return viewBinding.root
        }

        if(fanMakerSDK!!.useDarkLoadingScreen) {
            loadingAnimationFrame = viewBinding.root.findViewById<FrameLayout>(R.id.fanmaker_sdk_dark_loading_frame)
            loadingAnimationView = viewBinding.root.findViewById<ImageView>(R.id.darkLoadingGif)
        } else {
            loadingAnimationFrame = viewBinding.root.findViewById<FrameLayout>(R.id.fanmaker_sdk_light_loading_frame)
            loadingAnimationView = viewBinding.root.findViewById<ImageView>(R.id.lightLoadingGif)
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
        }

        fanMakerSharedPreferences = FanMakerSharedPreferences(requireContext(), fanMakerSDK!!.apiKey)

        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

        // if(!isPermissionGranted()) { askPermissions() }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request?.resources)
            }

            private fun openFileChooser(uploadMsg: ValueCallback<Uri>) {
                mUploadMessage = uploadMsg
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                fileChooserLauncher.launch(Intent.createChooser(intent, "File Chooser"))
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
                val dialogBuilder = AlertDialog.Builder(context)
                dialogBuilder.setTitle("Choose Source")
                dialogBuilder.setCancelable(true)
                dialogBuilder.setItems(arrayOf("Camera", "Gallery")) { dialog, which ->
                    when (which) {
                        0 -> {
                            // Camera selected, check permission first
                            camPermissionMethod = "startCamera"
                            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                startCamera()
                            } else {
                                askPermissions()
                            }
                        }
                        1 -> {
                            // Gallery selected, no permission required for PickVisualMedia
                            camPermissionMethod = null
                            openPicker()
                        }
                    }
                    dialog.dismiss()
                }
                val alert = dialogBuilder.create()
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

        val jsInterface = FanMakerSDKWebInterface(requireActivity(),
            fanMakerSDK,
            { authorized ->
                var jsString: String = "FanMakerReceiveLocationAuthorization("
                if (authorized) jsString = "${jsString}true)"
                else jsString = "${jsString}false)"
                Log.w("FANMAKER", jsString)

                getActivity()?.runOnUiThread {
                    webView.evaluateJavascript(jsString, null)
                }
            },
            { location ->
                val jsString: String =
                    "FanMakerReceiveLocation({ lat: ${location.latitude}, lng: ${location.longitude} })"
                Log.w("FANMAKER", jsString)

                getActivity()?.runOnUiThread {
                    webView.evaluateJavascript(jsString, null)
                }
            }
        )

        webView.addJavascriptInterface(jsInterface, "fanmaker")

        val queue = Volley.newRequestQueue(requireActivity())
        val url = "${FanMakerSDKHttpRequest.URL}/site_details/sdk"

        val settings = fanMakerSharedPreferences.getSharedPreferences()

        val request = object: JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                val status = response.getInt("status")
                if (status == 200) {
                    val data = response.getJSONObject("data")
                    val sdk_url = data.getString("url")
                    fanMakerSDK!!.updateBaseUrl(sdk_url)
                    fanMakerSDK!!.formatUrl { formattedUrl ->
                        Log.w("FanMakerSDK", formattedUrl)
                        webView.loadUrl(formattedUrl, fanMakerSDK!!.webViewHeaders())
                    }
                } else {
                    webView.loadUrl("https://admin.fanmaker.com/500")
                }
            },
            { error ->
                webView.loadUrl("https://admin.fanmaker.com/500")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return fanMakerSDK!!.webViewHeaders()
            }
        }
        queue.add(request)

        // Set up window insets handling for the fragment
        setupWindowInsets()

        return viewBinding.root
    }

    fun genericAlert(message: String) {
        val dialogBuilder = AlertDialog.Builder(context)

        dialogBuilder.setMessage(message)
        dialogBuilder.setCancelable(false)
        dialogBuilder.setNegativeButton("Close", DialogInterface.OnClickListener { dialog, id -> dialog.cancel() })
        var alert = dialogBuilder.create()
        alert.show()
    }

    private fun openPicker() {
        imagePickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun launchCamera() {
        // Show overlay
        viewBinding.viewFinder.visibility = View.VISIBLE
        viewBinding.imageCaptureButton.visibility = View.VISIBLE
        viewBinding.closeCameraButton.visibility = View.VISIBLE
        viewBinding.switchCameraButton.visibility = View.VISIBLE

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            fanMakerCameraProvider = cameraProvider
            val preview = Preview.Builder().build()
            val imageCapture = ImageCapture.Builder().build()
            this.imageCapture = imageCapture
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            preview.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            cameraProvider.unbindAll()
            try {
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                Log.d("CameraX", "Camera bound, lensFacing: $lensFacing")
            } catch (e: Exception) {
                Log.e("CameraX", "Failed to bind camera: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startCamera() {
        // Only request camera permission when user selects Camera
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            // Use ActivityResult API for permissions for consistency and privacy
            camPermissionMethod = "startCamera"
            permReqLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    // Remove legacy onRequestPermissionsResult for camera, use permReqLauncher instead
    private fun bindCameraUseCases() {
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val rotation = resources.configuration.orientation

        val height: Int
        val width: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentMetrics = requireContext().getSystemService(WindowManager::class.java).currentWindowMetrics
            height = currentMetrics.bounds.height()
            width = currentMetrics.bounds.width()
        } else {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requireActivity().display
            } else {
                @Suppress("DEPRECATION")
                requireActivity().windowManager.defaultDisplay
            }
            val size = android.graphics.Point()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics = requireContext().getSystemService(WindowManager::class.java).currentWindowMetrics
                val bounds = windowMetrics.bounds
                height = bounds.height()
                width = bounds.width()
            } else {
                @Suppress("DEPRECATION")
                display?.getSize(size)
                height = size.y
                width = size.x
            }
        }

        val screenAspectRatio = aspectRatio(width, height)

        val preview = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .build()

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .build()

        try {
            fanMakerCameraProvider?.unbindAll()

            viewBinding.viewFinder.visibility = View.VISIBLE
            viewBinding.closeCameraButton.visibility = View.VISIBLE
            viewBinding.imageCaptureButton.visibility = View.VISIBLE
            viewBinding.switchCameraButton.visibility = View.VISIBLE

            fanMakerCameraProvider?.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview, imageCapture)
            preview.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
        } catch(exc: Exception) {
            Log.e("START CAMERA EXCEPTION", "BINDING FAILED", exc)
        }
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val RATIO_4_3_VALUE = 4.0 / 3.0
        val RATIO_16_9_VALUE = 16.0 / 9.0

        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun closeCamera() {
    viewBinding.viewFinder.setVisibility(View.GONE)
    viewBinding.closeCameraButton.setVisibility(View.GONE)
    viewBinding.imageCaptureButton.setVisibility(View.GONE)
    viewBinding.switchCameraButton.setVisibility(View.GONE)
    fanMakerCameraProvider?.unbindAll()
    uploadMessage?.onReceiveValue(null)
    uploadMessage = null
    }

    private fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        Log.d("CameraX", "flipCamera called, new lensFacing: $lensFacing")
        launchCamera()
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
            .Builder(requireActivity().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
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
            else if(camPermissionMethod == "openPicker") { openPicker() }
            camPermissionMethod = null
        } else {
            camPermissionMethod = null
            genericAlert("Please make sure the app has access to your Camera and Media Gallery to use this feature.")
            uploadMessage?.onReceiveValue(null)
            uploadMessage = null
        }
    }

    private fun askPermissions() {
        permReqLauncher.launch(permission)
    }

    private fun isPermissionGranted(): Boolean {
        permission.forEach {
            if(ActivityCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED)
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

    // Remove deprecated Camera onActivityResult logic
    // CameraX handles image capture via takePhoto()

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
