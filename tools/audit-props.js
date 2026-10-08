/* world audit: vegetation / exterior props that overlap buildings. Prints offenders (resort-local x/z). node tools/audit-props.js */
const { boot } = require('./lib');
(async () => {
  const { browser, page } = await boot({ viewport: { width: 300, height: 300 } });
  const r = await page.evaluate(() => {
    const VEG = /bush|pine|tree|boulder|rock|snow pile|snowpile|grass|stump|log pile|dead tree|plant/i, RZ = SK.RES_Z, B = new THREE.Box3(), out = [], blds = [], props = [];
    SK.ED.UNITS.forEach((u, k) => { const n = SK.ED.nameOf(u) || ''; u.updateMatrixWorld(true); B.setFromObject(u); if (B.isEmpty()) return; const s = B.getSize(new THREE.Vector3());
      const e = { k, n, x0: B.min.x, x1: B.max.x, z0: B.min.z - RZ, z1: B.max.z - RZ, y0: B.min.y, y1: B.max.y };
      if (VEG.test(n)) props.push(e); else if (s.x * s.z > 18 && s.x * s.z < 1500 && s.y > 2.4 && B.min.y < 120 && !/trail|piste|lift|tower|cable|gate|fence|rail|ramp|pipe|terrain|path|sign|banner|light|lamp|bridge/i.test(n)) blds.push(e); });
    const ins = (p, b, m) => p.x0 + m < b.x1 && p.x1 - m > b.x0 && p.z0 + m < b.z1 && p.z1 - m > b.z0;
    props.forEach(p => blds.forEach(b => { if (ins(p, b, 0.3)) out.push(p.n + ' ' + p.k + ' @' + ((p.x0 + p.x1) / 2).toFixed(1) + ',' + ((p.z0 + p.z1) / 2).toFixed(1) + '  in  ' + b.n + ' ' + b.k); }));
    (SK.TREES || []).forEach(t => blds.forEach(b => { if (t[0] > b.x0 + 0.5 && t[0] < b.x1 - 0.5 && t[1] > b.z0 + 0.5 && t[1] < b.z1 - 0.5) out.push('forest pine @' + t[0].toFixed(1) + ',' + t[1].toFixed(1) + '  in  ' + b.n + ' ' + b.k); }));
    return { out, nb: blds.length, np: props.length, names: [...new Set(blds.map(b => b.n))].join(', ') };
  });
  console.log(r.nb + ' buildings, ' + r.np + ' props\nbuildings: ' + r.names + '\n' + (r.out.join('\n') || 'no overlaps')); await browser.close();
})();
