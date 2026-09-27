package com.imsx3d.classy.util

/**
 * 「今天」表头高亮的取值规则 —— **App 网格表头与网格组件共用同一套数值**。
 *
 * 为什么单独抽一个文件：这组值与深浅色强相关，而两端一个是 Compose（`Color`）、
 * 一个是 RemoteViews（`Int` ARGB），实现形态不同但**视觉必须一致** ——
 * 历史上已经因为不一致被用户抓到过（"组件有今日特效、App 里没有"）。
 * 把数值放一处，改一处两端一起变。
 *
 * 规则（2026-09-26 用户报障"深色下胶囊刺眼、字与底糊在一起"后定稿）：
 *   · 浅色：实心主色底 + `onPrimary` 字 —— 用户定稿"很有确认感、不刺眼"，不动。
 *   · 深色：主色在深色主题里是**浅色调**（Glasense dark primary ≈ 亮青 #85D2E7），
 *     整格铺实心 = 暗色页面上一块高亮 → 刺眼；而 `onPrimary` 在深色里是深青，
 *     压在亮青上对比不足 → 字与底糊在一起。
 *     改用 M3 深色**容器**语义：主色 @[DARK_PILL_ALPHA] 的暗底 + **主色本身当字色**
 *     （亮青字压暗青底）—— 仍是"整格实心块"的确认感，但不刺眼、字足够清楚。
 *   · 胶囊内的次级小块（休/补 角标、日期方章）在深色里用主色再叠一层
 *     （[DARK_INNER_ALPHA]），叠加后比底板更亮，保持"亮块压在底板上"的层次关系；
 *     浅色里则是 `onPrimary` 半透明（亮底上压更亮的小块）。
 */
object TodayHighlight {

    /** 深色：容器底 = 主色 @26%（叠在深色卡面上 ≈ 暗青块：看得清"选中"，又不刺眼）。
     *  取 0.26 而非更高：26% 时亮青字与暗青底的对比度 ≈ 4.5:1（WCAG AA 正文线），
     *  再往上调（如 0.30 → 4.0:1）字就开始不够清楚。 */
    const val DARK_PILL_ALPHA = 0.26f

    /** 深色：次级小块的叠加层（见 SelectedSurfaceColors.inner 的说明）。
     *  深色下**不再叠**（叠加后对比度只剩 3:1，小字就糊了）—— 次级元素直接浮在容器上，
     *  所以这里保留一个"不叠"的语义值给需要的地方引用。 */
    const val DARK_INNER_ALPHA = 0f

    /** 浅色：胶囊内次级小块 = onPrimary @22%（实心主色底上压一块更亮的）。 */
    const val LIGHT_INNER_ALPHA = 0.22f

    /** 深色用：把主色变成"半透明容器底"的 ARGB（RemoteViews 端只能传 Int）。 */
    fun darkContainerArgb(primary: Int, alpha: Float = DARK_PILL_ALPHA): Int =
        (primary and 0x00FFFFFF) or ((alpha * 255f).toInt().coerceIn(0, 255) shl 24)

    /** 深色用：胶囊内的次级小块 ARGB。 */
    fun darkInnerArgb(primary: Int): Int = darkContainerArgb(primary, DARK_INNER_ALPHA)
}
