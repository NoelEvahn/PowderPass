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
  /* render pause: with PP_PAUSE set, rAF callbacks are parked (the GPU idles between snippets); a shot releases a few frames */
  const init = () => { const raf = window.requestAnimationFrame.bind(window); let parked = [];
    window.PP_PAUSE = false; window.PP_FLUSH = () => { const p = parked; parked = []; p.forEach(f => raf(f)); };
    window.requestAnimationFrame = f => { if (window.PP_PAUSE && window.SK && SK.W && SK.W.on !== undefined && !document.getElementById('load')) { parked.push(f); return 0; } return raf(f); }; };
  let s = await boot({ viewport: { width: +process.env.PP_W || 900, height: +process.env.PP_H || 600 }, init });
  console.log('booted ms', s.bootMs);
  /* in-page helpers: PQ.stand(x, y|null, z, yaw, pitch, minutes) puts the walker there (as tools/shot.js does);
     PQ.orbit(x, zWorld, r, thetaDeg, phiDeg, minutes, y) frees the orbit camera (as tools/view.js does) */
  const helpers = () => s.page.evaluate(() => {
    window.PQ = {
      stand(x, y, z, yaw, pitch, min) { if (min != null) SK.P6.setTime(min); const W = SK.W; W.on = true; document.body.classList.remove('ed');
        W.x = x; W.z = z; W.y = y == null ? SK.groundY(x, z) : y; W.yaw = yaw || 0; W.pitch = pitch || 0; W.vx = W.vz = 0; SK.play(10); SK.P6.lightsFor(); SK.play(2);
        return { x: W.x, y: +W.y.toFixed(3), z: W.z }; },
      orbit(x, z, r, th, ph, min, y) { if (min != null) SK.P6.setTime(min); SK.W.on = false; document.body.classList.add('ed');
        const gy = y != null ? y : SK.groundY(x, z); SK.snap({ tx: x, ty: gy, tz: z, r, theta: th * Math.PI / 180, phi: ph * Math.PI / 180 }); SK.P6.lightsFor(); return gy; },
    };
    window.PP_PAUSE = true;
  });
  const ready = async () => { await s.page.waitForFunction(() => window.SK && SK.State && SK.W && SK.W.on && !document.getElementById('load'), null, { timeout: 180000 }); await helpers(); };
  await s.page.addStyleTag({ content: '*{transition:none!important}' }); await helpers();
  await s.page.evaluate(() => { window.PP_PAUSE = true; });
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
          await s.page.evaluate(() => new Promise(res => { const was = window.PP_PAUSE; window.PP_PAUSE = false; window.PP_FLUSH();
            requestAnimationFrame(() => requestAnimationFrame(() => requestAnimationFrame(() => { window.PP_PAUSE = was; res(); }))); }));
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
  const map = { eval: () => ['/eval', process.argv[3]], file: () => ['/eval', fs.readFileSync(process.argv[3], 'utf8')],
    shot: () => ['/shot', process.argv[3]], reload: () => ['/reload'], errors: () => ['/errors'], quit: () => ['/quit'] };
  const m = map[mode] && map[mode]();
  if (!m) { console.error('usage: probe.js serve|eval|file|shot|reload|errors|quit'); process.exit(2); }
  try {
    const { code, d } = await client(m[0], m[1]);
    const o = JSON.parse(d);
    console.log(typeof o.out === 'string' ? o.out : JSON.stringify(o.out, null, 1));
    process.exit(code === 200 ? 0 : 1);
  } catch (e) { console.error('probe not reachable: ' + e.message); process.exit(1); }
})();
