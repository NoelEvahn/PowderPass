/* round 11: elevator doors stay inside the wall when open + inner cab doors, walking under the stairs to the elevator, stair railing,
   trick input (jump + direction), first-person body, diners rack their skis, Crest Lift base on level ground, no reception prompt upstairs */
const { boot, check } = require('./lib');
(async () => {
  const fails = []; const { browser, page, errors } = await boot();
  const ev = (f, a) => page.evaluate(f, a);
  await ev(() => { window.T = {
    go(x, z, y) { const W = SK.W; if (SK.PL.mode === 'ski') SK.PL.exitSki(); W.sit = null; W.x = x; W.z = z; W.y = y === undefined ? SK.groundY(x, z) : y; W.vx = W.vz = 0; SK.play(3); },
    walk(yaw, n) { const W = SK.W; W.yaw = yaw; SK.keys.w = 1; for (let i = 0; i < n; i++) SK.play(1); SK.keys.w = 0; SK.play(8); },
    L(x, z) { const H = SK.HOTEL; return [H.x + x, H.z + z]; } }; SK.P6.setTime(15 * 60); });
  /* elevator: open at the lobby, every door leaf sits within the shaft's x range (no leaf poking out of the building) */
  let r = await ev(() => { const H = SK.HOTEL, sh = H.HX.shaft, E = SK.ELEV; E.go(0); SK.play(40); const xs = [];
    SK.scene.traverse(o => { if (o.isMesh && o.geometry && o.geometry.parameters && Math.abs(o.geometry.parameters.width - 0.33) < 1e-6 && Math.abs(o.position.y - (H.gy + H.HX.floors[0])) < 2) xs.push(+(o.position.x - H.x).toFixed(2)); });
    return { door: +E.S.door.toFixed(2), min: Math.min.apply(null, xs), max: Math.max.apply(null, xs), n: xs.length, x0: sh.x0 }; });
  check(r.door === 1 && r.n >= 4 && r.min - 0.165 >= -11.97 && r.max + 0.165 <= -9.89 && Math.abs((r.min + r.max) / 2 - (-10.93)) < 0.02,   /* shaft walls' outer faces -11.95 / -9.91; leaves symmetric about the centred door */ 'open elevator door leaves stay inside the wall pockets (' + JSON.stringify(r) + ')', fails);
  r = await ev(() => { let n = 0; SK.ELEV.cab.traverse(o => { if (o.isMesh && o.geometry.parameters && Math.abs(o.geometry.parameters.width - 0.27) < 1e-6) n++; }); return n; });
  check(r === 4, 'the cab has its own inner door leaves (' + r + ')', fails);
  /* lobby -> under the high end of flight one -> elevator lobby door */
  r = await ev(() => { const H = SK.HOTEL, W = SK.W, p = T.L(-9.0, -1.6); T.go(p[0], p[1], H.gy + H.HX.floors[0]); T.walk(Math.PI / 2, 9); const x1 = +(W.x - H.x).toFixed(2); T.walk(0, 30); return { x1: x1, z: +(W.z - H.z).toFixed(2), y: +(W.y - H.gy).toFixed(2) }; });
  check(r.x1 < -10.3 && r.z < -5.6 && r.y < 1, 'you can walk under the stairs from the lobby to the elevator (' + JSON.stringify(r) + ')', fails);
  /* on flight one, walking sideways east is stopped by the railing */
  r = await ev(() => { const H = SK.HOTEL, W = SK.W, p = T.L(-10.7, 3.3); T.go(p[0], p[1], H.gy + H.HX.floors[0]); T.walk(0, 30); const y0 = W.y - H.gy; T.walk(-Math.PI / 2, 20); return { y0: +y0.toFixed(2), x: +(W.x - H.x).toFixed(2), y: +(W.y - H.gy).toFixed(2) }; });
  check(r.y0 > 1 && r.x < -9.85 && r.y > 1, 'the stair railing keeps you on flight one (' + JSON.stringify(r) + ')', fails);
  /* tricks: W in the air does nothing alone, flips with jump held */
  r = await ev(() => { const PL = SK.PL, K = SK.keys, out = {}; T.go(-30, SK.RES_Z + 52); PL.tryToggle(); SK.play(2);
    const air = (hold) => { PL.y += 6; PL.vy = 6; PL.air = true; PL.airT = 0; PL.flip = 0; PL.crash = 0; K.w = 1; K[' '] = hold ? 1 : 0; for (let i = 0; i < 12; i++) SK.play(1); const f = PL.flip; K.w = 0; K[' '] = 0; return f; };
    out.plain = +air(false).toFixed(2); out.held = +air(true).toFixed(2); out.body = SK.State.cam.ski !== 'third' ? 'n/a' : 'third'; SK.play(90); PL.exitSki(); return out; });
  check(r.plain === 0 && r.held > 1, 'flips need jump held with W (' + JSON.stringify(r) + ')', fails);
  /* first-person body: in the world at your feet, not pinned to the screen. Looking straight ahead the skis are out of view; looking down they're in it */
  r = await ev(() => { const PL = SK.PL, S = SK.State, cam = SK.cam; T.go(-30, SK.RES_Z + 52); PL.tryToggle(); SK.play(2); const was = S.cam.ski; S.cam.ski = 'first'; PL.y += 6; PL.vy = 6; PL.air = true; SK.play(2);
    const inView = () => { SK.PRE.forEach(f => f()); cam.updateMatrixWorld(true); const fr = new THREE.Frustum(), m = new THREE.Matrix4().multiplyMatrices(cam.projectionMatrix, cam.matrixWorldInverse); fr.setFromProjectionMatrix(m);
      let n = 0; SK.VM.skis.traverse(o => { if (o.isMesh) { o.geometry.computeBoundingSphere(); const sph = o.geometry.boundingSphere.clone().applyMatrix4(o.matrixWorld); if (fr.intersectsSphere(sph)) n++; } }); return n; };
    PL.lookPitch = 0.1; SK.play(1); const ahead = inView(); PL.lookPitch = -1.0; SK.play(1); const down = inView(); PL.lookPitch = 0; S.cam.ski = was; SK.play(90); PL.exitSki(); return { rig: SK.VM.rig.visible !== undefined, ahead: ahead, down: down }; });
  check(r.ahead === 0 && r.down > 0, 'first-person skis are out of view looking ahead, in view looking down (' + JSON.stringify(r) + ')', fails);
  /* breakfast: a diner racks their skis in the ski room on the way in */
  r = await ev(() => { const BF = SK.BF; SK.P6.setTime(8 * 60); let d = null, carried = 0, racked = 0;
    for (let i = 0; i < 900 && !racked; i++) { SK.play(1); if (!d) d = BF.guests.find(q => q.carry && q.st === 'in' && q.carry.visible && !q.rack.visible); if (d) { if (d.carry.visible) carried++; if (d.rack.visible && !d.carry.visible) racked = i; } }
    return { racked: racked, carried: carried }; });
  check(r.racked > 0 && r.carried > 0, 'breakfast diners carry their skis in and rack them first (' + JSON.stringify(r) + ')', fails);
  /* Crest Lift base: no snow above the platform inside the station footprint */
  r = await ev(() => { const l = SK.LIFTS.find(q => q.name === 'Crest Lift'), v = new THREE.Vector3(); l.st0.getWorldPosition(v); const q = new THREE.Quaternion(); l.st0.getWorldQuaternion(q);
    const ex = new THREE.Vector3(1, 0, 0).applyQuaternion(q), ez = new THREE.Vector3(0, 0, 1).applyQuaternion(q); let mx = -9;
    for (let x = -7; x <= 7; x += 1) for (let z = -6; z <= 17; z += 1) mx = Math.max(mx, SK.groundRaw(v.x + x * ex.x + z * ez.x, v.z + x * ex.z + z * ez.z) - v.y); return +mx.toFixed(2); });
  check(r < 0.35, 'Crest Lift base station is not buried (' + r + ' m)', fails);
  /* no reception prompt on floor 2 above the desk */
  r = await ev(() => { const H = SK.HOTEL, p = T.L(-6.6, -1.6); T.go(p[0], p[1], H.gy + H.HX.floors[2]); const pr = SK.ECON.nearby(SK.W.x, SK.W.z); return pr ? pr.text : ''; });
  check(!/reception/i.test(r), 'reception is not offered from floor 2 (' + r + ')', fails);
  check(!errors.length, 'no page errors' + (errors.length ? ': ' + errors.slice(0, 3).join(' | ') : ''), fails);
  console.log(fails.length ? fails.length + ' FAILED' : 'all ok'); await browser.close(); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
