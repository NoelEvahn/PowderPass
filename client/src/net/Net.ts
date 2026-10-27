import type { C2S, S2C } from '@doors/shared';
import { PROTOCOL_VERSION } from '@doors/shared';
import { profileId } from '../settings';

type Handler = (m: S2C) => void;

export function serverUrl(): string {
  const env = import.meta.env.VITE_SERVER_URL as string | undefined;
  const q = new URLSearchParams(location.search).get('server');
  const base = q || env;
  if (base) return base.replace(/^http/, 'ws').replace(/\/$/, '') + (base.endsWith('/ws') ? '' : '/ws');
  return `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;
}

/**
 * WebSocket client with automatic reconnect. The session token lives in sessionStorage so a refresh
 * or network drop re-attaches to the running game within the server's reconnect window.
 */
export class Net {
  private ws: WebSocket | null = null;
  private handlers = new Set<Handler>();
  private queue: C2S[] = [];
  private retry = 0;
  private closedByUser = false;
  connected = false;
  rtt = 0.1;
  id = '';
  name = 'Guest';
  onStatus: (s: 'connecting' | 'online' | 'offline') => void = () => undefined;
  private pingTimer: number | undefined;

  get token() { try { return sessionStorage.getItem('doors-token') ?? undefined; } catch { return undefined; } }
  set token(v: string | undefined) { try { if (v) sessionStorage.setItem('doors-token', v); } catch { /* ignore */ } }

  connect(name: string) {
    this.name = name;
    this.closedByUser = false;
    this.open();
  }

  private open() {
    this.onStatus('connecting');
    let ws: WebSocket;
    try { ws = new WebSocket(serverUrl()); } catch { this.scheduleRetry(); return; }
    this.ws = ws;
    ws.onopen = () => {
      this.connected = true;
      this.retry = 0;
      this.onStatus('online');
      ws.send(JSON.stringify({ t: 'hello', name: this.name, profile: profileId(), token: this.token, v: PROTOCOL_VERSION } satisfies C2S));
      for (const m of this.queue.splice(0)) ws.send(JSON.stringify(m));
      clearInterval(this.pingTimer);
      this.pingTimer = window.setInterval(() => this.send({ t: 'ping', c: performance.now() }), 2000);
    };
    ws.onmessage = (ev) => {
      let m: S2C;
      try { m = JSON.parse(ev.data); } catch { return; }
      if (m.t === 'welcome') { this.id = m.id; this.token = m.token; }
      if (m.t === 'pong') { const r = (performance.now() - m.c) / 1000; this.rtt = this.rtt * 0.8 + r * 0.2; }
      for (const h of this.handlers) h(m);
    };
    ws.onclose = () => {
      this.connected = false;
      clearInterval(this.pingTimer);
      this.onStatus('offline');
      if (!this.closedByUser) this.scheduleRetry();
    };
    ws.onerror = () => ws.close();
  }

  private scheduleRetry() {
    const delay = Math.min(8000, 500 * 2 ** this.retry++);
    setTimeout(() => this.open(), delay);
  }

  send(m: C2S) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) this.ws.send(JSON.stringify(m));
    else if (m.t !== 'input' && m.t !== 'ping') this.queue.push(m);
  }

  on(h: Handler) { this.handlers.add(h); return () => this.handlers.delete(h); }

  close() { this.closedByUser = true; this.ws?.close(); }
}
