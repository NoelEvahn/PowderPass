// Builds compact chunk geometry: a solid/cutout mesh and a translucent (water/ice/portal) mesh.
import * as THREE from 'three';
import { BLOCKS, B } from './blocks.js';
import { CS, CH } from './worldgen.js';
import { faceTex, frontTex, TEX_INDEX, TINTS } from './textures.js';
import { FALLING } from './world.js';

export const POS_SCALE = 64;
const P = CS + 2; // padded size
const pidx = (x, y, z) => (y * P + (z + 1)) * P + (x + 1); // x,z in [-1,16]

// Per-block render info lookups
const RENDER = new Uint8Array(256); // 0 none, 1 cube, 2 cross, 3 liquid, 4 torch, 5 portal, 6 flat
const OPAQUE_CUBE = new Uint8Array(256);
const CUTOUT = new Uint8Array(256);
const TRANSLUCENT = new Uint8Array(256);
const ANIM = new Uint8Array(256);
for (const b of BLOCKS) {
  if (!b) continue;
  RENDER[b.id] = { none: 0, cube: 1, cross: 2, liquid: 3, torch: 4, portal: 5, flat: 6 }[b.render];
  OPAQUE_CUBE[b.id] = b.opaque && b.render === 'cube' ? 1 : 0;
  CUTOUT[b.id] = b.cutout ? 1 : 0;
  TRANSLUCENT[b.id] = b.translucent ? 1 : 0;
  ANIM[b.id] = b.key.includes('leaves') ? 1 : b.render === 'cross' ? 2 : 0;
}

const FACES = [
  { d: [1, 0, 0], c: [[1, 0, 1], [1, 0, 0], [1, 1, 0], [1, 1, 1]] },
  { d: [-1, 0, 0], c: [[0, 0, 0], [0, 0, 1], [0, 1, 1], [0, 1, 0]] },
  { d: [0, 1, 0], c: [[0, 1, 1], [1, 1, 1], [1, 1, 0], [0, 1, 0]] },
  { d: [0, -1, 0], c: [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]] },
  { d: [0, 0, 1], c: [[0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]] },
  { d: [0, 0, -1], c: [[1, 0, 0], [0, 0, 0], [0, 1, 0], [1, 1, 0]] },
];
const UVS = [[0, 1], [1, 1], [1, 0], [0, 0]];
// Facing meta -> face index showing the front texture: 0 south(+z) 1 west(-x) 2 north(-z) 3 east(+x)
const FRONT_FACE = [4, 1, 5, 0];

class GeoBuilder {
  constructor() {
    this.cap = 4096;
    this.alloc(this.cap);
    this.n = 0;
  }
  alloc(cap) {
    const old = this.pos ? { pos: this.pos, light: this.light, tint: this.tint, data: this.data, layer: this.layer, flow: this.flow } : null;
    this.pos = new Int16Array(cap * 3);
    this.light = new Uint8Array(cap * 4);
    this.tint = new Uint8Array(cap * 4);
    this.data = new Uint8Array(cap * 4);
    this.layer = new Uint16Array(cap);
    this.flow = new Int8Array(cap * 2);
    if (old) {
      this.pos.set(old.pos); this.light.set(old.light); this.tint.set(old.tint);
      this.data.set(old.data); this.layer.set(old.layer); this.flow.set(old.flow);
    }
    this.cap = cap;
  }
  vert(x, y, z, sky, blk, ao, flag, r, g, bl, a, face, anim, u, v, layer, fx = 0, fz = 0) {
    if (this.n >= this.cap) this.alloc(this.cap * 2);
    const n = this.n++;
    this.pos[n * 3] = Math.round(x * POS_SCALE);
    this.pos[n * 3 + 1] = Math.round(y * POS_SCALE);
    this.pos[n * 3 + 2] = Math.round(z * POS_SCALE);
    this.light[n * 4] = sky; this.light[n * 4 + 1] = blk; this.light[n * 4 + 2] = ao; this.light[n * 4 + 3] = flag;
    this.tint[n * 4] = r; this.tint[n * 4 + 1] = g; this.tint[n * 4 + 2] = bl; this.tint[n * 4 + 3] = a;
    this.data[n * 4] = face; this.data[n * 4 + 1] = anim; this.data[n * 4 + 2] = u; this.data[n * 4 + 3] = v;
    this.layer[n] = layer;
    this.flow[n * 2] = fx; this.flow[n * 2 + 1] = fz;
  }
  build(withFlow) {
    if (this.n === 0) return null;
    const g = new THREE.BufferGeometry();
    const n = this.n;
    g.setAttribute('position', new THREE.BufferAttribute(this.pos.slice(0, n * 3), 3));
    g.setAttribute('aLight', new THREE.BufferAttribute(this.light.slice(0, n * 4), 4, true));
    g.setAttribute('aTint', new THREE.BufferAttribute(this.tint.slice(0, n * 4), 4, true));
    g.setAttribute('aData', new THREE.BufferAttribute(this.data.slice(0, n * 4), 4));
    g.setAttribute('aLayer', new THREE.BufferAttribute(this.layer.slice(0, n), 1));
    if (withFlow) g.setAttribute('aFlow', new THREE.BufferAttribute(this.flow.slice(0, n * 2), 2, true));
    const quads = n / 4;
    const index = n > 65535 ? new Uint32Array(quads * 6) : new Uint16Array(quads * 6);
    for (let q = 0, i = 0; q < quads; q++) {
      const b = q * 4;
      if (this.flip && this.flip[q]) {
        index[i++] = b + 1; index[i++] = b + 2; index[i++] = b + 3;
        index[i++] = b + 1; index[i++] = b + 3; index[i++] = b;
      } else {
        index[i++] = b; index[i++] = b + 1; index[i++] = b + 2;
        index[i++] = b; index[i++] = b + 2; index[i++] = b + 3;
      }
    }
    g.setIndex(new THREE.BufferAttribute(index, 1));
    g.boundingSphere = new THREE.Sphere(new THREE.Vector3(8, 64, 8), 70);
    g.boundingBox = new THREE.Box3(new THREE.Vector3(0, 0, 0), new THREE.Vector3(16, 128, 16));
    return g;
  }
  reset() { this.n = 0; this.flip = []; }
}

const solid = new GeoBuilder();
const trans = new GeoBuilder();
// padded scratch volumes
const vBlocks = new Uint8Array(P * P * CH);
const vMeta = new Uint8Array(P * P * CH);
const vLight = new Uint8Array(P * P * CH);

function tintFor(type, temp, hum) {
  if (type === 'birch') return TINTS.birch;
  if (type === 'spruce') return TINTS.spruce;
  if (type === 'lily') return TINTS.lily;
  // smooth climate-based colour, similar to Minecraft's grass/foliage colormaps
  const t = Math.max(0, Math.min(1, (temp + 1) / 2));
  const h = Math.max(0, Math.min(1, (hum + 1) / 2));
  const F = type === 'foliage';
  const base = F ? [0.47, 0.67, 0.18] : [0.57, 0.74, 0.35];
  const hot = F ? [0.68, 0.64, 0.16] : [0.75, 0.72, 0.38];
  const lush = F ? [0.25, 0.68, 0.08] : [0.38, 0.77, 0.24];
  const cold = F ? [0.38, 0.63, 0.48] : [0.5, 0.71, 0.53];
  let c = base;
  if (t > 0.6) c = mixc(c, hot, (t - 0.6) * 2.5 * (1 - h));
  if (t > 0.55 && h > 0.6) c = mixc(c, lush, (h - 0.6) * 2.5);
  if (t < 0.35) c = mixc(c, cold, (0.35 - t) * 3);
  return c;
}
const mixc = (a, b, t) => {
  t = Math.max(0, Math.min(1, t));
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
};

export function buildChunkMesh(world, chunk) {
  const cx = chunk.cx, cz = chunk.cz;
  // ---- gather padded volume ----
  for (let dz = -1; dz <= 1; dz++)
    for (let dx = -1; dx <= 1; dx++) {
      const c = world.getChunk(cx + dx, cz + dz);
      const xs = dx === -1 ? 15 : 0, xe = dx === 1 ? 0 : 15;
      const zs = dz === -1 ? 15 : 0, ze = dz === 1 ? 0 : 15;
      for (let y = 0; y < CH; y++)
        for (let z = zs; z <= ze; z++) {
          const pz = z + dz * 16;
          for (let x = xs; x <= xe; x++) {
            const px = x + dx * 16;
            const pi = pidx(px, y, pz);
            if (c) {
              const ci = (y * CS + z) * CS + x;
              vBlocks[pi] = c.blocks[ci]; vMeta[pi] = c.meta[ci]; vLight[pi] = c.light[ci];
            } else {
              vBlocks[pi] = 0; vMeta[pi] = 0; vLight[pi] = world.hasSky ? 0xf0 : 0;
            }
          }
        }
    }

  solid.reset();
  trans.reset();
  const torches = [];
  const hasSky = world.hasSky;
  const blockAt = (x, y, z) => (y < 0 || y >= CH ? 0 : vBlocks[pidx(x, y, z)]);
  const lightAt = (x, y, z) => (y >= CH ? (hasSky ? 0xf0 : 0) : y < 0 ? 0 : vLight[pidx(x, y, z)]);

  let maxY = 0;
  for (let i = 0; i < chunk.blocks.length; i++) if (chunk.blocks[i]) maxY = i;
  maxY = Math.min(CH - 1, Math.floor(maxY / (CS * CS)) + 1);

  for (let y = 0; y <= maxY; y++)
    for (let z = 0; z < CS; z++)
      for (let x = 0; x < CS; x++) {
        const id = vBlocks[pidx(x, y, z)];
        if (!id) continue;
        const r = RENDER[id];
        const b = BLOCKS[id];
        const col = z * CS + x;
        if (r === 1) {
          const translucent = TRANSLUCENT[id];
          const out = translucent ? trans : solid;
          let tint = null;
          if (b.tint) tint = tintFor(b.tint, chunk.temp[col], chunk.hum[col]);
          for (let f = 0; f < 6; f++) {
            const fd = FACES[f].d;
            const nx = x + fd[0], ny = y + fd[1], nz = z + fd[2];
            const nid = blockAt(nx, ny, nz);
            if (ny < 0) continue;
            if (OPAQUE_CUBE[nid]) continue;
            if (nid === id && (translucent || id === B.glass)) continue;
            if (translucent && nid && RENDER[nid] === 1 && !CUTOUT[nid] && !TRANSLUCENT[nid]) continue;
            let layer = faceTex[id * 6 + f];
            if (b.facing) { if (f === FRONT_FACE[vMeta[pidx(x, y, z)] & 3]) layer = frontTex(id); }
            else if (id === B.crafting_table && (f === 4 || f === 1)) layer = frontTex(id);
            let ft = tint;
            if (b.tintTop && f === 2) ft = tintFor('grass', chunk.temp[col], chunk.hum[col]);
            const water = BLOCKS[nid].liquid === 'water' ? 255 : 0;
            emitFace(out, x, y, z, f, layer, ft, ANIM[id], blockAt, lightAt, water, translucent ? 1 : 0);
          }
        } else if (r === 2) {
          const tint = b.tint ? tintFor(b.tint, chunk.temp[col], chunk.hum[col]) : null;
          emitCross(solid, x, y, z, faceTex[id * 6], tint, lightAt(x, y, z), id);
        } else if (r === 3) {
          emitLiquid(b.liquid === 'water' ? trans : solid, x, y, z, id, b.liquid, blockAt, lightAt, chunk);
        } else if (r === 4) {
          torches.push(cx * CS + x, y, cz * CS + z);
          emitTorch(solid, x, y, z, faceTex[id * 6], lightAt(x, y, z));
        } else if (r === 5) {
          emitPortal(trans, x, y, z, faceTex[id * 6], vMeta[pidx(x, y, z)], lightAt(x, y, z));
        } else if (r === 6) {
          const tint = b.tint ? tintFor(b.tint, 0, 0) : null;
          emitFlat(solid, x, y, z, faceTex[id * 6], tint, lightAt(x, y, z));
        }
      }
  return { solid: solid.build(false), trans: trans.build(true), torches };
}

function emitFace(out, x, y, z, f, layer, tint, anim, blockAt, lightAt, waterFlag, kind) {
  const F = FACES[f];
  const d = F.d;
  const nx = x + d[0], ny = y + d[1], nz = z + d[2];
  // tangent axes
  const ax = d[0] !== 0 ? [1, 2] : d[1] !== 0 ? [0, 2] : [0, 1];
  const aos = [0, 0, 0, 0];
  const r = tint ? tint[0] * 255 : 255, g = tint ? tint[1] * 255 : 255, bl = tint ? tint[2] * 255 : 255;
  const shade = [0.6, 0.6, 1.0, 0.5, 0.8, 0.8][f];
  for (let v = 0; v < 4; v++) {
    const c = F.c[v];
    const s1 = [0, 0, 0], s2 = [0, 0, 0];
    s1[ax[0]] = c[ax[0]] ? 1 : -1;
    s2[ax[1]] = c[ax[1]] ? 1 : -1;
    const o1 = OPAQUE_CUBE[blockAt(nx + s1[0], ny + s1[1], nz + s1[2])];
    const o2 = OPAQUE_CUBE[blockAt(nx + s2[0], ny + s2[1], nz + s2[2])];
    const oc = OPAQUE_CUBE[blockAt(nx + s1[0] + s2[0], ny + s1[1] + s2[1], nz + s1[2] + s2[2])];
    const ao = o1 && o2 ? 0 : 3 - (o1 + o2 + oc);
    aos[v] = ao;
    // smooth light
    let sky = 0, blk = 0, cnt = 0;
    const l0 = lightAt(nx, ny, nz);
    sky += l0 >> 4; blk += l0 & 15; cnt++;
    if (!o1) { const l = lightAt(nx + s1[0], ny + s1[1], nz + s1[2]); sky += l >> 4; blk += l & 15; cnt++; }
    if (!o2) { const l = lightAt(nx + s2[0], ny + s2[1], nz + s2[2]); sky += l >> 4; blk += l & 15; cnt++; }
    if (!oc && !(o1 && o2)) { const l = lightAt(nx + s1[0] + s2[0], ny + s1[1] + s2[1], nz + s1[2] + s2[2]); sky += l >> 4; blk += l & 15; cnt++; }
    const uv = UVS[v];
    out.vert(x + c[0], y + c[1], z + c[2], (sky / cnt) * 17, (blk / cnt) * 17, ao * 85 * shade, waterFlag, r, g, bl, kind * 255, f, anim, uv[0] * 16, uv[1] * 16, layer);
  }
  out.flip[out.n / 4 - 1] = aos[0] + aos[2] < aos[1] + aos[3];
}

function emitCross(out, x, y, z, layer, tint, light, id) {
  const r = tint ? tint[0] * 255 : 255, g = tint ? tint[1] * 255 : 255, bl = tint ? tint[2] * 255 : 255;
  const sky = (light >> 4) * 17, blk = (light & 15) * 17;
  // slight random offset like Minecraft
  const h = ((x * 3129871) ^ (z * 116129781) ^ y) & 255;
  const ox = id === B.sugar_cane ? 0 : ((h & 15) / 15 - 0.5) * 0.3, oz = id === B.sugar_cane ? 0 : ((h >> 4) / 15 - 0.5) * 0.3;
  const a = 0.15, b2 = 0.85;
  const quads = [
    [[a, 0, a], [b2, 0, b2], [b2, 1, b2], [a, 1, a]],
    [[b2, 0, b2], [a, 0, a], [a, 1, a], [b2, 1, b2]],
    [[a, 0, b2], [b2, 0, a], [b2, 1, a], [a, 1, b2]],
    [[b2, 0, a], [a, 0, b2], [a, 1, b2], [b2, 1, a]],
  ];
  for (const q of quads) {
    for (let v = 0; v < 4; v++) {
      const c = q[v];
      out.vert(x + c[0] + ox, y + c[1], z + c[2] + oz, sky, blk, 255 * 0.9, 0, r, g, bl, 0, 2, 2 + (c[1] > 0 ? 0 : 0), UVS[v][0] * 16, UVS[v][1] * 16, layer);
    }
    out.flip[out.n / 4 - 1] = false;
  }
}

function emitFlat(out, x, y, z, layer, tint, light) {
  const r = tint ? tint[0] * 255 : 255, g = tint ? tint[1] * 255 : 255, bl = tint ? tint[2] * 255 : 255;
  const sky = (light >> 4) * 17, blk = (light & 15) * 17;
  const yy = y + 1 / 64;
  const top = [[0, yy, 1], [1, yy, 1], [1, yy, 0], [0, yy, 0]];
  for (let v = 0; v < 4; v++) out.vert(x + top[v][0], top[v][1], z + top[v][2], sky, blk, 255, 0, r, g, bl, 0, 2, 0, UVS[v][0] * 16, UVS[v][1] * 16, layer);
  out.flip[out.n / 4 - 1] = false;
  for (let v = 3; v >= 0; v--) out.vert(x + top[v][0], top[v][1], z + top[v][2], sky, blk, 255, 0, r, g, bl, 0, 3, 0, UVS[v][0] * 16, UVS[v][1] * 16, layer);
  out.flip[out.n / 4 - 1] = false;
}

function emitTorch(out, x, y, z, layer, light) {
  const sky = (light >> 4) * 17, blk = (light & 15) * 17;
  const x0 = 7 / 16, x1 = 9 / 16, y1 = 10 / 16;
  for (let f = 0; f < 6; f++) {
    const F = FACES[f];
    for (let v = 0; v < 4; v++) {
      const c = F.c[v];
      const px = c[0] ? x1 : x0, pz = c[2] ? x1 : x0, py = c[1] ? y1 : 0;
      let u, vv;
      if (f === 2) { u = c[0] ? 9 : 7; vv = c[2] ? 8 : 6; }
      else if (f === 3) { u = c[0] ? 9 : 7; vv = c[2] ? 16 : 14; }
      else { u = UVS[v][0] ? 9 : 7; vv = UVS[v][1] ? 16 : 6; }
      out.vert(x + px, y + py, z + pz, sky, blk, 255, 0, 255, 255, 255, 0, f, 0, u, vv, layer);
    }
    out.flip[out.n / 4 - 1] = false;
  }
}

function emitPortal(out, x, y, z, layer, meta, light) {
  const sky = (light >> 4) * 17, blk = 255;
  const alongX = meta === 0;
  const lo = 6 / 16, hi = 10 / 16;
  const faces = alongX
    ? [[[0, 0, hi], [1, 0, hi], [1, 1, hi], [0, 1, hi]], [[1, 0, lo], [0, 0, lo], [0, 1, lo], [1, 1, lo]]]
    : [[[lo, 0, 0], [lo, 0, 1], [lo, 1, 1], [lo, 1, 0]], [[hi, 0, 1], [hi, 0, 0], [hi, 1, 0], [hi, 1, 1]]];
  for (const q of faces) {
    for (let v = 0; v < 4; v++) out.vert(x + q[v][0], y + q[v][1], z + q[v][2], sky, blk, 255, 0, 255, 255, 255, 2 * 85, 4, 0, UVS[v][0] * 16, UVS[v][1] * 16, layer);
    out.flip[out.n / 4 - 1] = false;
  }
}

function fluidH(blockAt, x, y, z, id) {
  const b = blockAt(x, y, z);
  if (b !== id) return -1;
  if (blockAt(x, y + 1, z) === id) return 1;
  const m = vMeta[pidx(x, y, z)];
  if (m >= FALLING) return 0.89;
  return (8 - m) / 9;
}

function cornerHeight(blockAt, x, y, z, id) {
  // corner at (x, z) of cells (x-1..x, z-1..z)
  let sum = 0, w = 0;
  for (let dz = -1; dz <= 0; dz++)
    for (let dx = -1; dx <= 0; dx++) {
      const cx = x + dx, cz = z + dz;
      if (blockAt(cx, y + 1, cz) === id) return 1;
      const h = fluidH(blockAt, cx, y, cz, id);
      if (h >= 0) {
        const wt = h >= 0.8 ? 10 : 1;
        sum += h * wt; w += wt;
      } else if (!BLOCKS[blockAt(cx, y, cz)].solid) {
        w += 1;
      }
    }
  return w ? sum / w : 0.89;
}

function emitLiquid(out, x, y, z, id, fluid, blockAt, lightAt, chunk) {
  const above = blockAt(x, y + 1, z);
  const h00 = cornerHeight(blockAt, x, y, z, id);
  const h10 = cornerHeight(blockAt, x + 1, y, z, id);
  const h11 = cornerHeight(blockAt, x + 1, y, z + 1, id);
  const h01 = cornerHeight(blockAt, x, y, z + 1, id);
  // flow direction from height gradient
  let fx = (h00 + h01 - h10 - h11) * 0.5;
  let fz = (h00 + h10 - h01 - h11) * 0.5;
  const fl = Math.hypot(fx, fz);
  if (fl > 0.001) { fx /= fl; fz /= fl; } else { fx = 0; fz = 0; }
  const isWater = fluid === 'water';
  const kind = isWater ? 0 : 1;
  const tint = isWater ? tintWater(chunk, x, z) : [1, 1, 1];
  const r = tint[0] * 255, g = tint[1] * 255, bl = tint[2] * 255;
  const layer = TEX_INDEX[isWater ? 'water_still' : 'lava_still'];
  const anim = isWater ? 4 : 3;
  // water depth below (for colour absorption)
  let depth = 0;
  if (isWater) while (depth < 12 && blockAt(x, y - depth - 1, z) === id) depth++;
  const depthByte = Math.min(255, depth * 21);
  const L = (lx, ly, lz) => {
    const l = lightAt(lx, ly, lz);
    return [(l >> 4) * 17, (l & 15) * 17];
  };
  const own = lightAt(x, y, z);
  const fxB = Math.round(fx * 127), fzB = Math.round(fz * 127);
  const alpha = isWater ? 0 : 0;
  if (above !== id && !(above && BLOCKS[above].opaque && BLOCKS[above].render === 'cube')) {
    const lt = L(x, y + 1, z);
    const sky = Math.max(lt[0], (own >> 4) * 17), blk = Math.max(lt[1], (own & 15) * 17);
    const c = [[0, h01, 1], [1, h11, 1], [1, h10, 0], [0, h00, 0]];
    for (let v = 0; v < 4; v++) out.vert(x + c[v][0], y + c[v][1], z + c[v][2], sky, blk, depthByte, 0, r, g, bl, alpha, 2, anim, UVS[v][0] * 16, UVS[v][1] * 16, layer, fxB, fzB);
    out.flip[out.n / 4 - 1] = false;
  }
  const sides = [
    [1, 0, 0, [[1, 0, 1], [1, 0, 0], [1, h10, 0], [1, h11, 1]]],
    [-1, 0, 1, [[0, 0, 0], [0, 0, 1], [0, h01, 1], [0, h00, 0]]],
    [0, 1, 4, [[0, 0, 1], [1, 0, 1], [1, h11, 1], [0, h01, 1]]],
    [0, -1, 5, [[1, 0, 0], [0, 0, 0], [0, h00, 0], [1, h10, 0]]],
  ];
  for (const [dx, dz, f, c] of sides) {
    const n = blockAt(x + dx, y, z + dz);
    if (n === id) continue;
    const nb = BLOCKS[n];
    if (nb.opaque && nb.render === 'cube') continue;
    if (isWater && nb.liquid === 'lava') continue;
    const lt = L(x + dx, y, z + dz);
    for (let v = 0; v < 4; v++) {
      const vv = (1 - c[v][1]) * 16;
      out.vert(x + c[v][0], y + c[v][1], z + c[v][2], lt[0], lt[1], depthByte, 0, r * 0.85, g * 0.85, bl * 0.85, alpha, f, anim, UVS[v][0] * 16, vv, layer, 0, -127);
    }
    out.flip[out.n / 4 - 1] = false;
  }
  const below = blockAt(x, y - 1, z);
  if (below !== id && y > 0 && !(BLOCKS[below].opaque && BLOCKS[below].render === 'cube')) {
    const lt = L(x, y - 1, z);
    const c = [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]];
    for (let v = 0; v < 4; v++) out.vert(x + c[v][0], y, z + c[v][2], lt[0], lt[1], depthByte, 0, r * 0.6, g * 0.6, bl * 0.6, alpha, 3, anim, UVS[v][0] * 16, UVS[v][1] * 16, layer);
    out.flip[out.n / 4 - 1] = false;
  }
}

function tintWater(chunk, x, z) {
  const col = Math.max(0, Math.min(15, z)) * CS + Math.max(0, Math.min(15, x));
  const t = chunk.temp[col], h = chunk.hum[col];
  if (chunk.biome[col] === 12) return [0.38, 0.48, 0.36]; // swamp
  if (t < -0.45) return [0.24, 0.34, 0.7];
  if (t > 0.45 && h > 0.3) return [0.16, 0.55, 0.62];
  if (t > 0.45) return [0.2, 0.52, 0.66];
  return [0.17, 0.4, 0.82];
}
