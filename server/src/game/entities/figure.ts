import type { EntitySnap, RoomData, Vec3 } from '@doors/shared';
import { dist, distXZ, NOISE, tagValue, toWorld } from '@doors/shared';
import type { Entity, Game, PlayerState } from '../types';
import { NavGrid } from '../nav';
import { moveToward, r2 } from './common';

type FigState = 'dormant' | 'intro' | 'patrol' | 'investigate' | 'search' | 'chase' | 'attack' | 'stunned' | 'enraged' | 'heartbeat';

/**
 * The Figure: blind, hunts by sound. Patrols, investigates noises, searches, chases when it hears
 * someone close, and runs a heartbeat minigame on players hiding next to it.
 */
export class FigureEntity implements Entity {
  id: string;
  kind = 'figure' as const;
  pos: Vec3;
  yaw = 0;
  state: FigState = 'dormant';
  done = false;
  room: number;
  private nav: NavGrid;
  private path: Vec3[] = [];
  private t = 0;
  private stateT = 0;
  private target: Vec3 | null = null;
  private targetPlayer: PlayerState | null = null;
  private repath = 0;
  private heartbeat: { player: PlayerState; beats: number[]; start: number; hits: Set<number>; misses: number; window: number } | null = null;
  private hbCooldown = new Map<string, number>();
  private noisePulses: { pos: Vec3; r: number; t: number }[] = [];

  constructor(private g: Game, private r: RoomData) {
    this.id = g.nextId('figure');
    this.room = r.index;
    const sp = tagValue(r, 'figureSpawn') ?? [0, r.depth / 2];
    this.pos = toWorld(r, sp[0], 0, sp[1]);
    this.nav = new NavGrid(r, 0.45);
  }

  /** Loud world sounds (interactions, failed padlocks) the Figure can hear. */
  hear(pos: Vec3, radius: number) {
    this.noisePulses.push({ pos: { ...pos }, r: radius, t: this.g.time });
  }

  private setState(s: FigState) {
    if (this.state === s) return;
    this.state = s;
    this.stateT = 0;
    this.g.emit({ e: 'entity', kind: 'figure', phase: s, id: this.id, room: this.room });
  }

  private goTo(p: Vec3) {
    this.target = p;
    this.path = this.nav.findPath(this.pos, p) ?? [];
  }

  enrage() {
    this.setState('enraged');
  }

  update(dt: number) {
    this.t += dt;
    this.stateT += dt;
    const inRoom = this.g.alive().filter((p) => p.room === this.room && p.connected);
    if (this.state === 'dormant') {
      if (inRoom.some((p) => distXZ(p.pos, this.g.rooms[this.room].path[1]) > 4)) {
        this.setState('intro');
      }
      return;
    }
    if (this.state === 'intro') { if (this.stateT > 3.5) this.setState('patrol'); return; }
    if (this.state === 'stunned') { if (this.stateT > 5) this.setState('patrol'); return; }
    if (this.state === 'heartbeat') { this.updateHeartbeat(); return; }
    if (this.state === 'attack') { if (this.stateT > 1.6) this.setState('search'); return; }

    // ---- hearing
    let heard: { pos: Vec3; loud: number; p?: PlayerState } | null = null;
    for (const p of inRoom) {
      if (p.hidden) continue;
      const d = dist(p.pos, this.pos);
      if (p.noise > 0 && d < p.noise) {
        const loud = p.noise - d;
        if (!heard || loud > heard.loud) heard = { pos: { ...p.pos }, loud, p };
      }
      // it can "feel" anyone who walks right into it
      if (d < (p.crouch ? 0.9 : 1.6)) heard = { pos: { ...p.pos }, loud: 99, p };
    }
    this.noisePulses = this.noisePulses.filter((n) => this.g.time - n.t < 0.3);
    for (const n of this.noisePulses) {
      const d = dist(n.pos, this.pos);
      if (d < n.r && (!heard || n.r - d > heard.loud)) heard = { pos: n.pos, loud: n.r - d };
    }
    this.noisePulses = [];

    if (this.state === 'enraged') {
      if (this.stateT < 3.5) return; // breaking loose
      const tgt = inRoom.filter((p) => !p.hidden && !p.inElevator).sort((a, b) => dist(a.pos, this.pos) - dist(b.pos, this.pos))[0];
      if (tgt) { this.targetPlayer = tgt; this.chaseStep(dt, 4.6, tgt); }
      return;
    }

    if (heard) {
      const close = dist(heard.pos, this.pos) < 7 && heard.loud > 2;
      if (heard.p && (close || heard.loud > 9)) { this.targetPlayer = heard.p; this.setState('chase'); }
      else if (this.state !== 'chase' && (this.state !== 'investigate' || !this.target || dist(this.target, heard.pos) > 2)) { this.setState('investigate'); this.goTo(heard.pos); }
    }

    switch (this.state) {
      case 'patrol': {
        if (!this.path.length || this.stateT > 14) {
          const p = this.nav.randomFree(() => this.g.rng.next());
          if (p) this.goTo(p);
          this.stateT = 0;
        }
        this.walk(dt, 2.4);
        break;
      }
      case 'investigate': {
        this.walk(dt, 3.6);
        if (!this.path.length) this.setState('search');
        break;
      }
      case 'search': {
        this.yaw += dt * 1.4;
        this.checkHidden();
        if (this.stateT > 3.5) this.setState('patrol');
        break;
      }
      case 'chase': {
        const tp = this.targetPlayer;
        if (!tp || tp.dead || tp.room !== this.room) { this.setState('search'); break; }
        if (tp.hidden) { this.goTo(tp.pos); this.setState('investigate'); break; }
        // lose the target if it stops making noise far away
        if (this.stateT > 4 && tp.noise < NOISE.walk && dist(tp.pos, this.pos) > 8) { this.goTo(tp.pos); this.setState('investigate'); break; }
        this.chaseStep(dt, 4.7, tp);
        break;
      }
    }
  }

  private chaseStep(dt: number, speed: number, tp: PlayerState) {
    this.repath -= dt;
    if (this.repath <= 0 || !this.path.length) { this.goTo(tp.pos); this.repath = 0.5; }
    this.walk(dt, speed);
    if (dist(tp.pos, this.pos) < 1.3 && !tp.hidden) {
      if (this.g.useCrucifix(tp, 'figure')) { this.setState('stunned'); return; }
      this.g.damage(tp, 999, 'figure');
      this.setState('attack');
    }
  }

  private walk(dt: number, speed: number) {
    let budget = speed * dt;
    while (budget > 0 && this.path.length) {
      const m = moveToward(this.pos, this.path[0], budget);
      budget -= Math.hypot(m.pos.x - this.pos.x, m.pos.z - this.pos.z);
      this.pos = m.pos;
      if (m.yaw !== null) this.yaw = m.yaw;
      if (m.arrived) this.path.shift();
      else break;
    }
  }

  /** Searching next to a hiding spot triggers the heartbeat minigame for its occupant. */
  private checkHidden() {
    for (const p of this.g.alive()) {
      if (!p.hidden || p.room !== this.room) continue;
      if (dist(p.pos, this.pos) > 3.2) continue;
      if ((this.hbCooldown.get(p.id) ?? 0) > this.g.time) continue;
      const n = 4 + Math.floor(this.g.rng.next() * 3);
      const beats: number[] = [];
      let tt = 1.2;
      for (let i = 0; i < n; i++) { beats.push(r2(tt)); tt += 0.55 + this.g.rng.next() * 0.5; }
      this.heartbeat = { player: p, beats, start: this.g.time, hits: new Set(), misses: 0, window: 0.36 };
      this.g.emit({ e: 'heartbeat', target: p.id, beats, window: 0.36 });
      this.setState('heartbeat');
      return;
    }
  }

  /** Called by the room when a hidden player presses a heartbeat key. */
  heartbeatInput(p: PlayerState, idx: number, rtt: number) {
    const hb = this.heartbeat;
    if (!hb || hb.player !== p || idx >= hb.beats.length || hb.hits.has(idx)) return;
    const t = this.g.time - hb.start - Math.min(0.25, rtt / 2);
    if (Math.abs(t - hb.beats[idx]) <= hb.window + 0.12) hb.hits.add(idx);
    else hb.misses++;
  }

  private updateHeartbeat() {
    const hb = this.heartbeat!;
    const t = this.g.time - hb.start;
    for (let i = 0; i < hb.beats.length; i++) if (!hb.hits.has(i) && t > hb.beats[i] + hb.window + 0.45) { hb.hits.add(i); hb.misses++; }
    const finished = t > hb.beats[hb.beats.length - 1] + hb.window + 0.5;
    if (hb.misses >= 2 || finished || !hb.player.hidden) {
      const ok = hb.misses < 2 || !hb.player.hidden;
      this.g.emit({ e: 'heartbeatResult', target: hb.player.id, ok });
      this.hbCooldown.set(hb.player.id, this.g.time + 10);
      this.heartbeat = null;
      if (!ok) {
        this.g.damage(hb.player, 999, 'figure');
        this.setState('attack');
      } else this.setState('patrol');
    }
  }

  crucifix(p: PlayerState): boolean {
    if (!this.g.useCrucifix(p, 'figure')) return false;
    this.setState('stunned');
    return true;
  }

  snap(): EntitySnap {
    return { id: this.id, kind: 'figure', x: r2(this.pos.x), y: r2(this.pos.y), z: r2(this.pos.z), yaw: r2(this.yaw), state: this.state, room: this.room };
  }
}
