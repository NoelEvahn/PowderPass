import type { Rng } from '../rng';
import type { RoomKind } from '../types';
import { COURTYARD_ROOM, FINAL_ROOM, GREENHOUSE_END, GREENHOUSE_START, LIBRARY_ROOM, SHOP_ROOM } from '../constants';

export interface RunPlan {
  kinds: RoomKind[];
  chases: { start: number; end: number }[];
  locks: ('none' | 'key' | 'lever')[];
  dark: boolean[];
}

const NORMAL: { k: RoomKind; w: number; min: number }[] = [
  { k: 'hallway', w: 14, min: 1 },
  { k: 'bedroom', w: 16, min: 1 },
  { k: 'office', w: 10, min: 3 },
  { k: 'storage', w: 7, min: 5 },
  { k: 'dining', w: 6, min: 8 },
  { k: 'intersection', w: 6, min: 6 },
  { k: 'stairs', w: 5, min: 4 },
  { k: 'lounge', w: 8, min: 2 },
  { k: 'bathroom', w: 5, min: 6 },
  { k: 'kitchen', w: 4, min: 12 },
  { k: 'gallery', w: 5, min: 10 },
  { k: 'closetRoom', w: 8, min: 3 },
  { k: 'basement', w: 4, min: 56 },
];

/** Decide what kind of room each door leads to. Fixed landmarks are preserved, the journey between varies. */
export function planRun(rng: Rng): RunPlan {
  const kinds: RoomKind[] = new Array(FINAL_ROOM + 1).fill('hallway');
  const fixed = new Set<number>();
  const set = (i: number, k: RoomKind) => { kinds[i] = k; fixed.add(i); };

  set(0, 'reception');
  set(LIBRARY_ROOM, 'library');
  set(LIBRARY_ROOM + 1, 'vestibule');
  set(SHOP_ROOM, 'shop');
  set(COURTYARD_ROOM, 'courtyard');
  for (let i = GREENHOUSE_START; i <= GREENHOUSE_END; i++) set(i, 'greenhouse');
  set(FINAL_ROOM, 'electrical');
  set(rng.int(55, 68), 'infirmary');

  // Seek chases: 7 rooms each. First within 32–43, second within 72–81.
  const chases: { start: number; end: number }[] = [];
  for (const [lo, hi] of [[32, 43], [72, 81]] as const) {
    let s = rng.int(lo, hi);
    while ([...Array(7).keys()].some((o) => fixed.has(s + o))) s--;
    set(s, 'seekHall');
    set(s + 1, 'seekChase');
    set(s + 2, 'seekChase');
    set(s + 3, 'seekCross');
    set(s + 4, 'seekChase');
    set(s + 5, 'seekCross');
    set(s + 6, 'seekFinale');
    chases.push({ start: s, end: s + 6 });
  }

  // Halt corridors (0–2) and Dupe rooms (1–2) in free slots away from landmarks.
  const free = (i: number) => !fixed.has(i) && !fixed.has(i - 1) && !fixed.has(i + 1);
  const placeSpecial = (k: RoomKind, lo: number, hi: number) => {
    for (let a = 0; a < 40; a++) {
      const i = rng.int(lo, hi);
      if (free(i)) { set(i, k); return; }
    }
  };
  placeSpecial('halt', 26, 48);
  if (rng.chance(0.7)) placeSpecial('halt', 53, 70);
  placeSpecial('dupe', 14, 30);
  if (rng.chance(0.8)) placeSpecial('dupe', 56, 86);

  let prev: RoomKind = 'reception';
  for (let i = 1; i < FINAL_ROOM; i++) {
    if (fixed.has(i)) { prev = kinds[i]; continue; }
    const opts = NORMAL.filter((n) => n.min <= i && !(n.k === prev && n.k !== 'hallway')).map((n) => ({ item: n.k, w: n.w }));
    kinds[i] = rng.weighted(opts);
    prev = kinds[i];
  }
  // Early rooms are gentle: door 1 is always a hallway, door 2 a bedroom (introductory loot).
  kinds[1] = 'hallway';
  kinds[2] = 'bedroom';

  const lockable = (i: number) => i >= 3 && !fixed.has(i) && kinds[i] !== 'stairs';
  const locks: RunPlan['locks'] = kinds.map(() => 'none');
  for (let i = 3; i < FINAL_ROOM; i++) {
    if (!lockable(i) || locks[i - 1] !== 'none') continue;
    const r = rng.next();
    if (r < 0.2) locks[i] = 'key';
    else if (r < 0.27 && i > 10) locks[i] = 'lever';
  }

  const dark = kinds.map((k, i) => {
    if (k === 'greenhouse') return true;
    if (fixed.has(i) || i < 8) return false;
    if (k === 'basement') return true;
    const p = i < 30 ? 0.06 : i < 60 ? 0.12 : 0.2;
    return rng.chance(p);
  });
  return { kinds, chases, locks, dark };
}
