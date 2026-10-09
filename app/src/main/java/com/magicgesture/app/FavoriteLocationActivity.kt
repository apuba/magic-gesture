package com.magicgesture.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 收藏按钮位置页：从“手势练习与校准”拆出的独立页面，只负责查看与删除已标定的收藏按钮位置。
 * 视觉沿用深色霓虹风格与 ui/切片/bg2.png 背景；重新定义仍需回到目标 App 首次触发“收藏当前内容”。
 */
class FavoriteLocationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val profiles = GesturePreferences.favoriteProfiles(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(36))
        }

        content.addView(headerRow(), blockMargins(20))
        content.addView(introCard(), blockMargins(20))

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title("已保存的位置").apply { textSize = 17f }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(countChip(profiles.size))
        }, blockMargins(4))
        content.addView(body("按应用与横竖屏分别保存；同一应用的竖屏与横屏会各占一条。").apply {
            setPadding(0, dp(3), 0, dp(12))
        })

        if (profiles.isEmpty()) {
            content.addView(emptyCard())
        } else {
            profiles.forEach { profile -> content.addView(profileRow(profile), blockMargins(8)) }
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFillViewport = true
            addView(FrameLayout(this@FavoriteLocationActivity).apply {
                addView(pageBackgroundView(), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
                addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            })
        }
        return FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
            addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }

    /** 页面背景：ui/切片/bg2.png（841×1870），与其他设置页同一张素材。 */
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

    private fun headerRow() = FrameLayout(this).apply {
        addView(TextView(this@FavoriteLocationActivity).apply {
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
        addView(LinearLayout(this@FavoriteLocationActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(this@FavoriteLocationActivity).apply {
                text = "收藏按钮位置"
                textSize = 20f
                setTextColor(TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })

        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    /** 说明卡：紫色渐变，解释收藏按钮位置是什么、怎么产生。 */
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
        addView(FrameLayout(this@FavoriteLocationActivity).apply {
            background = rounded(Color.argb(120, 24, 8, 48), 99)
            addView(ImageView(this@FavoriteLocationActivity).apply {
                setImageResource(R.drawable.ic_fav_heart)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(9), dp(9), dp(9), dp(9))
            }, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(12) })
        addView(LinearLayout(this@FavoriteLocationActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@FavoriteLocationActivity).apply {
                text = "什么是收藏按钮位置？"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@FavoriteLocationActivity).apply {
                text = "在第三方 App 中首次触发“收藏当前内容”时，按提示标记收藏按钮。这里可以查看或删除已保存的位置；重新定义请回到目标 App 再次触发。"
                textSize = 12.5f
                setTextColor(Color.argb(230, 226, 214, 255))
                setLineSpacing(0f, 1.2f)
                setPadding(0, dp(5), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun countChip(size: Int) = TextView(this).apply {
        text = "$size"
        textSize = 13f
        setTextColor(COLOR_CYAN)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(11), dp(3), dp(11), dp(3))
        background = rounded(Color.argb(70, 24, 116, 220), 99, COLOR_BORDER)
    }

    private fun emptyCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(18), dp(16), dp(18))
        background = rounded(COLOR_SURFACE, 16, COLOR_BORDER)
        addView(TextView(this@FavoriteLocationActivity).apply {
            text = "暂未定义任何应用的收藏按钮位置"
            textSize = 15f
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        })
        addView(body("在目标 App 中做出 OK 手势即可开始标记；标记完成后，这里会出现对应的一条记录。").apply {
            setPadding(0, dp(6), 0, 0)
        })
    }

    /** 一条已保存位置：应用图标 + 名称 + 横竖屏状态 + 保存时间 + 删除。 */
    private fun profileRow(profile: FavoriteButtonProfile) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(10), dp(10))
        background = rounded(COLOR_SURFACE, 16, COLOR_BORDER)
        addView(ImageView(this@FavoriteLocationActivity).apply {
            setImageDrawable(appIcon(profile.packageName))
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = profile.appLabel
            background = rounded(Color.argb(120, 13, 42, 86), 12)
            clipToOutline = true
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
        addView(LinearLayout(this@FavoriteLocationActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@FavoriteLocationActivity).apply {
                text = profile.appLabel
                textSize = 15f
                setTextColor(TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@FavoriteLocationActivity).apply {
                text = "${if (profile.portrait) "竖屏" else "横屏"} · 已定义"
                textSize = 12.5f
                setTextColor(COLOR_CYAN)
                setPadding(0, dp(4), 0, 0)
            })
            addView(TextView(this@FavoriteLocationActivity).apply {
                text = savedAtText(profile.updatedAt)
                textSize = 11.5f
                setTextColor(TEXT_MUTED)
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@FavoriteLocationActivity).apply {
            text = "删除"
            textSize = 13.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            // 设计稿为实心粉红按钮，按压加深。
            background = pressable(Color.rgb(225, 41, 96), Color.rgb(188, 26, 74), 12)
            setOnClickListener { confirmDelete(profile) }
        }, LinearLayout.LayoutParams(dp(72), dp(40)).apply { marginStart = dp(8) })
    }

    private fun confirmDelete(profile: FavoriteButtonProfile) {
        AlertDialog.Builder(this)
            .setTitle("删除收藏位置")
            .setMessage("确定删除 ${profile.appLabel} 的${if (profile.portrait) "竖屏" else "横屏"}收藏位置吗？删除后需要重新标记才能使用。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                GesturePreferences.deleteFavoriteProfile(this, profile.packageName, profile.portrait)
                Toast.makeText(this, "已删除该收藏位置", Toast.LENGTH_SHORT).show()
                // 原地重建，列表与计数立即反映删除结果。
                setContentView(buildContent())
            }
            .show()
    }

    /** 应用图标取系统安装图标；已卸载或取不到时退回中性占位色块。 */
    private fun appIcon(packageName: String) = try {
        packageManager.getApplicationIcon(packageName)
    } catch (e: Exception) {
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(Color.argb(120, 24, 62, 110))
        }
    }

    private fun savedAtText(updatedAt: Long): String {
        if (updatedAt <= 0L) return "保存时间未记录"
        return "保存于 ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(updatedAt))}"
    }

    private fun title(value: String) = TextView(this).apply {
        text = value
        textSize = 17f
        setTextColor(TEXT_PRIMARY)
        typeface = Typeface.DEFAULT_BOLD
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

    /** 接受 Drawable 版 pressable，用于已描边的圆角底等自定义背景。 */
    private fun pressable(normal: android.graphics.drawable.Drawable, pressed: android.graphics.drawable.Drawable, radius: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(intArrayOf(android.R.attr.state_focused), pressed)
        addState(intArrayOf(), normal)
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
        private val TEXT_PRIMARY = Color.rgb(235, 242, 252)
        private val TEXT_MUTED = Color.rgb(158, 180, 211)
    }
}
