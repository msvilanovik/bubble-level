package com.zipoapps.level.orientation

interface OrientationListener {
    fun onOrientationChanged(orientation: Orientation, pitch: Float, roll: Float, balance: Float)
    fun onAngleChanged(angle: Float)
    fun onCalibrationSaved(success: Boolean)
    fun onCalibrationReset(success: Boolean)
}