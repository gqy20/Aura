package com.xiaoqi.companion.core.record

import kotlinx.serialization.Serializable

/** 食物密度表条目:每 100g 可食部的能量与三大营养素(《中国食物成分表》第 6 版)。 */
@Serializable
data class FoodDensityEntry(
    val n: String,
    val c: String = "",
    val k: Double,
    val p: Double,
    val f: Double,
    val b: Double,
) {
    val name: String get() = n
    val kcalPer100g: Double get() = k
    val proteinPer100g: Double get() = p
    val fatPer100g: Double get() = f
    val carbsPer100g: Double get() = b
}

/**
 * 菜名 → 密度匹配。两段式提取的第二段:模型只出菜名 + 克重,营养由本表密度 × 克重计算,
 * 消掉"同一道菜每次估的热量都不同"的方差(roadmap MR 精度策略)。
 *
 * 匹配顺序:精确 key → 最长子串 key(≥2 字,避免"面"这种单字误命中)。
 * key 生成规则:全名 + 去括号核心名 + ［］内逗号分隔的别名,全部小写化。
 */
class FoodDensityTable(entries: List<FoodDensityEntry>) {

    private val keys: Map<String, FoodDensityEntry>

    init {
        val map = HashMap<String, FoodDensityEntry>()
        entries.forEach { entry ->
            entryKeys(entry.n).forEach { key ->
                // 短 key 优先让位:核心名比全名更好命中(模型多输出"米饭"而非"米饭（蒸）")
                map.putIfAbsent(key, entry)
            }
        }
        keys = map
    }

    fun lookup(dishName: String): FoodDensityEntry? {
        val query = dishName.trim().lowercase()
        if (query.isEmpty()) return null
        keys[query]?.let { return it }
        // 子串限定"烹饪前缀 + 食材结尾"形态(酱牛肉/红烧牛肉 ⊃ 牛肉,前缀 ≤2 字):
        // 纯食材密度对整道菜按克重换算才成立。牛肉面/蛋炒饭这类"食材+主食"复合名
        // 不以食材结尾,套主料密度会把一碗面算成半斤纯牛肉(E2E 实测 960kcal/120g 蛋白),必须拒绝。
        return keys.entries
            .asSequence()
            .filter { (key, _) ->
                key.length >= 2 &&
                    query.endsWith(key) &&
                    query.length - key.length <= 2
            }
            .maxByOrNull { (key, _) -> key.length }
            ?.value
    }

    fun size(): Int = keys.values.toSet().size

    private fun entryKeys(rawName: String): List<String> {
        val name = rawName.trim().lowercase()
        if (name.isEmpty()) return emptyList()
        val result = mutableSetOf(name)
        // 核心名:去掉所有括号段(米饭（蒸，代表值） → 米饭)
        val core = name.replace(CORE_STRIP, "").trim()
        if (core.isNotEmpty()) result += core
        // 别名:［松口蘑，松茸］段内按逗号/顿号拆分
        ALIAS_FIND.findAll(name).forEach { match ->
            match.groupValues[1]
                .split('，', ',', '、')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { alias -> result += alias }
        }
        return result.toList()
    }

    private companion object {
        // （）与 () 均为核心名限定语;［］为别名列表(成分表排版惯例)
        val CORE_STRIP = Regex("""[（(［\[][^）)］\]]*[）)］\]]""")
        val ALIAS_FIND = Regex("""［([^］]*)］""")
    }
}

/** 密度表来源;Android 实现读 assets,测试用内联条目。 */
interface FoodDensitySource {
    suspend fun load(): FoodDensityTable?
}
