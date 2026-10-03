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

    /**
     * Converts one EXIF orientation constant into the clockwise rotation needed to view the picture
     * upright. Answers to JPEG_ORIENTATION differ per device: some rotate the pixels, others only
     * write the tag. Galleries honour the tag but BitmapFactory does not, so selfies looked sideways
     * in the overlay while looking fine elsewhere. Baking this rotation into the pixels makes every
     * consumer agree. Numbers are ExifInterface.ORIENTATION_ROTATE_180 / _90 / _270 (3 / 6 / 8);
     * the mirrored variants are deliberately treated as "no rotation" because they describe
     * flipping, which the pipeline already applies to preview frames.
     */
    fun exifRotationDegrees(orientationConstant: Int): Int = when (orientationConstant) {
        3 -> 180
        6 -> 90
        8 -> 270
        else -> 0
    }
}
