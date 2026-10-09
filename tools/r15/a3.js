/* A3: everyone seated has their thighs on a real seat. Every person in a seated move ('Sit on chair', 'Eating') is checked: the underside
   of the thighs next to the hips vs the furniture straight below (raycast, people/outlines/glass skipped). Gap must be within -3..+3 cm,
   and there must be a seat at all (no hit within 25 cm = sitting on air). Optional: PQ_A3_SIM seconds of simulation first. */
const fails = [], info = { seated: [] }, chk = (c, m) => { if (!c) fails.push(m); };
const V3 = () => new THREE.Vector3(), rc = new THREE.Raycaster(), DOWN = new THREE.Vector3(0, -1, 0); rc.camera = SK.cam;
const isPerson = o => { for (let p = o; p; p = p.parent) { if (p.userData && p.userData.person) return true; if (!p.visible) return true; } return false; };
const seatBelow = (x, y, z) => { rc.set(new THREE.Vector3(x, y, z), DOWN); rc.far = 0.6;
  const h = rc.intersectObjects(SK.scene.children, true).filter(h => h.object.visible && h.object.isMesh && !isPerson(h.object) && h.object.material && h.object.material.type !== 'ShaderMaterial' && !(h.object.material.transparent && h.object.material.opacity < 0.99) && h.face && h.face.normal.y > 0.3);
  return h.length ? h[0].point.y : null; };
function thigh(g) { const out = []; const v = V3(); g.userData.dbg.legs.forEach(l => { const hip = l.th.getWorldPosition(V3()); let lo = 1e9;
  l.th.children.find(c => c !== l.sn).traverse(o => { if (!o.isMesh || o.material.type === 'ShaderMaterial') return; const P = o.geometry.attributes.position; for (let i = 0; i < P.count; i++) { v.fromBufferAttribute(P, i).applyMatrix4(o.matrixWorld); if (Math.hypot(v.x - hip.x, v.z - hip.z) < 0.1 && v.y < lo) lo = v.y; } });
  out.push({ hip, lo }); }); return out; }
/* breakfast time: diners walk in, sit and eat; the player takes a plate and sits too (third person, so the body is placed) */
const camWas = SK.State.cam.walk; SK.State.cam.walk = 'third'; SK.P6.setTime(470); SK.play(Math.round((window.PQ_A3_SIM || 40) * 30));
{ const BF = SK.BF, s0 = BF.SEATS.find(q => !q.taken); if (BF.B.seat) BF.standUp(); SK.W.on = true; BF.B.plate = ['eggs', 'juice']; BF.sit(s0); SK.play(60); info.player = !!BF.B.seat; }
/* diners on their feet: soles on the breakfast floor */
info.diners = SK.BF.guests.filter(q => (q.g || q).visible && q.st !== 'sit' && q.st !== 'eat').map(q => { const g = q.g || q, w = g.getWorldPosition(V3()), fl = SK.SURF.at(w.x, w.z, w.y - 0.15, w.y + 0.15); return fl === null ? null : +(w.y - fl).toFixed(3); }).filter(v => v !== null);
chk(info.diners.every(d => Math.abs(d) < 0.03), 'diners off the floor: ' + JSON.stringify(info.diners));
const people = []; SK.scene.traverse(o => { if (o.userData && o.userData.person && o.userData.animInfo) { let vis = true; for (let p = o; p; p = p.parent) if (!p.visible) vis = false; const a = o.userData.animInfo(); if (vis && a && (a.nm === 'Sit on chair' || a.nm === 'Eating') && a.t > 1.0) people.push(o); } });
people.forEach(g => { g.updateMatrixWorld(true); const w = g.getWorldPosition(V3()), T = thigh(g); let worst = 0, miss = 0;
  T.forEach(t => { const s = seatBelow(t.hip.x, t.lo + 0.25, t.hip.z); if (s === null) { miss++; return; } const d = t.lo - s; if (Math.abs(d) > Math.abs(worst)) worst = d; });
  const r = { at: [+w.x.toFixed(1), +w.y.toFixed(2), +w.z.toFixed(1)], seatH: g.userData.seatH, gap: +worst.toFixed(3), miss }; info.seated.push(r);
  chk(!miss && Math.abs(worst) < 0.03, (miss ? 'no seat under ' : 'thighs ' + (worst > 0 ? 'above' : 'inside') + ' the seat by ' + Math.abs(worst * 100).toFixed(1) + ' cm: ') + JSON.stringify(r.at) + ' seatH ' + r.seatH); });
SK.BF.standUp(); SK.State.cam.walk = camWas; info.n = people.length; chk(people.length >= 5, 'expected seated people, found ' + people.length);
return { ok: !fails.length, fails, info };
