# Hotel of Doors

A 3D multiplayer horror game for the browser, built as a fan-inspired recreation of **The Hotel (Doors 0–100)** from Roblox *DOORS*.
All code, 3D models, textures and sounds are original and generated procedurally. No Roblox assets are used.

- **Client:** Three.js (WebGL2), TypeScript, Vite, Rapier character physics, WebAudio
- **Server:** an authoritative Node.js simulation over WebSockets (`ws`)
- **Shared:** a deterministic world generator and protocol used by both client and server

## Quick start

```bash
npm install
npm run dev            # server on :2567 + Vite client on :5173 (the /ws proxy is set up)
# open http://localhost:5173  (open a second tab or browser to play co-op)
```

To run a production build locally (one process serves both the game and the WebSocket):

```bash
npm run build
PORT=8080 STATIC_DIR=client/dist node server/dist/index.js
# open http://localhost:8080
```

Other useful commands:

| Command | What it does |
|---|---|
| `npm test` | Vitest: generation, solvability, AI, inventory, networking, full-run bots |
| `npm run test:e2e` | Playwright: builds and serves the game, then drives real browsers (2-player co-op, mobile, asset gallery) |
| `npm run typecheck` | Strict TypeScript for shared, server and client |
| `node --import tsx tools/botrun.ts <seed> [mortal] [players]` | Headless bot plays a whole run against the real simulation |
| `node --import tsx tools/gen-report.ts` | Generates and validates 30 seeds and reports any issues |
| `/?gallery=monsters` (or `library`, `greenhouse`, `courtyard`, `electrical`, `seekChase`, `shop`, `bedroom` …) | Offline visual QA of creatures and rooms |

## Controls

| Action | Desktop | Mobile |
|---|---|---|
| Move / look | WASD + mouse (pointer lock) | left-thumb joystick, swipe on the right |
| Sprint / crouch | Shift / Ctrl or C | RUN / CROUCH toggles |
| Interact (doors, drawers, hide, pick up) | E or right-click | USE |
| Equip / use / light / drop | 1–6 / Q or left-click / F / G | tap a slot / ITEM / LIGHT |
| Heartbeat minigame | Space | BEAT |
| Pause and settings | Esc | II |

Settings cover sensitivity, FOV, quality preset (low, medium, high), volumes, invert-Y, subtitles, the FPS overlay and fullscreen.

## Deployment (public HTTPS)

The server is one stateless container that serves both `https://…/` and `wss://…/ws`, so friends on different networks can join the same run with a party code or an invite link (`?join=CODE`). Profiles (knobs and stats) are stored in a JSON file at `DATA_FILE`, which should sit on a persistent volume.

- **Docker:** `docker build -t doors-hotel . && docker run -p 8080:8080 -v doors:/data doors-hotel`
- **Fly.io:** `fly launch --copy-config --no-deploy && fly volumes create doors_data --size 1 && fly deploy` (see `fly.toml`)
- **Render:** create a Blueprint from `render.yaml`

Environment variables:

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | 2567 (8080 in Docker) | HTTP/WS port |
| `STATIC_DIR` | `client/dist` | built client to serve; omit it to run a WS-only server |
| `DATA_FILE` | `server/data/profiles.json` | profile store (set it to an empty string to disable) |
| `MAX_PLAYERS` | 4 | players per elevator |
| `LOBBY_COUNTDOWN` | 5 | seconds between "all ready" and the doors closing |

**Split hosting:** host the static frontend on any CDN and build it with `VITE_SERVER_URL=https://your-game-server.example` so it connects to the separate WebSocket server. You can also pass `?server=…` at runtime.

## Architecture

```
shared/src   deterministic core shared by client and server
  rng.ts, math.ts, constants.ts, items.ts, types.ts, protocol.ts, world.ts
  gen/plan.ts        landmark placement (library 50, shop 52, infirmary, courtyard 89, greenhouse 90-99,
                     electrical 100), Seek chases (32-43 and 72-81), Halt and Dupe rooms, locks, dark rooms
  gen/shell.ts       room shells, exits, wall openings, colliders, walkable volumes
  gen/layout.ts      backtracking placement (no overlaps), per-room furnishing with retries, lanes and paths
  gen/templates.ts   ~25 room templates with furniture, loot, hiding spots, lights and puzzles
  gen/encounters.ts  entity scheduling (needs nearby hiding spots, spacing, no overlap with chases)
  gen/validate.ts    reachability flood-fill per room and whole-run validation
server/src
  index.ts           HTTP (static + /health) and WebSocket server with rate limiting
  validate.ts        strict schema validation of every client message
  lobby.ts           public quick play, private codes, ready and countdown, reconnects, cleanup of abandoned runs
  persistence.ts     profile store (atomic writes)
  game/GameRoom.ts   authoritative 20 Hz simulation: movement validation, doors and locks, loot,
                     inventory, hiding, damage and death, revives, Glitch, Hide, Screech, snares,
                     library and breaker puzzles, shop, elevator ending
  game/entities/     Rush and Ambush, Seek, Figure (A* navigation, hearing, heartbeat), Screech, Eyes, Halt
  game/nav.ts        grid A* with a binary heap and path smoothing
client/src
  assets/            procedural PBR textures, materials, geometry helpers, furniture library,
                     room shells, doors, items, skeletal rigs and creatures
  game/              RoomView (meshes and animated parts), WorldView (streaming, state, light pool), Game
  physics/           Rapier kinematic character controller (autostep, crouch, door blockers)
  render/            renderer, quality presets, post-processing (GTAO, bloom, grading)
  audio/             procedural WebAudio sound design with HRTF spatialisation and reverb
  ui/, input/, net/  HUD and menus, keyboard/mouse/touch input, reconnecting WebSocket client
```

**World generation.** The server picks the seed. Every client regenerates the identical hotel from that seed, and the same colliders drive the client's Rapier physics and the server's validation, so geometry never desyncs. Rooms load and unload in a sliding window around the player.

**Authority.** Clients send only intents plus their predicted position. The server checks speed, walkable volumes and door state, and corrects violations. The server owns monster AI, health, inventory, loot, locks and puzzle state. Puzzle solutions (library digits and breaker sequences) come from a private server RNG, so the public seed cannot reveal them. Remote players and entities are interpolated 100 ms behind the server clock.

## Notes on fidelity

Mechanics follow the current Hotel as closely as public documentation allowed:

- Rush and Ambush (warning flicker, rebounds), Seek chases with eyes, hands, a guiding light, fallen shelves, fire and chandeliers.
- The Figure in the library (books, hint paper, 5-digit padlock) and in the electrical room (key, 10 breaker switches, 3 breaker sequences, power, elevator escape).
- Screech, Eyes, Halt, Dupe, Hide, Snare, Timothy, Jack, Shadow, Glitch, Jeff's Shop and the Guiding Light hints.

Some exact numbers (speeds, damage, timings) are tuned estimates rather than values copied from the original.

## Known limitations

- **No hand-authored GLB assets.** No 3D DCC tool such as Blender was available. Every model is built procedurally at runtime: real geometry, skinned meshes with bone animation, PBR materials. They are not imported glTF files, and there is no KTX2/Basis texture compression; textures are generated on the device at a size set by the quality preset.
- **Visual fidelity.** The look aims for a stylized-realistic hotel, not photogrammetry-level photorealism.
- **Public URL.** No hosting credentials were available in the build environment, so there is no live URL yet. Deploy with the Docker, Fly or Render configs above.
- **Software rendering in CI.** Browser tests use SwiftShader, which runs at roughly 20 fps on the low preset. Real GPUs run far faster, but frame rates on physical phones were not measured.
