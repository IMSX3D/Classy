package com.imsx3d.classy.ui.screen.mine

import androidx.compose.runtime.saveable.SaverScope
import com.imsx3d.classy.data.CustomTheme
import org.junit.Assert.*
import org.junit.Test

class CustomThemeDraftSaverTest {
    private val scope = SaverScope { true }
    @Test fun `unsaved theme with empty identity and partial name survives recreation`() {
        val draft = CustomTheme("", "", "#123456", "#654321", "#ABCDEF", 123.5, 7.5, 42L)
        val saved = with(CustomThemeDraftSaver) { scope.save(draft) }!!
        assertEquals(draft, CustomThemeDraftSaver.restore(saved))
    }
    @Test fun `baseline and modified draft remain distinct after restoration`() {
        val initial = CustomTheme("existing", "原名", "#123456", "#654321", "#ABCDEF", 123.5, 7.5, 42L)
        val changed = initial.copy(name = "新名称", primary = "#FF0000")
        val baseline = CustomThemeDraftSaver.restore(with(CustomThemeDraftSaver) { scope.save(initial) }!!)!!
        val restored = CustomThemeDraftSaver.restore(with(CustomThemeDraftSaver) { scope.save(changed) }!!)!!
        assertNotEquals(baseline, restored)
        assertEquals(initial, restored.copy(name = initial.name, primary = initial.primary))
    }
}
