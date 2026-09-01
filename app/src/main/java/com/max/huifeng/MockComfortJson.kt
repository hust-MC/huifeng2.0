package com.max.huifeng

import org.json.JSONObject
import kotlin.random.Random

/**
 * 体感 mock JSON 工具。
 *
 * `USE_MOCK=true` 时，BodyFeelActivity 和 SeatDetailActivity 都走本地随机构造的体感 JSON：
 * - `overall(level)`  整车：`{"Ovr":..., "Validity":1}`，给 BodyFeelActivity 用
 * - `bodyParts(level)` 单座：20 字段（6 单 + 12 左右配对 + Ovr + Validity），给 SeatDetailActivity 12 部位卡片用
 *
 * 体感的 5 个档位（凉/稍凉/合适/稍暖/暖）的数值区间在这两个接口里保持一致，
 * 跟 SDK 真实数据走的是同一套 `BodyPart12.valueToLevel()` 映射规则，所以 UI 看到的颜色无差别。
 */
object MockComfortJson {

    /** mock 总开关。BodyFeelActivity / SeatDetailActivity 都读这个常量。 */
    const val USE_MOCK = false

    private val SINGLES = listOf("Head", "Face", "Neck", "Chest", "Back", "Abdomen")
    private data class Paired(val left: String, val right: String)
    private val PAIRS = listOf(
        Paired("ShoulderL", "ShoulderR"),
        Paired("ArmL", "ArmR"),
        Paired("HandL", "HandR"),
        Paired("ThighL", "ThighR"),
        Paired("LegL", "LegR"),
        Paired("FootL", "FootR")
    )

    private val random = Random.Default

    // ---------- 整车（Ovr 单字段） ----------

    /**
     * 整车体感 Ovr（2 字段）：{"Ovr": <value>, "Validity": 1}
     *
     * @param level 目标档位；null 时随机一档（5 个等概率）
     */
    fun overall(level: Int? = ComfortIconView.LEVEL_JUST_RIGHT): String {
        val targetLevel = level ?: randomLevel()
        return JSONObject().apply {
            put("Ovr", randomValueFor(targetLevel))
            put("Validity", 1)
        }.toString()
    }

    // ---------- 单座（12 部位完整字段） ----------

    /**
     * 单座完整体感 JSON：20 字段 = 6 单部位 + 12 左右配对 + Ovr + Validity。
     *
     * @param level 目标档位；null 时 12 个部位独立随机一次，每次调用都会刷新
     *              （调用方可在进入页面或切座位时拉一次，缓存结果避免刷新闪烁）。
     */
    fun bodyParts(level: Int? = null): String {
        val obj = JSONObject()
        SINGLES.forEach { key ->
            obj.put(key, pickValue(level))
        }
        PAIRS.forEach { p ->
            val v = pickValue(level)
            obj.put(p.left, v)
            obj.put(p.right, v)
        }
        obj.put("Ovr", pickValue(level))
        obj.put("Validity", 1)
        return obj.toString()
    }

    // ---------- 内部 ----------

    private fun pickValue(level: Int?): Double {
        val l = level ?: randomLevel()
        return randomValueFor(l)
    }

    private fun randomLevel(): Int = listOf(
        ComfortIconView.LEVEL_COOL,
        ComfortIconView.LEVEL_SLIGHTLY_COOL,
        ComfortIconView.LEVEL_JUST_RIGHT,
        ComfortIconView.LEVEL_SLIGHTLY_WARM,
        ComfortIconView.LEVEL_WARM
    ).random(random)

    /** 每档数值区间（与 BodyPart12.valueToLevel 阈值反向对齐）。 */
    private fun randomValueFor(level: Int): Double {
        val range = when (level) {
            ComfortIconView.LEVEL_COOL -> -0.8 to -0.5
            ComfortIconView.LEVEL_SLIGHTLY_COOL -> -0.3 to -0.1
            ComfortIconView.LEVEL_JUST_RIGHT -> -0.04 to 0.04
            ComfortIconView.LEVEL_SLIGHTLY_WARM -> 0.1 to 0.3
            ComfortIconView.LEVEL_WARM -> 0.5 to 0.8
            else -> 0.0 to 0.0
        }
        return range.first + (range.second - range.first) * random.nextDouble()
    }
}