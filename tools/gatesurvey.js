/* overhead + ground shots of every trail start and finish gate: tools/out/gates-*.png (contact sheet) */
const { boot, OUT } = require('./lib');
const { execSync } = require('child_process');
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: 360, height: 360 } });
  await page.addStyleTag({ content: '*{transition:none!important} #gh,#ctl,#qt,#hint,#toast,#ptags{display:none!important}' });
  const G = await page.evaluate(() => { const L = []; SK.ED.UNITS.forEach(o => { if (o.userData.gt && !o.userData.edGone) L.push({ k: o.userData.gt.slice(0, 2).join('-'), x: o.position.x, z: o.position.z, ry: o.rotation.y }); }); return L; });
  const files = [];
  for (const g of G) {
    await page.evaluate(g => { SK.W.on = false; SK.snap({ tx: g.x, ty: SK.groundY(g.x, g.z + SK.RES_Z), tz: g.z + SK.RES_Z, r: 34, theta: g.ry + Math.PI, phi: 0.9 }); }, g);
    await page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
    const f = `${OUT}/gates-${g.k}.png`; await page.screenshot({ path: f }); files.push([f, g.k]);
  }
  require('fs').writeFileSync(OUT + '/gates.json', JSON.stringify(files));
  console.log(G.length + ' gates', errors.join('|') || 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
