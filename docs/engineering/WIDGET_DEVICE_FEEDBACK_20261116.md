# 小组件真机反馈归档（ColorOS 16）— 2026-11-16

设备：OnePlus PKR110 / Android 16 (API 36) / ColorOS PKR110_16.0.2.400(CN01) /
桌面 `com.android.launcher` 16.4.5（OplusLauncher）。以下两条都来自同一轮真机反馈。

## 一、点「添加」没有系统确认框（WIDGET-01 的 ColorOS 复现）

### 现象

在 App「我的 → 小组件 → 添加到桌面」点任意样式后：

- 按钮显示「已请求添加」，但桌面不出现确认框
- `WidgetDiscovery` 诊断：4/4 provider `REGISTERED`、receiver enabled，但 `boundIds=` 全空；
  `lastPin=...: REQUESTED`（只代表系统受理，不代表桌面添加成功）
- `dumpsys appwidget` 的 Widgets 列表里没有任何 Classy 实例

### 根因（反编译 `OplusLauncher.apk` + root 打开桌面日志实锤）

系统事件日志显示，`com.android.launcher3.dragndrop.AddItemActivity`（确认框页面）
每次都是「创建后 ~50–300ms 内自行 `finish-imm`，从未绘制/显示」；root 打开
`log.tag.AddItemActivity` 后拿到调用栈：

```
ActivityClient: activity finished by caller:
  Activity.finish
  AddItemActivity.isAllowedAddWidget:187
  AddItemActivity.onCreate:256
```

反编译 `isAllowedAddWidget`：ColorOS 在 AOSP 流程外加了 **WidgetControl 白名单管控**
（`WidgetControlHelper`）。放行条件仅两种：

1. 应用持有 `oplus.permission.OPLUS_COMPONENT_SAFE`（Oplus 生态特权权限，第三方拿不到）
2. 命中 OPPO 云端 RUS 配置白名单：
   `launcher_widget_whitelist_package` / `launcher_widget_whitelist_componentinfo`

否则弹一条 toast「请长按桌面后到“卡片 - 插件”添加」后直接 `finish()`。
已排除：successCallback 缺失、INSTALL_SHORTCUT 权限、组件尺寸/minSpan 超网格
（把 2×2 组件 minWidth/Height 临时改成 40dp 仍被拦）。快捷方式 pin 走另一条路径，
不受此管控（真机弹窗、用户手动同意后 `dumpsys shortcut` 可见 pinned shortcut）。

`settings get secure/global/system` 中没有用户可开的「允许第三方主动添加小组件」开关；
Settings 里那组“是否允许该应用创建微件……”是 AOSP `AllowBindAppWidgetActivity`，
不是本管控的开关。

### 影响与处理

- 非白名单第三方应用在 ColorOS 16 上**无法**通过 `requestPinAppWidget` 主动添加，
  App 代码无法绕过（与 vivo 原子组件平台限制同类）
- 唯一正路：用户手动「长按桌面 → 卡片 → 插件 → 选择 Classy 添加」；桌面自己给出的
  toast 文案也是这条路径
- App 侧改进：`WidgetAddPanel` 在 `REQUESTED` 时立即展示
  `widget_pin_manual_hint`（长按桌面手动添加指引），不再要求用户先展开「添加遇到问题」

## 二、首次添加后组件全透明，手动刷新才显示

### 现象

新加「两日」组件后桌面只剩透明占位（停在 `initialLayout=widget_bitmap_container`，
该布局是透明 ImageView），到 App 里点「刷新所有小组件」后内容才出现。

### 根因（dumpsys 实锤）

ColorOS 把 App 进程冻结后，系统直接**丢弃**发往 provider 的广播，
`adb shell dumpsys activity broadcasts` 里记录得明明白白：

```
BroadcastRecord{... android.appwidget.action.APPWIDGET_UPDATE ...}
  cmp=com.imsx3d.classy/.widget.TwoDayWidgetReceiver
  SKIPPED ... reason: skipped by policy at enqueue: Skip broadcast to frozen process
```

添加组件时 `AppWidgetHostView` 收到的是 `remoteViews == null`，于是桌面显示
`initialLayout`（透明 ImageView）。只有用户打开 Classy（进程解冻）后，广播才能
送达，provider 才真正渲染 —— 这也解释了「打开 Classy 后卡片会刷新」。
（本 ROM 上首次添加也不会发 `ACTION_APPWIDGET_ADDED`，只发
`ACTION_APPWIDGET_ENABLED` + `ACTION_APPWIDGET_UPDATE` 且都被冻结策略拦下。）

### 修复

1. 所有 widget info XML 声明
   `android:configure="com.imsx3d.classy.widget.WidgetConfigureActivity"`：
   桌面在添加组件时会先拉起该 Activity，**App 进程因此被唤醒/解冻**，
   随后系统的 `APPWIDGET_UPDATE` 广播可送达并渲染。
2. 该 Activity 用透明主题 `Theme.Sleepy.Translucent`：首添路径无 UI，不闪白页；
   re-edit 路径在代码里 `setTheme(Theme.Sleepy)` 恢复不透明编辑界面。
3. 首添路径额外 `WidgetUpdater.armFirstAddRefresh` 排 2.5s 后的一次性补渲染
   （WorkManager，进程此刻是暖的），双保险。
4. 守住契约：`WidgetInfoXmlContractTest` 现在要求每个 info XML 都声明 configure，
   且 configure Activity 必须使用透明主题；`WidgetFirstAddRefreshContractTest`
   守住补渲染接线。

## 证据与限制

- 设备日志、`dumpsys appwidget`/`dumpsys shortcut`、反编译产物只在开发机，不入仓库
- ColorOS 版本相关：本结论针对 ColorOS 16.4.5 / Android 16；旧版 ColorOS 社区实测能弹窗
  （[掘金：国产设备 AppWidget 适配](https://juejin.cn/post/7511583779578331187)、
  [掘金：代码申请添加小部件](https://juejin.cn/post/7302241918351114274)），
  不同 ROM 表现可能不同
- 手动添加后组件渲染：待真机复核（本轮反馈设备上手动添加后内容显示正常，已截图）
