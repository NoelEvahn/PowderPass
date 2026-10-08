/* autopilot playtest: ski trails with the real controls (A/D steer, W tuck, S brake), log the run and take phone screenshots on the way.
   node tools/playbot.js [trailId ...]   (default: every trail) -> tools/out/bot-<trail>-<n>.png + a summary line per run */
const { boot, OUT } = require('./lib');
const want = process.argv.slice(2);
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: 390, height: 780 } });
  await page.addStyleTag({ content: '*{transition:none!important}' });
  const ids = await page.evaluate(() => SK.TRAILS.filter(t => t.grade !== 'conn').map(t => t.id));
  const list = want.length ? want : ids;
  for (const id of list) {
    const r = await page.evaluate(id => {
      const t = SK.TRAILS.find(x => x.id === id), W = SK.W, PL = SK.PL, K = SK.keys, P8 = SK.P8;
      if (SK.ECONX) SK.ECONX.closeSheet(); if (PL.mode === 'ski') PL.exitSki();
      for (const s0 of [2, 8, 14, 20]) {   /* the very top can be a station deck: step down until skis go on, like a player would */
        const p0 = P8.pointAt(t, s0), p1 = P8.pointAt(t, s0 + 4); W.x = p0.x; W.z = p0.z + SK.RES_Z; W.y = SK.groundY(W.x, W.z); W.yaw = Math.atan2(-(p1.x - p0.x), -(p1.z - p0.z)); W.vx = W.vz = 0;
        SK.play(3); PL.tryToggle(); SK.play(3); if (PL.mode === 'ski') break; }
      if (PL.mode !== 'ski') return { id, err: 'could not put skis on' };
      window.__bot = { t, s: 0, log: [], maxSp: 0, crash: 0, off: 0, steps: 0, sign: 1, stuck: 0 };
      return { id, len: Math.round(t.len), grade: t.grade };
    }, id);
    if (r.err) { console.log(id, r.err); continue; }
    let shots = 0, done = null;
    for (let chunk = 0; chunk < 80 && !done; chunk++) {
      done = await page.evaluate(() => {
        const B = window.__bot, t = B.t, PL = SK.PL, K = SK.keys, RUN = SK.RUN;
        for (let i = 0; i < 30; i++) {
          const lx = PL.x, lz = PL.z - SK.RES_Z; let bs = 0, bd = 1e9;
          for (let s = Math.max(0, B.s - 30); s < Math.min(t.len, B.s + 60); s += 1) { const p = SK.P8.pointAt(t, s), d = Math.hypot(p.x - lx, p.z - lz); if (d < bd) { bd = d; bs = s; } }
          B.s = bs; const look = Math.min(t.len, bs + 9 + PL.spd * 0.8); let q = SK.P8.pointAt(t, look);
          const SL = SK.SLALOM, ng = SL && SL.t === t && SL.S.on ? SL.G[SL.S.gi] : null;   /* slalom: aim through the next gate */
          if (ng && ng.s - bs < 24 && ng.s - bs > -1) { const p = SK.P8.pointAt(t, ng.s + 2), lead = ng.off * 0.8; q = { x: p.x - p.dz * lead, z: p.z + p.dx * lead }; }
          const dx = q.x - lx, dz = q.z - lz;
          const vx = PL.vx, vz = PL.vz, sp = Math.hypot(vx, vz), hx = sp > 0.5 ? vx / sp : Math.sin(PL.hd), hz = sp > 0.5 ? vz / sp : Math.cos(PL.hd);
          const cr = (hx * dz - hz * dx) / (Math.hypot(dx, dz) || 1);   /* + = target is to the left/right depending on frame */
          K.a = K.d = K.w = K.s = 0; const st = cr * B.sign; if (st > 0.06) K.d = 1; else if (st < -0.06) K.a = 1;
          if (PL.spd > (t.grade === 'green' ? 11 : t.grade === 'blue' ? 15 : 18) || Math.abs(cr) > 0.5) K.s = 1; else if (PL.spd < 6 && Math.abs(cr) < 0.15) K.w = 1;
          if (PL.spd < 3.5) { K.s = 0; K.w = 1; }   /* skate out of flats like a player would */
          SK.play(1, 1 / 30); B.steps++;
          B.maxSp = Math.max(B.maxSp, PL.spd); if (PL.crash > 0 && !B.inCrash) { B.crash++; B.inCrash = 1; let nd = 1e9; SK.SOLIDS.forEach(k => { if (!k.off) nd = Math.min(nd, Math.hypot(k.x - PL.x, k.z - PL.z) - Math.max(k.hw, k.hd)); }); let td = 1e9; (SK.TREES || []).forEach(q => { td = Math.min(td, Math.hypot(q[0] - PL.x, q[1] - (PL.z - SK.RES_Z))); }); (B.cr = B.cr || []).push('s' + bs + ' ' + Math.round(PL.spd * 3.6) + 'kmh ' + (PL.crashAnim || '') + ' box' + nd.toFixed(1) + ' tree' + td.toFixed(1) + (PL.air ? ' AIR' : '')); } if (PL.crash <= 0) B.inCrash = 0;
          if (bd > t.hw + 4) B.off++;
          if (PL.spd < 0.3) B.stuck++; else B.stuck = 0;
          if (B.stuck > 300) { K.a = K.d = K.w = K.s = 0; return { end: 'stuck', s: bs }; }
          if (B.steps > 30 * 300) { K.a = K.d = K.w = K.s = 0; return { end: 'timeout', s: bs }; }
          if (!RUN.t && B.started) { K.a = K.d = K.w = K.s = 0; return { end: 'run ended', s: bs }; }
          if (RUN.t) B.started = 1;
          if (bs >= t.len - 3) { K.a = K.d = K.w = K.s = 0; return { end: 'bottom', s: bs }; }
        }
        return null;
      });
      if (chunk % 6 === 3 && shots < 4) { await page.screenshot({ path: `${OUT}/bot-${id}-${shots++}.png` }); }
    }
    const sum = await page.evaluate(() => { const B = window.__bot, res = document.getElementById('res'); return { steps: B.steps, secs: Math.round(B.steps / 30), maxKmh: Math.round(B.maxSp * 3.6), crashes: B.crash, crashAt: B.cr || [], offFrames: B.off, result: res && !res.classList.contains('off') ? res.innerText.replace(/\s+/g, ' ').slice(0, 220) : '', coins: SK.State.coins }; });
    await page.screenshot({ path: `${OUT}/bot-${id}-end.png` });
    console.log(JSON.stringify(Object.assign(r, done || { end: 'chunks' }, sum)));
  }
  console.log(errors.length ? 'ERRORS ' + errors.join(' | ') : 'no errors');
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
