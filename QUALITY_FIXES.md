# 2026-10-06 质量修复

本批次对应 `5cd1bbf` 上的项目评估与缺陷审查。

## 行为约定

- 无课表时手动加课，在同一事务中创建课表、填写真实 `tableId`、保存课程及特殊时段。连续点击保存只提交一次；失败显示错误。
- 调休为“源日期的课程移动到目标日期”：源日停课，目标日替换其原有课程。取课使用源日期的周次和单双周；映射不递归串联。课表、今日页、组件、通知和 ICS 导出使用相同规则。
- 一个业务动作一次 `ScheduleRepository.atomicEdit`，多个仓库调用加入同一协程的 Room 事务。成功提交后才更新撤回快照并刷新；失败或取消不会替换原撤回记录。复制、导入和课程/槽位保存适用同一边界。
- 删除课表的撤回及重做包含调休配置；配置变化会刷新界面。撤回仍是进程内的单级历史，并未改成跨进程备份系统。
- 闹钟重排共用互斥锁，已排课程 ID 和调度版本持久记录；旧课程、旧版本、已改变的上课时间在通知接收时被拒绝。关闭提醒或重排时也清理已显示的课前通知/服务。
- 广播中的短任务使用 `goAsync` 并在结束时释放；课前提醒接收器不对外导出。截图预览 Activity 只编入 debug，强制流体云参数仅 debug 接受。
- 文件导入按实际读取字节限制为 2 MiB，读取在后台进行。外部导入文本持久保存为草稿，Intent 只传编号；进程重建只恢复预览，确认后才写课表。失败明确显示原因，用户可取消草稿。
- “发现新版本”与“可以下载”分开。没有目标 ABI 资产或有效 HTTPS 下载地址时，只提供发布页入口，下载层也拒绝无效地址。
- release 包必须有正式签名，不再回退 debug 证书。配置 `classy.storeFile`、`classy.storePassword`、`classy.keyAlias`、`classy.keyPassword`，通过 Gradle 属性或未跟踪的 `local.properties` 提供。开发验证使用 debug。
- Room 版本仍为 9，实体结构未修改；启用 schema 导出并保存版本 9 JSON，供未来迁移比较。

## 验证入口

```text
gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug
gradlew :app:connectedDebugAndroidTest
```

Windows 应从纯英文路径构建；本次使用 `E:/C015/classy-build` 指向原工程的目录联接，源码只有一份。JDK/SDK 路径通过命令环境提供，不修改全局配置。

`QualityRegressionTest` 验证调休、单双周、学期边界、ICS、限量读取、缺包更新和调休快照。`RepositoryQualityTest` 使用真实内存 Room 数据库验证首次保存、外键异常回滚、取消回滚、复合撤回/重做和调休恢复，需要 Android 设备。

通知实际到达、厂商后台限制、慢内容提供器及进程重建仍需设备验收；编译通过不能代替这些场景的运行结果。

本次最终验证：2,198 项 JVM 测试全部通过；5 项 Room 设备测试编译通过但未运行；三个架构 debug APK 构建成功；release Kotlin 编译及合并清单检查通过；lintDebug 为 0 错误、713 警告、24 提示。未配置正式签名时，verifyReleaseSigning 按预期拒绝打包。完整日志保存在工作区 `05-APP质量审查/gradle-final-verification.txt`。
