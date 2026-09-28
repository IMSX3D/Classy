package com.imsx3d.classy.ui.screen.mine

import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the experimental notification control to its existing reminders section. */
class ReminderExperimentalTagContractTest {

    private companion object {
        const val REL = "app/src/main/java/com/imsx3d/classy/ui/screen/mine/ReminderScreen.kt"
    }

    private val source: String by lazy {
        // 路径三候选兜底：上游开发机写死的 macOS 绝对路径在别的机器上永远读不到。
        sequenceOf(
            System.getProperty("sleepy.test.root")?.let { java.io.File(it, REL) },
            java.io.File("app/$REL"),
            java.io.File(REL),
        ).filterNotNull().firstOrNull { it.isFile }?.readText()
            ?: error("Unable to load ReminderScreen.kt source")
    }

    @Test
    fun `fluid notification row keeps experimental tag in reminders screen`() {
        val fluidRow = source.substringAfter(
            "title = stringResource(R.string.reminder_fluid_title)",
            ""
        ).substringBefore("if (fluidEnabled)")
        // UI-26a：行组件参数名 tag → titleBadge（"标题后缀小胶囊"），语义不变
        assertTrue(
            "流式通知那一行必须仍挂「实验」小胶囊",
            fluidRow.contains("titleBadge = stringResource(R.string.reminder_experimental_tag)")
        )
        assertTrue(source.contains("stringResource(R.string.reminder_fluid_title)"))
    }
}
