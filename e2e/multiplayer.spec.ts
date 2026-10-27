import { test, expect, type Page, type Browser } from '@playwright/test';

type W = { __doors: any; __galleryReady?: boolean };

async function newPlayer(browser: Browser, name: string, opts: Parameters<Browser['newContext']>[0] = {}) {
  const ctx = await browser.newContext({ viewport: { width: 960, height: 540 }, ...opts });
  const page = await ctx.newPage();
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('requestfailed', (r) => errors.push('request failed: ' + r.url()));
  await page.addInitScript((n) => localStorage.setItem('doors-hotel-settings-v1', JSON.stringify({ name: n, quality: 'low' })), name);
  await page.goto('/');
  await expect(page.locator('#netStatus')).toHaveClass(/online/);
  return { page, errors, ctx };
}

/** Steer the local player to a world point by setting yaw and holding W. */
async function walkTo(page: Page, p: { x: number; z: number }, tol = 0.6, maxMs = 45_000) {
  const t0 = Date.now();
  await page.keyboard.down('KeyW');
  try {
    while (Date.now() - t0 < maxMs) {
      const d = await page.evaluate((p) => {
        const g = (window as unknown as W).__doors.game;
        const dx = p.x - g.pos.x, dz = p.z - g.pos.z;
        g.yaw = Math.atan2(-dx, -dz); g.pitch = 0;
        return Math.hypot(dx, dz);
      }, p);
      if (d < tol) return true;
      await page.waitForTimeout(100);
    }
    return false;
  } finally { await page.keyboard.up('KeyW'); }
}

test('two players share one hotel: lobby code, synchronized start, doors, positions', async ({ browser }) => {
  const a = await newPlayer(browser, 'Alpha');
  const b = await newPlayer(browser, 'Bravo');
  await a.page.click('#bPrivate');
  await expect(a.page.locator('#lobby')).toHaveClass(/show/);
  const code = (await a.page.locator('#lobbyCode').textContent())!.trim();
  expect(code).toMatch(/^[A-Z0-9]{5}$/);
  await b.page.fill('#codeIn', code);
  await b.page.click('#bJoin');
  await expect(b.page.locator('#lobbyPlayers .p')).toHaveCount(2);
  await expect(a.page.locator('#lobbyPlayers .p')).toHaveCount(2);
  await a.page.click('#bReady');
  await b.page.click('#bReady');
  for (const p of [a.page, b.page]) await p.waitForFunction(() => (window as unknown as W).__doors?.inGame, null, { timeout: 90_000 });
  const seeds = await Promise.all([a.page, b.page].map((p) => p.evaluate(() => (window as unknown as W).__doors.game.layout.seed)));
  expect(seeds[0]).toBe(seeds[1]);
  // each sees the other as a remote avatar
  await expect.poll(() => a.page.evaluate(() => (window as unknown as W).__doors.remotes)).toBe(1);
  await expect.poll(() => b.page.evaluate(() => (window as unknown as W).__doors.remotes)).toBe(1);
  await a.page.screenshot({ path: 'test-results/mp-a-start.png' });

  // Alpha walks to door 1 and opens it with E
  const path = await a.page.evaluate(() => (window as unknown as W).__doors.game.layout.rooms[0].path);
  for (const p of path.slice(1, -1)) await walkTo(a.page, p);
  const door = await a.page.evaluate(() => (window as unknown as W).__doors.game.layout.rooms[1].origin);
  await a.page.evaluate((d) => { const g = (window as unknown as W).__doors.game; g.yaw = Math.atan2(-(d.x - g.pos.x), -(d.z - g.pos.z)); g.pitch = -0.1; }, door);
  await expect(a.page.locator('#prompt')).toContainText('Open door 1');
  await a.page.keyboard.press('KeyE');
  // Bravo's world reflects the server-authoritative door state
  await expect.poll(() => b.page.evaluate(() => (window as unknown as W).__doors.doorsOpen), { timeout: 20_000 }).toContain(1);
  // Bravo sees Alpha near the door (interpolated remote position)
  const remotePos = await b.page.evaluate(() => { const g = (window as unknown as W).__doors.game; const r = [...g.remotes.values()][0]; return r.av.root.position; });
  expect(Math.hypot(remotePos.x - door.x, remotePos.z - door.z)).toBeLessThan(4);
  await b.page.screenshot({ path: 'test-results/mp-b-sees-a.png' });

  // Bravo reloads (disconnect) and is reattached to the same run within the reconnect window
  await b.page.reload();
  await b.page.waitForFunction(() => (window as unknown as W).__doors?.inGame, null, { timeout: 90_000 });
  const seedAfter = await b.page.evaluate(() => (window as unknown as W).__doors.game.layout.seed);
  expect(seedAfter).toBe(seeds[0]);
  await expect.poll(() => b.page.evaluate(() => (window as unknown as W).__doors.doorsOpen)).toContain(1);

  const fps = await a.page.evaluate(() => (window as unknown as W).__doors.fps);
  console.log(`software-rendered fps (low preset): ${fps.toFixed(1)}`);
  expect(a.errors.filter((e) => !e.includes('gallery'))).toEqual([]);
  await a.ctx.close(); await b.ctx.close();
});

test('mobile: touch controls, responsive HUD and joystick movement', async ({ browser }) => {
  const m = await newPlayer(browser, 'Phone', { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2, userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148' });
  await m.page.click('#bQuick');
  await expect(m.page.locator('#lobby')).toHaveClass(/show/);
  await m.page.click('#bReady');
  await m.page.waitForFunction(() => (window as unknown as W).__doors?.inGame, null, { timeout: 90_000 });
  await expect(m.page.locator('#touch')).toHaveClass(/show/);
  await expect(m.page.locator('#tInteract')).toBeVisible();
  // the canvas fills the viewport
  const box = await m.page.locator('#game').boundingBox();
  expect(Math.round(box!.width)).toBe(390);
  const start = await m.page.evaluate(() => (window as unknown as W).__doors.pos);
  // drag the virtual joystick upward (forward) with CDP touch events
  const cdp = await m.ctx.newCDPSession(m.page);
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: 80, y: 700, id: 1 }] });
  for (let k = 1; k <= 10; k++) await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: 80, y: 700 - k * 6, id: 1 }] });
  await m.page.waitForTimeout(2500);
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  const end = await m.page.evaluate(() => (window as unknown as W).__doors.pos);
  expect(Math.hypot(end.x - start.x, end.z - start.z)).toBeGreaterThan(0.3);
  // swipe-to-look on the right half rotates the camera
  const yaw0 = await m.page.evaluate(() => (window as unknown as W).__doors.game.yaw);
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: 300, y: 400, id: 2 }] });
  for (let k = 1; k <= 8; k++) await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: 300 - k * 10, y: 400, id: 2 }] });
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  await m.page.waitForTimeout(300);
  const yaw1 = await m.page.evaluate(() => (window as unknown as W).__doors.game.yaw);
  expect(Math.abs(yaw1 - yaw0)).toBeGreaterThan(0.05);
  await m.page.screenshot({ path: 'test-results/mobile-hud.png' });
  expect(m.errors).toEqual([]);
  await m.ctx.close();
});

test('monster and room asset gallery renders without errors', async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 960, height: 540 } });
  const page = await ctx.newPage();
  const errors: string[] = [];
  page.on('pageerror', (e) => { if (!e.message.includes('gallery mode')) errors.push(e.message); });
  await page.addInitScript(() => localStorage.setItem('doors-hotel-settings-v1', JSON.stringify({ quality: 'low' })));
  for (const k of ['monsters', 'library', 'electrical', 'greenhouse']) {
    await page.goto('/?gallery=' + k);
    await page.waitForFunction(() => (window as unknown as W).__galleryReady, null, { timeout: 60_000 });
    await page.waitForTimeout(1000);
    await page.screenshot({ path: `test-results/gallery-${k}.png` });
  }
  expect(errors).toEqual([]);
  await ctx.close();
});
