package com.magicgesture.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SelfieFramingTest {
    @Test fun portraitSensorImageKeepsMaximumHeightAndCropsSidesToTallDisplay() {
        assertEquals(
            SelfieFraming.CropRect(left = 600, top = 0, width = 1800, height = 4000),
            SelfieFraming.centerCrop(
                SelfieFraming.Size(3000, 4000),
                SelfieFraming.Size(1080, 2400)
            )
        )
    }

    @Test fun landscapeSensorImageKeepsMaximumWidthAndCropsTopAndBottom() {
        assertEquals(
            SelfieFraming.CropRect(left = 0, top = 600, width = 4000, height = 1800),
            SelfieFraming.centerCrop(
                SelfieFraming.Size(4000, 3000),
                SelfieFraming.Size(2400, 1080)
            )
        )
    }

    @Test fun matchingAspectRatioDoesNotCrop() {
        assertEquals(
            SelfieFraming.CropRect(left = 0, top = 0, width = 2160, height = 4800),
            SelfieFraming.centerCrop(
                SelfieFraming.Size(2160, 4800),
                SelfieFraming.Size(1080, 2400)
            )
        )
    }

    @Test fun invalidDisplayFallsBackToTheWholeSource() {
        assertEquals(
            SelfieFraming.CropRect(left = 0, top = 0, width = 3000, height = 4000),
            SelfieFraming.centerCrop(
                SelfieFraming.Size(3000, 4000),
                SelfieFraming.Size(0, 0)
            )
        )
    }
}
