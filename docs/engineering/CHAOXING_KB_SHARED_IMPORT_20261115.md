# 超星课表分享页导入（chaoxing_kb）— 2026-11-15

## 需求

超星学习通自带「课表」页可以转发分享（`https://kb.chaoxing.com/res/pc/curriculum/schedule.html?curriculumUuid=…`）。
Classy 已有的 `chaoxing` 适配只覆盖超星**综合教务管理系统**（`jw.chaoxing.com/admin`、
`/pkgl/xskb/queryKbForGrdb`）的登录态个人课表，分享链接打不开、也没有任何学校条目能接住它：
在「自定义教务链接」里贴分享 URL 会被当成 `type=null` 的通用网页，走 HTML 抓取必 0 课。

本次新增通用协议 `chaoxing_kb`：**粘贴分享链接 → 打开分享页 → 点导入**，无需登录、不锁学校。

## 数据源与实采证据

分享页是公开 visitor 课表层（`data.visitor=2`），页面脚本同源请求：

```
GET {mctx}/curriculum/getOtherLessons?curriculumUuid={uuid}&curTime={ms}
GET {mctx}/curriculum/getOtherLessons?curriculumUuid={uuid}&week={N}&curTime={ms}
```

- `mctx` = `location.pathname` 截到 `/res` 之前的前缀（页面 `scheduleCommon.js` 同款规则），
  公网部署为 `''`，镜像部署可能是子路径；**禁止硬编码空前缀或 host**。
- **不带 `week` 只返回当前教学周**（`lessonArray` 行的 `weeks` 只有该周）；
  逐周翻页请求时按 `week=N` 返回第 N 周。因此必须并行抓 `1..maxWeek` 周合并，
  只抓当前周会丢掉其它周才出现的课（与 `cf_new` / `boya_pp` 逐周方案同构）。
- 2026-11-15 用真实分享链接直接 curl 实采（25 周，单周 0–15 行），全学期合并出
  29 种 (课名,星期,起止节,教室,教师) 排课，按周次段压成 47 条课程；第 5 周多门课
  在分享数据里缺失（国庆假周），解析端按实际周次拆段，不按 `weekType` 脑补。

响应结构（`data`）：

```
data.curriculum = {
  uuid, schoolYear, semester, maxWeek, currentWeek, maxLength,
  earlyMorningSection, lessonTimeConfigArray, firstWeekDateReal,
  campusLessonTimeConfigArray?, weeksLessonTimeConfigArray?
}
data.lessonArray = [{
  name, teacherName, location, onlineLocation,
  dayOfWeek(1..7, 周一=1), beginNumber(1 基), length, weeks("10" / "1,2,3"),
  lessonConfigUuid, conflictUuids, role, …
}]
```

- 节次时间取 `lessonTimeConfigArray`（`"8:00-8:50"` 形态）；多校区部署在
  `campusLessonTimeConfigArray[].timeConfig`，按周配置部署在
  `weeksLessonTimeConfigArray[].timeConfig`。fetch JS 按分享页自身口径归一
  （多校区 → 按周 → 统一配置），并把选中的 `earlyMorningSection` 一起回传。
- 早期节次：分享页按 `lessonTimeConfigArray[beginNumber + earlyMorningSection - 1]`
  取时间，因此解析端节号 = `beginNumber + earlyMorningSection`；仅当配置数组长度
  覆盖 `maxLength + earlyMorningSection` 时生效（与页面渲染口径一致，不硬编码）。
- 开学日：`firstWeekDateReal`（ms，第一周周一），换算为本地 `yyyy-MM-dd` 预填确认页。

无效/过期链接返回 `{"result":0,"msg":"未查到课表数据","data":null}`；fetch JS
直接把 `msg` 转成用户提示，不落伪「空学期」。

## 实现落点

| 位置 | 内容 |
| --- | --- |
| `data/jw/JwProtocol.kt` | `TYPE_CHAOXING_KB = "chaoxing_kb"`，displayName「超星课表分享」，category `chaoxing` |
| `data/jw/JwImportViewModel.kt` | URL 判型：`curriculumUuid=` + `/curriculum/schedule` → `chaoxing_kb`（无 uuid 的页面不判） |
| `ui/screen/imports/SchoolSelectScreen.kt` | 入口决策：URL 判型与目录条目协议冲突时不采用域名映射（见下节实机反馈） |
| `data/jw/SchoolDomainMatch.kt` | `matchSchoolForUrlEntry`：判型优先的目录映射决策（纯函数，可测） |
| `data/jw/JwImportDraft.kt` | 草稿 / 刷新基线解码：自定义条目（sortKey 空串）不再被拒 |
| `ui/screen/imports/JwChaoxingKbFetchJs.kt` | 逐周 fetch + 节次配置归一 + `{ok,data,periods,startDate}` 桥回传 |
| `ui/screen/imports/JwWebViewLoginScreen.kt` | 抓取按钮分支 + 分享页专用底栏提示文案 |
| `data/jw/JwParserRegistry.kt` | type → `JwChaoxingKbParser`，优先级 46 |
| `data/jw/JwChaoxingKbParser.kt` | 逐周行按排课键合并周次并压成周次段 |

合并键 = (课名, 星期, 起止节, 教室, 教师)；同一键的周次取并集后：

- 单个连续段 → `type=0`（每周）；
- 整体等差 step=2 → 奇数周 `type=1` / 偶数周 `type=2`；
- 其余（如节假周缺课）拆多个 `type=0` 连续段。

口径与既有 `JwChaoxingParser` / `JwWiseduParser` 的周次压缩一致。

## URL 入口的目录映射冲突（2026-11-15 实机反馈）

第一版实机把手机版分享链接粘进「自定义教务链接」时，URL 行显示「匹配学校：北京工业大学耿丹学院」而不是
「检测到：超星课表分享」，点进去打开的是该校 `chaoxing` 综合教务登录页 → 0 课。

根因不在判型，而在入口的域名映射：`SchoolDomainMatch.matchSchool` 按注册域匹配，`kb.chaoxing.com`
与目录里 `*.jw.chaoxing.com` 的学校条目同属 `chaoxing.com`，于是「同域学校映射」（issue #25 为统一
身份门户加的）覆盖了 URL 判型。

修复原则：**URL 判型优先于域名映射**。新增 `SchoolDomainMatch.matchSchoolForUrlEntry`：

- 有判型且与目录条目 `type` 不一致（含 typeless 条目）→ 不采用目录条目，
  用 URL 判型建自定义条目；
- 有判型且一致 → 仍采用目录条目（保留 issue #25 拿权威 URL 的行为）；
- 无判型（门户 / CAS 页）→ 维持原域名映射。

同时该入口按钮文案在 `chaoxing_kb` 下改成「用此链接直接导入」——分享页公开可读，不是登录。

## 节次时间补零（2026-11-15 第二次实机反馈）

修好入口后真机已能抓到 47 门课、开始日期 2026-09-07、12 节/天，但确认页报
「第 1 节时间无效（开始须早于结束）」。

根因：分享页 `lessonTimeConfigArray` 是不补零的 `8:00-8:50`，而确认页校验要求
`^\d{2}:\d{2}$` 且用字符串比较（`JwImportActivity` 的 confirm 校验）。桥回传的
periods 原样透传 `8:00` → 校验失败。

修复：`JwWebViewLoginScreen.normalizePeriodClock` 在 JS 桥解析 periods 时统一补零成
`HH:mm`（所有 fetch 协议共用该归一；只补零/去空格，不猜缺失分钟，非法值原样留给
确认页报错）。

## 「从教务更新」失败（2026-11-15 第三次实机反馈）

导入并确认成功后（导出文本核对：47 条课程、开始日 2026-09-07、20 周、12 节），
点「从教务更新」的差异复核页报「未更新…」，没有任何增减列表。

根因：自定义链接入口建出的 `JwSchoolInfo` `sortKey` 恒为空串（不是目录学校），而
`JwImportDraftCodec.schoolFromJson` 把 `sortKey` 当必填字段校验 → 导入时写入的
`jwSourceJson` 刷新基线解码失败 → `JwRefresh.plan` 的 `require(source != null)`
抛异常。副作用：这类导入的**导入草稿恢复**也会失败（同款解码）。

修复：`schoolFromJson` 只要求 `name` 非空，`sortKey` 仅用于学校列表分组、不参与来源
身份校验（空 sk 的自定义条目现在能正常写/读草稿与刷新基线）。

复核页的来源确认文案也会显示 `JwProtocol.sourceDisplayName(school)`：自定义链接条目的
`name` 是通用占位「自定义教务」，优先显示协议名（如「超星课表分享」）；学校目录条目
仍显示学校名。

## 测试

- `JwChaoxingKbParserTest`：fixture 为实采结构脱敏数据（课名/教师/教室替换，
  字段名与周次形态保持原样）——逐周行合并、单双周、跨段周次、行内缺 `weeks`
  回退周桶、超 `maxWeek` 过滤、早期节次偏移、重复行去重、空/坏 JSON。
- `JwChaoxingKbUrlDetectionTest`：PC/移动/镜像子路径/大写 URL 正例；
  无 uuid、非课表路径、`jw.chaoxing.com/admin/login` 反例；以及入口域名映射冲突
  （裸映射确实命中 `*.jw.chaoxing.com` 学校条目、组合决策必须保留判型）与
  issue #25 行为不回退（门户 / 同协议 URL 仍映射目录条目）。
- `JwChaoxingKbWebViewContractTest`：锁抓取分支与跨语言 invariant —
  JS 只做 fetch + 传输层归一，课程字段解码唯一落点在 Kotlin parser。
- `JwPeriodClockNormalizationTest`：`8:00` → `08:00` 补零、已补零不变、
  非法/越界值原样返回（第二次实机反馈）。
- `JwRefreshCustomUrlSchoolTest`：自定义链接（sortKey 空串）来源的草稿与
  `jwSourceJson` 刷新基线都能 round-trip，`JwRefresh.plan` 不再抛 `source`；
  空校名仍被拒绝（第三次实机反馈）。
- 既有 `JwParserRegistryTest` / `JwYethanParserTest` 的 `ALL_TYPES` 计数闸门 36 → 37。

## 已知边界

- 多校区课表默认取 `campusLessonTimeConfigArray` 第一套（与分享页未选过校区时的默认一致）；
  用户在分享页切过校区后，链接里通常带 `currentCampusId`，但接口返回的教室行已是所选
  校区数据，节次时间仍可能按第一套配置展示 — 确认页可手动改作息。
- 分享链接失效/被取消分享时给出接口原文提示，不猜测数据。
- 只支持「课表分享页」链接（`…/curriculum/schedule.html?curriculumUuid=…`）；
  超星综合教务登录页仍走既有 `chaoxing` 适配。

## 参考来源

接口与字段全部来自分享页自身前端脚本（`schedule.pc.js` / `scheduleCommon.js` /
`mirrorDomain.js`）与真实链接接口实采，未复用第三方仓库代码；因此无需新增开源许可致谢。
