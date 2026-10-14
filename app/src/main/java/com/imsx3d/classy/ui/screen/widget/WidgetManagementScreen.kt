package com.imsx3d.classy.ui.screen.widget

import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp


import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.imsx3d.classy.util.AppPrefs
import kotlinx.coroutines.launch
import com.imsx3d.classy.ui.component.SettingsSwitchRow
import com.imsx3d.classy.ui.component.SettingsRowDivider
import com.imsx3d.classy.ui.component.SectionHeader
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.SettingsScaffold
import com.imsx3d.classy.ui.component.SettingsGroupCard
import com.imsx3d.classy.widget.PlacedWidgetItem

/** 小组件：显示偏好、添加与管理，以及手动刷新。 */
@Composable
fun WidgetManagementScreen(
    onBack: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf(false) }
    fun refreshWidgets(showFeedback: Boolean = false) {
        if (showFeedback && refreshing) return
        if (showFeedback) refreshing = true
        scope.launch {
            try {
                com.imsx3d.classy.widget.WidgetUpdater.notifyDataChanged(context)
                refreshError = false
                if (showFeedback) android.widget.Toast.makeText(context, context.getString(R.string.settings_widget_refresh_requested), android.widget.Toast.LENGTH_SHORT).show()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                refreshError = true
            } finally { if (showFeedback) refreshing = false }
        }
    }
    var widgetColorless by remember { mutableStateOf(AppPrefs.isWidgetColorless(context)) }
    var titleCentered by remember { mutableStateOf(AppPrefs.isCourseTitleCentered(context, widget = true)) }
    var showTableName by remember { mutableStateOf(AppPrefs.isWidgetShowTableName(context)) }
    var useAlias by remember { mutableStateOf(AppPrefs.isWidgetUseAlias(context)) }
    var nearestBusyDay by remember { mutableStateOf(AppPrefs.isNearestBusyDay(context)) }
    val vm = com.imsx3d.classy.widget.rememberWidgetManagementViewModel()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        vm.reload()
    }
    val items by vm.state.collectAsState()
    val colors = MaterialTheme.colorScheme

    // UI-7c: 页头统一成「我的」页那套 32sp 大标题 + 返回行（原来 M3 小标题顶栏）；
    // 空态从「整屏 Column」改成 LazyColumn 里的一个 item（fillParentMaxSize 撑满剩余高度居中）
    SettingsScaffold(
        title = stringResource(R.string.widget_manage_title),
        onBack = onBack,
        verticalSpacing = 8.dp
    ) {

        item { SectionHeader(stringResource(R.string.widget_placed_title), subtitle = stringResource(R.string.widget_placed_hint), topSpacing = 0.dp) }
        if (items == null) {
            item { Text(stringResource(R.string.loading), Modifier.padding(24.dp), color = colors.onSurfaceVariant) }
        } else if (items.orEmpty().isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.widget_manage_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        } else {
            items(items.orEmpty(), key = { it.widgetId }) { item ->
                PlacedWidgetRow(item = item, onClick = { onSelect(item.widgetId) })
            }
        }
        item { WidgetAddPanel(onRefresh = { vm.reload() }) }
        item { SectionHeader(stringResource(R.string.widget_shared_title), subtitle = stringResource(R.string.widget_shared_hint)) }
        item {
            SettingsGroupCard {
                com.imsx3d.classy.ui.component.SettingsSegmentedRow(
                    title = stringResource(R.string.widget_course_title_position),
                    options = listOf(stringResource(R.string.course_title_top), stringResource(R.string.course_title_center)),
                    selectedKey = if (titleCentered) 1 else 0,
                    onSelect = {
                        titleCentered = it == 1
                        AppPrefs.setCourseTitleCentered(context, titleCentered, widget = true)
                        refreshWidgets()
                    }
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.widget_show_table_name),
                    checked = showTableName,
                    onCheckedChange = {
                        showTableName = it
                        AppPrefs.setWidgetShowTableName(context, it)
                        refreshWidgets()
                    }
                )
                SettingsRowDivider()
                com.imsx3d.classy.ui.component.SettingsSegmentedRow(
                    title = stringResource(R.string.widget_edit_section_alias),
                    subtitle = stringResource(R.string.widget_alias_hint),
                    options = listOf(stringResource(R.string.settings_name_original), stringResource(R.string.settings_name_alias)),
                    selectedKey = if (useAlias) 1 else 0,
                    onSelect = {
                        useAlias = it == 1
                        AppPrefs.setWidgetUseAlias(context, useAlias)
                        refreshWidgets()
                    }
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_widget_colorless),
                    subtitle = stringResource(R.string.settings_widget_colorless_sub),
                    checked = widgetColorless,
                    onCheckedChange = {
                        widgetColorless = it
                        AppPrefs.setWidgetColorless(context, it)
                        refreshWidgets()
                    }
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_nearest_busy_day),
                    subtitle = stringResource(R.string.settings_nearest_busy_day_sub),
                    checked = nearestBusyDay,
                    onCheckedChange = {
                        nearestBusyDay = it
                        AppPrefs.setNearestBusyDay(context, it)
                        refreshWidgets()
                    }
                )
            }
        }
        item { SectionHeader(stringResource(R.string.widget_refresh_section)) }
        item {
            androidx.compose.material3.TextButton(
                enabled = !refreshing,
                onClick = {
                    refreshWidgets(showFeedback = true)
                    vm.reload()
                }
            ) { Text(stringResource(R.string.mine_refresh_widgets)) }
            if (refreshError) Text(stringResource(R.string.settings_widget_refresh_error), color = colors.error)
        }

    }
}

@Composable
private fun PlacedWidgetRow(item: PlacedWidgetItem, onClick: () -> Unit) {
    SettingsGroupCard {
        com.imsx3d.classy.ui.component.SettingsGroupRow(
            title = stringResource(item.variant.displayNameRes),
            subtitle = item.tableName ?: stringResource(R.string.widget_edit_default_label),
            onClick = onClick,
            trailing = {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        )
    }
}
