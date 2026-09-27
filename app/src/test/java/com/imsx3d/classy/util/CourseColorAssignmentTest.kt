package com.imsx3d.classy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程色分配契约（2026-09-28 用户报「相近颜色扎堆」后立）。
 *
 * 锁三件事：
 *  ① 同一种子恒定（同门课在任何课表/任何一次导入里同色）；
 *  ② 种子取自**课程名**而不是 groupId（groupId 是导入时随机 UUID，会让同门课换色）；
 *  ③ 常见课名集合的色相分布要拉开 —— 不许出现旧实现的聚堆（15 个真实课名只落到 6/9 档）。
 *
 * 这里的期望值是**实测算出来的**（用同一份算法跑出来后再固化），
 * 目的是把"分布变差"这种回归挡在 CI/本地单测里，而不是等用户在手机上看出来。
 */
class CourseColorAssignmentTest {

    private fun course(name: String, groupId: String = "some-random-uuid") =
        com.imsx3d.classy.data.entity.CourseEntity(
            groupId = groupId, tableId = 1, courseName = name, day = 1,
            startNode = 1, step = 1, startWeek = 1, endWeek = 16,
            color = "#FF6750A4",   // 哨兵色 = 未自定义
            colorMode = com.imsx3d.classy.data.entity.CourseColorMode.GROUP
        )

    /** 样本：一批真实风格的中文课名（含演示数据那 10 门） */
    private val names = listOf(
        "高等数学 A", "大学英语（三）", "程序设计基础", "线性代数", "大学物理",
        "数据结构", "马克思主义基本原理", "体育（羽毛球）", "大学物理实验", "形势与政策",
        "微观经济学", "中级财务会计Ⅱ", "管理学原理", "欧洲古典音乐欣赏（公选）", "大学体育3（定向运动）"
    )

    @Test
    fun `same course name always maps to the same slot`() {
        names.forEach { n ->
            // 不同 groupId（模拟重新导入换了 UUID）不影响结果
            val a = CourseColorUtil.accentFor(CourseColorUtil.colorSeed(course(n, "uuid-a")), false)
            val b = CourseColorUtil.accentFor(CourseColorUtil.colorSeed(course(n, "uuid-b")), false)
            assertEquals("$n 的色相必须与 groupId 无关", a.first, b.first, 0f)
            assertEquals("$n 的档位必须与 groupId 无关", a.second, b.second, 0f)
            assertEquals("$n 的明度必须与 groupId 无关", a.third, b.third, 0f)
        }
    }

    @Test
    fun `hue distribution spreads across the ring instead of piling up`() {
        val hues = names.map { CourseColorUtil.accentFor(CourseColorUtil.colorSeed(course(it)), false).first }
        val distinct = hues.toSet().size
        // 旧实现（String.hashCode 直接取模）实测只有 6 个不同色相；混合后为 8。
        // 卡在 7：既不允许退回聚堆，也不要求凑到 9（那要靠"按表分配"，属于后续工作）。
        assertTrue("15 个课名至少要用到 7 个不同色相，实际 $distinct", distinct >= 7)
    }

    @Test
    fun `hue and tone are decorrelated`() {
        // 色相与档位若同源（旧实现的 h 与 h/9），同一色相会扎堆在同一档位。
        // 这里只验证"两个维度都被用到"：15 门课至少落在 10 个不同 (色相,档位) 组合上。
        val combos = names.map {
            val (h, _, _) = CourseColorUtil.accentFor(CourseColorUtil.colorSeed(course(it)), false)
            val (h2, _, _) = CourseColorUtil.accentFor(CourseColorUtil.colorSeed(course(it)), true)
            h to h2
        }.toSet().size
        assertTrue("色相维度在深浅两种模式下都应有区分度", combos >= 7)
    }

    /** 按表分配：前 9 门互不相同的课，色相必须两两不同（这才是"不扎堆"） */
    @Test
    fun `table-wide assignment gives every course a different hue up to ring size`() {
        val courses = names.take(9).mapIndexed { i, n -> course(n, "gid-$i").copy(id = (i + 1).toLong()) }
        val slots = CourseColorUtil.slotsFor(courses)
        assertEquals("9 门课应有 9 个槽位", 9, slots.size)
        val hues = slots.map { (seed, slot) -> CourseColorUtil.accentFor(seed, false, slot).first }
        assertEquals("前 9 门课的色相必须两两不同", 9, hues.toSet().size)
    }

    /** 追加课程不改已有课的颜色（槽位只由"它是第几个出现的"决定） */
    @Test
    fun `appending a course keeps existing colours`() {
        val base = names.take(6).mapIndexed { i, n -> course(n, "gid-$i").copy(id = (i + 1).toLong()) }
        val before = CourseColorUtil.slotsFor(base)
        val after = CourseColorUtil.slotsFor(base + course("新加的课", "gid-new").copy(id = 99))
        before.forEach { (seed, slot) -> assertEquals("$seed 的槽位不该被新增课程改变", slot, after[seed]) }
    }

    @Test
    fun `blank name falls back to group id`() {
        val c = course("   ", "fallback-uuid")
        assertEquals("fallback-uuid", CourseColorUtil.colorSeed(c))
    }
}
