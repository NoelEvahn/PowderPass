import type { GameEvent, Interactable, RoomData, S2C, Vec3 } from '@doors/shared';
import { FINAL_ROOM, PLAYER, TICK_DT, dist, distXZ, floorAt, toLocal } from '@doors/shared';
import type { GameRoom } from '../src/game/GameRoom';
import { NavGrid } from '../src/game/nav';

/** Collects messages for a simulated client. */
export class FakePeer {
  msgs: S2C[] = [];
  events: GameEvent[] = [];
  constructor(public id: string) {}
  send(m: S2C) {
    this.msgs.push(m);
    if (m.t === 'ev') this.events.push(...m.list);
    if (this.msgs.length > 400) this.msgs.splice(0, 200);
  }
}

const navCache = new WeakMap<RoomData, NavGrid>();
const nav = (r: RoomData) => { let n = navCache.get(r); if (!n) { n = new NavGrid(r, 0.36); navCache.set(r, n); } return n; };

/**
 * A scripted player that plays the run through the same message API as a real client:
 * walks, opens doors, finds keys, hides from Rush/Ambush, faces Screech, handles Halt,
 * solves the library and electrical room puzzles and boards the elevator.
 */
export class Bot {
  seq = 0;
  yaw = 0;
  pitch = -1.2;
  crouch = false;
  sprint = false;
  private route: Vec3[] = [];
  private routeGoal = '';
  private stuck = 0;
  log: string[] = [];
  digits = new Map<number, number>();
  lit: number[] = [];
  switches: boolean[] = new Array(10).fill(false);
  private waitUntil = 0;
  private visited = new Set<string>();

  constructor(public game: GameRoom, public id: string, public peer: FakePeer) {}

  get p() { return this.game.getPlayer(this.id)!; }
  get room() { return this.game.rooms[this.p.room]; }
  private send(m: Parameters<GameRoom['handle']>[1]) { this.game.handle(this.id, m); }
  interact(id: string) { this.send({ t: 'interact', id }); }

  private consumeEvents() {
    for (const e of this.peer.events) {
      if (e.e === 'book' && e.shape !== undefined) this.digits.set(e.shape, e.digit!);
      if (e.e === 'breaker') { this.lit = e.lit; this.switches = [...e.switches]; }
    }
    this.peer.events = [];
  }

  /** Move toward a world point using the room's navigation grid; returns true when within `tol`. */
  private go(target: Vec3, tol = 0.35, key = `${target.x.toFixed(2)},${target.z.toFixed(2)}`): boolean {
    const p = this.p;
    if (distXZ(p.pos, target) <= tol) return true;
    if (this.routeGoal !== key || !this.route.length) {
      this.routeGoal = key;
      const n = nav(this.room);
      this.route = n.findPath(p.pos, target) ?? [target];
      if (this.route.length && distXZ(this.route[this.route.length - 1], target) > 0.05) this.route.push(target);
    }
    this.stepAlong();
    return distXZ(this.p.pos, target) <= tol;
  }

  /** Follow a straight sequence of points (through doorways). */
  private stepAlong() {
    const p = this.p;
    const speed = this.p.snared > 0 ? 0 : this.crouch ? PLAYER.crouchSpeed * 0.95 : this.sprint ? PLAYER.sprintSpeed * 0.95 : PLAYER.walkSpeed * 0.95;
    let budget = speed * TICK_DT;
    let pos = { ...p.pos };
    while (budget > 0 && this.route.length) {
      const t = this.route[0];
      const d = distXZ(pos, t);
      if (d < 1e-3) { this.route.shift(); continue; }
      const k = Math.min(1, budget / d);
      pos = { x: pos.x + (t.x - pos.x) * k, y: pos.y, z: pos.z + (t.z - pos.z) * k };
      this.yaw = Math.atan2(-(t.x - p.pos.x), -(t.z - p.pos.z));
      budget -= d * k;
      if (k >= 1) this.route.shift();
    }
    const ri = this.game.world.roomAt(pos, p.room, 0.4);
    const r = this.game.rooms[ri >= 0 ? ri : p.room];
    pos.y = floorAt(r, pos.x, pos.z);
    const before = { ...p.pos };
    this.send({ t: 'input', seq: ++this.seq, x: pos.x, y: pos.y, z: pos.z, yaw: this.yaw, pitch: this.pitch, crouch: this.crouch, sprint: this.sprint, moving: true });
    if (distXZ(before, this.p.pos) < 1e-4) { if (++this.stuck > 60) { this.route = []; this.stuck = 0; this.log.push(`stuck in ${p.room} at ${p.pos.x.toFixed(1)},${p.pos.z.toFixed(1)}`); } }
    else this.stuck = 0;
  }

  private idle() {
    this.send({ t: 'input', seq: ++this.seq, x: this.p.pos.x, y: this.p.pos.y, z: this.p.pos.z, yaw: this.yaw, pitch: this.pitch, crouch: this.crouch, sprint: false, moving: false });
  }

  private walkTo(target: Vec3, tol = 0.35) { return this.go(target, tol); }

  private nearestHide(): Interactable | null {
    const p = this.p;
    const cands: Interactable[] = [];
    for (const ri of [p.room, p.room - 1, p.room + 1]) {
      const r = this.game.rooms[ri];
      if (!r || (ri > p.room && !this.game.isDoorOpen(ri))) continue;
      for (const it of r.interactables) if (it.kind === 'hide' && !it.data?.jack && !this.game.debugState().hideOcc[it.id]) cands.push(it);
    }
    cands.sort((a, b) => dist(a.pos, p.pos) - dist(b.pos, p.pos));
    return cands[0] ?? null;
  }

  tick() {
    this.consumeEvents();
    const p = this.p;
    const g = this.game;
    if (p.dead) { this.send({ t: 'revive' }); return; }
    const ents = [...g.entities.values()];
    // ---- threats
    const screech = ents.find((e) => e.kind === 'screech' && (e as any).target === p);
    if (screech) {
      const eye = g.eye(p);
      this.yaw = Math.atan2(-(screech.pos.x - eye.x), -(screech.pos.z - eye.z));
      this.pitch = Math.atan2(screech.pos.y - eye.y, Math.hypot(screech.pos.x - eye.x, screech.pos.z - eye.z));
      this.idle();
      return;
    }
    this.pitch = -1.25; // eyes down: never look at Eyes
    const rush = ents.find((e) => e.kind === 'rush' || e.kind === 'ambush');
    if (rush) {
      if (p.hidden) return this.idle();
      const h = this.nearestHide();
      if (h) {
        this.sprint = true;
        if (dist(g.eye(p), h.pos) < PLAYER.interactRange) { this.interact(h.id); return; }
        this.go(h.pos, 1.0, 'hide' + h.id);
        return;
      }
    } else if (p.hidden) { this.send({ t: 'unhide' }); return; }
    if (p.hidden) return this.idle();
    const r = this.room;
    const seek = ents.some((e) => e.kind === 'seek');
    this.sprint = seek;
    this.crouch = (r.kind === 'library' || r.kind === 'electrical') && !g.powerOn;
    if (r.kind === 'halt' && !g.debugState().opened.includes(r.index + 1)) return this.halt(r);
    if (r.index === 50) return this.library(r);
    if (r.index === FINAL_ROOM) return this.electrical(r);
    this.advance(r);
  }

  /** Walk to the room's exit, dealing with locks, then through to the next room. */
  private advance(r: RoomData) {
    const n = r.index + 1;
    const ex = r.exits[r.realExit];
    const st = this.game.debugState();
    if (this.game.isDoorOpen(n)) {
      const next = this.game.rooms[n];
      const exitPt = r.path[r.path.length - 2] ?? r.path[0];
      if (distXZ(this.p.pos, exitPt) > 0.6 && this.p.room === r.index && this.routeGoal !== 'through' + n) {
        if (!this.go(exitPt, 0.5, 'exit' + n)) return;
      }
      if (this.routeGoal !== 'through' + n) { this.routeGoal = 'through' + n; this.route = [r.path[r.path.length - 1], next.path[0], next.path[1]]; }
      this.stepAlong();
      if (!this.route.length) this.routeGoal = '';
      return;
    }
    // locked?
    if (ex.lock === 'key' && !this.p.inv.some((i) => i?.type === 'key' && i.door === n) && !st.opened.includes(n)) {
      const src = r.interactables.find((i) => i.loot?.item === 'key' && i.loot.door === n)!;
      if (dist(this.game.eye(this.p), src.pos) < PLAYER.interactRange) {
        if (src.kind === 'drawer' && !st.emptied.includes(src.id)) { this.interact(src.id); if (!this.game.debugState().emptied.includes(src.id)) this.interact(src.id); }
        else this.interact(src.id);
        return;
      }
      this.go(src.pos, 1.2, 'key' + n);
      return;
    }
    if (ex.lock === 'lever') {
      const lever = r.interactables.find((i) => i.kind === 'lever')!;
      if (dist(this.game.eye(this.p), lever.pos) < PLAYER.interactRange) { this.interact(lever.id); return; }
      this.go(lever.pos, 1.2, 'lever' + n);
      return;
    }
    const doorPt = r.path[r.path.length - 2];
    if (dist(this.p.pos, ex.world) < 2.4) { this.interact(`door:${n}`); this.idle(); return; }
    this.go(doorPt, 0.5, 'door' + n);
  }

  private halt(r: RoomData) {
    const h = [...this.game.entities.values()].find((e) => e.kind === 'halt');
    if (!h) { this.go(r.path[1], 0.5, 'haltwait'); return; }
    const [, pz] = toLocal(r, this.p.pos.x, this.p.pos.z);
    const [, hz] = toLocal(r, h.pos.x, h.pos.z);
    // keep away from Halt: move opposite to it along the corridor
    const target = h.state === 'front' ? r.path[1] : r.path[r.path.length - 2];
    if (Math.abs(pz - hz) < 3.5 && ((h.state === 'front' && hz > pz) || (h.state === 'behind' && hz < pz))) {
      this.go(target, 0.5, 'halt-' + h.state);
    } else if (h.state === 'behind') this.go(target, 0.5, 'halt-' + h.state);
    else this.go(target, 0.5, 'halt-' + h.state);
  }

  private library(r: RoomData) {
    const st = this.game.debugState();
    if (st.libraryUnlocked) return this.advance(r);
    const todo = r.interactables.filter((i) => (i.kind === 'book' && !this.visited.has(i.id)) || (i.loot?.item === 'hintPaper' && !st.taken.includes(i.id)));
    if (todo.length) {
      todo.sort((a, b) => dist(a.pos, this.p.pos) - dist(b.pos, this.p.pos));
      const t = todo[0];
      if (dist(this.game.eye(this.p), t.pos) < PLAYER.interactRange) { this.interact(t.id); this.visited.add(t.id); return; }
      this.go(t.pos, 1.0, 'lib' + t.id);
      return;
    }
    const pad = r.interactables.find((i) => i.kind === 'padlock')!;
    if (dist(this.game.eye(this.p), pad.pos) < 3) {
      const code = r.puzzle!.shapes!.map((s) => this.digits.get(s)).join('');
      this.send({ t: 'code', code });
      return;
    }
    this.go(pad.pos, 1.2, 'pad');
  }

  private electrical(r: RoomData) {
    const st = this.game.debugState();
    const g = this.game;
    const near = (it: Interactable, rr = PLAYER.interactRange) => dist(g.eye(this.p), it.pos) < rr;
    if (g.powerOn) {
      const el = r.interactables.find((i) => i.kind === 'elevator')!;
      this.sprint = true;
      if (!this.go(el.pos, 0.4, 'elevator')) return;
      this.idle();
      return;
    }
    if (!st.hvDoorOpen) {
      if (!this.p.inv.some((i) => i?.type === 'electricalKey')) {
        const key = r.interactables.find((i) => i.loot?.item === 'electricalKey')!;
        if (near(key)) { this.interact(key.id); return; }
        this.go(key.pos, 1.1, 'ekey'); return;
      }
      const door = r.interactables.find((i) => i.kind === 'cabinetDoor')!;
      if (near(door)) { this.interact(door.id); return; }
      this.go(door.pos, 1.0, 'hvdoor'); return;
    }
    const box = r.interactables.find((i) => i.kind === 'breakerBox')!;
    const inserted = (this as any).inserted ?? 0;
    const carried = this.p.inv.find((i) => i?.type === 'breaker')?.count ?? 0;
    const remaining = r.interactables.filter((i) => i.loot?.item === 'breaker' && !st.taken.includes(i.id));
    if (remaining.length && st.breakerRound === 0) {
      remaining.sort((a, b) => dist(a.pos, this.p.pos) - dist(b.pos, this.p.pos));
      const t = remaining[0];
      if (near(t)) { this.interact(t.id); return; }
      this.go(t.pos, 1.1, 'brk' + t.id); return;
    }
    if (!near(box, 3)) { this.go(box.pos, 1.0, 'box'); return; }
    if (carried > 0 || st.breakerRound === 0) { this.interact(box.id); (this as any).inserted = inserted + carried; return; }
    // flip the first mismatching switch per tick
    for (let i = 0; i < 10; i++) {
      const want = this.lit.includes(i + 1);
      if (this.switches[i] !== want) { this.send({ t: 'breakerToggle', n: i + 1 }); this.consumeEvents(); return; }
    }
    this.idle();
  }
}
