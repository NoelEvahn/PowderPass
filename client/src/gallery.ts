import * as THREE from 'three';
import { generateRun } from '@doors/shared';
import type { Renderer } from './render/Renderer';
import { RoomView } from './game/RoomView';
import { makeAvatar, makeEyes, makeFigure, makeHalt, makeRush, makeScreech, makeSeek, makeTimothy, type Creature } from './assets/monsters';

/**
 * Offline asset gallery (?gallery=<kind>|monsters): renders a generated room or the monster line-up
 * without a server. Used for visual QA and screenshots.
 */
export function runGallery(r: Renderer, what: string) {
  const scene = r.scene;
  scene.add(new THREE.HemisphereLight(0x8a7a6a, 0x1a1008, 0.4));
  const cam = r.camera;
  const creatures: { c: Creature; state: string; speed: number }[] = [];
  const lights: THREE.PointLight[] = [];
  if (what === 'monsters') {
    const floor = new THREE.Mesh(new THREE.PlaneGeometry(30, 30), new THREE.MeshStandardMaterial({ color: 0x2a1c12, roughness: 0.9 }));
    floor.rotation.x = -Math.PI / 2;
    floor.receiveShadow = true;
    scene.add(floor);
    const line: [Creature, string, number, number][] = [
      [makeFigure(), 'patrol', 1.5, -6], [makeSeek(), 'chase', 6, -3], [makeAvatar(0x6a2a2a), 'walk', 1.2, 0],
      [makeHalt(), 'front', 1, 2.5], [makeRush('rush'), 'run', 0, 5], [makeRush('ambush'), 'run', 0, 8],
    ];
    line.forEach(([c, st, sp, x]) => { c.root.position.set(x, x >= 5 ? 1.6 : 0, 0); scene.add(c.root); creatures.push({ c, state: st, speed: sp }); });
    const sc = makeScreech(); sc.root.position.set(-2.5, 1.6, 2); scene.add(sc.root); creatures.push({ c: sc, state: 'lurk', speed: 0 });
    const ey = makeEyes(); ey.root.position.set(3.5, 1.8, 2.5); scene.add(ey.root); creatures.push({ c: ey, state: 'idle', speed: 0 });
    const tm = makeTimothy(); tm.root.position.set(0.8, 0.1, 2.6); tm.root.scale.setScalar(3); scene.add(tm.root); creatures.push({ c: tm, state: '', speed: 0 });
    for (const [x, z] of [[-4, 4], [4, 4], [0, -3]]) { const l = new THREE.PointLight(0xffd0a0, 25, 20, 1.5); l.position.set(x, 4, z); scene.add(l); }
    cam.position.set(0, 1.7, 9.5);
    cam.lookAt(0, 1.3, 0);
  } else {
    const seed = Number(new URLSearchParams(location.search).get('seed') ?? 7);
    const layout = generateRun(seed);
    const room = what === 'library' ? layout.rooms[50] : what === 'electrical' ? layout.rooms[100] : what === 'reception' ? layout.rooms[0]
      : layout.rooms.find((x) => x.kind === what) ?? layout.rooms[2];
    const v = new RoomView(room, layout, r.preset.level);
    scene.add(v.group);
    for (const l of v.lights.slice(0, 8)) { const pl = new THREE.PointLight(l.color, l.intensity * 5, l.range * 1.6, 1.5); pl.position.set(l.world.x, l.world.y, l.world.z); scene.add(pl); lights.push(pl); }
    const p = room.path[1];
    const far = room.path[room.path.length - 2];
    cam.position.set(p.x, p.y + 1.6, p.z);
    cam.lookAt(far.x, far.y + 1.2, far.z);
    (scene.fog as THREE.FogExp2).density = 0.04;
    setInterval(() => v.update(0.016, performance.now() / 1000, cam), 16);
  }
  let last = performance.now();
  const loop = (now: number) => {
    const dt = Math.min(0.05, (now - last) / 1000); last = now;
    for (const c of creatures) c.c.update(dt, c.state, c.speed, cam);
    r.render(dt);
    requestAnimationFrame(loop);
  };
  requestAnimationFrame(loop);
  (window as unknown as Record<string, unknown>).__galleryReady = true;
}
