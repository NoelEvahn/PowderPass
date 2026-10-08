/* Round 7: spot-cone lights, model doors locked, breakfast diners on a schedule, real fallen skiers (max 2), winter dusk + aurora,
   faster warmth drain, walking-jump pose, layered eating, Ctrl camera lock, side-docked panels */
const { boot, check } = require('./lib');
(async () => {
  const fails = []; const { browser, page, errors } = await boot({ viewport: { width: 1000, height: 640 } });
  const ev = (f, a) => page.evaluate(f, a);
  let r = await ev(() => { SK.P6.setTime(22 * 60); const W = SK.W; W.x = -44; W.y = 0.25; W.z = -64; SK.play(3); SK.P6.lightsFor();
    return { spots: SK.P6.SPS.length, lit: SK.P6.SPS.filter(s => s.intensity > 0).length, pts: SK.P6.PTS.length, down: SK.P6.SPS.filter(s => s.intensity > 0).every(s => s.target.position.y < s.position.y), chunk: THREE.ShaderChunk.lights_toon_pars_fragment.indexOf('RE_Direct_ToonLocal') > 0 }; });
  check(r.spots === 12 && r.lit >= 4 && r.down && r.chunk, 'lamps are downward spot cones; local lights skip back faces (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { SK.State.cabins = []; SK.play(30); const D = SK.CDOORS; return { n: D.length, alp: !!D.find(d => d.id === 'alpine'), shut: D.every(d => d.o === 0 && !d.sol.off), af: D.filter(d => /^af/.test(d.id)).length }; });
  check(r.alp && r.af === 2 && r.shut, 'cabin doors (A-frames, Alpine) are the model leaf, shut and solid when not owned (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { const D = SK.CDOORS.find(d => d.id === 'alpine'); SK.State.cabins.push('alpine'); const W = SK.W; W.x = D.x; W.z = D.z + 1.5; W.y = SK.groundY(W.x, W.z); SK.play(60); const o = D.o; SK.State.cabins.pop(); return { o: o, solid: D.sol.off }; });
  check(r.o > 0.9 && r.solid, 'the Alpine front door swings open for its owner (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { SK.P6.setTime(4 * 60); SK.play(1800, 1 / 30);   /* diners walk out via the ski-room racks now: give them time */ const g0 = SK.BF.guests.filter(d => !d.chef && d.g.visible).length; SK.P6.setTime(8 * 60); for (let i = 0; i < 6; i++) { SK.State.warmth = SK.State.energy = 100; SK.play(300, 1 / 30); }
    const G = SK.BF.guests.filter(d => !d.chef), seated = G.filter(d => d.st === 'eat'), H = SK.HOTEL;
    const ok = seated.every(d => { const s = d.seat, dz = H.z + s.tz - d.g.position.z, fz = Math.cos(d.g.rotation.y); return Math.sign(dz) === Math.sign(fz); });
    const chef = SK.BF.guests.find(d => d.chef).g.position; SK.P6.setTime(11 * 60 + 30); SK.play(1500, 1 / 30); const g2 = G.filter(d => d.g.visible).length;
    return { before: g0, seated: seated.length, facing: ok, after: g2, chefZ: +(chef.z - H.z).toFixed(2) }; });
  check(r.before === 0 && r.seated >= 1 && r.facing && r.after === 0 && r.chefZ > -10.8, 'diners arrive at breakfast, sit facing their table, leave after 11; chef clear of the wall (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { SK.P6.setTime(10 * 60); let mx = 0; for (let i = 0; i < 40; i++) { SK.State.warmth = SK.State.energy = 100; SK.play(300, 1 / 30); mx = Math.max(mx, SK.State.q.help.sk.filter(k => !k.done).length); } const F = SK.QX.FALLEN.filter(f => f.g); return { mx: mx, real: F.every(f => SK.P8.TSK.some(t => t.g === f.g)), n: F.length }; });
  check(r.mx >= 1 && r.mx <= 2 && r.real, 'fallen skiers are real trail skiers, at most 2 at once (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { const n = t => { SK.P6.setTime(t * 60); return +SK.P6.night().toFixed(2); }; const a = { h16: n(16), h17: n(17.25), h18: n(18), h22: n(22) }; a.aur = +SK.SKY.uniforms.aur.value.toFixed(2); n(12); a.aurDay = SK.SKY.uniforms.aur.value; return a; });
  check(r.h16 === 0 && r.h17 > 0.2 && r.h17 < 0.8 && r.h18 === 1 && r.aur > 0 && r.aurDay === 0, 'night fades in from 16:30 and is full by 18:00; northern lights after dark (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { const S = SK.State, W = SK.W; SK.P6.setTime(12 * 60); W.x = -4; W.z = -60 + SK.RES_Z; W.y = SK.groundY(W.x, W.z); S.energy = 100; S.warmth = 90; SK.play(300, 1 / 30); return +(90 - S.warmth).toFixed(2); });
  check(r > 1.5, 'warmth drains faster outdoors (' + r + ' in 10 s)', fails);
  r = await ev(() => { SK.State.cam.walk = 'third'; SK.play(5); SK.W.jv = 4.6; SK.W.jy = 0.001; SK.play(3, 1 / 30); const a = SK.AVW ? SK.AVW.p.userData.animInfo() : null; return a && a.nm; });
  check(r === 'Hop' || r === null, 'walking jump plays the in-air Hop pose, no extra lift (' + r + ')', fails);
  r = await ev(() => { SK.play(60, 1 / 30); SK.State.inv.cocoa = 1; SK.ECONX.econOp(['use', 'cocoa']); SK.W.vx = 2; SK.play(4, 1 / 30); const busy = SK.VM.busy; SK.W.jv = 4.6; SK.W.jy = 0.001; SK.play(2, 1 / 30); return { busy: busy, jumped: SK.W.jy > 0 }; });
  check(r.busy && r.jumped, 'you can jump while drinking (eating layers on top)', fails);
  r = await ev(() => { const y0 = SK.W.yaw; dispatchEvent(new KeyboardEvent('keydown', { key: 'Control' })); dispatchEvent(new KeyboardEvent('keyup', { key: 'Control' })); const on = SK.CAM.on;
    dispatchEvent(new MouseEvent('mousemove', { movementX: 60, movementY: 0 })); const turned = Math.abs(SK.W.yaw - y0) > 0.1; const vis = getComputedStyle(document.getElementById('camlock')).display;
    dispatchEvent(new KeyboardEvent('keydown', { key: 'Control' })); dispatchEvent(new KeyboardEvent('keyup', { key: 'Control' })); return { on: on, turned: turned, vis: vis, off: !SK.CAM.on }; });
  check(r.on && r.turned && r.vis === 'block' && r.off, 'Ctrl locks the camera (mouse steers, crosshair shown), Ctrl again unlocks (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { SK.ECONX.openSheet('bag', null, null); const b = document.getElementById('sheet').getBoundingClientRect(); SK.ECONX.closeSheet(); return { left: b.left, cx: b.left + b.width / 2 }; });
  check(r.cx < 1000 * 0.4, 'sheets dock to the side, not the middle (' + JSON.stringify(r) + ')', fails);
  check(!errors.length, 'no page errors ' + errors.join(' | '), fails);
  console.log(fails.length ? 'FAILED ' + fails.length : 'ALL OK'); await browser.close(); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
