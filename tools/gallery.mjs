import { chromium } from 'playwright';
const OUT = process.argv[2];
const kinds = (process.argv[3] || 'monsters').split(',');
const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
for (const k of kinds) {
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  page.on('pageerror', (e) => { if (!e.message.includes('gallery mode')) console.log('pageerror', k, e.message); });
  await page.addInitScript((q) => localStorage.setItem('doors-hotel-settings-v1', JSON.stringify({ quality: q })), process.env.Q || 'medium');
  await page.goto('http://localhost:2567/?gallery=' + k);
  await page.waitForFunction(() => window.__galleryReady, null, { timeout: 60000 });
  await page.waitForTimeout(3000);
  await page.screenshot({ path: `${OUT}/gallery-${k}.png` });
  await page.close();
}
await browser.close();
