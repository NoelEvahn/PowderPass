import {
  type C2S, type GameEvent, type Interactable, type InventoryItem, type ItemType, type RoomData, type RunLayout,
  type S2C, type SelfState, type Vec3, type WorldSync, type EntityKind, type PlayerSnap,
  FINAL_ROOM, GLITCH_BEHIND, HIDE_LIMIT, INVENTORY_SLOTS, ITEMS, NOISE, PLAYER, RECONNECT_WINDOW_MS, Rng, TICK_DT,
  WorldQuery, dist, distXZ, floorAt, generateRun, hash2, pointInBox, tagValue, toLocal,
} from '@doors/shared';
import crypto from 'node:crypto';
import type { Entity, Game, Peer, PlayerState } from './types';
import { RushEntity } from './entities/rush';
import { SeekEntity } from './entities/seek';
import { FigureEntity } from './entities/figure';
import { EyesEntity, HaltEntity, ScreechEntity } from './entities/minor';
import { addItem, consumeOne, findItem, makeItem, newUid } from './inventory';
import { r2 } from './entities/common';

export interface GameRoomOptions {
  seed: number;
  players: { id: string; name: string; peer: Peer | null }[];
  /** Called when the run is over (after the end screen has been sent). */
  onEnd?: (result: 'win' | 'lose', stats: { id: string; door: number; gold: number; won: boolean }[]) => Record<string, number> | void;
  /** Disable the real-time loop (tests drive step() manually). */
  manual?: boolean;
  /** Start at a later door (testing / debugging). */
  startRoom?: number;
  /** Secret seed for puzzle solutions (random by default). */
  secret?: number;
  /** Testing only: players take no damage. */
  invulnerable?: boolean;
}

interface IndexedInteractable { room: number; it: Interactable }

/**
 * Authoritative simulation of one run. Owns the world seed, monster AI, health, inventory, interactions,
 * puzzles and progression. Clients only send intents and their predicted position (validated here).
 */
export class GameRoom implements Game {
  readonly layout: RunLayout;
  readonly rooms: RoomData[];
  readonly world: WorldQuery;
  readonly rng: Rng;
  readonly seed: number;
  time = 0;
  furthestDoor = 0;
  entities = new Map<string, Entity>();
  private ps = new Map<string, PlayerState>();
  private events: GameEvent[] = [];
  private idN = 0;
  private interact = new Map<string, IndexedInteractable>();
  // world state
  private opened = new Set<number>();
  private open = new Set<number>();
  private unlocked = new Set<number>();
  private openFake = new Set<string>();
  private containersOpen = new Set<string>();
  private emptied = new Set<string>();
  private taken = new Set<string>();
  private levers = new Set<string>();
  private broken = new Set<number>();
  private flicker = new Set<number>();
  private hideOcc = new Map<string, string>();
  private books = new Map<string, { shape: number; digit: number } | null>();
  private paperFound = false;
  private libraryUnlocked = false;
  private hvDoorOpen = false;
  private breakersInserted = 0;
  private breakerRound = 0;
  private switches: boolean[] = new Array(10).fill(false);
  powerOn = false;
  private haltDone = new Set<number>();
  private fallen = new Set<string>();
  private dropped = new Map<string, { item: InventoryItem; pos: Vec3 }>();
  private jackDone = new Set<number>();
  private visited = new Set<number>();
  private seekStarted = new Set<number>();
  private figures = new Map<number, FigureEntity>();
  private elevatorT: number | null = null;
  ended = false;
  private timer: NodeJS.Timeout | null = null;
  private tickN = 0;
  private opts: GameRoomOptions;
  private rtt = new Map<string, number>();
  private pendingChandeliers: { id: string; at: number; pos: Vec3; room: number }[] = [];
  /** Secret puzzle data drawn from a server-only RNG (the public seed must not reveal solutions). */
  private libDigits: number[];
  private breakerRounds: number[][];

  constructor(opts: GameRoomOptions) {
    this.opts = opts;
    this.seed = opts.seed;
    this.layout = generateRun(opts.seed);
    this.rooms = this.layout.rooms;
    this.world = new WorldQuery(this.layout);
    this.rng = new Rng(hash2(opts.seed, 0xdeadbeef));
    const secret = new Rng(opts.secret ?? crypto.randomInt(1, 2 ** 31 - 1));
    this.libDigits = Array.from({ length: 8 }, () => secret.int(0, 9));
    this.breakerRounds = Array.from({ length: 3 }, () => {
      const set = new Set<number>();
      for (let i = 1; i <= 10; i++) if (secret.chance(0.5)) set.add(i);
      while (set.size < 3) set.add(secret.int(1, 10));
      return [...set].sort((a, b) => a - b);
    });
    for (const r of this.rooms) for (const it of r.interactables) this.interact.set(it.id, { room: r.index, it });
    const start = opts.startRoom ?? 0;
    for (let n = 1; n <= start; n++) { this.opened.add(n); this.open.add(n); this.unlocked.add(n); }
    this.furthestDoor = start;
    const spawn = this.spawnPoints(start);
    opts.players.forEach((p, i) => this.addPlayer(p.id, p.name, p.peer, spawn[i % spawn.length], start));
    this.visited.add(start);
    if (!opts.manual) this.timer = setInterval(() => {
      try { this.step(TICK_DT); } catch (e) { console.error('[game] tick error', e); }
    }, TICK_DT * 1000);
  }

  // ------------------------------------------------------------------ players

  spawnPoints(room = 0): Vec3[] {
    const r = this.rooms[room];
    const sp = tagValue(r, 'spawn');
    const base = sp ? this.world.rooms[room] && { x: 0, z: sp[1] } : null;
    const out: Vec3[] = [];
    for (let i = 0; i < 4; i++) {
      const ox = (i % 2 ? 0.55 : -0.55), oz = i < 2 ? 0.35 : -0.35;
      if (base) {
        const p = this.toWorldLocal(r, ox, base.z + oz);
        out.push(p);
      } else {
        const p1 = r.path[1];
        out.push({ x: p1.x + ox, y: p1.y, z: p1.z + oz });
      }
    }
    return out;
  }

  private toWorldLocal(r: RoomData, lx: number, lz: number): Vec3 {
    const c = Math.cos((r.rot * Math.PI) / 2), s = Math.sin((r.rot * Math.PI) / 2);
    return { x: r.origin.x + lx * c + lz * s, y: r.origin.y, z: r.origin.z - lx * s + lz * c };
  }

  private addPlayer(id: string, name: string, peer: Peer | null, pos: Vec3, startRoom = 0) {
    const p: PlayerState = {
      id, name, peer, pos: { ...pos }, lastValid: { ...pos }, yaw: 0, pitch: 0, crouch: false, sprint: false, moving: false,
      room: this.world.roomAt(pos, startRoom), roomEnteredAt: 0, hp: PLAYER.maxHealth, dead: false, deadAt: 0, cause: null, gold: 0,
      inv: new Array(INVENTORY_SLOTS).fill(null), equipped: -1, stamina: PLAYER.maxStamina, boost: 0, hidden: null, hiddenTime: 0,
      hideWarned: false, snared: 0, snareCooldown: 0, lastSeq: 0, lastInputAt: 0, connected: !!peer, disconnectedAt: 0,
      revives: 1, noise: 0, behindSince: -1, screechCooldown: 20, cooldowns: new Map(), shopOpen: false, deaths: 0,
      furthest: 0, selfDirty: true, violations: 0, inElevator: false, slowUntil: 0,
    };
    if (p.room < 0) p.room = startRoom;
    this.ps.set(id, p);
  }

  players() { return [...this.ps.values()]; }
  alive() { return this.players().filter((p) => !p.dead); }
  getPlayer(id: string) { return this.ps.get(id); }
  eye(p: PlayerState): Vec3 { return { x: p.pos.x, y: p.pos.y + (p.crouch ? PLAYER.crouchEye : PLAYER.eye), z: p.pos.z }; }
  nextId(prefix: string) { return `${prefix}${++this.idN}`; }
  emit(ev: GameEvent) { this.events.push(ev); }
  isDoorOpen(n: number) { return this.open.has(n); }
  threatActive() {
    for (const e of this.entities.values()) if (e.kind === 'rush' || e.kind === 'ambush' || e.kind === 'figure' || e.kind === 'seek') return true;
    return false;
  }

  attach(id: string, peer: Peer) {
    const p = this.ps.get(id);
    if (!p) return false;
    p.peer = peer;
    p.connected = true;
    p.selfDirty = true;
    this.send(p, { t: 'start', seed: this.seed, version: this.layout.version, players: this.players().map((q) => ({ id: q.id, name: q.name })), spawn: [p.pos], you: p.id });
    this.send(p, { t: 'sync', w: this.worldSync() });
    this.emit({ e: 'notice', text: `${p.name} reconnected` });
    return true;
  }

  detach(id: string) {
    const p = this.ps.get(id);
    if (!p) return;
    p.peer = null;
    p.connected = false;
    p.disconnectedAt = Date.now();
    this.emit({ e: 'notice', text: `${p.name} lost connection` });
  }

  hasPlayer(id: string) { return this.ps.has(id); }
  connectedCount() { return this.players().filter((p) => p.connected).length; }

  private send(p: PlayerState, msg: S2C) {
    if (p.peer && p.connected) p.peer.send(msg);
  }
  private broadcast(msg: S2C) {
    for (const p of this.ps.values()) this.send(p, msg);
  }

  // ------------------------------------------------------------------ inventory & damage

  hasEquipped(p: PlayerState, t: ItemType) {
    const it = p.inv[p.equipped];
    return !!it && it.type === t;
  }

  useCrucifix(p: PlayerState, kind: EntityKind): boolean {
    if (!this.hasEquipped(p, 'crucifix')) return false;
    consumeOne(p.inv, p.equipped);
    if (!p.inv[p.equipped]) p.equipped = -1;
    p.selfDirty = true;
    this.emit({ e: 'crucifix', by: p.id, kind, ok: true });
    return true;
  }

  damage(p: PlayerState, amount: number, cause: string) {
    if (p.dead || this.ended || this.opts.invulnerable || (p.inElevator && cause === 'figure')) return;
    p.hp = Math.max(0, p.hp - amount);
    p.selfDirty = true;
    this.emit({ e: 'damage', id: p.id, amount, cause, hp: p.hp });
    if (p.hp <= 0) this.kill(p, cause);
  }

  private kill(p: PlayerState, cause: string) {
    p.dead = true;
    p.deadAt = this.time;
    p.cause = cause;
    p.deaths++;
    if (p.hidden) { this.hideOcc.delete(p.hidden); p.hidden = null; }
    p.selfDirty = true;
    this.emit({ e: 'death', id: p.id, cause });
    if (this.players().every((q) => q.dead || (!q.connected && Date.now() - q.disconnectedAt > RECONNECT_WINDOW_MS))) {
      this.endGame('lose');
    }
  }

  setLights(rooms: number[], mode: 'flicker' | 'broken' | 'on' | 'green' | 'red' | 'off') {
    for (const r of rooms) {
      if (mode === 'broken' || mode === 'off') this.broken.add(r);
      if (mode === 'on') { this.broken.delete(r); this.flicker.delete(r); }
      if (mode === 'flicker' || mode === 'green') this.flicker.add(r);
    }
    this.emit({ e: 'lights', rooms, mode });
  }

  // ------------------------------------------------------------------ doors

  openDoor(n: number, by: string) {
    if (this.open.has(n) || n < 1 || n > FINAL_ROOM) return;
    this.open.add(n);
    const first = !this.opened.has(n);
    this.opened.add(n);
    this.emit({ e: 'door', n, by });
    for (const f of this.figures.values()) f.hear(this.rooms[n].origin, NOISE.door);
    if (first) {
      this.furthestDoor = Math.max(this.furthestDoor, n);
      this.revealRoom(n);
    }
  }

  closeDoor(n: number) {
    if (!this.open.has(n)) return;
    this.open.delete(n);
    this.emit({ e: 'doorSlam', n });
  }

  /** Entity encounters attached to a room fire when its door is first opened. */
  private revealRoom(n: number) {
    const r = this.rooms[n];
    for (const en of r.encounters) {
      if (en.type === 'rush' || en.type === 'ambush') {
        const e = new RushEntity(this, en.type, n, en.delay ?? 5, en.rebounds ?? 0);
        this.entities.set(e.id, e);
      } else if (en.type === 'eyes') {
        const e = new EyesEntity(this, r);
        this.entities.set(e.id, e);
      } else if (en.type === 'shadow') {
        this.emit({ e: 'scare', kind: 'shadow', room: n });
      }
    }
    if (r.kind === 'library' || r.kind === 'electrical') {
      const f = new FigureEntity(this, r);
      this.figures.set(n, f);
      this.entities.set(f.id, f);
    }
  }

  private doorLock(n: number) {
    const prev = this.rooms[n - 1];
    return prev.exits[prev.realExit]?.lock ?? 'none';
  }

  private tryDoor(p: PlayerState, n: number) {
    if (n < 1 || n > FINAL_ROOM) return;
    const r = this.rooms[n];
    if (p.room !== n - 1 && p.room !== n) return;
    if (dist(p.pos, r.origin) > 3.2) return;
    if (this.open.has(n)) return;
    if (this.opened.has(n)) {
      // a door that slammed behind a chase stays shut
      return this.notice(p, 'It won\'t budge.');
    }
    const lock = this.doorLock(n);
    if (!this.unlocked.has(n)) {
      if (lock === 'key') {
        const k = findItem(p.inv, (i) => i.type === 'key' && i.door === n);
        if (k >= 0) { p.inv[k] = null; if (p.equipped === k) p.equipped = -1; }
        else if (!this.usePick(p)) return this.notice(p, `Door ${n} is locked. The key must be somewhere in this room.`);
        p.selfDirty = true;
        this.unlocked.add(n);
        this.emit({ e: 'doorUnlock', n, by: p.id });
      } else if (lock === 'lever') return this.notice(p, 'The gate is shut. There must be a lever nearby.');
      else if (lock === 'code') return this.notice(p, 'A padlock holds the door. Solve the code.');
      else if (lock === 'halt') return this.notice(p, 'The door is held shut...');
      else this.unlocked.add(n);
    }
    // Jack: the first time, the door opens onto a red room and slams
    if (r.encounters.some((e) => e.type === 'jackRoom') && !this.jackDone.has(n)) {
      this.jackDone.add(n);
      this.emit({ e: 'scare', kind: 'jack', target: p.id, room: n });
      return;
    }
    this.openDoor(n, p.id);
  }

  private usePick(p: PlayerState): boolean {
    const s = findItem(p.inv, (i) => i.type === 'skeletonKey' || i.type === 'lockpick');
    if (s < 0) return false;
    consumeOne(p.inv, s);
    if (!p.inv[s] && p.equipped === s) p.equipped = -1;
    p.selfDirty = true;
    return true;
  }

  private notice(p: PlayerState, text: string) {
    this.emit({ e: 'notice', text, target: p.id });
  }

  // ------------------------------------------------------------------ messages

  handle(id: string, m: C2S) {
    const p = this.ps.get(id);
    if (!p || this.ended) return;
    switch (m.t) {
      case 'input': return this.onInput(p, m);
      case 'interact': return this.onInteract(p, m.id);
      case 'unhide': return this.unhide(p);
      case 'equip': if (p.dead) return; p.equipped = m.slot >= 0 && p.inv[m.slot] ? (p.equipped === m.slot ? -1 : m.slot) : -1; p.selfDirty = true; return;
      case 'use': return this.useItem(p, m.slot);
      case 'drop': return this.dropItem(p, m.slot);
      case 'toggleLight': return this.toggleLight(p);
      case 'code': return this.onCode(p, m.code);
      case 'breakerToggle': return this.onBreakerToggle(p, m.n);
      case 'buy': return this.onBuy(p, m.item);
      case 'closeShop': p.shopOpen = false; return;
      case 'heartbeat': for (const f of this.figures.values()) f.heartbeatInput(p, m.idx, this.rtt.get(p.id) ?? 0.1); return;
      case 'revive': return this.onRevive(p);
      case 'ping': this.send(p, { t: 'pong', c: m.c, s: Date.now() }); return;
    }
  }

  setRtt(id: string, rtt: number) { this.rtt.set(id, rtt); }

  private onInput(p: PlayerState, m: Extract<C2S, { t: 'input' }>) {
    if (m.seq <= p.lastSeq) return;
    p.lastSeq = m.seq;
    p.yaw = m.yaw; p.pitch = Math.max(-1.55, Math.min(1.55, m.pitch));
    if (p.dead) return;
    p.crouch = m.crouch; p.moving = m.moving;
    p.sprint = m.sprint && p.stamina > 0 && !m.crouch;
    if (p.hidden || p.snared > 0 || p.inElevator && this.elevatorT !== null && this.elevatorT <= 0) return;
    const now = this.time;
    const dt = Math.min(0.6, Math.max(TICK_DT, now - p.lastInputAt));
    p.lastInputAt = now;
    const next = { x: m.x, y: m.y, z: m.z };
    const cap = PLAYER.sprintSpeed * (p.boost > 0 ? 1.35 : 1) * 1.3;
    const moved = distXZ(next, p.pos);
    let ok = moved <= cap * dt + 0.6;
    let room = -1;
    if (ok) {
      room = this.world.roomAt(next, p.room, 0.4);
      ok = room >= 0 && (room === 0 || this.opened.has(room));
      if (ok) {
        const fy = floorAt(this.rooms[room], next.x, next.z);
        ok = next.y > fy - 0.8 && next.y < fy + 1.6;
      }
      if (ok && room === FINAL_ROOM) ok = this.validFinalRoomPos(next);
    }
    if (!ok) {
      p.violations++;
      this.send(p, { t: 'correct', x: p.lastValid.x, y: p.lastValid.y, z: p.lastValid.z, seq: m.seq });
      return;
    }
    p.pos = next;
    p.lastValid = { ...next };
    if (room !== p.room) { p.room = room; p.roomEnteredAt = this.time; this.onEnterRoom(p, room); }
    p.furthest = Math.max(p.furthest, room);
  }

  private validFinalRoomPos(pos: Vec3): boolean {
    const r = this.rooms[FINAL_ROOM];
    const [lx, lz] = toLocal(r, pos.x, pos.z);
    // high-voltage enclosure (NW) needs its door open
    if (!this.hvDoorOpen && lx < r.xMin + 6.9 && lz > 0.2 + r.depth - 8 + 0.2) return false;
    const g = tagValue(r, 'powerGate');
    if (g && !this.powerOn && Math.abs(lx - g[0]) < 1.6 && lz > g[1] + 0.25) return false;
    return true;
  }

  private onEnterRoom(p: PlayerState, room: number) {
    if (this.visited.has(room)) return;
    this.visited.add(room);
    const r = this.rooms[room];
    if (r.kind === 'halt') {
      const h = new HaltEntity(this, r, () => {
        this.haltDone.add(room);
        this.unlocked.add(room + 1);
        this.emit({ e: 'doorUnlock', n: room + 1, by: 'halt' });
      });
      this.entities.set(h.id, h);
    }
    if (!r.dark && !this.broken.has(room)) this.emit({ e: 'lights', rooms: [room], mode: 'on' });
  }

  private nearInteract(p: PlayerState, pos: Vec3, range = PLAYER.interactRange + 0.6) {
    return dist(this.eye(p), pos) <= range;
  }

  private onInteract(p: PlayerState, id: string) {
    if (p.dead) return;
    if (id.startsWith('door:')) { if (!p.hidden) this.tryDoor(p, Number(id.slice(5))); return; }
    if (id.startsWith('fdoor:')) return this.onFakeDoor(p, id);
    if (id.startsWith('drop:')) return this.onPickupDropped(p, id);
    const ref = this.interact.get(id);
    if (!ref) return;
    const it = ref.it;
    if (p.hidden && !(it.kind === 'hide' && p.hidden === id)) return;
    if (!this.nearInteract(p, it.pos, it.kind === 'hide' ? PLAYER.interactRange + 1.0 : undefined)) return;
    switch (it.kind) {
      case 'drawer': return this.onDrawer(p, it);
      case 'hide': return p.hidden === id ? this.unhide(p) : this.hide(p, it);
      case 'pickup': return this.onPickup(p, it);
      case 'book': return this.onBook(p, it);
      case 'lever': return this.onLever(p, it, ref.room);
      case 'padlock': return; // client opens the code UI; code is verified in onCode
      case 'cabinetDoor': return this.onHvDoor(p);
      case 'breakerBox': return this.onBreakerBox(p);
      case 'shop': p.shopOpen = true; this.emit({ e: 'shop', target: p.id, open: true }); return;
      case 'elevator': if (!this.powerOn) this.notice(p, 'The elevator has no power.'); return;
    }
  }

  private onFakeDoor(p: PlayerState, id: string) {
    const [, rs, ks] = id.split(':');
    const r = this.rooms[Number(rs)];
    const ex = r?.exits[Number(ks)];
    if (!ex || ex.real || p.room !== r.index || dist(p.pos, ex.world) > 3.2) return;
    if (ex.fake === 'dupe') {
      if (this.openFake.has(id)) return;
      this.openFake.add(id);
      this.emit({ e: 'fakeDoor', id, by: p.id });
      this.emit({ e: 'entity', kind: 'dupe', phase: 'attack', id, room: r.index });
      this.damage(p, 35, 'dupe');
    } else this.notice(p, 'This way is blocked.');
  }

  private onDrawer(p: PlayerState, it: Interactable) {
    const open = !this.containersOpen.has(it.id);
    if (open) this.containersOpen.add(it.id); else this.containersOpen.delete(it.id);
    this.emit({ e: 'container', id: it.id, open, by: p.id });
    for (const f of this.figures.values()) f.hear(it.pos, NOISE.interact);
    if (!open || this.emptied.has(it.id) || !it.loot) return;
    const loot = it.loot;
    if (loot.timothy) {
      this.emptied.add(it.id);
      this.emit({ e: 'scare', kind: 'timothy', target: p.id, id: it.id });
      this.damage(p, 5, 'timothy');
      return;
    }
    if (loot.gold) {
      p.gold += loot.gold;
      this.emptied.add(it.id);
      p.selfDirty = true;
      this.emit({ e: 'loot', id: it.id, by: p.id, gold: loot.gold });
      return;
    }
    if (loot.item) {
      if (!addItem(p.inv, makeItem(loot.item, loot.door ? { door: loot.door } : {}))) return this.notice(p, 'Your inventory is full.');
      this.emptied.add(it.id);
      p.selfDirty = true;
      this.emit({ e: 'loot', id: it.id, by: p.id, item: loot.item });
    }
  }

  private onPickup(p: PlayerState, it: Interactable) {
    if (this.taken.has(it.id) || !it.loot?.item) return;
    const t = it.loot.item;
    if (t === 'hintPaper') {
      this.taken.add(it.id);
      this.paperFound = true;
      addItem(p.inv, makeItem('hintPaper'));
      p.selfDirty = true;
      this.emit({ e: 'paper', by: p.id });
      this.emit({ e: 'pickup', id: it.id, by: p.id, item: t });
      return;
    }
    if (!addItem(p.inv, makeItem(t, it.loot.door ? { door: it.loot.door } : {}))) return this.notice(p, 'Your inventory is full.');
    this.taken.add(it.id);
    p.selfDirty = true;
    this.emit({ e: 'pickup', id: it.id, by: p.id, item: t });
    for (const f of this.figures.values()) f.hear(it.pos, NOISE.interact);
  }

  private onPickupDropped(p: PlayerState, id: string) {
    const d = this.dropped.get(id.slice(5));
    if (!d || dist(p.pos, d.pos) > 3) return;
    if (!addItem(p.inv, d.item)) return this.notice(p, 'Your inventory is full.');
    this.dropped.delete(id.slice(5));
    p.selfDirty = true;
    this.emit({ e: 'pickup', id, by: p.id, item: d.item.type });
  }

  private onBook(p: PlayerState, it: Interactable) {
    if (this.books.has(it.id)) return;
    const hint = it.data?.shape !== undefined ? { shape: it.data.shape, digit: this.libDigits[it.data.shape] } : null;
    this.books.set(it.id, hint);
    this.emit({ e: 'book', id: it.id, by: p.id, shape: hint?.shape, digit: hint?.digit });
    for (const f of this.figures.values()) f.hear(it.pos, NOISE.book);
  }

  private onLever(p: PlayerState, it: Interactable, room: number) {
    if (this.levers.has(it.id)) return;
    this.levers.add(it.id);
    const n = room + 1;
    this.unlocked.add(n);
    this.emit({ e: 'lever', id: it.id, by: p.id, door: n });
    this.openDoor(n, p.id);
  }

  private onCode(p: PlayerState, code: string) {
    const lib = this.rooms[50];
    const pad = lib.interactables.find((i) => i.kind === 'padlock');
    if (!pad || this.libraryUnlocked || !this.nearInteract(p, pad.pos, 4)) return;
    if (code === this.libraryCode()) {
      this.libraryUnlocked = true;
      this.unlocked.add(51);
      this.emit({ e: 'padlock', ok: true, by: p.id });
      this.emit({ e: 'doorUnlock', n: 51, by: p.id });
      this.openDoor(51, p.id);
    } else {
      this.emit({ e: 'padlock', ok: false, by: p.id });
      this.figures.get(50)?.hear(pad.pos, 16);
    }
  }

  libraryCode(): string {
    return (this.rooms[50].puzzle?.shapes ?? []).map((sh) => this.libDigits[sh]).join('');
  }

  private onHvDoor(p: PlayerState) {
    if (this.hvDoorOpen) return;
    const k = findItem(p.inv, (i) => i.type === 'electricalKey');
    if (k >= 0) { p.inv[k] = null; if (p.equipped === k) p.equipped = -1; }
    else if (!this.usePick(p)) return this.notice(p, 'Locked. The Electrical Room Key is on one of the shelves.');
    p.selfDirty = true;
    this.hvDoorOpen = true;
    this.emit({ e: 'hvDoor', by: p.id });
    this.figures.get(FINAL_ROOM)?.hear(p.pos, NOISE.door);
  }

  private breakerState(by?: string): GameEvent {
    const rounds = this.breakerRounds;
    return { e: 'breaker', inserted: this.breakersInserted, round: this.breakerRound, switches: [...this.switches], lit: this.breakerRound >= 1 && this.breakerRound <= rounds.length ? rounds[this.breakerRound - 1] : [], by, complete: this.powerOn };
  }

  private onBreakerBox(p: PlayerState) {
    if (!this.hvDoorOpen) return;
    const k = findItem(p.inv, (i) => i.type === 'breaker');
    if (k >= 0) {
      const n = Math.min(p.inv[k]!.count, 10 - this.breakersInserted);
      this.breakersInserted += n;
      p.inv[k] = null;
      if (p.equipped === k) p.equipped = -1;
      p.selfDirty = true;
      if (this.breakersInserted >= 10 && this.breakerRound === 0) { this.breakerRound = 1; this.switches.fill(false); }
      this.emit(this.breakerState(p.id));
      this.figures.get(FINAL_ROOM)?.hear(p.pos, NOISE.breaker);
    } else if (this.breakersInserted < 10) this.notice(p, `${10 - this.breakersInserted} breaker switches are missing.`);
    else this.emit(this.breakerState(p.id));
  }

  private onBreakerToggle(p: PlayerState, n: number) {
    const box = this.rooms[FINAL_ROOM].interactables.find((i) => i.kind === 'breakerBox')!;
    const rounds = this.breakerRounds;
    if (this.powerOn || this.breakerRound < 1 || !this.nearInteract(p, box.pos, 3.5)) return;
    this.switches[n - 1] = !this.switches[n - 1];
    this.figures.get(FINAL_ROOM)?.hear(p.pos, NOISE.breaker * 0.6);
    const lit = rounds[this.breakerRound - 1];
    const match = this.switches.every((on, i) => on === lit.includes(i + 1));
    if (match) {
      this.breakerRound++;
      this.switches.fill(false);
      if (this.breakerRound > rounds.length) {
        this.powerOn = true;
        this.emit(this.breakerState(p.id));
        this.emit({ e: 'power' });
        this.setLights([FINAL_ROOM], 'on');
        this.figures.get(FINAL_ROOM)?.enrage();
        return;
      }
    }
    this.emit(this.breakerState(p.id));
  }

  private onBuy(p: PlayerState, item: ItemType) {
    const shop = this.rooms[52]?.interactables.find((i) => i.kind === 'shop');
    const def = ITEMS[item];
    if (!shop || !p.shopOpen || !def.price || !this.nearInteract(p, shop.pos, 5)) return;
    if (p.gold < def.price) { this.emit({ e: 'purchase', by: p.id, item, ok: false }); return; }
    if (!addItem(p.inv, makeItem(item))) { this.notice(p, 'Your inventory is full.'); return; }
    p.gold -= def.price;
    p.selfDirty = true;
    this.emit({ e: 'purchase', by: p.id, item, ok: true });
  }

  private hide(p: PlayerState, it: Interactable) {
    if (this.hideOcc.has(it.id)) return this.notice(p, 'Someone is already hiding there.');
    if (it.data?.jack) {
      this.emit({ e: 'scare', kind: 'jack', target: p.id, id: it.id });
      it.data = {};
      return;
    }
    if (p.snared > 0) return;
    this.hideOcc.set(it.id, p.id);
    p.hidden = it.id;
    p.hiddenTime = 0;
    p.hideWarned = false;
    p.pos = { x: it.pos.x, y: it.exitPos ? it.exitPos.y : p.pos.y, z: it.pos.z };
    p.selfDirty = true;
    this.emit({ e: 'hide', id: it.id, by: p.id, inside: true });
  }

  unhide(p: PlayerState) {
    if (!p.hidden) return;
    const ref = this.interact.get(p.hidden)!;
    this.hideOcc.delete(p.hidden);
    this.emit({ e: 'hide', id: p.hidden, by: p.id, inside: false });
    p.hidden = null;
    const ex = ref.it.exitPos ?? p.pos;
    p.pos = { ...ex };
    p.lastValid = { ...ex };
    p.lastInputAt = this.time;
    p.selfDirty = true;
    this.send(p, { t: 'correct', x: ex.x, y: ex.y, z: ex.z, seq: p.lastSeq });
  }

  private useItem(p: PlayerState, slot: number) {
    if (p.dead || slot < 0) return;
    const it = p.inv[slot];
    if (!it) return;
    switch (it.type) {
      case 'bandage':
        if (p.hp >= PLAYER.maxHealth) return this.notice(p, 'You are at full health.');
        p.hp = Math.min(PLAYER.maxHealth, p.hp + 35);
        consumeOne(p.inv, slot);
        this.emit({ e: 'heal', id: p.id, hp: p.hp });
        break;
      case 'vitamins':
        p.boost = 12;
        p.stamina = PLAYER.maxStamina;
        consumeOne(p.inv, slot);
        break;
      case 'battery': {
        const fl = findItem(p.inv, (i) => i.type === 'flashlight' && (i.charge ?? 1) < 0.999);
        if (fl < 0) return this.notice(p, 'Nothing to recharge.');
        p.inv[fl]!.charge = 1;
        consumeOne(p.inv, slot);
        break;
      }
      case 'flashlight': case 'lighter':
        p.equipped = slot;
        return this.toggleLight(p);
      default:
        p.equipped = slot;
    }
    if (p.equipped >= 0 && !p.inv[p.equipped]) p.equipped = -1;
    p.selfDirty = true;
  }

  private toggleLight(p: PlayerState) {
    const it = p.inv[p.equipped];
    if (!it || (it.type !== 'flashlight' && it.type !== 'lighter')) return;
    if ((it.charge ?? 0) <= 0) { it.on = false; this.notice(p, it.type === 'flashlight' ? 'The batteries are dead.' : 'Out of fuel.'); }
    else it.on = !it.on;
    p.selfDirty = true;
  }

  private dropItem(p: PlayerState, slot: number) {
    const it = p.inv[slot];
    if (!it || p.dead || p.hidden) return;
    if (it.type === 'hintPaper') return;
    p.inv[slot] = null;
    if (p.equipped === slot) p.equipped = -1;
    if (it.on) it.on = false;
    const id = newUid();
    const pos = { x: p.pos.x - Math.sin(p.yaw) * 0.6, y: p.pos.y + 0.05, z: p.pos.z - Math.cos(p.yaw) * 0.6 };
    this.dropped.set(id, { item: it, pos });
    p.selfDirty = true;
    this.emit({ e: 'drop', id: `drop:${id}`, item: it, pos });
  }

  private onRevive(p: PlayerState) {
    if (!p.dead || p.revives <= 0 || this.time - p.deadAt < 4) return;
    const anchor = this.alive().filter((q) => q.connected).sort((a, b) => b.room - a.room)[0];
    if (!anchor) return;
    const chaseActive = [...this.entities.values()].some((e) => e.kind === 'seek');
    if (chaseActive) return this.notice(p, 'You can revive once the chase is over.');
    p.revives--;
    p.dead = false;
    p.hp = PLAYER.maxHealth;
    p.cause = null;
    const r = this.rooms[anchor.room];
    const pos = { ...(r.path[1] ?? anchor.pos) };
    p.pos = pos; p.lastValid = { ...pos }; p.room = anchor.room; p.lastInputAt = this.time;
    p.selfDirty = true;
    this.emit({ e: 'revive', id: p.id, pos });
    this.send(p, { t: 'correct', x: pos.x, y: pos.y, z: pos.z, seq: p.lastSeq });
  }

  // ------------------------------------------------------------------ simulation

  step(dt: number) {
    if (this.ended) return;
    this.time += dt;
    this.tickN++;
    for (const p of this.ps.values()) this.updatePlayer(p, dt);
    for (const [id, e] of this.entities) {
      e.update(dt);
      if (e.done) { this.entities.delete(id); if (e.kind === 'figure') for (const [k, f] of this.figures) if (f === e) this.figures.delete(k); }
    }
    this.updateEncounters(dt);
    this.updateElevator(dt);
    this.cleanupDisconnected();
    this.flush();
  }

  private updatePlayer(p: PlayerState, dt: number) {
    if (p.dead) { p.noise = 0; return; }
    const inChase = this.powerOn || [...this.entities.values()].some((e) => e.kind === 'seek');
    if (p.sprint && p.moving && !p.hidden) {
      if (p.boost <= 0 && !inChase) p.stamina = Math.max(0, p.stamina - PLAYER.staminaDrain * dt);
    } else p.stamina = Math.min(PLAYER.maxStamina, p.stamina + PLAYER.staminaRegen * dt);
    if (p.boost > 0) { p.boost = Math.max(0, p.boost - dt); if (p.boost === 0) p.selfDirty = true; }
    if (p.snared > 0) { p.snared = Math.max(0, p.snared - dt); if (p.snared === 0) p.selfDirty = true; }
    // light drain
    for (const it of p.inv) {
      if (it && it.on && it.charge !== undefined) {
        const held = p.inv[p.equipped] === it;
        if (!held) continue;
        it.charge = Math.max(0, it.charge - dt / (it.type === 'flashlight' ? 160 : 100));
        if (it.charge === 0) { it.on = false; p.selfDirty = true; }
        if (this.tickN % 20 === 0) p.selfDirty = true;
      }
    }
    // noise for the Figure
    p.noise = p.hidden || !p.moving ? NOISE.still : p.crouch ? NOISE.crouch : p.sprint ? NOISE.sprint : NOISE.walk;
    // Hide punishes staying too long in one spot
    if (p.hidden) {
      p.hiddenTime += dt;
      const limit = this.threatActive() ? HIDE_LIMIT.threat : HIDE_LIMIT.calm;
      if (!p.hideWarned && p.hiddenTime > limit - HIDE_LIMIT.warn) {
        p.hideWarned = true;
        this.emit({ e: 'warn', kind: 'hide', target: p.id });
      }
      if (p.hiddenTime > limit) {
        this.emit({ e: 'scare', kind: 'hide', target: p.id });
        this.unhide(p);
        this.damage(p, 40, 'hide');
      }
    }
    // Glitch: players left far behind are pulled forward
    if (!p.hidden && p.connected && p.room >= 0 && p.room < this.furthestDoor - GLITCH_BEHIND) {
      if (p.behindSince < 0) p.behindSince = this.time;
      if (this.time - p.behindSince > 2.5) {
        const lead = this.alive().filter((q) => q !== p).sort((a, b) => b.room - a.room)[0];
        const target = lead ? lead.room : this.furthestDoor;
        const pos = { ...(this.rooms[target].path[1] ?? this.rooms[target].origin) };
        p.pos = pos; p.lastValid = { ...pos }; p.room = target; p.behindSince = -1; p.lastInputAt = this.time;
        this.emit({ e: 'glitch', target: p.id, pos });
        this.send(p, { t: 'correct', x: pos.x, y: pos.y, z: pos.z, seq: p.lastSeq });
      }
    } else p.behindSince = -1;
    if (this.tickN % 20 === 0) p.selfDirty = true;
    if (p.selfDirty) { p.selfDirty = false; this.send(p, { t: 'self', s: this.selfState(p) }); }
  }

  private updateEncounters(dt: number) {
    const players = this.alive().filter((p) => p.connected);
    for (const p of players) {
      const r = this.rooms[p.room];
      if (!r) continue;
      // Seek: starts once a player is well into the seek hallway
      if (r.kind === 'seekHall' && !this.seekStarted.has(r.index)) {
        const [, lz] = toLocal(r, p.pos.x, p.pos.z);
        if (lz > r.depth * 0.45) {
          this.seekStarted.add(r.index);
          const chase = this.layout.seekChases.findIndex((c) => c.start === r.index) + 1;
          const s = new SeekEntity(this, chase, r.index);
          this.entities.set(s.id, s);
        }
      }
      // Snare traps
      if (p.snared <= 0 && p.snareCooldown < this.time && !p.hidden) {
        for (const pr of r.props) {
          if (pr.type !== 'snare') continue;
          const w = this.propWorld(r, pr);
          if (distXZ(w, p.pos) < 0.4 && Math.abs(w.y - p.pos.y) < 0.6) {
            p.snared = 2.5; p.snareCooldown = this.time + 6; p.selfDirty = true;
            this.emit({ e: 'snare', id: pr.id, target: p.id });
            this.damage(p, 20, 'snare');
            for (const f of this.figures.values()) f.hear(p.pos, 10);
            break;
          }
        }
      }
      // Seek chase hazards: hands, fire, falling chandeliers
      if (r.kind === 'seekChase' || r.kind === 'seekFinale') {
        for (const pr of r.props) {
          if (pr.type === 'hands' || pr.type === 'fire') {
            const w = this.propWorld(r, pr);
            const reach = pr.type === 'hands' ? 1.05 : 0.95;
            const key = `${pr.id}:${p.id}`;
            if (distXZ(w, p.pos) < reach && (p.cooldowns.get(key) ?? 0) < this.time) {
              p.cooldowns.set(key, this.time + (pr.type === 'hands' ? 99 : 1));
              if (pr.type === 'hands') { p.slowUntil = this.time + 0.8; this.emit({ e: 'entity', kind: 'seek', phase: 'grab', id: pr.id, room: r.index }); }
              this.damage(p, pr.type === 'hands' ? 40 : 30, pr.type === 'hands' ? 'seekHands' : 'fire');
            }
          }
          if (pr.type === 'chandelier' && pr.data === 1 && !this.fallen.has(pr.id)) {
            const w = this.propWorld(r, pr);
            if (distXZ(w, p.pos) < 7.5) {
              this.fallen.add(pr.id);
              this.emit({ e: 'chandelier', id: pr.id });
              this.pendingChandeliers.push({ id: pr.id, at: this.time + 0.9, pos: { x: w.x, y: p.pos.y, z: w.z }, room: r.index });
            }
          }
        }
      }
      // Screech in dark rooms
      const dark = r.dark || this.broken.has(r.index);
      if (dark && !p.hidden && p.screechCooldown < this.time && this.time - p.roomEnteredAt > 4 && !this.threatActive()) {
        if (this.rng.next() < 0.05 * dt * 3 && ![...this.entities.values()].some((e) => e.kind === 'screech')) {
          p.screechCooldown = this.time + 25;
          const s = new ScreechEntity(this, p);
          this.entities.set(s.id, s);
        }
      }
    }
    for (const c of this.pendingChandeliers) {
      if (c.at > this.time) continue;
      for (const p of this.alive()) { if (distXZ(p.pos, c.pos) < 1.4) this.damage(p, 40, 'chandelier'); }
    }
    this.pendingChandeliers = this.pendingChandeliers.filter((c) => c.at > this.time);
  }

  private propWorld(r: RoomData, pr: { x: number; y: number; z: number }): Vec3 {
    return this.toWorldLocalY(r, pr.x, pr.y, pr.z);
  }
  private toWorldLocalY(r: RoomData, lx: number, ly: number, lz: number): Vec3 {
    const w = this.toWorldLocal(r, lx, lz);
    return { ...w, y: r.origin.y + ly };
  }

  private updateElevator(dt: number) {
    if (!this.powerOn) return;
    const r = this.rooms[FINAL_ROOM];
    const el = r.interactables.find((i) => i.kind === 'elevator')!;
    for (const p of this.alive()) p.inElevator = distXZ(p.pos, el.pos) < 1.5 && p.room === FINAL_ROOM;
    const alive = this.alive().filter((p) => p.connected);
    const inside = alive.filter((p) => p.inElevator);
    if (inside.length && this.elevatorT === null) {
      this.elevatorT = 12;
      this.emit({ e: 'elevator', phase: 'boarding', countdown: 12 });
    }
    if (this.elevatorT !== null) {
      this.elevatorT -= dt;
      if (inside.length === alive.length && this.elevatorT > 2.5) {
        this.elevatorT = 2.5;
        this.emit({ e: 'elevator', phase: 'boarding', countdown: 2.5 });
      }
      if (this.elevatorT <= 0) {
        this.emit({ e: 'elevator', phase: 'closing', countdown: 0 });
        for (const p of alive) if (!p.inElevator) this.damage(p, 999, 'figure');
        this.endGame(inside.length ? 'win' : 'lose');
      }
    }
  }

  private cleanupDisconnected() {
    const now = Date.now();
    for (const p of this.ps.values()) {
      if (!p.connected && p.disconnectedAt && now - p.disconnectedAt > RECONNECT_WINDOW_MS && !p.dead) {
        if (p.hidden) { this.hideOcc.delete(p.hidden); p.hidden = null; }
        p.dead = true; p.cause = 'disconnected';
        this.emit({ e: 'death', id: p.id, cause: 'disconnected' });
        if (this.players().every((q) => q.dead)) this.endGame('lose');
      }
    }
  }

  private flush() {
    if (this.events.length) {
      const list = this.events;
      this.events = [];
      this.broadcast({ t: 'ev', list });
    }
    this.broadcast({ t: 'snap', time: r2(this.time), players: this.players().map((p) => this.playerSnap(p)), entities: [...this.entities.values()].map((e) => e.snap()) });
  }

  private playerSnap(p: PlayerState): PlayerSnap {
    const eq = p.inv[p.equipped];
    return {
      id: p.id, name: p.name, x: r2(p.pos.x), y: r2(p.pos.y), z: r2(p.pos.z), yaw: r2(p.yaw), pitch: r2(p.pitch), crouch: p.crouch,
      hidden: p.hidden, hp: p.hp, dead: p.dead, room: p.room, equip: eq?.type ?? null, light: !!eq?.on, connected: p.connected, ack: p.lastSeq,
    };
  }

  selfState(p: PlayerState): SelfState {
    return {
      hp: p.hp, gold: p.gold, inventory: p.inv.map((i) => (i ? { ...i } : null)), equipped: p.equipped, stamina: Math.round(p.stamina),
      boost: r2(p.boost), dead: p.dead, revives: p.revives, hidden: p.hidden, snared: r2(p.snared),
      books: [], hasPaper: this.paperFound,
    };
  }

  worldSync(): WorldSync {
    return {
      openDoors: [...this.open], unlockedDoors: [...this.unlocked], openFakeDoors: [...this.openFake],
      openContainers: [...this.containersOpen], emptiedContainers: [...this.emptied], takenPickups: [...this.taken],
      usedLevers: [...this.levers], brokenLights: [...this.broken], flickerRooms: [...this.flicker],
      hideOccupants: Object.fromEntries(this.hideOcc), booksFound: [...this.books.values()].filter((b): b is { shape: number; digit: number } => !!b),
      bookIds: [...this.books.keys()], paperFound: this.paperFound, libraryUnlocked: this.libraryUnlocked, hvDoorOpen: this.hvDoorOpen,
      breakersInserted: this.breakersInserted, breakerRound: this.breakerRound, breakerSwitches: [...this.switches], powerOn: this.powerOn,
      furthestDoor: this.furthestDoor, haltDone: [...this.haltDone], fallenChandeliers: [...this.fallen],
      droppedItems: [...this.dropped].map(([id, d]) => ({ id: `drop:${id}`, item: d.item, pos: d.pos })), jackDoors: [...this.jackDone],
    };
  }

  endGame(result: 'win' | 'lose') {
    if (this.ended) return;
    this.flush();
    this.ended = true;
    if (this.timer) clearInterval(this.timer);
    const stats = this.players().map((p) => ({ id: p.id, name: p.name, door: p.furthest, gold: p.gold, deaths: p.deaths, cause: p.cause, won: result === 'win' && p.inElevator && !p.dead }));
    const knobs = this.opts.onEnd?.(result, stats) ?? {};
    for (const p of this.ps.values()) this.send(p, { t: 'end', result, door: this.furthestDoor, stats: stats.map(({ won: _w, ...s }) => s), knobs: knobs[p.id] ?? 0 });
  }

  /** Stop the simulation loop (room disposal). */
  dispose() {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    this.ended = true;
  }

  // ---- test helpers
  debugState() {
    return { haltDone: [...this.haltDone], unlocked: [...this.unlocked], opened: [...this.opened], libraryUnlocked: this.libraryUnlocked, powerOn: this.powerOn, breakerRound: this.breakerRound, hvDoorOpen: this.hvDoorOpen, books: [...this.books.values()], hideOcc: Object.fromEntries(this.hideOcc), taken: [...this.taken], emptied: [...this.emptied] };
  }
  isInside(p: Vec3, room: number) { return this.rooms[room].walkable.some((w) => pointInBox(p, w, 0.05)); }
}
