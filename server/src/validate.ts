import type { C2S } from '@doors/shared';
import { ITEMS } from '@doors/shared';

const num = (v: unknown, lim = 1e5): v is number => typeof v === 'number' && Number.isFinite(v) && Math.abs(v) < lim;
const str = (v: unknown, max = 64): v is string => typeof v === 'string' && v.length <= max;
const bool = (v: unknown): v is boolean => typeof v === 'boolean';
const int = (v: unknown, lo: number, hi: number): v is number => Number.isInteger(v) && (v as number) >= lo && (v as number) <= hi;

/** Strictly validate an untrusted client message. Returns null when malformed. */
export function parseC2S(raw: string): C2S | null {
  if (raw.length > 4096) return null;
  let m: any;
  try { m = JSON.parse(raw); } catch { return null; }
  if (!m || typeof m !== 'object' || typeof m.t !== 'string') return null;
  switch (m.t) {
    case 'hello': return str(m.name, 24) && str(m.profile, 64) && (m.token === undefined || str(m.token, 128)) && num(m.v) ? m : null;
    case 'listLobbies': case 'quickJoin': case 'leaveLobby': case 'start': case 'unhide': case 'toggleLight': case 'revive': case 'closeShop': return m;
    case 'createLobby': return bool(m.private) ? m : null;
    case 'joinLobby': return str(m.code, 8) ? m : null;
    case 'ready': return bool(m.ready) ? m : null;
    case 'input': return int(m.seq, 0, 2 ** 31) && num(m.x) && num(m.y) && num(m.z) && num(m.yaw, 1e3) && num(m.pitch, 10) && bool(m.crouch) && bool(m.sprint) && bool(m.moving) ? m : null;
    case 'interact': return str(m.id, 48) ? m : null;
    case 'equip': case 'use': case 'drop': return int(m.slot, -1, 5) ? m : null;
    case 'code': return str(m.code, 8) && /^\d{0,8}$/.test(m.code) ? m : null;
    case 'breakerToggle': return int(m.n, 1, 10) ? m : null;
    case 'buy': return str(m.item, 24) && m.item in ITEMS ? m : null;
    case 'heartbeat': return int(m.idx, 0, 20) ? m : null;
    case 'ping': return num(m.c, 1e15) ? m : null;
  }
  return null;
}
