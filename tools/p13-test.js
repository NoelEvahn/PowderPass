/* round 6: jump on foot, ramps you can ride (ground follows their shape), rail / box grinding, food in your hands, the working elevator cab */
const { boot, check, OUT } = require('./lib');
(async () => {
  const fails = []; const { browser, page, errors } = await boot();
  const ev = (f, a) => page.evaluate(f, a);
  await ev(() => { window.T = { go(x, z, y) { const W = SK.W; if (SK.PL.mode === 'ski') SK.PL.exitSki(); W.sit = null; W.x = x; W.z = z; W.y = y === undefined ? SK.groundY(x, z) : y; W.vx = W.vz = 0; SK.play(3); } }; });
  /* jump on foot */
  let r = await ev(() => { const W = SK.W; T.go(-30, SK.RES_Z + 52); let mx = 0; SK.keys[' '] = 1; for (let i = 0; i < 40; i++) { SK.play(1); mx = Math.max(mx, W.jy || 0); } SK.keys[' '] = 0; SK.play(10); return { mx: +mx.toFixed(2), after: W.jy || 0, cls: document.body.classList.contains('walkjump') }; });
  check(r.mx > 0.5 && r.mx < 1.3 && r.after === 0 && r.cls, 'Space jumps on foot and lands again (' + JSON.stringify(r) + ')', fails);
  /* ramps: the ground rises along the kicker and the fun box */
  r = await ev(() => { const S = SK.PARK.surfaces, k = S.find(q => q.sh.r === 2.2 && q.sc > 1.05) || S[0], out = { n: S.length, rails: SK.PARK.rails.length, prof: [] };
    for (let u = -1.4; u <= 1.41; u += 0.7) { const wx = k.x + u * k.sc * k.c, wz = k.z - u * k.sc * k.s; out.prof.push(+(SK.groundY(wx, wz) - k.y).toFixed(2)); } return out; });
  const inc = r.prof.every((v, i) => i === 0 || v >= r.prof[i - 1] - 0.01);
  check(r.n >= 4 && r.rails >= 3 && inc && r.prof[r.prof.length - 1] > 1.0, 'ramps lift the ground along their shape; rails registered (' + JSON.stringify(r) + ')', fails);
  /* walking up the kicker raises you */
  r = await ev(() => { const k = SK.PARK.surfaces.find(q => q.sh.r === 2.2 && q.sc > 1.05), W = SK.W; T.go(k.x + 1.2 * k.sc * k.c, k.z - 1.2 * k.sc * k.s); SK.play(20); return +(W.y - k.y).toFixed(2); });
  check(r > 0.8, 'standing on the kicker lip puts you on top of it (' + r + ' m)', fails);
  /* grind: ski onto the fun box / jump onto the rail */
  r = await ev(() => { const PL = SK.PL, W = SK.W, rl = SK.PARK.rails.find(q => q.kind === 'Rail slide'), a = rl.P[0], b = rl.P[rl.P.length - 1], dx = b[0] - a[0], dz = b[2] - a[2], L = Math.hypot(dx, dz), ux = dx / L, uz = dz / L;
    T.go(a[0] - ux * 3, a[2] - uz * 3); PL.tryToggle(); SK.play(2); if (PL.mode !== 'ski') return { err: 'no skis' };
    let caught = 0, pts = '', got = 0; SK.events.on('grind', e => { got = e.t; }); PL.x = a[0] + ux * 0.2; PL.z = a[2] + uz * 0.2; PL.y = a[1] + 0.25; PL.vx = ux * 6; PL.vz = uz * 6; PL.vy = -0.5; PL.air = true; PL.crash = 0;
    for (let i = 0; i < 90; i++) { SK.play(1); if (SK.GRIND.G.on) caught++; } pts = document.getElementById('trick').textContent; return { caught: caught, got: +got.toFixed(2), pts: pts, crash: PL.crash > 0 }; });
  check(r.caught > 5 && r.got > 0.25 && /slide/i.test(r.pts) && !r.crash, 'landing on the rail grinds it and scores a slide (' + JSON.stringify(r) + ')', fails);
  await ev(() => { if (SK.PL.mode === 'ski') SK.PL.exitSki(); });
  /* food in your hands */
  r = await ev(() => { const S = SK.State; T.go(-30, SK.RES_Z + 52); S.inv.cocoa = 1; S.energy = 40; SK.ECONX.econOp(['use', 'cocoa']); const busy = SK.VM.busy; SK.play(110); return { busy: busy, after: SK.VM.busy, e: Math.round(S.energy) }; });
  check(r.busy && !r.after, 'drinking cocoa plays a sip animation in your hands (' + JSON.stringify(r) + ')', fails);
  /* elevator: call it, step in, ride to floor 2 with the doors opening on arrival */
  r = await ev(() => { const E = SK.ELEV, H = SK.HOTEL, sh = H.HX.shaft, W = SK.W, fy = H.gy + H.HX.floors[0]; SK.P6.setTime(15 * 60);
    T.go(H.x - 11, H.z + sh.door + 0.8, fy); const call = SK.ECON.nearby(W.x, W.z); SK.ECON.act(W.x, W.z); SK.play(90); const open0 = E.S.door;
    W.yaw = 0; for (let i = 0; i < 60 && !E.inside(W.x, W.z, W.y); i++) { SK.keys.w = 1; SK.play(1); } SK.keys.w = 0; SK.play(10); const inCab = E.inside(W.x, W.z, W.y), pr = SK.ECON.nearby(W.x, W.z);
    SK.P6.op(['elev', '2']); for (let i = 0; i < 30 * 20 && !(E.S.at === 2 && E.S.phase === 'open'); i++) SK.play(1);
    return { call: call && call.text, open0: +open0.toFixed(2), inCab: inCab, pr: pr && pr.text, at: E.S.at, phase: E.S.phase, dy: +(W.y - E.FL[2]).toFixed(2) }; });
  check(/call/i.test(r.call) && r.open0 > 0.9 && r.inCab && /floor/i.test(r.pr) && r.at === 2 && r.phase === 'open' && Math.abs(r.dy) < 0.1, 'elevator: call, doors open, step in, ride to floor 2 (' + JSON.stringify(r) + ')', fails);
  await page.waitForTimeout(500); await page.screenshot({ path: OUT + '/p13-elev.png' });
  /* (18 steps: the floor-2 hallway is open now, so a long walk south carries on down the new second flight) */
  r = await ev(() => { const W = SK.W, E = SK.ELEV, H = SK.HOTEL; W.yaw = Math.PI; for (let i = 0; i < 18; i++) { SK.keys.w = 1; SK.play(1); } SK.keys.w = 0; SK.play(5); return { out: !E.inside(W.x, W.z, W.y), y: +(W.y - H.gy).toFixed(2), z: +(W.z - H.z).toFixed(2) }; });
  check(r.out && Math.abs(r.y - await ev(() => SK.HOTEL.HX.floors[2])) < 0.2, 'walk out of the cab onto floor 2 (' + JSON.stringify(r) + ')', fails);
  check(errors.length === 0, 'no page errors ' + errors.slice(0, 5).join(' | '), fails);
  await browser.close(); console.log(fails.length ? 'FAILED ' + fails.length : 'ALL OK'); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
