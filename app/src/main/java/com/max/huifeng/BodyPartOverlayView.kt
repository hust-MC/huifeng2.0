package com.max.huifeng

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * 在 3D 人像上叠加 12 个身体部位色斑。
 *
 * 每个部位位置由 [BodyPart] 内部坐标决定（相对人像 0..1），
 * 颜色由传入的档位映射（-2..2）。
 *
 * 与 SeatOverlayView 不同：
 *  - 12 个独立椭圆，每个部位单独算位置/半径
 *  - 复用渐变（90% 中心 → 50% 中段 → 0% 边缘）保持羽化效果一致
 */
class BodyPartOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 各部位档位（缺数据则不画） */
    private var partLevels: Map<BodyPart, Int> = emptyMap()

    // 渐变常量（与 SeatOverlayView 对齐）
    companion object {
        private const val CENTER_ALPHA = 0xE6
        private const val MID_ALPHA = 0x80
        private const val MID_STOP = 0.5f
        private const val EDGE_ALPHA = 0x00
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (partLevels.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 参考短边作为半径基准（保持比例一致）
        val baseRadius = min(w, h) * 0.5f

        partLevels.forEach { (part, level) ->
            val cx = part.relX * w
            val cy = part.relY * h
            val r = part.relRadius * baseRadius * 2f  // *2 因为 baseRadius 是 min/2

            val baseColor = ComfortIconView.getColor(level)
            val rgb = baseColor and 0x00FFFFFF
            val centerColor = rgb or (CENTER_ALPHA shl 24)
            val midColor = rgb or (MID_ALPHA shl 24)
            val edgeColor = rgb or (EDGE_ALPHA shl 24)
            paint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(centerColor, midColor, edgeColor),
                floatArrayOf(0f, MID_STOP, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, paint)
        }
    }

    /**
     * 设置所有部位的体感档位（覆盖）。
     * 缺数据的部位不会绘制。
     */
    fun setParts(levels: Map<BodyPart, Int>) {
        this.partLevels = levels
        invalidate()
    }

    /** 清空所有色斑 */
    fun clear() {
        this.partLevels = emptyMap()
        invalidate()
    }
}
