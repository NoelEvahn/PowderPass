import { Rng, hash2 } from '../rng';
import type { Box, Vec3 } from '../math';
import { boxOverlap, rotXZ } from '../math';
import type { RoomData, RunLayout, ExitData } from '../types';
import { FINAL_ROOM, WALL_T } from '../constants';
import { planRun } from './plan';
import { buildShell, makeExit, rollTheme, shellSpec, type ShellSpec } from './shell';
import { RoomBuilder } from './builder';
import { furnish } from './templates';
import { scheduleEncounters } from './encounters';
import { roomIssues } from './validate';

export const LAYOUT_VERSION = 3;

function boundsFor(origin: Vec3, rot: number, spec: ShellSpec, index: number): Box {
  const x0 = spec.xMin - WALL_T, x1 = spec.xMin + spec.w + WALL_T;
  const z0 = index === 0 ? -3.2 : 0, z1 = 2 * WALL_T + spec.d;
  const a = rotXZ(x0, z0, rot), b = rotXZ(x1, z1, rot);
  const lo = Math.min(0, spec.rise) - 0.4;
  const hi = Math.max(0, spec.rise) + (spec.h - Math.abs(spec.rise)) + 0.3;
  return {
    minX: origin.x + Math.min(a[0], b[0]), maxX: origin.x + Math.max(a[0], b[0]),
    minY: origin.y + lo, maxY: origin.y + hi,
    minZ: origin.z + Math.min(a[1], b[1]), maxZ: origin.z + Math.max(a[1], b[1]),
  };
}

function emptyRoom(index: number, plan: ReturnType<typeof planRun>, spec: ShellSpec, origin: Vec3, rot: number, rng: Rng): RoomData {
  const kind = plan.kinds[index];
  return {
    index, kind, rot, origin,
    xMin: spec.xMin, xMax: spec.xMin + spec.w, depth: spec.d, height: spec.h, rise: spec.rise,
    dark: plan.dark[index],
    theme: rollTheme(kind, rng, index),
    exits: [], realExit: -1, props: [], interactables: [], lights: [], colliders: [], crouchColliders: [], walkable: [],
    bounds: boundsFor(origin, rot, spec, index), path: [], encounters: [], tags: [], openings: [],
  };
}

/**
 * Generate the full hotel (rooms 0–100) deterministically from a seed.
 * Placement uses backtracking so no two rooms overlap; every room is furnished from templates and validated.
 */
export function generateRun(seed: number): RunLayout {
  const root = new Rng(seed);
  const plan = planRun(root.fork(1));
  const specs: ShellSpec[] = [];
  const placed: { origin: Vec3; rot: number; bounds: Box }[] = [];
  const attempts: number[] = new Array(FINAL_ROOM + 1).fill(0);
  let i = 0;
  let guard = 0;
  while (i <= FINAL_ROOM) {
    if (++guard > 20000) throw new Error(`generateRun(${seed}): placement failed`);
    const rng = new Rng(hash2(seed, i * 7919 + attempts[i] * 104729));
    let origin: Vec3, rot: number;
    if (i === 0) { origin = { x: 0, y: 0, z: 0 }; rot = 0; }
    else {
      const prev = placed[i - 1], ps = specs[i - 1];
      const e = ps.exits.find((x) => x.real)!;
      const tmp = { origin: prev.origin, rot: prev.rot } as RoomData;
      const ex = makeExit(tmp, ps, e, 0);
      origin = ex.world; rot = ex.worldRot;
    }
    const spec = shellSpec(plan.kinds[i], rng);
    // heading rule: never let the next room face south (reduces self-intersection)
    const real = spec.exits.find((e) => e.real);
    const nextRot = real ? (rot + real.dir) & 3 : 0;
    const b = boundsFor(origin, rot, spec, i);
    // also reserve space for the next doorway's swing area
    let ok = i === FINAL_ROOM || nextRot !== 2;
    if (ok) {
      for (let j = 0; j < i - 1 && ok; j++) if (boxOverlap(b, placed[j].bounds)) ok = false;
      if (ok && i > 0 && boxOverlap(b, placed[i - 1].bounds, 0.01)) ok = false;
    }
    if (ok) {
      specs[i] = spec;
      placed[i] = { origin, rot, bounds: b };
      i++;
      if (i <= FINAL_ROOM) attempts[i] = 0;
    } else {
      attempts[i]++;
      if (attempts[i] > 14) {
        // backtrack: re-roll the previous room
        attempts[i] = 0;
        i = Math.max(1, i - 1);
        attempts[i]++;
      }
    }
  }

  const rooms: RoomData[] = [];
  for (let k = 0; k <= FINAL_ROOM; k++) {
    rooms.push(buildRoom(seed, k, plan, specs[k], placed[k].origin, placed[k].rot));
  }
  scheduleEncounters(rooms, plan, root.fork(2));
  return { seed, rooms, seekChases: plan.chases, version: LAYOUT_VERSION };
}

function buildRoom(seed: number, index: number, plan: ReturnType<typeof planRun>, spec: ShellSpec, origin: Vec3, rot: number): RoomData {
  // Furnishing is retried with different salts until the room validates.
  let last: Error | null = null;
  for (let salt = 0; salt < 12; salt++) {
    const rng = new Rng(hash2(seed ^ 0x51ed27, index * 31337 + salt * 7));
    const room = emptyRoom(index, plan, spec, origin, rot, rng);
    const b = new RoomBuilder(room, rng);
    room.exits = spec.exits.map((e) => makeExit(room, spec, e, index + 1));
    room.realExit = room.exits.findIndex((e) => e.real);
    const lock = plan.locks[index];
    const real = room.exits[room.realExit];
    if (real && lock !== 'none') {
      real.lock = lock;
      if (lock === 'lever') real.style = 'gate';
    }
    numberFakes(room, rng);
    try {
      furnish(b, plan);
      buildShell(room, (r, y0, y1) => b.rectToWorldBox(r, y0, y1));
      room.path = computePath(b);
      const issues = roomIssues(room);
      if (issues.length) throw new Error(issues.join('; '));
      return room;
    } catch (err) {
      last = err as Error;
    }
  }
  throw new Error(`room ${index} (${plan.kinds[index]}) failed to furnish: ${last?.message}`);
}

function numberFakes(room: RoomData, rng: Rng) {
  const n = room.index + 1;
  const decoys = rng.shuffle([n + 1, n - 1 > room.index ? n - 1 : n + 2, n + 10, n + 3, Number(String(n).split('').reverse().join('')) || n + 4].filter((x) => x !== n));
  let k = 0;
  for (const e of room.exits) {
    if (e.real) continue;
    if (e.fake === 'dupe') e.number = decoys[k++ % decoys.length];
    else e.number = n;
  }
}

/** Waypoints from entry to the real exit (world space), following the clear lane. */
export function computePath(b: RoomBuilder): Vec3[] {
  const room = b.room;
  const pts: [number, number][] = [];
  pts.push([0, -0.3]);
  pts.push([0, WALL_T + 0.8]);
  const ex = room.exits[room.realExit];
  if (!ex) {
    // final room: path to the centre then elevator target recorded in tags
    const el = room.tags.find((t) => t.startsWith('elevator:'));
    if (el) {
      const [x, z] = el.slice(9).split(',').map(Number);
      pts.push([0, z]);
      pts.push([x, z]);
    }
  } else if (ex.dir === 0) {
    const mid = b.laneMid ?? WALL_T + room.depth / 2;
    pts.push([0, mid]);
    pts.push([ex.lx, mid]);
    pts.push([ex.lx, ex.lz - WALL_T - 0.8]);
    pts.push([ex.lx, ex.lz + 0.3]);
  } else {
    pts.push([0, ex.lz]);
    const inner = ex.dir === 1 ? ex.lx - WALL_T - 0.8 : ex.lx + WALL_T + 0.8;
    pts.push([inner, ex.lz]);
    pts.push([ex.dir === 1 ? ex.lx + 0.3 : ex.lx - 0.3, ex.lz]);
  }
  const out: Vec3[] = [];
  for (const [x, z] of pts) {
    const p = b.toWorld(x, b.floorAt(z), z);
    const last = out[out.length - 1];
    if (!last || Math.hypot(last.x - p.x, last.z - p.z) > 0.05) out.push(p);
  }
  return out;
}

export type { ExitData };
