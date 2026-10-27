import type { RoomData, Vec3 } from '@doors/shared';
import { floorAt } from '@doors/shared';

/** Grid navigation inside a single room (used by Figure). */
export class NavGrid {
  readonly cell = 0.4;
  readonly nx: number;
  readonly nz: number;
  readonly ox: number;
  readonly oz: number;
  readonly free: Uint8Array;
  readonly room: RoomData;

  constructor(room: RoomData, radius = 0.42, extraBlocked: { minX: number; maxX: number; minZ: number; maxZ: number }[] = []) {
    this.room = room;
    const w = room.walkable.reduce((a, b) => ((b.maxX - b.minX) * (b.maxZ - b.minZ) > (a.maxX - a.minX) * (a.maxZ - a.minZ) ? b : a));
    this.ox = w.minX; this.oz = w.minZ;
    this.nx = Math.ceil((w.maxX - w.minX) / this.cell);
    this.nz = Math.ceil((w.maxZ - w.minZ) / this.cell);
    this.free = new Uint8Array(this.nx * this.nz);
    for (let ix = 0; ix < this.nx; ix++) for (let iz = 0; iz < this.nz; iz++) {
      const x = this.ox + (ix + 0.5) * this.cell, z = this.oz + (iz + 0.5) * this.cell;
      const fy = floorAt(room, x, z);
      let ok = true;
      for (const b of [...room.colliders, ...room.crouchColliders]) {
        if (x + radius > b.minX && x - radius < b.maxX && z + radius > b.minZ && z - radius < b.maxZ && fy + 1.8 > b.minY && fy + 0.3 < b.maxY) { ok = false; break; }
      }
      if (ok) for (const b of extraBlocked) if (x + radius > b.minX && x - radius < b.maxX && z + radius > b.minZ && z - radius < b.maxZ) { ok = false; break; }
      this.free[ix * this.nz + iz] = ok ? 1 : 0;
    }
  }

  idx(x: number, z: number): number {
    const ix = Math.floor((x - this.ox) / this.cell), iz = Math.floor((z - this.oz) / this.cell);
    if (ix < 0 || iz < 0 || ix >= this.nx || iz >= this.nz) return -1;
    return ix * this.nz + iz;
  }
  center(k: number): Vec3 {
    const ix = Math.floor(k / this.nz), iz = k % this.nz;
    const x = this.ox + (ix + 0.5) * this.cell, z = this.oz + (iz + 0.5) * this.cell;
    return { x, y: floorAt(this.room, x, z), z };
  }
  /** Nearest free cell to a point (spiral search). */
  nearestFree(x: number, z: number): number {
    const k = this.idx(x, z);
    if (k >= 0 && this.free[k]) return k;
    const cx = Math.floor((x - this.ox) / this.cell), cz = Math.floor((z - this.oz) / this.cell);
    for (let r = 1; r < 12; r++) {
      let best = -1, bd = Infinity;
      for (let dx = -r; dx <= r; dx++) for (let dz = -r; dz <= r; dz++) {
        if (Math.max(Math.abs(dx), Math.abs(dz)) !== r) continue;
        const ix = cx + dx, iz = cz + dz;
        if (ix < 0 || iz < 0 || ix >= this.nx || iz >= this.nz) continue;
        const kk = ix * this.nz + iz;
        if (!this.free[kk]) continue;
        const d = dx * dx + dz * dz;
        if (d < bd) { bd = d; best = kk; }
      }
      if (best >= 0) return best;
    }
    return -1;
  }
  randomFree(rnd: () => number): Vec3 | null {
    for (let a = 0; a < 200; a++) {
      const k = Math.floor(rnd() * this.free.length);
      if (this.free[k]) return this.center(k);
    }
    return null;
  }

  /** A* with 8-neighbourhood, returns smoothed world waypoints (excluding start). */
  findPath(from: Vec3, to: Vec3, maxNodes = 6000): Vec3[] | null {
    const s = this.nearestFree(from.x, from.z), g = this.nearestFree(to.x, to.z);
    if (s < 0 || g < 0) return null;
    if (s === g) return [this.center(g)];
    const nz = this.nz;
    const gx = Math.floor(g / nz), gz = g % nz;
    const h = (k: number) => { const dx = Math.abs(Math.floor(k / nz) - gx), dz = Math.abs((k % nz) - gz); return Math.max(dx, dz) + 0.414 * Math.min(dx, dz); };
    const gScore = new Map<number, number>([[s, 0]]);
    const came = new Map<number, number>();
    const heap = new MinHeap();
    heap.push(h(s), s);
    const closed = new Set<number>();
    let n = 0;
    while (heap.size && n++ < maxNodes) {
      const cur = heap.pop();
      if (cur === g) break;
      if (closed.has(cur)) continue;
      closed.add(cur);
      const cx = Math.floor(cur / nz), cz = cur % nz;
      for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
        if (!dx && !dz) continue;
        const ix = cx + dx, iz = cz + dz;
        if (ix < 0 || iz < 0 || ix >= this.nx || iz >= nz) continue;
        const k = ix * nz + iz;
        if (!this.free[k] || closed.has(k)) continue;
        if (dx && dz && (!this.free[(cx + dx) * nz + cz] || !this.free[cx * nz + cz + dz])) continue;
        const ng = gScore.get(cur)! + (dx && dz ? 1.414 : 1);
        if (ng < (gScore.get(k) ?? Infinity)) {
          gScore.set(k, ng); came.set(k, cur); heap.push(ng + h(k), k);
        }
      }
    }
    if (!came.has(g)) return null;
    const cells: number[] = [g];
    let c = g;
    while (came.has(c)) { c = came.get(c)!; cells.push(c); }
    cells.reverse();
    // string-pull smoothing
    const out: Vec3[] = [];
    let anchor = 0;
    for (let i = 2; i < cells.length; i++) {
      if (!this.clear(cells[anchor], cells[i])) { out.push(this.center(cells[i - 1])); anchor = i - 1; }
    }
    out.push(this.center(g));
    return out;
  }

  clear(a: number, b: number): boolean {
    const pa = this.center(a), pb = this.center(b);
    const d = Math.hypot(pb.x - pa.x, pb.z - pa.z);
    const steps = Math.ceil(d / (this.cell * 0.5));
    for (let i = 1; i < steps; i++) {
      const t = i / steps;
      const k = this.idx(pa.x + (pb.x - pa.x) * t, pa.z + (pb.z - pa.z) * t);
      if (k < 0 || !this.free[k]) return false;
    }
    return true;
  }
}

/** Binary min-heap keyed by priority. */
class MinHeap {
  private p: number[] = [];
  private v: number[] = [];
  get size() { return this.v.length; }
  push(pri: number, val: number) {
    const p = this.p, v = this.v;
    let i = v.length;
    p.push(pri); v.push(val);
    while (i > 0) {
      const par = (i - 1) >> 1;
      if (p[par] <= p[i]) break;
      [p[par], p[i]] = [p[i], p[par]]; [v[par], v[i]] = [v[i], v[par]];
      i = par;
    }
  }
  pop(): number {
    const p = this.p, v = this.v;
    const top = v[0];
    const lp = p.pop()!, lv = v.pop()!;
    if (v.length) {
      p[0] = lp; v[0] = lv;
      let i = 0;
      for (;;) {
        const l = 2 * i + 1, r = l + 1;
        let m = i;
        if (l < v.length && p[l] < p[m]) m = l;
        if (r < v.length && p[r] < p[m]) m = r;
        if (m === i) break;
        [p[m], p[i]] = [p[i], p[m]]; [v[m], v[i]] = [v[i], v[m]];
        i = m;
      }
    }
    return top;
  }
}
