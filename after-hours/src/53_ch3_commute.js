// ───────────────────────── Chapter 3 — Commute ─────────────────────────
chapter(58.5, 'Commute');
const cxCityA = pathMover([[-7.5, 4.4], [-0.45, 3.55]], 58.5, 64.3);
const cxCityB = pathMover([[-0.45, 3.55], [-0.45, -3.45]], 74.5, 80.0);
const cxCityC = pathMover([[-0.45, -3.45], [-5.2, -5.4]], 80.0, 84.5);
const ccCityA = pathMover([[7.0, 4.4], [0.45, 3.55]], 58.7, 66.0);
const ccCityB = pathMover([[0.45, 3.55], [0.45, -3.45]], 74.5, 80.0);
const ccCityC = pathMover([[0.45, -3.45], [5.2, -5.4]], 80.0, 84.5);
const SIDEWALK_Y = 0.16;

function cityActor(R, env, t, movers, wait, style) {
  let p;
  const [A1, B1, C1] = movers;
  if (t < movers.tA1) p = walkPose(R, A1, t, REST, style).p;
  else if (t < movers.tB0) { p = Object.assign({}, REST, { x: wait[0], z: wait[1], yaw: lerp(A1(movers.tA1).yaw, Math.PI, sseg(t, movers.tA1, movers.tA1 + 0.5)) }); }
  else if (t < movers.tC0) p = walkPose(R, B1, t, REST, style).p;
  else p = walkPose(R, C1, t, REST, style).p;
  // step down/up the kerb smoothly
  const zEdge = Math.abs(p.z);
  p.fy = SIDEWALK_Y * sseg(zEdge, 2.9, 3.15);
  return p;
}
SEQ.city = (t, env, sh, info) => {
  const cx = A.cx, cc = A.cc;
  show(cx, env); show(cc, env);
  const cxm = [cxCityA, cxCityB, cxCityC]; cxm.tA1 = 64.3; cxm.tB0 = 74.5; cxm.tC0 = 80.0;
  const ccm = [ccCityA, ccCityB, ccCityC]; ccm.tA1 = 66.0; ccm.tB0 = 74.5; ccm.tC0 = 80.0;
  const pc = cityActor(cx, env, t, cxm, [-0.45, 3.55], { speed: 1.2, leg: 0.48, arm: 0.55 });
  const pl = cityActor(cc, env, t, ccm, [0.45, 3.55], { speed: 0.95, leg: 0.42, arm: 0.3 });
  // Codex taps its foot while waiting
  if (t > 64.8 && t < 66.3) { const tap = Math.max(0, Math.sin((t - 64.8) * 14)); pc.laX += 0; pc.lkB += tap * 0.15; pc.llF += tap * 0.12; }
  applyPose(cx, pc); applyPose(cc, pl);
  // Claude's travel tumbler (left hand), sips at 61.2 and 64.8
  const tum = ENVS.ccHome.props.tumbler;
  const mouth = mouthPos(cc, env, 0.25, -0.12);
  const hold = (() => { const v = new THREE.Vector3(0.14, 0.22, 0.17); cc.root.localToWorld(v); env.group.worldToLocal(v); return [v.x, v.y, v.z]; })();
  const toM = Math.max(win(t, 61.0, 61.4, 61.8, 62.2), win(t, 64.6, 65.0, 65.5, 65.9), win(t, 70.6, 70.9, 71.1, 71.4) * 0.0);
  armIK(cc, 'l', hold.map((v, i) => lerp(v, mouth[i], toM)), 1, [0.6, -0.5, -0.7]);
  const tilt = toM > 0.85 ? (toM - 0.85) / 0.15 * 0.8 : 0;
  holdUpright(tum, cc, 'l', env, [tilt, cc.root.rotation.y, 0], [0, 0, 0], [0, -0.07, 0]);
  // looks: towards each other during the corner banter, then a glance back while parting
  const ch = headPos(cc, env), xh = headPos(cx, env);
  const banter = win(t, 65.6, 66.1, 74.6, 75.2);
  headLook(cx, ch, banter * 0.9); headLook(cc, xh, banter * 0.8);
  const glance = win(t, 81.5, 81.9, 82.6, 83.0);
  headLook(cx, ch, glance, 1.6); headLook(cc, xh, glance, 1.6);
  if (t > 73.7 && t < 74.6) { const sig = [0, 2.0, -3.25]; headLook(cx, sig, 0.6); headLook(cc, sig, 0.6); }
  // faces
  const fx = cx.face, fc = cc.face;
  fx.boot = fc.boot = 1; fx.open = fc.open = 1; fx.extra = fc.extra = null; fx.lookY = fc.lookY = 0;
  fx.mood = t < 64.3 ? 'determined' : t < 66.2 ? 'annoyed' : t < 68.9 ? 'smug' : t < 71.2 ? 'surprised' : t < 72.7 ? 'smug' : t < 74.6 ? 'annoyed' : t < 81.5 ? 'determined' : 'smug';
  fc.mood = t < 66 ? 'happy' : t < 69.3 ? 'neutral' : t < 71.3 ? 'happy' : t < 72.7 ? 'neutral' : t < 74.4 ? 'happy' : t < 81.5 ? 'neutral' : 'happy';
  fx.lookX = banter * 0.7; fc.lookX = -banter * 0.6;
  info.focus = new THREE.Vector3((pc.x + pl.x) / 2, 0.5, (pc.z + pl.z) / 2);
};

const ccTrack = (t) => { const v = A.cc.root.position; return [v.x, v.y, v.z]; };
shot(58.5, 63.0, 'city', 'city', camK([[58.5, [-1.5, 6.5, -1.5], [-5.5, 0.8, 4.2], 44], [63, [-1.2, 1.3, 1.2], [-3.0, 0.75, 4.0], 40]]), { fadeIn: 0.6 });
shot(63.0, 66.5, 'city', 'city', (t) => { const q = [A.cc.root.position.x, 0.6, A.cc.root.position.z]; return { p: [q[0] - 1.3, 0.95, 1.9], t: [q[0] - 0.1, 0.72, q[2]], fov: 34 }; });
shot(66.5, 71.0, 'city', 'city', camK([[66.5, [0.05, 0.72, 1.55], [0, 0.72, 3.6], 36], [71, [0.05, 0.72, 1.85], [0, 0.74, 3.6], 34]]));
shot(71.0, 72.6, 'city', 'city', camK([[71, [0.35, 0.95, 2.1], [-0.45, 0.88, 3.55], 30], [72.6, [0.3, 0.95, 2.15], [-0.45, 0.88, 3.55], 28]]));
shot(72.6, 74.6, 'city', 'city', camK([[72.6, [-0.35, 0.9, 2.15], [0.45, 0.82, 3.55], 30], [74.6, [-0.3, 0.9, 2.2], [0.45, 0.82, 3.55], 28]]));
shot(74.6, 80.0, 'city', 'city', camK([[74.6, [-3.2, 2.2, -4.9], [0, 0.6, 1.5], 42], [80, [-2.4, 3.6, -5.3], [0, 1.4, -4.5], 48]]));

say(66.6, 'codex', 'Morning. Shipped three features before your tea cooled.', 2.6);
say(69.4, 'claude', 'I saw. Line 42 has an off-by-one.', 1.8);
say(71.3, 'codex', '…That’s a feature.', 1.2);
say(72.8, 'claude', 'It’s a bug with confidence.', 1.6);
sfx(58.5, 'cityAmb', { dur: 21.5 });
sfx(58.5, 'steps', { who: 'cx', mv: cxCityA, outdoor: true }); sfx(58.7, 'steps', { who: 'cc', mv: ccCityA, outdoor: true });
sfx(74.5, 'steps', { who: 'cx', mv: cxCityB, outdoor: true }); sfx(74.5, 'steps', { who: 'cc', mv: ccCityB, outdoor: true });
sfx(73.9, 'crossing', { dur: 6.4 });
