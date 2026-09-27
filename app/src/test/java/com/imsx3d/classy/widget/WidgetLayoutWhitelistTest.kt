package com.imsx3d.classy.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RemoteViews 类白名单契约 (2026-09-14 事故回归):
 * widget 布局会被 launcher 进程经 RemoteViews 反序列化 inflate, 只允许
 * @RemoteView 白名单类。android.view.View / Space 等不在白名单 —
 * 页脚透明条曾用 <View>, 溢出页脚一启用 launcher 端即 InflateException,
 * 用户侧表现为「载入窗口小部件时出现问题」。此测试静态扫描全部 widget_*.xml,
 * 任何非白名单元素直接红。
 */
class WidgetLayoutWhitelistTest {

    private val whitelist = setOf(
        "FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout",
        "GridView", "ListView", "StackView", "AdapterViewFlipper", "ViewFlipper",
        "TextView", "Button", "ImageButton", "ImageView", "CheckBox",
        "RadioButton", "CheckedTextView", "ProgressBar", "SeekBar", "RatingBar",
        "AnalogClock", "DigitalClock", "TextClock", "ViewStub", "ViewAnimator",
    )

    @Test
    fun `widget layouts only use RemoteViews-whitelisted classes`() {
        val dir = File("src/main/res/layout")
        val files = dir.listFiles { f ->
            f.name.startsWith("widget_") && f.name.endsWith(".xml")
        }.orEmpty()
        assertTrue("未找到任何 widget_*.xml (路径假设失效?)", files.isNotEmpty())

        val offenders = buildString {
            for (f in files) {
                val tags = Regex("<([A-Za-z][A-Za-z0-9.]*)[\\s/>]")
                    .findAll(f.readText())
                    .map { it.groupValues[1] }
                    .filter { it.first().isUpperCase() || it.contains('.') }
                    .distinct()
                for (t in tags) {
                    val simple = t.substringAfterLast('.')
                    // 全限定名只放行 android 框架类; 自定义 View 一律需 @RemoteView, 本仓没有
                    if (t.contains('.') && !t.startsWith("android.")) {
                        append("${f.name}: $t (非 android 框架类)\n")
                    } else if (simple !in whitelist) {
                        append("${f.name}: $t\n")
                    }
                }
            }
        }
        assertTrue(
            "widget 布局含 RemoteViews 白名单外类 (launcher 端会 InflateException):\n$offenders",
            offenders.isEmpty(),
        )
    }
}
