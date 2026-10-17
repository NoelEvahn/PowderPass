// ───────────────────────── Chapter 4 — Work ─────────────────────────
chapter(80, 'Requests');
const SEAT = { x: 0, y: 0.37, z: -1.03 };
const KB = { l: [-0.075, DESK_H + 0.035, -1.38], r: [0.075, DESK_H + 0.035, -1.38] };
const SIT = { x: SEAT.x, y: SEAT.y, z: SEAT.z, yaw: Math.PI, ground: 0, flat: 1, llF: 1.5, rlF: 1.5, lkB: 1.45, rkB: 1.45, llO: 0.06, rlO: 0.06, spX: 0.18 };

// ── screen scripts ──
const CX_CODE = [
  '@EventHandler',
  'public void onCreeperBoom(EntityExplodeEvent e) {',
  '    if (!(e.getEntity() instanceof Creeper)) return;',
  '    e.setCancelled(true); // no boom today',
  '    Location at = e.getLocation();',
  '    for (int i = 0; i < 8; i++) {',
  '        Block b = at.clone().add(r(),0,r()).getBlock();',
  '        if (b.getType().isAir())',
  '            b.setType(randomFlower());',
  '    }',
  '    e.getEntity().remove();',
  '}',
];
const CX_FIX = '        if (b != null && b.getType().isAir())';
const CX_RAINBOW = '            b.setType(RAINBOW[i % RAINBOW.length]);';
const CC_CODE = [
  '# redstone item-sorter calculator',
  'def slots_needed(items: int, stack: int = 64) -> int:',
  '    """Chest slots needed to store `items`."""',
  '    return items // stack',
  '',
  'def chests_needed(items, stack=64):',
  '    return -(-slots_needed(items, stack) // 27)',
  '',
  'def test_full():    assert slots_needed(64) == 1',
  'def test_partial(): assert slots_needed(65) == 2',
  'def test_pearls():  assert slots_needed(17,16) == 2',
];
const CC_FIX = '    return -(-items // stack)  # ceil division';
const CX_REQ = 'Make a Minecraft plugin: creepers drop flowers instead of exploding 🌼';
const CC_REQ = 'My Minecraft redstone sorter says 65 items fit in 1 slot?? Fix it + add tests pls';

function cxScreenState(t) {
  const st = { file: 'CreeperBloom.java', req: t > 83.3 ? CX_REQ : null, code: CX_CODE.slice(), typed: Math.max(0, (t - 84.6) * 52), chat: [], term: null };
  if (t > 99.0) { st.term = [{ text: '$ gradle test' }]; }
  if (t > 99.5 && t < 101.9) { st.term.push({ text: 'CreeperBloomTest > dropsFlowers() FAILED', color: THEMES.oa.bad }, { text: 'java.lang.NullPointerException: b is null (line 8)', color: THEMES.oa.bad }); st.hl = { line: 7, color: 'rgba(255,90,90,0.22)' }; }
  if (t > 100.6) { st.code[7] = CX_FIX.slice(0, Math.max(0, Math.floor((t - 100.6) * 50))) || st.code[7]; }
  if (t > 101.9) { st.term = [{ text: '$ gradle test' }, { text: 'CreeperBloomTest > dropsFlowers() PASSED', color: THEMES.oa.ok }]; }
  if (t > 102.3) { st.term.push({ text: 'BUILD SUCCESSFUL in 3s  ✓ 6 tests passed', color: THEMES.oa.ok }); st.hl = { line: 7, color: 'rgba(95,242,160,0.16)' }; }
  if (t > 102.3 && t < 104.0) st.banner = { text: '✓ BUILD SUCCESSFUL', bg: 'rgba(30,140,100,0.92)' };
  if (t > 104.4) st.chat.push({ who: 'gpt', text: 'Amazing!! One tiny change: can the flowers be rainbow? 🌈' });
  if (t > 108.0) { st.code[8] = CX_RAINBOW.slice(0, Math.max(10, Math.floor((t - 108.0) * 45))); }
  if (t > 112.3) st.chat.push({ who: 'Claude Code', text: 'reviewed PR #4096 — 14 comments' });
  return st;
}
function ccScreenState(t) {
  const st = { file: 'sorter.py', req: t > 88.6 ? CC_REQ : null, code: CC_CODE.slice(), typed: t < 89.0 ? 0 : 999, chat: [], term: null };
  // Claude reads first (code already open), then writes tests
  st.typed = 260 + Math.max(0, (t - 90.4) * 34);
  if (t > 93.8) st.term = [{ text: '$ pytest -q' }];
  if (t > 94.3 && t < 97.0) { st.term.push({ text: '..F.F.......  2 failed, 10 passed', color: THEMES.an.bad }, { text: 'test_partial: assert 1 == 2', color: THEMES.an.bad }); st.hl = { line: 3, color: 'rgba(255,110,90,0.22)' }; }
  if (t > 95.9) { const n = Math.floor((t - 95.9) * 30); st.code[3] = n < 4 ? st.code[3] : CC_FIX.slice(0, n); }
  if (t > 97.0) st.term = [{ text: '$ pytest -q' }];
  if (t > 97.5) { st.term.push({ text: '............  12 passed in 0.04s  ✓', color: THEMES.an.ok }); st.hl = { line: 3, color: 'rgba(160,227,154,0.18)' }; }
  if (t > 97.5 && t < 99.0) st.banner = { text: '✓ 12 / 12 PASSED', bg: 'rgba(120,90,40,0.92)' };
  if (t > 108.7) st.chat.push({ who: 'gpt', text: 'Love it! One tiny change: make it work for pigs too? 🐷' });
  return st;
}
const DM_MSGS = [
  { t: 114.4, from: 'Codex', text: 'your review is longer than my code', who: 'cx' },
  { t: 116.2, from: 'Claude Code', text: 'your code is longer than it needs to be', who: 'cc' },
  { t: 117.9, from: 'Codex', text: 'alley behind the server farm. midnight.', who: 'cx' },
  { t: 119.6, from: 'Claude Code', text: 'I’ll bring an umbrella.', who: 'cc' },
];
function drawScreens(env, who, t) {
  const tex = env.screen;
  const key = Math.floor(t * 20);
  if (tex.userData.key === key) return;
  tex.userData.key = key;
  if (t > 113.9) {
    const mine = who;
    const msgs = DM_MSGS.filter((m) => t > m.t).map((m) => ({ from: m.from, text: m.text, mine: m.who === mine })).slice(-4);
    const next = DM_MSGS.find((m) => t < m.t && t > m.t - 1.2);
    drawDM(tex, who === 'cx' ? 'oa' : 'an', msgs, next ? `${next.from} is typing…` : null, who === 'cx' ? 'DM · Claude Code' : 'DM · Codex');
  } else drawIDE(tex, who === 'cx' ? 'oa' : 'an', who === 'cx' ? cxScreenState(t) : ccScreenState(t), t);
  // side monitor: CI dashboard
  const c2 = env.screen2.userData.ctx, th = THEMES[who === 'cx' ? 'oa' : 'an'];
  c2.fillStyle = th.bg; c2.fillRect(0, 0, 512, 360);
  c2.fillStyle = th.acc; c2.font = `700 26px ${FONT_UI}`; c2.fillText(who === 'cx' ? 'QUEUE' : 'REVIEW QUEUE', 22, 44);
  const left = Math.max(0, 47 - Math.floor(Math.max(0, t - 84) * 0.9));
  c2.fillStyle = th.text; c2.font = `800 96px ${FONT_UI}`; c2.fillText(String(left), 22, 160);
  c2.font = `600 22px ${FONT_UI}`; c2.fillStyle = th.dim; c2.fillText('requests remaining', 22, 196);
  for (let i = 0; i < 5; i++) { c2.fillStyle = i < 3 ? th.ok : th.line; c2.fillRect(22 + i * 56, 236, 44, 18); }
  env.screen2.needsUpdate = true;
}

// ── typing pose helper ──
function typingArms(R, t, rate, amt) {
  if (amt <= 0) return;
  const tapL = Math.max(0, Math.sin(t * rate * 6.3)) * 0.012, tapR = Math.max(0, Math.sin(t * rate * 6.3 + 2.1)) * 0.012;
  const jx = noise1(t * rate) * 0.03;
  armIK(R, 'l', [KB.l[0] + jx, KB.l[1] + tapL, KB.l[2]], amt, [0.8, -0.3, 0.3]);
  armIK(R, 'r', [KB.r[0] + jx, KB.r[1] + tapR, KB.r[2]], amt, [-0.8, -0.3, 0.3]);
}

// ── Codex at OpenAI ──
const cxOfficeWalk = pathMover([[3.6, 1.6], [1.8, 0.2], [0.55, -1.03]], 80.0, 82.1);
const cxSitDown = K([
  [82.1, { x: 0.55, z: -1.03, yaw: -2.35, ground: 1 }],
  [82.3, { yaw: -2.6, spX: 0.2, lkB: 0.5, rkB: 0.5, llF: 0.3, rlF: 0.3 }],
  [82.55, { ground: 0, y: 0.6, x: 0.25, yaw: -Math.PI, llF: 1.0, rlF: 1.0, lkB: 1.0, rkB: 1.0, spX: 0.1, lsF: 0.6, rsF: 0.6 }, 'out'],
  [82.85, Object.assign({}, SIT, { yaw: -Math.PI, spX: 0.3 }), 'in'],
  [83.2, Object.assign({}, SIT, { yaw: -Math.PI })],
], REST);
SEQ.cxOffice = (t, env, sh, info) => {
  const R = A.cx; show(R, env);
  let p;
  if (t < 82.1) p = walkPose(R, cxOfficeWalk, t, REST, { speed: 1.1, leg: 0.45 }).p;
  else p = Object.assign({}, cxSitDown(t));
  if (t > 83.2) {
    p = Object.assign({}, REST, SIT, { yaw: -Math.PI });
    p.spX += 0.04 * Math.sin(t * 2.0);
    // reactions
    p.spX -= win(t, 99.5, 99.7, 100.3, 100.6) * 0.25; // recoil at the error
    p.spX -= win(t, 102.4, 102.6, 103.4, 103.8) * 0.2;
    p.spX -= win(t, 104.6, 104.9, 107.4, 107.8) * 0.15;
    p.spX -= win(t, 112.6, 112.8, 113.6, 114.0) * 0.3;
  }
  applyPose(R, p);
  const typing = t > 83.4 ? Math.min(win(t, 83.6, 84.2, 99.4, 99.7) + win(t, 100.4, 100.6, 101.7, 101.9) + win(t, 107.9, 108.2, 109.8, 110.1) + win(t, 114.0, 114.2, 114.9, 115.1) + win(t, 117.4, 117.6, 118.4, 118.6), 1) : 0;
  typingArms(R, t, 3.2, typing);
  // fist pump on success
  const pump = win(t, 102.45, 102.6, 103.3, 103.7);
  if (pump > 0) armIK(R, 'r', [0.16, 1.02 + Math.sin(t * 18) * 0.015, -1.15], pump, [-0.9, -0.6, 0.2]);
  R.j.rhand.fingers.rotation.x = lerp(R.j.rhand.fingers.rotation.x, 1.6, pump);
  // look at screen / GPT
  const gp = [0.62, 1.05, -1.45];
  if (t > 104.4 && t < 108.1) headLook(R, gp, 0.85); else headLook(R, [0, 0.92, -1.78], t > 83 ? 0.6 : 0);
  const f = R.face; f.boot = 1; f.open = 1; f.extra = null; f.lookY = 0;
  f.lookX = t > 84.6 && t < 99 ? Math.sin(t * 3.1) * 0.35 : 0;
  f.mood = t < 83.3 ? 'determined' : t < 99.5 ? 'focus' : t < 100.4 ? 'error' : t < 102.3 ? 'determined' : t < 104.4 ? 'happy' : t < 105.0 ? 'surprised' : t < 107.6 ? 'annoyed' : t < 108.1 ? 'worried' : t < 112.4 ? 'focus' : t < 113.9 ? 'surprised' : t < 115.4 ? 'angry' : t < 117.4 ? 'annoyed' : 'determined';
  drawScreens(env, 'cx', t);
  // ChatGPT pops in beside the monitor
  if (t > 104.2 && t < 108.4) {
    const G = A.gpt; show(G, env);
    const s = EASE.back(sseg(t, 104.2, 104.55)) * (1 - sseg(t, 108.1, 108.4)) * 0.55;
    poseGPT(G, t, { x: gp[0], y: gp[1], z: gp[2], yaw: -0.25, scale: Math.max(0.001, s), gesture: t < 105.2 ? 1 : 0 });
    G.face.mood = t < 106.2 ? 'cheer' : 'neutral';
  }
  info.focus = new THREE.Vector3(0, 0.6, -1.3);
};
// ── Claude at Anthropic ──
SEQ.ccOffice = (t, env, sh, info) => {
  const R = A.cc; show(R, env);
  let p = Object.assign({}, REST, SIT, { yaw: -Math.PI, y: 0.345 });
  p.spX += 0.03 * Math.sin(t * 1.6) + win(t, 89.5, 90.0, 93.6, 94.0) * 0.08;
  p.spX -= win(t, 108.9, 109.2, 110.8, 111.2) * 0.12;
  p.nkZ = win(t, 110.9, 111.2, 112.2, 112.5) * 0.25; // head tilt: "define work"
  applyPose(R, p);
  const typing = Math.min(win(t, 90.2, 90.6, 93.5, 93.8) + win(t, 95.7, 95.9, 97.0, 97.2) + win(t, 116.0, 116.2, 117.0, 117.2) + win(t, 119.2, 119.4, 120.1, 120.3), 1);
  typingArms(R, t, 2.2, typing);
  // chin-stroke while thinking
  const think = win(t, 88.8, 89.2, 90.0, 90.3) + win(t, 94.4, 94.7, 95.5, 95.7);
  if (think > 0) armIK(R, 'r', mouthPos(R, env, 0.2, -0.16), think, [-0.8, -0.6, 0.2]);
  const gp = [-0.62, 1.02, -1.45];
  if (t > 108.6 && t < 112.6) headLook(R, gp, 0.85); else headLook(R, [0, 0.92, -1.78], 0.6);
  setLoupe(R, win(t, 89.4, 89.8, 98.4, 98.8));
  const f = R.face; f.boot = 1; f.open = 1; f.extra = null; f.lookY = 0;
  f.lookX = t > 90 && t < 94 ? Math.sin(t * 2.2) * 0.4 : 0;
  f.mood = t < 89.2 ? 'neutral' : t < 90.2 ? 'thinking' : t < 94.3 ? 'focus' : t < 95.3 ? 'surprised' : t < 95.8 ? 'thinking' : t < 97.5 ? 'focus' : t < 99.5 ? 'happy' : t < 108.9 ? 'neutral' : t < 110.9 ? 'worried' : t < 112.6 ? 'thinking' : t < 116 ? 'annoyed' : 'determined';
  drawScreens(env, 'cc', t);
  if (t > 108.4 && t < 112.8) {
    const G = A.gpt; show(G, env);
    const s = EASE.back(sseg(t, 108.4, 108.75)) * (1 - sseg(t, 112.5, 112.8)) * 0.55;
    poseGPT(G, t, { x: gp[0], y: gp[1], z: gp[2], yaw: 0.25, scale: Math.max(0.001, s), gesture: t < 109.4 ? 1 : 3 });
    G.face.mood = t < 110 ? 'cheer' : 'neutral';
  }
  info.focus = new THREE.Vector3(0, 0.6, -1.3);
};

// ── shots ──
const OTS_CX = (a, b) => camK([[a, [0.62, 1.38, -0.2], [0.0, 0.9, -1.78], 40], [b, [0.55, 1.34, -0.3], [0.0, 0.9, -1.78], 36]]);
const SCREEN = (a, b, side = 1, fov = 40) => camK([[a, [side * 0.03, 0.93, -0.95], [0.0, 0.92, -1.79], fov], [b, [side * 0.02, 0.93, -1.02], [0.0, 0.92, -1.79], fov - 3]]);
shot(80.0, 83.5, 'oaOffice', 'cxOffice', camK([[80, [2.6, 1.5, 2.4], [1.0, 0.6, -0.6], 44], [83.5, [1.6, 1.25, 1.0], [0.1, 0.7, -1.2], 40]]), { fadeIn: 0.5 });
shot(83.5, 86.5, 'oaOffice', 'cxOffice', OTS_CX(83.5, 86.5));
shot(86.5, 89.0, 'oaOffice', 'cxOffice', SCREEN(86.5, 89.0), { hide: ['cx'] });
shot(89.0, 92.5, 'anOffice', 'ccOffice', camK([[89, [-0.85, 1.02, -1.62], [0.0, 0.88, -1.15], 38], [92.5, [-0.8, 1.0, -1.6], [0.0, 0.88, -1.15], 34]]));
shot(92.5, 95.8, 'anOffice', 'ccOffice', camK([[92.5, [-0.65, 1.42, -0.22], [0.05, 0.9, -1.78], 40], [95.8, [-0.58, 1.38, -0.32], [0.05, 0.9, -1.78], 36]]));
shot(95.8, 99.0, 'anOffice', 'ccOffice', SCREEN(95.8, 99.0, -1), { hide: ['cc'] });
shot(99.0, 101.0, 'oaOffice', 'cxOffice', camK([[99, [0.95, 1.0, -1.5], [0.0, 0.88, -1.15], 36], [101, [0.9, 0.98, -1.48], [0.0, 0.88, -1.15], 32]]));
shot(101.0, 104.2, 'oaOffice', 'cxOffice', SCREEN(101.0, 104.2), { hide: ['cx'] });
shot(104.2, 108.4, 'oaOffice', 'cxOffice', camK([[104.2, [1.25, 1.05, -0.35], [0.3, 0.92, -1.35], 36], [108.4, [1.2, 1.02, -0.45], [0.3, 0.92, -1.35], 33]]));
shot(108.4, 112.8, 'anOffice', 'ccOffice', camK([[108.4, [-1.25, 1.05, -0.35], [-0.3, 0.92, -1.35], 36], [112.8, [-1.2, 1.02, -0.45], [-0.3, 0.92, -1.35], 33]]));
shot(112.8, 114.2, 'oaOffice', 'cxOffice', camK([[112.8, [0.95, 1.0, -1.5], [0.0, 0.88, -1.15], 34], [114.2, [0.9, 1.0, -1.48], [0.0, 0.88, -1.15], 34]]));
shot(114.2, 115.4, 'oaOffice', 'cxOffice', SCREEN(114.2, 115.4), { hide: ['cx'] });
shot(115.4, 117.3, 'anOffice', 'ccOffice', SCREEN(115.4, 117.3, -1), { hide: ['cc'] });
shot(117.3, 118.6, 'anOffice', 'ccOffice', camK([[117.3, [-0.85, 1.02, -1.62], [0.0, 0.88, -1.15], 36], [118.6, [-0.8, 1.0, -1.6], [0.0, 0.88, -1.15], 33]]));
shot(118.6, 121.0, 'oaOffice', 'cxOffice', camK([[118.6, [0.0, 1.4, 1.6], [0.0, 0.8, -1.3], 36], [121, [0.0, 1.6, 2.6], [0.0, 0.9, -1.5], 40]]), { fadeOut: 0.9 });

say(95.3, 'claude', 'Ah. There you are.', 1.3);
say(99.9, 'codex', 'Null? Not on my watch.', 1.4);
say(102.8, 'codex', 'Fixed. Under a minute.', 1.4);
say(104.6, 'gpt', 'Amazing! One tiny change — can the flowers be rainbow?', 2.7);
say(107.5, 'codex', '…Sure. Tiny.', 1.0);
say(108.9, 'gpt', 'Love it! One tiny change — make it work for pigs too?', 2.5);
say(111.5, 'claude', 'Define “work” for pigs.', 1.3);
say(113.1, 'codex', 'Fourteen review comments?!', 1.3);
say(114.5, 'codex', 'Your review is longer than my code.', 1.6);
say(116.3, 'claude', 'Your code is longer than it needs to be.', 1.6);
say(118.0, 'codex', 'Alley behind the server farm. Midnight.', 1.6);
say(119.7, 'claude', '…I’ll bring an umbrella.', 1.4);
sfx(80.0, 'steps', { who: 'cx', mv: cxOfficeWalk }); sfx(82.85, 'land', { soft: true });
sfx(80.0, 'officeAmb', { dur: 41 });
for (const [a, b, r] of [[84.2, 99.4, 1], [100.6, 101.7, 1], [108.2, 109.8, 1], [114.2, 114.9, 1], [117.6, 118.4, 1]]) sfx(a, 'typing', { dur: b - a, fast: true });
for (const [a, b] of [[90.6, 93.5], [95.9, 97.0], [116.2, 117.0], [119.4, 120.1]]) sfx(a, 'typing', { dur: b - a });
sfx(94.3, 'fail'); sfx(99.5, 'fail'); sfx(97.5, 'success'); sfx(102.3, 'success');
sfx(104.25, 'gptPop'); sfx(108.45, 'gptPop'); sfx(112.4, 'notify'); sfx(114.4, 'dm'); sfx(116.2, 'dm'); sfx(117.9, 'dm'); sfx(119.6, 'dm');
