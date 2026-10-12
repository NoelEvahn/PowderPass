"""Original procedural music.  Every progression, voicing and melody rule below was written for this
project; no existing melody is quoted.  Mood references (see pack/MANIFEST.md) informed only broad
characteristics: slow tempo, maj7/add9 harmony, detuned/tape-warped keys, heavy reverb, distant mix.

Each piece renders a stereo mix; the mono (positional) version is the downmix.
"""
from __future__ import annotations

import numpy as np
from scipy import signal

from dsp import (SR, ns, taxis, db, peak, rms, white, pink, smooth_random, lp, hp, bp, peq, env_exp,
                 env_ar, env_points, fade_edges, phase_of, additive, saturate, make_ir, vardelay, tape,
                 mix_into, pad_to, normalize_lufs, limiter, multi_cutoff_lp)


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


# ============================================================================ instruments
def piano(m, dur, vel, rng, detune_c=1.5, bright=1.0, max_len=7.0, worn=0.0):
    f = hz(m) * 2 ** (rng.normal(0, worn) / 1200)
    L = min(max_len, dur + 0.4)
    n = ns(L)
    t = taxis(n)
    out = np.zeros(n)
    B = 0.0004
    T0 = float(np.clip(3.2 * (261.6 / f) ** 0.55, 0.7, 8.0))
    K = int(min(16, 7000 // f))
    hpos = 0.12 + rng.uniform(-0.01, 0.01)
    expo = 1.15 + 0.9 * (1 - vel * bright)
    for k in range(1, K + 1):
        fk = k * f * np.sqrt(1 + B * k * k)
        if fk > 9000:
            break
        a = k ** -expo * (abs(np.sin(np.pi * k * hpos)) + 0.15)
        tau = T0 / (1 + 0.4 * (k - 1))
        e = 0.6 * np.exp(-t / (tau * 0.3)) + 0.4 * np.exp(-t / tau)
        d = detune_c * (0.6 + 0.4 * rng.random()) / 1200
        s = np.sin(2 * np.pi * fk * (1 + d) * t + rng.uniform(0, 6.28)) + \
            np.sin(2 * np.pi * fk * (1 - d) * t + rng.uniform(0, 6.28))
        out += a * e * s * 0.5
    rel = np.ones(n)
    k0 = ns(dur)
    if k0 < n:
        rel[k0:] = np.exp(-taxis(n - k0) / 0.12)
    out *= rel
    out[: ns(0.002)] *= np.linspace(0, 1, ns(0.002))
    ham = lp(white(ns(0.03), rng), 1200 + 1500 * vel, 2) * env_exp(ns(0.03), 0.004, 0.0003)
    out[: len(ham)] += 0.03 * vel * ham
    return out * vel ** 1.4


def rhodes(m, dur, vel, rng, max_len=6.0, bark=1.0):
    f = hz(m)
    L = min(max_len, dur + 0.5)
    n = ns(L)
    t = taxis(n)
    idx = (1.4 * vel * bark + 0.2) * np.exp(-t / 0.22) + 0.25
    car = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)))
    tine = 0.0
    if f * 7.1 < SR / 2.3:
        tine = 0.12 * vel * np.sin(2 * np.pi * f * 7.1 * t) * np.exp(-t / 0.045)
    env = np.exp(-t / (2.6 * (261.6 / f) ** 0.4))
    k0 = ns(dur)
    if k0 < n:
        env[k0:] *= np.exp(-taxis(n - k0) / 0.18)
    env[: ns(0.003)] *= np.linspace(0, 1, ns(0.003))
    x = (car + tine) * env
    return np.tanh(1.3 * x) / np.tanh(1.3) * vel


def pad(m, dur, rng, attack=1.6, release=2.6, cutoff=1600, voices=3, detune=7.0, vel=1.0):
    f = hz(m)
    n = ns(dur + release)
    out = np.zeros(n)
    H = int(max(2, min(14, 2600 // f)))
    for j in range(voices):
        c = (j - (voices - 1) / 2) * detune * 2 / max(voices - 1, 1)
        ph = phase_of(f * 2 ** (c / 1200), n, rng.uniform(0, 6.28))
        out += additive(ph, [(1.0 / k) * (0.6 if k % 2 == 0 else 1.0) for k in range(1, H + 1)])
    out = lp(out / voices, cutoff, 2)
    e = np.ones(n)
    na = min(n, ns(attack))
    e[:na] = np.sin(np.linspace(0, np.pi / 2, na)) ** 2
    k0 = ns(dur)
    if k0 < n:
        e[k0:] *= np.cos(np.linspace(0, np.pi / 2, n - k0)) ** 2
    return out * e * vel


def bell(m, vel, rng, length=4.0, decay=1.8):
    f = hz(m)
    n = ns(length)
    t = taxis(n)
    idx = 1.1 * vel * np.exp(-t / 0.5) + 0.08
    x = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * 2 * f * t)) * np.exp(-t / decay)
    if f * 2.76 < SR / 2.3:
        x += 0.22 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t / (decay * 0.25))
    if f * 5.4 < SR / 2.3:
        x += 0.08 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t / (decay * 0.08))
    x[: ns(0.002)] *= np.linspace(0, 1, ns(0.002))
    return x * vel


def bass(m, dur, vel, rng):
    f = hz(m)
    n = ns(dur + 0.25)
    t = taxis(n)
    x = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(4 * np.pi * f * t) * np.exp(-t / 0.4) + \
        0.12 * np.sin(6 * np.pi * f * t) * np.exp(-t / 0.2)
    e = np.exp(-t / 0.9)
    e[: ns(0.008)] *= np.linspace(0, 1, ns(0.008))
    k0 = ns(dur)
    if k0 < n:
        e[k0:] *= np.exp(-taxis(n - k0) / 0.06)
    return x * e * vel


def brush(rng, vel=1.0, dur=0.18):
    n = ns(dur)
    x = bp(white(n, rng), 2500, 9000, 2) * env_ar(n, 0.02, dur * 0.8, 1.0)
    return x * vel


# ============================================================================ mixing helpers
class Mix:
    def __init__(self, seconds):
        self.n = ns(seconds)
        self.bus = {}

    def add(self, bus, x, t, pan=0.0, gain=1.0):
        if bus not in self.bus:
            self.bus[bus] = np.zeros((self.n, 2))
        a = (pan + 1) * np.pi / 4
        start = ns(t)
        b = self.bus[bus]
        mix_into(b[:, 0], x * np.cos(a) * gain, start)
        mix_into(b[:, 1], x * np.sin(a) * gain, start)

    def get(self, bus):
        return self.bus.get(bus, np.zeros((self.n, 2)))


def stereo_reverb(x, rng, rt=3.0, wet=0.4, predelay=0.03, dark=4000, er=()):
    ir = make_ir(rng, [(0, 400, rt * 1.1), (400, dark, rt), (dark, SR / 2, rt * 0.45)], er=er,
                 predelay=predelay, onset=0.05, stereo=True)
    out = np.zeros((len(x) + len(ir) - 1, 2))
    mono_in = x.mean(axis=1) if x.ndim == 2 else x
    for c in range(2):
        out[:, c] = signal.fftconvolve(mono_in, ir[:, c])
    out *= wet
    out[: len(x)] += x if x.ndim == 2 else np.stack([x, x], axis=1)
    return out


def echo(x, delay, fb=0.4, taps=5, lp_hz=2500, pingpong=True):
    out = np.zeros((len(x) + ns(delay * (taps + 1)), 2))
    out[: len(x)] += x
    y = x.copy()
    for k in range(1, taps + 1):
        y = lp(y, lp_hz, 2) * fb
        if pingpong:
            y = y[:, ::-1]
        mix_into(out[:, 0], y[:, 0], ns(delay * k))
        mix_into(out[:, 1], y[:, 1], ns(delay * k))
    return out


def chorus(x, rng, depth_ms=2.5, rate=0.35, mix=0.5):
    n = len(x)
    t = taxis(n)
    out = x.copy()
    for c in range(2):
        d = (7 + depth_ms * np.sin(2 * np.pi * rate * t + c * np.pi / 2 + rng.uniform(0, 1))) * SR / 1000
        out[:, c] = (1 - mix) * x[:, c] + mix * vardelay(x[:, c], d)
    return out


def hiss_bed(n, rng, level_db=-52):
    h = np.stack([lp(pink(n, rng), 6000, 2), lp(pink(n, rng), 6000, 2)], axis=1)
    return h / max(rms(h), 1e-9) * db(level_db)


def crackle(n, rng, rate=6.0, level=0.03):
    imp = (rng.random(n) < rate / SR) * rng.uniform(0.2, 1.0, n) ** 2
    c = bp(imp, 600, 5000, 2)
    c = c / max(peak(c), 1e-9) * level
    return np.stack([c, c], axis=1)


def master(x, target=-20.0, fade_in=0.05, fade_out=2.0, length=None):
    if length is not None:
        x = pad_to(x, ns(length))
    x = fade_edges(x, fade_in, fade_out)
    x = x - x.mean(axis=0, keepdims=True)
    return normalize_lufs(x, target, ceiling_db=-1.5)


# ============================================================================ composition helpers
def scale_notes(pcs, lo, hi):
    return [m for m in range(lo, hi + 1) if m % 12 in pcs]


def melody(rng, chords, bpb, scale, lo, hi, rhythms, motif_bars=2, rest_prob=0.15, start=None,
           active=None):
    """Motif-based melody: a seeded 2-bar rhythm+contour motif repeated with variation, strong-beat
    notes snapped to chord tones (extensions included).  Returns [(bar, beat, dur_beats, midi)]."""
    notes = scale_notes(scale, lo, hi)
    idx = len(notes) // 2 if start is None else int(np.argmin([abs(n - start) for n in notes]))
    steps = [-2, -1, -1, 1, 1, 2, 0, 3, -3]
    motif = []
    for b in range(motif_bars):
        r = rhythms[int(rng.integers(len(rhythms)))]
        for beat, dur in r:
            motif.append((b, beat, dur, int(rng.choice(steps))))
    out = []
    bars = len(chords)
    center = len(notes) // 2
    for b0 in range(0, bars, motif_bars):
        rep = b0 // motif_bars
        for bo, beat, dur, st in motif:
            bar = b0 + bo
            if bar >= bars or (active is not None and not active[bar]):
                continue
            if rng.random() < rest_prob:
                continue
            if rep % 2 == 1 and rng.random() < 0.35:
                st = int(rng.choice([-2, -1, 1, 2]))
            if abs(idx - center) > len(notes) * 0.3:
                st = -abs(st) if idx > center else abs(st)
            idx = int(np.clip(idx + st, 0, len(notes) - 1))
            pcs = chords[bar]
            strong = (beat % (bpb / 2 if bpb % 2 == 0 else bpb)) == 0
            if strong and notes[idx] % 12 not in pcs:
                c = [i for i, nn in enumerate(notes) if nn % 12 in pcs]
                if c:
                    idx = min(c, key=lambda i: abs(i - idx))
            out.append((bar, beat, dur, notes[idx]))
    return out


def pcs_of(*groups):
    return {m % 12 for g in groups for m in g}


# ============================================================================ pieces
def _piano_piece(rng, bpm, bpb, prog, form, scale, mel_range, rhythms, worn, wow, title_seed, roll=0.03,
                 waltz=False):
    spb = 60.0 / bpm
    bars = [prog[k] for k in form]
    total = len(bars) * bpb * spb + 6.0
    mx = Mix(total)
    chords = [pcs_of(lh, rh) for lh, rh in bars]
    active = [i not in (0, len(bars) - 1) for i in range(len(bars))]
    mel = melody(rng, chords, bpb, scale, *mel_range, rhythms, active=active)
    for i, (lh, rh) in enumerate(bars):
        t0 = i * bpb * spb
        last = i == len(bars) - 1
        hold = bpb * spb * (2.2 if last else 1.0) + 0.3
        h = lambda: rng.normal(0, 0.012)
        mx.add("pno", piano(lh[0], hold, 0.55, rng, 2.5, 0.8, worn=worn), t0 + abs(h()), -0.2)
        if waltz:
            for bt in (1, 2):
                if bt == 2 and rng.random() < 0.5:
                    continue
                for j, m in enumerate(rh):
                    mx.add("pno", piano(m, spb * (bpb - bt) + 0.2, 0.32 + 0.05 * rng.random(), rng, 2.5, 0.7,
                                        worn=worn), t0 + bt * spb + j * 0.012 + h(), 0.1 + 0.1 * j)
        else:
            mx.add("pno", piano(lh[1], hold - 0.5 * spb * 2, 0.38, rng, 2.5, 0.7, worn=worn), t0 + 2.5 * spb + h(), -0.1)
            rt = 0.0 if i % 2 == 0 else 1.0
            for j, m in enumerate(rh):
                mx.add("pno", piano(m, hold - rt * spb, 0.36 + 0.06 * rng.random(), rng, 2.5, 0.75, worn=worn),
                       t0 + rt * spb + j * roll + h(), 0.05 + 0.12 * j)
    for bar, beat, dur, m in mel:
        t = (bar * bpb + beat) * spb + rng.normal(0, 0.015)
        mx.add("mel", piano(m, dur * spb + 0.4, 0.42 + 0.12 * rng.random(), rng, 2.0, 0.9, worn=worn), t, 0.15)
    x = mx.get("pno") + mx.get("mel") * 1.15
    x = lp(x, 5200, 2)
    x = np.stack([tape(x[:, 0], np.random.default_rng(title_seed), wow_ms=wow),
                  tape(x[:, 1], np.random.default_rng(title_seed), wow_ms=wow)], axis=1)
    x = saturate(x / max(peak(x), 1e-9) * 0.7, 1.6)
    x = stereo_reverb(x, rng, rt=2.8, wet=0.38, predelay=0.03, dark=3500)
    x = x[: ns(total)] + hiss_bed(ns(total), rng, -48) * peak(x)
    return master(x, -20, 0.05, 3.5)


def lobby_1(rng):
    Db = [1, 3, 5, 6, 8, 10, 0]
    prog = {
        "I": ([37, 44], [65, 72, 75]),   # Dbmaj9
        "vi": ([34, 41], [61, 68, 72]),  # Bbm9
        "IV": ([42, 49], [58, 65, 72]),  # Gbmaj7#11
        "V": ([44, 51], [60, 65, 70]),   # Ab6/9
        "iii": ([41, 48], [63, 68, 72]),  # Fm7
        "ii": ([39, 46], [61, 65, 66, 70]),  # Ebm9
        "Vs": ([44, 51], [61, 66, 70]),  # Ab9sus4
    }
    form = ["I"] + ["I", "vi", "IV", "V"] * 2 + ["iii", "vi", "ii", "Vs"] + ["I", "vi", "IV", "V"] + ["I"]
    rh = [[(0, 2), (2, 2)], [(0, 3), (3, 1)], [(1, 1), (2, 2)], [(0, 4)], [(0.5, 1.5), (2, 2)], [(0, 1), (1, 1), (2, 2)]]
    return _piano_piece(rng, 58, 4, prog, form, Db, (68, 84), rh, worn=5.0, wow=1.6, title_seed=101)


def lobby_2(rng):
    C = [0, 2, 4, 5, 7, 9, 11]
    prog = {
        "F": ([41, 48], [64, 69, 72]), "Em": ([40, 47], [62, 67, 71]), "Dm": ([38, 45], [64, 65, 72]),
        "C": ([36, 43], [64, 67, 71]), "Am": ([33, 40], [60, 67, 71]), "Es": ([40, 47], [62, 69, 71]),
        "G6": ([43, 50], [59, 64, 67]), "C9": ([36, 43], [62, 64, 71]), "Gs": ([43, 50], [60, 65, 69]),
    }
    form = (["F"] + ["F", "Em", "Dm", "C", "F", "Em", "Am", "Es"] + ["Dm", "G6", "C9", "Am", "Dm", "Gs", "C", "Am"]
            + ["F", "Em", "Dm", "C"] + ["C"])
    rh = [[(0, 2), (2, 1)], [(0, 3)], [(1, 2)], [(0, 1), (1, 2)], [(0.5, 2.5)]]
    return _piano_piece(rng, 50, 3, prog, form, C, (67, 83), rh, worn=7.0, wow=2.3, title_seed=102, waltz=True)


def _pad_piece(rng, bpm, bpb, prog, form, scale, mel_range, rhythms, mel_inst, total_extra=7.0, wide=True):
    spb = 60.0 / bpm
    bars = [prog[k] for k in form]
    total = len(bars) * bpb * spb + total_extra
    mx = Mix(total)
    chords = [pcs_of([b], p) for b, p in bars]
    nb = len(bars)
    active = [nb // 3 <= i < nb - 1 for i in range(nb)]
    mel = melody(rng, chords, bpb, scale, *mel_range, rhythms, rest_prob=0.25, active=active)
    i = 0
    while i < nb:
        j = i
        while j + 1 < nb and form[j + 1] == form[i]:
            j += 1
        b, p = bars[i]
        t0 = i * bpb * spb
        d = (j - i + 1) * bpb * spb + (2.5 if j == nb - 1 else 0.6)
        for k, m in enumerate(p):
            mx.add("pad", pad(m, d, rng, 2.2, 3.0, 1500, 3, 9.0, 0.5), t0 + rng.uniform(0, 0.15), (k / max(len(p) - 1, 1) - 0.5) * 1.2)
        sb = b if b >= 36 else b + 12
        mx.add("sub", np.sin(phase_of(hz(sb), ns(d + 1.5))) * env_ar(ns(d + 1.5), 1.5, 1.8) * 0.18, t0, 0.0)
        i = j + 1
    for bar, beat, dur, m in mel:
        t = (bar * bpb + beat) * spb + rng.normal(0, 0.01)
        if mel_inst == "bell":
            mx.add("mel", bell(m, 0.5 + 0.2 * rng.random(), rng, 4.5, 1.6), t, rng.uniform(-0.5, 0.5))
        else:
            mx.add("mel", rhodes(m, dur * spb + 0.5, 0.5 + 0.2 * rng.random(), rng), t, rng.uniform(-0.4, 0.4))
            if rng.random() < 0.35:
                mx.add("mel", bell(m + 12, 0.25, rng, 3.0, 1.2), t, rng.uniform(-0.6, 0.6))
    padbus = chorus(mx.get("pad"), rng, 3.0, 0.22, 0.5)
    melbus = echo(mx.get("mel"), spb * 0.75, 0.38, 5, 2800)
    x = pad_to(padbus, len(melbus)) * 0.9 + melbus * 0.8 + pad_to(mx.get("sub"), len(melbus))
    x = stereo_reverb(x, rng, rt=4.5, wet=0.5, predelay=0.04, dark=3000)
    x = x[: ns(total)]
    x = np.stack([tape(x[:, 0], np.random.default_rng(7), 0.6, 0.4, 0.05, 5.0, 0.6),
                  tape(x[:, 1], np.random.default_rng(7), 0.6, 0.4, 0.05, 5.0, 0.6)], axis=1)
    x = saturate(x / max(peak(x), 1e-9) * 0.6, 1.3)
    x += hiss_bed(len(x), rng, -50)
    return master(x, -20, 1.5, 5.0)


def pool_1(rng):
    E = [4, 6, 8, 9, 11, 1, 3]
    prog = {"I": (28, [56, 63, 66, 71]), "vi": (37, [56, 59, 64, 66]),
            "IV": (33, [56, 61, 64, 71]), "V": (35, [54, 61, 66, 68])}
    form = ["I", "I", "vi", "vi", "IV", "IV", "V", "V"] * 2 + ["I", "I", "vi", "vi"]
    rh = [[(0, 2), (2, 2)], [(0, 3)], [(1, 1), (2, 2)], [], [(0, 1.5), (1.5, 2.5)]]
    return _pad_piece(rng, 68, 4, prog, form, [4, 6, 8, 11, 1], (76, 92), rh, "bell")


def pool_2(rng):
    prog = {"I": (29, [57, 64, 67, 72]), "vi": (38, [57, 60, 64, 65]),
            "IV": (34, [57, 62, 64, 69]), "V": (36, [55, 62, 64, 69])}
    form = ["I", "I", "vi", "vi", "IV", "IV", "V", "V"] * 2 + ["I", "I"]
    rh = [[(0, 2), (2, 1), (3, 1)], [(0, 3)], [(0.5, 1.5), (2, 2)], [], [(1, 3)]]
    return _pad_piece(rng, 60, 4, prog, form, [5, 7, 9, 0, 2, 4], (72, 89), rh, "rhodes")


def _radio(x, rng, lo=260, hi=3800, crack=8.0):
    m = x.mean(axis=1)
    m = bp(m, lo, hi, 4)
    m = saturate(m / max(peak(m), 1e-9) * 0.8, 2.0)
    m = peq(m, 1200, 3, 1.0)
    n = len(m)
    m = m * (1 + 0.04 * smooth_random(n, 0.3, rng))
    st = bp(white(n, rng), lo, hi, 2)
    m = m + st / max(rms(st), 1e-9) * rms(m) * db(-30)
    cr = crackle(n, rng, crack, 0.08)[:, 0] * peak(m)
    m = m + cr
    return np.stack([m, m], axis=1)


def _root_bass(m):
    pc = m % 12
    b = 40 + ((pc - 40) % 12)
    return b


def office_radio_1(rng):
    bpm, bpb = 84, 4
    spb = 60.0 / bpm
    prog = {"Bb": (46, [57, 62, 65]), "Gm": (43, [58, 62, 65]), "Cm": (48, [58, 63, 67]), "F7": (41, [57, 63, 67]),
            "Dm": (50, [57, 60, 65]), "G7": (43, [59, 64, 65]), "Eb": (51, [58, 62, 67]), "Ebm": (51, [58, 60, 66])}
    A = ["Bb", "Gm", "Cm", "F7", "Dm", "G7", "Cm", "F7"]
    B = ["Eb", "Ebm", "Dm", "G7", "Cm", "F7", "Bb", "F7"]
    form = A + B + A
    bars = [prog[k] for k in form]
    total = len(bars) * bpb * spb + 1.5
    mx = Mix(total + 2)
    sw = 2.0 / 3.0
    for i, (b, rh) in enumerate(bars):
        t0 = i * bpb * spb
        hits = [(0.0, 1.3), (1 + sw, 0.7)] if i % 2 == 0 else [(0.0, 1.0), (2 + sw, 1.0)]
        if rng.random() < 0.3:
            hits.append((3 + sw, 0.3))
        for bt, d in hits:
            for j, m in enumerate(rh):
                mx.add("keys", rhodes(m, d * spb, 0.42 + 0.1 * rng.random(), rng), t0 + bt * spb + j * 0.006, 0)
        root = _root_bass(b)
        fifth = root + 7 if root + 7 <= 52 else root - 5
        mx.add("bass", bass(root, 1.8 * spb, 0.8, rng), t0)
        mx.add("bass", bass(fifth if i % 2 == 0 else root, 1.8 * spb, 0.7, rng), t0 + 2 * spb)
        for bt in (1, 3):
            mx.add("drm", brush(rng, 0.5 + 0.1 * rng.random(), 0.2), t0 + bt * spb)
        for bt in (0, 1, 2, 3):
            mx.add("drm", brush(rng, 0.15, 0.05), t0 + (bt + sw) * spb)
    chords = [pcs_of([b], p) for b, p in bars]
    Bb = [10, 0, 2, 3, 5, 7, 9]
    rh = [[(0, 1.5), (1 + sw, 1.0), (3, 1)], [(0, 3)], [(0, 1), (1, 1), (2, 2)], [(sw, 1.0), (2, 2)], [(0, 4)]]
    active = [True] * len(bars)
    mel = melody(rng, chords, bpb, Bb, 65, 82, rh, rest_prob=0.1, active=active)
    for bar, beat, dur, m in mel:
        mx.add("mel", bell(m, 0.55, rng, 2.5, 0.9) * (1 + 0.3 * np.sin(2 * np.pi * 5 * taxis(ns(2.5)))),
               (bar * bpb + beat) * spb + rng.normal(0, 0.01))
    x = mx.get("keys") * 0.8 + mx.get("bass") * 0.9 + mx.get("drm") * 0.25 + mx.get("mel") * 0.8
    x = stereo_reverb(x, rng, rt=0.9, wet=0.25, predelay=0.01)
    x = x[: ns(total)]
    x = np.stack([tape(x[:, 0], np.random.default_rng(5), 1.0), tape(x[:, 1], np.random.default_rng(5), 1.0)], axis=1)
    x = _radio(x, rng)
    return master(x, -20, 0.03, 0.6)


def office_radio_2(rng):
    bpm, bpb = 100, 3
    spb = 60.0 / bpm
    prog = {"F": (41, [57, 64, 67]), "Dm": (50, [57, 60, 65]), "Gm": (43, [58, 62, 65]), "C7": (48, [58, 64, 67]),
            "Am": (45, [55, 60, 64]), "D7": (50, [57, 60, 66]), "Bb": (46, [57, 62, 65]), "Bbm": (46, [56, 61, 67])}
    A = ["F", "Dm", "Gm", "C7", "Am", "D7", "Gm", "C7"]
    B = ["Bb", "Bbm", "Am", "D7", "Gm", "C7", "F", "C7"]
    form = A + B + A + B + A
    bars = [prog[k] for k in form]
    total = len(bars) * bpb * spb + 1.0
    mx = Mix(total + 2)
    for i, (b, rh) in enumerate(bars):
        t0 = i * bpb * spb
        mx.add("bass", bass(_root_bass(b), 0.9 * spb, 0.8, rng), t0)
        for bt in (1, 2):
            for j, m in enumerate(rh):
                mx.add("keys", piano(m, 0.8 * spb, 0.35 + 0.08 * rng.random(), rng, 3.0, 0.8, 2.0, worn=4.0),
                       t0 + bt * spb + j * 0.005, 0)
    chords = [pcs_of([b], p) for b, p in bars]
    F = [5, 7, 9, 10, 0, 2, 4]
    rh = [[(0, 2), (2, 1)], [(0, 1), (1, 1), (2, 1)], [(0, 3)], [(1, 2)], [(0, 1.5), (1.5, 1.5)]]
    mel = melody(rng, chords, bpb, F, 69, 86, rh, rest_prob=0.1, active=[True] * len(bars))
    for bar, beat, dur, m in mel:
        mx.add("mel", bell(m, 0.6, rng, 2.2, 0.8), (bar * bpb + beat) * spb + rng.normal(0, 0.008))
        mx.add("mel", piano(m - 12, dur * spb, 0.3, rng, 2.0, 1.0, 2.5, worn=4.0), (bar * bpb + beat) * spb)
    x = mx.get("keys") * 0.8 + mx.get("bass") * 0.9 + mx.get("mel") * 0.75
    x = stereo_reverb(x, rng, rt=1.1, wet=0.3, predelay=0.012)
    x = x[: ns(total)]
    x = np.stack([tape(x[:, 0], np.random.default_rng(6), 1.5), tape(x[:, 1], np.random.default_rng(6), 1.5)], axis=1)
    x = _radio(x, rng, 320, 3000, 12.0)
    return master(x, -20, 0.03, 0.6)


def _rhodes_piece(rng, bpm, bpb, prog, form, scale, mel_range, rhythms, bell_frac=0.3):
    spb = 60.0 / bpm
    bars = [prog[k] for k in form]
    nb = len(bars)
    total = nb * bpb * spb + 7.0
    mx = Mix(total)
    for i, (b, rh) in enumerate(bars):
        t0 = i * bpb * spb
        last = i == nb - 1
        d = bpb * spb * (1.6 if last else 1.0)
        mx.add("bass", bass(b, d, 0.5, rng) * 0.8, t0)
        for j, m in enumerate(rh):
            mx.add("keys", rhodes(m, d * 0.95, 0.38 + 0.08 * rng.random(), rng, 6.0, 0.7), t0 + j * 0.035 + rng.normal(0, 0.008),
                   (j / max(len(rh) - 1, 1) - 0.5) * 0.9)
            if i % 2 == 1 and not last and j == len(rh) - 1:
                mx.add("keys", rhodes(m + 12, spb * 1.5, 0.25, rng), t0 + 2.5 * spb, 0.5)
            mx.add("pad", pad(m, d, rng, 2.0, 2.8, 1200, 3, 8.0, 0.35), t0, (j / max(len(rh) - 1, 1) - 0.5) * 1.6)
    chords = [pcs_of([b], p) for b, p in bars]
    active = [3 <= i < nb - 2 for i in range(nb)]
    mel = melody(rng, chords, bpb, scale, *mel_range, rhythms, rest_prob=0.2, active=active)
    for bar, beat, dur, m in mel:
        t = (bar * bpb + beat) * spb + rng.normal(0, 0.012)
        if rng.random() < bell_frac:
            mx.add("mel", bell(m, 0.45, rng, 3.5, 1.4), t, rng.uniform(-0.6, 0.6))
        else:
            mx.add("mel", rhodes(m, dur * spb + 0.3, 0.5 + 0.15 * rng.random(), rng), t, rng.uniform(-0.3, 0.3))
    keys = chorus(mx.get("keys"), rng, 2.0, 0.6, 0.35)
    x = keys * 0.8 + mx.get("pad") * 0.7 + mx.get("bass") * 0.6 + echo(mx.get("mel"), spb * 0.5, 0.3, 4, 2400)[: mx.n] * 0.85
    x = lp(x, 3200, 2)
    x = stereo_reverb(x, rng, rt=3.6, wet=0.55, predelay=0.05, dark=2800)
    x = x[: ns(total)]
    x = np.stack([tape(x[:, 0], np.random.default_rng(9), 0.9), tape(x[:, 1], np.random.default_rng(10), 0.9)], axis=1)
    x = saturate(x / max(peak(x), 1e-9) * 0.65, 1.4)
    x += hiss_bed(len(x), rng, -50)
    return master(x, -20, 1.0, 5.0)


def plains_1(rng):
    prog = {"IV": (36, [59, 62, 64, 67]), "iii": (35, [57, 62, 66]), "ii": (45, [55, 59, 60, 64]), "V": (38, [55, 60, 64])}
    form = ["IV", "iii", "ii", "V"] * 4 + ["IV"]
    rh = [[(0, 2), (2, 2)], [(0, 3), (3, 1)], [(1, 3)], [], [(0, 1), (1, 1), (2, 2)]]
    return _rhodes_piece(rng, 62, 4, prog, form, [7, 9, 11, 2, 4], (67, 84), rh)


def plains_2(rng):
    prog = {"I": (39, [55, 58, 62, 65]), "vi": (36, [55, 58, 62, 63]), "IV": (44, [55, 60, 62, 67]), "V": (46, [53, 55, 60, 65])}
    form = ["I", "vi", "IV", "V"] * 4 + ["I", "I"]
    rh = [[(0, 3), (3, 1)], [(0, 2), (2, 2)], [(1.5, 2.5)], [], [(0, 4)]]
    return _rhodes_piece(rng, 56, 4, prog, form, [3, 5, 7, 10, 0], (65, 82), rh, bell_frac=0.5)


def garage_1(rng):
    total = 82.0
    n = ns(total)
    t = taxis(n)
    drone = np.zeros(n)
    for m, a in ((25, 0.3), (37, 0.5), (44, 0.35), (49, 0.2)):
        f = hz(m) * (1 + 0.002 * smooth_random(n, 0.05, rng))
        drone += a * additive(phase_of(f, n, rng.uniform(0, 6)), [1 / k for k in range(1, 18)])
    w = np.stack([np.clip(smooth_random(n, 0.04, rng), 0, 1) + 0.2, np.clip(smooth_random(n, 0.05, rng), 0, 1),
                  np.clip(smooth_random(n, 0.03, rng), 0, 1) ** 2])
    drone = multi_cutoff_lp(drone, [180, 450, 1000], w)
    drone *= env_points(n, [(0, 0), (8, 1), (total - 10, 1), (total, 0)])
    mx = Mix(total + 2)
    mx.add("drone", drone, 0.0)
    cm = [52, 56, 59, 63, 66]  # C#m9 colour: E G# B D# F#
    for k, t0 in enumerate([6.0, 24.0, 42.0, 60.0]):
        for j, m in enumerate(cm[: 3 + (k % 3)]):
            mx.add("pad", pad(m - (12 if j == 0 else 0), 6.0, rng, 4.0, 6.0, 900, 3, 10.0, 0.3), t0 + j * 0.4,
                   (j - 2) * 0.35)
    penta = scale_notes([1, 4, 6, 8, 11], 61, 80)
    tp = 10.0
    while tp < total - 12:
        idx = int(rng.integers(2, len(penta) - 4))
        for q in range(int(rng.integers(3, 6))):
            if q:
                idx = int(np.clip(idx + rng.choice([-2, -1, -1, 1, 2]), 0, len(penta) - 1))
            m = penta[idx]
            inst = piano(m, 1.5, 0.35, rng, 3.0, 0.7, 4.0, worn=3.0) if rng.random() < 0.6 else bell(m, 0.35, rng, 3.5, 1.5)
            mx.add("frag", inst, tp + q * rng.uniform(0.6, 1.1), rng.uniform(-0.7, 0.7))
        tp += rng.uniform(8, 13)
    frag = echo(mx.get("frag"), 0.74, 0.45, 6, 1800)
    x = pad_to(mx.get("drone"), len(frag)) * 0.7 + pad_to(mx.get("pad"), len(frag)) * 0.6 + frag * 0.9
    x = stereo_reverb(x, rng, rt=3.2, wet=0.6, predelay=0.04, dark=2200,
                      er=[(0.031, 0.3, 4000), (0.057, 0.25, 3000)])
    x = lp(x[: n], 2500, 2)
    x += hiss_bed(n, rng, -52)
    return master(x, -20, 2.0, 6.0)


PIECES = {
    "music.lobby_1": lobby_1, "music.lobby_2": lobby_2, "music.pool_1": pool_1, "music.pool_2": pool_2,
    "music.office_radio_1": office_radio_1, "music.office_radio_2": office_radio_2,
    "music.plains_1": plains_1, "music.plains_2": plains_2, "music.garage_1": garage_1,
}
