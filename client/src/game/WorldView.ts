import * as THREE from 'three';
import type { Box, GameEvent, Interactable, LightData, RunLayout, Vec3, WorldSync } from '@doors/shared';
import { DOOR_W, FINAL_ROOM, WALL_T, rotXZ, tagValue, toWorld } from '@doors/shared';
import { RoomView } from './RoomView';
import type { Physics } from '../physics/Physics';
import { itemMesh } from '../assets/items';
import type { InventoryItem } from '@doors/shared';

/** Client-side mirror of the shared world: which rooms are built, door/container/light state, pickups. */
export class WorldView {
  views = new Map<number, RoomView>();
  readonly layout: RunLayout;
  doorsOpen = new Set<number>();
  doorsUnlocked = new Set<number>();
  containersOpen = new Set<string>();
  emptied = new Set<string>();
  taken = new Set<string>();
  levers = new Set<string>();
  fakeOpen = new Set<string>();
  broken = new Set<number>();
  modes = new Map<number, 'flicker' | 'green' | 'red'>();
  hideOcc = new Map<string, string>();
  books = new Set<string>();
  fallen = new Set<string>();
  hvDoorOpen = false;
  powerOn = false;
  furthest = 0;
  dropped = new Map<string, { item: InventoryItem; pos: Vec3; obj: THREE.Object3D }>();
  private lightPool: THREE.PointLight[] = [];
  private t = 0;
  private flickerUntil = new Map<number, number>();
  private interactIndex = new Map<string, { room: number; it: Interactable }>();

  constructor(layout: RunLayout, private scene: THREE.Scene, private physics: Physics, private quality: number, poolSize: number, shadowLights: number, shadowSize: number) {
    this.layout = layout;
    for (const r of layout.rooms) for (const it of r.interactables) this.interactIndex.set(it.id, { room: r.index, it });
    for (let i = 0; i < poolSize; i++) {
      const l = new THREE.PointLight(0xffd0a0, 0, 10, 2);
      if (i < shadowLights) {
        l.castShadow = true;
        l.shadow.mapSize.set(shadowSize, shadowSize);
        l.shadow.bias = -0.002;
        l.shadow.normalBias = 0.03;
        l.shadow.camera.near = 0.15;
        l.shadow.camera.far = 14;
      }
      scene.add(l);
      this.lightPool.push(l);
    }
  }

  interactable(id: string) { return this.interactIndex.get(id); }

  /** Load rooms around the player and the furthest opened door; unload far ones. */
  stream(playerRoom: number) {
    const lo = Math.max(0, playerRoom - 2);
    const hi = Math.min(FINAL_ROOM, Math.max(playerRoom + 1, this.furthest + 1));
    for (let i = lo; i <= hi; i++) if (!this.views.has(i)) this.load(i);
    for (const [i, v] of this.views) if (i < lo - 1 || i > hi + 1) { v.dispose(); this.views.delete(i); this.physics.removeRoom(i); this.physics.setBlocker(`door${i}`, null); this.physics.setBlocker(`room${i}fakes`, null); }
  }

  private load(i: number) {
    const room = this.layout.rooms[i];
    const v = new RoomView(room, this.layout, this.quality);
    this.scene.add(v.group);
    this.views.set(i, v);
    this.physics.addRoom(room);
    // apply state
    if (i > 0) {
      const open = this.doorsOpen.has(i);
      v.snapPart('door', open ? 1 : 0); v.snapPart('door1', open ? 1 : 0);
      this.updateDoorBlocker(i);
    } else v.snapPart('elevatorDoors', 1);
    room.exits.forEach((e, k) => {
      if (e.real) return;
      const box = this.doorBox(e.world, e.worldRot);
      this.physics.setBlocker(`fake${i}:${k}`, box);
    });
    for (const [id] of v.parts) {
      if (this.containersOpen.has(id)) v.snapPart(id, 1);
      if (this.levers.has(id)) v.snapPart(id, 1);
      if (this.fallen.has(id.replace(':chandelier', '')) && id.endsWith(':chandelier')) v.snapPart(id, 1);
    }
    for (const [id, m] of v.pickups) if (this.taken.has(id) || this.books.has(id)) m.visible = false;
    for (const [id, m] of v.drawerLoot) if (this.emptied.has(id)) m.visible = false;
    for (const [hid, pid] of this.hideOcc) this.setHideVisual(hid, !!pid, true);
    if (i === FINAL_ROOM) {
      v.snapPart('powerGate', this.powerOn ? 1 : 0);
      this.updateFinalBlockers();
    }
    this.applyRoomLights(i);
  }

  /** Thin box filling a doorway at a joint plane. */
  doorBox(world: Vec3, rot: number, w = DOOR_W): Box {
    const [ax, az] = rotXZ(w / 2, 0, rot);
    const [bx, bz] = rotXZ(0, 0.06, rot);
    const hx = Math.abs(ax) + Math.abs(bx), hz = Math.abs(az) + Math.abs(bz);
    return { minX: world.x - hx, maxX: world.x + hx, minY: world.y, maxY: world.y + 2.4, minZ: world.z - hz, maxZ: world.z + hz };
  }

  private updateDoorBlocker(n: number) {
    const r = this.layout.rooms[n];
    const prev = this.layout.rooms[n - 1];
    const ex = prev?.exits[prev.realExit];
    if (this.doorsOpen.has(n)) this.physics.setBlocker(`door${n}`, null);
    else this.physics.setBlocker(`door${n}`, this.doorBox(r.origin, r.rot, ex?.style === 'double' ? 2.2 : DOOR_W));
  }

  private updateFinalBlockers() {
    const r = this.layout.rooms[FINAL_ROOM];
    const hv = r.interactables.find((i) => i.kind === 'cabinetDoor');
    if (hv?.exitPos) {
      const p = hv.exitPos;
      this.physics.setBlocker('hvdoor', this.hvDoorOpen ? null : this.doorBox(p, (r.rot + 1) & 3, 1.4));
    }
    const g = tagValue(r, 'powerGate');
    if (g) {
      const w = toWorld(r, g[0], 0, g[1]);
      this.physics.setBlocker('powergate', this.powerOn ? null : this.doorBox(w, r.rot, 3.2));
    }
  }

  applySync(w: WorldSync) {
    this.doorsOpen = new Set(w.openDoors);
    this.doorsUnlocked = new Set(w.unlockedDoors);
    this.containersOpen = new Set(w.openContainers);
    this.emptied = new Set(w.emptiedContainers);
    this.taken = new Set(w.takenPickups);
    this.levers = new Set(w.usedLevers);
    this.fakeOpen = new Set(w.openFakeDoors);
    this.broken = new Set(w.brokenLights);
    this.hideOcc = new Map(Object.entries(w.hideOccupants));
    this.books = new Set(w.bookIds);
    this.fallen = new Set(w.fallenChandeliers);
    this.hvDoorOpen = w.hvDoorOpen;
    this.powerOn = w.powerOn;
    this.furthest = w.furthestDoor;
    for (const d of w.droppedItems) this.addDropped(d.id, d.item, d.pos);
    // rebuild loaded rooms with the new state
    for (const [i, v] of this.views) { v.dispose(); this.views.delete(i); this.physics.removeRoom(i); }
  }

  addDropped(id: string, item: InventoryItem, pos: Vec3) {
    if (this.dropped.has(id)) return;
    const obj = itemMesh(item.type);
    obj.position.set(pos.x, pos.y + 0.03, pos.z);
    this.scene.add(obj);
    this.dropped.set(id, { item, pos, obj });
  }

  setHideVisual(id: string, inside: boolean, snap = false) {
    const ref = this.interactIndex.get(id);
    if (!ref || !ref.it.propId) return;
    const v = this.views.get(ref.room);
    if (!v) return;
    for (const k of [`${ref.it.propId}:doorL`, `${ref.it.propId}:doorR`]) {
      // doors swing open briefly while entering/leaving, then close
      if (snap) v.snapPart(k, 0);
      else { v.setPart(k, 1); setTimeout(() => v.setPart(k, 0), 450); }
    }
    void inside;
  }

  onEvent(e: GameEvent) {
    switch (e.e) {
      case 'door':
        this.doorsOpen.add(e.n);
        this.furthest = Math.max(this.furthest, e.n);
        this.views.get(e.n)?.setPart('door', 1);
        this.views.get(e.n)?.setPart('door1', 1);
        this.updateDoorBlocker(e.n);
        break;
      case 'doorSlam':
        this.doorsOpen.delete(e.n);
        this.views.get(e.n)?.setPart('door', 0);
        this.views.get(e.n)?.setPart('door1', 0);
        this.updateDoorBlocker(e.n);
        break;
      case 'doorUnlock': this.doorsUnlocked.add(e.n); break;
      case 'fakeDoor': {
        this.fakeOpen.add(e.id);
        const room = Number(e.id.split(':')[1]);
        const v = this.views.get(room);
        v?.setPart(e.id, 1);
        setTimeout(() => v?.setPart(e.id, 0), 1600);
        break;
      }
      case 'container': {
        if (e.open) this.containersOpen.add(e.id); else this.containersOpen.delete(e.id);
        const ref = this.interactIndex.get(e.id);
        if (ref) this.views.get(ref.room)?.setPart(e.id, e.open ? 1 : 0);
        break;
      }
      case 'loot': {
        this.emptied.add(e.id);
        const ref = this.interactIndex.get(e.id);
        const m = ref && this.views.get(ref.room)?.drawerLoot.get(e.id);
        if (m) setTimeout(() => (m.visible = false), 250);
        break;
      }
      case 'scare': if (e.kind === 'timothy' && e.id) this.emptied.add(e.id); break;
      case 'pickup': {
        this.taken.add(e.id);
        if (e.id.startsWith('drop:')) { const d = this.dropped.get(e.id); if (d) { d.obj.removeFromParent(); this.dropped.delete(e.id); } break; }
        const ref = this.interactIndex.get(e.id);
        const m = ref && this.views.get(ref.room)?.pickups.get(e.id);
        if (m) m.visible = false;
        break;
      }
      case 'book': {
        this.books.add(e.id);
        const ref = this.interactIndex.get(e.id);
        const m = ref && this.views.get(ref.room)?.pickups.get(e.id);
        if (m) m.visible = false;
        break;
      }
      case 'drop': this.addDropped(e.id, e.item, e.pos); break;
      case 'lever': {
        this.levers.add(e.id);
        const ref = this.interactIndex.get(e.id);
        if (ref) this.views.get(ref.room)?.setPart(e.id, 1);
        break;
      }
      case 'hide':
        if (e.inside) this.hideOcc.set(e.id, e.by); else this.hideOcc.delete(e.id);
        this.setHideVisual(e.id, e.inside);
        break;
      case 'lights':
        for (const r of e.rooms) {
          if (e.mode === 'broken' || e.mode === 'off') { this.broken.add(r); this.modes.delete(r); this.flickerUntil.set(r, this.t + 0.6); }
          else if (e.mode === 'on') { this.broken.delete(r); this.modes.delete(r); }
          else this.modes.set(r, e.mode);
          this.applyRoomLights(r);
        }
        break;
      case 'hvDoor': this.hvDoorOpen = true; this.updateFinalBlockers(); break;
      case 'power':
        this.powerOn = true;
        this.views.get(FINAL_ROOM)?.setPart('powerGate', 1);
        this.updateFinalBlockers();
        break;
      case 'chandelier': {
        this.fallen.add(e.id);
        for (const v of this.views.values()) v.setPart(`${e.id}:chandelier`, 1);
        break;
      }
      case 'entity':
        if ((e.kind === 'rush' || e.kind === 'ambush') && e.phase === 'gone') for (const r of [e.room! - 1, e.room!]) if (this.modes.get(r)) { this.modes.delete(r); this.applyRoomLights(r); }
        break;
    }
  }

  private applyRoomLights(i: number) {
    const v = this.views.get(i);
    if (!v) return;
    const room = this.layout.rooms[i];
    const off = room.dark || this.broken.has(i);
    const mode = this.modes.get(i);
    v.setFixtures(!off, mode === 'green' ? 0x60ff70 : room.theme.lightColor);
  }

  /** Assign pooled point lights to the most relevant fixtures near the camera; handle flicker. */
  update(dt: number, cam: THREE.Camera, playerRoom: number) {
    this.t += dt;
    for (const v of this.views.values()) v.update(dt, this.t, cam);
    const cands: { l: LightData; d: number; room: number }[] = [];
    const cp = cam.position;
    for (const [i, v] of this.views) {
      if (Math.abs(i - playerRoom) > 2) continue;
      const brokenRoom = this.broken.has(i);
      for (const l of v.lights) {
        if (brokenRoom && l.kind !== 'emergency' && l.kind !== 'fire' && l.kind !== 'guide' && l.kind !== 'moon') continue;
        if (l.intensity <= 0 && !(i === FINAL_ROOM && this.powerOn)) continue;
        const d = Math.hypot(l.world.x - cp.x, l.world.y - cp.y, l.world.z - cp.z) - (i === playerRoom ? 4 : 0) - (l.kind === 'moon' ? 30 : 0);
        cands.push({ l, d, room: i });
      }
    }
    cands.sort((a, b) => a.d - b.d);
    for (let k = 0; k < this.lightPool.length; k++) {
      const pl = this.lightPool[k];
      const c = cands[k];
      if (!c) { pl.intensity = 0; pl.visible = false; continue; }
      pl.visible = true;
      const l = c.l;
      pl.position.set(l.world.x, l.world.y, l.world.z);
      const mode = this.modes.get(c.room);
      let inten = l.intensity * (l.kind === 'chandelier' ? 1.6 : 1.25);
      if (c.room === FINAL_ROOM && this.powerOn && l.kind === 'ceiling') inten = 3.5;
      let color = l.color;
      if (mode === 'flicker') inten *= Math.random() < 0.35 ? 0.05 : 0.7 + Math.random() * 0.6;
      else if (mode === 'green') { color = 0x50ff60; inten *= Math.random() < 0.3 ? 0.1 : 0.9; }
      else if (mode === 'red') color = 0xff3020;
      else if (l.flicker) {
        const s = Math.sin(this.t * 13 + l.lx * 7) + Math.sin(this.t * 31 + l.lz * 3);
        if (s > 1.75) inten *= 0.2;
      }
      if (l.kind === 'fire') inten *= 0.75 + Math.sin(this.t * 17 + l.lx) * 0.15 + Math.random() * 0.15;
      const fu = this.flickerUntil.get(c.room);
      if (fu && fu > this.t) inten *= Math.random();
      pl.color.setHex(color);
      pl.intensity = inten;
      pl.distance = l.range * 1.2;
      pl.decay = 1.6;
    }
  }

  /** Entry point of a door for interaction targeting (approach side). */
  doorPoint(n: number): Vec3 {
    const r = this.layout.rooms[n];
    const [ox, oz] = rotXZ(0, -0.15, r.rot);
    return { x: r.origin.x + ox, y: r.origin.y + 1.1, z: r.origin.z + oz };
  }

  dispose() {
    for (const v of this.views.values()) v.dispose();
    this.views.clear();
    for (const l of this.lightPool) l.removeFromParent();
    for (const d of this.dropped.values()) d.obj.removeFromParent();
  }
}

export { WALL_T };
