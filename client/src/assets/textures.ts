import * as THREE from 'three';

/**
 * Procedural PBR texture generation (original assets, no external files). Each generator produces a
 * tileable albedo, height (→ normal map) and roughness field. Results are cached per quality level.
 */
export interface TexSet { map: THREE.Texture; normalMap: THREE.Texture; roughnessMap: THREE.Texture; meters: number }

let SIZE = 512;
let ANISO = 4;
const cache = new Map<string, TexSet>();
export function setTextureQuality(size: number, aniso: number) {
  if (size !== SIZE) cache.clear();
  SIZE = size; ANISO = aniso;
}

// ---------------------------------------------------------------- noise helpers

function hash(x: number, y: number, s: number) {
  let h = (x * 374761393 + y * 668265263 + s * 1442695041) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}
const fade = (t: number) => t * t * (3 - 2 * t);
/** Tileable value noise with integer period p. */
function vnoise(x: number, y: number, p: number, s: number) {
  const xi = Math.floor(x), yi = Math.floor(y);
  const xf = x - xi, yf = y - yi;
  const x0 = ((xi % p) + p) % p, y0 = ((yi % p) + p) % p, x1 = (x0 + 1) % p, y1 = (y0 + 1) % p;
  const a = hash(x0, y0, s), b = hash(x1, y0, s), c = hash(x0, y1, s), d = hash(x1, y1, s);
  const u = fade(xf), v = fade(yf);
  return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
}
function fbm(x: number, y: number, p: number, oct: number, s: number) {
  let a = 0, amp = 0.5, f = 1, n = 0;
  for (let o = 0; o < oct; o++) { a += amp * vnoise(x * f, y * f, p * f, s + o * 31); n += amp; amp *= 0.5; f *= 2; }
  return a / n;
}

interface Field { w: number; h: number; col: Float32Array; ht: Float32Array; ro: Float32Array }
function field(): Field {
  const n = SIZE * SIZE;
  return { w: SIZE, h: SIZE, col: new Float32Array(n * 3), ht: new Float32Array(n), ro: new Float32Array(n) };
}
const mix = (a: number, b: number, t: number) => a + (b - a) * t;
const clamp01 = (v: number) => (v < 0 ? 0 : v > 1 ? 1 : v);
function hex(c: number): [number, number, number] { return [((c >> 16) & 255) / 255, ((c >> 8) & 255) / 255, (c & 255) / 255]; }

function toTextures(f: Field, meters: number, normalStrength: number): TexSet {
  const { w, h } = f;
  const cImg = new Uint8ClampedArray(new ArrayBuffer(w * h * 4)), nImg = new Uint8ClampedArray(new ArrayBuffer(w * h * 4)), rImg = new Uint8ClampedArray(new ArrayBuffer(w * h * 4));
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = y * w + x;
    cImg[i * 4] = f.col[i * 3] * 255; cImg[i * 4 + 1] = f.col[i * 3 + 1] * 255; cImg[i * 4 + 2] = f.col[i * 3 + 2] * 255; cImg[i * 4 + 3] = 255;
    const l = f.ht[y * w + ((x - 1 + w) % w)], r = f.ht[y * w + ((x + 1) % w)];
    const u = f.ht[((y - 1 + h) % h) * w + x], d = f.ht[((y + 1) % h) * w + x];
    const gx = (l - r) * normalStrength, gy = (d - u) * normalStrength;
    const L = Math.hypot(gx, gy, 1);
    nImg[i * 4] = (gx / L * 0.5 + 0.5) * 255; nImg[i * 4 + 1] = (gy / L * 0.5 + 0.5) * 255; nImg[i * 4 + 2] = (1 / L * 0.5 + 0.5) * 255; nImg[i * 4 + 3] = 255;
    const ro = clamp01(f.ro[i]) * 255;
    rImg[i * 4] = ro; rImg[i * 4 + 1] = ro; rImg[i * 4 + 2] = ro; rImg[i * 4 + 3] = 255;
  }
  const mk = (data: Uint8ClampedArray<ArrayBuffer>, srgb: boolean) => {
    const t = new THREE.DataTexture(data, w, h, THREE.RGBAFormat);
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.magFilter = THREE.LinearFilter;
    t.minFilter = THREE.LinearMipmapLinearFilter;
    t.generateMipmaps = true;
    t.anisotropy = ANISO;
    t.colorSpace = srgb ? THREE.SRGBColorSpace : THREE.NoColorSpace;
    t.repeat.set(1 / meters, 1 / meters);
    t.needsUpdate = true;
    return t;
  };
  return { map: mk(cImg, true), normalMap: mk(nImg, false), roughnessMap: mk(rImg, false), meters };
}

function each(f: Field, fn: (u: number, v: number, i: number) => void) {
  for (let y = 0; y < f.h; y++) for (let x = 0; x < f.w; x++) fn(x / f.w, y / f.h, y * f.w + x);
}
function setCol(f: Field, i: number, r: number, g: number, b: number) { f.col[i * 3] = clamp01(r); f.col[i * 3 + 1] = clamp01(g); f.col[i * 3 + 2] = clamp01(b); }

// ---------------------------------------------------------------- generators

/** Grime/age overlay shared by most surfaces. */
function grime(u: number, v: number, s: number) {
  const n = fbm(u * 6, v * 6, 6, 5, s);
  const streak = fbm(u * 24, v * 2, 24, 3, s + 9);
  return clamp01((n - 0.45) * 2.2) * 0.6 + clamp01(streak - 0.6) * 0.5;
}

function wallpaper(kind: number): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    let m = 0;
    const gx = u * 4, gy = v * 4;
    const cx = gx - Math.floor(gx) - 0.5, cy = gy - Math.floor(gy) - 0.5;
    switch (kind) {
      case 0: { // damask medallions
        const r = Math.hypot(cx * 1.3, cy);
        const a = Math.atan2(cy, cx);
        m = clamp01(1 - Math.abs(r - 0.28 - 0.06 * Math.cos(a * 6)) * 14) + clamp01(1 - r * 9) * 0.8;
        const ox = (gx + 0.5) - Math.floor(gx + 0.5) - 0.5, oy = (gy + 0.5) - Math.floor(gy + 0.5) - 0.5;
        m = Math.max(m, clamp01(1 - Math.hypot(ox, oy) * 12) * 0.7);
        break;
      }
      case 1: { // stripes
        const s = (u * 16) % 1;
        m = s < 0.18 ? 1 : s > 0.5 && s < 0.56 ? 0.6 : 0;
        break;
      }
      case 2: { // fleur-de-lis-ish lattice
        const d = Math.abs(cx) + Math.abs(cy);
        m = clamp01(1 - Math.abs(d - 0.42) * 22) + clamp01(1 - Math.hypot(cx, cy + 0.06) * 10) * (cy < 0.1 ? 1 : 0.4);
        break;
      }
      case 3: { // diamonds
        const dx = ((u * 8 + v * 8) % 1), dy = ((u * 8 - v * 8 + 100) % 1);
        m = (dx < 0.08 || dy < 0.08) ? 1 : 0;
        break;
      }
      case 4: { // floral vines
        const vine = Math.sin(v * Math.PI * 8 + Math.sin(u * Math.PI * 4) * 1.5);
        m = clamp01(1 - Math.abs(vine) * 6) * 0.8 + clamp01(1 - Math.hypot(cx, cy) * 6) * 0.7;
        break;
      }
      default: m = 0;
    }
    const n = fbm(u * 8, v * 8, 8, 4, 11 + kind);
    const g = grime(u, v, 40 + kind);
    // neutral light base; room tint multiplies via material color
    const base = 0.86 + n * 0.08;
    const val = base * (1 - m * 0.32) * (1 - g * 0.55);
    const warm = 1 - g * 0.2;
    setCol(f, i, val, val * 0.97 * warm, val * 0.9 * warm);
    f.ht[i] = m * 0.4 + n * 0.3 - g * 0.2;
    f.ro[i] = 0.82 - m * 0.18 + g * 0.1;
  });
  return toTextures(f, 1.6, 2.2);
}

function woodPlanks(dark = false): TexSet {
  const f = field();
  const planks = 6;
  each(f, (u, v, i) => {
    const row = Math.floor(v * planks);
    const off = hash(row, 0, 7) * 4;
    const pu = u * 4 + off;
    const seg = Math.floor(pu);
    const local = pu - seg;
    const pv = v * planks - row;
    const tone = hash(seg, row, 3);
    const grain = Math.sin((u * 4 + fbm(u * 3, v * 40, 3, 4, row + seg) * 1.2) * 90 + row * 13) * 0.5 + 0.5;
    const ring = fbm(u * 30, v * 3, 30, 4, seg * 7 + row);
    const gap = pv < 0.035 || pv > 0.965 || local < 0.006 ? 1 : 0;
    const g = grime(u, v, 17);
    const base = dark ? [0.22, 0.13, 0.08] : [0.42, 0.26, 0.15];
    const t = 0.75 + tone * 0.45 - grain * 0.18 - ring * 0.15 - g * 0.3;
    setCol(f, i, base[0] * t * (1 - gap * 0.7), base[1] * t * (1 - gap * 0.7), base[2] * t * (1 - gap * 0.7));
    f.ht[i] = -gap * 1.2 + grain * 0.15 + ring * 0.1;
    f.ro[i] = 0.55 + grain * 0.15 + g * 0.2 + gap * 0.3 - (dark ? 0.05 : 0);
  });
  return toTextures(f, 1.6, 3);
}

function woodGrain(): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    const w = fbm(u * 2, v * 2, 2, 3, 5);
    const rings = Math.sin((u * 14 + w * 3 + fbm(u * 30, v * 4, 30, 4, 9) * 0.8) * Math.PI * 2) * 0.5 + 0.5;
    const fine = fbm(u * 4, v * 64, 4, 3, 2);
    const t = 0.85 + rings * 0.14 - fine * 0.16;
    setCol(f, i, 0.62 * t, 0.42 * t, 0.27 * t);
    f.ht[i] = rings * 0.2 + fine * 0.15;
    f.ro[i] = 0.42 + fine * 0.2;
  });
  return toTextures(f, 1.0, 1.0);
}

function carpet(): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    const fiber = hash(Math.floor(u * SIZE), Math.floor(v * SIZE), 1) * 0.25 + fbm(u * 64, v * 64, 64, 2, 3) * 0.2;
    // ornate motif repeating every 1.25m
    const gx = (u * 2) % 1 - 0.5, gy = (v * 2) % 1 - 0.5;
    const r = Math.hypot(gx, gy);
    const motif = clamp01(1 - Math.abs(r - 0.3) * 20) + clamp01(1 - Math.abs(Math.abs(gx) + Math.abs(gy) - 0.18) * 25) * 0.8;
    const border = (Math.abs(gx) > 0.46 || Math.abs(gy) > 0.46) ? 0.6 : 0;
    const wear = clamp01(fbm(u * 3, v * 3, 3, 4, 22) - 0.35) * 0.7;
    const g = grime(u, v, 23);
    const val = 0.62 + fiber - motif * 0.25 - border * 0.15 + wear * 0.25 - g * 0.35;
    const gold = motif * 0.35;
    setCol(f, i, val + gold * 0.5, val * 0.92 + gold * 0.35, val * 0.86);
    f.ht[i] = fiber * 1.2 - motif * 0.2;
    f.ro[i] = 0.95;
  });
  return toTextures(f, 2.5, 2.5);
}

function tile(): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    const gx = u * 8, gy = v * 8;
    const ix = Math.floor(gx), iy = Math.floor(gy);
    const lx = gx - ix, ly = gy - iy;
    const grout = lx < 0.04 || ly < 0.04 ? 1 : 0;
    const check = (ix + iy) % 2;
    const n = fbm(u * 16, v * 16, 16, 4, 5);
    const g = grime(u, v, 31);
    const c = check ? 0.82 : 0.18;
    const t = c + n * 0.08 - g * 0.3;
    const gr = 0.35 - g * 0.15;
    setCol(f, i, grout ? gr : t, grout ? gr * 0.97 : t * 0.98, grout ? gr * 0.92 : t * 0.95);
    f.ht[i] = grout ? -1 : n * 0.1;
    f.ro[i] = grout ? 0.9 : 0.25 + g * 0.4;
  });
  return toTextures(f, 2, 3);
}

function marble(): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    const ix = Math.floor(u * 4), iy = Math.floor(v * 4);
    const lx = u * 4 - ix, ly = v * 4 - iy;
    const seam = lx < 0.012 || ly < 0.012 ? 1 : 0;
    const w = fbm(u * 4, v * 4, 4, 6, ix * 3 + iy);
    const vein = Math.pow(1 - Math.abs(Math.sin((u + v + w * 1.6) * Math.PI * 6)), 18);
    const g = grime(u, v, 44);
    const alt = (ix + iy) % 2 ? 0.92 : 0.78;
    const t = alt - vein * 0.35 - w * 0.06 - g * 0.25;
    setCol(f, i, t * (seam ? 0.6 : 1), t * 0.97 * (seam ? 0.6 : 1), t * 0.92 * (seam ? 0.6 : 1));
    f.ht[i] = -seam;
    f.ro[i] = 0.12 + g * 0.45 + vein * 0.1;
  });
  return toTextures(f, 3, 2);
}

function concrete(): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    const n = fbm(u * 6, v * 6, 6, 6, 8);
    const pits = hash(Math.floor(u * SIZE / 2), Math.floor(v * SIZE / 2), 4) > 0.985 ? 1 : 0;
    const stain = clamp01(fbm(u * 2, v * 2, 2, 4, 3) - 0.5) * 1.2;
    const t = 0.45 + n * 0.2 - stain * 0.25 - pits * 0.2;
    setCol(f, i, t, t * 0.98, t * 0.95);
    f.ht[i] = n * 0.4 - pits;
    f.ro[i] = 0.9 - stain * 0.2;
  });
  return toTextures(f, 3, 2);
}

function stone(): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    // irregular flagstones via jittered grid distance
    const gx = u * 5, gy = v * 5;
    const ix = Math.floor(gx), iy = Math.floor(gy);
    let d1 = 9, d2 = 9, id = 0;
    for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) {
      const cx = ix + dx, cy = iy + dy;
      const px = cx + hash(((cx % 5) + 5) % 5, ((cy % 5) + 5) % 5, 1), py = cy + hash(((cx % 5) + 5) % 5, ((cy % 5) + 5) % 5, 2);
      const d = Math.hypot(gx - px, gy - py);
      if (d < d1) { d2 = d1; d1 = d; id = hash(((cx % 5) + 5) % 5, ((cy % 5) + 5) % 5, 3); } else if (d < d2) d2 = d;
    }
    const edge = d2 - d1 < 0.06 ? 1 : 0;
    const n = fbm(u * 10, v * 10, 10, 4, 6);
    const moss = clamp01(fbm(u * 4, v * 4, 4, 4, 12) - 0.55) * 2;
    const t = 0.36 + id * 0.16 + n * 0.12;
    setCol(f, i, edge ? 0.12 : t - moss * 0.12, edge ? 0.12 : t + moss * 0.05, edge ? 0.1 : t * 0.92 - moss * 0.12);
    f.ht[i] = edge ? -1 : n * 0.5;
    f.ro[i] = 0.85;
  });
  return toTextures(f, 4, 3);
}

function grass(): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    const blades = hash(Math.floor(u * SIZE), Math.floor(v * SIZE * 0.5), 9);
    const n = fbm(u * 8, v * 8, 8, 5, 4);
    const dry = clamp01(fbm(u * 3, v * 3, 3, 4, 2) - 0.5) * 1.5;
    const t = 0.18 + n * 0.15 + blades * 0.08;
    setCol(f, i, t * (0.6 + dry * 0.6), t * 1.25, t * 0.45);
    f.ht[i] = blades * 0.8 + n * 0.3;
    f.ro[i] = 0.9;
  });
  return toTextures(f, 2, 2);
}

function plaster(): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    const n = fbm(u * 10, v * 10, 10, 5, 13);
    const crack = Math.pow(1 - Math.abs(fbm(u * 3, v * 3, 3, 5, 77) - 0.5) * 2, 60);
    const g = grime(u, v, 66);
    const t = 0.78 + n * 0.06 - g * 0.25 - crack * 0.12;
    setCol(f, i, t, t * 0.96, t * 0.88);
    f.ht[i] = n * 0.4 - crack;
    f.ro[i] = 0.92;
  });
  return toTextures(f, 3, 1.5);
}

function metal(): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    const brush = fbm(u * 2, v * 128, 2, 3, 3);
    const n = fbm(u * 6, v * 6, 6, 4, 9);
    const rust = clamp01(fbm(u * 5, v * 5, 5, 5, 31) - 0.62) * 2.5;
    const t = 0.7 + brush * 0.12;
    setCol(f, i, mix(t, 0.45, rust), mix(t, 0.22, rust), mix(t, 0.1, rust));
    f.ht[i] = brush * 0.2 + rust * 0.3;
    f.ro[i] = 0.32 + brush * 0.12 + rust * 0.5 + n * 0.08;
  });
  return toTextures(f, 1, 1);
}

function fabric(): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    const weave = (Math.sin(u * SIZE * 0.8) * Math.sin(v * SIZE * 0.8)) * 0.5 + 0.5;
    const n = fbm(u * 16, v * 16, 16, 3, 4);
    const g = grime(u, v, 5);
    const t = 0.72 + weave * 0.1 + n * 0.12 - g * 0.3;
    setCol(f, i, t, t, t);
    f.ht[i] = weave * 0.6;
    f.ro[i] = 0.95;
  });
  return toTextures(f, 0.6, 1.5);
}

function leaves(): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    const cells = fbm(u * 12, v * 12, 12, 3, 8);
    const vein = Math.pow(1 - Math.abs(Math.sin((u * 12 + v * 4) * Math.PI)), 12);
    const t = 0.12 + cells * 0.25;
    setCol(f, i, t * 0.55, t * 1.25 + vein * 0.05, t * 0.4);
    f.ht[i] = cells;
    f.ro[i] = 0.7;
  });
  return toTextures(f, 1, 2);
}

function brick(): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    const rows = 16, cols = 8;
    const row = Math.floor(v * rows);
    const off = row % 2 ? 0.5 : 0;
    const cu = u * cols + off;
    const lx = cu - Math.floor(cu), ly = v * rows - row;
    const mortar = lx < 0.05 || ly < 0.1 ? 1 : 0;
    const tone = hash(Math.floor(cu) % cols, row, 5);
    const n = fbm(u * 20, v * 20, 20, 3, 6);
    const t = 0.36 + tone * 0.14 + n * 0.1;
    setCol(f, i, mortar ? 0.5 : t * 1.25, mortar ? 0.48 : t * 0.62, mortar ? 0.44 : t * 0.45);
    f.ht[i] = mortar ? -1 : n * 0.3;
    f.ro[i] = 0.9;
  });
  return toTextures(f, 2, 3);
}

function dirt(): TexSet {
  const f = field();
  each(f, (u, v, i) => {
    const n = fbm(u * 8, v * 8, 8, 6, 4);
    const pebble = hash(Math.floor(u * 128), Math.floor(v * 128), 2) > 0.93 ? 1 : 0;
    const t = 0.2 + n * 0.18 + pebble * 0.12;
    setCol(f, i, t * 1.2, t * 0.95, t * 0.7);
    f.ht[i] = n + pebble * 0.6;
    f.ro[i] = 0.95;
  });
  return toTextures(f, 3, 3);
}

const GEN: Record<string, () => TexSet> = {
  wall0: () => wallpaper(0), wall1: () => wallpaper(1), wall2: () => wallpaper(2), wall3: () => wallpaper(3), wall4: () => wallpaper(4), wall5: () => wallpaper(5),
  planks: () => woodPlanks(false), planksDark: () => woodPlanks(true), wood: woodGrain, carpet, tile, marble, concrete, stone, grass, plaster, metal, fabric, leaves, brick, dirt,
};

export function getTex(name: keyof typeof GEN | string): TexSet {
  let t = cache.get(name);
  if (!t) {
    const g = GEN[name];
    if (!g) throw new Error(`unknown texture ${name}`);
    t = g();
    cache.set(name, t);
  }
  return t;
}

export const TEXTURE_NAMES = Object.keys(GEN);

// ---------------------------------------------------------------- canvas textures (paintings, faces, labels)

export function canvasTex(w: number, h: number, draw: (ctx: CanvasRenderingContext2D) => void, srgb = true): THREE.CanvasTexture {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const ctx = c.getContext('2d')!;
  draw(ctx);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = srgb ? THREE.SRGBColorSpace : THREE.NoColorSpace;
  t.anisotropy = ANISO;
  return t;
}

/** Old oil paintings: landscapes, portraits and still lifes, procedurally brushed. */
export function paintingTex(variant: number): THREE.CanvasTexture {
  return canvasTex(256, 320, (ctx) => {
    const R = (n: number) => hash(variant, n, 99);
    const kind = variant % 4;
    const g = ctx.createLinearGradient(0, 0, 0, 320);
    if (kind === 0) { g.addColorStop(0, '#2c3a4a'); g.addColorStop(0.55, '#8a7a5a'); g.addColorStop(1, '#2a2a18'); }
    else if (kind === 1) { g.addColorStop(0, '#1a120c'); g.addColorStop(1, '#3a2418'); }
    else if (kind === 2) { g.addColorStop(0, '#3a2a1a'); g.addColorStop(1, '#120c08'); }
    else { g.addColorStop(0, '#4a3826'); g.addColorStop(0.6, '#6a5a3a'); g.addColorStop(1, '#1e1810'); }
    ctx.fillStyle = g; ctx.fillRect(0, 0, 256, 320);
    if (kind === 0 || kind === 3) {
      // hills, trees, sky
      for (let k = 0; k < 3; k++) {
        ctx.fillStyle = `rgba(${40 + k * 20},${50 + k * 15},${30 + k * 10},0.9)`;
        ctx.beginPath(); ctx.moveTo(0, 320);
        for (let x = 0; x <= 256; x += 8) ctx.lineTo(x, 170 + k * 40 + Math.sin(x / (30 + k * 10) + R(k) * 6) * 20);
        ctx.lineTo(256, 320); ctx.fill();
      }
      for (let t = 0; t < 5; t++) { const x = R(t + 10) * 256, y = 200 + R(t + 20) * 60; ctx.fillStyle = '#1c2414'; ctx.beginPath(); ctx.ellipse(x, y - 30, 14, 34, 0, 0, Math.PI * 2); ctx.fill(); ctx.fillRect(x - 2, y, 4, 20); }
      if (kind === 3) { ctx.fillStyle = 'rgba(255,230,180,0.6)'; ctx.beginPath(); ctx.arc(60 + R(3) * 140, 70, 18, 0, Math.PI * 2); ctx.fill(); }
    } else if (kind === 1) {
      // portrait
      ctx.fillStyle = '#100a06'; ctx.beginPath(); ctx.ellipse(128, 300, 90, 90, 0, Math.PI, 0); ctx.fill();
      ctx.fillStyle = `rgb(${150 + R(1) * 40},${110 + R(2) * 30},${80 + R(3) * 20})`;
      ctx.beginPath(); ctx.ellipse(128, 150, 46, 60, 0, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = '#1a1008'; ctx.beginPath(); ctx.ellipse(128, 110, 50, 34, 0, Math.PI, 0); ctx.fill();
      ctx.fillStyle = '#0a0604'; ctx.fillRect(108, 140, 10, 5); ctx.fillRect(138, 140, 10, 5);
      ctx.fillStyle = 'rgba(80,30,20,0.7)'; ctx.fillRect(116, 180, 24, 4);
      ctx.fillStyle = '#2a1c10'; ctx.fillRect(70, 230, 116, 100);
    } else {
      // still life
      ctx.fillStyle = '#3a2412'; ctx.fillRect(0, 230, 256, 90);
      for (let k = 0; k < 4; k++) { ctx.fillStyle = `hsl(${R(k) * 60},50%,${30 + R(k + 5) * 20}%)`; ctx.beginPath(); ctx.arc(60 + k * 45, 215 - R(k + 9) * 30, 18 + R(k + 3) * 12, 0, Math.PI * 2); ctx.fill(); }
      ctx.fillStyle = '#5a4a3a'; ctx.fillRect(150, 120, 30, 100);
    }
    // varnish, craquelure and vignette
    for (let k = 0; k < 900; k++) { ctx.fillStyle = `rgba(0,0,0,${0.05 + R(k + 200) * 0.06})`; ctx.fillRect(R(k + 400) * 256, R(k + 800) * 320, 1 + R(k) * 2, 1); }
    const vg = ctx.createRadialGradient(128, 160, 60, 128, 160, 200);
    vg.addColorStop(0, 'rgba(60,40,10,0)'); vg.addColorStop(1, 'rgba(20,10,0,0.75)');
    ctx.fillStyle = vg; ctx.fillRect(0, 0, 256, 320);
  });
}

/** Door number plate texture ("0042"). */
export function plateTex(n: number, fake = false): THREE.CanvasTexture {
  return canvasTex(256, 96, (ctx) => {
    const g = ctx.createLinearGradient(0, 0, 0, 96);
    g.addColorStop(0, '#d9b467'); g.addColorStop(0.5, '#a07c34'); g.addColorStop(1, '#6e5220');
    ctx.fillStyle = g; ctx.fillRect(0, 0, 256, 96);
    ctx.strokeStyle = '#4a3410'; ctx.lineWidth = 6; ctx.strokeRect(6, 6, 244, 84);
    ctx.fillStyle = '#2a1a06';
    ctx.font = 'bold 60px Georgia, serif';
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText(String(n).padStart(4, '0'), 128, 52);
    if (fake) { ctx.fillStyle = 'rgba(40,0,0,0.12)'; ctx.fillRect(0, 0, 256, 96); }
  });
}

/** Book spine atlas for shelves. */
let spines: THREE.CanvasTexture | null = null;
export function spineTex(): THREE.CanvasTexture {
  if (spines) return spines;
  spines = canvasTex(512, 256, (ctx) => {
    let x = 0;
    let k = 0;
    while (x < 512) {
      const w = 10 + hash(k, 1, 4) * 18;
      const hue = [8, 25, 35, 120, 210, 0, 45][Math.floor(hash(k, 2, 4) * 7)];
      ctx.fillStyle = `hsl(${hue},${30 + hash(k, 3, 4) * 30}%,${12 + hash(k, 5, 4) * 22}%)`;
      ctx.fillRect(x, 0, w, 256);
      ctx.fillStyle = 'rgba(210,170,90,0.55)';
      ctx.fillRect(x + 2, 30 + hash(k, 6, 4) * 20, w - 4, 3);
      ctx.fillRect(x + 2, 200 - hash(k, 7, 4) * 20, w - 4, 3);
      ctx.fillStyle = 'rgba(0,0,0,0.35)'; ctx.fillRect(x + w - 1.5, 0, 1.5, 256);
      x += w; k++;
    }
  });
  spines.wrapS = THREE.RepeatWrapping;
  spines.userData.shared = true;
  return spines;
}

const spriteCache = new Map<string, THREE.CanvasTexture>();
export function radialSprite(inner: string, outer = 'rgba(0,0,0,0)', size = 128): THREE.CanvasTexture {
  const key = `${inner}|${outer}|${size}`;
  const hit = spriteCache.get(key);
  if (hit) return hit;
  const t = canvasTex(size, size, (ctx) => {
    const g = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
    g.addColorStop(0, inner); g.addColorStop(1, outer);
    ctx.fillStyle = g; ctx.fillRect(0, 0, size, size);
  });
  t.userData.shared = true;
  spriteCache.set(key, t);
  return t;
}
