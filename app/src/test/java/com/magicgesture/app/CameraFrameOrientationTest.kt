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
}
