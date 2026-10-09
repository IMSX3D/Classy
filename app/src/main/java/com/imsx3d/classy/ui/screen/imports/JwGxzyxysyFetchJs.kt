package com.imsx3d.classy.ui.screen.imports

import org.json.JSONArray
import org.json.JSONObject

/** Periods supplied by the tester on 2026-10-08; editable in the normal import preview. */
internal fun gxzyxysyFetchJs(eveningStart: Int, eveningEnd: Int): String {
    require(eveningStart in 1..30 && eveningEnd in eveningStart..30)
    val times = listOf("08:30-09:10", "09:15-09:55", "10:00-10:40", "10:50-11:30", "11:35-12:15",
        "14:20-15:00", "15:05-15:45", "15:55-16:35", "16:40-17:20", "18:30-19:10",
        "19:15-19:55", "20:00-20:40", "20:45-21:25")
    val periods = JSONArray(times.mapIndexed { i, time ->
        JSONObject().put("node", i + 1).put("start", time.substringBefore('-')).put("end", time.substringAfter('-'))
    })
    return GXZYXYSY_FETCH_JS.replace("__EVENING_START__", eveningStart.toString())
        .replace("__EVENING_END__", eveningEnd.toString()).replace("__PERIODS__", periods.toString())
}

internal const val GXZYXYSY_FETCH_JS = """
(function(){
  if(window.__classySainzFetching) return;
  window.__classySainzFetching=true;
  var done=false, controller=new AbortController();
  function finish(value){
    if(done)return;done=true;clearTimeout(timer);controller.abort();
    window.__classySainzFetching=false;
    window.__sleepyBridge.onWiseduResult(JSON.stringify(value));
  }
  var timer=setTimeout(function(){finish({ok:false,err:'课表采集超时，请重新打开学生选课表后重试'});},40000);
  function find(w,depth){
    if(depth>6)return null;
    try{
      if(w.location.hostname==='jw.gxzyxysy.com' && w.document.querySelector('form[action="/student/stselect.asp"]'))return w;
      for(var i=0;i<w.frames.length;i++){var child=find(w.frames[i],depth+1);if(child)return child;}
    }catch(e){}
    return null;
  }
  function info(doc){
    var text=doc.body?doc.body.textContent:'', m=text.match(/找到\s*(\d+)\s*页[，,]\s*总共\s*(\d+)\s*条[，,]\s*当前页号[：:]\s*(\d+)/);
    var form=doc.querySelector('form[action="/student/stselect.asp"]');
    if(!m||!form||!doc.querySelector('th'))throw Error('未取得学生选课表，可能登录已过期');
    return {total:Number(m[1]),count:Number(m[2]),page:Number(m[3]),form:form,text:text};
  }
  function body(form,button){
    var parts=[];
    ['term','grade','spno'].forEach(function(name){
      var input=form.querySelector('input[name="'+name+'"]');
      if(!input || !/^\d+/.test(input.value) || /\D/.test(input.value))throw Error('教务查询参数已变化');
      parts.push(name+'='+encodeURIComponent(input.value));
    });
    // This legacy ASP form uses GB2312, including the submit button value.
    parts.push(button==='first'?'lwBtnfirst=%CA%D7%D2%B3':'lwBtnnext=%CF%C2%D2%B3');
    return parts.join('&');
  }
  (async function(){
    var frame=find(window,0);
    if(!frame)throw Error('请先进入学生选课表，再点击导入');
    var endpoint=new URL('/student/stselect.asp',frame.location.href);
    if(endpoint.protocol!=='https:' || endpoint.hostname!=='jw.gxzyxysy.com')throw Error('教务地址不匹配');
    async function get(form,button){
      var response=await frame.fetch(endpoint.href,{method:'POST',credentials:'same-origin',
        headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body(form,button),signal:controller.signal});
      if(!response.ok)throw Error('教务服务器暂时无法返回课表');
      var responseUrl=new URL(response.url);
      if(responseUrl.origin!==endpoint.origin || responseUrl.pathname!==endpoint.pathname)throw Error('登录已过期，请重新登录');
      var buffer=await response.arrayBuffer();
      if(buffer.byteLength>2097152)throw Error('课表响应过大');
      return new DOMParser().parseFromString(new TextDecoder('gb18030').decode(buffer),'text/html');
    }
    var current=info(frame.document);
    var doc=await get(current.form,'first');
    var first=info(doc), term=first.form.querySelector('input[name="term"]').value;
    if(first.page!==1 || first.total<1 || first.total>50)throw Error('无法返回课表第一页，请手动点首页再导入');
    var pages=[], bytes=0;
    for(var page=1;page<=first.total;page++){
      var state=info(doc);
      if(state.page!==page || state.total!==first.total || state.count!==first.count || state.form.querySelector('input[name="term"]').value!==term)
        throw Error('分页未完整返回，请重新打开课表后重试');
      var html=doc.documentElement.outerHTML;bytes+=html.length;
      if(bytes>2097152)throw Error('课表总量过大');
      pages.push(html);
      if(page<first.total)doc=await get(state.form,'next');
    }
    var date=first.text.match(/本学期从\s*(\d{4})\/(\d{1,2})\/(\d{1,2})开始/);
    var startDate=date?date[1]+'-'+('0'+date[2]).slice(-2)+'-'+('0'+date[3]).slice(-2):'';
    finish({ok:true,data:JSON.stringify({format:'gxzyxysy-v1',pages:pages,eveningStart:__EVENING_START__,eveningEnd:__EVENING_END__}),
      startDate:startDate,periods:__PERIODS__});
  })().catch(function(e){finish({ok:false,err:e.message||'课表采集失败'});});
})()
"""
