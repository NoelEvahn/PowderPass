import * as THREE from 'three';
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js';

/**
 * Geometry helpers. All UVs are in meters so tiling textures stay at real-world scale on any size.
 */

/** Box with metric UVs, centred at origin unless offsets are given. */
export function box(w: number, h: number, d: number, x = 0, y = 0, z = 0, uvScale = 1): THREE.BufferGeometry {
  const g = new THREE.BoxGeometry(w, h, d);
  const pos = g.attributes.position as THREE.BufferAttribute;
  const nor = g.attributes.normal as THREE.BufferAttribute;
  const uv = g.attributes.uv as THREE.BufferAttribute;
  for (let i = 0; i < pos.count; i++) {
    const px = pos.getX(i) + x, py = pos.getY(i) + y, pz = pos.getZ(i) + z;
    const nx = Math.abs(nor.getX(i)), ny = Math.abs(nor.getY(i));
    if (nx > 0.5) uv.setXY(i, pz * uvScale, py * uvScale);
    else if (ny > 0.5) uv.setXY(i, px * uvScale, pz * uvScale);
    else uv.setXY(i, px * uvScale, py * uvScale);
  }
  g.translate(x, y, z);
  return g;
}

/** Box resting on y=0 (bottom at y). */
export function boxB(w: number, h: number, d: number, x = 0, y = 0, z = 0) {
  return box(w, h, d, x, y + h / 2, z);
}

/** Rounded box via bevelled extrusion — used for cushions, mattresses, handles. */
export function roundBox(w: number, h: number, d: number, r: number, x = 0, y = 0, z = 0): THREE.BufferGeometry {
  r = Math.min(r, w / 2 - 0.001, h / 2 - 0.001, d / 2 - 0.001);
  const s = new THREE.Shape();
  const hw = w / 2 - r, hh = h / 2 - r;
  s.moveTo(-hw, -h / 2); s.lineTo(hw, -h / 2); s.quadraticCurveTo(w / 2, -h / 2, w / 2, -hh);
  s.lineTo(w / 2, hh); s.quadraticCurveTo(w / 2, h / 2, hw, h / 2); s.lineTo(-hw, h / 2);
  s.quadraticCurveTo(-w / 2, h / 2, -w / 2, hh); s.lineTo(-w / 2, -hh); s.quadraticCurveTo(-w / 2, -h / 2, -hw, -h / 2);
  const g = new THREE.ExtrudeGeometry(s, { depth: d - 2 * r, bevelEnabled: true, bevelThickness: r, bevelSize: r * 0.6, bevelSegments: 3, curveSegments: 4 });
  g.translate(0, 0, -(d - 2 * r) / 2);
  metricUV(g);
  g.translate(x, y, z);
  return g;
}

/** Planar-projected metric UVs by dominant normal. */
export function metricUV(g: THREE.BufferGeometry, scale = 1) {
  g.computeVertexNormals();
  const pos = g.attributes.position as THREE.BufferAttribute;
  const nor = g.attributes.normal as THREE.BufferAttribute;
  const uv = new Float32Array(pos.count * 2);
  for (let i = 0; i < pos.count; i++) {
    const nx = Math.abs(nor.getX(i)), ny = Math.abs(nor.getY(i)), nz = Math.abs(nor.getZ(i));
    const x = pos.getX(i), y = pos.getY(i), z = pos.getZ(i);
    if (nx >= ny && nx >= nz) { uv[i * 2] = z * scale; uv[i * 2 + 1] = y * scale; }
    else if (ny >= nz) { uv[i * 2] = x * scale; uv[i * 2 + 1] = z * scale; }
    else { uv[i * 2] = x * scale; uv[i * 2 + 1] = y * scale; }
  }
  g.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
  return g;
}

export function cyl(rt: number, rb: number, h: number, seg = 12, x = 0, y = 0, z = 0): THREE.BufferGeometry {
  const g = new THREE.CylinderGeometry(rt, rb, h, seg);
  g.translate(x, y + h / 2, z);
  return g;
}

export function sphere(r: number, x = 0, y = 0, z = 0, ws = 12, hs = 8): THREE.BufferGeometry {
  const g = new THREE.SphereGeometry(r, ws, hs);
  g.translate(x, y, z);
  return g;
}

export function lathe(points: [number, number][], seg = 16, y = 0): THREE.BufferGeometry {
  const g = new THREE.LatheGeometry(points.map(([r, h]) => new THREE.Vector2(r, h)), seg);
  g.translate(0, y, 0);
  return g;
}

/** Tube along a polyline (pipes, cables, vines). */
export function tube(pts: THREE.Vector3[], r: number, seg = 24, radial = 6): THREE.BufferGeometry {
  const curve = new THREE.CatmullRomCurve3(pts);
  return new THREE.TubeGeometry(curve, seg, r, radial, false);
}

/** Merge geometries that share a material. Drops/normalises attributes so merge never fails. */
export function merge(geos: THREE.BufferGeometry[]): THREE.BufferGeometry {
  const prepared = geos.map((g) => {
    let gg = g.index ? g.toNonIndexed() : g;
    if (!gg.attributes.normal) gg.computeVertexNormals();
    if (!gg.attributes.uv) gg.setAttribute('uv', new THREE.BufferAttribute(new Float32Array(gg.attributes.position.count * 2), 2));
    for (const k of Object.keys(gg.attributes)) if (k !== 'position' && k !== 'normal' && k !== 'uv') gg.deleteAttribute(k);
    gg.morphAttributes = {};
    return gg;
  });
  const m = mergeGeometries(prepared, false);
  if (!m) throw new Error('merge failed');
  return m;
}

/** Collects geometry per material key, then emits merged meshes (one draw call per material). */
export class MeshBatch {
  private parts = new Map<THREE.Material, THREE.BufferGeometry[]>();
  add(mat: THREE.Material, g: THREE.BufferGeometry, m?: THREE.Matrix4) {
    if (m) g.applyMatrix4(m);
    let arr = this.parts.get(mat);
    if (!arr) this.parts.set(mat, (arr = []));
    arr.push(g);
  }
  build(castShadow = true, receiveShadow = true): THREE.Group {
    const grp = new THREE.Group();
    for (const [mat, geos] of this.parts) {
      if (!geos.length) continue;
      const mesh = new THREE.Mesh(merge(geos), mat);
      mesh.castShadow = castShadow;
      mesh.receiveShadow = receiveShadow;
      mesh.matrixAutoUpdate = false;
      mesh.updateMatrix();
      grp.add(mesh);
    }
    for (const geos of this.parts.values()) for (const g of geos) g.dispose();
    this.parts.clear();
    return grp;
  }
  get empty() { return this.parts.size === 0; }
}

export const tmpM = new THREE.Matrix4();
export function xform(x: number, y: number, z: number, ry = 0, rx = 0, rz = 0, s = 1): THREE.Matrix4 {
  return new THREE.Matrix4().compose(new THREE.Vector3(x, y, z), new THREE.Quaternion().setFromEuler(new THREE.Euler(rx, ry, rz)), new THREE.Vector3(s, s, s));
}
