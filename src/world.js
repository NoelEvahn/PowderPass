// Chunk storage, lighting (BFS sky + block light), fluids, block updates, saving.
import { BLOCKS, B } from './blocks.js';
import { CS, CH, idx, OverworldGen, NetherGen } from './worldgen.js';

export const FALLING = 8; // meta flag for falling fluid

export class Chunk {
  constructor(cx, cz) {
    this.cx = cx;
    this.cz = cz;
    this.blocks = new Uint8Array(CS * CS * CH);
    this.meta = new Uint8Array(CS * CS * CH); // fluid level / facing
    this.light = new Uint8Array(CS * CS * CH); // sky<<4 | block
    this.temp = new Float32Array(CS * CS);
    this.hum = new Float32Array(CS * CS);
    this.biome = new Uint8Array(CS * CS);
    this.lit = false;
    this.dirty = true;
    this.modified = false;
    this.mesh = null;
  }
}

// Light opacity: how much a block reduces light passing through (15 = opaque).
const OPACITY = new Uint8Array(256);
const EMIT = new Uint8Array(256);
const ATTEN = new Uint8Array(256); // extra light loss per block during spread
for (const b of BLOCKS) {
  if (!b) continue;
  OPACITY[b.id] = b.opaque ? 15 : b.liquid || b.key.includes('leaves') || b.key === 'ice' ? 1 : 0;
  ATTEN[b.id] = b.opaque ? 15 : b.key.includes('leaves') ? 1 : 0;
  EMIT[b.id] = b.light;
}
export { OPACITY, EMIT };

const DIRS = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]];

export class World {
  constructor(seed, dim, store) {
    this.seed = seed;
    this.dim = dim; // 'overworld' | 'nether'
    this.gen = dim === 'nether' ? new NetherGen(seed) : new OverworldGen(seed);
    this.hasSky = dim !== 'nether';
    this.chunks = new Map();
    this.store = store; // saved chunk data (key -> {blocks, meta})
    this.tick = 0;
    this.fluidQueue = new Map(); // key -> due tick
    this.updateQueue = []; // positions needing neighbour update (gravity etc)
    this.tileEntities = store.tiles || (store.tiles = {});
    this.listeners = { blockChange: [] };
    this.lightQ = [];
    this.removeQ = [];
  }

  key(cx, cz) { return cx + ',' + cz; }
  getChunk(cx, cz) { return this.chunks.get(cx + ',' + cz); }

  ensureChunk(cx, cz) {
    let c = this.getChunk(cx, cz);
    if (c) return c;
    c = new Chunk(cx, cz);
    const saved = this.store.chunks[this.dim + ':' + cx + ',' + cz];
    this.gen.generate(c);
    if (saved) {
      c.blocks.set(saved.blocks);
      c.meta.set(saved.meta);
      c.modified = true;
    }
    this.chunks.set(this.key(cx, cz), c);
    return c;
  }

  // Compute lighting for a freshly generated chunk and blend with neighbours.
  lightChunk(c) {
    if (c.lit) return;
    c.lit = true;
    const x0 = c.cx * CS, z0 = c.cz * CS;
    const q = this.lightQ;
    if (this.hasSky) {
      const heights = new Int16Array(CS * CS);
      for (let z = 0; z < CS; z++)
        for (let x = 0; x < CS; x++) {
          let y = CH - 1;
          for (; y >= 0; y--) {
            const id = c.blocks[idx(x, y, z)];
            if (OPACITY[id] > 0) break;
            c.light[idx(x, y, z)] = 0xf0;
          }
          heights[z * CS + x] = y;
        }
      // seed horizontal spread where neighbouring columns are lower-lit
      for (let z = 0; z < CS; z++)
        for (let x = 0; x < CS; x++) {
          const h = heights[z * CS + x];
          let maxN = h;
          for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
            const nx = x + dx, nz = z + dz;
            if (nx >= 0 && nz >= 0 && nx < CS && nz < CS) maxN = Math.max(maxN, heights[nz * CS + nx]);
            else maxN = Math.max(maxN, h + 1);
          }
          for (let y = h + 1; y <= Math.min(CH - 1, maxN + 1); y++) q.push(x0 + x, y, z0 + z, 1);
        }
    }
    // block light sources
    for (let y = 0; y < CH; y++)
      for (let z = 0; z < CS; z++)
        for (let x = 0; x < CS; x++) {
          const i = idx(x, y, z);
          const e = EMIT[c.blocks[i]];
          if (e) {
            c.light[i] = (c.light[i] & 0xf0) | e;
            q.push(x0 + x, y, z0 + z, 0);
          }
        }
    // seed from neighbour borders
    const nb = [[-1, 0], [1, 0], [0, -1], [0, 1]];
    for (const [dx, dz] of nb) {
      const n = this.getChunk(c.cx + dx, c.cz + dz);
      if (!n || !n.lit) continue;
      for (let y = 0; y < CH; y++)
        for (let k = 0; k < CS; k++) {
          const lx = dx === -1 ? CS - 1 : dx === 1 ? 0 : k;
          const lz = dz === -1 ? CS - 1 : dz === 1 ? 0 : k;
          const l = n.light[idx(lx, y, lz)];
          if (l === 0) continue;
          const wx = (c.cx + dx) * CS + lx, wz = (c.cz + dz) * CS + lz;
          if (l >> 4) q.push(wx, y, wz, 1);
          if (l & 15) q.push(wx, y, wz, 0);
        }
    }
    this.propagate();
  }

  getLightRaw(x, y, z) {
    if (y < 0) return 0;
    if (y >= CH) return 0xf0;
    const c = this.getChunk(x >> 4, z >> 4);
    if (!c) return 0;
    return c.light[idx(x & 15, y, z & 15)];
  }

  setLight(c, i, val) {
    if (c.light[i] !== val) {
      c.light[i] = val;
      c.dirty = true;
      return true;
    }
    return false;
  }

  // BFS propagate light queue entries [x,y,z,channel]. channel 1 = sky, 0 = block.
  propagate() {
    const q = this.lightQ;
    let head = 0;
    while (head < q.length) {
      const x = q[head++], y = q[head++], z = q[head++], ch = q[head++];
      const c = this.getChunk(x >> 4, z >> 4);
      if (!c) continue;
      const lv = ch ? c.light[idx(x & 15, y, z & 15)] >> 4 : c.light[idx(x & 15, y, z & 15)] & 15;
      if (lv <= 1) continue;
      for (let d = 0; d < 6; d++) {
        const nx = x + DIRS[d][0], ny = y + DIRS[d][1], nz = z + DIRS[d][2];
        if (ny < 0 || ny >= CH) continue;
        const nc = (nx >> 4) === c.cx && (nz >> 4) === c.cz ? c : this.getChunk(nx >> 4, nz >> 4);
        if (!nc) continue;
        const ni = idx(nx & 15, ny, nz & 15);
        const nid = nc.blocks[ni];
        const op = OPACITY[nid];
        if (op >= 15) continue;
        let nl = lv - 1 - ATTEN[nid];
        if (ch && d === 3 && lv === 15 && op === 0) nl = 15; // sky light falls without loss
        const cur = nc.light[ni];
        const curL = ch ? cur >> 4 : cur & 15;
        if (nl > curL) {
          nc.light[ni] = ch ? (cur & 15) | (nl << 4) : (cur & 0xf0) | nl;
          this.markDirtyAt(nc, nx & 15, nz & 15);
          q.push(nx, ny, nz, ch);
        }
      }
    }
    q.length = 0;
  }

  // Remove light starting at a position, then repropagate from the boundary.
  removeLight(x, y, z, ch) {
    const rq = this.removeQ;
    const c = this.getChunk(x >> 4, z >> 4);
    if (!c) return;
    const i0 = idx(x & 15, y, z & 15);
    const start = ch ? c.light[i0] >> 4 : c.light[i0] & 15;
    c.light[i0] = ch ? c.light[i0] & 15 : c.light[i0] & 0xf0;
    rq.push(x, y, z, start);
    let head = 0;
    while (head < rq.length) {
      const px = rq[head++], py = rq[head++], pz = rq[head++], lv = rq[head++];
      for (let d = 0; d < 6; d++) {
        const nx = px + DIRS[d][0], ny = py + DIRS[d][1], nz = pz + DIRS[d][2];
        if (ny < 0 || ny >= CH) continue;
        const nc = this.getChunk(nx >> 4, nz >> 4);
        if (!nc) continue;
        const ni = idx(nx & 15, ny, nz & 15);
        const cur = nc.light[ni];
        const nl = ch ? cur >> 4 : cur & 15;
        if (nl === 0) continue;
        if (nl < lv || (ch && d === 3 && lv === 15 && nl === 15)) {
          nc.light[ni] = ch ? cur & 15 : cur & 0xf0;
          this.markDirtyAt(nc, nx & 15, nz & 15);
          rq.push(nx, ny, nz, nl);
        } else {
          this.lightQ.push(nx, ny, nz, ch);
        }
      }
    }
    rq.length = 0;
  }

  markDirtyAt(c, lx, lz) {
    c.dirty = true;
    if (lx === 0) this.markDirty(c.cx - 1, c.cz);
    if (lx === 15) this.markDirty(c.cx + 1, c.cz);
    if (lz === 0) this.markDirty(c.cx, c.cz - 1);
    if (lz === 15) this.markDirty(c.cx, c.cz + 1);
  }
  markDirty(cx, cz) {
    const c = this.getChunk(cx, cz);
    if (c) c.dirty = true;
  }

  getBlock(x, y, z) {
    if (y < 0 || y >= CH) return 0;
    const c = this.getChunk(x >> 4, z >> 4);
    if (!c) return y < 0 ? B.bedrock : 0;
    return c.blocks[idx(x & 15, y, z & 15)];
  }
  getMeta(x, y, z) {
    if (y < 0 || y >= CH) return 0;
    const c = this.getChunk(x >> 4, z >> 4);
    if (!c) return 0;
    return c.meta[idx(x & 15, y, z & 15)];
  }
  isLoaded(x, z) { return !!this.getChunk(x >> 4, z >> 4); }

  // Set a block with full side effects (light, fluids, neighbours, save tracking).
  setBlock(x, y, z, id, meta = 0, opts = {}) {
    if (y < 0 || y >= CH) return false;
    const c = this.getChunk(x >> 4, z >> 4);
    if (!c) return false;
    const i = idx(x & 15, y, z & 15);
    const old = c.blocks[i];
    const oldMeta = c.meta[i];
    if (old === id && oldMeta === meta) return false;
    c.blocks[i] = id;
    c.meta[i] = meta;
    c.modified = true;
    this.markDirtyAt(c, x & 15, z & 15);
    if (c.lit && (OPACITY[old] !== OPACITY[id] || EMIT[old] !== EMIT[id])) this.relightAt(x, y, z, c, i, id);
    if (old !== id && this.tileEntities[this.dim + ':' + x + ',' + y + ',' + z] && !opts.keepTile) {
      if (!(BLOCKS[old].interact && BLOCKS[id].interact === BLOCKS[old].interact)) {
        const te = this.tileEntities[this.dim + ':' + x + ',' + y + ',' + z];
        for (const l of this.listeners.blockChange) l({ type: 'tileRemoved', x, y, z, te });
        delete this.tileEntities[this.dim + ':' + x + ',' + y + ',' + z];
      }
    }
    if (!opts.silent) {
      this.notifyNeighbors(x, y, z);
      for (const l of this.listeners.blockChange) l({ type: 'set', x, y, z, old, id });
    }
    return true;
  }

  relightAt(x, y, z, c, i, id) {
    if (this.hasSky) this.removeLight(x, y, z, 1);
    this.removeLight(x, y, z, 0);
    const e = EMIT[id];
    if (e) {
      c.light[i] = (c.light[i] & 0xf0) | e;
      this.lightQ.push(x, y, z, 0);
    }
    // neighbours pour light back in
    for (const [dx, dy, dz] of DIRS) {
      const ny = y + dy;
      if (ny < 0 || ny >= CH) continue;
      if (this.hasSky) this.lightQ.push(x + dx, ny, z + dz, 1);
      this.lightQ.push(x + dx, ny, z + dz, 0);
    }
    if (this.hasSky && y === CH - 1) {
      // top of world opened to sky
      if (OPACITY[id] < 15) { c.light[i] = (c.light[i] & 15) | 0xf0; this.lightQ.push(x, y, z, 1); }
    }
    this.propagate();
  }

  notifyNeighbors(x, y, z) {
    this.scheduleFluid(x, y, z);
    for (const [dx, dy, dz] of DIRS) {
      this.scheduleFluid(x + dx, y + dy, z + dz);
      this.updateQueue.push(x + dx, y + dy, z + dz);
    }
    this.updateQueue.push(x, y, z);
  }

  scheduleFluid(x, y, z, delay) {
    const id = this.getBlock(x, y, z);
    const b = BLOCKS[id];
    if (!b || !b.liquid) return;
    const rate = delay ?? (b.liquid === 'water' ? 5 : this.dim === 'nether' ? 10 : 30);
    const k = x + ',' + y + ',' + z;
    const due = this.tick + rate;
    const cur = this.fluidQueue.get(k);
    if (cur === undefined || cur > due) this.fluidQueue.set(k, due);
  }

  // ---- Fluid simulation (Minecraft-like levels: 0 source, 1..7 flowing, +8 falling) ----
  fluidTick() {
    const due = [];
    for (const [k, t] of this.fluidQueue) {
      if (t <= this.tick) due.push(k);
      if (due.length > 1500) break;
    }
    for (const k of due) {
      this.fluidQueue.delete(k);
      const [x, y, z] = k.split(',').map(Number);
      this.updateFluid(x, y, z);
    }
  }

  isFluidReplaceable(id, fluid) {
    const b = BLOCKS[id];
    if (id === 0) return true;
    if (b.liquid) return false;
    if (b.render === 'cross' || b.render === 'torch' || b.render === 'flat') return !(fluid === 'water' && id === B.lily_pad);
    return false;
  }

  updateFluid(x, y, z) {
    if (!this.isLoaded(x, z)) return;
    const id = this.getBlock(x, y, z);
    const b = BLOCKS[id];
    if (!b.liquid) return;
    const fluid = b.liquid;
    const drop = fluid === 'water' ? 1 : this.dim === 'nether' ? 1 : 2;
    const maxSpread = fluid === 'water' ? 4 : this.dim === 'nether' ? 4 : 2;
    let meta = this.getMeta(x, y, z);
    const isSource = meta === 0;

    // Lava meeting water
    if (fluid === 'lava') {
      for (const [dx, dy, dz] of DIRS) {
        if (dy === -1) continue;
        if (BLOCKS[this.getBlock(x + dx, y + dy, z + dz)].liquid === 'water') {
          this.setBlock(x, y, z, isSource ? B.obsidian : B.cobblestone);
          this.emit('fizz', x, y, z);
          return;
        }
      }
    }

    if (!isSource) {
      // recompute level from neighbours
      const above = this.getBlock(x, y + 1, z);
      let newMeta;
      if (BLOCKS[above].liquid === fluid) newMeta = FALLING;
      else {
        let best = 99;
        let sources = 0;
        for (let d = 0; d < 4; d++) {
          const [dx, , dz] = DIRS[d === 0 ? 0 : d === 1 ? 1 : d === 2 ? 4 : 5];
          const nid = this.getBlock(x + dx, y, z + dz);
          if (BLOCKS[nid].liquid !== fluid) continue;
          const nm = this.getMeta(x + dx, y, z + dz);
          if (nm === 0) sources++;
          const lvl = nm >= FALLING ? 0 : nm;
          best = Math.min(best, lvl + drop);
        }
        newMeta = best;
        if (fluid === 'water' && sources >= 2) {
          const below = this.getBlock(x, y - 1, z);
          if (BLOCKS[below].solid || (below === B.water && this.getMeta(x, y - 1, z) === 0)) newMeta = 0;
        }
      }
      if (newMeta > 7 && newMeta !== FALLING) {
        this.setBlock(x, y, z, 0);
        return;
      }
      if (newMeta !== meta) {
        meta = newMeta;
        this.setBlock(x, y, z, id, meta);
        return; // setBlock re-schedules neighbours; spreading happens next tick
      }
    }

    // Flow down
    const belowId = this.getBlock(x, y - 1, z);
    const belowB = BLOCKS[belowId];
    if (y > 0 && (this.isFluidReplaceable(belowId, fluid) || (belowB.liquid === fluid && this.getMeta(x, y - 1, z) !== 0 && this.getMeta(x, y - 1, z) !== FALLING))) {
      this.breakForFluid(x, y - 1, z, belowId);
      this.setBlock(x, y - 1, z, id, FALLING);
      if (!isSource) return;
    } else if (y > 0 && belowB.liquid && belowB.liquid !== fluid) {
      if (fluid === 'water') {
        this.setBlock(x, y - 1, z, this.getMeta(x, y - 1, z) === 0 ? B.obsidian : B.cobblestone);
      } else {
        this.setBlock(x, y - 1, z, B.stone);
      }
      this.emit('fizz', x, y - 1, z);
      return;
    }
    if (belowB.liquid === fluid && !isSource) return;

    // Spread horizontally
    const lvl = meta >= FALLING ? 0 : meta;
    const next = lvl + drop;
    if (next > 7) return;
    const dirs = this.flowDirections(x, y, z, fluid, maxSpread);
    for (const [dx, dz] of dirs) {
      const nx = x + dx, nz = z + dz;
      const nid = this.getBlock(nx, y, nz);
      const nb = BLOCKS[nid];
      if (this.isFluidReplaceable(nid, fluid)) {
        this.breakForFluid(nx, y, nz, nid);
        this.setBlock(nx, y, nz, id, next);
      } else if (nb.liquid === fluid) {
        const nm = this.getMeta(nx, y, nz);
        if (nm !== 0 && nm < FALLING && nm > next) this.setBlock(nx, y, nz, id, next);
      } else if (nb.liquid && nb.liquid !== fluid) {
        const nm = this.getMeta(nx, y, nz);
        if (fluid === 'water') this.setBlock(nx, y, nz, nm === 0 ? B.obsidian : B.cobblestone);
        else this.setBlock(nx, y, nz, B.cobblestone);
        this.emit('fizz', nx, y, nz);
      }
    }
  }

  breakForFluid(x, y, z, id) {
    if (id && BLOCKS[id].render !== 'none') {
      for (const l of this.listeners.blockChange) l({ type: 'fluidBreak', x, y, z, id });
    }
  }

  // Prefer directions leading to the nearest drop (like Minecraft).
  flowDirections(x, y, z, fluid, maxDist) {
    const H = [[1, 0], [-1, 0], [0, 1], [0, -1]];
    let best = 1000;
    const res = [];
    for (const [dx, dz] of H) {
      const nid = this.getBlock(x + dx, y, z + dz);
      if (!this.isFluidReplaceable(nid, fluid) && BLOCKS[nid].liquid !== fluid) continue;
      if (BLOCKS[nid].liquid === fluid && this.getMeta(x + dx, y, z + dz) === 0) continue;
      const d = this.distanceToDrop(x + dx, y, z + dz, fluid, maxDist, -dx, -dz, 1);
      if (d < best) { best = d; res.length = 0; }
      if (d === best) res.push([dx, dz]);
    }
    return res;
  }

  distanceToDrop(x, y, z, fluid, maxDist, fromX, fromZ, depth) {
    const below = this.getBlock(x, y - 1, z);
    if (this.isFluidReplaceable(below, fluid) || BLOCKS[below].liquid === fluid) return depth - 1;
    if (depth >= maxDist) return 1000;
    let best = 1000;
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      if (dx === fromX && dz === fromZ) continue;
      const nid = this.getBlock(x + dx, y, z + dz);
      if (!this.isFluidReplaceable(nid, fluid) && BLOCKS[nid].liquid !== fluid) continue;
      best = Math.min(best, this.distanceToDrop(x + dx, y, z + dz, fluid, maxDist, -dx, -dz, depth + 1));
    }
    return best;
  }

  // Fluid surface height (0..1) at a cell, for meshing and physics.
  fluidHeight(x, y, z, fluid) {
    const id = this.getBlock(x, y, z);
    if (BLOCKS[id].liquid !== fluid) return -1;
    if (BLOCKS[this.getBlock(x, y + 1, z)].liquid === fluid) return 1;
    const m = this.getMeta(x, y, z);
    if (m >= FALLING) return 0.89;
    return ((8 - m) / 9) * 1.0;
  }

  emit(type, x, y, z) {
    for (const l of this.listeners.blockChange) l({ type, x, y, z });
  }

  // Generic neighbour updates: gravity blocks, unsupported plants/torches.
  processUpdates(spawnFalling) {
    const q = this.updateQueue;
    const n = Math.min(q.length, 3000);
    const items = q.splice(0, n);
    for (let i = 0; i < items.length; i += 3) {
      const x = items[i], y = items[i + 1], z = items[i + 2];
      if (y < 0 || y >= CH || !this.isLoaded(x, z)) continue;
      const id = this.getBlock(x, y, z);
      if (!id) continue;
      const b = BLOCKS[id];
      const below = this.getBlock(x, y - 1, z);
      const bb = BLOCKS[below];
      if (b.gravity && (below === 0 || bb.liquid || bb.render === 'cross')) {
        this.setBlock(x, y, z, 0);
        spawnFalling(x, y, z, id);
      } else if ((b.render === 'cross' || b.render === 'flat') && !this.supportsPlant(id, below)) {
        this.setBlock(x, y, z, 0);
        for (const l of this.listeners.blockChange) l({ type: 'drop', x, y, z, id });
      } else if (b.render === 'torch' && !bb.solid) {
        this.setBlock(x, y, z, 0);
        for (const l of this.listeners.blockChange) l({ type: 'drop', x, y, z, id });
      } else if (id === B.cactus && below !== B.cactus && below !== B.sand && below !== B.red_sand) {
        this.setBlock(x, y, z, 0);
        for (const l of this.listeners.blockChange) l({ type: 'drop', x, y, z, id });
      } else if (id === B.nether_portal) {
        // portal breaks if frame is broken
        const ok = (d) => { const n2 = this.getBlock(x + d[0], y + d[1], z + d[2]); return n2 === B.obsidian || n2 === B.nether_portal; };
        const axisX = this.getMeta(x, y, z) === 0;
        const dirs = axisX ? [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0]] : [[0, 0, 1], [0, 0, -1], [0, 1, 0], [0, -1, 0]];
        if (!dirs.every(ok)) this.setBlock(x, y, z, 0);
      }
    }
  }

  supportsPlant(id, below) {
    if (id === B.lily_pad) return below === B.water;
    if (id === B.sugar_cane) return below === B.sugar_cane || below === B.grass || below === B.dirt || below === B.sand;
    if (id === B.dead_bush) return below === B.sand || below === B.red_sand || below === B.dirt || below === B.grass;
    if (id === B.red_mushroom || id === B.brown_mushroom) return BLOCKS[below].solid && BLOCKS[below].opaque;
    return below === B.grass || below === B.dirt || below === B.snowy_grass;
  }

  // ---------- Nether portal ----------
  // Try to light a portal at (x,y,z) (an air block inside an obsidian frame).
  tryLightPortal(x, y, z) {
    for (const axisX of [true, false]) {
      const frame = this.findPortalFrame(x, y, z, axisX);
      if (frame) {
        for (const [px, py, pz] of frame) this.setBlock(px, py, pz, B.nether_portal, axisX ? 0 : 1, { silent: true });
        for (const [px, py, pz] of frame) this.markDirtyAt(this.getChunk(px >> 4, pz >> 4), px & 15, pz & 15);
        return true;
      }
    }
    return false;
  }

  findPortalFrame(x, y, z, axisX) {
    const dx = axisX ? 1 : 0, dz = axisX ? 0 : 1;
    // move to bottom-left interior corner
    let bx = x, by = y, bz = z;
    let guard = 0;
    while (this.getBlock(bx, by - 1, bz) === 0 && guard++ < 22) by--;
    if (this.getBlock(bx, by - 1, bz) !== B.obsidian) return null;
    guard = 0;
    while (this.getBlock(bx - dx, by, bz - dz) === 0 && guard++ < 22) { bx -= dx; bz -= dz; }
    if (this.getBlock(bx - dx, by, bz - dz) !== B.obsidian) return null;
    let w = 0;
    while (this.getBlock(bx + dx * w, by, bz + dz * w) === 0 && w < 22) w++;
    let h = 0;
    while (this.getBlock(bx, by + h, bz) === 0 && h < 22) h++;
    if (w < 2 || h < 3) return null;
    const cells = [];
    for (let i = 0; i < w; i++) {
      if (this.getBlock(bx + dx * i, by - 1, bz + dz * i) !== B.obsidian) return null;
      if (this.getBlock(bx + dx * i, by + h, bz + dz * i) !== B.obsidian) return null;
      for (let j = 0; j < h; j++) {
        if (this.getBlock(bx + dx * i, by + j, bz + dz * i) !== 0) return null;
        cells.push([bx + dx * i, by + j, bz + dz * i]);
      }
    }
    for (let j = 0; j < h; j++) {
      if (this.getBlock(bx - dx, by + j, bz - dz) !== B.obsidian) return null;
      if (this.getBlock(bx + dx * w, by + j, bz + dz * w) !== B.obsidian) return null;
    }
    return cells;
  }

  // Build a portal (with frame) at a location, used on arrival in a dimension.
  buildPortal(x, y, z) {
    for (let i = -1; i <= 2; i++)
      for (let j = -1; j <= 3; j++) {
        const frame = i === -1 || i === 2 || j === -1 || j === 3;
        this.setBlock(x + i, y + j, z, frame ? B.obsidian : B.nether_portal, 0, { silent: true });
      }
    // platform and clearance
    for (let i = -2; i <= 3; i++)
      for (let k = -2; k <= 2; k++) {
        if (k === 0 && i >= -1 && i <= 2) continue;
        for (let j = 0; j <= 3; j++) this.setBlock(x + i, y + j, z + k, 0, 0, { silent: true });
        const fy = y - 1;
        if (!BLOCKS[this.getBlock(x + i, fy, z + k)].solid) this.setBlock(x + i, fy, z + k, this.dim === 'nether' ? B.netherrack : B.obsidian, 0, { silent: true });
      }
  }

  findPortalNear(x, y, z, r = 24) {
    let best = null, bd = 1e9;
    for (let cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++)
      for (let cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) {
        const c = this.getChunk(cx, cz);
        if (!c) continue;
        for (let i = 0; i < c.blocks.length; i++) {
          if (c.blocks[i] !== B.nether_portal) continue;
          const lx = i & 15, lz = (i >> 4) & 15, ly = i >> 8;
          const wx = cx * 16 + lx, wz = cz * 16 + lz;
          if (this.getBlock(wx, ly - 1, wz) === B.nether_portal) continue;
          const d = (wx - x) ** 2 + (ly - y) ** 2 + (wz - z) ** 2;
          if (d < bd) { bd = d; best = [wx, ly, wz]; }
        }
      }
    return best;
  }

  // Find a safe standing Y near a column (for portals / spawn).
  findSafeY(x, z, preferY) {
    const ok = (y) => {
      const b0 = BLOCKS[this.getBlock(x, y - 1, z)];
      return b0.solid && !b0.liquid && this.getBlock(x, y, z) === 0 && this.getBlock(x, y + 1, z) === 0 && this.getBlock(x, y + 2, z) === 0;
    };
    for (let d = 0; d < CH; d++) {
      for (const y of [preferY + d, preferY - d]) if (y > 1 && y < CH - 3 && ok(y)) return y;
    }
    return -1;
  }

  // ---------- persistence ----------
  saveModified() {
    for (const c of this.chunks.values()) {
      if (c.modified) {
        this.store.chunks[this.dim + ':' + c.cx + ',' + c.cz] = { blocks: c.blocks.slice(), meta: c.meta.slice(), dirtySave: true };
      }
    }
  }

  unloadChunk(c) {
    if (c.modified) this.store.chunks[this.dim + ':' + c.cx + ',' + c.cz] = { blocks: c.blocks.slice(), meta: c.meta.slice(), dirtySave: true };
    this.chunks.delete(this.key(c.cx, c.cz));
  }
}
