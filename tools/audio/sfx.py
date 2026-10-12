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
