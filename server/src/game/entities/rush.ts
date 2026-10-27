import type { EntitySnap, Vec3 } from '@doors/shared';
import { distXZ } from '@doors/shared';
import type { Entity, Game, PlayerState } from '../types';
import { PathFollower, r2 } from './common';

/**
 * Rush and Ambush: warn with flickering lights, then sweep the room path from behind the players to the newest
 * door. Anyone not hidden near the entity dies. Ambush rebounds several times.
 */
export class RushEntity implements Entity {
  id: string;
  kind: 'rush' | 'ambush';
  pos: Vec3;
  yaw = 0;
  state = 'warn';
  done = false;
  room: number;
  private t = 0;
  private follower: PathFollower;
  private passes = 0;
  private waitT = 0;
  private roomsOnPath: number[];
  private broken = new Set<number>();

  constructor(private g: Game, kind: 'rush' | 'ambush', room: number, private delay: number, private rebounds = 0) {
    this.kind = kind;
    this.id = g.nextId(kind);
    this.room = room;
    const start = Math.max(1, room - 3);
    this.roomsOnPath = [];
    const pts: Vec3[] = [];
    for (let i = start; i <= room; i++) {
      this.roomsOnPath.push(i);
      for (const p of g.rooms[i].path) pts.push({ x: p.x, y: p.y + 1.6, z: p.z });
    }
    this.follower = new PathFollower(pts);
    this.pos = { ...pts[0] };
    g.setLights([room - 1, room].filter((r) => r >= 0), kind === 'ambush' ? 'green' : 'flicker');
    g.emit({ e: 'entity', kind, phase: 'warn', id: this.id, room });
  }

  get speed() { return this.kind === 'rush' ? 19 : 27; }

  update(dt: number) {
    this.t += dt;
    if (this.state === 'warn') {
      if (this.t >= this.delay) {
        this.state = 'run';
        this.g.emit({ e: 'entity', kind: this.kind, phase: 'run', id: this.id, room: this.room });
      }
      return;
    }
    if (this.state === 'wait') {
      this.waitT -= dt;
      if (this.waitT <= 0) {
        this.follower.reverse();
        this.state = 'run';
        this.g.emit({ e: 'entity', kind: this.kind, phase: 'rebound', id: this.id, room: this.room, data: this.passes });
      }
      return;
    }
    if (this.state !== 'run') return;
    this.follower.advance(this.speed * dt);
    this.pos = this.follower.pos;
    this.yaw = this.follower.yaw;
    // break lights in rooms it passes through
    const cur = this.g.world.roomAt({ ...this.pos, y: this.pos.y - 1.6 }, this.room);
    if (cur >= 0 && !this.broken.has(cur) && this.kind === 'rush') { this.broken.add(cur); this.g.setLights([cur], 'broken'); }
    this.checkKills();
    if (this.follower.done) {
      this.passes++;
      if (this.passes > this.rebounds) {
        this.state = 'gone';
        this.done = true;
        if (this.kind === 'ambush') this.g.setLights(this.roomsOnPath.slice(-2), 'on');
        this.g.emit({ e: 'entity', kind: this.kind, phase: 'gone', id: this.id, room: this.room });
      } else {
        this.state = 'wait';
        this.waitT = 1.6 + this.g.rng.next() * 2.4;
      }
    }
  }

  private checkKills() {
    for (const p of this.g.alive()) {
      if (p.hidden || !p.connected) continue;
      const eye = this.g.eye(p);
      if (Math.abs(eye.y - this.pos.y) > 3) continue;
      const d = distXZ(eye, this.pos);
      if (d > 14) continue;
      if (d > 3.4) {
        // anyone in plain sight in a room it's tearing through is caught too
        if (!this.roomsOnPath.includes(p.room)) continue;
        if (!this.g.world.lineOfSight(this.pos, eye, [p.room - 1, p.room, p.room + 1])) continue;
      }
      if (this.crucifix(p)) return;
      this.g.damage(p, 999, this.kind);
    }
  }

  crucifix(p: PlayerState): boolean {
    if (!this.g.useCrucifix(p, this.kind)) return false;
    this.state = 'gone';
    this.done = true;
    this.g.emit({ e: 'entity', kind: this.kind, phase: 'banished', id: this.id, room: this.room });
    return true;
  }

  snap(): EntitySnap {
    return { id: this.id, kind: this.kind, x: r2(this.pos.x), y: r2(this.pos.y), z: r2(this.pos.z), yaw: r2(this.yaw), state: this.state, room: this.room, speed: this.state === 'run' ? this.speed : 0 };
  }
}
