#!/usr/bin/env python3
"""Synthesise every sound in tools/audio/catalog.py (contract: tools/audio/BRIEF.md).

    python3 tools/audio/generate.py              # everything
    python3 tools/audio/generate.py --only hum.fluorescent,music.pool_1
    python3 tools/audio/generate.py --keep-wav DIR   # also keep the float WAV masters

Writes Ogg Vorbis files to pack/java/assets/backrooms/sounds/<id path>/<event stem>_<n>.ogg.
Deterministic: all randomness is seeded from the sound id + variant index; ffmpeg runs bit-exact.
"""
from __future__ import annotations

import argparse
import importlib
import math
import os
import shutil
import subprocess
import sys
import tempfile
import time
from concurrent.futures import ProcessPoolExecutor, as_completed

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from scipy import signal  # noqa: E402
from scipy.io import wavfile  # noqa: E402

import catalog  # noqa: E402
import dsp  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "pack", "java", "assets", "backrooms", "sounds")
FFMPEG = os.environ.get("FFMPEG", "ffmpeg")


def to_rate(y: np.ndarray, sr: int) -> np.ndarray:
    """Resample from dsp.SR to sr (polyphase, deterministic). Loops keep their exact length in seconds
    (e.g. 40 s: 1 764 000 -> 1 920 000 samples) and their silent end samples."""
    if sr == dsp.SR:
        return y
    g = math.gcd(sr, dsp.SR)
    z = signal.resample_poly(y, sr // g, dsp.SR // g, axis=0)
    if np.all(y[0] == 0):
        z[0] = 0.0
    if np.all(y[-1] == 0):
        z[-1] = 0.0
    return z


def encode(y: np.ndarray, rel: str, quality: int, tmpdir: str, keep_wav: str | None, sr: int = dsp.SR):
    wav = os.path.join(tmpdir, rel.replace("/", "__") + ".wav")
    wavfile.write(wav, sr, np.asarray(to_rate(y, sr), dtype=np.float32))
    dst = os.path.join(OUT, rel + ".ogg")
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    subprocess.run([FFMPEG, "-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-i", wav,
                    "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", str(quality),
                    "-fflags", "+bitexact", "-flags:a", "+bitexact", dst], check=True)
    if keep_wav:
        d = os.path.join(keep_wav, rel + ".wav")
        os.makedirs(os.path.dirname(d), exist_ok=True)
        shutil.move(wav, d)
    else:
        os.remove(wav)
    return dst


def _fn(s):
    mod, name = s.gen.split(".")
    return getattr(importlib.import_module(mod), name)


def render(s: catalog.Sound):
    """Return {event_stem: [variant arrays]} for sound s (+ derived ids for music)."""
    out = {}
    fn = _fn(s)
    if s.category == "music":
        st = fn(dsp.rng_for(s.id))
        mono = dsp.normalize_lufs(st.mean(axis=1), s.level, -1.5)
        out[(s.id, "dry")] = [mono]
        for p in s.profiles:
            y = dsp.trim_tail(dsp.apply_profile(mono, p), -70)
            out[(s.id, p)] = [dsp.normalize_lufs(y, s.level, -1.5)]
        if s.muffled:
            y = dsp.trim_tail(dsp.muffle(mono), -70)
            out[(s.id, "muffled")] = [dsp.limiter(y, -1.5)]
        for d in catalog.SOUNDS:
            if d.source == s.id:
                out[(d.id, "dry")] = [dsp.normalize_lufs(st, d.level, -1.5)]
        return out
    dry = []
    for v in range(s.variants):
        rng = dsp.rng_for(s.id, v)
        if s.loop:
            n, P, F = dsp.loop_samples(s.length)
            ton, noi = fn(rng, v, n, P)
            dry.append(dsp.finish_loop(ton, noi, s.length, s.level))
        else:
            dry.append(dsp.finish_oneshot(fn(rng, v), s.level))
    out[(s.id, "dry")] = dry
    for p in s.profiles:
        out[(s.id, p)] = [dsp.cap_peak(dsp.trim_tail(dsp.apply_profile(x, p), -55), s.level) for x in dry]
        if s.muffled:
            out[(s.id, p + "_muffled")] = [dsp.trim_tail(dsp.muffle(x), -55) for x in out[(s.id, p)]]
    if s.muffled and not s.profiles:
        out[(s.id, "muffled")] = [dsp.trim_tail(dsp.muffle(x), -60) for x in dry]
    return out


def job(sid: str, tmpdir: str, keep_wav):
    t0 = time.time()
    s = catalog.BY_ID[sid]
    res = render(s)
    report = []
    for (eid, stem), arrs in res.items():
        es = catalog.BY_ID[eid]
        for i, y in enumerate(arrs):
            rel = f"{es.dir()}/{stem}_{i + 1}"
            if not es.stereo and y.ndim == 2:
                raise RuntimeError(f"{rel}: positional/mono sound rendered stereo")
            encode(y, rel, es.quality, tmpdir, keep_wav, es.sr)
            report.append((rel, len(y) / dsp.SR, dsp.to_db(dsp.peak(y))))
    return sid, time.time() - t0, report


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="comma separated ids to (re)generate")
    ap.add_argument("--jobs", type=int, default=os.cpu_count() or 2)
    ap.add_argument("--keep-wav", help="directory to keep float WAV masters in")
    a = ap.parse_args()
    ids = [s.id for s in catalog.SOUNDS if s.source is None]
    if a.only:
        want = set(a.only.split(","))
        unknown = want - set(catalog.BY_ID)
        if unknown:
            sys.exit(f"unknown ids: {sorted(unknown)}")
        want = {catalog.BY_ID[w].source or w for w in want}
        ids = [i for i in ids if i in want]
        for i in ids:
            for s in catalog.SOUNDS:
                if s.id == i or s.source == i:
                    shutil.rmtree(os.path.join(OUT, s.dir()), ignore_errors=True)
    else:
        # wipe generated output, but never user-supplied tracks in sounds/user/
        if os.path.isdir(OUT):
            for e in os.listdir(OUT):
                if e != "user":
                    p = os.path.join(OUT, e)
                    shutil.rmtree(p) if os.path.isdir(p) else os.remove(p)
    os.makedirs(OUT, exist_ok=True)
    # heaviest first
    ids.sort(key=lambda i: (catalog.BY_ID[i].category != "music", catalog.BY_ID[i].category != "voice"))
    t0 = time.time()
    failed = []
    with tempfile.TemporaryDirectory(prefix="backrooms-audio-") as tmp:
        with ProcessPoolExecutor(max_workers=a.jobs) as ex:
            futs = {ex.submit(job, i, tmp, a.keep_wav): i for i in ids}
            for f in as_completed(futs):
                try:
                    sid, dt, rep = f.result()
                except Exception as e:  # noqa: BLE001
                    failed.append(futs[f])
                    print(f"FAILED {futs[f]}: {e!r}", flush=True)
                    continue
                durs = sorted({round(d, 2) for _, d, _ in rep})
                print(f"{sid:28s} {len(rep):4d} files  {dt:6.1f}s  dur {durs[0]:.2f}-{durs[-1]:.2f}s  "
                      f"peak<={max(p for *_, p in rep):.1f} dBFS", flush=True)
    print(f"done in {time.time() - t0:.0f}s")
    if failed:
        sys.exit(f"{len(failed)} sounds failed: {failed}")


if __name__ == "__main__":
    main()
