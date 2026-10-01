package com.magicgesture.app

/** Calculates the rotation needed to make camera coordinates match the current display. */
internal object CameraFrameOrientation {
    fun relativeRotationDegrees(
        sensorOrientationDegrees: Int,
        displayRotationDegrees: Int,
        frontFacing: Boolean
    ): Int {
        val direction = if (frontFacing) 1 else -1
        return (sensorOrientationDegrees + displayRotationDegrees * direction + 360) % 360
    }
}
