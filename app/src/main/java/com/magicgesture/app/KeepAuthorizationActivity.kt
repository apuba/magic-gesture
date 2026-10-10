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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * 保持授权不丢失页：深色霓虹版，视觉与「收藏按钮位置」页一致（同样使用 ui/切片/bg2.png 背景）。
 * 内容与判定逻辑不变：按机型给出荣耀/华为、小米/Redmi 与通用建议三类方案。
 */
class KeepAuthorizationActivity : Activity() {

    /** Debug-only brand preview switcher. Null means "auto detect from Build". */
    private var previewBrand: String? = null
    private var debugTaps = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(36))
        }

        content.addView(headerRow(), blockMargins(18))
        content.addView(introCard(), blockMargins(16))
        content.addView(brandSection(), blockMargins(14))
        content.addView(noteCard(), blockMargins(18))

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFillViewport = true
            addView(FrameLayout(this@KeepAuthorizationActivity).apply {
                addView(pageBackgroundView(), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
                addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            })
        }
        return FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
            addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }

    /** 页面背景：ui/切片/bg2.png（841×1870），与收藏按钮位置页同一张素材。 */
    private fun pageBackgroundView(): View = object : ImageView(this) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width, (width * 1870f / 841f).toInt())
        }
    }.apply {
        setImageResource(R.drawable.calibration_bg2)
        scaleType = ImageView.ScaleType.FIT_XY
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun headerRow(): View = FrameLayout(this).apply {
        addView(TextView(this@KeepAuthorizationActivity).apply {
            text = "‹"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pressable(
                rounded(Color.argb(170, 10, 32, 66), 22, COLOR_BORDER),
                rounded(Color.argb(220, 24, 62, 110), 22, COLOR_BORDER), 22
            )
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.START or Gravity.CENTER_VERTICAL))
        addView(LinearLayout(this@KeepAuthorizationActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(this@KeepAuthorizationActivity).apply {
                text = "保持授权不丢失"
                textSize = 20f
                setTextColor(TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                if (BuildConfig.DEBUG) setOnClickListener {
                    debugTaps++
                    if (debugTaps >= 5) {
                        debugTaps = 0
                        cyclePreviewBrand()
                    }
                }
            })
            addView(TextView(this@KeepAuthorizationActivity).apply {
                text = "按机型完成设置，避免清理后台后手势失效"
                textSize = 12.5f
                setTextColor(TEXT_MUTED)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    /** 说明卡：紫色渐变，解释授权为什么会丢。 */
    private fun introCard() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            colors = intArrayOf(Color.argb(235, 74, 34, 125), Color.argb(200, 30, 51, 110))
            setStroke(dp(1), Color.argb(190, 202, 66, 255))
        }
        addView(FrameLayout(this@KeepAuthorizationActivity).apply {
            background = rounded(Color.argb(120, 24, 8, 48), 99)
            addView(ImageView(this@KeepAuthorizationActivity).apply {
                setImageResource(R.drawable.ic_keep_shield)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(9), dp(9), dp(9), dp(9))
            }, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(12) })
        addView(LinearLayout(this@KeepAuthorizationActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@KeepAuthorizationActivity).apply {
                text = "为什么授权会丢？"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@KeepAuthorizationActivity).apply {
                text = "无障碍授权由 Android 系统保存，但部分品牌会在清理后台时一并撤销它，导致手势控制失效。根据你的机型完成下面的设置，可以避免日常使用中授权丢失。"
                textSize = 12.5f
                setTextColor(Color.argb(230, 226, 214, 255))
                setLineSpacing(0f, 1.2f)
                setPadding(0, dp(5), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun brandSection(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(16))
        background = rounded(COLOR_SURFACE, 16, COLOR_BORDER)
        val scheme = detectScheme()
        if (scheme == null) {
            addView(TextView(this@KeepAuthorizationActivity).apply {
                text = "通用建议"
                textSize = 16f
                setTextColor(COLOR_CYAN)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(body("当前没有针对你的机型（${Build.MANUFACTURER ?: "未知品牌"}）的专项方案，可先参考通用做法：在系统设置中允许本应用后台运行和自启动，并尽量避免对它使用“强行停止”。").apply {
                setPadding(0, dp(8), 0, 0)
            })
        } else {
            addView(TextView(this@KeepAuthorizationActivity).apply {
                text = "✓  ${scheme.title}"
                textSize = 16f
                setTextColor(COLOR_GREEN)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(body(scheme.intro).apply { setPadding(0, dp(8), 0, dp(4)) })
            scheme.steps.forEachIndexed { index, step ->
                addView(LinearLayout(this@KeepAuthorizationActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(7), 0, dp(7))
                    addView(TextView(this@KeepAuthorizationActivity).apply {
                        text = "${index + 1}"
                        textSize = 12.5f
                        setTextColor(COLOR_CYAN)
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        background = rounded(Color.argb(70, 24, 116, 220), 99, COLOR_BORDER)
                    }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) })
                    addView(body(step), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                })
            }
        }
        addView(TextView(this@KeepAuthorizationActivity).apply {
            text = "去应用详情设置"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pressable(gradientButton(), gradientButtonPressed(), 16)
            setOnClickListener { openAppDetails() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(16) })
    }

    private fun noteCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = rounded(Color.argb(120, 58, 42, 14), 16, Color.rgb(122, 88, 26))
        addView(TextView(this@KeepAuthorizationActivity).apply {
            text = "补充说明"
            textSize = 15f
            setTextColor(COLOR_AMBER)
            typeface = Typeface.DEFAULT_BOLD
        })
        addView(TextView(this@KeepAuthorizationActivity).apply {
            text = "• 主动“强行停止”、清除应用数据或重新安装后，系统仍会要求重新授权，这是 Android 安全机制，任何应用都无法绕过。\n" +
                "• 已支持荣耀 / 华为（真机实测）与小米 / Redmi（依据公开设置路径整理，未实测）。OPPO、vivo 等品牌的专项方案会陆续补充。"
            textSize = 12.5f
            setTextColor(Color.argb(225, 233, 205, 145))
            setLineSpacing(0f, 1.2f)
            setPadding(0, dp(8), 0, 0)
        })
        if (BuildConfig.DEBUG) addView(TextView(this@KeepAuthorizationActivity).apply {
            text = debugPreviewHint()
            textSize = 11.5f
            setTextColor(TEXT_MUTED)
            setPadding(0, dp(10), 0, 0)
        })
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
        Toast.makeText(this, debugPreviewHint(), Toast.LENGTH_SHORT).show()
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

    private fun body(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(TEXT_MUTED)
        setLineSpacing(0f, 1.15f)
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun pressable(fill: Int, pressedFill: Int, radius: Int, stroke: Int? = null) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(pressedFill, radius, stroke))
        addState(intArrayOf(android.R.attr.state_focused), rounded(pressedFill, radius, stroke))
        addState(intArrayOf(), rounded(fill, radius, stroke))
    }

    /** 接受 Drawable 版 pressable，用于渐变按钮等非纯色背景。 */
    private fun pressable(normal: android.graphics.drawable.Drawable, pressed: android.graphics.drawable.Drawable, radius: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(intArrayOf(android.R.attr.state_focused), pressed)
        addState(intArrayOf(), normal)
    }

    private fun gradientButton() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(16).toFloat()
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        colors = intArrayOf(Color.rgb(56, 128, 255), Color.rgb(30, 78, 214))
    }

    private fun gradientButtonPressed() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(16).toFloat()
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        colors = intArrayOf(Color.rgb(40, 104, 220), Color.rgb(22, 58, 170))
    }

    private fun blockMargins(bottom: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val COLOR_BG = Color.rgb(0, 6, 32)
        private val COLOR_SURFACE = Color.argb(210, 7, 31, 67)
        private val COLOR_BORDER = Color.rgb(37, 88, 151)
        private val COLOR_CYAN = Color.rgb(24, 215, 255)
        private val COLOR_GREEN = Color.rgb(21, 224, 187)
        private val COLOR_AMBER = Color.rgb(255, 184, 40)
        private val TEXT_PRIMARY = Color.rgb(235, 242, 252)
        private val TEXT_MUTED = Color.rgb(158, 180, 211)
    }
}
