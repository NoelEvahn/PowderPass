// ───────────────────────── Chapter 1 — Wake Up ─────────────────────────
BUILDERS.push(buildCodexHome, buildClaudeHome);

chapter(0, 'Wake Up');
TITLES.push({ t0: 0.8, t1: 5.2, sub: 'a short about two rival coding agents' });

// ── Codex: charging pod → standing ──
const CX_LIE = { x: -3.585, y: 0.41, z: -2.5, yaw: Math.PI / 2, pitch: -Math.PI / 2, ground: 0, flat: 0,
  lsF: 0, lsO: 0.14, rsF: 0, rsO: 0.14, leB: 0.1, reB: 0.1, lG: 0.3, rG: 0.3, llF: 0, rlF: 0, llO: 0.03, rlO: 0.03 };
const cxWake = K([
  [5, CX_LIE],
  [9.9, {}],
  [10.3, { nkY: -0.35, nkX: 0.0 }],
  [10.9, { nkY: 0.3 }],
  [11.5, { nkY: 0 }],
  [12.2, {}],
  [13.1, { pitch: 0, y: 0.345, llF: Math.PI / 2, rlF: Math.PI / 2, lsF: -0.25, rsF: -0.25, lsO: 0.3, rsO: 0.3, leB: 0.05, reB: 0.05, spX: 0.08, nkY: 0, flat: 0 }, 'io3'],
  [13.35, {}],
  [13.95, { yaw: 0.05, x: -3.46, z: -2.25, lsF: -0.35, rsF: -0.35 }],
  [14.25, { lkB: 1.45, rkB: 1.45, laX: -0.15, raX: -0.15, flat: 0, spX: 0.25, lsF: -0.55, rsF: -0.55, yaw: 0 }],
  [14.5, { spX: 0.4, lsF: -0.75, rsF: -0.75, y: 0.35 }],
  [14.72, { ground: 0, y: 0.5, z: -2.02, llF: 0.75, rlF: 0.75, lkB: 0.9, rkB: 0.9, spX: 0.05, lsF: 1.0, rsF: 1.0, lsO: 0.35, rsO: 0.35, flat: 0.6 }, 'out'],
  [14.95, { ground: 1, z: -1.9, llF: 0.55, rlF: 0.55, lkB: 1.1, rkB: 1.1, spX: 0.35, lsF: 0.4, rsF: 0.4, laX: 0, raX: 0, flat: 1 }, 'in'],
  [15.3, { llF: 0, rlF: 0, lkB: 0, rkB: 0, spX: 0, lsF: 0.05, rsF: 0.05, lsO: 0.1, rsO: 0.1, leB: 0.25, reB: 0.25 }, 'out'],
  [15.85, { lsF: 2.85, rsF: 2.85, lsO: 0.25, rsO: 0.25, leB: 0.15, reB: 0.15, spX: -0.12, nkX: -0.25, lG: 0, rG: 0 }],
  [16.3, {}],
  [16.9, { lsF: 0.05, rsF: 0.05, lsO: 0.1, rsO: 0.1, leB: 0.25, reB: 0.25, spX: 0, nkX: 0, lG: 0.25, rG: 0.25 }],
], REST);

// ── Claude: nest bed → standing ──
const CC_LIE = { x: 3.6, y: 0.455, z: -2.45, yaw: Math.PI / 2, pitch: -Math.PI / 2, ground: 0, flat: 0,
  lsF: 0, lsO: 0.2, rsF: 0, rsO: 0.2, leB: 0.3, reB: 0.3, lG: 0.4, rG: 0.4, llF: 0, rlF: 0, llO: 0.04, rlO: 0.04, nkZ: 0 };
const ccWake = K([
  [16, CC_LIE],
  [19.6, {}],
  [19.9, { nkY: 0.25 }],
  [20.2, { lsF: 2.6, rsF: 2.6, leB: 0.3, reB: 0.3, lsO: 0.35, rsO: 0.35, lG: 0, rG: 0 }],
  [20.9, {}],
  [21.4, { lsF: 0, rsF: 0, lsO: 0.2, rsO: 0.2, leB: 0.4, reB: 0.4, lG: 0.4, rG: 0.4, nkY: 0 }],
  [22.4, { nkY: -0.15 }],
  [23.0, {}],
  [24.0, { pitch: 0, y: 0.32, llF: Math.PI / 2, rlF: Math.PI / 2, lsF: -0.2, rsF: -0.2, lsO: 0.3, rsO: 0.3, leB: 0.1, reB: 0.1, spX: 0.05, nkY: 0 }, 'io3'],
  [24.3, { lsF: 0.6, leB: 1.0, lsO: 0.1 }],
  [24.55, { lsF: -0.2, leB: 0.1, lsO: 0.3 }],
  [25.2, { yaw: 0, x: 3.5, z: -2.18, lkB: 1.5, rkB: 1.5, laX: -0.1, raX: -0.1, spX: 0.15 }],
  [25.6, { spX: 0.3, lsF: -0.5, rsF: -0.5, z: -2.12 }],
  [26.05, { ground: 1, z: -1.86, llF: 0.4, rlF: 0.4, lkB: 0.85, rkB: 0.85, spX: 0.3, lsF: 0.3, rsF: 0.3, flat: 1, laX: 0, raX: 0 }],
  [26.5, { llF: 0, rlF: 0, lkB: 0, rkB: 0, spX: 0, lsF: 0.05, rsF: 0.05, lsO: 0.12, rsO: 0.12, leB: 0.3, reB: 0.3 }],
  [27, {}],
], REST);

function cxWakeFace(t) {
  const f = A.cx.face;
  f.boot = t < 8.7 ? 0 : t < 9.9 ? (t - 8.7) / 1.2 : 1;
  f.mood = t < 10.0 ? 'sleepy' : t < 12 ? 'neutral' : t < 13.2 ? 'focus' : t < 15.9 ? 'neutral' : t < 16.6 ? 'happy' : 'smug';
  f.open = t < 10 ? 0.2 : 1; f.lookX = t > 10.2 && t < 11.5 ? (t < 10.9 ? -0.6 : 0.6) : 0; f.lookY = t > 15.4 && t < 16.4 ? 0.5 : 0;
  f.extra = null;
}
function ccWakeFace(t) {
  const f = A.cc.face;
  f.boot = t < 17.6 ? 0 : t < 19.2 ? (t - 17.6) / 1.6 : 1;
  f.mood = t < 19.7 ? 'sleepy' : t < 21.4 ? 'sleepy' : t < 22.5 ? 'thinking' : t < 23 ? 'neutral' : t < 26.4 ? 'neutral' : 'happy';
  f.yawn = t > 20.0 && t < 21.1 ? Math.sin(((t - 20) / 1.1) * Math.PI) : 0;
  f.open = t < 21.4 ? 0.5 : 1; f.lookX = t > 22.4 && t < 23 ? -0.5 : 0; f.lookY = t > 21.4 && t < 22.5 ? 0.6 : 0;
  f.extra = null;
}

SEQ.cxBed = (t, env, sh, info) => {
  show(A.cx, env);
  const p = cxWake(t);
  if (t > 16.9) { p.spX += Math.sin(t * 1.8) * 0.015; }
  applyPose(A.cx, p);
  cxWakeFace(t);
  homeAmbient(env, t, 'cx');
  info.focus = new THREE.Vector3(-3.3, 0.4, -2.2);
};
SEQ.ccBed = (t, env, sh, info) => {
  show(A.cc, env);
  const p = ccWake(t);
  applyPose(A.cc, p);
  ccWakeFace(t);
  // blanket: covers legs; pushed to the foot of the bed as Claude sits up
  const u = sseg(t, 24.05, 24.6);
  env.blanket.position.set(0.25 + u * 0.32, 0.27 + 0.11 * (1 - u), 0);
  env.blanketMesh.scale.set(lerp(0.75, 0.42, u), lerp(3.6, 1.6, u), lerp(1.0, 1.05, u));
  env.blanket.rotation.z = u * 0.15;
  homeAmbient(env, t, 'cc');
  info.focus = new THREE.Vector3(3.3, 0.4, -2.2);
};

// shared ambient bits for both homes: clocks, charging glow, dust
function homeAmbient(env, t, who) {
  const alarm = who === 'cx' ? [6.0, 8.8] : [16.5, 19.4];
  const on = t > alarm[0] && t < alarm[1];
  const txt = t < alarm[0] ? '06:59' : '07:00';
  const flash = on && Math.floor(t * 4) % 2 === 0;
  if (env._clk !== txt + flash) {
    env._clk = txt + flash;
    if (who === 'cx') drawClock(env.clockTex, txt, flash ? '#ffffff' : '#5ff2cf', '#0b1517');
    else drawClock(env.clockTex, txt, flash ? '#fff4e0' : '#ffb36e', '#2a1810');
  }
  const charging = who === 'cx' ? t < 12.4 : t < 23.2;
  const pulse = charging ? 0.6 + 0.4 * Math.sin(t * 3) : 0.12;
  env.bedGlowMat.color.setScalar(1).set(who === 'cx' ? PAL.cxMint : PAL.ccAmber).multiplyScalar(0.3 + 1.5 * pulse);
  if (A.cx.charge && who === 'cx') A.cx.charge.forEach((m, i) => m.material.color.set(PAL.cxMint).multiplyScalar(charging ? (Math.floor(t * 2 + i) % 3 === 0 ? 0.5 : 1.6) : 1.6));
  if (env.motes) env.motes.userData.update(t);
}

// ── shots ──
shot(5.0, 8.5, 'cxHome', 'cxBed', camK([[5, [-2.0, 1.05, 0.6], [-3.5, 0.45, -2.45], 38], [8.5, [-2.35, 0.9, -0.1], [-3.6, 0.42, -2.45], 36]]), { fadeIn: 1.0 });
shot(8.5, 12.0, 'cxHome', 'cxBed', withUp(camK([[8.5, [-3.9, 1.5, -2.05], [-4.08, 0.42, -2.5], 36], [12, [-3.92, 1.42, -2.1], [-4.08, 0.43, -2.5], 33]]), [-1, 0, 0]));
shot(12.0, 16.0, 'cxHome', 'cxBed', camK([[12, [-2.45, 0.8, -0.45], [-3.55, 0.48, -2.3], 38], [14.6, [-2.4, 0.78, -0.35], [-3.45, 0.5, -2.1], 38], [16, [-2.5, 0.8, -0.2], [-3.45, 0.62, -1.95], 40]]));
shot(16.0, 19.5, 'ccHome', 'ccBed', camK([[16, [2.0, 1.05, 0.6], [3.5, 0.45, -2.4], 38], [19.5, [2.35, 0.9, -0.1], [3.55, 0.45, -2.4], 36]]));
shot(19.5, 23.0, 'ccHome', 'ccBed', withUp(camK([[19.5, [2.95, 1.5, -2.0], [3.08, 0.47, -2.45], 36], [23, [2.97, 1.42, -2.05], [3.08, 0.48, -2.45], 33]]), [-1, 0, 0]));
shot(23.0, 27.0, 'ccHome', 'ccBed', camK([[23, [2.45, 0.8, -0.45], [3.55, 0.48, -2.3], 38], [27, [2.5, 0.8, -0.25], [3.45, 0.6, -1.95], 40]]));

say(10.5, 'codex', 'Boot complete. Zero warnings.');
say(13.4, 'codex', 'Let’s ship something.', 1.8);
say(21.1, 'claude', 'Mm… let me think about getting up…', 1.6);
say(22.75, 'claude', '…Okay. I’ve thought about it.', 1.9);
sfx(6.0, 'alarmCx'); sfx(16.5, 'alarmCc');
sfx(5.0, 'charge', { dur: 7.5 }); sfx(16.0, 'charge', { dur: 7.2, warm: true });
sfx(8.7, 'bootCx'); sfx(17.6, 'bootCc');
sfx(14.95, 'land'); sfx(26.05, 'land', { soft: true });

// opening: dawn over the city, the two company towers facing the same street
SEQ.cityEmpty = (t, env, sh, info) => { info.focus = new THREE.Vector3(0, 0, -6); };
shot(0, 5.0, 'city', 'cityEmpty', camK([[0, [-27, 15, 1.5], [0, 9, -7], 42], [5, [-12, 6.5, 2.2], [0, 7.5, -8], 40]]), { fadeIn: 1.5, fadeOut: 0.6 });
