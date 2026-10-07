/* trail sanity: flat / uphill stretches, trees and collision boxes inside the corridor, start on a building */
const { boot } = require('./lib');
(async () => {
  const { browser, page } = await boot({ viewport: { width: 300, height: 300 } });
  const r = await page.evaluate(() => {
    const out = [];
    SK.TRAILS.filter(t => t.grade !== 'conn').forEach(t => {
      const g = (x, z) => SK.groundY(x, z + SK.RES_Z), flats = []; let run = null;
      for (let s = 0; s < t.len - 4; s += 2) { const a = SK.P8.pointAt(t, s), b = SK.P8.pointAt(t, s + 4), dr = (g(a.x, a.z) - g(b.x, b.z)) / 4;
        if (dr < 0.03) { if (!run) run = { s0: s, min: dr }; run.s1 = s + 4; run.min = Math.min(run.min, dr); } else if (run) { if (run.s1 - run.s0 >= 8) flats.push(run); run = null; } }
      if (run && run.s1 - run.s0 >= 8) flats.push(run);
      const trees = []; SK.TREES.forEach(p => { let bd = 1e9, bs = 0; for (let s = 0; s < t.len; s += 2) { const q = SK.P8.pointAt(t, s), d = Math.hypot(q.x - p[0], q.z - p[1]); if (d < bd) { bd = d; bs = s; } } if (bd < t.hw * 0.8) trees.push(Math.round(bs) + '@' + bd.toFixed(1)); });
      const sol = []; SK.SOLIDS.forEach(k => { if (k.off || k.hw > 3) return; const x = k.x, z = k.z - SK.RES_Z; if (x < t.bx0 || x > t.bx1 || z < t.bz0 || z > t.bz1) return; let bd = 1e9, bs = 0; for (let s = 6; s < t.len - 6; s += 2) { const q = SK.P8.pointAt(t, s), d = Math.hypot(q.x - x, q.z - z); if (d < bd) { bd = d; bs = s; } } if (bd < t.hw * 0.6) sol.push(Math.round(bs) + '@' + bd.toFixed(1)); });
      const drop = g(t.pts[0][0], t.pts[0][1]) - g(t.pts[t.pts.length - 1][0], t.pts[t.pts.length - 1][1]);
      out.push(t.id + ' len ' + Math.round(t.len) + ' drop ' + drop.toFixed(0) + ' (' + (drop / t.len * 100).toFixed(0) + '%) flats: ' + (flats.map(f => f.s0 + '-' + f.s1 + '(' + (f.min * 100).toFixed(0) + '%)').join(' ') || '-') + ' | trees in corridor: ' + (trees.join(' ') || '-') + ' | boxes: ' + (sol.slice(0, 12).join(' ') || '-'));
    });
    return out.join('\n');
  });
  console.log(r); await browser.close();
})();
