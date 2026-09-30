package com.magicgesture.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class CalibrationActivity : Activity() {
    private var selectedSensitivity = "normal"
    private lateinit var lowButton: Button
    private lateinit var normalButton: Button
    private lateinit var highButton: Button
    private lateinit var reverseSwitch: Switch
    private lateinit var feedbackSwitch: Switch
    private lateinit var cursorSwitch: Switch
    private lateinit var clickSwitch: Switch
    private lateinit var scrollSwitch: Switch
    private lateinit var backSwitch: Switch
    private lateinit var homeSwitch: Switch
    private lateinit var screenshotSwitch: Switch
    private lateinit var recentsSwitch: Switch
    private lateinit var likeSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(37, 99, 235)
        selectedSensitivity = GesturePreferences.sensitivity(this)
        setContentView(buildContent())
        refreshSensitivityButtons()
    }

    private fun buildContent(): ScrollView {
        val savedFeatures = GesturePreferences.features(this)
        return ScrollView(this).apply {
        setBackgroundColor(Color.rgb(248, 250, 252))
        addView(LinearLayout(this@CalibrationActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(36))

            addView(Button(this@CalibrationActivity).apply {
                text = "←  返回首页"
                textSize = 14f
                setTextColor(Color.rgb(37, 99, 235))
                isAllCaps = false
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                background = pressable(Color.WHITE, Color.rgb(219, 234, 254), 14, Color.rgb(191, 219, 254))
                stateListAnimator = null
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                bottomMargin = dp(14)
            })

            addView(TextView(this@CalibrationActivity).apply {
                text = "手势练习与校准"
                textSize = 27f
                setTextColor(Color.rgb(30, 41, 59))
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(body("选择适合你的识别灵敏度。设置会保存，并在下次启动手势控制时生效。").apply {
                setPadding(0, dp(6), 0, dp(18))
            })

            addView(section("识别灵敏度", "如果经常识别不到，选择“灵敏”；如果容易误触，选择“稳定”。"))
            addView(LinearLayout(this@CalibrationActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                lowButton = sensitivityButton("稳定\n减少误触", "stable")
                normalButton = sensitivityButton("标准\n推荐", "normal")
                highButton = sensitivityButton("灵敏\n更易触发", "high")
                addView(lowButton, weightedMargins(end = 6))
                addView(normalButton, weightedMargins(start = 3, end = 3))
                addView(highButton, weightedMargins(start = 6))
            }, blockMargins(12))

            reverseSwitch = settingSwitch(
                "反转左右方向",
                "当实际左右滑动与手势方向相反时开启。光标左右方向也会同步反转。",
                GesturePreferences.reverseHorizontal(this@CalibrationActivity)
            )
            addView(reverseSwitch, blockMargins(12))

            feedbackSwitch = settingSwitch(
                "显示识别反馈",
                "在屏幕顶部显示动作名称，以及 V 字保持进度。",
                GesturePreferences.feedbackEnabled(this@CalibrationActivity)
            )
            addView(feedbackSwitch, blockMargins(22))

            addView(title("手势功能开关"))
            addView(body("测试时可只开启一个手势，关闭的功能不会参与判断，也不会影响其他动作。").apply {
                setPadding(0, dp(5), 0, dp(12))
            })
            cursorSwitch = featureSwitch("食指光标", "显示并移动青色光标。", savedFeatures.cursor)
            clickSwitch = featureSwitch("食指弯曲点击", "只伸出食指稳定约 0.2 秒，弯曲后在 1 秒内重新伸直执行点击。", savedFeatures.click)
            scrollSwitch = featureSwitch("上下滚动", "可使用水平食指上下挑动，或将食指、中指、无名指和小指并拢后整只手上下挥动；拇指不限。", savedFeatures.scroll)
            backSwitch = featureSwitch("向左滑动", "食指、中指、无名指和小指并拢后左挥，拇指不限；也可竖起食指后整只手左移。", savedFeatures.back)
            homeSwitch = featureSwitch("向右滑动", "食指、中指、无名指和小指并拢后右挥，拇指不限；也可竖起食指后整只手右移。", savedFeatures.home)
            screenshotSwitch = featureSwitch("五指张开组合截图", "五指明显分开并保持，按提示握拳，再次张开五指完成截图。", savedFeatures.screenshot)
            recentsSwitch = featureSwitch("V 字最近任务", "V 字保持 2 秒打开最近任务。", savedFeatures.recents)
            likeSwitch = featureSwitch("比心双击点赞", "拇指和食指交叉形成小爱心，保持约 0.6 秒后双击视频。", savedFeatures.like)
            listOf(cursorSwitch, clickSwitch, scrollSwitch, backSwitch, homeSwitch, screenshotSwitch, recentsSwitch, likeSwitch).forEach {
                addView(it, blockMargins(8))
            }
            addView(body("提示：如果只测试向下滑动，可关闭其余六项，保存后重新启动手势控制。"), blockMargins(22))

            addView(title("练习顺序"))
            addView(body("建议按顺序逐项测试。一次只做一个动作，触发后有 2 秒保护时间；先收起手指或把手移出画面。"))
            addView(practiceCard(R.drawable.gesture_point, "1  光标与点击", "食指移动光标；稳定约 0.2 秒后弯曲食指，再在 1 秒内重新伸直。"), blockMargins(10))
            addView(practiceCard(R.drawable.gesture_four_fingers_together, "2  方向动作", "水平食指挑动，或将食指、中指、无名指和小指并拢后挥动；拇指不限，四指分开时不触发。"), blockMargins(10))
            addView(practiceCard(R.drawable.gesture_v, "3  V 字计时", "保持 V 字 2 秒，观察顶部反馈从 0% 增长到 100%。"), blockMargins(10))
            addView(practiceCard(R.drawable.gesture_finger_heart, "4  比心双击点赞", "拇指与食指交叉形成小爱心，其余三指自然收拢并稳定保持约 0.6 秒。"), blockMargins(10))
            addView(practiceCard(R.drawable.gesture_palm, "5  截图组合", "五指明显分开并保持；看到提示后握拳，再次五指分开并保持完成截图。"), blockMargins(20))

            addView(Button(this@CalibrationActivity).apply {
                text = "保存设置并返回"
                textSize = 16f
                setTextColor(Color.WHITE)
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                background = pressable(Color.rgb(37, 99, 235), Color.rgb(29, 78, 216), 16)
                stateListAnimator = null
                setOnClickListener {
                    GesturePreferences.save(
                        this@CalibrationActivity,
                        selectedSensitivity,
                        reverseSwitch.isChecked,
                        feedbackSwitch.isChecked,
                        GestureFeatureConfig(
                            cursor = cursorSwitch.isChecked,
                            click = clickSwitch.isChecked,
                            scroll = scrollSwitch.isChecked,
                            back = backSwitch.isChecked,
                            home = homeSwitch.isChecked,
                            screenshot = screenshotSwitch.isChecked,
                            recents = recentsSwitch.isChecked,
                            like = likeSwitch.isChecked
                        )
                    )
                    Toast.makeText(this@CalibrationActivity, "设置已保存，下次启动手势控制时生效", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)))
        })
        }
    }

    private fun sensitivityButton(label: String, value: String) = Button(this).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        setOnClickListener { selectedSensitivity = value; refreshSensitivityButtons() }
    }

    private fun refreshSensitivityButtons() {
        listOf("stable" to lowButton, "normal" to normalButton, "high" to highButton).forEach { (value, button) ->
            val selected = value == selectedSensitivity
            button.setTextColor(if (selected) Color.WHITE else Color.rgb(51, 65, 85))
            button.background = rounded(if (selected) Color.rgb(37, 99, 235) else Color.WHITE, 14,
                if (selected) Color.rgb(37, 99, 235) else Color.rgb(203, 213, 225))
        }
    }

    private fun settingSwitch(name: String, description: String, checked: Boolean) = Switch(this).apply {
        text = "$name\n$description"
        textSize = 14f
        setTextColor(Color.rgb(30, 41, 59))
        setLineSpacing(0f, 1.15f)
        isChecked = checked
        setPadding(dp(16), dp(13), dp(12), dp(13))
        background = rounded(Color.WHITE, 16, Color.rgb(226, 232, 240))
    }

    private fun featureSwitch(name: String, description: String, checked: Boolean) = settingSwitch(name, description, checked).apply {
        minHeight = dp(56)
    }

    private fun practiceCard(image: Int, heading: String, description: String) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(10), dp(14), dp(10))
        background = rounded(Color.WHITE, 18, Color.rgb(226, 232, 240))
        addView(ImageView(this@CalibrationActivity).apply {
            setImageResource(image)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = heading
        }, LinearLayout.LayoutParams(dp(78), dp(78)))
        addView(LinearLayout(this@CalibrationActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(title(heading).apply { textSize = 16f })
            addView(body(description).apply { setPadding(0, dp(5), 0, 0) })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun section(heading: String, description: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(title(heading))
        addView(body(description).apply { setPadding(0, dp(5), 0, 0) })
    }

    private fun title(value: String) = TextView(this).apply {
        text = value
        textSize = 19f
        setTextColor(Color.rgb(30, 41, 59))
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun body(value: String) = TextView(this).apply {
        text = value
        textSize = 13.5f
        setTextColor(Color.rgb(71, 85, 105))
        setLineSpacing(0f, 1.15f)
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun pressable(fill: Int, pressedFill: Int, radius: Int, stroke: Int? = null) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(pressedFill, radius, stroke))
        addState(intArrayOf(android.R.attr.state_focused), rounded(pressedFill, radius, stroke))
        addState(intArrayOf(), rounded(fill, radius, stroke))
    }

    private fun weightedMargins(start: Int = 0, end: Int = 0) = LinearLayout.LayoutParams(0, dp(62), 1f).apply {
        marginStart = dp(start)
        marginEnd = dp(end)
    }

    private fun blockMargins(bottom: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
