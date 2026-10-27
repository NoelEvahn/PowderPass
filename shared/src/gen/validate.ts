import type { RoomData, RunLayout } from '../types';
import { boxOverlap, distXZ } from '../math';
import { FINAL_ROOM, PLAYER } from '../constants';
import { floorAt } from '../world';
import { hides } from './encounters';

export interface ValidationIssue { room: number; msg: string }

const CELL = 0.2;

/** Grid flood-fill of walkable space inside one room (crouching allowed). Returns a reachability tester. */
export function roomReachability(room: RoomData) {
  const w = room.walkable.reduce((a, b) => ((b.maxX - b.minX) * (b.maxZ - b.minZ) > (a.maxX - a.minX) * (a.maxZ - a.minZ) ? b : a));
  const nx = Math.ceil((w.maxX - w.minX) / CELL), nz = Math.ceil((w.maxZ - w.minZ) / CELL);
  const ox = w.minX, oz = w.minZ;
  const blocked = new Uint8Array(nx * nz);
  const R = PLAYER.radius - 0.04;
  for (let ix = 0; ix < nx; ix++) {
    for (let iz = 0; iz < nz; iz++) {
      const x = ox + (ix + 0.5) * CELL, z = oz + (iz + 0.5) * CELL;
      const fy = floorAt(room, x, z);
      const y0 = fy + 0.38, y1 = fy + PLAYER.crouchHeight - 0.05;
      let bl = false;
      for (const b of room.colliders) {
        if (x + R > b.minX && x - R < b.maxX && z + R > b.minZ && z - R < b.maxZ && y1 > b.minY && y0 < b.maxY) { bl = true; break; }
      }
      blocked[ix * nz + iz] = bl ? 1 : 0;
    }
  }
  const seen = new Uint8Array(nx * nz);
  const cellOf = (x: number, z: number) => [Math.floor((x - ox) / CELL), Math.floor((z - oz) / CELL)];
  const flood = (sx: number, sz: number) => {
    const st: number[] = [];
    const [cx, cz] = cellOf(sx, sz);
    if (cx < 0 || cz < 0 || cx >= nx || cz >= nz) return;
    // nudge start to nearest free cell
    let best = -1, bd = 1e9;
    for (let dx = -3; dx <= 3; dx++) for (let dz = -3; dz <= 3; dz++) {
      const ix = cx + dx, iz = cz + dz;
      if (ix < 0 || iz < 0 || ix >= nx || iz >= nz || blocked[ix * nz + iz]) continue;
      const d = dx * dx + dz * dz;
      if (d < bd) { bd = d; best = ix * nz + iz; }
    }
    if (best < 0) return;
    st.push(best); seen[best] = 1;
    while (st.length) {
      const c = st.pop()!;
      const ix = Math.floor(c / nz), iz = c % nz;
      const nb = [[ix + 1, iz], [ix - 1, iz], [ix, iz + 1], [ix, iz - 1]];
      for (const [a, b] of nb) {
        if (a < 0 || b < 0 || a >= nx || b >= nz) continue;
        const k = a * nz + b;
        if (seen[k] || blocked[k]) continue;
        seen[k] = 1; st.push(k);
      }
    }
  };
  const entry = room.path[1] ?? room.path[0];
  flood(entry.x, entry.z);
  /** Is any reachable cell within `range` of (x,z)? */
  const near = (x: number, z: number, range: number) => {
    const [cx, cz] = cellOf(x, z);
    const n = Math.ceil(range / CELL);
    for (let dx = -n; dx <= n; dx++) for (let dz = -n; dz <= n; dz++) {
      const ix = cx + dx, iz = cz + dz;
      if (ix < 0 || iz < 0 || ix >= nx || iz >= nz) continue;
      if (!seen[ix * nz + iz]) continue;
      const px = ox + (ix + 0.5) * CELL, pz = oz + (iz + 0.5) * CELL;
      if (Math.hypot(px - x, pz - z) <= range) return true;
    }
    return false;
  };
  let reachableCells = 0;
  for (let k = 0; k < seen.length; k++) reachableCells += seen[k];
  return { near, reachableCells, blocked, seen, nx, nz, ox, oz, cell: CELL };
}

/** Per-room reachability: entry → exit, every interactable and every hiding spot exit. */
export function roomIssues(r: RoomData): string[] {
  const out: string[] = [];
  const reach = roomReachability(r);
  if (reach.reachableCells < 10) out.push('entry not reachable');
  const ex = r.exits.find((e) => e.real);
  if (ex || r.index === FINAL_ROOM) {
    const p = r.path[r.path.length - 2] ?? r.path[r.path.length - 1];
    if (p && !reach.near(p.x, p.z, 0.6)) out.push('exit not reachable');
  }
  for (const it of r.interactables) {
    if (it.kind === 'cabinetDoor') continue;
    const range = it.kind === 'elevator' ? 1.0 : PLAYER.interactRange - 0.3;
    if (!reach.near(it.pos.x, it.pos.z, range)) out.push(`${it.kind} ${it.id} unreachable`);
    if (it.kind === 'hide' && it.exitPos && !reach.near(it.exitPos.x, it.exitPos.z, 0.5)) out.push(`hide ${it.id} exit blocked`);
  }
  return out;
}

/** Full-run validation: placement, numbering, reachability, puzzles and encounter fairness. */
export function validateRun(run: RunLayout): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  const rooms = run.rooms;
  const bad = (room: number, msg: string) => issues.push({ room, msg });
  if (rooms.length !== FINAL_ROOM + 1) bad(-1, `expected ${FINAL_ROOM + 1} rooms, got ${rooms.length}`);
  for (let i = 0; i < rooms.length; i++) {
    const r = rooms[i];
    if (r.index !== i) bad(i, 'index mismatch');
    for (let j = 0; j < i; j++) if (boxOverlap(r.bounds, rooms[j].bounds, 0.01)) bad(i, `overlaps room ${j}`);
    const reals = r.exits.filter((e) => e.real);
    if (i < FINAL_ROOM) {
      if (reals.length !== 1) bad(i, `expected exactly one real exit, got ${reals.length}`);
      const e = reals[0];
      if (e) {
        if (e.number !== i + 1) bad(i, `exit numbered ${e.number}`);
        const n = rooms[i + 1];
        if (n && (Math.abs(n.origin.x - e.world.x) > 1e-6 || Math.abs(n.origin.z - e.world.z) > 1e-6 || Math.abs(n.origin.y - e.world.y) > 1e-6)) bad(i, 'next room not attached to exit');
        if (n && n.rot !== e.worldRot) bad(i, 'next room rotation mismatch');
        if (e.lock === 'key' && !r.interactables.some((it) => it.loot?.item === 'key' && it.loot.door === i + 1)) bad(i, 'locked door without key');
        if (e.lock === 'lever' && !r.interactables.some((it) => it.kind === 'lever')) bad(i, 'gate without lever');
      }
      for (const f of r.exits.filter((x) => !x.real)) if (f.number === i + 1 && f.fake === 'dupe') bad(i, 'dupe door shows correct number');
    } else if (reals.length !== 0) bad(i, 'final room must not have a door');
    for (const m of roomIssues(r)) bad(i, m);
    // encounter fairness
    for (const en of r.encounters) {
      if ((en.type === 'rush' || en.type === 'ambush') && hides(r) + hides(rooms[i - 1]) === 0) bad(i, `${en.type} without hiding spot`);
    }
    // path sanity
    for (let k = 1; k < r.path.length; k++) if (distXZ(r.path[k - 1], r.path[k]) > 60) bad(i, 'path jump');
  }
  // library puzzle
  const lib = rooms[50];
  if (lib) {
    const pz = lib.puzzle;
    if (!pz?.shapes || !pz.code || pz.code.length !== 5) bad(50, 'library puzzle missing');
    else {
      const books = lib.interactables.filter((x) => x.kind === 'book' && x.data?.shape !== undefined);
      const code = pz.shapes.map((s) => books.find((b) => b.data!.shape === s)?.data!.digit).join('');
      if (code !== pz.code) bad(50, `library code not derivable (${code} vs ${pz.code})`);
      if (!lib.interactables.some((x) => x.loot?.item === 'hintPaper')) bad(50, 'hint paper missing');
      if (!lib.interactables.some((x) => x.kind === 'padlock')) bad(50, 'padlock missing');
    }
  }
  const el = rooms[FINAL_ROOM];
  if (el) {
    const br = el.interactables.filter((x) => x.loot?.item === 'breaker').length;
    if (br !== 10) bad(FINAL_ROOM, `expected 10 breaker switches, got ${br}`);
    if (!el.interactables.some((x) => x.loot?.item === 'electricalKey')) bad(FINAL_ROOM, 'electrical key missing');
    if (!el.interactables.some((x) => x.kind === 'breakerBox')) bad(FINAL_ROOM, 'breaker box missing');
    if (!el.interactables.some((x) => x.kind === 'elevator')) bad(FINAL_ROOM, 'elevator missing');
    if (!el.puzzle?.breakerRounds?.length) bad(FINAL_ROOM, 'breaker rounds missing');
  }
  // chases
  for (const c of run.seekChases) {
    if (rooms[c.start]?.kind !== 'seekHall') bad(c.start, 'seek chase start is not a seek hall');
    if (rooms[c.end]?.kind !== 'seekFinale') bad(c.end, 'seek chase end is not a finale');
  }
  return issues;
}
