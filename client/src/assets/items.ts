import * as THREE from 'three';
import type { ItemType } from '@doors/shared';
import { box, cyl, sphere, lathe } from './geom';
import { M } from './materials';

/** Small item models used for pickups, drawer loot, dropped items and the first-person view. */
export function itemMesh(type: ItemType | 'gold' | 'book'): THREE.Object3D {
  const g = new THREE.Group();
  const add = (geo: THREE.BufferGeometry, mat: THREE.Material) => { const m = new THREE.Mesh(geo, mat); m.castShadow = true; g.add(m); return m; };
  switch (type) {
    case 'key': case 'electricalKey': case 'skeletonKey': {
      const mat = type === 'skeletonKey' ? M.iron() : M.brass();
      add(new THREE.TorusGeometry(0.025, 0.007, 6, 14), mat);
      add(box(0.008, 0.008, 0.09, 0, 0, 0.065), mat);
      add(box(0.008, 0.02, 0.012, 0, -0.012, 0.1), mat);
      add(box(0.008, 0.014, 0.01, 0, -0.01, 0.08), mat);
      if (type === 'electricalKey') add(box(0.04, 0.05, 0.004, 0, 0, -0.05), M.red());
      else add(box(0.03, 0.04, 0.003, 0, -0.03, -0.04), M.paper());
      g.rotation.x = Math.PI / 2;
      break;
    }
    case 'gold': {
      for (let k = 0; k < 7; k++) add(cyl(0.018, 0.018, 0.005, 10, (k % 3 - 1) * 0.03, Math.floor(k / 3) * 0.006, ((k * 7) % 3 - 1) * 0.025), M.brass());
      break;
    }
    case 'book': {
      add(box(0.16, 0.035, 0.22), M.trim(0x5a1a14));
      add(box(0.15, 0.03, 0.21, 0.006, 0, 0), M.paper());
      break;
    }
    case 'hintPaper': add(box(0.21, 0.002, 0.29), M.paper()); break;
    case 'breaker': {
      add(box(0.06, 0.1, 0.05), M.rubber());
      add(box(0.015, 0.04, 0.015, 0, 0.06, 0.01), M.red());
      add(box(0.05, 0.015, 0.03, 0, -0.055, 0), M.brass());
      break;
    }
    case 'flashlight': {
      add(cyl(0.018, 0.018, 0.16, 10).rotateX(Math.PI / 2).translate(0, 0, 0.08), M.iron());
      add(cyl(0.028, 0.02, 0.05, 12).rotateX(Math.PI / 2).translate(0, 0, 0.18), M.iron());
      add(cyl(0.024, 0.024, 0.005, 12).rotateX(Math.PI / 2).translate(0, 0, 0.206), M.bulb(true, 0xfff0d0));
      add(box(0.01, 0.012, 0.02, 0, 0.02, 0.06), M.rubber());
      break;
    }
    case 'lighter': {
      add(box(0.035, 0.06, 0.012, 0, 0, 0), M.steel());
      add(box(0.035, 0.02, 0.012, 0, 0.04, 0), M.brass());
      break;
    }
    case 'battery': {
      add(cyl(0.012, 0.012, 0.05, 10), M.steel());
      add(cyl(0.0122, 0.0122, 0.03, 10, 0, 0.01, 0), M.red());
      break;
    }
    case 'lockpick': {
      add(box(0.005, 0.005, 0.1), M.steel());
      add(box(0.005, 0.005, 0.1, 0.012, 0, 0.01).rotateY(0.15), M.steel());
      add(box(0.02, 0.012, 0.03, 0.005, 0, -0.05), M.rubber());
      break;
    }
    case 'vitamins': {
      add(cyl(0.022, 0.022, 0.06, 12), new THREE.MeshStandardMaterial({ color: 0xd88820, roughness: 0.3, transparent: true, opacity: 0.85 }));
      add(cyl(0.024, 0.024, 0.015, 12, 0, 0.06, 0), M.porcelain());
      break;
    }
    case 'bandage': {
      add(cyl(0.03, 0.03, 0.05, 14).rotateZ(Math.PI / 2), M.fabric(0xeae4d8));
      break;
    }
    case 'crucifix': {
      add(box(0.015, 0.12, 0.01), M.brass());
      add(box(0.07, 0.015, 0.01, 0, 0.03, 0), M.brass());
      break;
    }
    default: add(sphere(0.03), M.brass());
  }
  return g;
}
