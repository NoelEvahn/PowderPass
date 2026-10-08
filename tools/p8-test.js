/* boarding needs a gate pass; Alpine Cabin (locked doors, perks: season pass, shuttle); West Bowl gate; Championship offer; finish lines off lift bases; trail skiers + lift riders */
const { boot, check, OUT } = require('./lib');
(async () => {
  const fails = []; const { browser, page, errors } = await boot({ viewport: { width: 420, height: 800 } });
  await page.addStyleTag({ content: '*{transition:none!important}' });
  const ev = (f, a) => page.evaluate(f, a);
  let r = await ev(() => { const L = SK.LIFTS[0]; let c = null; for (let i = 0; i < 4000 && !c; i++) { SK.sim(1); c = L.cars.find(q => q.userData.lwB > 0.9 && q.userData.dB > -3 && q.userData.dB < 1.2 && q.userData.open > 0.8 && q.userData.seats.indexOf(null) >= 0); }
    if (!c) return { none: true }; const v = new THREE.Vector3(...SK.LIFTX.door); c.localToWorld(v); L.state.pass = 0; const no = !!L.boardable(v.x, v.z, v.y + 1.6); L.state.pass = L.state.now + 90; const yes = !!L.boardable(v.x, v.z, v.y + 1.6); L.state.pass = 0; return { no: no, yes: yes }; });
  check(!r.none && !r.no && r.yes, 'boarding needs a pass from the ticket gate (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => ({ meadow: (t => t.len - t.finS)(SK.TRAILS.find(t => t.id === 'meadow')), ribbon: (t => t.len - t.finS)(SK.TRAILS.find(t => t.id === 'ribbon')) }));
  check(r.meadow === 16 && r.ribbon === 4, 'finish line pulled 16 m back from the lift base on Meadow Wander', fails);
  /* Alpine Cabin */
  r = await ev(() => { const S = SK.State, d = SK.ALPD[0], W = SK.W; W.x = d.x; W.z = d.z + 1.4; W.y = d.y; SK.sim(30); const a = { lockedSolid: !d.sol.off, msg: document.getElementById('toast').textContent };
    S.coins = 70000; SK.ECONX.econOp(['cabin', 'alpine']); SK.sim(40); a.owned = S.cabins.indexOf('alpine') >= 0; a.openSolid = !d.sol.off; a.coins = S.coins; return a; });
  check(r.lockedSolid && /Alpine Cabin is for sale/.test(r.msg) && r.owned && !r.openSolid && r.coins === 10000, 'Alpine Cabin locked until bought for 60,000, then its doors open (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => new Promise(res => { const S = SK.State, L = SK.LIFTS[0]; L.state.ticket = false; const at = SK.ECONX.CABINS.find(c => c.id === 'alpine').at; SK.W.x = at[0]; SK.W.z = at[1] + SK.RES_Z; SK.ECONX.openSheet('cabin', 'alpine', at); SK.sim(2);
    const html = document.querySelector('#sheet .sh-b').innerHTML; SK.ECONX.econOp(['shuttle', 'topA']); setTimeout(() => { res({ shuttleRow: /Top of Summit Express/.test(html), pass: /Season pass/.test(html), x: SK.W.x, z: SK.W.z - SK.RES_Z, ex: L.exitPos }); }, 900); }));
  check(r.shuttleRow && r.pass && Math.hypot(r.x - r.ex.x, r.z - r.ex.z) < 1, 'Alpine perks listed; shuttle drops you at the Summit Express top', fails);
  r = await ev(() => { const S = SK.State, c0 = S.coins, L = SK.LIFTS[0], k = SK.LIFTX; const W = SK.W; const c = Math.cos(L.ry), s = Math.sin(L.ry), w2 = (x, z) => [L.xs + x * c + z * s, L.zs - x * s + z * c], p = w2(-2.4, 12.9);
    W.x = p[0]; W.z = p[1] + SK.RES_Z; SK.sim(2); k.act(W.x, W.z); return { coins: S.coins - c0, ticket: L.state.ticket }; });
  check(r.coins === 0 && r.ticket, 'season pass: lift ticket is free', fails);
  /* West Bowl */
  r = await ev(() => { const S = SK.State, W = SK.W, B = SK.BOWL, cx = B.reduce((a, p) => a + p[0], 0) / B.length, cz = B.reduce((a, p) => a + p[1], 0) / B.length;
    W.x = cx; W.z = cz + 22 + SK.RES_Z; SK.sim(3); W.x = cx; W.z = cz + SK.RES_Z; SK.sim(3); const a = { inside: SK.P8.inPoly(W.x, W.z - SK.RES_Z, B) };
    S.owned.push('beacon'); S.gear.beacon = 'beacon'; S.medals = 25; W.x = cx; W.z = cz + SK.RES_Z; SK.sim(3); a.insideOk = SK.P8.inPoly(W.x, W.z - SK.RES_Z, B); return a; });
  check(!r.inside && r.insideOk, 'West Bowl turns you back without beacon + 20 medals, lets you in with them', fails);
  r = await ev(() => { SK.State.medals = 30; const R = SK.QX.REQ.find(q => q.id === 'champ'); return { has: !!R, steps: R.steps.map(s => s.txt) }; });
  check(r.has && r.steps.length === 3, 'Championship offered: ' + r.steps.join(' / '), fails);
  /* life on the mountain */
  r = await ev(() => { SK.sim(1200); return { skiers: SK.P8.TSK.filter(k => k.g.visible).length, riders: SK.P8.RID.filter(q => q.car).length }; });
  check(r.skiers >= 3 && r.riders >= 3, 'skiers on the trails (' + r.skiers + ') and riders on the lifts (' + r.riders + ')', fails);
  /* views */
  const shot = async (n, x, z, tx, tz, p, y) => { await ev(([x, z, tx, tz, p, y]) => { const W = SK.W; W.x = x; W.z = z + SK.RES_Z; W.y = SK.groundY(W.x, W.z) + (y || 0); W.yaw = Math.atan2(-(tx - x), -(tz - z)); W.pitch = p; SK.sim(4); }, [x, z, tx, tz, p, y]); await page.waitForTimeout(1500); await page.screenshot({ path: OUT + '/p8-' + n + '.png' }); };
  await ev(() => SK.P6.setTime(11 * 60));
  await shot('topexit', 12, -209, 12, -260, 0, 0); await shot('topback', 12, -209, -10, -190, -0.05, 0);
  await shot('liftbase', 14, 58, 12, 30, 0.05, 0); await shot('alpine', -40, -196, -52, -203, 0.1, 0); await shot('cafe', 46, -70, 46, -82, 0.1, 0);
  check(errors.length === 0, 'no errors ' + errors.join(' | '), fails);
  await browser.close(); console.log(fails.length ? 'FAILED ' + fails.length : 'ALL OK'); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
