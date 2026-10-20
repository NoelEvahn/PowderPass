// Procedural pixel-art textures (16x16), block texture array, item icons,
// and optional user-supplied resource pack loading.
import * as THREE from 'three';
import { mulberry32, hashString } from './noise.js';
import { BLOCKS, ITEMS, BLOCK_COUNT, ITEM_COUNT } from './blocks.js';

const S = 16;

// ---------- pixel helpers ----------
class Tex {
  constructor(name) {
    this.d = new Uint8ClampedArray(S * S * 4);
    this.r = mulberry32(hashString(name) + 1);
  }
  set(x, y, c, a = 255) {
    if (x < 0 || y < 0 || x >= S || y >= S) return;
    const i = (y * S + x) * 4;
    this.d[i] = c[0]; this.d[i + 1] = c[1]; this.d[i + 2] = c[2]; this.d[i + 3] = c[3] ?? a;
  }
  get(x, y) {
    const i = (((y + S) % S) * S + ((x + S) % S)) * 4;
    return [this.d[i], this.d[i + 1], this.d[i + 2], this.d[i + 3]];
  }
  rand() { return this.r(); }
  noise(base, v = 0.15, a = 255) {
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) this.set(x, y, vary(base, v, this.r()), a);
    return this;
  }
  speck(col, n, v = 0.08) {
    for (let i = 0; i < n; i++) this.set((this.r() * S) | 0, (this.r() * S) | 0, vary(col, v, this.r()));
    return this;
  }
  blob(col, cx, cy, size, v = 0.1) {
    for (let i = 0; i < size; i++) {
      const x = cx + Math.round((this.r() - 0.5) * 2.4), y = cy + Math.round((this.r() - 0.5) * 2.4);
      this.set(x, y, vary(col, v, this.r()));
    }
  }
  rect(x0, y0, w, h, col, v = 0) {
    for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) this.set(x, y, v ? vary(col, v, this.r()) : col);
  }
  shade(x, y, f) {
    const c = this.get(x, y);
    this.set(x, y, [c[0] * f, c[1] * f, c[2] * f, c[3]]);
  }
  clear() { this.d.fill(0); return this; }
}

function vary(c, v, r) {
  const f = 1 + (r - 0.5) * 2 * v;
  return [c[0] * f, c[1] * f, c[2] * f, c[3] ?? 255];
}
const mix = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];

function voronoiCells(t, n, edge, fillFn) {
  const pts = [];
  for (let i = 0; i < n; i++) pts.push([t.rand() * S, t.rand() * S, t.rand()]);
  for (let y = 0; y < S; y++)
    for (let x = 0; x < S; x++) {
      let d1 = 1e9, d2 = 1e9, best = 0;
      for (let i = 0; i < n; i++) {
        for (let oy = -1; oy <= 1; oy++)
          for (let ox = -1; ox <= 1; ox++) {
            const dx = x + 0.5 - (pts[i][0] + ox * S), dy = y + 0.5 - (pts[i][1] + oy * S);
            const d = Math.sqrt(dx * dx + dy * dy);
            if (d < d1) { d2 = d1; d1 = d; best = i; } else if (d < d2) d2 = d;
          }
      }
      fillFn(x, y, pts[best][2], d2 - d1 < edge, d1);
    }
}

// ---------- block texture generators ----------
const STONE = [125, 125, 125];
const DIRT = [134, 96, 67];
const GEN = {};

GEN.stone = (t) => {
  t.noise(STONE, 0.08);
  for (let i = 0; i < 14; i++) {
    const x = (t.rand() * S) | 0, y = (t.rand() * S) | 0, len = 1 + ((t.rand() * 3) | 0);
    for (let k = 0; k < len; k++) t.set(x + k, y, vary([100, 100, 100], 0.05, t.rand()));
  }
  t.speck([145, 145, 145], 14, 0.04);
};
GEN.bedrock = (t) => {
  t.noise([85, 85, 85], 0.3);
  for (let i = 0; i < 40; i++) t.set((t.rand() * S) | 0, (t.rand() * S) | 0, vary([30, 30, 30], 0.3, t.rand()));
  t.speck([150, 150, 150], 20, 0.1);
};
GEN.dirt = (t) => { t.noise(DIRT, 0.12); t.speck([105, 75, 52], 20); t.speck([150, 110, 80], 10); };
GEN.grass_top = (t) => { t.noise([205, 205, 205], 0.12); t.speck([170, 170, 170], 30); t.speck([235, 235, 235], 12); };
const GRASS_GREEN = [112, 150, 68];
GEN.grass_side = (t) => {
  GEN.dirt(t);
  for (let x = 0; x < S; x++) {
    const h = 3 + ((t.rand() * 2.2) | 0) + (t.rand() < 0.25 ? 1 : 0);
    for (let y = 0; y < h; y++) t.set(x, y, vary(GRASS_GREEN, 0.14, t.rand()));
  }
};
GEN.grass_side_snowy = (t) => {
  GEN.dirt(t);
  for (let x = 0; x < S; x++) {
    const h = 3 + ((t.rand() * 2) | 0) + (t.rand() < 0.3 ? 1 : 0);
    for (let y = 0; y < h; y++) t.set(x, y, vary([240, 245, 250], 0.04, t.rand()));
  }
};
GEN.cobblestone = (t) => {
  voronoiCells(t, 9, 0.9, (x, y, r, e) => {
    const base = mix([95, 95, 95], [150, 150, 150], r);
    t.set(x, y, e ? vary([60, 60, 60], 0.1, t.rand()) : vary(base, 0.07, t.rand()));
  });
};
GEN.mossy_cobblestone = (t) => {
  GEN.cobblestone(t);
  for (let i = 0; i < 6; i++) t.blob([70, 110, 45], (t.rand() * S) | 0, (t.rand() * S) | 0, 10, 0.15);
};
function planks(t, base) {
  t.noise(base, 0.06);
  for (let row = 0; row < 4; row++) {
    const y0 = row * 4;
    for (let x = 0; x < S; x++) t.set(x, y0 + 3, vary(base, 0.05, t.rand()).map((v, i) => (i < 3 ? v * 0.68 : v)));
    const seam = ((t.rand() * 12) | 0) + 2 + (row % 2) * 4;
    for (let y = y0; y < y0 + 3; y++) t.set(seam % S, y, base.map((v) => v * 0.72));
    for (let k = 0; k < 3; k++) {
      const gx = (t.rand() * S) | 0, gy = y0 + ((t.rand() * 3) | 0), gl = 2 + ((t.rand() * 4) | 0);
      for (let i = 0; i < gl; i++) t.set((gx + i) % S, gy, base.map((v) => v * 0.86));
    }
  }
}
GEN.oak_planks = (t) => planks(t, [162, 130, 78]);
GEN.birch_planks = (t) => planks(t, [196, 179, 123]);
GEN.spruce_planks = (t) => planks(t, [115, 85, 49]);
GEN.jungle_planks = (t) => planks(t, [160, 115, 80]);
function logSide(t, base, dark, birch = false) {
  t.noise(base, 0.08);
  for (let x = 0; x < S; x++) {
    if (t.rand() < 0.45) {
      const y0 = (t.rand() * S) | 0, len = 3 + ((t.rand() * 9) | 0);
      for (let k = 0; k < len; k++) t.set(x, (y0 + k) % S, vary(dark, 0.08, t.rand()));
    }
  }
  if (birch) {
    for (let i = 0; i < 6; i++) {
      const x = (t.rand() * 14) | 0, y = (t.rand() * S) | 0, w = 2 + ((t.rand() * 3) | 0);
      for (let k = 0; k < w; k++) t.set(x + k, y, [40, 40, 35]);
      if (t.rand() < 0.5) t.set(x + 1, y + 1, [55, 55, 50]);
    }
  }
}
function logTop(t, bark, inner, ring) {
  for (let y = 0; y < S; y++)
    for (let x = 0; x < S; x++) {
      const dx = x - 7.5, dy = y - 7.5;
      const d = Math.max(Math.abs(dx), Math.abs(dy));
      if (d > 6.6) t.set(x, y, vary(bark, 0.1, t.rand()));
      else t.set(x, y, vary(Math.round(d) % 2 === 0 ? ring : inner, 0.05, t.rand()));
    }
}
GEN.oak_log = (t) => logSide(t, [109, 85, 50], [76, 59, 34]);
GEN.oak_log_top = (t) => logTop(t, [109, 85, 50], [176, 143, 88], [150, 118, 70]);
GEN.birch_log = (t) => logSide(t, [216, 215, 210], [190, 190, 182], true);
GEN.birch_log_top = (t) => logTop(t, [216, 215, 210], [200, 180, 125], [180, 160, 108]);
GEN.spruce_log = (t) => logSide(t, [58, 37, 16], [42, 26, 10]);
GEN.spruce_log_top = (t) => logTop(t, [58, 37, 16], [125, 92, 55], [100, 72, 42]);
GEN.jungle_log = (t) => logSide(t, [85, 67, 25], [62, 48, 18]);
GEN.jungle_log_top = (t) => logTop(t, [85, 67, 25], [170, 120, 82], [145, 100, 65]);
GEN.acacia_log = (t) => logSide(t, [103, 96, 86], [80, 74, 66]);
GEN.acacia_log_top = (t) => logTop(t, [103, 96, 86], [175, 92, 50], [150, 78, 42]);
function leavesTex(t, base) {
  for (let y = 0; y < S; y++)
    for (let x = 0; x < S; x++) {
      const r = t.rand();
      if (r < 0.16) t.set(x, y, [0, 0, 0, 0]);
      else t.set(x, y, vary(base, 0.22, t.rand()));
    }
  for (let i = 0; i < 18; i++) t.set((t.rand() * S) | 0, (t.rand() * S) | 0, base.map((v) => v * 0.6));
}
GEN.oak_leaves = (t) => leavesTex(t, [190, 190, 190]);
GEN.birch_leaves = (t) => leavesTex(t, [195, 195, 195]);
GEN.spruce_leaves = (t) => leavesTex(t, [170, 170, 170]);
GEN.jungle_leaves = (t) => leavesTex(t, [190, 190, 190]);
GEN.acacia_leaves = (t) => leavesTex(t, [185, 185, 185]);
GEN.glass = (t) => {
  t.clear();
  for (let i = 0; i < S; i++) {
    t.set(i, 0, [220, 235, 240]); t.set(i, 15, [190, 210, 220]);
    t.set(0, i, [220, 235, 240]); t.set(15, i, [190, 210, 220]);
  }
  for (let i = 0; i < 4; i++) { t.set(3 + i, 6 - i, [235, 245, 250, 200]); t.set(9 + i, 12 - i, [235, 245, 250, 170]); }
  t.set(4, 3, [235, 245, 250, 180]);
};
function ore(col, base = GEN.stone, n = 5) {
  return (t) => {
    base(t);
    for (let i = 0; i < n; i++) {
      const cx = 2 + ((t.rand() * 12) | 0), cy = 2 + ((t.rand() * 12) | 0);
      t.set(cx, cy, col); t.set(cx + 1, cy, vary(col, 0.12, t.rand()));
      t.set(cx, cy + 1, vary(col, 0.18, t.rand()).map((v, i) => (i < 3 ? v * 0.75 : v)));
      if (t.rand() < 0.6) t.set(cx + 1, cy + 1, vary(col, 0.1, t.rand()));
    }
  };
}
GEN.coal_ore = ore([35, 35, 35]);
GEN.iron_ore = ore([216, 175, 147]);
GEN.gold_ore = ore([252, 238, 75]);
GEN.diamond_ore = ore([93, 236, 245]);
GEN.emerald_ore = ore([23, 221, 98], GEN.stone, 4);
GEN.nether_quartz_ore = (t) => ore([235, 228, 220], GEN.netherrack, 6)(t);
GEN.sand = (t) => { t.noise([219, 207, 163], 0.05); t.speck([200, 186, 140], 25); t.speck([235, 225, 185], 12); };
GEN.red_sand = (t) => { t.noise([190, 102, 33], 0.06); t.speck([165, 85, 25], 25); };
GEN.gravel = (t) => {
  voronoiCells(t, 14, 0.6, (x, y, r, e) => {
    const cols = [[136, 126, 126], [110, 102, 100], [160, 150, 148], [120, 110, 95]];
    t.set(x, y, e ? [80, 75, 72] : vary(cols[(r * 4) | 0], 0.06, t.rand()));
  });
};
GEN.sandstone = (t) => {
  t.noise([216, 203, 155], 0.04);
  for (let x = 0; x < S; x++) { t.set(x, 3, [196, 182, 135]); t.set(x, 10, [200, 186, 140]); t.set(x, 15, [180, 166, 120]); }
  t.speck([228, 216, 170], 14);
};
GEN.sandstone_top = (t) => { t.noise([220, 208, 160], 0.035); t.speck([205, 192, 145], 10); };
GEN.snow = (t) => { t.noise([243, 248, 252], 0.025); t.speck([225, 232, 245], 10); };
GEN.ice = (t) => {
  t.noise([140, 180, 252], 0.05, 185);
  for (let i = 0; i < 5; i++) { const x = (t.rand() * 12) | 0, y = (t.rand() * 12) | 0; for (let k = 0; k < 4; k++) t.set(x + k, y + k, [200, 225, 255], 200); }
};
GEN.packed_ice = (t) => { t.noise([141, 180, 250], 0.06); t.speck([175, 205, 255], 18); };
GEN.cactus_side = (t) => {
  t.clear();
  for (let y = 0; y < S; y++)
    for (let x = 1; x < 15; x++) {
      const stripe = x % 4 === 1;
      t.set(x, y, vary(stripe ? [40, 110, 30] : [72, 145, 50], 0.08, t.rand()));
    }
  for (let i = 0; i < 10; i++) { const y = (t.rand() * S) | 0; t.set(t.rand() < 0.5 ? 0 : 15, y, [20, 20, 20]); t.set((t.rand() * 14) | 0, y, [210, 210, 170]); }
};
GEN.cactus_top = (t) => {
  t.clear();
  for (let y = 1; y < 15; y++) for (let x = 1; x < 15; x++) {
    const d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
    t.set(x, y, vary(d > 5.5 ? [55, 125, 40] : d < 2 ? [100, 160, 70] : [80, 145, 55], 0.06, t.rand()));
  }
};
GEN.clay = (t) => { t.noise([160, 166, 179], 0.04); t.speck([145, 150, 165], 16); };
function crossTex(t, draw) { t.clear(); draw(t); }
GEN.tall_grass = (t) => crossTex(t, () => {
  for (let i = 0; i < 9; i++) {
    let x = 1 + ((t.rand() * 14) | 0);
    const h = 6 + ((t.rand() * 9) | 0);
    for (let y = 15; y > 15 - h; y--) {
      t.set(x, y, vary([200, 200, 200], 0.15, t.rand()));
      if (t.rand() < 0.2) x += t.rand() < 0.5 ? -1 : 1;
    }
  }
});
function flower(t, petal, center) {
  t.clear();
  for (let y = 8; y < 16; y++) t.set(7, y, [60, 130, 40]);
  t.set(8, 12, [70, 140, 45]); t.set(9, 11, [70, 140, 45]); t.set(6, 13, [70, 140, 45]); t.set(5, 12, [70, 140, 45]);
  for (const [dx, dy] of [[0, -1], [1, 0], [-1, 0], [0, 1], [1, -1], [-1, -1], [1, 1], [-1, 1], [0, -2], [2, 0], [-2, 0]]) t.set(7 + dx, 5 + dy, vary(petal, 0.1, t.rand()));
  t.set(7, 5, center);
}
GEN.dandelion = (t) => flower(t, [245, 220, 40], [230, 160, 20]);
GEN.poppy = (t) => flower(t, [215, 30, 30], [40, 20, 20]);
GEN.dead_bush = (t) => crossTex(t, () => {
  const c = [120, 80, 40];
  for (let y = 9; y < 16; y++) t.set(7, y, c);
  for (let k = 0; k < 5; k++) { t.set(7 - k, 9 - k, c); t.set(8 + k, 10 - k, c); t.set(6 - (k >> 1), 12 - k, c); }
  t.set(10, 5, c); t.set(3, 6, c); t.set(12, 7, c);
});
function mushroom(t, cap, spots) {
  t.clear();
  t.rect(7, 9, 2, 6, [220, 210, 190]);
  t.rect(4, 6, 8, 3, cap, 0.06); t.rect(5, 5, 6, 1, cap, 0.06);
  if (spots) { t.set(6, 6, [240, 240, 240]); t.set(9, 7, [240, 240, 240]); t.set(8, 5, [240, 240, 240]); }
}
GEN.red_mushroom = (t) => mushroom(t, [200, 30, 30], true);
GEN.brown_mushroom = (t) => mushroom(t, [150, 110, 80], false);
GEN.sugar_cane = (t) => crossTex(t, () => {
  for (const x of [3, 8, 12]) for (let y = 0; y < S; y++) t.set(x, y, vary(y % 5 === 0 ? [140, 150, 140] : [180, 190, 180], 0.06, t.rand()));
  t.set(4, 4, [170, 180, 170]); t.set(9, 9, [170, 180, 170]); t.set(11, 2, [170, 180, 170]);
});
GEN.lily_pad = (t) => {
  t.clear();
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const dx = x - 7.5, dy = y - 7.5;
    if (dx * dx + dy * dy < 50 && !(dx > 0 && Math.abs(dy) < dx * 0.35)) t.set(x, y, vary([150, 150, 150], 0.12, t.rand()));
  }
};
GEN.crafting_table_top = (t) => {
  planks(t, [162, 130, 78]);
  for (let i = 0; i < S; i++) { t.set(i, 0, [90, 60, 30]); t.set(i, 15, [90, 60, 30]); t.set(0, i, [90, 60, 30]); t.set(15, i, [90, 60, 30]); }
  for (let i = 2; i < 14; i++) { t.set(i, 5, [110, 80, 45]); t.set(i, 10, [110, 80, 45]); t.set(5, i, [110, 80, 45]); t.set(10, i, [110, 80, 45]); }
};
GEN.crafting_table_side = (t) => {
  planks(t, [162, 130, 78]);
  t.rect(0, 0, 16, 3, [120, 90, 50], 0.05);
  t.rect(3, 6, 1, 8, [100, 70, 40]); t.rect(2, 5, 3, 2, [150, 150, 150]); // hammer
  t.rect(11, 5, 1, 9, [100, 70, 40]); t.rect(10, 4, 3, 1, [170, 170, 170]); t.rect(12, 5, 1, 2, [170, 170, 170]);
};
GEN.crafting_table_front = (t) => {
  GEN.crafting_table_side(t);
  for (let i = 0; i < 6; i++) t.set(6 + i, 7 + (i >> 1), [180, 180, 180]);
  t.rect(6, 6, 2, 3, [100, 70, 40]);
};
function furnaceBase(t) {
  t.noise([120, 120, 120], 0.07);
  for (let i = 0; i < S; i++) { t.set(i, 0, [90, 90, 90]); t.set(i, 15, [85, 85, 85]); t.set(0, i, [95, 95, 95]); t.set(15, i, [85, 85, 85]); }
  t.speck([95, 95, 95], 20);
}
GEN.furnace_side = (t) => furnaceBase(t);
GEN.furnace_top = (t) => { furnaceBase(t); t.rect(2, 2, 12, 12, [130, 130, 130], 0.05); };
GEN.furnace_front = (t) => {
  furnaceBase(t);
  t.rect(3, 2, 10, 4, [100, 100, 100], 0.05);
  t.rect(4, 9, 8, 5, [25, 25, 25]);
  t.rect(3, 8, 10, 1, [80, 80, 80]);
};
GEN.furnace_front_on = (t) => {
  GEN.furnace_front(t);
  for (let x = 4; x < 12; x++) for (let y = 10; y < 14; y++) if (t.rand() < 0.8) t.set(x, y, mix([255, 230, 90], [230, 90, 20], (y - 10) / 4 + t.rand() * 0.3));
};
GEN.torch = (t) => {
  t.clear();
  for (let y = 6; y < 16; y++) { t.set(7, y, [120, 90, 50]); t.set(8, y, [95, 70, 38]); }
  t.set(7, 5, [255, 240, 140]); t.set(8, 5, [255, 210, 80]); t.set(7, 6, [255, 200, 60]); t.set(8, 6, [240, 140, 30]);
  t.set(7, 4, [255, 250, 200]); t.set(8, 4, [255, 230, 120]);
};
GEN.obsidian = (t) => {
  t.noise([20, 16, 32], 0.25);
  for (let i = 0; i < 10; i++) t.blob([60, 38, 92], (t.rand() * S) | 0, (t.rand() * S) | 0, 3, 0.15);
};
GEN.netherrack = (t) => {
  t.noise([111, 54, 52], 0.12);
  t.speck([140, 70, 66], 30); t.speck([80, 36, 35], 30);
};
GEN.soul_sand = (t) => {
  t.noise([81, 62, 50], 0.1);
  for (let i = 0; i < 3; i++) {
    const x = 2 + ((t.rand() * 10) | 0), y = 2 + ((t.rand() * 10) | 0);
    t.set(x, y, [45, 32, 25]); t.set(x + 2, y, [45, 32, 25]); t.rect(x, y + 2, 3, 1, [45, 32, 25]);
  }
};
GEN.glowstone = (t) => {
  voronoiCells(t, 8, 0.7, (x, y, r, e, d) => {
    t.set(x, y, e ? vary([140, 100, 50], 0.1, t.rand()) : vary(mix([255, 240, 170], [210, 160, 80], Math.min(1, d / 5)), 0.08, t.rand()));
  });
};
GEN.nether_portal = (t) => {
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const v = Math.sin(x * 0.9 + Math.sin(y * 0.7) * 2) * 0.5 + 0.5;
    t.set(x, y, mix([90, 20, 170], [180, 90, 255], v * 0.7 + t.rand() * 0.3), 200);
  }
};
GEN.bricks = (t) => {
  t.noise([150, 150, 145], 0.05);
  for (let row = 0; row < 4; row++) {
    const off = row % 2 ? 4 : 0;
    for (let b = -1; b < 2; b++) {
      const col = vary([150, 74, 58], 0.12, t.rand());
      for (let y = row * 4; y < row * 4 + 3; y++) for (let x = off + b * 8; x < off + b * 8 + 7; x++) if (x >= 0 && x < S) t.set(x, y, vary(col, 0.05, t.rand()));
    }
  }
};
GEN.nether_bricks = (t) => {
  t.noise([30, 14, 18], 0.1);
  for (let row = 0; row < 4; row++) {
    const off = row % 2 ? 4 : 0;
    for (let b = -1; b < 2; b++) for (let y = row * 4; y < row * 4 + 3; y++) for (let x = off + b * 8; x < off + b * 8 + 7; x++) if (x >= 0 && x < S) t.set(x, y, vary([68, 34, 40], 0.1, t.rand()));
  }
};
GEN.stone_bricks = (t) => {
  t.noise([122, 121, 122], 0.06);
  for (let x = 0; x < S; x++) { t.set(x, 7, [75, 75, 75]); t.set(x, 15, [75, 75, 75]); }
  for (let y = 0; y < 7; y++) t.set(15, y, [75, 75, 75]);
  for (let y = 8; y < 15; y++) t.set(7, y, [75, 75, 75]);
  for (let x = 0; x < S; x++) { t.set(x, 0, [145, 145, 145]); t.set(x, 8, [145, 145, 145]); }
};
function wool(col) {
  return (t) => {
    t.noise(col, 0.07);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) if ((x + y * 3) % 7 === 0) t.shade(x, y, 0.9);
  };
}
GEN.white_wool = wool([234, 236, 236]);
GEN.red_wool = wool([160, 39, 34]);
GEN.blue_wool = wool([53, 57, 157]);
GEN.yellow_wool = wool([248, 197, 39]);
GEN.green_wool = wool([84, 109, 27]);
GEN.black_wool = wool([25, 25, 29]);
GEN.bookshelf = (t) => {
  planks(t, [162, 130, 78]);
  const cols = [[150, 40, 40], [40, 80, 150], [50, 120, 50], [140, 110, 40], [100, 50, 120], [180, 170, 150]];
  for (const y0 of [1, 9]) {
    t.rect(0, y0, 16, 6, [60, 40, 20]);
    let x = 1;
    while (x < 15) {
      const w = 1 + ((t.rand() * 2) | 0), h = 4 + ((t.rand() * 2) | 0), c = cols[(t.rand() * cols.length) | 0];
      for (let k = 0; k < w && x < 15; k++, x++) for (let y = y0 + 6 - h; y < y0 + 6; y++) t.set(x, y, vary(c, 0.08, t.rand()));
      x++;
    }
  }
};
GEN.tnt_side = (t) => {
  t.noise([200, 50, 40], 0.06);
  for (let x = 0; x < S; x++) if (x % 4 === 3) for (let y = 0; y < S; y++) t.shade(x, y, 0.8);
  t.rect(0, 5, 16, 6, [230, 230, 225]);
  const L = [[1, 6, 3, 1], [2, 7, 1, 3], [5, 6, 1, 4], [6, 7, 1, 1], [7, 8, 1, 1], [8, 6, 1, 4], [10, 6, 3, 1], [11, 7, 1, 3]];
  for (const r of L) t.rect(r[0] + 1, r[1], r[2], r[3], [20, 20, 20]);
};
GEN.tnt_top = (t) => { t.noise([200, 50, 40], 0.06); t.rect(6, 6, 4, 4, [230, 230, 225]); t.rect(7, 7, 2, 2, [40, 40, 40]); };
GEN.tnt_bottom = (t) => { t.noise([200, 50, 40], 0.06); };
function metal(col) {
  return (t) => {
    t.noise(col, 0.04);
    for (let i = 0; i < S; i++) {
      t.set(i, 0, col.map((v) => Math.min(255, v * 1.15))); t.set(0, i, col.map((v) => Math.min(255, v * 1.15)));
      t.set(i, 15, col.map((v) => v * 0.75)); t.set(15, i, col.map((v) => v * 0.75));
    }
    for (let i = 2; i < 6; i++) t.set(i, 2, col.map((v) => Math.min(255, v * 1.25)));
  };
}
GEN.iron_block = metal([220, 220, 220]);
GEN.gold_block = metal([248, 210, 60]);
GEN.diamond_block = metal([100, 225, 220]);
GEN.coal_block = metal([28, 28, 30]);
GEN.quartz_block_side = (t) => { t.noise([236, 230, 222], 0.025); };
GEN.quartz_block_top = (t) => { t.noise([236, 230, 222], 0.02); for (let i = 1; i < 15; i++) { t.set(i, 1, [220, 214, 205]); t.set(1, i, [220, 214, 205]); } };
GEN.magma = (t) => {
  voronoiCells(t, 7, 0.9, (x, y, r, e) => {
    t.set(x, y, e ? vary([255, 140, 30], 0.15, t.rand()) : vary([110, 40, 20], 0.15, t.rand()));
  });
};
GEN.chest_top = (t) => { planks(t, [160, 110, 50]); for (let i = 0; i < S; i++) { t.set(i, 0, [60, 40, 20]); t.set(i, 15, [60, 40, 20]); t.set(0, i, [60, 40, 20]); t.set(15, i, [60, 40, 20]); } };
GEN.chest_side = (t) => { GEN.chest_top(t); for (let i = 0; i < S; i++) t.set(i, 5, [60, 40, 20]); };
GEN.chest_front = (t) => { GEN.chest_side(t); t.rect(7, 4, 2, 4, [200, 200, 200]); t.rect(7, 6, 2, 1, [80, 80, 80]); };
GEN.pumpkin_side = (t) => {
  t.noise([220, 130, 25], 0.06);
  for (let x = 0; x < S; x++) if (x % 4 === 0) for (let y = 0; y < S; y++) t.shade(x, y, 0.82);
};
GEN.pumpkin_top = (t) => { GEN.pumpkin_side(t); t.rect(6, 6, 4, 4, [100, 80, 30]); t.rect(7, 7, 2, 2, [80, 110, 40]); };
GEN.carved_pumpkin = (t) => {
  GEN.pumpkin_side(t);
  const glow = [255, 220, 90];
  t.rect(3, 4, 3, 3, glow); t.rect(10, 4, 3, 3, glow);
  t.rect(3, 10, 10, 2, glow); t.rect(5, 12, 2, 1, glow); t.rect(9, 12, 2, 1, glow); t.rect(6, 9, 1, 1, glow); t.rect(9, 9, 1, 1, glow);
};
GEN.water_still = (t) => {
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const v = Math.sin(x * 0.8 + y * 0.3) * Math.sin(y * 0.9 - x * 0.2) * 0.5 + 0.5;
    const g = 150 + v * 60 + t.rand() * 20;
    t.set(x, y, [g, g, g], 255);
  }
};
GEN.lava_still = (t) => {
  voronoiCells(t, 10, 1.2, (x, y, r, e, d) => {
    t.set(x, y, e ? vary([255, 180, 40], 0.1, t.rand()) : vary(mix([230, 80, 10], [190, 40, 5], r), 0.08, t.rand()));
  });
};
for (let s = 0; s < 10; s++) {
  GEN['destroy_stage_' + s] = (t) => {
    t.clear();
    const r = mulberry32(777);
    const cracks = 3 + s * 2;
    for (let c = 0; c < cracks; c++) {
      let x = 8 + ((r() - 0.5) * 6) | 0, y = 8 + ((r() - 0.5) * 6) | 0;
      const len = 3 + s;
      for (let k = 0; k < len; k++) {
        t.set(x, y, [20, 20, 20], 230);
        x += Math.round((r() - 0.5) * 2.5); y += Math.round((r() - 0.5) * 2.5);
      }
    }
  };
}

// Mapping from our texture names to vanilla resource-pack file candidates.
const PACK_NAMES = {
  grass_top: ['grass_block_top', 'grass_top'],
  grass_side: ['grass_block_side', 'grass_side'],
  grass_side_snowy: ['grass_block_snow', 'grass_side_snowed'],
  tall_grass: ['short_grass', 'grass', 'tallgrass'],
  snow: ['snow'],
  oak_leaves: ['oak_leaves', 'leaves_oak'],
  magma: ['magma'],
  packed_ice: ['packed_ice'],
  quartz_block_side: ['quartz_block_side'],
  quartz_block_top: ['quartz_block_top'],
  lily_pad: ['lily_pad', 'waterlily'],
};

// ---------- Texture array ----------
export const TEX_NAMES = [];
export const TEX_INDEX = {};
function addTex(name) {
  if (TEX_INDEX[name] === undefined) { TEX_INDEX[name] = TEX_NAMES.length; TEX_NAMES.push(name); }
  return TEX_INDEX[name];
}
for (let i = 1; i < BLOCK_COUNT; i++) {
  const b = BLOCKS[i];
  if (!b) continue;
  for (const k of ['top', 'side', 'bottom', 'front']) addTex(b.tex[k]);
}
for (const n of ['water_still', 'lava_still']) addTex(n);
for (let s = 0; s < 10; s++) addTex('destroy_stage_' + s);

// Per-block face texture layer lookup: faceTex[id*6 + face] (faces: +x,-x,+y,-y,+z,-z)
export const faceTex = new Int16Array(256 * 6);
for (let i = 0; i < BLOCK_COUNT; i++) {
  const b = BLOCKS[i];
  if (!b || i === 0) continue;
  const side = TEX_INDEX[b.tex.side];
  faceTex.set([side, side, TEX_INDEX[b.tex.top], TEX_INDEX[b.tex.bottom], side, side], i * 6);
}
export function frontTex(id) {
  return TEX_INDEX[BLOCKS[id].tex.front];
}

export const texData = {}; // name -> Uint8ClampedArray (S*S*4) at size texSize
export let texSize = S;

function generateAll() {
  for (const name of TEX_NAMES) {
    const t = new Tex(name);
    const gen = GEN[name];
    if (gen) gen(t);
    else t.noise([255, 0, 255], 0); // missing texture marker
    texData[name] = t.d;
  }
}
generateAll();

let arrayTexture = null;
export function getBlockTextureArray() {
  if (arrayTexture) return arrayTexture;
  arrayTexture = new THREE.DataArrayTexture(new Uint8Array(texSize * texSize * 4 * TEX_NAMES.length), texSize, texSize, TEX_NAMES.length);
  fillArrayTexture();
  arrayTexture.magFilter = THREE.NearestFilter;
  arrayTexture.minFilter = THREE.NearestMipmapLinearFilter;
  arrayTexture.generateMipmaps = true;
  arrayTexture.colorSpace = THREE.SRGBColorSpace;
  return arrayTexture;
}
function fillArrayTexture() {
  const size = texSize * texSize * 4;
  if (arrayTexture.image.width !== texSize) {
    arrayTexture.dispose();
    arrayTexture.image = { data: new Uint8Array(size * TEX_NAMES.length), width: texSize, height: texSize, depth: TEX_NAMES.length };
  }
  const data = arrayTexture.image.data;
  TEX_NAMES.forEach((n, i) => data.set(texData[n], i * size));
  arrayTexture.needsUpdate = true;
}

// ---------- Biome tint colors ----------
export const TINTS = {
  grass: [0.57, 0.74, 0.35],
  foliage: [0.47, 0.67, 0.18],
  birch: [0.5, 0.65, 0.33],
  spruce: [0.38, 0.6, 0.38],
  lily: [0.13, 0.5, 0.19],
};

// ---------- Icons ----------
function texCanvas(name, tint) {
  const c = document.createElement('canvas');
  c.width = c.height = texSize;
  const ctx = c.getContext('2d');
  const img = ctx.createImageData(texSize, texSize);
  const src = texData[name];
  for (let i = 0; i < src.length; i += 4) {
    img.data[i] = tint ? src[i] * tint[0] : src[i];
    img.data[i + 1] = tint ? src[i + 1] * tint[1] : src[i + 1];
    img.data[i + 2] = tint ? src[i + 2] * tint[2] : src[i + 2];
    img.data[i + 3] = src[i + 3];
  }
  ctx.putImageData(img, 0, 0);
  return c;
}

function blockIcon(b) {
  const N = 48;
  const c = document.createElement('canvas');
  c.width = c.height = N;
  const ctx = c.getContext('2d');
  ctx.imageSmoothingEnabled = false;
  const tint = b.tint ? TINTS[b.tint] : null;
  if (b.render !== 'cube' && b.render !== 'liquid') {
    ctx.drawImage(texCanvas(b.tex.side, tint), 4, 4, N - 8, N - 8);
    return c;
  }
  const texName = b.render === 'liquid' ? (b.liquid === 'water' ? 'water_still' : 'lava_still') : null;
  const liquidTint = b.liquid === 'water' ? [0.25, 0.45, 0.95] : null;
  const top = texCanvas(texName || b.tex.top, liquidTint || (b.tintTop ? TINTS.grass : tint));
  const left = texCanvas(texName || b.tex.front, liquidTint || tint);
  const right = texCanvas(texName || b.tex.side, liquidTint || tint);
  const s = texSize;
  const w = N / 2 - 2, h = w / 2;
  const cx = N / 2, cy = 4;
  // top face
  ctx.setTransform(w / s, h / s, -w / s, h / s, cx, cy);
  ctx.drawImage(top, 0, 0);
  // left face
  ctx.setTransform(w / s, h / s, 0, (w * 1.15) / s, cx - w, cy + h);
  ctx.drawImage(left, 0, 0);
  ctx.globalAlpha = 0.25; ctx.fillStyle = '#000'; ctx.fillRect(0, 0, s, s); ctx.globalAlpha = 1;
  // right face
  ctx.setTransform(w / s, -h / s, 0, (w * 1.15) / s, cx, cy + 2 * h);
  ctx.drawImage(right, 0, 0);
  ctx.globalAlpha = 0.45; ctx.fillStyle = '#000'; ctx.fillRect(0, 0, s, s); ctx.globalAlpha = 1;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  return c;
}

// --- item sprites ---
const MAT_COL = {
  wooden: [160, 125, 70], stone: [130, 130, 130], iron: [225, 225, 225], golden: [250, 215, 60], diamond: [90, 230, 220],
};
function line(t, x0, y0, x1, y1, col) {
  const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
  for (let i = 0; i <= n; i++) t.set(Math.round(x0 + ((x1 - x0) * i) / n), Math.round(y0 + ((y1 - y0) * i) / n), col);
}
function outline(t) {
  const src = new Uint8ClampedArray(t.d);
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    if (src[(y * S + x) * 4 + 3]) continue;
    let near = false;
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const nx = x + dx, ny = y + dy;
      if (nx >= 0 && ny >= 0 && nx < S && ny < S && src[(ny * S + nx) * 4 + 3] > 0) near = true;
    }
    if (near) t.set(x, y, [30, 25, 20], 255);
  }
}
const dk = (c, f = 0.7) => c.map((v) => v * f);
const lt = (c, f = 1.25) => c.map((v) => Math.min(255, v * f));
function stickTo(t, x1, y1) { line(t, 2, 13, x1, y1, [120, 90, 50]); line(t, 3, 13, x1 + 1, y1, [90, 65, 35]); }
const SPRITES = {
  stick: (t) => { line(t, 3, 12, 12, 3, [130, 98, 55]); line(t, 4, 12, 12, 4, [95, 70, 38]); },
  coal: (t) => { t.rect(4, 5, 8, 7, [40, 40, 42], 0.2); t.rect(5, 4, 5, 1, [55, 55, 58]); t.set(6, 6, [80, 80, 85]); },
  charcoal: (t) => { t.rect(4, 5, 8, 7, [55, 45, 35], 0.2); t.set(6, 6, [90, 75, 60]); },
  iron_ingot: (t) => ingot(t, [215, 215, 215]),
  gold_ingot: (t) => ingot(t, [250, 215, 60]),
  nether_brick: (t) => ingot(t, [80, 35, 40]),
  brick: (t) => ingot(t, [170, 80, 60]),
  diamond: (t) => gem(t, [90, 230, 225]),
  emerald: (t) => gem(t, [40, 210, 100]),
  quartz: (t) => gem(t, [235, 230, 222]),
  glowstone_dust: (t) => { for (let i = 0; i < 18; i++) t.set(4 + ((t.rand() * 8) | 0), 5 + ((t.rand() * 7) | 0), vary([250, 220, 110], 0.15, t.rand())); },
  sugar: (t) => { for (let i = 0; i < 22; i++) t.set(4 + ((t.rand() * 8) | 0), 7 + ((t.rand() * 6) | 0), [245, 245, 245]); },
  gunpowder: (t) => { for (let i = 0; i < 22; i++) t.set(4 + ((t.rand() * 8) | 0), 7 + ((t.rand() * 6) | 0), vary([90, 90, 90], 0.3, t.rand())); },
  flint: (t) => { t.rect(5, 4, 6, 8, [60, 60, 65], 0.15); t.rect(6, 3, 3, 1, [80, 80, 85]); t.set(7, 6, [120, 120, 125]); },
  feather: (t) => { line(t, 4, 13, 12, 3, [240, 240, 240]); line(t, 5, 12, 12, 5, [210, 210, 210]); line(t, 4, 11, 10, 3, [220, 220, 220]); line(t, 3, 14, 5, 12, [150, 150, 150]); },
  leather: (t) => { t.rect(4, 4, 8, 9, [150, 80, 40], 0.08); t.set(4, 4, [0, 0, 0, 0]); t.set(11, 12, [0, 0, 0, 0]); },
  string: (t) => { for (let i = 0; i < 12; i++) t.set(2 + i, 8 + Math.round(Math.sin(i * 0.9) * 2), [240, 240, 240]); },
  bone: (t) => { line(t, 4, 11, 11, 4, [235, 230, 210]); t.rect(3, 11, 2, 2, [235, 230, 210]); t.rect(11, 3, 2, 2, [235, 230, 210]); },
  rotten_flesh: (t) => { t.rect(4, 5, 8, 7, [140, 80, 50], 0.2); t.speck([90, 120, 50], 6); },
  apple: (t) => { for (let y = 5; y < 14; y++) for (let x = 3; x < 13; x++) { const dx = x - 7.5, dy = y - 9.2; if (dx * dx + dy * dy < 19) t.set(x, y, vary(dx < -1 && dy < 0 ? [255, 90, 80] : [205, 25, 30], 0.06, t.rand())); } t.rect(8, 3, 1, 3, [90, 60, 30]); t.rect(9, 3, 2, 1, [60, 140, 40]); },
  bread: (t) => { t.rect(2, 7, 12, 5, [200, 140, 60], 0.06); t.rect(3, 6, 10, 1, [215, 160, 80]); for (const x of [5, 8, 11]) t.set(x, 8, [235, 200, 130]); },
  wheat: (t) => { for (const x of [5, 8, 11]) { line(t, x, 14, x - 1, 3, [200, 170, 70]); t.set(x - 1, 4, [230, 200, 100]); t.set(x, 6, [230, 200, 100]); } },
  porkchop: (t) => meat(t, [240, 140, 140], [255, 220, 220]),
  cooked_porkchop: (t) => meat(t, [180, 110, 60], [235, 200, 150]),
  beef: (t) => meat(t, [200, 50, 45], [240, 220, 210]),
  cooked_beef: (t) => meat(t, [110, 60, 35], [150, 100, 60]),
  mutton: (t) => meat(t, [210, 70, 60], [240, 230, 220]),
  cooked_mutton: (t) => meat(t, [130, 75, 45], [200, 160, 120]),
  chicken: (t) => { meat(t, [245, 200, 180], [255, 235, 220]); t.rect(11, 11, 3, 2, [240, 240, 230]); },
  cooked_chicken: (t) => { meat(t, [200, 140, 70], [230, 180, 110]); t.rect(11, 11, 3, 2, [240, 240, 230]); },
  bucket: (t) => bucket(t, null),
  water_bucket: (t) => bucket(t, [60, 90, 230]),
  lava_bucket: (t) => bucket(t, [240, 110, 20]),
  flint_and_steel: (t) => { line(t, 3, 4, 8, 4, [200, 200, 200]); line(t, 3, 4, 3, 9, [200, 200, 200]); line(t, 8, 4, 8, 7, [170, 170, 170]); t.rect(8, 9, 5, 5, [60, 60, 65], 0.15); },
  paper: (t) => { t.rect(3, 3, 10, 11, [240, 240, 235]); for (const y of [6, 9, 12]) line(t, 5, y, 11, y, [200, 200, 200]); },
  book: (t) => { t.rect(3, 3, 10, 11, [120, 50, 30], 0.05); t.rect(4, 4, 1, 9, [90, 35, 20]); t.rect(12, 4, 1, 9, [240, 235, 220]); },
  arrow: (t) => { line(t, 3, 12, 11, 4, [120, 90, 50]); t.rect(11, 3, 2, 2, [180, 180, 180]); t.set(10, 3, [180, 180, 180]); t.set(13, 5, [180, 180, 180]); line(t, 2, 11, 4, 13, [240, 240, 240]); },
  clay_ball: (t) => { for (let y = 5; y < 12; y++) for (let x = 4; x < 12; x++) if ((x - 7.5) ** 2 + (y - 8.5) ** 2 < 11) t.set(x, y, vary([165, 170, 185], 0.06, t.rand())); },
};
function ingot(t, c) {
  for (let y = 0; y < 5; y++) for (let x = y; x < 12 - y + 2; x++) t.set(x + 1, 10 - y, vary(y === 4 ? lt(c) : c, 0.04, t.rand()));
  line(t, 1, 10, 14, 10, dk(c));
}
function gem(t, c) {
  const rows = [[6, 9], [4, 11], [3, 12], [3, 12], [4, 11], [5, 10], [6, 9], [7, 8]];
  rows.forEach(([a, b], i) => { for (let x = a; x <= b; x++) t.set(x, 4 + i, x < 6 + i * 0.3 ? lt(c) : x > 10 - i * 0.2 ? dk(c, 0.8) : c); });
  t.set(5, 6, [255, 255, 255]);
}
function meat(t, c, fat) {
  for (let y = 4; y < 13; y++) for (let x = 3; x < 13; x++) { const dx = x - 7.5, dy = y - 8; if (dx * dx * 0.6 + dy * dy < 14) t.set(x, y, vary(c, 0.07, t.rand())); }
  line(t, 4, 6, 10, 4, fat);
}
function bucket(t, fill) {
  const g = [180, 180, 185];
  for (let y = 5; y < 14; y++) { const inset = Math.floor((y - 5) / 4); for (let x = 3 + inset; x < 13 - inset; x++) t.set(x, y, x < 5 + inset ? lt(g, 1.1) : g); }
  line(t, 3, 4, 12, 4, dk(g)); line(t, 3, 2, 3, 4, dk(g)); line(t, 12, 2, 12, 4, dk(g)); line(t, 4, 1, 11, 1, dk(g));
  if (fill) t.rect(4, 5, 8, 2, fill, 0.08);
  else t.rect(4, 5, 8, 2, [60, 60, 65]);
}
function toolSprite(t, type, mat) {
  const c = MAT_COL[mat];
  if (type === 'sword') {
    line(t, 4, 11, 12, 3, c); line(t, 5, 11, 13, 3, dk(c, 0.85)); line(t, 4, 10, 12, 2, lt(c));
    line(t, 2, 10, 6, 14, [80, 60, 30]); line(t, 2, 13, 4, 11, [110, 80, 45]); t.set(1, 14, [110, 80, 45]); t.set(2, 14, [110, 80, 45]);
    return;
  }
  stickTo(t, 11, 4);
  if (type === 'pickaxe') {
    line(t, 4, 2, 9, 2, c); line(t, 9, 2, 13, 6, c); line(t, 13, 6, 13, 11, c);
    line(t, 5, 1, 9, 1, lt(c)); line(t, 10, 2, 14, 6, dk(c)); t.set(14, 7, dk(c)); line(t, 10, 3, 12, 5, c);
  } else if (type === 'axe') {
    t.rect(9, 1, 4, 5, c); t.rect(8, 2, 1, 4, lt(c)); t.rect(13, 2, 1, 3, dk(c)); t.rect(10, 6, 3, 1, dk(c));
  } else if (type === 'shovel') {
    t.rect(10, 1, 4, 4, c); t.rect(11, 0, 2, 1, lt(c)); t.rect(10, 1, 1, 3, lt(c)); t.rect(13, 2, 1, 3, dk(c)); t.set(10, 5, dk(c));
  }
}

const iconCache = new Map();
const iconCanvasCache = new Map();
const iconPixelCache = new Map();

// Raw 16x16 RGBA pixels of an item's flat icon (avoids slow canvas readback).
export function iconPixels(id) {
  if (iconPixelCache.has(id)) return iconPixelCache.get(id);
  const def = ITEMS[id];
  let out = new Uint8ClampedArray(S * S * 4);
  if (def && def.isBlock) {
    const src = texData[def.tex.side];
    const tint = def.tint ? TINTS[def.tint] : null;
    for (let y = 0; y < S; y++)
      for (let x = 0; x < S; x++) {
        const si = (Math.floor((y * texSize) / S) * texSize + Math.floor((x * texSize) / S)) * 4;
        const di = (y * S + x) * 4;
        out[di] = tint ? src[si] * tint[0] : src[si];
        out[di + 1] = tint ? src[si + 1] * tint[1] : src[si + 1];
        out[di + 2] = tint ? src[si + 2] * tint[2] : src[si + 2];
        out[di + 3] = src[si + 3];
      }
  } else if (def && packItemPixels[def.icon]) {
    out = packItemPixels[def.icon];
  } else {
    iconCanvas(id);
    if (iconPixelCache.has(id)) return iconPixelCache.get(id);
  }
  iconPixelCache.set(id, out);
  return out;
}
export function iconCanvas(id) {
  if (iconCanvasCache.has(id)) return iconCanvasCache.get(id);
  const def = ITEMS[id];
  let c;
  if (!def) {
    c = document.createElement('canvas'); c.width = c.height = 16;
  } else if (def.isBlock) {
    c = blockIcon(def);
  } else if (packItemImages[def.icon]) {
    c = packItemImages[def.icon];
  } else {
    const t = new Tex(def.key);
    t.clear();
    if (def.tool) toolSprite(t, def.tool.type, def.tool.mat);
    else if (SPRITES[def.key]) SPRITES[def.key](t);
    else t.rect(4, 4, 8, 8, [255, 0, 255]);
    outline(t);
    iconPixelCache.set(id, t.d);
    c = document.createElement('canvas'); c.width = c.height = S;
    const ctx = c.getContext('2d');
    const img = ctx.createImageData(S, S);
    img.data.set(t.d);
    ctx.putImageData(img, 0, 0);
  }
  iconCanvasCache.set(id, c);
  return c;
}
export function iconURL(id) {
  if (iconCache.has(id)) return iconCache.get(id);
  const url = iconCanvas(id).toDataURL();
  iconCache.set(id, url);
  return url;
}

// ---------- Resource pack loading (user-supplied .zip) ----------
const packItemImages = {};
const packItemPixels = {};

async function readZip(buf) {
  const view = new DataView(buf);
  let eocd = -1;
  for (let i = buf.byteLength - 22; i >= Math.max(0, buf.byteLength - 70000); i--) {
    if (view.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('Not a zip file');
  const count = view.getUint16(eocd + 10, true);
  let off = view.getUint32(eocd + 16, true);
  const files = new Map();
  const dec = new TextDecoder();
  for (let n = 0; n < count; n++) {
    if (view.getUint32(off, true) !== 0x02014b50) break;
    const method = view.getUint16(off + 10, true);
    const csize = view.getUint32(off + 20, true);
    const nameLen = view.getUint16(off + 28, true);
    const extraLen = view.getUint16(off + 30, true);
    const commentLen = view.getUint16(off + 32, true);
    const local = view.getUint32(off + 42, true);
    const name = dec.decode(new Uint8Array(buf, off + 46, nameLen));
    files.set(name, { method, csize, local });
    off += 46 + nameLen + extraLen + commentLen;
  }
  return {
    names: [...files.keys()],
    async get(name) {
      const f = files.get(name);
      if (!f) return null;
      const lnl = view.getUint16(f.local + 26, true), lel = view.getUint16(f.local + 28, true);
      const data = new Uint8Array(buf, f.local + 30 + lnl + lel, f.csize);
      if (f.method === 0) return data;
      if (f.method !== 8) return null;
      const ds = new DecompressionStream('deflate-raw');
      const out = new Blob([data]).stream().pipeThrough(ds);
      return new Uint8Array(await new Response(out).arrayBuffer());
    },
  };
}

async function decodePNG(bytes) {
  const bmp = await createImageBitmap(new Blob([bytes], { type: 'image/png' }));
  return bmp;
}

// Loads a Java-edition style resource pack the user owns. Returns number of textures replaced.
export async function loadResourcePack(file) {
  const zip = await readZip(await file.arrayBuffer());
  const base = zip.names.find((n) => /assets\/minecraft\/textures\/(block|blocks)\//.test(n));
  if (!base) throw new Error('No assets/minecraft/textures/block folder found in pack');
  const prefix = base.slice(0, base.indexOf('assets/minecraft/textures/'));
  const blockDir = /textures\/blocks\//.test(base) ? 'blocks' : 'block';
  const found = {};
  let size = 16;
  for (const name of TEX_NAMES) {
    const cands = PACK_NAMES[name] || [name];
    for (const c of cands) {
      const bytes = await zip.get(`${prefix}assets/minecraft/textures/${blockDir}/${c}.png`);
      if (bytes) {
        try {
          const bmp = await decodePNG(bytes);
          found[name] = bmp;
          size = Math.max(size, bmp.width);
        } catch { /* skip bad image */ }
        break;
      }
    }
  }
  size = Math.min(size, 64);
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = size;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  ctx.imageSmoothingEnabled = false;
  const old = texSize;
  texSize = size;
  for (const name of TEX_NAMES) {
    ctx.clearRect(0, 0, size, size);
    const bmp = found[name];
    if (bmp) {
      // animated strips: take first square frame
      ctx.drawImage(bmp, 0, 0, bmp.width, bmp.width, 0, 0, size, size);
    } else {
      const c = document.createElement('canvas');
      c.width = c.height = old;
      const cc = c.getContext('2d');
      const img = cc.createImageData(old, old);
      img.data.set(texData[name]);
      cc.putImageData(img, 0, 0);
      ctx.drawImage(c, 0, 0, size, size);
    }
    texData[name] = ctx.getImageData(0, 0, size, size).data;
  }
  // Item icons
  for (let i = 256; i < ITEM_COUNT; i++) {
    const def = ITEMS[i];
    const cands = [def.key, def.key.replace('wooden', 'wood').replace('golden', 'gold')];
    for (const c of cands) {
      const bytes = await zip.get(`${prefix}assets/minecraft/textures/item/${c}.png`) || await zip.get(`${prefix}assets/minecraft/textures/items/${c}.png`);
      if (bytes) {
        try {
          const bmp = await decodePNG(bytes);
          const ic = document.createElement('canvas');
          ic.width = ic.height = 16;
          const ictx = ic.getContext('2d', { willReadFrequently: true });
          ictx.imageSmoothingEnabled = false;
          ictx.drawImage(bmp, 0, 0, bmp.width, bmp.width, 0, 0, 16, 16);
          packItemImages[def.icon] = ic;
          packItemPixels[def.icon] = ictx.getImageData(0, 0, 16, 16).data;
        } catch { /* skip */ }
        break;
      }
    }
  }
  iconCache.clear();
  iconCanvasCache.clear();
  iconPixelCache.clear();
  if (arrayTexture) fillArrayTexture();
  return Object.keys(found).length;
}
