import crypto from 'node:crypto';
import type { C2S, LobbyPlayer, LobbySummary, S2C } from '@doors/shared';
import { LOBBY_COUNTDOWN, MAX_PLAYERS_DEFAULT, RECONNECT_WINDOW_MS } from '@doors/shared';
import { GameRoom } from './game/GameRoom';
import type { Peer } from './game/types';
import type { ProfileStore } from './persistence';

export interface ClientSession extends Peer {
  id: string;
  token: string;
  name: string;
  profile: string;
  connected: boolean;
  lobby: Lobby | null;
  lastSeen: number;
  rtt: number;
}

export class Lobby {
  members = new Map<string, { s: ClientSession; ready: boolean }>();
  host = '';
  countdown: number | null = null;
  game: GameRoom | null = null;
  emptySince = 0;
  constructor(readonly code: string, readonly isPrivate: boolean, readonly max: number) {}
  get state(): LobbySummary['state'] { return this.game ? 'playing' : this.countdown !== null ? 'countdown' : 'waiting'; }
}

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

/** Elevator lobbies: public matchmaking, private party codes, ready states and synchronized starts. */
export class LobbyManager {
  lobbies = new Map<string, Lobby>();
  sessions = new Map<string, ClientSession>(); // by token
  private timer: NodeJS.Timeout;
  constructor(private profiles: ProfileStore, private opts: { maxPlayers?: number; countdown?: number; seed?: () => number } = {}) {
    this.timer = setInterval(() => this.tick(), 250);
  }
  get maxPlayers() { return this.opts.maxPlayers ?? MAX_PLAYERS_DEFAULT; }

  dispose() {
    clearInterval(this.timer);
    for (const l of this.lobbies.values()) l.game?.dispose();
  }

  /** hello: (re)identify a session. Returns the session to bind to this socket. */
  hello(peerSend: (m: S2C) => void, m: Extract<C2S, { t: 'hello' }>): ClientSession {
    let s = m.token ? this.sessions.get(m.token) : undefined;
    const name = (m.name || 'Guest').replace(/[^\w \-.]/g, '').slice(0, 16) || 'Guest';
    if (s) {
      s.send = peerSend;
      s.connected = true;
      s.lastSeen = Date.now();
    } else {
      const token = crypto.randomBytes(18).toString('base64url');
      s = { id: 'p' + crypto.randomBytes(5).toString('hex'), token, name, profile: m.profile.slice(0, 64), connected: true, lobby: null, lastSeen: Date.now(), rtt: 0.1, send: peerSend };
      this.sessions.set(token, s);
    }
    s.name = name;
    const prof = this.profiles.get(s.profile, name);
    s.send({ t: 'welcome', id: s.id, token: s.token, knobs: prof.knobs, stats: { runs: prof.runs, best: prof.best, wins: prof.wins } });
    // reconnect into a running game
    const l = s.lobby;
    if (l) {
      if (l.game && l.game.hasPlayer(s.id) && !l.game.ended) l.game.attach(s.id, s);
      else this.sendLobby(l);
    }
    return s;
  }

  disconnect(s: ClientSession) {
    s.connected = false;
    s.lastSeen = Date.now();
    const l = s.lobby;
    if (!l) return;
    if (l.game && !l.game.ended) l.game.detach(s.id);
    else this.leave(s);
  }

  handle(s: ClientSession, m: C2S) {
    switch (m.t) {
      case 'listLobbies': s.send({ t: 'lobbyList', lobbies: this.list() }); return;
      case 'createLobby': this.leave(s); this.join(s, this.create(m.private)); return;
      case 'joinLobby': {
        const l = this.lobbies.get(m.code.toUpperCase());
        if (!l) return s.send({ t: 'error', msg: 'No elevator with that code.' });
        if (l.game) return s.send({ t: 'error', msg: 'That run has already started.' });
        if (l.members.size >= l.max && !l.members.has(s.id)) return s.send({ t: 'error', msg: 'That elevator is full.' });
        if (s.lobby !== l) this.leave(s);
        this.join(s, l);
        return;
      }
      case 'quickJoin': {
        this.leave(s);
        const l = [...this.lobbies.values()].find((x) => !x.isPrivate && !x.game && x.members.size < x.max) ?? this.create(false);
        this.join(s, l);
        return;
      }
      case 'leaveLobby': this.leave(s); s.send({ t: 'leftLobby' }); return;
      case 'ready': {
        const l = s.lobby; const mem = l?.members.get(s.id);
        if (!l || !mem || l.game) return;
        mem.ready = m.ready;
        this.updateCountdown(l);
        this.sendLobby(l);
        return;
      }
      case 'start': {
        // host may start immediately once everyone present is ready
        const l = s.lobby;
        if (!l || l.host !== s.id || l.game) return;
        if ([...l.members.values()].every((x) => x.ready)) { l.countdown = 0; }
        return;
      }
      default: {
        const l = s.lobby;
        if (m.t === 'ping') { s.send({ t: 'pong', c: m.c, s: Date.now() }); }
        if (l?.game && !l.game.ended) {
          if (m.t !== 'ping') l.game.handle(s.id, m);
        }
      }
    }
  }

  private create(isPrivate: boolean): Lobby {
    let code = '';
    do { code = Array.from({ length: 5 }, () => CODE_ALPHABET[crypto.randomInt(CODE_ALPHABET.length)]).join(''); } while (this.lobbies.has(code));
    const l = new Lobby(code, isPrivate, this.maxPlayers);
    this.lobbies.set(code, l);
    return l;
  }

  private join(s: ClientSession, l: Lobby) {
    if (!l.members.size) l.host = s.id;
    l.members.set(s.id, { s, ready: false });
    s.lobby = l;
    l.countdown = null;
    this.sendLobby(l);
  }

  leave(s: ClientSession) {
    const l = s.lobby;
    if (!l) return;
    if (l.game && !l.game.ended) { l.game.detach(s.id); }
    l.members.delete(s.id);
    s.lobby = null;
    if (l.host === s.id) l.host = [...l.members.keys()][0] ?? '';
    if (!l.members.size) { l.game?.dispose(); this.lobbies.delete(l.code); return; }
    this.updateCountdown(l);
    this.sendLobby(l);
  }

  private updateCountdown(l: Lobby) {
    const all = [...l.members.values()];
    const ready = all.length > 0 && all.every((m) => m.ready && m.s.connected);
    if (ready && l.countdown === null) l.countdown = this.opts.countdown ?? LOBBY_COUNTDOWN;
    if (!ready) l.countdown = null;
  }

  private list(): LobbySummary[] {
    return [...this.lobbies.values()].filter((l) => !l.isPrivate).map((l) => ({ code: l.code, players: l.members.size, max: l.max, state: l.state, name: `Elevator ${l.code}` }));
  }

  sendLobby(l: Lobby) {
    const players: LobbyPlayer[] = [...l.members.values()].map((m) => ({ id: m.s.id, name: m.s.name, ready: m.ready, host: l.host === m.s.id, connected: m.s.connected }));
    for (const m of l.members.values()) if (m.s.connected) m.s.send({ t: 'lobby', code: l.code, private: l.isPrivate, players, countdown: l.countdown === null ? null : Math.ceil(l.countdown), max: l.max, you: m.s.id });
  }

  private startGame(l: Lobby) {
    const seed = this.opts.seed ? this.opts.seed() : crypto.randomInt(1, 2 ** 31 - 1);
    const members = [...l.members.values()];
    l.countdown = null;
    const game = new GameRoom({
      seed,
      players: members.map((m) => ({ id: m.s.id, name: m.s.name, peer: m.s.connected ? m.s : null })),
      onEnd: (result, stats) => {
        const knobs: Record<string, number> = {};
        for (const st of stats) {
          const sess = members.find((m) => m.s.id === st.id)?.s;
          if (sess) knobs[st.id] = this.profiles.recordRun(sess.profile, st.door, st.won, st.gold);
        }
        setTimeout(() => this.returnToLobby(l, game), 1500);
        return knobs;
      },
    });
    l.game = game;
    const spawn = game.spawnPoints(0);
    members.forEach((m, i) => {
      if (m.s.connected) {
        m.s.send({ t: 'start', seed, version: game.layout.version, players: members.map((x) => ({ id: x.s.id, name: x.s.name })), spawn: [spawn[i % spawn.length]], you: m.s.id });
        m.s.send({ t: 'sync', w: game.worldSync() });
      }
    });
  }

  private returnToLobby(l: Lobby, game: GameRoom) {
    if (l.game !== game) return;
    game.dispose();
    l.game = null;
    for (const [id, m] of l.members) {
      m.ready = false;
      if (!m.s.connected) { l.members.delete(id); m.s.lobby = null; }
    }
    if (!l.members.size) { this.lobbies.delete(l.code); return; }
    if (!l.members.has(l.host)) l.host = [...l.members.keys()][0];
    this.sendLobby(l);
  }

  private tick() {
    const now = Date.now();
    for (const l of [...this.lobbies.values()]) {
      if (l.countdown !== null && !l.game) {
        l.countdown -= 0.25;
        if (l.countdown <= 0) this.startGame(l);
        else if (Math.abs(l.countdown - Math.round(l.countdown)) < 0.01) this.sendLobby(l);
      }
      // abandoned runs: nobody connected for the whole reconnect window
      if (l.game) {
        if (l.game.connectedCount() === 0) {
          if (!l.emptySince) l.emptySince = now;
          if (now - l.emptySince > RECONNECT_WINDOW_MS) {
            l.game.dispose();
            for (const m of l.members.values()) m.s.lobby = null;
            this.lobbies.delete(l.code);
          }
        } else l.emptySince = 0;
      }
    }
    // forget sessions idle for a long time
    for (const [tok, s] of this.sessions) if (!s.connected && !s.lobby && now - s.lastSeen > 10 * 60_000) this.sessions.delete(tok);
  }
}
