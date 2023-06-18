package com.zipoapps.level.utility

import kotlin.math.*

object MathExtensions {
    fun normalizeAngle(angle: Float): Float {
        return wrap(angle, 0f, 360f) % 360
    }

    private fun wrap(value: Float, min: Float, max: Float): Float {
        return wrap(value.toDouble(), min.toDouble(), max.toDouble()).toFloat()
    }

    private fun wrap(value: Double, min: Double, max: Double): Double {
        val range = max - min

        var newValue = value

        while (newValue > max) {
            newValue -= range
        }

        while (newValue < min) {
            newValue += range
        }

        return newValue
    }

    fun Float.toRadians(): Float {
        return Math.toRadians(this.toDouble()).toFloat()
    }

    fun Float.toMilliradian(): Float {
        return this * (1000 * (PI / 180)).toFloat()
    }

    fun Float.toGradians(): Float {
        return this * (200 / 180).toFloat()
    }

    fun Float.toDegrees(): Float {
        return Math.toDegrees(this.toDouble()).toFloat()
    }

    fun smoothAndSetReadings(readings: FloatArray, newReadings: FloatArray, readingsAlpha: Float) {
        readings[0] = readingsAlpha * newReadings[0] + (1 - readingsAlpha) * readings[0] // x
        readings[1] = readingsAlpha * newReadings[1] + (1 - readingsAlpha) * readings[1] // y
        readings[2] = readingsAlpha * newReadings[2] + (1 - readingsAlpha) * readings[2] // z
    }

}