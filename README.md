# PowderCraft

A Minecraft-style voxel sandbox that runs in the browser (desktop + mobile). No build step, no external assets — Three.js is vendored in `lib/`.

## Run
Serve the folder over HTTP (ES modules don't load from `file://`):

```
npx http-server -c-1 .     # or: python3 -m http.server
```
Open http://localhost:8080. WebGL2 is required.

## Features
- **Worlds**: 21 overworld biomes — plains, forest, flower forest, birch forest, dark forest, cherry grove, taiga, snowy plains, ice spikes, desert, badlands (terracotta mesas), savanna, jungle, swamp, mushroom fields, mountains, beach, ocean, deep/warm/frozen ocean — plus caves, ores, many tree types and giant mushrooms.
- **Structures**: villages (style per biome, houses, farms, well, lamp posts, villagers), shipwrecks, desert wells, ruined portals, dungeons, Nether fortresses — with loot chests.
- **The Nether**: obsidian portal (light with flint & steel), Nether wastes, crimson forest, warped forest and soul sand valley biomes, huge fungi, basalt pillars, lava seas, glowstone, fortresses, zombified piglins. 1:8 coordinate scaling.
- **Shaders** (Options → Graphics: Shaders): sun shadow mapping, god rays, ACES tonemapping, waving leaves/grass, realistic water (screen-space refraction, terrain reflections, depth-based light absorption, shoreline foam, Snell's window from below, sun glints), underwater caustics and distortion, sky with sunrise/sunset, stars, moon, clouds, day/night cycle.
- **Lighting**: flood-fill sky light and block light (torches, glowstone, lava), smooth lighting + ambient occlusion.
- **Fluids**: Minecraft-style levels — water spreads 7 blocks, lava 3 (7 in the Nether), getting lower further from the source; falling columns, infinite water sources, flow toward nearest drop, sloped surfaces, animated flow, currents push the player; water + lava make obsidian/cobblestone/stone.
- **Player**: survival & creative, health, hunger, drowning, fall/lava damage, sprint, sneak (edge-safe), swim, creative flight. First person and two third-person cameras with animated Steve (walk, sprint, sneak, swim stroke, fly, treading water, mining swing).
- **Mobs**: pig, cow, sheep, chicken, zombie, skeleton (shoots arrows), creeper (explodes), zombified piglin. Day/night spawning, drops.
- **Items**: block drops as spinning 3D items, falling sand/gravel, TNT, tools with tiers and durability, food, buckets.
- **GUIs**: hotbar, inventory with 2×2 crafting, crafting table 3×3, furnace (smelting + fuel), chest, creative inventory with tabs/search.
- **Saves**: worlds are stored in IndexedDB (auto-save every 30 s and on pause/close).

## Controls
| PC | Action |
|---|---|
| WASD / Space / Shift / Ctrl | move / jump / sneak / sprint (or double-tap W) |
| Mouse L / R / Middle | break & attack / place & use / pick block |
| 1–9, wheel | hotbar |
| E | inventory · Q drop (Ctrl+Q stack) |
| F5 or V | camera · F3 debug · F1 hide HUD · F4 toggle game mode |
| Double-tap Space or F | fly (creative) · Esc pause |

**Mobile**: left joystick to move (push far forward to run), drag right side to look, tap to place/use (or hit a mob), hold to break; on-screen buttons for jump, sneak, fly, inventory, camera, drop, pause.

## Real Minecraft textures, sounds and music
Mojang's assets are copyrighted, so they are not bundled or downloaded. If you own Minecraft, the title screen can use the files from **your own install** (read locally in the browser, remembered for next time):

- **Use Minecraft Textures** → pick `.minecraft/versions/<version>/<version>.jar` (or any resource pack `.zip`).
- **Use Minecraft Sounds & Music** (desktop browsers) → pick the `.minecraft/assets` folder. Block, mob, damage and liquid sounds plus game, creative, Nether and menu music are used.

`.minecraft` is at `%APPDATA%\.minecraft` (Windows), `~/Library/Application Support/minecraft` (macOS) or `~/.minecraft` (Linux). Without them the game uses its own procedural textures and synthesized sounds.
