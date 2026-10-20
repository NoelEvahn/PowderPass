// Game bootstrap: menus, world lifecycle, chunk streaming, tick loop, dimensions, explosions.
import * as THREE from 'three';
import { BLOCKS, ITEMS, B, I, SMELTING, maxStack } from './blocks.js';
import { loadResourcePack, iconURL } from './textures.js';
import { World } from './world.js';
import { CS, CH, SEA } from './worldgen.js';
import { buildChunkMesh } from './mesher.js';
import { Renderer } from './render.js';
import { Player } from './player.js';
import { Input } from './input.js';
import { UI } from './ui.js';
import { Audio } from './audio.js';
import { MobManager } from './mobs.js';
import { ItemEntity, FallingBlock, PrimedTNT, Particles, clearColorCache } from './entities.js';
import { clearItemCache } from './models.js';
import * as Store from './storage.js';
import { hashString } from './noise.js';

const $ = (id) => document.getElementById(id);
const isTouch = matchMedia('(pointer: coarse)').matches || 'ontouchstart' in window;

const DEFAULT_SETTINGS = {
  renderDistance: isTouch ? 4 : 7,
  quality: isTouch ? 1 : 2, // 0 fast, 1 fancy, 2 shaders
  shadows: true,
  godRays: true,
  shadowDistance: 64,
  fov: 70,
  sensitivity: 1,
  brightness: 0.4,
  viewBobbing: true,
  autoJump: isTouch,
  difficulty: 1,
  volume: 0.5,
  pixelRatio: isTouch ? 1 : 1.25,
  invertY: false,
  keepInventory: false,
};

function loadSettings() {
  try {
    return { ...DEFAULT_SETTINGS, ...JSON.parse(localStorage.getItem('pc_settings') || '{}') };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

class Game {
  constructor() {
    this.settings = loadSettings();
    this.canvas = $('game');
    this.renderer = new Renderer(this.canvas, this.settings);
    this.audio = new Audio(this);
    this.audio.volume = this.settings.volume;
    this.input = new Input(this.canvas, this);
    this.particles = new Particles(this.renderer.scene);
    this.entities = [];
    this.mobs = new MobManager(this);
    this.world = null;
    this.player = null;
    this.ui = null;
    this.running = false;
    this.paused = false;
    this.dayTime = 0.05;
    this.day = 1;
    this.fps = 0;
    this.meshCount = 0;
    this.tickAcc = 0;
    this.saveTimer = 0;
    this.lastTime = performance.now();
    this.frames = 0;
    this.fpsTime = 0;
    this.spiral = [];
    this.buildSpiral();
    this.player = new Player(this);
    this.ui = new UI(this);
    this.menus();
    requestAnimationFrame((t) => this.loop(t));
  }

  buildSpiral() {
    const R = this.settings.renderDistance + 2;
    this.spiral = [];
    for (let dz = -R; dz <= R; dz++) for (let dx = -R; dx <= R; dx++) this.spiral.push([dx, dz, dx * dx + dz * dz]);
    this.spiral.sort((a, b) => a[2] - b[2]);
  }

  saveSettings() {
    localStorage.setItem('pc_settings', JSON.stringify(this.settings));
  }

  // ---------------- menus ----------------
  menus() {
    const show = (id) => {
      for (const s of document.querySelectorAll('.screen')) s.style.display = 'none';
      if (id) $(id).style.display = 'flex';
    };
    this.show = show;
    const on = (id, fn) => $(id).addEventListener('click', () => { this.audio.resume(); this.audio.play('click'); fn(); });
    on('btnPlay', () => this.showWorlds());
    on('btnSettingsTitle', () => this.openSettings('title'));
    on('btnCreateWorld', () => { show('createScreen'); $('worldName').value = 'New World'; $('worldSeed').value = ''; });
    on('btnBackWorlds', () => show('titleScreen'));
    on('btnCreateCancel', () => this.showWorlds());
    on('btnCreateGo', () => {
      const name = $('worldName').value.trim() || 'New World';
      const seedStr = $('worldSeed').value.trim();
      const seed = seedStr ? (/^-?\d+$/.test(seedStr) ? parseInt(seedStr, 10) | 0 : hashString(seedStr) | 0) : (Math.random() * 2 ** 31) | 0;
      const mode = document.querySelector('input[name=mode]:checked').value;
      this.startWorld({ id: 'w' + Date.now(), name, seed, mode, created: Date.now() }, true);
    });
    on('btnResume', () => this.resume());
    on('btnSettingsPause', () => this.openSettings('pause'));
    on('btnQuit', () => this.quitToTitle());
    on('btnCamera', () => { this.cycleCamera(); });
    on('btnGamemode', () => {
      this.player.setMode(this.player.mode === 'creative' ? 'survival' : 'creative');
      $('btnGamemode').textContent = 'Game Mode: ' + (this.player.mode === 'creative' ? 'Creative' : 'Survival');
    });
    on('btnRespawn', () => { $('death').style.display = 'none'; this.player.respawn(); this.resume(); });
    on('btnDeathQuit', () => { $('death').style.display = 'none'; this.player.respawn(); this.quitToTitle(); });
    on('btnSettingsDone', () => {
      this.saveSettings();
      if (this.settingsFrom === 'pause') show('pauseScreen'); else show('titleScreen');
    });
    $('packFile').addEventListener('change', async (e) => {
      const f = e.target.files[0];
      if (!f) return;
      $('packStatus').textContent = 'Loading pack...';
      try {
        const n = await loadResourcePack(f);
        clearItemCache();
        clearColorCache();
        $('packStatus').textContent = `Loaded ${n} textures from ${f.name}`;
        if (this.world) { for (const c of this.world.chunks.values()) c.dirty = true; this.ui.refreshHotbar(); this.player.heldId = -1; }
        $('titleLogoIcon').src = iconURL(B.grass);
      } catch (err) {
        $('packStatus').textContent = 'Failed: ' + err.message;
      }
    });
    $('titleLogoIcon').src = iconURL(B.grass);
    show('titleScreen');
  }

  async showWorlds() {
    this.show('worldsScreen');
    const list = $('worldList');
    list.innerHTML = '<div class="small">Loading...</div>';
    const worlds = await Store.listWorlds();
    list.innerHTML = '';
    if (!worlds.length) list.innerHTML = '<div class="small">No worlds yet. Create one!</div>';
    for (const w of worlds) {
      const row = document.createElement('div');
      row.className = 'worldRow';
      row.innerHTML = `<div><b></b><div class="small"></div></div><div class="rowBtns"><button class="play">Play</button><button class="del">Delete</button></div>`;
      row.querySelector('b').textContent = w.name;
      row.querySelector('.small').textContent = `${w.mode} · seed ${w.seed} · ${new Date(w.lastPlayed || w.created).toLocaleString()}`;
      row.querySelector('.play').onclick = () => { this.audio.resume(); this.startWorld(w, false); };
      row.querySelector('.del').onclick = async () => {
        if (!confirm(`Delete world "${w.name}"? This cannot be undone.`)) return;
        await Store.deleteWorld(w.id);
        this.showWorlds();
      };
      list.appendChild(row);
    }
  }

  openSettings(from) {
    this.settingsFrom = from;
    this.show('settingsScreen');
    const s = this.settings;
    const box = $('settingsBody');
    box.innerHTML = '';
    const slider = (label, key, min, max, step, fmt, onChange) => {
      const row = document.createElement('label');
      row.className = 'setRow';
      row.innerHTML = `<span></span><input type="range" min="${min}" max="${max}" step="${step}">`;
      const span = row.querySelector('span'), inp = row.querySelector('input');
      inp.value = s[key];
      const upd = () => { span.textContent = `${label}: ${fmt(s[key])}`; };
      upd();
      inp.oninput = () => { s[key] = parseFloat(inp.value); upd(); onChange && onChange(); };
      box.appendChild(row);
    };
    const toggle = (label, key, onChange, fmt) => {
      const b = document.createElement('button');
      const upd = () => { b.textContent = `${label}: ${fmt ? fmt(s[key]) : s[key] ? 'ON' : 'OFF'}`; };
      upd();
      b.onclick = () => { this.audio.play('click'); onChange ? onChange() : (s[key] = !s[key]); upd(); this.renderer.applySettings(); };
      box.appendChild(b);
    };
    const QN = ['Fast', 'Fancy', 'Shaders'];
    toggle('Graphics', 'quality', () => { s.quality = (s.quality + 1) % 3; this.renderer.applySettings(); }, (v) => QN[v]);
    toggle('Shadows', 'shadows');
    toggle('God Rays', 'godRays');
    slider('Render Distance', 'renderDistance', 2, 16, 1, (v) => v + ' chunks', () => this.buildSpiral());
    slider('Shadow Distance', 'shadowDistance', 32, 128, 8, (v) => v + ' blocks');
    slider('FOV', 'fov', 50, 110, 1, (v) => v, () => this.renderer.applySettings());
    slider('Sensitivity', 'sensitivity', 0.2, 3, 0.05, (v) => Math.round(v * 100) + '%');
    slider('Brightness', 'brightness', 0, 1, 0.05, (v) => (v < 0.05 ? 'Moody' : v > 0.95 ? 'Bright' : Math.round(v * 100) + '%'), () => this.renderer.applySettings());
    slider('Resolution', 'pixelRatio', 0.5, 2, 0.25, (v) => Math.round(v * 100) + '%', () => this.renderer.applySettings());
    slider('Volume', 'volume', 0, 1, 0.05, (v) => Math.round(v * 100) + '%', () => { this.audio.volume = s.volume; });
    toggle('View Bobbing', 'viewBobbing');
    toggle('Auto-Jump', 'autoJump');
    toggle('Invert Mouse', 'invertY');
    toggle('Difficulty', 'difficulty', () => { s.difficulty = (s.difficulty + 1) % 3; if (s.difficulty === 0) this.mobs.list.filter((m) => !m.def.passive).forEach((m) => { m.dead = true; m.remove(); }); }, (v) => ['Peaceful', 'Normal', 'Hard'][v]);
    toggle('Keep Inventory', 'keepInventory');
  }

  // ---------------- world lifecycle ----------------
  async startWorld(meta, isNew) {
    this.show('loadingScreen');
    $('loadingText').textContent = 'Loading world...';
    await new Promise((r) => setTimeout(r, 30));
    this.meta = meta;
    this.seed = meta.seed;
    const chunks = isNew ? {} : await Store.loadChunks(meta.id, CS * CS * CH);
    this.store = { chunks, tiles: meta.tiles || {} };
    const p = this.player;
    p.inventory = (meta.inventory || new Array(36).fill(null)).map((s) => (s ? { ...s } : null));
    p.health = meta.health ?? 20;
    p.food = meta.food ?? 20;
    p.mode = meta.mode || 'survival';
    p.flying = false;
    p.dead = false;
    p.selected = 0;
    this.dayTime = meta.dayTime ?? 0.05;
    this.spawnPoint = meta.spawn || null;
    this.lastPortal = meta.lastPortal || null;
    const dim = meta.dim || 'overworld';
    this.mobs.clear();
    for (const e of this.entities) e.remove();
    this.entities = [];
    this.setDimension(dim);
    if (meta.pos) {
      p.body.pos = { ...meta.pos };
      p.yaw = meta.yaw || 0; p.pitch = meta.pitch || 0;
    } else {
      const sp = this.findSpawn();
      this.spawnPoint = sp;
      p.body.pos = { x: sp.x + 0.5, y: sp.y, z: sp.z + 0.5 };
      if (p.mode === 'creative' && isNew) this.giveCreativeStarter();
    }
    p.body.vel = { x: 0, y: 0, z: 0 };
    await this.preload();
    if (!meta.pos) {
      const y = this.world.findSafeY(Math.floor(p.body.pos.x), Math.floor(p.body.pos.z), SEA + 10);
      if (y > 0) p.body.pos.y = y;
      this.spawnPoint = { x: Math.floor(p.body.pos.x), y: p.body.pos.y, z: Math.floor(p.body.pos.z) };
    }
    p.setMode(p.mode);
    this.ui.refreshHotbar();
    this.ui.refreshStats();
    this.running = true;
    this.paused = false;
    this.show(null);
    $('hud').style.display = 'block';
    this.input.enabled = true;
    this.input.requestLock();
    this.ui.message(isNew ? `Welcome to ${meta.name}!` : `Loaded ${meta.name}`);
    if (isNew) this.ui.message(isTouch ? 'Drag to look · tap to place · hold to break' : 'WASD move · Space jump · E inventory · F5 camera · Esc menu', 8);
    this.save();
  }

  giveCreativeStarter() {
    const items = [B.grass, B.stone, B.oak_planks, B.glass, B.torch, B.oak_log, B.obsidian, I.flint_and_steel, I.water_bucket];
    items.forEach((id, i) => { this.player.inventory[i] = { id, count: maxStack(id) }; });
  }

  findSpawn() {
    const gen = this.world.gen;
    for (let r = 0; r < 400; r += 8) {
      for (let a = 0; a < 16; a++) {
        const x = Math.round(Math.cos((a / 16) * Math.PI * 2) * r), z = Math.round(Math.sin((a / 16) * Math.PI * 2) * r);
        const c = gen.column(x, z);
        if (c.h > SEA && c.h < 90 && c.biome !== 'beach' && c.biome !== 'ocean') return { x, y: c.h + 1, z };
      }
    }
    return { x: 0, y: 80, z: 0 };
  }

  setDimension(dim) {
    if (this.world) {
      this.world.saveModified();
      for (const c of this.world.chunks.values()) this.disposeMesh(c);
    }
    this.world = new World(this.seed, dim, this.store);
    this.world.listeners.blockChange.push((e) => this.onWorldEvent(e));
    this.renderer.updateEnvironment(this.dayTime, dim, false, this.settings.renderDistance);
  }

  // generate + mesh the area around the player before starting
  async preload() {
    const p = this.player.body.pos;
    const pcx = Math.floor(p.x / 16), pcz = Math.floor(p.z / 16);
    const R = Math.min(3, this.settings.renderDistance);
    let total = 0, done = 0;
    const list = this.spiral.filter(([dx, dz]) => Math.abs(dx) <= R + 1 && Math.abs(dz) <= R + 1);
    total = list.length;
    for (const [dx, dz] of list) {
      const c = this.world.ensureChunk(pcx + dx, pcz + dz);
      this.world.lightChunk(c);
      done++;
      if (done % 4 === 0) {
        $('loadingText').textContent = `Generating terrain... ${Math.round((done / total) * 100)}%`;
        await new Promise((r) => setTimeout(r, 0));
      }
    }
    for (const [dx, dz] of list) {
      if (Math.abs(dx) <= R && Math.abs(dz) <= R) this.meshChunk(this.world.getChunk(pcx + dx, pcz + dz));
    }
  }

  save() {
    if (!this.meta || !this.world) return;
    this.world.saveModified();
    const p = this.player;
    const m = this.meta;
    Object.assign(m, {
      lastPlayed: Date.now(), dim: this.world.dim, pos: { ...p.body.pos }, yaw: p.yaw, pitch: p.pitch,
      inventory: p.inventory.map((s) => (s ? { ...s } : null)), health: p.health, food: p.food, mode: p.mode,
      dayTime: this.dayTime, spawn: this.spawnPoint, tiles: this.store.tiles, lastPortal: this.lastPortal,
    });
    if (p.dead) { m.pos = null; m.health = 20; m.food = 20; }
    Store.saveWorldMeta(JSON.parse(JSON.stringify(m)));
    Store.saveChunks(m.id, this.store.chunks);
  }

  quitToTitle() {
    this.save();
    this.running = false;
    this.paused = false;
    this.input.enabled = false;
    document.exitPointerLock && document.exitPointerLock();
    for (const c of this.world.chunks.values()) this.disposeMesh(c);
    this.world = null;
    this.mobs.clear();
    for (const e of this.entities) e.remove();
    this.entities = [];
    $('hud').style.display = 'none';
    this.ui.close();
    this.show('titleScreen');
  }

  pause() {
    if (!this.running || this.player.dead) return;
    this.ui.close();
    this.paused = true;
    this.input.enabled = false;
    document.exitPointerLock && document.exitPointerLock();
    $('btnGamemode').textContent = 'Game Mode: ' + (this.player.mode === 'creative' ? 'Creative' : 'Survival');
    $('btnCamera').textContent = 'Camera: ' + ['First Person', 'Third Person (Back)', 'Third Person (Front)'][this.player.cameraMode];
    this.show('pauseScreen');
    this.save();
  }
  resume() {
    this.paused = false;
    this.show(null);
    this.input.enabled = true;
    this.input.requestLock();
  }
  onPointerUnlock() {
    if (this.running && !this.paused && !this.ui.open && !this.player.dead) this.pause();
  }

  cycleCamera() {
    this.player.cameraMode = (this.player.cameraMode + 1) % 3;
    $('btnCamera').textContent = 'Camera: ' + ['First Person', 'Third Person (Back)', 'Third Person (Front)'][this.player.cameraMode];
  }

  onKey(code, e) {
    this.audio.resume();
    if (!this.running) return;
    if (code === 'Escape') {
      if (this.ui.open) { this.ui.close(); return; }
      if (this.paused && $('pauseScreen').style.display !== 'none') { this.resume(); return; }
      if (!this.paused) this.pause();
      return;
    }
    if (this.paused || this.player.dead) return;
    if (code === 'KeyE') { this.ui.toggleInventory(); return; }
    if (this.ui.open) return;
    if (code === 'F5' || code === 'KeyV') this.cycleCamera();
    if (code === 'F3') this.ui.toggleDebug();
    if (code === 'F1') { this.settings.hideHud = !this.settings.hideHud; $('hud').style.display = this.settings.hideHud ? 'none' : 'block'; }
    if (code === 'F4') { this.player.setMode(this.player.mode === 'creative' ? 'survival' : 'creative'); this.ui.message('Game mode: ' + this.player.mode); }
    if (code === 'KeyF' && this.player.mode === 'creative') { this.player.flying = !this.player.flying; }
    void e;
  }

  pickBlock() {
    const t = this.player.currentTarget;
    if (!t || t.mob) return;
    let id = t.id === B.lit_furnace ? B.furnace : t.id;
    if (BLOCKS[id].liquid || id === B.nether_portal) return;
    const p = this.player;
    const idx = p.inventory.findIndex((s, i) => i < 9 && s && s.id === id);
    if (idx >= 0) { p.selectSlot(idx); return; }
    if (p.mode === 'creative') {
      p.inventory[p.selected] = { id, count: maxStack(id) };
      this.ui.refreshHotbar();
      p.heldId = -1;
    }
  }

  // ---------------- entities ----------------
  dropItem(x, y, z, id, count, vel, dmg) {
    if (!id || !ITEMS[id]) return;
    while (count > 0) {
      const n = Math.min(count, maxStack(id));
      const e = new ItemEntity(this, x, y, z, id, n, vel ? { ...vel } : undefined);
      e.dmg = dmg;
      this.entities.push(e);
      count -= n;
    }
  }

  onWorldEvent(e) {
    if (e.type === 'drop') {
      this.dropItem(e.x + 0.5, e.y + 0.5, e.z + 0.5, BLOCKS[e.id].drop || e.id, 1);
    } else if (e.type === 'fluidBreak') {
      const d = BLOCKS[e.id].drop;
      if (d) this.dropItem(e.x + 0.5, e.y + 0.5, e.z + 0.5, d, 1);
    } else if (e.type === 'fizz') {
      this.particles.burst(e.x + 0.5, e.y + 1, e.z + 0.5, [0.85, 0.85, 0.85], 10, 1, 0.15, -2);
      this.audio.play('fizz', e);
    } else if (e.type === 'tileRemoved') {
      const te = e.te;
      const items = te.type === 'chest' ? te.items : [te.input, te.fuel, te.output];
      for (const s of items) if (s) this.dropItem(e.x + 0.5, e.y + 0.5, e.z + 0.5, s.id, s.count, null, s.dmg);
      if (this.ui.tile === te) this.ui.close();
    }
  }

  explode(x, y, z, power) {
    const w = this.world;
    this.audio.play('explode', { x, y, z });
    this.particles.burst(x, y, z, [0.9, 0.9, 0.9], 60, 8, 0.4, -1);
    this.particles.burst(x, y, z, [0.3, 0.3, 0.3], 40, 6, 0.5, -0.5);
    const r = power;
    const broken = [];
    for (let dx = -r; dx <= r; dx++)
      for (let dy = -r; dy <= r; dy++)
        for (let dz = -r; dz <= r; dz++) {
          const d = Math.sqrt(dx * dx + dy * dy + dz * dz);
          if (d > r * (0.7 + Math.random() * 0.4)) continue;
          const bx = Math.floor(x + dx), by = Math.floor(y + dy), bz = Math.floor(z + dz);
          const id = w.getBlock(bx, by, bz);
          if (!id) continue;
          const b = BLOCKS[id];
          if (b.hardness < 0 || b.hardness >= 50 || b.liquid) continue;
          if (id === B.tnt) { w.setBlock(bx, by, bz, 0); this.entities.push(new PrimedTNT(this, bx, by, bz, 0.5 + Math.random())); continue; }
          broken.push([bx, by, bz, id]);
        }
    for (const [bx, by, bz, id] of broken) {
      w.setBlock(bx, by, bz, 0);
      if (Math.random() < 0.3) {
        const d = BLOCKS[id].drop;
        if (d) this.dropItem(bx + 0.5, by + 0.5, bz + 0.5, d, 1);
      }
    }
    // damage entities
    const hurt = (ent, isPlayer) => {
      const b = ent.body;
      const dx = b.pos.x - x, dy = b.pos.y + b.h / 2 - y, dz = b.pos.z - z;
      const d = Math.hypot(dx, dy, dz);
      if (d > power * 2) return;
      const f = 1 - d / (power * 2);
      const dmg = Math.round(f * power * 5);
      const kx = (dx / (d || 1)) * f * 18, kz = (dz / (d || 1)) * f * 18;
      if (isPlayer) { if (ent.mode !== 'creative') { ent.invuln = 0; ent.hurt(dmg, { x: kx, z: kz }); } }
      else { ent.hurtTime = 0; ent.hurt(dmg, { x: kx, z: kz }, null); }
      b.vel.y += f * 10;
    };
    hurt(this.player, true);
    for (const m of this.mobs.list) if (!m.dead) hurt(m, false);
    for (const e of this.entities) if (e instanceof ItemEntity) { e.body.vel.y += 5; }
  }

  respawnPlayer() {
    if (this.world.dim !== 'overworld') {
      this.setDimension('overworld');
    }
    const sp = this.spawnPoint || this.findSpawn();
    const p = this.player;
    p.body.pos = { x: sp.x + 0.5, y: sp.y, z: sp.z + 0.5 };
    // ensure chunks
    for (let dz = -1; dz <= 1; dz++)
      for (let dx = -1; dx <= 1; dx++) {
        const c = this.world.ensureChunk((sp.x >> 4) + dx, (sp.z >> 4) + dz);
        this.world.lightChunk(c);
      }
    const y = this.world.findSafeY(sp.x, sp.z, sp.y);
    if (y > 0) p.body.pos.y = y;
  }

  travelPortal() {
    const w = this.world;
    const p = this.player;
    const toNether = w.dim === 'overworld';
    const scale = toNether ? 1 / 8 : 8;
    const tx = Math.floor(p.body.pos.x * scale), tz = Math.floor(p.body.pos.z * scale);
    this.audio.play('travel');
    this.mobs.clear();
    for (const e of this.entities) e.remove();
    this.entities = [];
    this.setDimension(toNether ? 'nether' : 'overworld');
    const nw = this.world;
    for (let dz = -2; dz <= 2; dz++)
      for (let dx = -2; dx <= 2; dx++) {
        const c = nw.ensureChunk((tx >> 4) + dx, (tz >> 4) + dz);
        nw.lightChunk(c);
      }
    let portal = nw.findPortalNear(tx, 64, tz, 20);
    if (!portal) {
      let y = toNether ? nw.findSafeY(tx, tz, 50) : nw.findSafeY(tx, tz, SEA + 5);
      if (y < 0 || y > CH - 8 || (toNether && y < 33)) y = toNether ? 64 : SEA + 5;
      nw.buildPortal(tx, y, tz);
      portal = [tx, y, tz];
    }
    p.body.pos = { x: portal[0] + 0.5, y: portal[1], z: portal[2] + 1.5 };
    p.body.vel = { x: 0, y: 0, z: 0 };
    p.yaw = 0;
    p.portalTime = 0;
    p.portalCooldown = 4;
    this.ui.message(toNether ? 'Entered the Nether' : 'Returned to the Overworld');
  }

  // ---------------- chunk streaming ----------------
  disposeMesh(c) {
    if (!c.mesh) return;
    for (const m of [c.mesh.solid, c.mesh.trans]) {
      if (m) { this.renderer.scene.remove(m); m.geometry.dispose(); }
    }
    c.mesh = null;
    this.meshCount--;
  }

  meshChunk(c) {
    const res = buildChunkMesh(this.world, c);
    const had = !!c.mesh;
    this.disposeMesh(c);
    const mk = (geo, mat, shadow) => {
      if (!geo) return null;
      const m = new THREE.Mesh(geo, mat);
      m.position.set(c.cx * CS, 0, c.cz * CS);
      m.matrixAutoUpdate = false;
      m.updateMatrix();
      m.userData.chunk = true;
      if (shadow) m.layers.enable(1);
      this.renderer.scene.add(m);
      return m;
    };
    c.mesh = { solid: mk(res.solid, this.renderer.solidMat, true), trans: mk(res.trans, this.renderer.waterMat, false) };
    if (c.mesh.trans) c.mesh.trans.renderOrder = 2;
    c.torches = res.torches;
    c.dirty = false;
    this.meshCount++;
    void had;
  }

  streamChunks() {
    const w = this.world;
    const p = this.player.body.pos;
    const pcx = Math.floor(p.x / 16), pcz = Math.floor(p.z / 16);
    const R = this.settings.renderDistance;
    const t0 = performance.now();
    const genBudget = isTouch ? 6 : 8;
    // generate data within R+1
    for (const [dx, dz] of this.spiral) {
      if (Math.max(Math.abs(dx), Math.abs(dz)) > R + 1) continue;
      if (!w.getChunk(pcx + dx, pcz + dz)) {
        const c = w.ensureChunk(pcx + dx, pcz + dz);
        w.lightChunk(c);
        if (performance.now() - t0 > genBudget) break;
      }
    }
    // mesh dirty chunks within R whose neighbours exist
    const t1 = performance.now();
    let meshed = 0;
    for (const [dx, dz] of this.spiral) {
      if (Math.max(Math.abs(dx), Math.abs(dz)) > R) continue;
      const c = w.getChunk(pcx + dx, pcz + dz);
      if (!c || !c.dirty) continue;
      let ok = true;
      for (let oz = -1; oz <= 1 && ok; oz++) for (let ox = -1; ox <= 1 && ok; ox++) if (!w.getChunk(c.cx + ox, c.cz + oz)) ok = false;
      if (!ok) continue;
      this.meshChunk(c);
      meshed++;
      if (performance.now() - t1 > (isTouch ? 6 : 8) && meshed > 0) break;
    }
    // unload far chunks
    if (this.frames % 30 === 0) {
      for (const c of [...w.chunks.values()]) {
        if (Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) > R + 3) {
          this.disposeMesh(c);
          w.unloadChunk(c);
        }
      }
    }
    // hide meshes outside render distance (circular)
    for (const c of w.chunks.values()) {
      if (!c.mesh) continue;
      const d = Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz));
      const vis = d <= R;
      if (c.mesh.solid) c.mesh.solid.visible = vis;
      if (c.mesh.trans) c.mesh.trans.visible = vis;
    }
  }

  // ---------------- tick (20 TPS) ----------------
  tick() {
    const w = this.world;
    w.tick++;
    w.fluidTick();
    w.processUpdates((x, y, z, id) => this.entities.push(new FallingBlock(this, x, y, z, id)));
    // furnaces
    for (const [key, te] of Object.entries(w.tileEntities)) {
      if (te.type !== 'furnace' || !key.startsWith(w.dim + ':')) continue;
      const [x, y, z] = key.slice(w.dim.length + 1).split(',').map(Number);
      if (!w.isLoaded(x, z)) continue;
      this.tickFurnace(te, x, y, z);
    }
    this.ui.tickGUI();
  }

  tickFurnace(te, x, y, z) {
    const w = this.world;
    const out = te.input ? SMELTING[te.input.id] : undefined;
    const can = out !== undefined && (!te.output || (te.output.id === out && te.output.count < maxStack(out)));
    if (te.burn > 0) te.burn -= 0.05;
    if (te.burn <= 0 && can && te.fuel) {
      te.burnMax = te.burn = ITEMS[te.fuel.id].fuel || 0;
      if (te.fuel.id === I.lava_bucket) te.fuel = { id: I.bucket, count: 1 };
      else { te.fuel.count--; if (te.fuel.count <= 0) te.fuel = null; }
      te.changed = true;
    }
    if (te.burn > 0 && can) {
      te.cook += 0.05;
      if (te.cook >= 10) {
        te.cook = 0;
        te.input.count--;
        if (te.input.count <= 0) te.input = null;
        if (te.output) te.output.count++; else te.output = { id: out, count: 1 };
        te.changed = true;
      }
    } else te.cook = Math.max(0, te.cook - 0.1);
    if (te.burn < 0) te.burn = 0;
    const id = w.getBlock(x, y, z);
    const lit = te.burn > 0;
    if (lit && id === B.furnace) w.setBlock(x, y, z, B.lit_furnace, w.getMeta(x, y, z), { keepTile: true });
    else if (!lit && id === B.lit_furnace) w.setBlock(x, y, z, B.furnace, w.getMeta(x, y, z), { keepTile: true });
  }

  // ---------------- main loop ----------------
  loop(t) {
    requestAnimationFrame((tt) => this.loop(tt));
    const dt = Math.min(0.05, (t - this.lastTime) / 1000);
    this.lastTime = t;
    this.frames++;
    this.fpsTime += dt;
    if (this.fpsTime >= 1) { this.fps = Math.round(this.frames / this.fpsTime); this.frames = 0; this.fpsTime = 0; }
    const r = this.renderer;
    if (!this.running) {
      // title background: slow camera pan if a world is not loaded
      r.renderer.setClearColor(0x6a8fd8);
      r.renderer.clear();
      return;
    }
    const p = this.player;
    const input = this.input.poll(this.settings);
    if (!this.paused) {
      // physics substeps for stability
      const steps = dt > 0.025 ? 2 : 1;
      for (let i = 0; i < steps; i++) p.update(dt / steps, i === 0 ? input : { ...input, lookX: 0, lookY: 0, jumpPressed: false, attackPressed: false, usePressed: false, hotbar: null, scroll: 0, drop: false, forwardPressed: false, toggleFly: false, tapAttack: false });
      this.tickAcc += dt;
      let n = 0;
      while (this.tickAcc >= 0.05 && n++ < 4) { this.tickAcc -= 0.05; this.tick(); }
      this.dayTime += dt / 1200;
      this.mobs.update(dt);
      for (const e of this.entities) e.update(dt);
      this.pickupItems(dt);
      this.entities = this.entities.filter((e) => !e.dead);
      this.ambientParticles(dt);
      // splash
      if (p.body.inWater && !this.wasInWater && p.body.vel.y < -4) {
        this.audio.play('splash');
        this.particles.burst(p.body.pos.x, p.body.pos.y + 0.5, p.body.pos.z, [0.6, 0.75, 1], 20, 3, 0.1, 12);
      }
      this.wasInWater = p.body.inWater;
      this.saveTimer += dt;
      if (this.saveTimer > 30) { this.saveTimer = 0; this.save(); }
    }
    this.streamChunks();
    this.particles.update(dt, this.world, this.day);
    p.updateCamera(r.camera, dt);
    // underwater check at camera
    const cp = r.camera.position;
    const cid = this.world.getBlock(Math.floor(cp.x), Math.floor(cp.y), Math.floor(cp.z));
    let underwater = false;
    if (BLOCKS[cid].liquid === 'water') {
      const h = this.world.fluidHeight(Math.floor(cp.x), Math.floor(cp.y), Math.floor(cp.z), 'water');
      underwater = cp.y < Math.floor(cp.y) + h;
    }
    const inLava = BLOCKS[cid].liquid === 'lava';
    r.updateEnvironment(this.dayTime % 1, this.world.dim, underwater, this.settings.renderDistance);
    this.day = r.uniforms.uDay.value;
    r.render(dt, {
      shadowCenter: new THREE.Vector3(p.body.pos.x, p.body.pos.y, p.body.pos.z),
      inLava, damage: p.hurtTime > 0 ? p.hurtTime / 0.4 : 0, portal: Math.min(1, p.portalTime / 3),
    });
    p.renderHand(r, dt);
    this.ui.update(dt);
  }

  pickupItems(dt) {
    const p = this.player;
    if (p.dead) return;
    const pp = p.body.pos;
    for (const e of this.entities) {
      if (!(e instanceof ItemEntity) || e.dead || e.pickupDelay > 0) continue;
      const b = e.body.pos;
      const dx = pp.x - b.x, dy = pp.y + 0.8 - b.y, dz = pp.z - b.z;
      const d = Math.hypot(dx, dy, dz);
      if (d < 1.6) {
        if (d < 0.7) {
          const left = p.addItem(e.id, e.count, e.dmg);
          if (left < e.count) this.audio.play('pop');
          if (left === 0) e.remove();
          else e.count = left;
        } else {
          e.body.vel.x = (dx / d) * 8; e.body.vel.y = (dy / d) * 8; e.body.vel.z = (dz / d) * 8;
        }
      }
    }
    // merge nearby item stacks
    if (this.frames % 20 === 0) {
      const items = this.entities.filter((e) => e instanceof ItemEntity && !e.dead);
      for (let i = 0; i < items.length; i++)
        for (let j = i + 1; j < items.length; j++) {
          const a = items[i], b = items[j];
          if (a.dead || b.dead || a.id !== b.id || ITEMS[a.id].durability) continue;
          if (Math.hypot(a.body.pos.x - b.body.pos.x, a.body.pos.y - b.body.pos.y, a.body.pos.z - b.body.pos.z) < 1 && a.count + b.count <= maxStack(a.id)) {
            a.count += b.count;
            b.remove();
          }
        }
    }
    void dt;
  }

  ambientParticles(dt) {
    const p = this.player.body.pos;
    const w = this.world;
    // torch flames and smoke
    const pcx = Math.floor(p.x / 16), pcz = Math.floor(p.z / 16);
    for (let dz = -1; dz <= 1; dz++)
      for (let dx = -1; dx <= 1; dx++) {
        const c = w.getChunk(pcx + dx, pcz + dz);
        if (!c || !c.torches) continue;
        for (let i = 0; i < c.torches.length; i += 3) {
          if (Math.random() < dt * 2) {
            const x = c.torches[i] + 0.5, y = c.torches[i + 1] + 0.75, z = c.torches[i + 2] + 0.5;
            this.particles.add(x, y, z, 0, 0.3, 0, [1, 0.75, 0.3], 0.07, 0.4, -0.5);
            this.particles.add(x, y + 0.1, z, (Math.random() - 0.5) * 0.2, 0.6, (Math.random() - 0.5) * 0.2, [0.3, 0.3, 0.3], 0.08, 1.0, -0.3);
          }
        }
      }
    if (w.dim === 'nether') {
      for (let i = 0; i < 3; i++) {
        if (Math.random() < dt * 20) {
          this.particles.add(p.x + (Math.random() - 0.5) * 30, p.y + (Math.random() - 0.3) * 16, p.z + (Math.random() - 0.5) * 30, (Math.random() - 0.5) * 0.3, -0.2, (Math.random() - 0.5) * 0.3, Math.random() < 0.5 ? [0.6, 0.25, 0.15] : [0.25, 0.22, 0.22], 0.05, 6, 0);
        }
      }
    }
    // portal particles near the player
    if (Math.random() < dt * 30) {
      const x = Math.floor(p.x + (Math.random() - 0.5) * 12), y = Math.floor(p.y + (Math.random() - 0.5) * 8), z = Math.floor(p.z + (Math.random() - 0.5) * 12);
      const id = w.getBlock(x, y, z);
      if (id === B.nether_portal) this.particles.add(x + Math.random(), y + Math.random(), z + Math.random(), (Math.random() - 0.5), (Math.random() - 0.5), (Math.random() - 0.5), [0.6, 0.2, 0.9], 0.08, 1.2, 0);
      else if (id === B.lava && w.getBlock(x, y + 1, z) === 0 && Math.random() < 0.3) this.particles.add(x + Math.random(), y + 1, z + Math.random(), (Math.random() - 0.5) * 2, 4, (Math.random() - 0.5) * 2, [1, 0.6, 0.1], 0.08, 1.2, 12);
    }
    // underwater bubbles
    if (this.player.body.headInWater && Math.random() < dt * 4) {
      const e = this.player.eyePos();
      this.particles.add(e.x + (Math.random() - 0.5) * 0.5, e.y, e.z + (Math.random() - 0.5) * 0.5, 0, 1.5, 0, [0.7, 0.85, 1], 0.06, 1.5, -1);
    }
  }
}

window.addEventListener('beforeunload', () => { if (window.game && window.game.running) window.game.save(); });
document.addEventListener('visibilitychange', () => { if (document.hidden && window.game && window.game.running) window.game.save(); });

try {
  window.game = new Game();
} catch (e) {
  console.error(e);
  document.getElementById('fatal').style.display = 'flex';
  document.getElementById('fatal').textContent = 'Failed to start: ' + e.message + ' (WebGL2 is required)';
}
