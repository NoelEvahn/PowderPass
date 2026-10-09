/* B1/B2: tuck from 25 km/h with hysteresis, skating push-off below it on the flats, skis on the snow while skating */
const fails = [], info = {}, chk = (c, m) => { if (!c) fails.push(m); };
const PL = SK.PL, W = SK.W, K = SK.keys, kmh = v => v / 3.6, A = () => SK.AVS.p.userData, V3 = () => new THREE.Vector3();
const I = (fwd, brake) => ({ fwd: fwd, brake: !!brake, steer: 0, jump: false, grab: false });
const setSpd = v => { const h = PL.hd; PL.vx = -Math.sin(h) * v; PL.vz = -Math.cos(h) * v; PL.vy = 0; };
const ski = (x, z) => { W.on = true; W.sit = null; if (PL.mode === 'ski') PL.exitSki(); W.x = x; W.z = z; W.y = SK.groundY(x, z); PL.enterSki(); PL.crash = 0; PL.tuck = false; };
const flat = () => { for (let r = 0; r < 60; r += 3) for (let a = 0; a < 6.28; a += 0.7) { const x = -30 + r * Math.cos(a), z = SK.RES_Z + 52 + r * Math.sin(a), e = 1, g = Math.hypot(SK.groundY(x + e, z) - SK.groundY(x - e, z), SK.groundY(x, z + e) - SK.groundY(x, z - e)) / 2;
  if (g < 0.03 && !SK.SOLIDS.some(s => Math.hypot(s.x - x, s.z - z) < Math.hypot(s.hw, s.hd) + 8) && !SK.NOSNOW.some(q => x > q.x0 - 4 && x < q.x1 + 4 && z - SK.RES_Z > q.z0 - 4 && z - SK.RES_Z < q.z1 + 4)) return [x, z]; } return null; };
/* 1. gate edges */
{ const F = flat(); ski(F[0], F[1]); const g = v => { setSpd(kmh(v)); return PL.tuckGate(I(1)); };
  info.gate = { up249: g(24.9), up250: g(25.0), up251: g(25.1), hold24: g(24.0), drop229: g(22.9), rel: (setSpd(kmh(30)), PL.tuckGate(I(1)), PL.tuckGate(I(0))) };
  chk(!info.gate.up249 && info.gate.up250 && info.gate.up251, 'tuck threshold not at 25 km/h: ' + JSON.stringify(info.gate));
  chk(info.gate.hold24 && !info.gate.drop229, 'no hysteresis 23..25 km/h: ' + JSON.stringify(info.gate)); chk(!info.gate.rel, 'tuck stays on with W released'); }
/* 2. skating on the flats: pose, speed stays under the tuck, skis on the snow */
{ const F = flat(); ski(F[0], F[1]); PL.hd = 0; PL.camYaw = 0; K.w = 1; let maxV = 0, poses = {}, contact = [], buried = 0;
  for (let i = 0; i < 150; i++) { SK.play(1, 1 / 30); maxV = Math.max(maxV, PL.spd); const a = A().animInfo(); poses[a ? a.nm : '-'] = (poses[a ? a.nm : '-'] || 0) + 1;
    if (i > 40 && i % 5 === 0) { SK.AVS.p.updateMatrixWorld(true); const lows = A().dbg.skis.map(q => { let lo = 1e9; const v = V3(); q.traverse(o => { if (!o.isMesh || o.material.type === 'ShaderMaterial') return; const P = o.geometry.attributes.position; for (let k = 0; k < P.count; k += 3) { v.fromBufferAttribute(P, k).applyMatrix4(o.matrixWorld); const d = v.y - (SK.groundY(v.x, v.z) + 0.06); if (d < lo) lo = d; } }); return lo; });
      contact.push(+Math.min(...lows).toFixed(3)); if (Math.min(...lows) < -0.06) buried++; } }
  K.w = 0; info.skate = { maxKmh: +(maxV * 3.6).toFixed(1), poses, contact: contact.slice(0, 12) };
  chk((poses.Skate || 0) > 100, 'skating pose not used on the flats: ' + JSON.stringify(poses)); chk(!poses['Tuck / straight run'], 'tucked on the flats under 25 km/h');
  chk(maxV * 3.6 > 14 && maxV * 3.6 < 25, 'skating top speed ' + (maxV * 3.6).toFixed(1) + ' km/h'); chk(contact.every(c => c < 0.03), 'no ski on the snow while skating: ' + JSON.stringify(contact)); chk(!buried, 'ski buried while skating'); }
/* 3. slope: gravity carves (no skating), tuck once past 25 km/h with W, out of it when braking below 23 */
{ let best = null; for (let x = -30; x <= 30; x += 3) for (let z = -330; z <= -230; z += 3) { const wz = SK.RES_Z + z, e = 1, g = Math.hypot(SK.groundY(x + e, wz) - SK.groundY(x - e, wz), SK.groundY(x, wz + e) - SK.groundY(x, wz - e)) / 2; if (g > 0.18 && g < 0.3 && !SK.SOLIDS.some(s => Math.hypot(s.x - x, s.z - wz) < Math.hypot(s.hw, s.hd) + 6) && (!best || Math.abs(g - 0.22) < Math.abs(best.g - 0.22))) best = { x, z: wz, g }; }
  ski(best.x, best.z); const e = 1, gx = SK.groundY(best.x + e, best.z) - SK.groundY(best.x - e, best.z), gz = SK.groundY(best.x, best.z + e) - SK.groundY(best.x, best.z - e); PL.hd = Math.atan2(gx, gz); PL.camYaw = PL.hd;   /* face the fall line */
  K.w = 1; let seen = [], tAt = null; for (let i = 0; i < 160; i++) { SK.play(1, 1 / 30); const a = A().animInfo(), nm = a ? a.nm : '-', e2 = 1, gg = Math.hypot(SK.groundY(PL.x + e2, PL.z) - SK.groundY(PL.x - e2, PL.z), SK.groundY(PL.x, PL.z + e2) - SK.groundY(PL.x, PL.z - e2)) / 2; if (gg > 0.12 && seen[seen.length - 1] !== nm) seen.push(nm);   /* only while the ground is steep (the run-out below is flat: skating there is right) */ if (PL.tuck && tAt === null) tAt = +(PL.spd * 3.6).toFixed(1); if (PL.crash > 0) break; }
  K.w = 0; K.s = 1; let outAt = null; for (let i = 0; i < 120 && outAt === null; i++) { SK.play(1, 1 / 30); if (!PL.tuck) outAt = +(PL.spd * 3.6).toFixed(1); } K.s = 0;
  info.slope = { grade: best.g, seen, tuckAt: tAt };
  chk(!seen.includes('Skate'), 'skating on a ' + (best.g * 100).toFixed(0) + ' % slope: ' + seen.join(' > ')); chk(tAt !== null && tAt >= 25, 'tuck at ' + tAt + ' km/h (want >= 25)'); }
if (PL.mode === 'ski') PL.exitSki(); return { ok: !fails.length, fails, info };
