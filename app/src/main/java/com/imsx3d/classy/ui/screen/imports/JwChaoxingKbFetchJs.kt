package com.imsx3d.classy.ui.screen.imports

/**
 * 超星课表分享页 (kb.chaoxing.com/res/pc/curriculum/schedule.html?curriculumUuid=…) 课表抓取。
 *
 * 分享页本身是公开页面 (visitor 模式, 无需登录): 页面脚本用
 * `$.getJSON(mctx + '/curriculum/getOtherLessons', {curriculumUuid, week?, curTime})`
 * 拉取课表, 不带 week 时只返回当前教学周 — 逐周翻页看的就是这条接口。
 * 因此本 JS 必须按 maxWeek 并行抓 1..maxWeek 周再合并, 只抓当前周会丢掉
 * 其它周才出现的课 (与青果新版 / 博雅平台逐周方案同构)。
 *
 * 流程 (复用 __sleepyBridge.onWiseduResult 同一回调通道):
 *   1) 从 location.search 取 curriculumUuid (分享链接唯一参数); 取不到 → 明确报错
 *   2) mctx 按页面同款规则从 pathname 截 '/res' 前缀 (兼容镜像部署的子路径,
 *      禁硬编码空前缀), GET getOtherLessons 先拿 curriculum (maxWeek/节次配置/开学日)
 *   3) 并行 GET 1..maxWeek 周, 组装 {"curriculum":{…},"weeks":[{week,rows},…]}
 *   4) 有效节次配置按分享页口径归一: campusLessonTimeConfigArray (多校区) 优先,
 *      其次 weeksLessonTimeConfigArray (按周), 最后 lessonTimeConfigArray;
 *      归一时同步写入 earlyMorningSection, 供 Kotlin 端计算早期节次偏移
 *   5) periods = 归一后节次时间 (node 从 1 起), startDate = firstWeekDateReal
 *      (第一周周一, ms → 本地 yyyy-MM-dd), 一起回传确认页预填
 *
 * 跨语言 invariant: JS 只做 fetch + 传输层归一 (选哪套节次配置/周桶归属),
 * 课程字段解码的唯一落点 = JwChaoxingKbParser (Kotlin)。JS 禁出现
 * name/teacherName/dayOfWeek/beginNumber 等课程字段的赋值或改名逻辑。
 */
const val CHAOXING_KB_FETCH_JS = """(function(){
  function post(obj){
    window.__sleepyBridge.onWiseduResult(JSON.stringify(obj));
  }
  function fail(msg){
    post({ok:false, err: msg});
  }
  function getJSON(params){
    var qs = [];
    for (var k in params) {
      if (Object.prototype.hasOwnProperty.call(params, k)) {
        qs.push(encodeURIComponent(k) + '=' + encodeURIComponent(params[k]));
      }
    }
    return fetch(api + '?' + qs.join('&'), {
      credentials: 'include',
      headers: {'X-Requested-With': 'XMLHttpRequest'}
    }).then(function(r){
      if (!r.ok) throw new Error('课表接口 HTTP ' + r.status);
      return r.json();
    });
  }
  try {
    var search = window.location.search || '';
    var m = search.match(/[?&]curriculumUuid=([^&]+)/);
    if (!m) {
      fail('未找到课表参数: 请先打开超星课表分享页, 再点导入');
      return;
    }
    var uuid = decodeURIComponent(m[1]);
    // 镜像部署的子路径前缀与页面脚本同款规则: pathname 截到 '/res' 之前
    var path = window.location.pathname || '';
    var resIdx = path.indexOf('/res');
    var mctx = resIdx > 0 ? path.substring(0, resIdx) : '';
    var api = mctx + '/curriculum/getOtherLessons';

    getJSON({curriculumUuid: uuid, curTime: Date.now()}).then(function(j){
      if (!j || j.result != 1 || !j.data || !j.data.curriculum) {
        fail((j && j.msg) ? String(j.msg) : '未获取到课表数据: 分享链接可能已失效');
        return;
      }
      var cur = j.data.curriculum;
      var maxWeek = parseInt(cur.maxWeek, 10);
      if (!(maxWeek > 0)) maxWeek = 20;
      if (maxWeek > 30) maxWeek = 30;

      // 用 IIFE 传周号, 保证每个桶的 week 不被 for-var 闭包串味
      var jobs = [];
      for (var w = 1; w <= maxWeek; w++) {
        jobs.push((function(week){
          return getJSON({curriculumUuid: uuid, week: week, curTime: Date.now()})
            .then(function(r){
              return {week: week, rows: (r && r.data && r.data.lessonArray) || []};
            })
            .catch(function(){
              return {week: week, rows: [], failed: true};
            });
        })(w));
      }

      return Promise.all(jobs).then(function(buckets){
        var good = [];
        for (var i = 0; i < buckets.length; i++) {
          if (!buckets[i].failed) good.push(buckets[i]);
        }
        if (good.length === 0) {
          fail('课表接口无响应: 请刷新分享页后再点导入');
          return;
        }

        // 有效节次配置 (分享页 opType 1/2/3 归一): 多校区 → 按周 → 统一配置
        var timeConfig = cur.lessonTimeConfigArray || [];
        var early = parseInt(cur.earlyMorningSection || 0, 10) || 0;
        var campus = cur.campusLessonTimeConfigArray || [];
        if (campus.length > 0) {
          var campus0 = campus[0] || {};
          if (campus0.timeConfig && campus0.timeConfig.length) {
            timeConfig = campus0.timeConfig;
            early = parseInt(campus0.earlyMorningSection || 0, 10) || 0;
          }
        } else {
          var weekly = cur.weeksLessonTimeConfigArray || [];
          if (weekly.length > 0) {
            var pick = null;
            var curWeek = String(cur.currentWeek == null ? '' : cur.currentWeek);
            for (var wi = 0; wi < weekly.length; wi++) {
              var weekList = String(weekly[wi].weeks || '').split(',');
              if (weekList.indexOf(curWeek) >= 0) { pick = weekly[wi]; break; }
            }
            if (!pick) pick = weekly[0];
            if (pick && pick.timeConfig && pick.timeConfig.length) {
              timeConfig = pick.timeConfig;
              early = parseInt(pick.earlyMorningSection || 0, 10) || 0;
            }
          }
        }

        var periods = [];
        for (var pi = 0; pi < timeConfig.length; pi++) {
          var seg = String(timeConfig[pi] || '').split('-');
          if (seg.length != 2) continue;
          periods.push({node: pi + 1, start: seg[0].trim(), end: seg[1].trim()});
        }

        var startDate = '';
        var ms = parseInt(cur.firstWeekDateReal || cur.firstWeekDate || 0, 10);
        if (ms > 0) {
          var d = new Date(ms);
          startDate = d.getFullYear() + '-' +
            ('0' + (d.getMonth() + 1)).slice(-2) + '-' +
            ('0' + d.getDate()).slice(-2);
        }

        post({
          ok: true,
          data: JSON.stringify({
            curriculum: {
              uuid: cur.uuid || uuid,
              schoolYear: cur.schoolYear,
              semester: cur.semester,
              maxWeek: cur.maxWeek,
              currentWeek: cur.currentWeek,
              maxLength: cur.maxLength,
              earlyMorningSection: early,
              lessonTimeConfigArray: timeConfig,
              firstWeekDateReal: cur.firstWeekDateReal || cur.firstWeekDate
            },
            weeks: good
          }),
          periods: periods,
          startDate: startDate
        });
      });
    }).catch(function(e){
      fail(String((e && e.message) || e));
    });
  } catch (err) {
    post({ok:false, err: String(err)});
  }
})()"""
