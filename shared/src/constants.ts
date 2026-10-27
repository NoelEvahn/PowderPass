export const TICK_RATE = 20;
export const TICK_DT = 1 / TICK_RATE;
export const MAX_PLAYERS_DEFAULT = 4;
export const FINAL_ROOM = 100;
export const LIBRARY_ROOM = 50;
export const SHOP_ROOM = 52;
export const COURTYARD_ROOM = 89;
export const GREENHOUSE_START = 90;
export const GREENHOUSE_END = 99;

export const PLAYER = {
  radius: 0.32,
  height: 1.7,
  crouchHeight: 1.05,
  eye: 1.58,
  crouchEye: 0.95,
  walkSpeed: 3.6,
  sprintSpeed: 5.8,
  crouchSpeed: 1.7,
  maxStamina: 100,
  staminaDrain: 22,
  staminaRegen: 14,
  maxHealth: 100,
  interactRange: 2.4,
};

export const WALL_T = 0.2;
export const DOOR_W = 1.3;
export const DOOR_H = 2.4;

/** Noise radii (meters) used by Figure's hearing. */
export const NOISE = { still: 0, crouch: 2.2, walk: 8, sprint: 18, interact: 7, book: 9, door: 12, breaker: 14 };

export const HIDE_LIMIT = { calm: 22, threat: 60, warn: 6 };
export const GLITCH_BEHIND = 4;
export const RECONNECT_WINDOW_MS = 60_000;
export const LOBBY_COUNTDOWN = 5;
