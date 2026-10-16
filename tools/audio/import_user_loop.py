#!/usr/bin/env python3
"""Turn a long user-supplied recording into a seamless, plugin-ready ambience loop.

    python3 tools/audio/import_user_loop.py SOURCE.ogg \
        --id bed.level0 --out user/bed_level0.ogg --length 60 --lufs -26 \
        --license "..." --method "..."

Steps (deterministic for a given source file and toolchain):
  1. decode with ffmpeg, downmix to mono (channel mean), resample to 48 kHz (scipy polyphase);
  2. zero-phase 4th-order high-pass (default 50 Hz) to drop infrasonic rumble / handling noise;
  3. pick the steadiest stretch of length (loop period + crossfade): candidates every 0.5 s, scored by the
     spread of the 0.5 s RMS (dB), loud 0.5 s blocks and short (10 ms) transients against the local median;
     `--start S` overrides the automatic choice;
  4. build one loop period P = length - 0.3 s whose end flows into its start: the first `--xfade` seconds are
     an equal-power crossfade from the material just after the period into the period's own start;
  5. tile to exactly `--length` s and apply the 300 ms sin^2/cos^2 fades of tools/audio/dsp.finish_loop, so the
     plugin's 300 ms re-trigger overlap reconstructs the signal exactly;
  6. gain-normalise to `--lufs` (BS.1770, dsp.lufs; gain only, so the loop stays periodic), keep peaks below
     -3 dBFS, encode Ogg Vorbis (`-q:a 2`, bit-exact flags) at 48 kHz mono into
     pack/java/assets/backrooms/sounds/<out>;
  7. with `--id`, add/replace the entry in pack/user_sounds.json (category ambient, loop, non-positional, mono).

The source file itself is not part of the repository. Re-run this script, then tools/audio/build_pack.py.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import dsp  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SOUNDS = os.path.join(ROOT, "pack", "java", "assets", "backrooms", "sounds")
USER_SOUNDS = os.path.join(ROOT, "pack", "user_sounds.json")
FFMPEG = os.environ.get("FFMPEG", "ffmpeg")
SR = 48000


def decode_mono(path: str) -> tuple[np.ndarray, int]:
    info = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries",
                           "stream=sample_rate,channels", "-of", "json", path],
                          capture_output=True, text=True, check=True)
    st = json.loads(info.stdout)["streams"][0]
    sr, ch = int(st["sample_rate"]), int(st["channels"])
    raw = subprocess.run([FFMPEG, "-v", "error", "-i", path, "-f", "f32le", "-acodec", "pcm_f32le", "-"],
                         capture_output=True, check=True).stdout
    x = np.frombuffer(raw, dtype=np.float32)
    x = x[: len(x) // ch * ch].reshape(-1, ch).mean(axis=1, dtype=np.float64)
    return x, sr


def blocks_db(x: np.ndarray, w: int) -> np.ndarray:
    k = len(x) // w
    r = np.sqrt(np.mean(x[: k * w].reshape(k, w) ** 2, axis=1))
    return 20 * np.log10(np.maximum(r, 1e-12))


def choose_start(y: np.ndarray, W: int, skip: float) -> tuple[float, dict]:
    half = SR // 2
    b = blocks_db(y, half)                       # 0.5 s RMS
    s10 = blocks_db(y, SR // 100)                # 10 ms RMS
    med10 = signal.medfilt(s10, 101)             # ~1 s running median
    trans = s10 - med10                          # transient excess (dB)
    nb = W // half
    best = None
    for i in range(int(skip * 2), len(b) - nb):
        seg = b[i:i + nb]
        med = np.median(seg)
        t = trans[i * 50:(i + nb) * 50]
        score = (np.std(seg) + 1.5 * max(0.0, np.max(seg) - med - 1.5)
                 + 0.5 * max(0.0, np.max(t) - 6.0) + 0.2 * np.mean(np.clip(t - 3.0, 0, None) > 0) * 100)
        if best is None or score < best[0]:
            best = (score, i, {"rms_std_db": float(np.std(seg)), "loudest_block_over_median_db": float(np.max(seg) - med),
                               "max_10ms_transient_db": float(np.max(t)), "median_rms_dbfs": float(med)})
    return best[1] * 0.5, best[2]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("source")
    ap.add_argument("--out", default="user/bed_level0.ogg", help="path under sounds/ (default user/bed_level0.ogg)")
    ap.add_argument("--length", type=float, default=60.0, help="file length in s, including the 0.3 s overlap")
    ap.add_argument("--xfade", type=float, default=4.0, help="internal end->start crossfade (s)")
    ap.add_argument("--lufs", type=float, default=-26.0)
    ap.add_argument("--hp", type=float, default=50.0, help="high-pass corner (Hz), 0 = off")
    ap.add_argument("--skip", type=float, default=5.0, help="ignore the first S seconds of the source")
    ap.add_argument("--start", type=float, help="use this source offset (s) instead of the automatic choice")
    ap.add_argument("--quality", type=int, default=2)
    ap.add_argument("--id", help="also register this sound id in pack/user_sounds.json")
    ap.add_argument("--license", default="UNSPECIFIED")
    ap.add_argument("--method", default="")
    a = ap.parse_args()

    x, sr0 = decode_mono(a.source)
    g = math.gcd(SR, sr0)
    y = signal.resample_poly(x, SR // g, sr0 // g) if sr0 != SR else x
    if a.hp > 0:
        y = signal.sosfiltfilt(signal.butter(4, a.hp, "highpass", fs=SR, output="sos"), y)
    n = int(round(a.length * SR))
    F = int(round(dsp.FADE * SR))
    P = n - F
    X = int(round(a.xfade * SR))
    W = P + X
    if W > len(y):
        sys.exit(f"source too short: need {W / SR:.1f} s, have {len(y) / SR:.1f} s")
    if a.start is None:
        start, stats = choose_start(y, W, a.skip)
    else:
        start, stats = a.start, {}
    s0 = int(round(start * SR))
    seg = y[s0:s0 + W]
    if len(seg) < W:
        sys.exit("--start too late for the requested length")

    # one period whose end flows into its start
    u = (np.arange(X) + 0.5) / X
    per = seg[:P].copy()
    per[:X] = seg[:X] * np.sin(u * np.pi / 2) + seg[P:P + X] * np.cos(u * np.pi / 2)
    out = np.tile(per, int(np.ceil(n / P)))[:n]
    uf = (np.arange(F) + 0.5) / F
    out[:F] *= np.sin(uf * np.pi / 2) ** 2
    out[-F:] *= np.cos(uf * np.pi / 2) ** 2
    out[0] = out[-1] = 0.0

    g2 = math.gcd(SR, dsp.SR)
    cur = dsp.lufs(signal.resample_poly(out, dsp.SR // g2, SR // g2))
    out *= dsp.db(a.lufs - cur)
    pk = dsp.peak(out)
    if pk > dsp.db(-3.0):
        out *= dsp.db(-3.0) / pk

    dst = os.path.join(SOUNDS, a.out)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="backrooms-import-") as tmp:
        wav = os.path.join(tmp, "loop.wav")
        wavfile.write(wav, SR, out.astype(np.float32))
        subprocess.run([FFMPEG, "-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-i", wav,
                        "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", str(a.quality),
                        "-fflags", "+bitexact", "-flags:a", "+bitexact", dst], check=True)

    print(f"source {a.source}: {len(x) / sr0:.1f} s @ {sr0} Hz")
    print(f"stretch {start:.1f}-{start + W / SR:.1f} s  (period {P / SR:.2f} s + {a.xfade:.1f} s crossfade)  {stats}")
    print(f"wrote {dst}: {n / SR:.2f} s, 48 kHz mono, {a.lufs} LUFS target, peak {dsp.to_db(dsp.peak(out)):.1f} dBFS, "
          f"{os.path.getsize(dst) / 1e3:.0f} kB")

    if a.id:
        data = {}
        if os.path.exists(USER_SOUNDS):
            with open(USER_SOUNDS, encoding="utf-8") as f:
                data = json.load(f)
        method = a.method or "Imported with tools/audio/import_user_loop.py"
        method += (f" Source stretch {start:.1f}-{start + W / SR:.1f} s, {a.hp:g} Hz high-pass, {P / SR:.2f} s "
                   f"period with {a.xfade:g} s internal crossfade, 0.3 s loop fades, {a.lufs:g} LUFS, Vorbis q{a.quality}.")
        data[a.id] = {"files": [a.out], "category": "ambient", "positional": False, "stereo": False, "loop": True,
                      "length": a.length, "level": a.lufs, "license": a.license, "method": method}
        with open(USER_SOUNDS, "w", encoding="utf-8", newline="\n") as f:
            json.dump(dict(sorted(data.items())), f, indent=2, ensure_ascii=False)
            f.write("\n")
        print(f"registered {a.id} in {os.path.relpath(USER_SOUNDS, ROOT)}")


if __name__ == "__main__":
    main()
