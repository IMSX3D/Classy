package com.imsx3d.classy.ui.screen.mine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imsx3d.classy.BuildConfig
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.GlasenseSnackbarHost
import com.imsx3d.classy.ui.component.SectionHeader
import com.imsx3d.classy.ui.component.SettingsGroupCard
import com.imsx3d.classy.ui.component.SettingsGroupRow
import com.imsx3d.classy.ui.component.SettingsRowDivider
import com.imsx3d.classy.ui.component.SettingsScaffold
import com.imsx3d.classy.ui.component.SettingsSwitchRow
import com.imsx3d.classy.util.AppIdentity
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.util.UpdateInfo
import com.imsx3d.classy.util.UpdateManager
import com.imsx3d.classy.util.UpdateNotifier
import com.imsx3d.classy.ui.theme.noRippleClickable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.imsx3d.classy.ui.component.GlasenseIconButton
import androidx.compose.ui.res.stringArrayResource

/**
 * 「关于」页（UI-13a 重做）。
 *
 * 结构（自 Cresto / Pear Wall 的关于页借鉴，落到我们的组件上）：
 *   图标 + 应用名 + 版本 → 「开发者的心声」 → 「开发者」 → 「项目」 →（「更新」）→「特别鸣谢」→ 页尾提示。
 *
 * 三处刻意的取舍：
 *  ① **版本只在 hero 出现一次**（Cresto 在 hero 下面还有一张"版本信息"卡，我们要简洁 → 合并）。
 *  ② **更新相关整组由 [AppIdentity.hasRepo] 把关**：仓库未公开前不渲染，也不联网检查 ——
 *     本包签名与上游不同，若继续查上游 Releases，会提示"新版可用"并下载官方包（装不上、还可能
 *     要求先卸载 → 课表数据全丢）。仓库建好后填 `AppIdentity.REPO_URL` / `REPO_SLUG` 即自动恢复。
 *  ③ 上游的 QQ 群与作者邮箱入口已移除（那些是上游作者的联系方式，用户找过去会找错人）；
 *     我们的联系入口 = GitHub 仓库（同样等仓库建好后随 ② 一起出现）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicense: () -> Unit = {},
    updateNoticeVisible: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unknownErrorMessage = stringResource(R.string.error_unknown)
    val latestVersionFormat = stringResource(R.string.about_update_latest)
    var uiState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val updateAvailable by UpdateNotifier.updateAvailable.collectAsState()
    var updateCheckEnabled by remember { mutableStateOf(AppPrefs.isUpdateCheckEnabled(context)) }

    fun openUrl(url: String) {
        if (url.isBlank()) return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    /** 复制开发者 QQ（行与行尾图标共用；不拉起 QQ，见 AppIdentity.AUTHOR_QQ 的说明） */
    fun copyAuthorQq() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("QQ", AppIdentity.AUTHOR_QQ))
        Toast.makeText(
            context,
            context.getString(R.string.about_contact_copied, AppIdentity.AUTHOR_QQ),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun checkUpdate() {
        if (uiState is UpdateUiState.Checking) return
        uiState = UpdateUiState.Checking
        scope.launch {
            runCatching { UpdateManager.fetchUpdateInfo(context) }
                .onSuccess { info ->
                    if (info.isUpdateAvailable) {
                        uiState = UpdateUiState.UpdateAvailable(
                            info.version, info.changelog, info.downloadUrl
                        )
                    } else {
                        uiState = UpdateUiState.NoUpdate(info.version)
                    }
                }
                .onFailure { uiState = UpdateUiState.Failed(it.message ?: unknownErrorMessage, isCheckFailure = true) }
        }
    }

    fun startDownload(version: String, changelog: String, url: String) {
        if (!com.imsx3d.classy.util.isValidDownloadUrl(url)) {
            uiState = UpdateUiState.UpdateAvailable(version, changelog, "")
            return
        }
        val info = UpdateInfo(version, changelog, url, true)
        uiState = UpdateUiState.Downloading(0)
        downloadJob = scope.launch {
            runCatching {
                UpdateManager.downloadApk(context, info) { progress ->
                    uiState = UpdateUiState.Downloading(progress)
                }
            }.onSuccess { file ->
                uiState = UpdateUiState.Installing
                UpdateManager.install(context, file)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) {
                    uiState = UpdateUiState.UpdateAvailable(version, changelog, url)
                } else {
                    uiState = UpdateUiState.Failed(
                        e.message ?: unknownErrorMessage, version, changelog, url
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState) {
        val current = uiState
        if (current is UpdateUiState.NoUpdate) {
            snackbarHostState.showSnackbar(latestVersionFormat.format(current.version))
            uiState = UpdateUiState.Idle
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.about_title),
        onBack = onBack,
        snackbarHost = { GlasenseSnackbarHost(hostState = snackbarHostState) },
        // 有新版时整页轻刷主题色 5%（沿用旧版行为）；底色交给 Scaffold，正文比屏幕短也不会露白
        containerColor = if (updateNoticeVisible) colors.primary.copy(alpha = 0.05f) else colors.background
    ) {
        if (updateNoticeVisible && updateAvailable != null) {
            item {
                UpdateBanner(
                    version = updateAvailable!!.version,
                    onClick = {
                        openUrl("${AppIdentity.REPO_URL}/releases/tag/v${updateAvailable!!.version}")
                    },
                    onDismiss = { UpdateNotifier.dismiss(updateAvailable!!.version, context) }
                )
            }
        }

        // ── 头部：图标 + 名称 + 版本（版本只在这里出现一次）──
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp).clip(RoundedCornerShape(22.dp))
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.about_version_line, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }

        // ── 开发者的心声（长文：逐段 + 段间距，行高放宽到 22sp 才读得下去）──
        item {
            SectionHeader(title = stringResource(R.string.about_section_voice), topSpacing = 12.dp)
        }
        item {
            SettingsGroupCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    stringArrayResource(R.array.about_heart_paragraphs).forEach { paragraph ->
                        Text(
                            text = paragraph,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = colors.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "—— ${AppIdentity.AUTHOR_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    )
                }
            }
        }

        // ── 开发者 ──
        item {
            SectionHeader(title = stringResource(R.string.about_section_developer), topSpacing = 12.dp)
        }
        item {
            SettingsGroupCard {
                SettingsGroupRow(
                    title = AppIdentity.AUTHOR_NAME,
                    subtitle = stringResource(R.string.about_dev_tagline),
                    onClick = { openUrl(AppIdentity.AUTHOR_URL) },
                    leading = { InitialAvatar("I", colors.primary) },
                    trailing = { RowChevron() }
                )
            }
        }

        // ── 项目：开源地址 / 开源许可 /（反馈）──
        item {
            SectionHeader(title = stringResource(R.string.about_section_project), topSpacing = 12.dp)
        }
        item {
            SettingsGroupCard {
                // 仓库未建好前：只显示"搭建中"，不给假链接
                SettingsGroupRow(
                    title = stringResource(R.string.about_repo),
                    subtitle = if (AppIdentity.hasRepo) {
                        AppIdentity.REPO_URL.removePrefix("https://")
                    } else {
                        stringResource(R.string.about_repo_pending_detail)
                    },
                    onClick = if (AppIdentity.hasRepo) {
                        { openUrl(AppIdentity.REPO_URL) }
                    } else {
                        null
                    },
                    trailing = {
                        if (AppIdentity.hasRepo) {
                            RowChevron()
                        } else {
                            Text(
                                text = stringResource(R.string.about_repo_pending),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.outline
                            )
                        }
                    }
                )
                SettingsRowDivider()
                SettingsGroupRow(
                    title = stringResource(R.string.about_license_title),
                    subtitle = stringResource(R.string.about_license_row_detail),
                    onClick = onOpenLicense,
                    trailing = { RowChevron() }
                )
                if (AppIdentity.hasRepo) {
                    SettingsRowDivider()
                    SettingsGroupRow(
                        title = stringResource(R.string.about_issue),
                        subtitle = stringResource(R.string.about_issue_detail),
                        onClick = { openUrl(AppIdentity.newIssueUrl()) },
                        trailing = { RowChevron() }
                    )
                }
                // 联系开发者（UI-7e 用户令「关于页面留一个 QQ 联系方式方便大家联系我」）：
                // 号码**直接印在副标题里**（看得见、抄得走），点一下复制到剪贴板 + toast。
                // 不拉起 QQ —— 装的人手机上不一定有，复制才是"一定成功"的动作。
                SettingsRowDivider()
                SettingsGroupRow(
                    title = stringResource(R.string.about_contact_dev),
                    subtitle = stringResource(R.string.about_contact_dev_detail, AppIdentity.AUTHOR_QQ),
                    onClick = { copyAuthorQq() },
                    trailing = {
                        GlasenseIconButton(
                            icon = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.about_contact_copy_a11y),
                            onClick = { copyAuthorQq() },
                            compact = true
                        )
                    }
                )
            }
        }

        // Independent test builds keep repository links but never offer production updates.
        if (AppIdentity.hasReleaseUpdates) {
            item {
                SectionHeader(title = stringResource(R.string.about_section_update), topSpacing = 12.dp)
            }
            item {
                SettingsGroupCard {
                    SettingsGroupRow(
                        title = stringResource(R.string.about_update),
                        subtitle = stringResource(R.string.about_update_detail),
                        onClick = { checkUpdate() },
                        trailing = { RowChevron() }
                    )
                    SettingsRowDivider()
                    SettingsSwitchRow(
                        title = stringResource(R.string.about_update_check),
                        subtitle = stringResource(R.string.about_update_check_detail),
                        checked = updateCheckEnabled,
                        onCheckedChange = { v ->
                            updateCheckEnabled = v
                            AppPrefs.setUpdateCheckEnabled(context, v)
                            if (!v) UpdateNotifier.clearCache()
                        }
                    )
                }
            }
        }

        // ── 特别鸣谢 ──
        item {
            SectionHeader(title = stringResource(R.string.about_section_credits), topSpacing = 12.dp)
        }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsGroupCard {
                    SettingsGroupRow(
                        title = "sleepy",
                        subtitle = stringResource(R.string.about_sleepy_subtitle),
                        onClick = { openUrl(AppIdentity.SLEEPY_URL) },
                        leading = { InitialAvatar("S", colors.primary) },
                        trailing = { RowChevron() }
                    )
                    SettingsRowDivider()
                    SettingsGroupRow(
                        title = "Nevoit",
                        subtitle = stringResource(R.string.about_nevoit_subtitle),
                        onClick = { openUrl(AppIdentity.NEVOIT_URL) },
                        leading = { InitialAvatar("N", colors.primary) },
                        trailing = { RowChevron() }
                    )
                    SettingsRowDivider()
                    SettingsGroupRow(
                        title = "Kyant0",
                        subtitle = stringResource(R.string.about_kyant_subtitle),
                        onClick = { openUrl(AppIdentity.KYANT_URL) },
                        leading = { InitialAvatar("K", colors.primary) },
                        trailing = { RowChevron() }
                    )
                }
                Footnote(stringResource(R.string.about_sleepy_footnote))
                Footnote(stringResource(R.string.about_credits_footnote))
            }
        }

        // ── 页尾提示：更新节奏预期（脚注样式，不与正文抢注意力）──
        item {
            Text(
                text = stringResource(R.string.about_update_notice),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp)
            )
        }
    }

    UpdateChangelogDialog(
        state = uiState,
        onDismiss = { uiState = UpdateUiState.Idle },
        onDownload = { version, changelog, url -> startDownload(version, changelog, url) },
        onCancelDownload = { downloadJob?.cancel() },
        onRetry = { version, changelog, url ->
            val failed = uiState as? UpdateUiState.Failed
            if (failed?.isCheckFailure == true) checkUpdate()
            else startDownload(version, changelog, url)
        }
    )
}

/** 行右侧的标准「›」——四处复用，统一尺寸与颜色 */
@Composable
private fun RowChevron() {
    Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(20.dp)
    )
}

/**
 * 首字母头像（40dp 圆）—— 我们不外链网络头像，用字母圆代替：
 * 一眼能区分"这是人/项目"，也不会因为对方换头像而整页错位。
 */
@Composable
private fun InitialAvatar(letter: String, tint: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}

/** 卡下脚注：比正文更轻（bodySmall + 次要色），用它承载"一句说明" */
@Composable
private fun Footnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)
    )
}

/** 冷启动检查到新版可用时在「关于」顶部展示的横幅, 点击跳 Releases tag 页 */
@Composable
private fun UpdateBanner(
    version: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.primary.copy(alpha = 0.12f))
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .noRippleClickable(onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.NewReleases,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.about_update_available, "v$version"),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        GlasenseIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.about_update_dismiss),
            onClick = onDismiss,
            tint = colors.primary)
    }
}
