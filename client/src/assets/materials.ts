import * as THREE from 'three';
import { getTex } from './textures';

/** Shared PBR material library. Materials are cached by key so rooms batch draw calls. */
const cache = new Map<string, THREE.Material>();

function std(key: string, make: () => THREE.MeshStandardMaterial): THREE.MeshStandardMaterial {
  let m = cache.get(key) as THREE.MeshStandardMaterial | undefined;
  if (!m) { m = make(); m.name = key; cache.set(key, m); }
  return m;
}

function textured(tex: string, color: number, opts: Partial<THREE.MeshStandardMaterialParameters> = {}, normalScale = 1) {
  const t = getTex(tex);
  return new THREE.MeshStandardMaterial({ map: t.map, normalMap: t.normalMap, roughnessMap: t.roughnessMap, color, normalScale: new THREE.Vector2(normalScale, normalScale), roughness: 1, metalness: 0, ...opts });
}

export const M = {
  wallpaper: (pattern: number, tint: number) => std(`wp${pattern}-${tint}`, () => textured(`wall${pattern}`, tint, {}, 0.6)),
  floor: (kind: string, tint: number) => std(`fl-${kind}-${tint}`, () => {
    switch (kind) {
      case 'carpet': return textured('carpet', tint, {}, 0.8);
      case 'wood': return textured('planks', 0xffffff, {}, 0.9);
      case 'tile': return textured('tile', 0xffffff, {}, 0.7);
      case 'marble': return textured('marble', 0xf0e8dc, { envMapIntensity: 1.2 }, 0.4);
      case 'concrete': return textured('concrete', 0xffffff, {}, 0.8);
      case 'stone': return textured('stone', 0xffffff, {}, 1.2);
      case 'grass': return textured('grass', 0xffffff, {}, 1);
      default: return textured('planks', 0xffffff);
    }
  }),
  ceiling: () => std('ceiling', () => textured('plaster', 0xd8ccb8, {}, 0.5)),
  plaster: (tint = 0xcfc3ad) => std(`plaster-${tint}`, () => textured('plaster', tint, {}, 0.6)),
  wood: (tone: number) => std(`wood-${tone}`, () => textured('wood', tone, { roughness: 0.9 }, 0.6)),
  woodPlank: () => std('woodplank', () => textured('planks', 0xffffff)),
  woodDark: () => std('wooddark', () => textured('planksDark', 0xffffff)),
  trim: (tone: number) => std(`trim-${tone}`, () => textured('wood', tone, { roughness: 0.7 }, 0.4)),
  fabric: (tint: number) => std(`fab-${tint}`, () => textured('fabric', tint, {}, 0.8)),
  brass: () => std('brass', () => textured('metal', 0xc89a48, { metalness: 1, roughness: 0.55 }, 0.3)),
  iron: () => std('iron', () => textured('metal', 0x5a5a5e, { metalness: 0.9, roughness: 0.8 }, 0.5)),
  steel: () => std('steel', () => textured('metal', 0x9aa0a6, { metalness: 0.85, roughness: 0.6 }, 0.3)),
  rust: () => std('rust', () => textured('metal', 0x7a4a2a, { metalness: 0.5, roughness: 0.9 }, 0.8)),
  concrete: () => std('concrete', () => textured('concrete', 0xffffff)),
  brick: () => std('brick', () => textured('brick', 0xffffff, {}, 0.9)),
  stone: () => std('stone', () => textured('stone', 0xbbbbbb)),
  marble: () => std('marble', () => textured('marble', 0xffffff, {}, 0.3)),
  tile: () => std('tile', () => textured('tile', 0xffffff)),
  leaves: () => std('leaves', () => textured('leaves', 0xffffff, { side: THREE.DoubleSide }, 1)),
  dirt: () => std('dirt', () => textured('dirt', 0xffffff)),
  porcelain: () => std('porcelain', () => new THREE.MeshStandardMaterial({ color: 0xe8e2d6, roughness: 0.25 })),
  glass: () => std('glass', () => new THREE.MeshStandardMaterial({ color: 0x9fb6c0, roughness: 0.08, metalness: 0.1, transparent: true, opacity: 0.22, depthWrite: false })),
  mirror: () => std('mirror', () => new THREE.MeshStandardMaterial({ color: 0x8a9298, roughness: 0.05, metalness: 1 })),
  black: () => std('black', () => new THREE.MeshStandardMaterial({ color: 0x050505, roughness: 0.9 })),
  paper: () => std('paper', () => new THREE.MeshStandardMaterial({ color: 0xe6dcc0, roughness: 0.95 })),
  cardboard: () => std('cardboard', () => textured('concrete', 0xb58a52, {}, 0.3)),
  bark: () => std('bark', () => textured('wood', 0x4a3424, {}, 2)),
  water: () => std('water', () => new THREE.MeshStandardMaterial({ color: 0x1c2a30, roughness: 0.05, metalness: 0.3 })),
  bulb: (on: boolean, color = 0xffd9a0) => std(`bulb-${on}-${color}`, () => new THREE.MeshStandardMaterial({ color: on ? 0xfff2d8 : 0x333028, emissive: on ? color : 0x000000, emissiveIntensity: on ? 3.2 : 0, roughness: 0.3 })),
  shade: (tint = 0xd8b88a) => std(`shade-${tint}`, () => new THREE.MeshStandardMaterial({ color: tint, roughness: 0.9, side: THREE.DoubleSide, emissive: 0x000000 })),
  emissive: (color: number, intensity = 2) => std(`em-${color}-${intensity}`, () => new THREE.MeshStandardMaterial({ color: 0x000000, emissive: color, emissiveIntensity: intensity })),
  red: () => std('redmetal', () => new THREE.MeshStandardMaterial({ color: 0x7a1010, roughness: 0.6, metalness: 0.4 })),
  rubber: () => std('rubber', () => new THREE.MeshStandardMaterial({ color: 0x151515, roughness: 0.85 })),
  goo: () => std('goo', () => new THREE.MeshStandardMaterial({ color: 0x050505, roughness: 0.15, metalness: 0.2 })),
};

export function disposeMaterials() {
  for (const m of cache.values()) m.dispose();
  cache.clear();
}
