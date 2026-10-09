const assert=require('assert'); let result; let labels=[]; const document={querySelectorAll:()=>labels.map(textContent=>({textContent}))}; const window={__sleepyBridge:{onWiseduResult:s=>result=JSON.parse(s)}};
const fs=require('node:fs'); const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'app/src/main/java/com/imsx3d/classy/ui/screen/imports/JwZfNewFetchJs.kt'),'utf8');
const begin=source.indexOf('  function ok('); const finish=source.indexOf('  try {',begin);
assert(begin>=0 && finish>begin, 'Cannot locate the actual ZF collector');
const ok=new Function('window','document',source.slice(begin,finish)+'; return ok;')(window,document);
labels=['第1节（8:00-8:45）','2\n08:55～09:40','第3节 25:00-26:00','1-2节 08:00-09:40','课程 3 10:00-11:00']; ok('{}','','',false); assert.deepStrictEqual(result.periods,[{node:1,start:'08:00',end:'08:45'},{node:2,start:'08:55',end:'09:40'}]); labels=['1 08:00-08:45','1 09:00-09:45']; ok('{}','','',false); assert.equal(result.periods.length,0); labels=[];ok('{}','','',false);assert.equal(result.periods.length,0);console.log('ZF explicit row-time extraction: 3 scenarios passed');