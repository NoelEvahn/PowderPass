import * as THREE from 'three';
import type { EntitySnap, GameEvent, Interactable, PlayerSnap, RunLayout, S2C, SelfState, Vec3 } from '@doors/shared';
import { FINAL_ROOM, ITEMS, LIBRARY_ROOM, PLAYER, WorldQuery, generateRun, lookDir, angleDiff } from '@doors/shared';
import type { Renderer } from '../render/Renderer';
import type { Net } from '../net/Net';
import type { UI } from '../ui/UI';
import type { Input, Action } from '../input/Input';
import type { Audio, Surface } from '../audio/Audio';
import { Physics } from '../physics/Physics';
import { WorldView } from './WorldView';
import { itemMesh } from '../assets/items';
import { makeAvatar, makeEyes, makeFigure, makeHalt, makeRush, makeScreech, makeSeek, type Creature } from '../assets/monsters';
import { settings } from '../settings';

interface Snap<T> { t: number; s: T }
interface Remote { id: string; name: string; av: ReturnType<typeof makeAvatar>; label: THREE.Sprite; buf: Snap<PlayerSnap>[]; light: THREE.SpotLight | null; last: PlayerSnap | null; stepAcc: number; prev: THREE.Vector3 }
interface EntView { id: string; kind: EntitySnap['kind']; c: Creature; buf: Snap<EntitySnap>[]; snd: ReturnType<Audio['play']>; last: EntitySnap | null; prevPos: THREE.Vector3; speed: number }
interface Target { id: string; label: string; pos: Vec3; kind: string; it?: Interactable }

const PLAYER_COLORS = [0x6a2a2a, 0x2a4a6a, 0x3a5a2a, 0x6a5a2a];

function nameSprite(name: string) {
  const c = document.createElement('canvas');
  c.width = 256; c.height = 64;
  const ctx = c.getContext('2d')!;
  ctx.font = '600 30px Inter, sans-serif';
  ctx.textAlign = 'center';
  ctx.fillStyle = 'rgba(0,0,0,0.45)';
  const w = ctx.measureText(name).width + 24;
  ctx.fillRect(128 - w / 2, 10, w, 44);
  ctx.fillStyle = '#f0e6d0';
  ctx.fillText(name, 128, 43);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: t, depthTest: true, transparent: true }));
  s.scale.set(0.9, 0.225, 1);
  return s;
}

/** Client gameplay: owns the world view, local player, remote players, entities and all feedback. */
export class Game {
  layout!: RunLayout;
  world!: WorldView;
  query!: WorldQuery;
  physics!: Physics;
  me = '';
  self: SelfState | null = null;
  yaw = 0;
  pitch = 0;
  pos = new THREE.Vector3();
  room = 0;
  private eyeH = PLAYER.eye;
  private stamina = 100;
  private seq = 0;
  private sendAcc = 0;
  private remotes = new Map<string, Remote>();
  private ents = new Map<string, EntView>();
  private timeOffset: number | null = null;
  private target: Target | null = null;
  private bob = 0;
  private stepAcc = 0;
  private shake = 0;
  private damageFx = 0;
  private distortFx = 0;
  private viewItem: THREE.Object3D | null = null;
  private viewType = '';
  private flash: THREE.SpotLight;
  private lighter: THREE.PointLight;
  private hemi: THREE.HemisphereLight;
  private holder = new THREE.Group();
  private books = new Map<number, number>();
  private booksSearched = 0;
  private paper = false;
  private breaker = { inserted: 0, round: 0, switches: new Array(10).fill(false) as boolean[], lit: [] as number[], power: false };
  private spectate = 0;
  private haltTint = 0;
  private chase = false;
  private visitedRooms = new Set<number>();
  private ambientT = 0;
  private running = false;
  private hiddenKind: 'closet' | 'bed' | 'locker' | null = null;
  private hideLook = { yaw: 0 };
  private elevatorOpenAt = 0;
  private deathT = -1;
  private warnTimer = 0;
  private lastHp = 100;
  private shopOpen = false;
  private cleanup: (() => void)[] = [];
  private seekActive = false;
  private gt = 0;
  private flashOn = false;
  private readonly tmpV = new THREE.Vector3();

  constructor(private r: Renderer, private net: Net, private ui: UI, private input: Input, private audio: Audio) {
    this.flash = new THREE.SpotLight(0xfff2d8, 0, 22, 0.5, 0.55, 1.4);
    this.flash.castShadow = r.preset.shadows;
    this.flash.shadow.mapSize.set(r.preset.shadowSize, r.preset.shadowSize);
    this.flash.shadow.bias = -0.0015;
    this.flash.shadow.camera.near = 0.1;
    this.lighter = new THREE.PointLight(0xffa850, 0, 6, 1.6);
    this.hemi = new THREE.HemisphereLight(0x8a7a6a, 0x1a1008, 0.12);
    r.camera.add(this.holder);
    r.camera.add(this.flash);
    r.camera.add(this.flash.target);
    this.flash.position.set(0.2, -0.15, -0.1);
    this.flash.target.position.set(0.1, -0.2, -5);
    r.camera.add(this.lighter);
    this.lighter.position.set(0.25, -0.15, -0.4);
  }

  // ------------------------------------------------------------------ lifecycle

  async start(m: Extract<S2C, { t: 'start' }>) {
    this.stop();
    this.me = m.you;
    this.ui.setLoading('Generating the hotel…', 0.15);
    await new Promise((r) => setTimeout(r, 30));
    this.layout = generateRun(m.seed);
    if (this.layout.version !== m.version) this.ui.notice('Version mismatch with the server. Please reload.');
    this.query = new WorldQuery(this.layout);
    this.ui.setLoading('Building physics…', 0.35);
    this.physics = new Physics();
    await this.physics.init();
    this.ui.setLoading('Furnishing rooms…', 0.6);
    await new Promise((r) => setTimeout(r, 30));
    const p = this.r.preset;
    this.world = new WorldView(this.layout, this.r.scene, this.physics, p.level, p.lightPool, p.shadowLights, p.shadowSize);
    this.r.scene.add(this.hemi);
    const sp = m.spawn[0];
    this.pos.set(sp.x, sp.y, sp.z);
    this.room = 0;
    this.world.stream(0);
    this.physics.teleport(sp);
    this.yaw = 0; this.pitch = 0;
    for (const pl of m.players) if (pl.id !== this.me) this.addRemote(pl.id, pl.name, m.players.indexOf(pl));
    this.ui.setLoading('Entering…', 1);
    this.running = true;
    this.elevatorOpenAt = performance.now() / 1000 + 2.2;
    this.world.views.get(0)?.snapPart('elevatorDoors', 0);
    this.visitedRooms = new Set([0]);
    this.books.clear(); this.paper = false; this.booksSearched = 0;
    this.deathT = -1; this.chase = false; this.seekActive = false;
    this.ui.hideLibrary();
    this.cleanup.push(this.input.on((a) => this.onAction(a)));
    this.audio.loop('roomtone', 0.5);
    this.audio.setReverb('medium');
  }

  stop() {
    this.running = false;
    for (const c of this.cleanup) c();
    this.cleanup = [];
    for (const r of this.remotes.values()) { r.av.root.removeFromParent(); r.label.removeFromParent(); r.light?.removeFromParent(); }
    this.remotes.clear();
    for (const e of this.ents.values()) this.removeEnt(e);
    this.ents.clear();
    this.world?.dispose();
    this.hemi.removeFromParent();
    this.setViewItem(null);
    this.flash.intensity = 0; this.lighter.intensity = 0;
    for (const n of ['roomtone', 'rain', 'electric', 'wind', 'fire', 'musicbox', 'eyesHum', 'haltDrone']) this.audio.loop(n, 0);
    this.audio.chaseMusic(false);
    this.ui.closeModals();
    this.ui.endHeartbeat();
    this.ui.hideOverlay(null);
    this.ui.warning(null);
    this.timeOffset = null;
  }

  private addRemote(id: string, name: string, idx: number) {
    const av = makeAvatar(PLAYER_COLORS[idx % PLAYER_COLORS.length]);
    const label = nameSprite(name);
    this.r.scene.add(av.root);
    this.r.scene.add(label);
    const light = this.r.preset.level > 0 ? new THREE.SpotLight(0xfff0d0, 0, 16, 0.45, 0.5, 1.5) : null;
    if (light) { this.r.scene.add(light); this.r.scene.add(light.target); }
    this.remotes.set(id, { id, name, av, label, buf: [], light, last: null, stepAcc: 0, prev: new THREE.Vector3() });
  }

  // ------------------------------------------------------------------ messages

  handle(m: S2C) {
    if (!this.running) return;
    switch (m.t) {
      case 'snap': this.onSnap(m); break;
      case 'self': this.onSelf(m.s); break;
      case 'ev': for (const e of m.list) this.onEvent(e); break;
      case 'sync': this.world.applySync(m.w); this.world.stream(this.room); this.paper = m.w.paperFound; for (const b of m.w.booksFound) this.books.set(b.shape, b.digit); this.breaker = { inserted: m.w.breakersInserted, round: m.w.breakerRound, switches: m.w.breakerSwitches, lit: [], power: m.w.powerOn }; break;
      case 'correct': this.pos.set(m.x, m.y, m.z); this.physics.teleport(m); break;
    }
  }

  private onSnap(m: Extract<S2C, { t: 'snap' }>) {
    const now = performance.now() / 1000;
    const off = m.time - now;
    this.timeOffset = this.timeOffset === null ? off : Math.max(off, this.timeOffset * 0.98 + off * 0.02);
    for (const ps of m.players) {
      if (ps.id === this.me) continue;
      let rm = this.remotes.get(ps.id);
      if (!rm) { this.addRemote(ps.id, ps.name, this.remotes.size + 1); rm = this.remotes.get(ps.id)!; }
      rm.buf.push({ t: m.time, s: ps });
      if (rm.buf.length > 30) rm.buf.shift();
    }
    const seen = new Set<string>();
    for (const es of m.entities) {
      seen.add(es.id);
      let ev = this.ents.get(es.id);
      if (!ev) ev = this.spawnEnt(es);
      ev.buf.push({ t: m.time, s: es });
      if (ev.buf.length > 30) ev.buf.shift();
    }
    for (const [id, ev] of this.ents) if (!seen.has(id)) { this.removeEnt(ev); this.ents.delete(id); }
    const seek = m.entities.some((e) => e.kind === 'seek');
    if (seek !== this.seekActive) { this.seekActive = seek; }
  }

  private spawnEnt(es: EntitySnap): EntView {
    let c: Creature;
    switch (es.kind) {
      case 'figure': c = makeFigure(); break;
      case 'seek': c = makeSeek(); break;
      case 'rush': c = makeRush('rush'); break;
      case 'ambush': c = makeRush('ambush'); break;
      case 'screech': c = makeScreech(); break;
      case 'eyes': c = makeEyes(); break;
      case 'halt': c = makeHalt(); break;
      default: c = { root: new THREE.Group(), update() { /* invisible */ } };
    }
    this.r.scene.add(c.root);
    c.root.position.set(es.x, es.y, es.z);
    let snd: ReturnType<Audio['play']> = null;
    if (es.kind === 'eyes') snd = this.audio.play('eyesHum', { pos: es, loop: true, vol: 0.8, refDist: 3 });
    if (es.kind === 'halt') snd = this.audio.play('haltDrone', { loop: true, vol: 0.7 });
    const ev: EntView = { id: es.id, kind: es.kind, c, buf: [], snd, last: null, prevPos: new THREE.Vector3(es.x, es.y, es.z), speed: 0 };
    this.ents.set(es.id, ev);
    return ev;
  }

  private removeEnt(ev: EntView) {
    ev.c.root.removeFromParent();
    ev.c.dispose?.();
    ev.snd?.stop(0.6);
  }

  private onSelf(s: SelfState) {
    const prev = this.self;
    this.self = s;
    this.ui.setSelf(s);
    this.stamina = s.stamina;
    if (s.hp < this.lastHp) this.damageFx = Math.min(1, this.damageFx + (this.lastHp - s.hp) / 60);
    this.lastHp = s.hp;
    if (s.hidden && !prev?.hidden) {
      const ref = this.world.interactable(s.hidden);
      this.hiddenKind = ref?.it.hide ?? 'closet';
      this.ui.hideOverlay(this.hiddenKind);
      const it = ref?.it;
      if (it?.exitPos) this.hideLook.yaw = Math.atan2(-(it.exitPos.x - it.pos.x), -(it.exitPos.z - it.pos.z));
      this.yaw = this.hideLook.yaw; this.pitch = 0;
      this.audio.play('closet', { pos: it?.pos, vol: 0.6 });
    } else if (!s.hidden && prev?.hidden) {
      this.hiddenKind = null;
      this.ui.hideOverlay(null);
      this.ui.warning(null);
    }
    if (s.dead && !prev?.dead) this.onMyDeath();
    if (!s.dead && prev?.dead) { this.ui.hideDeath(); this.deathT = -1; this.ui.showHud(true, this.input.touch); }
    const eq = s.inventory[s.equipped];
    this.setViewItem(eq ? eq.type : null);
    this.flashOn = !!eq?.on;
    if (this.shopOpen) this.ui.renderShop(s.gold);
  }

  private causeOfDeath = 'unknown';
  private onMyDeath() {
    this.deathT = 0;
    this.audio.play('death', { vol: 0.9 });
    this.ui.closeModals();
    this.ui.endHeartbeat();
    this.ui.hideOverlay(null);
    setTimeout(() => this.ui.showDeath(this.causeOfDeath, this.self?.revives ?? 0), 1600);
  }

  private say(text: string) { this.ui.subtitle(text); }

  private onEvent(e: GameEvent) {
    this.world.onEvent(e);
    const mine = (id?: string) => id === this.me;
    const at = (n: number) => this.layout.rooms[n]?.origin;
    switch (e.e) {
      case 'door': this.audio.play('doorOpen', { pos: at(e.n), vol: 0.9 }); break;
      case 'doorSlam': this.audio.play('doorSlam', { pos: at(e.n), vol: 1 }); this.shake = Math.max(this.shake, 0.3); break;
      case 'doorUnlock': this.audio.play('unlock', { pos: at(e.n) }); break;
      case 'fakeDoor': {
        const [, rs, ks] = e.id.split(':');
        const ex = this.layout.rooms[Number(rs)]?.exits[Number(ks)];
        this.audio.play('dupe', { pos: ex?.world, vol: 1 });
        if (mine(e.by)) this.ui.jumpscare('dupe', 600);
        this.say('[Dupe snarls]');
        break;
      }
      case 'container': {
        const ref = this.world.interactable(e.id);
        if (ref) this.audio.play('drawer', { pos: ref.it.pos, vol: 0.7 });
        break;
      }
      case 'loot':
        if (mine(e.by)) {
          if (e.gold) { this.audio.play('coins', { vol: 0.8 }); this.ui.notice(`+${e.gold} gold`); }
          if (e.item) { this.audio.play('pickup'); this.ui.notice(`Found ${ITEMS[e.item].name}`); }
        }
        break;
      case 'pickup': if (mine(e.by)) { this.audio.play(e.item === 'key' || e.item === 'electricalKey' ? 'unlock' : 'pickup', { vol: 0.7 }); this.ui.notice(`Picked up ${ITEMS[e.item].name}`); } break;
      case 'lever': this.audio.play('lever', { pos: this.world.interactable(e.id)?.it.pos }); break;
      case 'lights':
        if (e.mode === 'flicker' || e.mode === 'green') { this.audio.play('creak', { vol: 0.6 }); this.say(e.mode === 'green' ? '[Lights flicker green]' : '[Lights flicker]'); }
        if (e.mode === 'broken' && e.rooms.some((r) => Math.abs(r - this.room) <= 1)) { this.audio.play('glass', { vol: 0.7, pos: this.layout.rooms[e.rooms[0]].path[1] }); }
        break;
      case 'damage':
        if (mine(e.id)) { this.audio.play('hit', { vol: 0.9 }); this.shake = Math.max(this.shake, 0.5); this.causeOfDeath = e.cause; }
        break;
      case 'heal': if (mine(e.id)) this.ui.notice(`Healed to ${e.hp} HP`); break;
      case 'death':
        if (mine(e.id)) this.causeOfDeath = e.cause;
        else this.ui.notice(`${this.remotes.get(e.id)?.name ?? 'Someone'} died`);
        break;
      case 'revive': if (!mine(e.id)) this.ui.notice(`${this.remotes.get(e.id)?.name ?? 'Someone'} was revived`); break;
      case 'entity': this.onEntityEvent(e); break;
      case 'warn':
        if (e.kind === 'screech' && mine(e.target)) {
          const d = e.dir ?? 0;
          const p = this.r.camera.position;
          this.audio.play('psst', { pos: { x: p.x - Math.sin(d) * 1.5, y: p.y, z: p.z - Math.cos(d) * 1.5 }, vol: 1, refDist: 0.8 });
          const rel = angleDiff(this.yaw, d);
          this.say(`[Psst!] ${Math.abs(rel) < 0.6 ? 'ahead' : Math.abs(rel) > 2.4 ? 'behind you' : rel > 0 ? 'to your left' : 'to your right'}`);
        }
        if ((e.kind === 'haltFront' || e.kind === 'haltBehind') && e.room === this.room) {
          this.haltTint = 1;
          this.audio.play('haltWhoosh', { vol: 0.8 });
          this.distortFx = 0.5;
          if (e.kind === 'haltFront') { this.ui.warning('TURN AROUND', 'halt'); this.say('[TURN AROUND]'); clearTimeout(this.warnTimer); this.warnTimer = window.setTimeout(() => this.ui.warning(null), 2200); }
        }
        if (e.kind === 'hide' && mine(e.target)) { this.ui.warning('GET OUT', 'hide'); this.audio.play('whisper', { vol: 1 }); this.say('[Get out…]'); clearTimeout(this.warnTimer); this.warnTimer = window.setTimeout(() => this.ui.warning(null), 3000); }
        break;
      case 'book': {
        this.audio.play('page', { pos: this.world.interactable(e.id)?.it.pos });
        this.booksSearched++;
        if (e.shape !== undefined) { this.books.set(e.shape, e.digit!); if (mine(e.by)) this.ui.notice('A hint is written inside the book.'); }
        else if (mine(e.by)) this.ui.notice('Nothing useful in this book.');
        break;
      }
      case 'paper': this.paper = true; this.ui.notice('The hint paper shows five shapes in order.'); break;
      case 'padlock': if (e.ok) { this.audio.play('unlock'); this.ui.notice('The padlock clicks open.'); if (this.ui.modalOpen === 'padlock') this.ui.closeModals(); } else { this.audio.play('locked'); if (mine(e.by)) this.ui.notice('Wrong code. Something heard that…'); } break;
      case 'hvDoor': this.audio.play('unlock'); this.ui.notice('The electrical room is open.'); break;
      case 'breaker':
        this.breaker = { inserted: e.inserted, round: e.round, switches: e.switches, lit: e.lit, power: !!e.complete };
        this.audio.play('breaker', { pos: this.layout.rooms[FINAL_ROOM].interactables.find((i) => i.kind === 'breakerBox')?.pos });
        if (this.ui.modalOpen === 'breakerPanel') this.ui.renderBreaker(e.inserted, e.round, e.switches, e.lit, !!e.complete);
        break;
      case 'power':
        this.audio.play('thunder', { vol: 0.8 }); this.audio.play('elevatorDing', { vol: 0.6 });
        this.ui.notice('POWER RESTORED. Get to the elevator!');
        this.shake = 0.6;
        break;
      case 'heartbeat': if (mine(e.target)) { this.ui.startHeartbeat(e.beats, e.window); this.audio.play('heartbeat'); } break;
      case 'heartbeatResult': if (mine(e.target)) { this.ui.endHeartbeat(); if (e.ok) this.ui.notice('It moved on…'); } break;
      case 'snare': if (mine(e.target)) { this.audio.play('snare', { vol: 1 }); this.ui.notice('Caught in a snare!'); this.shake = 0.4; } break;
      case 'chandelier': setTimeout(() => this.audio.play('crash', { vol: 1 }), 800); this.say('[Chandelier crashes]'); break;
      case 'glitch': if (mine(e.target)) { this.distortFx = 1.4; this.audio.play('glitch', { vol: 1 }); this.ui.notice('You fell behind…'); } break;
      case 'scare':
        if (e.kind === 'timothy' && mine(e.target)) { this.ui.jumpscare('timothy', 650); this.audio.play('timothy', { vol: 1 }); this.say('[Timothy]'); }
        if (e.kind === 'jack' && mine(e.target)) { this.ui.jumpscare('jack', 600); this.audio.play('jack', { vol: 1 }); this.audio.play('doorSlam'); }
        if (e.kind === 'hide' && mine(e.target)) { this.ui.jumpscare('hide', 800); }
        if (e.kind === 'shadow' && e.room !== undefined) this.spawnShadow(e.room);
        break;
      case 'purchase': if (mine(e.by)) { if (e.ok) { this.audio.play('coins'); this.ui.notice(`Bought ${ITEMS[e.item].name}`); } else this.ui.notice('Not enough gold.'); } break;
      case 'shop': if (mine(e.target)) { this.shopOpen = true; this.ui.openModal('shop'); this.ui.renderShop(this.self?.gold ?? 0); this.input.unlock(); } break;
      case 'chase':
        this.chase = e.phase === 'start';
        this.audio.chaseMusic(this.chase);
        if (this.chase) { this.ui.notice('RUN!'); this.audio.play('seekRoar', { vol: 1 }); }
        break;
      case 'elevator':
        if (e.phase === 'boarding') this.ui.notice(`The elevator doors close in ${Math.ceil(e.countdown)}s. Everyone get in!`);
        else this.audio.play('elevatorDing', { vol: 1 });
        break;
      case 'notice': if (!e.target || mine(e.target)) this.ui.notice(e.text); break;
      case 'crucifix': this.ui.notice(`${mine(e.by) ? 'Your' : (this.remotes.get(e.by)?.name ?? 'A') + "'s"} crucifix repelled ${e.kind}!`); this.audio.play('chime'); this.distortFx = 0.8; break;
      case 'drop': case 'container': default: break;
    }
  }

  private onEntityEvent(e: Extract<GameEvent, { e: 'entity' }>) {
    const pos = () => this.ents.get(e.id)?.c.root.position;
    switch (e.kind) {
      case 'rush': case 'ambush':
        if (e.phase === 'warn') this.say(e.kind === 'rush' ? '[Distant rumbling]' : '[A shriek in the distance]');
        if (e.phase === 'run' || e.phase === 'rebound') {
          const ev = this.ents.get(e.id);
          const p = ev?.c.root.position ?? this.layout.rooms[Math.max(0, (e.room ?? 1) - 3)].path[0];
          ev?.snd?.stop(0.2);
          const s = this.audio.play(e.kind, { pos: p, vol: 1.4, refDist: 4, loop: true });
          if (ev) ev.snd = s;
          else setTimeout(() => s?.stop(0.5), 4000);
          this.say(e.kind === 'rush' ? '[Rush roars]' : '[Ambush screams]');
        }
        if (e.phase === 'gone' || e.phase === 'banished') this.ents.get(e.id)?.snd?.stop(0.8);
        break;
      case 'seek':
        if (e.phase === 'rise') this.say('[Seek rises from the floor]');
        if (e.phase === 'grab') this.say('[Hands grab at you]');
        break;
      case 'figure':
        if (e.phase === 'intro') { this.audio.play('figureRoar', { pos: pos(), vol: 1.2, refDist: 4 }); this.say('[The Figure roars]'); }
        if (e.phase === 'chase' || e.phase === 'enraged') { this.audio.play('figureRoar', { pos: pos(), vol: 1, refDist: 4 }); this.say('[The Figure charges]'); }
        if (e.phase === 'investigate') { this.audio.play('figureGrowl', { pos: pos(), vol: 0.9, refDist: 3 }); this.say('[The Figure listens]'); }
        if (e.phase === 'stunned') this.say('[The Figure recoils]');
        break;
      case 'screech':
        if (e.phase === 'attack') { this.audio.play('screech', { vol: 1 }); this.say('[Screech bites]'); }
        if (e.phase === 'flee') this.say('[Screech retreats]');
        break;
      case 'eyes': if (e.phase === 'spawn') this.say('[A low hum… don\'t look]'); break;
      case 'dupe': break;
    }
  }

  private spawnShadow(room: number) {
    const r = this.layout.rooms[room];
    const p = r.path[Math.min(2, r.path.length - 1)];
    const s = new THREE.Mesh(new THREE.PlaneGeometry(0.8, 2), new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.85 }));
    s.position.set(p.x, p.y + 1, p.z);
    s.lookAt(this.r.camera.position);
    this.r.scene.add(s);
    setTimeout(() => { s.removeFromParent(); s.geometry.dispose(); }, 180);
  }

  // ------------------------------------------------------------------ actions

  private onAction(a: Action) {
    if (!this.running) return;
    const s = this.self;
    if (a === 'beat') { const i = this.ui.heartbeatPress(); if (i >= 0) { this.net.send({ t: 'heartbeat', idx: i }); this.audio.play('heartbeat', { vol: 0.7 }); } return; }
    if (a === 'spectateNext') { this.spectate++; return; }
    if (a === 'crouchToggle') { this.input.toggleCrouch(); return; }
    if (!s || s.dead) return;
    if (a.startsWith('slot')) { this.net.send({ t: 'equip', slot: Number(a.slice(4)) }); this.audio.play('click', { vol: 0.4 }); return; }
    if (a === 'light') { this.net.send({ t: 'toggleLight' }); this.audio.play(s.inventory[s.equipped]?.type === 'lighter' ? 'lighter' : 'click', { vol: 0.5 }); return; }
    if (a === 'drop') { if (s.equipped >= 0) this.net.send({ t: 'drop', slot: s.equipped }); return; }
    if (a === 'use') {
      if (s.equipped < 0) return;
      const it = s.inventory[s.equipped];
      if (!it) return;
      if (it.type === 'flashlight' || it.type === 'lighter') { this.net.send({ t: 'toggleLight' }); this.audio.play('click', { vol: 0.5 }); }
      else this.net.send({ t: 'use', slot: s.equipped });
      return;
    }
    if (a === 'interact') {
      if (s.hidden) { this.net.send({ t: 'unhide' }); this.audio.play('closet', { vol: 0.6 }); return; }
      const t = this.target;
      if (!t) return;
      if (t.kind === 'padlock') { this.ui.openModal('padlock'); this.input.unlock(); return; }
      if (t.kind === 'breakerBox') {
        this.net.send({ t: 'interact', id: t.id });
        this.ui.openModal('breakerPanel');
        this.ui.renderBreaker(this.breaker.inserted, this.breaker.round, this.breaker.switches, this.breaker.lit, this.breaker.power);
        this.input.unlock();
        return;
      }
      if (t.kind === 'locked') { this.audio.play('locked', { pos: t.pos }); }
      this.net.send({ t: 'interact', id: t.id });
    }
  }

  closeShop() { this.shopOpen = false; this.ui.closeModals(); this.net.send({ t: 'closeShop' }); this.input.lock(); }

  // ------------------------------------------------------------------ frame

  frame(dt: number) {
    if (!this.running || !this.world) return;
    this.gt += dt;
    const s = this.self;
    const dead = !!s?.dead;
    const hidden = s?.hidden ?? null;
    const cam = this.r.camera;
    // look
    const [lx, ly] = this.input.consumeLook();
    const modal = !!this.ui.modalOpen;
    if (!modal) {
      this.yaw -= lx;
      this.pitch = Math.max(-1.45, Math.min(1.45, this.pitch - ly));
      if (hidden) {
        const d = angleDiff(this.hideLook.yaw, this.yaw);
        if (Math.abs(d) > 1.0) this.yaw = this.hideLook.yaw + Math.sign(d) * 1.0;
        this.pitch = Math.max(-0.5, Math.min(0.5, this.pitch));
      }
    }
    // movement
    this.input.poll();
    let speed = 0;
    if (!dead && !hidden && !modal && s && s.snared <= 0 && this.physics.ready) {
      const wantCrouch = this.input.crouch;
      if (wantCrouch !== this.physics.crouched) this.physics.setCrouch(wantCrouch);
      const crouch = this.physics.crouched;
      const mx = this.input.moveX, my = this.input.moveY;
      const mlen = Math.min(1, Math.hypot(mx, my));
      const moving = mlen > 0.1;
      const freeStamina = this.seekActive || this.world.powerOn || (s.boost ?? 0) > 0;
      const sprint = this.input.sprint && moving && !crouch && (this.stamina > 1 || freeStamina);
      if (sprint && !freeStamina) this.stamina = Math.max(0, this.stamina - PLAYER.staminaDrain * dt);
      else if (!sprint) this.stamina = Math.min(100, this.stamina + PLAYER.staminaRegen * dt);
      this.ui.setStamina(this.stamina);
      speed = (crouch ? PLAYER.crouchSpeed : sprint ? PLAYER.sprintSpeed : PLAYER.walkSpeed) * ((s.boost ?? 0) > 0 ? 1.25 : 1) * mlen;
      const fx = -Math.sin(this.yaw), fz = -Math.cos(this.yaw);
      const rx = Math.cos(this.yaw), rz = -Math.sin(this.yaw);
      let dx = 0, dz = 0;
      if (moving) { const n = Math.hypot(mx, my) || 1; dx = (fx * my + rx * mx) / n * speed * dt; dz = (fz * my + rz * mx) / n * speed * dt; }
      const before = this.pos.clone();
      const f = this.physics.move(dx, dz, dt);
      this.pos.set(f.x, f.y, f.z);
      const moved = Math.hypot(this.pos.x - before.x, this.pos.z - before.z);
      speed = moved / Math.max(dt, 1e-4);
      // network send @20Hz
      this.sendAcc += dt;
      if (this.sendAcc >= 0.05) {
        this.sendAcc = 0;
        this.net.send({ t: 'input', seq: ++this.seq, x: +this.pos.x.toFixed(3), y: +this.pos.y.toFixed(3), z: +this.pos.z.toFixed(3), yaw: +this.yaw.toFixed(3), pitch: +this.pitch.toFixed(3), crouch, sprint, moving: moved > 0.001 });
      }
      // footsteps
      this.stepAcc += moved;
      const stride = crouch ? 0.9 : sprint ? 1.6 : 1.2;
      if (this.stepAcc > stride) { this.stepAcc = 0; this.audio.step(this.surface(this.room), { x: this.pos.x, y: this.pos.y, z: this.pos.z }, crouch ? 0.15 : sprint ? 0.9 : 0.5); }
      this.bob += moved * (sprint ? 5.5 : 6.5);
    } else if (!dead && this.physics.ready) {
      // still send look direction (Eyes/Screech checks need it)
      this.sendAcc += dt;
      if (this.sendAcc >= 0.1) { this.sendAcc = 0; this.net.send({ t: 'input', seq: ++this.seq, x: this.pos.x, y: this.pos.y, z: this.pos.z, yaw: this.yaw, pitch: this.pitch, crouch: this.physics.crouched, sprint: false, moving: false }); }
    }
    // room tracking & streaming
    const r = this.query.roomAt(this.pos, this.room, 0.4);
    if (r >= 0 && r !== this.room) this.onRoomChange(r);
    this.world.stream(this.room);

    // camera
    const targetEye = this.physics.crouched ? PLAYER.crouchEye : PLAYER.eye;
    this.eyeH += (targetEye - this.eyeH) * Math.min(1, dt * 10);
    if (dead) this.spectateCam(dt);
    else if (hidden) {
      const ref = this.world.interactable(hidden);
      const p = ref?.it.pos ?? this.pos;
      const bed = this.hiddenKind === 'bed';
      cam.position.set(p.x, (ref?.it.exitPos?.y ?? this.pos.y) + (bed ? 0.25 : 1.45), p.z);
    } else {
      const bobY = Math.sin(this.bob) * 0.035 * Math.min(1, speed / 3);
      const bobX = Math.cos(this.bob * 0.5) * 0.02 * Math.min(1, speed / 3);
      cam.position.set(this.pos.x + Math.cos(this.yaw) * bobX, this.pos.y + this.eyeH + bobY, this.pos.z - Math.sin(this.yaw) * bobX);
    }
    if (!dead) cam.rotation.set(this.pitch, this.yaw, 0, 'YXZ');
    this.shake = Math.max(0, this.shake - dt * 1.2);
    // proximity shake from Rush/Ambush/Seek
    for (const ev of this.ents.values()) {
      if (ev.kind !== 'rush' && ev.kind !== 'ambush' && ev.kind !== 'seek') continue;
      const d = ev.c.root.position.distanceTo(cam.position);
      if (ev.last?.state === 'run' || ev.kind === 'seek') this.shake = Math.max(this.shake, Math.max(0, 1 - d / 18) * 0.6);
    }
    if (this.shake > 0) { cam.position.x += (Math.random() - 0.5) * this.shake * 0.06; cam.position.y += (Math.random() - 0.5) * this.shake * 0.06; cam.rotation.z += (Math.random() - 0.5) * this.shake * 0.02; }
    cam.fov = settings.fov + (speed > 4.5 ? 6 : 0);
    cam.updateProjectionMatrix();
    cam.updateMatrixWorld();

    // world, remotes, entities
    this.world.update(dt, cam, this.room);
    if (performance.now() / 1000 > this.elevatorOpenAt && this.elevatorOpenAt > 0) { this.world.views.get(0)?.setPart('elevatorDoors', 1); this.audio.play('elevatorDing', { vol: 0.6 }); this.elevatorOpenAt = 0; }
    this.updateRemotes(dt);
    this.updateEnts(dt);
    this.updateLights(dt);
    this.updateTarget();
    this.updateHudExtras();
    this.updateAmbience(dt);
    this.audio.setListener(cam);
    // post effects
    this.damageFx = Math.max(0, this.damageFx - dt * 0.6);
    this.distortFx = Math.max(0, this.distortFx - dt * 0.8);
    this.haltTint = Math.max(0, this.haltTint - dt * 0.25);
    const lowHp = s ? Math.max(0, (40 - s.hp) / 40) * 0.4 : 0;
    const tint: [number, number, number] = this.room < this.layout.rooms.length && this.layout.rooms[this.room].kind === 'halt' ? [0.75, 0.85, 1.25] : this.chase ? [1.1, 0.92, 0.9] : [1, 1, 1];
    this.r.setGrade({ damage: Math.min(1, this.damageFx + lowHp), distort: this.distortFx + (dead && this.deathT < 1.6 ? 0.6 : 0), vignette: this.chase ? 1.35 : hidden ? 1.4 : 1.05, darkness: dead && this.deathT >= 0 && this.deathT < 1.6 ? Math.min(1, this.deathT / 1.6) : 0 }, tint);
    if (this.deathT >= 0) this.deathT += dt;
    this.ui.updateHeartbeat();
    if (settings.showFps) this.ui.fps(`${this.r.fps.toFixed(0)} fps · door ${this.room} · rtt ${(this.net.rtt * 1000).toFixed(0)}ms · ${this.r.renderer.info.render.calls} calls`);
    else this.ui.fps(null);
  }

  private surface(room: number): Surface {
    const f = this.layout.rooms[room]?.theme.floor ?? 'wood';
    return f as Surface;
  }

  private onRoomChange(r: number) {
    this.room = r;
    if (!this.visitedRooms.has(r) && r > 0) {
      this.visitedRooms.add(r);
      this.ui.door(r);
    }
    const room = this.layout.rooms[r];
    const area = (room.xMax - room.xMin) * room.depth;
    this.audio.setReverb(room.theme.outdoor ? 'outdoor' : area > 250 ? 'large' : area > 60 ? 'medium' : 'small');
    const fog = this.r.scene.fog as THREE.FogExp2;
    fog.density = room.kind === 'greenhouse' ? 0.09 : room.kind === 'courtyard' ? 0.035 : room.kind === 'library' ? 0.045 : room.dark ? 0.085 : 0.06;
    if (r === LIBRARY_ROOM) this.ui.library(this.paper, room.puzzle?.shapes ?? [], this.books, this.booksSearched);
    else this.ui.hideLibrary();
  }

  private interp<T extends { x: number; y: number; z: number; yaw: number }>(buf: Snap<T>[], renderT: number): { s: T; x: number; y: number; z: number; yaw: number } | null {
    if (!buf.length) return null;
    let a = buf[0], b = buf[buf.length - 1];
    for (let i = 0; i < buf.length - 1; i++) if (buf[i].t <= renderT && buf[i + 1].t >= renderT) { a = buf[i]; b = buf[i + 1]; break; }
    if (renderT >= b.t) return { s: b.s, x: b.s.x, y: b.s.y, z: b.s.z, yaw: b.s.yaw };
    if (renderT <= a.t) return { s: a.s, x: a.s.x, y: a.s.y, z: a.s.z, yaw: a.s.yaw };
    const k = (renderT - a.t) / Math.max(1e-4, b.t - a.t);
    return { s: k < 0.5 ? a.s : b.s, x: a.s.x + (b.s.x - a.s.x) * k, y: a.s.y + (b.s.y - a.s.y) * k, z: a.s.z + (b.s.z - a.s.z) * k, yaw: a.s.yaw + angleDiff(a.s.yaw, b.s.yaw) * k };
  }

  private renderTime() {
    return performance.now() / 1000 + (this.timeOffset ?? 0) - 0.1;
  }

  private updateRemotes(dt: number) {
    const rt = this.renderTime();
    for (const rm of this.remotes.values()) {
      const it = this.interp(rm.buf, rt);
      if (!it) continue;
      const s = it.s;
      rm.last = s;
      const visible = !s.dead && !s.hidden && s.connected;
      rm.av.root.visible = visible;
      rm.label.visible = visible;
      rm.av.root.position.set(it.x, it.y, it.z);
      rm.av.root.rotation.y = it.yaw + Math.PI;
      rm.label.position.set(it.x, it.y + (s.crouch ? 1.3 : 2.05), it.z);
      const sp = rm.prev.distanceTo(rm.av.root.position) / Math.max(dt, 1e-4);
      rm.stepAcc += rm.prev.distanceTo(rm.av.root.position);
      rm.prev.copy(rm.av.root.position);
      rm.av.update(dt, s.crouch ? 'crouch' : s.equip ? 'holding' : 'walk', Math.min(8, sp), this.r.camera);
      if (visible && rm.stepAcc > 1.3) { rm.stepAcc = 0; this.audio.step(this.surface(s.room), { x: it.x, y: it.y, z: it.z }, s.crouch ? 0.1 : 0.45); }
      if (rm.light) {
        rm.light.intensity = s.light && visible ? (s.equip === 'lighter' ? 4 : 18) : 0;
        if (s.light) {
          const eye = { x: it.x, y: it.y + (s.crouch ? 0.95 : 1.5), z: it.z };
          rm.light.position.set(eye.x, eye.y, eye.z);
          const d = lookDir(s.yaw, s.pitch);
          rm.light.target.position.set(eye.x + d.x * 4, eye.y + d.y * 4, eye.z + d.z * 4);
        }
      }
    }
  }

  private updateEnts(dt: number) {
    const rt = this.renderTime();
    for (const ev of this.ents.values()) {
      const it = this.interp(ev.buf, rt);
      if (!it) continue;
      ev.last = it.s;
      const root = ev.c.root;
      root.position.set(it.x, it.y, it.z);
      if (ev.kind !== 'eyes' && ev.kind !== 'screech' && ev.kind !== 'rush' && ev.kind !== 'ambush') root.rotation.y = it.yaw + Math.PI;
      const sp = ev.prevPos.distanceTo(root.position) / Math.max(dt, 1e-4);
      ev.speed = ev.speed * 0.8 + sp * 0.2;
      // heavy Figure footsteps
      if (ev.kind === 'figure' && ev.speed > 0.4) {
        (ev as EntView & { acc?: number }).acc = ((ev as EntView & { acc?: number }).acc ?? 0) + ev.prevPos.distanceTo(root.position);
        if (((ev as EntView & { acc?: number }).acc ?? 0) > 1.4) { (ev as EntView & { acc?: number }).acc = 0; this.audio.play('figureStep', { pos: root.position, vol: 0.9, refDist: 3 }); }
      }
      ev.prevPos.copy(root.position);
      ev.snd?.setPos(root.position);
      ev.c.update(dt, it.s.state, ev.speed, this.r.camera);
    }
  }

  private updateLights(dt: number) {
    const s = this.self;
    const eq = s && s.equipped >= 0 ? s.inventory[s.equipped] : null;
    const on = !!eq?.on && !s?.dead;
    this.flash.intensity = on && eq?.type === 'flashlight' ? 28 * Math.min(1, (eq.charge ?? 1) * 3 + 0.2) : 0;
    this.lighter.intensity = on && eq?.type === 'lighter' ? 2.2 + Math.sin(this.gt * 23) * 0.3 + Math.random() * 0.3 : 0;
    // ambient level: darker in dark/broken rooms
    const room = this.layout.rooms[this.room];
    const dark = room?.dark || this.world.broken.has(this.room);
    const targetHemi = room?.theme.outdoor ? 0.22 : dark ? 0.012 : 0.1;
    this.hemi.intensity += (targetHemi - this.hemi.intensity) * Math.min(1, dt * 2);
    // viewmodel bob
    if (this.viewItem) {
      this.viewItem.position.set(0.24 + Math.cos(this.bob * 0.5) * 0.008, -0.24 + Math.sin(this.bob) * 0.01, -0.42);
      this.viewItem.visible = !s?.hidden && !s?.dead;
    }
  }

  private setViewItem(type: string | null) {
    if (type === this.viewType) return;
    this.viewType = type ?? '';
    if (this.viewItem) { this.holder.remove(this.viewItem); this.viewItem = null; }
    if (!type) return;
    const m = itemMesh(type as never);
    m.scale.setScalar(type === 'hintPaper' ? 0.8 : 1.4);
    if (type === 'key' || type === 'electricalKey' || type === 'skeletonKey') m.rotation.set(0, -0.6, 0);
    else m.rotation.set(0, -0.15, 0);
    m.traverse((o) => { (o as THREE.Mesh).castShadow = false; });
    this.holder.add(m);
    this.viewItem = m;
  }

  /** Find what the player is looking at (doors, drawers, closets, items…) and show a prompt. */
  private updateTarget() {
    const s = this.self;
    this.target = null;
    if (!s || s.dead || s.hidden || this.ui.modalOpen) {
      this.ui.prompt(s?.hidden ? 'Get out' : null);
      return;
    }
    const cam = this.r.camera;
    const eye = cam.position;
    const look = new THREE.Vector3(0, 0, -1).applyQuaternion(cam.quaternion);
    let best: Target | null = null, bestScore = Infinity;
    const consider = (t: Target, range = PLAYER.interactRange) => {
      const v = this.tmpV.set(t.pos.x - eye.x, t.pos.y - eye.y, t.pos.z - eye.z);
      const d = v.length();
      if (d > range) return;
      const ang = v.normalize().angleTo(look);
      const maxAng = Math.max(0.22, Math.atan(0.5 / Math.max(d, 0.3)));
      if (ang > maxAng) return;
      // blocked by walls?
      const hit = this.physics.ray({ x: eye.x, y: eye.y, z: eye.z }, { x: v.x, y: v.y, z: v.z }, Math.max(0, d - 0.65));
      if (hit !== null) return;
      const score = ang + d * 0.05;
      if (score < bestScore) { bestScore = score; best = t; }
    };
    const w = this.world;
    // exit door of the current room
    const room = this.layout.rooms[this.room];
    const n = this.room + 1;
    if (room && room.realExit >= 0 && !w.doorsOpen.has(n)) {
      const ex = room.exits[room.realExit];
      const lock = ex.lock;
      const locked = !w.doorsUnlocked.has(n) && lock !== 'none' && !(lock === 'key' && s.inventory.some((i) => i?.type === 'key' && i.door === n));
      const hasPick = s.inventory.some((i) => i?.type === 'lockpick' || i?.type === 'skeletonKey');
      const label = !locked ? `Open door ${n}` : lock === 'key' ? (hasPick ? `Pick the lock (door ${n})` : `Locked · find the key for ${n}`) : lock === 'lever' ? 'Gate is shut · find the lever' : lock === 'code' ? 'Padlocked · solve the code' : lock === 'halt' ? '…' : `Open door ${n}`;
      consider({ id: `door:${n}`, label, pos: w.doorPoint(n), kind: locked && !(lock === 'key' && hasPick) ? 'locked' : 'door' }, 2.9);
    }
    if (room) room.exits.forEach((e, k) => { if (!e.real) consider({ id: `fdoor:${room.index}:${k}`, label: e.fake === 'blocked' ? 'Blocked' : `Open door ${e.number}`, pos: { x: e.world.x, y: e.world.y + 1.1, z: e.world.z }, kind: 'fake' }, 2.9); });
    for (const ri of [this.room - 1, this.room, this.room + 1]) {
      const rr = this.layout.rooms[ri];
      if (!rr) continue;
      for (const it of rr.interactables) {
        let label = '';
        switch (it.kind) {
          case 'drawer': label = w.containersOpen.has(it.id) ? 'Close' : 'Open'; break;
          case 'hide': if (w.hideOcc.has(it.id)) continue; label = it.hide === 'bed' ? 'Hide under bed' : 'Hide'; break;
          case 'pickup': if (w.taken.has(it.id) || !it.loot?.item) continue; label = `Pick up ${ITEMS[it.loot.item].name}`; break;
          case 'book': if (w.books.has(it.id)) continue; label = 'Search book'; break;
          case 'lever': if (w.levers.has(it.id)) continue; label = 'Pull lever'; break;
          case 'padlock': if (w.doorsUnlocked.has(LIBRARY_ROOM + 1)) continue; label = 'Enter code'; break;
          case 'cabinetDoor': if (w.hvDoorOpen) continue; label = s.inventory.some((i) => i?.type === 'electricalKey') ? 'Unlock electrical room' : 'Locked · Electrical Room Key needed'; break;
          case 'breakerBox': label = 'Breaker box'; break;
          case 'shop': label = "Browse Jeff's shop"; break;
          case 'elevator': label = w.powerOn ? 'Elevator' : 'No power'; break;
          default: continue;
        }
        consider({ id: it.id, label, pos: it.pos, kind: it.kind, it }, it.kind === 'hide' ? 2.8 : it.kind === 'cabinetDoor' ? 2.8 : PLAYER.interactRange);
      }
    }
    for (const [id, d] of w.dropped) consider({ id, label: `Pick up ${ITEMS[d.item.type].name}`, pos: d.pos, kind: 'drop' });
    this.target = best;
    const tt = this.target as Target | null;
    this.ui.prompt(tt ? tt.label : null);
  }

  private updateHudExtras() {
    if (this.room === LIBRARY_ROOM) this.ui.library(this.paper, this.layout.rooms[LIBRARY_ROOM].puzzle?.shapes ?? [], this.books, this.booksSearched);
  }

  private updateAmbience(dt: number) {
    this.ambientT += dt;
    if (this.ambientT < 0.5) return;
    this.ambientT = 0;
    const room = this.layout.rooms[this.room];
    if (!room) return;
    const outdoor = room.kind === 'courtyard';
    const glass = room.kind === 'greenhouse';
    this.audio.loop('rain', outdoor ? 0.9 : glass ? 0.7 : this.room > 85 ? 0.15 : 0);
    this.audio.loop('wind', outdoor ? 0.4 : 0);
    this.audio.loop('electric', room.kind === 'electrical' ? 0.5 : room.kind === 'basement' || room.kind === 'storage' ? 0.15 : 0);
    this.audio.loop('fire', room.kind === 'seekFinale' || room.props.some((p) => p.type === 'fireplace') ? 0.35 : 0);
    this.audio.loop('musicbox', room.kind === 'shop' ? 0.5 : 0);
    this.audio.loop('roomtone', outdoor ? 0 : 0.45);
    if (Math.random() < 0.04) this.audio.play('creak', { pos: { x: this.pos.x + (Math.random() - 0.5) * 12, y: this.pos.y + 2, z: this.pos.z + (Math.random() - 0.5) * 12 }, vol: 0.25 });
    if (room.tags.includes('drip') && Math.random() < 0.3) this.audio.play('drip', { pos: { x: this.pos.x + (Math.random() - 0.5) * 6, y: this.pos.y + 2.5, z: this.pos.z + (Math.random() - 0.5) * 6 }, vol: 0.4 });
    if ((outdoor || glass) && Math.random() < 0.015) { this.audio.play('thunder', { vol: 0.7 }); this.distortFx = 0.05; }
  }

  private spectateCam(dt: number) {
    const alive = [...this.remotes.values()].filter((r) => r.last && !r.last.dead && r.last.connected);
    const cam = this.r.camera;
    if (this.deathT < 1.6) {
      // death fall
      cam.position.y = Math.max(this.pos.y + 0.3, cam.position.y - dt * 1.5);
      cam.rotation.z = Math.min(0.9, cam.rotation.z + dt * 0.6);
      return;
    }
    if (!alive.length) return;
    const t = alive[this.spectate % alive.length];
    const s = t.last!;
    const p = t.av.root.position;
    cam.position.set(p.x, p.y + (s.crouch ? 0.95 : 1.58), p.z);
    cam.rotation.set(s.pitch, s.yaw, 0, 'YXZ');
    this.room = s.room >= 0 ? s.room : this.room;
  }
}
