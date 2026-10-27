import { describe, expect, it } from 'vitest';
import { FINAL_ROOM, TICK_DT } from '@doors/shared';
import { GameRoom } from '../src/game/GameRoom';
import { Bot, FakePeer } from './bot';

function playRun(seed: number, players: number, invulnerable: boolean, maxSim = 4000) {
  let result: string | null = null;
  const peers = Array.from({ length: players }, (_, i) => new FakePeer('b' + i));
  const game = new GameRoom({ seed, manual: true, invulnerable, secret: seed * 3 + 1, players: peers.map((p) => ({ id: p.id, name: p.id, peer: p })), onEnd: (r) => { result = r; } });
  const bots = peers.map((p) => new Bot(game, p.id, p));
  let t = 0;
  while (!result && t < maxSim) {
    for (const b of bots) b.tick();
    game.step(TICK_DT);
    t += TICK_DT;
  }
  return { result, t, game, bots };
}

describe('full run Doors 0–100', () => {
  for (const seed of [101, 2024, 777777]) {
    it(`completes the hotel with seed ${seed} (scripted bot)`, () => {
      const { result, t, game, bots } = playRun(seed, 1, true);
      if (result !== 'win') console.log('stuck at', bots[0].p.room, game.rooms[bots[0].p.room].kind, bots[0].log.slice(-5), game.debugState().opened.length);
      expect(result).toBe('win');
      expect(game.debugState().opened).toContain(FINAL_ROOM);
      expect(game.debugState().libraryUnlocked).toBe(true);
      expect(game.debugState().powerOn).toBe(true);
      console.log(`seed ${seed}: escaped in ${t.toFixed(0)}s of simulated time`);
    });
  }
});
