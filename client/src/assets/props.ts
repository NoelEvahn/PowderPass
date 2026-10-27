import * as THREE from 'three';
import type { Interactable, PropData, RoomData } from '@doors/shared';
import { FOOTPRINT } from '@doors/shared/gen/builder';
import { box, boxB, cyl, lathe, MeshBatch, roundBox, sphere, tube, xform, metricUV } from './geom';
import { M } from './materials';
import { paintingTex, spineTex, canvasTex } from './textures';

/** Context passed to prop builders. Static geometry goes to `add` (merged per material); dynamic parts to `dyn`. */
export interface PropCtx {
  p: PropData;
  room: RoomData;
  add: (mat: THREE.Material, g: THREE.BufferGeometry) => void;
  /** add a dynamic object in prop-local coordinates; returns it (already parented) */
  dyn: (o: THREE.Object3D) => THREE.Object3D;
  interactables: Interactable[];
  /** register an animated part by key (drawer id, closet door, etc.) */
  part: (key: string, o: THREE.Object3D) => void;
  fixture: (o: THREE.Object3D) => void;
  wood: THREE.Material;
  quality: number;
}

let bookMatCache: THREE.MeshStandardMaterial | null = null;
const fp = (t: PropData['type']) => FOOTPRINT[t] ?? [1, 1, 1];

function handle(mat: THREE.Material, x: number, y: number, z: number, w = 0.12): THREE.BufferGeometry {
  const g = box(w, 0.018, 0.018, x, y, z + 0.03);
  const a = box(0.016, 0.016, 0.03, x - w / 2 + 0.01, y, z + 0.012);
  const b = box(0.016, 0.016, 0.03, x + w / 2 - 0.01, y, z + 0.012);
  return mergeSmall([g, a, b]);
}
function knob(x: number, y: number, z: number, r = 0.022) { return sphere(r, x, y, z + r, 8, 6); }
function mergeSmall(gs: THREE.BufferGeometry[]) {
  const out = gs.map((g) => (g.index ? g.toNonIndexed() : g));
  const n = out.reduce((a, g) => a + g.attributes.position.count, 0);
  const pos = new Float32Array(n * 3), nor = new Float32Array(n * 3), uv = new Float32Array(n * 2);
  let o = 0;
  for (const g of out) {
    if (!g.attributes.normal) g.computeVertexNormals();
    pos.set(g.attributes.position.array as Float32Array, o * 3);
    nor.set(g.attributes.normal.array as Float32Array, o * 3);
    if (g.attributes.uv) uv.set(g.attributes.uv.array as Float32Array, o * 2);
    o += g.attributes.position.count;
  }
  const r = new THREE.BufferGeometry();
  r.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  r.setAttribute('normal', new THREE.BufferAttribute(nor, 3));
  r.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
  return r;
}

/** Panelled cabinet body: carcass, top with overhang, plinth and side panels. */
function carcass(c: PropCtx, w: number, h: number, d: number, opts: { top?: boolean; plinth?: number } = {}) {
  const plinth = opts.plinth ?? 0.08;
  c.add(c.wood, boxB(w - 0.02, plinth, d - 0.04, 0, 0, -0.01));
  c.add(c.wood, boxB(0.03, h - plinth, d, -w / 2 + 0.015, plinth, 0));
  c.add(c.wood, boxB(0.03, h - plinth, d, w / 2 - 0.015, plinth, 0));
  c.add(c.wood, boxB(w, 0.02, d, 0, plinth, 0));
  c.add(c.wood, boxB(w, h - plinth, 0.02, 0, plinth, -d / 2 + 0.01));
  if (opts.top !== false) {
    c.add(c.wood, boxB(w + 0.04, 0.035, d + 0.03, 0, h - 0.035, 0.005));
    c.add(M.trim(0x2a1a10), boxB(w + 0.05, 0.012, d + 0.035, 0, h - 0.045, 0.005));
  }
}

/** Sliding drawer object (opened by animating .position.z). */
function drawer(c: PropCtx, w: number, h: number, d: number, x: number, y: number, z: number, key?: string) {
  const g = new THREE.Group();
  const front = new THREE.Mesh(box(w - 0.01, h - 0.012, 0.025, 0, 0, 0), c.wood);
  const inset = new THREE.Mesh(box(w - 0.08, h - 0.07, 0.008, 0, 0, 0.016), M.trim(0x3a2414));
  const tray = new THREE.Mesh(mergeSmall([box(w - 0.04, 0.012, d - 0.04, 0, -h / 2 + 0.02, -d / 2), box(0.012, h * 0.6, d - 0.04, -w / 2 + 0.03, -h * 0.15, -d / 2), box(0.012, h * 0.6, d - 0.04, w / 2 - 0.03, -h * 0.15, -d / 2), box(w - 0.04, h * 0.6, 0.012, 0, -h * 0.15, -d + 0.03)]), c.wood);
  const hd = new THREE.Mesh(handle(M.brass(), 0, 0, 0.012), M.brass());
  for (const m of [front, inset, tray, hd]) { m.castShadow = true; g.add(m); }
  g.position.set(x, y, z);
  g.userData.closedZ = z;
  g.userData.openZ = z + d * 0.62;
  c.dyn(g);
  if (key) c.part(key, g);
  return g;
}

function drawersFor(c: PropCtx, w: number, h: number, d: number, y: number) {
  const its = c.interactables.filter((i) => i.propId === c.p.id && i.kind === 'drawer').sort((a, b) => (a.slot ?? 0) - (b.slot ?? 0));
  const n = Math.max(1, its.length);
  const dw = (w - 0.06) / n;
  its.forEach((it, i) => drawer(c, dw, h, d - 0.06, -w / 2 + 0.03 + dw * (i + 0.5), y, d / 2 - 0.012, it.id));
  return its.length;
}

// ---------------------------------------------------------------- builders

type Builder = (c: PropCtx) => void;

const B: Partial<Record<PropData['type'], Builder>> = {
  dresser(c) {
    const [w, d, h] = fp('dresser');
    carcass(c, w, h, d);
    const its = drawersFor(c, w, 0.2, d, h * 0.7);
    // decorative lower drawers
    for (let r = 0; r < 2; r++) {
      const y = 0.18 + r * 0.22;
      c.add(c.wood, box(w - 0.07, 0.19, 0.02, 0, y + 0.1, d / 2 - 0.01));
      c.add(M.brass(), handle(M.brass(), -w / 4, y + 0.1, d / 2));
      c.add(M.brass(), handle(M.brass(), w / 4, y + 0.1, d / 2));
    }
    if (!its) c.add(c.wood, box(w - 0.07, 0.2, 0.02, 0, h * 0.7, d / 2 - 0.01));
    if (c.p.variant % 2 === 0) {
      // trinkets on top
      c.add(M.porcelain(), lathe([[0, 0], [0.05, 0.01], [0.07, 0.08], [0.04, 0.18], [0.06, 0.24], [0, 0.25]], 10, h));
      c.add(M.paper(), boxB(0.18, 0.04, 0.24, -w / 3, h, 0, ));
    }
  },
  nightstand(c) {
    const [w, d, h] = fp('nightstand');
    carcass(c, w, h, d);
    if (!drawersFor(c, w, 0.16, d, h - 0.13)) c.add(c.wood, box(w - 0.05, 0.15, 0.02, 0, h - 0.13, d / 2));
    c.add(c.wood, box(w - 0.05, 0.25, 0.02, 0, 0.24, d / 2 - 0.01));
    c.add(M.brass(), knob(0, 0.24, d / 2));
  },
  desk(c) {
    const [w, d, h] = fp('desk');
    c.add(c.wood, boxB(w, 0.04, d, 0, h - 0.04, 0));
    for (const sx of [-1, 1]) for (const sz of [-1, 1]) c.add(c.wood, lathe([[0.03, 0], [0.025, 0.1], [0.035, 0.3], [0.025, h - 0.06], [0.03, h - 0.04]], 8).translate(sx * (w / 2 - 0.06), 0, sz * (d / 2 - 0.06)));
    c.add(c.wood, boxB(w * 0.4, 0.14, d - 0.06, w * 0.28, h - 0.18, -0.02));
    c.add(c.wood, boxB(w - 0.1, 0.1, 0.02, 0, h - 0.14, -d / 2 + 0.04));
    drawersFor(c, w * 0.4, 0.12, d - 0.04, h - 0.11);
    c.add(M.paper(), boxB(0.3, 0.005, 0.22, -0.3, h, 0.05).rotateY(0.2));
    c.add(M.black(), boxB(0.08, 0.1, 0.08, -0.55, h, -0.15));
  },
  cabinet(c) {
    const [w, d, h] = fp('cabinet');
    carcass(c, w, h, d);
    const has = drawersFor(c, w, 0.16, d, 0.9);
    // glass doors upper, panel doors lower
    for (const s of [-1, 1]) {
      c.add(c.wood, box(w / 2 - 0.04, 0.6, 0.025, s * w / 4, 0.45, d / 2 - 0.01));
      c.add(M.brass(), knob(s * 0.06, 0.6, d / 2));
      c.add(c.wood, box(w / 2 - 0.04, 0.04, 0.03, s * w / 4, 1.2, d / 2 - 0.01));
      c.add(M.glass(), box(w / 2 - 0.1, 0.62, 0.006, s * w / 4, 1.45, d / 2 - 0.01));
    }
    if (!has) c.add(c.wood, box(w - 0.05, 0.15, 0.02, 0, 0.9, d / 2));
    for (let k = 0; k < 4; k++) c.add(M.porcelain(), lathe([[0, 0], [0.04, 0], [0.05, 0.1], [0.03, 0.16], [0, 0.16]], 8).translate(-w / 3 + k * 0.2, 1.3, 0));
  },
  medCabinet(c) {
    const [w, d, h] = fp('medCabinet');
    const white = M.plaster(0xe8e6de);
    c.add(white, boxB(w, h, d, 0, 0, 0));
    c.add(M.glass(), box(w - 0.1, 0.8, 0.01, 0, 1.4, d / 2 + 0.005));
    c.add(M.red(), box(0.2, 0.05, 0.01, 0, 1.75, d / 2 + 0.01));
    c.add(M.red(), box(0.05, 0.2, 0.01, 0, 1.75, d / 2 + 0.01));
    drawersFor(c, w, 0.18, d, 1.1);
    for (let k = 0; k < 6; k++) c.add(M.porcelain(), cyl(0.03, 0.03, 0.12, 8, -0.35 + k * 0.14, 1.3, 0));
  },
  counter(c) {
    const [w, d, h] = fp('counter');
    carcass(c, w, h - 0.04, d, { top: false });
    c.add(M.marble(), boxB(w + 0.04, 0.04, d + 0.03, 0, h - 0.04, 0.01));
    drawersFor(c, w, 0.14, d, h - 0.14);
    for (let k = 0; k < 3; k++) { c.add(c.wood, box(w / 3 - 0.04, 0.55, 0.02, -w / 3 + k * w / 3, 0.38, d / 2)); c.add(M.brass(), knob(-w / 3 + k * w / 3 + 0.2, 0.55, d / 2)); }
  },
  closet(c) {
    const [w, d, h] = fp('closet');
    carcass(c, w, h, d, { plinth: 0.1 });
    c.add(c.wood, boxB(w + 0.08, 0.12, d + 0.06, 0, h - 0.06, 0.01));
    c.add(M.trim(0x2a1a10), boxB(w + 0.1, 0.03, d + 0.08, 0, h + 0.05, 0.01));
    // two hinged doors
    for (const s of [-1, 1]) {
      const pivot = new THREE.Group();
      pivot.position.set(s * (w / 2 - 0.02), 0.1, d / 2);
      const door = new THREE.Group();
      const dw = w / 2 - 0.03;
      const panel = new THREE.Mesh(box(dw, h - 0.22, 0.03, -s * dw / 2, (h - 0.22) / 2, 0), c.wood);
      const in1 = new THREE.Mesh(box(dw - 0.12, (h - 0.22) * 0.42, 0.012, -s * dw / 2, (h - 0.22) * 0.73, 0.018), M.trim(0x3a2414));
      const in2 = new THREE.Mesh(box(dw - 0.12, (h - 0.22) * 0.42, 0.012, -s * dw / 2, (h - 0.22) * 0.26, 0.018), M.trim(0x3a2414));
      const kn = new THREE.Mesh(knob(-s * (dw - 0.06), 1.0, 0.015), M.brass());
      for (const m of [panel, in1, in2, kn]) { m.castShadow = true; door.add(m); }
      // slats so you can peek out
      for (let k = 0; k < 5; k++) door.add(new THREE.Mesh(box(dw - 0.16, 0.02, 0.01, -s * dw / 2, (h - 0.22) * 0.6 + k * 0.06, 0.03), M.black()));
      pivot.add(door);
      pivot.userData.side = s;
      c.dyn(pivot);
      c.part(`${c.p.id}:door${s > 0 ? 'R' : 'L'}`, pivot);
    }
    c.add(M.black(), boxB(w - 0.08, h - 0.25, 0.02, 0, 0.12, -d / 2 + 0.05));
    c.add(M.iron(), cyl(0.012, 0.012, w - 0.1, 6, 0, h - 0.45, -0.05).rotateZ(Math.PI / 2).translate(0, 0, 0));
  },
  locker(c) {
    const [w, d, h] = fp('locker');
    const mat = M.steel();
    c.add(mat, boxB(w, h, 0.02, 0, 0, -d / 2 + 0.01));
    c.add(mat, boxB(0.02, h, d, -w / 2 + 0.01, 0, 0));
    c.add(mat, boxB(0.02, h, d, w / 2 - 0.01, 0, 0));
    c.add(mat, boxB(w, 0.02, d, 0, h - 0.02, 0));
    c.add(mat, boxB(w, 0.06, d, 0, 0, 0));
    const pivot = new THREE.Group();
    pivot.position.set(w / 2 - 0.02, 0.06, d / 2);
    const door = new THREE.Mesh(box(w - 0.04, h - 0.1, 0.02, -(w - 0.04) / 2, (h - 0.1) / 2, 0), M.steel());
    door.castShadow = true;
    pivot.add(door);
    for (let k = 0; k < 6; k++) pivot.add(new THREE.Mesh(box(w * 0.5, 0.015, 0.01, -(w - 0.04) / 2, h - 0.4 - k * 0.04, 0.012), M.black()));
    pivot.add(new THREE.Mesh(box(0.03, 0.12, 0.03, -w + 0.12, 1.0, 0.02), M.iron()));
    pivot.userData.side = 1;
    c.dyn(pivot);
    c.part(`${c.p.id}:doorR`, pivot);
  },
  bed(c) {
    const [w, d] = fp('bed');
    const frame = c.wood;
    c.add(frame, boxB(w, 0.28, d - 0.1, 0, 0.12, 0));
    for (const sx of [-1, 1]) for (const sz of [-1, 1]) c.add(frame, boxB(0.08, 0.14, 0.08, sx * (w / 2 - 0.05), 0, sz * (d / 2 - 0.08)));
    // headboard with carved panels
    c.add(frame, boxB(w + 0.06, 1.15, 0.07, 0, 0, -d / 2 + 0.035));
    c.add(M.trim(0x2a1a10), boxB(w + 0.1, 0.06, 0.1, 0, 1.15, -d / 2 + 0.035));
    for (let k = 0; k < 3; k++) c.add(M.trim(0x3a2414), box(w / 3 - 0.08, 0.45, 0.02, -w / 3 + k * (w / 3), 0.78, -d / 2 + 0.075));
    c.add(frame, boxB(w + 0.04, 0.55, 0.06, 0, 0, d / 2 - 0.03));
    const sheet = M.fabric(c.p.variant % 2 ? 0xd8d2c4 : 0xc8bca6);
    const blanket = M.fabric([0x6a2020, 0x2a3a5a, 0x4a4a2a, 0x5a3a4a][c.p.variant % 4]);
    c.add(sheet, roundBox(w - 0.08, 0.22, d - 0.2, 0.06, 0, 0.5, -0.02));
    c.add(blanket, roundBox(w - 0.02, 0.06, d * 0.62, 0.025, 0, 0.62, d * 0.15));
    c.add(blanket, box(w + 0.02, 0.3, 0.02, 0, 0.47, d / 2 - 0.12));
    for (const s of [-1, 1]) c.add(sheet, roundBox(w / 2 - 0.15, 0.12, 0.35, 0.05, s * w / 4, 0.66, -d / 2 + 0.3));
    c.add(M.black(), boxB(w - 0.12, 0.01, d - 0.3, 0, 0.12, 0));
  },
  gurney(c) {
    const [w, d, h] = fp('gurney');
    c.add(M.steel(), boxB(w, 0.05, d, 0, h - 0.2, 0));
    for (const sx of [-1, 1]) for (const sz of [-1, 1]) { c.add(M.steel(), cyl(0.02, 0.02, h - 0.2, 6, sx * (w / 2 - 0.05), 0, sz * (d / 2 - 0.05))); c.add(M.rubber(), sphere(0.05, sx * (w / 2 - 0.05), 0.05, sz * (d / 2 - 0.05))); }
    c.add(M.fabric(0xc8ccc4), roundBox(w - 0.06, 0.12, d - 0.1, 0.04, 0, h - 0.09, 0));
    c.add(M.fabric(0xb8c4c0), roundBox(w - 0.1, 0.05, d * 0.5, 0.02, 0, h + 0.0, d * 0.2));
  },
  bookshelf(c) {
    const [w, d, h] = fp('bookshelf');
    carcass(c, w, h, d, { plinth: 0.1 });
    const shelves = 5;
    const sp = spineTex();
    const bookMat = (bookMatCache ??= new THREE.MeshStandardMaterial({ map: sp, roughness: 0.8, name: 'books' }));
    for (let s = 0; s < shelves; s++) {
      const y = 0.1 + s * ((h - 0.2) / shelves);
      c.add(c.wood, boxB(w - 0.04, 0.025, d - 0.03, 0, y, 0.0));
      // row of books with gaps
      let x = -w / 2 + 0.05;
      let k = 0;
      while (x < w / 2 - 0.1) {
        const bw = 0.03 + ((c.p.id.length * 7 + s * 13 + k * 31) % 7) * 0.008;
        const bh = 0.24 + ((s * 5 + k * 11) % 5) * 0.025;
        const lean = (k + s) % 13 === 0 ? 0.25 : 0;
        if ((k * 7 + s * 3) % 17 !== 0) {
          const g = box(bw, bh, d - 0.1, x + bw / 2, y + 0.025 + bh / 2, 0.02);
          const uv = g.attributes.uv as THREE.BufferAttribute;
          const u0 = ((k * 37 + s * 11) % 40) / 40;
          for (let i = 0; i < uv.count; i++) uv.setXY(i, u0 + (uv.getX(i) - x) * 0.6, (uv.getY(i) - y) * 2.5);
          if (lean) g.rotateZ(lean);
          c.add(bookMat, g);
        }
        x += bw + 0.004;
        k++;
      }
    }
  },
  shelf(c) {
    const [w, d, h] = fp('shelf');
    const mat = c.p.variant === 3 ? c.wood : M.iron();
    for (const sx of [-1, 1]) for (const sz of [-1, 1]) c.add(mat, boxB(0.04, h, 0.04, sx * (w / 2 - 0.02), 0, sz * (d / 2 - 0.02)));
    for (let s = 0; s < 4; s++) {
      const y = 0.15 + s * 0.6;
      c.add(c.p.variant === 3 ? c.wood : M.woodPlank(), boxB(w, 0.03, d, 0, y, 0));
      // clutter: boxes, jars, cans
      for (let k = 0; k < 4; k++) {
        const t = (s * 7 + k * 3 + c.p.id.length) % 5;
        const x = -w / 2 + 0.25 + k * (w - 0.5) / 3;
        if (t === 0) c.add(M.cardboard(), boxB(0.3, 0.25, 0.3, x, y + 0.03, 0));
        else if (t === 1) c.add(M.porcelain(), cyl(0.06, 0.06, 0.18, 8, x, y + 0.03, 0));
        else if (t === 2) c.add(M.steel(), cyl(0.05, 0.05, 0.12, 8, x, y + 0.03, 0.05));
        else if (t === 3) c.add(M.paper(), boxB(0.25, 0.08, 0.2, x, y + 0.03, 0));
      }
    }
  },
  bigShelf(c) {
    const [w, d, h] = fp('bigShelf');
    const mat = M.red();
    for (const sx of [-1, 0, 1]) for (const sz of [-1, 1]) c.add(mat, boxB(0.08, h, 0.08, sx * (w / 2 - 0.04), 0, sz * (d / 2 - 0.04)));
    for (let s = 0; s < 4; s++) {
      const y = 0.15 + s * 1.2;
      c.add(M.steel(), boxB(w, 0.05, d, 0, y, 0));
      c.add(mat, boxB(w, 0.08, 0.05, 0, y + 0.02, d / 2 - 0.02));
      c.add(mat, boxB(w, 0.08, 0.05, 0, y + 0.02, -d / 2 + 0.02));
      for (let k = 0; k < 3; k++) {
        if ((s + k + c.p.variant) % 3 === 0) continue;
        const bw = 0.8 + ((s * k) % 3) * 0.15;
        c.add(M.cardboard(), boxB(bw, 0.5 + (k % 2) * 0.2, d - 0.2, -w / 2 + 0.6 + k * 1.2, y + 0.05, 0));
      }
    }
  },
  table(c) {
    const s = c.p.sx !== undefined ? [c.p.sx, c.p.sz ?? 1, 0.78] : fp('table');
    const [w, d, h] = s as number[];
    const top = c.p.variant === 2 ? M.marble() : c.wood;
    c.add(top, boxB(w, 0.04, d, 0, h - 0.04, 0));
    c.add(c.wood, boxB(w - 0.1, 0.08, d - 0.1, 0, h - 0.12, 0));
    for (const sx of [-1, 1]) for (const sz of [-1, 1]) c.add(c.wood, lathe([[0.025, 0], [0.04, 0.05], [0.025, 0.2], [0.035, 0.4], [0.025, h - 0.1], [0.03, h - 0.04]], 8).translate(sx * (w / 2 - 0.07), 0, sz * (d / 2 - 0.07)));
    if (c.p.variant === 1) c.add(M.paper(), boxB(0.25, 0.01, 0.3, 0.1, h, 0));
    else c.add(M.porcelain(), lathe([[0, 0], [0.06, 0], [0.08, 0.06], [0.05, 0.18], [0.07, 0.3], [0, 0.3]], 10, h).translate(-w / 4, 0, 0));
  },
  diningTable(c) {
    const [w, d, h] = fp('diningTable');
    c.add(c.wood, boxB(w, 0.05, d, 0, h - 0.05, 0));
    c.add(M.fabric(0xd8d0c0), box(w * 0.7, 0.003, d + 0.02, 0, h + 0.002, 0));
    for (const sx of [-1, 1]) c.add(c.wood, lathe([[0.25, 0], [0.2, 0.05], [0.06, 0.15], [0.08, 0.4], [0.06, h - 0.1], [0.12, h - 0.05]], 12).translate(sx * (w / 2 - 0.5), 0, 0));
    for (let k = 0; k < 3; k++) {
      const x = -w / 3 + k * w / 3;
      c.add(M.brass(), lathe([[0, 0], [0.06, 0], [0.02, 0.05], [0.015, 0.25], [0.04, 0.3], [0, 0.31]], 8, h).translate(x, 0, 0));
      c.add(M.porcelain(), cyl(0.012, 0.012, 0.12, 6, x, h + 0.31, 0));
      c.add(M.porcelain(), cyl(0.12, 0.1, 0.02, 12, x + 0.4, h, 0.35));
    }
  },
  chair(c) {
    const h = 0.46;
    c.add(c.wood, boxB(0.46, 0.05, 0.44, 0, h, 0));
    c.add(M.fabric(0x5a2020), roundBox(0.42, 0.05, 0.4, 0.02, 0, h + 0.07, 0.01));
    for (const sx of [-1, 1]) for (const sz of [-1, 1]) c.add(c.wood, cyl(0.02, 0.018, h, 6, sx * 0.19, 0, sz * 0.18));
    for (const sx of [-1, 1]) c.add(c.wood, boxB(0.04, 0.55, 0.04, sx * 0.19, h, -0.2));
    c.add(c.wood, box(0.42, 0.12, 0.03, 0, h + 0.47, -0.2));
    c.add(c.wood, box(0.06, 0.4, 0.02, 0, h + 0.25, -0.2));
  },
  sofa(c) {
    const [w, d, h] = fp('sofa');
    const fab = M.fabric([0x6a2a2a, 0x3a4a3a, 0x4a3a2a, 0x2a2a4a][c.p.variant % 4]);
    c.add(c.wood, boxB(w, 0.1, d, 0, 0.06, 0));
    c.add(fab, roundBox(w - 0.1, 0.18, d - 0.2, 0.06, 0, 0.27, 0.05));
    c.add(fab, roundBox(w - 0.1, 0.5, 0.2, 0.08, 0, 0.55, -d / 2 + 0.12));
    for (const s of [-1, 1]) c.add(fab, roundBox(0.16, 0.36, d - 0.05, 0.07, s * (w / 2 - 0.08), 0.36, 0));
    for (let k = 0; k < 3; k++) c.add(fab, roundBox((w - 0.4) / 3 - 0.02, 0.12, d - 0.3, 0.05, -w / 3 + 0.07 + k * ((w - 0.4) / 3 + 0.02) - 0.07, 0.42, 0.08));
    for (const sx of [-1, 1]) for (const sz of [-1, 1]) c.add(c.wood, cyl(0.03, 0.02, 0.06, 6, sx * (w / 2 - 0.08), 0, sz * (d / 2 - 0.08)));
  },
  armchair(c) {
    const fab = M.fabric([0x6a2a2a, 0x3a4a3a, 0x5a4020][c.p.variant % 3]);
    c.add(fab, roundBox(0.8, 0.2, 0.7, 0.06, 0, 0.32, 0.05));
    c.add(fab, roundBox(0.8, 0.6, 0.18, 0.08, 0, 0.6, -0.33));
    for (const s of [-1, 1]) c.add(fab, roundBox(0.14, 0.32, 0.8, 0.06, s * 0.4, 0.4, 0));
    for (const sx of [-1, 1]) for (const sz of [-1, 1]) c.add(c.wood, cyl(0.03, 0.02, 0.22, 6, sx * 0.35, 0, sz * 0.32));
  },
  piano(c) {
    const [w, d, h] = fp('piano');
    const lac = M.black();
    c.add(lac, boxB(w, h, d * 0.55, 0, 0, -d * 0.2));
    c.add(lac, boxB(w, 0.08, d * 0.45, 0, 0.72, d * 0.25));
    c.add(M.porcelain(), boxB(w - 0.1, 0.02, 0.15, 0, 0.8, d * 0.32));
    for (let k = 0; k < 36; k++) if (k % 7 !== 2 && k % 7 !== 6) c.add(lac, boxB(0.012, 0.015, 0.08, -w / 2 + 0.07 + k * ((w - 0.14) / 36), 0.82, d * 0.3));
    for (const s of [-1, 1]) c.add(lac, boxB(0.06, 0.72, 0.06, s * (w / 2 - 0.05), 0, d * 0.4));
    c.add(M.brass(), lathe([[0, 0], [0.06, 0], [0.02, 0.05], [0.015, 0.2], [0, 0.2]], 8, h).translate(-w / 3, 0, -d * 0.2));
  },
  clock(c) {
    const [w, d, h] = fp('clock');
    c.add(c.wood, boxB(w, h * 0.7, d, 0, 0.1, 0));
    c.add(c.wood, boxB(w + 0.06, 0.1, d + 0.04, 0, 0, 0));
    c.add(c.wood, boxB(w + 0.08, 0.5, d + 0.02, 0, h - 0.55, 0));
    c.add(M.paper(), cyl(0.18, 0.18, 0.01, 20, 0, 0, 0).rotateX(Math.PI / 2).translate(0, h - 0.3, d / 2 + 0.012));
    c.add(M.brass(), cyl(0.2, 0.2, 0.012, 20, 0, 0, 0).rotateX(Math.PI / 2).translate(0, h - 0.3, d / 2 + 0.006));
    c.add(M.glass(), box(w - 0.12, h * 0.45, 0.01, 0, h * 0.4, d / 2 + 0.005));
    const pend = new THREE.Group();
    pend.position.set(0, h * 0.62, d / 2 - 0.05);
    pend.add(new THREE.Mesh(box(0.01, 0.6, 0.01, 0, -0.3, 0), M.brass()));
    pend.add(new THREE.Mesh(cyl(0.07, 0.07, 0.015, 14).rotateX(Math.PI / 2).translate(0, -0.6, 0), M.brass()));
    pend.userData.swing = true;
    c.dyn(pend);
    c.part(`${c.p.id}:pendulum`, pend);
  },
  plant(c) {
    c.add(M.porcelain(), lathe([[0, 0], [0.16, 0], [0.2, 0.05], [0.24, 0.35], [0.26, 0.38], [0, 0.38]], 14));
    c.add(M.dirt(), cyl(0.23, 0.23, 0.02, 12, 0, 0.35, 0));
    const leaves = M.leaves();
    for (let k = 0; k < 9; k++) {
      const a = (k / 9) * Math.PI * 2 + c.p.variant;
      const g = new THREE.PlaneGeometry(0.16, 0.7);
      g.translate(0, 0.35, 0);
      g.rotateX(-0.6 - (k % 3) * 0.2);
      g.rotateY(a);
      g.translate(0, 0.38, 0);
      metricUV(g);
      c.add(leaves, g);
    }
    c.add(M.bark(), cyl(0.02, 0.03, 0.8, 6, 0, 0.38, 0));
  },
  lamp(c) {
    const on = !c.room.dark;
    const base = new THREE.Group();
    base.add(new THREE.Mesh(lathe([[0, 0], [0.08, 0], [0.09, 0.02], [0.03, 0.05], [0.02, 0.25], [0.04, 0.3], [0, 0.3]], 12), M.brass()));
    const shade = new THREE.Mesh(lathe([[0.16, 0.25], [0.09, 0.45]], 16), M.shade());
    base.add(shade);
    const bulb = new THREE.Mesh(sphere(0.03, 0, 0.33, 0), M.bulb(on));
    base.add(bulb);
    c.dyn(base);
    c.fixture(bulb);
  },
  floorLamp(c) {
    const on = !c.room.dark;
    c.add(M.brass(), lathe([[0, 0], [0.18, 0], [0.18, 0.02], [0.03, 0.06], [0.015, 1.4], [0, 1.4]], 12));
    const shade = new THREE.Mesh(lathe([[0.25, 1.35], [0.15, 1.65]], 16), M.shade(0xc8a878));
    c.dyn(shade);
    const bulb = new THREE.Mesh(sphere(0.04, 0, 1.45, 0), M.bulb(on));
    c.dyn(bulb);
    c.fixture(bulb);
  },
  painting(c) {
    const w = 0.7 + (c.p.variant % 3) * 0.15, h = w * 1.25;
    const frame = M.trim(0x6a4a1a);
    const gilt = M.brass();
    c.add(frame, box(w + 0.12, h + 0.12, 0.05, 0, 0, 0.025));
    c.add(gilt, box(w + 0.04, h + 0.04, 0.055, 0, 0, 0.03));
    const tex = paintingTex(c.p.variant + c.room.index * 3);
    const mat = new THREE.MeshStandardMaterial({ map: tex, roughness: 0.55 });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), mat);
    m.position.z = 0.06;
    c.dyn(m);
  },
  mirror(c) {
    c.add(M.brass(), box(0.7, 1.0, 0.04, 0, 0, 0.02));
    c.add(M.mirror(), box(0.6, 0.9, 0.01, 0, 0, 0.045));
  },
  wallLight(c) {
    const on = !c.room.dark;
    c.add(M.brass(), box(0.12, 0.2, 0.03, 0, 0, 0.015));
    c.add(M.brass(), tube([new THREE.Vector3(0, 0, 0.02), new THREE.Vector3(0, 0.05, 0.15), new THREE.Vector3(0, 0.1, 0.22)], 0.012, 8, 6));
    const shade = new THREE.Mesh(lathe([[0.09, 0.0], [0.05, 0.14]], 12), M.shade(0xe0c090));
    shade.position.set(0, 0.08, 0.22);
    c.dyn(shade);
    const bulb = new THREE.Mesh(sphere(0.025, 0, 0.15, 0.22), M.bulb(on));
    c.dyn(bulb);
    c.fixture(bulb);
  },
  ceilingLight(c) {
    const on = !c.room.dark;
    if (c.p.variant === 2) {
      // industrial pendant
      c.add(M.iron(), cyl(0.01, 0.01, 0.8, 4, 0, -0.8, 0));
      const shade = new THREE.Mesh(lathe([[0.02, 0], [0.25, -0.15], [0.3, -0.22]], 16), M.iron());
      shade.position.y = -0.8;
      c.dyn(shade);
      const bulb = new THREE.Mesh(sphere(0.05, 0, -0.95, 0), M.bulb(on, 0xdce8ff));
      c.dyn(bulb); c.fixture(bulb);
      return;
    }
    c.add(M.brass(), cyl(0.1, 0.12, 0.04, 14, 0, -0.04, 0));
    c.add(M.brass(), cyl(0.008, 0.008, 0.3, 4, 0, -0.34, 0));
    const glass = new THREE.Mesh(lathe([[0.04, 0], [0.18, -0.06], [0.2, -0.14], [0.12, -0.22], [0, -0.23]], 16), M.bulb(on, c.p.variant === 1 ? 0x8fb0ff : 0xffd8a0));
    glass.position.y = -0.34;
    c.dyn(glass);
    c.fixture(glass);
  },
  chandelier(c) {
    const on = !c.room.dark;
    const grp = new THREE.Group();
    const arms = 6 + c.p.variant * 2;
    const drop = 0.9;
    grp.add(new THREE.Mesh(cyl(0.008, 0.008, drop * 0.5, 4, 0, -drop * 0.5, 0), M.brass()));
    grp.add(new THREE.Mesh(lathe([[0, 0], [0.12, -0.05], [0.2, -0.2], [0.06, -0.35], [0.1, -0.45], [0, -0.55]], 14).translate(0, -drop * 0.5, 0), M.brass()));
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.45, 0.015, 6, 32).rotateX(Math.PI / 2).translate(0, -drop * 0.5 - 0.35, 0), M.brass());
    grp.add(ring);
    for (let k = 0; k < arms; k++) {
      const a = (k / arms) * Math.PI * 2;
      const x = Math.cos(a) * 0.45, z = Math.sin(a) * 0.45;
      grp.add(new THREE.Mesh(tube([new THREE.Vector3(0, -drop * 0.5 - 0.3, 0), new THREE.Vector3(x * 0.6, -drop * 0.5 - 0.42, z * 0.6), new THREE.Vector3(x, -drop * 0.5 - 0.3, z)], 0.01, 8, 4), M.brass()));
      grp.add(new THREE.Mesh(cyl(0.025, 0.03, 0.06, 8, x, -drop * 0.5 - 0.32, z), M.brass()));
      const candle = new THREE.Mesh(cyl(0.012, 0.012, 0.1, 6, x, -drop * 0.5 - 0.26, z), M.porcelain());
      grp.add(candle);
      const flame = new THREE.Mesh(sphere(0.02, x, -drop * 0.5 - 0.13, z, 6, 4).scale(1, 1.6, 1), M.bulb(on, 0xffb060));
      grp.add(flame);
      c.fixture(flame);
      // crystal drops
      if (c.quality > 1) grp.add(new THREE.Mesh(new THREE.OctahedronGeometry(0.02).translate(x * 0.8, -drop * 0.5 - 0.5, z * 0.8), M.glass()));
    }
    grp.traverse((o) => { (o as THREE.Mesh).castShadow = false; });
    c.dyn(grp);
    if (c.p.data === 1) c.part(`${c.p.id}:chandelier`, grp);
  },
  fireplace(c) {
    const [w, d, h] = fp('fireplace');
    const st = M.marble();
    c.add(st, boxB(w, 0.1, d + 0.2, 0, 0, 0.1));
    for (const s of [-1, 1]) c.add(st, boxB(0.35, h - 0.2, d, s * (w / 2 - 0.175), 0.1, 0));
    c.add(st, boxB(w + 0.1, 0.12, d + 0.12, 0, h - 0.12, 0.04));
    c.add(st, boxB(w - 0.7, 0.35, d, 0, h - 0.47, 0));
    c.add(M.brick(), boxB(w - 0.7, h - 0.57, 0.05, 0, 0.1, -d / 2 + 0.05));
    c.add(M.black(), boxB(w - 0.72, 0.02, d - 0.1, 0, 0.1, 0));
    c.add(M.iron(), boxB(0.7, 0.08, 0.3, 0, 0.12, 0));
    for (let k = 0; k < 3; k++) c.add(M.bark(), cyl(0.05, 0.05, 0.55, 6, 0, 0, 0).rotateZ(Math.PI / 2).translate(-0.05 + k * 0.03, 0.25 + k * 0.08, -0.05 + k * 0.08));
    c.add(M.brass(), lathe([[0, 0], [0.05, 0], [0.03, 0.2], [0.05, 0.25], [0, 0.26]], 8, h).translate(-w / 3, 0, 0));
    c.add(M.brass(), lathe([[0, 0], [0.05, 0], [0.03, 0.2], [0.05, 0.25], [0, 0.26]], 8, h).translate(w / 3, 0, 0));
  },
  rug(c) {
    const w = c.p.sx ?? 2, d = c.p.sz ?? 1.5;
    const tex = canvasTex(256, 256, (ctx) => {
      const tint = ['#5a1a1a', '#1a2a4a', '#3a2a1a', '#4a1a2a'][c.p.variant % 4];
      ctx.fillStyle = tint; ctx.fillRect(0, 0, 256, 256);
      ctx.strokeStyle = 'rgba(200,160,80,0.7)'; ctx.lineWidth = 8; ctx.strokeRect(14, 14, 228, 228);
      ctx.lineWidth = 3; ctx.strokeRect(30, 30, 196, 196);
      ctx.fillStyle = 'rgba(200,160,80,0.35)';
      for (let k = 0; k < 6; k++) { ctx.beginPath(); ctx.ellipse(128, 128, 90 - k * 14, 50 - k * 7, k * 0.5, 0, Math.PI * 2); ctx.fill(); }
      for (let k = 0; k < 4000; k++) { ctx.fillStyle = `rgba(0,0,0,${Math.random() * 0.12})`; ctx.fillRect(Math.random() * 256, Math.random() * 256, 2, 2); }
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(w, d).rotateX(-Math.PI / 2).translate(0, 0.006, 0), new THREE.MeshStandardMaterial({ map: tex, roughness: 1 }));
    m.receiveShadow = true;
    c.dyn(m);
  },
  pillar(c) {
    const h = c.p.sy ?? 3;
    const st = M.marble();
    c.add(st, boxB(0.62, 0.2, 0.62, 0, 0, 0));
    c.add(st, lathe([[0.26, 0], [0.24, 0.1], [0.24, h - 0.5], [0.28, h - 0.4]], 16, 0.2));
    c.add(st, boxB(0.66, 0.24, 0.66, 0, h - 0.24, 0));
  },
  stairs(c) {
    const w = c.p.sx ?? 3, L = c.p.sz ?? 4, rise = c.p.data ?? 2.8;
    const steps = Math.max(4, Math.round(Math.abs(rise) / 0.18));
    const sd = L / steps;
    const tread = c.p.variant === 1 ? M.marble() : M.floor('carpet', 0x5a1e1e);
    for (let s = 0; s < steps; s++) {
      const top = rise > 0 ? ((s + 1) / steps) * rise : ((s + 1) / steps) * rise;
      const bottom = Math.min(0, rise) - 0.02;
      const z = s * sd + sd / 2;
      const x0 = c.p.variant === 1 ? 0 : 0;
      c.add(c.wood, boxB(w, top - bottom - 0.03, sd, x0, bottom, z));
      c.add(tread, boxB(w - 0.1, 0.03, sd + 0.01, x0, top - 0.03, z));
      c.add(M.brass(), box(w - 0.1, 0.012, 0.02, x0, top, z + sd / 2 - 0.02));
    }
  },
  railing(c) {
    const L = c.p.sz ?? 4, rise = c.p.data ?? 0;
    const n = Math.max(3, Math.round(L / 0.3));
    for (let k = 0; k <= n; k++) {
      const t = k / n, z = -L / 2 + t * L, y = rise * t;
      c.add(c.wood, lathe([[0.025, 0], [0.035, 0.1], [0.02, 0.3], [0.03, 0.6], [0.02, 0.9]], 6, y).translate(0, 0, z));
    }
    const g = new THREE.CylinderGeometry(0.035, 0.035, Math.hypot(L, rise), 8);
    g.rotateX(Math.PI / 2 - Math.atan2(rise, L) * 0);
    const m = new THREE.Matrix4().makeRotationX(-Math.atan2(rise, L));
    g.applyMatrix4(m);
    g.translate(0, rise / 2 + 0.92, 0);
    c.add(c.wood, g);
  },
  crate(c) {
    const s = 1.0;
    const pl = M.woodPlank();
    c.add(pl, boxB(s - 0.04, s - 0.04, s - 0.04, 0, 0.02, 0));
    for (const y of [0.02, s - 0.1]) for (const sz of [-1, 1]) c.add(c.wood, boxB(s, 0.08, 0.04, 0, y, sz * (s / 2 - 0.02)));
    for (const sx of [-1, 1]) for (const sz of [-1, 1]) c.add(c.wood, boxB(0.08, s, 0.08, sx * (s / 2 - 0.04), 0, sz * (s / 2 - 0.04)));
    c.add(c.wood, box(0.06, 1.2, 0.03, 0, s / 2, s / 2).rotateZ(0.78).translate(0, 0, 0));
  },
  boxStack(c) {
    const cb = M.cardboard();
    c.add(cb, boxB(1.1, 0.55, 1.1, 0, 0, 0));
    c.add(cb, boxB(0.9, 0.5, 0.8, 0.05, 0.55, -0.05));
    c.add(cb, boxB(0.6, 0.45, 0.6, -0.1, 1.05, 0.1));
    c.add(M.paper(), box(1.12, 0.06, 0.01, 0, 0.3, 0.556));
  },
  barrel(c) {
    c.add(c.p.variant % 2 ? M.rust() : c.wood, lathe([[0.3, 0], [0.34, 0.2], [0.36, 0.5], [0.34, 0.8], [0.3, 1.0], [0, 1.0]], 16));
    for (const y of [0.15, 0.5, 0.85]) c.add(M.iron(), new THREE.TorusGeometry(0.345, 0.012, 4, 20).rotateX(Math.PI / 2).translate(0, y, 0));
  },
  pallet(c) {
    for (let k = 0; k < 5; k++) c.add(M.woodPlank(), boxB(1.2, 0.03, 0.18, 0, 0.12, -0.5 + k * 0.25));
    for (const s of [-1, 0, 1]) c.add(c.wood, boxB(0.12, 0.12, 1.2, s * 0.5, 0, 0));
  },
  cart(c) {
    c.add(M.steel(), boxB(0.7, 0.04, 1.1, 0, 0.3, 0));
    c.add(M.steel(), boxB(0.7, 0.04, 1.1, 0, 0.8, 0));
    for (const sx of [-1, 1]) for (const sz of [-1, 1]) { c.add(M.steel(), cyl(0.015, 0.015, 0.8, 6, sx * 0.33, 0.1, sz * 0.53)); c.add(M.rubber(), sphere(0.05, sx * 0.3, 0.05, sz * 0.5)); }
    c.add(M.cardboard(), boxB(0.5, 0.3, 0.5, 0, 0.84, 0.2));
  },
  generator(c) {
    const [w, d, h] = fp('generator');
    c.add(M.red(), boxB(w, h * 0.7, d, 0, 0.1, 0));
    c.add(M.iron(), boxB(w + 0.1, 0.1, d + 0.1, 0, 0, 0));
    c.add(M.iron(), cyl(0.2, 0.2, w * 0.6, 12).rotateZ(Math.PI / 2).translate(0, h * 0.85, 0));
    for (let k = 0; k < 6; k++) c.add(M.black(), box(0.02, h * 0.4, d + 0.01, -w / 2 + 0.2 + k * 0.1, h * 0.45, 0));
    c.add(M.emissive(0x30ff40, 1.5), box(0.04, 0.04, 0.01, w / 3, h * 0.6, d / 2 + 0.005));
  },
  electricPanel(c) {
    // the high-voltage enclosure walls are colliders; dress them with mesh panels & signage
    const w = c.p.sx ?? 7, d = c.p.sz ?? 8;
    const mat = M.steel();
    const doorZ = (c.p.data ?? 0) - c.p.z;
    c.add(mat, box(0.12, 4, d, 0, 2, 0).translate(0, 0, 0));
    const sign = canvasTex(256, 128, (ctx) => {
      ctx.fillStyle = '#e8c020'; ctx.fillRect(0, 0, 256, 128);
      ctx.fillStyle = '#111'; ctx.font = 'bold 30px sans-serif'; ctx.textAlign = 'center';
      ctx.fillText('DANGER', 128, 46); ctx.font = 'bold 22px sans-serif'; ctx.fillText('HIGH VOLTAGE', 128, 84);
      ctx.beginPath(); ctx.moveTo(20, 110); ctx.lineTo(236, 110); ctx.lineWidth = 6; ctx.stroke();
    });
    const sm = new THREE.Mesh(new THREE.PlaneGeometry(0.8, 0.4), new THREE.MeshStandardMaterial({ map: sign, roughness: 0.6 }));
    sm.rotation.y = Math.PI / 2;
    sm.position.set(0.07, 2.8, doorZ + 1.2);
    c.dyn(sm);
    void w;
  },
  breakerBox(c) {
    const [w, d, h] = fp('breakerBox');
    c.add(M.steel(), boxB(w, h, d, 0, 0.6, 0));
    c.add(M.iron(), box(w - 0.1, h - 0.1, 0.01, 0, 0.6 + h / 2, d / 2 + 0.003));
    const grp = new THREE.Group();
    grp.position.set(0, 0.6, d / 2 + 0.01);
    c.dyn(grp);
    c.part(`${c.p.id}:panel`, grp);
  },
  receptionDesk(c) {
    const [w, d, h] = fp('receptionDesk');
    c.add(c.wood, boxB(w, h, 0.08, 0, 0, d / 2 - 0.04));
    for (let k = 0; k < 5; k++) c.add(M.trim(0x3a2414), box(w / 5 - 0.12, h - 0.35, 0.02, -w / 2 + w / 10 + k * w / 5, h / 2, d / 2 + 0.005));
    c.add(M.marble(), boxB(w + 0.1, 0.05, 0.4, 0, h, d / 2 - 0.15));
    c.add(c.wood, boxB(w, 0.75, d - 0.1, 0, 0, -0.05));
    c.add(c.wood, boxB(0.08, h, d, -w / 2 + 0.04, 0, 0));
    c.add(c.wood, boxB(0.08, h, d, w / 2 - 0.04, 0, 0));
    c.add(M.brass(), lathe([[0, 0], [0.06, 0], [0.06, 0.01], [0.045, 0.04], [0.01, 0.07], [0, 0.08]], 12, h + 0.05).translate(w / 3, 0, d / 2 - 0.15));
    c.add(M.paper(), boxB(0.4, 0.04, 0.3, -w / 4, h + 0.05, d / 2 - 0.15));
    drawersFor(c, w - 0.4, 0.14, d - 0.2, 0.68);
  },
  shopCounter(c) {
    const [w, d, h] = fp('shopCounter');
    c.add(c.wood, boxB(w, h - 0.05, d, 0, 0, 0));
    c.add(M.glass(), box(w - 0.2, 0.5, 0.01, 0, h * 0.55, d / 2 + 0.01));
    c.add(M.marble(), boxB(w + 0.08, 0.05, d + 0.08, 0, h - 0.05, 0));
    c.add(M.brass(), lathe([[0, 0], [0.12, 0], [0.12, 0.2], [0, 0.2]], 4, h).translate(w / 3, 0, 0));
    const sign = canvasTex(512, 128, (ctx) => {
      ctx.fillStyle = '#2a1408'; ctx.fillRect(0, 0, 512, 128);
      ctx.strokeStyle = '#d8a848'; ctx.lineWidth = 6; ctx.strokeRect(8, 8, 496, 112);
      ctx.fillStyle = '#f0c868'; ctx.font = 'bold 56px Georgia, serif'; ctx.textAlign = 'center'; ctx.fillText("JEFF'S SHOP", 256, 84);
    });
    const sm = new THREE.Mesh(new THREE.PlaneGeometry(2.4, 0.6), new THREE.MeshStandardMaterial({ map: sign, emissive: 0x402010, emissiveMap: sign, emissiveIntensity: 1.2 }));
    sm.position.set(0, 2.5, -0.6);
    c.dyn(sm);
  },
  padlockDesk(c) {
    c.add(c.wood, boxB(0.9, 0.9, 0.5, 0, 0, 0));
    c.add(M.paper(), box(0.5, 0.01, 0.35, 0, 0.91, 0));
  },
  sink(c) {
    c.add(M.porcelain(), lathe([[0.08, 0], [0.1, 0.7], [0.3, 0.78], [0.32, 0.86], [0, 0.82]], 16).scale(1.1, 1, 0.75));
    c.add(M.steel(), tube([new THREE.Vector3(0, 0.86, -0.2), new THREE.Vector3(0, 1.0, -0.15), new THREE.Vector3(0, 0.98, 0)], 0.015, 8, 6));
  },
  toilet(c) {
    c.add(M.porcelain(), lathe([[0.1, 0], [0.16, 0.2], [0.2, 0.38], [0.18, 0.42], [0, 0.42]], 16).scale(1, 1, 1.3).translate(0, 0, 0.05));
    c.add(M.porcelain(), boxB(0.42, 0.4, 0.18, 0, 0.42, -0.25));
    c.add(c.wood, roundBox(0.38, 0.03, 0.46, 0.012, 0, 0.44, 0.06));
  },
  bathtub(c) {
    c.add(M.porcelain(), roundBox(0.8, 0.55, 1.8, 0.12, 0, 0.32, 0));
    c.add(M.water(), box(0.66, 0.01, 1.62, 0, 0.5, 0));
    for (const sx of [-1, 1]) for (const sz of [-1, 1]) c.add(M.brass(), sphere(0.05, sx * 0.32, 0.04, sz * 0.75));
  },
  stove(c) {
    c.add(M.iron(), boxB(0.9, 0.9, 0.7, 0, 0, 0));
    c.add(M.black(), box(0.6, 0.35, 0.01, 0, 0.42, 0.355));
    for (const sx of [-1, 1]) for (const sz of [-1, 1]) c.add(M.black(), cyl(0.1, 0.1, 0.02, 12, sx * 0.2, 0.9, sz * 0.15));
    c.add(M.iron(), boxB(0.9, 0.5, 0.06, 0, 0.9, -0.32));
  },
  fridge(c) {
    c.add(M.porcelain(), roundBox(0.9, 1.9, 0.75, 0.06, 0, 0.95, 0));
    c.add(M.steel(), box(0.04, 0.4, 0.04, 0.38, 1.2, 0.39));
    c.add(M.black(), box(0.88, 0.01, 0.02, 0, 1.3, 0.38));
  },
  fountain(c) {
    const st = M.stone();
    c.add(st, lathe([[1.7, 0], [1.7, 0.55], [1.55, 0.6], [1.5, 0.15], [0, 0.15]], 28));
    c.add(M.water(), cyl(1.5, 1.5, 0.02, 28, 0, 0.45, 0));
    c.add(st, lathe([[0.3, 0], [0.18, 0.3], [0.15, 1.0], [0.7, 1.15], [0.7, 1.25], [0, 1.2]], 18));
    c.add(st, lathe([[0.1, 1.2], [0.08, 1.7], [0.3, 1.8], [0, 1.9]], 12));
  },
  hedge(c) {
    const [w, d, h] = fp('hedge');
    c.add(M.leaves(), roundBox(w, h, d, 0.3, 0, h / 2, 0));
    c.add(M.dirt(), boxB(w + 0.1, 0.1, d + 0.1, 0, 0, 0));
  },
  tree(c) {
    c.add(M.bark(), cyl(0.12, 0.22, 3.2, 10));
    for (let k = 0; k < 6; k++) {
      const a = k * 1.1 + c.p.variant, r = 0.6 + (k % 3) * 0.3;
      c.add(M.leaves(), new THREE.IcosahedronGeometry(1.0 - (k % 2) * 0.25, 1).translate(Math.cos(a) * r, 3.2 + (k % 3) * 0.5, Math.sin(a) * r));
    }
    for (let k = 0; k < 4; k++) c.add(M.bark(), tube([new THREE.Vector3(0, 2.2, 0), new THREE.Vector3(Math.cos(k * 1.6) * 0.6, 2.8, Math.sin(k * 1.6) * 0.6), new THREE.Vector3(Math.cos(k * 1.6) * 1.1, 3.3, Math.sin(k * 1.6) * 1.1)], 0.05, 8, 5));
  },
  bench(c) {
    for (let k = 0; k < 4; k++) c.add(M.woodPlank(), boxB(1.6, 0.03, 0.1, 0, 0.45, -0.18 + k * 0.12));
    for (let k = 0; k < 3; k++) c.add(M.woodPlank(), box(1.6, 0.1, 0.03, 0, 0.6 + k * 0.13, -0.25).rotateX(-0.15));
    for (const s of [-1, 1]) { c.add(M.iron(), boxB(0.05, 0.45, 0.5, s * 0.7, 0, 0)); c.add(M.iron(), box(0.05, 0.5, 0.05, s * 0.7, 0.7, -0.26)); }
  },
  planter(c) {
    const [w, d, h] = fp('planter');
    c.add(M.woodPlank(), boxB(w, h, d, 0, 0, 0));
    c.add(M.dirt(), boxB(w - 0.1, 0.02, d - 0.1, 0, h - 0.05, 0));
    for (let k = 0; k < 6; k++) c.add(M.leaves(), new THREE.IcosahedronGeometry(0.22 + (k % 2) * 0.1, 0).translate(-w / 2 + 0.25 + k * (w - 0.5) / 5, h + 0.12, ((k * 7) % 3 - 1) * 0.2));
  },
  statue(c) {
    const st = M.marble();
    c.add(st, boxB(0.8, 0.8, 0.8, 0, 0, 0));
    c.add(st, lathe([[0.2, 0], [0.16, 0.3], [0.22, 0.7], [0.15, 1.0], [0.06, 1.05], [0.1, 1.15], [0.12, 1.3], [0, 1.38]], 14, 0.8));
    c.add(st, sphere(0.13, 0, 2.05, 0.02));
  },
  lamppost(c) {
    c.add(M.iron(), lathe([[0.15, 0], [0.12, 0.2], [0.05, 0.3], [0.04, 3.2], [0.08, 3.25], [0, 3.3]], 10));
    const glass = new THREE.Mesh(lathe([[0.05, 0], [0.16, 0.1], [0.14, 0.4], [0.05, 0.45], [0, 0.46]], 6).translate(0, 3.2, 0), M.bulb(true, 0xffc070));
    c.dyn(glass);
    c.fixture(glass);
    c.add(M.iron(), lathe([[0.18, 0], [0.04, 0.12], [0, 0.14]], 6, 3.66));
  },
  gazebo(c) {
    for (const [x, z] of [[-2, -2], [2, -2], [-2, 2], [2, 2]]) c.add(c.wood, cyl(0.12, 0.12, 3.2, 8, x, 0, z));
    c.add(c.wood, boxB(4.6, 0.1, 4.6, 0, 3.2, 0));
    c.add(M.woodDark(), new THREE.ConeGeometry(3.6, 1.6, 4).rotateY(Math.PI / 4).translate(0, 4.1, 0));
    c.add(M.woodPlank(), boxB(4.4, 0.12, 4.4, 0, 0, 0));
  },
  wheelbarrow(c) {
    c.add(M.rust(), boxB(0.6, 0.3, 0.9, 0, 0.35, 0.1));
    c.add(M.rubber(), cyl(0.18, 0.18, 0.08, 12).rotateZ(Math.PI / 2).translate(0, 0.18, 0.6));
    for (const s of [-1, 1]) c.add(M.iron(), box(0.03, 0.03, 1.2, s * 0.25, 0.4, -0.2));
  },
  woodPile(c) {
    for (let k = 0; k < 9; k++) c.add(M.bark(), cyl(0.08, 0.08, 1.3, 7).rotateZ(Math.PI / 2).translate(0, 0.08 + Math.floor(k / 4) * 0.15, -0.3 + (k % 4) * 0.18));
  },
  pot(c) {
    c.add(M.brick(), lathe([[0.18, 0], [0.32, 0.6], [0.34, 0.65], [0, 0.62]], 14));
    c.add(M.leaves(), new THREE.IcosahedronGeometry(0.35, 1).translate(0, 0.85, 0));
  },
  trellis(c) {
    for (let k = 0; k < 5; k++) c.add(c.wood, box(0.04, 2, 0.04, -0.6 + k * 0.3, 1, 0));
    for (let k = 0; k < 5; k++) c.add(c.wood, box(1.3, 0.04, 0.04, 0, 0.3 + k * 0.4, 0));
  },
  vines(c) {
    const h = c.p.sy ?? 4;
    for (let k = 0; k < 4; k++) {
      const pts: THREE.Vector3[] = [];
      for (let s = 0; s <= 8; s++) pts.push(new THREE.Vector3(Math.sin(s * 1.3 + k) * 0.3 + (k - 1.5) * 0.25, h - (s / 8) * h * 0.9, 0.05 + Math.abs(Math.cos(s + k)) * 0.06));
      c.add(M.bark(), tube(pts, 0.02, 24, 5));
      for (let s = 0; s < 8; s++) c.add(M.leaves(), new THREE.PlaneGeometry(0.15, 0.12).rotateY(s).translate(pts[s].x, pts[s].y, pts[s].z + 0.05));
    }
  },
  debris(c) {
    c.add(M.woodPlank(), boxB(1.0, 0.04, 0.12, 0, 0.02, 0).rotateY(0.4));
    c.add(M.woodPlank(), boxB(0.8, 0.04, 0.12, 0.1, 0.06, 0.2).rotateY(-0.7));
    c.add(M.plaster(), new THREE.DodecahedronGeometry(0.15).translate(-0.3, 0.1, 0.2));
    c.add(M.plaster(), new THREE.DodecahedronGeometry(0.1).translate(0.3, 0.07, -0.25));
    c.add(c.wood, boxB(0.5, 0.3, 0.05, -0.1, 0.0, -0.2).rotateX(-1.2));
  },
  bookPile(c) {
    for (let k = 0; k < 6; k++) c.add(M.trim([0x5a1a1a, 0x1a3a2a, 0x3a2a1a][k % 3]), boxB(0.25, 0.05, 0.18, 0, k * 0.05, 0).rotateY(k * 0.3));
  },
  skeleton(c) {
    const bone = M.porcelain();
    c.add(bone, sphere(0.1, 0, 0.1, -0.7));
    for (let k = 0; k < 8; k++) c.add(bone, cyl(0.012, 0.012, 0.3, 5).rotateZ(Math.PI / 2).translate(0, 0.08, -0.45 + k * 0.05));
    for (const s of [-1, 1]) { c.add(bone, cyl(0.02, 0.02, 0.45, 5).rotateX(Math.PI / 2).translate(s * 0.1, 0.05, 0.1)); c.add(bone, cyl(0.02, 0.02, 0.4, 5).rotateX(Math.PI / 2).translate(s * 0.13, 0.05, 0.5)); }
  },
  fallenShelf(c) {
    const w = c.p.sx ?? 5;
    const g = new THREE.Group();
    const body = new THREE.Mesh(boxB(w - 0.1, 0.45, 2.2, 0, 0, 0), c.wood);
    body.castShadow = true;
    g.add(body);
    for (let k = 0; k < 4; k++) g.add(new THREE.Mesh(box(w - 0.2, 0.04, 0.4, 0, 0.47, -0.8 + k * 0.5), M.trim(0x3a2414)));
    g.position.set(0, 1.18, 0);
    g.rotation.x = 0.12;
    c.dyn(g);
    for (const s of [-1, 1]) c.add(c.wood, boxB(0.1, 1.2, 0.1, s * (w / 2 - 0.15), 0, 0.9));
  },
};

/** Fallback for any prop type without a dedicated model: a dressed cabinet of the right footprint. */
function generic(c: PropCtx) {
  const [w, d, h] = fp(c.p.type);
  carcass(c, w, h, d);
}

export function buildProp(c: PropCtx) {
  (B[c.p.type] ?? generic)(c);
}

export const SKIP_PROPS = new Set(['elevator', 'gate', 'lever', 'hands', 'seekEyes', 'fire', 'guidingLight', 'jeff', 'snare', 'glassPanel', 'vent', 'pipe', 'cables', 'ventGrate', 'signpost', 'catwalk', 'curtain']);
