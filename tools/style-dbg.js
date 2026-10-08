/* art / lighting QA: the same set of player-level shots every time -> tools/out/st-<tag>-<name>.png
   node tools/style.js <tag> [names...]   (env PP_W / PP_H viewport, default 1280x720) */
const { boot, OUT } = require('./lib'); process.env.PP_DBG = 1;
const SHOTS = [   /* name, setup returning [x, y|null, z, yaw, pitch, minutes] (world coords) */
  ['base', `[-30, null, SK.RES_Z + 58, 0.15, 0.02, 600]`],
  ['plaza', `[-12, null, SK.RES_Z + 70, -0.6, 0.0, 600]`],
  ['forest', `[-62, null, SK.RES_Z - 40, 2.6, 0.05, 630]`],
  ['vista', `[12, null, -200 + 4, 0.2 + Math.PI, -0.12, 660]`],
  ['hotelx', `[SK.HOTEL.x + 3, null, SK.HOTEL.z + 17, 0.05, 0.12, 900]`],
  ['lobby', `[SK.HOTEL.x + 1.5, SK.HOTEL.gy + 0.23, SK.HOTEL.z + 1.5, 0.35, 0.05, 600]`],
  ['lobbyn', `[SK.HOTEL.x + 1.5, SK.HOTEL.gy + 0.23, SK.HOTEL.z + 1.5, 0.35, 0.05, 1290]`],
  ['streetn', `[-12, null, SK.RES_Z + 70, -0.6, 0.05, 1290]`],
  ['dusk', `[-30, null, SK.RES_Z + 58, 0.15, 0.02, 1050]`]
];
(async () => {
  const tag = process.argv[2] || 'x', only = process.argv.slice(3);
  const { browser, page, errors } = await boot({ viewport: { width: +process.env.PP_W || 1280, height: +process.env.PP_H || 720 } });
  await page.evaluate(() => { window.DBG = 1; }); await page.addStyleTag({ content: '*{transition:none!important} #ctl,#gh,#qt,#dock,#toast,#hint{display:none!important}' });
  for (const [n, src] of SHOTS) {
    if (only.length && only.indexOf(n) < 0) continue;
    await page.evaluate(src => { const [x, y, z, yaw, pitch, min] = (0, eval)(src), W = SK.W; SK.State.cam.walk = 'first'; if (SK.PL.mode === 'ski') SK.PL.exitSki();
      SK.P6.setTime(min); W.sit = null; W.x = x; W.z = z; W.y = y === null ? SK.groundY(x, z) : y; W.yaw = yaw; W.pitch = pitch; W.vx = W.vz = 0; SK.play(8); SK.P6.lightsFor(); SK.play(2); if (window.DBG) SK.POST.U.dbg.value = 1; }, src);
    await page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(() => requestAnimationFrame(r)))));
    await page.screenshot({ path: `${OUT}/st-${tag}-${n}.png` }); console.log('shot', n);
  }
  console.log(errors.join('\n') || 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
