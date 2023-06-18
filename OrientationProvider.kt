package com.zipoapps.level.orientation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import java.util.*
import kotlin.math.*


class OrientationProvider(private val activity: AppCompatActivity) : SensorEventListener {
    private val calibratedPitch = FloatArray(5)
    private val calibratedRoll = FloatArray(5)
    private val calibratedBalance = FloatArray(5)
    private val geoMagArray = floatArrayOf(1f, 1f, 1f)
    private val rArray = FloatArray(16)
    private val outR = FloatArray(16)
    private val loc = FloatArray(3)
    private var pitch = 0f
    private var roll = 0f
    private var displayOrientation = 0
    private var sensor: Sensor? = null
    private var sensorManager: SensorManager? = null
    private var listener: OrientationListener? = null
    var isListening = false
    private var calibrating = false
    private var balance = 0f
    private var tmp = 0f
    private var oldPitch = 0f
    private var oldRoll = 0f
    private var oldBalance = 0f
    private var minStep = 360f
    private var refValues = 0f
    private var orientation: Orientation? = null
    private var locked = false

    fun stopListening() {
        isListening = false
        sensorManager?.unregisterListener(this)
    }

    private val requiredSensors: List<Int> = listOf(Sensor.TYPE_GRAVITY)

    fun startListening(orientationListener: OrientationListener?) {
        calibrating = false
        Arrays.fill(calibratedPitch, 0f)
        Arrays.fill(calibratedRoll, 0f)
        Arrays.fill(calibratedBalance, 0f)
        val prefs = activity.getPreferences(Context.MODE_PRIVATE)
        for (orientation in Orientation.values()) {
            calibratedPitch[orientation.ordinal] = prefs.getFloat(SAVED_PITCH + orientation.toString(), 0f)
            calibratedRoll[orientation.ordinal] = prefs.getFloat(SAVED_ROLL + orientation.toString(), 0f)
            calibratedBalance[orientation.ordinal] = prefs.getFloat(SAVED_BALANCE + orientation.toString(), 0f)
        }
        sensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        isListening = true
        sensorManager?.let { sensorManager ->
            for (sensorType in requiredSensors) {
                val sensors = sensorManager.getSensorList(sensorType)
                if (sensors.size > 0) {
                    sensor = sensors[0]
                    isListening = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) && isListening
                }
            }
        }

        if (isListening) {
            listener = orientationListener
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    override fun onSensorChanged(event: SensorEvent) {

        SensorManager.getRotationMatrix(rArray, null, event.values, geoMagArray)

        when (displayOrientation) {
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                rArray,
                SensorManager.AXIS_MINUS_Y,
                SensorManager.AXIS_X,
                outR
            )
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                rArray,
                SensorManager.AXIS_MINUS_X,
                SensorManager.AXIS_MINUS_Y,
                outR
            )
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                rArray,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                outR
            )
            Surface.ROTATION_0 -> SensorManager.remapCoordinateSystem(
                rArray,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Y,
                outR
            )
            else -> SensorManager.remapCoordinateSystem(
                rArray,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Y,
                outR
            )
        }
        SensorManager.getOrientation(outR, loc)

        tmp = sqrt((outR[8] * outR[8] + outR[9] * outR[9]).toDouble()).toFloat()
        tmp = if (tmp == 0f) 0f else outR[8] / tmp

        pitch = Math.toDegrees(loc[1].toDouble()).toFloat()
        roll = -Math.toDegrees(loc[2].toDouble()).toFloat()
        balance = Math.toDegrees(asin(tmp.toDouble())).toFloat()

        if (oldRoll != roll || oldPitch != pitch || oldBalance != balance) {
            if (oldPitch != pitch) {
                minStep = min(minStep, abs(pitch - oldPitch))
            }
            if (oldRoll != roll) {
                minStep = min(minStep, abs(roll - oldRoll))
            }
            if (oldBalance != balance) {
                minStep = min(minStep, abs(balance - oldBalance))
            }
            if (refValues < MIN_VALUES) {
                refValues++
            }
        }

        if (!locked || orientation == null) {
            orientation = if (pitch < -45 && pitch > -135) {
                Orientation.TOP
            } else if (pitch <= 45 || pitch >= 135) {
                if (roll <= 45 || roll >= 135) {
                    if (roll >= -45 || roll <= -135) {
                        Orientation.LANDING
                    } else {
                        Orientation.LEFT
                    }
                } else {
                    Orientation.RIGHT
                }
            } else {
                Orientation.BOTTOM
            }
        }

        if (calibrating) {
            calibrating = false
            val editor = activity.getPreferences(Context.MODE_PRIVATE).edit()
            editor.putFloat(SAVED_PITCH + orientation.toString(), pitch)
            editor.putFloat(SAVED_ROLL + orientation.toString(), roll)
            editor.putFloat(SAVED_BALANCE + orientation.toString(), balance)
            val success = editor.commit()
            if (success) {
                orientation?.let {
                    calibratedPitch[it.ordinal] = pitch
                    calibratedRoll[it.ordinal] = roll
                    calibratedBalance[it.ordinal] = balance
                }
            }
            listener?.onCalibrationSaved(success)
            pitch = 0f
            roll = 0f
            balance = 0f
        } else {
            orientation?.let {
                pitch -= calibratedPitch[it.ordinal]
                roll -= calibratedRoll[it.ordinal]
                balance -= calibratedBalance[it.ordinal]
            }
        }

        orientation?.let {
            listener?.onOrientationChanged(it, pitch, roll, balance)
        }
    }

    fun resetCalibration() {
        var success = false
        try {
            success = activity.getPreferences(Context.MODE_PRIVATE).edit().clear().commit()
        } catch (e: Exception) {
        }
        if (success) {
            Arrays.fill(calibratedPitch, 0f)
            Arrays.fill(calibratedRoll, 0f)
            Arrays.fill(calibratedBalance, 0f)
        }
        listener?.onCalibrationReset(success)
    }

    fun saveCalibration() {
        calibrating = true
    }

    fun setLocked(locked: Boolean) {
        this.locked = locked
    }

    companion object {
        private const val MIN_VALUES = 20
        private const val SAVED_PITCH = "pitch."
        private const val SAVED_ROLL = "roll."
        private const val SAVED_BALANCE = "balance."
    }

    init {
        displayOrientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.rotation ?: 0
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.rotation
        }
    }
}