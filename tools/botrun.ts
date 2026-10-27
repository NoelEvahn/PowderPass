import { TICK_DT } from '../shared/src/index';
import { GameRoom } from '../server/src/game/GameRoom';
import { Bot, FakePeer } from '../server/test/bot';
const seed = Number(process.argv[2] ?? 101);
const inv = process.argv[3] !== 'mortal';
const n = Number(process.argv[4] ?? 1);
let result: string | null = null;
const peers = Array.from({ length: n }, (_, i) => new FakePeer('b' + i));
const game = new GameRoom({ seed, manual: true, invulnerable: inv, secret: 5, players: peers.map((p) => ({ id: p.id, name: p.id, peer: p })), onEnd: (r) => { result = r; } });
const bots = peers.map((p) => new Bot(game, p.id, p));
let t = 0, lastRoom = -1, lastT = 0;
const t0 = Date.now();
const MAX = Number(process.env.MAXT ?? 5000);
while (!result && t < MAX) {
  for (const b of bots) b.tick();
  game.step(TICK_DT);
  t += TICK_DT;
  const r = bots[0].p.room;
  if (process.env.TRACE && (t > 24 || Math.abs(t % 5) < TICK_DT)) console.log(t.toFixed(0), r, game.rooms[r].kind, [...game.entities.values()].map(e=>e.kind+":"+e.state).join(","));
  if (r !== lastRoom) { lastRoom = r; lastT = t; }
  if (t - lastT > 120) { console.log('STUCK room', r, game.rooms[r].kind, bots[0].log.slice(-3), bots[0].p.pos, game.rooms[r].exits[game.rooms[r].realExit]?.lock); break; }
}
console.log('result', result, 'sim', t.toFixed(0), 'wall ms', Date.now() - t0, 'room', bots[0].p.room, 'deaths', bots.map(b=>b.p.deaths), 'hp', bots.map(b=>b.p.hp), 'cause', bots.map(b=>b.p.cause), bots[0].peer.msgs.filter((m:any)=>m.t==='ev').flatMap((m:any)=>m.list).filter((e:any)=>e.e==='damage').reduce((a:any,e:any)=>{a[e.cause]=(a[e.cause]||0)+e.amount;return a},{}));
