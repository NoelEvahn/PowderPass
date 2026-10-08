/* foundation audit: every building-sized object and every lift station / tower. Samples the ground under its footprint and flags
   floating (gap under the base) or sunk (base far below the ground at the uphill side). node tools/audit-found.js */
const { boot } = require('./lib');
(async () => {
  const { browser, page } = await boot({ viewport: { width: 300, height: 300 } });
  const r = await page.evaluate(() => {
    const B = new THREE.Box3(), out = [], g = (x, z) => SK.groundRaw(x, z), v = new THREE.Vector3();
    const check = (o, n) => { o.updateMatrixWorld(true); B.setFromObject(o); if (B.isEmpty()) return; const sx = B.max.x - B.min.x, sz = B.max.z - B.min.z; if (sx * sz < 4 || sx * sz > 1500 || B.min.y > 400) return;
      let gmin = 1e9, gmax = -1e9; for (let i = 0; i <= 4; i++) for (let j = 0; j <= 4; j++) { const x = B.min.x + sx * (0.12 + 0.76 * i / 4), z = B.min.z + sz * (0.12 + 0.76 * j / 4), h = g(x, z); gmin = Math.min(gmin, h); gmax = Math.max(gmax, h); }
      const float = B.min.y - gmin, sunk = gmax - B.min.y; if (float > 0.6 || sunk > 2.2) out.push(n + ' @' + ((B.min.x + B.max.x) / 2).toFixed(0) + ',' + ((B.min.z + B.max.z) / 2 - SK.RES_Z).toFixed(0) + ' float ' + float.toFixed(2) + ' sunk ' + sunk.toFixed(2)); };
    SK.ED.UNITS.forEach((u, k) => { const n = SK.ED.nameOf(u) || ''; if (/tree|pine|bush|rock|cliff|cloud|sun|terrain|forest|outer|mountain|path|snow|label|gate|lamp|sign/i.test(n)) return; check(u, n + ' ' + k); });
    (SK.LIFTS || []).forEach(L => { const root = L.g || L.group; if (!root) return; root.traverse(o => { if (o.userData && (o.userData.tower || o.userData.station)) check(o, 'lift ' + L.id + (o.userData.tower ? ' tower' : ' station')); }); });
    return out;
  });
  console.log(r.join('\n') || 'all foundations ok'); await browser.close();
})();
