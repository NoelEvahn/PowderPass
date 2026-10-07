/* orbit-camera screenshots for visual QA: node tools/view.js "[[name, x, zLocal, r, thetaDeg, phiDeg, minutes]]"  (phi: 0 = overhead, 90 = horizontal) -> tools/out/v-name.png */
const { boot, OUT } = require('./lib');
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: +process.env.PP_W || 1000, height: +process.env.PP_H || 600 } });
  const list = JSON.parse(process.argv[2]);
  await page.evaluate(() => { SK.W.on = false; document.body.classList.add('ed'); });
  for (const [n, x, z, r, th, ph, min, y] of list) {
    await page.evaluate(([x, z, r, th, ph, min, y]) => { if (min != null) SK.P6.setTime(min); const gy = y != null ? y : SK.groundY(x, z + SK.RES_Z); SK.snap({ tx: x, ty: gy, tz: z + SK.RES_Z, r: r, theta: th * Math.PI / 180, phi: ph * Math.PI / 180 }); SK.P6.lightsFor(); }, [x, z, r, th, ph, min, y]);
    await page.waitForTimeout(900); await page.screenshot({ path: `${OUT}/v-${n}.png` }); console.log('view', n);
  }
  console.log(errors.join('\n') || 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
