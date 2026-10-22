package com.magicgesture.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCameraPolicyTest {
    @Test fun usableScreenAlwaysRunsCameraWithoutForcingMusic() {
        val policy = ScreenCameraPolicy(screenUsable = true, mediaMusicActive = false)
        assertTrue(policy.shouldRunCamera)
        assertFalse(policy.shouldForceMusicScene)
    }

    @Test fun screenOffWithoutMusicSuspendsCamera() {
        val policy = ScreenCameraPolicy(screenUsable = false, mediaMusicActive = false)
        assertFalse(policy.shouldRunCamera)
        assertFalse(policy.shouldForceMusicScene)
    }

    @Test fun screenOffWithMediaMusicKeepsCameraAndMusicScene() {
        val policy = ScreenCameraPolicy(screenUsable = false, mediaMusicActive = true)
        assertTrue(policy.shouldRunCamera)
        assertTrue(policy.shouldForceMusicScene)
    }

    @Test fun returningToUsableScreenStopsForcingMusic() {
        val policy = ScreenCameraPolicy(screenUsable = false, mediaMusicActive = true)
        policy.updateScreenUsable(true)
        assertTrue(policy.shouldRunCamera)
        assertFalse(policy.shouldForceMusicScene)
    }

    @Test fun mediaUsageCountsAsMusic() {
        assertTrue(ScreenCameraPolicy.isMediaMusicUsage(1))
    }

    @Test fun navigationGuidanceDoesNotCountAsMusic() {
        // Android AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE.
        assertFalse(ScreenCameraPolicy.isMediaMusicUsage(12))
    }
}
