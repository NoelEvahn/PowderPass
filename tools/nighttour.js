/* night + storm tour on a phone: village, hotel, lift base, piste, top; tools/out/nt-*.png and a contact sheet */
const { boot, OUT } = require('./lib');
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: 390, height: 780 } });
  await page.addStyleTag({ content: '*{transition:none!important} #toast{display:none!important}' });
  const fr = () => page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
  const spots = [['village', -14, 56, -14, 30], ['main-st', -4, 66, -40, 56], ['hotel', -20, 74, -33, 68], ['liftbase', 6, 46, 12, 30], ['piste', -12, -60, -12, -20], ['top', 6, -196, -14, -150]];
  const shots = [];
  for (const [mode, hour] of [['dusk', 18.5], ['night', 22], ['storm', null]]) {
    await page.evaluate(([mode, hour]) => { const P = SK.P7, S = SK.State; if (mode === 'storm') { let d = 1; while (P.forecast(d).k !== 'storm') d++; S.day = d; SK.P6.setTime(P.forecast(d).peak * 60); P.wxApply(); } else { SK.P6.setTime(hour * 60); } }, [mode, hour]);
    for (const [n, x, z, tx, tz] of spots) {
      await page.evaluate(([x, z, tx, tz]) => { const W = SK.W; W.x = x; W.z = z + SK.RES_Z; W.y = SK.groundY(W.x, W.z); W.yaw = Math.atan2(-(tx - x), -(tz - z)); W.pitch = -0.04; W.vx = W.vz = 0; SK.play(8); }, [x, z, tx, tz]);
      await fr(); const f = `${OUT}/nt-${mode}-${n}.png`; await page.screenshot({ path: f }); shots.push(f);
    }
  }
  require('fs').writeFileSync(OUT + '/nt.json', JSON.stringify(shots)); console.log(errors.join('|') || 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
