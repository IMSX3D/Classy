package com.imsx3d.classy.ui.screen.mine

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.SectionHeader
import com.imsx3d.classy.ui.component.SettingsGroupCard
import com.imsx3d.classy.ui.component.StatusBarScrim
import com.imsx3d.classy.ui.component.SettingsGroupFold
import com.imsx3d.classy.ui.component.SettingsGroupRow
import com.imsx3d.classy.ui.component.SettingsPageHeader
import com.imsx3d.classy.ui.component.SettingsRowDivider
import com.imsx3d.classy.ui.component.SettingsSwitchRow
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.imsx3d.classy.util.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

/** 通用：性能、语言和恢复默认设置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppPrefs.getLanguage(context)) }

    val languages = listOf(
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "es" to "Español"
    )

    var languageExpanded by rememberSaveable { mutableStateOf(false) }

    // 显示项变更后立即刷小组件(管线自 AppearanceScreen 迁移保留)
    val widgetScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    // UI-20a：恢复默认设置（确认弹窗 + 重置动作）
    var showResetDialog by remember { mutableStateOf(false) }
    fun performReset() {
        AppPrefs.resetSettings(context)
        // 设置清空后要把"有副作用的东西"重新按默认值落地：
        // 通知按默认开关重排、小组件按默认外观重画、更新提醒缓存清空，最后重建 Activity 让
        // 主题/语言/底栏形态等一次性读取的值生效。
        runCatching { com.imsx3d.classy.SleepyApp.get().notificationScheduler.scheduleAll() }
        runCatching { com.imsx3d.classy.util.UpdateNotifier.clearCache() }
        widgetScope.launch { com.imsx3d.classy.widget.WidgetUpdater.notifyDataChanged(context) }
        android.widget.Toast.makeText(context, context.getString(R.string.settings_reset_done), android.widget.Toast.LENGTH_SHORT).show()
        (context as? android.app.Activity)?.recreate()
    }

    // UI-17a：整页包 Box，内容之后盖一条状态栏遮罩（否则滚动时正文与状态栏时钟重叠）
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
    ) { padding ->
        LazyColumn(
            // UI-7a: 页头换成大标题后，顶部不再需要 Scaffold 的 topBar padding —— 由页头自己吃状态栏
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // UI-7a（2026-09-27 用户令"设置类三页仍是 M3 结构"）：页头统一成「我的」页那套
            // 32sp 大标题 + 返回行 —— 与 M3 小标题顶栏混在一起时像两个 App。
            item {
                // UI-25a：同 SettingsScaffold —— 页头补 12dp 底边距，让"大字 → 首块"= 28dp；
                // 因此下面**第一个**分组标题的 topSpacing 用 0（见分组①）。
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(bottom = 12.dp)
                ) {
                    SettingsPageHeader(
                        title = stringResource(R.string.mine_general),
                        onBack = onBack
                    )
                }
            }

            item { SettingsGroupCard {
                var highRefresh by remember { mutableStateOf(AppPrefs.isHighRefresh(context)) }
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_high_refresh),
                    checked = highRefresh,
                    onCheckedChange = { on ->
                        highRefresh = on
                        AppPrefs.setHighRefresh(context, on)
                        val activity = context as? android.app.Activity
                        if (activity == null) {
                            // 理论不可达 (本页只从 MainActivity 进入); 留日志防静默失败
                            Log.w("GeneralSettings", "high refresh toggle: context is not Activity, apply skipped")
                            return@SettingsSwitchRow
                        }
                        com.imsx3d.classy.util.HighRefreshRate.apply(activity, on)
                    }
                )
            } }
            // ── 分组④ 语言 (v1.0.56 T4: 折叠卡 — 收起只显当前语言, 点开展开 5 项) ──
            item {
                SectionHeader(
                    title = stringResource(R.string.settings_language),
                    topSpacing = 12.dp
                )
            }

            item {
                // UI-9a：语言本来就是「一张卡 + 折叠」，只是换成统一的分组折叠行（标题显当前语言）
                val currentLabel = languages.firstOrNull { it.first == language }?.second ?: language
                SettingsGroupCard {
                    SettingsGroupFold(
                        title = currentLabel,
                        expanded = languageExpanded,
                        onToggle = { languageExpanded = !languageExpanded }
                    ) {
                        languages.forEach { (code, label) ->
                            val selected = language == code
                            Row(
                                modifier = Modifier.fillMaxWidth().noRippleClickable {
                                    language = code
                                    AppPrefs.setLanguage(context, code)
                                    (context as? android.app.Activity)?.recreate()
                                }.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = label, style = MaterialTheme.typography.bodyLarge, color = if (selected) colors.primary else colors.onSurface)
                                if (selected) Icon(Icons.Outlined.Check, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                            }
                            if (code != languages.last().first) SettingsRowDivider()
                        }
                    }
                }
            }

            // ── 分组⑤ 重置（UI-20a）：调乱了能退回来 ──
            item {
                SectionHeader(
                    title = stringResource(R.string.settings_reset_group),
                    topSpacing = 12.dp
                )
            }
            item {
                SettingsGroupCard {
                    SettingsGroupRow(
                        title = stringResource(R.string.settings_reset_all),
                        subtitle = stringResource(R.string.settings_reset_all_sub),
                        onClick = { showResetDialog = true },
                        titleColor = colors.error
                    )
                }
            }


        }
    }
        StatusBarScrim(colors.background)
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_reset_confirm_body))
                    Spacer(Modifier.height(8.dp))
                    // UI-31d：破坏性动作 → destructive 色（C3 颜色管状态），按钮形态与其它弹窗一致
                    com.imsx3d.classy.ui.component.DialogActionButtons(
                        confirmText = stringResource(R.string.settings_reset_confirm_ok),
                        onConfirm = {
                            showResetDialog = false
                            performReset()
                        },
                        dismissText = stringResource(R.string.cancel),
                        onDismiss = { showResetDialog = false },
                        destructive = true
                    )
                }
            },
            // UI-31d：弹窗按钮统一走「色块按钮行」（DialogActionButtons），不再用裸 TextButton
            confirmButton = {},
            dismissButton = {}
        )
    }
}
