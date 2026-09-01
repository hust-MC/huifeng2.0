package com.max.huifeng

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.geely.aicarcontrolsdk.AiCarControlSDK
import com.geely.aicarcontrolsdk.ComfortLevelCallback
import com.geely.aicarcontrolsdk.Position
import org.json.JSONObject
import kotlin.math.exp

/**
 * 座位体感详情页（1-1 详情页）
 *
 * 框架版实现：左侧 2x2 座位切换 + 中央 3D 人像 + 右侧档位/温/湿/PMV/PPD 卡片。
 * 右侧卡片内容由当前选中座位的体感数据驱动：
 *   - 顶部大圆：当前座位的档位（凉/稍凉/合适/稍暖/暖），颜色取 ComfortIconView 配色
 *   - 标题："{主驾|副驾|后排左|后排右} · {档位中文}"
 *   - 英文胶囊：COLD / COOL / NEUTRAL / WARM / HOT，颜色随档位变化
 *   - 四宫格：温度 / 湿度 / PMV / PPD（当前为 mock，等 SDK HVAC 回调/字段对齐后再接真实数据）
 */
class SeatDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SeatDetail"

        /** 入口参数：被点击的 Position */
        const val EXTRA_POSITION = "extra_position"

        // 与 BodyFeelActivity 4 座车型一致的可见座位顺序（左上→右上→左下→右下）
        private val POSITIONS = listOf(
            Position.ROW_1_LEFT,
            Position.ROW_1_RIGHT,
            Position.ROW_2_RIGHT,
            Position.ROW_3_LEFT
        )

        private val POSITION_NAMES = mapOf(
            Position.ROW_1_LEFT to "主驾",
            Position.ROW_1_RIGHT to "副驾",
            Position.ROW_2_RIGHT to "后排右",
            Position.ROW_3_LEFT to "后排左"
        )

        // 每个 position 默认一个档位，用于 mock / 首次进入无 SDK 数据时的占位
        private val MOCK_BY_POSITION = mapOf(
            Position.ROW_1_LEFT to MockSeatData(
                level = ComfortIconView.LEVEL_COOL,
                temperature = 22.0, humidity = 38.0, pmv = -0.6
            ),
            Position.ROW_1_RIGHT to MockSeatData(
                level = ComfortIconView.LEVEL_SLIGHTLY_COOL,
                temperature = 23.5, humidity = 41.0, pmv = -0.2
            ),
            Position.ROW_2_RIGHT to MockSeatData(
                level = ComfortIconView.LEVEL_JUST_RIGHT,
                temperature = 25.0, humidity = 45.0, pmv = 0.0
            ),
            Position.ROW_3_LEFT to MockSeatData(
                level = ComfortIconView.LEVEL_SLIGHTLY_WARM,
                temperature = 26.5, humidity = 50.0, pmv = 0.2
            )
        )
    }

    private lateinit var glSeatThumbs: GridLayout
    private lateinit var comfortIcon: ComfortIconView
    private lateinit var tvSeatLabel: TextView
    private lateinit var tvSeatLabelEn: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvPmv: TextView
    private lateinit var tvPpd: TextView

    /** 12 张身体部位卡片（按 BodyPart12 枚举顺序绑定） */
    private val partCards: MutableMap<BodyPart12, FrameLayout> = linkedMapOf()

    /** 12 张卡片右下角的体感文字（凉/稍凉/合适/稍暖/暖），颜色随等级 */
    private val partLabels: MutableMap<BodyPart12, TextView> = linkedMapOf()

    private var currentPosition: Int = Position.ROW_1_LEFT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFullscreen()
        setContentView(R.layout.activity_seat_detail)

        currentPosition = intent.getIntExtra(EXTRA_POSITION, Position.ROW_1_LEFT)
        Log.i(TAG, "onCreate: position=$currentPosition")

        bindViews()
        initLeftSeatPanel()
        loadSeatData()
        registerComfortCallback()
        fetchInitialCards()
    }

    private fun bindViews() {
        glSeatThumbs = findViewById(R.id.gl_seat_thumbs)
        comfortIcon = findViewById(R.id.iv_seat_detail_icon)
        tvSeatLabel = findViewById(R.id.tv_seat_detail_label)
        tvSeatLabelEn = findViewById(R.id.tv_seat_detail_label_en)
        tvTemperature = findViewById(R.id.tv_seat_detail_temperature)
        tvHumidity = findViewById(R.id.tv_seat_detail_humidity)
        tvPmv = findViewById(R.id.tv_seat_detail_pmv)
        tvPpd = findViewById(R.id.tv_seat_detail_ppd)
        bindPartCards()
    }

    private fun bindPartCards() {
        partCards.clear()
        partLabels.clear()
        val labelMarginLeft = 119
        val labelMarginTop = 84

        val idMap = mapOf(
            BodyPart12.HEAD to R.id.card_head,
            BodyPart12.FACE to R.id.card_face,
            BodyPart12.NECK to R.id.card_neck,
            BodyPart12.SHOULDER to R.id.card_shoulder,
            BodyPart12.CHEST to R.id.card_chest,
            BodyPart12.BACK to R.id.card_back,
            BodyPart12.ABDOMEN to R.id.card_abdomen,
            BodyPart12.ARM to R.id.card_arm,
            BodyPart12.HAND to R.id.card_hand,
            BodyPart12.THIGH to R.id.card_thigh,
            BodyPart12.CALF to R.id.card_calf,
            BodyPart12.FOOT to R.id.card_foot
        )

        idMap.forEach { (part, resId) ->
            val card = findViewById<FrameLayout>(resId)
            partCards[part] = card

            val label = TextView(this).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 14.04f)
                visibility = View.GONE
                includeFontPadding = false
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(labelMarginLeft, labelMarginTop, 0, 0)
            }
            card.addView(label, lp)
            partLabels[part] = label
        }
    }

    /**
     * 左侧 2x2 网格座位切换面板。
     * 顺序：左上=主驾(0)、右上=副驾(1)、左下=后排右(4)、右下=后排左(5)。
     */
    private fun initLeftSeatPanel() {
        glSeatThumbs.removeAllViews()
        val cellW = 81
        val cellH = 149
        val gap = 12

        POSITIONS.forEachIndexed { idx, pos ->
            val isSelected = pos == currentPosition
            val item = View(this).apply {
                background = ContextCompat.getDrawable(
                    this@SeatDetailActivity,
                    if (isSelected) R.drawable.bg_seat_thumb_active
                    else R.drawable.bg_seat_thumb_normal
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { switchToSeat(pos) }
            }
            val lp = GridLayout.LayoutParams().apply {
                width = cellW
                height = cellH
                columnSpec = GridLayout.spec(idx % 2, 1f)
                rowSpec = GridLayout.spec(idx / 2, 1f)
                setMargins(gap / 2, gap / 2, gap / 2, gap / 2)
            }
            glSeatThumbs.addView(item, lp)
        }
    }

    private fun switchToSeat(position: Int) {
        if (position == currentPosition) return
        Log.i(TAG, "switchToSeat: $currentPosition -> $position")
        currentPosition = position
        initLeftSeatPanel()
        loadSeatData()
        val json = if (MockComfortJson.USE_MOCK) {
            MockComfortJson.bodyParts(null)
        } else {
            try {
                AiCarControlSDK.getComfortLevel(currentPosition)
            } catch (_: Exception) {
                null
            }
        }
        applyCardsByJson(json)
    }

    /**
     * 加载当前座位的体感数据。
     * 当前为 mock：每个 position 给一个固定的体感等级 + 配套的温/湿/PMV/PPD。
     * TODO：等 SDK 提供 HVAC / 温/湿 字段后，把 MOCK_BY_POSITION 替换为 SDK getComfortLevelAt + 空调回调。
     */
    private fun loadSeatData() {
        val data = MOCK_BY_POSITION[currentPosition]
            ?: MockSeatData(ComfortIconView.LEVEL_JUST_RIGHT, 25.0, 45.0, 0.0)
        applySeatData(data)
    }

    /**
     * 注册 SDK 舒适度回调：收到推送时刷新当前选中座位的卡片。
     * 同一份 JSON 既驱动右栏大圆，也驱动下方 12 张身体部位卡片。
     * 温/湿暂用占位，等 SDK HVAC 字段确定后再补齐。
     */
    private fun registerComfortCallback() {
        try {
            AiCarControlSDK.registerComfortLevelCallback(object : ComfortLevelCallback {
                override fun onComfortLevelChanged(position: Int, comfortLevel: String?) {
                    Log.i(TAG, "【回调】pos=$position, json=$comfortLevel")
                    runOnUiThread {
                        val level = jsonToLevel(comfortLevel)
                        val pmv = jsonToPmv(comfortLevel)
                        applyCardsByJson(comfortLevel)
                        if (position == currentPosition && level != null) {
                            val placeholder = MOCK_BY_POSITION[position]
                                ?: MockSeatData(level, 25.0, 45.0, pmv)
                            applySeatData(
                                MockSeatData(
                                    level = level,
                                    temperature = placeholder.temperature,
                                    humidity = placeholder.humidity,
                                    pmv = pmv
                                )
                            )
                        }
                    }
                }
            })
            Log.i(TAG, "体感回调已注册")
        } catch (e: Exception) {
            Log.e(TAG, "注册回调失败", e)
        }
    }

    /**
     * 首次进入时主动拉一次当前座位的体感数据，给 12 张卡片先打底。
     * Mock 模式下完全本地生成；真实模式下走 SDK getComfortLevel()。
     * 回调推送来之前不至于全白。
     */
    private fun fetchInitialCards() {
        val json = if (MockComfortJson.USE_MOCK) {
            Log.i(TAG, "【mock】拉取 12 部位体感")
            MockComfortJson.bodyParts(null)
        } else {
            try {
                AiCarControlSDK.getComfortLevel(currentPosition)
            } catch (e: Exception) {
                Log.w(TAG, "主动拉取体感失败", e)
                null
            }
        }
        Log.i(TAG, "【拉取】pos=$currentPosition, json=$json")
        applyCardsByJson(json)
    }

    /**
     * 把 JSON 解析成 12 个部位的体感档位，写到 12 张卡片上。
     * JSON 无效 / Validity != 1 时全部恢复默认白底，并把右下角的体感文字隐藏。
     */
    private fun applyCardsByJson(json: String?) {
        val values = BodyPart12.parse(json)
        partCards.forEach { (part, view) ->
            val label = partLabels[part] ?: return@forEach
            val v = values[part]
            if (v == null) {
                view.setBackgroundResource(R.drawable.bg_body_part_cell)
                label.visibility = View.GONE
            } else {
                val level = BodyPart12.valueToLevel(v)
                view.background = makePartCardDrawable(ComfortIconView.getColor(level))
                val levelColor = ComfortIconView.getColor(level)
                label.setTextColor(levelColor)
                label.text = comfortCn(level)
                label.visibility = View.VISIBLE
            }
        }
    }

    /**
     * 给 12 张卡片做带边框 + 弧度的纯色背景，保留与 bg_body_part_cell 一致的边框规格。
     * 填充色额外叠加 19% 透明度（0x30 ≈ 48/255），让卡片颜色变浅、不抢中央人像风头。
     */
    private fun makePartCardDrawable(@ColorInt fill: Int): GradientDrawable {
        // 19% alpha = 0x30 (48/255 ≈ 18.8%)
        val bgColor = (fill and 0x00FFFFFF) or 0x30000000.toInt()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 15.6f * resources.displayMetrics.density
            setColor(bgColor)
            setStroke((1f * resources.displayMetrics.density).toInt(), 0x0F000000.toInt())
        }
    }

    /**
     * 把一组数据推到右侧卡片所有相关 View 上。
     */
    private fun applySeatData(d: MockSeatData) {
        val color = ComfortIconView.getColor(d.level)
        val seatName = POSITION_NAMES[currentPosition] ?: "座位"
        val cn = comfortCn(d.level)
        val en = comfortEn(d.level)

        // 顶部大圆 + 圆内文字
        comfortIcon.setComfortLevel(d.level)
        // 圆圈下方说明文字
        tvSeatLabel.text = "${seatName}舒适感"
        // 圆圈下方主标题（已隐藏）
        // 英文胶囊
        tvSeatLabelEn.text = en
        tvSeatLabelEn.setTextColor(color)
        tvSeatLabelEn.background = makePillDrawable(color)
        // 四宫格
        tvTemperature.text = "%.1f°C".format(d.temperature)
        tvHumidity.text = "%.0f%%".format(d.humidity)
        tvPmv.text = "%+.1f".format(d.pmv)
        tvPpd.text = "%.0f%%".format(calcPpd(d.pmv))
    }

    private fun makePillDrawable(@ColorInt baseColor: Int): GradientDrawable {
        // 10% alpha = 0x1A
        val bg = (baseColor and 0x00FFFFFF) or 0x1A000000
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * resources.displayMetrics.density
            setColor(bg)
        }
    }

    private fun comfortCn(level: Int): String = when (level) {
        ComfortIconView.LEVEL_COOL -> "凉"
        ComfortIconView.LEVEL_SLIGHTLY_COOL -> "稍凉"
        ComfortIconView.LEVEL_JUST_RIGHT -> "合适"
        ComfortIconView.LEVEL_SLIGHTLY_WARM -> "稍暖"
        ComfortIconView.LEVEL_WARM -> "暖"
        else -> "合适"
    }

    private fun comfortEn(level: Int): String = when (level) {
        ComfortIconView.LEVEL_COOL -> "COLD"
        ComfortIconView.LEVEL_SLIGHTLY_COOL -> "COOL"
        ComfortIconView.LEVEL_JUST_RIGHT -> "NEUTRAL"
        ComfortIconView.LEVEL_SLIGHTLY_WARM -> "WARM"
        ComfortIconView.LEVEL_WARM -> "HOT"
        else -> "NEUTRAL"
    }

    /**
     * Fanger PPD 公式（PMV 与 PPD 的标准换算）。
     * PMV=0 → PPD=5；|PMV| 越大 → PPD 越大。
     */
    private fun calcPpd(pmv: Double): Double {
        return 100.0 - 95.0 * exp(-0.03353 * pmv.pow4() - 0.2179 * pmv * pmv)
    }

    private fun Double.pow4(): Double {
        val s = this * this
        return s * s
    }

    private fun jsonToLevel(json: String?): Int? {
        if (json.isNullOrEmpty()) return null
        return try {
            val obj = JSONObject(json)
            if (!obj.has("Ovr")) return null
            val ovr = obj.getDouble("Ovr")
            when {
                ovr <= -0.4 -> ComfortIconView.LEVEL_COOL
                ovr <= -0.05 -> ComfortIconView.LEVEL_SLIGHTLY_COOL
                ovr < 0.05 -> ComfortIconView.LEVEL_JUST_RIGHT
                ovr < 0.4 -> ComfortIconView.LEVEL_SLIGHTLY_WARM
                else -> ComfortIconView.LEVEL_WARM
            }
        } catch (e: Exception) {
            Log.w(TAG, "jsonToLevel 解析失败: $json", e)
            null
        }
    }

    private fun jsonToPmv(json: String?): Double {
        if (json.isNullOrEmpty()) return 0.0
        return try {
            JSONObject(json).optDouble("Ovr", 0.0)
        } catch (e: Exception) {
            0.0
        }
    }

    data class MockSeatData(
        val level: Int,
        val temperature: Double,
        val humidity: Double,
        val pmv: Double
    )
}