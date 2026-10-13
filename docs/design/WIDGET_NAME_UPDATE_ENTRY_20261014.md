# 组件名称显示与更新入口（2026-10-14）

## 实现

- 我的→小组件→共用显示设置：新增“显示课表名称”，默认开启。保存后沿用 WidgetUpdater 通知全部已放置组件刷新。
- 今日2×2、今日宽版、最近两天读取同一持久化偏好；关闭采用 GONE 释放名称布局，不隐藏日期、星期和课程信息。最近两天的日期保持靠右。周网格原本无课表名称，不新增空标题。管理页仍显示绑定课表，便于辨认。
- 关于页顶部版本提示复用“获取更新”的检查函数，随后沿用更新说明、下载进度、取消/重试和系统安装器。移除跳 GitHub 的回调与外链图标，检查/下载/安装状态防重复检查。测试包禁止获取正式包更新。

## 验证

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 成功；应用 lint 0 errors、589 warnings、22 hints。
- 更新解析/版本比较/提醒可见性/WidgetUpdater 接线：19项 JVM 测试通过。
- Android 15 模拟器：WidgetSettingsEntryTest 与 WidgetCardGeometryTest 合计5项通过；覆盖开关关闭、离开重进保持、重新开启，以及现有组件圆角和文字边界。
- 初次新增测试错误使用 performScrollTo 查找未组合的 LazyColumn 项，改为列表 performScrollToNode 后通过；并非业务开关故障。
- 未实测本轮真机桌面刷新观感；未伪造新版本发布来测试下载/安装。真实新版本下载、系统安装权限与OEM宿主行为仍需对应条件验收。
- 根级 lintDebug 额外运行 glasense-ui 库检查时发现4项既有 NewApi 错误，位于 DeviceCornerShape.kt 第32/73行的 API31 圆角访问；本次未改此库。应用模块 lint 成功不等于全仓库 lint 成功。

## 交付

ARM64 Debug APK：`E:/C015/交付/Classy-组件名称与更新入口-20261014/Classy-test-arm64.apk`。
构建、JVM与设备日志在 `E:/C015/99-整理记录/widget-name-update-20261014-*`。
本轮未改版本号、未推送、未创建或覆盖 tag/Release。原五项新设计计划保持未实施。
