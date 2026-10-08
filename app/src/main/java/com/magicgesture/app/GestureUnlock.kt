package com.magicgesture.app

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import java.time.Instant
import java.time.ZoneId

/**
 * 每日签到解锁权益（正式版一期，完全离线）。
 *
 * 产品规则见 `docs/GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md`：
 * - 初始开放 7 个基础编号；每天主动签到一次，永久解锁下一个功能包；断签不清零；7 次全部解锁。
 * - 所有正式 Release 用户同一规则；Debug/内部测试构建允许全部解锁，且不得进入正式包。
 *
 * 三层必须保持独立，不得混为一层：
 * - 解锁权益（本文件）：用户是否拥有该手势。
 * - 功能开关（`GestureFeatureConfig`）：拥有后是否参与识别。
 * - 动作映射（`GestureMappingManager`）：识别成功后执行什么动作。
 */
object GestureUnlockPlan {

    /**
     * 正式用户开箱即用的 5 个编号。
     *
     * 2026-10-08 由产品负责人确认：V 手势（G11）与开合掌（G13）移出开箱即用组——前者改成
     * 第 1 次签到解锁，后者改成第 7 次签到解锁，开箱当天只保留光标、点击与滚动这类基础操作。
     */
    val BASE_CODES: List<GestureCode> = listOf(
        GestureCode.G01, GestureCode.G02, GestureCode.G05, GestureCode.G06, GestureCode.G24
    )

    /**
     * 7 个功能包，第 N 次签到解锁 `PACKAGES[N - 1]`。
     *
     * 2026-10-08 由产品负责人确认把 12 次签到缩短为 7 次：原 12 个包按原优先级顺序合并，
     * 不拆散共用开关或同一动作族（两指媒体控制整族进第 2 次，张掌收指四连进第 4 次）。
     * G28（Love 手势）是系统级识别锁，不属于任何功能包。
     */
    val PACKAGES: List<List<GestureCode>> = listOf(
        listOf(GestureCode.G11, GestureCode.G22, GestureCode.G23),
        listOf(
            GestureCode.G31, GestureCode.G32,
            GestureCode.G29, GestureCode.G30, GestureCode.G33
        ),
        listOf(GestureCode.G12, GestureCode.G20, GestureCode.G21),
        listOf(GestureCode.G16, GestureCode.G17, GestureCode.G18, GestureCode.G19),
        listOf(GestureCode.G14, GestureCode.G15, GestureCode.G07, GestureCode.G08),
        listOf(GestureCode.G25, GestureCode.G27, GestureCode.G26, GestureCode.G34),
        listOf(
            GestureCode.G13, GestureCode.G35,
            GestureCode.G03, GestureCode.G04, GestureCode.G09, GestureCode.G10
        )
    )

    /** 功能包在首页与签到卡片上的中文说明，顺序与 [PACKAGES] 一致。 */
    val PACKAGE_LABELS: List<String> = listOf(
        "V 手势、握拳播放/暂停与小指静音",
        "两指媒体控制：切歌、音量与双击播放暂停",
        "点赞、拇指赞与 OK 收藏",
        "张掌收指打开常用与更多 App",
        "桌面、最近任务与并掌左右滚动",
        "单指枪竖向、C 手势、抓取与六六顺",
        "开合掌、双指枪竖向与轨迹预留"
    )

    val TOTAL_CHECK_INS: Int = PACKAGES.size

    /** 展示批次：0 表示开箱即用，即基础编号与系统级控制编号。 */
    const val BASE_STAGE = 0

    /** 单个编号的展示批次：0 = 开箱即用，N = 第 N 次签到解锁。 */
    fun stageOf(code: GestureCode): Int = checkInRequiredFor(code) ?: BASE_STAGE

    /**
     * 一张卡片或一行往往覆盖多个编号（例如并掌上挥 + 并掌下挥），展示批次取其中最晚的一个，
     * 保证只有该卡片覆盖的全部手势都解锁后，它才会显示为已解锁。
     */
    fun stageOfGroup(codes: Collection<GestureCode>): Int = codes.maxOfOrNull { stageOf(it) } ?: BASE_STAGE

    /** 分组标题；首页手势指南与校准页共用同一套文案。 */
    fun stageTitle(stage: Int): String =
        if (stage <= BASE_STAGE) "开箱即用 · 已解锁" else "第 $stage 次签到解锁"

    /** 未解锁提示文案；批次之外的情况统一显示“未解锁”。 */
    fun stageLockLabel(stage: Int): String =
        if (stage <= BASE_STAGE) "未解锁" else "第 $stage 次签到后开放"

    /**
     * 按展示批次稳定分组：批次升序，同批次保持传入顺序。
     * 首页手势指南、校准页动作映射列表与功能开关列表共用，避免三处各写一份排序。
     * 只影响展示，不改变 [PACKAGES] 与解锁权益。
     */
    fun <T> groupedByStage(items: List<T>, codesOf: (T) -> Collection<GestureCode>): List<StageGroup<T>> =
        items.groupBy { stageOfGroup(codesOf(it)) }.entries
            .sortedBy { it.key }
            .map { StageGroup(it.key, it.value) }

    /**
     * 给定签到次数后用户拥有的全部编号；次数会被收敛到 0..[TOTAL_CHECK_INS]。
     * 系统级控制编号（识别锁）始终拥有，与签到进度无关。
     */
    fun unlockedCodes(checkInCount: Int): Set<GestureCode> {
        val count = checkInCount.coerceIn(0, TOTAL_CHECK_INS)
        return LinkedHashSet<GestureCode>(BASE_CODES).apply {
            addAll(SYSTEM_CONTROL_CODES)
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

/** 同一展示批次下的一组手势卡片或配置行。 */
data class StageGroup<T>(val stage: Int, val items: List<T>)

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
    "two_finger_track" to listOf(GestureCode.G29, GestureCode.G30, GestureCode.G33),
    "two_finger_volume" to listOf(GestureCode.G31, GestureCode.G32),
    "two_finger_up" to listOf(GestureCode.G35),
    "open_app_1" to listOf(GestureCode.G16),
    "open_app_2" to listOf(GestureCode.G17),
    "open_app_3" to listOf(GestureCode.G18),
    "open_app_4" to listOf(GestureCode.G19)
)

/**
 * @param openingDay 开箱（首次安装并启动）所在的自然日；第 1 次签到必须晚于这一天，
 * 也就是今天开箱的话，最早明天才能开始第 1 次签到。老用户升级时记为昨天，不再多等一天。
 */
data class GestureUnlockState(
    val checkInCount: Int = 0,
    val lastCheckInDay: Long? = null,
    val openingDay: Long? = null
)

/** 签到结果：成功解锁、开箱当天、当天已签到、或已全部解锁。失败不得伪装成成功。 */
sealed interface CheckInResult {
    data class Unlocked(val state: GestureUnlockState, val newCodes: List<GestureCode>) : CheckInResult
    /** 开箱当天不能签到，第 1 次签到留到明天。 */
    data object OpeningDay : CheckInResult
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
            !isOpeningDay(state, todayEpochDay) &&
            (state.lastCheckInDay == null || todayEpochDay > state.lastCheckInDay)

    /** 开箱当天：只开放开箱即用的手势，第一次签到留给第二天。 */
    fun isOpeningDay(state: GestureUnlockState, todayEpochDay: Long): Boolean =
        state.openingDay != null && todayEpochDay <= state.openingDay

    fun checkIn(state: GestureUnlockState, todayEpochDay: Long): CheckInResult {
        if (GestureUnlockPlan.isComplete(state.checkInCount)) return CheckInResult.Completed
        // 开箱当天不发放签到权益，与“已签到”区分开，便于页面给出不同的说明。
        if (isOpeningDay(state, todayEpochDay)) return CheckInResult.OpeningDay
        // 同一天或时间回拨都视为不可再次领取；进度与已解锁权益保持不变。
        if (state.lastCheckInDay != null && todayEpochDay <= state.lastCheckInDay) return CheckInResult.AlreadyCheckedIn
        val gained = GestureUnlockPlan.PACKAGES[state.checkInCount]
        return CheckInResult.Unlocked(
            GestureUnlockState(state.checkInCount + 1, todayEpochDay, state.openingDay),
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
    fun state(now: Long = System.currentTimeMillis()): GestureUnlockState {
        val prefs = context.getSharedPreferences(GesturePreferences.FILE, Context.MODE_PRIVATE)
        val count = prefs.getInt(COUNT, 0).coerceIn(0, GestureUnlockPlan.TOTAL_CHECK_INS)
        val last = if (prefs.contains(LAST_DAY)) prefs.getLong(LAST_DAY, 0L) else null
        return GestureUnlockState(count, last, openingDay(prefs, now))
    }

    /**
     * 开箱日：首次安装所在的自然日，第 1 次签到必须晚于它（今天开箱，明天才能第 1 次签到）。
     *
     * 取自包管理器的首次安装时间，因此升级、重装与清除数据的行为都符合直觉：
     * 升级用户的安装日早于今天，当天即可照常签到；卸载重装视为重新开箱，当天要等到第二天。
     * 只在首次读取时写入一次，之后不再变更——用户改系统日期也不会把已过掉的等待期找回来。
     */
    private fun openingDay(prefs: SharedPreferences, now: Long): Long {
        val stored = if (prefs.contains(OPENING_DAY)) prefs.getLong(OPENING_DAY, 0L) else null
        if (stored != null) return stored
        val today = todayEpochDay(now)
        val installDay = installEpochDay()
        val value = if (installDay != null && installDay < today) installDay else today
        prefs.edit().putLong(OPENING_DAY, value).apply()
        return value
    }

    /** 本 App 首次安装的自然日；取不到时返回 null，由调用方退回今天。 */
    private fun installEpochDay(): Long? = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0)
        }
        Instant.ofEpochMilli(info.firstInstallTime).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    } catch (_: Exception) {
        null
    }

    /** 今天是否是开箱当天：是的话第 1 次签到要等到明天。 */
    fun isOpeningDay(now: Long = System.currentTimeMillis()): Boolean =
        GestureUnlockMachine.isOpeningDay(state(now), todayEpochDay(now))

    fun unlockedCodes(): Set<GestureCode> = GestureUnlockPlan.unlockedCodes(state().checkInCount)

    fun owns(code: GestureCode): Boolean = entitlement().owns(code)

    fun entitlement(): GestureEntitlement = GestureEntitlement(unlockedCodes(), debugUnlockAll)

    fun canCheckInToday(now: Long = System.currentTimeMillis()): Boolean =
        GestureUnlockMachine.canCheckIn(state(now), todayEpochDay(now))

    /** 主动签到；写入后立即生效，运行中的控制服务通过偏好监听刷新权益。 */
    fun checkIn(now: Long = System.currentTimeMillis()): CheckInResult {
        val current = state(now)
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
        private const val OPENING_DAY = "unlock_opening_day"
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
    // Love 手势（G28）是系统级识别锁：所有用户一开始就能用，不受签到解锁权益限制，
    // 也不可关闭。它既不在 BASE_CODES 里，也不在任何一个签到包里，若在这里做
    // unlocked.hasAny(G28) 判断，结果恒为 false，识别锁将永远无法触发。
    loveLock = true,
    six666 = six666 && unlocked.hasAny(GestureCode.G34),
    twoFingerTrack = twoFingerTrack && unlocked.hasAny(GestureCode.G29, GestureCode.G30, GestureCode.G33),
    twoFingerVolume = twoFingerVolume && unlocked.hasAny(GestureCode.G31, GestureCode.G32),
    twoFingerUp = twoFingerUp && unlocked.hasAny(GestureCode.G35),
    openApp1 = openApp1 && unlocked.hasAny(GestureCode.G16),
    openApp2 = openApp2 && unlocked.hasAny(GestureCode.G17),
    openApp3 = openApp3 && unlocked.hasAny(GestureCode.G18),
    openApp4 = openApp4 && unlocked.hasAny(GestureCode.G19)
)
