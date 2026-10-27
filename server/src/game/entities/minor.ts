import type { EntitySnap, RoomData, Vec3 } from '@doors/shared';
import { dist, lookDir, toLocal, toWorld, WALL_T } from '@doors/shared';
import type { Entity, Game, PlayerState } from '../types';
import { r2 } from './common';

const angleTo = (g: Game, p: PlayerState, target: Vec3) => {
  const eye = g.eye(p);
  const d = lookDir(p.yaw, p.pitch);
  const vx = target.x - eye.x, vy = target.y - eye.y, vz = target.z - eye.z;
  const L = Math.hypot(vx, vy, vz) || 1;
  return Math.acos(Math.max(-1, Math.min(1, (d.x * vx + d.y * vy + d.z * vz) / L)));
};

/** Screech: appears beside a player in a dark room; look at it within the window or get bitten. */
export class ScreechEntity implements Entity {
  id: string;
  kind = 'screech' as const;
  pos: Vec3;
  yaw = 0;
  state = 'lurk';
  done = false;
  private t = 0;
  constructor(private g: Game, private target: PlayerState) {
    this.id = g.nextId('screech');
    const a = target.yaw + Math.PI + (g.rng.next() - 0.5) * 2.2; // mostly behind
    const eye = g.eye(target);
    const r = 1.6;
    this.pos = { x: eye.x - Math.sin(a) * r, y: eye.y + (g.rng.next() - 0.3) * 0.6, z: eye.z - Math.cos(a) * r };
    // keep it inside the room
    if (!g.world.lineOfSight(eye, this.pos, [target.room])) this.pos = { x: eye.x + Math.sin(target.yaw) * r, y: eye.y, z: eye.z + Math.cos(target.yaw) * r };
    this.yaw = Math.atan2(eye.x - this.pos.x, eye.z - this.pos.z);
    g.emit({ e: 'warn', kind: 'screech', target: target.id, dir: Math.atan2(-(this.pos.x - eye.x), -(this.pos.z - eye.z)) });
  }
  update(dt: number) {
    this.t += dt;
    const p = this.target;
    if (p.dead || p.hidden || !p.connected) { this.done = true; this.state = 'gone'; return; }
    if (this.state === 'lurk') {
      if (angleTo(this.g, p, this.pos) < 0.55) {
        this.state = 'flee';
        this.g.emit({ e: 'entity', kind: 'screech', phase: 'flee', id: this.id });
        this.t = 0;
      } else if (this.t > 3.4) {
        this.state = 'attack';
        this.g.emit({ e: 'entity', kind: 'screech', phase: 'attack', id: this.id });
        this.g.damage(p, 40, 'screech');
        this.t = 0;
      }
    } else if (this.t > 0.8) { this.done = true; this.state = 'gone'; }
  }
  crucifix(p: PlayerState) {
    if (p !== this.target || !this.g.useCrucifix(p, 'screech')) return false;
    this.state = 'flee'; this.t = 0;
    return true;
  }
  snap(): EntitySnap {
    return { id: this.id, kind: 'screech', x: r2(this.pos.x), y: r2(this.pos.y), z: r2(this.pos.z), yaw: r2(this.yaw), state: this.state, room: this.target.room };
  }
}

/** Eyes: hovering cluster. Looking at it (with line of sight) hurts. */
export class EyesEntity implements Entity {
  id: string;
  kind = 'eyes' as const;
  pos: Vec3;
  yaw = 0;
  state = 'idle';
  done = false;
  room: number;
  private tick = 0;
  constructor(private g: Game, r: RoomData) {
    this.id = g.nextId('eyes');
    this.room = r.index;
    const cx = (r.xMin + r.xMax) / 2;
    this.pos = toWorld(r, cx, 1.9, WALL_T + r.depth / 2);
    g.emit({ e: 'entity', kind: 'eyes', phase: 'spawn', id: this.id, room: r.index });
  }
  update(dt: number) {
    if (this.g.furthestDoor > this.room + 1 && this.g.alive().every((p) => p.room > this.room || p.dead)) { this.done = true; this.state = 'gone'; return; }
    this.tick += dt;
    if (this.tick < 0.25) return;
    this.tick = 0;
    for (const p of this.g.alive()) {
      if (p.hidden || !p.connected || Math.abs(p.room - this.room) > 1) continue;
      const eye = this.g.eye(p);
      if (dist(eye, this.pos) > 28) continue;
      if (angleTo(this.g, p, this.pos) > 0.62) continue;
      if (!this.g.world.lineOfSight(eye, this.pos, [this.room - 1, this.room, this.room + 1])) continue;
      if (this.crucifix(p)) return;
      this.g.damage(p, 5, 'eyes');
    }
  }
  crucifix(p: PlayerState) {
    if (!this.g.useCrucifix(p, 'eyes')) return false;
    this.done = true; this.state = 'gone';
    this.g.emit({ e: 'entity', kind: 'eyes', phase: 'banished', id: this.id, room: this.room });
    return true;
  }
  snap(): EntitySnap {
    return { id: this.id, kind: 'eyes', x: r2(this.pos.x), y: r2(this.pos.y), z: r2(this.pos.z), yaw: 0, state: this.state, room: this.room };
  }
}

/**
 * Halt: the corridor alternates between "behind" (walk forward) and "front" (TURN AROUND) phases.
 * Halt glides toward the players; touching it hurts. After the sequence the exit unlocks.
 */
export class HaltEntity implements Entity {
  id: string;
  kind = 'halt' as const;
  pos: Vec3;
  yaw = 0;
  state = 'behind';
  done = false;
  room: number;
  private phase = 0;
  private t = 0;
  private lz = 0;
  private dirSign = 1;
  private readonly phases = ['behind', 'front', 'behind', 'front', 'behind'];
  constructor(private g: Game, private r: RoomData, private onDone: () => void) {
    this.id = g.nextId('halt');
    this.room = r.index;
    this.lz = WALL_T + 0.5;
    this.pos = toWorld(r, 0, 0, this.lz);
    this.start();
  }
  private start() {
    const ps = this.g.alive().filter((p) => p.room === this.room);
    const zs = ps.map((p) => toLocal(this.r, p.pos.x, p.pos.z)[1]);
    this.state = this.phases[this.phase];
    if (this.state === 'behind') {
      this.lz = Math.max(WALL_T + 0.3, (zs.length ? Math.min(...zs) : WALL_T) - 6);
      this.dirSign = 1;
    } else {
      this.lz = Math.min(WALL_T + this.r.depth - 0.3, (zs.length ? Math.max(...zs) : WALL_T) + 8);
      this.dirSign = -1;
    }
    this.t = 0;
    this.g.emit({ e: 'warn', kind: this.state === 'front' ? 'haltFront' : 'haltBehind', room: this.room });
  }
  update(dt: number) {
    this.t += dt;
    const dur = this.state === 'front' ? 5 : 7;
    const ps = this.g.alive().filter((p) => p.room === this.room);
    const zs = ps.map((p) => toLocal(this.r, p.pos.x, p.pos.z)[1]);
    if (this.state === 'behind') {
      // follows from behind but never closes in: walking forward is always safe
      const rear = zs.length ? Math.min(...zs) : WALL_T;
      this.lz = Math.min(this.lz + 2.0 * dt, rear - 3.2);
    } else {
      this.lz -= 2.0 * dt;
    }
    this.lz = Math.max(WALL_T + 0.3, Math.min(WALL_T + this.r.depth - 0.3, this.lz));
    this.pos = toWorld(this.r, 0, 1.0, this.lz);
    this.yaw = ((this.r.rot + (this.dirSign > 0 ? 0 : 2)) * Math.PI) / 2 + Math.PI;
    for (const p of this.g.alive()) {
      if (p.room !== this.room || this.state !== 'front') continue;
      const [, pz] = toLocal(this.r, p.pos.x, p.pos.z);
      if (Math.abs(pz - this.lz) < 0.9) {
        const k = `halt:${p.id}`;
        if ((p.cooldowns.get(k) ?? 0) < this.g.time) {
          p.cooldowns.set(k, this.g.time + 1.5);
          this.g.damage(p, 40, 'halt');
        }
      }
    }
    if (this.t > dur) {
      this.phase++;
      if (this.phase >= this.phases.length) {
        this.done = true; this.state = 'gone';
        this.g.emit({ e: 'entity', kind: 'halt', phase: 'gone', id: this.id, room: this.room });
        this.onDone();
        return;
      }
      this.start();
    }
  }
  snap(): EntitySnap {
    return { id: this.id, kind: 'halt', x: r2(this.pos.x), y: r2(this.pos.y), z: r2(this.pos.z), yaw: r2(this.yaw), state: this.state, room: this.room };
  }
}
