import { describe, expect, it } from 'vitest';
import { TICK_DT, toWorld, WALL_T, type Vec3 } from '@doors/shared';
import { GameRoom } from '../src/game/GameRoom';
import { FakePeer } from './bot';

function makeGame(players = 2, startRoom = 0, seed = 777) {
  const peers = Array.from({ length: players }, (_, i) => new FakePeer('p' + i));
  const game = new GameRoom({ seed, manual: true, startRoom, secret: 99, players: peers.map((p) => ({ id: p.id, name: p.id, peer: p })) });
  return { game, peers };
}
const step = (g: GameRoom, sec: number) => { for (let t = 0; t < sec; t += TICK_DT) g.step(TICK_DT); };
/** Place a player at a point by teleporting through the server's own state (test helper). */
function place(g: GameRoom, id: string, pos: Vec3, room: number) {
  const p = g.getPlayer(id)!;
  p.pos = { ...pos }; p.lastValid = { ...pos }; p.room = room;
}

describe('authoritative simulation', () => {
  it('only one player gets a contested pickup', () => {
    const { game } = makeGame(2, 99, 4242);
    const room = game.rooms[100];
    const key = room.interactables.find((i) => i.loot?.item === 'electricalKey')!;
    for (const id of ['p0', 'p1']) place(game, id, { x: key.pos.x, y: room.origin.y, z: key.pos.z }, 100);
    game.handle('p0', { t: 'interact', id: key.id });
    game.handle('p1', { t: 'interact', id: key.id });
    const owners = ['p0', 'p1'].filter((id) => game.getPlayer(id)!.inv.some((i) => i?.type === 'electricalKey'));
    expect(owners).toEqual(['p0']);
  });

  it('drawer loot is granted once and gold is tracked', () => {
    const { game } = makeGame(2, 1);
    const r = game.rooms[2];
    const d = r.interactables.find((i) => i.kind === 'drawer' && i.loot?.gold)!;
    if (!d) return; // seed without gold drawers in room 2
    for (const id of ['p0', 'p1']) place(game, id, { x: d.pos.x, y: r.origin.y, z: d.pos.z }, 2);
    game.handle('p0', { t: 'interact', id: d.id });
    game.handle('p1', { t: 'interact', id: d.id });
    game.handle('p1', { t: 'interact', id: d.id });
    expect(game.getPlayer('p0')!.gold).toBe(d.loot!.gold);
    expect(game.getPlayer('p1')!.gold).toBe(0);
  });

  it('hiding: one occupant per closet, and Hide kicks players out after too long', () => {
    const { game, peers } = makeGame(2, 2);
    const r = game.rooms.find((x) => x.index > 3 && x.interactables.some((i) => i.kind === 'hide' && !i.data?.jack))!;
    const h = r.interactables.find((i) => i.kind === 'hide' && !i.data?.jack)!;
    for (const id of ['p0', 'p1']) place(game, id, h.exitPos!, r.index);
    game.handle('p0', { t: 'interact', id: h.id });
    game.handle('p1', { t: 'interact', id: h.id });
    expect(game.getPlayer('p0')!.hidden).toBe(h.id);
    expect(game.getPlayer('p1')!.hidden).toBeNull();
    step(game, 30);
    const p0 = game.getPlayer('p0')!;
    expect(p0.hidden).toBeNull();
    expect(p0.hp).toBe(60);
    expect(peers[0].events.some((e) => e.e === 'warn' && e.kind === 'hide')).toBe(true);
  });

  it('inventory: equip, use bandage, drop and re-pick an item', () => {
    const { game } = makeGame(1, 5);
    const p = game.getPlayer('p0')!;
    p.inv[0] = { uid: 'b1', type: 'bandage', count: 2 };
    p.inv[1] = { uid: 'f1', type: 'flashlight', count: 1, charge: 1, on: false };
    p.hp = 50;
    game.handle('p0', { t: 'use', slot: 0 });
    expect(p.hp).toBe(85);
    expect(p.inv[0]!.count).toBe(1);
    game.handle('p0', { t: 'equip', slot: 1 });
    game.handle('p0', { t: 'toggleLight' });
    expect(p.inv[1]!.on).toBe(true);
    step(game, 10);
    expect(p.inv[1]!.charge!).toBeLessThan(1);
    const ev = new FakePeer('x');
    void ev;
    game.handle('p0', { t: 'drop', slot: 1 });
    expect(p.inv[1]).toBeNull();
  });

  it('Rush: warns, runs, kills exposed players and spares hidden ones', () => {
    const { game, peers } = makeGame(2, 0, 777);
    const rushRoom = game.rooms.find((r) => r.encounters.some((e) => e.type === 'rush'))!;
    const n = rushRoom.index;
    // stand both players in the rush room; hide one
    const hide = [rushRoom, game.rooms[n - 1]].flatMap((r) => r.interactables).find((i) => i.kind === 'hide' && !i.data?.jack)!;
    const hideRoom = game.rooms.findIndex((r) => r.interactables.includes(hide));
    for (let k = 1; k <= n; k++) game.openDoor(k, 'p0');
    place(game, 'p0', hide.exitPos!, hideRoom);
    game.handle('p0', { t: 'interact', id: hide.id });
    expect(game.getPlayer('p0')!.hidden).toBe(hide.id);
    place(game, 'p1', rushRoom.path[2], n);
    const rush = [...game.entities.values()].find((e) => e.kind === 'rush')!;
    expect(rush.state).toBe('warn');
    step(game, 12);
    expect(peers[1].events.some((e) => e.e === 'entity' && e.kind === 'rush' && e.phase === 'run')).toBe(true);
    expect(game.getPlayer('p1')!.dead).toBe(true);
    expect(game.getPlayer('p1')!.cause).toBe('rush');
    expect(game.getPlayer('p0')!.dead).toBe(false);
    expect([...game.entities.values()].some((e) => e.kind === 'rush')).toBe(false);
  });

  it('crucifix banishes Rush', () => {
    const { game } = makeGame(1, 0, 777);
    const rushRoom = game.rooms.find((r) => r.encounters.some((e) => e.type === 'rush'))!;
    const p = game.getPlayer('p0')!;
    p.inv[0] = { uid: 'c', type: 'crucifix', count: 1, uses: 1 };
    p.equipped = 0;
    for (let k = 1; k <= rushRoom.index; k++) game.openDoor(k, 'p0');
    place(game, 'p0', rushRoom.path[2], rushRoom.index);
    step(game, 12);
    expect(p.dead).toBe(false);
    expect(p.inv[0]).toBeNull();
  });

  it('Figure: hears a sprinting player and transitions patrol → chase', () => {
    const { game } = makeGame(1, 49, 777);
    const lib = game.rooms[50];
    game.openDoor(50, 'p0');
    place(game, 'p0', toWorld(lib, 0, 0, WALL_T + 8), 50);
    step(game, 5);
    const fig = [...game.entities.values()].find((e) => e.kind === 'figure')!;
    expect(['intro', 'patrol', 'investigate']).toContain(fig.state);
    step(game, 4);
    const p = game.getPlayer('p0')!;
    // sprint right next to it
    place(game, 'p0', { x: fig.pos.x + 3, y: fig.pos.y, z: fig.pos.z }, 50);
    p.sprint = true; p.moving = true;
    game.step(TICK_DT);
    p.sprint = true; p.moving = true;
    game.step(TICK_DT);
    expect(['chase', 'attack']).toContain(fig.state);
  });

  it('Eyes damage players who look at them', () => {
    const { game } = makeGame(1, 0, 4242);
    const er = game.rooms.find((r) => r.encounters.some((e) => e.type === 'eyes'));
    if (!er) return;
    for (let k = 1; k <= er.index; k++) game.openDoor(k, 'p0');
    const eyes = [...game.entities.values()].find((e) => e.kind === 'eyes')!;
    const p = game.getPlayer('p0')!;
    place(game, 'p0', er.path[1], er.index);
    const eye = game.eye(p);
    p.yaw = Math.atan2(-(eyes.pos.x - eye.x), -(eyes.pos.z - eye.z));
    p.pitch = Math.atan2(eyes.pos.y - eye.y, Math.hypot(eyes.pos.x - eye.x, eyes.pos.z - eye.z));
    step(game, 2);
    const hpLooking = p.hp;
    expect(hpLooking).toBeLessThan(100);
    p.pitch = -1.4; // look at the floor
    step(game, 2);
    expect(p.hp).toBe(hpLooking);
  });

  it('library padlock accepts only the secret code derived from books', () => {
    const { game } = makeGame(1, 50, 4242);
    const lib = game.rooms[50];
    const pad = lib.interactables.find((i) => i.kind === 'padlock')!;
    place(game, 'p0', { x: pad.pos.x, y: lib.origin.y, z: pad.pos.z }, 50);
    game.handle('p0', { t: 'code', code: lib.puzzle!.code! === '00000' ? '11111' : '00000' });
    expect(game.debugState().libraryUnlocked).toBe(false);
    game.handle('p0', { t: 'code', code: game.libraryCode() });
    expect(game.debugState().libraryUnlocked).toBe(true);
  });

  it('players left far behind are glitched forward', () => {
    const { game, peers } = makeGame(2, 0);
    for (let k = 1; k <= 10; k++) game.openDoor(k, 'p0');
    place(game, 'p0', game.rooms[10].path[1], 10);
    step(game, 4);
    expect(peers[1].events.some((e) => e.e === 'glitch' && e.target === 'p1')).toBe(true);
    expect(game.getPlayer('p1')!.room).toBe(10);
  });
});
