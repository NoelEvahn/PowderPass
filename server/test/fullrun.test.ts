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

describe('co-op and mortal runs', () => {
  it('two bots complete the run together (shared doors, puzzles, elevator)', () => {
    const { result, game, bots } = playRun(31337, 2, true);
    expect(result).toBe('win');
    expect(bots.every((b) => b.p.inElevator)).toBe(true);
    expect(game.debugState().powerOn).toBe(true);
  });

  it('reports mortal survival across seeds (entities really kill)', () => {
    const outcomes: string[] = [];
    let deaths = 0;
    for (const seed of [1, 2, 3, 4, 5, 6]) {
      const { result, bots } = playRun(seed, 1, false);
      outcomes.push(`${seed}:${result ?? 'timeout'}@${bots[0].p.furthest}${bots[0].p.cause ? '(' + bots[0].p.cause + ')' : ''}`);
      deaths += bots[0].p.deaths;
    }
    console.log('mortal bot outcomes:', outcomes.join(' '));
    expect(outcomes.length).toBe(6);
    // the scripted bot is not a perfect player: dangerous encounters must be able to kill it
    expect(deaths).toBeGreaterThan(0);
  });
});
