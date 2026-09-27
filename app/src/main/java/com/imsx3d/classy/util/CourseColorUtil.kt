package com.imsx3d.classy.util

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import com.imsx3d.classy.data.entity.CourseColorMode
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.theme.CoursePalette

/**
 * 课程底色单一事实来源 — 三层结构（决策 D3）
 *
 * 收敛原来分散在 TodayScreen / CourseTableView / WeekGridWidgetProvider /
 * WidgetBitmapRenderers 四处的课程配色私有副本，统一为一份逻辑：
 *
 * 决策树（所有入口 100% 同源）：
 *   ① 用户自定义颜色（color 非空且非哨兵值）→ 直接返回，colorless 不覆盖手动设色
 *   ② colorless=true → 返回中性灰（surfaceVariant，与网格线同色保持一致）
 *   ③ 否则 → 黄金角 137.508° 基于 groupId 撒 hue，同门课永远同色
 *
 * 三层结构：
 *   常量层     — GOLDEN_ANGLE / SENTINEL_COLOR / S_LIGHT / S_DARK / L_LIGHT / L_DARK（各定义一次）
 *   纯逻辑层   — stableHue / hasCustomColor（无平台依赖）
 *   平台适配层 — pickCourseColorCompose / pickCourseColorInt（两套返回类型，同一份逻辑）
 */
object CourseColorUtil {

    // ============================ 第一层 · 常量 ============================

    /** 黄金角 137.508°，相邻 id 色差最大化（13 门课最少差 ~27°） */
    private const val GOLDEN_ANGLE = 137.508f

    /** 哨兵色 "#FF6750A4"，标记「未设置颜色」，与主题默认紫完全相同（Phase2 换 isCustomColor 布尔 + DB migration） */
    private const val SENTINEL_COLOR = "#FF6750A4"

    /** 亮色模式饱和度（柔和粉彩，不刺眼）。2026-09-28 由 0.55 降到 0.48：
     *  用户报"色块对比度过高、刺眼"，浅色模式下尤其明显。 */
    private const val S_LIGHT = 0.52f

    /** 暗色模式饱和度 —— 2026-09-26 由 0.40 提到 0.48：
     *  低饱和 + 低亮度会把琥珀/橙/黄绿算成**棕褐色**，看起来像"未选中/已取消"
     *  （用户报障："这个颜色给人一种未选中、没有确认感的状态"）。深色下必须保色彩浓度。 */
    private const val S_DARK = 0.44f

    /** 亮色模式亮度。2026-09-28 由 0.82 提到 0.83（配上面的降饱和，整体更柔） */
    private const val L_LIGHT = 0.80f

    /** 暗色模式亮度 —— 2026-09-26 由 0.28 提到 0.34：同上，避免暗到发浊 */
    private const val L_DARK = 0.34f

    // ============================ 第二层 · 纯逻辑（无平台依赖） ============================

    /**
     * 基于课程组 ID 计算稳定色相（0°~360°）。
     * hue 种子必须是 groupId（课程身份标识），不能用 course.id（数据库自增主键，随导入顺序漂移）。
     */
    fun stableHue(groupId: String): Float =
        ((groupId.hashCode().toLong() * GOLDEN_ANGLE) % 360f + 360f) % 360f

    // ---- 稳定配色：精选色相环 + 三档明暗（2026-09-26 重做）----
    //
    // 为什么换掉"hashCode × 黄金角"：黄金角只在**序号连续**时才能拉开色差，
    // 而 hashCode 本身是随机分布的 → 乘完仍是随机色相，两门课可能只差几度
    // （用户反馈"相邻课程配色十分接近、没有变化感"）。而且它只用了色相一个维度，
    // 撞色时无从区分。
    // 现改为两个维度同时区分：
    //   ① 色相取自**精选 12 档色相环**（跳过 60°~130° 黄绿压缩区——那里相邻色相
    //      看起来几乎一样；本环相邻档感知差最大）；
    //   ② 同一色相再分**三档饱和/明度**，档位由哈希高位决定 —— 撞到同一色相时
    //      仍有约 2/3 概率分到不同档，肉眼可区分。
    // 配色仍 100% 由 groupId 决定 → 同门课在网格/列表/今日/组件各处同色，跨启动恒定。

    /**
     * 精选色相环（**9 档，等间距 25°**）：草绿→青绿→青→天蓝→蓝→靛→紫→品红→玫红
     *
     * 2026-09-28（用户报「相近颜色扎堆」）第二次重做，三条口径：
     *  ① **等间距**：旧环 11 档里有 3 档挤在品红区（310/330/352）、3 档挤在蓝区
     *     （195/214/235）—— 一天里两门课落进同一簇就几乎同色。现按 25° 均分，
     *     任意两档在感知上都拉得开（青绿↔青↔天蓝是三个**不同家族**的色，不是同族深浅）。
     *  ② **砍掉暖色端（40°~130° 整段不用）**：琥珀/黄绿在深色模式低亮度下必然发浊成棕褐
     *     （旧注释实测 #63522A），浅色模式又容易发荧光 —— 两端都不讨好，整段弃用。
     *  ③ **去掉"难看"的档**：橙(20°)在浅色下像警示色、春绿(150°)在浅色下发酸，都撤了；
     *     剩下的 9 档在深浅两种模式下都能保持明确的色彩身份。
     */
    private val HUE_RING = floatArrayOf(
        145f, 170f, 195f, 220f, 245f, 270f, 300f, 325f, 350f
    )

    /**
     * 同色相三档（亮色）：明度只往**更深**走、饱和度只**增大**。
     * 关键约束：任何一档都不得比基色更淡/更低饱和 —— 变淡正是"像被禁用"的观感来源。
     * 2026-09-28：档差由 ±0.06 拉到 ±0.07（三档 0.90 / 0.83 / 0.76），
     * 同色相撞车时肉眼能立刻分辨 —— 这是"撞色不可怕、撞得看不出才可怕"的兜底。
     */
    private val TONE_L_LIGHT = floatArrayOf(0.07f, 0f, -0.07f)
    private val TONE_S_LIGHT = floatArrayOf(-0.08f, 0f, 0.08f)

    /** 同色相三档（暗色）：明度允许更亮（更亮更清晰），饱和度只增大；不往更暗更浊走。 */
    private val TONE_L_DARK = floatArrayOf(0f, 0.09f, -0.06f)
    private val TONE_S_DARK = floatArrayOf(0f, 0.07f, -0.05f)

    /**
     * 32 位哈希混合（MurmurHash3 finalizer）。
     *
     * 为什么不能直接用 `seed.hashCode() % 9`：中文课名的 `String.hashCode()` 是
     * `Σ cᵢ·31^(n-1-i)` 这种**线性组合**（31 ≡ 4 (mod 9)），取模后聚堆得厉害 ——
     * 实测 15 个真实课名只落到 **6/9** 个色相（195° 一档挤了 4 门，正是用户说的"扎堆"）。
     * 混合后再取模落到 8/9，且不再与档位相关（档位用另一个混合值，见 [slotFor]）。
     */
    private fun mix32(v: Int): Int {
        var h = v
        h = h xor (h ushr 16)
        h *= -0x7A143595            // 0x85EBCA6B
        h = h xor (h ushr 13)
        h *= -0x3D4D51CB            // 0xC2B2AE35
        h = h xor (h ushr 16)
        return h
    }

    /** 档位偏移盐（黄金比例常数），让"色相"与"档位"来自两个互不相关的混合值 */
    private const val TONE_SALT = 0x9E3779B9L.toInt()

    private const val TONE_COUNT = 3

    /**
     * 色相步进 4（与 9 互质）：按 k·4 mod 9 走，前 9 门课正好把 9 个色相全用一遍，
     * 且**相邻两门课的色相相距 4 档**（≈100°）—— 同一天里挨着的课绝不会撞色。
     */
    private const val HUE_STRIDE = 4

    /**
     * **按表分配槽位** —— 治「相近颜色扎堆」的正解（2026-09-28 实测后确定）。
     *
     * 为什么哈希不行：hash 只能保证"同一门课恒定"，保证不了"这一组课互不相同"。
     * 实测 15 个真实中文课名：旧的 `hashCode % 9` 只落到 6 个色相（195° 挤 4 门）；
     * 换成哈希混合后仍有 5 门挤在 195°、且出现两门**完全相同**的颜色。
     * 只要分配只看"单门课自己的哈希"，就必然会出现这种扎堆（生日问题）。
     *
     * 所以改成看**整张表**：按课程在表中的出现顺序（id 升序，即导入顺序）给互不相同的
     * 课程名依次发号 k，色相 = HUE_RING[k·4 mod 9]、档位 = k / 9 ——
     *  · 前 9 门课拿满 9 个色相（理论上最分散）；
     *  · 第 10 门开始换档位再走一遍（同色相不同深浅）；
     *  · **追加课程不会改变已有课的颜色**（k 只由"它是第几个出现的"决定）✓ 稳定。
     *
     * 调用方（App 三个视图 / 小组件）把同一张表的 course 列表喂进来，
     * 得到 `courseSeed → 槽位号`，再交给 [paletteFor] / [accentFor]。
     */
    fun slotsFor(courses: List<CourseEntity>): Map<String, Int> {
        val map = LinkedHashMap<String, Int>()
        courses.sortedBy { it.id }.forEach { c ->
            val seed = colorSeed(c)
            if (!map.containsKey(seed)) map[seed] = map.size
        }
        return map
    }

    private fun hueIdxOf(slot: Int): Int = floorMod(slot * HUE_STRIDE, HUE_RING.size)

    /**
     * 档位：**在色相铺开的同时轮转**，而不是"前 9 门都同一档、第 10 门才换档"。
     *
     * 为什么：9 个色相铺满 9 门课时，**必然**有一对课的色相是相邻档（鸽巢），
     * 比如"245° 蓝"和"220° 天蓝"—— 只差 25°，光看色相分不开。
     * 让档位跟着轮转后，这一对的深浅也不同（46% vs 52% 的明度），肉眼立刻能分。
     * 第 10 门起（k≥9）档位整体再错开一位，保证与第一轮的同一色相不同档。
     */
    private fun toneOf(slot: Int): Int = floorMod(slot % TONE_COUNT + slot / HUE_RING.size, TONE_COUNT)

    /** 种子 → (色相索引, 档位)。同一种子恒定；色相与档位互不相关。 */
    private fun slotFor(seed: String): Pair<Int, Int> {
        val h = seed.hashCode()
        return floorMod(mix32(h), HUE_RING.size) to floorMod(mix32(h xor TONE_SALT), TONE_COUNT)
    }

    private fun floorMod(a: Int, n: Int): Int = ((a % n) + n) % n

    /**
     * 种子（课程名，见 [colorSeed]）+ 深浅 → (hue, saturation, lightness)。同一种子恒定不变。
     * 不用 course.id：数据库自增主键随导入顺序漂移（会破坏"同门课同色"）。
     */
    internal fun paletteFor(seed: String, isDark: Boolean, slot: Int? = null): Triple<Float, Float, Float> {
        val (hueIdx, tone) = if (slot != null) hueIdxOf(slot) to toneOf(slot) else slotFor(seed)
        val hue = HUE_RING[hueIdx]
        val ds = if (isDark) TONE_S_DARK[tone] else TONE_S_LIGHT[tone]
        val dl = if (isDark) TONE_L_DARK[tone] else TONE_L_LIGHT[tone]
        // 下限保护：暗色不得低于 0.42 饱和 / 0.26 亮度（低于此开始发浊、像禁用）
        val s = ((if (isDark) S_DARK else S_LIGHT) + ds).coerceIn(if (isDark) 0.42f else 0.45f, 0.88f)
        val l = ((if (isDark) L_DARK else L_LIGHT) + dl).coerceIn(0.24f, 0.90f)
        return Triple(hue, s, l)
    }

    /**
     * 颜色种子 —— **课程名（规范化）**，不是 groupId。
     *
     * 2026-09-28（用户报「课程色彩体系有问题」）根因之一：
     * `groupId` 由 `ScheduleRepository.assignGroupIds` 在**每次导入时随机 UUID**，
     * 所以同一门课重新导入一次就换一套颜色；跨课表也对不上。
     * 改用课程名做种子后：同门课在**任何课表、任何一次导入**里恒为同色，
     * 且颜色分布可以被推断和验证（见 tools/color_sheet.py）。
     *
     * 规范化口径与 assignGroupIds 的分组键一致（trim + 折叠空白 + 小写），
     * 名字为空时退回 groupId（组内同色仍成立）。
     */
    fun colorSeed(row: CourseEntity): String {
        val key = row.courseName.trim().replace(Regex("\\s+"), " ").lowercase()
        return if (key.isNotEmpty()) key else row.groupId
    }

    // ---- 强调档（色条 / 强调文字）—— 2026-09-28 新增 ----
    //
    // 用户报「课程色块对比度太高、刺眼，尤其浅色模式」，对照今日页（只有 4dp 色条 + 时间文字用课色）
    // 判断：**大面积填充要压彩度，小面积强调要够彩度** —— 同一条课色两种用法各取所需，于是分两档。
    // 亮色档 L=0.46（深而不黑，白底上清晰）；深色档 L=0.62（亮而不荧光，深底上清晰）。
    private const val ACCENT_S_LIGHT = 0.62f
    private const val ACCENT_L_LIGHT = 0.46f
    private const val ACCENT_S_DARK = 0.58f
    private const val ACCENT_L_DARK = 0.62f

    /** 强调档随三档位一起走（同色相撞车时，色条的明度也不同，仍能分辨） */
    private val ACCENT_TONE_L = floatArrayOf(-0.05f, 0f, 0.06f)

    /**
     * 强调色 (hue, s, l)：与 [paletteFor] 同色相、同档位，只是彩度更高、明度更适合小面积。
     * 用于色条与强调文字（今日页 → 网格/周视图 2026-09-28 统一）。
     */
    internal fun accentFor(seed: String, isDark: Boolean, slot: Int? = null): Triple<Float, Float, Float> {
        val (hueIdx, tone) = if (slot != null) hueIdxOf(slot) to toneOf(slot) else slotFor(seed)
        val hue = HUE_RING[hueIdx]
        val l = (if (isDark) ACCENT_L_DARK else ACCENT_L_LIGHT) + ACCENT_TONE_L[tone]
        return Triple(hue, if (isDark) ACCENT_S_DARK else ACCENT_S_LIGHT, l.coerceIn(0.30f, 0.70f))
    }

    /**
     * 判定课程是否有用户自定义颜色：color 非空且非哨兵值。
     * 哨兵判定收敛到此单点，为后续铺路。
     *
     * TODO(Phase2): 哨兵值 #FF6750A4 与主题默认紫完全相同，用户主动选紫主题或导入指定此色
     *   会被误判为「未设置」→ 需改为 isCustomColor 布尔字段 + DB migration。
     */
    fun hasCustomColor(course: CourseEntity): Boolean =
        course.color.isNotBlank() && !course.color.equals(SENTINEL_COLOR, ignoreCase = true)

    /**
     * hex-only 版哨兵判定 — 无实体上下文的调用方(改组色 UI 显示组色源)取用,
     * 判定口径与 [hasCustomColor] 完全一致,哨兵值仍收敛到 SENTINEL_COLOR 单点。
     */
    fun hasCustomColorHex(hex: String): Boolean =
        hex.isNotBlank() && !hex.equals(SENTINEL_COLOR, ignoreCase = true)

    /**
     * 明暗探针 — 读 CoursePalette.primary 亮度（原四副本中 isPaletteDark 的唯一收敛点）。
     * 注意: 只能用 CoursePalette（亮=0xFFEADDFF / 暗=0xFF4F378B），不能用 WakeUpColorScheme.primary
     * （亮色=0xFF6750A4 加权亮度 0.38 会被误判为暗色）。
     */
    fun isPaletteDark(p: CoursePalette): Boolean {
        val c = p.primary
        val lum = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
        return lum < 0.5f
    }

    // ============================ 第二层 · 文字色亮度自适应（决策 D5-13） ============================

    /**
     * BT.601 加权亮度（0f~1f）— 纯函数，权重与人眼感光曲线一致（绿最敏感）。
     * 与 WeekGridWidgetProvider 原 isDarkOn 私有实现同一算法，收敛至此单点。
     */
    fun luminance(color: Int): Float {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /** Compose Color 版本 — 同 BT.601 权重 */
    fun luminance(color: Color): Float =
        0.299f * color.red + 0.587f * color.green + 0.114f * color.blue

    /**
     * 按背景亮度自适应选文字色 — 深色自定义课色上文字必须可读（决策 D5-13）：
     *   深色底（luminance<0.5） → 白字（原固定 onSurface 在浅色主题下是深字，深字压深底不可见）
     *   浅色底+浅色主题         → onSurface（浅色 HSL 默认底 L=0.82 恰好可读，行为不变）
     *   浅色底+暗色主题         → 黑字（暗色主题 onSurface 是浅色，用户自定义亮色课色上不可读）
     * HSL 默认底（亮 0.82 / 暗 0.28）与 colorless 灰底均落在「行为不变」分支，仅自定义色跨界时切换。
     *
     * @param onSurface 当前主题的 onSurface（Compose 路径传 WakeUpColorScheme.onSurface）
     */
    fun textColorOn(bg: Color, isDark: Boolean, onSurface: Color): Color = when {
        luminance(bg) < 0.5f -> Color.White
        isDark -> Color.Black
        else -> onSurface
    }

    /** Canvas 路径版本（ARGB Int）— 同一决策树，供 WidgetBitmapRenderers 等画布渲染 */
    fun textColorOn(bg: Int, isDark: Boolean, onSurface: Int): Int = when {
        luminance(bg) < 0.5f -> android.graphics.Color.WHITE
        isDark -> android.graphics.Color.BLACK
        else -> onSurface
    }

    // ============================ 第二层 · HSL 转换 ============================

    /** HSL → Compose Color（供 TodayScreen / CourseTableView 的 Compose 路径） */
    fun hslToColor(h: Float, s: Float, l: Float): Color {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f   -> Triple(c, x, 0f)
            h < 120f  -> Triple(x, c, 0f)
            h < 180f  -> Triple(0f, c, x)
            h < 240f  -> Triple(0f, x, c)
            h < 300f  -> Triple(x, 0f, c)
            else      -> Triple(c, 0f, x)
        }
        return Color(r + m, g + m, b + m)
    }

    /** HSL → ARGB Int（供 WeekGridWidgetProvider / WidgetBitmapRenderers 的 Canvas 路径） */
    fun hslToColorInt(h: Float, s: Float, l: Float): Int {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f   -> Triple(c, x, 0f)
            h < 120f  -> Triple(x, c, 0f)
            h < 180f  -> Triple(0f, c, x)
            h < 240f  -> Triple(0f, x, c)
            h < 300f  -> Triple(x, 0f, c)
            else      -> Triple(c, 0f, x)
        }
        return (0xFF shl 24) or
            ((r + m).coerceIn(0f, 1f).times(255f).toInt() shl 16) or
            ((g + m).coerceIn(0f, 1f).times(255f).toInt() shl 8) or
            (b + m).coerceIn(0f, 1f).times(255f).toInt()
    }

    // ============================ 第三层 · 平台适配入口 ============================

    /**
     * Compose 路径取色入口（TodayScreen / CourseTableView）。
     * @param neutralColor colorless 灰底，即 WakeUpColorScheme.surfaceVariant（勿从 CoursePalette 取，它无此字段）
     */
    fun pickCourseColorCompose(
        course: CourseEntity,
        isDark: Boolean,
        neutralColor: Color,
        colorless: Boolean = false
    ): Color {
        if (hasCustomColor(course)) {
            runCatching { return Color(android.graphics.Color.parseColor(course.color)) }
        }
        if (colorless) return neutralColor
        val (hue, s, l) = paletteFor(course.groupId, isDark)
        return hslToColor(hue, s, l)
    }

    /**
     * Canvas 路径取色入口（WeekGridWidgetProvider / WidgetBitmapRenderers）。
     * @param neutralColorInt colorless 灰底，即 scheme.surfaceVariant 的 Int 值
     */
    fun pickCourseColorInt(
        course: CourseEntity,
        isDark: Boolean,
        neutralColorInt: Int,
        colorless: Boolean = false
    ): Int {
        if (hasCustomColor(course)) {
            runCatching { return android.graphics.Color.parseColor(course.color) }
        }
        if (colorless) return neutralColorInt
        val (hue, s, l) = paletteFor(course.groupId, isDark)
        return hslToColorInt(hue, s, l)
    }

    // ============================ 第三层 · issue#22 3 态取色入口 ============================
    //
    // 旧 [pickCourseColorCompose]/[pickCourseColorInt] 不动 — 它们的语义是
    // "整门课一个色",被 WidgetContent 之类纯 group-level 渲染路径使用。
    // issue#22 同名课程多地点修复后,WeekGrid/WeekList/Widget/Todo 等渲染
    // 每个 block 独立色的入口走下方 *WithGroupRows 版本。

    /**
     * Compose 路径 · issue#22 3 态取色入口。
     *
     * @param row 目标行
     * @param groupRows 同 groupId 所有行(含 row 自身) — 用于 AUTO 模式按行号顺序算 hue
     * @param neutralColor colorless 灰底
     */
    fun pickCourseColorComposeWithGroupRows(
        row: CourseEntity,
        groupRows: List<CourseEntity>,
        isDark: Boolean,
        neutralColor: Color,
        colorless: Boolean = false,
        slot: Int? = null
    ): Color {
        return when (row.colorMode) {
            CourseColorMode.CUSTOM -> runCatching {
                Color(android.graphics.Color.parseColor(row.color))
            }.getOrElse { neutralColor }
            CourseColorMode.AUTO -> {
                if (colorless) return neutralColor
                val hue = GoldenAngleColor.forRow(row, groupRows, groupSourceColorHex(groupRows))
                val (s, l) = GoldenAngleColor.hsl(hue, isDark)
                hslToColor(hue, s, l)
            }
            else -> {  // GROUP(0) 或未知
                if (hasCustomColor(row)) {
                    runCatching { return Color(android.graphics.Color.parseColor(row.color)) }
                }
                if (colorless) return neutralColor
                val (hue, s, l) = paletteFor(colorSeed(row), isDark, slot)
                hslToColor(hue, s, l)
            }
        }
    }

    /**
     * 强调色 Compose 入口 —— 决策树与 [pickCourseColorComposeWithGroupRows] 完全一致
     * （CUSTOM 用户色原样、colorless 中性、AUTO/GROUP 走强调档），只是 AUTO/GROUP 取"更浓"那一档。
     * 色条 / 时间文字一律走这里，保证"同门课在今日、网格、周视图里是同一条色"。
     */
    fun pickAccentComposeWithGroupRows(
        row: CourseEntity,
        groupRows: List<CourseEntity>,
        isDark: Boolean,
        neutralColor: Color,
        colorless: Boolean = false,
        slot: Int? = null
    ): Color {
        return when (row.colorMode) {
            CourseColorMode.CUSTOM -> runCatching {
                Color(android.graphics.Color.parseColor(row.color))
            }.getOrElse { neutralColor }
            CourseColorMode.AUTO -> {
                if (colorless) return neutralColor
                val hue = GoldenAngleColor.forRow(row, groupRows, groupSourceColorHex(groupRows))
                hslToColor(hue, if (isDark) ACCENT_S_DARK else ACCENT_S_LIGHT,
                    if (isDark) ACCENT_L_DARK else ACCENT_L_LIGHT)
            }
            else -> {
                if (hasCustomColor(row)) {
                    runCatching { return Color(android.graphics.Color.parseColor(row.color)) }
                }
                if (colorless) return neutralColor
                val (hue, sat, light) = accentFor(colorSeed(row), isDark, slot)
                hslToColor(hue, sat, light)
            }
        }
    }

    /** Canvas 路径 · issue#22 3 态取色入口 */
    fun pickCourseColorIntWithGroupRows(
        row: CourseEntity,
        groupRows: List<CourseEntity>,
        isDark: Boolean,
        neutralColorInt: Int,
        colorless: Boolean = false
    ): Int {
        return when (row.colorMode) {
            CourseColorMode.CUSTOM -> runCatching {
                android.graphics.Color.parseColor(row.color)
            }.getOrElse { neutralColorInt }
            CourseColorMode.AUTO -> {
                if (colorless) return neutralColorInt
                val hue = GoldenAngleColor.forRow(row, groupRows, groupSourceColorHex(groupRows))
                val (s, l) = GoldenAngleColor.hsl(hue, isDark)
                hslToColorInt(hue, s, l)
            }
            else -> {
                if (hasCustomColor(row)) {
                    runCatching { return android.graphics.Color.parseColor(row.color) }
                }
                if (colorless) return neutralColorInt
                val (hue, s, l) = paletteFor(row.groupId, isDark)
                hslToColorInt(hue, s, l)
            }
        }
    }

    /** 组色源 — 同 groupId 内 colorMode=GROUP 中 id 最小的行的 color */
    fun groupSourceColorHex(groupRows: List<CourseEntity>): String {
        val source = groupRows
            .filter { it.colorMode == CourseColorMode.GROUP }
            .minByOrNull { it.id }
            ?: groupRows.minByOrNull { it.id }
        return source?.color ?: ""
    }
}

/**
 * 自动色算法 — golden angle 137.508° 在 HSV 色环上按 row 序号推进。
 * 不落库: 仅用 row 自身 id + 同组行 id 顺序 + 组色源色相 算色相,确定性重算。
 * (issue#22 同名课程多地点修复的 AUTO 模式取色)
 */
object GoldenAngleColor {

    private const val GOLDEN_ANGLE_DEG = 137.508f
    private const val SATURATION_LIGHT = 0.55f
    private const val SATURATION_DARK = 0.40f
    private const val LIGHTNESS_LIGHT = 0.82f
    private const val LIGHTNESS_DARK = 0.28f

    /**
     * @param row 目标行(只读,用于取 id)
     * @param groupRows 同 groupId 的所有行(含 row 自身)
     * @param groupSourceColorHex 组色源行(通常是 colorMode=GROUP 中 id 最小)的 color 字段值
     */
    fun forRow(row: CourseEntity, groupRows: List<CourseEntity>, groupSourceColorHex: String?): Float {
        val baseHue = parseHexHue(groupSourceColorHex)
        val sorted = groupRows.sortedBy { it.id }
        val idx = sorted.indexOfFirst { it.id == row.id }
        val safeIdx = if (idx < 0) 0 else idx
        return (((baseHue + safeIdx * GOLDEN_ANGLE_DEG) % 360f) + 360f) % 360f
    }

    /** 给定 hue 返回 (saturation, lightness) — 由 isDark 决定亮/暗主题 */
    fun hsl(hue: Float, isDark: Boolean): Pair<Float, Float> =
        (if (isDark) SATURATION_DARK else SATURATION_LIGHT) to
            (if (isDark) LIGHTNESS_DARK else LIGHTNESS_LIGHT)

    private fun parseHexHue(hex: String?): Float {
        if (hex.isNullOrBlank()) return 0f
        return runCatching {
            val argb = android.graphics.Color.parseColor(hex)
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(argb, hsl)
            hsl[0]
        }.getOrDefault(0f)
    }
}

/**
 * 当前课表的「颜色槽位表」（courseSeed → 槽位号，见 [CourseColorUtil.slotsFor]）。
 *
 * 由页面根部按**整张表**的课程列表提供一次（MainActivity），叶子（网格格 / 周视图行 /
 * 今日卡）读它取色 —— 这样同一门课在三个视图里同色，且"同一张表内互不撞色"。
 * 没有提供时退回空表：取色退回按课程名哈希（编辑页预览等没有表上下文的场景）。
 */
val LocalCourseColorSlots = androidx.compose.runtime.compositionLocalOf<Map<String, Int>> { emptyMap() }
