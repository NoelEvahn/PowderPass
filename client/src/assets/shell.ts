import * as THREE from 'three';
import type { RoomData } from '@doors/shared';
import { DOOR_H, WALL_T } from '@doors/shared';
import { box, MeshBatch } from './geom';
import { M } from './materials';
import { canvasTex } from './textures';

type Wall = 'n' | 's' | 'e' | 'w';

/** Build the interior shell of a room in room-local coordinates into a batch. */
export function buildShell(room: RoomData, batch: MeshBatch, quality: number) {
  const { xMin, xMax, depth: D, theme } = room;
  const T = WALL_T;
  const lo = Math.min(0, room.rise);
  const top = Math.max(0, room.rise) + (room.height - Math.abs(room.rise));
  const z0 = T, z1 = T + D;
  const outdoor = !!theme.outdoor, glass = !!theme.glass;
  const wallMat = outdoor ? M.brick() : room.kind === 'electrical' || room.kind === 'basement' || room.kind === 'storage' ? M.concrete() : M.wallpaper(theme.wallpaper, theme.wallTint);
  const trim = M.trim(theme.woodTone);
  const floorMat = M.floor(theme.floor, theme.carpetTint);
  const stairs = room.tags.find((t) => t.startsWith('stair:'));

  // ---- floor
  if (stairs) {
    const [a, b] = stairs.slice(6).split(',').map(Number);
    batch.add(floorMat, floorPlane(xMin, z0, xMax, a, 0));
    batch.add(floorMat, floorPlane(xMin, b, xMax, z1, room.rise));
    // side walls under the upper landing are covered by the wall planes (they extend to `lo`)
  } else batch.add(floorMat, floorPlane(xMin, z0, xMax, z1, 0));
  if (outdoor) {
    // stone paths over grass
    batch.add(M.stone(), floorPlane(-1.2, z0, 1.2, z1, 0.01));
    batch.add(M.stone(), floorPlane(xMin + 1, (z0 + z1) / 2 - 1.2, xMax - 1, (z0 + z1) / 2 + 1.2, 0.012));
  }

  // ---- ceiling
  if (!outdoor) {
    const cm = glass ? M.glass() : M.ceiling();
    const g = new THREE.PlaneGeometry(xMax - xMin, D);
    g.rotateX(Math.PI / 2);
    g.translate((xMin + xMax) / 2, top, (z0 + z1) / 2);
    metric(g, 'y');
    batch.add(cm, g);
    if (glass) {
      for (let x = xMin; x <= xMax + 0.01; x += 1.5) batch.add(M.iron(), box(0.06, 0.1, D, x, top - 0.05, (z0 + z1) / 2));
      for (let z = z0; z <= z1 + 0.01; z += 1.5) batch.add(M.iron(), box(xMax - xMin, 0.1, 0.06, (xMin + xMax) / 2, top - 0.05, z));
    } else if (theme.ceilingBeams || room.kind === 'library') {
      for (let z = z0 + 1.2; z < z1 - 0.5; z += 2.2) batch.add(trim, box(xMax - xMin, 0.22, 0.18, (xMin + xMax) / 2, top - 0.11, z));
    }
    if (!glass && quality > 0) {
      // crown moulding
      for (const w of ['n', 's', 'e', 'w'] as Wall[]) wallStrip(room, w, top - 0.12, 0.12, 0.08, trim, batch, true);
    }
  }

  // ---- walls (inner faces + door reveals)
  const wallTop = top;
  for (const w of ['n', 's', 'e', 'w'] as Wall[]) {
    const segs = segments(room, w);
    for (const s of segs) {
      const y0 = s.y0 ?? lo, y1 = s.y1 ?? wallTop;
      if (glass) {
        // brick knee wall + glazing with iron mullions
        batch.add(M.brick(), wallPlane(room, w, s.a, s.b, y0, Math.min(y1, 1.0)));
        if (y1 > 1.0) {
          batch.add(M.glass(), wallPlane(room, w, s.a, s.b, Math.max(y0, 1.0), y1, 0.0));
          for (let t = s.a; t <= s.b + 0.01; t += 1.2) batch.add(M.iron(), postAt(room, w, t, Math.max(y0, 1.0), y1));
          batch.add(M.iron(), wallStripGeo(room, w, s.a, s.b, 1.0, 0.08, 0.08));
        }
      } else batch.add(wallMat, wallPlane(room, w, s.a, s.b, y0, y1));
    }
  }
  // door reveals for openings (jamb faces)
  for (const o of room.openings) {
    const oy = o.y ?? 0;
    for (const side of [-1, 1]) {
      const t = o.center + side * o.width / 2;
      batch.add(trim, revealPlane(room, o.wall, t, oy, oy + o.height, -side));
    }
    batch.add(trim, lintelPlane(room, o.wall, o.center, o.width, oy + o.height));
  }

  if (outdoor) {
    facade(room, batch);
    return;
  }
  if (glass) return;
  // ---- wainscot, chair rail, baseboard
  for (const w of ['n', 's', 'e', 'w'] as Wall[]) {
    const fy = (t: number) => (stairs ? 0 : 0) + (w === 'e' || w === 'w' ? floorAtLocal(room, t) : 0) + (w === 'n' ? room.rise : 0) * (stairs ? 1 : 0);
    if (theme.wainscot && room.kind !== 'electrical' && room.kind !== 'basement') {
      wallStrip(room, w, 0, 1.0, 0.025, trim, batch, false, fy);
      wallStrip(room, w, 1.0, 0.06, 0.045, trim, batch, false, fy);
    }
    wallStrip(room, w, 0, 0.16, 0.03, trim, batch, false, fy);
  }
}

function floorAtLocal(room: RoomData, lz: number) {
  const st = room.tags.find((t) => t.startsWith('stair:'));
  if (!st) return 0;
  const [a, b] = st.slice(6).split(',').map(Number);
  if (lz <= a) return 0;
  if (lz >= b) return room.rise;
  return ((lz - a) / (b - a)) * room.rise;
}

function metric(g: THREE.BufferGeometry, axis: 'x' | 'y' | 'z') {
  const p = g.attributes.position as THREE.BufferAttribute;
  const uv = g.attributes.uv as THREE.BufferAttribute;
  for (let i = 0; i < p.count; i++) {
    if (axis === 'y') uv.setXY(i, p.getX(i), p.getZ(i));
    else if (axis === 'x') uv.setXY(i, p.getZ(i), p.getY(i));
    else uv.setXY(i, p.getX(i), p.getY(i));
  }
}

function floorPlane(x0: number, z0: number, x1: number, z1: number, y: number) {
  const g = new THREE.PlaneGeometry(x1 - x0, z1 - z0);
  g.rotateX(-Math.PI / 2);
  g.translate((x0 + x1) / 2, y, (z0 + z1) / 2);
  metric(g, 'y');
  return g;
}

/** Wall segments (along-wall ranges), split around openings, with partial lintel/sill segments. */
function segments(room: RoomData, w: Wall): { a: number; b: number; y0?: number; y1?: number }[] {
  const horiz = w === 'n' || w === 's';
  const a0 = horiz ? room.xMin : WALL_T, a1 = horiz ? room.xMax : WALL_T + room.depth;
  const ops = room.openings.filter((o) => o.wall === w).sort((p, q) => p.center - q.center);
  const out: { a: number; b: number; y0?: number; y1?: number }[] = [];
  let cur = a0;
  const lo = Math.min(0, room.rise);
  for (const o of ops) {
    const oa = o.center - o.width / 2, ob = o.center + o.width / 2;
    if (oa > cur) out.push({ a: cur, b: oa });
    const oy = o.y ?? 0;
    out.push({ a: oa, b: ob, y0: oy + o.height });
    if (oy > lo) out.push({ a: oa, b: ob, y0: lo, y1: oy });
    cur = ob;
  }
  if (a1 > cur) out.push({ a: cur, b: a1 });
  return out;
}

/** Plane on the inner face of a wall between along-coords a..b and heights y0..y1, facing into the room. */
function wallPlane(room: RoomData, w: Wall, a: number, b: number, y0: number, y1: number, inset = 0) {
  const g = new THREE.PlaneGeometry(b - a, y1 - y0);
  const uv = g.attributes.uv as THREE.BufferAttribute;
  const p = g.attributes.position as THREE.BufferAttribute;
  for (let i = 0; i < p.count; i++) uv.setXY(i, p.getX(i) + (a + b) / 2, p.getY(i) + (y0 + y1) / 2);
  const m = (a + b) / 2, my = (y0 + y1) / 2;
  const T = WALL_T;
  switch (w) {
    case 's': g.translate(m, my, T + inset); break;
    case 'n': g.rotateY(Math.PI); g.translate(m, my, T + room.depth - inset); break;
    case 'w': g.rotateY(Math.PI / 2); g.translate(room.xMin + inset, my, m); break;
    case 'e': g.rotateY(-Math.PI / 2); g.translate(room.xMax - inset, my, m); break;
  }
  return g;
}

function revealPlane(room: RoomData, w: Wall, t: number, y0: number, y1: number, facing: number) {
  const T = WALL_T;
  const g = new THREE.PlaneGeometry(2 * T, y1 - y0);
  const horiz = w === 'n' || w === 's';
  // plane spans wall thickness (both rooms' walls) – perpendicular to wall, facing along-wall direction
  g.rotateY(facing > 0 ? Math.PI / 2 : -Math.PI / 2);
  const my = (y0 + y1) / 2;
  if (horiz) {
    const zc = w === 's' ? 0 : 2 * T + room.depth;
    g.translate(t, my, zc);
  } else {
    g.rotateY(Math.PI / 2);
    const xc = w === 'w' ? room.xMin - T : room.xMax + T;
    g.translate(xc, my, t);
  }
  return g;
}

function lintelPlane(room: RoomData, w: Wall, c: number, width: number, y: number) {
  const T = WALL_T;
  const g = new THREE.PlaneGeometry(width, 2 * T);
  g.rotateX(Math.PI / 2);
  if (w === 's') g.translate(c, y, 0);
  else if (w === 'n') g.translate(c, y, 2 * T + room.depth);
  else { g.rotateY(Math.PI / 2); g.translate(w === 'w' ? room.xMin - T : room.xMax + T, y, c); }
  return g;
}

function postAt(room: RoomData, w: Wall, t: number, y0: number, y1: number) {
  const T = WALL_T;
  const h = y1 - y0;
  if (w === 's') return box(0.06, h, 0.06, t, y0 + h / 2, T);
  if (w === 'n') return box(0.06, h, 0.06, t, y0 + h / 2, T + room.depth);
  if (w === 'w') return box(0.06, h, 0.06, room.xMin, y0 + h / 2, t);
  return box(0.06, h, 0.06, room.xMax, y0 + h / 2, t);
}

function wallStripGeo(room: RoomData, w: Wall, a: number, b: number, y: number, h: number, depth: number) {
  const T = WALL_T;
  const L = b - a, m = (a + b) / 2;
  if (w === 's') return box(L, h, depth, m, y + h / 2, T + depth / 2);
  if (w === 'n') return box(L, h, depth, m, y + h / 2, T + room.depth - depth / 2);
  if (w === 'w') return box(depth, h, L, room.xMin + depth / 2, y + h / 2, m);
  return box(depth, h, L, room.xMax - depth / 2, y + h / 2, m);
}

/** Moulding strip along a wall, interrupted by openings. */
function wallStrip(room: RoomData, w: Wall, y: number, h: number, depth: number, mat: THREE.Material, batch: MeshBatch, ignoreOpenings = false, fy?: (t: number) => number) {
  const segs = segments(room, w).filter((s) => s.y0 === undefined);
  const ops = room.openings.filter((o) => o.wall === w);
  for (const s of segs) {
    const base = fy ? fy((s.a + s.b) / 2) : 0;
    if (!ignoreOpenings) {
      if (ops.some((o) => y + base >= (o.y ?? 0) && y + base < (o.y ?? 0) + o.height && s.a < o.center + o.width / 2 && s.b > o.center - o.width / 2)) continue;
    }
    if (fy && (w === 'e' || w === 'w') && room.tags.some((t) => t.startsWith('stair:'))) continue; // skip on stair side walls
    batch.add(mat, wallStripGeo(room, w, s.a, s.b, y + base, h, depth));
  }
  if (ignoreOpenings) return;
}

/** Courtyard: dark hotel facade with lit and unlit windows above the brick walls. */
function facade(room: RoomData, batch: MeshBatch) {
  const lit = M.emissive(0xffb060, 1.6), dark = M.black();
  const frame = M.trim(0x2a2018);
  for (const w of ['n', 's', 'e', 'w'] as Wall[]) {
    const horiz = w === 'n' || w === 's';
    const a0 = horiz ? room.xMin : WALL_T, a1 = horiz ? room.xMax : WALL_T + room.depth;
    let k = 0;
    for (let t = a0 + 2; t < a1 - 1.5; t += 3) {
      for (const y of [3.6, 6.4]) {
        k++;
        const mat = (k * 7 + room.index) % 5 === 0 ? lit : dark;
        batch.add(frame, wallStripGeo(room, w, t - 0.65, t + 0.65, y - 0.05, 1.8, 0.1));
        batch.add(mat, wallStripGeo(room, w, t - 0.55, t + 0.55, y + 0.05, 1.6, 0.12));
      }
    }
    batch.add(M.stone(), wallStripGeo(room, w, a0, a1, 2.6, 0.25, 0.25));
  }
}

let skyTex: THREE.CanvasTexture | null = null;
export function skyTexture() {
  if (skyTex) return skyTex;
  skyTex = canvasTex(512, 256, (ctx) => {
    const g = ctx.createLinearGradient(0, 0, 0, 256);
    g.addColorStop(0, '#05070c'); g.addColorStop(0.6, '#0e1420'); g.addColorStop(1, '#1a2230');
    ctx.fillStyle = g; ctx.fillRect(0, 0, 512, 256);
    for (let k = 0; k < 40; k++) { ctx.fillStyle = `rgba(60,70,90,${0.15 + Math.random() * 0.2})`; ctx.beginPath(); ctx.ellipse(Math.random() * 512, 60 + Math.random() * 120, 60 + Math.random() * 90, 14 + Math.random() * 20, 0, 0, Math.PI * 2); ctx.fill(); }
    ctx.fillStyle = 'rgba(220,230,255,0.9)'; ctx.beginPath(); ctx.arc(380, 50, 12, 0, Math.PI * 2); ctx.fill();
  });
  skyTex.userData.shared = true;
  return skyTex;
}

export { DOOR_H };
