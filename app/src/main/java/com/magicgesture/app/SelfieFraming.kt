package com.magicgesture.app

/** Pure geometry for cropping a selfie to the current physical display aspect ratio. */
internal object SelfieFraming {
    data class Size(val width: Int, val height: Int)
    data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)

    fun centerCrop(source: Size, display: Size): CropRect {
        if (source.width <= 0 || source.height <= 0 || display.width <= 0 || display.height <= 0) {
            return CropRect(0, 0, source.width.coerceAtLeast(0), source.height.coerceAtLeast(0))
        }

        val sourceRatio = source.width.toDouble() / source.height
        val displayRatio = display.width.toDouble() / display.height
        return when {
            sourceRatio > displayRatio -> {
                val width = (source.height * displayRatio).toInt().coerceIn(1, source.width)
                CropRect((source.width - width) / 2, 0, width, source.height)
            }
            sourceRatio < displayRatio -> {
                val height = (source.width / displayRatio).toInt().coerceIn(1, source.height)
                CropRect(0, (source.height - height) / 2, source.width, height)
            }
            else -> CropRect(0, 0, source.width, source.height)
        }
    }
}
