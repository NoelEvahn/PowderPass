/* Phase 5: story chapters, requests, lessons, daily board, helping (fallen skiers, lost items, cocoa, guiding), lift medal locks, save round trip */
const { boot, check, OUT } = require('./lib');
(async () => {
  const fails = []; const { browser, page, errors } = await boot();
  const ev = (f, a) => page.evaluate(f, a);
  /* helpers inside the page: teleport (resort-local x/z), run sim ticks, act like pressing F */
  await ev(() => {
    window.T = {
      go(x, z) { const W = SK.W; if (SK.PL.mode === 'ski') SK.PL.exitSki(); W.x = x; W.z = z + SK.RES_Z; W.y = SK.groundY(W.x, W.z); W.vx = W.vz = 0; SK.sim(3); },
      act() { const q = SK.ECON.nearby(SK.W.x, SK.W.z); SK.ECON.act(SK.W.x, SK.W.z); SK.sim(3); return q && q.text; },
      op(s) { SK.ECONX.econOp(s.split(':')); SK.sim(2); },
      q: () => SK.State.q, sheet: () => { const S = SK.ECONX.sheetState(); return S ? S.kind + ':' + S.id : ''; },
      close() { SK.ECONX.closeSheet(); SK.sim(2); },
      track: () => { const t = SK.QX.tracked(); return t ? t.title + ' | ' + t.txt : ''; }
    };
  });
  /* --- fresh start: chapter 1, locks --- */
  let r = await ev(() => ({ ch: T.q().ch, tr: T.track(), lockA: SK.liftLock('A'), lockB: SK.liftLock('B'), lockE: SK.liftLock('E'), medals: SK.State.medals, daily: T.q().daily.list.length, sk: T.q().help.sk.length, lost: T.q().help.lost.length }));
  check(r.ch === 0 && /Coach Bo/.test(r.tr), 'fresh save starts chapter 1: ' + r.tr, fails);
  check(r.lockA === null && /3 medals/.test(r.lockB) && /15 medals/.test(r.lockE), 'locks: A open, B ' + r.lockB, fails);
  check(r.daily === 3 && r.sk === 0 && r.lost === 3, 'day rolled: 3 board quests, no pre-placed fallen skiers (real skiers crash), 3 lost items', fails);
  await page.screenshot({ path: OUT + '/q-start.png' });

  /* --- chapter 1: talk to Coach Bo, ride the carpet, ski Snowdrop --- */
  r = await ev(() => { T.go(-4, 40.5); const p = T.act(); const sh = T.sheet(); return { p: p, sh: sh, s: T.q().chP.s }; });
  check(/Coach Bo/.test(r.p) && r.sh === 'place:school' && r.s === 1, 'talking to Coach Bo opens the Ski School and advances (' + r.p + ')', fails);
  await page.waitForTimeout(300); await page.screenshot({ path: OUT + '/q-school.png' });
  r = await ev(() => { T.close(); const C = SK.CARPET; T.go(C.x, (C.z0 + C.z1) / 2); return T.q().chP.s; });
  check(r === 2, 'stepping on the Magic Carpet advances chapter 1', fails);
  r = await ev(() => { const c0 = SK.State.coins; SK.events.emit('runend', { trail: 'bunny', finished: true, result: { score: 300, tier: 1 } }); SK.sim(2); return { ch: T.q().ch, dc: SK.State.coins - c0, m: SK.State.medals }; });
  check(r.ch === 1 && r.dc === 100 && r.m === 2, 'chapter 1 done: +100 coins, 2 medals (' + JSON.stringify(r) + ')', fails);

  /* --- chapter 2: cacao from the Greenhouse to the Lodge Café --- */
  r = await ev(() => { const G = SK.GREEN; for (let i = 0; i < 3; i++) { const t = G.trees[i]; T.go(t.x, 74 + (t.z - 74) * 0.3); T.act(); } return { s: T.q().chP.s, inv: SK.State.inv.cacao }; });
  check(r.s === 1 && r.inv === 3, 'picking 3 cacao advances chapter 2', fails);
  r = await ev(() => { const S = SK.ECONX.SHOPS.cafe; T.go(S.at[0], S.at[1] + 3); SK.ECONX.openSheet('shop', 'cafe', S.at); SK.sim(2); const html = document.querySelector('#sheet .sh-b').innerHTML; const c0 = SK.State.coins; T.op('q:give:ch'); return { has: /Hand over 3/.test(html), ch: T.q().ch, dc: SK.State.coins - c0, inv: SK.State.inv.cacao || 0, m: SK.State.medals, B: SK.liftLock('B') }; });
  check(r.has && r.ch === 2 && r.dc === 250 && r.inv === 0 && r.m === 4 && r.B === null, 'chapter 2 hand-over at the café: +250, 4 medals, Lodge Lift open (' + JSON.stringify(r) + ')', fails);
  await ev(() => T.close());

  /* --- chapter 3: Rex, then help 3 fallen skiers --- */
  r = await ev(() => { const P = SK.QX.QP.patrol; T.go(P.at[0] - 1.6, P.at[1]); const p = T.act(); const s = T.q().chP.s; T.close(); return { p: p, s: s }; });
  check(/Rex/.test(r.p) && r.s === 1, 'talking to Rex starts the patrol step (' + r.p + ')', fails);
  r = await ev(() => { const out = [], H = T.q().help, down = () => H.sk.find(k => !k.done); let maxDown = 0;
    for (let i = 0; i < 3; i++) { let k = null; for (let n = 0; n < 120 && !(k = down()); n++) { SK.play(300, 1 / 30); maxDown = Math.max(maxDown, H.sk.filter(q => !q.done).length); }
      if (!k) { out.push(['none down', 0, false]); continue; } const f = SK.QX.FALLEN.find(q => q.k === k), real = !!f && SK.P8.TSK.some(t => t.g === f.g);
      T.go(k.x + 1.2, k.z); const c0 = SK.State.coins; const p = T.act(); out.push([p, SK.State.coins - c0, k.done, real]); SK.play(200, 1 / 30); }
    return { out: out, ch: T.q().ch, maxDown: maxDown }; });
  check(r.maxDown <= 2, 'never more than 2 skiers down at once (' + r.maxDown + ')', fails);
  check(r.out.every(o => o[3]), 'every fallen skier is a real trail skier', fails);
  check(r.out.every(o => /Help the fallen skier/.test(o[0]) && o[1] >= 40 && o[2]) && r.ch === 3, 'helped 3 fallen skiers, paid by grade, chapter 3 done (' + JSON.stringify(r.out.map(o => o[1])) + ')', fails);
  await ev(() => { for (let n = 0; n < 120 && !T.q().help.sk.find(q => !q.done); n++) SK.play(300, 1 / 30); const k = T.q().help.sk.find(q => !q.done); if (!k) return; T.go(k.x + 3, k.z + 3); SK.W.yaw = Math.atan2(-(k.x - SK.W.x), -(k.z + SK.RES_Z - SK.W.z)); SK.sim(2); });
  await page.waitForTimeout(400); await page.screenshot({ path: OUT + '/q-fallen.png' });

  /* --- chapter 4: firewood to the hotel --- */
  r = await ev(() => { SK.State.inv.firewood = 4; const S = SK.ECONX.SHOPS.hotel; T.go(S.at[0], S.at[1] - 6); SK.ECONX.openSheet('shop', 'hotel', S.at); SK.sim(2); const c0 = SK.State.coins; T.op('q:give:ch'); return { ch: T.q().ch, dc: SK.State.coins - c0, m: SK.State.medals, D: SK.liftLock('D') }; });
  check(r.ch === 4 && r.dc === 400 && r.m === 8 && r.D === null, 'chapter 4: +400, 8 medals, West Ridge open (' + JSON.stringify(r) + ')', fails);
  await page.waitForTimeout(300); await page.screenshot({ path: OUT + '/q-hotel.png' });

  /* --- request: the lost mitten (accept at the hotel, find it, bring it back) --- */
  r = await ev(() => { T.op('q:accept:mitten'); const a = !!T.q().act.mitten; T.close(); const sp = SK.QX.REQ[0].steps[0].spot; T.go(sp[0] + 1, sp[1]); const p = T.act(); const s = T.q().act.mitten && T.q().act.mitten.s;
    const S = SK.ECONX.SHOPS.hotel; T.go(S.at[0], S.at[1] - 6); const c0 = SK.State.coins; SK.ECONX.openSheet('shop', 'hotel', S.at); SK.sim(3); return { a: a, p: p, s: s, done: T.q().done.indexOf('mitten') >= 0, dc: SK.State.coins - c0 }; });
  check(r.a && /mitten/.test(r.p) && r.s === 1 && r.done && r.dc === 60, 'mitten request: accept, pick up, return +60 (' + JSON.stringify(r) + ')', fails);
  await ev(() => T.close());

  /* --- lost items: pick one up, hand it in at the hotel --- */
  r = await ev(() => { const L = T.q().help.lost[0]; T.go(L.x + 1, L.z); const p = T.act(); const st1 = L.st; const S = SK.ECONX.SHOPS.hotel; T.go(S.at[0], S.at[1] - 6); SK.ECONX.openSheet('shop', 'hotel', S.at); SK.sim(2); const c0 = SK.State.coins; T.op('q:lost'); T.close(); return { p: p, st1: st1, st2: L.st, dc: SK.State.coins - c0 }; });
  check(/Pick up the lost/.test(r.p) && r.st1 === 1 && r.st2 === 2 && r.dc >= 60, 'lost item found and handed in (+' + r.dc + ')', fails);

  /* --- cocoa round --- */
  r = await ev(() => { SK.State.inv.cocoa = 2; const C = SK.QX.COCOA[0]; T.go(C[0] - 1, C[1]); const c0 = SK.State.coins; const p = T.act(); return { p: p, dc: SK.State.coins - c0, given: T.q().help.cocoa.length, inv: SK.State.inv.cocoa }; });
  check(/hot cocoa/.test(r.p) && r.dc >= 28 && r.dc <= 40 && r.given === 1 && r.inv === 1, 'cocoa handed to the Lift A queue: +' + r.dc + ' (' + r.p + ')', fails);

  /* --- guiding a beginner down Snowdrop --- */
  r = await ev(() => { T.go(-4, 40.5); T.act(); T.op('q:guide'); const g1 = T.q().help.guide; T.close();
    const t = SK.TRAILS.find(q => q.id === 'bunny'), K = SK.QX.KID; T.go(K.x + 2, K.z); const g2 = T.q().help.guide;
    const c0 = SK.State.coins; let maxGap = 0;
    /* walk down the trail at 3 m/s, then wait at the bottom */
    const P = t.pts, steps = []; for (let i = 0; i < P.length - 1; i++) { const a = P[i], b = P[i + 1], L = Math.hypot(b[0] - a[0], b[1] - a[1]); for (let s = 0; s < L; s += 0.15) steps.push([a[0] + (b[0] - a[0]) * s / L, a[1] + (b[1] - a[1]) * s / L]); }
    steps.forEach(p => { SK.W.x = p[0]; SK.W.z = p[1] + SK.RES_Z; SK.sim(1); maxGap = Math.max(maxGap, Math.hypot(K.x - p[0], K.z - p[1])); });
    for (let i = 0; i < 200 && T.q().help.guide === 2; i++) SK.sim(1);
    return { g1: g1, g2: g2, g3: T.q().help.guide, dc: SK.State.coins - c0, maxGap: +maxGap.toFixed(1) }; });
  check(r.g1 === 1 && r.g2 === 2 && r.g3 === 3 && r.dc >= 80, 'guide job: accept, meet Leo, he follows down Snowdrop, +80 (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { SK.QX.FALLEN; T.op('q:guide'); return T.q().help.guide; });
  check(r === 3, 'guide job only once a day', fails);
  /* leaving the kid behind resets the job */
  r = await ev(() => { const H = T.q().help; H.guide = 1; const K = SK.QX.KID; K.wait = 0; K.x = -33.2; K.z = 2.6; T.go(K.x + 2, K.z); const a = H.guide; T.go(-29, 40); for (let i = 0; i < 100; i++) SK.sim(1); const b = H.guide; H.guide = 3; return [a, b]; });
  check(r[0] === 2 && r[1] === 1, 'running off leaves Leo behind: job goes back to "meet" (' + r + ')', fails);

  /* --- Ski School lessons gate trick points --- */
  r = await ev(() => { const e = { name: '540', air: 1.4, spin: 540, flips: 0, flipDir: 0, grab: 0, bailed: false, clean: true }; const before = SK.trickPoints(e);
    SK.State.coins += 1000; T.go(-4, 40.5); T.act(); T.op('q:lesson:spin540'); const les = T.q().lesson; T.close();
    SK.events.emit('trick', Object.assign({ x: 0, z: 0 }, e)); SK.sim(2); const after = SK.trickPoints(e);
    const f = { name: 'Backflip', air: 1.3, spin: 0, flips: 1, flipDir: -1, grab: 0, bailed: false, clean: false };
    return { before: before, after: after, les: les, learned: T.q().learned.slice(), flipNo: SK.trickPoints(f) }; });
  check(r.les === 'spin540' && r.learned.indexOf('spin540') >= 0 && r.after - r.before === Math.round(80 * 1.25), '540 lesson: booked, passed by landing one, pays 200 not 120 (' + r.before + ' -> ' + r.after + ')', fails);
  check(r.flipNo === 50 + Math.round(60 * 1.3), 'unlearned backflip pays no flip bonus (' + r.flipNo + ')', fails);
  r = await ev(() => { T.go(-4, 40.5); T.act(); T.op('q:lesson:spin720'); const a = T.q().lesson; T.op('q:lesson:backflip'); const b = T.q().lesson; T.close(); return [a, b]; });
  check(r[0] === 'spin720' && r[1] === 'spin720', 'one lesson at a time', fails);

  /* --- chapter 5: 10 medals, gold on Crest Couloir, Gold Edition skis --- */
  r = await ev(() => { const st = T.q().chP.s; SK.State.best.ribbon = { score: 9000, tier: 3, runs: 1 }; SK.State.medals = SK.State.best.ribbon.tier + 8 + (SK.State.best.bunny ? SK.State.best.bunny.tier : 0); SK.sim(12);
    const s2 = T.q().chP.s, C = SK.liftLock('C'); SK.State.best.couloir = { score: 9999, tier: 3, runs: 1 }; const c0 = SK.State.coins; SK.sim(12);
    return { st: st, s2: s2, C: C, ch: T.q().ch, dc: SK.State.coins - c0, skis: SK.State.gear.skis, owned: SK.State.owned.indexOf('gold') >= 0 }; });
  check(r.st === 0 && r.s2 === 1 && r.C === null && r.ch === 5 && r.dc === 1000 && r.skis === 'gold' && r.owned, 'chapter 5: Crest Lift at 10 medals, gold on Couloir, +1000 and Gold Edition skis (' + JSON.stringify(r) + ')', fails);

  /* --- daily board: force a speed-trap quest and complete it --- */
  r = await ev(() => { const q = T.q(); q.daily.list[0] = { k: 'trap', p: { v: 60 }, s: 0, n: 0, done: false }; const c0 = SK.State.coins; SK.events.emit('trap', { kmh: 58, pts: 50 }); const d1 = q.daily.list[0].done; SK.events.emit('trap', { kmh: 63, pts: 100 }); SK.sim(1); return { d1: d1, d2: q.daily.list[0].done, dc: SK.State.coins - c0 }; });
  check(!r.d1 && r.d2 && r.dc === 110, 'board quest: 58 km/h is not enough, 63 km/h completes +110', fails);
  await ev(() => { T.go(-26, 55); T.act(); });
  await page.waitForTimeout(300); await page.screenshot({ path: OUT + '/q-board.png' });
  await ev(() => { T.close(); SK.ECONX.openSheet('log', null, null); SK.sim(2); });
  await page.waitForTimeout(300); await page.screenshot({ path: OUT + '/q-log.png' });
  await ev(() => T.close());

  /* --- lift gate: a locked lift keeps your ticket --- */
  r = await ev(() => { const q = T.q(); const save = SK.State.best; SK.State.best = {}; q.ch = 0; SK.State.medals = 0; SK.sim(1);
    const L = SK.LIFTS.find(l => l.id === 'B'), c = Math.cos(L.ry), s = Math.sin(L.ry), W2 = (x, z) => [L.xs + x * c + z * s, L.zs - x * s + z * c];
    const k = W2(-2.4, 12.9); T.go(k[0], k[1]); const kiosk = SK.LIFTX.nearby(SK.W.x, SK.W.z);
    SK.LIFTX.state.ticket = true; const g = W2(0, L.st0.userData.gateZ + 1.2); T.go(g[0], g[1]); SK.sim(20); const kept = SK.LIFTX.state.ticket;
    q.ch = 5; SK.State.best = save; SK.State.medals = 99; SK.sim(1); T.go(g[0], g[1]); SK.sim(20); const used = !SK.LIFTX.state.ticket;
    SK.State.medals = -1; SK.sim(1); return { kiosk: kiosk && kiosk.text, kept: kept, used: used }; });
  check(/opens at 3 medals/.test(r.kiosk) && r.kept && r.used, 'Lodge Lift locked at 0 medals (kiosk: ' + r.kiosk + '), ticket kept; opens once you have the medals', fails);

  /* --- save round trip + new day --- */
  r = await ev(() => { let err = ''; try { JSON.stringify(SK.State); } catch (e) { err = e.message; } return { ok: SK.save(), err: err }; });
  check(r.ok && !r.err, 'save writes (' + r.err + ')', fails);
  await page.reload(); await page.waitForFunction(() => window.SK && SK.State && SK.State.q && !document.getElementById('load'), null, { timeout: 180000 });
  r = await ev(() => ({ ch: SK.State.q.ch, learned: SK.State.q.learned, done: SK.State.q.done, medals: SK.State.medals, skis: SK.State.gear.skis, guide: SK.State.q.help.guide, cocoa: SK.State.q.help.cocoa.length }));
  check(r.ch === 5 && r.learned.indexOf('spin540') >= 0 && r.done.indexOf('mitten') >= 0 && r.skis === 'gold' && r.guide === 3 && r.cocoa === 1, 'save round trip keeps quest state (' + JSON.stringify(r) + ')', fails);
  check(r.medals === Object.values(await ev(() => SK.State.best)).reduce((a, b) => a + b.tier, 0) + 10, 'medals = trail tiers + 2 per chapter after reload (' + r.medals + ')', fails);
  r = await ev(() => new Promise(res => { const d0 = SK.State.day; SK.ECONX.newDay(); setTimeout(() => { SK.sim(2); const q = SK.State.q; res({ day: SK.State.day, d0: d0, dd: q.daily.day, hd: q.help.day, guide: q.help.guide, cocoa: q.help.cocoa.length, n: q.daily.list.length }); }, 900); }));
  check(r.day === r.d0 + 1 && r.dd === r.day && r.hd === r.day && r.guide === 0 && r.cocoa === 0 && r.n === 3, 'new day re-rolls the board and helping (' + JSON.stringify(r) + ')', fails);
  /* garbage in the save never breaks the boot */
  r = await ev(() => { const raw = JSON.parse(localStorage.getItem('powderpass.save')); raw.q = { ch: 'x', act: { mitten: 5, bogus: {} }, daily: { day: 3, list: [{ k: 'nope' }, null] }, help: { day: 'a' }, learned: [1, 'spin540'] }; localStorage.setItem('powderpass.save', JSON.stringify(raw)); Storage.prototype.setItem = function () {}; return 1; });   /* the old page's pagehide autosave must not overwrite it */
  await page.reload(); await page.waitForFunction(() => window.SK && SK.State && SK.State.q && !document.getElementById('load'), null, { timeout: 180000 });
  r = await ev(() => ({ ch: SK.State.q.ch, act: Object.keys(SK.State.q.act), n: SK.State.q.daily.list.length, learned: SK.State.q.learned }));
  check(r.ch === 0 && r.act.join() === 'mitten' && r.n === 3 && r.learned.join() === 'spin540', 'corrupt quest save is cleaned up (' + JSON.stringify(r) + ')', fails);

  check(errors.length === 0, 'no errors ' + errors.join(' | '), fails);
  await browser.close(); console.log(fails.length ? 'FAILED ' + fails.length : 'ALL OK'); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
