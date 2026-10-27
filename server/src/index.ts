import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { WebSocketServer, type WebSocket } from 'ws';
import { PROTOCOL_VERSION, type S2C } from '@doors/shared';
import { LobbyManager, type ClientSession } from './lobby';
import { ProfileStore } from './persistence';
import { parseC2S } from './validate';

export interface ServerOptions { port: number; staticDir?: string | null; dataFile?: string | null; maxPlayers?: number; countdown?: number; seed?: () => number }

const MIME: Record<string, string> = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.wasm': 'application/wasm', '.png': 'image/png', '.svg': 'image/svg+xml', '.json': 'application/json', '.ico': 'image/x-icon', '.webmanifest': 'application/manifest+json' };

/** Start the HTTP + WebSocket game server. Serves the built client too when `staticDir` exists. */
export function startServer(opts: ServerOptions) {
  const profiles = new ProfileStore(opts.dataFile ?? null);
  const lobbies = new LobbyManager(profiles, { maxPlayers: opts.maxPlayers, countdown: opts.countdown, seed: opts.seed });
  const staticDir = opts.staticDir && fs.existsSync(opts.staticDir) ? opts.staticDir : null;

  const server = http.createServer((req, res) => {
    const url = new URL(req.url ?? '/', 'http://x');
    if (url.pathname === '/health') {
      res.writeHead(200, { 'content-type': 'application/json', 'access-control-allow-origin': '*' });
      res.end(JSON.stringify({ ok: true, lobbies: lobbies.lobbies.size, sessions: lobbies.sessions.size, protocol: PROTOCOL_VERSION }));
      return;
    }
    if (!staticDir) { res.writeHead(404); res.end('Doors game server. Connect via WebSocket at /ws'); return; }
    let p = path.normalize(decodeURIComponent(url.pathname)).replace(/^([/\\])+/, '');
    if (p.includes('..')) { res.writeHead(400); res.end(); return; }
    let file = path.join(staticDir, p || 'index.html');
    if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(staticDir, 'index.html');
    const ext = path.extname(file);
    res.writeHead(200, { 'content-type': MIME[ext] ?? 'application/octet-stream', 'cache-control': p.startsWith('assets/') ? 'public, max-age=31536000, immutable' : 'no-cache' });
    fs.createReadStream(file).pipe(res);
  });

  const wss = new WebSocketServer({ server, path: '/ws', maxPayload: 16 * 1024 });
  wss.on('connection', (ws: WebSocket) => {
    let session: ClientSession | null = null;
    let tokens = 120;
    let last = Date.now();
    const send = (m: S2C) => { if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(m)); };
    const alive = setInterval(() => { if (ws.readyState === ws.OPEN) ws.ping(); }, 15000);
    ws.on('message', (data) => {
      // token-bucket rate limiting
      const now = Date.now();
      tokens = Math.min(120, tokens + ((now - last) / 1000) * 80);
      last = now;
      if (--tokens < 0) return;
      const m = parseC2S(data.toString());
      if (!m) return;
      try {
        if (m.t === 'hello') {
          if (m.v !== PROTOCOL_VERSION) { send({ t: 'error', msg: 'Your client is out of date. Please reload.' }); return; }
          session = lobbies.hello(send, m);
          return;
        }
        if (!session) return;
        lobbies.handle(session, m);
      } catch (e) {
        console.error('[ws] handler error', e);
      }
    });
    ws.on('close', () => { clearInterval(alive); if (session && session.send === send) lobbies.disconnect(session); });
    ws.on('error', () => ws.close());
  });

  return new Promise<{ port: number; close: () => Promise<void>; lobbies: LobbyManager }>((resolve) => {
    server.listen(opts.port, () => {
      const addr = server.address();
      const port = typeof addr === 'object' && addr ? addr.port : opts.port;
      resolve({
        port,
        lobbies,
        close: () => new Promise<void>((r) => { lobbies.dispose(); profiles.flush(); for (const c of wss.clients) c.terminate(); wss.close(); server.close(() => r()); }),
      });
    });
  });
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const here = path.dirname(fileURLToPath(import.meta.url));
  const port = Number(process.env.PORT ?? 2567);
  const staticDir = process.env.STATIC_DIR ?? path.resolve(here, '../../client/dist');
  const dataFile = process.env.DATA_FILE === '' ? null : process.env.DATA_FILE ?? path.resolve(here, '../data/profiles.json');
  const countdown = process.env.LOBBY_COUNTDOWN ? Number(process.env.LOBBY_COUNTDOWN) : undefined;
  startServer({ port, staticDir, dataFile, countdown, maxPlayers: Number(process.env.MAX_PLAYERS ?? 4) }).then(({ port: p }) => {
    console.log(`[doors] server listening on :${p} (ws path /ws)${fs.existsSync(staticDir) ? `, serving ${staticDir}` : ''}`);
  });
}
