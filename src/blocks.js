// Block + item registry, recipes and smelting.

export const B = {}; // name -> id
export const BLOCKS = []; // id -> def
export const ITEMS = []; // id -> def (blocks share ids < 256)
export const I = {}; // item name -> id (includes block names)

function block(id, key, name, opts = {}) {
  const def = {
    id,
    key,
    name,
    isBlock: true,
    solid: true,
    opaque: true,
    render: 'cube', // cube | cross | liquid | torch | none | portal
    cutout: false, // alpha-tested (leaves, glass)
    light: 0,
    hardness: 1,
    tool: null, // pickaxe | axe | shovel | null
    level: 0, // required tool tier to drop (1 wood, 2 stone, 3 iron, 4 diamond)
    drop: id,
    tint: null, // grass | foliage | birch | spruce
    gravity: false,
    stack: 64,
    replaceable: false,
    ...opts,
  };
  if (!def.tex) def.tex = { all: key };
  if (def.tex.all) def.tex = { top: def.tex.all, side: def.tex.all, bottom: def.tex.all, ...def.tex };
  if (!def.tex.bottom) def.tex.bottom = def.tex.top;
  if (!def.tex.front) def.tex.front = def.tex.side;
  BLOCKS[id] = def;
  ITEMS[id] = def;
  B[key] = id;
  I[key] = id;
  return def;
}

const leaves = { hardness: 0.2, cutout: true, opaque: false, tool: 'hoe', drop: 0, flammable: true };
const plant = { solid: false, opaque: false, render: 'cross', hardness: 0, cutout: true, replaceable: false };

block(0, 'air', 'Air', { solid: false, opaque: false, render: 'none', hardness: 0, drop: 0, replaceable: true });
block(1, 'stone', 'Stone', { hardness: 1.5, tool: 'pickaxe', level: 1, drop: 4 });
block(2, 'grass', 'Grass Block', { hardness: 0.6, tool: 'shovel', drop: 3, tex: { top: 'grass_top', side: 'grass_side', bottom: 'dirt' }, tintTop: true });
block(3, 'dirt', 'Dirt', { hardness: 0.5, tool: 'shovel' });
block(4, 'cobblestone', 'Cobblestone', { hardness: 2, tool: 'pickaxe', level: 1 });
block(5, 'oak_planks', 'Oak Planks', { hardness: 2, tool: 'axe' });
block(6, 'bedrock', 'Bedrock', { hardness: -1 });
block(7, 'water', 'Water', { solid: false, opaque: false, render: 'liquid', hardness: -1, drop: 0, replaceable: true, liquid: 'water', stack: 1 });
block(8, 'lava', 'Lava', { solid: false, opaque: false, render: 'liquid', hardness: -1, drop: 0, replaceable: true, liquid: 'lava', light: 15, stack: 1 });
block(9, 'sand', 'Sand', { hardness: 0.5, tool: 'shovel', gravity: true });
block(10, 'gravel', 'Gravel', { hardness: 0.6, tool: 'shovel', gravity: true });
block(11, 'oak_log', 'Oak Log', { hardness: 2, tool: 'axe', tex: { top: 'oak_log_top', side: 'oak_log' } });
block(12, 'oak_leaves', 'Oak Leaves', { ...leaves, tint: 'foliage' });
block(13, 'glass', 'Glass', { hardness: 0.3, cutout: true, opaque: false, drop: 0 });
block(14, 'coal_ore', 'Coal Ore', { hardness: 3, tool: 'pickaxe', level: 1, drop: 'coal' });
block(15, 'iron_ore', 'Iron Ore', { hardness: 3, tool: 'pickaxe', level: 2 });
block(16, 'gold_ore', 'Gold Ore', { hardness: 3, tool: 'pickaxe', level: 3 });
block(17, 'diamond_ore', 'Diamond Ore', { hardness: 3, tool: 'pickaxe', level: 3, drop: 'diamond' });
block(18, 'sandstone', 'Sandstone', { hardness: 0.8, tool: 'pickaxe', level: 1, tex: { top: 'sandstone_top', side: 'sandstone' } });
block(19, 'snow', 'Snow Block', { hardness: 0.2, tool: 'shovel' });
block(20, 'ice', 'Ice', { hardness: 0.5, tool: 'pickaxe', opaque: false, translucent: true, drop: 0, slippery: true });
block(21, 'cactus', 'Cactus', { hardness: 0.4, opaque: false, cutout: true, tex: { top: 'cactus_top', side: 'cactus_side' }, damage: true });
block(22, 'clay', 'Clay', { hardness: 0.6, tool: 'shovel' });
block(23, 'birch_log', 'Birch Log', { hardness: 2, tool: 'axe', tex: { top: 'birch_log_top', side: 'birch_log' } });
block(24, 'birch_leaves', 'Birch Leaves', { ...leaves, tint: 'birch' });
block(25, 'spruce_log', 'Spruce Log', { hardness: 2, tool: 'axe', tex: { top: 'spruce_log_top', side: 'spruce_log' } });
block(26, 'spruce_leaves', 'Spruce Leaves', { ...leaves, tint: 'spruce' });
block(27, 'tall_grass', 'Grass', { ...plant, tint: 'grass', drop: 0, replaceable: true });
block(28, 'dandelion', 'Dandelion', { ...plant });
block(29, 'poppy', 'Poppy', { ...plant });
block(30, 'dead_bush', 'Dead Bush', { ...plant, drop: 'stick', replaceable: true });
block(31, 'crafting_table', 'Crafting Table', { hardness: 2.5, tool: 'axe', tex: { top: 'crafting_table_top', side: 'crafting_table_side', front: 'crafting_table_front', bottom: 'oak_planks' }, interact: 'crafting' });
block(32, 'furnace', 'Furnace', { hardness: 3.5, tool: 'pickaxe', level: 1, tex: { top: 'furnace_top', side: 'furnace_side', front: 'furnace_front', bottom: 'furnace_top' }, interact: 'furnace', facing: true });
block(33, 'torch', 'Torch', { solid: false, opaque: false, render: 'torch', hardness: 0, light: 14, cutout: true });
block(34, 'obsidian', 'Obsidian', { hardness: 50, tool: 'pickaxe', level: 4 });
block(35, 'netherrack', 'Netherrack', { hardness: 0.4, tool: 'pickaxe', level: 1 });
block(36, 'soul_sand', 'Soul Sand', { hardness: 0.5, tool: 'shovel', slow: true });
block(37, 'glowstone', 'Glowstone', { hardness: 0.3, light: 15, drop: 'glowstone_dust' });
block(38, 'nether_quartz_ore', 'Nether Quartz Ore', { hardness: 3, tool: 'pickaxe', level: 1, drop: 'quartz' });
block(39, 'nether_portal', 'Nether Portal', { solid: false, opaque: false, render: 'portal', hardness: -1, light: 11, drop: 0 });
block(40, 'bricks', 'Bricks', { hardness: 2, tool: 'pickaxe', level: 1 });
block(41, 'stone_bricks', 'Stone Bricks', { hardness: 1.5, tool: 'pickaxe', level: 1 });
block(42, 'white_wool', 'White Wool', { hardness: 0.8 });
block(43, 'bookshelf', 'Bookshelf', { hardness: 1.5, tool: 'axe', tex: { top: 'oak_planks', side: 'bookshelf' } });
block(44, 'tnt', 'TNT', { hardness: 0, tex: { top: 'tnt_top', side: 'tnt_side', bottom: 'tnt_bottom' }, interact: 'tnt' });
block(45, 'iron_block', 'Block of Iron', { hardness: 5, tool: 'pickaxe', level: 2 });
block(46, 'gold_block', 'Block of Gold', { hardness: 3, tool: 'pickaxe', level: 3 });
block(47, 'diamond_block', 'Block of Diamond', { hardness: 5, tool: 'pickaxe', level: 3 });
block(48, 'jungle_log', 'Jungle Log', { hardness: 2, tool: 'axe', tex: { top: 'jungle_log_top', side: 'jungle_log' } });
block(49, 'jungle_leaves', 'Jungle Leaves', { ...leaves, tint: 'foliage' });
block(50, 'mossy_cobblestone', 'Mossy Cobblestone', { hardness: 2, tool: 'pickaxe', level: 1 });
block(51, 'red_sand', 'Red Sand', { hardness: 0.5, tool: 'shovel', gravity: true });
block(52, 'snowy_grass', 'Snowy Grass Block', { hardness: 0.6, tool: 'shovel', drop: 3, tex: { top: 'snow', side: 'grass_side_snowy', bottom: 'dirt' } });
block(53, 'brown_mushroom', 'Brown Mushroom', { ...plant, light: 1 });
block(54, 'red_mushroom', 'Red Mushroom', { ...plant });
block(55, 'nether_bricks', 'Nether Bricks', { hardness: 2, tool: 'pickaxe', level: 1 });
block(56, 'magma_block', 'Magma Block', { hardness: 0.5, tool: 'pickaxe', level: 1, light: 3, tex: { all: 'magma' }, damage: true });
block(57, 'chest', 'Chest', { hardness: 2.5, tool: 'axe', tex: { top: 'chest_top', side: 'chest_side', front: 'chest_front', bottom: 'chest_top' }, interact: 'chest', facing: true });
block(58, 'birch_planks', 'Birch Planks', { hardness: 2, tool: 'axe' });
block(59, 'spruce_planks', 'Spruce Planks', { hardness: 2, tool: 'axe' });
block(60, 'sugar_cane', 'Sugar Cane', { ...plant, tint: 'grass' });
block(61, 'pumpkin', 'Pumpkin', { hardness: 1, tool: 'axe', tex: { top: 'pumpkin_top', side: 'pumpkin_side', front: 'carved_pumpkin', bottom: 'pumpkin_top' }, facing: true });
block(62, 'red_wool', 'Red Wool', { hardness: 0.8 });
block(63, 'blue_wool', 'Blue Wool', { hardness: 0.8 });
block(64, 'yellow_wool', 'Yellow Wool', { hardness: 0.8 });
block(65, 'green_wool', 'Green Wool', { hardness: 0.8 });
block(66, 'black_wool', 'Black Wool', { hardness: 0.8 });
block(67, 'lit_furnace', 'Furnace', { hardness: 3.5, tool: 'pickaxe', level: 1, light: 13, tex: { top: 'furnace_top', side: 'furnace_side', front: 'furnace_front_on', bottom: 'furnace_top' }, interact: 'furnace', facing: true, drop: 32, hidden: true });
block(68, 'coal_block', 'Block of Coal', { hardness: 5, tool: 'pickaxe', level: 1 });
block(69, 'quartz_block', 'Block of Quartz', { hardness: 0.8, tool: 'pickaxe', level: 1, tex: { top: 'quartz_block_top', side: 'quartz_block_side' } });
block(70, 'jungle_planks', 'Jungle Planks', { hardness: 2, tool: 'axe' });
block(71, 'acacia_log', 'Acacia Log', { hardness: 2, tool: 'axe', tex: { top: 'acacia_log_top', side: 'acacia_log' } });
block(72, 'acacia_leaves', 'Acacia Leaves', { ...leaves, tint: 'foliage' });
block(73, 'blue_ice', 'Packed Ice', { hardness: 0.5, tool: 'pickaxe', tex: { all: 'packed_ice' }, slippery: true });
block(74, 'lily_pad', 'Lily Pad', { solid: false, opaque: false, render: 'flat', hardness: 0, cutout: true, tint: 'lily' });
block(75, 'emerald_ore', 'Emerald Ore', { hardness: 3, tool: 'pickaxe', level: 3, drop: 'emerald' });

export const BLOCK_COUNT = 76;

// ---------- Items ----------
function item(id, key, name, opts = {}) {
  const def = { id, key, name, isBlock: false, stack: 64, icon: key, ...opts };
  ITEMS[id] = def;
  I[key] = id;
  return def;
}

let nid = 256;
const it = (key, name, opts) => item(nid++, key, name, opts);

it('stick', 'Stick');
it('coal', 'Coal', { fuel: 80 });
it('charcoal', 'Charcoal', { fuel: 80 });
it('iron_ingot', 'Iron Ingot');
it('gold_ingot', 'Gold Ingot');
it('diamond', 'Diamond');
it('emerald', 'Emerald');
it('quartz', 'Nether Quartz');
it('glowstone_dust', 'Glowstone Dust');
it('flint', 'Flint');
it('feather', 'Feather');
it('leather', 'Leather');
it('string', 'String');
it('gunpowder', 'Gunpowder');
it('bone', 'Bone');
it('rotten_flesh', 'Rotten Flesh', { food: 2 });
it('apple', 'Apple', { food: 4 });
it('bread', 'Bread', { food: 5 });
it('wheat', 'Wheat');
it('porkchop', 'Raw Porkchop', { food: 3 });
it('cooked_porkchop', 'Cooked Porkchop', { food: 8 });
it('beef', 'Raw Beef', { food: 3 });
it('cooked_beef', 'Steak', { food: 8 });
it('chicken', 'Raw Chicken', { food: 2 });
it('cooked_chicken', 'Cooked Chicken', { food: 6 });
it('mutton', 'Raw Mutton', { food: 2 });
it('cooked_mutton', 'Cooked Mutton', { food: 6 });
it('bucket', 'Bucket', { stack: 16 });
it('water_bucket', 'Water Bucket', { stack: 1, places: 7 });
it('lava_bucket', 'Lava Bucket', { stack: 1, places: 8, fuel: 1000 });
it('flint_and_steel', 'Flint and Steel', { stack: 1, durability: 64 });
it('sugar', 'Sugar');
it('paper', 'Paper');
it('book', 'Book');
it('arrow', 'Arrow');
it('nether_brick', 'Nether Brick');
it('brick', 'Brick');
it('clay_ball', 'Clay Ball');

const TIERS = {
  wooden: { tier: 1, speed: 2, durability: 59, dmg: 0 },
  stone: { tier: 2, speed: 4, durability: 131, dmg: 1 },
  iron: { tier: 3, speed: 6, durability: 250, dmg: 2 },
  golden: { tier: 1, speed: 12, durability: 32, dmg: 0 },
  diamond: { tier: 4, speed: 8, durability: 1561, dmg: 3 },
};
const TOOL_DMG = { pickaxe: 2, axe: 3, shovel: 1.5, sword: 4 };
for (const [mat, t] of Object.entries(TIERS)) {
  for (const type of ['pickaxe', 'axe', 'shovel', 'sword']) {
    it(`${mat}_${type}`, `${mat[0].toUpperCase() + mat.slice(1)} ${type[0].toUpperCase() + type.slice(1)}`, {
      stack: 1,
      tool: { type, tier: t.tier, speed: t.speed, mat },
      durability: t.durability,
      damage: 1 + TOOL_DMG[type] + t.dmg,
    });
  }
}
export const ITEM_COUNT = nid;

// Resolve string drops into ids now that items exist.
for (const b of BLOCKS) {
  if (b && typeof b.drop === 'string') b.drop = I[b.drop];
}
// Fuel for wooden things
for (const k of ['oak_planks', 'birch_planks', 'spruce_planks', 'jungle_planks', 'oak_log', 'birch_log', 'spruce_log', 'jungle_log', 'acacia_log', 'crafting_table', 'bookshelf', 'chest']) {
  ITEMS[I[k]].fuel = k.includes('log') ? 15 : 15;
}
ITEMS[I.stick].fuel = 5;
ITEMS[I.coal_block].fuel = 800;

export function maxStack(id) {
  return ITEMS[id] ? ITEMS[id].stack : 64;
}

// ---------- Recipes ----------
const LOGS = [B.oak_log, B.birch_log, B.spruce_log, B.jungle_log, B.acacia_log];
const PLANKS = [B.oak_planks, B.birch_planks, B.spruce_planks, B.jungle_planks];
const COBBLE = [B.cobblestone];

export const RECIPES = [];
function shaped(pattern, key, out, count = 1) {
  RECIPES.push({ type: 'shaped', pattern, key, out: typeof out === 'string' ? I[out] : out, count });
}
function shapeless(ings, out, count = 1) {
  RECIPES.push({ type: 'shapeless', ings, out: typeof out === 'string' ? I[out] : out, count });
}

shapeless([[B.oak_log]], B.oak_planks, 4);
shapeless([[B.birch_log]], B.birch_planks, 4);
shapeless([[B.spruce_log]], B.spruce_planks, 4);
shapeless([[B.jungle_log]], B.jungle_planks, 4);
shapeless([[B.acacia_log]], B.oak_planks, 4);
shaped(['#', '#'], { '#': PLANKS }, 'stick', 4);
shaped(['##', '##'], { '#': PLANKS }, B.crafting_table);
shaped(['###', '# #', '###'], { '#': COBBLE }, B.furnace);
shaped(['###', '# #', '###'], { '#': PLANKS }, B.chest);
shaped(['c', 's'], { c: [I.coal, I.charcoal], s: [I.stick] }, B.torch, 4);
shaped(['##', '##'], { '#': [B.sand] }, B.sandstone);
shaped(['##', '##'], { '#': [B.stone] }, B.stone_bricks, 4);
shaped(['##', '##'], { '#': [I.brick] }, B.bricks);
shaped(['##', '##'], { '#': [I.nether_brick] }, B.nether_bricks);
shaped(['##', '##'], { '#': [I.string] }, B.white_wool);
shaped(['##', '##'], { '#': [I.quartz] }, B.quartz_block);
shaped(['##', '##'], { '#': [I.glowstone_dust] }, B.glowstone);
shaped(['##', '##'], { '#': [I.clay_ball] }, B.clay);
shaped(['###', '###', '###'], { '#': [I.iron_ingot] }, B.iron_block);
shaped(['###', '###', '###'], { '#': [I.gold_ingot] }, B.gold_block);
shaped(['###', '###', '###'], { '#': [I.diamond] }, B.diamond_block);
shaped(['###', '###', '###'], { '#': [I.coal] }, B.coal_block);
shapeless([[B.iron_block]], 'iron_ingot', 9);
shapeless([[B.gold_block]], 'gold_ingot', 9);
shapeless([[B.diamond_block]], 'diamond', 9);
shapeless([[B.coal_block]], 'coal', 9);
shaped(['###', 'bbb', '###'], { '#': PLANKS, b: [I.book] }, B.bookshelf);
shaped(['xsx', 'sxs', 'xsx'], { x: [I.gunpowder], s: [B.sand, B.red_sand] }, B.tnt);
shaped(['# #', ' # '], { '#': [I.iron_ingot] }, 'bucket');
shaped(['i ', ' f'], { i: [I.iron_ingot], f: [I.flint] }, 'flint_and_steel');
shaped(['###'], { '#': [I.wheat] }, 'bread');
shapeless([[B.sugar_cane]], 'sugar');
shaped(['###'], { '#': [B.sugar_cane] }, 'paper', 3);
shapeless([[I.paper], [I.paper], [I.paper], [I.leather]], 'book');
shapeless([[I.flint], [I.stick], [I.feather]], 'arrow', 4);
shapeless([[B.white_wool], [B.poppy]], B.red_wool);
shapeless([[B.white_wool], [B.dandelion]], B.yellow_wool);
shapeless([[B.white_wool], [B.cactus]], B.green_wool);
shapeless([[B.white_wool], [I.coal]], B.black_wool);
shapeless([[B.white_wool], [B.blue_ice]], B.blue_wool);
shaped(['###', '###', '###'], { '#': [B.ice] }, B.blue_ice);
shapeless([[B.cobblestone], [B.tall_grass]], B.mossy_cobblestone);
shaped(['p', 't'], { p: [B.pumpkin], t: [B.torch] }, B.pumpkin);

const HEADS = {
  wooden: PLANKS,
  stone: COBBLE,
  iron: [I.iron_ingot],
  golden: [I.gold_ingot],
  diamond: [I.diamond],
};
for (const [mat, m] of Object.entries(HEADS)) {
  const key = { m, s: [I.stick] };
  shaped(['mmm', ' s ', ' s '], key, `${mat}_pickaxe`);
  shaped(['mm', 'ms', ' s'], key, `${mat}_axe`);
  shaped(['m', 's', 's'], key, `${mat}_shovel`);
  shaped(['m', 'm', 's'], key, `${mat}_sword`);
}

// ---------- Smelting ----------
export const SMELTING = {
  [B.iron_ore]: I.iron_ingot,
  [B.gold_ore]: I.gold_ingot,
  [B.sand]: B.glass,
  [B.red_sand]: B.glass,
  [B.cobblestone]: B.stone,
  [B.clay]: I.brick,
  [B.netherrack]: I.nether_brick,
  [B.cactus]: B.green_wool,
  [I.clay_ball]: I.brick,
  [I.porkchop]: I.cooked_porkchop,
  [I.beef]: I.cooked_beef,
  [I.chicken]: I.cooked_chicken,
  [I.mutton]: I.cooked_mutton,
  [B.diamond_ore]: I.diamond,
  [B.coal_ore]: I.coal,
  [B.nether_quartz_ore]: I.quartz,
};
for (const l of LOGS) SMELTING[l] = I.charcoal;

// Match a crafting grid (array of {id,count}|null, w x h) against recipes.
export function matchRecipe(grid, w, h) {
  // Find bounding box of non-empty cells
  let minX = w, minY = h, maxX = -1, maxY = -1;
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      if (grid[y * w + x]) {
        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
      }
    }
  if (maxX < 0) return null;
  const bw = maxX - minX + 1, bh = maxY - minY + 1;
  const cells = [];
  for (let y = minY; y <= maxY; y++) for (let x = minX; x <= maxX; x++) cells.push(grid[y * w + x]);

  for (const r of RECIPES) {
    if (r.type === 'shaped') {
      const ph = r.pattern.length, pw = Math.max(...r.pattern.map((p) => p.length));
      if (pw !== bw || ph !== bh) continue;
      for (const mirror of [false, true]) {
        let ok = true;
        for (let y = 0; y < ph && ok; y++)
          for (let x = 0; x < pw && ok; x++) {
            const row = r.pattern[y];
            const px = mirror ? pw - 1 - x : x;
            const ch = row[px] || ' ';
            const cell = cells[y * bw + x];
            if (ch === ' ') { if (cell) ok = false; }
            else if (!cell || !r.key[ch].includes(cell.id)) ok = false;
          }
        if (ok) return r;
      }
    } else {
      const items = cells.filter(Boolean).map((c) => c.id);
      if (items.length !== r.ings.length) continue;
      const used = new Array(items.length).fill(false);
      let ok = true;
      for (const ing of r.ings) {
        const idx = items.findIndex((id, i) => !used[i] && ing.includes(id));
        if (idx < 0) { ok = false; break; }
        used[idx] = true;
      }
      if (ok) return r;
    }
  }
  return null;
}

// Mining time in seconds for a block with the given held item (creative handled by caller).
export function breakTime(blockId, heldId) {
  const b = BLOCKS[blockId];
  if (!b || b.hardness < 0) return Infinity;
  if (b.hardness === 0) return 0.05;
  const held = ITEMS[heldId];
  const tool = held && held.tool;
  let speed = 1;
  const right = tool && tool.type === b.tool;
  if (right) speed = tool.speed;
  if (tool && tool.type === 'sword' && b.key.includes('leaves')) speed = 1.5;
  const canHarvest = b.level === 0 || (right && tool.tier >= b.level);
  const t = (b.hardness * (canHarvest ? 1.5 : 5)) / speed;
  return Math.max(0.05, t);
}

export function canHarvest(blockId, heldId) {
  const b = BLOCKS[blockId];
  if (!b) return false;
  if (b.level === 0) return true;
  const tool = ITEMS[heldId] && ITEMS[heldId].tool;
  return !!(tool && tool.type === b.tool && tool.tier >= b.level);
}

// Items shown in the creative inventory, grouped by tab.
export function creativeList() {
  const blocks = [];
  for (let i = 1; i < BLOCK_COUNT; i++) {
    const b = BLOCKS[i];
    if (!b || b.hidden || b.key === 'nether_portal') continue;
    blocks.push(i);
  }
  const items = [];
  for (let i = 256; i < ITEM_COUNT; i++) items.push(i);
  return { blocks, items };
}
