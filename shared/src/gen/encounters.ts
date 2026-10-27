import type { Rng } from '../rng';
import type { RoomData } from '../types';
import type { RunPlan } from './plan';

const QUIET = new Set(['reception', 'library', 'vestibule', 'shop', 'courtyard', 'halt', 'dupe', 'electrical', 'seekHall', 'seekChase', 'seekCross', 'seekFinale', 'infirmary']);

export const hides = (r: RoomData | undefined) => (r ? r.interactables.filter((i) => i.kind === 'hide' && !i.data?.jack).length : 0);

/**
 * Decide which entity encounters are eligible for each room. Rush/Ambush only where a hiding spot exists in
 * the triggering room or the one before it; major entities are spaced apart and never overlap a Seek chase.
 */
export function scheduleEncounters(rooms: RoomData[], plan: RunPlan, rng: Rng) {
  let lastMajor = -10;
  const nearChase = (i: number) => plan.chases.some((c) => i >= c.start - 3 && i <= c.end + 1);
  for (const c of plan.chases) rooms[c.start].encounters.push({ type: 'seek', chase: plan.chases.indexOf(c) + 1 });
  for (const r of rooms) {
    const i = r.index;
    if (r.kind === 'library' || r.kind === 'electrical') r.encounters.push({ type: 'figure' });
    if (r.kind === 'halt') r.encounters.push({ type: 'halt' });
    if (r.kind === 'dupe') r.encounters.push({ type: 'dupe' });
    if (r.dark) r.encounters.push({ type: 'screechZone' });
    if (r.props.some((p) => p.type === 'snare')) r.encounters.push({ type: 'snare' });
    if (i < 6 || QUIET.has(r.kind) || nearChase(i) || i === 51 || i === 52) continue;
    const canHide = hides(r) > 0 || hides(rooms[i - 1]) > 0;
    const green = r.kind === 'greenhouse';
    if (canHide && i - lastMajor >= 3) {
      const pr = green ? 0.2 : i < 20 ? 0.09 : 0.12;
      const pa = i >= 25 ? (green ? 0.12 : 0.07) : 0;
      const x = rng.next();
      if (x < pr) { r.encounters.push({ type: 'rush', delay: rng.range(3.5, 6.5) }); lastMajor = i; continue; }
      if (x < pr + pa) { r.encounters.push({ type: 'ambush', delay: rng.range(3, 5.5), rebounds: rng.int(2, i > 70 ? 6 : 4) }); lastMajor = i; continue; }
    }
    if (i >= 10 && i - lastMajor >= 2 && !green && rng.chance(0.07)) { r.encounters.push({ type: 'eyes' }); lastMajor = i; continue; }
    if (rng.chance(0.04)) r.encounters.push({ type: 'shadow' });
    else if (i > 12 && rng.chance(0.015)) r.encounters.push({ type: 'jackRoom' });
  }
  // guarantee an introductory Rush before the first chase
  if (!rooms.some((r) => r.index < plan.chases[0].start - 3 && r.encounters.some((e) => e.type === 'rush'))) {
    for (let i = 8; i < plan.chases[0].start - 3; i++) {
      const r = rooms[i];
      if (!QUIET.has(r.kind) && (hides(r) > 0 || hides(rooms[i - 1]) > 0) && !r.encounters.some((e) => e.type === 'eyes')) {
        r.encounters.push({ type: 'rush', delay: 5 });
        break;
      }
    }
  }
}
