# PERF-01：设置导航复测与候选撤回

最新结论见[有边界的最终验证](PERFORMANCE_BOUNDED_20261013.md)：2026-10-13专项未得到可交付修复，候选已撤回，本轮结束，不自动延长。以下为历史证据，不作为下一轮自动调度指令。

本轮结论：定位取得进展，尚未证明流畅度改善。只保留 benchmark 定位标记与复测工具；生产布局和动画不变。W2及批次1未完成。

## 条件与范围

- 应用基线40126e3（代码abaa796）；vivo、API36、1080×2400、480dpi、字号1.0、动画1.0、中文、独立benchmark空数据库。正式包未覆盖，未操作用户课表。
- 开始时USB供电、电量94%、电池31.8°C、thermal status 0。逐组温度与实际动态刷新率没有连续记录，不能推断固定帧预算或排除所有系统噪声。
- 路径：我的→设置；每组四次“课表显示→系统返回→通用→系统返回”，动作后等待0.7s；第0组预热，报告1–3组。只在计时前抓一次节点位置，计时中无录屏/截图。
- 设置链使用空数据；随后通过文本导入独立虚构课表补测四主标签及课程详情/编辑。背景开启、真实平板仍待独立采样。

## 三组结果

Janky为gfxinfo当前指标，未混入legacy；P95/P99单位ms。聚合数不能替代逐帧根因或视觉验收。

| 样本 | 第1组：总帧/超时/P95/P99 | 第2组 | 第3组 |
| --- | --- | --- | --- |
| 原始基线 | 924/16/16/24 | 920/16/14/24 | 928/16/12/24 |
| 带测量标记基线 | 926/16/12/23 | 928/16/12/22 | 926/15/13/22 |
| 分段行单次布局候选 | 924/16/13/20 | 924/16/14/23 | 920/16/16/22 |
| 撤回后恢复复核 | 920/17/14/27 | 926/16/13/23 | 928/15/12/21 |

候选超时率1.73–1.74%，未优于带标记基线1.62–1.73%；不能把单组P99较低当作稳定收益。

## 逐帧证据与决策

基线trace中最慢应用超时帧26.769ms：Compose measure约14.378ms，其中课表显示列表12.698ms，recomposer约5.062ms。列表测量包含多项子组合与文字布局。标记层层嵌套，不能相加计算总时间，也没有证据说明某个小控件是唯一根因。

候选将无副标题SettingsSegmentedRow的BoxWithConstraints/标题预测替换为一次实际测量后选择横排或上下排。单个分段行最大测量从1.954降至0.859ms，但课表显示列表最大测量从12.763变为13.922ms；整链超时未减少。因此完整撤回候选，保留既有窄屏/大字体布局规则。此前SettingsScaffold简化同样已撤回，不再重复当作已采用优化。

保留工具：MeasureTrace.kt（仅benchmark增加布局节点）、SettingsCards中的业务测量标记、settings-navigation.py与SQL。最终恢复版本assembleBenchmark成功，独立测试包已重新安装；仓库脚本实跑四组成功，恢复后波动1.62–1.85%，也不能宣称改善。恢复组legacy计数72/106/70（7.83/11.45/7.54%）明显高于当前指标，保留原始输出，不混算或隐去；该指标差异仍需进一步分析。采样结束后检查课表显示截图，当前配置文字/分段控件布局正常；未将静态截图当成转场验收。

## 证据索引与接续

原始证据位于工作区外于Git管理的`E:/C015/99-整理记录/mainline-navigation/`：

- resume-environment.json；resume-baseline-{0..3}.txt。
- resume-traced-baseline-{0..3}.txt；resume-segment-candidate-{0..3}.txt。
- resume-profile/updated.perfetto-trace及attribution、missed、detail查询和结果。
- resume-candidate/updated.perfetto-trace及attribution.csv；两份trace互不覆盖。
- resume-restored/group-{0..3}.txt为恢复后的同路径复核。

原始干净基线APK在`E:/C015/99-整理记录/mainline-resume-baseline.apk`，SHA256为67a6b2641ea03bc739dab1016e4864c4511b953533631677bf8dcb7ecc9272bd。最终构建日志mainline-measure-restored-build.log。原始设备证据不进Git。

## 主标签与课程链追加采样

恢复后的同一benchmark包，深色、背景关闭，导入虚构Performance课表（2026-08-31起、20周、12节/天）；Math在周二1–2节、Physics在周三3–4节，均第1–16周，当前第7周。课程/教室/教师均虚构。未修改正式包。

| 链路 | 第1组：总帧/超时/P95/P99 | 第2组 | 第3组 |
| --- | --- | --- | --- |
| 课表→今日→管理→我的，四轮 | 706/16/15/30 | 714/16/17/28 | 704/16/13/26 |
| Math详情→编辑这节课→系统返回，四轮 | 1436/16/16/40 | 1426/16/14/42 | 1438/16/15/48 |

各有独立预热组，使用预先确认的点击坐标，动作间0.7s，计时中无截图/录屏。不同链路帧数不同，不能直接拿超时百分比互相比流畅；课程链P99更长，需继续逐帧定位。此次只测无修改返回，不覆盖保存/放弃/键盘/非首时段等状态。采样前进入编辑已定位目标时段，返回课表正确。

脚本：证据目录上一层的mainline-tabs.py、mainline-course.py、mainline-tabs-course-trace.py；输出resume-tabs、resume-course及resume-tabs-course-profile-64mb。最初32MiB/45s跟踪（resume-tabs-course-profile）丢失开头进程映射，SQL空结果不能解释为没有卡顿；扩大64MiB重录，配置同步到仓库。

有效跟踪中编辑页测量最大10.416ms、编辑列表9.405ms。另一最慢样本的FrameTimeline显示64.592ms；对应主线程Recomposer约35.956ms，其中onRemembered约24.108ms，随后新type=2窗口首次relayout约8.253ms。结合课程详情ModalBottomSheet使用独立窗口，这是弹层首次窗口建立附近的调查线索，尚无调用栈证据将24ms全部归给具体库函数或Binder；不能断言编辑表单是唯一根因。该帧的另一窗口/Surface也有记录，不能把FrameTimeline逐行数量直接当gfxinfo超时帧数量。

归因继续分三路：设置列表首次子组合/文字测量；详情弹层窗口创建与系统等待；编辑页加载完成后的首次布局。保留库提供的返回、无障碍和窗口语义，不为了绕过开销直接改成自制弹窗。

最终benchmark APK SHA256：DF7002FA92080C1B59762B4AC1700200DD4E856A59D13B9901A83E9D6328945C。三个拆分后的SQL均在有效trace实跑成功；旧多结果SQL在trace_processor_shell不兼容，已拆分，未把失败的空输出作为性能结论。

下一步仍是批次1：课程链长帧及设置列表首次组合/文字测量与调度时间继续归因，评估有证据的改动；补背景代表样本。任何候选都先对照再推广，不改短动画掩盖慢帧，不拿本轮有限样本宣称全应用通过。


## 2026-10-13追加：窗口等待与背景对照

本轮没有宣称性能修复。沿原课程链trace将onRemembered/onForgotten按线程状态拆分：19.31–24.11ms的样本中，14.50–18.32ms处于睡眠等待，实际Running约4.27–5.91ms。不能把这整段都归到Compose布局。

新增binder_driver追踪（window-binder-20261013/navigation.perfetto-trace）显示进入弹层的同步Binder调用约9.94–16.02ms，退出约12.61–13.51ms。16.02ms样本发送至system_server的binder:2082_14，服务端reply15.11ms，包含wmUpdateFocus5.78ms、setInputFocusLw及其他Binder。已确认窗口/焦点管理链存在等待；没有具体Java调用栈证明可由某一业务函数替换消除。继续保留标准ModalBottomSheet的返回、无障碍与窗口隔离，不用临时自制弹层绕过系统语义。代码层仍需后续比较受支持的窗口方案或系统差异。

同一近发布benchmark包、虚构Performance课表、vivo普通字号，生成1080×1600棋盘图导入独立测试包，使用默认背景参数。先背景开、后移除，各预热一组并记录三组，不含录屏。非交错顺序、未逐组记录温度/刷新率，结果仅代表此样本，不外推所有照片/高模糊参数。

| 主标签链 | 第1组 总帧/超时/P95/P99 | 第2组 | 第3组 |
| --- | --- | --- | --- |
| 背景开启 | 712/16/17/28 | 708/16/16/29 | 708/17/15/28 |
| 背景关闭 | 710/16/15/27 | 706/16/16/28 | 712/17/16/29 |

此样本没有明显背景增量开销，整体约2.25–2.40%超时仍存在，不算流畅度通过。Legacy指标原样保留在原始txt，未与当前Janky指标混算。默认遮罩下棋盘背景/标题/课程可读，窄列长课程仍按省略显示，可进入详情阅读。结束已移除benchmark背景，正式用户背景未更改。

证据均在E:/C015/99-整理记录：mainline-window-analysis/states、mainline-window-binder-analysis、mainline-binder-target/server的SQL与txt；mainline-navigation/background-tabs-20261013和no-background-tabs-20261013；mainline-background-grid截图。分析SQL针对具体slice ID不可跨trace直接复用，已保留对应trace。仓库采集配置补binder_driver。
