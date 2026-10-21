// Deterministic structures: villages, shipwrecks, desert wells, ruined portals, dungeons, nether fortresses.
// Each structure is built once as a block list (cached) and clipped into every chunk it overlaps.
import { B, I } from './blocks.js';
import { mulberry32, hash3 } from './noise.js';
import { CS, CH, SEA } from './worldgen.js';

class Builder {
  constructor(gen, seed) {
    this.gen = gen;
    this.map = new Map();
    this.chests = [];
    this.mobs = [];
    this.rand = mulberry32(seed);
    this.minX = Infinity; this.maxX = -Infinity; this.minZ = Infinity; this.maxZ = -Infinity;
  }
  set(x, y, z, id, meta = 0) {
    if (y < 1 || y >= CH - 1) return;
    this.map.set(x + ',' + y + ',' + z, [x, y, z, id, meta]);
    if (x < this.minX) this.minX = x; if (x > this.maxX) this.maxX = x;
    if (z < this.minZ) this.minZ = z; if (z > this.maxZ) this.maxZ = z;
  }
  // air above sea level, water below (for flooded structures)
  clear(x, y, z) { this.set(x, y, z, y <= SEA && this.gen.column(x, z).h < SEA ? B.water : B.air); }
  h(x, z) { return this.gen.column(x, z).h; }
  chest(x, y, z, loot, meta = 0) { this.set(x, y, z, B.chest, meta); this.chests.push([x, y, z, loot]); }
  // fill from terrain up to y (exclusive) with a foundation block
  foundation(x, z, y, id) { for (let yy = this.h(x, z) + 1; yy < y; yy++) this.set(x, yy, z, id); }
  done(type) {
    if (!this.map.size) return null;
    return { type, blocks: [...this.map.values()], chests: this.chests, mobs: this.mobs, minX: this.minX, maxX: this.maxX, minZ: this.minZ, maxZ: this.maxZ };
  }
}

// ---------------- loot ----------------
export function rollLoot(table, seed) {
  const r = mulberry32(seed);
  const T = {
    village: [[I.bread, 1, 4, 0.8], [I.apple, 1, 3, 0.6], [I.wheat, 2, 8, 0.6], [I.iron_ingot, 1, 3, 0.4], [I.iron_pickaxe, 1, 1, 0.1], [B.oak_log, 2, 6, 0.4], [I.emerald, 1, 2, 0.2], [B.torch, 2, 8, 0.4]],
    shipwreck: [[I.iron_ingot, 1, 5, 0.7], [I.gold_ingot, 1, 4, 0.5], [I.emerald, 1, 3, 0.5], [I.diamond, 1, 1, 0.12], [I.paper, 1, 6, 0.6], [I.coal, 2, 8, 0.6], [I.bread, 1, 3, 0.4], [I.rotten_flesh, 1, 5, 0.4]],
    dungeon: [[I.bone, 1, 6, 0.8], [I.string, 1, 4, 0.6], [I.gunpowder, 1, 4, 0.6], [I.iron_ingot, 1, 4, 0.5], [I.gold_ingot, 1, 3, 0.35], [I.diamond, 1, 2, 0.1], [I.bucket, 1, 1, 0.3], [I.rotten_flesh, 1, 6, 0.5], [I.bread, 1, 2, 0.4]],
    portal: [[I.gold_ingot, 2, 7, 0.8], [B.obsidian, 1, 4, 0.6], [I.flint_and_steel, 1, 1, 0.5], [I.flint, 1, 4, 0.4], [I.golden_sword, 1, 1, 0.2], [I.golden_pickaxe, 1, 1, 0.15]],
    fortress: [[I.gold_ingot, 1, 4, 0.7], [I.iron_ingot, 1, 4, 0.5], [I.diamond, 1, 3, 0.25], [I.golden_sword, 1, 1, 0.3], [B.obsidian, 2, 6, 0.3], [I.flint_and_steel, 1, 1, 0.3]],
    well: [[I.emerald, 1, 2, 0.5], [I.gold_ingot, 1, 2, 0.4], [I.bread, 1, 2, 0.4]],
  }[table] || [];
  const items = new Array(27).fill(null);
  for (const [id, a, b, p] of T) {
    if (r() > p) continue;
    let slot = (r() * 27) | 0;
    for (let k = 0; k < 27 && items[slot]; k++) slot = (slot + 1) % 27;
    items[slot] = { id, count: a + ((r() * (b - a + 1)) | 0) };
  }
  return items;
}

// ---------------- village ----------------
const STYLES = {
  plains: { log: B.oak_log, plank: B.oak_planks, base: B.cobblestone, roof: B.oak_planks, path: B.dirt_path },
  flower_forest: { log: B.oak_log, plank: B.oak_planks, base: B.cobblestone, roof: B.oak_planks, path: B.dirt_path },
  savanna: { log: B.acacia_log, plank: B.oak_planks, base: B.orange_terracotta, roof: B.acacia_log, path: B.dirt_path },
  desert: { log: B.sandstone, plank: B.sandstone, base: B.sandstone, roof: B.sandstone, path: B.dirt_path, flat: true },
  taiga: { log: B.spruce_log, plank: B.spruce_planks, base: B.cobblestone, roof: B.spruce_planks, path: B.dirt_path },
  snowy_plains: { log: B.spruce_log, plank: B.spruce_planks, base: B.cobblestone, roof: B.spruce_planks, path: B.dirt_path },
  cherry_grove: { log: B.cherry_log, plank: B.cherry_planks, base: B.stone_bricks, roof: B.cherry_planks, path: B.dirt_path },
};

function house(b, st, x0, z0, w, d, door, big) {
  // floor height from footprint average
  let sum = 0, n = 0;
  for (let x = x0; x < x0 + w; x++) for (let z = z0; z < z0 + d; z++) { sum += b.h(x, z); n++; }
  const fy = Math.round(sum / n);
  const wallH = big ? 4 : 3;
  for (let x = x0; x < x0 + w; x++)
    for (let z = z0; z < z0 + d; z++) {
      b.foundation(x, z, fy, st.base);
      b.set(x, fy, z, st.base === B.sandstone ? B.sandstone : st.plank);
      const edge = x === x0 || x === x0 + w - 1 || z === z0 || z === z0 + d - 1;
      const corner = (x === x0 || x === x0 + w - 1) && (z === z0 || z === z0 + d - 1);
      for (let y = fy + 1; y <= fy + wallH + (st.flat ? 1 : 4) + 2; y++) b.set(x, y, z, B.air);
      if (edge) {
        for (let y = fy + 1; y <= fy + wallH; y++) {
          let id = corner ? st.log : st.plank;
          const along = x === x0 || x === x0 + w - 1 ? z - z0 : x - x0;
          if (!corner && y === fy + 2 && along % 2 === 0) id = B.glass;
          b.set(x, y, z, id);
        }
      }
    }
  // door opening
  const [dx, dz] = door;
  b.set(dx, fy + 1, dz, B.air);
  b.set(dx, fy + 2, dz, B.air);
  // roof
  if (st.flat) {
    for (let x = x0; x < x0 + w; x++) for (let z = z0; z < z0 + d; z++) b.set(x, fy + wallH + 1, z, st.roof);
  } else {
    const alongX = w >= d;
    const half = Math.ceil((alongX ? d : w) / 2);
    for (let k = 0; k <= half; k++) {
      const y = fy + wallH + 1 + k;
      for (let x = x0 - 1; x <= x0 + w; x++)
        for (let z = z0 - 1; z <= z0 + d; z++) {
          const c = alongX ? Math.min(z - (z0 - 1), z0 + d - z) : Math.min(x - (x0 - 1), x0 + w - x);
          if (c === k) b.set(x, y, z, st.roof);
          else if (c > k && (alongX ? x === x0 || x === x0 + w - 1 : z === z0 || z === z0 + d - 1)) b.set(x, y, z, st.plank);
        }
    }
  }
  // interior
  b.set(x0 + 1, fy + 1, z0 + 1, B.crafting_table);
  b.set(x0 + w - 2, fy + 1, z0 + d - 2, B.torch);
  if (big) {
    b.chest(x0 + w - 2, fy + 1, z0 + 1, 'village');
    b.set(x0 + 1, fy + 1, z0 + d - 2, B.furnace);
    b.set(x0 + 2, fy + 1, z0 + d - 2, B.bookshelf);
  } else if (b.rand() < 0.5) b.chest(x0 + w - 2, fy + 1, z0 + 1, 'village');
  // light at door outside
  b.mobs.push(['villager', dx + 0.5, fy + 1, dz + 0.5]);
}

function farm(b, x0, z0, w, d) {
  let sum = 0, n = 0;
  for (let x = x0; x < x0 + w; x++) for (let z = z0; z < z0 + d; z++) { sum += b.h(x, z); n++; }
  const fy = Math.round(sum / n);
  const mid = x0 + (w >> 1);
  for (let x = x0; x < x0 + w; x++)
    for (let z = z0; z < z0 + d; z++) {
      b.foundation(x, z, fy, B.dirt);
      for (let y = fy + 1; y < fy + 4; y++) b.set(x, y, z, B.air);
      const edge = x === x0 || x === x0 + w - 1 || z === z0 || z === z0 + d - 1;
      if (edge) b.set(x, fy, z, B.oak_log);
      else if (x === mid) b.set(x, fy, z, B.water);
      else { b.set(x, fy, z, B.farmland); b.set(x, fy + 1, z, B.wheat_crop); }
    }
}

function lampPost(b, x, z, st) {
  const y = b.h(x, z) + 1;
  b.set(x, y, z, st.log === B.sandstone ? B.sandstone : st.log);
  b.set(x, y + 1, z, st.log === B.sandstone ? B.sandstone : st.log);
  b.set(x, y + 2, z, B.torch);
}

function well(b, cx, cz, st) {
  const fy = b.h(cx, cz);
  for (let x = cx - 2; x <= cx + 2; x++)
    for (let z = cz - 2; z <= cz + 2; z++) {
      b.foundation(x, z, fy, st.base);
      const ring = Math.abs(x - cx) === 2 || Math.abs(z - cz) === 2;
      const inner = Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1;
      for (let y = fy + 1; y < fy + 6; y++) b.set(x, y, z, B.air);
      b.set(x, fy, z, inner ? B.water : st.base);
      if (inner) { b.set(x, fy - 1, z, B.water); b.set(x, fy - 2, z, B.water); b.set(x, fy - 3, z, st.base); }
      if (ring && !inner) b.set(x, fy + 1, z, st.base);
      if (Math.abs(x - cx) === 2 && Math.abs(z - cz) === 2) { b.set(x, fy + 2, z, st.log); b.set(x, fy + 3, z, st.log); }
      if (Math.abs(x - cx) <= 2 && Math.abs(z - cz) <= 2) b.set(x, fy + 4, z, st.roof === B.sandstone ? B.sandstone : st.plank);
    }
  b.set(cx, fy + 3, cz, B.bell);
}

function buildVillage(gen, cx, cz, seed) {
  const c = gen.column(cx, cz);
  const st = STYLES[c.biome];
  if (!st || c.h <= SEA) return null;
  // reject very bumpy terrain
  let minH = 999, maxH = -1;
  for (let k = -16; k <= 16; k += 8) for (let j = -16; j <= 16; j += 8) { const h = gen.column(cx + k, cz + j).h; minH = Math.min(minH, h); maxH = Math.max(maxH, h); }
  if (maxH - minH > 9 || minH <= SEA) return null;
  const b = new Builder(gen, seed);
  const L = 24;
  // paths
  for (let k = -L; k <= L; k++)
    for (const [px, pz] of [[cx + k, cz], [cx + k, cz + 1], [cx, cz + k], [cx + 1, cz + k]]) {
      const h = b.h(px, pz);
      if (h <= SEA) continue;
      b.set(px, h, pz, st.path);
      b.set(px, h + 1, pz, B.air);
      b.set(px, h + 2, pz, B.air);
    }
  well(b, cx - 4, cz - 4, st);
  // houses along both paths
  const slots = [];
  for (const dist of [7, 15]) {
    for (const side of [-1, 1]) {
      slots.push(['x', dist, side], ['x', -dist - 6, side], ['z', dist, side], ['z', -dist - 6, side]);
    }
  }
  let farms = 0;
  for (const [axis, off, side] of slots) {
    const r = b.rand();
    if (r < 0.15) continue;
    const big = r > 0.7;
    const w = big ? 7 : 5, d = big ? 6 : 5;
    let x0, z0, door;
    if (axis === 'x') {
      x0 = cx + off; z0 = side > 0 ? cz + 3 : cz - 2 - d;
      door = [x0 + (w >> 1), side > 0 ? z0 : z0 + d - 1];
    } else {
      z0 = cz + off; x0 = side > 0 ? cx + 3 : cx - 2 - w;
      door = [side > 0 ? x0 : x0 + w - 1, z0 + (d >> 1)];
    }
    if (b.h(x0 + (w >> 1), z0 + (d >> 1)) <= SEA) continue;
    if (r < 0.32 && farms < 3 && st.path) { farm(b, x0, z0, 7, 9 > w + 2 ? 7 : 7); farms++; continue; }
    house(b, st, x0, z0, w, d, door, big);
  }
  for (const k of [-20, -10, 10, 20]) { lampPost(b, cx + k, cz + 3, st); lampPost(b, cx - 2, cz + k, st); }
  for (let i = 0; i < 2; i++) b.mobs.push(['villager', cx + 0.5, b.h(cx, cz) + 1, cz + 3.5 + i]);
  return b.done('village');
}

// ---------------- shipwreck ----------------
function buildShipwreck(gen, cx, cz, seed) {
  const c = gen.column(cx, cz);
  if (!['ocean', 'deep_ocean', 'warm_ocean', 'beach'].includes(c.biome) || c.h > SEA - 3) return null;
  const b = new Builder(gen, seed);
  const alongX = b.rand() < 0.5;
  const L = 17, W = 5;
  const floor = c.h + 1;
  const tilt = b.rand() < 0.5 ? 0 : 1;
  const P = (i, j) => (alongX ? [cx + i, cz + j] : [cx + j, cz + i]);
  const plank = b.rand() < 0.5 ? B.spruce_planks : B.oak_planks;
  const broken = (x, y, z) => hash3(x, y, z, seed) < 0.18;
  for (let i = -8; i <= 8; i++) {
    // hull narrows toward bow (+) and stern (-)
    const taper = i > 5 ? i - 5 : i < -6 ? -6 - i : 0;
    const hw = Math.max(0, 2 - taper);
    for (let j = -hw; j <= hw; j++) {
      const [x, z] = P(i, j);
      const y0 = floor + (tilt && j < 0 ? 0 : 0);
      // keel and floor
      if (!broken(x, y0, z)) b.set(x, y0, z, plank);
      for (let y = y0 + 1; y <= y0 + 4; y++) {
        const wall = Math.abs(j) === hw;
        if (wall && y <= y0 + 3) { if (!broken(x, y, z)) b.set(x, y, z, plank); else b.clear(x, y, z); }
        else b.clear(x, y, z);
      }
      // deck
      if (i > -5 && i < 5 && !broken(x, y0 + 3, z) && Math.abs(j) < hw) b.set(x, y0 + 3, z, plank);
    }
  }
  // mast
  const [mx, mz] = P(0, 0);
  for (let y = floor + 1; y < floor + 11; y++) if (!(y > floor + 7 && b.rand() < 0.4)) b.set(mx, y, mz, B.spruce_log);
  // stern cabin with chest
  for (let i = -7; i <= -4; i++)
    for (let j = -1; j <= 1; j++) {
      const [x, z] = P(i, j);
      b.set(x, floor + 6, z, plank);
      if (Math.abs(j) === 1 || i === -4) for (let y = floor + 4; y <= floor + 5; y++) if (!broken(x, y, z)) b.set(x, y, z, plank);
    }
  const [sx, sz] = P(-6, 0);
  b.chest(sx, floor + 1, sz, 'shipwreck');
  const [bx, bz] = P(5, 0);
  b.chest(bx, floor + 1, bz, 'shipwreck');
  return b.done('shipwreck');
}

// ---------------- desert well ----------------
function buildDesertWell(gen, cx, cz, seed) {
  const c = gen.column(cx, cz);
  if (c.biome !== 'desert' || c.h <= SEA) return null;
  const b = new Builder(gen, seed);
  const fy = c.h;
  for (let x = cx - 2; x <= cx + 2; x++)
    for (let z = cz - 2; z <= cz + 2; z++) {
      b.foundation(x, z, fy, B.sandstone);
      b.set(x, fy, z, B.sandstone);
      for (let y = fy + 1; y < fy + 5; y++) b.set(x, y, z, B.air);
      const ring = Math.max(Math.abs(x - cx), Math.abs(z - cz));
      if (ring === 1) b.set(x, fy + 1, z, B.sandstone);
    }
  b.set(cx, fy, cz, B.water); b.set(cx, fy - 1, cz, B.water); b.set(cx, fy - 2, cz, B.sandstone);
  for (const [dx, dz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) { b.set(cx + dx, fy + 2, cz + dz, B.sandstone); b.set(cx + dx, fy + 3, cz + dz, B.sandstone); }
  for (let x = cx - 1; x <= cx + 1; x++) for (let z = cz - 1; z <= cz + 1; z++) b.set(x, fy + 4, z, B.sandstone);
  b.chest(cx, fy - 2, cz + 1, 'well');
  return b.done('desert_well');
}

// ---------------- ruined portal ----------------
function buildRuinedPortal(gen, cx, cz, seed) {
  const c = gen.column(cx, cz);
  if (c.h <= SEA || ['ocean', 'deep_ocean', 'warm_ocean', 'frozen_ocean'].includes(c.biome)) return null;
  const b = new Builder(gen, seed);
  const fy = c.h;
  for (let x = cx - 4; x <= cx + 5; x++)
    for (let z = cz - 3; z <= cz + 3; z++) {
      if (Math.hypot(x - cx - 0.5, z - cz) > 4.5 + b.rand()) continue;
      const r = b.rand();
      b.set(x, b.h(x, z), z, r < 0.55 ? B.netherrack : r < 0.7 ? B.magma_block : B.blackstone);
    }
  for (let i = -1; i <= 2; i++)
    for (let j = -1; j <= 3; j++) {
      const frame = i === -1 || i === 2 || j === -1 || j === 3;
      if (!frame) continue;
      if (b.rand() < 0.3) continue;
      b.set(cx + i, fy + 1 + j, cz, b.rand() < 0.2 ? B.crying_obsidian : B.obsidian);
    }
  b.chest(cx + 3, fy + 1, cz + 1, 'portal');
  return b.done('ruined_portal');
}

// ---------------- dungeon (chunk local, underground) ----------------
function buildDungeon(gen, chunkX, chunkZ, seed) {
  const r = mulberry32(seed);
  if (r() > 0.2) return null;
  const cx = chunkX * CS + 4 + ((r() * 8) | 0), cz = chunkZ * CS + 4 + ((r() * 8) | 0);
  const surface = gen.column(cx, cz).h;
  const y0 = 12 + ((r() * Math.max(1, Math.min(30, surface - 25))) | 0);
  if (y0 > surface - 8) return null;
  const b = new Builder(gen, seed);
  for (let x = cx - 3; x <= cx + 3; x++)
    for (let z = cz - 3; z <= cz + 3; z++)
      for (let y = y0 - 1; y <= y0 + 4; y++) {
        const wall = Math.abs(x - cx) === 3 || Math.abs(z - cz) === 3 || y === y0 - 1 || y === y0 + 4;
        if (wall) b.set(x, y, z, r() < 0.4 ? B.mossy_cobblestone : B.cobblestone);
        else b.set(x, y, z, B.air);
      }
  b.chest(cx - 2, y0, cz, 'dungeon');
  b.chest(cx + 2, y0, cz - 2, 'dungeon');
  b.set(cx - 2, y0 + 3, cz - 2, B.cobweb);
  b.set(cx + 2, y0 + 3, cz + 2, B.cobweb);
  b.set(cx, y0, cz, B.torch);
  return b.done('dungeon');
}

// ---------------- nether fortress ----------------
function buildFortress(gen, cx, cz, seed) {
  const b = new Builder(gen, seed);
  const y = 64;
  const N = B.nether_bricks;
  const arm = 30;
  const corridor = (alongX) => {
    for (let k = -arm; k <= arm; k++)
      for (let w = -2; w <= 2; w++) {
        const x = alongX ? cx + k : cx + w, z = alongX ? cz + w : cz + k;
        b.set(x, y, z, N);
        const edge = Math.abs(w) === 2;
        for (let yy = y + 1; yy <= y + 4; yy++) b.set(x, yy, z, edge && yy === y + 1 ? N : B.air);
        // support pillars every 8 blocks down to the ground
        if (Math.abs(k) % 8 === 0 && edge) for (let yy = y - 1; yy > 32; yy--) b.set(x, yy, z, N);
      }
  };
  corridor(true);
  corridor(false);
  // central room
  for (let x = cx - 5; x <= cx + 5; x++)
    for (let z = cz - 5; z <= cz + 5; z++)
      for (let yy = y; yy <= y + 6; yy++) {
        const wall = Math.abs(x - cx) === 5 || Math.abs(z - cz) === 5;
        const door = (Math.abs(x - cx) <= 1 || Math.abs(z - cz) <= 1) && yy <= y + 3 && yy > y;
        if (yy === y || yy === y + 6) b.set(x, yy, z, N);
        else if (wall && !door) b.set(x, yy, z, N);
        else b.set(x, yy, z, B.air);
      }
  b.chest(cx + 3, y + 1, cz + 3, 'fortress');
  b.chest(cx - 3, y + 1, cz - 3, 'fortress');
  b.set(cx, y + 5, cz, B.glowstone);
  return b.done('fortress');
}

// ---------------- registry ----------------
const TYPES = {
  overworld: [
    { name: 'village', region: 320, extent: 48, build: buildVillage, chance: 0.75 },
    { name: 'shipwreck', region: 112, extent: 14, build: buildShipwreck, chance: 0.6 },
    { name: 'desert_well', region: 128, extent: 4, build: buildDesertWell, chance: 0.5 },
    { name: 'ruined_portal', region: 288, extent: 8, build: buildRuinedPortal, chance: 0.6 },
  ],
  nether: [{ name: 'fortress', region: 352, extent: 40, build: buildFortress, chance: 0.8 }],
};

export class Structures {
  constructor(gen, seed, dim) {
    this.gen = gen;
    this.seed = seed;
    this.dim = dim;
    this.cache = new Map();
  }
  instance(t, rx, rz) {
    const key = t.name + ':' + rx + ',' + rz;
    if (this.cache.has(key)) return this.cache.get(key);
    const s = (this.seed ^ Math.imul(rx, 73856093) ^ Math.imul(rz, 19349663) ^ Math.imul(t.region, 83492791)) >>> 0;
    const r = mulberry32(s);
    let inst = null;
    if (r() < t.chance) {
      const margin = t.extent + 8;
      const cx = rx * t.region + margin + Math.floor(r() * (t.region - margin * 2));
      const cz = rz * t.region + margin + Math.floor(r() * (t.region - margin * 2));
      inst = t.build(this.gen, cx, cz, s);
      if (inst) inst.id = key;
    }
    if (this.cache.size > 200) this.cache.clear();
    this.cache.set(key, inst);
    return inst;
  }
  // structures overlapping a chunk
  forChunk(cx, cz) {
    const x0 = cx * CS, z0 = cz * CS, x1 = x0 + CS - 1, z1 = z0 + CS - 1;
    const out = [];
    for (const t of TYPES[this.dim] || []) {
      for (let rx = Math.floor((x0 - t.extent) / t.region); rx <= Math.floor((x1 + t.extent) / t.region); rx++)
        for (let rz = Math.floor((z0 - t.extent) / t.region); rz <= Math.floor((z1 + t.extent) / t.region); rz++) {
          const inst = this.instance(t, rx, rz);
          if (inst && inst.maxX >= x0 && inst.minX <= x1 && inst.maxZ >= z0 && inst.minZ <= z1) out.push(inst);
        }
    }
    if (this.dim === 'overworld') {
      const d = buildDungeon(this.gen, cx, cz, (this.seed ^ Math.imul(cx, 341873128) ^ Math.imul(cz, 132897987)) >>> 0);
      if (d) { d.id = 'dungeon:' + cx + ',' + cz; out.push(d); }
    }
    return out;
  }
  // write structure blocks into a chunk; returns chests and mobs that fall inside it
  apply(chunk) {
    const x0 = chunk.cx * CS, z0 = chunk.cz * CS;
    const chests = [], mobs = [];
    for (const s of this.forChunk(chunk.cx, chunk.cz)) {
      for (const [x, y, z, id, meta] of s.blocks) {
        const lx = x - x0, lz = z - z0;
        if (lx < 0 || lz < 0 || lx >= CS || lz >= CS) continue;
        const i = (y * CS + lz) * CS + lx;
        chunk.blocks[i] = id;
        chunk.meta[i] = meta;
      }
      for (const c of s.chests) if (c[0] >= x0 && c[0] < x0 + CS && c[2] >= z0 && c[2] < z0 + CS) chests.push(c);
      for (const m of s.mobs) if (m[1] >= x0 && m[1] < x0 + CS && m[3] >= z0 && m[3] < z0 + CS) mobs.push(m);
    }
    chunk.structChests = chests;
    chunk.structMobs = mobs;
  }
  // nearest structure of a type (for debug / locate)
  locate(type, x, z, radius = 2) {
    const t = (TYPES[this.dim] || []).find((tt) => tt.name === type);
    if (!t) return null;
    let best = null, bd = Infinity;
    const rx0 = Math.floor(x / t.region), rz0 = Math.floor(z / t.region);
    for (let rx = rx0 - radius; rx <= rx0 + radius; rx++)
      for (let rz = rz0 - radius; rz <= rz0 + radius; rz++) {
        const s = this.instance(t, rx, rz);
        if (!s) continue;
        const sx = (s.minX + s.maxX) / 2, sz = (s.minZ + s.maxZ) / 2;
        const d = Math.hypot(sx - x, sz - z);
        if (d < bd) { bd = d; best = [Math.round(sx), Math.round(sz)]; }
      }
    return best;
  }
}
