import './style.css';
import type { InventoryItem, ItemType, LobbyPlayer, LobbySummary, SelfState } from '@doors/shared';
import { ITEMS, SHOP_STOCK, SHAPES } from '@doors/shared';
import { ICONS, SHAPE_SVG } from './icons';
import { settings, saveSettings } from '../settings';

const $ = <T extends HTMLElement = HTMLElement>(root: ParentNode, sel: string) => root.querySelector(sel) as T;
const esc = (s: string) => s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);

/** Guiding Light: hints shown on death, by cause (original wording). */
export const DEATH_HINTS: Record<string, string[]> = {
  rush: ['You met the one who rushes through the halls.', 'When the lights begin to flicker, it is already on its way. Hide in a closet or under a bed until it passes.'],
  ambush: ['That was the one that comes back.', 'Its glow is green and it returns again and again. Stay hidden while it passes, but step out between its runs.'],
  seek: ['The one with many eyes caught up to you.', 'Do not stop running. Follow the light to the correct door, and crouch under what has fallen.'],
  seekHands: ['The walls reached out for you.', 'Keep to the centre of the hallway while running from it.'],
  fire: ['The flames took you.', 'Look for the light that guides you around the fire.'],
  chandelier: ['Something fell on you.', 'Listen for the creak above and keep moving.'],
  figure: ['It heard you.', 'It cannot see. Crouch to move quietly, hide when it comes close, and keep your heartbeat steady.'],
  screech: ['Something bit you in the dark.', 'When you hear it in a dark room, turn and look right at it.'],
  eyes: ['You looked into the eyes for too long.', 'Keep your gaze on the floor while you pass through its room.'],
  halt: ['You walked into it.', 'When the hallway tells you to turn around, do it. Walk away from it until it passes.'],
  dupe: ['That was not the right door.', 'Remember which number comes next. Only one door is real.'],
  hide: ['Something did not want you in there.', 'Do not stay hidden for too long when nothing is coming.'],
  snare: ['You stepped into something.', 'Watch the ground in the greenhouse and walk around the traps.'],
  timothy: ['A small surprise.', 'Some drawers have occupants.'],
  disconnected: ['You lost connection.', 'Rejoin quickly to keep your place.'],
};

export interface UIHandlers {
  quickPlay(): void; createPrivate(): void; joinCode(code: string): void; joinLobby(code: string): void; refreshLobbies(): void;
  leaveLobby(): void; ready(r: boolean): void; startNow(): void;
  equip(slot: number): void; buy(item: ItemType): void; closeShop(): void; submitCode(code: string): void; closePadlock(): void;
  toggleBreaker(n: number): void; closeBreaker(): void; revive(): void; backToLobby(): void; resume(): void; leaveGame(): void;
  settingsChanged(): void; nameChanged(n: string): void;
}

export class UI {
  root: HTMLElement;
  private screens: Record<string, HTMLElement> = {};
  private notices!: HTMLElement;
  private subs!: HTMLElement;
  private h: UIHandlers;
  private code = '';
  private hb: { el: HTMLElement; beats: { el: HTMLElement; t: number }[]; start: number; window: number } | null = null;
  doorTimer = 0;
  modalOpen: string | null = null;

  constructor(root: HTMLElement, h: UIHandlers) {
    this.root = root;
    this.h = h;
    root.innerHTML = this.template();
    for (const el of Array.from(root.querySelectorAll<HTMLElement>('.screen'))) this.screens[el.id] = el;
    this.notices = $(root, '#notices');
    this.subs = $(root, '#subs');
    this.wire();
  }

  private template() {
    return `
    <div id="menu" class="screen menu">
      <div class="status" id="netStatus"><span class="dot"></span><span>offline</span></div>
      <h1 class="logo">DOORS</h1>
      <div class="sub">THE HOTEL · 100 DOORS</div>
      <div class="panel">
        <label>Your name</label>
        <input type="text" id="nameIn" maxlength="16" placeholder="Guest" />
        <button class="btn primary" id="bQuick">Quick Play · Public Elevator</button>
        <button class="btn" id="bPrivate">Create Private Elevator</button>
        <div class="row"><input type="text" id="codeIn" maxlength="5" placeholder="Party code" style="text-transform:uppercase"/><button class="btn small" id="bJoin">Join</button></div>
        <label>Public elevators <a class="muted" id="bRefresh" style="cursor:pointer;float:right">refresh</a></label>
        <div class="lobbylist" id="lobbyList"><div class="muted">None yet. Quick Play creates one.</div></div>
        <div class="row"><button class="btn small" id="bSettings">Settings</button><button class="btn small" id="bHelp">How to play</button></div>
        <div class="muted" id="profile" style="margin-top:8px"></div>
      </div>
    </div>
    <div id="lobby" class="screen menu elevator-bg">
      <div class="panel">
        <div class="muted" style="text-align:center">ELEVATOR</div>
        <div class="code" id="lobbyCode">-----</div>
        <div class="muted" style="text-align:center" id="lobbyKind"></div>
        <div class="players" id="lobbyPlayers"></div>
        <div class="countdown" id="lobbyCountdown"></div>
        <button class="btn primary" id="bReady">Ready</button>
        <div class="row"><button class="btn small" id="bCopy">Copy invite link</button><button class="btn small" id="bLeave">Leave</button></div>
        <div class="help" style="margin-top:10px">Everyone must be ready for the doors to close. Share the code so friends can join from anywhere.</div>
      </div>
    </div>
    <div id="loading" class="screen"><h1 class="logo" style="font-size:40px">DOORS</h1><div class="muted" id="loadMsg" style="margin:16px">Preparing the hotel…</div><div class="bar"><i id="loadBar" style="width:0%"></i></div></div>
    <div id="death" class="screen">
      <div class="ttl">YOU DIED</div>
      <div class="msg" id="deathMsg"></div>
      <div class="row" style="width:min(420px,90vw)"><button class="btn primary" id="bRevive">Revive (1)</button><button class="btn" id="bSpectate">Spectate</button></div>
      <div class="muted" id="deathInfo"></div>
    </div>
    <div id="end" class="screen"><div class="panel" style="width:min(560px,100%)"><h1 class="logo" style="font-size:38px;text-align:center" id="endTitle"></h1><div class="muted" style="text-align:center" id="endSub"></div><table class="stats" id="endStats"></table><button class="btn primary" id="bBack">Back to the elevator</button></div></div>
    <div id="pause" class="screen"><div class="panel">
      <h2 style="font-family:Cinzel;color:var(--gold);margin:0 0 6px">Paused</h2>
      <div id="settingsBox"></div>
      <button class="btn primary" id="bResume">Resume</button>
      <button class="btn" id="bLeaveGame">Leave run</button>
    </div></div>
    <div id="settingsScreen" class="screen menu"><div class="panel"><h2 style="font-family:Cinzel;color:var(--gold);margin:0">Settings</h2><div id="settingsBox2"></div><button class="btn primary" id="bCloseSettings">Done</button></div></div>
    <div id="helpScreen" class="screen menu"><div class="panel help">
      <h2 style="font-family:Cinzel;color:var(--gold);margin:0 0 6px">How to play</h2>
      Reach door 100 and escape the hotel. Everything is shared with your party: doors, loot and puzzles.<br/>
      <kbd>W A S D</kbd> move · <kbd>Mouse</kbd> look · <kbd>Shift</kbd> sprint · <kbd>Ctrl</kbd>/<kbd>C</kbd> crouch<br/>
      <kbd>E</kbd> / right-click: interact, open, hide · <kbd>1–6</kbd> equip · <kbd>Q</kbd> / click: use item · <kbd>F</kbd> light on/off · <kbd>G</kbd> drop · <kbd>Space</kbd> heartbeat<br/>
      <b>Flickering lights</b> mean an entity is coming. Hide in a closet or under a bed.<br/>
      <b>Green flicker</b>: it will come back several times.<br/>
      <b>Eyes</b>: don't look at them. <b>In the dark</b>, a "psst" means look toward the sound.<br/>
      <b>The Figure</b> is blind. Crouch, keep quiet, and hide.<br/>
      On mobile: left thumb moves, right thumb looks, buttons on the right.
      <button class="btn primary" id="bCloseHelp">Got it</button>
    </div></div>
    <div id="hud">
      <div id="crosshair"></div>
      <div id="prompt"></div>
      <div id="doornum"></div>
      <div id="notices"></div>
      <div id="subs"></div>
      <div id="warning"></div>
      <div id="library"><div class="muted">LIBRARY</div><div class="shapes" id="libShapes"></div><div class="muted" id="libBooks" style="margin-top:4px"></div></div>
      <div id="stats"><div id="gold">0</div><div class="bar"><i id="hpBar" style="width:100%"></i></div><div class="bar stam"><i id="stBar" style="width:100%"></i></div></div>
      <div id="hotbar"></div>
      <div id="heartbeat"><div class="lbl">HEARTBEAT · press SPACE / tap when the pulse is in the zone</div><div class="track"><div class="zone"></div></div></div>
      <div id="padlock" class="modal"><div class="panel"><div class="muted">PADLOCK · 5 digits</div><div class="codeview" id="codeView"></div><div class="keypad" id="keypad"></div><div class="row" style="margin-top:8px"><button class="btn" id="bPadClose">Close</button><button class="btn primary" id="bPadOk">Enter</button></div></div></div>
      <div id="breakerPanel" class="modal"><div class="panel"><div class="muted" id="brkTitle">BREAKER BOX</div><div class="lamps" id="brkLamps"></div><div class="breaker" id="brkSwitches"></div><div class="muted" id="brkInfo"></div><button class="btn" id="bBrkClose">Close</button></div></div>
      <div id="shop" class="modal"><div class="panel"><div class="row"><b style="font-family:Cinzel;color:var(--gold)">Jeff's Shop</b><span id="shopGold" style="text-align:right;color:var(--gold)"></span></div><div class="shopgrid" id="shopGrid"></div><button class="btn" id="bShopClose">Close</button></div></div>
      <div id="hideView"></div>
      <div id="jumpscare"><canvas width="512" height="512"></canvas></div>
      <div id="fps"></div>
    </div>
    <div id="touch">
      <div id="touchArea"></div>
      <div id="joy"><div id="joyKnob"></div></div>
      <div class="tbtn" id="tInteract">USE</div>
      <div class="tbtn" id="tCrouch">CROUCH</div>
      <div class="tbtn" id="tSprint">RUN</div>
      <div class="tbtn" id="tLight">LIGHT</div>
      <div class="tbtn" id="tUse">ITEM</div>
      <div class="tbtn" id="tBeat">BEAT</div>
      <div class="tbtn" id="tMenu">II</div>
    </div>`;
  }

  private wire() {
    const r = this.root;
    const nameIn = $<HTMLInputElement>(r, '#nameIn');
    nameIn.value = settings.name;
    nameIn.addEventListener('change', () => { settings.name = nameIn.value.trim().slice(0, 16); saveSettings(); this.h.nameChanged(settings.name); });
    $(r, '#bQuick').onclick = () => this.h.quickPlay();
    $(r, '#bPrivate').onclick = () => this.h.createPrivate();
    $(r, '#bJoin').onclick = () => { const c = $<HTMLInputElement>(r, '#codeIn').value.trim().toUpperCase(); if (c) this.h.joinCode(c); };
    $(r, '#bRefresh').onclick = () => this.h.refreshLobbies();
    $(r, '#bSettings').onclick = () => { this.renderSettings($(r, '#settingsBox2')); this.show('settingsScreen'); };
    $(r, '#bCloseSettings').onclick = () => this.show('menu');
    $(r, '#bHelp').onclick = () => this.show('helpScreen');
    $(r, '#bCloseHelp').onclick = () => this.show('menu');
    let ready = false;
    $(r, '#bReady').onclick = () => { ready = !ready; this.h.ready(ready); };
    this.setReadyLocal = (v: boolean) => { ready = v; };
    $(r, '#bLeave').onclick = () => this.h.leaveLobby();
    $(r, '#bCopy').onclick = () => {
      const url = `${location.origin}${location.pathname}?join=${this.lobbyCode}`;
      navigator.clipboard?.writeText(url).then(() => this.notice('Invite link copied'), () => this.notice(url));
    };
    $(r, '#bRevive').onclick = () => this.h.revive();
    $(r, '#bSpectate').onclick = () => this.hideDeath();
    $(r, '#bBack').onclick = () => this.h.backToLobby();
    $(r, '#bResume').onclick = () => this.h.resume();
    $(r, '#bLeaveGame').onclick = () => this.h.leaveGame();
    $(r, '#bShopClose').onclick = () => this.h.closeShop();
    $(r, '#bPadClose').onclick = () => this.h.closePadlock();
    $(r, '#bPadOk').onclick = () => this.h.submitCode(this.code);
    $(r, '#bBrkClose').onclick = () => this.h.closeBreaker();
    const kp = $(r, '#keypad');
    for (const k of ['1', '2', '3', '4', '5', '6', '7', '8', '9', '⌫', '0', 'C']) {
      const b = document.createElement('button');
      b.className = 'btn';
      b.textContent = k;
      b.onclick = () => {
        if (k === '⌫') this.code = this.code.slice(0, -1);
        else if (k === 'C') this.code = '';
        else if (this.code.length < 5) this.code += k;
        $(r, '#codeView').textContent = this.code;
      };
      kp.appendChild(b);
    }
  }
  setReadyLocal: (v: boolean) => void = () => undefined;
  lobbyCode = '';

  show(id: string) {
    for (const [k, el] of Object.entries(this.screens)) el.classList.toggle('show', k === id);
  }
  hideScreens() { for (const el of Object.values(this.screens)) el.classList.remove('show'); }
  showHud(on: boolean, touch: boolean) {
    $(this.root, '#hud').classList.toggle('show', on);
    $(this.root, '#touch').classList.toggle('show', on && touch);
  }

  setNet(status: string) {
    const el = $(this.root, '#netStatus');
    el.classList.toggle('online', status === 'online');
    el.querySelector('span:last-child')!.textContent = status;
  }
  setProfile(knobs: number, stats: { runs: number; best: number; wins: number }) {
    $(this.root, '#profile').textContent = `Knobs ${knobs} · Runs ${stats.runs} · Best door ${stats.best} · Escapes ${stats.wins}`;
  }

  renderLobbyList(list: LobbySummary[]) {
    const el = $(this.root, '#lobbyList');
    if (!list.length) { el.innerHTML = '<div class="muted">None yet. Quick Play creates one.</div>'; return; }
    el.innerHTML = list.map((l) => `<div class="item" data-code="${esc(l.code)}"><span>${esc(l.name)}</span><span class="muted">${l.players}/${l.max} · ${l.state}</span></div>`).join('');
    for (const it of Array.from(el.querySelectorAll<HTMLElement>('.item'))) it.onclick = () => this.h.joinLobby(it.dataset.code!);
  }

  renderLobby(code: string, priv: boolean, players: LobbyPlayer[], countdown: number | null, max: number, you: string) {
    this.lobbyCode = code;
    $(this.root, '#lobbyCode').textContent = code;
    $(this.root, '#lobbyKind').textContent = `${priv ? 'Private' : 'Public'} · ${players.length}/${max} players`;
    $(this.root, '#lobbyPlayers').innerHTML = players.map((p) => `<div class="p ${p.ready ? 'ready' : ''}"><span>${esc(p.name)}${p.host ? '<span class="tag">host</span>' : ''}${p.id === you ? '<span class="tag">you</span>' : ''}</span><span>${p.connected ? (p.ready ? 'READY' : 'waiting') : 'reconnecting…'}</span></div>`).join('');
    $(this.root, '#lobbyCountdown').textContent = countdown !== null ? `Doors closing in ${countdown}…` : '';
    const me = players.find((p) => p.id === you);
    $(this.root, '#bReady').textContent = me?.ready ? 'Not ready' : 'Ready';
    this.setReadyLocal(!!me?.ready);
  }

  setLoading(msg: string, pct: number) {
    $(this.root, '#loadMsg').textContent = msg;
    $(this.root, '#loadBar').style.width = `${Math.round(pct * 100)}%`;
  }

  // ---------------------------------------------------------- HUD
  setSelf(s: SelfState) {
    $(this.root, '#hpBar').style.width = `${s.hp}%`;
    $(this.root, '#gold').textContent = String(s.gold);
    $(this.root, '#stBar').style.width = `${s.stamina}%`;
    this.renderHotbar(s.inventory, s.equipped);
    $(this.root, '#bRevive').textContent = `Revive (${s.revives})`;
    ($(this.root, '#bRevive') as HTMLButtonElement).disabled = s.revives <= 0;
    $(this.root, '#shopGold').textContent = `${s.gold} gold`;
  }
  setStamina(v: number) { $(this.root, '#stBar').style.width = `${v}%`; }

  private renderHotbar(inv: (InventoryItem | null)[], eq: number) {
    const el = $(this.root, '#hotbar');
    el.innerHTML = inv.map((it, i) => {
      if (!it) return `<div class="slot" data-i="${i}"><span class="n">${i + 1}</span></div>`;
      const count = it.count > 1 ? it.count : it.uses !== undefined && it.uses > 1 ? it.uses : '';
      const charge = it.charge !== undefined ? `<div class="ch"><i style="width:${Math.round(it.charge * 100)}%"></i></div>` : '';
      const label = it.type === 'key' && it.door ? `<span class="c">${it.door}</span>` : count ? `<span class="c">${count}</span>` : '';
      return `<div class="slot ${i === eq ? 'eq' : ''}" data-i="${i}" title="${esc(ITEMS[it.type].name)}"><span class="n">${i + 1}</span>${ICONS[it.type]}${label}${charge}</div>`;
    }).join('');
    for (const s of Array.from(el.querySelectorAll<HTMLElement>('.slot'))) s.onclick = (e) => { e.stopPropagation(); this.h.equip(Number(s.dataset.i)); };
  }

  prompt(text: string | null, key = 'E') {
    const el = $(this.root, '#prompt');
    if (!text) { el.classList.remove('show'); return; }
    const k = matchMedia('(pointer: coarse)').matches ? 'USE' : key;
    const html = `<kbd>${k}</kbd>${esc(text)}`;
    if (el.innerHTML !== html) el.innerHTML = html;
    el.classList.add('show');
  }

  door(n: number) {
    const el = $(this.root, '#doornum');
    el.textContent = String(n).padStart(4, '0');
    el.classList.add('show');
    clearTimeout(this.doorTimer);
    this.doorTimer = window.setTimeout(() => el.classList.remove('show'), 2200);
  }

  notice(text: string) {
    const d = document.createElement('div');
    d.className = 'notice';
    d.textContent = text;
    this.notices.appendChild(d);
    setTimeout(() => d.remove(), 4000);
    while (this.notices.children.length > 4) this.notices.firstChild?.remove();
  }

  subtitle(text: string) {
    if (!settings.subtitles) return;
    const d = document.createElement('div');
    d.textContent = text;
    this.subs.appendChild(d);
    setTimeout(() => d.remove(), 3200);
    while (this.subs.children.length > 3) this.subs.firstChild?.remove();
  }

  warning(text: string | null, cls = '') {
    const el = $(this.root, '#warning');
    if (!text) { el.classList.remove('show'); return; }
    el.className = `show ${cls}`;
    el.textContent = text;
  }

  library(paper: boolean, shapes: number[], found: Map<number, number>, books: number) {
    const el = $(this.root, '#library');
    el.classList.add('show');
    $(this.root, '#libShapes').innerHTML = paper
      ? shapes.map((s) => `<div class="sh">${SHAPE_SVG[s]}<b>${found.has(s) ? found.get(s) : '?'}</b></div>`).join('')
      : '<div class="muted">Find the hint paper.</div>';
    $(this.root, '#libBooks').textContent = `Books with hints: ${found.size} · searched ${books}`;
  }
  hideLibrary() { $(this.root, '#library').classList.remove('show'); }

  hideOverlay(kind: 'closet' | 'bed' | 'locker' | null) {
    const el = $(this.root, '#hideView');
    el.classList.toggle('show', !!kind);
    el.classList.toggle('bed', kind === 'bed');
  }

  fps(text: string | null) {
    const el = $(this.root, '#fps');
    el.classList.toggle('show', !!text);
    if (text) el.textContent = text;
  }

  jumpscare(kind: 'timothy' | 'jack' | 'hide' | 'dupe', ms = 700) {
    const el = $(this.root, '#jumpscare');
    const c = el.querySelector('canvas')!;
    const ctx = c.getContext('2d')!;
    ctx.clearRect(0, 0, 512, 512);
    if (kind === 'timothy') {
      ctx.fillStyle = '#1a1008'; ctx.beginPath(); ctx.ellipse(256, 270, 90, 70, 0, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = '#1a1008'; ctx.lineWidth = 12;
      for (let k = 0; k < 8; k++) { const a = (k / 8) * Math.PI * 2; ctx.beginPath(); ctx.moveTo(256, 270); ctx.quadraticCurveTo(256 + Math.cos(a) * 200, 270 + Math.sin(a) * 60 - 120, 256 + Math.cos(a) * 240, 270 + Math.sin(a) * 200); ctx.stroke(); }
      ctx.fillStyle = '#f33'; for (const sx of [-1, 1]) for (const sy of [0, 1]) { ctx.beginPath(); ctx.arc(256 + sx * (20 + sy * 14), 230 + sy * 14, 9 - sy * 3, 0, Math.PI * 2); ctx.fill(); }
    } else if (kind === 'jack') {
      ctx.fillStyle = '#d8d0c4'; ctx.beginPath(); ctx.ellipse(256, 256, 150, 200, 0, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = '#000'; for (const sx of [-1, 1]) { ctx.beginPath(); ctx.ellipse(256 + sx * 55, 200, 34, 46, 0, 0, Math.PI * 2); ctx.fill(); }
      ctx.beginPath(); ctx.ellipse(256, 360, 70, 90, 0, 0, Math.PI * 2); ctx.fill();
      el.style.background = 'rgba(160,0,0,.75)';
    } else if (kind === 'hide') {
      ctx.fillStyle = '#000'; ctx.fillRect(0, 0, 512, 512);
      ctx.fillStyle = '#ddd'; for (const sx of [-1, 1]) { ctx.beginPath(); ctx.ellipse(256 + sx * 70, 240, 30, 12, 0, 0, Math.PI * 2); ctx.fill(); }
      ctx.fillStyle = '#c22'; ctx.font = 'bold 54px Cinzel, serif'; ctx.textAlign = 'center'; ctx.fillText('GET OUT', 256, 380);
    } else {
      ctx.fillStyle = '#0a0a0a'; ctx.beginPath(); ctx.ellipse(256, 256, 160, 210, 0, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = '#eee'; for (let k = 0; k < 16; k++) { ctx.beginPath(); ctx.moveTo(150 + k * 13, 300); ctx.lineTo(156 + k * 13, 340); ctx.lineTo(162 + k * 13, 300); ctx.fill(); }
      ctx.fillStyle = '#fff'; for (const sx of [-1, 1]) { ctx.beginPath(); ctx.arc(256 + sx * 60, 190, 14, 0, Math.PI * 2); ctx.fill(); }
    }
    el.classList.add('show');
    setTimeout(() => { el.classList.remove('show'); el.style.background = ''; }, ms);
  }

  // ---------------------------------------------------------- modals
  openModal(id: 'padlock' | 'breakerPanel' | 'shop') {
    this.closeModals();
    this.modalOpen = id;
    $(this.root, '#' + id).classList.add('show');
    if (id === 'padlock') { this.code = ''; $(this.root, '#codeView').textContent = ''; }
  }
  closeModals() {
    for (const id of ['padlock', 'breakerPanel', 'shop']) $(this.root, '#' + id).classList.remove('show');
    this.modalOpen = null;
  }

  renderShop(gold: number) {
    $(this.root, '#shopGold').textContent = `${gold} gold`;
    $(this.root, '#shopGrid').innerHTML = SHOP_STOCK.map((t) => `<div class="shopitem" data-t="${t}">${ICONS[t]}<div><div>${ITEMS[t].name}</div><div class="pr">${ITEMS[t].price} gold</div><div class="muted" style="font-size:11px">${ITEMS[t].desc}</div></div></div>`).join('');
    for (const el of Array.from(this.root.querySelectorAll<HTMLElement>('.shopitem'))) el.onclick = () => this.h.buy(el.dataset.t as ItemType);
  }

  renderBreaker(inserted: number, round: number, switches: boolean[], lit: number[], power: boolean) {
    $(this.root, '#brkTitle').textContent = power ? 'POWER RESTORED' : round > 0 ? `BREAKER BOX · SEQUENCE ${round} / 3` : 'BREAKER BOX';
    $(this.root, '#brkLamps').innerHTML = Array.from({ length: 10 }, (_, i) => `<div class="lamp ${lit.includes(i + 1) ? 'lit' : ''}">${i + 1}</div>`).join('');
    $(this.root, '#brkSwitches').innerHTML = Array.from({ length: 10 }, (_, i) => `<div class="sw ${switches[i] ? 'on' : ''}" data-n="${i + 1}">${i < inserted ? i + 1 : '—'}</div>`).join('');
    $(this.root, '#brkInfo').textContent = inserted < 10 ? `${inserted}/10 breaker switches inserted. Find the rest in the warehouse.` : power ? 'Run to the elevator!' : 'Set each switch to match the lit numbers above.';
    for (const el of Array.from(this.root.querySelectorAll<HTMLElement>('.sw'))) el.onclick = () => { if (inserted >= 10) this.h.toggleBreaker(Number(el.dataset.n)); };
  }

  // ---------------------------------------------------------- heartbeat minigame
  startHeartbeat(beats: number[], window: number) {
    const el = $(this.root, '#heartbeat');
    const track = el.querySelector('.track')!;
    track.querySelectorAll('.beat').forEach((b) => b.remove());
    const list = beats.map((t) => { const b = document.createElement('div'); b.className = 'beat'; track.appendChild(b); return { el: b, t }; });
    this.hb = { el, beats: list, start: performance.now() / 1000, window };
    el.classList.add('show');
    $(this.root, '#tBeat').style.display = 'flex';
  }
  /** Returns the index of the beat currently in the zone (or -1). */
  heartbeatPress(): number {
    if (!this.hb) return -1;
    const t = performance.now() / 1000 - this.hb.start;
    let best = -1, bd = 1e9;
    this.hb.beats.forEach((b, i) => { const d = Math.abs(b.t - t); if (d < bd && b.el.style.opacity !== '0') { bd = d; best = i; } });
    if (best >= 0) { this.hb.beats[best].el.style.opacity = '0'; }
    return best;
  }
  updateHeartbeat() {
    if (!this.hb) return;
    const t = performance.now() / 1000 - this.hb.start;
    for (const b of this.hb.beats) {
      const x = 50 + (b.t - t) * 40;
      b.el.style.left = `${x}%`;
      if (x < -5 && b.el.style.opacity !== '0') b.el.style.background = '#444';
    }
  }
  endHeartbeat() {
    $(this.root, '#heartbeat').classList.remove('show');
    $(this.root, '#tBeat').style.display = 'none';
    this.hb = null;
  }

  // ---------------------------------------------------------- death / end
  showDeath(cause: string, revives: number) {
    const lines = DEATH_HINTS[cause] ?? ['You died.', 'The hotel claims another guest.'];
    const el = $(this.root, '#deathMsg');
    el.textContent = '';
    this.show('death');
    let i = 0;
    const full = lines.join('\n\n');
    const tick = () => { if (i <= full.length) { el.textContent = full.slice(0, i); i += 2; setTimeout(tick, 28); } };
    tick();
    $(this.root, '#deathInfo').textContent = revives > 0 ? 'You can revive once per run, at your party\'s location.' : 'Spectating your party.';
  }
  hideDeath() { this.screens.death.classList.remove('show'); }

  showEnd(win: boolean, door: number, stats: { name: string; door: number; gold: number; deaths: number; cause: string | null }[], knobs: number) {
    $(this.root, '#endTitle').textContent = win ? 'ESCAPED' : 'CHECKED OUT';
    $(this.root, '#endSub').textContent = win ? `You made it out of the hotel. +${knobs} knobs` : `The party reached door ${door}. +${knobs} knobs`;
    $(this.root, '#endStats').innerHTML = `<tr><th>Player</th><th>Door</th><th>Gold</th><th>Fate</th></tr>` + stats.map((s) => `<tr><td>${esc(s.name)}</td><td>${s.door}</td><td>${s.gold}</td><td>${s.cause ? esc(s.cause) : win ? 'escaped' : '—'}</td></tr>`).join('');
    this.show('end');
  }

  renderSettings(box: HTMLElement) {
    box.innerHTML = `
      <label>Mouse / look sensitivity <span id="sv">${settings.sensitivity.toFixed(2)}</span></label><input type="range" id="sSens" min="0.2" max="3" step="0.05" value="${settings.sensitivity}" />
      <label>Field of view <span id="fv">${settings.fov}</span></label><input type="range" id="sFov" min="60" max="100" step="1" value="${settings.fov}" />
      <label>Graphics quality</label><select id="sQ"><option value="low">Low (mobile)</option><option value="medium">Medium (laptop)</option><option value="high">High (desktop)</option></select>
      <label>Master volume</label><input type="range" id="sVol" min="0" max="1" step="0.05" value="${settings.master}" />
      <label>Music volume</label><input type="range" id="sMus" min="0" max="1" step="0.05" value="${settings.music}" />
      <div class="row" style="margin-top:8px"><label style="margin:0"><input type="checkbox" id="sInv" ${settings.invertY ? 'checked' : ''}/> Invert Y</label><label style="margin:0"><input type="checkbox" id="sSubs" ${settings.subtitles ? 'checked' : ''}/> Subtitles</label><label style="margin:0"><input type="checkbox" id="sFps" ${settings.showFps ? 'checked' : ''}/> FPS</label></div>
      <button class="btn small" id="bFull" style="margin-top:8px">Toggle fullscreen</button>`;
    const q = box.querySelector('#sQ') as HTMLSelectElement;
    q.value = settings.quality;
    const upd = () => {
      settings.sensitivity = Number((box.querySelector('#sSens') as HTMLInputElement).value);
      settings.fov = Number((box.querySelector('#sFov') as HTMLInputElement).value);
      settings.quality = q.value as typeof settings.quality;
      settings.master = Number((box.querySelector('#sVol') as HTMLInputElement).value);
      settings.music = Number((box.querySelector('#sMus') as HTMLInputElement).value);
      settings.invertY = (box.querySelector('#sInv') as HTMLInputElement).checked;
      settings.subtitles = (box.querySelector('#sSubs') as HTMLInputElement).checked;
      settings.showFps = (box.querySelector('#sFps') as HTMLInputElement).checked;
      (box.querySelector('#sv') as HTMLElement).textContent = settings.sensitivity.toFixed(2);
      (box.querySelector('#fv') as HTMLElement).textContent = String(settings.fov);
      saveSettings();
      this.h.settingsChanged();
    };
    box.querySelectorAll('input,select').forEach((el) => el.addEventListener('input', upd));
    (box.querySelector('#bFull') as HTMLElement).onclick = () => {
      if (document.fullscreenElement) document.exitFullscreen();
      else document.documentElement.requestFullscreen?.().catch(() => this.notice('Fullscreen is not available here'));
    };
  }

  showPause(on: boolean) {
    if (on) { this.renderSettings($(this.root, '#settingsBox')); this.screens.pause.classList.add('show'); }
    else this.screens.pause.classList.remove('show');
  }
}

export { SHAPES };
