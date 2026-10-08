/* Rita's race: talk to her, skis on during the countdown, ski Pine Ribbon with the autopilot, check the result and the daily prize */
const { boot, OUT } = require('./lib');
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: 390, height: 780 } });
  await page.addStyleTag({ content: '*{transition:none!important}' });
  const fr = () => page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
  for (const round of [1, 2]) {
    const a = await page.evaluate(() => { const W = SK.W, g = SK.RACE.g; if (SK.PL.mode === 'ski') SK.PL.exitSki(); W.x = g.position.x + 1.2; W.z = g.position.z + 0.6; W.y = SK.groundY(W.x, W.z); W.yaw = Math.atan2(-(g.position.x - W.x), -(g.position.z - W.z)); W.vx = W.vz = 0; SK.play(2);
      const q = SK.ECON.nearby(W.x, W.z); return { prompt: q && q.text, pace: SK.RACE.pace(), coins: SK.State.coins }; });
    console.log('round', round, JSON.stringify(a));
    if (round === 1) { await fr(); await page.screenshot({ path: OUT + '/race-0-rita.png' }); }
    await page.keyboard.press('f'); await page.waitForTimeout(200);
    const r = await page.evaluate(() => { const W = SK.W, PL = SK.PL, t = SK.RACE.t, K = SK.keys, st = SK.RACE.R.st;
      /* walk to the start line and put skis on during the countdown */
      const p0 = SK.P8.pointAt(t, 3), p1 = SK.P8.pointAt(t, 7); W.x = p0.x; W.z = p0.z + SK.RES_Z; W.y = SK.groundY(W.x, W.z); W.yaw = Math.atan2(-(p1.x - p0.x), -(p1.z - p0.z)); SK.play(2); PL.tryToggle();
      while (SK.RACE.R.st === 'count') SK.play(1);
      let s = 0; for (let i = 0; i < 30 * 60; i++) { const lx = PL.x, lz = PL.z - SK.RES_Z; let bd = 1e9, bs = 0; for (let q = Math.max(0, s - 20); q < Math.min(t.len, s + 40); q++) { const p = SK.P8.pointAt(t, q), d = Math.hypot(p.x - lx, p.z - lz); if (d < bd) { bd = d; bs = q; } } s = bs;
        const q = SK.P8.pointAt(t, Math.min(t.len, s + 9 + PL.spd * 0.8)), dx = q.x - lx, dz = q.z - lz, sp = Math.hypot(PL.vx, PL.vz), hx = sp > 0.5 ? PL.vx / sp : Math.sin(PL.hd), hz = sp > 0.5 ? PL.vz / sp : Math.cos(PL.hd), cr = (hx * dz - hz * dx) / (Math.hypot(dx, dz) || 1);
        K.a = K.d = K.s = 0; K.w = 1; if (cr > 0.06) K.d = 1; else if (cr < -0.06) K.a = 1; if (Math.abs(cr) > 0.6) K.s = 1;
        SK.play(1, 1 / 30); if (SK.RACE.R.st === 'after') break; }
      K.a = K.d = K.w = K.s = 0; return { st0: st, st: SK.RACE.R.st, won: SK.RACE.R.won, T: SK.RACE.R.T, toast: document.getElementById('toast').textContent, coins: SK.State.coins, rita: [+SK.RACE.g.position.x.toFixed(1), +(SK.RACE.g.position.z - SK.RES_Z).toFixed(1)] }; });
    console.log('   result', JSON.stringify(r));
    await fr(); await page.screenshot({ path: OUT + '/race-' + round + '.png' });
    await page.evaluate(() => SK.play(30 * 8));
  }
  console.log(errors.length ? 'ERRORS ' + errors.join(' | ') : 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
