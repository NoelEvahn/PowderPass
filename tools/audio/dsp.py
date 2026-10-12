"""Shared DSP toolkit for the offline audio pipeline.

Everything is mono float64 at 44.1 kHz unless stated otherwise (stereo = shape (n, 2)).
All randomness comes from explicitly seeded numpy Generators so output is deterministic.
"""
from __future__ import annotations

import zlib

import numpy as np
from scipy import signal
from scipy.interpolate import CubicSpline
from scipy.ndimage import minimum_filter1d

SR = 44100
FADE = 0.3  # loop fade length (s) — contract with the plugin (6 ticks overlap)
PROFILES = ["carpet", "office", "concrete", "pool", "corridor", "open"]


# ----------------------------------------------------------------------------- basics
def ns(seconds: float) -> int:
    return int(round(seconds * SR))


def taxis(n: int) -> np.ndarray:
    return np.arange(n) / SR


def rng_for(*parts) -> np.random.Generator:
    seed = zlib.crc32("|".join(str(p) for p in parts).encode("utf-8"))
    return np.random.default_rng(seed)


def db(x: float) -> float:
    return 10 ** (x / 20.0)


def to_db(x: float) -> float:
    return 20 * np.log10(max(x, 1e-12))


def peak(x) -> float:
    return float(np.max(np.abs(x))) if len(x) else 0.0


def rms(x) -> float:
    return float(np.sqrt(np.mean(np.square(x)))) if len(x) else 0.0


def mix_into(buf: np.ndarray, x: np.ndarray, start: int, gain: float = 1.0):
    """Add x into buf at sample offset start (clipped to buffer bounds)."""
    if start >= len(buf):
        return
    if start < 0:
        x = x[-start:]
        start = 0
    end = min(len(buf), start + len(x))
    buf[start:end] += gain * x[: end - start]


def pad_to(x: np.ndarray, n: int) -> np.ndarray:
    if len(x) >= n:
        return x[:n]
    out = np.zeros((n,) + x.shape[1:])
    out[: len(x)] = x
    return out


# ----------------------------------------------------------------------------- noise
def white(n, rng):
    return rng.standard_normal(n)


def colored(n, rng, exponent=1.0):
    """1/f^exponent noise via FFT shaping (exponent 1 = pink, 2 = brown), unit RMS."""
    m = max(n, 16)
    X = np.fft.rfft(rng.standard_normal(m))
    f = np.fft.rfftfreq(m, 1 / SR)
    f[0] = f[1]
    X /= f ** (exponent / 2.0)
    X[0] = 0
    x = np.fft.irfft(X, m)[:n]
    s = np.std(x)
    return x / s if s > 0 else x


def pink(n, rng):
    return colored(n, rng, 1.0)


def brown(n, rng):
    return colored(n, rng, 2.0)


def smooth_random(n, rate_hz, rng, lo=-1.0, hi=1.0):
    """Smooth random control curve (cubic spline through random points at rate_hz)."""
    k = max(4, int(n / SR * rate_hz) + 4)
    xs = np.linspace(0, n, k)
    ys = rng.uniform(lo, hi, k)
    return CubicSpline(xs, ys)(np.arange(n))


# ----------------------------------------------------------------------------- filters
def _sos(kind, freq, order=4):
    if kind in ("bandpass", "bandstop"):
        f = [max(1.0, freq[0]), min(SR / 2 * 0.98, freq[1])]
    else:
        f = min(SR / 2 * 0.98, max(1.0, freq))
    return signal.butter(order, f, btype=kind, fs=SR, output="sos")


def lp(x, f, order=4):
    return signal.sosfilt(_sos("lowpass", f, order), x, axis=0)


def hp(x, f, order=4):
    return signal.sosfilt(_sos("highpass", f, order), x, axis=0)


def bp(x, lo, hi, order=4):
    return signal.sosfilt(_sos("bandpass", (lo, hi), order), x, axis=0)


def biquad_peak(f0, gain_db, q):
    A = 10 ** (gain_db / 40)
    w0 = 2 * np.pi * f0 / SR
    alpha = np.sin(w0) / (2 * q)
    b = [1 + alpha * A, -2 * np.cos(w0), 1 - alpha * A]
    a = [1 + alpha / A, -2 * np.cos(w0), 1 - alpha / A]
    return np.array(b) / a[0], np.array(a) / a[0]


def peq(x, f0, gain_db, q=1.0):
    b, a = biquad_peak(f0, gain_db, q)
    return signal.lfilter(b, a, x, axis=0)


def reson(x, f0, q):
    """Constant-peak-gain band-pass resonator."""
    b, a = signal.iirpeak(min(f0, SR / 2 * 0.95), q, fs=SR)
    return signal.lfilter(b, a, x, axis=0)


def circ_filter(period: np.ndarray, fn):
    """Apply a (causal, decaying) filter fn to a periodic signal so the result stays periodic."""
    p = len(period)
    y = fn(np.concatenate([period, period, period]))
    return y[2 * p: 3 * p]


def circ_conv(period: np.ndarray, ir: np.ndarray):
    """Circular convolution (wraps reverb tails) so loops stay periodic."""
    p = len(period)
    y = signal.fftconvolve(period, ir)
    out = y[:p].copy()
    tail = y[p:]
    while len(tail):
        k = min(p, len(tail))
        out[:k] += tail[:k]
        tail = tail[k:]
    return out


def multi_cutoff_lp(x, cutoffs, weights):
    """Time-varying low-pass by crossfading statically filtered copies.
    weights: array (len(cutoffs), n) non-negative, normalised here."""
    w = np.asarray(weights, float)
    w = w / np.maximum(w.sum(axis=0, keepdims=True), 1e-9)
    out = np.zeros_like(x)
    for c, wi in zip(cutoffs, w):
        out += lp(x, c, 2) * wi
    return out


# ----------------------------------------------------------------------------- envelopes
def env_exp(n, tau, attack=0.002):
    t = taxis(n)
    e = np.exp(-t / max(tau, 1e-4))
    na = min(n, max(1, ns(attack)))
    e[:na] *= np.linspace(0, 1, na)
    return e


def env_ar(n, attack, release, curve=2.0):
    """Attack-sustain-release over n samples (smooth sine-shaped edges)."""
    e = np.ones(n)
    na, nr = min(n, ns(attack)), min(n, ns(release))
    if na:
        e[:na] = np.sin(np.linspace(0, np.pi / 2, na)) ** curve
    if nr:
        e[n - nr:] *= np.cos(np.linspace(0, np.pi / 2, nr)) ** curve
    return e


def env_points(n, pts):
    """Piecewise-linear envelope from (time_s, value) points."""
    ts = np.array([p[0] for p in pts]) * SR
    vs = np.array([p[1] for p in pts])
    return np.interp(np.arange(n), ts, vs)


def fade_edges(x, fin=0.002, fout=0.02):
    x = x.copy()
    a, b = min(len(x), ns(fin)), min(len(x), ns(fout))
    if a:
        x[:a] *= np.sin(np.linspace(0, np.pi / 2, a))[:, None] if x.ndim == 2 else np.sin(np.linspace(0, np.pi / 2, a))
    if b:
        g = np.cos(np.linspace(0, np.pi / 2, b))
        x[-b:] *= g[:, None] if x.ndim == 2 else g
    return x


# ----------------------------------------------------------------------------- oscillators
def osc_sine(freq, n, phase=0.0):
    """freq: scalar or per-sample array (Hz)."""
    if np.isscalar(freq):
        return np.sin(2 * np.pi * freq * taxis(n) + phase)
    ph = 2 * np.pi * np.cumsum(freq) / SR + phase
    return np.sin(ph)


def phase_of(freq, n, phase=0.0):
    if np.isscalar(freq):
        return 2 * np.pi * freq * taxis(n) + phase
    return 2 * np.pi * np.cumsum(freq) / SR + phase


def additive(phase, amps, ratios=None):
    """Sum of sin(k*phase) — band-limited waveform from a running fundamental phase."""
    out = np.zeros_like(phase)
    for i, a in enumerate(amps):
        if a == 0:
            continue
        k = (i + 1) if ratios is None else ratios[i]
        out += a * np.sin(k * phase)
    return out


def saw_bl(freq, n, phase=0.0, max_h=60):
    f0 = freq if np.isscalar(freq) else float(np.max(freq))
    H = int(max(1, min(max_h, (SR / 2 * 0.9) // max(f0, 1))))
    ph = phase_of(freq, n, phase)
    return additive(ph, [(-1) ** (k + 1) / k for k in range(1, H + 1)]) * (2 / np.pi)


def modal(freqs, amps, decays, n, rng=None, phase_rand=False):
    """Impulse response of a bank of decaying sinusoidal modes."""
    t = taxis(n)
    out = np.zeros(n)
    for f, a, d in zip(freqs, amps, decays):
        if f >= SR / 2 * 0.95:
            continue
        ph = rng.uniform(0, 2 * np.pi) if (phase_rand and rng is not None) else 0.0
        out += a * np.sin(2 * np.pi * f * t + ph) * np.exp(-t / d)
    return out


# ----------------------------------------------------------------------------- nonlinear / time
def saturate(x, drive=2.0):
    return np.tanh(drive * x) / np.tanh(drive)


def soft_clip_asym(x, drive=2.0, bias=0.2):
    y = np.tanh(drive * (x + bias)) - np.tanh(drive * bias)
    return y / max(peak(y), 1e-9) * peak(x)


def vardelay(x, delay_samples):
    """Read x at (n - delay[n]) with linear interpolation (tape wow / flutter / doppler)."""
    n = len(x)
    idx = np.arange(n) - delay_samples
    if x.ndim == 1:
        return np.interp(idx, np.arange(n), x, left=0.0, right=0.0)
    return np.stack([np.interp(idx, np.arange(n), x[:, c], left=0.0, right=0.0)
                     for c in range(x.shape[1])], axis=1)


def tape(x, rng, wow_ms=1.2, wow_hz=0.55, flutter_ms=0.12, flutter_hz=6.3, drift_ms=1.5):
    n = len(x)
    t = taxis(n)
    d = (wow_ms * np.sin(2 * np.pi * wow_hz * t + rng.uniform(0, 6.28))
         + flutter_ms * np.sin(2 * np.pi * flutter_hz * t + rng.uniform(0, 6.28))
         + drift_ms * smooth_random(n, 0.25, rng))
    d = (d + wow_ms + drift_ms + flutter_ms + 1.0) * SR / 1000.0
    return vardelay(x, d)


def resample_ratio(x, ratio):
    """Change speed by ratio (>1 = faster/higher) via interpolation."""
    n_out = int(len(x) / ratio)
    return np.interp(np.arange(n_out) * ratio, np.arange(len(x)), x)


# ----------------------------------------------------------------------------- loudness
def _k_weight_coeffs():
    # BS.1770 pre-filter (RBJ high shelf) + RLB high-pass, designed for SR.
    G, Q, fc = 4.0, 1 / np.sqrt(2), 1500.0
    A = 10 ** (G / 40)
    w0 = 2 * np.pi * fc / SR
    alpha = np.sin(w0) / (2 * Q)
    c = np.cos(w0)
    b1 = [A * ((A + 1) + (A - 1) * c + 2 * np.sqrt(A) * alpha), -2 * A * ((A - 1) + (A + 1) * c),
          A * ((A + 1) + (A - 1) * c - 2 * np.sqrt(A) * alpha)]
    a1 = [(A + 1) - (A - 1) * c + 2 * np.sqrt(A) * alpha, 2 * ((A - 1) - (A + 1) * c),
          (A + 1) - (A - 1) * c - 2 * np.sqrt(A) * alpha]
    fc, Q = 38.0, 0.5
    w0 = 2 * np.pi * fc / SR
    alpha = np.sin(w0) / (2 * Q)
    c = np.cos(w0)
    b2 = [(1 + c) / 2, -(1 + c), (1 + c) / 2]
    a2 = [1 + alpha, -2 * c, 1 - alpha]
    return (np.array(b1) / a1[0], np.array(a1) / a1[0]), (np.array(b2) / a2[0], np.array(a2) / a2[0])


_KW = _k_weight_coeffs()


def lufs(x) -> float:
    """Gated integrated loudness (ITU-R BS.1770-4), mono or stereo."""
    x = np.asarray(x, float)
    if x.ndim == 1:
        x = x[:, None]
    (b1, a1), (b2, a2) = _KW
    y = signal.lfilter(b2, a2, signal.lfilter(b1, a1, x, axis=0), axis=0)
    blk, hop = ns(0.4), ns(0.1)
    if len(y) < blk:
        z = np.mean(y ** 2, axis=0, keepdims=True)
    else:
        starts = np.arange(0, len(y) - blk + 1, hop)
        cs = np.concatenate([np.zeros((1, y.shape[1])), np.cumsum(y ** 2, axis=0)])
        z = (cs[starts + blk] - cs[starts]) / blk
    lk = -0.691 + 10 * np.log10(np.maximum(z.sum(axis=1), 1e-20))
    keep = lk > -70
    if not np.any(keep):
        return -70.0
    zr = z[keep]
    rel = -0.691 + 10 * np.log10(zr.sum(axis=1).mean()) - 10
    keep2 = lk > rel
    keep2 &= keep
    zz = z[keep2] if np.any(keep2) else zr
    return float(-0.691 + 10 * np.log10(zz.sum(axis=1).mean()))


def limiter(x, ceiling_db=-1.5, lookahead=0.005, release=0.08):
    """Simple look-ahead peak limiter + final soft safety clip."""
    c = db(ceiling_db)
    a = np.abs(x) if x.ndim == 1 else np.max(np.abs(x), axis=1)
    if a.max() <= c:
        return x
    g_raw = np.minimum(1.0, c / np.maximum(a, 1e-12))
    w = max(1, ns(lookahead))
    g_min = minimum_filter1d(g_raw, size=2 * w + 1)
    k = np.exp(-1 / (release * SR))
    # smooth with a one-pole in both directions, then re-take min to guarantee <= ceiling
    gs = signal.filtfilt([1 - k], [1, -k], g_min)
    g = np.minimum(np.minimum(gs, 1.0), g_raw)
    y = x * (g if x.ndim == 1 else g[:, None])
    return np.clip(y, -c, c)


def normalize_peak(x, peak_db):
    p = peak(x)
    return x * (db(peak_db) / p) if p > 0 else x


def normalize_lufs(x, target, ceiling_db=-1.5):
    cur = lufs(x)
    y = x * db(target - cur)
    return limiter(y, ceiling_db)


# ----------------------------------------------------------------------------- impulse responses
def make_ir(rng, rt_bands, er=(), predelay=0.0, length=None, onset=0.008, stereo=False, density_hz=None):
    """Synthetic room IR.

    rt_bands: list of (lo_hz, hi_hz, rt60_s) — diffuse tail built per band.
    er: list of (delay_s, gain, lowpass_hz or None) discrete early reflections.
    Returns IR (direct sound NOT included), late part normalised to unit energy, ERs added on top.
    """
    rt_max = max(r[2] for r in rt_bands)
    if length is None:
        length = predelay + rt_max * 1.05 + 0.05
    n = ns(length)
    chans = 2 if stereo else 1
    out = np.zeros((n, chans))
    t = taxis(n)
    for ch in range(chans):
        late = np.zeros(n)
        for lo, hi, rt in rt_bands:
            nz = rng.standard_normal(n)
            if density_hz:  # sparse-ish (grainy) tail
                nz *= (rng.random(n) < density_hz / SR) * 6
            if lo <= 20:
                band = lp(nz, hi, 4)
            elif hi >= SR / 2 * 0.9:
                band = hp(nz, lo, 4)
            else:
                band = bp(nz, lo, hi, 3)
            late += band * np.exp(-6.9078 * t / rt)
        pd = ns(predelay)
        late = np.concatenate([np.zeros(pd), late])[:n]
        ramp = np.clip((t - predelay) / max(onset, 1e-3), 0, 1)
        late *= ramp ** 2
        late /= np.sqrt(np.sum(late ** 2)) + 1e-12
        for d, gain, lpf in er:
            k = ns(d)
            if k >= n:
                continue
            imp = np.zeros(min(n - k, ns(0.004)) or 1)
            imp[0] = 1.0
            if lpf:
                imp = lp(imp, lpf, 2)
            sgn = 1 if ch == 0 else (1 if rng.random() > 0.3 else -1)
            out[k:k + len(imp), ch] += sgn * gain * imp
        out[:, ch] += late
    return out if stereo else out[:, 0]


# profile -> (rt bands, early reflections builder, dry gain, wet gain, predelay)
def profile_ir(profile: str, rng):
    if profile == "carpet":
        ir = make_ir(rng, [(0, 400, 0.38), (400, 2500, 0.28), (2500, SR / 2, 0.12)],
                     er=[(0.004, 0.25, 2500), (0.009, 0.15, 2000)], predelay=0.003)
        return ir, 1.0, 0.22
    if profile == "office":
        ir = make_ir(rng, [(0, 300, 0.7), (300, 3000, 0.6), (3000, SR / 2, 0.35)],
                     er=[(0.006, 0.3, 6000), (0.011, 0.22, 5000), (0.017, 0.18, 4000), (0.026, 0.12, 4000)],
                     predelay=0.008)
        return ir, 1.0, 0.38
    if profile == "concrete":
        er = [(d, g, None) for d, g in zip(sorted(rng.uniform(0.008, 0.075, 9)), rng.uniform(0.25, 0.5, 9))]
        ir = make_ir(rng, [(0, 250, 2.0), (250, 4000, 1.8), (4000, SR / 2, 1.3)], er=er, predelay=0.012)
        return ir, 0.9, 0.62
    if profile == "pool":
        ir = make_ir(rng, [(0, 300, 3.7), (300, 2500, 3.5), (2500, 6000, 2.4), (6000, SR / 2, 1.4)],
                     er=[(0.018, 0.12, 3000), (0.031, 0.1, 3000)], predelay=0.025, onset=0.06)
        return ir, 0.75, 0.8
    if profile == "corridor":
        T = 0.0215 + rng.uniform(-0.002, 0.002)
        er = [(T * k, 0.55 * 0.78 ** k, 7000 - 300 * k) for k in range(1, 18)]
        er += [(T * 1.5 * k, 0.2 * 0.75 ** k, 5000) for k in range(1, 8)]
        ir = make_ir(rng, [(0, 400, 1.0), (400, 3000, 0.9), (3000, SR / 2, 0.55)], er=er, predelay=0.01)
        return ir, 1.0, 0.45
    if profile == "open":
        ir = make_ir(rng, [(0, 500, 0.5), (500, SR / 2, 0.3)],
                     er=[(0.17 + rng.uniform(-0.02, 0.02), 0.14, 2500), (0.33, 0.05, 1500)],
                     predelay=0.02)
        return ir, 1.0, 0.06
    raise ValueError(profile)


_IR_CACHE: dict = {}


def get_profile_ir(profile):
    if profile not in _IR_CACHE:
        _IR_CACHE[profile] = profile_ir(profile, rng_for("ir", profile))
    return _IR_CACHE[profile]


def apply_ir(x, ir, dry=1.0, wet=0.4):
    y = signal.fftconvolve(x, ir)
    out = np.zeros(len(y))
    out[: len(x)] += dry * x
    out += wet * y
    return out


def apply_profile(x, profile):
    ir, dry, wet = get_profile_ir(profile)
    return apply_ir(x, ir, dry, wet)


def muffle(x):
    """'Behind a wall': 4th-order LP ~600 Hz, -6 dB, a little extra (dark) reverb."""
    ir = _IR_CACHE.get("_muffle")
    if ir is None:
        ir = make_ir(rng_for("ir", "muffle"), [(0, 300, 0.9), (300, SR / 2, 0.5)],
                     er=[(0.007, 0.3, 1500), (0.015, 0.2, 1200)], predelay=0.005)
        _IR_CACHE["_muffle"] = ir
    y = lp(x, 600, 4) * db(-6)
    return apply_ir(y, lp(ir, 900, 2), 1.0, 0.3)


def trim_tail(x, rel_db=-65.0, min_len=0.05, fade=0.03):
    """Trim trailing samples quieter than rel_db below peak and fade out."""
    p = peak(x)
    if p == 0:
        return x
    a = np.abs(x) if x.ndim == 1 else np.max(np.abs(x), axis=1)
    idx = np.nonzero(a > p * db(rel_db))[0]
    end = max(ns(min_len), (idx[-1] + 1) if len(idx) else len(x))
    end = min(len(x), end + ns(fade))
    return fade_edges(x[:end], 0.0, fade)


# ----------------------------------------------------------------------------- loops
def loop_samples(L):
    n = ns(L)
    f = ns(FADE)
    return n, n - f, f


def qf(freq, P):
    """Quantise frequency so an integer number of cycles fits in P samples (periodic loops)."""
    c = max(1, round(freq * P / SR))
    return c * SR / P


def tile(period, n):
    reps = int(np.ceil(n / len(period)))
    return np.tile(period, reps)[:n]


def finish_loop(tonal_period, noisy, L, target_lufs, ceiling_db=-3.0):
    """Assemble a loop of exactly L seconds.

    tonal_period: periodic component of length P (= L - FADE) or None; tiled so the last FADE
                  seconds equal the first FADE seconds, and faded with sin^2/cos^2 (sum-to-one)
                  curves -> a 300 ms overlap of two instances reconstructs it exactly.
    noisy:        non-periodic (noise) component of length L or None; faded with equal-power
                  sin/cos curves -> constant power across an overlap of uncorrelated material.
    """
    n, P, F = loop_samples(L)
    ton = tile(tonal_period, n) if tonal_period is not None else np.zeros(n)
    noi = pad_to(noisy, n) if noisy is not None else np.zeros(n)
    g = db(target_lufs - lufs(ton + noi))
    ton, noi = ton * g, noi * g
    # peak control (gain only — a limiter would break periodicity of the tonal part)
    pk = peak(ton + noi)
    if pk > db(ceiling_db):
        s = db(ceiling_db) / pk
        ton, noi = ton * s, noi * s
    u = np.linspace(0, 1, F, endpoint=False) + 0.5 / F
    ton[:F] *= np.sin(u * np.pi / 2) ** 2
    ton[-F:] *= np.cos(u * np.pi / 2) ** 2
    noi[:F] *= np.sin(u * np.pi / 2)
    noi[-F:] *= np.cos(u * np.pi / 2)
    out = ton + noi
    out[0] = 0.0
    out[-1] = 0.0
    return out


def finish_oneshot(x, peak_db=-3.0, fin=0.001, rel_db=-65):
    x = trim_tail(np.asarray(x, float), rel_db=rel_db)
    x = fade_edges(x, fin, 0.01)
    return normalize_peak(x, peak_db)


def cap_peak(x, peak_db):
    p = peak(x)
    if p > db(peak_db):
        return x * (db(peak_db) / p)
    return x


def periodic_smooth(n, P, rate_hz, rng, lo=-1.0, hi=1.0):
    """Smooth random control curve that is exactly periodic with period P samples (for loop beds:
    the level at the loop junction matches, so overlapping instances do not jump)."""
    pd = P / SR
    K = max(2, int(round(rate_hz * pd)))
    t = taxis(n)
    y = np.zeros(n)
    for k in range(1, K + 1):
        y += rng.normal() / np.sqrt(k) * np.sin(2 * np.pi * k * t / pd + rng.uniform(0, 2 * np.pi))
    m = np.max(np.abs(y[:P])) or 1.0
    y = y / m
    return lo + (y + 1) * 0.5 * (hi - lo)
