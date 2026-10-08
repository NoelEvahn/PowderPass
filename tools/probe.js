/* persistent probe: boot the game once, then run many snippets against it (saves ~30-90 s per QA iteration).
   node tools/probe.js serve [port]          boot headless and listen (run in background)
   node tools/probe.js eval '<js>'           evaluate an async function body in the page; prints JSON result
   node tools/probe.js file snippet.js       same, body read from a file
   node tools/probe.js shot name             screenshot -> tools/out/p-name.png
   node tools/probe.js reload                reload powder-pass.html (after edits) and wait for boot
   node tools/probe.js errors                print + clear collected page errors
   node tools/probe.js quit                  close the browser and stop the server */
const http = require('http');
const fs = require('fs');
const path = require('path');
const PORT = +(process.env.PP_PROBE_PORT || 9555);
const mode = process.argv[2];

function client(route, body) {
  return new Promise((res, rej) => {
    const r = http.request({ host: '127.0.0.1', port: PORT, path: route, method: 'POST' }, rs => {
      let d = ''; rs.on('data', c => d += c); rs.on('end', () => res({ code: rs.statusCode, d }));
    });
    r.on('error', rej); r.setTimeout(600000); r.end(body || '');
  });
}

async function serve() {
  const { boot, OUT } = require('./lib');
  fs.mkdirSync(OUT, { recursive: true });
  let s = await boot({ viewport: { width: 900, height: 600 } });
  console.log('booted ms', s.bootMs);
  const ready = () => s.page.waitForFunction(() => window.SK && SK.State && SK.W && SK.W.on && !document.getElementById('load'), null, { timeout: 180000 });
  const srv = http.createServer((q, r) => {
    let body = ''; q.on('data', c => body += c);
    q.on('end', async () => {
      const send = (code, o) => { r.writeHead(code, { 'content-type': 'application/json' }); r.end(JSON.stringify(o)); };
      try {
        if (q.url === '/eval') {
          const out = await s.page.evaluate(src => (new Function('return (async () => {' + src + '\n})()'))(), body);
          send(200, { ok: true, out });
        } else if (q.url === '/shot') {
          const f = path.join(OUT, 'p-' + (body || 'shot') + '.png');
          await s.page.evaluate(() => new Promise(requestAnimationFrame));
          await s.page.screenshot({ path: f }); send(200, { ok: true, out: f });
        } else if (q.url === '/reload') {
          s.errors.length = 0; const t = Date.now(); await s.page.reload(); await ready(); send(200, { ok: true, out: 'reloaded ms ' + (Date.now() - t) });
        } else if (q.url === '/errors') {
          send(200, { ok: true, out: s.errors.splice(0) });
        } else if (q.url === '/quit') {
          send(200, { ok: true, out: 'bye' }); await s.browser.close(); process.exit(0);
        } else send(404, { ok: false, out: 'unknown route' });
      } catch (e) { send(500, { ok: false, out: String(e && e.stack || e) }); }
    });
  });
  srv.listen(PORT, '127.0.0.1', () => console.log('probe listening', PORT));
}

(async () => {
  if (mode === 'serve') return serve();
  const map = { eval: ['/eval', process.argv[3]], file: ['/eval', process.argv[3] && fs.readFileSync(process.argv[3], 'utf8')],
    shot: ['/shot', process.argv[3]], reload: ['/reload'], errors: ['/errors'], quit: ['/quit'] };
  const m = map[mode];
  if (!m) { console.error('usage: probe.js serve|eval|file|shot|reload|errors|quit'); process.exit(2); }
  try {
    const { code, d } = await client(m[0], m[1]);
    const o = JSON.parse(d);
    console.log(typeof o.out === 'string' ? o.out : JSON.stringify(o.out, null, 1));
    process.exit(code === 200 ? 0 : 1);
  } catch (e) { console.error('probe not reachable: ' + e.message); process.exit(1); }
})();
