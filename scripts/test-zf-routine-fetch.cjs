// Execute the production WebView script. Only school-wide routine times are real;
// courses, DOM, sessions and HTTP responses are synthetic. No credentials or network.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const root = path.join(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'app/src/main/java/com/imsx3d/classy/ui/screen/imports/JwZfNewFetchJs.kt'), 'utf8').split('const val ZF_NEW_FETCH_JS = """')[1].split('"""')[0];
const routine = JSON.parse(fs.readFileSync(path.join(root, 'app/src/test/resources/zf-new/fzfu-routine.json'), 'utf8'));
async function run(options = {}) {
  let result, callbacks = 0;
  const calls = [];
  const courses = options.courses || [{xqh_id:'1', kcmc:'Test course', jcs:'9-11', zcd:'1-16', xqj:'1'}];
  const scope = {
    location:{pathname:'/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html',search:'?gnmkdm=N2151'},
    document:{querySelector: s => ({value:s === '#xnm' ? '2026':'3'}),
      querySelectorAll: () => (options.headings || []).map(textContent=>({textContent}))},
    window:{__sleepyBridge:{onWiseduResult:raw=>{result=JSON.parse(raw);callbacks++;}}},
    AbortController, setTimeout, clearTimeout,
    fetch: async (url, request) => {
      calls.push({url,request});
      if (url.includes('cxRjc')) {
        if (options.timeout) return new Promise((resolve,reject)=>request.signal.addEventListener('abort',()=>reject(Error('timeout'))));
        if (options.failed) throw Error('network failure');
        return {ok:!options.httpError, json:async()=>{
          if(options.expired) throw Error('login HTML');
          return options.routine === undefined ? routine : options.routine;
        }};
      }
      return {text:async()=>options.login ? '<form action="login_slogin"><input name="mm">csrftoken</form>':JSON.stringify({kbList:courses})};
    }
  };
  if (options.timeout) scope.setTimeout = fn => setTimeout(fn, 1);
  vm.runInNewContext(source, scope);
  for(let i=0;i<40 && !result;i++) await new Promise(resolve=>setTimeout(resolve,2));
  assert.ok(result, 'bridge must return'); assert.equal(callbacks,1);
  return {result,calls};
}
(async()=>{
  let {result,calls}=await run();
  assert.equal(result.ok,true);
  assert.equal(result.periods.length,11);
  assert.deepEqual(result.periods[0],{node:1,start:'08:25',end:'09:10'});
  assert.deepEqual(result.periods[10],{node:11,start:'20:10',end:'20:55'});
  assert.equal(JSON.parse(result.data).kbList.length,1);
  assert.equal(calls[1].request.body,'xnm=2026&xqm=3&xqh_id=1');
  assert.equal(calls[1].request.credentials,'include');
  for (const options of [{failed:true},{expired:true},{httpError:true},{timeout:true},{routine:{}}]) {
    ({result}=await run(options)); assert.equal(result.ok,true); assert.deepEqual(result.periods,[]);
  }
  ({result}=await run({failed:true,headings:['1 (08:25-09:10)']}));
  assert.equal(result.periods.length,1);
  ({result}=await run({headings:['1 (10:00-10:45)']}));
  assert.equal(result.periods[0].start,'08:25','endpoint must not mix with stale DOM');
  ({result}=await run({routine:[...routine,{jcmc:'1',qssj:'08:00',jssj:'08:45'}]}));
  assert.equal(result.periods.some(p=>p.node===1),false,'conflicting times must not be guessed');
  ({result}=await run({routine:[{jcmc:'1-2',qssj:'08:00',jssj:'09:40'},{jcmc:'3',qssj:'25:00',jssj:'26:00'},{jcmc:'4',qssj:'08:50',jssj:'08:00'}]}));
  assert.deepEqual(result.periods,[]);
  for(const courses of [[{xqh_id:'1'},{xqh_id:'2'}],[{}]]) {
    ({result,calls}=await run({courses}));assert.equal(calls.length,1);assert.deepEqual(result.periods,[]);
  }
  ({result,calls}=await run({courses:[]}));assert.equal(result.emptySemester,true);assert.equal(calls.length,1);
  ({result,calls}=await run({login:true}));assert.equal(result.kind,'SESSION_EXPIRED');assert.equal(calls.length,1);
  for(const n of [8,10,11,12,13]) {
    const rows=Array.from({length:n},(_,i)=>({jcmc:String(i+1),qssj:`${String(i+6).padStart(2,'0')}:00`,jssj:`${String(i+6).padStart(2,'0')}:45`}));
    ({result}=await run({routine:rows}));assert.equal(result.periods.length,n);
  }
  console.log('ZF routine fetch: all scenarios passed');
})().catch(e=>{console.error(e);process.exitCode=1;});
