import * as THREE from 'three';
import type { Interactable, LightData, PropData, RoomData, RunLayout } from '@doors/shared';
import { DOOR_H, DOOR_W, WALL_T, toLocal } from '@doors/shared';
import { MeshBatch, box, cyl, sphere } from '../assets/geom';
import { M } from '../assets/materials';
import { buildShell, skyTexture } from '../assets/shell';
import { buildProp, SKIP_PROPS, type PropCtx } from '../assets/props';
import { buildDoor } from '../assets/doors';
import { itemMesh } from '../assets/items';
import { canvasTex, radialSprite } from '../assets/textures';
import { makeAvatar, makeGuide, type Creature } from '../assets/monsters';

export type PartKind = 'door' | 'drawer' | 'closetL' | 'closetR' | 'gate' | 'lever' | 'elevatorDoors' | 'chandelier' | 'fakeDoor' | 'hvDoor';

interface AnimPart { obj: THREE.Object3D; kind: PartKind; target: number; cur: number; speed: number; side: number }

let eyeTexCache: THREE.CanvasTexture | null = null;
function seekEyeTex() {
  if (eyeTexCache) return eyeTexCache;
  eyeTexCache = canvasTex(128, 64, (ctx) => {
    ctx.fillStyle = 'rgba(0,0,0,0)'; ctx.clearRect(0, 0, 128, 64);
    ctx.fillStyle = '#060606'; ctx.beginPath(); ctx.ellipse(64, 32, 62, 30, 0, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = '#e8e4dc'; ctx.beginPath(); ctx.ellipse(64, 32, 44, 20, 0, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = '#000'; ctx.beginPath(); ctx.arc(64, 32, 13, 0, Math.PI * 2); ctx.fill();
  });
  eyeTexCache.userData.shared = true;
  return eyeTexCache;
}

/**
 * All meshes for one room: merged static shell + furniture, animated parts (doors, drawers, closets),
 * pickups, decorative animated effects and light fixtures.
 */
export class RoomView {
  group = new THREE.Group();
  parts = new Map<string, AnimPart>();
  pickups = new Map<string, THREE.Object3D>();
  drawerLoot = new Map<string, THREE.Object3D>();
  fixtures: THREE.Mesh[] = [];
  lights: LightData[];
  creatures: Creature[] = [];
  updaters: ((dt: number, t: number, cam: THREE.Camera) => void)[] = [];
  private disposables: (THREE.BufferGeometry | THREE.Material | THREE.Texture)[] = [];
  lightMode: 'on' | 'flicker' | 'broken' | 'green' | 'red' | 'off' = 'on';
  bookGlow: THREE.Object3D[] = [];
  handPositions: THREE.Vector3[] = [];

  constructor(readonly room: RoomData, layout: RunLayout, quality: number) {
    const g = this.group;
    g.position.set(room.origin.x, room.origin.y, room.origin.z);
    g.rotation.y = (room.rot * Math.PI) / 2;
    g.updateMatrixWorld(true);
    this.lights = room.dark ? room.lights.filter((l) => l.kind === 'emergency' || l.kind === 'guide' || l.kind === 'fire' || l.kind === 'moon') : room.lights;
    if (room.dark) this.lightMode = 'off';

    const batch = new MeshBatch();
    buildShell(room, batch, quality);
    const wood = M.wood(room.theme.woodTone);
    for (const p of room.props) {
      if (SKIP_PROPS.has(p.type)) { this.special(p, quality); continue; }
      const m = new THREE.Matrix4().compose(new THREE.Vector3(p.x, p.y, p.z), new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), (p.rot * Math.PI) / 2), new THREE.Vector3(1, 1, 1));
      const holder = new THREE.Group();
      holder.applyMatrix4(m);
      const ctx: PropCtx = {
        p, room, wood, quality,
        add: (mat, geo) => batch.add(mat, geo, m),
        dyn: (o) => { holder.add(o); return o; },
        interactables: room.interactables,
        part: (key, o) => this.registerPart(key, o, p),
        fixture: (o) => this.fixtures.push(o as THREE.Mesh),
      };
      buildProp(ctx);
      if (holder.children.length) g.add(holder);
    }
    const statics = batch.build(quality > 0, true);
    statics.traverse((o) => { if ((o as THREE.Mesh).isMesh) this.disposables.push((o as THREE.Mesh).geometry); });
    g.add(statics);
    if (room.theme.outdoor) {
      const sky = new THREE.Mesh(new THREE.PlaneGeometry(200, 200), new THREE.MeshBasicMaterial({ map: skyTexture(), fog: false, side: THREE.DoubleSide }));
      sky.rotation.x = Math.PI / 2;
      sky.position.set((room.xMin + room.xMax) / 2, room.height + 6, WALL_T + room.depth / 2);
      g.add(sky);
    }

    // entry door (door number = room index); the reception's entry is the elevator
    if (room.index > 0) {
      const prev = layout.rooms[room.index - 1];
      const ex = prev.exits[prev.realExit];
      const { root, pivots } = buildDoor(room.index, ex.style, prev.theme.woodTone, { locked: ex.lock === 'key' || ex.lock === 'code' });
      g.add(root);
      pivots.forEach((pv, i) => this.parts.set(i === 0 ? 'door' : `door${i}`, { obj: pv, kind: ex.style === 'gate' ? 'gate' : 'door', target: 0, cur: 0, speed: ex.style === 'gate' ? 1.2 : 2.2, side: pv.userData.side ?? -1 }));
    }
    // fake doors on this room's walls
    room.exits.forEach((e, k) => {
      if (e.real) return;
      const { root, pivots } = buildDoor(e.number, e.style, room.theme.woodTone, { fake: true });
      root.position.set(e.lx, e.ly, e.lz);
      root.rotation.y = (e.dir * Math.PI) / 2;
      // backing so the doorway never shows the void
      const back = new THREE.Mesh(box(DOOR_W + 0.2, DOOR_H + 0.1, 0.05, 0, (DOOR_H + 0.1) / 2, WALL_T * 2 + 0.1), M.black());
      root.add(back);
      if (e.fake === 'blocked') {
        for (let b = 0; b < 3; b++) root.add(new THREE.Mesh(box(DOOR_W + 0.3, 0.14, 0.04, 0, 0.6 + b * 0.6, -WALL_T - 0.06).rotateZ((b - 1) * 0.15), M.woodPlank()));
      }
      g.add(root);
      this.parts.set(`fdoor:${room.index}:${k}`, { obj: pivots[0], kind: 'fakeDoor', target: 0, cur: 0, speed: 3, side: pivots[0].userData.side ?? -1 });
    });

    this.buildPickups();
    if (quality >= 2) this.buildShafts();
  }

  private registerPart(key: string, o: THREE.Object3D, p: PropData) {
    if (key.endsWith(':doorL') || key.endsWith(':doorR')) this.parts.set(key, { obj: o, kind: key.endsWith('L') ? 'closetL' : 'closetR', target: 0, cur: 0, speed: 3.5, side: o.userData.side });
    else if (key.endsWith(':chandelier')) this.parts.set(key, { obj: o, kind: 'chandelier', target: 0, cur: 0, speed: 1, side: 0 });
    else if (key.endsWith(':pendulum')) this.updaters.push((_dt, t) => { o.rotation.z = Math.sin(t * 2.2) * 0.12; });
    else if (key.endsWith(':panel')) this.parts.set('breakerPanel', { obj: o, kind: 'lever', target: 0, cur: 0, speed: 1, side: 0 });
    else this.parts.set(key, { obj: o, kind: 'drawer', target: 0, cur: 0, speed: 3, side: 0 });
    void p;
  }

  /** Convert a world position into this room's local frame. */
  local(pos: { x: number; y: number; z: number }) {
    const [x, z] = toLocal(this.room, pos.x, pos.z);
    return new THREE.Vector3(x, pos.y - this.room.origin.y, z);
  }

  private buildPickups() {
    for (const it of this.room.interactables) {
      if (it.kind === 'pickup' && it.loot?.item) {
        const m = itemMesh(it.loot.item);
        m.position.copy(this.local(it.pos));
        m.rotation.y = (it.id.length * 1.7) % 6.28;
        if (it.loot.item === 'hintPaper') m.rotation.y = 0.3;
        this.group.add(m);
        this.pickups.set(it.id, m);
        // subtle glint so small items read in the dark
        const glint = new THREE.Sprite(new THREE.SpriteMaterial({ map: radialSprite('rgba(255,230,170,0.5)'), blending: THREE.AdditiveBlending, depthWrite: false, transparent: true }));
        glint.scale.setScalar(0.25);
        m.add(glint);
      } else if (it.kind === 'book') {
        const m = itemMesh('book');
        const lp = this.local(it.pos);
        m.position.copy(lp);
        m.rotation.set(Math.PI / 2, (this.room.rot * 0) + (this.findProp(it.propId)?.rot ?? 0) * Math.PI / 2, 0);
        const glow = new THREE.Sprite(new THREE.SpriteMaterial({ map: radialSprite('rgba(160,255,190,0.55)'), blending: THREE.AdditiveBlending, depthWrite: false, transparent: true }));
        glow.scale.setScalar(0.5);
        m.add(glow);
        this.bookGlow.push(glow);
        this.group.add(m);
        this.pickups.set(it.id, m);
      } else if (it.kind === 'drawer' && it.loot && (it.loot.gold || it.loot.item)) {
        const part = this.parts.get(it.id);
        if (!part) continue;
        const m = itemMesh(it.loot.gold ? 'gold' : it.loot.item!);
        m.position.set(0, -0.06, -0.15);
        m.scale.setScalar(it.loot.gold ? 1 : 0.9);
        part.obj.add(m);
        this.drawerLoot.set(it.id, m);
      }
    }
  }

  private findProp(id?: string) { return id ? this.room.props.find((p) => p.id === id) : undefined; }

  /** Soft additive light cones under lamps for a volumetric look (high quality only). */
  private buildShafts() {
    const tex = canvasTex(64, 128, (ctx) => {
      const g = ctx.createLinearGradient(0, 0, 0, 128);
      g.addColorStop(0, 'rgba(255,220,160,0.32)'); g.addColorStop(1, 'rgba(255,220,160,0)');
      ctx.fillStyle = g; ctx.fillRect(0, 0, 64, 128);
    });
    this.disposables.push(tex);
    for (const l of this.lights) {
      if (l.kind !== 'ceiling' && l.kind !== 'chandelier' && l.kind !== 'lamp') continue;
      if (!this.room.props.some((p) => Math.hypot(p.x - l.lx, p.z - l.lz) < 0.6 && (p.type === 'ceilingLight' || p.type === 'chandelier' || p.type === 'lamp' || p.type === 'floorLamp'))) continue;
      const h = l.kind === 'lamp' ? 0.9 : Math.min(2.6, l.ly - 0.2);
      const r = l.kind === 'lamp' ? 0.35 : 1.3;
      const geo = new THREE.CylinderGeometry(0.12, r, h, 18, 1, true);
      geo.translate(0, -h / 2, 0);
      const mat = new THREE.MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide, color: l.color, opacity: 0.22, fog: true });
      const m = new THREE.Mesh(geo, mat);
      m.position.set(l.lx, l.ly - (l.kind === 'lamp' ? -0.05 : 0.05), l.lz);
      m.userData.shaft = true;
      this.group.add(m);
      this.disposables.push(geo, mat);
      this.shafts.push(m);
    }
  }
  shafts: THREE.Mesh[] = [];

  /** Props needing bespoke visuals/animation. */
  private special(p: PropData, quality: number) {
    const holder = new THREE.Group();
    holder.position.set(p.x, p.y, p.z);
    holder.rotation.y = (p.rot * Math.PI) / 2;
    this.group.add(holder);
    switch (p.type) {
      case 'elevator': {
        const metal = M.steel();
        const cabin = new THREE.Group();
        const sx = p.variant === 1 ? 1 : 1;
        // cabin behind the entry plane (local -z) for the reception, or into the alcove for the finale
        const dz = -1.5;
        cabin.add(new THREE.Mesh(box(3.0, 0.05, 3.0, 0, 0.0, dz), M.floor('carpet', 0x3a1414)));
        cabin.add(new THREE.Mesh(box(3.0, 2.9, 0.05, 0, 1.45, dz - 1.5), M.woodDark()));
        for (const s of [-1, 1]) cabin.add(new THREE.Mesh(box(0.05, 2.9, 3.0, s * 1.5, 1.45, dz), M.woodDark()));
        cabin.add(new THREE.Mesh(box(3.0, 0.05, 3.0, 0, 2.8, dz), M.brass()));
        cabin.add(new THREE.Mesh(box(2.8, 0.06, 0.06, 0, 1.0, dz - 1.45), M.brass()));
        cabin.add(new THREE.Mesh(box(0.2, 0.4, 0.03, 1.2, 1.3, -0.1), metal));
        const lamp = new THREE.Mesh(sphere(0.08, 0, 2.7, dz), M.bulb(true, 0xffe0b0));
        cabin.add(lamp);
        this.fixtures.push(lamp);
        holder.add(cabin);
        const doors = new THREE.Group();
        for (const s of [-1, 1]) {
          const d = new THREE.Mesh(box(0.66, 2.3, 0.04, s * 0.33, 1.15, 0.02), M.brass());
          d.userData.side = s;
          doors.add(d);
        }
        holder.add(doors);
        this.parts.set('elevatorDoors', { obj: doors, kind: 'elevatorDoors', target: 0, cur: 0, speed: 0.6, side: 0 });
        void sx;
        break;
      }
      case 'gate': {
        const g = new THREE.Group();
        const W = p.sx ?? 3.2;
        for (let k = 0; k <= 12; k++) g.add(new THREE.Mesh(box(0.04, 2.9, 0.04, -W / 2 + k * (W / 12), 1.45, 0), M.iron()));
        for (const y of [0.2, 1.45, 2.8]) g.add(new THREE.Mesh(box(W, 0.06, 0.05, 0, y, 0), M.iron()));
        holder.add(g);
        this.parts.set('powerGate', { obj: g, kind: 'gate', target: 0, cur: 0, speed: 0.5, side: 0 });
        break;
      }
      case 'lever': {
        holder.add(new THREE.Mesh(box(0.3, 0.45, 0.06, 0, 0, 0.03), M.iron()));
        const arm = new THREE.Group();
        arm.position.set(0, 0, 0.08);
        arm.add(new THREE.Mesh(box(0.04, 0.35, 0.04, 0, 0.17, 0), M.steel()));
        arm.add(new THREE.Mesh(sphere(0.05, 0, 0.36, 0), M.red()));
        arm.rotation.x = -0.6;
        holder.add(arm);
        const it = this.room.interactables.find((i) => i.kind === 'lever');
        if (it) this.parts.set(it.id, { obj: arm, kind: 'lever', target: 0, cur: 0, speed: 2, side: 0 });
        break;
      }
      case 'hands': {
        const mat = M.goo();
        const arms: THREE.Group[] = [];
        for (let k = 0; k < 2; k++) {
          const arm = new THREE.Group();
          arm.position.set((k - 0.5) * 0.5, 0.9 + k * 0.5, 0);
          arm.add(new THREE.Mesh(cyl(0.05, 0.07, 0.7, 8).rotateX(Math.PI / 2), mat));
          for (let f = 0; f < 4; f++) arm.add(new THREE.Mesh(cyl(0.012, 0.009, 0.16, 5).rotateX(Math.PI / 2 + (f - 1.5) * 0.25).translate((f - 1.5) * 0.025, 0, 0.75), mat));
          holder.add(arm);
          arms.push(arm);
        }
        const wp = new THREE.Vector3();
        holder.getWorldPosition(wp);
        this.handPositions.push(wp);
        this.updaters.push((_dt, t, cam) => {
          const d = cam.position.distanceTo(holder.getWorldPosition(wp));
          arms.forEach((a, k) => {
            const reach = d < 3 ? 0.45 : 0.1;
            a.position.z = reach + Math.sin(t * 6 + k * 2) * 0.08;
            a.rotation.z = Math.sin(t * 3 + k) * 0.2;
          });
        });
        break;
      }
      case 'seekEyes': {
        const m = new THREE.Mesh(new THREE.PlaneGeometry(0.34, 0.17), new THREE.MeshBasicMaterial({ map: seekEyeTex(), transparent: true, depthWrite: false }));
        m.position.z = 0.012;
        holder.add(m);
        const phase = (p.id.length * 13 + p.x * 7) % 10;
        this.updaters.push((_dt, t) => {
          const b = Math.sin(t * 0.7 + phase);
          m.scale.y = b > 0.96 ? 0.1 : 1;
        });
        break;
      }
      case 'fire': {
        const tex = radialSprite('rgba(255,170,60,0.95)', 'rgba(255,60,0,0)');
        const flames: THREE.Sprite[] = [];
        for (let k = 0; k < 7; k++) {
          const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, blending: THREE.AdditiveBlending, depthWrite: false, transparent: true }));
          holder.add(s);
          flames.push(s);
        }
        holder.add(new THREE.Mesh(new THREE.CircleGeometry(0.7, 12).rotateX(-Math.PI / 2).translate(0, 0.01, 0), M.black()));
        this.updaters.push((dt, t) => {
          flames.forEach((s, k) => {
            const ph = (t * 1.6 + k / 7) % 1;
            s.position.set(Math.sin(k * 2.1 + t) * 0.3 * (1 - ph), 0.2 + ph * 1.4, Math.cos(k * 1.7 + t) * 0.3 * (1 - ph));
            s.scale.setScalar((1 - ph) * 0.9 + 0.2);
            (s.material as THREE.SpriteMaterial).opacity = 1 - ph;
          });
          void dt;
        });
        break;
      }
      case 'guidingLight': {
        const s = makeGuide();
        s.position.set(0, 1.4, 0.3);
        holder.add(s);
        this.updaters.push((_dt, t) => { s.position.y = 1.4 + Math.sin(t * 2) * 0.1; });
        break;
      }
      case 'jeff': {
        const jeff = makeAvatar(0x6a1a1a, { jeff: true });
        holder.add(jeff.root);
        this.creatures.push(jeff);
        this.updaters.push((dt, _t, cam) => jeff.update(dt, 'idle-shop', 0, cam));
        break;
      }
      case 'snare': {
        const mat = M.rust();
        for (let k = 0; k < 10; k++) {
          const a = (k / 10) * Math.PI * 2;
          const c = new THREE.Mesh(new THREE.ConeGeometry(0.03, 0.16, 4).translate(0, 0.08, 0), mat);
          c.position.set(Math.cos(a) * 0.22, 0, Math.sin(a) * 0.22);
          c.rotation.set(Math.sin(a) * 0.5, 0, -Math.cos(a) * 0.5);
          holder.add(c);
        }
        holder.add(new THREE.Mesh(new THREE.TorusGeometry(0.24, 0.02, 4, 16).rotateX(Math.PI / 2), mat));
        holder.add(new THREE.Mesh(new THREE.CircleGeometry(0.2, 10).rotateX(-Math.PI / 2).translate(0, 0.005, 0), M.dirt()));
        break;
      }
      case 'pipe': {
        const len = p.sx ?? 6;
        const m = new THREE.Mesh(cyl(0.06, 0.06, len, 10).rotateX(Math.PI / 2).translate(0, 0, 0), M.rust());
        m.position.y = 0;
        holder.add(m);
        for (let k = -len / 2 + 0.5; k < len / 2; k += 1.5) holder.add(new THREE.Mesh(box(0.2, 0.05, 0.05, 0, 0.08, k), M.iron()));
        break;
      }
      case 'cables': {
        const w = p.sx ?? 10, d = p.sz ?? 10;
        for (let k = 0; k < 5; k++) {
          const pts = [new THREE.Vector3(-w / 2 + k * w / 5, 7.5, -d / 2), new THREE.Vector3(-w / 2 + k * w / 5 + 1, 6.8, 0), new THREE.Vector3(-w / 2 + k * w / 5, 7.5, d / 2)];
          holder.add(new THREE.Mesh(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts), 16, 0.03, 4), M.rubber()));
        }
        break;
      }
      case 'curtain': {
        const m = new THREE.Mesh(new THREE.PlaneGeometry(2.0, 2.2, 20, 1), M.fabric(0xa8b8b0));
        const pos = m.geometry.attributes.position as THREE.BufferAttribute;
        for (let i = 0; i < pos.count; i++) pos.setZ(i, Math.sin(pos.getX(i) * 9) * 0.06);
        m.geometry.computeVertexNormals();
        m.rotation.y = Math.PI / 2;
        m.position.y = 1.2;
        holder.add(m);
        break;
      }
      default: break;
    }
    void quality;
  }

  setPart(key: string, target: number) {
    const p = this.parts.get(key);
    if (p) p.target = target;
  }
  snapPart(key: string, v: number) {
    const p = this.parts.get(key);
    if (p) { p.target = v; p.cur = v; this.applyPart(p); }
  }

  private applyPart(p: AnimPart) {
    const e = p.cur < 0.5 ? 2 * p.cur * p.cur : 1 - Math.pow(-2 * p.cur + 2, 2) / 2;
    switch (p.kind) {
      case 'door': case 'fakeDoor': p.obj.rotation.y = p.side * e * (p.kind === 'fakeDoor' ? 0.45 : 1.75); break;
      case 'closetL': case 'closetR': p.obj.rotation.y = p.side * e * 1.7; break;
      case 'gate': p.obj.position.y = e * 2.6; break;
      case 'drawer': p.obj.position.z = p.obj.userData.closedZ + (p.obj.userData.openZ - p.obj.userData.closedZ) * e; break;
      case 'lever': p.obj.rotation.x = -0.6 + e * 1.2; break;
      case 'elevatorDoors': p.obj.children.forEach((c) => (c.position.x = c.userData.side * (0.33 + e * 0.62))); break;
      case 'chandelier': {
        p.obj.position.y = -e * e * 4.2;
        p.obj.rotation.z = e * 0.6;
        break;
      }
    }
  }

  update(dt: number, t: number, cam: THREE.Camera) {
    for (const p of this.parts.values()) {
      if (p.cur === p.target) continue;
      const d = p.target - p.cur;
      const step = Math.sign(d) * Math.min(Math.abs(d), dt * p.speed);
      p.cur += step;
      this.applyPart(p);
    }
    for (const u of this.updaters) u(dt, t, cam);
    if (this.bookGlow.length) for (const g of this.bookGlow) g.scale.setScalar(0.45 + Math.sin(t * 3) * 0.08);
  }

  setFixtures(on: boolean, color?: number) {
    for (const f of this.fixtures) f.material = M.bulb(on, color ?? 0xffd9a0);
    for (const s of this.shafts) s.visible = on;
  }

  dispose() {
    this.group.removeFromParent();
    this.group.traverse((o) => {
      const m = o as THREE.Mesh;
      if (m.geometry) m.geometry.dispose();
      const mat = m.material as THREE.Material | THREE.Material[] | undefined;
      // shared library materials are cached; only dispose per-room ones (those with canvas maps)
      if (mat && !Array.isArray(mat) && !mat.name) {
        const map = (mat as THREE.MeshStandardMaterial).map;
        // painting/plate/sign textures are unique per room; shared sprites are flagged
        if (map && !map.userData.shared) map.dispose();
        mat.dispose();
      }
    });
    for (const d of this.disposables) d.dispose();
  }

  interactPoint(it: Interactable) { return it.pos; }
}
