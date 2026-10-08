/* screenshots from named viewpoints: node tools/shots.js '[["name", x, z, lookX, lookZ, pitch], ...]' [WxH] [top]
   x/z are resort-local; first person on foot. "top" entries: ["name", "top", x, z, radius] give an overhead orbit view */
const { boot, OUT } = require('./lib');
(async () => {
  const V = JSON.parse(process.argv[2]), sz = (process.argv[3] || '844x390').split('x').map(Number);
  const { browser, page, errors } = await boot({ viewport: { width: sz[0], height: sz[1] } });
  await page.addStyleTag({ content: '*{transition:none!important}' });
  for (const v of V) {
    if (v[1] === 'top') await page.evaluate(([x, z, r]) => { SK.W.on = false; SK.snap({ tx: x, ty: SK.groundY(x, z + SK.RES_Z), tz: z + SK.RES_Z, r: r, theta: 0, phi: 0.02 }); }, [v[2], v[3], v[4]]);
    else await page.evaluate(([x, z, tx, tz, p, y6, tm6]) => { const W = SK.W; W.on = true; SK.ECONX.closeSheet(); W.x = x; W.z = z + SK.RES_Z; W.y = SK.groundY(W.x, W.z) + (y6 || 0); if (tm6 !== undefined && SK.P6) SK.P6.setTime(tm6); W.yaw = Math.atan2(-(tx - x), -(tz - z)); W.pitch = p || 0; W.vx = W.vz = 0; SK.sim(12); }, v.slice(1));
    await page.waitForTimeout(1500); await page.screenshot({ path: OUT + '/s-' + v[0] + '.png' }); console.log('shot', v[0]);
  }
  console.log(errors.length ? 'ERRORS ' + errors.join(' | ') : 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
