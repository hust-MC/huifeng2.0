package com.max.huifeng

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
        private const val USE_MOCK = false
    }

    private lateinit var seatViews: Map<Int, ImageView>
    private lateinit var cardViews: Map<Int, CardBinding>

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
        val comfortIcon: ComfortIconView
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 沉浸式全屏：内容延伸到系统栏背后，并隐藏状态栏与导航栏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContentView(R.layout.activity_body_feel)

        bindCardViews()
        inflateSeatLayout()

        // SDK 已在 Application 中初始化，这里只负责注册回调 + 拉取数据
        Log.i(TAG, "Activity 启动，注册体感回调...")
        registerComfortCallback()

        // 如果 SDK 已连接，立即拉取数据；否则等待回调触发
        if (AiCarControlSDK.isConnected()) {
            Log.i(TAG, "SDK 已连接，立即拉取初始数据")
            loadInitialData()
        } else {
            Log.i(TAG, "SDK 尚未连接，等待回调推送数据")
        }
    }

    /**
     * 注册舒适度回调。SDK 会自动重连并重挂回调，Activity 重建后需重新注册。
     */
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
        val levels = AiCarControlSDK.getComfortLevel()
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
            CardBinding(
                root = root,
                tvName = root.findViewById(R.id.tv_seat_name),
                viewDot = root.findViewById(R.id.view_status_dot),
                energyBar = root.findViewById(R.id.energy_bar),
                comfortIcon = root.findViewById(R.id.iv_comfort_icon)
            )
        }.filterValues { it != null }
            .mapValues { (_, v) -> v!! }

        cardViews.forEach { (pos, cb) ->
            cb.tvName.text = positionCardNames[pos] ?: "未知"
        }
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
        updateEnergyBar(level, cb.energyBar)
    }

    /**
     * 左侧小圆点颜色与右侧 ComfortIconView 主色严格 1:1 对齐。
     * 取整到 5 档后映射到同名颜色 drawable。
     */
    private fun dotResFor(level: Int?): Int = when {
        level == null -> R.drawable.bg_status_dot_unknown
        level <= ComfortIconView.LEVEL_COOL -> R.drawable.bg_status_dot_cool
        level == ComfortIconView.LEVEL_SLIGHTLY_COOL -> R.drawable.bg_status_dot_slightly_cool
        level == ComfortIconView.LEVEL_JUST_RIGHT -> R.drawable.bg_status_dot_active
        level == ComfortIconView.LEVEL_SLIGHTLY_WARM -> R.drawable.bg_status_dot_slightly_warm
        else /* >= WARM */ -> R.drawable.bg_status_dot_warm
    }

    private fun updateEnergyBar(level: Int?, container: LinearLayout) {
        // 5 段能量条按"温度计累进"语义：每升一档多亮一节
        //   null      -> 全灰（无数据）
        //   -2 凉     -> 1 段
        //   -1 稍凉   -> 2 段
        //    0 合适   -> 3 段（居中）
        //    1 稍暖   -> 4 段
        //    2 暖     -> 5 段
        val activeCount = when (level) {
            null -> 0
            ComfortIconView.LEVEL_COOL -> 1
            ComfortIconView.LEVEL_SLIGHTLY_COOL -> 2
            ComfortIconView.LEVEL_JUST_RIGHT -> 3
            ComfortIconView.LEVEL_SLIGHTLY_WARM -> 4
            else /* LEVEL_WARM or higher */ -> 5
        }
        val selectedBg = R.drawable.bg_energy_selected
        val unselectedBg = R.drawable.bg_energy_unselected
        for (i in 0 until container.childCount) {
            container.getChildAt(i).setBackgroundResource(
                if (i < activeCount) selectedBg else unselectedBg
            )
        }
    }

    /**
     * 把舒适度 JSON 折算成 5 档体感等级（取 Ovr 字段）。
     * 未占用（json 为 null/空）或解析失败返回 null。
     */
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

    /** 给模拟数据构造一个最小可用的 JSON（只设 Ovr 和 Validity） */
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
