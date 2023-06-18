package com.zipoapps.level.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Surface.*
import android.view.animation.Animation
import android.view.animation.Transformation
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.zipoapps.level.databinding.ActivityLevelVisualBinding
import com.zipoapps.level.utility.PhUtils
import java.text.DecimalFormat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min


class LevelVisualActivity : AppCompatActivity(), SensorEventListener {

    private val binding: ActivityLevelVisualBinding by lazy {
        ActivityLevelVisualBinding.inflate(layoutInflater)
    }

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(16)
    private val magnetometerReading = floatArrayOf(1.0f, 1.0f, 1.0f)
    private val outR = FloatArray(16)
    private val loc = FloatArray(3)
    private var rollX = 0.0
    private var pitchY = 0.0
    private var lastRollX = 0f
    private var lastPitchY = 0f
    private var orientation: Int = 0
    private var lastTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        startCamera()
        cameraExecutor = Executors.newSingleThreadExecutor()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        binding.ivBack.setOnClickListener { onBackPressed() }

        orientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: 0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({

            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (ex: Exception) {
                Log.d("_TAG_", "startCamera-48: Use case binding failed $ex")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private val requiredSensors: List<Int> = listOf(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER)

    private fun getSensorList(): Int? {
        for (intValue in requiredSensors) {
            if (sensorManager.getSensorList(intValue).size > 0) {
                return Integer.valueOf(intValue)
            }
        }

        return null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { e ->
            if (e.sensor.type == Sensor.TYPE_ACCELEROMETER || e.sensor.type == Sensor.TYPE_GRAVITY)
                SensorManager.getRotationMatrix(accelerometerReading, null, e.values, magnetometerReading)
            else
                SensorManager.getRotationMatrixFromVector(accelerometerReading, e.values)

            calculateRotation()
        }
    }


    private fun calculateRotation() {
        val currentTimeMillis = System.currentTimeMillis()
        if (currentTimeMillis - lastTime >= 100) {
            lastTime = currentTimeMillis
            when (orientation) {
                ROTATION_0 -> SensorManager.remapCoordinateSystem(accelerometerReading, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR)
                ROTATION_90 -> SensorManager.remapCoordinateSystem(accelerometerReading, SensorManager.AXIS_Z, SensorManager.AXIS_MINUS_X, outR)
                ROTATION_180 -> SensorManager.remapCoordinateSystem(accelerometerReading, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Z, outR)
                ROTATION_270 -> SensorManager.remapCoordinateSystem(accelerometerReading, SensorManager.AXIS_MINUS_Z, SensorManager.AXIS_X, outR)
            }
            SensorManager.getOrientation(outR, loc)

            pitchY = Math.toDegrees(loc[1].toDouble())
            rollX = Math.toDegrees(loc[2].toDouble())

            binding.txtAngle.text = DecimalFormat("000.0").format(abs(rollX))

        }
    }


    inner class PitchViewAnimation : Animation() {

        private var pWith = 0
        private var pHeight = 0
        private var halfWith = 0f
        private var halfHeight = 0f
        private var lastTime: Long = 0

        init {
            fillAfter = true
        }

        override fun applyTransformation(interpolatedTime: Float, trans: Transformation?) {
            super.applyTransformation(interpolatedTime, trans)
            val currentTimeMillis = System.currentTimeMillis()
            if (lastTime > 0) {
                val t = pitchY - lastPitchY
                lastPitchY = (lastPitchY + (t + 6f * min(1f, (currentTimeMillis - lastTime) / 1000f))).toFloat()
            }
            lastTime = currentTimeMillis
            trans?.alpha = 1f - (lastPitchY / 90f)
            trans?.matrix?.preTranslate(0f, (halfWith * lastPitchY / 90f))
            trans?.matrix?.postRotate(-lastRollX, halfWith, halfHeight)

        }

        override fun initialize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
            super.initialize(width, height, parentWidth, parentHeight)
            pWith = parentWidth
            pHeight = parentHeight
            halfWith = width.toFloat() / 2f
            halfHeight = height.toFloat() / 2f
        }
    }

    inner class RollViewAnimation : Animation() {

        private var mWidth = 0f
        private var mHeight = 0f
        private var lastTime: Long = 0

        init {
            fillAfter = true
        }

        override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
            super.applyTransformation(interpolatedTime, t)
            val currentTimeMillis = System.currentTimeMillis()
            if (lastTime > 0) {
                val u = rollX - lastRollX
                lastRollX = ((lastRollX + (((u % 360) * 6.0f) * min(1.0f, (currentTimeMillis - lastTime) / 1000.0f))) % 360).toFloat()
            }
            lastTime = currentTimeMillis
            t?.matrix?.setRotate(90 - lastRollX, mWidth, mHeight)
        }

        override fun initialize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
            super.initialize(width, height, parentWidth, parentHeight)
            mWidth = resolveSize(RELATIVE_TO_SELF, 0.5f, width, parentWidth)
            mHeight = resolveSize(RELATIVE_TO_SELF, 0.5f, height, parentHeight)
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()

        getSensorList()?.let {
            val sList = sensorManager.getSensorList(it)
            if (sList.size > 0) {
                val sensor: Sensor = sList[0]
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        val rollAnimation = RollViewAnimation()
        rollAnimation.duration = 1000
        rollAnimation.repeatCount = -1
        binding.ivLevelLine.startAnimation(rollAnimation)

        val pitchViewAnimation = PitchViewAnimation()
        pitchViewAnimation.duration = 1000
        pitchViewAnimation.repeatCount = -1
        binding.ivLevelCircle.startAnimation(pitchViewAnimation)
    }

    override fun onPause() {
        super.onPause()
        binding.ivLevelLine.clearAnimation()
        binding.ivLevelCircle.clearAnimation()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onBackPressed() {
        PhUtils.showInterstitialOnNextActivity(this)
        finish()
    }

}