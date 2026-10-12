# Offline audio pipeline

The plugin never synthesises audio. Everything it plays is generated here, committed, and embedded in the JAR.
`BRIEF.md` is the contract between this pipeline and the plugin's audio engine.

## Regenerate

```sh
python3 tools/audio/generate.py && python3 tools/audio/build_pack.py
```

Requirements: Python 3 with `numpy` and `scipy`, `ffmpeg`/`ffprobe` with `libvorbis`, and `espeak-ng`
(`/usr/bin/espeak-ng`, or set the `ESPEAK` env var). A full run takes about 1.5 min on 4 cores.

* `generate.py` renders every id in `catalog.py` and encodes it to Ogg Vorbis under
  `pack/java/assets/backrooms/sounds/<id with dots as slashes>/<event>_<n>.ogg`. The event stem is `dry`, a
  profile name, `<profile>_muffled` or `muffled`. Options:
  * `--only id1,id2`: regenerate some ids only. Their directories are replaced.
  * `--keep-wav DIR`: also keep the float WAV masters.
  * `--jobs N`: number of worker processes.
* `build_pack.py` validates every file (ffprobe and a full decode) and writes `sounds.json`, `pack.mcmeta`,
  `pack.png`, the Bedrock pack (`manifest.json`, `sound_definitions.json`, the same OGG files), the
  deterministic zips `src/main/resources/backrooms-pack.zip` and `backrooms-bedrock.mcpack`,
  `src/main/resources/pack-index.json`, and the asset table in `pack/MANIFEST.md`. It exits non-zero on any
  contract violation (missing or unreferenced file, non-Vorbis, wrong sample rate, positional non-mono,
  clipping) and on sanity failures (silent file, unexpected duration, loop not faded or not overlapping
  smoothly). At the end it prints the counts, the loudness of loops and music, and the sizes and SHA-1s of both
  zips.

## Files

| file | contents |
|---|---|
| `catalog.py` | every sound id with its metadata (category, length, variants, profiles, muffled, loudness, method) |
| `dsp.py` | filters, noise, envelopes, loudness (BS.1770), limiter, synthetic room IRs and the six acoustic profiles, loop assembly |
| `sfx.py` | ambience beds and sound effects |
| `voices.py` | espeak-ng wrapper and voice processing (whisper, murmur, wall, PA, radio), formant-synthesised cry |
| `music.py` | instruments (piano, Rhodes, pad, bell, bass, brush), mixing, effects, the nine compositions |
| `generate.py` / `build_pack.py` | the two entry points |

## Determinism

Seeds come from `crc32("<id>|<variant>")`, the IRs from `crc32("ir|<profile>")`, and ffmpeg encodes with
`-fflags +bitexact -flags:a +bitexact -map_metadata -1`. Zips use sorted entries, a fixed 1980-01-01
timestamp and fixed permissions. Re-running on the same toolchain gives byte-identical OGGs and the same zip
SHA-1. A different numpy/scipy/libvorbis version can change the bits, but not the sound.

## Adding or changing a sound

1. Write a generator in `sfx.py`, `voices.py` or `music.py`:
   * one-shot: `fn(rng, variant) -> mono float array`. Normalised later.
   * loop: `fn(rng, variant, n, P) -> (tonal_period, noise)`. `tonal_period` has length `P = n - 0.3 s` and must
     be exactly periodic: use `qf()` to quantise frequencies, and `circ_filter` / `circ_conv` for processing.
     `noise` has length `n`. Use `periodic_smooth` for its slow level modulation.
2. Add a `Sound(...)` entry to `catalog.py`.
3. Run `generate.py --only <id>`, then `build_pack.py`.

User-supplied, properly licensed tracks go in `pack/user_sounds.json`. See `pack/MANIFEST.md`.
