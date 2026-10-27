export type ItemType =
  | 'flashlight' | 'lighter' | 'battery' | 'lockpick' | 'vitamins' | 'bandage' | 'crucifix'
  | 'key' | 'hintPaper' | 'electricalKey' | 'breaker' | 'skeletonKey';

export interface ItemDef {
  type: ItemType;
  name: string;
  /** Max uses / charge. undefined = unlimited/non-consumable. */
  uses?: number;
  /** Charge-based (0..1) items drain over time while active. */
  charge?: boolean;
  stack?: boolean;
  price?: number;
  desc: string;
}

export const ITEMS: Record<ItemType, ItemDef> = {
  flashlight: { type: 'flashlight', name: 'Flashlight', charge: true, price: 100, desc: 'Bright beam. Drains battery.' },
  lighter: { type: 'lighter', name: 'Lighter', charge: true, price: 60, desc: 'Weak flickering light. Limited fuel.' },
  battery: { type: 'battery', name: 'Batteries', stack: true, price: 40, desc: 'Recharges the flashlight.' },
  lockpick: { type: 'lockpick', name: 'Lockpicks', uses: 3, price: 75, desc: 'Opens locked doors, drawers and gates.' },
  vitamins: { type: 'vitamins', name: 'Vitamins', stack: true, price: 60, desc: 'Temporary speed and stamina boost.' },
  bandage: { type: 'bandage', name: 'Bandage', stack: true, price: 50, desc: 'Restores 35 health.' },
  crucifix: { type: 'crucifix', name: 'Crucifix', uses: 1, price: 250, desc: 'Banishes Rush, Ambush, Eyes and Screech; stuns Seek and Figure.' },
  key: { type: 'key', name: 'Room Key', desc: 'Opens a numbered locked door.' },
  hintPaper: { type: 'hintPaper', name: 'Library Hint Paper', desc: 'Shapes in order. Books reveal their digits.' },
  electricalKey: { type: 'electricalKey', name: 'Electrical Room Key', desc: 'Opens the high-voltage room.' },
  breaker: { type: 'breaker', name: 'Breaker Switch', stack: true, desc: 'Insert into the breaker box.' },
  skeletonKey: { type: 'skeletonKey', name: 'Skeleton Key', uses: 5, price: 400, desc: 'Opens any lock.' },
};

export const SHOP_STOCK: ItemType[] = ['flashlight', 'lighter', 'battery', 'lockpick', 'vitamins', 'bandage', 'crucifix', 'skeletonKey'];

export const INVENTORY_SLOTS = 6;

export interface InventoryItem {
  uid: string;
  type: ItemType;
  /** stack count for stackable items */
  count: number;
  /** remaining uses for use-limited items */
  uses?: number;
  /** 0..1 charge for charge items */
  charge?: number;
  /** door number this key unlocks */
  door?: number;
  /** on/off for light items */
  on?: boolean;
}
