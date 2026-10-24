# P4：独立 Dock 材质原型

> 历史记录：本文写于 当时，记录当时的验收过程。"本机证据目录""交付目录"是开发者本机路径，不随仓库分发；结论与判据仍有效。

2026-10-11。完成范围为工程原型，不是正式接入或性能验收通过。

## 实现与兼容

- 复用项目已有 `io.github.kyant0:backdrop:2.0.0`，app 仅新增相同版本的 `debugImplementation`，不升级 Kotlin/Compose，不引入第二套渲染依赖。
- 根据 Maven 发布的 2.0.0 源码核对 `rememberLayerBackdrop`、`layerBackdrop`、`drawPlainBackdrop`、`blur` 和 `lens`。Backdrop 为 Apache-2.0；这里只调用 API，没有复制上游示例实现。
- Debug 外观页增加“底栏材质预览（测试）”。独立 Activity 只使用固定样例，不写课表或外观偏好；生产构建无此 Activity，入口由 BuildConfig.DEBUG 隔离。
- 共用 PillNavigationBar 增加可选 Dock 表面 Modifier。默认调用行为不变；原型实色和玻璃复用同一份座位、选中状态、按压反馈与底部 inset。
- 只有内容层记录采样，Dock 为兄弟层，文字与图标不参与模糊；关闭玻璃时不记录背景。使用 8dp 模糊、12dp 边缘折射范围、8dp 折射量和 72% 主题表面叠色作为初始候选。无色散、亮边或持续循环。
- Android 13+、硬件加速且非省电模式才进入完整玻璃路径，否则同几何实色降级。省电广播动态刷新；用户选择通过 rememberSaveable 保存，旋转不重置。
- 测试页可切换明暗、复杂背景并滚动样例、快速切底栏。平板上仍只测试 Dock 材质，不改变正式导航 Rail。

## 验证边界

纯策略测试覆盖 Android 26–32 回退、Android 33 开启、用户关闭、软件渲染与省电回退。构建与测试不能证明玻璃可读性、帧时间或 GPU 兼容性；没有 Android 设备时，不开放正式开关。

设备入口也可使用：

```powershell
adb shell am start -n com.imsx3d.classy.debug/com.imsx3d.classy.debug.GlassSampleActivity
```

同一设备分别以实色和玻璃滚动固定样例、快速切换、返回再进入、横竖屏/分屏；按主计划记录 P95 帧时间、卡顿帧比例、内存与静止渲染。Debug 页供功能与观感检查；性能门槛要用后续可分析的非调试构建测量，不能以 Debug 测量替代正式结论。

## 新增优先级

用户在本阶段指出页面/按钮/弹层仍割裂。已整理 [高频交互统一清单](INTERACTION_UNIFICATION.md)，作为玻璃正式接入的前置工作。当前原型保持隔离，不扩展到主页面、课程卡或菜单。

## 本轮工程结果

2297 项 JVM 测试通过，0 失败/错误/跳过。Debug 构建、Lint 和 Release Kotlin 编译成功；ARM64 APK 签名校验通过。Lint 分布：{'Warning': 663, 'Hint': 20}。日志：`本机证据目录/P4-validation.log`。安装包：`交付目录/Classy-P4-Prototype-20261011`。未推送，未改动版本号或 tag/Release。
