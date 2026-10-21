// Box-model helpers, procedural skins, Steve and mob models, item meshes.
import * as THREE from 'three';
import { ITEMS, BLOCKS } from './blocks.js';
import { iconPixels, getBlockTextureArray, faceTex, frontTex, TINTS } from './textures.js';
import { mulberry32 } from './noise.js';

const PX = 1.8 / 32; // one skin pixel in blocks

// Build a box whose faces map to the Minecraft skin layout at (u,v).
export function skinBox(w, h, d, u, v, texW, texH, inflate = 0) {
  const g = new THREE.BoxGeometry((w + inflate * 2) * PX, (h + inflate * 2) * PX, (d + inflate * 2) * PX);
  const uv = g.attributes.uv;
  // face regions [x, y, w, h] in pixels, order: +x, -x, +y, -y, +z, -z
  const R = [
    [u + d + w, v + d, d, h], // +x: character's left
    [u, v + d, d, h], // -x: character's right
    [u + d, v, w, d], // top
    [u + d + w, v, w, d], // bottom
    [u + d, v + d, w, h], // front (+z)
    [u + d + w + d, v + d, w, h], // back
  ];
  for (let f = 0; f < 6; f++) {
    const [rx, ry, rw, rh] = R[f];
    for (let i = 0; i < 4; i++) {
      const k = f * 4 + i;
      const a = uv.getX(k), b = uv.getY(k);
      let uu = rx + a * rw, vv = ry + (1 - b) * rh;
      if (f === 3) vv = ry + b * rh;
      uv.setXY(k, uu / texW, 1 - vv / texH);
    }
  }
  return g;
}

function canvasTex(c) {
  const t = new THREE.CanvasTexture(c);
  t.magFilter = THREE.NearestFilter;
  t.minFilter = THREE.NearestFilter;
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}

// Painter utility for skins
function painter(w, h, seed = 1) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const ctx = c.getContext('2d');
  const r = mulberry32(seed);
  const p = {
    c, ctx,
    fill(x, y, ww, hh, col, v = 0.08) {
      for (let j = 0; j < hh; j++)
        for (let i = 0; i < ww; i++) {
          const f = 1 + (r() - 0.5) * 2 * v;
          ctx.fillStyle = `rgb(${Math.min(255, col[0] * f) | 0},${Math.min(255, col[1] * f) | 0},${Math.min(255, col[2] * f) | 0})`;
          ctx.fillRect(x + i, y + j, 1, 1);
        }
    },
    px(x, y, col) { ctx.fillStyle = `rgb(${col[0]},${col[1]},${col[2]})`; ctx.fillRect(x, y, 1, 1); },
    // fill all 6 faces of a box region
    box(u, v, w2, h2, d, col, va) { p.fill(u, v, d * 2 + w2 * 2, d + h2, col, va); },
    rand: r,
  };
  return p;
}

// ---------------- Steve ----------------
function steveSkin() {
  const p = painter(64, 64, 42);
  const skin = [196, 140, 100], hair = [60, 40, 22], shirt = [0, 170, 170], pants = [55, 60, 150], shoe = [80, 80, 80];
  p.box(0, 0, 8, 8, 8, skin, 0.04); // head
  p.fill(8, 0, 8, 8, hair, 0.1); // top
  p.fill(0, 8, 32, 2, hair, 0.1); // hair band around
  p.fill(24, 8, 8, 6, hair, 0.1); // back of head
  p.fill(0, 10, 2, 2, hair); p.fill(22, 10, 2, 2, hair);
  // face (front at 8,8)
  p.fill(8, 8, 8, 2, hair, 0.1);
  p.px(8, 10, hair); p.px(15, 10, hair);
  p.px(9, 12, [255, 255, 255]); p.px(10, 12, [75, 50, 140]);
  p.px(14, 12, [255, 255, 255]); p.px(13, 12, [75, 50, 140]);
  p.fill(11, 13, 2, 1, [150, 95, 70], 0); // nose
  p.fill(10, 14, 4, 1, [110, 60, 40], 0); // mouth
  p.fill(9, 15, 6, 1, [120, 75, 50], 0.05); // beard
  // body
  p.box(16, 16, 8, 12, 4, shirt, 0.06);
  // arms
  p.box(40, 16, 4, 12, 4, skin, 0.04);
  p.fill(40, 20, 16, 4, shirt, 0.06);
  p.fill(44, 16, 4, 4, shirt, 0.06);
  // legs
  p.box(0, 16, 4, 12, 4, pants, 0.06);
  p.fill(0, 29, 16, 3, shoe, 0.08);
  p.fill(8, 16, 4, 4, shoe, 0.08);
  return p.c;
}

export function makeHumanoid(skinCanvas, opts = {}) {
  const tex = canvasTex(skinCanvas || steveSkin());
  const mat = new THREE.MeshBasicMaterial({ map: tex, alphaTest: 0.5 });
  const root = new THREE.Group();
  const body = new THREE.Group(); // rotates for swimming/sneaking (pivot at hips)
  root.add(body);
  const armW = opts.slim ? 2 : 4;
  const limbW = opts.thin ? 2 : 4;
  const mk = (g, x, y, z) => { const m = new THREE.Mesh(g, mat); m.position.set(x, y, z); return m; };

  const torso = mk(skinBox(8, 12, 4, 16, 16, 64, 64), 0, 6 * PX, 0);
  const hips = new THREE.Group(); hips.position.y = 12 * PX; body.add(hips);
  hips.add(torso);
  const head = new THREE.Group(); head.position.y = 12 * PX; hips.add(head);
  head.add(mk(skinBox(8, 8, 8, 0, 0, 64, 64), 0, 4 * PX, 0));
  const armR = new THREE.Group(); armR.position.set(-(4 + armW / 2) * PX, 10 * PX, 0); hips.add(armR);
  const armL = new THREE.Group(); armL.position.set((4 + armW / 2) * PX, 10 * PX, 0); hips.add(armL);
  armR.add(mk(skinBox(opts.thin ? 2 : armW, 12, opts.thin ? 2 : 4, 40, 16, 64, 64), 0, -4 * PX, 0));
  armL.add(mk(skinBox(opts.thin ? 2 : armW, 12, opts.thin ? 2 : 4, 40, 16, 64, 64), 0, -4 * PX, 0));
  const legR = new THREE.Group(); legR.position.set(-2 * PX, 12 * PX, 0); body.add(legR);
  const legL = new THREE.Group(); legL.position.set(2 * PX, 12 * PX, 0); body.add(legL);
  legR.add(mk(skinBox(limbW, 12, limbW, 0, 16, 64, 64), 0, -6 * PX, 0));
  legL.add(mk(skinBox(limbW, 12, limbW, 0, 16, 64, 64), 0, -6 * PX, 0));
  // held item anchor in right hand
  const hand = new THREE.Group(); hand.position.set(0, -10 * PX, 1 * PX); armR.add(hand);
  root.userData = { body, hips, head, armR, armL, legR, legL, hand, mat, phase: 0, swing: 0 };
  root.traverse((o) => { if (o.isMesh) o.layers.enable(1); });
  return root;
}

// Steve animation state machine
export function animateHumanoid(model, s, dt) {
  const u = model.userData;
  const lerp = (a, b, t) => a + (b - a) * Math.min(1, t);
  const k = dt * 10;
  u.phase += dt * s.speed * (s.sprint ? 2.2 : 1.6) * 3.2;
  const amp = Math.min(1, s.speed / 4.3) * (s.sprint ? 1.25 : 1);
  const sw = Math.sin(u.phase) * amp;
  let bodyPitch = 0, hipsPitch = 0, legR = 0, legL = 0, armRx = 0, armLx = 0, armRz = 0.05, armLz = -0.05, headPitch = s.pitch, bodyY = 0;
  const breathe = Math.sin(performance.now() / 900) * 0.04;

  if (s.swimming) {
    // horizontal crawl stroke
    bodyPitch = -Math.PI / 2 + Math.max(-0.6, Math.min(0.6, -s.pitch * 0.8));
    const t = performance.now() / 1000 * 4;
    armRx = -Math.PI + Math.sin(t) * 1.4 - 1.4;
    armLx = -Math.PI + Math.sin(t + Math.PI) * 1.4 - 1.4;
    armRx = (t % (Math.PI * 2)) - Math.PI * 2;
    armLx = ((t + Math.PI) % (Math.PI * 2)) - Math.PI * 2;
    armRz = 0.25; armLz = -0.25;
    legR = Math.sin(t * 2) * 0.35; legL = -Math.sin(t * 2) * 0.35;
    headPitch = -0.9 + s.pitch * 0.3;
    bodyY = 0.4;
  } else if (s.flying) {
    const fwd = Math.min(1, s.speed / 10);
    bodyPitch = -fwd * (s.sprint ? 1.2 : 0.35);
    armRx = 0.25 * fwd + breathe; armLx = 0.25 * fwd - breathe;
    armRz = 0.12 + fwd * 0.2; armLz = -0.12 - fwd * 0.2;
    legR = 0.12 * fwd + Math.sin(performance.now() / 300) * 0.05; legL = 0.12 * fwd - Math.sin(performance.now() / 300) * 0.05;
    headPitch = s.pitch + bodyPitch * -0.8;
  } else if (s.inWater && !s.onGround) {
    // treading water
    const t = performance.now() / 1000 * 3;
    armRx = Math.sin(t) * 0.5 - 0.3; armLx = -Math.sin(t) * 0.5 - 0.3;
    armRz = 0.6 + Math.sin(t) * 0.3; armLz = -0.6 - Math.sin(t) * 0.3;
    legR = Math.sin(t * 1.3) * 0.4; legL = -legR;
  } else {
    legR = sw * 0.9; legL = -sw * 0.9;
    armRx = -sw * 0.8 + breathe; armLx = sw * 0.8 - breathe;
    if (!s.onGround && s.vy < -6) { armRz = 0.4; armLz = -0.4; armRx = -0.3; armLx = -0.3; }
    if (s.sneak) { hipsPitch = 0.45; bodyY = -0.12; legR *= 0.6; legL *= 0.6; armRx += 0.3; armLx += 0.3; headPitch = s.pitch - 0.45; }
    if (s.sprint) hipsPitch += 0.15;
  }
  if (s.zombieArms) { armRx = -Math.PI / 2 + Math.sin(performance.now() / 500) * 0.05; armLx = armRx; }
  if (s.holdingItem && !s.swimming) armRx = armRx * 0.5 - 0.2;
  // attack / mining swing
  if (s.swing > 0) {
    const p = 1 - s.swing; // 0..1
    const a = Math.sin(p * Math.PI);
    armRx += -a * 1.6;
    armRz += a * 0.4;
  }
  u.body.rotation.x = lerp(u.body.rotation.x, bodyPitch, k);
  u.body.position.y = lerp(u.body.position.y, bodyY, k);
  // pivot body around the chest when swimming
  u.body.position.z = lerp(u.body.position.z, s.swimming ? 0.8 : 0, k);
  u.hips.rotation.x = lerp(u.hips.rotation.x, hipsPitch, k);
  u.legR.rotation.x = lerp(u.legR.rotation.x, legR, k * 1.5);
  u.legL.rotation.x = lerp(u.legL.rotation.x, legL, k * 1.5);
  u.armR.rotation.x = s.swing > 0 ? armRx : lerp(u.armR.rotation.x, armRx, k * 1.5);
  u.armL.rotation.x = lerp(u.armL.rotation.x, armLx, k * 1.5);
  u.armR.rotation.z = lerp(u.armR.rotation.z, armRz, k);
  u.armL.rotation.z = lerp(u.armL.rotation.z, armLz, k);
  u.head.rotation.x = lerp(u.head.rotation.x, -headPitch, k * 2);
  u.head.rotation.y = s.headYaw || 0;
  if (s.sneak) { u.legR.position.z = 0.15; u.legL.position.z = 0.15; } else { u.legR.position.z = 0; u.legL.position.z = 0; }
}

// ---------------- Mobs ----------------
function quad(model, mat, defs) {
  // defs: name -> {g, pos, parent, pivot}
  const parts = {};
  for (const [name, d] of Object.entries(defs)) {
    const grp = new THREE.Group();
    grp.position.set(...d.pivot);
    const m = new THREE.Mesh(d.g, mat);
    m.position.set(...(d.off || [0, 0, 0]));
    m.layers.enable(1);
    grp.add(m);
    (d.parent ? parts[d.parent] : model).add(grp);
    parts[name] = grp;
  }
  return parts;
}

const MOB_SKINS = {};
function mobSkin(type) {
  if (MOB_SKINS[type]) return MOB_SKINS[type];
  let p;
  if (type === 'pig') {
    p = painter(64, 32, 3);
    const pink = [240, 160, 160];
    p.fill(0, 0, 64, 32, pink, 0.06);
    p.px(8, 12, [255, 255, 255]); p.px(9, 12, [0, 0, 0]); p.px(14, 12, [255, 255, 255]); p.px(13, 12, [0, 0, 0]);
    p.fill(16, 17, 4, 2, [200, 110, 110], 0.04); // snout front
    p.px(17, 18, [120, 60, 60]); p.px(18, 18, [120, 60, 60]);
    p.fill(0, 26, 16, 6, [200, 120, 120], 0.05);
  } else if (type === 'cow') {
    p = painter(64, 32, 4);
    p.fill(0, 0, 64, 32, [80, 55, 40], 0.08);
    for (let i = 0; i < 9; i++) p.fill((p.rand() * 58) | 0, (p.rand() * 26) | 0, 4 + ((p.rand() * 5) | 0), 3 + ((p.rand() * 4) | 0), [235, 235, 235], 0.04);
    p.fill(6, 6, 8, 6, [80, 55, 40], 0.08);
    p.px(7, 8, [255, 255, 255]); p.px(8, 8, [0, 0, 0]); p.px(12, 8, [255, 255, 255]); p.px(11, 8, [0, 0, 0]);
    p.fill(8, 10, 4, 2, [190, 160, 150], 0.04);
    p.fill(22, 0, 6, 3, [220, 220, 210], 0.03); // horns region
  } else if (type === 'sheep') {
    p = painter(64, 32, 5);
    p.fill(0, 0, 64, 32, [235, 235, 230], 0.07);
    p.fill(0, 0, 22, 14, [210, 175, 150], 0.05); // face
    p.px(7, 9, [255, 255, 255]); p.px(8, 9, [0, 0, 0]); p.px(12, 9, [255, 255, 255]); p.px(11, 9, [0, 0, 0]);
    p.fill(0, 22, 16, 10, [210, 175, 150], 0.05);
  } else if (type === 'chicken') {
    p = painter(64, 32, 6);
    p.fill(0, 0, 64, 32, [245, 245, 245], 0.04);
    p.px(4, 4, [0, 0, 0]); p.px(7, 4, [0, 0, 0]);
    p.fill(14, 0, 8, 4, [250, 170, 30], 0.03); // beak
    p.fill(14, 4, 6, 4, [220, 30, 30], 0.03); // wattle
    p.fill(26, 0, 12, 8, [250, 170, 30], 0.03); // legs
  } else if (type === 'creeper') {
    p = painter(64, 32, 7);
    p.fill(0, 0, 64, 32, [80, 175, 70], 0.22);
    // face
    p.fill(9, 11, 2, 2, [0, 0, 0], 0); p.fill(13, 11, 2, 2, [0, 0, 0], 0);
    p.fill(11, 13, 2, 3, [0, 0, 0], 0); p.fill(10, 14, 1, 2, [0, 0, 0], 0); p.fill(13, 14, 1, 2, [0, 0, 0], 0);
  } else if (type === 'zombie') {
    p = painter(64, 64, 8);
    const skin = [100, 150, 80];
    p.box(0, 0, 8, 8, 8, skin, 0.08);
    p.fill(8, 0, 8, 8, [70, 110, 55], 0.1);
    p.fill(9, 12, 2, 1, [20, 20, 20], 0); p.fill(13, 12, 2, 1, [20, 20, 20], 0);
    p.fill(11, 14, 2, 1, [60, 90, 50], 0);
    p.box(16, 16, 8, 12, 4, [40, 160, 160], 0.08);
    p.box(40, 16, 4, 12, 4, skin, 0.08);
    p.fill(40, 20, 16, 3, [40, 160, 160], 0.08);
    p.box(0, 16, 4, 12, 4, [70, 60, 150], 0.08);
    p.fill(0, 29, 16, 3, [60, 60, 60], 0.08);
  } else if (type === 'skeleton') {
    p = painter(64, 64, 9);
    const bone = [200, 200, 200];
    p.box(0, 0, 8, 8, 8, bone, 0.06);
    p.fill(9, 11, 2, 2, [30, 30, 30], 0); p.fill(13, 11, 2, 2, [30, 30, 30], 0);
    p.fill(10, 14, 4, 1, [60, 60, 60], 0);
    p.box(16, 16, 8, 12, 4, bone, 0.1);
    for (let y = 21; y < 32; y += 2) p.fill(20, y, 8, 1, [70, 70, 70], 0);
    p.box(40, 16, 4, 12, 4, bone, 0.06);
    p.box(0, 16, 4, 12, 4, bone, 0.06);
  } else if (type === 'villager') {
    p = painter(64, 64, 11);
    const skin = [180, 125, 95], robe = [110, 75, 50];
    p.box(0, 0, 8, 8, 8, skin, 0.05);
    p.fill(8, 0, 8, 8, [90, 60, 40], 0.1);
    p.fill(9, 11, 2, 1, [255, 255, 255], 0); p.fill(13, 11, 2, 1, [255, 255, 255], 0);
    p.px(10, 11, [30, 110, 40]); p.px(13, 11, [30, 110, 40]);
    p.fill(11, 12, 2, 4, [160, 105, 80], 0); // big nose
    p.fill(10, 9, 4, 1, [70, 45, 30], 0); // unibrow
    p.box(16, 16, 8, 12, 4, robe, 0.08);
    p.fill(20, 26, 8, 6, [90, 60, 40], 0.08);
    p.box(40, 16, 4, 12, 4, robe, 0.08);
    p.fill(44, 26, 4, 4, skin, 0.05);
    p.box(0, 16, 4, 12, 4, robe, 0.08);
    p.fill(0, 29, 16, 3, [60, 40, 25], 0.08);
  } else if (type === 'piglin') {
    p = painter(64, 64, 10);
    const skin = [230, 150, 140];
    p.box(0, 0, 8, 8, 8, skin, 0.08);
    for (let i = 0; i < 12; i++) p.fill((p.rand() * 30) | 0, (p.rand() * 14) | 0, 2, 2, [110, 160, 90], 0.1);
    p.fill(9, 11, 2, 1, [250, 250, 250], 0); p.fill(13, 11, 2, 1, [250, 250, 250], 0);
    p.fill(10, 13, 4, 2, [200, 110, 110], 0);
    p.box(16, 16, 8, 12, 4, [200, 120, 110], 0.08);
    for (let i = 0; i < 6; i++) p.fill(16 + ((p.rand() * 20) | 0), 20 + ((p.rand() * 10) | 0), 3, 2, [100, 150, 80], 0.1);
    p.box(40, 16, 4, 12, 4, skin, 0.08);
    p.box(0, 16, 4, 12, 4, [120, 80, 50], 0.08);
  }
  MOB_SKINS[type] = p.c;
  return p.c;
}

export function makeMobModel(type) {
  if (type === 'zombie' || type === 'skeleton' || type === 'piglin' || type === 'villager') {
    const m = makeHumanoid(mobSkin(type), { thin: type === 'skeleton' });
    m.userData.humanoid = true;
    return m;
  }
  const tex = canvasTex(mobSkin(type));
  const mat = new THREE.MeshBasicMaterial({ map: tex });
  const model = new THREE.Group();
  const P = PX;
  let parts;
  if (type === 'pig') {
    parts = quad(model, mat, {
      body: { g: skinBox(10, 8, 16, 28, 8, 64, 32), pivot: [0, 9 * P, 0] },
      head: { g: skinBox(8, 8, 8, 0, 0, 64, 32), pivot: [0, 12 * P, 7 * P], off: [0, 0, 4 * P] },
      snout: { g: skinBox(4, 3, 1, 16, 16, 64, 32), pivot: [0, -1 * P, 8.5 * P], parent: 'head' },
      legFR: { g: skinBox(4, 6, 4, 0, 16, 64, 32), pivot: [-3 * P, 6 * P, 5 * P], off: [0, -3 * P, 0] },
      legFL: { g: skinBox(4, 6, 4, 0, 16, 64, 32), pivot: [3 * P, 6 * P, 5 * P], off: [0, -3 * P, 0] },
      legBR: { g: skinBox(4, 6, 4, 0, 16, 64, 32), pivot: [-3 * P, 6 * P, -6 * P], off: [0, -3 * P, 0] },
      legBL: { g: skinBox(4, 6, 4, 0, 16, 64, 32), pivot: [3 * P, 6 * P, -6 * P], off: [0, -3 * P, 0] },
    });
  } else if (type === 'cow') {
    parts = quad(model, mat, {
      body: { g: skinBox(12, 10, 18, 18, 4, 64, 32), pivot: [0, 17 * P, 0] },
      head: { g: skinBox(8, 8, 6, 0, 0, 64, 32), pivot: [0, 20 * P, 9 * P], off: [0, 0, 3 * P] },
      hornL: { g: skinBox(1, 3, 1, 22, 0, 64, 32), pivot: [4.5 * P, 4 * P, 2 * P], parent: 'head' },
      hornR: { g: skinBox(1, 3, 1, 22, 0, 64, 32), pivot: [-4.5 * P, 4 * P, 2 * P], parent: 'head' },
      legFR: { g: skinBox(4, 12, 4, 0, 16, 64, 32), pivot: [-4 * P, 12 * P, 6 * P], off: [0, -6 * P, 0] },
      legFL: { g: skinBox(4, 12, 4, 0, 16, 64, 32), pivot: [4 * P, 12 * P, 6 * P], off: [0, -6 * P, 0] },
      legBR: { g: skinBox(4, 12, 4, 0, 16, 64, 32), pivot: [-4 * P, 12 * P, -7 * P], off: [0, -6 * P, 0] },
      legBL: { g: skinBox(4, 12, 4, 0, 16, 64, 32), pivot: [4 * P, 12 * P, -7 * P], off: [0, -6 * P, 0] },
    });
  } else if (type === 'sheep') {
    parts = quad(model, mat, {
      body: { g: skinBox(10, 10, 16, 28, 8, 64, 32, 1), pivot: [0, 17 * P, 0] },
      head: { g: skinBox(6, 6, 8, 0, 0, 64, 32), pivot: [0, 20 * P, 8 * P], off: [0, 0, 3 * P] },
      legFR: { g: skinBox(4, 12, 4, 0, 16, 64, 32), pivot: [-3 * P, 12 * P, 5 * P], off: [0, -6 * P, 0] },
      legFL: { g: skinBox(4, 12, 4, 0, 16, 64, 32), pivot: [3 * P, 12 * P, 5 * P], off: [0, -6 * P, 0] },
      legBR: { g: skinBox(4, 12, 4, 0, 16, 64, 32), pivot: [-3 * P, 12 * P, -6 * P], off: [0, -6 * P, 0] },
      legBL: { g: skinBox(4, 12, 4, 0, 16, 64, 32), pivot: [3 * P, 12 * P, -6 * P], off: [0, -6 * P, 0] },
    });
  } else if (type === 'chicken') {
    parts = quad(model, mat, {
      body: { g: skinBox(6, 6, 8, 0, 9, 64, 32), pivot: [0, 8 * P, 0] },
      head: { g: skinBox(4, 6, 3, 0, 0, 64, 32), pivot: [0, 11 * P, 4 * P], off: [0, 2 * P, 0] },
      beak: { g: skinBox(4, 2, 2, 14, 0, 64, 32), pivot: [0, 2 * P, 2.5 * P], parent: 'head' },
      wattle: { g: skinBox(2, 2, 2, 14, 4, 64, 32), pivot: [0, 0, 2 * P], parent: 'head' },
      wingL: { g: skinBox(1, 4, 6, 24, 13, 64, 32), pivot: [3.5 * P, 10 * P, 0], off: [0, -2 * P, 0] },
      wingR: { g: skinBox(1, 4, 6, 24, 13, 64, 32), pivot: [-3.5 * P, 10 * P, 0], off: [0, -2 * P, 0] },
      legFR: { g: skinBox(1, 5, 1, 26, 0, 64, 32), pivot: [-1.5 * P, 5 * P, 0], off: [0, -2.5 * P, 0] },
      legFL: { g: skinBox(1, 5, 1, 26, 0, 64, 32), pivot: [1.5 * P, 5 * P, 0], off: [0, -2.5 * P, 0] },
    });
  } else if (type === 'creeper') {
    parts = quad(model, mat, {
      body: { g: skinBox(8, 12, 4, 16, 16, 64, 32), pivot: [0, 12 * P, 0] },
      head: { g: skinBox(8, 8, 8, 0, 0, 64, 32), pivot: [0, 18 * P, 0], off: [0, 4 * P, 0] },
      legFR: { g: skinBox(4, 6, 4, 0, 16, 64, 32), pivot: [-2 * P, 6 * P, 4 * P], off: [0, -3 * P, 0] },
      legFL: { g: skinBox(4, 6, 4, 0, 16, 64, 32), pivot: [2 * P, 6 * P, 4 * P], off: [0, -3 * P, 0] },
      legBR: { g: skinBox(4, 6, 4, 0, 16, 64, 32), pivot: [-2 * P, 6 * P, -4 * P], off: [0, -3 * P, 0] },
      legBL: { g: skinBox(4, 6, 4, 0, 16, 64, 32), pivot: [2 * P, 6 * P, -4 * P], off: [0, -3 * P, 0] },
    });
  }
  model.userData = { parts, mat, phase: 0 };
  return model;
}

export function animateQuadruped(model, speed, dt, extra = {}) {
  const u = model.userData;
  const p = u.parts;
  u.phase += dt * speed * 6;
  const sw = Math.sin(u.phase) * Math.min(1, speed / 2) * 0.8;
  if (p.legFR) p.legFR.rotation.x = sw;
  if (p.legFL) p.legFL.rotation.x = -sw;
  if (p.legBR) p.legBR.rotation.x = -sw;
  if (p.legBL) p.legBL.rotation.x = sw;
  if (p.wingL) { const f = extra.flap ? Math.sin(performance.now() / 50) * 0.8 + 0.8 : 0; p.wingL.rotation.z = -f; p.wingR.rotation.z = f; }
  if (p.head && extra.headPitch !== undefined) p.head.rotation.x = extra.headPitch;
}

// ---------------- Item meshes ----------------
const itemGeoCache = new Map();
let blockItemMat = null;
export function getBlockItemMaterial() {
  if (blockItemMat) return blockItemMat;
  blockItemMat = new THREE.ShaderMaterial({
    uniforms: { uTex: { value: getBlockTextureArray() }, uBright: { value: 1 } },
    vertexShader: `attribute float aLayer; attribute vec3 aTint; varying vec3 vUv; varying vec3 vTint; varying float vShade;
      void main(){ vUv = vec3(uv, aLayer); vTint = pow(aTint, vec3(2.2)); vec3 n = normalize(normal);
      vShade = abs(n.y) > 0.5 ? (n.y > 0.0 ? 1.0 : 0.5) : (abs(n.x) > 0.5 ? 0.65 : 0.8);
      gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
    fragmentShader: `precision highp sampler2DArray; uniform sampler2DArray uTex; uniform float uBright;
      varying vec3 vUv; varying vec3 vTint; varying float vShade;
      void main(){ vec4 t = texture(uTex, vUv); if (t.a < 0.5) discard; gl_FragColor = vec4(t.rgb * vTint * vShade * uBright, 1.0);
      #include <colorspace_fragment>
      }`,
  });
  return blockItemMat;
}

export function makeBlockItemGeometry(id) {
  const b = BLOCKS[id];
  const g = new THREE.BoxGeometry(1, 1, 1);
  const n = g.attributes.position.count;
  const layer = new Float32Array(n);
  const tint = new Float32Array(n * 3);
  const uv = g.attributes.uv;
  // BoxGeometry face order: +x, -x, +y, -y, +z, -z matches our face indices
  for (let f = 0; f < 6; f++) {
    let L = faceTex[id * 6 + f];
    if (f === 4 && (b.facing || b.key === 'crafting_table')) L = frontTex(id);
    let t = b.tint ? TINTS[b.tint] || TINTS.foliage : [1, 1, 1];
    if (b.tintTop) t = f === 2 ? TINTS.grass : [1, 1, 1];
    for (let i = 0; i < 4; i++) {
      const k = f * 4 + i;
      layer[k] = L;
      tint.set(t, k * 3);
      uv.setY(k, 1 - uv.getY(k));
    }
  }
  g.setAttribute('aLayer', new THREE.BufferAttribute(layer, 1));
  g.setAttribute('aTint', new THREE.BufferAttribute(tint, 3));
  return g;
}

// Extruded pixel-art item geometry (like Minecraft's held items).
export function makeFlatItemGeometry(id) {
  const S = 16;
  const data = iconPixels(id);
  const pos = [], col = [];
  const T = 1 / 16;
  const alpha = (x, y) => (x < 0 || y < 0 || x >= S || y >= S ? 0 : data[(y * S + x) * 4 + 3]);
  const quadP = (a, b, c2, d, r, g, bl, sh) => {
    pos.push(...a, ...b, ...c2, ...a, ...c2, ...d);
    for (let i = 0; i < 6; i++) col.push(r * sh, g * sh, bl * sh);
  };
  for (let y = 0; y < S; y++)
    for (let x = 0; x < S; x++) {
      const i = (y * S + x) * 4;
      if (data[i + 3] < 128) continue;
      const r = Math.pow(data[i] / 255, 2.2), g = Math.pow(data[i + 1] / 255, 2.2), bl = Math.pow(data[i + 2] / 255, 2.2);
      const x0 = x * T - 0.5, x1 = x0 + T, y1 = 0.5 - y * T, y0 = y1 - T, zf = T / 2, zb = -T / 2;
      quadP([x0, y0, zf], [x1, y0, zf], [x1, y1, zf], [x0, y1, zf], r, g, bl, 1);
      quadP([x1, y0, zb], [x0, y0, zb], [x0, y1, zb], [x1, y1, zb], r, g, bl, 0.75);
      if (alpha(x - 1, y) < 128) quadP([x0, y0, zb], [x0, y0, zf], [x0, y1, zf], [x0, y1, zb], r, g, bl, 0.6);
      if (alpha(x + 1, y) < 128) quadP([x1, y0, zf], [x1, y0, zb], [x1, y1, zb], [x1, y1, zf], r, g, bl, 0.6);
      if (alpha(x, y - 1) < 128) quadP([x0, y1, zf], [x1, y1, zf], [x1, y1, zb], [x0, y1, zb], r, g, bl, 0.85);
      if (alpha(x, y + 1) < 128) quadP([x0, y0, zb], [x1, y0, zb], [x1, y0, zf], [x0, y0, zf], r, g, bl, 0.5);
    }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
  return g;
}

export function isCubeItem(id) {
  const b = BLOCKS[id];
  return b && b.isBlock && (b.render === 'cube' || b.render === 'liquid');
}

export function makeItemMesh(id) {
  let geo = itemGeoCache.get(id);
  const cube = isCubeItem(id);
  if (!geo) {
    geo = cube ? makeBlockItemGeometry(id) : makeFlatItemGeometry(id);
    itemGeoCache.set(id, geo);
  }
  let mesh;
  if (cube) {
    const mat = getBlockItemMaterial().clone();
    mat.uniforms.uTex.value = getBlockTextureArray();
    mesh = new THREE.Mesh(geo, mat);
  } else {
    mesh = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ vertexColors: true }));
  }
  mesh.userData.cube = cube;
  mesh.layers.enable(1);
  return mesh;
}

export function setMeshBrightness(obj, b) {
  obj.traverse((o) => {
    if (!o.isMesh) return;
    const m = o.material;
    if (m.uniforms && m.uniforms.uBright) m.uniforms.uBright.value = b;
    else if (m.color) m.color.setScalar(b);
  });
}

export function clearItemCache() {
  itemGeoCache.clear();
}

export { ITEMS };
