/* cabins: tiers differ (stash, morning goods, stove recipes, wake perks), stash store/take, hot tub warms owners, save round trip; greenhouse pods */
const { boot, check } = require('./lib');
(async () => {
  const fails = []; const { browser, page, errors } = await boot();
  const ev = (f, a) => page.evaluate(f, a);
  let r = await ev(() => { const C = SK.ECONX.CABINS; return C.map(c => [c.id, c.tier, c.stash, Object.keys(c.morning).length, c.stove.length, !!c.tub, !!c.wax]); });
  check(new Set(r.map(x => x[2])).size >= 4 && r[0][4] < r[1][4] && !r[0][5] && r[2][5] && r[3][6], 'tiers differ: ' + JSON.stringify(r), fails);
  r = await ev(() => { const S = SK.State, op = s => SK.ECONX.econOp(s.split(':')); S.coins = 50000; op('cabin:chalet');
    S.inv = { log: 5 }; op('store:log:5'); const st1 = Object.assign({}, S.stash), inv1 = S.inv.log || 0; op('take:log:2');
    return { owned: S.cabins.slice(), st1: st1, inv1: inv1, st2: S.stash.log, inv2: S.inv.log }; });
  check(r.owned.join() === 'chalet' && r.st1.log === 5 && r.inv1 === 0 && r.st2 === 3 && r.inv2 === 2, 'buy chalet, store 5 logs, take 2 back (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => new Promise(res => { const S = SK.State, d0 = S.day; SK.P6.setTime(21 * 60); SK.ECONX.econOp(['sleep', '0', 'chalet']);   /* beds are for the night */ setTimeout(() => res({ day: S.day - d0, stash: Object.assign({}, S.stash), fondue: (S.buffs.fondue || 0) > S.playTime, wax: (S.buffs.wax || 0) > S.playTime }), 1000); }));
  check(r.day === 1 && r.stash.log === 6 && r.stash.berry === 3 && r.stash.cone === 3 && r.fondue && !r.wax, 'chalet morning: goods in the stash, breakfast buff, no wax (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { const S = SK.State, W = SK.W, out = id => { const t = SK.TUBS.find(q => q.id === id), c = SK.ECONX.CABINS.find(q => q.id === id), dx = t.x - c.at[0], dz = t.z - SK.RES_Z - c.at[1], l = Math.hypot(dx, dz); W.x = t.x + dx / l * 1.4; W.z = t.z + dz / l * 1.4; W.y = SK.groundY(W.x, W.z); };   /* stand on the far side, out of the house */
    const t = SK.TUBS.find(q => q.id === 'chalet'); out('chalet'); S.warmth = 40; SK.sim(40); const w1 = S.warmth;
    out('af1'); S.warmth = 40; SK.sim(40); return { own: +w1.toFixed(1), other: +S.warmth.toFixed(1) }; });
  check(r.own > 45 && r.other < 41, 'own hot tub warms you, a neighbour\'s does not (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { const G = SK.GREEN, W = SK.W, S = SK.State; S.inv = {}; let n = 0; for (let i = 0; i < 6; i++) { const t = G.trees[i]; W.x = t.x; W.z = 74 + (t.z - 74) * 0.3 + SK.RES_Z; W.y = SK.groundY(W.x, W.z); SK.sim(2); const q = SK.ECON.nearby(W.x, W.z); if (q && /cacao/.test(q.text)) { SK.ECON.act(W.x, W.z); n++; } }
    const t = G.trees[0]; W.x = t.x; W.z = 74 + (t.z - 74) * 0.3 + SK.RES_Z; SK.sim(2); const q2 = SK.ECON.nearby(W.x, W.z); return { n: n, inv: S.inv.cacao, after: q2 && q2.text }; });
  check(r.n === 6 && r.inv === 6 && !/Pick a ripe/.test(r.after || ''), 'greenhouse: 6 trees, one ripe pod each, then none (' + JSON.stringify(r) + ')', fails);
  await ev(() => SK.save()); await page.reload(); await page.waitForFunction(() => window.SK && SK.State && SK.State.q && !document.getElementById('load'), null, { timeout: 180000 });
  r = await ev(() => ({ stash: SK.State.stash, cab: SK.State.cabins }));
  check(r.stash.log === 6 && r.cab.join() === 'chalet', 'stash survives a reload', fails);
  check(errors.length === 0, 'no errors ' + errors.join(' | '), fails);
  await browser.close(); console.log(fails.length ? 'FAILED ' + fails.length : 'ALL OK'); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
