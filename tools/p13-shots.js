/* screenshots for round 6: breakfast hall, plate in hand, seated eating (3rd person), elevator cab, rail grind */
const { boot, OUT } = require('./lib');
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: 390, height: 780 } });
  const ev = (f, a) => page.evaluate(f, a), shot = async n => { await ev(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))); await page.waitForTimeout(400); await page.screenshot({ path: OUT + '/p13-' + n + '.png' }); };
  await ev(() => { const H = SK.HOTEL, W = SK.W; SK.P6.setTime(8 * 60); W.x = H.x + 9.8; W.z = H.z - 6.0; W.y = H.gy + 0.23; W.yaw = 1.35; W.pitch = -0.18; SK.play(3); });
  await shot('hall');
  await ev(() => { const H = SK.HOTEL, W = SK.W; W.x = H.x + 4.75; W.z = H.z - 9.4; W.yaw = 0; W.pitch = -0.4; SK.play(3); SK.ECON.act(W.x, W.z); ['eggs', 'pancakes', 'fruit', 'coffee'].forEach(k => { W.x = H.x + SK.BF.ST.find(s => s.k === k).x; SK.play(2); SK.ECON.act(W.x, W.z); }); W.x = H.x + 2; SK.play(3); });
  await shot('plate');
  await ev(() => { const H = SK.HOTEL, W = SK.W, s = SK.BF.SEATS.find(q => !q.taken && q.tab === 1); W.x = H.x + s.x; W.z = H.z + s.z + (s.yaw ? -0.3 : 0.3); SK.play(2); SK.ECON.act(W.x, W.z); SK.play(12); });
  await shot('eat1st');
  await ev(() => { SK.State.cam.walk = 'third'; SK.W.yaw += 0.6; SK.play(4); });
  await shot('eat3rd');
  await ev(() => { SK.play(400); SK.ECON.act(SK.W.x, SK.W.z); SK.State.cam.walk = 'first'; const H = SK.HOTEL, W = SK.W, sh = H.HX.shaft; W.x = H.x - 11; W.z = H.z + sh.door + 2.2; W.y = H.gy + 0.23; W.yaw = 0; W.pitch = -0.05; SK.play(3); SK.ELEV.go(0); SK.play(60); });
  await shot('elevdoor');
  await ev(() => { const H = SK.HOTEL, W = SK.W, sh = H.HX.shaft; W.x = H.x - 10.9; W.z = H.z + (sh.z0 + sh.z1) / 2 + 0.3; W.yaw = 0; W.pitch = -0.1; SK.play(3); SK.W.yaw = Math.PI * 0.85; SK.play(2); });
  await shot('cab');
  await ev(() => { const PL = SK.PL, W = SK.W, rl = SK.PARK.rails.find(q => q.kind === 'Kink slide'), a = rl.P[0], b = rl.P[1], L = Math.hypot(b[0] - a[0], b[2] - a[2]), ux = (b[0] - a[0]) / L, uz = (b[2] - a[2]) / L;
    W.x = a[0] - ux * 3; W.z = a[2] - uz * 3; W.y = SK.groundY(W.x, W.z); SK.play(2); PL.tryToggle(); SK.play(2); PL.x = a[0] + ux * 0.3; PL.z = a[2] + uz * 0.3; PL.y = a[1] + 0.2; PL.vx = ux * 6; PL.vz = uz * 6; PL.vy = -0.3; PL.air = true; SK.play(14); });
  await shot('grind');
  console.log(errors.length ? 'ERRORS ' + errors.join(' | ') : 'no errors'); await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
