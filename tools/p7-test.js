/* Phase 6: seeded weather (varied, deterministic), storm cuts visibility and warms slower, goggles help, graphics levels, settings saved, sound starts on a tap without errors */
const { boot, check, OUT } = require('./lib');
(async () => {
  const fails = []; const { browser, page, errors } = await boot();
  const ev = (f, a) => page.evaluate(f, a);
  let r = await ev(() => { const P = SK.P7, ks = {}; for (let d = 1; d <= 120; d++) ks[P.forecast(d).k] = (ks[P.forecast(d).k] || 0) + 1; return { ks: ks, same: JSON.stringify(P.forecast(7)) === JSON.stringify(P.forecast(7)) }; });
  check(Object.keys(r.ks).length >= 8 && r.same, 'forecast varies over 60 days (9 kinds of day) and is repeatable ' + JSON.stringify(r.ks), fails);
  r = await ev(() => { const P = SK.P7, S = SK.State; let d = 1; while (P.forecast(d).k !== 'storm') d++; S.day = d; S.settings.gfx = 'high'; P.qualApply(); SK.P6.setTime(P.forecast(d).peak * 60);
    const a = { amt: +P.WX.amt.toFixed(2), far: Math.round(SK.scene.fog.far), cold: P.WX.cold, snow: +SK.SNPFX.amount().toFixed(2) };
    S.owned.push('goggles'); S.gear.goggles = 'goggles'; P.wxApply(); a.farG = Math.round(SK.scene.fog.far);
    S.settings.gfx = 'low'; P.qualApply(); P.wxApply(); a.snowLow = +SK.SNPFX.amount().toFixed(2); a.lowPts = SK.P6.SPS.filter(p => !p.userData.off).length; a.spotSh = SK.P6.SPS[0].castShadow;
    let c = 1; while (P.forecast(c).k !== 'clear') c++; S.day = c; P.wxApply(); a.clearFar = Math.round(SK.scene.fog.far); a.clearSnow = SK.SNPFX.amount(); return a; });
  check(r.amt > 0.9 && r.far < 200 && r.cold > 1.5 && r.snow === 1, 'storm at its peak: heavy snow, fog closes to ' + r.far + ' m, colder', fails);
  check(r.farG > r.far * 2, 'goggles push visibility to ' + r.farG + ' m', fails);
  check(r.snowLow <= 0.35 && r.lowPts === 5 && !r.spotSh, 'low graphics: lighter snow, 5 lamp cones, no lamp shadows', fails);
  check(r.clearFar === 950 && r.clearSnow === 0, 'clear day: no snow, full view', fails);
  /* storm at night screenshot */
  await ev(() => { const P = SK.P7, S = SK.State; let d = 1; while (P.forecast(d).k !== 'storm') d++; S.day = d; S.settings.gfx = 'high'; S.gear.goggles = 'nogog'; P.qualApply(); SK.P6.setTime(21 * 60); P.wxApply();
    const W = SK.W; W.x = -6; W.z = 63 + SK.RES_Z; W.y = SK.groundY(W.x, W.z); W.yaw = -Math.PI / 2; SK.sim(10); });
  await page.waitForTimeout(2500); await page.screenshot({ path: OUT + '/p7-storm-night.png' });
  /* settings sheet + sound */
  r = await ev(() => { SK.ECONX.openSheet('set', null, null); SK.sim(2); const html = document.querySelector('#sheet .sh-b').innerHTML; SK.ECONX.econOp(['p6', 'p7', 'vib']); const vib = SK.State.settings.vib; SK.ECONX.closeSheet(); return { html: /Weather today/.test(html) && /Graphics/.test(html), vib: vib }; });
  await page.mouse.click(600, 400); await page.waitForTimeout(500);
  r.ac = await ev(() => { ['coin', 'quest', 'land', 'lock', 'ding'].forEach(k => SK.sfx(k)); return !!SK.AC; });
  check(r.html && r.vib === 0 && r.ac, 'settings sheet shows weather, vibration toggles off, audio starts on a tap', fails);
  /* the real buttons: tap them in the sheet like a player would */
  await ev(() => { SK.ECONX.openSheet('set', null, null); SK.sim(2); });
  const tap = async a => { await page.click('#sheet button[data-a="' + a + '"]'); await page.waitForTimeout(300); };
  await tap('p6:p7:gfx:low');
  r = await ev(() => ({ gfx: SK.State.settings.gfx, dpr: SK.renderer.getPixelRatio(), sh: SK.scene.children.find(o => o.isDirectionalLight).shadow.mapSize.x, pts: SK.P6.SPS.filter(p => !p.userData.off).length }));
  check(r.gfx === 'low' && r.dpr <= 1 && r.sh === 512 && r.pts === 5, 'tapping Low in Settings applies it: ' + JSON.stringify(r), fails);
  await tap('p6:p7:gfx:high');
  r = await ev(() => ({ gfx: SK.State.settings.gfx, sh: SK.scene.children.find(o => o.isDirectionalLight).shadow.mapSize.x, pts: SK.P6.SPS.filter(p => !p.userData.off).length, sp: SK.P6.SPS[0].castShadow }));
  check(r.gfx === 'high' && r.sh === 2048 && r.pts === 12 && r.sp, 'tapping High restores it: ' + JSON.stringify(r), fails);
  await tap('p6:p7:vib'); r = await ev(() => SK.State.settings.vib); check(r === 1, 'vibration button toggles', fails);
  await tap('p6:p7:vib');
  r = await ev(() => { let err = ''; try { const W = SK.W; W.x = 12; W.z = 40 + SK.RES_Z; SK.sim(40); } catch (e) { err = e.message; } return err; });
  check(!r, 'lift, motor and crowd sounds run near the Summit Express (' + r + ')', fails);
  await ev(() => SK.save()); await page.reload(); await page.waitForFunction(() => window.SK && SK.State && SK.State.q && !document.getElementById('load'), null, { timeout: 180000 });
  r = await ev(() => SK.State.settings); check(r.vib === 0 && r.gfx === 'high', 'settings survive a reload ' + JSON.stringify(r), fails);
  check(errors.length === 0, 'no errors ' + errors.join(' | '), fails);
  await browser.close(); console.log(fails.length ? 'FAILED ' + fails.length : 'ALL OK'); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
