/* trick playtest: ski at the Air Line kickers, charge the jump, release at the lip, spin (+grab) in the air, log what the game awarded */
const { boot, OUT } = require('./lib');
const plans = [['straight', 0, 0], ['360', 1, 0], ['360 grab', 1, 1], ['540', 1, 0, 1.6], ['backflip', 0, 0, 0, 1]];
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: 390, height: 780 } });
  await page.addStyleTag({ content: '*{transition:none!important}' });
  for (let k = 0; k < plans.length; k++) {
    const [name, spin, grab, spinMul, flip] = plans[k];
    const r = await page.evaluate(([ki, spin, grab, spinMul, flip]) => {
      const K = SK.keys, PL = SK.PL, W = SK.W, KI = [[-65.33, -126, 0.316, 0.949], [-62.0, -116, 0.316, 0.949], [-60.88, -103, -0.124, 0.992], [-62.13, -93, -0.124, 0.992]][ki % 2];
      if (PL.mode === 'ski') PL.exitSki(); const sx = KI[0] - KI[2] * 34, sz = KI[1] - KI[3] * 34;
      W.x = sx; W.z = sz + SK.RES_Z; W.y = SK.groundY(W.x, W.z); W.yaw = Math.atan2(-KI[2], -KI[3]); W.vx = W.vz = 0; SK.play(3); PL.tryToggle(); SK.play(2);
      const out = { pops: [], maxAir: 0, crash: 0, spinDeg: 0, airT: 0, approachKmh: 0 }; let state = 'approach', t0 = 0, lastPop = '';
      for (let i = 0; i < 30 * 14; i++) {
        const lx = PL.x, lz = PL.z - SK.RES_Z, u = (lx - KI[0]) * KI[2] + (lz - KI[1]) * KI[3], v = -(lx - KI[0]) * KI[3] + (lz - KI[1]) * KI[2];
        K.a = K.d = K.w = K.s = 0; K[' '] = 0; K.shift = 0;
        if (state === 'approach') { const hx = PL.vx, hz = PL.vz; if (v > 0.25) K.a = 1; else if (v < -0.25) K.d = 1; K.w = 1; if (u > -3.5) K[' '] = 1; if (u > -0.3 || PL.air) { state = PL.air ? 'air' : 'lip'; out.approachKmh = Math.round(PL.spd * 3.6); } }
        else if (state === 'lip') { if (PL.air) state = 'air'; else if (++t0 > 20) state = 'air'; }
        if (state === 'air' && PL.air) { out.airT += 1 / 30; if (spin || flip) K[' '] = 1;   /* tricks need jump held */ if (spin) { if (Math.abs(PL.spin) < (spinMul || 1) * 2 * Math.PI - 0.6) K.d = 1; } if (flip && Math.abs(PL.flip) < 2 * Math.PI - 0.6) K.s = 1; if (grab) K.shift = 1; }
        SK.play(1, 1 / 30);
        out.maxAir = Math.max(out.maxAir, PL.y - SK.groundY(PL.x, PL.z)); out.spinDeg = Math.max(out.spinDeg, Math.round(Math.abs(PL.spin) * 57.3));
        if (PL.crash > 0) out.crash = 1;
        const tr = document.getElementById('trick'); if (tr && !tr.classList.contains('off') && tr.textContent !== lastPop) { lastPop = tr.textContent; out.pops.push(tr.textContent); }
        if (state === 'air' && !PL.air && out.airT > 0.2 && i > 0) { for (let j = 0; j < 45; j++) { SK.play(1, 1 / 30); if (PL.crash > 0) out.crash = 1; const tr2 = document.getElementById('trick'); if (tr2 && !tr2.classList.contains('off') && tr2.textContent !== lastPop) { lastPop = tr2.textContent; out.pops.push(tr2.textContent); } } break; }
      }
      K.a = K.d = K.w = K.s = 0; K[' '] = 0; K.shift = 0; out.maxAir = +out.maxAir.toFixed(2); out.airT = +out.airT.toFixed(2); return out;
    }, [k, spin, grab, spinMul || 0, flip || 0]);
    console.log(name.padEnd(10), JSON.stringify(r));
    await page.screenshot({ path: `${OUT}/trick-${k}.png` });
  }
  console.log(errors.length ? 'ERRORS ' + errors.join(' | ') : 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
