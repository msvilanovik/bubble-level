package com.zipoapps.level.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.preference.PreferenceManager
import com.zipoapps.level.R
import com.zipoapps.level.orientation.Orientation
import com.zipoapps.level.setting.PrefCoordinateSystem
import com.zipoapps.level.setting.PrefInclinationAngle
import com.zipoapps.level.setting.PrefRotationAngle
import com.zipoapps.level.utility.PrefShowLabels
import com.zipoapps.level.utility.PrefShowNorth

class LevelView(context: Context?, attrs: AttributeSet?) : SurfaceView(context, attrs),
    SurfaceHolder.Callback {


    private var levelDraw: LevelPainter? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        levelDraw?.pause(!hasWindowFocus)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val displayType = when (prefs.getString(PrefInclinationAngle, context.getString(R.string.default_inclination_angle))) {
            context.getString(R.string.degrees) -> DisplayType.ANGLE
            context.getString(R.string.inclination) -> DisplayType.INCLINATION
            context.getString(R.string.roof_pitch) -> DisplayType.ROOF_PITCH
            else -> DisplayType.ANGLE
        }
        levelDraw = LevelPainter(
            holder, context, Handler(Looper.getMainLooper()), displayType,
            prefs.getString(PrefRotationAngle, context.getString(R.string.default_rotation_angle)) ?: context.getString(R.string.default_rotation_angle),
            prefs.getBoolean(PrefShowLabels, true),
            prefs.getBoolean(PrefShowNorth, true),
            prefs.getString(PrefCoordinateSystem, context.getString(R.string.coordinate_cartesian)) ?: context.getString(R.string.coordinate_cartesian)
        )
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        levelDraw?.setSurfaceSize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        levelDraw?.let {
            it.pause(true)
            it.clean()
            levelDraw = null
        }
        System.gc()
    }

    fun onOrientationChanged(orientation: Orientation, pitch: Double, roll: Double, balance: Double) {
        levelDraw?.onOrientationChanged(orientation, pitch.toFloat(), roll.toFloat(), balance.toFloat())
    }

    fun setOrientationLock(isLock: Boolean) {
        levelDraw?.setOrientationLocked(isLock)
    }

    fun setNorthAngle(angle: Float) {
        levelDraw?.setNorthAngle(angle)
    }
}