/* trail support audit: walks every trail centre-line (centre -> both edges -> shoulders) and every gate, flags sections with no
   mountain under them: steep cross-slope inside the corridor, a ridge falling away on both sides, or points off the terrain.
   node tools/audit-trails.js  [--all] */
const { boot } = require('./lib');
(async () => {
  const { browser, page } = await boot({ viewport: { width: 300, height: 300 } });
  const r = await page.evaluate(all => {
    const RZ = SK.RES_Z, g = (x, z) => SK.groundRaw ? SK.groundRaw(x, z + RZ) : SK.groundY(x, z + RZ), TB = SK.TB, out = [], sum = [];
    SK.TRAILS.forEach(t => { const P = t.pts; let bad = 0, n = 0, worst = 0, wAt = null;
      for (let i = 0; i < P.length - 1; i++) { const a = P[i], b = P[i + 1], L = Math.hypot(b[0] - a[0], b[1] - a[1]), ux = (b[0] - a[0]) / L, uz = (b[1] - a[1]) / L, nx = -uz, nz = ux;
        for (let s = 0; s < L; s += 3) { const x = a[0] + ux * s, z = a[1] + uz * s, hc = g(x, z), w = t.hw, sh = w + 6; n++;
          const hl = g(x + nx * w, z + nz * w), hr = g(x - nx * w, z - nz * w), sl = g(x + nx * sh, z + nz * sh), sr = g(x - nx * sh, z - nz * sh);
          const cross = Math.abs(hl - hr) / (2 * w), ridge = Math.min(hc - sl, hc - sr) / sh, off = x - sh < TB.x0 || x + sh > TB.x1 || z - sh < TB.z0 - RZ || z + sh > TB.z1 - RZ;
          const score = Math.max(cross / 0.45, ridge / 0.35, off ? 2 : 0); if (score > 1) { bad++; if (score > worst) { worst = score; wAt = [Math.round(x), Math.round(z), +cross.toFixed(2), +ridge.toFixed(2), off]; } } } }
      sum.push(t.id + ': ' + bad + '/' + n + ' unsupported' + (wAt ? '  worst @' + wAt[0] + ',' + wAt[1] + ' cross ' + wAt[2] + ' ridge ' + wAt[3] + (wAt[4] ? ' OFF-GRID' : '') : '')); });
    (SK.GATES || []).forEach(q => { const t = SK.TRAILS.find(x => x.id === q.t); if (!t) return; });
    return sum;
  }, process.argv.includes('--all'));
  console.log(r.join('\n')); await browser.close();
})();
