// ───────────────────────── apartments ─────────────────────────
// Room: x ∈ [-5, 5], z ∈ [-3, 3]; back wall at z = -3. Robots ~1.0 tall, counters 0.55.
const COUNTER_H = 0.42;

function buildRoomShell(env, o) {
  const g = env.group;
  const floorTex = plankTex(o.floor, o.floorDark, 512, 512, 7, o.seed);
  floorTex.wrapS = floorTex.wrapT = THREE.RepeatWrapping; floorTex.repeat.set(4, 2.4);
  mesh(boxg(10.4, 0.1, 6.4), texMat(floorTex), 0, -0.05, 0, g, { cast: false });
  const wallTex = noiseTex(o.wall, 0.035, 256, 256, o.seed + 2, [6, 3]);
  const wm = texMat(wallTex);
  wallWithHoles(g, 10.4, 2.6, 0.2, wm, o.holes, 0, 0, -3.1);
  wallWithHoles(g, 6.4, 2.6, 0.2, wm, o.leftHoles || [], -5.1, 0, 0, Math.PI / 2);
  wallWithHoles(g, 6.4, 2.6, 0.2, wm, o.rightHoles || [], 5.1, 0, 0, -Math.PI / 2);
  // baseboards
  const bb = toon(o.trim);
  mesh(boxg(10.2, 0.08, 0.03), bb, 0, 0.04, -2.99, g, { cast: false });
  mesh(boxg(0.03, 0.08, 6.2), bb, -4.99, 0.04, 0, g, { cast: false });
  mesh(boxg(0.03, 0.08, 6.2), bb, 4.99, 0.04, 0, g, { cast: false });
  if (o.wainscot) {
    const wmat = texMat(plankTex(o.wainscot, shade(o.wainscot, 0.7), 256, 256, 1, 4));
    mesh(boxg(10.2, 0.75, 0.025), wmat, 0, 0.375, -2.985, g, { cast: false });
    mesh(boxg(10.2, 0.04, 0.05), bb, 0, 0.77, -2.98, g, { cast: false });
  }
  // ceiling (only visible from low angles)
  mesh(boxg(10.4, 0.1, 6.4), toon(o.ceiling || 0xf2efe8), 0, 2.65, 0, g, { cast: false });
  // outside sky plane behind back wall
  const sky = skyTex(o.skyTop, o.skyMid, o.skyBot, { sun: [380, 120], seed: o.seed, near: o.skyNear, far: o.skyFar });
  mesh(new THREE.PlaneGeometry(16, 6), new THREE.MeshBasicMaterial({ map: sky, toneMapped: false }), 0, 1.6, -4.2, g, { cast: false, recv: false });
  // lights
  env.hemi = new THREE.HemisphereLight(o.hemiSky, o.hemiGround, o.hemiI); g.add(env.hemi);
  env.amb = new THREE.AmbientLight(o.amb, o.ambI); g.add(env.amb);
  const key = new THREE.DirectionalLight(o.keyC, o.keyI); key.position.set(2, 4, 6); g.add(key); g.add(key.target); key.target.position.set(0, 0.5, 0); env.key = key;
  makeSun(env, o.sunC, o.sunI, [-0.35, 0.55, -1.0], 6.5);
  env.focusDefault = new THREE.Vector3(0, 0.5, -1);
}

function buildCodexHome() {
  const env = makeEnv('cxHome', new THREE.Vector3(0, 0, 0));
  const g = env.group;
  buildRoomShell(env, {
    floor: '#c9c1b3', floorDark: '#9e9384', wall: '#dfe7e6', trim: 0xf6f8f8, seed: 11,
    holes: [[-3.55, 1.35, 1.3, 1.0], [2.6, 1.4, 1.1, 0.8]], rightHoles: [[1.1, 0.75, 0.75, 1.5]],
    skyTop: '#9ec9e8', skyMid: '#f6d7b8', skyBot: '#ffd9a6', skyNear: 'rgba(110,130,160,0.85)', skyFar: 'rgba(170,190,215,0.65)',
    hemiSky: 0xdfeeff, hemiGround: 0xc8b49a, hemiI: 1.25, amb: 0xfff6ee, ambI: 0.4, keyC: 0xfff1df, keyI: 0.9, sunC: 0xffe0b0, sunI: 2.4,
  });
  const white = toon(0xf4f6f6), graph = toon(PAL.cxGraphite), mint = toon(PAL.cxMint, { emissive: PAL.cxMint, ei: 0.25 }), wood = toon(0xc49a6c);
  addWindow(g, -3.55, 1.35, -3.0, 1.3, 1.0, white);
  addWindow(g, 2.6, 1.4, -3.0, 1.1, 0.8, white);
  // front door on right wall
  const door = group(g, 4.98, 0, 1.1, -Math.PI / 2);
  mesh(boxg(0.75, 1.5, 0.05), toon(0x5d6b73), 0, 0.75, 0, door, { ol: 0.006 });
  mesh(sphg(0.03), toon(0xd0d4d6), -0.28, 0.72, 0.04, door);
  mesh(boxg(0.85, 0.06, 0.08), white, 0, 1.53, 0, door);

  // ── charging pod bed (along back wall, head at -x) ──
  const bed = group(g, -3.55, 0, -2.5); env.bed = bed;
  mesh(rbox(1.5, 0.2, 0.8, 0.08, 4), graph, 0, 0.1, 0, bed, { ol: 0.01 });
  mesh(rbox(1.16, 0.08, 0.66, 0.04, 3), white, 0.1, 0.24, 0, bed, { ol: 0.006 }); // mattress, top = 0.28
  mesh(rbox(0.3, 0.05, 0.5, 0.03), toon(0xbfeee2), -0.6, 0.215, 0, bed); // head cradle, top 0.24
  // hood at head end with charging ring
  mesh(rbox(0.14, 0.7, 0.8, 0.06, 4), graph, -0.73, 0.45, 0, bed, { ol: 0.01 });
  mesh(rbox(0.2, 0.08, 0.8, 0.04), white, -0.66, 0.8, 0, bed, { ol: 0.006 });
  env.bedGlow = [];
  const gm = glow(PAL.cxMint, 1.6).clone();
  env.bedGlow.push(mesh(boxg(1.3, 0.018, 0.01), gm, 0, 0.12, 0.405, bed, { cast: false }));
  env.bedGlow.push(mesh(torg(0.14, 0.012, 6, 32), gm, -0.655, 0.46, 0, bed, { ry: Math.PI / 2, cast: false }));
  env.bedGlowMat = gm;
  // cable to wall
  mesh(cylg(0.012, 0.012, 0.5, 6), toon(0x222222), -0.8, 0.12, -0.3, bed, { rz: Math.PI / 2, cast: false });
  // nightstand + alarm clock + lamp
  const ns = group(g, -2.45, 0, -2.65);
  mesh(rbox(0.45, 0.4, 0.4, 0.04), white, 0, 0.2, 0, ns, { ol: 0.006 });
  mesh(boxg(0.4, 0.004, 0.005), graph, 0, 0.2, 0.201, ns, { cast: false });
  const clockTex = canvasTex(128, 64); env.clockTex = clockTex;
  const clk = group(ns, -0.08, 0.4, 0.05);
  mesh(rbox(0.18, 0.1, 0.08, 0.02), graph, 0, 0.05, 0, clk, { ol: 0.005 });
  mesh(new THREE.PlaneGeometry(0.15, 0.075), new THREE.MeshBasicMaterial({ map: clockTex, toneMapped: false }), 0, 0.05, 0.041, clk, { cast: false });
  const lamp = group(ns, 0.12, 0.4, -0.05);
  mesh(cylg(0.05, 0.06, 0.02, 16), graph, 0, 0.01, 0, lamp);
  mesh(cylg(0.008, 0.008, 0.22, 6), graph, 0, 0.12, 0, lamp);
  mesh(cylg(0.04, 0.08, 0.08, 16, true), toon(0xf8f4e8, { emissive: 0xffe8c0, ei: 0.4 }), 0, 0.25, 0, lamp, { ol: 0.004 });
  // shelf with books & trophy above bed
  const sh = group(g, -3.55, 1.95, -2.86);
  mesh(boxg(1.5, 0.04, 0.24), wood, 0, 0, 0, sh, { ol: 0.005 });
  addBooks(sh, -0.35, 0.02, 0, 0.6, 3, [0x2d3e50, 0x3fe6c0, 0xf1ece0, 0x6b7a85, 0x34c8ec]);
  const tro = group(sh, 0.45, 0.02, 0);
  mesh(cylg(0.04, 0.05, 0.03, 12), graph, 0, 0.015, 0, tro); mesh(cylg(0.012, 0.012, 0.06, 6), toon(0xe8c45a), 0, 0.06, 0, tro); mesh(cylg(0.05, 0.02, 0.07, 14), toon(0xe8c45a), 0, 0.12, 0, tro, { ol: 0.004 });
  addPlant(sh, 0.68, 0.02, 0, 0.35, 0xf3f3f3, 0x3d8f62);
  // posters
  addPoster(g, -1.4, 1.5, -2.98, 0.6, 0.8, (c, w, h) => {
    c.fillStyle = '#1d2a30'; c.fillRect(0, 0, w, h);
    c.fillStyle = '#3fe6c0'; c.font = `800 44px ${FONT_UI}`; c.textAlign = 'center'; c.fillText('SHIP', w / 2, 70); c.fillText('IT.', w / 2, 116);
    c.strokeStyle = '#f3f6f7'; c.lineWidth = 6; c.beginPath(); c.moveTo(w / 2, 150); c.lineTo(w / 2 - 22, 210); c.lineTo(w / 2 + 22, 210); c.closePath(); c.stroke();
    c.fillStyle = '#ffb36e'; c.beginPath(); c.moveTo(w / 2 - 10, 214); c.lineTo(w / 2, 245); c.lineTo(w / 2 + 10, 214); c.fill();
  });
  addPoster(g, -4.98, 1.4, -1.2, 0.7, 0.5, (c, w, h) => {
    c.fillStyle = '#f3f6f7'; c.fillRect(0, 0, w, h); c.fillStyle = '#2c3238'; c.font = `700 120px ${FONT_MONO}`; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText('{ }', w / 2, h / 2); c.fillStyle = '#34c8ec'; c.fillRect(30, h - 40, w - 60, 10);
  }, Math.PI / 2);
  addRug(g, -2.6, -0.8, 1.4, 1.0, (c, w, h) => { c.fillStyle = '#cfe9e3'; c.fillRect(0, 0, w, h); c.strokeStyle = '#8fd6c6'; c.lineWidth = 18; for (let r = 60; r < 260; r += 50) { c.beginPath(); c.arc(256, 256, r, 0, TAU); c.stroke(); } });
  addPlant(g, -4.5, 0, -2.6, 1.5, 0xf4f6f6, 0x3d8f62);
  // beanbag + small desk w/ laptop
  mesh(sphg(0.35, 20, 14), toon(0x5fb8b0), -4.3, 0.18, 0.8, g, { s: [1, 0.55, 1], ol: 0.01 });
  const desk = group(g, -1.1, 0, -2.6);
  mesh(rbox(1.0, 0.04, 0.5, 0.015), wood, 0, 0.5, 0, desk, { ol: 0.006 });
  for (const sx of [-0.45, 0.45]) mesh(boxg(0.04, 0.5, 0.45), graph, sx, 0.25, 0, desk);
  const lap = group(desk, 0, 0.52, 0.02);
  mesh(rbox(0.36, 0.015, 0.24, 0.005), toon(0xc8ccd0), 0, 0.008, 0.02, lap);
  const scr = group(lap, 0, 0.012, -0.1); scr.rotation.x = -0.3;
  mesh(rbox(0.36, 0.24, 0.012, 0.005), toon(0xc8ccd0), 0, 0.12, 0, scr);
  mesh(new THREE.PlaneGeometry(0.33, 0.2), glow(0x1d3940, 1.0), 0, 0.12, 0.007, scr, { cast: false });

  // ── kitchen (right side) ──
  const k = group(g, 0, 0, 0);
  const cabFront = toon(0xf7f8f8), counterTop = toon(0x8e989e), handle = toon(0x2c3238);
  // base cabinets x 0.9..4.3 along back wall
  mesh(boxg(3.4, COUNTER_H - 0.04, 0.6), cabFront, 2.6, (COUNTER_H - 0.04) / 2, -2.7, k, { ol: 0.008 });
  mesh(rbox(3.5, 0.04, 0.66, 0.01), counterTop, 2.6, COUNTER_H - 0.02, -2.68, k, { ol: 0.006 });
  for (let i = 0; i < 6; i++) { const cx = 1.18 + i * 0.57; mesh(boxg(0.004, COUNTER_H - 0.1, 0.004), toon(0xc9d0d3), cx + 0.285, 0.27, -2.398, k, { cast: false }); mesh(rbox(0.16, 0.018, 0.02, 0.008), handle, cx, 0.44, -2.39, k); }
  mesh(boxg(3.4, 0.05, 0.02), toon(0x2c3238), 2.6, 0.025, -2.39, k, { cast: false });
  // upper cabinets
  mesh(boxg(1.2, 0.5, 0.35), cabFront, 1.25, 1.85, -2.82, k, { ol: 0.008 });
  mesh(boxg(1.0, 0.5, 0.35), cabFront, 3.85, 1.85, -2.82, k, { ol: 0.008 });
  // backsplash tiles
  mesh(new THREE.PlaneGeometry(3.4, 0.5), texMat(canvasTex(256, 64, (c, w, h) => { c.fillStyle = '#eaf3f2'; c.fillRect(0, 0, w, h); c.strokeStyle = '#bcd3d0'; c.lineWidth = 2; for (let x = 0; x < w; x += 16) { c.beginPath(); c.moveTo(x, 0); c.lineTo(x, h); c.stroke(); } for (let y = 0; y < h; y += 16) { c.beginPath(); c.moveTo(0, y); c.lineTo(w, y); c.stroke(); } })), 2.6, COUNTER_H + 0.25, -2.995, k, { cast: false });
  // sink under window
  mesh(rbox(0.5, 0.02, 0.34, 0.01), toon(0xb9c3c8), 2.6, COUNTER_H + 0.002, -2.72, k);
  const fau = group(k, 2.6, COUNTER_H, -2.93);
  mesh(cylg(0.015, 0.02, 0.22, 8), toon(0xd8dde0), 0, 0.11, 0, fau); mesh(cylg(0.012, 0.012, 0.16, 8), toon(0xd8dde0), 0, 0.22, 0.07, fau, { rx: Math.PI / 2 });
  // espresso machine at x=1.5
  const em = group(k, 1.45, COUNTER_H, -2.55); em.scale.setScalar(0.8); env.espresso = em;
  mesh(rbox(0.42, 0.42, 0.36, 0.04), toon(0xd9dee1), 0, 0.21, 0, em, { ol: 0.008 });
  mesh(rbox(0.44, 0.06, 0.38, 0.02), graph, 0, 0.44, 0, em, { ol: 0.005 });
  mesh(rbox(0.36, 0.1, 0.02, 0.01), graph, 0, 0.33, 0.18, em);
  const emTex = canvasTex(128, 32); env.espressoTex = emTex;
  mesh(new THREE.PlaneGeometry(0.16, 0.04), new THREE.MeshBasicMaterial({ map: emTex, toneMapped: false }), -0.06, 0.33, 0.192, em, { cast: false });
  env.espressoBtn = mesh(cylg(0.022, 0.022, 0.02, 14), mint, 0.1, 0.33, 0.19, em, { rx: Math.PI / 2 });
  mesh(cylg(0.05, 0.045, 0.04, 16), graph, 0, 0.23, 0.12, em); // group head
  mesh(boxg(0.16, 0.02, 0.03), graph, 0.06, 0.2, 0.17, em); // portafilter handle
  mesh(cylg(0.006, 0.006, 0.12, 6), toon(0xd8dde0), -0.18, 0.18, 0.15, em, { rz: 0.3 }); // steam wand
  mesh(rbox(0.3, 0.02, 0.16, 0.01), graph, 0, 0.012, 0.1, em); // drip tray
  // espresso stream (scaled by story)
  env.stream = mesh(cylg(0.006, 0.006, 1, 6), toon(0x4a2a18), 0, 0.12, 0.12, em, { cast: false });
  env.stream.visible = false;
  // toaster at x=2.0
  const ts = group(k, 2.1, COUNTER_H, -2.55); env.toaster = ts;
  mesh(rbox(0.3, 0.2, 0.18, 0.06), toon(PAL.cxMint), 0, 0.1, 0, ts, { ol: 0.006 });
  mesh(boxg(0.2, 0.01, 0.03), toon(0x1b1e20), 0, 0.2, -0.035, ts, { cast: false });
  mesh(boxg(0.2, 0.01, 0.03), toon(0x1b1e20), 0, 0.2, 0.035, ts, { cast: false });
  env.toastLever = mesh(rbox(0.04, 0.02, 0.05, 0.008), graph, 0.16, 0.15, 0, ts);
  // fruit bowl, knife block, fridge
  const fb = group(k, 3.5, COUNTER_H, -2.7);
  mesh(cylg(0.14, 0.08, 0.07, 18), toon(0xf4f6f6), 0, 0.035, 0, fb, { ol: 0.004 });
  [[0xe24c3c, -0.05, 0], [0xf3c64b, 0.05, 0.02], [0x7cc36a, 0, -0.06]].forEach(([c, x, z]) => mesh(sphg(0.045), toon(c), x, 0.09, z, fb, { ol: 0.003 }));
  const fr = group(k, 4.6, 0, -2.6);
  mesh(rbox(0.7, 1.7, 0.7, 0.05), toon(0xeef1f2), 0, 0.85, 0, fr, { ol: 0.01 });
  mesh(boxg(0.68, 0.01, 0.01), toon(0xb8c0c4), 0, 1.15, 0.351, fr, { cast: false });
  mesh(rbox(0.03, 0.4, 0.03, 0.01), graph, -0.28, 1.35, 0.37, fr); mesh(rbox(0.03, 0.3, 0.03, 0.01), graph, -0.28, 0.8, 0.37, fr);
  [[0xffd36b, 0.1, 1.4], [0x9ff2d6, -0.05, 1.3], [0xffffff, 0.15, 0.9]].forEach(([c, x, y], i) => mesh(boxg(0.12, 0.12, 0.005), toon(c), x, y, 0.353, fr, { cast: false, rz: (i - 1) * 0.1 }));
  // pendant lamps
  for (const lx of [1.8, 3.3]) {
    mesh(cylg(0.004, 0.004, 0.8, 4), graph, lx, 2.25, -1.8, k, { cast: false });
    mesh(cylg(0.06, 0.16, 0.14, 18, true), graph, lx, 1.8, -1.8, k, { ol: 0.004 });
    mesh(sphg(0.05), glow(0xfff0d0, 1.4), lx, 1.74, -1.8, k, { cast: false });
  }
  // bar table + stool
  const bt = group(k, 2.6, 0, -0.9);
  mesh(cylg(0.32, 0.32, 0.03, 28), wood, 0, 0.62, 0, bt, { ol: 0.006 });
  mesh(cylg(0.03, 0.03, 0.6, 8), graph, 0, 0.31, 0, bt); mesh(cylg(0.18, 0.2, 0.02, 20), graph, 0, 0.01, 0, bt);
  const st = group(k, 3.2, 0, -0.7);
  mesh(cylg(0.13, 0.13, 0.04, 20), toon(PAL.cxMint), 0, 0.42, 0, st, { ol: 0.005 });
  mesh(cylg(0.02, 0.02, 0.4, 6), graph, 0, 0.2, 0, st);
  addPlant(g, 4.4, 0, -1.4, 1.2, 0x2c3238, 0x4f9a6a);

  // props the story moves
  env.props = {};
  const cup = group(g); env.props.cup = cup;
  mesh(cylg(0.04, 0.032, 0.06, 16), toon(0xffffff), 0, 0.03, 0, cup, { ol: 0.004 });
  mesh(torg(0.02, 0.006, 6, 12), toon(0xffffff), 0.045, 0.032, 0, cup, { ry: 0, cast: false });
  env.cupFill = mesh(cylg(0.034, 0.034, 0.004, 16), toon(0x4a2a18), 0, 0.01, 0, cup, { cast: false });
  mesh(cylg(0.06, 0.06, 0.006, 16), toon(0xffffff), 0, 0.003, 0, cup, { cast: false }); // saucer-ish base
  const toast = group(g); env.props.toast = toast;
  env.toastMesh = mesh(rbox(0.11, 0.12, 0.022, 0.02), toon(0xd9a35c), 0, 0, 0, toast, { ol: 0.004 });
  mesh(rbox(0.095, 0.105, 0.024, 0.018), toon(0xf2d39a), 0, 0, 0, toast, { cast: false });
  env.toastBite = mesh(sphg(0.035, 10, 8), toon(0xf2d39a), 0.055, 0.06, 0, toast, { cast: false });
  env.toastBite.visible = false;
  const bag = group(g); env.props.bag = bag;
  mesh(rbox(0.18, 0.2, 0.08, 0.04), toon(0x2c3238), 0, 0, 0, bag, { ol: 0.005 });
  mesh(boxg(0.12, 0.01, 0.01), toon(PAL.cxMint), 0, 0.03, 0.041, bag, { cast: false });
  env.motes = dustMotes(g, new THREE.Vector3(-3.4, 0, -1.8), new THREE.Vector3(1.4, 1.6, 1.4));
  env.beams = [addSunBeam(g, new THREE.Vector3(-3.6, 1.4, -3.05), new THREE.Vector3(-2.6, 0.0, -1.0), 1.2, 0xffe6c0, 0.08),
    addSunBeam(g, new THREE.Vector3(2.6, 1.4, -3.05), new THREE.Vector3(3.4, 0.4, -1.5), 0.9, 0xffe6c0, 0.06)];
  env.fogColor = 0xe9e2d8;
  env.bg = new THREE.Color(0xe9e2d8);
  return env;
}

function buildClaudeHome() {
  const env = makeEnv('ccHome', new THREE.Vector3(0, 0, 40));
  const g = env.group;
  env.props = {};
  buildRoomShell(env, {
    floor: '#a8754e', floorDark: '#6e4a31', wall: '#f1dfc6', trim: 0xe9d5b8, seed: 23, wainscot: '#b98a5e', ceiling: 0xf3e6d3,
    holes: [[3.55, 1.4, 1.1, 1.1], [-2.6, 1.4, 1.0, 0.8]], leftHoles: [[-1.1, 0.75, 0.75, 1.5]],
    skyTop: '#a9c6e0', skyMid: '#ffd9b0', skyBot: '#ffc78e', skyNear: 'rgba(150,120,120,0.85)', skyFar: 'rgba(200,175,175,0.6)',
    hemiSky: 0xfff0dc, hemiGround: 0xb08060, hemiI: 1.25, amb: 0xffeedd, ambI: 0.4, keyC: 0xffe6cc, keyI: 0.9, sunC: 0xffd7a0, sunI: 2.4,
  });
  const wood = toon(0x9a6440), woodL = toon(0xc99668), cream = toon(0xf6e8d2), copper = toon(PAL.ccCopper), dark = toon(0x4a2e20);
  // round window over bed
  const rw = group(g, 3.55, 1.4, -3.0);
  mesh(torg(0.56, 0.05, 10, 40), woodL, 0, 0, 0, rw, { ol: 0.006 });
  mesh(boxg(1.1, 0.03, 0.04), woodL, 0, 0, 0, rw); mesh(boxg(0.03, 1.1, 0.04), woodL, 0, 0, 0, rw);
  mesh(rbox(1.3, 0.05, 0.16, 0.02), woodL, 0, -0.62, 0.05, rw, { ol: 0.005 });
  // fill the square hole corners around round window
  const wm = texMat(noiseTex('#f1dfc6', 0.035, 256, 256, 25, [1, 1]));
  for (const [x, y] of [[-1, 1], [1, 1], [-1, -1], [1, -1]]) {
    const shp = new THREE.Shape(); const s = 0.55;
    shp.moveTo(x * s, y * s); shp.lineTo(0, y * s); shp.absarc(0, 0, s, y > 0 ? Math.PI / 2 : -Math.PI / 2, x > 0 ? 0 : Math.PI, (x > 0) === (y > 0)); shp.lineTo(x * s, y * s);
    mesh(new THREE.ExtrudeGeometry(shp, { depth: 0.2, bevelEnabled: false }), wm, 0, 0, -0.1, rw);
  }
  addWindow(g, -2.6, 1.4, -3.0, 1.0, 0.8, woodL);
  // door on left wall
  const door = group(g, -4.98, 0, 1.1, Math.PI / 2);
  mesh(boxg(0.75, 1.5, 0.05), toon(0x7a4a30), 0, 0.75, 0, door, { ol: 0.006 });
  mesh(sphg(0.03), toon(0xd8a060), 0.28, 0.72, 0.04, door);
  // ── nest bed (round, along back wall right) ──
  const bed = group(g, 3.55, 0, -2.45); env.bed = bed;
  mesh(cylg(0.78, 0.72, 0.2, 40), wood, 0, 0.1, 0, bed, { s: [1, 1, 0.62], ol: 0.01 });
  mesh(torg(0.74, 0.09, 12, 40), toon(0xe7c7a0), 0, 0.24, 0, bed, { rx: Math.PI / 2, s: [1, 0.62, 1], ol: 0.008 });
  mesh(cylg(0.7, 0.7, 0.07, 40), toon(0xf4dfc4), 0, 0.235, 0, bed, { s: [1, 1, 0.6] }); // mattress top 0.27
  env.pillow = mesh(sphg(0.22, 16, 12), toon(0xfbf2e4), -0.5, 0.27, 0, bed, { s: [0.9, 0.3, 1.3], ol: 0.006 });
  // blanket (story animates)
  const blanket = group(bed, 0.22, 0.27, 0); env.blanket = blanket;
  const blTex = canvasTex(128, 128, (c, w, h) => { c.fillStyle = '#e48a52'; c.fillRect(0, 0, w, h); c.fillStyle = '#f6c48e'; for (let i = 0; i < 8; i++) { c.fillRect(i * 16, 0, 6, h); c.fillRect(0, i * 16, w, 6); } }, { repeat: [2, 2] });
  env.blanketMesh = mesh(rbox(0.85, 0.07, 0.62, 0.035, 3), new THREE.MeshToonMaterial({ map: blTex, gradientMap: GRAD3 }), 0, 0.0, 0, blanket, { ol: 0.006 });
  // copper charging coil arch at head
  mesh(torg(0.35, 0.025, 8, 32, Math.PI), copper, -0.72, 0.25, 0, bed, { ry: Math.PI / 2, ol: 0.005 });
  env.bedGlowMat = glow(PAL.ccAmber, 1.5).clone();
  env.bedGlow = [mesh(torg(0.35, 0.01, 6, 32, Math.PI), env.bedGlowMat, -0.7, 0.25, 0, bed, { ry: Math.PI / 2, cast: false })];
  // bedside table, alarm (bell chime clock), tea-light lamp
  const ns = group(g, 2.35, 0, -2.65);
  mesh(cylg(0.22, 0.2, 0.42, 20), woodL, 0, 0.21, 0, ns, { ol: 0.006 });
  const clockTex = canvasTex(128, 64); env.clockTex = clockTex;
  const clk = group(ns, -0.05, 0.42, 0.06);
  mesh(cylg(0.07, 0.07, 0.05, 20), copper, 0, 0.07, 0, clk, { rx: Math.PI / 2, ol: 0.005 });
  mesh(new THREE.CircleGeometry(0.058, 20), new THREE.MeshBasicMaterial({ map: clockTex, toneMapped: false }), 0, 0.07, 0.026, clk, { cast: false });
  mesh(sphg(0.025), copper, -0.05, 0.14, 0, clk); mesh(sphg(0.025), copper, 0.05, 0.14, 0, clk);
  mesh(cylg(0.04, 0.05, 0.1, 14), toon(0xfff3e0, { emissive: 0xffc890, ei: 0.5 }), 0.1, 0.47, -0.06, ns, { ol: 0.004 });
  // bookshelf on back wall left of bed
  const bs = group(g, 1.3, 0, -2.85);
  mesh(boxg(1.2, 1.8, 0.3), wood, 0, 0.9, 0, bs, { ol: 0.008 });
  for (let i = 0; i < 4; i++) {
    mesh(boxg(1.12, 0.03, 0.28), woodL, 0, 0.12 + i * 0.44, 0.02, bs);
    addBooks(bs, 0, 0.135 + i * 0.44, 0.04, 1.0, 40 + i, [0xc96a3d, 0x7b4a2e, 0xe0c27a, 0x6c8a5a, 0xa04a4a, 0xf1ece0, 0x8a5a8a]);
  }
  addPlant(bs, 0.4, 1.8, 0.02, 0.5, 0xc8693a, 0x5c9a5a);
  // string lights over bed
  const sl = group(g, 3.55, 2.0, -2.95);
  for (let i = 0; i < 11; i++) { const x = -0.9 + i * 0.18; const y = -Math.sin((i / 10) * Math.PI) * 0.2; mesh(sphg(0.025, 8, 6), glow(0xffc57a, 1.8), x, y, 0.02, sl, { cast: false }); }
  // posters / framed art
  addPoster(g, 4.98, 1.45, -1.0, 0.6, 0.6, (c, w, h) => { c.fillStyle = '#f4e3c8'; c.fillRect(0, 0, w, h); c.strokeStyle = '#c8693a'; c.lineWidth = 10; c.lineCap = 'round'; for (let i = 0; i < 8; i++) { const a = i / 8 * TAU; c.beginPath(); c.moveTo(w / 2 + Math.cos(a) * 20, h / 2 + Math.sin(a) * 20); c.lineTo(w / 2 + Math.cos(a) * 85, h / 2 + Math.sin(a) * 85); c.stroke(); } }, -Math.PI / 2);
  addPoster(g, 0.1, 1.55, -2.98, 0.5, 0.65, (c, w, h) => { c.fillStyle = '#3b2a20'; c.fillRect(0, 0, w, h); c.fillStyle = '#ffb877'; c.font = `700 30px ${FONT_UI}`; c.textAlign = 'center'; c.fillText('measure', w / 2, 90); c.fillText('twice,', w / 2, 128); c.fillText('commit', w / 2, 166); c.fillText('once.', w / 2, 204); });
  addRug(g, 2.6, -0.7, 1.5, 1.0, (c, w, h) => { c.fillStyle = '#d9a46a'; c.fillRect(0, 0, w, h); c.strokeStyle = '#b5683c'; c.lineWidth = 14; for (let r = 40; r < 260; r += 36) { c.beginPath(); c.arc(256, 256, r, 0, TAU); c.stroke(); } });
  addPlant(g, 4.55, 0, -2.6, 1.4, 0xc8693a, 0x5c9a5a);
  // armchair
  const ac = group(g, 4.1, 0, 0.6, -0.6);
  mesh(rbox(0.7, 0.3, 0.6, 0.1), toon(0xc87a4a), 0, 0.2, 0, ac, { ol: 0.008 });
  mesh(rbox(0.7, 0.5, 0.16, 0.08), toon(0xc87a4a), 0, 0.45, -0.25, ac, { ol: 0.008 });
  for (const sx of [-0.33, 0.33]) mesh(rbox(0.12, 0.38, 0.6, 0.05), toon(0xb56a3e), sx, 0.3, 0, ac, { ol: 0.006 });

  // ── kitchen (left side) ──
  const k = group(g);
  mesh(boxg(3.4, COUNTER_H - 0.04, 0.6), toon(0xe7d3b5), -2.6, (COUNTER_H - 0.04) / 2, -2.7, k, { ol: 0.008 });
  mesh(rbox(3.5, 0.045, 0.66, 0.01), woodL, -2.6, COUNTER_H - 0.02, -2.68, k, { ol: 0.006 });
  for (let i = 0; i < 6; i++) { const cx = -4.02 + i * 0.57; mesh(boxg(0.004, COUNTER_H - 0.1, 0.004), toon(0xc9b498), cx + 0.285, 0.27, -2.398, k, { cast: false }); mesh(sphg(0.018), copper, cx, 0.44, -2.39, k); }
  mesh(boxg(3.4, 0.05, 0.02), dark, -2.6, 0.025, -2.39, k, { cast: false });
  // open shelves with jars & mugs
  for (const y of [1.55, 1.95]) mesh(boxg(1.4, 0.035, 0.24), woodL, -1.4, y, -2.88, k, { ol: 0.005 });
  const jarCols = [0xd9b48a, 0x9cc58c, 0xe8a070, 0xc9a8d8];
  for (let i = 0; i < 4; i++) { mesh(cylg(0.06, 0.06, 0.16, 14), toon(0xf6efe4, { p: { transparent: true, opacity: 0.9 } }), -1.9 + i * 0.32, 1.65, -2.86, k, { ol: 0.004 }); mesh(cylg(0.05, 0.05, 0.08, 14), toon(jarCols[i]), -1.9 + i * 0.32, 1.61, -2.86, k, { cast: false }); mesh(cylg(0.062, 0.062, 0.03, 14), woodL, -1.9 + i * 0.32, 1.745, -2.86, k); }
  for (let i = 0; i < 4; i++) { mesh(cylg(0.045, 0.038, 0.08, 14), toon([0xc8693a, 0xf6e8d2, 0x6c8a5a, 0xe0c27a][i]), -1.9 + i * 0.3, 2.01, -2.86, k, { ol: 0.004 }); }
  // tiled backsplash
  mesh(new THREE.PlaneGeometry(3.4, 0.5), texMat(canvasTex(256, 64, (c, w, h) => { c.fillStyle = '#f3e3c9'; c.fillRect(0, 0, w, h); c.strokeStyle = '#d9b48a'; c.lineWidth = 2; for (let y = 0; y < h; y += 16) for (let x = (y / 16) % 2 ? 8 : 0; x < w; x += 32) c.strokeRect(x, y, 32, 16); })), -2.6, COUNTER_H + 0.25, -2.995, k, { cast: false });
  // stove + copper kettle at x=-3.6
  const stv = group(k, -3.6, COUNTER_H, -2.55); env.stove = stv;
  mesh(rbox(0.6, 0.03, 0.45, 0.01), toon(0x2a2422), 0, 0.015, 0, stv);
  for (const [x, z] of [[-0.14, -0.1], [0.14, -0.1], [-0.14, 0.1], [0.14, 0.1]]) mesh(torg(0.06, 0.008, 6, 20), toon(0x555050), x, 0.032, z, stv, { rx: Math.PI / 2, cast: false });
  env.burner = mesh(torg(0.06, 0.01, 6, 20), glow(0xff6a2a, 1.6).clone(), 0.14, 0.035, 0.1, stv, { rx: Math.PI / 2, cast: false });
  for (let i = 0; i < 4; i++) mesh(cylg(0.018, 0.018, 0.02, 10), toon(0xd8c8b0), -0.21 + i * 0.14, -0.06, 0.24, stv, { rx: Math.PI / 2 });
  const kettle = group(g); env.props.kettle = kettle;
  mesh(sphg(0.1, 20, 14), copper, 0, 0.08, 0, kettle, { s: [1, 0.85, 1], ol: 0.006 });
  mesh(cylg(0.09, 0.1, 0.03, 18), copper, 0, 0.015, 0, kettle);
  mesh(cylg(0.02, 0.03, 0.03, 10), dark, 0, 0.17, 0, kettle);
  mesh(cylg(0.012, 0.02, 0.12, 8), copper, 0.1, 0.1, 0, kettle, { rz: -0.9 });
  mesh(torg(0.07, 0.012, 6, 16, Math.PI), dark, 0, 0.16, 0, kettle, { ry: Math.PI / 2 });
  env.kettleSpout = new THREE.Vector3(0.16, 0.14, 0);
  // teapot + cup + scone plate
  const teapot = group(g); env.props.teapot = teapot;
  mesh(sphg(0.08, 18, 12), toon(0xf6efe4), 0, 0.07, 0, teapot, { s: [1, 0.85, 1], ol: 0.005 });
  mesh(cylg(0.03, 0.04, 0.02, 12), toon(PAL.ccCopper), 0, 0.135, 0, teapot);
  mesh(cylg(0.01, 0.016, 0.08, 8), toon(0xf6efe4), 0.09, 0.08, 0, teapot, { rz: -0.8 });
  mesh(torg(0.04, 0.01, 6, 14), toon(0xf6efe4), -0.085, 0.075, 0, teapot);
  const cup = group(g); env.props.cup = cup;
  mesh(cylg(0.042, 0.034, 0.06, 16), toon(0xf6e8d2), 0, 0.03, 0, cup, { ol: 0.004 });
  mesh(torg(0.02, 0.006, 6, 12), toon(0xf6e8d2), 0.046, 0.032, 0, cup, { cast: false });
  mesh(cylg(0.044, 0.044, 0.008, 16), toon(PAL.ccCopper), 0, 0.05, 0, cup, { cast: false });
  env.cupFill = mesh(cylg(0.036, 0.036, 0.004, 16), toon(0xb8662c), 0, 0.045, 0, cup, { cast: false });
  env.teaTag = mesh(boxg(0.02, 0.025, 0.002), toon(0xffd27a), -0.05, 0.05, 0, cup, { cast: false });
  const plate = group(g); env.props.plate = plate;
  mesh(cylg(0.1, 0.08, 0.012, 20), toon(0xffffff), 0, 0.006, 0, plate, { ol: 0.003 });
  const scone = group(g); env.props.scone = scone;
  env.sconeMesh = mesh(sphg(0.05, 14, 10), toon(0xd9a35c), 0, 0.025, 0, scone, { s: [1, 0.65, 1], ol: 0.004 });
  mesh(sphg(0.05, 14, 10), toon(0xc04050), 0, 0.045, 0, scone, { s: [0.7, 0.15, 0.7], cast: false });
  env.sconeBite = mesh(sphg(0.03, 10, 8), toon(0xf2d39a), 0.04, 0.03, 0, scone, { cast: false }); env.sconeBite.visible = false;
  const tumbler = group(g); env.props.tumbler = tumbler;
  mesh(cylg(0.04, 0.035, 0.14, 16), copper, 0, 0.07, 0, tumbler, { ol: 0.004 });
  mesh(cylg(0.042, 0.042, 0.03, 16), cream, 0, 0.15, 0, tumbler);
  // round table + chair (Claude sits here)
  const tb = group(g, -2.3, 0, -0.6); env.table = tb;
  mesh(cylg(0.42, 0.42, 0.04, 32), woodL, 0, 0.5, 0, tb, { ol: 0.007 });
  mesh(cylg(0.04, 0.05, 0.48, 10), wood, 0, 0.25, 0, tb); mesh(cylg(0.22, 0.25, 0.03, 24), wood, 0, 0.015, 0, tb);
  const ch = group(g, -2.3, 0, 0.02); env.chair = ch;
  mesh(rbox(0.4, 0.05, 0.38, 0.02), toon(0xc87a4a), 0, 0.3, 0, ch, { ol: 0.006 });
  for (const [x, z] of [[-0.16, -0.15], [0.16, -0.15], [-0.16, 0.15], [0.16, 0.15]]) mesh(cylg(0.018, 0.015, 0.3, 6), wood, x, 0.15, z, ch);
  mesh(rbox(0.4, 0.35, 0.04, 0.02), wood, 0, 0.5, 0.18, ch, { ol: 0.006 });
  // pendant
  mesh(cylg(0.004, 0.004, 0.8, 4), dark, -2.3, 2.25, -0.6, k, { cast: false });
  mesh(G('hemiShade', () => new THREE.SphereGeometry(0.16, 18, 10, 0, TAU, 0, Math.PI / 2)), toon(PAL.ccCopper, { p: { side: THREE.DoubleSide } }), -2.3, 1.78, -0.6, k, { ol: 0.004 });
  mesh(sphg(0.05), glow(0xffe0b0, 1.5), -2.3, 1.76, -0.6, k, { cast: false });
  env.motes = dustMotes(g, new THREE.Vector3(3.3, 0, -1.8), new THREE.Vector3(1.4, 1.6, 1.4), 120, 0xffe0b0);
  env.beams = [addSunBeam(g, new THREE.Vector3(3.55, 1.45, -3.05), new THREE.Vector3(2.6, 0.0, -1.0), 1.1, 0xffd9a8, 0.09),
    addSunBeam(g, new THREE.Vector3(-2.6, 1.4, -3.05), new THREE.Vector3(-1.9, 0.4, -1.5), 0.9, 0xffd9a8, 0.06)];
  env.bg = new THREE.Color(0xf0e2cf);
  return env;
}

function drawClock(tex, text, color, bg) {
  const c = tex.userData.ctx; const { width: w, height: h } = tex.userData.canvas;
  c.fillStyle = bg; c.fillRect(0, 0, w, h);
  c.fillStyle = color; c.shadowColor = color; c.shadowBlur = 8; c.font = `700 38px ${FONT_MONO}`; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(text, w / 2, h / 2 + 2);
  c.shadowBlur = 0;
  tex.needsUpdate = true;
}
