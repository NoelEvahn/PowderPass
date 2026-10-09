/* C1: first person is the real body. The avatar is drawn with its head hidden, the camera sits at its eyes, looking around never turns
   the hips / legs / skis, poles stay in the hands, the old floating mittens and skis are gone, and switching camera keeps the pose. */
const fails = [], info = {}, chk = (c, m) => { if (!c) fails.push(m); };
const PL = SK.PL, W = SK.W, V3 = () => new THREE.Vector3(), Q = () => new THREE.Quaternion(), camW = SK.State.cam.walk, camS = SK.State.cam.ski;
const eyeW = av => av.p.userData.dbg.head.localToWorld(V3().set(0, 0.34, 0.22));
const yawOf = o => { const q = o.getWorldQuaternion(Q()), f = V3().set(0, 0, 1).applyQuaternion(q); return Math.atan2(f.x, f.z); };
const dA = (a, b) => Math.abs(Math.atan2(Math.sin(a - b), Math.cos(a - b)));
/* 1. walking, first person */
{ SK.State.cam.walk = 'first'; W.on = true; W.sit = null; if (PL.mode === 'ski') PL.exitSki(); W.x = -24; W.z = SK.RES_Z + 55; W.y = SK.groundY(W.x, W.z); W.yaw = 0.4; PL.walkFace = 0.4; W.pitch = 0; W.vx = W.vz = 0; SK.play(20);
  const A = SK.AVW, d = A.p.userData.dbg; info.walk = { vis: A.wrap.visible, head: d.head.visible, camEye: +SK.cam.position.distanceTo(eyeW(A)).toFixed(3) };
  chk(A.wrap.visible && !d.head.visible, 'first-person walker: body ' + A.wrap.visible + ', head ' + d.head.visible + ' (want body shown, head hidden)');
  chk(info.walk.camEye < 0.03, 'walking camera ' + info.walk.camEye + ' m from the eyes');
  const y0 = yawOf(d.pelvis), q0 = A.wrap.quaternion.clone(); const look = []; [[-1.2, 0.4], [0.8, 0.4], [0, 1.3], [0, -0.5]].forEach(([p, y]) => { W.pitch = p; W.yaw = 0.4 + (y - 0.4); SK.play(3); look.push(+dA(yawOf(d.pelvis), y0).toFixed(3)); });
  info.walk.pelvisTurn = look; chk(look[0] < 0.05 && look[1] < 0.05, 'hips turned by looking down/up: ' + JSON.stringify(look));
  chk(look[2] < 0.15 && look[3] < 0.15, 'hips followed a sideways look inside the turn-in-place angle: ' + JSON.stringify(look));
  W.pitch = 0; W.yaw = 0.4; SK.play(30); }
/* 2. skiing, first person: look round, skis and hips keep the heading */
{ SK.State.cam.ski = 'first'; W.x = -24; W.z = SK.RES_Z + 55; W.y = SK.groundY(W.x, W.z); W.yaw = 1.2; PL.enterSki(); PL.lookOff = 0; PL.lookPitch = -0.22; SK.play(20, 1 / 30);
  const A = SK.AVS, d = A.p.userData.dbg; info.ski = { vis: A.wrap.visible, head: d.head.visible, camEye: +SK.cam.position.distanceTo(eyeW(A)).toFixed(3) };
  chk(A.wrap.visible && !d.head.visible, 'first-person skier: body ' + A.wrap.visible + ', head ' + d.head.visible);
  chk(info.ski.camEye < 0.05, 'ski camera ' + info.ski.camEye + ' m from the eyes');
  const s0 = d.skis.map(yawOf), p0 = yawOf(d.pelvis), qw = A.wrap.quaternion.clone(), turns = [];
  [[-1.2, 0], [0.6, 0], [-0.2, 1.1], [-0.2, -1.1]].forEach(([p, o]) => { PL.lookPitch = p; PL.lookOff = o; SK.play(2, 1 / 30); turns.push(+Math.max(dA(yawOf(d.pelvis), p0), ...d.skis.map((q, i) => dA(yawOf(q), s0[i]))).toFixed(3)); });
  info.ski.turns = turns; info.ski.wrapTurn = +A.wrap.quaternion.angleTo(qw).toFixed(3);
  chk(turns.every(v => v < 0.12), 'skis / hips turned with the look: ' + JSON.stringify(turns)); chk(info.ski.wrapTurn < 0.02, 'skier body rotated by the look ' + info.ski.wrapTurn);
  /* poles in the hands */
  const gap = d.arms.map(a => { const h = a.el.children.find(c => c.children.includes(a.pole)) || a.pole.parent; return +a.pole.getWorldPosition(V3()).distanceTo(h.getWorldPosition(V3())).toFixed(3); }); info.ski.poleGap = gap;
  chk(gap.every(v => v < 0.01), 'pole not held: ' + JSON.stringify(gap));
  /* camera switch keeps the body where it was and the move it was in */
  PL.lookOff = 0; PL.lookPitch = -0.22; SK.play(2, 1 / 30); const a0 = A.p.userData.animInfo(), pp = A.wrap.position.clone(), pq = A.wrap.quaternion.clone();
  SK.State.cam.ski = 'third'; SK.play(1, 1 / 30); const a1 = A.p.userData.animInfo();
  info.ski.switch = { sameMove: a0 === a1, moved: +A.wrap.position.distanceTo(pp).toFixed(3), turned: +A.wrap.quaternion.angleTo(pq).toFixed(3), headShown3rd: d.head.visible };
  chk(a0 === a1 && info.ski.switch.moved < 0.05 && info.ski.switch.turned < 0.05, 'camera switch jumped the body: ' + JSON.stringify(info.ski.switch)); chk(d.head.visible, 'head still hidden in third person');
  PL.exitSki(); }
/* 3. no stand-in mittens / skis */
{ let shown = 0; SK.VM.rig.children.forEach(c => { if (c !== SK.VM.skis && c.visible && c.children.length && c.type === 'Group' && c !== SK.VM.rig.children[0]) shown++; }); info.vmShown = shown + (SK.VM.skis.visible ? 1 : 0); chk(!info.vmShown, 'old view-model mittens / skis still drawn'); }
/* 4. chairlift: seated in your own body, head hidden, camera at its eyes */
{ SK.State.cam.walk = 'first'; let car = null; SK.scene.traverse(o => { if (!car && o.userData && o.userData.seats && o.userData.kind === 0 && o.userData.seats.every(q => !q) && o.visible) car = o; });
  if (car) { const p = car.getWorldPosition(V3()); W.x = p.x; W.z = p.z; SK.startBoard(car, 0); SK.play(130); const A = SK.AVW;
    info.ride = { riding: !!W.ride, vis: A.wrap.visible, head: A.p.userData.dbg.head.visible, anim: A.p.userData.animInfo().nm, camEye: +SK.cam.position.distanceTo(eyeW(A)).toFixed(3) };
    chk(info.ride.riding && info.ride.vis && !info.ride.head && info.ride.anim === 'Sit on chair' && info.ride.camEye < 0.05, 'chairlift body: ' + JSON.stringify(info.ride)); W.toggle(true, 'here'); } else fails.push('no free chair to ride'); }
SK.State.cam.walk = camW; SK.State.cam.ski = camS; return { ok: !fails.length, fails, info };
