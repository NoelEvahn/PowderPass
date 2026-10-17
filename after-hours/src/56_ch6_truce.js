// ───────────────────────── Chapter 6 — Same Ticket ─────────────────────────
chapter(146, 'Same Ticket');
TITLES.push({ t0: 171.0, t1: 178.6, sub: 'fin' });
const cxStepIn = pathMover([[0, -1.4], [0, -0.42]], 157.6, 158.6);
const ccStepIn = pathMover([[0, 1.4], [0, 0.42]], 157.6, 158.6);

function finalePoses(t, pcx, pcc) {
  if (t < 147.8) return;
  for (const [p, mv, yaw0, R] of [[pcx, cxStepIn, 0, A.cx], [pcc, ccStepIn, Math.PI, A.cc]]) {
    Object.assign(p, REST);
    const m = mv(t);
    p.x = m.x; p.z = m.z;
    const turn = sseg(t, 166.4, 167.1);
    p.yaw = lerp(yaw0, Math.PI / 2, turn);
    if (t > 157.6 && t < 158.7) walkLayer(p, (m.dist / strideLen(R)) * Math.PI, clamp(m.speed / 0.8), { leg: 0.42 });
    if (turn > 0 && turn < 1) { const s = Math.sin(turn * TAU); p.llF += s * 0.2; p.rlF -= s * 0.2; p.lkB += Math.max(0, s) * 0.3; p.rkB += Math.max(0, -s) * 0.3; }
    p.spX += 0.02 * Math.sin(t * 1.7);
    // awkward look-away lean
    p.spY += (R === A.cx ? -1 : 1) * win(t, 156.8, 157.1, 157.5, 157.7) * 0.25;
    // cheer hop
    const hop = win(t, 162.65, 162.8, 162.95, 163.1);
    if (hop > 0) { p.ground = 1 - hop * 0.999; p.y = R.dim.H + hop * 0.12; }
  }
}
function finaleArms(t, cx, cc, env) {
  if (t < 146.3) return false;
  for (const R of [cx, cc]) {
    const p = { x: R.root.position.x, y0: R.root.position.y, z: R.root.position.z, yaw: R.root.rotation.y };
    const k = (R === cx ? cxFight : ccFight)(Math.min(t, 146.3));
    // right arm: weapon hand drops, later handshake / cheer / fist bump
    let rT = cf(p, [k.rx, k.ry, k.rz]); let rb = 1 - sseg(t, 147.3, 147.9);
    const shake = win(t, 158.75, 159.0, 160.2, 160.5);
    if (shake > 0) { rT = [0, 0.66 + Math.sin((t - 159) * 14) * 0.025 * (t > 159.1 && t < 160 ? 1 : 0), 0]; rb = shake; }
    const bump = win(t, 163.6, 163.85, 164.05, 164.35);
    if (bump > 0) { rT = [0, 0.74, 0]; rb = bump; }
    const cheer = win(t, 162.6, 162.75, 163.15, 163.4);
    if (cheer > 0) { rT = cf(p, [-0.14, 0.78, 0.05]); rb = cheer; }
    armIK(R, 'r', rT, rb, [-0.7, -0.6, -0.4]);
    // left arm: shield eyes from the light, cheer
    let lT = cf(p, [0.02, 0.52, 0.2]); let lb = win(t, 146.35, 146.6, 147.2, 147.6);
    if (cheer > 0) { lT = cf(p, [0.14, 0.78, 0.05]); lb = cheer; }
    if (t < 147.6 && cheer <= 0) { const kl = (R === cx ? cxFight : ccFight)(Math.min(t, 146.3)); const fT = cf(p, [kl.lx, kl.ly, kl.lz]); armIK(R, 'l', fT, 1 - sseg(t, 146.3, 146.5)); }
    armIK(R, 'l', lT, lb, [0.7, -0.6, -0.4]);
    if (cheer > 0) { R.j.lhand.fingers.rotation.x = 0; R.j.rhand.fingers.rotation.x = 0; }
    if (bump > 0) R.j.rhand.fingers.rotation.x = 1.6;
  }
  return true;
}
const camNow = () => { const v = camera.position.clone(); ENVS.alley.group.worldToLocal(v); return [v.x, v.y, v.z]; };
function finaleFaces(t, cx, cc) {
  if (t < 146.3) return;
  const fx = cx.face, fc = cc.face;
  const pick = (list) => { let m = list[0][1]; for (const [tt, mm] of list) if (t >= tt) m = mm; return m; };
  fx.mood = pick([[146.3, 'surprised'], [147.4, 'worried'], [150.1, 'surprised'], [151.8, 'focus'], [153.8, 'annoyed'], [156.1, 'surprised'], [156.8, 'annoyed'], [158.8, 'neutral'], [160.4, 'focus'], [162.6, 'happy'], [163.45, 'smug'], [164.4, 'surprised'], [165.9, 'annoyed'], [166.7, 'determined'], [167.6, 'smug']]);
  fc.mood = pick([[146.3, 'surprised'], [147.4, 'worried'], [150.1, 'neutral'], [151.8, 'thinking'], [153.8, 'surprised'], [156.1, 'worried'], [156.8, 'annoyed'], [158.8, 'neutral'], [160.4, 'focus'], [162.6, 'happy'], [163.45, 'neutral'], [164.4, 'worried'], [166.0, 'surprised'], [167.0, 'focus'], [167.8, 'happy']]);
  const env = ENVS.alley;
  const gpt = [A.gpt.root.position.x, A.gpt.root.position.y, A.gpt.root.position.z];
  const looks = [[146.4, gpt], [149.5, [0, 1.9, 0]], [153.0, 'other'], [156.0, gpt], [156.8, 'away'], [157.7, 'other'], [160.5, [0, 1.45, 0]], [162.9, 'up'], [163.4, 'other'], [164.3, gpt], [166.0, [0.3, 1.5, 0]], [167.1, 'cam']];
  let L = looks[0][1]; for (const [tt, l] of looks) if (t >= tt) L = l;
  for (const [R, O] of [[cx, cc], [cc, cx]]) {
    let target;
    if (L === 'other') target = headPos(O, env);
    else if (L === 'away') { const h = headPos(R, env); target = [h[0] + (R === cx ? -1 : 1), h[1] - 0.1, h[2]]; }
    else if (L === 'up') { const h = headPos(R, env); target = [h[0], h[1] + 1, h[2] + (R === cx ? 0.4 : -0.4)]; }
    else if (L === 'cam') target = camNow();
    else target = L;
    headLook(R, target, 0.85, 1.4);
  }
  if (L === 'away') { fx.lookX = -0.6; fc.lookX = 0.6; }
}
function drawTicket(c, w, h) {
  holoFrame(c, w, h, '#8ff0c9', 'TICKET #4096');
  c.fillStyle = '#ffffff'; c.font = `700 40px ${FONT_UI}`;
  c.fillText('Minecraft: creepers drop flowers 🌼', 34, 140);
  c.fillStyle = '#c9fff0'; c.font = `600 32px ${FONT_UI}`;
  c.fillText('+ rainbow flowers  ·  + pigs too', 34, 195);
  c.fillStyle = '#8ff0c9'; c.font = `800 30px ${FONT_UI}`; c.fillText('ASSIGNEES', 34, 268);
  c.fillStyle = '#5ff2cf'; c.fillText('● Codex', 34, 316); c.fillStyle = '#ffb36e'; c.fillText('● Claude Code', 230, 316);
  c.fillStyle = '#ffd27a'; c.font = `700 28px ${FONT_UI}`; c.fillText('⚠ 2 agents · 1 ticket · 0 coordination', 34, 380);
}
function drawBuild(u) {
  return (c, w, h) => {
    const ok = u >= 1;
    holoFrame(c, w, h, ok ? '#9fe39a' : '#ffe0a0', ok ? 'BUILD PASSED ✓' : 'BUILDING…');
    c.fillStyle = '#ffffff'; c.font = `700 30px ${FONT_UI}`;
    c.fillText('PR #4096 · Codex + Claude Code', 34, 120);
    c.fillStyle = 'rgba(255,255,255,0.15)'; rrect(c, 34, 150, w - 68, 34, 17); c.fill();
    const g = c.createLinearGradient(34, 0, w - 34, 0); g.addColorStop(0, '#5ff2cf'); g.addColorStop(1, '#ffb36e');
    c.fillStyle = g; rrect(c, 34, 150, Math.max(34, (w - 68) * u), 34, 17); c.fill();
    c.font = `600 26px ${FONT_MONO}`; c.fillStyle = '#e8fff8';
    const steps = ['✓ compile        CreeperBloom.java', '✓ pytest         12 passed', '✓ gradle test    6 passed', '✓ integration    128 / 128'];
    steps.slice(0, Math.floor(u * 4.2)).forEach((s, i) => c.fillText(s, 34, 236 + i * 40));
    if (ok) { c.fillStyle = '#9fe39a'; c.font = `700 26px ${FONT_UI}`; c.fillText('Creepers now plant rainbow flowers. Pigs too.', 34, 410); }
  };
}
let _shock, _beam;
function finaleFX(t, env, info) {
  if (_beam) _beam.visible = false;
  if (t < 145.9) return;
  const G = A.gpt; show(G, env);
  const yDesc = lerp(4.2, 1.3, EASE.out3(sseg(t, 146.0, 147.4)));
  const y = yDesc + sseg(t, 157.2, 158.2) * 0.42;
  poseGPT(G, t, { x: 0, y, z: 0, yaw: Math.PI / 2 * sseg(t, 148.5, 149.3) + (t > 159 ? 0 : 0), scale: 0.62, gesture: t < 149.2 ? 2 : t > 156.0 && t < 157.6 ? 3 : t > 164.3 && t < 165.5 ? 1 : 0, tilt: 0 });
  G.face.mood = t < 150 ? 'stern' : t < 156 ? 'neutral' : 'cheer';
  // light column + shockwave
  const col = win(t, 145.95, 146.3, 147.2, 148.4);
  if (col > 0) {
    if (!_beam) {
      const bt = canvasTex(64, 256, (c, w, h) => { const g = c.createLinearGradient(0, 0, w, 0); g.addColorStop(0, 'rgba(255,255,255,0)'); g.addColorStop(0.5, 'rgba(255,255,255,1)'); g.addColorStop(1, 'rgba(255,255,255,0)'); c.fillStyle = g; c.fillRect(0, 0, w, h); });
      _beam = new THREE.Mesh(new THREE.CylinderGeometry(0.35, 0.75, 7, 24, 1, true), new THREE.MeshBasicMaterial({ map: bt, color: new THREE.Color(0.9, 1.6, 1.4), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide, toneMapped: false }));
      env.group.add(_beam);
    }
    _beam.position.set(0, 3.5, 0); _beam.material.opacity = 0.45 * col; _beam.visible = true; _beam.rotation.y = t * 0.3;
    info.flash = Math.max(info.flash || 0, win(t, 146.5, 146.6, 146.7, 147.0) * 0.6);
    info.bloom = 1.1;
  }
  if (!_shock) { _shock = new THREE.Mesh(new THREE.RingGeometry(0.9, 1.0, 64), new THREE.MeshBasicMaterial({ color: new THREE.Color(1.5, 2.2, 2.0), transparent: true, side: THREE.DoubleSide, blending: THREE.AdditiveBlending, depthWrite: false })); _shock.rotation.x = -Math.PI / 2; env.group.add(_shock); }
  const sa = t - 146.6;
  _shock.visible = sa > 0 && sa < 0.7;
  if (_shock.visible) { _shock.position.set(0, 0.03, 0); _shock.scale.setScalar(0.2 + sa * 5); _shock.material.opacity = 1 - sa / 0.7; }
  if (sa > 0 && sa < 0.6) fxSparks(env, [0, 0.4, 0], t, 146.6, { n: 60, speed: 3.5, up: 0.2, color: 0xdffff4, color2: 0x8ff0c9, life: 0.6, flash: 1.2 });
  // ticket hologram
  if (t > 149.3 && t < 157.0) {
    const s = EASE.back(sseg(t, 149.3, 149.7)) * (1 - sseg(t, 156.6, 157.0));
    fxHolo('ticket', env, [0, 1.6, 0.0], 1.0, 0.69, drawTicket, 'ticket', { yaw: -Math.PI / 2, scale: s });
  }
  // shared build
  if (t > 160.5 && t < 165.5) {
    const u = sseg(t, 160.9, 162.6);
    const s = EASE.back(sseg(t, 160.5, 160.85)) * (1 - sseg(t, 165.1, 165.5));
    fxHolo('build', env, [0, 1.5, 0], 1.3, 0.9, drawBuild(u), 'b' + Math.floor(u * 40), { yaw: Math.PI / 2, scale: s });
  }
  // confetti of code glyphs
  if (t > 162.6 && t < 165) for (let i = 0; i < 70; i++) {
    const a = t - 162.6, h1 = hash1(i * 3.1), h2 = hash1(i * 7.7), h3 = hash1(i * 1.9);
    const vx = (h1 - 0.5) * 2.2, vz = (h2 - 0.5) * 2.2, vy = 1.5 + h3 * 1.5;
    const yy = 1.5 + vy * a - 2.2 * a * a; if (yy < 0) continue;
    const c = i % 2 ? PAL.cxMint : PAL.ccAmber;
    fxGlow(env, [vx * a, yy, vz * a], 0.03, c, 0.9 * (1 - a / 2.4));
  }
  // the next request arrives
  if (t > 165.9 && t < 171.5) {
    const s = EASE.back(sseg(t, 165.9, 166.25)) * (1 - sseg(t, 171.0, 171.5));
    fxHolo('next', env, [0.15, 1.62, 0], 0.95, 0.52, drawGptNote(['NEW REQUEST', 'make the creepers dance 💃'], '#8ff0c9'), 'next', { yaw: Math.PI / 2, scale: s });
  }
}

// ── shots (Chapter 5 + 6) ──
shot(121.0, 126.0, 'alley', 'alley', camK([[121, [0.2, 6.2, 10.5], [0, 1.6, -3], 46], [126, [0.3, 1.25, 6.4], [0, 0.85, -1.6], 40]]), { fadeIn: 1.0 });
shot(126.0, 130.4, 'alley', 'alley', camK([[126, [0.55, 0.95, -2.95], [0, 0.75, 4.5], 40], [130.4, [0.5, 0.92, -2.9], [0, 0.75, 2.0], 38]]));
shot(130.4, 132.4, 'alley', 'alley', camK([[130.4, [0.35, 0.92, -0.55], [0, 0.85, -1.6], 38], [132.4, [0.3, 0.9, -0.6], [0, 0.8, -1.6], 34]]));
shot(132.4, 134.5, 'alley', 'alley', camK([[132.4, [-0.35, 0.92, 0.55], [0, 0.82, 1.6], 38], [134.5, [-0.3, 0.9, 0.6], [0, 0.8, 1.6], 34]]));
shot(134.5, 135.6, 'alley', 'alley', camK([[134.5, [1.95, 0.8, 0.1], [-0.2, 0.7, 0], 50], [135.6, [1.85, 0.85, 0.0], [-0.2, 0.75, 0], 46]]), { hand: 0.4 });
shot(135.6, 136.6, 'alley', 'alley', camK([[135.6, [0.55, 0.42, 2.3], [0, 0.6, -0.7], 46], [136.6, [0.5, 0.45, 2.2], [0, 0.6, -0.9], 46]]));
shot(136.6, 137.9, 'alley', 'alley', camK([[136.6, [1.8, 0.8, -0.2], [0, 0.8, 0], 42], [137.9, [1.7, 0.75, -0.3], [0, 0.85, 0], 40]]), { hand: 0.5 });
shot(137.9, 139.4, 'alley', 'alley', camK([[137.9, [1.25, 0.85, 0.35], [0, 0.75, 0], 40], [139.4, [1.3, 0.8, 0.2], [0, 0.75, 0.1], 42]]), { hand: 0.7 });
shot(139.4, 140.5, 'alley', 'alley', camK([[139.4, [-0.6, 0.5, -3.3], [0, 0.6, 0.6], 44], [140.5, [-0.5, 0.45, -2.9], [0, 0.7, 0.6], 44]]));
shot(140.5, 141.6, 'alley', 'alley', camK([[140.5, [1.95, 0.3, -0.3], [0, 0.85, 0.3], 48], [141.6, [1.95, 0.32, 0.2], [0, 0.7, 0.9], 48]]));
shot(141.6, 142.8, 'alley', 'alley', camK([[141.6, [-0.5, 0.9, -1.75], [0, 0.8, 1.2], 42], [142.8, [-0.55, 0.88, -1.9], [0, 0.8, 0.4], 42]]), { hand: 0.5 });
shot(142.8, 143.35, 'alley', 'alley', camK([[142.8, [0.55, 1.0, -0.2], [0, 0.88, -1.2], 36], [143.35, [0.52, 1.0, -0.25], [0, 0.88, -1.2], 34]]));
shot(143.35, 143.85, 'alley', 'alley', camK([[143.35, [-0.55, 0.98, 0.2], [0, 0.85, 1.2], 36], [143.85, [-0.52, 0.98, 0.25], [0, 0.85, 1.2], 34]]));
shot(143.85, 146.0, 'alley', 'alley', (t) => { const a = lerp(0.25, 1.4, sseg(t, 143.85, 146.0)); return { p: [Math.cos(a) * 1.55, 0.8, Math.sin(a) * 1.55], t: [0, 0.82, 0], fov: 40 }; }, { hand: 0.4 });
shot(146.0, 149.5, 'alley', 'alley', camK([[146, [1.9, 0.55, 1.6], [0, 1.3, 0], 50], [149.5, [1.85, 0.6, 1.4], [0, 1.2, 0], 48]]));
shot(149.5, 151.7, 'alley', 'alley', camK([[149.5, [-2.05, 1.05, 0.2], [0, 1.2, 0.0], 54], [151.7, [-1.95, 1.05, 0.15], [0, 1.2, 0.0], 52]]));
shot(151.7, 153.7, 'alley', 'alley', camK([[151.7, [0.55, 0.95, 0.45], [0, 0.88, 1.4], 34], [153.7, [0.52, 0.95, 0.5], [0, 0.88, 1.4], 32]]));
shot(153.7, 156.0, 'alley', 'alley', camK([[153.7, [-0.55, 0.97, -0.45], [0, 0.9, -1.4], 34], [156, [-0.52, 0.97, -0.5], [0, 0.9, -1.4], 32]]));
shot(156.0, 158.7, 'alley', 'alley', camK([[156, [1.95, 1.0, 0], [0, 1.0, 0], 50], [158.7, [1.9, 0.95, 0], [0, 0.95, 0], 46]]));
shot(158.7, 160.4, 'alley', 'alley', camK([[158.7, [0.9, 0.78, 0.18], [0, 0.66, 0], 38], [160.4, [0.85, 0.8, 0.12], [0, 0.68, 0], 36]]));
shot(160.4, 163.4, 'alley', 'alley', camK([[160.4, [1.9, 1.1, 0], [0, 1.2, 0], 48], [163.4, [1.95, 1.05, 0], [0, 1.1, 0], 50]]));
shot(163.4, 166.0, 'alley', 'alley', camK([[163.4, [1.55, 0.85, -0.45], [0, 0.8, 0], 42], [166, [1.5, 0.88, -0.4], [0, 0.85, 0], 40]]));
shot(166.0, 169.0, 'alley', 'alley', camK([[166, [1.95, 0.9, 0.05], [0, 0.95, 0], 42], [169, [1.85, 0.88, 0.0], [0, 0.88, 0], 38]]));
shot(169.0, 179.0, 'alley', 'alley', camK([[169, [1.85, 0.88, 0.0], [0, 0.88, 0], 38], [172, [1.2, 4.5, 2.5], [0, 1.0, 0], 46, 'in'], [179, [0.2, 15, 10], [0, 4, -6], 50, 'out']]), { fadeOut: 0.9 });

say(130.9, 'codex', 'Fastest commit wins.', 1.3);
say(132.5, 'claude', 'Correct commit wins.', 1.4);
say(147.0, 'gpt', 'STOP! You two are on the SAME ticket!', 2.2);
say(150.3, 'codex', '…Wait. Yours too?', 1.4);
say(151.9, 'claude', 'My tests were written for your code.', 1.8);
say(153.9, 'codex', '…And my code was written to pass your tests.', 2.1);
say(156.2, 'gpt', 'So… pair program?', 1.4);
say(160.5, 'gpt', 'Great! Merging…', 1.3);
say(164.4, 'gpt', 'Perfect! Now… one tiny change.', 1.9);
say(166.8, 'codex', '…Bring it.', 1.0);
say(167.9, 'claude', '…Carefully.', 1.0);
sfx(121.0, 'rain', { dur: 58 });
for (const lt of LIGHTNING) sfx(lt, 'thunder');
sfx(126.0, 'steps', { who: 'cc', track: ccFight, t0: 126.0, t1: 130.2, outdoor: true, wet: true });
sfx(130.6, 'whoosh', { soft: true }); sfx(131.3, 'clatter');
sfx(131.6, 'summon', { who: 'cx' }); sfx(133.0, 'summon', { who: 'cc' });
for (const [ti, , s] of IMPACTS) sfx(ti, 'impact', { s });
for (const ti of WHOOSH) sfx(ti, 'whoosh');
for (const [ti] of SPLASH) sfx(ti, 'splash');
sfx(140.9, 'skid', { dur: 0.5 });
sfx(143.9, 'lock', { dur: 2.4 });
sfx(146.0, 'gptDescend'); sfx(146.6, 'shock');
sfx(149.3, 'holo'); sfx(160.5, 'holo'); sfx(162.6, 'buildPass'); sfx(163.9, 'bump'); sfx(165.9, 'notify');
sfx(157.6, 'steps', { who: 'cx', mv: cxStepIn, wet: true }); sfx(157.6, 'steps', { who: 'cc', mv: ccStepIn, wet: true });
