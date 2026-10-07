/* ad-hoc screenshots: node tools/shot.js '<js setup returning [[name, x, y|null, z(world), yaw, pitch, minutes], ...]>'  -> tools/out/s-<name>.png
   y null = stand on the ground/floor there. Optional env PP_W/PP_H viewport. */
const { boot, OUT } = require('./lib');
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: +process.env.PP_W || 900, height: +process.env.PP_H || 600 } });
  await page.addStyleTag({ content: '*{transition:none!important}' });
  const list = await page.evaluate(src => (0, eval)(src), process.argv[2]);
  for (const [n, x, y, z, yaw, pitch, min, pre] of list) {
    await page.evaluate(([x, y, z, yaw, pitch, min, pre]) => { if (min !== undefined && min !== null) SK.P6.setTime(min); if (pre) (0, eval)(pre); const W = SK.W; W.x = x; W.z = z; W.y = y === null ? SK.groundY(x, z) : y; W.yaw = yaw; W.pitch = pitch; W.vx = W.vz = 0; SK.play(10); SK.P6.lightsFor(); SK.play(2); },
      [x, y, z, yaw, pitch, min, pre]);
    await page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
    await page.screenshot({ path: `${OUT}/s-${n}.png` }); console.log('shot', n);
  }
  console.log(errors.join('\n') || 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
