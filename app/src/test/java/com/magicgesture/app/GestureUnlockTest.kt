package com.magicgesture.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离线签到解锁的纯逻辑测试（不依赖 Android）。
 * 覆盖：初始 7 个基础编号、7 次全部解锁、一天一次、断签不清零、时间回拨、
 * 解锁权益与功能开关互不覆盖，以及公共映射表的完整性。
 */
class GestureUnlockTest {

    @Test fun initialStateOwnsFiveBaseCodesPlusTheSystemControl() {
        val unlocked = GestureUnlockPlan.unlockedCodes(0)
        // 5 个基础编号 + G28 识别锁（系统级控制，与签到无关）。
        assertEquals(5 + SYSTEM_CONTROL_CODES.size, unlocked.size)
        assertTrue(
            unlocked.containsAll(
                listOf(GestureCode.G01, GestureCode.G02, GestureCode.G05, GestureCode.G06, GestureCode.G24)
            )
        )
    }

    /** 2026-10-08：V 手势改第 1 次签到，开合掌改第 7 次签到，都不再开箱即用。 */
    @Test fun vGestureAndScreenshotMoveOutOfTheOpeningSet() {
        val unlocked = GestureUnlockPlan.unlockedCodes(0)
        assertFalse("V 手势改为第 1 次签到", GestureCode.G11 in unlocked)
        assertFalse("开合掌改为第 7 次签到", GestureCode.G13 in unlocked)
        assertEquals(1, GestureUnlockPlan.stageOf(GestureCode.G11))
        assertEquals(7, GestureUnlockPlan.stageOf(GestureCode.G13))
        assertTrue(GestureCode.G11 in GestureUnlockPlan.unlockedCodes(1))
        assertTrue(GestureCode.G13 in GestureUnlockPlan.unlockedCodes(7))
    }

    /** 识别锁对所有用户可用，不随签到解锁，也不属于任何功能包。 */
    @Test fun recognitionLockIsAlwaysOwnedAndNeverGatedByCheckIn() {
        SYSTEM_CONTROL_CODES.forEach { code ->
            assertTrue("识别锁必须一开始就拥有", code in GestureUnlockPlan.unlockedCodes(0))
            assertNull("识别锁不需要签到", GestureUnlockPlan.checkInRequiredFor(code))
            assertTrue("识别锁不得作为签到奖励", GestureUnlockPlan.PACKAGES.none { code in it })
        }
    }

    @Test fun sevenCheckInsUnlockEveryGestureCode() {
        val unlocked = GestureUnlockPlan.unlockedCodes(GestureUnlockPlan.TOTAL_CHECK_INS)
        assertEquals(GestureCode.entries.toSet(), unlocked)
    }

    @Test fun packagesAreDisjointAndNeverRepeatBaseCodes() {
        val base = GestureUnlockPlan.BASE_CODES.toSet()
        val seen = mutableListOf<GestureCode>()
        GestureUnlockPlan.PACKAGES.forEach { pkg ->
            assertTrue("功能包不能为空", pkg.isNotEmpty())
            assertTrue("功能包不得包含基础编号", pkg.none { it in base })
            assertTrue("功能包之间不得重复编号", pkg.none { it in seen })
            seen += pkg
        }
        assertEquals(GestureCode.entries.toSet(), (base + seen + SYSTEM_CONTROL_CODES).toSet())
    }

    @Test fun checkInRequiredForReportsTheDayAGestureArrives() {
        assertNull("基础编号不需要签到", GestureUnlockPlan.checkInRequiredFor(GestureCode.G01))
        assertEquals(1, GestureUnlockPlan.checkInRequiredFor(GestureCode.G11))
        assertEquals(1, GestureUnlockPlan.checkInRequiredFor(GestureCode.G22))
        assertEquals(1, GestureUnlockPlan.checkInRequiredFor(GestureCode.G23))
        assertEquals(2, GestureUnlockPlan.checkInRequiredFor(GestureCode.G31))
        assertEquals(2, GestureUnlockPlan.checkInRequiredFor(GestureCode.G29))
        assertEquals(3, GestureUnlockPlan.checkInRequiredFor(GestureCode.G21))
        assertEquals(4, GestureUnlockPlan.checkInRequiredFor(GestureCode.G16))
        assertEquals(5, GestureUnlockPlan.checkInRequiredFor(GestureCode.G07))
        assertEquals(6, GestureUnlockPlan.checkInRequiredFor(GestureCode.G34))
        assertEquals(7, GestureUnlockPlan.checkInRequiredFor(GestureCode.G03))
        assertEquals(7, GestureUnlockPlan.checkInRequiredFor(GestureCode.G10))
        assertEquals(7, GestureUnlockPlan.checkInRequiredFor(GestureCode.G13))
        assertEquals(7, GestureUnlockPlan.checkInRequiredFor(GestureCode.G35))
    }

    /** 2026-10-08 起只需 7 次签到：27 个非基础编号必须全部落在这 7 个包里。 */
    @Test fun sevenPackagesCoverEveryNonBaseGesture() {
        assertEquals(7, GestureUnlockPlan.TOTAL_CHECK_INS)
        val packed = GestureUnlockPlan.PACKAGES.flatten().toSet()
        assertEquals(GestureCode.entries.toSet() - GestureUnlockPlan.BASE_CODES.toSet() - SYSTEM_CONTROL_CODES, packed)
    }

    @Test fun firstCheckInUnlocksTheFirstPackage() {
        val result = GestureUnlockMachine.checkIn(GestureUnlockState(), 100L)
        assertTrue(result is CheckInResult.Unlocked)
        val unlocked = result as CheckInResult.Unlocked
        assertEquals(1, unlocked.state.checkInCount)
        assertEquals(100L, unlocked.state.lastCheckInDay)
        assertEquals(listOf(GestureCode.G11, GestureCode.G22, GestureCode.G23), unlocked.newCodes)
    }

    /** 开箱当天不能签到：今天开箱的话，第 1 次签到留到明天。 */
    @Test fun theFirstCheckInIsNotAllowedOnTheOpeningDay() {
        val state = GestureUnlockState(openingDay = 100L)
        assertFalse("开箱当天不可签到", GestureUnlockMachine.canCheckIn(state, 100L))
        assertEquals(CheckInResult.OpeningDay, GestureUnlockMachine.checkIn(state, 100L))
        assertTrue("次日即可开始第 1 次签到", GestureUnlockMachine.canCheckIn(state, 101L))
        val nextDay = GestureUnlockMachine.checkIn(state, 101L) as CheckInResult.Unlocked
        assertEquals(1, nextDay.state.checkInCount)
    }

    /** 开箱日只挡第一次，之后的连续签到不受影响。 */
    @Test fun openingDayOnlyDelaysTheFirstCheckIn() {
        var state = GestureUnlockState(openingDay = 100L)
        repeat(GestureUnlockPlan.TOTAL_CHECK_INS) { day ->
            state = (GestureUnlockMachine.checkIn(state, 101L + day) as CheckInResult.Unlocked).state
        }
        assertEquals(GestureUnlockPlan.TOTAL_CHECK_INS, state.checkInCount)
        assertEquals(GestureCode.entries.toSet(), GestureUnlockPlan.unlockedCodes(state.checkInCount))
    }

    /** 没有开箱日记录（老用户升级）时，第一次签到照常可用，不会多等一天。 */
    @Test fun aMissingOpeningDayNeverBlocksCheckIn() {
        val state = GestureUnlockState()
        assertTrue(GestureUnlockMachine.canCheckIn(state, 100L))
        assertTrue(GestureUnlockMachine.checkIn(state, 100L) is CheckInResult.Unlocked)
    }

    @Test fun aSecondCheckInOnTheSameDayIsRefused() {
        val after = (GestureUnlockMachine.checkIn(GestureUnlockState(), 100L) as CheckInResult.Unlocked).state
        assertEquals(CheckInResult.AlreadyCheckedIn, GestureUnlockMachine.checkIn(after, 100L))
        assertFalse(GestureUnlockMachine.canCheckIn(after, 100L))
    }

    @Test fun nextDayCheckInAdvancesWithoutResettingProgress() {
        val first = (GestureUnlockMachine.checkIn(GestureUnlockState(), 100L) as CheckInResult.Unlocked).state
        val second = (GestureUnlockMachine.checkIn(first, 101L) as CheckInResult.Unlocked).state
        assertEquals(2, second.checkInCount)
        assertTrue(
            GestureUnlockPlan.unlockedCodes(second.checkInCount)
                .containsAll(listOf(GestureCode.G22, GestureCode.G31, GestureCode.G32))
        )
    }

    @Test fun clockRollbackNeverClearsOwnedGestures() {
        val after = (GestureUnlockMachine.checkIn(GestureUnlockState(), 100L) as CheckInResult.Unlocked).state
        assertEquals(CheckInResult.AlreadyCheckedIn, GestureUnlockMachine.checkIn(after, 50L))
        assertEquals(1, after.checkInCount)
        assertTrue(GestureUnlockPlan.unlockedCodes(after.checkInCount).contains(GestureCode.G22))
    }

    @Test fun completingAllCheckInsStopsFurtherRewards() {
        var state = GestureUnlockState()
        repeat(GestureUnlockPlan.TOTAL_CHECK_INS) { day ->
            state = (GestureUnlockMachine.checkIn(state, day.toLong()) as CheckInResult.Unlocked).state
        }
        assertEquals(GestureUnlockPlan.TOTAL_CHECK_INS, state.checkInCount)
        assertTrue(GestureUnlockPlan.isComplete(state.checkInCount))
        assertEquals(CheckInResult.Completed, GestureUnlockMachine.checkIn(state, 999L))
        assertNull(GestureUnlockPlan.nextPackage(state.checkInCount))
    }

    @Test fun lockedGesturesStayOffEvenWhenTheirSwitchIsStoredAsOn() {
        val features = GestureFeatureConfig().restrictedTo(GestureUnlockPlan.unlockedCodes(0))
        assertTrue(features.cursor)
        assertFalse("V 手势属于第 1 次签到", features.selfie)
        assertFalse("开合掌属于第 7 次签到", features.screenshot)
        assertFalse("G22 属于第 1 次签到", features.playPause)
        assertFalse("G31/G32 属于第 2 次签到", features.twoFingerVolume)
        assertFalse("G29/G30/G33 属于第 3 次签到", features.twoFingerTrack)
        assertFalse(features.openApp1)
        assertFalse(features.six666)
    }

    /** 音量与切歌同在第 2 次签到解锁，但仍是两个开关：关掉一个不影响另一个。 */
    @Test fun volumeAndTrackUnlockTogetherButStaySeparateSwitches() {
        val afterFirst = GestureFeatureConfig().restrictedTo(GestureUnlockPlan.unlockedCodes(1))
        assertFalse("第 1 次签到后音量仍锁定", afterFirst.twoFingerVolume)
        assertFalse("第 1 次签到后切歌仍锁定", afterFirst.twoFingerTrack)
        val afterSecond = GestureFeatureConfig().restrictedTo(GestureUnlockPlan.unlockedCodes(2))
        assertTrue(afterSecond.twoFingerVolume)
        assertTrue(afterSecond.twoFingerTrack)
        val volumeOnly = GestureFeatureConfig(twoFingerTrack = false).restrictedTo(GestureUnlockPlan.unlockedCodes(2))
        assertTrue("关切歌不得影响音量", volumeOnly.twoFingerVolume)
        assertFalse(volumeOnly.twoFingerTrack)
    }

    @Test fun unlockingRestoresTheSwitchTheUserNeverTurnedOff() {
        val unlocked = GestureUnlockPlan.unlockedCodes(GestureUnlockPlan.TOTAL_CHECK_INS)
        assertTrue(GestureFeatureConfig().restrictedTo(unlocked).playPause)
        assertTrue(GestureFeatureConfig().restrictedTo(unlocked).six666)
    }

    @Test fun aUserDisabledSwitchStaysDisabledAfterUnlocking() {
        val unlocked = GestureUnlockPlan.unlockedCodes(GestureUnlockPlan.TOTAL_CHECK_INS)
        val disabled = GestureFeatureConfig(playPause = false, six666 = false).restrictedTo(unlocked)
        assertFalse("关闭功能不等于未解锁，关闭状态必须保留", disabled.playPause)
        assertFalse(disabled.six666)
        assertTrue(disabled.selfie)
    }

    @Test fun featureKeyTableCoversEveryGestureCode() {
        assertEquals(GestureCode.entries.toSet(), GESTURE_CODES_BY_FEATURE.values.flatten().toSet())
    }

    @Test fun displayNamesCoverEveryGestureCode() {
        assertEquals(GestureCode.entries.toSet(), GESTURE_DISPLAY_NAMES.keys)
    }

    @Test fun stagePutsBaseAndSystemControlCodesBeforeEveryCheckIn() {
        GestureUnlockPlan.BASE_CODES.forEach { assertEquals("基础编号为开箱即用", 0, GestureUnlockPlan.stageOf(it)) }
        SYSTEM_CONTROL_CODES.forEach { assertEquals("识别锁为开箱即用", 0, GestureUnlockPlan.stageOf(it)) }
        assertEquals(1, GestureUnlockPlan.stageOf(GestureCode.G22))
        assertEquals(5, GestureUnlockPlan.stageOf(GestureCode.G07))
        assertEquals(7, GestureUnlockPlan.stageOf(GestureCode.G35))
    }

    /** 一张卡片覆盖多个编号时，展示批次取其中最晚的一个。 */
    @Test fun stageOfGroupFollowsTheLatestGestureBehindOneCard() {
        assertEquals(0, GestureUnlockPlan.stageOfGroup(listOf(GestureCode.G05, GestureCode.G06)))
        assertEquals(2, GestureUnlockPlan.stageOfGroup(listOf(GestureCode.G29, GestureCode.G30)))
        assertEquals(2, GestureUnlockPlan.stageOfGroup(listOf(GestureCode.G31, GestureCode.G32)))
        assertEquals(5, GestureUnlockPlan.stageOfGroup(listOf(GestureCode.G14, GestureCode.G07)))
    }

    @Test fun groupedByStageListsBaseFirstThenEveryCheckInInOrder() {
        val cards = listOf(
            "握拳" to listOf(GestureCode.G22),
            "并掌上挥 / 并掌下挥" to listOf(GestureCode.G05, GestureCode.G06),
            "抓取手势" to listOf(GestureCode.G26),
            "双指左挥 / 双指右挥" to listOf(GestureCode.G29, GestureCode.G30)
        )
        val groups = GestureUnlockPlan.groupedByStage(cards) { it.second }
        assertEquals(listOf(0, 1, 2, 6), groups.map { it.stage })
        assertEquals(listOf("并掌上挥 / 并掌下挥"), groups[0].items.map { it.first })
        assertEquals(listOf("握拳"), groups[1].items.map { it.first })
        assertEquals(listOf("双指左挥 / 双指右挥"), groups[2].items.map { it.first })
        assertEquals(listOf("抓取手势"), groups[3].items.map { it.first })
    }

    /** 同批次内的顺序保持书写顺序，避免每次构建后卡片位置跳动。 */
    @Test fun groupedByStageKeepsTheWrittenOrderInsideAStage() {
        val cards = listOf(
            "莲花指" to listOf(GestureCode.G14),
            "兰花指" to listOf(GestureCode.G15)
        )
        val groups = GestureUnlockPlan.groupedByStage(cards) { it.second }
        assertEquals(1, groups.size)
        assertEquals(5, groups.first().stage)
        assertEquals(listOf("莲花指", "兰花指"), groups.first().items.map { it.first })
    }

    @Test fun stageTitlesAndLockLabelsUseTheCheckInWording() {
        assertEquals("开箱即用 · 已解锁", GestureUnlockPlan.stageTitle(0))
        assertEquals("第 1 次签到解锁", GestureUnlockPlan.stageTitle(1))
        assertEquals("第 7 次签到解锁", GestureUnlockPlan.stageTitle(GestureUnlockPlan.TOTAL_CHECK_INS))
        assertEquals("第 1 次签到后开放", GestureUnlockPlan.stageLockLabel(1))
        assertEquals("未解锁", GestureUnlockPlan.stageLockLabel(0))
    }
}
