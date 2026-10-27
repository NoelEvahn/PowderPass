import * as THREE from 'three';

/**
 * Procedural skeletal rigs. A rig is a bone hierarchy plus geometry parts that are skinned to bones
 * (with blending toward the parent near joints), producing a real THREE.SkinnedMesh that is animated
 * by rotating bones.
 */
export interface BoneDef { name: string; parent?: string; pos: [number, number, number] }
export interface Part { geo: THREE.BufferGeometry; bone: string; blend?: number; mat?: number }

export class Rig {
  bones = new Map<string, THREE.Bone>();
  root: THREE.Bone;
  skeleton!: THREE.Skeleton;
  mesh!: THREE.SkinnedMesh;
  private defs: BoneDef[];
  private bindPos = new Map<string, THREE.Vector3>();

  constructor(defs: BoneDef[]) {
    this.defs = defs;
    for (const d of defs) {
      const b = new THREE.Bone();
      b.name = d.name;
      b.position.set(...d.pos);
      this.bones.set(d.name, b);
    }
    for (const d of defs) if (d.parent) this.bones.get(d.parent)!.add(this.bones.get(d.name)!);
    this.root = this.bones.get(defs[0].name)!;
    this.root.updateMatrixWorld(true);
    for (const [n, b] of this.bones) this.bindPos.set(n, new THREE.Vector3().setFromMatrixPosition(b.matrixWorld));
  }

  /** World-space bind position of a bone (for building geometry in model space). */
  at(name: string) { return this.bindPos.get(name)!.clone(); }

  /** Limb segment from bone a toward bone b (or by explicit end point). */
  limb(a: string, b: string | THREE.Vector3, r0: number, r1: number, seg = 10): THREE.BufferGeometry {
    const p0 = this.at(a), p1 = typeof b === 'string' ? this.at(b) : b;
    const len = p0.distanceTo(p1);
    const g = new THREE.CylinderGeometry(r1, r0, len, seg, 4, false);
    g.translate(0, len / 2, 0);
    const q = new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 1, 0), p1.clone().sub(p0).normalize());
    g.applyQuaternion(q);
    g.translate(p0.x, p0.y, p0.z);
    return g;
  }

  build(parts: Part[], materials: THREE.Material[]): THREE.SkinnedMesh {
    const boneList = this.defs.map((d) => this.bones.get(d.name)!);
    const index = new Map(this.defs.map((d, i) => [d.name, i]));
    const geos: THREE.BufferGeometry[] = [];
    const groups: { start: number; count: number; mat: number }[] = [];
    let vcount = 0;
    const allPos: number[] = [], allNor: number[] = [], allUv: number[] = [], skinI: number[] = [], skinW: number[] = [];
    for (const part of parts) {
      const g = part.geo.index ? part.geo.toNonIndexed() : part.geo;
      if (!g.attributes.normal) g.computeVertexNormals();
      const pos = g.attributes.position as THREE.BufferAttribute;
      const nor = g.attributes.normal as THREE.BufferAttribute;
      const uv = g.attributes.uv as THREE.BufferAttribute | undefined;
      const bi = index.get(part.bone)!;
      const parentName = this.defs[bi].parent;
      const pi = parentName ? index.get(parentName)! : bi;
      const bp = this.at(part.bone);
      const childDef = this.defs.find((d) => d.parent === part.bone);
      const cp = childDef ? this.at(childDef.name) : bp.clone().add(new THREE.Vector3(0, -0.3, 0));
      const axis = cp.clone().sub(bp);
      const L = axis.length() || 1;
      axis.normalize();
      for (let i = 0; i < pos.count; i++) {
        const x = pos.getX(i), y = pos.getY(i), z = pos.getZ(i);
        allPos.push(x, y, z);
        allNor.push(nor.getX(i), nor.getY(i), nor.getZ(i));
        allUv.push(uv ? uv.getX(i) : 0, uv ? uv.getY(i) : 0);
        let w = 1;
        if (part.blend && pi !== bi) {
          const t = new THREE.Vector3(x, y, z).sub(bp).dot(axis) / L;
          w = THREE.MathUtils.clamp(0.5 + t / part.blend * 0.5, 0.5, 1);
        }
        skinI.push(bi, pi, 0, 0);
        skinW.push(w, 1 - w, 0, 0);
      }
      groups.push({ start: vcount, count: pos.count, mat: part.mat ?? 0 });
      vcount += pos.count;
      geos.push(g);
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(allPos, 3));
    geo.setAttribute('normal', new THREE.Float32BufferAttribute(allNor, 3));
    geo.setAttribute('uv', new THREE.Float32BufferAttribute(allUv, 2));
    geo.setAttribute('skinIndex', new THREE.Uint16BufferAttribute(skinI, 4));
    geo.setAttribute('skinWeight', new THREE.Float32BufferAttribute(skinW, 4));
    // merge consecutive groups with the same material
    for (const gr of groups) {
      const last = geo.groups[geo.groups.length - 1];
      if (last && last.materialIndex === gr.mat && last.start + last.count === gr.start) last.count += gr.count;
      else geo.addGroup(gr.start, gr.count, gr.mat);
    }
    const mesh = new THREE.SkinnedMesh(geo, materials);
    mesh.add(this.root);
    this.skeleton = new THREE.Skeleton(boneList);
    mesh.bind(this.skeleton);
    mesh.castShadow = true;
    mesh.frustumCulled = false;
    this.mesh = mesh;
    return mesh;
  }

  b(name: string) { return this.bones.get(name)!; }
}

/** Standard biped skeleton scaled by height. */
export function bipedDefs(h: number, opts: { shoulder?: number; armLen?: number; legLen?: number } = {}): BoneDef[] {
  const s = h / 1.8;
  const leg = (opts.legLen ?? 0.9) * s, arm = (opts.armLen ?? 0.62) * s, sh = (opts.shoulder ?? 0.2) * s;
  return [
    { name: 'hips', pos: [0, leg + 0.05 * s, 0] },
    { name: 'spine', parent: 'hips', pos: [0, 0.18 * s, 0] },
    { name: 'chest', parent: 'spine', pos: [0, 0.2 * s, 0] },
    { name: 'neck', parent: 'chest', pos: [0, 0.2 * s, 0] },
    { name: 'head', parent: 'neck', pos: [0, 0.1 * s, 0] },
    { name: 'headTop', parent: 'head', pos: [0, 0.22 * s, 0] },
    { name: 'shL', parent: 'chest', pos: [sh, 0.15 * s, 0] },
    { name: 'armL', parent: 'shL', pos: [0.02 * s, 0, 0] },
    { name: 'foreL', parent: 'armL', pos: [0, -arm * 0.5, 0] },
    { name: 'handL', parent: 'foreL', pos: [0, -arm * 0.48, 0] },
    { name: 'tipL', parent: 'handL', pos: [0, -0.16 * s, 0] },
    { name: 'shR', parent: 'chest', pos: [-sh, 0.15 * s, 0] },
    { name: 'armR', parent: 'shR', pos: [-0.02 * s, 0, 0] },
    { name: 'foreR', parent: 'armR', pos: [0, -arm * 0.5, 0] },
    { name: 'handR', parent: 'foreR', pos: [0, -arm * 0.48, 0] },
    { name: 'tipR', parent: 'handR', pos: [0, -0.16 * s, 0] },
    { name: 'thighL', parent: 'hips', pos: [0.1 * s, -0.05 * s, 0] },
    { name: 'shinL', parent: 'thighL', pos: [0, -leg * 0.5, 0] },
    { name: 'footL', parent: 'shinL', pos: [0, -leg * 0.47, 0] },
    { name: 'toeL', parent: 'footL', pos: [0, -0.03 * s, 0.16 * s] },
    { name: 'thighR', parent: 'hips', pos: [-0.1 * s, -0.05 * s, 0] },
    { name: 'shinR', parent: 'thighR', pos: [0, -leg * 0.5, 0] },
    { name: 'footR', parent: 'shinR', pos: [0, -leg * 0.47, 0] },
    { name: 'toeR', parent: 'footR', pos: [0, -0.03 * s, 0.16 * s] },
  ];
}

/** Procedural gait: sets limb rotations for walking/running with phase `t` (radians). */
export function gait(rig: Rig, t: number, amt: number, opts: { armSwing?: number; lean?: number; bounce?: number; armsForward?: number; hunch?: number } = {}) {
  const sw = Math.sin(t) * amt, sw2 = Math.sin(t + Math.PI) * amt;
  rig.b('thighL').rotation.x = sw * 0.8;
  rig.b('thighR').rotation.x = sw2 * 0.8;
  rig.b('shinL').rotation.x = Math.max(0, -Math.cos(t)) * amt * 1.2 + 0.05;
  rig.b('shinR').rotation.x = Math.max(0, -Math.cos(t + Math.PI)) * amt * 1.2 + 0.05;
  rig.b('footL').rotation.x = -sw * 0.3;
  rig.b('footR').rotation.x = -sw2 * 0.3;
  const arm = opts.armSwing ?? 0.7;
  rig.b('armL').rotation.x = -sw * arm - (opts.armsForward ?? 0);
  rig.b('armR').rotation.x = -sw2 * arm - (opts.armsForward ?? 0);
  rig.b('foreL').rotation.x = -0.3 - Math.abs(sw) * 0.4 - (opts.armsForward ?? 0) * 0.3;
  rig.b('foreR').rotation.x = -0.3 - Math.abs(sw2) * 0.4 - (opts.armsForward ?? 0) * 0.3;
  rig.b('armL').rotation.z = 0.08;
  rig.b('armR').rotation.z = -0.08;
  rig.b('spine').rotation.x = (opts.lean ?? 0) + (opts.hunch ?? 0) * 0.5;
  rig.b('chest').rotation.x = (opts.hunch ?? 0) * 0.5;
  rig.b('spine').rotation.y = Math.sin(t) * amt * 0.15;
  rig.b('hips').position.y = rig.at('hips').y - Math.abs(Math.cos(t)) * (opts.bounce ?? 0.04) * amt;
}
