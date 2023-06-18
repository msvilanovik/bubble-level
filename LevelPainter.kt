package com.zipoapps.level.view

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.zipoapps.level.R
import com.zipoapps.level.orientation.Orientation
import com.zipoapps.level.setting.PrefSensitivity
import com.zipoapps.level.utility.MathExtensions.normalizeAngle
import com.zipoapps.level.utility.MathExtensions.toGradians
import com.zipoapps.level.utility.MathExtensions.toMilliradian
import com.zipoapps.level.utility.MathExtensions.toRadians
import com.zipoapps.level.utility.appPrefGetValue
import java.text.DecimalFormat
import kotlin.math.*

class LevelPainter(
    private val surfaceHolder: SurfaceHolder, private val context: Context, private val handler: Handler,
    private val displayType: DisplayType, private val angleType: String, private val showLabel: Boolean,
    private val showNorth: Boolean, private val coordinateSystem: String
) : Runnable {
    private var initialized: Boolean
    private var wait: Boolean
    private var height = 0
    private var width = 0
    private var canvasWidth = 0
    private var canvasHeight = 0
    private var minLevelX = 0
    private var maxLevelX = 0
    private var levelMinusBubbleWidth = 0
    private var levelMinusBubbleHeight = 0
    private var middleX = 0
    private var middleY = 0
    private var bubbleWidth = 0
    private var bubbleHeight = 0
    private var halfBubbleWidth = 0
    private var halfBubbleHeight = 0
    private var halfMarkerGap = 0
    private var minLevelY = 0
    private var maxLevelY = 0
    private var minBubble = 0
    private var maxBubble = 0
    private val markerThickness: Int
    private val levelBorderWidth: Int
    private val levelBorderHeight: Int
    private val infoHeight: Int
    private val lcdWidth: Int
    private val lcdHeight: Int
    private val arrowHeight: Int

    private val displayPadding: Int
    private val displayGap: Int
    private var infoY = 0
    private var sensorY = 0
    private val sensorGap: Int

    private var levelMaxDimension = 0

    private var displayRect: Rect = Rect()

    private var angle1 = 0f
    private var angle2 = 0f

    private var orientation: Orientation

    private var lastTime: Long = 0
    private var northAngle = 0f
    private var polarAngle = 0f
    private var angleX = 0.0
    private var angleY = 0.0
    private var x = 0.0
    private var y = 0.0
    private var pitch = 0f
    private var roll = 0f

    private var level1D: Drawable?
    private var bubble1D: Drawable?
    private var marker1D: Drawable?
    private var level2D: Drawable?
    private var bubble2D: Drawable?
    private var marker2D: Drawable?
    private var display: Drawable?
    private var angle1D: Drawable?
    private var angle2D: Drawable?
    private var sideLeft: Drawable?
    private var sideRight: Drawable?

    private var viscosityValue = 0.0
    private var sens = 0.0

    private var displayFormat: DecimalFormat
    private var displayBackgroundText = ""
    private val lcdForegroundPaint: Paint

    private val infoPaint: Paint
    private val blackPaint: Paint
    private val backgroundColor: Int

    private var orientationLocked: Boolean
    private val frameRate: Long = (1000 / context.resources.getInteger(R.integer.frame_rate)).toLong()
    fun clean() {
        synchronized(surfaceHolder) {
            level1D = null
            level2D = null
            bubble1D = null
            bubble2D = null
            marker1D = null
            marker2D = null
            display = null
            angle1D = null
            angle2D = null
            sideLeft = null
            sideRight = null
        }
    }

    override fun run() {
        var c: Canvas? = null
        updatePhysics()
        try {
            c = surfaceHolder.lockCanvas(null)
            c?.let {
                synchronized(surfaceHolder) { doDraw(it) }
            }
        } finally {
            c?.let {
                surfaceHolder.unlockCanvasAndPost(it)
            }
        }
        handler.removeCallbacks(this)
        if (!wait) {
            handler.postDelayed(this, frameRate - System.currentTimeMillis() + lastTime)
        }
    }

    fun pause(paused: Boolean) {
        wait = !initialized || paused
        if (!wait) {
            handler.postDelayed(this, frameRate)
        }
    }

    fun setSurfaceSize(width: Int, height: Int) {
        canvasWidth = width
        canvasHeight = height
        levelMaxDimension = min(
            min(height, width) - 2 * displayGap,
            max(height, width) - 2 * (sensorGap + 2 * infoHeight + 3 * displayGap + lcdHeight)
        )
        setOrientation(orientation)
    }

    private fun updatePhysics() {
        val currentTime = System.currentTimeMillis()
        if (lastTime >= 0) {
            val timeDiff = (currentTime - lastTime) / 100.0
            val posX = orientation.reverse * (2f * x - minLevelX - maxLevelX) / levelMinusBubbleWidth
            val speedX: Double
            val speedY: Double
            when (orientation) {
                Orientation.TOP, Orientation.BOTTOM -> speedX = orientation.reverse * (angleX - posX) * viscosityValue
                Orientation.LEFT, Orientation.RIGHT -> speedX = orientation.reverse * (angleY - posX) * viscosityValue
                Orientation.LANDING -> {
                    val posY = (2f * y - minLevelY - maxLevelY) / levelMinusBubbleHeight
                    speedX = (angleX - posX) * viscosityValue
                    speedY = (angleY - posY) * viscosityValue
                    y += speedY * timeDiff
                }
            }
            x += speedX * timeDiff
            if (orientation == Orientation.LANDING) {
                if (sqrt((middleX - x) * (middleX - x) + (middleY - y) * (middleY - y)) > levelMaxDimension / 2.0f - halfBubbleWidth) {
                    x = (angleX * levelMinusBubbleWidth + minLevelX + maxLevelX) / 2f
                    y = (angleY * levelMinusBubbleHeight + minLevelY + maxLevelY) / 2f
                }
            } else {
                if (x < minLevelX + halfBubbleWidth || x > maxLevelX - halfBubbleWidth) {
                    x = (angleX * levelMinusBubbleWidth + minLevelX + maxLevelX) / 2f
                }
            }
            polarAngle = 180f - Math.toDegrees(atan2( angleX * viscosityValue * timeDiff, angleY * viscosityValue * timeDiff)).toFloat()
        }
        lastTime = currentTime
    }

    private fun doDraw(canvas: Canvas) {
        canvas.save()
        canvas.drawColor(backgroundColor)
        drawCoordinate(canvas)
        drawBubbleMarker(canvas)
        canvas.restore()
        drawNorth(canvas)
    }

    private fun drawCoordinate(canvas: Canvas) {
        if (orientation == Orientation.LANDING) {
            display?.setBounds(
                displayRect.left - (displayRect.width() + displayGap) / 2,
                displayRect.top,
                displayRect.right - (displayRect.width() + displayGap) / 2,
                displayRect.bottom
            )
            display?.draw(canvas)
            display?.setBounds(
                displayRect.left + (displayRect.width() + displayGap) / 2,
                displayRect.top,
                displayRect.right + (displayRect.width() + displayGap) / 2,
                displayRect.bottom
            )
            display?.draw(canvas)


            if (coordinateSystem == context.getString(R.string.coordinate_cartesian)) {
                canvas.drawText(
                    displayFormat.format(angle2.toDouble()),
                    middleX - (displayRect.width() + displayGap) / 2.0f,
                    displayRect.centerY() + lcdHeight / 2.0f,
                    lcdForegroundPaint
                )
            } else {
                canvas.drawText(
                    displayFormat.format(polarAngle),
                    middleX - (displayRect.width() + displayGap) / 2.0f,
                    displayRect.centerY() + lcdHeight / 2.0f,
                    lcdForegroundPaint
                )
            }
            angle1D?.setBounds(
                (middleX - displayRect.width() - displayGap - arrowHeight),
                (displayRect.centerY() - arrowHeight / 2f).toInt(),
                (middleX - displayRect.width() - displayGap * 2f + arrowHeight).toInt(),
                (displayRect.centerY() + arrowHeight / 2f).toInt()
            )
            angle1D?.draw(canvas)

            if (coordinateSystem == context.getString(R.string.coordinate_cartesian)) {
                canvas.drawText(
                    displayFormat.format(angle1.toDouble()),
                    middleX + (displayRect.width() + displayGap) / 2.0f,
                    displayRect.centerY() + lcdHeight / 2.0f,
                    lcdForegroundPaint
                )
            } else {
                canvas.drawText(
                    displayFormat.format(angle2 + angle1),
                    middleX + (displayRect.width() + displayGap) / 2.0f,
                    displayRect.centerY() + lcdHeight / 2.0f,
                    lcdForegroundPaint
                )
            }
            angle2D?.setBounds(
                (middleX + displayRect.width() + displayGap * 2f - arrowHeight).toInt(), (displayRect.centerY() - arrowHeight / 2f).toInt(),
                (middleX + displayRect.width() + displayGap + arrowHeight), (displayRect.centerY() + arrowHeight / 2f).toInt()
            )
            angle2D?.draw(canvas)
        } else {
            canvas.rotate(orientation.rotation.toFloat(), middleX.toFloat(), middleY.toFloat())
            display?.bounds = displayRect
            display?.draw(canvas)

            canvas.drawText(
                displayFormat.format(angle1.toDouble()),
                middleX.toFloat(),
                displayRect.centerY() + lcdHeight / 2.0f,
                lcdForegroundPaint
            )

            sideLeft?.setBounds(
                (middleX - displayRect.width() / 2f - displayGap * 2f).toInt(),
                (displayRect.centerY() - arrowHeight / 2f).toInt(),
                (middleX - displayRect.width() / 2f - displayGap).toInt(),
                (displayRect.centerY() + arrowHeight / 2f).toInt()
            )
            sideLeft?.draw(canvas)

            sideRight?.setBounds(
                (middleX + displayRect.width() / 2f + displayGap).toInt(),
                (displayRect.centerY() - arrowHeight / 2f).toInt(),
                (middleX + displayRect.width() / 2f + displayGap * 2f).toInt(),
                (displayRect.centerY() + arrowHeight / 2f).toInt()
            )
            sideRight?.draw(canvas)
        }
    }

    private fun drawBubbleMarker(canvas: Canvas) {
        if (orientation == Orientation.LANDING) {
            level2D?.draw(canvas)
            bubble2D?.setBounds((x - halfBubbleWidth).toInt(), (y - halfBubbleHeight).toInt(), (x + halfBubbleWidth).toInt(), (y + halfBubbleHeight).toInt())
            bubble2D?.draw(canvas)
            marker2D?.draw(canvas)
            canvas.drawLine(
                middleX.toFloat(), minLevelY.toFloat(),
                middleX.toFloat(), minLevelY + 60f, blackPaint
            )

            if ((angle1 < -0.5f || angle1 > 0.5f) || (angle2 < -0.5f || angle2 > 0.5f)) {
                if (showLabel) {
                    canvas.save()
                    canvas.rotate(polarAngle + 90, middleX.toFloat(), middleY.toFloat())
                    canvas.drawLine(minLevelX.toFloat(), middleY.toFloat(), (middleX - halfMarkerGap).toFloat(), middleY.toFloat(), infoPaint)
                    canvas.restore()

                    canvas.save()
                    canvas.rotate(polarAngle, middleX.toFloat(), middleY.toFloat())
                    canvas.drawText(String.format("%1$.1f°", angleNormalize(polarAngle)), middleX.toFloat(), (minLevelY - 10).toFloat(), infoPaint)
                    canvas.restore()
                }
            }

        } else {
            level1D?.draw(canvas)
            canvas.clipRect(
                minLevelX + levelBorderWidth,
                minLevelY + levelBorderHeight,
                maxLevelX - levelBorderWidth,
                maxLevelY - levelBorderHeight
            )
            bubble1D?.setBounds(
                (x - halfBubbleWidth).toInt(),
                minBubble, (x + halfBubbleWidth).toInt(),
                maxBubble
            )
            bubble1D?.draw(canvas)
            marker1D?.setBounds(
                middleX - halfMarkerGap - markerThickness,
                minLevelY,
                middleX - halfMarkerGap,
                maxLevelY
            )
            marker1D?.draw(canvas)
            marker1D?.setBounds(
                middleX + halfMarkerGap,
                minLevelY,
                middleX + halfMarkerGap + markerThickness,
                maxLevelY
            )
            marker1D?.draw(canvas)
        }
    }

    private fun drawNorth(canvas: Canvas) {
        if (orientation == Orientation.LANDING && showNorth) {
            canvas.save()
            canvas.rotate(normalizeAngle(northAngle).unaryMinus(), middleX.toFloat(), middleY.toFloat())
            drawTriangle(canvas, infoPaint, middleX.toFloat(), (minLevelY + TRIANGLE_SIZE).toFloat())
            if (showLabel) {
                canvas.drawText("N", middleX.toFloat(), minLevelY.toFloat() - TRIANGLE_SIZE / 4f, infoPaint)
            }
            canvas.restore()
        }
    }

    private fun drawTriangle(canvas: Canvas, paint: Paint, x: Float, y: Float) {
        val halfWidth = TRIANGLE_SIZE / 2
        val path = Path()
        path.moveTo(x, y - halfWidth)
        path.lineTo(x - halfWidth, y + halfWidth)
        path.lineTo(x + halfWidth, y + halfWidth)
        path.lineTo(x, y - halfWidth)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun angleNormalize(angle: Float): Float {
        return when (angleType) {
            context.getString(R.string.unit_angle_degrees) -> angle
            context.getString(R.string.unit_angle_gradians) -> angle.toGradians()
            context.getString(R.string.unit_angle_radians) -> angle.toRadians()
            context.getString(R.string.unit_angle_milliradians) -> angle.toMilliradian()
            else -> angle
        }
    }

    private fun setOrientation(newOrientation: Orientation) {
        if (!orientationLocked || !initialized) {
            synchronized(surfaceHolder) {
                orientation = newOrientation
                when (newOrientation) {
                    Orientation.LEFT, Orientation.RIGHT -> {
                        height = canvasWidth
                        width = canvasHeight
                        infoY = (canvasHeight - canvasWidth) / 2 + canvasWidth - infoHeight
                    }
                    Orientation.TOP, Orientation.BOTTOM -> {
                        height = canvasHeight
                        width = canvasWidth
                        infoY = canvasHeight - infoHeight
                    }
                    else -> {
                        height = canvasHeight
                        width = canvasWidth
                        infoY = canvasHeight - infoHeight
                    }
                }
                sensorY = infoY - infoHeight - sensorGap
                middleX = canvasWidth / 2
                middleY = canvasHeight / 2

                val levelWidth: Int
                val levelHeight: Int

                when (newOrientation) {
                    Orientation.LANDING -> {
                        levelWidth = levelMaxDimension
                        levelHeight = levelMaxDimension
                    }
                    Orientation.TOP, Orientation.BOTTOM, Orientation.LEFT, Orientation.RIGHT -> {
                        levelWidth = (width - 2 * displayGap)
                        levelHeight = (levelWidth * LEVEL_ASPECT_RATIO).toInt()
                    }
                }
                viscosityValue = levelWidth * sens
                minLevelX = middleX - levelWidth / 2
                maxLevelX = middleX + levelWidth / 2
                minLevelY = middleY - levelHeight / 2
                maxLevelY = middleY + levelHeight / 2

                halfBubbleWidth = (levelWidth * BUBBLE_WIDTH / 2).toInt()
                halfBubbleHeight = (halfBubbleWidth * BUBBLE_ASPECT_RATIO).toInt()
                bubbleWidth = 2 * halfBubbleWidth
                bubbleHeight = 2 * halfBubbleHeight
                maxBubble = (maxLevelY - bubbleHeight * BUBBLE_CROPPING).toInt()
                minBubble = maxBubble - bubbleHeight

                displayRect = Rect(
                    middleX - lcdWidth / 2 - displayPadding,
                    sensorY - displayGap - 2 * displayPadding - lcdHeight - infoHeight / 2,
                    middleX + lcdWidth / 2 + displayPadding, sensorY - displayGap - infoHeight / 2
                )

                halfMarkerGap = (levelWidth * MARKER_GAP / 2).toInt()

                levelMinusBubbleWidth = levelWidth - bubbleWidth - 2 * levelBorderWidth
                levelMinusBubbleHeight = levelHeight - bubbleHeight - 2 * levelBorderWidth

                level1D?.setBounds(minLevelX, minLevelY, maxLevelX, maxLevelY)
                level2D?.setBounds(minLevelX, minLevelY, maxLevelX, maxLevelY)
                marker2D?.setBounds(
                    middleX - halfMarkerGap - markerThickness,
                    middleY - halfMarkerGap - markerThickness,
                    middleX + halfMarkerGap + markerThickness,
                    middleY + halfMarkerGap + markerThickness
                )
                x = (maxLevelX + minLevelX).toDouble() / 2
                y = (maxLevelY + minLevelY).toDouble() / 2

                if (!initialized) {
                    initialized = true
                    pause(false)
                }
            }
        }
    }

    fun onOrientationChanged(newOrientation: Orientation, newPitch: Float, newRoll: Float, newBalance: Float) {
        if (orientation != newOrientation) {
            setOrientation(newOrientation)
        }
        pitch = newPitch
        roll = newRoll

        if (!wait) {
            when (orientation) {
                Orientation.TOP, Orientation.BOTTOM -> {
                    angle1 = abs(newBalance)
                    angleX = sin(Math.toRadians(newBalance.toDouble())) / MAX_SINUS
                }
                Orientation.LANDING -> {
                    angle2 = abs(newRoll)
                    angleX = sin(Math.toRadians(newRoll.toDouble())) / MAX_SINUS
                    angle1 = abs(newPitch)
                    angleY = sin(Math.toRadians(newPitch.toDouble())) / MAX_SINUS
                    if (angle1 > 90) {
                        angle1 = 180 - angle1
                    }
                }
                Orientation.RIGHT, Orientation.LEFT -> {
                    angle1 = abs(newPitch)
                    angleY = sin(Math.toRadians(newPitch.toDouble())) / MAX_SINUS
                    if (angle1 > 90) {
                        angle1 = 180 - angle1
                    }
                }
            }
            if (displayType == DisplayType.INCLINATION) {
                angle1 = (100 * tan(angle1 / 360 * 2 * Math.PI)).toFloat()
                angle2 = (100 * tan(angle2 / 360 * 2 * Math.PI)).toFloat()
            } else if (displayType == DisplayType.ROOF_PITCH) {
                angle1 = 12 * tan(Math.toRadians(angle1.toDouble())).toFloat()
                angle2 = 12 * tan(Math.toRadians(angle2.toDouble())).toFloat()
            }


            val angleTypeMax = displayType.max
            if (angle1 > angleTypeMax) {
                angle1 = angleTypeMax.toFloat()
            }
            if (angle2 > angleTypeMax) {
                angle2 = angleTypeMax.toFloat()
            }

            if (angleX > 1) {
                angleX = 1.0
            } else if (angleX < -1) {
                angleX = -1.0
            }
            if (angleY > 1) {
                angleY = 1.0
            } else if (angleY < -1) {
                angleY = -1.0
            }

            if (orientation == Orientation.LANDING && angleX != 0.0 && angleY != 0.0) {
                val n = sqrt(angleX * angleX + angleY * angleY)
                val t = acos(abs(angleX) / n)
                val l = 1 / max(abs(cos(t)), abs(sin(t)))
                angleX /= l
                angleY /= l
            }

            if (coordinateSystem == context.getString(R.string.coordinate_cartesian)) {
                if (angleX < -0.01 && angleY > 0.01) {
                    angle1D = ContextCompat.getDrawable(context, R.drawable.ic_bubble_left)
                    angle2D = ContextCompat.getDrawable(context, R.drawable.ic_bubble_down)
                } else if (angleX > 0.01 && angleY > 0.01) {
                    angle1D = ContextCompat.getDrawable(context, R.drawable.ic_bubble_right)
                    angle2D = ContextCompat.getDrawable(context, R.drawable.ic_bubble_down)
                } else if (angleX < -0.01 && angleY < -0.01) {
                    angle1D = ContextCompat.getDrawable(context, R.drawable.ic_bubble_left)
                    angle2D = ContextCompat.getDrawable(context, R.drawable.ic_bubble_up)
                } else if (angleX > 0.01 && angleY < -0.01) {
                    angle1D = ContextCompat.getDrawable(context, R.drawable.ic_bubble_right)
                    angle2D = ContextCompat.getDrawable(context, R.drawable.ic_bubble_up)
                }
            }

            sideLeft = null
            sideRight = null
            if (orientation == Orientation.RIGHT || orientation == Orientation.LEFT) {
                if (newPitch > 0) {
                    sideLeft = ContextCompat.getDrawable(context, R.drawable.ic_level_left)
                } else if (newPitch < 0) {
                    sideRight = ContextCompat.getDrawable(context, R.drawable.ic_level_right)
                }
            }
            if (orientation == Orientation.TOP || orientation == Orientation.BOTTOM) {
                if (newBalance < 0) {
                    sideLeft = ContextCompat.getDrawable(context, R.drawable.ic_level_left)
                } else if (newBalance > 0) {
                    sideRight = ContextCompat.getDrawable(context, R.drawable.ic_level_right)
                }
            }
        }
    }

    fun setNorthAngle(northAngle: Float) {
        this.northAngle = northAngle
    }

    fun setOrientationLocked(lock: Boolean) {
        this.orientationLocked = lock
    }

    companion object {
        private const val LEVEL_ASPECT_RATIO = 0.150
        private const val BUBBLE_WIDTH = 0.150
        private const val BUBBLE_ASPECT_RATIO = 1.000
        private const val BUBBLE_CROPPING = 0.500
        private const val MARKER_GAP = BUBBLE_WIDTH + 0.020
        private const val TRIANGLE_SIZE = 40
        private val MAX_SINUS = sin(Math.PI / 4)
        private const val FONT_LCD = "fonts/oswald_regular.ttf"
    }


    init {
        level1D = ContextCompat.getDrawable(context, R.drawable.level_1d)
        level2D = ContextCompat.getDrawable(context, R.drawable.level_2d)
        bubble1D = ContextCompat.getDrawable(context, R.drawable.bubble_1d)
        bubble2D = ContextCompat.getDrawable(context, R.drawable.bubble_2d)
        marker1D = ContextCompat.getDrawable(context, R.drawable.marker_1d)
        marker2D = ContextCompat.getDrawable(context, R.drawable.marker_2d)
        display = ContextCompat.getDrawable(context, R.drawable.display)
        sideLeft = ContextCompat.getDrawable(context, R.drawable.ic_level_left)
        sideRight = ContextCompat.getDrawable(context, R.drawable.ic_level_right)
        if (coordinateSystem == context.getString(R.string.coordinate_cartesian)) {
            angle1D = ContextCompat.getDrawable(context, R.drawable.ic_bubble_left)
            angle2D = ContextCompat.getDrawable(context, R.drawable.ic_bubble_up)
        } else {
            angle1D = ContextCompat.getDrawable(context, R.drawable.ic_rotation_angle)
            angle2D = ContextCompat.getDrawable(context, R.drawable.ic_rotate_pich)
        }
        if (coordinateSystem == context.getString(R.string.coordinate_cartesian)) {
            displayFormat = DecimalFormat(displayType.displayFormat)
            displayBackgroundText = displayType.displayBackgroundText
        } else {
            displayFormat = DecimalFormat("00.00")
            displayBackgroundText = "888.88"
        }

        backgroundColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE)

        val lcd = Typeface.createFromAsset(context.assets, FONT_LCD)

        infoPaint = Paint()
        infoPaint.color = ContextCompat.getColor(context, R.color.black)
        infoPaint.isAntiAlias = true
        infoPaint.textSize = context.resources.getDimensionPixelSize(R.dimen.info_text).toFloat()
        infoPaint.typeface = lcd
        infoPaint.textAlign = Paint.Align.CENTER
        infoPaint.strokeWidth = 2f

        lcdForegroundPaint = Paint()
        lcdForegroundPaint.color = ContextCompat.getColor(context, R.color.lcd_front)
        lcdForegroundPaint.isAntiAlias = true
        lcdForegroundPaint.textSize = context.resources.getDimensionPixelSize(R.dimen.lcd_text).toFloat()
        lcdForegroundPaint.typeface = lcd
        lcdForegroundPaint.textAlign = Paint.Align.CENTER

        blackPaint = Paint()
        blackPaint.color = ContextCompat.getColor(context, R.color.black)
        blackPaint.isAntiAlias = true
        blackPaint.strokeWidth = 8f
        blackPaint.style = Paint.Style.STROKE

        val rect = Rect()
        infoHeight = rect.height()
        lcdForegroundPaint.getTextBounds(displayBackgroundText, 0, displayBackgroundText.length, rect)
        lcdHeight = rect.height()
        lcdWidth = rect.width()
        levelBorderWidth = context.resources.getDimensionPixelSize(R.dimen.level_border_width)
        levelBorderHeight = context.resources.getDimensionPixelSize(R.dimen.level_border_height)
        markerThickness = context.resources.getDimensionPixelSize(R.dimen.marker_thickness)
        displayGap = context.resources.getDimensionPixelSize(R.dimen.display_gap)
        sensorGap = context.resources.getDimensionPixelSize(R.dimen.sensor_gap)
        displayPadding = context.resources.getDimensionPixelSize(R.dimen.display_padding)
        displayRect = Rect()
        arrowHeight = context.resources.getDimensionPixelSize(R.dimen.arrow_height)

        // init
        orientationLocked = false
        orientation = Orientation.TOP
        wait = true
        initialized = false

        sens = sin(Math.toRadians(context.appPrefGetValue(PrefSensitivity, context.resources.getInteger(R.integer.sensitivity_range_default)).toDouble()))

    }

}