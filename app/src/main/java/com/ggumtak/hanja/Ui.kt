package com.ggumtak.hanja

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.ScrollView
import android.widget.TextView

/** Colours for the system's light or dark mode. */
class Palette(ctx: Context) {
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val card = if (dark) Color.rgb(0x22, 0x22, 0x22) else Color.WHITE
    val text = if (dark) Color.rgb(0xEE, 0xEE, 0xEE) else Color.rgb(0x16, 0x16, 0x16)
    val sub = if (dark) Color.rgb(0xA8, 0xA8, 0xA8) else Color.rgb(0x66, 0x66, 0x66)
    val accent = if (dark) Color.rgb(0xF0, 0x8A, 0x70) else Color.rgb(0xB8, 0x48, 0x30)
    val line = if (dark) Color.rgb(0x3A, 0x3A, 0x3A) else Color.rgb(0xE2, 0xE2, 0xE2)
    val button = if (dark) Color.rgb(0x33, 0x33, 0x33) else Color.rgb(0xF0, 0xEE, 0xEA)
}

fun Context.dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

fun Context.label(text: CharSequence, sp: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    setTextColor(color)
    if (bold) paint.isFakeBoldText = true
}

fun rounded(color: Int, radiusPx: Int, strokePx: Int = 0, strokeColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx.toFloat()
        if (strokePx > 0) setStroke(strokePx, strokeColor)
    }

fun Context.selectableBackground(): android.graphics.drawable.Drawable? {
    val tv = TypedValue()
    theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
    return if (tv.resourceId != 0) getDrawable(tv.resourceId) else null
}

/** A ScrollView never taller than [fraction] of the screen as it is now (rotation does not recreate the card). */
class CappedScrollView(ctx: Context, private val fraction: Float) : ScrollView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxPx = (resources.displayMetrics.heightPixels * fraction).toInt()
        val size = View.MeasureSpec.getSize(heightMeasureSpec)
        val cap = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) maxPx else minOf(size, maxPx)
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST))
    }
}
