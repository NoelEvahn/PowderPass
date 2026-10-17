// Render specific film times to PNGs for inspection.
// usage: node tools/shoot.mjs <outDir> <w>x<h> t1 t2 ...   (needs THREE_DIR env pointing to node_modules/three)
import { chromium } from 'playwright';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
const dir = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const threeDir = process.env.THREE_DIR;
const [outDir, size, ...times] = process.argv.slice(2);
const [W, H] = size.split('x').map(Number);
fs.mkdirSync(outDir, { recursive: true });
const server = http.createServer((req, res) => {
  const u = req.url.split('?')[0]; const f = path.join(dir, u === '/' ? 'index.html' : u);
  fs.readFile(f, (e, d) => { if (e) { res.writeHead(404); res.end(); } else { res.writeHead(200, { 'content-type': 'text/html' }); res.end(d); } });
}).listen(0);
const port = server.address().port;
const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
const page = await browser.newPage({ viewport: { width: W, height: H } });
const logs = [];
page.on('console', (m) => logs.push(m.type() + ': ' + m.text()));
page.on('pageerror', (e) => logs.push('PAGEERROR: ' + e.message));
await page.route('https://cdn.jsdelivr.net/npm/three@0.160.0/**', (route) => {
  const rel = route.request().url().split('three@0.160.0/')[1];
  route.fulfill({ path: path.join(threeDir, rel), headers: { 'content-type': 'text/javascript', 'access-control-allow-origin': '*' } });
});
await page.route('https://fonts.googleapis.com/**', (r) => r.fulfill({ body: '', headers: { 'content-type': 'text/css' } }));
await page.route('https://fonts.gstatic.com/**', (r) => r.abort());
await page.addInitScript(() => { window.__AH_TEST = true; });
await page.goto(`http://localhost:${port}/?test`);
try { await page.waitForFunction(() => window.__ahReady, null, { timeout: 120000, polling: 500 }); }
catch (e) { console.log(logs.join('\n')); throw e; }
await page.evaluate(() => { document.getElementById('start').hidden = true; });
for (const t of times) {
  const t0 = Date.now();
  await page.evaluate((t) => window.__AH.renderAt(+t), t);
  await page.locator('#stage').screenshot({ path: path.join(outDir, `t${String(t).padStart(6, '0')}.png`) });
  console.log('t=' + t, (Date.now() - t0) + 'ms');
}
console.log(logs.filter((l) => !l.includes('GPU stall')).slice(0, 30).join('\n'));
await browser.close(); server.close();
