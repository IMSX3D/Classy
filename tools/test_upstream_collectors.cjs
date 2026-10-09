const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const dir = path.join(__dirname, '../app/src/main/java/com/imsx3d/classy/ui/screen/imports');
const web = fs.readFileSync(path.join(dir, 'JwWebViewLoginScreen.kt'), 'utf8');
function script(source, name) {
  const match = source.match(new RegExp('const val '+name+' = """([\\s\\S]*?)"""'));
  assert(match, name); return match[1];
}
const response = (data, status=200) => ({ok:status<300, status, json:async()=>data, text:async()=>typeof data==='string'?data:JSON.stringify(data)});
async function run(js, overrides) {
  const calls=[];
  let timer;
  try {
    const result=await new Promise((resolve,reject)=>{
      timer=setTimeout(()=>reject(new Error('collector did not call bridge')),1500);
      vm.runInNewContext(js, {
        document:{cookie:'',body:{innerText:''},querySelector:()=>null,querySelectorAll:()=>[]},
        location:{hostname:'yhxt.swjtu.edu.cn',pathname:'/jwapp/',search:''},
        localStorage:{getItem:()=>''},
        ...overrides,
        fetch:async(url,options)=>{calls.push({url,options});return overrides.fetch(url,options);},
        window:{__sleepyBridge:{onWiseduResult:s=>resolve(JSON.parse(s))}}
      },{timeout:1000});
    });
    return {result,calls};
  } finally {clearTimeout(timer);}
}
let passed=0;
async function test(name, fn) {await fn();passed++;console.log('PASS '+name);}
(async()=>{
  const hfut = web.slice(web.indexOf('    function extractSemesterId'), web.indexOf('    // 1) GET course-table',web.indexOf('    function extractSemesterId')));
  const extract=vm.runInNewContext(hfut+'; extractSemesterId');
  await test('HFUT selected term, both attribute orders',()=>{
    assert.equal(extract('<select id="allSemesters"><option value="1">old</option><option selected value="2">new</option></select>'), '2');
    assert.equal(extract('<select name="semesterId"><option value="3" selected="selected">new</option></select>'), '3');
  });
  await test('HFUT ignores unrelated selected values',()=>{
    assert.equal(extract('<select id="campus"><option selected value="99"></option></select><script>semesterId = 7;</script>'),'7');
    assert.equal(extract('<select id="campus"><option selected value="99"></option></select>'),'');
  });
  const yethan=script(web,'YETHAN_FETCH_JS');
  for(const [name,cookie,stored,want] of [['cookie priority','ytoken=cookie-token','storage-token','cookie-token'],['storage fallback','','storage-token','storage-token'],['HttpOnly cookie','','',undefined]]) {
    await test('SWJTU '+name,async()=>{
      const {result,calls}=await run(yethan,{
        document:{cookie,body:{innerText:''},querySelector:()=>null},localStorage:{getItem:()=>stored},
        fetch:async url=>response(url.includes('student-course')?{code:'00000',data:[{name:'course'}]}:{data:{}})
      });
      assert.equal(result.ok,true);assert.equal(calls.length,2);
      assert.equal(calls[0].options.headers.ytoken,want);assert.equal(calls[0].options.credentials,'include');
    });
  }
  await test('SWJTU config failure does not discard valid courses',async()=>{
    const {result}=await run(yethan,{fetch:async url=>url.includes('common-config')?response('error',500):response({code:'00000',data:[1]})});
    assert.equal(result.ok,true);assert.deepEqual(JSON.parse(result.data).data,[1]);
  });
  for(const code of ['401','A0230','A0422']) await test('SWJTU expired '+code,async()=>{
    const {result}=await run(yethan,{fetch:async()=>response({code})});
    assert.equal(result.ok,false);assert.match(result.err,/登录/);
  });
  await test('SWJTU QR guidance after server authentication error',async()=>{
    const {result}=await run(yethan,{document:{cookie:'',body:{innerText:'微信登录'},querySelector:()=>null},fetch:async()=>response({},401)});
    assert.equal(result.ok,false);assert.match(result.err,/微信扫码/);
  });
  const wisedu=script(web,'WISEDu_FETCH_JS'.toUpperCase());
  for(const [name,rows,term] of [
    ['SFSY flag',[{DM:'2025-2026-1'},{DM:'2026-2027-1',SFSY:'1'}],'2026-2027-1'],
    ['sole semester',[{DM:'2026-2027-2'}],'2026-2027-2'],
    ['ambiguous semesters',[{DM:'2025-2026-1'},{DM:'2026-2027-1'}],null]]) {
    await test('Wisedu '+name,async()=>{
      const {result,calls}=await run(wisedu,{fetch:async url=>response(url.includes('dqxnxq')?{datas:{dqxnxq:{rows}}}:{} )});
      assert.equal(result.ok,!!term);
      const course=calls.find(c=>c.url.includes('xskcb.do'));
      if(term) assert.equal(course.options.body,'XNXQDM='+term);else assert.equal(course,undefined);
    });
  }
  await test('Wisedu reports HTTP status and stops before course request',async()=>{
    const {result,calls}=await run(wisedu,{fetch:async()=>response('forbidden',403)});
    assert.equal(result.ok,false);assert.match(result.err,/403/);assert.equal(calls.length,1);
  });
  const zf=script(fs.readFileSync(path.join(dir,'JwZfNewFetchJs.kt'),'utf8'),'ZF_NEW_FETCH_JS');
  for(const [name,options,ok] of [
    ['explicit default',[['',false],['2025',false],['2026',true]],true],
    ['only valid option',[['',true],['2026',false]],true],
    ['no safe default',[['2025',false],['2026',false]],false]]) {
    await test('ZF '+name,async()=>{
      class DOMParser {parseFromString(){return {getElementById:id=>({options:(id==='xnm'?options:[['3',true]]).map(([value,selected])=>({value,hasAttribute:()=>selected}))})};}}
      const {result,calls}=await run(zf,{DOMParser,location:{pathname:'/jwglxt/kbcx/',search:''},fetch:async url=>response(url.includes('Index')?'<html/>':{kbList:[{kcmc:'数学'}]})});
      assert.equal(result.ok,ok);
      if(ok) assert.match(calls[1].options.body,/xnm=2026&xqm=3/);else assert.equal(calls.length,1);
    });
  }
  console.log(`${passed} collector behavior tests passed`);
})().catch(e=>{console.error(e);process.exitCode=1;});
