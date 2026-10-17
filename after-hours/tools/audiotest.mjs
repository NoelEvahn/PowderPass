// Samples output level (RMS/peak after the compressor) at points across the film to confirm the score/SFX play.
import { chromium } from 'playwright';
import http from 'node:http'; import fs from 'node:fs'; import path from 'node:path';
const dir = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const server = http.createServer((req, res) => { const u = req.url.split('?')[0]; fs.readFile(path.join(dir, u === '/' ? 'index.html' : u), (e, d) => { if (e) { res.writeHead(404); res.end(); } else { res.writeHead(200, { 'content-type': 'text/html' }); res.end(d); } }); }).listen(0);
const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--autoplay-policy=no-user-gesture-required'] });
const page = await (await browser.newContext({ viewport: { width: 640, height: 360 } })).newPage();
page.on('pageerror', (e) => console.log('PAGEERROR', e.message));
await page.route('https://cdn.jsdelivr.net/npm/three@0.160.0/**', (r) => r.fulfill({ path: path.join(process.env.THREE_DIR, r.request().url().split('three@0.160.0/')[1]), headers: { 'content-type': 'text/javascript', 'access-control-allow-origin': '*' } }));
await page.route('https://fonts.**/**', (r) => r.abort());
await page.goto(`http://localhost:${server.address().port}/`);
await page.waitForFunction(() => window.__ahReady, null, { timeout: 120000, polling: 500 });
await page.click('#playbig'); await page.waitForTimeout(500);
await page.evaluate(() => { window.__tap = window.__AH.Audio.debugTap(); });
const times = (process.argv[2] || '1,7,18,30,45,62,85,100,105,124,136,141,147,163,172').split(',').map(Number);
for (const t of times) {
  await page.evaluate((t) => window.__AH.seek(t), t);
  let mx = 0, rs = 0, n = 0;
  for (let i = 0; i < 12; i++) { await page.waitForTimeout(100); const v = await page.evaluate(() => window.__tap()); mx = Math.max(mx, v.peak); rs += v.rms; n++; }
  console.log(`t=${String(t).padStart(5)}  rms=${(rs / n).toFixed(4)}  peak=${mx.toFixed(3)}`);
}
await browser.close(); server.close();
