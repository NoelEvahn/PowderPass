// ───────────────────────── city (morning commute + dawn establishing) ─────────────────────────
// Main street along x. Road z ∈ [-3, 3]; sidewalks to |z| = 5.5. OpenAI (x<0) and Anthropic (x>0) on the north side.
function windowTex(base, lit, unlit, cols = 4, rows = 6, seed = 1, frame) {
  return canvasTex(256, 256, (c, w, h) => {
    const r = rng(seed);
    c.fillStyle = base; c.fillRect(0, 0, w, h);
    const cw = w / cols, rh = h / rows;
    for (let y = 0; y < rows; y++) for (let x = 0; x < cols; x++) {
      c.fillStyle = r() < 0.35 ? lit : unlit;
      c.fillRect(x * cw + cw * 0.14, y * rh + rh * 0.18, cw * 0.72, rh * 0.62);
      if (frame) { c.fillStyle = frame; c.fillRect(x * cw + cw * 0.49, y * rh + rh * 0.18, cw * 0.03, rh * 0.62); }
    }
  });
}
// one bay × one floor of facade; tiled with texture repeat
const _facadeCache = new Map();
function facadeTex(wall, glass, seed, opts = {}) {
  const key = wall + glass + seed + JSON.stringify(opts);
  if (_facadeCache.has(key)) return _facadeCache.get(key).clone();
  const t = canvasTex(256, 256, (c, w, h) => {
    const r = rng(seed);
    c.fillStyle = wall; c.fillRect(0, 0, w, h);
    for (let i = 0; i < 300; i++) { c.fillStyle = r() > 0.5 ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)'; c.fillRect(r() * w, r() * h, 3, 3); }
    c.fillStyle = shade(wall, 0.85); c.fillRect(0, h - 18, w, 18); // floor band
    for (let k = 0; k < 2; k++) {
      const x = 28 + k * 120, y = 40, ww = 80, hh = 150;
      c.fillStyle = shade(wall, 0.7); c.fillRect(x - 6, y - 6, ww + 12, hh + 12);
      const lit = r() < (opts.lit ?? 0.25);
      const g = c.createLinearGradient(0, y, 0, y + hh);
      if (lit) { g.addColorStop(0, '#ffe7b0'); g.addColorStop(1, '#f2b968'); } else { g.addColorStop(0, glass); g.addColorStop(1, shade(glass, 0.65)); }
      c.fillStyle = g; c.fillRect(x, y, ww, hh);
      c.fillStyle = 'rgba(255,255,255,0.35)'; c.beginPath(); c.moveTo(x + 8, y + hh); c.lineTo(x + 30, y); c.lineTo(x + 44, y); c.lineTo(x + 22, y + hh); c.fill();
      c.fillStyle = shade(wall, 0.55); c.fillRect(x + ww / 2 - 2, y, 4, hh); c.fillRect(x, y + hh * 0.45, ww, 4);
      if (!lit && r() > 0.6) { c.fillStyle = 'rgba(240,235,225,0.75)'; c.fillRect(x, y, ww, hh * (0.2 + r() * 0.4)); }
      c.fillStyle = shade(wall, 1.12); c.fillRect(x - 10, y + hh + 6, ww + 20, 9); // sill
      if (opts.plants && r() > 0.6) { c.fillStyle = '#5a9a5a'; for (let p = 0; p < 6; p++) { c.beginPath(); c.arc(x + 10 + p * 12, y + hh + 2, 8, 0, TAU); c.fill(); } }
    }
  });
  _facadeCache.set(key, t);
  return t.clone();
}
function facadeMesh(parent, w, h, d, x, z, wall, glass, seed, opts = {}) {
  const tex = facadeTex(wall, glass, seed, opts); tex.wrapS = tex.wrapT = THREE.RepeatWrapping; tex.repeat.set(Math.max(1, Math.round(w / 2.4)), Math.max(1, Math.round(h / 2.2))); tex.needsUpdate = true;
  const g = group(parent, x, 0, z);
  mesh(boxg(w, h, d), texMat(tex), 0, h / 2, 0, g, { ol: 0.025 });
  mesh(boxg(w + 0.3, 0.35, d + 0.3), toon(shadeHex(new THREE.Color(wall).getHex(), 0.8)), 0, h + 0.17, 0, g, { ol: 0.01 }); // cornice
  const r = rng(seed + 5);
  if (r() > 0.4) { const wt = group(g, (r() - 0.5) * w * 0.5, h + 0.35, (r() - 0.5) * d * 0.4); mesh(cylg(0.55, 0.55, 1.1, 12), toon(0x8a6448), 0, 0.9, 0, wt, { ol: 0.01 }); mesh(G('wtcone', () => new THREE.ConeGeometry(0.62, 0.45, 12)), toon(0x5a4030), 0, 1.66, 0, wt); for (const [lx, lz] of [[-0.4, -0.4], [0.4, -0.4], [-0.4, 0.4], [0.4, 0.4]]) mesh(boxg(0.06, 0.4, 0.06), toon(0x3a3030), lx, 0.2, lz, wt); }
  for (let i = 0; i < 2; i++) if (r() > 0.3) mesh(rbox(0.9, 0.5, 0.7, 0.05), toon(0xb8bcc0), (r() - 0.5) * w * 0.7, h + 0.6, (r() - 0.5) * d * 0.6, g, { ol: 0.008 });
  if (r() > 0.6) mesh(cylg(0.03, 0.03, 2.5, 6), toon(0x4a4a4a), w * 0.3, h + 1.6, 0, g);
  return g;
}
function addTree(parent, x, z, s = 1, seed = 1, leaf = 0x6aa864) {
  const g = group(parent, x, 0, z); g.scale.setScalar(s);
  mesh(cylg(0.07, 0.1, 1.2, 8), toon(0x7a5236), 0, 0.6, 0, g, { ol: 0.008 });
  const r = rng(seed); const lm = toon(leaf), lm2 = toon(shadeHex(leaf, 0.82));
  for (let i = 0; i < 6; i++) mesh(sphg(0.42 + r() * 0.2, 14, 10), i % 2 ? lm : lm2, (r() - 0.5) * 0.7, 1.45 + r() * 0.5, (r() - 0.5) * 0.7, g, { ol: 0.012 });
  mesh(cylg(0.32, 0.32, 0.06, 16), toon(0x6b5440), 0, 0.03, 0, g, { cast: false });
  return g;
}
function addLamp(parent, x, z, ry = 0, lit = false) {
  const g = group(parent, x, 0, z, ry);
  const m = toon(0x2f363c);
  mesh(cylg(0.05, 0.07, 2.6, 10), m, 0, 1.3, 0, g, { ol: 0.008 });
  mesh(cylg(0.02, 0.02, 0.5, 6), m, 0, 2.58, 0.22, g, { rx: Math.PI / 2 });
  mesh(rbox(0.18, 0.08, 0.3, 0.03), m, 0, 2.55, 0.45, g, { ol: 0.005 });
  const bulb = mesh(boxg(0.12, 0.02, 0.22), lit ? glow(0xffd9a0, 2.2) : toon(0xfff3d8), 0, 2.505, 0.45, g, { cast: false });
  g.userData.bulb = bulb;
  return g;
}
function buildCar(color, seed) {
  const g = new THREE.Group();
  const body = toon(color), dark = toon(0x1c2126), glass = toon(0x9fc4d8, { emissive: 0x3c6a80, ei: 0.15 });
  mesh(rbox(1.7, 0.42, 0.85, 0.16, 4), body, 0, 0.36, 0, g, { ol: 0.012 });
  mesh(rbox(1.0, 0.34, 0.78, 0.14, 4), body, -0.1, 0.68, 0, g, { ol: 0.01 });
  mesh(rbox(0.96, 0.26, 0.8, 0.1, 3), glass, -0.1, 0.69, 0, g);
  for (const x of [-0.55, 0.55]) for (const z of [-0.42, 0.42]) {
    mesh(cylg(0.17, 0.17, 0.12, 16), dark, x, 0.17, z, g, { rx: Math.PI / 2, ol: 0.006 });
    mesh(cylg(0.08, 0.08, 0.13, 10), toon(0xc8ccd0), x, 0.17, z, g, { rx: Math.PI / 2, cast: false });
  }
  mesh(boxg(0.04, 0.08, 0.2), glow(0xfff6dd, 1.4), 0.86, 0.42, 0.28, g, { cast: false });
  mesh(boxg(0.04, 0.08, 0.2), glow(0xfff6dd, 1.4), 0.86, 0.42, -0.28, g, { cast: false });
  mesh(boxg(0.04, 0.07, 0.18), glow(0xff3a3a, 1.2), -0.86, 0.42, 0.3, g, { cast: false });
  mesh(boxg(0.04, 0.07, 0.18), glow(0xff3a3a, 1.2), -0.86, 0.42, -0.3, g, { cast: false });
  return g;
}
// cheap background bot pedestrians
function buildBgBot(color, face, seed) {
  const g = new THREE.Group(); const r = rng(seed);
  const m = toon(color), d = toon(0x22262a);
  const body = group(g); g.userData.body = body;
  mesh(capg(0.13, 0.16, 6), m, 0, 0.42, 0, body, { ol: 0.007 });
  const hs = 0.18 + r() * 0.05;
  mesh(r() > 0.5 ? rbox(hs * 2.1, hs * 1.6, hs * 1.7, 0.06) : sphg(hs, 16, 12), m, 0, 0.78, 0, body, { ol: 0.007 });
  mesh(rbox(hs * 1.5, hs * 0.8, 0.02, 0.03), d, 0, 0.78, hs * 0.86, body, { cast: false });
  mesh(boxg(hs * 0.25, hs * 0.25, 0.01), glow(face, 1.4), -hs * 0.3, 0.8, hs * 0.88, body, { cast: false });
  mesh(boxg(hs * 0.25, hs * 0.25, 0.01), glow(face, 1.4), hs * 0.3, 0.8, hs * 0.88, body, { cast: false });
  const legs = [];
  for (const sx of [-1, 1]) { const l = group(g, sx * 0.07, 0.26, 0); mesh(capg(0.04, 0.14, 4), d, 0, -0.12, 0, l); legs.push(l); }
  g.userData.legs = legs;
  return g;
}

function buildCity() {
  const env = makeEnv('city', new THREE.Vector3(200, 0, 0));
  const g = env.group;
  // ground: road + sidewalks
  const roadTex = canvasTex(512, 256, (c, w, h) => {
    c.fillStyle = '#4b5056'; c.fillRect(0, 0, w, h);
    const r = rng(9); for (let i = 0; i < 1500; i++) { c.fillStyle = `rgba(${r() > 0.5 ? 255 : 0},${r() > 0.5 ? 255 : 0},${r() > 0.5 ? 255 : 0},0.03)`; c.fillRect(r() * w, r() * h, 3, 3); }
    c.fillStyle = '#f2e9c8'; for (let x = 0; x < w; x += 64) c.fillRect(x, h / 2 - 3, 36, 6);
  }, { repeat: [10, 1] });
  mesh(boxg(120, 0.1, 6), texMat(roadTex), 0, -0.05, 0, g, { cast: false });
  const swTex = canvasTex(256, 256, (c, w, h) => { c.fillStyle = '#d4cdc0'; c.fillRect(0, 0, w, h); c.strokeStyle = '#b5ad9f'; c.lineWidth = 3; for (let x = 0; x <= w; x += 64) { c.beginPath(); c.moveTo(x, 0); c.lineTo(x, h); c.stroke(); } for (let y = 0; y <= h; y += 64) { c.beginPath(); c.moveTo(0, y); c.lineTo(w, y); c.stroke(); } }, { repeat: [60, 1.25] });
  for (const z of [-4.25, 4.25]) {
    mesh(boxg(120, 0.16, 2.5), texMat(swTex), 0, 0.08, z, g, { cast: false });
    mesh(boxg(120, 0.18, 0.12), toon(0xb8b0a2), 0, 0.07, z > 0 ? 3.0 : -3.0, g, { cast: false });
  }
  mesh(boxg(120, 0.1, 30), toon(0xcfc6b6), 0, 0.0, -20.5, g, { cast: false });
  mesh(boxg(120, 0.1, 30), toon(0xcfc6b6), 0, 0.0, 20.5, g, { cast: false });
  // crosswalk stripes at x = 0
  for (let i = 0; i < 7; i++) mesh(boxg(1.6, 0.012, 0.42), toon(0xf4f1ea), 0, 0.006, -2.55 + i * 0.85, g, { cast: false });
  mesh(boxg(0.18, 0.012, 2.9), toon(0xf4f1ea), -1.3, 0.006, 1.5, g, { cast: false });
  mesh(boxg(0.18, 0.012, 2.9), toon(0xf4f1ea), 1.3, 0.006, -1.5, g, { cast: false });
  // traffic + pedestrian signals
  env.walkTex = [];
  for (const [x, z, ry] of [[-1.1, 3.25, Math.PI], [1.1, -3.25, 0]]) {
    const sg = group(g, x, 0.16, z, ry);
    const m = toon(0x2f363c);
    mesh(cylg(0.045, 0.055, 2.2, 10), m, 0, 1.1, 0, sg, { ol: 0.006 });
    mesh(rbox(0.26, 0.6, 0.2, 0.04), m, 0, 2.3, 0.05, sg, { ol: 0.006 });
    const tl = []; [0xff4a3a, 0xffc23a, 0x4ae08a].forEach((c, i) => { tl.push(mesh(cylg(0.07, 0.07, 0.02, 14), new THREE.MeshBasicMaterial({ color: c, toneMapped: false }), 0, 2.48 - i * 0.18, 0.16, sg, { rx: Math.PI / 2, cast: false })); });
    const wt = canvasTex(64, 64); env.walkTex.push(wt);
    mesh(rbox(0.22, 0.22, 0.1, 0.03), m, 0.0, 1.55, -0.1, sg);
    mesh(new THREE.PlaneGeometry(0.18, 0.18), new THREE.MeshBasicMaterial({ map: wt, toneMapped: false }), 0, 1.55, -0.152, sg, { ry: Math.PI, cast: false });
    sg.userData.tl = tl; (env.signals = env.signals || []).push(sg);
  }
  // ── company buildings (north side) ──
  const glassTex = windowTex('#dfe6ea', '#bfe8f2', '#93b3c4', 6, 8, 3, '#e8eef0');
  // OpenAI: crisp white + dark glass, minimal
  const oa = group(g, -5.2, 0, -9.5); env.openai = oa;
  const oaGlass = glassTex.clone(); oaGlass.wrapS = oaGlass.wrapT = THREE.RepeatWrapping; oaGlass.repeat.set(2, 4); oaGlass.needsUpdate = true;
  mesh(boxg(8, 18, 7), texMat(oaGlass), 0, 9, 0, oa, { ol: 0.03 });
  mesh(boxg(8.4, 0.3, 7.4), toon(0xf6f7f7), 0, 18.1, 0, oa);
  for (let fy = 4.4; fy < 18; fy += 2.2) mesh(boxg(8.06, 0.16, 7.06), toon(0xf6f7f7), 0, fy, 0, oa, { cast: false });
  mesh(boxg(5.5, 1.6, 4.5), toon(0x2c3238), 0, 19.0, 0, oa, { ol: 0.015 }); mesh(boxg(5.7, 0.12, 4.7), toon(0x3fe6c0, { emissive: 0x3fe6c0, ei: 0.5 }), 0, 19.85, 0, oa);
  mesh(boxg(8.2, 2.6, 0.4), toon(0xf6f7f7), 0, 1.3, 3.55, oa, { ol: 0.015 });
  mesh(boxg(3.0, 1.8, 0.1), toon(0x1f2a30, { emissive: 0x24474a, ei: 0.3 }), 0, 0.92, 3.78, oa); // entrance glass
  mesh(boxg(0.06, 1.8, 0.12), toon(0xd8dde0), 0, 0.92, 3.8, oa);
  mesh(boxg(8.2, 0.12, 1.4), toon(0x2c3238), 0, 2.7, 4.1, oa, { ol: 0.01 }); // canopy
  const oaSign = canvasTex(512, 128, (c, w, h) => { c.fillStyle = '#ffffff'; c.fillRect(0, 0, w, h); c.fillStyle = '#111416'; c.font = `700 84px ${FONT_UI}`; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText('OpenAI', w / 2, h / 2 + 4); });
  mesh(new THREE.PlaneGeometry(3.6, 0.9), new THREE.MeshToonMaterial({ map: oaSign, gradientMap: GRAD4 }), 0, 3.5, 3.76, oa, { cast: false });
  addPlant(oa, -2.4, 0.16, 4.2, 2.2, 0x2c3238, 0x3d8f62); addPlant(oa, 2.4, 0.16, 4.2, 2.2, 0x2c3238, 0x3d8f62);
  // Anthropic: warm terracotta + cream, wood fins
  const an = group(g, 5.2, 0, -9.5); env.anthropic = an;
  const anWin = windowTex('#e7cfb0', '#ffe2b6', '#b99779', 5, 7, 8, '#cf9c70'); anWin.wrapS = anWin.wrapT = THREE.RepeatWrapping; anWin.repeat.set(2, 3.5); anWin.needsUpdate = true;
  mesh(boxg(8, 16, 7), texMat(anWin), 0, 8, 0, an, { ol: 0.03 });
  for (let i = 0; i < 9; i++) mesh(boxg(0.16, 13, 0.3), toon(0xb87a4e), -3.6 + i * 0.9, 9.5, 3.6, an, { cast: false });
  mesh(boxg(8.4, 0.3, 7.4), toon(0xc8693a), 0, 16.1, 0, an);
  for (let fy = 4.2; fy < 16; fy += 2.4) mesh(boxg(8.06, 0.14, 7.06), toon(0xd9b48a), 0, fy, 0, an, { cast: false });
  mesh(boxg(6, 1.2, 5), toon(0xf1e1c8), 0, 16.85, 0, an, { ol: 0.015 }); addPlant(an, -2, 17.45, 0, 3.5, 0xc8693a, 0x5c9a5a); addPlant(an, 2, 17.45, 0, 3.0, 0xc8693a, 0x4f9a6a);
  mesh(boxg(8.2, 2.6, 0.4), toon(0xf1e1c8), 0, 1.3, 3.55, an, { ol: 0.015 });
  mesh(boxg(3.0, 1.8, 0.1), toon(0x3a2418, { emissive: 0x5a3018, ei: 0.3 }), 0, 0.92, 3.78, an);
  mesh(boxg(0.06, 1.8, 0.12), toon(0xc99668), 0, 0.92, 3.8, an);
  mesh(boxg(8.2, 0.12, 1.4), toon(0xc8693a), 0, 2.7, 4.1, an, { ol: 0.01 });
  const anSign = canvasTex(768, 128, (c, w, h) => { c.fillStyle = '#f1e1c8'; c.fillRect(0, 0, w, h); c.fillStyle = '#191512'; c.font = `700 70px ${FONT_UI}`; c.textAlign = 'center'; c.textBaseline = 'middle'; c.letterSpacing = '14px'; c.fillText('ANTHROPIC', w / 2, h / 2 + 4); });
  mesh(new THREE.PlaneGeometry(4.6, 0.77), new THREE.MeshToonMaterial({ map: anSign, gradientMap: GRAD4 }), 0, 3.5, 3.76, an, { cast: false });
  addPlant(an, -2.4, 0.16, 4.2, 2.2, 0xc8693a, 0x5c9a5a); addPlant(an, 2.4, 0.16, 4.2, 2.2, 0xc8693a, 0x5c9a5a);
  // other north-side buildings
  const pal = ['#e9e1d3', '#d9e3e6', '#efd9c4', '#cfd8d4', '#e6d2d0'];
  const r = rng(31);
  for (const [x0, x1] of [[-40, -9.6], [9.6, 40]]) {
    let x = x0;
    while (x < x1) {
      const w = 4 + r() * 3, h = 8 + r() * 12;
      facadeMesh(g, w, h, 7, x + w / 2, -9.5, pal[Math.floor(r() * pal.length)], '#8fb0c4', Math.floor(r() * 9), { plants: r() > 0.5 });
      x += w + 0.1;
    }
  }
  // south-side shops
  const shops = [['CAFÉ', '#c8693a', '#f6e8d2'], ['BAKERY', '#e8b44a', '#fff4dc'], ['BOOKS', '#3b6e8f', '#e6eef2'], ['RAMEN', '#b8433a', '#fbe9e0'], ['HARDWARE', '#4f9a6a', '#e8f2ea'], ['FLOWERS', '#d9668a', '#fde8ef']];
  let sx = -34;
  for (let i = 0; i < 12; i++) {
    const [name, accent, wall] = shops[i % shops.length];
    const w = 5.2, h = 6 + (i % 3) * 2.2;
    const b = group(g, sx + w / 2, 0, 9.5);
    facadeMesh(b, w, h, 7, 0, 0, wall, '#9fb8c6', 20 + (i % 5), { plants: i % 2 === 0, lit: 0.15 });
    mesh(boxg(w, 2.0, 0.3), toon(new THREE.Color(wall).getHex()), 0, 1.0, -3.55, b, { ol: 0.01 });
    mesh(boxg(w * 0.7, 1.3, 0.1), toon(0x26323a, { emissive: 0x2a3a40, ei: 0.2 }), 0, 0.85, -3.72, b);
    // striped awning
    const aw = canvasTex(128, 32, (c) => { for (let k = 0; k < 8; k++) { c.fillStyle = k % 2 ? '#ffffff' : accent; c.fillRect(k * 16, 0, 16, 32); } });
    const awm = mesh(boxg(w * 0.9, 0.06, 1.1), new THREE.MeshToonMaterial({ map: aw, gradientMap: GRAD3 }), 0, 2.15, -4.1, b, { rx: -0.35, ol: 0.008 });
    void awm;
    const st = canvasTex(256, 64, (c, cw, ch) => { c.fillStyle = accent; c.fillRect(0, 0, cw, ch); c.fillStyle = '#fff'; c.font = `800 38px ${FONT_UI}`; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(name, cw / 2, ch / 2 + 2); });
    mesh(new THREE.PlaneGeometry(2.2, 0.55), new THREE.MeshToonMaterial({ map: st, gradientMap: GRAD4 }), 0, 2.7, -3.72, b, { ry: Math.PI, cast: false });
    b.rotation.y = Math.PI;
    b.position.z = 9.5;
    sx += w + 0.2;
  }
  // distant towers
  for (let i = 0; i < 40; i++) {
    const x = -70 + i * 3.6 + r() * 2, z = (r() > 0.5 ? -1 : 1) * (22 + r() * 18), h = 14 + r() * 30, w = 3 + r() * 4;
    const tw = facadeTex(pal[i % pal.length], '#98b4c8', 40 + (i % 6)); tw.wrapS = tw.wrapT = THREE.RepeatWrapping; tw.repeat.set(Math.max(1, Math.round(w / 2.4)), Math.round(h / 2.2)); tw.needsUpdate = true;
    mesh(boxg(w, h, w), texMat(tw), x, h / 2, z, g, { cast: false });
  }
  // trees, lamps, benches, hydrant along sidewalks
  for (let x = -30; x <= 30; x += 6) {
    if (Math.abs(x) < 3) continue;
    addTree(g, x + 1.5, 4.9, 1.0, x + 100);
    addLamp(g, x - 1.2, 3.35, Math.PI);
    if (Math.abs(x) > 8) addTree(g, x + 0.5, -4.9, 1.0, x + 300); else addLamp(g, x - 1.2, -3.35, 0);
  }
  for (const x of [-6.5, 7.5]) { const b = group(g, x, 0.16, 5.0, Math.PI); mesh(rbox(1.2, 0.06, 0.4, 0.02), toon(0x9a6440), 0, 0.42, 0, b, { ol: 0.006 }); mesh(rbox(1.2, 0.35, 0.06, 0.02), toon(0x9a6440), 0, 0.65, -0.2, b, { ol: 0.006 }); for (const lx of [-0.5, 0.5]) mesh(boxg(0.05, 0.42, 0.35), toon(0x2f363c), lx, 0.21, 0, b); }
  const hy = group(g, 2.2, 0.16, 3.4); mesh(cylg(0.09, 0.11, 0.45, 12), toon(0xd94a3a), 0, 0.22, 0, hy, { ol: 0.006 }); mesh(sphg(0.1, 12, 8), toon(0xd94a3a), 0, 0.45, 0, hy);
  // sky dome
  env.skyDay = skyTex('#7fb6e6', '#cfe4f2', '#f7e8d2', { seed: 4 });
  env.skyDawn = skyTex('#36457a', '#e89a7a', '#ffd29a', { seed: 4, sun: [300, 250] });
  env.skyMat = new THREE.MeshBasicMaterial({ map: env.skyDay, side: THREE.BackSide, toneMapped: false, fog: false });
  mesh(new THREE.SphereGeometry(180, 32, 16), env.skyMat, 0, 0, 0, g, { cast: false, recv: false });
  // cars
  env.cars = [];
  const carCols = [0xe8584a, 0x3b78c8, 0xf3c64b, 0xf4f1ea, 0x4fb38a, 0x2c3238, 0xe58a3a];
  for (let i = 0; i < 8; i++) {
    const car = buildCar(carCols[i % carCols.length], i);
    const lane = i % 2; // 0: +x direction (z = 1.5), 1: −x (z = −1.5)
    car.userData = { lane, x0: i * 15 + (lane ? 7 : 0), v: 6 + (i % 3) };
    g.add(car); env.cars.push(car);
  }
  // background pedestrians
  env.peds = [];
  const pc = [[0xe8b4a0, 0xffffff], [0x9cc0e0, 0xcff8ff], [0xd8d0f0, 0xffe0a0], [0xc0e0b0, 0xffffff], [0xf0d090, 0x80ffd0], [0xb0b8c0, 0xffd0a0]];
  for (let i = 0; i < 14; i++) {
    const [c, f] = pc[i % pc.length];
    const b = buildBgBot(c, f, i + 3);
    b.userData.side = i % 2 ? 1 : -1; b.userData.x0 = i * 9.3; b.userData.v = (0.7 + (i % 4) * 0.12) * (i % 3 === 0 ? -1 : 1); b.userData.lane = (i % 2 ? 4.3 : -4.3) + ((i % 4) - 1.5) * 0.35;
    g.add(b); env.peds.push(b);
  }
  // lights
  env.hemi = new THREE.HemisphereLight(0xd8ecff, 0xc8b49a, 1.1); g.add(env.hemi);
  env.amb = new THREE.AmbientLight(0xffffff, 0.3); g.add(env.amb);
  makeSun(env, 0xfff2dc, 2.2, [0.45, 0.8, 0.6], 9, 2048);
  env.fog = new THREE.Fog(0xcfe0ec, 30, 140);
  env.bg = new THREE.Color(0xcfe0ec);
  env.updaters.push((t) => updateCity(env, t));
  return env;
}

function walkSignal(t) { return t > 73.9 && t < 80.5 ? 'walk' : 'stop'; }
function updateCity(env, t) {
  const dawn = t < 8;
  env.skyMat.map = dawn ? env.skyDawn : env.skyDay;
  env.sun.color.set(dawn ? 0xffb27a : 0xfff2dc); env.sun.intensity = dawn ? 1.6 : 2.2;
  env.sun.userData.dir.set(dawn ? -0.8 : 0.45, dawn ? 0.35 : 0.8, dawn ? 0.3 : 0.6).normalize();
  env.hemi.color.set(dawn ? 0x8a8fc0 : 0xd8ecff); env.hemi.groundColor.set(dawn ? 0x8a6a5a : 0xc8b49a);
  env.fog.color.set(dawn ? 0xd6a894 : 0xcfe0ec); env.bg.set(env.fog.color);
  const L = 120;
  const sig = walkSignal(t);
  env.cars.forEach((car, i) => {
    const u = car.userData;
    let x = ((u.x0 + u.v * t) % L) - L / 2;
    if (u.lane === 1) x = -x;
    // stop for pedestrians while the walk signal is on
    if (t > 73.4 && t < 80.8) {
      const dir = u.lane === 0 ? 1 : -1;
      const stopX = -dir * 2.6;
      const xAt = (tt) => { let q = ((u.x0 + u.v * tt) % L) - L / 2; return u.lane === 1 ? -q : q; };
      const x0 = xAt(73.4);
      if ((stopX - x0) * dir > 0) { // still approaching at the start of the red
        const queue = env.cars.filter((c2, j) => j < i && c2.userData.lane === u.lane).length % 3;
        const limit = stopX - dir * queue * 2.4;
        if ((x - limit) * dir > 0 || Math.abs(x - x0) > 30) x = limit;
      }
    }
    car.position.set(x, 0, u.lane === 0 ? 1.5 : -1.5);
    car.rotation.y = u.lane === 0 ? 0 : Math.PI;
  });
  env.peds.forEach((b) => {
    const u = b.userData;
    const x = ((u.x0 + u.v * t) % 80 + 80) % 80 - 40;
    b.position.set(x, 0.16, u.lane);
    b.rotation.y = u.v > 0 ? Math.PI / 2 : -Math.PI / 2;
    const ph = t * Math.abs(u.v) * 6;
    b.userData.legs[0].rotation.x = Math.sin(ph) * 0.5; b.userData.legs[1].rotation.x = -Math.sin(ph) * 0.5;
    b.userData.body.position.y = Math.abs(Math.cos(ph)) * 0.03;
  });
  // signals
  const key = sig + (Math.floor(t * 2) % 2);
  if (env._sig !== key) {
    env._sig = key;
    for (const wt of env.walkTex) {
      const c = wt.userData.ctx; c.fillStyle = '#111'; c.fillRect(0, 0, 64, 64);
      if (sig === 'walk') { c.fillStyle = '#7dffb0'; c.beginPath(); c.arc(32, 14, 6, 0, TAU); c.fill(); c.fillRect(28, 22, 8, 20); c.save(); c.translate(32, 42); c.rotate(0.4); c.fillRect(-3, 0, 6, 16); c.restore(); c.save(); c.translate(32, 42); c.rotate(-0.4); c.fillRect(-3, 0, 6, 16); c.restore(); }
      else { c.fillStyle = '#ff6a3a'; c.fillRect(20, 22, 24, 26); for (let i = 0; i < 4; i++) c.fillRect(20 + i * 6, 12, 4, 12); }
      wt.needsUpdate = true;
    }
    for (const sg of env.signals) { const [red, yel, grn] = sg.userData.tl; red.material.color.set(sig === 'walk' ? 0xff4a3a : 0x401010); grn.material.color.set(sig === 'walk' ? 0x103018 : 0x4ae08a); yel.material.color.set(0x302810); }
  }
}
BUILDERS.push(buildCity);
