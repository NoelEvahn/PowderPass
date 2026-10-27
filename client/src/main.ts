import type { S2C } from '@doors/shared';
import { Renderer } from './render/Renderer';
import { Net } from './net/Net';
import { UI } from './ui/UI';
import { Input } from './input/Input';
import { Audio } from './audio/Audio';
import { Game } from './game/Game';
import { settings, saveSettings } from './settings';

const canvas = document.getElementById('game') as HTMLCanvasElement;
const uiRoot = document.getElementById('ui')!;

let renderer: Renderer;
try {
  renderer = new Renderer(canvas, settings.quality);
} catch (e) {
  uiRoot.innerHTML = `<div style="position:fixed;inset:0;display:flex;align-items:center;justify-content:center;color:#e8dcc4;font-family:sans-serif;text-align:center;padding:20px;pointer-events:auto">This game needs WebGL, which is unavailable in this browser.<br/>${String((e as Error).message)}</div>`;
  throw e;
}
const net = new Net();
const audio = new Audio();
let inGame = false;
let paused = false;

const ui = new UI(uiRoot, {
  quickPlay: () => { ensureName(); net.send({ t: 'quickJoin' }); },
  createPrivate: () => { ensureName(); net.send({ t: 'createLobby', private: true }); },
  joinCode: (code) => { ensureName(); net.send({ t: 'joinLobby', code }); },
  joinLobby: (code) => { ensureName(); net.send({ t: 'joinLobby', code }); },
  refreshLobbies: () => net.send({ t: 'listLobbies' }),
  leaveLobby: () => net.send({ t: 'leaveLobby' }),
  ready: (r) => { void audio.unlock(); net.send({ t: 'ready', ready: r }); },
  startNow: () => net.send({ t: 'start' }),
  equip: (slot) => net.send({ t: 'equip', slot }),
  buy: (item) => net.send({ t: 'buy', item }),
  closeShop: () => game.closeShop(),
  submitCode: (code) => net.send({ t: 'code', code }),
  closePadlock: () => { ui.closeModals(); input.lock(); },
  toggleBreaker: (n) => net.send({ t: 'breakerToggle', n }),
  closeBreaker: () => { ui.closeModals(); input.lock(); },
  revive: () => net.send({ t: 'revive' }),
  backToLobby: () => { endGame(); ui.show('lobby'); },
  resume: () => setPaused(false),
  leaveGame: () => { setPaused(false); endGame(); net.send({ t: 'leaveLobby' }); ui.show('menu'); },
  settingsChanged: () => {
    audio.setVolume('master', settings.master);
    audio.setVolume('music', settings.music);
    if (renderer.preset.name !== settings.quality) { renderer.setQuality(settings.quality); if (inGame) ui.notice('Quality applies fully on the next run.'); }
  },
  nameChanged: () => undefined,
});
const input = new Input(canvas, uiRoot);
const game = new Game(renderer, net, ui, input, audio);

function ensureName() {
  void audio.unlock();
  if (!settings.name) { settings.name = 'Guest' + Math.floor(Math.random() * 900 + 100); saveSettings(); }
}

function setPaused(p: boolean) {
  paused = p;
  ui.showPause(p);
  if (p) input.unlock(); else input.lock();
}

function endGame() {
  inGame = false;
  input.enabled = false;
  game.stop();
  ui.showHud(false, input.touch);
  input.unlock();
}

input.on((a) => {
  if (a !== 'menu' || !inGame) return;
  if (ui.modalOpen) { if (ui.modalOpen === 'shop') game.closeShop(); else ui.closeModals(); input.lock(); return; }
  setPaused(!paused);
});
document.addEventListener('pointerlockchange', () => {
  if (inGame && !input.locked && !ui.modalOpen && !paused && !input.touch) setPaused(true);
});

net.onStatus = (s) => ui.setNet(s);
net.on(async (m: S2C) => {
  switch (m.t) {
    case 'welcome': ui.setProfile(m.knobs, m.stats); net.send({ t: 'listLobbies' }); autoJoin(); break;
    case 'lobbyList': ui.renderLobbyList(m.lobbies); break;
    case 'lobby':
      if (inGame) break;
      ui.renderLobby(m.code, m.private, m.players, m.countdown, m.max, m.you);
      ui.show('lobby');
      if (m.countdown !== null) audio.play('click', { vol: 0.3 });
      break;
    case 'leftLobby': ui.show('menu'); net.send({ t: 'listLobbies' }); break;
    case 'error': ui.notice(m.msg); alertMenu(m.msg); break;
    case 'start':
      ui.show('loading');
      await audio.unlock();
      try {
        await game.start(m);
      } catch (e) {
        console.error(e);
        ui.notice('Failed to start: ' + (e as Error).message);
        ui.show('menu');
        return;
      }
      inGame = true;
      input.enabled = true;
      ui.hideScreens();
      ui.showHud(true, input.touch);
      input.lock();
      break;
    case 'end':
      ui.showEnd(m.result === 'win', m.door, m.stats, m.knobs);
      input.enabled = false;
      input.unlock();
      break;
    default: game.handle(m);
  }
});

function alertMenu(msg: string) {
  const box = document.querySelector('#menu .panel');
  if (!box || !document.getElementById('menu')!.classList.contains('show')) return;
  let el = box.querySelector('.err') as HTMLElement | null;
  if (!el) { el = document.createElement('div'); el.className = 'err muted'; el.style.color = '#e88'; box.prepend(el); }
  el.textContent = msg;
}

let autoJoined = false;
function autoJoin() {
  const code = new URLSearchParams(location.search).get('join');
  if (code && !autoJoined) { autoJoined = true; ensureName(); net.send({ t: 'joinLobby', code: code.toUpperCase() }); }
}

ui.show('menu');
net.connect(settings.name || 'Guest');
audio.setVolume('master', settings.master);
audio.setVolume('music', settings.music);
// unlock audio on the first gesture anywhere (mobile autoplay rules)
const unlockOnce = () => { void audio.unlock(); window.removeEventListener('pointerdown', unlockOnce); window.removeEventListener('keydown', unlockOnce); };
window.addEventListener('pointerdown', unlockOnce);
window.addEventListener('keydown', unlockOnce);

let last = performance.now();
function loop(now: number) {
  const dt = Math.min(0.05, (now - last) / 1000);
  last = now;
  if (inGame) game.frame(dt);
  renderer.render(dt);
  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);

// test / debugging hook (read-only state for automated browser tests)
(window as unknown as Record<string, unknown>).__doors = {
  get inGame() { return inGame; },
  get room() { return game.room; },
  get pos() { return { x: game.pos.x, y: game.pos.y, z: game.pos.z }; },
  get self() { return game.self; },
  get fps() { return renderer.fps; },
  get calls() { return renderer.renderer.info.render.calls; },
  get doorsOpen() { return game.world ? [...game.world.doorsOpen] : []; },
  get remotes() { return (game as unknown as { remotes: Map<string, { last: unknown }> }).remotes.size; },
  get netId() { return net.id; },
  game,
  net,
};
