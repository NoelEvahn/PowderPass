# After Hours

A 2:59, six-chapter, 3D anime-style animated short in one web page (Three.js r160, procedural Web Audio).
Two rival coding-agent robot mascots, **Codex** and **Claude Code**, live their day in parallel: they wake up, make breakfast, commute, take requests, and fight in a rainy alley. Then **ChatGPT** tells them they've been working the same ticket.

## Watch
Open `index.html` in a browser (it needs internet access to load three.js from jsDelivr and fonts from Google Fonts). Press **Play**.
Controls: Space/K play-pause · ←/→ seek 5 s · M mute · C subtitles · F fullscreen · chapter menu · seek bar.

## Build
`node tools/build.mjs` concatenates `src/*.js` (one shared module scope, in filename order) into `index.html` and `dist/artifact.html`.

| file | role |
|---|---|
| `00_core.js` | math, keyframe tracks `K()`, toon materials + outline shells, canvas textures |
| `01_registry.js` | shot / line / SFX / chapter registries |
| `10_robot.js` | rigged mascots, animated canvas faces, walk cycle, arm IK, head look, ChatGPT |
| `2x_env_*.js` | apartments, city, offices, alley (reflective puddles, neon, rain) |
| `30_fx.js` | analytic particles (sparks, steam, splashes, rain), weapon trails, holograms |
| `40_screens.js` | IDE / test runner / DM screens |
| `5x_ch*.js` | the six chapters: choreography, cameras, dialogue, sound cues |
| `60_audio.js` | synthesized score (per-chapter sections, leitmotifs) and sound design |
| `70_player.js` | renderer, bloom + grade post-processing, 16:9 letterbox, UI, adaptive resolution |

Everything on screen is a pure function of film time, so seeking is exact. Audio events run on the AudioContext clock, and that clock also drives the picture, so sound stays in sync after seeks and restarts.

## Test tools (need Playwright and a local `three@0.160.0`, path given in `THREE_DIR`)
- `tools/shoot.mjs out WxH t1 t2 …`: renders film times to PNG
- `tools/playtest.mjs WxH [prefix] [mobile]`: play, seek, pause, mute, restart, chapters, seek bar, end of film
- `tools/audiotest.mjs`: samples output RMS/peak across the film
