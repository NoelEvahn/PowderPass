#!/usr/bin/env python3
"""Validate generated audio and build the Java + Bedrock resource packs and the plugin index.

    python3 tools/audio/build_pack.py

Fails (exit 1) on: a sounds.json reference without a file, an .ogg that is not referenced, a
non-Vorbis / unreadable OGG (ffprobe), a positional sound that is not mono, clipping (decoded peak
> -0.5 dBFS), a sample rate other than 44100/48000 -- plus sanity checks: silent files, unexpected
durations, loops that are not faded at both ends or that do not overlap smoothly.

Outputs:
  pack/java/assets/backrooms/sounds.json, pack/java/pack.mcmeta, pack/java/pack.png
  pack/bedrock/{manifest.json,pack_icon.png,sounds/sound_definitions.json,sounds/backrooms/...ogg}
  src/main/resources/{backrooms-pack.zip,bedrock-skeleton.zip,pack-index.json}
  pack/MANIFEST.md (asset table between the BEGIN/END markers is regenerated)
"""
from __future__ import annotations

import hashlib
import json
import math
import os
import shutil
import struct
import subprocess
import sys
import zipfile
import zlib
from concurrent.futures import ThreadPoolExecutor

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import catalog  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
JAVA = os.path.join(ROOT, "pack", "java")
BEDROCK = os.path.join(ROOT, "pack", "bedrock")
SOUNDS = os.path.join(JAVA, "assets", "backrooms", "sounds")
RES = os.path.join(ROOT, "src", "main", "resources")
MANIFEST = os.path.join(ROOT, "pack", "MANIFEST.md")
USER_SOUNDS = os.path.join(ROOT, "pack", "user_sounds.json")
FFPROBE = os.environ.get("FFPROBE", "ffprobe")
FFMPEG = os.environ.get("FFMPEG", "ffmpeg")

BEDROCK_HEADER_UUID = "13076765-d044-5b76-8142-71ec26fa3369"
BEDROCK_MODULE_UUID = "1b31ed73-dee1-58af-aae5-eb4b43233f18"
FIXED_TIME = (1980, 1, 1, 0, 0, 0)
FADE = 0.3
TICK = 20


def dbfs(x):
    return 20 * math.log10(max(x, 1e-12))


# ------------------------------------------------------------------------------------- probing
def probe(path):
    r = subprocess.run([FFPROBE, "-v", "error", "-show_entries",
                        "stream=codec_name,codec_type,sample_rate,channels:format=format_name",
                        "-of", "json", path], capture_output=True, text=True)
    if r.returncode != 0:
        return {"error": r.stderr.strip() or "ffprobe failed"}
    info = json.loads(r.stdout)
    st = [s for s in info.get("streams", []) if s.get("codec_type") == "audio"]
    if len(st) != 1:
        return {"error": f"expected 1 audio stream, got {len(st)}"}
    s = st[0]
    out = {"codec": s.get("codec_name"), "sr": int(s.get("sample_rate", 0)), "ch": int(s.get("channels", 0)),
           "format": info.get("format", {}).get("format_name")}
    d = subprocess.run([FFMPEG, "-v", "error", "-i", path, "-f", "f32le", "-acodec", "pcm_f32le", "-"],
                       capture_output=True)
    if d.returncode != 0:
        out["error"] = "decode failed: " + d.stderr.decode(errors="replace")[:200]
        return out
    x = np.frombuffer(d.stdout, dtype=np.float32).astype(np.float64)
    ch = max(1, out["ch"])
    x = x[: len(x) // ch * ch].reshape(-1, ch)
    out["x"] = x
    out["dur"] = len(x) / max(out["sr"], 1)
    out["peak"] = float(np.max(np.abs(x))) if len(x) else 0.0
    out["rms"] = float(np.sqrt(np.mean(x ** 2))) if len(x) else 0.0
    return out


def loop_checks(x, sr, errs, name):
    m = x.mean(axis=1)
    F = int(round(FADE * sr))
    body = m[F:-F]
    br = np.sqrt(np.mean(body ** 2))
    head = np.sqrt(np.mean(m[: int(0.01 * sr)] ** 2))
    tail = np.sqrt(np.mean(m[-int(0.01 * sr):] ** 2))
    if head > 0.1 * br or tail > 0.1 * br or abs(m[0]) > 0.01 or abs(m[-1]) > 0.01:
        errs.append(f"{name}: loop not faded at both ends (head {dbfs(head / br):.1f} dB, tail {dbfs(tail / br):.1f} dB)")
    # overlap simulation: tail of one instance + head of the next (same take)
    ov = m[-F:] + m[:F]
    w = int(0.1 * sr)
    seg = [np.sqrt(np.mean(ov[i:i + w] ** 2)) for i in range(0, F - w + 1, w // 2)]
    # compare with the range of the material in the second before (end of instance A) and the second
    # after (start of instance B), measured with the same window
    near = np.concatenate([m[-F - sr:-F], m[F:F + sr]])
    nr = [np.sqrt(np.mean(near[i:i + w] ** 2)) for i in range(0, len(near) - w, w // 2)]
    lo, hi = min(nr), max(nr)
    dev = max(max(0.0, dbfs(max(seg) / hi)), max(0.0, dbfs(lo / max(min(seg), 1e-12))))
    if dev > 2.0:
        errs.append(f"{name}: overlap level deviates {dev:.1f} dB from the adjacent material")
    return dev


def lufs(x, sr):
    sys.path.insert(0, HERE)
    import dsp
    if sr != dsp.SR:  # the K-weighting filters are designed for dsp.SR
        from scipy import signal
        g = math.gcd(sr, dsp.SR)
        x = signal.resample_poly(x, dsp.SR // g, sr // g, axis=0)
    return dsp.lufs(x if x.shape[1] > 1 else x[:, 0])


# ------------------------------------------------------------------------------------- outputs
def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, sort_keys=False, ensure_ascii=False)
        f.write("\n")


def write_png(path, img):
    h, w, _ = img.shape
    raw = b"".join(b"\x00" + img[y].astype(np.uint8).tobytes() for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(data)


def make_icon():
    """64x64 'mono-yellow wallpaper under a fluorescent panel' icon (pure numpy)."""
    rng = np.random.default_rng(1980)
    img = np.zeros((64, 64, 3))
    wall = np.array([196, 178, 96])
    for y in range(64):
        for x in range(64):
            c = wall.copy()
            if x % 8 in (0, 1):
                c = c * 0.93
            if (x + y) % 16 == 0 or (x - y) % 16 == 0:
                c = c * 0.96
            img[y, x] = c
    img[:14] = [224, 220, 196]
    img[3:11, 12:52] = [255, 255, 238]
    img[2, 12:52] = img[11, 12:52] = [200, 198, 180]
    for y in range(14, 30):
        img[y] = img[y] * (1 + 0.12 * (30 - y) / 16)
    img[54:] = [126, 104, 62]
    img[54] = [96, 80, 48]
    # faint sound-wave glyph
    for x in range(14, 50):
        yy = 34 + 6 * math.sin((x - 14) / 36 * 2 * math.pi * 1.5) * math.sin((x - 14) / 36 * math.pi)
        img[int(round(yy)), x] = [110, 92, 50]
    img += rng.normal(0, 2.0, img.shape)
    return np.clip(img, 0, 255)


def zip_dir(src, dst):
    files = []
    for d, _, fs in os.walk(src):
        for f in fs:
            full = os.path.join(d, f)
            files.append((os.path.relpath(full, src).replace(os.sep, "/"), full))
    files.sort()
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    tmp = dst + ".tmp"
    with zipfile.ZipFile(tmp, "w") as z:
        for rel, full in files:
            zi = zipfile.ZipInfo(rel, date_time=FIXED_TIME)
            zi.create_system = 3
            zi.external_attr = 0o100644 << 16
            stored = rel.endswith((".ogg", ".png"))
            zi.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
            with open(full, "rb") as f:
                z.writestr(zi, f.read(), compresslevel=None if stored else 9)
    os.replace(tmp, dst)
    with open(dst, "rb") as f:
        data = f.read()
    return len(data), hashlib.sha1(data).hexdigest(), len(files)


def update_manifest(rows):
    begin, end = "<!-- BEGIN ASSET TABLE (generated by build_pack.py) -->", "<!-- END ASSET TABLE -->"
    if not os.path.exists(MANIFEST):
        print("WARNING: pack/MANIFEST.md missing; asset table not written")
        return
    with open(MANIFEST, encoding="utf-8") as f:
        text = f.read()
    if begin not in text or end not in text:
        raise SystemExit("pack/MANIFEST.md lacks the asset-table markers")
    table = ["| event id (namespace `backrooms:`) | files | category | how it was made | license |",
             "|---|---|---|---|---|"]
    table += rows
    pre, rest = text.split(begin, 1)
    _, post = rest.split(end, 1)
    with open(MANIFEST, "w", encoding="utf-8", newline="\n") as f:
        f.write(pre + begin + "\n" + "\n".join(table) + "\n" + end + post)


# ------------------------------------------------------------------------------------- main
def main():
    errs, warns = [], []
    sounds = list(catalog.SOUNDS) + catalog.load_user_sounds(USER_SOUNDS)
    expected = {}  # rel path (no ext) -> (sound, event, stem)
    events = {}    # event -> (sound, [rel])
    for s in sounds:
        for ev, stem in s.events():
            rels = s.files(stem)
            events[ev] = (s, rels)
            for r in rels:
                expected[r] = (s, ev, stem)
    present = set()
    for d, _, fs in os.walk(SOUNDS):
        for f in fs:
            rel = os.path.relpath(os.path.join(d, f), SOUNDS).replace(os.sep, "/")
            if not f.endswith(".ogg"):
                errs.append(f"unexpected non-ogg file in sounds/: {rel}")
                continue
            present.add(rel[:-4])
    for r in sorted(set(expected) - present):
        errs.append(f"sounds.json references missing file: backrooms:{r}")
    for r in sorted(present - set(expected)):
        errs.append(f"file not referenced by sounds.json: {r}.ogg")

    paths = sorted(set(expected) & present)
    with ThreadPoolExecutor(max_workers=os.cpu_count() or 4) as ex:
        infos = dict(zip(paths, ex.map(lambda r: probe(os.path.join(SOUNDS, r + ".ogg")), paths)))

    loop_dev = {}
    durations = {}
    for r in paths:
        s, ev, stem = expected[r]
        i = infos[r]
        if "error" in i:
            errs.append(f"{r}.ogg: unreadable: {i['error']}")
            continue
        if i["codec"] != "vorbis" or "ogg" not in (i["format"] or ""):
            errs.append(f"{r}.ogg: not Ogg Vorbis ({i['format']}/{i['codec']})")
        if i["sr"] not in (44100, 48000):
            errs.append(f"{r}.ogg: sample rate {i['sr']}")
        elif not s.external and i["sr"] != s.sr:
            errs.append(f"{r}.ogg: sample rate {i['sr']} != catalog {s.sr}")
        if s.external:
            if s.positional and i["ch"] != 1:
                errs.append(f"{r}.ogg: positional user sound must be mono, has {i['ch']} ch")
        elif s.stereo:
            if i["ch"] != 2:
                errs.append(f"{r}.ogg: ambient music expected stereo, has {i['ch']} ch")
        elif i["ch"] != 1:
            errs.append(f"{r}.ogg: {'positional' if s.positional else 'non-ambient'} sound must be mono, has {i['ch']} ch")
        if dbfs(i["peak"]) > -0.5:
            errs.append(f"{r}.ogg: clipping risk, decoded peak {dbfs(i['peak']):.2f} dBFS")
        if dbfs(i["rms"]) < -65:
            errs.append(f"{r}.ogg: (near-)silent, RMS {dbfs(i['rms']):.1f} dBFS")
        dur = i["dur"]
        durations[r] = dur
        if s.external:
            if s.loop:
                if s.length and abs(dur - s.length) > 0.05:
                    errs.append(f"{r}.ogg: user loop length {dur:.3f}s != declared {s.length}s")
                loop_dev[r] = loop_checks(i["x"], i["sr"], errs, r)
        elif s.loop:
            if abs(dur - s.length) > 0.02:
                errs.append(f"{r}.ogg: loop length {dur:.3f}s != {s.length}s")
            loop_dev[r] = loop_checks(i["x"], i["sr"], errs, r)
        elif s.category == "music":
            lo, hi = 60, 120 + (8 if stem != "dry" else 0)
            if not lo <= dur <= hi:
                errs.append(f"{r}.ogg: music duration {dur:.1f}s outside {lo}-{hi}s")
        else:
            if stem == "dry":
                lo, hi = 0.35 * s.length, 2.2 * s.length + 0.4
            else:
                lo, hi = 0.35 * s.length, 2.2 * s.length + 5.0
            if not lo <= dur <= hi:
                errs.append(f"{r}.ogg: duration {dur:.2f}s outside expected {lo:.2f}-{hi:.2f}s")
            # one-shots must start/end cleanly
            m = i["x"].mean(axis=1)
            if abs(m[-1]) > 0.01:
                errs.append(f"{r}.ogg: one-shot does not end at silence")
    if errs:
        print("VALIDATION FAILED:")
        for e in errs:
            print("  -", e)
        sys.exit(1)

    # ---------------------------------------------------------------- sounds.json
    sj = {}
    for ev in sorted(events):
        s, rels = events[ev]
        sj[ev] = {"sounds": [{"name": f"{catalog.NAMESPACE}:{r}", "stream": s.stream} if s.stream
                             else f"{catalog.NAMESPACE}:{r}" for r in rels]}
    write_json(os.path.join(JAVA, "assets", "backrooms", "sounds.json"), sj)
    write_json(os.path.join(JAVA, "pack.mcmeta"), {"pack": {
        "description": "Backrooms by NoelCicolini - ambience, blocks, camera",
        "pack_format": 75, "min_format": 69, "max_format": 75}})
    icon = make_icon()
    write_png(os.path.join(JAVA, "pack.png"), icon)

    # ---------------------------------------------------------------- bedrock
    # The Bedrock pack reuses the Java pack's Ogg files. Only its skeleton (manifest, icon, sound definitions)
    # is embedded in the plugin; the plugin assembles the full .mcpack from the Java pack on first start,
    # mapping assets/backrooms/sounds/<p>.ogg -> sounds/backrooms/<p>.ogg. That keeps the JAR at one copy.
    shutil.rmtree(os.path.join(BEDROCK, "sounds"), ignore_errors=True)
    os.makedirs(os.path.join(BEDROCK, "sounds"), exist_ok=True)
    defs = {}
    for ev in sorted(events):
        s, rels = events[ev]
        entry = {"category": "music" if s.category == "music" else "ambient", "sounds": []}
        for r in rels:
            snd = {"name": f"sounds/backrooms/{r}"}
            if s.stream:
                snd["stream"] = True
            if not s.positional:
                snd["is3D"] = False
            entry["sounds"].append(snd)
        defs[f"{catalog.NAMESPACE}:{ev}"] = entry
        defs[ev] = json.loads(json.dumps(entry))
    write_json(os.path.join(BEDROCK, "sounds", "sound_definitions.json"),
               {"format_version": "1.14.0", "sound_definitions": dict(sorted(defs.items()))})
    write_json(os.path.join(BEDROCK, "manifest.json"), {
        "format_version": 2,
        "header": {"name": "NoelCicolini Backrooms Sounds", "description": "Original CC0 Backrooms ambience",
                   "uuid": BEDROCK_HEADER_UUID, "version": [1, 0, 0], "min_engine_version": [1, 20, 0]},
        "modules": [{"type": "resources", "uuid": BEDROCK_MODULE_UUID, "version": [1, 0, 0]}]})
    write_png(os.path.join(BEDROCK, "pack_icon.png"), icon)

    # ---------------------------------------------------------------- index
    idx = {"version": 1, "sounds": {}}
    for s in sounds:
        ev_ticks = {}
        for ev, stem in s.events():
            ev_ticks[ev] = max(math.ceil(durations[r] * TICK - 1e-6) for r in s.files(stem))
        idx["sounds"][s.id] = {
            "category": s.category, "duration_ticks": ev_ticks[s.id], "loop": s.loop,
            "positional": s.positional, "variants": s.variants, "profiles": list(s.profiles),
            "muffled": s.muffled, "stream": s.stream, "event_ticks": ev_ticks}
    write_json(os.path.join(RES, "pack-index.json"), idx)

    jsize, jsha, jn = zip_dir(JAVA, os.path.join(RES, "backrooms-pack.zip"))
    bsize, bsha, bn = zip_dir(BEDROCK, os.path.join(RES, "bedrock-skeleton.zip"))
    old = os.path.join(RES, "backrooms-bedrock.mcpack")
    if os.path.exists(old):
        os.remove(old)

    # ---------------------------------------------------------------- manifest table
    rows = []
    for s in sounds:
        evs = s.events()
        files = sum(len(s.files(stem)) for _, stem in evs)
        lic = s.license
        evtxt = f"`{s.id}`" + (f" (+{len(evs) - 1} profile/muffled events)" if len(evs) > 1 else "")
        loops = "loop " if s.loop else ""
        dmax = max(durations[r] for r in s.files("dry"))
        where = f"`sounds/{s.dir()}/`" if not s.external else ", ".join(f"`sounds/{p}.ogg`" for p in s.paths)
        rows.append(f"| {evtxt} | {files} x .ogg in {where} ({s.variants} take(s), {loops}{dmax:.1f} s"
                    f"{', stereo' if s.stereo else ', mono'}) | {s.category} | {s.method} | {lic} |")
    update_manifest(rows)

    # ---------------------------------------------------------------- report
    total_ogg = sum(os.path.getsize(os.path.join(SOUNDS, r + ".ogg")) for r in paths)
    print(f"ids: {len(sounds)}  events: {len(events)}  ogg files: {len(paths)}  "
          f"ogg bytes: {total_ogg / 1e6:.2f} MB")
    mono = sum(1 for r in paths if infos[r]["ch"] == 1)
    print(f"mono files: {mono}  stereo files: {len(paths) - mono} "
          f"(stereo only for: {sorted({expected[r][0].id for r in paths if infos[r]['ch'] == 2})})")
    print(f"max decoded peak: {max(dbfs(infos[r]['peak']) for r in paths):.2f} dBFS")
    if loop_dev:
        worst = max(loop_dev.items(), key=lambda kv: kv[1])
        print(f"loops: {len(loop_dev)} files, worst overlap level deviation {worst[1]:.2f} dB ({worst[0]})")
    for s in sounds:
        if (s.loop or s.category == "music") and (not s.external or s.loop):
            r = s.files("dry")[0]
            i = infos[r]
            print(f"  loudness {s.id:26s} {lufs(i['x'], i['sr']):6.1f} LUFS (target {s.level}"
                  f"{', user file' if s.external else ''})  {i['sr']} Hz  {durations[r]:.2f} s  "
                  f"{os.path.getsize(os.path.join(SOUNDS, r + '.ogg')) / 1e3:.0f} kB")
    print(f"backrooms-pack.zip       {jsize / 1e6:6.2f} MB  {jn} entries  sha1 {jsha}")
    print(f"bedrock-skeleton.zip     {bsize / 1e6:6.2f} MB  {bn} entries  sha1 {bsha} (oggs added by the plugin)")
    if jsize > 29e6:
        print("WARNING: Java pack exceeds the 29 MB target (the plugin JAR must stay under 30 MB)")
    print("OK")


if __name__ == "__main__":
    main()
