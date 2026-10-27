import type { EntitySnap, Vec3 } from '@doors/shared';
import { dist, distXZ } from '@doors/shared';
import type { Entity, Game, PlayerState } from '../types';
import { PathFollower, r2 } from './common';

/**
 * Seek chase: rises behind the players in the seek hallway, then pursues along the chase route.
 * Doors auto-open ahead of players; wrong doors in crossroads are blocked. The chase ends when every
 * living player passes the final door, which slams on Seek.
 */
export class SeekEntity implements Entity {
  id: string;
  kind = 'seek' as const;
  pos: Vec3;
  yaw = 0;
  state = 'rise';
  done = false;
  room: number;
  private t = 0;
  private follower: PathFollower;
  private stun = 0;
  readonly endRoom: number;

  constructor(private g: Game, public chase: number, readonly startRoom: number) {
    this.id = g.nextId('seek');
    this.room = startRoom;
    this.endRoom = startRoom + 6;
    const pts: Vec3[] = [];
    for (let i = startRoom; i <= this.endRoom + 1 && i < g.rooms.length; i++) for (const p of g.rooms[i].path) pts.push({ ...p });
    this.follower = new PathFollower(pts);
    // emerge a short way into the hallway, behind the lead player
    this.follower.advance(2.5);
    this.pos = { ...this.follower.pos };
    g.emit({ e: 'chase', phase: 'start', chase });
    g.emit({ e: 'entity', kind: 'seek', phase: 'rise', id: this.id, room: startRoom });
  }

  /** distance along path of a point (approx: nearest projected point) */
  private progressOf(p: Vec3): number {
    const pts = this.follower.pts;
    let acc = 0, best = Infinity, bestS = 0;
    for (let i = 0; i < pts.length - 1; i++) {
      const a = pts[i], b = pts[i + 1];
      const vx = b.x - a.x, vz = b.z - a.z, L = Math.hypot(vx, vz);
      if (L < 1e-6) continue;
      const t = Math.max(0, Math.min(1, ((p.x - a.x) * vx + (p.z - a.z) * vz) / (L * L)));
      const qx = a.x + vx * t, qz = a.z + vz * t;
      const d = Math.hypot(p.x - qx, p.z - qz) + Math.abs(p.y - (a.y + (b.y - a.y) * t)) * 2;
      if (d < best) { best = d; bestS = acc + L * t; }
      acc += L;
    }
    return bestS;
  }
  private selfProgress(): number { return this.progressOf(this.pos); }

  update(dt: number) {
    this.t += dt;
    if (this.state === 'rise') {
      if (this.t > 2.4) { this.state = 'chase'; this.g.emit({ e: 'entity', kind: 'seek', phase: 'chase', id: this.id }); }
      return;
    }
    if (this.state === 'gone') return;
    if (this.stun > 0) { this.stun -= dt; return; }
    const alive = this.g.alive().filter((p) => p.connected);
    if (!alive.length) { this.end(false); return; }
    // chase over when every living player is past the final door
    if (alive.every((p) => p.room > this.endRoom)) { this.end(true); return; }
    const me = this.selfProgress();
    const chasers = alive.filter((p) => p.room >= this.startRoom && p.room <= this.endRoom + 1);
    const lead = chasers.length ? Math.min(...chasers.map((p) => this.progressOf(p.pos))) - me : 99;
    // rubber band: relentless but beatable at a sprint
    const speed = lead > 16 ? 7.2 : lead > 9 ? 5.6 : 4.9;
    this.follower.advance(speed * dt);
    this.pos = { ...this.follower.pos };
    this.yaw = this.follower.yaw;
    this.room = Math.max(this.room, this.g.world.roomAt(this.pos, this.room));
    // auto-open the route's doors near players so the chase flows
    for (const p of alive) {
      const r = p.room;
      if (r < this.startRoom || r > this.endRoom) continue;
      const ex = this.g.rooms[r].exits[this.g.rooms[r].realExit];
      if (ex && !this.g.isDoorOpen(r + 1) && distXZ(p.pos, ex.world) < 4.0) this.g.openDoor(r + 1, p.id);
    }
    for (const p of alive) {
      if (p.room < this.startRoom - 1 || p.room > this.endRoom + 1) continue;
      if (dist(p.pos, this.pos) < 1.5) {
        if (this.crucifix(p)) return;
        this.g.damage(p, 999, 'seek');
      }
    }
  }

  private end(escaped: boolean) {
    this.state = 'gone';
    this.done = true;
    if (escaped) this.g.closeDoor(this.endRoom + 1);
    this.g.emit({ e: 'chase', phase: 'end', chase: this.chase });
    this.g.emit({ e: 'entity', kind: 'seek', phase: 'gone', id: this.id });
  }

  crucifix(p: PlayerState): boolean {
    if (!this.g.useCrucifix(p, 'seek')) return false;
    this.stun = 4;
    // knocked back along the route
    const back = Math.max(0, this.follower.i - 2);
    this.follower.i = back;
    this.follower.pos = { ...this.follower.pts[back] };
    this.pos = { ...this.follower.pos };
    this.g.emit({ e: 'entity', kind: 'seek', phase: 'stunned', id: this.id });
    return true;
  }

  snap(): EntitySnap {
    return { id: this.id, kind: 'seek', x: r2(this.pos.x), y: r2(this.pos.y), z: r2(this.pos.z), yaw: r2(this.yaw), state: this.stun > 0 ? 'stunned' : this.state, room: this.room };
  }
}
