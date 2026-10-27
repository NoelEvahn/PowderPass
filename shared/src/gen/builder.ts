import type { Box, Vec3 } from '../math';
import { rotXZ } from '../math';
import type { Rng } from '../rng';
import type { Interactable, LightData, PropData, PropType, RoomData, InteractKind, Loot } from '../types';
import { WALL_T } from '../constants';

/** Local 2D rectangle in the room frame. */
export interface Rect { x0: number; z0: number; x1: number; z1: number }

/** Default footprints (w along local x, d along local z, h height) per prop type; null = no collision. */
export const FOOTPRINT: Partial<Record<PropType, [number, number, number] | null>> = {
  bed: [1.6, 2.2, 0.75], closet: [1.3, 0.75, 2.3], dresser: [1.4, 0.55, 1.0], desk: [1.5, 0.75, 0.8],
  nightstand: [0.5, 0.45, 0.6], bookshelf: [1.6, 0.45, 2.4], table: [1.2, 1.2, 0.78], diningTable: [3.2, 1.3, 0.78],
  chair: [0.5, 0.5, 1.0], sofa: [2.1, 0.9, 0.9], armchair: [0.95, 0.9, 1.0], plant: [0.6, 0.6, 1.4],
  crate: [1.0, 1.0, 1.0], barrel: [0.7, 0.7, 1.0], piano: [1.6, 0.7, 1.3], clock: [0.55, 0.4, 2.1],
  shelf: [1.8, 0.5, 2.0], cabinet: [1.0, 0.5, 1.9], sink: [0.7, 0.5, 0.9], toilet: [0.45, 0.7, 0.8],
  bathtub: [0.8, 1.8, 0.6], counter: [2.4, 0.65, 0.95], pillar: [0.6, 0.6, 3], fountain: [3.4, 3.4, 1.2],
  hedge: [3, 0.9, 2], tree: [0.8, 0.8, 4], bench: [1.6, 0.55, 0.5], planter: [2.6, 0.9, 0.8], statue: [0.9, 0.9, 2.2],
  receptionDesk: [4.5, 1.0, 1.1], boxStack: [1.2, 1.2, 1.6], shopCounter: [3.6, 0.9, 1.05], medCabinet: [1.0, 0.45, 1.8],
  gurney: [0.9, 2.0, 0.85], locker: [0.9, 0.6, 2.0], stove: [0.9, 0.7, 0.95], fridge: [0.9, 0.75, 1.9],
  pallet: [1.2, 1.2, 0.3], generator: [1.6, 1.0, 1.3], electricPanel: [1.4, 0.4, 2.0], fireplace: [2.0, 0.6, 1.6],
  breakerBox: [1.4, 0.35, 1.6], padlockDesk: [1.0, 0.6, 1.0], gazebo: null, wheelbarrow: [0.7, 1.4, 0.7],
  woodPile: [1.4, 0.8, 0.8], pot: [0.7, 0.7, 0.9], bookPile: [0.6, 0.6, 0.5], cart: [0.7, 1.2, 1.0],
  bigShelf: [3.6, 1.1, 4.2], elevator: null, jeff: [0.6, 0.6, 1.9], debris: [1.0, 1.0, 0.4], skeleton: null,
};


export class RoomBuilder {
  readonly room: RoomData;
  readonly rng: Rng;
  occupied: Rect[] = [];
  keepOut: Rect[] = [];
  private propN = 0;
  /** z of the cross lane for north exits */
  laneMid?: number;

  constructor(room: RoomData, rng: Rng) {
    this.room = room;
    this.rng = rng;
  }

  /** Interior bounds in local coords. */
  get iz0() { return WALL_T; }
  get iz1() { return WALL_T + this.room.depth; }
  get ix0() { return this.room.xMin; }
  get ix1() { return this.room.xMax; }
  get w() { return this.room.xMax - this.room.xMin; }
  get cx() { return (this.room.xMin + this.room.xMax) / 2; }
  get cz() { return WALL_T + this.room.depth / 2; }

  /** Floor height at a local z (handles stair rooms). */
  floorAt(lz: number): number {
    const st = this.room.tags.find((t) => t.startsWith('stair:'));
    if (!st) return 0;
    const [a, b] = st.slice(6).split(',').map(Number);
    if (lz <= a) return 0;
    if (lz >= b) return this.room.rise;
    return ((lz - a) / (b - a)) * this.room.rise;
  }

  toWorld(lx: number, ly: number, lz: number): Vec3 {
    const [x, z] = rotXZ(lx, lz, this.room.rot);
    return { x: this.room.origin.x + x, y: this.room.origin.y + ly, z: this.room.origin.z + z };
  }

  rectToWorldBox(r: Rect, y0: number, y1: number): Box {
    const a = rotXZ(r.x0, r.z0, this.room.rot);
    const b = rotXZ(r.x1, r.z1, this.room.rot);
    const o = this.room.origin;
    return {
      minX: o.x + Math.min(a[0], b[0]), maxX: o.x + Math.max(a[0], b[0]),
      minY: o.y + y0, maxY: o.y + y1,
      minZ: o.z + Math.min(a[1], b[1]), maxZ: o.z + Math.max(a[1], b[1]),
    };
  }

  addCollider(r: Rect, y0: number, y1: number, crouch = false) {
    const b = this.rectToWorldBox(r, y0, y1);
    (crouch ? this.room.crouchColliders : this.room.colliders).push(b);
    return b;
  }

  /** Footprint rect of a prop centred at (x,z) with rotation. */
  static rectFor(x: number, z: number, w: number, d: number, rot: number): Rect {
    const sw = rot & 1 ? d : w, sd = rot & 1 ? w : d;
    return { x0: x - sw / 2, z0: z - sd / 2, x1: x + sw / 2, z1: z + sd / 2 };
  }

  static overlaps(a: Rect, b: Rect, pad = 0) {
    return a.x0 < b.x1 + pad && a.x1 > b.x0 - pad && a.z0 < b.z1 + pad && a.z1 > b.z0 - pad;
  }

  insideInterior(r: Rect, margin = 0.02) {
    return r.x0 >= this.ix0 + margin && r.x1 <= this.ix1 - margin && r.z0 >= this.iz0 + margin && r.z1 <= this.iz1 - margin;
  }

  canPlace(r: Rect, pad = 0.05, ignoreKeepOut = false) {
    if (!this.insideInterior(r)) return false;
    for (const o of this.occupied) if (RoomBuilder.overlaps(r, o, pad)) return false;
    if (!ignoreKeepOut) for (const k of this.keepOut) if (RoomBuilder.overlaps(r, k, 0)) return false;
    return true;
  }

  addKeepOut(r: Rect) {
    this.keepOut.push(r);
  }

  nextId(prefix = 'p') {
    return `${this.room.index}:${prefix}${this.propN++}`;
  }

  addProp(
    type: PropType,
    x: number,
    z: number,
    rot = 0,
    opts: { y?: number; variant?: number; sx?: number; sy?: number; sz?: number; data?: number; size?: [number, number, number] | null; occupy?: boolean; collide?: boolean; crouch?: number } = {},
  ): PropData {
    const fp = opts.size !== undefined ? opts.size : FOOTPRINT[type] ?? null;
    const y = opts.y ?? this.floorAt(z);
    const p: PropData = { id: this.nextId(), type, x, y, z, rot: rot & 3, variant: opts.variant ?? this.rng.int(0, 3) };
    if (opts.sx !== undefined) p.sx = opts.sx;
    if (opts.sy !== undefined) p.sy = opts.sy;
    if (opts.sz !== undefined) p.sz = opts.sz;
    if (opts.data !== undefined) p.data = opts.data;
    this.room.props.push(p);
    if (fp) {
      const r = RoomBuilder.rectFor(x, z, fp[0], fp[1], rot);
      if (opts.occupy !== false) this.occupied.push(r);
      if (opts.collide !== false) {
        if (opts.crouch !== undefined) {
          // obstacle with a crawl gap below `crouch` meters
          this.addCollider(r, y + opts.crouch, y + fp[2], true);
        } else this.addCollider(r, y, y + fp[2]);
      }
    }
    return p;
  }

  /**
   * Try to place a prop flush against a random wall. Returns the prop or null.
   * `walls` limits candidate walls. Prop front faces into the room.
   */
  placeAgainstWall(
    type: PropType,
    opts: { walls?: ('n' | 's' | 'e' | 'w')[]; attempts?: number; gap?: number; variant?: number; size?: [number, number, number]; y?: number; extraFront?: number } = {},
  ): PropData | null {
    const fp = opts.size ?? FOOTPRINT[type];
    if (!fp) return null;
    const [w, d] = fp;
    const walls = opts.walls ?? ['n', 's', 'e', 'w'];
    const gap = opts.gap ?? 0.03;
    const front = opts.extraFront ?? 0;
    for (let a = 0; a < (opts.attempts ?? 24); a++) {
      const wall = this.rng.pick(walls);
      let x = 0, z = 0, rot = 0;
      if (wall === 's' || wall === 'n') {
        const span = this.w - w - 0.2;
        if (span <= 0) continue;
        x = this.ix0 + 0.1 + w / 2 + this.rng.next() * span;
        z = wall === 's' ? this.iz0 + d / 2 + gap : this.iz1 - d / 2 - gap;
        rot = wall === 's' ? 0 : 2;
      } else {
        const span = this.room.depth - w - 0.2;
        if (span <= 0) continue;
        z = this.iz0 + 0.1 + w / 2 + this.rng.next() * span;
        x = wall === 'w' ? this.ix0 + d / 2 + gap : this.ix1 - d / 2 - gap;
        rot = wall === 'w' ? 1 : 3;
      }
      const r = RoomBuilder.rectFor(x, z, w, d, rot);
      // require clearance in front (for usable furniture)
      const fr = front > 0 ? this.frontRect(x, z, w, d, rot, front) : null;
      if (!this.canPlace(r)) continue;
      if (fr && !this.canPlace(fr, 0, true)) continue;
      if (Math.abs(this.floorAt(r.z0) - this.floorAt(r.z1)) > 0.01) continue;
      const p = this.addProp(type, x, z, rot, { variant: opts.variant, size: opts.size, y: opts.y });
      if (fr) this.occupied.push({ ...fr, x0: fr.x0 + 0.01, x1: fr.x1 - 0.01 }); // reserve access space
      return p;
    }
    return null;
  }

  frontRect(x: number, z: number, w: number, d: number, rot: number, depth: number): Rect {
    const [fx, fz] = rotXZ(0, d / 2 + depth / 2, rot);
    return RoomBuilder.rectFor(x + fx, z + fz, w * 0.9, depth, rot);
  }

  /** Free-standing placement anywhere in the interior. */
  placeFree(type: PropType, opts: { attempts?: number; rot?: number; variant?: number; margin?: number; size?: [number, number, number]; region?: Rect } = {}): PropData | null {
    const fp = opts.size ?? FOOTPRINT[type];
    if (!fp) return null;
    const m = opts.margin ?? 0.6;
    const reg = opts.region ?? { x0: this.ix0 + m, z0: this.iz0 + m, x1: this.ix1 - m, z1: this.iz1 - m };
    for (let a = 0; a < (opts.attempts ?? 20); a++) {
      const rot = opts.rot ?? this.rng.int(0, 3);
      const x = this.rng.range(reg.x0, reg.x1);
      const z = this.rng.range(reg.z0, reg.z1);
      const r = RoomBuilder.rectFor(x, z, fp[0], fp[1], rot);
      if (!this.canPlace(r, 0.5)) continue;
      if (Math.abs(this.floorAt(r.z0) - this.floorAt(r.z1)) > 0.01) continue;
      return this.addProp(type, x, z, rot, { variant: opts.variant, size: opts.size });
    }
    return null;
  }

  addInteractable(kind: InteractKind, lx: number, ly: number, lz: number, extra: Partial<Interactable> = {}): Interactable {
    const it: Interactable = { id: extra.id ?? `${this.room.index}:i${this.room.interactables.length}`, kind, pos: this.toWorld(lx, ly, lz), ...extra };
    this.room.interactables.push(it);
    return it;
  }

  /** Local point offset from a prop's centre in its own frame. */
  propLocal(p: PropData, ox: number, oz: number): [number, number] {
    const [rx, rz] = rotXZ(ox, oz, p.rot);
    return [p.x + rx, p.z + rz];
  }

  addLight(lx: number, ly: number, lz: number, l: Omit<LightData, 'lx' | 'ly' | 'lz' | 'world'>): LightData {
    const ld: LightData = { lx, ly, lz, world: this.toWorld(lx, ly, lz), ...l };
    this.room.lights.push(ld);
    return ld;
  }

  /** Register drawers on a container prop. Each drawer gets loot rolled by `lootFn`. */
  addDrawers(p: PropData, count: number, lootFn: () => Loot | undefined, height = 0.6, spread = 0.35, frontOffset = 0.3) {
    for (let i = 0; i < count; i++) {
      const ox = count === 1 ? 0 : (i - (count - 1) / 2) * spread;
      const [lx, lz] = this.propLocal(p, ox, frontOffset);
      this.addInteractable('drawer', lx, p.y + height - Math.floor(i / 2) * 0.0, lz, { propId: p.id, slot: i, loot: lootFn() });
    }
  }

  addHide(p: PropData, kind: 'closet' | 'bed' | 'locker', jack = false) {
    const fp = FOOTPRINT[p.type]!;
    const [lx, lz] = this.propLocal(p, 0, fp[1] / 2 + 0.2);
    const [ex, ez] = this.propLocal(p, 0, fp[1] / 2 + 0.75);
    const it = this.addInteractable('hide', lx, p.y + 1.0, lz, { propId: p.id, hide: kind, exitPos: this.toWorld(ex, p.y, ez) });
    if (jack) it.data = { jack: true };
    return it;
  }

}
