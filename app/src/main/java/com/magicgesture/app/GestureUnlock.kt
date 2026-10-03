package com.magicgesture.app

import android.content.Context
import java.time.Instant
import java.time.ZoneId

/**
 * 每日签到解锁权益（正式版一期，完全离线）。
 *
 * 产品规则见 `docs/GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md`：
 * - 初始开放 7 个基础编号；每天主动签到一次，永久解锁下一个功能包；断签不清零；12 次全部解锁。
 * - 所有正式 Release 用户同一规则；Debug/内部测试构建允许全部解锁，且不得进入正式包。
 *
 * 三层必须保持独立，不得混为一层：
 * - 解锁权益（本文件）：用户是否拥有该手势。
 * - 功能开关（`GestureFeatureConfig`）：拥有后是否参与识别。
 * - 动作映射（`GestureMappingManager`）：识别成功后执行什么动作。
 */
object GestureUnlockPlan {

    /** 正式用户初始拥有的 7 个编号。 */
    val BASE_CODES: List<GestureCode> = listOf(
        GestureCode.G01, GestureCode.G02, GestureCode.G03, GestureCode.G04,
        GestureCode.G24, GestureCode.G11, GestureCode.G13
    )

    /** 12 个功能包，第 N 次签到解锁 `PACKAGES[N - 1]`。 */
    val PACKAGES: List<List<GestureCode>> = listOf(
        listOf(GestureCode.G22),
        listOf(GestureCode.G31, GestureCode.G32),
        listOf(GestureCode.G29, GestureCode.G30, GestureCode.G33),
        listOf(GestureCode.G23),
        listOf(GestureCode.G21),
        listOf(GestureCode.G16, GestureCode.G17),
        listOf(GestureCode.G18, GestureCode.G19),
        listOf(GestureCode.G14, GestureCode.G15),
        listOf(GestureCode.G05, GestureCode.G06),
        listOf(GestureCode.G07, GestureCode.G08, GestureCode.G09, GestureCode.G10),
        listOf(GestureCode.G25, GestureCode.G27, GestureCode.G28),
        listOf(GestureCode.G12, GestureCode.G20, GestureCode.G26, GestureCode.G34, GestureCode.G35)
    )

    /** 功能包在首页与签到卡片上的中文说明，顺序与 [PACKAGES] 一致。 */
    val PACKAGE_LABELS: List<String> = listOf(
        "握拳播放/暂停",
        "两指持续增减音量",
        "两指切歌与双击播放暂停",
        "小指手势静音开关",
        "OK 收藏当前内容",
        "张掌收指打开常用 App",
        "张掌收指打开更多 App",
        "莲花指与兰花指",
        "并掌上下滚动",
        "横向滚动手势",
        "通知栏与系统导航",
        "点赞手势与高级手势"
    )

    val TOTAL_CHECK_INS: Int = PACKAGES.size

    /** 给定签到次数后用户拥有的全部编号；次数会被收敛到 0..[TOTAL_CHECK_INS]。 */
    fun unlockedCodes(checkInCount: Int): Set<GestureCode> {
        val count = checkInCount.coerceIn(0, TOTAL_CHECK_INS)
        return LinkedHashSet<GestureCode>(BASE_CODES).apply {
            PACKAGES.take(count).forEach { addAll(it) }
        }
    }

    /** 下一次签到将解锁的编号；全部解锁后为 null。 */
    fun nextPackage(checkInCount: Int): List<GestureCode>? = PACKAGES.getOrNull(checkInCount)

    /** 下一次签到将解锁的功能包说明；全部解锁后为 null。 */
    fun nextPackageLabel(checkInCount: Int): String? = PACKAGE_LABELS.getOrNull(checkInCount)

    /** 解锁该手势所需的第几次签到；基础编号返回 null（一开始就拥有）。 */
    fun checkInRequiredFor(code: GestureCode): Int? {
        val index = PACKAGES.indexOfFirst { code in it }
        return if (index < 0) null else index + 1
    }

    fun isComplete(checkInCount: Int): Boolean = checkInCount >= TOTAL_CHECK_INS
}

/**
 * 功能开关键 → 该开关覆盖的手势编号。首页与校准页据此把未解锁手势的开关置为不可用，
 * 标识与 `GesturePreferences` 的 `feature_*` 后缀一致。
 */
val GESTURE_CODES_BY_FEATURE: Map<String, List<GestureCode>> = mapOf(
    "cursor" to listOf(GestureCode.G01),
    "click" to listOf(GestureCode.G02),
    "index_vertical_scroll" to listOf(GestureCode.G03, GestureCode.G04),
    "palm_vertical_scroll" to listOf(GestureCode.G05, GestureCode.G06),
    "palm_left_scroll" to listOf(GestureCode.G07),
    "index_left_scroll" to listOf(GestureCode.G09),
    "palm_right_scroll" to listOf(GestureCode.G08),
    "index_right_scroll" to listOf(GestureCode.G10),
    "screenshot" to listOf(GestureCode.G13),
    "selfie" to listOf(GestureCode.G11),
    "like" to listOf(GestureCode.G12),
    "thumbs_up" to listOf(GestureCode.G20),
    "ok" to listOf(GestureCode.G21),
    "play_pause" to listOf(GestureCode.G22),
    "pinky_mute" to listOf(GestureCode.G23),
    "lotus_recents" to listOf(GestureCode.G14),
    "orchid_back" to listOf(GestureCode.G15),
    "left_l" to listOf(GestureCode.G24),
    "l_shape" to listOf(GestureCode.G25),
    "claw_drag" to listOf(GestureCode.G26),
    "c_shape" to listOf(GestureCode.G27),
    "love_lock" to listOf(GestureCode.G28),
    "six666" to listOf(GestureCode.G34),
    "two_finger_media" to listOf(
        GestureCode.G29, GestureCode.G30, GestureCode.G31, GestureCode.G32, GestureCode.G33
    ),
    "two_finger_up" to listOf(GestureCode.G35),
    "open_app_1" to listOf(GestureCode.G16),
    "open_app_2" to listOf(GestureCode.G17),
    "open_app_3" to listOf(GestureCode.G18),
    "open_app_4" to listOf(GestureCode.G19)
)

data class GestureUnlockState(
    val checkInCount: Int = 0,
    val lastCheckInDay: Long? = null
)

/** 签到结果：成功解锁、当天已签到、或已全部解锁。失败不得伪装成成功。 */
sealed interface CheckInResult {
    data class Unlocked(val state: GestureUnlockState, val newCodes: List<GestureCode>) : CheckInResult
    data object AlreadyCheckedIn : CheckInResult
    data object Completed : CheckInResult
}

/**
 * 签到状态机（纯逻辑，不依赖 Android，便于 JVM 测试）。
 * 日期用本地时区的 epochDay；系统时间回拨只会让当天无法再次签到，不会清零已有权益。
 */
object GestureUnlockMachine {

    fun canCheckIn(state: GestureUnlockState, todayEpochDay: Long): Boolean =
        !GestureUnlockPlan.isComplete(state.checkInCount) &&
            (state.lastCheckInDay == null || todayEpochDay > state.lastCheckInDay)

    fun checkIn(state: GestureUnlockState, todayEpochDay: Long): CheckInResult {
        if (GestureUnlockPlan.isComplete(state.checkInCount)) return CheckInResult.Completed
        // 同一天或时间回拨都视为不可再次领取；进度与已解锁权益保持不变。
        if (state.lastCheckInDay != null && todayEpochDay <= state.lastCheckInDay) return CheckInResult.AlreadyCheckedIn
        val gained = GestureUnlockPlan.PACKAGES[state.checkInCount]
        return CheckInResult.Unlocked(
            GestureUnlockState(state.checkInCount + 1, todayEpochDay),
            gained
        )
    }
}

/** 手势拥有权判定。Debug 构建全开，正式 Release 严格按签到进度。 */
class GestureEntitlement(
    private val unlocked: Set<GestureCode>,
    private val unlockAll: Boolean = false
) {
    fun owns(code: GestureCode): Boolean = unlockAll || code in unlocked

    val codes: Set<GestureCode> get() = if (unlockAll) GestureCode.entries.toSet() else unlocked

    val unlockAllEnabled: Boolean get() = unlockAll
}

/** 本机离线存储：签到次数与最近一次签到的自然日。 */
class GestureUnlockStore(
    private val context: Context,
    /** Debug/内部测试构建允许全部解锁；正式 Release 恒为 false。 */
    private val debugUnlockAll: Boolean = BuildConfig.DEBUG
) {
    fun state(): GestureUnlockState {
        val prefs = context.getSharedPreferences(GesturePreferences.FILE, Context.MODE_PRIVATE)
        val count = prefs.getInt(COUNT, 0).coerceIn(0, GestureUnlockPlan.TOTAL_CHECK_INS)
        val last = if (prefs.contains(LAST_DAY)) prefs.getLong(LAST_DAY, 0L) else null
        return GestureUnlockState(count, last)
    }

    fun unlockedCodes(): Set<GestureCode> = GestureUnlockPlan.unlockedCodes(state().checkInCount)

    fun owns(code: GestureCode): Boolean = entitlement().owns(code)

    fun entitlement(): GestureEntitlement = GestureEntitlement(unlockedCodes(), debugUnlockAll)

    fun canCheckInToday(now: Long = System.currentTimeMillis()): Boolean =
        GestureUnlockMachine.canCheckIn(state(), todayEpochDay(now))

    /** 主动签到；写入后立即生效，运行中的控制服务通过偏好监听刷新权益。 */
    fun checkIn(now: Long = System.currentTimeMillis()): CheckInResult {
        val current = state()
        val result = GestureUnlockMachine.checkIn(current, todayEpochDay(now))
        if (result is CheckInResult.Unlocked) {
            context.getSharedPreferences(GesturePreferences.FILE, Context.MODE_PRIVATE).edit()
                .putInt(COUNT, result.state.checkInCount)
                .putLong(LAST_DAY, requireNotNull(result.state.lastCheckInDay))
                .apply()
        }
        return result
    }

    /** 本地时区下的自然日；跨天与时区变化均以此为准。 */
    fun todayEpochDay(now: Long = System.currentTimeMillis()): Long =
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    companion object {
        /** 偏好键前缀，控制服务据此在运行中即时刷新权益。 */
        const val KEY_PREFIX = "unlock_"
        private const val COUNT = "unlock_checkin_count"
        private const val LAST_DAY = "unlock_last_day"
    }
}

private fun Set<GestureCode>.hasAny(vararg codes: GestureCode) = codes.any { it in this }

/**
 * 按解锁权益收敛功能开关：未拥有的手势不参与识别。
 * 用户关闭的开关保持关闭，已解锁且开启的开关保持原值——功能开关与解锁权益互不覆盖。
 */
fun GestureFeatureConfig.restrictedTo(unlocked: Set<GestureCode>): GestureFeatureConfig = copy(
    cursor = cursor && unlocked.hasAny(GestureCode.G01),
    click = click && unlocked.hasAny(GestureCode.G02),
    indexVerticalScroll = indexVerticalScroll && unlocked.hasAny(GestureCode.G03, GestureCode.G04),
    palmVerticalScroll = palmVerticalScroll && unlocked.hasAny(GestureCode.G05, GestureCode.G06),
    palmLeftScroll = palmLeftScroll && unlocked.hasAny(GestureCode.G07),
    indexLeftScroll = indexLeftScroll && unlocked.hasAny(GestureCode.G09),
    palmRightScroll = palmRightScroll && unlocked.hasAny(GestureCode.G08),
    indexRightScroll = indexRightScroll && unlocked.hasAny(GestureCode.G10),
    screenshot = screenshot && unlocked.hasAny(GestureCode.G13),
    selfie = selfie && unlocked.hasAny(GestureCode.G11),
    like = like && unlocked.hasAny(GestureCode.G12),
    thumbsUp = thumbsUp && unlocked.hasAny(GestureCode.G20),
    ok = ok && unlocked.hasAny(GestureCode.G21),
    playPause = playPause && unlocked.hasAny(GestureCode.G22),
    pinkyMute = pinkyMute && unlocked.hasAny(GestureCode.G23),
    lotusRecents = lotusRecents && unlocked.hasAny(GestureCode.G14),
    orchidBack = orchidBack && unlocked.hasAny(GestureCode.G15),
    leftL = leftL && unlocked.hasAny(GestureCode.G24),
    lShape = lShape && unlocked.hasAny(GestureCode.G25),
    clawDrag = clawDrag && unlocked.hasAny(GestureCode.G26),
    cShape = cShape && unlocked.hasAny(GestureCode.G27),
    loveLock = loveLock && unlocked.hasAny(GestureCode.G28),
    six666 = six666 && unlocked.hasAny(GestureCode.G34),
    twoFingerMedia = twoFingerMedia && unlocked.hasAny(
        GestureCode.G29, GestureCode.G30, GestureCode.G31, GestureCode.G32, GestureCode.G33
    ),
    twoFingerUp = twoFingerUp && unlocked.hasAny(GestureCode.G35),
    openApp1 = openApp1 && unlocked.hasAny(GestureCode.G16),
    openApp2 = openApp2 && unlocked.hasAny(GestureCode.G17),
    openApp3 = openApp3 && unlocked.hasAny(GestureCode.G18),
    openApp4 = openApp4 && unlocked.hasAny(GestureCode.G19)
)
