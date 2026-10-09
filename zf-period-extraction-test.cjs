const assert=require('assert'); let result; let labels=[]; const document={querySelectorAll:()=>labels.map(textContent=>({textContent}))}; const window={__sleepyBridge:{onWiseduResult:s=>result=JSON.parse(s)}};
  function ok(data, xnm, xqm, empty){
    // Read only explicit single-period row headings; never infer a lesson time from course text.
    var periods = [], seen = {}, conflicts = {};
    var headings = document.querySelectorAll('tr > th, tr > td:first-child');
    for (var i=0; i<headings.length; i++) {
      var txt = (headings[i].innerText || headings[i].textContent || '').trim();
      var match = txt.match(/^(?:第\s*)?(\d{1,2})\s*(?:节)?\s*[（(\[]?\s*(\d{1,2})[:：](\d{2})\s*[-~～—至]\s*(\d{1,2})[:：](\d{2})\s*[）)\]]?$/);
      if (!match) continue;
      var node=+match[1], sh=+match[2], sm=+match[3], eh=+match[4], em=+match[5];
      if (node<1 || node>48 || sh>23 || eh>23 || sm>59 || em>59 || sh*60+sm>=eh*60+em) continue;
      function pad(n){return ('0'+n).slice(-2);}
      var period={node:node,start:pad(sh)+':'+pad(sm),end:pad(eh)+':'+pad(em)};
      if (seen[node] && (seen[node].start!==period.start || seen[node].end!==period.end)) conflicts[node]=true;
      seen[node]=period;
    }
    Object.keys(seen).forEach(function(n){if(!conflicts[n]) periods.push(seen[n]);});
    periods.sort(function(a,b){return a.node-b.node;});
    window.__sleepyBridge.onWiseduResult(JSON.stringify({
      ok:true, data:data, periods:periods, xnm:xnm, xqm:xqm, emptySemester:!!empty, format:'zf_new'
    }));
  }
labels=['第1节（8:00-8:45）','2\n08:55～09:40','第3节 25:00-26:00','1-2节 08:00-09:40','课程 3 10:00-11:00']; ok('{}','','',false); assert.deepStrictEqual(result.periods,[{node:1,start:'08:00',end:'08:45'},{node:2,start:'08:55',end:'09:40'}]); labels=['1 08:00-08:45','1 09:00-09:45']; ok('{}','','',false); assert.equal(result.periods.length,0); labels=[];ok('{}','','',false);assert.equal(result.periods.length,0);console.log('ZF explicit row-time extraction: 3 scenarios passed');