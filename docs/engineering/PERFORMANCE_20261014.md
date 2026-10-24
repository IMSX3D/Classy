# 冷启动与小组件首次入口：有数据的优化构建测量

> 历史记录：本文写于 2026-10-14，记录当时的验收过程。"本机证据目录""交付目录"是开发者本机路径，不随仓库分发；结论与判据仍有效。

## 测量与结论

设备为 vivo PA2553 / Android 16 平板，横屏 2800×1968、density 400；独立 `.benchmark` 身份、R8 优化、24 条虚构课程、无此身份的桌面绑定。用户真实课表及桌面组件属于 `.debug`，未覆盖。测量期间不录屏。**不能把此结果外推为实体手机或四个绑定组件场景全部通过。**

每组强制停止 benchmark → 启动 → 等待 → 我的 → 首次进入小组件 → 返回；三组。gfxinfo 包含进出动画，不是单个页面函数的运行时间。OEM FrameTimeline 分类与 gfxinfo 旧算法有明显差异；不以“0% jank”判定丝滑。Perfetto 记录首次布局约 15.8ms、重组最长约 38.5ms、postAndWait 最长约 48.2ms，剩余耗时横跨 Compose 与渲染/调度，不能继续只归因数据库。

| 条件 | 小组件链 P95，三组 | P99，三组 |
| --- | --- | --- |
| 初始安装状态 | 9 / 57 / 57 ms | 69 / 65 / 69 ms |
| 同包已采集运行时 profile 后 speed-profile 编译（探索） | 27 / 21 / 27 ms | 44 / 40 / 44 ms |
| 新打包 profile，reset 后安装 profile 并 speed-profile 编译 | 24 / 31 / 27 ms | 53 / 48 / 48 ms |

P99 有下降，P95 首组反而上升；不能承诺每一帧都改善，也不能宣布全局无卡顿。已保留应用 profile 来降低首次执行成本，不改动画时长、不延迟显示页面。真实安装后何时执行优化由 Android/安装器决定，手动强制编译是验证条件，不是用户安装即必然达到的保证。

新增 `ReportDrawnWhen`，首次 table/course/routine 状态完成并绘制后才报告 Fully drawn，包括真实空库。最终三次系统报告为 **180 / 206 / 214ms**；这是该虚构课表/平板/profile 条件的当前基线，旧包没有同一报告点，**不能据此计算冷启动加速比例**。Activity `am start -W` 只代表初始窗口，不能代替课程就绪。

## Profile 来源与维护

`app/src/main/baseline-prof.txt` 含 1960 条应用规则。源规则来自未缩减 Debug 运行时 descriptor，按优化构建在本次课程/我的/小组件旅程实际出现的应用类族筛选；排除 R8 ExternalSynthetic 类，不把优化后的匿名类重写结果直接当源规则。库自带 profile 由 AGP 合并。规则是代码签名，不包含课程、账号或设备内容。

生成器：`tools/validation/filter-baseline-profile.py --source <unminified-profile> --reference <optimized-profile> --output app/src/main/baseline-prof.txt`。这是当前实测的首份配置，不是永久精确的全路径覆盖；导航/Compose/编译器大改后应重新采集并对比。后续可迁移到专门的 Baseline Profile Gradle 生成模块。

已确认 benchmark APK 内 `assets/dexopt/baseline.prof` 6478 bytes、`baseline.profm` 804 bytes，设备 dexopt 状态为 speed-profile。未发布新版本。

## 复测边界

品牌连续礼花另做一次优化构建样本：420 rendered frames，gfxinfo 判定 1 个 janky frame，P95 18ms、P99 25ms，GPU P95 13ms / P99 14ms。证据 `five-pack-brand-benchmark-gfx.txt` 与 `five-pack-brand-benchmark.png`。这是单设备单次样本，不能替代所有入口、长时间操作或跨设备验收。

本机证据 `本机证据目录/five-pack-perf-{baseline,compiled,packaged-profile}/`，含三组原始 gfxinfo、启动报告、Perfetto、设备环境。首份 30 秒 trace 只涵盖部分轮次；三组 gfxinfo 均独立保存，不能把一个 trace 当作三组完整归因。

仍未关闭：实体手机最终包、有 4 个实际绑定组件的同条件测量、全部入口极端配置、OEM 动态刷新率导致的长帧。约 40–50ms 尾部帧仍需后续专项证据；本次不通过反复改布局或缩短动画来隐瞒它。
