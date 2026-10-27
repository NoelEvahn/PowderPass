import type { InventoryItem, ItemType } from '@doors/shared';
import { INVENTORY_SLOTS, ITEMS } from '@doors/shared';

let uidCounter = 0;
export const newUid = () => `it${(++uidCounter).toString(36)}${Math.floor(Math.random() * 1e6).toString(36)}`;

export function makeItem(type: ItemType, extra: Partial<InventoryItem> = {}): InventoryItem {
  const d = ITEMS[type];
  const it: InventoryItem = { uid: newUid(), type, count: 1 };
  if (d.uses !== undefined) it.uses = d.uses;
  if (d.charge) { it.charge = 1; it.on = false; }
  return { ...it, ...extra };
}

/** Add an item to a slot array. Stackables merge. Returns false if no room. */
export function addItem(inv: (InventoryItem | null)[], item: InventoryItem): boolean {
  const d = ITEMS[item.type];
  if (d.stack) {
    const ex = inv.find((x) => x && x.type === item.type);
    if (ex) { ex.count += item.count; return true; }
  }
  if (item.type === 'lockpick') {
    const ex = inv.find((x) => x && x.type === 'lockpick');
    if (ex) { ex.uses = (ex.uses ?? 0) + (item.uses ?? 0); return true; }
  }
  const i = inv.findIndex((x) => x === null);
  if (i < 0 || i >= INVENTORY_SLOTS) return false;
  inv[i] = item;
  return true;
}

export function findItem(inv: (InventoryItem | null)[], pred: (i: InventoryItem) => boolean): number {
  return inv.findIndex((x) => !!x && pred(x));
}

/** Consume one unit (count for stackables, uses for limited items). Removes when depleted. */
export function consumeOne(inv: (InventoryItem | null)[], slot: number) {
  const it = inv[slot];
  if (!it) return;
  if (it.uses !== undefined) {
    it.uses--;
    if (it.uses <= 0) inv[slot] = null;
    return;
  }
  it.count--;
  if (it.count <= 0) inv[slot] = null;
}
