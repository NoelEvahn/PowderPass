import type { Box, Vec3 } from './math';
import { pointInBox, rayBox, rotXZ } from './math';
import type { RoomData, RunLayout } from './types';
import { FINAL_ROOM } from './constants';

/** Convert a world XZ position into a room's local frame. */
export function toLocal(room: RoomData, x: number, z: number): [number, number] {
  return rotXZ(x - room.origin.x, z - room.origin.z, (4 - room.rot) & 3);
}

export function toWorld(room: RoomData, lx: number, ly: number, lz: number): Vec3 {
  const [x, z] = rotXZ(lx, lz, room.rot);
  return { x: room.origin.x + x, y: room.origin.y + ly, z: room.origin.z + z };
}

/** Floor height (world y) at a world position inside a room. */
export function floorAt(room: RoomData, x: number, z: number): number {
  const st = room.tags.find((t) => t.startsWith('stair:'));
  if (!st) return room.origin.y;
  const [, lz] = toLocal(room, x, z);
  const [a, b] = st.slice(6).split(',').map(Number);
  const t = lz <= a ? 0 : lz >= b ? 1 : (lz - a) / (b - a);
  return room.origin.y + t * room.rise;
}

export function tagValue(room: RoomData, key: string): number[] | null {
  const t = room.tags.find((x) => x.startsWith(key + ':'));
  return t ? t.slice(key.length + 1).split(',').map(Number) : null;
}

/** Spatial helpers shared by server simulation and client. */
export class WorldQuery {
  readonly rooms: RoomData[];
  constructor(layout: RunLayout) {
    this.rooms = layout.rooms;
  }

  /** Find the room whose walkable volume contains p, searching outward from a hint. */
  roomAt(p: Vec3, hint = 0, pad = 0.05): number {
    const order: number[] = [hint];
    for (let d = 1; d <= FINAL_ROOM; d++) {
      if (hint + d <= FINAL_ROOM) order.push(hint + d);
      if (hint - d >= 0) order.push(hint - d);
      if (d > 6 && order.length > 14) break;
    }
    for (const i of order) {
      const r = this.rooms[i];
      if (!r || !pointInBox(p, r.bounds, 0.5)) continue;
      for (const w of r.walkable) if (pointInBox(p, w, pad)) return i;
    }
    return -1;
  }

  /** Is p inside any walkable volume of the given room? */
  inRoom(p: Vec3, i: number, pad = 0.05): boolean {
    const r = this.rooms[i];
    if (!r) return false;
    return r.walkable.some((w) => pointInBox(p, w, pad));
  }

  /** Nearest collider hit along a ray within the listed rooms. */
  raycast(o: Vec3, d: Vec3, maxT: number, roomIdx: number[], extra: Box[] = []): number {
    let best = maxT;
    for (const i of roomIdx) {
      const r = this.rooms[i];
      if (!r) continue;
      for (const b of r.colliders) {
        const t = rayBox(o, d, b, best);
        if (t < best) best = t;
      }
    }
    for (const b of extra) {
      const t = rayBox(o, d, b, best);
      if (t < best) best = t;
    }
    return best;
  }

  lineOfSight(a: Vec3, b: Vec3, roomIdx: number[], extra: Box[] = []): boolean {
    const dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
    const len = Math.hypot(dx, dy, dz);
    if (len < 1e-4) return true;
    const d = { x: dx / len, y: dy / len, z: dz / len };
    return this.raycast(a, d, len, roomIdx, extra) >= len - 0.05;
  }
}
