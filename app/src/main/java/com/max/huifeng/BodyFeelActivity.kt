package com.max.huifeng

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
        Position.ROW_1_LEFT  to "主驾",
        Position.ROW_1_RIGHT to "副驾",
        Position.ROW_2_RIGHT to "后排右",
        Position.ROW_3_LEFT  to "后排左",
        Position.ROW_2_LEFT  to "后排中"
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
        setContentView(R.layout.activity_body_feel)

        bindCardViews()
        inflateSeatLayout()

        // 注册舒适度回调（新 SDK 回调第二参为 JSON 字符串）
        AiCarControlSDK.registerComfortLevelCallback(object : ComfortLevelCallback {
            override fun onComfortLevelChanged(position: Int, comfortLevel: String?) {
                Log.i(TAG, "【回调】pos=$position, json=$comfortLevel")
                runOnUiThread { updateSeatAndCard(position, comfortLevel) }
            }
        })

        // 首次拉取所有座位舒适度（新 SDK 返回 List<String?>，以 Position 为索引）
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
            val json = mockJson.getOrNull(index) ?: levels.getOrNull(pos)
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
        val cb = cardViews[position] ?: return
        val level = jsonToLevel(comfortLevelJson)
        val (dotRes, _) = getComfortDrawable(level)
        cb.viewDot.setBackgroundResource(dotRes)
        cb.comfortIcon.setComfortLevel(level ?: ComfortIconView.LEVEL_JUST_RIGHT)
        updateEnergyBar(level, cb.energyBar)
    }

    private fun getComfortDrawable(level: Int?): Pair<Int, Int> = when {
        level == null                          -> Pair(R.drawable.bg_status_dot_unknown, R.drawable.comfortable)
        level <= -1                            -> Pair(R.drawable.bg_status_dot_cool, R.drawable.comfortable)
        level in 0..1                          -> Pair(R.drawable.bg_status_dot_active, R.drawable.comfortable)
        else                                   -> Pair(R.drawable.bg_status_dot_warm, R.drawable.warmish)
    }

    private fun updateEnergyBar(level: Int?, container: LinearLayout) {
        // 5段能量条：-2/-1 -> 索引0-1(蓝色), 0-1 -> 索引2(绿色), 2 -> 索引3(橙色), >=3 -> 索引4(红色)
        val activeIndex = when {
            level == null                          -> -1
            level <= -2                            -> 0
            level == -1                            -> 1
            level in 0..1                          -> 2
            level == 2                             -> 3
            else                                   -> 4
        }
        val selectedBg = R.drawable.bg_energy_selected
        val unselectedBg = R.drawable.bg_energy_unselected
        for (i in 0 until container.childCount) {
            container.getChildAt(i).setBackgroundResource(if (i == activeIndex) selectedBg else unselectedBg)
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
                ovr <= -0.4  -> ComfortIconView.LEVEL_COOL          // -2 凉
                ovr <= -0.05 -> ComfortIconView.LEVEL_SLIGHTLY_COOL // -1 稍凉
                ovr < 0.05   -> ComfortIconView.LEVEL_JUST_RIGHT    //  0 合适
                ovr < 0.4    -> ComfortIconView.LEVEL_SLIGHTLY_WARM //  1 稍暖
                else         -> ComfortIconView.LEVEL_WARM          //  2 暖
            }
        } catch (e: Exception) {
            Log.w(TAG, "jsonToLevel 解析失败: $json", e)
            null
        }
    }

    /** 给模拟数据构造一个最小可用的 JSON（只设 Ovr 和 Validity） */
    private fun jsonFor(level: Int): String {
        val ovr = when (level) {
            ComfortIconView.LEVEL_COOL          -> -0.6
            ComfortIconView.LEVEL_SLIGHTLY_COOL -> -0.2
            ComfortIconView.LEVEL_JUST_RIGHT    -> 0.0
            ComfortIconView.LEVEL_SLIGHTLY_WARM -> 0.2
            ComfortIconView.LEVEL_WARM          -> 0.6
            else                                -> 0.0
        }
        return """{"Ovr":$ovr,"Validity":1}"""
    }
}
