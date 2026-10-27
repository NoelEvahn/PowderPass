import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import WebSocket from 'ws';
import type { C2S, S2C } from '@doors/shared';
import { PROTOCOL_VERSION } from '@doors/shared';
import { startServer } from '../src/index';

/** Minimal WebSocket test client speaking the real protocol. */
class Client {
  ws!: WebSocket;
  msgs: S2C[] = [];
  token?: string;
  id = '';
  constructor(public name: string, public port: number) {}
  async connect(token?: string) {
    this.ws = new WebSocket(`ws://localhost:${this.port}/ws`);
    this.ws.on('message', (d) => {
      const m = JSON.parse(d.toString()) as S2C;
      if (m.t === 'welcome') { this.token = m.token; this.id = m.id; }
      this.msgs.push(m);
    });
    await new Promise((r) => this.ws.once('open', r));
    this.send({ t: 'hello', name: this.name, profile: 'prof-' + this.name, token, v: PROTOCOL_VERSION });
    await this.wait((m) => m.t === 'welcome');
  }
  send(m: C2S) { this.ws.send(JSON.stringify(m)); }
  async wait<T extends S2C>(pred: (m: S2C) => boolean, ms = 8000): Promise<T> {
    const t0 = Date.now();
    for (;;) {
      const i = this.msgs.findIndex(pred);
      if (i >= 0) return this.msgs.splice(i, 1)[0] as T;
      if (Date.now() - t0 > ms) throw new Error(`${this.name}: timeout waiting for message`);
      await new Promise((r) => setTimeout(r, 20));
    }
  }
  close() { this.ws.close(); }
}

let srv: Awaited<ReturnType<typeof startServer>>;
beforeAll(async () => { srv = await startServer({ port: 0, staticDir: null, dataFile: null, countdown: 0.5, seed: () => 4242 }); });
afterAll(async () => { await srv.close(); });

describe('lobby & multiplayer over WebSockets', () => {
  it('rejects malformed and out-of-date messages', async () => {
    const ws = new WebSocket(`ws://localhost:${srv.port}/ws`);
    const got: S2C[] = [];
    ws.on('message', (d) => got.push(JSON.parse(d.toString())));
    await new Promise((r) => ws.once('open', r));
    ws.send('not json');
    ws.send(JSON.stringify({ t: 'input', seq: 'x' }));
    ws.send(JSON.stringify({ t: 'hello', name: 'old', profile: 'p', v: 1 }));
    await new Promise((r) => setTimeout(r, 200));
    expect(got.some((m) => m.t === 'error')).toBe(true);
    expect(got.some((m) => m.t === 'welcome')).toBe(false);
    ws.close();
  });

  it('private party code: join, ready, synchronized start, shared doors', async () => {
    const a = new Client('Alice', srv.port), b = new Client('Bob', srv.port);
    await a.connect(); await b.connect();
    a.send({ t: 'createLobby', private: true });
    const la = await a.wait<Extract<S2C, { t: 'lobby' }>>((m) => m.t === 'lobby');
    b.send({ t: 'joinLobby', code: la.code });
    await b.wait((m) => m.t === 'lobby' && m.players.length === 2);
    // private lobbies are not listed publicly
    b.send({ t: 'listLobbies' });
    const list = await b.wait<Extract<S2C, { t: 'lobbyList' }>>((m) => m.t === 'lobbyList');
    expect(list.lobbies.find((l) => l.code === la.code)).toBeUndefined();
    a.send({ t: 'ready', ready: true });
    b.send({ t: 'ready', ready: true });
    const sa = await a.wait<Extract<S2C, { t: 'start' }>>((m) => m.t === 'start');
    const sb = await b.wait<Extract<S2C, { t: 'start' }>>((m) => m.t === 'start');
    expect(sa.seed).toBe(sb.seed);
    expect(sa.players.map((p) => p.id).sort()).toEqual(sb.players.map((p) => p.id).sort());
    // walk Alice from the elevator to door 1 in legal steps and open it; Bob must see the door open
    const game = srv.lobbies.lobbies.get(la.code)!.game!;
    const path = game.rooms[0].path;
    let seq = 0;
    const pts = [sa.spawn[0], ...path.slice(1, -1)];
    for (let i = 1; i < pts.length; i++) {
      const p0 = pts[i - 1], p1 = pts[i];
      const n = Math.ceil(Math.hypot(p1.x - p0.x, p1.z - p0.z) / 0.15);
      for (let k = 1; k <= n; k++) {
        a.send({ t: 'input', seq: ++seq, x: p0.x + (p1.x - p0.x) * k / n, y: 0, z: p0.z + (p1.z - p0.z) * k / n, yaw: 0, pitch: 0, crouch: false, sprint: false, moving: true });
        await new Promise((r) => setTimeout(r, 30));
      }
    }
    a.send({ t: 'interact', id: 'door:1' });
    await b.wait((m) => m.t === 'ev' && m.list.some((e) => e.e === 'door' && e.n === 1));
    // Bob sees Alice's position in snapshots
    const snap = await b.wait<Extract<S2C, { t: 'snap' }>>((m) => m.t === 'snap' && m.players.some((p) => p.id === a.id && p.z > 2));
    expect(snap.players.find((p) => p.id === a.id)!.room).toBeGreaterThanOrEqual(0);

    // speed hack is corrected by the server
    a.send({ t: 'input', seq: ++seq, x: 50, y: 0, z: 50, yaw: 0, pitch: 0, crouch: false, sprint: true, moving: true });
    await a.wait((m) => m.t === 'correct');

    // disconnect + reconnect within the window resumes the same player in the same run
    const tok = a.token;
    a.close();
    await b.wait((m) => m.t === 'ev' && m.list.some((e) => e.e === 'notice' && e.text.includes('lost connection')));
    const a2 = new Client('Alice', srv.port);
    a2.msgs = [];
    await a2.connect(tok);
    const s2 = await a2.wait<Extract<S2C, { t: 'start' }>>((m) => m.t === 'start');
    expect(s2.seed).toBe(sa.seed);
    expect(a2.id).toBe(a.id);
    const sync = await a2.wait<Extract<S2C, { t: 'sync' }>>((m) => m.t === 'sync');
    expect(sync.w.openDoors).toContain(1);
    a2.close(); b.close();
  });

  it('quick play matches players into the same public elevator', async () => {
    const c = new Client('C', srv.port), d = new Client('D', srv.port);
    await c.connect(); await d.connect();
    c.send({ t: 'quickJoin' });
    const lc = await c.wait<Extract<S2C, { t: 'lobby' }>>((m) => m.t === 'lobby');
    d.send({ t: 'quickJoin' });
    const ld = await d.wait<Extract<S2C, { t: 'lobby' }>>((m) => m.t === 'lobby');
    expect(ld.code).toBe(lc.code);
    d.send({ t: 'listLobbies' });
    const list = await d.wait<Extract<S2C, { t: 'lobbyList' }>>((m) => m.t === 'lobbyList');
    expect(list.lobbies.find((l) => l.code === lc.code)?.players).toBe(2);
    d.send({ t: 'leaveLobby' });
    await d.wait((m) => m.t === 'leftLobby');
    await c.wait((m) => m.t === 'lobby' && m.players.length === 1);
    c.close(); d.close();
  });

  it('a full elevator rejects extra players', async () => {
    const cs = await Promise.all([0, 1, 2, 3, 4].map(async (i) => { const c = new Client('P' + i, srv.port); await c.connect(); return c; }));
    cs[0].send({ t: 'createLobby', private: true });
    const l = await cs[0].wait<Extract<S2C, { t: 'lobby' }>>((m) => m.t === 'lobby');
    for (let i = 1; i < 4; i++) { cs[i].send({ t: 'joinLobby', code: l.code }); await cs[i].wait((m) => m.t === 'lobby'); }
    cs[4].send({ t: 'joinLobby', code: l.code });
    const err = await cs[4].wait<Extract<S2C, { t: 'error' }>>((m) => m.t === 'error');
    expect(err.msg).toMatch(/full/);
    for (const c of cs) c.close();
  });
});
