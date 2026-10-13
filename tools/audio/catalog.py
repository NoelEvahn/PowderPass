"""Single source of truth for every sound id (see tools/audio/BRIEF.md).

Shared by generate.py (what to synthesise) and build_pack.py (sounds.json, pack-index.json,
Bedrock definitions, validation, MANIFEST table).
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional

ALL = ["carpet", "office", "concrete", "pool", "corridor", "open"]
NAMESPACE = "backrooms"


@dataclass
class Sound:
    id: str
    category: str            # ambient | event | voice | music
    gen: str                 # "module.function"
    length: float            # loop length (exact) or nominal one-shot length (s)
    variants: int = 1
    loop: bool = False
    profiles: List[str] = field(default_factory=list)
    muffled: bool = False
    positional: bool = True
    stereo: bool = False
    level: float = -3.0      # loops/music: LUFS target; one-shots: peak dBFS
    method: str = ""
    source: Optional[str] = None  # for derived ids (music .ambient): id of the job that renders it
    paths: Optional[List[str]] = None  # external (user-supplied) sounds: explicit file paths, no ext
    license: str = "CC0-1.0, original (this project)"

    @property
    def stream(self) -> bool:
        return self.loop and self.length >= 10 or self.category == "music"

    @property
    def quality(self) -> int:
        return 2 if self.category == "music" else 3

    def events(self):
        """[(event_name_without_namespace, file_stem)] in a stable order."""
        out = [(self.id, "dry")]
        for p in self.profiles:
            out.append((f"{self.id}.{p}", p))
        if self.muffled:
            if self.profiles:
                for p in self.profiles:
                    out.append((f"{self.id}.{p}.muffled", f"{p}_muffled"))
            else:
                out.append((f"{self.id}.muffled", "muffled"))
        return out

    def dir(self) -> str:
        return self.id.replace(".", "/")

    @property
    def external(self) -> bool:
        return self.paths is not None

    def files(self, stem):
        if self.paths is not None:
            return list(self.paths)
        return [f"{self.dir()}/{stem}_{i + 1}" for i in range(self.variants)]


def S(*a, **k):
    return Sound(*a, **k)


SOUNDS: List[Sound] = [
    # ---------------------------------------------------------------- electrical / light
    S("hum.fluorescent", "ambient", "sfx.hum_fluorescent", 8, 3, loop=True, level=-24,
      method="Additive 120 Hz ballast stack (exact loop-periodic harmonics, boosted odd 'rattle' harmonics, "
             "seeded spectral jitter per take), slow AM, buzz-gated band noise, room IR."),
    S("hum.fluorescent_dense", "ambient", "sfx.hum_fluorescent_dense", 8, loop=True, level=-24,
      method="Six detuned tube stacks (detune quantised to whole cycles per loop -> slow, seamless beating), "
             "chorused buzz noise, room IR."),
    S("hum.mains", "ambient", "sfx.hum_mains", 10, loop=True, level=-28,
      method="60 Hz + harmonics, low-passed, concrete IR (distant transformer), brown-noise rumble."),
    S("light.flicker", "event", "sfx.light_flicker", 1.5, 4,
      method="Gated ballast buzz with random on/off segments and modal starter ticks."),
    S("light.pop", "event", "sfx.light_pop", 0.5, 3,
      method="Noise transient + pitch-dropping thump + modal glass tinkle, small room."),
    S("light.arc", "event", "sfx.light_arc", 1.0, 3,
      method="Clipped jittered 120 Hz arc buzz with bursty envelope, Poisson spark crackles, HF sizzle."),
    S("power.down", "event", "sfx.power_down", 2.5,
      method="Relay clunk (thump + modal click) then harmonic hum gliding down in pitch/brightness."),
    S("power.up", "event", "sfx.power_up", 2.0,
      method="Relay clunk, hum gliding up and swelling, tube starter ticks."),
    # ---------------------------------------------------------------- machinery / air
    S("vent.hvac", "ambient", "sfx.vent_hvac", 10, loop=True, level=-24,
      method="Pink-noise air rush with duct-mode resonances and turbulence AM; FM fan blade-pass and motor whine."),
    S("vent.small", "ambient", "sfx.vent_small", 8, loop=True, level=-27,
      method="Band-passed grille hiss with flutter AM, low air, faint wobbling grille whistle."),
    S("machine.distant", "ambient", "sfx.machine_distant", 12, loop=True, level=-24,
      method="Fully loop-periodic machine cycle (9 x 1.3 s): thump, modal clank, pneumatic hiss, motor drone; "
             "concrete IR baked in (circular convolution)."),
    S("machine.start", "event", "sfx.machine_start", 4.0, profiles=ALL,
      method="Contactor clunk, motor spin-up (rotation harmonics, gear whine, rising air noise, rattle)."),
    S("transformer.hum", "ambient", "sfx.transformer_hum", 8, loop=True, level=-23,
      method="Asymmetrically saturated 120/60 Hz core buzz + harmonic rattle band, buzz-gated noise."),
    S("pipe.knock", "event", "sfx.pipe_knock", 2.0, 3, profiles=ALL,
      method="Modal pipe (free-bar ratios) excited by bouncing hammer hits + thud."),
    S("metal.creak", "event", "sfx.metal_creak", 2.0, 4, profiles=ALL,
      method="Stick-slip impulse train with gliding rate through random metal modes; optional expansion tick."),
    # ---------------------------------------------------------------- water
    S("water.drip", "event", "sfx.water_drip", 0.8, 5, profiles=ALL,
      method="Minnaert-bubble drop model (exponentially rising sine) + impact click, optional second plip."),
    S("water.splash", "event", "sfx.water_splash", 2.0, 3, profiles=ALL,
      method="Band-limited noise burst, plunge thump, dozens of bubble blips, falling-back drops, distance LPF."),
    S("water.lap", "ambient", "sfx.water_lap", 10, 2, loop=True, level=-25,
      method="Irregular slap envelopes on band-passed pink noise, tlok drops, bubbles, low swell, pool IR."),
    S("water.circulation", "ambient", "sfx.water_circulation", 10, loop=True, level=-25,
      method="Pump motor + impeller vane tones (loop-periodic), pink flow noise, gurgle via moving band weights."),
    S("water.bubbles", "event", "sfx.water_bubbles", 2.0, 3,
      method="Clustered bubble blips (size->pitch) and low gurgle."),
    S("water.underwater", "ambient", "sfx.water_underwater", 8, loop=True, positional=False, level=-26,
      method="Low-passed brown-noise bed with slow pressure swell, muffled bubbles, faint 55 Hz tone."),
    S("room.damp", "ambient", "sfx.room_damp", 10, loop=True, level=-32,
      method="Very quiet pink/brown room tone with two faint reverberant drips."),
    # ---------------------------------------------------------------- presence
    S("knock.distant", "event", "sfx.knock_distant", 1.5, 4, profiles=ALL, muffled=True,
      method="Wood-door modal body excited by knuckle/fist bursts, four knock patterns, distance LPF."),
    S("impact.distant", "event", "sfx.impact_distant", 2.5, 3, profiles=ALL, muffled=True,
      method="Pitch-dropping boom, sub noise, crunch, ringing structure, debris ticks, distance LPF."),
    S("step.carpet", "event", "sfx.step_carpet", 0.5, 6, profiles=ALL,
      method="Heel + toe low-passed noise thuds with fabric scuff."),
    S("step.concrete", "event", "sfx.step_concrete", 0.5, 6, profiles=ALL,
      method="Heel/toe click + body thump + grit micro-clicks."),
    S("step.tile", "event", "sfx.step_tile", 0.5, 6, profiles=ALL,
      method="Hard click + short ceramic modal ring + thud."),
    S("step.water", "event", "sfx.step_water", 1.1, 4, profiles=ALL,
      method="Two sloshes (moving band weights), bubbles, falling drops."),
    S("movement.distant", "event", "sfx.movement_distant", 3.0, 3, profiles=ALL, muffled=True,
      method="Granular friction drag through furniture resonances + slow creak + set-down thump / rumble."),
    S("breath.heavy", "event", "sfx.breath_heavy", 4.0, 3,
      method="Formant-filtered noise inhales/exhales with tremble and a creaky glottal rasp; dry, close."),
    # ---------------------------------------------------------------- creature (skinwalker)
    S("skinwalker.crackle", "event", "sfx.skinwalker_crackle", 2.5, 3, profiles=ALL, muffled=True,
      method="Accelerating Poisson cluster (~7/s -> ~50/s) of bone cracks (fracture micro-impulses through damped "
             "inharmonic modal bodies + low knock), knuckle ratchets, bright snaps and wet joint pops (gliding "
             "cavitation pop + gated squelch noise), stick-slip sinew strain underneath, final heavy crunch "
             "(deep knock, dense splinter burst, gritty crush noise, wet tail). No voice."),
    S("skinwalker.screech", "event", "voices.skinwalker_screech", 2.0, 2, profiles=ALL,
      method="Pitch-dropping saturated harmonic shriek (period-doubling rasp, inharmonic second voice, high "
             "formants) over an espeak-ng pure vowel (phoneme input, no words) glide-resampled down, ring-modulated "
             "and saturated, plus a throat growl and chitin mandible click rattles."),
    # target -25.5: the 2 x 0.3 s fades are 15 % of a 4 s loop and Vorbis trims some click HF, so the
    # decoded file measures about -26.3 LUFS
    S("skinwalker.chitter", "ambient", "sfx.skinwalker_chitter", 4, loop=True, level=-25.5,
      method="Loop-periodic chitin clicks (irregular chitter bursts, fast stridulating trills, lone clicks through "
             "short inharmonic shell modes, soft-limited) over a low wet breath (formant-filtered pink noise with "
             "loop-periodic inhale/exhale envelope, gated squelch, sub rumble)."),
    S("skinwalker.scuttle", "event", "sfx.skinwalker_scuttle", 0.3, 6, profiles=ALL,
      method="3-7 hard leg-tip taps 16-45 ms apart (sharp tick, short shell ring, floor knock, occasional flam), "
             "flat / crescendo / decrescendo bursts."),
    # ---------------------------------------------------------------- radio / speakers
    S("radio.static", "ambient", "sfx.radio_static", 8, loop=True, level=-26,
      method="AM-band hiss with fading, Poisson crackles, faint drifting heterodyne whistle."),
    S("speaker.broken", "event", "sfx.speaker_broken", 2.0, 3,
      method="Hum + noise through intermittent-contact gate, hard clipping, crackles, small-cone EQ."),
    # ---------------------------------------------------------------- voices
    S("voice.whisper", "voice", "voices.voice_whisper", 4.0, 6, profiles=ALL,
      method="espeak-ng whisper variants on random nonsense syllables, chunk-shuffled/reversed, breath layer."),
    S("voice.murmur", "voice", "voices.voice_murmur", 3.8, 4, profiles=ALL, muffled=True,
      method="2-3 layered espeak-ng nonsense lines, reversed, slowed, low-passed."),
    S("voice.conversation", "voice", "voices.voice_conversation", 6.0, 4, muffled=True,
      method="Two espeak-ng voices alternating nonsense turns, room IR, 500 Hz wall low-pass."),
    S("voice.phrase", "voice", "voices.voice_phrase", 2.0, 4, profiles=ALL, muffled=True,
      method="espeak-ng lines (one per variant: 'Is anybody there?', 'Can you hear me?', 'Hello?', "
             "'I think someone is here.'), band-limited, distance reflections."),
    S("voice.announce", "voice", "voices.voice_announce", 4.5, 3, profiles=ALL,
      method="Two-tone chime + espeak-ng PA lines ('Please remain where you are.', 'This area is closed.', "
             "'Do not enter.'), 300-3400 Hz, saturation, multi-speaker delays, PA reverb."),
    S("voice.radio", "voice", "voices.voice_radio", 3.0, 3,
      method="espeak-ng lines through radio band-pass, hard clip, static bed, squelch tails, dropouts."),
    S("voice.cry", "voice", "voices.voice_cry", 3.4, 2, profiles=ALL,
      method="Formant-synthesised distant vowel call (no TTS), heavy low-pass and long distant reverb."),
    # ---------------------------------------------------------------- office
    S("office.ac", "ambient", "sfx.office_ac", 10, loop=True, level=-26,
      method="Smooth low-passed pink diffuser air, light hiss, faint fan harmonics."),
    S("office.printer", "event", "sfx.office_printer", 4.0, 2, profiles=ALL,
      method="Solenoid clunk, feed-roller whir and paper swish, stepper carriage passes, line-feed ticks."),
    S("office.phone", "event", "sfx.office_phone", 3.6, profiles=ALL, muffled=True,
      method="Two inharmonic modal bells struck alternately at 20 Hz, two ring bursts, room."),
    S("office.click", "event", "sfx.office_click", 0.25, 4,
      method="Relay, keyboard key, wall switch and latch clicks (noise bursts through modal bodies)."),
    # ---------------------------------------------------------------- plains
    S("wind.plains", "ambient", "sfx.wind_plains", 12, 2, loop=True, level=-30,
      method="Low-passed brown-noise gusts and a faint wandering resonant whistle."),
    S("grass.rustle", "event", "sfx.grass_rustle", 2.0, 3,
      method="Dense band-passed micro-crackle with swish layer and gusty envelope."),
    S("plains.distant", "event", "sfx.plains_distant", 4.0, 3,
      method="Far horn-like swell / slow metallic groan / beating low tones, long distant IR with slaps."),
    # ---------------------------------------------------------------- transitions
    S("silence.transition", "event", "sfx.silence_transition", 3.0, positional=False,
      method="Brown-noise pressure swell with closing low-pass and sub tone, drop to near-silence, faint ringing."),
    # ---------------------------------------------------------------- music
    S("music.lobby_1", "music", "music.lobby_1", 80, muffled=True, level=-20,
      method="Db major, 58 BPM; detuned additive piano (2-string beating, +-5 c tuning drift), rolled maj9/m9 "
             "voicings, motif melody, tape wow/flutter, saturation, hall IR, hiss."),
    S("music.lobby_2", "music", "music.lobby_2", 80, muffled=True, level=-20,
      method="C major slow waltz (50 BPM 3/4), worn piano, heavier wow, hall IR."),
    S("music.pool_1", "music", "music.pool_1", 78, profiles=["pool"], level=-20,
      method="E major, 68 BPM; chorused detuned-saw pads (maj9/m11), sub, FM celesta bell melody with "
             "ping-pong echo, 4.5 s reverb."),
    S("music.pool_2", "music", "music.pool_2", 79, profiles=["pool"], level=-20,
      method="F major, 60 BPM; pads (maj9/m9/maj7#11/6-9), Rhodes melody with bell doubling, echo, long reverb."),
    S("music.office_radio_1", "music", "music.office_radio_1", 70, muffled=True, level=-20,
      method="Bb major lounge tune, 84 BPM swing; FM Rhodes comping, bass, brushes, vibes-like bell melody; "
             "AM-radio band-limit, saturation, static and crackle. Ends on the V7 so it loops."),
    S("music.office_radio_2", "music", "music.office_radio_2", 74, muffled=True, level=-20,
      method="F major waltz, 100 BPM; piano comp, bass, bell melody; narrower radio band, more crackle; ends on V7."),
    S("music.plains_1", "music", "music.plains_1", 73, level=-20,
      method="G major (IV-iii-ii-V), 62 BPM; FM Rhodes chords + pad + sparse Rhodes/bell melody, chorus, "
             "dark long reverb, tape, distant low-pass. Mono downmix of the stereo render."),
    S("music.plains_1.ambient", "music", "music.plains_1", 73, positional=False, stereo=True, level=-20,
      source="music.plains_1", method="Stereo version of music.plains_1 (non-positional)."),
    S("music.plains_2", "music", "music.plains_2", 84, level=-20,
      method="Eb major (I-vi-IV-V with maj9/m9/maj7#11/6sus2), 56 BPM; same palette as plains_1."),
    S("music.plains_2.ambient", "music", "music.plains_2", 84, positional=False, stereo=True, level=-20,
      source="music.plains_2", method="Stereo version of music.plains_2 (non-positional)."),
    S("music.garage_1", "music", "music.garage_1", 82, profiles=["concrete"], level=-20,
      method="C# drone (detuned saw stack, slowly sweeping filters), C#m9 pad swells, sparse pentatonic "
             "piano/bell fragments with long echoes, reverb."),
]

BY_ID = {s.id: s for s in SOUNDS}


def load_user_sounds(path):
    """Optional pack/user_sounds.json: properly licensed tracks supplied by a server owner.

    { "music.user_lobby": { "files": ["user/my_track.ogg"], "category": "music",
                            "positional": false, "loop": false, "license": "CC-BY-4.0 Jane Doe" } }
    Files live under pack/java/assets/backrooms/sounds/ (keep them in sounds/user/).
    """
    import json
    import os
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    out = []
    for sid, d in sorted(data.items()):
        if sid in BY_ID:
            raise SystemExit(f"user sound id {sid} collides with a built-in id")
        files = [p[:-4] if p.endswith(".ogg") else p for p in d["files"]]
        positional = bool(d.get("positional", d.get("category") != "music"))
        out.append(Sound(sid, d.get("category", "music"), "external", float(d.get("length", 0)),
                         variants=len(files), loop=bool(d.get("loop", False)), positional=positional,
                         stereo=not positional, paths=files, license=d.get("license", "UNSPECIFIED"),
                         method=d.get("method", "User-supplied track")))
    return out
