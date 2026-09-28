package com.imsx3d.classy.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 源码级守卫: 渲染路径里调用 `awm.updateAppWidget(...)` 后不能再紧跟 `Bitmap.recycle()`。
 *
 * 背景:
 * - 第三方启动器(lawnchair / 部分 OEM 桌面)在 `updateAppWidget` 后异步消费
 *   RemoteViews.mBitmapCache 持有的 Bitmap; 若本进程立即 `recycle()` 释放 native pixel,
 *   启动器进程拿到的是 recycled bitmap → `setImageBitmap` 抛
 *   "trying to use a recycled bitmap" → RemoteViews.apply() 失败 →
 *   AppWidgetHostView 回落到"无法加载微件"错误视图。
 * - 修复见 [RemoteViewsWidgetHelper.renderAndPush] / [RemoteViewsWidgetHelper.pushScrollable]
 *   / [WeekGridWidgetProvider.renderWidget]: 三处显式删除 `bmp.recycle()` / `shellBitmap.recycle()`。
 *
 * 仓库无 Robolectric(Bitmap 像素管线无法在纯 JVM 跑), 采用源码静态检查守住契约。
 */
class WidgetBitmapLifecycleTest {

    private fun widgetSource(name: String): File {
        // try direct, then walk up to git root
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/com/imsx3d/classy/widget/$name")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$name not found above CWD=${File(".").absolutePath}")
    }

    /**
     * 在文件中查找所有 "updateAppWidget(" 调用, 对每个调用点向后扫描直到
     * 方法末尾(`}` 计数)或最多 60 行, 区间内不得出现 `.recycle()`.
     *
     * 注意: 仅做粗粒度括号/大括号扫描; 不解析完整 Kotlin 语法, 目标是
     * "误把 recycle 加回去" 这类粗错, 而非精确控制流分析。
     */
    private fun assertNoRecycleAfterUpdateAppWidget(file: File, friendlyName: String) {
        val lines = file.readLines()
        val violations = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].contains("updateAppWidget(")) {
                // 从下一行起扫描到方法结束或 60 行上限
                var braceDepth = 0
                var seenBrace = false
                for (j in (i + 1) until minOf(i + 60, lines.size)) {
                    val line = lines[j]
                    val codePart = line.substringBefore("//").trim()
                    // 只检查可执行代码: 注释行(// 在 recycle 之前)跳过,
                    // 防止"NOTE: 不能 .recycle()!"这类守卫注释误报。
                    if (codePart.contains(".recycle()")) {
                        violations += "$friendlyName:${j + 1}: ${line.trim()}"
                        break
                    }
                    for (c in line) {
                        if (c == '{') { braceDepth++; seenBrace = true }
                        else if (c == '}') braceDepth--
                    }
                    if (seenBrace && braceDepth == 0) break
                }
            }
            i++
        }
        assertTrue(
            "$friendlyName: recycle() found after updateAppWidget → $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `RemoteViewsWidgetHelper renderAndPush does not recycle after updateAppWidget`() {
        assertNoRecycleAfterUpdateAppWidget(
            widgetSource("RemoteViewsWidgetHelper.kt"),
            "RemoteViewsWidgetHelper"
        )
    }

    @Test
    fun `WeekGridWidgetProvider renderWidget does not recycle after updateAppWidget`() {
        assertNoRecycleAfterUpdateAppWidget(
            widgetSource("WeekGridWidgetProvider.kt"),
            "WeekGridWidgetProvider"
        )
    }

    /**
     * 补充: 确保修复注释被保留 (防止后人误读原始 commit 注释后回退)。
     * 检查三个文件里都有"不能 recycle"或"已删除"的中文说明锚点。
     */
    @Test
    fun `recycle guard comments are preserved in render sources`() {
        val rvh = widgetSource("RemoteViewsWidgetHelper.kt").readText()
        val wgp = widgetSource("WeekGridWidgetProvider.kt").readText()
        assertTrue(
            "RemoteViewsWidgetHelper.kt 缺失 recycle 守卫注释",
            rvh.contains("不能 bmp.recycle()") || rvh.contains("不能 recycle")
        )
        // UI-4k 起 WeekGrid 生产路径改真实布局、不再画 Canvas 位图，注释锚点也随之改写。
        // 把"文案契约"升级成"行为契约"：① 生产渲染函数体内不得出现位图下发；
        // ② 全 widget 包不得出现真实 .recycle() 调用（只有守卫注释在解释为什么不能回收）。
        val renderWidget = wgp.substringAfter("fun renderWidget").substringBefore("fun renderBitmap")
        assertTrue(
            "WeekGrid 生产路径 (renderWidget) 不得再下发位图",
            !renderWidget.contains("setImageViewBitmap") && !renderWidget.contains("renderBitmap")
        )
        val widgetDir = widgetSource("WeekGridWidgetProvider.kt").parentFile
            ?: error("widget 源码目录找不到")
        val widgetPkg = widgetDir.walkTopDown().filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        // 只查"真代码行"：注释里那句 `// NOTE: 不能 bmp.recycle()!` 正是**守卫本身**，不是调用。
        val recycleCalls = widgetPkg.lines().filter { line ->
            val code = line.substringBefore("//")
            Regex("""\.recycle\(\)""").containsMatchIn(code)
        }
        assertTrue(
            "widget 包不得出现真实 bitmap.recycle() 调用（RemoteViews 异步消费，回收即花屏）：$recycleCalls",
            recycleCalls.isEmpty()
        )
        assertFalse(
            "RemoteViewsWidgetHelper.kt 还残留旧的 binder-copy 误导注释",
            rvh.contains("拷贝 bitmap 到 binder 事务")
        )
        assertFalse(
            "WeekGridWidgetProvider.kt 还残留旧的 binder-copy 误导注释",
            wgp.contains("拷贝 bitmap 到 binder 事务")
        )
    }
}
