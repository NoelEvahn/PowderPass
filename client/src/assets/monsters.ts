import * as THREE from 'three';
import { Rig, bipedDefs, gait } from './rig';
import { canvasTex, radialSprite } from './textures';
import { M } from './materials';

/** Visual + animation controller for one entity/avatar. */
export interface Creature {
  root: THREE.Object3D;
  update(dt: number, state: string, speed: number, cam: THREE.Camera): void;
  dispose?(): void;
}

function fleshTex(base: string, vein: string) {
  return canvasTex(256, 256, (ctx) => {
    ctx.fillStyle = base; ctx.fillRect(0, 0, 256, 256);
    for (let k = 0; k < 2500; k++) { ctx.fillStyle = `rgba(0,0,0,${Math.random() * 0.15})`; ctx.fillRect(Math.random() * 256, Math.random() * 256, 2 + Math.random() * 3, 2); }
    ctx.strokeStyle = vein; ctx.lineWidth = 1.5;
    for (let k = 0; k < 40; k++) {
      ctx.beginPath(); let x = Math.random() * 256, y = Math.random() * 256; ctx.moveTo(x, y);
      for (let s = 0; s < 8; s++) { x += (Math.random() - 0.5) * 30; y += Math.random() * 20; ctx.lineTo(x, y); }
      ctx.stroke();
    }
  });
}

function teethRing(r: number, n: number, len: number, y: number, z: number, inward = true) {
  const geos: THREE.BufferGeometry[] = [];
  for (let k = 0; k < n; k++) {
    const a = (k / n) * Math.PI * 2;
    const g = new THREE.ConeGeometry(0.018, len, 5);
    g.rotateX(inward ? Math.PI / 2 : -Math.PI / 2);
    g.rotateZ(a);
    g.translate(Math.cos(a) * r, y + Math.sin(a) * r, z);
    geos.push(g);
  }
  return geos;
}

// ---------------------------------------------------------------- Figure

export function makeFigure(): Creature {
  const H = 2.9;
  const rig = new Rig(bipedDefs(H, { armLen: 0.95, legLen: 0.95, shoulder: 0.24 }));
  const flesh = new THREE.MeshStandardMaterial({ map: fleshTex('#6a2a1e', 'rgba(120,20,20,0.6)'), roughness: 0.55, color: 0xc08070 });
  const teeth = new THREE.MeshStandardMaterial({ color: 0xd8c8a0, roughness: 0.4 });
  const mouth = new THREE.MeshStandardMaterial({ color: 0x120404, roughness: 0.9 });
  const parts: { geo: THREE.BufferGeometry; bone: string; blend?: number; mat?: number }[] = [];
  const P = (geo: THREE.BufferGeometry, bone: string, mat = 0, blend?: number) => parts.push({ geo, bone, mat, blend });
  const hips = rig.at('hips'), chest = rig.at('chest'), head = rig.at('head');
  // torso: lumpy, ribbed
  P(new THREE.SphereGeometry(0.24, 14, 10).scale(1, 1.1, 0.75).translate(hips.x, hips.y + 0.05, 0), 'hips');
  P(rig.limb('spine', 'chest', 0.2, 0.26, 12), 'spine', 0, 0.4);
  P(new THREE.SphereGeometry(0.3, 14, 10).scale(1.15, 1.2, 0.8).translate(chest.x, chest.y + 0.12, 0.02), 'chest');
  for (let k = 0; k < 5; k++) P(new THREE.TorusGeometry(0.27 - k * 0.012, 0.018, 5, 18, Math.PI).rotateY(Math.PI / 2).rotateZ(Math.PI / 2).scale(1.1, 1, 0.9).translate(0, chest.y - 0.05 + k * 0.07, 0.1), 'chest', 0);
  P(rig.limb('neck', 'head', 0.09, 0.08, 10), 'neck', 0, 0.5);
  // head: elongated, eyeless, enormous mouth of rings of teeth
  P(new THREE.SphereGeometry(0.2, 16, 12).scale(1, 1.25, 1.05).translate(head.x, head.y + 0.16, 0.02), 'head');
  P(new THREE.CylinderGeometry(0.15, 0.13, 0.12, 16, 1, true).rotateX(Math.PI / 2).translate(head.x, head.y + 0.1, 0.2), 'head', 2);
  P(new THREE.CircleGeometry(0.13, 16).translate(head.x, head.y + 0.1, 0.14), 'head', 2);
  for (const g of teethRing(0.135, 22, 0.07, head.y + 0.1, 0.24)) P(g, 'head', 1);
  for (const g of teethRing(0.1, 16, 0.05, head.y + 0.1, 0.19)) P(g, 'head', 1);
  for (const g of teethRing(0.065, 12, 0.04, head.y + 0.1, 0.16)) P(g, 'head', 1);
  // arms (long, sinewy) with long fingers
  for (const s of ['L', 'R']) {
    P(new THREE.SphereGeometry(0.1, 10, 8).translate(rig.at('sh' + s).x, rig.at('sh' + s).y, 0), 'sh' + s);
    P(rig.limb('arm' + s, 'fore' + s, 0.075, 0.06), 'arm' + s, 0, 0.3);
    P(rig.limb('fore' + s, 'hand' + s, 0.06, 0.045), 'fore' + s, 0, 0.3);
    const hp = rig.at('hand' + s);
    P(new THREE.SphereGeometry(0.06, 8, 6).scale(1, 1.3, 0.6).translate(hp.x, hp.y - 0.03, 0), 'hand' + s);
    for (let f = 0; f < 4; f++) {
      const g = new THREE.CylinderGeometry(0.012, 0.008, 0.26, 5);
      g.translate(0, -0.13, 0);
      g.rotateZ((f - 1.5) * 0.12 * (s === 'L' ? 1 : -1));
      g.rotateX(0.15);
      g.translate(hp.x + (f - 1.5) * 0.025, hp.y - 0.08, 0.01);
      P(g, 'hand' + s);
    }
    P(rig.limb('thigh' + s, 'shin' + s, 0.11, 0.08), 'thigh' + s, 0, 0.3);
    P(rig.limb('shin' + s, 'foot' + s, 0.08, 0.055), 'shin' + s, 0, 0.3);
    const fp = rig.at('foot' + s);
    P(new THREE.BoxGeometry(0.11, 0.06, 0.3).translate(fp.x, fp.y - 0.02, 0.09), 'foot' + s);
  }
  const mesh = rig.build(parts, [flesh, teeth, mouth]);
  const root = new THREE.Group();
  root.add(mesh);
  let t = 0, prev = '';
  let stateT = 0;
  return {
    root,
    update(dt, state, speed) {
      if (state !== prev) { prev = state; stateT = 0; }
      stateT += dt;
      const moving = speed > 0.2;
      const rate = state === 'chase' || state === 'enraged' ? 9 : state === 'investigate' ? 6 : 4.2;
      if (moving) t += dt * rate;
      const amt = moving ? (state === 'chase' || state === 'enraged' ? 0.9 : 0.55) : 0.05;
      gait(rig, t, amt, { hunch: state === 'chase' || state === 'enraged' ? 0.45 : 0.25, armsForward: state === 'chase' || state === 'enraged' ? 0.7 : 0, armSwing: 0.5, bounce: 0.06 });
      const head = rig.b('head'), neck = rig.b('neck');
      // listening: head tilts and sweeps
      const listen = state === 'search' || state === 'patrol' || state === 'heartbeat' || state === 'dormant';
      head.rotation.z = listen ? Math.sin(stateT * 1.3) * 0.35 : 0;
      neck.rotation.y = listen ? Math.sin(stateT * 0.7) * 0.6 : 0;
      head.rotation.x = state === 'attack' || state === 'intro' ? -0.6 + Math.sin(stateT * 20) * 0.08 : state === 'heartbeat' ? 0.3 : 0;
      if (state === 'attack') { rig.b('armL').rotation.x = -2.2; rig.b('armR').rotation.x = -2.2; rig.b('spine').rotation.x = 0.5; }
      if (state === 'intro') { rig.b('armL').rotation.z = 0.9 + Math.sin(stateT * 10) * 0.05; rig.b('armR').rotation.z = -0.9; rig.b('chest').rotation.x = -0.3; }
      if (state === 'stunned') { rig.b('spine').rotation.x = 0.8; head.rotation.x = 0.5; rig.b('armL').rotation.x = -1.4; rig.b('armR').rotation.x = -1.4; }
    },
  };
}

// ---------------------------------------------------------------- Seek

export function makeSeek(): Creature {
  const rig = new Rig(bipedDefs(2.15, { armLen: 0.75 }));
  const goo = new THREE.MeshStandardMaterial({ color: 0x030303, roughness: 0.12, metalness: 0.3 });
  const eyeW = new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0x444444, roughness: 0.2 });
  const pupil = new THREE.MeshStandardMaterial({ color: 0x000000, roughness: 0.1 });
  const parts: { geo: THREE.BufferGeometry; bone: string; blend?: number; mat?: number }[] = [];
  const P = (geo: THREE.BufferGeometry, bone: string, mat = 0, blend?: number) => parts.push({ geo, bone, mat, blend });
  const hips = rig.at('hips'), chest = rig.at('chest'), head = rig.at('head');
  P(new THREE.SphereGeometry(0.2, 12, 10).scale(1, 1.1, 0.8).translate(0, hips.y, 0), 'hips');
  P(rig.limb('spine', 'chest', 0.17, 0.22, 12), 'spine', 0, 0.4);
  P(new THREE.SphereGeometry(0.25, 14, 10).scale(1.1, 1.2, 0.8).translate(0, chest.y + 0.1, 0), 'chest');
  P(rig.limb('neck', 'head', 0.08, 0.08), 'neck', 0, 0.5);
  P(new THREE.SphereGeometry(0.2, 16, 12).scale(1, 1.15, 1).translate(0, head.y + 0.14, 0), 'head');
  P(new THREE.SphereGeometry(0.1, 16, 12).scale(1, 0.75, 0.5).translate(0, head.y + 0.16, 0.17), 'head', 1);
  P(new THREE.SphereGeometry(0.045, 12, 8).scale(1, 1, 0.5).translate(0, head.y + 0.16, 0.215), 'head', 2);
  for (const s of ['L', 'R']) {
    P(rig.limb('arm' + s, 'fore' + s, 0.07, 0.06), 'arm' + s, 0, 0.3);
    P(rig.limb('fore' + s, 'hand' + s, 0.06, 0.05), 'fore' + s, 0, 0.3);
    P(new THREE.SphereGeometry(0.07, 8, 6).translate(rig.at('hand' + s).x, rig.at('hand' + s).y - 0.05, 0), 'hand' + s);
    P(rig.limb('thigh' + s, 'shin' + s, 0.1, 0.075), 'thigh' + s, 0, 0.3);
    P(rig.limb('shin' + s, 'foot' + s, 0.075, 0.06), 'shin' + s, 0, 0.3);
    P(new THREE.SphereGeometry(0.08, 8, 6).scale(1, 0.6, 1.6).translate(rig.at('foot' + s).x, rig.at('foot' + s).y - 0.02, 0.06), 'foot' + s);
  }
  // goo drips hanging off the body
  for (let k = 0; k < 14; k++) {
    const bone = ['chest', 'spine', 'armL', 'armR', 'head'][k % 5];
    const bp = rig.at(bone);
    P(new THREE.SphereGeometry(0.03 + (k % 3) * 0.01, 6, 5).scale(1, 2.2, 1).translate(bp.x + Math.sin(k) * 0.15, bp.y - 0.1 - (k % 4) * 0.05, Math.cos(k * 2) * 0.12), bone);
  }
  const mesh = rig.build(parts, [goo, eyeW, pupil]);
  const root = new THREE.Group();
  root.add(mesh);
  const puddle = new THREE.Mesh(new THREE.CircleGeometry(1.0, 24).rotateX(-Math.PI / 2).translate(0, 0.01, 0), M.goo());
  root.add(puddle);
  let t = 0, stateT = 0, prev = '';
  return {
    root,
    update(dt, state, speed) {
      if (state !== prev) { prev = state; stateT = 0; }
      stateT += dt;
      if (state === 'rise') {
        const k = Math.min(1, stateT / 2.3);
        mesh.position.y = -2.2 * (1 - k * k * (3 - 2 * k));
        puddle.visible = true;
        puddle.scale.setScalar(0.4 + k);
        gait(rig, 0, 0);
        rig.b('armL').rotation.x = -2.6 * (1 - k); rig.b('armR').rotation.x = -2.6 * (1 - k);
        return;
      }
      mesh.position.y = 0;
      puddle.visible = false;
      t += dt * (speed > 6 ? 13 : 11);
      gait(rig, t, state === 'stunned' ? 0.1 : 1.0, { lean: 0.35, armSwing: 1.1, bounce: 0.08 });
      rig.b('head').rotation.x = -0.3;
    },
  };
}

// ---------------------------------------------------------------- Rush / Ambush

function faceTex(kind: 'rush' | 'ambush') {
  return canvasTex(512, 512, (ctx) => {
    const g = ctx.createRadialGradient(256, 256, 40, 256, 256, 256);
    g.addColorStop(0, kind === 'rush' ? 'rgba(10,10,10,1)' : 'rgba(12,30,14,1)');
    g.addColorStop(0.7, kind === 'rush' ? 'rgba(20,20,20,0.95)' : 'rgba(20,60,24,0.95)');
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g; ctx.fillRect(0, 0, 512, 512);
    // smoky wisps
    for (let k = 0; k < 220; k++) { ctx.fillStyle = `rgba(${kind === 'rush' ? '40,40,40' : '30,80,40'},${Math.random() * 0.25})`; const a = Math.random() * Math.PI * 2, r = 150 + Math.random() * 100; ctx.beginPath(); ctx.arc(256 + Math.cos(a) * r, 256 + Math.sin(a) * r, 8 + Math.random() * 30, 0, Math.PI * 2); ctx.fill(); }
    // hollow eyes
    for (const sx of [-1, 1]) {
      ctx.fillStyle = kind === 'rush' ? '#000' : '#010';
      ctx.beginPath(); ctx.ellipse(256 + sx * 70, 200, 42, kind === 'rush' ? 50 : 70, sx * 0.2, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = kind === 'rush' ? 'rgba(230,230,220,0.95)' : 'rgba(200,255,200,0.95)';
      ctx.beginPath(); ctx.arc(256 + sx * 70, 205, 9, 0, Math.PI * 2); ctx.fill();
    }
    // huge grin
    ctx.fillStyle = '#000';
    ctx.beginPath();
    ctx.moveTo(130, 290); ctx.quadraticCurveTo(256, kind === 'rush' ? 430 : 470, 382, 290); ctx.quadraticCurveTo(256, 340, 130, 290); ctx.fill();
    ctx.fillStyle = kind === 'rush' ? '#e8e2d0' : '#d8ffd0';
    for (let k = 0; k < 15; k++) {
      const x = 145 + k * 16;
      const yTop = 296 + Math.sin((k / 14) * Math.PI) * 18;
      ctx.beginPath(); ctx.moveTo(x, yTop); ctx.lineTo(x + 7, yTop + 22); ctx.lineTo(x + 14, yTop); ctx.fill();
      const yb = 290 + Math.sin((k / 14) * Math.PI) * (kind === 'rush' ? 95 : 120);
      ctx.beginPath(); ctx.moveTo(x, yb); ctx.lineTo(x + 7, yb - 20); ctx.lineTo(x + 14, yb); ctx.fill();
    }
  });
}

export function makeRush(kind: 'rush' | 'ambush'): Creature {
  const root = new THREE.Group();
  const face = new THREE.Mesh(new THREE.PlaneGeometry(2.6, 2.6), new THREE.MeshBasicMaterial({ map: faceTex(kind), transparent: true, depthWrite: false, fog: false }));
  root.add(face);
  // smoke trail particles
  const n = 120;
  const geo = new THREE.BufferGeometry();
  const pos = new Float32Array(n * 3);
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  const smoke = new THREE.Points(geo, new THREE.PointsMaterial({ size: 0.9, map: radialSprite(kind === 'rush' ? 'rgba(20,20,20,0.7)' : 'rgba(20,70,30,0.7)'), transparent: true, depthWrite: false, sizeAttenuation: true }));
  smoke.frustumCulled = false;
  const life = new Float32Array(n);
  const world = new THREE.Vector3();
  const scene = { added: false };
  let t = 0;
  return {
    root,
    update(dt, state, _speed, cam) {
      t += dt;
      if (!scene.added && root.parent) { root.parent.add(smoke); scene.added = true; }
      face.visible = state === 'run';
      smoke.visible = state === 'run';
      face.lookAt(cam.position);
      const jit = kind === 'ambush' ? 0.18 : 0.1;
      face.position.set((Math.random() - 0.5) * jit, (Math.random() - 0.5) * jit, 0);
      face.scale.setScalar(1 + Math.sin(t * 30) * 0.04);
      root.getWorldPosition(world);
      for (let i = 0; i < n; i++) {
        life[i] -= dt;
        if (life[i] <= 0) {
          life[i] = 0.6 + Math.random() * 0.6;
          pos[i * 3] = world.x + (Math.random() - 0.5) * 1.6; pos[i * 3 + 1] = world.y + (Math.random() - 0.5) * 1.6; pos[i * 3 + 2] = world.z + (Math.random() - 0.5) * 1.6;
        } else pos[i * 3 + 1] += dt * 0.3;
      }
      geo.attributes.position.needsUpdate = true;
    },
    dispose() { smoke.removeFromParent(); geo.dispose(); },
  };
}

// ---------------------------------------------------------------- Screech

export function makeScreech(): Creature {
  const root = new THREE.Group();
  const body = new THREE.Mesh(new THREE.IcosahedronGeometry(0.32, 2), new THREE.MeshStandardMaterial({ color: 0x050505, roughness: 0.4 }));
  root.add(body);
  const face = new THREE.Mesh(new THREE.PlaneGeometry(0.62, 0.62), new THREE.MeshBasicMaterial({ transparent: true, map: canvasTex(256, 256, (ctx) => {
    ctx.fillStyle = '#000'; ctx.beginPath(); ctx.ellipse(128, 150, 100, 70, 0, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = '#f0ecdc';
    for (let k = 0; k < 14; k++) { const a = (k / 14) * Math.PI * 2; ctx.beginPath(); ctx.moveTo(128 + Math.cos(a) * 95, 150 + Math.sin(a) * 65); ctx.lineTo(128 + Math.cos(a + 0.2) * 60, 150 + Math.sin(a + 0.2) * 40); ctx.lineTo(128 + Math.cos(a + 0.4) * 95, 150 + Math.sin(a + 0.4) * 65); ctx.fill(); }
    ctx.fillStyle = '#e8e8ff'; for (const sx of [-1, 1]) { ctx.beginPath(); ctx.arc(128 + sx * 40, 60, 10, 0, Math.PI * 2); ctx.fill(); }
  }) }));
  face.position.z = 0.3;
  root.add(face);
  for (let k = 0; k < 7; k++) {
    const g = new THREE.ConeGeometry(0.05, 0.5, 5);
    g.translate(0, -0.25, 0);
    const m = new THREE.Mesh(g, body.material);
    m.rotation.set(Math.PI + (Math.random() - 0.5), k, (Math.random() - 0.5));
    root.add(m);
  }
  let t = 0;
  return {
    root,
    update(dt, state, _s, cam) {
      t += dt;
      root.lookAt(cam.position);
      const s = state === 'attack' ? 1.6 : state === 'flee' ? Math.max(0.01, 1 - t * 2) : 1;
      root.scale.setScalar(s * (1 + Math.sin(t * 12) * 0.05));
      if (state !== 'flee') t = Math.min(t, 100);
    },
  };
}

// ---------------------------------------------------------------- Eyes

function eyeSprite() {
  return canvasTex(128, 128, (ctx) => {
    const g = ctx.createRadialGradient(64, 64, 0, 64, 64, 64);
    g.addColorStop(0, 'rgba(255,240,255,1)'); g.addColorStop(0.25, 'rgba(220,120,255,0.95)'); g.addColorStop(0.6, 'rgba(120,20,200,0.4)'); g.addColorStop(1, 'rgba(60,0,120,0)');
    ctx.fillStyle = g; ctx.fillRect(0, 0, 128, 128);
    ctx.fillStyle = '#000'; ctx.beginPath(); ctx.ellipse(64, 64, 8, 16, 0, 0, Math.PI * 2); ctx.fill();
  });
}

export function makeEyes(): Creature {
  const root = new THREE.Group();
  const core = new THREE.Mesh(new THREE.IcosahedronGeometry(0.35, 2), new THREE.MeshStandardMaterial({ color: 0x08020c, roughness: 0.3, emissive: 0x200030 }));
  root.add(core);
  const tex = eyeSprite();
  const eyes: THREE.Sprite[] = [];
  for (let k = 0; k < 16; k++) {
    const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false, fog: false, blending: THREE.AdditiveBlending }));
    s.scale.setScalar(0.32 + (k % 3) * 0.08);
    root.add(s);
    eyes.push(s);
  }
  const light = new THREE.PointLight(0xa040ff, 3, 9, 2);
  root.add(light);
  let t = 0;
  return {
    root,
    update(dt) {
      t += dt;
      eyes.forEach((s, k) => {
        const a = k * 2.4 + t * (0.3 + (k % 4) * 0.1), b = k * 1.3 + t * 0.2;
        s.position.set(Math.cos(a) * Math.cos(b) * 0.55, Math.sin(b) * 0.55, Math.sin(a) * Math.cos(b) * 0.55);
        const blink = Math.sin(t * 2 + k * 7) > 0.97 ? 0.1 : 1;
        s.scale.y = (0.32 + (k % 3) * 0.08) * blink;
      });
      light.intensity = 2.5 + Math.sin(t * 6) * 0.6;
      root.position.y = Math.sin(t * 1.4) * 0.08;
    },
  };
}

// ---------------------------------------------------------------- Halt

export function makeHalt(): Creature {
  const rig = new Rig(bipedDefs(1.9));
  const mat = new THREE.MeshStandardMaterial({ color: 0x0a1840, emissive: 0x2050ff, emissiveIntensity: 0.6, transparent: true, opacity: 0.75, roughness: 0.3 });
  const parts: { geo: THREE.BufferGeometry; bone: string; blend?: number }[] = [];
  const P = (geo: THREE.BufferGeometry, bone: string, blend?: number) => parts.push({ geo, bone, blend });
  P(rig.limb('hips', 'chest', 0.2, 0.25, 10), 'spine', 0.4);
  P(new THREE.SphereGeometry(0.17, 12, 10).translate(0, rig.at('head').y + 0.12, 0), 'head');
  for (const s of ['L', 'R']) {
    P(rig.limb('arm' + s, 'hand' + s, 0.06, 0.04), 'arm' + s);
    P(rig.limb('thigh' + s, 'foot' + s, 0.09, 0.05), 'thigh' + s);
  }
  const mesh = rig.build(parts, [mat]);
  const root = new THREE.Group();
  root.add(mesh);
  let t = 0;
  return {
    root,
    update(dt) {
      t += dt;
      gait(rig, t * 3, 0.15, { armsForward: 1.2 });
      mat.opacity = 0.55 + Math.sin(t * 9) * 0.2;
      mesh.position.x = (Math.random() - 0.5) * 0.04;
    },
  };
}

// ---------------------------------------------------------------- humanoid avatars (players, Jeff)

export function makeAvatar(color: number, opts: { jeff?: boolean } = {}): Creature & { rig: Rig; hand: THREE.Object3D } {
  const rig = new Rig(bipedDefs(opts.jeff ? 2.0 : 1.75));
  const shirt = new THREE.MeshStandardMaterial({ color, roughness: 0.85 });
  const skin = new THREE.MeshStandardMaterial({ color: opts.jeff ? 0x8a2a2a : 0xd8a888, roughness: 0.6 });
  const pants = new THREE.MeshStandardMaterial({ color: opts.jeff ? 0x1a1a1a : 0x2a2a34, roughness: 0.9 });
  const dark = new THREE.MeshStandardMaterial({ color: 0x111111, roughness: 0.5 });
  const parts: { geo: THREE.BufferGeometry; bone: string; blend?: number; mat?: number }[] = [];
  const P = (geo: THREE.BufferGeometry, bone: string, mat = 0, blend?: number) => parts.push({ geo, bone, mat, blend });
  const hips = rig.at('hips'), chest = rig.at('chest'), head = rig.at('head');
  P(new THREE.SphereGeometry(0.16, 12, 8).scale(1.1, 0.8, 0.8).translate(0, hips.y, 0), 'hips', 2);
  P(rig.limb('spine', 'chest', 0.15, 0.18, 12), 'spine', 0, 0.4);
  P(new THREE.SphereGeometry(0.2, 12, 10).scale(1.1, 1.1, 0.7).translate(0, chest.y + 0.08, 0), 'chest', 0);
  P(rig.limb('neck', 'head', 0.05, 0.05), 'neck', 1);
  P(new THREE.SphereGeometry(0.12, 14, 12).scale(1, 1.15, 1.05).translate(0, head.y + 0.1, 0.01), 'head', 1);
  if (opts.jeff) {
    // grin and tie
    P(new THREE.TorusGeometry(0.06, 0.01, 4, 12, Math.PI).rotateZ(Math.PI).translate(0, head.y + 0.07, 0.12), 'head', 3);
    P(new THREE.BoxGeometry(0.06, 0.3, 0.02).translate(0, chest.y - 0.02, 0.15), 'chest', 3);
    for (const sx of [-1, 1]) P(new THREE.SphereGeometry(0.02, 6, 6).translate(sx * 0.045, head.y + 0.14, 0.11), 'head', 3);
  } else {
    P(new THREE.SphereGeometry(0.125, 12, 8, 0, Math.PI * 2, 0, Math.PI / 2).translate(0, head.y + 0.12, -0.005), 'head', 3);
  }
  for (const s of ['L', 'R']) {
    P(rig.limb('arm' + s, 'fore' + s, 0.055, 0.05), 'arm' + s, 0, 0.3);
    P(rig.limb('fore' + s, 'hand' + s, 0.05, 0.04), 'fore' + s, 0, 0.3);
    P(new THREE.SphereGeometry(0.045, 8, 6).translate(rig.at('hand' + s).x, rig.at('hand' + s).y - 0.04, 0), 'hand' + s, 1);
    P(rig.limb('thigh' + s, 'shin' + s, 0.075, 0.06), 'thigh' + s, 2, 0.3);
    P(rig.limb('shin' + s, 'foot' + s, 0.06, 0.05), 'shin' + s, 2, 0.3);
    P(new THREE.BoxGeometry(0.09, 0.06, 0.24).translate(rig.at('foot' + s).x, rig.at('foot' + s).y - 0.01, 0.05), 'foot' + s, 3);
  }
  const mesh = rig.build(parts, [shirt, skin, pants, dark]);
  const root = new THREE.Group();
  root.add(mesh);
  const hand = new THREE.Group();
  rig.b('handR').add(hand);
  hand.position.set(0, -0.08, 0.04);
  let t = 0;
  return {
    root, rig, hand,
    update(dt, state, speed) {
      const moving = speed > 0.3;
      if (moving) t += dt * (4 + speed * 1.3);
      const crouch = state === 'crouch';
      gait(rig, t, moving ? Math.min(1, speed / 4) * 0.8 : 0, { bounce: 0.03 });
      rig.b('hips').position.y = rig.at('hips').y - (crouch ? 0.45 : 0);
      if (crouch) { rig.b('thighL').rotation.x -= 1.1; rig.b('thighR').rotation.x -= 1.1; rig.b('shinL').rotation.x += 1.6; rig.b('shinR').rotation.x += 1.6; rig.b('spine').rotation.x = 0.4; }
      if (state === 'holding') { rig.b('armR').rotation.x = -1.2; rig.b('foreR').rotation.x = -0.3; }
      if (state === 'idle-shop') { rig.b('armL').rotation.x = -0.4 + Math.sin(performance.now() / 700) * 0.1; rig.b('head').rotation.z = Math.sin(performance.now() / 1300) * 0.15; }
    },
  };
}

// ---------------------------------------------------------------- Timothy (spider)

export function makeTimothy(): Creature {
  const root = new THREE.Group();
  const mat = new THREE.MeshStandardMaterial({ color: 0x1a1008, roughness: 0.6 });
  root.add(new THREE.Mesh(new THREE.SphereGeometry(0.08, 10, 8).scale(1, 0.7, 1.3), mat));
  root.add(new THREE.Mesh(new THREE.SphereGeometry(0.05, 8, 6).translate(0, 0, 0.1), mat));
  const legs: THREE.Mesh[] = [];
  for (let k = 0; k < 8; k++) {
    const g = new THREE.CylinderGeometry(0.006, 0.004, 0.18, 4);
    g.translate(0, -0.09, 0);
    const m = new THREE.Mesh(g, mat);
    const side = k < 4 ? 1 : -1;
    m.position.set(side * 0.05, 0, -0.06 + (k % 4) * 0.04);
    m.rotation.z = side * 1.9;
    root.add(m);
    legs.push(m);
  }
  let t = 0;
  return { root, update(dt) { t += dt; legs.forEach((l, k) => (l.rotation.x = Math.sin(t * 30 + k) * 0.4)); } };
}

/** Floating cyan light used by the Guiding Light in seek crossroads. */
export function makeGuide(): THREE.Object3D {
  const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: radialSprite('rgba(170,230,255,1)', 'rgba(40,120,255,0)'), blending: THREE.AdditiveBlending, depthWrite: false, transparent: true, fog: false }));
  s.scale.setScalar(1.2);
  return s;
}
