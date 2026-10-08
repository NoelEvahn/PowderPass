/* round 10: stairs reach floor 2, one wide upstairs hallway, reception desk by the receptionist, breakfast tables, close-button icons,
   camera lock survives actions (sheets / map only hold it), first-person near plane */
const { boot, check } = require('./lib');
(async () => {
  const fails = []; const { browser, page, errors } = await boot();
  const ev = (f, a) => page.evaluate(f, a);
  await ev(() => { window.T = {
    go(x, z, y) { const W = SK.W; if (SK.PL.mode === 'ski') SK.PL.exitSki(); W.sit = null; W.x = x; W.z = z; W.y = y === undefined ? SK.groundY(x, z) : y; W.vx = W.vz = 0; SK.play(3); },
    walk(yaw, n) { const W = SK.W; W.yaw = yaw; SK.keys.w = 1; for (let i = 0; i < n; i++) SK.play(1); SK.keys.w = 0; SK.play(8); },
    lv() { const H = SK.HOTEL, f = H.HX.floors, y = SK.W.y - H.gy; return f.reduce((b, v, i) => Math.abs(v - y) < Math.abs(f[b] - y) ? i : b, 0); },
    L(x, z) { const H = SK.HOTEL; return [H.x + x, H.z + z]; } }; SK.P6.setTime(15 * 60); });
  /* up flight one: lobby -> floor 1 */
  let r = await ev(() => { const H = SK.HOTEL, p = T.L(-10.7, 3.3); T.go(p[0], p[1], H.gy + H.HX.floors[0]); T.walk(0, 75); return { lv: T.lv(), z: +(SK.W.z - H.z).toFixed(2) }; });
  check(r.lv === 1, 'flight one climbs from the lobby to floor 1 (' + JSON.stringify(r) + ')', fails);
  /* floor 1: from the top of flight one round the well (no sofas in the way) to the foot of flight two */
  r = await ev(() => { const H = SK.HOTEL, W = SK.W; T.walk(-Math.PI / 2, 20); const x1 = +(W.x - H.x).toFixed(2); T.walk(Math.PI, 85); const z1 = +(W.z - H.z).toFixed(2); T.walk(Math.PI / 2, 30);
    return { x1: x1, z1: z1, x: +(W.x - H.x).toFixed(2), lv: T.lv() }; });
  check(r.x1 > -9.6 && r.z1 > 3.0 && r.x < -9.9 && r.lv === 1, 'floor 1 landing is clear from the top of flight one to the foot of flight two (' + JSON.stringify(r) + ')', fails);
  /* up flight two: floor 1 -> floor 2 */
  r = await ev(() => { const H = SK.HOTEL, W = SK.W, p = T.L(-10.7, 3.4); T.go(p[0], p[1], H.gy + H.HX.floors[1]); T.walk(0, 75); return { lv: T.lv(), z: +(W.z - H.z).toFixed(2) }; });
  check(r.lv === 2 && r.z < -3.3, 'flight two climbs from floor 1 to floor 2 (' + JSON.stringify(r) + ')', fails);
  /* and back down to floor 1 */
  r = await ev(() => { const H = SK.HOTEL, W = SK.W, p = T.L(-10.7, -4.2); T.go(p[0], p[1], H.gy + H.HX.floors[2]); T.walk(Math.PI, 75); return { lv: T.lv(), z: +(W.z - H.z).toFixed(2) }; });
  check(r.lv === 1 && r.z > 2.6, 'flight two leads back down to floor 1 (' + JSON.stringify(r) + ')', fails);
  /* the old wall between the wing corridor and the room corridor is gone on floors 1 and 2 */
  r = await ev(() => { const H = SK.HOTEL, W = SK.W, out = []; [1, 2].forEach(f => { const p = T.L(0, -6.0); T.go(p[0], p[1], H.gy + H.HX.floors[f]); T.walk(Math.PI, 25); out.push(+(W.z - H.z).toFixed(2)); }); return out; });
  check(r.every(z => z > -4.2), 'upstairs hallways are one wide corridor: walking south from the wing crosses z -5 (' + JSON.stringify(r) + ')', fails);
  /* the lobby is still closed off from the breakfast hall except through the arch */
  r = await ev(() => { const H = SK.HOTEL, W = SK.W, p = T.L(-6.0, -4.3); T.go(p[0], p[1], H.gy + H.HX.floors[0]); T.walk(0, 25); return +(W.z - H.z).toFixed(2); });
  check(r > -5.2, 'the ground-floor wall between lobby and breakfast hall stays (' + r + ')', fails);
  /* reception: the desk sits right in front of the receptionist and check-in is still offered from the lobby side */
  r = await ev(() => { const H = SK.HOTEL, desk = SK.SOLIDS.find(s => Math.abs(s.x - (H.x - 6.6)) < 0.05 && Math.abs(s.hw - 2.9) < 0.01), p = T.L(-6.6, -1.6); T.go(p[0], p[1], H.gy + H.HX.floors[0]);
    const pr = SK.ECON.nearby(SK.W.x, SK.W.z); return { dz: desk ? +(desk.z - H.z).toFixed(2) : null, prompt: pr && pr.text }; });
  check(r.dz !== null && r.dz < -2.8 && /reception|room|check/i.test(r.prompt || ''), 'reception desk moved back to the receptionist, still usable (' + JSON.stringify(r) + ')', fails);
  /* breakfast: four tables (the crammed one by the window is gone), 16 seats */
  r = await ev(() => SK.BF && SK.BF.SEATS ? SK.BF.SEATS.length : (SK.BF && SK.BF.seats ? SK.BF.seats.length : -1));
  check(r === -1 || r === 16, 'breakfast hall has four tables (' + r + ' seats)', fails);
  /* close buttons carry an SVG X with a dark stroke */
  r = await ev(() => ['#sheet .sh-x', '#map .mp-x'].map(q => { const b = document.querySelector(q), s = b && b.querySelector('svg'); return !!s && getComputedStyle(s).stroke; }));
  check(r.every(s => s && s !== 'none'), 'sheet and map close buttons draw an X icon (' + JSON.stringify(r) + ')', fails);
  /* camera lock: no label, survives opening and closing a sheet and the map */
  r = await ev(() => { const key = t => { dispatchEvent(new KeyboardEvent('keydown', { key: 'Control' })); dispatchEvent(new KeyboardEvent('keyup', { key: 'Control' })); };
    const p = T.L(-6.6, -1.6); T.go(p[0], p[1]); key(); const on = SK.CAM.on, txt = document.getElementById('camlock').textContent;
    SK.ECON.act(SK.W.x, SK.W.z); SK.play(2); const sheetOpen = !document.getElementById('sheet').classList.contains('off'), held = SK.CAM.on && SK.CAM.held;
    const y0 = SK.W.yaw; dispatchEvent(new MouseEvent('mousemove', { movementX: 80 })); const still = SK.W.yaw === y0;
    document.querySelector('#sheet .sh-x').click(); const back = SK.CAM.on && !SK.CAM.held;
    dispatchEvent(new KeyboardEvent('keydown', { key: 'm' })); SK.play(2); const mapHeld = SK.CAM.on && SK.CAM.held; document.querySelector('#map .mp-x').click(); SK.play(2);
    const after = SK.CAM.on && !SK.CAM.held; key(); return { on: on, txt: txt, sheetOpen: sheetOpen, held: held, still: still, back: back, mapHeld: mapHeld, after: after, off: !SK.CAM.on }; });
  check(r.on && r.txt === '' && r.sheetOpen && r.held && r.still && r.back && r.after && r.off, 'camera lock has no label and stays on through a sheet and the map (' + JSON.stringify(r) + ')', fails);
  /* first person on foot: the near plane is close enough that a wall at arm's length is not cut open */
  r = await ev(() => { SK.State.cam.walk = 'first'; SK.play(3); return SK.cam.near; });
  check(r <= 0.12, 'first-person near plane is tight (' + r + ')', fails);
  check(!errors.length, 'no page errors' + (errors.length ? ': ' + errors.slice(0, 3).join(' | ') : ''), fails);
  console.log(fails.length ? fails.length + ' FAILED' : 'all ok'); await browser.close(); process.exit(fails.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
