import type { Rng } from '../rng';
import type { ExitData, RoomData, RoomKind, RoomTheme, FloorMat } from '../types';
import { DOOR_H, DOOR_W, WALL_T } from '../constants';
import { rotXZ } from '../math';

export interface ShellSpec {
  w: number; d: number; h: number; xMin: number;
  exits: { dir: number; pos: number; real: boolean; fake?: 'dupe' | 'blocked' }[];
  rise: number;
}

const WOOD = [0x5a3a22, 0x6b4428, 0x4a2e1c, 0x7a5232, 0x3d2616];
const WALL = [0x7a6a52, 0x6e5b48, 0x5f6a5a, 0x7a5c4e, 0x6a6258, 0x584a3e, 0x6f6553];
const CARPET = [0x5c1f1f, 0x2f3d5c, 0x3d4a2a, 0x5a3a1e, 0x4a2238];

export function rollTheme(kind: RoomKind, rng: Rng, index: number): RoomTheme {
  let floor: FloorMat = rng.pick(['carpet', 'carpet', 'wood', 'wood'] as FloorMat[]);
  if (kind === 'bathroom' || kind === 'kitchen' || kind === 'infirmary') floor = 'tile';
  if (kind === 'storage' || kind === 'basement' || kind === 'electrical') floor = 'concrete';
  if (kind === 'reception' || kind === 'gallery' || kind === 'vestibule') floor = 'marble';
  if (kind === 'library' || kind === 'shop') floor = 'wood';
  if (kind === 'courtyard') floor = 'grass';
  if (kind === 'greenhouse') floor = 'stone';
  if (kind === 'halt') floor = 'carpet';
  const late = index > 60;
  return {
    wallpaper: rng.int(0, 5),
    wallTint: rng.pick(WALL),
    floor,
    carpetTint: rng.pick(CARPET),
    woodTone: rng.pick(WOOD),
    wainscot: rng.chance(0.6),
    ceilingBeams: rng.chance(0.3),
    lightColor: kind === 'halt' ? 0x7fa8ff : late ? rng.pick([0xffc98a, 0xffb870, 0xffd9a8]) : rng.pick([0xffcf8f, 0xffd8a0, 0xffc480]),
    outdoor: kind === 'courtyard' || undefined,
    glass: kind === 'greenhouse' || undefined,
  };
}

/** Candidate shell specs per room kind. Several are rolled per room to give placement alternatives. */
export function shellSpec(kind: RoomKind, rng: Rng): ShellSpec {
  const R = (a: number, b: number) => Math.round(rng.range(a, b) * 2) / 2;
  let w = 8, d = 8, h = 3.2, rise = 0;
  let centered = false;
  let exitDirs = [0, 1, 3];
  let multi: 'dupe' | 'blocked' | null = null;
  switch (kind) {
    case 'reception': w = 16; d = 16; h = 5.5; centered = true; exitDirs = [0]; break;
    case 'hallway': w = R(3.5, 4.5); d = R(10, 18); h = 3.2; centered = true; break;
    case 'bedroom': w = R(7, 10); d = R(7, 10); break;
    case 'office': w = R(6, 9); d = R(6, 9); break;
    case 'storage': w = R(5, 8); d = R(6, 10); h = 3.0; break;
    case 'dining': w = R(10, 13); d = R(10, 13); h = 4.0; break;
    case 'intersection': w = R(8, 11); d = R(8, 11); h = 3.6; multi = rng.chance(0.5) ? 'blocked' : null; break;
    case 'stairs': w = R(5, 6.5); d = R(11, 14); h = 3.4; rise = rng.chance(0.5) ? 2.8 : -2.8; exitDirs = [0]; centered = true; break;
    case 'lounge': w = R(8, 11); d = R(8, 11); h = 3.6; break;
    case 'bathroom': w = R(5, 6.5); d = R(5, 7); h = 3.0; break;
    case 'kitchen': w = R(7, 9.5); d = R(7, 9); break;
    case 'gallery': w = R(5, 6); d = R(14, 19); h = 4.2; centered = true; break;
    case 'closetRoom': w = R(8, 11); d = R(8, 10); break;
    case 'basement': w = R(7, 10); d = R(8, 12); h = 2.8; break;
    case 'darkRoom': w = R(7, 10); d = R(7, 10); break;
    case 'library': w = 24; d = 28; h = 6.5; centered = true; exitDirs = [0]; break;
    case 'vestibule': w = 5; d = 10; h = 4; centered = true; exitDirs = [0]; break;
    case 'shop': w = 11; d = 10; h = 3.8; exitDirs = [0, 1, 3]; break;
    case 'infirmary': w = R(10, 12); d = R(12, 14); h = 3.4; break;
    case 'courtyard': w = 26; d = 30; h = 9; centered = true; exitDirs = [0]; break;
    case 'greenhouse': w = R(9, 12); d = R(14, 20); h = 5; break;
    case 'electrical': w = 32; d = 30; h = 8; centered = true; exitDirs = []; break;
    case 'halt': w = 3.6; d = 34; h = 3.4; centered = true; exitDirs = [0]; break;
    case 'dupe': w = R(8, 10); d = R(7, 9); h = 3.4; exitDirs = [0]; multi = 'dupe'; break;
    case 'seekHall': w = 4; d = R(22, 26); h = 3.6; centered = true; exitDirs = [0]; break;
    case 'seekChase': w = 5; d = R(20, 24); h = 3.6; centered = true; break;
    case 'seekCross': w = 12; d = 10; h = 4; centered = true; exitDirs = [0]; multi = 'blocked'; break;
    case 'seekFinale': w = 14; d = 24; h = 6; centered = true; exitDirs = [0]; break;
  }
  const t = centered ? 0.5 : rng.range(0.3, 0.7);
  let xMin = -w * t;
  // keep the entry door clear of corners
  xMin = Math.min(xMin, -DOOR_W / 2 - 0.4);
  if (xMin + w < DOOR_W / 2 + 0.4) xMin = DOOR_W / 2 + 0.4 - w;
  xMin = Math.round(xMin * 4) / 4;
  const xMax = xMin + w;
  const exits: ShellSpec['exits'] = [];
  if (exitDirs.length) {
    if (multi) {
      // three doors on the north wall, one real
      const realIdx = rng.int(0, 2);
      const span = w - 2.4;
      for (let k = 0; k < 3; k++) {
        const pos = xMin + 1.2 + (span * (k + 0.5)) / 3;
        exits.push({ dir: 0, pos, real: k === realIdx, fake: k === realIdx ? undefined : multi });
      }
      if (kind === 'intersection') {
        // intersections: two doors, one boarded up
        exits.splice(rng.int(0, 2), 1);
        if (!exits.some((e) => e.real)) { exits[0].real = true; exits[0].fake = undefined; }
      }
    } else {
      const dir = rng.pick(exitDirs);
      let pos: number;
      if (dir === 0) pos = centered ? 0 : rng.range(xMin + 1.3, xMax - 1.3);
      else pos = WALL_T + rng.range(d * 0.55, d - 1.3);
      exits.push({ dir, pos, real: true });
    }
  }
  return { w, d, h: h + Math.abs(rise), xMin, exits, rise };
}

/** Local opening position for an exit (on the joint plane, outside the wall). */
export function exitLocal(spec: { xMin: number; w: number; d: number }, dir: number, pos: number): [number, number] {
  if (dir === 0) return [pos, 2 * WALL_T + spec.d];
  if (dir === 1) return [spec.xMin + spec.w + WALL_T, pos];
  return [spec.xMin - WALL_T, pos];
}

/** Build wall/floor/ceiling colliders, door openings and walkable volumes. */
export function buildShell(room: RoomData, toWorldBox: (r: { x0: number; z0: number; x1: number; z1: number }, y0: number, y1: number) => import('../math').Box) {
  const { xMin, xMax, depth: D, height: H } = room;
  const T = WALL_T;
  const lo = Math.min(0, room.rise);
  const top = Math.max(0, room.rise) + (H - Math.abs(room.rise));
  const add = (x0: number, z0: number, x1: number, z1: number, y0: number, y1: number) => {
    if (x1 - x0 < 0.001 || z1 - z0 < 0.001 || y1 - y0 < 0.001) return;
    room.colliders.push(toWorldBox({ x0, z0, x1, z1 }, y0, y1));
  };
  const isStairs = room.tags.some((t) => t.startsWith('stair:'));
  if (!isStairs) add(xMin - T, 0, xMax + T, 2 * T + D, -0.4, 0);
  if (!room.theme.outdoor) add(xMin - T, 0, xMax + T, 2 * T + D, top, top + 0.3);

  // openings: entry on south (room 0's entry is the elevator; still an opening)
  room.openings.push({ wall: 's', center: 0, width: DOOR_W, height: DOOR_H, y: 0 });
  for (const e of room.exits) {
    const wall = e.dir === 0 ? 'n' : e.dir === 1 ? 'e' : 'w';
    room.openings.push({ wall, center: e.dir === 0 ? e.lx : e.lz, width: e.style === 'double' ? 2.2 : DOOR_W, height: DOOR_H, y: e.ly });
  }
  const wallSegs = (wall: 'n' | 's' | 'e' | 'w', a0: number, a1: number, mk: (s0: number, s1: number, y0: number, y1: number) => void) => {
    const ops = room.openings.filter((o) => o.wall === wall).sort((p, q) => p.center - q.center);
    let cur = a0;
    for (const o of ops) {
      const o0 = o.center - o.width / 2, o1 = o.center + o.width / 2;
      const oy = o.y ?? 0;
      mk(cur, o0, lo, top);
      mk(o0, o1, oy + o.height, top); // lintel
      if (oy > lo) mk(o0, o1, lo, oy); // below raised opening
      cur = o1;
    }
    mk(cur, a1, lo, top);
  };
  wallSegs('s', xMin - T, xMax + T, (s0, s1, y0, y1) => add(s0, 0, s1, T, y0, y1));
  wallSegs('n', xMin - T, xMax + T, (s0, s1, y0, y1) => add(s0, T + D, s1, 2 * T + D, y0, y1));
  wallSegs('w', 0, 2 * T + D, (s0, s1, y0, y1) => add(xMin - T, s0, xMin, s1, y0, y1));
  wallSegs('e', 0, 2 * T + D, (s0, s1, y0, y1) => add(xMax, s0, xMax + T, s1, y0, y1));

  // walkable volumes
  const wb = (x0: number, z0: number, x1: number, z1: number, y0: number, y1: number) => room.walkable.push(toWorldBox({ x0, z0, x1, z1 }, y0, y1));
  wb(xMin, T, xMax, T + D, lo - 0.2, top);
  wb(-DOOR_W / 2, 0, DOOR_W / 2, T, -0.2, DOOR_H);
  for (const e of room.exits) {
    if (!e.real) continue;
    const hw = DOOR_W / 2;
    if (e.dir === 0) wb(e.lx - hw, T + D, e.lx + hw, 2 * T + D, e.ly - 0.2, e.ly + DOOR_H);
    else if (e.dir === 1) wb(xMax, e.lz - hw, xMax + T, e.lz + hw, e.ly - 0.2, e.ly + DOOR_H);
    else wb(xMin - T, e.lz - hw, xMin, e.lz + hw, e.ly - 0.2, e.ly + DOOR_H);
  }
}

export function makeExit(room: RoomData, spec: ShellSpec, e: ShellSpec['exits'][number], number: number): ExitData {
  const [lx, lz] = exitLocal({ xMin: spec.xMin, w: spec.w, d: spec.d }, e.dir, e.pos);
  const [wx, wz] = rotXZ(lx, lz, room.rot);
  return {
    lx, ly: spec.rise, lz, dir: e.dir,
    world: { x: room.origin.x + wx, y: room.origin.y + spec.rise, z: room.origin.z + wz },
    worldRot: (room.rot + e.dir) & 3,
    real: e.real,
    number,
    lock: 'none',
    fake: e.fake,
    style: 'wood',
  };
}
