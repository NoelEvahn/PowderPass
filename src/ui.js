// HUD, menus and inventory GUIs (player inventory, crafting table, furnace, chest, creative).
import { ITEMS, BLOCKS, I, B, maxStack, matchRecipe, SMELTING, creativeList } from './blocks.js';
import { iconURL } from './textures.js';
import { BIOMES } from './worldgen.js';

const $ = (id) => document.getElementById(id);

// ---- tiny pixel icons for hearts/hunger/air ----
function pixIcon(rows, pal) {
  const c = document.createElement('canvas');
  c.width = c.height = 9;
  const ctx = c.getContext('2d');
  rows.forEach((r, y) => [...r].forEach((ch, x) => { if (pal[ch]) { ctx.fillStyle = pal[ch]; ctx.fillRect(x, y, 1, 1); } }));
  return c.toDataURL();
}
const HEART = ['.kk...kk.', 'krrk.krrk', 'krwrrrrrk', 'krrrrrrrk', 'krrrrrrrk', '.krrrrrk.', '..krrrk..', '...krk...', '....k....'];
const ICONS = {
  heart: pixIcon(HEART, { k: '#1a0000', r: '#e01010', w: '#ffb0b0' }),
  heartHalf: pixIcon(HEART.map((r) => r.slice(0, 5).replace(/[rw]/g, (c) => c) + r.slice(5).replace(/[rw]/g, 'e')), { k: '#1a0000', r: '#e01010', w: '#ffb0b0', e: '#3a1010' }),
  heartEmpty: pixIcon(HEART, { k: '#1a0000', r: '#3a1010', w: '#3a1010' }),
  food: pixIcon(['....kkk..', '...kbbbk.', '..kbbbbbk', '..kbbbbbk', '..kbbbbk.', '.kwkkkk..', 'kwk......', 'kk.......', '.........'], { k: '#2a1500', b: '#b8661e', w: '#e8e0d0' }),
  foodEmpty: pixIcon(['....kkk..', '...keeek.', '..keeeeek', '..keeeeek', '..keeeek.', '.kekkkk..', 'kek......', 'kk.......', '.........'], { k: '#2a1500', e: '#3a2a1a' }),
  bubble: pixIcon(['..kkkkk..', '.kbbbbbk.', 'kbwbbbbbk', 'kbwbbbbbk', 'kbbbbbbbk', 'kbbbbbbbk', '.kbbbbbk.', '..kkkkk..', '.........'], { k: '#0a2a6a', b: '#3070e0', w: '#d0e8ff' }),
};

export class UI {
  constructor(game) {
    this.game = game;
    this.cursor = null;
    this.open = null; // null | 'inventory' | 'crafting' | 'furnace' | 'chest' | 'creative'
    this.craft2 = new Array(4).fill(null);
    this.craft3 = new Array(9).fill(null);
    this.containerPos = null;
    this.creativeTab = 'blocks';
    this.creativeSearch = '';
    this.buildHotbar();
    this.buildStats();
    this.itemNameTimer = 0;
    document.addEventListener('mousemove', (e) => this.moveCursor(e.clientX, e.clientY));
    document.addEventListener('touchmove', (e) => { if (this.open && e.touches[0]) this.moveCursor(e.touches[0].clientX, e.touches[0].clientY); }, { passive: true });
    $('gui').addEventListener('contextmenu', (e) => e.preventDefault());
    $('gui').addEventListener('mousedown', (e) => {
      if (e.target === $('gui') && this.cursor) {
        // click outside window drops the held stack
        const n = e.button === 2 ? 1 : this.cursor.count;
        this.dropStack({ ...this.cursor, count: n });
        this.cursor.count -= n;
        if (this.cursor.count <= 0) this.cursor = null;
        this.renderCursor();
      } else if (e.target === $('gui') && this.game.input.isTouch) this.close();
    });
  }

  // ---------------- HUD ----------------
  buildHotbar() {
    const hb = $('hotbar');
    hb.innerHTML = '';
    for (let i = 0; i < 9; i++) {
      const s = document.createElement('div');
      s.className = 'slot';
      s.dataset.i = i;
      s.addEventListener('touchstart', (e) => { e.preventDefault(); this.game.player.selectSlot(i); }, { passive: false });
      s.addEventListener('mousedown', () => { if (this.game.input.isTouch) this.game.player.selectSlot(i); });
      hb.appendChild(s);
    }
    this.refreshHotbar();
  }
  slotHTML(s) {
    if (!s) return '';
    const def = ITEMS[s.id];
    let html = `<img src="${iconURL(s.id)}" draggable="false">`;
    if (s.count > 1) html += `<span class="count">${s.count}</span>`;
    if (def && def.durability && s.dmg) {
      const f = 1 - s.dmg / def.durability;
      html += `<span class="dura"><i style="width:${f * 100}%;background:hsl(${f * 120},90%,45%)"></i></span>`;
    }
    return html;
  }
  refreshHotbar() {
    const p = this.game.player;
    if (!p) return;
    const slots = $('hotbar').children;
    for (let i = 0; i < 9; i++) {
      slots[i].innerHTML = this.slotHTML(p.inventory[i]);
      slots[i].classList.toggle('sel', i === p.selected);
    }
    if (this.open) this.renderGUI();
  }
  buildStats() {
    const mk = (id, n) => {
      const el = $(id);
      el.innerHTML = '';
      for (let i = 0; i < n; i++) el.appendChild(document.createElement('img'));
    };
    mk('hearts', 10); mk('hunger', 10); mk('air', 10);
  }
  refreshStats() {
    const p = this.game.player;
    if (!p) return;
    const survival = p.mode !== 'creative';
    $('stats').style.display = survival ? '' : 'none';
    const h = $('hearts').children, f = $('hunger').children, a = $('air').children;
    const low = p.health <= 4;
    for (let i = 0; i < 10; i++) {
      const hv = p.health - i * 2;
      h[i].src = hv >= 2 ? ICONS.heart : hv === 1 ? ICONS.heartHalf : ICONS.heartEmpty;
      h[i].classList.toggle('shake', low);
      const fv = p.food - (9 - i) * 2;
      f[i].src = fv >= 1 ? ICONS.food : ICONS.foodEmpty;
      f[i].style.opacity = fv === 1 ? 0.6 : 1;
      a[i].src = ICONS.bubble;
      a[i].style.visibility = p.air / 30 > i ? 'visible' : 'hidden';
    }
    $('air').style.display = p.air < 300 ? '' : 'none';
  }
  flashDamage() {
    const el = $('damage');
    el.classList.remove('flash');
    void el.offsetWidth;
    el.classList.add('flash');
  }
  showItemName(s) {
    const el = $('itemName');
    if (!s) { el.style.opacity = 0; return; }
    el.textContent = ITEMS[s.id].name;
    el.style.opacity = 1;
    this.itemNameTimer = 2;
  }
  message(text, time = 4) {
    const el = document.createElement('div');
    el.className = 'msg';
    el.textContent = text;
    $('messages').appendChild(el);
    setTimeout(() => el.classList.add('fade'), time * 1000);
    setTimeout(() => el.remove(), time * 1000 + 1000);
  }
  update(dt) {
    if (this.itemNameTimer > 0) {
      this.itemNameTimer -= dt;
      if (this.itemNameTimer <= 0) $('itemName').style.opacity = 0;
    }
    const p = this.game.player;
    if (p && p.mode !== 'creative') {
      if (this.lastAir !== Math.ceil(p.air / 30)) { this.lastAir = Math.ceil(p.air / 30); this.refreshStats(); }
    }
    const g = this.game;
    // low-quality overlays (when post-processing is off)
    const post = g.renderer.postEnabled;
    $('waterOverlay').style.display = !post && p.body.headInWater ? 'block' : 'none';
    $('lavaOverlay').style.display = !post && p.body.inLava && p.cameraMode === 0 ? 'block' : 'none';
    $('portalOverlay').style.opacity = !post ? Math.min(0.7, p.portalTime / 3) : 0;
    if (this.debugOn) this.updateDebug();
  }
  toggleDebug() {
    this.debugOn = !this.debugOn;
    $('debug').style.display = this.debugOn ? 'block' : 'none';
  }
  updateDebug() {
    const g = this.game, p = g.player, b = p.body;
    const x = Math.floor(b.pos.x), y = Math.floor(b.pos.y), z = Math.floor(b.pos.z);
    const c = g.world.getChunk(x >> 4, z >> 4);
    const biome = c ? Object.values(BIOMES)[c.biome[(z & 15) * 16 + (x & 15)]].name : '?';
    const l = g.world.getLightRaw(x, y, z);
    const dirs = ['North (-Z)', 'West (-X)', 'South (+Z)', 'East (+X)'];
    const facing = dirs[((Math.round(p.yaw / (Math.PI / 2)) % 4) + 4) % 4];
    $('debug').textContent =
      `PowderCraft  ${g.fps} fps\n` +
      `XYZ: ${b.pos.x.toFixed(2)} / ${b.pos.y.toFixed(2)} / ${b.pos.z.toFixed(2)}\n` +
      `Chunk: ${x >> 4} ${z >> 4}  loaded ${g.world.chunks.size}  meshes ${g.meshCount}\n` +
      `Facing: ${facing}\nBiome: ${biome}  Dimension: ${g.world.dim}\n` +
      `Light: sky ${l >> 4} block ${l & 15}\n` +
      `Mobs: ${g.mobs.list.length}  Entities: ${g.entities.length}\n` +
      `Time: day ${Math.floor(g.dayTime)} ${(g.dayTime % 1 * 24).toFixed(1)}h\n` +
      `Seed: ${g.seed}  Mode: ${p.mode}`;
  }

  showDeath() {
    this.close();
    $('death').style.display = 'flex';
    this.game.input.enabled = false;
    document.exitPointerLock && document.exitPointerLock();
  }

  // ---------------- GUIs ----------------
  toggleInventory() {
    if (this.open) this.close();
    else this.openContainer(this.game.player.mode === 'creative' ? 'creative' : 'inventory');
  }

  openContainer(type, x, y, z) {
    const g = this.game;
    this.open = type;
    this.containerPos = x !== undefined ? { x, y, z } : null;
    if (type === 'furnace' || type === 'chest') {
      const key = g.world.dim + ':' + x + ',' + y + ',' + z;
      if (!g.world.tileEntities[key]) {
        g.world.tileEntities[key] = type === 'furnace' ? { type, input: null, fuel: null, output: null, burn: 0, burnMax: 0, cook: 0 } : { type, items: new Array(27).fill(null) };
      }
      this.tile = g.world.tileEntities[key];
    }
    g.input.enabled = false;
    document.exitPointerLock && document.exitPointerLock();
    $('gui').style.display = 'flex';
    this.renderGUI();
    g.audio.play('click');
  }

  close() {
    if (!this.open) return;
    const g = this.game;
    // return crafting grid items
    for (const grid of [this.craft2, this.craft3]) {
      for (let i = 0; i < grid.length; i++) {
        if (grid[i]) {
          const left = g.player.addItem(grid[i].id, grid[i].count, grid[i].dmg);
          if (left) this.dropStack({ id: grid[i].id, count: left });
          grid[i] = null;
        }
      }
    }
    if (this.cursor) {
      const left = g.player.addItem(this.cursor.id, this.cursor.count, this.cursor.dmg);
      if (left) this.dropStack({ id: this.cursor.id, count: left });
      this.cursor = null;
    }
    this.renderCursor();
    this.open = null;
    this.tile = null;
    $('gui').style.display = 'none';
    $('gui').innerHTML = '';
    if (!g.paused && !g.player.dead) {
      g.input.enabled = true;
      g.input.requestLock();
    }
    this.refreshHotbar();
  }

  dropStack(s) {
    const p = this.game.player;
    const d = p.lookDir(), e = p.eyePos();
    this.game.dropItem(e.x + d.x * 0.5, e.y - 0.3, e.z + d.z * 0.5, s.id, s.count, { x: d.x * 5, y: 2, z: d.z * 5 }, s.dmg);
  }

  dropContainerContents() { /* handled through tileRemoved world events */ }

  moveCursor(x, y) {
    this.cx = x; this.cy = y;
    const c = $('cursorItem');
    c.style.left = x - 20 + 'px';
    c.style.top = y - 20 + 'px';
  }
  renderCursor() {
    const c = $('cursorItem');
    c.innerHTML = this.cursor ? this.slotHTML(this.cursor) : '';
  }

  // Build a slot element bound to get/set accessors
  slotEl(get, set, opts = {}) {
    const el = document.createElement('div');
    el.className = 'slot' + (opts.big ? ' big' : '');
    el.innerHTML = this.slotHTML(get());
    const s = get();
    if (s) el.title = ITEMS[s.id].name;
    let pressTimer = null, longPressed = false;
    el.addEventListener('mousedown', (e) => {
      if (this.game.input.isTouch) return;
      e.preventDefault();
      this.clickSlot(get, set, e.button === 2 ? 'right' : 'left', e.shiftKey, opts);
    });
    el.addEventListener('touchstart', (e) => {
      e.preventDefault();
      const t = e.touches[0];
      this.moveCursor(t.clientX, t.clientY);
      longPressed = false;
      pressTimer = setTimeout(() => { longPressed = true; this.clickSlot(get, set, 'right', false, opts); }, 400);
    }, { passive: false });
    el.addEventListener('touchend', (e) => {
      e.preventDefault();
      clearTimeout(pressTimer);
      if (!longPressed) this.clickSlot(get, set, 'left', false, opts);
    }, { passive: false });
    el.addEventListener('mouseenter', () => {
      const s2 = get();
      $('tooltip').textContent = s2 ? ITEMS[s2.id].name : '';
      $('tooltip').style.display = s2 ? 'block' : 'none';
    });
    el.addEventListener('mousemove', (e) => { $('tooltip').style.left = e.clientX + 14 + 'px'; $('tooltip').style.top = e.clientY - 28 + 'px'; });
    el.addEventListener('mouseleave', () => { $('tooltip').style.display = 'none'; });
    return el;
  }

  clickSlot(get, set, button, shift, opts) {
    const g = this.game;
    const cur = this.cursor;
    const s = get();
    g.audio.play('click');
    if (opts.creative) {
      if (cur) { this.cursor = null; }
      else if (s) this.cursor = { id: s.id, count: shift || button === 'left' ? maxStack(s.id) : 1 };
      this.afterChange();
      return;
    }
    if (opts.output) {
      if (!s) return;
      if (shift) {
        // craft as many as possible into inventory
        let guard = 64;
        while (get() && guard-- > 0) {
          const out = get();
          if (g.player.addItem(out.id, out.count) > 0) break;
          opts.onTake();
        }
      } else if (!cur || (cur.id === s.id && cur.count + s.count <= maxStack(s.id))) {
        if (cur) cur.count += s.count; else this.cursor = { ...s };
        opts.onTake();
      }
      this.afterChange();
      return;
    }
    if (opts.trash) {
      this.cursor = null;
      this.afterChange();
      return;
    }
    if (shift && s) {
      if (opts.quickMove) opts.quickMove(get, set);
      this.afterChange();
      return;
    }
    if (opts.accept && cur && !opts.accept(cur.id)) return;
    if (button === 'left') {
      if (!cur) { this.cursor = s; set(null); }
      else if (!s) { set(cur); this.cursor = null; }
      else if (s.id === cur.id && !ITEMS[s.id].durability) {
        const n = Math.min(cur.count, maxStack(s.id) - s.count);
        s.count += n; cur.count -= n;
        if (cur.count <= 0) this.cursor = null;
        set(s);
      } else { set(cur); this.cursor = s; }
    } else {
      if (!cur && s) {
        const half = Math.ceil(s.count / 2);
        this.cursor = { id: s.id, count: half, dmg: s.dmg };
        s.count -= half;
        set(s.count > 0 ? s : null);
      } else if (cur && (!s || (s.id === cur.id && s.count < maxStack(s.id)))) {
        if (!s) set({ id: cur.id, count: 1, dmg: cur.dmg });
        else { s.count++; set(s); }
        cur.count--;
        if (cur.count <= 0) this.cursor = null;
      }
    }
    this.afterChange();
  }

  afterChange() {
    this.renderCursor();
    this.renderGUI();
    const p = this.game.player;
    const slots = $('hotbar').children;
    for (let i = 0; i < 9; i++) { slots[i].innerHTML = this.slotHTML(p.inventory[i]); slots[i].classList.toggle('sel', i === p.selected); }
  }

  invSlot(i, extra = {}) {
    const p = this.game.player;
    return this.slotEl(() => p.inventory[i], (v) => { p.inventory[i] = v; }, {
      quickMove: (get, set) => {
        const s = get();
        // move to container if open, else between hotbar/main
        if (this.open === 'chest' && this.tile) {
          set(this.mergeInto(s, this.tile.items, 0, 27));
        } else if (this.open === 'furnace' && this.tile) {
          if (SMELTING[s.id] !== undefined && (!this.tile.input || this.tile.input.id === s.id)) set(this.mergeInto(s, this.tile, 'input'));
          else if (ITEMS[s.id].fuel) set(this.mergeInto(s, this.tile, 'fuel'));
        } else {
          const toHotbar = i >= 9;
          set(this.mergeInto(s, p.inventory, toHotbar ? 0 : 9, toHotbar ? 9 : 36));
        }
      },
      ...extra,
    });
  }

  // merge stack s into array range or a named furnace slot; returns leftover or null
  mergeInto(s, target, a, b) {
    if (typeof a === 'string') {
      const cur = target[a];
      if (!cur) { target[a] = s; return null; }
      if (cur.id !== s.id) return s;
      const n = Math.min(s.count, maxStack(s.id) - cur.count);
      cur.count += n; s.count -= n;
      return s.count > 0 ? s : null;
    }
    for (let i = a; i < b && s.count > 0; i++) {
      const cur = target[i];
      if (cur && cur.id === s.id && !ITEMS[s.id].durability && cur.count < maxStack(s.id)) {
        const n = Math.min(s.count, maxStack(s.id) - cur.count);
        cur.count += n; s.count -= n;
      }
    }
    for (let i = a; i < b && s.count > 0; i++) {
      if (!target[i]) { target[i] = { ...s }; s.count = 0; }
    }
    return s.count > 0 ? s : null;
  }

  playerInvBlock(win) {
    const lbl = document.createElement('div');
    lbl.className = 'label';
    lbl.textContent = 'Inventory';
    win.appendChild(lbl);
    const main = document.createElement('div');
    main.className = 'grid9';
    for (let i = 9; i < 36; i++) main.appendChild(this.invSlot(i));
    win.appendChild(main);
    const hb = document.createElement('div');
    hb.className = 'grid9 hotrow';
    for (let i = 0; i < 9; i++) hb.appendChild(this.invSlot(i));
    win.appendChild(hb);
  }

  craftingBlock(grid, size) {
    const wrap = document.createElement('div');
    wrap.className = 'craft';
    const g = document.createElement('div');
    g.className = 'gridN';
    g.style.gridTemplateColumns = `repeat(${size}, var(--slot))`;
    for (let i = 0; i < size * size; i++) {
      g.appendChild(this.slotEl(() => grid[i], (v) => { grid[i] = v; }, {
        quickMove: (get, set) => set(this.game.player.addItem(get().id, get().count, get().dmg) ? get() : null),
      }));
    }
    wrap.appendChild(g);
    const arrow = document.createElement('div');
    arrow.className = 'arrow';
    arrow.textContent = '➜';
    wrap.appendChild(arrow);
    const recipe = matchRecipe(grid, size, size);
    const out = recipe ? { id: recipe.out, count: recipe.count } : null;
    wrap.appendChild(this.slotEl(() => {
      const r = matchRecipe(grid, size, size);
      return r ? { id: r.out, count: r.count } : null;
    }, () => {}, {
      output: true, big: true,
      onTake: () => {
        for (let i = 0; i < grid.length; i++) {
          if (grid[i]) {
            // buckets leave an empty bucket behind
            const leftover = grid[i].id === I.water_bucket || grid[i].id === I.lava_bucket ? { id: I.bucket, count: 1 } : null;
            grid[i].count--;
            if (grid[i].count <= 0) grid[i] = leftover;
          }
        }
      },
    }));
    void out;
    return wrap;
  }

  renderGUI() {
    if (!this.open) return;
    const gui = $('gui');
    gui.innerHTML = '';
    const win = document.createElement('div');
    win.className = 'window';
    win.addEventListener('mousedown', (e) => e.stopPropagation());
    gui.appendChild(win);
    const title = (t) => { const d = document.createElement('div'); d.className = 'label'; d.textContent = t; win.appendChild(d); };
    const closeBtn = document.createElement('button');
    closeBtn.className = 'closeBtn';
    closeBtn.textContent = '✕';
    closeBtn.onclick = () => this.close();
    closeBtn.addEventListener('touchend', (e) => { e.preventDefault(); this.close(); });
    win.appendChild(closeBtn);

    if (this.open === 'inventory') {
      title('Crafting');
      const top = document.createElement('div');
      top.className = 'invTop';
      const preview = document.createElement('div');
      preview.className = 'preview';
      preview.innerHTML = `<div class="steveIcon"></div><div class="small">${this.game.player.mode === 'creative' ? 'Creative' : 'Survival'}</div>`;
      top.appendChild(preview);
      top.appendChild(this.craftingBlock(this.craft2, 2));
      win.appendChild(top);
      this.playerInvBlock(win);
    } else if (this.open === 'crafting') {
      title('Crafting Table');
      win.appendChild(this.craftingBlock(this.craft3, 3));
      this.playerInvBlock(win);
    } else if (this.open === 'furnace') {
      title('Furnace');
      const t = this.tile;
      const f = document.createElement('div');
      f.className = 'furnace';
      const col = document.createElement('div');
      col.className = 'fcol';
      col.appendChild(this.slotEl(() => t.input, (v) => { t.input = v; }, { quickMove: (get, set) => set(this.game.player.addItem(get().id, get().count) ? get() : null) }));
      const flame = document.createElement('div');
      flame.className = 'flame';
      flame.innerHTML = `<i style="height:${t.burnMax ? (t.burn / t.burnMax) * 100 : 0}%"></i>`;
      col.appendChild(flame);
      col.appendChild(this.slotEl(() => t.fuel, (v) => { t.fuel = v; }, {
        accept: (id) => !!ITEMS[id].fuel,
        quickMove: (get, set) => set(this.game.player.addItem(get().id, get().count) ? get() : null),
      }));
      f.appendChild(col);
      const prog = document.createElement('div');
      prog.className = 'progress';
      prog.innerHTML = `<i style="width:${(t.cook / 10) * 100}%"></i>`;
      f.appendChild(prog);
      f.appendChild(this.slotEl(() => t.output, (v) => { t.output = v; }, {
        big: true,
        accept: () => false,
        quickMove: (get, set) => set(this.game.player.addItem(get().id, get().count) ? get() : null),
      }));
      win.appendChild(f);
      this.playerInvBlock(win);
      this.furnaceEls = { flame, prog };
    } else if (this.open === 'chest') {
      title('Chest');
      const grid = document.createElement('div');
      grid.className = 'grid9';
      const items = this.tile.items;
      for (let i = 0; i < 27; i++) {
        grid.appendChild(this.slotEl(() => items[i], (v) => { items[i] = v; }, {
          quickMove: (get, set) => set(this.mergeInto(get(), this.game.player.inventory, 0, 36)),
        }));
      }
      win.appendChild(grid);
      this.playerInvBlock(win);
    } else if (this.open === 'creative') {
      this.renderCreative(win);
    }
  }

  renderCreative(win) {
    const tabs = document.createElement('div');
    tabs.className = 'tabs';
    for (const [k, n] of [['blocks', 'Blocks'], ['items', 'Items & Tools'], ['search', 'Search'], ['survival', 'Inventory']]) {
      const b = document.createElement('button');
      b.textContent = n;
      b.className = this.creativeTab === k ? 'active' : '';
      b.onclick = () => { this.creativeTab = k; this.renderGUI(); };
      b.addEventListener('touchend', (e) => { e.preventDefault(); this.creativeTab = k; this.renderGUI(); });
      tabs.appendChild(b);
    }
    win.appendChild(tabs);
    if (this.creativeTab === 'survival') {
      const top = document.createElement('div');
      top.className = 'invTop';
      top.appendChild(this.craftingBlock(this.craft2, 2));
      win.appendChild(top);
      this.playerInvBlock(win);
      return;
    }
    const { blocks, items } = creativeList();
    let list = this.creativeTab === 'blocks' ? blocks : this.creativeTab === 'items' ? items : [...blocks, ...items];
    if (this.creativeTab === 'search') {
      const inp = document.createElement('input');
      inp.className = 'search';
      inp.placeholder = 'Search items...';
      inp.value = this.creativeSearch;
      inp.oninput = () => { this.creativeSearch = inp.value; this.renderGUI(); const n = document.querySelector('.search'); n.focus(); n.setSelectionRange(n.value.length, n.value.length); };
      win.appendChild(inp);
      const q = this.creativeSearch.toLowerCase();
      list = list.filter((id) => ITEMS[id].name.toLowerCase().includes(q));
    }
    const grid = document.createElement('div');
    grid.className = 'grid9 scroll';
    for (const id of list) grid.appendChild(this.slotEl(() => ({ id, count: 1 }), () => {}, { creative: true }));
    win.appendChild(grid);
    const row = document.createElement('div');
    row.className = 'hotwrap';
    const hb = document.createElement('div');
    hb.className = 'grid9 hotrow';
    for (let i = 0; i < 9; i++) hb.appendChild(this.invSlot(i));
    row.appendChild(hb);
    const trash = this.slotEl(() => null, () => {}, { trash: true });
    trash.classList.add('trash');
    trash.innerHTML = '🗑';
    row.appendChild(trash);
    win.appendChild(row);
    if (this.creativeScroll !== undefined) grid.scrollTop = this.creativeScroll;
    grid.addEventListener('scroll', () => { this.creativeScroll = grid.scrollTop; });
  }

  // called from game tick to keep furnace GUI progress live
  tickGUI() {
    if (this.open === 'furnace' && this.furnaceEls && this.tile) {
      const t = this.tile;
      this.furnaceEls.flame.firstChild.style.height = (t.burnMax ? (t.burn / t.burnMax) * 100 : 0) + '%';
      this.furnaceEls.prog.firstChild.style.width = (t.cook / 10) * 100 + '%';
      if (t.changed) { t.changed = false; this.renderGUI(); }
    }
  }
}

export { ICONS, BLOCKS, B };
