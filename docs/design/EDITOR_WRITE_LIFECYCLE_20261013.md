# 保存/删除生命周期复核（2026-10-13）

承接8a0f8b6。主目标是保存/删除中的配置重建与进程终止，不恢复性能专项。

## 决策与实现

- COURSE-WRITE-07：保存和删除原来由rememberCoroutineScope执行，旋转会取消页面作用域，忙时/错误/成功返回状态均可能丢失。新增CourseEditorOperations，以编辑会话UUID作为Activity ViewModel键；操作只持有提交快照、应用Context及结果，不持有Activity/导航回调。页面重建后消费结果并返回原入口。保存前复制临时作息修改列表，避免异步操作读取后来变化的表单。
- COURSE-WRITE-08：提交成功后的终态禁止再次保存/删除，操作结果只消费一次；迟到的恢复查询不能再次发出成功事件。冲突结果不进入完成态，页面保留需用户确认的明细，仍可返回修改或明确继续保存。
- COURSE-CREATE-09：每个新建表单保存一个creationGroupId。数据库事务先查该组是否已经创建，已存在则返回原课表ID、不再插入或覆盖恢复后的旧草稿；无课表时也不会重复建表。恢复表单可只读识别此前已提交的新建操作。直接使用现有课程groupId持久化，无数据库版本升级。此标记只防同一次新建的重放，不阻止用户另开表单有意创建同名课程。
- COURSE-RESTORE-10：已有课程的编辑会话在进程终止后仍按原规则返回主界面重新读取数据；明确禁止这段恢复期间把editingCourse=null画成新建表单。没有新增全应用草稿自动存盘。
- 错误提示由当前UI语言资源渲染；补齐values-zh-rCN中旧删除确认文案覆盖。离场终态禁止保存/删除/改组色再次触发。

## 自动化证据

日志在E:/C015/99-整理记录。

- 初版测试探针awaitCancellation使Kotlin推导Nothing，已显式声明Unit后编译通过（不是生产缺陷）。
- editor-lifecycle-final-build.log：Debug/AndroidTest/JVM构建通过；初轮editor-lifecycle-instrumentation.log 12项通过。
- editor-lifecycle-final-instrumentation.log：14项通过，含真实ActivityScenario.recreate（非StateRestorationTester）下保存等待、删除失败/重试、状态保留、重复提交抑制、完成后不重复返回；已有真实AddCourseScreen编辑/放弃/保存、组色恢复、删除和数据库回归。
- Activity重建测试使用Debug专属、不导出的EditorLifecycleTestActivity，承载真实CourseEditorOperations及删除确认组件。它不是完整生产导航UI，不能单凭该测试宣称全页面旋转均已通过。
- 外部进程终止探针：editor-process-probe.py以明确的数据库阶段标记同步，停止com.imsx3d.classy.debug后再启动新进程验证。Room使用独立editor-process-probe.db，不碰应用数据库。
  - 未提交事务：先删除课程并改表名，事务尚未返回时终止；重启后原课程/表名仍在，验证回滚。
  - 已提交新建：创建课程和课表后终止，重启重放同一个creationGroupId，仍仅一表一课；不存在重复课程/课表。
  - prepare阶段日志的Process crashed是测试主动终止；verifyRollback/verifyCommittedReplay各OK(1 test)。数据库和阶段标记在验证结束后清理。

## 平板直接验证与限制

PA2553 / Android16 / 中文深色 / font1.0，独立Debug。正式包、benchmark及桌面组件未改。

- 平板仪器测试停在第一个用例，系统焦点仍为桌面，测试Activity没有正常打开。主动force-stop停止并改用真实UI，不把该日志归类为产品崩溃，也不标仪器测试通过。
- 新建Lifecycle-Fixture及Process-Draft，用来验证真实表单保存。首轮am kill未终止进程（pid仍在）；该轮只能说明前后台保留，不能作进程恢复证据。
- 首次SIGKILL发生在Home后很短时间，恢复到主界面，未保存草稿没有恢复。随后严格核实MainActivity state=STOPPED、mHaveState=true，再由应用UID终止自身进程（旧PID15905消失），通过MAIN/LAUNCHER重开，Stopped-Draft新建草稿恢复（lifecycle-stopped-process-restored）。区别保留：系统还没保存状态时突然强杀，不承诺未保存内容可恢复；不把系统快照等同于持久自动草稿。
- 恢复后保存触发与Process-Draft的冲突；确认框旋转到竖屏仍保留，选择仍然保存后课程列表各一条（lifecycle-conflict-before/after-rotation、lifecycle-restored-course-saved）。
- Process-Draft详情→编辑→删除确认→旋转→删除成功，回到课表只剩Stopped-Draft（lifecycle-delete-confirm/after-rotation/completed）。这是真实确认框旋转与成功链，不等同于平板上数据库延迟写入中旋转；后者由模拟器受控测试覆盖。

## 完成边界

本批覆盖保存/删除的会话保持、成功结果消费、事务终止和新建重放。未宣称磁盘损坏、断电、任意OEM终止策略、任意时刻未保存草稿均可恢复；也未扩大成全部页面适配或性能通过。之后按总计划继续其它入口，不重复开展本批已通过样本。


## 最终候选与交付

- editor-lifecycle-final-delivery-build.log：Debug/AndroidTest/Release构建成功，2339项JVM零失败/零跳过。最终限定改动为完成状态的按钮守卫和简体中文删除确认；后续针对实际受影响流程做复测。
- 平板覆盖安装最终Debug，确认删除文字为“删除课程「Stopped-Draft」及其上课安排？”，删除成功；Lifecycle-Fixture虚构表及全部课程已清理（lifecycle-final-delete-copy、lifecycle-fixture-cleaned）。旋转恢复auto=1/user_rotation=1，字体/主题不变，原benchmark桌面组件未动。
- Release ARM64 APK：E:/C015/交付/Classy-保存删除状态保护-20261013/Classy-editor-lifecycle-arm64.apk。
- SHA256：E228D94EAFD6B7082D9BE157E987CAE1664F4CBEA766DA2485CEFA5FF778F687。
- 仅本地提交，未推送、不改现有tag/Release。正式包未安装。Debug测试宿主不导出，Release不包含。

- 最终候选editor-lifecycle-release-candidate-check.log共5项通过（重建2、实际编辑1、删除2）；此前14项完整回归及2项进程终止验证分批计数，不相加冒称去重覆盖。模拟器保持原540×960/240dpi/English1.5，无系统设置修改。
