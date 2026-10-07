/* lift journey on a phone: find the ticket booth, buy, walk through the gate, board the Summit Express, ride, get off at the top */
const { boot, OUT } = require('./lib');
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: 390, height: 780 } });
  await page.addStyleTag({ content: '*{transition:none!important}' });
  const fr = () => page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
  const shot = async n => { await fr(); await page.screenshot({ path: `${OUT}/lj-${n}.png` }); };
  /* scan the base area for prompts */
  const P = await page.evaluate(() => { const L = SK.LIFT, out = {}; for (let x = L.xs - 14; x <= L.xs + 14; x += 0.5) for (let z = L.zs; z <= L.zs + 30; z += 0.5) { const q = SK.LIFTX.nearby(x, z + SK.RES_Z); if (q && !out[q.text]) out[q.text] = [x, z]; } return out; });
  console.log('prompts at base:', JSON.stringify(P));
  const booth = Object.keys(P).find(k => /ticket/i.test(k) && /buy|\d/i.test(k)) || Object.keys(P)[0];
  const at = P[booth]; console.log('booth prompt:', booth);
  const ok = await page.evaluate(() => { const W = SK.W, L = SK.LIFT; for (let x = L.xs - 14; x <= L.xs + 14; x += 0.5) for (let z = L.zs; z <= L.zs + 30; z += 0.5) { const q = SK.LIFTX.nearby(x, z + SK.RES_Z); if (!q || !/Buy a lift ticket/.test(q.text)) continue;
      W.x = x; W.z = z + SK.RES_Z; W.y = SK.groundY(W.x, W.z); W.vx = W.vz = 0; SK.play(4); const q2 = SK.LIFTX.nearby(W.x, W.z); if (q2 && /Buy a lift ticket/.test(q2.text)) return [x, z, W.x, W.z - SK.RES_Z]; } return null; });
  console.log('standable booth spot:', JSON.stringify(ok));
  await shot('1-booth');
  await page.keyboard.press('f'); await page.waitForTimeout(300); await shot('2-after-f');
  const st = await page.evaluate(() => ({ ticket: SK.LIFTX.state.ticket, coins: SK.State.coins, act: document.getElementById('act').textContent, sheet: document.getElementById('sheet').classList.contains('off') ? '' : document.getElementById('sheet').innerText.slice(0, 200) }));
  console.log('after F:', JSON.stringify(st));
  /* walk the queue: hold W towards the lift for up to 30 s of game time, logging prompts */
  const log = await page.evaluate(() => { const W = SK.W, L = SK.LIFT, out = []; let last = ''; W.yaw = Math.atan2(-(L.xs - W.x), -((L.zs + SK.RES_Z) - W.z)); SK.keys.w = 1;
    W.x = L.xs; W.z = L.zs + 12.5 + SK.RES_Z; W.y = SK.groundY(W.x, W.z); W.vx = W.vz = 0;   /* in line with the gate, as a player walking up the queue */
    for (let i = 0; i < 900; i++) { const tx = L.xs, tz = L.zs + 2 + SK.RES_Z; W.yaw = Math.atan2(-(tx - W.x), -(tz - W.z)); SK.play(1); const q = SK.LIFTX.nearby(W.x, W.z), t = (q ? q.text : '') + (W.board ? ' [boarding]' : '') + (W.ride ? ' [riding]' : '');
      if (t !== last) { out.push(i + ': ' + t + ' @' + W.x.toFixed(1) + ',' + (W.z - SK.RES_Z).toFixed(1)); last = t; } if (q && q.act && /gate|scan|ticket/i.test(q.text)) { SK.LIFTX.act(W.x, W.z); } if (W.ride || W.board) break; }
    SK.keys.w = 0; return out; });
  console.log(log.join('\n'));
  await shot('3-queue');
  const r = await page.evaluate(() => { const W = SK.W, L = SK.LIFT, D = SK.LIFTX.door; let tries = 0;
    for (let i = 0; i < 2400 && !W.ride; i++) {
      let best = null; L.cars.forEach(c => { const U = c.userData; if (U.open < 0.5) return; const p = L.toWorld(c, [D[0], D[1], D[2] + 0.55]), d = Math.hypot(p.x - W.x, p.z - W.z); if (d < 8 && (!best || d < best.d)) best = { d, p }; });
      if (best && !W.board) { W.yaw = Math.atan2(-(best.p.x - W.x), -(best.p.z - W.z)); SK.keys.w = best.d > 0.3 ? 1 : 0; tries++; } else SK.keys.w = 0;
      SK.play(1); }
    SK.keys.w = 0; return { board: !!W.board, ride: !!W.ride, tries }; });
  console.log('boarded:', JSON.stringify(r)); await shot('4-ride-start');
  if (r.ride) { await page.evaluate(() => { for (let i = 0; i < 30 * 40; i++) SK.play(1); }); await shot('5-ride-mid');
    for (const [n, yw] of [['y90', Math.PI / 2], ['y180', Math.PI], ['y270', -Math.PI / 2]]) { await page.evaluate(yw => { SK.W.yaw = yw; SK.W.pitch = -0.12; SK.play(1); }, yw); await shot('5-' + n); }
    const top = await page.evaluate(() => { const W = SK.W; let i = 0; for (; i < 30 * 200 && W.ride && !SK.LIFTX.leaveInfo(W.ride); i++) SK.play(1); const can = !!(W.ride && SK.LIFTX.leaveInfo(W.ride)); if (can) W.leave(); for (let k = 0; k < 120; k++) SK.play(1); return { secs: Math.round(i / 30), left: can, ride: !!W.ride, at: [W.x.toFixed(1), (W.z - SK.RES_Z).toFixed(1)] }; });
    console.log('top:', JSON.stringify(top)); await shot('6-top'); }
  console.log(errors.length ? 'ERRORS ' + errors.join(' | ') : 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
