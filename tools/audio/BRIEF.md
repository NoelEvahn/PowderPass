# Audio asset brief (contract between the asset pipeline and the plugin)

Everything here is produced offline by `tools/audio/*.py` and committed. The plugin never synthesises audio.

## Environment facts
- Python 3 with numpy and scipy installed; `ffmpeg` with `libvorbis`; `espeak-ng` (GPL tool, its *output* is not
  encumbered) for original synthetic speech. freesound.org / opengameart.org / mc-packs.net are NOT reachable from
  this sandbox (proxy 403). PyPI and archive.ubuntu.com are reachable. So: everything is synthesised or TTS-generated,
  all original, released by this project as CC0. No third-party recordings, with one owner-supplied exception:
  `bed.level0` (see "Per-level beds"), registered as a user sound with its own license note.
- Deterministic: fixed RNG seeds per asset so re-running produces byte-identical WAV input (OGG encoding should be
  stable too; pin `-q:a`).

## Layout (output)
```
pack/java/pack.mcmeta                      # pack_format 75, min_format 69, max_format 75 (Paper 1.21.11 client)
pack/java/pack.png                         # optional 64x64, generated
pack/java/assets/backrooms/sounds.json
pack/java/assets/backrooms/sounds/<group>/<file>.ogg
pack/bedrock/manifest.json, pack/bedrock/sounds/sound_definitions.json, pack/bedrock/sounds/... (same ogg files)
pack/MANIFEST.md                           # every asset: id, file(s), how made, license (CC0, original), research notes
src/main/resources/backrooms-pack.zip      # deterministic zip of pack/java (sorted entries, fixed timestamps 1980-01-01)
src/main/resources/backrooms-bedrock.mcpack# zip of pack/bedrock
src/main/resources/pack-index.json         # read by the plugin (schema below)
```
Scripts: `tools/audio/generate.py` (all synthesis), `tools/audio/build_pack.py` (validate + zip + index),
`tools/audio/README.md`. `build_pack.py` must fail loudly on: a sounds.json reference without a file, a file not
referenced, a non-Vorbis or unreadable OGG (check with ffprobe), a positional sound that is not mono, clipping
(peak > -0.5 dBFS), sample rate other than 44100/48000.

## Format rules
- Mono for everything positional. Stereo ONLY for `music.*.ambient` (non-positional, played at the listener).
- `"stream": true` in sounds.json for anything longer than ~10 s (music, long loops).
- Loops: 6–12 s, 300 ms equal-power fade in/out at both ends so the plugin can re-trigger with a 300 ms overlap
  without clicks. Hum loops must be pitch-stable so overlaps do not beat audibly. Exceptions: the per-level beds
  (`bed.*`) are 40–60 s so their repetition is not obvious; `skinwalker.chitter` and `car.engine` are 4 s.
- Loudness: ambience around -24 LUFS-ish (quiet), one-shots peak -3 dBFS, music around -20 LUFS. Never clip.
- Vorbis quality: `-q:a 3` for SFX, `-q:a 2` for long music and for the beds (`Sound.q` overrides it per id).
  Sample rate 44.1 kHz, except the beds at 48 kHz (`Sound.sr`; synthesis runs at 44.1 kHz and `generate.py`
  resamples on encode). Budget: the audio part of the Java pack at most 26.5 MB, the whole Java pack (audio +
  ~1.4 MB of textures) small enough that the plugin JAR stays under 30 MB (`build_pack.py` warns above 29 MB).

## pack-index.json schema
```json
{ "version": 1,
  "sounds": {
    "hum.fluorescent": { "category": "ambient", "duration_ticks": 160, "loop": true, "positional": true,
                         "variants": 3, "profiles": [], "muffled": false },
    "knock.distant":   { "category": "event", "duration_ticks": 30, "loop": false, "positional": true,
                         "variants": 4, "profiles": ["carpet","office","concrete","pool","corridor","open"], "muffled": true }
  } }
```
- The sound event name in sounds.json is `backrooms:<id>` for the dry/base event, `backrooms:<id>.<profile>` for each
  acoustic profile, and `backrooms:<id>.<profile>.muffled` (or `<id>.muffled` when no profiles) for the
  low-passed "behind a wall" variant. Variants (different takes) are multiple entries inside ONE event (vanilla picks
  randomly), not separate events.
- `duration_ticks` = longest variant length in ticks (20/s), rounded up.

## Acoustic profiles (offline convolution with synthetic impulse responses)
| profile | character |
|---|---|
| carpet   | very short, damped (RT60 ~0.3 s, strong HF damping) |
| office   | medium room (RT60 ~0.6 s) |
| concrete | hard, long warehouse/garage (RT60 ~1.8 s, bright early reflections) |
| pool     | broad, smooth, long (RT60 ~3.5 s, dense, slightly dark) |
| corridor | flutter-ish early echoes, RT60 ~0.9 s |
| open     | almost dry, faint distant slap |
"muffled" = 4th-order low-pass ~600 Hz, -6 dB, a little extra reverb.

Profile copies cost space (one full copy of every take per profile), so each sound only carries the profiles
that the levels it plays in can request. The plugin (`Acoustics.profile()`) picks:

| level | profiles it can request | catalog constant |
|---|---|---|
| Level 0 (Lobby) | carpet, office, corridor | `LOBBY` |
| Level 1 (Habitable Zone garage) | concrete, corridor | `GARAGE` |
| Poolrooms | pool | `POOL` |
| Level 4 (Office) | carpet, office, corridor | `OFFICE` (= `LOBBY`) |
| Plains | open | `PLAINS` |

Combined sets in `catalog.py`: `L0_L1` (carpet, office, concrete, corridor), `WATER_L1` (concrete, pool,
corridor), `INDOOR` (everything but open). A profile copy that does not exist falls back to the dry event (or the
muffled one), so a sound heard somewhere new still plays, only without the matching room. When the plugin starts
playing a sound in another level, add that level's profiles to its catalog entry and regenerate it.

## Sound list (ids WITHOUT namespace)
Category in brackets. L = loop. Variants = distinct takes.

Electrical / light
- `hum.fluorescent` [ambient, L 8s, 3 variants] ballast buzz: 120 Hz + harmonics, rattly odd harmonics, slight AM.
- `hum.fluorescent_dense` [ambient, L 8s] many tubes: thicker, chorused version.
- `hum.mains` [ambient, L 10s] 60 Hz + harmonics, very low, distant transformer-like.
- `light.flicker` [event, ~1.5 s, 4 variants] tube strike/ticking flicker.
- `light.pop` [event, short, 3 variants] electrical pop.
- `light.arc` [event, ~1 s, 3 variants] arc / sparks / short circuit.
- `power.down` [event, ~2.5 s] hum winding down + relay clunk. `power.up` [event, ~2 s] relay clunk + hum swelling back.

Per-level beds (the constant, player-attached layer)
- One long seamless loop per level, played by the plugin ON THE LISTENER (non-positional, follows the player) for
  as long as the player is in that level, re-triggered 300 ms before its end (6 ticks before `duration_ticks`) so
  the two instances crossfade. Everything else (positional hums, events, music) plays on top of it.
  All: [ambient, mono, 48 kHz, `positional: false`, `loop: true`, stream, Vorbis q2]. Built-in beds are made
  loop-periodic (circular noise, `circ_filter`, `circ_conv` reverbs, `mix_wrap` events), so the overlap
  reconstructs them exactly.
- `bed.level0` [L 60 s, -26 LUFS] Level 0. A real recording of fluorescent lights buzzing supplied by the server
  owner (from the "Backrooms PBR Resource Pack" by Pro_fessional, packsmc.com; redistribution permission must be
  confirmed by the owner). It is a user sound: `pack/user_sounds.json` + `sounds/user/bed_level0.ogg`, made by
  `tools/audio/import_user_loop.py`. The source recording is not in the repository.
- `bed.level0_synth` [L 40 s, -27 LUFS] original CC0 fallback for Level 0: ten detuned 120 Hz ballast stacks
  (slow beating), buzz-gated noise, faint mains hum. Use it when the recording may not be redistributed.
- `bed.poolrooms` [L 40 s, -27 LUFS] pump motor + impeller hum, circulation flow, distant soft lapping against
  tile, very faint high tiled-room air tone; pool reverb.
- `bed.garage` [L 48 s, -27 LUFS] underground parking garage (Habitable Zone): ventilation rush with duct
  resonances, low distant machinery cycle, steam leaks hissing and swelling irregularly, rare faint metal ticks
  and tiny groans; concrete reverb.
- `bed.office` [L 40 s, -27 LUFS] HVAC diffuser air, faint 120 Hz ballast buzz, low computer/server hum, very
  faint distant electrical buzz.
- `bed.plains` [L 48 s, -30 LUFS] extremely subtle low wind with slow gust swells, faint grass hiss.

Machinery / air (Habitable Zone parking garage, Office)
- `vent.hvac` [ambient, L 10s] air-handler rush with fan whine. `vent.small` [ambient, L 8s] grille hiss.
- `machine.distant` [ambient, L 12s] distant rhythmic machinery with low thumps (has profile `concrete` baked in).
- `machine.start` [event, ~4 s, profiles GARAGE] equipment activation: clunk, motor spin-up.
- `transformer.hum` [ambient, L 8s] 60/120 Hz heavy buzz.
- `pipe.knock` [event, ~2 s, 3 variants, profiles GARAGE] pipe vibration / water hammer.
- `metal.creak` [event, ~2 s, 4 variants, profiles GARAGE] thermal expansion creak.
- `metal.clank` [event, ~2.5 s, 4 variants, profiles GARAGE] something heavy hitting metal pipes/ducts far away: impact,
  inharmonic ringing, rattle.
- `metal.groan` [event, ~4 s, 3 variants, profiles GARAGE, muffled] long low structural groan (stick-slip, slowly
  gliding), distant.
- `steam.burst` [event, ~2 s, 3 variants, profiles GARAGE] pipe valve / leak letting out a burst of steam: sharp
  hiss onset with moving resonance, sputter, tail, faint pipe ring.
- `steam.hiss` [ambient, L 6s, mono, positional, -25 LUFS] continuous steam leaking from a pipe valve: resonant
  hiss around a slowly wandering ~4.3 kHz centre, slight ~11 Hz flutter, faint whistle.

Cars (Level 1 parking garage)
- `car.engine` [ambient, L 4s (exception to the 6-12 s rule), mono, positional, -21 LUFS] old 4-cylinder car
  idling (~740 rpm), loop-periodic (the firing pulses fit the period exactly), so the plugin can raise its pitch
  with the car's speed and re-trigger it seamlessly.
- `car.start` [event, ~1.5 s, profiles GARAGE] solenoid, starter motor cranking against compression, the engine
  catching, a short rev settling towards idle.
- `car.horn` [event, ~0.8 s, 2 variants, profiles GARAGE] old two-tone diaphragm horn: one long honk / two short.
- `car.door` [event, ~0.6 s, 2 variants, profiles GARAGE] door slammed shut (thunk) / door opened (latch, creak,
  lighter thunk).

Lifts (Level 4 towers)
- `lift.ding` [event, ~1.2 s, profiles OFFICE] 2000s lift arrival chime (single soft electronic bell).
- `lift.door` [event, ~1.5 s, profiles OFFICE] sliding doors: operator motor whir, roller rumble, rattle, bump.
- `lift.motor` [ambient, L 6s, mono, positional, -24 LUFS] travel hum and inverter whine inside the car, rope/rail
  rush, faint guide-rail bumps.

Water (Poolrooms, damp)
- `water.drip` [event, ~1 s, 5 variants, profiles WATER_L1] single drips.
- `water.splash` [event, ~2 s, 3 variants, profiles POOL] distant splash.
- `slide.rush` [event, ~3 s, profiles POOL] riding a water slide: rushing water whoosh, gurgle, spray.
- `water.lap` [ambient, L 10s, 2 variants] gentle water movement against tile.
- `water.circulation` [ambient, L 10s] pool pump hum + flow.
- `water.bubbles` [event, ~2 s, 3 variants]. `water.underwater` [ambient, L 8s] muffled underwater bed (non-positional).
- `room.damp` [ambient, L 10s] very quiet room tone with occasional faint drip.

Presence
- `knock.distant` [event, ~1.5 s, 4 variants, profiles L0_L1, muffled] knocks / thumps on a wall or door.
- `impact.distant` [event, ~2.5 s, 3 variants, profiles L0_L1, muffled] heavy unexplained impact.
- `step.carpet` (profiles LOBBY), `step.concrete` (GARAGE), `step.tile` (POOL) [event, ~0.5 s each, 6 variants]
  single footsteps (plugin sequences them). `step.water` [event, 4 variants, profiles INDOOR] wading step (used
  in any level while the listener is under water).
- `movement.distant` [event, ~3 s, 3 variants, profiles LOBBY, muffled] something shifting / dragging far away.
- `breath.heavy` [event, ~4 s, 3 variants] close, unsettling breathing (mono, dry).

Creature (skinwalker: imitates players, then cracks open into a spider-like true form)
The creature code plays these by their base event (`backrooms:<id>`, no profile), so they carry no profile copies.
- `skinwalker.crackle` [event, ~2.5 s, 3 variants, muffled] accelerating bone cracks/snaps and wet joint
  pops ending in a heavy crunch. No voice.
- `skinwalker.screech` [event, ~2 s, 2 variants] layered pitch-dropping shriek, almost-human vowel
  underneath (espeak-ng vowel only, never words), clicks.
- `skinwalker.chitter` [ambient, L 4s (exception to the 6-12 s rule), ~-26 LUFS] rapid irregular chitin clicking
  over a low wet breath.
- `skinwalker.scuttle` [event, ~0.3 s, 6 variants] fast multi-leg skitter: several hard taps.

Creature (bacteria: a tall, thin, black humanoid entity in Level 0)
- `bacteria.screech` [event, ~2 s, 2 variants, profiles LOBBY] distorted, glitchy, high shrieking scream: thin
  saturated shriek over an espeak-ng vowel (never words) sped up, wrecked by tape wow and digital artefacts
  (buffer-repeat stutters, bit-crush, tape-speed jumps, dropouts, static bursts), hard clipped.
- `bacteria.skitter` [event, ~0.6 s, 4 variants, profiles LOBBY] fast irregular long-limbed footfalls on carpet
  with dry joint clicks.
- `bacteria.breath` [event, ~3 s, 2 variants, no profiles] wet, ragged, rattling breath, close and dry.

Radio / speakers
- `radio.static` [ambient, L 8s] AM static/hiss loop. `speaker.broken` [event, ~2 s, 3 variants] crackle/distortion
  burst.

Voices (original synthetic speech from espeak-ng, varied voices/pitches; never imitate a real person)
- `voice.whisper` [voice, 6 variants, profiles LOBBY] indistinct whispers (unintelligible).
- `voice.murmur` [voice, 4 variants, profiles POOL, muffled] reversed-sounding murmurs.
- `voice.conversation` [voice, 4 variants, muffled] distant unintelligible conversation behind a wall (layered, LPF).
- `voice.phrase` [voice, 8 variants, profiles INDOOR, muffled] intelligible lines, one variant per phrase:
  "Is anybody there?", "Can you hear me?", "Hello?", "I think someone is here.", "Wait for me.", "Over here.",
  "Did you hear that?", "Who's there?", processed as distant/room voices.
- `voice.announce` [voice, 10 variants, profiles INDOOR] PA/intercom lines after a two-tone chime: "Please remain
  where you are.", "This area is closed.", "Do not enter.", "Attention. The building will close shortly.",
  "Please proceed to the nearest exit.", "There is no exit on this floor.", "Remain calm. Help is on the way.",
  "Your patience is appreciated.", "Do not look behind you.", "Maintenance is in progress on all levels."
  (band-limited 300–3400 Hz, slight distortion, PA reverb). The plugin plays an announcement or voice line about
  every 40 s in Level 0, the Poolrooms, Level 1 and Level 4, hence the ten takes.
- `voice.radio` [voice] radio-transmission versions of a few phrases with static and dropouts.
- `voice.cry` [voice, 2 variants, profiles PLAINS] very distant cry (heavily processed, not graphic).

Office
- `office.ac` [ambient, L 10s] office air conditioning. `office.printer` [event, ~4 s, 2 variants, profiles
  OFFICE] printer-like mechanism. `office.phone` [event, ~4 s, profiles OFFICE, muffled] old desk phone ring.
  `office.click` [event, short, 4 variants] relay/keyboard/switch clicks.

Plains
- `wind.plains` [ambient, L 12s, 2 variants] extremely subtle low wind. `grass.rustle` [event, ~2 s, 3 variants].
- `plains.distant` [event, ~4 s, 3 variants] distant unidentified sound (low horn-like swell / metallic groan, far).

Transitions
- `silence.transition` [event, ~3 s] soft pressure swell into near-silence (non-positional).

Music (ORIGINAL compositions, see research section; 60–120 s each; mono positional + optional ambient stereo)
- `music.lobby_1`, `music.lobby_2` [music, mono, stream, muffled] worn detuned piano, slow, tape wow/flutter, sparse.
- `music.pool_1`, `music.pool_2` [music, mono, stream, profiles ["pool"]] dreamy soft synth pads + bell melody,
  gentle, lots of space (inspired only in broad mood by "School Rooftop"; no melody copying).
- `music.office_radio_1`, `music.office_radio_2` [music, mono, stream, muffled] old-radio filtered nostalgic tune
  (AM band-limited, crackle), loops well.
- `music.plains_1`, `music.plains_2` [music, stream] soft nostalgic synth/Rhodes chords, distant (mood references
  "Six Forty Seven", "Numbers" — broad characteristics only). Provide BOTH mono `music.plains_N` and stereo
  `music.plains_N.ambient`.
- `music.garage_1` [music, mono, stream, profiles ["concrete"]] distant echoing drone with faint melodic fragments.

## Research notes to write into pack/MANIFEST.md
From general knowledge (the listed URLs are not reachable here and must NOT be downloaded): characteristics of the
four reference tracks and of liminal/dreamcore/vaporwave ambience (slowed tempo, detuned/tape-warped keys, heavy
reverb, sparse harmony, major-7th/add9 chords, lo-fi saturation, distant mix). State clearly that no copyrighted
material is included, that references were used only for broad mood, and how a server owner can add their own
properly licensed tracks (drop OGG into pack, add to sounds.json, rebuild with build_pack.py, set config).
