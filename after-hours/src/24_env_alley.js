// ───────────────────────── night alley (rain, neon, puddles) ─────────────────────────
// Alley runs along z ∈ [-12, 12], walls at x = ±2.3. Fight centre at the origin.
function brickTex(base, mortar, seed) {
  return canvasTex(512, 512, (c, w, h) => {
    const r = rng(seed);
    c.fillStyle = mortar; c.fillRect(0, 0, w, h);
    const bh = 32, bw = 72;
    for (let y = 0; y < h; y += bh) for (let x = -((y / bh) % 2) * bw / 2; x < w; x += bw) {
      const l = 0.75 + r() * 0.35;
      c.fillStyle = shade(base, l); c.fillRect(x + 3, y + 3, bw - 6, bh - 6);
      if (r() > 0.8) { c.fillStyle = 'rgba(0,0,0,0.18)'; c.fillRect(x + 3, y + bh - 10, bw - 6, 7); }
    }
    // wet streaks
    for (let i = 0; i < 40; i++) { const x = r() * w; const g = c.createLinearGradient(0, 0, 0, h); g.addColorStop(0, 'rgba(0,0,0,0)'); g.addColorStop(1, 'rgba(0,0,0,0.25)'); c.fillStyle = g; c.fillRect(x, r() * h * 0.5, 2 + r() * 6, h); }
  }, { repeat: [6, 2] });
}
function neonSign(parent, text, color, x, y, z, ry, size = 0.35, font = FONT_UI) {
  const tex = canvasTex(512, 160, (c, w, h) => {
    c.font = `800 110px ${font}`; c.textAlign = 'center'; c.textBaseline = 'middle';
    c.shadowColor = color; c.shadowBlur = 30; c.strokeStyle = color; c.lineWidth = 10; c.strokeText(text, w / 2, h / 2 + 6);
    c.shadowBlur = 0; c.fillStyle = '#ffffff'; c.globalAlpha = 0.9; c.lineWidth = 4; c.strokeStyle = '#fff'; c.strokeText(text, w / 2, h / 2 + 6);
  });
  const g = group(parent, x, y, z, ry);
  const m = new THREE.MeshBasicMaterial({ map: tex, transparent: true, toneMapped: false, color: new THREE.Color(2.2, 2.2, 2.2), depthWrite: false });
  const pl = mesh(new THREE.PlaneGeometry(size * 3.2, size), m, 0, 0, 0.03, g, { cast: false, recv: false });
  mesh(boxg(size * 3.3, size * 1.05, 0.04), toon(0x15161c), 0, 0, 0, g, { cast: false });
  g.userData.mat = m;
  return g;
}

function buildAlley() {
  const env = makeEnv('alley', new THREE.Vector3(0, 0, -140));
  const g = env.group;
  // wet ground with puddle holes over a reflector
  const groundTex = canvasTex(1024, 1024, (c, w, h) => {
    const r = rng(5);
    c.fillStyle = '#2a2d36'; c.fillRect(0, 0, w, h);
    for (let i = 0; i < 3000; i++) { c.fillStyle = `rgba(${r() > 0.5 ? 255 : 0},${r() > 0.5 ? 255 : 0},${r() > 0.5 ? 255 : 0},0.05)`; c.fillRect(r() * w, r() * h, 3, 3); }
    c.strokeStyle = 'rgba(0,0,0,0.35)'; c.lineWidth = 3; for (let i = 0; i < 18; i++) { c.beginPath(); c.moveTo(r() * w, r() * h); for (let k = 0; k < 5; k++) c.lineTo(r() * w, r() * h); c.stroke(); }
  });
  const alphaTex = canvasTex(1024, 1024, (c, w, h) => {
    const r = rng(17);
    c.fillStyle = '#fff'; c.fillRect(0, 0, w, h);
    c.fillStyle = '#000';
    const blobs = [[0.5, 0.5, 0.28, 0.12], [0.35, 0.62, 0.12, 0.06], [0.62, 0.38, 0.14, 0.08], [0.45, 0.25, 0.1, 0.05], [0.55, 0.78, 0.16, 0.07], [0.3, 0.42, 0.08, 0.05], [0.68, 0.6, 0.1, 0.06]];
    for (const [x, y, rx, ry] of blobs) { c.beginPath(); for (let a = 0; a < TAU; a += 0.3) { const k = 1 + (r() - 0.5) * 0.35; const px = (x + Math.cos(a) * rx * k) * w, py = (y + Math.sin(a) * ry * k) * h; a === 0 ? c.moveTo(px, py) : c.lineTo(px, py); } c.closePath(); c.fill(); }
  });
  const groundMat = new THREE.MeshToonMaterial({ map: groundTex, gradientMap: GRAD3, alphaMap: alphaTex, alphaTest: 0.5 });
  const ground = mesh(new THREE.PlaneGeometry(4.6, 24), groundMat, 0, 0.0, 0, g, { rx: -Math.PI / 2, cast: false });
  void ground;
  const refl = new Reflector(new THREE.PlaneGeometry(4.6, 24), { textureWidth: 512, textureHeight: 512, color: 0x6a7080, clipBias: 0.003 });
  refl.rotation.x = -Math.PI / 2; refl.position.y = -0.01; g.add(refl); env.reflector = refl;
  // walls
  const bl = brickTex('#5a4a52', '#2a2228', 3), br = brickTex('#4a4a5e', '#24242e', 8);
  mesh(boxg(0.4, 7, 24), texMat(bl), -2.5, 3.5, 0, g);
  mesh(boxg(0.4, 7, 24), texMat(br), 2.5, 3.5, 0, g);
  // wall details: windows with warm light, AC units, pipes, vents
  const winLit = glow(0xffc477, 1.3), winDim = toon(0x1a1c26);
  for (let i = 0; i < 6; i++) for (const side of [-1, 1]) {
    const z = -9 + i * 3.6 + (side > 0 ? 1.2 : 0); const y = 3.0 + (i % 2) * 1.4;
    mesh(boxg(0.05, 0.8, 0.6), (i + side) % 3 === 0 ? winLit : winDim, side * 2.29, y, z, g, { cast: false });
    mesh(boxg(0.1, 0.05, 0.7), toon(0x3a3a44), side * 2.26, y - 0.43, z, g);
  }
  const metal = toon(0x5c6370), rust = toon(0x7a5040);
  for (const [z, y] of [[-6, 2.2], [4.5, 2.6]]) { const ac = group(g, -2.1, y, z); mesh(rbox(0.38, 0.4, 0.6, 0.03), metal, 0, 0, 0, ac, { ol: 0.01 }); mesh(cylg(0.16, 0.16, 0.02, 16), toon(0x2a2e36), 0.19, 0, 0, ac, { rz: Math.PI / 2 }); }
  for (const z of [-3.5, 2.0, 7.5]) mesh(cylg(0.06, 0.06, 7, 10), rust, -2.22, 3.5, z, g, { ol: 0.008 });
  mesh(cylg(0.05, 0.05, 24, 10), rust, -2.2, 1.9, 0, g, { rx: Math.PI / 2 });
  mesh(cylg(0.07, 0.07, 24, 10), metal, 2.18, 4.6, 0, g, { rx: Math.PI / 2 });
  // fire escape on right wall
  const fe = toon(0x2c2f38);
  for (const y of [2.3, 4.0]) {
    const fz = -2.5;
    mesh(boxg(0.9, 0.04, 3.2), fe, 1.85, y, fz, g, { ol: 0.006 });
    for (let k = 0; k <= 8; k++) mesh(boxg(0.02, 0.6, 0.02), fe, 1.42, y + 0.3, fz - 1.6 + k * 0.4, g, { cast: true });
    mesh(boxg(0.02, 0.02, 3.2), fe, 1.42, y + 0.6, fz, g);
    for (let k = 0; k < 6; k++) mesh(boxg(0.6, 0.02, 0.04), fe, 1.85, y - 0.25 * k - 0.2, fz + 1.4 - k * 0.12, g, { cast: false });
  }
  // dumpster, bags, crates, boxes
  const dump = group(g, -1.75, 0, 3.2);
  mesh(rbox(0.8, 0.7, 1.4, 0.05), toon(0x2f6a4a), 0, 0.38, 0, dump, { ol: 0.012 });
  mesh(rbox(0.85, 0.06, 1.45, 0.02), toon(0x24543a), 0, 0.75, 0, dump, { rz: 0.1, ol: 0.008 });
  for (const [x, z, s] of [[-1.7, 4.5, 0.32], [-1.4, 4.7, 0.25], [-1.85, 1.9, 0.28], [1.8, 6.0, 0.3], [1.95, 5.6, 0.22]]) mesh(sphg(s, 12, 10), toon(0x1c1d22), x, s * 0.8, z, g, { s: [1, 0.85, 1.05], ol: 0.008 });
  for (const [x, z, s, ry] of [[1.85, -5.5, 0.5, 0.2], [1.75, -4.95, 0.35, -0.3], [1.95, 1.8, 0.45, 0.4]]) mesh(rbox(s, s, s, 0.02), toon(0x8a6a48), x, s / 2, z, g, { ry, ol: 0.008 });
  // server-farm door (left) with blinking LED strip
  const door = group(g, -2.29, 0, -1.2, Math.PI / 2);
  mesh(boxg(1.0, 1.9, 0.08), toon(0x30343e), 0, 0.95, 0, door, { ol: 0.01 });
  env.doorLeds = [];
  for (let i = 0; i < 8; i++) env.doorLeds.push(mesh(boxg(0.06, 0.03, 0.02), glow(PAL.cxMint, 2).clone(), -0.35 + i * 0.1, 1.98, 0.05, door, { cast: false }));
  mesh(boxg(1.15, 0.06, 0.4), toon(0x3a3e48), 0, 2.08, 0.2, door, { ol: 0.006 });
  // neon signs
  env.neons = [
    neonSign(g, 'NOODLES', '#ff5fa2', 2.25, 3.0, 3.6, -Math.PI / 2, 0.42),
    neonSign(g, '</>', '#4de8ff', -2.25, 2.7, -4.6, Math.PI / 2, 0.5, FONT_MONO),
    neonSign(g, 'OPEN 24/7', '#ffb347', 2.25, 1.9, -6.5, -Math.PI / 2, 0.3),
    neonSign(g, 'SERVER FARM ▸', '#7dffd9', -2.25, 2.5, -1.2, Math.PI / 2, 0.24),
  ];
  // overhead lamp over the fight area
  const lampG = group(g, 0, 3.4, 0);
  mesh(cylg(0.01, 0.01, 2.3, 4), toon(0x222222), 0, 0.4, 0, lampG, { rz: Math.PI / 2, cast: false });
  mesh(cylg(0.12, 0.25, 0.18, 16, true), toon(0x30343e), 0, 0, 0, lampG, { ol: 0.006 });
  mesh(sphg(0.08), glow(0xffe0b0, 2.5), 0, -0.08, 0, lampG, { cast: false });
  const spot = new THREE.SpotLight(0xffd8a8, 6.5, 9, 0.8, 0.6, 1.4);
  spot.position.set(0, 3.3, 0); spot.target.position.set(0, 0, 0.2); spot.castShadow = true; spot.shadow.mapSize.set(1024, 1024); spot.shadow.bias = -0.0004; spot.shadow.normalBias = 0.02;
  g.add(spot); g.add(spot.target); env.spot = spot;
  // neon colour lights
  env.pl = [];
  for (const [c, x, y, z, i] of [[0xff5fa2, 1.6, 2.6, 3.6, 3], [0x4de8ff, -1.6, 2.4, -4.6, 3], [0xffb347, 1.6, 1.8, -6.5, 2], [0x7dffd9, -1.6, 2.2, -1.2, 2.2]]) {
    const pl = new THREE.PointLight(c, i, 6, 1.6); pl.position.set(x, y, z); g.add(pl); env.pl.push(pl);
  }
  env.hemi = new THREE.HemisphereLight(0x5060a8, 0x1c1826, 1.45); g.add(env.hemi);
  env.amb = new THREE.AmbientLight(0x6070a0, 0.5); g.add(env.amb);
  const rim = new THREE.DirectionalLight(0x7aa8ff, 0.45); rim.position.set(1, 4, -8); g.add(rim); g.add(rim.target); env.rim = rim;
  // the street glow at both ends + night skyline above
  mesh(new THREE.PlaneGeometry(4.6, 6), glow(0x3a4a7a, 0.9), 0, 3, -12.1, g, { cast: false });
  mesh(new THREE.PlaneGeometry(4.6, 6), glow(0x4a3a6a, 0.8), 0, 3, 12.1, g, { cast: false, ry: Math.PI });
  const sky = canvasTex(1024, 512, (c, w, h) => {
    const gr = c.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, '#05070f'); gr.addColorStop(0.7, '#141c38'); gr.addColorStop(1, '#2a2a50'); c.fillStyle = gr; c.fillRect(0, 0, w, h);
    const r = rng(77); c.fillStyle = 'rgba(255,255,255,0.7)'; for (let i = 0; i < 120; i++) c.fillRect(r() * w, r() * h * 0.5, 1.5, 1.5);
  });
  env.skyMat = new THREE.MeshBasicMaterial({ map: sky, side: THREE.BackSide, toneMapped: false, fog: false });
  mesh(new THREE.SphereGeometry(120, 24, 12), env.skyMat, 0, 0, 0, g, { cast: false, recv: false });
  const tw = windowTex('#141826', '#ffcf7a', '#202638', 5, 10, 91); tw.wrapS = tw.wrapT = THREE.RepeatWrapping; tw.repeat.set(2, 5); tw.needsUpdate = true;
  const twm = new THREE.MeshBasicMaterial({ map: tw, toneMapped: false, color: new THREE.Color(1.2, 1.2, 1.2) });
  const r = rng(12);
  for (let i = 0; i < 26; i++) { const a = (i / 26) * TAU, d = 16 + r() * 16, h = 10 + r() * 22, w = 3 + r() * 4; mesh(boxg(w, h, w), twm, Math.cos(a) * d, h / 2, Math.sin(a) * d, g, { cast: false, recv: false }); }
  // rooftop caps over the alley walls
  mesh(boxg(1.2, 0.2, 24), toon(0x22242c), -2.9, 7.05, 0, g); mesh(boxg(1.2, 0.2, 24), toon(0x22242c), 2.9, 7.05, 0, g);
  // umbrella prop for Claude
  const umb = group(g); env.umbrella = umb;
  const canopy = mesh(G('umbC', () => new THREE.ConeGeometry(0.42, 0.2, 10, 1, true)), toon(0xc8693a, { p: { side: THREE.DoubleSide } }), 0, 0.42, 0, umb, { ol: 0.006 });
  void canopy;
  mesh(cylg(0.008, 0.008, 0.5, 6), toon(0x2a1810), 0, 0.2, 0, umb);
  mesh(torg(0.03, 0.007, 6, 10, Math.PI), toon(0x2a1810), 0.03, -0.04, 0, umb, { rz: Math.PI });
  env.umbFolded = mesh(cylg(0.02, 0.05, 0.4, 8), toon(0xc8693a), 0, 0.3, 0, umb, { ol: 0.004 }); env.umbFolded.visible = false;
  env.umbCanopy = canopy;
  env.fog = new THREE.FogExp2(0x0b1022, 0.055);
  env.bg = new THREE.Color(0x070a14);
  env.updaters.push((t, dt, info) => {
    const flick = (k) => (hash1(Math.floor(t * 12) + k * 13) > 0.94 ? 0.35 : 1);
    env.neons.forEach((n, i) => n.userData.mat.color.setScalar(2.2 * flick(i)));
    env.doorLeds.forEach((l, i) => l.material.color.set(Math.floor(t * 4 + i * 0.7) % 3 ? PAL.cxMint : 0x103830).multiplyScalar(2));
    // lightning flashes
    let fl = 0; for (const lt of LIGHTNING) { const a = t - lt; if (a > 0 && a < 0.35) fl = Math.max(fl, (a < 0.06 ? 1 : 0.4) * (1 - a / 0.35)); }
    env.hemi.intensity = 1.45 + fl * 4; info.flash = Math.max(info.flash || 0, fl * 0.25);
  });
  return env;
}
const LIGHTNING = [122.2, 131.4, 144.2];
BUILDERS.push(buildAlley);
