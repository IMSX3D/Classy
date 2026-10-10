# Chrnova 自定义背景评估

调研日期：2026-10-11。仓库：[restarhalf/Chrnova](https://github.com/restarhalf/Chrnova)，读取提交 `e76d47ce82d82cf1ccd5db8d98e1665529df5df1`。

结论：有值得采用的交互与分层思路，尤其是裁切交互、背景与卡面分离、统一响应式设置。Classy 应结合平板裁切、可靠存储与可读性约束重新实现，不直接移植整套图片选择器或无限制透明度设置。

## 已核实的实现

| 内容 | 源码证据 | 对 Classy 的意义 |
| --- | --- | --- |
| 设置入口集中 | `ChangeBackgroundScreen.kt` 提供选择/清除图片、背景透明度、模糊度、组件透明度；已有图片才显示清除入口 | 一个页面完成背景设置，恢复默认入口清楚；Classy 用自身设置组件保持统一 |
| 参数响应式更新 | `BackgroundViewModel.kt` combine 四个配置 Flow，设置回调直接写入 `BackgroundSettingsPort` | 配置源集中，预览与显示读取同一状态；Classy 若提供“应用/取消”，需另建预览草稿，不能照搬每次拖动直接持久化的语义 |
| 背景与内容分层 | `AppContent.kt` 用 AsyncImage + ContentScale.Crop 绘制背景，仅对背景图施加 `blur(25.dp * backgroundBlur)` 与 alpha | 模糊只作用背景、不糊正文；具体模糊半径与低 API 行为需在 Classy minSdk 26 上验证 |
| 卡面透明度独立 | `AppCard.kt` 通过 LocalComponentsAlpha 改 Card 背景色 alpha，而非对整棵内容施加 alpha | 可以让背景更可见而不连文字一起变淡；单纯保持文字不透明仍不能保证复杂图片上的对比度 |
| 裁切交互克制 | `CropScreen.kt` 支持拖动、双指缩放、双击复位、独立重置按钮、缩放读数；手势期间显示三分线；加载/保存状态控制确定按钮 | 适合 Classy：辅助线只在操作时出现，保留显式重置；不需装饰性动效 |
| 解码与方向处理 | `PictureSelectorPortImpl.android.kt` 先读取尺寸/EXIF，按预览与裁切需求采样解码，处理图片方向；`ImageCropMath.kt` 约束裁切边界并映射预览/原图坐标 | 对大照片和旋转照片有参考价值；Classy 还需设内存上限并验证极端尺寸和快速取消 |
| 基础 UI 测试 | `ChangeBackgroundScreenUiTest.kt` 验证设置项、已有图片时出现清除操作、清除回写 null | 可参考用户可见行为测试；本轮未执行该项目测试，不能据此宣称存储/性能已验证 |

## 不直接沿用的部分

1. **缓存持久性。** `ImageCropper.android.kt` 将裁切 JPEG 写入 `context.cacheDir/pictureselector`，返回 FileProvider URI；所读背景回调把该 URI 直接保存为设置。本次检查的路径中未见再复制到持久目录。缓存清理可能使背景失效；Classy 应在确认应用时原子写入受管理持久目录，写入失败保持旧背景。
2. **当前窗口一次性裁切。** `PictureSelectorHost.android.kt` 默认取 currentWindowMetrics 的宽高作为输出大小，裁切后显示再用 Crop 填充。对 Classy 的横竖屏与分屏需求，应保留有足够分辨率的源副本和归一化焦点，按当前容器裁切，避免首次竖屏裁掉的内容在横屏时无法恢复。
3. **图片选择权限与维护成本。** 其 Android 宿主申请 READ_MEDIA_IMAGES 并接自有相册选择页。Classy 首版优先系统 Photo Picker/兼容回退，减少不必要的相册浏览器维护，不直接套用其平台假设。
4. **透明度自由度。** 三个滑块便于精调，但也容易把课程底板调得过透。Classy 优先给出经过验证的默认值；如开放高级选项，限制卡面可读性下限，文字和色条保持独立。背景图不能干扰课程色与已结束课程的变淡含义。

## 推荐进入 Classy 的候选设计

基础流程保持“选图 → 拖动/缩放 → 带真实课程的预览 → 应用”，附恢复默认。裁切页加入手势时辅助线、双击复位和明确重置按钮。

设置页首先提供一个“背景柔化”调节。若实测确有分别控制的需求，在折叠的高级区域提供“背景清晰度”和“卡片底色强度”，不用要求每位用户理解三个独立渲染参数。高级控制仍是候选，并未确定或实现。

预览覆盖手机竖屏、平板横屏和窄分屏，包含课名、时间轴、色条、已结束课程、导航。只有显示图片的裁切预览不足以判断最终可读性。玻璃开关保持独立，不把背景模糊宣传成液态玻璃。

## 读取范围与许可

本轮阅读设置页、ViewModel、设置存储实现、AppContent、AppCard、裁切页/数学、Android 图片宿主/解码/写入，以及背景页面测试；没有运行 APK、做真机性能测试或完成全仓库审计。GitHub API 返回仓库许可为 AGPL-3.0。本轮仅记录设计与工程思路，没有复制代码；将来若引入代码须按实际许可证处理。

相关固定提交源码目录：[背景设置页](https://github.com/restarhalf/Chrnova/blob/e76d47ce82d82cf1ccd5db8d98e1665529df5df1/composeApp/src/commonMain/kotlin/restarhalf/stellar/schedule/ui/screens/ChangeBackgroundScreen.kt)、[裁切交互](https://github.com/restarhalf/Chrnova/blob/e76d47ce82d82cf1ccd5db8d98e1665529df5df1/composeApp/src/commonMain/kotlin/restarhalf/stellar/schedule/pictureselector/CropScreen.kt)、[Android 缓存写入](https://github.com/restarhalf/Chrnova/blob/e76d47ce82d82cf1ccd5db8d98e1665529df5df1/composeApp/src/androidMain/kotlin/restarhalf/stellar/schedule/pictureselector/ImageCropper.android.kt)。
