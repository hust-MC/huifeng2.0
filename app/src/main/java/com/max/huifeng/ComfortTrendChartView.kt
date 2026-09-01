package com.max.huifeng

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * 总体舒适感趋势曲线图（30 分钟窗口，每 5 秒一个数据点）。
 *
 * 数据模型：滑动窗口最多 360 个点。每次 setPoints() 触发重绘。
 * Y 轴：-2..2（凉/稍凉/合适/稍暖/暖），画 5 个色带作为背景层。
 * X 轴：-30min..now，右端为最新数据点。
 */
class ComfortTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 曲线点集合（按时间从旧到新），每个点是体感档位 -2..2 */
    private val points = ArrayDeque<Float>()

    /** 最多保留的点数：30 分钟 / 5 秒 = 360 */
    var maxPoints: Int = 360
        set(value) { field = max(60, value) }

    /** Y 轴可视范围（默认 -2.5 .. 2.5，给曲线留点 padding） */
    private val yMin = -2.5f
    private val yMax = 2.5f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        strokeWidth = 1f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        textSize = 22f
    }
    private val labelRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A0FFFFFF")
        textSize = 22f
        textAlign = Paint.Align.RIGHT
    }
    private val labelLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A0FFFFFF")
        textSize = 22f
        textAlign = Paint.Align.LEFT
    }
    private val path = Path()

    /** 折线图绘图区（去掉坐标轴留白） */
    private val padLeft = 56f
    private val padTop = 24f
    private val padRight = 56f
    private val padBottom = 36f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val plotLeft = padLeft
        val plotRight = w - padRight
        val plotTop = padTop
        val plotBottom = h - padBottom

        drawColorBands(canvas, plotLeft, plotTop, plotRight, plotBottom)
        drawGrid(canvas, plotLeft, plotTop, plotRight, plotBottom)
        drawAxisLabels(canvas, plotLeft, plotTop, plotRight, plotBottom)
        drawLine(canvas, plotLeft, plotTop, plotRight, plotBottom)
    }

    /** 5 个色带背景（凉/稍凉/合适/稍暖/暖），从下到上叠 */
    private fun drawColorBands(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        // 色带按 Y 轴分段：2..1.5 / 1.5..0.5 / 0.5..-0.5 / -0.5..-1.5 / -1.5..-2
        // 使用渐变透明叠加而非整段实色，避免盖过前景曲线
        val bands = listOf(
            -2.5f to -1.5f to Color.parseColor("#1A3C86ED"),   // 凉
            -1.5f to -0.5f to Color.parseColor("#1A45A9A4"),   // 稍凉
            -0.5f to 0.5f to Color.parseColor("#1A4ECD58"),    // 合适
            0.5f to 1.5f to Color.parseColor("#1AA5B52E"),     // 稍暖
            1.5f to 2.5f to Color.parseColor("#1AF19F0D")      // 暖
        )
        bands.forEach { (range, color) ->
            val (lo, hi) = range
            val yTop = yToPixel(hi, t, b)
            val yBottom = yToPixel(lo, t, b)
            bgPaint.color = color
            canvas.drawRect(l, yTop, r, yBottom, bgPaint)
        }
    }

    private fun drawGrid(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        // 水平网格线：y = -2, -1, 0, 1, 2
        for (i in -2..2) {
            val y = yToPixel(i.toFloat(), t, b)
            canvas.drawLine(l, y, r, y, gridPaint)
        }
        // 竖直网格线：5 等分
        for (i in 0..5) {
            val x = l + (r - l) * i / 5f
            canvas.drawLine(x, t, x, b, gridPaint)
        }
    }

    private fun drawAxisLabels(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        // Y 轴档位标签
        val yLabels = listOf(2 to "暖", 1 to "稍暖", 0 to "合适", -1 to "稍凉", -2 to "凉")
        yLabels.forEach { (v, txt) ->
            val y = yToPixel(v.toFloat(), t, b)
            val baseline = y + textPaint.textSize / 3f
            canvas.drawText(txt, l - 8f, baseline, labelRightPaint)
        }
        // X 轴时间标签（-30min 到 now）
        val xLabels = listOf(0f to "-30min", 1f to "-20min", 2f to "-10min", 3f to "now")
        xLabels.forEach { (frac, txt) ->
            val x = l + (r - l) * frac
            val y = b + textPaint.textSize + 6f
            canvas.drawText(txt, x - 18f, y, labelLeftPaint)
        }
    }

    private fun drawLine(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        if (points.size < 2) return
        val plotW = r - l
        val plotH = b - t
        val pointCount = points.size
        path.reset()
        var first = true
        val values = points.toList()
        values.forEachIndexed { idx, v ->
            // 旧点靠左，新点靠右
            val x = l + plotW * idx / (maxPoints - 1).toFloat()
            val y = t + plotH * (1f - (v - yMin) / (yMax - yMin))
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
    }

    /** Y 轴档位 -> 像素坐标（Y 轴向下增长，所以翻一下） */
    private fun yToPixel(v: Float, t: Float, b: Float): Float {
        val plotH = b - t
        return t + plotH * (1f - (v - yMin) / (yMax - yMin))
    }

    /** 添加新点（最新），超出窗口则丢最旧的 */
    fun addPoint(level: Float) {
        points.addLast(level.coerceIn(yMin, yMax))
        while (points.size > maxPoints) points.removeFirst()
        invalidate()
    }

    /** 整体替换（用于一次性灌入历史数据） */
    fun setPoints(levels: List<Float>) {
        points.clear()
        levels.takeLast(maxPoints).forEach { points.addLast(it.coerceIn(yMin, yMax)) }
        invalidate()
    }

    fun clear() {
        points.clear()
        invalidate()
    }

    /** 当前数据点数（用于调试/状态显示） */
    fun size(): Int = points.size

    /** 当前最新点（用于同步右侧"实时档位"显示） */
    fun latest(): Float? = points.lastOrNull()
}
