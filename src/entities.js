// Dropped items, falling blocks, primed TNT, arrows and particles.
import * as THREE from 'three';
import { BLOCKS, B, maxStack } from './blocks.js';
import { Body, moveBody } from './physics.js';
import { makeItemMesh, makeBlockItemGeometry, getBlockItemMaterial, setMeshBrightness } from './models.js';
import { texData, TEX_INDEX, faceTex, TEX_NAMES } from './textures.js';

export function brightnessAt(world, x, y, z, day) {
  const l = world.getLightRaw(Math.floor(x), Math.floor(y), Math.floor(z));
  const sky = Math.pow(0.8, 15 - (l >> 4)) * (world.hasSky ? Math.max(0.06, day) : 0);
  const blk = Math.pow(0.8, 15 - (l & 15));
  return Math.min(1.1, Math.max(world.hasSky ? 0.05 : 0.38, sky, blk * 1.05));
}

export class ItemEntity {
  constructor(game, x, y, z, id, count, vel) {
    this.game = game;
    this.id = id;
    this.count = count;
    this.body = new Body(0.25, 0.25);
    this.body.pos = { x, y, z };
    this.body.vel = vel || { x: (Math.random() - 0.5) * 3, y: 4, z: (Math.random() - 0.5) * 3 };
    this.age = 0;
    this.pickupDelay = 0.6;
    this.mesh = makeItemMesh(id);
    this.mesh.scale.setScalar(this.mesh.userData.cube ? 0.25 : 0.4);
    this.group = new THREE.Group();
    this.group.add(this.mesh);
    game.renderer.scene.add(this.group);
    this.dead = false;
  }
  update(dt) {
    const b = this.body;
    this.age += dt;
    this.pickupDelay -= dt;
    if (b.inWater) {
      b.vel.y = Math.min(b.vel.y + 6 * dt, 1.2);
      b.vel.x += b.flow.x * dt * 4; b.vel.z += b.flow.z * dt * 4;
      b.vel.x *= 0.95; b.vel.z *= 0.95;
    } else b.vel.y -= 20 * dt;
    if (b.inLava) { this.remove(); this.game.particles.burst(b.pos.x, b.pos.y, b.pos.z, [0.3, 0.3, 0.3], 6); return; }
    if (b.onGround) { b.vel.x *= Math.pow(0.02, dt); b.vel.z *= Math.pow(0.02, dt); }
    moveBody(this.game.world, b, dt);
    if (b.pos.y < -20) this.remove();
    const bob = Math.sin(this.age * 2.5) * 0.06 + 0.12;
    this.group.position.set(b.pos.x, b.pos.y + bob + 0.05, b.pos.z);
    this.mesh.rotation.y = this.age * 1.4;
    if (this.age > 300) this.remove();
    setMeshBrightness(this.group, brightnessAt(this.game.world, b.pos.x, b.pos.y + 0.3, b.pos.z, this.game.day));
  }
  remove() {
    if (this.dead) return;
    this.dead = true;
    this.game.renderer.scene.remove(this.group);
    this.mesh.material.dispose();
  }
}

export class FallingBlock {
  constructor(game, x, y, z, id) {
    this.game = game;
    this.id = id;
    this.body = new Body(0.98, 0.98);
    this.body.pos = { x: x + 0.5, y, z: z + 0.5 };
    const mat = getBlockItemMaterial().clone();
    this.mesh = new THREE.Mesh(makeBlockItemGeometry(id), mat);
    this.mesh.layers.enable(1);
    game.renderer.scene.add(this.mesh);
    this.dead = false;
    this.age = 0;
  }
  update(dt) {
    const b = this.body;
    this.age += dt;
    b.vel.y = Math.max(b.vel.y - 25 * dt, -40);
    moveBody(this.game.world, b, dt);
    this.mesh.position.set(b.pos.x, b.pos.y + 0.49, b.pos.z);
    setMeshBrightness(this.mesh, brightnessAt(this.game.world, b.pos.x, b.pos.y + 0.5, b.pos.z, this.game.day));
    if (b.onGround || this.age > 20) {
      const x = Math.floor(b.pos.x), y = Math.floor(b.pos.y + 0.1), z = Math.floor(b.pos.z);
      const cur = this.game.world.getBlock(x, y, z);
      if (cur === 0 || BLOCKS[cur].liquid || BLOCKS[cur].replaceable) this.game.world.setBlock(x, y, z, this.id);
      else this.game.dropItem(x + 0.5, y + 0.5, z + 0.5, this.id, 1);
      this.remove();
    }
  }
  remove() {
    this.dead = true;
    this.game.renderer.scene.remove(this.mesh);
  }
}

export class PrimedTNT {
  constructor(game, x, y, z, fuse = 4) {
    this.game = game;
    this.body = new Body(0.98, 0.98);
    this.body.pos = { x: x + 0.5, y, z: z + 0.5 };
    this.body.vel = { x: (Math.random() - 0.5), y: 3, z: (Math.random() - 0.5) };
    this.fuse = fuse;
    const mat = getBlockItemMaterial().clone();
    this.mesh = new THREE.Mesh(makeBlockItemGeometry(B.tnt), mat);
    this.mesh.layers.enable(1);
    game.renderer.scene.add(this.mesh);
    this.dead = false;
  }
  update(dt) {
    const b = this.body;
    b.vel.y -= 20 * dt;
    if (b.onGround) { b.vel.x *= 0.8; b.vel.z *= 0.8; }
    moveBody(this.game.world, b, dt);
    this.fuse -= dt;
    this.mesh.position.set(b.pos.x, b.pos.y + 0.49, b.pos.z);
    const flash = Math.floor(this.fuse * 5) % 2 === 0;
    const s = this.fuse < 0.4 ? 1 + (0.4 - this.fuse) * 0.5 : 1;
    this.mesh.scale.setScalar(s);
    setMeshBrightness(this.mesh, flash ? 2.2 : brightnessAt(this.game.world, b.pos.x, b.pos.y + 0.5, b.pos.z, this.game.day));
    if (this.fuse <= 0) {
      this.remove();
      this.game.explode(b.pos.x, b.pos.y + 0.5, b.pos.z, 4);
    }
  }
  remove() {
    this.dead = true;
    this.game.renderer.scene.remove(this.mesh);
  }
}

export class Arrow {
  constructor(game, x, y, z, vx, vy, vz, owner) {
    this.game = game;
    this.pos = new THREE.Vector3(x, y, z);
    this.vel = new THREE.Vector3(vx, vy, vz);
    this.owner = owner;
    this.age = 0;
    this.stuck = false;
    const g = new THREE.BoxGeometry(0.05, 0.05, 0.5);
    this.mesh = new THREE.Mesh(g, new THREE.MeshBasicMaterial({ color: 0x8b6a3a }));
    game.renderer.scene.add(this.mesh);
    this.dead = false;
  }
  update(dt) {
    this.age += dt;
    if (this.age > 30) return this.remove();
    if (this.stuck) return;
    this.vel.y -= 20 * dt;
    const step = this.vel.clone().multiplyScalar(dt);
    const next = this.pos.clone().add(step);
    const w = this.game.world;
    const id = w.getBlock(Math.floor(next.x), Math.floor(next.y), Math.floor(next.z));
    if (id && BLOCKS[id].solid) { this.stuck = true; this.game.audio.play('hit', next); return; }
    // hit entities
    const targets = [this.game.player, ...this.game.mobs.list];
    for (const t of targets) {
      if (t === this.owner || t.dead) continue;
      const bx = t.body;
      const r = bx.w / 2 + 0.1;
      if (Math.abs(next.x - bx.pos.x) < r && Math.abs(next.z - bx.pos.z) < r && next.y > bx.pos.y && next.y < bx.pos.y + bx.h) {
        t.hurt(3, this.vel.clone().normalize().multiplyScalar(4), this.owner);
        return this.remove();
      }
    }
    this.pos.copy(next);
    this.mesh.position.copy(this.pos);
    this.mesh.lookAt(this.pos.clone().add(this.vel));
  }
  remove() {
    this.dead = true;
    this.game.renderer.scene.remove(this.mesh);
  }
}

// ---------------- Particles ----------------
const avgColorCache = new Map();
export function blockColor(id) {
  if (avgColorCache.has(id)) return avgColorCache.get(id);
  const b = BLOCKS[id];
  const name = b.render === 'liquid' ? (b.liquid === 'water' ? 'water_still' : 'lava_still') : b.tex.side;
  const d = texData[name];
  const cols = [];
  for (let i = 0; i < d.length; i += 4 * 7) if (d[i + 3] > 128) cols.push([d[i] / 255, d[i + 1] / 255, d[i + 2] / 255]);
  if (!cols.length) cols.push([0.5, 0.5, 0.5]);
  let tint = [1, 1, 1];
  if (b.tint || b.tintTop) tint = [0.45, 0.7, 0.3];
  if (b.liquid === 'water') tint = [0.25, 0.45, 0.9];
  const res = cols.map((c) => [c[0] * tint[0], c[1] * tint[1], c[2] * tint[2]]);
  avgColorCache.set(id, res);
  return res;
}
export function clearColorCache() { avgColorCache.clear(); }

export class Particles {
  constructor(scene) {
    this.max = 3000;
    this.pos = new Float32Array(this.max * 3);
    this.col = new Float32Array(this.max * 3);
    this.size = new Float32Array(this.max);
    this.vel = new Float32Array(this.max * 3);
    this.life = new Float32Array(this.max);
    this.grav = new Float32Array(this.max);
    this.count = 0;
    const g = new THREE.BufferGeometry();
    this.posAttr = new THREE.BufferAttribute(this.pos, 3);
    this.colAttr = new THREE.BufferAttribute(this.col, 3);
    this.sizeAttr = new THREE.BufferAttribute(this.size, 1);
    this.posAttr.setUsage(THREE.DynamicDrawUsage);
    g.setAttribute('position', this.posAttr);
    g.setAttribute('color', this.colAttr);
    g.setAttribute('size', this.sizeAttr);
    this.mat = new THREE.ShaderMaterial({
      uniforms: { uScale: { value: 600 }, uBright: { value: 1 } },
      vertexShader: `attribute float size; attribute vec3 color; varying vec3 vC; uniform float uScale;
        void main(){ vC = pow(color, vec3(2.2)); vec4 mv = modelViewMatrix * vec4(position,1.0); gl_PointSize = size * uScale / -mv.z; gl_Position = projectionMatrix * mv; }`,
      fragmentShader: `varying vec3 vC; uniform float uBright; void main(){ gl_FragColor = vec4(vC * uBright, 1.0);
        #include <colorspace_fragment>
      }`,
    });
    this.points = new THREE.Points(g, this.mat);
    this.points.frustumCulled = false;
    this.points.renderOrder = 3;
    scene.add(this.points);
  }
  add(x, y, z, vx, vy, vz, c, size, life, grav = 18) {
    if (this.count >= this.max) return;
    const i = this.count++;
    this.pos[i * 3] = x; this.pos[i * 3 + 1] = y; this.pos[i * 3 + 2] = z;
    this.vel[i * 3] = vx; this.vel[i * 3 + 1] = vy; this.vel[i * 3 + 2] = vz;
    this.col[i * 3] = c[0]; this.col[i * 3 + 1] = c[1]; this.col[i * 3 + 2] = c[2];
    this.size[i] = size; this.life[i] = life; this.grav[i] = grav;
  }
  blockBreak(x, y, z, id) {
    const cols = blockColor(id);
    for (let i = 0; i < 28; i++) {
      const c = cols[(Math.random() * cols.length) | 0];
      this.add(x + Math.random(), y + Math.random(), z + Math.random(), (Math.random() - 0.5) * 4, Math.random() * 4, (Math.random() - 0.5) * 4, c, 0.08 + Math.random() * 0.06, 0.5 + Math.random() * 0.6);
    }
  }
  blockHit(x, y, z, face, id) {
    const cols = blockColor(id);
    for (let i = 0; i < 2; i++) {
      const c = cols[(Math.random() * cols.length) | 0];
      const px = x + 0.5 + face[0] * 0.52 + (face[0] ? 0 : Math.random() - 0.5);
      const py = y + 0.5 + face[1] * 0.52 + (face[1] ? 0 : Math.random() - 0.5);
      const pz = z + 0.5 + face[2] * 0.52 + (face[2] ? 0 : Math.random() - 0.5);
      this.add(px, py, pz, face[0] * 1.5 + (Math.random() - 0.5), 1 + Math.random(), face[2] * 1.5 + (Math.random() - 0.5), c, 0.06, 0.4);
    }
  }
  burst(x, y, z, c, n, speed = 2, size = 0.1, grav = 2) {
    for (let i = 0; i < n; i++) this.add(x, y, z, (Math.random() - 0.5) * speed, Math.random() * speed, (Math.random() - 0.5) * speed, c, size * (0.6 + Math.random() * 0.8), 0.6 + Math.random() * 0.8, grav);
  }
  update(dt, world, day) {
    let j = 0;
    for (let i = 0; i < this.count; i++) {
      this.life[i] -= dt;
      if (this.life[i] <= 0) continue;
      this.vel[i * 3 + 1] -= this.grav[i] * dt;
      let nx = this.pos[i * 3] + this.vel[i * 3] * dt;
      let ny = this.pos[i * 3 + 1] + this.vel[i * 3 + 1] * dt;
      let nz = this.pos[i * 3 + 2] + this.vel[i * 3 + 2] * dt;
      const id = world.getBlock(Math.floor(nx), Math.floor(ny), Math.floor(nz));
      if (id && BLOCKS[id].solid && this.grav[i] > 5) {
        ny = this.pos[i * 3 + 1];
        this.vel[i * 3 + 1] = 0; this.vel[i * 3] *= 0.5; this.vel[i * 3 + 2] *= 0.5;
        nx = this.pos[i * 3]; nz = this.pos[i * 3 + 2];
      }
      if (j !== i) {
        this.vel[j * 3] = this.vel[i * 3]; this.vel[j * 3 + 1] = this.vel[i * 3 + 1]; this.vel[j * 3 + 2] = this.vel[i * 3 + 2];
        this.col[j * 3] = this.col[i * 3]; this.col[j * 3 + 1] = this.col[i * 3 + 1]; this.col[j * 3 + 2] = this.col[i * 3 + 2];
        this.size[j] = this.size[i]; this.life[j] = this.life[i]; this.grav[j] = this.grav[i];
      }
      this.pos[j * 3] = nx; this.pos[j * 3 + 1] = ny; this.pos[j * 3 + 2] = nz;
      j++;
    }
    this.count = j;
    this.points.geometry.setDrawRange(0, this.count);
    this.posAttr.needsUpdate = true;
    this.colAttr.needsUpdate = true;
    this.sizeAttr.needsUpdate = true;
    this.mat.uniforms.uBright.value = world.hasSky ? 0.35 + day * 0.65 : 0.7;
    this.mat.uniforms.uScale.value = window.innerHeight * 0.9;
  }
}

export { maxStack, TEX_INDEX, faceTex, TEX_NAMES };
