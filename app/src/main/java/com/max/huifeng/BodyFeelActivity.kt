package com.max.huifeng

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.geely.aicarcontrolsdk.AiCarControlSDK
import com.geely.aicarcontrolsdk.ComfortLevelCallback
import com.geely.aicarcontrolsdk.Position

class BodyFeelActivity : AppCompatActivity() {

    private lateinit var seatViews: Map<Int, ImageView>

    private val positionNames = mapOf(
        Position.ROW_1_LEFT  to "前排主驾",
        Position.ROW_1_RIGHT to "前排副驾",
        Position.ROW_2_LEFT  to "后排中间",
        Position.ROW_2_RIGHT to "后排右侧",
        Position.ROW_3_LEFT  to "后排左侧"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_body_feel)

        seatViews = mapOf(
            Position.ROW_1_LEFT  to findViewById(R.id.iv_seat_0),
            Position.ROW_1_RIGHT to findViewById(R.id.iv_seat_1),
            Position.ROW_2_LEFT to findViewById(R.id.iv_seat_2),
            Position.ROW_2_RIGHT to findViewById(R.id.iv_seat_3),
            Position.ROW_3_LEFT to findViewById(R.id.iv_seat_4),
        )

        // 注册回调，监听舒适度变化
        AiCarControlSDK.registerComfortLevelCallback(object : ComfortLevelCallback {
            override fun onComfortLevelChanged(position: Int, comfortLevel: Int?) {
                val name = positionNames[position] ?: "未知"
                Log.i("BodyFeel", "【回调】座位: $name (pos=$position), 舒适度: $comfortLevel")
                runOnUiThread {
                    updateSeatColor(position, comfortLevel)
                }
            }
        })

        // 首次拉取当前所有座位的舒适度
        Log.i("BodyFeel", "========== 首次拉取所有座位舒适度 ==========")
        val levels = AiCarControlSDK.getComfortLevel()
        for (pos in seatViews.keys) {
            val name = positionNames[pos] ?: "未知"
            val level = levels.getOrNull(pos)
            Log.i("BodyFeel", "【初始】座位: $name (pos=$pos), 舒适度: $level")
            updateSeatColor(pos, level)
        }
        Log.i("BodyFeel", "============================================")
    }

    private fun updateSeatColor(position: Int, comfortLevel: Int?) {
        val view = seatViews[position] ?: return
        val name = positionNames[position] ?: "未知"
        val bgRes = when {
            comfortLevel == null -> {
                Log.d("BodyFeel", "座位[$name] -> 未知/未启动")
                R.drawable.bg_seat_level_unknown
            }
            comfortLevel == Int.MIN_VALUE -> {
                Log.d("BodyFeel", "座位[$name] -> 无人占用")
                R.drawable.bg_seat_level_unoccupied
            }
            comfortLevel <= -2 -> {
                Log.d("BodyFeel", "座位[$name] -> 冷 (level=$comfortLevel)")
                R.drawable.bg_seat_level_cold
            }
            comfortLevel == -1 -> {
                Log.d("BodyFeel", "座位[$name] -> 稍凉 (level=$comfortLevel)")
                R.drawable.bg_seat_level_cool
            }
            comfortLevel in 0..1 -> {
                Log.d("BodyFeel", "座位[$name] -> 舒适 (level=$comfortLevel)")
                R.drawable.bg_seat_level_comfortable
            }
            comfortLevel == 2 -> {
                Log.d("BodyFeel", "座位[$name] -> 暖 (level=$comfortLevel)")
                R.drawable.bg_seat_level_warm
            }
            else -> {
                Log.d("BodyFeel", "座位[$name] -> 热 (level=$comfortLevel)")
                R.drawable.bg_seat_level_hot
            }
        }
        view.setBackgroundResource(bgRes)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 页面销毁时清理回调（如果 SDK 支持）
    }
}
