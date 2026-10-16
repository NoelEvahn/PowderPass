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
  deterministic zips `src/main/resources/backrooms-pack.zip` and `bedrock-skeleton.zip`,
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
| `sfx.py` | ambience loops, the per-level beds (`bed.*`) and sound effects |
| `voices.py` | espeak-ng wrapper and voice processing (whisper, murmur, wall, PA, radio), formant-synthesised cry |
| `music.py` | instruments (piano, Rhodes, pad, bell, bass, brush), mixing, effects, the nine compositions |
| `generate.py` / `build_pack.py` | the two entry points |
| `import_user_loop.py` | turns a long user-supplied recording into a seamless 48 kHz loop and registers it in `pack/user_sounds.json` |

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

## Per-level beds

Each level has one constant bed the plugin plays on the listener (non-positional, it follows the player) and
re-triggers 300 ms before its end, under everything else:

| level | bed id |
|---|---|
| Level 0 | `bed.level0` (owner-supplied recording), fallback `bed.level0_synth` (original, CC0) |
| Poolrooms | `bed.poolrooms` |
| Habitable Zone / parking garage | `bed.garage` |
| Office | `bed.office` |
| Plains | `bed.plains` |

The synthesised beds are 40-48 s, mono, 48 kHz (`Sound.sr`), Vorbis q2 (`Sound.q`), -27 LUFS (plains -30).
Generators are in the "per-level beds" section of `sfx.py`. They keep everything loop-periodic: noise
generated at the period length (FFT-shaped noise is circular), `circ_filter`, `circ_conv` reverbs, events
written with `mix_wrap`, and `_pcurve` / `_swell` periodic control curves.

`bed.level0` comes from a recording the server owner supplied. Its license is unknown, so it is a user sound,
not a catalog entry. To rebuild it from the source file (which is not committed):

```sh
python3 tools/audio/import_user_loop.py /path/to/fluorescentlight.ogg --id bed.level0 \
    --out user/bed_level0.ogg --length 60 --lufs -26 --license "..." --method "..."
python3 tools/audio/build_pack.py
```

The script downmixes to mono, resamples to 48 kHz and high-passes at 50 Hz. It then picks the steadiest stretch
(lowest RMS spread, fewest transients) and builds a 59.7 s period whose last 4 s crossfade into its start. It
adds the 300 ms loop fades and normalises the loop. User loops (`"loop": true` in `user_sounds.json`) get the
same fade and overlap checks in `build_pack.py` as the built-in loops. `"length"` sets `stream` and the
expected duration.

The skinwalker creature sounds (`skinwalker.crackle`, `.screech`, `.chitter`, `.scuttle`) live at the end of
`sfx.py` / `voices.py`; `skinwalker.chitter` is a deliberately short 4 s loop.

After them come, in this order: the bacteria entity of Level 0 (`bacteria.skitter`, `bacteria.breath` in
`sfx.py`, `bacteria.screech` in `voices.py`), the Level 1 cars (`car.engine`, a 4 s loop-periodic idle that the
plugin pitches up with speed, `car.start`, `car.horn`, `car.door`), `steam.hiss` (6 s loop), the Level 4 lifts
(`lift.ding`, `lift.door`, `lift.motor` as a 6 s loop) and the Poolrooms `slide.rush`. `voice.announce` (10 PA
lines) and `voice.phrase` (8 lines) take their texts from the `ANNOUNCE` and `PHRASES` tables in `voices.py`.

## Acoustic profiles and the size budget

Every profile copy is a full copy of all takes, so a sound only lists the profiles its levels can request
(`Acoustics.profile()` in the plugin): Level 0 and Level 4 use carpet/office/corridor, Level 1 concrete/corridor,
the Poolrooms pool and the Plains open. `catalog.py` names these sets (`LOBBY`, `OFFICE`, `GARAGE`, `POOL`,
`PLAINS`, plus `L0_L1`, `WATER_L1`, `INDOOR`). A missing copy falls back to the dry or muffled event. If the
plugin starts playing a sound in another level, add that level's profiles and regenerate the id. Sounds the
plugin plays only by their base event (the skinwalker's) carry no profile copies.

The JAR embeds the Java pack and must stay under 30 MB; keep the audio part at or below 26.5 MB (it is about
23.5 MB). `build_pack.py` prints the total OGG bytes and warns when the zip exceeds 29 MB.

User-supplied, properly licensed tracks go in `pack/user_sounds.json`. See `pack/MANIFEST.md`.
