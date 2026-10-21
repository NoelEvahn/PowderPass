// Small procedural WebAudio sound effects (no external assets).
import { BLOCKS } from './blocks.js';
import { putAsset, getAsset, deleteAsset } from './storage.js';

// Minecraft sound event names (from the player's own assets folder) for each in-game event.
const MC_SOUNDS = {
  step: (m) => `step/${m}`, dig: (m) => `dig/${m}`, place: (m) => `dig/${m}`, break: (m) => (m === 'glass' ? 'random/glass' : `dig/${m}`),
  hurt_player: () => 'damage/hit', fall: () => 'damage/fallbig', swim: () => 'liquid/swim', splash: () => 'liquid/splash',
  fizz: () => 'random/fizz', explode: () => 'random/explode', fuse: () => 'random/fuse', eat: () => 'random/eat', burp: () => 'random/burp',
  pop: () => 'random/pop', click: () => 'random/click', bucket_fill: () => 'item/bucket/fill', bucket_empty: () => 'item/bucket/empty',
  flint: () => 'fire/ignite', portal_ignite: () => 'fire/ignite', portal_ambient: () => 'portal/portal', travel: () => 'portal/travel',
  bow: () => 'random/bow', hit: () => 'random/bowhit', break_tool: () => 'random/break', levelup: () => 'random/levelup',
  hurt: () => 'damage/hit',
  mob_pig: () => 'mob/pig/say', mob_cow: () => 'mob/cow/say', mob_sheep: () => 'mob/sheep/say', mob_chicken: () => 'mob/chicken/say',
  mob_zombie: () => 'mob/zombie/say', mob_skeleton: () => 'mob/skeleton/say', mob_villager: () => 'mob/villager/idle',
  mob_piglin: () => 'mob/zombified_piglin/ambient', hurt_pig: () => 'mob/pig/say', hurt_cow: () => 'mob/cow/hurt', hurt_sheep: () => 'mob/sheep/say',
  hurt_chicken: () => 'mob/chicken/hurt', hurt_zombie: () => 'mob/zombie/hurt', hurt_skeleton: () => 'mob/skeleton/hurt', hurt_creeper: () => 'mob/creeper/say',
  hurt_villager: () => 'mob/villager/hit', hurt_piglin: () => 'mob/zombified_piglin/hurt',
};
const MC_MAT = { stone: 'stone', wood: 'wood', grass: 'grass', sand: 'sand', gravel: 'gravel', cloth: 'cloth', glass: 'stone' };

export class Audio {
  constructor(game) {
    this.game = game;
    this.ctx = null;
    this.volume = 0.5;
    this.noiseBuf = null;
    this.last = {};
    this.mc = null; // { sounds: {prefix: [blob...]}, music: {game:[], creative:[], nether:[], menu:[]} }
    this.decoded = new Map();
    this.musicVolume = 0.35;
    this.musicTimer = 20;
    this.musicSrc = null;
  }

  // ---- real sounds from the user's own Minecraft install ----
  // files: FileList from a directory picker pointed at .minecraft/assets
  async loadMinecraftAssets(files, onProgress) {
    const byPath = new Map();
    let indexFiles = [];
    for (const f of files) {
      const p = (f.webkitRelativePath || f.name).replace(/\\/g, '/');
      const m = p.match(/(?:^|\/)objects\/([0-9a-f]{2})\/([0-9a-f]{40})$/);
      if (m) byPath.set(m[2], f);
      else if (/(?:^|\/)indexes\/[^/]+\.json$/.test(p)) indexFiles.push(f);
    }
    if (!indexFiles.length) throw new Error('No indexes/*.json found — pick the ".minecraft/assets" folder');
    // newest index (largest object count) wins
    let best = null;
    for (const f of indexFiles) {
      try {
        const j = JSON.parse(await f.text());
        if (j.objects && (!best || Object.keys(j.objects).length > Object.keys(best).length)) best = j.objects;
      } catch { /* skip */ }
    }
    if (!best) throw new Error('Could not read the asset index');
    const wantedSounds = new Set();
    for (const fn of Object.values(MC_SOUNDS)) for (const m of Object.values(MC_MAT)) wantedSounds.add(fn(m));
    const sounds = {}, music = { game: [], creative: [], nether: [], menu: [] };
    const entries = Object.entries(best).filter(([k]) => k.startsWith('minecraft/sounds/') && k.endsWith('.ogg'));
    let done = 0;
    let musicBytes = 0;
    for (const [k, v] of entries) {
      const rel = k.slice('minecraft/sounds/'.length, -4);
      const file = byPath.get(v.hash);
      if (!file) continue;
      const prefix = rel.replace(/\d+$/, '');
      if (wantedSounds.has(prefix)) {
        (sounds[prefix] = sounds[prefix] || []).push(await file.arrayBuffer());
      } else if (rel.startsWith('music/')) {
        const cat = rel.startsWith('music/game/creative') ? 'creative' : rel.startsWith('music/game/nether') ? 'nether' : rel.startsWith('music/menu') ? 'menu' : rel.startsWith('music/game') ? 'game' : null;
        if (cat && musicBytes < 150e6) { music[cat].push(await file.arrayBuffer()); musicBytes += file.size; }
      }
      if (++done % 50 === 0 && onProgress) onProgress(done / entries.length);
    }
    const nSounds = Object.keys(sounds).length;
    const nMusic = Object.values(music).reduce((a, b) => a + b.length, 0);
    if (!nSounds && !nMusic) throw new Error('No sound files found in that folder');
    this.mc = { sounds, music };
    this.decoded.clear();
    await putAsset('mc_audio', this.mc);
    return { nSounds, nMusic };
  }
  async restoreMinecraftAssets() {
    const a = await getAsset('mc_audio');
    if (a && a.sounds) this.mc = a;
    return !!this.mc;
  }
  async clearMinecraftAssets() { this.mc = null; this.decoded.clear(); this.stopMusic(); await deleteAsset('mc_audio'); }

  decode(buf) {
    if (this.decoded.has(buf)) return this.decoded.get(buf);
    const p = this.ctx.decodeAudioData(buf.slice(0)).catch(() => null);
    this.decoded.set(buf, p);
    return p;
  }
  async playBuffer(buf, gain, rate = 1) {
    const ab = await this.decode(buf);
    if (!ab) return null;
    const src = this.ctx.createBufferSource();
    src.buffer = ab;
    src.playbackRate.value = rate;
    const g = this.ctx.createGain();
    g.gain.value = gain;
    src.connect(g); g.connect(this.master);
    src.start();
    return { src, g };
  }
  playMC(name, pos, mat) {
    const fn = MC_SOUNDS[name];
    if (!fn) return false;
    const list = this.mc.sounds[fn(MC_MAT[mat] || 'stone')];
    if (!list || !list.length) return false;
    const buf = list[(Math.random() * list.length) | 0];
    const base = name === 'step' ? 0.25 : name === 'dig' ? 0.35 : name === 'explode' ? 1 : name.startsWith('mob_') ? 0.6 : 0.7;
    const pitch = name === 'step' || name === 'dig' ? 0.9 + Math.random() * 0.2 : name === 'place' ? 0.8 : 1;
    this.playBuffer(buf, base * this.volume * this.atten(pos), pitch);
    return true;
  }

  // background music: picks a random track every few minutes, cross-fading
  updateMusic(dt, context) {
    if (!this.mc || !this.ctx || this.ctx.state !== 'running' || this.musicVolume <= 0) return;
    const list = this.mc.music[context] && this.mc.music[context].length ? this.mc.music[context] : this.mc.music.game;
    if (this.musicContext !== context && this.musicSrc) { this.stopMusic(); this.musicTimer = 3; }
    this.musicContext = context;
    if (this.musicSrc) return;
    this.musicTimer -= dt;
    if (this.musicTimer > 0 || !list || !list.length) return;
    this.musicTimer = 1e9;
    const buf = list[(Math.random() * list.length) | 0];
    this.playBuffer(buf, 0).then((h) => {
      if (!h) { this.musicTimer = 30; return; }
      this.musicSrc = h;
      h.g.gain.linearRampToValueAtTime(this.musicVolume, this.ctx.currentTime + 4);
      h.src.onended = () => { if (this.musicSrc === h) { this.musicSrc = null; this.musicTimer = 60 + Math.random() * 180; } };
    });
  }
  stopMusic() {
    if (!this.musicSrc) return;
    const h = this.musicSrc;
    this.musicSrc = null;
    try { h.g.gain.linearRampToValueAtTime(0, this.ctx.currentTime + 2); h.src.stop(this.ctx.currentTime + 2.1); } catch { /* ignore */ }
  }
  setMusicVolume(v) {
    this.musicVolume = v;
    if (this.musicSrc) this.musicSrc.g.gain.value = v;
  }
  ensure() {
    if (this.ctx) return true;
    try {
      this.ctx = new (window.AudioContext || window.webkitAudioContext)();
      const len = this.ctx.sampleRate * 1;
      this.noiseBuf = this.ctx.createBuffer(1, len, this.ctx.sampleRate);
      const d = this.noiseBuf.getChannelData(0);
      for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
      this.master = this.ctx.createGain();
      this.master.connect(this.ctx.destination);
    } catch {
      return false;
    }
    return true;
  }
  resume() {
    if (this.ensure() && this.ctx.state === 'suspended') this.ctx.resume();
  }
  material(id) {
    const b = BLOCKS[id];
    if (!b) return 'stone';
    const k = b.key;
    if (k.includes('leaves') || k.includes('grass') || k === 'tall_grass' || b.render === 'cross') return 'grass';
    if (k.includes('sand') || k === 'snow' || k === 'snowy_grass') return 'sand';
    if (k.includes('gravel') || k === 'dirt' || k === 'clay') return 'gravel';
    if (k.includes('log') || k.includes('planks') || k.includes('table') || k === 'chest' || k === 'bookshelf') return 'wood';
    if (k.includes('wool')) return 'cloth';
    if (k === 'glass' || k.includes('ice') || k === 'glowstone') return 'glass';
    return 'stone';
  }
  noise(dur, freq, q, gain, type = 'bandpass', pos) {
    const c = this.ctx;
    const src = c.createBufferSource();
    src.buffer = this.noiseBuf;
    src.playbackRate.value = 0.8 + Math.random() * 0.4;
    const f = c.createBiquadFilter();
    f.type = type;
    f.frequency.value = freq;
    f.Q.value = q;
    const g = c.createGain();
    const v = gain * this.volume * this.atten(pos);
    g.gain.setValueAtTime(v, c.currentTime);
    g.gain.exponentialRampToValueAtTime(0.0001, c.currentTime + dur);
    src.connect(f); f.connect(g); g.connect(this.master);
    src.start(c.currentTime, Math.random() * 0.5);
    src.stop(c.currentTime + dur + 0.05);
  }
  tone(freq, dur, gain, type = 'sine', slide = 0, pos) {
    const c = this.ctx;
    const o = c.createOscillator();
    o.type = type;
    o.frequency.setValueAtTime(freq, c.currentTime);
    if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(20, freq * slide), c.currentTime + dur);
    const g = c.createGain();
    const v = gain * this.volume * this.atten(pos);
    g.gain.setValueAtTime(v, c.currentTime);
    g.gain.exponentialRampToValueAtTime(0.0001, c.currentTime + dur);
    o.connect(g); g.connect(this.master);
    o.start();
    o.stop(c.currentTime + dur + 0.05);
  }
  atten(pos) {
    if (!pos) return 1;
    const p = this.game.player.body.pos;
    const d = Math.hypot(pos.x - p.x, pos.y - p.y, pos.z - p.z);
    return Math.max(0, 1 - d / 24);
  }
  play(name, pos, id) {
    if (!this.ctx || this.ctx.state !== 'running' || this.volume <= 0) return;
    const now = performance.now();
    if (this.last[name] && now - this.last[name] < 40) return;
    this.last[name] = now;
    const m = id !== undefined ? this.material(id) : 'stone';
    if (this.mc && this.playMC(name, pos, m)) return;
    if (name.startsWith('mob_')) return;
    if (name.startsWith('hurt_') && name !== 'hurt_player') name = 'hurt';
    const MAT = { stone: [1800, 1.2], wood: [700, 2], grass: [2600, 0.8], sand: [3500, 0.6], gravel: [1200, 0.7], cloth: [900, 0.5], glass: [3000, 3] };
    const [f, q] = MAT[m];
    switch (name) {
      case 'step': this.noise(0.08, f * 0.8, q, 0.12, 'bandpass', pos); break;
      case 'dig': this.noise(0.09, f, q, 0.25, 'bandpass', pos); break;
      case 'place': this.noise(0.12, f * 0.7, q, 0.4, 'bandpass', pos); break;
      case 'break':
        this.noise(0.25, f * 0.6, q, 0.55, 'bandpass', pos);
        if (m === 'glass') { this.tone(2400, 0.15, 0.1, 'triangle', 0.6, pos); this.tone(3100, 0.2, 0.08, 'triangle', 0.7, pos); }
        break;
      case 'pop': this.tone(900 + Math.random() * 500, 0.06, 0.15, 'sine', 1.6); break;
      case 'hurt': this.tone(320, 0.18, 0.25, 'square', 0.6, pos); break;
      case 'hurt_player': this.tone(220, 0.2, 0.3, 'sawtooth', 0.7); this.noise(0.1, 600, 1, 0.2); break;
      case 'fall': this.noise(0.15, 300, 1, 0.5, 'lowpass'); break;
      case 'swim': this.noise(0.35, 900, 0.6, 0.12, 'lowpass'); break;
      case 'splash': this.noise(0.5, 1200, 0.5, 0.4, 'lowpass', pos); break;
      case 'fizz': this.noise(0.6, 5000, 0.5, 0.25, 'highpass', pos); break;
      case 'explode': this.noise(1.4, 140, 0.4, 1.0, 'lowpass', pos); this.tone(60, 1.0, 0.6, 'sine', 0.5, pos); break;
      case 'fuse': this.noise(1.2, 6000, 0.8, 0.15, 'highpass', pos); break;
      case 'eat': this.noise(0.08, 1500, 1.5, 0.2); break;
      case 'burp': this.tone(160, 0.25, 0.2, 'sawtooth', 0.8); break;
      case 'bucket_fill': this.noise(0.4, 800, 0.7, 0.3, 'lowpass'); break;
      case 'bucket_empty': this.noise(0.4, 1100, 0.7, 0.3, 'lowpass'); break;
      case 'flint': this.noise(0.08, 4000, 3, 0.3, 'bandpass'); break;
      case 'portal_ignite': this.tone(200, 1.2, 0.3, 'sawtooth', 3, pos); break;
      case 'portal_ambient': this.tone(90 + Math.random() * 40, 0.8, 0.04, 'sine', 1.5); break;
      case 'travel': this.tone(120, 2.5, 0.3, 'sawtooth', 4); this.noise(2, 400, 0.5, 0.2, 'lowpass'); break;
      case 'click': this.tone(1200, 0.04, 0.12, 'square', 0.9); break;
      case 'bow': this.noise(0.12, 2500, 2, 0.25, 'bandpass', pos); break;
      case 'hit': this.noise(0.08, 1800, 2, 0.2, 'bandpass', pos); break;
      case 'break_tool': this.tone(800, 0.3, 0.3, 'square', 0.3); break;
      case 'levelup': this.tone(600, 0.3, 0.15, 'triangle', 1.5); break;
    }
  }
}
