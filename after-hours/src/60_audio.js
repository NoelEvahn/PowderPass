// ───────────────────────── audio: procedural score + synthesized sound design ─────────────────────────
// Every sound is an event at a film time. On play/seek a fresh "session" bus is created and events are
// scheduled ahead of the film clock (which *is* the AudioContext clock), so picture and sound stay locked.
const NOTE = (() => { const m = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 }; return (n) => { const r = /^([A-G])(#|b)?(-?\d)$/.exec(n); let s = m[r[1]] + (r[2] === '#' ? 1 : r[2] === 'b' ? -1 : 0) + (+r[3] + 1) * 12; return 440 * Math.pow(2, (s - 69) / 12); }; })();
const tr = (f, semis) => f * Math.pow(2, semis / 12);

const Audio = (() => {
  const AU = { muted: false, ctx: null, events: [] };
  let ctx = null, master, comp, noiseBuf, brownBuf, revBuf, sess = null, timer = null, idx = 0;
  // ── helpers usable by synth functions ──
  const now = () => ctx.currentTime;
  function envAt(param, pts) {
    // pts: [[time, value], ...]; handles start times in the past by interpolating
    const t0 = now();
    let i = 0;
    while (i < pts.length - 1 && pts[i + 1][0] <= t0) i++;
    let v0 = pts[i][1];
    if (pts[i][0] < t0 && i < pts.length - 1) { const [ta, va] = pts[i], [tb, vb] = pts[i + 1]; v0 = va + (vb - va) * ((t0 - ta) / Math.max(1e-4, tb - ta)); }
    param.cancelScheduledValues(0);
    param.setValueAtTime(Math.max(1e-5, v0), Math.max(t0, pts[0][0]));
    for (let k = i + 1; k < pts.length; k++) if (pts[k][0] > t0) param.linearRampToValueAtTime(Math.max(0, pts[k][1]), pts[k][0]);
  }
  function osc(type, f, t, d, out) { const o = ctx.createOscillator(); o.type = type; o.frequency.value = f; o.connect(out); o.start(Math.max(now(), t)); o.stop(t + d + 0.05); return o; }
  function gain(v, dest) { const g = ctx.createGain(); g.gain.value = v; if (dest) g.connect(dest); return g; }
  function filt(type, f, q, dest) { const b = ctx.createBiquadFilter(); b.type = type; b.frequency.value = f; b.Q.value = q ?? 0.7; if (dest) b.connect(dest); return b; }
  function noise(t, d, out, buf) { const s = ctx.createBufferSource(); s.buffer = buf || noiseBuf; s.loop = true; s.loopStart = 0; s.connect(out); const off = Math.max(0, now() - t); s.start(Math.max(now(), t), (off + Math.random() * 1.5) % 1.9); s.stop(t + d + 0.05); return s; }
  function adsr(g, t, a, peak, d, sus, rel, end) { envAt(g.gain, [[t, 0.0001], [t + a, peak], [t + a + d, peak * sus], [end, peak * sus], [end + rel, 0.0001]]); }
  // ── instruments ──
  const I = {
    epiano(o, t, f, d, v) { const g = gain(0, o.music); g.connect(o.rev); const c = osc('sine', f, t, d + 1.2, g); const m = osc('sine', f * 2, t, d + 1.2, gain(f * 1.2, null)); m.disconnect(); const mg = gain(f * 0.9 * v); m.connect(mg); mg.connect(c.frequency); envAt(mg.gain, [[t, f * 1.0], [t + 0.6, f * 0.1]]); osc('triangle', f * 2, t, d + 0.6, gain(0.12, g)); adsr(g, t, 0.006, 0.22 * v, 0.9, 0.35, 0.5, t + d); },
    pad(o, t, fs, d, v, bright = 900) { const lp = filt('lowpass', bright, 0.5, o.music); lp.connect(o.rev); const g = gain(0, lp); adsr(g, t, Math.min(1.2, d * 0.3), 0.08 * v, 0.5, 0.8, 1.2, t + d); for (const f of fs) { osc('sawtooth', f * 0.997, t, d + 1.4, g); osc('sawtooth', f * 1.004, t, d + 1.4, g); } },
    pluck(o, t, f, v, type = 'triangle', dec = 0.28) { const lp = filt('lowpass', 3200, 1, o.music); lp.connect(o.rev); const g = gain(0, lp); osc(type, f, t, dec + 0.1, g); envAt(g.gain, [[t, 0.0001], [t + 0.004, 0.2 * v], [t + dec, 0.0001]]); envAt(lp.frequency, [[t, 4500], [t + dec, 700]]); },
    marimba(o, t, f, v) { const g = gain(0, o.music); g.connect(o.rev); osc('sine', f, t, 0.6, g); osc('sine', f * 3.98, t, 0.15, gain(0.15, g)); envAt(g.gain, [[t, 0.0001], [t + 0.004, 0.26 * v], [t + 0.5, 0.0001]]); },
    bass(o, t, f, d, v) { const lp = filt('lowpass', 420, 2, o.music); const g = gain(0, lp); osc('sine', f, t, d + 0.1, g); osc('square', f, t, d + 0.1, gain(0.25, g)); adsr(g, t, 0.01, 0.32 * v, 0.15, 0.7, 0.08, t + d); },
    lead(o, t, f, d, v, type = 'square') { const lp = filt('lowpass', 2600, 1, o.music); lp.connect(o.rev); const g = gain(0, lp); const oo = osc(type, f, t, d + 0.3, g); const lfo = osc('sine', 5.5, t, d + 0.3, gain(f * 0.008, oo.frequency)); void lfo; adsr(g, t, 0.02, 0.09 * v, 0.1, 0.8, 0.15, t + d); },
    flute(o, t, f, d, v) { const lp = filt('lowpass', 2200, 0.6, o.music); lp.connect(o.rev); const g = gain(0, lp); const oo = osc('sine', f, t, d + 0.4, g); osc('triangle', f * 2, t, d + 0.4, gain(0.15, g)); osc('sine', 5, t, d + 0.4, gain(f * 0.006, oo.frequency)); noise(t, d, filt('bandpass', f * 2, 3, gain(0.012 * v, g))); adsr(g, t, 0.06, 0.14 * v, 0.1, 0.85, 0.25, t + d); },
    bell(o, t, f, v, dec = 1.6) { const g = gain(0, o.music); g.connect(o.rev); [[1, 1], [2.76, 0.4], [5.4, 0.2], [8.9, 0.08]].forEach(([m, a]) => osc('sine', f * m, t, dec, gain(a, g))); envAt(g.gain, [[t, 0.0001], [t + 0.003, 0.16 * v], [t + dec, 0.0001]]); },
    kick(o, t, v) { const g = gain(0, o.music); const oo = osc('sine', 150, t, 0.4, g); envAt(oo.frequency, [[t, 150], [t + 0.12, 48]]); envAt(g.gain, [[t, 0.0001], [t + 0.003, 0.7 * v], [t + 0.32, 0.0001]]); },
    snare(o, t, v) { const g = gain(0, o.music); g.connect(o.rev); noise(t, 0.2, filt('bandpass', 1900, 0.8, g)); osc('triangle', 210, t, 0.1, gain(0.6, g)); envAt(g.gain, [[t, 0.0001], [t + 0.002, 0.32 * v], [t + 0.16, 0.0001]]); },
    rim(o, t, v) { const g = gain(0, o.music); g.connect(o.rev); osc('square', 1700, t, 0.03, filt('bandpass', 1700, 4, g)); noise(t, 0.03, filt('highpass', 3000, 1, gain(0.3, g))); envAt(g.gain, [[t, 0.0001], [t + 0.001, 0.12 * v], [t + 0.04, 0.0001]]); },
    hat(o, t, v, open) { const g = gain(0, o.music); noise(t, open ? 0.3 : 0.06, filt('highpass', 7500, 0.8, g)); envAt(g.gain, [[t, 0.0001], [t + 0.001, 0.09 * v], [t + (open ? 0.25 : 0.045), 0.0001]]); },
    tom(o, t, f, v) { const g = gain(0, o.music); g.connect(o.rev); const oo = osc('sine', f * 1.5, t, 0.5, g); envAt(oo.frequency, [[t, f * 1.5], [t + 0.25, f]]); envAt(g.gain, [[t, 0.0001], [t + 0.003, 0.5 * v], [t + 0.45, 0.0001]]); },
    taiko(o, t, v) { const g = gain(0, o.music); g.connect(o.rev); const oo = osc('sine', 95, t, 1.0, g); envAt(oo.frequency, [[t, 95], [t + 0.3, 52]]); noise(t, 0.15, filt('lowpass', 700, 0.7, gain(0.6, g))); envAt(g.gain, [[t, 0.0001], [t + 0.004, 0.75 * v], [t + 0.9, 0.0001]]); },
    drone(o, t, f, d, v) { const lp = filt('lowpass', 500, 1, o.music); lp.connect(o.rev); const g = gain(0, lp); osc('sawtooth', f, t, d + 2, g); osc('sawtooth', f * 1.5 * 1.003, t, d + 2, gain(0.5, g)); osc('sine', f / 2, t, d + 2, gain(1.2, g)); const l = osc('sine', 0.15, t, d + 2, gain(250, lp.frequency)); void l; adsr(g, t, 2.0, 0.12 * v, 0.5, 0.9, 2.0, t + d); },
    stab(o, t, fs, v) { const lp = filt('lowpass', 2400, 1, o.music); lp.connect(o.rev); const g = gain(0, lp); for (const f of fs) osc('sawtooth', f, t, 0.35, g); envAt(g.gain, [[t, 0.0001], [t + 0.004, 0.07 * v], [t + 0.3, 0.0001]]); envAt(lp.frequency, [[t, 5000], [t + 0.25, 600]]); },
  };
  // ── sound effects ──
  const S = {
    alarmCx(o, t) { for (let k = 0; k < 6; k++) for (let j = 0; j < 2; j++) { const tt = t + k * 0.45 + j * 0.12; const g = gain(0, o.sfx); osc('square', 1760, tt, 0.08, filt('lowpass', 4000, 1, g)); envAt(g.gain, [[tt, 0.0001], [tt + 0.005, 0.07], [tt + 0.07, 0.0001]]); } },
    alarmCc(o, t) { const notes = ['C6', 'E6', 'G6', 'E6', 'C6', 'G5']; for (let k = 0; k < 2; k++) notes.forEach((n, i) => I.bell(o, t + k * 1.4 + i * 0.2, NOTE(n), 0.6, 1.0)); },
    charge(o, t, x) { const d = x.dur; const g = gain(0, o.sfx); const f = x.warm ? 110 : 140; osc('sine', f, t, d, g); osc('triangle', f * 2, t, d, gain(0.3, g)); osc('sine', 0.5, t, d, gain(0.004, g.gain)); adsr(g, t, 0.8, 0.02, 0.1, 1, 0.6, t + d - 0.6); },
    bootCx(o, t) { ['C5', 'G5', 'C6', 'E6', 'G6', 'C7'].forEach((n, i) => { const tt = t + i * 0.12; const g = gain(0, o.sfx); g.connect(o.rev); osc('square', NOTE(n), tt, 0.1, filt('lowpass', 3000, 1, g)); envAt(g.gain, [[tt, 0.0001], [tt + 0.005, 0.05], [tt + 0.09, 0.0001]]); }); for (let k = 0; k < 10; k++) { const tt = t + 0.1 + k * 0.07; const g = gain(0, o.sfx); osc('square', 2000 + hash1(k) * 3000, tt, 0.02, g); envAt(g.gain, [[tt, 0.0001], [tt + 0.002, 0.015], [tt + 0.02, 0.0001]]); } },
    bootCc(o, t) { ['F4', 'A4', 'C5', 'E5', 'G5'].forEach((n, i) => I.bell(o, t + i * 0.28, NOTE(n), 0.55, 2.0)); },
    land(o, t, x) { const g = gain(0, o.sfx); osc('sine', 85, t, 0.2, g); noise(t, 0.08, filt('lowpass', x.soft ? 500 : 900, 1, gain(0.6, g))); envAt(g.gain, [[t, 0.0001], [t + 0.003, x.soft ? 0.18 : 0.3], [t + 0.18, 0.0001]]); },
    step(o, t, x) { const g = gain(0, o.sfx); const cx = x.who === 'cx'; const f = cx ? 1500 : 900; noise(t, 0.06, filt('bandpass', f * (0.85 + hash1(t * 9) * 0.3), 1.2, g)); osc('sine', cx ? 140 : 110, t, 0.06, gain(0.5, g)); const v = (x.soft ? 0.05 : 0.09) * (x.run ? 1.4 : 1); envAt(g.gain, [[t, 0.0001], [t + 0.002, v], [t + 0.06, 0.0001]]); if (x.wet) { const w = gain(0, o.sfx); noise(t, 0.12, filt('bandpass', 2600, 1.5, w)); envAt(w.gain, [[t, 0.0001], [t + 0.01, 0.06], [t + 0.12, 0.0001]]); } },
    click(o, t) { const g = gain(0, o.sfx); osc('sine', 2200, t, 0.04, g); envAt(g.gain, [[t, 0.0001], [t + 0.001, 0.1], [t + 0.035, 0.0001]]); },
    grind(o, t, x) { const g = gain(0, o.sfx); const bp = filt('bandpass', 700, 1.5, g); noise(t, x.dur, bp); osc('square', 32, t, x.dur, gain(0.5, g.gain)); adsr(g, t, 0.05, 0.12, 0.1, 1, 0.1, t + x.dur); },
    pour(o, t, x) { const g = gain(0, o.sfx); const bp = filt('bandpass', x.thin ? 1600 : 1000, 2.5, g); noise(t, x.dur, bp); osc('sine', 7, t, x.dur, gain(250, bp.frequency)); envAt(bp.frequency, [[t, x.thin ? 1500 : 900], [t + x.dur, x.thin ? 1900 : 1400]]); adsr(g, t, 0.08, 0.1, 0.1, 1, 0.15, t + x.dur); },
    hiss(o, t, x) { const g = gain(0, o.sfx); noise(t, x.dur, filt('highpass', 4200, 0.7, g)); adsr(g, t, 0.3, 0.04, 0.2, 0.7, 0.4, t + x.dur); },
    lever(o, t) { S.click(o, t); const g = gain(0, o.sfx); const oo = osc('triangle', 320, t, 0.25, g); envAt(oo.frequency, [[t, 320], [t + 0.2, 180]]); envAt(g.gain, [[t, 0.0001], [t + 0.005, 0.06], [t + 0.22, 0.0001]]); },
    pop(o, t) { const g = gain(0, o.sfx); g.connect(o.rev); const oo = osc('sine', 300, t, 0.35, g); envAt(oo.frequency, [[t, 300], [t + 0.08, 900], [t + 0.3, 600]]); osc('sine', 18, t, 0.35, gain(80, oo.frequency)); envAt(g.gain, [[t, 0.0001], [t + 0.005, 0.15], [t + 0.32, 0.0001]]); S.click(o, t); },
    catch(o, t) { S.land(o, t, { soft: true }); },
    clink(o, t, x) { const g = gain(0, o.sfx); g.connect(o.rev); const b = x.low ? 1700 : 2600; [[1, 1], [2.1, 0.5], [3.3, 0.25]].forEach(([m, a]) => osc('sine', b * m, t, 0.3, gain(a, g))); envAt(g.gain, [[t, 0.0001], [t + 0.002, 0.07], [t + 0.25, 0.0001]]); },
    sip(o, t, x) { const g = gain(0, o.sfx); const bp = filt('bandpass', x.gulp ? 500 : 1200, 2, g); noise(t, 0.35, bp); envAt(bp.frequency, [[t, x.gulp ? 400 : 900], [t + 0.3, x.gulp ? 250 : 1800]]); envAt(g.gain, [[t, 0.0001], [t + 0.05, 0.08], [t + 0.3, 0.0001]]); },
    crunch(o, t, x) { for (let k = 0; k < 4; k++) { const tt = t + k * 0.07; const g = gain(0, o.sfx); noise(tt, 0.05, filt('bandpass', 2500 + k * 300, 1, g)); envAt(g.gain, [[tt, 0.0001], [tt + 0.002, x.soft ? 0.06 : 0.1], [tt + 0.05, 0.0001]]); } },
    whistle(o, t, x) { const g = gain(0, o.sfx); g.connect(o.rev); const oo = osc('sine', 1800, t, x.dur, g); envAt(oo.frequency, [[t, 1700], [t + x.dur, 2250]]); osc('sine', 9, t, x.dur, gain(25, oo.frequency)); noise(t, x.dur, filt('bandpass', 2000, 5, gain(0.3, g))); adsr(g, t, 0.4, 0.06, 0.1, 1, 0.2, t + x.dur); },
    ding(o, t) { I.bell(o, t, NOTE('G6'), 0.7, 1.5); },
    notify(o, t) { I.bell(o, t, NOTE('C6'), 0.5, 0.6); I.bell(o, t + 0.11, NOTE('G6'), 0.5, 0.9); },
    dm(o, t) { const g = gain(0, o.sfx); const oo = osc('sine', 700, t, 0.12, g); envAt(oo.frequency, [[t, 600], [t + 0.08, 1100]]); envAt(g.gain, [[t, 0.0001], [t + 0.005, 0.08], [t + 0.12, 0.0001]]); },
    gptPop(o, t) { const g = gain(0, o.sfx); g.connect(o.rev); const oo = osc('sine', 380, t, 0.45, g); envAt(oo.frequency, [[t, 380], [t + 0.25, 980]]); osc('sine', 22, t, 0.45, gain(60, oo.frequency)); envAt(g.gain, [[t, 0.0001], [t + 0.02, 0.12], [t + 0.42, 0.0001]]); I.bell(o, t + 0.2, NOTE('E7'), 0.25, 0.6); },
    typing(o, t, x) { const rate = x.fast ? 13 : 7.5; const n = Math.floor(x.dur * rate); for (let k = 0; k < n; k++) { const tt = t + k / rate + (hash1(k * 3.3 + t) - 0.5) * 0.04; if (tt < now() - 0.01) continue; const g = gain(0, o.sfx); noise(tt, 0.03, filt('bandpass', 2600 + hash1(k) * 1800, 1.4, g)); envAt(g.gain, [[tt, 0.0001], [tt + 0.001, x.fast ? 0.07 : 0.05], [tt + 0.025, 0.0001]]); } },
    fail(o, t) { [[220, 0], [175, 0.18]].forEach(([f, dt]) => { const tt = t + dt; const g = gain(0, o.sfx); osc('square', f, tt, 0.16, filt('lowpass', 900, 1, g)); envAt(g.gain, [[tt, 0.0001], [tt + 0.005, 0.07], [tt + 0.15, 0.0001]]); }); },
    success(o, t) { ['C6', 'E6', 'G6', 'C7'].forEach((n, i) => I.bell(o, t + i * 0.07, NOTE(n), 0.6, 0.8)); },
    cityAmb(o, t, x) { const d = x.dur; const g = gain(0, o.sfx); noise(t, d, filt('lowpass', 380, 0.7, g), brownBuf); adsr(g, t, 0.8, 0.22, 0.1, 1, 0.8, t + d - 0.8); for (let k = 0; k < d / 1.7; k++) { const tt = t + k * 1.7 + hash1(k) * 0.8; const b = gain(0, o.sfx); const oo = osc('sine', 3200 + hash1(k * 5) * 1800, tt, 0.18, b); envAt(oo.frequency, [[tt, 3600 + hash1(k) * 1000], [tt + 0.12, 2600]]); envAt(b.gain, [[tt, 0.0001], [tt + 0.01, 0.025], [tt + 0.15, 0.0001]]); } for (let k = 0; k < d / 3.2; k++) { const tt = t + 1 + k * 3.2; const c = gain(0, o.sfx); const bp = filt('bandpass', 500, 0.8, c); noise(tt, 1.6, bp); envAt(bp.frequency, [[tt, 300], [tt + 0.8, 900], [tt + 1.6, 350]]); envAt(c.gain, [[tt, 0.0001], [tt + 0.8, 0.07], [tt + 1.6, 0.0001]]); } },
    crossing(o, t, x) { for (let k = 0; k < x.dur / 0.16; k++) { const tt = t + k * 0.16; const g = gain(0, o.sfx); osc('square', 1300, tt, 0.03, filt('bandpass', 1300, 3, g)); envAt(g.gain, [[tt, 0.0001], [tt + 0.002, 0.035], [tt + 0.03, 0.0001]]); } },
    officeAmb(o, t, x) { const g = gain(0, o.sfx); noise(t, x.dur, filt('lowpass', 260, 0.7, g), brownBuf); adsr(g, t, 0.6, 0.1, 0.1, 1, 0.8, t + x.dur - 0.8); },
    rain(o, t, x) { const d = x.dur; const g = gain(0, o.sfx); noise(t, d, filt('highpass', 900, 0.5, gain(0.6, g))); noise(t, d, filt('bandpass', 4500, 0.6, gain(0.35, g))); noise(t, d, filt('lowpass', 300, 0.7, gain(0.8, g)), brownBuf); adsr(g, t, 1.5, 0.16, 0.1, 1, 1.5, t + d - 1.5); },
    thunder(o, t) { const g = gain(0, o.sfx); g.connect(o.rev); noise(t, 3.2, filt('lowpass', 160, 0.8, g), brownBuf); noise(t, 0.4, filt('bandpass', 1200, 0.6, gain(0.4, g))); envAt(g.gain, [[t, 0.0001], [t + 0.05, 0.5], [t + 0.5, 0.25], [t + 3.0, 0.0001]]); },
    whoosh(o, t, x) { const g = gain(0, o.sfx); const bp = filt('bandpass', 500, 1.6, g); noise(t, 0.3, bp); envAt(bp.frequency, [[t, 400], [t + 0.12, 2200], [t + 0.28, 600]]); envAt(g.gain, [[t, 0.0001], [t + 0.1, x.soft ? 0.08 : 0.17], [t + 0.28, 0.0001]]); },
    clatter(o, t) { for (let k = 0; k < 3; k++) S.clink(o, t + k * 0.09, { low: true }); },
    summon(o, t, x) { const cx = x.who === 'cx'; const g = gain(0, o.sfx); g.connect(o.rev); const oo = osc(cx ? 'sawtooth' : 'sine', cx ? 220 : 180, t, 0.9, filt('lowpass', 3000, 2, g)); envAt(oo.frequency, [[t, cx ? 220 : 180], [t + 0.6, cx ? 1320 : 720]]); envAt(g.gain, [[t, 0.0001], [t + 0.5, 0.08], [t + 0.85, 0.0001]]); (cx ? ['C6', 'G6', 'C7'] : ['A5', 'E6', 'A6']).forEach((n, i) => I.bell(o, t + 0.45 + i * 0.07, NOTE(n), 0.5, 1.0)); },
    impact(o, t, x) { const s = x.s; const g = gain(0, o.sfx); g.connect(o.rev); noise(t, 0.25, filt('lowpass', 5000, 0.5, g)); const th = osc('sine', 90, t, 0.4, gain(1.2, g)); envAt(th.frequency, [[t, 120], [t + 0.25, 50]]); [[1240, 1], [2930, 0.6], [4870, 0.3]].forEach(([f, a]) => osc('sine', f * (0.95 + hash1(t) * 0.1), t, 0.6, gain(0.12 * a, g))); envAt(g.gain, [[t, 0.0001], [t + 0.002, 0.32 * Math.min(1.4, s)], [t + 0.12, 0.12 * s], [t + 0.55, 0.0001]]); },
    splash(o, t) { const g = gain(0, o.sfx); noise(t, 0.25, filt('bandpass', 1800, 0.8, g)); envAt(g.gain, [[t, 0.0001], [t + 0.01, 0.12], [t + 0.22, 0.0001]]); for (let k = 0; k < 4; k++) { const tt = t + 0.04 + k * 0.05; const d = gain(0, o.sfx); const oo = osc('sine', 1400 + hash1(k + t) * 1600, tt, 0.05, d); envAt(oo.frequency, [[tt, 1200 + k * 400], [tt + 0.04, 2400]]); envAt(d.gain, [[tt, 0.0001], [tt + 0.003, 0.03], [tt + 0.045, 0.0001]]); } },
    skid(o, t, x) { const g = gain(0, o.sfx); noise(t, x.dur, filt('bandpass', 1100, 1.2, g)); envAt(g.gain, [[t, 0.0001], [t + 0.03, 0.12], [t + x.dur, 0.0001]]); },
    lock(o, t, x) { const g = gain(0, o.sfx); g.connect(o.rev); const bp = filt('bandpass', 1600, 2, g); noise(t, x.dur, bp); osc('square', 23, t, x.dur, gain(0.6, g.gain)); osc('sine', 880, t, x.dur, gain(0.2, g)); osc('sine', 1318, t, x.dur, gain(0.15, g)); adsr(g, t, 0.05, 0.12, 0.2, 0.8, 0.3, t + x.dur - 0.3); },
    gptDescend(o, t) { ['D5', 'F#5', 'A5', 'D6'].forEach((n, i) => { const g = gain(0, o.sfx); g.connect(o.rev); osc('sine', NOTE(n), t + i * 0.15, 2.2, g); osc('sine', 6, t, 2.4, gain(NOTE(n) * 0.006, g.gain)); adsr(g, t + i * 0.15, 0.6, 0.05, 0.4, 0.8, 0.8, t + 1.6); }); },
    shock(o, t) { const g = gain(0, o.sfx); g.connect(o.rev); const oo = osc('sine', 70, t, 1.4, g); envAt(oo.frequency, [[t, 110], [t + 1, 40]]); noise(t, 0.6, filt('lowpass', 1500, 0.6, gain(0.6, g))); envAt(g.gain, [[t, 0.0001], [t + 0.01, 0.45], [t + 1.3, 0.0001]]); },
    holo(o, t) { ['E6', 'B6', 'E7'].forEach((n, i) => I.bell(o, t + i * 0.05, NOTE(n), 0.35, 0.7)); },
    buildPass(o, t) { ['C5', 'E5', 'G5', 'C6', 'E6', 'G6'].forEach((n, i) => I.bell(o, t + i * 0.05, NOTE(n), 0.55, 1.6)); },
    bump(o, t) { I.bell(o, t, NOTE('A6'), 0.5, 0.6); S.land(o, t, { soft: true }); },
    voice(o, t, x) {
      // robot chirp-babble that follows the subtitle line
      const kind = x.who, n = Math.max(2, Math.round(x.text.replace(/[^a-zA-Z]/g, '').length / 3.2));
      const base = kind === 'codex' ? 560 : kind === 'claude' ? 360 : 640;
      const span = Math.min(x.d - 0.3, n * 0.11);
      for (let k = 0; k < n; k++) {
        const tt = t + (k / n) * span; if (tt < now() - 0.02) continue;
        const dur = 0.06 + hash1(k * 1.7 + t) * 0.05;
        const f0 = base * (0.85 + hash1(k * 2.3 + t * 3) * 0.4) * (x.text.includes('?') && k === n - 1 ? 1.35 : 1) * (x.text.includes('!') ? 1.12 : 1);
        const g = gain(0, o.voice);
        const type = kind === 'codex' ? 'square' : kind === 'claude' ? 'triangle' : 'sine';
        const oo = osc(type, f0, tt, dur + 0.02, filt('lowpass', kind === 'codex' ? 2400 : 1600, 2, g));
        envAt(oo.frequency, [[tt, f0], [tt + dur, f0 * (kind === 'gpt' ? 1.25 : 0.92)]]);
        envAt(g.gain, [[tt, 0.0001], [tt + 0.008, kind === 'codex' ? 0.05 : 0.07], [tt + dur, 0.0001]]);
      }
    },
  };
  AU.S = S; AU.I = I;

  // ── score: generate note events for each section ──
  function buildScore(E) {
    const add = (t, dur, fn) => E.push({ t, dur, fn, bus: 'music' });
    const at = (t0, bpm) => (beat) => t0 + (beat * 60) / bpm;
    const chord = (names) => names.map(NOTE);
    // theme & motifs (beats)
    const THEME = [['E5', 1], ['G5', 0.5], ['A5', 0.5], ['G5', 1], ['E5', 1], ['D5', 0.5], ['E5', 0.5], ['C5', 1], ['D5', 2]];
    const CXM = [['C5', 0.25], ['E5', 0.25], ['G5', 0.25], ['B5', 0.25], ['C6', 0.5]];
    const CCM = [['G5', 1], ['F5', 0.5], ['E5', 0.5], ['D5', 1], ['C5', 1]];
    const playLine = (line, b0, B, semis, fn) => { let b = b0; for (const [n, d] of line) { const f = tr(NOTE(n), semis); const tt = B(b), dd = (B(b + d) - B(b)) * 0.92; add(tt, dd, (o, T) => fn(o, T, f, dd)); b += d; } return b; };
    // 0–5 opening pad + bells
    add(0.2, 5.0, (o, T) => I.pad(o, T, chord(['F3', 'A3', 'C4', 'E4', 'G4']), 5.0, 1.0, 1200));
    [['C6', 1.4], ['A5', 2.0], ['G5', 2.6], ['E6', 3.4]].forEach(([n, tt]) => add(tt, 1.6, (o, T) => I.bell(o, T, NOTE(n), 0.5, 2.2)));
    // 5–27 wake: lo-fi 80 bpm
    {
      const B = at(5, 80), prog = [['F3', 'A3', 'C4', 'E4'], ['E3', 'G3', 'B3', 'D4'], ['D3', 'F3', 'A3', 'C4'], ['C3', 'E3', 'G3', 'B3']];
      for (let bar = 0; bar < 7; bar++) {
        const c = chord(prog[bar % 4]); const b0 = bar * 4;
        c.forEach((f) => add(B(b0), 2.6, (o, T) => I.epiano(o, T, f, 2.4, 0.8)));
        c.slice(1).forEach((f) => add(B(b0 + 2.5), 1.2, (o, T) => I.epiano(o, T, f, 1.0, 0.45)));
        add(B(b0), 1.4, (o, T) => I.bass(o, T, c[0] / 2, 1.3, 0.8)); add(B(b0 + 2), 1.4, (o, T) => I.bass(o, T, c[0] / 2, 1.3, 0.6));
        if (bar >= 1) { add(B(b0), 0.3, (o, T) => I.kick(o, T, 0.5)); add(B(b0 + 2.5), 0.3, (o, T) => I.kick(o, T, 0.35)); add(B(b0 + 1), 0.1, (o, T) => I.rim(o, T, 0.7)); add(B(b0 + 3), 0.1, (o, T) => I.rim(o, T, 0.7)); for (let h = 0; h < 8; h++) add(B(b0 + h * 0.5 + (h % 2 ? 0.08 : 0)), 0.1, (o, T) => I.hat(o, T, 0.35)); }
      }
      // Codex motif (cool plucks) over his wake-up; Claude motif (flute) over hers
      playLine(CXM, 6, B, 0, (o, T, f) => I.pluck(o, T, f, 0.6, 'square', 0.18));
      playLine(CXM, 10, B, 5, (o, T, f) => I.pluck(o, T, f, 0.55, 'square', 0.18));
      playLine(CCM, 17, B, 0, (o, T, f, d) => I.flute(o, T, f, d, 0.8));
      playLine(CCM, 22, B, -2, (o, T, f, d) => I.flute(o, T, f, d, 0.75));
    }
    // 27–58.5 breakfast: bouncy 104 bpm in C
    {
      const B = at(27, 104), prog = [['C3', 'E4', 'G4'], ['A2', 'C4', 'E4'], ['F2', 'A3', 'C4'], ['G2', 'B3', 'D4']];
      const bars = Math.floor(((58.5 - 27) * 104) / 60 / 4);
      for (let bar = 0; bar < bars; bar++) {
        const c = chord(prog[bar % 4]); const b0 = bar * 4; const cxScene = B(b0) < 40 || B(b0) >= 55;
        for (let q = 0; q < 4; q++) add(B(b0 + q), 0.4, (o, T) => I.pluck(o, T, q % 2 ? c[0] * 1.5 : c[0], 0.9, 'triangle', 0.22));
        [1, 3].forEach((q) => c.slice(1).forEach((f) => add(B(b0 + q + 0.5), 0.3, (o, T) => cxScene ? I.pluck(o, T, f, 0.45, 'square', 0.12) : I.marimba(o, T, f, 0.5))));
        add(B(b0), 0.3, (o, T) => I.kick(o, T, 0.45)); add(B(b0 + 2), 0.3, (o, T) => I.kick(o, T, 0.35));
        add(B(b0 + 1), 0.1, (o, T) => I.rim(o, T, 0.6)); add(B(b0 + 3), 0.1, (o, T) => I.rim(o, T, 0.6));
        for (let h = 0; h < 4; h++) add(B(b0 + h + 0.5), 0.1, (o, T) => I.hat(o, T, 0.25));
        if (bar % 4 === 0) playLine(THEME, b0, B, 0, (o, T, f) => (cxScene ? I.marimba(o, T, f, 0.75) : I.flute(o, T, f, 0.35, 0.7)));
        if (bar % 4 === 2) playLine(cxScene ? CXM : CCM, b0 + 1, B, 0, (o, T, f, d) => (cxScene ? I.pluck(o, T, f * 2, 0.4, 'square', 0.14) : I.flute(o, T, f, d, 0.6)));
      }
    }
    // 58.5–80 commute: city pop 116 bpm in G
    {
      const B = at(58.5, 116), prog = [['G2', 'B3', 'D4', 'G4'], ['E2', 'G3', 'B3', 'E4'], ['C2', 'E3', 'G3', 'C4'], ['D2', 'F#3', 'A3', 'D4']];
      const bars = Math.floor(((80 - 58.5) * 116) / 60 / 4);
      for (let bar = 0; bar < bars; bar++) {
        const c = chord(prog[bar % 4]); const b0 = bar * 4;
        for (let e = 0; e < 8; e++) add(B(b0 + e * 0.5), 0.3, (o, T) => I.bass(o, T, e % 2 ? c[0] * 2 : c[0], 0.22, 0.85));
        [0.5, 1.5, 2.5, 3.5].forEach((q) => add(B(b0 + q), 0.3, (o, T) => I.stab(o, T, c.slice(1), 0.8)));
        for (let q = 0; q < 4; q++) add(B(b0 + q), 0.3, (o, T) => I.kick(o, T, q % 2 ? 0.4 : 0.6));
        add(B(b0 + 1), 0.2, (o, T) => I.snare(o, T, 0.6)); add(B(b0 + 3), 0.2, (o, T) => I.snare(o, T, 0.6));
        for (let h = 0; h < 8; h++) add(B(b0 + h * 0.5), 0.1, (o, T) => I.hat(o, T, h % 2 ? 0.35 : 0.2, h === 7));
        if (bar % 2 === 0 && bar < bars - 1) playLine(THEME, b0, B, 7 - 12, (o, T, f, d) => I.lead(o, T, f * 2, d, 0.8, 'square'));
      }
    }
    // 80–121 work: focus electronica 120 bpm, tense minor at dusk
    {
      const B = at(80, 120);
      const bars = Math.floor(((121 - 80) * 120) / 60 / 4);
      const brightP = [['A2', 'C4', 'E4'], ['F2', 'A3', 'C4'], ['C3', 'E4', 'G4'], ['G2', 'B3', 'D4']];
      const tenseP = [['A2', 'C4', 'E4'], ['F2', 'A3', 'C4'], ['D2', 'F3', 'A3'], ['E2', 'G#3', 'B3']];
      for (let bar = 0; bar < bars; bar++) {
        const b0 = bar * 4, T0 = B(b0), tense = T0 > 112;
        const c = chord((tense ? tenseP : brightP)[bar % 4]);
        add(T0, B(b0 + 4) - T0, (o, T) => I.pad(o, T, c, B(b0 + 4) - T0, 0.7, tense ? 600 : 1000));
        for (let s = 0; s < 16; s++) { const f = c[(s % 3)] * (s % 6 < 3 ? 2 : 4); if (tense && s % 2) continue; add(B(b0 + s * 0.25), 0.2, (o, T) => I.pluck(o, T, f, 0.35, 'sawtooth', 0.14)); }
        if (T0 > 83.5 && !(T0 > 104 && T0 < 112)) { for (let q = 0; q < 4; q++) add(B(b0 + q), 0.3, (o, T) => I.kick(o, T, 0.42)); for (let h = 0; h < 4; h++) add(B(b0 + h + 0.5), 0.1, (o, T) => I.hat(o, T, 0.25)); }
        add(T0, 1.8, (o, T) => I.bass(o, T, c[0], 1.8, 0.7)); add(B(b0 + 2), 1.8, (o, T) => I.bass(o, T, c[0], 1.8, 0.6));
      }
      // comedic "one tiny change" stingers
      [104.6, 108.9].forEach((tt) => ['E5', 'D#5', 'D5', 'C#5'].forEach((n, i) => add(tt + 0.6 + i * 0.16, 0.2, (o, T) => I.pluck(o, T, NOTE(n), 0.7, 'square', 0.15))));
    }
    // 121–134.5 alley: dark drone + taiko
    {
      add(121, 13.5, (o, T) => I.drone(o, T, NOTE('D2'), 13.0, 1.0));
      add(121, 13.5, (o, T) => I.pad(o, T, chord(['D3', 'F3', 'A3']), 13.0, 0.6, 500));
      [123.0, 125.0, 127.0, 128.5, 130.0, 131.0, 132.0, 132.5, 133.0, 133.5, 133.75, 134.0, 134.25].forEach((tt, i) => add(tt, 0.9, (o, T) => I.taiko(o, T, 0.5 + i * 0.03)));
      add(131.6, 1.0, (o, T) => I.stab(o, T, chord(['D4', 'F4', 'A4']), 0.8)); add(133.0, 1.0, (o, T) => I.stab(o, T, chord(['C4', 'E4', 'A4']), 0.8));
    }
    // 134.5–146 fight: 150 bpm D minor
    {
      const B = at(134.5, 150);
      const bars = Math.floor(((146 - 134.5) * 150) / 60 / 4);
      const prog = [['D2', 'D4', 'F4', 'A4'], ['Bb1', 'D4', 'F4', 'Bb4'], ['C2', 'C4', 'E4', 'G4'], ['A1', 'C#4', 'E4', 'A4']];
      for (let bar = 0; bar < bars; bar++) {
        const c = chord(prog[bar % 4]); const b0 = bar * 4;
        for (let s = 0; s < 16; s++) add(B(b0 + s * 0.25), 0.15, (o, T) => I.bass(o, T, s % 4 === 2 ? c[0] * 2 : c[0], 0.12, 0.9));
        [0, 1.5, 2.5].forEach((q) => add(B(b0 + q), 0.3, (o, T) => I.kick(o, T, 0.7)));
        add(B(b0 + 1), 0.2, (o, T) => I.snare(o, T, 0.8)); add(B(b0 + 3), 0.2, (o, T) => I.snare(o, T, 0.8));
        for (let h = 0; h < 8; h++) add(B(b0 + h * 0.5), 0.1, (o, T) => I.hat(o, T, 0.35));
        add(B(b0), 0.4, (o, T) => I.stab(o, T, c.slice(1), 1.0)); add(B(b0 + 2.75), 0.4, (o, T) => I.stab(o, T, c.slice(1), 0.7));
        if (bar % 2 === 1) [0, 0.25, 0.5, 0.75].forEach((q, i) => add(B(b0 + 3 + q), 0.4, (o, T) => I.tom(o, T, 150 - i * 20, 0.6)));
        if (bar >= 2) playLine([['D5', 0.5], ['F5', 0.5], ['A5', 0.5], ['G5', 0.5], ['F5', 1], ['E5', 1]], b0, B, 0, (o, T, f, d) => I.lead(o, T, f, d, 0.6, 'sawtooth'));
      }
      IMPACTS.forEach(([tt, , s]) => { add(tt, 1.0, (o, T) => I.taiko(o, T, Math.min(1, 0.5 + s * 0.3))); add(tt, 0.6, (o, T) => I.stab(o, T, chord(['D3', 'A3', 'D4', 'F4']), s)); });
      add(143.9, 2.3, (o, T) => I.pad(o, T, chord(['D3', 'A3', 'D4', 'F4', 'A4']), 2.2, 1.2, 1800));
    }
    // 146–160.5 interruption + uneasy dialogue: gentle, tentative
    {
      add(146.2, 3.6, (o, T) => I.pad(o, T, chord(['D4', 'F#4', 'A4', 'C#5']), 3.4, 0.8, 1600));
      const B = at(149.5, 70), prog = [['D3', 'F#3', 'A3'], ['C#3', 'E3', 'A3'], ['B2', 'D3', 'F#3'], ['G2', 'B2', 'D3']];
      for (let bar = 0; bar < 3; bar++) { const c = chord(prog[bar % 4]); c.forEach((f, i) => add(B(bar * 4 + i * 0.5), 3.0, (o, T) => I.epiano(o, T, f * 2, 2.8, 0.55))); add(B(bar * 4), 3.2, (o, T) => I.bass(o, T, c[0] / 2, 3.0, 0.5)); }
      playLine(CXM, 2, B, 2, (o, T, f) => I.pluck(o, T, f, 0.4, 'square', 0.18));
      playLine(CCM, 6, B, 2, (o, T, f, d) => I.flute(o, T, f, d, 0.55));
    }
    // 160.5–169 build → triumph with both motifs
    {
      const B = at(160.5, 120);
      for (let s = 0; s < 16; s++) add(B(s * 0.25), 0.2, (o, T) => I.pluck(o, T, tr(NOTE('D5'), [0, 4, 7, 12][s % 4] + Math.floor(s / 4) * 2), 0.5, 'triangle', 0.18));
      const B2 = at(162.6, 120);
      add(162.6, 1.5, (o, T) => I.stab(o, T, chord(['D4', 'F#4', 'A4', 'D5']), 1.2));
      const prog = [['D3', 'F#3', 'A3', 'D4'], ['B2', 'D3', 'F#3', 'B3'], ['G2', 'B2', 'D3', 'G3'], ['A2', 'C#3', 'E3', 'A3']];
      for (let bar = 0; bar < 3; bar++) {
        const c = chord(prog[bar % 4]); const b0 = bar * 4;
        add(B2(b0), 2.0, (o, T) => I.pad(o, T, c.map((f) => f * 2), 2.0, 0.6, 1600));
        for (let q = 0; q < 4; q++) add(B2(b0 + q), 0.3, (o, T) => I.kick(o, T, 0.45));
        add(B2(b0 + 1), 0.2, (o, T) => I.snare(o, T, 0.5)); add(B2(b0 + 3), 0.2, (o, T) => I.snare(o, T, 0.5));
        add(B2(b0), 1.8, (o, T) => I.bass(o, T, c[0], 1.8, 0.7)); add(B2(b0 + 2), 1.8, (o, T) => I.bass(o, T, c[0], 1.8, 0.6));
      }
      playLine(THEME, 0, B2, 2, (o, T, f, d) => { I.marimba(o, T, f, 0.8); I.flute(o, T, f, d, 0.45); });
      // the next request: cheeky question
      ['A5', 'B5', 'C#6', 'E6'].forEach((n, i) => add(166.0 + i * 0.1, 0.4, (o, T) => I.bell(o, T, NOTE(n), 0.5, 0.8)));
    }
    // 169–179 outro: theme reprise, warm and slow
    {
      const B = at(169, 84);
      const prog = [['D3', 'F#3', 'A3', 'C#4'], ['B2', 'D3', 'F#3', 'A3'], ['G2', 'B2', 'D3', 'F#3'], ['A2', 'C#3', 'E3', 'G3']];
      for (let bar = 0; bar < 3; bar++) { const c = chord(prog[bar]); c.forEach((f) => add(B(bar * 4), 2.9, (o, T) => I.epiano(o, T, f * 2, 2.8, 0.6))); add(B(bar * 4), 2.9, (o, T) => I.pad(o, T, c, 2.8, 0.5, 900)); add(B(bar * 4), 2.9, (o, T) => I.bass(o, T, c[0] / 2, 2.8, 0.5)); }
      playLine(THEME, 0, B, 2, (o, T, f, d) => I.flute(o, T, f, d, 0.7));
      add(177.4, 1.6, (o, T) => I.pad(o, T, chord(['D3', 'A3', 'D4', 'F#4', 'A4']), 1.4, 0.8, 1200));
      add(177.4, 1.6, (o, T) => I.bell(o, T, NOTE('D6'), 0.5, 2.5));
    }
  }

  AU.init = (story) => {
    const E = [];
    // sfx from the story
    for (const ev of SFX) {
      const o = ev.opts || {};
      if (ev.name === 'steps') {
        // derive footfalls from the real walk distance so feet and sound match
        const R = A[o.who];
        const step = strideLen(R, o.run ? { leg: 0.6 } : {});
        let t0, t1, dist;
        if (o.mv) { t0 = o.mv.t0; t1 = o.mv.t1; dist = (t) => o.mv(t).dist; }
        else { t0 = o.t0; t1 = o.t1; let px = o.track(t0).x, pz = o.track(t0).z, acc = 0, last = t0; const cache = new Map(); dist = (t) => { while (last < t) { last += 1 / 120; const q = o.track(last); acc += Math.hypot(q.x - px, q.z - pz); px = q.x; pz = q.z; } return acc; }; void cache; }
        let k = 0;
        for (let t = t0; t <= t1 + 0.05; t += 1 / 120) { const n = Math.floor(dist(t) / step + 0.5); if (n > k) { k = n; const tt = t; E.push({ t: tt, dur: 0.1, bus: 'sfx', fn: (O, T) => S.step(O, T, o) }); } }
        continue;
      }
      const f = S[ev.name];
      if (!f) continue;
      E.push({ t: ev.t, dur: o.dur || 1.5, bus: 'sfx', fn: (O, T) => f(O, T, o) });
    }
    for (const l of LINES) E.push({ t: l.t, dur: l.d, bus: 'voice', fn: (O, T) => S.voice(O, T, l) });
    buildScore(E);
    E.sort((a, b) => a.t - b.t);
    AU.events = E;
  };
  function makeBuffers() {
    const sr = ctx.sampleRate, n = Math.floor(sr * 2);
    noiseBuf = ctx.createBuffer(1, n, sr); const d = noiseBuf.getChannelData(0); for (let i = 0; i < n; i++) d[i] = Math.random() * 2 - 1;
    brownBuf = ctx.createBuffer(1, n, sr); const b = brownBuf.getChannelData(0); let last = 0; for (let i = 0; i < n; i++) { last = (last + 0.02 * (Math.random() * 2 - 1)) / 1.02; b[i] = last * 3.5; }
    const rn = Math.floor(sr * 2.4); revBuf = ctx.createBuffer(2, rn, sr);
    for (let ch = 0; ch < 2; ch++) { const r = revBuf.getChannelData(ch); for (let i = 0; i < rn; i++) r[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / rn, 2.6); }
  }
  AU.resume = () => {
    try {
      if (!ctx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) return;
        ctx = new AC({ latencyHint: 'interactive' });
        AU.ctx = ctx;
        comp = ctx.createDynamicsCompressor(); comp.threshold.value = -14; comp.ratio.value = 3.5; comp.attack.value = 0.005; comp.release.value = 0.2;
        master = ctx.createGain(); master.gain.value = AU.muted ? 0 : 0.9;
        master.connect(comp); comp.connect(ctx.destination);
        makeBuffers();
        ctx.addEventListener('statechange', () => { if (Film.playing) { Film.anchorFilm = Film.t; Film.anchorClock = AU.clock(); if (ctx.state === 'running') AU.start(Film.t); } });
      }
      if (ctx.state !== 'running') ctx.resume().catch(() => {});
    } catch (e) { console.warn('audio unavailable', e); ctx = null; }
  };
  AU.clock = () => (ctx && ctx.state === 'running' ? ctx.currentTime : performance.now() / 1000);
  function killSession() {
    if (!sess) return;
    const s = sess; sess = null;
    try { s.g.gain.cancelScheduledValues(0); s.g.gain.setValueAtTime(s.g.gain.value, ctx.currentTime); s.g.gain.linearRampToValueAtTime(0, ctx.currentTime + 0.04); setTimeout(() => { try { s.g.disconnect(); } catch (e) { /* already gone */ } }, 120); } catch (e) { /* ignore */ }
  }
  AU.start = (filmT) => {
    if (!ctx) return;
    killSession();
    const g = ctx.createGain(); g.gain.value = 1; g.connect(master);
    const rev = ctx.createConvolver(); rev.buffer = revBuf; const revOut = ctx.createGain(); revOut.gain.value = 0.32; rev.connect(revOut); revOut.connect(g);
    const revIn = ctx.createGain(); revIn.gain.value = 1; revIn.connect(rev);
    const music = ctx.createGain(); music.gain.value = 0.55; music.connect(g);
    const sfx = ctx.createGain(); sfx.gain.value = 0.9; sfx.connect(g);
    const voice = ctx.createGain(); voice.gain.value = 0.6; voice.connect(g);
    sess = { g, o: { music, sfx, voice, rev: revIn } };
    // find first event still sounding at filmT
    idx = 0; while (idx < AU.events.length && AU.events[idx].t + AU.events[idx].dur < filmT) idx++;
    clearInterval(timer);
    timer = setInterval(pump, 30);
    pump();
  };
  function pump() {
    if (!sess || !ctx || !Film.playing) return;
    const fNow = Film.anchorFilm + (AU.clock() - Film.anchorClock);
    const horizon = fNow + 0.35;
    const E = AU.events;
    while (idx < E.length && E[idx].t <= horizon) {
      const ev = E[idx++];
      if (ev.t + ev.dur < fNow) continue;
      if (ev.t < fNow - 0.08 && ev.dur < 0.6) continue; // too late for a short sound: skip it
      const T = ctx.currentTime + (ev.t - fNow);
      try { ev.fn(sess.o, T); } catch (e) { /* one bad sound must not stop the score */ }
    }
  }
  AU.stop = () => { clearInterval(timer); timer = null; killSession(); };
  AU.setMuted = (m) => { AU.muted = m; if (master) { master.gain.cancelScheduledValues(0); master.gain.setTargetAtTime(m ? 0 : 0.9, ctx.currentTime, 0.03); } };
  return AU;
})();
