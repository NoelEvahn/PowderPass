"""Original synthetic voices: espeak-ng speech (GPL tool; its output is not encumbered) + formant
synthesis, processed into distant / whispered / PA / radio voices.  No real person is imitated.
"""
from __future__ import annotations

import os
import subprocess
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile

from dsp import (SR, ns, taxis, db, peak, rms, white, pink, smooth_random, lp, hp, bp, peq, reson,
                 env_exp, env_ar, env_points, fade_edges, phase_of, additive, saturate, make_ir, apply_ir,
                 get_profile_ir, mix_into, pad_to, resample_ratio)

ESPEAK = os.environ.get("ESPEAK", "/usr/bin/espeak-ng")


def speak(text, voice="en-us+m3", pitch=50, speed=150, gap=0, amp=100):
    """Render text with espeak-ng; returns mono float at SR with leading/trailing silence trimmed."""
    with tempfile.TemporaryDirectory() as td:
        path = os.path.join(td, "s.wav")
        subprocess.run([ESPEAK, "-v", voice, "-p", str(int(pitch)), "-s", str(int(speed)), "-a", str(int(amp)),
                        "-g", str(int(gap)), "-w", path, text], check=True, capture_output=True)
        sr, x = wavfile.read(path)
    x = x.astype(np.float64) / 32768.0
    if x.ndim == 2:
        x = x.mean(axis=1)
    from math import gcd
    g = gcd(SR, sr)
    x = signal.resample_poly(x, SR // g, sr // g)
    a = np.abs(x)
    idx = np.nonzero(a > peak(x) * db(-45))[0]
    if len(idx):
        x = x[max(0, idx[0] - ns(0.01)): idx[-1] + ns(0.05)]
    return hp(x, 70, 2)


SYL_C = ["m", "n", "s", "sh", "l", "r", "t", "d", "k", "h", "v", "w", "f", "b", "p"]
SYL_V = ["a", "e", "i", "o", "u", "ah", "eh", "oo", "ay", "ow"]


def gibberish(rng, words=6):
    out = []
    for _ in range(words):
        syl = int(rng.integers(1, 4))
        w = "".join(SYL_C[rng.integers(len(SYL_C))] + SYL_V[rng.integers(len(SYL_V))] +
                    (SYL_C[rng.integers(len(SYL_C))] if rng.random() < 0.3 else "") for _ in range(syl))
        out.append(w)
    return " ".join(out)


def env_follow(x, tau=0.02):
    k = np.exp(-1 / (tau * SR))
    return signal.filtfilt([1 - k], [1, -k], np.abs(x))


def shuffle_chunks(rng, x, chunk=(0.12, 0.3), rev_prob=0.5):
    """Cut into chunks, reverse some, crossfade back together — keeps prosody, kills intelligibility."""
    out = []
    i = 0
    while i < len(x):
        k = ns(rng.uniform(*chunk))
        c = x[i:i + k].copy()
        if rng.random() < rev_prob:
            c = c[::-1]
        out.append(fade_edges(c, 0.008, 0.008))
        i += k
    return np.concatenate(out) if out else x


def pa_chain(x, rng, drive=2.5):
    y = bp(x, 300, 3400, 4)
    y = saturate(y / max(peak(y), 1e-9), drive)
    y = peq(y, 2000, 4, 1.5)
    out = np.zeros(len(y) + ns(0.5))
    out[: len(y)] += y
    for d, g in ((0.045, -6), (0.11, -10), (0.19, -14), (0.27, -19)):
        mix_into(out, lp(y, 2800, 2) * db(g), ns(d))
    ir = make_ir(rng, [(0, 500, 1.4), (500, 3000, 1.2), (3000, SR / 2, 0.7)], er=[(0.013, 0.3, 3000)],
                 predelay=0.015)
    return apply_ir(out, ir, 1.0, 0.35)


def chime(rng):
    def tone(f, d):
        n = ns(d)
        t = taxis(n)
        x = np.sin(2 * np.pi * f * t + 0.8 * np.exp(-t / 0.3) * np.sin(2 * np.pi * f * 2 * t))
        x += 0.25 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t / 0.25)
        return x * env_exp(n, 0.7, 0.004)
    n = ns(1.6)
    out = np.zeros(n)
    mix_into(out, tone(659.25, 1.5), 0)
    mix_into(out, tone(523.25, 1.2), ns(0.42))
    return out * 0.6


# ------------------------------------------------------------------------------------------ events
WHISPER_VOICES = [("en+whisper", 45, 120), ("en+whisperf", 60, 115), ("en-us+whisper", 30, 105),
                  ("en+whisperf", 40, 130), ("en-gb+whisper", 55, 110), ("en-us+whisperf", 70, 125)]


def voice_whisper(rng, v):
    vo, p, s = WHISPER_VOICES[v % 6]
    x = speak(gibberish(rng, int(rng.integers(5, 9))), vo, p, s)
    x = shuffle_chunks(rng, x, (0.1, 0.25), 0.5)
    x = resample_ratio(x, rng.uniform(0.92, 1.06))
    x = x[: ns(4.0)]
    e = env_follow(x, 0.03)
    br = bp(pink(len(x), rng), 900, 6500, 2) * e / max(peak(e), 1e-9)
    y = hp(x, 350, 2) / max(rms(x), 1e-9) + 0.5 * br / max(rms(br), 1e-9)
    y = lp(y, 7000, 2)
    return fade_edges(y, 0.01, 0.15)


def voice_murmur(rng, v):
    layers = []
    voices = [("en-us+m3", 30), ("en+m1", 22), ("en-gb+m7", 38), ("en+f2", 40), ("en-us+m6", 26), ("en+m4", 34)]
    for j in range(2 + (v % 2)):
        vo, p = voices[(v * 2 + j) % len(voices)]
        x = speak(gibberish(rng, int(rng.integers(6, 10))), vo, p + rng.integers(-6, 6), rng.integers(120, 150))
        x = x[::-1]
        x = resample_ratio(x, rng.uniform(0.82, 0.92))
        layers.append((x, rng.uniform(0, 0.6)))
    n = ns(3.8)
    out = np.zeros(n)
    for x, off in layers:
        mix_into(out, x / max(rms(x), 1e-9) * rng.uniform(0.6, 1.0), ns(off))
    out = lp(hp(out, 120, 2), 1500, 2)
    out *= env_points(n, [(0, 0), (0.25, 1), (3.3, 1), (3.8, 0)])
    return out


def voice_conversation(rng, v):
    pairs = [(("en-us+m3", 35), ("en+f3", 55)), (("en-gb+m1", 30), ("en-us+f4", 60)),
             (("en+m7", 40), ("en-gb+f2", 50)), (("en-us+m5", 28), ("en+m2", 44))]
    a, b = pairs[v % 4]
    n = ns(6.0)
    out = np.zeros(n)
    tpos = rng.uniform(0.05, 0.3)
    turn = 0
    while tpos < 5.2:
        vo, p = (a, b)[turn % 2]
        x = speak(gibberish(rng, int(rng.integers(3, 7))), vo, p + rng.integers(-8, 8), rng.integers(140, 175), gap=2)
        x = x / max(rms(x), 1e-9) * (1.0 if turn % 2 == 0 else 0.8)
        mix_into(out, x, ns(tpos))
        tpos += len(x) / SR + rng.uniform(-0.15, 0.6)
        turn += 1
    out = out[:n] * env_points(n, [(0, 1), (5.6, 1), (6.0, 0)])
    ir, _, _ = get_profile_ir("office")
    out = apply_ir(out, ir, 1.0, 0.45)[:n + ns(0.6)]
    out = peq(lp(out, 500, 4), 160, 3, 1.0)
    return out


PHRASES = [("Is anybody there?", "en-us+m3", 44, 135),
           ("Can you hear me?", "en+f3", 58, 130),
           ("Hello?", "en-gb+m7", 50, 115),
           ("I think someone is here.", "en-us+f2", 52, 140)]


def voice_phrase(rng, v):
    text, vo, p, s = PHRASES[v % 4]
    x = speak(text, vo, p, s)
    x = np.concatenate([np.zeros(ns(0.03)), x])
    x = lp(hp(x, 160, 2), 4200, 2)
    x = saturate(x / max(peak(x), 1e-9) * 0.8, 1.2)
    out = np.zeros(len(x) + ns(0.4))
    out[: len(x)] = x
    for d, g in ((0.009, -9), (0.017, -12)):
        mix_into(out, lp(x, 2500, 2) * db(g), ns(d))
    return out


ANNOUNCE = [("Please remain where you are.", "en-gb+m1", 38, 125),
            ("This area is closed.", "en-us+f1", 50, 125),
            ("Do not enter.", "en-gb+m4", 34, 110)]


def voice_announce(rng, v):
    text, vo, p, s = ANNOUNCE[v % 3]
    x = speak(text, vo, p, s)
    ch = chime(rng)
    n = len(ch) + ns(0.1) + len(x)
    dry = np.zeros(n)
    mix_into(dry, ch / max(peak(ch), 1e-9) * 0.5, 0)
    mix_into(dry, x / max(peak(x), 1e-9), len(ch) + ns(0.1))
    return pa_chain(dry, rng)


RADIO = [("Can you hear me?", "en-us+m2", 40, 150),
         ("Is anybody there? Hello?", "en+f4", 55, 145),
         ("Please respond. Over.", "en-gb+m3", 36, 140)]


def voice_radio(rng, v):
    text, vo, p, s = RADIO[v % 3]
    x = speak(text, vo, p, s)
    x = bp(x, 400, 2800, 4)
    x = np.tanh(5 * x / max(peak(x), 1e-9)) * 0.6
    pre, post = ns(0.18), ns(0.25)
    n = pre + len(x) + post
    v_track = np.zeros(n)
    v_track[pre: pre + len(x)] = x
    gate = np.ones(n)
    for _ in range(int(rng.integers(2, 5))):
        s0 = int(rng.uniform(pre, pre + len(x) - ns(0.2)))
        gate[s0: s0 + ns(rng.uniform(0.06, 0.16))] = 0.08
    gate = np.convolve(gate, np.ones(ns(0.004)) / ns(0.004), "same")
    stat = bp(white(n, rng), 300, 3500, 2)
    stat /= max(rms(stat), 1e-9)
    sq = env_points(n, [(0, 1), (pre / SR, 1), (pre / SR + 0.02, 0.12), ((n - post) / SR, 0.12),
                        ((n - post) / SR + 0.02, 1), (n / SR - 0.03, 1), (n / SR, 0)])
    sq = np.maximum(sq, (1 - gate) * 0.7)
    out = v_track * gate / max(rms(x), 1e-9) * 0.3 + stat * sq * 0.12
    for c in (pre, n - post):
        mix_into(out, np.exp(-taxis(ns(0.01)) / 0.001) * 0.8, c)
    cr = (rng.random(n) < 30 / SR) * rng.uniform(0.2, 1, n)
    out += bp(cr, 800, 3000, 2) * 0.6
    return out


def voice_cry(rng, v):
    dur = 3.4
    n = ns(dur)
    t = taxis(n)
    if v == 0:
        f0 = env_points(n, [(0, 330), (0.7, 470), (1.6, 430), (2.4, 300), (dur, 280)])
        vow = [(800, 8, 1.0), (1150, 9, 0.6), (2900, 12, 0.25)]
        amp = env_points(n, [(0, 0), (0.2, 1), (1.8, 0.9), (2.6, 0)])
    else:
        f0 = env_points(n, [(0, 380), (0.4, 440), (0.9, 360), (1.3, 380), (1.7, 450), (2.6, 320), (dur, 300)])
        vow = [(500, 8, 1.0), (900, 9, 0.6), (2500, 12, 0.2)]
        amp = env_points(n, [(0, 0), (0.15, 1), (0.9, 0.7), (1.15, 0.1), (1.35, 0.9), (2.4, 0.8), (2.9, 0)])
    f0 = f0 * (1 + 0.02 * np.sin(2 * np.pi * 5.3 * t) + 0.01 * smooth_random(n, 8, rng))
    ph = phase_of(f0, n)
    H = int(SR / 2 / 520)
    src = additive(ph, [k ** -1.4 for k in range(1, min(H, 30) + 1)])
    src = src + 0.15 * white(n, rng)
    y = sum(a * reson(src, f, q) for f, q, a in vow) * amp
    y = lp(y, 1300, 2)
    ir = make_ir(rng, [(0, 400, 3.0), (400, SR / 2, 2.2)], er=[(0.12, 0.35, 1500), (0.29, 0.2, 1200)],
                 predelay=0.04, onset=0.08)
    return apply_ir(y, ir, 0.25, 1.0)


# ------------------------------------------------------------------------------------------ creature
def _glide_resample(x, ratio):
    """Read x with a per-output-sample speed ratio (pitch + formants follow); stops at the end of x."""
    pos = np.cumsum(ratio) - ratio[0]
    pos = pos[pos < len(x) - 1]
    return np.interp(pos, np.arange(len(x)), x)


SCREECH = [  # (espeak voice, pitch, f0 contour points, human-layer speed ratio contour)
    ("en-us+f3", 85, [(0, 1050), (0.12, 1350), (0.45, 1150), (1.3, 520), (2.0, 300)], [(0, 1.45), (2.0, 0.55)]),
    ("en+m3", 70, [(0, 900), (0.08, 1500), (0.3, 1250), (0.55, 1380), (1.4, 430), (2.0, 260)], [(0, 1.3), (2.0, 0.5)]),
]


def skinwalker_screech(rng, v):
    from sfx import _chitin_click  # shared click model
    dur = 2.0
    n = ns(dur)
    t = taxis(n)
    vo, p, f0pts, rpts = SCREECH[v % 2]
    amp = env_points(n, [(0, 0), (0.025, 1), (0.25, 0.9), (1.2, 0.8), (1.75, 0.35), (dur, 0)])
    # 1) inhuman shriek: bright harmonic source, period-doubling roughness, an inharmonic second voice,
    #    high 'scream' formants, hard saturation
    f0 = env_points(n, f0pts) * (1 + 0.035 * smooth_random(n, 25, rng) + 0.012 * np.sin(2 * np.pi * 7.5 * t))
    ph = phase_of(f0, n)
    H = int(SR / 2 * 0.9 / 1500)
    src = additive(ph, [k ** -0.9 for k in range(1, H + 1)])
    src *= 1 + 0.55 * np.sin(ph / 2 + 0.3)                       # sub-harmonic rasp
    src2 = additive(phase_of(f0 * 1.414 * (1 + 0.006 * smooth_random(n, 3, rng)), n),
                    [k ** -1.1 for k in range(1, int(H / 1.414) + 1)])
    src = src + 0.45 * src2 + 0.25 * white(n, rng)
    shriek = sum(a * reson(src, f, q) for f, q, a in [(1500, 4, 1.0), (2900, 6, 0.8), (4300, 7, 0.45)])
    shriek = saturate(shriek / max(peak(shriek), 1e-9), 5.0)
    shriek = bp(shriek, 280, 9000, 2)
    # 2) almost-human layer: a pure espeak vowel (phoneme input, no words), pitch/formants dragged down,
    #    ping-ponged to length, ring-modulated a little, saturated
    vow = speak("[[A:A:A:A:A:]]", vo, p, 70)
    vow = np.concatenate([fade_edges(vow, 0.01, 0.03), fade_edges(vow[::-1], 0.03, 0.03)])
    ratio = env_points(n * 2, [(a, b) for a, b in rpts] + [(4.0, rpts[-1][1])])
    hum = pad_to(_glide_resample(np.tile(vow, 3), ratio), n)
    hum = hum * (0.7 + 0.3 * np.sin(2 * np.pi * rng.uniform(34, 48) * t))
    hum = saturate(hum / max(peak(hum), 1e-9), 3.0)
    hum = bp(hum, 180, 5000, 2)
    # 3) throat growl under the drop
    g0 = env_points(n, [(0, 95), (dur, 62)]) * (1 + 0.05 * smooth_random(n, 15, rng))
    growl = additive(phase_of(g0, n), [k ** -1.2 for k in range(1, 60)])
    growl = sum(a * reson(growl, f, q) for f, q, a in [(520, 4, 1.0), (950, 5, 0.6), (2300, 6, 0.3)])
    growl = saturate(growl / max(peak(growl), 1e-9), 4.0) * env_points(n, [(0, 0), (0.3, 0.2), (1.2, 1), (dur, 0.6)])
    y = (rms_norm(shriek) + 0.55 * rms_norm(hum) + 0.35 * rms_norm(growl)) * amp
    # 4) mandible clicks: a rattle at the onset, scattered clicks, a rattle as it dies away
    clk = np.zeros(n)
    for start, cnt, gap in ((0.0, int(rng.integers(6, 11)), (0.012, 0.025)),
                            (rng.uniform(1.55, 1.7), int(rng.integers(6, 12)), (0.018, 0.04))):
        q = start
        for _ in range(cnt):
            mix_into(clk, _chitin_click(rng, rng.uniform(0.8, 1.1)) * rng.uniform(0.5, 1.0), ns(q))
            q += rng.uniform(*gap)
    for _ in range(int(rng.integers(8, 15))):
        mix_into(clk, _chitin_click(rng, rng.uniform(0.8, 1.2)) * rng.uniform(0.3, 0.8), ns(rng.uniform(0.2, 1.5)))
    y = y + 2.2 * clk / max(peak(clk), 1e-9) * peak(y)
    ir, _, _ = get_profile_ir("office")
    return apply_ir(y, ir, 1.0, 0.08)


def rms_norm(x):
    r = rms(x)
    return x / r if r > 0 else x
