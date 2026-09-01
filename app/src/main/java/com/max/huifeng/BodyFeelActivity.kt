package com.max.huifeng

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.geely.aicarcontrolsdk.AiCarControlSDK
import com.geely.aicarcontrolsdk.ComfortLevelCallback
import com.geely.aicarcontrolsdk.Position
import org.json.JSONObject

class BodyFeelActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BodyFeel"

        // 车型座位数：4 / 5 / 6
        private const val SEAT_COUNT = 4

        /**
         * 是否使用本地 mock 数据。
         * true  -> 首次拉取用本地构造的 JSON，方便无真机/无信号时调试 UI
         * false -> 直接走 SDK getComfortLevel() 拿真实数据（默认）
         */
        private const val USE_MOCK = true

        private val SEGMENT_SENSATIONS = intArrayOf(-2, -1, 0, 1, 2)

        private val BORDER_DRAWABLE = R.drawable.bg_energy_segment_border

        private const val OVERLAY_ROTATION_DEG = 7f       // 顶部向右旋转 7°（顺时针）
        private const val OVERLAY_ALPHA = 0.5f            // 透明度 50%（对齐 Figma 蓝 case 右侧 Layer 参数）
    }

    private lateinit var seatViews: Map<Int, ImageView>
    private lateinit var seatOverlayViews: Map<Int, SeatOverlayView>
    private lateinit var cardViews: Map<Int, CardBinding>

    private val selectedSegmentIndex = mutableMapOf<Int, Int>()

    // 4座/5座对应的 Position 列表
    private val visiblePositionsBySeatCount = mapOf(
        4 to listOf(
            Position.ROW_1_LEFT,
            Position.ROW_1_RIGHT,
            Position.ROW_2_RIGHT,
            Position.ROW_3_LEFT
        ),
        5 to listOf(
            Position.ROW_1_LEFT,
            Position.ROW_1_RIGHT,
            Position.ROW_2_LEFT,
            Position.ROW_2_RIGHT,
            Position.ROW_3_LEFT
        )
    )

    private val seatsLayoutByCount = mapOf(
        4 to R.layout.layout_seats_4,
        5 to R.layout.layout_seats_5,
        6 to R.layout.layout_seats_6
    )

    // Position -> 卡片显示名
    private val positionCardNames = mapOf(
        Position.ROW_1_LEFT to "主驾",
        Position.ROW_1_RIGHT to "副驾",
        Position.ROW_2_RIGHT to "后排右",
        Position.ROW_3_LEFT to "后排左",
        Position.ROW_2_LEFT to "后排中"
    )

    // 辅助类：封装卡片内部 View
    data class CardBinding(
        val root: View,
        val tvName: TextView,
        val viewDot: View,
        val energyBar: LinearLayout,
        val comfortIcon: ComfortIconView,
        val segments: List<View>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 沉浸式全屏：内容延伸到系统栏背后，并隐藏状态栏与导航栏
        setFullscreen()

        setContentView(R.layout.activity_body_feel)

        bindCardViews()
        inflateSeatLayout()
        bindCardClickToOpenDetail()

        // SDK 已在 Application 中初始化，这里只负责注册回调 + 拉取数据
        Log.i(TAG, "Activity 启动，注册体感回调...")
        registerComfortCallback()

        // Mock 模式下直接走本地数据，不依赖 SDK 连接状态
        if (USE_MOCK) {
            Log.i(TAG, "Mock 模式，跳过 SDK 连接检查，直接加载本地数据")
            loadInitialData()
        } else if (AiCarControlSDK.isConnected()) {
            Log.i(TAG, "SDK 已连接，立即拉取初始数据")
            loadInitialData()
        } else {
            Log.i(TAG, "SDK 尚未连接，等待回调推送数据")
        }
    }

    private fun registerComfortCallback() {
        AiCarControlSDK.registerComfortLevelCallback(object : ComfortLevelCallback {
            override fun onComfortLevelChanged(position: Int, comfortLevel: String?) {
                Log.i(TAG, "【回调】pos=$position, json=$comfortLevel")
                try {
                    runOnUiThread { updateSeatAndCard(position, comfortLevel) }
                } catch (e: Exception) {
                    Log.e(TAG, "回调处理失败: pos=$position", e)
                }
            }
        })
        Log.i(TAG, "体感回调已注册")
    }

    /**
     * 首次拉取所有座位舒适度数据并更新界面
     */
    private fun loadInitialData() {
        Log.i(TAG, "========== 首次拉取所有座位舒适度（USE_MOCK=$USE_MOCK） ==========")
        // Mock 模式下跳过 SDK 调用，避免依赖连接状态
        val levels: List<String?>? = if (USE_MOCK) null else AiCarControlSDK.getComfortLevel()
        val positions = visiblePositionsBySeatCount[SEAT_COUNT] ?: emptyList()

        // Mock 数据：仅在 USE_MOCK=true 时生效。4个座位分别设为 凉、稍凉、合适、稍暖
        val mockJson: List<String> = if (USE_MOCK) listOf(
            jsonFor(ComfortIconView.LEVEL_COOL),            // 主驾 - 凉
            jsonFor(ComfortIconView.LEVEL_SLIGHTLY_COOL),   // 副驾 - 稍凉
            jsonFor(ComfortIconView.LEVEL_JUST_RIGHT),      // 后排右 - 合适
            jsonFor(ComfortIconView.LEVEL_SLIGHTLY_WARM)    // 后排左 - 稍暖
        ) else emptyList()

        for ((index, pos) in positions.withIndex()) {
            val name = positionCardNames[pos] ?: "未知"
            // USE_MOCK=true 时优先用 mockJson；否则（或用完）直接走 SDK 真实数据
            val json = mockJson.getOrNull(index) ?: levels?.getOrNull(pos)
            Log.i(TAG, "【初始】$name (pos=$pos), json=$json")
            updateSeatAndCard(pos, json)
        }
        Log.i(TAG, "============================================")
    }

    private fun bindCardViews() {
        val visiblePositions = visiblePositionsBySeatCount[SEAT_COUNT] ?: emptyList()

        cardViews = visiblePositions.associateWith { pos ->
            val resId = resources.getIdentifier("card_seat_$pos", "id", packageName)
            val root = if (resId != 0) findViewById<View>(resId) else null
            if (root == null) {
                Log.w(TAG, "bindCardViews: 找不到 card_seat_$pos (resId=$resId)")
                return@associateWith null
            }
            val segments = listOf<View>(
                root.findViewById(R.id.seg_0),
                root.findViewById(R.id.seg_1),
                root.findViewById(R.id.seg_2),
                root.findViewById(R.id.seg_3),
                root.findViewById(R.id.seg_4)
            )
            segments.forEachIndexed { index, seg ->
                seg.setOnClickListener { onSegmentClicked(pos, index) }
            }
            CardBinding(
                root = root,
                tvName = root.findViewById(R.id.tv_seat_name),
                viewDot = root.findViewById(R.id.view_status_dot),
                energyBar = root.findViewById(R.id.energy_bar),
                comfortIcon = root.findViewById(R.id.iv_comfort_icon),
                segments = segments
            )
        }.filterValues { it != null }
            .mapValues { (_, v) -> v!! }

        cardViews.forEach { (pos, cb) ->
            cb.tvName.text = positionCardNames[pos] ?: "未知"
        }
    }

    private fun bindCardClickToOpenDetail() {
        cardViews.forEach { (pos, cb) ->
            cb.root.setOnClickListener { openSeatDetail(pos) }
        }
        seatOverlayViews.forEach { (pos, overlay) ->
            overlay.setOnClickListener { openSeatDetail(pos) }
        }
    }

    fun openSeatDetail(position: Int) {
        Log.i(TAG, "openSeatDetail: position=$position")
        val intent = Intent(this, SeatDetailActivity::class.java).apply {
            putExtra(SeatDetailActivity.EXTRA_POSITION, position)
        }
        startActivity(intent)
    }

    private fun inflateSeatLayout() {
        val layoutRes = seatsLayoutByCount[SEAT_COUNT]
            ?: throw IllegalStateException("未配置 $SEAT_COUNT 座布局")
        val visiblePositions = visiblePositionsBySeatCount[SEAT_COUNT] ?: emptyList()

        val container = findViewById<android.view.ViewGroup>(R.id.seat_container)
        container.removeAllViews()
        layoutInflater.inflate(layoutRes, container, true)

        seatViews = visiblePositions.associateWith { pos ->
            val resId = resources.getIdentifier("iv_seat_${SEAT_COUNT}_$pos", "id", packageName)
            findViewById(resId)
        }

        seatOverlayViews = visiblePositions.associateWith { pos ->
            val resId = resources.getIdentifier("seat_overlay_${SEAT_COUNT}_$pos", "id", packageName)
            val view: SeatOverlayView? = if (resId != 0) {
                val raw = findViewById<View>(resId)
                if (raw is SeatOverlayView) raw else {
                    Log.w(TAG, "inflateSeatLayout: seat_overlay_${SEAT_COUNT}_$pos 不是 SeatOverlayView，实际类型 ${raw?.javaClass?.simpleName}")
                    null
                }
            } else null
            if (view == null) {
                Log.w(TAG, "inflateSeatLayout: 找不到 seat_overlay_${SEAT_COUNT}_$pos (resId=$resId)")
            }
            view
        }.filterValues { it != null }.mapValues { (_, v) -> v!! }

        Log.i(TAG, "座位布局已应用：${SEAT_COUNT}座，${seatViews.size} 个座位")
    }

    private fun updateSeatAndCard(position: Int, comfortLevelJson: String?) {
        val cb = cardViews[position]
        if (cb == null) {
            Log.w(
                TAG,
                "updateSeatAndCard: 未找到 position=$position 对应的卡片，可能该座位不在当前车型中"
            )
            return
        }

        val level = jsonToLevel(comfortLevelJson)
        val seatName = positionCardNames[position] ?: "座位$position"
        Log.d(TAG, "刷新卡片: $seatName (pos=$position), level=$level")

        cb.viewDot.setBackgroundResource(dotResFor(level))
        // 体感卡片要进入「无人」状态时，用合适档位占位（绿），避免显示错乱
        cb.comfortIcon.setComfortLevel(level ?: ComfortIconView.LEVEL_JUST_RIGHT)
        updateEnergyBar(level, cb.energyBar, position)
        // 给座位人像叠加体感颜色（半透明遮罩）
        applySeatOverlay(position, level)
        clearSegmentSelection(position)
    }

    /**
     * 根据体感等级在人像胸口位置叠加一个带径向渐变的椭圆色斑，
     * 颜色取自 energy bar 同档位色；旋转/透明度/HardLight 混合对齐 Figma 设计稿。
     * level == null 时清除色斑（透明 + 恢复默认旋转/alpha）。
     */
    private fun applySeatOverlay(position: Int, level: Int?) {
        val overlay = seatOverlayViews[position]
        if (overlay == null) {
            Log.w(TAG, "applySeatOverlay: position=$position 没有对应 overlay")
            return
        }
        if (level == null) {
            // 清空色斑：baseColor = TRANSPARENT，onDraw 会直接 return
            overlay.setComfortLevel(null)
            overlay.alpha = 1f
            overlay.rotation = 0f
            return
        }
        val opaqueColor = ComfortIconView.getColor(level)
        Log.d(TAG, "applySeatOverlay: pos=$position, level=$level, color=0x${Integer.toHexString(opaqueColor)}")
        // 在 onDraw 里画径向渐变椭圆；整体透明度由 View.alpha 控制
        overlay.setComfortLevel(level)
        overlay.alpha = OVERLAY_ALPHA              // 50%
        overlay.rotation = OVERLAY_ROTATION_DEG    // 7°
    }

    private fun dotResFor(level: Int?): Int = when {
        level == null -> R.drawable.bg_status_dot_unknown
        level <= ComfortIconView.LEVEL_COOL -> R.drawable.bg_status_dot_cool
        level == ComfortIconView.LEVEL_SLIGHTLY_COOL -> R.drawable.bg_status_dot_slightly_cool
        level == ComfortIconView.LEVEL_JUST_RIGHT -> R.drawable.bg_status_dot_active
        level == ComfortIconView.LEVEL_SLIGHTLY_WARM -> R.drawable.bg_status_dot_slightly_warm
        else /* >= WARM */ -> R.drawable.bg_status_dot_warm
    }

    private fun updateEnergyBar(level: Int?, container: LinearLayout, position: Int? = null) {
        val backgrounds = listOf(
            R.drawable.bg_energy_cool,         // 索引0: 凉 #3C86ED
            R.drawable.bg_energy_slightly_cool, // 索引1: 稍凉 #45A9A4
            R.drawable.bg_energy_just_right,    // 索引2: 合适 #4ECD58
            R.drawable.bg_energy_slightly_warm, // 索引3: 稍暖 #A5B52E
            R.drawable.bg_energy_warm           // 索引4: 暖 #F19F00
        )

        // 根据档位确定显示到哪个索引（包含该索引）
        val visibleIndex = when (level) {
            null -> -1  // 无数据，全部透明
            ComfortIconView.LEVEL_COOL -> 0          // 凉：显示第1个
            ComfortIconView.LEVEL_SLIGHTLY_COOL -> 1 // 稍凉：显示前2个
            ComfortIconView.LEVEL_JUST_RIGHT -> 2    // 合适：显示前3个
            ComfortIconView.LEVEL_SLIGHTLY_WARM -> 3 // 稍暖：显示前4个
            else /* LEVEL_WARM or higher */ -> 4     // 暖：显示全部5个
        }

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (i <= visibleIndex) {
                // 显示该方块并设置对应颜色
                child.visibility = View.VISIBLE
                child.setBackgroundResource(backgrounds.getOrNull(i) ?: R.drawable.bg_energy_unselected)
                // 清掉之前的选中边框（updateEnergyBar 在 onSegmentClicked 之前/之后都可能被调用）
                child.foreground = null
            } else {
                child.visibility = View.VISIBLE
                child.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                child.foreground = null
            }
        }
    }

    private fun onSegmentClicked(position: Int, segmentIndex: Int) {
        if (segmentIndex !in SEGMENT_SENSATIONS.indices) {
            Log.w(TAG, "onSegmentClicked: 非法 segmentIndex=$segmentIndex")
            return
        }
        val cb = cardViews[position] ?: return
        val sensation = SEGMENT_SENSATIONS[segmentIndex]
        val seatName = positionCardNames[position] ?: "座位$position"

        applyExclusiveSegmentBorder(cb, segmentIndex)
        selectedSegmentIndex[position] = segmentIndex

        Log.i(TAG, "【点击】$seatName (pos=$position) segment=$segmentIndex -> sensation=$sensation")
        try {
            val ok = AiCarControlSDK.updateThermalSensation(position, sensation)
            if (!ok) Log.w(TAG, "updateThermalSensation 返回失败: pos=$position, sensation=$sensation")
        } catch (e: Exception) {
            Log.e(TAG, "updateThermalSensation 异常: pos=$position, sensation=$sensation", e)
        }
    }

    private fun applyExclusiveSegmentBorder(cb: CardBinding, targetIndex: Int) {
        val border = androidx.core.content.ContextCompat.getDrawable(this, BORDER_DRAWABLE)
        cb.segments.forEachIndexed { index, seg ->
            if (index == targetIndex) {
                seg.visibility = View.VISIBLE
                seg.foreground = border
            } else {
                seg.foreground = null
            }
        }
    }

    private fun clearSegmentSelection(position: Int) {
        val cb = cardViews[position] ?: return
        cb.segments.forEach { it.foreground = null }
        selectedSegmentIndex.remove(position)
    }

    private fun jsonToLevel(json: String?): Int? {
        if (json.isNullOrEmpty()) return null
        return try {
            val obj = JSONObject(json)
            if (!obj.has("Ovr")) return null
            val ovr = obj.getDouble("Ovr")
            when {
                ovr <= -0.4 -> ComfortIconView.LEVEL_COOL          // -2 凉
                ovr <= -0.05 -> ComfortIconView.LEVEL_SLIGHTLY_COOL // -1 稍凉
                ovr < 0.05 -> ComfortIconView.LEVEL_JUST_RIGHT    //  0 合适
                ovr < 0.4 -> ComfortIconView.LEVEL_SLIGHTLY_WARM //  1 稍暖
                else -> ComfortIconView.LEVEL_WARM          //  2 暖
            }
        } catch (e: Exception) {
            Log.w(TAG, "jsonToLevel 解析失败: $json", e)
            null
        }
    }

    private fun jsonFor(level: Int): String {
        val ovr = when (level) {
            ComfortIconView.LEVEL_COOL -> -0.6
            ComfortIconView.LEVEL_SLIGHTLY_COOL -> -0.2
            ComfortIconView.LEVEL_JUST_RIGHT -> 0.0
            ComfortIconView.LEVEL_SLIGHTLY_WARM -> 0.2
            ComfortIconView.LEVEL_WARM -> 0.6
            else -> 0.0
        }
        return """{"Ovr":$ovr,"Validity":1}"""
    }
}
