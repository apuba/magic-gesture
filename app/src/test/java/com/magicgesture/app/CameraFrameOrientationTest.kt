package com.magicgesture.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFrameOrientationTest {
    @Test fun frontCameraTracksAllDisplayRotations() {
        assertEquals(270, CameraFrameOrientation.relativeRotationDegrees(270, 0, true))
        assertEquals(0, CameraFrameOrientation.relativeRotationDegrees(270, 90, true))
        assertEquals(90, CameraFrameOrientation.relativeRotationDegrees(270, 180, true))
        assertEquals(180, CameraFrameOrientation.relativeRotationDegrees(270, 270, true))
    }

    @Test fun backCameraUsesOppositeDisplayDirection() {
        assertEquals(180, CameraFrameOrientation.relativeRotationDegrees(270, 90, false))
        assertEquals(0, CameraFrameOrientation.relativeRotationDegrees(270, 270, false))
    }

    @Test fun everyExifOrientationMapsToTheRotationThatMakesItUpright() {
        assertEquals(90, CameraFrameOrientation.exifRotationDegrees(6))   // ORIENTATION_ROTATE_90
        assertEquals(180, CameraFrameOrientation.exifRotationDegrees(3))  // ORIENTATION_ROTATE_180
        assertEquals(270, CameraFrameOrientation.exifRotationDegrees(8))  // ORIENTATION_ROTATE_270
    }

    @Test fun uprightOrMirroredExifTagsNeedNoRotation() {
        assertEquals(0, CameraFrameOrientation.exifRotationDegrees(0))    // ORIENTATION_UNDEFINED
        assertEquals(0, CameraFrameOrientation.exifRotationDegrees(1))    // ORIENTATION_NORMAL
        assertEquals(0, CameraFrameOrientation.exifRotationDegrees(2))    // ORIENTATION_FLIP_HORIZONTAL
        assertEquals(0, CameraFrameOrientation.exifRotationDegrees(7))    // ORIENTATION_TRANSVERSE
    }
}
