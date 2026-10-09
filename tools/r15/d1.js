/* D (hotel): elevator centred between equal piers, sliding glass doors solid when shut and open for you, no slab hovering over the
   breakfast floor, every suite's bathroom walled off from the bedroom / lounge. */
const fails = [], info = {}, chk = (c, m) => { if (!c) fails.push(m); };
const H = SK.HOTEL, X = H.HX, sh = X.shaft, W = SK.W; SK.P6.setTime(780); SK.play(3);   /* afternoon: no breakfast guests holding the doors open */
/* 1. elevator centred on its shaft */
info.elev = { ex: X.ex, shaftMid: +((sh.x0 + sh.x1) / 2).toFixed(3) }; chk(Math.abs(X.ex - (sh.x0 + sh.x1) / 2) < 0.02, 'elevator door off the shaft centre ' + JSON.stringify(info.elev));
/* 2. sliding glass doors: shut = solid, you walking up opens them */
{ const D = H.doors; const sl = SK.SOLIDS.filter(s => s.top !== undefined && Math.abs(s.hw - 1.3) < 1e-6 && Math.abs(s.x - H.x) < 14 && Math.abs(s.z - H.z) < 14 && s.hd < 0.1);
  W.on = true; W.x = H.x; W.z = H.z + 30; W.y = SK.groundY(W.x, W.z); SK.play(90); const shut = sl.map(s => !s.off);
  W.x = H.x; W.z = H.z + 6.3; W.y = SK.groundY(W.x, W.z); SK.play(30); const near = sl.filter(s => Math.hypot(s.x - W.x, s.z - W.z) < 3).map(s => !!s.off);
  info.doors = { n: sl.length, shut, nearOpen: near }; chk(sl.length >= 2 && shut.every(Boolean), 'glass doors not solid when shut: ' + JSON.stringify(info.doors)); chk(near.length && near.every(Boolean), 'entrance did not open for you: ' + JSON.stringify(info.doors)); }
/* 3. breakfast entrance: the floor you stand on is the real floor (the old rug hovered 20 cm up) */
{ const fl = SK.SURF.at(H.x, H.z - 7.5, H.wy, H.wy + 0.5); info.bfFloor = fl === null ? null : +(fl - H.wy - 0.23).toFixed(3); chk(fl !== null && Math.abs(fl - H.wy - 0.23) < 0.03, 'breakfast entrance surface ' + info.bfFloor + ' m off the hotel floor (slab top 0.23 over the origin)'); }
/* 4. suites: a wall solid on every side of the bathroom (west, front with doorway, east) */
{ info.suites = X.rooms.filter(r => r.type === 'suite').map(r => { const yb = H.wy + X.walk[r.floor === 2 ? 2 : 1], x0 = r.hx - (r.hx - Math.floor(r.hx)), n = SK.SOLIDS.filter(s => s.top === undefined && s.bot !== undefined && Math.abs(s.z - (H.z + r.z)) < 2.6 && Math.abs(s.x - H.x - r.hx) < 4.5 && (s.hw < 0.1 || s.hd < 0.1)).length; return { no: r.no, walls: n }; });
  chk(info.suites.length >= 3 && info.suites.every(s => s.walls >= 3), 'suite bathroom walls missing: ' + JSON.stringify(info.suites)); }
return { ok: !fails.length, fails, info };
