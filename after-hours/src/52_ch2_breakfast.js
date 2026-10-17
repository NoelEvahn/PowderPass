// ───────────────────────── Chapter 2 — Breakfast ─────────────────────────
chapter(27, 'Breakfast');
const win = (t, a, b, c, d) => Math.min(sseg(t, a, b), 1 - sseg(t, c, d));

// ── Codex: espresso + toast ──
const CXK = { mach: [1.45, 0, -2.55], btn: [1.53, COUNTER_H + 0.264, -2.395], cupSpot: [1.45, COUNTER_H + 0.019, -2.454], lever: [2.26, COUNTER_H + 0.15, -2.55], slot: [2.1, COUNTER_H + 0.15, -2.585] };
const cxWalk1 = pathMover([[-0.7, -1.25], [0.55, -1.75], [1.45, -2.2]], 27.0, 29.0);
const cxSide1 = pathMover([[1.45, -2.2], [2.02, -2.2]], 30.3, 30.8);
const cxSide2 = pathMover([[2.02, -2.2], [1.45, -2.2]], 34.4, 34.9);
const cxRun = pathMover([[1.45, -2.05], [2.2, -1.38], [3.4, -0.1], [4.55, 1.05]], 56.4, 58.4, EASE.in);
const cxArms2 = K([
  [27, {}], [35.0, {}],
  [36.4, { spX: 0.0 }],
], REST);
SEQ.cxKitchen = (t, env, sh, info) => {
  const R = A.cx; show(R, env);
  let p = Object.assign({}, REST), lookAt = null;
  if (t < 29.0) { p = walkPose(R, cxWalk1, t, REST, { speed: 1.1 }).p; }
  else {
    p.x = 1.45; p.z = -2.2; p.yaw = lerp(cxWalk1(29).yaw, Math.PI, sseg(t, 29.0, 29.35));
    if (t >= 30.3 && t < 30.8) p = walkPose(R, cxSide1, t, Object.assign(p, { yaw: Math.PI }), { side: 1, keepYaw: 1, speed: 1.0 }).p;
    else if (t >= 30.8 && t < 34.4) { p.x = 2.02; p.yaw = Math.PI; }
    else if (t >= 34.4 && t < 34.9) p = walkPose(R, cxSide2, t, Object.assign(p, { yaw: Math.PI }), { side: 1, keepYaw: 1, speed: 1.0 }).p;
    if (t >= 35.45 && t < 56.4) { const u = sseg(t, 35.45, 36.0); p.yaw = Math.PI + u * Math.PI; p.z = -2.2 + u * 0.15; p.x = 1.45; if (u > 0 && u < 1) { p.llF += Math.sin(u * Math.PI * 2) * 0.25; p.rlF -= Math.sin(u * Math.PI * 2) * 0.25; p.lkB += Math.max(0, Math.sin(u * TAU)) * 0.4; p.rkB += Math.max(0, -Math.sin(u * TAU)) * 0.4; } }
    if (t >= 56.4) { p = walkPose(R, cxRun, t, REST, { speed: 1.6, leg: 0.6, arm: 0.8, lean: 0.25, knee: 1.2 }).p; }
  }
  p.spX += (win(t, 29.3, 29.5, 29.8, 30.1) + win(t, 34.9, 35.1, 35.25, 35.45) + win(t, 55.9, 56.05, 56.2, 56.4)) * 0.18;
  applyPose(R, p);
  // ── props ──
  const cup = env.props.cup, toast = env.props.toast;
  // espresso brew (fill rises 31.0–33.4)
  const fill = sseg(t, 31.0, 33.4);
  env.cupFill.position.y = 0.01 + fill * 0.04; env.cupFill.visible = t > 31.0;
  const sipAmt = sseg(t, 36.0, 36.6) * 0.35;
  env.cupFill.scale.setScalar(1);
  if (t > 31.0 && t < 33.5) fxStream('espresso', env, [1.45, COUNTER_H + 0.168, -2.454], [1.45, COUNTER_H + 0.03 + fill * 0.03, -2.454], 0.004 + 0.002 * Math.sin(t * 40), 0x4a2a18);
  if (t > 30.0 && t < 34.5) fxSteam(env, [1.45, COUNTER_H + 0.1, -2.44], t, 31.2, 34.0, { rate: 10, size: 0.04, alpha: 0.25, rise: 0.18 });
  // IK: button press (right), toaster lever (left), catch toast (left), cup (right)
  const pressDepth = win(t, 29.55, 29.62, 29.72, 29.8) * 0.015;
  armIK(R, 'r', [CXK.btn[0], CXK.btn[1], CXK.btn[2] - pressDepth], win(t, 29.3, 29.55, 29.85, 30.15));
  env.espressoBtn.position.z = 0.19 - pressDepth * 0.6;
  const leverDown = t > 31.15 && t < 33.8 ? 1 : sseg(t, 30.95, 31.15) * (t < 33.8 ? 1 : 0);
  env.toastLever.position.y = 0.15 - leverDown * 0.09;
  armIK(R, 'l', [CXK.lever[0], CXK.lever[1] + 0.02 - leverDown * 0.09, CXK.lever[2] + 0.02], win(t, 30.75, 30.95, 31.25, 31.55));
  // toast pop (33.8), caught by left hand (34.12)
  const caught = t >= 34.12;
  const catchPt = [2.06, COUNTER_H + 0.42, -2.42];
  armIK(R, 'l', catchPt, win(t, 33.75, 34.05, 34.4, 34.9) * (t < 34.4 ? 1 : 0), [0.6, -0.3, -0.8]);
  if (t < 34.12 || t > 40.5) {
    if (t < 30.9 || t > 40.5) toast.visible = false;
    else {
      const u = t - 33.8;
      const y = u < 0 ? CXK.slot[1] : CXK.slot[1] + u * 2.0 - 4.9 * u * u * 2.2;
      placeProp(toast, env, [CXK.slot[0], Math.max(CXK.slot[1], Math.min(y, CXK.slot[1] + 0.3)), CXK.slot[2]], [0, 0, u > 0 ? u * 9 : 0]);
    }
  }
  // cup: under machine until 35.35, then in right hand; set down on the bar table at 57.0
  const cupHeld = t >= 35.35 && t < 57.0;
  const tableSpot = [2.5, 0.635, -1.1];
  const cupTarget = (() => {
    if (t < 35.35) return [1.5, COUNTER_H + 0.05, -2.454];
    const m = mouthPos(R, env, 0.26, -0.12);
    const chest = (() => { const v = new THREE.Vector3(-0.13, 0.28, 0.24); R.root.localToWorld(v); env.group.worldToLocal(v); return [v.x, v.y, v.z]; })();
    const toMouth = Math.max(win(t, 36.1, 36.45, 36.75, 37.1), win(t, 55.85, 56.05, 56.25, 56.45));
    const a = chest.map((v, i) => lerp(v, m[i], toMouth));
    return a.map((v, i) => lerp(v, [tableSpot[0] + 0.045, tableSpot[1] + 0.04, tableSpot[2]][i], sseg(t, 56.65, 57.0)));
  })();
  if (t >= 34.9 && t < 57.3) armIK(R, 'r', cupTarget, t < 35.35 ? sseg(t, 34.95, 35.3) : t > 57.0 ? 1 - sseg(t, 57.0, 57.3) : 1);
  if (cupHeld) {
    const tilt = Math.max(win(t, 36.4, 36.5, 36.7, 36.85) * 0.9, win(t, 56.0, 56.08, 56.25, 56.35) * 1.3);
    const yw = R.root.rotation.y;
    holdUpright(cup, R, 'r', env, [-tilt, yw, 0], [0, 0, 0], [-0.045 * Math.cos(yw), -0.035, 0.045 * Math.sin(yw)]);
  } else if (t >= 57.0) placeProp(cup, env, tableSpot);
  else placeProp(cup, env, CXK.cupSpot);
  if (t > 56.1) env.cupFill.visible = false;
  // toast in left hand + bite at 39.25
  if (caught && t <= 40.5) {
    const m = mouthPos(R, env, 0.24, -0.12);
    const hold = (() => { const v = new THREE.Vector3(0.13, 0.25, 0.22); R.root.localToWorld(v); env.group.worldToLocal(v); return [v.x, v.y, v.z]; })();
    const toM = win(t, 38.95, 39.2, 39.35, 39.65);
    if (t > 34.4) armIK(R, 'l', hold.map((v, i) => lerp(v, m[i], toM)), sseg(t, 34.4, 34.9));
    holdUpright(toast, R, 'l', env, [0, R.root.rotation.y, 0.2], [0, 0, 0.0], [0, 0.02, 0]);
    env.toastBite.visible = t > 39.3;
  }
  if (t > 39.3 && t < 39.9) fxSparks(env, mouthPos(R, env, 0.24, -0.12), t, 39.3, { n: 10, color: 0xe0b070, speed: 0.6, up: 0.2, size: 0.014, life: 0.6, flash: 0 });
  // look targets
  if (t > 29.2 && t < 30.3) lookAt = CXK.btn;
  else if (t > 30.5 && t < 31.5) lookAt = CXK.lever;
  else if (t > 31.5 && t < 33.6) lookAt = [1.45, COUNTER_H + 0.1, -2.45];
  else if (t > 33.6 && t < 34.3) lookAt = [2.08, COUNTER_H + 0.45, -2.5];
  if (lookAt) headLook(R, lookAt, 0.8);
  // face
  const f = R.face; f.boot = 1; f.open = 1; f.extra = null; f.lookX = 0; f.lookY = 0;
  f.mood = t < 31 ? 'focus' : t < 33.8 ? 'neutral' : t < 34.3 ? 'surprised' : t < 36.3 ? 'happy' : t < 36.9 ? 'sleepy' : t < 39.3 ? 'smug' : t < 40 ? 'happy' : t < 55.25 ? 'neutral' : t < 55.6 ? 'surprised' : 'smug';
  if (t > 55.2 && t < 55.7) headLook(R, [1.98, 1.18, -1.6], 0.8);
  // espresso display
  const disp = t < 29.6 ? 'READY' : t < 31 ? 'GRIND…' : t < 33.4 ? 'BREW ' + Math.floor(sseg(t, 31, 33.4) * 100) + '%' : 'DONE ✓';
  if (env._disp !== disp) { env._disp = disp; const c = env.espressoTex.userData.ctx; c.fillStyle = '#0b1517'; c.fillRect(0, 0, 128, 32); c.fillStyle = '#5ff2cf'; c.font = `700 20px ${FONT_MONO}`; c.textAlign = 'center'; c.fillText(disp, 64, 23); env.espressoTex.needsUpdate = true; }
  env.props.bag.visible = false;
  // notification hologram
  if (t > 55.15 && t < 57.8) {
    const s = EASE.back(sseg(t, 55.15, 55.45)) * (1 - sseg(t, 57.4, 57.8));
    fxHolo('note', env, [1.98, 1.18, -1.6], 0.56, 0.31, drawGptNote(['Good morning! ☀', '47 new requests today.']), 'cx', { yaw: 0, scale: s });
  }
  homeAmbient(env, t, 'cx');
  info.focus = new THREE.Vector3(p.x, 0.5, p.z);
};

// ── Claude: kettle, teapot, tea + scone ──
const CCK = { kettle: [-3.46, COUNTER_H + 0.035, -2.45], teapot: [-3.0, COUNTER_H, -2.47], cup: [-2.72, COUNTER_H, -2.47], plate: [-2.45, COUNTER_H, -2.45] };
const ccSide1 = pathMover([[-3.55, -2.17], [-3.25, -2.17]], 41.3, 41.8);
const ccSide2 = pathMover([[-3.25, -2.17], [-3.55, -2.17]], 43.0, 43.4);
const ccSide3 = pathMover([[-3.55, -2.17], [-2.86, -2.17]], 43.8, 44.45);
SEQ.ccKitchen = (t, env, sh, info) => {
  const R = A.cc; show(R, env);
  let p = Object.assign({}, REST, { x: -3.55, z: -2.17, yaw: Math.PI });
  const sideStyle = { side: 1, keepYaw: 1, speed: 0.8 };
  if (t >= 41.3 && t < 41.8) p = walkPose(R, ccSide1, t, p, sideStyle).p;
  else if (t >= 41.8 && t < 43.0) p.x = -3.25;
  else if (t >= 43.0 && t < 43.4) p = walkPose(R, ccSide2, t, p, sideStyle).p;
  else if (t >= 43.4 && t < 43.8) p.x = -3.55;
  else if (t >= 43.8 && t < 44.45) p = walkPose(R, ccSide3, t, p, sideStyle).p;
  else if (t >= 44.45) p.x = -2.86;
  if (t >= 51.1) { const u = sseg(t, 51.1, 51.6); p.yaw = Math.PI + u * Math.PI; p.z = -2.17 + u * 0.12; if (u > 0 && u < 1) { p.llF += Math.sin(u * TAU) * 0.22; p.rlF -= Math.sin(u * TAU) * 0.22; p.lkB += Math.max(0, Math.sin(u * TAU)) * 0.35; p.rkB += Math.max(0, -Math.sin(u * TAU)) * 0.35; } }
  p.spX += win(t, 40.6, 40.9, 43.4, 43.8) * 0.22 + win(t, 48.5, 48.8, 50.2, 50.5) * 0.18 + win(t, 50.3, 50.6, 50.8, 51.1) * 0.15;
  p.nkX += win(t, 44.5, 44.8, 47.8, 48.2) * 0.25;
  applyPose(R, p);
  const { kettle, teapot, cup, plate, scone, tumbler } = env.props;
  tumbler.visible = false;
  // kettle
  const handleOff = [0, -0.235, 0];
  const kHeld = t >= 41.1 && t < 43.5;
  const above = [-3.12, COUNTER_H + 0.42, -2.42];
  const kTarget = (() => {
    const rest = [CCK.kettle[0], CCK.kettle[1] + 0.235, CCK.kettle[2]];
    const u = sseg(t, 41.15, 41.7), back = sseg(t, 42.95, 43.45);
    return rest.map((v, i) => lerp(lerp(v, above[i], u), v, back));
  })();
  armIK(R, 'r', kTarget, win(t, 40.75, 41.1, 43.5, 43.85), [-0.5, -0.4, -0.8]);
  const pourTilt = win(t, 41.85, 42.15, 42.75, 42.95) * 0.75;
  if (kHeld) holdUpright(kettle, R, 'r', env, [0, 0, -pourTilt], [0, 0, 0], [0, -0.235, 0]);
  else placeProp(kettle, env, CCK.kettle);
  if (pourTilt > 0.5) {
    kettle.updateMatrixWorld(true);
    const sp = env.kettleSpout.clone(); kettle.localToWorld(sp); env.group.worldToLocal(sp);
    fxStream('kettle', env, [sp.x, sp.y, sp.z], [CCK.teapot[0], CCK.teapot[1] + 0.13, CCK.teapot[2]], 0.005, 0xd6ecf2);
  }
  if (t > 40.1 && t < 41.6) {
    kettle.updateMatrixWorld(true); const sp = env.kettleSpout.clone().add(new THREE.Vector3(0.02, 0.01, 0)); kettle.localToWorld(sp); env.group.worldToLocal(sp);
    fxSteam(env, [sp.x, sp.y, sp.z], t, 40.15, 41.5, { rate: 30, size: 0.05, alpha: 0.35, rise: 0.4, dx: 0.25, life: 0.9 });
  }
  env.burner.material.color.set(0xff6a2a).multiplyScalar(t < 43.6 ? 1.6 : 0.15);
  // teapot (left hand) pours into cup 49.2–49.9
  const tpHandle = [CCK.teapot[0] - 0.085, CCK.teapot[1] + 0.075, CCK.teapot[2]];
  const tpHeld = t >= 48.9 && t < 50.15;
  const tpAbove = [-2.93, COUNTER_H + 0.24, -2.45];
  const tpT = tpHandle.map((v, i) => lerp(lerp(v, tpAbove[i], sseg(t, 48.9, 49.2)), v, sseg(t, 49.85, 50.15)));
  armIK(R, 'l', tpT, win(t, 48.55, 48.9, 50.15, 50.45), [0.5, -0.4, -0.8]);
  const tpTilt = win(t, 49.15, 49.35, 49.7, 49.9) * 0.7;
  if (tpHeld) holdUpright(teapot, R, 'l', env, [0, 0, -tpTilt], [0, 0, 0], [0.085, -0.075, 0]);
  else placeProp(teapot, env, CCK.teapot);
  if (tpTilt > 0.45) { teapot.updateMatrixWorld(true); const sp = new THREE.Vector3(0.125, 0.115, 0); teapot.localToWorld(sp); env.group.worldToLocal(sp); fxStream('teapot', env, [sp.x, sp.y, sp.z], [CCK.cup[0], CCK.cup[1] + 0.05, CCK.cup[2]], 0.004, 0xb8662c); }
  const teaFill = sseg(t, 49.3, 49.9);
  env.cupFill.visible = teaFill > 0.02; env.cupFill.position.y = 0.012 + teaFill * 0.034;
  env.teaTag.visible = t > 44;
  // cup (right hand, 50.7) and scone (left hand, 51.05); turn to the room; sip 51.7; bite 53.2
  const m = mouthPos(R, env, 0.27, -0.13);
  const local = (x, y, z) => { const v = new THREE.Vector3(x, y, z); R.root.localToWorld(v); env.group.worldToLocal(v); return [v.x, v.y, v.z]; };
  const cHeld = t >= 50.7;
  const cTarget = (() => {
    const handle = [CCK.cup[0] + 0.046, CCK.cup[1] + 0.035, CCK.cup[2]];
    if (t < 50.7) return handle;
    const chest = local(-0.11, 0.2, 0.25);
    const toM = win(t, 51.7, 52.0, 52.3, 52.6);
    return chest.map((v, i) => lerp(v, m[i], toM));
  })();
  armIK(R, 'r', cTarget, sseg(t, 50.35, 50.7), [-0.5, -0.4, -0.8]);
  if (cHeld) {
    const tilt = win(t, 51.95, 52.05, 52.3, 52.45) * 0.85;
    const yw = R.root.rotation.y;
    holdUpright(cup, R, 'r', env, [-tilt, yw, 0], [0, 0, 0], [-0.046 * Math.cos(yw), -0.035, 0.046 * Math.sin(yw)]);
  } else placeProp(cup, env, CCK.cup);
  placeProp(plate, env, CCK.plate);
  const sHeld = t >= 51.05;
  const sTarget = (() => { const pl = [CCK.plate[0], CCK.plate[1] + 0.04, CCK.plate[2]]; if (t < 51.05) return pl; const toM = win(t, 52.95, 53.15, 53.3, 53.55); const hold = local(0.12, 0.2, 0.24); return hold.map((v, i) => lerp(v, m[i], toM)); })();
  if (t > 50.6) armIK(R, 'l', sTarget, sseg(t, 50.7, 51.05));
  if (sHeld) holdUpright(scone, R, 'l', env, [0, R.root.rotation.y, 0], [0, 0, 0], [0, -0.03, 0]);
  else placeProp(scone, env, [CCK.plate[0], CCK.plate[1] + 0.012, CCK.plate[2]]);
  env.sconeBite.visible = t > 53.2;
  // loupe flip (44.6 → 48.1)
  const lp = win(t, 44.6, 45.0, 47.9, 48.2);
  R.j.loupe.rotation.x = -lp * 0.0; R.j.loupe.rotation.y = 0; R.j.loupe.visible = true;
  setLoupe(R, lp);
  // looks
  let lookAt = null;
  if (t < 41.2) lookAt = CCK.kettle; else if (t < 43.0) lookAt = CCK.teapot; else if (t < 44.5) lookAt = CCK.teapot;
  else if (t < 48.2) lookAt = [CCK.teapot[0] + 0.05, CCK.teapot[1] + 0.08, CCK.teapot[2]]; else if (t < 50.4) lookAt = CCK.cup;
  else if (t > 53.6 && t < 54.6) lookAt = [-2.35, 1.15, -1.75];
  if (lookAt) headLook(R, lookAt, 0.7);
  const f = R.face; f.boot = 1; f.open = 1; f.lookX = 0; f.lookY = 0;
  f.mood = t < 41 ? 'neutral' : t < 43.5 ? 'focus' : t < 44.6 ? 'neutral' : t < 47.9 ? 'focus' : t < 48.5 ? 'happy' : t < 51.9 ? 'neutral' : t < 52.4 ? 'sleepy' : t < 53.6 ? 'happy' : t < 54.0 ? 'surprised' : 'happy';
  if (t > 44.6 && t < 48.2) headLook(R, [-2.05, 0.86, -2.4], 0.75, 1.3);
  f.extra = t > 44.7 && t < 48.2 ? (t < 47.85 ? '⏱ 3:00' : '✓ steeped') : null;
  if (t > 53.6 && t < 55.0) {
    const s = EASE.back(sseg(t, 53.6, 53.9)) * (1 - sseg(t, 54.75, 55.0));
    fxHolo('note2', env, [-2.35, 1.15, -1.75], 0.56, 0.31, drawGptNote(['Good morning! ☀', '47 new requests today.'], '#8ff0c9'), 'cc', { yaw: 0, scale: s });
  }
  homeAmbient(env, t, 'cc');
  info.focus = new THREE.Vector3(p.x, 0.5, p.z);
};
// loupe: hinge from up (0) to down over the left eye (1)
function setLoupe(R, u) {
  const L = R.j.loupe; if (!L) return;
  L.rotation.set(lerp(-1.25, 0, u), 0, 0);
}

// ── shots ──
shot(27.0, 31.0, 'cxHome', 'cxKitchen', camK([[27, [-0.4, 1.0, 0.9], [0.6, 0.5, -1.8], 42], [31, [0.2, 0.95, 0.2], [1.4, 0.55, -2.3], 40]]), { fadeIn: 0.5 });
shot(31.0, 35.0, 'cxHome', 'cxKitchen', camK([[31, [3.35, 0.86, -2.33], [1.8, 0.6, -2.38], 40], [35, [3.25, 0.84, -2.3], [1.75, 0.62, -2.35], 38]]));
shot(35.0, 40.0, 'cxHome', 'cxKitchen', camK([[35, [1.7, 0.88, -0.45], [1.45, 0.76, -2.05], 32], [40, [1.66, 0.86, -0.6], [1.45, 0.8, -2.05], 29]]));
shot(40.0, 44.5, 'ccHome', 'ccKitchen', camK([[40, [-1.55, 0.88, -2.4], [-3.3, 0.62, -2.4], 40], [44.5, [-1.6, 0.86, -2.38], [-3.1, 0.62, -2.35], 38]]));
shot(44.5, 48.5, 'ccHome', 'ccKitchen', camK([[44.5, [-2.05, 0.86, -2.42], [-2.86, 0.84, -2.2], 32], [48.5, [-2.08, 0.86, -2.4], [-2.86, 0.84, -2.2], 29]]));
shot(48.5, 51.0, 'ccHome', 'ccKitchen', camK([[48.5, [-1.7, 0.86, -2.42], [-2.9, 0.6, -2.4], 38], [51, [-1.75, 0.86, -2.4], [-2.9, 0.62, -2.35], 36]]));
shot(51.0, 55.0, 'ccHome', 'ccKitchen', camK([[51, [-2.65, 0.88, -0.65], [-2.86, 0.72, -2.05], 32], [55, [-2.6, 0.9, -0.75], [-2.8, 0.78, -2.0], 30]]));
shot(55.0, 58.5, 'cxHome', 'cxKitchen', camK([[55, [1.68, 0.88, -0.55], [1.5, 0.8, -2.0], 32], [56.4, [1.6, 0.92, -0.5], [1.7, 0.75, -1.8], 38], [58.5, [1.2, 1.15, -0.9], [4.3, 0.6, 0.9], 48]]));

say(37.0, 'codex', 'Espresso. Fast, strong, zero dependencies.', 2.4);
say(44.9, 'claude', 'Steeping: three minutes.', 1.5);
say(46.5, 'claude', 'Not two. Not “roughly three.”', 1.5);
say(52.5, 'claude', 'Mm. Reviewed… and approved.', 1.6);
say(54.15, 'claude', 'Forty-seven. Lovely.', 0.9);
say(55.65, 'codex', 'Only forty-seven? Done by lunch.', 1.9);
sfx(27.0, 'steps', { who: 'cx', mv: cxWalk1 }); sfx(30.3, 'steps', { who: 'cx', mv: cxSide1, soft: true }); sfx(34.4, 'steps', { who: 'cx', mv: cxSide2, soft: true });
sfx(41.3, 'steps', { who: 'cc', mv: ccSide1, soft: true }); sfx(43.0, 'steps', { who: 'cc', mv: ccSide2, soft: true }); sfx(43.8, 'steps', { who: 'cc', mv: ccSide3, soft: true });
sfx(29.6, 'click'); sfx(29.8, 'grind', { dur: 1.2 }); sfx(31.0, 'pour', { dur: 2.4, thin: true }); sfx(31.1, 'lever'); sfx(31.2, 'hiss', { dur: 2.6 });
sfx(33.8, 'pop'); sfx(34.12, 'catch'); sfx(35.35, 'clink'); sfx(36.45, 'sip'); sfx(39.3, 'crunch'); sfx(56.05, 'sip', { gulp: true }); sfx(57.0, 'clink');
sfx(40.2, 'whistle', { dur: 1.3 }); sfx(41.1, 'clink', { low: true }); sfx(41.9, 'pour', { dur: 1.0 }); sfx(43.5, 'clink', { low: true });
sfx(47.85, 'ding'); sfx(49.2, 'pour', { dur: 0.7, thin: true }); sfx(50.7, 'clink'); sfx(52.05, 'sip'); sfx(53.2, 'crunch', { soft: true });
sfx(53.6, 'notify'); sfx(55.15, 'notify'); sfx(56.4, 'steps', { who: 'cx', mv: cxRun, run: true });
