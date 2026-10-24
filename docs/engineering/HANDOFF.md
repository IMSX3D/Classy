# Classy 开发接手指南

Classy 是一个本地优先的安卓课程表应用（Kotlin + Jetpack Compose，GPL-3.0），仓库 `IMSX3D/Classy`，主分支 `main`。这份文档给第一次接手的人：怎么跑起来、代码在哪、哪些约定不能破坏、发版走哪条路。

面向用户的介绍在根目录 [README.md](../../README.md)，使用说明在 [readme/GUIDE.zh-CN.md](../../readme/GUIDE.zh-CN.md)。

## 一、30 秒跑起来

需要 Android SDK（compileSdk 37）与 JDK 21。

```bash
git clone https://github.com/IMSX3D/Classy.git
cd Classy
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

| 目的 | 命令 |
| --- | --- |
| 装到手机看效果 | `./gradlew.bat :app:assembleDebug`，产物在 `app/build/outputs/apk/debug/` |
| 跑 JVM 单测 | `./gradlew.bat :app:testDebugUnitTest` |
| 跑设备测试 | `./gradlew.bat :app:assembleDebugAndroidTest`，再 `connectedDebugAndroidTest` |
| 静态检查 | `./gradlew.bat lintDebug`（根任务，同时覆盖 `:app` 与 `:glasense-ui`） |
| 出正式包 | `./gradlew.bat :app:assembleRelease`（需要签名配置） |

Debug 包与正式包可共存：`applicationId` 加 `.debug` 后缀，版本名加 `-debug`；`preview`、`benchmark` 是两个额外变体，不装进正式环境。

## 二、先读这四份

| 文档 | 什么时候读 |
| --- | --- |
| [AGENTS.md](../../AGENTS.md) | 动 UI、动文案、准备发版前必读，是仓库级硬规则 |
| [docs/design/INTERACTION_DELIVERY_PLAN.md](../design/INTERACTION_DELIVERY_PLAN.md) | 主线状态与交互契约，改导航、改交互前 |
| [docs/releases/MAINTAINING.md](../releases/MAINTAINING.md) | 发版流程与面向用户的发布文案规则 |
| [docs/design/DESIGN_SYSTEM.md](../design/DESIGN_SYSTEM.md) 与 [TABLET_ADAPTATION.md](../design/TABLET_ADAPTATION.md) | 动视觉、动响应式布局前 |

## 三、仓库地图

| 位置 | 内容 | 什么时候动 |
| --- | --- | --- |
| `app/src/main/java/com/imsx3d/classy/data/` | Room 实体与 DAO、仓库层、撤销栈 | 改数据结构、改变更语义 |
| `data/jw/`（52 个文件） | 34 种教务系统适配器与登录跳转 | 加学校、修某校解析 |
| `app/src/main/assets/schools.json` | 349 所学校清单，`type` 字段指向适配器 | 新增学校时必须同步 |
| `data/parser/`、`data/diff/`、`data/undo/` | 文本与 ICS 解析、变更比对、撤销 | 改导入或撤销 |
| `ui/` | Compose 界面：`screen/`（today、schedule、imports、edit、mine、widget）、`component/`、`nav/`、`theme/` | 改页面与交互 |
| `widget/` | 四种桌面组件（RemoteViews）与 `notification/` 提醒通知 | 改组件外观、刷新与提醒 |
| `glasense-ui/` | 独立设计系统模块，来自 [Nevodev/Cresto](https://github.com/Nevodev/Cresto)（Apache-2.0）；玻璃效果在 SDK 33+ 才启用 | 改材质与主题基座 |
| `readme/` | 首页素材、中英使用指南、素材校验清单 | 换图、改说明 |
| `docs/` | 设计、工程、发布文档 | 记录决策与验收 |
| `tools/validation/`、`scripts/` | 设备性能采集与截图脚本、组件预览生成 | 复测性能 |
| `.github/ISSUE_TEMPLATE/` | issue 模板 | — |

## 四、动手前必须知道的约束

1. **导航只有两个出口**：`ui/nav/SleepyNavHost.kt` 与 `SleepyRoutes.kt`。新页面进路由表，不要在页面里直接跳内部页面。
2. **数据改动走仓库层 + Room 事务**，界面不直接碰 DAO；撤销栈在 `data/undo/`。
3. **桌面组件是 RemoteViews，不是 Glance**。不要引回 Glance，历史上已经移除。
4. **颜色不硬编码**：主题与材质走 `glasense-ui` + `ui/theme/`；课程色条统一用 `CourseColorUtil.softenedIndicator`。
5. **中英文同步**：`values/`、`values-zh-rCN/`、`values-en/` 三套字符串，改一处就要三处对齐，旧语言偏好由 `SupportedLanguages` 归一化。
6. **首次布局即目的地状态**：区分"加载中"与"空/缺失"，别靠进入后滚动来补。

## 五、常见改动怎么下手

| 想做的事 | 从哪开始 | 注意 |
| --- | --- | --- |
| 支持一所新学校 | `data/jw/` 找同类型适配器，再往 `schools.json` 加条目 | `type` 必须是已有适配器名；教务抓取默认关闭，只对少数学校开启 |
| 修某校导入失败 | 对应适配器，加 `app/src/test/` 下该校 fixture | 学校端会改版，保留登录跳转与兜底逻辑 |
| 改课表网格 | `ui/screen/today/`、`ui/component/` | 手机、平板、分屏三档都要看 |
| 改桌面组件 | `widget/` 与 `res/xml/` 的组件尺寸定义 | 组件尺寸与刷新时机受系统限制，不能承诺实时 |
| 改文案与翻译 | 三套 `strings.xml` | 发布文案规则见 MAINTAINING |
| 加设置项 | `ui/screen/mine/` + DataStore | 默认值要能对齐 `docs/engineering/DEFAULT_SETTINGS_20261014.md` 的口径 |
| 发版 | 见第六节 | 已发布的 tag 与资产不覆盖 |

## 六、发版

流程在 [docs/releases/MAINTAINING.md](../releases/MAINTAINING.md)，要点：

1. 改 `versionCode` 与 `versionName`；
2. 更新 `app/src/main/assets/release-notes.json`（构建会校验版本号与语言，不一致直接失败）；
3. 跑单测、设备包、Lint、正式签名构建；
4. 打 tag 并发 Release：三 ABI APK + `SHA256SUMS.txt` + 验证记录。

面向用户的说明（应用内更新弹窗、Release 正文）只写 3–6 条具体可感知的变化，技术细节留在仓库文档里链接过去。

## 七、上游血缘与兼容红线

项目从 [lingion/sleepy](https://github.com/lingion/sleepy)（GPL-3.0）二次开发。以下标识要保留，否则老用户数据会丢：

- 数据库文件名 `sleepy.db`、偏好文件名 `sleepy_prefs`；
- 导出格式 `classy-v1`，以及仍要能读的旧 `#sleepy-v1` 文本；
- `AndroidManifest.xml` 里组件类名与组件尺寸；
- `data/jw` 的适配器行为与登录跳转。

吸收上游更新时，只取单个适配器或解析规则，先补测试再合并，不整块覆盖本项目界面层。

## 八、现状与已知边界

- 工程审计与未关闭项：[docs/engineering/CODE_AUDIT_20261014.md](CODE_AUDIT_20261014.md)
- 手机验收（帧时间、组件进出链）：[docs/engineering/PHONE_ACCEPTANCE_20261014.md](PHONE_ACCEPTANCE_20261014.md)
- 性能采集口径：[docs/engineering/PERFORMANCE_20261014.md](PERFORMANCE_20261014.md)
- 已知未完成：TalkBack 与部分厂商卡片适配；组件长文本会省略，不宣称任意文本完整展示。

## 九、路径约定

历史文档不再写开发机的绝对路径，统一改用下面这些说法。读到它们时，按右列理解：

| 文档里的写法 | 实际含义 |
| --- | --- |
| `本机证据目录/xxx.log` | 当年的开发者本机日志与截图，**不在仓库里**，也无法获取 |
| `交付目录/Classy-xxx-20261013/` | 当年构建的测试包与交付说明，**不在仓库里** |
| `仓库克隆目录` | 就是你现在 clone 下来的这个仓库 |
| `Android SDK 目录` / `JDK 21 目录` / `Gradle 用户目录` | 本机安装路径，由 `ANDROID_HOME`、`JAVA_HOME`、`GRADLE_USER_HOME` 决定 |

`docs/design/` 与 `docs/engineering/` 里带日期后缀的历史记录，凡引用过本机证据的，顶部都有一行"历史记录"说明：它们记录当时的验收过程，**结论与判据仍有效，路径与设备信息只对当时那台机器成立**。

写新文档时：仓库内文件用相对路径；仓库外的东西用上表的说法描述，不要写盘符。现状以 [INTERACTION_DELIVERY_PLAN.md](../design/INTERACTION_DELIVERY_PLAN.md) 与 [CODE_AUDIT_20261014.md](CODE_AUDIT_20261014.md) 为准，`docs/design/*_2026101x.md` 是当时的记录，不是现状。
