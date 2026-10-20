// Small procedural WebAudio sound effects (no external assets).
import { BLOCKS } from './blocks.js';

export class Audio {
  constructor(game) {
    this.game = game;
    this.ctx = null;
    this.volume = 0.5;
    this.noiseBuf = null;
    this.last = {};
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
