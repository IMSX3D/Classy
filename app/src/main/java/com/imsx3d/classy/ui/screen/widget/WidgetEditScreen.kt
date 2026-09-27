package com.imsx3d.classy.ui.screen.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.SettingsScaffold
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.widget.WidgetEditViewModel

/**
 * Per-widget edit screen — composed of [WidgetEditSection] rows so future
 * per-widget settings (e.g. "show week number", "dim past periods") can
 * be added as additional sections without changing this screen's shape.
 *
 * Currently two sections: [WidgetEditScheduleSection] (pick which schedule
 * this widget displays) and [WidgetEditAliasSection] (issue#26 — original
 * name vs alias). Toggling either writes
 * [com.imsx3d.classy.widget.WidgetBindingStore] / AppPrefs and triggers
 * [com.imsx3d.classy.widget.WidgetUpdater.notifyDataChanged] so all 9
 * widget receivers re-read their binding and redraw.
 */
@Composable
fun WidgetEditScreen(
    widgetId: Int,
    onBack: () -> Unit
) {
    val vm = remember(widgetId) { WidgetEditViewModel(widgetId) }
    val state by vm.state.collectAsState()
    val colors = MaterialTheme.colorScheme

    // To add a new section later, append here — the screen picks it up
    // automatically. Each section must implement WidgetEditSection.
    val sections: List<WidgetEditSection> = remember {
        listOf(WidgetEditScheduleSection, WidgetEditAliasSection, WidgetEditScrollSection)
    }
    val scope = remember(state.currentBinding, state.availableTables, state.useAlias,
        state.receiverSimpleName, state.scrollEnabled) {
        WidgetEditScope(
            widgetId = widgetId,
            currentBinding = state.currentBinding,
            availableTables = state.availableTables,
            onSelectTable = { vm.setBinding(it) },
            useAlias = state.useAlias,
            onUseAliasChange = { vm.setUseAlias(it) },
            receiverSimpleName = state.receiverSimpleName,
            scrollEnabled = state.scrollEnabled,
            onScrollEnabledChange = { vm.setScrollEnabled(it) }
        )
    }

    // UI-7c: 页头统一成「我的」页那套 32sp 大标题 + 返回行（原来 M3 小标题顶栏）
    SettingsScaffold(
        title = stringResource(R.string.widget_edit_title),
        onBack = onBack
    ) {
        items(sections.size) { idx ->
            sections[idx].Content(scope)
        }
        }
}
