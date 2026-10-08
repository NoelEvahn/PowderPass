/* first-person viewmodel shots: node tools/fpshot.js [names...] -> tools/out/fp-<name>.png
   states: air (skis in the air), grab, flip (mid front flip), crash, plate (holding a breakfast plate), bite (eating) */
const { boot, OUT } = require('./lib');
const STATES = {
  air: `PL.y += 7; PL.vy = 5; PL.air = true; PL.airT = 0.3; PL.flip = 0; PL.spin = 0; SK.play(2);`,
  grab: `PL.y += 7; PL.vy = 5; PL.air = true; PL.airT = 0.3; PL.grabT = 0.4; SK.keys.shift = 1; SK.play(2);`,
  flip: `PL.y += 7; PL.vy = 5; PL.air = true; PL.airT = 0.3; PL.flip = 2.2; SK.play(1);`,
  crash: `PL.crash = 1.6; PL.crashAnim = 'Crash tumble'; SK.play(2);`,
  plate: `SK.PL.exitSki(); SK.State.cam.walk = 'first'; SK.VM.showPlate(['eggs', 'bacon', 'fruit']); SK.play(2);`,
  bite: `SK.PL.exitSki(); SK.State.cam.walk = 'first'; SK.VM.consume('croissant', null, 3); SK.play(9);`
};
(async () => {
  const names = process.argv.slice(2).length ? process.argv.slice(2) : Object.keys(STATES);
  const { browser, page, errors } = await boot({ viewport: { width: +process.env.PP_W || 1100, height: +process.env.PP_H || 700 } });
  for (const n of names) {
    await page.evaluate(src => { const PL = SK.PL, W = SK.W; SK.keys.shift = 0; if (PL.mode === 'ski') PL.exitSki(); W.sit = null; W.x = -30; W.z = SK.RES_Z + 52; W.y = SK.groundY(W.x, W.z); W.yaw = 0.3; W.pitch = -0.1; SK.play(3);
      PL.tryToggle(); SK.State.cam.ski = 'first'; SK.play(3); PL.vx = -1; PL.vz = -6; new Function('PL', src)(PL); }, STATES[n]);
    await page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
    await page.screenshot({ path: `${OUT}/fp-${n}.png` }); console.log('shot', n);
    await page.evaluate(() => { SK.keys.shift = 0; SK.VM.clear(); });
  }
  console.log(errors.join('\n') || 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
