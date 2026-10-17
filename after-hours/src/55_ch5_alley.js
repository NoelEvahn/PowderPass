// ───────────────────────── Chapter 5 — The Alley (fight) + Chapter 6 action ─────────────────────────
chapter(121, 'After Hours');

// ── code-powered weapons ──
function glyphSprite(ch, color) {
  const tex = canvasTex(64, 64, (c) => { c.font = `700 52px ${FONT_MONO}`; c.textAlign = 'center'; c.textBaseline = 'middle'; c.shadowColor = color; c.shadowBlur = 12; c.fillStyle = color; c.fillText(ch, 32, 34); });
  const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false, color: new THREE.Color(1.6, 1.6, 1.6) }));
  s.scale.setScalar(0.08); return s;
}
function buildBlade() {
  const g = new THREE.Group();
  const inner = group(g); g.userData.inner = inner;
  mesh(cylg(0.017, 0.017, 0.12, 10), toon(PAL.cxGraphite), 0, -0.02, 0, inner, { ol: 0.004 });
  mesh(rbox(0.11, 0.022, 0.035, 0.008), toon(PAL.cxWhite), 0, 0.045, 0, inner, { ol: 0.004 });
  const blade = mesh(rbox(0.045, 0.56, 0.01, 0.004), glow(PAL.cxMint, 2.4, { transparent: true, opacity: 0.95 }), 0, 0.34, 0, inner, { cast: false });
  mesh(boxg(0.012, 0.54, 0.012), glow(0xffffff, 2.2), 0, 0.34, 0, inner, { cast: false });
  g.userData.blade = blade;
  g.userData.glyphs = ['{', '}', ';', '<', '>', '/'].map((ch) => { const s = glyphSprite(ch, '#5ff2cf'); inner.add(s); return s; });
  g.userData.tip = new THREE.Vector3(0, 0.62, 0); g.userData.base = new THREE.Vector3(0, 0.08, 0);
  return g;
}
function buildStaff() {
  const g = new THREE.Group();
  const inner = group(g); g.userData.inner = inner;
  mesh(cylg(0.016, 0.016, 0.9, 10), toon(PAL.ccCopper), 0, 0, 0, inner, { ol: 0.004 });
  for (const y of [-0.12, 0.12]) mesh(cylg(0.021, 0.021, 0.05, 10), toon(PAL.ccCream), 0, y, 0, inner);
  for (const y of [-0.46, 0.46]) { mesh(sphg(0.035, 12, 10), glow(PAL.ccAmber, 2.4), 0, y, 0, inner, { cast: false }); mesh(torg(0.04, 0.007, 6, 16), toon(PAL.ccCopper), 0, y * 0.93, 0, inner, { rx: Math.PI / 2, cast: false }); }
  g.userData.glyphs = ['✓', '#', '?', '…'].map((ch) => { const s = glyphSprite(ch, '#ffb877'); inner.add(s); return s; });
  g.userData.tip = new THREE.Vector3(0, 0.46, 0); g.userData.base = new THREE.Vector3(0, -0.46, 0);
  return g;
}
function buildShield() {
  const g = new THREE.Group();
  const tex = canvasTex(256, 256, (c, w, h) => {
    c.translate(w / 2, h / 2); c.beginPath(); for (let i = 0; i < 6; i++) { const a = i / 6 * TAU + Math.PI / 6; c.lineTo(Math.cos(a) * 120, Math.sin(a) * 120); } c.closePath();
    c.fillStyle = 'rgba(255,150,70,0.22)'; c.fill(); c.strokeStyle = '#ffc07a'; c.lineWidth = 8; c.stroke(); c.clip();
    c.fillStyle = '#ffd7a8'; c.font = `600 15px ${FONT_MONO}`;
    ['def review(pr):', '  for line in pr:', '    assert ok(line)', '  return "LGTM"', '# careful.', '# tested.', 'raise NotSafe()'].forEach((l, i) => c.fillText(l, -95, -70 + i * 22));
  });
  const m = new THREE.MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false, side: THREE.DoubleSide, blending: THREE.AdditiveBlending, toneMapped: false, color: new THREE.Color(1.5, 1.5, 1.5) });
  const plate = mesh(new THREE.CircleGeometry(0.17, 6), m, 0, 0, 0, g, { cast: false });
  plate.rotation.z = Math.PI / 6;
  mesh(torg(0.17, 0.008, 6, 6), glow(PAL.ccAmber, 2.2), 0, 0, 0, g, { cast: false }).rotation.z = Math.PI / 6;
  return g;
}

// ── fight choreography (absolute film time) ──
const F0 = 134.5;
const CXS = { ground: 1, llF: 0.4, lkB: 0.7, rlF: -0.3, rkB: 0.45, llO: 0.12, rlO: 0.1, spX: 0.12, spY: 0.25, rx: -0.1, ry: 0.3, rz: 0.24, lx: 0.12, ly: 0.36, lz: 0.2, run: 0, pitch: 0, roll: 0, spZ: 0, x: 0 };
const CCS = { ground: 1, llF: 0.35, lkB: 0.6, rlF: -0.25, rkB: 0.4, llO: 0.12, rlO: 0.1, spX: 0.12, spY: -0.2, rx: -0.14, ry: 0.22, rz: 0.16, lx: 0.12, ly: 0.32, lz: 0.2, run: 0, pitch: 0, roll: 0, spZ: 0, x: 0 };
const IDLE_CX = Object.assign({}, REST, { rx: -0.12, ry: 0.12, rz: 0.06, lx: 0.12, ly: 0.12, lz: 0.06, run: 0 });
const IDLE_CC = Object.assign({}, REST, { rx: -0.12, ry: 0.12, rz: 0.06, lx: 0.12, ly: 0.12, lz: 0.06, run: 0 });
const cxFight = K([
  [121, Object.assign({}, IDLE_CX, { z: -1.6 })],
  [131.3, {}],
  [131.9, Object.assign({}, CXS, { z: -1.6 })],
  [134.5, {}],
  [134.6, { run: 1 }], [135.15, { z: -0.5, run: 1 }, 'lin'],
  [135.25, { run: 0, rx: -0.16, ry: 0.66, rz: -0.02, spY: 0.45 }, 'out'],
  [135.5, { rx: 0.06, ry: 0.16, rz: 0.33, spY: -0.3, spX: 0.28 }, 'snap'],
  [135.72, { rx: -0.1, ry: 0.3, rz: 0.22, spY: 0.2, spX: 0.12 }],
  [135.85, { lkB: 1.05, rkB: 0.85, spX: 0.32 }],
  [136.05, { ground: 0, y: 0.62, z: -0.82, llF: 0.9, lkB: 1.35, rlF: 0.7, rkB: 1.4, spX: 0.05, pitch: -0.15 }, 'out'],
  [136.25, { ground: 1, z: -1.0, llF: 0.45, lkB: 1.05, rlF: -0.2, rkB: 0.85, pitch: 0, spX: 0.3 }, 'in'],
  [136.5, Object.assign({}, CXS, { z: -1.0 })],
  [136.8, { z: -0.55, llF: 0.85, lkB: 0.95, rlF: -0.5, rkB: 0.2, spX: 0.32, rx: -0.02, ry: 0.32, rz: 0.37, spY: -0.25 }, 'snap'],
  [137.1, Object.assign({}, CXS, { z: -0.6 })],
  [137.35, { rx: -0.13, ry: 0.63, rz: 0.13, lx: 0.12, ly: 0.63, lz: 0.13, spX: 0.0, spY: 0 }],
  [137.55, { lkB: 1.15, rkB: 0.95, ry: 0.55, ly: 0.55 }, 'snap'],
  [137.8, Object.assign({}, CXS, { z: -0.6 })],
  [138.1, { lx: 0.05, ly: 0.33, lz: 0.37, spY: -0.4, spX: 0.22 }, 'snap'],
  [138.3, Object.assign({}, CXS, { z: -0.6 })],
  [138.5, { lkB: 1.45, rkB: 1.25, spX: 0.6, ry: 0.22, rz: 0.15, ly: 0.25 }, 'snap'],
  [138.75, { lkB: 0.7, rkB: 0.5, spX: 0.1 }],
  [139.0, { lx: 0.04, ly: 0.56, lz: 0.31, spY: -0.35, spX: -0.05, lkB: 0.4, rkB: 0.3 }, 'snap'],
  [139.3, Object.assign({}, CXS, { z: -0.6 })],
  [139.45, { ground: 0, y: 0.52, z: -1.05 }, 'out'], [139.62, { ground: 1, z: -1.35 }, 'in'],
  [139.78, { ground: 0, y: 0.48, z: -1.6 }, 'out'], [139.95, Object.assign({}, CXS, { ground: 1, z: -1.75 }), 'in'],
  [140.0, { run: 1 }], [140.4, { z: -0.6, run: 1 }, 'lin'],
  [140.48, { run: 0, lkB: 1.15, rkB: 1.05, spX: 0.32 }],
  [140.62, { ground: 0, y: 0.86, z: -0.05, pitch: -0.3, rlF: 1.45, rkB: 0.08, llF: 0.25, lkB: 1.65, spX: -0.1, spY: 0.3, rx: -0.3, ry: 0.36, rz: -0.06, lx: 0.3, ly: 0.36, lz: 0.0 }, 'out'],
  [140.85, { y: 0.8, z: 0.4 }, 'lin'],
  [141.1, { ground: 1, y: 0.3, z: 0.46, pitch: 0, rlF: 0.4, rkB: 1.0, llF: 0.35, lkB: 1.05, spX: 0.32, spY: 0 }, 'in'],
  [141.3, { ground: 0, y: 0.48, z: 0.2 }, 'out'], [141.5, Object.assign({}, CXS, { ground: 1, z: -0.1 }), 'in'],
  [142.25, {}],
  [142.38, { rx: -0.1, ry: 0.46, rz: 0.26, spY: 0.1 }, 'snap'],
  [142.7, { z: -0.7, lkB: 1.0, rkB: 0.75 }],
  [143.0, Object.assign({}, CXS, { z: -1.2 })],
  [143.42, { rx: -0.22, ry: 0.16, rz: -0.12, spY: 0.55, lkB: 0.95 }],
  [143.5, { run: 1 }], [143.85, { z: -0.44, run: 1 }, 'lin'],
  [143.9, { run: 0, rx: -0.03, ry: 0.42, rz: 0.32, lx: 0.04, ly: 0.4, lz: 0.3, spY: 0.0, spX: 0.25 }, 'snap'],
  [146.4, { z: -0.42 }],
  [147.0, { z: -1.4, lkB: 0.9, rkB: 0.7, spX: 0.3 }, 'out'],
  [147.8, Object.assign({}, IDLE_CX, { z: -1.4 })],
], REST);
const ccFight = K([
  [121, Object.assign({}, IDLE_CC, { z: 8 })],
  [126.0, {}],
  [130.2, { z: 1.6 }, 'walk'],
  [132.8, {}],
  [133.4, Object.assign({}, CCS, { z: 1.6 })],
  [134.5, {}],
  [134.6, { run: 1 }], [135.15, { z: 0.5, run: 1 }, 'lin'],
  [135.25, { run: 0, lx: 0.04, ly: 0.48, lz: 0.27 }, 'snap'],
  [135.5, { z: 0.6, spX: -0.05 }, 'snap'],
  [135.62, Object.assign({}, CCS, { z: 0.55 })],
  [135.78, { rx: -0.32, ry: 0.15, rz: 0.0, spY: -0.65 }],
  [136.0, { rx: 0.14, ry: 0.0, rz: 0.32, spY: 0.55, spX: 0.38, lkB: 1.0, rkB: 0.8 }, 'snap'],
  [136.3, Object.assign({}, CCS, { z: 0.5 })],
  [136.75, { x: -0.26, roll: -0.3, spZ: -0.35, z: 0.55 }, 'snap'],
  [137.05, { x: 0, roll: 0, spZ: 0 }],
  [137.35, { rx: -0.1, ry: 0.7, rz: -0.06, spX: -0.12 }],
  [137.55, { rx: -0.02, ry: 0.42, rz: 0.3, spX: 0.32 }, 'snap'],
  [137.8, Object.assign({}, CCS, { z: 0.55 })],
  [138.05, { lx: 0.06, ly: 0.34, lz: 0.26 }, 'snap'],
  [138.3, {}],
  [138.5, { lx: 0.05, ly: 0.35, lz: 0.38, spX: 0.28 }, 'snap'],
  [138.75, Object.assign({}, CCS, { z: 0.55 })],
  [138.95, { lx: 0.05, ly: 0.43, lz: 0.28 }, 'snap'],
  [139.0, { z: 0.58 }],
  [139.3, { z: 0.92, spX: -0.08 }],
  [139.6, Object.assign({}, CCS, { z: 0.92 })],
  [140.5, { lx: 0.04, ly: 0.42, lz: 0.26, rx: -0.04, ry: 0.38, rz: 0.24, lkB: 0.95, rkB: 0.75 }],
  [140.85, { z: 0.92 }],
  [141.35, { z: 1.85, spX: 0.32 }, 'out'],
  [141.6, Object.assign({}, CCS, { z: 1.85 })],
  [141.7, { run: 1 }], [142.25, { z: 0.6, run: 1 }, 'lin'],
  [142.3, { run: 0, rx: -0.1, ry: 0.62, rz: 0.05 }],
  [142.4, { rx: 0.05, ry: 0.32, rz: 0.34, spX: 0.32, spY: 0.3 }, 'snap'],
  [142.7, Object.assign({}, CCS, { z: 0.6 })],
  [143.0, { z: 1.2 }],
  [143.42, { rx: -0.25, ry: 0.2, rz: -0.05, spY: -0.55 }],
  [143.5, { run: 1 }], [143.85, { z: 0.44, run: 1 }, 'lin'],
  [143.9, { run: 0, rx: -0.03, ry: 0.42, rz: 0.32, lx: 0.05, ly: 0.4, lz: 0.3, spY: 0, spX: 0.25 }, 'snap'],
  [146.4, { z: 0.42 }],
  [147.0, { z: 1.4, lkB: 0.9, rkB: 0.7, spX: 0.3 }, 'out'],
  [147.8, Object.assign({}, IDLE_CC, { z: 1.4 })],
], REST);
// impacts: time, env-local point, strength
const IMPACTS = [
  [135.5, [0, 0.86, 0.2], 1], [137.55, [0, 1.06, -0.22], 1.3], [138.1, [0, 0.72, 0.2], 0.6], [139.0, [0, 0.86, 0.22], 0.9],
  [140.85, [0, 0.8, 0.66], 1.5], [142.4, [0, 0.82, 0.22], 1.1], [143.9, [0, 0.86, 0.0], 2.2],
];
const WHOOSH = [135.42, 136.0, 136.8, 137.5, 138.48, 140.75, 142.36];
const SPLASH = [[136.25, [0, 0, -1.0]], [139.62, [0, 0, -1.35]], [139.95, [0, 0, -1.75]], [141.1, [0, 0, 0.46]], [141.5, [0, 0, -0.1]], [140.95, [0.05, 0, 1.15]], [141.15, [-0.05, 0, 1.55]], [147.0, [0, 0, -1.4]], [147.0, [0, 0, 1.4]]];
// travel distance tables for run cycles
function travelTable(track, t0, t1) {
  const N = Math.ceil((t1 - t0) * 240), d = new Float32Array(N + 1); let px = track(t0).x, pz = track(t0).z;
  for (let i = 1; i <= N; i++) { const q = track(t0 + i / 240); d[i] = d[i - 1] + Math.hypot(q.x - px, q.z - pz); px = q.x; pz = q.z; }
  return (t) => d[clamp(Math.round((t - t0) * 240), 0, N)];
}
let _cxTravel, _ccTravel;
// character-frame offset → env coords
function cf(p, off) { const c = Math.cos(p.yaw), s = Math.sin(p.yaw); return [p.x + c * off[0] + s * off[2], p.y0 + off[1], p.z - s * off[0] + c * off[2]]; }

function fightPose(R, track, travel, t, yaw) {
  const k = track(t);
  const p = Object.assign({}, REST, k);
  p.yaw = yaw;
  if (k.run > 0.01) walkLayer(p, (travel(t) / strideLen(R, { leg: 0.6 })) * Math.PI, k.run, { leg: 0.6, arm: 0.0, knee: 1.2, lean: 0.25 });
  return p;
}
SEQ.alley = (t, env, sh, info, dt) => {
  const cx = A.cx, cc = A.cc;
  show(cx, env); show(cc, env);
  if (!_cxTravel) { _cxTravel = travelTable(cxFight, 121, 150); _ccTravel = travelTable(ccFight, 121, 150); }
  // ── Claude's entrance walk uses the normal walk cycle ──
  let pc;
  const cxp = fightPose(cx, cxFight, _cxTravel, t, 0);
  if (t < 130.4) {
    const k = ccFight(t);
    pc = Object.assign({}, REST, k); pc.yaw = Math.PI;
    const d = _ccTravel(t), sp = (_ccTravel(t + 0.05) - d) / 0.05;
    walkLayer(pc, (d / strideLen(cc)) * Math.PI, clamp(sp / 0.9), { leg: 0.42, arm: 0.3 });
  } else pc = fightPose(cc, ccFight, _ccTravel, t, Math.PI);
  // finale overrides (after the interruption)
  finalePoses(t, cxp, pc);
  applyPose(cx, cxp); applyPose(cc, pc);
  const hipC = { x: cx.root.position.x, y0: cx.root.position.y, z: cx.root.position.z, yaw: cxp.yaw };
  const hipL = { x: cc.root.position.x, y0: cc.root.position.y, z: cc.root.position.z, yaw: pc.yaw };
  const kc = cxFight(t), kl = ccFight(t);
  const armsOn = (tt) => sseg(tt, 131.0, 131.9) * (1 - sseg(tt, 147.2, 147.9));
  const ccArms = sseg(t, 132.6, 133.4) * (1 - sseg(t, 147.2, 147.9));
  if (!finaleArms(t, cx, cc, env)) {
    armIK(cx, 'r', cf(hipC, [kc.rx, kc.ry, kc.rz]), armsOn(t), [-0.7, -0.6, -0.4]);
    armIK(cx, 'l', cf(hipC, [kc.lx, kc.ly, kc.lz]), armsOn(t), [0.7, -0.6, -0.4]);
    if (t >= 130.2) {
      armIK(cc, 'r', cf(hipL, [kl.rx, kl.ry, kl.rz]), ccArms, [-0.7, -0.6, -0.4]);
      armIK(cc, 'l', cf(hipL, [kl.lx, kl.ly, kl.lz]), ccArms, [0.7, -0.6, -0.4]);
    }
  }
  // umbrella: held overhead while walking in, tossed at 130.6
  const U = env.umbrella;
  if (t < 130.6) {
    const up = cf(hipL, [-0.05, 0.62 + (t > 130.25 ? sseg(t, 130.25, 130.55) * -0.1 : 0), 0.1]);
    const side = cf(hipL, [-0.38, 0.45, 0.05]);
    const u = sseg(t, 130.25, 130.55);
    armIK(cc, 'r', up.map((v, i) => lerp(v, side[i], u)), 1 - sseg(t, 130.55, 130.9) * 0, [-0.6, -0.5, -0.5]);
    holdUpright(U, cc, 'r', env, [0, 0, u * 0.9], [0, 0, 0], [0, -0.05, 0]);
    env.umbCanopy.visible = true; env.umbFolded.visible = false;
  } else {
    const u = clamp((t - 130.6) / 0.7);
    const a = [-0.35, 1.0, 1.65], b = [1.85, 0.05, 2.6];
    const pos = [lerp(a[0], b[0], u), lerp(a[1], b[1], u) + Math.sin(u * Math.PI) * 0.7, lerp(a[2], b[2], u)];
    placeProp(U, env, pos, [u * 4.0, 0, u < 1 ? u * 3 : 1.4]);
    if (u >= 1) U.rotation.set(0, 0.4, 1.45);
  }
  // weapons
  weaponUpdate(t, env, cx, cc, info);
  // face moods
  const fx = cx.face, fc = cc.face;
  fx.boot = fc.boot = 1; fx.open = fc.open = 1; fx.extra = fc.extra = null; fx.lookX = fc.lookX = 0; fx.lookY = fc.lookY = 0;
  fx.mood = t < 126 ? 'determined' : t < 130.4 ? 'smug' : t < 134.5 ? 'determined' : t < 146.3 ? 'angry' : 'surprised';
  fc.mood = t < 130.4 ? 'neutral' : t < 134.5 ? 'determined' : t < 146.3 ? 'angry' : 'surprised';
  if (t > 138.45 && t < 138.65) fx.mood = 'surprised';
  if (t > 140.85 && t < 141.3) fc.mood = 'surprised';
  finaleFaces(t, cx, cc);
  if (t < 146.3) { headLook(cx, headPos(cc, env), 0.7); headLook(cc, headPos(cx, env), 0.7); }
  // rain, splashes, sparks
  const slow = 1 - 0.75 * win(t, 143.85, 143.95, 145.6, 146.2);
  fxRain(new THREE.Vector3(camera.position.x, 0, camera.position.z - 0.0).lerp(W(env, [0, 0, 0]), 0.5), t * slow + (1 - slow) * 143.9, { h: 7.5, sx: 4.6, sz: 12, speed: 9, floor: env.group.position.y });
  FX.rain.material.opacity = 0.26 * (1 - sseg(camera.position.y - env.group.position.y, 3, 9) * 0.6);
  for (const [ti, p, s] of IMPACTS) {
    fxSparks(env, p, t, ti, { n: Math.round(40 * s), speed: 2.5 * s, color: ti === 143.9 ? 0xffffff : (ti % 2 > 1 ? 0xb8fff0 : 0xffd29a), color2: ti === 143.9 ? 0x7dffd9 : 0xffb36e, flash: 0.2 * s, life: 0.55 + 0.2 * s });
    const a = t - ti; if (a > 0 && a < 0.25) { info.shake = Math.max(info.shake || 0, s * 1.8 * (1 - a / 0.25)); info.ab = Math.max(info.ab || 0, s * 1.2 * (1 - a / 0.25)); info.flash = Math.max(info.flash || 0, s * 0.12 * (1 - a / 0.18)); }
  }
  // continuous blade-lock sparks
  if (t > 143.9 && t < 146.4) { for (let k = 0; k < 6; k++) fxSparks(env, [0, 0.86, 0], t, 143.95 + Math.floor((t - 143.9) * 10 - k) / 10, { n: 10, speed: 1.6, color: 0xffffff, color2: 0x7dffd9, flash: 0.15, life: 0.5 }); info.shake = Math.max(info.shake || 0, 0.35); }
  for (const [ti, p] of SPLASH) fxSplash(env, p, t, ti, { n: 30 });
  if (t > 140.9 && t < 141.4) for (let k = 0; k < 5; k++) fxSplash(env, [0, 0, lerp(0.95, 1.85, (t - 140.9) / 0.5) - k * 0.1], t, 140.9 + k * 0.1, { n: 12, speed: 1.0 });
  // steam vents
  fxSteam(env, [-2.0, 0.05, -6.0], t, 121, 180, { rate: 6, size: 0.25, alpha: 0.12, rise: 0.35, life: 3.0 });
  fxSteam(env, [2.0, 0.05, 5.0], t, 121, 180, { rate: 5, size: 0.3, alpha: 0.1, rise: 0.3, life: 3.2, seed: 4 });
  finaleFX(t, env, info);
  info.focus = new THREE.Vector3(0, 0.5, 0);
  info.bloom = Math.max(info.bloom || 0, 0.7); info.bloomTh = 1.3;
};

let _blade, _staff, _shield;
function weaponUpdate(t, env, cx, cc, info) {
  if (!_blade) { _blade = buildBlade(); _staff = buildStaff(); _shield = buildShield(); }
  const bs = EASE.back(sseg(t, 131.6, 132.2)) * (1 - sseg(t, 147.3, 147.9));
  const ss = EASE.back(sseg(t, 133.0, 133.6)) * (1 - sseg(t, 147.3, 147.9));
  const charge = 1 + win(t, 143.3, 143.6, 146.0, 146.4) * 0.8;
  // blade in Codex's right hand (−x of the hand frame points forward)
  if (bs > 0.001) {
    holdProp(_blade, cx, 'r', [0, 0, 0], [0, 0, Math.PI / 2]);
    _blade.scale.set(1, bs, 1);
    _blade.userData.glyphs.forEach((g, i) => { const a = t * 3 + i * 1.05; g.position.set(Math.cos(a) * 0.07, 0.12 + ((i * 0.09 + t * 0.4) % 0.5), Math.sin(a) * 0.07); g.material.opacity = 0.8; });
    _blade.userData.blade.material.color.set(PAL.cxMint).multiplyScalar(2.4 * charge);
    _blade.updateMatrixWorld(true);
    const tip = _blade.userData.tip.clone(), base = _blade.userData.base.clone(); _blade.localToWorld(tip); _blade.localToWorld(base); env.group.worldToLocal(tip); env.group.worldToLocal(base);
    if (t > 134.5 && t < 146.5) fxTrail('blade', env, [base.x, base.y, base.z], [tip.x, tip.y, tip.z], t, PAL.cxMint, 12);
    fxGlow(env, [tip.x, tip.y, tip.z], 0.08 * charge, PAL.cxMint, 0.6);
    fxSwirl(env, sockPos(cx, 'r', env), t, win(t, 131.5, 131.7, 132.1, 132.5) + win(t, 143.3, 143.5, 143.8, 143.95), PAL.cxMint, 0.22);
  } else _blade.visible = false;
  if (ss > 0.001) {
    holdProp(_staff, cc, 'r', [0, 0, 0], [0, 0, Math.PI / 2]);
    _staff.scale.set(1, ss, 1);
    // spin during the counter-attack
    const spin = t > 141.6 && t < 142.35 ? (t - 141.6) * 22 : 0;
    _staff.userData.inner.rotation.x = spin;
    _staff.userData.glyphs.forEach((g, i) => { const a = -t * 2.5 + i * 1.6; g.position.set(Math.cos(a) * 0.08, -0.3 + i * 0.2, Math.sin(a) * 0.08); });
    _staff.updateMatrixWorld(true);
    const tip = _staff.userData.tip.clone(), base = _staff.userData.base.clone(); _staff.userData.inner.localToWorld(tip); _staff.userData.inner.localToWorld(base); env.group.worldToLocal(tip); env.group.worldToLocal(base);
    if (t > 134.5 && t < 146.5) fxTrail('staff', env, [base.x, base.y, base.z], [tip.x, tip.y, tip.z], t, PAL.ccAmber, 10);
    fxGlow(env, [tip.x, tip.y, tip.z], 0.06 * charge, PAL.ccAmber, 0.6); fxGlow(env, [base.x, base.y, base.z], 0.06 * charge, PAL.ccAmber, 0.6);
    fxSwirl(env, sockPos(cc, 'r', env), t, win(t, 132.9, 133.1, 133.5, 133.9) + win(t, 143.3, 143.5, 143.8, 143.95), PAL.ccAmber, 0.22);
    // hex shield on the left forearm
    if (_shield.parent !== cc.j.le) cc.j.le.add(_shield);
    _shield.position.set(0.05, -cc.dim.Lf * 0.55, 0.0); _shield.rotation.set(0, Math.PI / 2, 0);
    _shield.scale.setScalar(ss); _shield.visible = true;
  } else { _staff.visible = false; if (_shield) _shield.visible = false; }
}
