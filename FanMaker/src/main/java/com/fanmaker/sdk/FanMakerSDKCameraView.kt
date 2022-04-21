package com.fanmaker.sdk

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.fanmaker.sdk.databinding.FanmakerSdkCameraViewBinding
import java.io.File
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

class FanMakerSDKCameraView : AppCompatActivity() {
    private val permission = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
    )
    private val MEDIA_RESULTCODE = 1
    private val PERMISSION_RESULTCODE = 2
    private val REQUEST_SELECT_FILE = 100

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var backgroundHandlerThread: HandlerThread
    private lateinit var backgroundHandler: android.os.Handler

    private lateinit var viewBinding: FanmakerSdkCameraViewBinding
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
        setContentView(R.layout.fanmaker_sdk_camera_view)

        viewBinding = FanmakerSdkCameraViewBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        viewBinding.fanmakerCameraImageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.fanmakerCameraCancelButton.setOnClickListener { closeCamera() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        startCamera()
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.w("CAMERA TRIED TO OPEN", "It probably didn't")
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            Log.w("CAMERA DISCONNECTED", "Bummer")
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

    private fun takePhoto() {
        Log.w("FANMAKER CAMERA", "Say Cheese")
    }

    private fun closeCamera() {
        finish()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also() {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch(exc: Exception) {
                Log.e("TAKE PHOTO EXCEPTION", "BINDING FAILED", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun askPermissions() {
        ActivityCompat.requestPermissions(this, permission, PERMISSION_RESULTCODE)
    }

    private fun isPermissionGranted(): Boolean {
        permission.forEach {
            if(ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED)
                return false
        }
        return true
    }
}