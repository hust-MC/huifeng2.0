package com.max.huifeng

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt

class ComfortIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // 体感等级
        const val LEVEL_COOL = -2      // 凉
        const val LEVEL_SLIGHTLY_COOL = -1  // 稍凉
        const val LEVEL_JUST_RIGHT = 0   // 合适
        const val LEVEL_SLIGHTLY_WARM = 1   // 稍暖
        const val LEVEL_WARM = 2        // 暖

        // 颜色
        @ColorInt val COLOR_COOL = 0xFF3C86ED.toInt()          // 凉
        @ColorInt val COLOR_SLIGHTLY_COOL = 0xFF45A9A4.toInt() // 稍凉
        @ColorInt val COLOR_JUST_RIGHT = 0xFF4ECD58.toInt()    // 合适
        @ColorInt val COLOR_SLIGHTLY_WARM = 0xFFA5B52E.toInt() // 稍暖
        @ColorInt val COLOR_WARM = 0xFFF19F0D.toInt()          // 暖

        // 文字
        private const val TEXT_COOL = "凉"
        private const val TEXT_SLIGHTLY_COOL = "稍凉"
        private const val TEXT_JUST_RIGHT = "合适"
        private const val TEXT_SLIGHTLY_WARM = "稍暖"
        private const val TEXT_WARM = "暖"

        private fun getColor(level: Int): Int = when (level) {
            LEVEL_COOL -> COLOR_COOL
            LEVEL_SLIGHTLY_COOL -> COLOR_SLIGHTLY_COOL
            LEVEL_JUST_RIGHT -> COLOR_JUST_RIGHT
            LEVEL_SLIGHTLY_WARM -> COLOR_SLIGHTLY_WARM
            LEVEL_WARM -> COLOR_WARM
            else -> COLOR_JUST_RIGHT
        }

        private fun getText(level: Int): String = when (level) {
            LEVEL_COOL -> TEXT_COOL
            LEVEL_SLIGHTLY_COOL -> TEXT_SLIGHTLY_COOL
            LEVEL_JUST_RIGHT -> TEXT_JUST_RIGHT
            LEVEL_SLIGHTLY_WARM -> TEXT_SLIGHTLY_WARM
            LEVEL_WARM -> TEXT_WARM
            else -> TEXT_JUST_RIGHT
        }
    }

    // 描边画笔
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6.75f
    }

    // 文字画笔
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var level: Int = LEVEL_JUST_RIGHT

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (minOf(width, height) / 2f) - strokePaint.strokeWidth

        val color = getColor(level)

        // 画圆圈（描边）
        strokePaint.color = color
        canvas.drawCircle(centerX, centerY, radius, strokePaint)

        // 画文字
        textPaint.color = color
        textPaint.textSize = radius * 0.7f

        val text = getText(level)
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, centerX, textY, textPaint)
    }

    fun setComfortLevel(level: Int) {
        this.level = level
        invalidate()
    }
}
