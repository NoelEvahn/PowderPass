// Mobs: passive animals, hostiles, nether mobs; AI, spawning and drops.
import * as THREE from 'three';
import { B, I, BLOCKS } from './blocks.js';
import { Body, moveBody, isSolid, raycast } from './physics.js';
import { makeMobModel, animateHumanoid, animateQuadruped, makeItemMesh, setMeshBrightness } from './models.js';
import { brightnessAt, Arrow } from './entities.js';
import { CH } from './worldgen.js';

export const MOB_TYPES = {
  pig: { hp: 10, w: 0.9, h: 0.9, speed: 1.6, passive: true, drops: [[I.porkchop, 1, 3]], dim: 'overworld' },
  cow: { hp: 10, w: 0.9, h: 1.4, speed: 1.4, passive: true, drops: [[I.beef, 1, 3], [I.leather, 0, 2]], dim: 'overworld' },
  sheep: { hp: 8, w: 0.9, h: 1.3, speed: 1.5, passive: true, drops: [[B.white_wool, 1, 1], [I.mutton, 1, 2]], dim: 'overworld' },
  chicken: { hp: 4, w: 0.4, h: 0.7, speed: 1.4, passive: true, drops: [[I.chicken, 1, 1], [I.feather, 0, 2]], dim: 'overworld' },
  zombie: { hp: 20, w: 0.6, h: 1.9, speed: 2.4, hostile: true, damage: 3, drops: [[I.rotten_flesh, 0, 2]], dim: 'overworld', burns: true },
  skeleton: { hp: 20, w: 0.6, h: 1.9, speed: 2.4, hostile: true, ranged: true, drops: [[I.bone, 0, 2], [I.arrow, 0, 2]], dim: 'overworld', burns: true },
  creeper: { hp: 20, w: 0.6, h: 1.7, speed: 2.3, hostile: true, creeper: true, drops: [[I.gunpowder, 0, 2]], dim: 'overworld' },
  piglin: { hp: 20, w: 0.6, h: 1.9, speed: 2.3, neutral: true, damage: 5, drops: [[I.rotten_flesh, 0, 1], [I.gold_ingot, 0, 1]], dim: 'nether' },
};

let nextId = 1;

export class Mob {
  constructor(game, type, x, y, z) {
    this.game = game;
    this.type = type;
    this.def = MOB_TYPES[type];
    this.id = nextId++;
    this.hp = this.def.hp;
    this.body = new Body(this.def.w, this.def.h);
    this.body.pos = { x, y, z };
    this.yaw = Math.random() * Math.PI * 2;
    this.headYaw = 0;
    this.model = makeMobModel(type);
    if (type === 'piglin') {
      const sword = makeItemMesh(I.golden_sword);
      sword.scale.setScalar(0.6);
      sword.rotation.set(-Math.PI / 2, Math.PI / 2, Math.PI / 4);
      sword.position.set(0, -0.05, 0.25);
      this.model.userData.hand.add(sword);
    }
    if (type === 'skeleton') {
      const bow = makeItemMesh(I.stick);
      bow.scale.setScalar(0.5);
      bow.position.set(0, 0, 0.2);
      this.model.userData.hand.add(bow);
    }
    game.renderer.scene.add(this.model);
    this.state = 'idle';
    this.target = null;
    this.timer = Math.random() * 3;
    this.attackCd = 0;
    this.hurtTime = 0;
    this.fuse = 0;
    this.dead = false;
    this.deathTime = 0;
    this.angry = false;
    this.speedNow = 0;
    this.swing = 0;
    this.burnTimer = 0;
  }

  hurt(dmg, knock, attacker) {
    if (this.dead || this.hurtTime > 0.35) return false;
    this.hp -= dmg;
    this.hurtTime = 0.5;
    if (knock) { this.body.vel.x += knock.x; this.body.vel.z += knock.z; this.body.vel.y = 5; }
    this.game.audio.play('hurt', this.body.pos);
    if (this.def.passive) { this.state = 'panic'; this.timer = 4; }
    if (this.def.neutral && attacker === this.game.player) {
      this.angry = true;
      // alert nearby piglins
      for (const m of this.game.mobs.list) if (m.type === 'piglin' && dist2(m.body.pos, this.body.pos) < 400) m.angry = true;
    }
    if (this.hp <= 0) this.die(attacker);
    return true;
  }

  die(attacker) {
    this.dead = true;
    this.deathTime = 0;
    if (attacker === this.game.player || !attacker) {
      for (const [id, min, max] of this.def.drops) {
        const n = min + Math.floor(Math.random() * (max - min + 1));
        if (n > 0) this.game.dropItem(this.body.pos.x, this.body.pos.y + 0.5, this.body.pos.z, id, n);
      }
    }
  }

  remove() {
    this.game.renderer.scene.remove(this.model);
    this.removed = true;
  }

  update(dt) {
    const g = this.game, b = this.body, d = this.def;
    if (this.dead) {
      this.deathTime += dt;
      this.model.rotation.z = Math.min(Math.PI / 2, this.deathTime * 5);
      setMeshBrightness(this.model, 1.4);
      if (this.deathTime > 1) {
        g.particles.burst(b.pos.x, b.pos.y + 0.5, b.pos.z, [0.9, 0.9, 0.9], 14, 1.5, 0.15, -1);
        this.remove();
      }
      return;
    }
    this.hurtTime = Math.max(0, this.hurtTime - dt);
    this.attackCd = Math.max(0, this.attackCd - dt);
    this.swing = Math.max(0, this.swing - dt * 3);
    const player = g.player;
    const pd = Math.sqrt(dist2(player.body.pos, b.pos));
    const canTarget = !player.dead && player.mode !== 'creative';
    let moveX = 0, moveZ = 0, speed = d.speed;
    this.timer -= dt;

    const hostileNow = (d.hostile || (d.neutral && this.angry)) && canTarget && pd < 24;
    if (hostileNow) {
      this.state = 'chase';
      const dx = player.body.pos.x - b.pos.x, dz = player.body.pos.z - b.pos.z;
      const len = Math.hypot(dx, dz) || 1;
      const targetYaw = Math.atan2(dx, dz);
      this.yaw = lerpAngle(this.yaw, targetYaw, dt * 8);
      if (d.ranged) {
        if (pd > 10) { moveX = dx / len; moveZ = dz / len; }
        else if (pd < 5) { moveX = -dx / len; moveZ = -dz / len; }
        if (this.attackCd <= 0 && pd < 16 && this.hasLineOfSight(player)) {
          this.attackCd = 2;
          this.shoot(player);
        }
      } else if (d.creeper) {
        if (pd < 3) {
          this.fuse += dt;
          if (this.fuse === dt) g.audio.play('fuse', b.pos);
          if (this.fuse > 1.5) {
            this.dead = true;
            this.remove();
            g.explode(b.pos.x, b.pos.y + 0.8, b.pos.z, 3);
            return;
          }
        } else {
          this.fuse = Math.max(0, this.fuse - dt);
          moveX = dx / len; moveZ = dz / len;
        }
      } else {
        if (pd > 1.2) { moveX = dx / len; moveZ = dz / len; }
        if (pd < 1.8 && this.attackCd <= 0 && Math.abs(player.body.pos.y - b.pos.y) < 1.8) {
          this.attackCd = 1;
          this.swing = 1;
          player.hurt(d.damage, { x: (dx / len) * 6, z: (dz / len) * 6 }, this);
        }
      }
    } else if (this.state === 'panic') {
      speed = d.speed * 2;
      if (this.timer <= 0) this.state = 'idle';
      if (Math.random() < dt * 2) this.yaw += (Math.random() - 0.5) * 2;
      moveX = Math.sin(this.yaw); moveZ = Math.cos(this.yaw);
    } else {
      this.fuse = Math.max(0, this.fuse - dt);
      if (this.timer <= 0) {
        if (this.state === 'wander') { this.state = 'idle'; this.timer = 2 + Math.random() * 5; }
        else { this.state = 'wander'; this.timer = 1 + Math.random() * 3; this.wanderYaw = Math.random() * Math.PI * 2; }
      }
      if (this.state === 'wander') {
        this.yaw = lerpAngle(this.yaw, this.wanderYaw, dt * 4);
        moveX = Math.sin(this.yaw); moveZ = Math.cos(this.yaw);
        speed = d.speed * 0.6;
      }
      // passive mobs look at nearby players
      if (pd < 6) this.headYaw = clampAngle(Math.atan2(player.body.pos.x - b.pos.x, player.body.pos.z - b.pos.z) - this.yaw, 1.2);
      else this.headYaw *= 0.9;
    }

    // avoid walking into lava / off big drops
    if (moveX || moveZ) {
      const ax = Math.floor(b.pos.x + moveX * 0.8), az = Math.floor(b.pos.z + moveZ * 0.8);
      const ay = Math.floor(b.pos.y);
      const ahead = g.world.getBlock(ax, ay, az), aheadBelow = g.world.getBlock(ax, ay - 1, az);
      let drop = 0;
      while (drop < 4 && !isSolid(g.world, ax, ay - 1 - drop, az) && !BLOCKS[g.world.getBlock(ax, ay - 1 - drop, az)].liquid) drop++;
      if (BLOCKS[ahead].liquid === 'lava' || BLOCKS[aheadBelow].liquid === 'lava' || (drop >= 4 && this.state !== 'chase')) {
        moveX = 0; moveZ = 0;
        this.wanderYaw = this.yaw + Math.PI;
      }
    }

    // physics
    const accel = b.onGround ? 20 : 4;
    b.vel.x += (moveX * speed - b.vel.x) * Math.min(1, accel * dt);
    b.vel.z += (moveZ * speed - b.vel.z) * Math.min(1, accel * dt);
    if (b.inWater || b.inLava) {
      b.vel.y = Math.min(b.vel.y + 18 * dt, 2.5);
      b.vel.x *= 0.9; b.vel.z *= 0.9;
      if (b.flow) { b.vel.x += b.flow.x * dt * 3; b.vel.z += b.flow.z * dt * 3; }
    } else {
      b.vel.y -= 28 * dt;
      if (this.type === 'chicken' && b.vel.y < -2) b.vel.y = -2;
    }
    b.vel.y = Math.max(b.vel.y, -40);
    const fall = moveBody(g.world, b, dt);
    if (fall > 3.5 && this.type !== 'chicken') this.hurt(Math.ceil(fall - 3), null, null);
    if (b.hitWall && b.onGround && (moveX || moveZ)) b.vel.y = 8.2; // jump obstacles
    if (b.inLava) this.hurt(4, null, null);
    // burn in daylight
    if (d.burns && g.world.hasSky && g.day > 0.6) {
      const l = g.world.getLightRaw(Math.floor(b.pos.x), Math.floor(b.pos.y + 1.6), Math.floor(b.pos.z));
      if (l >> 4 >= 15 && !b.inWater) {
        this.burnTimer += dt;
        if (Math.random() < dt * 8) g.particles.add(b.pos.x + (Math.random() - 0.5) * 0.6, b.pos.y + Math.random() * 1.8, b.pos.z + (Math.random() - 0.5) * 0.6, 0, 1, 0, [1, 0.6, 0.1], 0.12, 0.5, -1);
        if (this.burnTimer > 1) { this.burnTimer = 0; this.hurtTime = 0; this.hurt(1, null, null); }
      }
    }
    if (b.pos.y < -10) { this.dead = true; this.remove(); return; }

    this.speedNow = Math.hypot(b.vel.x, b.vel.z);
    // model update
    this.model.position.set(b.pos.x, b.pos.y, b.pos.z);
    this.model.rotation.y = this.yaw;
    const u = this.model.userData;
    if (u.humanoid) {
      animateHumanoid(this.model, {
        speed: this.speedNow, sprint: false, onGround: b.onGround, vy: b.vel.y, pitch: 0, headYaw: this.headYaw,
        zombieArms: this.type === 'zombie', swing: this.swing, holdingItem: this.type !== 'zombie',
      }, dt);
    } else {
      animateQuadruped(this.model, this.speedNow, dt, { flap: this.type === 'chicken' && !b.onGround });
      if (u.parts.head) u.parts.head.rotation.y = this.headYaw;
    }
    let bright = brightnessAt(g.world, b.pos.x, b.pos.y + b.h * 0.7, b.pos.z, g.day);
    if (this.hurtTime > 0.2) bright = 1.6;
    setMeshBrightness(this.model, bright);
    if (this.hurtTime > 0.2) this.model.traverse((o) => { if (o.isMesh && o.material.color) o.material.color.setRGB(1.5, 0.4, 0.4); });
    if (d.creeper && this.fuse > 0) {
      const f = Math.floor(this.fuse * 8) % 2 === 0;
      const s = 1 + this.fuse * 0.15;
      this.model.scale.set(s, 1 + this.fuse * 0.05, s);
      if (f) setMeshBrightness(this.model, 2.5);
    } else this.model.scale.setScalar(1);
  }

  hasLineOfSight(player) {
    const b = this.body, p = player.body;
    const ox = b.pos.x, oy = b.pos.y + 1.6, oz = b.pos.z;
    const dx = p.pos.x - ox, dy = p.pos.y + 1.5 - oy, dz = p.pos.z - oz;
    const len = Math.hypot(dx, dy, dz);
    const hit = raycast(this.game.world, ox, oy, oz, dx / len, dy / len, dz / len, len, (id) => BLOCKS[id].opaque);
    return !hit;
  }

  shoot(player) {
    const b = this.body, p = player.body;
    const ox = b.pos.x, oy = b.pos.y + 1.5, oz = b.pos.z;
    const dx = p.pos.x - ox, dy = p.pos.y + 1.2 - oy, dz = p.pos.z - oz;
    const len = Math.hypot(dx, dz);
    const speed = 20;
    const t = len / speed;
    const vy = dy / t + 10 * t; // compensate gravity
    this.swing = 1;
    this.game.entities.push(new Arrow(this.game, ox + (dx / len) * 0.6, oy, oz + (dz / len) * 0.6, (dx / len) * speed, vy, (dz / len) * speed, this));
    this.game.audio.play('bow', b.pos);
  }
}

function dist2(a, b) {
  return (a.x - b.x) ** 2 + (a.y - b.y) ** 2 + (a.z - b.z) ** 2;
}
function lerpAngle(a, b, t) {
  let d = ((b - a + Math.PI) % (Math.PI * 2)) - Math.PI;
  if (d < -Math.PI) d += Math.PI * 2;
  return a + d * Math.min(1, t);
}
function clampAngle(a, m) {
  a = ((a + Math.PI) % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2) - Math.PI;
  return Math.max(-m, Math.min(m, a));
}

export class MobManager {
  constructor(game) {
    this.game = game;
    this.list = [];
    this.spawnTimer = 0;
  }
  clear() {
    for (const m of this.list) m.remove();
    this.list = [];
  }
  spawn(type, x, y, z) {
    const m = new Mob(this.game, type, x, y, z);
    this.list.push(m);
    return m;
  }
  update(dt) {
    for (const m of this.list) m.update(dt);
    this.list = this.list.filter((m) => !m.removed);
    // despawn far mobs
    const p = this.game.player.body.pos;
    for (const m of this.list) {
      if (dist2(m.body.pos, p) > 96 * 96) { m.remove(); }
    }
    this.list = this.list.filter((m) => !m.removed);
    this.spawnTimer -= dt;
    if (this.spawnTimer <= 0) {
      this.spawnTimer = 1;
      this.trySpawn();
    }
  }
  trySpawn() {
    const g = this.game, w = g.world;
    const p = g.player.body.pos;
    const passive = this.list.filter((m) => m.def.passive).length;
    const hostile = this.list.filter((m) => !m.def.passive).length;
    const nether = w.dim === 'nether';
    for (let attempt = 0; attempt < 4; attempt++) {
      const ang = Math.random() * Math.PI * 2;
      const dist = 20 + Math.random() * 36;
      const x = Math.floor(p.x + Math.cos(ang) * dist), z = Math.floor(p.z + Math.sin(ang) * dist);
      if (!w.isLoaded(x, z)) continue;
      // find a standing spot: scan from player height
      let y = -1;
      if (nether) {
        const start = Math.floor(p.y) + 12;
        for (let yy = Math.min(CH - 3, start); yy > 30; yy--) {
          if (isSolid(w, x, yy - 1, z) && w.getBlock(x, yy, z) === 0 && w.getBlock(x, yy + 1, z) === 0) { y = yy; break; }
        }
      } else {
        for (let yy = CH - 2; yy > 1; yy--) {
          if (isSolid(w, x, yy - 1, z)) { if (w.getBlock(x, yy, z) === 0 && w.getBlock(x, yy + 1, z) === 0) y = yy; break; }
          if (BLOCKS[w.getBlock(x, yy, z)].liquid) break;
        }
        // hostile mobs also spawn in dark caves
        if (Math.random() < 0.5) {
          for (let yy = Math.floor(p.y) + 8; yy > Math.floor(p.y) - 16 && yy > 2; yy--) {
            if (isSolid(w, x, yy - 1, z) && w.getBlock(x, yy, z) === 0 && w.getBlock(x, yy + 1, z) === 0) {
              const l = w.getLightRaw(x, yy, z);
              if ((l >> 4) < 4 && (l & 15) < 4) { y = yy; break; }
            }
          }
        }
      }
      if (y < 0) continue;
      const light = w.getLightRaw(x, y, z);
      const skyL = (light >> 4) * (nether ? 0 : g.day);
      const ground = w.getBlock(x, y - 1, z);
      if (nether) {
        if (hostile < 8 && g.settings.difficulty > 0) this.spawnGroup('piglin', x, y, z, 1 + ((Math.random() * 2) | 0));
        return;
      }
      if (ground === B.grass && skyL > 8 && passive < 14) {
        const types = ['pig', 'cow', 'sheep', 'chicken'];
        this.spawnGroup(types[(Math.random() * types.length) | 0], x, y, z, 2 + ((Math.random() * 3) | 0));
        return;
      }
      const dark = Math.max(skyL, light & 15) < 7;
      if (dark && hostile < 10 && g.settings.difficulty > 0) {
        const r = Math.random();
        this.spawnGroup(r < 0.45 ? 'zombie' : r < 0.75 ? 'skeleton' : 'creeper', x, y, z, 1);
        return;
      }
    }
  }
  spawnGroup(type, x, y, z, n) {
    const w = this.game.world;
    for (let i = 0; i < n; i++) {
      const sx = x + (i ? Math.floor((Math.random() - 0.5) * 4) : 0), sz = z + (i ? Math.floor((Math.random() - 0.5) * 4) : 0);
      if (i && !(isSolid(w, sx, y - 1, sz) && w.getBlock(sx, y, sz) === 0 && w.getBlock(sx, y + 1, sz) === 0)) continue;
      this.spawn(type, sx + 0.5, y, sz + 0.5);
    }
  }
}
