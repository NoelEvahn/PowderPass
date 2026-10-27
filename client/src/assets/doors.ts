import * as THREE from 'three';
import type { ExitData } from '@doors/shared';
import { DOOR_H, DOOR_W, WALL_T } from '@doors/shared';
import { box, cyl, sphere } from './geom';
import { M } from './materials';
import { plateTex } from './textures';

/**
 * Hotel door assembly in local coordinates: origin at the joint plane (z=0), door swings toward +z.
 * Returns the root group and the hinged pivot to animate.
 */
export function buildDoor(number: number, style: ExitData['style'], tone: number, opts: { fake?: boolean; locked?: boolean } = {}) {
  const root = new THREE.Group();
  const T = WALL_T;
  const W = style === 'double' ? 2.2 : DOOR_W, H = DOOR_H;
  const wood = M.wood(tone);
  const trim = M.trim(Math.max(0, tone - 0x101008));
  // casing on both faces
  for (const side of [-1, 1]) {
    const z = side * (T + 0.012);
    root.add(mesh(box(0.12, H + 0.12, 0.03, -W / 2 - 0.06, (H + 0.12) / 2, z), trim));
    root.add(mesh(box(0.12, H + 0.12, 0.03, W / 2 + 0.06, (H + 0.12) / 2, z), trim));
    root.add(mesh(box(W + 0.36, 0.16, 0.04, 0, H + 0.1, z), trim));
  }
  const pivots: THREE.Group[] = [];
  if (style === 'gate') {
    // iron bar gate that slides up
    const g = new THREE.Group();
    for (let k = 0; k <= 8; k++) g.add(mesh(box(0.035, H - 0.05, 0.035, -W / 2 + 0.05 + k * (W - 0.1) / 8, (H - 0.05) / 2, 0), M.iron()));
    for (const y of [0.15, H / 2, H - 0.2]) g.add(mesh(box(W, 0.05, 0.05, 0, y, 0), M.iron()));
    g.userData.slide = true;
    root.add(g);
    pivots.push(g);
  } else {
    const leaves = style === 'double' ? [-1, 1] : [-1];
    for (const s of leaves) {
      const lw = style === 'double' ? W / 2 : W;
      const pivot = new THREE.Group();
      pivot.position.set(s * W / 2, 0, 0);
      const dir = -s; // panel extends from hinge toward centre
      const panel = new THREE.Group();
      panel.add(mesh(box(lw - 0.01, H - 0.01, 0.05, dir * lw / 2, H / 2, 0), wood));
      // raised panels on both faces
      for (const f of [-1, 1]) {
        for (const [py, ph] of [[0.55, 0.75], [1.55, 1.0]] as const) {
          for (const px of style === 'double' ? [0] : [-0.24, 0.24]) panel.add(mesh(box(style === 'double' ? lw - 0.25 : lw / 2 - 0.2, ph, 0.014, dir * lw / 2 + px * (style === 'double' ? 0 : 1), py + 0.05, f * 0.03), trim));
        }
      }
      // brass knob + escutcheon, both faces
      const kx = dir * (lw - 0.1);
      for (const f of [-1, 1]) {
        panel.add(mesh(box(0.05, 0.18, 0.01, kx, 1.0, f * 0.03), M.brass()));
        panel.add(mesh(sphere(0.03, kx, 1.0, f * 0.07, 10, 8), M.brass()));
        panel.add(mesh(cyl(0.01, 0.01, 0.04, 6).rotateX(Math.PI / 2).translate(kx, 1.0, f * 0.05), M.brass()));
      }
      if (opts.locked && s === leaves[0]) {
        // padlock hasp on the approach side
        panel.add(mesh(box(0.08, 0.1, 0.03, kx, 1.2, -0.05), M.brass()));
        panel.add(mesh(new THREE.TorusGeometry(0.03, 0.007, 6, 12, Math.PI).translate(kx, 1.25, -0.05), M.steel()));
      }
      pivot.add(panel);
      pivot.userData.side = s;
      root.add(pivot);
      pivots.push(pivot);
    }
  }
  // number plate faces the approaching side (-z)
  if (number > 0) {
    const plate = new THREE.Mesh(new THREE.PlaneGeometry(0.36, 0.135), new THREE.MeshStandardMaterial({ map: plateTex(number, opts.fake), metalness: 0.7, roughness: 0.35 }));
    plate.position.set(0, H + 0.1, -(T + 0.04));
    plate.rotation.y = Math.PI;
    root.add(plate);
    // small light above the plate makes numbers readable in the dark
    const lampG = mesh(sphere(0.035, 0, H + 0.28, -(T + 0.06), 8, 6), M.bulb(true, 0xffc070));
    root.add(lampG);
    root.userData.plateLamp = lampG;
  }
  root.traverse((o) => { if ((o as THREE.Mesh).isMesh) { o.castShadow = true; o.receiveShadow = true; } });
  return { root, pivots };
}

function mesh(g: THREE.BufferGeometry, m: THREE.Material) {
  return new THREE.Mesh(g, m);
}
