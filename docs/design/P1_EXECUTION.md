# P0 + P1 实施记录

日期：2026-10-11。单目录 `E:/C015/classy`，未创建额外开发线程。

## 可回退基线

`a38115e` 保存首轮色条、语言精简、重复入口清理及已确认的设计计划。此前本地工程检查为 2280 项 JVM 测试通过、Debug 构建通过、Lint 零错误。基线提交在本地，现有 v0.0.9 tag/Release 未变。

## 问题与实现对照

以下为源码检查发现的问题及实现对照，不是设备截图验收。

| 优先级 | 页面 / 复现条件 | 基线表现 | P1 处理 | 待验证 |
| --- | --- | --- | --- | --- |
| 高 | 今日，长课程名、冲突并栏、字体 1.5 倍 | 固定 74dp 时间栏占用课名宽度；时间强制单行；正文最多两行 | 课名、节次时间、教师地点纵向排列，允许换行；每栏不足 156dp × 字体比例时纵向显示同组冲突课程 | 320dp 窗口 / 英文 / 三栏冲突下阅读和点击 |
| 高 | 今日，日期、星期和状态在窄屏放大 | 日期与状态固定 Row，空间不足时拥挤 | FlowRow 按实际可用宽度换行 | 中英、1.0/1.3/1.5 倍字体 |
| 高 | 今日，打开课程详情后旋转 | 详情选择只保存在 remember 中 | rememberSaveable 保存课程 ID，从当前课程数据解析详情；不持久保存过期实体 | 旋转、进程重建、异步数据恢复 |
| 中 | 今日 / 通用设置，平板横屏 | 内容整屏拉伸，文字和右侧操作距离过远 | 同一 720dp 阅读容器，手机仍保留两侧 16dp；不按设备类型分支重建页面 | 600/840dp 边界、平板横竖屏、分屏 |
| 中 | 通用设置、公共卡片 | 通用页重复 Scaffold；今日卡片与设置卡片分别读取 12/16dp 圆角 | 通用页复用 SettingsScaffold；普通分组卡片统一读取 Glasense cardShape | 所有 settingsCard 使用方，尤其自带形状的控件 |
| 中 | 返回 / 语言选择 | 返回行和语言项未显式保证 48dp；语言选中主要靠颜色和图标 | 最小点击高度 48dp；语言单选语义及分组；重置弹窗可保存显示状态 | TalkBack、键盘、旋转 |
| 中 | 公共自定义可点击表面 | noRippleClickable 没有可见按压或键盘焦点反馈 | 按压 8%、焦点 6% 中性遮罩；松手即时撤销，无缩放或排队动画 | 明暗、自定义主题；拖动取消后遮罩释放；不同组件裁切 |

## 公共来源与边界

- 色彩继续由 Glasense → `GlasenseColorBridge` 映射到 Material；没有新增调色板。
- 普通卡面读取 cardBackground / surfaceContainer；普通卡形统一 Glasense cardShape。浮层继续读取已有 elevatedCardBackground / Material 高层表面。
- 字体复用现有 Glasense 与 Material 映射；课程色只负责识别，时间用中性文字。
- `ClassyLayout` 只维护尺寸：页面边距 16dp、项目间距 12dp、组间距 16dp、阅读容器 720dp、触摸目标 48dp，不复制主题颜色或形状。
- 通用设置率先使用 SettingsScaffold 的阅读宽度参数。其他设置页保留原宽度，P2 逐页迁移；公共卡形、返回命中区域和点击反馈已共享生效。
- 页面容器负责底色、阅读宽度；TodayContent 负责内容列表；未来背景是页面底层职责，本批未接图片或玻璃。
- 今日的冲突引擎、课程过滤、时间计算与变淡规则保持原有逻辑；窄屏仍明确显示“冲突课程”。详情继续使用生产 CourseDetailSheet。

## 固定样例与验证入口

仅 Debug 包包含 `DesignSampleActivity`；没有增加生产导航入口，也不往数据库写入演示课表。复用生产 TodayContent 和公共控件。固定时钟为 **2026-10-12 10:30、第 6 周**：长中英课名、长地点、单节/连堂、冲突课程、已结束课程。空态可选择 empty / before / after。示范页点击弹出只读摘要，用于确认点击命中；生产详情、编辑与恢复需在主 App 中另测。

安装 Debug APK 后：

```powershell
adb shell am start -n com.imsx3d.classy.debug/com.imsx3d.classy.debug.DesignSampleActivity --es page today
adb shell am start -n com.imsx3d.classy.debug/com.imsx3d.classy.debug.DesignSampleActivity --es page today --ez dark true
adb shell am start -n com.imsx3d.classy.debug/com.imsx3d.classy.debug.DesignSampleActivity --es page today --es scenario empty
adb shell am start -n com.imsx3d.classy.debug/com.imsx3d.classy.debug.DesignSampleActivity --es page settings
adb shell am start -n com.imsx3d.classy.debug/com.imsx3d.classy.debug.DesignSampleActivity --es page controls
```

设置样板使用 Debug 应用自己的真实设置；语言与恢复默认动作会改变 Debug 应用的设置。控件样板复用已有 ControlGalleryScreen，无新正式入口。深色参数适用于这三个样板。中英语言在 Debug 应用设置切换，字体缩放通过设备设置调整。

每张截图记录：构建提交/工作区版本、设备型号与 API、窗口 dp、横竖屏、字体缩放、语言、主题、刷新率、系统动画缩放。对照尺寸：320×640、393×852、600×960、840×600、1280×800dp；边界补充 599/600 与 839/840dp，真实系统栏占用后再记录内容区。

固定操作：打开页面 → 滚到底 → 点课程/返回 → 旋转/分屏 → 检查滚动和弹层状态；设置页展开语言 → 旋转 → 选择语言 → 返回；动画倍率 1 与 0；键盘 Tab/Enter 和 TalkBack。今日与设置本身无文本输入框；编辑键盘安全区留在 P2，不能据此宣布全应用键盘验收通过。

## 验证状态

工程验证完成：`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 全部通过；2280 项 JVM 测试，0 失败/错误/跳过；Lint 0 错误、657 警告、20 提示（未在本批清理全部警告）。ARM64 APK 通过 apksigner 签名验证，包名 `com.imsx3d.classy.debug`，版本 `0.0.9-debug`，可与正式版并存。最终日志：`E:/C015/99-整理记录/P1-final-validation-20261011.log`。

体验包目录：`E:/C015/交付/Classy-P1-20261011`，含 ARM64、ARM32、x86_64 APK、SHA256 及安装说明。优先使用 ARM64 包。

当前 `adb devices` 无设备，SDK 未安装 emulator/system-images，尚未执行原生截图、模拟器布局、触摸、TalkBack 或性能验收。没有用浏览器方向稿代替原生样板。

本批可交付工程实现与 Debug 体验 APK；P1 的设备验收仍开放，P2 全应用推广尚未开始。
