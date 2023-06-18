package com.zipoapps.level.utility

import com.zipoapps.level.utility.MathExtensions.normalizeAngle
import com.zipoapps.level.utility.MathExtensions.toDegrees
import kotlin.math.atan2

object CompassAzimuth {
    fun calculate(gravity: Vector3, magneticField: Vector3): Float {
        // Gravity
        val normGravity = gravity.normalize()
        val normMagField = magneticField.normalize()

        // East vector
        val east = normMagField.cross(normGravity)
        val normEast = east.normalize()

        // Magnitude check
        val eastMagnitude = east.magnitude()
        val gravityMagnitude = gravity.magnitude()
        val magneticMagnitude = magneticField.magnitude()
        if (gravityMagnitude * magneticMagnitude * eastMagnitude < 0.1f) {
            return 0F
        }

        // North vector
        val dotProduct = normGravity.dot(normMagField)
        val north = normMagField.minus(normGravity * dotProduct)
        val normNorth = north.normalize()

        // Azimuth
        val sin = normEast.y - normNorth.x
        val cos = normEast.x + normNorth.y
        val azimuth = if (!(sin == 0f && sin == cos)) atan2(sin, cos) else 0f

        if (azimuth.isNaN()) {
            return 0F
        }

        return if (azimuth.isNaN() || !azimuth.isFinite()) 0f else normalizeAngle(azimuth.toDegrees())
    }
}