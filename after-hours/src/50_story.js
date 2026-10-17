// ───────────────────────── story framework ─────────────────────────
// Everything on screen is a pure function of film time t (so seeking is exact).
function shot(t0, t1, env, seq, cam, grade = {}) { SHOTS.push({ t0, t1, env, seq, cam, grade }); }
function say(t, who, text, d) { LINES.push({ t, who, text, d: d ?? Math.max(1.6, text.length * 0.062) }); }
function sfx(t, name, opts = {}) { SFX.push({ t, name, opts }); }
function chapter(t, name) { CHAPTERS.push({ t, name }); }

// camera helpers
function camK(keys) {
  const tr = K(keys.map(([t, p, tg, fov, e]) => [t, { px: p[0], py: p[1], pz: p[2], tx: tg[0], ty: tg[1], tz: tg[2], fov: fov ?? 40 }, e || 'io']));
  return (t) => { const v = tr(t); return { p: [v.px, v.py, v.pz], t: [v.tx, v.ty, v.tz], fov: v.fov }; };
}
function withUp(cam, up) { return (t, lt) => Object.assign({}, cam(t, lt), { up }); }
function camStatic(p, tg, fov = 40) { return () => ({ p, t: tg, fov }); }
// follow a world point produced by fn(t) with an offset
function camFollow(fnTarget, off, fov = 40, lookOff = [0, 0, 0]) {
  return (t) => { const q = fnTarget(t); return { p: [q[0] + off[0], q[1] + off[1], q[2] + off[2]], t: [q[0] + lookOff[0], q[1] + lookOff[1], q[2] + lookOff[2]], fov }; };
}

const Story = (() => {
  const S = { chapters: CHAPTERS, duration: 0 };
  let curEnv = null, lastShot = null;
  const tmpV = new THREE.Vector3(), tmpT = new THREE.Vector3();
  let sceneRef;
  S.build = (scene) => {
    sceneRef = scene;
    for (const b of BUILDERS) b();
    for (const k in ENVS) scene.add(ENVS[k].group);
    A.cx = buildRobot('codex'); A.cc = buildRobot('claude'); A.gpt = buildGPT();
    for (const k of ['cx', 'cc', 'gpt']) { A[k].root.visible = false; scene.add(A[k].root); }
    SHOTS.sort((a, b) => a.t0 - b.t0);
    LINES.sort((a, b) => a.t - b.t);
    CHAPTERS.sort((a, b) => a.t - b.t);
    SFX.sort((a, b) => a.t - b.t);
    S.duration = SHOTS[SHOTS.length - 1].t1;
    if (typeof buildFX === 'function') buildFX(scene);
  };
  S.shotAt = (t) => { for (let i = SHOTS.length - 1; i >= 0; i--) if (t >= SHOTS[i].t0) return SHOTS[i]; return SHOTS[0]; };
  S.lineAt = (t) => { for (let i = LINES.length - 1; i >= 0; i--) { const l = LINES[i]; if (t >= l.t && t < l.t + l.d) return l; if (l.t + 20 < t) break; } return null; };
  S.talking = (who, t) => {
    for (let i = LINES.length - 1; i >= 0; i--) {
      const l = LINES[i]; if (l.who !== who) continue;
      if (t >= l.t && t < l.t + l.d - 0.35) { const u = t - l.t; return clamp(0.35 + 0.65 * Math.abs(Math.sin(u * 13 + Math.sin(u * 5) * 2))) * clamp(u * 8) ; }
      if (l.t + 20 < t) break;
    }
    return 0;
  };
  S.chapterAt = (t) => { let c = null; for (const ch of CHAPTERS) if (t >= ch.t - 0.001) c = ch; return c; };
  S.titleAt = (t) => { for (const tc of TITLES) if (t >= tc.t0 && t <= tc.t1) { const o = Math.min(sseg(t, tc.t0, tc.t0 + 1.0), 1 - sseg(t, tc.t1 - 0.8, tc.t1)); return { o, sub: tc.sub }; } return { o: 0 }; };
  S.chapterCard = (t) => {
    for (let i = 0; i < CHAPTERS.length; i++) {
      const ch = CHAPTERS[i]; if (!ch.card) continue;
      const a = ch.t + 0.4, b = ch.t + 3.6;
      if (t >= a && t <= b) return { o: Math.min(sseg(t, a, a + 0.6), 1 - sseg(t, b - 0.7, b)), html: `CHAPTER ${ch.card}<b>${ch.name}</b>` };
    }
    return { o: 0 };
  };
  S.update = (t, dt) => {
    const sh = S.shotAt(t);
    const env = ENVS[sh.env];
    if (env !== curEnv) {
      for (const k in ENVS) ENVS[k].group.visible = ENVS[k] === env;
      curEnv = env;
    }
    sceneRef.background = env.bg;
    sceneRef.fog = env.fog || null;
    for (const k of ['cx', 'cc', 'gpt']) A[k].root.visible = false;
    if (typeof hideFX === 'function') hideFX();
    const info = { fade: 0, flash: 0, ab: 0, bloom: 0.55, tint: null, focus: null, shake: 0 };
    for (const up of env.updaters) up(t, dt, info);
    SEQ[sh.seq](t, env, sh, info, dt);
    // camera
    const c = sh.cam(t, t - sh.t0);
    tmpV.set(c.p[0], c.p[1], c.p[2]).add(env.group.position);
    tmpT.set(c.t[0], c.t[1], c.t[2]).add(env.group.position);
    const sk = (info.shake || 0) + (sh.grade.hand || 0);
    if (sk > 0) {
      tmpV.x += noise1(t * 9.1) * sk * 0.05; tmpV.y += noise1(t * 8.3 + 5) * sk * 0.05;
      tmpT.x += noise1(t * 6.7 + 9) * sk * 0.04; tmpT.y += noise1(t * 7.9 + 2) * sk * 0.04;
    }
    camera.position.copy(tmpV);
    if (c.up) camera.up.set(c.up[0], c.up[1], c.up[2]).normalize(); else camera.up.set(0, 1, 0);
    camera.lookAt(tmpT);
    if (c.roll) camera.rotateZ(c.roll);
    if (camera.fov !== c.fov) { camera.fov = c.fov; camera.updateProjectionMatrix(); }
    // shadows follow the action
    placeSun(env, info.focus || tmpT.clone().sub(env.group.position));
    // faces & secondary motion
    for (const k of ['cx', 'cc']) {
      const R = A[k];
      if (!R.root.visible) continue;
      R.face.talk = S.talking(k === 'cx' ? 'codex' : 'claude', t);
      drawFace(R, t);
      updateSecondary(R, t, dt);
    }
    if (A.gpt.root.visible) { A.gpt.face.talk = S.talking('gpt', t); drawGPTFace(A.gpt, t); }
    if (typeof updateFX === 'function') updateFX(t, dt, env, info);
    // dip-to-black around chapter boundaries & shot-specific grade
    const g = sh.grade;
    if (g.fadeIn) info.fade = Math.max(info.fade, 1 - sseg(t, sh.t0, sh.t0 + g.fadeIn));
    if (g.fadeOut) info.fade = Math.max(info.fade, sseg(t, sh.t1 - g.fadeOut, sh.t1));
    if (g.tint && !info.tint) info.tint = g.tint;
    if (g.bloom !== undefined) info.bloom = g.bloom;
    if (g.sat !== undefined) info.sat = g.sat;
    if (g.vig !== undefined) info.vig = g.vig;
    lastShot = sh;
    return info;
  };
  return S;
})();

// put an actor into an env (env-local coordinates)
function show(R, env) { if (R.root.parent !== env.group) env.group.add(R.root); R.root.visible = true; }
// attach a prop to a hand socket with a local transform, or place it in env coordinates
function holdProp(prop, R, side, pos = [0, 0, 0], rot = [0, 0, 0]) {
  const sock = R.j[side === 'l' ? 'lhand' : 'rhand'].socket;
  if (prop.parent !== sock) sock.add(prop);
  prop.position.set(...pos); prop.rotation.set(...rot); prop.visible = true;
}
function placeProp(prop, env, pos, rot = [0, 0, 0]) {
  if (prop.parent !== env.group) env.group.add(prop);
  prop.position.set(...pos); prop.rotation.set(...rot); prop.visible = true;
}
// world position of a joint in env-local coords
function jointPos(R, name, env, off) {
  const o = R.j[name].isObject3D ? R.j[name] : R.j[name].h;
  const v = new THREE.Vector3(...(off || [0, 0, 0]));
  o.localToWorld(v); env.group.worldToLocal(v);
  return [v.x, v.y, v.z];
}
function headPos(R, env) { return jointPos(R, 'head', env); }
