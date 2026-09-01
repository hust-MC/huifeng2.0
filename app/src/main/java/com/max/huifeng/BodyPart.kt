package com.max.huifeng

/**
 * 12 个身体部位定义（体感详情页模型）。
 *
 * 每部位在 3D 人像上的位置是固定的（坐标以 0..1 表示人像区域的相对位置），
 * 颜色随 [ComfortLevel] 变化。
 */
enum class BodyPart(
    val displayName: String,       // 中文显示名：头颈、肚脐…
    val engName: String,           // 英文：HeadNeck, Navel… 方便 JSON 反序列化
    /** 在人像区域中的相对位置（0..1） */
    val relX: Float,
    val relY: Float,
    /** 色块椭圆半径（相对人像短边） */
    val relRadius: Float
) {
    HEAD_NECK("头颈", "HeadNeck", 0.50f, 0.16f, 0.10f),
    NECK("颈部", "Neck", 0.46f, 0.21f, 0.07f),
    SHOULDER_NECK("肩颈", "ShoulderNeck", 0.55f, 0.24f, 0.10f),
    SHOULDER("肩部", "Shoulder", 0.66f, 0.28f, 0.09f),
    BACK("后背", "Back", 0.52f, 0.36f, 0.10f),
    LOWER_BACK("下腰", "LowerBack", 0.50f, 0.46f, 0.10f),
    NAVEL("肚脐", "Navel", 0.44f, 0.40f, 0.07f),
    HAND_BACK("手背", "HandBack", 0.78f, 0.46f, 0.06f),
    HAND("双手", "Hand", 0.84f, 0.52f, 0.06f),
    THIGH("大腿", "Thigh", 0.46f, 0.60f, 0.10f),
    CALF("小腿", "Calf", 0.46f, 0.78f, 0.08f),
    FOOT("脚掌", "Foot", 0.46f, 0.92f, 0.07f);

    companion object {
        /** JSON 反序列化用：英文名 -> BodyPart */
        private val byEngName: Map<String, BodyPart> = values().associateBy { it.engName }

        /** 给定英文 Key 解析 BodyPart，找不到返回 null */
        fun fromEngName(name: String?): BodyPart? = name?.let { byEngName[it] }
    }
}

/**
 * 12 部位的当前体感档位数据（按 Position 区分每个座位）。
 *
 * 用 Map<BodyPart, ComfortLevel> 表示；缺数据的位置不入 map。
 */
data class BodyPartComfort(
    val position: Int,
    val parts: Map<BodyPart, Int>          // 每个部位的体感档位（-2..2）
) {
    operator fun get(part: BodyPart): Int = parts[part] ?: ComfortIconView.LEVEL_JUST_RIGHT
    fun hasData(part: BodyPart): Boolean = parts.containsKey(part)
}
