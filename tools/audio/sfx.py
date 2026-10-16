"""Procedural sound effects and ambience beds.

Loop generators:   fn(rng, v, n, P) -> (tonal_period[P] | None, noisy[n] | None)
One-shot generators: fn(rng, v) -> mono float array (normalised later)
"""
from __future__ import annotations

import numpy as np
from scipy import signal

from dsp import (periodic_smooth, SR, ns, taxis, db, peak, rms, white, pink, brown, smooth_random, lp, hp, bp, peq,
                 reson, circ_filter, circ_conv, multi_cutoff_lp, env_exp, env_ar, env_points,
                 fade_edges, osc_sine, phase_of, additive, modal, saturate, make_ir, apply_ir,
                 get_profile_ir, qf, mix_into, pad_to)


# ============================================================================ building blocks
def mix_wrap(buf, x, start):
    """Add x into periodic buffer buf starting at start, wrapping around."""
    p = len(buf)
    start %= p
    while len(x):
        k = min(p - start, len(x))
        buf[start:start + k] += x[:k]
        x = x[k:]
        start = 0


def unit(x):
    r = rms(x)
    return x / r if r > 0 else x


def burst(rng, dur, tau, lo=None, hi=None):
    n = ns(dur)
    x = white(n, rng) * env_exp(n, tau, 0.0005)
    if lo and hi:
        x = bp(x, lo, hi, 2)
    elif hi:
        x = lp(x, hi, 2)
    elif lo:
        x = hp(x, lo, 2)
    return x


def thump(rng, f0=80, f1=40, tau=0.05, dur=0.3, noise=0.4):
    n = ns(dur)
    t = taxis(n)
    f = f1 + (f0 - f1) * np.exp(-t / 0.03)
    x = np.sin(phase_of(f, n)) * env_exp(n, tau, 0.001)
    x += noise * lp(white(n, rng), 250, 2) * env_exp(n, tau * 0.6, 0.0005) * 2
    return x


def clunk(rng, heavy=1.0):
    """Relay / contactor clunk: low thump + metallic click + short ring."""
    n = ns(0.35)
    x = heavy * thump(rng, 95, 48, 0.05, 0.35, 0.5)
    ex = burst(rng, 0.004, 0.0008, 800, 9000)
    ring = modal([1750 * rng.uniform(0.9, 1.1), 2880, 4350, 6100], [1, 0.6, 0.4, 0.2],
                 [0.03, 0.02, 0.012, 0.008], ns(0.12))
    x[: ns(0.12) + len(ex) - 1] += 0.6 * signal.fftconvolve(ex, ring)[: ns(0.12) + len(ex) - 1] * 8
    second = burst(rng, 0.003, 0.0006, 1500, 9000) * 0.25
    mix_into(x, second, ns(0.006 + rng.uniform(0, 0.004)))
    return x


def drop(rng, f0, rise=1.0, tau=0.03, amp=1.0, click=0.15, dur=0.18):
    """Water drop / bubble 'plip': exponentially rising sinusoid (Minnaert bubble)."""
    n = ns(dur)
    t = taxis(n)
    f = f0 * (1 + rise * (1 - np.exp(-t / 0.02)))
    x = np.sin(phase_of(f, n)) * env_exp(n, tau, 0.0008)
    if click:
        x += click * hp(white(n, rng), 3000, 2) * env_exp(n, 0.0007, 0.0)
    return amp * x


def harm_tone(P, f0, amps, rng, detune_cycles=0, phases=None):
    """Periodic harmonic stack: harmonic k at k*f0 (+ detune) — f0 must be loop-quantised."""
    t = taxis(P)
    out = np.zeros(P)
    df = detune_cycles * SR / P
    for k, a in enumerate(amps, start=1):
        f = k * f0 + df
        if a == 0 or f > SR * 0.45:
            continue
        ph = rng.uniform(0, 2 * np.pi) if phases is None else phases[k - 1]
        out += a * np.sin(2 * np.pi * f * t + ph)
    return out


def periodic_am(P, items):
    """1 + sum(depth*sin(2*pi*qf(rate)*t + ph)) with loop-quantised rates."""
    t = taxis(P)
    m = np.ones(P)
    for rate, depth, ph in items:
        m += depth * np.sin(2 * np.pi * qf(rate, P) * t + ph)
    return m


def preroll(fn, n, pre):
    """Render fn(total_len) and return the last n samples (so reverbs/filters are 'warmed up')."""
    y = fn(n + ns(pre))
    return y[-n:]


def far(x, lp_hz=2500, profile=None, wet=None):
    y = lp(x, lp_hz, 2)
    if profile:
        ir, d, w = get_profile_ir(profile)
        y = apply_ir(y, ir, d * 0.6, w if wet is None else wet)
    return y


def room(x, wet=0.15, profile="office"):
    ir, d, _ = get_profile_ir(profile)
    return apply_ir(x, ir, 1.0, wet)


# ============================================================================ electrical / light
def _tube(P, rng, f0, tilt=1.25, odd=1.8, rattle=0.04, maxf=7000, detune=0):
    K = int(maxf // f0)
    amps = []
    for k in range(1, K + 1):
        f = k * f0
        a = k ** -tilt
        if k % 2 == 1 and k >= 3:
            a *= odd
        a += rattle * np.exp(-((f - 2900) / 1300) ** 2)
        a *= db(rng.normal(0, 2.5))
        amps.append(a)
    return harm_tone(P, f0, amps, rng, detune)


def hum_fluorescent(rng, v, n, P):
    f0 = qf(120, P)
    tilt = [1.25, 1.1, 1.4][v % 3]
    odd = [1.8, 2.4, 1.5][v % 3]
    ton = _tube(P, rng, f0, tilt, odd, rattle=[0.04, 0.07, 0.025][v % 3])
    ton += 0.15 * harm_tone(P, qf(60, P), [0, 0, 0.5, 0, 0.3, 0, 0.2], rng)  # weak odd-60 family
    ton *= periodic_am(P, [(0.37 + 0.11 * v, 0.035, rng.uniform(0, 6)), (1.3, 0.015, 0.0)])
    ton = circ_filter(ton, lambda x: lp(x, 5500, 2))
    ir, _, _ = get_profile_ir("office")
    ton = unit(ton + 0.18 * circ_conv(ton, ir))
    t = taxis(n)
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * qf(120, P) * t)) ** 6
    buzz = bp(pink(n, rng), 1500, 6000, 2) * gate
    roomtone = lp(brown(n, rng), 180, 2)
    noi = 0.12 * unit(buzz) + 0.08 * unit(roomtone) + 0.02 * unit(hp(white(n, rng), 6000, 2))
    return ton, noi


def hum_fluorescent_dense(rng, v, n, P):
    f0 = qf(120, P)
    ton = np.zeros(P)
    for j, det in enumerate([0, 1, -1, 2, -3, 3]):
        tube = _tube(P, rng, f0, rng.uniform(1.05, 1.45), rng.uniform(1.4, 2.4), rng.uniform(0.02, 0.08),
                     detune=det)
        tube *= periodic_am(P, [(rng.uniform(0.2, 0.9), 0.04, rng.uniform(0, 6))])
        ton += unit(tube) * rng.uniform(0.5, 1.0)
    ton = circ_filter(ton, lambda x: lp(x, 5000, 2))
    ir, _, _ = get_profile_ir("office")
    ton = unit(ton + 0.25 * circ_conv(ton, ir))
    t = taxis(n)
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * f0 * t)) ** 4
    buzz = bp(pink(n, rng), 1200, 7000, 2) * gate
    noi = 0.16 * unit(buzz) + 0.1 * unit(lp(brown(n, rng), 200, 2))
    return ton, noi


def hum_mains(rng, v, n, P):
    f0 = qf(60, P)
    ton = harm_tone(P, f0, [1.0, 0.6, 0.35, 0.22, 0.12, 0.08, 0.04, 0.03], rng)
    ton *= periodic_am(P, [(0.21, 0.05, 0.3), (0.67, 0.02, 1.0)])
    ton = circ_filter(ton, lambda x: lp(x, 450, 2))
    ir, _, _ = get_profile_ir("concrete")
    ton = unit(0.5 * ton + circ_conv(lp(ton, 400, 2), ir) * 0.5)
    noi = 0.25 * unit(lp(brown(n, rng), 90, 2))
    return ton, noi


def transformer_hum(rng, v, n, P):
    f120, f60 = qf(120, P), qf(60, P)
    t = taxis(P)
    w = np.sin(2 * np.pi * f120 * t) + 0.25 * np.sin(2 * np.pi * f60 * t + 0.7)
    ton = np.tanh(2.6 * (w + 0.18))
    ton -= ton.mean()
    ton += 0.5 * harm_tone(P, f120, [0, 0.6, 0.4, 0.3, 0.1, 0.15, 0.05, 0.08], rng)
    rat = [0.0] * 30
    for k in range(10, 30):
        rat[k - 1] = 0.03 * np.exp(-((k * 120 - 2200) / 700) ** 2) * db(rng.normal(0, 3))
    ton += harm_tone(P, f120, rat, rng)
    ton *= periodic_am(P, [(0.27, 0.03, 0.0)])
    ton = unit(circ_filter(ton, lambda x: lp(x, 3200, 2)))
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * f120 * taxis(n))) ** 8
    noi = 0.1 * unit(bp(white(n, rng), 1500, 5000, 2) * gate) + 0.15 * unit(lp(brown(n, rng), 120, 2))
    return ton, noi


def _buzz(rng, n, f=120.0):
    """Non-periodic fluorescent buzz for one-shots."""
    ph = phase_of(f, n)
    amps = [k ** -1.2 * (1.8 if k % 2 and k > 2 else 1) * db(rng.normal(0, 2)) for k in range(1, 50)]
    x = additive(ph, amps)
    return lp(x, 6000, 2)


def light_flicker(rng, v):
    dur = 1.5
    n = ns(dur)
    on = np.zeros(n)
    t0 = 0.02
    events = []
    k = 0
    while t0 < dur - 0.25:
        seg = rng.uniform(0.03, 0.16) if k < 6 else rng.uniform(0.15, 0.4)
        off = rng.uniform(0.04, 0.22)
        a, b = ns(t0), ns(min(dur - 0.05, t0 + seg))
        on[a:b] = rng.uniform(0.5, 1.0)
        events.append(t0)
        t0 += seg + off
        k += 1
    if v % 2 == 0:  # ends lit
        on[ns(t0 - 0.0):] = 0.9
    sm = signal.windows.hann(ns(0.004))
    on = np.convolve(on, sm / sm.sum(), mode="same")
    x = unit(_buzz(rng, n)) * on
    x += 0.15 * hp(pink(n, rng), 2000, 2) * on
    for e in events:  # starter ticks
        ex = burst(rng, 0.003, 0.0006, 1000, 12000)
        tk = signal.fftconvolve(ex, modal([2300 * rng.uniform(0.9, 1.1), 4100, 6300], [1, 0.6, 0.3],
                                          [0.008, 0.005, 0.003], ns(0.03)))
        mix_into(x, tk * rng.uniform(4, 9), ns(e) - ns(0.002))
    x = room(fade_edges(x, 0.001, 0.08), 0.12)
    return x


def light_pop(rng, v):
    n = ns(0.6)
    x = np.zeros(n)
    mix_into(x, 2.5 * burst(rng, 0.05, 0.004 + 0.002 * v, None, 5000 + 1500 * v), 0)
    x += 1.2 * pad_to(thump(rng, 140, 50, 0.025, 0.2, 0.3), n)
    glass = modal(np.array([3200, 4700, 6900, 8800]) * rng.uniform(0.85, 1.15), [1, 0.7, 0.5, 0.3],
                  [0.09, 0.07, 0.05, 0.03], ns(0.3), rng, True)
    for _ in range(2 + v):
        mix_into(x, glass * rng.uniform(0.05, 0.15) * signal.windows.tukey(len(glass), 0.02),
                 ns(rng.uniform(0.01, 0.12)))
    x += 0.2 * hp(white(n, rng), 3000, 2) * env_exp(n, 0.04)
    return room(x, 0.2)


def light_arc(rng, v):
    dur = 1.0
    n = ns(dur)
    env = np.clip(smooth_random(n, 18, rng, -0.3, 1.2), 0, 1) ** 1.5 * env_points(n, [(0, 0), (0.01, 1), (0.7, 0.8), (1.0, 0)])
    rate = 120 * (1 + 0.03 * smooth_random(n, 30, rng))
    buzz = np.tanh(5 * np.sin(phase_of(rate, n)) + 2 * np.sin(phase_of(rate * 3.01, n)))
    buzz = bp(buzz, 250, 5000, 2) * env
    sparks = np.zeros(n)
    k = int(rng.uniform(40, 80))
    for _ in range(k):
        tpos = rng.uniform(0, dur * 0.85)
        g = env[min(n - 1, ns(tpos))] + 0.2
        mix_into(sparks, burst(rng, rng.uniform(0.0005, 0.004), rng.uniform(0.0003, 0.0015), 1500, 14000) * g * rng.uniform(0.3, 1.5), ns(tpos))
    sizzle = hp(white(n, rng), 4000, 2) * env * 0.15
    x = 0.5 * unit(buzz) * 0.3 + sparks * 2 + sizzle
    return room(x, 0.12)


def power_down(rng, v):
    dur = 2.5
    n = ns(dur)
    t = taxis(n)
    x = np.zeros(n)
    mix_into(x, clunk(rng, 1.0) * 0.6, ns(0.02))
    tt = np.clip(t - 0.06, 0, None)
    f = 22 + 98 * np.exp(-tt / 0.75)
    ph = phase_of(f, n)
    hum = np.zeros(n)
    for k in range(1, 26):
        a = k ** -1.2 * (1.6 if k % 2 and k > 2 else 1) * np.exp(-(k * f) / 2500)
        hum += a * np.sin(k * ph)
    amp = ((f - 22) / 98) ** 1.3 * (t > 0.05)
    hum = unit(hum) * amp * 1.6
    rumble = lp(brown(n, rng), 150, 2) * env_points(n, [(0, 0), (0.06, 0.3), (2.4, 0)]) * 0.15
    x += hum + unit(rumble) * 0.04
    return room(fade_edges(x, 0.001, 0.2), 0.15)


def power_up(rng, v):
    dur = 2.0
    n = ns(dur)
    t = taxis(n)
    x = np.zeros(n)
    mix_into(x, clunk(rng, 1.0) * 0.6, ns(0.01))
    tt = np.clip(t - 0.08, 0, None)
    f = 120 - 85 * np.exp(-tt / 0.18)
    ph = phase_of(f, n)
    hum = np.zeros(n)
    for k in range(1, 30):
        hum += k ** -1.2 * (1.7 if k % 2 and k > 2 else 1) * np.sin(k * ph)
    swell = np.clip(tt / 1.1, 0, 1) ** 1.6 * (t > 0.08)
    x += unit(lp(hum, 6000, 2)) * swell * 0.8
    for e in (0.42, 0.57, 0.71):
        ex = burst(rng, 0.003, 0.0006, 1000, 12000)
        tk = signal.fftconvolve(ex, modal([2400 * rng.uniform(0.9, 1.1), 4200], [1, 0.5], [0.008, 0.005], ns(0.03)))
        mix_into(x, tk * 5, ns(e + rng.uniform(-0.03, 0.03)))
    return room(fade_edges(x, 0.001, 0.3), 0.15)


# ============================================================================ machinery / air
def vent_hvac(rng, v, n, P):
    rush = pink(n, rng)
    rush = lp(rush, 1800, 4)
    rush = peq(peq(peq(rush, 95, 6, 4), 190, 4, 5), 410, 3, 3)
    rush *= 1 + 0.12 * periodic_smooth(n, P, 0.3, rng)
    hiss = bp(white(n, rng), 2000, 7000, 2)
    noi = unit(rush) + 0.08 * unit(hiss)
    t = taxis(P)
    fb, fm = qf(143, P), qf(0.37, P)
    ph = 2 * np.pi * fb * t + 3.0 * np.sin(2 * np.pi * fm * t)
    blade = additive(ph, [1, 0.5, 0.25, 0.12])
    fw, fm2 = qf(1190, P), qf(0.21, P)
    whine = np.sin(2 * np.pi * fw * t + 10 * np.sin(2 * np.pi * fm2 * t))
    ton = 0.14 * unit(circ_filter(blade, lambda x: lp(x, 900, 2))) + 0.035 * whine
    return ton, noi


def vent_small(rng, v, n, P):
    hiss = bp(white(n, rng), 1500, 8000, 2)
    t = taxis(n)
    hiss *= 1 + 0.05 * np.sin(2 * np.pi * 2.7 * t) + 0.08 * periodic_smooth(n, P, 0.5, rng)
    noi = unit(hiss) + 0.35 * unit(lp(pink(n, rng), 300, 2))
    tp = taxis(P)
    whistle = np.sin(2 * np.pi * qf(2650, P) * tp + 6 * np.sin(2 * np.pi * qf(0.3, P) * tp))
    return 0.012 * whistle, noi


def machine_distant(rng, v, n, P):
    per = np.zeros(P)
    cycles = 9
    cl = P / cycles
    for c in range(cycles):
        base = int(c * cl)
        g = db(rng.normal(0, 1.5))
        mix_wrap(per, thump(rng, 70, 36, 0.22, 1.0, 0.8) * 1.0 * g, base)
        ex = burst(rng, 0.01, 0.002, 300, 6000)
        clank = signal.fftconvolve(ex, modal([420, 1130, 1870, 2650], [1, 0.6, 0.4, 0.25], [0.3, 0.2, 0.12, 0.08], ns(0.6)))
        mix_wrap(per, clank * 1.2 * g * rng.uniform(0.8, 1.1), base + int(0.476 * cl))
        hiss = bp(white(ns(0.35), rng), 800, 5000, 2) * env_ar(ns(0.35), 0.05, 0.25) * 0.08
        mix_wrap(per, hiss, base + int(0.9 * SR))
    t = taxis(P)
    motor = harm_tone(P, qf(47.5, P), [1, 0.5, 0.3, 0.2, 0.1], rng) * 0.08
    motor *= 1 + 0.3 * np.sin(2 * np.pi * qf(cycles / (P / SR), P) * t)
    per += motor
    per = circ_filter(per, lambda x: lp(x, 1600, 2))
    ir, _, _ = get_profile_ir("concrete")
    per = unit(0.35 * per + circ_conv(per, ir))
    noi = 0.25 * unit(lp(brown(n, rng), 120, 2))
    return per, noi


def machine_start(rng, v):
    dur = 4.0
    n = ns(dur)
    t = taxis(n)
    x = np.zeros(n)
    mix_into(x, clunk(rng, 1.0) * 0.3, ns(0.03))
    tt = np.clip(t - 0.15, 0, None)
    fr = 24 * (1 - np.exp(-tt / 0.9))
    on = (t > 0.15).astype(float)
    ph = phase_of(fr, n)
    rot = additive(ph * 8, [1, 0.4, 0.2]) * (fr / 24) ** 1.5 * 0.35
    gear = np.sin(ph * 31) * (fr / 24) ** 2 * 0.08
    elec = additive(phase_of(120.0, n), [1, 0.5, 0.3, 0.2]) * np.clip(tt / 0.3, 0, 1) * 0.12
    air = pink(n, rng)
    w = np.stack([1 - fr / 24, fr / 24])
    air = multi_cutoff_lp(air, [250, 2200], w) * (fr / 24) ** 1.2 * 0.5
    rattle = bp(white(n, rng), 1500, 5000, 2) * (0.5 + 0.5 * np.cos(ph)) ** 6 * 0.15 * on
    x += (rot + gear + elec + unit(air) * 0.12 + rattle) * env_points(n, [(0, 1), (3.3, 1), (4.0, 0)])
    return room(x, 0.15)


def pipe_knock(rng, v):
    n = ns(2.0)
    x = np.zeros(n)
    count = [3, 1, 4][v % 3]
    f1 = rng.uniform(150, 260)
    ratios = np.array([1, 2.76, 5.40, 8.93, 13.3])
    ir = modal(f1 * ratios * rng.uniform(0.98, 1.02, 5), [1, 0.7, 0.45, 0.25, 0.12],
               [0.9, 0.55, 0.3, 0.16, 0.08], ns(1.6))
    tpos, gap, amp = 0.02, rng.uniform(0.14, 0.22), 1.0
    for i in range(count):
        ex = burst(rng, 0.006, 0.0012, None, 4000) * amp
        hit = signal.fftconvolve(ex, ir)[: ns(1.6)]
        hit += pad_to(thump(rng, 90, 45, 0.05, 0.3, 0.6), len(hit)) * 0.5 * amp
        mix_into(x, hit, ns(tpos))
        tpos += gap
        gap *= rng.uniform(0.6, 0.9)
        amp *= rng.uniform(0.55, 0.8)
    return room(x, 0.1)


def creak(rng, n, rate_pts, modes, decays, amp_env, jitter=0.25):
    """Stick-slip friction: irregular impulse train through a modal body."""
    t = taxis(n)
    rate = env_points(n, rate_pts) * (1 + 0.15 * smooth_random(n, 6, rng))
    ph = np.cumsum(rate) / SR
    idx = np.nonzero(np.diff(np.floor(ph)) > 0)[0]
    exc = np.zeros(n)
    for i in idx:
        j = min(n - 1, i + int(rng.uniform(-jitter, jitter) * SR / max(rate[i], 1)))
        exc[max(0, j)] += amp_env[i] * rng.uniform(0.5, 1.0)
    exc = exc + 0.03 * white(n, rng) * amp_env
    body = modal(modes, [1.0 / (1 + 0.3 * k) for k in range(len(modes))], decays, ns(0.5), rng, True)
    return signal.fftconvolve(exc, body)[:n]


def metal_creak(rng, v):
    dur = 2.0
    n = ns(dur)
    modes = np.sort(rng.uniform(260, 2200, 6))
    rp = [(0, rng.uniform(15, 30)), (0.6, rng.uniform(40, 80)), (1.4, rng.uniform(20, 45)), (2.0, 15)]
    env = env_points(n, [(0, 0), (0.25, 0.7), (0.9, 1.0), (1.5, 0.6), (1.75, 0)])
    x = creak(rng, n, rp, modes, rng.uniform(0.04, 0.2, 6), env)
    if v % 2 == 1:  # expansion 'tick' at the end
        ex = burst(rng, 0.003, 0.0005, 800, 10000)
        ping = signal.fftconvolve(ex, modal(np.array([830, 2210, 3570]) * rng.uniform(0.8, 1.2), [1, .6, .3], [0.25, 0.12, 0.06], ns(0.4)))
        mix_into(x, ping * 3 * peak(x) / max(peak(ping), 1e-9), ns(1.78))
    return room(hp(x, 120, 2), 0.1)


# ============================================================================ water
def water_drip(rng, v):
    n = ns(0.8)
    x = np.zeros(n)
    f0 = [900, 1250, 700, 1500, 1080][v % 5] * rng.uniform(0.95, 1.05)
    mix_into(x, drop(rng, f0, rng.uniform(0.6, 1.4), rng.uniform(0.025, 0.05), 1.0, 0.2), ns(0.005))
    if v in (1, 3, 4):
        mix_into(x, drop(rng, f0 * rng.uniform(1.2, 1.6), 0.8, 0.02, rng.uniform(0.2, 0.4), 0.05),
                 ns(rng.uniform(0.07, 0.16)))
    return room(hp(x, 200, 2), 0.12)


def water_splash(rng, v):
    dur = 2.0
    n = ns(dur)
    x = np.zeros(n)
    body = bp(white(n, rng), 400, 6000, 2) * env_exp(n, 0.25 + 0.08 * v, 0.008)
    x += unit(body) * 0.35 * env_points(n, [(0, 1), (1.2, 0.2), (2.0, 0)])
    x += pad_to(thump(rng, 130, 60, 0.07, 0.4, 0.4), n) * 0.5
    nb = int(rng.uniform(30, 60))
    for _ in range(nb):
        tp = rng.exponential(0.25)
        if tp > 1.6:
            continue
        mix_into(x, drop(rng, rng.uniform(400, 3000), rng.uniform(0.3, 1.0), rng.uniform(0.008, 0.03),
                         rng.uniform(0.05, 0.25) * np.exp(-tp / 0.6), 0.0, 0.1), ns(0.01 + tp))
    for _ in range(int(rng.uniform(3, 7))):
        tp = rng.uniform(0.3, 1.4)
        mix_into(x, drop(rng, rng.uniform(800, 1600), 1.0, 0.03, rng.uniform(0.08, 0.2), 0.1), ns(tp))
    return lp(x, 4500, 2)


def water_lap(rng, v, n, P):
    def render(m):
        y = np.zeros(m)
        tpos = 0.0
        while tpos < m / SR:
            d = rng.uniform(0.4, 0.9)
            k = ns(d)
            slap = bp(pink(k, rng), 250 + 100 * v, 1800, 2) * env_ar(k, rng.uniform(0.06, 0.15), d * 0.7, 1.5)
            mix_into(y, unit(slap) * rng.uniform(0.3, 1.0), ns(tpos))
            if rng.random() < 0.5:
                mix_into(y, drop(rng, rng.uniform(300, 600), 0.6, 0.04, 0.4, 0.0, 0.2), ns(tpos + d * 0.3))
            for _ in range(int(rng.integers(0, 4))):
                mix_into(y, drop(rng, rng.uniform(700, 1800), 0.8, 0.015, rng.uniform(0.03, 0.1), 0.0, 0.08),
                         ns(tpos + rng.uniform(0, d)))
            tpos += rng.uniform(0.8, 2.2)
        y += 0.25 * unit(lp(brown(m, rng), 400, 2))
        ir, _, _ = get_profile_ir("pool")
        return apply_ir(y, ir, 1.0, 0.35)[:m]
    return None, preroll(render, n, 3.0)


def water_circulation(rng, v, n, P):
    t = taxis(P)
    ton = harm_tone(P, qf(120, P), [0.5, 0.2, 0.1], rng)
    ton += harm_tone(P, qf(177, P), [0.6, 0.25, 0.1], rng)
    ton *= periodic_am(P, [(0.31, 0.05, 0.0)])
    ton = unit(circ_filter(ton, lambda x: lp(x, 800, 2)))
    flow = lp(pink(n, rng), 900, 2)
    g = pink(n, rng)
    bands = [bp(g, 200, 400, 2), bp(g, 400, 800, 2), bp(g, 800, 1600, 2)]
    w = np.stack([np.clip(periodic_smooth(n, P, 1.5, rng), 0, 1) ** 2 for _ in bands])
    gurgle = sum(b * wi for b, wi in zip(bands, w))
    noi = unit(flow) + 0.6 * unit(gurgle) + 0.05 * unit(hp(white(n, rng), 4000, 2))
    noi = lp(noi, 3000, 2)
    return 0.35 * ton, noi


def water_bubbles(rng, v):
    dur = 2.0
    n = ns(dur)
    x = np.zeros(n)
    centers = rng.uniform(0.05, 1.3, 1 + v)
    for c in centers:
        for _ in range(int(rng.uniform(8, 18))):
            tp = c + abs(rng.normal(0, 0.15))
            if tp > 1.75:
                continue
            f = rng.uniform(250, 1200)
            mix_into(x, drop(rng, f, rng.uniform(0.2, 0.8), rng.uniform(0.01, 0.04), rng.uniform(0.2, 1.0) * (600 / f) ** 0.5, 0.0, 0.12), ns(tp))
    gur = bp(brown(n, rng), 80, 300, 2) * env_points(n, [(0, 0), (centers.min(), 0), (centers.min() + 0.1, 1), (1.9, 0)])
    x += 0.2 * unit(gur) * peak(x) * 0.3
    return lp(x, 5000, 2)


def water_underwater(rng, v, n, P):
    bed = lp(brown(n, rng), 350, 2) * db(3 * periodic_smooth(n, P, 0.15, rng))
    bub = np.zeros(n)
    for _ in range(6):
        bub_t = rng.uniform(0.5, n / SR - 0.5)
        mix_into(bub, drop(rng, rng.uniform(150, 400), 0.5, 0.06, rng.uniform(0.3, 1.0), 0.0, 0.25), ns(bub_t))
    noi = unit(bed) + 0.8 * unit(lp(bub, 500, 2)) * 0.5
    ton = harm_tone(P, qf(55, P), [1, 0.4], rng) * periodic_am(P, [(0.13, 0.3, 0.0)])
    return 0.15 * ton, noi


def room_damp(rng, v, n, P):
    tone = unit(hp(lp(pink(n, rng), 2500, 2), 40, 2)) + 0.6 * unit(lp(brown(n, rng), 120, 2))
    dr = np.zeros(n)
    mix_into(dr, drop(rng, 1150, 1.0, 0.035, 1.0, 0.15), ns(4.1))
    mix_into(dr, drop(rng, 1500, 0.8, 0.025, 0.4, 0.05), ns(7.3))
    ir, _, _ = get_profile_ir("pool")
    dr = apply_ir(dr, ir, 0.6, 0.8)[:n]
    noi = tone + 0.9 * dr * rms(tone) / max(rms(dr), 1e-9) * 0.35
    return None, noi


# ============================================================================ presence
def _wood_knock(rng, heavy=1.0, base=110):
    n = ns(0.5)
    modes = np.array([base, base * 2.1, base * 3.6, base * 5.8, base * 8.4, base * 12.6]) * rng.uniform(0.95, 1.05, 6)
    ir = modal(modes, [1, 0.7, 0.5, 0.35, 0.25, 0.15], [0.12, 0.09, 0.06, 0.04, 0.03, 0.02], n, rng, True)
    ex = burst(rng, 0.004, 0.0009 * heavy, None, 3500 / heavy)
    x = signal.fftconvolve(ex, ir)[:n] * 6
    x += heavy * pad_to(thump(rng, 85, 50, 0.04, 0.3, 0.5), n) * 0.7
    return x


def knock_distant(rng, v):
    n = ns(1.5)
    x = np.zeros(n)
    pats = [[0.0, 0.22, 0.44], [0.0, 0.45], [0.0], [0.0, 0.15, 0.3, 0.45]]
    heavy = [1.0, 1.4, 2.2, 0.9][v % 4]
    for i, tp in enumerate(pats[v % 4]):
        a = (1.0 if i == 0 else rng.uniform(0.7, 1.0)) * (0.85 ** i if v == 3 else 1)
        mix_into(x, _wood_knock(rng, heavy, rng.uniform(95, 130)) * a, ns(0.01 + tp + rng.uniform(-0.01, 0.01)))
    return room(lp(x, 3500, 2), 0.15)


def impact_distant(rng, v):
    dur = 2.5
    n = ns(dur)
    t = taxis(n)
    f = 30 + 35 * np.exp(-t / 0.12) * rng.uniform(0.8, 1.2)
    x = np.sin(phase_of(f, n)) * env_exp(n, 0.5, 0.003)
    x += 0.8 * unit(lp(brown(n, rng), 150, 2)) * env_exp(n, 0.4, 0.002) * 0.5
    x += 0.5 * unit(bp(white(n, rng), 200, 1200, 2)) * env_exp(n, 0.12, 0.001)
    ring = modal(np.array([310, 740, 1180]) * rng.uniform(0.8, 1.2), [1, 0.5, 0.3], [0.6, 0.35, 0.2], n, rng, True)
    x += 0.15 * ring
    for _ in range(int(rng.uniform(4, 9))):
        tp = rng.uniform(0.12, 0.9)
        ex = burst(rng, 0.003, 0.0005, 500, 8000)
        tk = signal.fftconvolve(ex, modal(rng.uniform(900, 3500, 3), [1, .5, .3], [0.05, 0.03, 0.02], ns(0.1)))
        mix_into(x, tk * rng.uniform(0.3, 1.0), ns(tp))
    return lp(x, 1800, 2)


def _step(rng, kind, v):
    n = ns(0.5)
    x = np.zeros(n)
    heel = rng.uniform(0.8, 1.0)
    toe_t = rng.uniform(0.06, 0.12)
    toe = rng.uniform(0.35, 0.65)
    if kind == "carpet":
        for tp, a in ((0.0, heel), (toe_t, toe)):
            y = lp(white(ns(0.15), rng), rng.uniform(300, 450), 2) * env_exp(ns(0.15), 0.025, 0.002) * 3
            y += pad_to(thump(rng, 75, 50, 0.035, 0.15, 0.2), ns(0.15)) * 0.8
            mix_into(x, y * a, ns(0.005 + tp))
        scuff = bp(white(ns(0.12), rng), 1500, 5000, 2) * env_ar(ns(0.12), 0.02, 0.09) * 0.06
        mix_into(x, scuff, ns(0.02 + rng.uniform(0, 0.05)))
    elif kind == "concrete":
        for tp, a in ((0.0, heel), (toe_t, toe)):
            y = hp(white(ns(0.1), rng), 1200, 2) * env_exp(ns(0.1), 0.006, 0.0003) * 1.5
            y += bp(white(ns(0.1), rng), 150, 900, 2) * env_exp(ns(0.1), 0.02, 0.0005) * 2.5
            y += pad_to(thump(rng, 95, 55, 0.025, 0.1, 0.2), ns(0.1)) * 0.6
            mix_into(x, y * a, ns(0.005 + tp))
        for _ in range(int(rng.uniform(15, 35))):
            mix_into(x, burst(rng, 0.0015, 0.0003, 3000, 10000) * rng.uniform(0.05, 0.25),
                     ns(0.005 + rng.uniform(0, toe_t + 0.06)))
    elif kind == "tile":
        modes = np.array([1850, 3100, 4600, 6200]) * rng.uniform(0.9, 1.1)
        ring = modal(modes, [1, 0.6, 0.4, 0.25], [0.03, 0.02, 0.015, 0.01], ns(0.1), rng, True)
        for tp, a in ((0.0, heel), (toe_t * 0.8, toe)):
            y = hp(white(ns(0.1), rng), 2000, 2) * env_exp(ns(0.1), 0.004, 0.0002) * 1.5
            y += ring * 0.25
            y += bp(white(ns(0.1), rng), 200, 1200, 2) * env_exp(ns(0.1), 0.015, 0.0005) * 1.5
            y += pad_to(thump(rng, 110, 60, 0.02, 0.1, 0.1), ns(0.1)) * 0.5
            mix_into(x, y * a, ns(0.005 + tp))
    return x


def step_carpet(rng, v):
    return room(_step(rng, "carpet", v), 0.05)


def step_concrete(rng, v):
    return room(_step(rng, "concrete", v), 0.05)


def step_tile(rng, v):
    return room(_step(rng, "tile", v), 0.05)


def step_water(rng, v):
    dur = 1.1
    n = ns(dur)
    x = np.zeros(n)
    for tp, a, d in ((0.0, 1.0, 0.35), (rng.uniform(0.22, 0.32), 0.55, 0.3)):
        k = ns(d)
        src = pink(k, rng)
        bands = [bp(src, 250, 700, 2), bp(src, 700, 1600, 2), bp(src, 1600, 3500, 2)]
        w = np.stack([np.clip(smooth_random(k, 12, rng), 0, 1) for _ in bands])
        sl = sum(b * wi for b, wi in zip(bands, w)) * env_ar(k, rng.uniform(0.03, 0.06), d * 0.75, 1.2)
        mix_into(x, unit(sl) * a, ns(0.005 + tp))
    for _ in range(int(rng.uniform(5, 12))):
        mix_into(x, drop(rng, rng.uniform(400, 1500), 0.6, 0.02, rng.uniform(0.1, 0.4), 0.0, 0.1),
                 ns(rng.uniform(0.02, 0.6)))
    for _ in range(int(rng.uniform(2, 5))):
        mix_into(x, drop(rng, rng.uniform(900, 1700), 1.0, 0.03, rng.uniform(0.1, 0.25), 0.1),
                 ns(rng.uniform(0.45, 0.95)))
    return room(lp(x, 6000, 2), 0.08)


def movement_distant(rng, v):
    dur = 3.0
    n = ns(dur)
    t = taxis(n)
    x = np.zeros(n)
    if v == 0 or v == 1:
        segs = [(0.05, 2.1)] if v == 0 else [(0.05, 0.9), (1.25, 0.8)]
        for s, d in segs:
            k = ns(d)
            grain = np.abs(smooth_random(k, 35, rng)) ** 2
            src = white(k, rng) * grain * env_ar(k, 0.25, 0.35)
            y = bp(src, 120, 1500, 2)
            y = y + 0.6 * reson(src, 180 * rng.uniform(0.8, 1.2), 6) + 0.4 * reson(src, 430, 6) + 0.25 * reson(src, 760, 8)
            cr = creak(rng, k, [(0, 12), (d, 20)], rng.uniform(150, 900, 4), [0.08] * 4,
                       env_ar(k, 0.2, 0.3), 0.4)
            mix_into(x, unit(y) + 0.3 * unit(cr), ns(s))
        mix_into(x, pad_to(thump(rng, 70, 40, 0.12, 0.6, 0.8), ns(0.6)) * 4, ns(2.25 if v == 0 else 2.2))
    else:
        rum = lp(brown(n, rng), 300, 2) * env_points(n, [(0, 0), (0.6, 1), (2.0, 0.8), (2.8, 0)])
        x += unit(rum)
        cr = creak(rng, n, [(0, 6), (1.5, 14), (3.0, 8)], rng.uniform(100, 600, 5), [0.15] * 5,
                   env_points(n, [(0, 0), (0.8, 1), (2.4, 0.5), (2.9, 0)]), 0.4)
        x += 0.6 * unit(cr)
    return room(lp(x, 2000, 2), 0.15)


def _glottal(rng, n, f0, jitter=0.02):
    f = f0 * (1 + jitter * smooth_random(n, 20, rng))
    ph = phase_of(f, n)
    H = int(min(40, SR / 2 / max(np.max(f), 1)))
    return additive(ph, [k ** -1.3 for k in range(1, H + 1)])


def _formants(x, fs_q):
    return sum(a * reson(x, f, q) for f, q, a in fs_q)


def breath_heavy(rng, v):
    dur = 4.0
    n = ns(dur)
    x = np.zeros(n)
    tpos = rng.uniform(0.02, 0.1)
    while tpos < dur - 0.6:
        di = rng.uniform(0.75, 1.0)
        k = ns(di)
        src = 0.6 * pink(k, rng) + 0.4 * white(k, rng)
        inh = _formants(bp(src, 800, 6000, 2), [(1800, 3, 1.0), (3300, 4, 0.6), (5200, 3, 0.3)]) + 0.3 * bp(src, 900, 5000, 2)
        inh *= env_points(k, [(0, 0), (di * 0.6, 1), (di, 0)]) * (1 + 0.12 * np.sin(2 * np.pi * rng.uniform(6, 9) * taxis(k)))
        mix_into(x, unit(inh) * 0.55, ns(tpos))
        tpos += di + rng.uniform(0.05, 0.18)
        de = rng.uniform(0.9, 1.25)
        k = ns(de)
        src = pink(k, rng)
        exh = _formants(bp(src, 250, 4000, 2), [(700, 3, 1.0), (1250, 4, 0.7), (2500, 5, 0.4)]) + 0.3 * bp(src, 300, 3000, 2)
        rasp = _formants(_glottal(rng, k, rng.uniform(75, 95), 0.08), [(650, 6, 1.0), (1150, 7, 0.6), (2400, 8, 0.3)])
        rasp *= (np.abs(smooth_random(k, 40, rng)) ** 1.5)
        envx = env_points(k, [(0, 0), (0.08, 1), (de * 0.45, 0.7), (de, 0)])
        mix_into(x, (unit(exh) + 0.25 * unit(rasp) * (0.5 + 0.5 * v / 2)) * envx, ns(tpos))
        tpos += de + rng.uniform(0.15, 0.4)
    return hp(x, 90, 2)


# ============================================================================ radio / speakers
def radio_static(rng, v, n, P):
    hiss = bp(white(n, rng), 300, 4000, 4)
    hiss *= 1 + 0.25 * periodic_smooth(n, P, 0.4, rng)
    cr = np.zeros(n)
    tp = 0.0
    while True:
        tp += rng.exponential(1 / 8)
        if tp >= n / SR:
            break
        mix_into(cr, burst(rng, rng.uniform(0.0003, 0.003), 0.0005, 500, 3000) * rng.uniform(0.5, 3) ** 2, ns(tp))
    noi = unit(hiss) + 0.5 * cr / max(rms(cr), 1e-9) * 0.25
    t = taxis(P)
    wh = np.sin(2 * np.pi * qf(1050, P) * t + 25 * np.sin(2 * np.pi * qf(0.13, P) * t))
    return 0.025 * wh, lp(noi, 4500, 2)


def speaker_broken(rng, v):
    dur = 2.0
    n = ns(dur)
    src = additive(phase_of(60.0, n), [1, 0.8, 0.5, 0.4, 0.3, 0.2]) + 0.5 * white(n, rng)
    gate = np.zeros(n)
    tp = 0.0
    on = True
    while tp < dur:
        d = rng.uniform(0.02, 0.22)
        if on:
            gate[ns(tp):ns(tp + d)] = rng.uniform(0.4, 1.0)
        tp += d
        on = not on
    sm = signal.windows.hann(ns(0.002))
    gate = np.convolve(gate, sm / sm.sum(), "same")
    x = np.tanh(6 * src * gate) * 0.5
    edges = np.nonzero(np.abs(np.diff(gate)) > 0.05)[0]
    for e in edges[::3]:
        mix_into(x, burst(rng, 0.004, 0.001, 800, 6000) * rng.uniform(1, 3), int(e))
    x = bp(x, 250, 4500, 2)
    x = peq(x, 1800, 6, 2)
    x *= env_points(n, [(0, 1), (1.6, 1), (2.0, 0)])
    return x


# ============================================================================ office
def office_ac(rng, v, n, P):
    noi = unit(lp(pink(n, rng), 700, 2)) + 0.12 * unit(bp(white(n, rng), 2000, 6000, 2))
    noi *= db(0.5 * periodic_smooth(n, P, 0.2, rng))
    ton = harm_tone(P, qf(118, P), [1, 0.4, 0.2], rng)
    return 0.06 * ton, noi


def office_printer(rng, v):
    dur = 4.0
    n = ns(dur)
    x = np.zeros(n)
    mix_into(x, clunk(rng, 0.4) * 0.25, ns(0.01))
    fm = [420, 520][v % 2]
    def whir(d, f, a):
        k = ns(d)
        ph = phase_of(f * (1 + 0.01 * smooth_random(k, 5, rng)), k)
        return additive(ph, [1, 0, 0.33, 0, 0.2, 0, 0.14]) * env_ar(k, 0.04, 0.06) * a
    def swish(d, a):
        k = ns(d)
        return bp(white(k, rng), 1000, 5000, 2) * env_ar(k, d * 0.3, d * 0.5) * a
    def carriage(d, rate, a):
        k = ns(d)
        r = env_points(k, [(0, rate * 0.4), (0.08, rate), (d - 0.08, rate), (d, rate * 0.4)])
        ph = np.cumsum(r) / SR
        exc = np.zeros(k)
        exc[np.nonzero(np.diff(np.floor(ph)) > 0)[0]] = 1
        body = modal([700, 1500, 2600, 3900], [1, .7, .4, .2], [0.006, 0.004, 0.003, 0.002], ns(0.02))
        return signal.fftconvolve(exc, body)[:k] * a * env_ar(k, 0.02, 0.04)
    def tick():
        ex = burst(rng, 0.002, 0.0005, 1000, 9000)
        return signal.fftconvolve(ex, modal([2100, 3400], [1, .5], [0.01, 0.006], ns(0.04))) * 3
    mix_into(x, whir(0.55, fm, 0.25), ns(0.1))
    mix_into(x, swish(0.5, 0.3), ns(0.12))
    passes = [(0.75, 0.62), (1.47, 0.62), (2.19, 0.62)] if v == 0 else [(0.7, 0.9), (1.7, 0.9)]
    for i, (s, d) in enumerate(passes):
        mix_into(x, carriage(d, [240, 300][v % 2] * (1 + 0.05 * (i % 2)), 1.0), ns(s))
        mix_into(x, whir(d, fm * 0.7, 0.08), ns(s))
        mix_into(x, tick(), ns(s + d + 0.02))
    mix_into(x, whir(0.7, fm * 1.1, 0.25), ns(3.05))
    mix_into(x, swish(0.75, 0.45), ns(3.05))
    x *= env_points(n, [(0, 1), (3.85, 1), (4.0, 0)])
    return room(x, 0.15)


def office_phone(rng, v):
    dur = 3.6
    n = ns(dur)
    x = np.zeros(n)
    ratios = np.array([1, 2.08, 2.98, 4.11, 5.2])
    bell_a = modal(1240 * ratios, [1, .5, .35, .2, .1], [0.5, 0.3, 0.2, 0.12, 0.08], ns(0.9), rng, True)
    bell_b = modal(1410 * ratios, [1, .5, .35, .2, .1], [0.45, 0.28, 0.18, 0.1, 0.07], ns(0.9), rng, True)
    for start in (0.05, 1.7):
        k = 0
        tp = start
        while tp < start + 1.1:
            mix_into(x, bell_a * rng.uniform(0.8, 1.0), ns(tp))
            mix_into(x, bell_b * rng.uniform(0.7, 0.95), ns(tp + 1 / 40))
            mix_into(x, burst(rng, 0.002, 0.0004, 3000, 12000) * 0.3, ns(tp))
            tp += 1 / 20
            k += 1
    return room(x * env_points(n, [(0, 1), (3.3, 1), (3.6, 0)]), 0.2)


def office_click(rng, v):
    n = ns(0.25)
    x = np.zeros(n)
    if v == 0:  # relay
        for tp, a in ((0.003, 1.0), (0.0065, 0.4), (0.012, 0.2)):
            ex = burst(rng, 0.001, 0.0002, 1000, 12000)
            mix_into(x, signal.fftconvolve(ex, modal([2600, 4100, 5900], [1, .6, .3], [0.01, 0.006, 0.004], ns(0.03))) * a, ns(tp))
        mix_into(x, thump(rng, 160, 80, 0.01, 0.05, 0.1) * 0.2, ns(0.003))
    elif v == 1:  # keyboard key
        for tp, a in ((0.003, 1.0), (rng.uniform(0.08, 0.11), 0.5)):
            y = lp(white(ns(0.05), rng), 3000, 2) * env_exp(ns(0.05), 0.003, 0.0003)
            y += modal([450, 1100, 2400], [1, .6, .4], [0.02, 0.015, 0.01], ns(0.05), rng, True) * 0.3
            mix_into(x, y * a, ns(tp))
    elif v == 2:  # wall switch
        y = hp(white(ns(0.03), rng), 1500, 2) * env_exp(ns(0.03), 0.002, 0.0001) * 1.5
        y += modal([320, 900, 2200], [1, .5, .3], [0.02, 0.012, 0.006], ns(0.03), rng, True) * 0.5
        mix_into(x, y, ns(0.003))
        mix_into(x, thump(rng, 120, 70, 0.015, 0.06, 0.1) * 0.3, ns(0.003))
    else:  # latch / button pair
        for tp, a in ((0.003, 1.0), (rng.uniform(0.05, 0.07), 0.7)):
            ex = burst(rng, 0.001, 0.0002, 1500, 10000)
            mix_into(x, signal.fftconvolve(ex, modal([3300, 5200], [1, .5], [0.006, 0.004], ns(0.02))) * a, ns(tp))
    return room(x, 0.1)


# ============================================================================ plains
def wind_plains(rng, v, n, P):
    gust = 0.6 + 0.4 * periodic_smooth(n, P, 0.12 + 0.05 * v, rng)
    base = lp(brown(n, rng), 400 + 100 * v, 2) * gust
    src = pink(n, rng)
    fs = [300, 420, 560, 720]
    w = np.stack([np.clip(periodic_smooth(n, P, 0.15, rng), 0, 1) ** 2 for _ in fs])
    whistle = sum(reson(src, f, 12) * wi for f, wi in zip(fs, w)) * gust ** 2
    noi = unit(base) + 0.15 * unit(whistle)
    return None, noi


def grass_rustle(rng, v):
    dur = 2.0
    n = ns(dur)
    env = env_points(n, [(0, 0), (0.25 + 0.1 * v, 1), (1.0, 0.8), (1.8, 0)])
    env *= np.clip(1 + 0.4 * smooth_random(n, 3, rng), 0.2, 1.6)
    imp = (rng.random(n) < (300 + 400 * env) / SR) * white(n, rng)
    crack = bp(imp, 2000, 9000, 2) * env
    swish = bp(pink(n, rng), 1500, 6000, 2) * env
    return unit(crack) + 0.5 * unit(swish)


def plains_distant(rng, v):
    dur = 4.0
    n = ns(dur)
    t = taxis(n)
    if v == 0:
        f0 = rng.uniform(62, 75) * (1 + 0.004 * np.sin(2 * np.pi * 0.2 * t))
        x = additive(phase_of(f0, n), [1, 0.6, 0.4, 0.25, 0.15, 0.1]) + 0.5 * additive(phase_of(f0 * 1.5, n), [1, 0.4, 0.2])
        x = lp(x, 600, 2) * env_points(n, [(0, 0), (1.6, 1), (2.4, 1), (3.8, 0)]) ** 1.5
    elif v == 1:
        x = creak(rng, n, [(0, 7), (1.5, 13), (3.5, 6)], np.array([140, 233, 371, 590]) * rng.uniform(0.9, 1.1),
                  [0.6, 0.45, 0.3, 0.2], env_points(n, [(0, 0), (1.0, 1), (2.8, 0.6), (3.6, 0)]), 0.3)
        x = lp(x, 900, 2)
    else:
        x = additive(phase_of(98.0, n), [1, 0.4, 0.2]) + additive(phase_of(103.5, n), [0.8, 0.3, 0.1])
        x += 0.3 * unit(bp(pink(n, rng), 200, 600, 2))
        x = lp(x, 700, 2) * env_points(n, [(0, 0), (1.2, 1), (2.5, 0.8), (3.8, 0)]) ** 2
    ir = make_ir(rng, [(0, 400, 3.2), (400, SR / 2, 2.2)], er=[(0.18, 0.3, 1200), (0.41, 0.15, 900)],
                 predelay=0.05, onset=0.1)
    y = apply_ir(lp(x, 900, 2), ir, 0.35, 1.0)
    return y


def silence_transition(rng, v):
    dur = 3.0
    n = ns(dur)
    t = taxis(n)
    env = env_points(n, [(0, 0), (1.6, 1), (1.75, 1), (2.15, 0.02), (3.0, 0)]) ** 1.5
    w = np.stack([env_points(n, [(0, 1), (1.7, 0.2), (2.2, 0)]), env_points(n, [(0, 0), (1.7, 0.8), (2.2, 1)])])
    nz = multi_cutoff_lp(brown(n, rng), [1500, 80], w)
    sub = np.sin(2 * np.pi * 38 * t) * env
    x = unit(nz) * env + 0.8 * sub
    ring = np.sin(2 * np.pi * 7200 * t) * env_points(n, [(0, 0), (1.8, 0), (2.3, 1), (3.0, 0)]) * 0.004
    return x + ring * 3


# ============================================================================ creature (skinwalker)
def _bone_crack(rng, size=1.0, bright=1.0):
    """Bone / cartilage crack: a few fracture micro-impulses through a damped, inharmonic 'bone' body,
    a crisp broadband snap and a small low knock (weight). size > 1 = bigger, lower, heavier."""
    n = ns(0.12)
    exc = np.zeros(n)
    tp = 0.0
    for k in range(int(rng.integers(1, 5))):
        mix_into(exc, burst(rng, 0.0015, 0.0003, 1500, 12000) * rng.uniform(0.4, 1.0) * (1.0 if k == 0 else 0.6),
                 ns(tp))
        tp += rng.uniform(0.0004, 0.0035)
    fb = rng.uniform(700, 1600) / size
    body = modal(fb * np.array([1.0, 2.31, 3.87, 5.62]) * rng.uniform(0.95, 1.05, 4), [1.0, 0.7, 0.45, 0.25],
                 np.array([0.012, 0.008, 0.005, 0.0035]) * size ** 0.5, ns(0.06), rng, True)
    x = signal.fftconvolve(exc, body)[:n] * 6
    x += bright * hp(white(n, rng), 3000, 2) * env_exp(n, 0.0008, 0.0) * 1.2
    x += size * pad_to(thump(rng, 190 / size ** 0.5, 85 / size ** 0.5, 0.012 * size, 0.08, 0.3), n) * 0.5
    return x


def _wet_pop(rng, size=1.0):
    """Joint pop (cavitation): short upward-gliding low pop + a sticky squelch of fast-gated noise."""
    d = rng.uniform(0.07, 0.16) * size ** 0.5
    n = ns(d + 0.02)
    t = taxis(n)
    f0 = rng.uniform(170, 320) / size ** 0.5
    f = f0 * (1 + 1.4 * (1 - np.exp(-t / 0.012)))
    x = np.sin(phase_of(f, n)) * env_exp(n, rng.uniform(0.012, 0.02) * size, 0.0006)
    x += 0.25 * hp(white(n, rng), 2500, 2) * env_exp(n, 0.0006, 0.0)
    k = ns(d)
    g = np.clip(smooth_random(k, rng.uniform(90, 160), rng), 0, None) ** 2
    sq = bp(pink(k, rng), 350, 2200, 2) * g * env_ar(k, 0.004, d * 0.8, 1.5)
    mix_into(x, unit(sq) * rng.uniform(0.12, 0.25), ns(rng.uniform(0.0, 0.01)))
    return x


def skinwalker_crackle(rng, v):
    dur = 2.5
    n = ns(dur)
    x = np.zeros(n)
    t_end = rng.uniform(1.98, 2.1)            # final crunch
    # accelerating cluster: inhomogeneous Poisson, rate rising ~7/s -> ~50/s, opening on a crack
    tp = rng.uniform(0.01, 0.04)
    mix_into(x, _bone_crack(rng, rng.uniform(0.9, 1.2)) * 0.45, ns(tp))
    while True:
        u = tp / t_end
        rate = 7.0 + 43.0 * u ** 2.0
        tp += rng.exponential(1 / rate)
        if tp >= t_end - 0.03:
            break
        u = tp / t_end
        amp = 0.35 + 0.5 * u
        r = rng.random()
        if r < 0.5:      # crack
            mix_into(x, _bone_crack(rng, rng.uniform(0.8, 1.4)) * amp * rng.uniform(0.6, 1.0), ns(tp))
        elif r < 0.7:    # quick knuckle ratchet: 3-6 small cracks a few ms apart
            q = tp
            for _ in range(int(rng.integers(3, 7))):
                mix_into(x, _bone_crack(rng, rng.uniform(0.6, 0.9), 1.3) * amp * rng.uniform(0.3, 0.6), ns(q))
                q += rng.uniform(0.007, 0.018)
        elif r < 0.85:   # bright snap
            mix_into(x, _bone_crack(rng, rng.uniform(0.5, 0.7), 1.8) * amp * rng.uniform(0.5, 0.9), ns(tp))
        else:            # wet joint pop
            mix_into(x, _wet_pop(rng, rng.uniform(0.8, 1.3)) * amp * rng.uniform(0.6, 1.0), ns(tp))
    # sinew / flesh strain underneath, tightening towards the crunch
    k = ns(t_end + 0.05)
    amp_env = env_points(k, [(0, 0.0), (0.4, 0.2), (t_end - 0.1, 1.0), (t_end + 0.05, 0.0)])
    strain = creak(rng, k, [(0, 18), (t_end, 70)], [190, 430, 780, 1300], [0.03, 0.02, 0.012, 0.008], amp_env, 0.35)
    mix_into(x, unit(strain) * 0.05, 0)
    # final heavy crunch: deep knock, dense splinter burst, gritty crush noise, wet tail
    c = ns(t_end)
    mix_into(x, thump(rng, 140, 42, 0.09, 0.45, 0.7) * 1.1, c)
    for j in range(int(rng.integers(18, 32))):
        q = rng.exponential(0.025)
        if q < 0.12:
            mix_into(x, _bone_crack(rng, rng.uniform(0.7, 1.8), 1.2) * np.exp(-q / 0.05) * rng.uniform(0.5, 1.0),
                     c + ns(q))
    m = ns(0.3)
    grit = bp(white(m, rng), 250, 4500, 2) * np.clip(smooth_random(m, 220, rng), 0, None) ** 2
    mix_into(x, unit(grit) * env_exp(m, 0.06, 0.001) * 0.35, c)
    for _ in range(int(rng.integers(2, 4))):
        mix_into(x, _wet_pop(rng, rng.uniform(1.0, 1.6)) * rng.uniform(0.3, 0.5), c + ns(rng.uniform(0.05, 0.25)))
    return room(hp(x, 45, 2), 0.06)


def _chitin_click(rng, hi=1.0):
    """Hard chitin click: tiny broadband impulse through a short, high, inharmonic shell body."""
    n = ns(0.03)
    ex = burst(rng, 0.0008, 0.00015, 1500, 14000)
    fb = rng.uniform(2600, 4800) * hi
    body = modal([fb, fb * 1.47, fb * 2.13, fb * 0.31], [1.0, 0.55, 0.3, 0.35],
                 [0.004, 0.0028, 0.0018, 0.006], ns(0.025), rng, True)
    x = signal.fftconvolve(ex, body)[:n] * 5
    x[: len(ex)] += ex * 0.8
    return x


def _leg_tap(rng, hard=1.0):
    """One leg tip striking the floor: sharp tick, short shell ring, small floor knock."""
    n = ns(0.06)
    x = hp(white(n, rng), 2200, 2) * env_exp(n, 0.0007, 0.0001) * 1.6 * hard
    fb = rng.uniform(1800, 3400)
    x += modal([fb, fb * 1.71, fb * 2.62], [1.0, 0.5, 0.3], [0.005, 0.0035, 0.002], n, rng, True) * 0.35
    x += pad_to(thump(rng, 170, 80, 0.012, 0.06, 0.25), n) * 0.45
    if rng.random() < 0.35:       # flam: second tip lands a few ms later
        mix_into(x, hp(white(ns(0.01), rng), 2500, 2) * env_exp(ns(0.01), 0.0006, 0.0001) * 0.7,
                 ns(rng.uniform(0.002, 0.006)))
    return x


def skinwalker_scuttle(rng, v):
    n = ns(0.3)
    x = np.zeros(n)
    k = int(rng.integers(3, 8))
    shape = rng.choice(["flat", "cresc", "decresc"])
    tp = rng.uniform(0.003, 0.008)
    for j in range(k):
        u = j / max(1, k - 1)
        a = {"flat": 1.0, "cresc": 0.55 + 0.45 * u, "decresc": 1.0 - 0.45 * u}[shape] * rng.uniform(0.7, 1.0)
        mix_into(x, _leg_tap(rng, rng.uniform(0.8, 1.2)) * a, ns(tp))
        tp += rng.uniform(0.016, 0.045)
        if tp > 0.22:
            break
    return room(hp(x, 60, 2), 0.05)


def skinwalker_chitter(rng, v, n, P):
    # periodic click layer (mix_wrap into a P-sample buffer -> overlaps reconstruct exactly)
    buf = np.zeros(P)
    pd = P / SR
    tp = rng.uniform(0, 0.2)
    while tp < pd:
        kind = rng.random()
        if kind < 0.6:     # chitter burst: 4-14 irregular clicks
            q = tp
            m = int(rng.integers(4, 15))
            hi = rng.uniform(0.85, 1.15)
            for j in range(m):
                a = np.sin(np.pi * (j + 0.5) / m) ** 0.7 * rng.uniform(0.5, 1.0)
                mix_wrap(buf, _chitin_click(rng, hi * rng.uniform(0.95, 1.05)) * a, ns(q))
                q += rng.uniform(0.016, 0.042)
            tp = q
        elif kind < 0.8:   # fast stridulating trill
            m = int(rng.uniform(0.12, 0.3) * rng.uniform(50, 70))
            per = 1 / rng.uniform(50, 70)
            for j in range(m):
                a = np.sin(np.pi * (j + 0.5) / m) * 0.45
                mix_wrap(buf, _chitin_click(rng, 1.25) * a, ns(tp + j * per + rng.uniform(-0.002, 0.002)))
            tp += m * per
        else:              # lone click
            mix_wrap(buf, _chitin_click(rng, rng.uniform(0.8, 1.2)) * rng.uniform(0.4, 0.9), ns(tp))
        tp += rng.exponential(0.22) + 0.05
    ir, _, _ = get_profile_ir("carpet")
    ton = circ_filter(buf, lambda y: hp(y, 500, 2))
    ton = ton + circ_conv(ton, ir) * 0.15
    # wet, low breath underneath (loop-periodic envelope, noise part)
    t = taxis(n)
    br_f = qf(0.55, P)
    cyc = 0.5 - 0.5 * np.cos(2 * np.pi * br_f * t)
    env = 0.3 + 0.7 * cyc ** 1.6
    src = pink(n, rng)
    breath = _formants(bp(src, 120, 2400, 2), [(320, 3, 1.0), (850, 4, 0.6), (1900, 5, 0.25)])
    wet = bp(pink(n, rng), 200, 1100, 2) * np.clip(smooth_random(n, 30, rng), 0, None) ** 2
    rumble = lp(brown(n, rng), 140, 2)
    noi = (unit(breath) + 0.6 * unit(wet) + 0.5 * unit(rumble)) * env
    noi = lp(noi, 2200, 2)
    ton = unit(ton)
    ton = 6.0 * np.tanh(ton / 6.0)      # memoryless soft limit of click peaks (keeps the layer periodic)
    return unit(ton), 0.5 * unit(noi)


# ============================================================================ per-level beds (player-attached)
# Long (40-48 s) seamless loops played on the listener. Almost everything lives in the loop-periodic part:
# FFT-shaped noise is circular, filters run through circ_filter, reverbs through circ_conv, and discrete
# events are written with mix_wrap -> two instances overlapping by 300 ms reconstruct the bed exactly.
def _pcurve(P, rate, rng, lo=-1.0, hi=1.0):
    """Smooth random curve that is exactly periodic over P samples (band-limited to ~rate Hz, via FFT)."""
    K = max(2, int(round(rate * P / SR)))
    k = np.arange(1, K + 1)
    X = np.zeros(P // 2 + 1, complex)
    X[1:K + 1] = (rng.normal(size=K) + 1j * rng.normal(size=K)) / np.sqrt(k)
    y = np.fft.irfft(X, P)
    y /= np.max(np.abs(y)) or 1.0
    return lo + (y + 1) * 0.5 * (hi - lo)


def _swell(P, rate, rng, thresh=0.0, power=1.5):
    """0..1 periodic envelope that swells irregularly and rests at 0 between swells."""
    e = np.clip(_pcurve(P, rate, rng) - thresh, 0, None)
    return (e / (np.max(e) or 1.0)) ** power


def _ping(rng, freqs, decays, dur=0.4, ex_hi=10000):
    ex = burst(rng, 0.002, 0.0004, 600, ex_hi)
    m = modal(freqs, [1.0 / (1 + 0.5 * k) for k in range(len(freqs))], decays, ns(dur), rng, True)
    return signal.fftconvolve(ex, m)[: ns(dur)]


def bed_poolrooms(rng, v, n, P):
    # pump: motor 120 Hz electrical hum + 7-vane impeller blade-pass on a 29.6 Hz shaft
    mot = harm_tone(P, qf(120, P), [0.5, 0.22, 0.12, 0.05], rng)
    bpf = qf(7 * 29.6, P)
    imp = harm_tone(P, bpf, [1.0, 0.35, 0.15], rng) * periodic_am(P, [(29.6, 0.18, 0.0), (0.23, 0.06, 1.0)])
    shaft = harm_tone(P, qf(29.6, P), [0.4, 0.25], rng)
    pump = circ_filter(unit(mot) + 0.6 * unit(imp) + 0.5 * unit(shaft), lambda x: lp(x, 900, 2))
    # circulation flow + gurgle (moving band weights)
    g = pink(P, rng)
    flow = circ_filter(g, lambda x: lp(x, 850, 2)) * (1 + 0.1 * _pcurve(P, 0.2, rng))
    g2 = pink(P, rng)
    gur = np.zeros(P)
    for lo_, hi_ in [(180, 360), (360, 720), (720, 1400)]:
        gur += circ_filter(g2, lambda x: bp(x, lo_, hi_, 2)) * np.clip(_pcurve(P, 1.2, rng), 0, 1) ** 2
    # distant lapping against tile: irregular slaps + 'tlok' drops, written around the loop
    lap = np.zeros(P)
    tp = rng.uniform(0, 1)
    while tp < P / SR:
        d = rng.uniform(0.5, 1.1)
        k = ns(d)
        slap = bp(pink(k, rng), 220, 1500, 2) * env_ar(k, rng.uniform(0.08, 0.2), d * 0.7, 1.5)
        mix_wrap(lap, unit(slap) * rng.uniform(0.25, 1.0), ns(tp))
        if rng.random() < 0.4:
            mix_wrap(lap, drop(rng, rng.uniform(280, 520), 0.6, 0.045, 0.5, 0.0, 0.2), ns(tp + d * 0.3))
        tp += rng.uniform(1.0, 3.2)
    lap = circ_filter(lap, lambda x: lp(x, 1400, 2))
    # very faint high tiled-room air tone
    air = circ_filter(white(P, rng), lambda x: bp(x, 4500, 9500, 2)) * (1 + 0.2 * _pcurve(P, 0.1, rng))
    whis = np.sin(2 * np.pi * qf(6300, P) * taxis(P) + 2.0 * np.sin(2 * np.pi * qf(0.17, P) * taxis(P)))
    dry = 0.35 * unit(pump) + unit(flow) + 0.45 * unit(gur) + 0.35 * unit(lap) + 0.06 * unit(air) + 0.004 * whis
    ir, _, _ = get_profile_ir("pool")
    ton = 0.6 * dry + 0.55 * circ_conv(circ_filter(dry, lambda x: lp(x, 3000, 2)), ir)
    return circ_filter(ton, lambda x: hp(x, 28, 2)), None


def bed_garage(rng, v, n, P):
    tP = taxis(P)
    # ventilation: pink air rush with harmonic duct modes, slow turbulence
    rush = circ_filter(pink(P, rng), lambda x: peq(peq(peq(peq(lp(x, 1500, 4), 63, 5, 6), 126, 4, 6),
                                                         189, 3, 6), 340, 3, 3))
    rush *= 1 + 0.14 * _pcurve(P, 0.25, rng) + 0.04 * _pcurve(P, 3.0, rng)
    hiss = circ_filter(white(P, rng), lambda x: bp(x, 2000, 6000, 2))
    blade = additive(2 * np.pi * qf(97, P) * tP + 2.5 * np.sin(2 * np.pi * qf(0.29, P) * tP), [1, 0.5, 0.2])
    vent = unit(rush) + 0.05 * unit(hiss) + 0.06 * blade
    # distant machinery: 16 slow cycles (3 s) of thump + soft clank + motor drone
    mach = np.zeros(P)
    cyc = 16
    cl = P / cyc
    for c in range(cyc):
        base = int(c * cl)
        gg = db(rng.normal(0, 2.0))
        mix_wrap(mach, thump(rng, 62, 32, 0.25, 1.2, 0.8) * gg, base)
        if rng.random() < 0.7:
            ex = burst(rng, 0.01, 0.002, 300, 4000)
            clank = signal.fftconvolve(ex, modal([310, 760, 1290, 1910], [1, 0.6, 0.35, 0.2],
                                                 [0.35, 0.22, 0.12, 0.08], ns(0.7)))
            mix_wrap(mach, clank * 0.6 * gg, base + int(0.52 * cl))
    motor = harm_tone(P, qf(41, P), [1, 0.6, 0.35, 0.2, 0.1], rng) * 0.1
    motor *= 1 + 0.35 * np.sin(2 * np.pi * qf(cyc / (P / SR), P) * tP)
    mach = circ_filter(mach + motor, lambda x: lp(x, 650, 2))
    # steam: three leaks that swell and fade irregularly (loop-periodic), with sputter and flutter
    steam = np.zeros(P)
    for fc, rate, thr, gain in [(3100, 0.08, 0.1, 1.0), (5200, 0.12, 0.25, 0.6), (2200, 0.06, 0.0, 0.6)]:
        src = white(P, rng)
        h = 0.6 * unit(circ_filter(src, lambda x: bp(x, fc * 0.55, fc * 1.8, 2)))
        h += 0.4 * unit(circ_filter(src, lambda x: reson(x, fc, 4)))
        sput = 1 - 0.55 * np.clip(_pcurve(P, 7, rng), 0, 1) ** 2
        steam += h * _swell(P, rate, rng, thr) * sput * (1 + 0.15 * _pcurve(P, 2.5, rng)) * gain
    # metal: sparse expansion ticks and two tiny groans
    met = np.zeros(P)
    for _ in range(7):
        f = np.sort(rng.uniform(1500, 5200, 3))
        mix_wrap(met, _ping(rng, f, rng.uniform(0.04, 0.18, 3)) * rng.uniform(0.3, 1.0), int(rng.uniform(0, P)))
    pk = peak(met)
    for k in range(2):
        m = ns(1.6)
        gr = creak(rng, m, [(0, 9), (0.7, rng.uniform(18, 28)), (1.6, 7)], np.sort(rng.uniform(180, 900, 5)),
                   rng.uniform(0.1, 0.35, 5), env_points(m, [(0, 0), (0.4, 1), (1.1, 0.7), (1.5, 0)]))
        gr = lp(gr, 1500, 2)
        mix_wrap(met, gr * 0.6 * pk / max(peak(gr), 1e-9), int((k + rng.uniform(0.2, 0.8)) * P / 2))
    met = circ_filter(met, lambda x: hp(x, 150, 2))
    ir, _, _ = get_profile_ir("concrete")
    machf = circ_conv(mach, ir)
    dry = (unit(vent) + 0.3 * unit(0.4 * mach + machf) + 0.4 * unit(steam)
           + 0.025 * met / max(rms(met), 1e-9))
    ton = 0.75 * dry + 0.45 * circ_conv(circ_filter(dry, lambda x: lp(x, 5000, 2)), ir)
    return circ_filter(ton, lambda x: hp(x, 28, 2)), None


def bed_office(rng, v, n, P):
    tP = taxis(P)
    ballast = _tube(P, rng, qf(120, P), 1.3, 1.8, 0.04)
    ballast *= periodic_am(P, [(0.31, 0.03, rng.uniform(0, 6)), (1.1, 0.012, 0.0)])
    ballast = circ_filter(ballast, lambda x: lp(x, 5000, 2))
    server = harm_tone(P, qf(60, P), [1, 0.5, 0.3, 0.15], rng)
    fan = harm_tone(P, qf(236, P), [1, 0.45, 0.2, 0.1], rng) * periodic_am(P, [(0.19, 0.08, 0.0)])
    fan_air = circ_filter(pink(P, rng), lambda x: bp(x, 300, 2000, 2))
    whine = np.sin(2 * np.pi * qf(3420, P) * tP + 3 * np.sin(2 * np.pi * qf(0.13, P) * tP))
    comp = (0.6 * unit(circ_filter(server, lambda x: lp(x, 400, 2))) + 0.5 * unit(fan) + 0.6 * unit(fan_air)
            + 0.02 * whine)
    w = np.sin(2 * np.pi * qf(120, P) * tP) + 0.25 * np.sin(2 * np.pi * qf(60, P) * tP + 0.7)
    dist = np.tanh(2.5 * (w + 0.2))
    dist = circ_filter(dist - dist.mean(), lambda x: lp(x, 1400, 2)) * (0.6 + 0.4 * _pcurve(P, 0.05, rng))
    ir, _, _ = get_profile_ir("office")
    cir, _, _ = get_profile_ir("concrete")
    dist = circ_conv(dist, cir)
    ton = 0.16 * unit(ballast) + 0.28 * unit(comp) + 0.05 * unit(dist)
    ton = ton + 0.3 * circ_conv(ton, ir)
    # HVAC diffuser air (noise part, loop-periodic level)
    noi = unit(lp(pink(n, rng), 700, 2)) + 0.12 * unit(bp(white(n, rng), 2000, 6000, 2))
    noi *= db(0.8 * periodic_smooth(n, P, 0.12, rng))
    return ton, unit(noi)


def bed_plains(rng, v, n, P):
    gust = 0.6 + 0.4 * periodic_smooth(n, P, 0.06, rng, 0, 1) ** 1.5
    gust *= 1 + 0.08 * periodic_smooth(n, P, 0.7, rng)
    base = lp(brown(n, rng), 380, 2) * gust
    src = pink(n, rng)
    fs = [260, 370, 510, 690]
    w = np.stack([np.clip(periodic_smooth(n, P, 0.1, rng), 0, 1) ** 2 for _ in fs])
    whistle = sum(reson(src, f, 10) * wi for f, wi in zip(fs, w)) * gust ** 2
    gr = bp(pink(n, rng), 2500, 8000, 2) * gust ** 2.5
    imp = (rng.random(n) < (120 + 500 * gust ** 3) / SR) * white(n, rng)
    crack = bp(imp, 2500, 9000, 2) * gust
    noi = unit(base) + 0.1 * unit(whistle) + 0.07 * unit(gr) + 0.03 * unit(crack)
    return None, hp(noi, 25, 2)


def bed_level0_synth(rng, v, n, P):
    f0 = qf(120, P)
    ton = np.zeros(P)
    for det in [0, 1, -1, 2, -2, 3, -3, 4, -5, 6]:  # whole cycles per loop -> slow, seamless beating
        tube = _tube(P, rng, f0, rng.uniform(1.05, 1.45), rng.uniform(1.4, 2.4), rng.uniform(0.02, 0.08),
                     maxf=6500, detune=det)
        tube *= periodic_am(P, [(rng.uniform(0.05, 0.6), 0.05, rng.uniform(0, 6))])
        if det:  # the other tubes mostly add rattly upper harmonics (their fundamentals would beat too hard)
            tube = circ_filter(tube, lambda x: hp(x, 320, 2))
        ton += unit(tube) * (1.0 if det == 0 else rng.uniform(0.3, 0.5))
    ton = circ_filter(ton, lambda x: lp(x, 5000, 2))
    mains = harm_tone(P, qf(60, P), [1.0, 0.5, 0.3, 0.15], rng)
    mains = circ_filter(mains, lambda x: lp(x, 300, 2))
    ir, _, _ = get_profile_ir("office")
    ton = unit(ton + 0.25 * circ_conv(ton, ir)) + 0.12 * unit(mains)
    t = taxis(n)
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * f0 * t)) ** 4
    buzz = bp(pink(n, rng), 1200, 7000, 2) * gate * db(1.5 * periodic_smooth(n, P, 0.1, rng))
    noi = 0.16 * unit(buzz) + 0.1 * unit(lp(brown(n, rng), 200, 2))
    return ton, noi


# ============================================================================ steam / heavy metal one-shots
def steam_burst(rng, v):
    dur = [1.8, 2.2, 2.0][v % 3] * rng.uniform(0.95, 1.05)
    n = ns(dur + 0.2)
    t = taxis(n)
    src = white(n, rng)
    # moving resonance: resonator bank weighted by a gliding centre frequency (log-gaussian)
    fpk = rng.uniform(3800, 5200)
    c = env_points(n, [(0, fpk * 0.55), (0.05, fpk), (0.4, fpk * 0.8), (dur, fpk * 0.5)])
    c *= 1 + 0.07 * smooth_random(n, 5, rng)
    bank = [1600, 2100, 2800, 3600, 4700, 6100, 7900]
    mov = np.zeros(n)
    for f in bank:
        wgt = np.exp(-0.5 * (np.log(f / c) / 0.22) ** 2)
        mov += reson(src, f, 3.0) * wgt
    broad = bp(src, 1200, 10000, 2)
    hiss = 0.65 * unit(mov) + 0.35 * unit(broad)
    env = env_points(n, [(0, 0), (0.006, 1.0), (0.1, 0.72), (0.5 * dur, 0.5), (0.85 * dur, 0.18), (dur, 0)]) ** 1.2
    # sputter: irregular chopping that grows after the onset, plus wet spits
    depth = env_points(n, [(0, 0), (0.2, 0), (0.45, 0.6 + 0.2 * (v == 1)), (dur, 0.9)])
    chop = 1 - depth * np.clip(smooth_random(n, 16 + 6 * v, rng), 0, 1) ** 1.3
    hiss = hiss * env * chop
    for _ in range(int(rng.integers(4, 10))):
        tp = rng.uniform(0.3, dur * 0.85)
        sp = burst(rng, rng.uniform(0.015, 0.04), 0.008, 700, 5000) * rng.uniform(0.3, 0.8)
        mix_into(hiss, unit(sp) * np.interp(tp, t, env) * 1.2, ns(tp))
    # tail darkens
    wl = np.stack([env_points(n, [(0, 1), (0.4 * dur, 1), (dur, 0.1)]), env_points(n, [(0, 0), (0.4 * dur, 0), (dur, 0.9)])])
    hiss = multi_cutoff_lp(hiss, [12000, 2600], wl)
    # valve tick + faint metallic pipe ring excited by the onset and (weakly) by the hiss
    f1 = rng.uniform(280, 460)
    ring = modal(f1 * np.array([1, 2.76, 5.40, 8.93]) * rng.uniform(0.98, 1.02, 4), [1, 0.6, 0.35, 0.2],
                 [0.45, 0.3, 0.18, 0.1], ns(3.0))
    ex = burst(rng, 0.004, 0.001, 500, 8000)
    x = pad_to(unit(hiss), ns(3.0))
    tick = signal.fftconvolve(ex, ring)[: ns(3.0)]
    mix_into(x, tick * 0.22 / max(peak(tick), 1e-9) * peak(x), 0)
    symp = signal.fftconvolve(hiss[: ns(0.3)], ring)[: ns(3.0)]
    mix_into(x, symp * 0.05 * rms(x) / max(rms(symp), 1e-9), 0)
    return room(hp(x, 300, 2), 0.1)


def metal_clank(rng, v):
    n = ns(4.0)
    x = np.zeros(n)
    f1 = rng.uniform(75, 150)
    pipe = modal(f1 * np.array([1, 2.76, 5.40, 8.93, 13.3]) * rng.uniform(0.97, 1.03, 5),
                 [1, 0.75, 0.5, 0.3, 0.15], [0.7, 0.5, 0.32, 0.2, 0.12], n, rng, True)
    sheet_f = np.sort(rng.uniform(180, 2600, 9))
    sheet = modal(sheet_f, rng.uniform(0.3, 1.0, 9), rng.uniform(0.1, 0.45, 9), n, rng, True)
    hits = [(0.02, 1.0)]
    if v % 2 == 1:
        hits.append((rng.uniform(0.18, 0.4), rng.uniform(0.35, 0.6)))
    for tp, a in hits:
        ex = burst(rng, 0.014, 0.003, None, 4500) * a
        y = signal.fftconvolve(ex, pipe)[:n] + 0.7 * signal.fftconvolve(ex, sheet)[:n] * rms(pipe) / max(rms(sheet), 1e-9)
        y = unit(y) * a
        y += pad_to(thump(rng, 72, 34, 0.12, 0.8, 0.8), n) * 1.6 * a
        boom = reson(lp(white(n, rng), 300, 2), rng.uniform(70, 110), 5) * env_exp(n, 0.35, 0.004)
        y += unit(boom) * 0.6 * a
        mix_into(x, y, ns(tp))
    # rattle: loose small parts bouncing after the hit
    tp, gap, amp = rng.uniform(0.05, 0.1), rng.uniform(0.05, 0.11), 0.5
    for _ in range(int(rng.integers(6, 15))):
        r = _ping(rng, np.sort(rng.uniform(1200, 5500, 3)), rng.uniform(0.01, 0.05, 3), 0.12, 7000)
        mix_into(x, unit(r) * amp * 0.5, ns(tp))
        tp += gap * rng.uniform(0.6, 1.3)
        gap *= rng.uniform(0.75, 0.95)
        amp *= rng.uniform(0.7, 0.9)
    x = lp(x, 3200, 2)  # far away
    return room(x, 0.18, "concrete")


def metal_groan(rng, v):
    dur = 4.0
    n = ns(dur + 0.4)
    t = taxis(n)
    rp = [[(0, 7), (1.1, 20), (2.4, 34), (3.4, 16), (4.4, 6)],
          [(0, 5), (1.6, 13), (2.6, 18), (3.3, 10), (4.4, 5)],
          [(0, 9), (0.9, 26), (1.7, 12), (2.6, 30), (3.6, 9), (4.4, 6)]][v % 3]
    modes = np.sort(rng.uniform(70, 650, 7))
    env = env_points(n, [(0, 0), (0.6, 0.6), (1.6, 1.0), (2.9, 0.8), (3.7, 0)])
    x = creak(rng, n, rp, modes, rng.uniform(0.15, 0.6, 7), env, 0.2)
    # gliding buzzy moan, gated by the slips
    f0 = env_points(n, [(0, rng.uniform(52, 60)), (2.0, rng.uniform(62, 72)), (4.4, rng.uniform(44, 52))])
    f0 *= 1 + 0.01 * smooth_random(n, 3, rng)
    moan = additive(phase_of(f0, n), [1 / k ** 0.8 for k in range(1, 14)])
    moan = reson(moan, modes[2], 3) + 0.5 * lp(moan, 400, 2)
    rate = env_points(n, rp)
    slip = 0.55 + 0.45 * np.sin(np.cumsum(2 * np.pi * rate / SR)) ** 2
    moan *= env * slip * (0.7 + 0.3 * smooth_random(n, 2, rng))
    sub = lp(brown(n, rng), 90, 2) * env
    y = unit(x) + 0.5 * unit(moan) + 0.25 * unit(sub)
    y = lp(y, 1300, 2)
    ir = make_ir(rng, [(0, 400, 1.3), (400, SR / 2, 0.9)], er=[(0.07, 0.3, 1200), (0.16, 0.15, 900)],
                 predelay=0.03, onset=0.05)
    return hp(apply_ir(y, ir, 0.7, 0.5), 35, 2)


# ============================================================================ bacteria (Level 0 entity)
def _carpet_fall(rng, weight=1.0):
    """One long-limbed footfall on carpet: soft padded thud, low body weight, faint fabric scuff."""
    n = ns(0.14)
    y = lp(white(n, rng), rng.uniform(260, 420), 2) * env_exp(n, rng.uniform(0.014, 0.024), 0.0015) * 3
    y += pad_to(thump(rng, rng.uniform(70, 100), 42, 0.03, 0.14, 0.25), n) * 0.9
    y += bp(white(n, rng), 1500, 5000, 2) * env_ar(n, 0.01, 0.1) * 0.05
    return y * weight


def bacteria_skitter(rng, v):
    """Fast, irregular, long-limbed footfalls on carpet, with dry joint clicks between them."""
    n = ns(0.6)
    x = np.zeros(n)
    fast = [0.035, 0.05, 0.03, 0.045][v % 4]
    tp = rng.uniform(0.004, 0.02)
    while tp < 0.46:
        mix_into(x, _carpet_fall(rng, rng.uniform(0.55, 1.0)), ns(tp))
        if rng.random() < 0.45:  # a joint snapping as the limb unfolds
            mix_into(x, _bone_crack(rng, rng.uniform(0.5, 0.8), 0.6) * rng.uniform(0.12, 0.3),
                     ns(max(0.0, tp + rng.uniform(-0.02, 0.025))))
        # gallop-like groups: short gaps inside a group, a longer stride between groups
        tp += rng.uniform(fast * 0.5, fast * 1.2) if rng.random() < 0.75 else rng.uniform(0.06, 0.11)
    k = ns(rng.uniform(0.08, 0.16))  # long fingers dragging over the carpet pile
    drag = bp(white(k, rng), 1800, 6500, 2) * env_ar(k, 0.02, k / SR * 0.7) * 0.07
    mix_into(x, drag, ns(rng.uniform(0.1, 0.4)))
    return room(hp(x, 45, 2), 0.06, "carpet")


def bacteria_breath(rng, v):
    """Wet, ragged, rattling breath, very close and dry: breath cycles with a throat rattle and bubbles."""
    dur = 3.0
    n = ns(dur)
    x = np.zeros(n)
    tpos = rng.uniform(0.02, 0.08)
    while tpos < dur - 0.7:
        for phase in ("in", "out"):
            d = rng.uniform(0.55, 0.8) if phase == "in" else rng.uniform(0.7, 0.95)
            d = min(d, dur - tpos - 0.02)
            if d < 0.25:
                break
            k = ns(d)
            tk = taxis(k)
            src = 0.6 * pink(k, rng) + 0.4 * white(k, rng)
            if phase == "in":
                air = _formants(bp(src, 700, 6500, 2), [(1900, 3, 1.0), (3400, 4, 0.6), (5600, 3, 0.3)])
                rf = rng.uniform(26, 38)
            else:
                air = _formants(bp(src, 200, 4500, 2), [(620, 3, 1.0), (1200, 4, 0.7), (2600, 5, 0.4)])
                rf = rng.uniform(18, 30)
            # throat rattle: phlegm fluttering at ~20-40 Hz, irregular
            rate = rf * (1 + 0.2 * smooth_random(k, 6, rng))
            rat = (0.5 + 0.5 * np.sin(np.cumsum(2 * np.pi * rate / SR))) ** 6
            rasp = _formants(_glottal(rng, k, rng.uniform(55, 80), 0.12),
                             [(500, 5, 1.0), (1100, 6, 0.6), (2300, 7, 0.3)])
            body = unit(air) * (0.55 + 0.45 * rat) + 0.35 * unit(rasp) * rat * (0.6 if phase == "in" else 1.0)
            # ragged: catches and stutters in the airflow
            catch = 1 - 0.7 * np.clip(smooth_random(k, 14, rng), 0, 1) ** 2
            shape = ([(0, 0), (d * 0.15, 0.8), (d * 0.6, 1.0), (d, 0)] if phase == "in"
                     else [(0, 0), (0.05, 1.0), (d * 0.5, 0.7), (d, 0)])
            seg = body * catch * env_points(k, shape) * (0.75 if phase == "in" else 1.0)
            # wet: small bubbles / clicks of saliva
            lvl = rms(seg)
            for _ in range(int(rng.poisson(14 * d if phase == "in" else 26 * d))):
                b = drop(rng, rng.uniform(180, 750), 0.4, rng.uniform(0.006, 0.015), rng.uniform(0.1, 0.35), 0.05,
                         0.05)
                mix_into(seg, b * lvl * 4, ns(rng.uniform(0, d - 0.05)))
            mix_into(x, seg * (1 + 0.1 * np.sin(2 * np.pi * rng.uniform(5, 8) * tk)), ns(tpos))
            tpos += d + rng.uniform(0.04, 0.16)
    return hp(x, 80, 2)


# ============================================================================ cars (Level 1 parking garage)
def _engine_pulse(rng, f=62.0, tau=0.016):
    """One cylinder firing as heard from the exhaust: a low decaying pressure thump plus a little noise."""
    n = ns(0.08)
    t = taxis(n)
    x = np.sin(2 * np.pi * f * t * (1 - 0.25 * t / 0.08)) * env_exp(n, tau, 0.0015)
    x += 0.35 * lp(white(n, rng), 1200, 2) * env_exp(n, tau * 0.5, 0.0005)
    return x


def _exhaust(x, circular=False):
    def f(y):
        return (reson(y, 92, 3) + 0.55 * reson(y, 185, 4) + 0.3 * reson(y, 370, 5) + 0.12 * reson(y, 760, 6)
                + 0.5 * lp(y, 600, 2))
    return circ_filter(x, f) if circular else f(x)


def car_engine(rng, v, n, P):
    """Old 4-cylinder idling (~740 rpm): loop-periodic firing pulses (a weak cylinder, slight timing jitter),
    exhaust resonances, tappet ticks, alternator whine; rumble and intake hiss as noise."""
    pd = P / SR
    nf = int(round(24.6 * pd))                     # firings per loop period (integer -> exactly periodic)
    sp = P / nf
    cyl = np.array([1.0, 0.86, 0.97, 0.7]) * rng.uniform(0.95, 1.05, 4)
    per = np.zeros(P)
    for i in range(nf):
        pos = int(round(i * sp + rng.normal(0, 0.0025) * SR))
        mix_wrap(per, _engine_pulse(rng, rng.uniform(56, 66)) * cyl[i % 4] * db(rng.normal(0, 1.0)), pos)
    per = _exhaust(per, True)
    tick = np.zeros(P)
    for i in range(2 * nf):                        # tappets / valve train
        tk = burst(rng, 0.002, 0.0004, 2500, 9000) * rng.uniform(0.3, 1.0)
        mix_wrap(tick, tk, int(i * sp / 2 + rng.uniform(0, 0.004) * SR))
    tick = circ_filter(tick, lambda y: reson(y, 3600, 5) + 0.5 * reson(y, 5200, 6))
    t = taxis(P)
    fire_f = nf / pd
    whine = np.sin(2 * np.pi * qf(820, P) * t + 0.4 * np.sin(2 * np.pi * fire_f * t))
    hunt = periodic_am(P, [(0.75, 0.06, 0.0), (1.5, 0.03, 1.0)])   # idle hunting
    ton = (unit(per) + 0.02 * unit(circ_filter(tick, lambda y: lp(y, 6000, 2))) + 0.012 * whine) * hunt
    tn = taxis(n)
    chuff = (0.5 + 0.5 * np.cos(2 * np.pi * fire_f * tn)) ** 3
    noi = 0.5 * unit(lp(brown(n, rng), 160, 2)) + 0.25 * unit(bp(pink(n, rng), 500, 3000, 2)) * (0.5 + chuff)
    noi *= 1 + 0.1 * periodic_smooth(n, P, 0.8, rng)
    return ton, 0.22 * unit(noi)


def car_start(rng, v):
    """Starter motor cranking against compression, the engine catching, a rev and settling towards idle."""
    dur = 1.5
    n = ns(dur)
    t = taxis(n)
    x = np.zeros(n)
    tc = rng.uniform(0.62, 0.72)                           # the engine catches
    crank = ((t > 0.03) & (t < tc + 0.08)).astype(float)
    comp = 0.5 + 0.5 * np.cos(2 * np.pi * 6.4 * t)        # compression strokes slow the starter down
    fm = 78 * (1 - 0.14 * comp) * np.clip((t - 0.03) / 0.08, 0, 1)
    fm = fm * (1 + 0.6 * np.clip((t - tc) / 0.08, 0, 1))  # freewheels as the engine fires
    ph = phase_of(fm + 1e-6, n)
    starter = additive(ph, [0.4, 0.3, 0.25, 0.3, 0.2, 0.18, 0.15, 0.12, 0.1, 0.12, 0.08, 0.06, 0.05])
    starter += 0.3 * np.sin(ph * 11)                      # pinion gear whine
    starter = bp(starter, 150, 4000, 2) * (0.55 + 0.45 * (1 - comp)) * crank
    starter *= env_points(n, [(0, 0), (0.05, 1), (tc, 1), (tc + 0.08, 0)])
    chug = np.zeros(n)
    tp = 0.08
    while tp < tc:
        mix_into(chug, fade_edges(thump(rng, 70, 45, 0.04, 0.15, 0.6), 0.002, 0.04) * rng.uniform(0.5, 0.8), ns(tp))
        tp += 1 / 6.4
    # firing: two sparse pops, then an accelerating run (rev to ~1600 rpm) settling towards idle
    fire = np.zeros(n)
    tp = tc
    pops = 0
    while tp < dur - 0.05:
        u = tp - tc
        rate = 8.0 if pops < 2 else float(np.interp(u, [0.0, 0.1, 0.3, 0.75], [12, 55, 48, 27]))
        mix_into(fire, _engine_pulse(rng, rng.uniform(55, 70)) * rng.uniform(0.7, 1.1) * (1.4 if pops < 2 else 1.0),
                 ns(tp))
        tp += 1 / rate * rng.uniform(0.85, 1.15)
        pops += 1
    fire = _exhaust(fire)
    revn = env_points(n, [(0, 0), (tc, 0), (tc + 0.25, 1), (dur, 0.7)])
    roar = lp(brown(n, rng), 250, 2) * revn
    x += 0.5 * unit(starter) + 0.35 * unit(chug) + unit(fire) + 0.25 * unit(roar)
    cl = clunk(rng, 0.6)
    mix_into(x, cl / peak(cl) * 0.45 * peak(x), 0)          # starter solenoid
    x = x * env_points(n, [(0, 1), (dur - 0.3, 1), (dur, 0)])
    return room(x, 0.06)


def car_horn(rng, v):
    """Old vibrating-diaphragm car horn: buzzy pulse source with a pitch sag at the onset, horn resonances.
    v0: one long two-tone honk; v1: two short honks."""
    if v % 2 == 0:
        segs, tones = [(0.0, 0.7)], [356.0, 449.0]
    else:
        segs, tones = [(0.0, 0.22), (0.33, 0.42)], [412.0, 520.0]
    dur = segs[-1][0] + segs[-1][1] + 0.08
    n = ns(dur)
    x = np.zeros(n)
    for s0, d in segs:
        k = ns(d)
        tk = taxis(k)
        seg = np.zeros(k)
        for f in tones:
            f0 = f * (1 - 0.05 * np.exp(-tk / 0.03)) * (1 + 0.004 * smooth_random(k, 8, rng))
            src = additive(phase_of(f0, k, rng.uniform(0, 6.28)), [h ** -0.75 for h in range(1, 26)])
            seg += saturate(src / max(peak(src), 1e-9) + 0.25, 3.0)
        seg *= 1 + 0.08 * np.sin(2 * np.pi * rng.uniform(25, 35) * tk)   # rough old diaphragm
        seg *= env_ar(k, 0.012, 0.035, 1.0)
        mix_into(x, seg, ns(s0))
    x = hp(x, 250, 2)
    x = peq(peq(x, 1250, 6, 2.0), 2500, 4, 2.5)
    x = lp(x, 4200, 4)
    return room(x, 0.05)


def car_door(rng, v):
    """v0: door slammed shut (latch, heavy panel thunk, trim rattle); v1: door opened (handle, latch release,
    short hinge creak, lighter stop thunk)."""
    n = ns(0.65)
    x = np.zeros(n)

    def panel():
        return modal(np.array([120, 205, 330, 520, 760]) * rng.uniform(0.93, 1.07, 5),
                     [1.0, 0.7, 0.5, 0.3, 0.2], [0.11, 0.08, 0.05, 0.035, 0.02], ns(0.4), rng, True)

    def latch():
        return _ping(rng, list(np.array([2100, 3400, 5100]) * rng.uniform(0.9, 1.1)), [0.012, 0.008, 0.005], 0.06)

    if v % 2 == 0:
        ex = burst(rng, 0.012, 0.004, 60, 1800)
        th = signal.fftconvolve(ex, panel())[: ns(0.4)]
        mix_into(x, unit(th), ns(0.012))
        mix_into(x, pad_to(thump(rng, 85, 42, 0.06, 0.3, 0.6), ns(0.3)) * 2.0, ns(0.012))
        mix_into(x, unit(latch()) * 0.6, ns(0.01))
        mix_into(x, unit(latch()) * 0.4, ns(0.03))
        for _ in range(int(rng.integers(3, 7))):
            mix_into(x, burst(rng, 0.003, 0.0008, 1500, 7000) * rng.uniform(0.05, 0.15), ns(rng.uniform(0.04, 0.16)))
    else:
        mix_into(x, unit(latch()) * 0.5, 0)
        mix_into(x, unit(latch()) * 0.7, ns(rng.uniform(0.07, 0.1)))
        k = ns(0.22)
        cr = creak(rng, k, [(0, 30), (0.22, 60)], rng.uniform(600, 1800, 3), [0.02] * 3, env_ar(k, 0.03, 0.12), 0.3)
        mix_into(x, unit(cr) * 0.15, ns(0.12))
        ex = burst(rng, 0.008, 0.003, 60, 1500)
        th = signal.fftconvolve(ex, panel())[: ns(0.3)]
        mix_into(x, unit(th) * 0.6, ns(0.36))
    return room(hp(x, 40, 2), 0.06)


# ============================================================================ steam / lifts / slides
def steam_hiss(rng, v, n, P):
    """Continuous steam leak from a pipe valve: resonant hiss with a slowly wandering resonance, a slight
    flutter, a faint whistle."""
    pre = ns(0.5)
    m = n + pre
    src = white(m, rng)
    c = 4300 * (1 + 0.08 * np.concatenate([np.zeros(pre), periodic_smooth(n, P, 0.4, rng)]))
    mov = np.zeros(m)
    for f in (2100, 2800, 3600, 4700, 6100, 7900):
        mov += reson(src, f, 3.0) * np.exp(-0.5 * (np.log(f / c) / 0.3) ** 2)
    broad = bp(src, 1300, 11000, 2)
    tm = taxis(m)
    flutter = (1 + 0.1 * np.sin(2 * np.pi * 11.3 * tm + 0.8 * np.sin(2 * np.pi * 0.7 * tm))
               + 0.07 * np.clip(smooth_random(m, 9, rng), -1, 1))
    hiss = (0.6 * unit(mov) + 0.4 * unit(broad)) * flutter + 0.06 * unit(lp(brown(m, rng), 220, 2))
    ir, _, _ = get_profile_ir("concrete")
    hiss = apply_ir(hp(hiss, 280, 2), ir, 1.0, 0.12)[pre:pre + n]
    t = taxis(P)
    whistle = np.sin(2 * np.pi * qf(5240, P) * t + 2.0 * np.sin(2 * np.pi * qf(0.5, P) * t))
    return 0.012 * whistle, unit(hiss)


def lift_ding(rng, v):
    """2000s lift arrival chime: one soft electronic bell strike through a small ceiling speaker."""
    n = ns(1.25)
    t = taxis(n)
    f = 987.77
    x = (np.sin(2 * np.pi * f * t) * np.exp(-t / 0.5)
         + 0.35 * np.sin(2 * np.pi * f * 2.0 * t) * np.exp(-t / 0.3)
         + 0.18 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t / 0.18)
         + 0.08 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t / 0.08)
         + 0.1 * np.sin(2 * np.pi * f * 0.5 * t) * np.exp(-t / 0.4))
    x *= np.clip(t / 0.004, 0, 1) * env_points(n, [(0, 1), (1.05, 1), (1.25, 0)])
    x = bp(saturate(x / peak(x) * 0.9, 1.3), 280, 7500, 2)
    return room(x, 0.15, "office")


def lift_door(rng, v):
    """Sliding lift doors: relay click, operator motor whir and roller rumble, panel rattle, rubber-edge bump."""
    dur = 1.55
    n = ns(dur)
    x = np.zeros(n)
    sp = env_points(n, [(0, 0), (0.1, 0), (0.25, 1), (0.95, 0.9), (1.2, 0.25), (1.27, 0), (dur, 0)])
    fw = 300 + 180 * sp
    whine = additive(phase_of(fw, n), [1, 0.4, 0.25, 0.1]) * sp ** 1.5
    rum = lp(brown(n, rng), 260, 2) * sp
    rollers = bp(white(n, rng), 120, 700, 2) * (0.5 + 0.5 * np.sin(np.cumsum(2 * np.pi * 15 * sp / SR))) ** 4 * sp
    rattle = bp(white(n, rng), 1500, 5000, 2) * np.clip(smooth_random(n, 22, rng), 0, 1) ** 3 * sp
    x += 0.1 * unit(whine) + 0.6 * unit(rum) + 0.5 * unit(rollers) + 0.18 * unit(rattle)
    cl = clunk(rng, 0.3)
    mix_into(x, cl / peak(cl) * 0.3 * peak(x), 0)
    mix_into(x, pad_to(thump(rng, 75, 42, 0.05, 0.25, 0.6), ns(0.25)) * 1.6, ns(1.27))
    mix_into(x, unit(_ping(rng, [310, 530, 870], [0.08, 0.05, 0.03], 0.25)) * 0.35, ns(1.275))
    return room(hp(x, 40, 2), 0.12, "office")


def lift_motor(rng, v, n, P):
    """Inside a travelling lift car: traction-motor hum, inverter whine, rope/rail rush, faint guide-rail
    bumps (loop-periodic), small carpeted-cabin reflections."""
    t = taxis(P)
    hum = harm_tone(P, qf(60, P), [0.25, 1.0, 0.2, 0.35, 0.08, 0.12, 0.05], rng)
    whine = (np.sin(2 * np.pi * qf(1460, P) * t + 0.6 * np.sin(2 * np.pi * qf(0.5, P) * t))
             + 0.3 * np.sin(2 * np.pi * qf(2920, P) * t))
    bumps = np.zeros(P)
    for k in range(3):
        pos = int((k + rng.uniform(0.1, 0.9)) * P / 3)
        b = pad_to(thump(rng, 60, 38, 0.04, 0.2, 0.7), ns(0.2)) + pad_to(burst(rng, 0.01, 0.003, 300, 2500), ns(0.2)) * 0.3
        mix_wrap(bumps, b * rng.uniform(0.6, 1.0), pos)
    ir, _, _ = get_profile_ir("carpet")
    ton = unit(hum) + 0.07 * whine + 0.35 * unit(circ_filter(bumps, lambda y: lp(y, 1500, 2)))
    ton = ton + 0.25 * circ_conv(ton, ir)
    rush = lp(pink(n, rng), 450, 2) * (1 + 0.15 * periodic_smooth(n, P, 0.6, rng))
    air = bp(white(n, rng), 400, 2500, 2)
    noi = unit(rush) + 0.12 * unit(air)
    return 0.6 * ton, 0.5 * unit(noi)


def slide_rush(rng, v):
    """Rushing water slide ride: turbulent water flow with moving bands, a whoosh that approaches and passes
    (low-pass opening and closing), spray drops, a low gurgle."""
    dur = 3.0
    n = ns(dur + 0.2)
    env = env_points(n, [(0, 0), (0.25, 0.55), (0.7, 1.0), (2.1, 0.95), (2.7, 0.35), (dur, 0), (dur + 0.2, 0)])
    src = pink(n, rng)
    bands = [bp(src, 250, 700, 2), bp(src, 700, 1800, 2), bp(src, 1800, 4500, 2), bp(src, 4500, 11000, 2)]
    w = [np.clip(0.5 + 0.6 * smooth_random(n, 9, rng), 0.05, 1.2) for _ in bands]
    flow = sum(unit(b) * wi * g for b, wi, g in zip(bands, w, (1.0, 0.8, 0.5, 0.25)))
    pas = env_points(n, [(0, 0), (1.2, 1), (1.6, 1), (dur, 0.1), (dur + 0.2, 0.1)])
    flow = multi_cutoff_lp(flow, [1200, 9000], np.stack([1 - pas, pas]))
    gurgle = lp(brown(n, rng), 220, 2) * (0.6 + 0.4 * np.clip(smooth_random(n, 5, rng), 0, 1))
    x = (unit(flow) + 0.4 * unit(gurgle)) * env
    for _ in range(int(rng.integers(30, 55))):
        tp = rng.uniform(0.2, dur - 0.2)
        mix_into(x, drop(rng, rng.uniform(500, 1800), 0.7, 0.02, rng.uniform(0.1, 0.35), 0.1, 0.12)
                 * float(np.interp(tp * SR, np.arange(n), env)), ns(tp))
    return room(hp(x, 40, 2), 0.08, "office")


# ============================================================================ creature (skinwalker, tentacles)
def skinwalker_tear(rng, v):
    """Flesh splitting along the back: a slow wet rip (band-limited noise through a moving resonance, stick-slip
    gated), sinew pings, a few cracks, and a heavy wet release."""
    dur = 1.6
    n = ns(dur)
    t = taxis(n)
    x = np.zeros(n)
    # the rip: stick-slip gating (fast random gate) on squelchy noise whose resonance climbs as it tears
    g = np.clip(smooth_random(n, rng.uniform(40, 70), rng, -0.4, 1.0), 0, None) ** 1.6
    rip = bp(pink(n, rng), 250, 4200, 2) * g
    fc = 380 + 1500 * (t / dur) ** 1.3
    rip = sum(reson(rip, f, 3) * w for f, w in ((fc.mean() * 0.6, 0.5), (fc.mean(), 0.8), (fc.mean() * 1.7, 0.4)))
    rip *= env_points(n, [(0, 0.0), (0.12, 0.5), (1.1, 1.0), (1.35, 0.7), (dur, 0.0)])
    x += unit(rip) * 0.8
    # sinew pings and cracks scattered through it
    tp = rng.uniform(0.1, 0.25)
    while tp < 1.3:
        if rng.random() < 0.55:
            mix_into(x, _bone_crack(rng, rng.uniform(0.7, 1.1)) * rng.uniform(0.2, 0.45), ns(tp))
        else:
            mix_into(x, _wet_pop(rng, rng.uniform(0.6, 1.0)) * rng.uniform(0.25, 0.5), ns(tp))
        tp += rng.exponential(0.12)
    # the release: a deep wet slap as it opens
    rel = ns(rng.uniform(1.25, 1.38))
    mix_into(x, pad_to(thump(rng, 120, 45, 0.09, 0.35, 0.9), ns(0.35)) * 0.9, rel)
    mix_into(x, _wet_pop(rng, 1.6) * 0.7, rel)
    return room(hp(x, 45, 2), 0.08)


def skinwalker_whip(rng, v):
    """A tentacle lash: a rising whoosh (air past a long limb) ending in a hard wet crack."""
    dur = 0.7
    n = ns(dur)
    t = taxis(n)
    hit = rng.uniform(0.32, 0.4)
    # whoosh: noise through a band that sweeps up and gets louder until the hit
    k = ns(hit)
    w = pink(k, rng)
    tk = taxis(k)
    f = 300 + 2200 * (tk / hit) ** 2
    wh = np.zeros(k)
    for lo in (0.6, 1.0, 1.5):
        wh += reson(w, float(np.mean(f) * lo), 2.5)
    wh = unit(wh) * (tk / hit) ** 2.2
    x = np.zeros(n)
    mix_into(x, wh * 0.7, 0)
    # the crack: a broadband snap with a short body thud and a wet smack
    mix_into(x, burst(rng, 0.03, 0.004, 900, 11000) * 1.2, ns(hit))
    mix_into(x, pad_to(thump(rng, 160, 70, 0.03, 0.15, 0.6), ns(0.15)) * 0.8, ns(hit))
    mix_into(x, _wet_pop(rng, 0.9) * 0.4, ns(hit + 0.01))
    return room(hp(x, 60, 2), 0.06)


def skinwalker_crawl(rng, v):
    """Its legs planting as it crawls: three to five soft, wet, heavy tentacle tips set down in quick succession,
    with a drag of flesh on the floor between."""
    dur = 0.8
    n = ns(dur)
    x = np.zeros(n)
    tp = rng.uniform(0.01, 0.04)
    for j in range(int(rng.integers(3, 6))):
        a = rng.uniform(0.6, 1.0)
        mix_into(x, pad_to(thump(rng, rng.uniform(110, 160), 55, 0.035, 0.2, 0.7), ns(0.2)) * a * 0.8, ns(tp))
        mix_into(x, _wet_pop(rng, rng.uniform(0.7, 1.1)) * a * 0.35, ns(tp + 0.004))
        if rng.random() < 0.5:
            mix_into(x, _leg_tap(rng, 0.6) * a * 0.3, ns(tp + 0.01))
        tp += rng.uniform(0.09, 0.17)
        if tp > 0.62:
            break
    drag = bp(brown(n, rng), 120, 1400, 2) * np.clip(smooth_random(n, 9, rng, -0.3, 1.0), 0, None)
    x += unit(drag) * 0.18
    return room(hp(x, 40, 2), 0.05)


# ============================================================================ cars
def car_brake(rng, v):
    """Tyres squealing under hard braking: a narrow, wavering 1.6-3 kHz tone (stick-slip of rubber) over road
    hiss, fading as the car stops."""
    dur = 1.2
    n = ns(dur)
    t = taxis(n)
    f0 = rng.uniform(1600, 2400)
    wob = smooth_random(n, 7, rng) * 90 + smooth_random(n, 31, rng) * 25
    tone = np.sin(phase_of(f0 + wob - 260 * t, n))
    tone += 0.35 * np.sin(phase_of(2 * (f0 + wob - 260 * t), n))
    am = 0.6 + 0.4 * np.clip(smooth_random(n, 18, rng), -1, 1)
    hiss = bp(white(n, rng), 1500, 6000, 2) * 0.25
    x = (tone * am + hiss) * env_points(n, [(0, 0.0), (0.05, 1.0), (0.8, 0.8), (dur, 0.0)])
    return room(x, 0.1, "concrete")


def metal_scrape(rng, v):
    """A car's flank grinding along a wall: gritty friction noise exciting inharmonic sheet-metal modes."""
    dur = 1.0
    n = ns(dur)
    exc = bp(white(n, rng), 300, 8000, 2) * np.clip(smooth_random(n, 60, rng, -0.2, 1.0), 0, None)
    modes = modal(np.sort(rng.uniform(220, 3400, 8)), rng.uniform(0.3, 1.0, 8), rng.uniform(0.04, 0.2, 8), ns(0.4), rng, True)
    x = signal.fftconvolve(exc, modes)[:n]
    x = unit(x) * 0.8 + unit(exc) * 0.35
    x *= env_points(n, [(0, 0.0), (0.04, 1.0), (0.75, 0.85), (dur, 0.0)])
    return room(hp(x, 80, 2), 0.1, "concrete")
