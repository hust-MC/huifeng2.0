package com.max.huifeng

import org.json.JSONObject

/**
 * 12 张卡片对应的体感数据模型。
 *
 * 数据来源：SDK 回调 / 拉取的 comfortLevelJson（21 个 double 字段）。
 * 单字段直接取值；左右成对部位（肩/臂/手/腿/脚）取两侧平均。
 */
enum class BodyPart12(
    val displayName: String,
    /** 单字段名；非空时表示此部位对应单个 SDK key */
    val singleKey: String? = null,
    /** 左右成对 key（仅当 [singleKey] 为空时生效） */
    val leftKey: String? = null,
    val rightKey: String? = null
) {
    HEAD("头部", singleKey = "Head"),
    FACE("脸部", singleKey = "Face"),
    NECK("颈部", singleKey = "Neck"),
    SHOULDER("肩部", leftKey = "ShoulderL", rightKey = "ShoulderR"),
    CHEST("前胸", singleKey = "Chest"),
    BACK("后背", singleKey = "Back"),
    ABDOMEN("下腹", singleKey = "Abdomen"),
    ARM("手臂", leftKey = "ArmL", rightKey = "ArmR"),
    HAND("双手", leftKey = "HandL", rightKey = "HandR"),
    THIGH("大腿", leftKey = "ThighL", rightKey = "ThighR"),
    CALF("小腿", leftKey = "LegL", rightKey = "LegR"),
    FOOT("脚部", leftKey = "FootL", rightKey = "FootR");

    companion object {
        /** JSON 无效 / Validity != 1 时整份丢弃，返回空 Map */
        fun parse(json: String?): Map<BodyPart12, Double> {
            if (json.isNullOrEmpty()) return emptyMap()
            val obj = try {
                JSONObject(json)
            } catch (_: Exception) {
                return emptyMap()
            }
            if (obj.optDouble("Validity", 0.0) != 1.0) return emptyMap()
            val out = HashMap<BodyPart12, Double>(values().size)
            values().forEach { part ->
                extractValue(obj, part)?.let { out[part] = it }
            }
            return out
        }

        /** Double → ComfortLevel（与右栏 jsonToLevel 规则一致） */
        fun valueToLevel(v: Double): Int = when {
            v <= -0.4 -> ComfortIconView.LEVEL_COOL
            v <= -0.05 -> ComfortIconView.LEVEL_SLIGHTLY_COOL
            v < 0.05 -> ComfortIconView.LEVEL_JUST_RIGHT
            v < 0.4 -> ComfortIconView.LEVEL_SLIGHTLY_WARM
            else -> ComfortIconView.LEVEL_WARM
        }

        private fun extractValue(obj: JSONObject, part: BodyPart12): Double? {
            val sk = part.singleKey
            if (sk != null) return obj.optDoubleOrNull(sk)
            val lk = part.leftKey ?: return null
            val rk = part.rightKey ?: return obj.optDoubleOrNull(lk)
            val lv = obj.optDoubleOrNull(lk) ?: return obj.optDoubleOrNull(rk)
            val rv = obj.optDoubleOrNull(rk) ?: return lv
            return (lv + rv) / 2.0
        }

        private fun JSONObject.optDoubleOrNull(key: String): Double? {
            if (!has(key) || isNull(key)) return null
            val v = optDouble(key, Double.NaN)
            return if (v.isNaN()) null else v
        }
    }
}