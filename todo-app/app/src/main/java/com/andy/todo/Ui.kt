package com.andy.todo

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    fun dp(c: Context, v: Number): Int = Math.round(v.toFloat() * c.resources.displayMetrics.density)

    fun isNight(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun rounded(color: Int, radiusPx: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
    }

    fun dot(c: Context, color: Int, sizeDp: Int = 10) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setSize(dp(c, sizeDp), dp(c, sizeDp))
    }

    /** A pill-shaped selectable chip. */
    fun chip(c: Context, text: String, selected: Boolean, dotColor: Int? = null, onClick: () -> Unit): TextView =
        TextView(c).apply {
            this.text = text
            textSize = 14f
            setPadding(dp(c, 16), dp(c, 9), dp(c, 16), dp(c, 9))
            setTextColor(if (selected) Color.WHITE else c.getColor(R.color.text))
            background = rounded(c.getColor(if (selected) R.color.primary else R.color.field), dp(c, 20).toFloat())
            if (dotColor != null) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    dot(c, if (selected) Color.WHITE else dotColor), null, null, null)
                compoundDrawablePadding = dp(c, 8)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(c, 8) }
            setOnClickListener { onClick() }
        }

    data class Bars(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /**
     * Draws the activity behind the system bars (required when targeting Android 15+) and
     * reports the bar sizes so content can pad itself. Older versions keep the classic layout.
     */
    fun edgeToEdge(a: Activity, root: View, apply: (Bars) -> Unit) {
        val w = a.window
        val light = !isNight(a)
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false)
            @Suppress("DEPRECATION")
            w.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            w.navigationBarColor = Color.TRANSPARENT
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            w.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
            root.setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                apply(Bars(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom)))
                insets
            }
            root.requestApplyInsets()
        } else {
            if (Build.VERSION.SDK_INT >= 27 && light) {
                @Suppress("DEPRECATION")
                w.decorView.systemUiVisibility =
                    w.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            apply(Bars(0, 0, 0, 0))
        }
    }
}
