package com.imsx3d.classy.data.undo

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Architecture guard only: real rollback/undo behaviour is covered by RepositoryQualityTest.
 * Public writes must join atomicEdit, which captures once before any DAO change.
 */
class UndoCaptureCoverageTest {

    private val source: String by lazy {
        sequenceOf(
            File("app/src/main/java/com/imsx3d/classy/data/repository/ScheduleRepository.kt"),
            File("src/main/java/com/imsx3d/classy/data/repository/ScheduleRepository.kt")
        ).first { it.isFile }.readText()
    }

    /** 会写课表数据的公开方法 — 漏一个 capture, 对应动作的撤回按钮就消失一次。 */
    private val mustCapture = listOf(
        "insertTable", "updateTable", "updateTableRemappingCourses", "deleteTable",
        "insertCourse", "insertCourses", "insertCoursesKeepingGroups",
        "replaceCoursesKeepingGroups", "updateCourse", "updateCourseGroup",
        "applyDiff", "deleteCourse", "deleteCourseGroup", "replaceCourses"
    )

    /** 方法签名起点 → 下一个方法声明之间的源码段。 */
    private fun segmentOf(method: String): String {
        val start = Regex("""fun\s+$method\s*\(""").find(source)?.range?.first
            ?: error("ScheduleRepository 缺少方法 $method — 契约清单需同步")
        val next = Regex("""\n\s*(private\s+)?(suspend\s+)?fun\s+\w+""")
            .find(source, startIndex = start + 20)?.range?.first ?: source.length
        return source.substring(start, next)
    }

    @Test
    fun `every public write method captures undo snapshot`() {
        val missing = mustCapture.filter { "= atomicEdit(" !in segmentOf(it) }
        assertTrue(
            "公开写方法缺少 atomicEdit: $missing — 这些动作的撤回按钮将不亮(2026-09-10 编辑课程翻车)",
            missing.isEmpty()
        )
    }

    @Test
    fun `applyDiff captures before first dao write`() {
        val seg = segmentOf("applyDiff")
        val captureAt = seg.indexOf("atomicEdit(")
        val firstWrite = listOf("deleteByIds", "updateAll", "insertAll")
            .map { seg.indexOf(it) }
            .filter { it >= 0 }
            .min()
        assertTrue(
            "applyDiff 必须先 atomicEdit 再动 DAO — 否则编辑课程无快照可撤",
            captureAt in 0 until firstWrite
        )
    }
}
