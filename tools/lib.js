/* shared headless boot for the tests: opens powder-pass.html in Chromium (software WebGL), waits for the world, collects errors */
const { chromium } = require('playwright');
const path = require('path');
const FILE = 'file://' + path.resolve(__dirname, '..', process.env.PP_FILE || 'powder-pass.html');
async function boot(opts) {
  opts = opts || {};
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: opts.viewport || { width: 900, height: 600 } });
  page.setDefaultTimeout(300000); const errors = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
  /* every boot is a new browser with empty storage, so a run starts from a fresh save. opts.save seeds one on the first load only
     (file:// pages are opaque origins: window.name and sessionStorage can't be trusted to survive a reload, so the flag lives here) */
  if (opts.save !== undefined) { let first = true; await page.exposeFunction('ppFirstLoad', () => { const f = first; first = false; return f; });
    await page.addInitScript(s => { window.ppFirstLoad().then(f => { if (f) try { localStorage.setItem('powderpass.save', s); } catch (e) {} }); }, opts.save); }
  if (opts.init) await page.addInitScript(opts.init);
  const t0 = Date.now();
  await page.goto((opts.file ? 'file://' + path.resolve(opts.file) : FILE) + (opts.query || ''));
  await page.waitForFunction(() => window.SK && SK.State && SK.W && SK.W.on && !document.getElementById('load'), null, { timeout: 180000 });
  return { browser, page, errors, bootMs: Date.now() - t0 };
}
function check(cond, msg, fails) { console.log((cond ? 'ok   ' : 'FAIL ') + msg); if (!cond) fails.push(msg); }
module.exports = { boot, check, OUT: path.resolve(__dirname, 'out') };
