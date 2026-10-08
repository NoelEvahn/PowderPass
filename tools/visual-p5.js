/* Phase 5 screenshots: Summit Hut on Main Street (outside + inside), Ski School sheet, Rex, a fallen skier, Leo following, markers. tools/out/p5-*.png */
const { boot, OUT } = require('./lib');
(async () => {
  const { browser, page, errors } = await boot({ viewport: { width: 844, height: 390 } });   /* phone landscape */
  await page.addStyleTag({ content: '*{transition:none!important}' });
  const ev = (f, a) => page.evaluate(f, a);
  const look = (x, z, tx, tz, pitch) => ev(([x, z, tx, tz, p]) => { const W = SK.W; if (SK.PL.mode === 'ski') SK.PL.exitSki(); SK.ECONX.closeSheet(); W.x = x; W.z = z + SK.RES_Z; W.y = SK.groundY(W.x, W.z); W.yaw = Math.atan2(-(tx - x), -(tz - z)); W.pitch = p || 0; W.vx = W.vz = 0; SK.sim(4); }, [x, z, tx, tz, pitch]);
  const shot = async n => { await page.waitForTimeout(1200); await page.screenshot({ path: OUT + '/p5-' + n + '.png' }); console.log('shot', n); };
  await look(-1, 64, -1, 54.5, 0.05); await shot('hut-outside');
  await look(-1, 56.6, -1, 52, -0.15); await shot('hut-inside');
  await look(-4, 41.5, -4, 38, -0.1); await ev(() => { SK.ECON.act(SK.W.x, SK.W.z); SK.sim(3); }); await shot('school-sheet');
  await ev(() => { SK.State.q.ch = 2; SK.State.q.chP = { s: 0, n: 0 }; });
  const P = await ev(() => SK.QX.QP.patrol.at); await look(P[0] - 5, P[1] + 1.5, P[0], P[1], -0.05); await shot('rex');
  const k = await ev(() => SK.State.q.help.sk[0]); await look(k.x + 4, k.z + 3, k.x, k.z, -0.25); await shot('fallen');
  const L = await ev(() => SK.State.q.help.lost[0]); await look(L.x + 3, L.z + 2.5, L.x, L.z, -0.35); await shot('lost-item');
  await ev(() => { SK.State.q.ch = 1; SK.State.q.help.guide = 1; }); await look(-29, -3, -33.2, 2.6, -0.1); await shot('leo-waiting');
  await look(-6, 50, -26, 52.5, 0.05); await shot('board-marker');
  await ev(() => { SK.ECONX.closeSheet(); SK.sim(2); }); await page.setViewportSize({ width: 390, height: 780 }); await page.waitForTimeout(500); await shot('phone-portrait');
  console.log(errors.length ? 'ERRORS ' + errors.join(' | ') : 'no errors');
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
