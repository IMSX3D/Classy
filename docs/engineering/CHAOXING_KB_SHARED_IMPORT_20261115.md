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
| `ui/screen/imports/JwChaoxingKbFetchJs.kt` | 逐周 fetch + 节次配置归一 + `{ok,data,periods,startDate}` 桥回传 |
| `ui/screen/imports/JwWebViewLoginScreen.kt` | 抓取按钮分支 + 分享页专用底栏提示文案 |
| `data/jw/JwParserRegistry.kt` | type → `JwChaoxingKbParser`，优先级 46 |
| `data/jw/JwChaoxingKbParser.kt` | 逐周行按排课键合并周次并压成周次段 |

合并键 = (课名, 星期, 起止节, 教室, 教师)；同一键的周次取并集后：

- 单个连续段 → `type=0`（每周）；
- 整体等差 step=2 → 奇数周 `type=1` / 偶数周 `type=2`；
- 其余（如节假周缺课）拆多个 `type=0` 连续段。

口径与既有 `JwChaoxingParser` / `JwWiseduParser` 的周次压缩一致。

## 测试

- `JwChaoxingKbParserTest`：fixture 为实采结构脱敏数据（课名/教师/教室替换，
  字段名与周次形态保持原样）——逐周行合并、单双周、跨段周次、行内缺 `weeks`
  回退周桶、超 `maxWeek` 过滤、早期节次偏移、重复行去重、空/坏 JSON。
- `JwChaoxingKbUrlDetectionTest`：PC/移动/镜像子路径/大写 URL 正例；
  无 uuid、非课表路径、`jw.chaoxing.com/admin/login` 反例。
- `JwChaoxingKbWebViewContractTest`：锁抓取分支与跨语言 invariant —
  JS 只做 fetch + 传输层归一，课程字段解码唯一落点在 Kotlin parser。
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
