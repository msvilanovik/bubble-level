package com.zipoapps.level.orientation

import kotlin.math.abs

enum class Orientation(val reverse: Int, val rotation: Int) {
    LANDING(1, 0),
    TOP(1, 0),
    RIGHT(1, 90),
    BOTTOM(-1, 180),
    LEFT(-1, -90);

    fun isLevel(pitch: Float, roll: Float, balance: Float): Boolean {

        if (this == BOTTOM || this == TOP ) {
            return balance < 0.05f && balance > -0.05f
        }
        if( this == LEFT || this == RIGHT){
            return pitch < 0.05f && pitch > -0.05f
        }
        if (this == LANDING && roll < 0.05f && roll > -0.05f) {
            return abs(pitch) < 0.05f || abs(pitch) > 179.95f
        }
        return false
    }
}