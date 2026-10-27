import type { InventoryItem, ItemType } from './items';
import type { Vec3 } from './math';

export const PROTOCOL_VERSION = 4;

export interface LobbyPlayer { id: string; name: string; ready: boolean; host: boolean; connected: boolean }
export interface LobbySummary { code: string; players: number; max: number; state: 'waiting' | 'countdown' | 'playing'; name: string }

export type EntityKind =
  | 'rush' | 'ambush' | 'seek' | 'figure' | 'screech' | 'eyes' | 'halt' | 'dupe' | 'hide' | 'timothy' | 'jack' | 'shadow' | 'glitch';

export interface EntitySnap {
  id: string;
  kind: EntityKind;
  x: number; y: number; z: number;
  yaw: number;
  state: string;
  /** optional extra per-kind data (e.g. target player, room) */
  room?: number;
  speed?: number;
}

export interface PlayerSnap {
  id: string;
  name: string;
  x: number; y: number; z: number;
  yaw: number; pitch: number;
  crouch: boolean;
  hidden: string | null;
  hp: number;
  dead: boolean;
  room: number;
  equip: ItemType | null;
  light: boolean;
  connected: boolean;
  /** last processed input sequence (for prediction reconciliation) */
  ack: number;
}

export interface SelfState {
  hp: number;
  gold: number;
  inventory: (InventoryItem | null)[];
  equipped: number;
  stamina: number;
  boost: number;
  dead: boolean;
  revives: number;
  hidden: string | null;
  snared: number;
  books: number[];
  hasPaper: boolean;
}

export interface WorldSync {
  openDoors: number[];
  unlockedDoors: number[];
  openFakeDoors: string[];
  openContainers: string[];
  emptiedContainers: string[];
  takenPickups: string[];
  usedLevers: string[];
  brokenLights: number[];
  flickerRooms: number[];
  hideOccupants: Record<string, string>;
  booksFound: { shape: number; digit: number }[];
  bookIds: string[];
  paperFound: boolean;
  libraryUnlocked: boolean;
  hvDoorOpen: boolean;
  breakersInserted: number;
  breakerRound: number;
  breakerSwitches: boolean[];
  powerOn: boolean;
  furthestDoor: number;
  haltDone: number[];
  fallenChandeliers: string[];
  droppedItems: { id: string; item: InventoryItem; pos: Vec3 }[];
  jackDoors: number[];
}

export type GameEvent =
  | { e: 'door'; n: number; by: string }
  | { e: 'doorUnlock'; n: number; by: string }
  | { e: 'doorSlam'; n: number }
  | { e: 'fakeDoor'; id: string; by: string }
  | { e: 'container'; id: string; open: boolean; by: string }
  | { e: 'loot'; id: string; by: string; gold?: number; item?: ItemType }
  | { e: 'pickup'; id: string; by: string; item: ItemType }
  | { e: 'drop'; id: string; item: InventoryItem; pos: Vec3 }
  | { e: 'lever'; id: string; by: string; door: number }
  | { e: 'hide'; id: string; by: string; inside: boolean }
  | { e: 'lights'; rooms: number[]; mode: 'flicker' | 'broken' | 'on' | 'green' | 'red' | 'off' }
  | { e: 'damage'; id: string; amount: number; cause: string; hp: number }
  | { e: 'heal'; id: string; hp: number }
  | { e: 'death'; id: string; cause: string }
  | { e: 'revive'; id: string; pos: Vec3 }
  | { e: 'entity'; kind: EntityKind; phase: string; id: string; room?: number; dir?: number; data?: number }
  | { e: 'warn'; kind: string; room?: number; target?: string; dir?: number }
  | { e: 'book'; id: string; by: string; shape?: number; digit?: number }
  | { e: 'paper'; by: string }
  | { e: 'padlock'; ok: boolean; by: string }
  | { e: 'hvDoor'; by: string }
  | { e: 'breaker'; inserted: number; round: number; switches: boolean[]; lit: number[]; by?: string; complete?: boolean }
  | { e: 'power' }
  | { e: 'heartbeat'; target: string; beats: number[]; window: number }
  | { e: 'heartbeatResult'; target: string; ok: boolean }
  | { e: 'snare'; id: string; target: string }
  | { e: 'chandelier'; id: string }
  | { e: 'glitch'; target: string; pos: Vec3 }
  | { e: 'scare'; kind: 'timothy' | 'jack' | 'shadow' | 'hide'; target?: string; id?: string; room?: number }
  | { e: 'purchase'; by: string; item: ItemType; ok: boolean }
  | { e: 'shop'; target: string; open: boolean }
  | { e: 'chase'; phase: 'start' | 'end'; chase: number }
  | { e: 'elevator'; phase: 'boarding' | 'closing'; countdown: number }
  | { e: 'notice'; text: string; target?: string }
  | { e: 'crucifix'; by: string; kind: EntityKind; ok: boolean };

export type C2S =
  | { t: 'hello'; name: string; profile: string; token?: string; v: number }
  | { t: 'listLobbies' }
  | { t: 'createLobby'; private: boolean }
  | { t: 'joinLobby'; code: string }
  | { t: 'quickJoin' }
  | { t: 'leaveLobby' }
  | { t: 'ready'; ready: boolean }
  | { t: 'start' }
  | { t: 'input'; seq: number; x: number; y: number; z: number; yaw: number; pitch: number; crouch: boolean; sprint: boolean; moving: boolean }
  | { t: 'interact'; id: string }
  | { t: 'unhide' }
  | { t: 'equip'; slot: number }
  | { t: 'use'; slot: number }
  | { t: 'drop'; slot: number }
  | { t: 'toggleLight' }
  | { t: 'code'; code: string }
  | { t: 'breakerToggle'; n: number }
  | { t: 'buy'; item: ItemType }
  | { t: 'closeShop' }
  | { t: 'heartbeat'; idx: number }
  | { t: 'revive' }
  | { t: 'ping'; c: number };

export type S2C =
  | { t: 'welcome'; id: string; token: string; knobs: number; stats: { runs: number; best: number; wins: number } }
  | { t: 'lobbyList'; lobbies: LobbySummary[] }
  | { t: 'lobby'; code: string; private: boolean; players: LobbyPlayer[]; countdown: number | null; max: number; you: string }
  | { t: 'leftLobby' }
  | { t: 'start'; seed: number; version: number; players: { id: string; name: string }[]; spawn: Vec3[] ; you: string }
  | { t: 'snap'; time: number; players: PlayerSnap[]; entities: EntitySnap[] }
  | { t: 'self'; s: SelfState }
  | { t: 'ev'; list: GameEvent[] }
  | { t: 'sync'; w: WorldSync }
  | { t: 'correct'; x: number; y: number; z: number; seq: number }
  | { t: 'error'; msg: string }
  | { t: 'end'; result: 'win' | 'lose'; door: number; stats: { id: string; name: string; door: number; gold: number; deaths: number; cause: string | null }[]; knobs: number }
  | { t: 'pong'; c: number; s: number };
