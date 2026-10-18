package com.xiaoqi.companion.core.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FoodDensityTableTest {

    private val table = FoodDensityTable(
        listOf(
            FoodDensityEntry(n = "米饭（蒸，代表值）", c = "谷类", k = 116.0, p = 2.6, f = 0.3, b = 25.9),
            FoodDensityEntry(n = "面条（生，代表值）", c = "谷类", k = 301.0, p = 10.9, f = 0.6, b = 63.5),
            FoodDensityEntry(n = "鸡蛋（代表值）", c = "蛋类", k = 139.0, p = 13.3, f = 8.8, b = 2.8),
            FoodDensityEntry(n = "牛肉（里脊）", c = "畜肉类", k = 107.0, p = 22.2, f = 0.9, b = 2.4),
            FoodDensityEntry(n = "松蘑（干）［松口蘑，松茸］", c = "菌类", k = 112.0, p = 20.3, f = 3.2, b = 48.2),
        )
    )

    @Test
    fun lookup_exactName() {
        val entry = table.lookup("鸡蛋（代表值）")
        assertNotNull(entry)
        assertEquals(139.0, entry!!.kcalPer100g, 0.001)
    }

    @Test
    fun lookup_strippedCoreName_matches() {
        // 模型说"鸡蛋",库内全名是"鸡蛋（代表值）"——核心名命中
        assertEquals(139.0, table.lookup("鸡蛋")!!.kcalPer100g, 0.001)
        assertEquals(116.0, table.lookup("米饭")!!.kcalPer100g, 0.001)
    }

    @Test
    fun lookup_substring_matchesDishNameWithModifiers() {
        // "酱牛肉":食材结尾 + ≤2字烹饪前缀 → 命中主食材密度
        assertEquals(107.0, table.lookup("酱牛肉")!!.kcalPer100g, 0.001)
        assertEquals(107.0, table.lookup("红烧牛肉")!!.kcalPer100g, 0.001)
        // "食材+主食"复合菜不以食材结尾:套主料密度会把一碗面算成半斤纯牛肉(E2E 实测),必须拒绝
        assertNull(table.lookup("红烧牛肉面"))
        assertNull(table.lookup("牛肉面"))
    }

    @Test
    fun lookup_aliasInBrackets_matches() {
        // 成分表别名:［松口蘑，松茸］
        assertEquals(112.0, table.lookup("松茸")!!.kcalPer100g, 0.001)
        assertEquals(112.0, table.lookup("松口蘑")!!.kcalPer100g, 0.001)
    }

    @Test
    fun lookup_caseInsensitiveAndTrims() {
        assertEquals(139.0, table.lookup("  鸡蛋  ")!!.kcalPer100g, 0.001)
    }

    @Test
    fun lookup_longestSubstringWins() {
        // "牛肉（里脊）"核心名是"牛肉",若另有纯"牛肉（均值）"条目,核心名"牛肉"只有一个 key(先到先得)
        val twoBeef = FoodDensityTable(
            listOf(
                FoodDensityEntry(n = "牛肉（瘦）", k = 106.0, p = 20.2, f = 2.3, b = 1.2),
                FoodDensityEntry(n = "牛肉干", k = 550.0, p = 45.6, f = 40.0, b = 1.9),
            )
        )
        // "清炒牛肉干"同时含"牛肉"(核心名)与"牛肉干"(全名),长 key 胜
        assertEquals(550.0, twoBeef.lookup("清炒牛肉干")!!.kcalPer100g, 0.001)
    }

    @Test
    fun lookup_emptyAndUnknown() {
        assertNull(table.lookup(""))
        assertNull(table.lookup("  "))
        assertNull(table.lookup("麻婆豆腐")) // 库里没有
    }
}
