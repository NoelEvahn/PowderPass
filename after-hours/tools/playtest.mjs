// Interactive playback test: click Play, check clock/audio, seek, pause, mute, restart, chapters.
import { chromium } from 'playwright';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
const dir = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const threeDir = process.env.THREE_DIR;
const [W, H] = (process.argv[2] || '1280x720').split('x').map(Number);
const out = process.argv[3];
const server = http.createServer((req, res) => { const u = req.url.split('?')[0]; fs.readFile(path.join(dir, u === '/' ? 'index.html' : u), (e, d) => { if (e) { res.writeHead(404); res.end(); } else { res.writeHead(200, { 'content-type': 'text/html' }); res.end(d); } }); }).listen(0);
const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=user-gesture-required'] });
const ctxOpts = { viewport: { width: W, height: H } };
if (process.argv[4] === 'mobile') Object.assign(ctxOpts, { isMobile: true, hasTouch: true, deviceScaleFactor: 2 });
const page = await (await browser.newContext(ctxOpts)).newPage();
const errs = [];
page.on('pageerror', (e) => errs.push('PAGEERROR ' + e.message));
page.on('console', (m) => { if (m.type() === 'error') errs.push('console.error ' + m.text()); });
await page.route('https://cdn.jsdelivr.net/npm/three@0.160.0/**', (route) => route.fulfill({ path: path.join(threeDir, route.request().url().split('three@0.160.0/')[1]), headers: { 'content-type': 'text/javascript', 'access-control-allow-origin': '*' } }));
await page.route('https://fonts.googleapis.com/**', (r) => r.fulfill({ body: '', headers: { 'content-type': 'text/css' } }));
await page.route('https://fonts.gstatic.com/**', (r) => r.abort());
await page.goto(`http://localhost:${server.address().port}/`);
await page.waitForFunction(() => window.__ahReady, null, { timeout: 120000, polling: 500 });
const st = () => page.evaluate(() => ({ t: +window.__AH.Film.t.toFixed(2), playing: window.__AH.Film.playing, ctx: window.__AH.Audio.ctx && window.__AH.Audio.ctx.state, events: window.__AH.Audio.events.length, muted: window.__AH.Audio.muted, q: document.querySelector('#time').textContent }));
const reveal = async () => { const b = await page.locator('#stage').boundingBox(); await page.mouse.move(b.x + b.width / 2 + Math.random() * 20, b.y + b.height / 3); await page.waitForTimeout(150); };
const log = (k, v) => console.log(k.padEnd(18), JSON.stringify(v));
const box = await page.locator('#stage').boundingBox(); log('stage box', box);
if (out) await page.screenshot({ path: out + '-poster.png' });
await page.click('#playbig');
await page.waitForTimeout(4000); log('after play 4s', await st());
await page.evaluate(() => window.__AH.seek(131.5)); await page.waitForTimeout(3000); log('seek→131.5 +3s', await st());
if (out) await page.screenshot({ path: out + '-play.png' });
await page.keyboard.press('Space'); await page.waitForTimeout(800); const p1 = await st(); await page.waitForTimeout(1200); log('paused', [p1, await st()]);
await page.keyboard.press('m'); log('muted', await st());
await page.keyboard.press('Space'); await page.waitForTimeout(1500); log('resumed', await st());
await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2); await page.waitForTimeout(200);
await reveal(); await page.click('#b-restart'); await page.waitForTimeout(1500); log('restart +1.5s', await st());
await reveal(); await page.click('#b-ch'); await page.waitForTimeout(300);
const n = await page.locator('#chapters button').count(); await page.locator('#chapters button').nth(3).click(); await page.waitForTimeout(1500); log('chapter 4 (+1.5s) of ' + n, await st());
// seek bar click at 75%
await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2); await page.waitForTimeout(200);
await reveal(); const sb = await page.locator('#seek').boundingBox(); await page.mouse.click(sb.x + sb.width * 0.75, sb.y + sb.height / 2); await page.waitForTimeout(1500); log('seekbar 75%', await st());
if (out) { await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2); await page.screenshot({ path: out + '-ui.png' }); }
// end of film
await page.evaluate(() => window.__AH.seek(window.__AH.duration - 1)); await page.waitForTimeout(2500); log('end', await st());
const fps = await page.evaluate(async () => { let n = 0; const t0 = performance.now(); await new Promise((r) => { const f = () => { n++; if (performance.now() - t0 < 2000) requestAnimationFrame(f); else r(); }; requestAnimationFrame(f); }); return n / 2; });
log('raf fps (idle)', fps);
console.log(errs.length ? errs.join('\n') : 'no page errors');
await browser.close(); server.close();
