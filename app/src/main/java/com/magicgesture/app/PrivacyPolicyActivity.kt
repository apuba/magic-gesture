package com.magicgesture.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Offline, in-app copy of the public privacy policy. It never opens a network connection. */
class PrivacyPolicyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(72, 69, 198)
        window.navigationBarColor = Color.rgb(246, 245, 255)
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(34))
        }

        content.addView(actionButton("←  返回") { finish() }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(44)
        ).apply { bottomMargin = dp(8) })

        content.addView(TextView(this).apply {
            text = "魔法手势隐私政策"
            textSize = 26f
            setTextColor(Color.rgb(31, 31, 55))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), dp(12), 0, dp(4))
        })
        content.addView(TextView(this).apply {
            text = "更新日期：2026年10月4日  ·  生效日期：2026年10月4日"
            textSize = 12f
            setTextColor(Color.rgb(104, 102, 126))
            setPadding(dp(2), 0, 0, dp(16))
        })
        content.addView(actionButton("查看在线版本") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(46)
        ).apply { bottomMargin = dp(14) })
        content.addView(TextView(this).apply {
            text = resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
            textSize = 14f
            setTextColor(Color.rgb(38, 37, 59))
            setLineSpacing(0f, 1.18f)
            setTextIsSelectable(true)
            setPadding(dp(16), dp(16), dp(16), dp(18))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(229, 227, 246))
            }
        })

        return ScrollView(this).apply {
            setBackgroundColor(Color.rgb(246, 245, 255))
            isFillViewport = true
            addView(content)
        }
    }

    private fun actionButton(textValue: String, action: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 15f
        setTextColor(Color.rgb(83, 80, 214))
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(Color.rgb(232, 230, 255)))
            addState(intArrayOf(android.R.attr.state_focused), rounded(Color.rgb(232, 230, 255)))
            addState(intArrayOf(), rounded(Color.WHITE))
        }
        stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), Color.rgb(204, 201, 239))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PRIVACY_POLICY_URL = "https://magicgesture.mt4000.com/privacy-policy.html"
    }
}
