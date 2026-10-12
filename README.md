# Backrooms — a five-level dimension for Paper 1.21.11

Backrooms adds a separate, server-side-only alternate reality to a Paper 1.21.11 server. It is built from five generated worlds, display-entity props and creatures, and sounds and light effects. Players need no mods or resource packs. The worlds never touch the Overworld, the Nether or the End.

| | |
|---|---|
| Server | Paper **1.21.11**, Java 21 |
| Plugin JAR | `build/libs/Backrooms-1.0.0.jar` |
| Dependencies | none at runtime; no NMS, no packets, no resource pack |
| Worlds | `backrooms_level0`, `backrooms_level1`, `backrooms_poolrooms`, `backrooms_level4`, `backrooms_plains` |

---

## Installation

1. Put `Backrooms-1.0.0.jar` in `plugins/` and start the server once. The five worlds are created on first start; each takes a few hundred milliseconds.
2. Edit `plugins/Backrooms/config.yml` if you want to, then restart the server or run `/backrooms reload`.
3. Enter with `/backrooms enter` (this needs `backrooms.use`, which everyone has by default) or `/backrooms enter <player>`.

> Use `/backrooms reload` rather than `/reload` or plugin managers. The worlds keep a reference to the generator, so hot-swapping the JAR is not supported.

**Building from source:** run `./gradlew build` (or `gradle build`). The API comes from `repo.papermc.io`. If that host is unreachable, pass a local Maven mirror with `-PlocalPaperRepo=/path/to/repo`. The JAR in this branch was built that way, against a Paper API 1.21.11 compiled from PaperMC's `ver/1.21.11` source.

---

## How players experience it

- **Getting in:** there are two ways:
  - **Suffocate in sand, gravel or concrete powder** (anything that falls) outside the Backrooms for about 2 seconds. Reality gives way, and you take no suffocation damage while it counts. Ordinary suffocation in a normal wall is never affected.
  - Run `/backrooms enter`. It can be turned off and limited to certain worlds. Admins can also send players in.

  Either way you **fall in**:
  - You appear high in the dark above Level 0, looking down, and fall straight down with your sideways movement locked.
  - Just before you reach the roof, a 3×3 shaft opens through the roof and ceiling.
  - You land in the Lobby with no fall damage, and the shaft closes behind you about 2 seconds later.

  Each entry lands in a different part of Level 0, so players don't all share one spot. The place they left from is saved as their **return point**.
- **Moving between levels:** every level contains physical structures that lead elsewhere. Each district (about 180–220 blocks square) is guaranteed to hold one of each of its level's required transitions. Nearby transitions also give a recognisable cue: a low beacon hum, or wind for exits.
- **Getting out:** players have to *find* an exit. `/backrooms leave` is admin-only by default. The exits are:
  - the EXIT door in Level 0
  - the FIRE EXIT in Level 4
  - the giant doorframe in the Plains

  Dying also returns the player to their return point (`death.mode: RETURN`).
- **Surviving:**
  - Generated barrels hold limited supplies the first time they are opened: Almond Water, rations, batteries, flashlights, and damp notes that hint at the transitions.
  - **Sanity** drains in the dark and near creatures. It recovers in light, in safe rooms and near other players.
  - Low sanity brings whispers, a heartbeat, nausea and private hallucinations.
  - The **Flashlight** casts a moving light that only clients see.
  - Answering a ringing phone gets a whispered direction and distance to the nearest way onward. Radios carry warnings about the creatures.

### Transition map

| From | Structure (trigger) | To |
|---|---|---|
| Level 0 | Concrete stairwell pit (walk down) | Level 1 |
| Level 0 | Cold tiled seam in a wall (crouch against it, or stand there for 3 s) | Poolrooms |
| Level 0 | Door with grass under it and daylight around the frame (walk through) | Plains (about 60% of districts) |
| Level 0 | Free-standing door with green EXIT sign | **Exit** (about 30% of districts) |
| Level 1 | Yellow wallpaper doorway | Level 0 |
| Level 1 | Freight elevator (stand inside for 2 s) | Level 4 |
| Level 1 | Half-open loading shutter with daylight under it | Plains (about 70%) |
| Poolrooms | Black drain at the bottom of an 8-deep pool | Level 0 |
| Poolrooms | Grey service-door booth | Level 1 |
| Poolrooms | Sunlit arch | Plains |
| Level 4 | Stairwell in the largest room | Level 1 |
| Level 4 | Copper FIRE EXIT door | **Exit** (about 45%) |
| Level 4 | Window that is far too bright | Plains (about 60%) |
| Plains | The one yellow house (step inside) | Level 0 |
| Plains | Backyard pool (dive to the bottom) | Poolrooms |
| Plains | Bunker hatch in the grass | Level 1 |
| Plains | Giant free-standing doorframe | **Exit** (about 50%) |

---

## The five levels

Every level follows the same rules: **a small block palette**, and **no light-emitting blocks at all** (no lanterns, froglights, glowstone, sea lanterns, torches or bulbs). Every light source is an invisible `LIGHT` block behind a display-entity fixture model: ceiling panels, tube lights, cage lamps, skylights, pool lamps, floor lamps. Some fixtures are dead and some flicker; blackouts dim the fixture models along with the light.

Each level uses a **different generation rule**:

**Level 0 — The Lobby.** *The only maze.* 8-block cells joined by a spanning tree with extra loops.
- Palette: smooth sandstone walls, yellow terracotta carpet with brown stains, white ceiling.
- Thick walls, stub walls, pillar cells and open halls; dark sectors, low-ceiling sectors and damp sectors with puddles.
- Whole regions sometimes repeat their neighbour exactly.
- Landmarks: a desk with a phone, a circle of chairs facing nothing.

**Level 1 — Habitable Zone.** *A grid, not a maze.* Endless straight concrete corridors on a 40-block grid.
- Corridor width varies per line; about 15% are grand halls 9 wide and 12 tall with pillar rows.
- Rooms open off the corridors: empty, storage, flooded, supply caches with better loot, and **safe rooms** (wooden floor; creatures never enter, sanity recovers).
- Tube lights and cage lamps every 10 blocks, with dark stretches.

**The Poolrooms.** *One continuous hall of bays.* 12-block bays between 2×2 pillars; nothing is a corridor.
- Each bay is dry, shallow or deep (3–8 blocks), and the ceiling rises with depth (7, 10 or 16 blocks).
- White and light-blue tile and water only. Free-standing tile screens always leave a gap.
- Deep pools have a submerged light at the bottom; skylight panels, pool lamps, caustic reflections, a lifebuoy, a lifeguard post and a working water slide.

**Level 4 — Abandoned Office.** *Partitioned, not carved.* 48-block city blocks are split into rooms by binary space partitioning, with a perimeter corridor.
- Rooms by size: open-plan desk farms, glass meeting rooms, break rooms, offices, closets, storage.
- Small rooms have doors; some rooms are completely unlit.

**The Plains.** *Open sky, stuck at evening.* A superflat field under a sky with no sun movement.
- Sparse dirt roads lined with utility poles and identical white houses with dark windows.
- **Everything is slightly wrong:** houses on the same road share a defect (no door, a door too high, sunk into a trench, floating, a door into a wall, or replaced by a black monolith). Chairs face the back wall.
- **Colossal display-entity structures** stand on the horizon: a giant chair, a doorframe, stairs to nowhere, a wall outlet, a TV, a floating panel, a lone wall and a tall figure.
- A Faceling watches from the field (see below).

> Distant colossi need clients to track entities far away. Raise `world-settings.default.entity-tracking-range.display` in `spigot.yml` if they pop in late; `displays.outdoor-activation-radius-chunks` controls how far they spawn.

---

## Creatures and anomalies

None of these is a renamed vanilla mob. Each is a coordinated group of display entities with smooth interpolated motion and limb animation, moved by a bounded A* search over real blocks. Creatures that can be hit have a real `Interaction` hitbox, and damage follows the attacker's attack-damage attribute and cooldown.

| Type | Levels | Class | Behaviour and counterplay |
|---|---|---|---|
| **Smiler** | 0, 1 | threat | Floating grin and eyes, found only in the dark. Freezes while watched; stare for about 4 s and it dissolves. Creeps forward when you look away. Light at ≥11 keeps it at bay. A giggle warns you when it is close. |
| **Faceling** | all | sighting | Tall and faceless. Stalks at 12–20 blocks and leaves when stared at. Harmless, but drains sanity; also appears as **private hallucinations**. **In the Plains** it stands 40–70 blocks out in the field, comes 7 blocks closer every 7 s you don't look at it, and leaves when stared at for 5 s or approached within 18 blocks. |
| **Hound** | 1 | threat (killable, 30 HP) | Quadruped. **Hunts by sound** (sprinting is loud, sneaking almost silent) and by sight. Growls and lowers its head before a lunge. Flees when badly hurt. |
| **Watcher** | 0, 1, 4 | stalker (hit it and it is gone) | A flat black silhouette at the end of a hall. While anyone looks at it, it does not move at all; the moment nobody is, it closes the distance fast and silently. Stare it down for about 7 s and it disappears. If it reaches you: damage, darkness, and the lights flicker. |
| **Drifter** | Poolrooms | threat (20 HP) | Pale shape in deep water. Bubbles warn you. It drags swimmers down; leave the water or strike it. |

Creatures notice everyone except spectators, so they also appear and stalk around **creative-mode** players (useful for testing). They only ever **hurt** players in survival or adventure mode.

Spawning is deliberately rare:
- Each player gets an attempt every 15 s, with chances per level.
- Caps apply per player (2) and per level (8).
- Creatures never spawn in safe rooms or in plain sight (Facelings excepted).
- Each type has its own placement rules (Smilers in the dark, Drifters in deep water).

## Environmental events

`flicker`, `blackout` (raises spawn chances), `lights_out` (lights die one by one toward you), `footsteps`, `knocking`, `door_behind` (a real door behind you opens, then closes), `machinery`, `pool_echo`, `phone_ring`, `wind` (Plains), `deja_vu` (a hallucination), `hum_shift`.

All light effects are **client-side only** (`sendBlockChange` on the invisible `LIGHT` blocks, plus dimming the fixture models). The world is never modified, so a crash can never leave lights broken.

---

## Commands

| Command | Permission | Notes |
|---|---|---|
| `/backrooms help` | `backrooms.use` | Player and admin help |
| `/backrooms enter [player]` | `backrooms.use` (self, if `entry.allow-self-enter`) · `backrooms.admin.teleport` (others) | Safe entry into Level 0; saves the return point |
| `/backrooms leave [player]` | `backrooms.admin.teleport` (or self, if `entry.allow-self-leave`) | Back to the saved return point or the fallback |
| `/backrooms level <0\|1\|poolrooms\|4\|plains> [player]` | `backrooms.admin.teleport` | Admin spawn if set, otherwise the safe cell nearest the origin |
| `/backrooms levels` | `backrooms.use` | The five levels and their status |
| `/backrooms setspawn [level]` | `backrooms.admin.teleport` | Stand in the level; the spot is validated as safe |
| `/backrooms status` | `backrooms.admin.debug` | Worlds, players, loaded chunks, threats per type, display counts, generator info, average tick cost |
| `/backrooms entity list` | `backrooms.admin.entities` | Creature catalogue |
| `/backrooms entity spawn <type> [amount]` | `backrooms.admin.entities` | Uses the natural placement rules, with a fallback in front of you |
| `/backrooms entity clear [radius\|level [level]]` | `backrooms.admin.entities` | Clearing a whole level needs `/backrooms confirm` within 15 s |
| `/backrooms event list` | `backrooms.admin.events` | |
| `/backrooms event trigger <event> [level]` | `backrooms.admin.events` | For yourself, or for every player in the named level |
| `/backrooms reload` | `backrooms.admin.reload` | Restarts services without unloading worlds or duplicating schedulers |
| `/backrooms debug <on\|off>` | `backrooms.admin.debug` | Action-bar overlay (level, cell, zone, nearest transition, light, sanity) plus a console log |
| `/backrooms give <almond_water\|flashlight\|battery\|ration\|note> [player]` | `backrooms.admin` | For testing |
| `/backrooms confirm` | — | Confirms a pending destructive action |

Commands work from the console wherever a target player or level is given. Alias: `/br`.

**Permission defaults:**

| Permission | Default |
|---|---|
| `backrooms.use` | everyone |
| `backrooms.admin` and its children (`.teleport`, `.entities`, `.events`, `.debug`, `.reload`, plus `backrooms.bypass.commands`) | op |
| `backrooms.bypass.protection` | nobody (build inside the Backrooms) |

---

## Configuration reference (`config.yml`)

| Key | Meaning |
|---|---|
| `seed` | Base seed; `0` picks a random one once. **Locked**: stored in `data/worlds.yml` |
| `world-prefix` | World folder prefix |
| `levels.<level>.enabled` | Load this level's world. A disabled level's transitions show "the way is sealed". Players left in a disabled level are sent home when they join |
| `levels.<level>.biome` | Biome key for ambient particles, sounds and water colour |
| `levels.<level>.generation.*` | **Locked** per level when its world is created: each level has its own keys (Level 0: hall/thick-wall/repeat/loop chances; Level 1: grand-hall/room/cache chances; Poolrooms: water ratio, screens; Level 4: room-light chance; Plains: road/house/colossus chances), plus exit, Plains and landmark chances and decoration density |
| `entry.allow-self-enter` / `allow-self-leave` / `allowed-worlds` / `spread-radius` / `title` | Entry rules |
| `entry.fall.enabled` / `entry.fall.height` | Enter by falling from this many blocks above the roof (default 90); off = instant teleport |
| `entry.suffocation.enabled` / `entry.suffocation.seconds` | The sand/gravel suffocation trigger and how long it takes (default 2 s). It honours `allowed-worlds` and needs `backrooms.use` |
| `return.fallback-world` | Used when a return point is missing or its world is gone |
| `death.mode` | `RETURN`, `LEVEL0` or `STAY`. `death.keep-inventory` keeps items and XP for deaths inside |
| `restrictions.*` | Block protection, ender pearls, chorus fruit, portals, beds, music suppression, `blocked-commands` (e.g. `home`, `spawn`, `tpa`, `warp`, `back`) |
| `transitions.*` | Arrival cooldown, dwell/intent times, arrival protection |
| `displays.*` | Activation radius (chunks), `outdoor-activation-radius-chunks` (Plains, default 8), `max-entities-per-chunk`, `max-entities-per-world`, chunks per tick, animation interval, view range, hazards on/off |
| `creatures.*` | Tick interval, spawn attempt period, caps, despawn distance, `level-chance`, per-type `enabled`/`damage`/`health`/`weight` per level |
| `events.*` | Events on/off, check period, chance, ambience, flicker radius |
| `sanity.*` | Sanity on/off; optional action-bar readout |
| `items.*` | Flashlight battery length |
| `messages.*` | Every player-facing message, in MiniMessage format |

**Locked values** (the seed and `generation.*`) are snapshotted the first time a level's world is created. Later edits only affect levels whose worlds do not exist yet, so existing worlds never develop seams. The snapshot lives in `plugins/Backrooms/data/worlds.yml`. Writes are atomic and the previous version is kept as `.bak`. **Back this file up with your worlds.**

---

## Architecture notes

- **Dimension:** five server-created worlds with the NORMAL environment and custom `ChunkGenerator`s.
  - All vanilla stages are disabled: noise, caves, decorations, structures and mob spawning.
  - The worlds use fixed time (midnight indoors, evening in the Plains), no weather, no mob spawning, a random tick speed of 0 and no fire spread.
  - Indoor levels are sealed with bedrock top and bottom; the Plains has a bedrock floor and open sky.
  - Paper cannot add new client-side dimension types; players experience the five worlds as one reality because the plugin moves them with controlled teleports.
- **Generation:** a pure function of (seed, coordinates), so it is thread-safe and seamless across chunks.
  - Every level has its own layout rule (maze, corridor grid, bay hall, BSP office blocks, open field), but all are organised into **regions** and **districts**; each district receives the level's guaranteed transitions, and each layout is checked for full reachability by the tests.
  - Zones come from smooth noise sampled per cell, so style borders never cut through a room.
  - All water is contained still source water, and flowing is additionally cancelled.
- **Display subsystem:**
  - About 80 authored models: BlockDisplay, ItemDisplay, TextDisplay and Interaction.
  - Animation: spin, sway, bob, jitter, slide, orbit, blink and flicker, sent with display interpolation.
  - Scripted behaviours: elevator doors, a ringing phone, lamp dimming during events.
  - Emitters, telegraphed hazards, and interactions (bell, phone, radio, vending machine).
  - Props are derived from the layout, so they are **never saved**. They are spawned near players within per-chunk and per-world budgets, spread across ticks, and removed when out of range, when the chunk unloads, and on disable.
  - Every entity is non-persistent and tagged in its PDC; tagged leftovers are swept when chunks load.
  - Props that need collision get invisible barrier blocks from the generator. Lamps get invisible light blocks.
- **Entry shafts:** before any roof block is removed, the shaft is written to `data/open-shafts.yml`; it is erased once the blocks are put back. If the server crashes mid-fall, any shaft left open is closed on the next start. Block entities (barrels, chests) are never cut.
- **Persistence:** the return point, the "inside" flag (used to detect stranded players) and sanity live in each player's PDC. World metadata and admin spawns live in `data/worlds.yml`. Worlds are never deleted or regenerated. A world created by an older generator version logs a warning; delete its folder (and its entry in `data/worlds.yml`) to regenerate it with the new layout.
- **Threading:** Bukkit calls happen on the main thread. Chunk preparation for teleports uses `getChunkAtAsync`. Generator block states are pre-resolved at startup. Spawn writes are snapshotted on the main thread and written asynchronously.
- **No NMS and no packets.** Client-side light effects use the stable `Player#sendBlockChange` API, and private hallucinations use `Entity#setVisibleByDefault` and `Player#showEntity`.

## Testing

`gradle test` runs 49 automated tests. All of them pass on this build.

- **Generation (31 tests across all 5 levels):**
  - Determinism across instances and chunk order, and seed sensitivity.
  - **Flood-fill reachability** of every safe spot and every transition trigger, walking real painted chunks the way a player would (walking, stepping, dropping, swimming, climbing).
  - Water is contained and still; bedrock shell (no roof check in the Plains).
  - At least one guaranteed transition in every district.
  - The safe-spot search lands on standable, trigger-free ground.
- **Plugin integration on MockBukkit (15 tests):** world creation and locked snapshots, permissions and tab completion, enter/leave and return points, protection, a stairwell transition with arrival cooldown, creature hitboxes and confirmed clearing, decoration spawn/despawn, clean disable.
  - **The fall entry:** the player starts above the roof, sideways movement is locked, a 3×3 shaft (and only 3×3) opens before the roof, fall damage is cancelled during the fall and just after landing, and the roof and ceiling are restored with no pending shaft left on disk.
  - **The suffocation trigger:** suffocating in sand takes you into Level 0 with no damage; suffocating in stone is left alone.
  - **Creative players:** creatures stay alive around them and never hurt them.
- **Models and states (3 tests):** every decoration id builds into a model (77 used), display density per chunk stays within budget (highest: Level 4 at about 25 per chunk), and all 62 generator block states are validated by a real block-data parser.

### Not verified in a real game: please check in-game

No Paper server binary could be downloaded in the build environment, so nothing has been run on a live server yet. Checklist:

1. **Upgrading:** delete the old `backrooms_level2/3/5` folders and any world that logs an old generator version, then start the server. The five worlds should be created without errors, and `/backrooms status` should show them.
2. **Visual pass, one `/backrooms level <x>` per level:** palettes, light levels, fixture models lining up with the ceiling `LIGHT` blocks, and prop orientation against walls.
3. **The Plains:** colossi visible at distance (see the tracking-range note), house variants, the fixed evening sky.
4. **The fall entry:** the sky start, the elytra wind sound, the shaft opening in time on a laggy connection, landing with no damage, and the ceiling closing. Bury yourself in sand in the Overworld to test the suffocation trigger.
5. **Each transition:** trigger feel, arrival spot, no bounce back. The Poolrooms slide push. Seam intent (crouch). The drain and the backyard pool dive.
6. **Creature behaviour and fairness:** creatures spawning naturally within a minute or two (watch `/backrooms debug on`); the Watcher freezing exactly while watched; Smiler freezes when looked at; Hound sound tracking and lunge; Drifter grab and release; the Plains Faceling approaching only while unwatched; clicking `Interaction` hitboxes with real clients.
7. **Client-side effects:** that a `LIGHT` block sent by `sendBlockChange` relights on real clients (flashlight, flicker, blackout, lights out).
8. **The `door_behind` event:** the door opens and closes back cleanly.
9. **Death in each `death.mode`;** joining after a level was disabled (stranded recovery); ender pearl and chorus blocking; blocked commands.
10. **Performance with several players:** watch `/backrooms status` tick costs, then tune `displays.activation-radius-chunks` and the entity caps if needed.
