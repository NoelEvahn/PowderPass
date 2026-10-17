// ───────────────────────── core utilities ─────────────────────────
const TAU = Math.PI * 2;
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (t) => t * t * (3 - 2 * t);
const seg = (t, a, b) => clamp((t - a) / (b - a));
const sseg = (t, a, b) => smooth(seg(t, a, b));
const EASE = {
  lin: (t) => t,
  io: smooth,
  in: (t) => t * t,
  out: (t) => 1 - (1 - t) * (1 - t),
  in3: (t) => t * t * t,
  out3: (t) => 1 - Math.pow(1 - t, 3),
  io3: (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2),
  back: (t) => { const c = 1.7; return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2); },
  snap: (t) => 1 - Math.pow(1 - t, 5),
  step: (t) => (t < 1 ? 0 : 1),
  // walk: gentle accel / decel at the ends, linear in the middle
  walk: (t) => {
    const a = 0.18;
    if (t < a) return (t * t) / (2 * a * (1 - a));
    if (t > 1 - a) return 1 - ((1 - t) * (1 - t)) / (2 * a * (1 - a));
    return (t - a / 2) / (1 - a);
  },
};
function hash1(n) { const s = Math.sin(n * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); }
function rng(seed) { let s = seed >>> 0 || 1; return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }
function noise1(x) { const i = Math.floor(x), f = x - i; return lerp(hash1(i), hash1(i + 1), smooth(f)) * 2 - 1; }

// Keyframe track: keys = [[time, {props}, easeName], ...]; values fill forward.
function K(keys, base = {}) {
  const filled = [];
  let cur = Object.assign({}, base);
  for (const k of keys) {
    cur = Object.assign({}, cur, k[1]);
    filled.push({ t: k[0], v: cur, e: EASE[k[2] || 'io'] });
  }
  const names = Object.keys(cur);
  const out = {};
  const fn = (t) => {
    const n = filled.length;
    if (t <= filled[0].t) { Object.assign(out, filled[0].v); return out; }
    if (t >= filled[n - 1].t) { Object.assign(out, filled[n - 1].v); return out; }
    let i = 0;
    while (i < n - 1 && filled[i + 1].t <= t) i++;
    const a = filled[i], b = filled[i + 1];
    const u = b.e((t - a.t) / (b.t - a.t));
    for (const k of names) {
      const va = a.v[k], vb = b.v[k];
      out[k] = typeof va === 'number' && typeof vb === 'number' ? va + (vb - va) * u : (u < 1 ? va : vb);
    }
    return out;
  };
  fn.keys = filled;
  return fn;
}

// ───────────────────────── materials ─────────────────────────
function makeGradient(stops) {
  const data = new Uint8Array(stops.length * 4);
  stops.forEach((v, i) => { data[i * 4] = data[i * 4 + 1] = data[i * 4 + 2] = v; data[i * 4 + 3] = 255; });
  const tex = new THREE.DataTexture(data, stops.length, 1, THREE.RGBAFormat);
  tex.minFilter = tex.magFilter = THREE.NearestFilter;
  tex.generateMipmaps = false;
  tex.needsUpdate = true;
  return tex;
}
const GRAD3 = makeGradient([90, 175, 255]);
const GRAD4 = makeGradient([70, 140, 205, 255]);
const _matCache = new Map();
function toon(color, opts = {}) {
  const key = color + JSON.stringify(opts);
  if (!opts.unique && _matCache.has(key)) return _matCache.get(key);
  const m = new THREE.MeshToonMaterial(Object.assign({ color, gradientMap: opts.soft ? GRAD4 : GRAD3 }, opts.p || {}));
  if (opts.emissive) { m.emissive = new THREE.Color(opts.emissive); m.emissiveIntensity = opts.ei ?? 1; }
  if (!opts.unique) _matCache.set(key, m);
  return m;
}
function glow(color, intensity = 1.6, opts = {}) {
  const m = new THREE.MeshBasicMaterial(Object.assign({ color: new THREE.Color(color).multiplyScalar(intensity), toneMapped: false }, opts));
  return m;
}
// inverted-hull outline material (pushes vertices along normals)
const _outlineCache = new Map();
function outlineMat(width = 0.012, color = 0x14121c) {
  const key = width + ':' + color;
  if (_outlineCache.has(key)) return _outlineCache.get(key);
  const m = new THREE.MeshBasicMaterial({ color, side: THREE.BackSide });
  m.onBeforeCompile = (sh) => {
    sh.vertexShader = sh.vertexShader.replace('#include <begin_vertex>', `#include <begin_vertex>\n transformed += normalize(normal) * ${width.toFixed(4)};`);
  };
  m.customProgramCacheKey = () => 'ol' + width;
  _outlineCache.set(key, m);
  return m;
}
function addOutline(mesh, width = 0.012, color) {
  const o = new THREE.Mesh(mesh.geometry, outlineMat(width, color));
  o.castShadow = false; o.receiveShadow = false;
  o.userData.isOutline = true;
  mesh.add(o);
  return mesh;
}

// ───────────────────────── geometry helpers ─────────────────────────
const _geoCache = new Map();
function G(key, make) { if (!_geoCache.has(key)) _geoCache.set(key, make()); return _geoCache.get(key); }
function rbox(w, h, d, r = 0.02, s = 3) { return G(`rb${w},${h},${d},${r},${s}`, () => new RoundedBoxGeometry(w, h, d, s, Math.min(r, w / 2 - 1e-4, h / 2 - 1e-4, d / 2 - 1e-4))); }
function boxg(w, h, d) { return G(`b${w},${h},${d}`, () => new THREE.BoxGeometry(w, h, d)); }
function cylg(rt, rb, h, s = 16) { return G(`c${rt},${rb},${h},${s}`, () => new THREE.CylinderGeometry(rt, rb, h, s)); }
function sphg(r, ws = 20, hs = 14) { return G(`s${r},${ws},${hs}`, () => new THREE.SphereGeometry(r, ws, hs)); }
function capg(r, l, s = 8) { return G(`cap${r},${l},${s}`, () => new THREE.CapsuleGeometry(r, l, s, 16)); }
function torg(r, t, rs = 10, ts = 28, arc = TAU) { return G(`t${r},${t},${rs},${ts},${arc}`, () => new THREE.TorusGeometry(r, t, rs, ts, arc)); }

function mesh(geo, mat, x = 0, y = 0, z = 0, parent, opts = {}) {
  const m = new THREE.Mesh(geo, mat);
  m.position.set(x, y, z);
  if (opts.rx) m.rotation.x = opts.rx;
  if (opts.ry) m.rotation.y = opts.ry;
  if (opts.rz) m.rotation.z = opts.rz;
  if (opts.s) { if (typeof opts.s === 'number') m.scale.setScalar(opts.s); else m.scale.set(...opts.s); }
  m.castShadow = opts.cast !== false;
  m.receiveShadow = opts.recv !== false;
  if (opts.ol) addOutline(m, opts.ol, opts.olc);
  if (parent) parent.add(m);
  return m;
}
function group(parent, x = 0, y = 0, z = 0, ry = 0) {
  const g = new THREE.Group(); g.position.set(x, y, z); g.rotation.y = ry; if (parent) parent.add(g); return g;
}

// ───────────────────────── canvas textures ─────────────────────────
function canvasTex(w, h, draw, opts = {}) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  const ctx = c.getContext('2d');
  if (draw) draw(ctx, w, h);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 4;
  if (opts.repeat) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(...opts.repeat); }
  t.userData = { canvas: c, ctx };
  return t;
}
function rrect(ctx, x, y, w, h, r) {
  r = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath();
}
const FONT_UI = '"M PLUS Rounded 1c", "Nunito", system-ui, sans-serif';
const FONT_MONO = '"JetBrains Mono", ui-monospace, Menlo, Consolas, monospace';

// palette
const PAL = {
  cxWhite: 0xf3f6f7, cxGraphite: 0x2c3238, cxDark: 0x1a1e22, cxMint: 0x3fe6c0, cxCyan: 0x34c8ec,
  ccCream: 0xf6e8d2, ccCopper: 0xc8693a, ccAmber: 0xffa65a, ccBrown: 0x5a3626, ccDeep: 0x2a1810,
  gpt: 0xf5f5f2, gptDark: 0x111416, gptGreen: 0x10a37f,
};
