package com.zipoapps.level.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.zipoapps.level.R
import com.zipoapps.level.databinding.ActivityMainBinding
import com.zipoapps.level.orientation.Orientation
import com.zipoapps.level.orientation.OrientationListener
import com.zipoapps.level.orientation.OrientationProvider
import com.zipoapps.level.setting.SettingsActivity
import com.zipoapps.level.utility.*
import com.zipoapps.level.utility.MathExtensions.smoothAndSetReadings
import com.zipoapps.permissions.PermissionRequester


class MainActivity : AppCompatActivity(), OrientationListener, SensorEventListener {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private var orientationProvider: OrientationProvider? = null

    private var isSound = true
    private var isLock = false
    private var isCalibrate = true

    private var soundPool: SoundPool? = null
    private var bipSoundID = 0
    private var bipRate = 0
    private var lastBip: Long = 0


    private lateinit var sensorManager: SensorManager
    private lateinit var sensorAccelerometer: Sensor
    private lateinit var sensorMagneticField: Sensor
    private lateinit var gravity: Sensor

    private var haveAccelerometerSensor = false
    private var haveMagnetometerSensor = false
    private var hasGravitySensor = false

    private var accelerometer = Vector3.zero
    private var magnetometer = Vector3.zero

    private val accelerometerReadings = FloatArray(3)
    private val magnetometerReadings = FloatArray(3)
    private val rotation = FloatArray(9)
    private val inclination = FloatArray(9)

    private var readingsAlpha = 0.05f
    private val degreesPerRadian = 180 / Math.PI
    private val twoTimesPi = 2.0 * Math.PI

    private var mDisplayOrientation = 0

    private val permissionRequester = PermissionRequester(this, Manifest.permission.CAMERA)
        .onGranted {
            PhUtils.showInterstitialOnNextActivity(this)
            startActivity(Intent(this@MainActivity, LevelVisualActivity::class.java))
        }
        .onRationale {
            it.showRationale(R.string.permission_title, R.string.permission_message, R.string.ok)
        }
        .onPermanentlyDenied { requester, canShowSettingsDialog ->
            if(canShowSettingsDialog){
                requester.showOpenSettingsDialog(R.string.permission_title, R.string.permission_message, R.string.go_to_settings, R.string.later)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Level_Main)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.updatePadding(top = insets.top)
            binding.layoutMain.updatePadding(bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        initToolbar()
        initView()
        initSensor()


    }

    private fun initToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun initView() {
        mDisplayOrientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: 0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

        orientationProvider = OrientationProvider(this)

        // sound
        soundPool = SoundPool.Builder().setMaxStreams(1).build().apply {
            bipSoundID = load(this@MainActivity, R.raw.bip, 1)
        }
        bipRate = resources.getInteger(R.integer.bip_rate)


        isSound = appPrefGetValue(PrefSound, true)
        binding.btnSound.icon = ContextCompat.getDrawable(applicationContext, if (isSound) R.drawable.ic_sound_on else R.drawable.ic_sound_off)
        binding.btnSound.iconTint = ColorStateList.valueOf(if (!isSound) ContextCompat.getColor(applicationContext, R.color.seed) else ContextCompat.getColor(applicationContext, R.color.black))

        binding.btnSound.setOnClickListener {
            isSound = !isSound
            binding.btnSound.icon = ContextCompat.getDrawable(applicationContext, if (isSound) R.drawable.ic_sound_on else R.drawable.ic_sound_off)
            binding.btnSound.iconTint = ColorStateList.valueOf(if (!isSound) ContextCompat.getColor(applicationContext, R.color.seed) else ContextCompat.getColor(applicationContext, R.color.black))
            appPrefPutValue(PrefSound, isSound)
        }


        isCalibrate = appPrefGetValue(PrefCustomCalibrate, false)
        binding.btnCenter.icon = ContextCompat.getDrawable(
            applicationContext,
            if (isCalibrate) R.drawable.ic_center_focus
            else R.drawable.ic_center_focus_strong
        )
        binding.btnCenter.iconTint = ColorStateList.valueOf(if (!isCalibrate) ContextCompat.getColor(applicationContext, R.color.seed) else ContextCompat.getColor(applicationContext, R.color.black))
        binding.btnCenter.setOnClickListener {
            isCalibrate = !isCalibrate
            binding.btnCenter.icon = ContextCompat.getDrawable(
                applicationContext,
                if (isCalibrate) R.drawable.ic_center_focus
                else R.drawable.ic_center_focus_strong
            )
            binding.btnCenter.iconTint = ColorStateList.valueOf(if (!isCalibrate) ContextCompat.getColor(applicationContext, R.color.seed) else ContextCompat.getColor(applicationContext, R.color.black))
            appPrefPutValue(PrefCustomCalibrate, isCalibrate)
            if (isCalibrate) {
                orientationProvider?.saveCalibration()
            } else {
                orientationProvider?.resetCalibration()
            }
        }

        binding.btnLock.icon = ContextCompat.getDrawable(applicationContext, if (isLock) R.drawable.ic_lock_on else R.drawable.ic_rotate)
        binding.levelView.setOrientationLock(isLock)
        binding.btnLock.iconTint = ColorStateList.valueOf(if (isLock) ContextCompat.getColor(applicationContext, R.color.seed) else ContextCompat.getColor(applicationContext, R.color.black))

        binding.btnLock.setOnClickListener {
            isLock = !isLock
            binding.btnLock.icon = ContextCompat.getDrawable(applicationContext, if (isLock) R.drawable.ic_lock_on else R.drawable.ic_rotate)
            binding.btnLock.iconTint = ColorStateList.valueOf(if (isLock) ContextCompat.getColor(applicationContext, R.color.seed) else ContextCompat.getColor(applicationContext, R.color.black))
            binding.levelView.setOrientationLock(isLock)
            orientationProvider?.setLocked(isLock)
        }

        binding.btnSetting.setOnClickListener {
            PhUtils.showInterstitialOnNextActivity(this)
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

        binding.btnEye.setOnClickListener {
            permissionRequester.request()
        }
    }

    override fun onOrientationChanged(orientation: Orientation, pitch: Float, roll: Float, balance: Float) {
        if (isSound
            && orientation.isLevel(pitch, roll, balance)
            && System.currentTimeMillis() - lastBip > bipRate
        ) {
            val mgr = getSystemService(AUDIO_SERVICE) as AudioManager
            val streamVolumeCurrent = mgr.getStreamVolume(AudioManager.STREAM_RING).toFloat()
            val streamVolumeMax = mgr.getStreamMaxVolume(AudioManager.STREAM_RING).toFloat()
            val volume = streamVolumeCurrent / streamVolumeMax
            lastBip = System.currentTimeMillis()
            soundPool?.play(bipSoundID, volume, volume, 1, 0, 1f)
        }
        binding.levelView.onOrientationChanged(orientation, pitch.toDouble(), roll.toDouble(), balance.toDouble())
    }

    override fun onAngleChanged(angle: Float) {
        binding.levelView.setNorthAngle(angle)
    }

    override fun onCalibrationSaved(success: Boolean) {
        Toast.makeText(this, if (success) R.string.calibrate_restored else R.string.calibrate_failed, Toast.LENGTH_SHORT).show()
    }

    override fun onCalibrationReset(success: Boolean) {
        Toast.makeText(this, if (success) R.string.calibrate_saved else R.string.calibrate_failed, Toast.LENGTH_SHORT).show()
    }


    private fun initSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        kotlin.runCatching {
            sensorMagneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            haveMagnetometerSensor = true
            haveAccelerometerSensor = true
        }.getOrElse {
            haveAccelerometerSensor = false
            haveMagnetometerSensor = false
        }

        kotlin.runCatching {
            gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            hasGravitySensor = true
        }.getOrElse {
            hasGravitySensor = false
        }

    }

    private fun registerSensor() {
        if (haveAccelerometerSensor && haveMagnetometerSensor) {
            sensorManager.registerListener(this, sensorAccelerometer, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(this, sensorMagneticField, SensorManager.SENSOR_DELAY_GAME)
        } else {
            showNotSupportedDialog()
        }
        if (hasGravitySensor) {
            sensorManager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME)
        } else {
            showNotSupportedDialog()
        }
    }

    private fun unregisterSensor() {
        if (haveAccelerometerSensor && haveMagnetometerSensor) {
            sensorManager.unregisterListener(this, sensorAccelerometer)
            sensorManager.unregisterListener(this, sensorMagneticField)
        }
        if (hasGravitySensor) {
            sensorManager.unregisterListener(this, gravity)
        }
    }

    private fun showNotSupportedDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_not_supported_title))
            .setMessage(getString(R.string.dialog_not_supported_message))
            .setNegativeButton(R.string.dialog_negative_text_button) { dialog, which -> dialog.dismiss() }
            .create()
            .show()
    }


    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->

            when (sensorEvent.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    smoothAndSetReadings(accelerometerReadings, sensorEvent.values, readingsAlpha)
                    accelerometer = Vector3(accelerometerReadings[0], accelerometerReadings[1], accelerometerReadings[2])
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    smoothAndSetReadings(magnetometerReadings, sensorEvent.values, readingsAlpha)
                    magnetometer = Vector3(magnetometerReadings[0], magnetometerReadings[1], magnetometerReadings[2])
                }
            }


            val geoNorth = appPrefGetValue(PrefGeoNorth, true)

            val angle = if (geoNorth) {
                CompassAzimuth.calculate(gravity = accelerometer, magneticField = magnetometer)
            } else {
                val successfullyCalculatedRotationMatrix = SensorManager.getRotationMatrix(rotation, inclination, accelerometerReadings, magnetometerReadings)
                if (successfullyCalculatedRotationMatrix) {
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotation, orientation)
                    ((orientation[0] + twoTimesPi) % twoTimesPi * degreesPerRadian).toFloat()
                } else {
                    0F
                }
            }
            binding.levelView.setNorthAngle(angle)

        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    override fun onBackPressed() {
        if(PhUtils.onMainActivityBackPressed(this)){
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        orientationProvider?.startListening(this)

        registerSensor()
    }

    override fun onPause() {
        super.onPause()
        orientationProvider?.let {
            if (it.isListening) {
                it.stopListening()
            }
        }
        unregisterSensor()
    }

    override fun onDestroy() {
        soundPool?.release()
        super.onDestroy()
    }
}