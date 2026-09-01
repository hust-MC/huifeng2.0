package com.max.huifeng

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.sqrt

/**
 * 胸前椭圆色斑：在 onDraw 里直接画一个带径向渐变的椭圆。
 *
 * 严格对齐 Figma 设计规范（以蓝色 case 为基准，其他色块共用此 View，只换颜色）：
 * - Frame Opacity: 50%（调用处设置 View.alpha）
 * - Frame Rotation: -6.9°（调用处设置 View.rotation）
 * - Blend Mode: Hard light（API 29+；低版本回退到默认 SRC_OVER）
 * - Radial Gradient: 同色，中心 90% alpha、边缘 10% alpha；中心居中 (0.5, 0.5)；半径 = View 对角线一半
 *
 * 不依赖 GradientDrawable，规避某些车机上 OVAL + RADIAL_GRADIENT 不渲染的 bug。
 */
class SeatOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        /** 渐变中心 alpha（90%） */
        private const val CENTER_ALPHA: Int = 0xE6
        /** 渐变中段 alpha（50%），让过渡更柔 */
        private const val MID_ALPHA: Int = 0x80
        /** 渐变中段位置（占整体半径比例） */
        private const val MID_STOP: Float = 0.5f
        /** 渐变边缘 alpha（0%），羽化到完全透明 */
        private const val EDGE_ALPHA: Int = 0x00
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var baseColor: Int = Color.TRANSPARENT
    // shader 缓存：尺寸/颜色变化时才重建
    private var cachedW: Float = 0f
    private var cachedH: Float = 0f
    private var cachedColor: Int = Color.TRANSPARENT

    init {
        // Figma "Hard light" 混合：仅 API 29+ 支持，低版本回退到默认 SRC_OVER（视觉略有差异，可接受）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            applyFigmaHardLightBlend()
        }
    }

    /**
     * Figma 的 Hard light 混合模式（API 29+）。
     *
     * 实现要点：
     * - View 没有 setBlendMode（只有 setBackground/ForegroundTintBlendMode），不能用 View 层混合。
     * - 把 View 设为 LAYER_TYPE_NONE，让 onDraw 直接画到父 canvas 上，
     *   这样 Paint.blendMode = HARD_LIGHT 才能跟下面的 seat figure 像素做硬光混合。
     * - LAYER_TYPE_NONE 会关闭硬件加速，但本 View 区域小、shader 缓存，性能可接受。
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun applyFigmaHardLightBlend() {
        setLayerType(LAYER_TYPE_NONE, null)
        paint.blendMode = BlendMode.HARD_LIGHT
    }

    /**
     * 设置体感等级；null 表示清空色斑。
     */
    fun setComfortLevel(level: Int?) {
        baseColor = if (level == null) Color.TRANSPARENT else ComfortIconView.getColor(level)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (baseColor == Color.TRANSPARENT) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 尺寸或颜色变了，重建 shader
        if (paint.shader == null || w != cachedW || h != cachedH || baseColor != cachedColor) {
            cachedW = w
            cachedH = h
            cachedColor = baseColor
            // 渐变中心居中（对齐 Figma 50%/50%）
            val cx = w * 0.5f
            val cy = h * 0.5f
            // 半径 = View 中心到角点的距离，对齐 Figma 径向渐变边界 = 矩形角点
            val radius = sqrt(w * w + h * h) / 2f
            val rgb = baseColor and 0x00FFFFFF
            val centerColor = rgb or (CENTER_ALPHA shl 24)
            val midColor = rgb or (MID_ALPHA shl 24)
            val edgeColor = rgb or (EDGE_ALPHA shl 24)
            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(centerColor, midColor, edgeColor),  // 90% → 50% → 0%，羽化到全透明
                floatArrayOf(0f, MID_STOP, 1f),
                Shader.TileMode.CLAMP
            )
        }

        // 整体透明度由 View.alpha 控制（Figma 50%）
        canvas.drawOval(0f, 0f, w, h, paint)
    }
}
