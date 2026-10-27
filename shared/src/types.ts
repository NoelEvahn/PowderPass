import type { Box, Vec3 } from './math';
import type { ItemType } from './items';

export type RoomKind =
  | 'reception' | 'hallway' | 'bedroom' | 'office' | 'storage' | 'dining' | 'intersection' | 'stairs'
  | 'basement' | 'lounge' | 'bathroom' | 'kitchen' | 'gallery' | 'darkRoom' | 'closetRoom'
  | 'library' | 'vestibule' | 'shop' | 'infirmary' | 'courtyard' | 'greenhouse' | 'electrical'
  | 'halt' | 'dupe' | 'seekHall' | 'seekChase' | 'seekCross' | 'seekFinale';

export type FloorMat = 'carpet' | 'wood' | 'tile' | 'stone' | 'grass' | 'concrete' | 'marble';

export interface RoomTheme {
  wallpaper: number;        // pattern index 0..5
  wallTint: number;         // hex
  floor: FloorMat;
  carpetTint: number;
  woodTone: number;         // hex for trim/furniture wood
  wainscot: boolean;
  ceilingBeams: boolean;
  lightColor: number;
  outdoor?: boolean;
  glass?: boolean;          // greenhouse glass walls/roof
}

export type PropType =
  | 'bed' | 'closet' | 'dresser' | 'desk' | 'nightstand' | 'bookshelf' | 'lamp' | 'floorLamp' | 'chandelier'
  | 'painting' | 'fireplace' | 'table' | 'diningTable' | 'chair' | 'sofa' | 'armchair' | 'plant' | 'crate'
  | 'barrel' | 'pipe' | 'vent' | 'rug' | 'wallLight' | 'ceilingLight' | 'piano' | 'clock' | 'shelf' | 'cabinet'
  | 'sink' | 'toilet' | 'bathtub' | 'counter' | 'stairs' | 'railing' | 'pillar' | 'fountain' | 'hedge' | 'tree'
  | 'bench' | 'planter' | 'statue' | 'lever' | 'gate' | 'breakerBox' | 'elevator' | 'receptionDesk'
  | 'boxStack' | 'shopCounter' | 'medCabinet' | 'gurney' | 'curtain' | 'locker' | 'mirror' | 'stove'
  | 'fridge' | 'pallet' | 'generator' | 'electricPanel' | 'cables' | 'debris' | 'fallenShelf' | 'hands'
  | 'seekEyes' | 'fire' | 'guidingLight' | 'jeff' | 'padlockDesk' | 'vines' | 'glassPanel' | 'snare'
  | 'gazebo' | 'lamppost' | 'trellis' | 'wheelbarrow' | 'woodPile' | 'pot' | 'bookPile' | 'skeleton'
  | 'cart' | 'ventGrate' | 'signpost' | 'bigShelf' | 'catwalk';

export interface PropData {
  id: string;
  type: PropType;
  /** local position in room frame (y = floor-relative) */
  x: number; y: number; z: number;
  /** local rotation 0..3 */
  rot: number;
  variant: number;
  /** optional scale / size parameters (meaning depends on type) */
  sx?: number; sy?: number; sz?: number;
  /** extra numeric data (e.g. stair rise) */
  data?: number;
}

export type InteractKind =
  | 'drawer' | 'hide' | 'pickup' | 'lever' | 'padlock' | 'breakerBox' | 'shop' | 'elevator' | 'fakeDoor' | 'book' | 'cabinetDoor';

export interface Loot { gold?: number; item?: ItemType; door?: number; timothy?: boolean; }

export interface Interactable {
  id: string;
  kind: InteractKind;
  /** world position used for range checks and UI */
  pos: Vec3;
  propId?: string;
  slot?: number;
  loot?: Loot;
  hide?: 'closet' | 'bed' | 'locker';
  /** for hide spots: world exit position + facing (yaw) */
  exitPos?: Vec3;
  /** for books: shape & digit; for fakeDoor: exit index */
  data?: { shape?: number; digit?: number; exit?: number; lockedBy?: 'electricalKey'; jack?: boolean };
  /** Locked containers need lockpick */
  locked?: boolean;
}

export type ExitLock = 'none' | 'key' | 'lever' | 'code' | 'power' | 'halt' | 'seek';

export interface ExitData {
  /** local position on the joint plane, floor level */
  lx: number; ly: number; lz: number;
  /** local direction offset 0 (north), 1 (+x), 3 (-x) */
  dir: number;
  world: Vec3;
  worldRot: number;
  real: boolean;
  /** number displayed on the door plate */
  number: number;
  lock: ExitLock;
  /** fake door behavior */
  fake?: 'dupe' | 'blocked';
  /** door style */
  style: 'wood' | 'double' | 'gate' | 'metal' | 'glass' | 'iron';
}

export interface LightData {
  lx: number; ly: number; lz: number;
  world: Vec3;
  color: number;
  intensity: number;
  range: number;
  kind: 'chandelier' | 'ceiling' | 'wall' | 'lamp' | 'fire' | 'moon' | 'shop' | 'emergency' | 'neon' | 'guide';
  flicker?: boolean;
  /** shadow casting candidate */
  shadow?: boolean;
}

export type EncounterType = 'rush' | 'ambush' | 'eyes' | 'halt' | 'seek' | 'figure' | 'dupe' | 'screechZone' | 'snare' | 'shadow' | 'jackRoom';

export interface Encounter {
  type: EncounterType;
  /** seconds of delay after trigger */
  delay?: number;
  /** ambush rebounds */
  rebounds?: number;
  /** seek: chase id (1 or 2) */
  chase?: number;
}

export interface RoomData {
  index: number;
  kind: RoomKind;
  rot: number;
  origin: Vec3;
  xMin: number; xMax: number;
  /** interior depth along local z (interior spans z in [WALL_T, WALL_T+depth]) */
  depth: number;
  height: number;
  /** vertical offset of the exit relative to origin (stairs) */
  rise: number;
  dark: boolean;
  theme: RoomTheme;
  exits: ExitData[];
  realExit: number;
  props: PropData[];
  interactables: Interactable[];
  lights: LightData[];
  /** world-space solid colliders (walls, floor, furniture) */
  colliders: Box[];
  /** colliders that can be crouched under (gap below) – blocked only when standing */
  crouchColliders: Box[];
  /** world volumes where a player's feet may legally be */
  walkable: Box[];
  /** world footprint box incl. walls (for overlap checks/visibility) */
  bounds: Box;
  /** world waypoints from entry to the real exit */
  path: Vec3[];
  encounters: Encounter[];
  /** decorative/narrative flags */
  tags: string[];
  /** world openings (door holes) for client wall meshing: local */
  openings: { wall: 'n' | 's' | 'e' | 'w'; center: number; width: number; height: number; y?: number }[];
  /** library puzzle data */
  puzzle?: PuzzleData;
}

export interface PuzzleData {
  /** library: 5 shapes in order and their digits */
  shapes?: number[];
  code?: string;
  /** electrical: breaker rounds (each round: set of switch numbers that must be ON) */
  breakerRounds?: number[][];
}

export interface RunLayout {
  seed: number;
  rooms: RoomData[];
  seekChases: { start: number; end: number }[];
  version: number;
}

export const SHAPES = ['circle', 'triangle', 'square', 'star', 'diamond', 'cross', 'moon', 'heart'] as const;
