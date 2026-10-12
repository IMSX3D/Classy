# 第二阶段设备验收：第一批

日期：2026-10-11。状态：进行中，不能标记全部页面验收完成。

## 环境与证据

原本无真机、无模拟器。本轮从 Google 官方仓库下载安装 Android Emulator 和 API35 Google APIs x86_64 镜像，校验官方 SHA1 后解压；WHPX 硬件加速可用，使用 swiftshader 图形后端。独立 AVD ClassyStage2 位于 E:/T1/tools/android-sdk/classy-stage2.avd，测试包 com.imsx3d.classy.debug。未触及个人真机或正式版数据。

尺寸是模拟器 wm 覆盖：手机 1080×1920/420dpi（约 411×731dp）；平板 1600×2560/240dpi（约 1067×1707dp）；窄窗口 540×960/240dpi（360×640dp），系统字号 1.5。上述是显示尺寸测试，不等同于 Android 系统分屏或真实平板。

证据目录 E:/C015/99-整理记录/：
- stage2-device/interaction-tests.txt：首次设备测试，3 通过、1 失败，保留原失败。
- stage2-all-device-tests.log：最终手机尺寸，14 项通过。
- stage2-all-tablet-tests.log：最终平板尺寸，14 项通过。
- stage2-narrow-tests.log：窄窗口大字体，4 项选择器/布局测试通过。
- stage2-repository-gestures.log：初次旧拖动测试失败；修正测试夹具后最终日志通过。
- stage2-smoke/：真实应用页面截图及 UI hierarchy。
- stage2-final-build.log：Debug/测试 APK 构建和 2337 项 JVM 测试通过。
- stage2-emulator*.log、stage2-avd-create.log：环境搭建信息。

测试可用 tools/validation/run-stage2-device.ps1 重跑，只允许 emulator-*，不默认在用户真机执行。需先启动 AVD、构建 app 与 androidTest APK。该脚本验证四项交互测试，完整 14 项通过直接 instrument runner 运行。

## 已发现并修复

公共 TimePickerField 用禁用 TextField 包裹外层点击，第一次打开后取消并更改字段，再点击无法稳定重开。设备测试失败；等待 350/500ms、增加 3 秒窗口出现等待、避开状态栏均不能解决。最终让装饰 TextField 清除语义，顶层 matchParentSize 点击目标承接整块触摸，提供字段值、标签和按钮角色。未修改时间保存/取消规则、未添加业务延时。修复后手机、平板、窄屏设备测试均通过。

旧 GridCourseGestureTest 定位裸数字，但实际标签通过 period_format_node 本地化；且未传 timeJson 导致标准节次分类夹具不完整。改为资源标签和已有默认时间表，保留原点击、拖动、目标范围和未打开详情断言。两项测试最终通过，不将最初定位失败报告为产品拖动故障。

## 验收记录

| 项目 | 结果 | 范围限制 |
| --- | --- | --- |
| 日期取消不提交、重新打开使用当前字段值 | 三种尺寸通过 | 未逐一覆盖系统日期输入模式/所有语言 |
| 时间取消不提交、重新打开使用当前字段值 | 修复后通过 | 覆盖实际点击与确认回传 |
| 时间弹窗保存状态恢复、无自动写入 | 三种尺寸通过 | StateRestorationTester，不等同于真实 Activity 旋转或杀进程 |
| 1.5 倍字体、280dp 字段区域起止时间上下排列 | 三种尺寸通过 | 断言位置与可见性，不代表所有表单适配 |
| Room 保存/移动相关事务 | 8 项手机和平板通过 | 测试使用内存数据库，不覆盖真实文件导入及故障恢复全部路径 |
| 空网格选择、扩大范围、确认新增；课程长按移动 | 2 项手机和平板通过 | 单组件测试，不等于整页切周/拖动所有组合 |
| 平板我的→设置→课表显示，返回链 | 基础启动/导航和截图检查通过 | 未录制完整动画或测量帧时间 |
| 窄屏大字体设置→返回→外观 | 基础显示/返回通过 | 明亮中文；外观截图选中填充是测试环境状态，不改变设计原则 |
| 提醒页系统通知授权拒绝 | 返回提醒页正常 | 只验证该拒绝路径，未测闹钟、后台、OEM通知 |
| 真实平板、真实桌面组件、系统分屏 | 未覆盖 | 需要真实设备或后续专门场景 |
| 全部 19 路由动效、快速返回、预测性返回、减少动态效果 | 未完成 | 本批不得表述为所有页面丝滑验收通过 |
| 深色、English、键盘、双栏长列表、导入/保存/复制/删除全链 | 未完成 | 下一批优先 |

截图 narrow-large-display.png 在 wm 调整过程中取到旧渲染尺寸，不作为窄屏通过证据；narrow-large-appearance-settled.png 才是 540×960 的稳定画面。所有截图是模拟器，不冒充真机效果。

## 后续顺序

1. 使用虚构课表数据补全 19 路由操作链，录屏检查进入/返回/保存/删除，重点课程编辑和双栏连续调整。
2. 明暗、中英、键盘、真实 Activity 旋转、系统分屏、动画关闭；错误/取消/重复点击回归。
3. 用户真机和平板补桌面组件、权限、实际流畅度与真实学校导入。测试账号不写进验收文件。
4. 所有未覆盖项逐项更新，不因构建或本批通过就勾选阶段完成。

本批仅本地提交与 Debug APK 交付，未推送、未修改版本、tag 或 Release。


## 第二批：Android 16 真机（2026-10-11 晚至 2026-10-12）

用户连接并授权真机验收。V2507A，Android 16 / API36，1080×2400、480dpi（360×800dp），系统字号与三项动画缩放均为 1.0。使用用户已有测试版的深色中文环境，覆盖安装本地 Debug APK；正式版未操作。测试前已将 Debug 私有数据备份到仓库外，不提交设备数据、截图中的课程信息或备份。

### 测试与实际操作

- 分组执行：InteractionStageTwoTest 4/4、GridCourseGestureTest 2/2、两组 Room 事务测试合计 8/8，全部通过。Room 使用内存库；涉及 AppPrefs 的测试在 teardown 恢复原值。
- 首次全套运行在第 9 项 UI Activity 启动阶段停住，由操作者 force-stop 后日志报告 Process crashed；不能把这个结果写成产品自发崩溃或整套通过。将应用置于前台后分别运行 UI 类通过；具体 ROM 限制原因未确认。保留首次与分组日志。
- 实际访问：课表/管理/我的、设置、通用、课表显示及底部灰显分组、节假日、外观、提醒、小组件与实例编辑、全部课表、编辑当前课表、课程清单及课程组详情、作息列表、导出、导入面板、教务学校选择 Activity 及返回。以上是入口/返回检查，不代表每个页面的全部业务和动画已通过。
- 课程编辑：分别从第一、第二个上课安排进入，稳定画面定位相应时段；录制第一时段进入和返回，抽帧未见先展示顶部表单的中间画面，但不能据有限采样保证永不复现。系统返回和侧滑均回课表。第二时段打开键盘并临时编辑地点，返回出现放弃确认；继续编辑后再放弃，重新打开详情验证原地点未改变。没有保存或删除用户课程。
- 桌面实看今日 4×2 与最近两天 4×2；跨午夜后内容更新为新的一天。圆角内侧有留白，列表底部仍可见部分下一行和渐隐，需继续核对滚动可发现性；4×4 实际桌面及重新添加/调整尺寸未验收。实例编辑页不代表桌面外观通过。
- 未改变用户字号、系统动画比例或语言/主题设置。本轮结束返回课表页。

### 发现与修改

导出页用 CourseEntity 数量显示“门课程”，真机同一课表在“我的”为 13 门、导出却显示 24 门。实际 24 是上课安排数。将默认中文、简体中文及 English 的 export_course_count 改为“%1$d 个上课安排” / “%1$d scheduled sessions”。不改数量计算及导出数据。Debug 资源构建通过，覆盖安装后在真机验证显示“24 个上课安排”。14 项测试执行于文案变更前；文案变更后执行构建与实际页面核对，不冒称重跑全部测试。

### 流畅度仍未达成最终验收

使用 dumpsys gfxinfo 在限定操作区间 reset 后取样：

| 样本 | 总帧数 | Janky frames（非 legacy） | P95 / P99 |
| --- | ---: | ---: | --- |
| 设置等页面导航，录屏开启 | 618 | 19（3.07%） | 38 / 65 ms |
| 课程编辑进入/系统返回/侧滑，录屏开启 | 521 | 14（2.69%） | 44 / 105 ms |
| 设置↔显示、设置↔通用各重复三次，无录屏 | 634 | 19（3.00%） | 34 / 69 ms |

这是 Debug 构建的聚合帧统计，录屏样本和无录屏样本操作不完全相同，不做严格性能对照，也不据此定位某个函数。无录屏仍有超时帧，需要后续用可分析的接近发布配置、逐链 FrameTimeline/系统跟踪定位 UI 线程工作；不能将“看得见过渡”当成全部丝滑通过。未修改转场参数掩盖问题。

### 证据与下一步

仓库外证据：E:/C015/99-整理记录/stage2-real-device/。pickers.txt、gestures-foreground.txt、repository.txt 为最终分组结果；instrumentation.txt/gestures.txt 为中断结果；settings.mp4、edit.mp4、对应 gfx 文件及页面截图/XML；device-config.json；copy-build.txt。私有数据备份仅用于本地恢复，不随 APK 交付。

后续优先：真机转场性能定位；主题/日期/时间页面级取消与恢复；作息编辑、提醒草稿；真实旋转/分屏、English/明亮/大字体；4×4 桌面与小组件添加；使用独立虚构课表完成保存、复制、删除和导出多数据源场景。真实学校登录、完整导入、预测返回中途取消、所有 19 路由全链仍未覆盖。第二阶段继续保持“进行中”。


## 第三批：真实平板与性能跟踪（2026-10-12）

用户临时收回手机并连接 vivo PA2553 平板，Android 16，1968×2800、400dpi，横屏约1120×787dp，深色中文，字号1.0。停止操作原手机。新增独立 benchmark 构建（.benchmark、非 debuggable、R8、profileable、Debug 本地签名），不覆盖正式/Debug 数据；非正式包不提供生产更新。平板原本无 Classy，本批使用空数据，不能与手机有课表的 Debug 样本直接比较。

### 本批代码及验证

- 首页无课表提示卡横屏铺满大部分宽度；修正为共享720dp阅读容器内居中，同时处理已有课表无课程的提示。真机截屏验证无课表修正；有表无课分支仅构建验证，后续补数据实测。
- 通用语言单选：当前语言重复选中不再写偏好/重建 Activity。真机实际点击展开列表中的已选项后仍保留展开状态；第一次误点折叠标题的截图不作为该修复证据，以 same-language-option.png 为准。
- W1 首批12个入口决策见第一阶段台账新增段落。主标签内容目前无过渡、管理页一次性事件与重组的关系、开关行语义/星期重复实现列为待修或调查，未声称本批完成这些修改。
- Benchmark 与 Debug 构建成功；2337 JVM测试零失败/错误/跳过，arm64两种包签名校验通过。最终修正版 Benchmark 已安装平板。

### 平板实际检查

横屏：侧栏导航、我的→设置→显示/通用正常进入；提示卡限宽实看通过。
竖屏：通过系统旋转实际重建/重排，通用页截图1968×2800；原加速度旋转=1、user_rotation=1在 finally 恢复并核对。
系统分屏：通过系统顶部多窗口菜单选择分屏，另一侧打开系统设置作为占位。dumpsys确认Classy task为multi-window，约557dp窗口切底栏；进入课表显示并返回全屏后仍在该页面。证据split-active/split-display/after-split-display与split-activity.txt。未改变系统设置项；最终退出分屏并回课表。并非wm尺寸模拟；但尚未验证有数据双栏、编辑草稿和拖动中的跨断点。

### 性能结果和局限

基线为修复前Benchmark，空数据、横屏、不录屏，设置→显示→返回→通用→返回，三组gfxinfo分别：222帧/5超时(2.25%)、222/4(1.80%)、220/4(1.82%)；P95分别24/19/30ms，P99为40/38/36ms。这三组未严格分离首次进入与预热，并有UIAutomator取节点，因此只能作为探索基线，不能用来声称达到最终流畅度或修复带来百分比提升。

随后使用45秒Perfetto采集相同导航链。初次配置路径遭ROM读取权限拒绝，改stdin输入后成功。分析工具官方存储首域不可达，使用同一官方bucket的storage.googleapis.com地址下载。完整trace在录制退出后重新拉取，分析成功。

FrameTimeline归类：App Deadline Missed=15，App Deadline Missed+Buffer Stuffing=1，Buffer Stuffing=182，None=136。不要将Buffer Stuffing直接算成应用卡顿率。主线程汇总见trace-summary.txt：measure最大22.16ms、AndroidOwner:onMeasure最大22.06ms、Recomposer:recompose最大10.28ms。它们是包含子过程的耗时，不能相加；尚未逐慢帧关联到具体业务组件，不能宣布根因已经锁定。下一轮优先将页面进入测量与重组按单帧、具体组件细分，并去除UIAutomator观测干扰。没有改转场曲线掩盖慢帧。

复现资料入仓库：tools/validation/PERFORMANCE_CAPTURE.md、classy-navigation.pbtxt、classy-navigation.sql。本地完整证据：E:/C015/99-整理记录/stage2-tablet-device/；构建日志tablet-benchmark-build.log、tablet-fixes-build.log位于上一级。设备截屏/trace含系统内容，不随源码或安装包公开。

本批仍不标记第二阶段完成：首标签内容过渡、带数据的平板双栏/草稿、完整页面/组件状态与真实帧归因继续推进。无推送、无版本/tag/Release更改。


## 2026-10-12 自主回归与修复（接19d3706）

代码以包含本节的提交为准。设备为已授权 PA2553 平板，独立 Benchmark 数据；旧手机未操作。仅使用虚构课表，不涉及学校账号。证据目录 `E:/C015/99-整理记录/stage2-tablet-device/`，截图/XML按下列前缀检索。

| 操作链 | 本轮结果与证据 |
| --- | --- |
| 空课表引导→来源→系统文件→预览→确认 | 虚构8节、4门课5个上课安排导入成功；修复成功后停留来源面板。最终回管理页，`final-import-confirm/exit`；文件选择器重名节点须选搜索结果，首次脚本误点不算产品失败 |
| 来源面板→教务→返回 | 先隐藏来源面板再进入学校列表；返回管理页，`final-school-entry/return`；未登录，不宣称学校全链通过 |
| 双栏课程清单 | Math两安排，横竖屏保留选中，返回先退详情；`final-course-detail`及前批旋转证据 |
| 课程第二时段→键盘→旋转→返回/保存 | 发现 ROTATE-EDIT-01：旧 NavSession 旋转丢失导致退页。改 Activity ViewModel；`rotation-fixed-portrait/landscape/back/saved/restored`证明 Room 102-QA 草稿保留、放弃询问、保存并重新打开核对，之后恢复 Room 102 |
| 课表复制/删除 | 副本取消与确认删除；删除文案使用5个上课安排；修复返回时暗改主标签，`delete-final-message/return` |
| 作息保存 | 无绑定直接保存 `period-direct-save`；将虚构副本换绑到12节作息后，仍有换绑确认和共享影响预览（1张表/5安排不变），取消后留在编辑；`shared-period-save-confirm/impact` |
| 主题任务 | 新建→颜色取消→随机配色→返回放弃询问→继续→保存→编辑→删除取消/确认；`final-theme-*`。测试主题已删除，配色恢复系统 |
| 导出 | 从当前UI Acceptance2切到UI Acceptance导出原生文件；只打开系统分享后取消，没有发送；回管理仍是UI Acceptance2，`final-export-*` |
| 提醒 | 拒绝权限后可重开授权；时间取消保留07:00；测试结束关闭提醒总开关，避免虚构课程通知 |
| 语言/字号/主题 | 简中→English保留通用页面；英文1.5字号课表显示首屏与中段无截图可见遮挡；返回简中、字号恢复1.0。外观浅色/深色检查；`final-language-english`、`english-large-display*`、`final-light-appearance`。不是所有页面配置笛卡尔积通过 |
| 关闭动画 | 暂时将3个系统动画比例设0，课表显示/通用进入返回可用；finally恢复原值；`zero-motion-*` |
| 关于/许可 | 信息页与许可入口及返回正常，`final-about* / final-license`，未实际发送反馈或安装更新 |
| 桌面组件 | 应用内固定请求返回成功但OEM无确认；改桌面组件搜索手动添加4×4成功，实际被宿主压成很矮一行，内容不足以阅读。选择器顺序正确。`launcher-widget-* / final-widget-grid-desktop / widget-resize-menu`。仅观察到移除菜单，未完成正常尺寸及缩放验收；测试组件已移除。禁止把这项记通过，也不擅改用户要求的40dp自由缩放下限 |

验证：最终业务代码 Debug/Benchmark 构建成功；JVM2337/2337，无跳过；模拟器API35最终16/16（Room8、网格2、选择器4、主标签/开关2）。lintDebug 0错误、555警告、21提示；没有把历史警告说成已清零。日志：`interaction-rotation-final-build.log`、`interaction-rotation-final-device-tests.txt`。

性能：同旧空数据采样方法的优化前三组为5/222、4/222、4/220；后为5/222、5/224、5/226，未证明改善。optimized trace慢布局21.35ms，其中主线程Running21.29ms、可运行等待0.07ms（裁剪到该slice的调度区间），可确认这个样本主要在CPU执行，不能归因于系统调度等待。包含多个Compose重组/应用和文字布局，无单一业务函数已被证明为全部根因。尝试仅Benchmark引入runtime-tracing，实际仍未获得函数级切片，故撤掉该诊断依赖；试验trace不混入正式性能对比。无录屏固定坐标采样与ART编译诊断另存precompile/postcompile文件，结论见最新交接。

尚未通过的边界：PERF-01持续慢帧；OEM周网格正常尺寸/拖放；预测返回手势中途取消；进程死亡完整草稿恢复（当前按既有安全退出）；数据库失败UI注入；学校真实登录/导入；全部页面×语言/主题/字号/窗口组合。已通过的局部测试不替代这些项目，不宣布第二阶段整体完成。

ART诊断补充：固定坐标无录屏，编译前超时4/224、5/224、4/224；speed编译后4/226、4/232、5/224，无显著改善。这是包含临时诊断依赖的测试包，不能冒充最终APK发布性能。


## 2026-10-12 平板安全区、组件圆角与小号比例修复

用户材料：Redmi K80 Ultra / Android16 / HyperOS3.0.305.0，视频中2×2宿主轮廓为矩形；未连接该红米，不能宣称红米实机通过。授权平板上当前已有用户添加的四种Benchmark组件，本轮保留其位置与绑定，没有新增或删除。

- INSETS-TABLET-01已复现：宽屏MainRoute的Row无系统安全边距，NavigationRail仅照顾自身，ScheduleScreen顶部日期/图标与状态栏重叠。Row统一windowInsetsPadding(safeDrawing)，同时消费紧凑Scaffold已应用的padding，防后代重复避让。平板横屏四标签、竖屏课表、设置子页与小号组件点击进入通过截图；`user-insets-before` 对比 `insets-fixed-*`。手机窄屏模拟器空态启动截图仅证明状态栏避让，不代表所有窄屏大字号内容已验收；实际系统分屏本轮未完成。
- WIDGET-CORNER-01：生产RemoteViews旧12dp圆角不能依赖OEM再次裁圆。四种组件、Canvas备用绘制、预览统一使用widget_corner_radius=28dp，16dp内容安全内边距保留。生成脚本读同一资源并重新生成中英预览，避免预览与实物不同。
- WIDGET-SQUARE-01：Android12+小号今日根据宿主上报的尺寸，在透明容器内居中放置min(width,height)方形卡面。多尺寸RemoteViews让宿主选择实际方向；无sizes列表按现有尺寸解析回退。其余三种维持自由填满，桌面占位格与40dp自由缩放下限不改。系统仍可能分配长方形格子，剩余区域留空。Android8–11保留原有填满行为；红米具体拖动、旋转、宿主尺寸真实性仍需该设备验证。
- 真平板四种实物已刷新：`widget-corners-after`（周网格正常可阅读高度，内层到底部前裁切），`widget-corners-after-page5`（小号正方形、今日横条、最近两天）。小号点击进入课表通过 `small-widget-open-app`。这说明此前矮条不是周网格必然呈现；极小高度兼容与新添加默认高度仍不算全部解决。
- 验证：2337 JVM全过；17仪器测试全过，新增RemoteViews实际inflate/measure/layout测试，160×250、300×140、200×200宿主中卡面等宽高且不越界。最终Debug/Benchmark、lintDebug构建成功，Lint0错误/556警告/21提示。业务代码设备测试后只更新预览PNG资源，最终Benchmark已重新安装并启动核对。
- 日志：widget-insets-final-build.log、widget-insets-device-tests.txt、widget-insets-delivery-build.log；证据仍在E:/C015/99-整理记录/stage2-tablet-device/。平板旋转恢复1/1，未修改课表数据。

## 2026-10-12 组件退场圆角、网格填满与平板下置导航

承接087e214的用户复测。已确认上一版强制小号正方形导致卡面不填满宿主；用户明确“几×几对应格数，不必正方形”。删除固定边长适配；四种组件按宿主测量铺满。原底色是内层setBackgroundColor方块、只靠父outline裁切；改共享圆角shape ImageView + setColorFilter，API26+一致，无位图缓存或固定像素尺寸。

vivo PA2553 API36、401G5705710000H：连接途中短暂断开后恢复；新版Benchmark安装成功。四种已有组件全部保留，未移动/删除/新增。小号与今日大号上下边缘对齐、透明余量消除；四种长按时保持圆角。两页退出录屏供逐帧复核。宽屏导航移到左下圆角区，四标签和竖屏截图，旋转设置恢复。系统主题/字号未改。

证据目录 E:/C015/99-整理记录/stage2-tablet-device/：widget-home-after-fill.png、widget-{small,today,twoday,week}-longpress-after.png、widget-week-after-fill.png；widget-exit-after.mp4、widget-week-exit-after.mp4；rail-tablet-{after,today,manage,mine,schedule,portrait}.png。

模拟器API35：1600×1100/density160、900×480/density160、字体1.5，明亮/深色导航截图rail-wide-emulator.png、rail-short-emulator.png、rail-short-dark-emulator.png。三套RemoteViews在160×250、300×140、200×200dp，白/黑底，关闭outline裁切后实际绘制四角透明且填满宿主，仪器测试通过。完整仪器套件17/17，JVM2337/2337；lint0错误、555警告、21提示。旧源码契约强制NavigationRail容器与本轮新设计冲突，移除这一过时断言，保留Material项及Compact分支要求。

边界：红米K80 Ultra/HyperOS3未连接；目标2×2元数据已核对，但不能从视频确认该桌面每格实测尺寸及最小跨度，所以旧正式版红米长方形的OEM层原因仍是待验证，不声称已查明具体系统缺陷。真实分屏/完整配置矩阵及PERF-01仍未通过；本轮修复不表示所有页面性能完成。

## 2026-10-12 vivo 手机恢复连接：首批基线

用户明确换回vivo手机并要求继续，重新授权10AF9A2KMF004Y3（此前临时换平板的限制不再适用）。ADB识别V2507A/PD2507、Android16、1080×2400、density480、font_scale1.0、animator_duration_scale1.0。独立Benchmark 5c1f74d安装成功，不覆盖正式版。此包首次安装为空数据库，空态不能代表有课表全链验收。

四个主标签、我的→小组件、展开添加样式已截图检查；状态栏及底部Dock未见遮挡。Today首次uiautomator dump提示无法idle，复用了旧XML，该XML不能单独作为Today通过证据。点击小号添加只显示“已请求添加”，OEM未显示确认且页面没有绑定项；WIDGET-01添加流程仍待定位，不能算成功。

无录屏、3轮固定坐标主标签点击（每次约0.5秒间隔）gfxinfo聚合528帧、11次deadline missed（2.08%）、P95 19ms/P99 30ms；只是单批空数据样本，非性能验收通过，也不能和不同设备/数据的旧样本直接比较。仍需有数据逐链多组trace归因。

证据：E:/C015/99-整理记录/stage2-vivo-phone-device/phone-*.png及phone-main-tabs-gfxinfo.txt。未修改系统设置，未新建/移动/删除桌面组件，手机留在测试版我的页面。后续：测试数据操作链、OEM添加入口及四组件实际显示、键盘/返回/动画逐链验证。

## 2026-10-12 用户确认与手机通知调查（进行中）

用户明确确认：手机上现有桌面组件全部由用户手动添加，应用内自动添加从未成功。不得用已存在组件或系统搜索结果证明自动添加成功。直接添加仍待定位，更新指引只是手动回退，不是自动添加修复。

暂停后用户归还真机，继续V2507A验收。编辑课程键盘遮挡已复现：窗口mAttrs显示adjust=pan；MainActivity显式adjustResize后真机mAttrs为adjust=resize，phone-ime-resize-after.png显示状态栏与输入框避让正常，未改课程数据。此前错误归因“公共容器”的猜测修正为窗口软键盘模式。

用户新增正式版通知一直不可用，逐设置核查列P1。正式版0.0.9 POST_NOTIFICATIONS已授予，sleepy_daily importance3、sleepy_before_class/流体通道importance4未关闭，手机勿扰开启；保持原设置不改。当前源码发现Application冷启动主动重排导致正在投递的generation失效风险、重复闹钟不精确、流体成功返回吞掉横幅、部分主字段不应用于厂商内容。修复与验证进行中，尚未覆盖正式版升级与后台杀进程/重启全链。


## 2026-10-12 通知可靠性与 vivo 后台限制

本批优先级：用户报告正式版长期无法通知，暂时优先于 W2 动效性能收口。所有桌面组件均由用户手动添加，自动固定未通过。

源码修复：Application 被广播冷启动时不再取消/重排当前闹钟；前台进入、开机/更新时间变化负责恢复。每日摘要/午夜补排改一次性精确 idle 闹钟并自行续排，无精确权限则使用允许 idle 的非精确回退；权限恢复重排。横幅 ID2001 与流体 ID2000 独立，主内容选择写入系统标题及 vivo/魅族字段，优先显示时间/地点时正文保留课名。流体恢复包含跨午夜的明日窗口；前台服务失败提供有截止时间的普通提醒回退。

提醒页增加通知/渠道/准时权限/勿扰状态及系统入口，按偏好变化与返回刷新。vivo 显示经过真机核对的后台耗电路径。权限状态和用户开关分开表述，不声称应用可强制系统弹横幅。

### 真实正式包证据

- vivo V2507A Android16，正式签名包覆盖安装成功，包名 com.imsx3d.classy；版本仍为0.0.9，本地修复包，不更改GitHub tag/Release。正式课表未编辑。勿扰保持开启。
- 首次正式版21:50摘要未达：退到桌面后系统 `dumpsys alarm` 记录 `Reason=frozen`，对应每日/课前等闹钟被移除。随后将该应用“电量→后台耗电管理”由智能控制改为允许后台耗电；截图 production-background-before/allowed.png。仅修改Classy后台耗电设置，没有改全局省电或勿扰。
- 第二次22:00正式包摘要后台送达：退出约2分钟后NotificationRecord确认com.imsx3d.classy、id1001、channel sleepy_daily、importance3；此前闹钟保留并显示window=0。说明此vivo后台限制确实影响提醒，不能只归因应用权限或代码。
- 测试后今日摘要恢复07:00；明日预告保持关闭及22:00，课表未改。production-reminder-restored.png/xml可核对。
- 路径 E:/C015/99-整理记录/stage2-vivo-phone-device/：production-delivered-after-background.txt、production-background-alarm-retained.txt、production-reminder-final.png。通知记录仅保留Classy条目。

### 设置与回归覆盖

| 项目 | 证据/边界 |
| --- | --- |
| 今日/明日开关及每日总开关 | 模拟器真实通知投递；先确认存在再关闭，验证撤回且不影响另一项 |
| 提醒总开关 | 模拟器先投递后关闭，实际通知被清除；用户子偏好保留 |
| 横幅/流体四组合 | Android35模拟器真实接收器与前台服务，验证各自ID存在/不存在；无debug_force绕过校验 |
| 课前开关及快速开关 | 最后新增回归发现前台服务启动/停止竞态，修复与复测记录见下方结论 |
| 课前提前时间 | 使用5分钟窗口验证接收器；冷进程用1分钟实际排程触发；任意分钟/DST边界依赖既有单测，非逐值真机验收 |
| 课程名/时间/教室 | 所有renderer分支实际Notification载荷断言；厂商胶囊外观不等于这些断言已在各品牌实测 |
| 摘要时间 | vivo临时改22:00并实际触发后恢复；模拟器今日/明日同一目标时间各自触发并续排 |
| 冷进程 | 空模拟器合成课表，真实排程后am kill（不是force-stop），到点自动唤醒并送达1001/1002/2001，generation未被冷启动改写；夹具随后删除 |
| 系统通知/渠道/准时权限/勿扰 | 状态查询与恢复路径已实现；vivo通知许可/渠道允许且勿扰开启实测；拒绝所有权限组合、长期Doze/重启待补 |

冷进程日志 reminder-cold-course-{prepare,result}.txt；完整仪器套件及后续补充日志位于 E:/C015/99-整理记录/。快速开关曾出现ForegroundServiceDidNotStartInTimeException，不将此失败隐去：需等服务履行前台契约后再停止，统一主线程启动/停止管理，禁止取消尚未创建完毕的服务。

尚未覆盖：真实vivo课前各组合逐项到点、重启/长期锁屏/极端省电、自启动权限被拒、所有厂商胶囊与红米宿主；不承诺所有厂商限制下100%到达。后台许可是系统策略，不会通过常驻进程或全局关闭省电绕开。

本批最终验证：2337 JVM通过；完整常规仪器18项通过（另2项冷启动分阶段测试默认跳过，显式运行通过）；追加课前关闭与快速开关后曾复现服务竞态，修复后专项首次通过，并连续3轮通过（22.572/30.520/26.891秒），日志reminder-race-fixed-result及reminder-race-repeat-{1,2,3}.txt。Debug/正式Release与lint通过，0错误/556警告/21提示。最后UI与服务改动源码包含在本地交付APK中；真机22:00投递证据早于最后服务竞态修复，不能伪称最终包已重复完成所有定时验证。
