// ───────────────────────── environment helpers ─────────────────────────
function plankTex(base, dark, w = 512, h = 512, rows = 8, seed = 1) {
  return canvasTex(w, h, (c) => {
    const r = rng(seed);
    c.fillStyle = base; c.fillRect(0, 0, w, h);
    const rh = h / rows;
    for (let i = 0; i < rows; i++) {
      let x = -r() * 200;
      while (x < w) {
        const pw = 140 + r() * 180;
        const l = 0.88 + r() * 0.2;
        c.fillStyle = shade(base, l); c.fillRect(x, i * rh, pw, rh);
        c.strokeStyle = 'rgba(0,0,0,0.05)'; c.lineWidth = 1;
        for (let k = 0; k < 4; k++) { c.beginPath(); const yy = i * rh + r() * rh; c.moveTo(x, yy); c.bezierCurveTo(x + pw * 0.3, yy + 3, x + pw * 0.6, yy - 3, x + pw, yy); c.stroke(); }
        c.fillStyle = dark; c.fillRect(x, i * rh, 2, rh);
        x += pw;
      }
      c.fillStyle = dark; c.fillRect(0, i * rh, w, 2);
    }
  });
}
function shade(hex, l) {
  const c = new THREE.Color(hex); c.multiplyScalar(l); return '#' + c.getHexString();
}
function noiseTex(base, amt = 0.06, w = 256, h = 256, seed = 3, repeat) {
  return canvasTex(w, h, (c) => {
    const r = rng(seed);
    c.fillStyle = base; c.fillRect(0, 0, w, h);
    for (let i = 0; i < 1400; i++) { c.fillStyle = r() > 0.5 ? `rgba(255,255,255,${amt * r()})` : `rgba(0,0,0,${amt * r()})`; c.fillRect(r() * w, r() * h, 2 + r() * 5, 2 + r() * 5); }
  }, repeat ? { repeat } : {});
}
function texMat(tex, opts = {}) { return new THREE.MeshToonMaterial(Object.assign({ map: tex, gradientMap: GRAD3 }, opts)); }

// sky/skyline canvas for window views
function skyTex(top, mid, bot, opts = {}) {
  return canvasTex(512, 320, (c, w, h) => {
    const g = c.createLinearGradient(0, 0, 0, h); g.addColorStop(0, top); g.addColorStop(0.6, mid); g.addColorStop(1, bot);
    c.fillStyle = g; c.fillRect(0, 0, w, h);
    if (opts.sun) { const sg = c.createRadialGradient(opts.sun[0], opts.sun[1], 4, opts.sun[0], opts.sun[1], 120); sg.addColorStop(0, 'rgba(255,250,230,1)'); sg.addColorStop(0.15, 'rgba(255,230,180,0.8)'); sg.addColorStop(1, 'rgba(255,200,150,0)'); c.fillStyle = sg; c.fillRect(0, 0, w, h); }
    const r = rng(opts.seed || 5);
    // clouds
    c.fillStyle = 'rgba(255,255,255,0.35)';
    for (let i = 0; i < 7; i++) { const x = r() * w, y = 30 + r() * 110; for (let k = 0; k < 5; k++) { c.beginPath(); c.ellipse(x + k * 22, y + Math.sin(k) * 6, 30 + r() * 20, 10 + r() * 6, 0, 0, TAU); c.fill(); } }
    // skyline
    for (let layer = 0; layer < 2; layer++) {
      c.fillStyle = layer ? opts.near || 'rgba(120,130,160,0.9)' : opts.far || 'rgba(170,180,205,0.7)';
      let x = 0;
      while (x < w) { const bw = 20 + r() * 40, bh = (layer ? 60 : 90) + r() * (layer ? 80 : 110); c.fillRect(x, h - bh, bw, bh); if (opts.windows) { c.save(); c.fillStyle = opts.windows; for (let wy = h - bh + 8; wy < h - 6; wy += 10) for (let wx = x + 4; wx < x + bw - 4; wx += 8) if (r() > 0.6) c.fillRect(wx, wy, 4, 5); c.restore(); } x += bw + 2; }
    }
  });
}

function addPlant(parent, x, y, z, s = 1, potColor = 0xe9e4da, leaf = 0x4f9a6a) {
  const g = group(parent, x, y, z); g.scale.setScalar(s);
  mesh(cylg(0.12, 0.09, 0.2, 18), toon(potColor), 0, 0.1, 0, g, { ol: 0.006 });
  mesh(cylg(0.11, 0.11, 0.02, 18), toon(0x5a3b28), 0, 0.195, 0, g);
  const lm = toon(leaf), lm2 = toon(shadeHex(leaf, 0.8));
  const r = rng(Math.floor(x * 100 + z * 7) + 9);
  for (let i = 0; i < 9; i++) {
    const a = r() * TAU, tilt = 0.4 + r() * 0.6, h = 0.25 + r() * 0.25;
    const stem = group(g, 0, 0.2, 0); stem.rotation.set(Math.cos(a) * tilt, 0, Math.sin(a) * tilt);
    mesh(cylg(0.006, 0.008, h, 5), lm2, 0, h / 2, 0, stem, { cast: false });
    const leafM = mesh(sphg(0.08, 10, 8), i % 2 ? lm : lm2, 0, h, 0, stem, { s: [0.9, 0.25, 1.6], ol: 0.004 });
    leafM.rotation.y = a;
  }
  return g;
}
function shadeHex(hex, l) { return new THREE.Color(hex).multiplyScalar(l).getHex(); }

function addBooks(parent, x, y, z, width, seed = 1, palette) {
  const r = rng(seed); const pal = palette || [0xc96a3d, 0x3b6e8f, 0xe0c27a, 0x6c8a5a, 0xa04a4a, 0xf1ece0, 0x2d3e50];
  let cx = x - width / 2;
  while (cx < x + width / 2 - 0.04) {
    const w = 0.03 + r() * 0.03, h = 0.16 + r() * 0.1, d = 0.13 + r() * 0.04;
    const b = mesh(boxg(w, h, d), toon(pal[Math.floor(r() * pal.length)]), cx + w / 2, y + h / 2, z, parent, { ol: 0.003 });
    if (r() > 0.85) { b.rotation.z = 0.25; b.position.y -= 0.005; }
    cx += w + 0.004;
  }
}
function addPoster(parent, x, y, z, w, h, draw, ry = 0) {
  const tex = canvasTex(Math.round(256 * w / h), 256, draw);
  const g = group(parent, x, y, z, ry);
  mesh(boxg(w + 0.04, h + 0.04, 0.02), toon(0x2a2a2e), 0, 0, 0, g, { ol: 0.004 });
  mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshToonMaterial({ map: tex, gradientMap: GRAD4 }), 0, 0, 0.011, g, { cast: false });
  return g;
}
function addRug(parent, x, z, rx, rz, draw) {
  const tex = canvasTex(512, 512, draw);
  const m = mesh(G(`rug${rx},${rz}`, () => new THREE.CylinderGeometry(1, 1, 0.012, 48)), new THREE.MeshToonMaterial({ map: tex, gradientMap: GRAD3 }), x, 0.006, z, parent, { cast: false });
  m.scale.set(rx, 1, rz);
  return m;
}
function addWindow(parent, x, y, z, w, h, frameMat, ry = 0) {
  const g = group(parent, x, y, z, ry);
  const t = 0.05;
  mesh(boxg(w + t * 2, t, 0.12), frameMat, 0, h / 2 + t / 2, 0, g, { ol: 0.005 });
  mesh(boxg(w + t * 2, t, 0.16), frameMat, 0, -h / 2 - t / 2, 0.02, g, { ol: 0.005 });
  mesh(boxg(t, h, 0.12), frameMat, -w / 2 - t / 2, 0, 0, g, { ol: 0.005 });
  mesh(boxg(t, h, 0.12), frameMat, w / 2 + t / 2, 0, 0, g, { ol: 0.005 });
  mesh(boxg(0.025, h, 0.05), frameMat, 0, 0, 0, g);
  mesh(boxg(w, 0.025, 0.05), frameMat, 0, 0, 0, g);
  return g;
}
// wall with rectangular holes: holes = [[cx, cy, w, h]] in wall-local x (along width) / y
function wallWithHoles(parent, W, H, D, mat, holes, x, y, z, ry = 0) {
  const g = group(parent, x, y, z, ry);
  // build by slicing along x
  const xs = [-W / 2, W / 2];
  holes.forEach((hl) => { xs.push(hl[0] - hl[2] / 2, hl[0] + hl[2] / 2); });
  xs.sort((a, b) => a - b);
  for (let i = 0; i < xs.length - 1; i++) {
    const x0 = xs[i], x1 = xs[i + 1], cx = (x0 + x1) / 2, w = x1 - x0;
    if (w < 1e-4) continue;
    const hole = holes.find((hl) => cx > hl[0] - hl[2] / 2 && cx < hl[0] + hl[2] / 2);
    if (!hole) { mesh(boxg(w, H, D), mat, cx, H / 2, 0, g); continue; }
    const yb = hole[1] - hole[3] / 2, yt = hole[1] + hole[3] / 2;
    if (yb > 0) mesh(boxg(w, yb, D), mat, cx, yb / 2, 0, g);
    if (yt < H) mesh(boxg(w, H - yt, D), mat, cx, (yt + H) / 2, 0, g);
  }
  return g;
}
function addSunBeam(parent, from, to, width, color = 0xfff0c8, opacity = 0.12) {
  // additive fake light shaft: a stretched plane between window and floor patch
  const dir = new THREE.Vector3().subVectors(to, from);
  const len = dir.length();
  const tex = canvasTex(64, 256, (c, w, h) => { const g = c.createLinearGradient(0, 0, 0, h); g.addColorStop(0, 'rgba(255,255,255,0.0)'); g.addColorStop(0.15, 'rgba(255,255,255,1)'); g.addColorStop(1, 'rgba(255,255,255,0.0)'); c.fillStyle = g; c.fillRect(0, 0, w, h); const g2 = c.createLinearGradient(0, 0, w, 0); g2.addColorStop(0, 'rgba(0,0,0,1)'); g2.addColorStop(0.2, 'rgba(0,0,0,0)'); g2.addColorStop(0.8, 'rgba(0,0,0,0)'); g2.addColorStop(1, 'rgba(0,0,0,1)'); c.globalCompositeOperation = 'destination-out'; c.fillStyle = g2; c.fillRect(0, 0, w, h); });
  const m = new THREE.MeshBasicMaterial({ map: tex, color, transparent: true, opacity, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide, toneMapped: false });
  const g = group(parent);
  for (let i = 0; i < 2; i++) {
    const p = mesh(new THREE.PlaneGeometry(width, len), m, 0, 0, 0, g, { cast: false, recv: false });
    p.position.copy(from).add(to).multiplyScalar(0.5);
    p.lookAt(to); p.rotateX(Math.PI / 2); p.rotateY(i * Math.PI / 2);
  }
  return g;
}
function dustMotes(parent, center, size, count = 120, color = 0xfff2d0) {
  const geo = new THREE.BufferGeometry();
  const r = rng(77); const pos = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) { pos[i * 3] = (r() - 0.5) * size.x; pos[i * 3 + 1] = r() * size.y; pos[i * 3 + 2] = (r() - 0.5) * size.z; }
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  const pts = new THREE.Points(geo, new THREE.PointsMaterial({ color, size: 0.012, transparent: true, opacity: 0.7, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false }));
  pts.position.copy(center); parent.add(pts);
  pts.userData.base = pos.slice();
  pts.userData.update = (t) => { const a = geo.attributes.position.array, b = pts.userData.base; for (let i = 0; i < count; i++) { a[i * 3] = b[i * 3] + Math.sin(t * 0.3 + i) * 0.05; a[i * 3 + 1] = (b[i * 3 + 1] + t * 0.02 * (0.5 + (i % 5) / 5)) % size.y; a[i * 3 + 2] = b[i * 3 + 2] + Math.cos(t * 0.25 + i * 1.3) * 0.05; } geo.attributes.position.needsUpdate = true; };
  return pts;
}

function makeSun(env, color, intensity, dir, size = 6, mapSize = 2048) {
  const s = new THREE.DirectionalLight(color, intensity);
  s.castShadow = true;
  s.shadow.mapSize.set(mapSize, mapSize);
  const cam = s.shadow.camera; cam.left = -size; cam.right = size; cam.top = size; cam.bottom = -size; cam.near = 0.5; cam.far = 60;
  s.shadow.bias = -0.0004; s.shadow.normalBias = 0.02;
  s.userData.dir = new THREE.Vector3(...dir).normalize();
  env.group.add(s); env.group.add(s.target);
  env.sun = s;
  return s;
}
function placeSun(env, focus) {
  const s = env.sun; if (!s) return;
  s.target.position.copy(focus);
  s.position.copy(focus).addScaledVector(s.userData.dir, 20);
}

const ENVS = {};
function makeEnv(name, origin) {
  const env = { name, group: new THREE.Group(), bg: new THREE.Color(0x000000), fog: null, updaters: [], screens: [] };
  env.group.position.copy(origin);
  env.group.visible = false;
  ENVS[name] = env;
  return env;
}
