/* asset-key stability: node tools/keycheck.js dump <html> <out.json> | node tools/keycheck.js cmp <a.json> <b.json>
   Saved editor edits are keyed by build order, so a world change must not shift keys (or must be checked against them). */
const fs = require('fs');
const [, , cmd, a, b] = process.argv;
if (cmd === 'cmp') {
  const A = JSON.parse(fs.readFileSync(a)), B = JSON.parse(fs.readFileSync(b)); let bad = 0, n = 0;
  Object.keys(A).forEach(k => { n++; const x = A[k], y = B[k]; if (!y || x.n !== y.n || Math.hypot(x.x - y.x, x.z - y.z) > 0.5) { bad++; if (bad < 15) console.log('changed', k, JSON.stringify(x), '->', JSON.stringify(y)); } });
  const moved = Object.keys(A).filter(k => B[k] && A[k].g !== undefined && Math.abs(A[k].g - B[k].g) > 0.25 && Math.abs(B[k].y - B[k].g) < 3).map(k => k + ' ' + A[k].n + ' ground ' + A[k].g + ' -> ' + B[k].g + ' (obj y ' + B[k].y + ')');
  if (moved.length) console.log('ground moved under ' + moved.length + ' assets:\n  ' + moved.join('\n  '));
  console.log(n + ' keys, ' + bad + ' changed'); process.exit(bad ? 1 : 0);
}
const { boot } = require('./lib');
(async () => {
  const { browser, page } = await boot({ file: a, viewport: { width: 300, height: 300 } });
  const m = await page.evaluate(() => { const o = {}; SK.ED.UNITS.forEach((u, k) => { const r = u.userData.ed0, p = r ? r.p : u.position; o[k] = { n: SK.ED.nameOf(u), x: +p.x.toFixed(2), z: +p.z.toFixed(2), y: +p.y.toFixed(2), g: +SK.groundY(p.x, p.z + SK.RES_Z).toFixed(2) }; }); return o; });
  fs.writeFileSync(b, JSON.stringify(m)); console.log(Object.keys(m).length + ' keys'); await browser.close();
})();
