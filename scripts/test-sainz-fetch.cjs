// Execute the actual injected collector with an in-memory browser/session boundary.
// No school requests or credentials are used. DOM/HTTP responses are synthetic.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const file = path.join(__dirname, '../app/src/main/java/com/imsx3d/classy/ui/screen/imports/JwGxzyxysyFetchJs.kt');
const source = fs.readFileSync(file, 'utf8').split('internal const val GXZYXYSY_FETCH_JS = """')[1].split('"""')[0]
  .replaceAll('__EVENING_START__', '10').replaceAll('__EVENING_END__', '13').replaceAll('__PERIODS__', '[]');
function doc(page, total = 3, count = 25, term = '20261') {
  const form = {querySelector: selector => {
    const name = /name="(.*?)"/.exec(selector)?.[1];
    return {value: {term, grade: '2025', spno: '1000'}[name]};
  }};
  return {
    body: {textContent: `找到${total}页，总共${count}条，当前页号：${page} 本学期从2026/8/31开始`},
    documentElement: {outerHTML: `synthetic-page-${page}`},
    querySelector: selector => selector.startsWith('form') ? form : selector === 'th' ? {} : null
  };
}
async function run(options = {}) {
  const calls = [];
  let result, timer, callbackCount = 0;
  const docs = options.docs || [doc(1), doc(2), doc(3)];
  const frame = {
    location: {hostname: 'jw.gxzyxysy.com', href: 'https://jw.gxzyxysy.com/student/stselect.asp'},
    document: doc(options.current || 1), frames: [],
    fetch: async (url, request) => {
      calls.push({url, request});
      if (options.timeout) { timer(); throw Error('aborted'); }
      const index = calls.length - 1;
      return {
        ok: !options.httpError,
        url: options.redirect || url,
        arrayBuffer: async () => new TextEncoder().encode(String(index)).buffer
      };
    }
  };
  const window = {
    location: {hostname: 'jw.gxzyxysy.com'}, document: {querySelector: () => null},
    frames: options.missing ? [] : [frame],
    __sleepyBridge: {onWiseduResult: raw => {result = JSON.parse(raw); callbackCount++;}}
  };
  vm.runInNewContext(source, {
    window, URL, TextDecoder, AbortController,
    DOMParser: class {parseFromString(value) {return docs[Number(value)];}},
    setTimeout: fn => {timer = fn; return 1;}, clearTimeout: () => {}
  });
  for (let i = 0; i < 40; i++) await Promise.resolve();
  assert.ok(result, 'must call the real bridge callback');
  assert.equal(callbackCount, 1);
  assert.equal(window.__classySainzFetching, false);
  return {result, calls};
}
(async () => {
  for (const current of [1, 2, 3]) {
    const {result, calls} = await run({current});
    assert.equal(result.ok, true);
    assert.equal(result.startDate, '2026-08-31');
    assert.equal(JSON.parse(result.data).pages.length, 3);
    assert.equal(calls.length, 3);
    assert.match(calls[0].request.body, /lwBtnfirst=%CA%D7%D2%B3/);
    assert.match(calls[1].request.body, /lwBtnnext=%CF%C2%D2%B3/);
    assert.equal(calls[0].request.credentials, 'same-origin');
    assert.equal(calls[0].url, 'https://jw.gxzyxysy.com/student/stselect.asp');
  }
  for (const options of [
    {docs: [doc(1), doc(1), doc(3)]},
    {docs: [doc(1), doc(2, 3, 26), doc(3)]},
    {docs: [doc(1), doc(2, 3, 25, '20262'), doc(3)]},
    {httpError: true}, {redirect: 'https://jw.gxzyxysy.com/student/public/login.asp'},
    {redirect: 'https://example.test/student/stselect.asp'}, {timeout: true}, {missing: true}
  ]) {
    const {result} = await run(options);
    assert.equal(result.ok, false);
    assert.equal(result.data, undefined, 'never deliver partial pages');
  }
  console.log('PASS: 11 collector scenarios (synthetic DOM/HTTP; no live school session)');
})().catch(error => {console.error(error); process.exitCode = 1;});
