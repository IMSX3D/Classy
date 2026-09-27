package com.imsx3d.classy.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.theme.ThemePresets
import com.imsx3d.classy.util.DateUtils
import com.imsx3d.classy.util.WeekDisplayStatus
import com.nevoit.glasense.theme.GlasenseDarkPalette
import com.nevoit.glasense.theme.GlasenseLightPalette
import com.nevoit.glasense.theme.tokens.Gray200
import com.nevoit.glasense.theme.tokens.Gray800
import java.time.LocalDate

/**
 * 小组件数据模型 + 配色派生 — 生产 RemoteViews 渲染链路
 * (WidgetBitmapRenderers / WeekGridWidgetProvider / WidgetRenderActivity) 共用。
 *
 * Glance composable 层(WidgetContent/WeekListContent/TwoDayContent/WeekGridContent)
 * 已删除(决策 D5-11): 5 个生产入口全走 RemoteViews + Canvas bitmap,
 * Glance 层生产不可达, 其旧关键词配色路径(CourseColorRules)一并移除。
 * 文件名保留 WidgetContent.kt 以减小 diff; 如需可后续重命名为 WidgetModels.kt。
 */

/**
 * 小组件渲染数据 — 让渲染端单纯绘制，不读 DB。
 * Receiver.loadDataSync 在后台线程拉数据，组装成这个 model 喂给 renderer。
 */
data class WidgetData(
    /** 今日日期 */
    val date: LocalDate,
    /** 今日课程（已按当前周次过滤 + 排序） */
    val courses: List<CourseEntity>,
    /** timeJson（用于查开始/结束时间） */
    val timeJson: String,
    /** 是否有课表 */
    val hasTable: Boolean,
    /** 跟 app 主题保持一致：true=深色小组件 */
    val isDark: Boolean = false,
    /** 跟 app 主题色（ThemePresets key） */
    val themeKey: String = ThemePresets.DEFAULT_KEY,
    /** 学期状态（v1.0.37）: 学期外时 Today 渲染状态文案不渲染课程 */
    val semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE,
    /**
     * date 是否为真·今天 (issue #24 Feature2 日期导航)。
     * 今日态 → 标题「今天 · 周X」; 导航态 → 标题「M/D · 周X」+ 右侧「回到今天」。
     * 默认 true: 既有调用方 (WidgetRenderActivity 预览 / weekGridMinimumTodayData)
     * 构造的都是今日数据, 不加字段零改动。
     */
    val isToday: Boolean = true,
    /** 当前展示周状态：下一周/周末返回实际周/本周已结束。 */
    val weekDisplayStatus: WeekDisplayStatus = WeekDisplayStatus.NORMAL
) {
    val dayName: String get() = DateUtils.localizedDay(date.dayOfWeek.value, com.imsx3d.classy.SleepyApp.get())
    val dateLabel: String get() = "${date.monthValue}/${date.dayOfMonth}"
}

/**
 * 4 元组：背景 / 主题强调色 / 正文色 / 次要色
 * 跟 app M3 scheme 派生方式相同：surface / primary / onSurface / onSurfaceVariant
 *
 * 死代码清理: 原 coursePrimary…coursePractice 9 个课程色字段赋值后从未被渲染使用
 * (课程底色实际走 CourseColorUtil 黄金角 HSL), 已随 CoursePalette 死属性一并删除。
 */
/**
 * [intentional custom] 小组件 RemoteViews 渲染层拿不到 Compose Color/Context 走不到
 * MaterialTheme.colorScheme, 故维护一份「与 app M3 scheme 同源派生」的扁平值类型。
 * 全部由 [resolveSchemePublic] 构造, 不允许默认值 — 历史默认值
 * (#FDFCFF/#FFFBFE/#6750A4 等) 已被 ThemePresets.LightScheme 取代
 * (#FEF7FF/同/#6750A4), 默认值即漂移源, 故全部删默认, 强制派生。
 */
data class WidgetScheme(
    val bg: Color,
    val surface: Color,
    val primary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val surfaceContainer: Color,
    val surfaceVariant: Color,
    val isDark: Boolean
)

/**
 * 按 themeKey + isDark 派生小组件配色。
 *
 * UI-4（2026-09-26）：**中性色改从 Glasense 令牌取**，与 App 完全同源 ——
 *   App 端也是「Glasense 中性板 + 用户所选主题色作强调色」（见 ui/theme/Theme.kt），
 *   此前组件中性色走 M3 预设色板（ThemePresets），底色/层次/描边与 App 对不上，
 *   组件看着"不像同一个软件"（用户反馈：扁平、割裂）。
 *   · 组件自身底 → cardBackground（浮在桌面壁纸上的"卡片"）
 *   · 组件内面板 → 浅色用 pageBackground（App 里的浅灰面板）/ 深色用 elevatedCardBackground
 *   · 网格线、无色课底 → Gray200 / Gray800（与 glasenseM3Scheme 的 surfaceVariant 同源）
 *   · 文字 → content / contentVariant
 * 强调色仍按主题解析（与 App 的 primary 同源）：
 *   themeKey == "system" → Material You 动态取色（API 31+，低版本降级 Default）；
 *   "custom:" 前缀 → 用户自定义主题（与 App 同一派生函数 CustomSchemeDeriver）；
 *   否则 → ThemePresets 预设。id 读不到（已删）→ 回落 Default，与 App unknown-key 语义一致。
 */
internal fun resolveSchemePublic(context: Context, themeKey: String, isDark: Boolean): WidgetScheme {
    val g = if (isDark) GlasenseDarkPalette else GlasenseLightPalette
    val primary = resolvePrimaryColor(context, themeKey, isDark)
    return WidgetScheme(
        bg = g.cardBackground,
        surface = g.cardBackground,
        primary = primary,
        primaryContainer = primary.copy(alpha = 0.16f),
        onPrimaryContainer = primary,
        onSurface = g.content,
        onSurfaceVariant = g.contentVariant,
        surfaceContainer = if (isDark) g.elevatedCardBackground else g.pageBackground,
        surfaceVariant = if (isDark) Gray800 else Gray200,
        isDark = isDark
    )
}

/** 小组件强调色 = App 的主题色（同一条解析链：动态取色 / 自定义 / 预设）。 */
private fun resolvePrimaryColor(context: Context, themeKey: String, isDark: Boolean): Color {
    // "跟随系统" 主题 → Material You 动态取色 (API 31+), 低版本降级 Default
    if (themeKey == ThemePresets.KEY_SYSTEM &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    ) {
        val dyn = if (isDark) androidx.compose.material3.dynamicDarkColorScheme(context)
                  else androidx.compose.material3.dynamicLightColorScheme(context)
        return dyn.primary
    }
    if (themeKey.startsWith(ThemePresets.CUSTOM_KEY_PREFIX)) {
        // 用户自定义主题 → 同源派生(App 端 SleepyThemeProvider 同一函数)
        val custom = com.imsx3d.classy.data.CustomThemeStore.getById(
            context, themeKey.removePrefix(ThemePresets.CUSTOM_KEY_PREFIX)
        )
        if (custom != null) return com.imsx3d.classy.ui.theme.CustomSchemeDeriver.derive(custom, isDark).primary
        // 已删/损坏 → 回落默认主题,与 App 端 unknown-key 语义一致
        return ThemePresets.byKey(ThemePresets.DEFAULT_KEY).let { if (isDark) it.dark else it.light }.primary
    }
    val preset = ThemePresets.byKey(themeKey)
    return (if (isDark) preset.dark else preset.light).primary
}

// ═══════════════════════════════════════════════════════
// Multi-day widget data
// ═══════════════════════════════════════════════════════

/** 单天数据 */
data class DayData(
    val date: LocalDate,
    val dayOfWeek: Int,
    val courses: List<CourseEntity>,
    val timeJson: String
) {
    val dayLabel: String get() = DateUtils.shortDate(date)
    val dayName: String get() = DateUtils.localizedDay(dayOfWeek, com.imsx3d.classy.SleepyApp.get())
    val isToday: Boolean get() = date == LocalDate.now()
    val isTomorrow: Boolean get() = date == LocalDate.now().plusDays(1)
}

/** 周视图数据 */
data class WeekData(
    val days: List<DayData>,
    val hasTable: Boolean,
    val isDark: Boolean = false,
    val themeKey: String = ThemePresets.DEFAULT_KEY,
    // displayMode 死字段已删（renderer 各自直读 AppPrefs.getDisplayMode, 传入字段从未被消费）
    val showDate: Boolean = false,
    val visibleDays: Set<Int> = (1..7).toSet(),
    /** 学期状态（v1.0.37）: 学期外时列头加状态行 */
    val semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE,
    /**
     * 最小档三天窗口 (2026-09-15 用户令): 真实日期日列, 可跨上下周
     * (周一「今日居第二位」= 上周日/周一/周二, 各按所在周周次过滤课程)。
     * 空 = 数据源未提供, compact 渲染回退旧 weekViewCompactColumns 口径。
     */
    val compactWindow: List<DayData> = emptyList(),
    /** 当前展示周状态。 */
    val weekDisplayStatus: WeekDisplayStatus = WeekDisplayStatus.NORMAL
)

/** 两天视图数据 */
data class TwoDayData(
    val days: List<DayData>,
    val hasTable: Boolean,
    val isDark: Boolean = false,
    val themeKey: String = ThemePresets.DEFAULT_KEY,
    /** 学期状态（v1.0.37）: 学期外时渲染状态文案不渲染课程 */
    val semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE,
    /** 当前展示周状态。 */
    val weekDisplayStatus: WeekDisplayStatus = WeekDisplayStatus.NORMAL
)
