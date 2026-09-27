package com.imsx3d.classy.widget

/**
 * 「今日课程 · 中」变体壳 (设计 §7 M 档 300×160) — 渲染/数据全部继承基类,
 * 仅 info XML 放置尺寸不同 (targetCell 4×2)。variantHint 保持 REGULAR:
 * 拖大拖小显示效果与 L 档一致, 差别只在初始占格。
 */
class TodayWideWidgetReceiver : TodayWidgetReceiver()
