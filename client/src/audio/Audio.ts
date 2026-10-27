import * as THREE from 'three';

type Surface = 'carpet' | 'wood' | 'tile' | 'stone' | 'grass' | 'concrete' | 'marble';

/**
 * Procedural WebAudio sound engine: every sound is synthesized at startup (original audio, no files).
 * Positional sounds use HRTF panners; a generated impulse response provides room reverb.
 */
export class Audio {
  ctx: AudioContext | null = null;
  private master!: GainNode;
  private sfx!: GainNode;
  private amb!: GainNode;
  private music!: GainNode;
  private reverb!: ConvolverNode;
  private wet!: GainNode;
  private buffers = new Map<string, AudioBuffer[]>();
  private loops = new Map<string, { src: AudioBufferSourceNode; gain: GainNode }>();
  private volume = { master: 0.8, sfx: 1, amb: 0.7, music: 0.6 };
  unlocked = false;
  private listenerPos = new THREE.Vector3();
  private chaseNodes: { stop: () => void } | null = null;

  /** Must be called from a user gesture (mobile autoplay policies). */
  async unlock() {
    if (!this.ctx) this.build();
    if (this.ctx!.state !== 'running') await this.ctx!.resume().catch(() => undefined);
    this.unlocked = this.ctx!.state === 'running';
  }

  setVolume(k: keyof Audio['volume'], v: number) {
    this.volume[k] = v;
    if (!this.ctx) return;
    this.master.gain.value = this.volume.master;
    this.sfx.gain.value = this.volume.sfx;
    this.amb.gain.value = this.volume.amb;
    this.music.gain.value = this.volume.music;
  }

  private build() {
    const AC = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    const ctx = (this.ctx = new AC({ latencyHint: 'interactive' }));
    const comp = ctx.createDynamicsCompressor();
    comp.threshold.value = -16; comp.ratio.value = 4;
    comp.connect(ctx.destination);
    this.master = ctx.createGain(); this.master.gain.value = this.volume.master; this.master.connect(comp);
    this.reverb = ctx.createConvolver();
    this.reverb.buffer = this.impulse(2.4, 2.8);
    this.wet = ctx.createGain(); this.wet.gain.value = 0.28;
    this.reverb.connect(this.wet); this.wet.connect(this.master);
    this.sfx = ctx.createGain(); this.sfx.connect(this.master); this.sfx.connect(this.reverb);
    this.amb = ctx.createGain(); this.amb.gain.value = this.volume.amb; this.amb.connect(this.master);
    this.music = ctx.createGain(); this.music.gain.value = this.volume.music; this.music.connect(this.master);
    this.synthAll();
  }

  setReverb(size: 'small' | 'medium' | 'large' | 'outdoor') {
    if (!this.ctx) return;
    this.wet.gain.setTargetAtTime(size === 'outdoor' ? 0.08 : size === 'small' ? 0.18 : size === 'medium' ? 0.28 : 0.42, this.ctx.currentTime, 0.4);
  }

  // ------------------------------------------------------------- synthesis helpers

  private impulse(sec: number, decay: number) {
    const ctx = this.ctx!;
    const n = Math.floor(ctx.sampleRate * sec);
    const b = ctx.createBuffer(2, n, ctx.sampleRate);
    for (let c = 0; c < 2; c++) {
      const d = b.getChannelData(c);
      for (let i = 0; i < n; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / n, decay) * (i < 400 ? i / 400 : 1);
    }
    return b;
  }

  private make(sec: number, fn: (t: number, i: number, sr: number) => number, stereo = false): AudioBuffer {
    const ctx = this.ctx!;
    const sr = ctx.sampleRate;
    const n = Math.max(1, Math.floor(sr * sec));
    const b = ctx.createBuffer(stereo ? 2 : 1, n, sr);
    const d = b.getChannelData(0);
    for (let i = 0; i < n; i++) d[i] = fn(i / sr, i, sr);
    if (stereo) { const d2 = b.getChannelData(1); for (let i = 0; i < n; i++) d2[i] = d[(i + 37) % n]; }
    // normalise
    let peak = 0;
    for (let i = 0; i < n; i++) peak = Math.max(peak, Math.abs(d[i]));
    if (peak > 0) { const k = 0.9 / peak; for (let c = 0; c < b.numberOfChannels; c++) { const dd = b.getChannelData(c); for (let i = 0; i < n; i++) dd[i] *= k; } }
    return b;
  }

  private add(name: string, ...bufs: AudioBuffer[]) { this.buffers.set(name, bufs); }

  private synthAll() {
    const noise = () => Math.random() * 2 - 1;
    const env = (t: number, a: number, d: number) => (t < a ? t / a : Math.exp(-(t - a) / d));
    // simple one-pole filtered noise generator
    const lp = (cut: number) => { let y = 0; return (x: number) => (y += (x - y) * cut); };
    const hp = (cut: number) => { let y = 0, px = 0; return (x: number) => { y = (1 - cut) * (y + x - px); px = x; return y; }; };

    const step = (surf: Surface, v: number) => {
      const f = lp(surf === 'carpet' ? 0.04 : surf === 'grass' ? 0.25 : 0.18);
      const h = hp(0.6);
      const pitch = surf === 'wood' ? 160 + v * 25 : surf === 'tile' || surf === 'marble' ? 900 + v * 60 : surf === 'concrete' || surf === 'stone' ? 420 : 90;
      return this.make(0.22, (t) => {
        const thump = Math.sin(2 * Math.PI * pitch * t) * env(t, 0.002, surf === 'wood' ? 0.05 : 0.02);
        const n = noise();
        const scuff = (surf === 'carpet' ? f(n) * 2 : surf === 'grass' ? h(f(n)) * 1.5 : f(n)) * env(t, 0.003, surf === 'carpet' ? 0.05 : 0.03);
        const creak = surf === 'wood' && v === 2 ? Math.sin(2 * Math.PI * (300 + Math.sin(t * 40) * 40) * t) * env(t, 0.03, 0.08) * 0.4 : 0;
        return thump * (surf === 'carpet' ? 0.5 : 0.8) + scuff + creak;
      });
    };
    for (const s of ['carpet', 'wood', 'tile', 'stone', 'grass', 'concrete', 'marble'] as Surface[]) this.add('step-' + s, step(s, 0), step(s, 1), step(s, 2));

    const creak = (len: number, f0: number) => this.make(len, (t) => {
      const f = f0 + Math.sin(t * 6) * 60 + Math.sin(t * 23) * 25;
      const s = Math.sin(2 * Math.PI * f * t + Math.sin(2 * Math.PI * f * 0.5 * t) * 2);
      return s * Math.sin(Math.PI * t / len) * (0.6 + Math.random() * 0.4) * 0.6 + noise() * 0.02;
    });
    this.add('doorOpen', this.make(1.1, (t) => {
      const latch = t < 0.04 ? noise() * env(t, 0.001, 0.01) * 2 : 0;
      const f = 240 + Math.sin(t * 5) * 80;
      const cr = Math.sin(2 * Math.PI * f * t + Math.sin(2 * Math.PI * 3 * t) * 3) * Math.sin(Math.PI * Math.min(1, t / 0.9)) * 0.5;
      return latch + cr * (t > 0.05 ? 1 : 0);
    }));
    this.add('creak', creak(1.4, 180), creak(1.0, 260), creak(1.8, 140));
    const slam = lp(0.1);
    this.add('doorSlam', this.make(0.9, (t) => slam(noise()) * env(t, 0.002, 0.12) * 3 + Math.sin(2 * Math.PI * 60 * t) * env(t, 0.001, 0.2)));
    const dr = lp(0.3);
    this.add('drawer', this.make(0.5, (t) => dr(noise()) * Math.sin(Math.PI * Math.min(1, t / 0.4)) * (1 + Math.sin(t * 90) * 0.3) + (t > 0.4 ? noise() * env(t - 0.4, 0.001, 0.02) : 0)));
    this.add('closet', creak(0.7, 320));
    this.add('locked', this.make(0.35, (t) => (Math.sin(2 * Math.PI * 1800 * t) * 0.3 + noise() * 0.6) * (env(t, 0.001, 0.02) + env(Math.max(0, t - 0.12), 0.001, 0.02) * (t > 0.12 ? 1 : 0))));
    this.add('unlock', this.make(0.5, (t) => Math.sin(2 * Math.PI * 2200 * t) * env(t, 0.001, 0.03) + (t > 0.2 ? Math.sin(2 * Math.PI * 1400 * t) * env(t - 0.2, 0.001, 0.05) + noise() * env(t - 0.2, 0.001, 0.02) : 0)));
    this.add('coins', this.make(0.6, (t) => { let s = 0; for (let k = 0; k < 6; k++) { const t0 = k * 0.07; if (t > t0) s += Math.sin(2 * Math.PI * (3000 + k * 431) * t) * env(t - t0, 0.001, 0.05); } return s; }));
    this.add('pickup', this.make(0.25, (t) => Math.sin(2 * Math.PI * (600 + t * 1200) * t) * env(t, 0.005, 0.06) * 0.6 + noise() * env(t, 0.001, 0.02) * 0.3));
    this.add('click', this.make(0.06, (t) => noise() * env(t, 0.0005, 0.008) + Math.sin(2 * Math.PI * 3000 * t) * env(t, 0.0005, 0.005)));
    this.add('lighter', this.make(0.5, (t) => (t < 0.05 ? noise() * 2 : 0) + noise() * 0.15 * env(Math.max(0, t - 0.05), 0.05, 0.3)));
    const pg = hp(0.3);
    this.add('page', this.make(0.4, (t) => pg(noise()) * Math.sin(Math.PI * t / 0.4) * (1 + Math.sin(t * 60))));
    this.add('lever', this.make(0.8, (t) => (t < 0.5 ? Math.sin(2 * Math.PI * 90 * t) * 0.3 + noise() * 0.2 : 0) + (t > 0.5 ? (noise() + Math.sin(2 * Math.PI * 70 * t)) * env(t - 0.5, 0.001, 0.08) * 2 : 0)));
    this.add('breaker', this.make(0.3, (t) => (noise() * 0.8 + Math.sin(2 * Math.PI * 120 * t)) * env(t, 0.001, 0.04)));
    this.add('glass', this.make(1.0, (t) => { let s = 0; for (let k = 0; k < 10; k++) { const t0 = k * 0.03 + Math.random() * 0.05; if (t > t0) s += Math.sin(2 * Math.PI * (2500 + Math.random() * 4000) * t) * env(t - t0, 0.001, 0.08); } return s + noise() * env(t, 0.001, 0.05); }));
    this.add('hit', this.make(0.4, (t) => (noise() * 0.7 + Math.sin(2 * Math.PI * 80 * t)) * env(t, 0.002, 0.08)));
    this.add('heartbeat', this.make(0.5, (t) => Math.sin(2 * Math.PI * 50 * t) * env(t, 0.005, 0.06) + Math.sin(2 * Math.PI * 45 * t) * env(Math.max(0, t - 0.2), 0.005, 0.06) * (t > 0.2 ? 0.8 : 0)));
    this.add('chime', this.make(2.0, (t) => (Math.sin(2 * Math.PI * 880 * t) + Math.sin(2 * Math.PI * 1320 * t) * 0.5 + Math.sin(2 * Math.PI * 1760 * t) * 0.3) * env(t, 0.01, 0.6)));
    this.add('elevatorDing', this.make(1.5, (t) => Math.sin(2 * Math.PI * 1047 * t) * env(t, 0.002, 0.5) + (t > 0.35 ? Math.sin(2 * Math.PI * 784 * t) * env(t - 0.35, 0.002, 0.6) : 0)));
    const sn = lp(0.2);
    this.add('snare', this.make(0.6, (t) => (noise() + Math.sin(2 * Math.PI * 200 * t)) * env(t, 0.001, 0.05) * 2 + sn(noise()) * env(t, 0.01, 0.2)));
    this.add('crash', this.make(2.0, (t) => { const g = noise() * env(t, 0.002, 0.4); let s = 0; for (let k = 0; k < 8; k++) s += Math.sin(2 * Math.PI * (2000 + k * 777) * t) * env(t, 0.001, 0.3 + k * 0.05); return g + s * 0.3 + Math.sin(2 * Math.PI * 50 * t) * env(t, 0.001, 0.3); }));
    this.add('death', this.make(2.5, (t) => (Math.sin(2 * Math.PI * (120 - t * 30) * t) * 0.5 + noise() * 0.3) * env(t, 0.01, 0.9) + Math.sin(2 * Math.PI * 55 * t) * env(t, 0.01, 1.5)));

    // ---- monsters
    const rush = lp(0.08), rush2 = lp(0.02);
    this.add('rush', this.make(4, (t) => {
      const n = noise();
      const roar = rush(n) * 2.5 + rush2(n) * 4;
      const mod = 0.7 + Math.sin(t * 50) * 0.3;
      const scream = Math.sin(2 * Math.PI * (180 + Math.sin(t * 9) * 30) * t) * 0.25;
      const d = Math.tanh((roar * mod + scream) * 2);
      return d * Math.min(1, t * 2) * Math.min(1, (4 - t) * 2);
    }));
    const am = lp(0.15);
    this.add('ambush', this.make(3, (t) => {
      const f = 420 + Math.sin(t * 28) * 120 + Math.sin(t * 3) * 80;
      const s = (Math.sin(2 * Math.PI * f * t) + Math.sin(2 * Math.PI * f * 1.5 * t) * 0.5) * 0.6;
      return Math.tanh((s + am(noise()) * 2) * 2) * Math.min(1, t * 3) * Math.min(1, (3 - t) * 2);
    }));
    this.add('seekRoar', this.make(2.2, (t) => Math.tanh((Math.sin(2 * Math.PI * (90 + Math.sin(t * 12) * 20) * t) + noise() * 0.6) * 3) * env(t, 0.1, 0.9)));
    const fg = lp(0.05);
    this.add('figureGrowl', this.make(2.0, (t) => Math.tanh((fg(noise()) * 6 + Math.sin(2 * Math.PI * (70 + Math.sin(t * 7) * 10) * t) * 0.5) * 2) * Math.sin(Math.PI * t / 2)));
    this.add('figureRoar', this.make(3.0, (t) => Math.tanh((Math.sin(2 * Math.PI * (130 - t * 20) * t) * 0.8 + noise() * 0.7 + Math.sin(2 * Math.PI * 65 * t) * 0.5) * 3) * env(t, 0.15, 1.2)));
    this.add('figureStep', this.make(0.5, (t) => (Math.sin(2 * Math.PI * 45 * t) * 1.2 + fg(noise()) * 3) * env(t, 0.003, 0.08)));
    this.add('psst', this.make(0.5, (t) => { const h = noise(); return h * (t < 0.08 ? 0.6 : t < 0.35 ? 1.0 : 0) * (1 + Math.sin(t * 900) * 0.2) * env(t, 0.01, 0.2); }));
    this.add('screech', this.make(1.2, (t) => Math.tanh((Math.sin(2 * Math.PI * (900 + Math.sin(t * 60) * 300) * t) + noise() * 0.8) * 3) * env(t, 0.005, 0.4)));
    this.add('eyesHum', this.make(3, (t) => (Math.sin(2 * Math.PI * 220 * t) + Math.sin(2 * Math.PI * 221.5 * t) + Math.sin(2 * Math.PI * 330.7 * t) * 0.5) * 0.4 * (0.7 + Math.sin(t * 2.0) * 0.3), true));
    this.add('haltDrone', this.make(3, (t) => (Math.sin(2 * Math.PI * 55 * t) + Math.sin(2 * Math.PI * 110.3 * t) * 0.5 + noise() * 0.05) * 0.5, true));
    this.add('haltWhoosh', this.make(1.2, (t) => { const f = lp(0.05 + t * 0.2); return f(noise()) * 4 * Math.sin(Math.PI * t / 1.2); }));
    this.add('whisper', this.make(1.6, (t) => { const h = hp(0.5); return h(noise()) * (0.5 + Math.sin(t * 14) * 0.5) * Math.sin(Math.PI * t / 1.6); }));
    this.add('jack', this.make(1.0, (t) => Math.tanh((Math.sin(2 * Math.PI * (600 - t * 300) * t) + noise()) * 4) * env(t, 0.003, 0.3)));
    this.add('glitch', this.make(0.8, (t) => (Math.floor(t * 60) % 2 ? 1 : -1) * Math.random() * env(t, 0.001, 0.3) + Math.sin(2 * Math.PI * 1000 * Math.floor(t * 30) / 30 * t) * 0.3 * env(t, 0.001, 0.3)));
    this.add('dupe', this.make(1.0, (t) => Math.tanh((Math.sin(2 * Math.PI * 150 * t) * Math.sin(2 * Math.PI * 7 * t) + noise() * 0.5) * 3) * env(t, 0.01, 0.35)));
    this.add('timothy', this.make(0.6, (t) => noise() * (Math.sin(t * 160) > 0 ? 1 : 0.2) * env(t, 0.001, 0.2) + Math.sin(2 * Math.PI * 1500 * t) * env(t, 0.001, 0.1)));

    // ---- ambience loops
    const rn = lp(0.6);
    this.add('rain', this.make(6, (t) => rn(noise()) * 0.35 + (Math.random() > 0.9993 ? 1 : 0) * Math.random() + noise() * 0.05, true));
    const hum = lp(0.01);
    this.add('roomtone', this.make(6, (t) => hum(noise()) * 6 + Math.sin(2 * Math.PI * 50 * t) * 0.04, true));
    this.add('electric', this.make(4, (t) => (Math.sin(2 * Math.PI * 60 * t) + Math.sin(2 * Math.PI * 120 * t) * 0.6 + Math.sin(2 * Math.PI * 180 * t) * 0.3) * 0.3 + noise() * 0.02 * (Math.random() > 0.98 ? 6 : 1), true));
    const wd = lp(0.008);
    this.add('wind', this.make(6, (t) => wd(noise()) * 8 * (0.6 + Math.sin(t * 0.8) * 0.4), true));
    const fc = hp(0.3);
    this.add('fire', this.make(3, (t) => fc(noise()) * 0.3 * (Math.random() > 0.995 ? 4 : 1) + noise() * 0.05, true));
    this.add('drip', this.make(0.3, (t) => Math.sin(2 * Math.PI * (1200 - t * 2000) * t) * env(t, 0.001, 0.03)));
    this.add('thunder', this.make(4, (t) => { const f = lp(0.01 + 0.03 * Math.exp(-t)); return f(noise()) * 12 * env(t, 0.05, 1.2); }));
    // music box for Jeff's shop
    const notes = [523, 659, 784, 659, 698, 587, 523, 494];
    this.add('musicbox', this.make(4, (t) => { const k = Math.floor(t / 0.5) % notes.length; const lt = t % 0.5; return (Math.sin(2 * Math.PI * notes[k] * t) + Math.sin(2 * Math.PI * notes[k] * 2 * t) * 0.3) * env(lt, 0.002, 0.25) * 0.5; }, true));
  }

  // ------------------------------------------------------------- playback

  setListener(cam: THREE.Camera) {
    if (!this.ctx) return;
    const l = this.ctx.listener;
    const p = cam.position;
    this.listenerPos.copy(p);
    const f = new THREE.Vector3(0, 0, -1).applyQuaternion(cam.quaternion);
    const u = new THREE.Vector3(0, 1, 0).applyQuaternion(cam.quaternion);
    if (l.positionX) {
      const t = this.ctx.currentTime;
      l.positionX.setValueAtTime(p.x, t); l.positionY.setValueAtTime(p.y, t); l.positionZ.setValueAtTime(p.z, t);
      l.forwardX.setValueAtTime(f.x, t); l.forwardY.setValueAtTime(f.y, t); l.forwardZ.setValueAtTime(f.z, t);
      l.upX.setValueAtTime(u.x, t); l.upY.setValueAtTime(u.y, t); l.upZ.setValueAtTime(u.z, t);
    } else {
      (l as unknown as { setPosition: (x: number, y: number, z: number) => void }).setPosition(p.x, p.y, p.z);
      (l as unknown as { setOrientation: (...a: number[]) => void }).setOrientation(f.x, f.y, f.z, u.x, u.y, u.z);
    }
  }

  /** Play a one-shot. With `pos`, it is spatialized. Returns a handle to move/stop it. */
  play(name: string, opts: { pos?: { x: number; y: number; z: number }; vol?: number; rate?: number; bus?: 'sfx' | 'amb' | 'music'; refDist?: number; loop?: boolean } = {}) {
    if (!this.ctx || !this.unlocked) return null;
    const bufs = this.buffers.get(name);
    if (!bufs) return null;
    const ctx = this.ctx;
    const src = ctx.createBufferSource();
    src.buffer = bufs[Math.floor(Math.random() * bufs.length)];
    src.playbackRate.value = (opts.rate ?? 1) * (0.95 + Math.random() * 0.1);
    src.loop = !!opts.loop;
    const g = ctx.createGain();
    g.gain.value = opts.vol ?? 1;
    let panner: PannerNode | null = null;
    src.connect(g);
    if (opts.pos) {
      panner = ctx.createPanner();
      panner.panningModel = 'HRTF';
      panner.distanceModel = 'inverse';
      panner.refDistance = opts.refDist ?? 1.5;
      panner.rolloffFactor = 1.2;
      panner.maxDistance = 60;
      panner.positionX.value = opts.pos.x; panner.positionY.value = opts.pos.y; panner.positionZ.value = opts.pos.z;
      g.connect(panner);
      panner.connect(opts.bus === 'amb' ? this.amb : this.sfx);
    } else g.connect(opts.bus === 'amb' ? this.amb : opts.bus === 'music' ? this.music : this.sfx);
    src.start();
    return {
      setPos: (p: { x: number; y: number; z: number }) => { if (panner) { const t = ctx.currentTime; panner.positionX.setTargetAtTime(p.x, t, 0.03); panner.positionY.setTargetAtTime(p.y, t, 0.03); panner.positionZ.setTargetAtTime(p.z, t, 0.03); } },
      stop: (fade = 0.2) => { const t = ctx.currentTime; g.gain.setTargetAtTime(0, t, fade / 3); src.stop(t + fade); },
      gain: g,
    };
  }

  /** Ambient loop by name with crossfade. */
  loop(name: string, vol: number) {
    if (!this.ctx || !this.unlocked) return;
    const ex = this.loops.get(name);
    if (vol <= 0.001) {
      if (ex) { ex.gain.gain.setTargetAtTime(0, this.ctx.currentTime, 0.5); const s = ex.src; setTimeout(() => s.stop(), 2500); this.loops.delete(name); }
      return;
    }
    if (ex) { ex.gain.gain.setTargetAtTime(vol, this.ctx.currentTime, 0.5); return; }
    const bufs = this.buffers.get(name);
    if (!bufs) return;
    const src = this.ctx.createBufferSource();
    src.buffer = bufs[0]; src.loop = true;
    const g = this.ctx.createGain(); g.gain.value = 0;
    src.connect(g); g.connect(name === 'musicbox' ? this.music : this.amb);
    g.gain.setTargetAtTime(vol, this.ctx.currentTime, 0.6);
    src.start();
    this.loops.set(name, { src, gain: g });
  }

  /** Procedural chase music: pounding drums + dissonant pulse while Seek hunts. */
  chaseMusic(on: boolean) {
    if (!this.ctx || !this.unlocked) return;
    if (!on) { this.chaseNodes?.stop(); this.chaseNodes = null; return; }
    if (this.chaseNodes) return;
    const ctx = this.ctx;
    const out = ctx.createGain(); out.gain.value = 0.55; out.connect(this.music);
    const kick = this.make(0.3, (t) => Math.sin(2 * Math.PI * (120 * Math.exp(-t * 20) + 45) * t) * Math.exp(-t * 10));
    const stab = this.make(0.25, (t) => (Math.sin(2 * Math.PI * 233 * t) + Math.sin(2 * Math.PI * 247 * t) + Math.sin(2 * Math.PI * 349 * t)) * Math.exp(-t * 9) * 0.4);
    let beat = 0, alive = true;
    const tick = () => {
      if (!alive) return;
      const t = ctx.currentTime + 0.05;
      const k = ctx.createBufferSource(); k.buffer = kick; k.connect(out); k.start(t);
      if (beat % 2 === 1) { const s = ctx.createBufferSource(); s.buffer = stab; s.playbackRate.value = beat % 8 === 7 ? 1.12 : 1; s.connect(out); s.start(t); }
      beat++;
      setTimeout(tick, 260);
    };
    tick();
    this.chaseNodes = { stop: () => { alive = false; out.gain.setTargetAtTime(0, ctx.currentTime, 0.4); setTimeout(() => out.disconnect(), 2000); } };
  }

  step(surface: Surface, pos: { x: number; y: number; z: number }, vol: number) {
    this.play('step-' + surface, { pos, vol, refDist: 1 });
  }
}

export type { Surface };
