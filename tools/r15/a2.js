/* A2: boots on the surface. The player's walker is dropped at indoor and outdoor spots, left to settle, and every sole vertex (lowest
   3 cm of each boot) is compared with the surface straight below it: indoors a raycast against the visible scene (people, outlines and
   glass skipped), outdoors the snow layer (terrain + 0.06). Hard floors: -3..+3 cm. Snow: soles may dig in up to 10 cm, float <= 3 cm. */
const fails = [], info = {}, chk = (c, m) => { if (!c) fails.push(m); };
const V3 = () => new THREE.Vector3(), W = SK.W, PL = SK.PL, H = SK.HOTEL, RZ = SK.RES_Z, camWas = SK.State.cam.walk, inStoreWas = SK.inStore;
SK.inStore = () => false;   /* stores hide the third-person body; the check needs it placed */
const rc = new THREE.Raycaster(), DOWN = new THREE.Vector3(0, -1, 0); rc.camera = SK.cam;
const isPerson = o => { for (let p = o; p; p = p.parent) if (p.userData && (p.userData.person || p.userData.seats)) return true; return false; };
const surfRay = (x, z, yTop) => { rc.set(new THREE.Vector3(x, yTop, z), DOWN); rc.far = 2.5;
  const hits = rc.intersectObjects(SK.scene.children, true).filter(h => h.object.visible && h.object.isMesh && !isPerson(h.object) && h.object.material && h.object.material.type !== 'ShaderMaterial' && !(h.object.material.transparent && h.object.material.opacity < 0.99) && h.face && h.face.normal.y > 0.5);
  return hits.length ? hits[0].point.y : null; };
const terr = (x, z) => SK.groundY(x, z);
/* soles: vertices of the boot groups within 3 cm of each boot's lowest point */
function soles() { const out = []; SK.AVW.p.updateMatrixWorld(true); SK.AVW.p.userData.dbg.skis.forEach(q => { const vs = []; q.traverse(o => { if (!o.isMesh || o.material.type === 'ShaderMaterial') return; const P = o.geometry.attributes.position; for (let i = 0; i < P.count; i += 2) vs.push(V3().fromBufferAttribute(P, i).applyMatrix4(o.matrixWorld)); });
  const lo = Math.min(...vs.map(v => v.y)); out.push(vs.filter(v => v.y < lo + 0.03)); }); return out; }
function drop(name, x, z, y, indoor) {
  SK.State.cam.walk = 'third'; W.on = true; W.sit = null; W.ride = W.board = W.unboard = null; if (PL.mode === 'ski') PL.exitSki();
  W.x = x; W.z = z; W.y = y === undefined ? terr(x, z) : y; W.vx = W.vz = 0; W.jy = W.jv = 0; W.goff = undefined; SK.AVW.p.userData.play('Standing', true); SK.play(45);
  const S = soles(); let pen = -1e9, gap = 1e9;
  S.forEach(vs => { const c = vs.reduce((a, v) => a.add(v), V3()).multiplyScalar(1 / vs.length), fl = indoor ? surfRay(c.x, c.z, c.y + 0.6) : null;
    vs.forEach(v => { const s = indoor ? fl : terr(v.x, v.z) + 0.06; if (s === null) return; pen = Math.max(pen, s - v.y); gap = Math.min(gap, v.y - s); }); });
  const r = { pen: +pen.toFixed(3), gap: +gap.toFixed(3), wy: +W.y.toFixed(3), at: [+W.x.toFixed(1), +W.z.toFixed(1)] }; info[name] = r;
  if (indoor) chk(pen < 0.03 && gap < 0.03, name + ': sole ' + (pen > 0.03 ? 'buried ' + (pen * 100).toFixed(1) : 'floating ' + (gap * 100).toFixed(1)) + ' cm');
  else chk(pen < 0.10 && gap < 0.03, name + ': sole ' + (pen > 0.10 ? 'buried ' + (pen * 100).toFixed(1) : 'floating ' + (gap * 100).toFixed(1)) + ' cm (snow)');
}
const hx = H.x, hz = H.z, HW = H.HX.walk || [0.23, 3.93, 7.23];
drop('hotel lobby', hx + 4, hz + 0.5, H.gy + 0.2, 1);
drop('hotel breakfast', hx + 2, hz - 6.2, H.gy + 0.2, 1);
drop('hotel floor 1', hx + 2, hz - 5.0, H.gy + HW[1], 1);
drop('hotel floor 2', hx + 2, hz - 5.0, H.gy + HW[2], 1);
const shop = id => SK.ECON && SK.ECON.SHOPS ? SK.ECON.SHOPS[id] : null;
[['cafe', 46, -82], ['pro', 40, -225], ['mart', 19.8, 71]].forEach(q => { const x = q[1], z = RZ + q[2]; drop(q[0] + ' inside', x, z, terr(x, z) + 0.3, 1); });
drop('snow flat', -30, RZ + 50, undefined, 0);
/* steepest open snow near the lift base (no solids within 3 m) */
{ let best = null; for (let x = -40; x <= 40; x += 4) for (let z = -260; z <= -180; z += 4) { const wz = RZ + z, g = Math.hypot(terr(x + 1, wz) - terr(x - 1, wz), terr(x, wz + 1) - terr(x, wz - 1)) / 2;
    if (g > 0.25 && g < 0.45 && !SK.SOLIDS.some(s => Math.hypot(s.x - x, s.z - wz) < Math.hypot(s.hw, s.hd) + 3) && (!best || g > best.g)) best = { x, z: wz, g }; }
  if (best) { info.slopeGrade = +best.g.toFixed(2); [0, Math.PI / 2].forEach((yaw, i) => { PL.walkFace = yaw; drop('snow slope ' + (i ? 'across' : 'along'), best.x, best.z, undefined, 0); }); } else fails.push('no slope spot found'); }
/* elevator: every stop level with the landing floor in front of the door (no lip stepping in or out) */
{ const E = SK.ELEV, sh = H.HX.shaft; SK.play(2); info.elevLip = E.FL.map(v => { const s = SK.SURF.at(H.x - 11, H.z + sh.door + 0.45, v - 0.15, v + 0.3); return s === null ? null : +(s - v).toFixed(3); });
  chk(info.elevLip.every(d => d !== null && Math.abs(d) < 0.01), 'elevator stop vs landing floor: ' + JSON.stringify(info.elevLip)); }
SK.State.cam.walk = camWas; SK.inStore = inStoreWas; return { ok: !fails.length, fails, info };
