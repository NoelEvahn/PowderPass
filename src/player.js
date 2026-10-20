// Player: movement physics (walk, sprint, sneak, swim, fly), survival stats, mining and placing.
import * as THREE from 'three';
import { BLOCKS, ITEMS, B, I, breakTime, canHarvest, maxStack } from './blocks.js';
import { Body, moveBody, raycast, rayBox, boxIntersectsBlock } from './physics.js';
import { makeHumanoid, animateHumanoid, makeItemMesh, setMeshBrightness, isCubeItem, skinBox } from './models.js';
import { brightnessAt, PrimedTNT } from './entities.js';
import { TEX_INDEX } from './textures.js';

const EYE = 1.62;

export class Player {
  constructor(game) {
    this.game = game;
    this.body = new Body(0.6, 1.8);
    this.yaw = 0;
    this.pitch = 0;
    this.bodyYaw = 0;
    this.mode = 'survival';
    this.flying = false;
    this.sprinting = false;
    this.sneaking = false;
    this.swimming = false;
    this.health = 20;
    this.food = 20;
    this.saturation = 5;
    this.exhaustion = 0;
    this.air = 300;
    this.inventory = new Array(36).fill(null);
    this.selected = 0;
    this.dead = false;
    this.hurtTime = 0;
    this.invuln = 0;
    this.regenTimer = 0;
    this.lavaTimer = 0;
    this.drownTimer = 0;
    this.portalTime = 0;
    this.portalCooldown = 0;
    this.mining = null; // {x,y,z,progress}
    this.swing = 0;
    this.attackCd = 0;
    this.placeCd = 0;
    this.eatTime = 0;
    this.cameraMode = 0; // 0 first, 1 third back, 2 third front
    this.lastJumpPress = -1;
    this.lastForwardPress = -1;
    this.stepDist = 0;
    this.bob = 0;
    this.spawn = null;
    this.model = makeHumanoid();
    this.model.visible = false;
    game.renderer.scene.add(this.model);
    this.heldMesh = null;
    this.heldId = -1;
    this.setupHand();
  }

  // ---------- inventory helpers ----------
  get held() { return this.inventory[this.selected]; }
  addItem(id, count, dmg = 0) {
    const max = maxStack(id);
    for (let i = 0; i < 36 && count > 0; i++) {
      const s = this.inventory[i];
      if (s && s.id === id && s.count < max && !ITEMS[id].durability) {
        const n = Math.min(count, max - s.count);
        s.count += n; count -= n;
      }
    }
    for (let i = 0; i < 36 && count > 0; i++) {
      if (!this.inventory[i]) {
        const n = Math.min(count, max);
        this.inventory[i] = { id, count: n, dmg };
        count -= n;
      }
    }
    this.game.ui.refreshHotbar();
    return count; // leftover
  }
  consumeHeld(n = 1) {
    if (this.mode === 'creative') return;
    const s = this.held;
    if (!s) return;
    s.count -= n;
    if (s.count <= 0) this.inventory[this.selected] = null;
    this.game.ui.refreshHotbar();
  }
  damageTool() {
    if (this.mode === 'creative') return;
    const s = this.held;
    if (!s || !ITEMS[s.id].durability) return;
    s.dmg = (s.dmg || 0) + 1;
    if (s.dmg >= ITEMS[s.id].durability) {
      this.inventory[this.selected] = null;
      this.game.audio.play('break_tool');
    }
    this.game.ui.refreshHotbar();
  }

  eyePos() {
    let h = EYE;
    if (this.sneaking && !this.flying) h = 1.32;
    if (this.swimming) h = 0.45;
    return new THREE.Vector3(this.body.pos.x, this.body.pos.y + h, this.body.pos.z);
  }
  lookDir() {
    const cp = Math.cos(this.pitch);
    return new THREE.Vector3(-Math.sin(this.yaw) * cp, Math.sin(this.pitch), -Math.cos(this.yaw) * cp);
  }

  hurt(dmg, knock, source) {
    if (this.dead || this.mode === 'creative' || this.invuln > 0) return;
    if (this.game.settings.difficulty === 0 && source && source.def) return;
    this.health -= dmg;
    this.invuln = 0.5;
    this.hurtTime = 0.4;
    this.exhaustion += 0.1;
    if (knock) { this.body.vel.x += knock.x || 0; this.body.vel.z += knock.z || 0; this.body.vel.y = Math.max(this.body.vel.y, 5); }
    this.game.audio.play('hurt_player');
    this.game.ui.flashDamage();
    if (this.health <= 0) this.die();
    this.game.ui.refreshStats();
  }

  die() {
    this.dead = true;
    this.health = 0;
    // drop inventory
    if (!this.game.settings.keepInventory) {
      for (let i = 0; i < 36; i++) {
        const s = this.inventory[i];
        if (s) { this.game.dropItem(this.body.pos.x, this.body.pos.y + 1, this.body.pos.z, s.id, s.count, null, s.dmg); this.inventory[i] = null; }
      }
    }
    this.game.ui.showDeath();
  }

  respawn() {
    this.dead = false;
    this.health = 20; this.food = 20; this.saturation = 5; this.air = 300;
    this.body.vel = { x: 0, y: 0, z: 0 };
    this.game.respawnPlayer();
    this.game.ui.refreshStats();
    this.game.ui.refreshHotbar();
  }

  setMode(mode) {
    this.mode = mode;
    if (mode !== 'creative') this.flying = false;
    this.game.ui.refreshStats();
  }

  // ---------- per-frame update ----------
  update(dt, input) {
    const g = this.game, b = this.body, w = g.world;
    if (this.dead) { this.updateModel(dt); return; }
    this.invuln = Math.max(0, this.invuln - dt);
    this.hurtTime = Math.max(0, this.hurtTime - dt);
    this.attackCd = Math.max(0, this.attackCd - dt);
    this.placeCd = Math.max(0, this.placeCd - dt);
    this.swing = Math.max(0, this.swing - dt * 4);
    this.portalCooldown = Math.max(0, this.portalCooldown - dt);

    // look
    this.yaw -= input.lookX;
    this.pitch -= input.lookY;
    this.pitch = Math.max(-Math.PI / 2 + 0.001, Math.min(Math.PI / 2 - 0.001, this.pitch));

    const creative = this.mode === 'creative';
    // toggles: double tap jump to fly
    if (input.jumpPressed) {
      const now = performance.now();
      if (creative && now - this.lastJumpPress < 300) { this.flying = !this.flying; b.vel.y = 0; }
      this.lastJumpPress = now;
    }
    if (input.toggleFly && creative) { this.flying = !this.flying; b.vel.y = 0; }
    if (this.flying && b.onGround && !input.jump && b.vel.y <= 0 && !creative) this.flying = false;

    // sprint
    if (input.forwardPressed) {
      const now = performance.now();
      if (now - this.lastForwardPress < 280) this.sprinting = true;
      this.lastForwardPress = now;
    }
    if (input.sprint) this.sprinting = true;
    const forward = input.moveY, strafe = input.moveX;
    if (forward <= 0.3 || (this.food <= 6 && !creative) || (b.hitWall && !this.flying)) this.sprinting = false;
    this.sneaking = input.sneak && !this.flying;

    // swimming: sprinting while submerged
    if (b.inWater && b.headInWater && this.sprinting && forward > 0) this.swimming = true;
    if (!b.inWater || !this.sprinting) this.swimming = false;
    b.h = this.swimming ? 0.6 : this.sneaking ? 1.5 : 1.8;

    // movement
    const sin = Math.sin(this.yaw), cos = Math.cos(this.yaw);
    let mx = -sin * forward + cos * strafe;
    let mz = -cos * forward - sin * strafe;
    const ml = Math.hypot(mx, mz);
    if (ml > 1) { mx /= ml; mz /= ml; }
    const below = w.getBlock(Math.floor(b.pos.x), Math.floor(b.pos.y - 0.2), Math.floor(b.pos.z));
    let speed = 4.317;
    if (this.flying) speed = this.sprinting ? 21.6 : 10.9;
    else if (this.sneaking) speed = 1.31;
    else if (this.sprinting) speed = 5.612;
    if (below === B.soul_sand && b.onGround) speed *= 0.45;
    if (b.inWater && !this.flying) speed = this.swimming ? 5.6 : 2.2;
    if (b.inLava && !this.flying) speed = 1.2;

    if (this.flying) {
      const acc = Math.min(1, dt * 8);
      b.vel.x += (mx * speed - b.vel.x) * acc;
      b.vel.z += (mz * speed - b.vel.z) * acc;
      let vy = 0;
      if (input.jump) vy += 7.5 * (this.sprinting ? 1.6 : 1);
      if (input.sneak) vy -= 7.5 * (this.sprinting ? 1.6 : 1);
      b.vel.y += (vy - b.vel.y) * acc;
    } else if (this.swimming) {
      const d = this.lookDir();
      const f = Math.max(0, forward);
      const acc = Math.min(1, dt * 4);
      b.vel.x += (d.x * f * speed + cos * strafe * 2 - b.vel.x) * acc;
      b.vel.z += (d.z * f * speed - sin * strafe * 2 - b.vel.z) * acc;
      b.vel.y += (d.y * f * speed - b.vel.y) * acc;
    } else if (b.inWater || b.inLava) {
      const acc = Math.min(1, dt * 5);
      b.vel.x += (mx * speed - b.vel.x) * acc;
      b.vel.z += (mz * speed - b.vel.z) * acc;
      const drag = b.inLava ? 0.5 : 0.8;
      b.vel.y *= Math.pow(drag, dt * 20 * 0.25);
      b.vel.y -= (b.inLava ? 4 : 9) * dt;
      if (input.jump) b.vel.y = Math.min(b.vel.y + 30 * dt, b.inLava ? 2 : 3.5);
      if (input.sneak) b.vel.y = Math.max(b.vel.y - 20 * dt, -3);
      // hop out of water at edges
      if (input.jump && b.hitWall && b.inWater) b.vel.y = 5;
      if (b.flow && b.inWater) { b.vel.x += b.flow.x * dt * 7; b.vel.z += b.flow.z * dt * 7; }
      b.vel.y = Math.max(b.vel.y, -4);
    } else {
      const slip = below === B.ice || below === B.blue_ice;
      const acc = Math.min(1, dt * (b.onGround ? (slip ? 1.5 : 14) : 2.2));
      b.vel.x += (mx * speed - b.vel.x) * acc;
      b.vel.z += (mz * speed - b.vel.z) * acc;
      b.vel.y -= 32 * dt;
      b.vel.y = Math.max(b.vel.y, -78);
      if (input.jump && b.onGround) {
        b.vel.y = 9.0;
        if (this.sprinting) { b.vel.x += mx * 1.8; b.vel.z += mz * 1.8; }
        this.exhaustion += this.sprinting ? 0.2 : 0.05;
      }
      // auto jump (mobile)
      if (g.settings.autoJump && b.onGround && b.hitWall && ml > 0.3 && !this.sneaking) b.vel.y = 9.0;
    }
    const fall = moveBody(w, b, dt, { sneakEdge: this.sneaking && !this.flying && b.onGround });
    if (this.flying && b.onGround && creative && input.sneak) this.flying = false;
    if (fall > 3 && !creative && !this.flying) {
      this.hurt(Math.floor(fall - 3), null, null);
      g.audio.play('fall');
    }
    if (b.onGround && fall > 0.5) g.audio.play('step', b.pos, below);
    // footsteps
    const hs = Math.hypot(b.vel.x, b.vel.z);
    if (b.onGround && hs > 0.5) {
      this.stepDist += hs * dt;
      if (this.stepDist > 1.8) { this.stepDist = 0; g.audio.play('step', b.pos, below); }
      this.exhaustion += hs * dt * (this.sprinting ? 0.1 : 0.01);
    }
    if ((b.inWater || b.inLava) && hs > 0.5 && Math.random() < dt * 2) g.audio.play('swim');

    // survival effects
    if (!creative) this.survivalTick(dt);

    // portal
    this.checkPortal(dt);

    // hotbar
    if (input.hotbar !== null) this.selectSlot(input.hotbar);
    if (input.scroll) this.selectSlot((this.selected + input.scroll + 9) % 9);
    if (input.drop) this.dropHeld(input.dropAll);

    this.interact(dt, input);
    this.updateModel(dt);
  }

  selectSlot(i) {
    if (i === this.selected) return;
    this.selected = i;
    this.mining = null;
    this.eatTime = 0;
    this.game.ui.refreshHotbar();
    this.game.ui.showItemName(this.held);
  }

  dropHeld(all) {
    const s = this.held;
    if (!s) return;
    const n = all ? s.count : 1;
    const d = this.lookDir();
    const e = this.eyePos();
    this.game.dropItem(e.x + d.x * 0.3, e.y - 0.3, e.z + d.z * 0.3, s.id, n, { x: d.x * 6, y: d.y * 6 + 2, z: d.z * 6 }, s.dmg);
    s.count -= n;
    if (s.count <= 0) this.inventory[this.selected] = null;
    this.game.ui.refreshHotbar();
  }

  survivalTick(dt) {
    const b = this.body;
    // drowning
    if (b.headInWater) {
      this.air -= dt * 20;
      if (this.air <= 0) {
        this.air = 0;
        this.drownTimer += dt;
        if (this.drownTimer > 1) { this.drownTimer = 0; this.hurt(2); }
      }
    } else this.air = Math.min(300, this.air + dt * 60);
    // lava
    if (b.inLava) {
      this.lavaTimer += dt;
      if (this.lavaTimer > 0.5) { this.lavaTimer = 0; this.invuln = 0; this.hurt(4); }
    }
    // cactus / magma
    const bx = Math.floor(b.pos.x), bz = Math.floor(b.pos.z), by = Math.floor(b.pos.y - 0.05);
    if (this.game.world.getBlock(bx, by, bz) === B.magma_block && !this.sneaking) this.hurt(1);
    for (const [dx, dz] of [[0.35, 0], [-0.35, 0], [0, 0.35], [0, -0.35]]) {
      if (this.game.world.getBlock(Math.floor(b.pos.x + dx), Math.floor(b.pos.y + 0.5), Math.floor(b.pos.z + dz)) === B.cactus) { this.hurt(1); break; }
    }
    // hunger
    this.exhaustion += dt * 0.005;
    if (this.exhaustion >= 4) {
      this.exhaustion -= 4;
      if (this.saturation > 0) this.saturation = Math.max(0, this.saturation - 1);
      else this.food = Math.max(0, this.food - 1);
      this.game.ui.refreshStats();
    }
    this.regenTimer += dt;
    if (this.regenTimer > 4) {
      this.regenTimer = 0;
      if (this.food >= 18 && this.health < 20) { this.health = Math.min(20, this.health + 1); this.exhaustion += 1.5; this.game.ui.refreshStats(); }
      else if (this.food === 0 && this.health > 1) this.hurt(1);
    }
  }

  checkPortal(dt) {
    const b = this.body, w = this.game.world;
    let inPortal = false;
    for (let y = Math.floor(b.pos.y); y <= Math.floor(b.pos.y + 1.5); y++)
      if (w.getBlock(Math.floor(b.pos.x), y, Math.floor(b.pos.z)) === B.nether_portal) inPortal = true;
    if (inPortal && this.portalCooldown <= 0) {
      this.portalTime += dt;
      if (Math.random() < dt * 10) this.game.audio.play('portal_ambient');
      const need = this.mode === 'creative' ? 1 : 3.5;
      if (this.portalTime >= need) {
        this.portalTime = 0;
        this.portalCooldown = 5;
        this.game.travelPortal();
      }
    } else this.portalTime = Math.max(0, this.portalTime - dt * 2);
  }

  // ---------- interaction ----------
  target() {
    const e = this.eyePos(), d = this.lookDir();
    const reach = this.mode === 'creative' ? 6 : 4.8;
    const held = this.held;
    const wantFluid = held && held.id === I.bucket;
    const hit = raycast(this.game.world, e.x, e.y, e.z, d.x, d.y, d.z, reach, (id, x, y, z) => {
      const b = BLOCKS[id];
      if (b.render === 'none') return false;
      if (b.liquid) return wantFluid && this.game.world.getMeta(x, y, z) === 0;
      return true;
    });
    // mobs
    let mob = null, md = hit ? hit.dist : reach;
    for (const m of this.game.mobs.list) {
      if (m.dead) continue;
      const bx = m.body.box();
      const t = rayBox(e.x, e.y, e.z, d.x, d.y, d.z, bx);
      if (t >= 0 && t < md) { md = t; mob = m; }
    }
    if (mob) return { mob, dist: md };
    return hit;
  }

  interact(dt, input) {
    const g = this.game, w = g.world;
    const t = this.target();
    this.currentTarget = t;
    const creative = this.mode === 'creative';
    const held = this.held;

    // attacking / mining
    if (t && t.mob) {
      this.mining = null;
      if (input.attackPressed || input.tapAttack || (input.attack && this.attackCd <= 0 && input.isTouch)) {
        if (this.attackCd <= 0) {
          this.attackCd = 0.45;
          this.swing = 1;
          const dmg = held && ITEMS[held.id].damage ? ITEMS[held.id].damage : 1;
          const d = this.lookDir();
          const crit = !this.body.onGround && this.body.vel.y < 0 ? 1.5 : 1;
          if (t.mob.hurt(dmg * crit, { x: d.x * (this.sprinting ? 9 : 5), z: d.z * (this.sprinting ? 9 : 5) }, this)) {
            if (held && ITEMS[held.id].tool) this.damageTool();
            this.exhaustion += 0.1;
          }
        }
      }
    } else if (t && input.attack) {
      if (input.attackPressed || this.swing <= 0.3) this.swing = 1;
      const id = t.id;
      if (creative) {
        if (input.attackPressed || this.placeCd <= 0) {
          if (!(held && ITEMS[held.id].tool && ITEMS[held.id].tool.type === 'sword')) {
            this.breakBlock(t.x, t.y, t.z, false);
          }
          this.placeCd = 0.25;
        }
      } else {
        if (!this.mining || this.mining.x !== t.x || this.mining.y !== t.y || this.mining.z !== t.z) {
          this.mining = { x: t.x, y: t.y, z: t.z, progress: 0, sound: 0 };
        }
        const bt = breakTime(id, held ? held.id : 0);
        let mult = 1;
        if (this.body.inWater && this.body.headInWater) mult *= 0.2;
        if (!this.body.onGround && !this.flying) mult *= 0.2;
        this.mining.progress += (dt / bt) * mult;
        this.mining.sound -= dt;
        if (this.mining.sound <= 0) { this.mining.sound = 0.25; g.audio.play('dig', { x: t.x, y: t.y, z: t.z }, id); g.particles.blockHit(t.x, t.y, t.z, t.face, id); }
        if (this.mining.progress >= 1) {
          this.breakBlock(t.x, t.y, t.z, true);
          this.mining = null;
          this.placeCd = 0.05;
        }
      }
    } else {
      this.mining = null;
    }

    // crack overlay
    const r = g.renderer;
    if (this.mining && this.mining.progress > 0) {
      r.crack.visible = true;
      r.crack.position.set(this.mining.x + 0.5, this.mining.y + 0.5, this.mining.z + 0.5);
      r.crackMat.uniforms.uLayer.value = TEX_INDEX['destroy_stage_' + Math.min(9, Math.floor(this.mining.progress * 10))];
    } else r.crack.visible = false;
    if (t && !t.mob) {
      r.selection.visible = true;
      const b = BLOCKS[t.id];
      if (b.render === 'cross' || b.render === 'torch' || b.render === 'flat') {
        r.selection.scale.set(b.render === 'torch' ? 0.25 : 0.8, b.render === 'flat' ? 0.05 : b.render === 'torch' ? 0.65 : 0.9, b.render === 'torch' ? 0.25 : 0.8);
        r.selection.position.set(t.x + 0.5, t.y + (b.render === 'flat' ? 0.03 : b.render === 'torch' ? 0.32 : 0.45), t.z + 0.5);
      } else {
        r.selection.scale.set(1, 1, 1);
        r.selection.position.set(t.x + 0.5, t.y + 0.5, t.z + 0.5);
      }
    } else r.selection.visible = false;

    // eating
    if (input.use && held && ITEMS[held.id].food && (this.food < 20 || creative) && !(t && !t.mob && BLOCKS[t.id].interact)) {
      this.eatTime += dt;
      if (Math.random() < dt * 6) { g.audio.play('eat'); const e = this.eyePos(), d = this.lookDir(); g.particles.burst(e.x + d.x * 0.4, e.y - 0.2, e.z + d.z * 0.4, [0.7, 0.5, 0.3], 2, 1, 0.06, 10); }
      if (this.eatTime >= 1.6) {
        this.eatTime = 0;
        const def = ITEMS[held.id];
        this.food = Math.min(20, this.food + def.food);
        this.saturation = Math.min(this.food, this.saturation + def.food * 0.6);
        this.consumeHeld();
        g.audio.play('burp');
        g.ui.refreshStats();
      }
      return;
    }
    this.eatTime = 0;

    // using / placing
    if (input.usePressed || (input.use && this.placeCd <= 0)) {
      if (!t || t.mob) return;
      this.placeCd = 0.25;
      const tb = BLOCKS[t.id];
      if (tb.interact && !input.sneak) {
        if (tb.interact === 'tnt' && held && held.id === I.flint_and_steel) {
          w.setBlock(t.x, t.y, t.z, 0);
          g.entities.push(new PrimedTNT(g, t.x, t.y, t.z));
          g.audio.play('fuse', { x: t.x, y: t.y, z: t.z });
          this.damageTool();
          this.swing = 1;
          return;
        }
        if (tb.interact !== 'tnt') {
          g.ui.openContainer(tb.interact, t.x, t.y, t.z);
          return;
        }
      }
      if (!held) return;
      const def = ITEMS[held.id];
      const px = t.x + t.face[0], py = t.y + t.face[1], pz = t.z + t.face[2];
      if (held.id === I.flint_and_steel) {
        this.swing = 1;
        if (w.getBlock(px, py, pz) === 0 && w.tryLightPortal(px, py, pz)) {
          g.audio.play('portal_ignite', { x: px, y: py, z: pz });
          this.damageTool();
        } else g.audio.play('flint');
        return;
      }
      if (held.id === I.bucket) {
        const L = tb.liquid;
        if (L && w.getMeta(t.x, t.y, t.z) === 0) {
          w.setBlock(t.x, t.y, t.z, 0);
          g.audio.play('bucket_fill');
          this.swing = 1;
          const full = L === 'water' ? I.water_bucket : I.lava_bucket;
          if (creative) return;
          if (held.count === 1) this.inventory[this.selected] = { id: full, count: 1 };
          else { held.count--; this.addItem(full, 1); }
          g.ui.refreshHotbar();
        }
        return;
      }
      if (def.places) {
        // place fluid into the target if replaceable, else adjacent
        let fx = px, fy = py, fz = pz;
        if (tb.replaceable && !tb.liquid) { fx = t.x; fy = t.y; fz = t.z; }
        const cur = w.getBlock(fx, fy, fz);
        if (cur === 0 || BLOCKS[cur].replaceable || BLOCKS[cur].liquid) {
          if (g.world.dim === 'nether' && def.places === B.water) {
            g.particles.burst(fx + 0.5, fy + 0.5, fz + 0.5, [0.9, 0.9, 0.9], 12, 1, 0.15, -1);
            g.audio.play('fizz');
          } else {
            w.setBlock(fx, fy, fz, def.places, 0);
            w.scheduleFluid(fx, fy, fz, 1);
          }
          g.audio.play('bucket_empty');
          this.swing = 1;
          if (!creative) { this.inventory[this.selected] = { id: I.bucket, count: 1 }; g.ui.refreshHotbar(); }
        }
        return;
      }
      if (def.isBlock) {
        let x = px, y = py, z = pz;
        if (tb.replaceable && !tb.liquid && t.id !== held.id) { x = t.x; y = t.y; z = t.z; }
        if (y < 0 || y >= 128) return;
        const cur = w.getBlock(x, y, z);
        if (cur !== 0 && !BLOCKS[cur].replaceable) return;
        const nb = BLOCKS[held.id];
        if (nb.solid) {
          const pb = this.body.box();
          if (boxIntersectsBlock(pb, x, y, z) && !this.flying) return;
          if (boxIntersectsBlock(pb, x, y, z) && this.flying) return;
          for (const m of g.mobs.list) if (!m.dead && boxIntersectsBlock(m.body.box(), x, y, z)) return;
        }
        const below = w.getBlock(x, y - 1, z);
        if ((nb.render === 'cross' || nb.render === 'flat') && !w.supportsPlant(held.id, below)) return;
        if (nb.render === 'torch' && !BLOCKS[below].solid) return;
        if (held.id === B.cactus && below !== B.sand && below !== B.red_sand && below !== B.cactus) return;
        let meta = 0;
        if (nb.facing) {
          // face toward the player
          const a = ((Math.round(this.yaw / (Math.PI / 2)) % 4) + 4) % 4;
          meta = [0, 3, 2, 1][a];
        }
        w.setBlock(x, y, z, held.id, meta);
        g.audio.play('place', { x, y, z }, held.id);
        this.swing = 1;
        this.consumeHeld();
      }
    }
  }

  breakBlock(x, y, z, drop) {
    const g = this.game, w = g.world;
    const id = w.getBlock(x, y, z);
    const b = BLOCKS[id];
    if (!id || b.hardness < 0) return;
    g.particles.blockBreak(x, y, z, id);
    g.audio.play('break', { x, y, z }, id);
    // ice melts into water when broken above a block
    if (id === B.ice && drop && BLOCKS[w.getBlock(x, y - 1, z)].solid && w.dim !== 'nether') {
      w.setBlock(x, y, z, B.water, 0);
      w.scheduleFluid(x, y, z, 1);
    } else w.setBlock(x, y, z, 0);
    // double plants above
    if (drop) {
      const held = this.held;
      if (canHarvest(id, held ? held.id : 0)) {
        for (const [did, n] of blockDrops(id)) g.dropItem(x + 0.5, y + 0.5, z + 0.5, did, n);
      }
      if (b.hardness > 0) this.damageTool();
      this.exhaustion += 0.005;
    }
    if (b.interact === 'chest' || b.interact === 'furnace') g.ui.dropContainerContents(x, y, z);
  }

  // ---------- model & camera ----------
  updateModel(dt) {
    const b = this.body;
    const m = this.model;
    m.position.set(b.pos.x, b.pos.y + (this.swimming ? 0.3 : 0), b.pos.z);
    // body yaw lags behind head yaw
    const moving = Math.hypot(b.vel.x, b.vel.z) > 0.3;
    let diff = this.yaw - this.bodyYaw;
    diff = ((diff + Math.PI) % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2) - Math.PI;
    if (moving || this.swimming || this.flying) this.bodyYaw += diff * Math.min(1, dt * 10);
    else if (Math.abs(diff) > 0.85) this.bodyYaw += (diff - Math.sign(diff) * 0.85);
    let headYaw = this.yaw - this.bodyYaw;
    headYaw = ((headYaw + Math.PI) % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2) - Math.PI;
    m.rotation.y = this.bodyYaw + Math.PI;
    if (this.dead) m.rotation.z = Math.min(Math.PI / 2, (m.rotation.z || 0) + dt * 5);
    else m.rotation.z = 0;
    animateHumanoid(m, {
      speed: Math.hypot(b.vel.x, b.vel.z), sprint: this.sprinting, sneak: this.sneaking, swimming: this.swimming,
      flying: this.flying, inWater: b.inWater, onGround: b.onGround, vy: b.vel.y, pitch: this.pitch, headYaw,
      swing: this.swing, holdingItem: !!this.held,
    }, dt);
    // held item in third-person hand
    const held = this.held;
    const hid = held ? held.id : 0;
    if (hid !== this.heldId) {
      this.heldId = hid;
      const hand = m.userData.hand;
      if (this.heldMesh) hand.remove(this.heldMesh);
      this.heldMesh = null;
      if (hid) {
        this.heldMesh = makeItemMesh(hid);
        if (this.heldMesh.userData.cube) { this.heldMesh.scale.setScalar(0.25); this.heldMesh.position.set(0, -0.05, 0.12); }
        else { this.heldMesh.scale.setScalar(0.55); this.heldMesh.rotation.set(-Math.PI / 2, Math.PI / 2, Math.PI / 4); this.heldMesh.position.set(0, -0.05, 0.18); }
        hand.add(this.heldMesh);
      }
      this.updateHandItem();
    }
    const bright = brightnessAt(this.game.world, b.pos.x, b.pos.y + 1.2, b.pos.z, this.game.day);
    setMeshBrightness(m, this.hurtTime > 0.2 ? 1.5 : bright);
    if (this.hurtTime > 0.2) m.traverse((o) => { if (o.isMesh && o.material.color) o.material.color.setRGB(1.4, 0.5, 0.5); });
    this.handBright = bright;
  }

  updateCamera(camera, dt) {
    const e = this.eyePos();
    const d = this.lookDir();
    // smooth eye height (sneak)
    this.eyeY = this.eyeY === undefined ? e.y : this.eyeY + (e.y - this.eyeY) * Math.min(1, dt * 15);
    if (Math.abs(this.eyeY - e.y) > 2) this.eyeY = e.y;
    e.y = this.eyeY;
    const hs = Math.hypot(this.body.vel.x, this.body.vel.z);
    if (this.body.onGround && hs > 0.5 && !this.flying) this.bob += dt * hs * 2.2;
    const bobAmt = this.game.settings.viewBobbing && this.cameraMode === 0 ? 1 : 0;
    this.model.visible = this.cameraMode !== 0;
    if (this.cameraMode === 0) {
      camera.position.copy(e);
      camera.position.y += Math.abs(Math.sin(this.bob)) * 0.06 * bobAmt * Math.min(1, hs / 4);
      camera.position.x += Math.cos(this.yaw) * Math.sin(this.bob) * 0.03 * bobAmt * Math.min(1, hs / 4);
      camera.rotation.set(this.pitch, this.yaw, 0, 'YXZ');
    } else {
      const dist = 4;
      const dir = this.cameraMode === 1 ? d.clone().negate() : d.clone();
      // pull camera in when blocked
      const hit = raycast(this.game.world, e.x, e.y, e.z, dir.x, dir.y, dir.z, dist, (id) => BLOCKS[id].solid && BLOCKS[id].opaque);
      const dd = hit ? Math.max(0.3, hit.dist - 0.3) : dist;
      camera.position.copy(e).addScaledVector(dir, dd);
      if (this.cameraMode === 1) camera.rotation.set(this.pitch, this.yaw, 0, 'YXZ');
      else camera.rotation.set(-this.pitch, this.yaw + Math.PI, 0, 'YXZ');
    }
    // FOV effects
    const base = this.game.settings.fov;
    let fov = base;
    if (this.sprinting) fov *= this.flying ? 1.18 : 1.12;
    camera.fov += (fov - camera.fov) * Math.min(1, dt * 8);
    camera.updateProjectionMatrix();
  }

  // ---------- first-person hand ----------
  setupHand() {
    this.handScene = new THREE.Scene();
    this.handCam = new THREE.PerspectiveCamera(70, 1, 0.01, 10);
    this.handRoot = new THREE.Group();
    this.handScene.add(this.handRoot);
    const skin = this.model.userData.mat;
    this.armMesh = new THREE.Mesh(skinBox(4, 12, 4, 40, 16, 64, 64), skin.clone());
    this.armMesh.scale.setScalar(0.9);
    this.handItem = null;
    this.handRoot.add(this.armMesh);
    this.equipAnim = 0;
  }

  updateHandItem() {
    if (this.handItem) this.handRoot.remove(this.handItem);
    this.handItem = null;
    this.equipAnim = 1;
    const held = this.held;
    this.armMesh.visible = !held;
    if (!held) return;
    const mesh = makeItemMesh(held.id);
    if (mesh.userData.cube) {
      mesh.scale.setScalar(0.26);
      mesh.rotation.set(0.1, Math.PI / 4, 0);
    } else {
      mesh.scale.setScalar(0.45);
      mesh.rotation.set(0, -Math.PI / 2 + 0.35, 0.25);
    }
    this.handItem = mesh;
    this.handRoot.add(mesh);
  }

  renderHand(renderer, dt) {
    if (this.cameraMode !== 0 || this.dead || this.game.settings.hideHud) return;
    const r = renderer.renderer;
    this.handCam.aspect = window.innerWidth / window.innerHeight;
    this.handCam.updateProjectionMatrix();
    this.equipAnim = Math.max(0, this.equipAnim - dt * 5);
    const hs = Math.hypot(this.body.vel.x, this.body.vel.z);
    const bobX = Math.sin(this.bob) * 0.02 * Math.min(1, hs / 4);
    const bobY = Math.abs(Math.cos(this.bob)) * 0.02 * Math.min(1, hs / 4);
    const sw = this.swing > 0 ? Math.sin((1 - this.swing) * Math.PI) : 0;
    const sw2 = this.swing > 0 ? Math.sin(Math.sqrt(1 - this.swing) * Math.PI) : 0;
    const eat = this.eatTime > 0 ? Math.abs(Math.sin(this.eatTime * 12)) * 0.03 : 0;
    const root = this.handRoot;
    root.position.set(0.56 + bobX - sw2 * 0.2 - (this.eatTime > 0 ? 0.3 : 0), -0.52 - bobY - this.equipAnim * 0.6 + sw * 0.1 + eat + (this.eatTime > 0 ? 0.15 : 0), -0.75 - sw * 0.15);
    root.rotation.set(-sw * 0.6, -sw2 * 0.4, sw * 0.3);
    if (this.handItem) {
      if (this.handItem.userData.cube) this.handItem.position.set(0.02, 0.06, 0.05);
      else this.handItem.position.set(0.02, 0.1, 0.05);
    }
    this.armMesh.position.set(0.16, -0.2, 0.18);
    this.armMesh.rotation.set(-1.25, 0.12, 0.35);
    setMeshBrightness(this.handRoot, this.handBright || 1);
    r.autoClear = false;
    r.clearDepth();
    r.render(this.handScene, this.handCam);
    r.autoClear = true;
  }
}

export function blockDrops(id) {
  const b = BLOCKS[id];
  const r = Math.random();
  if (id === B.gravel) return [[r < 0.12 ? I.flint : B.gravel, 1]];
  if (b.key.includes('leaves')) {
    const out = [];
    if ((id === B.oak_leaves || id === B.jungle_leaves) && r < 0.06) out.push([I.apple, 1]);
    if (Math.random() < 0.04) out.push([I.stick, 1]);
    return out;
  }
  if (id === B.tall_grass) return r < 0.12 ? [[I.wheat, 1]] : [];
  if (id === B.glowstone) return [[I.glowstone_dust, 2 + ((Math.random() * 3) | 0)]];
  if (id === B.clay) return [[I.clay_ball, 4]];
  if (id === B.coal_ore) return [[I.coal, 1]];
  if (id === B.nether_quartz_ore) return [[I.quartz, 1]];
  if (id === B.diamond_ore) return [[I.diamond, 1]];
  if (id === B.emerald_ore) return [[I.emerald, 1]];
  if (id === B.dead_bush) return r < 0.5 ? [[I.stick, 1 + ((Math.random() * 2) | 0)]] : [];
  if (!b.drop) return [];
  return [[b.drop, 1]];
}

export { ITEMS, isCubeItem };
