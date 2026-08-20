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

class BodyFeelActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BodyFeel"

        // 车型座位数：4 / 5 / 6
        private const val SEAT_COUNT = 4
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
        val ivComfortIcon: ImageView
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_body_feel)

        bindCardViews()
        inflateSeatLayout()

        // 注册舒适度回调
        AiCarControlSDK.registerComfortLevelCallback(object : ComfortLevelCallback {
            override fun onComfortLevelChanged(position: Int, comfortLevel: Int?) {
                Log.i(TAG, "【回调】pos=$position, level=$comfortLevel")
                runOnUiThread { updateSeatAndCard(position, comfortLevel) }
            }
        })

        // 首次拉取所有座位舒适度
        Log.i(TAG, "========== 首次拉取所有座位舒适度 ==========")
        val levels = AiCarControlSDK.getComfortLevel()
        val positions = visiblePositionsBySeatCount[SEAT_COUNT] ?: emptyList()
        for (pos in positions) {
            val name = positionCardNames[pos] ?: "未知"
            val level = levels.getOrNull(pos)
            Log.i(TAG, "【初始】$name (pos=$pos), level=$level")
            updateSeatAndCard(pos, level)
        }
        Log.i(TAG, "============================================")
    }

    private fun bindCardViews() {
        val visiblePositions = visiblePositionsBySeatCount[SEAT_COUNT] ?: emptyList()

        cardViews = visiblePositions.associateWith { pos ->
            val resId = resources.getIdentifier("card_seat_$pos", "id", packageName)
            val root = findViewById<View>(resId)
            CardBinding(
                root = root,
                tvName = root.findViewById(R.id.tv_seat_name),
                viewDot = root.findViewById(R.id.view_status_dot),
                energyBar = root.findViewById(R.id.energy_bar),
                ivComfortIcon = root.findViewById(R.id.iv_comfort_icon)
            )
        }

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
            findViewById<ImageView>(resId)
        }

        Log.i(TAG, "座位布局已应用：${SEAT_COUNT}座，${seatViews.size} 个座位")
    }

    private fun updateSeatAndCard(position: Int, comfortLevel: Int?) {
        val cb = cardViews[position] ?: return
        val (dotRes, iconRes) = getComfortDrawable(comfortLevel)
        cb.viewDot.setBackgroundResource(dotRes)
        cb.ivComfortIcon.setImageResource(iconRes)
        updateEnergyBar(comfortLevel, cb.energyBar)
    }

    private fun getComfortDrawable(level: Int?): Pair<Int, Int> = when {
        level == null || level == Int.MIN_VALUE -> Pair(R.drawable.bg_status_dot_unknown, R.drawable.comfortable)
        level <= -1                           -> Pair(R.drawable.bg_status_dot_cool, R.drawable.comfortable)
        level in 0..1                        -> Pair(R.drawable.bg_status_dot_active, R.drawable.comfortable)
        else                                  -> Pair(R.drawable.bg_status_dot_warm, R.drawable.warmish)
    }

    private fun updateEnergyBar(level: Int?, container: LinearLayout) {
        // 5段能量条：-2/-1 -> 索引0-1(蓝色), 0-1 -> 索引2(绿色), 2 -> 索引3(橙色), >=3 -> 索引4(红色)
        val activeIndex = when {
            level == null || level == Int.MIN_VALUE -> -1
            level <= -2                            -> 0
            level == -1                             -> 1
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
}
