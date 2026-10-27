import type { EntityKind, EntitySnap, GameEvent, InventoryItem, ItemType, RoomData, RunLayout, Vec3, WorldQuery, Rng } from '@doors/shared';

export interface Peer {
  id: string;
  send(msg: unknown): void;
}

export interface PlayerState {
  id: string;
  name: string;
  peer: Peer | null;
  pos: Vec3;
  lastValid: Vec3;
  yaw: number;
  pitch: number;
  crouch: boolean;
  sprint: boolean;
  moving: boolean;
  room: number;
  roomEnteredAt: number;
  hp: number;
  dead: boolean;
  deadAt: number;
  cause: string | null;
  gold: number;
  inv: (InventoryItem | null)[];
  equipped: number;
  stamina: number;
  boost: number;
  hidden: string | null;
  hiddenTime: number;
  hideWarned: boolean;
  snared: number;
  snareCooldown: number;
  lastSeq: number;
  lastInputAt: number;
  connected: boolean;
  disconnectedAt: number;
  revives: number;
  noise: number;
  behindSince: number;
  screechCooldown: number;
  cooldowns: Map<string, number>;
  shopOpen: boolean;
  deaths: number;
  furthest: number;
  selfDirty: boolean;
  violations: number;
  inElevator: boolean;
  slowUntil: number;
}

export interface Entity {
  id: string;
  kind: EntityKind;
  pos: Vec3;
  yaw: number;
  state: string;
  done: boolean;
  room?: number;
  update(dt: number): void;
  snap(): EntitySnap;
  /** Called when a crucifix-holding player confronts the entity; returns true if it was affected. */
  crucifix?(p: PlayerState): boolean;
}

export interface Game {
  readonly layout: RunLayout;
  readonly rooms: RoomData[];
  readonly world: WorldQuery;
  readonly rng: Rng;
  time: number;
  furthestDoor: number;
  players(): PlayerState[];
  alive(): PlayerState[];
  emit(ev: GameEvent): void;
  damage(p: PlayerState, amount: number, cause: string): void;
  setLights(rooms: number[], mode: 'flicker' | 'broken' | 'on' | 'green' | 'red' | 'off'): void;
  eye(p: PlayerState): Vec3;
  /** Consume an equipped crucifix if held. */
  useCrucifix(p: PlayerState, kind: EntityKind): boolean;
  hasEquipped(p: PlayerState, t: ItemType): boolean;
  threatActive(): boolean;
  entities: Map<string, Entity>;
  nextId(prefix: string): string;
  isDoorOpen(n: number): boolean;
  openDoor(n: number, by: string): void;
  closeDoor(n: number): void;
}
