import type { Rng } from '../rng';
import type { Loot, PropData, PropType, RoomData } from '../types';
import { RoomBuilder, FOOTPRINT, type Rect } from './builder';
import type { RunPlan } from './plan';
import { DOOR_W, WALL_T } from '../constants';
import type { ItemType } from '../items';

type Wall = 'n' | 's' | 'e' | 'w';
const LANE = 0.8; // half width of the clear walking lane

// ---------------------------------------------------------------- loot

export function rollLoot(rng: Rng, index: number, bias: 'normal' | 'med' | 'rich' = 'normal'): Loot | undefined {
  const r = rng.next();
  if (index > 4 && r < 0.025) return { timothy: true };
  if (r < 0.42) return { gold: rng.int(2, 9) * (index > 50 ? 3 : index > 20 ? 2 : 1) + (bias === 'rich' ? 10 : 0) };
  if (r < 0.64) {
    const table: { item: ItemType; w: number }[] =
      bias === 'med'
        ? [{ item: 'bandage', w: 6 }, { item: 'vitamins', w: 4 }, { item: 'battery', w: 1 }]
        : [
            { item: 'battery', w: 5 }, { item: 'lockpick', w: 3 }, { item: 'bandage', w: 3 }, { item: 'vitamins', w: 3 },
            { item: 'lighter', w: 2 }, { item: 'flashlight', w: index > 10 ? 1 : 0.3 }, { item: 'crucifix', w: index > 15 ? 0.25 : 0 },
          ];
    return { item: rng.weighted(table) };
  }
  return undefined;
}

// ---------------------------------------------------------------- helpers

function reserveDoors(b: RoomBuilder) {
  const r = b.room;
  b.addKeepOut({ x0: -DOOR_W / 2 - 0.35, z0: WALL_T, x1: DOOR_W / 2 + 0.35, z1: WALL_T + 1.7 });
  for (const e of r.exits) {
    const hw = DOOR_W / 2 + 0.35;
    if (e.dir === 0) b.addKeepOut({ x0: e.lx - hw, z0: b.iz1 - 1.7, x1: e.lx + hw, z1: b.iz1 });
    else if (e.dir === 1) b.addKeepOut({ x0: b.ix1 - 1.7, z0: e.lz - hw, x1: b.ix1, z1: e.lz + hw });
    else b.addKeepOut({ x0: b.ix0, z0: e.lz - hw, x1: b.ix0 + 1.7, z1: e.lz + hw });
  }
}

/** Keep a clear lane from the entry to the real exit; Rush/Seek follow it and players are never blocked. */
function reserveLane(b: RoomBuilder) {
  const r = b.room;
  const ex = r.exits[r.realExit];
  if (!ex) {
    b.addKeepOut({ x0: -LANE, z0: b.iz0, x1: LANE, z1: b.cz });
    return;
  }
  if (ex.dir === 0) {
    const lo = b.iz0 + 1.6, hi = b.iz1 - 1.6;
    b.laneMid = Math.abs(ex.lx) < 0.3 ? b.cz : lo < hi ? b.rng.range(lo, hi) : b.cz;
    const m = b.laneMid;
    b.addKeepOut({ x0: -LANE, z0: b.iz0, x1: LANE, z1: m + LANE });
    b.addKeepOut({ x0: Math.min(0, ex.lx) - LANE, z0: m - LANE, x1: Math.max(0, ex.lx) + LANE, z1: m + LANE });
    b.addKeepOut({ x0: ex.lx - LANE, z0: m - LANE, x1: ex.lx + LANE, z1: b.iz1 });
  } else {
    b.addKeepOut({ x0: -LANE, z0: b.iz0, x1: LANE, z1: ex.lz + LANE });
    const xa = ex.dir === 1 ? 0 : b.ix0, xb = ex.dir === 1 ? b.ix1 : 0;
    b.addKeepOut({ x0: Math.min(xa, xb) - LANE, z0: ex.lz - LANE, x1: Math.max(xa, xb) + LANE, z1: ex.lz + LANE });
  }
}

function openingsOn(b: RoomBuilder, wall: Wall) {
  return b.room.openings.length ? b.room.openings.filter((o) => o.wall === wall) : derivedOpenings(b).filter((o) => o.wall === wall);
}
function derivedOpenings(b: RoomBuilder) {
  const out: { wall: Wall; center: number; width: number }[] = [{ wall: 's', center: 0, width: DOOR_W }];
  for (const e of b.room.exits) out.push({ wall: e.dir === 0 ? 'n' : e.dir === 1 ? 'e' : 'w', center: e.dir === 0 ? e.lx : e.lz, width: e.style === 'double' ? 2.2 : DOOR_W });
  return out;
}

/** Point on the inner face of a wall, with rotation facing into the room. */
function wallAt(b: RoomBuilder, wall: Wall, t: number): { x: number; z: number; rot: number } {
  switch (wall) {
    case 's': return { x: t, z: b.iz0, rot: 0 };
    case 'n': return { x: t, z: b.iz1, rot: 2 };
    case 'w': return { x: b.ix0, z: t, rot: 1 };
    default: return { x: b.ix1, z: t, rot: 3 };
  }
}

/** Find positions along a wall that avoid door openings (for wall-mounted decor). */
function wallSpots(b: RoomBuilder, wall: Wall, width: number, count: number): number[] {
  const horizontal = wall === 's' || wall === 'n';
  const a0 = horizontal ? b.ix0 : b.iz0, a1 = horizontal ? b.ix1 : b.iz1;
  const ops = openingsOn(b, wall);
  const out: number[] = [];
  for (let k = 0; k < count * 6 && out.length < count; k++) {
    const t = b.rng.range(a0 + width / 2 + 0.3, a1 - width / 2 - 0.3);
    if (ops.some((o) => Math.abs(o.center - t) < o.width / 2 + width / 2 + 0.35)) continue;
    if (out.some((o) => Math.abs(o - t) < width + 0.5)) continue;
    out.push(t);
  }
  return out;
}

function decorateWalls(b: RoomBuilder, paintings: number, sconces: number, walls: Wall[] = ['n', 's', 'e', 'w']) {
  for (let k = 0; k < paintings; k++) {
    const wall = b.rng.pick(walls);
    const [t] = wallSpots(b, wall, 1.1, 1);
    if (t === undefined) continue;
    const p = wallAt(b, wall, t);
    b.addProp('painting', p.x, p.z, p.rot, { y: b.floorAt(p.z) + b.rng.range(1.5, 1.8), size: null, variant: b.rng.int(0, 7) });
  }
  for (let k = 0; k < sconces; k++) {
    const wall = b.rng.pick(walls);
    const [t] = wallSpots(b, wall, 0.4, 1);
    if (t === undefined) continue;
    const p = wallAt(b, wall, t);
    const y = b.floorAt(p.z) + 2.0;
    b.addProp('wallLight', p.x, p.z, p.rot, { y, size: null });
    if (!b.room.dark) {
      const [ox, oz] = b.propLocal({ x: p.x, z: p.z, rot: p.rot } as PropData, 0, 0.25);
      b.addLight(ox, y + 0.1, oz, { color: b.room.theme.lightColor, intensity: 2.2, range: 6, kind: 'wall', flicker: b.rng.chance(0.15) });
    }
  }
}

function ceilingLights(b: RoomBuilder, kind: 'chandelier' | 'ceilingLight', n: number, intensity = 7, range = 11) {
  const r = b.room;
  const top = Math.max(0, r.rise) + (r.height - Math.abs(r.rise));
  const spots: [number, number][] = [];
  if (n === 1) spots.push([b.cx, b.cz]);
  else {
    const alongZ = r.depth >= b.w;
    for (let k = 0; k < n; k++) {
      const t = (k + 0.5) / n;
      spots.push(alongZ ? [b.cx, b.iz0 + r.depth * t] : [b.ix0 + b.w * t, b.cz]);
    }
  }
  for (const [x, z] of spots) {
    b.addProp(kind, x, z, 0, { y: top, size: null, variant: b.rng.int(0, 2) });
    if (!r.dark) b.addLight(x, top - (kind === 'chandelier' ? 1.0 : 0.3), z, { color: r.theme.lightColor, intensity, range, kind: kind === 'chandelier' ? 'chandelier' : 'ceiling', flicker: b.rng.chance(0.12), shadow: true });
  }
}

function container(b: RoomBuilder, type: PropType, drawers: number, opts: { walls?: Wall[]; bias?: 'normal' | 'med' | 'rich'; height?: number; spread?: number } = {}): PropData | null {
  const p = b.placeAgainstWall(type, { walls: opts.walls, extraFront: 0.85 });
  if (!p) return null;
  const fp = FOOTPRINT[type]!;
  b.addDrawers(p, drawers, () => rollLoot(b.rng, b.room.index, opts.bias), opts.height ?? fp[2] * 0.7, opts.spread ?? fp[0] / drawers, fp[1] / 2 + 0.05);
  return p;
}

function closet(b: RoomBuilder, walls?: Wall[], type: 'closet' | 'locker' = 'closet'): PropData | null {
  const p = b.placeAgainstWall(type, { walls, extraFront: 1.15 });
  if (!p) return null;
  b.addHide(p, type === 'locker' ? 'locker' : 'closet', b.room.index > 8 && b.rng.chance(0.03));
  return p;
}

function hideCount(r: RoomData) {
  return r.interactables.filter((i) => i.kind === 'hide' && !i.data?.jack).length;
}

function rug(b: RoomBuilder, w: number, d: number) {
  b.addProp('rug', b.cx, b.cz, b.rng.int(0, 1), { size: null, sx: w, sz: d, variant: b.rng.int(0, 3) });
}

function scatter(b: RoomBuilder, types: PropType[], n: number) {
  for (let k = 0; k < n; k++) b.placeFree(b.rng.pick(types), { attempts: 10 });
}

function lockHardware(b: RoomBuilder) {
  const r = b.room;
  const ex = r.exits[r.realExit];
  if (!ex) return;
  if (ex.lock === 'key') {
    // key is in a drawer of this room, otherwise on a table or the floor
    const drawers = r.interactables.filter((i) => i.kind === 'drawer' && !i.loot?.timothy);
    if (drawers.length) {
      const d = b.rng.pick(drawers);
      d.loot = { item: 'key', door: r.index + 1 };
    } else {
      const t = b.placeFree('table', { attempts: 30 });
      const pos = t ? [t.x, t.z] : [b.cx + 1, b.cz];
      b.addInteractable('pickup', pos[0], (t ? 0.8 : 0.05) + b.floorAt(pos[1]), pos[1], { loot: { item: 'key', door: r.index + 1 } });
    }
  } else if (ex.lock === 'lever') {
    for (let a = 0; a < 30; a++) {
      const wall = b.rng.pick<Wall>(['e', 'w', 's', 'n']);
      const [t] = wallSpots(b, wall, 0.6, 1);
      if (t === undefined) continue;
      const p = wallAt(b, wall, t);
      const rect = RoomBuilder.rectFor(p.x, p.z, 0.6, 0.6, p.rot);
      if (!b.canPlace({ ...rect }, 0) && a < 25) continue;
      const lever = b.addProp('lever', p.x, p.z, p.rot, { y: b.floorAt(p.z) + 1.2, size: null });
      const [lx, lz] = b.propLocal(lever, 0, 0.15);
      b.addInteractable('lever', lx, lever.y, lz, { propId: lever.id });
      return;
    }
    throw new Error('no lever spot');
  }
}

// ---------------------------------------------------------------- room kinds

function normalCommon(b: RoomBuilder, opts: { paintings?: number; sconces?: number; lights?: number; chandelier?: boolean } = {}) {
  decorateWalls(b, opts.paintings ?? b.rng.int(1, 3), opts.sconces ?? b.rng.int(0, 2));
  ceilingLights(b, opts.chandelier ? 'chandelier' : 'ceilingLight', opts.lights ?? 1);
}

function hallway(b: RoomBuilder) {
  const n = Math.max(1, Math.round(b.room.depth / 6));
  ceilingLights(b, 'ceilingLight', n, 5, 8);
  decorateWalls(b, b.rng.int(1, 4), b.rng.int(1, 3), ['e', 'w']);
  for (let k = 0; k < b.rng.int(0, 2); k++) container(b, 'dresser', 2, { walls: ['e', 'w'] });
  if (b.rng.chance(0.5)) b.placeAgainstWall('plant', { walls: ['e', 'w'] });
  if (b.w >= 3.5 && b.rng.chance(0.55)) closet(b, ['e', 'w']);
  if (b.rng.chance(0.3)) b.placeAgainstWall('clock', { walls: ['e', 'w'] });
  b.addProp('rug', 0, b.cz, 0, { size: null, sx: Math.min(1.6, b.w - 0.6), sz: b.room.depth - 1.2, variant: 3 });
}

function bedroom(b: RoomBuilder) {
  const beds = b.w * b.room.depth > 70 ? b.rng.int(1, 2) : 1;
  for (let k = 0; k < beds; k++) {
    const bed = b.placeAgainstWall('bed', { extraFront: 0.8 });
    if (!bed) continue;
    b.addHide(bed, 'bed');
    for (const side of [-1, 1]) {
      const [nx, nz] = b.propLocal(bed, side * 1.1, -0.85);
      const rect = RoomBuilder.rectFor(nx, nz, 0.5, 0.45, bed.rot);
      const front = b.frontRect(nx, nz, 0.5, 0.45, bed.rot, 0.7);
      if (b.canPlace(rect, 0) && b.canPlace(front, 0, true)) {
        b.occupied.push(front);
        const ns = b.addProp('nightstand', nx, nz, bed.rot);
        b.addDrawers(ns, 1, () => rollLoot(b.rng, b.room.index), 0.45, 0, 0.25);
        if (b.rng.chance(0.6)) {
          b.addProp('lamp', nx, nz, bed.rot, { y: 0.6, size: null });
          if (!b.room.dark) b.addLight(nx, 1.0, nz, { color: 0xffb46b, intensity: 1.4, range: 4.5, kind: 'lamp' });
        }
      }
    }
  }
  container(b, 'dresser', 3);
  if (b.rng.chance(0.7)) closet(b);
  if (b.rng.chance(0.5)) closet(b);
  if (b.rng.chance(0.4)) { const d = container(b, 'desk', 2); if (d) { const [cx, cz] = b.propLocal(d, 0, 0.7); const r = RoomBuilder.rectFor(cx, cz, 0.5, 0.5, d.rot); if (b.canPlace(r, 0)) b.addProp('chair', cx, cz, d.rot + 2); } }
  if (b.rng.chance(0.3)) b.placeAgainstWall('fireplace', { extraFront: 0.6 });
  if (b.rng.chance(0.4)) b.placeAgainstWall('plant');
  if (b.rng.chance(0.3)) b.placeAgainstWall('mirror', { size: [0.8, 0.1, 1.6] });
  rug(b, Math.min(3, b.w - 2), Math.min(2.4, b.room.depth - 2));
  normalCommon(b);
}

function office(b: RoomBuilder) {
  const desk = container(b, 'desk', 2);
  if (desk) {
    const [cx, cz] = b.propLocal(desk, 0, 0.7);
    if (b.canPlace(RoomBuilder.rectFor(cx, cz, 0.5, 0.5, desk.rot), 0)) b.addProp('chair', cx, cz, desk.rot + 2);
    b.addProp('lamp', ...b.propLocal(desk, 0.5, -0.1), desk.rot, { y: 0.8, size: null, variant: 1 });
    if (!b.room.dark) { const [lx, lz] = b.propLocal(desk, 0.5, 0); b.addLight(lx, 1.2, lz, { color: 0xffc070, intensity: 1.6, range: 5, kind: 'lamp' }); }
  }
  for (let k = 0; k < b.rng.int(1, 3); k++) b.placeAgainstWall('bookshelf');
  if (b.rng.chance(0.6)) container(b, 'cabinet', 2);
  if (b.rng.chance(0.6)) closet(b);
  if (b.rng.chance(0.4)) b.placeAgainstWall('clock');
  if (b.rng.chance(0.5)) b.placeAgainstWall('plant');
  if (b.rng.chance(0.4)) b.placeFree('armchair');
  rug(b, Math.min(2.6, b.w - 2), Math.min(2.2, b.room.depth - 2));
  normalCommon(b);
}

function storage(b: RoomBuilder) {
  for (let k = 0; k < b.rng.int(2, 4); k++) b.placeAgainstWall(b.rng.pick(['shelf', 'shelf', 'cabinet'] as PropType[]));
  for (let k = 0; k < b.rng.int(1, 2); k++) container(b, 'cabinet', 2);
  scatter(b, ['crate', 'boxStack', 'barrel', 'crate', 'pallet'], b.rng.int(3, 6));
  if (b.rng.chance(0.7)) closet(b, undefined, b.rng.chance(0.5) ? 'locker' : 'closet');
  b.addProp('pipe', b.cx, b.cz, b.rng.int(0, 1), { y: b.room.height - 0.35, size: null, sx: b.room.depth });
  ceilingLights(b, 'ceilingLight', 1, 4, 8);
  b.room.tags.push('bulb');
}

function dining(b: RoomBuilder) {
  const n = b.room.depth > 11 ? 2 : 1;
  for (let k = 0; k < n; k++) {
    const t = b.placeFree('diningTable', { rot: b.rng.int(0, 1), attempts: 30, margin: 1.2 });
    if (!t) continue;
    for (let c = 0; c < 6; c++) {
      const side = c < 3 ? -1 : 1, along = ((c % 3) - 1) * 1.0;
      const [cx, cz] = b.propLocal(t, along, side * 0.95);
      b.addProp('chair', cx, cz, t.rot + (side < 0 ? 0 : 2), { occupy: false });
    }
  }
  for (let k = 0; k < b.rng.int(1, 2); k++) container(b, 'cabinet', 2);
  container(b, 'dresser', 3);
  closet(b);
  if (b.rng.chance(0.5)) closet(b);
  if (b.rng.chance(0.5)) b.placeAgainstWall('fireplace', { extraFront: 0.6 });
  if (b.rng.chance(0.3)) b.placeAgainstWall('piano', { extraFront: 0.6 });
  for (let k = 0; k < 2; k++) b.placeAgainstWall('plant');
  normalCommon(b, { chandelier: true, lights: n, paintings: b.rng.int(2, 4), sconces: b.rng.int(1, 3) });
}

function intersection(b: RoomBuilder) {
  const pillars = b.rng.chance(0.6);
  if (pillars) {
    for (const [px, pz] of [[0.3, 0.3], [0.7, 0.3], [0.3, 0.7], [0.7, 0.7]]) {
      const x = b.ix0 + b.w * px, z = b.iz0 + b.room.depth * pz;
      if (b.canPlace(RoomBuilder.rectFor(x, z, 0.6, 0.6, 0), 0.2)) b.addProp('pillar', x, z, 0, { sy: b.room.height });
    }
  }
  for (let k = 0; k < b.rng.int(1, 2); k++) closet(b);
  container(b, 'dresser', 2);
  b.placeAgainstWall('sofa');
  scatter(b, ['plant', 'armchair', 'table'], 2);
  normalCommon(b, { chandelier: b.rng.chance(0.5), lights: 2 });
}

function stairs(b: RoomBuilder) {
  const r = b.room;
  const a = WALL_T + 2.4, len = Math.abs(r.rise) / 0.18 * 0.28;
  const z1 = Math.min(a + len, b.iz1 - 2.2);
  r.tags.push(`stair:${a},${z1}`);
  // floor slabs + steps (solid boxes from the lowest level)
  const lo = Math.min(0, r.rise) - 0.4;
  b.addCollider({ x0: r.xMin - WALL_T, z0: 0, x1: r.xMax + WALL_T, z1: a }, lo, 0);
  b.addCollider({ x0: r.xMin - WALL_T, z0: z1, x1: r.xMax + WALL_T, z1: 2 * WALL_T + r.depth }, lo, r.rise);
  const steps = Math.max(4, Math.round(Math.abs(r.rise) / 0.18));
  const stepD = (z1 - a) / steps;
  for (let s = 0; s < steps; s++) {
    const topY = (r.rise * (s + 1)) / steps;
    b.addCollider({ x0: r.xMin, z0: a + s * stepD, x1: r.xMax, z1: a + (s + 1) * stepD }, lo, topY);
  }
  b.addProp('stairs', 0, a, 0, { y: 0, size: null, sx: b.w, sz: z1 - a, data: r.rise });
  b.occupied.push({ x0: r.xMin, z0: a - 0.6, x1: r.xMax, z1: z1 + 0.6 });
  b.addProp('railing', r.xMin + 0.08, (a + z1) / 2, 0, { y: 0, size: null, sz: z1 - a, data: r.rise });
  b.addProp('railing', r.xMax - 0.08, (a + z1) / 2, 0, { y: 0, size: null, sz: z1 - a, data: r.rise });
  // upper/lower landing furniture
  if (b.rng.chance(0.6)) container(b, 'dresser', 2);
  if (b.rng.chance(0.4)) b.placeAgainstWall('plant');
  if (b.rng.chance(0.6)) closet(b);
  decorateWalls(b, b.rng.int(1, 3), 1, ['e', 'w']);
  ceilingLights(b, 'ceilingLight', 2, 5, 9);
}

function lounge(b: RoomBuilder) {
  if (b.rng.chance(0.7)) b.placeAgainstWall('fireplace', { extraFront: 1.0 });
  for (let k = 0; k < b.rng.int(1, 2); k++) b.placeAgainstWall('sofa', { extraFront: 0.6 });
  scatter(b, ['armchair', 'armchair', 'table', 'floorLamp', 'plant'], b.rng.int(2, 4));
  for (const p of b.room.props.filter((q) => q.type === 'floorLamp')) if (!b.room.dark) b.addLight(p.x, 1.6, p.z, { color: 0xffb46b, intensity: 1.6, range: 5, kind: 'lamp' });
  if (b.rng.chance(0.4)) b.placeAgainstWall('piano', { extraFront: 0.6 });
  if (b.rng.chance(0.6)) b.placeAgainstWall('bookshelf');
  container(b, 'dresser', 2);
  closet(b);
  if (b.rng.chance(0.4)) closet(b);
  rug(b, Math.min(3.4, b.w - 2), Math.min(2.6, b.room.depth - 2));
  normalCommon(b, { chandelier: true });
}

function bathroom(b: RoomBuilder) {
  for (let k = 0; k < b.rng.int(1, 3); k++) {
    const s = b.placeAgainstWall('sink');
    if (s) b.addProp('mirror', ...b.propLocal(s, 0, -0.27), s.rot, { y: 1.5, size: null });
  }
  for (let k = 0; k < b.rng.int(1, 2); k++) b.placeAgainstWall('toilet');
  if (b.rng.chance(0.6)) b.placeAgainstWall('bathtub');
  container(b, 'cabinet', 1, { bias: 'med' });
  if (b.rng.chance(0.5)) closet(b, undefined, 'locker');
  ceilingLights(b, 'ceilingLight', 1, 4, 7);
}

function kitchen(b: RoomBuilder) {
  for (let k = 0; k < b.rng.int(1, 2); k++) container(b, 'counter', 3, { height: 0.75 });
  b.placeAgainstWall('stove');
  b.placeAgainstWall('fridge');
  b.placeAgainstWall('sink');
  b.placeFree('table', { margin: 1 });
  container(b, 'cabinet', 2);
  if (b.rng.chance(0.6)) closet(b);
  ceilingLights(b, 'ceilingLight', 2, 5, 8);
}

function gallery(b: RoomBuilder) {
  decorateWalls(b, b.rng.int(5, 8), 0, ['e', 'w']);
  for (let k = 0; k < b.rng.int(2, 4); k++) b.placeAgainstWall('statue', { walls: ['e', 'w'] });
  for (let k = 0; k < b.rng.int(1, 2); k++) b.placeAgainstWall('bench', { walls: ['e', 'w'] });
  if (b.rng.chance(0.7)) closet(b, ['e', 'w']);
  container(b, 'dresser', 2, { walls: ['e', 'w'] });
  ceilingLights(b, 'chandelier', Math.max(2, Math.round(b.room.depth / 7)), 6, 10);
}

function closetRoom(b: RoomBuilder) {
  const n = b.rng.int(4, 7);
  for (let k = 0; k < n; k++) closet(b);
  container(b, 'dresser', 3);
  if (b.rng.chance(0.5)) container(b, 'dresser', 2);
  scatter(b, ['plant', 'armchair'], 1);
  normalCommon(b, { chandelier: b.rng.chance(0.4) });
}

function basement(b: RoomBuilder) {
  for (let k = 0; k < 2; k++) b.placeAgainstWall(b.rng.pick(['shelf', 'generator'] as PropType[]));
  scatter(b, ['crate', 'barrel', 'boxStack', 'debris'], b.rng.int(3, 5));
  for (let k = 0; k < 2; k++) closet(b, undefined, 'locker');
  container(b, 'cabinet', 2);
  for (let k = 0; k < 2; k++) b.addProp('pipe', b.ix0 + 0.4 + k * 0.3, b.cz, 0, { y: b.room.height - 0.3 - k * 0.2, size: null, sx: b.room.depth });
  if (b.rng.chance(0.3)) b.placeFree('skeleton', { size: [0.8, 1.6, 0.3] });
  ceilingLights(b, 'ceilingLight', 1, 3, 7);
  b.room.tags.push('bulb', 'drip');
}

function reception(b: RoomBuilder) {
  const r = b.room;
  // elevator behind the entry wall
  const el = (x0: number, z0: number, x1: number, z1: number, y0: number, y1: number) => b.addCollider({ x0, z0, x1, z1 }, y0, y1);
  el(-1.7, -3.2, 1.7, 0, -0.4, 0);
  el(-1.7, -3.2, 1.7, -3.0, 0, 3);
  el(-1.7, -3.2, -1.5, 0, 0, 3);
  el(1.5, -3.2, 1.7, 0, 0, 3);
  el(-1.7, -3.2, 1.7, 0, 2.8, 3.1);
  b.addProp('elevator', 0, 0, 0, { y: 0, size: null });
  r.walkable.push(b.rectToWorldBox({ x0: -1.5, z0: -3.0, x1: 1.5, z1: 0 }, -0.2, 2.8));
  r.tags.push('spawn:0,-1.6');
  const desk = b.addProp('receptionDesk', -3.2, b.iz0 + 9.5, 2);
  b.addDrawers(desk, 3, () => rollLoot(b.rng, 0, 'rich'), 0.8, 1.3, -0.55);
  // guaranteed lighter to help newcomers through dark rooms
  const dr = r.interactables.filter((i) => i.kind === 'drawer');
  dr[1].loot = { item: 'lighter' };
  b.addProp('shelf', -3.2, b.iz0 + 11.3, 2, { variant: 3 });
  b.addProp('lamp', -4.6, b.iz0 + 9.5, 2, { y: 1.1, size: null, variant: 1 });
  b.addLight(-4.6, 1.6, b.iz0 + 9.5, { color: 0xffbd70, intensity: 2.2, range: 6, kind: 'lamp' });
  // grand (blocked) staircase along the east wall
  b.addProp('stairs', r.xMax - 1.6, b.iz0 + 4, 0, { y: 0, size: null, sx: 2.8, sz: 7, data: 3.6, variant: 1 });
  b.addCollider({ x0: r.xMax - 3.0, z0: b.iz0 + 4, x1: r.xMax, z1: b.iz0 + 11 }, 0, 3.6);
  b.occupied.push({ x0: r.xMax - 3.2, z0: b.iz0 + 3.5, x1: r.xMax, z1: b.iz0 + 11.5 });
  b.addProp('railing', r.xMax - 3.0, b.iz0 + 7.5, 0, { y: 0, size: null, sz: 7, data: 3.6 });
  b.addProp('pillar', -5, b.iz0 + 4, 0, { sy: r.height });
  b.addProp('pillar', 5, b.iz0 + 4, 0, { sy: r.height });
  b.addProp('pillar', -5, b.iz0 + 12, 0, { sy: r.height });
  b.addProp('pillar', 5, b.iz0 + 12, 0, { sy: r.height });
  b.addProp('sofa', -5.5, b.iz0 + 2, 0);
  b.addProp('table', -5.5, b.iz0 + 3.3, 0, { size: [1.2, 0.7, 0.45], variant: 2 });
  b.addProp('armchair', -7, b.iz0 + 3.3, 1);
  b.addProp('plant', 2.2, b.iz0 + 0.6, 0);
  b.addProp('plant', -2.2, b.iz0 + 0.6, 0);
  b.addProp('plant', 1.6, b.iz1 - 0.5, 0);
  b.addProp('plant', -1.6, b.iz1 - 0.5, 0);
  b.addProp('clock', -7.6, b.iz1 - 1.5, 1);
  b.addProp('rug', 0, b.cz, 0, { size: null, sx: 3.2, sz: 12, variant: 0 });
  decorateWalls(b, 4, 4, ['w', 'n']);
  ceilingLights(b, 'chandelier', 1, 14, 18);
  b.addLight(0, 3, b.iz0 + 3, { color: 0xffd0a0, intensity: 4, range: 10, kind: 'ceiling' });
  b.addLight(0, 3, b.iz1 - 3, { color: 0xffd0a0, intensity: 4, range: 10, kind: 'ceiling' });
}

function library(b: RoomBuilder, puzzleRng: Rng) {
  const r = b.room;
  r.exits[r.realExit].lock = 'code';
  r.exits[r.realExit].style = 'double';
  // puzzle: 5 distinct shapes in order with digits
  const shapes = puzzleRng.shuffle([0, 1, 2, 3, 4, 5, 6, 7]).slice(0, 5);
  const digits = shapes.map(() => puzzleRng.int(0, 9));
  r.puzzle = { shapes, code: digits.join('') };
  const shelves: PropData[] = [];
  // shelf rows (back-to-back units) on both sides of the central aisle
  for (const side of [-1, 1]) {
    for (let row = 0; row < 5; row++) {
      const z = b.iz0 + 4.2 + row * 4.4;
      for (let u = 0; u < 4; u++) {
        const x = side * (3.6 + 0.8 + u * 1.6);
        if (Math.abs(x) + 0.8 > r.xMax - 1.4) continue;
        for (const face of [0, 2]) {
          const p = b.addProp('bookshelf', x, z + (face === 0 ? -0.23 : 0.23), face === 0 ? 2 : 0, { variant: 2 + (u % 2) });
          shelves.push(p);
        }
      }
    }
  }
  // reading desks along the central aisle
  for (let k = 0; k < 4; k++) {
    const z = b.iz0 + 3 + k * 6;
    for (const side of [-1, 1]) {
      const d = b.addProp('desk', side * 2.0, z, side < 0 ? 1 : 3, { variant: 2 });
      b.addDrawers(d, 1, () => rollLoot(b.rng, 50), 0.6, 0, 0.42);
      b.addProp('lamp', side * 2.0, z + 0.4, 0, { y: 0.8, size: null, variant: 2 });
      if (k % 2 === 0) b.addLight(side * 2.0, 1.2, z + 0.4, { color: 0x9fe08a, intensity: 1.0, range: 4, kind: 'lamp' });
    }
  }
  // hiding closets along the side walls, between shelf rows
  for (const side of [-1, 1]) for (let row = 0; row < 5; row++) {
    if (row % 2 === 1) continue;
    const z = b.iz0 + 2.0 + row * 4.4;
    const p = b.addProp('closet', side < 0 ? r.xMin + 0.4 : r.xMax - 0.4, z, side < 0 ? 1 : 3);
    b.addHide(p, 'closet');
  }
  // books: one per code shape + decoys, placed on shelf faces at aisle ends
  const facing = shelves.filter((s) => true);
  puzzleRng.shuffle(facing);
  const bookCount = 9;
  for (let k = 0; k < bookCount; k++) {
    const s = facing[k];
    const [lx, lz] = b.propLocal(s, puzzleRng.range(-0.5, 0.5), 0.35);
    const data = k < 5 ? { shape: shapes[k], digit: digits[k] } : {};
    b.addInteractable('book', lx, puzzleRng.range(0.9, 1.6), lz, { propId: s.id, data });
  }
  // hint paper + padlock
  b.addInteractable('pickup', -2.0, 0.84, b.iz0 + 3.2, { loot: { item: 'hintPaper' } });
  const ex = r.exits[r.realExit];
  b.addProp('padlockDesk', ex.lx + 1.5, b.iz1 - 0.4, 2, { size: null });
  b.addInteractable('padlock', ex.lx, 1.2, b.iz1 - 0.35, {});
  r.tags.push(`figureSpawn:0,${b.iz0 + r.depth * 0.55}`);
  for (let k = 0; k < 3; k++) {
    const z = b.iz0 + 5 + k * 9;
    b.addProp('chandelier', 0, z, 0, { y: r.height, size: null, variant: 1 });
    b.addLight(0, r.height - 1.2, z, { color: 0xffb070, intensity: 3.2, range: 12, kind: 'chandelier', shadow: true, flicker: k === 1 });
  }
  decorateWalls(b, 4, 0, ['s', 'n']);
}

function vestibule(b: RoomBuilder) {
  b.placeAgainstWall('sofa', { walls: ['e', 'w'] });
  b.placeAgainstWall('plant', { walls: ['e', 'w'] });
  b.placeAgainstWall('plant', { walls: ['e', 'w'] });
  container(b, 'dresser', 2, { walls: ['e', 'w'], bias: 'rich' });
  decorateWalls(b, 2, 2, ['e', 'w']);
  ceilingLights(b, 'chandelier', 1, 7, 10);
}

function shop(b: RoomBuilder) {
  const r = b.room;
  const c = b.placeAgainstWall('shopCounter', { walls: ['e', 'w', 'n'].filter((w) => w !== exitWall(r)) as Wall[], extraFront: 1.4, attempts: 60 });
  if (!c) throw new Error('shop counter');
  const [jx, jz] = b.propLocal(c, 0, -0.9);
  b.addProp('jeff', jx, jz, c.rot, { size: null });
  const [sx, sz] = b.propLocal(c, 0, 0.9);
  b.addInteractable('shop', sx, 1.0, sz, { propId: c.id });
  for (let k = 0; k < 3; k++) b.placeAgainstWall('shelf', { variant: 2 });
  b.placeAgainstWall('plant');
  b.placeAgainstWall('plant');
  ceilingLights(b, 'ceilingLight', 2, 8, 10);
  b.addLight(jx, 2.4, jz, { color: 0xffe0b0, intensity: 4, range: 7, kind: 'shop' });
  r.tags.push('safe');
}

function exitWall(r: RoomData): Wall {
  const e = r.exits[r.realExit];
  return !e ? 'n' : e.dir === 0 ? 'n' : e.dir === 1 ? 'e' : 'w';
}

function infirmary(b: RoomBuilder) {
  for (let k = 0; k < b.rng.int(3, 5); k++) {
    const g = b.placeAgainstWall('gurney', { extraFront: 0.5 });
    if (g && b.rng.chance(0.6)) b.addProp('curtain', ...b.propLocal(g, 0.75, 0), g.rot, { size: null });
  }
  for (let k = 0; k < 3; k++) container(b, 'medCabinet', 2, { bias: 'med', height: 1.1 });
  b.placeAgainstWall('sink');
  closet(b);
  closet(b);
  ceilingLights(b, 'ceilingLight', 2, 6, 10);
  b.room.tags.push('infirmary');
}

function courtyard(b: RoomBuilder) {
  const r = b.room;
  b.addProp('fountain', -6, b.cz, 0);
  // stone path ring keeps lane; hedges and trees fill the rest
  for (const side of [-1, 1]) {
    for (let k = 0; k < 3; k++) {
      const z = b.iz0 + 4 + k * 9;
      const x = side * 7.5;
      if (b.canPlace(RoomBuilder.rectFor(x, z, 3, 0.9, 0), 0.2)) b.addProp('hedge', x, z, 0);
      const tx = side * 10.5;
      if (b.canPlace(RoomBuilder.rectFor(tx, z + 3, 0.8, 0.8, 0), 0.4)) b.addProp('tree', tx, z + 3, 0, { variant: k });
    }
  }
  for (let k = 0; k < 4; k++) b.placeFree('bench', { attempts: 20 });
  for (const [x, z] of [[-3, 6], [3, 6], [-3, 24], [3, 24]]) {
    const p = b.addProp('lamppost', x, b.iz0 + z, 0, { size: [0.3, 0.3, 3.6] });
    b.addLight(p.x, 3.4, p.z, { color: 0xffc27a, intensity: 3, range: 9, kind: 'lamp', flicker: b.rng.chance(0.3) });
  }
  b.addProp('gazebo', -8, b.iz1 - 6, 0, { size: null });
  for (const [x, z] of [[-10, b.iz1 - 8], [-6, b.iz1 - 8], [-10, b.iz1 - 4], [-6, b.iz1 - 4]]) b.addCollider({ x0: x - 0.15, z0: z - 0.15, x1: x + 0.15, z1: z + 0.15 }, 0, 3.2);
  b.occupied.push({ x0: -10.5, z0: b.iz1 - 8.5, x1: -5.5, z1: b.iz1 - 3.5 });
  scatter(b, ['statue', 'wheelbarrow', 'pot', 'woodPile'], 4);
  b.addLight(0, 20, b.cz, { color: 0x7d93c4, intensity: 0.6, range: 60, kind: 'moon' });
  r.tags.push('rain', 'outdoor');
}

function greenhouse(b: RoomBuilder) {
  const r = b.room;
  // planter rows along the lane
  for (let k = 0; k < 6; k++) b.placeFree('planter', { rot: b.rng.int(0, 1) * 1, attempts: 12, margin: 0.8 });
  for (let k = 0; k < b.rng.int(2, 4); k++) closet(b);
  scatter(b, ['pot', 'pot', 'wheelbarrow', 'trellis' as PropType], 3);
  for (let k = 0; k < 4; k++) {
    const wall = b.rng.pick<Wall>(['e', 'w']);
    const [t] = wallSpots(b, wall, 1, 1);
    if (t !== undefined) { const p = wallAt(b, wall, t); b.addProp('vines', p.x, p.z, p.rot, { y: 0, size: null, sy: r.height }); }
  }
  container(b, 'cabinet', 2);
  // snares: some at lane edges so careful navigation matters
  const ex = r.exits[r.realExit];
  const nSn = b.rng.int(2, 4);
  for (let k = 0; k < nSn; k++) {
    const z = b.rng.range(b.iz0 + 2.5, b.iz1 - 2.5);
    const x = (b.rng.chance(0.5) ? -1 : 1) * b.rng.range(0.35, 0.65) + (ex && ex.dir === 0 && z > (b.laneMid ?? 0) ? ex.lx : 0);
    b.addProp('snare', x, z, 0, { size: null });
  }
  for (let k = 0; k < b.rng.int(3, 6); k++) {
    const x = b.rng.range(r.xMin + 0.5, r.xMax - 0.5), z = b.rng.range(b.iz0 + 2, b.iz1 - 1);
    if (Math.abs(x) < 1.2) continue;
    b.addProp('snare', x, z, 0, { size: null });
  }
  b.addLight(b.cx, r.height + 3, b.cz, { color: 0x6f86b8, intensity: 0.35, range: 30, kind: 'moon' });
  r.tags.push('rain', 'glass');
}

function electrical(b: RoomBuilder) {
  const r = b.room;
  const iz1 = b.iz1;
  // high voltage enclosure (NW corner)
  const ex0 = r.xMin, ex1 = r.xMin + 7, ez0 = iz1 - 8;
  const doorZ = ez0 + 4;
  b.addCollider({ x0: ex0, z0: ez0 - 0.15, x1: ex1 + 0.15, z1: ez0 + 0.15 }, 0, 4);
  b.addCollider({ x0: ex1 - 0.15, z0: ez0, x1: ex1 + 0.15, z1: doorZ - 0.7 }, 0, 4);
  b.addCollider({ x0: ex1 - 0.15, z0: doorZ + 0.7, x1: ex1 + 0.15, z1: iz1 }, 0, 4);
  b.addCollider({ x0: ex1 - 0.15, z0: doorZ - 0.7, x1: ex1 + 0.15, z1: doorZ + 0.7 }, 2.4, 4);
  b.occupied.push({ x0: ex0, z0: ez0 - 0.8, x1: ex1 + 1.6, z1: iz1 });
  b.addProp('electricPanel', ex1, (ez0 + iz1) / 2, 0, { size: null, sx: 7, sz: 8, data: doorZ });
  const inner = b.addInteractable('cabinetDoor', ex1 + 0.2, 1.1, doorZ, { id: `${r.index}:hvdoor`, data: { lockedBy: 'electricalKey' } });
  inner.exitPos = b.toWorld(ex1, 0, doorZ);
  const box = b.addProp('breakerBox', ex0 + 3.5, iz1 - 0.2, 2);
  const [bx, bz] = b.propLocal(box, 0, 0.45);
  b.addInteractable('breakerBox', bx, 1.3, bz, { id: `${r.index}:breaker`, propId: box.id });
  b.addProp('generator', ex0 + 1.2, ez0 + 1.2, 0);
  // elevator alcove with power gate (NE)
  const elX = r.xMax - 4, elZ = iz1 - 1.6;
  b.addCollider({ x0: elX - 1.8, z0: iz1 - 3.4, x1: elX - 1.6, z1: iz1 }, 0, 3.4);
  b.addCollider({ x0: elX + 1.6, z0: iz1 - 3.4, x1: elX + 1.8, z1: iz1 }, 0, 3.4);
  b.addCollider({ x0: elX - 1.8, z0: iz1 - 3.4, x1: elX + 1.8, z1: iz1 }, 3.0, 3.4);
  b.occupied.push({ x0: elX - 2, z0: iz1 - 5.5, x1: elX + 2, z1: iz1 });
  b.addProp('elevator', elX, iz1 - 3.4, 2, { y: 0, size: null, variant: 1 });
  b.addProp('gate', elX, iz1 - 3.4, 0, { y: 0, size: null, sx: 3.2, variant: 1 });
  b.addInteractable('elevator', elX, 1.0, elZ, { id: `${r.index}:elevator` });
  r.tags.push(`elevator:${elX},${elZ}`, `powerGate:${elX},${iz1 - 3.4}`);
  r.tags.push(`figureSpawn:${r.xMax - 6},${b.cz}`);
  b.addKeepOut({ x0: -LANE, z0: b.iz0, x1: LANE, z1: elZ + LANE });
  b.addKeepOut({ x0: -LANE, z0: elZ - LANE - 0.6, x1: elX, z1: iz1 });
  // warehouse shelving grid
  const shelves: PropData[] = [];
  for (let row = 0; row < 3; row++) {
    for (let col = 0; col < 4; col++) {
      const x = r.xMin + 6 + col * 6.2;
      const z = b.iz0 + 5.5 + row * 6;
      if (Math.abs(x) < 2.6) continue;
      const rect = RoomBuilder.rectFor(x, z, 3.6, 1.1, 0);
      if (!b.canPlace(rect, 0.3)) continue;
      shelves.push(b.addProp('bigShelf', x, z, 0, { variant: row }));
    }
  }
  scatter(b, ['crate', 'crate', 'boxStack', 'pallet', 'barrel', 'cart'], 14);
  for (let k = 0; k < 5; k++) closet(b, ['s', 'e', 'w'], 'locker');
  // key on a random shelf
  const ks = b.rng.pick(shelves);
  const [kx, kz] = b.propLocal(ks, b.rng.range(-1.4, 1.4), b.rng.chance(0.5) ? 0.6 : -0.6);
  b.addInteractable('pickup', kx, b.rng.pick([0.9, 1.6]), kz, { loot: { item: 'electricalKey' } });
  // ten breaker switches around the warehouse
  const crates = r.props.filter((p) => p.type === 'crate' || p.type === 'boxStack' || p.type === 'pallet');
  const spots: [number, number, number][] = [];
  for (const s of shelves) for (const f of [-1, 1]) spots.push([...b.propLocal(s, b.rng.range(-1.4, 1.4), f * 0.65), b.rng.pick([0.25, 0.9])] as [number, number, number]);
  for (const c of crates) { const fp = FOOTPRINT[c.type]!; spots.push([c.x, c.z, fp[2] + 0.05]); }
  b.rng.shuffle(spots);
  for (let k = 0; k < 10; k++) {
    const [x, z, y] = spots[k];
    b.addInteractable('pickup', x, y, z, { loot: { item: 'breaker' } });
  }
  // breaker rounds: three rounds of lit switches
  const rounds: number[][] = [];
  for (let k = 0; k < 3; k++) {
    const set = [...Array(10).keys()].map((i) => i + 1).filter(() => b.rng.chance(0.5));
    if (set.length < 2) set.push(b.rng.int(1, 10));
    rounds.push([...new Set(set)].sort((a, c) => a - c));
  }
  r.puzzle = { breakerRounds: rounds };
  for (let k = 0; k < 6; k++) {
    const x = r.xMin + 4 + (k % 3) * 12, z = b.iz0 + 6 + Math.floor(k / 3) * 14;
    b.addProp('ceilingLight', x, z, 0, { y: r.height, size: null, variant: 2 });
    b.addLight(x, r.height - 0.6, z, { color: 0xd6e4ff, intensity: k % 2 ? 2.5 : 0, range: 14, kind: 'ceiling', flicker: true });
  }
  b.addLight(ex0 + 3.5, 3.2, ez0 + 4, { color: 0xff3020, intensity: 2.5, range: 8, kind: 'emergency' });
  b.addLight(elX, 2.8, elZ, { color: 0xff3020, intensity: 1.6, range: 6, kind: 'emergency' });
  b.addProp('cables', b.cx, b.cz, 0, { size: null, sx: r.xMax - r.xMin, sz: r.depth });
}

function halt(b: RoomBuilder) {
  const r = b.room;
  const n = Math.round(r.depth / 5);
  for (let k = 0; k < n; k++) {
    const z = b.iz0 + 2.5 + k * 5;
    b.addProp('ceilingLight', 0, z, 0, { y: r.height, size: null, variant: 1 });
    b.addLight(0, r.height - 0.3, z, { color: 0x6f95ff, intensity: 3, range: 7, kind: 'ceiling', flicker: true });
  }
  decorateWalls(b, 4, 0, ['e', 'w']);
  r.exits[r.realExit].lock = 'halt';
  r.tags.push('halt');
}

function dupe(b: RoomBuilder) {
  decorateWalls(b, 2, 2, ['e', 'w', 's']);
  container(b, 'dresser', 2, { walls: ['e', 'w', 's'] });
  closet(b, ['e', 'w', 's']);
  ceilingLights(b, 'ceilingLight', 1);
  b.room.tags.push('dupe');
}

function seekRoom(b: RoomBuilder, kind: RoomData['kind']) {
  const r = b.room;
  if (kind === 'seekHall') {
    ceilingLights(b, 'ceilingLight', Math.round(r.depth / 6), 4, 8);
    for (let k = 0; k < 26; k++) {
      const wall = b.rng.pick<Wall>(['e', 'w']);
      const t = b.rng.range(b.iz0 + 1, b.iz1 - 1);
      const p = wallAt(b, wall, t);
      b.addProp('seekEyes', p.x, p.z, p.rot, { y: b.rng.range(0.4, 2.8), size: null, variant: b.rng.int(0, 3) });
    }
    r.tags.push('seekStart');
  } else if (kind === 'seekChase') {
    ceilingLights(b, 'ceilingLight', Math.round(r.depth / 6), 3.5, 8);
    // grabbing hands on the walls and obstacles to crouch under
    const n = b.rng.int(5, 8);
    for (let k = 0; k < n; k++) {
      const side = b.rng.chance(0.5) ? -1 : 1;
      const z = b.rng.range(b.iz0 + 3, b.iz1 - 3);
      b.addProp('hands', side * (b.w / 2 - 0.35), z, side < 0 ? 1 : 3, { size: null, y: 0 });
    }
    const cut = b.rng.int(1, 2);
    for (let k = 0; k < cut; k++) {
      const z = b.iz0 + (r.depth * (k + 1)) / (cut + 1);
      b.addProp('fallenShelf', 0, z, 0, { size: [b.w, 0.5, 2.0], crouch: 1.18, occupy: false, collide: true });
    }
    scatter(b, ['debris', 'chair', 'crate'], 3);
    decorateWalls(b, 2, 0, ['e', 'w']);
  } else if (kind === 'seekCross') {
    ceilingLights(b, 'chandelier', 1, 4, 12);
    const ex = r.exits[r.realExit];
    b.addProp('guidingLight', ex.lx, b.iz1 - 0.6, 2, { size: null });
    b.addLight(ex.lx, 2.0, b.iz1 - 0.8, { color: 0x7fd0ff, intensity: 3.5, range: 6, kind: 'guide' });
    for (const e of r.exits) if (!e.real) b.addProp('debris', e.lx, b.iz1 - 0.6, 0, { size: null });
    scatter(b, ['debris', 'armchair', 'table', 'chair'], 4);
  } else {
    // finale: burning room, falling chandeliers, guiding light to the exit
    const ex = r.exits[r.realExit];
    for (let k = 0; k < 9; k++) {
      const x = b.rng.range(r.xMin + 1, r.xMax - 1), z = b.rng.range(b.iz0 + 3, b.iz1 - 3);
      if (Math.abs(x) < 1.6) continue;
      b.addProp('fire', x, z, 0, { size: null, variant: k % 3 });
      b.addLight(x, 1.0, z, { color: 0xff7a2a, intensity: 2.2, range: 6, kind: 'fire', flicker: true });
    }
    for (let k = 0; k < 3; k++) {
      const z = b.iz0 + 5 + k * 6;
      b.addProp('chandelier', b.rng.range(-1.2, 1.2), z, 0, { y: r.height, size: null, data: 1, variant: 0 });
    }
    b.addProp('guidingLight', ex.lx, b.iz1 - 0.6, 2, { size: null });
    b.addLight(ex.lx, 2.0, b.iz1 - 0.8, { color: 0x7fd0ff, intensity: 4, range: 8, kind: 'guide' });
    scatter(b, ['debris', 'debris', 'bookshelf', 'armchair'], 5);
    r.tags.push('seekEnd');
  }
}

// ---------------------------------------------------------------- dispatcher

export function furnish(b: RoomBuilder, plan: RunPlan) {
  const r = b.room;
  reserveDoors(b);
  if (r.kind !== 'stairs') reserveLane(b);
  else b.laneMid = undefined;
  switch (r.kind) {
    case 'reception': reception(b); break;
    case 'hallway': hallway(b); break;
    case 'bedroom': bedroom(b); break;
    case 'office': office(b); break;
    case 'storage': storage(b); break;
    case 'dining': dining(b); break;
    case 'intersection': intersection(b); break;
    case 'stairs': stairs(b); break;
    case 'lounge': lounge(b); break;
    case 'bathroom': bathroom(b); break;
    case 'kitchen': kitchen(b); break;
    case 'gallery': gallery(b); break;
    case 'closetRoom': closetRoom(b); break;
    case 'darkRoom': bedroom(b); break;
    case 'basement': basement(b); break;
    case 'library': library(b, b.rng.fork(50)); break;
    case 'vestibule': vestibule(b); break;
    case 'shop': shop(b); break;
    case 'infirmary': infirmary(b); break;
    case 'courtyard': courtyard(b); break;
    case 'greenhouse': greenhouse(b); break;
    case 'electrical': electrical(b); break;
    case 'halt': halt(b); break;
    case 'dupe': dupe(b); break;
    default: seekRoom(b, r.kind);
  }
  // Every ordinary room gets at least one hiding spot so entity encounters are always survivable.
  const needsHide = !['reception', 'shop', 'courtyard', 'halt', 'seekHall', 'seekChase', 'seekCross', 'seekFinale', 'library', 'vestibule', 'electrical'].includes(r.kind);
  if (needsHide && hideCount(r) === 0) {
    if (!closet(b) && !closet(b, undefined, 'locker')) throw new Error('could not place a hiding spot');
  }
  lockHardware(b);
  // seek eye decorations in the rooms leading up to a chase
  for (const c of plan.chases) {
    const k = c.start - r.index;
    if (k >= 1 && k <= 5) {
      const n = (6 - k) * 3;
      for (let e = 0; e < n; e++) {
        const wall = b.rng.pick<Wall>(['e', 'w', 'n', 's']);
        const [t] = wallSpots(b, wall, 0.3, 1);
        if (t === undefined) continue;
        const p = wallAt(b, wall, t);
        b.addProp('seekEyes', p.x, p.z, p.rot, { y: b.floorAt(p.z) + b.rng.range(0.5, 2.6), size: null, variant: b.rng.int(0, 3) });
      }
      r.tags.push('seekEyes');
    }
  }
}

export type { Rect };
