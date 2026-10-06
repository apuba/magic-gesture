package com.magicgesture.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Per-brand guidance for keeping the accessibility authorization alive across background cleanup. */
class KeepAuthorizationActivity : Activity() {

    /** Debug-only brand preview switcher. Null means "auto detect from Build". */
    private var previewBrand: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(72, 69, 198)
        window.navigationBarColor = Color.rgb(246, 245, 255)
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(32))
        }

        content.addView(actionButton("←  返回", Color.WHITE, Color.rgb(83, 80, 214), Color.rgb(232, 230, 255), Color.rgb(204, 201, 239)) {
            finish()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)).apply { bottomMargin = dp(4) })

        val titleView = label("保持授权不丢失", 24f, Color.rgb(31, 31, 55), true).apply {
            setPadding(dp(2), dp(14), 0, dp(4))
        }
        content.addView(titleView)
        if (BuildConfig.DEBUG) {
            var taps = 0
            titleView.setOnClickListener {
                taps++
                if (taps >= 5) {
                    taps = 0
                    cyclePreviewBrand()
                }
            }
            content.addView(label(debugPreviewHint(), 11.5f, Color.rgb(150, 148, 170), false).apply {
                setPadding(dp(2), 0, dp(2), dp(6))
            }, 2)
        }
        content.addView(label("无障碍授权由 Android 系统保存，但部分品牌会在清理后台时一并撤销它，导致手势控制失效。根据你的机型完成下面的设置，可以避免日常使用中授权丢失。", 13f, Color.rgb(104, 102, 126), false).apply {
            setPadding(dp(2), dp(4), dp(2), dp(14))
            setLineSpacing(0f, 1.15f)
        })

        content.addView(brandSection(), margins(bottom = 14))

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.rgb(255, 247, 226), 16, Color.rgb(244, 216, 157))
            addView(label("补充说明", 14f, Color.rgb(157, 92, 20), true))
            addView(label(
                "• 主动“强行停止”、清除应用数据或重新安装后，系统仍会要求重新授权，这是 Android 安全机制，任何应用都无法绕过。\n" +
                    "• 已支持荣耀 / 华为（真机实测）与小米 / Redmi（依据公开设置路径整理，未实测）。OPPO、vivo 等品牌的专项方案会陆续补充。", 12.5f, Color.rgb(122, 90, 40), false
            ).apply {
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(0f, 1.2f)
            })
        })

        return ScrollView(this).apply {
            setBackgroundColor(Color.rgb(246, 245, 255))
            isFillViewport = true
            addView(content)
        }
    }

    private fun brandSection(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.WHITE, 18, Color.rgb(229, 227, 246))
        elevation = dp(2).toFloat()
        val scheme = detectScheme()
        if (scheme == null) {
            addView(label("通用建议", 16f, Color.rgb(66, 63, 160), true))
            addView(label(
                "当前没有针对你的机型（${Build.MANUFACTURER ?: "未知品牌"}）的专项方案，可先参考通用做法：在系统设置中允许本应用后台运行和自启动，并尽量避免对它使用“强行停止”。", 12.5f, Color.rgb(91, 89, 113), false
            ).apply {
                setPadding(0, dp(10), 0, 0)
                setLineSpacing(0f, 1.15f)
            })
            addView(actionButton("去应用详情设置", Color.WHITE, Color.rgb(83, 80, 214), Color.rgb(232, 230, 255), Color.rgb(204, 201, 239)) {
                openAppDetails()
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(14) })
        } else {
            addView(label("✓  ${scheme.title}", 16f, Color.rgb(35, 115, 78), true))
            addView(label(scheme.intro, 12.5f, Color.rgb(91, 89, 113), false).apply {
                setPadding(0, dp(10), 0, dp(4))
                setLineSpacing(0f, 1.15f)
            })
            scheme.steps.forEachIndexed { index, step ->
                addView(label("${index + 1}.  $step", 13.5f, Color.rgb(38, 37, 59), false).apply {
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    setLineSpacing(0f, 1.15f)
                })
            }
            addView(actionButton("去应用详情设置", Color.WHITE, Color.rgb(83, 80, 214), Color.rgb(232, 230, 255), Color.rgb(204, 201, 239)) {
                openAppDetails()
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(12) })
        }
    }

    private data class BrandScheme(val title: String, val intro: String, val steps: List<String>)

    /** Debug-only: cycles auto → XIAOMI → GENERIC so every wording can be checked on any device. */
    private fun cyclePreviewBrand() {
        previewBrand = when (previewBrand) {
            null -> "XIAOMI"
            "XIAOMI" -> "GENERIC"
            else -> null
        }
        setContentView(buildContent())
    }

    private fun debugPreviewHint(): String {
        val current = when (previewBrand) {
            "XIAOMI" -> "小米方案"
            "GENERIC" -> "通用建议（无品牌匹配）"
            else -> "自动识别（${Build.MANUFACTURER ?: "未知品牌"}）"
        }
        return "[Debug] 当前预览：$current　连点标题 5 次切换"
    }

    /** Add OPPO / VIVO schemes here once they are confirmed on real devices. */
    private fun detectScheme(): BrandScheme? {
        if (previewBrand == "XIAOMI") return xiaomiScheme()
        if (previewBrand == "GENERIC") return null
        val manufacturer = (Build.MANUFACTURER ?: "").uppercase(Locale.ROOT)
        val brand = (Build.BRAND ?: "").uppercase(Locale.ROOT)
        val isHonorOrHuawei = listOf(manufacturer, brand).any {
            it.contains("HONOR") || it.contains("HUAWEI")
        }
        if (isHonorOrHuawei) return honorScheme()
        val isXiaomi = listOf(manufacturer, brand).any {
            it.contains("XIAOMI") || it.contains("REDMI")
        }
        if (isXiaomi) return xiaomiScheme()
        return null
    }

    private fun honorScheme() = BrandScheme(
        title = "荣耀 / 华为机型设置方案",
        intro = "荣耀 MagicOS / 华为 HarmonyOS 会在“上划清理后台”或“强行停止”时撤销无障碍授权（2026-10 已实测确认），需要通过启动管理白名单避免。",
        steps = listOf(
            "打开 设置 → 应用 → 魔法手势 → 电池（或 设置 → 电池 → 应用启动管理，找到魔法手势）",
            "关闭“自动管理”，改为“手动管理”",
            "开启“允许自启动”“允许关联启动”“允许后台活动”"
        )
    )

    private fun xiaomiScheme() = BrandScheme(
        title = "小米 / Redmi 机型设置方案",
        intro = "MIUI / 澎湃OS 会在一键清理、省电策略或内存回收时撤销无障碍与悬浮窗授权，需要自启动、省电策略与后台锁定同时设置。本方案依据公开设置路径整理，尚未在小米真机上实测，菜单名称可能随系统版本略有差异。",
        steps = listOf(
            "打开 设置 → 应用设置 → 应用管理 → 魔法手势 → 权限管理，开启“悬浮窗”与“后台弹出界面”",
            "同一页面找到“省电策略 / 电池与性能”，改为“无限制”，避免系统自动清理后台",
            "返回 设置 → 应用设置 → 自启动管理，开启魔法手势的自启动",
            "确认无障碍已开启：设置 → 更多设置 → 无障碍 → 已下载的服务 → 魔法手势",
            "在最近任务界面长按魔法手势卡片并锁定，避免一键清理时授权被回收"
        )
    )

    private fun openAppDetails() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Exception) { }
    }

    private fun actionButton(
        textValue: String,
        fill: Int,
        textColor: Int,
        pressedFill: Int,
        stroke: Int? = null,
        action: () -> Unit
    ) = Button(this).apply {
        text = textValue
        textSize = 15f
        setTextColor(textColor)
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(pressedFill, 16, stroke))
            addState(intArrayOf(android.R.attr.state_focused), rounded(pressedFill, 16, stroke))
            addState(intArrayOf(), rounded(fill, 16, stroke))
        }
        stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun margins(bottom: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
