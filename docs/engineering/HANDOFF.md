# Classy 开发接手指南

手机共用优化已在 vivo V2507A 上验收首批，范围与大字号七列限制见 [手机验收](PHONE_ACCEPTANCE_20261014.md)。平板已获用户明确认可，不因手机验收重启平板视觉微调。

最后一轮执行见 [有界收口](FINAL_BOUNDED_PASS_20261014.md)。用户体验包使用 `:app:assemblePreview`：Release 优化、`.debug` 测试身份和原 debug 签名，保留测试版课程与组件绑定；不是正式发布包。`Debug` 留给 instrumentation/诊断，`Benchmark` 仍是独立合成数据身份。性能对比不可把三者或不同数据混为同一条件；同身份覆盖安装也不应卸载/清数据。

最新批次入口：[五工作包收口](FIVE_PACK_CLOSEOUT_20261014.md)。性能 profile 来源、再生成方法与适用范围见 [性能记录](PERFORMANCE_20261014.md)。自动更新检查的生命周期门控为 `StartupCheck`；成功无更新也缓存，失败可重试，关闭时旧请求不能回写。

通知冷进程/重启检查使用 `tools/validation/cold-reminder.py`，仅限隔离空模拟器。Android 会在 instrumentation 开始/结束强制停止应用，不能在第二段 instrumentation 中读取通知来证明前一阶段送达；工具先恢复正常启动排程，从系统记录读取结果后才清理。禁止把此脚本指向用户设备。

## 起点与责任边界

唯一活动仓库为 `E:/C015/classy`，远端 `IMSX3D/Classy`，主分支 main。先读根目录 AGENTS.md 与 docs/design/INTERACTION_DELIVERY_PLAN.md，再查看对应工作包状态。旧并行目录不再开发。遇到合并冲突先讨论，不自行舍弃任一功能。已发布tag/附件不可覆盖。

| 模块 | 入口与职责 | 修改时必须保留 |
| --- | --- | --- |
| 生命周期/导航 | SleepyApp、MainActivity、ui/nav | Activity/条目拥有状态；进入目标就绪再显示，离场保持内容；21条路由契约见交互台账 |
| 数据与事务 | data/entity、dao、AppDatabase、Migrations、repository/ScheduleRepository | atomicEdit串行化写入和撤销快照，事务成功后刷新；不能绕过仓库拼写多个DAO操作 |
| 课程日期与作息 | CourseDateResolver、TimeTableUtils、PeriodTableEntity、TimeTableEntity.hydratedWith | 当前表原始timeJson可能是旧兼容快照；优先有效绑定作息；ownTime是实际时刻，不可误删除 |
| 学校导入/刷新 | data/jw、data/parser、ui/screen/imports、assets适配脚本 | JwRefresh比较完整安排和本地指纹，保护本地编辑；刷新课程不覆盖作息；真实账号不落库/日志/fixture |
| 课表呈现 | CourseTableView、ConflictCard、CourseColorUtil、ScheduleViewModel | 原始色与显示色分离；课程身份与颜色身份不同；灰显不是停课 |
| 学期安排 | SemesterSchedule、SemesterScreen | 后台投影，不改源数据；调休目标可跨学期边界；相同名字不代表同一课程 |
| 外观与交互 | ui/theme、ui/component、glasense-ui | 复用圆角/按压/动效；尊重减少动画；性能证据与视觉验收分开 |
| 桌面组件 | WidgetVariantInfo、WidgetUpdater、CourseRowPusher、WeekGridRowService | 实际宿主尺寸决定布局；设置变化刷新所有实例；请求固定成功不等于厂商桌面实际添加成功 |
| 提醒与厂商卡片 | widget/notification | 普通通知、持续卡片、厂商岛分别验收；权限、省电与重启边界不能混为一项 |
| 更新发布 | UpdateNotifier、UpdateManager、AppIdentity、docs/releases | Debug不获取生产更新；签名保持一致，ABI资产名和版本信息匹配；用户新授权才发布 |

## 构建与测试

JDK21、Android SDK使用本机工具目录。签名配置仅在忽略的local.properties中；不要复制到文档或提交。独立测试包与正式包数据隔离，不清除用户数据库/组件绑定。

```powershell
$env:JAVA_HOME='E:/T1/tools/jdk21'
$env:ANDROID_HOME='E:/T1/tools/android-sdk'
$env:GRADLE_USER_HOME='E:/T1/tools/gradle-home'
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest lintDebug
```

- 首先跑对应纯规则测试，再跑受影响的Compose/设备测试；不要用源码字符串断言代替业务结果测试。
- 仪器测试优先隔离模拟器；只用虚构fixture。真实学校数据与截图不得提交。安装使用 `adb -s <serial> install -r <apk>`，不卸载或清数据。
- 课程读取样板：明暗、1.0/1.5字号、缺少副信息和长文本。彩蛋覆盖连续点击/拖动取消/关闭动画/离页。学期安排核对每个实际日期而不只核对周次数量。
- root lintDebug会检查app与glasense-ui；只跑app lint不能代表全部模块通过。警告必须按来源判断，不全局禁用。

## 上游更新保留与吸收

来源项目为lingion/sleepy；UI包含glasense相关代码，其许可和署名仍保留。当前没有验证可直接整体合入的上游提交基线；查Git历史与差异后再决定，不能把版本名称当作共同祖先。

保留 data/jw 的学校适配器、导入格式兼容、数据库迁移、既有Manifest组件身份和小组件尺寸兼容实现。即使当前入口不曝光，也可能服务历史安装、外部Intent或导入数据，不能仅靠调用次数删除。

吸收流程：定位上游具体修复→核对许可/本地差异→提取学校或规则层最小变化→跑学校fixture/JS契约/数据迁移测试→独立测试包验证。不要把上游发布地址、包名、签名或版本覆盖本地身份。冲突先与用户讨论。

已删除的SemesterOverviewCard只剩专属测试引用、无Manifest/反射/序列化入口，产品已恢复静态统计，因此连同该废弃行为测试删除；历史实现可从Git取回。

## 已知问题与审计状态

本指南与目录清单是接手入口，不是全项目逐行审计完成证明。详见 CODE_AUDIT_20261014.md。未关闭性能/厂商集成/TalkBack问题见当前总计划，不能用全绿构建替代真机验收。
