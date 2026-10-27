export interface Vec2 { x: number; z: number }
export interface Vec3 { x: number; y: number; z: number }
/** Axis-aligned box in world space. All generated geometry is axis-aligned (rotations are multiples of 90°). */
export interface Box { minX: number; minY: number; minZ: number; maxX: number; maxY: number; maxZ: number }

export const clamp = (v: number, a: number, b: number) => (v < a ? a : v > b ? b : v);
export const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
export const dist2 = (a: Vec3, b: Vec3) => (a.x - b.x) ** 2 + (a.y - b.y) ** 2 + (a.z - b.z) ** 2;
export const dist = (a: Vec3, b: Vec3) => Math.sqrt(dist2(a, b));
export const distXZ = (a: Vec2, b: Vec2) => Math.hypot(a.x - b.x, a.z - b.z);
export const v3 = (x = 0, y = 0, z = 0): Vec3 => ({ x, y, z });

/** Rotate a local XZ offset by rot*90° around Y, matching three.js `rotation.y = rot*PI/2`. */
export function rotXZ(x: number, z: number, rot: number): [number, number] {
  switch (rot & 3) {
    case 0: return [x, z];
    case 1: return [z, -x];
    case 2: return [-x, -z];
    default: return [-z, x];
  }
}

/** Direction vector for a rotation index: 0:+z 1:+x 2:-z 3:-x */
export function dirVec(rot: number): [number, number] {
  return rotXZ(0, 1, rot);
}

export function boxOverlap(a: Box, b: Box, eps = 0.001): boolean {
  return a.minX < b.maxX - eps && a.maxX > b.minX + eps && a.minY < b.maxY - eps && a.maxY > b.minY + eps && a.minZ < b.maxZ - eps && a.maxZ > b.minZ + eps;
}

export function pointInBox(p: Vec3, b: Box, pad = 0): boolean {
  return p.x >= b.minX - pad && p.x <= b.maxX + pad && p.y >= b.minY - pad && p.y <= b.maxY + pad && p.z >= b.minZ - pad && p.z <= b.maxZ + pad;
}

/** Ray/box slab test. Returns hit distance along normalized dir or Infinity. */
export function rayBox(o: Vec3, d: Vec3, b: Box, maxT = Infinity): number {
  let tmin = 0, tmax = maxT;
  const axes: ['x' | 'y' | 'z', number, number][] = [['x', b.minX, b.maxX], ['y', b.minY, b.maxY], ['z', b.minZ, b.maxZ]];
  for (const [ax, mn, mx] of axes) {
    const dd = d[ax], oo = o[ax];
    if (Math.abs(dd) < 1e-9) {
      if (oo < mn || oo > mx) return Infinity;
    } else {
      let t1 = (mn - oo) / dd, t2 = (mx - oo) / dd;
      if (t1 > t2) [t1, t2] = [t2, t1];
      tmin = Math.max(tmin, t1);
      tmax = Math.min(tmax, t2);
      if (tmin > tmax) return Infinity;
    }
  }
  return tmin;
}

export function angleDiff(a: number, b: number): number {
  let d = (b - a) % (Math.PI * 2);
  if (d > Math.PI) d -= Math.PI * 2;
  if (d < -Math.PI) d += Math.PI * 2;
  return d;
}

/** Unit look vector from yaw/pitch (yaw 0 looks toward -z, three.js camera convention). */
export function lookDir(yaw: number, pitch: number): Vec3 {
  const cp = Math.cos(pitch);
  return { x: -Math.sin(yaw) * cp, y: Math.sin(pitch), z: -Math.cos(yaw) * cp };
}
