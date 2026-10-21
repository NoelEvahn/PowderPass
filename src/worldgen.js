// Terrain generation for the Overworld and the Nether.
import { Simplex, mulberry32, hash3 } from './noise.js';
import { B } from './blocks.js';
import { Structures } from './structures.js';

export const CS = 16; // chunk size x/z
export const CH = 128; // chunk height
export const SEA = 62;
export const idx = (x, y, z) => (y * CS + z) * CS + x;

export const BIOMES = {
  ocean: { name: 'Ocean' },
  frozen_ocean: { name: 'Frozen Ocean' },
  beach: { name: 'Beach' },
  plains: { name: 'Plains' },
  forest: { name: 'Forest' },
  birch_forest: { name: 'Birch Forest' },
  taiga: { name: 'Snowy Taiga' },
  snowy_plains: { name: 'Snowy Plains' },
  desert: { name: 'Desert' },
  savanna: { name: 'Savanna' },
  jungle: { name: 'Jungle' },
  mountains: { name: 'Mountains' },
  swamp: { name: 'Swamp' },
  nether: { name: 'Nether Wastes' },
  badlands: { name: 'Badlands' },
  mushroom_fields: { name: 'Mushroom Fields' },
  cherry_grove: { name: 'Cherry Grove' },
  dark_forest: { name: 'Dark Forest' },
  ice_spikes: { name: 'Ice Spikes' },
  flower_forest: { name: 'Flower Forest' },
  deep_ocean: { name: 'Deep Ocean' },
  warm_ocean: { name: 'Warm Ocean' },
  crimson_forest: { name: 'Crimson Forest' },
  warped_forest: { name: 'Warped Forest' },
  soul_sand_valley: { name: 'Soul Sand Valley' },
};
export const BIOME_KEYS = Object.keys(BIOMES);

export class OverworldGen {
  constructor(seed) {
    this.seed = seed;
    this.n1 = new Simplex(seed);
    this.n2 = new Simplex(seed + 1);
    this.n3 = new Simplex(seed + 2);
    this.n4 = new Simplex(seed + 3);
    this.n5 = new Simplex(seed + 4);
    this.cave1 = new Simplex(seed + 10);
    this.cave2 = new Simplex(seed + 11);
    this.cave3 = new Simplex(seed + 12);
    this.colCache = new Map();
    this.n6 = new Simplex(seed + 5);
    this.structures = new Structures(this, seed, 'overworld');
  }

  // Continuous climate/height values for a column.
  column(x, z) {
    const key = x * 73856093 ^ z * 19349663;
    const c = this.colCache.get(key);
    if (c && c.x === x && c.z === z) return c;
    const n1 = this.n1, n2 = this.n2;
    // domain warp for organic coastlines
    const wx = x + n2.noise2(x / 200, z / 200) * 40;
    const wz = z + n2.noise2(x / 200 + 50, z / 200 + 50) * 40;
    const cont = n1.fbm2(wx / 700, wz / 700, 4) * 1.25 + 0.12;
    const temp = this.n3.fbm2(x / 900, z / 900, 3) * 1.4;
    const hum = this.n4.fbm2(x / 700 + 300, z / 700 + 300, 3) * 1.4;
    const mnt = Math.max(0, n2.fbm2(x / 380 + 9, z / 380 + 9, 4) * 1.6 - 0.25);
    const ridge = 1 - Math.abs(n1.noise2(x / 140 + 99, z / 140 + 99));
    const hills = n1.fbm2(x / 140, z / 140, 4);
    const detail = n2.noise2(x / 28, z / 28);
    let h;
    if (cont < -0.12) {
      h = SEA - 3 + (cont + 0.12) * 50 + hills * 4;
    } else {
      const land = Math.min(1, (cont + 0.12) * 4);
      h = SEA + 1 + land * (3 + hills * 9 + (cont + 0.12) * 14) + detail * 1.5;
      const m = mnt * land;
      h += m * m * 55 * (0.55 + ridge * 0.55);
      // flatten swamps a bit
      if (hum > 0.45 && temp > -0.2 && temp < 0.45 && m < 0.1) h = SEA + 1 + (h - SEA - 1) * 0.25;
    }
    h = Math.max(4, Math.min(CH - 6, Math.round(h)));
    const v = this.n6.fbm2(x / 420 + 77, z / 420 - 31, 2) * 1.5;
    const biome = this.pickBiome(h, temp, hum, mnt, cont, v);
    if (biome === 'badlands') {
      // terraced mesa plateaus
      const p = Math.max(0, this.n6.fbm2(x / 70, z / 70, 3) * 1.6 + 0.1);
      h += Math.floor((p * 26) / 5) * 5;
      h = Math.min(CH - 8, h);
    } else if (biome === 'mushroom_fields') h = Math.max(h, SEA + 1);
    const out = { x, z, h, temp, hum, biome, mnt };
    if (this.colCache.size > 20000) this.colCache.clear();
    this.colCache.set(key, out);
    return out;
  }

  pickBiome(h, temp, hum, mnt, cont, v = 0) {
    if (h < SEA - 1) {
      if (temp < -0.55) return 'frozen_ocean';
      if (h < SEA - 14) return 'deep_ocean';
      return temp > 0.45 ? 'warm_ocean' : 'ocean';
    }
    if (v > 0.62 && cont < 0.1 && temp > -0.3 && h < SEA + 12) return 'mushroom_fields';
    if (h <= SEA + 1 && cont < 0.0 && temp > -0.4) return temp > 0.5 ? 'desert' : 'beach';
    if (h > 92 || (mnt > 0.35 && h > 80)) return 'mountains';
    if (temp < -0.45) return hum > 0.0 ? 'taiga' : v > 0.35 ? 'ice_spikes' : 'snowy_plains';
    if (temp > 0.45) {
      if (hum < -0.05) return v < -0.35 ? 'badlands' : 'desert';
      if (hum > 0.3) return 'jungle';
      return 'savanna';
    }
    if (hum > 0.45 && h <= SEA + 3) return 'swamp';
    if (h > 76 && v > 0.3) return 'cherry_grove';
    if (hum > 0.15) {
      if (v < -0.4) return 'dark_forest';
      return temp > 0.1 ? (v > 0.4 ? 'flower_forest' : 'forest') : 'birch_forest';
    }
    if (hum < -0.25) return v > 0.45 ? 'flower_forest' : 'plains';
    return hum > -0.05 ? 'forest' : 'plains';
  }

  generate(chunk) {
    const { cx, cz, blocks } = chunk;
    const x0 = cx * CS, z0 = cz * CS;
    const cols = [];
    for (let z = 0; z < CS; z++)
      for (let x = 0; x < CS; x++) {
        const c = this.column(x0 + x, z0 + z);
        cols.push(c);
        chunk.temp[z * CS + x] = c.temp;
        chunk.hum[z * CS + x] = c.hum;
        chunk.biome[z * CS + x] = Object.keys(BIOMES).indexOf(c.biome);
      }

    // Cave density sampled on a coarse grid and trilinearly interpolated.
    const caveGrid = this.caveGrid(x0, z0);

    for (let z = 0; z < CS; z++)
      for (let x = 0; x < CS; x++) {
        const c = cols[z * CS + x];
        const h = c.h;
        const bio = c.biome;
        let top = B.grass, filler = B.dirt, depth = 3 + ((hash3(x0 + x, 0, z0 + z, this.seed) * 2) | 0);
        if (bio === 'desert') { top = B.sand; filler = B.sand; depth = 4; }
        else if (bio === 'beach') { top = B.sand; filler = B.sand; }
        else if (bio === 'ocean' || bio === 'frozen_ocean') { top = h < SEA - 8 ? B.gravel : B.sand; filler = top === B.gravel ? B.gravel : B.sand; if (hash3(x0 + x, 1, z0 + z, this.seed) < 0.06) top = B.clay; }
        else if (bio === 'snowy_plains' || bio === 'taiga') { top = B.snowy_grass; }
        else if (bio === 'ice_spikes') { top = B.snow; filler = B.dirt; }
        else if (bio === 'mushroom_fields') { top = B.mycelium; }
        else if (bio === 'deep_ocean') { top = B.gravel; filler = B.gravel; }
        else if (bio === 'warm_ocean') { top = B.sand; filler = B.sand; }
        else if (bio === 'badlands') { top = B.red_sand; filler = B.red_sand; depth = 1; }
        else if (bio === 'mountains') {
          top = h > 100 ? B.snow : h > 88 ? B.stone : B.grass;
          if (c.mnt > 0.5 && h > 84 && h <= 100 && this.n5.noise2((x0 + x) / 20, (z0 + z) / 20) > 0.2) top = B.gravel;
          filler = top === B.grass ? B.dirt : B.stone;
        } else if (bio === 'savanna' && this.n5.noise2((x0 + x) / 30, (z0 + z) / 30) > 0.55) { top = B.red_sand; filler = B.red_sand; }
        for (let y = 0; y <= Math.max(h, SEA); y++) {
          let id;
          if (y === 0) id = B.bedrock;
          else if (y < 4 && hash3(x0 + x, y, z0 + z, this.seed) < 0.6 - y * 0.15) id = B.bedrock;
          else if (y > h) id = y <= SEA ? (bio === 'frozen_ocean' && y === SEA ? B.ice : B.water) : B.air;
          else if (y === h) id = h < SEA ? (top === B.grass || top === B.snowy_grass ? B.dirt : top) : top;
          else if (y > h - depth) id = filler;
          else if (bio === 'desert' && y > h - depth - 3) id = B.sandstone;
          else if (bio === 'badlands' && y > SEA - 4) id = MESA_BANDS[(y + ((this.n6.noise2((x0 + x) / 90, (z0 + z) / 90) * 3) | 0) + 64) % MESA_BANDS.length];
          else id = B.stone;
          // caves
          if (id !== B.bedrock && id !== B.water && id !== B.air && id !== B.ice && y < h + 1 && y > 3) {
            const surfaceGuard = h < SEA + 2 ? h - 6 : h + 1;
            if (y < surfaceGuard && this.isCave(caveGrid, x, y, z)) id = y < 11 ? B.lava : B.air;
          }
          blocks[idx(x, y, z)] = id;
        }
      }

    this.ores(chunk);
    this.decorate(chunk, cols);
    this.structures.apply(chunk);
  }

  caveGrid(x0, z0) {
    const nx = 5, ny = CH / 4 + 1;
    const g = new Float32Array(nx * nx * ny * 2);
    let i = 0;
    for (let gy = 0; gy < ny; gy++)
      for (let gz = 0; gz < nx; gz++)
        for (let gx = 0; gx < nx; gx++) {
          const wx = x0 + gx * 4, wy = gy * 4, wz = z0 + gz * 4;
          // spaghetti tunnels: intersection of two noise "zero sheets"
          const a = this.cave1.noise3(wx / 48, wy / 32, wz / 48);
          const b = this.cave2.noise3(wx / 48, wy / 32, wz / 48);
          g[i++] = a * a + b * b;
          // cheese caverns
          g[i++] = this.cave3.noise3(wx / 70, wy / 36, wz / 70);
        }
    return g;
  }

  isCave(g, x, y, z) {
    const fx = x / 4, fy = y / 4, fz = z / 4;
    const ix = Math.min(3, fx | 0), iy = Math.min(CH / 4 - 1, fy | 0), iz = Math.min(3, fz | 0);
    const tx = fx - ix, ty = fy - iy, tz = fz - iz;
    const at = (gx, gy, gz, k) => g[((gy * 5 + gz) * 5 + gx) * 2 + k];
    const lerp = (a, b, t) => a + (b - a) * t;
    const sample = (k) => {
      const c00 = lerp(at(ix, iy, iz, k), at(ix + 1, iy, iz, k), tx);
      const c10 = lerp(at(ix, iy + 1, iz, k), at(ix + 1, iy + 1, iz, k), tx);
      const c01 = lerp(at(ix, iy, iz + 1, k), at(ix + 1, iy, iz + 1, k), tx);
      const c11 = lerp(at(ix, iy + 1, iz + 1, k), at(ix + 1, iy + 1, iz + 1, k), tx);
      return lerp(lerp(c00, c10, ty), lerp(c01, c11, ty), tz);
    };
    if (sample(0) < 0.012 + (y < 30 ? 0.006 : 0)) return true;
    const cheese = sample(1);
    return y < 50 && cheese > 0.62 - (50 - y) * 0.004;
  }

  ores(chunk) {
    const rand = mulberry32(this.seed ^ (chunk.cx * 341873128712 + chunk.cz * 132897987541));
    const vein = (id, count, size, ymin, ymax, replace = B.stone) => {
      for (let i = 0; i < count; i++) {
        let x = 1 + rand() * 14, y = ymin + rand() * (ymax - ymin), z = 1 + rand() * 14;
        for (let k = 0; k < size; k++) {
          const xi = x | 0, yi = y | 0, zi = z | 0;
          if (xi >= 0 && xi < CS && zi >= 0 && zi < CS && yi > 0 && yi < CH) {
            const j = idx(xi, yi, zi);
            if (chunk.blocks[j] === replace) chunk.blocks[j] = id;
          }
          x += rand() * 2 - 1; y += rand() * 2 - 1; z += rand() * 2 - 1;
        }
      }
    };
    vein(B.coal_ore, 14, 12, 5, 110);
    vein(B.iron_ore, 10, 8, 5, 64);
    vein(B.gold_ore, 3, 7, 5, 32);
    vein(B.diamond_ore, 1.6, 6, 5, 16);
    vein(B.emerald_ore, 2, 2, 50, 120);
    vein(B.gravel, 5, 20, 5, 100);
    vein(B.dirt, 5, 20, 5, 100);
  }

  decorate(chunk, cols) {
    const x0 = chunk.cx * CS, z0 = chunk.cz * CS;
    const set = (x, y, z, id, onlyAir = true) => {
      const lx = x - x0, lz = z - z0;
      if (lx < 0 || lz < 0 || lx >= CS || lz >= CS || y < 0 || y >= CH) return;
      const j = idx(lx, y, lz);
      const cur = chunk.blocks[j];
      if (onlyAir && cur !== B.air && cur !== B.tall_grass && !(id !== B.oak_leaves && cur >= 0 && isLeaf(cur))) return;
      chunk.blocks[j] = id;
    };
    // Trees can overlap from neighbouring columns (margin 3).
    for (let z = z0 - 3; z < z0 + CS + 3; z++)
      for (let x = x0 - 3; x < x0 + CS + 3; x++) {
        const c = this.column(x, z);
        if (c.h < SEA + 1) continue;
        const r = hash3(x, 7, z, this.seed);
        const tree = treeFor(c.biome, r, hash3(x, 8, z, this.seed));
        if (!tree) continue;
        // ground must be grass/dirt type in our generator (caves keep surface intact)
        const r2 = hash3(x, 9, z, this.seed);
        placeTree(tree, x, c.h + 1, z, r2, set);
      }

    // Ground cover inside the chunk
    for (let lz = 0; lz < CS; lz++)
      for (let lx = 0; lx < CS; lx++) {
        const c = cols[lz * CS + lx];
        const x = x0 + lx, z = z0 + lz;
        const y = c.h + 1;
        if (y >= CH) continue;
        const ground = chunk.blocks[idx(lx, c.h, lz)];
        const above = chunk.blocks[idx(lx, y, lz)];
        const r = hash3(x, 3, z, this.seed);
        if (c.biome === 'swamp' && c.h === SEA && above === B.air) {
          // shallow water pools
        }
        if (above !== B.air) {
          if (above === B.water && chunk.blocks[idx(lx, Math.min(CH - 1, SEA + 1), lz)] === B.air && c.biome === 'swamp' && r < 0.08 && SEA + 1 < CH) {
            chunk.blocks[idx(lx, SEA + 1, lz)] = B.lily_pad;
          }
          continue;
        }
        if (ground === B.grass) {
          const flowerNoise = this.n5.noise2(x / 40, z / 40);
          const grassChance = c.biome === 'plains' ? 0.3 : c.biome === 'jungle' ? 0.45 : c.biome === 'savanna' ? 0.35 : c.biome === 'swamp' ? 0.2 : 0.12;
          if (r < grassChance) chunk.blocks[idx(lx, y, lz)] = B.tall_grass;
          else if (c.biome === 'flower_forest' && r < grassChance + 0.3) chunk.blocks[idx(lx, y, lz)] = [B.dandelion, B.poppy, B.red_tulip, B.cornflower, B.allium][(hash3(x, 4, z, this.seed) * 5) | 0];
          else if (r < grassChance + 0.03 + Math.max(0, flowerNoise) * 0.06) chunk.blocks[idx(lx, y, lz)] = [B.dandelion, B.poppy, B.dandelion, B.poppy, B.cornflower, B.red_tulip][(hash3(x, 4, z, this.seed) * 6) | 0];
          else if (c.biome === 'swamp' && r < grassChance + 0.06) chunk.blocks[idx(lx, y, lz)] = r < grassChance + 0.045 ? B.brown_mushroom : B.red_mushroom;
          else if (r > 0.9993 && flowerNoise < -0.3) chunk.blocks[idx(lx, y, lz)] = B.pumpkin;
          // sugar cane next to water
          if (c.h === SEA && r > 0.9 && r < 0.96 && nearWater(chunk, lx, c.h, lz)) {
            const hgt = 1 + ((hash3(x, 5, z, this.seed) * 3) | 0);
            for (let k = 0; k < hgt && y + k < CH; k++) chunk.blocks[idx(lx, y + k, lz)] = B.sugar_cane;
          }
        } else if (ground === B.sand && c.biome === 'desert') {
          if (r < 0.006 && lx > 0 && lx < 15 && lz > 0 && lz < 15) {
            const hgt = 1 + ((hash3(x, 5, z, this.seed) * 3) | 0);
            for (let k = 0; k < hgt && y + k < CH; k++) chunk.blocks[idx(lx, y + k, lz)] = B.cactus;
          } else if (r < 0.015) chunk.blocks[idx(lx, y, lz)] = B.dead_bush;
        } else if (ground === B.snowy_grass && r < 0.05) {
          chunk.blocks[idx(lx, y, lz)] = B.tall_grass;
        } else if (ground === B.red_sand && r < 0.02) chunk.blocks[idx(lx, y, lz)] = B.dead_bush;
        else if (ground === B.red_sand && c.biome === 'badlands' && r > 0.995 && lx > 0 && lx < 15 && lz > 0 && lz < 15) { chunk.blocks[idx(lx, y, lz)] = B.cactus; }
        else if (ground === B.mycelium && r < 0.02) chunk.blocks[idx(lx, y, lz)] = r < 0.01 ? B.red_mushroom : B.brown_mushroom;
      }
  }
}

const MESA_BANDS = [B.terracotta, B.orange_terracotta, B.terracotta, B.yellow_terracotta, B.terracotta, B.terracotta, B.red_terracotta, B.white_terracotta, B.orange_terracotta, B.brown_terracotta, B.terracotta, B.red_terracotta];

function isLeaf(id) {
  return id === B.oak_leaves || id === B.birch_leaves || id === B.spruce_leaves || id === B.jungle_leaves || id === B.acacia_leaves || id === B.cherry_leaves || id === B.dark_oak_leaves;
}

function nearWater(chunk, lx, y, lz) {
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    const x = lx + dx, z = lz + dz;
    if (x < 0 || z < 0 || x >= CS || z >= CS) continue;
    if (chunk.blocks[idx(x, y, z)] === B.water) return true;
  }
  return false;
}

function treeFor(biome, r, r2) {
  switch (biome) {
    case 'forest': return r < 0.05 ? (r2 < 0.25 ? 'birch' : r2 < 0.3 ? 'big_oak' : 'oak') : null;
    case 'birch_forest': return r < 0.05 ? (r2 < 0.85 ? 'birch' : 'oak') : null;
    case 'plains': return r < 0.0025 ? 'oak' : null;
    case 'taiga': return r < 0.035 ? 'spruce' : null;
    case 'snowy_plains': return r < 0.003 ? 'spruce' : null;
    case 'jungle': return r < 0.07 ? (r2 < 0.2 ? 'jungle_big' : r2 < 0.65 ? 'jungle' : 'bush') : null;
    case 'savanna': return r < 0.006 ? 'acacia' : null;
    case 'swamp': return r < 0.015 ? 'swamp_oak' : null;
    case 'mountains': return r < 0.006 ? 'spruce' : null;
    case 'cherry_grove': return r < 0.012 ? 'cherry' : null;
    case 'dark_forest': return r < 0.09 ? (r2 < 0.07 ? (r2 < 0.035 ? 'huge_red_mushroom' : 'huge_brown_mushroom') : r2 < 0.85 ? 'dark_oak' : 'oak') : null;
    case 'flower_forest': return r < 0.025 ? (r2 < 0.3 ? 'birch' : 'oak') : null;
    case 'mushroom_fields': return r < 0.006 ? (r2 < 0.5 ? 'huge_red_mushroom' : 'huge_brown_mushroom') : null;
    case 'ice_spikes': return r < 0.014 ? 'ice_spike' : null;
    default: return null;
  }
}

function placeTree(type, x, y, z, r, set) {
  const leafBall = (cx, cy, cz, rad, leaf, cut = 0.6) => {
    for (let dy = -rad; dy <= rad; dy++)
      for (let dz = -rad; dz <= rad; dz++)
        for (let dx = -rad; dx <= rad; dx++) {
          const d = dx * dx + dy * dy * 1.4 + dz * dz;
          if (d <= rad * rad + 0.5 && hash3(cx + dx, cy + dy, cz + dz, 99) < (d > rad * rad - 1.5 ? cut : 1)) set(cx + dx, cy + dy, cz + dz, leaf);
        }
  };
  if (type === 'oak' || type === 'swamp_oak' || type === 'big_oak') {
    const h = (type === 'big_oak' ? 6 : 4) + ((r * 3) | 0);
    const top = y + h;
    for (let dy = -2; dy <= 1; dy++) {
      const rad = dy >= 0 ? 1 : 2;
      for (let dz = -rad; dz <= rad; dz++)
        for (let dx = -rad; dx <= rad; dx++) {
          if (Math.abs(dx) === rad && Math.abs(dz) === rad && (dy >= 0 || hash3(x + dx, top + dy, z + dz, 5) < 0.5)) continue;
          set(x + dx, top + dy, z + dz, B.oak_leaves);
        }
    }
    if (type === 'swamp_oak') leafBall(x, top - 1, z, 3, B.oak_leaves, 0.4);
    if (type === 'big_oak') { leafBall(x, top - 1, z, 3, B.oak_leaves, 0.5); leafBall(x + 2, top - 3, z + 1, 2, B.oak_leaves); }
    for (let k = 0; k < h; k++) set(x, y + k, z, B.oak_log, false);
  } else if (type === 'birch') {
    const h = 5 + ((r * 3) | 0);
    const top = y + h;
    for (let dy = -2; dy <= 1; dy++) {
      const rad = dy >= 0 ? 1 : 2;
      for (let dz = -rad; dz <= rad; dz++)
        for (let dx = -rad; dx <= rad; dx++) {
          if (Math.abs(dx) === rad && Math.abs(dz) === rad) continue;
          set(x + dx, top + dy, z + dz, B.birch_leaves);
        }
    }
    for (let k = 0; k < h; k++) set(x, y + k, z, B.birch_log, false);
  } else if (type === 'spruce') {
    const h = 6 + ((r * 5) | 0);
    const pattern = [0, 1, 1, 2, 1, 2, 3, 2, 3, 2, 3];
    for (let k = h, p = 0; k >= 2; k--, p++) {
      const rad = pattern[Math.min(p, pattern.length - 1)];
      for (let dz = -rad; dz <= rad; dz++)
        for (let dx = -rad; dx <= rad; dx++) {
          if (rad > 0 && Math.abs(dx) === rad && Math.abs(dz) === rad) continue;
          set(x + dx, y + k, z + dz, B.spruce_leaves);
        }
    }
    set(x, y + h + 1, z, B.spruce_leaves);
    for (let k = 0; k < h; k++) set(x, y + k, z, B.spruce_log, false);
  } else if (type === 'jungle' || type === 'jungle_big') {
    const h = (type === 'jungle_big' ? 14 : 7) + ((r * 5) | 0);
    leafBall(x, y + h, z, type === 'jungle_big' ? 4 : 2, B.jungle_leaves, 0.5);
    for (let k = 0; k < h; k++) {
      set(x, y + k, z, B.jungle_log, false);
      if (type === 'jungle_big') { set(x + 1, y + k, z, B.jungle_log, false); set(x, y + k, z + 1, B.jungle_log, false); set(x + 1, y + k, z + 1, B.jungle_log, false); }
    }
  } else if (type === 'bush') {
    set(x, y, z, B.jungle_log, false);
    leafBall(x, y + 1, z, 2, B.oak_leaves, 0.4);
  } else if (type === 'cherry') {
    const h = 5 + ((r * 2) | 0);
    for (let k = 0; k < h; k++) set(x, y + k, z, B.cherry_log, false);
    for (const [dx, dz] of [[2, 0], [-2, 1]]) { set(x + dx / 2, y + h - 2, z + dz / 2, B.cherry_log, false); }
    leafBall(x, y + h, z, 3, B.cherry_leaves, 0.5);
    leafBall(x + 2, y + h - 1, z + 1, 2, B.cherry_leaves, 0.5);
    leafBall(x - 2, y + h - 1, z - 1, 2, B.cherry_leaves, 0.5);
  } else if (type === 'dark_oak') {
    const h = 6 + ((r * 3) | 0);
    for (let k = 0; k < h; k++) for (const [dx, dz] of [[0, 0], [1, 0], [0, 1], [1, 1]]) set(x + dx, y + k, z + dz, B.dark_oak_log, false);
    for (let dy = -1; dy <= 1; dy++) {
      const rad = dy === 1 ? 2 : 3;
      for (let dz = -rad; dz <= rad + 1; dz++)
        for (let dx = -rad; dx <= rad + 1; dx++) {
          if ((dx === -rad || dx === rad + 1) && (dz === -rad || dz === rad + 1)) continue;
          set(x + dx, y + h + dy, z + dz, B.dark_oak_leaves);
        }
    }
  } else if (type === 'huge_red_mushroom' || type === 'huge_brown_mushroom') {
    const h = 5 + ((r * 3) | 0);
    for (let k = 0; k < h; k++) set(x, y + k, z, B.mushroom_stem, false);
    if (type === 'huge_brown_mushroom') {
      for (let dz = -3; dz <= 3; dz++) for (let dx = -3; dx <= 3; dx++) if (!(Math.abs(dx) === 3 && Math.abs(dz) === 3)) set(x + dx, y + h, z + dz, B.brown_mushroom_block);
    } else {
      for (let dy = 0; dy <= 3; dy++) {
        const rad = dy === 3 ? 1 : 2;
        for (let dz = -rad; dz <= rad; dz++) for (let dx = -rad; dx <= rad; dx++) {
          const shell = dy === 3 || Math.abs(dx) === rad || Math.abs(dz) === rad;
          if (shell && !(Math.abs(dx) === rad && Math.abs(dz) === rad && dy < 3)) set(x + dx, y + h - 3 + dy, z + dz, B.red_mushroom_block);
        }
      }
    }
  } else if (type === 'ice_spike') {
    const h = 6 + ((r * 18) | 0);
    for (let k = 0; k < h; k++) {
      const rad = Math.max(0, Math.round((1 - k / h) * 2.2));
      for (let dz = -rad; dz <= rad; dz++) for (let dx = -rad; dx <= rad; dx++) if (dx * dx + dz * dz <= rad * rad + 0.5) set(x + dx, y + k, z + dz, B.blue_ice, false);
    }
  } else if (type === 'acacia') {
    const h = 5 + ((r * 2) | 0);
    const dir = [[1, 0], [-1, 0], [0, 1], [0, -1]][(r * 4) | 0];
    let cx = x, cz = z;
    for (let k = 0; k < h; k++) {
      if (k >= 3) { cx += dir[0]; cz += dir[1]; }
      set(cx, y + k, cz, B.acacia_log, false);
    }
    const top = y + h;
    for (let dz = -3; dz <= 3; dz++)
      for (let dx = -3; dx <= 3; dx++) {
        if (Math.abs(dx) + Math.abs(dz) > 4) continue;
        set(cx + dx, top, cz + dz, B.acacia_leaves);
        if (Math.abs(dx) + Math.abs(dz) <= 2) set(cx + dx, top + 1, cz + dz, B.acacia_leaves);
      }
  }
}

// ---------------- Nether ----------------
export class NetherGen {
  constructor(seed) {
    this.seed = seed;
    this.n = new Simplex(seed + 100);
    this.n2 = new Simplex(seed + 101);
    this.n3 = new Simplex(seed + 102);
    this.nb = new Simplex(seed + 103);
    this.structures = new Structures(this, seed, 'nether');
  }
  netherBiome(x, z) {
    const a = this.nb.fbm2(x / 220, z / 220, 2), b = this.nb.fbm2(x / 220 + 400, z / 220 + 400, 2);
    if (a > 0.25) return b > 0 ? 'crimson_forest' : 'warped_forest';
    if (a < -0.3) return 'soul_sand_valley';
    return 'nether';
  }
  column(x = 0, z = 0) {
    return { h: 64, biome: this.netherBiome(x, z), temp: 2, hum: -1 };
  }
  generate(chunk) {
    const { cx, cz, blocks } = chunk;
    const x0 = cx * CS, z0 = cz * CS;
    const nbio = [];
    for (let z = 0; z < CS; z++) for (let x = 0; x < CS; x++) { const nbb = this.netherBiome(x0 + x, z0 + z); nbio.push(nbb); chunk.biome[z * CS + x] = BIOME_KEYS.indexOf(nbb); }
    chunk.temp.fill(2);
    chunk.hum.fill(-1);
    const LAVA = 31;
    // density grid
    const nx = 5, ny = CH / 4 + 1;
    const g = new Float32Array(nx * nx * ny);
    let i = 0;
    for (let gy = 0; gy < ny; gy++)
      for (let gz = 0; gz < nx; gz++)
        for (let gx = 0; gx < nx; gx++) {
          const wx = x0 + gx * 4, wy = gy * 4, wz = z0 + gz * 4;
          let d = this.n.noise3(wx / 60, wy / 30, wz / 60) * 0.7 + this.n2.noise3(wx / 22, wy / 16, wz / 22) * 0.3;
          // solid floor and ceiling
          if (wy < 28) d += (28 - wy) * 0.06;
          if (wy > 96) d += (wy - 96) * 0.07;
          g[i++] = d;
        }
    const at = (gx, gy, gz) => g[(gy * 5 + gz) * 5 + gx];
    for (let z = 0; z < CS; z++)
      for (let x = 0; x < CS; x++)
        for (let y = 0; y < CH; y++) {
          let id;
          if (y === 0 || y === CH - 1) id = B.bedrock;
          else if ((y < 4 || y > CH - 5) && hash3(x0 + x, y, z0 + z, this.seed) < 0.5) id = B.bedrock;
          else {
            const fx = x / 4, fy = y / 4, fz = z / 4;
            const ix = Math.min(3, fx | 0), iy = Math.min(ny - 2, fy | 0), iz = Math.min(3, fz | 0);
            const tx = fx - ix, ty = fy - iy, tz = fz - iz;
            const l = (a, b, t) => a + (b - a) * t;
            const d = l(
              l(l(at(ix, iy, iz), at(ix + 1, iy, iz), tx), l(at(ix, iy + 1, iz), at(ix + 1, iy + 1, iz), tx), ty),
              l(l(at(ix, iy, iz + 1), at(ix + 1, iy, iz + 1), tx), l(at(ix, iy + 1, iz + 1), at(ix + 1, iy + 1, iz + 1), tx), ty),
              tz,
            );
            if (d > 0.08) id = B.netherrack;
            else id = y <= LAVA ? B.lava : B.air;
          }
          blocks[idx(x, y, z)] = id;
        }
    // surface variation: soul sand near lava level, gravel
    for (let z = 0; z < CS; z++)
      for (let x = 0; x < CS; x++) {
        const sn = this.n3.noise2((x0 + x) / 24, (z0 + z) / 24);
        for (let y = LAVA - 4; y < LAVA + 6; y++) {
          const j = idx(x, y, z);
          if (blocks[j] === B.netherrack && blocks[idx(x, y + 1, z)] === B.air) {
            if (sn > 0.35) { blocks[j] = B.soul_sand; if (y > 1) blocks[idx(x, y - 1, z)] = B.soul_sand; }
            else if (sn < -0.55) blocks[j] = B.gravel;
          }
          if (blocks[j] === B.netherrack && blocks[idx(x, y + 1, z)] === B.lava && hash3(x0 + x, y, z0 + z, 3) < 0.15) blocks[j] = B.magma_block;
        }
      }
    const rand = mulberry32(this.seed ^ (cx * 73428767 + cz * 912931));
    // quartz
    for (let v = 0; v < 12; v++) {
      let x = 1 + rand() * 14, y = 10 + rand() * 108, z = 1 + rand() * 14;
      for (let k = 0; k < 6; k++) {
        const j = idx(x | 0, y | 0, z | 0);
        if (blocks[j] === B.netherrack) blocks[j] = B.nether_quartz_ore;
        x = Math.max(0, Math.min(15, x + rand() * 2 - 1)); y += rand() * 2 - 1; z = Math.max(0, Math.min(15, z + rand() * 2 - 1));
      }
    }
    // glowstone clusters hanging from ceilings
    for (let v = 0; v < 3; v++) {
      const x = 3 + ((rand() * 10) | 0), z = 3 + ((rand() * 10) | 0);
      for (let y = CH - 6; y > 40; y--) {
        if (blocks[idx(x, y, z)] === B.air && blocks[idx(x, y + 1, z)] === B.netherrack) {
          for (let k = 0; k < 30; k++) {
            const gx = x + Math.round((rand() - 0.5) * 4), gy = y - ((rand() * 4) | 0), gz = z + Math.round((rand() - 0.5) * 4);
            if (gx >= 0 && gx < CS && gz >= 0 && gz < CS && gy > 0) {
              const j = idx(gx, gy, gz);
              if (blocks[j] === B.air) blocks[j] = B.glowstone;
            }
          }
          break;
        }
      }
    }
    // mushrooms
    for (let v = 0; v < 4; v++) {
      const x = (rand() * 16) | 0, z = (rand() * 16) | 0;
      for (let y = LAVA + 1; y < 110; y++) {
        if (blocks[idx(x, y, z)] === B.air && blocks[idx(x, y - 1, z)] === B.netherrack) {
          blocks[idx(x, y, z)] = rand() < 0.5 ? B.red_mushroom : B.brown_mushroom;
          break;
        }
      }
    }
    // nether gold ore
    for (let v = 0; v < 8; v++) {
      const x = 1 + ((rand() * 14) | 0), y = 10 + ((rand() * 108) | 0), z = 1 + ((rand() * 14) | 0);
      for (let k = 0; k < 4; k++) { const j = idx(Math.min(15, x + (k & 1)), y + (k >> 1), z); if (blocks[j] === B.netherrack) blocks[j] = B.gold_ore_nether; }
    }
    // biome surfaces
    for (let z = 0; z < CS; z++)
      for (let x = 0; x < CS; x++) {
        const bio = nbio[z * CS + x];
        if (bio === 'nether') continue;
        for (let y = LAVA; y < CH - 6; y++) {
          const j = idx(x, y, z);
          if (blocks[j] !== B.netherrack || blocks[idx(x, y + 1, z)] !== B.air) continue;
          const r = hash3(x0 + x, y, z0 + z, this.seed + 7);
          if (bio === 'soul_sand_valley') {
            blocks[j] = r < 0.5 ? B.soul_sand : B.soul_soil;
            if (y > 1) blocks[idx(x, y - 1, z)] = B.soul_soil;
            if (r > 0.993 && x > 1 && x < 14 && z > 1 && z < 14) {
              const ph = 4 + ((r * 1000) % 14);
              for (let k = 1; k <= ph && y + k < CH - 5; k++) if (blocks[idx(x, y + k, z)] === B.air) blocks[idx(x, y + k, z)] = B.basalt;
            }
          } else {
            const crimson = bio === 'crimson_forest';
            blocks[j] = crimson ? B.crimson_nylium : B.warped_nylium;
            const above = idx(x, y + 1, z);
            if (r > 0.985 && x > 2 && x < 13 && z > 2 && z < 13) {
              // huge fungus
              const fh = 5 + ((r * 1000) % 6) | 0;
              let ok = true;
              for (let k = 1; k <= fh + 3; k++) if (y + k >= CH - 5 || blocks[idx(x, y + k, z)] !== B.air) ok = false;
              if (ok) {
                for (let k = 1; k <= fh; k++) blocks[idx(x, y + k, z)] = crimson ? B.crimson_stem : B.warped_stem;
                for (let dy = -2; dy <= 1; dy++) {
                  const rad = dy === 1 ? 1 : 2;
                  for (let dz = -rad; dz <= rad; dz++)
                    for (let dx = -rad; dx <= rad; dx++) {
                      const jj = idx(x + dx, y + fh + dy, z + dz);
                      if (blocks[jj] !== B.air) continue;
                      if (dy < 1 && Math.abs(dx) < rad && Math.abs(dz) < rad) continue;
                      blocks[jj] = hash3(x0 + x + dx, y + dy, z0 + z + dz, 5) < 0.08 ? B.shroomlight : crimson ? B.nether_wart_block : B.warped_wart_block;
                    }
                }
              }
            } else if (r > 0.9) blocks[above] = crimson ? B.crimson_fungus : B.warped_fungus;
          }
        }
      }
    this.structures.apply(chunk);
  }
}
