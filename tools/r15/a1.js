/* A1: humans ~10 % shorter with short necks; seated hips on the seat for every body size; camera at the body's eyes.
   Runs in the page (node tools/probe.js file tools/r15/a1.js, or node tools/r15/run.js a1). Returns { ok, fails, info }. */
const fails = [], info = {}, chk = (c, m) => { if (!c) fails.push(m); };
const V3 = () => new THREE.Vector3(), W = SK.W, x0 = -44, z0 = -55, gy = SK.groundY(x0, z0);
const pose = (g, nm, seatH) => { if (seatH) g.userData.seatH = seatH; g.userData.play(nm, true); SK.sim(24, 0.05); g.updateMatrixWorld(true); };
const eyeOf = g => { const d = g.userData.dbg, hb = d.head.getWorldPosition(V3()); return hb.y + 0.34 * d.head.getWorldScale(V3()).y; };   /* eyes sit HC + 0.04 above the head group */
/* the real seat contact: lowest thigh-mesh vertex within 12 cm (horizontally) of each hip joint */
const thighLow = g => { let lo = 1e9; const v = V3(); g.userData.dbg.legs.forEach(l => { const hip = l.th.getWorldPosition(V3()); l.th.children.find(c => c !== l.sn).traverse(o => { if (!o.isMesh || o.material.type === 'ShaderMaterial') return; const P = o.geometry.attributes.position; for (let i = 0; i < P.count; i++) { v.fromBufferAttribute(P, i).applyMatrix4(o.matrixWorld); if (Math.hypot(v.x - hip.x, v.z - hip.z) < 0.12 && v.y < lo) lo = v.y; } }); }); return lo; };
/* 1. standing walker: height + neck */
{ const A = SK.AVW; A.wrap.position.set(x0, gy, z0); A.wrap.rotation.set(0, 0, 0); A.wrap.visible = true; pose(A.p, 'Standing');
  const d = A.p.userData.dbg, bb = new THREE.Box3().setFromObject(A.p), nk = d.head.parent.getWorldPosition(V3()), hd = d.head.getWorldPosition(V3());
  info.height = +(bb.max.y - gy).toFixed(3); info.neck = +(hd.y - nk.y).toFixed(3); info.eye = +(eyeOf(A.p) - gy).toFixed(3); info.bodyK = A.p.userData.bodyK;
  chk(info.height > 1.95 && info.height < 2.12, 'walker height ' + info.height + ' not ~10% under the v30 2.31 m');
  chk(info.neck < 0.085, 'neck ' + info.neck + ' m still long (v30 0.13)');
  chk(Math.abs(info.bodyK - 0.9) < 1e-6, 'adult bodyK ' + info.bodyK);
  /* first-person eye: walking camera matches the avatar's eyes (avatar wrap sits at W.y + 0.06) */
  W.on = true; W.x = x0; W.z = z0; W.y = gy; W.vx = W.vz = 0; W.sit = null; SK.sim(2, 0.05); SK.W.step(0.016);
  info.camEye = +(SK.cam.position.y - gy).toFixed(3); chk(Math.abs(SK.cam.position.y - (eyeOf(A.p) + 0.06)) < 0.06, 'walk camera ' + info.camEye + ' vs eyes ' + (info.eye + 0.06).toFixed(3));
  A.wrap.visible = false; }
/* 2. seated hips on the seat surface for adult man, woman, teen, kid (same seatH) */
{ const seatH = 1.02, surf = (seatH - 0.37) * 0.5, out = {};   /* NPCs are placed at 0.5 scale: seat surface in metres above the feet */
  ['man', 'woman', 'teen', 'boy', 'guy', 'lass'].forEach(k => { const g = SK.PEOPLE[k]({ npc: 1, skis: false }); g.scale.setScalar(0.5); g.position.set(x0, gy, z0); SK.scene.add(g); pose(g, 'Sit on chair', seatH);
    out[k] = +(thighLow(g) - gy - surf).toFixed(3); SK.scene.remove(g); });
  info.seatGap = out; const v = Object.values(out); info.seatSpread = +(Math.max(...v) - Math.min(...v)).toFixed(3);
  chk(info.seatSpread < 0.02, 'seat contact differs by body size: ' + JSON.stringify(out));
  chk(v.every(q => Math.abs(q) < 0.035), 'hips not on the seat surface: ' + JSON.stringify(out)); }
/* 3. seated player: camera at the seated avatar's eyes */
{ const A = SK.AVW, fy = gy; A.wrap.position.set(x0, fy + 0.06, z0); A.wrap.visible = true; pose(A.p, 'Sit on chair', 1.02); info.sitEye = +(eyeOf(A.p) - fy).toFixed(3); A.wrap.visible = false;
  info.sitCam = SK.SIT_EYE; chk(SK.SIT_EYE !== undefined && Math.abs(SK.SIT_EYE - info.sitEye) < 0.05, 'seated camera ' + SK.SIT_EYE + ' vs seated eyes ' + info.sitEye); }
return { ok: !fails.length, fails, info };
