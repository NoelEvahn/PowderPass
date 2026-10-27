// Scripted browser playtest: walks the local player along the generated route, opens doors,
// and captures screenshots + metrics. Usage: node tools/playtest.mjs <outdir> [rooms] [quality]
import { chromium } from 'playwright';
const OUT = process.argv[2];
const ROOMS = Number(process.argv[3] || 3);
const Q = process.argv[4] || 'low';
const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
const errors = [];
page.on('pageerror', (e) => errors.push('pageerror ' + e.message));
page.on('console', (m) => { if (m.type() === 'error') errors.push('console ' + m.text()); });
await page.addInitScript((q) => localStorage.setItem('doors-hotel-settings-v1', JSON.stringify({ name: 'Bot', quality: q, showFps: true })), Q);
await page.goto('http://localhost:2567/');
await page.waitForTimeout(800);
await page.click('#bPrivate');
await page.waitForSelector('#lobby.show');
await page.click('#bReady');
await page.waitForFunction(() => window.__doors?.inGame, null, { timeout: 60000 });
await page.waitForTimeout(2500);
// steer toward a world point by setting yaw each step
async function walkTo(p, tol = 0.6, maxMs = 30000) {
  const t0 = Date.now();
  await page.keyboard.down('KeyW');
  while (Date.now() - t0 < maxMs) {
    const d = await page.evaluate((p) => {
      const g = __doors.game;
      const dx = p.x - g.pos.x, dz = p.z - g.pos.z;
      g.yaw = Math.atan2(-dx, -dz);
      g.pitch = -0.05;
      return Math.hypot(dx, dz);
    }, p);
    if (d < tol) break;
    await page.waitForTimeout(100);
  }
  await page.keyboard.up('KeyW');
}
let shot = 0;
for (let n = 0; n < ROOMS; n++) {
  const path = await page.evaluate((n) => __doors.game.layout.rooms[n].path, n);
  for (const p of path.slice(1, -1)) await walkTo(p);
  await page.screenshot({ path: `${OUT}/room${n}-${shot++}.png` });
  // face the door and press E
  const door = await page.evaluate((n) => __doors.game.layout.rooms[n + 1].origin, n);
  await page.evaluate((d) => { const g = __doors.game; g.yaw = Math.atan2(-(d.x - g.pos.x), -(d.z - g.pos.z)); g.pitch = -0.1; }, door);
  await page.waitForTimeout(400);
  const prompt = await page.evaluate(() => document.querySelector('#prompt')?.textContent);
  await page.keyboard.press('KeyE');
  await page.waitForTimeout(1500);
  const open = await page.evaluate((n) => __doors.doorsOpen.includes(n + 1), n);
  console.log(`room ${n}: prompt="${prompt}" door ${n + 1} open=${open}`);
  if (!open) {
    // locked: try every drawer in the room for a key, then retry
    const ids = await page.evaluate((n) => __doors.game.layout.rooms[n].interactables.filter((i) => i.kind === 'drawer' || i.kind === 'lever' || i.kind === 'pickup').map((i) => ({ id: i.id, pos: i.pos })), n);
    for (const it of ids) {
      await walkTo(it.pos, 1.3, 15000);
      await page.evaluate((id) => __doors.game.net.send({ t: 'interact', id }), it.id);
      await page.waitForTimeout(300);
      if (await page.evaluate((n) => __doors.game.self.inventory.some((i) => i && i.type === 'key' && i.door === n + 1) || __doors.doorsOpen.includes(n + 1), n)) break;
    }
    const last = path[path.length - 2];
    await walkTo(last);
    await page.evaluate((d) => { const g = __doors.game; g.yaw = Math.atan2(-(d.x - g.pos.x), -(d.z - g.pos.z)); }, door);
    await page.keyboard.press('KeyE');
    await page.waitForTimeout(1200);
    console.log(`  retry door ${n + 1} open=${await page.evaluate((n) => __doors.doorsOpen.includes(n + 1), n)}`);
  }
  await page.screenshot({ path: `${OUT}/room${n}-${shot++}-door.png` });
  // walk through
  const next = await page.evaluate((n) => __doors.game.layout.rooms[n + 1].path[1], n);
  await walkTo(next);
}
console.log('metrics', JSON.stringify(await page.evaluate(() => ({ room: __doors.room, fps: __doors.fps.toFixed(1), self: { hp: __doors.self.hp, gold: __doors.self.gold } }))));
console.log('errors', errors.slice(0, 15).join('\n'));
await browser.close();
