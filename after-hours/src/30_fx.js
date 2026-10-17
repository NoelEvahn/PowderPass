// ───────────────────────── effects: particles, streams, trails, holograms, rain ─────────────────────────
// All effects are analytic in time (seek-safe) except weapon trails, which reset on discontinuities.
const FX = { systems: {}, streams: {}, trails: {}, holos: {}, rain: null, used: new Set() };
const _fxv = new THREE.Vector3();
function W(env, p) { return new THREE.Vector3(p[0], p[1], p[2]).add(env.group.position); }

function softSpriteTex() {
  return canvasTex(64, 64, (c) => { const g = c.createRadialGradient(32, 32, 0, 32, 32, 32); g.addColorStop(0, 'rgba(255,255,255,1)'); g.addColorStop(0.35, 'rgba(255,255,255,0.6)'); g.addColorStop(1, 'rgba(255,255,255,0)'); c.fillStyle = g; c.fillRect(0, 0, 64, 64); });
}
function makePSys(max, additive) {
  const geo = new THREE.BufferGeometry();
  const pos = new Float32Array(max * 3), col = new Float32Array(max * 4), size = new Float32Array(max);
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3).setUsage(THREE.DynamicDrawUsage));
  geo.setAttribute('aColor', new THREE.BufferAttribute(col, 4).setUsage(THREE.DynamicDrawUsage));
  geo.setAttribute('aSize', new THREE.BufferAttribute(size, 1).setUsage(THREE.DynamicDrawUsage));
  const mat = new THREE.ShaderMaterial({
    uniforms: { uTex: { value: softSpriteTex() }, uScale: { value: 400 } },
    vertexShader: 'attribute vec4 aColor; attribute float aSize; uniform float uScale; varying vec4 vC; void main(){ vC = aColor; vec4 mv = modelViewMatrix * vec4(position,1.0); gl_PointSize = aSize * uScale / max(0.05, -mv.z); gl_Position = projectionMatrix * mv; }',
    fragmentShader: 'uniform sampler2D uTex; varying vec4 vC; void main(){ vec4 t = texture2D(uTex, gl_PointCoord); gl_FragColor = vec4(vC.rgb, vC.a * t.a); if (gl_FragColor.a < 0.003) discard; }',
    transparent: true, depthWrite: false, blending: additive ? THREE.AdditiveBlending : THREE.NormalBlending,
  });
  const pts = new THREE.Points(geo, mat); pts.frustumCulled = false; pts.renderOrder = additive ? 10 : 9;
  const sys = { pts, n: 0, max, pos, col, size, geo,
    add(x, y, z, s, r, g, b, a) { if (this.n >= max) return; const i = this.n++; pos[i * 3] = x; pos[i * 3 + 1] = y; pos[i * 3 + 2] = z; col[i * 4] = r; col[i * 4 + 1] = g; col[i * 4 + 2] = b; col[i * 4 + 3] = a; size[i] = s; },
    flush() { geo.setDrawRange(0, this.n); geo.attributes.position.needsUpdate = true; geo.attributes.aColor.needsUpdate = true; geo.attributes.aSize.needsUpdate = true; },
  };
  return sys;
}
let _fxScene;
function buildFX(scene) {
  _fxScene = scene;
  FX.systems.add = makePSys(6000, true); FX.systems.alpha = makePSys(3000, false);
  scene.add(FX.systems.add.pts); scene.add(FX.systems.alpha.pts);
  // rain
  const N = 3200;
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(N * 6), 3).setUsage(THREE.DynamicDrawUsage));
  const rm = new THREE.LineBasicMaterial({ color: new THREE.Color(0x9fc8ff).multiplyScalar(0.9), transparent: true, opacity: 0.45, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false });
  FX.rain = new THREE.LineSegments(geo, rm); FX.rain.frustumCulled = false; FX.rain.visible = false; scene.add(FX.rain);
  FX.rain.userData.N = N;
}
function hideFX() {
  FX.used.clear();
  FX.systems.add.n = 0; FX.systems.alpha.n = 0;
  if (FX.rain) FX.rain.visible = false;
}
function updateFX(t, dt, env, info) {
  for (const k in FX.streams) FX.streams[k].visible = FX.used.has('s' + k);
  for (const k in FX.trails) FX.trails[k].mesh.visible = FX.used.has('t' + k);
  for (const k in FX.holos) FX.holos[k].mesh.visible = FX.used.has('h' + k);
  FX.systems.add.pts.material.uniforms.uScale.value = renderer.domElement.height * 0.9;
  FX.systems.alpha.pts.material.uniforms.uScale.value = renderer.domElement.height * 0.9;
  FX.systems.add.flush(); FX.systems.alpha.flush();
}

// ── emitters ──
function fxSteam(env, p, t, t0, t1, opts = {}) {
  const sys = FX.systems.alpha, rate = opts.rate || 14, life = opts.life || 1.6, N = Math.ceil(rate * life);
  const base = W(env, p);
  for (let k = 0; k < N; k++) {
    const idx = Math.floor((t - t0) * rate) - k; const ts = t0 + idx / rate;
    if (ts < t0 || ts > t1) continue;
    const age = t - ts; if (age < 0 || age > life) continue;
    const u = age / life, h = hash1(idx * 7.3 + (opts.seed || 0));
    const x = base.x + (h - 0.5) * 0.05 + Math.sin(age * 3 + h * 6) * 0.03 * u + (opts.dx || 0) * age;
    const y = base.y + age * (opts.rise || 0.25);
    const z = base.z + (hash1(idx * 3.1) - 0.5) * 0.05;
    sys.add(x, y, z, (opts.size || 0.05) * (0.6 + u * 1.8), 1, 1, 1, (opts.alpha || 0.35) * Math.sin(u * Math.PI));
  }
}
// burst of sparks at an impact; gravity, deterministic seed
function fxSparks(env, p, t, tImpact, opts = {}) {
  const age = t - tImpact; const life = opts.life || 0.7;
  if (age < 0 || age > life) return;
  const sys = FX.systems.add, base = W(env, p), n = opts.n || 40;
  const col = new THREE.Color(opts.color || 0xffe2a0);
  const col2 = new THREE.Color(opts.color2 || opts.color || 0xffffff);
  for (let i = 0; i < n; i++) {
    const h1 = hash1(i * 12.9 + tImpact * 7), h2 = hash1(i * 78.2 + tImpact * 3), h3 = hash1(i * 37.7 + tImpact);
    const sp = (opts.speed || 3) * (0.3 + h1 * 0.9);
    const th = h2 * TAU, ph = (h3 - 0.5) * Math.PI * 0.9;
    const vx = Math.cos(th) * Math.cos(ph) * sp, vy = Math.sin(ph) * sp + (opts.up || 1), vz = Math.sin(th) * Math.cos(ph) * sp;
    const life_i = life * (0.4 + 0.6 * h1);
    if (age > life_i) continue;
    const u = age / life_i;
    const c = i % 3 === 0 ? col2 : col;
    // streak: two points along velocity
    for (let s = 0; s < 3; s++) {
      const a2 = Math.max(0, age - s * 0.012);
      sys.add(base.x + vx * a2, base.y + vy * a2 - 4.9 * a2 * a2, base.z + vz * a2, (opts.size || 0.035) * (1 - u) * (1 - s * 0.25), c.r * 2, c.g * 2, c.b * 2, (1 - u) * (1 - s * 0.3));
    }
  }
  // flash core
  if (age < 0.12) sys.add(base.x, base.y, base.z, (opts.flash || 0.5) * (1 - age / 0.12), col.r * 3, col.g * 3, col.b * 3, 1 - age / 0.12);
}
function fxSplash(env, p, t, tHit, opts = {}) {
  const age = t - tHit; if (age < 0 || age > 0.6) return;
  const sys = FX.systems.alpha, base = W(env, p), n = opts.n || 26;
  for (let i = 0; i < n; i++) {
    const h1 = hash1(i * 9.1 + tHit), h2 = hash1(i * 4.7 + tHit * 2);
    const th = h1 * TAU, sp = (opts.speed || 1.4) * (0.5 + h2);
    const vx = Math.cos(th) * sp * 0.6, vz = Math.sin(th) * sp * 0.6, vy = sp * 1.2;
    const y = base.y + vy * age - 6 * age * age; if (y < base.y - 0.01) continue;
    sys.add(base.x + vx * age, y, base.z + vz * age, 0.025, 0.75, 0.85, 1.0, 0.7 * (1 - age / 0.6));
  }
}
function fxGlow(env, p, size, color, a = 1) { const b = W(env, p), c = new THREE.Color(color); FX.systems.add.add(b.x, b.y, b.z, size, c.r, c.g, c.b, a); }
// rotating code-glyph sparkles around a point (weapon summon / charge)
function fxSwirl(env, p, t, amt, color, radius = 0.35, n = 24) {
  if (amt <= 0) return;
  const b = W(env, p), c = new THREE.Color(color);
  for (let i = 0; i < n; i++) {
    const a = t * (2 + (i % 3)) + i * 2.4, r = radius * (0.6 + 0.4 * hash1(i)), y = (hash1(i * 3.3) - 0.5) * 0.6 + Math.sin(t * 2 + i) * 0.05;
    FX.systems.add.add(b.x + Math.cos(a) * r, b.y + y, b.z + Math.sin(a) * r, 0.03 * amt, c.r * 2, c.g * 2, c.b * 2, amt * 0.9);
  }
}
// a liquid stream / beam between two env-local points
function fxStream(key, env, a, b, radius, color, opts = {}) {
  let m = FX.streams[key];
  if (!m) { m = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 1, 8, 1, true), opts.glow ? glow(color, opts.glow, { transparent: true, opacity: 0.85 }) : toon(color, { unique: true, p: { transparent: true, opacity: 0.9 } })); m.castShadow = false; _fxScene.add(m); FX.streams[key] = m; }
  FX.used.add('s' + key);
  const A1 = W(env, a), B1 = W(env, b);
  m.position.copy(A1).add(B1).multiplyScalar(0.5);
  const dir = B1.clone().sub(A1); const len = dir.length();
  m.scale.set(radius, len, radius);
  m.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.normalize());
}
// weapon trail ribbon from a history of (base, tip) pairs
function fxTrail(key, env, base, tip, t, color, len = 14, discontinuity = false) {
  let tr = FX.trails[key];
  if (!tr) {
    const geo = new THREE.BufferGeometry();
    const N = 24; geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(N * 2 * 3), 3).setUsage(THREE.DynamicDrawUsage));
    geo.setAttribute('alpha', new THREE.BufferAttribute(new Float32Array(N * 2), 1).setUsage(THREE.DynamicDrawUsage));
    const idx = []; for (let i = 0; i < N - 1; i++) { const a = i * 2; idx.push(a, a + 1, a + 2, a + 1, a + 3, a + 2); }
    geo.setIndex(idx);
    const mat = new THREE.ShaderMaterial({ uniforms: { uC: { value: new THREE.Color(color).multiplyScalar(2) } }, vertexShader: 'attribute float alpha; varying float vA; void main(){ vA = alpha; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }', fragmentShader: 'uniform vec3 uC; varying float vA; void main(){ gl_FragColor = vec4(uC, vA); }', transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide });
    const mesh = new THREE.Mesh(geo, mat); mesh.frustumCulled = false; _fxScene.add(mesh);
    tr = FX.trails[key] = { mesh, hist: [], N, lastT: -1 };
  }
  FX.used.add('t' + key);
  if (discontinuity || t < tr.lastT || t - tr.lastT > 0.2) tr.hist = [];
  tr.lastT = t;
  tr.hist.unshift([W(env, base), W(env, tip)]);
  if (tr.hist.length > Math.min(len, tr.N)) tr.hist.length = Math.min(len, tr.N);
  const pos = tr.mesh.geometry.attributes.position.array, al = tr.mesh.geometry.attributes.alpha.array;
  for (let i = 0; i < tr.N; i++) {
    const h = tr.hist[Math.min(i, tr.hist.length - 1)];
    pos.set([h[0].x, h[0].y, h[0].z], i * 6); pos.set([h[1].x, h[1].y, h[1].z], i * 6 + 3);
    const a = i < tr.hist.length ? (1 - i / tr.hist.length) * 0.6 : 0;
    al[i * 2] = a * 0.2; al[i * 2 + 1] = a;
  }
  tr.mesh.geometry.attributes.position.needsUpdate = true; tr.mesh.geometry.attributes.alpha.needsUpdate = true;
}
// holographic panel (canvas) — draw(ctx, w, h) is called when key changes
function fxHolo(key, env, p, w, h, draw, drawKey, opts = {}) {
  let ho = FX.holos[key];
  if (!ho) {
    const tex = canvasTex(Math.round(512 * w / h), 512);
    const mat = new THREE.MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide, toneMapped: false });
    const mesh = new THREE.Mesh(new THREE.PlaneGeometry(w, h), mat); mesh.renderOrder = 11; _fxScene.add(mesh);
    ho = FX.holos[key] = { mesh, tex, k: null };
  }
  FX.used.add('h' + key);
  if (ho.k !== drawKey) { ho.k = drawKey; const c = ho.tex.userData.ctx, cw = ho.tex.userData.canvas.width, ch = ho.tex.userData.canvas.height; c.clearRect(0, 0, cw, ch); draw(c, cw, ch); ho.tex.needsUpdate = true; }
  ho.mesh.position.copy(W(env, p));
  ho.mesh.rotation.set(0, opts.yaw || 0, 0);
  if (opts.lookAt) ho.mesh.lookAt(opts.lookAt);
  const s = opts.scale ?? 1; ho.mesh.scale.set(s, s, s);
  ho.mesh.material.opacity = opts.opacity ?? 1;
  return ho;
}
function holoFrame(c, w, h, color, title) {
  c.fillStyle = 'rgba(10,30,40,0.55)'; rrect(c, 6, 6, w - 12, h - 12, 26); c.fill();
  c.strokeStyle = color; c.lineWidth = 5; c.stroke();
  c.fillStyle = color; c.globalAlpha = 0.08; for (let y = 10; y < h; y += 8) c.fillRect(8, y, w - 16, 2); c.globalAlpha = 1;
  if (title) { c.font = `800 34px ${FONT_UI}`; c.fillStyle = color; c.fillText(title, 34, 62); }
}
// GPT notification card
function drawGptNote(lines, accent = '#8ff0c9') {
  return (c, w, h) => {
    holoFrame(c, w, h, accent);
    c.fillStyle = accent; c.beginPath(); c.arc(64, 70, 28, 0, TAU); c.fill();
    c.fillStyle = '#0d1a17'; c.beginPath(); c.arc(54, 66, 5, 0, TAU); c.arc(74, 66, 5, 0, TAU); c.fill();
    c.strokeStyle = '#0d1a17'; c.lineWidth = 4; c.beginPath(); c.arc(64, 72, 10, 0.3, Math.PI - 0.3); c.stroke();
    c.fillStyle = accent; c.font = `800 32px ${FONT_UI}`; c.fillText('ChatGPT', 110, 82);
    c.fillStyle = '#ffffff'; c.font = `700 38px ${FONT_UI}`;
    lines.forEach((l, i) => c.fillText(l, 40, 160 + i * 52));
  };
}

// rain volume around a centre (world), analytic
function fxRain(center, t, opts = {}) {
  const R = FX.rain; R.visible = true;
  const a = R.geometry.attributes.position.array, N = R.userData.N;
  const H = opts.h || 7, sx = opts.sx || 9, sz = opts.sz || 9, sp = opts.speed || 9, len = opts.len || 0.28, slant = opts.slant ?? 0.08;
  const floorY = opts.floor ?? 0;
  for (let i = 0; i < N; i++) {
    const hx = hash1(i * 1.37), hz = hash1(i * 2.71), hy = hash1(i * 3.97);
    const x = center.x + (hx - 0.5) * sx, z = center.z + (hz - 0.5) * sz;
    let y = floorY + H - ((t * sp + hy * H) % H);
    a[i * 6] = x; a[i * 6 + 1] = y; a[i * 6 + 2] = z;
    a[i * 6 + 3] = x - slant * len; a[i * 6 + 4] = y + len; a[i * 6 + 5] = z;
  }
  R.geometry.attributes.position.needsUpdate = true;
  // splash sprites on the ground
  const sys = FX.systems.add;
  const M = opts.splashes || 220;
  for (let i = 0; i < M; i++) {
    const cyc = 0.35, ph = hash1(i * 5.1) * cyc, k = Math.floor((t + ph) / cyc), age = (t + ph) - k * cyc;
    const hx = hash1(i * 13.1 + k * 0.7), hz = hash1(i * 7.7 + k * 1.3);
    const x = center.x + (hx - 0.5) * sx, z = center.z + (hz - 0.5) * sz;
    const u = age / cyc;
    sys.add(x, floorY + 0.01, z, 0.02 + u * 0.03, 0.5, 0.65, 0.9, (1 - u) * 0.5);
  }
}
