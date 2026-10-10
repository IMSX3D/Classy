# 补充参考调研

日期：2026-10-11。结论：已有参考加上本轮补充，足够启动 P0–P2；动效与玻璃具体参数需在 Classy 真机上验证，不能由项目数量或演示效果决定。

原有正方、SleepDown、Backdrop、Haze 的取舍见 [设计体系](DESIGN_SYSTEM.md)。本轮补足共用组件、适配与性能验证，不增加新的视觉风格。

## 可直接转化为任务的发现

| 参考 | 已查证的实践 | Classy 的采用方式 |
| --- | --- | --- |
| [Now in Android Button.kt](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/core/designsystem/src/main/kotlin/com/google/samples/apps/nowinandroid/core/designsystem/component/Button.kt) | 在 Material Button / OutlinedButton / TextButton 上封装品牌样式，保留 enabled、onClick、内容槽；集中处理带图标的内距和预览 | P1 优先整理现有公共组件和主题桥接；保持标准交互语义，不为统一外观重写点击/禁用行为，也不复制整个多模块架构 |
| [Now in Android 滚动基准](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/benchmarks/src/main/kotlin/com/google/samples/apps/nowinandroid/foryou/ScrollForYouFeedBenchmark.kt) | Macrobenchmark 使用 FrameTimingMetric、固定滚动步骤、10 次迭代，对照不同编译模式 | P3/P4 在同设备固定课表与操作下采集帧数据；基础基线就记录构建和编译条件，避免拿 Debug 与 Release 比较后误判效果开销 |
| [Compose Samples / Reply](https://github.com/android/compose-samples/blob/5a390e4a9d3c4e5b30e942041bde93582c4d9492/Reply/README.md) | 官方样例说明按窗口尺寸选择导航形式与单/双栏内容，支持明暗主题和动态窗口变化 | P1/P2 检查横屏/窗口变化/字体放大。借鉴适配原则，不自动把 Classy 改成邮件式双栏，也不扩大本轮范围去重做所有大屏导航 |
| [Tivi 启动基准](https://github.com/chrisbanes/tivi/blob/a0c62c2c763c83e3a0ecf79b283224374bb06c4a/android-app/benchmark/src/main/kotlin/app/tivi/benchmark/StartupBenchmark.kt) | 实际应用使用 Macrobenchmark、冷启动、5 次重复和明确初始化步骤 | P5 按需要增加启动检查，防止新增材质依赖或初始化拖慢启动；该文件不能用来证明 Tivi 所有动画的流畅度 |

上述三个仓库 API 返回的许可均为 Apache-2.0；引用代码时仍核对具体文件与保留声明。本轮阅读仓库目录、固定提交文件和 README，没有运行这些应用、没有复制其实现、没有新增依赖。

## 对开发计划的补充

1. P0 记录固定数据与设备设置，并记录构建类型/编译模式。设备不可用时可继续参数与样板开发，真机验收保持待完成。
2. P1 共用组件优先封装现有标准组件，保留语义、焦点和禁用行为；已有自绘组件明确补齐验证。无障碍验收仍需 TalkBack 实测，不能因引用官方样例就视为完成。
3. P2 使用窗口变化和字体放大验证布局，不追求每个页面同一密度。
4. P3 延续正方项目关于中断连续性的调研，具体到 Classy 的导航状态、手势取消和返回链路时再定点读源码。
5. P4 在接入前复核选定玻璃依赖的固定版本、平台下限、采样边界与当前依赖兼容性；用真机测量作采用依据。

停止扩展调研的条件：已能为当前阶段说明采用什么、解决什么、如何验证。剩余资料需求跟随具体技术问题，不把浏览 GitHub 作为持续推迟样板交付的前置条件。
