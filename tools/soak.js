/* soak test: ~20 min of game time with random play (walk, ski, lifts, F at whatever is near, sheets, map, editor, time + weather jumps), then a save round trip.
   Any page error fails it. node tools/soak.js [minutes] [seed] */
const { boot, check } = require('./lib');
const MIN = +(process.argv[2] || 20), SEED = +(process.argv[3] || 7);
(async () => {
  const fails = [];
  const { browser, page, errors } = await boot({ viewport: { width: 390, height: 780 } });
  const log = await page.evaluate(([MIN, SEED]) => {
    let r = SEED; const rnd = () => (r = (r * 16807) % 2147483647) / 2147483647, pick = a => a[Math.floor(rnd() * a.length)];
    const W = SK.W, PL = SK.PL, K = SK.keys, out = { acts: {}, steps: 0, rides: 0, runs: 0 };
    SK.events.on('runend', () => out.runs++);
    const spots = () => { const L = []; SK.TRAILS.forEach(t => { if (t.grade !== 'conn') L.push(['trail', t]); }); Object.keys(SK.ECONX.SHOPS).forEach(k => L.push(['shop', SK.ECONX.SHOPS[k]])); SK.LIFTS.forEach(l => L.push(['lift', l])); return L; };
    const S = spots(), end = 30 * 60 * MIN;
    const did = k => { out.acts[k] = (out.acts[k] || 0) + 1; };
    while (out.steps < end) {
      const a = pick(['ski', 'ski', 'walk', 'shop', 'lift', 'map', 'sheet', 'editor', 'time', 'weather', 'bag']);
      did(a);
      try {
        if (SK.ECONX) SK.ECONX.closeSheet(); if (SK.MAP && SK.MAP.open) SK.MAP.show(false);
        if (a === 'ski') { const t = pick(S.filter(s => s[0] === 'trail'))[1], s0 = rnd() * t.len * 0.5, p = SK.P8.pointAt(t, s0), p1 = SK.P8.pointAt(t, s0 + 4); if (PL.mode === 'ski') PL.exitSki(); W.ride = null;
          W.x = p.x; W.z = p.z + SK.RES_Z; W.y = SK.groundY(W.x, W.z); W.yaw = Math.atan2(-(p1.x - p.x), -(p1.z - p.z)); W.vx = W.vz = 0; SK.play(2); PL.tryToggle();
          for (let i = 0; i < 30 * 12; i++) { K.a = rnd() < 0.3; K.d = !K.a && rnd() < 0.3; K.w = rnd() < 0.4; K.s = rnd() < 0.1; K[' '] = rnd() < 0.08; K.shift = rnd() < 0.05; SK.play(1); out.steps++; } }
        else if (a === 'walk') { if (PL.mode === 'ski') PL.exitSki(); W.x = -60 + rnd() * 120; W.z = SK.RES_Z + 20 + rnd() * 70; W.y = SK.groundY(W.x, W.z); W.yaw = rnd() * 6.28;
          for (let i = 0; i < 30 * 8; i++) { K.w = 1; K.a = rnd() < 0.1; K.d = rnd() < 0.1; K.shift = rnd() < 0.3; if (i % 40 === 0) { SK.LIFTX.act(W.x, W.z) || SK.ECON.act(W.x, W.z); } SK.play(1); out.steps++; } }
        else if (a === 'shop') { const sh = pick(S.filter(s => s[0] === 'shop'))[1]; if (sh.at) { if (PL.mode === 'ski') PL.exitSki(); W.x = sh.at[0] + rnd() * 2 - 1; W.z = sh.at[1] + SK.RES_Z + rnd() * 2 - 1; W.y = SK.groundY(W.x, W.z); SK.play(3); SK.ECON.act(W.x, W.z); SK.play(20); out.steps += 23;
          const b = [...document.querySelectorAll('#sheet:not(.off) .sh-b button')].filter(x => !x.disabled); if (b.length) { pick(b).click(); SK.play(5); } } }
        else if (a === 'lift') { const L = pick(S.filter(s => s[0] === 'lift'))[1]; if (PL.mode === 'ski') PL.exitSki(); SK.LIFTX.state.ticket = true; W.x = L.xs; W.z = L.zs + 9 + SK.RES_Z; W.y = SK.groundY(W.x, W.z); W.yaw = 0;
          for (let i = 0; i < 30 * 30 && !W.ride; i++) { const c = L.cars.find(c => c.userData.open > 0.5); if (c) { const p = L.toWorld(c, SK.LIFTX.door); W.yaw = Math.atan2(-(p.x - W.x), -(p.z - W.z)); K.w = 1; } SK.play(1); out.steps++; }
          K.w = 0; if (W.ride) { out.rides++; for (let i = 0; i < 30 * 25; i++) { SK.play(1); out.steps++; } if (W.ride && SK.LIFTX.leaveInfo(W.ride)) W.leave(); } }
        else if (a === 'map') { SK.MAP.show(true); SK.MAP.pickAt(100 + rnd() * 500, 100 + rnd() * 900); SK.MAP.draw(); SK.MAP.show(false); }
        else if (a === 'sheet') { document.getElementById(pick(['bBag', 'bQst', 'bSet'])).click(); const b = [...document.querySelectorAll('#sheet:not(.off) .sh-b button')].filter(x => !x.disabled && !/sound|reset|delete/i.test(x.textContent)); if (b.length && rnd() < 0.5) pick(b).click(); SK.play(10); out.steps += 10; }
        else if (a === 'bag') { const b = [...document.querySelectorAll('#sheet:not(.off) button')]; if (b.length) pick(b).click(); }
        else if (a === 'editor') { if (W.on && !W.ride) { SK.ED.open(); const u = [...SK.ED.UNITS.values()]; SK.ED.select(pick(u)); SK.ED.S.parts = rnd() < 0.5; SK.ED.S.dirty = false; SK.play(5); window.confirm = () => true; SK.ED.close(); out.steps += 5; } }
        else if (a === 'time') { SK.P6.setTime(rnd() * 24 * 60); SK.play(30); out.steps += 30; }
        else if (a === 'weather') { SK.State.day = 1 + Math.floor(rnd() * 60); SK.P7.wxApply(); SK.play(30); out.steps += 30; }
      } catch (e) { out.err = (out.err || []).concat(a + ': ' + e.message); if (out.err.length > 5) break; }
      K.a = K.d = K.w = K.s = 0; K[' '] = 0; K.shift = 0;
    }
    out.coins = SK.State.coins; out.day = SK.State.day; out.medals = SK.State.medals; return out;
  }, [MIN, SEED]);
  console.log(JSON.stringify(log));
  check(!log.err, 'no exceptions in actions ' + (log.err || []).join(' | '), fails);
  /* save round trip after all that */
  const sv = await page.evaluate(() => { SK.writeSave && SK.writeSave(); const raw = localStorage.getItem('powderpass.save'); try { JSON.parse(raw); return raw.length; } catch (e) { return -1; } });
  check(sv > 100, 'save still valid JSON (' + sv + ' bytes)', fails);
  check(errors.length === 0, 'no page errors ' + errors.slice(0, 5).join(' | '), fails);
  await browser.close(); console.log(fails.length ? 'FAILED ' + fails.length : 'ALL OK'); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
